# Plan 02 — Reference resolution and jump navigation (M5.5)

> ## Status: SHIPPED, 2026-09-03 — as M5.5a (`f1ab14a`) + M5.5b (`5fea9ef`)
>
> The milestone was split at the seam §Risks/9 pre-authorised. Everything this plan scoped
> is implemented, tested and owner-confirmed in a live IDE. See §"As built" below for the
> two real bugs the milestone surfaced, and for what remains open.
>
> Sequencing note: this plan ran between M6c and M6d, per
> [ADR 0007 D11](../adr/0007-corpus-driven-grammar-acceptance.md) — its prerequisite was
> **M6c green**, not M5c green. See [plan 04](04-grammar-corrections.md) §M5.5.

> **Originally ratified 2026-09-02, post-M4b.** Written before M4b landed, re-read against the
> shipped tree at `ce92694`, **substantively unchanged**. M4b's flat `ParserDefinition`
> ([ADR 0005](../adr/0005-minimal-parser-definition-for-commenting.md)) produces no composite
> element types whatsoever, so all five prerequisite checks below still fail against today's
> tree and this milestone is still fully gated on the real grammar. Three edits were made:
> the prerequisite is now **M5c** (not "M5"), which is defined in
> [plan 03](03-grammar-and-psi.md); the ADR 0004 D7 amendments are now binding requirements of
> plan 03 §M5b rather than a note on someone else's milestone; and the `WordsScanner`
> note in "Files to create" is corrected. See
> [ADR 0004 § Post-M4b review](../adr/0004-reference-resolution-approach.md).
>
> **Plan numbering is allocation order, not milestone order** — plan 03 runs before this one.

Governed by [ADR 0004](../adr/0004-reference-resolution-approach.md). Prerequisite:
**M5c green** ([plan 03](03-grammar-and-psi.md)), including the four amendments in
ADR 0004 D7 — which plan 03 §M5b implements and covers with `TypeSpecPsiContractTest`.
Do not start this plan against an M5 that does not satisfy them —
retrofitting `PsiNameIdentifierOwner` onto a shipped grammar is the expensive path.

Package root: `simpli.fyi.plugins.typespec`. Kotlin, JDK 21, IntelliJ IDEA Community
2025.2.6.3 on the compile classpath. One `<depends>`, unchanged
([ADR 0004 F1](../adr/0004-reference-resolution-approach.md)).

**Deliverable in one sentence:** Ctrl/Cmd-click (and *Go To Declaration*, and *Find Usages*)
on a TypeSpec type reference lands on the `model` / `enum` / `union` / `interface` / `alias` /
`scalar` / `op` / `namespace` declaration it names.

---

## What this milestone does not do

Stated up front so nobody implements it by accident:

| Not in M5.5 | Where it goes | Why |
|---|---|---|
| Rename refactoring | M6.5 | Write operation; needs an element factory, a `NamesValidator`, backtick escaping. ADR 0004 D6. |
| Go To Symbol / Go To Class | M6.5 | Needs a stub index to be affordable. ADR 0004 D6. |
| Stub index | M6.5 | ADR 0004 D2 — the word index buys most of it for a fraction of the cost. |
| `@decorator` navigation | blocked | `@Ns.name` is one lexer token by design. ADR 0004 F6, open question 1. |
| `import "@typespec/rest"` (bare specifiers) | open question 2 | Needs `node_modules` / `package.json` `tspMain` walking. |
| Resolving `Foo.bar` where `bar` is a model **property** / enum member / interface op | later | Only namespaces are traversable containers in the first cut. |
| Template parameter scoping (`model Page<T> { items: T[] }` → `T`) | later | See risk 6; `T` simply does not resolve, softly. |
| Unresolved-reference highlighting | M6 annotator | All references are soft here. ADR 0004 D3. |

---

## Prerequisite check — run this before writing any code

`tsp-dev` starts by verifying, against the **M5c** tree, that all five hold. If any fails, stop
and report; do not work around it in the resolver. (`TypeSpecPsiContractTest` from
[plan 03](03-grammar-and-psi.md) §M5b/§M5c already asserts 1–5; re-running it *is* this check.
If plan 03 shipped and this check fails, that is a plan 03 regression, not an M5.5 problem.)

1. `TypeSpecIdentifier` exists as a PSI rule node wrapping exactly one name token
   (ordinary `IDENTIFIER`, including the backticked form).
