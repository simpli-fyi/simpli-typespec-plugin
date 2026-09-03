package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecImportStatement

/**
 * `import "…"` becomes a reference
 * ([ADR 0010](../../../../../../../../docs/adr/0010-library-import-resolution.md),
 * [plan 05](../../../../../../../../docs/plans/05-import-and-decorator-navigation.md) M5.6b).
 * One instance per `import_statement`, covering both forms — relative
 * (`import "../master-data/branch.tsp";`) and bare/library (`import "@typespec/openapi";`) —
 * since [TypeSpecImportResolver] already handles both.
 *
 * `isSoft() == true` (ADR 0010 D5): an import whose target does not exist — a typo, or
 * `node_modules` simply not installed yet — resolves to nothing and paints nothing red.
 */
class TypeSpecImportReference(
    element: TypeSpecImportStatement,
    range: TextRange,
) : PsiReferenceBase<TypeSpecImportStatement>(element, range, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        val file = element.containingFile as? TypeSpecFile ?: return null
        return TypeSpecImportResolver.resolve(file, value)
    }

    // File rename does not rewrite imports yet (a stated limitation, not a silent one) — M6.5
    // territory, same as TypeSpecNamedElementMixin.setName().
    override fun handleElementRename(newElementName: String): PsiElement =
        throw IncorrectOperationException("Renaming an import target is not supported yet (plan 05 M5.6b)")

    override fun bindToElement(element: PsiElement): PsiElement =
        throw IncorrectOperationException("Rebinding an import target is not supported yet (plan 05 M5.6b)")
}
