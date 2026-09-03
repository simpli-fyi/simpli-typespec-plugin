package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.psi.TypeSpecFile

/**
 * Tier C ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md)
 * D2, [plan 02](../../../../../../../../docs/plans/02-navigation.md)). Project-wide widening
 * for the case tiers A/B cannot reach: a merged namespace declared in a file the current file
 * neither contains nor transitively `import`s (ADR 0004 F4) — the norm in real TypeSpec
 * projects, not an edge case.
 *
 * The prefilter is the platform's own word index
 * (`CacheManager.getVirtualFilesWithWord`), not a full-project scan: only `.tsp` files whose
 * text literally contains the candidate identifier are ever parsed. `UsageSearchContext.ANY` is
 * mandatory, not a shortcut — see the KDoc below.
 *
 * [ADR 0008](../../../../../../../../docs/adr/0008-tier-c-file-cap.md) (perf investigation):
 * `node_modules` is excluded from [tspScope]. Measured against a real owner project
 * (`ph-cdm`, 83 `.tsp` files, 60 of them under `node_modules`), `GlobalSearchScope.projectScope`
 * includes `node_modules` unconditionally — nothing in a CE-only, `JAVA_MODULE`-typed `.iml`
 * marks it excluded, and no Node/JS plugin is present to do so automatically. A single npm
 * package's *own bundled test fixtures* (`@typespec/protobuf/test/scenarios/**/input/main.tsp`,
 * 36 files) was enough on its own to push the word-index hit count for the identifier
 * `Protobuf` — which appears in `using TypeSpec.Protobuf;`, present in nearly every file of
 * that project's actual source — past [TIER_C_FILE_CAP]. That is the concrete mechanism behind
 * both halves of ADR 0008's tension: it inflates the candidate pool with files that were never
 * legitimate navigation targets for the *project's own* merged-namespace case (ADR 0004 F4 is
 * about the owner's own multi-file namespaces, not a dependency's vendored copy), which both
 * wastes tier C's parse budget (performance) and starves the cap of headroom for the names the
 * owner's own project actually shares (correctness). Excluding `node_modules` does not regress
 * a working feature: resolving *into* a library's own declarations was never a deliberate
 * capability of this milestone (ADR 0004 open question 2, still unimplemented) — it was an
 * accidental side effect of `node_modules` being unintentionally in scope, and an expensive,
 * cap-eating one at that.
 */
object TypeSpecSearchScopes {

    /** ADR 0004 D2 — degrade to "unresolved" beyond this many candidate files, never freeze. */
    const val TIER_C_FILE_CAP = 50

    /**
     * The project-wide `.tsp`-only search scope tier C widens into, excluding `node_modules`
     * (see class KDoc — ADR 0008 perf investigation). A file is excluded when any path segment
     * is literally `node_modules`, matching npm/pnpm/yarn convention; this needs no `NodeJS`
     * plugin dependency and no project-model exclusion the owner's `.iml` does not have.
     */
    fun tspScope(project: Project): GlobalSearchScope =
        GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.projectScope(project),
            TypeSpecFileType.INSTANCE,
        ).intersectWith(NotUnderNodeModulesScope(project))

    /**
     * The `.tsp` files whose text contains the word [name], word-index backed, capped at
     * [TIER_C_FILE_CAP]. Returns `null` when the index is unavailable (dumb mode) or the
     * result exceeds the cap — both mean "the resolver stops here, unresolved", never "parse
     * a truncated subset and hope" (a silently partial answer is worse than a clean miss).
     *
     * `UsageSearchContext.ANY` is mandatory: until [simpli.fyi.plugins.typespec.findusages.TypeSpecFindUsagesProvider]
     * is registered, `.tsp` words are indexed by the platform's default `SimpleWordsScanner`
     * with a null occurrence kind, which the indexer records as `ANY`. Once the provider *is*
     * registered (this same milestone) occurrences become properly categorised
     * (`IN_CODE`/`IN_COMMENTS`/`IN_STRINGS`), but `ANY` still matches all of them — it is
     * correct before and after, whereas `IN_CODE` alone would be wrong before.
     */
    fun filesContainingWord(project: Project, name: String): List<TypeSpecFile>? {
        if (DumbService.isDumb(project)) return null

        // getVirtualFilesWithWord is an index lookup, not a parse — cheap even before the
        // cap check below. Parsing (via PsiManager.findFile) only happens for files that
        // survive the cap.
        val virtualFiles = CacheManager.getInstance(project)
            .getVirtualFilesWithWord(name, UsageSearchContext.ANY, tspScope(project), true)
        if (virtualFiles.size > TIER_C_FILE_CAP) return null

        val psiManager = PsiManager.getInstance(project)
        return virtualFiles.mapNotNull { psiManager.findFile(it) as? TypeSpecFile }
    }
}

/**
 * Excludes any [VirtualFile] under a `node_modules` directory, by path segment. See
 * [TypeSpecSearchScopes] KDoc (ADR 0008 perf investigation) for why.
 */
private class NotUnderNodeModulesScope(project: Project) : GlobalSearchScope(project) {
    override fun contains(file: VirtualFile): Boolean =
        generateSequence(file) { it.parent }.none { it.name == "node_modules" }

    override fun isSearchInModuleContent(aModule: Module): Boolean = true
    // Deliberately false: `tspScope`'s base (`GlobalSearchScope.projectScope`) never searched
    // libraries either. Declaring `true` here made `intersectWith` broaden enumeration into the
    // platform/JDK library word index — observed to make a single `filesContainingWord` call
    // hang (ADR 0008 perf investigation); false matches this scope's actual intent (project
    // content only) and matches the base scope's own semantics.
    override fun isSearchInLibraries(): Boolean = false
}
