# Plan 06 — The stub index (M6.5a–M6.5e), plus the std-library import (M5.6g)

Implements [ADR 0011](../adr/0011-stub-index-replaces-tier-c.md). Closes
[ADR 0008](../adr/0008-tier-c-file-cap.md). Inherits the `node_modules` constraint from
[ADR 0010](../adr/0010-library-import-resolution.md) §Consequences.

Depth and shape follow [plan 02](02-navigation.md): exact paths, exact APIs, what `tsp-tester`
asserts, and a command that must pass.

## Numbering

**M6.5** is already this work's name in [plan 00](00-milestones.md) §M6.5, ADR 0004 D6 and
ADR 0008 option C. Keep it. **M5.6g** is a continuation of [plan 05](05-import-and-decorator-navigation.md)
(import closure work), not part of the index.

## Sequencing

```
M5.6g  implicit @typespec/compiler std-library import   (independent; ship first)
   │
M6.5a  stub infrastructure, zero behaviour change
   ├──► M6.5b  the name index + query API
   │        └──► M6.5c  resolver: index replaces tier C  (absorbs plan 05 M5.6c)
   │                        └──► M6.5d  dumb mode, caching and perf guards
   │                                        └──► M6.5e  Go To Symbol   (optional, owner's call)
   └──► (later) plan 05 M5.6d decorator references — now cheaper, see §Interaction below
```

### Why M5.6g first

`@doc`, `string`, `int32` never resolve today because nothing imports `@typespec/compiler`; the
compiler loads it implicitly. That is the largest number of unresolved symbols per real file of
any open item, it is one dev run, and it touches only `TypeSpecImportGraph` — **not** tier C, not
the index. Doing it first means the owner sees a win while the index is being built. It is also
the case that proves ADR 0011 D2/D4's split: the std library lives *inside* `node_modules`, is
reached along an import edge, and must never be in the index.

### Interaction with the unimplemented plan-05 milestones

- **M5.6c (name-based resolver core) is absorbed into M6.5c.** M6.5c rewrites
  `resolveLeadingSegment` anyway; extracting `resolvePath(names, index, context)` in the same edit
  is free, and doing it twice on the same 30 lines is not. Plan 05 M5.6c is hereby marked
  *superseded by plan 06 M6.5c* — `tsp-dev` must not implement it separately.
- **M5.6d (decorator references) stays in plan 05 and comes after M6.5c.** It consumes the
  name-based entry point M6.5c produces, and it benefits directly from the index: a decorator name
  like `@doc` is exactly a common-name lookup that the cap used to fail.

---

## Architecture in one screen

```
reference resolve (leading segment)
 ├─ tier A  same file          PSI, cached, dumb-safe        unchanged
 ├─ tier B  import closure     PSI, cached, dumb-safe        unchanged  (reaches node_modules)
 └─ tier C' STUB INDEX         no parse, no cap, smart-mode  NEW       (never node_modules)
```

What is stored, per stubbed declaration: **name** (backtick-stripped) and **enclosing namespace
path** (dotted string, pre-computed at build time from the parent stub chain). Nothing else. The
declaration *kind* is the element type — never a field.

What is indexed: `name → declaration`. One index. The namespace question is answered by comparing
the stub's stored path, in memory, with no AST load (ADR 0011 D3).

### What gets stubbed — exactly 10 rules

`TypeSpecNamedElement` is currently carried by **14** rules. Only the 10 that can be a *direct
child of a file or a `namespace` statement* get stubs — precisely the set
`TypeSpecFileDeclarations.build()` already walks:

| Stubbed (10) | Not stubbed (4) |
|---|---|
| `namespace_statement`, `model_statement`, `op_statement`, `interface_statement`, `enum_statement`, `union_statement`, `alias_statement`, `scalar_statement`, `dec_statement`, `fn_statement` | `model_property`, `enum_member`, `union_variant`, `interface_operation`, `template_parameter` |

