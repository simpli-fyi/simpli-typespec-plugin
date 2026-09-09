package simpli.fyi.plugins.typespec.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.IncorrectOperationException

/**
 * Direct tests for [TypeSpecElementFactory]'s three guard branches (ADR 0012 D4). In
 * production these are unreachable because [TypeSpecNames.escape] guarantees its output never
 * trips them; the factory itself never escapes, so a directly supplied, deliberately
 * "already-escaped-looking" but malformed string reaches each guard individually here.
 */
class TypeSpecElementFactoryTest : BasePlatformTestCase() {

    // ---- guard 1: the dummy file contains a PsiErrorElement ------------------------------

    /**
     * "9lives" cannot lex as a single `IDENTIFIER` (identifiers cannot start with a digit --
     * `_TypeSpecLexer.flex`'s `IdentifierStart` excludes digits), so `model 9lives {}` fails to
     * parse cleanly and the dummy file contains a `PsiErrorElement`.
     */
    fun testCreateIdentifier_parseError_throwsAndNamesText() {
        val exception = assertThrowsIncorrectOperation { TypeSpecElementFactory.createIdentifier(project, "9lives") }
        assertTrue(
            "exception message must name the offending text, was: ${exception.message}",
            exception.message?.contains("9lives") == true,
        )
    }

    // ---- guard 2: not exactly one TypeSpecIdentifier --------------------------------------

    /**
     * "Foo extends Bar" parses cleanly as a valid `model_statement` (`model` name
     * `extends_clause` `model_body`), but that clause's identifier means the dummy file
     * contains two `TypeSpecIdentifier` leaves ("Foo" and "Bar"), not one.
     */
    fun testCreateIdentifier_wrongIdentifierCount_throwsAndNamesText() {
        val exception = assertThrowsIncorrectOperation {
            TypeSpecElementFactory.createIdentifier(project, "Foo extends Bar")
        }
        assertTrue(
            "exception message must name the offending text, was: ${exception.message}",
            exception.message?.contains("Foo extends Bar") == true,
        )
    }

    // ---- guard 3: the identifier consumed only a prefix of the requested text -------------

    /**
     * "Foo " (trailing space) parses cleanly, produces exactly one `TypeSpecIdentifier`
     * ("Foo"), but that identifier's text is a strict prefix of the requested "Foo " -- the
     * trailing space was consumed as trivia, not as part of the identifier.
     */
    fun testCreateIdentifier_partiallyConsumedText_throwsAndNamesText() {
        val exception = assertThrowsIncorrectOperation { TypeSpecElementFactory.createIdentifier(project, "Foo ") }
        assertTrue(
            "exception message must name the offending text, was: ${exception.message}",
            exception.message?.contains("Foo ") == true,
        )
    }

    private fun assertThrowsIncorrectOperation(block: () -> Unit): IncorrectOperationException {
        try {
            block()
        } catch (e: IncorrectOperationException) {
            return e
        }
        fail("expected IncorrectOperationException, none was thrown")
        error("unreachable")
    }
}
