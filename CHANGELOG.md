<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Simpli TypeSpec Highlighter Changelog

## [Unreleased]

### Added

- M5.6d: decorator names now navigate on Cmd-click, per dotted segment — the owner's third
  reported gap (`@TypeSpec.OpenAPI.info(#{ version: "1.5.1" })` did not resolve). The single
  `DECORATOR`/`AUGMENT_DECORATOR` lexer token is untouched; `TypeSpecDecoratorReferenceHost`
  (shared `mixin=` on `decorator_application` and `augment_decorator_statement`) splits the
  token's own text on `.` after dropping the `@`/`@@` prefix and hangs one
  `TypeSpecDecoratorReference` per segment, each resolving via `TypeSpecResolver.multiResolve`
  (plan 06 M6.5c's name-list core). `@TypeSpec.<caret>OpenAPI.info` resolves to the namespace,
  `@TypeSpec.OpenAPI.<caret>info` to the `extern dec info` declaration; both segments and the
  `@@augment` form behave identically. Unresolved decorators (bare std-library ones such as
  `@doc` without an explicit import, or genuinely unknown names) stay soft — no red squiggle.
  Zero lexer, grammar-token, golden or highlighting changes (ADR 0009 option B's central claim,
  confirmed: 256 tests unedited, `verifyPlugin` still Compatible with two `<depends>`). See
  `docs/adr/0009-decorator-reference-strategy.md` and
  `docs/plans/05-import-and-decorator-navigation.md` §M5.6d.

- M6.5c: the resolver's project-wide tier now queries the stub index instead of the word-index
  file-cap prefilter. `TypeSpecSearchScopes.filesContainingWord` and `TIER_C_FILE_CAP` are
  deleted outright — no fallback. `TypeSpecResolver`'s core is now name-list based
  (`resolvePath(names, index, context)`, absorbing plan 05 M5.6c); `resolveSegment` is a thin PSI
  adapter over it, and a new public `multiResolve(names, index, context)` is available for a
  future name-based caller. This fixes the reported bug: a module with `using Shared;` and no
  `import` of the module that declares `namespace Shared;` now resolves both a bare reference
  (`VolumeUnit`) and a qualified one (`Shared.VolumeUnit`), regardless of project size — pinned by
  a 130-plus-file fixture. `node_modules` stays invisible to this tier by construction (ADR 0011
  D4) — verified separately. See `docs/adr/0011-stub-index-replaces-tier-c.md` and
  `docs/plans/06-stub-index.md` §M6.5c.

- M6.5b: a project-wide stub index, `TypeSpecDeclarationNameIndex` (`StubIndexKey`
  `tsp.decl.name`), answers "which declarations are named X" without parsing any candidate file.
  Keyed by the stub's already backtick-stripped simple name; the namespace-qualified question is
  answered by a per-hit comparison against `TypeSpecDeclStub.namespacePath`
  (`TypeSpecStubQueries.declarationsNamed`), never a second index. A `namespace` statement is
  indexed once per dotted segment (`namespace A.B.C;` → findable by `A`, `B` and `C`), mirroring
  `TypeSpecFileDeclarations`'s existing PSI-only rule so the two can never disagree. See
  `docs/adr/0011-stub-index-replaces-tier-c.md` and `docs/plans/06-stub-index.md` §M6.5b.

- M6.5a: TypeSpec declarations now build a stub tree (no behaviour change yet — the name index
  and resolver switch-over are M6.5b/c). The 10 rules that can be a direct child of a file or a
  `namespace` (`namespace`/`model`/`op`/`interface`/`enum`/`union`/`alias`/`scalar`/`dec`/`fn`)
  carry `stubClass=` in `TypeSpec.bnf` and route their element type through a new
  `TypeSpecStubTypes` holder (`<stubElementTypeHolder externalIdPrefix="tsp."/>`). Each stub
  stores only a backtick-stripped name and a pre-computed dotted enclosing-namespace path,
  computed from the parent stub chain, never from PSI; a `namespace` stub additionally stores its
  own dotted segments. `node_modules` files are excluded from the persistent stub index at build
  time (`TypeSpecFileElementType.shouldBuildStubFor`, backed by a new shared
  `TypeSpecNodeModules` predicate) — reachable ad hoc via `PsiFile.getStub()` when explicitly
  opened (e.g. along an import edge), but never indexed for project-wide search. See
  `docs/adr/0011-stub-index-replaces-tier-c.md` and `docs/plans/06-stub-index.md` §M6.5a.

- M5.6g': the standard library is now genuinely reachable, not just imported. Two gaps
  remained after M5.6g, both closed by matching upstream compiler behaviour verified against
  `@typespec/compiler`'s own sources: `lib/intrinsics.tsp` (where `string`, `int32`, `boolean`,
  `float` are declared) is seeded out-of-band, because it is absent from `lib/std/main.tsp`'s
  import closure and from `package.json`'s `exports` map — the compiler loads it relative to its
  own package root (`program.js` `loadIntrinsicTypes`); and every file now gets the implicit
  ambient `using TypeSpec;` the compiler injects via `name-resolver.js` `addUsingSymbols`. The
  ambient using is gated on the std library actually having loaded, applies only at file-root
  scope, and is consulted only after direct declarations, so a local declaration of the same name
  still wins. Absent `@typespec/compiler` degrades silently to the previous behaviour.

- M0: project bootstrapped from the JetBrains IntelliJ Platform Plugin Template (v2.6.0),
  pinned to IntelliJ IDEA Community `2025.2.6.3` on JDK 21. See `docs/adr/0002-build-and-platform-baseline.md`.
- M1: `.tsp` files are now recognised as TypeSpec — `TypeSpecLanguage`, `TypeSpecFileType`,
  and a file-type icon, registered via `fileType` in `plugin.xml`.
