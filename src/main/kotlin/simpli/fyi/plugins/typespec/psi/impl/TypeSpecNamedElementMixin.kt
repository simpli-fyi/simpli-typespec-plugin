package simpli.fyi.plugins.typespec.psi.impl

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecPsiUtil
import simpli.fyi.plugins.typespec.stubs.TypeSpecDeclStub

/**
 * Hand-written base class satisfying [TypeSpecNamedElement] for every declaration rule that
 * carries `mixin("<rule>") = "...psi.impl.TypeSpecNamedElementMixin"` in `TypeSpec.bnf`:
 * `namespace_statement`, `model_statement`, `model_property`, `template_parameter`, and — since
 * M6.5a (plan 06, ADR 0011) — `op_statement`, `interface_statement`, `enum_statement`,
 * `union_statement`, `alias_statement`, `scalar_statement`, `dec_statement`, `fn_statement`
 * ([ADR 0004](../../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D7.2/D7.3,
 * [ADR 0006](../../../../../../../../../docs/adr/0006-grammar-toolchain.md) D7). Grammar-Kit only
 * ever sees this class name as a *string* attribute and emits an `extends` clause from it — it
 * never needs this class on the generator's own classpath (unlike `methods=[...]` /
 * `psiImplUtilClass`, both banned in this repo's `.bnf` files for exactly that reason).
 *
 * All contract methods are implemented directly here, as ordinary Kotlin instance methods —
 * `methods=[...]` is never used, so Grammar-Kit is never asked to know any of them exist.
 *
 * Base class is [StubBasedPsiElementBase] (not `ASTWrapperPsiElement`) since M6.5a: Grammar-Kit
 * generates a `(TypeSpecDeclStub stub, IStubElementType stubType)` constructor on every one of
 * the 10 stubbed rules' `...Impl` classes, calling `super(stub, stubType)` — verified by running
 * the generator standalone against this repo's own `.bnf` (ADR 0011 D5). The 4 rules that are
 * NOT stubbed (`model_property`, `template_parameter`, plus `enum_member`/`union_variant`, which
 * share this mixin too) still only ever get the `(ASTNode)` constructor, which
 * [StubBasedPsiElementBase] also provides.
 */
abstract class TypeSpecNamedElementMixin : StubBasedPsiElementBase<TypeSpecDeclStub>, TypeSpecNamedElement {

    constructor(node: ASTNode) : super(node)
    constructor(stub: TypeSpecDeclStub, stubType: IStubElementType<*, *>) : super(stub, stubType)

    /**
     * Reproduces `ASTWrapperPsiElement`'s exact `toString()` format — bytecode-verified against
     * ideaIC-2025.2.6.3 as `getClass().getSimpleName() + "(" + node.getElementType() + ")"`.
     * Neither `StubBasedPsiElementBase` nor `ASTDelegatePsiElement` (its superclass) declares a
     * `toString()` of its own (also bytecode-verified) — losing this override after switching
     * base classes would silently turn every parser-golden PSI dump line into
     * `simpli.fyi...Impl@1f2e3d`, which is both wrong and non-deterministic across JVM runs
     * (plan 06 M6.5a Approach §4, "the golden-churn trap"; ADR 0011). Deliberately uses
     * [getElementTypeImpl] — protected, works whether or not this element currently has an
     * AST — not `getElementType()`.
     */
    override fun toString(): String = "${javaClass.simpleName}($elementTypeImpl)"

    /**
     * The name's [TypeSpecIdentifier] node — the **last** segment for a dotted
     * `namespace_statement`, the (only) segment for everything else. Navigation, Find Usages
     * previews and the structure view all point here, not at the declaration's keyword.
     */
    override fun getNameIdentifier(): TypeSpecIdentifier? = TypeSpecPsiUtil.findNameIdentifier(this)

    /**
     * Backtick-stripped text of [getNameIdentifier] ([ADR 0004](../../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D7.3).
     * Prefers the stub's own (already backtick-stripped) name when a stub is present — plan 06
     * M6.5a Approach §4: this is what lets a project-wide name lookup answer without ever
     * forcing this element's AST to load.
     */
    override fun getName(): String? = greenStub?.name ?: TypeSpecPsiUtil.stripBackticks(nameIdentifier?.text)

    /**
     * Points navigation/Find Usages/the structure view at the name, not the declaration's
     * leading keyword — getting this wrong trips `ParsingTestCase.checkRangeConsistency`.
     */
    override fun getTextOffset(): Int = nameIdentifier?.textRange?.startOffset ?: super.getTextOffset()

    /** Rename is out of scope for M5b — it is M6.5's ([ADR 0004](../../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D7.3). */
    override fun setName(name: String): PsiElement =
        throw IncorrectOperationException("Rename is not supported until M6.5 (ADR 0004 D7.3)")

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String? = name
        override fun getLocationString(): String? = containingFile?.name
        override fun getIcon(unused: Boolean) = null
    }
}
