package simpli.fyi.plugins.typespec.stubs

import com.intellij.openapi.vfs.VirtualFile

/**
 * The single `node_modules` predicate (ADR 0011 D4, plan 06 M6.5a) — a file is "under
 * `node_modules`" when any ancestor directory is literally named `node_modules`, matching
 * npm/pnpm/yarn convention. Used at stub **build** time by
 * [TypeSpecFileElementType.shouldBuildStubFor] so no stub is ever built for a `node_modules`
 * file in the first place (D4: excluded at build time, not only query time).
 *
 * From M6.5b/c onward the stub **query** scope (`TypeSpecSearchScopes`) is repointed at this
 * exact predicate too, so build-time exclusion and query-time filtering can never drift apart —
 * that is the whole point of factoring it out here instead of leaving two copies (one for the
 * index, one already living in `TypeSpecSearchScopes.NotUnderNodeModulesScope`). This is load
 * bearing: an unfiltered project-wide index recreates ADR 0008's file-count pathology with a
 * bigger blast radius, and it is what fixed a real EDT hang (ADR 0008 perf investigation,
 * ADR 0011 §Context case 2).
 *
 * Changing this predicate is an [TypeSpecStubVersion] D6 item-4 bump — it changes which files
 * get a stub tree at all.
 */
object TypeSpecNodeModules {
    fun isUnder(file: VirtualFile): Boolean =
        generateSequence(file) { it.parent }.any { it.name == "node_modules" }
}
