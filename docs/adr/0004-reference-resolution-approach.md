# ADR 0004 — How TypeSpec references resolve (and what "jump navigation" costs)

- Status: **Accepted, ratified 2026-09-02 after M4b.** (D6 and the open questions remain
  pending owner ratification.) Reviewed against the shipped M4b tree; see "Post-M4b review"
  below — **no decision changed**.
- Date: 2026-09-02 (reviewed and ratified same day, post-`ce92694`)
- Deciders: `tsp-architect` (proposed and ratified), project owner (open questions only)
- Relates to: [ADR 0001](0001-highlighting-approach.md) (staged: lexer first, parser later),
  [ADR 0003](0003-parser-definition-timing.md) (`ParserDefinition` timing),
  [ADR 0005](0005-minimal-parser-definition-for-commenting.md) (the flat `ParserDefinition`
  that actually landed, in M4b), [ADR 0006](0006-grammar-toolchain.md) (the grammar toolchain
  that will build the PSI this ADR depends on),
  [plan 00](../plans/00-milestones.md), [plan 02](../plans/02-navigation.md),
  [plan 03](../plans/03-grammar-and-psi.md)

## Post-M4b review (2026-09-02)

This ADR was drafted before M4b landed and sat untracked. It has now been re-read against
the shipped tree at `ce92694`. Recording the review so its currency is not in doubt:

1. **The header line "`ParserDefinition` lands in M5" was stale and is corrected above.**
   [ADR 0005](0005-minimal-parser-definition-for-commenting.md) moved a *minimal flat*
   `ParserDefinition` into M4b, because `lang.commenter` resolves by file language and was
   silently dead without one.
2. **That does not weaken F5, and does not obsolete any decision here.** M4b's
   `TypeSpecFlatParser` emits every lexer token as a flat leaf under one root and its
   `createElement` deliberately throws — there are **no composite element types at all**.
   So there is still no node for `Foo.Bar`, no per-segment node, and no declaration node that
   could implement `PsiNameIdentifierOwner`. F5's diagnosis ("M5 must be amended before it
   ships") is unchanged; only its *addressee* narrows, from "M5" to
   [plan 03](../plans/03-grammar-and-psi.md) §M5b.
3. **D7's four amendments are now binding requirements of M5b**, written out in
   [plan 03](../plans/03-grammar-and-psi.md) §M5b steps 2–4 with their own acceptance tests
   (`TypeSpecPsiContractTest`). They are no longer a note on someone else's milestone.
4. **D5's numbering survives.** M5 is now split M5a/M5b/M5c (plan 03); M5.5 still follows M5c
   and still precedes M6. Decimal numbering keeps ADR 0002/0003's M4/M5/M6/M7 references
   truthful — that argument holds unchanged.
5. **D1 is independently corroborated.** A 2026-09-02 platform-API research pass confirmed
   that `psi.referenceContributor` exists to attach references to PSI you do **not** own, that
   `gotoDeclarationHandler` is **not** needed when `PsiReference` is implemented, and that the
   `com.intellij.model` Symbol API is still `@ApiStatus.Experimental`. All three match D1.
   It also confirmed every EP named in F1 is CE-available under
   `<depends>com.intellij.modules.platform</depends>` (they are `xi:include`d into
   `PlatformLangPlugin.xml`, `<id>com.intellij`).
6. **One refinement to D2's implementation, not its decision:** the same pass found
   `FindUsagesProvider.getWordsScanner()` defaults to `null` (meaning `SimpleWordsScanner`),
   so `DefaultWordsScanner` is **recommended, not mandatory** — and if supplied it "MUST be
   thread-safe, otherwise you should return a new instance". D6 still ships it; plan 02 should
   note the thread-safety requirement.
7. **Prerequisite restated:** this ADR's milestone (M5.5) cannot start until **M5c** is
   green, not M5b — six of its eight target declaration kinds only exist after M5c.

## Context

The owner asked for **jump navigation**: Ctrl/Cmd-click and *Go To Declaration* on a type
reference landing on its `model` / `enum` / `union` / `interface` / `alias` / `scalar`
declaration.

The roadmap did not cover it. Plan 00 §M5 listed *"Reference resolution,
`PsiNamedElement`/rename, indexing/stubs"* under **Out of scope**, described as "a follow-on
milestone if wanted". M6 covers structure view / folding / completion / annotator /
breadcrumbs — none of which delivers go-to-declaration.

