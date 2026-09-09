package simpli.fyi.plugins.typespec.refactoring

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ProcessingContext
import simpli.fyi.plugins.typespec.psi.TypeSpecDecStatement

/**
 * Plan 07 M6.5j risk note: [TypeSpecDecoratorRenameInputValidator] must be an ACCEPT gate for
 * every legal bare identifier a `dec`/`fn` may be renamed to, not merely a REJECT gate for the
 * unrepresentable ones. `RenameUtil.isValidName` gives a matching `RenameInputValidator`
 * precedence over `NamesValidator` *entirely* (ADR 0012 F2), so a validator that only rejects
 * would silently narrow what these declarations can be renamed to and nothing else in the suite
 * would notice -- this file is that notice.
 */
class TypeSpecDecoratorRenameInputValidatorTest : BasePlatformTestCase() {

    private fun decStatement(): TypeSpecDecStatement {
        val file = myFixture.configureByText(
            "input-validator-${System.nanoTime()}.tsp",
            "extern dec doc(target: unknown, text: string);",
        )
        return PsiTreeUtil.findChildOfType(file, TypeSpecDecStatement::class.java)!!
    }

    private val validator = TypeSpecDecoratorRenameInputValidator()

    private fun isInputValidDirect(name: String): Boolean =
        validator.isInputValid(name, decStatement(), ProcessingContext())

    // ---- getPattern(): must match TypeSpecDecStatement / TypeSpecFnStatement --------------

    fun testPattern_matchesDecStatement() {
        assertTrue(validator.getPattern().accepts(decStatement()))
    }

    fun testPattern_doesNotMatchModelStatement() {
        val file = myFixture.configureByText("pattern-model.tsp", "model Foo {}")
        val model = PsiTreeUtil.findChildOfType(file, simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement::class.java)!!
        assertFalse(validator.getPattern().accepts(model))
    }

    // ---- the ACCEPT direction: every legal bare identifier ---------------------------------

    fun testAccepts_everyLegalBareIdentifier() {
        val legal = listOf("Foo", "_x", "a\$b", "doc2", "describe", "_", "a1")
        for (name in legal) {
            assertTrue("expected \"$name\" to be accepted", isInputValidDirect(name))
        }
    }

    /**
     * `isInputValid` trims before calling [simpli.fyi.plugins.typespec.psi.TypeSpecNames.isBareIdentifier],
     * mirroring `RenameUtil.isValidName`'s own `.trim()` (ADR 0012 F2/D2) so the dialog's
     * validation and [simpli.fyi.plugins.typespec.psi.TypeSpecNames.escape]'s write-time
     * `.trim()` cannot disagree. `"  Foo  "` is therefore accepted here for exactly the reason
     * [TypeSpecNamesValidatorTest.testIsIdentifier_trueForRepresentableNames] already accepts it
     * — not a gap, the mirrored behaviour working as designed.
     */
    fun testAccepts_untrimmedButOtherwiseLegalName() {
        assertTrue("expected \"  Foo  \" to be accepted (trimmed internally)", isInputValidDirect("  Foo  "))
    }

    // ---- the REJECT direction: keywords, whitespace, backticks, illegal starts, empty -----

    fun testRejects_keywordsAndOtherIllegalNames() {
        val illegal = listOf("model", "namespace", "op", "my name", "`model`", "9lives", "")
        for (name in illegal) {
            assertFalse("expected \"$name\" to be rejected", isInputValidDirect(name))
        }
    }

    // ---- the pipeline: RenameUtil.isValidName must reflect this validator, not NamesValidator

    fun testPipeline_renameUtilIsValidName_reflectsThisValidatorNotNamesValidator() {
        val dec = decStatement()

        // "model" IS a valid TypeSpec identifier by NamesValidator's lights (any name is
        // representable via backticks for a plain declaration) but must be false here because
        // this RenameInputValidator takes precedence entirely (ADR 0012 F2).
        assertFalse(
            "RenameUtil.isValidName must be false for a dec renamed to 'model' -- if this is " +
                "true, either the validator regressed or it is no longer given precedence",
            RenameUtil.isValidName(project, dec, "model"),
        )
        assertTrue(
            "RenameUtil.isValidName must be true for a dec renamed to a legal bare identifier",
            RenameUtil.isValidName(project, dec, "describe"),
        )
    }
}
