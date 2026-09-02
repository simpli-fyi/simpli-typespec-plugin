# ADR 0005 — A minimal flat `ParserDefinition` lands in M4 (correcting ADR 0003 F5/D1)

- Status: **Accepted**
- Date: 2026-09-02
- Deciders: `tsp-architect` (proposed), project owner (to ratify)
- Amends: [ADR 0003](0003-parser-definition-timing.md) — F5 row 1 is **factually wrong**;
  D1/D2 are **narrowed**, not reversed.
- Relates to: [ADR 0001](0001-highlighting-approach.md), [plan 00](../plans/00-milestones.md)
  (M4, M5 Task 0)

## Context

M4 (commit `3c4c84c`) shipped `TypeSpecCommenter`, `TypeSpecBraceMatcher` and
`TypeSpecQuoteHandler` on a lexer-only setup, on the strength of ADR 0003 F5, which asserted:

> | `lang.commenter` | Yes | Not PSI-driven. |

`./gradlew test` then failed **all 6** `TypeSpecCommenterTest` cases — not merely the
selection-based ones. `tsp-intellij-researcher` traced the cause in the platform source:

1. With no `ParserDefinition`, `AbstractFileViewProvider.createFile()` falls back to
   `PsiPlainTextFileImpl`, whose language is `PlainTextLanguage` (ADR 0003 F1 — that part
   was correct).
2. `CommentByLineCommentHandler` / `CommentByBlockCommentHandler` resolve the commenter via
   `LanguageCommenters.INSTANCE.forLanguage(file.getLanguage())`. That language is `TEXT`.
   Our `<lang.commenter language="TypeSpec">` is **never consulted**.

So commenting is broken *entirely*, not just for selections. The caret-only cases appeared
to pass earlier only because before/after fixtures were compared on an untouched document.

**ADR 0003's claim that the `Commenter` EP "is not PSI-driven" is false on the current
platform.** Resolution is by *file language*, and file language is a PSI-derived property.
This ADR corrects that and decides the remedy.

## Options

### Option A — land a minimal, flat `ParserDefinition` now, in M4

Own `IFileElementType(TypeSpecLanguage)`, own `TypeSpecFile : PsiFileBase`, and a trivial
`PsiParser` that opens the root marker, consumes every lexer token as a flat leaf, and
closes it. No grammar, no BNF, no PSI element hierarchy. `getCommentTokens()` /
`getStringLiteralElementTypes()` / `getWhitespaceTokens()` wired to the existing
`TypeSpecTokenSets`.

- File language becomes `TypeSpec` → every language-keyed EP resolves correctly.
- `DefaultASTFactoryImpl` turns comment tokens into real `PsiComment` leaves and whitespace
  into `PsiWhiteSpace`, so the **stock** comment handlers work, honouring
  `LINE_COMMENT_ADD_SPACE` / `BLOCK_COMMENT_ADD_SPACE` code-style settings.
- ~40 lines, zero custom document surgery.
- Not throwaway: M5 Task 0 was already scheduled to write exactly these classes. M5 replaces
  the `PsiParser` body with the generated Grammar-Kit parser and keeps everything else.

Cost: the whole file is parsed and held as an AST; we now own the comment/whitespace token
contracts. Both were already accepted as M5 costs — this pulls them forward, not adds them.

### Option B — lexer-only escape hatch

`MultipleLangCommentProvider` (EP `com.intellij.multiLangCommenter`) to route commenter
resolution despite plain-text PSI, **plus** `TypeSpecCommenter : SelfManagingCommenter<CommenterDataHolder>`
with hand-rolled document-level comment/uncomment/insert/detect logic over
`SelfManagingCommenterUtil`.

- ~70 lines of hand-written offset arithmetic — the highest-defect-density code in the
  plugin so far, guarding an entirely cosmetic feature.
