# ADR 0006 — Grammar toolchain: the IPGP GrammarKit subplugin, not `org.jetbrains.grammarkit`

- Status: **Accepted.** **Supersedes [ADR 0002](0002-build-and-platform-baseline.md) D6.**
  D7 below (the `mixin=` question) is a *gated* decision: it is resolved by a spike inside
  M5a, and `tsp-dev` amends this ADR with the outcome.
- Date: 2026-09-02
- Deciders: `tsp-architect` (proposed), project owner (to ratify D3's version bump)
- Relates to: [ADR 0001](0001-highlighting-approach.md) (staged: lexer first, parser later),
  [ADR 0002](0002-build-and-platform-baseline.md) (IC 2025.2.6.3, JDK 21, Gradle 9.5,
  IPGP 2.16.0), [ADR 0005](0005-minimal-parser-definition-for-commenting.md) (the flat
  `ParserDefinition` this milestone replaces), [ADR 0004](0004-reference-resolution-approach.md)
  (D7's PSI contract, which M5 must satisfy), [plan 03](../plans/03-grammar-and-psi.md).

## Context

M5 turns the flat `TypeSpecFlatParser` (ADR 0005) into a real Grammar-Kit parse tree.
Before any `.bnf` is written, the build has to be able to *run* Grammar-Kit. ADR 0002 D6
settled the lexer half of that question in M2 by rejecting the Grammar-Kit Gradle plugin
and hand-rolling a `JavaExec` task against the IntelliJ-patched JFlex jar. That decision
was correct **at the time and for the reason given**. The reason has since evaporated in
the *opposite* direction from what D6 anticipated, so the decision has to be revisited
rather than merely extended to `generateParser`.

Findings below come from a `tsp-intellij-researcher` pass (2026-09-02) against the
IPGP changelog, the IPGP task/dependency-extension docs, the Grammar-Kit generator source
(`JavaParserGenerator.java`, `KnownAttribute.java`) and the `intellij-community` tag
`idea/252.25557.131` matching our pin. Citations are inline.

## Findings

### F1 — `org.jetbrains.grammarkit` (the standalone plugin) is archived

`JetBrains/gradle-grammar-kit-plugin` is archived; `2023.3.0.4` is its final release, and
its README now says: *"Migrate to the IntelliJ Platform Gradle Plugin and its
`org.jetbrains.intellij.platform.grammarkit` subplugin, which now handle all Grammar-Kit
integration."* The SDK page `tools-gradle-grammar-kit-plugin.html` still documents the
archived plugin and is **stale relative to the archive notice** — do not be misled by it.

ADR 0002 D6's premise ("latest release is `2023.3.0.3`; it predates Gradle 9 and the
configuration cache") was accurate and is now moot: the plugin it referred to is dead, and
a *maintained, first-party* integration exists that did not exist when D6 was written.

### F2 — The replacement is a subplugin of the plugin we already apply

Plugin id `org.jetbrains.intellij.platform.grammarkit`; adds `generateLexer` and
`generateParser`. Tooling dependencies come from the Dependencies Extension helpers
`grammarKit()` and `jflex()`. Both task types **extend `JavaExec`** — the same base class
our hand-rolled task uses, which is the direct evidence that configuration-cache
compatibility carries over rather than being re-litigated (ADR 0002 D6's fallback clause).

### F3 — Our pinned IPGP 2.16.0 has the tasks but not the safety fixes

From the IPGP changelog:

| Version | Relevant entry |
|---|---|
| 2.12.0 | Adds `org.jetbrains.intellij.platform.grammarkit` with `generateLexer`/`generateParser`. Raises the minimum Gradle to 9.0.0. |
| 2.17.0 | "Fix GrammarKit lexer cleanup to purge only its resolved output directory so shared parser output is preserved." |
| 2.18.0 | "Prevent GrammarKit lexer and parser cleanup from deleting shared output roots by purging only explicit generator outputs." |

Both fixes address exactly the failure mode we would create — a lexer task and a parser
task generating under one root, the first wiping the second's output. Our Gradle 9.5.0
already clears the 9.0.0 floor.

### F4 — Generation is build-time only; the CE constraint is untouched

The subplugin emits `.java` into a source set and never touches `plugin.xml`. The generated
parser's runtime imports are `IElementType`, `PsiElement`, `ASTNode`, `TokenSet`,
`com.intellij.lang.parser.GeneratedParserUtilBase` and `com.intellij.psi.util.PsiTreeUtil` —
all in `platform/core-api` / `core-impl`, all `xi:include`d into `PlatformLangPlugin.xml`
whose `<id>` is `com.intellij` and which itself declares `com.intellij.modules.platform`.
**`<depends>com.intellij.modules.platform</depends>` remains sufficient and remains the only
`<depends>` this milestone adds.** (The ADR 0003 D3 spellchecking exception is unrelated and
still pending owner ratification.)

### F5 — Grammar-Kit does not need to own the lexer

`generateParser` and `generateLexer` read independent inputs (`.bnf` vs `.flex`) and are
independent tasks. Grammar-Kit *can* derive a `.flex` from a `.bnf` `tokens` section; that is
an IDE action, not a requirement, and here it would be actively harmful — `_TypeSpecLexer.flex`
is the shipped, tested source of truth for tokenization (M2, `TypeSpecLexerTest`).

### F6 — `tokenTypeFactory` is the documented bridge to an existing token holder

`JavaParserGenerator.java:1099-1116`: when the global `tokenTypeFactory` attribute is set,
the generated element-type holder emits
`IElementType MODEL_KEYWORD = TypeSpecTokenTypes.fromNameOrText("model");` instead of
`new TypeSpecTokenType("model")`. That makes the generated parser reference **the same
`IElementType` instances the JFlex lexer already emits**, which is the entire requirement.

🔴 The string handed to the factory is the token **text** for a literal token
(`MODEL_KEYWORD='model'` → `"model"`) but the token **name** for a `regexp:` token
(`IDENTIFIER='regexp:…'` → `"IDENTIFIER"`). The factory must accept both keys per token.

The blunter alternative `generateTokens=false` emits no constants at all and is more
fragile; rejected.

### F7 — The `IFileElementType` and `TypeSpecFile` from M4b survive unchanged

Grammar-Kit never generates a file element type. `TypeSpecElementTypes.FILE` and
`TypeSpecFile : PsiFileBase` stay exactly as ADR 0005 left them. `ParsingTestCase` derives
its language from `definition.getFileNodeType().getLanguage()`, so keeping the instance is
also what keeps the parsing tests wired to TypeSpec. The root BNF rule must **not** declare
its own element type colliding with `FILE`.

### F8 — ⚠ `mixin=` may require two-pass generation, and support is unverified

The *archived* plugin's SDK page states plainly: *"The plugin does not support two-pass
generation. Therefore, it does not support method mixins."* Whether the **new** IPGP
subplugin lifts this is **unverified** — the researcher found no statement either way.
This is the single highest-value unknown in the milestone, because ADR 0004 D7.2 wants one
shared named-element base across ~12 declaration rules, which is the textbook `mixin=` case.

## Decision

**D1. Adopt `org.jetbrains.intellij.platform.grammarkit`. Retire the hand-rolled JFlex
`JavaExec` task.** ADR 0002 D6 is superseded, not amended: its conclusion ("no Grammar-Kit
Gradle plugin") is reversed, and its stated fallback (commit `idea-flex.skeleton`, or commit
generated `.java`) is withdrawn as unnecessary. Migrating the lexer task at the same time as
adding the parser task is deliberate — running one generated by IPGP and one by hand is two
mechanisms for one job and would rot.

**D2. Lexer and parser generate into disjoint output roots.**
`build/generated/sources/grammarkit-lexer/java/main` and
`build/generated/sources/grammarkit-parser/java/main`. This is what makes F3's shared-root
bug unreachable *by construction* rather than by version luck, and it lets the lexer task be
handed straight to `sourceSets.main.java.srcDir(taskProvider)` in exclusive-ownership mode.
Keep the explicit `tasks.named("compileKotlin") { dependsOn(...) }` as belt-and-braces.

**D3. Bump IPGP `2.16.0 → 2.18.1` in `settings.gradle.kts` as the first act of M5a.**
Rationale in F3. This is a **build-wide** change touching the platform plugin we depend on
for everything, so it ships as its own commit inside M5a with a full `clean build test
verifyPlugin` between it and any grammar work. If the bump regresses anything, the fallback
is to stay on 2.16.0 **with `purgeOldFiles = false`** on both tasks, which D2's disjoint
roots make safe; record that as an amendment rather than silently diverging.

**D4. Generate at build time; do not commit generated sources.** Consistent with what M2
already does for the lexer. Noted for the record: JetBrains' own `simple_language_plugin`
commits to `src/main/gen` and uses the IDE action with no Gradle generation — a legitimate
alternative we are **not** taking. Consequence to accept: after adding the `.bnf`, one
`./gradlew generateParser generateLexer` is required before the Kotlin editor can resolve
the generated PSI classes; until then the IDE shows red while the CLI build is green. That
is expected, not a defect. `.gitignore` must keep `build/` ignored (it does).

**D5. The `.bnf` consumes the hand-written lexer's tokens via `tokenTypeFactory`.**
Per F5/F6. `TypeSpecTokenTypes` grows one `@JvmStatic fun fromNameOrText(key: String):
IElementType` backed by a map registering **both** the token name and, for literal tokens,
the token text. It **throws** on an unknown key. It must never mint a new `IElementType` —
a silently-minted duplicate produces a parser that can never match its own lexer, and the
symptom (everything is an error element) points nowhere near the cause.

`@JvmStatic` and `@JvmField` are mandatory: the generated code is **Java** calling into a
Kotlin `object` statically. Same applies to every method on `psiImplUtilClass`.

**D6. Error recovery is designed in from the first rule, not retrofitted.**
Every declaration rule carries a `pin`; every rule inside a `*`/`+` loop carries a
`recoverWhile` pointing at a `private`, zero-length `!(...)` predicate whose stop set always
includes `'}'` and `';'`. Pin *after* the point of no return (`model_property ::= identifier
':' type ';'` pins at 2, the `':'`, not at 1). `extendedPin=true` is the default; leave it.
Rationale: without a pin a failed rule rolls back wholesale and one typo destroys the file's
PSI — which would take highlighting, folding, structure view and navigation with it.

**D7. `mixin=` vs `psiImplUtilClass` is decided by a spike in M5a, before the grammar is
written.** Preferred outcome, if the spike passes: `mixin=` for the shared declaration
hierarchy (one `TypeSpecNamedElementMixin` implementing `getName`/`getNameIdentifier`/
`getTextOffset`/`getPresentation` for all ~12 declaration rules) plus `psiImplUtilClass` for
one-off accessors. Reason to prefer it: static utils cannot hold per-element state, and
M5.5's resolver wants a `CachedValue` on reference elements (ADR 0004 D2).
Fallback if the spike fails (F8): `psiImplUtilClass` + `implements(...)` throughout, which is
confirmed to work single-pass, at the cost of repeating `methods=[...]` on every declaration
rule and having no place to hang a per-element cache — which would in turn push ADR 0004 D2's
caching entirely onto `ResolveCache` + `CachedValuesManager.getCachedValue(psiFile)`.
**`tsp-dev` amends this ADR with the spike result and, if it fails, flags the ADR 0004 D2
consequence explicitly.** The spike is one throwaway rule; it must not become a grammar.

**D8. Golden parse trees are regenerated, then read.** The flat→structured migration
invalidates every existing PSI-shape expectation. `-Didea.tests.overwrite.data=true`
regenerates the `.txt` goldens in bulk; `ParsingTestCase` also writes a missing golden and
fails the first run. **Neither is acceptance.** `tsp-tester` reads every generated tree and
confirms it is correct before committing — the reviewed diff *is* the milestone's evidence.
A wrong tree committed as a golden is permanently frozen and will be defended by the test
suite forever.

**D9. `ensureCorrectReparse` stays on.** `ParsingTestCase.doSanityChecks` re-parses the file
and asserts an identical tree with no PSI events. A non-deterministic grammar fails there
with a confusing message. The escape hatch `isCheckNoPsiEventsOnReparse()` is `@Deprecated`
with the comment *"Please fix your parser instead of overriding this method."* We take that
advice: overriding it is **banned** in this repo.

## Consequences

- `build.gradle.kts` loses ~20 lines of hand-rolled `JavaExec`/`Configuration` plumbing and
  ADR 0002 D6's comment block; `settings.gradle.kts` gains a version bump.
- One `<depends>` unchanged (F4). The CE constraint is not touched by this ADR.
- M2's `_TypeSpecLexer.flex` and `TypeSpecLexerTest` are unchanged by design (F5). If the
  migrated `generateLexer` produces a byte-different lexer, that is a **finding to report**,
  not something to paper over — `TypeSpecLexerTest` is the guard and must stay green
  without edits.
- `TypeSpecTokenTypes` gains a factory method and a name/text map — the one place M2's
  shipped code is modified.
- ADR 0002 D6 is superseded. ADR 0002's other decisions (D1–D5, D7) are untouched.
- A `mixin=` failure (F8) has a known, costed fallback and does not block the milestone.

## Citations

- [gradle-grammar-kit-plugin README](https://github.com/JetBrains/gradle-grammar-kit-plugin/blob/master/README.md) — archive notice, final release `2023.3.0.4` (F1).
- [SDK: tools-gradle-grammar-kit-plugin.html](https://plugins.jetbrains.com/docs/intellij/tools-gradle-grammar-kit-plugin.html) — stale page; source of the two-pass/mixin limitation quote (F1, F8).
- [SDK: IPGP Plugins](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-plugins.html) § GrammarKit — plugin id and task list (F2).
- [SDK: IPGP Dependencies Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html) § "Grammar and Parser" — `grammarKit()`, `jflex()` (F2).
- [SDK: IPGP Tasks](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html) § `generateLexer` / `generateParser` — exclusive-vs-shared output root semantics, `pathToParser`, `pathToPsiRoot`, `JavaExec` base (F2, D2).
- [IPGP CHANGELOG](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/main/CHANGELOG.md) — 2.12.0 / 2.17.0 / 2.18.0 entries (F3).
- [Grammar-Kit `JavaParserGenerator.java`](https://github.com/JetBrains/Grammar-Kit/blob/master/generator/src/org/intellij/grammar/generator/JavaParserGenerator.java) — `generateElementTypesHolder` imports (F4), `tokenCreateCall` L1099-1116 (F6), `Factory.createElement` L1152-1165 (F7).
- [Grammar-Kit `KnownAttribute.java`](https://github.com/JetBrains/Grammar-Kit/blob/master/bnf-language/src/org/intellij/grammar/KnownAttribute.java) — attribute names/scopes/defaults, incl. `generateTokenAccessors` default `false` (D5, plan 03).
- [Grammar-Kit HOWTO](https://github.com/JetBrains/Grammar-Kit/blob/master/HOWTO.md) §2.2 (`recoverWhile` contract), §3.3 (`mixin`), §3.4 (`psiImplUtilClass`), §3.5 (stubs are hand-written) (D6, D7).
- [SDK: implementing-parser-and-psi.html](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html) — `IFileElementType`, `PsiFile`, `PsiNamedElement`, the `$Language$TokenSets` guidance (F7, plan 03).
- [SDK: parsing-test.html](https://plugins.jetbrains.com/docs/intellij/parsing-test.html) and `ParsingTestCase` source — golden mechanics, `idea.tests.overwrite.data`, `ensureCorrectReparse` (D8, D9).

## Open questions for the project owner

1. **Ratify the IPGP `2.16.0 → 2.18.1` bump** (D3). It is a build-wide dependency change on
   the plugin that resolves our IDE, our test framework and our verifier. Low risk, but not
   `tsp-architect`'s call to make silently.
2. **⚠ Unverified (F8): `mixin=` / two-pass support.** Gated behind M5a's spike; no owner
   action needed unless the fallback is taken, in which case ADR 0004 D2's caching story
   narrows (see D7).
3. Whether `GenerateParserTask` is `@CacheableTask` for the *remote* build cache —
   unverified, and of nil practical impact on a single-machine repo. Recorded, not chased.
4. The task FQCNs `org.jetbrains.intellij.platform.gradle.tasks.GenerateLexerTask` /
   `GenerateParserTask` were inferred from the docs' "Sources:" metadata; the package is not
   confirmed. If the typed `tasks.named<...>` form does not resolve, use the untyped
   `tasks.named("generateLexer") { ... }`. `tsp-dev` reports which form was needed.