This ADR settles four things the plan cannot decide implicitly: which reference API to use,
how resolution finds declarations without a stub index, where the milestone sits in the
dependency order, and whether any of it breaks the Community-Edition constraint.

## Findings

All findings verified directly against the resolved compile-classpath distribution
**ideaIC-2025.2.6.3-aarch64** at
`~/.gradle/caches/9.5.0/transforms/b7737863f4f6e0fb10700e6d53a086b4/transformed/`.
Where a claim could not be verified it is marked ⚠ and appears in the open questions.

### F1 — Every extension point navigation needs is in the core `com.intellij` descriptor

`lib/app.jar!/META-INF/IdeaPlugin.xml` declares, in the same top-level `<extensionPoints>`
blocks that already give us `lang.parserDefinition` (`:3162`) and
`lang.syntaxHighlighterFactory` (`:3574`):

| EP | Line in `IdeaPlugin.xml` | Bean / interface |
|---|---|---|
| `lang.elementManipulator` | 3165 | `com.intellij.openapi.util.ClassExtensionPoint` (`forClass` + `implementationClass`) |
| `psi.referenceContributor` | 3207 | `PsiReferenceContributorEP` |
| `psi.symbolReferenceProvider` | 3210 | `PsiSymbolReferenceProviderBean` |
| `gotoDeclarationHandler` | 3258 | `GotoDeclarationHandler` |
| `referencesSearch` | 3462 | `QueryExecutor` |
| `lang.refactoringSupport` | 3935 | `LanguageExtensionPoint` |
| `lang.namesValidator` | 3938 | `LanguageExtensionPoint` |
| `lang.findUsagesProvider` | 4207 | `LanguageExtensionPoint` |
| `gotoSymbolContributor` | 4251 | `ChooseByNameContributor` |
| `symbolNavigation` | 4306 | `ClassExtensionPoint` |
| `stubIndex`, `stubElementTypeHolder` | (same file) | — |

The implementation classes live in core platform jars, not in any bundled plugin:

```
lib/util-8.jar     com/intellij/psi/PsiReferenceBase.class
lib/util-8.jar     com/intellij/psi/PsiPolyVariantReferenceBase.class
lib/util-8.jar     com/intellij/psi/PsiNameIdentifierOwner.class
lib/util-8.jar     com/intellij/psi/AbstractElementManipulator.class
lib/util-8.jar     com/intellij/psi/scope/PsiScopeProcessor.class
lib/app-client.jar com/intellij/psi/impl/source/resolve/ResolveCache.class
lib/app-client.jar com/intellij/lang/findUsages/FindUsagesProvider.class
lib/app-client.jar com/intellij/lang/cacheBuilder/DefaultWordsScanner.class
lib/app-client.jar com/intellij/codeInsight/TargetElementUtil.class
lib/app-client.jar com/intellij/psi/impl/cache/CacheManager.class
lib/app-client.jar com/intellij/psi/search/FileTypeIndex.class
```

**Consequence: no second `<depends>` is required.** This is *not* the ADR 0003 D3 situation
(spellchecking, which needs `com.intellij.modules.spellchecker`). Nothing in this milestone
lives in a separate content module.

### F2 — The word index is already populated for `.tsp` files, for free

`IdTableBuilding.getFileTypeIndexer(FileType)` (javap of
`lib/app-client.jar!/com/intellij/psi/impl/cache/impl/id/IdTableBuilding.class`):

```
CacheBuilderRegistry.getCacheBuilder(fileType)                    // null for us
  → if fileType instanceof LanguageFileType:
        LanguageFindUsages.getWordsScanner(language)              // null for us today
  → fallback: new SimpleWordsScanner()
  → createDefaultIndexer(scanner)
```

`SimpleWordsScanner.processWords` splits on `Character.isJavaIdentifierStart` /
`isJavaIdentifierPart` and constructs each `WordOccurrence` with a **null `Kind`**
(`aconst_null` at offset 18 of `processWords`), which the default indexer maps to
`UsageSearchContext.ANY`.

**Consequence:** `CacheManager.getInstance(project).getVirtualFilesWithWord(name,
UsageSearchContext.ANY, scope, true)` already returns exactly the `.tsp` files whose text
contains that identifier — a real, index-backed prefilter, available today with **zero**
custom index registration. This is what makes cross-file resolution affordable without
stubs, and it is the single most load-bearing finding in this ADR.

