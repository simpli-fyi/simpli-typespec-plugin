package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.PsiElement

/**
 * Hand-written interface used only to verify the Grammar-Kit `mixin=` + `implements=`
 * seam end-to-end (ADR 0006 D7, D10.1). The generated `TypeSpecThrowaway` PSI
 * interface extends this; [simpli.fyi.plugins.typespec.psi.impl.TypeSpecThrowawayMixin]
 * satisfies it by ordinary Kotlin inheritance, so Grammar-Kit never needs to know
 * anything about the method — see ADR 0006 F8 on why `methods=[...]` is banned instead.
 *
 * Removed in M5b, which replaces this throwaway seam check with the real
 * `TypeSpecNamedElement` contract (ADR 0004 D7).
 */
interface TypeSpecThrowawayIface : PsiElement {
    fun throwawaySeamMarker(): String
}
