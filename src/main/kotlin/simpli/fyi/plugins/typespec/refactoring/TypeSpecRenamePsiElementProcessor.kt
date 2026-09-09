package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import simpli.fyi.plugins.typespec.psi.TypeSpecAliasStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecDecStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecEnumStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecFnStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecInterfaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecOpStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecScalarStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecUnionStatement
import simpli.fyi.plugins.typespec.resolve.NamespacePath
import simpli.fyi.plugins.typespec.resolve.TypeSpecScope
import simpli.fyi.plugins.typespec.stubs.TypeSpecNodeModules
import simpli.fyi.plugins.typespec.stubs.TypeSpecStubQueries

/**
 * Scope and search-flag half of rename (M6.5h, ADR 0012). The write path itself
 * ([simpli.fyi.plugins.typespec.psi.impl.TypeSpecNamedElementMixin.setName],
 * [simpli.fyi.plugins.typespec.resolve.TypeSpecIdentifierManipulator]) landed in M6.5g and is
 * untouched here.
 *
 * The EP interface plugins register against is `RenamePsiElementProcessorBase` (ADR 0012 F4);
 * `RenamePsiElementProcessor`, which this subclasses, is the concrete class plugins extend in
 * 2025.2.
 *
 * **Do not override `findReferences`.** The inherited default is already exactly right for this
 * language: `RenamePsiElementProcessorBase.findReferences` calls
 * `ReferencesSearch.search(element, scope)`, which runs the word-index prefilter and then, per
 * candidate, `TypeSpecReference.isReferenceTo` — which in turn calls `TypeSpecResolver`, backed
 * by the stub index and uncapped since M6.5c (ADR 0011). Adding a search override here would be
 * redundant at best and a recall regression at worst.
 */
class TypeSpecRenamePsiElementProcessor : RenamePsiElementProcessor() {

    /**
     * The renameable set is exactly the declarations whose usages
     * [simpli.fyi.plugins.typespec.resolve.TypeSpecResolver] can find (ADR 0012 D5):
     * `model`, `op`, `interface`, `enum`, `union`, `alias`, `scalar`.
     *
     * `TypeSpecNamespaceStatement` was added in M6.5i; `TypeSpecDecStatement` /
     * `TypeSpecFnStatement` here in M6.5j — their usages (`@doc`, `@@doc`) resolve through
     * [simpli.fyi.plugins.typespec.resolve.TypeSpecDecoratorReference], whose own
     * `handleElementRename` (not [simpli.fyi.plugins.typespec.resolve.TypeSpecIdentifierManipulator])
     * does the rewrite. [TypeSpecRenameVetoCondition] — not this predicate — is what refuses the
     * member/template kinds, `node_modules` declarations, and non-bare current names.
     */
    override fun canProcessElement(element: PsiElement): Boolean {
        return element is TypeSpecNamedElement &&
            (element is TypeSpecModelStatement ||
                element is TypeSpecOpStatement ||
                element is TypeSpecInterfaceStatement ||
                element is TypeSpecEnumStatement ||
                element is TypeSpecUnionStatement ||
                element is TypeSpecAliasStatement ||
                element is TypeSpecScalarStatement ||
                element is TypeSpecNamespaceStatement ||
                element is TypeSpecDecStatement ||
                element is TypeSpecFnStatement)
    }

