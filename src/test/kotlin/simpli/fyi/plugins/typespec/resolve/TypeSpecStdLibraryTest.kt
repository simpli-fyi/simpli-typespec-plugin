package simpli.fyi.plugins.typespec.resolve

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement

/**
 * M5.6g / M5.6g' — the implicit `@typespec/compiler` std-library edge
 * ([ADR 0010](../../../../../../../../docs/adr/0010-library-import-resolution.md) open
 * question 1, [plan 06](../../../../../../../../docs/plans/06-stub-index.md) M5.6g/M5.6g')
 * plus the ambient `using TypeSpec;` it enables.
 *
 * Fixtures are built programmatically via `myFixture.addFileToProject`, same technique as
 * `TypeSpecImportResolverTest` — every fake `node_modules/@typespec/compiler` layout lives next
 * to the assertion it backs.
 */
class TypeSpecStdLibraryTest : BasePlatformTestCase() {

    private fun tsp(path: String, text: String) = myFixture.addFileToProject(path, text)

    /** Standard fake `@typespec/compiler` install: `lib/std/main.tsp` is the real `tspMain`. */
    private fun installStdLibrary(
        mainBody: String = "",
        intrinsicsBody: String = STD_INTRINSICS,
    ): TypeSpecFile {
        tsp(
            "node_modules/@typespec/compiler/package.json",
            """{"tspMain": "lib/std/main.tsp"}""",
        )
        val stdMain = tsp(
            "node_modules/@typespec/compiler/lib/std/main.tsp",
            "namespace TypeSpec;\n$mainBody",
        ) as TypeSpecFile
        tsp("node_modules/@typespec/compiler/lib/intrinsics.tsp", intrinsicsBody)
        return stdMain
    }

    private fun resolveAt(file: TypeSpecFile, needle: String): List<Any?> {
        val offset = file.text.indexOf(needle)
        assertTrue("marker '$needle' not found in ${file.name}", offset >= 0)
        val ref = file.findReferenceAt(offset) as? PsiPolyVariantReference
        assertNotNull("no reference at offset of '$needle' in ${file.name}", ref)
        return ref!!.multiResolve(false).map { it.element }
    }

    companion object {
        private const val STD_INTRINSICS = "namespace TypeSpec;\nscalar string;\nscalar int32;\n"
    }

    // ---- 1: string / int32 resolve from lib/intrinsics.tsp with zero imports ---------------

    fun testStringAndInt32ResolveFromIntrinsicsWithNoImports() {
        installStdLibrary()
        val app = tsp("app-intrinsics.tsp", "model M { a: string; b: int32; }") as TypeSpecFile

        val stringTargets = resolveAt(app, "string")
        assertEquals(1, stringTargets.size)
        assertEquals("intrinsics.tsp", (stringTargets[0] as TypeSpecNamedElement).containingFile.name)
        assertEquals("string", (stringTargets[0] as TypeSpecNamedElement).name)

        val int32Targets = resolveAt(app, "int32")
        assertEquals(1, int32Targets.size)
        assertEquals("intrinsics.tsp", (int32Targets[0] as TypeSpecNamedElement).containingFile.name)
        assertEquals("int32", (int32Targets[0] as TypeSpecNamedElement).name)
    }

    // ---- 2: an ordinary std-lib declaration resolves unqualified via the ambient using -----

    fun testStdLibDeclarationResolvesUnqualifiedViaAmbientUsing() {
        installStdLibrary(mainBody = "model StdWidget {}\n")
        val app = tsp("app-ambient.tsp", "model M { a: StdWidget; }") as TypeSpecFile

        val targets = resolveAt(app, "StdWidget;")
        assertEquals(1, targets.size)
        val target = targets[0] as TypeSpecNamedElement
        assertEquals("StdWidget", target.name)
        assertEquals("main.tsp", target.containingFile.name)
    }

    // ---- 3: shadowing — a local declaration with a std name must still win ------------------

    fun testLocalModelNamedStringShadowsStdScalarString() {
        installStdLibrary()
        val app = tsp(
            "app-shadow-string.tsp",
            "model string {}\nmodel M { a: string; }",
        ) as TypeSpecFile

        // offset of "a: string" lands mid-way through "a: "; adjust to the identifier itself.
        val offset = app.text.indexOf("a: string") + "a: ".length
        val ref = app.findReferenceAt(offset) as PsiPolyVariantReference
        val results = ref.multiResolve(false).map { it.element as TypeSpecNamedElement }
        assertEquals(1, results.size)
        assertEquals(
            "the local model named 'string' must win, not the std scalar in intrinsics.tsp",
            "app-shadow-string.tsp",
            results[0].containingFile.name,
        )
    }

