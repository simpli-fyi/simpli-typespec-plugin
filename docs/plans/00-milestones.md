# Milestone roadmap — TypeSpec plugin for IntelliJ IDEA Community

Governing decisions: [ADR 0001](../adr/0001-highlighting-approach.md) (JFlex lexer, parser
deferred), [ADR 0002](../adr/0002-build-and-platform-baseline.md) (IC 2025.2.6.3, JDK 21,
Gradle 9.5, IPGP 2.16.0 — **D6 superseded by ADR 0006**),
[ADR 0003](../adr/0003-parser-definition-timing.md)
(`ParserDefinition` is M5's first task; no placeholder; spellchecking moves to M5;
`checkHighlighting` / `EditorTestUtil.testFileSyntaxHighlighting` banned until then),
[ADR 0004](../adr/0004-reference-resolution-approach.md) (references are `PsiReference` on our
own PSI; resolution is a cached, word-index-prefiltered tree walk; **D7 imposes four PSI-shape
requirements on M5b**; navigation is M5.5, rename/Go-To-Symbol/stubs are M6.5),
[ADR 0005](../adr/0005-minimal-parser-definition-for-commenting.md) (**amends ADR 0003**: a
minimal flat `ParserDefinition` lands in M4 as M4b, because `lang.commenter` resolves by file
language and therefore cannot work on plain-text PSI),
[ADR 0006](../adr/0006-grammar-toolchain.md) (**supersedes ADR 0002 D6**: adopt the IPGP
`grammarkit` subplugin, bump IPGP to 2.18.1, bridge the hand-written lexer's tokens into the
BNF via `tokenTypeFactory`, design error recovery in from the first rule).

Conventions used throughout:

- Package root: `simpli.fyi.plugins.typespec` (pending ratification, ADR 0002 D5).
- Source root: `src/main/kotlin/simpli/fyi/plugins/typespec/`.
- Test root: `src/test/kotlin/simpli/fyi/plugins/typespec/`, fixtures in `src/test/testData/`.
- One milestone = one `tsp-dev` run. `tsp-dev` never writes tests; `tsp-tester` never edits
  `src/main/`.
- **Every milestone ends green on `./gradlew build`.** A milestone that does not compile is
  not done, regardless of how much of it exists.

| # | Title | One-line goal | Status |
|---|---|---|---|
| M0 | Bootstrap | Template scaffolded, pinned to IC 2025.2.6.3 on JDK 21, `build`/`runIde`/`verifyPlugin` green with zero plugin code. | ✅ `6a2ceec` |
| M1 | Language + file type | `.tsp` files are recognised as a TypeSpec language file with an icon. | ✅ `46b4422` |
| M2 | Token types + JFlex lexer | A generated, restartable lexer turns TypeSpec source into the full token set. | ✅ `c7a9efe` |
| M3 | Syntax highlighter + colour settings | Tokens are coloured, and the colours are user-configurable. **← primary deliverable ships here** | ✅ `d9e5e74` |
| M4 | Editor conveniences | Comment/uncomment, brace matching, quote handling, TODO comments. Spellchecking moved to M5 (ADR 0003). | ✅ `3c4c84c` |
| M4b | Minimal flat `ParserDefinition` | `.tsp` gets real `TypeSpecFile` PSI so M4's language-keyed EPs resolve. No grammar (ADR 0005). | ✅ `ce92694` |
| **M5a** | **Grammar-Kit toolchain** | **IPGP 2.18.1 + `grammarkit` subplugin; `generateParser` runs and reuses the hand-written lexer's tokens. No TypeSpec grammar. (ADR 0006)** | **← NEXT** |
| M5b | Core grammar + PSI contract | Real tree for file/`import`/`using`/`namespace`/`model`; `TypeSpecIdentifier`, `TypeSpecQualifiedName`, `PsiNameIdentifierOwner` everywhere (ADR 0004 D7). Flat parser deleted. | planned |
| M5c | Remaining grammar | `op`/`interface`/`enum`/`union`/`alias`/`scalar`, decorators, type expressions. Spellchecking tail (**ratified**). `KitchenSinkCore.tsp` (the M5c-scoped subset) parses clean. | planned |
| M5d | Deferred-construct sweep | `const` + value expressions, `#suppress`/`#deprecated` directives, function-type expressions + `dec`/`fn`/`extern`. Full unmodified `kitchen-sink.tsp` parses clean. **Not a prerequisite for M5.5.** | scoped, not planned |
| M5.5 | Reference resolution + navigation | Ctrl-click / Go To Declaration / Find Usages on a type reference. (ADR 0004, [plan 02](02-navigation.md)) | planned |
| M6 | Structure view, folding, completion | The PSI-backed feature set from the prior-art checklist. Annotator + completion build on M5.5's resolver. | planned |
| M6.5 | Rename, Go To Symbol, stub index | The write-side refactoring and the index that makes project-wide symbol search affordable. (ADR 0004 D6) | planned |
| M7 | Compatibility + release readiness | Verified across the whole supported IDE range; CI green; metadata complete. | planned |

