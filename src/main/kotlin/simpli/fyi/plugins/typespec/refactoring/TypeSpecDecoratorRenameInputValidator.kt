package simpli.fyi.plugins.typespec.refactoring

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import simpli.fyi.plugins.typespec.psi.TypeSpecDecStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecFnStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNames

/**
 * Refuses, in the rename dialog itself, a `dec`/`fn` rename to a name that
 * [simpli.fyi.plugins.typespec.resolve.TypeSpecDecoratorReference.handleElementRename] would
 * have to reject anyway (M6.5j, plan 07, ADR 0012 F6). A decorator usage is `"@" {QualifiedName}`
 * / `"@@" {QualifiedName}` over *bare* identifiers only, so a name that needs backticks (a
 * keyword, or one containing whitespace) is unrepresentable in `@doc`/`@@doc` form — without
 * this, the dialog would accept it (it *is* representable for the declaration itself) and the
 * refactoring would fail mid-flight while rewriting the first usage.
 *
 * **[TypeSpecNames.isBareIdentifier] is the correct predicate here, deliberately not
 * [TypeSpecNames.isIndexKeyableName]** (plan 07 §M6.5f approach 2b's comparison table): this
 * constraint is about *spelling* — what the `.flex` decorator patterns can lex — not about
 * word-index recall, which is what [TypeSpecNames.isIndexKeyableName] answers. Conflating the
 * two predicates has already produced one defect in this milestone series (M6.5h's veto,
 * corrected in plan 07's third amendment); do not repeat it here.
 *
 * [com.intellij.refactoring.rename.RenameUtil.isValidName] gives a matching
 * [com.intellij.refactoring.rename.RenameInputValidator] precedence over
 * [com.intellij.lang.refactoring.NamesValidator] **entirely** (ADR 0012 F2) — so [isInputValid]
 * must accept every name [TypeSpecNames.isBareIdentifier] accepts and reject only the rest;
 * narrowing this predicate would silently make `dec`/`fn` un-renameable to names the dialog
 * ought to allow, since [TypeSpecKeywords][simpli.fyi.plugins.typespec.psi.TypeSpecKeywords] is
 * the only thing [TypeSpecNames.isBareIdentifier] excludes here and a keyword genuinely cannot
 * appear as a decorator's name (the `.flex` decorator patterns lex over identifiers, not
 * keywords).
 *
 * **Caveat, for the record:** this validator is consulted only by `RenameUtil.isValidName`, i.e.
 * only on the rename-dialog path (ADR 0012 F8b). In-place rename bypasses
 * [com.intellij.refactoring.rename.RenameInputValidator] entirely, which is exactly why M6.5k
 * (selective in-place rename) excludes `dec`/`fn` from the in-place door rather than relying on
 * this validator to keep applying there.
 */
class TypeSpecDecoratorRenameInputValidator : com.intellij.refactoring.rename.RenameInputValidator {

    override fun getPattern(): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement().with(
            object : PatternCondition<PsiElement>("TypeSpecDecOrFnStatement") {
                override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean =
                    t is TypeSpecDecStatement || t is TypeSpecFnStatement
            },
        )

    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean =
        TypeSpecNames.isBareIdentifier(newName.trim())
}