2. `TypeSpecQualifiedName` exists, contains one or more `TypeSpecIdentifier` children
   separated by `DOT`, and is the node used by **every** naming position: `using`,
   `namespace`, `extends`, `is`, model property types, `op` parameter/return types,
   template arguments, spread, union variant types.
3. `model` / `enum` / `union` / `interface` / `alias` / `scalar` / `op` / `namespace`
   statements implement `PsiNameIdentifierOwner`, and `getNameIdentifier()` returns a
   `TypeSpecIdentifier` (not a raw leaf, not null for a well-formed declaration).
4. `getTextOffset()` on those declarations returns the **name's** offset, not the keyword's.
5. `TypeSpecFile` exposes `getImportStatements()`, `getUsingStatements()`,
   `getFileNamespace()`, `getTopLevelDeclarations()`.

A five-assertion `TypeSpecPsiContractTest` is the cheapest way to check this and is the
first thing `tsp-tester` writes for this milestone.

---

## As built

### M5.5a — reference resolution and go-to-declaration (`f1ab14a`, tests `bc1b101`)

Tiers A (same file) and B (transitive `import` graph). `TypeSpecReference`,
`TypeSpecResolver`, `TypeSpecScope`, `TypeSpecFileDeclarations`, `TypeSpecImportGraph`,
`TypeSpecIdentifierManipulator`. The owner has confirmed Cmd-click / *Go To Declaration*
working in a live IDE against a real project — the §"Done when" `runIde` checklist passed.

Two real bugs were found and fixed during the milestone. Both are regression-pinned; neither
was predicted by this plan's Risks section.

1. **`StackOverflowError` resolving a `using` statement's own target** (`f1ab14a`).
   Resolving the reference inside `using Foo.Bar;` consulted the file's `using` statements to
   build the scope, which re-entered resolution of the same reference. Fixed with a
   `ThreadLocal` re-entrancy guard: a reference already being resolved on this thread
   contributes nothing to the scope, so the recursion terminates with "unresolved" rather
   than a stack overflow. A guard, not a special case for `using` — the same cycle is
   reachable through any scope contributor that itself resolves.

2. **Fully-qualified references into dotted namespaces did not resolve** (`5528c5e`, pin
   flipped in `822f5a4`). A blockless `namespace A.B.C;` declares *three* namespaces, and the
   resolver indexed it once, under its full path. A reference to `A.B` therefore found
   nothing. Fixed by indexing a dotted namespace declaration **once per own segment, under
   its correct prefix path**, and by having `resolveSegment` carry the denoted path forward
   rather than calling `fullPathOf` on the declaration it landed on. Multi-file
   overlapping-namespace coverage was added in the same change.

Note for anyone reading the resolver: this is the concrete consequence of
[plan 04](04-grammar-corrections.md) §M5.5's correction — under a blockless namespace,
`TypeSpecFile.getTopLevelDeclarations()` returns exactly one element, and the resolver
recurses through namespaces rather than treating that accessor as "every declaration in the
file".

### M5.5b — tier C and Find Usages (`5fea9ef`, tests `bfc0b9b`)

- `lang.findUsagesProvider` → `TypeSpecFindUsagesProvider`. *Find Usages* on a declaration
  lists references grouped by declaration kind.
- Tier C project-wide resolution via `TypeSpecSearchScopes`, word-index prefiltered
  (`CacheManager.getVirtualFilesWithWord`, `UsageSearchContext.ANY`), with
  `TIER_C_FILE_CAP = 50` and unresolved-on-dumb-mode.

**Both cliffs are tested, and degradation is clean, never partial** — the resolver never
parses a truncated candidate set. §Risks/3 above predicted the usefulness collapse on common
names and it is real; it is now written up as an owner decision in
**[ADR 0008](../adr/0008-tier-c-file-cap.md)** rather than left as a risk bullet.

### What remains — M6.5

Unchanged in scope from §"What this milestone does not do":

| Remaining | State today |
|---|---|
| **Rename refactoring** | `TypeSpecIdentifierManipulator` is **registered but deliberately throws** `IncorrectOperationException("Rename is not supported until M6.5")`. It exists so the platform does not log a confusing "no manipulator" error from unrelated code paths (ADR 0004 D6). Implementing rename needs an element factory, a `NamesValidator` and backtick escaping. |
| **Go To Symbol / Go To Class** | Not implemented. Needs the stub index to be affordable. |
| **Stub index** | Not implemented. It is also option C of [ADR 0008](../adr/0008-tier-c-file-cap.md) — the only option that removes the tier C ceiling rather than moving it. |

