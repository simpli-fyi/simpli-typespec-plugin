# ADR 0007 — Corpus-driven grammar acceptance

- **Status:** **accepted 2026-09-02.** Owner ratified D4 (corpus vendoring) and D11
  (sequencing); D9/D10 resolved by the architect against upstream primary sources.
- **Date:** 2026-09-02
- **Supersedes:** nothing. **Amends:** [ADR 0006](0006-grammar-toolchain.md) §D8/D9
  (acceptance oracle for grammar milestones), [plan 03](../plans/03-grammar-and-psi.md)
  §M5c "Kitchen-sink scope resolution" and §M5d.
- **Context:** post-M5c audit, tree at `9183c50`. `./gradlew build verifyPlugin` green,
  140 tests / 0 failures, verifier `Compatible` against IC-252.28539.97.

---

## Context

The plugin's grammar milestones (M5a–M5c) shipped with an acceptance oracle consisting
entirely of **hand-authored fixtures written by the same agent that wrote the grammar**:
31 files under `src/test/testData/parser/`, each a `.tsp` input plus a golden `.txt` tree.

Measured against a real production TypeSpec repository
(`/Users/KHODIAKOVA/IdeaProjects/puenktlichhansa/ph-cdm`, 23 first-party `.tsp` files
plus 83 `.tsp` files of `@typespec/*` standard library under `node_modules`), the shipped
grammar produces `PsiErrorElement`s in **73 of 106 files**, including **13 of the owner's own 23
first-party files** — which is *every* first-party file that declares a model, an event
or a proto dialect. The remaining 10 clean ones are one-line `import`/`using` aggregators.
In practice every substantive file in the owner's repository shows red squiggles in the IDE.

The 140 passing tests did not detect any of it.

### Why the oracle failed

1. **The fixtures and the grammar have the same author.** A fixture can only exercise a
   construct its author remembered. Every construct the grammar author did not think of
   is simultaneously absent from the grammar *and* absent from the tests — the failure is
   invisible by construction. This is not a coverage gap that more of the same fixtures
   fixes; it is a *missing independent oracle*.

2. **The one independent oracle that existed was withdrawn.** Plan 03 §M5c revision 2 had
   "`src/test/testData/lexer/kitchen-sink.tsp` parses with zero `PsiErrorElement`" as
   M5c's done-signal. Revision 3 declared that "unachievable inside M5c's declared scope",
   withdrew it, and replaced it with `KitchenSinkCore.tsp` — *a subset of the same file,
   chosen by the implementer, containing exactly what the implementer's grammar could
   already parse*. The residual was deferred to an unplanned "M5d". That substitution is
   the precise moment the project stopped being able to detect its own gaps.
   (`kitchen-sink.tsp` was itself team-authored, so even the withdrawn criterion was only
   weakly independent.)

3. **Golden `.txt` trees assert stability, not correctness.** A golden locks in whatever
   the parser did on the day it was baselined.

4. **"Zero `PsiErrorElement`" is an incomplete oracle for this grammar.** ADR 0006 D6's
   recovery design uses `bad_top_level_token_` / `bad_model_member_token_` /
   `bad_interface_member_token_` / `bad_enum_member_token_` / `bad_union_variant_token_`
   *fallback alternatives* rather than `recoverWhile`. These are `private` rules that
   consume one token and succeed — **silently**. An entirely unknown construct is therefore
   swallowed with **no error element at all**. Verified: `const x = 5;` — a construct with
   no rule anywhere in `TypeSpec.bnf` — parses with zero `PsiErrorElement` and produces a
   flat run of bare leaf tokens directly under the file node. Any future corpus test that
   asserts only "no `PsiErrorElement`" will report a false green for exactly the class of
   construct it exists to catch.

---

## Decisions

### D1 — The acceptance oracle for every grammar milestone is a real-world corpus, not a hand-authored fixture

