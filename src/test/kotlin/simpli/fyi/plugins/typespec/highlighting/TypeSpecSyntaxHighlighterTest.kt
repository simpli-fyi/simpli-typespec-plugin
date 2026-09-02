package simpli.fyi.plugins.typespec.highlighting

import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.TypeSpecLanguage
import simpli.fyi.plugins.typespec.lexer.TypeSpecLexerAdapter
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenType
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes

/**
 * M3 acceptance: [TypeSpecSyntaxHighlighter] and [TypeSpecColorSettingsPage], both as pure
 * unit tests (no fixture needed) and through the real editor-highlighter path.
 *
 * Deliberately does NOT use `myFixture.checkHighlighting()` (HighlightInfo/annotator-driven,
 * asserts on an empty set here) nor `EditorTestUtil.testFileSyntaxHighlighting` (resolves the
 * highlighter via `PsiFile.getFileType()`, which is `PlainTextFileType` for TypeSpec until a
 * `ParserDefinition` lands in M5 — see `TypeSpecFileTypeTest` — so it would silently exercise
 * `PlainSyntaxHighlighter` instead of production code).
 */
class TypeSpecSyntaxHighlighterTest : BasePlatformTestCase() {

    // ---- reflection over the declared token set ------------------------------

    /** Every `TypeSpecTokenType` declared as a `@JvmField val` on [TypeSpecTokenTypes]. */
    private val allDeclaredTokenTypes: List<IElementType> =
        TypeSpecTokenTypes::class.java.fields
            .filter { TypeSpecTokenType::class.java.isAssignableFrom(it.type) }
            .map { it.get(null) as IElementType }

    private fun highlighter() = TypeSpecSyntaxHighlighter()

    // ---- 1. registration test (guards plugin.xml) -----------------------------

    fun testSyntaxHighlighterFactoryIsRegisteredForTypeSpecLanguage() {
        val factory = SyntaxHighlighterFactory.getSyntaxHighlighter(
            TypeSpecLanguage.INSTANCE, project, null
        )
        assertNotNull("No SyntaxHighlighterFactory registered for TypeSpecLanguage in plugin.xml", factory)
        assertTrue(
            "Expected a TypeSpecSyntaxHighlighter but got ${factory?.javaClass}",
            factory is TypeSpecSyntaxHighlighter
        )
    }

    // ---- 2. total coverage: every declared token type is coloured -------------

    fun testEveryDeclaredTokenTypeIsColoured() {
        assertTrue("Expected TypeSpecTokenTypes to declare at least one token type", allDeclaredTokenTypes.isNotEmpty())
        val h = highlighter()
        for (tokenType in allDeclaredTokenTypes) {
            val keys = h.getTokenHighlights(tokenType)
            assertTrue(
                "TypeSpecSyntaxHighlighter.getTokenHighlights($tokenType) is empty; " +
                    "every token type in TypeSpecTokenTypes must map to a TextAttributesKey",
                keys.isNotEmpty()
            )
        }
    }

    fun testWhitespaceMapsToEmpty() {
        assertTrue(highlighter().getTokenHighlights(TokenType.WHITE_SPACE).isEmpty())
    }

    fun testBadCharacterIsColoured() {
        val keys = highlighter().getTokenHighlights(TokenType.BAD_CHARACTER)
        assertTrue(keys.isNotEmpty())
        assertTrue(
            "Expected TSP_BAD_CHARACTER (falls back to HighlighterColors.BAD_CHARACTER) in $keys",
            keys.contains(TypeSpecColors.BAD_CHARACTER)
        )
        assertEquals(HighlighterColors.BAD_CHARACTER, TypeSpecColors.BAD_CHARACTER.fallbackAttributeKey)
    }

    // ---- per-token-type spot checks (table in docs/plans/01-lexer-and-highlighter.md) --