Detail plans: [01](01-lexer-and-highlighter.md) (M1–M3), [03](03-grammar-and-psi.md)
(M5a–M5d), [02](02-navigation.md) (M5.5). **Plan file numbers are allocation order, not
milestone order** — plan 03 runs before plan 02.

M5 was a single milestone; it is split into M5a/M5b/M5c (+M5d) by
[plan 03](03-grammar-and-psi.md), using the split authorisation the original §M5 already
granted. M5.5 and M6.5 are decimal by [ADR 0004](../adr/0004-reference-resolution-approach.md)
D5 — renumbering M6/M7 would silently falsify ADR 0002 and ADR 0003, which cite them by
number.

Ongoing, not a milestone: **re-verify the keyword set against
`microsoft/typespec` → `packages/compiler/src/core/scanner.ts` on each TypeSpec minor
release.** ADR 0001 accepts that we own a copy of the grammar.

---

## M0 — Bootstrap

**Goal.** A green, empty, correctly-configured plugin project. No TypeSpec code at all.

**In scope**
- Clone `JetBrains/intellij-platform-plugin-template` (v2.6.0), drop its git history,
  `git init -b main`.
- Delete template-only leftovers: `.github/workflows/template-cleanup.yml`,
  `.github/workflows/template-verify.yml`, `.github/template-cleanup/`,
  `.github/readme/`, `CODE_OF_CONDUCT.md` (keep `LICENSE`, `CHANGELOG.md`).
- Delete the template's demo code: `MyBundle.kt`, `services/MyProjectService.kt`,
  `startup/MyProjectActivity.kt`, `toolWindow/MyToolWindowFactory.kt`,
  `src/main/resources/messages/MyBundle.properties`,
  `src/test/kotlin/.../MyPluginTest.kt`, `src/test/testData/rename/`.
  Remove the corresponding `<extensions>` entries and `<resource-bundle>` from `plugin.xml`.
- Apply ADR 0002: `intellijIdeaCommunity("2025.2.6.3")`, `sinceBuild = "252"`,
  `untilBuild = provider { null }`, `kotlin { jvmToolchain(21) }`,
  `org.gradle.java.home` → Zulu 21, `group = simpli.fyi`, `version = 0.0.1`.
- `plugin.xml`: id / name / vendor / description, `<depends>com.intellij.modules.platform</depends>`
  and nothing else, empty `<extensions>` block.
- `pluginVerification { ides { ide(IntellijIdeaCommunity, "2025.2.6.3") } }`.
- Update `.claude/skills/tsp-plugin-bootstrap/SKILL.md` §3/§5 to match ADR 0002 (its
  property table describes a template revision that no longer exists).
- `.gitignore`: ensure `build/`, `.gradle/`, and `src/main/gen/` are ignored.

**Out of scope.** Any `Language`, `FileType`, lexer, or highlighter class. Marketplace
signing/publishing config. CI changes beyond deleting the two dead workflows.

**Files to create/modify**
```
build.gradle.kts                        (modify)
settings.gradle.kts                     (modify — rootProject.name = "simpli-typespec-highlighter")
gradle.properties                       (modify)
src/main/resources/META-INF/plugin.xml  (modify)
.gitignore                              (modify)
CHANGELOG.md                            (modify — reset to 0.0.1 Unreleased)
README.md                               (rewrite — replace template README)
.claude/skills/tsp-plugin-bootstrap/SKILL.md (modify)
```

**Acceptance (`tsp-tester`)**
- `./gradlew build` succeeds from a clean `build/`.
- `./gradlew verifyPlugin` reports no compatibility problems.
- Assert by inspection and record verbatim in the report:
  - `plugin.xml` contains exactly one `<depends>`, and it is `com.intellij.modules.platform`.
  - `grep -ri "modules.ultimate\|platform.lsp\|NodeJS\|JavaScript" src/ build.gradle.kts` is empty.
  - `./gradlew dependencies` / the resolved IntelliJ Platform is `ideaIC`, not `ideaIU`.
