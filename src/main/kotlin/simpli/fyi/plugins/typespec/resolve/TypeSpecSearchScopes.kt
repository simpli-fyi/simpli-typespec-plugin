package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
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
 */
object TypeSpecSearchScopes {

    /** ADR 0004 D2 — degrade to "unresolved" beyond this many candidate files, never freeze. */
    const val TIER_C_FILE_CAP = 50

    /** The project-wide `.tsp`-only search scope tier C widens into. */
    fun tspScope(project: Project): GlobalSearchScope =
        GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.projectScope(project),
            TypeSpecFileType.INSTANCE,
        )

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
