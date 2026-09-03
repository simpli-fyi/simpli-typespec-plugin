# ADR 0011 — A stub index replaces tier C

- **Status:** **ACCEPTED.** Chosen by the owner on 2026-09-03 (ADR 0008 option C), over option A
  (raise the cap) and option B (log the degradation).
- **Date:** 2026-09-03
- **Supersedes:** [ADR 0008](0008-tier-c-file-cap.md) — that ADR is now **CLOSED**; its question
  ("keep the cap, raise it, or replace it?") is answered here.
- **Amends:** [ADR 0004](0004-reference-resolution-approach.md) D2 (tier C was always described
  there as a stand-in for this), and picks up [plan 02](../plans/02-navigation.md) §Risks/3.
- **Constrained by:** [ADR 0010](0010-library-import-resolution.md) §Consequences (the
  `node_modules` exclusion is a forward constraint on any index we build).
- **Implemented by:** [plan 06](../plans/06-stub-index.md) (M6.5a–M6.5e).
- **Tree at decision time:** 208 tests / 0 failures, `verifyPlugin` Compatible, two `<depends>`.

---

## Context — three reports, one mechanism

1. Silent unresolved references on common names (ADR 0008's own analysis: the cap is
   anti-correlated with usefulness).
2. A "Resolving reference…" EDT hang on a large project. Root-caused to unbounded recursive
   `using` re-resolution and to `node_modules` sitting inside `tspScope`. **Both are fixed**;
   neither fix touches the structural ceiling.
3. Cross-module resolution: module `shared` declares `scalar VolumeUnit extends string;` and
   `model MetaData`; a second module does `using Shared;` and references `VolumeUnit` /
   `...MetaData;`. Neither resolves. The *same* spread works **inside** `shared` — because there
   it resolves through the import closure (tiers A/B, direct, cap-free) and never reaches tier C.

`tsp-dev` reproduced case 3 with a multi-content-root fixture and **both cases resolved
correctly**, so `scalar` reachability, spread references and module-spanning scope are each
confirmed working in isolation. What the reproduction could not reproduce is the owner's *file
count*. The cap is the remaining candidate mechanism and the only one that is a property of
project size rather than of the code under test. It is unconfirmed against the owner's tree,
which we do not have access to — and it is the one mechanism whose removal we can justify on
design grounds without needing that confirmation, because the cap is a known-wrong stand-in and
not merely a suspect.

## Decision

**D1 — Build the stub index; delete tier C's word-index path outright.**

`TypeSpecSearchScopes.filesContainingWord` and `TIER_C_FILE_CAP` are removed, not kept behind a
flag. Two sources of truth for "what does this name mean project-wide" would be a permanent
mystery-differential; a stale word-index fallback would mask index bugs exactly when we most need
to see them. `tspScope` and its `node_modules` filter survive — they become the stub *query*
scope. There is nothing to cap afterwards: the index answers "which declarations are named
`Response`", not "which files contain the word `Response`", so the candidate set is declarations,
not textual occurrences, and no file is parsed to find out.

**D2 — Tiers A and B survive, unchanged, ahead of the index.**

Not as an optimisation. Two semantic reasons:

- **A/B work in dumb mode.** They are pure PSI over a bounded import closure. Today tier C
  returns unresolved during indexing; after this change *the same fallback still resolves* — the
  index tier is simply skipped. This is a strict improvement over today, not a regression.
- **A/B reach into `node_modules`; the index deliberately does not** (D4). After
  [ADR 0010](0010-library-import-resolution.md) M5.6a the import closure follows bare specifiers
  into libraries. That is the *only* sanctioned route into library code, and it must stay ahead
  of the index or library symbols stop resolving.

Tier ordering also encodes TypeSpec's own scoping (nearest scope first), so "first tier that
yields, wins" is a correctness rule, not a perf heuristic.

**D3 — One index, keyed by simple name; the namespace path lives in the stub.**