The members are excluded because member resolution (`Foo.bar`) is still out of scope
(ADR 0004 D6), and stubbing them would multiply index size by the average member count per
declaration for zero current benefit. Adding them later is a stub-version bump (ADR 0011 D6, item 2).

`namespace_statement` is the one irregular case: `namespace A.B.C;` declares three namespaces with
one PSI node. Its stub therefore stores its **own dotted segments** as a list, and
`TypeSpecFileDeclarations`' existing "index every segment under its own prefix" rule is reproduced
against stubs.

---

## M5.6g — Implicit `@typespec/compiler` std-library import

**Goal:** `@doc`, `string`, `int32`, `Record<T>` resolve in a file that imports nothing.

**Files:**
- modify `src/main/kotlin/simpli/fyi/plugins/typespec/resolve/TypeSpecImportGraph.kt`
- modify `src/main/kotlin/simpli/fyi/plugins/typespec/resolve/TypeSpecImportResolver.kt` (only if
  an entry-point helper needs exposing; prefer no change)

**Approach:**
- In `TypeSpecImportGraph.compute`, seed the BFS queue with the resolution of the bare specifier
  `"@typespec/compiler"` from the *starting* file, in addition to `file` itself — i.e. every file's
  closure implicitly contains the compiler library's entry point and its relative closure. Use the
  existing `TypeSpecImportResolver.resolve(file, "@typespec/compiler")`; add nothing new.
- Absent or unresolvable (`node_modules` not installed) → seed nothing, silently. No error, no
  notification (ADR 0010 D5).
- `CLOSURE_CAP` (200) unchanged: measured std-library closure is 5 files (ADR 0010).
- Keep the existing `PsiModificationTracker.MODIFICATION_COUNT` cache dependency — the implicit
  edge changes only when the project model does.

**Acceptance (`tsp-tester`, `TypeSpecResolveTest` + a new `TypeSpecStdLibraryTest`):**
1. Fixture with a fake `node_modules/@typespec/compiler/package.json` + `lib/main.tsp` declaring
   `namespace TypeSpec; extern dec doc(...); scalar string;` — a source file that imports nothing
   resolves `string` and (after plan 05 M5.6d) `@doc`.
2. Same fixture **without** `node_modules` → resolves to nothing, no exception, test asserts the
   absence of a thrown exception explicitly.
3. Regression: the whole existing navigation suite green, unedited.

**Done when:** `./gradlew test` green, test count up by ≥3, no existing test edited.

**Risks / open questions:**
- Closure growth: +5 files in *every* file's closure, so tiers A/B get slightly more expensive
  everywhere. Bounded and cached; if it shows up, the fix is to cache the std-library closure once
  per project rather than per file — note it, do not pre-build it.
- Monorepos with several `@typespec/compiler` copies resolve per-file, which is correct.

---

## M6.5a — Stub infrastructure, zero behaviour change

**Goal:** `.tsp` files get a stub tree. Nothing resolves differently. No index yet.

**Files to create:**

| Path | What |
|---|---|
| `src/main/java/simpli/fyi/plugins/typespec/stubs/TypeSpecStubTypes.java` | **Java interface** (see below) holding the 10 `IStubElementType` constants + `static IElementType factory(String)` |
| `src/main/kotlin/simpli/fyi/plugins/typespec/stubs/TypeSpecStubVersion.kt` | `const val VERSION = 1`, plus the ADR 0011 D6 bump checklist verbatim in KDoc |
| `src/main/kotlin/simpli/fyi/plugins/typespec/stubs/TypeSpecDeclStub.kt` | the stub interface + `TypeSpecDeclStubImpl : NamedStubBase<TypeSpecNamedElement>` |
| `src/main/kotlin/simpli/fyi/plugins/typespec/stubs/TypeSpecDeclStubElementType.kt` | one `IStubElementType<TypeSpecDeclStub, TypeSpecNamedElement>` subclass, instantiated 10 times |
| `src/main/kotlin/simpli/fyi/plugins/typespec/stubs/TypeSpecFileStub.kt` | `PsiFileStubImpl<TypeSpecFile>` subclass |
| `src/main/kotlin/simpli/fyi/plugins/typespec/stubs/TypeSpecFileElementType.kt` | `IStubFileElementType<TypeSpecFileStub>` — `getStubVersion`, `getExternalId`, `getBuilder`, `shouldBuildStubFor` |
| `src/main/kotlin/simpli/fyi/plugins/typespec/stubs/TypeSpecStubBuilder.kt` | `DefaultStubBuilder` subclass |
| `src/main/kotlin/simpli/fyi/plugins/typespec/stubs/TypeSpecNodeModules.kt` | the single `node_modules` predicate, shared with `TypeSpecSearchScopes` |

