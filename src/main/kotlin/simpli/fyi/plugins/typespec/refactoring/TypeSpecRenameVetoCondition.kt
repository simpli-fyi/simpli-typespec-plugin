package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import simpli.fyi.plugins.typespec.TypeSpecLanguage

/**
 * The half of rename that actually refuses (M6.5h, ADR 0012). `TypeSpecRenamePsiElementProcessor`
 * only opts a handful of kinds *in*; every kind it does not mention — the member/template kinds
 * that stay in scope for a future member-resolution milestone, plus `TypeSpecNamespaceStatement`,
 * `TypeSpecDecStatement`, `TypeSpecFnStatement` before M6.5i/j land — falls through to the
 * platform default `RenamePsiElementProcessor`, which *would* offer rename. This
 * `vetoRenameCondition` (consulted by `PsiElementRenameHandler.isVetoed`, ADR 0012 F3) is what
 * actually blocks the kinds whose usages cannot be found.
 *
 * `vetoRenameCondition` is a **global** extension point — every language's rename handler
 * consults every registered condition — so [value] guards on [TypeSpecLanguage] first and
 * returns `false` immediately for anything that is not TypeSpec PSI. A veto that leaked past
 * that guard would silently disable rename for other languages in the same IDE.
 *
 * Runs on the EDT ([com.intellij.refactoring.rename.PsiElementRenameHandler] calls it
 * synchronously before the rename dialog even opens), so every check here is a type test, a
 * `getName()` string check, or a `VirtualFile` ancestor walk — nothing resolves a reference or
 * touches the index.
 *
 * The actual eligibility predicate lives in [TypeSpecRenameScope.isRenameable] (M6.5k) — this
 * class only guards the [TypeSpecLanguage] type check and inverts the result, so the dialog path
 * (here) and the in-place path ([TypeSpecRefactoringSupportProvider]) cannot drift apart.
 */
class TypeSpecRenameVetoCondition : Condition<PsiElement> {

    override fun value(element: PsiElement): Boolean {
        if (element.language != TypeSpecLanguage.INSTANCE) return false

        return !TypeSpecRenameScope.isRenameable(element)
    }
}