Caveat: once M5.5 registers a `FindUsagesProvider` whose `getWordsScanner()` returns a
`DefaultWordsScanner`, occurrences become properly categorised (`IN_CODE` /
`IN_COMMENTS` / `IN_STRINGS`). Searching with `UsageSearchContext.ANY` is correct **both
before and after** that change; searching with `IN_CODE` would silently return nothing
until the provider ships. Use `ANY`.

### F3 — `PsiReference` gives five features; `GotoDeclarationHandler` gives one

`TargetElementUtil` (the Ctrl-click / *Go To Declaration* entry point) consults, in order,
`GotoDeclarationHandler` extensions and then `PsiFile.findReferenceAt(offset).resolve()`.
Implementing `PsiReference` therefore satisfies Ctrl-click **and simultaneously** feeds:

| Consumer | What it uses |
|---|---|
| Ctrl-click / Go To Declaration | `PsiReference.resolve()` |
| Find Usages | `ReferencesSearch` → word index → `PsiReference.isReferenceTo()` |
| Rename | `PsiReference.handleElementRename()` via `ElementManipulators` |
| Completion | `PsiReference.getVariants()` |
| Quick Documentation / Ctrl-hover | the resolved `PsiElement` |

A `GotoDeclarationHandler` satisfies only the first row and would have to be duplicated for
each of the others. It is the right tool only for navigation that is *not* a reference
(e.g. jumping from a string literal to a file), which is not our case.

### F4 — TypeSpec's name resolution is namespace-relative and cross-file

From the TypeSpec docs (`language-basics/namespaces`, `language-basics/imports`):

- A namespace may be **blockless** (`namespace Foo;`, at most one per file, after imports) or
  **block** (`namespace Foo { … }`), and may be **dotted** (`namespace Foo.Bar.Baz`), which
  is sugar for nesting.
- The **same namespace declared in several files is merged** into one. A declaration you
  reference is frequently in a file you do not `import` — the compiler builds one program
  from an entry point (`main.tsp`) and every file in that compilation shares the namespace
  tree.
- `using Foo.Bar;` binds that namespace's members **locally to the namespace in which the
  `using` appears**; the bindings do not become members of that namespace.
- Names before a blockless `namespace` declaration resolve globally; names after it resolve
  **relative to the file namespace**, walking outward.

**Consequence:** a resolver that only walks the current file plus its transitive `import`
closure is *correct but frequently useless* — it misses the merged-namespace case, which is
the norm in real TypeSpec projects. Some project-wide widening is unavoidable.

### F5 — What M5's PSI must expose, and what it currently promises

Plan 00 §M5's grammar list covers the declaration statements and "type expressions (union
`|`, intersection `&`, array `[]`, template args `<>`, optional `?`)" but **names no rule for
a type reference itself**, and says nothing about `PsiNamedElement`. As written, M5 would
ship a tree in which:

- there is no single PSI node representing `Foo.Bar` to hang a reference on, and no per-
  segment node to hang a *per-segment* reference on;
- declaration statements are plain `ASTWrapperPsiElement`s with no `getName()`, so a
  resolved target has no name to match, no `getNameIdentifier()` to navigate to, and
  `getTextOffset()` points at the `model` keyword rather than the name.

M5 must be amended before it ships. See D7 and plan 00 §M5.

### F6 — `@Ns.name` is one lexer token, so decorator references are not reachable

M2 (plan 01 §"Decorators and directives") deliberately emits `@name` / `@Ns.name` as a
**single** `DECORATOR` token so the whole thing colours as metadata. That decision is sound
for highlighting and is already shipped and tested. It also means there is no identifier
node inside a decorator application for a reference to attach to. Decorator navigation
therefore cannot be delivered without splitting that token — a breaking change to M2's lexer
tests and M3's highlighter tests. Out of scope here; open question 1.

## Decision

**D1. References are modelled as `PsiReference`, implemented on our own PSI, not as a
`GotoDeclarationHandler` and not (yet) as a `PsiSymbolReference`.**