    fun testKeywordAndIdentifier() {
        assertKey(TypeSpecTokenTypes.KEYWORD, TypeSpecColors.KEYWORD)
        assertKey(TypeSpecTokenTypes.IDENTIFIER, TypeSpecColors.IDENTIFIER)
    }

    fun testStringsAndEscapes() {
        assertKey(TypeSpecTokenTypes.STRING, TypeSpecColors.STRING)
        assertKey(TypeSpecTokenTypes.MULTILINE_STRING, TypeSpecColors.MULTILINE_STRING)
        assertKey(TypeSpecTokenTypes.VALID_ESCAPE, TypeSpecColors.VALID_ESCAPE)
        assertKey(TypeSpecTokenTypes.INVALID_ESCAPE, TypeSpecColors.INVALID_ESCAPE)
    }

    fun testNumber() {
        assertKey(TypeSpecTokenTypes.NUMBER, TypeSpecColors.NUMBER)
    }

    fun testComments() {
        assertKey(TypeSpecTokenTypes.LINE_COMMENT, TypeSpecColors.LINE_COMMENT)
        assertKey(TypeSpecTokenTypes.BLOCK_COMMENT, TypeSpecColors.BLOCK_COMMENT)
        assertKey(TypeSpecTokenTypes.DOC_COMMENT, TypeSpecColors.DOC_COMMENT)
    }

    fun testDecoratorsShareOneKey() {
        assertKey(TypeSpecTokenTypes.DECORATOR, TypeSpecColors.DECORATOR)
        assertKey(TypeSpecTokenTypes.AUGMENT_DECORATOR, TypeSpecColors.DECORATOR)
        assertKey(TypeSpecTokenTypes.AT, TypeSpecColors.DECORATOR)
        assertKey(TypeSpecTokenTypes.AT_AT, TypeSpecColors.DECORATOR)
    }

    fun testDirective() {
        assertKey(TypeSpecTokenTypes.DIRECTIVE, TypeSpecColors.DIRECTIVE)
        assertKey(TypeSpecTokenTypes.HASH, TypeSpecColors.DIRECTIVE)
    }

    fun testBraces() {
        assertKey(TypeSpecTokenTypes.LBRACE, TypeSpecColors.BRACES)
        assertKey(TypeSpecTokenTypes.RBRACE, TypeSpecColors.BRACES)
        assertKey(TypeSpecTokenTypes.HASH_BRACE, TypeSpecColors.BRACES)
    }

    fun testParentheses() {
        assertKey(TypeSpecTokenTypes.LPAREN, TypeSpecColors.PARENTHESES)
        assertKey(TypeSpecTokenTypes.RPAREN, TypeSpecColors.PARENTHESES)
    }

    fun testBrackets() {
        assertKey(TypeSpecTokenTypes.LBRACKET, TypeSpecColors.BRACKETS)
        assertKey(TypeSpecTokenTypes.RBRACKET, TypeSpecColors.BRACKETS)
        assertKey(TypeSpecTokenTypes.HASH_BRACKET, TypeSpecColors.BRACKETS)
    }

    fun testSemicolonCommaDot() {
        assertKey(TypeSpecTokenTypes.SEMICOLON, TypeSpecColors.SEMICOLON)
        assertKey(TypeSpecTokenTypes.COMMA, TypeSpecColors.COMMA)
        assertKey(TypeSpecTokenTypes.DOT, TypeSpecColors.DOT)
    }

