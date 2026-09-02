package simpli.fyi.plugins.typespec.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecPsiUtil

/**
 * Hand-written base class satisfying [TypeSpecNamedElement] for every declaration rule that
 * carries `mixin("<rule>") = "...psi.impl.TypeSpecNamedElementMixin"` in `TypeSpec.bnf`:
 * `namespace_statement`, `model_statement`, `model_property`, `template_parameter`
 * ([ADR 0004](../../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D7.2/D7.3,
 * [ADR 0006](../../../../../../../../../docs/adr/0006-grammar-toolchain.md) D7). Grammar-Kit only
 * ever sees this class name as a *string* attribute and emits an `extends` clause from it — it
 * never needs this class on the generator's own classpath (unlike `methods=[...]` /
 * `psiImplUtilClass`, both banned in this repo's `.bnf` files for exactly that reason).
 *
 * All contract methods are implemented directly here, as ordinary Kotlin instance methods —
 * `methods=[...]` is never used, so Grammar-Kit is never asked to know any of them exist.
 */
abstract class TypeSpecNamedElementMixin(node: ASTNode) :
    ASTWrapperPsiElement(node),
    TypeSpecNamedElement {

    /**
     * The name's [TypeSpecIdentifier] node — the **last** segment for a dotted
     * `namespace_statement`, the (only) segment for everything else. Navigation, Find Usages
     * previews and the structure view all point here, not at the declaration's keyword.
     */
    override fun getNameIdentifier(): TypeSpecIdentifier? = TypeSpecPsiUtil.findNameIdentifier(this)

    /** Backtick-stripped text of [getNameIdentifier] ([ADR 0004](../../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D7.3). */
    override fun getName(): String? = TypeSpecPsiUtil.stripBackticks(nameIdentifier?.text)

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
