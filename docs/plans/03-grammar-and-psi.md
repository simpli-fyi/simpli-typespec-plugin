# Plan 03 — Grammar-Kit parser and PSI (M5a → M5c, + M5d scoped)

> **Plan numbering is allocation order, not milestone order.** `02-navigation.md` (M5.5) was
> written first but runs *after* this plan. Read this one first.
>
> **POST-M5c AUDIT, 2026-09-02 — §M5d is SUPERSEDED by [plan 04](04-grammar-corrections.md).**
> **Update 2026-09-03: plan 04 is complete, and §M5d is now formally CLOSED** — see the
> close note on that section for where each of its three constructs actually landed.
> M5a/M5b/M5c all landed as specified and their tests pass, but the acceptance oracle they
> were closed against was self-authored. Measured against a real production TypeSpec
> repository, the shipped grammar produces `PsiErrorElement`s in **73 of 106 files**,
> including every substantive file in the owner's own repo. The largest gap — a decorator on
> a model member (`@key id: string;`, 366 corpus occurrences) — was inside §M5c's declared
> scope ("decorator applications") and shipped broken; decorators were attached to every
> declaration rule *except* `model_property`. §M5c's withdrawal of the full-`kitchen-sink.tsp`
> criterion in favour of an implementer-chosen subset is identified as the specific point at
> which the project lost the ability to detect its own gaps. See
> [ADR 0007](../adr/0007-corpus-driven-grammar-acceptance.md) for the root cause and the
> replacement oracle, and [plan 04](04-grammar-corrections.md) for the corrective sequence
> that replaces §M5d: **M6a → M6b → M6c → M5.5 (navigation) → M6d → M6e → M6f**, with the
> owner's repository reaching zero parse errors at M6c and navigation deliberately
> sequenced ahead of the TypeSpec-stdlib coverage milestones
> ([ADR 0007 D11](../adr/0007-corpus-driven-grammar-acceptance.md)). §M5a–§M5c below are retained as the historical record; do not
> re-run them.

Governed by [ADR 0006](../adr/0006-grammar-toolchain.md) (toolchain, `tokenTypeFactory`,
recovery, goldens), [ADR 0004](../adr/0004-reference-resolution-approach.md) D7 (the PSI
contract M5 must ship so M5.5 is not a retrofit), [ADR 0005](../adr/0005-minimal-parser-definition-for-commenting.md)
(what M4b already landed), [ADR 0003](../adr/0003-parser-definition-timing.md) D3
(spellchecking tail), [ADR 0002](../adr/0002-build-and-platform-baseline.md) (platform
baseline; **D6 superseded by ADR 0006**).

Prerequisite: **M4b green** — `ce92694`, 96 tests passing, `build test verifyPlugin` clean.

Package root: `simpli.fyi.plugins.typespec`. Kotlin, JDK 21, IntelliJ IDEA Community
2025.2.6.3 on the compile classpath. **The grammar work adds no `<depends>`**
([ADR 0006](../adr/0006-grammar-toolchain.md) F4). The **second `<depends>` on
`com.intellij.modules.spellchecker` in M5c is now RATIFIED and CE-CONFIRMED** — the owner
approved it and Community availability was verified against the pinned distribution
([ADR 0003](../adr/0003-parser-definition-timing.md) D3,
[ADR 0006](../adr/0006-grammar-toolchain.md) F10). It is no longer gated.

> **Revision 2 (2026-09-02) — read this before starting M5a.** Two things changed after
> commit `3733d52`:
> 1. **The M5a `mixin=` spike is cancelled.** ADR 0006 D7 is now a *decided* design:
>    `mixin=` (base class) + `implements=` (hand-written interface), with `methods=[...]`
>    and `psiImplUtilClass` **banned**. Revision 1's "`psiImplUtilClass` fallback" was based
>    on a wrong claim and is void. ADR 0004 D2's per-element `CachedValue` survives intact.
> 2. **The Gradle wiring changed.** `srcDir(taskProvider)` does not work on IPGP 2.18.1
>    (ADR 0006 F9). Use ADR 0006 D2's explicit recipe verbatim.

**Deliverable in one sentence:** `.tsp` files parse into a real, named, error-tolerant PSI
tree, replacing M4b's flat leaf stream.

---

## Why this is the next milestone, and not M5.5

M4b shipped a `ParserDefinition`, a `TypeSpecFile`, an `IFileElementType`, comment/string
token sets and `PsiComment`/`PsiWhiteSpace` leaves. It shipped **no composite element types
at all** — `TypeSpecFlatParser` opens one marker, drains the lexer, closes it, and
`createElement` deliberately throws. So:

- There is no node representing `Foo.Bar`, and none representing `Foo` or `Bar` separately,
  so a `PsiReference` has nothing to attach to (ADR 0004 F5).
- There is no declaration node, so nothing can implement `PsiNameIdentifierOwner`, so a
  resolved target has no `getName()` to match against and no `getNameIdentifier()` to
  navigate to.
- `myFixture.getReferenceAtCaretPositionWithAssertion` would have to be pointed at a leaf
  token, which is not the API's contract.

Every one of plan 02's five prerequisite checks fails against today's tree. Navigation is
therefore **not** unblocked by M4b; the flat parser only made M4's language-keyed EPs
resolve. The next milestone is unambiguously the real grammar.

M4b *did* shrink M5 — the `ParserDefinition` shape, the file element type and `TypeSpecFile`
are done and stay untouched (ADR 0006 F7). M5's task 0 is now a two-line swap of
`createParser` / `createElement`, not a new subsystem.

---

## Splitting

