package simpli.fyi.plugins.typespec.refactoring

import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.lexer.TypeSpecLexerAdapter
import simpli.fyi.plugins.typespec.psi.TypeSpecDecStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecDecoratorReferenceHost
import java.io.File

/**
 * Plan 07 M6.5j acceptance (docs/plans/07-rename.md, "M6.5j -- dec/fn rename, rewriting
 * @decorator usages" -- Acceptance) plus both risks noted there.
 *
 * Fixtures live under `src/test/testData/rename/decorator/`.
 */
class TypeSpecDecoratorRenameTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun textOf(relativePath: String): String = File(testDataPath, relativePath).readText()

    // ---- acceptance 1: same-file dec declaration + @doc / @@doc usages -------------------

    fun testDec_sameFile_declarationAndBothUsageFormsRewritten() {
        myFixture.testRename(
            "rename/decorator/SameFile.tsp",
            "rename/decorator/SameFile_after.tsp",
            "describe",
        )
    }

    // ---- acceptance 2: dotted usage, only the final segment changes -----------------------

    fun testDec_dottedUsage_onlyFinalSegmentRewritten() {
        myFixture.testRename(
            "rename/decorator/Dotted.tsp",
            "rename/decorator/Dotted_after.tsp",
            "described",
        )
    }

    // ---- acceptance 3: cross-file usage reached through an import --------------------------

    fun testDec_crossFile_bothFilesRewritten() {
        myFixture.testRename(
            "rename/decorator/CrossFileUser.tsp",
            "rename/decorator/CrossFileUser_after.tsp",
            "describe",
            "rename/decorator/CrossFileDecl.tsp",
        )

        val declVirtual = myFixture.findFileInTempDir("rename/decorator/CrossFileDecl.tsp")
        assertNotNull("rename/decorator/CrossFileDecl.tsp must have been copied into the test project", declVirtual)
        val declPsi = PsiManager.getInstance(project).findFile(declVirtual!!)!!
        assertEquals(
            "the declaration file itself must also be rewritten",
            textOf("rename/decorator/CrossFileDecl_after.tsp"),
            declPsi.text,
        )
    }

    // ---- acceptance 4: renaming to an unrepresentable name refuses cleanly ----------------

    /**
     * "The escape check runs before any tree access, so a partial write here would mean that
     * ordering broke" (M6.5j risk note). Both the declaration file and a second, importing file
     * are asserted byte-identical -- not just the throw -- because a partial write could leave
     * the declaration renamed while the usage file (or vice-versa) is left dangling.
     */
    private fun assertUnrepresentableRenameRefused(newName: String) {
        val dir = "unrep-${System.nanoTime()}"
        val declFile = myFixture.addFileToProject(
            "$dir/decl.tsp",
            "extern dec doc(target: unknown, text: string);\n",
        )
        val userFile = myFixture.addFileToProject(
            "$dir/user.tsp",
            """
            import "./decl.tsp";

            @doc("x")
            model Foo {}
            """.trimIndent(),
        )
        val declBefore = declFile.text
        val userBefore = userFile.text

        val dec = PsiTreeUtil.findChildOfType(declFile, TypeSpecDecStatement::class.java)!!
        var thrown: Throwable? = null
        try {
            myFixture.renameElement(dec, newName)
        } catch (t: Throwable) {
            thrown = t
        }

        assertNotNull("renaming dec 'doc' to \"$newName\" must throw", thrown)
        assertTrue(
            "expected an IncorrectOperationException (found ${thrown?.javaClass}: ${thrown?.message})",
            thrown is IncorrectOperationException || thrown?.cause is IncorrectOperationException,
        )
        assertEquals("declaration file must be byte-identical after the refused rename", declBefore, declFile.text)
        assertEquals("usage file must be byte-identical after the refused rename", userBefore, userFile.text)
    }

    fun testDec_renameToKeyword_throwsAndLeavesFilesUnchanged() {
        assertUnrepresentableRenameRefused("model")
    }

    fun testDec_renameToNameWithSpace_throwsAndLeavesFilesUnchanged() {
        assertUnrepresentableRenameRefused("my name")
    }

    // ---- acceptance 5: fn declarations, same shape as acceptance 1 ------------------------

    fun testFn_sameFile_declarationAndBothUsageFormsRewritten() {
        myFixture.testRename(
            "rename/decorator/Fn.tsp",
            "rename/decorator/Fn_after.tsp",
            "describe",
        )
    }

    // ---- risk 1: a partially-typed decorator must not crash --------------------------------

    fun testPartiallyTyped_atSignAlone_yieldsNoReferencesAndDoesNotCrash() {
        val file = myFixture.configureByText("partial-at.tsp", "@\nmodel Foo {}")
        val host = PsiTreeUtil.findChildOfType(file, TypeSpecDecoratorReferenceHost::class.java)
        // Depending on how the parser recovers, the host itself may or may not exist; either
        // way, no reference must be produced and nothing must throw.
        val references: Array<PsiReference> = host?.references ?: PsiReference.EMPTY_ARRAY
        assertEquals("a bare '@' must yield no decorator references", 0, references.size)
    }

    fun testPartiallyTyped_trailingDot_yieldsNoReferencesAndDoesNotCrash() {
        val file = myFixture.configureByText("partial-dot.tsp", "@Foo.\nmodel Foo {}")
        val host = PsiTreeUtil.findChildOfType(file, TypeSpecDecoratorReferenceHost::class.java)
        val references: Array<PsiReference> = host?.references ?: PsiReference.EMPTY_ARRAY
        // "@Foo." still has one complete segment ("Foo"); the important thing is that the
        // trailing, unterminated dot does not throw or produce a bogus extra reference.
        assertTrue(
            "a trailing '.' with nothing after it must not crash; expected 0 or 1 references, got ${references.size}",
            references.size <= 1,
        )
    }

    // ---- risk 2 / re-lex property: the rewritten token lexes as exactly one token ---------

    /**
     * Same property [simpli.fyi.plugins.typespec.refactoring.TypeSpecNamesTest] pins for plain
     * identifiers (M6.5f acceptance 3), asserted here directly against the real lexer for the
     * decorator token produced by rename -- this is what makes the splice in
     * [simpli.fyi.plugins.typespec.resolve.TypeSpecDecoratorReference.handleElementRename] safe.
     */
    private fun assertLexesAsSingleDecoratorToken(text: String, expectAugment: Boolean) {
        val lexer = TypeSpecLexerAdapter()
        lexer.start(text)

        val expectedType = if (expectAugment) TypeSpecTokenTypes.AUGMENT_DECORATOR else TypeSpecTokenTypes.DECORATOR
        assertEquals("token type for \"$text\"", expectedType, lexer.tokenType)
        assertEquals("token start for \"$text\"", 0, lexer.tokenStart)
        assertEquals("token end for \"$text\"", text.length, lexer.tokenEnd)

        lexer.advance()
        assertNull("expected exactly one token for \"$text\", found a second", lexer.tokenType)
    }

    fun testRelex_decoratorTokenAfterRename_isExactlyOneDecoratorToken() {
        val file = myFixture.configureByText(
            "relex-decorator.tsp",
            """
            extern dec do<caret>c(target: unknown, text: string);

            @doc("x")
            model Foo {}
            """.trimIndent(),
        )
        val dec = PsiTreeUtil.findChildOfType(file, TypeSpecDecStatement::class.java)!!
        myFixture.renameElement(dec, "describe")

        val decoratorText = "@describe"
        val actualIndex = file.text.indexOf(decoratorText)
        assertTrue("expected \"$decoratorText\" to appear in the renamed file", actualIndex >= 0)
        assertLexesAsSingleDecoratorToken(decoratorText, expectAugment = false)
    }

    fun testRelex_augmentDecoratorTokenAfterRename_isExactlyOneAugmentDecoratorToken() {
        val file = myFixture.configureByText(
            "relex-augment.tsp",
            """
            extern dec do<caret>c(target: unknown, text: string);

            model Foo {}

            @@doc(Foo, "x");
            """.trimIndent(),
        )
        val dec = PsiTreeUtil.findChildOfType(file, TypeSpecDecStatement::class.java)!!
        myFixture.renameElement(dec, "describe")

        val decoratorText = "@@describe"
        val actualIndex = file.text.indexOf(decoratorText)
        assertTrue("expected \"$decoratorText\" to appear in the renamed file", actualIndex >= 0)
        assertLexesAsSingleDecoratorToken(decoratorText, expectAugment = true)
    }
}