`TypeSpecDeclarationNameIndex : StringStubIndexExtension<TypeSpecNamedElement>`, key = the
backtick-stripped declaration name. No namespace-qualified index. Each declaration stub carries
its enclosing namespace path as a pre-computed dotted string, so the qualified question —
"is there a `VolumeUnit` under namespace `Shared`?" — is answered by one name lookup plus a string
compare **per stub**, with no AST load and no second index to keep consistent.

A qualified-key index was rejected for now: bare-name lookup is required regardless (a `using`
brings names in unqualified), TypeSpec namespaces merge across files so a qualified key is not a
unique key either, and index hits here are *declarations*, not word occurrences — a name declared
by more than a handful of declarations in one project is already an ambiguity the user can see.
Revisit only on measurement (plan 06 §Deferred).

**D4 — `node_modules` is excluded at index-build time, not only at query time.**

`shouldBuildStubFor(VirtualFile)` returns `false` for any file with a `node_modules` path segment,
so no stub is ever built there. Verified against the 2025.2 platform at bytecode level:
`StubTreeBuilder.getStubBuilderType` calls `LanguageStubDefinition.shouldBuildStubFor`, and an
`IStubFileElementType` is adapted to that interface. The query scope keeps the same filter as
defence in depth. This is [ADR 0010](0010-library-import-resolution.md)'s forward constraint
honoured literally: an index over an unfiltered project scope would recreate ADR 0008's pathology
with a bigger blast radius and would make Go To Symbol offer a dependency's vendored test
fixtures.

Coexistence with M5.6a's library-import work is clean *because* they use different mechanisms:
that work resolves **into** `node_modules` by **targeted lookup along an import edge**
(`TypeSpecImportResolver`, a `package.json` read plus `findFile`) — it never queries an index. The
index answers **project-wide search**, which is exactly the question that must not be allowed to
range over dependencies. The asymmetry ADR 0010 §Consequences already stated and accepted —
reachable along an import edge, not by project-wide search — is unchanged by this ADR.

**D5 — Legacy `IStubElementType`, not the new `StubRegistryExtension` API.**

Both exist in the 2025.2 CE distribution. `IStubFileElementType` is `@ApiStatus.Obsolete` (not
deprecated, not scheduled for removal — verified in `app-client.jar`). We use the obsolete one
anyway, and the reason is forced, not preference: **Grammar-Kit 2023.3.4 generates**

```java
public TypeSpecModelStatementImpl(TypeSpecDeclStub stub, IStubElementType stubType) {
  super(stub, stubType);
}
```

