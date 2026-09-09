package simpli.fyi.plugins.typespec.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.DefaultStubBuilder
import com.intellij.psi.stubs.StubElement
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecTypes

/**
 * The stub tree builder for `.tsp` files (plan 06 M6.5a). [DefaultStubBuilder] parses PSI at
 * index time and walks it looking for elements whose `IElementType.shouldCreateStub` returns
 * `true` — `LightStubBuilder` would avoid the PSI parse, but the generated parser needs
 * `ILightStubElementType` everywhere for that, which is not worth it before this is measured as
 * a problem (plan 06 M6.5a Risks).
 */
class TypeSpecStubBuilder : DefaultStubBuilder() {

    override fun createStubForFile(file: PsiFile): StubElement<*> =
        TypeSpecFileStub(file as? TypeSpecFile)

    /**
     * None of the 10 stubbed rules can ever live inside a `model`/`enum`/`union`/`interface`
     * body (`TypeSpec.bnf`'s member rules — `model_property`, `enum_member`, `union_variant`,
     * `interface_operation` — are deliberately not among the 10, plan 06 §What gets stubbed), so
     * there is nothing to find by descending into one. Changing this set is an
     * [TypeSpecStubVersion] D6 item-4 bump.
     */
    override fun skipChildProcessingWhenBuildingStubs(parent: ASTNode, node: ASTNode): Boolean {
        val type = node.elementType
        return type === TypeSpecTypes.MODEL_BODY ||
            type === TypeSpecTypes.ENUM_BODY ||
            type === TypeSpecTypes.UNION_BODY ||
            type === TypeSpecTypes.INTERFACE_BODY
    }
}