**Files to modify:** `src/main/grammars/TypeSpec.bnf`,
`src/main/kotlin/simpli/fyi/plugins/typespec/psi/impl/TypeSpecNamedElementMixin.kt`,
`src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecElementTypes.kt`,
`src/main/resources/META-INF/plugin.xml`.

**Approach — the parts that are not guessable, all verified against `ideaIC-2025.2.6.3`:**

1. **`TypeSpec.bnf`.** Two edits, no rule bodies touched:
   - global header gains
     `elementTypeFactory("(namespace|model|op|interface|enum|union|alias|scalar|dec|fn)_statement")="simpli.fyi.plugins.typespec.stubs.TypeSpecStubTypes.factory"`
   - each of the 10 rules gains `stubClass="simpli.fyi.plugins.typespec.stubs.TypeSpecDeclStub"`
     alongside its existing `mixin=`/`implements=`. `mixin=`/`implements=` stay exactly as they
     are; `methods=[...]`/`psiImplUtilClass` remain banned (ADR 0006 D7/F8).

   Verified by running the generator standalone (grammar-kit 2023.3.4) against this repo's `.bnf`:
   the interface becomes
   `interface TypeSpecModelStatement extends TypeSpecNamedElement, StubBasedPsiElement<TypeSpecDeclStub>`,
   the impl gains a second constructor
   `TypeSpecModelStatementImpl(TypeSpecDeclStub stub, IStubElementType stubType) { super(stub, stubType); }`,
   and `TypeSpecTypes.MODEL_STATEMENT` becomes `TypeSpecStubTypes.factory("MODEL_STATEMENT")`.
   Non-matching rules keep `new TypeSpecElementType(...)`. **Nothing else in the generated output
   changes.**

2. **The factory must return the holder's singletons.** `TypeSpecStubTypes.factory(name)` looks the
   constant up by name and throws on an unknown name — it must never `new` an instance, or
   `TypeSpecTypes.MODEL_STATEMENT` and `TypeSpecStubTypes.MODEL_STATEMENT` become different objects
   and every `==` comparison in the parser silently stops matching.

3. **The holder is a Java *interface*, and holds nothing but stub element types.**
   `StubElementTypeHolderEP.initializeOptimized` (bytecode-verified) asserts
   `clazz.isInterface()` and wraps **every declared non-synthetic field** in a `StubFieldAccessor`
   that casts the value to `ObjectStubSerializer`/`IElementType` and demands a registered
   serializer. So: a Kotlin `object` is not usable (assertion fires with `-ea`, which tests run
   with), and pointing the EP at the generated `TypeSpecTypes` is not usable either (it holds ~60
   non-stub element types). Hence a hand-written Java interface under `src/main/java`. External IDs
   are `externalIdPrefix + fieldName`, e.g. `tsp.MODEL_STATEMENT` — changing either is an ADR 0011
   D6 item 3 bump.

