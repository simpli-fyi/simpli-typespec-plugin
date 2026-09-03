package simpli.fyi.plugins.typespec.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import simpli.fyi.plugins.typespec.psi.TypeSpecImportStatement
import simpli.fyi.plugins.typespec.resolve.TypeSpecImportReference

/**
 * Hand-written base class satisfying `getReference()` for the `import_statement` rule
 * ([ADR 0010](../../../../../../../../../docs/adr/0010-library-import-resolution.md),
 * [plan 05](../../../../../../../../../docs/plans/05-import-and-decorator-navigation.md) M5.6b).
 * Grammar-Kit only ever sees this class name as a *string* `mixin=` attribute on
 * `import_statement` in `TypeSpec.bnf` — same technique as
 * [TypeSpecNamedElementMixin]/[TypeSpecIdentifierMixin], never loaded by the generator itself.
 *
 * One reference for the whole path, with an explicit [TextRange] over the `STRING` token's
 * *contents* (inside the quotes), expressed relative to this `import_statement` node — not a
 * per-segment `FileReferenceSet`. An explicit range means no `ElementManipulator` is required by
 * [TypeSpecImportReference]'s constructor.
 */
abstract class TypeSpecImportStatementMixin(node: ASTNode) :
    ASTWrapperPsiElement(node),
    TypeSpecImportStatement {

    override fun getReference(): PsiReference? {
        val stringToken = string ?: return null
        val text = stringToken.text
        // A well-formed import target is `"..."` — at least the two quote characters. An
        // unterminated/malformed literal (no closing quote reached) falls back to no reference
        // rather than guessing a range.
        if (text.length < 2 || !text.startsWith("\"")) return null

        val startInElement = stringToken.startOffsetInParent + 1
        val endInElement = stringToken.startOffsetInParent + text.length - (if (text.endsWith("\"")) 1 else 0)
        if (endInElement <= startInElement) return null

        return TypeSpecImportReference(this, TextRange(startInElement, endInElement))
    }
}
