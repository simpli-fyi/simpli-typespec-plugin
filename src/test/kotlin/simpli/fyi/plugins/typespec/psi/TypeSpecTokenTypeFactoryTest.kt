package simpli.fyi.plugins.typespec.psi

import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Verifies ADR 0006 D5's `tokenTypeFactory` bridge: [TypeSpecTokenTypes.fromNameOrText]
 * must resolve every existing token constant to the identical instance the lexer
 * already emits — never mint a new one — and must throw on an unknown key.
 */
class TypeSpecTokenTypeFactoryTest {

    @Test
    fun `resolves every constant by its debug name to the identical instance`() {
        for (field in TypeSpecTokenTypes.javaClass.declaredFields) {
            if (!Modifier.isStatic(field.modifiers)) continue
            if (field.type != TypeSpecTokenType::class.java) continue
            field.isAccessible = true
            val expected = field.get(null) as TypeSpecTokenType
            assertSame(
                "fromNameOrText(\"${field.name}\") must return the same instance as field ${field.name}",
                expected,
                TypeSpecTokenTypes.fromNameOrText(field.name),
            )
        }
    }

    @Test
    fun `throws on an unknown key`() {
        assertThrows(IllegalArgumentException::class.java) {
            TypeSpecTokenTypes.fromNameOrText("nonsense")
        }
    }
}
