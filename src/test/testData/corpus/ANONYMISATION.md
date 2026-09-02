# corpus/real — anonymisation rules and rename map

Single source of truth for `tools/corpus-sync/anonymise.py`. See
[ADR 0007](../../../../docs/adr/0007-corpus-driven-grammar-acceptance.md) D4/D8
and [plan 04 §M6a](../../../../docs/plans/04-grammar-corrections.md) for the
decision this implements.

`corpus/real/**` is derived from a private production TypeSpec repository
(never vendored verbatim, never committed itself). This file records exactly
how the derivation works so it is re-runnable and auditable.

## Rules (plan 04 §M6a, verbatim intent)

1. **Preserve, byte for byte:** every keyword, punctuation token, decorator
   *name* (`@doc`, `@field`, `@@package`), built-in scalar (`string`,
   `int32`, `utcDateTime`), stdlib type reference (`Record`,
   `TypeSpec.Protobuf.Extern`), comment/whitespace layout, line structure,
   and the *shape* of every construct: dotted-namespace segment counts,
   template parameter/argument counts, optionality markers, member
   separators, decorator ordering, string-literal *kind* (`"…"` vs
   `"""…"""`) and line count.
2. **Rename** only: user-declared namespace segments, model/enum/union/
   interface/alias/scalar/op names, property/member/parameter names, and the
   *contents* of user-authored string literals.
3. **The rename map is injective and category-preserving** (ADR 0007 D4.2).
   One-to-one; a rename never changes a name's category (identifier stays
   identifier, backticked stays backticked, dotted name keeps its segment
   count, kebab-case stays kebab-case).
4. **String literal contents** are replaced with same-shape filler:
   map-covered words (case-insensitive) use the same target as the
   identifier map; other prose words of length ≥ 4 get a deterministic
   filler word; everything shorter (regex character classes, short codes)
   is left untouched. Escape sequences and multi-line-ness are always
   preserved exactly (only alphabetic runs are ever substituted).
5. **Comments** are replaced with generic filler of the same line count:
   `// (comment)` for line comments, `(comment)` runs for `/* … */` blocks,
   `* (doc)` runs inside `/** … */` — which keeps its doc-comment *form*.
6. **File and directory names** are renamed through the same map
   (kebab-case-aware).
7. **Forbidden:** reformatting, sorting, deduplicating, "tidying", or
   dropping any declaration. If a construct appears five times in the
   source, it appears five times in `corpus/real/`.

## Reserved words (never renamed, never trigger a hard failure)

Grammar keywords (from `_TypeSpecLexer.flex`), builtin scalar types, the
library/namespace names actually referenced (`TypeSpec`, `Protobuf`,
`JsonSchema`, `Timestamp`, `WellKnown`), and the standard decorator/parameter
names actually used (`doc`, `example`, `pattern`, `minLength`, `maxLength`,
`key`, `field`, `package`, `service`, `title`, `friendlyName`, `name`,
`scope`, `jsonSchema`). The full list lives in `anonymise.py`'s `ALLOWLIST`
— duplicating it here would only let the two drift.

## Rename map — keyed by hash, not by source name

**The map below is keyed by `name_hash(source)` (sha256 of the exact
source-side spelling, first 12 hex characters) — never by the plaintext
source name.** This is not an implementation nicety, it is the point of the
exercise: a committed file that reads `<source> -> <target>` in plaintext
publishes exactly the domain vocabulary the anonymisation exists to protect
— the owner's internal model, event and field names appear nowhere else in
this repository, so a plaintext map would be a dictionary equivalent to the
schema it was supposed to hide, even though the anonymised `.tsp` files
themselves no longer contain it. Per ADR 0007 D4 / plan 04 §M6a: "the rename
map lives in the repo, keyed by *target* name with a stable hash of the
source name — never the source name itself."

A source name encountered while walking the upstream tree that hashes to
something **not** in this map, and that is not one of the reserved words
above, is a **hard failure** in `anonymise.py` — the failure output names
only `name_hash(name)`, never the name itself, so an unattended re-sync can
never publish an unvetted domain term (not even into a CI log).

**Adding a new mapping:** run `tools/corpus-sync/anonymise.py --hash <name>`
to compute the same hash the tool's hard-failure path prints, choose a
target name (injective, category-preserving — rule 3), and add a line below.
The plaintext source name lives only in your terminal history / the
private upstream repository, never in a file this repository tracks.

Because the map is hash-keyed, it cannot do case-insensitive matching at
lookup time the way a plaintext map could (there is no plaintext key left to
lower-case and compare). `anonymise.py` compensates two ways: (1) it tries a
handful of common casing variants of whatever word it is looking up
(as-is, lower, upper, capitalised) before falling back to filler, and (2)
this map explicitly carries a row for **every exact casing of a term that
actually occurs** in the source tree — a PascalCase code identifier
(`namespace ...Foo;`), its all-lowercase appearance in a dotted string or a
kebab-case filename (`foo.tsp`, `"...foo..."`), and any ALL-CAPS occurrence
in prose, each get their own hash row, all pointing at the same target (the
same concept, several literal spellings). This is why the map below has more
rows than there are distinct concepts — that is expected, not a duplication
bug. Injectivity (rule 3) is checked at the *concept* level when the map is
authored/extended, not mechanically from the hash rows (see `load_map`'s
docstring in `anonymise.py`).

