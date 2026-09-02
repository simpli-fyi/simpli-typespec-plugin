# Plan 04 — Grammar corrections from the ph-cdm corpus audit (M6a → M6f), then M5.5

> **Status: proposed, 2026-09-02.** Written after auditing the tree at `9183c50` against
> the real production TypeSpec repository at
> `/Users/KHODIAKOVA/IdeaProjects/puenktlichhansa/ph-cdm` (read-only). Governed by
> [ADR 0007](../adr/0007-corpus-driven-grammar-acceptance.md); amends
> [plan 03](03-grammar-and-psi.md) §M5d, which is **superseded** by M6a–M6f below.
> [Plan 02](02-navigation.md) (M5.5, navigation) is **unchanged** but re-gated — see the
> last section.

Package root `simpli.fyi.plugins.typespec`. Kotlin, JDK 21, IntelliJ IDEA Community
2025.2.6.3. Two `<depends>` — `com.intellij.modules.platform`,
`com.intellij.modules.spellchecker` — and **no third** ([ADR 0007 D7](../adr/0007-corpus-driven-grammar-acceptance.md)).
Nothing in this plan touches `plugin.xml`'s `<depends>`.

---

## The measurement this plan is built on

Every `.tsp` file under `ph-cdm` (23 first-party + 83 `@typespec/*` stdlib files under
`node_modules`, 106 total) was parsed with the shipped `TypeSpecParserDefinition`, and its
`PsiErrorElement`s collected. Result: **73 of 106 files have parse errors**, including 13
of the 23 first-party files — every first-party file that declares anything.

Construct frequency, both halves of the corpus, ordered by how much damage each does:

| # | Construct | Example | ph-cdm hits / files | stdlib hits / files | Status |
|---|---|---|---|---|---|
| 1 | Decorator on a model member | `@key @field(1) id: ReservationId;` | 102 / 9 | 264 / 42 | **FAIL** |
| 2 | Brace model-expression as decorator argument | `@@package(A.B, { name: "x" })` | 13 / 11 | 45 / 21 | **FAIL** (reported) |
| 3 | `model X is Y;` with no body | `model FlightDseEvent is CdmEvent<Flight>;` | 7 / 7 | 16 / 4 | **FAIL** |
| 4 | Triple-quoted multi-line string | `@doc("""…""")` | 10 / 3 | 0 / 0 | **FAIL** |
| 5 | Comma-separated / unterminated model member | `model M { a: string, b: string }` | 2 / 1 | 50 / 13 | **FAIL** |
| 6 | Spread with template arguments | `...Record<unknown>;` | 1 / 1 | 11 / 4 | **FAIL** |
| 7 | Spread parameter in an operation signature | `op foo(...Input): Output;` | 0 / 0 | 37 / 29 | **FAIL** |
| 8 | `extern dec` / `extern model` / `extern fn` | `extern dec useAuth(target: Namespace, …);` | 0 / 0 | 119 / 10 | **FAIL** |
| 9 | `valueof` in a type/constraint position | `visibility: valueof EnumMember` | 0 / 0 | 96 / 12 | **FAIL** |
| 10 | `scalar` with a body | `scalar plainDate { init fromISO(…); }` | 0 / 0 | 5 / 1 | **FAIL** |
| 11 | Statement-level directive | `#suppress "deprecated" "x"` | 0 / 0 | 6 / 2 | **FAIL** |
| 12 | `typeof` | `typeof Foo` | 0 / 0 | 3 / 2 | **FAIL** |
| 13 | `interface I is Stream<T>` | | 0 / 0 | 1 / 1 | **FAIL** |
| — | `const`, projections, `fn`, `=>` function types | | 0 / 0 | 0 / 0 | absent from corpus — **deprioritised** |

Two findings that are not in the table:

- **`op foo is Bar;` and `interface I extends J`** fail and have zero corpus hits. Real
  TypeSpec constructs; low priority, folded into M6d.
- **Unknown constructs are swallowed silently.** `const x = 5;` has no rule anywhere in
  `TypeSpec.bnf`, yet parses with **zero** `PsiErrorElement` — ADR 0006 D6's
  `bad_*_token_` fallback alternatives consume it one token at a time and succeed. This is
  why every acceptance criterion below asserts *two* properties, not one
  ([ADR 0007 D2](../adr/0007-corpus-driven-grammar-acceptance.md)).

**Sequencing principle:** row 2 first (it is the owner's reported defect, and it is a
correctness bug in a *shipped* feature), then strictly by corpus damage. Rows 8–13 are
stdlib-only: they matter because *Go to declaration* (M5.5) lands the user inside
`node_modules/@typespec/**`, and a red-squiggled destination is a bad landing.

