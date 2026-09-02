# ADR 0006 — Grammar toolchain: the IPGP GrammarKit subplugin, not `org.jetbrains.grammarkit`

- Status: **Accepted and finalised (revision 2, 2026-09-02).**
  **Supersedes [ADR 0002](0002-build-and-platform-baseline.md) D6.**
  **All open questions are closed.** D3's version bump is **ratified by the project owner**;
  D7's `mixin=` gate is **closed as PASSED with a scope correction** (see F8, rewritten);
  D2's wiring recipe is **rewritten** to work around a real IPGP 2.18.1 regression (F9, new).
  No part of this ADR is now gated on a spike.
- Date: 2026-09-02 (revised same day after a second `tsp-intellij-researcher` pass)
- Deciders: `tsp-architect` (proposed), project owner (ratified D3 and the ADR 0003 D3
  second `<depends>`)
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

🔧 **Citation correction (revision 2).** The two-pass/method-mixin limitation quote used in
F8 below was previously attributed to that SDK page. It is not there — the sentence *"The
plugin does not support two-pass generation. Therefore, it does not support method mixins."*
belongs to the **archived plugin's own `README.md:5` at commit `9933414`**. The attribution
matters because the claim is about the *archived* plugin's Gradle wrapper, not about
Grammar-Kit itself, and not about IPGP.

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
**`<depends>com.intellij.modules.platform</depends>` remains sufficient, and remains the only
`<depends>` *this milestone's grammar work* adds.** The ADR 0003 D3 spellchecking exception is
unrelated to Grammar-Kit and is now **ratified and CE-CONFIRMED** — see F10.

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

### F8 — ✅ RESOLVED: class-extension `mixin=` works single-pass; *method* mixins do not — and neither does `psiImplUtilClass`

Revision 2 replaces the previous "unverified, spike-gated" text. The researcher settled this
against Grammar-Kit's own golden test.

**What works single-pass.** `mixin=` used as a **base class** does not require the mixin class
to be on the generator's classpath. Grammar-Kit's own generator golden test
(`testData/generator/PsiGen.bnf`) uses an intentionally **unresolvable** `MyRefImpl` class as a
mixin, generates single-pass, and the emitted `*Impl` correctly `extends MyRefImpl`. The
generator writes the `extends` clause from the *string* attribute; it never loads the class.

