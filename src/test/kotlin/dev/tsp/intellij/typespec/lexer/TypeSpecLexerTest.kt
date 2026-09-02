package dev.tsp.intellij.typespec.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LexerTestCase
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.AMP
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.AMP_AMP
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.ARROW
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.AUGMENT_DECORATOR
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.BAR
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.BAR_BAR
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.BLOCK_COMMENT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.COLON_COLON
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.DECORATOR
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.DIRECTIVE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.DOC_COMMENT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.DOT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.ELLIPSIS
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.EQ_EQ
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.GE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.HASH_BRACE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.HASH_BRACKET
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.IDENTIFIER
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.INVALID_ESCAPE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.KEYWORD
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.LE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.LINE_COMMENT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.MINUS
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.MULTILINE_STRING
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.NE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.NUMBER
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.QUESTION
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.STRING
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.VALID_ESCAPE
import java.io.File

/**
 * M2 acceptance: drives the real [TypeSpecLexerAdapter] over input and asserts the actual
 * token stream (type + text), not just that the code compiles.
 */
class TypeSpecLexerTest : LexerTestCase() {

    override fun createLexer() = TypeSpecLexerAdapter()
    override fun getDirPath() = "" // unused; all expectations are inline

    // ---- helpers ------------------------------------------------------------

    /** Lexes [text] and returns the (tokenType, tokenText) sequence, EOF excluded. */
    private fun tokenize(text: String): List<Pair<IElementType?, String>> {
        val lexer = createLexer()
        lexer.start(text)
        val result = mutableListOf<Pair<IElementType?, String>>()
        while (lexer.tokenType != null) {
            result.add(lexer.tokenType to lexer.tokenText)
            lexer.advance()
        }
        return result
    }

    private fun assertTokens(text: String, vararg expected: Pair<IElementType?, String>) {
        val actual = tokenize(text)
        assertEquals(expected.toList(), actual)
    }

    private fun assertTokenTypes(text: String, vararg expected: IElementType) {
        val actual = tokenize(text).map { it.first }
        assertEquals(expected.toList(), actual)
    }

    // ---- keywords -------------------------------------------------------------

    fun testKeywords() {
        val words = "model op interface enum union alias scalar dec fn extern const init"
        val tokens = tokenize(words).filter { it.first != TokenType.WHITE_SPACE }
        assertTrue(tokens.isNotEmpty())
        tokens.forEach { (type, text) -> assertEquals("keyword '$text'", KEYWORD, type) }
    }

    fun testReservedKeywords() {
        val tokens = tokenize("struct trait macro satisfies").filter { it.first != TokenType.WHITE_SPACE }
        tokens.forEach { (type, text) -> assertEquals("reserved keyword '$text'", KEYWORD, type) }
    }

    fun testNullIsIdentifierNotKeyword() {
        assertTokens("null", IDENTIFIER to "null")
    }

    fun testBooleansAreKeywords() {
        assertTokenTypes("true false", KEYWORD, TokenType.WHITE_SPACE, KEYWORD)
    }

    // ---- identifiers ------------------------------------------------------------

    fun testIdentifiers() {
        assertTokenTypes(
            "Widget foo_bar \$x",
            IDENTIFIER, TokenType.WHITE_SPACE, IDENTIFIER, TokenType.WHITE_SPACE, IDENTIFIER,
        )
    }

    fun testBacktickIdentifier() {
        assertTokens("`if`", IDENTIFIER to "`if`")
    }

    fun testUnterminatedBacktickIdentifier() {
        val tokens = tokenize("`if")
        assertEquals(1, tokens.size)
        assertEquals(IDENTIFIER, tokens[0].first)
        assertEquals("`if", tokens[0].second)
    }

    // ---- strings ------------------------------------------------------------

    fun testSimpleString() {
        assertTokens("\"hello\"", STRING to "\"hello\"")
    }

