package simpli.fyi.plugins.typespec.refactoring

import com.intellij.lang.LanguageNamesValidation
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.TypeSpecLanguage

/**
 * Plan 07 M6.5f acceptance group 5 -- validator wiring. Goes through
 * `LanguageNamesValidation.isIdentifier(TypeSpecLanguage.INSTANCE, ...)` so this asserts the
 * `lang.namesValidator` extension point is actually registered and picked up for the TypeSpec
 * language, not just that [TypeSpecNamesValidator] compiles.
 */
class TypeSpecNamesValidatorTest : BasePlatformTestCase() {

    private fun isIdentifier(name: String): Boolean =
        LanguageNamesValidation.INSTANCE.forLanguage(TypeSpecLanguage.INSTANCE).isIdentifier(name, project)

    private fun isKeyword(name: String): Boolean =
        LanguageNamesValidation.INSTANCE.forLanguage(TypeSpecLanguage.INSTANCE).isKeyword(name, project)

    // Same non-throwing rows as TypeSpecNamesTest group 1 -- all representable, so all valid.
    fun testIsIdentifier_trueForRepresentableNames() {
        val representable = listOf("Foo", "model", "my name", "`model`", "`Foo`", "_x\$1", "9lives", "  Foo  ")
        for (name in representable) {
            assertTrue("isIdentifier(\"$name\") should be true", isIdentifier(name))
        }
    }

    fun testIsIdentifier_falseForUnrepresentableNames() {
        val unrepresentable = listOf("a`b", "", "a\nb")
        for (name in unrepresentable) {
            assertFalse("isIdentifier(\"$name\") should be false", isIdentifier(name))
        }
    }

    fun testIsKeyword_matchesTypeSpecKeywordsAll() {
        assertTrue(isKeyword("model"))
        assertTrue(isKeyword("struct")) // reserved, still a keyword
        assertFalse(isKeyword("Foo"))
    }
}
