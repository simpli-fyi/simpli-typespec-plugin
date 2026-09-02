package simpli.fyi.plugins.typespec.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.codeInsight.editorActions.TypedHandler
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes

/**
 * M4 acceptance: [TypeSpecQuoteHandler].
 */
class TypeSpecQuoteHandlerTest : BasePlatformTestCase() {

    // ---- registration resolves for the language ------------------------------------

    fun testQuoteHandlerIsRegisteredForTypeSpecLanguage() {
        // TypedHandler.getQuoteHandler(PsiFile, Editor) is the real platform entry point
        // TypedHandler.handleQuote drives. It resolves via viewProvider.baseLanguage, which
        // is TypeSpecLanguage (ADR 0003 F1), and now — since TypeSpecParserDefinition (ADR
        // 0005) landed — psiFile.language agrees too.
        val psiFile = myFixture.configureByText("a.tsp", "\"\"")
        val handler = TypedHandler.getQuoteHandler(psiFile, myFixture.editor)
        assertNotNull("No QuoteHandler registered for TypeSpecLanguage in plugin.xml", handler)
        assertTrue(
            "Expected a TypeSpecQuoteHandler but got ${handler?.javaClass}",
            handler is TypeSpecQuoteHandler,
        )
    }

    // ---- the token set passed to the base class is exactly STRING/MULTILINE_STRING ---

    fun testLiteralTokenSetIsStringAndMultilineString() {
        val handler = TypeSpecQuoteHandler()
        val field = SimpleTokenSetQuoteHandler::class.java.getDeclaredField("myLiteralTokenSet")
        field.isAccessible = true
        val tokenSet = field.get(handler) as TokenSet

        assertEquals(
            setOf(TypeSpecTokenTypes.STRING, TypeSpecTokenTypes.MULTILINE_STRING),
            tokenSet.types.toSet(),
        )
    }
}