4. **`TypeSpecNamedElementMixin` gains a second constructor and — critically — a `toString`.**
   Grammar-Kit's generated impls call `super(stub, stubType)`, so the mixin must extend
   `StubBasedPsiElementBase<TypeSpecDeclStub>` and expose
   `(stub: TypeSpecDeclStub, stubType: IStubElementType<*, *>)` next to `(node: ASTNode)`.
   `StubBasedPsiElementBase` (and `ASTDelegatePsiElement` above it) **has no `toString()`** —
   verified with `javap`; only `ASTWrapperPsiElement` does. Losing it makes every PSI dump print
   `simpli.fyi…Impl@1f2e3d`, which is both wrong and non-deterministic. Reproduce
   `ASTWrapperPsiElement`'s exact format (bytecode-verified):

   ```kotlin
   override fun toString(): String = "${javaClass.simpleName}($elementTypeImpl)"
   ```

   using the protected `getElementTypeImpl()` (works with or without AST), not `getElementType()`.
   Everything else on the mixin (`getNameIdentifier`, `getName`, `getTextOffset`, `setName`,
   `getPresentation`) is unchanged; `getName()` should prefer the stub when one is present
   (`greenStub?.name ?: …`) so name access never forces an AST load.

5. **`TypeSpecElementTypes.FILE`** becomes `TypeSpecFileElementType` (an `IStubFileElementType`).
   Same field, same name, same instance identity contract — `TypeSpecParserDefinition.getFileNodeType()`
   is untouched. `getExternalId() = "typespec.FILE"`. `getStubVersion() = TypeSpecStubVersion.VERSION`.

6. **Builder.** `TypeSpecStubBuilder : DefaultStubBuilder`, overriding `createStubForFile` to return
   `TypeSpecFileStub(file)` and `skipChildProcessingWhenBuildingStubs` to refuse to descend into
   `MODEL_BODY` / `ENUM_BODY` / `UNION_BODY` / `INTERFACE_BODY` (no stubbed element lives there).
   `TypeSpecDeclStubElementType.createStub(psi, parent)` computes:
   - `name` = `TypeSpecPsiUtil.stripBackticks(psi.name)`;
   - `ownSegments` = for a namespace, its dotted segments; empty otherwise;
   - `namespacePath` = the parent stub chain's concatenated `ownSegments`, joined with `.` —
     computed from **stubs**, never from PSI ancestors.
   - `shouldCreateStub(node)` returns `false` when the declaration has no name (broken source), so
     unnamed error-recovery nodes never enter the index.

7. **`plugin.xml`** gains exactly one line in M6.5a:
   `<stubElementTypeHolder class="simpli.fyi.plugins.typespec.stubs.TypeSpecStubTypes" externalIdPrefix="tsp."/>`.
   No new `<depends>` — the EP is declared in the core `com.intellij` descriptor
   (`META-INF/IdeaPlugin.xml:3168`, `META-INF/Core.xml:46`), same provenance as
   `lang.parserDefinition` (ADR 0004 F1).

**Acceptance (`tsp-tester`):**
- New `src/test/kotlin/.../stubs/TypeSpecStubTreeTest.kt`:
  1. `DebugUtil.stubTreeToString` of a fixture with nested + dotted namespaces, one of each of the
     10 kinds, matches a new golden `src/test/testData/stubs/Declarations.txt`.
  2. Every stub's `name` is backtick-stripped (fixture uses `` `model` `` as a name).
  3. `namespace A.B.C; model M {}` → `M`'s stub `namespacePath == "A.B.C"`.
  4. A stubbed PSI element's `getName()` does not load AST: assert
     `(psi as PsiFileImpl).stub != null` still holds — i.e. `file.node` was not forced.
- **Golden blast radius, quantified.** `grep`-measured on the current tree: **43 of 46** parser
  goldens contain at least one of the 10 impl class names; **70 lines of 2045** would change.
  Expected churn: **zero** — element-type debug names, impl class names and the `toString` format
  are all preserved by design (§4 above). *If `tsp-dev` reports golden churn, the `toString`
  override is missing or wrong — do not re-record the goldens.* The corpus suite (83 `.tsp` files)
  is property-based with no goldens and is unaffected.
- Full existing suite (208) green and unedited.

**Done when:** `./gradlew test verifyPlugin` green; `git diff --stat src/test/testData/parser/` is
empty; `verifyPlugin` still reports Compatible with two `<depends>`.

**Risks / open questions:**
- `StubElementTypeHolderEP` may log "was instantiated too late" if some other code touches
  `TypeSpecTypes` before the EP runs. Symptom is a log error, not a failure; fix is ordering, not
  design. Watch the `runIde` log once.
