package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache

/**
 * One dotted segment of a `@Ns.name` / `@@Ns.name` decorator
 * ([ADR 0009](../../../../../../../../docs/adr/0009-decorator-reference-strategy.md) option B,
 * [plan 05](../../../../../../../../docs/plans/05-import-and-decorator-navigation.md) M5.6d).
 * [element] is the `decorator_application`/`augment_decorator_statement` host node —
 * [TypeSpecDecoratorReferenceHost] hangs one instance of this class per segment on it, each with
 * its own [range]. [names] is the full already-split, already-stripped segment list (decorator
 * segments cannot be backticked); [index] is which of them this reference resolves.
 *
 * `TypeSpec.<caret>OpenAPI.info` resolves the "OpenAPI" segment (index 1) to the namespace;
 * `TypeSpec.OpenAPI.<caret>info` resolves "info" (index 2) to the `extern dec info` declaration
 * — both through [TypeSpecResolver.multiResolve]'s name-based entry point (plan 06 M6.5c),
 * exactly the way [TypeSpecReference] resolves an `identifier` PSI segment.
 */
class TypeSpecDecoratorReference(
    element: PsiElement,
    range: TextRange,
    private val names: List<String>,
    private val index: Int,
) : PsiPolyVariantReferenceBase<PsiElement>(element, range) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, RESOLVER, /* needToPreventRecursion = */ true, incompleteCode)
            ?: ResolveResult.EMPTY_ARRAY

    // Bare standard-library decorators (`@doc`, `@key`, ...) and any decorator whose owning
    // library a file does not import never resolve — a soft reference keeps that a non-event
    // instead of painting the file red (ADR 0009 §Consequences, mirrors ADR 0004 D3).
    override fun isSoft(): Boolean = true

    override fun getVariants(): Array<Any> = emptyArray()

    private companion object {
        val RESOLVER = ResolveCache.PolyVariantResolver<TypeSpecDecoratorReference> { ref, _ ->
            TypeSpecResolver.multiResolve(ref.names, ref.index, ref.element)
        }
    }
}