**What does not work single-pass.** `methods=[...]` — "method mixins" — is a different
mechanism: Grammar-Kit resolves the listed method signatures **by reflection/bytecode
(`AsmHelper`) against the generator's own classpath**. In a Gradle build the mixin class is
compiled *after* generation, so it is not there. This is the archived plugin README's
"two-pass generation" statement (F1's corrected citation), and it is a property of *how
`methods=` resolves*, not of the Gradle wrapper.

🔴 **Correction to revision 1.** Revision 1's D7/F8 claimed `psiImplUtilClass` was "confirmed
to work single-pass" and offered it as the fallback. **That claim was wrong.**
`psiImplUtilClass` is resolved through the *same* `AsmHelper` classpath wall as `methods=[...]`
— it only takes effect when a rule lists `methods=[…]` naming the util methods, and those
signatures must be resolvable at generation time. It is **not** a safe fallback; it has the
identical limitation. Any plan text offering it as the escape hatch is void.

**The single-pass-safe pattern, and the one this project uses:**

```
mixin("model_statement")      = "…psi.impl.TypeSpecNamedElementMixin"   // base class, string only
implements("model_statement") = "…psi.TypeSpecNamedElement"             // hand-written interface
// and NEVER: methods=[getName getNameIdentifier …]
```

The methods are implemented **directly on the hand-written mixin class**, which satisfies the
hand-written interface by ordinary Java/Kotlin inheritance. Grammar-Kit is not asked to know
anything about them. This is sound, needs no second pass, and — critically — preserves
[ADR 0004](0004-reference-resolution-approach.md) D2's requirement for a **per-element
`CachedValue`**, because the mixin is a real instance-bearing base class.

### F9 — 🔴 REGRESSION: `GenerateParserTask.targetRootOutputDir` is `@Internal` on IPGP 2.18.1

Found by the revision-2 research pass, and it invalidates revision 1's D2 wiring.

On IPGP **2.16.0**, `GenerateParserTask.targetRootOutputDir` was annotated `@OutputDirectory`.
On **2.18.1** it is `@Internal`. The restoration exists only on an unreleased `main` branch
(IPGP issue **#2186**, fixed by commit `5f298077`, **not in any release as of 2.18.1**).

Two consequences, both fatal to the "exclusive-ownership, hand the provider to `srcDir`" form:

1. `sourceSets.main.java.srcDir(tasks.generateParser)` **does not register a source root** on
   2.18.1, because Gradle derives the directory from the task's declared *outputs* and there
   are none.
2. `srcDir(tasks.generateParser.flatMap { it.targetRootOutputDir })` registers the directory
   but **carries no implicit task dependency** — `flatMap` only propagates a task dependency
   from a property that is a declared output. Compilation will therefore race generation.

The working recipe (F9-safe) is D2, rewritten below. It works identically on 2.16.0, 2.18.1
and any future release that restores the annotation, so it is what we use regardless.

### F10 — ✅ CONFIRMED: `com.intellij.modules.spellchecker` is Community-available

Verified **directly against our pinned distribution**, not inferred: `IC-252.28539.97`'s
`product-info.json` lists the module, and the module's own descriptor
(`lib/modules/intellij.spellchecker.jar!/intellij.spellchecker.xml`) declares **only**
`intellij.platform.*` and bundled-library dependencies — nothing Ultimate, nothing
`com.intellij.modules.ultimate`. The `spellchecker.support` EP is `dynamic="true"`, so it
needs no IDE restart.

Combined with the project owner's explicit approval ("as long as it's available in Community
Edition"), this **closes [ADR 0003](0003-parser-definition-timing.md) D3's open question as
CONFIRMED**. The second `<depends>` is sanctioned and lands in M5c.

### F11 — `elementTypeHolderClass` naming, and the generated `Factory`

`TypeSpecTypes` (in `…psi`) is confirmed as the conventional name — Grammar-Kit's own
convention is `<Lang>Types`. It **coexists normally** with M4b's hand-written
`TypeSpecElementTypes`; they are different classes with different jobs and the latter must
survive (F7).

The generated holder also emits a nested `Factory`. Therefore:

- `TypeSpecParserDefinition.createElement(node)` → `TypeSpecTypes.Factory.createElement(node)`
- `TypeSpecParserDefinition.getFileNodeType()` → **still** `TypeSpecElementTypes.FILE`

### F12 — Task metadata, confirmed

- `GenerateParserTask` and `GenerateLexerTask` **are** `@CacheableTask` on 2.18.1 (confirmed by
  bytecode inspection). Revision 1's open question 3 is closed.
- FQCNs confirmed: `org.jetbrains.intellij.platform.gradle.tasks.GenerateParserTask` and
  `org.jetbrains.intellij.platform.gradle.tasks.GenerateLexerTask`. Both extend
  `org.gradle.api.tasks.JavaExec`. Both are DSL-accessible as `tasks.generateParser` /
  `tasks.generateLexer`. Revision 1's open question 4 is closed — the typed form resolves.

## Decision

**D1. Adopt `org.jetbrains.intellij.platform.grammarkit`. Retire the hand-rolled JFlex
`JavaExec` task.** ADR 0002 D6 is superseded, not amended: its conclusion ("no Grammar-Kit
Gradle plugin") is reversed, and its stated fallback (commit `idea-flex.skeleton`, or commit
generated `.java`) is withdrawn as unnecessary. Migrating the lexer task at the same time as
adding the parser task is deliberate — running one generated by IPGP and one by hand is two
mechanisms for one job and would rot.

**D2. Lexer and parser generate into disjoint output roots, wired explicitly.**
`build/generated/sources/grammarkit-lexer/java/main` and
`build/generated/sources/grammarkit-parser/java/main`. Disjoint roots make F3's shared-root
bug unreachable *by construction* rather than by version luck.

🔧 **Rewritten in revision 2.** Revision 1 said to "hand the task provider straight to
`srcDir(taskProvider)` in exclusive-ownership mode." **That does not work on IPGP 2.18.1** —
see F9. The following is the wiring recipe `tsp-dev` implements; it is not a sketch:

```kotlin
// build.gradle.kts
tasks {
    generateLexer {
        sourceFile = file("src/main/grammars/_TypeSpecLexer.flex")
        // pathToClass is REQUIRED — do not rely on targetOutputDir (deprecation status unverified)
        targetRootOutputDir = file("build/generated/sources/grammarkit-lexer/java/main")
        pathToClass = "simpli/fyi/plugins/typespec/lexer/_TypeSpecLexer.java"
        purgeOldFiles = true
    }
    generateParser {
        sourceFile = file("src/main/grammars/TypeSpec.bnf")
        targetRootOutputDir = file("build/generated/sources/grammarkit-parser/java/main")
        // BOTH are required together on 2.18.1; setting only one is a configuration error
        pathToParser = "simpli/fyi/plugins/typespec/parser/TypeSpecParser.java"
        pathToPsiRoot = "simpli/fyi/plugins/typespec/psi"
        purgeOldFiles = true
    }
}

sourceSets.main {
    // explicit .flatMap of the property — NOT srcDir(taskProvider), which registers nothing
    java.srcDir(tasks.generateLexer.flatMap { it.targetRootOutputDir })
    java.srcDir(tasks.generateParser.flatMap { it.targetRootOutputDir })
}

// MANDATORY: .flatMap carries no implicit task dependency while the property is @Internal (F9).
// Without these two lines compilation races generation and fails intermittently.
tasks.named("compileKotlin") { dependsOn(tasks.generateLexer, tasks.generateParser) }
tasks.named("compileJava")   { dependsOn(tasks.generateLexer, tasks.generateParser) }
```

The explicit `dependsOn` is **not** belt-and-braces here as revision 1 called it — it is
load-bearing. Removing it produces a build that passes on a warm tree and fails from `clean`.
This form is correct on 2.16.0 and 2.18.1 alike; if a later IPGP restores `@OutputDirectory`
(IPGP #2186), the recipe keeps working and the `dependsOn` merely becomes redundant. Do not
"simplify" it back on that basis without re-reading F9.

**D3. Bump IPGP `2.16.0 → 2.18.1` in `settings.gradle.kts` as the first act of M5a.**
✅ **Ratified by the project owner (2026-09-02). No longer an open question.**
Rationale in F3. This is a **build-wide** change touching the platform plugin we depend on
for everything, so it ships as its own commit inside M5a with a full `clean build test
verifyPlugin` between it and any grammar work. If the bump regresses anything, the fallback
is to stay on 2.16.0 **with `purgeOldFiles = false`** on both tasks, which D2's disjoint
roots make safe; record that as an amendment rather than silently diverging. Note that D2's
wiring recipe is version-agnostic, so taking the fallback requires no wiring change.

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
Kotlin `object` statically. (Revision 1 added "same applies to every method on
`psiImplUtilClass`" — struck, because per F8 we do not use `psiImplUtilClass` at all. The
same `@JvmStatic`/`@JvmField` rule does apply to any Kotlin member the generated Java touches,
including the mixin base classes of D7.)

**D6. Error recovery is designed in from the first rule, not retrofitted.**
Every declaration rule carries a `pin`; every rule inside a `*`/`+` loop carries a
`recoverWhile` pointing at a `private`, zero-length `!(...)` predicate whose stop set always
includes `'}'` and `';'`. Pin *after* the point of no return (`model_property ::= identifier
':' type ';'` pins at 2, the `':'`, not at 1). `extendedPin=true` is the default; leave it.
Rationale: without a pin a failed rule rolls back wholesale and one typo destroys the file's
PSI — which would take highlighting, folding, structure view and navigation with it.

**D7. ✅ DECIDED (revision 2 — the M5a spike gate is CLOSED as PASSED, with a scope
correction). Use `mixin=` as a base class + `implements=` a hand-written interface. Never use
`methods=[...]`, and never use `psiImplUtilClass`.**

The gate is closed on evidence (F8), not on a spike `tsp-dev` still has to run. The shape:

- `mixin("<rule>") = "…psi.impl.TypeSpecNamedElementMixin"` — a **hand-written** abstract
  Kotlin class extending `ASTWrapperPsiElement`, implementing `getName` /
  `getNameIdentifier` / `getTextOffset` / `getPresentation` / `setName` **as ordinary
  methods on itself**. Grammar-Kit only ever sees this as a string and emits `extends`.
- `implements("<rule>") = "…psi.TypeSpecNamedElement"` — a **hand-written** interface
  extending `PsiNameIdentifierOwner`. The generated PSI interface extends it; the mixin
  satisfies it by inheritance.
- **`methods=[...]` is banned in this repo's `.bnf`.** So is `psiImplUtilClass`. Both resolve
  through Grammar-Kit's `AsmHelper` against the *generator's* classpath, which in a Gradle
  build cannot contain classes this build has not compiled yet. Revision 1's claim that
  `psiImplUtilClass` was a safe single-pass fallback was **wrong and is retracted**.

Consequence, positively stated: because the mixin is a real instance-bearing base class,
[ADR 0004](0004-reference-resolution-approach.md) D2's **per-element `CachedValue`** is
preserved exactly as designed. Revision 1's "narrowed caching story" contingency — pushing
caching onto `ResolveCache` + `CachedValuesManager.getCachedValue(psiFile)` — is **withdrawn
as unnecessary**. M5.5 plans against the per-element cache.

There is no longer a throwaway-mixin spike to run. M5a instead carries **two empirical
acceptance checks** (see D10), which are verification of a decided design, not a gate on it.

**D10. M5a verifies three things empirically that research could not settle from documents.**
These are acceptance criteria, not decision gates — a failure is a finding to report, and the
design above does not change without a new ADR revision:

1. **Kotlin↔generated-Java resolution.** Grammar-Kit emits **Java** `*Impl` classes that
   `extend` a **Kotlin** mixin, with the generated Java added to `sourceSets.main` via
   `srcDir`. Standard Kotlin Gradle Plugin mixed-source-set compilation should resolve this
   in either direction, but it is **asserted, not assumed**: M5a's throwaway rule uses a real
   Kotlin mixin base class and the build must compile.
2. **`GenerateLexerTask.pathToClass` vs `targetOutputDir`.** Exact deprecation status is
   unverified. D2 uses `pathToClass`; if it warns or fails, report the exact message.
3. **A future IPGP with the `rootOutputDirectory` restoration (IPGP #2186).** If 2.19.0 ships
   it before M5a lands, it may be preferred — but D2's `pathToParser`/`pathToPsiRoot` form
   works on both, so **use D2 regardless** and do not delay M5a waiting for a release.

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
  ADR 0002 D6's comment block; it gains D2's explicit `srcDir(...flatMap...)` +
  `dependsOn(...)` wiring. `settings.gradle.kts` gains a version bump.
- The grammar work adds **no** `<depends>` (F4). The *separate*, owner-ratified spellchecking
  `<depends>` on `com.intellij.modules.spellchecker` lands in M5c and is CE-confirmed (F10).
  The CE constraint is satisfied either way.
- M2's `_TypeSpecLexer.flex` and `TypeSpecLexerTest` are unchanged by design (F5). If the
  migrated `generateLexer` produces a byte-different lexer, that is a **finding to report**,
  not something to paper over — `TypeSpecLexerTest` is the guard and must stay green
  without edits.
- `TypeSpecTokenTypes` gains a factory method and a name/text map — the one place M2's
  shipped code is modified.
- ADR 0002 D6 is superseded. ADR 0002's other decisions (D1–D5, D7) are untouched.
- ADR 0004 D2's per-element `CachedValue` design **survives intact** (D7). No downstream
  milestone needs replanning.
- ADR 0003 D3's open question is closed CONFIRMED by F10 + owner approval.

## Citations

- [gradle-grammar-kit-plugin README](https://github.com/JetBrains/gradle-grammar-kit-plugin/blob/master/README.md) — archive notice, final release `2023.3.0.4` (F1); **`README.md:5` @ commit `9933414`** is the true source of *"The plugin does not support two-pass generation. Therefore, it does not support method mixins."* (F1 correction, F8).
- [SDK: tools-gradle-grammar-kit-plugin.html](https://plugins.jetbrains.com/docs/intellij/tools-gradle-grammar-kit-plugin.html) — stale page documenting the archived plugin. ⚠ It does **not** contain the two-pass/mixin sentence; revision 1 mis-attributed it here (F1).
- [Grammar-Kit `testData/generator/PsiGen.bnf`](https://github.com/JetBrains/Grammar-Kit/blob/master/testData/generator/PsiGen.bnf) — golden test proving class-extension `mixin=` generates single-pass against an unresolvable class (F8).
- Grammar-Kit `AsmHelper` / `JavaHelper` — classpath-reflection resolution behind `methods=[...]` **and** `psiImplUtilClass` (F8).
- [IPGP issue #2186](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2186) and commit `5f298077` — `targetRootOutputDir` `@OutputDirectory` → `@Internal` regression on 2.18.1, restored only on unreleased `main` (F9, D2).
- `IC-252.28539.97` `product-info.json`; `lib/modules/intellij.spellchecker.jar!/intellij.spellchecker.xml` — `com.intellij.modules.spellchecker` declares only `intellij.platform.*` / bundled-library deps; `spellchecker.support` is `dynamic="true"` (F10).
- IPGP `2.18.1` bytecode — `GenerateParserTask` / `GenerateLexerTask` are `@CacheableTask`; FQCN package `org.jetbrains.intellij.platform.gradle.tasks`; both extend `org.gradle.api.tasks.JavaExec` (F12).
- [SDK: IPGP Plugins](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-plugins.html) § GrammarKit — plugin id and task list (F2).
- [SDK: IPGP Dependencies Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html) § "Grammar and Parser" — `grammarKit()`, `jflex()` (F2).
- [SDK: IPGP Tasks](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html) § `generateLexer` / `generateParser` — `pathToParser`, `pathToPsiRoot`, `pathToClass`, `JavaExec` base (F2, D2).
- [IPGP CHANGELOG](https://github.com/JetBrains/intellij-platform-gradle-plugin/blob/main/CHANGELOG.md) — 2.12.0 / 2.17.0 / 2.18.0 entries (F3).
- [Grammar-Kit `JavaParserGenerator.java`](https://github.com/JetBrains/Grammar-Kit/blob/master/generator/src/org/intellij/grammar/generator/JavaParserGenerator.java) — `generateElementTypesHolder` imports (F4), `tokenCreateCall` L1099-1116 (F6), `Factory.createElement` L1152-1165 (F7, F11).
- [Grammar-Kit `KnownAttribute.java`](https://github.com/JetBrains/Grammar-Kit/blob/master/bnf-language/src/org/intellij/grammar/KnownAttribute.java) — attribute names/scopes/defaults, incl. `generateTokenAccessors` default `false` (D5, plan 03).
- [Grammar-Kit HOWTO](https://github.com/JetBrains/Grammar-Kit/blob/master/HOWTO.md) §2.2 (`recoverWhile` contract), §3.3 (`mixin`), §3.4 (`psiImplUtilClass`), §3.5 (stubs are hand-written) (D6, D7).
- [SDK: implementing-parser-and-psi.html](https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html) — `IFileElementType`, `PsiFile`, `PsiNamedElement`, the `$Language$TokenSets` guidance (F7, plan 03).
- [SDK: parsing-test.html](https://plugins.jetbrains.com/docs/intellij/parsing-test.html) and `ParsingTestCase` source — golden mechanics, `idea.tests.overwrite.data`, `ensureCorrectReparse` (D8, D9).

## Open questions

**None. All four of revision 1's open questions are closed:**

1. ~~Ratify the IPGP `2.16.0 → 2.18.1` bump~~ — ✅ **ratified by the project owner**, 2026-09-02 (D3).
2. ~~Unverified: `mixin=` / two-pass support~~ — ✅ **closed PASSED with a scope correction** (F8, D7).
   `mixin=` as a base class works; `methods=[...]` and `psiImplUtilClass` do not and are banned.
   ADR 0004 D2's caching design is unaffected.
3. ~~Is `GenerateParserTask` `@CacheableTask`?~~ — ✅ **yes**, both tasks, on 2.18.1 (F12).
4. ~~Are the task FQCNs correct?~~ — ✅ **confirmed**; the typed `tasks.named<...>` form resolves (F12).

Additionally, [ADR 0003](0003-parser-definition-timing.md) D3's open question (the second
`<depends>` on `com.intellij.modules.spellchecker`) is **closed CONFIRMED** by F10 plus the
owner's explicit approval. It lands in M5c.

**Not open questions, but M5a acceptance checks** (D10): Kotlin-mixin ↔ generated-Java
resolution across the source set; `pathToClass` deprecation status; whether a released IPGP
restores `rootOutputDirectory`. None of these blocks starting M5a.