Plan 00 §M5 was a single milestone and explicitly authorised a split *"if the first `tsp-dev`
run stalls."* Splitting it up front instead, for three reasons that are visible now rather
than after a stall:

1. The toolchain change (ADR 0006) touches `settings.gradle.kts`, `build.gradle.kts` and
   M2's shipped `TypeSpecTokenTypes` — none of which is grammar work, all of which can fail
   for build reasons alone.
2. ADR 0006 F8's `mixin=` question was **blocking for the grammar's architecture**. It is now
   answered (revision 2) — but the answer arrived by research, and M5a still owns proving the
   toolchain end-to-end before ~12 rules depend on it.
3. A grammar covering all of `model`/`op`/`interface`/`enum`/`union`/`alias`/`scalar`/
   decorators/type-expressions plus the ADR 0004 D7 contract plus goldens for each is well
   past one `tsp-dev` run.

| Milestone | Scope | One `tsp-dev` run? |
|---|---|---|
| **M5a** | Toolchain: IPGP bump, GrammarKit subplugin, lexer task migration, `tokenTypeFactory` bridge, one throwaway rule proving generation *and* the Kotlin-mixin/generated-Java seam end-to-end. **No TypeSpec grammar.** | Yes |
| **M5b** | Core grammar: file / `import` / `using` / `namespace` / `model`, plus `TypeSpecIdentifier`, `TypeSpecQualifiedName` and the full ADR 0004 D7 named-element contract. Swap `createParser`/`createElement`. | Yes |
| **M5c** | Remaining grammar: `op`, `interface`, `enum`, `union`, `alias`, `scalar`, decorator applications, type expressions. Spellchecking tail. | Yes |

**M5b is the milestone that must not be compromised.** M5a is plumbing; M5c is more of the
same shape. M5b is where the PSI contract that M5.5, M6 and M6.5 all build on gets fixed.

---

### M5a — Grammar-Kit toolchain and the token bridge

**Goal.** `./gradlew generateParser` runs, emits Java into a source root, compiles against
our existing token types **and against a Kotlin mixin base class**, and the whole existing
test suite is still green. Nothing about TypeSpec's actual syntax is decided.

**Files**
```
settings.gradle.kts                                                   (modify — IPGP 2.16.0 → 2.18.1; RATIFIED)
build.gradle.kts                                                      (modify — grammarkit subplugin; grammarKit()+jflex(); replace the hand-rolled JavaExec; ADR 0006 D2 wiring VERBATIM)
src/main/grammars/TypeSpec.bnf                                        (create — header + ONE throwaway rule with mixin= + implements=)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecTokenTypes.kt (modify — fromNameOrText factory + key map)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecElementType.kt (create — IElementType subclass for composites)
src/test/kotlin/.../TypeSpecTokenTypeFactoryTest.kt                   (create — tsp-tester)
docs/adr/0006-grammar-toolchain.md                                    (modify — ONLY if D10's empirical checks contradict the ADR)
```
Not modified: `_TypeSpecLexer.flex`, `TypeSpecLexerAdapter`, `TypeSpecParserDefinition`,
`TypeSpecFlatParser`, `plugin.xml`. **M5a ships with the flat parser still wired in.**

**Approach**

1. **Bump IPGP to 2.18.1** ([ADR 0006](../adr/0006-grammar-toolchain.md) D3 — **owner-ratified,
   no longer gated**) and run `./gradlew clean build test verifyPlugin` **before touching
   anything else**. Commit this alone if it is green. If it is red, stop and report — do not
   proceed onto a broken base.
2. Apply `id("org.jetbrains.intellij.platform.grammarkit")`; add `grammarKit()` and `jflex()`
   inside the `intellijPlatform { }` dependencies block; delete the `val jflex: Configuration`,
   the `generateTypeSpecLexer` `JavaExec` task and ADR 0002 D6's comment block.