Concretely: a `TypeSpecIdentifier` PSI node overrides `getReference()` and returns a
`TypeSpecReference : PsiPolyVariantReferenceBase<TypeSpecIdentifier>`. Rationale in F3 —
one implementation, five features. We do **not** use `psi.referenceContributor`: that EP
exists to attach references to PSI you do not own (XML, Java string literals). We own the
PSI, so `getReference()` is more direct, cheaper at runtime (no pattern matching on every
element) and easier to test.

We do **not** use the newer `PsiSymbolReference` / `psi.symbolReferenceProvider` API. It is
available in CE (F1) but is still marked experimental in the platform, has thinner
test-framework support, and the refactoring/search stack for custom languages is still
predominantly `PsiReference`-shaped. Revisit if the platform deprecates `PsiReference`;
recorded as a known future migration, not a defect.

**D2. Resolution is a cached tree walk, prefiltered by the word index. No stubs, no custom
indices, in this milestone.**

Three tiers, tried in order, stopping at the first that yields a hit:

- **Tier A — current file.** Enclosing namespaces innermost-outward, then the file's
  blockless namespace, then global; plus `using` bindings visible at each scope (F4).
- **Tier B — transitive `import` closure** of the current file. Relative file paths and
  directory-with-`main.tsp` only; bare specifiers (`import "@typespec/rest";`) are skipped.
  Cycle-safe via a visited set.
- **Tier C — project widening.** `CacheManager.getVirtualFilesWithWord(segmentText,
  UsageSearchContext.ANY, tspScope, true)` (F2), where `tspScope` is
  `GlobalSearchScope.getScopeRestrictedByFileTypes(projectScope, TypeSpecFileType.INSTANCE)`.
  Only those files are parsed.

Caching: a per-file `name → declarations` table via
`CachedValuesManager.getCachedValue(psiFile) { Result.create(table, psiFile) }`, so editing
one file invalidates only that file's table — **not** `PsiModificationTracker.MODIFICATION_COUNT`,
which would invalidate everything on every keystroke. The reference result itself goes
through `ResolveCache.resolveWithCaching`.

**Tier C is hard-capped at 50 candidate files.** Beyond the cap the resolver returns
*unresolved* rather than parsing on. Degrading to "no navigation" is acceptable; freezing
the EDT inside a read action is not. The cap is a named constant and is unit-tested.

Why not stubs now: a stub index means `StubElementTypeHolder`, stub classes for every
declaration, a stub version constant, and an index rebuild story — comfortably a milestone
of its own, and premature before we know navigation is shaped right. F2 buys most of the
benefit for a fraction of the cost.

Where this breaks, stated plainly:

| Project size | Behaviour |
|---|---|
| ≤ 200 `.tsp` files | Instant. Tier C rarely reached, and cheap when it is. |
| 200–1000 | Fine after warm-up. First tier-C resolve for a given name parses the word-index candidates (usually < 10 files). |
| > 1000, **or** a very common segment name (`Name`, `Id`, `Error`) | The word prefilter stops discriminating; the 50-file cap fires and navigation silently stops working for those names. **This is the signal to build the stub index.** |
| Any size | No stubs means candidate files are held as full ASTs; the platform drops them under memory pressure, causing re-parse churn on the next resolve. |

**D3. Every TypeSpec reference is soft (`isSoft() == true`).**

TypeSpec's built-in types (`string`, `int32`, `boolean`, `null`, `Record<>`, `Array<>`, …)
and every library type are not declared anywhere in the user's `.tsp` sources, so they will
never resolve. A hard reference invites the platform's unresolved-reference machinery to
paint the file red. Soft references keep the editor quiet and keep "does not resolve" a
non-event. When M6's annotator wants to flag genuinely unknown names, it does so with its
own, deliberately narrow rule — not as a side effect of this milestone.

**D4. Multi-resolve from day one.** `TypeSpecReference` extends
`PsiPolyVariantReferenceBase`. Tier C can legitimately find two `model Foo` declarations in
two unrelated compilations inside one IDE project; showing the user a chooser is correct,
jumping to an arbitrary one is not. `resolve()` returns the single result when there is
exactly one, null otherwise — the standard `PsiPolyVariantReferenceBase` contract.

**D5. The milestone is `M5.5`, and it lands *before* M6.**

Decimal, not renumbered: ADR 0002 and ADR 0003 both reference M4/M5/M6/M7 by number
(ADR 0003 D1/D3, "plan 00 §M4", "M5a/M5b"). Renumbering would silently falsify them.