    fun testLocalModelShadowsStdDecoratorNamedDoc() {
        installStdLibrary(mainBody = "extern dec doc(target: unknown);\n")
        val app = tsp(
            "app-shadow-doc.tsp",
            "model doc {}\nalias X = doc;",
        ) as TypeSpecFile

        val offset = app.text.indexOf("= doc") + "= ".length
        val ref = app.findReferenceAt(offset) as PsiPolyVariantReference
        val results = ref.multiResolve(false).map { it.element as TypeSpecNamedElement }
        assertEquals(1, results.size)
        assertEquals(
            "the local model named 'doc' must win over the std dec statement of the same name",
            "app-shadow-doc.tsp",
            results[0].containingFile.name,
        )
    }

    // ---- 4: the ambient using is file-root-scope only, not offered inside a nested namespace -

    /**
     * If [TypeSpecResolver.ambientStdUsing]'s `scope.segments.isNotEmpty()` guard were ever
     * removed, this test would start failing: at `App`'s own (non-empty) scope, the ambient
     * using would be added to `App`'s using-target list *alongside* the explicit
     * `using Other;`, matching against std's `TypeSpec.Thing` *as well as* `Other.Thing` in the
     * very same lookup — turning a single, correct resolution into two. Because
     * `resolveLeadingSegmentIn` returns as soon as one scope's using-targets yield a match, that
     * spurious duplicate would never be corrected by continuing up the chain: the reference
     * would resolve to *both* declarations, and Cmd-click would offer a chooser it should not.
     */
    fun testAmbientUsingNotOfferedAtNestedNamespaceScope() {
        installStdLibrary(mainBody = "model Thing {}\n")
        val app = tsp(
            "app-nested-scope.tsp",
            """
            namespace App {
              using Other;
              model UseSite { x: Thing; }
            }
            namespace Other {
              model Thing {}
            }
            """.trimIndent(),
        ) as TypeSpecFile

        val otherThing = PsiTreeUtil.findChildrenOfType(app, TypeSpecModelStatement::class.java)
            .single { it.name == "Thing" }

        val offset = app.text.indexOf("x: Thing") + "x: ".length
        val ref = app.findReferenceAt(offset) as PsiPolyVariantReference
        val results = ref.multiResolve(false)

        assertEquals(
            "ambient std using must not be offered at App's own nested scope — only one match " +
                "(Other.Thing) is expected, not a spurious second one from std TypeSpec.Thing",
            1,
            results.size,
        )
        assertSame(otherThing, results[0].element)
    }

    // ---- 5: absent @typespec/compiler degrades quietly, no exception -----------------------

    fun testAbsentStdLibraryDegradesQuietlyWithoutThrowing() {
        // deliberately no node_modules at all
        val app = tsp("app-no-std.tsp", "model M { a: string; }") as TypeSpecFile

        var threw: Throwable? = null
        var results: List<Any?> = emptyList()
        try {
            results = resolveAt(app, "string")
        } catch (t: Throwable) {
            threw = t
        }
        assertNull("resolving a built-in with no @typespec/compiler installed must never throw", threw)
        assertTrue("no library installed means 'string' stays unresolved", results.isEmpty())
    }

    // ---- 6: the intrinsics edge specifically — reached even though nothing imports it -------

    fun testIntrinsicsFileReachedEvenThoughNothingImportsIt() {
        val stdMain = installStdLibrary()
        val app = tsp("app-intrinsics-edge.tsp", "model M {}") as TypeSpecFile

        // sanity: lib/std/main.tsp's own import list does not mention intrinsics.tsp — the
        // compiler loads it out-of-band, not through the std entry point's import closure.
        assertTrue(
            "fixture sanity: lib/std/main.tsp must not itself import intrinsics.tsp, or this " +
                "test would not distinguish the out-of-band edge from an ordinary import",
            stdMain.getImportStatements().isEmpty(),
        )

        val closure = TypeSpecImportGraph.transitiveClosure(app)
        val closureNames = closure.mapNotNull { it.virtualFile?.path }
        assertTrue(
            "lib/intrinsics.tsp must be in the closure via the out-of-band seed — actual: $closureNames",
            closureNames.any { it.endsWith("lib/intrinsics.tsp") },
        )
    }