- `./gradlew runIde` launches, and the About dialog says **IntelliJ IDEA Community Edition
  2025.2.6.3**. (Manual; report the actual outcome.)

**Verification command**
```bash
./gradlew clean build verifyPlugin
```

**Risks / open questions**
- Gradle 9.5 on JDK 26 may refuse to start; `org.gradle.java.home` is the fix and is part of
  this milestone, not a workaround.
- The template enables the **configuration cache**. If any later milestone's task is not
  config-cache compatible, prefer fixing the task over disabling the cache; if you must
  disable it, say so loudly.
- ADR 0002 D5 open questions (plugin id, vendor) — use the proposed values, flag them in the
  report, do not block.

---

## M1 — Language registration and file type

**Goal.** IntelliJ recognises `.tsp` as its own language. No colours yet.

**In scope.** `TypeSpecLanguage`, `TypeSpecFileType`, `TypeSpecIcons`, a 16×16 SVG icon,
and the `<fileType>` registration.

**Out of scope.** Lexer, token types, highlighter, parser.

Detail: [`01-lexer-and-highlighter.md` § M1](01-lexer-and-highlighter.md).

**Files**
```
src/main/kotlin/simpli/fyi/plugins/typespec/TypeSpecLanguage.kt
src/main/kotlin/simpli/fyi/plugins/typespec/TypeSpecFileType.kt
src/main/kotlin/simpli/fyi/plugins/typespec/TypeSpecIcons.kt
src/main/resources/icons/typespec.svg
src/main/resources/META-INF/plugin.xml            (modify)
```

**Acceptance.** `TypeSpecFileTypeTest`:
- `FileTypeManager.getInstance().getFileTypeByExtension("tsp")` is `TypeSpecFileType.INSTANCE`.
- `myFixture.configureByText("a.tsp", "")` → `file.virtualFile.fileType` is
  `TypeSpecFileType.INSTANCE` and `file.viewProvider.baseLanguage` is `TypeSpecLanguage.INSTANCE`.
  **Not** `file.language` / `file.fileType` — those are plain text until M5 by design
  ([ADR 0003](../adr/0003-parser-definition-timing.md) F1).
- `TypeSpecFileType.INSTANCE.defaultExtension == "tsp"` and `icon != null`.

**Verification**
```bash
./gradlew build test
```

**Risks.** Icon must be a real 16×16 SVG; a malformed one fails at runtime, not compile
time. `runIde` is the only way to confirm it renders.

---

## M2 — Token types and JFlex lexer

**Goal.** `TypeSpecLexerAdapter` produces the complete TypeSpec token stream, is
restartable, and never fails to advance.

**In scope.** `TypeSpecTokenType`, `TypeSpecTokenTypes`, `TypeSpecTokenSets`,
`_TypeSpecLexer.flex`, `TypeSpecLexerAdapter`, and the Gradle `generateTypeSpecLexer` task
(ADR 0002 D6).

**Out of scope.** Any mapping to colours. Any `ParserDefinition`.

Detail: [`01-lexer-and-highlighter.md` § M2](01-lexer-and-highlighter.md).

**Files**
```
build.gradle.kts                                                (modify — jflex config + JavaExec task)
src/main/grammars/_TypeSpecLexer.flex
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecTokenType.kt
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecTokenTypes.kt
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecTokenSets.kt
src/main/kotlin/simpli/fyi/plugins/typespec/lexer/TypeSpecLexerAdapter.kt
```

**Acceptance.** `TypeSpecLexerTest` (extends `LexerTestCase`), one case per row of the token
table in plan 01, plus the trap cases: `@@doc`, backtick identifier, `"""…"""`,
`#suppress`, `#{`/`#[`, unterminated `"`, unterminated `/*`, `...` vs `.`, `0x1f`/`0b1010`,
`1.5e-3`, EOF inside every lexer state, and a full-file lex of `src/test/testData/lexer/kitchen-sink.tsp`
that reaches EOF with **zero** `BAD_CHARACTER` tokens.

**Verification**
```bash
./gradlew build test --tests '*TypeSpecLexerTest*'
```

**Risks.** A JFlex rule that can match the empty string hangs the editor. The
kitchen-sink-to-EOF test is the guard. Configuration-cache compatibility of the JavaExec
task — see ADR 0002 D6 fallback.

---

## M3 — Syntax highlighter and colour settings page

**Goal.** The primary deliverable: `.tsp` files are coloured, in every theme, and the
colours are configurable under *Settings | Editor | Color Scheme | TypeSpec*.

