package simpli.fyi.plugins.typespec.resolve

import com.intellij.psi.stubs.StubTreeLoader
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.stubs.TypeSpecStubQueries

/**
 * `TypeSpecSearchScopes` (plan 06 M6.5c). The cap and dumb-mode-returns-null cases this class
 * used to pin belonged to the deleted `filesContainingWord`/`TIER_C_FILE_CAP` word-index path
 * (ADR 0011 D1: deleted outright, not kept behind a flag — there is nothing left to cap). What
 * survives, unedited in spirit, is `tspScope`'s `node_modules` exclusion — now the stub-index
 * query scope rather than tier C's word-index prefilter — extended with the stub-index-shaped
 * replacements for the cap/dumb-mode invariants ([TypeSpecStubIndexTest] owns the bulk of that
 * new coverage; the cases here are the ones anchored specifically to `tspScope`/`node_modules`).
 */
class TypeSpecSearchScopesTest : BasePlatformTestCase() {

    // ---- node_modules exclusion (ADR 0008 perf investigation, `dbd7825`) ---------------------

    /**
     * Regression pin for the second real-IDE hang cause: `tspScope` used to include
     * `node_modules` unconditionally (`GlobalSearchScope.projectScope` does not exclude it on a
     * CE-only, non-Node-plugin project), which let a vendored package's own bundled test
     * fixtures eat tier C's file cap for a word that also appears throughout the owner's own
     * source (ADR 0008, `TypeSpecSearchScopes` KDoc). A `.tsp` file under a `node_modules` path
     * segment must never be a stub-index query candidate; an otherwise-identical file outside
     * one must.
     */
    fun testTspScopeContainsExcludesNodeModulesPathSegment() {
        val nodeModulesFile = myFixture.addFileToProject(
            "vendor/node_modules/@typespec/rest/lib.tsp",
            "namespace Lib {}",
        ).virtualFile
        val ownedFile = myFixture.addFileToProject(
            "src/lib.tsp",
            "namespace Lib {}",
        ).virtualFile

        val scope = TypeSpecSearchScopes.tspScope(project)
        assertFalse("node_modules file must be excluded from tspScope", scope.contains(nodeModulesFile))
        assertTrue("a sibling file outside node_modules must be included in tspScope", scope.contains(ownedFile))
    }

    /** Same assertion, driven through the stub index rather than `tspScope.contains` directly. */
    fun testDeclarationsNamedExcludesNodeModulesFileButIncludesSibling() {
        myFixture.addFileToProject(
            "vendor/node_modules/@typespec/protobuf/vendored.tsp",
            """
            namespace VendorScope {
              model VendorWidget {}
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "src/owned.tsp",
            """
            namespace OwnScope {
              model VendorWidget {}
            }
            """.trimIndent(),
        )

        val hits = TypeSpecStubQueries.declarationsNamed(project, "VendorWidget", null)
        val hitFileNames = hits.map { it.containingFile.name }.toSet()
        assertEquals(
            "expected only the file outside node_modules — actual candidates: $hitFileNames",
            setOf("owned.tsp"),
            hitFileNames,
        )
    }

    /**
     * Defence in depth verified directly: a `node_modules` file is not merely absent from a
     * lookup's *results* — it is never eligible for a stub tree at all
     * ([StubTreeLoader.canHaveStub], ADR 0011 D4). `stub == null` is deliberately not used here:
     * `PsiFileImpl.getStub()` can return a non-null, unpersisted ad hoc tree even for a file
     * excluded from indexing.
     */
    fun testNodeModulesFileCanNeverHaveAStub() {
        val nodeModulesFile = myFixture.addFileToProject(
            "vendor/node_modules/@typespec/protobuf/vendored.tsp",
            "model VendorWidget {}",
        ).virtualFile
        val ownedFile = myFixture.addFileToProject(
            "src/owned.tsp",
            "model OwnedWidget {}",
        ).virtualFile

        assertFalse(
            "a node_modules file must never be eligible for a stub tree",
            StubTreeLoader.getInstance().canHaveStub(nodeModulesFile),
        )
        assertTrue(
            "a sibling file outside node_modules must remain eligible for a stub tree",
            StubTreeLoader.getInstance().canHaveStub(ownedFile),
        )
    }

    /**
     * Replaces the old `filesContainingWord`-returns-null-in-dumb-mode pin: the stub-index query
     * (`DumbService.isDumb` guarded inside [TypeSpecStubQueries.declarationsNamed] itself) must
     * degrade to an empty result during indexing, never throw `IndexNotReadyException`.
     */
    fun testDeclarationsNamedReturnsEmptyWithoutThrowingInDumbMode() {
        myFixture.addFileToProject(
            "dumb-widget.tsp",
            """
            namespace Ns {
              model Widget {}
            }
            """.trimIndent(),
        )

        var result: List<TypeSpecNamedElement>? = null
        var threw: Throwable? = null
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            try {
                result = TypeSpecStubQueries.declarationsNamed(project, "Widget", null)
            } catch (t: Throwable) {
                threw = t
            }
        }

        assertNull("no exception (in particular no IndexNotReadyException) may escape dumb mode", threw)
        assertEquals(
            "declarationsNamed must return an empty list while DumbService.isDumb == true",
            emptyList<TypeSpecNamedElement>(),
            result,
        )
    }

    /**
     * End-to-end: resolution into a merged namespace still succeeds when a `node_modules`-vendored
     * decoy file declares the SAME namespace and member name. Confirms excluding `node_modules`
     * did not break resolution within the owner's own corpus — the decoy must be invisible to the
     * resolver, and the real owner-code target must still be found. Unedited from the tier-C-era
     * suite: it drives the actual `PsiReference`, so it is agnostic to which tier answers under
     * the hood.
     */
    fun testResolutionIgnoresNodeModulesDecoyAndStillResolvesOwnerCode() {
        myFixture.addFileToProject(
            "vendor/node_modules/decoy-pkg/decoy.tsp",
            """
            namespace Shared {
              model NodeModulesDecoyTarget {}
            }
            """.trimIndent(),
        )
        val ownFile = myFixture.addFileToProject(
            "src/decoy-target-owner.tsp",
            """
            namespace Shared {
              model NodeModulesDecoyTarget {}
            }
            """.trimIndent(),
        )
        val referencingFile = myFixture.addFileToProject(
            "src/decoy-target-referencer.tsp",
            """
            namespace Shared {
              model User {
                t: NodeModulesDecoyTarget;
              }
            }
            """.trimIndent(),
        )

        myFixture.configureFromExistingVirtualFile(referencingFile.virtualFile)
        val text = myFixture.file.text
        val offset = text.lastIndexOf("NodeModulesDecoyTarget")
        assertTrue(offset >= 0)
        val reference = myFixture.file.findReferenceAt(offset)
        assertNotNull(reference)
        val target = reference!!.resolve()
        assertTrue("expected the owner-code decl to resolve, got $target", target is TypeSpecModelStatement)
        assertEquals("NodeModulesDecoyTarget", (target as TypeSpecNamedElement).name)
        assertEquals(
            "must resolve to the owner-code file, not the node_modules decoy",
            "decoy-target-owner.tsp",
            target.containingFile.name,
        )
        assertEquals(ownFile.virtualFile, target.containingFile.virtualFile)
    }
}
