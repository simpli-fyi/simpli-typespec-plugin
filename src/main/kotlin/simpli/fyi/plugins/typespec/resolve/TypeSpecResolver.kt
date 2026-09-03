package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecPsiUtil
import simpli.fyi.plugins.typespec.psi.TypeSpecQualifiedName
import simpli.fyi.plugins.typespec.stubs.TypeSpecStubQueries

/**
 * The entry point and the only place the tier logic lives
 * ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md),
 * [ADR 0011](../../../../../../../../docs/adr/0011-stub-index-replaces-tier-c.md),
 * [plan 02](../../../../../../../../docs/plans/02-navigation.md),
 * [plan 06](../../../../../../../../docs/plans/06-stub-index.md) M6.5c).
 *
 * Implements all three tiers: A (current file, lexically scoped), B (transitive `import`
 * closure — [TypeSpecImportGraph.transitiveClosure]) and C′ (project-wide **stub index**
 * lookup — [TypeSpecStubQueries.declarationsNamed], zero candidate-file parses), tried in that
 * order, stopping at the first that yields a hit. Tier C′ only ever widens the **leading**
 * segment of a [TypeSpecQualifiedName] (index 0) — the case it exists for is a bare or
 * namespace-relative name resolving into a merged namespace the current file neither contains
 * nor imports (ADR 0004 F4, plan 02 case 15). Non-leading segments (`Foo.<caret>Bar`) get their
 * own, narrower index fallback — see [resolvePath] — because `Foo` having already resolved to a
 * namespace does not by itself guarantee `Bar` lives in a file tiers A/B reach.
 *
 * The name-based core is [resolvePath] (absorbs plan 05 M5.6c): everything below it works on
 * already-stripped name lists and a resolution [PsiElement] context, never on
 * [TypeSpecQualifiedName]/[TypeSpecIdentifier] PSI shape directly. [resolveSegment] is the thin
 * PSI-shaped adapter that the reference contributor ([TypeSpecReference]) still calls.
 */
object TypeSpecResolver {

    /**
     * The implicit ambient `using TypeSpec;` every source file gets for free (plan 06 M5.6g' gap
     * 1) — verified against `name-resolver.js`'s `resolveProgram`, which does, unconditionally
     * for every `program.sourceFiles` entry once the std library is merged into the global
     * namespace:
     * ```
     * const typespecNamespaceBinding = globalNamespaceSym.exports.get("TypeSpec");
     * if (typespecNamespaceBinding) {
     *     for (const file of program.sourceFiles.values()) {
     *         addUsingSymbols(typespecNamespaceBinding.exports, file.locals);
     *     }
     * }
     * ```
     * Two things follow from that upstream shape, both implemented below:
     *  - It is gated on `typespecNamespaceBinding` existing at all — i.e. on the std library
     *    having actually loaded a `namespace TypeSpec` declaration into scope — never applied
     *    unconditionally. Here that gate is "does any file already in [TypeSpecImportGraph]'s
     *    closure (which always tries to seed the std library, M5.6g) declare a global-scope
     *    `TypeSpec` namespace" — same effect, without re-deriving the package check.
     *  - It targets `file.locals`, i.e. exactly the *file-root* scope — the same place an
     *    explicit top-level `using TypeSpec;` would land, not every nested namespace. So it is
     *    only offered as a using target for [NamespacePath]'s empty (global) segment, folded into
     *    the existing per-scope using fallback in [resolveLeadingSegmentIn]. Because that fallback
     *    already only runs *after* [resolveLeadingSegmentIn] finds no direct declaration at that
     *    same scope, a local top-level declaration of the same name still wins — matching
     *    upstream's own precedence (`resolveIdentifier`'s `globalBinding` — real global exports —
     *    is checked, and returned on a hit, strictly before its `usingBinding` fallback, which is
     *    where the injected `TypeSpec` symbols live).
     */
    private val AMBIENT_STD_USING = NamespacePath(listOf("TypeSpec"))

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

    fun multiResolve(identifier: TypeSpecIdentifier): Array<ResolveResult> =
        PsiElementResolveResult.createResults(resolveSegment(identifier).map { it.second })

