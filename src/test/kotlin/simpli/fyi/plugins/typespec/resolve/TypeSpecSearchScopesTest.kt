package simpli.fyi.plugins.typespec.resolve

import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
}