Before M6, because:
- It only requires M5, and it is the feature the owner actually asked for.
- M6's annotator wants "declaration names vs references, resolved vs unresolved" — that
  distinction *is* M5.5's resolver. Doing M6 first means writing it blind and rewriting it.
- M6's completion contributor should offer in-scope type names, which is
  `TypeSpecReference.getVariants()` over the same scope walk. Same argument.
- M6's structure view needs `PsiNamedElement` + `getNameIdentifier()` on declarations, which
  M5.5 (via the M5 amendment, D7) establishes. M6 gets cheaper, not more expensive.

Nothing in M6 is a prerequisite for M5.5.

**D6. Find Usages ships in M5.5. Rename and Go To Symbol do not — they become M6.5.**

Find Usages is in: it is one class (`TypeSpecFindUsagesProvider` on `lang.findUsagesProvider`)
plus a `DefaultWordsScanner`; `ReferencesSearch`'s default executor already works off the
word index (F2) and `PsiReference.isReferenceTo()`. It is read-only and it is the natural
inverse of the feature being built. Without the provider, the Find Usages action reports
"Cannot search for usages" on a `.tsp` declaration, which reads as a bug.

Rename is out: it is a **write** operation. It needs `setName()`, which needs a
`TypeSpecElementFactory` (parse a throwaway file, lift the identifier node), plus a
`NamesValidator`, plus correct backtick-escaping when the new name is a TypeSpec keyword or
contains spaces. That is an independent risk surface and it is the one thing here that can
corrupt a user's source. It gets its own milestone with its own tests.

Go To Symbol is out: `gotoSymbolContributor` /`ChooseByNameContributorEx` is called per
keystroke in the popup over the whole project. Without a stub index that means parsing the
project repeatedly — exactly the failure mode D2's cap exists to avoid. Go To Symbol is
gated on the stub index, not on this milestone.

Also out of M5.5, for the record: bare-specifier / `node_modules` imports (open question 2),
decorator name resolution (F6, open question 1), template-parameter scoping, and resolving
model *properties* / enum *members* / interface *operations* as members of a qualified name
(only namespaces are traversable containers in the first cut).

**D7. M5 is amended now, before it ships.** Four additions, written into plan 00 §M5:

1. **Grammar must emit `TypeSpecIdentifier`** — a rule node wrapping a single name
   (ordinary or backticked), and **`TypeSpecQualifiedName`** — a dot-separated sequence of
   `TypeSpecIdentifier`. Every construct that names a type uses `TypeSpecQualifiedName`
   uniformly: `using`, `namespace`, `extends`, `is`, property types, `op` parameter and
   return types, template arguments, spread (`...Base`), and union variant types. Without a
   per-segment node there is nothing to attach a reference to and `Foo.Bar` cannot be
   navigated segment by segment.
2. **Every declaration statement's PSI implements `PsiNameIdentifierOwner`** (via a
   Grammar-Kit `mixin`/`implements` and a shared `TypeSpecNamedElementImpl` base):
   `model`, `enum`, `union`, `interface`, `alias`, `scalar`, `op`, `namespace`, and — for
   M6's structure view and a later milestone's member resolution — `model` property,
   `enum` member, `union` variant, template parameter.
3. **`getNameIdentifier()`, `getName()`, `getTextOffset()` are part of M5's contract**, not
   M5.5's. `getNameIdentifier()` returns the name's `TypeSpecIdentifier` node;
   `getName()` returns its text with surrounding backticks stripped; `getTextOffset()`
   returns `nameIdentifier.textRange.startOffset` so navigation, Find Usages previews and
   the structure view all point at the name rather than at the `model` keyword.
   `setName()` may throw `IncorrectOperationException` in M5 — rename is M6.5.
4. **`TypeSpecFile` exposes `getImportStatements()`, `getUsingStatements()`,
   `getFileNamespace()` and `getTopLevelDeclarations()`.** M5.5's resolver reads these on
   every file it touches; discovering them by ad-hoc `PsiTreeUtil` queries from the resolver
   would scatter grammar knowledge across two milestones.

For a dotted `namespace Foo.Bar.Baz`, the `nameIdentifier` is the **last** segment; the
resolver expands the dotted path into a virtual namespace chain. This keeps the grammar
simple and matches how TypeSpec itself treats the sugar.

