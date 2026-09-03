package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.tree.IFileElementType
import simpli.fyi.plugins.typespec.stubs.TypeSpecFileElementType

/**
 * The single [IFileElementType] for `.tsp` files. Since M6.5a (plan 06, ADR 0011) this is a
 * [TypeSpecFileElementType] — a stub file element type — rather than a bare `IFileElementType`,
 * but the field's name, type (`IStubFileElementType` IS-A `IFileElementType`) and
 * instance-identity contract are unchanged from ADR 0005 M4b (ADR 0006 F7): Grammar-Kit never
 * generates a file element type of its own — the generated parser's root rule (`typespec_file`
 * in `TypeSpec.bnf`) is handed this exact instance at parse time via
 * `TypeSpecParserDefinition.getFileNodeType()`, and inlines directly under it instead of
 * declaring a colliding element type. Every other composite node in the tree (`model`,
 * `namespace`, etc.) is one of the real element types Grammar-Kit generates into
 * `TypeSpecTypes` from M5b onward — ten of them stub element types since M6.5a
 * (`TypeSpecStubTypes`), the rest still plain `TypeSpecElementType`.
 */
object TypeSpecElementTypes {
    @JvmField
    val FILE: IFileElementType = TypeSpecFileElementType()
}
