package simpli.fyi.plugins.typespec.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import com.intellij.psi.util.PsiTreeUtil
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.TypeSpecLanguage

/**
 * The PSI file for `.tsp` files. Backed by the generated Grammar-Kit parser tree of
 * `TypeSpecParserDefinition` (M5b; flat tree in ADR 0005 M4b) — a real `PsiFile` subclass so
 * file language resolves to [TypeSpecLanguage] instead of falling back to `PsiPlainTextFileImpl`
 * (ADR 0003 F1 / ADR 0005).
 *
 * The four accessors below are [ADR 0004](../../../../../../../docs/adr/0004-reference-resolution-approach.md)
 * D7.4's amendment: M5.5's resolver reads them on every file it touches, so the grammar
 * knowledge of "what is a top-level declaration" lives here once, not scattered across two
 * milestones. Plain `PsiTreeUtil` queries, not caches — caching is M5.5's problem.
 */
class TypeSpecFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TypeSpecLanguage.INSTANCE) {

    override fun getFileType() = TypeSpecFileType.INSTANCE

    override fun toString(): String = "TypeSpec File"

    /** Every `import` statement at the top level of this file, in source order. */
    fun getImportStatements(): List<TypeSpecImportStatement> =
        PsiTreeUtil.getChildrenOfTypeAsList(this, TypeSpecImportStatement::class.java)

    /** Every `using` statement at the top level of this file, in source order. */
    fun getUsingStatements(): List<TypeSpecUsingStatement> =
        PsiTreeUtil.getChildrenOfTypeAsList(this, TypeSpecUsingStatement::class.java)

    /**
     * This file's own `namespace` statement, if any — trivial by construction: a blockless
     * `namespace Foo;` contains the rest of the file as its own children (see `TypeSpec.bnf`'s
     * `namespace_statement` rule), so the file's namespace is simply its first direct
     * `namespace_statement` child, block or blockless alike.
     */
    fun getFileNamespace(): TypeSpecNamespaceStatement? =
        PsiTreeUtil.getChildOfType(this, TypeSpecNamespaceStatement::class.java)

    /**
     * Every top-level named declaration in this file (`namespace`, `model`), in source order.
     * Import/using statements are not declarations and are excluded — see [getImportStatements]
     * / [getUsingStatements] for those.
     */
    fun getTopLevelDeclarations(): List<TypeSpecNamedElement> =
        PsiTreeUtil.getChildrenOfTypeAsList(this, TypeSpecNamedElement::class.java)
}
