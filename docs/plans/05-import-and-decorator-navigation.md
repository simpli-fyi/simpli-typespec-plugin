# Plan 05 — Import and decorator navigation

Closes two gaps the owner reported from a real IDE session, neither of which navigates on
Cmd-click today:

1. **Import statements**, both forms — `import "../master-data/branch.tsp";` and
   `import "@typespec/openapi";`
2. **Decorator names** — `@TypeSpec.OpenAPI.info(#{ version: "1.5.1" })`

Decisions this plan executes: [ADR 0009](../adr/0009-decorator-reference-strategy.md) (decorator
references as sub-ranges on the existing single token — **no lexer change, no golden change, no
highlighting change**) and [ADR 0010](../adr/0010-library-import-resolution.md) (library imports
resolve by targeted `node_modules` lookup; `tspScope` keeps excluding `node_modules`).

Baseline: 202 tests, `verifyPlugin` Compatible, corpus gate absolute over 83 files, two
`<depends>`. **All three must still hold at every milestone boundary.**

## Numbering

These are **M5.6a–M5.6f**, not M7. M7 is already "Compatibility and release readiness" in
[plan 00](00-milestones.md), and ADR 0002/0003 cite M6/M7 by number — renumbering would falsify
them ([plan 00](00-milestones.md) §D5). This work is a direct continuation of M5.5's navigation
and sits before M6, so it takes the next decimal.

## Sequencing and dependencies

```
M5.6a  import target resolution engine  ──┬──►  M5.6b  import string references
                                          │
                                          └──►  M5.6d  decorator references ──► M5.6e  find-usages
M5.6c  name-based resolver core  ─────────────►  M5.6d                          words scanner
```

- **M5.6a before M5.6d.** Not just tidiness: `@TypeSpec.OpenAPI.info` can only resolve once
  `openapi/lib/decorators.tsp` is in the file's tier B closure, which is exactly what M5.6a
  delivers. Doing M5.6d first ships a decorator reference that resolves nothing in the owner's
  real project and looks like it does not work.
- **M5.6c before M5.6d.** The resolver is PSI-shaped today; decorator segments are not
  `TypeSpecIdentifier`s.
- **M5.6b and M5.6c are independent** and can be done in either order.
- **Nothing here depends on [ADR 0008](../adr/0008-tier-c-file-cap.md).** No milestone changes
  `TIER_C_FILE_CAP`, `tspScope`, or the tier C candidate pool. The only coupling runs forward:
  when the stub index (ADR 0008 option C, M6.5) is built it **must** inherit the `node_modules`
  exclusion (ADR 0010 §Consequences). Do not sequence this plan behind an ADR 0008 decision.

---

### M5.6a — Import target resolution engine (library + relative), no UI yet

**Goal:** one resolver that turns any import specifier into a `TypeSpecFile`, matching upstream's
rules, entering `node_modules` only by targeted lookup. No user-visible change yet —
`TypeSpecImportGraph`'s tier B closure silently starts following library imports.

**Files:**
- create `src/main/kotlin/simpli/fyi/plugins/typespec/resolve/TypeSpecImportResolver.kt`
- modify `src/main/kotlin/simpli/fyi/plugins/typespec/resolve/TypeSpecImportGraph.kt` —
  `resolveImportTarget` delegates to the new object; delete the `startsWith("./")` bail-out and
  the hardcoded `findChild("main.tsp")`
- modify `build.gradle.kts` — add the bundled JSON parser (ADR 0010 D3)
- create test fixtures under `src/test/testData/imports/` (**not** under
  `src/test/testData/corpus/` — `TypeSpecCorpusTest` walks that tree and would adopt them)

**Approach:**
- `TypeSpecImportResolver.resolve(from: TypeSpecFile, specifier: String): TypeSpecFile?`
  - relative (`./`, `../`) → `VirtualFile.findFileByRelativePath` from the importing file's parent
  - bare → walk parents via `VirtualFile.getParent()`, at each level `findChild("node_modules")`
    then the (possibly `@scope/`-split) package directory. Stop at the VFS root. This walk is the
    workspace-monorepo case and is not optional.
  - directory (either form) → shared `entryPointOf(dir)`: `exports["."]` `"typespec"` condition →
    `tspMain` → `main` → `main.tsp` (ADR 0010 D2; two of six real packages need more than
    `lib/main.tsp`)
  - canonicalise the result (`VirtualFile.canonicalFile`) before `PsiManager.findFile` (ADR 0010 D4)
  - a non-`.tsp` target (`../dist/src/tsp-index.js`) returns `null`, silently
  - `ProgressManager.checkCanceled()` in the upward walk
