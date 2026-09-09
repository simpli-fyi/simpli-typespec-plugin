package simpli.fyi.plugins.typespec.stubs

/**
 * One version constant for the whole `.tsp` stub tree (ADR 0011 D6, plan 06 M6.5a) — referenced
 * by both [TypeSpecFileElementType.getStubVersion] and, from M6.5b onward, the name index
 * extension's own `getVersion()`. There is deliberately only one number for the whole stub
 * shape; per-stub-class versioning would let the file stub and the declaration stubs drift out
 * of sync with each other, which is worse than a single shared number that is occasionally
 * bumped one release too many.
 *
 * **Bump this — in the same commit as the change — for ANY of:**
 *
 * 1. a stub class ([TypeSpecDeclStub]/[TypeSpecFileStub]) gains, loses or reorders a serialised
 *    field, or changes a field's encoding;
 * 2. the set of stubbed element types changes (a `TypeSpec.bnf` rule gains or loses
 *    `stubClass=`);
 * 3. any external ID changes — [TypeSpecStubTypes]' `externalIdPrefix` (plugin.xml), a field
 *    name inside [TypeSpecStubTypes] itself, or [TypeSpecFileElementType.getExternalId];
 * 4. [TypeSpecDeclStubElementType.shouldCreateStub], [TypeSpecStubBuilder]'s
 *    `skipChildProcessingWhenBuildingStubs`, or [TypeSpecFileElementType.shouldBuildStubFor]
 *    changes which nodes or which files get stubs — **including the `node_modules` predicate**
 *    in [TypeSpecNodeModules];
 * 5. the value of anything stored in a stub changes meaning: backtick stripping, the dotted
 *    namespace-path computation, name normalisation;
 * 6. a grammar change alters the shape of a stubbed rule's subtree such that a stub built by the
 *    old code no longer describes the new PSI.
 *
 * **Not a bump:** resolver-only changes, query-side filtering, scope changes that do not affect
 * `shouldBuildStubFor`, KDoc.
 *
 * The failure mode this list exists to prevent is asymmetric and nasty: **forgetting a bump does
 * not fail the build or any test** — the test suite always re-indexes from scratch — it corrupts
 * a *user's* on-disk index and surfaces months later as randomly broken resolution that a
 * restart does not fix. Over-bumping only costs one re-index. When in doubt, bump.
 */
object TypeSpecStubVersion {
    const val VERSION: Int = 1
}
