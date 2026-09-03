package simpli.fyi.plugins.typespec.resolve

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement

/**
 * Regression pin for the real-IDE hang report (2026-09-03, `dbd7825`): unbounded recursive
 * re-resolution in [TypeSpecScope.usingsVisibleIn] via `resolveUsingTarget` -> `multiResolve` ->
 * `TypeSpecResolver.resolveLeadingSegmentIn` -> `usingsVisibleIn`, with no memoization across the
 * different call paths that all reach the same `using` PSI element. Fixed by wrapping
 * `resolveUsingTarget`'s inner computation in `CachedValuesManager.getCachedValue(using) { ... }`,
 * keyed on the `using` statement's containing file - the same cache grain
 * [TypeSpecFileDeclarations] already uses (ADR 0004 D2).
 *
 * ## Why identity, not a wall-clock timeout, is the primary pin
 *
 * The bug was a HANG, not a wrong answer: [TypeSpecScope.usingsVisibleIn] returns the correct
 * [NamespacePath] with or without memoization, so a plain "does it resolve correctly" test
 * passes on both the broken and the fixed tree and would never catch a regression.
 *
 * [NamespacePath] is a `JvmInline value class` and gets Kotlin's structural `equals`/`hashCode`
 * for free, so even `assertEquals` on two independently-computed [NamespacePath] values proves
 * nothing about whether the *same* computation ran once or twice. What DOES distinguish "cached"
 * from "recomputed" is object identity of the cached backing list returned across two calls with
 * an unchanged dependency. Decompiling `TypeSpecScope.class` confirms `resolveUsingTarget`'s
 * private return type is erased to `List<String>` (the value class's own backing field) - Kotlin
 * unboxes `NamespacePath` at that boundary and reboxes a fresh wrapper at every call site, so the
 * wrapper's own identity is never stable even when the underlying list is. Comparing
 * `NamespacePath.segments` (the raw backing list) instead of the wrapper is therefore the correct
 * probe: [com.intellij.psi.util.CachedValuesManager] returns the exact same `List<String>`
 * instance on every call until the dependency (the `using`'s containing file) is modified.
 * `testResolveUsingTargetIsMemoizedPerUsingStatement` below asserts `assertSame` on that list -
 * deterministic, no flake, and it fails immediately if the `CachedValuesManager` wrapping around
 * `resolveUsingTarget`'s body is ever removed (each call would then mint a fresh `List` from a
 * fresh reference resolve, breaking identity even though the *value* is unchanged). This is the
 * same technique [TypeSpecCachingTest] already uses for [TypeSpecFileDeclarations] ("assert the
 * cached value is reused"), adapted for a value-class return type.
 *
 * `testManyIndependentResolutionsThroughTheSameUsingShareOneComputation` adds a second,
 * complementary pin: it drives the *public*, Cmd-click-shaped API (`PsiReference.resolve()`)
 * across 40 independent files that all resolve `Address` only by falling back to the same single
 * `using Common;` statement in a shared hub file - the actual fan-out shape from the hang report
 * (many call paths converging on one `using` PSI element) - and asserts BOTH that every one of
 * them resolves correctly AND that the whole batch completes within a generous bound (well under
 * what 40 un-memoized full re-resolutions plus their own internal `using` fallback would cost).
 * The timing bound alone would be flaky in isolation (CI variance); paired with the identity
 * assertion above and the correctness assertion in the same test, it is a secondary confirmation
 * of the *shape* of the fix, not the sole evidence for it.
 *
 * ## This is a DIFFERENT protection from the pre-existing `ThreadLocal` guard
 *
 * [TypeSpecResolveTest.testUsingSelfTargetDoesNotStackOverflow] pins `resolveUsingTarget`'s
 * `ThreadLocal` re-entrancy guard (`resolvingUsings`), which stops a `using` statement from being
 * resolved while it is ALREADY being resolved on the same thread - i.e. it breaks a *cycle*
 * (`using Common;` whose own resolution walks back through the scope that declares it). The
 * memoization added here stops the SAME (already-terminating, non-cyclic) resolution from being
 * repeated from scratch on every one of many *different, non-overlapping* call paths that all
 * eventually reach that `using` element. Both were manually reverted in isolation against this
 * suite while writing these tests and confirmed independently necessary:
 * - Removing only the `ThreadLocal` guard (keeping the `CachedValuesManager` wrapping)
 *   reintroduces a `StackOverflowError` on `testUsingSelfTargetDoesNotStackOverflow` - memoization
 *   does not cache an IN-PROGRESS computation, only a completed one, so it cannot break a cycle by
 *   itself.
 * - Removing only the memoization (keeping the `ThreadLocal` guard) leaves
 *   `testUsingSelfTargetDoesNotStackOverflow` passing but breaks
 *   `testResolveUsingTargetIsMemoizedPerUsingStatement`'s identity assertion - the guard only
 *   prevents cycles, it does not bound the fan-out across independent, acyclic call paths.
 */
class TypeSpecUsingMemoizationTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // ---- primary pin: identity of the cached backing list ------------------------------------

    fun testResolveUsingTargetIsMemoizedPerUsingStatement() {
        val file = myFixture.configureByFile("resolve/memo-using-target.tsp") as TypeSpecFile
        val scope = NamespacePath(listOf("MyOrg"))

        val first = TypeSpecScope.usingsVisibleIn(scope, file)
        val second = TypeSpecScope.usingsVisibleIn(scope, file)
        val third = TypeSpecScope.usingsVisibleIn(scope, file)

        assertEquals(1, first.size)
        assertEquals(NamespacePath(listOf("Common")), first[0])

        assertSame(
            "resolveUsingTarget must return the SAME cached backing list across repeated calls " +
                "with an unchanged dependency - if this fails, the CachedValuesManager wrapping " +
                "around resolveUsingTarget's body has been removed and every usingsVisibleIn " +
                "call is recomputing the using's target from scratch again (the hang this test " +
                "guards against)",
            first[0].segments,
            second[0].segments,
        )
        assertSame("must be stable across more than two calls too", second[0].segments, third[0].segments)
    }

    // ---- secondary pin: many independent call paths converging on one `using` -----------------

    /**
     * 40 files, each importing a shared hub file whose only route to `Address` is the hub's own
     * `using Common;`. Each file resolves `Address` through two nested namespace scopes before
     * falling back to the hub's global-scope `using` - the same "several scopes and files" shape
     * the job description asks for. Every one of the 40 resolutions is an independent
     * `PsiReference.resolve()` call (the actual Cmd-click entry point), not a direct call into
     * [TypeSpecScope] - this exercises the fix end to end, not just the cache primitive.
     */
    fun testManyIndependentResolutionsThroughTheSameUsingShareOneComputation() {
        myFixture.configureByFile("resolve/memo-fanout-hub.tsp")
        myFixture.configureByFile("resolve/memo-fanout-common.tsp")

        val callerFiles = (0 until 40).map { i ->
            myFixture.configureByFile("resolve/memo-fanout-caller-$i.tsp")
        }

        val start = System.nanoTime()
        for (callerFile in callerFiles) {
            val text = callerFile.text
            val offset = text.lastIndexOf("Address")
            assertTrue("marker 'Address' not found in ${callerFile.name}", offset >= 0)
            val reference = callerFile.findReferenceAt(offset)
            assertNotNull("no reference at 'Address' in ${callerFile.name}", reference)
            val target = reference!!.resolve()
            assertTrue(
                "expected ${callerFile.name}'s Address reference to resolve to a model, got $target",
                target is TypeSpecModelStatement,
            )
            assertEquals("Address", (target as TypeSpecNamedElement).name)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        // Generous on purpose (paired with the identity assertion above, not standalone evidence
        // - see class KDoc): 40 resolutions each walking 2 namespace scopes and falling back to
        // one shared `using` should not take anywhere near this long once that `using`'s target
        // is computed once and reused; the un-memoized code path recomputed it from scratch on
        // every one of the 40 calls.
        assertTrue(
            "40 using-mediated resolutions took ${elapsedMs}ms - suspiciously slow for a " +
                "memoized single `using` target; this is the shape of the reported hang",
            elapsedMs < 30_000,
        )
    }

    // ---- "also verify": cache invalidation on editing the `using`'s own file ------------------

    /**
     * ADR 0004 D2's accepted tradeoff, restated for this new cache grain (the "Perf fix" note on
     * [TypeSpecScope.resolveUsingTarget]): the memoization is keyed on the `using` statement's
     * OWN containing file. Editing that file must invalidate its memoized target and produce a
     * fresh (structurally different) result. Cross-file staleness is the accepted tradeoff,
     * pinned here so it stays visible rather than implicit.
     */
    fun testEditingTheUsingsOwnFileInvalidatesItsMemoizedTarget() {
        myFixture.configureByFile("resolve/memo-invalidate-common.tsp")
        val virtualFileUser = myFixture.copyFileToProject(
            "resolve/memo-invalidate-user.tsp",
            "memo-invalidate-user-live.tsp",
        )
        myFixture.configureFromExistingVirtualFile(virtualFileUser)
        val userFile = myFixture.file as TypeSpecFile

        val scope = NamespacePath(listOf("MyOrg"))
        val before = TypeSpecScope.usingsVisibleIn(scope, userFile)
        assertEquals(1, before.size)
        assertEquals(NamespacePath(listOf("CommonA")), before[0])

        // Flip the using's own target from CommonA to CommonB.
        val document = myFixture.editor.document
        val text = document.text
        val usingOffset = text.indexOf("using CommonA;")
        assertTrue(usingOffset >= 0)
        myFixture.editor.caretModel.moveToOffset(usingOffset)
        myFixture.editor.selectionModel.setSelection(usingOffset, usingOffset + "using CommonA;".length)
        myFixture.type("using CommonB;")
        myFixture.doHighlighting() // force PSI/document reconciliation

        val after = TypeSpecScope.usingsVisibleIn(scope, myFixture.file as TypeSpecFile)
        assertEquals(1, after.size)
        assertEquals(
            "editing the using statement's own file must invalidate its memoized target",
            NamespacePath(listOf("CommonB")),
            after[0],
        )
    }
}
