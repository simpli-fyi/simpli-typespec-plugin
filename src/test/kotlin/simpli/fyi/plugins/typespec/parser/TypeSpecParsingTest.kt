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

    // M6c (plan 04, ADR 0007 D9): `is` admits either `;` or a body; `extends` still requires
    // one. Covers all four corpus-observed shapes: bare `is B;`, templated `is D<E>;`, a
    // decorated declaration, and `is G<H> { ... }` (the `@typespec/http` streams shape).
    fun testModelIsNoBody() = doTest(true, true)

    // Negative fixture locking ADR 0007 D9 in place: `extends` without a body, and
    // `extends` + `is` together, both still produce a PsiErrorElement -- this is the exact
    // combination `ContractFixture.tsp` (src/test/testData/psi) used to rely on the old,
    // over-permissive grammar accepting.
    fun testModelExtendsNoBodyIsError() = doTest(true)

    // M6c row 4: the lexer already emits a single MULTILINE_STRING token per `"""..."""`
    // literal (TypeSpecLexerTest#testTripleQuotedString pins this); this fixture is the
    // parser-side golden -- a decorator argument, a decorated model-property default, and
    // an alias value, matching the shape in corpus/real/booking/booking.tsp.
    fun testMultilineString() = doTest(true, true)

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

    /**
     * Regression for `9aba27f` (plan 04 M6a, ADR 0007 D6): the owner's literal repro
     * (`@@package(A.B, { name: "x" })`), the same defect on a plain decorator
     * (`@dec({title: "x"})`), and a decorated model property (`@key @field(1) id: string;`).
     * All three previously failed to parse.
     */
    fun testDecoratorModelExpressionArg() = doTest(true, true)

    /**
     * Regression for `9aba27f` (plan 04 M6b row 5 pulled forward early, ADR 0007's
     * primary-source table): a single-member `model_expression` with no trailing separator
     * (`{ name: "x" }`, the dominant inline-object shape) and the comma-separated form
     * (`{ a: string, b: string }`) both parse — `,` is a valid model-body member
     * separator upstream, not merely tolerated.
     */
    fun testModelExpressionSeparators() = doTest(true, true)

    // ------------------------------------------------------------------
    // M6f Job 2 (plan 04 §M6f) -- fixtures for constructs landed in M6d/M6e/M6e' that
    // those milestones' dev runs deliberately left for tsp-tester to pin (dev runs may
    // not touch src/test/). Goldens were dumped, hand-reviewed against the actual
    // TypeSpec.bnf rule shapes (operation_spread_parameter_, op_statement's is_clause
    // alternative, interface_heritage_, interface_operation's optional 'op', dec/fn
    // modifier_* + statements, valueof_expression/typeof_expression, scalar_body,
    // directive_statement, type_reference_'s call_argument_list alternative,
    // trailing_comma_), then written -- never via -Didea.tests.overwrite.data=true.
    // ------------------------------------------------------------------

    /** M6d row 7: a plain spread parameter (`op foo(...Input): Output;`) and a decorated one
     *  mixed with a named parameter (`op bar(@doc("d") ...Base, id: string): Widget;`) --
     *  `operation_spread_parameter_`'s `decorator_application*` prefix. */
    fun testOperationSpread() = doTest(true, true)

    /** M6d: `op X is Y;` (bare) and `op X is Y<T>;` (templated) -- `op_statement`'s
     *  `is_clause` alternative, ADR 0007 primary-source facts table's low-priority tail. */
    fun testOpIs() = doTest(true, true)

    /** M6d: `interface X extends A, B { ... }` -- `interface_heritage_`, a comma-separated
     *  list of bare `type_reference_`s (not full type expressions). */
    fun testInterfaceExtends() = doTest(true, true)

    /** M6d: the optional `op` keyword prefix on an interface member
     *  (`interface I { op foo(): void; }`) -- `ListKind.InterfaceMembers`'s
     *  `allowedStatementKeyword`, zero corpus occurrences but grammatical upstream. */
    fun testInterfaceOpPrefix() = doTest(true, true)

    /** Negative fixture pinning ADR 0007 D9's corollary: `interface I is Stream<T>;` is
     *  still rejected -- `parseInterfaceStatement` (and this grammar's `interface_statement`)
     *  has no `is` branch. An earlier survey row wrongly claimed this form was accepted;
     *  it is not, and must never become one by accident. */
    fun testInterfaceIsRejected() = doTest(true)

    /** M6e row 8: `extern dec`/`extern fn`/`extern model`, including the `internal extern fn`
     *  modifier combination (`modifier_*`'s repeatable loop, verified against the corpus's
     *  actual `internal extern dec/fn` occurrences, not just the plan's literal 'extern'-only
     *  wording). */
    fun testExternDeclarations() = doTest(true, true)

    /** M6e rows 9/12: `valueof` in a model-property type, in a template-parameter `extends`
     *  bound (`T extends valueof string`), and `typeof` in a model-property type. */
    fun testValueofTypeof() = doTest(true, true)

    /** M6e row 10: `scalar plainDate { init fromISO(value: string); init now(); }` --
     *  `scalar_body`/`scalar_member`, reusing `dec_fn_parameter_list`. */
    fun testScalarWithBody() = doTest(true, true)

    /** M6e row 11: statement-level `#suppress "code" "message"` / `#deprecated "message"`
     *  directives, each immediately preceding an `extern dec` declaration -- the corpus's
     *  actual placement (never at model/enum/union member level). */
    fun testStatementDirectives() = doTest(true, true)

    /** M6e' gap 1: a call expression in type position (`alias X<T> = someFn(T, #{a: 1});`)
     *  -- `type_reference_`'s `call_argument_list` alternative; the `#{...}` argument is
     *  covered for free via `value_expression`. */
    fun testCallExpressionInTypePosition() = doTest(true, true)

    /** M6e' gap 2, the ACCEPTED bound: exactly one trailing comma after a real item, in
     *  every list kind that carries `trailing_comma_?` -- decorator arguments, a tuple
     *  type, template arguments, and call arguments. */
    fun testTrailingSeparators() = doTest(true, true)

    /** M6e' gap 2, the REJECTED bound: a doubled separator (`Page<Widget,,>`, no second item
     *  between the commas) and an empty leading element (`Page<,Widget>`, no item before the
     *  first comma) both still produce a `PsiErrorElement` at the comma, rather than being
     *  silently swallowed -- `trailing_comma_?` accepts at most one comma directly after a
     *  real item, never a bare comma standing in for one. */
    fun testTrailingSeparatorsRejected() = doTest(true)

    /**
     * Regression for `4c4c191`: a member-level `#deprecated "..."` directive inside a `model`
     * body parses clean, zero `PsiErrorElement`. Upstream's `parseAnnotations()` collects
     * directives and decorators together, in any order, as a shared prefix of the item they
     * annotate -- so the fixed grammar's `member_annotation_ ::= decorator_application |
     * directive_statement` is now the leading prefix of `model_property` itself, and
     * `DIRECTIVE_STATEMENT` is the property's *first child* (sibling of `@key`), not a sibling
     * of `MODEL_PROPERTY` in `MODEL_BODY`. Golden hand-reviewed 2026-09-03: dumped via
     * `toParseTreeText`, confirmed zero `PsiErrorElement` and zero unclaimed leaves, and that the
     * directive/decorators/identifier/colon/type/semicolon all nest correctly under the single
     * `MODEL_PROPERTY` for `id`, matching upstream's "annotations attach to the item" model.
     */
    fun testMemberDirectiveInModel() = doTest(true, true)

    /**
     * Regression for `4c4c191`, follow-on to [testMemberDirectiveInModel]: this fixture used to
     * pin a **swallow bug** -- `directive_argument_`'s bare-`identifier` alternative had no guard,
     * so `#deprecated "..."` directly followed by an *undecorated* property (no decorator to
     * absorb the ambiguity) greedily ate that property's leading identifier as one more
     * `directive_argument_`, corrupting `id: string;` into bare unclaimed leaves under
     * `MODEL_BODY`. The fix added a `!(':' | '?')` guard to that alternative, rejecting an
     * identifier that actually starts the next member. Renamed (it no longer swallows anything):
     * `id: string;` now parses as a proper `MODEL_PROPERTY`, with `DIRECTIVE_STATEMENT` as its
     * first child, same shape as [testMemberDirectiveInModel]'s `id` property minus the decorator.
     * Golden hand-reviewed 2026-09-03 the same way: zero `PsiErrorElement`, zero unclaimed leaves,
     * `id`'s colon/type/semicolon all correctly nested under `MODEL_PROPERTY`.
     */
    fun testMemberDirectivePropertyAfterDirective() = doTest(true, true)
}
