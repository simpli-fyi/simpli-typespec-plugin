package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecPsiUtil
import simpli.fyi.plugins.typespec.psi.TypeSpecQualifiedName

/**
 * The entry point and the only place the tier logic lives
 * ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md),
 * [plan 02](../../../../../../../../docs/plans/02-navigation.md)).
 *
 * This milestone implements tiers A (current file, lexically scoped) and B (transitive `import`
 * closure) — [TypeSpecImportGraph.transitiveClosure]. Tier C (project-wide word-index
 * prefilter, `TypeSpecSearchScopes`) is **not** implemented; see the M5.5 report for why this is
 * a deliberate, plan-sanctioned split (plan 02 risk 9, "M5.5a").
 */
object TypeSpecResolver {

    /**
     * `false` when [identifier] is not in a name position at all, or is one of the segments of
     * a `namespace` statement's own dotted name (plan 02 risk 4 — first cut: a namespace's own
     * name is a declaration on every segment, never a reference). Every other
     * [TypeSpecQualifiedName] segment — `using`, `extends`/`is`, property/parameter/return
     * types, template arguments, spread — is a reference position. A declaration's own name
     * (`model`/`op`/.../`alias` identifier) is never wrapped in a [TypeSpecQualifiedName] at
     * all, so it is excluded by construction, without a separate check.
     */
    fun isReferencePosition(identifier: TypeSpecIdentifier): Boolean {
        val qualifiedName = identifier.parent as? TypeSpecQualifiedName ?: return false
        val qualifiedNameParent = qualifiedName.parent
        if (qualifiedNameParent is TypeSpecNamespaceStatement && qualifiedNameParent.qualifiedName === qualifiedName) {
            return false
        }
        return true
    }

    fun multiResolve(identifier: TypeSpecIdentifier): Array<ResolveResult> {
        val matches = resolveSegment(identifier).map { it.second }
        return PsiElementResolveResult.createResults(matches)
    }

    /**
     * Resolves [identifier] together with the [NamespacePath] it denotes, recursing on the
     * *previous* segment via this same function — never via [TypeSpecScope.fullPathOf] on a
     * resolved namespace element. That distinction matters for a dotted namespace declaration
     * (`namespace A.B.C;`): every one of its segments ("A", "B", "C") is indexed in
     * [TypeSpecFileDeclarations] against the *same* [TypeSpecNamespaceStatement] PSI node (there
     * is no separate declaration of the virtual intermediate segments "A"/"A.B"), so
     * `fullPathOf` that one node always yields its one full path ("A.B.C") regardless of which
     * segment resolved to it. The denoted path of a match is instead always reconstructed as
     * "the [NamespacePath] it was found under" + "its own name" — correct for every segment,
     * dotted-sugar or block-nested alike.
     */
    private fun resolveSegment(identifier: TypeSpecIdentifier): List<Pair<NamespacePath, TypeSpecNamedElement>> {
        ProgressManager.checkCanceled()
        val name = TypeSpecPsiUtil.stripBackticks(identifier.text) ?: return emptyList()
        val qualifiedName = identifier.parent as? TypeSpecQualifiedName ?: return emptyList()
        val segments = qualifiedName.identifierList
        val index = segments.indexOf(identifier)
        if (index < 0) return emptyList()

        val file = identifier.containingFile as? TypeSpecFile ?: return emptyList()
        val candidateFiles = TypeSpecImportGraph.transitiveClosure(file)

        return if (index == 0) {
            resolveLeadingSegment(identifier, name, candidateFiles)
        } else {
            val previousPaths = resolveSegment(segments[index - 1]).map { it.first }.distinct()
            previousPaths.flatMap { previousPath ->
                val path = NamespacePath(previousPath.segments + name)
                candidateFiles
                    .flatMap { f -> TypeSpecFileDeclarations.of(f).find(name, previousPath) }
                    .map { path to it }
            }
        }
    }

    private fun resolveLeadingSegment(
        identifier: TypeSpecIdentifier,
        name: String,
        candidateFiles: Set<TypeSpecFile>,
    ): List<Pair<NamespacePath, TypeSpecNamedElement>> {
        val chain = TypeSpecScope.chainFor(identifier)
        for (scope in chain) {
            ProgressManager.checkCanceled()

            val direct = candidateFiles.flatMap { TypeSpecFileDeclarations.of(it).find(name, scope) }
            if (direct.isNotEmpty()) {
                val path = NamespacePath(scope.segments + name)
                return direct.distinct().map { path to it }
            }

            val usingTargets = candidateFiles
                .flatMap { TypeSpecScope.usingsVisibleIn(scope, it) }
                .distinct()
            if (usingTargets.isNotEmpty()) {
                val viaUsing = usingTargets.flatMap { target ->
                    val path = NamespacePath(target.segments + name)
                    candidateFiles.flatMap { f -> TypeSpecFileDeclarations.of(f).find(name, target) }.map { path to it }
                }
                if (viaUsing.isNotEmpty()) return viaUsing.distinctBy { it.second }
            }
        }
        return emptyList()
    }
}
