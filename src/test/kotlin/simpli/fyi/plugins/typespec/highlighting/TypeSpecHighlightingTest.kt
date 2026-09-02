package simpli.fyi.plugins.typespec.highlighting

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * M3 acceptance: exercises the *real* editor-highlighter path
 * (`EditorHighlighterFactory.createEditorHighlighter`) production code takes when a `.tsp`
 * file is opened, as opposed to unit-testing [TypeSpecSyntaxHighlighter] in isolation.
 *
 * Uses the `VirtualFile` overload deliberately — it is the exact overload the IDE calls
 * (`EditorHighlighterFactoryImpl.kt:39`). `setText` on a highlighter created this way is
 * null-editor-safe (`LexerEditorHighlighter.java:482` guards the editor touch), so no editor
 * needs to be opened.
 */
class TypeSpecHighlightingTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData"

    private data class HighlightedToken(val tokenType: IElementType, val text: String, val keys: List<TextAttributesKey>)

    private fun highlightAll(text: String): List<HighlightedToken> {
        val psiFile = myFixture.configureByText("a.tsp", text)
        val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
            psiFile.virtualFile,
            EditorColorsManager.getInstance().globalScheme,
            project,
        )
        highlighter.setText(text)
        val result = mutableListOf<HighlightedToken>()
        val it = highlighter.createIterator(0)
        while (!it.atEnd()) {
            result.add(
                HighlightedToken(
                    it.tokenType,
                    text.substring(it.start, it.end),
                    it.textAttributesKeys.toList(),
                )
            )
            it.advance()
        }
        return result
    }

    fun testEditorHighlighterIsProductionTypeSpecHighlighter() {
        val psiFile = myFixture.configureByText("a.tsp", "model Widget {}")
        val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(
            psiFile.virtualFile,
            EditorColorsManager.getInstance().globalScheme,
            project,
        )
        // A LexerEditorHighlighter wrapping our lexer; smoke-check it actually tokenizes TypeSpec
        // (a PlainTextFileType fallback would still produce iterator output, but with a single
        // WHITE_SPACE/plain token covering everything instead of KEYWORD/IDENTIFIER/braces).
        highlighter.setText("model Widget {}")
        val it = highlighter.createIterator(0)
        assertFalse(it.atEnd())
        assertEquals("model", "model Widget {}".substring(it.start, it.end))
    }

    fun testKeywordsIdentifiersAndBraces() {
        val tokens = highlightAll("model Widget {}")
        assertEquals(
            listOf("model", "Widget", "{", "}"),
            tokens.filter { it.tokenType != TokenType.WHITE_SPACE }.map { it.text }
        )
        val nonWs = tokens.filter { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals(listOf(TypeSpecColors.KEYWORD), nonWs[0].keys)
        assertEquals(listOf(TypeSpecColors.IDENTIFIER), nonWs[1].keys)
        assertEquals(listOf(TypeSpecColors.BRACES), nonWs[2].keys)
        assertEquals(listOf(TypeSpecColors.BRACES), nonWs[3].keys)
    }

    fun testStringsAndEscapes() {
        val tokens = highlightAll("\"a\\nb\\qc\"").filter { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals(listOf(TypeSpecColors.STRING), tokens[0].keys) // "a
        assertEquals(listOf(TypeSpecColors.VALID_ESCAPE), tokens[1].keys) // \n
        assertEquals(listOf(TypeSpecColors.STRING), tokens[2].keys) // b
        assertEquals(listOf(TypeSpecColors.INVALID_ESCAPE), tokens[3].keys) // \q
        assertEquals(listOf(TypeSpecColors.STRING), tokens[4].keys) // c"
    }

    fun testNumbers() {
        val tokens = highlightAll("0 42 1.5 1.5e-3 0x1f 0b1010")
            .filter { it.tokenType != TokenType.WHITE_SPACE && it.text != "-" }
        for (t in tokens) {
            assertEquals("token '${t.text}'", listOf(TypeSpecColors.NUMBER), t.keys)
        }
    }

    fun testAllThreeCommentKinds() {
        val lineComment = highlightAll("// x").first { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals(listOf(TypeSpecColors.LINE_COMMENT), lineComment.keys)

        val blockComment = highlightAll("/* a */").first { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals(listOf(TypeSpecColors.BLOCK_COMMENT), blockComment.keys)

        val docComment = highlightAll("/** doc */").first { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals(listOf(TypeSpecColors.DOC_COMMENT), docComment.keys)
    }

    fun testDecorators() {
        val decorator = highlightAll("@doc").first { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals("@doc", decorator.text)
        assertEquals(listOf(TypeSpecColors.DECORATOR), decorator.keys)

        val augment = highlightAll("@@doc").first { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals("@@doc", augment.text)
        assertEquals(listOf(TypeSpecColors.DECORATOR), augment.keys)
    }

    fun testDirective() {
        val directive = highlightAll("#suppress \"x\"").first { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals("#suppress", directive.text)
        assertEquals(listOf(TypeSpecColors.DIRECTIVE), directive.keys)
    }

    fun testHashBraceAndHashBracket() {
        val objTokens = highlightAll("#{ a: 1 }").filter { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals("#{", objTokens[0].text)
        assertEquals(listOf(TypeSpecColors.BRACES), objTokens[0].keys)

        val arrTokens = highlightAll("#[1, 2]").filter { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals("#[", arrTokens[0].text)
        assertEquals(listOf(TypeSpecColors.BRACKETS), arrTokens[0].keys)
    }

    fun testPunctuationAndOperatorClasses() {
        val cases = mapOf(
            "(" to TypeSpecColors.PARENTHESES,
            ")" to TypeSpecColors.PARENTHESES,
            "[" to TypeSpecColors.BRACKETS,
            "]" to TypeSpecColors.BRACKETS,
            ";" to TypeSpecColors.SEMICOLON,
            "," to TypeSpecColors.COMMA,
            "." to TypeSpecColors.DOT,
            "<=" to TypeSpecColors.OPERATOR,
            ">=" to TypeSpecColors.OPERATOR,
            "==" to TypeSpecColors.OPERATOR,
            "!=" to TypeSpecColors.OPERATOR,
            "=>" to TypeSpecColors.OPERATOR,
            "&&" to TypeSpecColors.OPERATOR,
            "||" to TypeSpecColors.OPERATOR,
            "::" to TypeSpecColors.OPERATOR,
            "?" to TypeSpecColors.OPERATOR,
            "|" to TypeSpecColors.OPERATOR,
            "&" to TypeSpecColors.OPERATOR,
            "=" to TypeSpecColors.OPERATOR,
            ":" to TypeSpecColors.OPERATOR,
            "..." to TypeSpecColors.OPERATOR,
        )
        for ((text, expectedKey) in cases) {
            val token = highlightAll(text).first { it.tokenType != TokenType.WHITE_SPACE }
            assertEquals("token '$text'", text, token.text)
            assertEquals("token '$text'", listOf(expectedKey), token.keys)
        }
    }

    fun testBadCharacterIsHighlighted() {
        // "€" (euro sign) is NOT a valid probe here: JFlex's [:jletter:] treats currency
        // symbols as Java identifier-start characters, so it actually lexes as IDENTIFIER (see
        // the documented divergence in TypeSpecLexerTest.testBadCharacter and M3 plan risk #2).
        // "\u001C" (File Separator) is neither identifier-start nor identifier-part.
        val badChar = "\u001C"
        val token = highlightAll(badChar).first { it.tokenType != TokenType.WHITE_SPACE }
        assertEquals(listOf(TypeSpecColors.BAD_CHARACTER), token.keys)
    }

    fun testKitchenSinkFixtureHighlightsWithNoBadCharacters() {
        val text = java.io.File(testDataPath, "lexer/kitchen-sink.tsp").readText()
        val tokens = highlightAll(text)
        val badCharacters = tokens.filter { it.tokenType == TokenType.BAD_CHARACTER }
        assertTrue(
            "kitchen-sink.tsp produced BAD_CHARACTER tokens: ${badCharacters.map { it.text }}",
            badCharacters.isEmpty()
        )
        assertTrue(tokens.isNotEmpty())
    }
}
