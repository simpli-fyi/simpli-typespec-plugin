package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.tree.IElementType
import simpli.fyi.plugins.typespec.TypeSpecLanguage

class TypeSpecTokenType(debugName: String) : IElementType(debugName, TypeSpecLanguage.INSTANCE) {
    override fun toString() = "TypeSpec:" + super.toString()
}