3. Configure both tasks **exactly as [ADR 0006](../adr/0006-grammar-toolchain.md) D2 specifies
   — copy that snippet, do not paraphrase it.** The three things that will silently break the
   build if you deviate:
   - `generateParser` needs **`pathToParser` AND `pathToPsiRoot` together**; setting one is a
     configuration error.
   - `generateLexer` uses **`pathToClass`** (not `targetOutputDir`).
   - Source roots must be registered as
     `java.srcDir(tasks.generateX.flatMap { it.targetRootOutputDir })` and **each compile task
     needs an explicit `dependsOn(tasks.generateLexer, tasks.generateParser)`**.
     `srcDir(taskProvider)` registers **nothing** on 2.18.1, and `.flatMap` carries **no**
     implicit task dependency, because `targetRootOutputDir` is `@Internal` on this version
     ([ADR 0006](../adr/0006-grammar-toolchain.md) F9, IPGP #2186). Omitting the `dependsOn`
     yields a build that passes warm and fails from `clean` — the worst possible failure mode.
   Roots stay **disjoint** ([ADR 0006](../adr/0006-grammar-toolchain.md) D2).
   The typed `tasks.named<GenerateParserTask>` form **does** resolve; FQCN package is
   `org.jetbrains.intellij.platform.gradle.tasks`
   ([ADR 0006](../adr/0006-grammar-toolchain.md) F12).
4. **`TypeSpec.bnf` header** per ADR 0006 D5 and the researcher's verified attribute table:
   `parserClass`, `parserUtilClass="com.intellij.lang.parser.GeneratedParserUtilBase"`,
   `extends="com.intellij.extapi.psi.ASTWrapperPsiElement"`, `psiClassPrefix="TypeSpec"`,
   `psiImplClassSuffix="Impl"`, `psiPackage=…psi`, `psiImplPackage=…psi.impl`,
   `elementTypeHolderClass="…psi.TypeSpecTypes"`,
   `elementTypeClass="…psi.TypeSpecElementType"`,
   `tokenTypeClass="…psi.TypeSpecTokenType"`,
   `tokenTypeFactory="…psi.TypeSpecTokenTypes.fromNameOrText"`,
   and **`generateTokenAccessors=true`** (its default is `false` — this is the documented
   first-day surprise; we want token accessors on the PSI interfaces).
   `elementTypeHolderClass` **must not** be `TypeSpecElementTypes` — that name is taken by
   M4b's hand-written file-element holder. `TypeSpecTypes` is Grammar-Kit's own `<Lang>Types`
   convention and the two coexisting is normal, not a smell
   ([ADR 0006](../adr/0006-grammar-toolchain.md) F11).
5. **`tokens=[...]`** listing every token the grammar will reference, mapped to the exact
   names/texts `fromNameOrText` accepts. Start with only what the throwaway rule needs.
6. **`fromNameOrText`** on `TypeSpecTokenTypes`: `@JvmStatic`, backed by a map registering
   both the token *name* and (for literal tokens) the token *text*
   ([ADR 0006](../adr/0006-grammar-toolchain.md) D5, F6). **Throws** on an unknown key.
   Existing constants get `@JvmField` if they do not have it. Do not change any existing
   token's identity or debug name — `TypeSpecLexerTest` and `TypeSpecSyntaxHighlighterTest`
   must pass **unedited**.
7. **The mixin seam check — not a spike, a verification.**
   ([ADR 0006](../adr/0006-grammar-toolchain.md) D7, D10.1, F8.) The design is decided; what
   M5a proves is that the *build* supports it. The throwaway rule carries **both**:
   ```
   mixin("throwaway")      = "simpli.fyi.plugins.typespec.psi.impl.TypeSpecThrowawayMixin"
   implements("throwaway") = "simpli.fyi.plugins.typespec.psi.TypeSpecThrowawayIface"
   ```
   where the mixin is a **Kotlin** abstract class extending `ASTWrapperPsiElement` that
   implements a trivial method of the hand-written **Kotlin** interface *directly on itself*.
   Run `./gradlew clean build`. Assert:
   - the generated `TypeSpecThrowawayImpl` **extends the Kotlin mixin**, not `ASTWrapperPsiElement`;
   - the generated **Java** compiles against the **Kotlin** mixin and interface in the same
     source set (ADR 0006 D10.1 — the one genuinely unverified thing in this milestone);
   - **no `methods=[...]` and no `psiImplUtilClass` appear anywhere in the `.bnf`.** They
     resolve via Grammar-Kit's `AsmHelper` against the generator's classpath, which cannot
     contain classes this build has not compiled yet (ADR 0006 F8). They are **banned**.
   If the Kotlin↔Java seam fails, **stop and report** — it invalidates ADR 0006 D7 and needs
   an ADR revision, not a workaround. Keep the throwaway rule and its mixin wiring until M5b
   replaces them with the real `TypeSpecNamedElementMixin`.
8. Confirm the configuration cache still hits (`./gradlew build` twice; second run reports
   "Reusing configuration cache"). If it does not, report rather than disabling it
   (plan 00 §M0 risk 2).

**Acceptance (`tsp-tester`)**
- `TypeSpecTokenTypeFactoryTest` (new): for **every** constant in `TypeSpecTokenTypes`,
  `fromNameOrText(<name>)` returns the identical instance (`assertSame`, not `assertEquals`);
  for every token declared with a literal in the `.bnf` `tokens` block,
  `fromNameOrText(<text>)` returns the identical instance; `fromNameOrText("nonsense")`
  throws. This is the test that makes ADR 0006 D5's failure mode impossible.
- The **entire existing suite passes with zero edits to any existing test file.** If a test
  needed editing, that is a regression in M5a, not a test update. Report it.
- Assert by inspection and record verbatim: `plugin.xml` still has exactly one `<depends>`
  (the spellchecker one arrives in M5c, not here);
  `grep -ri "modules.ultimate\|platform.lsp\|NodeJS\|JavaScript" src/ build.gradle.kts
  settings.gradle.kts` is empty.
- `git status` shows nothing generated under `src/` — generation lands under `build/`
  ([ADR 0006](../adr/0006-grammar-toolchain.md) D4).
- **Clean-build determinism.** `./gradlew clean build` must pass **from cold**, not just
  incrementally. This is the assertion that catches a missing `dependsOn`
  ([ADR 0006](../adr/0006-grammar-toolchain.md) F9) — an incremental-only green build hides
  exactly that bug.
- **Generated-Java ↔ Kotlin-mixin compilation** succeeds (approach step 7,
  [ADR 0006](../adr/0006-grammar-toolchain.md) D10.1).
- The `.bnf` contains **no** `methods=[...]` and **no** `psiImplUtilClass`
  (`grep -n "methods\s*=\|psiImplUtilClass" src/main/grammars/TypeSpec.bnf` is empty).

**Done when**
```bash
./gradlew clean build test verifyPlugin && ./gradlew build   # second run must reuse the configuration cache
```

**Risks / open questions**
- **No open questions block M5a.** ADR 0006's four are closed; the IPGP bump is ratified;
  the `mixin=` gate is closed PASSED. Start immediately.
- ⚠ **The `@Internal` `targetRootOutputDir` regression** is the highest-risk item
  ([ADR 0006](../adr/0006-grammar-toolchain.md) F9). Symptom if wired wrong: green warm
  build, red `clean` build, or "cannot find symbol TypeSpecParser". Follow D2 exactly.
- The migrated `generateLexer` may emit a byte-different `_TypeSpecLexer.java` than the
  hand-rolled task. `TypeSpecLexerTest` is the guard; if it goes red, **report the diff**,
  do not adjust the test.
- `GenerateLexerTask.pathToClass` vs `targetOutputDir` deprecation status is unverified
  ([ADR 0006](../adr/0006-grammar-toolchain.md) D10.2). Use `pathToClass`; report any warning.
- If a released IPGP 2.19.0 restores `rootOutputDirectory` (IPGP #2186) before M5a lands, do
  **not** wait for it and do **not** switch — D2's form works on every version
  ([ADR 0006](../adr/0006-grammar-toolchain.md) D10.3).
- `TypeSpecTypes` / `TypeSpecElementTypes` coexistence — two similarly-named holders is
  confusing at 5pm; ADR 0006 F11 says which does what.

---

### M5b — Core grammar, identifiers, and the named-element contract

**Goal.** A real parse tree for the file skeleton and `model`, with every declaration a
`PsiNameIdentifierOwner` and every type name a per-segment addressable node. The flat parser
is deleted here.

**In scope (grammar coverage).** `typespec_file`; `import_statement`; `using_statement`;
`namespace_statement` (both block and blockless, dotted names); `model_statement` with
properties, `extends`, `is`, spread (`...Base`), optional `?`, and template parameter *lists*
(`model Page<T>` — declaring `T`, not resolving it).

**Out of scope.** Everything in M5c. Reference *resolution* (M5.5 — this milestone builds the
nodes a reference will later attach to, and attaches nothing). Rename / `setName` — it
throws `IncorrectOperationException` here ([ADR 0004](../adr/0004-reference-resolution-approach.md)
D7.3).

**Files**
```
src/main/grammars/TypeSpec.bnf                                            (modify — real rules)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecNamedElement.kt   (create — interface : PsiNameIdentifierOwner)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecNamedElementMixin.kt   → psi/impl/ (create — the mixin base class; ADR 0006 D7)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecPsiUtil.kt        (create — plain Kotlin helpers, NOT a psiImplUtilClass)
src/main/kotlin/simpli/fyi/plugins/typespec/parser/TypeSpecParserDefinition.kt (modify — createParser/createElement)
src/main/kotlin/simpli/fyi/plugins/typespec/parser/TypeSpecFlatParser.kt  (DELETE)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecFile.kt           (modify — ADR 0004 D7.4 accessors)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecElementTypes.kt   (modify — KDoc only; FILE is unchanged)
```

**Approach**

1. **Task 0 — swap the parser.** `createParser` returns the generated `TypeSpecParser()`;
   `createElement` returns `TypeSpecTypes.Factory.createElement(node)` (replacing the
   `UnsupportedOperationException`). `getFileNodeType()`, `createFile`, `getCommentTokens`,
   `getStringLiteralElements` are **unchanged**; `getWhitespaceTokens` stays as-is
   ([ADR 0006](../adr/0006-grammar-toolchain.md) F7 — keep the same `IFileElementType`
   *instance*). Delete `TypeSpecFlatParser` in the same commit; ADR 0003 D2 forbids leaving a
   one-leaf stub as a resting state.
   The root rule `typespec_file` must **not** declare its own element type — the platform
   hands the generated parser `root = TypeSpecElementTypes.FILE`.
2. **`TypeSpecIdentifier`** — a rule node wrapping exactly one name token, ordinary **or**
   backticked. **`TypeSpecQualifiedName`** — a `.`-separated sequence of `TypeSpecIdentifier`.
   Every construct naming a type uses `TypeSpecQualifiedName` **uniformly**: `using`,
   `namespace`, `extends`, `is`, property types, spread
   ([ADR 0004](../adr/0004-reference-resolution-approach.md) D7.1). This is the single most
   important shape decision in the milestone; without per-segment nodes `Foo.Bar` cannot be
   navigated segment by segment and M5.5 is blocked.
3. **Named-element contract** ([ADR 0004](../adr/0004-reference-resolution-approach.md) D7.2/D7.3).
   `TypeSpecNamedElement : PsiNameIdentifierOwner`, **hand-written**. Every declaration in
   scope (`namespace`, `model`, model property, template parameter) gets
   `mixin("<rule>") = "…psi.impl.TypeSpecNamedElementMixin"` **and**
   `implements("<rule>") = "…psi.TypeSpecNamedElement"`, with the methods implemented
   **directly on the mixin class** ([ADR 0006](../adr/0006-grammar-toolchain.md) D7).
   **No `methods=[...]`. No `psiImplUtilClass`.** Both are banned repo-wide (ADR 0006 F8):
   - `getNameIdentifier()` → the name's `TypeSpecIdentifier` node.
   - `getName()` → its text **with surrounding backticks stripped**.
   - `getTextOffset()` → `nameIdentifier.textRange.startOffset`, so Find Usages, structure
     view and navigation point at the name, not at the `model` keyword. ⚠ Getting this wrong
     trips `ParsingTestCase.checkRangeConsistency`, which is a *good* thing — it fails loudly.
   - `setName()` → `throw IncorrectOperationException` (rename is M6.5).
   For a dotted `namespace Foo.Bar.Baz`, `getNameIdentifier()` is the **last** segment.
4. **`TypeSpecFile` accessors** ([ADR 0004](../adr/0004-reference-resolution-approach.md) D7.4):
   `getImportStatements()`, `getUsingStatements()`, `getFileNamespace()`,
   `getTopLevelDeclarations()`. These exist so M5.5's resolver does not scatter grammar
   knowledge across two milestones. Implement with `PsiTreeUtil`; they are queries, not
   caches — caching is M5.5's problem.
5. **Recovery** per [ADR 0006](../adr/0006-grammar-toolchain.md) D6, from the first rule.
   `pin` on every declaration; `private` zero-length `!(...)` recovery predicates for the
   statement loop and the model-property loop; `'}'` and `';'` in every stop set.
6. `@JvmStatic` / `@JvmField` on any Kotlin member the generated **Java** touches statically
   ([ADR 0006](../adr/0006-grammar-toolchain.md) D5). The mixin base classes are ordinary
   Kotlin classes extended by generated Java — no annotation needed for inherited instance
   methods, only for statics and fields.

**Acceptance (`tsp-tester`)**
- `TypeSpecParsingTest : ParsingTestCase("parser", "tsp", TypeSpecParserDefinition())`,
  overriding `getTestDataPath()` (**mandatory** — the default assumes the platform source
  tree) and `includeRanges() = true`. JUnit3 naming: plain `fun testXxx()`, **no `@Test`,
  no JUnit5**. Fixture case must match `getTestName()` exactly (APFS trips
  `checkCaseSensitiveFS`).
  One `.tsp`/`.txt` pair per area: `Imports`, `Usings`, `NamespaceBlock`, `NamespaceBlockless`,
  `NamespaceDotted`, `ModelSimple`, `ModelExtends`, `ModelIs`, `ModelSpread`,
  `ModelOptionalProperty`, `ModelTemplateParams`, `BacktickIdentifier`.
  Use `doTest(true, true)` (asserts no error elements) for all well-formed fixtures.
- **Recovery fixtures**, `doTest(true)` (no error assertion) so the golden locks in the
  recovery *shape*: `BrokenProperty` (a property missing its type, followed by a good one),
  `BrokenStatement` (garbage between two good `model`s). Assert in the golden that the
  following good declaration is still a proper composite node — that is the whole point of
  ADR 0006 D6.
- `TypeSpecPsiContractTest` (`BasePlatformTestCase`): for each declaration kind, the PSI node
  `is TypeSpecNamedElement`; `getName()` matches; backticked names are unquoted;
  `getTextOffset()` equals the name's start offset, **not** the keyword's; `setName()` throws
  `IncorrectOperationException`. Plus `TypeSpecFile`'s four accessors return the expected
  counts/values on a fixture exercising all of them.
- **Regression sweep.** `TypeSpecLexerTest`, `TypeSpecSyntaxHighlighterTest`,
  `TypeSpecHighlightingTest`, `TypeSpecCommenterTest`, `TypeSpecBraceMatcherTest`,
  `TypeSpecQuoteHandlerTest`, `TypeSpecFileTypeTest` all pass. `TypeSpecParserDefinitionTest`
  **will** need rewriting — its expectations encode the flat shape
  ([ADR 0006](../adr/0006-grammar-toolchain.md) F7's "migration gotcha"). That is the one
  sanctioned test rewrite; every other edit is a finding.
- **Tighten what M4b/M1 deliberately left loose.** `psiFile.language` and `psiFile.fileType`
  are now correct; `TypeSpecFileTypeTest` may assert them directly. The
  [ADR 0003](../adr/0003-parser-definition-timing.md) D4 ban on `checkHighlighting` /
  `EditorTestUtil.testFileSyntaxHighlighting` **lifts here** — but do not rush to use them;
  lifting the ban is not a mandate to rewrite green tests.
- `kitchen-sink.tsp` is **not** expected to parse error-free — not in M5b and **not in M5c
  either**. M5c asserts zero errors over its own scoped subset fixture
  (`src/test/testData/parser/KitchenSinkCore.tsp`); the unmodified lexer fixture is
  **M5d's** done-signal. See M5c's "Kitchen-sink scope resolution".
- ⚠ **`-Didea.tests.overwrite.data=true` regenerates goldens; it does not validate them.**
  Read every generated tree and confirm it is correct before committing
  ([ADR 0006](../adr/0006-grammar-toolchain.md) D8). A wrong golden is frozen forever.
- **`isCheckNoPsiEventsOnReparse()` must not be overridden**
  ([ADR 0006](../adr/0006-grammar-toolchain.md) D9). If `ensureCorrectReparse` fails, the
  grammar is non-deterministic — fix the grammar.

**Done when**
```bash
./gradlew clean build test verifyPlugin
```

**Risks / open questions**
- ~~**`<` / `>` template-argument ambiguity.**~~ **CLOSED — there never was one.**
  `tsp-intellij-researcher` verified against upstream `microsoft/typespec` `parser.ts` and
  `spec.emu.html` that TypeSpec has **no relational/comparison expression grammar**, so
  `<` / `>` are unambiguous bracket delimiters in `TemplateParameters` and
  `TemplateArguments` only. M5b's split (parameter lists here, arguments in M5c) remains a
  fine *sizing* decision, but it was never a risk-avoidance one.
- If the M5a seam check failed, **M5b does not start** — ADR 0006 D7 needs revising first.
  There is no longer a `psiImplUtilClass` fallback; revision 1's was based on a wrong claim
  ([ADR 0006](../adr/0006-grammar-toolchain.md) F8). ADR 0004 D2's per-element `CachedValue`
  is preserved by the chosen design, so M5.5's caching plan stands unchanged.
- Backtick stripping in `getName()` is easy to get subtly wrong (`` `foo` `` → `foo`, but a
  bare `foo` must not lose characters). Covered explicitly by `TypeSpecPsiContractTest`.
- Blockless `namespace Foo;` covering the rest of the file is a scoping oddity that the
  grammar must express as containment or M5.5's resolver walk gets harder. Flagged for
  `tsp-dev`: prefer whatever shape makes `getFileNamespace()` trivial.

---

### M5c — Remaining declarations, type expressions, spellchecking

> **Revision 3 (2026-09-02).** Rewritten after `tsp-intellij-researcher`'s deep read of
> upstream `microsoft/typespec` (`parser.ts`, `spec.emu.html`). Three changes, all recorded
> below so `tsp-dev` does not rediscover them: the `<`/`>` ambiguity risk is **closed**; the
> `TypeOptional` fixture is **repointed** (there is no `T?` type suffix in TypeSpec);
> `void`/`never`/`unknown` need **explicit grammar alternatives**. The
> `kitchen-sink.tsp` acceptance criterion is **replaced** — see "Kitchen-sink scope
> resolution" below — because the real fixture contains constructs outside this
> milestone's scope.

**Goal.** The rest of plan 00 §M5's grammar list, on the shape M5b fixed:
every remaining declaration kind, decorator applications, and a real type-expression
grammar — verified against an M5c-scoped kitchen-sink subset that parses with zero
`PsiErrorElement`.

**In scope.** `op`, `interface`, `enum`, `union`, `alias`, `scalar`; decorator applications
(`@dec`, `@@dec` augment-decorator statements); type expressions (union `|`, intersection
`&`, array `[]`, tuple/parenthesised, template *arguments* `<>`, and the three keywordized
intrinsics `void` / `never` / `unknown`); a **coarse** `#{ … }` / `#[ … ]` value-literal rule
(enough to appear as a decorator argument or a default value, no inner structure).
Named-element contract extended to `op`, `interface`, `enum` + enum member, `union` + union
variant, `alias`, `scalar`
([ADR 0004](../adr/0004-reference-resolution-approach.md) D7.2's full list).

**Explicitly deferred** (plan 00 §M5's list, now itemised because M5d consumes it):
- projections;
- `dec` / `fn` / `extern` declarations;
- **function-type expressions** — `(p: T) => R`, and the `op f(): void => void;` return-type
  form. Upstream `spec.emu.html` §FunctionTypeExpression; `fn`-adjacent, so it lands with the
  `fn` bucket, not here;
- **`const` declarations** — these require a real *value*-expression grammar (string /
  number / member-access / tuple / object literals), which is exactly the "value literals
  beyond a coarse rule" exclusion. Not cheap, not in M5c;
- **statement-level directives** — `#suppress "deprecated" "legacy"`, `#deprecated`. The
  lexer already returns the whole `#suppress` as one `DIRECTIVE` token, so the *rule* is
  cheap, but its **placement** (a prefix attachable to any statement) is not, and nothing in
  plan 00 scoped it. M5d;
- value literals `#{}` / `#[]` **beyond** the coarse rule above.

#### Kitchen-sink scope resolution — DECIDED: option (a), a scoped subset fixture

Revision 2's acceptance criterion ("`src/test/testData/lexer/kitchen-sink.tsp` parses with
zero `PsiErrorElement`") is **unachievable inside M5c's declared scope and is withdrawn.**
Audit of the real file found four out-of-scope constructs:

| Line | Construct | Bucket |
|---|---|---|
| 21 | `#suppress "deprecated" "legacy"` | statement-level directive — deferred |
| 26–29 | `const text = """…"""` | `const` + value expressions — deferred |
| 33 | `op transform(): void => void;` | FunctionTypeExpression — `fn` bucket, deferred |
| 35–37 | `interface Store { values: #[1, 2, 3]; }` | interface member that is a *property*, not an operation signature — not valid TypeSpec; M2 fixture content chosen for **lexical** coverage only |

`kitchen-sink.tsp` was written in M2 to hit every *token* kind, not to be a syntactically
valid program. It keeps that job. Two consequences:

1. **M5c gets its own fixture**: `src/test/testData/parser/KitchenSinkCore.tsp` — the
   **M5c-achievable subset of kitchen-sink, not the full file**. Derive it from
   `kitchen-sink.tsp` by keeping lines 1–20, 22–24 and 31 verbatim (imports, using, decorated
   blockless namespace with a `#{}` argument, doc comments, the full `Widget` model with
   template parameter, optional/defaulted/backticked/union-typed/spread properties, the
   `@@doc` augment statement, the decorated `op read` with a template *argument* and a union
   return, the `enum`, the `alias`) and by **replacing** the four out-of-scope items with
   in-scope equivalents that keep the coverage intent:
   - drop line 21 (`#suppress`) — nothing replaces it (line 22's `op read` is kept);
   - drop lines 26–29 (`const`) — nothing replaces it;
   - replace line 33 with `op transform(input: Widget<string>[]): void;` (keeps `void`,
     array and template-argument coverage, drops `=>`);
   - replace lines 35–37 with a real operation-bearing interface,
     `interface Store { list(): Widget<string>[]; get(id: string): Widget<string> | Error; }`;
   - keep the trailing comments (lines 39–41).
   The file must carry a header comment stating verbatim: *"M5c-achievable subset of
   src/test/testData/lexer/kitchen-sink.tsp — NOT the full file. Constructs deferred to M5d
   (`const`, `#suppress` directives, function-type expressions) are deliberately absent."*
2. **The full-file zero-error parse moves to M5d** (below), which exists precisely to close
   the gap. `kitchen-sink.tsp` itself is not touched, moved, or edited by M5c.

**Files**
```
src/main/grammars/TypeSpec.bnf                                           (modify)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecPsiUtil.kt        (modify)
src/main/kotlin/simpli/fyi/plugins/typespec/spellchecker/TypeSpecSpellcheckingStrategy.kt (create — RATIFIED, ships)
src/main/resources/META-INF/plugin.xml                                   (modify — spellchecker.support + 2nd <depends>, RATIFIED)
src/test/testData/parser/KitchenSinkCore.tsp                             (create — subset fixture, see above)
```

**Approach — settled grammar decisions (do not re-litigate)**

- **`<` / `>` is plain recursive descent.** TypeSpec has **no relational or comparison
  expression grammar at all** — `<` and `>` occur in exactly two productions,
  `TemplateParameters` (M5b, shipped) and `TemplateArguments`
  (`tsp-intellij-researcher`, from upstream `parser.ts` + `spec.emu.html`). They are
  unambiguous bracket delimiters. Write `template_argument_list ::= '<' … '>' { pin=1 }`
  exactly like the existing `template_parameter_list`. **No external rules, no token
  remapping, no `GeneratedParserUtilBase` tricks.**
- **`void` / `never` / `unknown` must be explicit alternatives** in the primary
  type-expression rule. The lexer keywordizes all three (`_TypeSpecLexer.flex` lines
  103–105, matching upstream), so they can **never** reach the `qualified_name` /
  `IDENTIFIER` path. Omitting them makes `op f(): void;` fail to parse. This — not `<`/`>` —
  is M5c's actual landmine.
- **There is no `T?` type suffix in TypeSpec.** Optionality is a property of `ModelProperty`
  / `FunctionParameter` (`identifier '?'? ':' expression`), which **M5b's `model_property`
  rule already implements correctly**. Do not add `'?'` to any type-expression rule.
- Type-expression precedence, loosest to tightest: union `|` → intersection `&` → array
  `[]` postfix → primary (`qualified_name` with optional `template_argument_list`,
  `void`/`never`/`unknown`, string/number literal, parenthesised, tuple `[T, U]`).

**Spellchecking tail — ✅ RATIFIED, ships in M5c.** `spellchecker.support` lives in
`com.intellij.modules.spellchecker` and needs a **second `<depends>`**. Both halves of the
gate are cleared:
- **CE availability CONFIRMED** by direct verification against the pinned `IC-252.28539.97` —
  `product-info.json` lists it, and
  `lib/modules/intellij.spellchecker.jar!/intellij.spellchecker.xml` declares only
  `intellij.platform.*` / bundled-library deps, nothing Ultimate
  ([ADR 0006](../adr/0006-grammar-toolchain.md) F10).
- **Owner approved it explicitly**, conditioned on exactly that CE availability
  ([ADR 0003](../adr/0003-parser-definition-timing.md) D3, now CLOSED/CONFIRMED).

The EP is `dynamic="true"`, so no restart is required. This is the **only** sanctioned
exception to the one-`<depends>` rule — the plugin ships exactly two `<depends>` and a third
needs a new ADR. `tsp-dev` implements it in M5c without further ratification; the previous
"if unratified, ship M5c without it" instruction is **withdrawn**.

**Acceptance (`tsp-tester`)**
- `TypeSpecParsingTest` grows a `.tsp`/`.txt` pair per new area: `Operation`, `Interface`,
  `Enum`, `Union`, `Alias`, `Scalar`, `Decorators`, `AugmentDecorator`, `TypeUnion`,
  `TypeIntersection`, `TypeArray`, `TypeTemplateArgs`, `TypeIntrinsics`, and
  `OptionalPropertyComplexType`. Same golden-review discipline
  ([ADR 0006](../adr/0006-grammar-toolchain.md) D8).
  - **`TypeIntrinsics.tsp`** must contain at least `op f(): void;`, `op g(): never;`,
    `op h(): unknown;` — it is the regression guard for the keywordized-intrinsics landmine.
  - **`OptionalPropertyComplexType.tsp`** *replaces* revision 2's `TypeOptional` fixture,
    which is deleted from this plan: it asserted a `T?` type suffix that does not exist in
    TypeSpec. The replacement asserts that M5b's optional-**property** behaviour still holds
    once the property type is a full type expression — e.g. `model M { a?: string | null;
    b?: Widget<string>[]; }`.
- **`KitchenSinkCore` parses with zero `PsiErrorElement`** — `doTest(true, true)` over
  `src/test/testData/parser/KitchenSinkCore.tsp`, the M5c-achievable subset defined in
  "Kitchen-sink scope resolution" above. **This is M5c's real done-signal.** The full
  `src/test/testData/lexer/kitchen-sink.tsp` is **not** expected to parse error-free in M5c
  and must not be copied, edited, or moved by this milestone; it is M5d's done-signal.
- `TypeSpecPsiContractTest` extended to every declaration kind added here.
- Spellchecking **ships**: `./gradlew verifyPlugin` stays clean **and** `runIde` on a bare
  Community install still loads the plugin. `tsp-tester` reports the verbatim `<depends>`
  list, which must be exactly `com.intellij.modules.platform` +
  `com.intellij.modules.spellchecker` — no more, no less.
- Full regression sweep, no edits to M5b's goldens (`ModelOptionalProperty.txt` included —
  it is the second half of the optionality regression guard). **A changed M5b golden means
  M5c destabilised the core grammar — report it, do not re-baseline it.**

**Done when**
```bash
./gradlew clean build test verifyPlugin
```

**Risks / open questions**
- ~~**`<` / `>` template-argument ambiguity.**~~ **CLOSED.** `tsp-intellij-researcher`
  verified against upstream `microsoft/typespec` `parser.ts` and `spec.emu.html` that
  TypeSpec defines **no relational/comparison expression grammar**, so there is no competing
  production for `<` / `>`; they are pure bracket delimiters in `TemplateParameters` and
  `TemplateArguments` only. Ordinary recursive descent with `pin=1` on `<` suffices. The
  revision-2 contingency "split template arguments into M5d if it stalls" is **withdrawn** —
  template arguments stay in M5c.
- **Keywordized intrinsics are the real landmine** (see Approach). If `op f(): void;`
  produces a `PsiErrorElement`, the cause is a missing alternative in the primary
  type-expression rule, not a lexer bug — do **not** de-keywordize `void`/`never`/`unknown`
  in the flex file; that would break M2/M3 highlighting goldens.
- Decorators are one lexer token by design (`@Ns.name` → `DECORATOR`, `@@Ns.name` →
  `AUGMENT_DECORATOR`; [ADR 0004](../adr/0004-reference-resolution-approach.md) F6). The
  grammar wraps that token in a decorator-application / augment-decorator-statement node; it
  does **not** split it. Splitting is a separate, breaking change to M2/M3 tests —
  [ADR 0004](../adr/0004-reference-resolution-approach.md) open question 1, owner's call.
- Type-expression rules are left-recursive by nature; Grammar-Kit handles this with
  precedence-ordered rules, not with PEG left recursion. If `tsp-dev` finds itself writing
  `expression ::= expression '|' expression`, stop and use the layered precedence chain in
  Approach instead.
- ~~Second `<depends>` is unratified.~~ ✅ Ratified and CE-confirmed (above). No longer a risk.

---

### M5d — Deferred-construct sweep (full kitchen-sink parse) — **CLOSED, 2026-09-03. Superseded and never executed.**

> **Formal close, per [plan 04](04-grammar-corrections.md) §M6f.** M5d was superseded by
> [plan 04](04-grammar-corrections.md) at ratification and is now closed as **completed by
> other means**. Nothing in this section should be scheduled or implemented.
>
> **Where its three constructs actually landed:**
>
> | M5d construct | Landed in | Commit |
> |---|---|---|
> | statement-level directives (`#suppress` / `#deprecated`) | plan 04 §M6e | `9312eb5` |
> | `const` + a real value-expression grammar | ADR 0007 D6 / plan 04 §M6a — `value_expression` was unified with `type_expression_`, which subsumes the whole sub-goal | `9aba27f` |
> | `dec` / `fn` / `extern` and their parameter lists | plan 04 §M6e (as upstream's `parseModifiers()` loop, **not** an `extern_declaration` wrapper) | `9312eb5` |
>
> **Its done-signal is withdrawn, not transferred.** "The unmodified `kitchen-sink.tsp`
> parses with zero `PsiErrorElement`" can never be satisfied: the file is not valid TypeSpec
> ([ADR 0007 D10](../adr/0007-corpus-driven-grammar-acceptance.md)). The acceptance oracle
> for grammar work is now the 83-file `TypeSpecCorpusTest` gate (ADR 0007 D1/D2), which is
> unconditional as of `b5ba371`.
>
> **Its open question is closed** by [ADR 0007 D10](../adr/0007-corpus-driven-grammar-acceptance.md):
> option (ii) — `src/test/testData/lexer/kitchen-sink.tsp` stays byte-identical and
> lexically motivated. The valid-TypeSpec parser variant D10 called for
> (`src/test/testData/parser/KitchenSink.tsp`) was **not created**; see the D10 addendum and
> [plan 04](04-grammar-corrections.md) §"What this plan did not close"/1.
>
> Everything below this line is retained for the record only.


**Not a prerequisite for M5.5.** [Plan 02](02-navigation.md)'s prerequisite remains **M5c
green**; nothing in M5.5's resolver touches M5d's constructs. M5d may run before or after
M5.5 at the owner's discretion.

**Goal.** Close the three constructs M5c deferred, so that the *unmodified*
`src/test/testData/lexer/kitchen-sink.tsp` parses with zero `PsiErrorElement`:
1. statement-level directives (`#suppress`, `#deprecated`) — one `DIRECTIVE` lexer token
   plus trailing string arguments, attachable as a statement prefix;
2. `const` declarations, which pull in a real **value**-expression grammar (multi-line
   strings, numbers, member access, `#{}` / `#[]` with inner structure);
3. function-type expressions `(p: T) => R` and the `op f(): void => void;` return form,
   together with the `dec` / `fn` / `extern` declaration bucket they belong to.

**Done when** `doTest(true, true)` over a copy of `kitchen-sink.tsp` in
`src/test/testData/parser/` reports zero `PsiErrorElement` (the lexer copy at
`src/test/testData/lexer/kitchen-sink.tsp` stays authoritative and unedited — M2's lexer test
owns it), **and** `./gradlew clean build test verifyPlugin` is green.

**Open question for the owner.** `interface Store { values: #[1, 2, 3]; }` (kitchen-sink
lines 35–37) is a *property* in an interface body, which upstream TypeSpec does **not**
accept — interface members are operation signatures. Before M5d starts, decide one of:
(i) edit `kitchen-sink.tsp` to make it valid TypeSpec (touches M2's lexer golden — must be
re-reviewed, not re-baselined); or (ii) keep the file lexically-motivated and declare M5d's
target to be a *valid-TypeSpec* variant of it. **`tsp-dev` must not choose this silently.**

---

## After M5

**M5d** (above) is scoped but not planned, and is **not** a prerequisite for anything below.

[Plan 02](02-navigation.md) — **M5.5**, reference resolution and jump navigation. Its
prerequisite is **M5c green**, not M5b and not M5d: its resolver targets `model`/`enum`/`union`/
`interface`/`alias`/`scalar`/`op`/`namespace`, and six of those eight only exist after M5c.
Plan 02's five-point prerequisite check runs first and is authorised to stop the milestone.
