# ADR 0001 — Highlighting approach: hand-written JFlex lexer

- Status: **Accepted**
- Date: 2026-09-02
- Deciders: `tsp-architect` (proposed), project owner (to ratify)
- Supersedes: —

## Context

We need TypeSpec (`.tsp`) syntax highlighting in IntelliJ IDEA **Community Edition**.
`CLAUDE.md` fixes one non-negotiable constraint:

> The only permitted `<depends>` is `com.intellij.modules.platform`. `platformType=IC`.
> Anything requiring `com.intellij.modules.ultimate`, `com.intellij.platform.lsp.*`,
> `JavaScript`, or `NodeJS` is out of scope by definition.

The prior art, `siketyan/intellij-typespec-plugin`, violates this: it declares
`com.intellij.modules.ultimate` and registers `platform.lsp.serverSupportProvider`. It is a
**feature checklist only**.

Four routes were considered.

## Options

### A. TextMate bundle only

Ship `microsoft/typespec`'s `grammars/typespec.json` as a TextMate bundle and let the
bundled TextMate plugin colour the file.

- CE-safe: yes — the TextMate plugin ships in Community.
- Effort: very low.
- Quality: mediocre. TextMate colouring in IntelliJ is coarse, does not participate in the
  language's own colour settings page, and produces **no `Language`, no `FileType`, no PSI**.
- Extensibility: **dead end**. Nothing downstream (folding, structure view, completion,
  resolve, find-usages) can be built on it. Every later milestone would require throwing
  this away and starting over.
- Extra risk: registering a TextMate bundle from a plugin is a different, less-documented
  extension surface than `lang.syntaxHighlighterFactory`, and we would be re-shipping an
  upstream file we do not control.

### B. LSP via LSP4IJ

Use the third-party **LSP4IJ** plugin as a `<depends>` and drive
`@typespec/compiler`'s language server.

- CE-safe: technically yes (LSP4IJ is a Community-compatible marketplace plugin), but it
  adds a **hard runtime dependency on a third-party plugin plus a Node.js toolchain and an
  npm-installed `tsp-server` on the user's machine**. Highlighting stops working the moment
  Node is missing.
- It also violates the *spirit* of the constraint: `CLAUDE.md` names `NodeJS` as out of
  scope, and the whole point of this project is a self-contained CE plugin.
- Effort: medium. Quality: good semantic tokens **when it runs**.
- Rejected: the failure mode ("no Node → no colours at all") is worse than the ceiling of
  a lexer, and it makes the plugin unusable for the exact audience we are targeting.

### C. JFlex lexer + Grammar-Kit parser + PSI, all at once

- CE-safe: yes. Quality: best. Effort: **high**, and it front-loads all the risk
  (grammar ambiguity around `<`/`>`, string templates, projections) before a single colour
  appears on screen.
- Rejected *as a first step*, adopted later — see Consequences.

### D. JFlex lexer + `SyntaxHighlighter`, parser deferred (**chosen**)

Highlighting in the IntelliJ Platform requires **only a lexer**. `SyntaxHighlighterBase`
maps `IElementType` to `TextAttributesKey[]`; no `ParserDefinition` is needed.

- CE-safe: `com.intellij.lang.Language`, `LanguageFileType`, `FlexAdapter`,
  `SyntaxHighlighterFactory`, `ColorSettingsPage`, `DefaultLanguageHighlighterColors` are
  all `com.intellij.modules.platform` API.
- Effort: low-medium; the whole surface is one `.flex` file plus five small Kotlin classes.
- Quality: good. Everything in the `typespec-language` token table is lexically decidable.
- Extensibility: the token types written for the lexer are exactly the token types a
  Grammar-Kit `.bnf` consumes later. Nothing is thrown away.
- Runtime dependencies: **none**.

## Decision

**Route D, staged into Route C.**

1. Ship a hand-written JFlex lexer + `SyntaxHighlighter` + `ColorSettingsPage` first
   (plan `01-lexer-and-highlighter.md`, milestones M1–M3).