From M5d onward, no grammar milestone is "done" on hand-authored fixtures alone. The
done-signal must include a corpus test over `.tsp` files **the plugin team did not write**.
Hand-authored fixtures keep their role — they pin *tree shape* (golden `.txt`) and are the
right place for recovery and edge-case tests — but they no longer certify coverage.

### D2 — The corpus test asserts two properties, not one

For each corpus file:

1. `PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` is empty; **and**
2. **no unclaimed leaf tokens** — every non-whitespace, non-comment leaf in the tree has a
   composite `TypeSpecElementType` ancestor other than the file node itself.

Property (2) is what closes the silent-swallow hole in §Context/4. Without it the corpus
test is a false-green generator.

Implementation note for `tsp-dev`: the `bad_*_token_` rules are `private` and therefore
inlined, so a swallowed token appears as a direct leaf child of `TypeSpecFile` (top level)
or of `model_body` / `interface_body` / `enum_body` / `union_body` (member level). The
assertion is a tree walk, not a grammar change — do **not** make the `bad_*` rules public
to make this easier; that changes every existing golden.

### D3 — The corpus is vendored into the repository, not read from an absolute path

`src/test/testData/corpus/` holds committed copies. Reasons: the audit's ad-hoc run read
`/Users/KHODIAKOVA/IdeaProjects/puenktlichhansa/ph-cdm` directly, which is single-machine,
mutable by a third party, and would make the test both non-reproducible and a silent
no-op on any other checkout. A vendored corpus is a regression asset with a known content
hash.

The corpus test must **fail loudly** if the corpus directory is empty — never skip.

### D4 — Corpus composition — DECIDED (owner, 2026-09-02)

Two tiers, both vendored under `src/test/testData/corpus/`:

- `corpus/stdlib/` — the `@typespec/*` library `.tsp` files, **vendored verbatim**.
  MIT-licensed; attribution obligations in D4.1.
- `corpus/real/` — the `ph-cdm` files, **vendored as a domain-anonymised rewrite**
  (owner's ruling on OQ1). Syntactic constructs preserved *exactly* — the corpus exists to
  reproduce the real parse failures — but no `ph-cdm` schema content is published.

The full anonymisation ruleset, re-sync procedure and licence layout are specified in
[plan 04](../plans/04-grammar-corrections.md) §M6a, which is where `tsp-dev` executes them.
Two constraints are load-bearing enough to restate here:

- **D4.1 — attribution.** `corpus/stdlib/LICENSE` (copied from
  `node_modules/@typespec/compiler/LICENSE`) and `corpus/stdlib/PROVENANCE.md` (package
  names + exact versions + the `tsp` compiler version that produced them) are mandatory.
  A vendored MIT corpus without its licence is a licence violation, not a style nit.
- **D4.2 — anonymisation must be construct-preserving and injective.** The rename map is
  one-to-one: two distinct source names never collapse onto one target name, and a rename
  never changes a token's *category* (an identifier stays an identifier, a dotted namespace
  keeps the same number of segments, a backticked name stays backticked). Collapsing two
  differently-shaped namespaces onto one name silently deletes a test case, which is the
  exact failure mode this ADR exists to prevent.

### D5 — Grammar tiering is by corpus frequency, not by construct taxonomy

Plan 03's M5c/M5d split was drawn along conceptual lines ("declarations" vs "projections,
`const`, function types"). That taxonomy put `@key id: string;` — a decorated model
property, 102 occurrences across 9 of the owner's 13 files, the single most common
non-trivial construct in real TypeSpec — on neither side of the line, and it shipped
broken. Remaining grammar work is re-sequenced by measured corpus frequency
([plan 04](../plans/04-grammar-corrections.md)).

### D6 — `value_expression` and `type_expression_` are unified, per upstream

