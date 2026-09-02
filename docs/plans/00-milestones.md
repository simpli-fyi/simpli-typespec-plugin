# Milestone roadmap — TypeSpec plugin for IntelliJ IDEA Community

Governing decisions: [ADR 0001](../adr/0001-highlighting-approach.md) (JFlex lexer, parser
deferred), [ADR 0002](../adr/0002-build-and-platform-baseline.md) (IC 2025.2.6.3, JDK 21,
Gradle 9.5, IPGP 2.16.0), [ADR 0003](../adr/0003-parser-definition-timing.md)
(`ParserDefinition` is M5's first task; no placeholder; spellchecking moves to M5;
`checkHighlighting` / `EditorTestUtil.testFileSyntaxHighlighting` banned until then).

Conventions used throughout:

- Package root: `dev.tsp.intellij.typespec` (pending ratification, ADR 0002 D5).
- Source root: `src/main/kotlin/dev/tsp/intellij/typespec/`.
- Test root: `src/test/kotlin/dev/tsp/intellij/typespec/`, fixtures in `src/test/testData/`.
- One milestone = one `tsp-dev` run. `tsp-dev` never writes tests; `tsp-tester` never edits
  `src/main/`.
- **Every milestone ends green on `./gradlew build`.** A milestone that does not compile is
  not done, regardless of how much of it exists.

| # | Title | One-line goal |
|---|---|---|
| M0 | Bootstrap | Template scaffolded, pinned to IC 2025.2.6.3 on JDK 21, `build`/`runIde`/`verifyPlugin` green with zero plugin code. |
| M1 | Language + file type | `.tsp` files are recognised as a TypeSpec language file with an icon. |
| M2 | Token types + JFlex lexer | A generated, restartable lexer turns TypeSpec source into the full token set. |
| M3 | Syntax highlighter + colour settings | Tokens are coloured, and the colours are user-configurable. **← primary deliverable ships here** |
| M4 | Editor conveniences | Comment/uncomment, brace matching, quote handling, TODO comments. Spellchecking moved to M5 (ADR 0003). |
| M5 | Grammar-Kit parser + PSI | A real parse tree, unlocking everything structural. Opens with the `ParserDefinition` (ADR 0003 D1). |
| M6 | Structure view, folding, completion | The PSI-backed feature set from the prior-art checklist. |
| M7 | Compatibility + release readiness | Verified across the whole supported IDE range; CI green; metadata complete. |

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
  `org.gradle.java.home` → Zulu 21, `group = dev.tsp.intellij`, `version = 0.0.1`.
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
settings.gradle.kts                     (modify — rootProject.name = "intellij-tsp-plugin")
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
src/main/kotlin/dev/tsp/intellij/typespec/TypeSpecLanguage.kt
src/main/kotlin/dev/tsp/intellij/typespec/TypeSpecFileType.kt
src/main/kotlin/dev/tsp/intellij/typespec/TypeSpecIcons.kt
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
src/main/kotlin/dev/tsp/intellij/typespec/psi/TypeSpecTokenType.kt
src/main/kotlin/dev/tsp/intellij/typespec/psi/TypeSpecTokenTypes.kt
src/main/kotlin/dev/tsp/intellij/typespec/psi/TypeSpecTokenSets.kt
src/main/kotlin/dev/tsp/intellij/typespec/lexer/TypeSpecLexerAdapter.kt
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
src/main/kotlin/dev/tsp/intellij/typespec/highlighting/TypeSpecColors.kt
src/main/kotlin/dev/tsp/intellij/typespec/highlighting/TypeSpecSyntaxHighlighter.kt
src/main/kotlin/dev/tsp/intellij/typespec/highlighting/TypeSpecSyntaxHighlighterFactory.kt
src/main/kotlin/dev/tsp/intellij/typespec/highlighting/TypeSpecColorSettingsPage.kt
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
useless before a `ParserDefinition` exists. **No `ParserDefinition`, placeholder or
otherwise, is added in M4** (ADR 0003 D1/D2) — nothing else here needs one.