Still open from §Risks, unchanged: `@decorator` navigation (blocked by the one-token
`DECORATOR` design, ADR 0004 F6), bare import specifiers (`import "@typespec/rest";`),
resolving `Foo.bar` where `bar` is a model property / enum member / interface op, template
parameter scoping (`T` resolves to nothing, softly), and `import` string literals as
navigable file references.

---

## Architecture

```
  editor Ctrl-click
        │
        ▼
  TargetElementUtil ──► PsiFile.findReferenceAt(offset)
        │                      │
        │                      ▼
        │            TypeSpecIdentifier.getReference()
        │                      │
        │                      ▼
        │            TypeSpecReference : PsiPolyVariantReferenceBase
        │                      │  multiResolve()  [via ResolveCache]
        │                      ▼
        │            TypeSpecResolver.resolve(identifier)
        │                      │
        │      ┌───────────────┼───────────────────┐
        │      ▼               ▼                   ▼
        │  TypeSpecScope   TypeSpecImportGraph  TypeSpecSearchScopes
        │  (lexical walk)  (tier B closure)     (tier C word-index prefilter)
        │      │               │                   │
        │      └───────────────┴───────────────────┘
        │                      ▼
        │            TypeSpecFileDeclarations  (per-file cached name → declarations)
        ▼
  navigate to PsiNameIdentifierOwner.getTextOffset()
```

Three ideas carry the whole design:

1. **One reference class**, on `TypeSpecIdentifier`, feeding navigation, find-usages and
   (later) completion and rename — ADR 0004 D1/F3.
2. **Per-file declaration tables cached on the file itself**, so editing one file
   invalidates one table — ADR 0004 D2.
3. **The word index as prefilter**, so tier C parses candidate files, not the project —
   ADR 0004 F2, capped at 50 files.

---

## Files to create

### `resolve/TypeSpecReference.kt`

```kotlin
class TypeSpecReference(
    element: TypeSpecIdentifier,
) : PsiPolyVariantReferenceBase<TypeSpecIdentifier>(element, TextRange(0, element.textLength)) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, RESOLVER, /* needToPreventRecursion = */ true, incompleteCode)
            ?: ResolveResult.EMPTY_ARRAY

    override fun isSoft(): Boolean = true            // ADR 0004 D3 — never paint the file red

    override fun getVariants(): Array<Any> = emptyArray()   // filled in by M6's completion

    private companion object {
        val RESOLVER = ResolveCache.PolyVariantResolver<TypeSpecReference> { ref, _ ->
            TypeSpecResolver.multiResolve(ref.element)
        }
    }
}
```

Notes for `tsp-dev`:

- `rangeInElement` is the **whole** `TypeSpecIdentifier`, because the identifier node wraps
  exactly one name. Do not try to make one reference span a whole `TypeSpecQualifiedName`
  with sub-ranges — per-segment references are what make `Foo.<caret>Bar` and
  `<caret>Foo.Bar` navigate to different things.
- `needToPreventRecursion = true`. `alias A = B; alias B = A;` is legal input and must not
  stack-overflow.
- `getVariants()` returns empty **on purpose** in this milestone. M6's completion
  contributor fills it from `TypeSpecScope`; leaving it empty now avoids shipping a
  half-baked completion list.

Hook-up, in the M5 `.bnf` via `mixin`/`implements` (preferred) or in a hand-written
`TypeSpecIdentifierMixin`:

```kotlin
override fun getReference(): PsiReference? =
    if (TypeSpecResolver.isReferencePosition(this)) TypeSpecReference(this) else null
```

`isReferencePosition` returns **false** when the identifier *is* a declaration's
`nameIdentifier` (otherwise a declaration would hold a reference to itself and Find Usages
would count the declaration as a usage) and **false** inside an `import` string. Everything
else — `using`, `namespace` (see risk 4), type positions — is a reference position.

### `resolve/TypeSpecResolver.kt`

The entry point and the only place the tier logic lives.

```kotlin
object TypeSpecResolver {
    fun multiResolve(identifier: TypeSpecIdentifier): Array<ResolveResult>
    fun isReferencePosition(identifier: TypeSpecIdentifier): Boolean
}
```

Algorithm for `multiResolve`:

1. Let `qname` be the enclosing `TypeSpecQualifiedName` and `i` the index of `identifier`
   among its segments.
