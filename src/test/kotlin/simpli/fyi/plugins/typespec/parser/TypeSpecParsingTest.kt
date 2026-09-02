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

    fun testOperation() = doTest(true, true)

    fun testInterface() = doTest(true, true)

    fun testEnum() = doTest(true, true)

    fun testUnion() = doTest(true, true)

    fun testAlias() = doTest(true, true)

    fun testScalar() = doTest(true, true)

    fun testDecorators() = doTest(true, true)

    fun testAugmentDecorator() = doTest(true, true)

    fun testTypeUnion() = doTest(true, true)

    fun testTypeIntersection() = doTest(true, true)

    fun testTypeArray() = doTest(true, true)

    fun testTypeTemplateArgs() = doTest(true, true)

    /** Regression guard for the keywordized-intrinsics landmine (plan 03 M5c Risks). */
    fun testTypeIntrinsics() = doTest(true, true)

    /**
     * Replaces revision 2's withdrawn `TypeOptional` fixture (there is no `T?` type suffix in
     * TypeSpec): asserts M5b's optional-property behaviour still holds once the property type
     * is a full type expression.
     */
    fun testOptionalPropertyComplexType() = doTest(true, true)

    /** M5c's real done-signal (plan 03) — the M5c-achievable subset of kitchen-sink.tsp. */
    fun testKitchenSinkCore() = doTest(true, true)

    /** A property missing its type (`broken;`), followed by a good one — ADR 0006 D6. */
    fun testBrokenProperty() = doTest(true)

    /** Garbage between two good `model`s — ADR 0006 D6. */
    fun testBrokenStatement() = doTest(true)
}