---

### M6a — Corpus harness + decorator-argument expressions
**Goal:** the reported defect is fixed, and the project gains the independent oracle whose
absence let it ship ([ADR 0007 D1–D3](../adr/0007-corpus-driven-grammar-acceptance.md)).

**Files:**
- create `src/test/testData/corpus/` (contents per ADR 0007 D4 — **blocked on OQ1**, see
  "Owner decisions" below) with `PROVENANCE.md` and the upstream MIT `LICENSE`
- create `src/test/kotlin/simpli/fyi/plugins/typespec/parser/TypeSpecCorpusTest.kt`
- create `src/test/testData/corpus/BASELINE.txt` — the ratchet (see below)
- modify `src/main/grammars/TypeSpec.bnf` — `value_expression`
- modify `src/test/testData/parser/Decorators.txt`, `AugmentDecorator.txt` (goldens shift)
- create `src/test/testData/parser/DecoratorModelExpressionArg.tsp` / `.txt`

**Approach:**
- Grammar, per [ADR 0007 D6](../adr/0007-corpus-driven-grammar-acceptance.md). Replace
  ```
  value_expression ::= (STRING (VALID_ESCAPE | INVALID_ESCAPE | STRING)*) | NUMBER | 'true' | 'false'
                     | qualified_name | object_literal | array_literal
  ```
  with
  ```
  value_expression ::= object_literal | array_literal | type_expression_
  ```
  `type_expression_` already reaches `literal_type` (STRING + escape run, NUMBER, `true`,
  `false`), `type_reference_` (`qualified_name` + optional template args),
  `model_expression` (`{ … }` — the missing case), `tuple_expression`, `paren_type_expression`
  and `intrinsic_type`. Ordering matters: `object_literal` / `array_literal` (`#{`, `#[`)
  must stay **first**; they are distinct tokens so there is no real ambiguity, but ordered
  choice makes that explicit.
  This single change fixes **both** `@dec({…})` and `@@dec(…, {…})` — the audit confirmed
  they share `value_expression`, so they share the bug and share the fix.
- Harness: `BasePlatformTestCase`; walk `src/test/testData/corpus/**.tsp`;
  `PsiFileFactory.getInstance(project).createFileFromText(name, TypeSpecFileType.INSTANCE, text)`;
  assert per ADR 0007 D2 — (i) no `PsiErrorElement`, (ii) no *unclaimed leaf token*: every
  leaf whose type is not whitespace/comment has a composite ancestor below the file node.
  Do **not** make the `bad_*_token_` rules public to implement (ii) — walk the tree.
- **Ratchet:** the corpus does not go green in one milestone. `BASELINE.txt` lists corpus
  paths still permitted to fail. The test asserts *exactly* that set fails — a file that
  starts failing **and** a file that stops failing without being removed from the baseline
  both fail the test. This makes each later milestone's acceptance a mechanical baseline
  shrink and prevents silent regressions.
- The corpus directory being empty is a **failure**, never a skip.

**Acceptance (`tsp-tester`):**
- `TypeSpecCorpusTest` exists, reads the vendored corpus, and both assertions are live.
- `BASELINE.txt` is checked in with the post-M6a failing set; every file listed in it has a
  one-line reason naming a row number from the table above.
- New fixture `DecoratorModelExpressionArg.tsp` covers `@service({ title: "x" })`,
  `@@package(A.B, { name: "x" });`, `@dec(#{a: 1}, [T, U], "s", 3, true, Foo.Bar)` and
  passes `doTest(true, true)`.
- `Decorators.txt` / `AugmentDecorator.txt` re-reviewed by hand — a `value_expression` for
  a plain string/number/reference must now contain the inlined `literal_type` /
  `qualified_name` shape. **Blind `-Didea.tests.overwrite.data=true` rebaselining is
  forbidden**; `tsp-tester` reads the new goldens and confirms each changed line.
- All 140 existing tests still pass.

**Done when:** `./gradlew clean build test verifyPlugin` is green **and** the ph-cdm-derived
half of `BASELINE.txt` no longer lists any file whose only failure was row 2.

**Risks / open questions:**
- **OQ1 (owner, blocking)** — vendoring the `ph-cdm` files. See "Owner decisions".
- `value_expression` is referenced by `model_property`'s default, `object_literal_member`,
  `enum_member` and both decorator rules. Widening it widens all five; that is intended
  (upstream has one `Expression`), but the M5b/M5c goldens for `Enum`, `KitchenSinkCore`
  and `OptionalPropertyComplexType` may also shift. Same hand-review rule applies.