    fun testStringEscapes() {
        assertTokens(
            "\"a\\nb\\qc\"",
            STRING to "\"a",
            VALID_ESCAPE to "\\n",
            STRING to "b",
            INVALID_ESCAPE to "\\q",
            STRING to "c\"",
        )
    }

    fun testTemplateInterpolationIsOpaque() {
        // ADR 0001: the whole literal, including ${...}, is one STRING token.
        assertTokens("\"\${a}\"", STRING to "\"\${a}\"")
    }

    fun testUnterminatedString() {
        val tokens = tokenize("\"abc")
        assertEquals(1, tokens.size)
        assertEquals(STRING, tokens[0].first)
        assertEquals("\"abc", tokens[0].second)
    }

    fun testTripleQuotedString() {
        assertTokens("\"\"\"a\n\"b\"\nc\"\"\"", MULTILINE_STRING to "\"\"\"a\n\"b\"\nc\"\"\"")
    }

    fun testUnterminatedTripleQuotedString() {
        val tokens = tokenize("\"\"\"abc")
        assertEquals(1, tokens.size)
        assertEquals(MULTILINE_STRING, tokens[0].first)
        assertEquals("\"\"\"abc", tokens[0].second)
    }

    // ---- numbers ------------------------------------------------------------

    fun testNumbers() {
        for (n in listOf("0", "42", "1.5", "1.5e-3", "0x1f", "0b1010")) {
            val tokens = tokenize(n)
            assertEquals("number '$n'", 1, tokens.size)
            assertEquals("number '$n'", NUMBER, tokens[0].first)
            assertEquals("number '$n'", n, tokens[0].second)
        }
    }

    fun testSignedNumberDoesNotFold() {
        // By design (ADR-recorded in the .flex file): -1 is MINUS, NUMBER, not a signed literal.
        assertTokens("-1", MINUS to "-", NUMBER to "1")
    }

    fun testUppercaseExponentIsNotFolded() {
        // Scanner ground truth: exponent marker is lowercase `e` only.
        assertTokens("1E5", NUMBER to "1", IDENTIFIER to "E5")
    }

    // ---- comments ------------------------------------------------------------

    fun testLineComment() {
        assertTokens("// x", LINE_COMMENT to "// x")
    }

    fun testBlockComment() {
        assertTokens("/* a * b */", BLOCK_COMMENT to "/* a * b */")
    }

    fun testEmptyBlockCommentIsNotDoc() {
        assertTokens("/**/", BLOCK_COMMENT to "/**/")
    }

    fun testDocComment() {
        assertTokens("/** @param x */", DOC_COMMENT to "/** @param x */")
    }

    fun testUnterminatedBlockComment() {
        val tokens = tokenize("/* abc")
        assertEquals(1, tokens.size)
        assertEquals(BLOCK_COMMENT, tokens[0].first)
        assertEquals("/* abc", tokens[0].second)
    }

    fun testUnterminatedDocComment() {
        val tokens = tokenize("/** abc")
        assertEquals(1, tokens.size)
        assertEquals(DOC_COMMENT, tokens[0].first)
        assertEquals("/** abc", tokens[0].second)
    }

    // ---- decorators & directives ------------------------------------------------------------

    fun testDecorator() {
        assertTokens("@doc", DECORATOR to "@doc")
    }

    fun testQualifiedDecorator() {
        assertTokens("@Http.route", DECORATOR to "@Http.route")
    }

    fun testAugmentDecoratorIsSingleToken() {
        assertTokens("@@doc", AUGMENT_DECORATOR to "@@doc")
    }

    fun testDirectiveWithStringArgs() {
        assertTokens(
            "#suppress \"x\" \"y\"",
            DIRECTIVE to "#suppress",
            TokenType.WHITE_SPACE to " ",
            STRING to "\"x\"",
            TokenType.WHITE_SPACE to " ",
            STRING to "\"y\"",
        )
    }