```rename-map
852fe9ec16a1 -> Acme
b456e6d5ae43 -> Booking
c1b70b9fbfd4 -> BookingCancelled
5cfb648929c0 -> BookingCancelledDetails
2d838ad0d805 -> BookingChangeGroup
d02fc192442f -> BookingCreated
3a4037f7a72a -> BookingCreatedDetails
925d73e20e75 -> BookingId
2a9ab80e4aee -> BookingRelevant
e9d2e58032ef -> BookingRelevantSet
7fee43cdee15 -> BookingSyncSignal
f741a5cac602 -> CORE
e74aebc37032 -> CREATED
addbde73cc84 -> CarrierBooking
72a5c4018145 -> CloudSignalDetails
229422957c70 -> CloudSignals
e3c4b39d6d50 -> Common
fd3f626ecec3 -> Core
7a0db47f3d0b -> CoreBusinessSignal
bccd372eabd9 -> CoreChangeGroup
e78d587ce8db -> CoreDate
76d04d549070 -> CoreInstant
f809433fef85 -> CoreSignal
dd0bc878baca -> CoreSignals
fd1331d7a6cd -> Created
afcc3bc93518 -> EX
4445ac515530 -> Ex
5c8f075f7421 -> FeedFault
f0ca7f1c3c20 -> FeedFaultSignal
320b5d80f8cf -> Group
d839f0fdd5d9 -> Journey
e29b856a0025 -> JourneyChangeGroup
5939def34c3b -> JourneyNumber
235a52a6cd81 -> JourneyRelevantSet
0f11dd726871 -> JourneySyncSignal
a90bab6ff81b -> KIND
baaddf70fb5d -> Kind
c97c29c7a71b -> PAYLOADDATA
cec3a9b89b2e -> PayloadData
82601d1f2649 -> REL
005f543ec63f -> SYNC
8d14f6e72de8 -> Signals
119eceae4158 -> SlotNumber
4d382a92f23c -> StationCode
f7d1eaf4682f -> TRel
5915ad2affc7 -> TSync
68971283841a -> TopicSubject
c732e9cb18f0 -> TravelerId
e632b7095b0b -> U
8ab6812a66e4 -> VOIDED
d30c61304e8e -> VehicleType
c6f986974e6f -> acme
9efa077f044e -> booking
040cb1bf9815 -> bookingId
8fcfccf675b1 -> cause
3cd81d346c57 -> chainId
18a0dfa3689a -> cloudsignals
a4d26868017c -> common
55085deadcfa -> core
2941728c0d38 -> corePayload
323748f86a76 -> capturedAt
6796883fecf6 -> created
e64c826b6b33 -> cs
6ffc1fcbe3e4 -> destinationStation
4f9ed95d8bf7 -> detailNote
1e08d7cc63d1 -> detailPhase
88b3e13a9890 -> ex
4a41a7e28a09 -> faultCause
3ebd933aca76 -> faultNote
2b8520409faa -> faultTrace
0da778911dd8 -> faultType
dfb031335e28 -> feedProduct
844015349c64 -> feedProductIdentifierName
1384b5cef9b8 -> feedProductIdentifierType
a878411de821 -> feedProductIdentifierValue
4bb24efc9641 -> group
336074805fc8 -> instant
03b6b3e9163d -> journey
ac2c2d798758 -> journeyDate
ddc6e5cea6ea -> journeyNumber
1303c06b0b01 -> kind
41cf6794ba42 -> origin
b2138a36590d -> originChannel
9890b098defa -> originNotePayload
ae112a309131 -> originNoteSource
a2c84b1c2629 -> originNoteTimestamp
e43af761b073 -> originStation
b862d15242a0 -> originatorId
3a6eb0790f39 -> payloadData
b3b739db43c1 -> payloadcontenttype
92a42bac0585 -> payloadschema
9a88a82561a3 -> plannedLanding
6868d0d51b61 -> plannedStart
36c68ee28432 -> rel
0829103205fb -> retiredAt
862417b9e7c3 -> signals
5d2c13d6f9fa -> slot
5f521e332e3e -> specrevision
cc41efabb0ca -> stagePhase
7c7412331edf -> startDate
073c1634c496 -> state
e6f56c4c2504 -> supersededBy
9fc78162dd90 -> sync
a9491f4c1bf7 -> topicSubject
cb6c363a3b53 -> travelerId
a56145270ce6 -> uid
c9b358039139 -> vehicleType
8b47045eb7b8 -> voided
```

## Re-sync procedure

```bash
# 1. Compute the hash for each new source-side identifier you encounter
#    (never write the identifier itself into this file):
tools/corpus-sync/anonymise.py --hash <name>

# 2. Add a `<hash> -> <target>` line above by hand, picking an injective,
#    category-preserving target (rule 3).

# 3. Re-run the sync:
tools/corpus-sync/anonymise.py \
    --source /path/to/upstream-repo/model \
    --map    src/test/testData/corpus/ANONYMISATION.md \
    --dest   src/test/testData/corpus/real \
    --verify
```

`--verify` re-derives the construct-category fingerprint (regex-based —
this is a standalone Python tool, it has no access to the JVM parser) over
both trees and refuses to write anything on a mismatch. `git diff` on
`corpus/real/` after a re-sync must be reviewed by a human before commit.
The stdlib half of the corpus (`corpus/stdlib/`) re-syncs by straight copy
from `node_modules/@typespec/*/**.tsp` — no anonymisation, MIT-licensed,
`PROVENANCE.md` versions updated in the same commit.
