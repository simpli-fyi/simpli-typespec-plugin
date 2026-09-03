package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier

/**
 * The one reference class for every TypeSpec name-position identifier
 * ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D1,
 * [plan 02](../../../../../../../../docs/plans/02-navigation.md)). Feeds Ctrl/Cmd-click,
 * *Go To Declaration*, and (later) Find Usages / completion / rename, all from one
 * implementation.
 *
 * `rangeInElement` is the whole [TypeSpecIdentifier] — the identifier node wraps exactly one
 * name — so `Foo.<caret>Bar` and `<caret>Foo.Bar` navigate independently, one reference per
 * segment.
 */
class TypeSpecReference(
    element: TypeSpecIdentifier,
) : PsiPolyVariantReferenceBase<TypeSpecIdentifier>(element, TextRange(0, element.textLength)) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, RESOLVER, /* needToPreventRecursion = */ true, incompleteCode)
            ?: ResolveResult.EMPTY_ARRAY

    // ADR 0004 D3 — TypeSpec built-in types and library types never resolve; a soft reference
    // keeps that a non-event instead of painting the file red.
    override fun isSoft(): Boolean = true

    // M6's completion contributor fills this from TypeSpecScope; empty on purpose here.
    override fun getVariants(): Array<Any> = emptyArray()

    private companion object {
        val RESOLVER = ResolveCache.PolyVariantResolver<TypeSpecReference> { ref, _ ->
            TypeSpecResolver.multiResolve(ref.element)
        }
    }
}
