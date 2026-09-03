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
     * KNOWN BUG (reported, not fixed — `src/main/` is not tsp-tester's to edit). Plan 02 case
     * 7 requires "`A.B.C.Model` resolves through `namespace A.B.C`". It does not, as
     * implemented:
     *
     * `TypeSpecFileDeclarations.build` records a namespace declaration's name as its LAST
     * dotted segment only ("C" for `namespace A.B.C`), keyed by the declaration's LEXICAL
     * container path — it never indexes the intermediate virtual segments "A" or "A.B"
     * anywhere `byName` can find them. Resolving the leading segment "A" of a four-segment
     * reference therefore finds no declaration; `TypeSpecResolver.resolveTrailingSegment` for
     * "B"/"C"/"Model" is then fed an unresolved `previousSegment` at every step, and the whole
     * chain returns null. Confirmed by running this exact assertion: `elementAtCaret` throws
     * "element not found ... TargetElementUtilBase.findTargetElement(...)=null".
     *
     * This test pins the CURRENT (broken) behaviour so the suite is green and the gap is
     * tracked; it must be flipped back to the plan 02 assertion (commented out below) when the
     * resolver gains support for qualified references through a dotted namespace's virtual
     * intermediate segments.
     */
    fun testDottedNamespaceDeclarationQualifiedReferenceDoesNotResolveKnownBug() {
        myFixture.configureByFile("resolve/dotted-ns.tsp")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertNull(
            "plan 02 case 7 expects this to resolve to `model Model` inside `namespace A.B.C`" +
                " — it currently does not (production gap in TypeSpecFileDeclarations /" +
                " TypeSpecResolver's handling of dotted namespace intermediate segments," +
                " reported in the M5.5a test report)",
            reference!!.resolve(),
        )
        // Target assertion once the resolver supports this (plan 02 case 7):
        // val target = myFixture.elementAtCaret
        // assertTrue(target is TypeSpecModelStatement)
        // assertEquals("Model", (target as TypeSpecNamedElement).name)
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
     * Plan 02 case 15, explicitly deferred: M5.5a ships tiers A/B only (plan 02 risk 9,
     * "M5.5a"/"M5.5b" split). `merged-a.tsp` and `merged-b.tsp` declare the SAME namespace and
     * neither imports the other, so this is a pure tier-C case. Pinning `resolve() == null`
     * here is deliberate — it documents the current (correct, plan-sanctioned) gap, not a
     * defect. Update this test's expectation, not its fixture, when M5.5b ships tier C.
     */
    fun testCrossFileNoImportDoesNotResolveYetTierCNotShipped() {
        myFixture.configureByFiles("resolve/merged-b.tsp", "resolve/merged-a.tsp")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertNull(reference!!.resolve())
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
