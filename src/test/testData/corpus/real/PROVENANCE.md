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

- Source tree: 24 first-party `.tsp` files (private repository, read-only,
  never committed) as of the 2026-09-03 re-sync (23 at the original
  2026-09-02 sync).
- Anonymised: 2026-09-02, via `anonymise.py --verify` (failure-profile
  self-check passed — see `git log` for this commit's tool output).
  Re-synced 2026-09-03 (found the member-directive/decorator grammar defect
  fixed in `4c4c191`; added directive-name/package-specifier-word handling
  to `anonymise.py`, see `DIRECTIVE_NAMES`/`PACKAGE_SPECIFIER_WORDS`).
- Re-sync: see `../ANONYMISATION.md` § "Re-sync procedure".

## Staleness guard (added 2026-09-03)

`corpus/real`'s content can drift from what was actually verified at the
last real re-sync — a hand-edit, a partial re-sync, or simply forgetting to
run `anonymise.py` after this file's own count/date lines were last true —
and nothing before this caught that silently. `TypeSpecCorpusTest` recomputes
the block below (file count + SHA-256 over sorted `relPath\ncontent\n` for
every `corpus/real/**.tsp` file, `content_fingerprint` in `anonymise.py`) on
every `./gradlew test` run and fails loudly, with the actual vs. recorded
values, if it does not match. It hashes only this already-anonymised,
already-committed content — nothing source-side.

After any change to `corpus/real` (a real re-sync OR a hand edit to a
fixture), regenerate this block in the same commit:

```
python3 tools/corpus-sync/anonymise.py --print-fingerprint src/test/testData/corpus/real
```

```corpus-fingerprint
files: 24
sha256: 5508a187af209f0a49388831945a5de263826fef457e2bf064708fa1d92a2e3c
```