- Bypasses code-style settings users expect to work.
- Installs a **global** EP whose `canProcess` runs for every file opened in the IDE — a
  Community-wide performance and correctness surface for a `.tsp`-only feature.
- Deleted by M5 anyway, once the real `ParserDefinition` exists.

Option B is more code, worse behaviour, wider blast radius, and shorter-lived than Option A.

## Decision

**D1. Option A. A minimal flat `ParserDefinition` lands now, as M4b, before M4 is called
done.** ADR 0003 D1 ("does not land in M4") is overturned on the narrow ground that its
supporting finding (F5, `lang.commenter`) was wrong: M4 *does* have a feature that needs it.

**D2. ADR 0003 D2 ("no placeholder at all") is narrowed, not discarded.** What lands is a
placeholder *parser*, never a placeholder *`ParserDefinition` shape*. The prohibitions of
ADR 0003 F4 stand and are binding on `tsp-dev`:

- **Do not subclass or reuse `PlainTextParserDefinition`** — its `createFile` returns
  `PsiPlainTextFileImpl` and re-triggers the exact fallback we are fixing.
- Own `IFileElementType(TypeSpecLanguage.INSTANCE)`, own `TypeSpecFile : PsiFileBase`.
- The flat parser is explicitly a **transitional state with a named owner (M5 Task 0)**, not
  a permanent one. `TypeSpecParserDefinition` must carry a KDoc saying so.

**D3. Option B is rejected outright and must not be implemented, now or later.**
`SelfManagingCommenter` and `multiLangCommenter` are off the table for this plugin.
`TypeSpecCommenter` stays a plain `Commenter`; only its KDoc changes (the "not PSI-driven"
sentence is a lie and must go).

**D4. `lang.quoteHandler` and `lang.braceMatcher` both require re-verification as part of
this fix — non-optional.** ADR 0003 marked the quote handler "Yes, verified" and the brace
matcher "Likely, not proven"; the commenter was also marked "Yes" and was wrong, so neither
label is trusted. After M4b the environment changes anyway (file language becomes TypeSpec),
so the prior analysis is stale in both directions. `tsp-tester` re-runs both suites *after*
the `ParserDefinition` lands and reports actual behaviour. **ADR 0003 D5's "keep it
registered, at risk, verify manually in runIde" concession expires**: with a real file
language there is no longer an excuse for an unproven registration — brace matching must now
be asserted in an automated test.

**D5. The minimal `ParserDefinition` gets its own test coverage in M4b**, not just indirect
coverage via the commenter suite. Minimum assertions listed under Acceptance below. This is
what makes M5 Task 0 a safe refactor rather than a rewrite.

**D6. The M1/M3 test comments that assert plain-text PSI must be swept.**
`TypeSpecFileTypeTest` documents `psiFile.language == PlainTextLanguage` as correct-until-M5;
after M4b it is wrong. The `checkHighlighting` ban of ADR 0003 D4 also lifts early — but
lifting it is **permission, not instruction**: existing M3 tests stay as they are.

## What `tsp-dev` implements (M4b)

**Create**
```
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecFile.kt
src/main/kotlin/simpli/fyi/plugins/typespec/parser/TypeSpecParserDefinition.kt
src/main/kotlin/simpli/fyi/plugins/typespec/parser/TypeSpecFlatParser.kt
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecElementTypes.kt   (FILE element type)
```
**Modify**
```
src/main/resources/META-INF/plugin.xml                                    (+ lang.parserDefinition)
src/main/kotlin/simpli/fyi/plugins/typespec/editor/TypeSpecCommenter.kt   (KDoc only — delete the
                                                                           "not PSI-driven" claim)
```