- M2: a generated, restartable JFlex lexer (`TypeSpecLexerAdapter`) turning TypeSpec source
  into the full token set (keywords, identifiers, backtick identifiers, strings incl.
  triple-quoted, numbers, comments incl. doc comments, decorators/augment decorators,
  directives, `#{`/`#[` value-literal sigils, and all punctuation/operators). The lexer is
  generated by a config-cache-compatible `generateTypeSpecLexer` Gradle task invoking the
  IntelliJ-patched JFlex directly (no Grammar-Kit lexer plugin), per ADR 0002 D6.
- M3: `.tsp` files are now coloured in the editor and the colours are user-configurable
  under *Settings | Editor | Color Scheme | TypeSpec*. Adds `TypeSpecColors` (a
  `TextAttributesKey` per highlighting category, all derived from
  `DefaultLanguageHighlighterColors`), `TypeSpecSyntaxHighlighter` +
  `TypeSpecSyntaxHighlighterFactory`, and `TypeSpecColorSettingsPage` with a demo snippet
  (`colorSettings/demo.tsp.txt`) exercising every category. Display strings now live in a
  new `messages.TypeSpecBundle` resource bundle (`TypeSpecBundle.kt`), registered via
  `lang.syntaxHighlighterFactory` and `colorSettingsPage` in `plugin.xml`.
- M4: editor conveniences — `TypeSpecCommenter` (`//` line, `/* */` block comments,
  `lang.commenter`), `TypeSpecBraceMatcher` (`{}` / `()` / `[]` and the `#{` / `#[`
  value-literal openers, `lang.braceMatcher`), and `TypeSpecQuoteHandler`
  (auto-close/auto-skip for `"..."` / `"""..."""`, `lang.quoteHandler`). TODO-comment
  highlighting is deferred to M5 (ADR 0003 D1/D2).
- M4b: a minimal, flat `ParserDefinition` (`TypeSpecParserDefinition`, `lang.parserDefinition`)
  landed ahead of M5 to fix `lang.commenter` resolution, which requires a real file
  *language*, not just file type (see `docs/adr/0005-minimal-parser-definition-for-commenting.md`,
  amending `docs/adr/0003-parser-definition-timing.md`). Adds a real `TypeSpecFile` PSI
  (`PsiFileBase`) and a transitional `TypeSpecFlatParser` that wraps every lexer token as a
  flat leaf with no grammar/PSI hierarchy — owned by M5 Task 0, which replaces the parser
  body with the generated Grammar-Kit parser. `.tsp` files now resolve `psiFile.language` /
  `psiFile.fileType` to TypeSpec instead of falling back to `PsiPlainTextFileImpl`, fixing all
  6 `TypeSpecCommenterTest` cases and giving `TypeSpecBraceMatcher` / `TypeSpecQuoteHandler`
  automated re-verification under the new PSI.
- M5a: the Grammar-Kit toolchain now runs end-to-end (`docs/adr/0006-grammar-toolchain.md`).
  Bumped the IntelliJ Platform Gradle Plugin `2.16.0` → `2.18.1` and replaced the hand-rolled
  JFlex `JavaExec` task with the `org.jetbrains.intellij.platform.grammarkit` subplugin's
  `generateLexer`/`generateParser` tasks, wired into disjoint output roots with an explicit
  `dependsOn` on `compileKotlin`/`compileJava` (works around the 2.18.1 `@Internal`
  `targetRootOutputDir` regression — a missing `dependsOn` here passes warm and fails clean).
  Added a throwaway seam-verification grammar (`src/main/grammars/TypeSpec.bnf`, no real
  TypeSpec syntax yet) proving the `mixin=` + `implements=` single-pass pattern: the generated
  `TypeSpecThrowawayImpl` (Java) correctly extends the hand-written `TypeSpecThrowawayMixin`
  (Kotlin) and implements `TypeSpecThrowawayIface`, with `methods=[...]`/`psiImplUtilClass`
  banned. `TypeSpecTokenTypes` gained a `fromNameOrText` factory (the `tokenTypeFactory`
  bridge, covered by a new `TypeSpecTokenTypeFactoryTest`) so generated parser code resolves
  to the same token instances the JFlex lexer already emits. `TypeSpecElementType` added for
  future composite element types. `TypeSpecParserDefinition`/`TypeSpecFlatParser`/
  `TypeSpecElementTypes`/`plugin.xml` are untouched — the flat parser ships as-is until M5b.
