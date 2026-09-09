package simpli.fyi.plugins.typespec.refactoring

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet
import com.intellij.util.IncorrectOperationException
import junit.framework.TestCase
import simpli.fyi.plugins.typespec.lexer.TypeSpecLexerAdapter
import simpli.fyi.plugins.typespec.psi.TypeSpecNames
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenSets
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.IDENTIFIER

/**
 * M6.5f acceptance groups 1-3 (plan 07 "M6.5f -- Names, keywords, escaping and the
 * NamesValidator" -- Acceptance).
 *
 * Group 3 in particular is the property that makes rename safe: every non-throwing escaping
 * row must lex, through the real [TypeSpecLexerAdapter], as exactly one IDENTIFIER token
 * spanning the whole escaped text.
 */
class TypeSpecNamesTest : TestCase() {

    // ---- group 1: escaping table (ADR 0012 D2, plan 07 M6.5f acceptance 1) ----------------

    /** Rows that must escape without throwing: raw input -> expected escaped output. */
    private val nonThrowingRows: List<Pair<String, String>> = listOf(
        "Foo" to "Foo",
        "model" to "`model`",
        "my name" to "`my name`",
        "`model`" to "`model`",
        "`Foo`" to "Foo",
        "_x\$1" to "_x\$1",
        "9lives" to "`9lives`",
        "  Foo  " to "Foo",
    )

    fun testEscapingTable_nonThrowingRows() {
        for ((raw, expected) in nonThrowingRows) {
            assertEquals("escape(\"$raw\")", expected, TypeSpecNames.escape(raw))
        }
    }

    fun testEscape_backtickInName_throws() {
        assertThrowsIncorrectOperation { TypeSpecNames.escape("a`b") }
    }

    fun testEscape_empty_throws() {
        assertThrowsIncorrectOperation { TypeSpecNames.escape("") }
    }

    fun testEscape_embeddedNewline_throws() {
        assertThrowsIncorrectOperation { TypeSpecNames.escape("a\nb") }
    }

    private fun assertThrowsIncorrectOperation(block: () -> Unit) {
        try {
            block()
            fail("expected IncorrectOperationException")
        } catch (_: IncorrectOperationException) {
            // expected
        }
    }

    // ---- group 2: round-trip + idempotence (plan 07 M6.5f acceptance 2) -------------------

    fun testRoundTrip_unescapeOfEscape() {
        for ((raw, _) in nonThrowingRows) {
            val escaped = TypeSpecNames.escape(raw)
            val expected = TypeSpecNames.unescape(raw.trim().let(TypeSpecNames::unescape))
            assertEquals(
                "unescape(escape(\"$raw\"))",
                expected,
                TypeSpecNames.unescape(escaped),
            )
        }
    }

    fun testIdempotence_escapeOfEscape() {
        for ((raw, _) in nonThrowingRows) {
            val escaped = TypeSpecNames.escape(raw)
            assertEquals(
                "escape(escape(\"$raw\")) must equal escape(\"$raw\")",
                escaped,
                TypeSpecNames.escape(escaped),
            )
        }
    }

    // ---- group 3: lexer agreement -- the important one (plan 07 M6.5f acceptance 3) -------

    fun testLexerAgreement_escapedNameLexesAsSingleIdentifier() {
        for ((raw, _) in nonThrowingRows) {
            val escaped = TypeSpecNames.escape(raw)
            val lexer = TypeSpecLexerAdapter()
            lexer.start(escaped)

            assertEquals("token type for escaped \"$raw\" -> \"$escaped\"", IDENTIFIER, lexer.tokenType)
            assertEquals("token start for \"$escaped\"", 0, lexer.tokenStart)
            assertEquals("token end for \"$escaped\"", escaped.length, lexer.tokenEnd)

            lexer.advance()
            assertNull("expected exactly one token for \"$escaped\", found a second", lexer.tokenType)
        }
    }

    // ---- group 3b: word-scanner agreement -- the guard for isIndexKeyableName -------------
    //
    // Carried by the M6.5h correction (plan 07 "Amendment 3"), not by re-opening M6.5f: this
    // pins TypeSpecNames.isIndexKeyableName -- a hand transcription of decompiled platform code
    // (ADR 0012 F12) -- against a real DefaultWordsScanner, configured exactly as
    // TypeSpecFindUsagesProvider.getWordsScanner() configures it. Declares each name with
    // `alias <name> = string;` rather than `model` specifically so that the keyword literal in
    // the snippet itself ("alias", "string") can never coincide with, and so double-count, one
    // of the table's own test names.

    private fun newWordsScanner(): DefaultWordsScanner =
        DefaultWordsScanner(
            TypeSpecLexerAdapter(),
            TokenSet.create(IDENTIFIER),
            TypeSpecTokenSets.COMMENTS,
            TypeSpecTokenSets.STRINGS,
        )

    private fun wordOccurrences(source: String): List<String> {
        val occurrences = mutableListOf<String>()
        newWordsScanner().processWords(source) { occurrence ->
            occurrences.add(occurrence.baseText.subSequence(occurrence.start, occurrence.end).toString())
            true
        }
        return occurrences
    }

    fun testWordScannerAgreement_isIndexKeyableNameMatchesScannerRecall() {
        val table = listOf("Foo", "model", "_x", "a\$b", "9lives", "Ünïcode", "my name", "a-b", "a.b")

        for (name in table) {
            val spelled = TypeSpecNames.escape(name)
            val source = "alias $spelled = string;"
            val occurrences = wordOccurrences(source)
            // Every token in the snippet (including keyword literals like "alias"/"string")
            // is passed through the scanner, so the total occurrence count is not 1 in
            // general -- the property under test is that exactly one of them equals `name`
            // whole, which is what "the word index can key on this name" means.
            val matchCount = occurrences.count { it == name }
            val isSingleMatchingOccurrence = matchCount == 1

            assertEquals(
                "isIndexKeyableName(\"$name\") must agree with the scanner's recall over " +
                    "\"$source\" (occurrences found: $occurrences)",
                TypeSpecNames.isIndexKeyableName(name),
                isSingleMatchingOccurrence,
            )
        }
    }

    /**
     * The empty string cannot be embedded in a declaration at all ([TypeSpecNames.escape] throws
     * for it), so it is asserted directly rather than through the scanner: it is not
     * index-keyable, vacuously.
     */
    fun testWordScannerAgreement_emptyNameIsNotIndexKeyable() {
        assertFalse(TypeSpecNames.isIndexKeyableName(""))
    }
}