- `model_expression` reuses `model_member_`, whose recovery fallback eats almost anything.
  Inside a decorator argument list that fallback can now swallow a stray `)`. If a
  well-formed file starts parsing *worse*, tighten `bad_model_member_guard_` to
  `!('}' | ')' | ',')` rather than reverting the unification.

---

### M6b — Model member surface (rows 1, 5, 6)
**Goal:** every model body in the corpus parses. This is the largest single win in the plan
(row 1 alone is 366 occurrences across 51 files).

**Files:** `src/main/grammars/TypeSpec.bnf`; new fixtures
`src/test/testData/parser/ModelDecoratedProperty.tsp`/`.txt`,
`ModelCommaSeparators.tsp`/`.txt`, `ModelSpreadTemplate.tsp`/`.txt`;
`src/test/testData/corpus/BASELINE.txt`.

**Approach:**
- `model_property ::= decorator_application* identifier '?'? ':' type_expression_ ('=' value_expression)? member_separator_?`
  — add the decorator prefix (row 1). `pin` must move from `3` to `4`? **No**: `pin` is
  1-based over the *sequence*, and `decorator_application*` is one element, so the `':'`
  moves from position 3 to position 4. `tsp-dev` must set `pin=4` and verify the two
  recovery goldens (`BrokenProperty.txt`) still hold — a wrong pin here silently degrades
  all error recovery.
- `private member_separator_ ::= ';' | ','` and make it **optional**, so `model M { a: string }`
  (no trailing separator — the dominant form inside inline `model_expression`s) parses
  (row 5).
- `model_spread ::= '...' type_expression_ member_separator_?` (row 6) — `Record<unknown>`
  needs template arguments, which `type_expression_` supplies. Keep the `'...'` pin.
- Add `DECORATOR` to `bad_model_member_token_`'s token list. It is currently absent, which
  is why a single decorated property poisons the whole model body instead of being
  recovered past.
- **PSI contract regression check:** `TypeSpecPsiUtil.findNameIdentifier` takes the *first*
  direct `TypeSpecIdentifier` child. `decorator_application` wraps a single `DECORATOR`
  token and has no `identifier` child, so a decorated property's name is still found — but
  `tsp-dev` must confirm this against the generated PSI, not assume it. If
  `decorator_argument_list`'s contents ever became direct children this silently breaks.

**Acceptance (`tsp-tester`):**
- Three new golden fixtures, `doTest(true, true)`.
- `TypeSpecPsiContractTest` gains a case: a decorated property's `getName()` /
  `getNameIdentifier()` / `getTextOffset()` still point at the property name, not at the
  decorator.
- Existing `BrokenProperty.txt` / `BrokenStatement.txt` recovery goldens re-reviewed by
  hand after the pin change.
- `BASELINE.txt` shrinks by at least the 9 first-party + 42 stdlib files whose only
  failures were rows 1/5/6.

**Done when:** `./gradlew clean build test verifyPlugin` green, and `BASELINE.txt` contains
**zero** `corpus/real/**` entries attributable to rows 1, 5 or 6.

**Risks / open questions:**
- Making the member separator optional makes `model M { a: string b: string }` legal. That
  is a real loosening. Accept it: an over-permissive grammar produces a wrong tree,
  an under-permissive one produces red squiggles on valid code, and only the second is
  visible to the user. Record the loosening as a comment in the `.bnf`.

---

### M6c — Model heritage without a body, and multi-line strings (rows 3, 4)
**Goal:** the last two constructs blocking the owner's own repository.

**Files:** `src/main/grammars/TypeSpec.bnf`;
`src/test/testData/parser/ModelIsNoBody.tsp`/`.txt`, `MultilineString.tsp`/`.txt`;
`BASELINE.txt`.

**Approach:**
- `model_statement ::= decorator_application* 'model' identifier template_parameter_list? ( is_clause ';' | extends_clause? is_clause? model_body )`
  — the `is`-without-body form is a *distinct alternative*, not an optional `model_body`.
  Making the body optional would also legalise `model M extends Foo;`, which the audit
  found zero occurrences of and believes is invalid (ADR 0007 OQ2). Keep them separate so
  the decision stays reversible in one line.
- Multi-line strings: **the lexer is already correct** — `_TypeSpecLexer.flex` lines 53–54
  emit `MULTILINE_STRING`. The grammar simply never references that token. Declare
  `MULTILINE_STRING` in the `.bnf` `tokens=[…]` block (bare-referencable, same as
  `DECORATOR`) and add it as an alternative of `literal_type`. Confirm whether the
  `MULTILINE_STRING_S` lexer state can emit *several* `MULTILINE_STRING` tokens for one
  source literal the way `STRING_S` does for escapes; if so, `literal_type` needs a
  `MULTILINE_STRING+` run, not a single token. **`tsp-dev` must check the lexer test's
  actual token stream, not the flex source's intent.**

