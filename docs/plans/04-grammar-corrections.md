# Plan 04 — Grammar corrections from the ph-cdm corpus audit

**Sequence: M6a → M6b → M6c → M5.5 (navigation) → M6d → M6e → M6f.**

> **Status: ratified 2026-09-02.** Written after auditing the tree at `9183c50` against the
> real production TypeSpec repository at
> `/Users/KHODIAKOVA/IdeaProjects/puenktlichhansa/ph-cdm` (read-only). Governed by
> [ADR 0007](../adr/0007-corpus-driven-grammar-acceptance.md); supersedes
> [plan 03](03-grammar-and-psi.md) §M5d. [Plan 02](02-navigation.md) (M5.5) is unchanged in
> content but **re-gated on M6c** and **re-sequenced ahead of M6d–M6f** per
> [ADR 0007 D11](../adr/0007-corpus-driven-grammar-acceptance.md).
>
> All four of the audit's open questions are **closed**: OQ1 → ADR 0007 D4/D8 (owner),
> OQ2 → D9 (architect, upstream-verified), OQ3 → D10 (architect, upstream-verified),
> OQ4 → D11 (owner). Nothing in this plan is blocked on a decision.

Package root `simpli.fyi.plugins.typespec`. Kotlin, JDK 21, IntelliJ IDEA Community
2025.2.6.3. Two `<depends>` — `com.intellij.modules.platform`,
`com.intellij.modules.spellchecker` — and **no third**
([ADR 0007 D7](../adr/0007-corpus-driven-grammar-acceptance.md)). Nothing in this plan
touches `plugin.xml`'s `<depends>`.

---

## The measurement this plan is built on

Every `.tsp` file under `ph-cdm` (23 first-party + 83 `@typespec/*` stdlib files under
`node_modules`, 106 total) was parsed with the shipped `TypeSpecParserDefinition` and its
`PsiErrorElement`s collected. **73 of 106 files have parse errors**, including 13 of the 23
first-party files — every first-party file that declares anything. The clean 10 are
one-line `import`/`using` aggregators.

| # | Construct | Example | ph-cdm hits / files | stdlib hits / files | Lands in |
|---|---|---|---|---|---|
| 1 | Decorator on a model member | `@key @field(1) id: ReservationId;` | 102 / 9 | 264 / 42 | **M6b** |
| 2 | Brace model-expression as decorator argument | `@@package(A.B, { name: "x" })` | 13 / 11 | 45 / 21 | **M6a** (reported defect) |
| 3 | `model X is Y;` / `model X is Y { … }` | `model FlightDseEvent is CdmEvent<Flight>;` | 7 / 7 | 16 / 4 | **M6c** |
| 4 | Triple-quoted multi-line string | `@doc("""…""")` | 10 / 3 | 0 / 0 | **M6c** |
| 5 | Comma-separated / unterminated model member | `model M { a: string, b: string }` | 2 / 1 | 50 / 13 | **M6b** |
| 6 | Spread with template arguments | `...Record<unknown>;` | 1 / 1 | 11 / 4 | **M6b** |
| 7 | Spread parameter in an operation signature | `op foo(...Input): Output;` | 0 / 0 | 37 / 29 | M6d |
| 8 | `extern dec` / `extern model` / `extern fn` | `extern dec useAuth(target: Namespace, …);` | 0 / 0 | 119 / 10 | M6e |
| 9 | `valueof` in a type/constraint position | `visibility: valueof EnumMember` | 0 / 0 | 96 / 12 | M6e |
| 10 | `scalar` with a body | `scalar plainDate { init fromISO(…); }` | 0 / 0 | 5 / 1 | M6e |
| 11 | Statement-level directive | `#suppress "deprecated" "x"` | 0 / 0 | 6 / 2 | M6e |
| 12 | `typeof` | `typeof ContentType` | 0 / 0 | 3 / 2 | M6e |
| — | ~~`interface I is Stream<T>`~~ | — | — | — | **struck** — no such form upstream (ADR 0007 D9); the sighting was a multi-line `model … is Stream<T> {` |
| — | `op foo is Bar;`, `interface I extends J`, `op`-prefixed interface member | | 0 / 0 | 0 / 0 | M6d, low priority |
| — | `const`, projections, `fn`, `=>` function types | | 0 / 0 | 0 / 0 | absent from corpus — deprioritised |