    // ---- 7: entry point is lib/std/main.tsp, not lib/main.tsp -------------------------------

    fun testEntryPointIsLibStdMainTspNotLibMainTsp() {
        tsp(
            "node_modules/@typespec/compiler/package.json",
            """{"tspMain": "lib/std/main.tsp"}""",
        )
        val decoy = tsp("node_modules/@typespec/compiler/lib/main.tsp", "model WrongEntry {}")
        val realEntry = tsp(
            "node_modules/@typespec/compiler/lib/std/main.tsp",
            "namespace TypeSpec;\nmodel RightEntry {}\n",
        )
        tsp("node_modules/@typespec/compiler/lib/intrinsics.tsp", STD_INTRINSICS)
        val app = tsp("app-entry-point.tsp", "model M {}") as TypeSpecFile

        val closure = TypeSpecImportGraph.transitiveClosure(app)
        val closureFiles = closure.mapNotNull { it.virtualFile }

        assertTrue(
            "the real tspMain (lib/std/main.tsp) must be in the closure",
            realEntry.virtualFile in closureFiles,
        )
        assertFalse(
            "a naive lib/main.tsp guess must NOT be in the closure — it is not the real entry point",
            decoy.virtualFile in closureFiles,
        )
    }

    // ---- 8: reached along an import edge, tier C's node_modules exclusion is untouched ------

    fun testStdLibraryReachedByImplicitEdgeButExcludedFromTierCWordIndex() {
        installStdLibrary(mainBody = "model StdLibDistinctiveWord {}\n")
        val app = tsp("app-scope-pin.tsp", "model M { a: StdLibDistinctiveWord; }") as TypeSpecFile

        // reached — with NO explicit import statement anywhere in app.tsp.
        assertTrue(app.getImportStatements().isEmpty())
        val closure = TypeSpecImportGraph.transitiveClosure(app)
        val stdFile = closure.firstOrNull { it.virtualFile?.path?.endsWith("lib/std/main.tsp") == true }
        assertNotNull("the std library must be reached via the implicit edge alone", stdFile)

        // ...but tier C's word-index prefilter must still exclude it (ADR 0010 D1, ADR 0008).
        val scope = TypeSpecSearchScopes.tspScope(project)
        assertFalse(
            "the std library file reached along the implicit import edge must stay excluded " +
                "from tspScope, exactly like any other node_modules file",
            scope.contains(stdFile!!.virtualFile!!),
        )
        val hits = TypeSpecSearchScopes.filesContainingWord(project, "StdLibDistinctiveWord")
        val hitNames = hits?.map { it.name }.orEmpty()
        assertFalse(
            "tier C's word index must not surface the std library file even though the " +
                "implicit import edge can reach it — actual hits: $hitNames",
            hitNames.contains(stdFile.name),
        )
    }

    // ---- 9: cost — closure is cached and does not crowd CLOSURE_CAP ------------------------

    fun testStdLibraryClosureIsCachedAndFarBelowCap() {
        installStdLibrary(mainBody = "model StdWidget {}\n")
        val app = tsp("app-cost.tsp", "model M { a: string; b: StdWidget; }") as TypeSpecFile

        val first = TypeSpecImportGraph.transitiveClosure(app)
        val second = TypeSpecImportGraph.transitiveClosure(app)
        assertSame("the closure must be cached (same Set instance) across calls", first, second)

        assertTrue(
            "std-library closure (app + lib/std/main.tsp + lib/intrinsics.tsp) should be a " +
                "handful of files, not anywhere near CLOSURE_CAP=${TypeSpecImportGraph.CLOSURE_CAP} " +
                "— actual: ${first.size}",
            first.size <= 10,
        )

        // wall-clock smoke check, not a benchmark: a cached re-read must be effectively free.
        val start = System.nanoTime()
        repeat(1000) { TypeSpecImportGraph.transitiveClosure(app) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(
            "1000 cached closure reads took ${elapsedMs}ms — expected well under 1000ms if " +
                "caching is actually working",
            elapsedMs < 1000,
        )
    }
}
