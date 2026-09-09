package simpli.fyi.plugins.typespec.refactoring

import com.intellij.psi.PsiElement
import simpli.fyi.plugins.typespec.psi.TypeSpecAliasStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecEnumMember
import simpli.fyi.plugins.typespec.psi.TypeSpecEnumStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecInterfaceOperation
import simpli.fyi.plugins.typespec.psi.TypeSpecInterfaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecModelProperty
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNames
import simpli.fyi.plugins.typespec.psi.TypeSpecOpStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecScalarStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecTemplateParameter
import simpli.fyi.plugins.typespec.psi.TypeSpecUnionStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecUnionVariant
import simpli.fyi.plugins.typespec.stubs.TypeSpecNodeModules

/**
 * The single rename-eligibility predicate, with two callers (M6.5k, ADR 0012 D7/D8).
 *
 * [isRenameable] is [TypeSpecRenameVetoCondition]'s veto condition inverted, moved here
 * **verbatim** so the dialog path (the veto) and the in-place path (below) cannot drift apart —
 * [TypeSpecRenameVetoCondition] now delegates to it rather than repeating it.
 *
 * [isInplaceRenameable] narrows [isRenameable] further, for the subset of kinds whose *only*
 * naming constraint is [simpli.fyi.plugins.typespec.refactoring.TypeSpecNamesValidator.isIdentifier]
 * (ADR 0012 D8): `dec`/`fn` have an extra, element-specific constraint enforced by a
 * `RenameInputValidator` that the in-place path skips entirely (F2/F8b), so they are excluded
 * here regardless of [isRenameable]. `namespace` is excluded because its blast radius (every
 * file reopening it) deserves the dialog's Preview Usages, not a silent inline edit.
 */
object TypeSpecRenameScope {

    /**
     * `true` when [element] is eligible for rename at all — i.e. when
     * [TypeSpecRenameVetoCondition] would **not** veto it. Moved verbatim from M6.5h's veto
     * branches, inverted: not a member/template kind, not under `node_modules`, and the current
     * name is index-keyable ([TypeSpecNames.isIndexKeyableName] — ADR 0012 D7, corrected; not
     * [TypeSpecNames.isBareIdentifier]).
     */
    fun isRenameable(element: PsiElement): Boolean {
        // Member / template-parameter resolution is out of scope, so their usages would not be
        // found by TypeSpecResolver — renaming them would rename the declaration and nothing
        // else (ADR 0012 D5).
        if (element is TypeSpecModelProperty ||
            element is TypeSpecEnumMember ||
            element is TypeSpecUnionVariant ||
            element is TypeSpecInterfaceOperation ||
            element is TypeSpecTemplateParameter
        ) {
            return false
        }

        // Library source (ADR 0010): not renameable (ADR 0012 D9). Reuse the one predicate that
        // already gates stub building and the search scope so all three cannot drift apart.
        val virtualFile = element.containingFile?.virtualFile
        if (virtualFile != null && TypeSpecNodeModules.isUnder(virtualFile)) {
            return false
        }

        // A current name the word index cannot key on (e.g. `` `my name` ``) yields zero
        // candidate files from ReferencesSearch, so renaming it would silently leave every
        // usage pointing at a name that no longer exists (ADR 0012 D7). Note the asymmetry:
        // renaming *to* such a name is fine — the search runs on the old name.
        if (element is TypeSpecNamedElement) {
            val currentName = element.name
            if (currentName != null && !TypeSpecNames.isIndexKeyableName(currentName)) {
                return false
            }
        }

        return true
    }

    /**
     * `true` when [element] may be renamed **in place**, in the editor, via
     * [TypeSpecRefactoringSupportProvider]. Strictly narrower than [isRenameable]: also requires
     * one of the seven plain declaration kinds — not `dec`/`fn` (element-specific constraint
     * lives in a `RenameInputValidator` the inline path skips, ADR 0012 D8/F8b) and not
     * `namespace` (blast radius deserves the dialog's Preview Usages) — and requires the name to
     * be **written bare in the source**.
     *
     * The backtick gate is deliberately `element.nameIdentifier?.text == element.name`, not
     * `TypeSpecNames.isBareIdentifier(nameIdentifier.text)`: this is a third, distinct question
     * from "spellable bare" (`isBareIdentifier`) and "index-keyable" (`isIndexKeyableName`) — it
     * asks how the name is *written in the file*, because the in-place template edits exactly
     * that text. `element.name` is backtick-stripped, so comparing against it alone would admit
     * `` `Foo` `` even though the template would still show raw backticks.
     */
    fun isInplaceRenameable(element: PsiElement): Boolean {
        if (!isRenameable(element)) return false

        val isPlainKind = element is TypeSpecModelStatement ||
            element is TypeSpecOpStatement ||
            element is TypeSpecInterfaceStatement ||
            element is TypeSpecEnumStatement ||
            element is TypeSpecUnionStatement ||
            element is TypeSpecAliasStatement ||
            element is TypeSpecScalarStatement
        if (!isPlainKind) return false

        if (element !is TypeSpecNamedElement) return false
        return element.nameIdentifier?.text == element.name
    }
}
