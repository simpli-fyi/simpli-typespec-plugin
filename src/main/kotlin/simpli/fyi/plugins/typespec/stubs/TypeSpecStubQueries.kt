package simpli.fyi.plugins.typespec.stubs

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.StubIndex
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.resolve.NamespacePath
import simpli.fyi.plugins.typespec.resolve.TypeSpecScope
import simpli.fyi.plugins.typespec.resolve.TypeSpecSearchScopes

/**
 * The query side of [TypeSpecDeclarationNameIndex] (plan 06 M6.5b) — everything a resolver needs
 * and nothing it can misuse. `node_modules` is excluded twice over: no stub is ever built for a
 * file under it ([TypeSpecFileElementType.shouldBuildStubFor], ADR 0011 D4) and
 * [TypeSpecSearchScopes.tspScope] — the query scope used here — filters it again as defence in
 * depth.
 */
object TypeSpecStubQueries {

    /**
     * Declarations named [name], project-wide, optionally filtered to those whose stub-recorded
     * [TypeSpecDeclStub.namespacePath] equals [path] exactly. [path] is `null` to mean "any
     * namespace" (the caller has already decided which namespace to ask about — this never does
     * a "starts with" match; ADR 0011 D3 is a name lookup plus a per-hit string compare, not a
     * second index).
     *
     * Returns an empty list, never throws, when the index is unavailable
     * ([DumbService.isDumb]) — a resolve during indexing degrades to "unresolved by this tier",
     * exactly like every other tier's dumb-mode contract in this resolver (never an
     * `IndexNotReadyException` reaching a caller).
     */
    fun declarationsNamed(project: Project, name: String, path: NamespacePath?): List<TypeSpecNamedElement> {
        if (DumbService.isDumb(project)) return emptyList()

        val scope = TypeSpecSearchScopes.tspScope(project)
        val hits = StubIndex.getElements(
            TypeSpecDeclarationNameIndex.KEY,
            name,
            project,
            scope,
            TypeSpecNamedElement::class.java,
        )
        if (path == null) return hits.toList()

        return hits.filter { element -> matchesEnclosingPath(element, name, path) }
    }

    /**
     * `true` when [element]'s enclosing namespace equals [path] exactly. For the 9
     * non-`namespace_statement` kinds this is a direct comparison against
     * [TypeSpecDeclStub.namespacePath]. A `namespace_statement` is the irregular case
     * ([TypeSpecDeclStub] KDoc): `namespace A.B.C;` was indexed under each of `A`, `B`, `C`, but
     * a single stub only records ONE enclosing path — the path of whatever contains the whole
     * `namespace A.B.C;` statement, i.e. `A`'s enclosing path, not `B`'s or `C`'s. So for a hit
     * whose [TypeSpecDeclStub.ownSegments] is non-empty, [name]'s position within that list is
     * located first, and the segments *before* it are appended to
     * [TypeSpecDeclStub.namespacePath] to reconstruct the enclosing path the matched segment
     * actually denotes — exactly mirroring [simpli.fyi.plugins.typespec.resolve.TypeSpecFileDeclarations]'s
     * own per-segment prefix walk, so a stub lookup and a PSI lookup can never disagree.
     *
     * Tries [StubBasedPsiElementBase.getGreenStub] first — no AST load, the common case for a
     * project-wide hit in a file nothing has opened yet. `getGreenStub()` can return `null` for a
     * hit whose containing file's AST is *already* loaded elsewhere (e.g. an open editor) — verified
     * empirically: a stub-based PSI element created while re-resolving a fully-parsed file's own
     * stub tree does not always rebind its `myStub` field, and `getGreenStub()`'s own fallback (via
     * the containing file's cached green stub tree) does not always find it either. In that case —
     * and only in that case — this falls back to [TypeSpecScope.pathOf]/[TypeSpecScope.segmentsOf],
     * the exact same PSI walk [simpli.fyi.plugins.typespec.resolve.TypeSpecFileDeclarations] already
     * trusts; the AST for that one hit's file is already resident in memory at that point, so this
     * does not parse anything new — it stays within the "AST loaded only for what the caller already
     * touched" budget, not "AST loaded for a rejected candidate".
     */
    private fun matchesEnclosingPath(element: TypeSpecNamedElement, name: String, path: NamespacePath): Boolean {
        val stub = (element as? StubBasedPsiElementBase<*>)?.greenStub as? TypeSpecDeclStub
        val enclosing = if (stub != null) {
            val ownSegments = stub.ownSegments
            if (ownSegments.isEmpty()) {
                stub.namespacePath.toSegments()
            } else {
                val index = ownSegments.indexOf(name)
                if (index < 0) return false
                stub.namespacePath.toSegments() + ownSegments.subList(0, index)
            }
        } else if (element is TypeSpecNamespaceStatement) {
            val ownSegments = TypeSpecScope.segmentsOf(element)
            val index = ownSegments.indexOf(name)
            if (index < 0) return false
            TypeSpecScope.pathOf(element).segments + ownSegments.subList(0, index)
        } else {
            TypeSpecScope.pathOf(element).segments
        }
        return enclosing == path.segments
    }

    private fun String.toSegments(): List<String> = if (isEmpty()) emptyList() else split(".")
}
