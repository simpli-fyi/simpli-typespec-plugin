# Plan 03 — Grammar-Kit parser and PSI (M5a → M5c)

> **Plan numbering is allocation order, not milestone order.** `02-navigation.md` (M5.5) was
> written first but runs *after* this plan. Read this one first.

Governed by [ADR 0006](../adr/0006-grammar-toolchain.md) (toolchain, `tokenTypeFactory`,
recovery, goldens), [ADR 0004](../adr/0004-reference-resolution-approach.md) D7 (the PSI
contract M5 must ship so M5.5 is not a retrofit), [ADR 0005](../adr/0005-minimal-parser-definition-for-commenting.md)
(what M4b already landed), [ADR 0003](../adr/0003-parser-definition-timing.md) D3
(spellchecking tail), [ADR 0002](../adr/0002-build-and-platform-baseline.md) (platform
baseline; **D6 superseded by ADR 0006**).

Prerequisite: **M4b green** — `ce92694`, 96 tests passing, `build test verifyPlugin` clean.

Package root: `simpli.fyi.plugins.typespec`. Kotlin, JDK 21, IntelliJ IDEA Community
2025.2.6.3 on the compile classpath. **One `<depends>`**, unchanged
([ADR 0006](../adr/0006-grammar-toolchain.md) F4) — the spellchecking second `<depends>` in
M5c is the one sanctioned exception and is **gated on owner ratification**.

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
2. ADR 0006 F8's `mixin=` unknown is **blocking for the grammar's architecture**. Discovering
   it mid-grammar means unwinding ~12 rules' worth of decisions.
3. A grammar covering all of `model`/`op`/`interface`/`enum`/`union`/`alias`/`scalar`/
   decorators/type-expressions plus the ADR 0004 D7 contract plus goldens for each is well
   past one `tsp-dev` run.

| Milestone | Scope | One `tsp-dev` run? |
|---|---|---|
| **M5a** | Toolchain: IPGP bump, GrammarKit subplugin, lexer task migration, `tokenTypeFactory` bridge, `mixin=` spike, one throwaway rule proving generation end-to-end. **No TypeSpec grammar.** | Yes |
| **M5b** | Core grammar: file / `import` / `using` / `namespace` / `model`, plus `TypeSpecIdentifier`, `TypeSpecQualifiedName` and the full ADR 0004 D7 named-element contract. Swap `createParser`/`createElement`. | Yes |
| **M5c** | Remaining grammar: `op`, `interface`, `enum`, `union`, `alias`, `scalar`, decorator applications, type expressions. Spellchecking tail. | Yes |

**M5b is the milestone that must not be compromised.** M5a is plumbing; M5c is more of the
same shape. M5b is where the PSI contract that M5.5, M6 and M6.5 all build on gets fixed.

---

### M5a — Grammar-Kit toolchain and the token bridge

**Goal.** `./gradlew generateParser` runs, emits Java into a source root, compiles against
our existing token types, and the whole existing test suite is still green. Nothing about
TypeSpec's actual syntax is decided.

**Files**
```
settings.gradle.kts                                                   (modify — IPGP 2.16.0 → 2.18.1)
build.gradle.kts                                                      (modify — grammarkit subplugin; grammarKit()+jflex(); replace the hand-rolled JavaExec)
src/main/grammars/TypeSpec.bnf                                        (create — header + ONE throwaway rule)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecTokenTypes.kt (modify — fromNameOrText factory + key map)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecElementType.kt (create — IElementType subclass for composites)
docs/adr/0006-grammar-toolchain.md                                    (modify — record the D7 spike outcome)
```
Not modified: `_TypeSpecLexer.flex`, `TypeSpecLexerAdapter`, `TypeSpecParserDefinition`,
`TypeSpecFlatParser`, `plugin.xml`. **M5a ships with the flat parser still wired in.**

**Approach**

1. **Bump IPGP to 2.18.1** ([ADR 0006](../adr/0006-grammar-toolchain.md) D3) and run
   `./gradlew clean build test verifyPlugin` **before touching anything else**. Commit this
   alone if it is green. If it is red, stop and report — do not proceed onto a broken base.
2. Apply `id("org.jetbrains.intellij.platform.grammarkit")`; add `grammarKit()` and `jflex()`
   inside the `intellijPlatform { }` dependencies block; delete the `val jflex: Configuration`,
   the `generateTypeSpecLexer` `JavaExec` task and ADR 0002 D6's comment block.
