package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.tree.IFileElementType
import simpli.fyi.plugins.typespec.TypeSpecLanguage

/**
 * The single [IFileElementType] for `.tsp` files, unchanged since ADR 0005 M4b (ADR 0006 F7).
 * Grammar-Kit never generates a file element type of its own — the generated parser's root
 * rule (`typespec_file` in `TypeSpec.bnf`) is handed this exact instance at parse time via
 * `TypeSpecParserDefinition.getFileNodeType()`, and inlines directly under it instead of
 * declaring a colliding element type. Every other composite node in the tree (`model`,
 * `namespace`, etc.) is one of the real element types Grammar-Kit generates into
 * `TypeSpecTypes` from M5b onward.
 */
object TypeSpecElementTypes {
    @JvmField
    val FILE = IFileElementType(TypeSpecLanguage.INSTANCE)
}