- M5b: the real Grammar-Kit grammar (`docs/plans/03-grammar-and-psi.md` M5b,
  `docs/adr/0004-reference-resolution-approach.md` D7). `TypeSpec.bnf` now covers
  `typespec_file`/`import_statement`/`using_statement`/`namespace_statement` (block and
  blockless — the blockless form contains the rest of the file as children, so
  `TypeSpecFile.getFileNamespace()` is a trivial first-child query)/`model_statement` with
  `extends`/`is`/spread (`...Base`)/optional properties/template parameter *lists* (template
  *arguments*, e.g. `Page<Pet>`, stay out of scope for M5c). Backtick identifiers need no
  separate token — the lexer already returns the same `IDENTIFIER` for both, and
  `TypeSpecIdentifier`/`TypeSpecQualifiedName` are used uniformly everywhere a name is
  referenced. `TypeSpecParserDefinition` now wires the generated `TypeSpecParser` and
  `TypeSpecTypes.Factory.createElement`, replacing the throwaway
  `UnsupportedOperationException`; `TypeSpecFlatParser` and the M5a throwaway seam files
  (`TypeSpecThrowawayIface`/`TypeSpecThrowawayMixin`) are deleted. Added the hand-written
  named-element contract (ADR 0004 D7.2/D7.3, ADR 0006 D7): `TypeSpecNamedElement` (a bare
  `PsiNameIdentifierOwner`), `TypeSpecNamedElementMixin` (implements `getNameIdentifier`
  /`getName`/`getTextOffset`/`setName` directly — no `methods=[...]`/`psiImplUtilClass`, both
  banned repo-wide), and `TypeSpecPsiUtil` (plain Kotlin helpers, not a `psiImplUtilClass`)
  for `namespace_statement`/`model_statement`/`model_property`/`template_parameter`.
  `TypeSpecFile` gained `getImportStatements()`/`getUsingStatements()`/`getFileNamespace()`
  /`getTopLevelDeclarations()` (ADR 0004 D7.4). Grammar recovery (ADR 0006 D6) uses a
  fallback "consume one non-terminator token" alternative in both the top-level and
  model-property loops rather than `recoverWhile` on those loops directly — `recoverWhile`'s
  built-in eatMore/lastErrorPos skip cannot make any progress on input matching *no*
  alternative at all, and combining it with a catch-all fallback alternative causes it to
  spuriously flag the fallback's own successful match as an error (both verified
  empirically while writing this grammar). New tests: `TypeSpecParsingTest` (14
  `ParsingTestCase` golden-tree fixtures, goldens manually reviewed per ADR 0006 D8) and
  `TypeSpecPsiContractTest` (named-element contract + `TypeSpecFile` accessors — caught a
  real bug in `TypeSpecPsiUtil.findNameIdentifier()` misidentifying a property's type as its
  name). `kitchen-sink.tsp` is not expected to parse error-free yet (M5c constructs).
- M5c: extends the grammar (`docs/plans/03-grammar-and-psi.md` M5c,
  `docs/adr/0004-reference-resolution-approach.md` D7.2) to `op`/`interface`/`enum`
  (+ member)/`union` (+ variant)/`alias`/`scalar` declarations, decorator applications
  (`@doc(...)`) and augment-decorator statements (`@@doc(Widget.id, "the id");`), and a
  full type-expression precedence chain (`type_expression_ -> union_type_expression_ ->
  intersection_type_expression_ -> array_type_expression_ -> primary_type_expression`,
  covering `|`/`&`/postfix `[]`/template arguments/the keywordized `void`/`never`/`unknown`
  intrinsics). Every rule in that chain is `private` (trailing-underscore names) so it adds
  zero extra tree nodes for the trivial "just a name" case — this is what keeps every M5b
  golden byte-identical; the tradeoff is that even a *real* union/intersection/array type
  shows up as flat sibling tokens (`BAR`/`AMP`/`LBRACKET`/`RBRACKET`) rather than nested
  `UnionTypeExpression`/etc. composite nodes. Added a coarse `value_expression` grammar
  (`STRING`/`NUMBER`/`true`/`false`/`qualified_name`/`#{ }` object literals/`#[ ]` array
  literals) for decorator arguments and property defaults. Discovered and fixed a real
  landmine: the hand-written lexer splits any string literal containing an escape sequence
  (e.g. `"hi\nthere"`) into multiple leaf tokens (`STRING`, `VALID_ESCAPE`/`INVALID_ESCAPE`,
  `STRING`, ...) rather than one — every grammar rule that consumes "a string literal"
  (`literal_type`, `value_expression`) now consumes the whole
  `STRING (VALID_ESCAPE | INVALID_ESCAPE | STRING)*` run, not a bare `STRING`. Extended the
  named-element contract to all eight new declaration/member kinds (same `mixin=`/
  `implements=` pattern, no `methods=[...]`/`psiImplUtilClass`). Ships the (ratified)
  `spellchecker.support` extension — `TypeSpecSpellcheckingStrategy` (comments/strings via
  `CommentSplitter`/`TextSplitter`, identifiers via `IdentifierSplitter`) — behind a second,
  intentional `<depends>com.intellij.modules.spellchecker</depends>` (`build.gradle.kts`
  gained a matching `bundledModule("intellij.spellchecker")` for the compile classpath). New
  fixture `src/test/testData/parser/KitchenSinkCore.tsp` (the M5c-achievable subset of
  `kitchen-sink.tsp` — `kitchen-sink.tsp` itself is untouched, still M2's/M5d's fixture) now
  parses with zero `PsiErrorElement`s. New tests: 14 `TypeSpecParsingTest` golden-tree
  fixtures plus `KitchenSinkCore` (all goldens manually reviewed per ADR 0006 D8), and 7 new
  `TypeSpecPsiContractTest` methods (own `ContractFixtureM5c.tsp`, M5b's `ContractFixture.tsp`
  untouched). All M5b goldens/tests unchanged.

