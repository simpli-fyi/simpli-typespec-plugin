package simpli.fyi.plugins.typespec.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier
import simpli.fyi.plugins.typespec.resolve.TypeSpecReference
import simpli.fyi.plugins.typespec.resolve.TypeSpecResolver

/**
 * Hand-written base class satisfying `getReference()` for the `identifier` rule
 * ([ADR 0004](../../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D1,
 * [plan 02](../../../../../../../../../docs/plans/02-navigation.md) "Files to create" §
 * `resolve/TypeSpecReference.kt"). Grammar-Kit only ever sees this class name as a *string*
 * `mixin=` attribute on the `identifier` rule in `TypeSpec.bnf` — same technique as
 * [TypeSpecNamedElementMixin], never loaded by the generator itself.
 *
 * `getReference()` returns a real [TypeSpecReference] only in a *reference position*
 * ([TypeSpecResolver.isReferencePosition]) — never when this identifier IS a declaration's own
 * name (that would make a declaration hold a reference to itself) and never for the segments of
 * a `namespace` statement's own dotted name (ADR 0004/plan 02 risk 4).
 */
abstract class TypeSpecIdentifierMixin(node: ASTNode) :
    ASTWrapperPsiElement(node),
    TypeSpecIdentifier {

    override fun getReference(): PsiReference? =
        if (TypeSpecResolver.isReferencePosition(this)) TypeSpecReference(this) else null
}
