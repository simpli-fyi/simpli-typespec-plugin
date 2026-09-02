package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.tree.IElementType
import simpli.fyi.plugins.typespec.TypeSpecLanguage

/**
 * Element type for Grammar-Kit-generated composite (non-leaf) PSI nodes — the
 * `elementTypeClass` referenced from `TypeSpec.bnf`'s header. Mirrors
 * [TypeSpecTokenType]'s shape; kept as a separate class because token types and
 * composite element types are conceptually different things even though both are
 * plain [IElementType] subclasses today.
 */
class TypeSpecElementType(debugName: String) : IElementType(debugName, TypeSpecLanguage.INSTANCE) {
    override fun toString() = "TypeSpec:" + super.toString()
}