**Rows 1–6 are the whole of the owner's exposure.** Rows 7–12 are stdlib-only; they matter
because *Go to declaration* lands the user inside `node_modules/@typespec/**`. Per ADR 0007
D11 that is accepted as a known-imperfect landing until M6e.

Two findings not in the table:

- **Unknown constructs are swallowed silently.** `const x = 5;` has no rule anywhere in
  `TypeSpec.bnf`, yet parses with **zero** `PsiErrorElement` — ADR 0006 D6's `bad_*_token_`
  fallback alternatives consume it one token at a time and succeed. Every acceptance
  criterion below therefore asserts *two* properties ([ADR 0007 D2](../adr/0007-corpus-driven-grammar-acceptance.md)).
- **`DECORATOR` is missing from `bad_model_member_token_`'s token list**, which is why one
  decorated property poisons an entire model body instead of being recovered past.

---

### M6a — Corpus harness + decorator-argument expressions
**Goal:** the reported defect is fixed, and the project gains the independent oracle whose
absence let it ship ([ADR 0007 D1–D3](../adr/0007-corpus-driven-grammar-acceptance.md)).

**Files:**
- create `src/test/testData/corpus/stdlib/**` (verbatim), `corpus/stdlib/LICENSE`,
  `corpus/stdlib/PROVENANCE.md`
- create `src/test/testData/corpus/real/**` (anonymised), `corpus/real/PROVENANCE.md`
- create `src/test/testData/corpus/ANONYMISATION.md` — the rename map + ruleset
- create `tools/corpus-sync/anonymise.py` — the re-sync tool (test tooling, not production)
- create `src/test/testData/corpus/BASELINE.txt` — the ratchet
- create `src/test/kotlin/simpli/fyi/plugins/typespec/parser/TypeSpecCorpusTest.kt`
- modify `src/main/grammars/TypeSpec.bnf` — `value_expression` only
- modify `src/test/testData/parser/Decorators.txt`, `AugmentDecorator.txt` (goldens shift)
- create `src/test/testData/parser/DecoratorModelExpressionArg.tsp` / `.txt`

#### Corpus layout

```
src/test/testData/corpus/
├── ANONYMISATION.md          rename map + rules (below); the single source of truth
├── BASELINE.txt              ratchet; deleted at M6f
├── stdlib/                   @typespec/* — VERBATIM, MIT
│   ├── LICENSE               copied from node_modules/@typespec/compiler/LICENSE
│   ├── PROVENANCE.md         package names, exact versions, tsp compiler version, sync date
│   └── <pkg>/<path>.tsp      mirrors the node_modules path, e.g. http/lib/decorators.tsp
└── real/                     ph-cdm — ANONYMISED REWRITE
    ├── PROVENANCE.md         "derived from a private repository via tools/corpus-sync;
    │                          see ANONYMISATION.md. No original names retained."
    └── <path>.tsp            mirrors the ph-cdm path with anonymised path segments
```

#### Anonymisation rules (`ANONYMISATION.md`) — ADR 0007 D4.2

The corpus reproduces *parse failures*, so it must preserve syntax exactly and identity
only structurally.

1. **Preserve, byte for byte:** every keyword, punctuation token, decorator *name*
   (`@doc`, `@field`, `@@package`), built-in scalar (`string`, `int32`, `utcDateTime`),
   stdlib type reference (`Record`, `TypeSpec.Protobuf.Extern`), comment/whitespace layout,
   line structure, and — critically — the **shape** of every construct: number of dotted
   namespace segments, template parameter/argument counts, optionality markers, member
   separators, decorator ordering, string-literal *kind* (`"…"` vs `"""…"""`) and line count.