3. Configure `generateLexer` (source `src/main/grammars/_TypeSpecLexer.flex`, root
   `build/generated/sources/grammarkit-lexer/java/main`) and `generateParser` (source
   `src/main/grammars/TypeSpec.bnf`, root `build/generated/sources/grammarkit-parser/java/main`,
   `pathToParser = "simpli/fyi/plugins/typespec/parser/TypeSpecParser.java"`,
   `pathToPsiRoot = "simpli/fyi/plugins/typespec/psi"`). **Disjoint roots**
   ([ADR 0006](../adr/0006-grammar-toolchain.md) D2). Wire both into
   `sourceSets.main.java.srcDir(...)` and keep the explicit
   `tasks.named("compileKotlin") { dependsOn(...) }`.
   If the typed `tasks.named<GenerateLexerTask>` form will not resolve, use the untyped form
   and **report which was needed** (ADR 0006 open question 4).
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
   M4b's hand-written file-element holder and the two must not collide.
5. **`tokens=[...]`** listing every token the grammar will reference, mapped to the exact
   names/texts `fromNameOrText` accepts. Start with only what the throwaway rule needs.
6. **`fromNameOrText`** on `TypeSpecTokenTypes`: `@JvmStatic`, backed by a map registering
   both the token *name* and (for literal tokens) the token *text*
   ([ADR 0006](../adr/0006-grammar-toolchain.md) D5, F6). **Throws** on an unknown key.
   Existing constants get `@JvmField` if they do not have it. Do not change any existing
   token's identity or debug name — `TypeSpecLexerTest` and `TypeSpecSyntaxHighlighterTest`
   must pass **unedited**.
7. **The `mixin=` spike** ([ADR 0006](../adr/0006-grammar-toolchain.md) D7, F8). One
   throwaway rule with `mixin("throwaway")="…"` pointing at a trivial abstract class
   extending `ASTWrapperPsiElement`. Run `./gradlew generateParser`. Confirm the generated
   `*Impl` extends the mixin rather than `ASTWrapperPsiElement`. **Record the result as an
   amendment to ADR 0006 D7**, and if it fails, note ADR 0004 D2's narrowed caching story.
   Then delete the throwaway rule's mixin wiring if unused — but keep the finding.
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
- Assert by inspection and record verbatim: `plugin.xml` still has exactly one `<depends>`;
  `grep -ri "modules.ultimate\|platform.lsp\|NodeJS\|JavaScript" src/ build.gradle.kts
  settings.gradle.kts` is empty.
- `git status` shows nothing generated under `src/` — generation lands under `build/`
  ([ADR 0006](../adr/0006-grammar-toolchain.md) D4).

**Done when**
```bash
./gradlew clean build test verifyPlugin && ./gradlew build   # second run must reuse the configuration cache
```

**Risks / open questions**
- **Owner ratification of the IPGP bump** ([ADR 0006](../adr/0006-grammar-toolchain.md)
  open question 1). Do not block on it; flag it in the report.
- The migrated `generateLexer` may emit a byte-different `_TypeSpecLexer.java` than the
  hand-rolled task. `TypeSpecLexerTest` is the guard; if it goes red, **report the diff**,
  do not adjust the test.
