package simpli.fyi.plugins.typespec.editor

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter
import com.intellij.lang.LanguageBraceMatching
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.TypeSpecLanguage
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes

/**
 * M4 acceptance: [TypeSpecBraceMatcher].
 *
 * [testBraceMatchingUtilResolvesOurMatcher] confirms brace matching resolves through the
 * exact platform entry point (`BraceMatchingUtil.getBraceMatcher(FileType, IElementType)`)
 * editor code uses, per ADR 0005 D4's requirement that this be automated-test-verified now
 * that `TypeSpecParserDefinition` exists (superseding ADR 0003 D5's "likely, not proven").
 */
class TypeSpecBraceMatcherTest : BasePlatformTestCase() {

    private val matcher = TypeSpecBraceMatcher()

    // ---- registration -----------------------------------------------------------

    fun testBraceMatcherIsRegisteredForTypeSpecLanguage() {
        val registered = LanguageBraceMatching.INSTANCE.forLanguage(TypeSpecLanguage.INSTANCE)
        assertNotNull("No PairedBraceMatcher registered for TypeSpecLanguage in plugin.xml", registered)
        assertTrue(
            "Expected a TypeSpecBraceMatcher but got ${registered?.javaClass}",
            registered is TypeSpecBraceMatcher,
        )
    }

    // ---- pair list ----------------------------------------------------------------

    fun testPairList() {
        val pairs = matcher.pairs
        assertEquals(5, pairs.size)

        fun pair(left: com.intellij.psi.tree.IElementType) = pairs.first { it.leftBraceType == left }

        val brace = pair(TypeSpecTokenTypes.LBRACE)
        assertEquals(TypeSpecTokenTypes.RBRACE, brace.rightBraceType)
        assertTrue("{} must be structural", brace.isStructural)

        val paren = pair(TypeSpecTokenTypes.LPAREN)
        assertEquals(TypeSpecTokenTypes.RPAREN, paren.rightBraceType)
        assertFalse(paren.isStructural)

        val bracket = pair(TypeSpecTokenTypes.LBRACKET)
        assertEquals(TypeSpecTokenTypes.RBRACKET, bracket.rightBraceType)
        assertFalse(bracket.isStructural)
    }

    fun testHashBraceAndHashBracketAsymmetricClosers() {
        // The lexer emits HASH_BRACE ("#{") / HASH_BRACKET ("#[") as single opening tokens,
        // but closes both value literals with plain RBRACE ("}") / RBRACKET ("]") — there is
        // no HASH-prefixed closing token. Assert the matcher's pair list reflects that
        // asymmetry rather than pairing HASH_BRACE with itself or inventing a closer.
        val pairs = matcher.pairs

        val hashBrace = pairs.first { it.leftBraceType == TypeSpecTokenTypes.HASH_BRACE }
        assertEquals(TypeSpecTokenTypes.RBRACE, hashBrace.rightBraceType)
        assertTrue("#{ ... } must be structural", hashBrace.isStructural)

        val hashBracket = pairs.first { it.leftBraceType == TypeSpecTokenTypes.HASH_BRACKET }
        assertEquals(TypeSpecTokenTypes.RBRACKET, hashBracket.rightBraceType)
        assertFalse(hashBracket.isStructural)

        // Both RBRACE and RBRACKET each appear exactly twice as a right-brace type (once for
        // the plain opener, once for the HASH_ opener) — never as a left-brace type themselves.
        assertEquals(2, pairs.count { it.rightBraceType == TypeSpecTokenTypes.RBRACE })
        assertEquals(2, pairs.count { it.rightBraceType == TypeSpecTokenTypes.RBRACKET })
        assertTrue(pairs.none { it.leftBraceType == TypeSpecTokenTypes.RBRACE })
        assertTrue(pairs.none { it.leftBraceType == TypeSpecTokenTypes.RBRACKET })
    }

    // ---- isPairedBracesAllowedBeforeType ------------------------------------------