- Read `package.json` via `VfsUtilCore.loadText`, parse with the bundled parser, tolerate malformed
  JSON by returning `null` (never throw out of a resolve).
- Cache the specifier→file mapping per importing file alongside the existing
  `CachedValuesManager` usage in `TypeSpecImportGraph`; do **not** invent a project-level cache.
- **Do not touch `TypeSpecSearchScopes`.** Any diff to that file in this milestone is a review
  failure.

**Acceptance (`tsp-tester`, `TypeSpecImportResolverTest`):**
- relative file, relative directory-with-`main.tsp`, relative directory-with-`package.json`
- bare `@scope/pkg` found in a `node_modules` **two directories up** from the importing file
- entry point picked from `tspMain` when it is *not* `lib/main.tsp` (mirror `@typespec/protobuf`'s
  `lib/proto.tsp` and `@typespec/compiler`'s `lib/std/main.tsp`)
- entry point picked from `exports["."].typespec` when both it and `tspMain` are present
- fallback to `main.tsp` when `package.json` has neither, and when there is no `package.json`
- missing package → `null`, no exception; malformed `package.json` → `null`, no exception
- `.js` target → `null`
- `TypeSpecImportGraph.transitiveClosure` now contains the library entry point and its own
  relative imports
- **regression pin:** a fixture file under a fixture `node_modules` is still **absent** from
  `TypeSpecSearchScopes.filesContainingWord` results — the ADR 0008 exclusion is intact

**Done when:** `./gradlew test` green (≥202 + new) **and** `./gradlew verifyPlugin` still reports
Compatible with exactly two `<depends>` (this is the check on ADR 0010 D3's bundled parser).

**Risks / open questions:**
- The bundled-JSON-parser choice is the only unverified API call in this plan. If `verifyPlugin`
  objects, fall back to asking `tsp-intellij-researcher` for a platform-provided JSON reader that
  needs no `<depends>` — do **not** add `com.intellij.modules.json` without a new ADR.
- `VirtualFile.canonicalFile` returns `null` for a broken symlink; treat as unresolved.

---

### M5.6b — The import string becomes a reference

**Goal:** Cmd-click on `import "…"` navigates, both forms.

**Files:**
- create `src/main/kotlin/simpli/fyi/plugins/typespec/psi/impl/TypeSpecImportStatementMixin.kt`
- create `src/main/kotlin/simpli/fyi/plugins/typespec/resolve/TypeSpecImportReference.kt`
- modify `src/main/grammars/TypeSpec.bnf` — add `mixin=`/`implements=` to `import_statement` only

**Approach:**
- `TypeSpecImportReference : PsiReferenceBase<TypeSpecImportStatement>` with an explicit
  `TextRange` covering the string **contents** (inside the quotes), expressed relative to the
  `import_statement` node — i.e. `string.startOffsetInParent + 1 .. -1`. An explicit range means no
  `ElementManipulator` is required by the constructor.
- `resolve()` delegates to `TypeSpecImportResolver`. `isSoft() = true` (ADR 0010 D5).
- `handleElementRename` / `bindToElement` throw `IncorrectOperationException` for now — file rename
  does not rewrite imports yet, and that is a stated limitation, not a silent one.
- One reference for the whole path, **not** a per-segment `FileReferenceSet`. Per-segment
  directory navigation and path completion are a later, separate want; the reported gap is the
  jump.
- Adding `mixin=` does not rename the generated `Impl` class or the element type, so **no golden
  changes** — see below.

**Acceptance (`TypeSpecImportReferenceTest`):**
- `myFixture.file.findReferenceAt(offset)` inside the quotes of a relative import resolves to the
  target `TypeSpecFile`; offset **on the quote character** and on the `import` keyword resolve to
  nothing
- same for a bare `@scope/pkg` import against the fixture `node_modules`
- unresolved import → `resolve() == null`, `isSoft() == true`, **and** `myFixture.checkHighlighting`
  reports no error on the file (ADR 0010 D5 — a missing package must not paint anything red)
- **regression pin:** `./gradlew test --tests "*TypeSpecParsingTest*"` unchanged, zero golden files
  edited in this milestone's diff

**Done when:** `./gradlew test` green and `git diff --stat src/test/testData/parser/` is empty.

**Risks / open questions:**
- Whether `findReferenceAt` on a *leaf offset* reaches a reference declared on the ancestor
  `import_statement` is the one platform behaviour to pin **first**, before writing the rest. It is
  the same mechanism M5.6d depends on. Write that assertion as the first test; if it fails, move
  the reference host down onto the `STRING` leaf via a `PsiReferenceContributor` and record the
  change in ADR 0009 rather than working around it silently.

---

### M5.6c — Name-based core in the resolver

**Goal:** `TypeSpecResolver` can resolve a dotted name given as `List<String>` + a context element,
not only as `TypeSpecIdentifier` PSI. No behaviour change.

**Files:**
- modify `src/main/kotlin/simpli/fyi/plugins/typespec/resolve/TypeSpecResolver.kt`

**Approach:**
- Extract from `resolveSegment` a private core
  `resolvePath(names: List<String>, index: Int, context: PsiElement): List<Pair<NamespacePath, TypeSpecNamedElement>>`
  carrying today's logic verbatim: index 0 → `resolveLeadingSegment` (tiers A/B then the tier C
  widening), index > 0 → recurse on `index - 1` and `find(name, previousPath)`.
- `resolveSegment(identifier)` becomes a thin adapter: `stripBackticks` each element of
  `qualifiedName.identifierList`, find the index, call the core with the identifier as context.
- Add a public `multiResolve(names: List<String>, index: Int, context: PsiElement)` for M5.6d.
- `TypeSpecScope.chainFor` already takes `PsiElement` — no change there.
- Tier C's "leading segment only" rule and the `ProgressManager.checkCanceled()` calls move with
  the code, unchanged.

**Acceptance:** no new behaviour to assert — the acceptance *is* that the entire existing
navigation suite is untouched and still green. `tsp-tester` adds one direct unit test of the new
public entry point (`multiResolve(listOf("A","B"), 1, contextFile)` on a fixture) to prove the
name-based path reaches the same target as the PSI path on the same source.

**Done when:** `./gradlew test` green with **zero** edits to existing navigation tests.

**Risks / open questions:**
- Backtick handling now lives in the adapter. Decorator names cannot be backticked, so the core
  must take already-stripped names; make that explicit in the KDoc or it will be re-introduced as
  a double-strip bug.

---

### M5.6d — Decorator names resolve, per segment

**Goal:** `@TypeSpec.OpenAPI.info` navigates — the last segment to the `extern dec info`
declaration, earlier segments to their namespaces. `@@augment` behaves identically.

**Files:**
- create `src/main/kotlin/simpli/fyi/plugins/typespec/psi/impl/TypeSpecDecoratorReferenceHost.kt`
  (one shared base; `decorator_application` and `augment_decorator_statement` both use it, with the
  prefix length as the only difference — 1 for `@`, 2 for `@@`)
- create `src/main/kotlin/simpli/fyi/plugins/typespec/resolve/TypeSpecDecoratorReference.kt`
- modify `src/main/grammars/TypeSpec.bnf` — `mixin=`/`implements=` on `decorator_application` and
  `augment_decorator_statement`. **Nothing else in the bnf, and nothing at all in
  `_TypeSpecLexer.flex`.**

**Approach (ADR 0009 option B):**
- `getReferences()` splits the `DECORATOR`/`AUGMENT_DECORATOR` token text on `.` after dropping the
  prefix, and returns one `TypeSpecDecoratorReference` per segment, each with a `rangeInElement`
  relative to the **host node** (`token.startOffsetInParent + offsetWithinToken`, which is 0 for
  both rules since the token is the first child — assert that rather than assume it).
- `TypeSpecDecoratorReference : PsiPolyVariantReferenceBase`, resolving via
  `TypeSpecResolver.multiResolve(names, index, context = host)`, wrapped in `ResolveCache`
  exactly as `TypeSpecReference` does. `isSoft() = true` (bare std-lib decorators such as `@doc`
  do not resolve — ADR 0010 open question 1).
- **`dec_statement` is already reachable — verified, no extra work.** It carries
  `mixin="…TypeSpecNamedElementMixin"` / `implements="…TypeSpecNamedElement"`, and
  `TypeSpecFileDeclarations.build`'s walk indexes *every* `TypeSpecNamedElement` child generically
  (`PsiTreeUtil.getChildrenOfTypeAsList(container, TypeSpecNamedElement::class.java)`), descending
  into `namespace` statements. For `namespace TypeSpec.OpenAPI;` + `extern dec info(...)` the
  statement is a direct child of the namespace node, so it is indexed at path
  `["TypeSpec","OpenAPI"]` under the name `info` — exactly what the third segment asks for.
- **Highlighting is not touched.** No file under `highlighting/` and no `.flex` appears in this
  milestone's diff.

**Acceptance (`TypeSpecDecoratorReferenceTest`):**
- caret on each of the three segments of `@TypeSpec.OpenAPI.info` resolves to, respectively, the
  namespace statement, the namespace statement, and the `extern dec info` — against a fixture
  reproducing `namespace TypeSpec.OpenAPI; extern dec info(...)` reached through a **library
  import** (this is the M5.6a↔M5.6d interlock, and it is the test that proves the owner's exact
  report is fixed)
- caret on the `@`, on a `.`, and inside the argument list yields no decorator reference (argument
  identifiers keep their own references)
- `@@augment` form: same assertions, one extra prefix character
- unqualified `@doc` produces exactly one reference, unresolved, soft, no error highlighting
- **the blast-radius pins, asserted as unchanged:** `./gradlew test --tests "*TypeSpecLexerTest*"`,
  `--tests "*TypeSpecSyntaxHighlighterTest*"`, `--tests "*TypeSpecHighlightingTest*"`,
  `--tests "*TypeSpecParsingTest*"` all green with **zero** edits — 19 golden token lines across 9
  files, 3 lexer assertions, 6 highlighter assertions

**Done when:** `./gradlew test` green **and** `git diff --stat` for this milestone shows no change
under `src/test/testData/parser/`, `src/main/grammars/_TypeSpecLexer.flex`, or
`src/main/kotlin/**/highlighting/`.

**Risks / open questions:**
- Same `findReferenceAt`-on-ancestor question as M5.6b; if M5.6b already pinned it, this is free.
- A decorator on a `model_property` sits inside a deeply nested tree; confirm the host node's
  first child really is the token in **both** rules (`decorator_application ::= DECORATOR
  decorator_argument_list?`, `augment_decorator_statement ::= AUGMENT_DECORATOR '(' …`) — it is,
  per the current bnf, but compute the offset rather than hardcoding 0.

---

### M5.6e — Find Usages sees decorator usages

**Goal:** Find Usages on `extern dec info` lists `@TypeSpec.OpenAPI.info` call sites.

**Files:**
- modify `src/main/kotlin/simpli/fyi/plugins/typespec/findusages/TypeSpecFindUsagesProvider.kt`

**Approach:** the provider's `DefaultWordsScanner` is constructed with
`TokenSet.create(TypeSpecTokenTypes.IDENTIFIER)` as its identifier set and `DECORATOR` /
`AUGMENT_DECORATOR` in none of the three sets — decorator text is therefore **not word-indexed at
all**, so `ReferencesSearch`'s word prefilter never offers those files as candidates and M5.6d's
references are never consulted. Make the scanner emit the individual segments of a decorator token
as words. Keep the "return a fresh instance every call" contract already documented in that file's
KDoc.

**Acceptance:** Find Usages on a `extern dec` declaration finds its `@`-usage in another file;
`filesContainingWord("info", …)` returns the file containing only `@TypeSpec.OpenAPI.info`.
Regression: the existing find-usages and tier C tests stay green (word-index content changed —
confirm no tier C count crosses `TIER_C_FILE_CAP` in the corpus fixtures).

**Done when:** `./gradlew test` green.

**Risks / open questions:**
- This adds entries to the word index, which is the same lever ADR 0008 is starved on. Decorator
  *namespace* segments (`TypeSpec`, `OpenAPI`) are common words. If the tier C cap starts tripping
  in tests, that is evidence for ADR 0008 option C and belongs in that ADR, not worked around here.

---

### M5.6f — (optional, owner's call) Unresolved-import inspection

**Goal:** a **relative** import pointing at a nonexistent file gets a weak warning. Library imports
stay silent (uninstalled `node_modules` is not a source error).

Not scheduled. Listed so the "unresolved is silent" decision (ADR 0010 D5) has a visible successor
rather than being forgotten.

---

## What still will not navigate after this plan

State this to the owner up front; none of it is a defect.

1. **Bare standard-library decorators** — `@doc`, `@key`, `@visibility`. Nothing imports
   `@typespec/compiler`; the compiler loads it implicitly. Fix is ADR 0010 open question 1, an
   owner decision, and it is the single highest-value follow-up here — it covers most decorators
   in most real files.
2. **Built-in types** — `string`, `int32`, `Record<T>`. Same root cause, same fix.
3. **Symbols in libraries a file does not import** — deliberate (ADR 0010 §Consequences).
4. **Renaming a file does not rewrite imports**, and renaming a decorator does not rewrite its call
   sites — no `ElementManipulator` on those hosts yet. M6.5 territory.

## Needs an owner decision

- **Implicit `@typespec/compiler` std-library import** (ADR 0010 open question 1). Recommended, as
  its own milestone after M5.6e.
- **Bundling a JSON parser into the plugin jar** (ADR 0010 D3) — the alternative is a third
  `<depends>`, which `plugin.xml` says needs a new ADR. Architect's recommendation is the bundle.
- **ADR 0008 remains open and untouched.** This plan neither needs nor blocks it.
