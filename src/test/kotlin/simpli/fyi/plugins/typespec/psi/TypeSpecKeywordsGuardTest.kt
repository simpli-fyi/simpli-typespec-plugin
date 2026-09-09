package simpli.fyi.plugins.typespec.psi

import junit.framework.TestCase
import simpli.fyi.plugins.typespec.lexer.TypeSpecLexerAdapter
import java.io.File

/**
 * Plan 07 M6.5f acceptance group 4 -- the keyword-set mirror guard (ADR 0012 D3).
 *
 * Parses `src/main/grammars/_TypeSpecLexer.flex` for every `"word" { return KEYWORD; }` rule
 * and asserts set equality with [TypeSpecKeywords.ALL] in *both* directions, so that:
 *  - a keyword added to the lexer without updating [TypeSpecKeywords.ALL] fails here, and
 *  - a stale entry left in [TypeSpecKeywords.ALL] after a keyword is removed from the lexer
 *    also fails here.
 *
 * This is a test reading a `src/main` file by design (the drift alarm, plan 07 M6.5f "Risks");
 * it cannot perturb parsing.
 */
class TypeSpecKeywordsGuardTest : TestCase() {

    private val flexFile = File("src/main/grammars/_TypeSpecLexer.flex")

    /** `"word"         { return KEYWORD; }` -- captures the quoted literal. */
    private val keywordRule = Regex(""""([^"]+)"\s*\{\s*return\s+KEYWORD\s*;\s*\}""")

    private fun keywordsFromFlex(): Set<String> {
        assertTrue("expected to find ${flexFile.path}", flexFile.exists())
        val text = flexFile.readText()
        val found = keywordRule.findAll(text).map { it.groupValues[1] }.toList()
        assertTrue("expected at least one KEYWORD rule in ${flexFile.path}", found.isNotEmpty())
        return found.toSet()
    }

    fun testFlexKeywordRulesMatchTypeSpecKeywordsAll() {
        val fromFlex = keywordsFromFlex()

        val missingFromKotlin = fromFlex - TypeSpecKeywords.ALL
        assertTrue(
            "keyword(s) present in _TypeSpecLexer.flex but missing from TypeSpecKeywords.ALL: $missingFromKotlin",
            missingFromKotlin.isEmpty(),
        )

        val staleInKotlin = TypeSpecKeywords.ALL - fromFlex
        assertTrue(
            "keyword(s) present in TypeSpecKeywords.ALL but not returned as KEYWORD by _TypeSpecLexer.flex: $staleInKotlin",
            staleInKotlin.isEmpty(),
        )

        assertEquals(fromFlex, TypeSpecKeywords.ALL)
    }

    fun testEveryKeywordLexesAsExactlyOneKeywordToken() {
        for (word in TypeSpecKeywords.ALL) {
            val lexer = TypeSpecLexerAdapter()
            lexer.start(word)

            assertEquals("token type for keyword '$word'", TypeSpecTokenTypes.KEYWORD, lexer.tokenType)
            assertEquals("token start for keyword '$word'", 0, lexer.tokenStart)
            assertEquals("token end for keyword '$word'", word.length, lexer.tokenEnd)

            lexer.advance()
            assertNull("expected exactly one token for keyword '$word'", lexer.tokenType)
        }
    }
}