2. **If `i == 0`** — resolve the leading segment lexically:
   - Build the scope chain with `TypeSpecScope.chainFor(identifier)`: innermost enclosing
     block namespaces outward, then the file's blockless namespace and each of *its* dotted
     ancestors, then global. (TypeSpec resolves names after a blockless `namespace Foo.Bar;`
     relative to `Foo.Bar`, then `Foo`, then global — ADR 0004 F4.)
   - For each scope `S`, innermost first, look for a declaration named `identifier.name`:
     - among the **members of `S`** — where "members of `S`" means, across every file in the
       resolve scope, the declarations whose containing namespace path equals `S`'s path
       (namespaces merge across files);
     - then among the members of each namespace named by a `using` statement **declared in
       `S`** (a `using` binds locally to the namespace it appears in — ADR 0004 F4).
   - The first scope that yields any match wins; return all matches from that scope.
     Inner scopes shadow outer ones; ties inside one scope are returned as multiple results.
3. **If `i > 0`** — resolve segment `i-1` first (recursively, via the platform reference on
   that segment so `ResolveCache` is shared), require the result to be a namespace
   declaration, and look up `identifier.name` among that namespace's members. A non-namespace
   container (a model, an enum) yields no result in this milestone.
4. Return `PsiElementResolveResult.createResults(matches)`.

Resolve scope for step 2 is provided by `TypeSpecSearchScopes.candidateFiles(file, name)`
(below): tier A ∪ tier B, then tier C only if that yielded nothing.

Guardrails, all of which need a test:

- Never throw. A file containing `PsiErrorElement`s must resolve to *nothing*, not blow up.
- Never resolve to an element in a file that is not a `TypeSpecFile`.
- Respect `ProgressManager.checkCanceled()` inside the per-file loop; a Ctrl-click that the
  user abandons must be cancellable.

### `resolve/TypeSpecScope.kt`

Pure, PSI-only, no I/O. Turns a position into an ordered chain of namespace paths.

```kotlin
@JvmInline value class NamespacePath(val segments: List<String>)   // empty == global

object TypeSpecScope {
    fun chainFor(element: PsiElement): List<NamespacePath>          // innermost → global
    fun pathOf(declaration: PsiElement): NamespacePath              // where a decl lives
    fun usingsVisibleIn(path: NamespacePath, file: TypeSpecFile): List<NamespacePath>
}
```

`pathOf` walks up through enclosing block namespaces, prepending each dotted name in
reverse, then prepends the file's blockless namespace if there is one. `namespace Foo.Bar`
contributes two segments, not one — the dotted form is sugar for nesting
([ADR 0004](../adr/0004-reference-resolution-approach.md) F4/D7).

`usingsVisibleIn` also has to resolve the `using` target itself relative to the enclosing
namespace (`namespace MyOrg.Service; using Models;` means `MyOrg.Models`). Resolve the
`using` path with the same chain walk, longest-prefix-first.

### `resolve/TypeSpecFileDeclarations.kt`

The per-file cached table. This is the performance centre of the milestone.

```kotlin
class TypeSpecFileDeclarations private constructor(
    private val byName: Map<String, List<TypeSpecNamedDeclaration>>,
    private val byNamespace: Map<NamespacePath, List<TypeSpecNamedDeclaration>>,
) {
    fun find(name: String, path: NamespacePath): List<TypeSpecNamedDeclaration>
    fun containsName(name: String): Boolean

    companion object {
        fun of(file: TypeSpecFile): TypeSpecFileDeclarations =
            CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result.create(build(file), file)
            }
    }
}
```

- The cache dependency is **`file`**, deliberately. Using
  `PsiModificationTracker.MODIFICATION_COUNT` would invalidate every file's table on every
  keystroke anywhere and turn the design into a project re-parse per character
  ([ADR 0004](../adr/0004-reference-resolution-approach.md) D2). This is the single easiest
  thing to get wrong here.
- `build` walks the file once, recursively through namespace blocks, recording every
  `PsiNameIdentifierOwner` with its `NamespacePath`. Names are stored **backtick-stripped**
  (`getName()` already does that per the M5 contract).
- `containsName` is the cheap gate tier C uses before doing any namespace matching.

### `resolve/TypeSpecImportGraph.kt`

Tier B. Turns `import "./x.tsp";` into files.

```kotlin
object TypeSpecImportGraph {
    fun transitiveClosure(file: TypeSpecFile): Set<TypeSpecFile>   // includes `file`
}
```

