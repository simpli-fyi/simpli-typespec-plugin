package simpli.fyi.plugins.typespec.resolve

import com.intellij.psi.PsiReferenceBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecFile

/**
 * `TypeSpecImportReference`/`TypeSpecImportStatementMixin` — M5.6b
 * ([ADR 0010](../../../../../../../../docs/adr/0010-library-import-resolution.md),
 * [plan 05](../../../../../../../../docs/plans/05-import-and-decorator-navigation.md)). Exercises
 * the reference through `PsiFile.findReferenceAt`, the same entry point `TargetElementUtil`
 * (Cmd-click) uses, against fixtures under `src/test/testData/imports/` (deliberately not under
 * `corpus/` — `TypeSpecCorpusTest` walks that tree and would adopt them).
 */
class TypeSpecImportReferenceTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // ---- relative import ----------------------------------------------------------------------

    fun testRelativeImportResolvesInsideQuotes() {
        myFixture.configureByFiles("imports/consumer/main.tsp", "imports/master-data/branch.tsp")
        val text = myFixture.file.text
        val offset = text.indexOf("master-data")
        assertTrue(offset > 0)

        val reference = myFixture.file.findReferenceAt(offset)
        assertNotNull("expected a reference inside the import string", reference)

        val resolved = reference!!.resolve()
        assertTrue("expected a TypeSpecFile, got $resolved", resolved is TypeSpecFile)
        assertEquals("branch.tsp", (resolved as TypeSpecFile).name)
    }

    // ---- offset on the quote character / on the `import` keyword: no reference --------------

    fun testOffsetOnQuoteCharacterAndOnImportKeywordYieldNoReference() {
        myFixture.configureByFiles("imports/consumer/main.tsp", "imports/master-data/branch.tsp")
        val text = myFixture.file.text

        val quoteOffset = text.indexOf("\"")
        assertTrue(quoteOffset >= 0)
        assertNull(
            "the opening quote character itself must not be part of the reference's range",
            myFixture.file.findReferenceAt(quoteOffset),
        )

        val importKeywordOffset = text.indexOf("import")
        assertTrue(importKeywordOffset >= 0)
        assertNull(
            "the `import` keyword must not carry a reference",
            myFixture.file.findReferenceAt(importKeywordOffset),
        )

        // Sanity: an offset genuinely inside the string DOES yield a reference, so the two
        // negative assertions above are meaningful and not just a missing fixture/offset bug.
        val insideStringOffset = text.indexOf("master-data") + 1
        assertNotNull(
            "an offset inside the string contents must yield a reference",
            myFixture.file.findReferenceAt(insideStringOffset),
        )
    }

    // ---- library (bare specifier) import ---------------------------------------------------

    fun testLibraryImportResolvesViaNodeModules() {
        myFixture.configureByFiles(
            "imports/consumer/lib-consumer.tsp",
            "imports/consumer/node_modules/@acme/widgets/package.json",
            "imports/consumer/node_modules/@acme/widgets/lib/main.tsp",
        )
        val text = myFixture.file.text
        val offset = text.indexOf("widgets")
        assertTrue(offset > 0)

        val reference = myFixture.file.findReferenceAt(offset)
        assertNotNull(reference)
        val resolved = reference!!.resolve()
        assertTrue("expected a TypeSpecFile, got $resolved", resolved is TypeSpecFile)
        assertEquals("main.tsp", (resolved as TypeSpecFile).name)
        assertEquals(
            "must resolve into the fixture's own node_modules package, not some other main.tsp",
            "lib",
            resolved.virtualFile?.parent?.name,
        )
    }

    // ---- upward node_modules walk from a nested directory (monorepo) ------------------------

    fun testLibraryImportResolvesViaUpwardNodeModulesWalkFromNestedDirectory() {
        myFixture.configureByFiles(
            "imports/monorepo/apps/service/consumer.tsp",
            "imports/monorepo/node_modules/@acme/mono-pkg/main.tsp",
        )
        val text = myFixture.file.text
        val offset = text.indexOf("mono-pkg")
        assertTrue(offset > 0)

        val reference = myFixture.file.findReferenceAt(offset)
        assertNotNull(reference)
        val resolved = reference!!.resolve()
        assertTrue("expected a TypeSpecFile, got $resolved", resolved is TypeSpecFile)
        assertEquals(
            "node_modules two directories above apps/service must be found by the upward walk",
            "main.tsp",
            (resolved as TypeSpecFile).name,
        )
        assertTrue(resolved.virtualFile!!.path.contains("monorepo/node_modules/@acme/mono-pkg"))
    }

    // ---- directory import --------------------------------------------------------------------

    fun testDirectoryImportResolvesToEntryPoint() {
        myFixture.configureByFiles("imports/consumer/dir-consumer.tsp", "imports/consumer/dep-dir/main.tsp")
        val text = myFixture.file.text
        val offset = text.indexOf("dep-dir")
        assertTrue(offset > 0)

        val reference = myFixture.file.findReferenceAt(offset)
        assertNotNull(reference)
        val resolved = reference!!.resolve()
        assertTrue("expected a TypeSpecFile, got $resolved", resolved is TypeSpecFile)
        assertEquals("main.tsp", (resolved as TypeSpecFile).name)
    }

    // ---- missing target: null resolve, soft reference, no error highlighting ----------------

    fun testMissingImportTargetResolvesToNullIsSoftAndPaintsNoError() {
        myFixture.configureByFile("imports/consumer/missing-consumer.tsp")
        val text = myFixture.file.text
        val offset = text.indexOf("does-not-exist")
        assertTrue(offset > 0)

        val reference = myFixture.file.findReferenceAt(offset)
        assertNotNull("a reference must still exist even though the target is missing", reference)
        assertNull("an import to a nonexistent file must resolve to null", reference!!.resolve())
        assertTrue(
            "must be a PsiReferenceBase to assert isSoft",
            reference is PsiReferenceBase<*>,
        )
        assertTrue(
            "ADR 0010 D5: an unresolved import is soft, never a hard/red reference",
            (reference as PsiReferenceBase<*>).isSoft,
        )

        // Literal acceptance-criteria assertion (plan 05 M5.6b). Note: the plugin has no
        // unresolved-reference HighlightVisitor (ADR 0010 D5's own words) and
        // TypeSpecSyntaxHighlighterTest documents checkHighlighting as producing an empty
        // HighlightInfo set regardless of soft/hard (ADR 0003 D4) — so this call is expected to
        // trivially pass today and is a weak assertion on its own; isSoft above is the real one.
        myFixture.checkHighlighting(true, false, true)
    }

    // ---- scope invariant: reference resolves into node_modules, tier C still excludes it -----

    /**
     * ADR 0010 D1, exercised end-to-end through the actual `PsiReference` a Cmd-click uses (not
     * just the resolver directly, see [TypeSpecImportResolverTest]): the exact node_modules file
     * `lib-consumer.tsp`'s import resolves to must still be invisible to
     * [TypeSpecSearchScopes.filesContainingWord]/[TypeSpecSearchScopes.tspScope]. The two
     * mechanisms coexist over the *same* file.
     */
    fun testLibraryReferenceResolvesIntoNodeModulesWhileTierCSearchExcludesIt() {
        myFixture.configureByFiles(
            "imports/consumer/lib-consumer.tsp",
            "imports/consumer/node_modules/@acme/widgets/package.json",
            "imports/consumer/node_modules/@acme/widgets/lib/main.tsp",
        )
        val text = myFixture.file.text
        val offset = text.indexOf("widgets")
        val reference = myFixture.file.findReferenceAt(offset)
        val resolved = reference?.resolve() as? TypeSpecFile
        assertNotNull("sanity: the reference must resolve into node_modules", resolved)
        val resolvedVirtualFile = resolved!!.virtualFile!!
        assertTrue(resolvedVirtualFile.path.contains("node_modules"))

        val scope = TypeSpecSearchScopes.tspScope(project)
        assertFalse(
            "the very file the import reference just resolved to must stay outside tspScope",
            scope.contains(resolvedVirtualFile),
        )

        val hits = TypeSpecSearchScopes.filesContainingWord(project, "Widget")
        val hitPaths = hits?.mapNotNull { it.virtualFile?.path }.orEmpty()
        assertFalse(
            "tier C word-index search must not surface the node_modules file even though the " +
                "import reference reaches it — actual hits: $hitPaths",
            hitPaths.contains(resolvedVirtualFile.path),
        )
    }
}
