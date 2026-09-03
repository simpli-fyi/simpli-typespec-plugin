# ADR 0008 — Tier C's 50-file cap: an open UX question for the owner

- **Status:** **OPEN — awaiting an owner decision.** The current behaviour is shipped and
  correct; what is undecided is whether it is the behaviour we want to keep.
- **Date:** 2026-09-03
- **Decided by:** nobody yet. `tsp-architect` and `tsp-tester` concur on the analysis below
  and explicitly decline to choose the outcome.
- **Amends:** [ADR 0004](0004-reference-resolution-approach.md) D2 (tier C degradation),
  and picks up [plan 02](../plans/02-navigation.md) §Risks/3, which predicted this exactly.
- **Context:** shipped in M5.5b (`5fea9ef`), tested in `bfc0b9b`. Tree at plan-04 close:
  202 tests, 0 failures.

---

## What is shipped

`simpli.fyi.plugins.typespec.resolve.TypeSpecSearchScopes`:

```kotlin
const val TIER_C_FILE_CAP = 50
```

Tier C is the last resort of the resolver: when tiers A (same file) and B (transitive
`import` graph) do not find a declaration, tier C widens to the whole project. It does not
scan the project — it asks the platform word index for the `.tsp` files whose text literally
contains the identifier (`CacheManager.getVirtualFilesWithWord`, `UsageSearchContext.ANY`),
and parses only those.

Two cliffs return `null`, which the resolver treats as **unresolved**:

1. **Dumb mode.** The index is unavailable during a re-index; resolution stops.
2. **More than `TIER_C_FILE_CAP` candidate files.** Nothing is parsed.

Both cliffs are tested. The degradation is **clean, never partial**: the resolver never
parses a truncated subset and returns a plausible-but-arbitrary subset of targets. That
design choice is right and is not in question — a silently partial answer is worse than a
clean miss, because the user cannot tell a partial answer from a complete one.

## The problem

The cap fires precisely on the names tier C exists to serve.

Tier C's whole purpose is the *merged namespace* case (ADR 0004 F4): a declaration in a file
the current file neither contains nor transitively imports. In a real TypeSpec project, the
identifiers that live in merged namespaces and get referenced from unrelated files are the
shared vocabulary — `Common`, `Shared`, `Id`, `Error`, `Response`, `Name`, `Status`. Those
are also the identifiers most likely to appear as a *word* in more than fifty `.tsp` files,
because the word index matches the token anywhere in the file text: in a comment, in a
string, in an unrelated `ErrorResponse`-shaped identifier. Fifty files is not a large
TypeSpec project.

So the cap is anti-correlated with usefulness: rare names resolve, common names do not, and
common names are the ones users Cmd-click.

Worse, **the degradation is silent**. When Cmd-click does nothing, the user cannot
distinguish:

- "I mistyped this name / it genuinely does not exist" — the correct no-op, from
- "this resolved through tier C yesterday and hit the cap today because the project grew".

There is no log line, no status-bar message, no tooltip. From the user's side the two are
identical, and the second one looks like the plugin is broken intermittently.

`tsp-tester` raised this and the architect agrees. It is a genuine tradeoff, not a defect:
the cap is a defensible EDT-safety measure. Parsing an unbounded number of files inside a
resolve that may run on the EDT is the failure mode the cap prevents, and that failure mode
is much worse than a missed jump — it freezes the IDE.

## Options for the owner

Not ranked. All three are compatible with each other.

### A — Raise the cap

Cheapest. Change one constant. Buys headroom on medium projects; does not change the shape
of the problem, only where the cliff sits, and it moves the EDT-freeze risk closer.
Before choosing a number, measure: parse cost per `.tsp` file × candidate count, on a real
project, on the EDT budget. Do not pick a number by feel — the current 50 was itself a
conservative guess, not a measurement.

### B — Make the degradation visible

Cheap and independent of A. When the cap trips, leave a breadcrumb: a `Logger` warn line at
minimum, ideally something the user actually sees (a status-bar hint, or a resolve-time
notification throttled to once per session). This does not make navigation work; it makes
the failure *legible*, which is the part users are actually complaining about when a feature
"randomly stops working". Cost: care with throttling, and no notification on the EDT hot
path.

### C — Build the stub index (M6.5)

Removes the ceiling rather than moving it. A stub index answers "which files declare a
model named `Response`?" directly, without parsing candidates and without a word-frequency
prefilter, so there is nothing to cap. This is what [ADR 0004](0004-reference-resolution-approach.md)
D2 always said tier C was a stand-in for, and what [plan 02](../plans/02-navigation.md)
§Risks/3 named as this problem's "concrete trigger". Most expensive; also unlocks Go To
Symbol, which the same index serves. Pulling M6.5 forward is a real option.

## Recommendation the architect is willing to make

**B unconditionally and immediately** (it is a small change and it costs nothing to be
honest with the user), then **C on its own schedule**. A is a stopgap that is only worth
doing if it is measured rather than guessed.

But the sequencing question — is Go To Symbol + stub index worth pulling ahead of M6's
structure view / folding / completion? — is a product-priority call, and that belongs to
the owner.

## Consequences of leaving it as-is

Acceptable in the short term. Navigation is correct when it answers and silent when it does
not; nothing is *wrong*, and no user data or IDE stability is at risk. The cost is a
feature that appears unreliable on exactly the projects big enough to need it. Do not let
"it is tested and it degrades cleanly" be read as "it is finished".