- M5.5 (plan 02, ADR 0004): Ctrl/Cmd-click and *Go To Declaration* on a qualified type
  reference now jump to its `model`/`enum`/`union`/`interface`/`alias`/`scalar`/`op`/
  `namespace` declaration. One `PsiReference` implementation
  (`resolve/TypeSpecReference.kt`, `PsiPolyVariantReferenceBase`, always soft per ADR 0004
  D3) hangs off every name-position `TypeSpecIdentifier` via a new
  `TypeSpecIdentifierMixin.getReference()` (`identifier` rule gains `mixin=` in
  `TypeSpec.bnf`; no tree-shape change). `resolve/TypeSpecResolver.kt` resolves the leading
  segment of a qualified name lexically (`resolve/TypeSpecScope.kt`'s namespace-chain walk,
  innermost enclosing namespace outward to global, `using` bindings resolved via the same
  per-segment reference mechanism, longest-prefix-first) and later segments as members of
  the previous segment's resolved namespace; `resolve/TypeSpecFileDeclarations.kt` is a
  per-file `CachedValuesManager`-backed name table (cache-dependent on the file itself, not
  `MODIFICATION_COUNT` — editing one file invalidates only that file's table, ADR 0004 D2);
  `resolve/TypeSpecImportGraph.kt` follows relative (`./`, `../`) `import` targets
  (including directory-with-`main.tsp`) to their transitive closure, cycle-safe, capped at
  200 files. Scope: tiers A (current file) and B (transitive import closure) only — tier C
  (project-wide word-index prefilter for the merged-namespace, no-explicit-import case) and
  `FindUsagesProvider` are not implemented in this milestone; see the M5.5 report. Also
  registers a `lang.elementManipulator` for `TypeSpecIdentifier`
  (`resolve/TypeSpecIdentifierManipulator.kt`) that deliberately throws — rename ships in
  M6.5 (ADR 0004 D6). No new `<depends>` (ADR 0004 F1).

- M5.5b (plan 02, ADR 0004 D2/D6): tier C and Find Usages, completing M5.5's risk-9 split.
  `resolve/TypeSpecSearchScopes.kt` widens the leading segment of a qualified name to every
  `.tsp` file in the project whose text contains the candidate name, word-index prefiltered
  (`CacheManager.getVirtualFilesWithWord`, `UsageSearchContext.ANY`), only once tiers A/B
  (current file, transitive import closure) have yielded nothing; capped at
  `TIER_C_FILE_CAP` (50) files and returns `null` (unresolved, not a truncated partial answer)
  when the index is unavailable (dumb mode) or the cap is exceeded — `TypeSpecResolver`
  degrades to "unresolved" in both cases rather than parsing on. This makes a reference in one
  file resolve into a same-namespace declaration in another file with no explicit `import`
  (ADR 0004 F4; plan 02 acceptance case 15) — the norm in real TypeSpec projects. New
  `findusages/TypeSpecFindUsagesProvider.kt` (`lang.findUsagesProvider`) supplies a
  `DefaultWordsScanner` (a fresh instance per call, per the platform's thread-safety
  contract) and a `getType`/`getDescriptiveName`/`getNodeText` covering every declaration
  kind (`model`/`enum`/`union`/`interface`/`alias`/`scalar`/`op`/`namespace`/model
  property/template parameter); it needs no working `TypeSpecIdentifierManipulator` — Find
  Usages runs entirely through `ReferencesSearch` and `PsiReference.isReferenceTo()`, never
  through the manipulator, which stays a deliberate rename-is-M6.5 stub. Registering the
  provider changes the `.tsp` word-index encoding (occurrences become categorised instead of
  `ANY`), so the first IDE start after installing this build re-indexes `.tsp` files —
  expected, not a regression. No new `<depends>` (ADR 0004 F1).

- M5.6a/M5.6b (plan 05, ADR 0010): `import "…"` now navigates on Cmd-click, both forms the
  owner reported — relative (`import "../master-data/branch.tsp";`) and bare/library
  (`import "@typespec/openapi";`). New `resolve/TypeSpecImportResolver.kt` resolves any
  specifier to a `TypeSpecFile`, matching the compiler's own entry-point order
  (`exports["."]` under the `"typespec"` condition → `tspMain` → `main` → `main.tsp`,
  verified against `entrypoint-resolution.js`/`source-loader.js`, not guessed — a naive
  `lib/main.tsp` is wrong for `@typespec/compiler` and `@typespec/protobuf`); a bare
  specifier is found by walking **up** from the importing file through `node_modules`
  directories (the npm-workspaces-monorepo case), and a symlinked target is canonicalised
  before becoming PSI (ADR 0010 D4). `TypeSpecImportGraph.resolveImportTarget` now delegates
  here, so library imports also enter a file's transitive-closure tier B for free —
  `@TypeSpec.OpenAPI.info`-shaped decorator navigation (a later milestone) will resolve
  through this. `package.json` is read with a bundled Gson dependency (`build.gradle.kts`,
  ADR 0010 D3 — no JSON library exists on the CE classpath and a JSON plugin dependency
  would be a third `<depends>`); malformed/missing `package.json` and a non-`.tsp` target
  (every real library entry point also `import`s a `.js` decorator implementation) both
  resolve to `null`, silently. `TypeSpecSearchScopes` is untouched — this is a targeted
  lookup along a declared `import` edge, never a project-wide search, so `node_modules`
  stays excluded from tier C exactly as ADR 0008 shipped it. New
  `psi/impl/TypeSpecImportStatementMixin.kt` (`import_statement` gains `mixin=` in
  `TypeSpec.bnf`; no tree-shape or golden change) and `resolve/TypeSpecImportReference.kt`
  (`PsiReferenceBase` with an explicit range over the `STRING` token's contents, always soft
  per ADR 0010 D5 — a missing package is not a source error; `handleElementRename`/
  `bindToElement` throw, same stated limitation as M5.5's identifier reference). No new
  `<depends>`.

- M5.6g (plan 06, ADR 0010 open question 1): every file's transitive-closure tier B now also
  implicitly includes the `@typespec/compiler` standard-library entry point, matching the real
  compiler's behaviour of always loading it whether or not a file imports anything.
  `resolve/TypeSpecImportGraph.compute` seeds its BFS queue with
  `TypeSpecImportResolver.resolve(file, "@typespec/compiler")` once, from the starting file,
  in addition to `file` itself — no new resolution path, no hardcoded path, `node_modules`
  entered only along this one resolver lookup exactly as ADR 0010 already sanctioned. Absent
  or unresolvable `@typespec/compiler` (no `node_modules`, package not installed) seeds
  nothing, silently — no exception, no notification. `TypeSpecSearchScopes` is untouched.
  Measured effect: a std-library declaration (e.g. `scalar string;` in `lib/std/main.tsp`)
  now resolves from any file that writes `using TypeSpec;`, without an explicit `import`.
  **Known gap, not closed by this milestone:** a file that writes neither `using TypeSpec;`
  nor lives inside `namespace TypeSpec { ... }` still does not resolve unqualified std-lib
  names — the real compiler injects an implicit, ambient `using TypeSpec;` into every source
  file once the std library is loaded (`name-resolver.js`'s `addUsingSymbols` over every
  `program.sourceFiles`), and this plugin's scope resolver (`TypeSpecScope`/
  `TypeSpecResolver`) does not model that; closing it needs a follow-up decision, not
  guesswork, since it changes core scope-chain semantics rather than the import graph.
  Decorator references (`@doc`, `@key`, …) are unaffected by this milestone either way — the
  grammar has no `PsiReference` on a decorator name yet (`DECORATOR` is a single lexer token,
  never wrapped in `qualified_name`), which is M5.6d's job, listed as absorbed into a later
  milestone. Compiler intrinsics `void`/`never`/`unknown` are lexer keywords in this grammar,
  never identifiers, so they are not reference positions and cannot resolve by nature.
  `string`/`int32`/`boolean`/`float` etc. are declared in `lib/intrinsics.tsp`, which is
  *not* imported by `lib/std/main.tsp`'s closure (verified against the real package under
  `@typespec/compiler` — the compiler loads it out-of-band, not via `import`), so this
  milestone's edge does not reach that file either.

