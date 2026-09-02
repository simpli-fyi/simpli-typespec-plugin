package simpli.fyi.plugins.typespec.parser

import com.intellij.testFramework.ParsingTestCase

/**
 * M5b acceptance (plan 03, ADR 0006 D8/D9): golden parse trees for the real Grammar-Kit
 * grammar. JUnit3 naming (`fun testXxx()`, no `@Test`) — [ParsingTestCase] derives the
 * fixture name from the test method name via `getTestName(false)`.
 *
 * `doTest(true, true)` asserts both that the tree matches the `.txt` golden *and* that there
 * are no `PsiErrorElement`s — used for every well-formed fixture. The two recovery fixtures use
 * `doTest(true)` (golden match only) so the golden locks in the recovery *shape* without
 * asserting the file is error-free (ADR 0006 D6).
 *
 * `ensureCorrectReparse` (part of `ParsingTestCase`'s sanity checks) is never suppressed —
 * `isCheckNoPsiEventsOnReparse()` is not overridden anywhere in this repo (ADR 0006 D9).
 */
class TypeSpecParsingTest : ParsingTestCase("parser", "tsp", TypeSpecParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun includeRanges(): Boolean = true

    fun testImports() = doTest(true, true)

    fun testUsings() = doTest(true, true)

    fun testNamespaceBlock() = doTest(true, true)

    fun testNamespaceBlockless() = doTest(true, true)

    fun testNamespaceDotted() = doTest(true, true)

    fun testModelSimple() = doTest(true, true)

    fun testModelExtends() = doTest(true, true)

    fun testModelIs() = doTest(true, true)

    fun testModelSpread() = doTest(true, true)

    fun testModelOptionalProperty() = doTest(true, true)

    fun testModelTemplateParams() = doTest(true, true)

    fun testBacktickIdentifier() = doTest(true, true)

    /** A property missing its type (`broken;`), followed by a good one — ADR 0006 D6. */
    fun testBrokenProperty() = doTest(true)

    /** Garbage between two good `model`s — ADR 0006 D6. */
    fun testBrokenStatement() = doTest(true)
}
