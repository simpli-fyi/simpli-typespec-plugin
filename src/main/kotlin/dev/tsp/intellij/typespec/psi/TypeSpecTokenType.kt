package dev.tsp.intellij.typespec.psi

import com.intellij.psi.tree.IElementType
import dev.tsp.intellij.typespec.TypeSpecLanguage

class TypeSpecTokenType(debugName: String) : IElementType(debugName, TypeSpecLanguage.INSTANCE) {
    override fun toString() = "TypeSpec:" + super.toString()
}