Shape (descriptive, not code): `TypeSpecParserDefinition : ParserDefinition` returning
`TypeSpecLexerAdapter` from `createLexer`; `TypeSpecFlatParser` from `createParser`; a single
`IFileElementType(TypeSpecLanguage.INSTANCE)` from `getFileNodeType`; `TypeSpecTokenSets.COMMENTS`
from `getCommentTokens`; `TypeSpecTokenSets.STRINGS` from `getStringLiteralElementTypes`;
`TokenSet.WHITE_SPACE` from `getWhitespaceTokens`; `TypeSpecFile(viewProvider)` from
`createFile`; and — since no composite element types exist yet — `createElement` throwing or
returning a single generic element, never silently producing a wrong type.
`TypeSpecFlatParser.parse` = mark root → `while (!builder.eof()) builder.advanceLexer()` →
done(root) → `builder.treeBuilt`. **No `TokenSet` for `getWhitespaceTokens` may include a
comment token, and vice versa** — that miswiring is the classic source of broken commenting.

Add nothing else: no folding, no spellchecking (still M5, ADR 0003 D3 unchanged), no BNF.

## Acceptance (`tsp-tester`, M4b)

New `TypeSpecParserDefinitionTest`:
- `psiFile.language == TypeSpecLanguage.INSTANCE` and `psiFile.fileType == TypeSpecFileType.INSTANCE`
  for a configured `.tsp` file (the inverse of today's `TypeSpecFileTypeTest` comment).
- `psiFile is TypeSpecFile`, and **not** `PsiPlainTextFileImpl`.
- Round-trip: `psiFile.text == originalSource` for `testData/lexer/kitchen-sink.tsp` — the flat
  parser must lose nothing.
- `PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)` is non-empty for a file with
  `//`, `/* */` and `/** */` comments, and each comment's text matches the source slice.
- `LanguageCommenters.INSTANCE.forLanguage(psiFile.language)` is a `TypeSpecCommenter`
  (this is the assertion that would have caught the M4 regression).

Existing suites, re-run and repaired:
- `TypeSpecCommenterTest` — all 6 cases green, via the stock `IdeActions.ACTION_COMMENT_LINE` /
  `ACTION_COMMENT_BLOCK` path. Delete the `println("DEBUG …")` diagnostics left in the file.
- `TypeSpecQuoteHandlerTest` — re-verified under the new file language (D4).
- `TypeSpecBraceMatcherTest` — now asserts real matching behaviour, not just the pair list (D4).
- `TypeSpecFileTypeTest` — assertions and KDoc corrected (D6).
- `TypeSpecSyntaxHighlighterTest` / `TypeSpecHighlightingTest` — comments referencing the
  plain-text fallback corrected; **assertions unchanged**, and they must still pass. A break
  here means the `ParserDefinition` changed highlighting, which it must not.

## Done when

```bash
./gradlew clean build test verifyPlugin
```
is green with zero skipped/ignored TypeSpec tests, and `verifyPlugin` still reports exactly one
`<depends>`: `com.intellij.modules.platform`.

## Consequences

- M4 gains a sub-milestone (M4b) and is not "done" until it is green. M5 Task 0 shrinks from
  "land the `ParserDefinition`" to "replace the flat parser body with the generated one".
- Every `.tsp` file open in the IDE now carries an AST. For a highlighting-scale plugin on
  files of realistic size this is not a measurable cost, but it is a real behaviour change
  and is the price of D1.
- The `psiFile.language == PlainTextLanguage` oddity that ADR 0003 said would persist until
  M5 disappears now. Any doc or comment claiming otherwise is stale.
- Standing lesson, applied to every future EP we register: **"does this EP resolve by file
  language?" must be answered from platform source before an EP is planned as lexer-only.**
  Language-keyed EPs on a plain-text PSI file are silently dead, not loudly broken.

## Open questions for the project owner

1. Ratify pulling the `ParserDefinition` into M4 (D1). The alternative — accept broken
   commenting and defer all of M4's editor conveniences into M5 — is coherent but wastes the
   shipped work; say so and this ADR is amended.
2. ADR 0003's open question 1 (second `<depends>` for spellchecking in M5) is **unaffected**
   and still outstanding.