None of these four changes M5's *grammar coverage* materially — they are shape and API
requirements on a tree M5 was already going to build. Budget impact is small; the cost of
retrofitting them after M5 ships is not.

## Consequences

- M5 grows four explicit requirements (D7) and loses its "reference resolution is out of
  scope, a follow-on milestone if wanted" framing — it now points forward at M5.5.
- The roadmap gains M5.5 (this milestone) and M6.5 (rename + Go To Symbol + stub index).
  M6 and M7 keep their numbers, so ADR 0002 and ADR 0003 remain accurate.
- M6's annotator and completion contributor get an existing resolver to build on; plan 00 §M6
  is annotated to say so.
- The plugin keeps exactly one `<depends>` (F1). The ADR 0003 D3 spellchecking exception
  remains the only one on the table.
- A stub index is now an explicitly deferred, explicitly triggered piece of work (D2's table)
  rather than an unbounded "later".
- Ctrl-click will not work on decorators (F6) or on library types (D3). Both are visible
  gaps a user will notice; both are recorded, not accidental.

## Citations

All against **ideaIC-2025.2.6.3-aarch64** as resolved onto this project's compile classpath.

- `lib/app.jar!/META-INF/IdeaPlugin.xml` — EP declarations at lines 3162, 3165, 3207, 3210,
  3258, 3462, 3574, 3935, 3938, 4207, 4251, 4306 (F1).
- `lib/util-8.jar` — `PsiReferenceBase`, `PsiPolyVariantReferenceBase`,
  `PsiNameIdentifierOwner`, `AbstractElementManipulator`, `PsiScopeProcessor` (F1).
- `lib/app-client.jar` — `ResolveCache`, `FindUsagesProvider`, `DefaultWordsScanner`,
  `SimpleWordsScanner`, `CacheManager`, `FileTypeIndex`, `TargetElementUtil`,
  `ASTWrapperPsiElement`, `GotoDeclarationHandler`, `ChooseByNameContributorEx`,
  `RefactoringSupportProvider`, `LanguageNamesValidation$DefaultNamesValidator` (F1).
- javap of `lib/app-client.jar!/com/intellij/psi/impl/cache/impl/id/IdTableBuilding.class`
  (`getFileTypeIndexer` → `CacheBuilderRegistry.getCacheBuilder` →
  `LanguageFindUsages.getWordsScanner` → `new SimpleWordsScanner`) and of
  `SimpleWordsScanner.processWords` (null `Kind`) (F2).
- TypeSpec docs, `language-basics/namespaces` and `language-basics/imports` (F4).
- [plan 01](../plans/01-lexer-and-highlighter.md) §"Decorators and directives" (F6).

## Open questions for the project owner

1. **Decorator navigation.** `@Ns.name` is a single lexer token by design (F6). Ctrl-click on
   a decorator will do nothing. Making it work means splitting `DECORATOR` into `@` +
   `TypeSpecQualifiedName` in the lexer and re-baselining M2's lexer tests and M3's
   highlighter tests. Worth it, or accept the gap?
2. **Library imports.** `import "@typespec/rest";` resolves through `node_modules` and
   `package.json`'s `tspMain`. Reading those is plain filesystem work and needs **no**
   `NodeJS` plugin dependency, so it is not a CE-constraint problem — but it is real scope,
   and it is what makes built-in library types navigable. In or out?
3. **Confirm the M6.5 split** (rename + Go To Symbol + stub index as a separate milestone,
   D6), versus folding rename into M5.5.
4. **Confirm tier-C project widening** (D2). It is what makes navigation useful on real
   TypeSpec layouts (F4), but it can cross compilation boundaries inside one IDE project and
   offer a chooser with a wrong candidate in it. The strict alternative — import closure only
   — is always correct and frequently finds nothing.
5. ⚠ **Unverified:** whether `TargetElementUtil`'s Ctrl-click path applies any additional
   filter to *soft* references (D3). The SDK does not document one and the read of
   `TargetElementUtil` was not exhaustive. If Ctrl-click turns out to ignore soft references,
   the fallback is a thin `GotoDeclarationHandler` delegating to the same resolver, or making
   references hard and suppressing the unresolved-reference inspection. `tsp-tester`'s
   `elementAtCaret` assertion (plan 02) detects this on the first run — it is a first-hour
   discovery, not a late surprise.