**Files**
```
src/main/kotlin/dev/tsp/intellij/typespec/editor/TypeSpecCommenter.kt
src/main/kotlin/dev/tsp/intellij/typespec/editor/TypeSpecBraceMatcher.kt
src/main/kotlin/dev/tsp/intellij/typespec/editor/TypeSpecQuoteHandler.kt
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

1. **Brace matching is likely-but-not-proven** without a `ParserDefinition`
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
   documented exception to the one-`<depends>` rule, pre-approved in ADR 0003 D3 and pending
   owner ratification. Deferred to M5 with the feature itself.
3. Correct EP for TODO highlighting in a lexer-only language. Note that once M5 registers a
   `ParserDefinition`, its `getCommentTokens()` starts feeding TODO indexing for free — so if
   this turns out to be awkward here, deferring it to M5 is cheap.

---

## M5 — Grammar-Kit parser and PSI

**Goal.** A parse tree for TypeSpec. This is where ADR 0001's staged plan cashes in.

**Task 0 (first, before any grammar work) — land the `ParserDefinition`.**
Per [ADR 0003](../adr/0003-parser-definition-timing.md) D1/D2 this was deliberately kept out
of M4. Register `TypeSpecParserDefinition` (EP `lang.parserDefinition`) with **our own**
`IFileElementType(TypeSpecLanguage)` and **our own** `TypeSpecFile : PsiFileBase` —
**never subclass `PlainTextParserDefinition`**, whose `createFile` returns
`PsiPlainTextFileImpl` and re-triggers the file-type overwrite (ADR 0003 F1/F4).

The moment this lands, three things change and `tsp-tester` must sweep for them:
- `psiFile.language` / `psiFile.fileType` become correct — the M1 assertions can be
  *tightened* (they were deliberately written against `virtualFile` / `baseLanguage`, which
  remain true either way, so nothing breaks).
- `checkHighlighting` and `EditorTestUtil.testFileSyntaxHighlighting` stop being traps and
  become usable; the ADR 0003 D4 ban lifts here.
- `getCommentTokens()` / `getWhitespaceTokens()` start driving TODO indexing, the commenter
  and whitespace logic. Get those token sets right.

**In scope.** `TypeSpec.bnf`, `generateTypeSpecParser` Gradle task,
`TypeSpecParserDefinition`, `TypeSpecFile : PsiFileBase`, `TypeSpecElementType`,
generated parser + PSI into `src/main/gen/` (or `build/generated/`), EP `lang.parserDefinition`.

**Tail task — spellchecking**, moved here from M4 (ADR 0003 D3). `TypeSpecSpellcheckingStrategy`
on EP `spellchecker.support`, plus a **second `<depends>` on `com.intellij.modules.spellchecker`**
in `plugin.xml`. This is a CE-available platform module, not an Ultimate dependency; it is the
one sanctioned exception to the one-`<depends>` rule and **needs owner ratification before
`tsp-dev` writes it**. Acceptance: `./gradlew verifyPlugin` stays clean and the plugin still
installs and loads on a bare IntelliJ IDEA Community install.

Grammar coverage, in dependency order — **do not attempt all of TypeSpec**:
`import` / `using` / `namespace` statements; `model` with properties, `extends`, `is`,
spread; `op`; `interface`; `enum`; `union`; `alias`; `scalar`; decorator applications;
type expressions (union `|`, intersection `&`, array `[]`, template args `<>`, optional `?`).
Explicitly deferred: projections, `dec`/`fn`/`extern` declarations, value literals
(`#{}`/`#[]`) beyond a coarse rule.

**Out of scope.** Reference resolution, `PsiNamedElement`/rename, indexing/stubs. Those are
a follow-on milestone if wanted.

**Acceptance.** `TypeSpecParsingTest : ParsingTestCase("parser", "tsp", TypeSpecParserDefinition())`
with a `.tsp`/`.txt` pair per grammar area. **The first run generates the `.txt`; `tsp-tester`
must read each generated tree and confirm it is correct before committing** — a wrong tree
gets frozen otherwise. Plus: re-run the whole M2/M3 test suite unchanged (the lexer contract
must not have shifted) and assert no `PsiErrorElement` in `kitchen-sink.tsp`.

**Verification**
```bash
./gradlew clean build test verifyPlugin
```

**Risks.** `<`/`>` template-argument ambiguity is the known hard part; expect to need
Grammar-Kit external rules or `pin`/recover attributes. Grammar-Kit's Gradle integration has
the same staleness problem as JFlex (ADR 0002 D6) — plan on a `JavaExec` task, and budget
this milestone as the largest by a wide margin. **Split it if the first `tsp-dev` run
stalls**: M5a = task 0 (`ParserDefinition` + file element) + a grammar covering only
`import`/`using`/`namespace`/`model`; M5b = the rest of the grammar plus the spellchecking
tail. Even in a split, M5a ships a **real** `ParserDefinition` over a partial grammar — never
a `parseContents`-emits-one-leaf stub as a resting state (ADR 0003 D2).

---

## M6 — Structure view, folding, completion, semantic colouring

**Goal.** The feature checklist from `siketyan/intellij-typespec-plugin`, delivered on our
own PSI.

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
