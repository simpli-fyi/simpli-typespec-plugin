# corpus/stdlib — provenance

Vendored **verbatim**, MIT-licensed (see `LICENSE`, copied unmodified from
`@typespec/compiler/LICENSE`). No content was altered; directory structure
mirrors each package's path under `node_modules/@typespec/`.

Sourced from a private repository's `node_modules` (dependency tree, not the
repository's own code — these are third-party library sources, ADR 0007 D4).

| Package | Version |
|---|---|
| `@typespec/compiler` | 1.15.0 |
| `@typespec/http` | 1.13.0 |
| `@typespec/json-schema` | 1.15.0 |
| `@typespec/openapi` | 1.13.0 |
| `@typespec/openapi3` | 1.13.0 |
| `@typespec/protobuf` | 0.83.0 |

`tsp` compiler version (via `@typespec/compiler`): **1.15.0**.

Sync date: 2026-09-02. 60 `.tsp` files vendored (library sources under each
package's `lib/`, plus `@typespec/protobuf`'s `test/scenarios/**/input/*.tsp`
fixtures and `@typespec/compiler`'s `templates/**` snapshot/scaffold files —
all `.tsp` files found under `node_modules/@typespec/**`, no filtering).

Re-sync: straight copy from `node_modules/@typespec/*/**.tsp` — see
`../ANONYMISATION.md` § "Re-sync procedure". Update this table's versions in
the same commit as any re-sync.

## Note on the ADR 0007 audit's file count

The audit that produced [ADR 0007](../../../../docs/adr/0007-corpus-driven-grammar-acceptance.md)
recorded 83 stdlib `.tsp` files (106 total with the 23 first-party files).
Re-measuring against the same private-repository checkout for this milestone found 60
stdlib files (83 total). The discrepancy is environmental (the audit's
`node_modules` install state at the time is not reproducible from this
checkout — no `@typespec/versioning`, `@typespec/rest`, etc. are present
here) and does not change M6a's scope: this corpus vendors what is actually
present now, and `BASELINE.txt` records the failure profile measured against
*this* vendored set, not the audit's original count.
