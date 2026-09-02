#!/usr/bin/env python3
"""Re-sync tool for src/test/testData/corpus/real/ (ADR 0007 D4, plan 04 §M6a).

Reads first-party .tsp files from a real TypeSpec repository (never committed,
never modified) and writes a domain-anonymised copy into this repository's
vendored corpus. See src/test/testData/corpus/ANONYMISATION.md for the
seven preservation/rename rules this script implements and the rename map
itself (the single source of truth; this script only *applies* it).

Usage:
    anonymise.py --source /path/to/upstream-repo --map src/test/testData/corpus/ANONYMISATION.md \
        --dest src/test/testData/corpus/real --verify

--verify re-derives a structural fingerprint (construct-category counts, per
ADR 0007 D4.2) for both the source tree and the freshly-written dest tree and
refuses to write anything if they differ -- an anonymisation that changes the
failure profile has destroyed test value.

The map file (ANONYMISATION.md) is keyed by HASH, never by the plaintext
source name -- see `load_map` below. A source-side identifier that hashes to
something not covered by the map, and is not one of the grammar's reserved
words (KEYWORDS / BUILTIN_SCALARS below), is a HARD FAILURE. The failure
message prints only `name_hash(offending_name)`, never the name itself --
this is what stops an unattended re-sync from publishing a new domain term
into a committed file or CI log. Use `--hash <name>` to compute the same
hash by hand when adding a new mapping (see `main` below).
"""
from __future__ import annotations

import argparse
import hashlib
import re
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Reserved words: never renamed, never trigger a hard failure. Sourced from
# _TypeSpecLexer.flex's keyword list plus the builtin scalar / library names
# and standard decorator parameter names actually used in the corpus. Kept
# deliberately broader than the current corpus so a routine re-sync (a new
# property using an existing builtin, e.g. `int32`) doesn't spuriously fail.
#
# This allowlist is consulted for BOTH code identifiers (`rename_identifier`)
# AND prose/string words (`transform_prose_word`) -- see the note there. A
# word exempted here because it is a public stdlib/grammar term (e.g.
# `Timestamp`, from `WellKnown.Timestamp`) must be exempted the same way in
# every context that exact spelling can appear, or the same source-side word
# ends up preserved verbatim in one place and hashed-and-renamed in another --
# which is itself a leak, because the untouched occurrence then reveals what
# the renamed one's map entry stands for.
# ---------------------------------------------------------------------------
KEYWORDS = {
    "import", "model", "scalar", "namespace", "interface", "union", "if",
    "else", "projection", "using", "op", "extends", "is", "enum", "alias",
    "dec", "fn", "valueof", "typeof", "const", "init", "true", "false",
    "return", "void", "never", "unknown", "extern", "auto", "internal",
    "statemachine", "macro", "package", "metadata", "env", "arg", "declare",
    "array", "struct",
}

BUILTIN_SCALARS = {
    "string", "boolean", "bytes", "numeric", "integer", "float", "int8",
    "int16", "int32", "int64", "uint8", "uint16", "uint32", "uint64",
    "safeint", "float32", "float64", "decimal", "decimal128", "plainDate",
    "plainTime", "utcDateTime", "offsetDateTime", "duration", "url", "null",
}

LIBRARY_NAMES = {
    "TypeSpec", "Protobuf", "JsonSchema", "Timestamp", "WellKnown", "Http",
    "OpenAPI", "OpenAPI3", "Rest", "Versioning",
}

DECORATOR_NAMES = {
    "doc", "example", "pattern", "minLength", "maxLength", "minValue",
    "maxValue", "key", "field", "package", "service", "title",
    "friendlyName", "name", "scope", "jsonSchema", "summary", "format",
    "visibility", "encode", "encodedName", "discriminator",
}

ALLOWLIST = KEYWORDS | BUILTIN_SCALARS | LIBRARY_NAMES | DECORATOR_NAMES

IDENTIFIER_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")