- `DefaultStubBuilder` parses PSI at index time. `LightStubBuilder` is possible later — the
  generated parser already implements `LightPsiParser` (verified) — but it needs
  `ILightStubElementType` everywhere and is not worth it before measurement.

---

## M6.5b — The name index

**Goal:** `StubIndex` answers "which declarations are named X" for project `.tsp` sources.

**Files:**
- create `src/main/kotlin/simpli/fyi/plugins/typespec/stubs/TypeSpecDeclarationNameIndex.kt`
- create `src/main/kotlin/simpli/fyi/plugins/typespec/stubs/TypeSpecStubQueries.kt`
- modify `TypeSpecDeclStubElementType.indexStub`, `plugin.xml`, `TypeSpecStubVersion` (to 2 if
  M6.5a shipped separately to a user build; otherwise stay at 1 — see D6)

**Approach:**
- `class TypeSpecDeclarationNameIndex : StringStubIndexExtension<TypeSpecNamedElement>()` with
  `KEY = StubIndexKey.createIndexKey<String, TypeSpecNamedElement>("tsp.decl.name")`,
  `getVersion() = TypeSpecStubVersion.VERSION`.
- `indexStub(stub, sink)`: `sink.occurrence(KEY, stub.name)`. For a namespace stub, one occurrence
  **per dotted segment** (`namespace A.B.C;` → `A`, `B`, `C`), mirroring
  `TypeSpecFileDeclarations`' rule so a stub lookup and a PSI lookup can never disagree.
- `TypeSpecStubQueries.declarationsNamed(project, name, path: NamespacePath?): List<TypeSpecNamedElement>`
  — `StubIndex.getElements(KEY, name, project, TypeSpecSearchScopes.tspScope(project), TypeSpecNamedElement::class.java)`
  filtered by the stub's stored `namespacePath` when `path` is non-null. Guarded by
  `DumbService.isDumb(project)` → empty list (never an `IndexNotReadyException` to the caller).
- `plugin.xml`: `<stubIndex implementation="…TypeSpecDeclarationNameIndex"/>`. EP is core
  (`IdeaPlugin.xml:3460`) — still no third `<depends>`.
- `tspScope` stays exactly as it is and is now the index query scope; `shouldBuildStubFor` uses the
  *same* `TypeSpecNodeModules.isUnder(vf)` predicate, so build-time and query-time filters cannot
  drift.

**Acceptance (`tsp-tester`, new `TypeSpecStubIndexTest`):**
1. Two files declaring `model Response` in different namespaces → lookup by name returns 2;
   lookup by name + `NamespacePath(["Shared"])` returns 1.
2. `namespace A.B.C;` is findable by each of `A`, `B`, `C`.
3. **`node_modules` exclusion:** a declaration in `node_modules/@x/y/lib/main.tsp` is **not**
   returned by any lookup, and `file.stub == null`/no stub tree was built for it. This is the ADR
   0010 forward constraint; it must fail loudly if someone widens the scope.
4. **Scale, replacing the cap test:** generate 120 files each containing the word `Shared` in a
   comment and one file that actually declares `namespace Shared;` → the lookup returns exactly the
   one declaration. (The old `TypeSpecSearchScopesTest` cap assertions are deleted in M6.5c, not
   here.)
5. Dumb mode: inside `DumbServiceImpl.getInstance(project).runInDumbMode { }`, the query returns
   empty and does not throw.

**Done when:** `./gradlew test` green with the 5 new assertions; test count ≥ 213.

**Risks:**
- Light-fixture stub indexing: `myFixture.addFileToProject` files must be indexed before assertion.
  If a lookup is flaky, the cause is fixture setup, not the index — do not "fix" it with a sleep.

---

## M6.5c — The resolver: the index replaces tier C, the cap is deleted

**Goal:** the ADR 0011 §Context case 3 (`using Shared;` across modules) resolves, on any project
size. Absorbs plan 05 M5.6c.