    /**
     * A namespace rename is a *whole-namespace* operation (ADR 0012 D6, M6.5i): renaming one of
     * several `namespace Shared;` declarations and leaving the rest behind would silently split
     * the namespace, so every other reopening of the *same* namespace is added to [allRenames].
     *
     * `RenamePsiElementProcessorBase` also declares a four-argument overload taking a
     * `SearchScope`; its default body delegates to this three-argument one, so overriding only
     * this one is sufficient (ADR 0012, M6.5i approach 2).
     *
     * **The wrong-segment hazard (load-bearing — read before touching the filter below).**
     * [TypeSpecStubQueries.declarationsNamed] finds a declaration by name across the *whole*
     * project, and [simpli.fyi.plugins.typespec.stubs.TypeSpecDeclarationNameIndex] keys a
     * dotted `namespace A.B.C;` under **every** segment — `A`, `B` and `C` alike. So a query for
     * name `Shared` can legitimately return a declaration written `namespace Shared.Sub;` — a
     * real reopening of `Shared` — whose [TypeSpecNamedElement.getName] is **`Sub`**, because
     * [simpli.fyi.plugins.typespec.psi.impl.TypeSpecNamedElementMixin] always names a namespace
     * declaration by its *final* segment. Adding that element to [allRenames] would make
     * `RenameProcessor` call `setName(newName)` on it, rewriting `Sub` — not `Shared` — turning
     * `namespace Shared.Sub;` into `namespace Shared.Renamed;` in a file the user never opened,
     * while the `Shared` actually being renamed is left untouched. That is silent corruption in
     * an unopened file. The `segmentsOf(it).lastOrNull() == name` filter below is what removes
     * such hits; deleting it as "redundant" is the single most likely regression here. Anything
     * it filters out is a genuine reopening of the namespace being renamed and is reported
     * instead, in [findExistingNameConflicts], so the namespace is not silently split.
     *
     * **First statement: [TypeSpecAliasedDocuments.reconcileBeforeWrite] (ADR 0015, plan 09
     * M6.5m)**, unconditionally and before the namespace-only early return below. This method is
     * the one seam `RenameProcessor.doRun()` calls — for both the in-place and the dialog rename
     * path, since `MemberInplaceRenamer.MyRenameProcessor extends RenameProcessor` — on the EDT,
     * outside any read or write action, before usage search and before
     * `commitAllDocumentsUnderProgress()`. That is the only point where a `Document` save and a
     * synchronous VFS refresh are both legal, which is why the reconciliation call lives here and
     * not in `RefactoringHelper.prepareOperation` (which runs inside `ReadAction.compute` and can
     * do neither). A naive insertion below the `namespace` early return would only reconcile
     * namespace renames, so it runs first, for every renameable kind this processor handles.
     */
    override fun prepareRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
    ) {
        TypeSpecAliasedDocuments.reconcileBeforeWrite(element.project, element, allRenames)
        val namespace = element as? TypeSpecNamespaceStatement ?: return
        val name = namespace.name ?: return
        val project = namespace.project
        if (DumbService.isDumb(project)) {
            throw IncorrectOperationException(
                "Cannot rename namespace '$name' while indexing is in progress: " +
                    "the set of files declaring this namespace cannot be determined.",
            )
        }
        val enclosing = NamespacePath(TypeSpecScope.fullPathOf(namespace).segments.dropLast(1))
        TypeSpecStubQueries.declarationsNamed(project, name, enclosing)
            .filterIsInstance<TypeSpecNamespaceStatement>()
            .filter { it != namespace }
            .filter { TypeSpecScope.segmentsOf(it).lastOrNull() == name }
            .filterNot { candidate ->
                val virtualFile = candidate.containingFile?.virtualFile
                virtualFile != null && TypeSpecNodeModules.isUnder(virtualFile)
            }
            .forEach { allRenames[it] = newName }
    }

    /**
     * Reports every declaration that [prepareRenaming]'s wrong-segment filter excluded: a
     * genuine reopening of the namespace being renamed whose *own* name differs (e.g.
     * `namespace Shared.Sub;` when renaming `Shared`). Left silently alone, that reopening would
     * still say `Shared` after the rename, which now denotes nothing — a silently split
     * namespace (ADR 0012 D6, M6.5i approach 4). This is a conflict with a Continue option, not
     * a hard refusal: a hard refusal would make `namespace Shared;` un-renameable in any project
     * that anywhere writes `namespace Shared.Sub;`, which is a common, legitimate layout.
     *
     * This is deliberately narrow — one mechanical report about mid-segment occurrences of the
     * name being renamed — and not the general duplicate-declaration conflict detection that
     * plan 07 defers to a later, annotator-driven milestone.
     */
    override fun findExistingNameConflicts(
        element: PsiElement,
        newName: String,
        conflicts: MultiMap<PsiElement, String>,
    ) {
        val namespace = element as? TypeSpecNamespaceStatement ?: return
        val name = namespace.name ?: return
        val project = namespace.project
        if (DumbService.isDumb(project)) return

        val enclosing = NamespacePath(TypeSpecScope.fullPathOf(namespace).segments.dropLast(1))
        TypeSpecStubQueries.declarationsNamed(project, name, enclosing)
            .filterIsInstance<TypeSpecNamespaceStatement>()
            .filter { it != namespace }
            .filter { TypeSpecScope.segmentsOf(it).lastOrNull() != name }
            .filterNot { candidate ->
                val virtualFile = candidate.containingFile?.virtualFile
                virtualFile != null && TypeSpecNodeModules.isUnder(virtualFile)
            }
            .forEach { leftBehind ->
                val ownName = leftBehind.name ?: return@forEach
                val fileName = leftBehind.containingFile?.name ?: "an unknown file"
                conflicts.putValue(
                    leftBehind,
                    "'$name' is reopened as 'namespace ${TypeSpecScope.fullPathOf(leftBehind).segments.joinToString(".")};' " +
                        "in $fileName; only the final segment ('$ownName') of a namespace " +
                        "declaration can be renamed — that occurrence will not be updated.",
                )
            }
    }

    /**
     * Blind text replacement in a `.tsp` file would rewrite `import "…/widget.tsp"` path
     * literals and `/** @doc */` prose (ADR 0012 D11). The user can still tick the checkbox on
     * for a single refactoring in the dialog; the default is off.
     */
    override fun isToSearchInComments(element: PsiElement): Boolean = false

    /**
     * See [isToSearchInComments] — same rationale (ADR 0012 D11).
     */
    override fun isToSearchForTextOccurrences(element: PsiElement): Boolean = false
}