**In scope.** `TypeSpecSyntaxHighlighter`, `TypeSpecSyntaxHighlighterFactory`,
`TypeSpecColorSettingsPage`, the two `plugin.xml` registrations, and a demo snippet resource.

**Out of scope.** `Annotator`-based semantic colouring (needs PSI → M6).

Detail: [`01-lexer-and-highlighter.md` § M3](01-lexer-and-highlighter.md).

**Files**
```
src/main/kotlin/simpli/fyi/plugins/typespec/highlighting/TypeSpecColors.kt
src/main/kotlin/simpli/fyi/plugins/typespec/highlighting/TypeSpecSyntaxHighlighter.kt
src/main/kotlin/simpli/fyi/plugins/typespec/highlighting/TypeSpecSyntaxHighlighterFactory.kt
src/main/kotlin/simpli/fyi/plugins/typespec/highlighting/TypeSpecColorSettingsPage.kt
src/main/resources/META-INF/plugin.xml                       (modify)
```

**Acceptance.** `TypeSpecSyntaxHighlighterTest`:
- For every `IElementType` in `TypeSpecTokenTypes`, `getTokenHighlights` returns a non-empty
  array. (Guards the "new token added, forgot to colour it" regression.)
- Every `AttributesDescriptor` key on the colour settings page is one the highlighter can
  actually return, and vice versa — the two sets are equal.
- `TypeSpecColorSettingsPage.getHighlighter()` is a `TypeSpecSyntaxHighlighter`, and its
  demo text lexes to EOF with no `BAD_CHARACTER`.
- `TypeSpecHighlightingTest.testBasic()` — `myFixture.configureByFile("highlighting/basic.tsp")`,
  then drive `EditorHighlighterFactory.getInstance().createEditorHighlighter(virtualFile, scheme, project)`
  and iterate. **Do not use `checkHighlighting` or `EditorTestUtil.testFileSyntaxHighlighting`** —
  both are vacuous or silently wrong without a `ParserDefinition`
  ([ADR 0003](../adr/0003-parser-definition-timing.md) F3/D4, detail in plan 01 §M3).

**Verification**
```bash
./gradlew clean build test verifyPlugin
```
plus manual `./gradlew runIde`: open a `.tsp` file, confirm colouring, toggle Light ↔ Darcula
and confirm the `DefaultLanguageHighlighterColors` fallbacks hold.

**Risks.** Colour *choices* are subjective and cannot be unit-tested; the runIde screenshot
check is the real acceptance. Decorator/directive both falling back to `METADATA` may look
flat — open cosmetic question in plan 01.

---

## M4 — Editor conveniences (still lexer-only)

**Goal.** The cheap ergonomics that make the plugin feel finished, none of which need PSI.

**In scope**
- `TypeSpecCommenter : Commenter` — `//`, `/* */`. EP `lang.commenter`.
- `TypeSpecBraceMatcher : PairedBraceMatcher` for `{}`, `()`, `[]`. EP `lang.braceMatcher`.
  Ships **at risk** — see risk 1 below.
- `TypeSpecQuoteHandler : SimpleTokenSetQuoteHandler`. EP `lang.quoteHandler`. Verified to
  work without a `ParserDefinition` (ADR 0003 F5).
- TODO/comment indexing so `// TODO` shows up: verify whether this needs
  `IdIndexer`/`TodoIndexer` registration or comes free from the syntax highlighter's
  comment token set.

**Out of scope.** Folding (wants PSI → M6). **Spellchecking — moved to M5**
([ADR 0003](../adr/0003-parser-definition-timing.md) D3): the inspection walks PSI, so it is
useless before a `ParserDefinition` exists. ~~**No `ParserDefinition`, placeholder or
otherwise, is added in M4** (ADR 0003 D1/D2) — nothing else here needs one.~~
**Overturned by [ADR 0005](../adr/0005-minimal-parser-definition-for-commenting.md):**
`lang.commenter` *does* need one, so a minimal flat `ParserDefinition` lands here as **M4b**.
Still out of scope in M4b: any BNF/grammar, any real PSI element hierarchy, spellchecking.

**Files**
```
src/main/kotlin/simpli/fyi/plugins/typespec/editor/TypeSpecCommenter.kt
src/main/kotlin/simpli/fyi/plugins/typespec/editor/TypeSpecBraceMatcher.kt
src/main/kotlin/simpli/fyi/plugins/typespec/editor/TypeSpecQuoteHandler.kt
src/main/resources/META-INF/plugin.xml   (modify)
```