FILLER_WORDS = [
    "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel",
    "india", "juliet", "kilo", "lima", "mike", "november", "oscar", "papa",
    "quebec", "romeo", "sierra", "tango", "uniform", "victor", "whiskey",
    "amber", "birch", "cedar", "dune", "ember", "fern", "glacier", "harbor",
    "ivory", "jade", "knoll", "lagoon", "meadow", "onyx", "pebble", "quarry",
    "ridge", "slate", "terrace", "umber", "vale", "willow", "yield",
]


def name_hash(name: str) -> str:
    return hashlib.sha256(name.encode("utf-8")).hexdigest()[:12]


def filler_for(word_lower: str) -> str:
    idx = int(hashlib.sha256(word_lower.encode("utf-8")).hexdigest(), 16) % len(FILLER_WORDS)
    return FILLER_WORDS[idx]


def apply_case(template: str, replacement: str) -> str:
    if template.isupper():
        return replacement.upper()
    if template.islower():
        return replacement.lower()
    if template[0].isupper():
        return replacement[0].upper() + replacement[1:]
    return replacement[0].lower() + replacement[1:]


# ---------------------------------------------------------------------------
# Rename map I/O -- ANONYMISATION.md holds a fenced ```rename-map block of
# `<hash> -> <target>` lines, where `<hash>` is `name_hash(source)` (sha256,
# first 12 hex chars) of the EXACT source-side spelling. This is the single
# source of truth (rule 3) and it is deliberately NOT keyed by the plaintext
# source name -- ADR 0007 D4/plan 04 §M6a: "the rename map lives in the repo
# ... keyed by *target* name with a stable hash of the source name -- never
# the source name itself." A committed map that reads `Foo -> Bar` in
# plaintext is a dictionary of the owner's domain vocabulary; `<hash of "Foo">
# -> Bar` is not.
# ---------------------------------------------------------------------------
MAP_LINE_RE = re.compile(r"^([0-9a-f]{12})\s*->\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?:#.*)?$")


def load_map(map_path: Path) -> dict[str, str]:
    """Returns `{hash(source): target}`. The plaintext source name is never
    recovered from this file -- lookups hash the *candidate* identifier
    encountered while walking the source tree and check membership."""
    text = map_path.read_text(encoding="utf-8")
    m = re.search(r"```rename-map\n(.*?)\n```", text, re.S)
    if not m:
        raise SystemExit(f"no ```rename-map fenced block found in {map_path}")
    mapping: dict[str, str] = {}
    for line in m.group(1).splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        lm = MAP_LINE_RE.match(line)
        if not lm:
            raise SystemExit(f"unparseable rename-map line: {line!r}")
        h, tgt = lm.group(1), lm.group(2)
        if h in mapping and mapping[h] != tgt:
            raise SystemExit(f"duplicate/conflicting map entry for hash {h}")
        mapping[h] = tgt
    # Injectivity (rule 3 / ADR 0007 D4.2) is a property of *source concepts*,
    # not of hash rows: one concept legitimately gets several rows here (one
    # per exact casing variant actually seen in the source tree -- a PascalCase
    # code identifier and its lowercase dotted-string/filename spelling both
    # resolve to the same target). Because the map never stores
    # the plaintext source, this file cannot re-derive "which rows are the
    # same concept" to distinguish that from a genuine two-concepts-collapsed
    # violation -- injectivity is verified once, by hand, against the source
    # concept list when the map is authored/extended (see the re-sync
    # procedure below), not re-checked mechanically from hashes alone.
    return mapping


def lookup_target(word: str, rename: dict[str, str]) -> str | None:
    """Case-aware hash lookup: tries the word exactly as encountered, then a
    small set of common casing variants (lower/upper/capitalised), so prose
    mentions of a mapped term (e.g. a lowercase dotted-package string) still
    resolve even though the map only stores one canonical casing per hash.
    No plaintext source name is ever reconstructed -- only hashes of
    candidate strings are computed and checked for membership."""
    for candidate in (word, word.lower(), word.upper(), word.capitalize()):
        h = name_hash(candidate)
        if h in rename:
            return rename[h]
    return None


