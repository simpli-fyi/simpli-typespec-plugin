# ADR 0007 — Corpus-driven grammar acceptance

- **Status:** proposed (needs owner ratification on D4/D5 and the two open questions)
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

### D4 — Corpus composition (needs owner ratification)

Proposed two tiers:

- `src/test/testData/corpus/stdlib/` — the `@typespec/*` library `.tsp` files
  (`compiler/lib/std`, `http/lib`, `openapi/lib`, `openapi3/lib`, `json-schema/lib`,
  `protobuf/lib`). These are the files a user's *Go to declaration* will land in, so they
  must parse. Upstream `microsoft/typespec` is MIT-licensed; vendoring requires retaining
  the licence header — `tsp-dev` must copy `node_modules/@typespec/compiler/LICENSE`
  alongside and add a `PROVENANCE.md` recording package name + version.
- `src/test/testData/corpus/real/` — representative files derived from `ph-cdm`.
  **These are the owner's proprietary domain model.** See open question OQ1.

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
- Plan 03 §M5d's done-signal ("unmodified `kitchen-sink.tsp` parses clean") is retained but
  demoted: it is no longer sufficient, only necessary. Its open question about
  `interface Store { values: #[1,2,3]; }` is resolved by D1 — that construct is invalid
  TypeSpec, no corpus file contains it, and the grammar must not accept it. `tsp-dev`
  should take option (ii): a valid-TypeSpec variant, leaving M2's lexer golden untouched.

## Open questions for the owner

- **OQ1 — may `ph-cdm` files be vendored into this plugin repository?** They are the
  owner's production domain model (flight/reservation CDM) and this plugin repo may be
  published. Options: (a) vendor verbatim; (b) vendor a structurally-identical,
  domain-anonymised rewrite (same constructs, renamed types — preserves all test value);
  (c) do not vendor, rely on the MIT-licensed `@typespec` stdlib corpus only, and keep the
  `ph-cdm` sweep as a manual pre-release check. **Recommendation: (b).** `tsp-dev` must
  not choose.
- **OQ2 — is `model M extends Foo;` (heritage, no body, `;`-terminated) valid TypeSpec?**
  The audit found **zero** occurrences in 106 corpus files, while `model M is Foo;`
  occurs 23 times. Upstream appears to allow the `;` form only after `is`. Currently both
  fail. Plan 04 fixes the `is` form only and deliberately leaves `extends`-without-body
  rejected. Unverified against `microsoft/typespec` primary sources — flagged rather than
  assumed. If wrong, it is a one-line grammar addition.