**Acceptance.** `TypeSpecCommenterTest` using `myFixture.performEditorAction(
IdeActions.ACTION_COMMENT_LINE / ACTION_COMMENT_BLOCK)` with before/after fixtures;
`TypeSpecBraceMatcherTest` asserting the pair list and `isPairedBracesAllowedBeforeType`.

**Verification**
```bash
./gradlew build test verifyPlugin
```

**Risks / open questions**

0. **RESOLVED THE HARD WAY.** M4 shipped (commit `3c4c84c`) and all 6 `TypeSpecCommenterTest`
   cases failed: language-keyed EPs do not resolve on plain-text PSI. See
   [ADR 0005](../adr/0005-minimal-parser-definition-for-commenting.md). Risk 1 below is
   superseded by ADR 0005 D4 — brace matching is no longer "at risk", it is re-verified with
   an automated test after M4b.
1. ~~**Brace matching is likely-but-not-proven** without a `ParserDefinition`~~
   ([ADR 0003](../adr/0003-parser-definition-timing.md) F5/D5).
   `BraceMatchingUtil.getBraceMatcher` keys off `IElementType.getLanguage()` from the lexer's
   tokens and resolves via `LanguageBraceMatching.forLanguage` *before* the FileType
   fallback — so it should work, **but only if every `IElementType` in `TypeSpecTokenTypes`
   is constructed with `TypeSpecLanguage.INSTANCE`**; `tsp-dev` must confirm that. Whether
   `BraceHighlightingHandler` early-returns on plain-text PSI could not be verified.
   Mitigation: `tsp-tester` checks it manually in `runIde`. If the brace does not light up,
   **keep the class registered** and record "activates in M5" — do not remove it, and do not
   add a `ParserDefinition` to force it.
2. ~~Is `SpellcheckingStrategy` in the platform module or a bundled plugin?~~ **Resolved:**
   it *is* in Community (`IC/lib/modules/intellij.spellchecker.jar`, EP `spellchecker.support`)
   but lives in module `com.intellij.modules.spellchecker`, needing a **second `<depends>`**.
   That is a CE-available platform module, not an Ultimate dependency — a deliberate,
   documented exception to the one-`<depends>` rule. ✅ **Owner-ratified 2026-09-02 and
   CE-availability re-verified directly against the pinned `IC-252.28539.97`**
   ([ADR 0003](../adr/0003-parser-definition-timing.md) D3 CLOSED,
   [ADR 0006](../adr/0006-grammar-toolchain.md) F10). Deferred to **M5c** with the feature itself.
3. Correct EP for TODO highlighting in a lexer-only language. Note that once M5 registers a
   `ParserDefinition`, its `getCommentTokens()` starts feeding TODO indexing for free — so if
   this turns out to be awkward here, deferring it to M5 is cheap.

---

## M4b — Minimal flat `ParserDefinition` (unblocks M4's commenter)

**Goal.** `.tsp` files get a real `TypeSpecFile` PSI whose language is TypeSpec, so every
language-keyed EP registered in M4 actually resolves. No grammar.

Governing decision: [ADR 0005](../adr/0005-minimal-parser-definition-for-commenting.md)
(Option A chosen; Option B — `SelfManagingCommenter` + `multiLangCommenter` — rejected
outright and must not be implemented).

**Files**
```
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecFile.kt              (create)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecElementTypes.kt      (create — FILE type)
src/main/kotlin/simpli/fyi/plugins/typespec/parser/TypeSpecParserDefinition.kt (create)
src/main/kotlin/simpli/fyi/plugins/typespec/parser/TypeSpecFlatParser.kt     (create)
src/main/resources/META-INF/plugin.xml                                       (modify)
src/main/kotlin/simpli/fyi/plugins/typespec/editor/TypeSpecCommenter.kt      (modify — KDoc only)
```

**Approach.** Exactly as specified in ADR 0005 § "What `tsp-dev` implements (M4b)".
Never subclass `PlainTextParserDefinition` (ADR 0003 F4).

**Acceptance.** ADR 0005 § Acceptance: new `TypeSpecParserDefinitionTest`, plus repair and
re-run of `TypeSpecCommenterTest` (6/6 green), `TypeSpecQuoteHandlerTest`,
`TypeSpecBraceMatcherTest`, `TypeSpecFileTypeTest`; highlighting suites' assertions unchanged
and still passing.

**Done when**
```bash
./gradlew clean build test verifyPlugin
```

---