# ---------------------------------------------------------------------------
# Tokeniser: comment (line / block / doc) / string (regular / triple) / code.
# Order matters: triple-quoted strings and doc comments must be tried before
# their single-character-prefixed siblings.
# ---------------------------------------------------------------------------
SEGMENT_RE = re.compile(
    r'(?P<doc>/\*\*.*?\*/)'
    r'|(?P<block>/\*.*?\*/)'
    r'|(?P<line>//[^\n]*)'
    r'|(?P<triple>"""(?:[^\\]|\\.)*?""")'
    r'|(?P<string>"(?:[^"\\\n]|\\.)*")'
    r'|(?P<code>[^"/]+|/(?![/*])|"(?![^"\\\n]*"))',
    re.S,
)

WORD_RE = re.compile(r"[A-Za-z]+")


def transform_prose_word(word: str, rename: dict[str, str]) -> str:
    if word in ALLOWLIST:
        # Same exact-spelling check `rename_identifier` applies to code. Without
        # it, a reserved word that is ALSO an ordinary English word (e.g.
        # `Timestamp`, both `WellKnown.Timestamp` the stdlib type and a plain
        # prose word) is preserved verbatim as an identifier but renamed via
        # the map wherever it shows up in a string/comment -- and the
        # untouched identifier occurrence then hashes to that same map entry,
        # which is exactly the leak this function exists to prevent.
        return word
    target = lookup_target(word, rename)
    if target is not None:
        return apply_case(word, target)
    if len(word) >= 4:
        return apply_case(word, filler_for(word.lower()))
    return word  # short runs (regex char classes, abbreviations like "RES") untouched


def transform_string_content(content: str, rename: dict[str, str]) -> str:
    return WORD_RE.sub(lambda m: transform_prose_word(m.group(0), rename), content)


def transform_line_comment(text: str) -> str:
    # text includes leading "//"
    return "// (comment)"


def transform_block_comment(text: str) -> str:
    # text includes /* ... */, not a doc comment
    inner = text[2:-2]
    lines = inner.split("\n")
    if len(lines) == 1:
        return "/* (comment) */"
    out = ["/* (comment)"]
    for _ in lines[1:-1]:
        out.append(" (comment)")
    out.append(" */")
    return "\n".join(out)


def transform_doc_comment(text: str) -> str:
    inner = text[3:-2]  # strip leading /** and trailing */
    lines = inner.split("\n")
    if len(lines) == 1:
        return "/** (doc) */"
    out = ["/**"]
    for ln in lines[1:-1]:
        out.append(" * (doc)")
    out.append(" */")
    return "\n".join(out)


def rename_identifier(tok: str, rename: dict[str, str]) -> str:
    if tok in ALLOWLIST:
        return tok
    h = name_hash(tok)
    if h in rename:
        # Verbatim, not apply_case(tok, ...): the map stores one hash row per
        # *exact* casing variant actually seen (see load_map's docstring), and
        # each row's target is already cased correctly for that exact source
        # spelling -- re-deriving casing here would double-apply it and wreck
        # compound camelCase targets (e.g. "subject" -> "topicSubject").
        return rename[h]
    raise HardFailure(tok)


class HardFailure(Exception):
    def __init__(self, source_name: str):
        super().__init__(f"unmapped source-side identifier (hash {name_hash(source_name)}) -- "
                          f"add a mapping to ANONYMISATION.md before re-syncing")
        self.source_name = source_name


def transform_code(text: str, rename: dict[str, str]) -> str:
    return IDENTIFIER_RE.sub(lambda m: rename_identifier(m.group(0), rename), text)


def transform_import_target(content: str, rename: dict[str, str]) -> str:
    """The string argument of an `import "..."` statement is a library
    package specifier (`@typespec/protobuf` -- rule 1, preserve verbatim,
    it is a stdlib reference, not domain content) or a relative `.tsp` file
    path (rule 6, renamed through the same map so the corpus's internal
    imports keep resolving to the files this tool actually wrote)."""
    if content.startswith("@"):
        return content  # library package specifier -- untouched
    parts = content.split("/")
    return "/".join(anonymise_path_segment(p, rename) if p not in ("", ".", "..") else p for p in parts)