    /**
     * The name-based public entry point ([resolvePath]) — absorbs plan 05 M5.6c, kept public for
     * a caller that already has a stripped name list and a resolution context rather than a
     * [TypeSpecQualifiedName] segment (plan 05 M5.6d, decorator references, is the first such
     * caller — not implemented here).
     */
    fun multiResolve(names: List<String>, index: Int, context: PsiElement): Array<ResolveResult> =
        PsiElementResolveResult.createResults(resolvePath(names, index, context).map { it.second })

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
        val qualifiedName = identifier.parent as? TypeSpecQualifiedName ?: return emptyList()
        val segments = qualifiedName.identifierList
        val index = segments.indexOf(identifier)
        if (index < 0) return emptyList()
        val names = segments.map { TypeSpecPsiUtil.stripBackticks(it.text) ?: it.text }
        return resolvePath(names, index, identifier)
    }

    /**
     * The name-based resolver core (plan 06 M6.5c, absorbing plan 05 M5.6c). [names] are already
     * backtick-stripped; [index] is the segment being resolved; [context] is any [PsiElement]
     * inside the file the names are being resolved from — used for
     * [TypeSpecImportGraph.transitiveClosure] and [TypeSpecScope.chainFor], both already
     * PSI-position-based rather than identifier-based.
     */
    private fun resolvePath(names: List<String>, index: Int, context: PsiElement): List<Pair<NamespacePath, TypeSpecNamedElement>> {
        ProgressManager.checkCanceled()
        if (index !in names.indices) return emptyList()

        val file = context.containingFile as? TypeSpecFile ?: return emptyList()
        val candidateFiles = TypeSpecImportGraph.transitiveClosure(file)
        val name = names[index]

        return if (index == 0) {
            resolveLeadingSegment(context, name, candidateFiles)
        } else {
            val previousPaths = resolvePath(names, index - 1, context).map { it.first }.distinct()
            previousPaths.flatMap { previousPath ->
                val path = NamespacePath(previousPath.segments + name)
                val direct = candidateFiles.flatMap { f -> TypeSpecFileDeclarations.of(f).find(name, previousPath) }
                // Tier C' fallback (M6.5c): a `Shared.VolumeUnit`-shaped qualified reference
                // whose namespace lives in a file tiers A/B never reach. Zero candidate-file
                // parses — one stub-index lookup plus O(hits) string compares.
                val results = direct.ifEmpty {
                    TypeSpecStubQueries.declarationsNamed(context.project, name, previousPath)
                }
                results.map { path to it }
            }
        }
    }

    private fun resolveLeadingSegment(
        context: PsiElement,
        name: String,
        candidateFiles: Set<TypeSpecFile>,
    ): List<Pair<NamespacePath, TypeSpecNamedElement>> {
        val direct = resolveLeadingSegmentIn(context, name, candidateFiles)
        if (direct.isNotEmpty()) return direct

        // Tiers A/B (candidateFiles) yielded nothing — tier C' (M6.5c, ADR 0011): the stub
        // index, per scope-chain entry and per `using` target visible at that scope. No file
        // set, no cap, no CacheManager, no candidate-file parse.
        return resolveLeadingSegmentViaIndex(context, name, candidateFiles)
    }

    private fun resolveLeadingSegmentIn(
        context: PsiElement,
        name: String,
        candidateFiles: Set<TypeSpecFile>,
    ): List<Pair<NamespacePath, TypeSpecNamedElement>> {
        val chain = TypeSpecScope.chainFor(context)
        for (scope in chain) {
            ProgressManager.checkCanceled()

            val direct = candidateFiles.flatMap { TypeSpecFileDeclarations.of(it).find(name, scope) }
            if (direct.isNotEmpty()) {
                val path = NamespacePath(scope.segments + name)
                return direct.distinct().map { path to it }
            }

            val usingTargets = (
                candidateFiles.flatMap { TypeSpecScope.usingsVisibleIn(scope, it) } +
                    ambientStdUsing(scope, candidateFiles)
                ).distinct()
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

    /**
     * Tier C′ (plan 06 M6.5c, ADR 0011): mirrors [resolveLeadingSegmentIn]'s shape exactly —
     * same scope-chain order (longest prefix first), same "direct declaration at this scope,
     * else its `using` targets" precedence — but every "does this name exist here" question is
     * answered by [TypeSpecStubQueries.declarationsNamed] instead of walking [candidateFiles].
     * `using` *targets themselves* are still resolved through [candidateFiles]' PSI
     * ([TypeSpecScope.usingsVisibleIn]) because a `using` statement always lives in a file
     * already in this file's tier A/B closure (the file being resolved from, at minimum) — only
     * the declaration a `using` points *at* may live outside that closure, which is exactly what
     * this tier widens.
     */
    private fun resolveLeadingSegmentViaIndex(
        context: PsiElement,
        name: String,
        candidateFiles: Set<TypeSpecFile>,
    ): List<Pair<NamespacePath, TypeSpecNamedElement>> {
        val project = context.project
        val chain = TypeSpecScope.chainFor(context)
        for (scope in chain) {
            ProgressManager.checkCanceled()

            val direct = TypeSpecStubQueries.declarationsNamed(project, name, scope)
            if (direct.isNotEmpty()) {
                val path = NamespacePath(scope.segments + name)
                return direct.distinct().map { path to it }
            }

            val usingTargets = (
                candidateFiles.flatMap { TypeSpecScope.usingsVisibleIn(scope, it) } +
                    ambientStdUsing(scope, candidateFiles)
                ).distinct()
            if (usingTargets.isNotEmpty()) {
                val viaUsing = usingTargets.flatMap { target ->
                    val path = NamespacePath(target.segments + name)
                    TypeSpecStubQueries.declarationsNamed(project, name, target).map { path to it }
                }
                if (viaUsing.isNotEmpty()) return viaUsing.distinctBy { it.second }
            }
        }
        return emptyList()
    }

    /**
     * [AMBIENT_STD_USING], but only at the file-root scope (empty [NamespacePath]) and only when
     * a global-scope `namespace TypeSpec` declaration is actually reachable in [candidateFiles] —
     * i.e. the std library resolved into the closure at all ([TypeSpecImportGraph] M5.6g). Absent
     * that (no `@typespec/compiler` installed, or [TypeSpecImportGraph] couldn't reach it), this
     * degrades to no ambient using, silently — same contract as the closure seed itself.
     */
    private fun ambientStdUsing(scope: NamespacePath, candidateFiles: Set<TypeSpecFile>): List<NamespacePath> {
        if (scope.segments.isNotEmpty()) return emptyList()
        val stdLibraryLoaded = candidateFiles.any { f ->
            TypeSpecFileDeclarations.of(f).find("TypeSpec", NamespacePath(emptyList()))
                .any { it is TypeSpecNamespaceStatement }
        }
        return if (stdLibraryLoaded) listOf(AMBIENT_STD_USING) else emptyList()
    }
}
