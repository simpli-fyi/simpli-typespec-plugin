# ADR 0010 — Library imports resolve by targeted lookup, not by widening the search scope

- **Status:** ACCEPTED (architect's call). **Closes** [ADR 0004](0004-reference-resolution-approach.md)
  open question 2 ("should we resolve into libraries?") with a qualified *yes*: yes along
  declared `import` edges, still no by project-wide search.
- **Date:** 2026-09-03
- **Amends:** [ADR 0008](0008-tier-c-file-cap.md) — the `node_modules` exclusion from `tspScope`
  stays exactly as shipped. This ADR adds a second, disjoint path in.
- **Context:** owner bug report — neither `import "../master-data/branch.tsp"` nor
  `import "@typespec/openapi"` navigates. Plan: [05](../plans/05-import-and-decorator-navigation.md).

## Context

Two facts collide.

1. `import_statement ::= 'import' STRING ';'`. The `STRING` carries no `PsiReference`, so no
   import path navigates — including the relative ones, whose targets
   `TypeSpecImportGraph.resolveImportTarget` **already computes** for scope purposes. Half of the
   reported bug is "surface what already exists".
2. `import "@typespec/openapi"` necessarily targets a file under `node_modules`, and a concurrent
   hang fix deliberately **removed `node_modules` from `tspScope`** — 36 bundled test fixtures in
   `@typespec/protobuf` alone were pushing common identifiers past `TIER_C_FILE_CAP`, and one
   `filesContainingWord` call was observed to hang.

Re-adding `node_modules` to `tspScope` would fix the import arrow and reintroduce the hang. That
is not a trade to make.

## Decision

### D1 — Two disjoint mechanisms, and only one of them is a *search*

| | Tier C (`TypeSpecSearchScopes.tspScope`) | Import resolution (this ADR) |
|---|---|---|
| Shape | **search**: word index → N candidate files → parse | **lookup**: one specifier → at most one file |
| Cost | unbounded in the number of project files; capped at 50 | O(directory depth) VFS `findChild` calls + one `package.json` read |
| `node_modules` | **excluded, permanently** | **entered, but only at a path the user literally wrote** |

`node_modules` is not put back into any `GlobalSearchScope`. `tspScope` is not touched by this
work at all. A library file becomes reachable only because the user wrote
`import "@typespec/openapi";` in that file, and only that package's entry point (plus what *it*
imports) is followed. Nothing is enumerated, nothing is word-indexed, no cap is consumed.

Concretely, in the owner's own project (`ph-cdm`, 60 `.tsp` under `node_modules`): the 36
`@typespec/protobuf` test fixtures that caused the hang are **not** reachable — `@typespec/protobuf`
has exactly one non-test `.tsp` (`lib/proto.tsp`, its `tspMain`), and that file's only import is a
`.js` file, which is skipped. The pathology ADR 0008 diagnosed cannot recur through this door,
because this door does not enumerate.

### D2 — Specifier resolution, matching upstream

Verified against the TypeSpec compiler shipped in the owner's project
(`node_modules/@typespec/compiler/dist/src/core/entrypoint-resolution.js` and
`core/source-loader.js`), not from memory:

**Bare specifier (`@scope/pkg`, `pkg`, or `@scope/pkg/sub/path`):**

1. Walk **up** from the importing file's directory, checking `<dir>/node_modules/<specifier>`.
   (Mandatory: `ph-cdm` is an npm workspaces monorepo — the sources live in `model/*` and the
   only `node_modules` is at the repo root, four levels up from some importing files.)
2. In the package directory, pick the entry point in upstream's order:
   `exports["."]` under the `"typespec"` condition → `tspMain` → `main` → `main.tsp`.
   Upstream: `resolveTypeSpecEntrypointForDir` tries `exports["."]` with `conditions: ["typespec"]`,
   falls through to `resolveTspMain(pkg)`, then `resolvePath(dir, "main.tsp")`; `source-loader.js`
   passes `resolveMain(pkg) { return resolveTspMain(pkg) ?? pkg.main }` and
   `directoryIndexFiles: ["main.tsp", "index.mjs", "index.js"]`.
3. A trailing subpath (`@typespec/http/experimental`) is resolved relative to the package
   directory. Deliberate simplification: real `exports` subpath maps are **not** implemented, only
   `exports["."]`. No package in the owner's tree needs more.

Measured against the six real packages under
`/Users/KHODIAKOVA/IdeaProjects/puenktlichhansa/ph-cdm/node_modules/@typespec/` — and note that a
naive `<pkg>/lib/main.tsp` guess would be **wrong for two of six**:

| Package | `tspMain` | `exports["."].typespec` |
|---|---|---|
| `@typespec/openapi` | `lib/main.tsp` | `./lib/main.tsp` |
| `@typespec/compiler` | **`lib/std/main.tsp`** | `./lib/std/main.tsp` |
| `@typespec/http` | `lib/main.tsp` | — |
| `@typespec/openapi3` | `lib/main.tsp` | — |
| `@typespec/json-schema` | `lib/main.tsp` | — |
| `@typespec/protobuf` | **`lib/proto.tsp`** | — |