## M5 — Grammar-Kit parser and PSI → **split into M5a / M5b / M5c (+ M5d)**

**Goal.** A parse tree for TypeSpec. This is where ADR 0001's staged plan cashes in, and
what unblocks M5.5, M6 and M6.5.

**This section is now a summary. The executable plan is
[`03-grammar-and-psi.md`](03-grammar-and-psi.md)** — go there for files, approach, acceptance
and done-signals. What follows is the roadmap-level record of how M5 changed shape.

**Split (decided up front, 2026-09-02).** The original §M5 authorised a split *"if the first
`tsp-dev` run stalls."* It is split before starting instead, because three of its reasons are
visible now rather than after a stall: the toolchain change is not grammar work and can fail
on its own; ADR 0006 F8's `mixin=` unknown is blocking for the grammar's *architecture*; and
the full grammar plus the ADR 0004 D7 contract plus a golden per area is past one `tsp-dev`
run.

| | Scope |
|---|---|
| **M5a** | IPGP `2.16.0 → 2.18.1` (**ratified**); apply `org.jetbrains.intellij.platform.grammarkit`; migrate the hand-rolled JFlex `JavaExec` to `generateLexer`; add `generateParser` (**ADR 0006 D2's explicit wiring — `srcDir(taskProvider)` is broken on 2.18.1, F9**); bridge M2's `TypeSpecTokenTypes` into the BNF via `tokenTypeFactory`; verify the Kotlin-mixin/generated-Java seam (**the `mixin=` spike is cancelled — D7 is decided**). **No TypeSpec grammar.** ([ADR 0006](../adr/0006-grammar-toolchain.md)) |
| **M5b** | Grammar for file / `import` / `using` / `namespace` / `model`. `TypeSpecIdentifier` + `TypeSpecQualifiedName`. Every declaration a `PsiNameIdentifierOwner` with correct `getTextOffset()`. `TypeSpecFile` accessors. Swap `createParser`/`createElement`; **delete `TypeSpecFlatParser`**. ([ADR 0004](../adr/0004-reference-resolution-approach.md) D7) |
| **M5c** | `op`, `interface`, `enum`, `union`, `alias`, `scalar`, decorator applications (incl. `@@` augment statements), type expressions (union / intersection / array / template arguments / `void`,`never`,`unknown`), coarse `#{}`/`#[]`. Spellchecking tail. **`src/test/testData/parser/KitchenSinkCore.tsp`** — the M5c-achievable *subset* of the M2 lexer fixture — parses with zero `PsiErrorElement`. ([plan 03](03-grammar-and-psi.md) §M5c "Kitchen-sink scope resolution") |
| **M5d** | The three constructs M5c defers because they are out of plan 00 §M5's declared scope: `const` declarations + a real value-expression grammar; statement-level `#suppress`/`#deprecated` directives; function-type expressions `(p: T) => R` with the `dec`/`fn`/`extern` bucket. Done-signal: the **unmodified** `src/test/testData/lexer/kitchen-sink.tsp` parses with zero `PsiErrorElement`. Runs before or after M5.5 at the owner's discretion. |

**Task 0 — ~~land~~ *replace* the `ParserDefinition`.** ~~Per
[ADR 0003](../adr/0003-parser-definition-timing.md) D1/D2 this was deliberately kept out of
M4.~~ **Superseded:** M4b already landed `TypeSpecParserDefinition`, `TypeSpecFile` and the
flat `TypeSpecFlatParser`
([ADR 0005](../adr/0005-minimal-parser-definition-for-commenting.md) D1). Task 0 is now a
two-line swap in **M5b**: `createParser` → the generated `TypeSpecParser`, `createElement` →
`TypeSpecTypes.Factory.createElement(node)`. The `ParserDefinition` *shape*, the
`IFileElementType` **instance** and `TypeSpecFile` all stay exactly as they are
([ADR 0006](../adr/0006-grammar-toolchain.md) F7). **Never subclass
`PlainTextParserDefinition`** (ADR 0003 F1/F4) — still binding.

**Three things change the moment M5b lands, and `tsp-tester` must sweep for them:**
- `psiFile.language` / `psiFile.fileType` become correct — the M1 assertions can be
  *tightened* (they were deliberately written against `virtualFile` / `baseLanguage`, which
  remain true either way, so nothing breaks).
- `checkHighlighting` and `EditorTestUtil.testFileSyntaxHighlighting` stop being traps and
  become usable; the ADR 0003 D4 ban lifts **in M5b**. Lifting the ban is not a mandate to
  rewrite green tests.
- `getCommentTokens()` / `getWhitespaceTokens()` start driving TODO indexing, the commenter
  and whitespace logic. Get those token sets right.

**Toolchain, changed.** [ADR 0006](../adr/0006-grammar-toolchain.md) **supersedes
[ADR 0002](../adr/0002-build-and-platform-baseline.md) D6**: the standalone
`org.jetbrains.grammarkit` plugin is archived, and the maintained path is the IPGP subplugin
`org.jetbrains.intellij.platform.grammarkit`. The hand-rolled JFlex `JavaExec` task is
retired in M5a rather than being extended to `generateParser` — one job, one mechanism.

**PSI contract, added.** [ADR 0004](../adr/0004-reference-resolution-approach.md) D7 imposes
four requirements that M5 as originally written would have missed, and that are expensive to
retrofit after the grammar ships: per-segment `TypeSpecIdentifier` /
`TypeSpecQualifiedName` nodes used *uniformly* in every naming position;
`PsiNameIdentifierOwner` on every declaration; `getName()`/`getNameIdentifier()`/
`getTextOffset()` as part of **M5b's** contract (not M5.5's); and four `TypeSpecFile`
accessors. All four are now binding requirements of M5b with their own acceptance test.