    fun testIsPairedBracesAllowedBeforeTypeIsAlwaysTrue() {
        assertTrue(matcher.isPairedBracesAllowedBeforeType(TypeSpecTokenTypes.LBRACE, null))
        assertTrue(
            matcher.isPairedBracesAllowedBeforeType(TypeSpecTokenTypes.LBRACE, TypeSpecTokenTypes.IDENTIFIER)
        )
        assertTrue(
            matcher.isPairedBracesAllowedBeforeType(TypeSpecTokenTypes.HASH_BRACKET, TypeSpecTokenTypes.RBRACE)
        )
    }

    // ---- getCodeConstructStart -----------------------------------------------------

    fun testGetCodeConstructStartReturnsOpeningBraceOffsetUnchanged() {
        assertEquals(42, matcher.getCodeConstructStart(null, 42))
    }

    // ---- the interesting one: the real platform resolution path --------------------

    fun testBraceMatchingUtilResolvesOurMatcher() {
        val resolved = BraceMatchingUtil.getBraceMatcher(TypeSpecFileType.INSTANCE, TypeSpecTokenTypes.LBRACE)
        assertNotNull(
            "BraceMatchingUtil.getBraceMatcher returned null for a TypeSpec token type",
            resolved,
        )
        assertTrue(
            "Expected the resolved BraceMatcher to be a PairedBraceMatcherAdapter wrapping our " +
                "PairedBraceMatcher, but got ${resolved.javaClass}",
            resolved is PairedBraceMatcherAdapter,
        )

        // PairedBraceMatcherAdapter wraps the language's PairedBraceMatcher in a private final
        // field; reach in to prove it is specifically our TypeSpecBraceMatcher instance, not
        // merely "some adapter".
        val myMatcherField = PairedBraceMatcherAdapter::class.java.getDeclaredField("myMatcher")
        myMatcherField.isAccessible = true
        val delegate = myMatcherField.get(resolved)
        assertTrue(
            "PairedBraceMatcherAdapter's delegate is ${delegate?.javaClass}, expected TypeSpecBraceMatcher",
            delegate is TypeSpecBraceMatcher,
        )

        // And functionally: the resolved BraceMatcher agrees with our pairs, including the
        // HASH_BRACE/HASH_BRACKET asymmetry.
        assertEquals(
            TypeSpecTokenTypes.RBRACE,
            resolved.getOppositeBraceTokenType(TypeSpecTokenTypes.LBRACE),
        )
        assertEquals(
            TypeSpecTokenTypes.RBRACE,
            resolved.getOppositeBraceTokenType(TypeSpecTokenTypes.HASH_BRACE),
        )
        assertEquals(
            TypeSpecTokenTypes.RBRACKET,
            resolved.getOppositeBraceTokenType(TypeSpecTokenTypes.HASH_BRACKET),
        )
        assertTrue(resolved.isPairBraces(TypeSpecTokenTypes.LBRACE, TypeSpecTokenTypes.RBRACE))
        assertTrue(resolved.isPairBraces(TypeSpecTokenTypes.HASH_BRACE, TypeSpecTokenTypes.RBRACE))
        assertTrue(resolved.isPairBraces(TypeSpecTokenTypes.HASH_BRACKET, TypeSpecTokenTypes.RBRACKET))
    }

    fun testBraceMatchingUtilResolvesForEveryOpeningToken() {
        for (tokenType in listOf(
            TypeSpecTokenTypes.LBRACE,
            TypeSpecTokenTypes.LPAREN,
            TypeSpecTokenTypes.LBRACKET,
            TypeSpecTokenTypes.HASH_BRACE,
            TypeSpecTokenTypes.HASH_BRACKET,
        )) {
            val resolved = BraceMatchingUtil.getBraceMatcher(TypeSpecFileType.INSTANCE, tokenType)
            assertNotNull("No BraceMatcher resolved for $tokenType", resolved)
            assertTrue(
                "Resolved BraceMatcher for $tokenType is ${resolved.javaClass}, expected PairedBraceMatcherAdapter",
                resolved is PairedBraceMatcherAdapter,
            )
        }
    }
}
