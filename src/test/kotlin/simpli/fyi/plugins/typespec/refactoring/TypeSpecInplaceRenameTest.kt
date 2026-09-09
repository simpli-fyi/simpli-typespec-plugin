package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import simpli.fyi.plugins.typespec.psi.TypeSpecDecStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecEnumMember
import simpli.fyi.plugins.typespec.psi.TypeSpecFnStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecInterfaceOperation
import simpli.fyi.plugins.typespec.psi.TypeSpecModelProperty
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecTemplateParameter
import simpli.fyi.plugins.typespec.psi.TypeSpecUnionVariant
import java.io.File

/**
 * Plan 07 M6.5k acceptance (docs/plans/07-rename.md, "M6.5k -- Selective in-place rename" --
 * Acceptance, corrected 2026-09-08 per ADR 0012 F14).
 *
 * F14 is the reason two different fixture APIs appear below: `renameElementAtCaretUsingHandler`
 * only resolves a handler and calls `invoke` -- for a *dialog* handler
 * ([PsiElementRenameHandler]) that is a real, blocking, end-to-end rename in headless test mode;
 * for [MemberInplaceRenameHandler] it only starts a live template and returns, so it is used
 * below **only** for handler-resolution/refusal assertions (cases 1, 5, 6, 7, 9). The end-to-end
 * cases (2, 3, 4) instead drive the template to completion with
 * [CodeInsightTestUtil.doInlineRename], which types the name and calls `gotoEnd`, triggering
 * `MemberInplaceRenamer.performRefactoringRename` -> `RenameProcessor`.
 */
class TypeSpecInplaceRenameTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun textOf(relativePath: String): String = File(testDataPath, relativePath).readText()

    /**
     * Mirrors the data the platform feeds [RenameHandlerRegistry] for a caret-based invocation
     * (project, editor, element, file) -- the same shape `renameElementAtCaretUsingHandler`
     * builds internally (ADR 0012 F14), reconstructed here so the *resolved handler* can be
     * asserted directly rather than only its side effect.
     */
    private fun resolvedHandler(element: PsiElement): RenameHandler? {
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_ELEMENT, element)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()
        return RenameHandlerRegistry.getInstance().getRenameHandler(dataContext)
    }

    private fun isMemberInplaceRenameAvailable(element: PsiElement): Boolean =
        TypeSpecRefactoringSupportProvider().isMemberInplaceRenameAvailable(element, null)

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
            "file text must be unchanged after a refused/vetoed rename attempt",
            originalText,
            file.text,
        )
    }

    // ---- acceptance 1: the right handler is chosen ---------------------------------------

    fun test1_rightHandlerChosen_forModel() {
        myFixture.configureByText("Model1.tsp", "model F<caret>oo {}")
        val model = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecModelStatement::class.java)!!

        val handler = resolvedHandler(model)

        assertTrue(
            "expected MemberInplaceRenameHandler for a caret on a bare model name, got " +
                "${handler?.javaClass}",
            handler is MemberInplaceRenameHandler,
        )
        assertFalse("must not be PsiElementRenameHandler", handler is PsiElementRenameHandler)
        assertFalse(
            "must not be a bare VariableInplaceRenameHandler (F10b: if another plugin's " +
                "renameHandler ever starts reporting isRenaming for .tsp, the dedup branch drops " +
                "ours and the resolved handler silently becomes this one instead)",
            handler?.javaClass == VariableInplaceRenameHandler::class.java,
        )
    }

    // ---- acceptance 2: in-place renames correctly, end to end, for each of the seven kinds

    private fun assertInplaceRenameEndToEnd(before: String, after: String, newName: String) {
        myFixture.configureByText("Inplace-${System.nanoTime()}.tsp", before)
        CodeInsightTestUtil.doInlineRename(MemberInplaceRenameHandler(), newName, myFixture)
        assertEquals(after, myFixture.file.text)
    }

    fun test2_model_declarationAndUsageRewritten() {
        assertInplaceRenameEndToEnd(
            "model F<caret>oo {}\n\nalias A = Foo;\n",
            "model Bar {}\n\nalias A = Bar;\n",
            "Bar",
        )
    }

    fun test2_op_declarationAndUsageRewritten() {
        assertInplaceRenameEndToEnd(
            "op F<caret>oo(): void;\n\nop Baz is Foo;\n",
            "op Bar(): void;\n\nop Baz is Bar;\n",
            "Bar",
        )
    }

    fun test2_interface_declarationAndUsageRewritten() {
        assertInplaceRenameEndToEnd(
            "interface F<caret>oo {\n  m(): void;\n}\n\nalias A = Foo;\n",
            "interface Bar {\n  m(): void;\n}\n\nalias A = Bar;\n",
            "Bar",
        )
    }

    fun test2_enum_declarationAndUsageRewritten() {
        assertInplaceRenameEndToEnd(
            "enum F<caret>oo {\n  A: \"a\"\n}\n\nalias A2 = Foo;\n",
            "enum Bar {\n  A: \"a\"\n}\n\nalias A2 = Bar;\n",
            "Bar",
        )
    }

    fun test2_union_declarationAndUsageRewritten() {
        assertInplaceRenameEndToEnd(
            "union F<caret>oo {\n  a: string\n}\n\nalias A = Foo;\n",
            "union Bar {\n  a: string\n}\n\nalias A = Bar;\n",
            "Bar",
        )
    }

    fun test2_alias_declarationAndUsageRewritten() {
        assertInplaceRenameEndToEnd(
            "alias F<caret>oo = string;\n\nalias A = Foo;\n",
            "alias Bar = string;\n\nalias A = Bar;\n",
            "Bar",
        )
    }

    fun test2_scalar_declarationAndUsageRewritten() {
        assertInplaceRenameEndToEnd(
            "scalar F<caret>oo extends string;\n\nalias A = Foo;\n",
            "scalar Bar extends string;\n\nalias A = Bar;\n",
            "Bar",
        )
    }

    // ---- acceptance 3: escaping still applies on the inline path -------------------------

    fun test3_renameToKeyword_backticksDeclarationAndReparsesCleanly() {
        assertInplaceRenameEndToEnd(
            "model F<caret>oo {}\n",
            "model `model` {}\n",
            "model",
        )
        assertNull(
            "renamed file must re-parse with no PsiErrorElement",
            PsiTreeUtil.findChildOfType(myFixture.file, PsiErrorElement::class.java),
        )
    }

    // ---- acceptance 4: illegal name is a no-op, not a write -------------------------------

    fun test4_illegalName_isNoOp_fileByteIdentical() {
        myFixture.configureByText("Illegal.tsp", "model F<caret>oo {}\n")
        val before = myFixture.file.text
        CodeInsightTestUtil.doInlineRename(MemberInplaceRenameHandler(), "a`b", myFixture)
        assertEquals(
            "an illegal name (rejected by TypeSpecNamesValidator.isIdentifier) must be a no-op, " +
                "not a write",
            before,
            myFixture.file.text,
        )
    }

    // ---- acceptance 5: dec/fn fall back to the dialog -------------------------------------

    fun test5_dec_fallsBackToDialog() {
        myFixture.configureByFile("rename/decorator/SameFile.tsp")
        val dec = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecDecStatement::class.java)!!

        assertFalse("dec must not be member-inplace-renameable", isMemberInplaceRenameAvailable(dec))
        val handler = resolvedHandler(dec)
        assertFalse("resolved handler for dec must not be in-place", handler is MemberInplaceRenameHandler)

        myFixture.testRename("rename/decorator/SameFile.tsp", "rename/decorator/SameFile_after.tsp", "describe")
    }

    fun test5_fn_fallsBackToDialog() {
        myFixture.configureByFile("rename/decorator/Fn.tsp")
        val fn = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecFnStatement::class.java)!!

        assertFalse("fn must not be member-inplace-renameable", isMemberInplaceRenameAvailable(fn))
        val handler = resolvedHandler(fn)
        assertFalse("resolved handler for fn must not be in-place", handler is MemberInplaceRenameHandler)

        myFixture.testRename("rename/decorator/Fn.tsp", "rename/decorator/Fn_after.tsp", "describe")
    }

    // ---- acceptance 6: namespace falls back to the dialog ---------------------------------

    fun test6_namespace_fallsBackToDialog() {
        myFixture.configureByFile("rename/namespace/Dotted.tsp")
        val namespace = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecNamespaceStatement::class.java)!!

        assertFalse(
            "namespace must not be member-inplace-renameable -- its blast radius (every " +
                "declaring file) deserves the dialog's Preview Usages",
            isMemberInplaceRenameAvailable(namespace),
        )

        val offset = myFixture.file.text.indexOf("A.B.C;") + "A.B.".length
        myFixture.editor.caretModel.moveToOffset(offset)
        val handler = resolvedHandler(namespace)
        assertFalse("resolved handler for namespace must not be in-place", handler is MemberInplaceRenameHandler)

        // Dialog-path rename still works (M6.5i behaviour, unregressed).
        myFixture.renameElementAtCaretUsingHandler("Z")
        assertEquals(
            textOf("rename/namespace/Dotted_afterZ.tsp"),
            myFixture.file.text,
        )
    }

    // ---- acceptance 7: the veto is not bypassed by the in-place door ----------------------

    private fun assertVetoedAndInplaceUnavailable(fileName: String, before: String, elementFinder: (PsiElement) -> PsiElement) {
        val file = myFixture.configureByText(fileName, before)
        val originalText = file.text
        val element = elementFinder(myFixture.file)

        assertFalse(
            "vetoed element must not be member-inplace-renameable -- if it is, isVetoed is not " +
                "consulted on the inline path (ADR 0012 F10c) and the predicate in " +
                "isInplaceRenameAvailable is the only guard, which is exactly why it is there",
            isMemberInplaceRenameAvailable(element),
        )

        try {
            myFixture.renameElementAtCaretUsingHandler("Renamed")
        } catch (_: Throwable) {
            // refusal expected
        }
        assertEquals(
            "file text must be unchanged after a vetoed rename attempt via the in-place door",
            originalText,
            file.text,
        )
    }

    // NOTE: `prop` is itself a reserved TypeSpec keyword (TypeSpecKeywords.ALL) -- using it as
    // the property *name* here (as the existing rename/Member.tsp fixture does) lexes as
    // KEYWORD rather than IDENTIFIER, so the model body fails to parse a TypeSpecModelProperty
    // at all and PsiTreeUtil.findChildOfType returns null. Use a non-keyword field name.
    fun test7_modelProperty_vetoedAndInplaceUnavailable() {
        assertVetoedAndInplaceUnavailable(
            "veto-property.tsp",
            "model M {\n  fie<caret>ld: string;\n}",
        ) { f -> PsiTreeUtil.findChildOfType(f, TypeSpecModelProperty::class.java)!! }
    }

    fun test7_enumMember_vetoedAndInplaceUnavailable() {
        assertVetoedAndInplaceUnavailable(
            "veto-enum.tsp",
            "enum Color {\n  Re<caret>d: \"red\"\n}",
        ) { f -> PsiTreeUtil.findChildOfType(f, TypeSpecEnumMember::class.java)!! }
    }

    fun test7_unionVariant_vetoedAndInplaceUnavailable() {
        assertVetoedAndInplaceUnavailable(
            "veto-union.tsp",
            "union Shape {\n  cir<caret>cle: Circle\n}",
        ) { f -> PsiTreeUtil.findChildOfType(f, TypeSpecUnionVariant::class.java)!! }
    }

    fun test7_interfaceOperation_vetoedAndInplaceUnavailable() {
        assertVetoedAndInplaceUnavailable(
            "veto-interface.tsp",
            "interface Store {\n  g<caret>et(id: string): string;\n}",
        ) { f -> PsiTreeUtil.findChildOfType(f, TypeSpecInterfaceOperation::class.java)!! }
    }

    fun test7_templateParameter_vetoedAndInplaceUnavailable() {
        assertVetoedAndInplaceUnavailable(
            "veto-template.tsp",
            "model Foo<T<caret>Param> { x: TParam; }",
        ) { f -> PsiTreeUtil.findChildOfType(f, TypeSpecTemplateParameter::class.java)!! }
    }

    fun test7_nodeModulesDeclaration_vetoedAndInplaceUnavailable() {
        myFixture.configureByFile("rename/NodeModules/node_modules/@x/y/lib/main.tsp")
        val model = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecModelStatement::class.java)!!
        val before = myFixture.file.text

        assertFalse(
            "a node_modules declaration must not be member-inplace-renameable",
            isMemberInplaceRenameAvailable(model),
        )
        try {
            myFixture.renameElementAtCaretUsingHandler("Renamed")
        } catch (_: Throwable) {
            // refusal expected
        }
        assertEquals("node_modules file text must be unchanged", before, myFixture.file.text)
    }

    fun test7_backtickedNonIndexKeyableName_vetoedAndInplaceUnavailable() {
        assertVetoedAndInplaceUnavailable(
            "veto-space-name.tsp",
            "model `my na<caret>me` {}",
        ) { f -> PsiTreeUtil.findChildOfType(f, TypeSpecModelStatement::class.java)!! }
    }

    // ---- acceptance 8: backtick gate -- both rows renameable, but not in-place -----------

    fun test8_backtickedLegalName_notInplace_dialogDropsBackticks() {
        myFixture.configureByText("Backtick8a.tsp", "model `Fo<caret>o` {}")
        val model = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecModelStatement::class.java)!!
        assertFalse(
            "`Foo` is a legal, redundantly-quoted name -- renameable via the dialog, but not in-place",
            isMemberInplaceRenameAvailable(model),
        )

        myFixture.renameElementAtCaretUsingHandler("Bar")
        assertEquals("model Bar {}", myFixture.file.text)
    }

    fun test8_backtickedKeywordName_notInplace_dialogKeepsBackticks() {
        myFixture.testRename("rename/Backticked.tsp", "rename/Backticked_after.tsp", "Foo")
    }

    fun test8_backtickedKeywordName_notMemberInplaceRenameable() {
        val file = myFixture.configureByText("backticked-model.tsp", "model `model` {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecModelStatement::class.java)!!
        assertFalse(
            "`model` must stay dialog-only in-place (D7 un-vetoes it for the dialog, but the " +
                "in-place gate is a distinct question about how the name is written in the file)",
            isMemberInplaceRenameAvailable(model),
        )
    }

    // ---- acceptance 9: F11, asserted on the in-place path too -----------------------------

    fun test9_caretOnMiddleSegment_stillRefusedWithInplaceAvailable() {
        myFixture.configureByFile("rename/namespace/Dotted.tsp")
        val before = myFixture.file.text
        val offset = myFixture.file.text.indexOf("A.B.C;") + "A.".length
        myFixture.editor.caretModel.moveToOffset(offset)

        var thrown: Throwable? = null
        try {
            myFixture.renameElementAtCaretUsingHandler("Z")
        } catch (t: Throwable) {
            thrown = t
        }

        assertEquals(
            "caret on B must still be refused with in-place available (M6.5i asserted this on " +
                "the dialog path; in-place is a second door onto the same element)",
            before,
            myFixture.file.text,
        )
        @Suppress("UNUSED_EXPRESSION")
        thrown
    }

    // ---- acceptance 10: regression -- covered by running the full suite; see report ------

    // ---- F14, unverified point: in-place vs dialog on a CROSS-FILE usage -----------------

    /**
     * ADR 0012 F14 leaves open whether `MemberInplaceRenamer`'s rename processor uses the same
     * search flags as the dialog path -- i.e. whether the two paths produce byte-identical
     * results. All of cases 1-9 above are same-file. This drives [CodeInsightTestUtil]'s
     * inline-rename machinery with the caret on the *declaration*, in one file, while a second,
     * already-added project file holds the only usage (reached through an `import`) -- the same
     * shape as [TypeSpecRenameTest.testCrossFile_bothFilesRewritten]'s dialog-path fixtures
     * (`rename/CrossFile.tsp` / `rename/CrossFileUser.tsp`) -- and asserts both files end up
     * exactly as that dialog-path test leaves them.
     *
     * Measured: [CodeInsightTestUtil.doInlineRename] **can** drive the fixture across files --
     * it only requires the *current* editor's caret to sit on the renameable element; usages in
     * other already-added project files are found by the ordinary `RenameProcessor` the same as
     * on the dialog path, because (M6.5k approach point 5) nothing about the search/usage-finding
     * step differs between the two paths -- only how the commit is triggered. Nothing here is
     * faked or skipped.
     */
    fun testF14_crossFileUsage_inplaceMatchesDialogPath() {
        val declFile = myFixture.addFileToProject("rename/inplace/CrossFile.tsp", "model Foo {}\n")
        myFixture.addFileToProject(
            "rename/inplace/CrossFileUser.tsp",
            """
            import "./CrossFile.tsp";

            model Uses {
              a: Foo;
            }

            """.trimIndent() + "\n",
        )

        myFixture.configureFromExistingVirtualFile(declFile.virtualFile)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("Foo") + 1)
        CodeInsightTestUtil.doInlineRename(MemberInplaceRenameHandler(), "Bar", myFixture)

        val declPsi = PsiManager.getInstance(project).findFile(declFile.virtualFile)!!
        val userVf = myFixture.findFileInTempDir("rename/inplace/CrossFileUser.tsp")!!
        val userPsi = PsiManager.getInstance(project).findFile(userVf)!!

        assertEquals(
            "the declaration file must match what the dialog path (testRename) produces for " +
                "the same rename -- see rename/CrossFile_after.tsp",
            "model Bar {}\n",
            declPsi.text,
        )
        assertEquals(
            "the cross-file usage must match what the dialog path produces -- see " +
                "rename/CrossFileUser_after.tsp",
            """
            import "./CrossFile.tsp";

            model Uses {
              a: Bar;
            }

            """.trimIndent() + "\n",
            userPsi.text,
        )
    }
}
