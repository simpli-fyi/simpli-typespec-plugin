package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import simpli.fyi.plugins.typespec.fixtures.TypeSpecHeavyFixtureTestCase
import simpli.fyi.plugins.typespec.fixtures.TypeSpecWorkspaceLayout
import simpli.fyi.plugins.typespec.psi.TypeSpecElementFactory
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Plan 08 M6.5L (ADR 0014). Pins two production defects the light 342-test suite could not
 * reach -- neither has a real file on disk, so ADR 0013's `node_modules` symlink twin cannot be
 * expressed, and ADR 0012 F13's PSI/document/disk divergence has nothing to diverge from.
 *
 * Pins 4 and 5 are the "oracle teeth" tests: they prove the failure signal each defect-pin
 * depends on can actually fire, deliberately, in test code. See each pin's KDoc for outcome.
 */
class TypeSpecRenameOnDiskTest : TypeSpecHeavyFixtureTestCase() {

    private fun mainModel(layout: TypeSpecWorkspaceLayout.Layout): TypeSpecModelStatement =
        PsiTreeUtil.findChildrenOfType(layout.main.psiFile, TypeSpecModelStatement::class.java)
            .first { it.name == "Foo" }

    // ---- Pin 1 -- ADR 0013, cheap and deterministic --------------------------------------

    /**
     * No saving, no refresh, no async: the moment `getUseScope()` stops excluding
     * `node_modules`, this fails in under a second with a comprehensible message.
     */
    fun testPin1_useScopeExcludesNodeModulesTwin_andOnlyOneUsageIsFound() {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)
        val foo = mainModel(layout)

        val useScope = foo.useScope
        assertTrue("useScope must be a GlobalSearchScope, was ${useScope.javaClass}", useScope is GlobalSearchScope)
        val globalUseScope = useScope as GlobalSearchScope

        assertTrue(
            "useScope must contain the real model/pkg/User.tsp",
            globalUseScope.contains(layout.user.real),
        )
        assertFalse(
            "useScope must NOT contain the node_modules/pkg/User.tsp twin -- this is the exact " +
                "ADR 0013 defect shape (double-counted usage through a symlinked twin)",
            globalUseScope.contains(layout.user.nodeModulesTwin),
        )

