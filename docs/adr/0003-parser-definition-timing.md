# ADR 0003 — When the `ParserDefinition` lands (and what a missing one actually costs)

- Status: **Accepted**
- Date: 2026-09-02
- Deciders: `tsp-architect` (proposed), project owner (to ratify)
- Relates to: [ADR 0001](0001-highlighting-approach.md) (staged approach: lexer first,
  Grammar-Kit parser later), [plan 00](../plans/00-milestones.md),
  [plan 01](../plans/01-lexer-and-highlighter.md)

## Context

ADR 0001 chose a staged approach: ship a JFlex lexer + `SyntaxHighlighter` with **no
`ParserDefinition`**, and layer a Grammar-Kit parser on later (M5). M0–M3 are implemented
and committed on that basis.

That left one question open, flagged as a risk in both plans: *what actually breaks when a
`Language` is registered with a `FileType` but no `lang.parserDefinition`?* Plan 01 §M3 risk 3
and plan 00 §M4 open questions 1–2 were explicitly blocked on it.

`tsp-intellij-researcher` answered it authoritatively against **ideaIC-2025.2.6.3
(build 252.28539.97)** and the corresponding `intellij-community` sources. The findings below
are the record; the decision follows.

## Findings (researcher, with citations)

### F1 — Plain-text PSI fallback is expected platform behaviour, not a bug

`AbstractFileViewProvider.createFile(Language)`
(`platform/core-impl/src/com/intellij/psi/AbstractFileViewProvider.java:157-163`) returns
`null` when `LanguageParserDefinitions.INSTANCE.forLanguage(lang)` is `null`. The caller
(`:154`) then falls through to `new PsiPlainTextFileImpl(this)`.

`PsiPlainTextFileImpl` (`:22-24`) **force-overwrites the file type**: because the view
provider's base language is not `PlainTextLanguage`, it sets
`myFileType = PlainTextFileType.INSTANCE`.

So for a `.tsp` file today:

| Expression | Value |
|---|---|
| `virtualFile.fileType` | `TypeSpecFileType` ✅ |
| `psiFile.viewProvider.baseLanguage` | `TypeSpecLanguage` ✅ |
| `psiFile.language` | `PlainTextLanguage` ❌ |
| `psiFile.fileType` | `PlainTextFileType` ❌ |

**Consequence:** the M1 acceptance criterion in plan 01 asserting
`myFixture.file.language == TypeSpecLanguage.INSTANCE` (and the matching
`myFixture.file.fileType` assertion) was simply **the wrong assertion**. It must assert
`virtualFile.fileType` and `viewProvider.baseLanguage` instead. This is a documentation fix,
not a product defect.

### F2 — Editor syntax highlighting works with no `ParserDefinition`

`EditorHighlighterFactoryImpl.createEditorHighlighter`
(`platform/platform-impl/src/com/intellij/openapi/editor/highlighter/EditorHighlighterFactoryImpl.kt:39-70`)
is driven entirely by `VirtualFile` / `FileType` / `Language`, never by PSI:

```
file.fileType
  → LanguageUtil.getLanguageForPsi   (consults only file-type language + LanguageSubstitutors)
  → FileTypeEditorHighlighterProviders
  → SyntaxHighlighterFactory
  → SyntaxHighlighterLanguageFactory (LanguageExtension over com.intellij.lang.syntaxHighlighterFactory)
  → wrapped in LexerEditorHighlighter
```

`ParserDefinition` appears nowhere on that path. **M3 ships as-is; the primary deliverable is
not blocked.**

### F3 — `checkHighlighting()` is meaningless here, and the SDK docs are actively misleading

`myFixture.checkHighlighting()` is driven by `HighlightInfo` produced by annotators and
inspections. A lexer-only language produces none, so the call is vacuous — it can never fail
and never proves colouring.

Worse, the SDK-recommended alternative is a **trap**:
`EditorTestUtil.testFileSyntaxHighlighting`
(`platform/testFramework/src/com/intellij/testFramework/EditorTestUtil.java:580-586`)
resolves the highlighter via `testFile.getFileType()` — which is `PlainTextFileType` per F1.
It would silently pick `PlainSyntaxHighlighter` and assert against empty output. A green test
that proves nothing is worse than no test.

**Correct approach until a `ParserDefinition` exists:**

1. `EditorHighlighterFactory.getInstance().createEditorHighlighter(virtualFile, scheme, project)`
   — note **`virtualFile`**, whose `fileType` is still correct — then iterate the returned
   highlighter's `HighlighterIterator` over the document and assert token ranges/attribute keys.
2. Direct unit tests of `TypeSpecSyntaxHighlighter.getTokenHighlights(tokenType)`, which need
   no fixture at all.

### F4 — A placeholder `ParserDefinition` is a sanctioned pattern, but must not subclass the platform's

The platform's own `PlainTextParserDefinition` is the reference minimal implementation: an
`IFileElementType` whose `parseContents` emits a single leaf, so **no real `PsiParser` is
needed**.

But we must **not** subclass or reuse it: its `createFile` returns `PsiPlainTextFileImpl`,
which re-triggers the file-type overwrite of F1. A placeholder for TypeSpec means owning our
own `IFileElementType(TypeSpecLanguage)` and our own `PsiFileBase` subclass, plus
`createLexer` / `createParser` / `getFileNodeType` / `createElement` / `createFile`.

Cost of doing it: the whole file is parsed and held as an AST; `getCommentTokens` /
`getWhitespaceTokens` immediately start feeding TODO indexing, commenter and whitespace logic
(desirable, but now our responsibility to get right).

No SDK prose blesses this in words — **the sanction is by platform precedent only**.

### F5 — M4 feature-by-feature impact

