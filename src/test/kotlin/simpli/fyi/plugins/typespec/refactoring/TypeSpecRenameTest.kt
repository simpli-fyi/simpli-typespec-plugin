package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import java.io.File

/**
 * Plan 07 M6.5h acceptance (docs/plans/07-rename.md, "M6.5h -- The rename processor: which
 * elements, which usages" -- Acceptance). Fixtures live under `src/test/testData/rename/`
 * (recreated here; deleted at M0).
 *
 * `testRename(fileBefore, fileAfter, newName)` / its `additionalFiles` overload go through
 * `CodeInsightTestFixtureImpl.renameElementAtCaret` -- bytecode-verified to bypass
 * `PsiElementRenameHandler`, and therefore the veto -- so they are used below only for cases
 * that are expected to *succeed*. Every veto assertion instead uses
 * `renameElementAtCaretUsingHandler`, which does route through the handler and its `isVetoed`
 * check (plan 07 M6.5h risk note 1).
 */
class TypeSpecRenameTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun tsp(path: String, text: String) = myFixture.addFileToProject(path, text)

    private fun textOf(relativePath: String): String = File(testDataPath, relativePath).readText()

    // ---- acceptance 1: same-file declaration + usages -----------------------------------

    fun testSameFile_declarationAndUsagesRenamed() {
        myFixture.testRename("rename/SameFile.tsp", "rename/SameFile_after.tsp", "Bar")
    }

    // ---- acceptance 2: renaming to a keyword backtick-escapes every site, no parse errors -

    fun testKeyword_allSitesBackticked_andReparsesCleanly() {
        myFixture.testRename("rename/Keyword.tsp", "rename/Keyword_after.tsp", "model")
        assertNull(
            "renamed file must re-parse with no PsiErrorElement",
            PsiTreeUtil.findChildOfType(myFixture.file, PsiErrorElement::class.java),
        )
    }

    // ---- acceptance 3: THE case the original veto predicate broke -----------------------

    fun testBackticked_dropsBackticksAtDeclarationAndUsage() {
        myFixture.testRename("rename/Backticked.tsp", "rename/Backticked_after.tsp", "Foo")
    }

    /**
     * Direct unit assertion beside the fixture test above (plan 07 M6.5h acceptance 3): a
     * fixture failure here reads as "rename is broken"; this assertion says which predicate.
     * `TypeSpecRenameVetoCondition.value` must be `false` for `` model `model` {} `` --
     * `isBareIdentifier("model")` is `false` (it is a keyword) but `isIndexKeyableName("model")`
     * is `true` (ADR 0012 D7, corrected), and the veto must use the latter.
     */
    fun testVetoCondition_backtickedKeywordDeclaration_isNotVetoed() {
        val file = myFixture.configureByText("backticked-model.tsp", "model `model` {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecModelStatement::class.java)!!
        assertEquals("model", model.name)
        assertFalse(
            "TypeSpecRenameVetoCondition.value must be false for `model \\`model\\` {}` " +
                "-- see plan 07 M6.5h acceptance 3 / ADR 0012 D7",
            TypeSpecRenameVetoCondition().value(model),
        )
    }

    // ---- acceptance 4: cross-file, both files' text asserted -----------------------------

    fun testCrossFile_bothFilesRewritten() {
        myFixture.testRename(
            "rename/CrossFileUser.tsp",
            "rename/CrossFileUser_after.tsp",
            "Bar",
            "rename/CrossFile.tsp",
        )

        val crossFileVirtual = myFixture.findFileInTempDir("rename/CrossFile.tsp")
        assertNotNull("rename/CrossFile.tsp must have been copied into the test project", crossFileVirtual)
        val crossFilePsi = PsiManager.getInstance(project).findFile(crossFileVirtual!!)!!
        assertEquals(
            "the declaration file itself must also be rewritten",
            textOf("rename/CrossFile_after.tsp"),
            crossFilePsi.text,
        )
    }

    // ---- acceptance 5: scale case -- the regression guard for ADR 0012 D1 ----------------

    /**
     * 120 noise files mentioning `Widget` textually (never declared) plus one file that really
     * uses it, all in-memory via `addFileToProject`. Direct analogue of plan 06 M6.5b
     * acceptance 4: on the pre-M6.5c, 50-file-capped resolver this would have silently failed
     * to rewrite the real usage past the cap.
     */
    fun testScale_120NoiseFilesPlusOneRealUsage_realUsageRewritten() {
        repeat(120) { i ->
            tsp(
                "noise/module-$i/decoy.tsp",
                "// Widget is mentioned here as plain text, never declared\nmodel Decoy$i {}\n",
            )
        }
        tsp("decl/decl.tsp", "model Widget {}")
        val userFile = tsp(
            "user/user.tsp",
            """
            import "../decl/decl.tsp";

            model Uses {
              a: Widget;
            }
            """.trimIndent(),
        )

        val declFile = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("decl/decl.tsp")!!)!!
        val widget = PsiTreeUtil.findChildrenOfType(declFile, TypeSpecModelStatement::class.java)
            .first { it.name == "Widget" }

        myFixture.renameElement(widget, "Gadget")

        assertEquals(
            "the real usage in user.tsp must have been rewritten",
            """
            import "../decl/decl.tsp";

            model Uses {
              a: Gadget;
            }
            """.trimIndent(),
            userFile.text,
        )
    }

    // ---- acceptance 6: qualified usage, only the final segment rewritten -----------------

    fun testQualifiedUsage_onlyLastSegmentRewritten() {
        myFixture.testRename("rename/Qualified.tsp", "rename/Qualified_after.tsp", "Bar")
    }

    // ---- acceptance 6b: veto boundary table, both sides (ADR 0012 D7 anti-regression) ----

    private fun declareModel(spelling: String): TypeSpecModelStatement {
        val file = myFixture.configureByText("boundary-${System.nanoTime()}.tsp", "model $spelling {}")
        return PsiTreeUtil.findChildOfType(file, TypeSpecModelStatement::class.java)!!
    }

    fun testVetoBoundary_renameable() {
        for (spelling in listOf("Foo", "`model`", "`9lives`", "a\$b", "_x")) {
            val model = declareModel(spelling)
            assertFalse(
                "expected renameable (not vetoed) for declaration spelled `$spelling`, name=${model.name}",
                TypeSpecRenameVetoCondition().value(model),
            )
        }
    }

    fun testVetoBoundary_vetoed() {
        for (spelling in listOf("`my name`", "`a-b`", "`a.b`")) {
            val model = declareModel(spelling)
            assertTrue(
                "expected vetoed for declaration spelled `$spelling`, name=${model.name}",
                TypeSpecRenameVetoCondition().value(model),
            )
        }
    }

    // ---- acceptance 7: veto cases via the handler, file text unchanged -------------------

    private fun assertRenameRefusedViaHandler(fileName: String, before: String) {
        val file = myFixture.configureByText(fileName, before)
        val originalText = file.text
        try {
            myFixture.renameElementAtCaretUsingHandler("Renamed")
        } catch (_: Throwable) {
            // A thrown refusal is an acceptable outcome; the only thing that must never happen
            // is the file text actually changing (asserted unconditionally below).
        }
        assertEquals(
            "file text must be unchanged after a vetoed rename attempt",
            originalText,
            file.text,
        )
    }

    fun testVeto_modelProperty_viaHandler() {
        myFixture.configureByFile("rename/Member.tsp")
        val before = myFixture.file.text
        try {
            myFixture.renameElementAtCaretUsingHandler("renamed")
        } catch (_: Throwable) {
            // refusal expected
        }
        assertEquals("file text must be unchanged after a vetoed rename attempt", before, myFixture.file.text)
    }

    fun testVeto_enumMember_viaHandler() {
        assertRenameRefusedViaHandler(
            "veto-enum.tsp",
            """
            enum Color {
              Re<caret>d: "red"
            }
            """.trimIndent(),
        )
    }

    fun testVeto_unionVariant_viaHandler() {
        assertRenameRefusedViaHandler(
            "veto-union.tsp",
            """
            union Shape {
              cir<caret>cle: Circle
            }
            """.trimIndent(),
        )
    }

    fun testVeto_interfaceOperation_viaHandler() {
        assertRenameRefusedViaHandler(
            "veto-interface.tsp",
            """
            interface Store {
              g<caret>et(id: string): string;
            }
            """.trimIndent(),
        )
    }

    fun testVeto_templateParameter_viaHandler() {
        assertRenameRefusedViaHandler("veto-template.tsp", "model Foo<T<caret>Param> { x: TParam; }")
    }

    fun testVeto_nodeModulesDeclaration_viaHandler() {
        myFixture.configureByFile("rename/NodeModules/node_modules/@x/y/lib/main.tsp")
        val before = myFixture.file.text
        try {
            myFixture.renameElementAtCaretUsingHandler("Renamed")
        } catch (_: Throwable) {
            // refusal expected
        }
        assertEquals(
            "node_modules file text must be unchanged after a vetoed rename attempt",
            before,
            myFixture.file.text,
        )
    }

    fun testVeto_currentNameHasSpace_viaHandler() {
        assertRenameRefusedViaHandler("veto-space-name.tsp", "model `my na<caret>me` {}")
    }

    // ---- acceptance 8: non-TypeSpec element sanity ---------------------------------------

    /**
     * `vetoRenameCondition` is a *global* EP: every language's rename handler consults every
     * registered condition. A veto that leaked past the
     * [simpli.fyi.plugins.typespec.TypeSpecLanguage] guard would silently disable rename for
     * every other language in the same IDE.
     */
    fun testVetoCondition_nonTypeSpecElement_isNeverVetoed() {
        val plainTextFile = myFixture.configureByText("plain.txt", "hello world")
        assertFalse(
            "TypeSpecRenameVetoCondition must return false for a non-TypeSpec PSI element",
            TypeSpecRenameVetoCondition().value(plainTextFile),
        )
    }

    // ---- acceptance 9: comments and strings are never rewritten (ADR 0012 D11) -----------

    fun testCommentsAndImportStringLiteral_neverRewritten() {
        myFixture.testRename(
            "rename/CommentsAndStrings.tsp",
            "rename/CommentsAndStrings_after.tsp",
            "Bar",
        )
    }

    // ---- acceptance 10: dumb mode -- rename must not silently mis-rename -----------------

    /**
     * `PsiElementRenameHandler` is not `DumbAware` (ADR 0012 §Consequences) -- but that governs
     * the *handler*, not `RenameHandlerRegistry.hasAvailableHandler`, which is what this
     * observes. Measured 2026-09-08, deterministic across 3 fresh `--rerun-tasks` runs:
     * `hasAvailableHandler` returns `true` in dumb mode for a `TypeSpecModelStatement` --
     * neither `TypeSpecRenamePsiElementProcessor` nor `TypeSpecRenameVetoCondition` declares
     * itself `DumbAware`-sensitive, so the registry falls back to reporting a handler exists
     * without actually invoking it.
     *
     * NOTE: this probes handler *availability* only. It never calls
     * `renameElementAtCaretUsingHandler` (or any other rename entry point), so it proves nothing
     * about whether an actually-attempted rename is safe in dumb mode -- see
     * [testDumbMode_sameFile_actualRenameAttempt] and
     * [testDumbMode_crossFile_actualRenameAttempt] for that question. The "file unchanged"
     * assertion below is trivially true (nothing here tries to change the file); it is kept only
     * to pin the availability measurement, not as a safety proof.
     */
    fun testDumbMode_renameUnavailableOrRefused() {
        val file = tsp("dumb/widget.tsp", "model Widget {}")
        val before = file.text

        var observedAvailable = true
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val model = PsiTreeUtil.findChildOfType(file, TypeSpecModelStatement::class.java)!!
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.PSI_ELEMENT, model)
                .build()
            observedAvailable = RenameHandlerRegistry.getInstance().hasAvailableHandler(dataContext)
        }

        assertTrue(
            "observed dumb-mode value flipped to false -- RenameHandlerRegistry.hasAvailableHandler " +
                "behaviour for TypeSpecModelStatement has changed; re-derive this assertion, " +
                "do not just flip it",
            observedAvailable,
        )
        assertEquals(
            "no rename was attempted in this test -- this only pins the availability measurement " +
                "above, it is not a safety assertion",
            before,
            file.text,
        )
    }

    // ---- acceptance 10b: dumb mode -- does an *attempted* rename actually stay safe? -----

    /**
     * Same-file declaration + usage, rename attempted via the real handler entry point
     * (`renameElementAtCaretUsingHandler`, which does route through `isVetoed` --
     * see the class doc) while [DumbModeTestUtils.runInDumbModeSynchronously] holds the project
     * in dumb mode throughout. Unlike [testDumbMode_renameUnavailableOrRefused], this one
     * actually tries to rename and records what happens to both the declaration and the usage.
     *
     * A sanity check with the same fixture and caret placement, run *outside* dumb mode, first
     * confirmed `renameElementAtCaretUsingHandler` does successfully rewrite both the
     * declaration and the usage (`model Gadget {}` / `a: Gadget;`) -- so the fixture and caret
     * setup are known-good, and any difference observed below is attributable to dumb mode.
     *
     * Measured 2026-09-08, deterministic across 3 fresh `--rerun-tasks` runs: the attempt neither
     * throws nor rewrites anything -- the handler declines to act while the project is dumb, and
     * the file comes out byte-identical. This is outcome 1 (refused), not outcome 3 (silent
     * partial rewrite) -- no defect here.
     */
    fun testDumbMode_sameFile_actualRenameAttempt() {
        val file = myFixture.configureByText(
            "dumb-samefile.tsp",
            """
            model Wid<caret>get {}

            model Uses {
              a: Widget;
            }
            """.trimIndent(),
        )
        val before = file.text
        var thrown: Throwable? = null

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            try {
                myFixture.renameElementAtCaretUsingHandler("Gadget")
            } catch (t: Throwable) {
                thrown = t
            }
        }

        assertNull("no exception is expected for the same-file case (see class doc)", thrown)
        assertEquals(
            "same-file dumb-mode rename attempt must leave the file byte-identical -- a change " +
                "here (e.g. the declaration renamed but the usage left dangling) would be the " +
                "ADR 0012 D1 defect and must be reported, not silently accepted",
            before,
            file.text,
        )
    }

    // ---- acceptance 10c: dumb mode -- cross-file case, the one that depends on the index --

    /**
     * Declaration in one file, the only usage in a second file that imports it, caret on the
     * declaration, rename attempted via the real handler entry point while dumb mode holds
     * throughout -- the exact configuration `TypeSpecRenamePsiElementProcessor`'s doc comment
     * says depends on `ReferencesSearch` / the stub index (M6.5c, ADR 0011), which is unavailable
     * during indexing. This is the case ADR 0012 D1 and the 120-file scale test exist to guard;
     * dumb mode is a second, independent way to lose the index out from under that guard.
     *
     * A sanity check with the same two-file fixture and caret placement, run *outside* dumb mode,
     * first confirmed `renameElementAtCaretUsingHandler` does successfully rewrite both the
     * declaration (`model Gadget {}`) and the cross-file usage (`a: Gadget;`) -- so the fixture
     * and caret setup are known-good, and any difference observed below is attributable to dumb
     * mode.
     *
     * Measured 2026-09-08, deterministic across 3 fresh `--rerun-tasks` runs: identical to the
     * same-file case -- the attempt neither throws nor rewrites either file. Outcome 1 (refused),
     * not outcome 3 (declaration rewritten with the cross-file usage left dangling) -- no defect
     * found for this configuration.
     */
    fun testDumbMode_crossFile_actualRenameAttempt() {
        val userFile = tsp(
            "dumbcross/user.tsp",
            """
            import "./decl.tsp";

            model Uses {
              a: Widget;
            }
            """.trimIndent(),
        )
        val declPsiFile = tsp("dumbcross/decl.tsp", "model Widget {}")
        myFixture.configureFromExistingVirtualFile(declPsiFile.virtualFile)
        myFixture.editor.caretModel.moveToOffset(declPsiFile.text.indexOf("Widget") + 3)
        val declFile = myFixture.file
        val userBefore = userFile.text
        val declBefore = declFile.text
        var thrown: Throwable? = null

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            try {
                myFixture.renameElementAtCaretUsingHandler("Gadget")
            } catch (t: Throwable) {
                thrown = t
            }
        }

        val declAfter = declFile.text
        val userAfter = userFile.text

        assertNull("no exception is expected for the cross-file case either (see class doc)", thrown)
        assertEquals(
            "cross-file dumb-mode rename attempt must leave the declaration file byte-identical",
            declBefore,
            declAfter,
        )
        assertEquals(
            "PRODUCTION DEFECT if this ever fails (see class doc): the cross-file usage file " +
                "must stay byte-identical too -- a change here while the declaration stayed put, " +
                "or a declaration rewrite with this file unchanged, is exactly the ADR 0012 D1 " +
                "failure mode (declaration renamed, usage silently left dangling) reached via " +
                "dumb mode instead of the search cap",
            userBefore,
            userAfter,
        )
    }
}