The reported defect (`@@package(A.B, { name: "x" })`) is not a missing edge case, it is a
modelling error. Upstream TypeSpec has **one** `Expression` production used for decorator
arguments; it spans type expressions *and* value literals. The current grammar has two
disjoint hierarchies — `value_expression` (literals, `qualified_name`, `#{}`, `#[]`) and
`type_expression_` (unions, intersections, arrays, `{}` model expressions, tuples) — and
routes decorator arguments only through the first. Any decorator argument that is a type
expression therefore fails.

Decision: `value_expression` becomes an alternation whose type-expression branch is
`type_expression_`, so decorator arguments accept both. The value-only literals (`#{}`,
`#[]`) stay ordered first so they are not shadowed. This deletes the duplicated
`STRING`/`NUMBER`/`true`/`false`/`qualified_name` alternatives, which `literal_type` and
`type_reference_` already cover.

`type_expression_` and the rest of the precedence chain stay `private` (ADR 0006's
inlining rationale, which keeps the M5b goldens byte-identical, is unaffected — but the
`Decorators.txt` / `AugmentDecorator.txt` goldens *will* change and must be re-reviewed by
hand, not blind-rebaselined).

### D7 — The CE constraint is intact; the reported "third `<depends>`" is a false alarm

`src/main/resources/META-INF/plugin.xml` declares exactly two `<depends>`:
`com.intellij.modules.platform` and `com.intellij.modules.spellchecker`. `grep -c "<depends"`
returns 3 because it also matches the XML comment line *"the only sanctioned second
`<depends>` — a third needs a new ADR"*. No unsanctioned dependency exists. The plugin
verifier's `Compatible` verdict against IC-252.28539.97 independently confirms this. No
action required; recorded here so the question is not re-opened.

---

## Consequences

- Grammar milestones get slower to close and much harder to falsely close.
- The corpus test is a single test method over ~106 files. It runs in-process via
  `PsiFileFactory.createFileFromText`; the audit's run took well under a minute.
- The repository gains a vendored third-party corpus (licence obligations, D4).
- Plan 03 §M5d's done-signal ("unmodified `kitchen-sink.tsp` parses clean") is **withdrawn**,
  not merely demoted: the file is not valid TypeSpec (see D10) and can never be the target.
  A valid-TypeSpec variant replaces it in M6f, and the corpus is the real oracle.

## Decisions taken on the audit's open questions

### D8 — OQ1, corpus vendoring: domain-anonymised rewrite (owner, 2026-09-02)

Ruled by the owner. Option (b) of the audit's three. See D4 above and
[plan 04](../plans/04-grammar-corrections.md) §M6a for the executable detail.

### D9 — OQ2, `model M extends Foo;`: invalid TypeSpec, and the grammar must keep rejecting it

**Resolved against a primary source**, not left open. `@typespec/compiler`'s own parser
(`dist/src/core/parser.js`, `parseModelStatement`) branches as follows:

```js
const optionalExtends = parseOptionalModelExtends();
const optionalIs = optionalExtends ? undefined : parseOptionalModelIs();
if (optionalIs) {
    const tok = expectTokenIsOneOf(Token.Semicolon, Token.OpenBrace);   // `is` -> ';' OR '{'
    if (tok === Token.Semicolon) { nextToken(); }
    else { propDetail = parseList(ListKind.ModelProperties, …); }
} else {
    propDetail = parseList(ListKind.ModelProperties, …);                // everything else -> '{' required
}
```

Only the `is` branch admits `;`. `extends` falls through to the `else` and **requires a
body**. This matches the corpus exactly (0 occurrences of `model M extends Foo;`).
The audit's recommendation stands and is now evidence-backed: M6c adds the `is` form only,
and ships a negative fixture asserting `model M extends Foo;` still produces a
`PsiErrorElement`.

**Corollary the audit had not established:** the same code shows `model X is Y { … }` — `is`
*with* a body — is equally valid, and it occurs in the corpus
(`@typespec/http/lib/streams/main.tsp`, `model HttpStream<…> is Stream<Type> { … }`). M6c
must accept **both** `is` forms. The audit's provisional "row 13, `interface I is Stream<T>`"
was a regex artefact reading that same multi-line `model … is` declaration; there is no
`interface … is` form upstream (`parseInterfaceStatement` has no `is` branch at all) and it
is struck from the work list.