| M4 item | Works without `ParserDefinition`? | Evidence |
|---|---|---|
| `lang.commenter` | Yes | Not PSI-driven. |
| `lang.quoteHandler` | **Yes, verified** | `TypedQuoteImpl.getQuoteHandler` falls back to `viewProvider.baseLanguage` (correct per F1), then operates on editor-highlighter tokens. |
| `lang.braceMatcher` | **Likely, not proven** | `BraceMatchingUtil.getBraceMatcher` keys off `IElementType.getLanguage()` taken from the lexer's tokens, so it resolves via `LanguageBraceMatching.forLanguage` *before* the FileType fallback. Holds **only if every TypeSpec token type is constructed with `TypeSpecLanguage`**. The researcher could **not** verify whether `BraceHighlightingHandler` early-returns on plain-text PSI. |
| `spellchecker.support` | **No** | The inspection walks PSI; useless without a `ParserDefinition`. |

On spellchecking availability: `SpellcheckingStrategy` **is** in Community
(`IC/lib/modules/intellij.spellchecker.jar`; EP `spellchecker.support` declared in content
module `intellij.spellchecker`), but it lives in module `com.intellij.modules.spellchecker`,
so registering it requires a **second `<depends>`**. That is a Community-available platform
module, not an Ultimate dependency — it is a deliberate, documented exception to the
project's one-`<depends>` rule, not a violation of the CE constraint.

## Decision

**D1. The `ParserDefinition` does not land in M4. It becomes M5's first task.**

Nothing in M4 needs it except spellchecking (F5), and spellchecking is worthless without it.
Adding a placeholder `ParserDefinition` in M4 would buy one feature and pay for it with an
AST over every open file, ownership of comment/whitespace token contracts, and a
throwaway class that M5 deletes weeks later.

**D2. We ship no placeholder `ParserDefinition` at all.** When the `ParserDefinition` arrives
in M5 it is the real one, backed by the Grammar-Kit parser. The F4 placeholder pattern is
recorded as the **fallback** if M5 splits (plan 00 already contemplates M5a/M5b): M5a may
land a real `TypeSpecParserDefinition` with a partial grammar, but never a
`parseContents`-emits-one-leaf stub as a permanent state.

**D3. Spellchecking moves from M4 to M5**, sequenced after the `ParserDefinition`, and its
second `<depends>` on `com.intellij.modules.spellchecker` is pre-approved as an exception —
subject to `./gradlew verifyPlugin` staying clean and the plugin still resolving on a bare IC
install.

**D4. Test guidance is corrected now, retroactively.** `checkHighlighting` and
`EditorTestUtil.testFileSyntaxHighlighting` are **banned** from this codebase for as long as
there is no `ParserDefinition`. `tsp-tester` uses `EditorHighlighterFactory` +
`HighlighterIterator` over the `VirtualFile`, and direct `getTokenHighlights` unit tests.

**D5. M4 keeps `lang.braceMatcher`, at risk.** It is cheap, it very likely works, and the
failure mode is cosmetic (no brace highlight) rather than an exception. Two conditions:
(a) `tsp-dev` must confirm every `IElementType` in `TypeSpecTokenTypes` is constructed with
`TypeSpecLanguage.INSTANCE`; (b) `tsp-tester` verifies it manually in `runIde`, since
`BraceHighlightingHandler`'s behaviour on plain-text PSI is unverified. If it does not light
up in `runIde`, the class stays registered and the plan notes it as "activates in M5" — we do
not remove it.

## Consequences

- M3 stands. No rework to shipped code from any of this.
- One test assertion in the M1 suite is wrong and must be amended (F1). `tsp-tester` fixes it.
- M4 is unchanged in scope except that spellchecking leaves it; it remains a pure
  lexer-only milestone.
- M5 grows a first task and a spellchecking tail, reinforcing plan 00's existing warning that
  M5 is the largest milestone and should be split at the first sign of stall.
- The `psiFile.language == PlainTextLanguage` oddity will persist and be visible in
  *PsiViewer* / *Internal Actions* until M5. Anyone reporting it as a bug should be pointed at F1.

## Citations

All against ideaIC-2025.2.6.3 (build 252.28539.97) / matching `intellij-community` sources.

- `platform/core-impl/src/com/intellij/psi/AbstractFileViewProvider.java:154`, `:157-163`
- `platform/core-impl/src/com/intellij/psi/impl/source/PsiPlainTextFileImpl.java:22-24`
- `platform/platform-impl/src/com/intellij/openapi/editor/highlighter/EditorHighlighterFactoryImpl.kt:39-70`
- `platform/testFramework/src/com/intellij/testFramework/EditorTestUtil.java:580-586`
- `LanguageUtil.getLanguageForPsi`, `SyntaxHighlighterLanguageFactory`
  (`LanguageExtension` over `com.intellij.lang.syntaxHighlighterFactory`), `LexerEditorHighlighter`
- `BraceMatchingUtil.getBraceMatcher`, `LanguageBraceMatching`, `TypedQuoteImpl.getQuoteHandler`
- `PlainTextParserDefinition` (reference minimal implementation)
- `IC/lib/modules/intellij.spellchecker.jar`; EP `spellchecker.support` in content module
  `intellij.spellchecker`; module `com.intellij.modules.spellchecker`

## Open questions for the project owner

1. Ratify the second `<depends>` on `com.intellij.modules.spellchecker` in M5 (D3). If the
   one-`<depends>` rule is meant literally, spellchecking is dropped entirely — say so and
   this ADR is amended.
2. `BraceHighlightingHandler` on plain-text PSI is unverified (D5). If a definitive answer is
   wanted before M4 starts rather than a `runIde` check after, that is another
   `tsp-intellij-researcher` round.
