package simpli.fyi.plugins.typespec.stubs

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.resolve.NamespacePath

/**
 * `TypeSpecDeclarationNameIndex` / `TypeSpecStubQueries` (plan 06 M6.5a/b, ADR 0011). Covers
 * both the index's query surface (name lookup, namespace-path filtering, scale without a cap)
 * and the stub payload the index is built from (backtick-stripped name, namespace path computed
 * from the parent **stub** chain, a dotted `namespace` statement's own segments) — the M6.5a
 * acceptance criteria plan 06 describes were never pinned by a committed test before this suite.
 */
class TypeSpecStubIndexTest : BasePlatformTestCase() {

    // ---- 1: lookup by name, then filtered by namespace path --------------------------------

    fun testLookupByNameAcrossNamespacesThenFilteredByPath() {
        myFixture.addFileToProject(
            "shared/response.tsp",
            """
            namespace Shared {
              model Response {}
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "other/response.tsp",
            """
            namespace Other {
              model Response {}
            }
            """.trimIndent(),
        )

        val all = TypeSpecStubQueries.declarationsNamed(project, "Response", null)
        assertEquals("expected one Response per namespace", 2, all.size)

        val filtered = TypeSpecStubQueries.declarationsNamed(project, "Response", NamespacePath(listOf("Shared")))
        assertEquals(1, filtered.size)
        assertTrue(filtered[0] is TypeSpecModelStatement)
        assertEquals("response.tsp", filtered[0].containingFile.name)
        assertEquals("shared", filtered[0].containingFile.virtualFile.parent.name)
    }

    // ---- 2: dotted namespace findable by each segment ---------------------------------------

    fun testDottedNamespaceFindableByEachSegment() {
        myFixture.addFileToProject("dotted.tsp", "namespace A.B.C;\nmodel M {}\n")

        for (segment in listOf("A", "B", "C")) {
            val hits = TypeSpecStubQueries.declarationsNamed(project, segment, null)
            assertTrue(
                "segment '$segment' must be findable — hits: $hits",
                hits.any { it is TypeSpecNamespaceStatement },
            )
        }
    }

    // ---- 3: scale — no cap, noise does not corrupt the answer -------------------------------

    /**
     * 120 files each mention the word `Shared` textually (in a comment, never a declaration) plus
     * one file that actually declares `namespace Shared;`. This is the plan 06 M6.5b scale
     * acceptance case, replacing the deleted `TIER_C_FILE_CAP` test: the lookup must return
     * exactly the one real declaration regardless of how many files merely contain the word.
     */
    fun testScaleLookupIgnoresTextualNoise() {
        repeat(120) { i ->
            myFixture.addFileToProject(
                "noise/noise-$i.tsp",
                "// mentions Shared as plain text, never a declaration\nmodel Noise$i {}\n",
            )
        }
        myFixture.addFileToProject("real/shared.tsp", "namespace Shared;\nmodel Anchor {}\n")

        val hits = TypeSpecStubQueries.declarationsNamed(project, "Shared", null)
        assertEquals(
            "expected exactly the one real declaration among 120 noise files — actual: $hits",
            1,
            hits.size,
        )
        assertTrue(hits[0] is TypeSpecNamespaceStatement)
        assertEquals("shared.tsp", hits[0].containingFile.name)
    }

    // ---- 4: stub payload — backtick-stripped name --------------------------------------------

    fun testStubNameIsBacktickStripped() {
        myFixture.addFileToProject("backtick.tsp", "model `model` {}\n")

        val hits = TypeSpecStubQueries.declarationsNamed(project, "model", null)
        assertEquals(1, hits.size)
        assertEquals("model", hits[0].name)

        val stub = (hits[0] as StubBasedPsiElementBase<*>).greenStub as? TypeSpecDeclStub
        assertNotNull("expected a stub-based hit for an unopened file", stub)
        assertEquals("model", stub!!.name)
    }

    // ---- 5: stub payload — namespace path computed from the parent stub chain --------------

    fun testStubNamespacePathComputedFromParentStubChain() {
        myFixture.addFileToProject(
            "nested.tsp",
            """
            namespace A.B.C;
            model M {}
            """.trimIndent(),
        )

        val hits = TypeSpecStubQueries.declarationsNamed(project, "M", null)
        assertEquals(1, hits.size)
        val stub = (hits[0] as StubBasedPsiElementBase<*>).greenStub as? TypeSpecDeclStub
        assertNotNull("expected a stub-based hit for an unopened file", stub)
        assertEquals("A.B.C", stub!!.namespacePath)
        assertEquals(emptyList<String>(), stub.ownSegments)
    }

    /** The irregular case: a namespace statement's own stub carries its dotted segments. */
    fun testNamespaceStubOwnSegments() {
        myFixture.addFileToProject("own-segments.tsp", "namespace A.B.C;\nmodel M {}\n")

        val hits = TypeSpecStubQueries.declarationsNamed(project, "C", null)
        val namespaceHit = hits.filterIsInstance<TypeSpecNamespaceStatement>().single()
        val stub = (namespaceHit as StubBasedPsiElementBase<*>).greenStub as? TypeSpecDeclStub
        assertNotNull(stub)
        assertEquals(listOf("A", "B", "C"), stub!!.ownSegments)
        assertEquals("", stub.namespacePath)
    }

    // ---- 6: matchesEnclosingPath — the stub path AND the PSI-fallback path -------------------

    /**
     * Unopened target file: the common project-wide-search case. Per
     * [TypeSpecStubQueries.matchesEnclosingPath]'s own KDoc, `getGreenStub()` is expected to
     * answer here without forcing an AST load — verified indirectly by the assertion succeeding
     * without ever calling `configureByFile`/`configureFromExistingVirtualFile` on the target.
     */
    fun testNamespaceFilteredLookupCorrectWhenTargetFileNeverOpened() {
        myFixture.addFileToProject(
            "unopened/shared.tsp",
            """
            namespace Shared {
              model VolumeUnit {}
            }
            """.trimIndent(),
        )

        val hits = TypeSpecStubQueries.declarationsNamed(project, "VolumeUnit", NamespacePath(listOf("Shared")))
        assertEquals(1, hits.size)
        assertEquals("shared.tsp", hits[0].containingFile.name)
    }

    /**
     * Same fixture, but the target file's AST is forced resident first (`configureByFile` opens
     * it as the active editor document) before the lookup runs — the scenario the M6.5c dev note
     * describes where `getGreenStub()` can return `null` and [TypeSpecStubQueries] falls back to
     * a PSI walk ([simpli.fyi.plugins.typespec.resolve.TypeSpecScope.pathOf]). The answer must be
     * identical either way; this test exists specifically so "stub path only" is not silently the
     * only path exercised across the suite.
     */
    fun testNamespaceFilteredLookupCorrectWhenTargetFileAlreadyOpened() {
        val target = myFixture.addFileToProject(
            "opened/shared.tsp",
            """
            namespace Shared {
              model VolumeUnit {}
            }
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(target.virtualFile)
        // Force the AST, not just open the editor.
        assertNotNull(myFixture.file.node)

        val hits = TypeSpecStubQueries.declarationsNamed(project, "VolumeUnit", NamespacePath(listOf("Shared")))
        assertEquals(1, hits.size)
        assertEquals("shared.tsp", hits[0].containingFile.name)
    }
}