### D10 — OQ3, `interface Store { values: #[1, 2, 3]; }`: invalid; option (ii)

**Resolved against a primary source.** `parseInterfaceStatement` parses its body as
`parseList(ListKind.InterfaceMembers, … parseOperationStatement …)` — interface members are
*operation statements only*. A property in an interface body is not valid TypeSpec, no
corpus file contains one, and **the grammar must not be widened to accept it.**

Therefore option **(ii)**: `src/test/testData/lexer/kitchen-sink.tsp` stays exactly as it
is (M2's lexer golden is untouched, and the file remains lexically motivated — its job is
token coverage, not validity), and M6f introduces a *separate* valid-TypeSpec variant,
`src/test/testData/parser/KitchenSink.tsp`, as the parser-side target. Plan 03 §M5d's
standing open question is hereby closed.

### D11 — OQ4, sequencing: M6a → M6b → M6c → M5.5, then M6d–M6f (owner, 2026-09-02)

The owner's repository going clean is the target, and it is reached at **M6c**. Navigation
(M5.5, [plan 02](../plans/02-navigation.md)) runs next. TypeSpec-stdlib coverage
(M6d–M6f) follows navigation rather than preceding it.

Consequence accepted knowingly: M5.5 ships while `node_modules/@typespec/**` still parses
with errors, so *Go to declaration* into a library file will land on a squiggled page. That
is a strictly better state than today (no navigation at all), and M6e closes it.

Publishing stays deferred per [ADR 0002](0002-build-and-platform-baseline.md) D7 — no
release decision is required now. Recorded for later: **M6c is the natural
release-candidate point**, being the first commit at which a real-world TypeSpec repository
is error-free in the editor.

---

## Primary-source facts established during this audit

Read from `node_modules/@typespec/compiler/dist/src/core/parser.js` (the shipped compiler,
same version the corpus was authored against). Recorded so no later milestone re-derives
or re-litigates them:

| Fact | Source | Consumed by |
|---|---|---|
| `model` body separator is `;`, with `,` **fully tolerated and valid** (`ListKind.ModelProperties`: `delimiter: Semicolon, toleratedDelimiter: Comma`, no `toleratedDelimiterIsValid: false`); trailing separator optional | `ListKind.ModelProperties` | plan 04 M6b |
| `enum` members use the *same* list kind as model properties (`ListKind.EnumMembers = { ...ListKind.ModelProperties }`) | ibid. | plan 04 M6b |
| `#{ … }` object-literal members are **comma**-delimited (`ListKind.ObjectLiteralProperties`) | ibid. | already correct in `TypeSpec.bnf` |
| `scalar` bodies are `;`-delimited with `,` tolerated (`ListKind.ScalarMembers`) | ibid. | plan 04 M6e |
| Interface bodies are `;`-delimited, `,` tolerated **but invalid** (`toleratedDelimiterIsValid: false`), and admit an optional `op` keyword prefix (`allowedStatementKeyword: Token.OpKeyword`) | `ListKind.InterfaceMembers` | plan 04 M6d |
| `interface X extends A, B` is a **comma-separated list of reference expressions** (`parseList(ListKind.Heritage, parseReferenceExpression)`), not of full type expressions; there is no `interface … is` | `parseInterfaceStatement` | plan 04 M6d |
| `model X is Y;` **and** `model X is Y { … }` are both valid; `model X extends Y;` is not | `parseModelStatement` | plan 04 M6c (D9) |
| Interface members are operation statements only | `parseInterfaceStatement` | D10 |

The `op`-keyword-prefixed interface member (`interface I { op foo(): X; }`) is grammatical
upstream but has **zero** occurrences in 106 corpus files. Deprioritised to M6d's optional
tail, not dropped.
