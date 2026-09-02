package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.tree.IFileElementType
import simpli.fyi.plugins.typespec.TypeSpecLanguage

/**
 * The single root node type for the flat parser tree (ADR 0005 M4b). There is only ever
 * one composite element type in the tree today: the file itself. Every other node under it
 * is a flat leaf token (see `TypeSpecFlatParser`). This is a transitional shape owned by
 * M5 Task 0, which introduces real composite element types from the generated grammar.
 */
object TypeSpecElementTypes {
    @JvmField
    val FILE = IFileElementType(TypeSpecLanguage.INSTANCE)
}