### Fixed

- Three confirmed grammar defects in the same family as the member-directive cascade fix
  below, found re-syncing the corpus (`real/common/lima.tsp`, and the owner's real
  `standards.tsp`), all traced to the same root cause: `model_member_`'s `directive_statement`
  alternative (added for the fix below) modelled a member-level directive as a member sibling
  in its own right, which is not how upstream's parser actually works. Read directly from
  `@typespec/compiler`'s `parser.js` (`parseList`, `parseAnnotations`): every list item
  (model property/spread, enum member, union variant, interface operation) is preceded by a
  single shared `parseAnnotations()` loop that collects directives AND decorators together, in
  whatever order they appear, any number of each, before the parser even knows which item kind
  follows — there is no separate "directive statement" member. `TypeSpec.bnf` is re-modelled to
  match: a new private `member_annotation_ ::= decorator_application | directive_statement` is
  now each real member rule's own leading prefix (`model_property`, `model_spread`,
  `enum_member`, `union_variant`, `interface_operation`), replacing their previous
  `decorator_application*`-only prefix, and the standalone `directive_statement` alternative is
  removed from `model_member_`. This fixes, in one change:
  - `enum`/`union`/`interface` member-level directives, which never got the `model_member_` fix
    below and still broke their own member loop (`bad_enum_member_token_` /
    `bad_union_variant_token_` / `bad_interface_member_token_` had no `DIRECTIVE` alternative) —
    now all three also gain `DIRECTIVE` in their `bad_*_token_` fallback, matching
    `bad_scalar_member_token_`'s existing precedent, for a lone directive with nothing real
    following it before the body's closing brace.
  - Decorators interleaved with a member directive in ANY order (`@a #d @b prop`, `#d @a @b
    prop`) no longer roll back and silently drop the decorators as unclaimed leaves — the real
    `lima.tsp`/`standards.tsp` shape (`@minLength(0) @maxLength(40) #deprecated "..." @field(70)
    prop: string;`) now attaches all three decorators and the directive to the same property.
  - `directive_argument_`'s `identifier` alternative — safe only at top level, where no
    plain-identifier construct could follow a directive, an invariant that broke the moment a
    directive could appear directly before a member — no longer swallows an undecorated
    property's leading identifier as a further directive argument
    (`#deprecated "x"` immediately followed by `name: string;`). Upstream itself bounds a
    directive's argument list with a real `NewLine` token, which this lexer does not expose to
    the grammar (`_TypeSpecLexer.flex` folds all whitespace into one `WHITE_SPACE` trivia
    token); absent that signal, the `identifier` alternative is now guarded by a negative
    lookahead (`identifier !(':' | '?')`) — an identifier immediately followed by `':'` or `'?'`
    was never a valid directive argument to begin with (that shape only ever means "the next
    member's name"), so this rejects exactly the ambiguous case without narrowing any
    genuinely-valid directive usage.

  Pin changes: `model_property`/`enum_member`/`union_variant`/`interface_operation` keep their
  existing pins (`member_annotation_*` is one repeated sequence element, same position as the
  `decorator_application*` it replaces). `model_spread` gains `member_annotation_*` as a NEW
  leading element (upstream's `parseModelSpreadProperty` also receives whatever decorators/
  directives `parseAnnotations()` collected ahead of it, even though decorators there are
  flagged `invalid-decorator-location`) — `'...'` shifts from sequence position 1 to position 2,
  so `model_spread`'s pin changes from `pin=1` to `pin=2`. No stub format, encoding, external-ID
  or `node_modules`-predicate change, so `TypeSpecStubVersion.VERSION` is not bumped.

- Confirmed resolution bug, real repro (ph-cdm `model/flight/flight.tsp` / `model/shared/standards.tsp`):
  `...MetaData;` and `defaultMeasurementVolume?: VolumeUnit;` failed to resolve even though
  `flight.tsp` directly `import`s `standards.tsp` (tier A/B territory, not a stub-index case).
  Root cause was **not** the dotted-blockless-namespace path computation (that code was already
  correct on both the stub and PSI paths) but a grammar gap: `model_member_` had no alternative
  for a member-level `#deprecated`/`#suppress` `directive_statement` (M6e had deliberately deferred
  this — zero corpus occurrences at the time). Hitting one mid-body broke `model_property`'s own
  parse attempt, fell through to `bad_model_member_token_` one token at a time, and never
  resynchronized before the model's closing `'}'`, which then surfaced as a second, unrelated
  parse error — and because the enclosing `namespace_statement` here is the file's own blockless,
  `';'`-bodied form (whose body is `top_level_item_*`, with no closing brace of its own to recover
  at), every declaration textually after the broken model — including `MetaData` and `VolumeUnit`,
  hundreds of lines later — was silently dropped from the PSI tree and never entered the stub
  index at all. Fix: `model_member_` now accepts `directive_statement` as a first-class
  alternative (`enum`/`union`/`interface` member positions are unchanged — no confirmed repro
  needs them). Verified against the owner's real file content (in-memory fixture, no content-root
  change): both references now resolve to their declarations in `standards.tsp`; stripping the
  two `#deprecated` occurrences already made the repro pass, confirming the diagnosis before the
  grammar fix and after it. No stub format, encoding, external-ID or `node_modules`-predicate
  change, so `TypeSpecStubVersion.VERSION` is not bumped.