**Tail task — spellchecking**, moved here from M4 (ADR 0003 D3), lands in **M5c**.
`TypeSpecSpellcheckingStrategy` on EP `spellchecker.support`, plus a **second `<depends>` on
`com.intellij.modules.spellchecker`**. That is a CE-available platform module, not an Ultimate
dependency; it is the one sanctioned exception to the one-`<depends>` rule and is ✅
**owner-ratified and CE-confirmed** ([ADR 0003](../adr/0003-parser-definition-timing.md) D3,
[ADR 0006](../adr/0006-grammar-toolchain.md) F10). `tsp-dev` writes it in M5c without further
ratification. The plugin then ships exactly **two** `<depends>`; a third needs a new ADR.

**Grammar coverage** — **do not attempt all of TypeSpec.** Explicitly deferred: projections,
`dec`/`fn`/`extern` declarations, value literals (`#{}`/`#[]`) beyond a coarse rule.

**Out of scope, still.** Reference resolution and rename — but they are no longer "a
follow-on milestone if wanted": they are **M5.5** ([plan 02](02-navigation.md)) and **M6.5**,
scheduled by [ADR 0004](../adr/0004-reference-resolution-approach.md) D5/D6.

**Verification** (each of M5a/M5b/M5c/M5d independently)
```bash
./gradlew clean build test verifyPlugin
```

**Risks.** ~~`<`/`>` template-argument ambiguity is the known hard part.~~ **CLOSED** —
upstream TypeSpec (`parser.ts`, `spec.emu.html`) defines no relational/comparison expression
grammar, so `<`/`>` are unambiguous bracket delimiters; plain recursive descent with `pin=1`
suffices ([plan 03](03-grammar-and-psi.md) §M5c). M5c's real landmine is the keywordized
`void`/`never`/`unknown` intrinsics, which need explicit type-expression alternatives.
Template arguments remain confined to **M5c**.
Error recovery is designed in from the first rule
([ADR 0006](../adr/0006-grammar-toolchain.md) D6), not retrofitted. Golden parse trees must
be **read**, not merely regenerated ([ADR 0006](../adr/0006-grammar-toolchain.md) D8), and
`isCheckNoPsiEventsOnReparse()` must never be overridden (D9). Never leave a
`parseContents`-emits-one-leaf stub as a resting state (ADR 0003 D2) — M5b deletes it.
---

## M5.5 — Reference resolution and jump navigation

**Goal.** Ctrl/Cmd-click, *Go To Declaration* and *Find Usages* on a TypeSpec type reference
land on its declaration.

Governed by [ADR 0004](../adr/0004-reference-resolution-approach.md). Executable plan:
[`02-navigation.md`](02-navigation.md). **Prerequisite: M5c green**, including ADR 0004 D7's
four PSI-shape requirements (which M5b implements and `TypeSpecPsiContractTest` asserts).

References are `PsiReference` on our own PSI — **not** `psi.referenceContributor` (that EP is
for PSI you do not own), **not** `gotoDeclarationHandler`, **not** the still-experimental
`com.intellij.model` Symbol API. Resolution is a three-tier cached tree walk (current file →
import closure → project widening prefiltered by the word index), hard-capped at 50 candidate
files. All references are **soft** — TypeSpec's built-ins and library types are declared
nowhere in user sources and must not paint the file red.