2. Structure the code so a Grammar-Kit parser can be layered on top without rewriting the
   lexer: token types live in a standalone `TypeSpecTokenTypes` holder, the `.flex` lives in
   `src/main/grammars/` beside where the future `.bnf` will live, and lexer generation is a
   first-class Gradle task from day one (M2) so adding `generateParser` later is additive.
3. Grammar-Kit parser + PSI is milestone **M5**, gated on M1–M4 being green.

### Sub-decisions recorded here

| Question | Decision | Why |
|---|---|---|
| Lexer generation tooling | Invoke the **IntelliJ-patched JFlex** (`org.jetbrains.intellij.deps.jflex:jflex:1.10.17`, resolved from the `intellijDependencies()` repository) from a plain Gradle `JavaExec` task | The `org.jetbrains.grammarkit` Gradle plugin's newest release is `2023.3.0.3` and predates Gradle 9.x + configuration cache, which the current template enables. A hand-rolled `JavaExec` is ~15 lines, fully cacheable, and removes a stale third-party build dependency. See ADR 0002. |
| Generated lexer checked in? | **No.** `build/generated/` only, `.gitignore`d. | The `.flex` is the source of truth; a checked-in generated file drifts. Fallback if generation proves flaky: commit it and regenerate manually — record as an amendment. |
| String interpolation `${...}` | M1–M3: colour the **entire** literal as a string. Sub-token interpolation deferred. | Simplest correct behaviour; matches the `typespec-language` skill's guidance. Revisit at M5. |
| `<` / `>` | Lexed as plain punctuation/operator; template-vs-comparison disambiguation is a parser concern. | Not lexically decidable. |
| Reserved-but-unused keywords (`macro`, `struct`, `trait`, …) | Lexed as `KEYWORD` | They are in `scanner.ts`'s `Keywords` map; TypeSpec's own TextMate grammar colours them as keywords. |

## Consequences

**Positive**

- No runtime dependency of any kind. Works on a bare IntelliJ IDEA Community install.
- `verifyPlugin` against an IC distribution is a *real* gate, not a formality — the compile
  classpath physically lacks Ultimate classes (see ADR 0002).
- Fast feedback: `LexerTestCase` runs in milliseconds and catches most colouring bugs.
- Direct upgrade path to PSI-backed features.

**Negative / accepted costs**

- No semantic colouring in M1–M3: a model name and a variable name look the same; a
  decorator's *arguments* are not distinguished from ordinary expressions. Mitigation:
  M5/M6 add an `Annotator` on top of PSI.
- No resolve, no find-usages, no rename until M5.
- We own the grammar. TypeSpec's scanner changes upstream; we must re-verify the keyword
  set against `packages/compiler/src/core/scanner.ts` per TypeSpec minor release. Tracked
  as recurring maintenance in plan `00-milestones.md`.
- Doc comments (`/** */`) are coloured as one block. TypeSpec's real scanner emits
  `DocText`/`DocCodeSpan`/`DocCodeFenceDelimiter` sub-tokens; we deliberately do not.

## Verification of the CE claim

Every API used in M1–M3 lives in `com.intellij.modules.platform`:

`com.intellij.lang.Language`, `com.intellij.openapi.fileTypes.LanguageFileType`,
`com.intellij.psi.tree.IElementType`, `com.intellij.psi.TokenType`,
`com.intellij.lexer.FlexAdapter` / `FlexLexer`,
`com.intellij.openapi.fileTypes.SyntaxHighlighterBase` / `SyntaxHighlighterFactory`,
`com.intellij.openapi.editor.DefaultLanguageHighlighterColors`,
`com.intellij.openapi.editor.HighlighterColors`,
`com.intellij.openapi.options.colors.ColorSettingsPage`.

The enforcement mechanism is not this list — it is the IC compile classpath (ADR 0002) plus
`verifyPlugin`. If someone reaches for an Ultimate API, the build fails.