    fun testHashBraceIsSingleToken() {
        assertTokenTypes(
            "#{ a: 1 }",
            HASH_BRACE,
            TokenType.WHITE_SPACE, IDENTIFIER, dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.COLON,
            TokenType.WHITE_SPACE, NUMBER, TokenType.WHITE_SPACE,
            dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.RBRACE,
        )
    }

    fun testHashBracketIsSingleToken() {
        assertTokenTypes(
            "#[1, 2]",
            HASH_BRACKET, NUMBER, dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.COMMA,
            TokenType.WHITE_SPACE, NUMBER, dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.RBRACKET,
        )
    }

    // ---- punctuation & operators ------------------------------------------------------------

    fun testEllipsisVsDot() {
        assertTokenTypes("...A.b", ELLIPSIS, IDENTIFIER, DOT, IDENTIFIER)
    }

    fun testOperators() {
        assertTokenTypes(
            "<= >= == != => && || :: ? | &",
            LE, TokenType.WHITE_SPACE,
            GE, TokenType.WHITE_SPACE,
            EQ_EQ, TokenType.WHITE_SPACE,
            NE, TokenType.WHITE_SPACE,
            ARROW, TokenType.WHITE_SPACE,
            AMP_AMP, TokenType.WHITE_SPACE,
            BAR_BAR, TokenType.WHITE_SPACE,
            COLON_COLON, TokenType.WHITE_SPACE,
            QUESTION, TokenType.WHITE_SPACE,
            BAR, TokenType.WHITE_SPACE,
            AMP,
        )
    }

    fun testBadCharacter() {
        // NOTE: the euro sign is *not* a valid probe here -- JFlex's [:jletter:] treats
        // currency symbols as valid Java identifier-start characters (per the JLS), so it
        // actually lexes as IDENTIFIER, not BAD_CHARACTER. A C0 control character like
        // \u0001 is *also* not a valid probe: Character.isJavaIdentifierPart(0x01) == true
        // (it's a Java "identifier-ignorable" char), so JFlex's [:jletterdigit:] swallows it
        // as an identifier *continuation* char when adjacent to a letter. \u001C (File
        // Separator) is neither identifier-start nor identifier-part -- verified via
        // Character.isJavaIdentifierStart/Part before picking it.
        val badChar = "\u001C"
        val tokens = tokenize(badChar)
        assertEquals(1, tokens.size)
        assertEquals(TokenType.BAD_CHARACTER, tokens[0].first)
        assertEquals(badChar, tokens[0].second)
    }

    fun testBadCharacterAdvancesLexer() {
        // Confirms a BAD_CHARACTER does not stall the lexer: valid tokens surround it.
        assertTokenTypes("a" + "\u001C" + "b", IDENTIFIER, TokenType.BAD_CHARACTER, IDENTIFIER)
    }

    // ---- kitchen sink ------------------------------------------------------------

    fun testKitchenSinkReachesEofWithNoBadCharactersAndFullCoverage() {
        val file = File("src/test/testData/lexer/kitchen-sink.tsp")
        assertTrue("fixture must exist: ${file.absolutePath}", file.exists())
        val text = file.readText()

        val lexer = createLexer()
        lexer.start(text)
        val sb = StringBuilder()
        val badCharacters = mutableListOf<String>()
        while (lexer.tokenType != null) {
            if (lexer.tokenType == TokenType.BAD_CHARACTER) {
                badCharacters.add(lexer.tokenText)
            }
            sb.append(lexer.tokenText)
            lexer.advance()
        }

        assertTrue("no BAD_CHARACTER tokens, found: $badCharacters", badCharacters.isEmpty())
        // Offset-coverage invariant: concatenation of all token texts equals the original text.
        assertEquals(text, sb.toString())
    }

    // ---- restartability ------------------------------------------------------------

    fun testLexerIsRestartable() {
        val file = File("src/test/testData/lexer/kitchen-sink.tsp")
        checkCorrectRestart(file.readText())
    }
}
