package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import simpli.fyi.plugins.typespec.fixtures.TypeSpecHeavyFixtureTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Plan 07 M6.5i acceptance, cases 6 and 7 -- the two cases the plan's deviation note calls out
 * as needing a real disk and a real symlink (ADR 0013, ADR 0012 F13); everything else in M6.5i
 * is covered by the light [TypeSpecNamespaceRenameTest].
 *
 * Layout is built by hand here, deliberately not reusing [simpli.fyi.plugins.typespec.fixtures.TypeSpecWorkspaceLayout]:
 * that layout's fixed `model Foo {}` / `model Uses {}` shape does not express "three files
 * declaring the same namespace" or "a namespace declared only under `node_modules`", which is
 * what these two cases need. The npm-workspace symlink recipe (real files, then a directory
 * symlink, then a synchronous recursive refresh, then a stub-index wait) is the same recipe
 * [simpli.fyi.plugins.typespec.fixtures.TypeSpecWorkspaceLayout.npmWorkspace] uses.
 */
class TypeSpecNamespaceRenameOnDiskTest : TypeSpecHeavyFixtureTestCase() {

    // ---- case 6 (heavy) -- reopened namespace, on disk, npm-workspace symlink present -----

    /**
     * Three real files under `model/pkg/`, each declaring `namespace Shared;`, plus a
     * `node_modules/pkg -> ../model/pkg` symlink so every one of them also has a twin
     * `VirtualFile`. Rename from the real `A.tsp`, settle the VFS, and assert: no exception from
     * `MemoryDiskConflictResolver` (ADR 0013), PSI/document/disk agree on every real file
     * (ADR 0012 F13), and the bytes on disk say `namespace Renamed;` in all three.
     */
    fun testCase6_reopenedNamespaceOnDisk_npmWorkspaceSymlink_allThreeRewritten_noConflict() {
        IoTestUtil.assumeSymLinkCreationIsSupported()

        fixture.addFileToProject("model/pkg/A.tsp", "namespace Shared;\n\nmodel Alpha {}\n")
        fixture.addFileToProject("model/pkg/B.tsp", "namespace Shared;\n\nmodel Beta {}\n")
        fixture.addFileToProject("model/pkg/C.tsp", "namespace Shared;\n\nmodel Gamma {}\n")

        val tempDirPath = fixture.tempDirPath
        Files.createDirectories(Paths.get(tempDirPath, "node_modules"))
        IoTestUtil.createSymLink("$tempDirPath/model/pkg", "$tempDirPath/node_modules/pkg")

        val tempRoot = requireNotNull(fixture.tempDirFixture.getFile(".")) {
            "fixture.tempDirFixture.getFile(\".\") must resolve the temp root"
        }
        VfsUtil.markDirtyAndRefresh(false, true, true, tempRoot)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val realA = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/model/pkg/A.tsp"))
        val realB = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/model/pkg/B.tsp"))
        val realC = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/model/pkg/C.tsp"))

        fixture.configureFromExistingVirtualFile(realA)
        fixture.editor.caretModel.moveToOffset(fixture.file.text.indexOf("Shared;"))

        var thrown: Throwable? = null
        try {
            fixture.renameElementAtCaretUsingHandler("Renamed")
            settleVfs(tempRoot)
        } catch (t: Throwable) {
            thrown = t
        }
        assertNull(
            "rename + settle must not throw -- a double write raises IllegalStateException from " +
                "MemoryDiskConflictResolver.askReloadFromDisk (ADR 0013)",
            thrown,
        )

        val psiManager = PsiManager.getInstance(project)
        val aPsi = requireNotNull(psiManager.findFile(realA))
        val bPsi = requireNotNull(psiManager.findFile(realB))
        val cPsi = requireNotNull(psiManager.findFile(realC))

        assertPsiDocumentAndDiskAgree(aPsi)
        assertPsiDocumentAndDiskAgree(bPsi)
        assertPsiDocumentAndDiskAgree(cPsi)

        for ((path, psi) in listOf("A" to aPsi, "B" to bPsi, "C" to cPsi)) {
            assertTrue(
                "expected $path.tsp on disk to say 'namespace Renamed;', got: ${psi.text}",
                psi.text.contains("namespace Renamed;"),
            )
            assertFalse(
                "expected $path.tsp on disk to no longer say 'namespace Shared;', got: ${psi.text}",
                psi.text.contains("namespace Shared;"),
            )
        }

        val diskA = Files.readString(Paths.get(tempDirPath, "model", "pkg", "A.tsp"))
        assertEquals("disk bytes must equal the in-memory PSI text for A.tsp", aPsi.text, diskA)
    }

