package simpli.fyi.plugins.typespec.resolve

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecAliasStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecEnumStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecInterfaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecOpStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecScalarStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecUnionStatement

/**
 * The core M5.5a resolve suite (plan 02 "Acceptance — what `tsp-tester` writes" §
 * `TypeSpecResolveTest`). Cases numbered per plan 02's table. `elementAtCaret` is the
 * canonical assertion — it goes through `TargetElementUtil`, the same code path Ctrl-click
 * uses (plan 02, risk 1).
 *
 * M5.5a ships tiers A (current file) and B (transitive `import` closure) only; tier C
 * (project-wide word-index prefilter, case 15) is explicitly deferred to M5.5b (plan 02
 * risk 9). Case 15 here pins *current* (tier-C-absent) behaviour, not the eventual one.
 */
class TypeSpecResolveTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // ---- 1: same-file model -> model -----------------------------------------------------

    fun testSameFileModelToModel() {
        myFixture.configureByFile("resolve/same-file-model.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecNamedElement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
        assertEquals("same-file-model.tsp", target.containingFile.name)
    }

    // ---- 2: every declaration kind --------------------------------------------------------

    fun testResolvesToEveryDeclarationKind() {
        myFixture.configureByFile("resolve/all-kinds.tsp")
        val text = myFixture.file.text

        fun resolveUsage(declName: String): com.intellij.psi.PsiElement? {
            val offset = text.lastIndexOf(declName)
            assertTrue("marker $declName not found twice in fixture", offset > 0)
            myFixture.editor.caretModel.moveToOffset(offset)
            return myFixture.file.findReferenceAt(offset)?.resolve()
        }

        val model = resolveUsage("TargetModel")
        assertTrue(model is TypeSpecModelStatement)
        assertEquals("TargetModel", (model as TypeSpecNamedElement).name)

        val enum = resolveUsage("TargetEnum")
        assertTrue(enum is TypeSpecEnumStatement)

        val union = resolveUsage("TargetUnion")
        assertTrue(union is TypeSpecUnionStatement)

        val iface = resolveUsage("TargetInterface")
        assertTrue(iface is TypeSpecInterfaceStatement)

        val alias = resolveUsage("TargetAlias")
        assertTrue(alias is TypeSpecAliasStatement)

        val scalar = resolveUsage("TargetScalar")
        assertTrue(scalar is TypeSpecScalarStatement)

        val op = resolveUsage("targetOp")
        assertTrue(op is TypeSpecOpStatement)

        val namespace = resolveUsage("TargetNamespace")
        assertTrue(namespace is TypeSpecNamespaceStatement)
    }

    // ---- 3: forward reference --------------------------------------------------------------

    fun testForwardReferenceResolves() {
        myFixture.configureByFile("resolve/forward-ref.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecNamedElement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
    }

    // ---- 4: shadowing -------------------------------------------------------------------

    fun testInnerNamespaceShadowsOuterDeclaration() {
        myFixture.configureByFile("resolve/shadowing.tsp")
        val target = myFixture.elementAtCaret as TypeSpecNamedElement
        assertEquals("Foo", target.name)
        // the SHADOWING (inner) Foo, not the outer one -> its enclosing namespace is Inner.
        val enclosingNamespace = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            target,
            TypeSpecNamespaceStatement::class.java,
        )
        assertNotNull("shadowing Foo must be nested inside `namespace Inner`", enclosingNamespace)
        assertEquals("Inner", enclosingNamespace!!.name)
    }

    // ---- 5 / 6: qualified name, last / first segment ---------------------------------------

    fun testQualifiedNameLastSegmentResolvesToModel() {
        myFixture.configureByFile("resolve/qualified.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Bar", (target as TypeSpecNamedElement).name)
    }

    fun testQualifiedNameFirstSegmentResolvesToNamespace() {
        myFixture.configureByFile("resolve/qualified-first-segment.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecNamespaceStatement)
        assertEquals("Foo", (target as TypeSpecNamedElement).name)
    }

    // ---- 7: dotted namespace declaration ---------------------------------------------------

    /**
     * Plan 02 case 7: `A.B.C.Model` resolves through `namespace A.B.C`. Previously a known bug
     * (`TypeSpecFileDeclarations` only indexed a dotted namespace declaration's last segment,
     * so the leading segments of a qualified reference never resolved); fixed by indexing every
     * dotted segment under its own prefix path, all pointing at the same statement, and by
     * `TypeSpecResolver.resolveSegment` reconstructing the denoted path explicitly instead of
     * reading it back off the (now shared) resolved element via `fullPathOf`.
     *
     * Per the fix's design decision: `namespace A.B.C;` has no standalone declaration of `A` or
     * `A.B` — the intermediate segments resolve to the *same* `namespace A.B.C` statement as the
     * full name does, since that statement is the only declaration site they have.
     */
    fun testDottedNamespaceDeclarationQualifiedReferenceResolves() {
        myFixture.configureByFile("resolve/dotted-ns.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Model", (target as TypeSpecNamedElement).name)
    }

    /** Same fixture, intermediate segments: `A` and `A.B` both resolve to `namespace A.B.C`. */
    fun testDottedNamespaceDeclarationIntermediateSegmentsResolveToSameStatement() {
        myFixture.configureByFile("resolve/dotted-ns-intermediate.tsp")
        val text = myFixture.file.text

        // `A.B.C.Model` (the reference usage) appears exactly once; the declaration
        // `namespace A.B.C {` has no trailing `.Model` and so is not matched by this anchor.
        val usageStart = text.lastIndexOf("A.B.C.Model")
        assertTrue("usage `A.B.C.Model` not found", usageStart >= 0)
        val offsetA = usageStart // "A"
        val offsetB = usageStart + 2 // "B"

        val a = myFixture.file.findReferenceAt(offsetA)?.resolve()
        assertTrue(a is TypeSpecNamespaceStatement)
        assertEquals("C", (a as TypeSpecNamedElement).name)

        val b = myFixture.file.findReferenceAt(offsetB)?.resolve()
        assertTrue(b is TypeSpecNamespaceStatement)
        assertEquals("C", (b as TypeSpecNamedElement).name)

        assertEquals("intermediate segments must resolve to the same declaration node", a, b)
    }

    // ---- multi-file overlapping dotted prefixes (Job 2, investigative) ----------------------

    /**
     * `namespace A.B.C;` in `overlap-a.tsp` and `namespace A.B.D;` in `overlap-b.tsp`, both
     * imported by `overlap-main.tsp`. Per the fix's indexing scheme both files index "A" at
     * path `[]` and "B" at path `["A"]`, so resolving the "B" segment of `A.B.C.Model` should
     * yield BOTH namespace statements (one per file establishing the `A.B` prefix) — the same
     * multi-candidate shape a reopened, non-dotted namespace already produces. This test
     * verifies that reasoning against the real resolver rather than trusting the code-reading
     * derivation.
     */
    fun testMultiFileOverlappingDottedPrefixesMultiResolveToBothStatements() {
        myFixture.configureByFiles("resolve/overlap-main.tsp", "resolve/overlap-a.tsp", "resolve/overlap-b.tsp")
        val text = myFixture.file.text
        val usageStart = text.lastIndexOf("A.B.C.Model")
        assertTrue("usage `A.B.C.Model` not found", usageStart >= 0)
        val offsetB = usageStart + 2 // "B" segment

        val reference = myFixture.file.findReferenceAt(offsetB) as? PsiPolyVariantReference
        assertNotNull(reference)
        val results = reference!!.multiResolve(false)
        val resolvedNamespaces = results.mapNotNull { it.element as? TypeSpecNamespaceStatement }
        assertEquals(
            "expected one candidate per file establishing the A.B prefix (overlap-a.tsp's " +
                "A.B.C and overlap-b.tsp's A.B.D) — actual candidates: " +
                resolvedNamespaces.map { "${it.name}@${it.containingFile.name}" },
            2,
            resolvedNamespaces.size,
        )
        val containingFiles = resolvedNamespaces.map { it.containingFile.name }.toSet()
        assertEquals(setOf("overlap-a.tsp", "overlap-b.tsp"), containingFiles)
    }

    /**
     * `namespace A { }` declared standalone in `prefix-elsewhere-a.tsp`, and `namespace A.B.C;`
     * declared (dotted sugar, prefix "A" is virtual) in `prefix-elsewhere-b.tsp`. Both imported
     * by `prefix-elsewhere-main.tsp`. Resolving the leading "A" segment of `A.B.C.Model`
     * observably yields BOTH: the real `namespace A { }` statement AND the `namespace A.B.C;`
     * statement (indexed under name "A" at path `[]`, per the fix). Both statements genuinely
     * establish the "A" prefix, so two Cmd-click candidates is consistent with how a plain
     * reopened namespace (`namespace A {}` declared twice) already behaves elsewhere in this
     * suite — not a regression, just the same multi-candidate shape applied to a mixed
     * real/virtual prefix. Encoded as observed behaviour, not asserted as ideal UX (see report).
     */
    fun testDottedPrefixDeclaredSeparatelyElsewhereMultiResolve() {
        myFixture.configureByFiles(
            "resolve/prefix-elsewhere-main.tsp",
            "resolve/prefix-elsewhere-a.tsp",
            "resolve/prefix-elsewhere-b.tsp",
        )
        val text = myFixture.file.text
        val usageStart = text.lastIndexOf("A.B.C.Model")
        assertTrue("usage `A.B.C.Model` not found", usageStart >= 0)

        val reference = myFixture.file.findReferenceAt(usageStart) as? PsiPolyVariantReference
        assertNotNull(reference)
        val results = reference!!.multiResolve(false)
        val resolvedNamespaces = results.mapNotNull { it.element as? TypeSpecNamespaceStatement }
        val actual = resolvedNamespaces.map { "${it.name}@${it.containingFile.name}" }.toSet()
        assertEquals(
            "observed candidates for leading segment 'A': $actual",
            setOf("A@prefix-elsewhere-a.tsp", "C@prefix-elsewhere-b.tsp"),
            actual,
        )
    }

    // ---- 8: using -------------------------------------------------------------------------

    fun testUsingBringsInScopeUnqualifiedName() {
        myFixture.configureByFile("resolve/using.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
    }

    /**
     * Regression pin for the StackOverflowError the dev found and fixed: resolving a `using`
     * statement's own target identifier recurses into `TypeSpecScope.usingsVisibleIn` for the
     * SAME scope the `using` statement is declared in — that scope's using list necessarily
     * includes this very statement — and `ResolveCache`'s recursion guard does not catch it
     * because `TypeSpecIdentifierMixin.getReference()` mints a fresh `TypeSpecReference` per
     * call. `TypeSpecScope.resolveUsingTarget`'s `ThreadLocal` re-entrancy guard is what
     * actually breaks the cycle. If that guard is removed, this test hangs / stack-overflows
     * instead of resolving.
     */
    fun testUsingSelfTargetDoesNotStackOverflow() {
        myFixture.configureByFile("resolve/using-self-target.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecNamespaceStatement)
        assertEquals("Common", (target as TypeSpecNamedElement).name)
    }

    // ---- 9: using relative to blockless namespace -------------------------------------------

    fun testUsingRelativeToBlocklessNamespace() {
        myFixture.configureByFiles("resolve/using-relative-main.tsp", "resolve/using-relative-dep.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
        assertEquals("using-relative-dep.tsp", target.containingFile.name)
    }

    // ---- 10: blockless namespace file --------------------------------------------------------

    fun testBlocklessNamespaceSiblingsResolveUnqualified() {
        myFixture.configureByFile("resolve/blockless.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
    }

    // ---- 11: cross-file via import (tier B) --------------------------------------------------

    fun testCrossFileResolutionViaImport() {
        myFixture.configureByFiles("resolve/import-main.tsp", "resolve/import-dep.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
        assertEquals("import-dep.tsp", target.containingFile.name)
    }

    // ---- 12: transitive import ----------------------------------------------------------------

    fun testTransitiveImportTwoHopsAway() {
        myFixture.configureByFiles("resolve/t-a.tsp", "resolve/t-b.tsp", "resolve/t-c.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
        assertEquals("t-c.tsp", target.containingFile.name)
    }

    // ---- 13: import cycle -----------------------------------------------------------------

    fun testImportCycleResolvesAndTerminates() {
        myFixture.configureByFiles("resolve/cycle-a.tsp", "resolve/cycle-b.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
        assertEquals("cycle-b.tsp", target.containingFile.name)
    }

    // ---- 14: directory import ----------------------------------------------------------------

    fun testDirectoryImportFollowsMainTsp() {
        myFixture.configureByFiles("resolve/dir-main.tsp", "resolve/sub/main.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
        assertEquals("main.tsp", target.containingFile.name)
    }

    // ---- 15: cross-file, no import (tier C) ----------------------------------------------------

    /**
     * Plan 02 case 15, now shipped in M5.5b: `merged-a.tsp` and `merged-b.tsp` declare the SAME
     * namespace and neither imports the other, so tiers A/B (current file + transitive import
     * closure) see nothing — this is a pure tier-C case (project-wide word-index prefilter,
     * `TypeSpecSearchScopes.filesContainingWord`). ADR 0004 F4 calls this "the norm in real
     * TypeSpec projects, not an edge case", since the compiler merges same-named namespaces
     * declared across files that share a compilation but do not `import` each other directly
     * (both are pulled in by a shared entry point instead). This replaces the M5.5a pin that
     * documented tier C as not-yet-shipped.
     */
    fun testCrossFileNoImportResolvesViaTierCWordIndex() {
        myFixture.configureByFiles("resolve/merged-b.tsp", "resolve/merged-a.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
        assertEquals("merged-a.tsp", target.containingFile.name)
    }

    // ---- 16: built-in type -------------------------------------------------------------------

    fun testBuiltinTypeDoesNotResolveAndIsSoft() {
        myFixture.configureByFile("resolve/builtin.tsp")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertNull(reference!!.resolve())
        assertTrue(reference.isSoft)
    }

    // ---- 17: unknown name ---------------------------------------------------------------------

    fun testUnknownNameDoesNotResolveNoException() {
        myFixture.configureByFile("resolve/unknown.tsp")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertNull(reference!!.resolve())
    }

    // ---- 18: backticked identifier -----------------------------------------------------------

    fun testBacktickedIdentifierResolves() {
        myFixture.configureByFile("resolve/backticked.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("model", (target as TypeSpecNamedElement).name)
    }

    // ---- 19: template argument -----------------------------------------------------------------

    fun testTemplateArgumentResolves() {
        myFixture.configureByFile("resolve/template-arg.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Address", (target as TypeSpecNamedElement).name)
    }

    // ---- 20: spread -----------------------------------------------------------------------------

    fun testSpreadResolves() {
        myFixture.configureByFile("resolve/spread.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Base", (target as TypeSpecNamedElement).name)
    }

    // ---- 21: extends / is -------------------------------------------------------------------

    fun testExtendsResolves() {
        myFixture.configureByFile("resolve/extends-is.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Base", (target as TypeSpecNamedElement).name)
    }

    fun testIsResolves() {
        myFixture.configureByFile("resolve/is-clause.tsp")
        val target = myFixture.elementAtCaret
        assertTrue(target is TypeSpecModelStatement)
        assertEquals("Base", (target as TypeSpecNamedElement).name)
    }

    // ---- 22: declaration name is not a reference -----------------------------------------------

    fun testDeclarationOwnNameHasNoReference() {
        myFixture.configureByFile("resolve/decl-name.tsp")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNull(reference)
    }

    // ---- 23: broken file --------------------------------------------------------------------

    fun testBrokenFileDoesNotResolveNoExceptionNoHang() {
        myFixture.configureByFile("resolve/broken.tsp")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        // A syntax error must not throw and must not resolve to anything meaningful.
        reference?.resolve()
    }

    // ---- 24: self-referential alias --------------------------------------------------------------

    fun testSelfReferentialAliasTerminatesWithoutStackOverflow() {
        myFixture.configureByFile("resolve/alias-cycle.tsp")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset) as? PsiPolyVariantReference
        assertNotNull(reference)
        // Must terminate (needToPreventRecursion = true in TypeSpecReference); the result value
        // itself is secondary to "this call returns at all".
        reference!!.multiResolve(false)
    }
}
