package simpli.fyi.plugins.typespec.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement

/**
 * Turns on Shift-F6's in-place rename for the seven declaration kinds whose only naming
 * constraint is `isIdentifier` (M6.5k, ADR 0012 D8): `model`, `op`, `interface`, `enum`,
 * `union`, `alias`, `scalar`. `dec`, `fn`, `namespace`, every kind
 * [TypeSpecRenameVetoCondition] refuses, and every declaration whose name is currently
 * backtick-quoted keep the Rename dialog — see [TypeSpecRenameScope.isInplaceRenameable].
 *
 * **Overrides [isMemberInplaceRenameAvailable] and NOTHING else.** In particular,
 * [isInplaceRenameAvailable] is deliberately left at its inherited `false` (ADR 0012 F10a).
 * Overriding both is actively harmful, and fails *silently*: `VariableInplaceRenameHandler`
 * would then also report `isRenaming` for the same element,
 * `RenameHandlerRegistry.doGetRenameHandlers`'s title-keyed `TreeMap` would hold two entries for
 * it, and its `size() != 1` branch **removes `MemberInplaceRenameHandler`** from the result
 * (ADR 0012 F10b) — the resolved handler would silently become the *variable* renamer instead of
 * the member one, with no error or log anywhere. This is the least guessable failure mode in
 * this milestone; do not "complete" this class by adding the other override.
 *
 * Every other [RefactoringSupportProvider] method keeps its inherited default: in particular
 * `isSafeDeleteAvailable` stays `false` (safe delete is out of scope) and every `get…Handler()`
 * stays `null`.
 */
class TypeSpecRefactoringSupportProvider : RefactoringSupportProvider() {

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return TypeSpecRenameScope.isInplaceRenameable(element)
    }
}