- Grammar corrections from the ph-cdm corpus audit (plan 04 M6a/M6b; see
  `docs/adr/0007-corpus-driven-grammar-acceptance.md`):
  - `value_expression` is unified with `type_expression_` (ADR 0007 D6), so decorator and
    augment-decorator arguments now accept brace model expressions —
    `@@package(A.B, { name: "x" })` and `@dec({ title: "x" })` previously failed to parse.
  - `model_property` gains a leading `decorator_application*` — `@key id: string;` (the
    single most common non-trivial construct in the ph-cdm corpus) previously failed to
    parse because `model_property` was the one decoratable rule with no decorator prefix.
    `pin` moves from `3` to `4` to keep pinning at `':'`. `DECORATOR` is added to
    `bad_model_member_token_` so a decorated property no longer poisons the rest of the
    model body's error recovery.
  - `model_property`'s trailing `;` becomes an optional `member_separator_` (`;` or `,`) —
    needed for the reported repro itself: an inline `{ name: "x" }` / `{title: "x"}` object
    argument has no trailing punctuation before its closing `}`.
  - `Decorators.txt`, `AugmentDecorator.txt`, `Enum.txt` and `KitchenSinkCore.txt` parser
    goldens shift as a result (a plain string/number `value_expression` now wraps in the
    inlined `literal_type` node) and are pending hand review — not blind-rebaselined.
  - M6b: `model_spread` is repointed from a bare `qualified_name` to a full
    `type_expression_` so a spread can carry template arguments (`...Record<unknown>;`) the
    same way `model_property`'s type position already does, and its trailing `;` becomes the
    optional `member_separator_` (`'...'` keeps the pin).
  - M6b: `enum_member` and `union_variant` both gain the same optional trailing
    `member_separator_?` as `model_property` — upstream's `ListKind.EnumMembers` and
    `ListKind.UnionVariants` are `ListKind.ModelProperties`-shaped (`delimiter: Semicolon,
    toleratedDelimiter: Comma`, the tolerated form fully valid, per `@typespec/compiler`'s
    `parser.ts`). Neither addition moves its rule's pin (`enum_member` stays `pin=2`,
    `union_variant` stays `pin=3`) since `member_separator_?` is appended after the existing
    sequence in both cases. `Union.txt`, `Enum.txt` and `KitchenSinkCore.txt` goldens shift
    again (a member's trailing `,`/`;` now nests inside the member node instead of sitting
    beside it in the body) and were hand-reviewed, not blind-rebaselined.
  - M6b done-signal check against `corpus/real/**`: only `booking/signals/rel-union.tsp`
    (mislabelled row5 in `BASELINE.txt`, real construct: `,`-separated union variants) was
    actually blocked by rows 1/5/6 and is now fixed by the `union_variant` change above. The
    other six files `BASELINE.txt` had labelled row1 are, after `model_property`'s decorator
    fix, actually blocked by row3 (`model X is Y;`) or row4 (triple-quoted strings) — both
    M6c scope, correctly left failing here.
  - M6c (plan 04 §M6c, ADR 0007 D9): `model_statement`'s heritage now matches
    `@typespec/compiler`'s `parseModelStatement` exactly — `model X is Y;` **and**
    `model X is Y { … }` are both valid (`is_clause (';' | model_body)`), while `extends`
    (or no heritage at all) still requires a body (`extends_clause? model_body`).
    `model M extends Foo;` remains a `PsiErrorElement`. `pin=2` (on `'model'`) is unaffected.
  - M6c row 4: `MULTILINE_STRING` is declared in the `.bnf` `tokens=[…]` block and added as
    an alternative of `literal_type`, so `"""…"""` triple-quoted strings are now usable as a
    decorator argument (`@doc("""…""")`) and a property default — the lexer already emitted
    the token (`_TypeSpecLexer.flex`), the grammar simply never referenced it. Confirmed via
    `TypeSpecLexerTest` that one well-formed multi-line literal yields exactly one
    `MULTILINE_STRING` token (unlike `STRING`'s escape-run splitting), so no `+` repetition
    is needed.
  - M6c done-signal: all 9 remaining `corpus/real/**` entries in `BASELINE.txt` (6 row3, 3
    row4) now parse clean with zero `PsiErrorElement`s and zero unclaimed leaves — the
    corpus-driven done-signal for `ph-cdm` going clean in the editor (ADR 0007 D11).
  - M5.5a navigation bug: a fully-qualified reference through a dot-nested `namespace`
    declaration (`namespace A.B.C;`, referenced as `A.B.C.Model`) failed to resolve (plan 02
    acceptance case 7). `TypeSpecFileDeclarations.build` indexed such a declaration only
    under its last dotted segment (`"C"`), never the virtual intermediate segments `"A"`/
    `"A.B"`, so resolving the leading segment `"A"` found nothing and the whole chain
    returned null. Every segment of a dotted namespace declaration is now indexed under its
    own prefix path, pointing at the same `namespace` statement — there is no separate
    declaration site for the virtual segments, so `A`, `A.B` and `A.B.C` in `A.B.C.Model` all
    resolve to that one statement, exactly like a reopened non-dotted namespace with the same
    name would. `TypeSpecResolver` no longer derives a trailing segment's namespace path via
    `TypeSpecScope.fullPathOf` on the resolved element (which would collapse every dotted
    segment onto the declaration's one full path); it now reconstructs the denoted path as
    "the path a segment was found under" + "its own name", recursively, for both dotted and
    block-nested namespaces.
  - M6d (plan 04 §M6d): the operation/interface surface, per ADR 0007's primary-source
    facts table.
    - `operation_parameter` is split into two full alternatives —
      `operation_spread_parameter_` (`decorator_application* '...' type_expression_`,
      `pin=2`) and `operation_named_parameter_` (unchanged named-parameter shape plus an
      optional `('=' value_expression)?` default, `pin=4`) — instead of a single sequence
      with a leading `'...'?`. Verified directly against the corpus
      (`stdlib/protobuf/test/scenarios/simple/input/main.tsp`) that a spread parameter
      (`op foo(...Input): Output;`, row 7, the largest remaining corpus category — 37
      stdlib occurrences / 29 files) is `'...' type_expression_` with no following
      `identifier '?'? ':'`, the same shape as `model_spread`, not a named parameter with
      a `'...'?` prefix as one reading of the plan's approach line suggested; probing that
      literal reading against the actual construct fails with a self-contradictory parser
      error ("expected '...', got '...'"), so the corrected two-alternative form is what
      shipped.
    - `op_statement` gains an optional `template_parameter_list?` and an `is`-heritage
      alternative (`op foo is Bar;`, `op foo is Bar<T>;` — zero corpus occurrences, listed
      in the plan as low-priority but grammatical upstream). `pin=2` (on `'op'`) is
      unaffected.
    - `interface_statement` gains an optional `interface_heritage_?`
      (`'extends' type_reference_ (',' type_reference_)*`, `pin=1`) — per
      `parseInterfaceStatement`/`ListKind.Heritage` (ADR 0007 primary-source facts table),
      `interface X extends A, B` is a comma-separated list of *reference expressions*
      (`type_reference_`), deliberately narrower than `extends_clause`'s full
      `type_expression_`. There is no `interface ... is` form upstream and none was added;
      `interface I is Stream<T>;` still produces a `PsiErrorElement` (probed directly,
      confirmed unchanged). `interface_statement`'s own `pin=2` is unaffected.
    - `interface_operation` gains an optional leading `'op'?` keyword
      (`ListKind.InterfaceMembers`'s `allowedStatementKeyword: Token.OpKeyword` — zero
      corpus occurrences, implemented per the plan's explicit instruction) and now delegates
      its parameter list to the updated `operation_parameter`. `'op'?` occupies sequence
      position 2 (after `decorator_application*`), shifting `operation_parameter_list` from
      position 3 to 4 — `pin=4`, not `pin=3`.
    - `model M extends Foo;` (ADR 0007 D9) was re-probed and confirmed still a
      `PsiErrorElement` — unaffected by this milestone's changes.
    - No hand-authored parser golden shifted: `Operation.txt` and `Interface.txt` (neither
      fixture exercises spread parameters, `op ... is`, or `extends` heritage) are
      byte-identical; all 189 non-corpus tests pass.
    - 27 `corpus/stdlib/protobuf/test/scenarios/**/input/main.tsp` files whose only blocker
      was row 7 now parse with zero `PsiErrorElement`s and zero unclaimed leaves (removed
      from `BASELINE.txt` by the ratchet's next update — not done in this milestone, since
      `src/test/` is out of scope for `tsp-dev`). `corpus/real/**` is unaffected: it remained
      at 0 `BASELINE.txt` entries before and after this change.
  - M6e (plan 04 §M6e, rows 8–12): the library-authoring surface —
    `modifier_* ::= 'extern' | 'internal' | 'auto'` as a new prefix on `model_statement`
    (`pin` moves `2` → `3`, still on `'model'`); `dec_statement`/`fn_statement` top-level
    rules (`'dec'`/`'fn'` identifier `dec_fn_parameter_list` `(':' type_expression_)?` `';'`);
    `valueof_expression`/`typeof_expression` added to `primary_type_expression`; an optional
    `'{' scalar_member* '}'` body on `scalar_statement` (row 10, reusing
    `dec_fn_parameter_list` for `init` constructors); a standalone top-level
    `directive_statement` for `#suppress`/`#deprecated` (row 11; member-level directive
    placement deliberately deferred — zero corpus occurrences). `template_parameter`'s
    default is repointed from `type_expression_` to `value_expression` (the one straggler
    M6a's ADR 0007 D6 unification missed). 37 corpus files move from `BASELINE.txt`-failing
    to passing; `BASELINE.txt` itself untouched (`tsp-tester`'s file).
  - M6e′ (this milestone) closes the two gaps M6e left open, both verified directly against
    `@typespec/compiler`'s `parser.js` rather than assumed:
    - **Call expressions in type position** (`alias FilterVisibility<...> =
      applyVisibilityFilter(M, Filter, NameTemplate);`,
      `stdlib/compiler/lib/std/visibility.tsp`; `applyMergePatchTransform(T, NameTemplate,
      #{...})`, `stdlib/http/lib/main.tsp`). Confirmed against `parsePrimaryExpression`'s
      `Token.Identifier` case → `parseCallOrReferenceExpression`: not a separate top-level
      production — the identifier/member-expression target is parsed once, then branches on
      `(` (call, `ListKind.FunctionArguments`, arguments are the general `Expression`) vs.
      anything else (existing template-argument-list path). `private type_reference_` becomes
      `qualified_name (call_argument_list | template_argument_list)?` and gains a new
      `call_argument_list ::= '(' (value_expression (',' value_expression)*
      trailing_comma_?)? ')'` sibling to `decorator_argument_list` — the two tokens `(`/`<`
      are disjoint, so no ambiguity. `type_reference_` stays `private` and has no `pin`; no
      pin changes anywhere from this piece.
    - **Trailing separator in every punctuation-delimited list** (`testInputField5: string,`
      / `): {};` in `stdlib/protobuf/test/scenarios/simple-error/input/main.tsp`'s
      `operation_parameter_list`). Confirmed against `parser.js`'s `parseList`: for every
      `ListKind` whose `close !== Token.None`, a single trailing occurrence of the primary
      delimiter immediately before the close token is tolerated and ends the list —
      independent of list kind, so fixed with one shared shape rather than case-by-case. New
      `private trailing_comma_ ::= ','`, appended (as `trailing_comma_?`) to
      `decorator_argument_list`, `augment_decorator_statement`'s inline argument list,
      `array_literal`, `dec_fn_parameter_list`, `tuple_expression`, `template_parameter_list`,
      `template_argument_list`, `operation_parameter_list` and the new `call_argument_list`.
      `object_literal` already had the equivalent `,'?` since M6a and needed no change.
      `interface_heritage_` deliberately excluded — upstream's `ListKind.Heritage` has
      `open`/`close === Token.None`, which upstream's own `parseList` never routes through
      the trailing-close branch. None of these rules had a `pin` position affected: in every
      case `trailing_comma_?` is appended *inside* the existing optional/repeated group,
      after the last real item and before the literal close token, so no sequence position
      that carries a `pin` moved. The rule accepts exactly one trailing comma after a real
      item and rejects both an empty list element and a doubled separator (a second comma
      has no `item` between it and the first to satisfy the repetition).
      `stdlib/protobuf/test/scenarios/simple-error/input/main.tsp` also contains two
      deliberately-invalid semantic fixtures for the protobuf emitter (an out-of-range
      `@field` index; `@message` applied to an `interface`) — both are checker-level
      diagnostics, not parse errors; confirmed the file parses with zero `PsiErrorElement`s
      once the trailing comma is accepted.
    - No hand-authored parser golden shifted; all 190 non-ratchet tests pass unchanged.
      `TypeSpecCorpusTest.testCorpusMatchesBaseline` now reports the full 40-file
      `BASELINE.txt` set as newly-passing and zero undeclared regressions —
      `corpus/real/**` remained at 0 entries throughout. `BASELINE.txt` itself is untouched
      here (`tsp-tester`'s file, per plan 04 M6f).
  - **Cmd-click hang (owner-reported, ADR 0008 perf investigation).** Measured against a real
    owner project (`ph-cdm`, 83 `.tsp` files, 60 under `node_modules`, mirrored read-only into
    a test fixture — the owner's tree itself was never modified): resolving an identifier that
    can only be found through a `using` binding (the normal case for any name the `using`
    exists to bring into scope, not a corner case) recurses through
    `TypeSpecScope.usingsVisibleIn` → `resolveUsingTarget` → a full nested
    `TypeSpecReference.multiResolve` for every `using` statement visible at every scope level,
    for every tier C candidate file — with no memoization across separate calls that reach the
    *same* `using` statement by different paths. Against the real corpus this pinned one CPU
    core, confirmed via thread dump (`AWT-EventQueue-0`, `RUNNABLE`, tens of stacked
    `usingsVisibleIn`/`resolveUsingTarget` frames) still recursing after 15+ minutes — exactly
    the platform's "Resolving reference..." hang the owner reported, and squarely inside a
    read action, on what should be the EDT dispatch path. Fixed by caching
    `resolveUsingTarget`'s result per `using` PSI element (`CachedValuesManager`, dependency on
    the `using` statement's containing file — the same tradeoff `TypeSpecFileDeclarations`
    already makes, ADR 0004 D2): each `using` statement's target is now resolved at most once
    per cache generation, however many scopes/candidate files reach it, collapsing the
    combinatorial blow-up. The existing per-thread re-entrancy guard is unchanged and still
    guards the genuine self-referential case.
  - **`node_modules` unintentionally in tier C's search scope (ADR 0008 perf investigation).**
    `TypeSpecSearchScopes.tspScope` used `GlobalSearchScope.projectScope`, which includes
    `node_modules` unconditionally — nothing in a CE-only project without the Node/JS plugin
    marks it excluded. Measured against `ph-cdm`: one npm package's own bundled test fixtures
    (`@typespec/protobuf/test/scenarios/**/input/main.tsp`, 36 files) alone pushed the
    word-index hit count for `Protobuf` — the identifier in `using TypeSpec.Protobuf;`,
    present in nearly every one of that project's own source files — past
    `TIER_C_FILE_CAP` (50), the exact tension recorded in
    `docs/adr/0008-tier-c-file-cap.md`: too many files for performance, too few for the
    owner's own correctness, from the same cause. `tspScope` now excludes any file under a
    `node_modules` directory (matched by path segment, no `NodeJS` plugin dependency needed).
    This does not regress a working feature — resolving *into* a library's own declarations by
    accidentally landing in `node_modules` was never a deliberate capability of this milestone
    (ADR 0004 open question 2 remains open and unimplemented); it was an expensive, cap-eating
    side effect of scope that was never meant to be searched.