    fun testOperators() {
        val operatorTokens = listOf(
            TypeSpecTokenTypes.LT, TypeSpecTokenTypes.GT, TypeSpecTokenTypes.LE, TypeSpecTokenTypes.GE,
            TypeSpecTokenTypes.EQ, TypeSpecTokenTypes.EQ_EQ, TypeSpecTokenTypes.NE, TypeSpecTokenTypes.ARROW,
            TypeSpecTokenTypes.AMP, TypeSpecTokenTypes.AMP_AMP, TypeSpecTokenTypes.BAR, TypeSpecTokenTypes.BAR_BAR,
            TypeSpecTokenTypes.QUESTION, TypeSpecTokenTypes.EXCL, TypeSpecTokenTypes.PLUS, TypeSpecTokenTypes.MINUS,
            TypeSpecTokenTypes.STAR, TypeSpecTokenTypes.SLASH, TypeSpecTokenTypes.ELLIPSIS, TypeSpecTokenTypes.COLON,
            TypeSpecTokenTypes.COLON_COLON,
        )
        for (t in operatorTokens) {
            assertKey(t, TypeSpecColors.OPERATOR)
        }
    }

    private fun assertKey(tokenType: IElementType, expected: TextAttributesKey) {
        val keys = highlighter().getTokenHighlights(tokenType)
        assertEquals("Unexpected keys for $tokenType", listOf(expected), keys.toList())
    }

    // ---- 4. page <-> highlighter agreement -------------------------------------

    fun testColorSettingsPageDescriptorsMatchHighlighterKeys() {
        val h = highlighter()
        val reachableFromHighlighter: Set<TextAttributesKey> = allDeclaredTokenTypes
            .flatMap { h.getTokenHighlights(it).toList() }
            .toSet() + h.getTokenHighlights(TokenType.BAD_CHARACTER).toList()

        val page = TypeSpecColorSettingsPage()
        val fromPage: Set<TextAttributesKey> = page.attributeDescriptors.map { it.key }.toSet()

        assertEquals(
            "Highlighter produces a key with no AttributesDescriptor on the color settings page",
            emptySet<TextAttributesKey>(),
            reachableFromHighlighter - fromPage
        )
        assertEquals(
            "Color settings page has an AttributesDescriptor for a key the highlighter never produces (orphaned descriptor)",
            emptySet<TextAttributesKey>(),
            fromPage - reachableFromHighlighter
        )
    }

    // ---- 5. external-name stability ---------------------------------------------

    fun testExternalNamesAreStable() {
        val expected = mapOf(
            "TSP_KEYWORD" to TypeSpecColors.KEYWORD,
            "TSP_IDENTIFIER" to TypeSpecColors.IDENTIFIER,
            "TSP_STRING" to TypeSpecColors.STRING,
            "TSP_MULTILINE_STRING" to TypeSpecColors.MULTILINE_STRING,
            "TSP_VALID_ESCAPE" to TypeSpecColors.VALID_ESCAPE,
            "TSP_INVALID_ESCAPE" to TypeSpecColors.INVALID_ESCAPE,
            "TSP_NUMBER" to TypeSpecColors.NUMBER,
            "TSP_LINE_COMMENT" to TypeSpecColors.LINE_COMMENT,
            "TSP_BLOCK_COMMENT" to TypeSpecColors.BLOCK_COMMENT,
            "TSP_DOC_COMMENT" to TypeSpecColors.DOC_COMMENT,
            "TSP_DECORATOR" to TypeSpecColors.DECORATOR,
            "TSP_DIRECTIVE" to TypeSpecColors.DIRECTIVE,
            "TSP_BRACES" to TypeSpecColors.BRACES,
            "TSP_PARENTHESES" to TypeSpecColors.PARENTHESES,
            "TSP_BRACKETS" to TypeSpecColors.BRACKETS,
            "TSP_SEMICOLON" to TypeSpecColors.SEMICOLON,
            "TSP_COMMA" to TypeSpecColors.COMMA,
            "TSP_DOT" to TypeSpecColors.DOT,
            "TSP_OPERATOR" to TypeSpecColors.OPERATOR,
            "TSP_BAD_CHARACTER" to TypeSpecColors.BAD_CHARACTER,
        )
        for ((externalName, key) in expected) {
            assertEquals(externalName, key.externalName)
        }
    }