IMPORT_TAIL_RE = re.compile(r"import\s*$")


def anonymise_text(text: str, rename: dict[str, str]) -> str:
    out = []
    last_code = ""
    for m in SEGMENT_RE.finditer(text):
        kind = m.lastgroup
        tok = m.group(0)
        if kind == "doc":
            out.append(transform_doc_comment(tok))
        elif kind == "block":
            out.append(transform_block_comment(tok))
        elif kind == "line":
            out.append(transform_line_comment(tok))
        elif kind == "triple":
            inner = tok[3:-3]
            out.append('"""' + transform_string_content(inner, rename) + '"""')
        elif kind == "string":
            inner = tok[1:-1]
            if IMPORT_TAIL_RE.search(last_code):
                out.append('"' + transform_import_target(inner, rename) + '"')
            else:
                out.append('"' + transform_string_content(inner, rename) + '"')
        else:  # code
            transformed = transform_code(tok, rename)
            out.append(transformed)
            last_code = tok
    return "".join(out)


def anonymise_path_segment(segment: str, rename: dict[str, str]) -> str:
    """Rename a file/directory name component, preserving kebab-case shape."""
    def repl(m: re.Match) -> str:
        word = m.group(0)
        if word in ALLOWLIST:
            return word
        target = lookup_target(word, rename)
        if target is not None:
            return apply_case(word, target)
        if len(word) >= 4:
            return apply_case(word, filler_for(word.lower()))
        return word

    return re.sub(r"[A-Za-z]+", repl, segment)


# ---------------------------------------------------------------------------
# Construct-category fingerprint (the "failure-profile self-check", plan 04
# §M6a). Regex-based, deliberately independent of the plugin's real parser
# (this tool is plain Python, no JVM access) -- it exists to prove the
# anonymised tree has the *same shape* as the source tree, row for row.
# ---------------------------------------------------------------------------
CATEGORY_PATTERNS: dict[str, re.Pattern] = {
    "decorator_on_member": re.compile(r"^\s*@[A-Za-z]", re.M),
    "augment_decorator": re.compile(r"@@[A-Za-z]"),
    "brace_decorator_arg": re.compile(r"\(\s*[A-Za-z][\w.]*\s*,\s*\{"),
    "model_is": re.compile(r"\bmodel\s+\w+(<[^>]*>)?\s+is\s+"),
    "model_extends": re.compile(r"\bmodel\s+\w+(<[^>]*>)?\s+extends\s+"),
    "triple_quoted_string": re.compile(r'"""'),
    "comma_member_separator": re.compile(r",\s*\n?\s*\}"),
    "spread_template_arg": re.compile(r"\.\.\.\w+<"),
    "spread_operation_param": re.compile(r"\(\s*\.\.\."),
    "extern_declaration": re.compile(r"\bextern\s+(dec|model|fn)\b"),
    "valueof": re.compile(r"\bvalueof\b"),
    "typeof": re.compile(r"\btypeof\b"),
    "scalar_body": re.compile(r"\bscalar\s+\w+[^;{]*\{"),
    "directive": re.compile(r"#suppress|#deprecated"),
}


def fingerprint_file(text: str) -> dict[str, int]:
    """Construct-category counts over *code only* -- comments and string
    contents are excluded first (via the same tokeniser anonymise_text uses)
    so a construct merely *mentioned in prose* (e.g. "// NO @@package of its
    own") is never confused with an occurrence of the actual construct."""
    code_chunks = []
    triple_count = 0
    for m in SEGMENT_RE.finditer(text):
        if m.lastgroup == "code":
            code_chunks.append(m.group(0))
        elif m.lastgroup == "triple":
            triple_count += 1
    code_text = "\n".join(code_chunks)
    counts: dict[str, int] = {}
    for name, pat in CATEGORY_PATTERNS.items():
        if name == "triple_quoted_string":
            counts[name] = triple_count
        else:
            counts[name] = len(pat.findall(code_text))
    return counts