        val usages = ReferencesSearch.search(foo).findAll()
        assertEquals(
            "expected exactly one usage of Foo, not one per VirtualFile twin -- found: " +
                usages.map { it.element.containingFile.virtualFile.path },
            1,
            usages.size,
        )
    }

    // ---- Pin 2 -- ADR 0013, end to end -----------------------------------------------------

    /**
     * Renames through the real handler entry point, settles the VFS, and asserts: no double
     * write reaches disk, no [IllegalStateException] escapes (that would be
     * `MemoryDiskConflictResolver.askReloadFromDisk` firing -- ADR 0014 §The oracle), and PSI /
     * document / disk all agree afterward for both project files.
     *
     * ADR 0015 owner ruling 2 / plan 09 M6.5n: driven through both [renameAtCaret] handlers --
     * opting out of in-place here (as this pin did, pre-M6.5n) is exactly what let the ADR 0015
     * defect through a fixture built to catch this class of bug.
     */
    fun testPin2_renameEndToEnd_singleWriteReachesDisk_noConflictException() {
        testPin2Body(inPlace = false)
    }

    fun testPin2_renameEndToEnd_singleWriteReachesDisk_noConflictException_inPlace() {
        testPin2Body(inPlace = true)
    }

    private fun testPin2Body(inPlace: Boolean) {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)

        fixture.configureFromExistingVirtualFile(layout.main.real)
        val fooOffset = fixture.file.text.indexOf("Foo")
        fixture.editor.caretModel.moveToOffset(fooOffset + 1)

        var thrown: Throwable? = null
        try {
            renameAtCaret("Bar", inPlace)
            settleVfs(layout.tempRoot)
        } catch (t: Throwable) {
            thrown = t
        }
        assertNull("rename + settle must not throw (inPlace=$inPlace) -- a double write raises " +
            "IllegalStateException from MemoryDiskConflictResolver.askReloadFromDisk", thrown)

        val realUserPath = Paths.get(fixture.tempDirPath, "model", "pkg", "User.tsp")
        val twinUserPath = Paths.get(fixture.tempDirPath, "node_modules", "pkg", "User.tsp")
        val realUserText = Files.readString(realUserPath)
        val twinUserText = Files.readString(twinUserPath)

        assertEquals(
            "expected exactly one occurrence of Bar in the rewritten file, got: $realUserText",
            1,
            Regex("\\bBar\\b").findAll(realUserText).count(),
        )
        assertEquals(
            "expected zero occurrences of Foo left in the rewritten file, got: $realUserText",
            0,
            Regex("\\bFoo\\b").findAll(realUserText).count(),
        )
        assertEquals(
            "the node_modules twin is the same inode -- it must read back identical content",
            realUserText,
            twinUserText,
        )

        val mainPsi = requireNotNull(PsiManager.getInstance(project).findFile(layout.main.real))
        val userPsi = requireNotNull(PsiManager.getInstance(project).findFile(layout.user.real))
        assertPsiDocumentAndDiskAgree(mainPsi)
        assertPsiDocumentAndDiskAgree(userPsi)
    }

    // ---- Pin 3 -- ADR 0012 F13, with the shipped manipulator ------------------------------

    /**
     * With the shipped `ElementManipulators.handleContentChange`-routed `setName` (ADR 0012 D4),
     * PSI, the in-memory Document and the bytes on disk must all agree after a rename on a file
     * that has a cached, open-editor Document. Whether this fixture would have caught the *old*,
     * defective raw `ASTNode.replaceChild` write is answered separately by Pin 5.
     */
    fun testPin3_f13Invariant_holdsWithShippedManipulator() {
        testPin3Body(inPlace = false)
    }

    fun testPin3_f13Invariant_holdsWithShippedManipulator_inPlace() {
        testPin3Body(inPlace = true)
    }

    /**
     * ADR 0015 owner ruling 2 / plan 09 M6.5n: driven through both [renameAtCaret] handlers --
     * see [testPin2Body]'s KDoc for why opting out of in-place here used to be the gap.
     */
    private fun testPin3Body(inPlace: Boolean) {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)

        fixture.openFileInEditor(layout.main.real)
        fixture.configureFromExistingVirtualFile(layout.main.real)
        val fooOffset = fixture.file.text.indexOf("Foo")
        fixture.editor.caretModel.moveToOffset(fooOffset + 1)
        renameAtCaret("Baz", inPlace)

        val mainPsi = requireNotNull(PsiManager.getInstance(project).findFile(layout.main.real))
        assertPsiDocumentAndDiskAgree(mainPsi)
    }

    // ---- Pin 4 -- oracle teeth for ADR 0013 -----------------------------------------------

    /**
     * Provokes `MemoryDiskConflictResolver`'s conflict deliberately, in test code only:
     * 1. dirty the `node_modules` twin's cached Document (unsaved);
     * 2. write *different* bytes through the real path;
     * 3. refresh, then dispatch the invocation queue -- `askReloadFromDisk`'s throw happens
     *    inside an `invokeLater` runnable (ADR 0014 §The oracle), not synchronously in the write.
     *
     * If this test does not throw, Pin 2 is decorative -- reported verbatim in the milestone
     * report either way, never silently weakened.
     */
    fun testPin4_oracleTeeth_deliberateConflictThrowsIllegalStateException() {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)

        val twinDocument = requireNotNull(FileDocumentManager.getInstance().getDocument(layout.user.nodeModulesTwin))
        WriteCommandAction.runWriteCommandAction(project) {
            twinDocument.insertString(twinDocument.textLength, "\n// dirtied via node_modules twin\n")
        }
        assertTrue(
            "the twin's document must be unsaved before the deliberate conflict",
            FileDocumentManager.getInstance().isDocumentUnsaved(twinDocument),
        )

        val realUserPath = Paths.get(fixture.tempDirPath, "model", "pkg", "User.tsp")
        Files.writeString(realUserPath, "// different bytes written through the real path\nmodel Uses {}\n")

        var thrown: Throwable? = null
        try {
            settleVfs(layout.tempRoot)
        } catch (t: Throwable) {
            thrown = t
        }

        val chain = generateSequence(thrown) { it.cause }.toList()
        val illegalState = chain.filterIsInstance<IllegalStateException>().firstOrNull()

        assertNotNull(
            "expected an IllegalStateException from MemoryDiskConflictResolver.askReloadFromDisk " +
                "somewhere in the exception chain; observed chain: " +
                chain.joinToString { "${it.javaClass.name}: ${it.message}" } +
                " (if this is null, Pin 2 is decorative -- report verbatim, do not weaken this test)",
            illegalState,
        )
        assertTrue(
            "the IllegalStateException's message must name the offending twin path -- got: " +
                "${illegalState?.message}",
            illegalState?.message?.contains("node_modules") == true,
        )
    }

    // ---- Pin 5 -- oracle teeth for F13 -----------------------------------------------------

    /**
     * EXPERIMENT, per plan 08 M6.5L: performs the *old*, defective write -- a raw
     * `ASTNode.replaceChild` bypassing `ElementManipulators`/the PSI-to-document synchroniser --
     * on a physical file with an open editor, and records whether [assertPsiDocumentAndDiskAgree]
     * fails as a result.
     *
     * The architect was explicit going in that whether this fixture can observe the F13
     * divergence this way was unverified. **Measured result: it does not.**
     * `identifier.node.treeParent!!.replaceChild(...)` inside a write action still leaves PSI,
     * the bound Document and the saved disk bytes in agreement here -- `PsiDocumentManager`
     * appears to synchronise from any AST mutation performed inside a write command on a
     * `PhysicalPsiFile`, not only ones that go through `ElementManipulators`. This heavy-fixture
     * shape is therefore **not** a regression test for ADR 0012 F13; the F13 claim under Pin 3 is
     * dropped rather than dressed up. This test is kept, asserting the observed (surprising)
     * agreement, purely as a documented negative result -- so a future change to this fixture or
     * to `PsiDocumentManager`'s synchronisation behaviour that silently makes it start diverging
     * is itself visible as a test change, not a silent gap.
     */
    fun testPin5_oracleTeeth_rawAstReplaceChildDoesNotObservablyDivergeInThisFixture() {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)

        fixture.openFileInEditor(layout.main.real)
        fixture.configureFromExistingVirtualFile(layout.main.real)
        val identifier = requireNotNull(mainModel(layout).nameIdentifier) { "Foo must have a name identifier" }
        val replacement = TypeSpecElementFactory.createIdentifier(project, "Baz")

        WriteCommandAction.runWriteCommandAction(project) {
            val parent = requireNotNull(identifier.node.treeParent) { "identifier must have a tree parent" }
            parent.replaceChild(identifier.node, replacement.node)
        }

        val mainPsi = requireNotNull(PsiManager.getInstance(project).findFile(layout.main.real))
        // Documented negative result (see KDoc above): this does NOT throw. If it ever starts
        // throwing, that is a genuine change in observable behaviour worth investigating --
        // do not "fix" this test by swallowing a new failure here.
        assertPsiDocumentAndDiskAgree(mainPsi)
    }
}