**Files:**
- modify `src/main/kotlin/simpli/fyi/plugins/typespec/resolve/TypeSpecResolver.kt`
- modify `src/main/kotlin/simpli/fyi/plugins/typespec/resolve/TypeSpecSearchScopes.kt` (delete
  `filesContainingWord` and `TIER_C_FILE_CAP`; keep `tspScope` and `NotUnderNodeModulesScope`,
  which now delegate the path predicate to `TypeSpecNodeModules`)
- delete the cap/dumb-mode cases from `src/test/kotlin/.../resolve/TypeSpecSearchScopesTest.kt`
  (`tsp-tester` owns this edit)

**Approach:**
- Extract `resolvePath(names: List<String>, index: Int, context: PsiElement)` exactly as plan 05
  M5.6c specified (already-stripped names; `resolveSegment` becomes a thin adapter; public
  `multiResolve(names, index, context)` added for plan 05 M5.6d).
- `resolveLeadingSegment` becomes:
  1. A/B as today — `resolveLeadingSegmentIn(candidateFiles)`; return on first hit.
  2. Otherwise, for each scope in `TypeSpecScope.chainFor(context)` (longest prefix first) and then
     for each `using` target visible at that scope, call
     `TypeSpecStubQueries.declarationsNamed(project, name, path)`; first non-empty wins, results
     `distinctBy { it.second }`. No file set, no cap, no `CacheManager`.
  3. Nothing found → empty, exactly as today (soft reference, ADR 0010 D5).
- Non-leading segments are unchanged in shape but gain the same index fallback: after
  `candidateFiles.find(name, previousPath)` comes `declarationsNamed(project, name, previousPath)`.
  This is what makes `Shared.VolumeUnit` work as well as `using Shared; VolumeUnit`.
- `using` target resolution (`TypeSpecScope.resolveUsingTarget`) is unchanged, memoisation and
  re-entrancy guard included — it goes through the same `multiResolve`, so it inherits the index
  for free. **Do not remove the guard or the memoisation**; they fixed a real hang and the index
  makes each individual resolve cheaper, not idempotent.
- `ProgressManager.checkCanceled()` stays at the top of every loop.

**Cost after this milestone, stated so it can be checked:** one leading-segment resolve = tier A/B
walk over the (cached) closure + at most (scope-chain length × (1 + using count)) stub-index
lookups, each O(hits) string compares over stub fields. **Zero** candidate-file parses. AST is
loaded only for the file the user actually jumps to.

**Acceptance (`tsp-tester`, `TypeSpecResolveTest` + `TypeSpecCrossModuleResolveTest`):**
1. **The owner's case, verbatim:** module `shared` with `namespace Shared; scalar VolumeUnit extends string; model MetaData {}`;
   module `app` with `using Shared;` referencing `VolumeUnit` and `...MetaData;`. Both resolve, in a
   fixture with **> 120** `.tsp` files that mention `Shared` textually. This is the test that would
   have failed before this plan and is the whole point of it.
2. `Shared.VolumeUnit` (qualified, no `using`) resolves.
3. Reopened/merged namespaces across 3 files resolve to all declarations (poly-variant count).
4. Dumb mode: same-file and imported-file references still resolve; project-wide ones return
   nothing and do not throw. (New: this is better than today, assert it explicitly.)
5. Library asymmetry pinned (ADR 0010): a declaration in an *imported* library file still resolves
   (tier B), and one in a *non-imported* library file still does not.
6. `TypeSpecSearchScopesTest`: cap and dumb-mode-null cases deleted; `tspScope`'s `node_modules`
   exclusion cases kept and extended to assert `TypeSpecNodeModules` is the single predicate.
7. Grep gate: `grep -r TIER_C_FILE_CAP src/` returns nothing.

**Done when:** `./gradlew test verifyPlugin` green; the new cross-module test passes; no
`TIER_C_FILE_CAP` anywhere; test count ≥ 218.