- Only string literals starting with `./` or `../` are followed. A bare specifier
  (`"@typespec/rest"`) is skipped — ADR 0004 open question 2.
- Resolution: strip quotes, `file.virtualFile.parent.findFileByRelativePath(path)`. If the
  target is a **directory**, look for `main.tsp` inside it.
- Cycle-safe: a visited `Set<VirtualFile>`; TypeSpec allows import cycles.
- Cached per file with `CachedValuesManager` and a dependency on
  `PsiModificationTracker.MODIFICATION_COUNT` — the import graph is small, changes rarely,
  and correctness on file rename matters more than cache hit rate here. (Note the
  deliberate asymmetry with `TypeSpecFileDeclarations`, and why.)
- Cap the closure at 200 files, defensively.

### `resolve/TypeSpecSearchScopes.kt`

Tier C, and the cap.

```kotlin
object TypeSpecSearchScopes {
    const val TIER_C_FILE_CAP = 50            // ADR 0004 D2 — degrade, do not freeze

    fun tspScope(project: Project): GlobalSearchScope =
        GlobalSearchScope.getScopeRestrictedByFileTypes(
            GlobalSearchScope.projectScope(project), TypeSpecFileType.INSTANCE)

    /** Files that literally contain [name], word-index backed, capped. Null == cap exceeded. */
    fun filesContainingWord(project: Project, name: String): List<TypeSpecFile>?
}
```

`filesContainingWord` uses

```kotlin
CacheManager.getInstance(project)
    .getVirtualFilesWithWord(name, UsageSearchContext.ANY, tspScope(project), true)
```

- `UsageSearchContext.ANY` is mandatory, not a shortcut. Until this milestone's
  `FindUsagesProvider` ships, `.tsp` words are indexed by `SimpleWordsScanner` with a **null
  occurrence kind**, which the default indexer records as `ANY`; searching `IN_CODE` would
  return nothing. `ANY` is correct before *and* after the provider lands
  ([ADR 0004](../adr/0004-reference-resolution-approach.md) F2).
- Returns `null` when the result exceeds `TIER_C_FILE_CAP`; the resolver treats `null` as
  "unresolved" and stops. Do not truncate the list and search a subset — a silently partial
  answer is worse than a clean miss.
- `DumbService.isDumb(project)` → return `null`. Indices are unavailable during indexing;
  never call `CacheManager` in dumb mode.

### `resolve/TypeSpecIdentifierManipulator.kt`

```kotlin
class TypeSpecIdentifierManipulator : AbstractElementManipulator<TypeSpecIdentifier>() {
    override fun handleContentChange(
        element: TypeSpecIdentifier, range: TextRange, newContent: String,
    ): TypeSpecIdentifier = throw IncorrectOperationException("Rename is not supported yet")
}
```

Registered but deliberately throwing. Rationale: `PsiReferenceBase.handleElementRename`
routes through `ElementManipulators`, and with **no** manipulator registered the platform
logs a confusing "no manipulator" error from unrelated code paths. A registered manipulator
that throws a clear message is the honest state until M6.5 implements it. Say so in a
KDoc comment on the class.

### `findusages/TypeSpecFindUsagesProvider.kt`

```kotlin
class TypeSpecFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner =
        DefaultWordsScanner(
            TypeSpecLexerAdapter(),
            TokenSet.create(TypeSpecTokenTypes.IDENTIFIER),
            TypeSpecTokenSets.COMMENTS,
            TypeSpecTokenSets.STRINGS,
        )

    override fun canFindUsagesFor(element: PsiElement) = element is PsiNameIdentifierOwner
    override fun getHelpId(element: PsiElement): String? = null
    override fun getType(element: PsiElement): String        // "model", "enum", "namespace", …
    override fun getDescriptiveName(element: PsiElement): String
    override fun getNodeText(element: PsiElement, useFullName: Boolean): String
}
```

- The token sets come from M2's `TypeSpecTokenSets` — do not restate literals
  (plan 01 §M2).
- ⚠ **`getWordsScanner()` is optional, not mandatory.** Its default returns `null`, which
  means "`SimpleWordsScanner` is OK". We supply `DefaultWordsScanner` anyway for index
  quality and correct search-in-comments/strings filtering. Platform contract: the returned
  scanner **"MUST be thread-safe, otherwise you should return a new instance of your
  scanner"** — `DefaultWordsScanner` wraps a mutable `Lexer`, so **return a new instance per
  call** (as written above) and do not hoist it into a field or a `companion object`.
  (Corrected 2026-09-02, ADR 0004 § Post-M4b review item 6.)
