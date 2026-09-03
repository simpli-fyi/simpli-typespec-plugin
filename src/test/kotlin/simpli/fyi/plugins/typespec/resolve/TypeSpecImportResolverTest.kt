package simpli.fyi.plugins.typespec.resolve

import com.intellij.psi.stubs.StubTreeLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.stubs.TypeSpecStubQueries

/**
 * `TypeSpecImportResolver` — M5.6a ([ADR 0010](../../../../../../../../docs/adr/0010-library-import-resolution.md),
 * [plan 05](../../../../../../../../docs/plans/05-import-and-decorator-navigation.md)). Fixtures
 * are built programmatically via `myFixture.addFileToProject`, same technique as
 * `TypeSpecSearchScopesTest`'s `node_modules` fixtures, so every `package.json` shape lives next
 * to the assertion it backs rather than in a shared testData tree.
 */
class TypeSpecImportResolverTest : BasePlatformTestCase() {

    private fun tsp(path: String, text: String) = myFixture.addFileToProject(path, text)

    // ---- relative: file, directory-with-main.tsp, directory-with-package.json --------------

    fun testRelativeFileResolves() {
        val target = tsp("dep/target.tsp", "model Target {}")
        val from = tsp("main.tsp", "import \"./dep/target.tsp\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "./dep/target.tsp")
        assertEquals(target.virtualFile, resolved?.virtualFile)
    }

    fun testRelativeDirectoryFallsBackToMainTsp() {
        val target = tsp("dep2/main.tsp", "model Target2 {}")
        val from = tsp("main2.tsp", "import \"./dep2\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "./dep2")
        assertEquals(target.virtualFile, resolved?.virtualFile)
    }

    fun testRelativeDirectoryWithPackageJsonTspMainWinsOverMainTsp() {
        tsp("dep3/package.json", """{"tspMain": "lib/entry.tsp"}""")
        val target = tsp("dep3/lib/entry.tsp", "model Target3 {}")
        tsp("dep3/main.tsp", "model WrongTarget {}") // decoy — must not be picked
        val from = tsp("main3.tsp", "import \"./dep3\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "./dep3")
        assertEquals(target.virtualFile, resolved?.virtualFile)
    }

    // ---- bare specifier, node_modules two directories up (workspace monorepo) --------------

    fun testBareSpecifierFoundTwoDirectoriesUpNodeModulesWalk() {
        val target = tsp("workspace/node_modules/@scope/pkg/main.tsp", "model Pkg {}")
        val from = tsp("workspace/model/nested/consumer.tsp", "import \"@scope/pkg\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "@scope/pkg")
        assertEquals(
            "must walk up past workspace/model/nested and workspace/model before finding " +
                "workspace/node_modules",
            target.virtualFile,
            resolved?.virtualFile,
        )
    }

    // ---- entry-point precedence (ADR 0010 D2, mirroring the two real packages that break a --
    // ---- naive lib/main.tsp guess) -----------------------------------------------------------

    fun testTspMainPickedWhenNotLibMainTspMirroringProtobuf() {
        tsp(
            "node_modules/@typespec/protobuf/package.json",
            """{"tspMain": "lib/proto.tsp"}""",
        )
        val target = tsp("node_modules/@typespec/protobuf/lib/proto.tsp", "model Proto {}")
        val from = tsp("app-protobuf.tsp", "import \"@typespec/protobuf\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "@typespec/protobuf")
        assertEquals(
            "a naive <pkg>/lib/main.tsp guess is wrong here — the real tspMain is lib/proto.tsp",
            target.virtualFile,
            resolved?.virtualFile,
        )
    }

    fun testTspMainPickedWhenNestedMirroringCompiler() {
        tsp(
            "node_modules/@typespec/compiler/package.json",
            """{"tspMain": "lib/std/main.tsp"}""",
        )
        val target = tsp("node_modules/@typespec/compiler/lib/std/main.tsp", "model Std {}")
        val from = tsp("app-compiler.tsp", "import \"@typespec/compiler\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "@typespec/compiler")
        assertEquals(
            "a naive <pkg>/lib/main.tsp guess is wrong here — the real tspMain is lib/std/main.tsp",
            target.virtualFile,
            resolved?.virtualFile,
        )
    }

    fun testExportsDotTypespecWinsOverTspMain() {
        tsp(
            "node_modules/@acme/openapi-like/package.json",
            """
            {
              "tspMain": "lib/wrong.tsp",
              "exports": { ".": { "typespec": "./lib/right.tsp" } }
            }
            """.trimIndent(),
        )
        tsp("node_modules/@acme/openapi-like/lib/wrong.tsp", "model Wrong {}") // decoy
        val target = tsp("node_modules/@acme/openapi-like/lib/right.tsp", "model Right {}")
        val from = tsp("app-exports.tsp", "import \"@acme/openapi-like\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "@acme/openapi-like")
        assertEquals(
            "exports[\".\"].typespec must win over tspMain (ADR 0010 D2)",
            target.virtualFile,
            resolved?.virtualFile,
        )
    }

    fun testFallsBackToMainTspWhenPackageJsonHasNeitherField() {
        tsp("node_modules/@acme/bare/package.json", """{"name": "@acme/bare"}""")
        val target = tsp("node_modules/@acme/bare/main.tsp", "model Bare {}")
        val from = tsp("app-bare.tsp", "import \"@acme/bare\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "@acme/bare")
        assertEquals(target.virtualFile, resolved?.virtualFile)
    }

    fun testFallsBackToMainTspWhenNoPackageJsonAtAll() {
        val target = tsp("node_modules/@acme/no-package-json/main.tsp", "model NoPkg {}")
        val from = tsp("app-no-pkg-json.tsp", "import \"@acme/no-package-json\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "@acme/no-package-json")
        assertEquals(target.virtualFile, resolved?.virtualFile)
    }

    // ---- graceful degradation: missing package, malformed package.json ----------------------

    fun testMissingPackageResolvesToNullWithoutThrowing() {
        val from = tsp("app-missing.tsp", "import \"@does-not/exist\";") as TypeSpecFile

        var resolved: TypeSpecFile? = null
        var threw: Throwable? = null
        try {
            resolved = TypeSpecImportResolver.resolve(from, "@does-not/exist")
        } catch (t: Throwable) {
            threw = t
        }
        assertNull("resolve() must never throw for a missing package", threw)
        assertNull(resolved)
    }

    fun testMalformedPackageJsonDegradesToNullWhenNoMainTspFallback() {
        tsp("node_modules/@acme/malformed-no-fallback/package.json", "{ this is not valid json")
        val from = tsp("app-malformed-1.tsp", "import \"@acme/malformed-no-fallback\";") as TypeSpecFile

        var threw: Throwable? = null
        var resolved: TypeSpecFile? = null
        try {
            resolved = TypeSpecImportResolver.resolve(from, "@acme/malformed-no-fallback")
        } catch (t: Throwable) {
            threw = t
        }
        assertNull("a malformed package.json must never throw out of resolve()", threw)
        assertNull(
            "no main.tsp fallback exists either, so the overall result must be null, not an exception",
            resolved,
        )
    }

    fun testMalformedPackageJsonDegradesGracefullyToMainTspFallback() {
        tsp("node_modules/@acme/malformed-with-fallback/package.json", "{ not: valid, json ][")
        val target = tsp("node_modules/@acme/malformed-with-fallback/main.tsp", "model Fallback {}")
        val from = tsp("app-malformed-2.tsp", "import \"@acme/malformed-with-fallback\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "@acme/malformed-with-fallback")
        assertEquals(
            "malformed package.json must degrade to the main.tsp directory-index fallback, not throw",
            target.virtualFile,
            resolved?.virtualFile,
        )
    }

    // ---- non-.tsp target is skipped, silently -------------------------------------------------

    fun testJsTargetResolvesToNull() {
        tsp("dist/src/tsp-index.js", "module.exports = {};")
        val from = tsp("app-js-target.tsp", "import \"./dist/src/tsp-index.js\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "./dist/src/tsp-index.js")
        assertNull(resolved)
    }

    // ---- transitive closure follows a library import (the M5.6a<->M5.6d interlock) ----------

    fun testTransitiveClosureFollowsLibraryImportAndItsOwnRelativeImports() {
        tsp(
            "node_modules/@typespec/openapi/package.json",
            """{"tspMain": "lib/main.tsp"}""",
        )
        val libMain = tsp(
            "node_modules/@typespec/openapi/lib/main.tsp",
            "import \"./decorators.tsp\";\n\nnamespace TypeSpec.OpenAPI {}\n",
        )
        val libDecorators = tsp(
            "node_modules/@typespec/openapi/lib/decorators.tsp",
            "namespace TypeSpec.OpenAPI;\nextern dec info(target: unknown);\n",
        )
        val app = tsp("app-closure.tsp", "import \"@typespec/openapi\";\n") as TypeSpecFile

        val closure = TypeSpecImportGraph.transitiveClosure(app)
        val closureNames = closure.mapNotNull { it.virtualFile?.path }.toSet()

        assertTrue(app.virtualFile!!.path in closureNames)
        assertTrue("library entry point must be in the closure", libMain.virtualFile.path in closureNames)
        assertTrue(
            "the library's own relative import must be followed too",
            libDecorators.virtualFile.path in closureNames,
        )
        assertEquals(3, closure.size)
    }

    // ---- regression pin: this door does not widen tspScope (ADR 0010 D1) --------------------

    /**
     * The library file the resolver just found via a targeted lookup must still be absent from
     * the stub index's project-wide lookup — the two mechanisms are disjoint (ADR 0010 D1, plan
     * 06 M6.5c). Retargeted off the deleted `filesContainingWord`/`TIER_C_FILE_CAP` word-index
     * path onto [TypeSpecStubQueries.declarationsNamed]; this is the same fixture the closure
     * test above uses, so it is the *same* node_modules file proven reachable by import
     * resolution and unreachable by project-wide search, in one place.
     */
    fun testResolvedLibraryFileStaysExcludedFromStubIndex() {
        tsp(
            "node_modules/@acme/scope-pin/package.json",
            """{"tspMain": "lib/main.tsp"}""",
        )
        val libFile = tsp(
            "node_modules/@acme/scope-pin/lib/main.tsp",
            "model ScopePinDistinctiveWord {}",
        )
        val from = tsp("app-scope-pin.tsp", "import \"@acme/scope-pin\";") as TypeSpecFile

        val resolved = TypeSpecImportResolver.resolve(from, "@acme/scope-pin")
        assertEquals(
            "sanity: the targeted lookup does find the node_modules file",
            libFile.virtualFile,
            resolved?.virtualFile,
        )

        val scope = TypeSpecSearchScopes.tspScope(project)
        assertFalse(
            "the same node_modules file reachable by import resolution must stay excluded from tspScope",
            scope.contains(libFile.virtualFile),
        )

        // ADR 0011 D4: excluded at stub-build time, not merely at query time. `stub == null` is
        // the wrong assertion here (an ad hoc unpersisted tree can still be produced for an
        // excluded file) — `canHaveStub` is what actually pins "no stub is ever built".
        assertFalse(
            "a node_modules file must never be eligible for a stub tree at all",
            StubTreeLoader.getInstance().canHaveStub(libFile.virtualFile),
        )

        val hits = TypeSpecStubQueries.declarationsNamed(project, "ScopePinDistinctiveWord", null)
        val hitNames = hits.mapNotNull { it.containingFile?.name }
        assertFalse(
            "the stub index must not surface a node_modules file even though import " +
                "resolution can reach it by targeted lookup — actual hits: $hitNames",
            hitNames.contains(libFile.name),
        )
        assertTrue("expected zero index hits for a name declared only in node_modules", hits.isEmpty())
    }
}