- The `mixin=` spike may fail (F8). That is a *recorded outcome*, not a milestone failure.
- `elementTypeHolderClass` / `TypeSpecElementTypes` name collision — called out above because
  it is the kind of thing that produces a baffling compile error at the worst moment.

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
src/main/kotlin/simpli/fyi/plugins/typespec/psi/impl/TypeSpecNamedElementMixin.kt (create — IF the M5a spike passed)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecPsiImplUtil.kt    (create — @JvmStatic helpers)
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
   `TypeSpecNamedElement : PsiNameIdentifierOwner`. Every declaration in scope
   (`namespace`, `model`, model property, template parameter) implements it via `mixin=`
   (or `implements(...)` + `methods=[...]` if M5a's spike failed):
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
6. `@JvmStatic` on every `TypeSpecPsiImplUtil` method, `@JvmField` where a Java-visible field
   is needed — the generated `*Impl` classes are Java
   ([ADR 0006](../adr/0006-grammar-toolchain.md) D5).

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
- `kitchen-sink.tsp` is **not** yet expected to parse error-free — it exercises M5c
  constructs. Assert that instead in M5c.
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
- **`<` / `>` template-argument ambiguity** is the known hard part of TypeSpec's grammar.
  M5b only needs template *parameter lists* on declarations (`model Page<T>`), not template
  *arguments* in type position (`Page<Pet>`) — that is M5c. Deliberate: it keeps the hardest
  ambiguity out of the milestone that fixes the PSI contract.
- If the M5a spike failed, `psiImplUtilClass` + `methods=[...]` must be repeated on every
  declaration rule. Mechanical, more verbose, no functional loss in M5b — the loss lands in
  M5.5's caching ([ADR 0006](../adr/0006-grammar-toolchain.md) D7).
- Backtick stripping in `getName()` is easy to get subtly wrong (`` `foo` `` → `foo`, but a
  bare `foo` must not lose characters). Covered explicitly by `TypeSpecPsiContractTest`.
- Blockless `namespace Foo;` covering the rest of the file is a scoping oddity that the
  grammar must express as containment or M5.5's resolver walk gets harder. Flagged for
  `tsp-dev`: prefer whatever shape makes `getFileNamespace()` trivial.

---

### M5c — Remaining declarations, type expressions, spellchecking

**Goal.** The rest of plan 00 §M5's grammar list, on the shape M5b fixed. `kitchen-sink.tsp`
parses with no `PsiErrorElement`.

**In scope.** `op`, `interface`, `enum`, `union`, `alias`, `scalar`; decorator applications;
type expressions (union `|`, intersection `&`, array `[]`, template *arguments* `<>`,
optional `?`). Named-element contract extended to `op`, `interface`, `enum` + enum member,
`union` + union variant, `alias`, `scalar`
([ADR 0004](../adr/0004-reference-resolution-approach.md) D7.2's full list).

**Explicitly deferred** (unchanged from plan 00 §M5): projections; `dec`/`fn`/`extern`
declarations; value literals `#{}` / `#[]` beyond a coarse rule.

**Files**
```
src/main/grammars/TypeSpec.bnf                                           (modify)
src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecPsiImplUtil.kt   (modify)
src/main/kotlin/simpli/fyi/plugins/typespec/spellchecker/TypeSpecSpellcheckingStrategy.kt (create — GATED, see below)
src/main/resources/META-INF/plugin.xml                                   (modify — spellchecker.support + 2nd <depends>, GATED)
```

**Spellchecking tail — gated, do not write it unratified.** `spellchecker.support` lives in
`com.intellij.modules.spellchecker`, which needs a **second `<depends>`**. It is CE-available
(`IC/lib/modules/intellij.spellchecker.jar`), not an Ultimate dependency, and is the one
sanctioned exception to the one-`<depends>` rule — pre-approved in
[ADR 0003](../adr/0003-parser-definition-timing.md) D3 **and still pending owner
ratification**. If unratified when M5c starts, **ship M5c without it** and report; do not
add the `<depends>` on `tsp-architect`'s say-so.

**Acceptance (`tsp-tester`)**
- `TypeSpecParsingTest` grows a `.tsp`/`.txt` pair per new area: `Operation`, `Interface`,
  `Enum`, `Union`, `Alias`, `Scalar`, `Decorators`, `TypeUnion`, `TypeIntersection`,
  `TypeArray`, `TypeTemplateArgs`, `TypeOptional`. Same golden-review discipline
  ([ADR 0006](../adr/0006-grammar-toolchain.md) D8).
- **`KitchenSink` fixture parses with zero `PsiErrorElement`** — `doTest(true, true)` over
  `src/test/testData/lexer/kitchen-sink.tsp` (copied, not moved; M2's lexer test owns the
  original path). This is M5's real done-signal.
- `TypeSpecPsiContractTest` extended to every declaration kind added here.
- If spellchecking shipped: `./gradlew verifyPlugin` stays clean **and** `runIde` on a bare
  Community install still loads the plugin. `tsp-tester` reports the verbatim `<depends>`
  list.
- Full regression sweep, no edits to M5b's goldens. **A changed M5b golden means M5c
  destabilised the core grammar — report it, do not re-baseline it.**

**Done when**
```bash
./gradlew clean build test verifyPlugin
```

**Risks / open questions**
- **`<` / `>` ambiguity lands here in full.** `Page<Pet>` vs a less-than expression. Expect to
  need Grammar-Kit external rules or careful `pin` placement. This is the single most likely
  reason M5c stalls; if it does, split template arguments into M5d rather than weakening the
  recovery rules to make it "work".
- Decorators are one lexer token by design (`@Ns.name`,
  [ADR 0004](../adr/0004-reference-resolution-approach.md) F6). The grammar wraps that token
  in a decorator-application node; it does **not** split it. Splitting is a separate,
  breaking change to M2/M3 tests — [ADR 0004](../adr/0004-reference-resolution-approach.md)
  open question 1, owner's call.
- Second `<depends>` is unratified (above).

---

## After M5

[Plan 02](02-navigation.md) — **M5.5**, reference resolution and jump navigation. Its
prerequisite is **M5c green**, not M5b: its resolver targets `model`/`enum`/`union`/
`interface`/`alias`/`scalar`/`op`/`namespace`, and six of those eight only exist after M5c.
Plan 02's five-point prerequisite check runs first and is authorised to stop the milestone.