- `getType` drives the "Found usages of *model* Foo" grouping. Derive it from the PSI class,
  and cover every declaration kind; a missing branch shows as an empty group header.
- Registering this **changes the word index encoding** for `.tsp` files (occurrences become
  `IN_CODE` / `IN_COMMENTS` / `IN_STRINGS` instead of `ANY`). This is why
  `TypeSpecSearchScopes` searches `ANY`. It also bumps the index version, so the first IDE
  start after installing this build re-indexes `.tsp` files — expected, mention it in the
  milestone report.

### `psi/TypeSpecNamedDeclaration.kt` (marker interface, if M5 did not already add it)
```kotlin
interface TypeSpecNamedDeclaration : PsiNameIdentifierOwner, NavigatablePsiElement
```

Implemented by every declaration statement. Gives the resolver one type to work with instead
of a `when` over eight PSI classes. If M5's amendment already introduced this, reuse it.

---

## Files to modify

### `src/main/resources/META-INF/plugin.xml`

Add, inside the existing `<extensions defaultExtensionNs="com.intellij">`:

```xml
<lang.findUsagesProvider
        language="TypeSpec"
        implementationClass="simpli.fyi.plugins.typespec.findusages.TypeSpecFindUsagesProvider"/>
<lang.elementManipulator
        forClass="simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier"
        implementationClass="simpli.fyi.plugins.typespec.resolve.TypeSpecIdentifierManipulator"/>
```

That is **all**. In particular:

- **No `psi.referenceContributor`** — we own the PSI and implement `getReference()` directly
  ([ADR 0004](../adr/0004-reference-resolution-approach.md) D1).
- **No `gotoDeclarationHandler`** — the `PsiReference` is the navigation mechanism (D1).
- **No `referencesSearch`** — the platform's default executor already searches via the word
  index and `isReferenceTo` (F2/F3).
- **No second `<depends>`** — every EP above is declared in the core `com.intellij`
  descriptor, the same file as `lang.parserDefinition` (F1). If `verifyPlugin` disagrees,
  **stop and escalate**; do not add a `<depends>` to make it pass.

### `src/main/grammars/TypeSpec.bnf` (M5's file)

Add `mixin` / `implements` on `TypeSpecIdentifier` (for `getReference`) and on the
declaration rules (for `PsiNameIdentifierOwner`), plus the `methods` entries pointing at
`TypeSpecPsiImplUtil`. If M5 already did this per ADR 0004 D7, this file is untouched — say
which in the report.

---

## Acceptance — what `tsp-tester` writes

Test root `src/test/kotlin/simpli/fyi/plugins/typespec/resolve/`, fixtures in
`src/test/testData/resolve/`. All tests extend `BasePlatformTestCase`.

### `TypeSpecPsiContractTest`

The five prerequisite-check assertions above, as five test methods. These fail loudly if M5
regresses the shape M5.5 depends on.

### `TypeSpecResolveTest` — the core suite

The canonical assertion, used for every case below, is

```kotlin
myFixture.configureByFile("resolve/<case>.tsp")
val target = myFixture.elementAtCaret
```

`elementAtCaret` goes through `TargetElementUtil` — **the same code path Ctrl-click uses** —
so a green test here is real evidence about the feature, not about a helper. Assert
`target is TypeSpecNamedDeclaration`, `target.name == "Expected"`, and
`target.containingFile.name == "expected.tsp"`.

Where a case must assert *absence*, use `myFixture.file.findReferenceAt(caretOffset)` and
assert `resolve() == null` — `elementAtCaret` throws when nothing is found.