(verified by running the generator standalone against this repo's own `.bnf`). The generated
constructor's parameter type is `IStubElementType`, so the element types the platform hands to
PSI creation must *be* `IStubElementType` instances. The new-API route would require hand-writing
the 10 PSI impl classes Grammar-Kit generates today, which trades one obsolete-but-supported API
for a permanent maintenance burden. Migration is a follow-up gated on Grammar-Kit itself, and is
recorded as an open question below.

**D6 — Stub version discipline is a single constant with a written trigger list.**

One `TypeSpecStubVersion.VERSION`, referenced by both the file element type's `getStubVersion()`
and the index extension's `getVersion()`. Bump it — in the same commit as the change — for **any**
of:

1. a stub class gains, loses or reorders a serialised field, or changes a field's encoding;
2. the set of stubbed element types changes (a rule gains or loses `stubClass=`);
3. any external ID changes — the holder interface's `externalIdPrefix`, a field name inside the
   holder, or the file element type's `getExternalId()`;
4. `shouldCreateStub` / `skipChildProcessingWhenBuildingStubs` / `shouldBuildStubFor` changes
   which nodes or which files get stubs — **including the `node_modules` predicate**;
5. the value of anything stored in a stub changes meaning: backtick stripping, the dotted
   namespace-path computation, name normalisation;
6. a grammar change alters the shape of a stubbed rule's subtree such that a stub built by the
   old code no longer describes the new PSI.

Not a bump: resolver-only changes, query-side filtering, scope changes that do not affect
`shouldBuildStubFor`, KDoc.

The failure mode this list exists to prevent is asymmetric and nasty: **forgetting a bump does not
fail the build or the tests** (tests re-index from scratch), it corrupts a *user's* on-disk index
and surfaces months later as randomly broken resolution that a restart does not fix. Over-bumping
costs one re-index. When in doubt, bump.

## Consequences

- **The ceiling is gone**, not moved. Resolution cost for a leading segment becomes: one stub-index
  query, plus O(hits) string comparisons on stub fields, plus AST load for the *target* file only.
  No candidate file is parsed to be rejected. This is strictly cheaper than today's tier C even
  when today's tier C succeeds.
- **Correctness on common names is restored** — the `Shared` / `Common` / `Response` case that the
  cap was silently failing is the case the index is best at.
- **Dumb mode improves** (D2): A/B still answer.
- **First launch after install re-indexes `.tsp` files.** Expected, one-time, same as the word-index
  bump `lang.findUsagesProvider` already caused in M5.5b.
- **A new class of bug becomes possible**: stub/AST mismatch and stale indexes. Mitigated by D6,
  by a stub-tree golden test, and by keeping the stub payload deliberately small — name, namespace
  path, and nothing that requires interpretation.
- **`node_modules` declarations are invisible to project-wide search, by design** (D4). If the
  owner reports "I cannot Cmd-click a library symbol from a file that does not import it", that is
  this decision, not a bug.
- **Go To Symbol becomes nearly free** once the index exists (`gotoSymbolContributor`, a core EP) —
  scheduled, not assumed, as plan 06 M6.5e.

## CE compatibility

Every API named here is in the core `com.intellij` descriptor, the same provenance argument as
[ADR 0004](0004-reference-resolution-approach.md) F1. Verified against
`ideaIC-2025.2.6.3-aarch64` by inspecting the distribution itself:

| API / EP | Where | Verified |
|---|---|---|
| `com.intellij.psi.stubs.*` (`IStubElementType`, `StubBase`, `NamedStubBase`, `StringStubIndexExtension`, `StubIndexKey`, `IndexSink`, `StubInputStream/OutputStream`, `DefaultStubBuilder`, `PsiFileStubImpl`, `StubElementTypeHolderEP`) | `lib/util-8.jar` | `javap` signatures read |
| `com.intellij.psi.tree.IStubFileElementType` | `lib/app-client.jar` | `javap`; annotated `@ApiStatus.Obsolete` |
| `com.intellij.extapi.psi.StubBasedPsiElementBase` | `lib/*.jar` | `javap`; ctors `(T, IStubElementType)`, `(T, IElementType)`, `(ASTNode)` |
| `<stubIndex>` EP | `META-INF/IdeaPlugin.xml:3460`, `META-INF/Indexing.xml:10` | grep of the shipped descriptors |
| `<stubElementTypeHolder>` EP | `META-INF/IdeaPlugin.xml:3168`, `META-INF/Core.xml:46` | same |
| `<gotoSymbolContributor>` EP | `META-INF/IdeaPlugin.xml:4251` | same |

**No third `<depends>`.** The two existing ones (`com.intellij.modules.platform`,
`com.intellij.modules.spellchecker`) are unchanged, and `verifyPlugin` is the gate.

## Open questions — owner's call, not the architect's

1. **Sequencing against the implicit `@typespec/compiler` std library.** Without it `@doc`,
   `string`, `int32` never resolve — [ADR 0010](0010-library-import-resolution.md) open question 1,
   and by the architect's reckoning the single highest-value remaining follow-up by number of
   symbols it makes navigable. It is independent of this ADR (it adds an import edge; it does not
   touch the index) and small. Plan 06 sequences it **first**, as M5.6g, on the argument that it is
   one dev run and it unblocks the most user-visible gap. The owner may reorder.
2. **New stub API migration** (D5). Revisit when Grammar-Kit emits `StubElementFactory`-shaped PSI,
   or if `IStubFileElementType` moves from Obsolete to Deprecated. Not now.
3. **Whether Go To Symbol (M6.5e) ships in this plan or waits for M6.** It is cheap once the index
   exists but it is a new user-facing feature with its own polish surface.
