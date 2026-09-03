package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReference
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
        ProgressManager.checkCanceled()
        val name = TypeSpecPsiUtil.stripBackticks(identifier.text) ?: return ResolveResult.EMPTY_ARRAY
        val qualifiedName = identifier.parent as? TypeSpecQualifiedName ?: return ResolveResult.EMPTY_ARRAY
        val segments = qualifiedName.identifierList
        val index = segments.indexOf(identifier)
        if (index < 0) return ResolveResult.EMPTY_ARRAY

        val file = identifier.containingFile as? TypeSpecFile ?: return ResolveResult.EMPTY_ARRAY
        val candidateFiles = TypeSpecImportGraph.transitiveClosure(file)

        val matches: List<TypeSpecNamedElement> = if (index == 0) {
            resolveLeadingSegment(identifier, name, candidateFiles)
        } else {
            resolveTrailingSegment(segments[index - 1], name, candidateFiles)
        }
        return PsiElementResolveResult.createResults(matches)
    }

    private fun resolveLeadingSegment(
        identifier: TypeSpecIdentifier,
        name: String,
        candidateFiles: Set<TypeSpecFile>,
    ): List<TypeSpecNamedElement> {
        val chain = TypeSpecScope.chainFor(identifier)
        for (scope in chain) {
            ProgressManager.checkCanceled()

            val direct = candidateFiles.flatMap { TypeSpecFileDeclarations.of(it).find(name, scope) }
            if (direct.isNotEmpty()) return direct.distinct()

            val usingTargets = candidateFiles
                .flatMap { TypeSpecScope.usingsVisibleIn(scope, it) }
                .distinct()
            if (usingTargets.isNotEmpty()) {
                val viaUsing = usingTargets.flatMap { target ->
                    candidateFiles.flatMap { f -> TypeSpecFileDeclarations.of(f).find(name, target) }
                }
                if (viaUsing.isNotEmpty()) return viaUsing.distinct()
            }
        }
        return emptyList()
    }

    private fun resolveTrailingSegment(
        previousSegment: TypeSpecIdentifier,
        name: String,
        candidateFiles: Set<TypeSpecFile>,
    ): List<TypeSpecNamedElement> {
        ProgressManager.checkCanceled()
        val previousTargets = (previousSegment.reference as? PsiPolyVariantReference)
            ?.multiResolve(false)
            ?.mapNotNull { it.element as? TypeSpecNamespaceStatement }
            ?.distinct()
            ?: emptyList()

        return previousTargets.flatMap { namespace ->
            val namespacePath = TypeSpecScope.fullPathOf(namespace)
            candidateFiles.flatMap { file -> TypeSpecFileDeclarations.of(file).find(name, namespacePath) }
        }
    }
}