**Risks / open questions:**
- **Ordering regressions.** Removing the cap means more candidates reach the poly-variant result on
  common names. If a previously-single-target Cmd-click starts showing a chooser, that is *correct*
  (merged namespaces are genuinely ambiguous) but it is a visible behaviour change — call it out to
  the owner in the milestone report.
- Resolution now differs between dumb and smart mode. That is deliberate and is asserted, but it
  means a bug report must always say whether indexing was running.

---

## M6.5d — Dumb mode, caching and perf guards

**Goal:** no EDT hazard, no unbounded work, cache dependencies stated once.

**Files:** `TypeSpecStubQueries.kt`, `TypeSpecResolver.kt`, `TypeSpecScope.kt` (KDoc only where
behaviour is unchanged).

**Approach:**
- Reference results are already cached per-resolve by the platform's `ResolveCache`; the new index
  hits need a cache dependency that is *not* `PsiModificationTracker.MODIFICATION_COUNT` for
  per-file tables (unchanged from ADR 0004 D2) but **must** invalidate on index change where a
  result is cached across files. Use `StubIndex.getInstance().getStubIndexModificationTracker(project)`
  (verified present) as the dependency for anything that memoises an index answer. If nothing
  memoises index answers — the preferred outcome — write that down and add no cache.
- No `runReadActionInSmartMode` anywhere on a resolve path (it blocks the EDT).
- Keep `ProgressManager.checkCanceled()` in every loop that scales with hits.

**Acceptance:** `TypeSpecCachingTest` gains: editing file B invalidates a resolve in file A that
targets B (via the index modification tracker); editing an unrelated file C does not force a
re-resolve. Plus a smoke test that a resolve inside a 200-file fixture completes under a generous
wall-clock bound (assert a bound, not a benchmark).

**Done when:** `./gradlew test` green; no new flakiness across 3 consecutive runs.

---

## M6.5e — Go To Symbol (optional; owner's call)

**Goal:** Cmd-Alt-O lists TypeSpec declarations.

**Files:** create `src/main/kotlin/simpli/fyi/plugins/typespec/navigation/TypeSpecGotoSymbolContributor.kt`;
modify `plugin.xml`.

**Approach:** `ChooseByNameContributorEx` over `TypeSpecDeclarationNameIndex.KEY`
(`processNames` → `StubIndex.processAllKeys`, `processElementsWithName` →
`StubIndex.processElements`), registered as `<gotoSymbolContributor>` (core EP,
`IdeaPlugin.xml:4251`, no new `<depends>`). Presentation comes from the existing
`TypeSpecNamedElementMixin.getPresentation()`.

**Acceptance:** a fixture with 4 declarations across 2 files; assert the contributor yields each by
name and by prefix, and that a `node_modules` declaration is **not** offered.

**Done when:** `./gradlew test verifyPlugin` green.

---

## Deferred, with the trigger that would revive it

| Item | Revive when |
|---|---|
| Namespace-qualified (FQN) index | a name is measured with > ~100 declarations in a real project, i.e. the in-memory path filter stops being cheap |
| Stubbing the 4 member kinds | member resolution (`Foo.bar`) is scheduled — ADR 0004 D6 |
| `using`/`import` stored in the file stub (tier B without parsing) | tier B is measured as the dominant resolve cost after M6.5c |
| `LightStubBuilder` | index-time parse cost is measured as a problem |
| New `StubRegistryExtension` API | Grammar-Kit emits `StubElementFactory`-shaped PSI, or `IStubFileElementType` becomes Deprecated (ADR 0011 D5) |

## Needs an owner decision

1. **Is M5.6g (std library) really first?** It is the architect's recommendation (largest visible
   win per dev run) but it delays the cross-module fix by one milestone.
2. **Does M6.5e (Go To Symbol) ship here or wait for M6?**
3. **Behaviour change to expect:** removing the cap can turn a silent no-op into a multi-target
   chooser on common names. That is correct behaviour for merged namespaces, but it is *visible*.
4. Still open from earlier plans and untouched here: plugin id/vendor/marketplace publishing,
   `tspconfig.yaml` `entrypoint` support (ADR 0010 open question 2).