| # | Case | Fixture | Expected |
|---|---|---|---|
| 1 | model → model, same file | `same-file-model.tsp` | resolves to `model Address` |
| 2 | every declaration kind | `all-kinds.tsp` | one caret per kind: `enum`, `union`, `interface`, `alias`, `scalar`, `op` |
| 3 | forward reference (used before declared) | `forward-ref.tsp` | resolves — order must not matter |
| 4 | shadowing | `shadowing.tsp` | inner `namespace Inner { model Foo }` wins over outer `model Foo` |
| 5 | qualified name, last segment | `qualified.tsp` | caret on `Bar` in `Foo.Bar` → `model Bar` |
| 6 | qualified name, first segment | `qualified.tsp` | caret on `Foo` in `Foo.Bar` → `namespace Foo` |
| 7 | dotted namespace declaration | `dotted-ns.tsp` | `A.B.C.Model` resolves through `namespace A.B.C` |
| 8 | `using` | `using.tsp` | unqualified `Address` resolves via `using Common;` |
| 9 | `using` relative to blockless namespace | `using-relative.tsp` | `namespace MyOrg.Service; using Models;` → `MyOrg.Models` |
| 10 | blockless namespace file | `blockless.tsp` | sibling declarations resolve unqualified |
| 11 | cross-file via `import` (tier B) | `import-main.tsp` + `import-dep.tsp` | resolves into the imported file |
| 12 | transitive import | `t-a.tsp` → `t-b.tsp` → `t-c.tsp` | resolves two hops away |
| 13 | import cycle | `cycle-a.tsp` ↔ `cycle-b.tsp` | resolves, terminates, no SOE |
| 14 | directory import | `dir-main.tsp` + `sub/main.tsp` | `import "./sub";` follows `main.tsp` |
| 15 | **cross-file with no import (tier C)** | `merged-a.tsp` + `merged-b.tsp`, same namespace, neither imports the other | resolves — this is the case ADR 0004 F4 says is the norm |
| 16 | built-in type | `builtin.tsp` | caret on `string` → `resolve() == null`, and `reference.isSoft` is true |
| 17 | unknown name | `unknown.tsp` | `resolve() == null`, no exception |
| 18 | backticked identifier | `backticked.tsp` | `` model `model` `` is reachable from `` `model` `` |
| 19 | template argument | `template-arg.tsp` | `Page<Address>` → caret on `Address` resolves |
| 20 | spread | `spread.tsp` | `...Base` resolves |
| 21 | `extends` / `is` | `extends-is.tsp` | both resolve |
| 22 | declaration name is not a reference | `decl-name.tsp` | `findReferenceAt` on `model **Foo**`'s own name returns null |
| 23 | broken file | `broken.tsp` (deliberate syntax error) | `resolve() == null`, no exception, test does not hang |
| 24 | self-referential alias | `alias-cycle.tsp` (`alias A = B; alias B = A;`) | terminates; no `StackOverflowError` |

Cases 15 and 24 are the two that most often reveal a wrong design. Write them early.

### `TypeSpecFindUsagesTest`

```kotlin
val usages = myFixture.testFindUsages("resolve/usages-a.tsp", "resolve/usages-b.tsp")
assertSize(3, usages)
```

Plus: `TypeSpecFindUsagesProvider.canFindUsagesFor` is true for every declaration kind and
false for a keyword leaf; `getType` returns a non-empty string for every declaration kind
(loop over the same fixture as case 2 — guards the missing-`when`-branch regression).

### `TypeSpecSearchScopesTest`

- `TIER_C_FILE_CAP` is honoured: with a synthetic project of `TIER_C_FILE_CAP + 5` `.tsp`
  files all containing the word `Widget`, `filesContainingWord` returns `null` and the
  resolver returns unresolved **without** parsing them. Assert on the return value, and
  assert the test completes well inside the default timeout.
- Dumb mode: inside `DumbServiceImpl.getInstance(project).runInDumbMode { }`,
  `filesContainingWord` returns `null` and no exception escapes.

### `TypeSpecCachingTest`

- `TypeSpecFileDeclarations.of(fileA)` returns the **same instance** on a second call.
- After `myFixture.type("x")` in **file B**, `TypeSpecFileDeclarations.of(fileA)` is still
  the same instance. This is the assertion that pins ADR 0004 D2's cache-dependency choice;
  without it, a later "cleanup" to `MODIFICATION_COUNT` passes every other test and quietly
  makes the plugin unusable on a large project.
- After typing in **file A**, `TypeSpecFileDeclarations.of(fileA)` is a new instance and
  reflects the edit.

### Regression sweep

Re-run M1–M5's suites unchanged. Nothing in this milestone may alter the lexer contract, the
highlighter, or the parse trees; if a `ParsingTestCase` `.txt` baseline changes, the grammar
changed and that needs justifying in the report.

---

## Done when

```bash
./gradlew clean build test verifyPlugin
```

is green, **and** `./gradlew runIde` demonstrates, in a scratch TypeSpec project of at least
three files:

1. Cmd-click on a type reference jumps to the declaration and the caret lands on the
   **name**, not the `model` keyword.