2. **Rename** only: user-declared namespace segments, model/enum/union/interface/alias/
   scalar/op names, property/member/parameter names, and the *contents* of user-authored
   string literals.
3. **The rename map is injective and category-preserving** (ADR 0007 D4.2). One-to-one:
   `Airlines`→`Acme`, `Ph`→`Ex`, `Cdm`→`Core`, `Reservation`→`Booking`,
   `Flight`→`Journey`, … Never collapse two distinct source names onto one target. Never
   change a name's *category*: an identifier stays an identifier, a backticked name stays
   backticked (` `first-name` ` → ` `first-part` `), a dotted name keeps its segment count,
   a `kebab-case` name stays kebab-case.
4. **String literal contents** are replaced with same-shape filler: same length class, same
   internal structure (a dotted package string `"airlines.ph.cdm.reservation.batch"` →
   `"acme.ex.core.booking.batch"` — still dotted, still lower-case, same segment count), and
   **escape sequences and multi-line-ness preserved exactly** (a `"""` block keeps its line
   count and indentation — that is row 4's entire test value).
5. **Comments** are replaced with `// (comment)` of the same line count, except where a
   comment's *form* is under test (a `/** doc */` stays a doc comment).
6. **File and directory names** are renamed through the same map.
7. **Forbidden:** reformatting, sorting, deduplicating, "tidying", or dropping any
   declaration. If a construct appears five times, it appears five times.

**Self-check, mandatory, and the reason the rules above are enforceable:** run the survey
harness over the *original* `ph-cdm` tree and over `corpus/real/`, and assert the two
produce the **same multiset of `(construct-category, count)` pairs and the same number of
failing files**. An anonymisation that changes the failure profile has destroyed test
value and must be redone. `tools/corpus-sync/anonymise.py` prints this comparison.

#### Re-sync procedure (for a future contributor, no domain leakage)

```
tools/corpus-sync/anonymise.py \
    --source   /path/to/ph-cdm \
    --map      src/test/testData/corpus/ANONYMISATION.md \
    --dest     src/test/testData/corpus/real \
    --verify
```

- The rename map lives **in the repo**, keyed by *target* name with a stable hash of the
  source name — never the source name itself. New source names not in the map cause a
  **hard failure** listing only their hashes, and the contributor adds a mapping by hand.
  This is what stops an unattended re-sync from silently publishing a new domain term.
- `--verify` re-runs the failure-profile self-check and refuses to write on mismatch.
- `git diff` on `corpus/real/` after a re-sync must be reviewed by a human before commit.
- The stdlib half re-syncs by straight copy from `node_modules/@typespec/*/lib/**.tsp`;
  `PROVENANCE.md` versions must be updated in the same commit.

#### Grammar change (the reported defect)

Per [ADR 0007 D6](../adr/0007-corpus-driven-grammar-acceptance.md). Replace

```
value_expression ::= (STRING (VALID_ESCAPE | INVALID_ESCAPE | STRING)*) | NUMBER | 'true' | 'false'
                   | qualified_name | object_literal | array_literal
```

with

```
value_expression ::= object_literal | array_literal | type_expression_
```

`type_expression_` already reaches `literal_type` (STRING + escape run, NUMBER, `true`,
`false`), `type_reference_` (`qualified_name` + optional template args), **`model_expression`
(`{ … }` — the missing case)**, `tuple_expression`, `paren_type_expression` and
`intrinsic_type`. `object_literal` / `array_literal` (`#{`, `#[`) stay **first**: distinct
tokens, so no real ambiguity, but ordered choice makes it explicit. One change fixes both
`@dec({…})` and `@@dec(…, {…})` — the audit confirmed they share `value_expression`.

#### Harness

`BasePlatformTestCase`; walk `src/test/testData/corpus/**.tsp`;
`PsiFileFactory.getInstance(project).createFileFromText(name, TypeSpecFileType.INSTANCE, text)`;
assert per ADR 0007 D2 — (i) no `PsiErrorElement`, (ii) **no unclaimed leaf token**: every
leaf whose type is not whitespace/comment has a composite ancestor below the file node. Do
**not** make the `bad_*_token_` rules public to implement (ii) — walk the tree.

**Ratchet:** `BASELINE.txt` lists corpus paths still permitted to fail, each with a one-line
reason naming a row number from the table. The test asserts *exactly* that set fails — a
newly-failing file **and** a file that stops failing without leaving the baseline both fail
the test. An empty corpus directory is a **failure**, never a skip.

**Acceptance (`tsp-tester`):**
- Corpus vendored per the layout above; `stdlib/LICENSE` and both `PROVENANCE.md` present;
  `ANONYMISATION.md` present and matching what was actually applied.
- Grepping `corpus/real/` for the strings `Airlines`, `Ph`, `Cdm`, `Puenktlich`, `hansa`,
  and every source-side name in the map returns **nothing**. This is an explicit test.
- The failure-profile self-check passes (same construct-category multiset as the original).
- `TypeSpecCorpusTest` exists with both assertions live and the ratchet wired.
- `BASELINE.txt` checked in with the post-M6a failing set.
- `DecoratorModelExpressionArg.tsp` covers `@service({ title: "x" })`,
  `@@package(A.B, { name: "x" });`, `@dec(#{a: 1}, [T, U], "s", 3, true, Foo.Bar)`, and
  passes `doTest(true, true)`.
- `Decorators.txt` / `AugmentDecorator.txt` **hand-reviewed** — a `value_expression` for a
  plain string/number/reference now contains the inlined `literal_type` / `qualified_name`
  shape. **Blind `-Didea.tests.overwrite.data=true` rebaselining is forbidden**;
  `tsp-tester` reads each changed line and confirms it.
- All 140 existing tests still pass.

**Done when:** `./gradlew clean build test verifyPlugin` is green **and** no
`corpus/real/**` entry in `BASELINE.txt` cites row 2.

**Risks / open questions:**
- `value_expression` is referenced by `model_property`'s default, `object_literal_member`,
  `enum_member` and both decorator rules. Widening it widens all five — intended (upstream
  has one `Expression`), but the `Enum`, `KitchenSinkCore` and `OptionalPropertyComplexType`
  goldens may also shift. Same hand-review rule.
- `model_expression` reuses `model_member_`, whose recovery fallback eats almost anything.
  Inside a decorator argument list it can now swallow a stray `)`. If a well-formed file
  starts parsing *worse*, tighten `bad_model_member_guard_` to `!('}' | ')' | ',')` rather
  than reverting the unification.
- The anonymisation is the bulk of this milestone's effort, not the one-line grammar fix.
  If it threatens to stall M6a, land the harness + grammar fix against `corpus/stdlib/`
  only, with `corpus/real/` and its assertions as an immediately-following M6a′ — but
  **M6b must not start before `corpus/real/` exists**, since M6b's done-signal is stated in
  terms of it.

---

### M6b — Model member surface (rows 1, 5, 6)
**Goal:** every model body in the corpus parses. Largest single win — row 1 alone is 366
occurrences across 51 files.

**Files:** `src/main/grammars/TypeSpec.bnf`; new fixtures
`src/test/testData/parser/ModelDecoratedProperty.tsp`/`.txt`,
`ModelCommaSeparators.tsp`/`.txt`, `ModelSpreadTemplate.tsp`/`.txt`;
`src/test/testData/corpus/BASELINE.txt`.

**Approach.** Separator semantics are settled against upstream
(`ListKind.ModelProperties`: `delimiter: Semicolon`, `toleratedDelimiter: Comma`, tolerated
*is* valid, trailing separator optional; `ListKind.EnumMembers` is the same object) — see
[ADR 0007 § Primary-source facts](../adr/0007-corpus-driven-grammar-acceptance.md). Do not
re-derive.

- `model_property ::= decorator_application* identifier '?'? ':' type_expression_ ('=' value_expression)? member_separator_?`
  — adds the decorator prefix (row 1). `decorator_application*` occupies sequence position 1,
  so the `':'` moves from position 3 to **position 4: set `pin=4`**. A wrong pin here
  silently degrades all error recovery without failing a test; verify `BrokenProperty.txt`
  by hand afterwards.
- `private member_separator_ ::= ';' | ','`, used **optionally**, so `model M { a: string }`
  (no trailing separator — the dominant form inside inline `model_expression`s) parses (row 5).
- `enum_member` gets the same optional `member_separator_?` treatment.
- `model_spread ::= '...' type_expression_ member_separator_?` (row 6) — `Record<unknown>`
  needs template arguments, which `type_expression_` supplies. Keep the `'...'` pin.
- **Add `DECORATOR` to `bad_model_member_token_`'s token list.** Currently absent; this is
  why one decorated property poisons a whole model body.
- **PSI contract regression check:** `TypeSpecPsiUtil.findNameIdentifier` takes the *first*
  direct `TypeSpecIdentifier` child. `decorator_application` wraps a single `DECORATOR`
  token and has no `identifier` child, so a decorated property's name is still found — but
  `tsp-dev` must confirm this against the **generated** PSI, not assume it.

**Acceptance (`tsp-tester`):**
- Three new golden fixtures at `doTest(true, true)`, covering: multiple stacked decorators;
  decorators with and without argument lists; `,` separators; a missing final separator;
  `...Record<unknown>;` and `...Foo.Bar<T, U>,`.
- `TypeSpecPsiContractTest` gains a case: a decorated property's `getName()` /
  `getNameIdentifier()` / `getTextOffset()` still point at the property name, not the
  decorator.
- `BrokenProperty.txt` / `BrokenStatement.txt` re-reviewed **by hand** after the pin change.
- `BASELINE.txt` shrinks by every file whose only failures were rows 1/5/6.

**Done when:** `./gradlew clean build test verifyPlugin` green, and `BASELINE.txt` contains
**zero** `corpus/real/**` entries attributable to rows 1, 5 or 6.

**Risks / open questions:** an optional separator makes `model M { a: string b: string }`
legal. Accept it — an over-permissive grammar yields a wrong tree, an under-permissive one
yields red squiggles on valid code, and only the second is visible to the user. Record the
loosening as a comment in the `.bnf`.

---

### M6c — Model heritage without a body, and multi-line strings (rows 3, 4)
**Goal:** the last two constructs blocking the owner's repository. **This is the milestone
at which `ph-cdm` is clean in the IDE** — the visible goal of the whole plan, and the
natural release-candidate point if the owner later wants one
([ADR 0007 D11](../adr/0007-corpus-driven-grammar-acceptance.md); publishing itself stays
deferred per ADR 0002 D7).

**Files:** `src/main/grammars/TypeSpec.bnf`;
`src/test/testData/parser/ModelIsNoBody.tsp`/`.txt`, `ModelExtendsNoBodyIsError.tsp`/`.txt`,
`MultilineString.tsp`/`.txt`; `src/test/testData/corpus/BASELINE.txt`.

**Approach.**

- Model heritage, per [ADR 0007 D9](../adr/0007-corpus-driven-grammar-acceptance.md) —
  upstream `parseModelStatement` admits `;` **only** after `is`, and admits a body after
  `is` too:
  ```
  model_statement ::= decorator_application* 'model' identifier template_parameter_list?
                      ( is_clause (';' | model_body) | extends_clause? model_body )
  ```
  Both `is` forms are required: `model M is Foo<Bar>;` (23 corpus occurrences) **and**
  `model M is Stream<T> { … }` (`@typespec/http/lib/streams/main.tsp`). `extends` keeps
  requiring a body — do **not** simply make `model_body` optional, which would legalise
  `model M extends Foo;`. Keeping the alternatives separate keeps D9 reversible in one line.
- Multi-line strings: **the lexer is already correct** — `_TypeSpecLexer.flex` lines 53–54
  emit `MULTILINE_STRING`; the grammar never references the token. Declare
  `MULTILINE_STRING` in the `.bnf` `tokens=[…]` block (bare-referencable, same as
  `DECORATOR`) and add it as an alternative of `literal_type`. **Check whether the
  `MULTILINE_STRING_S` lexer state emits *several* `MULTILINE_STRING` tokens for one source
  literal** the way `STRING_S` does for escapes; if so `literal_type` needs a
  `MULTILINE_STRING+` run. Read the actual token stream in `TypeSpecLexerTest`, not the
  flex source's intent.

**Acceptance (`tsp-tester`):**
- `ModelIsNoBody.tsp` covers `model A is B;`, `model C is D<E>;`, a decorated one, and
  `model F is G<H> { x: string; }` — all at `doTest(true, true)`.
- `ModelExtendsNoBodyIsError.tsp` at `doTest(true)` asserts `model M extends Foo;` **still**
  produces a `PsiErrorElement`, locking ADR 0007 D9 in place so a future change is deliberate.
- `MultilineString.tsp` uses the exact shape from `ph-cdm/model/reservation/reservation.tsp`
  line 57+ — a `@doc("""…""")` spanning four lines inside a model body — plus a
  `"""`-valued `alias` and a `"""` as a decorator argument.
- `TypeSpecLexerTest` gains an assertion pinning how many tokens one `"""…"""` literal yields.

**Done when:** `./gradlew clean build test verifyPlugin` green **and** `BASELINE.txt`
contains **zero `corpus/real/**` entries at all**.

**Risks / open questions:** the `is` alternative interacts with `pin=2` on
`model_statement`; a mis-set pin turns `model` + identifier into an unrecoverable commit
point. Re-review both recovery goldens.

---

### M5.5 — Reference resolution and jump navigation

**Runs here**, between M6c and M6d, per
[ADR 0007 D11](../adr/0007-corpus-driven-grammar-acceptance.md).
[Plan 02](02-navigation.md) is the plan; it is unchanged in content. Three notes carried
over from the audit:

1. **Navigation was never implemented and was never claimed to be.** `plugin.xml` registers
   no `psi.referenceContributor` and no `gotoDeclarationHandler`;
   `grep -rl "PsiReference\|GotoDeclaration\|ResolveResult" src/main/kotlin` is empty. Plan
   02 has always scoped this as M5.5. The gap was between expectation and sequence, not
   between plan and tree.

2. **The `PsiNamedElement` groundwork M5b/M5c owed it is genuinely present and correct.**
   `TypeSpecNamedElement : PsiNameIdentifierOwner`, `TypeSpecNamedElementMixin` (backtick-
   stripping `getName`, `getNameIdentifier`, `getTextOffset` at the name, throwing
   `setName`, `getPresentation`), `TypeSpecPsiUtil.findNameIdentifier`, and the four
   `TypeSpecFile` accessors all exist and are covered by `TypeSpecPsiContractTest` across
   all 11 named rules. ADR 0004 D7.2's contract is satisfied.

3. **Prerequisite is M6c green, not M5c green.** A resolver built and tested against a
   grammar that mis-parses every decorated model property would be tuned to the wrong tree
   shape, and M6b changes `model_property`'s child list — exactly the node the resolver's
   declaration index reads. Plan 02's five-point prerequisite check runs first and is
   authorised to stop the milestone; **substitute "M6c" wherever it reads "M5c".**

**One correction to plan 02 to make before it starts.**
`TypeSpecFile.getTopLevelDeclarations()` returns *direct* `TypeSpecNamedElement` children of
the file. Every file in the corpus uses a **blockless** `namespace Foo.Bar;`, under which —
by the `namespace_statement` rule's own containment design — all subsequent declarations are
children of the *namespace*, not the file. So the accessor returns exactly one element for a
real-world file. That is correct per ADR 0004 D7.4 and is **not** a bug, but plan 02's
resolver must recurse through namespaces rather than treat the accessor as "all declarations
in this file". Verified against the corpus; flagged so `tsp-dev` does not rediscover it as a
resolve-returns-null mystery.

**Known-imperfect landing, accepted (ADR 0007 D11).** M5.5 ships while
`node_modules/@typespec/**` still parses with errors, so *Go to declaration* into a library
file lands on a squiggled page. Strictly better than no navigation; M6e closes it. If plan
02 adds a corpus-backed navigation test, it must target `corpus/real/**` (clean after M6c),
not `corpus/stdlib/**`.

---

### M6d — Operation and interface surface (row 7 + heritage forms)
**Goal:** operation signatures in the stdlib parse.

**Files:** `src/main/grammars/TypeSpec.bnf`;
`src/test/testData/parser/OperationSpreadParam.tsp`/`.txt`,
`InterfaceHeritage.tsp`/`.txt`, `OpIs.tsp`/`.txt`; `BASELINE.txt`.

**Approach.** Upstream facts already established (ADR 0007 § Primary-source facts) — do not
re-derive: interface bodies are `;`-delimited with `,` tolerated-but-invalid; they admit an
optional `op` keyword prefix; `interface X extends A, B` is a **comma-separated list of
reference expressions** (`parseList(ListKind.Heritage, parseReferenceExpression)`), not of
full type expressions; and **there is no `interface … is` form**.

- `operation_parameter ::= decorator_application* '...'? identifier '?'? ':' type_expression_ ('=' value_expression)?`
  — spread form (row 7) and default value. The pin moves again; re-verify recovery.
- `op_statement` gains an `is` alternative:
  `… identifier template_parameter_list? ( is_clause ';' | operation_parameter_list ':' type_expression_ ';' )`.
- `interface_statement ::= decorator_application* 'interface' identifier template_parameter_list? interface_heritage_? interface_body`
  with `private interface_heritage_ ::= 'extends' type_reference_ (',' type_reference_)*`.
  **No `is` alternative.**
- `interface_operation` mirrors `operation_parameter`'s changes and accepts an optional
  leading `'op'` keyword (grammatical upstream; **zero** corpus occurrences, so implement it
  but do not spend time on it).

**Acceptance (`tsp-tester`):** three fixtures at `doTest(true, true)`; a negative fixture
asserting `interface I is Stream<T>;` still errors (locks ADR 0007 D9's corollary);
`BASELINE.txt` shrinks by the 29 stdlib files whose only failure was row 7.

**Done when:** `./gradlew clean build test verifyPlugin` green and the baseline shrinks as
stated.

**Risks / open questions:** none outstanding — the two the audit flagged here (heritage list
shape, `interface is`) are closed by ADR 0007.

---

### M6e — Library-authoring surface (rows 8–12)
**Goal:** `node_modules/@typespec/**` parses clean, so *Go to declaration* lands somewhere
that is not red.

**Files:** `src/main/grammars/TypeSpec.bnf`; fixtures `ExternDeclarations.tsp`/`.txt`,
`ValueOf.tsp`/`.txt`, `ScalarBody.tsp`/`.txt`, `Directives.tsp`/`.txt`; `BASELINE.txt`.

**Approach:**
- `extern_declaration ::= 'extern' ( dec_statement | fn_statement | model_statement )` as a
  new `top_level_item_` alternative, plus
  `dec_statement ::= 'dec' identifier operation_parameter_list ';'` and `fn_statement`.
  **Check first whether the lexer keywordizes `extern` / `dec` / `fn`** — if not they arrive
  as `IDENTIFIER` and bare literals in the `.bnf` will not match. Read
  `_TypeSpecLexer.flex`; do not assume.
- `valueof` / `typeof` as prefix operators in `primary_type_expression`. Same lexer check.
- `scalar_statement` gains an optional body: `'{' scalar_member_* '}'`, `;`-delimited with
  `,` tolerated (`ListKind.ScalarMembers`), members being `init` constructors.
- Statement-level directives: one `DIRECTIVE` token plus a run of trailing string literals,
  attachable as a prefix on `top_level_item_` and on model/enum/union members. Plan 03
  already identified *placement* as the hard part; it still is.
- Rest parameters `...visibilities: valueof EnumMember[]` fall out of M6d's spread plus
  `valueof` here.

**Acceptance (`tsp-tester`):** four fixtures at `doTest(true, true)`; `BASELINE.txt` reduced
to at most a handful of entries, each with a written reason.

**Done when:** `./gradlew clean build test verifyPlugin` green.

**Risks / open questions:** directive placement is genuinely invasive. If it stalls, split it
out rather than blocking rows 8–10.

---

### M6f — Zero-baseline gate
**Goal:** delete the ratchet; the corpus becomes an absolute assertion.

**Files:** delete `src/test/testData/corpus/BASELINE.txt`; modify `TypeSpecCorpusTest.kt`;
create `src/test/testData/parser/KitchenSink.tsp`/`.txt`; modify
`docs/plans/03-grammar-and-psi.md` (§M5d formally closed); modify
`docs/plans/00-milestones.md`.

**Approach:**
- Remove the allowlist; the test asserts *every* corpus file satisfies both ADR 0007 D2
  properties.
- Add `src/test/testData/parser/KitchenSink.tsp` — a **valid-TypeSpec variant** of
  `src/test/testData/lexer/kitchen-sink.tsp`, per
  [ADR 0007 D10](../adr/0007-corpus-driven-grammar-acceptance.md). The lexer copy stays
  **byte-identical and untouched** (M2's golden owns it); the invalid
  `interface Store { values: #[1, 2, 3]; }` becomes an operation signature in the parser
  variant, and the grammar is **not** widened to accept a property in an interface body.
- Record in `00-milestones.md` that the corpus is now part of "what a grammar change must
  not break".

**Acceptance (`tsp-tester`):** `TypeSpecCorpusTest` has no allowlist; `KitchenSink.tsp`
passes `doTest(true, true)`; deliberately breaking one `.bnf` rule makes the corpus test
fail (verify by hand once, then revert).

**Done when:** `./gradlew clean build test verifyPlugin` green with no baseline file in the
tree.

**Risks / open questions:** none beyond the preceding milestones landing.

---

## Decision log

| Q | Decision | By | Recorded |
|---|---|---|---|
| OQ1 | Corpus vendored as a **domain-anonymised rewrite** (`corpus/real/`), stdlib verbatim + MIT attribution (`corpus/stdlib/`) | owner | ADR 0007 D4, D8; §M6a above |
| OQ2 | `model M extends Foo;` is **invalid**; grammar keeps rejecting it, with a negative fixture. Corollary: `model X is Y { … }` **is** valid and must parse; `interface … is` does not exist | architect, verified against `@typespec/compiler` `parseModelStatement` / `parseInterfaceStatement` | ADR 0007 D9; §M6c, §M6d |
| OQ3 | `interface Store { values: #[1,2,3]; }` is **invalid**; option (ii) — `kitchen-sink.tsp` untouched, a valid variant added in M6f | architect, verified against `parseInterfaceStatement` | ADR 0007 D10; §M6f |
| OQ4 | Sequence **M6a → M6b → M6c → M5.5 → M6d → M6e → M6f**; publishing deferred per ADR 0002 D7; M6c noted as the natural RC point | owner | ADR 0007 D11; this document's header |

**No open questions remain.** A new one must be raised as an ADR amendment, not resolved
inline by `tsp-dev`.