**Acceptance (`tsp-tester`):**
- `ModelIsNoBody.tsp` covers `model A is B;`, `model C is D<E>;`, and a decorated one.
- A negative fixture asserting `model M extends Foo;` **still** produces a `PsiErrorElement`
  (locks OQ2's current answer in place so a future change is deliberate).
- `MultilineString.tsp` uses the exact shape from `ph-cdm/model/reservation/reservation.tsp`
  lines 57+ — a `@doc("""…""")` spanning four lines inside a model body.
- A lexer-level assertion in `TypeSpecLexerTest` pinning how many tokens one `"""…"""`
  literal yields.

**Done when:** `./gradlew clean build test verifyPlugin` green **and** `BASELINE.txt`
contains **zero** `corpus/real/**` entries at all. *This is the milestone at which the
owner's repository is clean in the IDE* — the visible goal of the whole plan.

**Risks / open questions:** the `is`-alternative interacts with `pin=2`; a mis-set pin
turns `model` + identifier into an unrecoverable commit point.

---

### M6d — Operation and interface surface (rows 7, 13, + `op is`, `interface extends`)
**Goal:** operation signatures in the stdlib parse.

**Files:** `src/main/grammars/TypeSpec.bnf`; `OperationSpreadParam.tsp`/`.txt`,
`InterfaceHeritage.tsp`/`.txt`, `OpIs.tsp`/`.txt`; `BASELINE.txt`.

**Approach:**
- `operation_parameter ::= decorator_application* ('...')? identifier '?'? ':' type_expression_ ('=' value_expression)?`
  — the spread form and the default value. Note the pin must move again.
- `op_statement` gains an `is` alternative: `… identifier template_parameter_list? ( is_clause ';' | operation_parameter_list ':' type_expression_ ';' )`.
- `interface_statement` gains `extends_clause?` (comma-separated list of references, per
  upstream) and an `is` form.
- `interface_operation` mirrors `op_statement`'s parameter changes.

**Acceptance (`tsp-tester`):** three fixtures at `doTest(true, true)`; `BASELINE.txt`
shrinks by the 29 stdlib files whose only failure was row 7.

**Done when:** `./gradlew clean build test verifyPlugin` green and the baseline shrinks as
stated.

**Risks / open questions:** `interface I extends A, B` — is the heritage list
comma-separated, and may it carry template arguments? Zero corpus occurrences, so this is
**unverified**; `tsp-dev` should implement the comma-separated form and mark it in the
`.bnf` as unverified-against-corpus.

---

### M6e — Library-authoring surface (rows 8–12)
**Goal:** `node_modules/@typespec/**` parses clean, so M5.5's *Go to declaration* lands
somewhere that is not red.

**Files:** `src/main/grammars/TypeSpec.bnf`; fixtures `ExternDeclarations.tsp`/`.txt`,
`ValueOf.tsp`/`.txt`, `ScalarBody.tsp`/`.txt`, `Directives.tsp`/`.txt`; `BASELINE.txt`.

**Approach:**
- `extern_declaration ::= 'extern' ( dec_statement | fn_statement | model_statement )` as a
  new `top_level_item_` alternative, plus `dec_statement ::= 'dec' identifier operation_parameter_list ';'`
  and `fn_statement`. `dec`/`fn`/`extern` — check whether the lexer keywordizes them; if not,
  they arrive as `IDENTIFIER` and the literals in the `.bnf` will not match. **Verify in
  `_TypeSpecLexer.flex` before writing a rule.**
- `valueof` / `typeof` as prefix operators in `primary_type_expression`. Same lexer check.
- `scalar_statement` gains an optional body (`'{' scalar_member_* '}'`) with `init`
  constructors.
- Statement-level directives: one `DIRECTIVE` token plus a run of trailing string literals,
  attachable as a prefix on `top_level_item_` and on model/enum/union members. Plan 03
  already identified placement as the hard part; it still is.
- Rest parameters `...visibilities: valueof EnumMember[]` — covered by M6d's spread plus
  `valueof` here.

**Acceptance (`tsp-tester`):** four fixtures at `doTest(true, true)`; `BASELINE.txt` reduced
to at most a handful of entries, each with a written reason.

**Done when:** `./gradlew clean build test verifyPlugin` green.

**Risks / open questions:** directive placement is genuinely invasive. If it stalls, split
it out rather than blocking rows 8–10.

---

### M6f — Zero-baseline gate
**Goal:** delete `BASELINE.txt` and make the corpus test an absolute assertion.

**Files:** delete `src/test/testData/corpus/BASELINE.txt`; modify
`TypeSpecCorpusTest.kt`; modify `docs/plans/03-grammar-and-psi.md` (mark §M5d superseded);
add the valid-TypeSpec kitchen-sink variant per ADR 0007 §Consequences.

**Approach:** remove the ratchet; the test now asserts *every* corpus file satisfies both
ADR 0007 D2 properties. Add the corpus directory to the "what a grammar change must not
break" note in `docs/plans/00-milestones.md`.

**Acceptance (`tsp-tester`):** `TypeSpecCorpusTest` has no allowlist; deliberately breaking
one `.bnf` rule makes it fail (verify this by hand once, then revert).

**Done when:** `./gradlew clean build test verifyPlugin` green with no baseline file in the
tree.

**Risks / open questions:** none beyond the preceding milestones landing.

---

## Then, and only then: M5.5 — navigation

[Plan 02](02-navigation.md) is unchanged and still correct. Two corrections to its status:

1. **Navigation was never implemented and was never claimed to be.** `plugin.xml` registers
   no `psi.referenceContributor` and no `gotoDeclarationHandler`;
   `grep -rl "PsiReference\|GotoDeclaration\|ResolveResult" src/main/kotlin` is empty. Plan
   02 has always described this as M5.5, gated on M5c. The gap is between the owner's
   expectation and the milestone sequence, not between the plan and the tree.

2. **The `PsiNamedElement` groundwork M5b/M5c owed it is genuinely present and correct.**
   `TypeSpecNamedElement : PsiNameIdentifierOwner`,
   `TypeSpecNamedElementMixin` (`getName` with backtick stripping, `getNameIdentifier`,
   `getTextOffset` at the name, `setName` throwing, `getPresentation`),
   `TypeSpecPsiUtil.findNameIdentifier`, and the four `TypeSpecFile` accessors all exist and
   are covered by `TypeSpecPsiContractTest` across all 11 named rules
   (`namespace`, `model`, model property, template parameter, `op`, `interface`, `enum` +
   member, `union` + variant, `alias`, `scalar`). ADR 0004 D7.2's contract is satisfied.

**Re-gating.** Plan 02's prerequisite becomes **M6c green**, not M5c green. Rationale: a
resolver built and tested against a grammar that mis-parses every decorated model property
would be tuned to the wrong tree shape, and M6b changes `model_property`'s child list —
exactly the node the resolver's declaration index reads.

**One correction to plan 02 to make before it starts.** `TypeSpecFile.getTopLevelDeclarations()`
returns *direct* `TypeSpecNamedElement` children of the file. Every file in the ph-cdm corpus
uses a **blockless** `namespace Foo.Bar;`, under which — by the `namespace_statement` rule's
own containment design — all subsequent declarations are children of the *namespace*, not of
the file. So this accessor returns exactly one element for a real-world file. That is
correct per ADR 0004 D7.4 and is *not* a bug, but plan 02's resolver must recurse through
namespaces rather than treating the accessor as "all declarations in this file". Verified
against the corpus, flagged here so `tsp-dev` does not discover it as a resolve-returns-null
mystery.

---

## Owner decisions required

| # | Decision | Why it cannot be made by an agent |
|---|---|---|
| OQ1 | **May `ph-cdm` `.tsp` files be vendored into this repo?** (verbatim / domain-anonymised rewrite / not at all). Recommendation: anonymised rewrite — keeps all test value, publishes no domain model. | The files are the owner's production CDM; this plugin repo may be published. **Blocks M6a.** |
| OQ2 | Is `model M extends Foo;` (heritage, no body) valid TypeSpec? Zero corpus occurrences; M6c deliberately keeps rejecting it. | Unverified against `microsoft/typespec` primary sources. Cheap to reverse. |
| OQ3 | Should `interface Store { values: #[1,2,3]; }` in `src/test/testData/lexer/kitchen-sink.tsp` be corrected to valid TypeSpec (plan 03 §M5d's standing open question)? ADR 0007 recommends option (ii) — a separate valid-TypeSpec variant, leaving M2's lexer golden untouched. | Touches an M2 golden. Was already flagged for the owner in plan 03 and is still unanswered. |
| OQ4 | Ship an interim 0.0.2 with M6a–M6c only (owner's repo clean, stdlib still squiggly), or hold until M6f? | Release/marketplace call. |