2. Cmd-hover shows the underline and the tooltip.
3. *Go To → Declaration* (`⌘B`) does the same.
4. *Find Usages* (`⌥F7`) on a declaration lists the references, grouped under
   "model Foo" / "enum Bar".
5. Cmd-click on `string` does nothing and produces **no** error, no red squiggle, no
   exception in the IDE log.
6. The IDE log contains no `Slow operations are prohibited on EDT` warning after twenty
   Cmd-clicks. If it does, that is a real finding — report it verbatim, it means a resolve
   is escaping the read-action/caching design.

`tsp-tester` reports items 1–6 by observation, verbatim, pass or fail.

---

## Risks and open questions

1. **⚠ Soft references and Ctrl-click.** ADR 0004 open question 5: it is unverified whether
   `TargetElementUtil` filters soft references out of the go-to-declaration path. If
   `TypeSpecResolveTest` case 1 fails on `elementAtCaret` while `findReferenceAt(...).resolve()`
   succeeds, that is this risk firing. Fallback, in order of preference: (a) make references
   hard and rely on M6's annotator being the only thing that reports unresolved names, with
   the platform's unresolved-reference inspection suppressed for TypeSpec; (b) add a thin
   `gotoDeclarationHandler` delegating to `TypeSpecResolver`. Do **not** silently pick one —
   report which and why.

2. **Tier C correctness.** Two unrelated TypeSpec compilations in one IDE project will see
   each other's declarations. `PsiPolyVariantReferenceBase` turns this into a chooser rather
   than a wrong jump (ADR 0004 D4), which is the mitigation, not a fix. Owner ratification
   pending (ADR 0004 open question 4).

3. **Tier C usefulness collapse on common names.** — **FIRED; now
   [ADR 0008](../adr/0008-tier-c-file-cap.md), an open owner decision.** `Name`, `Id`, `Error`, `Response` appear
   in most files, so the word prefilter stops discriminating, the 50-file cap fires, and
   navigation silently stops working *for exactly the names users click most*. This is the
   known ceiling of the no-stub design and the concrete trigger for M6.5's stub index. If
   `tsp-tester` sees it in `runIde` on a realistic project, say so loudly — it may justify
   pulling the stub index forward.

4. **Is a `namespace` name a reference or a declaration?** `namespace Foo.Bar { }` where
   `Foo` is declared elsewhere: the `Bar` segment is a declaration, the `Foo` segment is
   arguably both. First cut: treat **all** segments of a `namespace` declaration's name as
   declarations (no reference), so Ctrl-click on `Foo` there does nothing. `using Foo.Bar;`
   segments **are** references. Revisit if it feels wrong in `runIde`; note it in the report.

5. **`PsiPolyVariantReferenceBase` and equal-name merged namespaces.** When a namespace is
   declared in five files, "resolve the namespace `Foo`" legitimately has five targets. For
   the *intermediate* segments of a qualified name, deduplicate to the namespace **path**
   before looking up the next segment, or the search fans out multiplicatively. For the
   *final* segment, return all matches.

6. **Template parameters do not resolve.** In `model Page<T> { items: T[] }`, `T` resolves to
   nothing (softly). Acceptable — it is silent, not wrong. Making it work means a fourth
   scope kind ahead of the namespace chain, and it needs M5 to expose template parameter
   lists as named elements (ADR 0004 D7 item 2 asks for this, so the door is open).

7. **`import` string literals are not navigable** in this milestone. `import "./other.tsp";`
   as a clickable file reference is a `FileReferenceSet` job — cheap, genuinely useful, and a
   good candidate to fold in if this milestone finishes early. Ask before doing it; do not
   scope-creep silently.

8. **Index version bump.** Registering the `FindUsagesProvider` changes the `.tsp` word-index
   encoding, so the first launch on an existing project re-indexes. Harmless, but it will
   look like a regression to anyone who does not expect it.

9. **Milestone size.** This is the second-largest milestone after M5. **This risk fired and
   the split was taken as authorised** — M5.5a `f1ab14a`, M5.5b `5fea9ef`. If a single
   `tsp-dev` run stalls, split at a clean seam: **M5.5a** = `TypeSpecReference` + `TypeSpecResolver`
   tiers A and B + `TypeSpecScope` + `TypeSpecFileDeclarations` (cases 1–14, 16–24);
   **M5.5b** = tier C (`TypeSpecSearchScopes`, case 15) + Find Usages. M5.5a is a coherent,
   shippable state on its own: navigation works within a file and across explicit imports.