def fingerprint_tree(root: Path) -> dict[str, int]:
    counts: dict[str, int] = {k: 0 for k in CATEGORY_PATTERNS}
    for f in sorted(root.rglob("*.tsp")):
        if "node_modules" in f.parts:
            continue
        text = f.read_text(encoding="utf-8")
        for name, v in fingerprint_file(text).items():
            counts[name] += v
    return counts


def anonymise_tree(source: Path, dest: Path, rename: dict[str, str]) -> list[Path]:
    written = []
    for src_file in sorted(source.rglob("*.tsp")):
        if "node_modules" in src_file.parts:
            continue
        rel = src_file.relative_to(source)
        new_parts = [anonymise_path_segment(p, rename) for p in rel.parts[:-1]]
        stem = anonymise_path_segment(rel.stem, rename)
        new_name = stem + rel.suffix
        dest_rel = Path(*new_parts, new_name) if new_parts else Path(new_name)
        dest_file = dest / dest_rel
        text = src_file.read_text(encoding="utf-8")
        anonymised = anonymise_text(text, rename)
        dest_file.parent.mkdir(parents=True, exist_ok=True)
        dest_file.write_text(anonymised, encoding="utf-8")
        written.append(dest_file)
    return written


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", type=Path)
    ap.add_argument("--map", type=Path)
    ap.add_argument("--dest", type=Path)
    ap.add_argument("--verify", action="store_true")
    ap.add_argument(
        "--hash",
        metavar="NAME",
        help="print name_hash(NAME) and exit -- use this to add a new mapping to "
             "ANONYMISATION.md's rename-map block by hand without ever writing the "
             "plaintext source name into a committed file. Hash the EXACT casing "
             "you saw in the source tree (this tool looks up a handful of casing "
             "variants at rename time, but the map itself is exact-match).",
    )
    args = ap.parse_args()

    if args.hash is not None:
        print(name_hash(args.hash))
        return 0

    if not (args.source and args.map and args.dest):
        ap.error("--source, --map and --dest are required (unless --hash is given)")

    rename = load_map(args.map)

    if args.verify:
        # write to a scratch copy first, verify, then promote -- never leave
        # a partially-written, unverified corpus/real behind.
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            tmp_dest = Path(tmp) / "real"
            try:
                anonymise_tree(args.source, tmp_dest, rename)
            except HardFailure as e:
                print(f"REFUSING to write: {e}", file=sys.stderr)
                return 1
            source_fp = fingerprint_tree(args.source)
            dest_fp = fingerprint_tree(tmp_dest)
            if source_fp != dest_fp:
                print("REFUSING to write: failure-profile self-check mismatch", file=sys.stderr)
                print(f"{'category':<24}{'source':>8}{'dest':>8}")
                for k in source_fp:
                    marker = "  <-- MISMATCH" if source_fp[k] != dest_fp[k] else ""
                    print(f"{k:<24}{source_fp[k]:>8}{dest_fp[k]:>8}{marker}")
                return 1
            print("Failure-profile self-check: OK (source and anonymised tree match)")
            print(f"{'category':<24}{'count':>8}")
            for k, v in source_fp.items():
                print(f"{k:<24}{v:>8}")
            # promote
            if args.dest.exists():
                import shutil
                for child in args.dest.iterdir():
                    if child.name in ("PROVENANCE.md",):
                        continue
                    if child.is_dir():
                        shutil.rmtree(child)
                    else:
                        child.unlink()
            args.dest.mkdir(parents=True, exist_ok=True)
            import shutil
            for item in tmp_dest.iterdir():
                dst = args.dest / item.name
                if item.is_dir():
                    shutil.copytree(item, dst, dirs_exist_ok=True)
                else:
                    shutil.copy2(item, dst)
        print(f"Wrote anonymised corpus to {args.dest}")
        return 0
    else:
        try:
            written = anonymise_tree(args.source, args.dest, rename)
        except HardFailure as e:
            print(f"FAILED: {e}", file=sys.stderr)
            return 1
        print(f"Wrote {len(written)} files to {args.dest} (no --verify: self-check skipped)")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
