# ADR 0009 — How a decorator name carries a reference

- **Status:** ACCEPTED (architect's call; supersedes the *deferral* in
  [ADR 0004](0004-reference-resolution-approach.md) open question 1, and **closes** it).
- **Date:** 2026-09-03
- **Context:** owner bug report from a real IDE session — `@TypeSpec.OpenAPI.info(...)` does not
  navigate on Cmd-click. Plan: [05](../plans/05-import-and-decorator-navigation.md).
- **Tree at decision time:** 202 tests, `verifyPlugin` Compatible, corpus gate absolute over
  83 files, two `<depends>`.

## Context

`_TypeSpecLexer.flex` emits all of `@Ns.name` as **one** token:

```
"@@" {QualifiedName}   { return AUGMENT_DECORATOR; }
"@"  {QualifiedName}   { return DECORATOR; }
```

`TypeSpec.bnf` wraps that single token in `decorator_application ::= DECORATOR
decorator_argument_list?` and `augment_decorator_statement ::= AUGMENT_DECORATOR '(' … ')' ';'`.
There is no `identifier` PSI inside, and `getReference()` exists only on the `identifier` rule
(`psi/impl/TypeSpecIdentifierMixin.kt`). So a decorator name has nowhere to hang a reference.

ADR 0004 open question 1 deferred the fix as "the owner's call, because splitting the token
breaks the M2/M3 lexer tests and highlighting". This ADR chooses without splitting the token, so
the owner's cost objection disappears rather than being paid.

## Options

### A — Split the lexer token

`@`, `Ns`, `.`, `name` as separate tokens; `decorator_name ::= AT identifier ('.' identifier)*`
in the grammar.

*Upside:* if the segments are reused as the existing `identifier` rule, they inherit
`TypeSpecIdentifierMixin.getReference()` and resolve through `TypeSpecResolver` with **no**
resolver change at all. It is also closer to upstream `parser.js`, where `@` and the name are
distinct tokens (so `@ doc` with intervening whitespace is legal and we currently reject it).

*Fatal downside — highlighting.* `TypeSpecSyntaxHighlighter` is a `SyntaxHighlighter`: it maps
**token type → `TextAttributesKey`, context-free**. An `IDENTIFIER` inside a decorator is
indistinguishable, at that layer, from any other identifier. Restoring the current colouring
therefore needs one of:

- an `Annotator`, which runs *after* parsing on the highlighting pass, not on the immediate
  lexer pass. Decorators would paint plain, then recolour — a visible flicker on file open and
  on every large re-highlight. The constraint on this work is "highlighting must not visibly
  change"; this visibly changes it.
- distinct sub-token types (`DECORATOR_AT` / `DECORATOR_SEGMENT` / `DECORATOR_DOT`) all mapped
  to `TypeSpecColors.DECORATOR`. This *is* pixel-identical. But the segments are then no longer
  `IDENTIFIER`, the `identifier` rule cannot be reused, the mixin does not apply — so the free
  resolution upside is forfeited and the resolver refactor of option B is needed **anyway**, on
  top of the lexer and golden churn.

*Blast radius, counted, not estimated:*

| Surface | Real number |
|---|---|
| `src/main/grammars/_TypeSpecLexer.flex` | the 2 decorator rules, plus a lexical state to keep `@` glued to its name |
| `src/main/grammars/TypeSpec.bnf` | 2 rules rewritten + 1 new; `DECORATOR` also appears in **6** `bad_*_token_` recovery alternatives (lines 130, 309, 760, 797, 831, 883) that each must list the new token(s) or ADR 0007's unclaimed-leaf property breaks |
| Parser goldens | **9** files contain decorator token lines; **19** token lines total (16 `DECORATOR`, 3 `AUGMENT_DECORATOR`), each expanding to 2–4 lines → 19 lines become 38–76. Every one needs regeneration **and** ADR 0007 hand review |
| `TypeSpecLexerTest` | 3 assertions (lines 208, 212, 216); `@Http.route` goes from 1 expected token to 4 |
| `TypeSpecSyntaxHighlighterTest` | 4 key-mapping assertions (lines 103–106) + the `"TSP_DECORATOR"` external-name mapping (line 193) |
| `TypeSpecHighlightingTest` | 2 assertions (lines 109, 113) that today assert **one** attribute range per decorator; they become N adjacent ranges |
| Word index | decorator segments become `IDENTIFIER`, which `TypeSpecFindUsagesProvider`'s `DefaultWordsScanner` indexes → **raises** tier C candidate counts for exactly the shared names [ADR 0008](0008-tier-c-file-cap.md) is already starved on. Wrong direction |
| Corpus gate | property-based over 83 files, no goldens → no churn, but the new leaves must all be claimed |