**Relative specifier (`./x`, `../x`):** unchanged in spirit from what ships, and now sharing D2's
directory branch — a path that resolves to a directory uses the *same* entry-point rule, not a
hardcoded `main.tsp`, because a relative import can point at a workspace package directory
(`import "../shared"` where `model/shared` has its own `package.json`). Upstream does the same:
`importPath` → `loadDirectory` → `resolveTypeSpecEntrypointForDir`.

**Non-`.tsp` targets are skipped, silently and on purpose.** Every real library entry point starts
with `import "../dist/src/tsp-index.js";`. That is a JS decorator implementation; it is not a
navigation target for this plugin and it is not an error.

### D3 — Reading `package.json` without a third `<depends>`

The bundled JSON plugin would be a third `<depends>` (`plugin.xml` says a third needs a new ADR)
and no JSON parser was found on the CE distribution's own classpath (checked
`ideaIC-2025.2.6.3`: no Gson, no `kotlinx-serialization-json`, no Jackson in `lib/`).
**Decision: bundle a small JSON parser into the plugin's own jar** via a Gradle `implementation`
dependency. This adds no `<depends>` and no runtime requirement on the user's machine; the plugin
classloader serves it. Hand-rolling a JSON parser for three fields is rejected as a needless
correctness liability inside `package.json` files we do not control.

*This is the one item in this ADR that `verifyPlugin` must confirm rather than the architect*, and
plan 05 M5.6a's "done when" includes it.

### D4 — Symlink canonicalisation

`ph-cdm/node_modules` contains workspace symlinks (`cdm-shared` → `model/shared`). Resolving
through one yields a `VirtualFile` whose path is under `node_modules`, i.e. a **second** PSI file
for source the project already has — duplicated resolve targets, and a navigation jump that lands
the user in a path that looks vendored. Import resolution therefore canonicalises the resolved
`VirtualFile` before handing it to `PsiManager`, collapsing a symlinked workspace package back to
its real project path.

### D5 — Unresolved is silent

An import whose target does not exist (typo, or `node_modules` simply not installed) resolves to
nothing and the reference is **soft**. No red, no error, matching
[ADR 0004](0004-reference-resolution-approach.md) D3 and `TypeSpecReference.isSoft() = true`.

Note the plugin has no unresolved-reference `HighlightVisitor`, so "hard" would be inert today
anyway — this decision is about not painting a file red the first time someone opens a checkout
before `npm install`. A visible, opt-in *inspection* for a broken **relative** import (where a
missing file is unambiguously a mistake, unlike a missing library) is a reasonable later
milestone and is listed in plan 05 as optional.

## Consequences

- **Gaps 1 and 2 interlock, and that is the mechanism, not a coincidence.** Once
  `import "@typespec/openapi"` resolves, `openapi/lib/main.tsp` and (via its relative import)
  `openapi/lib/decorators.tsp` enter that file's tier B transitive closure. `decorators.tsp`
  declares `namespace TypeSpec.OpenAPI;` and `extern dec info(...)`. So Cmd-click on
  `@TypeSpec.OpenAPI.info` resolves **through tier B**, with `tspScope` untouched. The answer to
  "what happens when the user then Cmd-clicks a symbol declared in that library file" is: it
  works, for symbols in libraries the file actually imports, and only those.
- **Asymmetry, stated plainly and accepted:** a library declaration is reachable *along an import
  edge* but not *by project-wide search*. Two consequences the owner should expect. (a) Cmd-click
  inside a library file itself is weaker than inside project files — tiers A and B still work
  there, tier C never will. (b) A merged-namespace declaration that lives in a library and is used
  in a file that does **not** import that library stays unresolved. Both are the intended shape:
  ADR 0004 F4's merged-namespace case is about the owner's own multi-file namespaces.
- **Closure cost is bounded and small.** `TypeSpecImportGraph.CLOSURE_CAP` (200) already bounds
  this. Real library closures measured above are single digits: `@typespec/openapi` → 2 files,
  `@typespec/compiler` → 5, `@typespec/protobuf` → 1. The cap needs no change.
- **[ADR 0008](0008-tier-c-file-cap.md) is unaffected and this work must not be blocked on it.**
  Nothing here raises or lowers `TIER_C_FILE_CAP`, and nothing here adds candidates to tier C's
  pool. One forward constraint follows though: **when the stub index (ADR 0008 option C / M6.5) is
  built, it must inherit the `node_modules` exclusion.** An index built over an unfiltered project
  scope would silently re-create ADR 0008's pathology with a bigger blast radius, and would make
  Go To Symbol offer a dependency's vendored test fixtures.

## Open questions — owner's call, not the architect's

1. **Implicit standard library.** The TypeSpec compiler always loads `@typespec/compiler`'s std
   library, whether or not anything imports it. That is why `@doc`, `@key`, `string` and `int32`
   are unresolved today and will stay unresolved after this work: no file imports them. Making
   them resolve means synthesising an implicit import edge to `@typespec/compiler` in every file
   — behaviourally right, matching the compiler, and it would make the *majority* of real
   decorators navigate. It also puts 5 more files in every file's closure, and needs a decision on
   what to do when `@typespec/compiler` is not installed. **Recommend doing it, as a separate
   opt-in milestone after the reported gaps are closed.**
2. **`tspconfig.yaml`'s `entrypoint`.** Upstream's directory resolution consults the project
   config before `package.json`. Not implemented; no file in the owner's tree uses it.
