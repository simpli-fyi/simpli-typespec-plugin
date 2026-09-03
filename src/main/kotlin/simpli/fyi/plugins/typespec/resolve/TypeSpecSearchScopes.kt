package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.stubs.TypeSpecNodeModules

/**
 * The stub-index query scope (ADR 0011, plan 06 M6.5c). Tier C — the project-wide word-index
 * prefilter this class used to expose via `filesContainingWord`/`TIER_C_FILE_CAP` — is deleted
 * outright, not kept behind a flag (ADR 0011 D1): [TypeSpecStubQueries][simpli.fyi.plugins.typespec.stubs.TypeSpecStubQueries]
 * answers "which declarations are named X" directly from the stub index, so there is no file set
 * to cap and no candidate file is ever parsed to find out.
 *
 * `node_modules` exclusion (ADR 0008 perf investigation, ADR 0011 D4) survives, now delegating
 * its path predicate to [TypeSpecNodeModules] — the exact same predicate
 * [simpli.fyi.plugins.typespec.stubs.TypeSpecFileElementType.shouldBuildStubFor] uses at stub
 * **build** time, so build-time exclusion and this query-time filter can never drift apart. This
 * is defence in depth, not the primary mechanism: no stub is ever built for a `node_modules`
 * file in the first place.
 */
object TypeSpecSearchScopes {

    /**
     * The project-wide `.tsp`-only search scope the stub index is queried over, excluding
     * `node_modules` (see class KDoc). A file is excluded when any path segment is literally
     * `node_modules`, matching npm/pnpm/yarn convention; this needs no `NodeJS` plugin dependency
     * and no project-model exclusion the owner's `.iml` does not have.
     */
    fun tspScope(project: Project): GlobalSearchScope =
        GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.projectScope(project),
            TypeSpecFileType.INSTANCE,
        ).intersectWith(NotUnderNodeModulesScope(project))
}

/**
 * Excludes any [VirtualFile] under a `node_modules` directory, delegating to
 * [TypeSpecNodeModules] — the single predicate shared with stub-build-time exclusion (ADR 0011
 * D4). See [TypeSpecSearchScopes] KDoc for why.
 */
private class NotUnderNodeModulesScope(project: Project) : GlobalSearchScope(project) {
    override fun contains(file: VirtualFile): Boolean = !TypeSpecNodeModules.isUnder(file)

    override fun isSearchInModuleContent(aModule: Module): Boolean = true
    // Deliberately false: `tspScope`'s base (`GlobalSearchScope.projectScope`) never searched
    // libraries either. Declaring `true` here made `intersectWith` broaden enumeration into the
    // platform/JDK library word index — observed to make a single project-wide lookup hang (ADR
    // 0008 perf investigation); false matches this scope's actual intent (project content only)
    // and matches the base scope's own semantics.
    override fun isSearchInLibraries(): Boolean = false
}