Nothing about `@Ns.name`-as-one-token is *load-bearing*; it is simply asserted in 9 places and
dumped in 19 more. The cost is real but it is churn, not risk. The disqualifier is the
highlighting dilemma above, not the churn.

### B — One token, one reference host, several sub-ranges  ← **chosen**

Keep the lexer and the grammar exactly as they are. Give `decorator_application` and
`augment_decorator_statement` a mixin whose `getReferences()` returns **one `PsiReference` per
dotted segment**, each with a `rangeInElement` covering just that segment (skipping the `@`,
`@@` and the dots).

This is the platform's own documented pattern for precisely this shape — the SDK describes
"a `PsiElement` representing a fully qualified name with multiple dedicated `PsiReference`s, each
bound to the range it resolves to (skipping the `.` separator)", and `PsiElement.findReferenceAt`
is defined as finding the reference at an offset. Cmd-click, Go To Declaration and Ctrl-hover all
route through `PsiFile.findReferenceAt(offset)`, so per-segment navigation is genuine, not
approximated: `@TypeSpec.<caret>OpenAPI.info` goes to the namespace, `@TypeSpec.OpenAPI.<caret>info`
goes to the `extern dec`.

*Blast radius:* **zero** lexer changes, **zero** token-type changes, **zero** golden lines, **zero**
highlighting changes. Adding `mixin=`/`implements=` to a bnf rule changes the generated class's
*base* class; the generated class name (`TypeSpecDecoratorApplicationImpl`) and the element type
name are unchanged, and those are what the golden dumps print.

*Cost:* `TypeSpecResolver.resolveSegment` is currently PSI-shaped — it derives the segment list
from `qualifiedName.identifierList` and the current segment's index from `indexOf(identifier)`.
Decorator segments are not `TypeSpecIdentifier`s, so the resolver needs a name-list-shaped core
(`names: List<String>`, `index: Int`, `context: PsiElement`) that both callers share.
`TypeSpecScope.chainFor` already takes a plain `PsiElement`, so the scope side needs nothing.
That refactor is the whole price, and it is one `tsp-dev` run.

*Known limitation, addressed separately:* `TypeSpecFindUsagesProvider`'s `DefaultWordsScanner` is
configured with `TokenSet.create(IDENTIFIER)` as its identifier set, and `DECORATOR` is in none of
its three sets — so decorator text is **not word-indexed at all** today. Reference *resolution*
(Cmd-click) does not care, but `ReferencesSearch`'s word prefilter does, so Find Usages on an
`extern dec` would not list its `@`-usages. Fixed by a words scanner that word-splits
`DECORATOR`/`AUGMENT_DECORATOR`; see plan 05 M5.6e. This is a gap option A would not have had —
it is the one genuine point in A's favour, and it is worth one small class, not the table above.

### C — `GotoDeclarationHandler`

Register `com.intellij.gotoDeclarationHandler`, compute the segment under the caret by hand,
resolve, return the target.

Rejected: it needs the same offset→segment arithmetic and the same resolver refactor as B, and
buys strictly less — a `GotoDeclarationHandler` participates in Go To Declaration only. It is
invisible to Find Usages, highlight-usages-on-hover, rename, and the completion/`isReferenceTo`
machinery `TypeSpecReference` already feeds. Same cost, fewer features, and a second parallel
resolve entry point to keep consistent with the first.

## Decision

**Option B.** One reference host per decorator node, one `PsiReference` per dotted segment,
sub-ranges into the single existing `DECORATOR` / `AUGMENT_DECORATOR` token. `@@augment`
decorators use the same mixin logic against `AUGMENT_DECORATOR` (2-char prefix instead of 1) —
the two are handled by one shared segment-splitting helper, never by two copies that can drift.

`@Ns.name`-as-one-token therefore stays true, and ADR 0004 open question 1 is closed as
"not needed" rather than "not yet".

## Consequences

- Highlighting is bit-identical, because nothing in the highlighting path is touched.
- The 19 golden token lines and the 9 lexer/highlighter assertions stay exactly as they are;
  their continued passing is itself the regression check that this option kept its promise.
- The resolver grows a name-based entry point. That is reusable: completion (M6) and any future
  string-shaped reference wants the same thing.
- Decorator segments are not renameable until an `ElementManipulator` exists for the decorator
  nodes. Not in scope; `handleElementRename` throws `IncorrectOperationException` until then.
- `@doc`, `@key` and every other bare standard-library decorator still will **not** resolve, for
  a reason that has nothing to do with this ADR: the standard library is never imported
  explicitly. See [ADR 0010](0010-library-import-resolution.md) §Open questions.
