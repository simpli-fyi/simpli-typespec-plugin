package simpli.fyi.plugins.typespec.fixtures

import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNotSame
import junit.framework.TestCase.assertTrue
import java.nio.file.Files
import java.nio.file.Paths

/**
 * ADR 0013 regression layout (plan 08 M6.5L): an npm-workspace symlink pointing `node_modules`
 * back into the project's own sources, so every file is reachable at two paths.
 *
 * ```
 * model/pkg/Main.tsp        model Foo {}
 * model/pkg/User.tsp        import "./Main.tsp";  model Uses { a: Foo; }
 * node_modules/pkg   ->  ../model/pkg          (directory symlink)
 * ```
 *
 * `Foo` is deliberately declared at the top level, not inside a `namespace` -- matching
 * `src/test/testData/rename/CrossFile.tsp`'s convention -- so a bare `Foo` reference in
 * `User.tsp` resolves without an additional `using` statement.
 */
object TypeSpecWorkspaceLayout {

    /** Both `VirtualFile` twins of one on-disk file, and its project-side `PsiFile`. */
    class FileTwins(val real: VirtualFile, val nodeModulesTwin: VirtualFile) {
        val psiFile: PsiFile get() = requireNotNull(psiFileOrNull) { "no PsiManager attached yet" }
        var psiFileOrNull: PsiFile? = null
    }

    class Layout(
        val tempRoot: VirtualFile,
        val main: FileTwins,
        val user: FileTwins,
    )

    private const val MAIN_TEXT = "model Foo {}\n"
    private const val USER_TEXT = "import \"./Main.tsp\";\nmodel Uses { a: Foo; }\n"

    fun npmWorkspace(fixture: CodeInsightTestFixture): Layout {
        // 1. On a platform that cannot make symlinks, skip rather than fail spuriously.
        IoTestUtil.assumeSymLinkCreationIsSupported()

        // 2. Real bytes into the temp dir.
        fixture.addFileToProject("model/pkg/Main.tsp", MAIN_TEXT)
        fixture.addFileToProject("model/pkg/User.tsp", USER_TEXT)

        val tempDirPath = fixture.tempDirPath
        val tempDirNio = Paths.get(tempDirPath)

        // 3. The symlink: node_modules/pkg -> ../model/pkg (directory symlink).
        Files.createDirectories(tempDirNio.resolve("node_modules"))
        val target = "$tempDirPath/model/pkg"
        val link = "$tempDirPath/node_modules/pkg"
        IoTestUtil.createSymLink(target, link)
        assertEquals(
            "IoTestUtil.createSymLink's parameter order must be (target, link) -- " +
                "verify against the method's own bytecode before trusting this",
            Paths.get(target),
            Files.readSymbolicLink(Paths.get(link)),
        )

        // 4. Synchronous, recursive refresh.
        val tempRootVfs = requireNotNull(fixture.tempDirFixture.getFile(".")) {
            "fixture.tempDirFixture.getFile(\".\") must resolve the temp root"
        }
        VfsUtil.markDirtyAndRefresh(false, true, true, tempRootVfs)
        // The stub index must have actually indexed the new content root before any caller runs
        // ReferencesSearch / rename over it -- without this wait, cross-file resolution silently
        // finds nothing (0 usages) because indexing is asynchronous with respect to the refresh.
        IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)

        // 5. Assert the layout is real before returning it.
        val realMain = LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/model/pkg/Main.tsp")
        val twinMain = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath("$tempDirPath/node_modules/pkg/Main.tsp")
        val realUser = LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/model/pkg/User.tsp")
        val twinUser = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath("$tempDirPath/node_modules/pkg/User.tsp")

        assertNotNull("real model/pkg/Main.tsp must exist", realMain)
        assertNotNull("node_modules/pkg/Main.tsp twin must exist", twinMain)
        assertNotNull("real model/pkg/User.tsp must exist", realUser)
        assertNotNull("node_modules/pkg/User.tsp twin must exist", twinUser)

        assertNotSame(
            "the real file and its node_modules twin must be distinct VirtualFile instances " +
                "-- this is the exact defect shape ADR 0013 fixed",
            realMain,
            twinMain,
        )
        assertNotSame(
            "the real file and its node_modules twin must be distinct VirtualFile instances",
            realUser,
            twinUser,
        )

        assertEquals(
            "twin content must equal the real file's content (same inode, different path)",
            String(realMain!!.contentsToByteArray()),
            String(twinMain!!.contentsToByteArray()),
        )
        assertEquals(
            "twin content must equal the real file's content (same inode, different path)",
            String(realUser!!.contentsToByteArray()),
            String(twinUser!!.contentsToByteArray()),
        )

        val nodeModulesPkgDir = requireNotNull(
            LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/node_modules/pkg"),
        ) { "node_modules/pkg directory must exist" }
        assertTrue(
            "node_modules/pkg must be reported as a symlink -- if the VFS collapsed it, every " +
                "ADR-0013 assertion downstream is meaningless",
            nodeModulesPkgDir.`is`(VFileProperty.SYMLINK),
        )
        assertFalse(
            "the real model/pkg directory must not itself be reported as a symlink",
            requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/model/pkg"))
                .`is`(VFileProperty.SYMLINK),
        )

        val psiManager = PsiManager.getInstance(fixture.project)
        val mainTwins = FileTwins(realMain, twinMain).also { it.psiFileOrNull = psiManager.findFile(realMain) }
        val userTwins = FileTwins(realUser!!, twinUser!!).also { it.psiFileOrNull = psiManager.findFile(realUser) }

        return Layout(tempRootVfs, mainTwins, userTwins)
    }
}