    // ---- case 7 (heavy) -- node_modules declaration excluded; its project twin renamed once --

    /**
     * Two declarations of `namespace Shared;`:
     * - a genuine library-only file that exists **only** under `node_modules` (no symlink, not a
     *   twin of anything) -- this must never be added to `allRenames` (approach 8 / ADR 0012 D9)
     *   and must be byte-identical afterwards;
     * - a project file `model/pkg/A.tsp` that also has a `node_modules/pkg/A.tsp` symlink twin --
     *   this is "its symlink twin (which is a project file)" from the plan text, and it must be
     *   rewritten exactly once, not twice, through either `VirtualFile` path (ADR 0013).
     */
    fun testCase7_nodeModulesDeclaration_neverRenamed_symlinkTwinRenamedExactlyOnce() {
        IoTestUtil.assumeSymLinkCreationIsSupported()

        fixture.addFileToProject("model/pkg/A.tsp", "namespace Shared;\n\nmodel Alpha {}\n")

        val tempDirPath = fixture.tempDirPath
        Files.createDirectories(Paths.get(tempDirPath, "node_modules", "pkg"))
        IoTestUtil.createSymLink("$tempDirPath/model/pkg", "$tempDirPath/node_modules/pkg")

        // A genuine, non-symlinked library file living only under node_modules.
        val libDir = Paths.get(tempDirPath, "node_modules", "extlib", "lib")
        Files.createDirectories(libDir)
        val libText = "namespace Shared;\n\nmodel ExternalThing {}\n"
        Files.writeString(libDir.resolve("Lib.tsp"), libText)

        val tempRoot = requireNotNull(fixture.tempDirFixture.getFile(".")) {
            "fixture.tempDirFixture.getFile(\".\") must resolve the temp root"
        }
        VfsUtil.markDirtyAndRefresh(false, true, true, tempRoot)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val realA = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/model/pkg/A.tsp"))
        val twinA = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/node_modules/pkg/A.tsp"))
        assertNotSame("real A.tsp and its node_modules twin must be distinct VirtualFiles", realA, twinA)

        fixture.configureFromExistingVirtualFile(realA)
        val namespace = PsiTreeUtil.findChildOfType(fixture.file, TypeSpecNamespaceStatement::class.java)!!

        // Unit-level check first: the library-only declaration must never enter allRenames.
        val allRenames = mutableMapOf<com.intellij.psi.PsiElement, String>()
        TypeSpecRenamePsiElementProcessor().prepareRenaming(namespace, "Renamed", allRenames)
        assertTrue(
            "allRenames must not contain any declaration whose file is under node_modules -- " +
                "found: ${allRenames.keys.map { it.containingFile?.virtualFile?.path }}",
            allRenames.keys.none { element ->
                element.containingFile?.virtualFile?.path?.contains("/node_modules/extlib/") == true
            },
        )

        fixture.editor.caretModel.moveToOffset(fixture.file.text.indexOf("Shared;"))
        var thrown: Throwable? = null
        try {
            fixture.renameElementAtCaretUsingHandler("Renamed")
            settleVfs(tempRoot)
        } catch (t: Throwable) {
            thrown = t
        }
        assertNull("rename + settle must not throw", thrown)

        // a. the library-only file is byte-identical.
        val libTextAfter = Files.readString(libDir.resolve("Lib.tsp"))
        assertEquals(
            "the node_modules-only library declaration must be byte-identical after the rename",
            libText,
            libTextAfter,
        )

        // b. the project file (and its symlink twin, same inode) is renamed exactly once.
        val aPsi = requireNotNull(PsiManager.getInstance(project).findFile(realA))
        assertPsiDocumentAndDiskAgree(aPsi)
        assertEquals(
            "expected exactly one 'namespace Renamed;' occurrence, got: ${aPsi.text}",
            1,
            Regex("namespace Renamed;").findAll(aPsi.text).count(),
        )
        assertEquals(
            "expected zero remaining 'namespace Shared;' occurrences, got: ${aPsi.text}",
            0,
            Regex("namespace Shared;").findAll(aPsi.text).count(),
        )

        val diskA = Files.readString(Paths.get(tempDirPath, "model", "pkg", "A.tsp"))
        val diskTwinA = Files.readString(Paths.get(tempDirPath, "node_modules", "pkg", "A.tsp"))
        assertEquals(
            "the node_modules symlink twin is the same inode -- it must read back identical content",
            diskA,
            diskTwinA,
        )
    }
}