Ships with Find Usages (`lang.findUsagesProvider`). **Rename and Go To Symbol do not** — they
are M6.5 (ADR 0004 D6). Known, recorded gaps: `@decorator` navigation (blocked by M2's
single-token `DECORATOR` design, ADR 0004 F6/open question 1) and library-type navigation
(ADR 0004 open question 2).

---

## M6 — Structure view, folding, completion, semantic colouring

**Goal.** The feature checklist from `siketyan/intellij-typespec-plugin`, delivered on our
own PSI.

**Depends on M5.5, not just M5c.** The annotator's "declaration name vs reference, resolved
vs unresolved" distinction *is* M5.5's resolver, and the completion contributor's in-scope
type names are `TypeSpecReference.getVariants()` over the same scope walk. Doing M6 first
means writing both blind and rewriting them
([ADR 0004](../adr/0004-reference-resolution-approach.md) D5).

**In scope**, each independently shippable — take them one at a time:
- `TypeSpecStructureViewFactory` + `StructureViewModel` (EP `lang.psiStructureViewFactory`).
- `TypeSpecFoldingBuilder : FoldingBuilderEx` for `{}` blocks, block comments, `import`
  runs (EP `lang.foldingBuilder`).
- `TypeSpecAnnotator` — semantic colouring: declaration names vs references, known vs
  unknown decorators (EP `annotator`).
- `TypeSpecCompletionContributor` — keywords first, then built-in scalars
  (`string`, `int32`, `boolean`, …) (EP `completion.contributor`).
- Breadcrumbs (`breadcrumbsInfoProvider`).

**Acceptance.** `myFixture.testStructureView { }`; `myFixture.testFolding(path)` with
`<fold text='...'>` fixtures; `myFixture.complete(CompletionType.BASIC)` +
`assertContainsElements(myFixture.lookupElementStrings, ...)`; annotator fixtures with
`<info descr="...">` tags.

**Verification**
```bash
./gradlew build test verifyPlugin
```

---

## M6.5 — Rename, Go To Symbol, stub index

**Goal.** The write-side refactoring, plus the index that makes project-wide symbol search
affordable.

Governed by [ADR 0004](../adr/0004-reference-resolution-approach.md) D6. Not yet planned in
detail — it gets its own plan doc when M5.5 ships.

**In scope.** `setName()` backed by a `TypeSpecElementFactory` (parse a throwaway file, lift
the identifier node); a `NamesValidator`; correct backtick-escaping when the new name is a
TypeSpec keyword or contains spaces; `lang.elementManipulator` (register it **before**
attempting rename); `gotoSymbolContributor` / `ChooseByNameContributorEx`; and the stub index
(`IStubFileElementType` + hand-written stub classes + a `getStubVersion()` constant) that Go
To Symbol is gated on.

**Why it is separate.** Rename is the one thing in this roadmap that can **corrupt a user's
source**; it deserves its own risk surface and its own tests. Go To Symbol is called per
keystroke over the whole project — without stubs that means repeated parsing, exactly the
failure mode M5.5's 50-file cap exists to avoid.

**Trigger for the stub index** (ADR 0004 D2's table): projects over ~1000 `.tsp` files, or
very common segment names (`Name`, `Id`, `Error`) where the word prefilter stops
discriminating and M5.5's cap starts firing.

---

## M7 — Compatibility and release readiness

**Goal.** Prove the CE constraint holds across the whole supported range and make the repo
publishable if the owner chooses to.

**In scope**
- Widen `pluginVerification.ides` to ADR 0002 D3's full list (2025.2, 2025.3, 2026.1, 2026.2)
  and get every one clean.
- Confirm the plugin loads and colours correctly in the *unified* 2025.3+ IDEA — the one
  scenario the IC compile classpath cannot prove.
- README with screenshots (Light + Darcula), feature list, and an explicit
  "works on Community Edition" statement.
- `CHANGELOG.md` populated; `gradle-changelog-plugin` wired to `patchChangelog`.
- CI: `.github/workflows/build.yml` green on JDK 21.
- Settle ADR 0002's open questions; only then decide on `release.yml` / signing / publishing.

**Acceptance.** `./gradlew verifyPlugin` clean on all four IDEs. Zero verifier reports
mentioning a non-`com.intellij.modules.platform` class.

**Verification**
```bash
./gradlew clean build test verifyPlugin
```

**Open questions.** All five in ADR 0002 must be answered by the owner before this milestone
can complete.
