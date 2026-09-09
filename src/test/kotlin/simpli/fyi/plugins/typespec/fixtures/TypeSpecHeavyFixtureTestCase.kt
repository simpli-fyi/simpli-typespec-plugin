package simpli.fyi.plugins.typespec.fixtures

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import java.nio.file.Files

/**
 * ADR 0014 / plan 08 M6.5L — the heavy fixture. Built by hand exactly the way
 * `com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase.setUp` builds itself
 * (bytecode-verified against `ideaIC-2025.2.6.3`, `lib/testFramework.jar`): a real project
 * directory on disk (`createFixtureBuilder`), a real `TempDirTestFixtureImpl` (the single-arg
 * `createCodeInsightFixture` overload, NOT the two-arg `LightTempDirTestFixtureImpl` one), and a
 * module whose source content root is that same real directory so it falls inside
 * `GlobalSearchScope.projectScope` (ADR 0014 §Alternatives rejected, row 3).
 *
 * Do not subclass `BasePlatformTestCase` (light, in-memory `TempFileSystem` — no inode, no
 * symlink) or `HeavyPlatformTestCase` (real disk, but no `CodeInsightTestFixture`: no caret
 * markup, no `renameElementAtCaretUsingHandler`).
 *
 * The field is named `fixture`, not `myFixture`, so a heavy test can never be mistaken for a
 * light one at a glance.
 */
abstract class TypeSpecHeavyFixtureTestCase : UsefulTestCase() {

    protected lateinit var fixture: CodeInsightTestFixture
    protected lateinit var module: Module
    protected val project: Project get() = fixture.project

    override fun setUp() {
        super.setUp()
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val projectBuilder = factory.createFixtureBuilder(name) // HEAVY: real project dir
        fixture = factory.createCodeInsightFixture(projectBuilder.fixture)
        //        single-arg overload; its bytecode is `new TempDirTestFixtureImpl()`, a REAL
        //        directory under the system temp dir. The two-arg overload with
        //        LightTempDirTestFixtureImpl is the thing this class exists to avoid.
        val moduleBuilder = projectBuilder.addModule(EmptyModuleFixtureBuilder::class.java)
        moduleBuilder.addSourceContentRoot(fixture.tempDirPath)
        fixture.testDataPath = "src/test/testData"
        fixture.setUp()
        module = moduleBuilder.fixture.module
    }

    override fun tearDown() {
        RunAll.runAll(
            { if (::fixture.isInitialized) fixture.tearDown() },
            { super.tearDown() },
        )
    }

    /** F13's invariant: PSI, the in-memory Document and the bytes on disk all say the same thing. */
    protected fun assertPsiDocumentAndDiskAgree(psiFile: PsiFile) {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(psiFile)!!
        assertTrue("PSI is not committed to the document", documentManager.isCommitted(document))
        assertEquals("document text diverged from PSI text", psiFile.text, document.text)
        WriteAction.runAndWait<Throwable> { FileDocumentManager.getInstance().saveAllDocuments() }
        assertEquals(
            "disk bytes diverged from PSI text",
            psiFile.text,
            Files.readString(psiFile.virtualFile.toNioPath()),
        )
    }

    /** Drains the queue so MemoryDiskConflictResolver.processConflicts runs here, inside the test. */
    protected fun settleVfs(root: VirtualFile) {
        WriteAction.runAndWait<Throwable> { FileDocumentManager.getInstance().saveAllDocuments() }
        VfsUtil.markDirtyAndRefresh(false, true, true, root)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    /**
     * ADR 0015 owner ruling 2 / plan 09 M6.5n: every heavy write-path pin must run through
     * *both* rename handlers, not just the dialog one. Pins 2/3 (plan 08) opted out of in-place
     * (`isVariableInplaceRenameEnabled = false`) to test the disk-write path in isolation -- which
     * is exactly why a fixture built to catch aliased-document conflicts (ADR 0013/0015) missed
     * the in-place-only shape of the ADR 0015 defect until `TypeSpecInplaceRenameOnDiskTest` was
     * written by hand outside this helper. New pins should call this instead of duplicating either
     * branch.
     *
     * `inPlace = true` uses [CodeInsightTestUtil.doInlineRename] with a fresh
     * [MemberInplaceRenameHandler], not `fixture.renameElementAtCaretUsingHandler`: the latter only
     * *starts* the live template and returns without completing it (ADR 0012 F14), so an
     * end-to-end in-place assertion needs `doInlineRename` to actually finish the rename.
     *
     * `inPlace = false` disables in-place on the current editor's settings first, then drives the
     * ordinary handler entry point -- `RenameDialog` is suppressed by
     * `renameElementAtCaretUsingHandler` running headless in tests, so this exercises the same
     * `RenameProcessor` write path Pins 2/3 already relied on.
     */
    protected fun renameAtCaret(newName: String, inPlace: Boolean) {
        if (inPlace) {
            CodeInsightTestUtil.doInlineRename(MemberInplaceRenameHandler(), newName, fixture)
        } else {
            fixture.editor.settings.isVariableInplaceRenameEnabled = false
            fixture.renameElementAtCaretUsingHandler(newName)
        }
    }
}