    // ---- 6. demo text is well-formed ---------------------------------------------
    //
    // demoText is a colour SHOWCASE for ColorSettingsPage, not valid TypeSpec: it must
    // deliberately contain one BAD_CHARACTER token so TSP_BAD_CHARACTER previews live in
    // Settings | Editor | Color Scheme | TypeSpec. That is why this is split into two
    // tests instead of one "lexes cleanly" test -- "zero BAD_CHARACTER tokens" and
    // "exercises every descriptor including TSP_BAD_CHARACTER" cannot both hold for this
    // fixture. Contrast with kitchen-sink.tsp (asserted elsewhere), which IS meant to be
    // valid TypeSpec and must lex with zero BAD_CHARACTER tokens.

    fun testDemoTextExercisesEveryRegisteredDescriptor() {
        val page = TypeSpecColorSettingsPage()
        val demoText = page.demoText
        assertTrue("demoText must not be blank", demoText.isNotBlank())

        val lexer = TypeSpecLexerAdapter()
        lexer.start(demoText)
        val producedTypes = mutableSetOf<IElementType>()
        var lastTokenEnd = 0
        while (lexer.tokenType != null) {
            producedTypes.add(lexer.tokenType!!)
            lastTokenEnd = lexer.tokenEnd
            lexer.advance()
        }
        assertEquals("Lexer did not consume all of demoText", demoText.length, lastTokenEnd)

        val h = highlighter()
        val producedKeys = producedTypes.flatMap { h.getTokenHighlights(it).toList() }.toSet()
        for (descriptor in page.attributeDescriptors) {
            assertTrue(
                "AttributesDescriptor '${descriptor.displayName}' (key=${descriptor.key.externalName}) " +
                    "is never produced by lexing demoText",
                producedKeys.contains(descriptor.key)
            )
        }
    }

    fun testDemoTextHasExactlyOneIntentionalBadCharacter() {
        // The one deliberate BAD_CHARACTER probe is the File Separator control character
        // (\u001C) documented in TypeSpecLexerTest.testBadCharacter, at the fixed offset
        // below. Any OTHER BAD_CHARACTER token would be an accidental, unlexable character
        // slipping into the demo text -- that is what this test guards against.
        val page = TypeSpecColorSettingsPage()
        val demoText = page.demoText
        val intendedOffset = demoText.indexOf('\u001C')
        assertTrue("demoText no longer contains the intended \\u001C bad-character probe", intendedOffset >= 0)

        val lexer = TypeSpecLexerAdapter()
        lexer.start(demoText)
        val badCharacterOffsets = mutableListOf<Int>()
        while (lexer.tokenType != null) {
            if (lexer.tokenType == TokenType.BAD_CHARACTER) {
                badCharacterOffsets.add(lexer.tokenStart)
            }
            lexer.advance()
        }

        assertEquals(
            "Expected exactly one BAD_CHARACTER token (the intended \\u001C probe); " +
                "found at offsets $badCharacterOffsets",
            listOf(intendedOffset),
            badCharacterOffsets
        )
    }
    // ---- ColorSettingsPage smoke tests --------------------------------------------

    fun testAttributeDescriptorsNonEmpty() {
        assertTrue(TypeSpecColorSettingsPage().attributeDescriptors.isNotEmpty())
    }

    fun testHighlighterPropertyReturnsRightType() {
        assertTrue(TypeSpecColorSettingsPage().highlighter is TypeSpecSyntaxHighlighter)
    }

    fun testDisplayNamesResolveInBundle() {
        for (descriptor in TypeSpecColorSettingsPage().attributeDescriptors) {
            assertFalse(
                "AttributesDescriptor display name '${descriptor.displayName}' is an unresolved bundle key",
                descriptor.displayName.startsWith("!") && descriptor.displayName.endsWith("!")
            )
        }
        assertFalse(
            TypeSpecColorSettingsPage().displayName.let { it.startsWith("!") && it.endsWith("!") }
        )
    }
}
