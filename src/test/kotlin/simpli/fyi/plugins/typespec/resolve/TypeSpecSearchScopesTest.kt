package simpli.fyi.plugins.typespec.resolve

import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement

/**
 * Tier C's two behavioural cliffs ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md)
 * D2, [plan 02](../../../../../../../../docs/plans/02-navigation.md) §
 * `TypeSpecSearchScopesTest`). Both are deliberate EDT-safety tradeoffs: [filesContainingWord]
 * returns `null` — "stop, unresolved" — rather than parsing a truncated subset, either because
 * the word-index hit count exceeds [TypeSpecSearchScopes.TIER_C_FILE_CAP] or because
 * [com.intellij.openapi.project.DumbService] reports the project is indexing. Neither cliff is
 * currently pinned by a test; this class is that pin.
 */
class TypeSpecSearchScopesTest : BasePlatformTestCase() {

    // ---- the 50-file cap ---------------------------------------------------------------------

    /**
     * More than [TypeSpecSearchScopes.TIER_C_FILE_CAP] `.tsp` files all contain the word
     * `Widget`. Asserts (a) [TypeSpecSearchScopes.filesContainingWord] itself returns `null`
     * once the cap is exceeded, and (b) the full resolver — the thing a Cmd-click actually
     * calls — degrades to unresolved for a cross-file, no-import reference to `Widget`, rather
     * than resolving to an arbitrary member of a truncated subset. This is the "never parse a
     * truncated subset" claim in plan 02's `TypeSpecSearchScopesTest` section, verified against
     * the real resolver rather than trusted from the KDoc.
     */
    fun testFileCapExceededReturnsNullAndResolverDegradesToUnresolved() {
        val fileCount = TypeSpecSearchScopes.TIER_C_FILE_CAP + 5
        repeat(fileCount) { i ->
            myFixture.addFileToProject(
                "cap-widget-$i.tsp",
                """
                namespace Ns$i {
                  model Widget {}
                }
                """.trimIndent(),
            )
        }

        val start = System.nanoTime()
        val hits = TypeSpecSearchScopes.filesContainingWord(project, "Widget")
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertNull(
            "filesContainingWord must return null once the word-index hit count exceeds " +
                "TIER_C_FILE_CAP (${TypeSpecSearchScopes.TIER_C_FILE_CAP}), not a truncated list",
            hits,
        )
        // A pure index lookup that bails on the cap before parsing anything should be fast,
        // not proportional to fileCount full parses.
        assertTrue("filesContainingWord took ${elapsedMs}ms, suspiciously slow for a capped index lookup", elapsedMs < 20_000)

        // Now exercise the real resolver: a reference to `Widget` with no local declaration,
        // no import, in a namespace that does not merge with any of the Ns$i namespaces above.
        val referencingFile = myFixture.addFileToProject(
            "cap-referencer.tsp",
            """
            namespace CapReferencer {
              model User {
                w: <caret>Widget;
              }
            }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(referencingFile.virtualFile)
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertNull(
            "resolver must degrade to unresolved when tier C's cap fires, never resolve into " +
                "an arbitrary member of a truncated candidate set",
            reference!!.resolve(),
        )
    }

    // ---- dumb mode -----------------------------------------------------------------------

    /**
     * During indexing, `CacheManager` must not be consulted at all — calling it in dumb mode
     * throws `IndexNotReadyException`. Asserts [TypeSpecSearchScopes.filesContainingWord]
     * returns `null` inside `runInDumbMode` and, crucially, that no exception escapes.
     */
    fun testDumbModeReturnsNullWithoutThrowing() {
        myFixture.addFileToProject(
            "dumb-widget.tsp",
            """
            namespace Ns {
              model Widget {}
            }
            """.trimIndent(),
        )

        var result: List<simpli.fyi.plugins.typespec.psi.TypeSpecFile>? = listOf() // sentinel, overwritten below
        var threw: Throwable? = null
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            try {
                result = TypeSpecSearchScopes.filesContainingWord(project, "Widget")
            } catch (t: Throwable) {
                threw = t
            }
        }

        assertNull("no exception (in particular no IndexNotReadyException) may escape dumb mode", threw)
        assertNull("filesContainingWord must return null while DumbService.isDumb == true", result)
    }

    // ---- node_modules exclusion (ADR 0008 perf investigation, `dbd7825`) ---------------------

    /**
     * Regression pin for the second real-IDE hang cause: `tspScope` used to include
     * `node_modules` unconditionally (`GlobalSearchScope.projectScope` does not exclude it on a
     * CE-only, non-Node-plugin project), which let a vendored package's own bundled test
     * fixtures eat tier C's file cap for a word that also appears throughout the owner's own
     * source (ADR 0008, `TypeSpecSearchScopes` KDoc). A `.tsp` file under a `node_modules` path
     * segment must never be a tier C candidate; an otherwise-identical file outside one must.
     */
    fun testFilesContainingWordExcludesNodeModulesFileButIncludesSibling() {
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

        val hits = TypeSpecSearchScopes.filesContainingWord(project, "VendorWidget")
        assertNotNull(hits)
        val hitNames = hits!!.map { it.name }.toSet()
        assertEquals(
            "expected only the file outside node_modules — actual candidates: $hitNames",
            setOf("owned.tsp"),
            hitNames,
        )
    }

    /** Same assertion, but directly against [TypeSpecSearchScopes.tspScope]'s `contains`. */
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

    /**
     * End-to-end: a tier C resolution into a merged namespace still succeeds when a
     * `node_modules`-vendored decoy file declares the SAME namespace and member name. Confirms
     * excluding `node_modules` did not break resolution within the owner's own corpus — the
     * decoy must be invisible to the resolver, and the real owner-code target must still be
     * found.
     */
    fun testTierCResolutionIgnoresNodeModulesDecoyAndStillResolvesOwnerCode() {
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
