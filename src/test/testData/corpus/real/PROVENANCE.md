# corpus/real — provenance

Derived from a private production TypeSpec repository via
`tools/corpus-sync/anonymise.py`. See
[`../ANONYMISATION.md`](../ANONYMISATION.md) for the full rename map and
rule set that produced these files.

**No original names retained.** Every namespace segment, declared type/
member/parameter name, file/directory name and user-authored string-literal
content has been passed through the injective, category-preserving rename
map in `ANONYMISATION.md`; comments have been replaced with generic filler
of the same line count. Only syntactic *shape* (dotted-namespace segment
counts, template argument counts, decorator/member ordering, separator
choice, string-literal kind) is preserved — that shape is the entire test
value of this corpus (ADR 0007 D4.2).

- Source tree: 23 first-party `.tsp` files (private repository, read-only,
  never committed).
- Anonymised: 2026-09-02, via `anonymise.py --verify` (failure-profile
  self-check passed — see `git log` for this commit's tool output).
- Re-sync: see `../ANONYMISATION.md` § "Re-sync procedure".
