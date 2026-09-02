# Plan 01 — Language, lexer and syntax highlighter (M1 → M3)

Governed by [ADR 0001](../adr/0001-highlighting-approach.md) and
[ADR 0002](../adr/0002-build-and-platform-baseline.md). Prerequisite: **M0 green**.

This plan covers the primary deliverable. It is split into three milestones so each fits one
`tsp-dev` run. Do not start M2 before M1's test is green; do not start M3 before M2's is.

Package root: `simpli.fyi.plugins.typespec`. Kotlin, JDK 21 target, IntelliJ IDEA Community
2025.2.6.3 on the compile classpath.

---

## Grammar ground truth (verified 2026-09-02)

Everything below was read out of `microsoft/typespec` →
`packages/compiler/src/core/scanner.ts` (`main` branch, 1784 lines). Where the
`typespec-language` skill and the scanner disagree, **the scanner wins**; four such
corrections are flagged with ⚠.

### Keywords — the complete `Keywords` map

Active:
```
import  model  scalar  namespace  interface  union  if  else  projection  using
op  extends  is  enum  alias  dec  fn  valueof  typeof  const  init
true  false  return  void  never  unknown  extern  auto  internal
```

Reserved for future use (also in the `Keywords` map — lex them as keywords, ADR 0001):
```
statemachine  macro  package  metadata  env  arg  declare  array  struct  record
module  mod  sym  context  prop  property  scenario  pub  sub  typeref  trait
this  self  super  keyof  with  implements  impl  satisfies  flag  partial
private  public  protected  sealed  local  async
```

⚠ **`null` is NOT a keyword.** It does not appear in `Keywords`; TypeSpec's `null` is an
intrinsic type resolved by name. Lex it as `IDENTIFIER`. The `typespec-language` skill's
"Boolean / null literal" row is wrong on this point.

⚠ **`true`/`false` ARE keywords** (`Token.TrueKeyword`/`FalseKeyword`), not a separate
literal class. They get `TSP_KEYWORD`.

⚠ Keyword recognition in the real scanner is gated on the identifier starting with a
**lowercase ASCII letter** and being 2–12 characters (`KeywordLimit`). In JFlex, simply
listing the literals before the identifier rule gives the same result; no special handling
needed.

### Punctuation and operators — the complete set

```
{  }  (  )  [  ]  .  ...  ;  ,  <  >  =  &  |  ?  :  ::  @  @@  #  #{  #[
*  /  +  -  !  <=  >=  &&  ||  ==  !=  =>
```

⚠ `#` is **not** exclusively a directive sigil. `#{` (`Token.HashBrace`) and `#[`
(`Token.HashBracket`) open object- and array-**value** literals. A rule that greedily eats
`#` + anything will mis-lex `#{ title: "x" }`. The `typespec-language` skill omits this.

Multi-character operators must be listed **before** their single-character prefixes in the
`.flex` file, and `...` before `.`, `@@` before `@`, `#{`/`#[` before `#`.

### Literals

| Form | Scanner behaviour | Our lexer |
|---|---|---|
| `"…"` | escapes via `\`; `${` opens a template head | one `STRING` token; `\x` sub-tokens split out (below) |
| `"""…"""` | triple-quoted, multi-line; a lone `"` inside is content | `MULTILINE_STRING`, own lexer state |
| `${expr}` | scanner emits `StringTemplateHead/Middle/Tail` | ADR 0001: **whole literal coloured as string**, no sub-tokens. Deferred to M5. |
| integer / decimal | `digits [ '.' digits ] [ 'e' [+/-] digits ]` | `NUMBER` |
| hex | `0x` + hex digits | `NUMBER` |
| binary | `0b` + binary digits | `NUMBER` |

⚠ The exponent marker is lowercase **`e` only** (`CharCode.e`); `1E5` is not one numeric
token in TypeSpec. Match `e` only, and note it in a `.flex` comment so nobody "fixes" it.

⚠ The scanner folds a leading `+`/`-` into the numeric literal when it is directly followed
by a digit (`scanSignedNumber`). **We deliberately do not**: `+`/`-` stays an operator token
and the number is unsigned. Colouring is identical either way, and this keeps the lexer
context-free. Record it in a `.flex` comment.

### Identifiers

- Ordinary: ASCII/Unicode identifier start + continue.
- Backticked: `` `if` ``, `` `has space` `` — `Token.Identifier` with a `Backticked` flag.
  **Must lex as `IDENTIFIER`, not `STRING`.** Own JFlex state so an unterminated backtick at
  EOF still yields a token.

### Comments

| Form | Token |
|---|---|
| `// …` to end of line | `LINE_COMMENT` |
| `/* … */` | `BLOCK_COMMENT` |
| `/** … */` | `DOC_COMMENT` |

Block comments **do not nest** in TypeSpec's scanner — the first `*/` closes. Do not write a
nesting rule. `/**/` is an empty block comment, not an unterminated doc comment: put the
`/**/` case ahead of the doc-comment rule, or require at least one char after `/**`.

### Decorators and directives

- `@name`, `@Ns.name`, `@@name`, `@@Ns.name` → a **single** `DECORATOR` / `AUGMENT_DECORATOR`
  token covering the sigil and the dotted name. One token, not three, so the whole thing
  colours as metadata.
- `#name` where `name` starts an identifier and the char after `#` is **not** `{` or `[`
  → `DIRECTIVE` (e.g. `#suppress`, `#deprecated`). The directive's *string arguments* are
  ordinary `STRING` tokens on the same line; do not swallow them.
- A bare `#` followed by anything else → `HASH` punctuation.

---

# M1 — Language and file type

**Goal.** IntelliJ recognises `.tsp` as the TypeSpec language and shows an icon. No colours.

## Files to create

### `src/main/kotlin/simpli/fyi/plugins/typespec/TypeSpecLanguage.kt`
`object TypeSpecLanguage : Language("TypeSpec")`, with a `@JvmField val INSTANCE` or the
Kotlin `object` itself exposed such that `plugin.xml`'s `fieldName="INSTANCE"` resolves.
Prefer the conventional shape used by JetBrains samples:
```kotlin
class TypeSpecLanguage private constructor() : Language("TypeSpec") {
    companion object { @JvmStatic val INSTANCE = TypeSpecLanguage() }
}
```
Override `getDisplayName()` → `"TypeSpec"`.

### `src/main/kotlin/simpli/fyi/plugins/typespec/TypeSpecFileType.kt`
`class TypeSpecFileType private constructor() : LanguageFileType(TypeSpecLanguage.INSTANCE)`
with `companion object { @JvmStatic val INSTANCE = TypeSpecFileType() }`.

- `getName()` = `"TypeSpec"` (must match `plugin.xml`'s `name` attribute exactly)
- `getDescription()` = `"TypeSpec API description language"`
- `getDefaultExtension()` = `"tsp"` (no dot)
- `getIcon()` = `TypeSpecIcons.FILE`
- `getCharset(file, content)` — leave default; TypeSpec is UTF-8, but do not hard-code.

### `src/main/kotlin/simpli/fyi/plugins/typespec/TypeSpecIcons.kt`
```kotlin
object TypeSpecIcons {
    val FILE: Icon = IconLoader.getIcon("/icons/typespec.svg", TypeSpecIcons::class.java)
}
```

### `src/main/resources/icons/typespec.svg`
16×16, `viewBox="0 0 16 16"`, monochrome, using `fill="#6C707E"` (JetBrains' neutral grey) so
it reads in both themes. A simple mark is fine — do not copy Microsoft's TypeSpec logo
without checking its licence; note that check in the milestone report.

## Files to modify

### `src/main/resources/META-INF/plugin.xml`
```xml
<extensions defaultExtensionNs="com.intellij">
  <fileType name="TypeSpec"
            implementationClass="simpli.fyi.plugins.typespec.TypeSpecFileType"
            fieldName="INSTANCE"
            language="TypeSpec"
            extensions="tsp"/>
</extensions>
```
`language` must equal the `Language` id string; `name` must equal `FileType.getName()`.

## Acceptance — `tsp-tester` writes

`src/test/kotlin/simpli/fyi/plugins/typespec/TypeSpecFileTypeTest.kt`, extends
`BasePlatformTestCase`:

1. `assertSame(TypeSpecFileType.INSTANCE, FileTypeManager.getInstance().getFileTypeByExtension("tsp"))`
2. `myFixture.configureByText("demo.tsp", "namespace Demo;")` →
   `assertSame(TypeSpecFileType.INSTANCE, myFixture.file.virtualFile.fileType)` and
   `assertSame(TypeSpecLanguage.INSTANCE, myFixture.file.viewProvider.baseLanguage)`

   > **Do not assert `myFixture.file.language` or `myFixture.file.fileType`.** Until a
   > `ParserDefinition` is registered (M5), those are `PlainTextLanguage` /
   > `PlainTextFileType` **by design**: `AbstractFileViewProvider.createFile` returns null
   > with no `ParserDefinition` and falls back to `PsiPlainTextFileImpl`, which
   > force-overwrites `myFileType`. See [ADR 0003](../adr/0003-parser-definition-timing.md)
   > F1. The `VirtualFile`'s file type and the view provider's **base** language are the
   > assertions that stay true across the M5 transition.
3. `assertEquals("tsp", TypeSpecFileType.INSTANCE.defaultExtension)`
4. `assertNotNull(TypeSpecFileType.INSTANCE.icon)` — catches a missing/misnamed SVG resource,
   which otherwise fails only at runtime.

## Done when

```bash
./gradlew build test --tests '*TypeSpecFileTypeTest*'
```
passes, and `./gradlew verifyPlugin` is still clean.

## Risks

- `fieldName="INSTANCE"` + Kotlin: without `@JvmStatic` the field is not where the platform
  looks and registration fails silently at runtime (the test above catches it).
- Icon path is absolute-from-resources-root and starts with `/`.

---

# M2 — Token types and JFlex lexer

**Goal.** A generated, restartable lexer emitting the full token set. No colours yet.

## Build wiring (ADR 0002 D6)

Add to `build.gradle.kts`:

```kotlin
val jflex: Configuration by configurations.creating
dependencies { jflex("org.jetbrains.intellij.deps.jflex:jflex:1.10.17") }

val generateTypeSpecLexer by tasks.registering(JavaExec::class) {
    val flexFile = layout.projectDirectory.file("src/main/grammars/_TypeSpecLexer.flex")
    val outDir   = layout.buildDirectory.dir("generated/sources/jflex/simpli/fyi/plugins/typespec/lexer")
    inputs.file(flexFile)
    outputs.dir(outDir)
    classpath = jflex
    mainClass = "jflex.Main"
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("-d", outDir.get().asFile.absolutePath, "--nobak", flexFile.asFile.absolutePath)
    })
}

sourceSets.main { java.srcDir(generateTypeSpecLexer.map { layout.buildDirectory.dir("generated/sources/jflex") }) }
tasks.named("compileKotlin") { dependsOn(generateTypeSpecLexer) }
```

Notes for `tsp-dev`:
- The artifact resolves from the JetBrains `intellij-dependencies` repo, already present via
  `defaultRepositories()`. If it 404s, add `intellijDependencies()` explicitly to
  `settings.gradle.kts`.
- Use `argumentProviders`, not `args`, so the task stays **configuration-cache compatible**
  (the template enables the config cache).
- The JetBrains JFlex fork applies the IntelliJ lexer skeleton by default. If the generated
  class does not implement `com.intellij.lexer.FlexLexer`, that assumption is wrong —
  download `idea-flex.skeleton`, commit it under `src/main/grammars/`, and add
  `--skel <path>`. Report which path you took.
- Add `build/` to `.gitignore` (already there from M0). Never edit the generated `.java`.

## `src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecTokenType.kt`

```kotlin
class TypeSpecTokenType(debugName: String) : IElementType(debugName, TypeSpecLanguage.INSTANCE) {
    override fun toString() = "TypeSpec:" + super.toString()
}
```
The `toString()` prefix makes `LexerTestCase` output readable and stable — do not omit it,
the expected strings in M2's tests depend on it.

## `src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecTokenTypes.kt`

One `@JvmField val` per row. Complete list, with the exact debug name to use:

**Trivia / comments**
| Constant | Debug name | Matches |
|---|---|---|
| `LINE_COMMENT` | `LINE_COMMENT` | `//…` |
| `BLOCK_COMMENT` | `BLOCK_COMMENT` | `/*…*/` (non-nesting) |
| `DOC_COMMENT` | `DOC_COMMENT` | `/**…*/` |

**Literals**
| `STRING` | `STRING` | `"…"` incl. `${}` |
| `MULTILINE_STRING` | `MULTILINE_STRING` | `"""…"""` |
| `VALID_ESCAPE` | `VALID_ESCAPE` | `\"` `\\` `\n` `\r` `\t` `\$` `\``  |
| `INVALID_ESCAPE` | `INVALID_ESCAPE` | `\` + anything else |
| `NUMBER` | `NUMBER` | decimal / hex / binary |

**Names**
| `IDENTIFIER` | `IDENTIFIER` | incl. `` `backticked` `` and `null` |
| `KEYWORD` | `KEYWORD` | every word in the Keywords map, active + reserved |

**Metadata**
| `DECORATOR` | `DECORATOR` | `@name`, `@Ns.name` |
| `AUGMENT_DECORATOR` | `AUGMENT_DECORATOR` | `@@name`, `@@Ns.name` |
| `DIRECTIVE` | `DIRECTIVE` | `#suppress`, `#deprecated` |

**Punctuation** (separate constants — the parser in M5 needs them individually)
| `LBRACE` `{` | `RBRACE` `}` | `LPAREN` `(` | `RPAREN` `)` | `LBRACKET` `[` | `RBRACKET` `]` |
| `HASH_BRACE` `#{` | `HASH_BRACKET` `#[` | `HASH` `#` | `SEMICOLON` `;` | `COMMA` `,` |
| `DOT` `.` | `ELLIPSIS` `...` | `COLON` `:` | `COLON_COLON` `::` | `AT` `@` | `AT_AT` `@@` |

**Operators**
| `LT` `<` | `GT` `>` | `LE` `<=` | `GE` `>=` | `EQ` `=` | `EQ_EQ` `==` | `NE` `!=` |
| `ARROW` `=>` | `AMP` `&` | `AMP_AMP` `&&` | `BAR` `\|` | `BAR_BAR` `\|\|` | `QUESTION` `?` |
| `EXCL` `!` | `PLUS` `+` | `MINUS` `-` | `STAR` `*` | `SLASH` `/` |

Whitespace uses `com.intellij.psi.TokenType.WHITE_SPACE`; unmatched input uses
`com.intellij.psi.TokenType.BAD_CHARACTER`. Do **not** define your own for these.

## `src/main/kotlin/simpli/fyi/plugins/typespec/psi/TypeSpecTokenSets.kt`

```kotlin
object TypeSpecTokenSets {
    val COMMENTS  = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT)
    val STRINGS   = TokenSet.create(STRING, MULTILINE_STRING)
    val KEYWORDS  = TokenSet.create(KEYWORD)
    val BRACES    = TokenSet.create(LBRACE, RBRACE, HASH_BRACE)
    val PARENS    = TokenSet.create(LPAREN, RPAREN)
    val BRACKETS  = TokenSet.create(LBRACKET, RBRACKET, HASH_BRACKET)
    val OPERATORS = TokenSet.create(LT, GT, LE, GE, EQ, EQ_EQ, NE, ARROW, AMP, AMP_AMP,
                                    BAR, BAR_BAR, QUESTION, EXCL, PLUS, MINUS, STAR, SLASH,
                                    ELLIPSIS, COLON_COLON)
    val DECORATORS = TokenSet.create(DECORATOR, AUGMENT_DECORATOR, AT, AT_AT)
}
```
M3, M4 and M5 all consume these; defining them here avoids duplicating literal lists.

## `src/main/grammars/_TypeSpecLexer.flex`

Header:
```
package simpli.fyi.plugins.typespec.lexer;
import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes;

%%
%class _TypeSpecLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}
```

Lexer **states** (`%state`): `YYINITIAL`, `STRING_S`, `MULTILINE_STRING_S`, `BACKTICK_S`,
`BLOCK_COMMENT_S`, `DOC_COMMENT_S`.

Rules, **in this order** (order is the correctness contract):

1. Whitespace → `TokenType.WHITE_SPACE`
2. `"/**/"` → `BLOCK_COMMENT` (before the doc rule)
3. `"/**"` → enter `DOC_COMMENT_S`; `"/*"` → enter `BLOCK_COMMENT_S`; `"//"[^\r\n]*` → `LINE_COMMENT`
4. `"\"\"\""` → enter `MULTILINE_STRING_S`; `"\""` → enter `STRING_S`
5. `` "`" `` → enter `BACKTICK_S`
6. `"@@"` + optional dotted name → `AUGMENT_DECORATOR`; `"@"` + optional dotted name → `DECORATOR`;
   bare `"@@"` → `AT_AT`; bare `"@"` → `AT`
7. `"#{"` → `HASH_BRACE`; `"#["` → `HASH_BRACKET`; `"#"{IdentifierStart}{IdentifierPart}*` → `DIRECTIVE`; `"#"` → `HASH`
8. Numbers: `0x[0-9a-fA-F]+`, `0b[01]+`, `[0-9]+(\.[0-9]+)?(e[+-]?[0-9]+)?`
9. Keywords — all 66 literals, each `→ KEYWORD`
10. Identifier `{IdentifierStart}{IdentifierPart}*` → `IDENTIFIER`
11. Multi-char operators before single-char: `...` `::` `<=` `>=` `==` `!=` `=>` `&&` `||`
12. Single-char punctuation and operators
13. `[^]` → `TokenType.BAD_CHARACTER` — **this rule must exist and must be last**

Macros:
```
IdentifierStart = [:jletter:] | "_" | "$"
IdentifierPart  = [:jletterdigit:] | "_" | "$"
```

State bodies:
- `STRING_S`: `\\[^]` → `VALID_ESCAPE` if in the allowed set else `INVALID_ESCAPE`
  (two rules: an explicit `\\[\"\\nrt$`]` and a catch-all `\\[^]`); `\"` → pop, `STRING`;
  `[\r\n]` → pop, `STRING` (unterminated); `<<EOF>>` → pop, `STRING`; everything else consumed.
- `MULTILINE_STRING_S`: `\"\"\"` → pop, `MULTILINE_STRING`; newlines are content;
  `<<EOF>>` → pop, `MULTILINE_STRING`.
- `BACKTICK_S`: `` ` `` → pop, `IDENTIFIER`; `[\r\n]` or `<<EOF>>` → pop, `IDENTIFIER`.
- `BLOCK_COMMENT_S` / `DOC_COMMENT_S`: `"*/"` → pop with the respective token;
  `<<EOF>>` → pop with the respective token. **No nesting.**

Hard rules — violating any of these ships an editor freeze:
- Every state must handle EOF and return a token rather than looping.
- No rule may match the empty string.
- The lexer must hold **no** mutable field beyond what JFlex generates; it is restarted from
  arbitrary offsets on every keystroke.

Simplification accepted here: emitting one `STRING` token per literal means multi-line
strings and unterminated strings re-lex from the start of the literal. That is fine for a
`FlexAdapter`.

## `src/main/kotlin/simpli/fyi/plugins/typespec/lexer/TypeSpecLexerAdapter.kt`

```kotlin
class TypeSpecLexerAdapter : FlexAdapter(_TypeSpecLexer(null))
```

## Acceptance — `tsp-tester` writes

`src/test/kotlin/simpli/fyi/plugins/typespec/lexer/TypeSpecLexerTest.kt`, extending
`com.intellij.testFramework.LexerTestCase` with `createLexer() = TypeSpecLexerAdapter()` and
`getDirPath()` unused (inline expected strings).

Required cases — one test method each:

| Test | Input | Must assert |
|---|---|---|
| keywords | `model op interface enum union alias scalar dec fn extern const init` | every token `TypeSpec:KEYWORD` |
| reserved keywords | `struct trait macro satisfies` | all `TypeSpec:KEYWORD` |
| `null` is an identifier ⚠ | `null` | `TypeSpec:IDENTIFIER`, **not** KEYWORD |
| booleans | `true false` | `TypeSpec:KEYWORD` |
| identifiers | `Widget foo_bar $x` | `TypeSpec:IDENTIFIER` |
| backtick identifier | `` `if` `` | one `TypeSpec:IDENTIFIER` spanning all 4 chars |
| unterminated backtick | `` `if `` + EOF | one `IDENTIFIER`, no `BAD_CHARACTER`, terminates |
| string | `"hello"` | one `TypeSpec:STRING` |
| string escapes | `"a\nb\qc"` | `STRING`, `VALID_ESCAPE`, `STRING`, `INVALID_ESCAPE`, `STRING` |
| interpolation is opaque | `"${a}"` | a single `STRING` token (ADR 0001) |
| unterminated string | `"abc` + EOF | one `STRING`, terminates |
| triple-quoted | `"""a\n"b"\nc"""` | one `TypeSpec:MULTILINE_STRING` |
| unterminated triple | `"""abc` + EOF | one `MULTILINE_STRING`, terminates |
| numbers | `0 42 1.5 1.5e-3 0x1f 0b1010` | each one `TypeSpec:NUMBER` |
| ⚠ signed number | `-1` | `MINUS` then `NUMBER` (two tokens, by design) |
| ⚠ uppercase exponent | `1E5` | `NUMBER("1")`, `IDENTIFIER("E5")` — matches the scanner |
| line comment | `// x` | `LINE_COMMENT` |
| block comment | `/* a * b */` | one `BLOCK_COMMENT` |
| empty block comment ⚠ | `/**/` | one `BLOCK_COMMENT`, not DOC_COMMENT |
| doc comment | `/** @param x */` | one `DOC_COMMENT` |
| unterminated block comment | `/* abc` + EOF | one `BLOCK_COMMENT`, terminates |
| decorator | `@doc` | one `TypeSpec:DECORATOR` |
| qualified decorator | `@Http.route` | one `DECORATOR` |
| augment decorator ⚠ | `@@doc` | one `AUGMENT_DECORATOR`, **not** two DECORATORs |
| directive | `#suppress "x" "y"` | `DIRECTIVE`, `WS`, `STRING`, `WS`, `STRING` |
| ⚠ object value | `#{ a: 1 }` | `HASH_BRACE` (one token), not `HASH` + `LBRACE` |
| ⚠ array value | `#[1, 2]` | `HASH_BRACKET` |
| ellipsis vs dot | `...A.b` | `ELLIPSIS`, `IDENTIFIER`, `DOT`, `IDENTIFIER` |
| operators | `<= >= == != => && \|\| :: ? \| &` | one token each, correct types |
| bad character | `€` (outside identifiers) | `BAD_CHARACTER`, and the lexer advances |

Plus `src/test/testData/lexer/kitchen-sink.tsp` — use the fixture snippet in the
`typespec-language` skill verbatim, extended with `#{ }`, `#[ ]`, `0x1f`, `null`, `=>`, `::`,
and an escape sequence. Assert: lexing reaches EOF, produces **zero** `BAD_CHARACTER`
tokens, and the concatenation of all token texts equals the original file content
(the offset-coverage invariant — this catches dropped characters, which are otherwise
invisible).

Add a restartability test: lex the kitchen sink, then for each token boundary call
`lexer.start(text, offset, text.length, state)` with the recorded state and confirm the
remaining token stream matches. `LexerTestCase` may provide a helper; if not, write it
manually — this is the single most valuable test in the milestone.

## Done when

```bash
./gradlew clean build test --tests '*TypeSpecLexerTest*'
```

## Risks / open questions

1. Does the JetBrains JFlex fork apply the IntelliJ skeleton without `--skel`? Verify in the
   first build; fallback documented above.
2. `[:jletter:]` is Java's definition, not TypeSpec's Unicode identifier definition. Close
   enough for M2; note the divergence and revisit only if a real file breaks.
3. Kotlin + generated Java in the same module: the generated lexer is Java, so
   `compileKotlin` must depend on lexer generation *and* the Java source dir must be on the
   main source set. If Kotlin cannot see the Java class, check `sourceSets.main { java.srcDir(...) }`
   ran before evaluation.

---

# M3 — Syntax highlighter and colour settings page

**Goal.** Colours on screen, configurable, theme-safe.

## `src/main/kotlin/simpli/fyi/plugins/typespec/highlighting/TypeSpecColors.kt`

Every key is created with `TextAttributesKey.createTextAttributesKey(externalName, fallback)`.
**Never hard-code RGB.** External names are permanent (they are written into user colour
schemes) — pick them once.

| `TypeSpecColors` field | External name | Fallback (`DefaultLanguageHighlighterColors.*` unless noted) | Fed by token types |
|---|---|---|---|
| `KEYWORD` | `TSP_KEYWORD` | `KEYWORD` | `KEYWORD` |
| `IDENTIFIER` | `TSP_IDENTIFIER` | `IDENTIFIER` | `IDENTIFIER` |
| `STRING` | `TSP_STRING` | `STRING` | `STRING` |
| `MULTILINE_STRING` | `TSP_MULTILINE_STRING` | `STRING` | `MULTILINE_STRING` |
| `VALID_ESCAPE` | `TSP_VALID_ESCAPE` | `VALID_STRING_ESCAPE` | `VALID_ESCAPE` |
| `INVALID_ESCAPE` | `TSP_INVALID_ESCAPE` | `INVALID_STRING_ESCAPE` | `INVALID_ESCAPE` |
| `NUMBER` | `TSP_NUMBER` | `NUMBER` | `NUMBER` |
| `LINE_COMMENT` | `TSP_LINE_COMMENT` | `LINE_COMMENT` | `LINE_COMMENT` |
| `BLOCK_COMMENT` | `TSP_BLOCK_COMMENT` | `BLOCK_COMMENT` | `BLOCK_COMMENT` |
| `DOC_COMMENT` | `TSP_DOC_COMMENT` | `DOC_COMMENT` | `DOC_COMMENT` |
| `DECORATOR` | `TSP_DECORATOR` | `METADATA` | `DECORATOR`, `AUGMENT_DECORATOR`, `AT`, `AT_AT` |
| `DIRECTIVE` | `TSP_DIRECTIVE` | `METADATA` | `DIRECTIVE`, `HASH` |
| `BRACES` | `TSP_BRACES` | `BRACES` | `LBRACE`, `RBRACE`, `HASH_BRACE` |
| `PARENTHESES` | `TSP_PARENTHESES` | `PARENTHESES` | `LPAREN`, `RPAREN` |
| `BRACKETS` | `TSP_BRACKETS` | `BRACKETS` | `LBRACKET`, `RBRACKET`, `HASH_BRACKET` |
| `SEMICOLON` | `TSP_SEMICOLON` | `SEMICOLON` | `SEMICOLON` |
| `COMMA` | `TSP_COMMA` | `COMMA` | `COMMA` |
| `DOT` | `TSP_DOT` | `DOT` | `DOT` |
| `OPERATOR` | `TSP_OPERATOR` | `OPERATION_SIGN` | everything in `TypeSpecTokenSets.OPERATORS`, plus `COLON` and `EQ` |
| `BAD_CHARACTER` | `TSP_BAD_CHARACTER` | `HighlighterColors.BAD_CHARACTER` | `TokenType.BAD_CHARACTER` |

`AUGMENT_DECORATOR` deliberately shares `TSP_DECORATOR` — a separate colour for `@@` would be
noise. If a user asks for it later, splitting is a one-line change because the token types
are already distinct.

Also declare `val EMPTY: Array<TextAttributesKey> = emptyArray()` for the whitespace case.

## `TypeSpecSyntaxHighlighter.kt`

```kotlin
class TypeSpecSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = TypeSpecLexerAdapter()
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = ...
}
```
Implement the map as a `private val keys: Map<IElementType, Array<TextAttributesKey>>`
built once in a companion object, using `SyntaxHighlighterBase.pack(...)`, and return
`keys[tokenType] ?: EMPTY`. A `when` chain also works; the map makes M3's
"every token type is coloured" test trivially satisfiable and is easier to keep in sync.

`TokenType.WHITE_SPACE` → `EMPTY`.

## `TypeSpecSyntaxHighlighterFactory.kt`

```kotlin
class TypeSpecSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) =
        TypeSpecSyntaxHighlighter()
}
```

## `TypeSpecColorSettingsPage.kt`

- `getIcon()` → `TypeSpecIcons.FILE`
- `getHighlighter()` → `TypeSpecSyntaxHighlighter()`
- `getDemoText()` → a self-contained snippet exercising **every** descriptor. Base it on
  `kitchen-sink.tsp`. Keep it in the Kotlin source as a raw string, or load it from
  `/colorSettings/demo.tsp.txt` — either is fine; loading from a resource lets M3's test
  assert demo-text and fixture stay in sync.
- `getAdditionalHighlightingTagToDescriptorMap()` → `null` (no semantic tags until M6)
- `getAttributeDescriptors()` → one `AttributesDescriptor(displayName, key)` per row of the
  table above. Display names use IntelliJ's `//`-separated grouping convention, e.g.
  `"Comments//Line comment"`, `"Braces and operators//Braces"`, `"Metadata//Decorator"`.
- `getColorDescriptors()` → `ColorDescriptor.EMPTY_ARRAY`
- `getDisplayName()` → `"TypeSpec"`

## `plugin.xml` additions

```xml
<lang.syntaxHighlighterFactory
    language="TypeSpec"
    implementationClass="simpli.fyi.plugins.typespec.highlighting.TypeSpecSyntaxHighlighterFactory"/>
<colorSettingsPage
    implementation="simpli.fyi.plugins.typespec.highlighting.TypeSpecColorSettingsPage"/>
```

## Acceptance — `tsp-tester` writes

`src/test/kotlin/simpli/fyi/plugins/typespec/highlighting/TypeSpecSyntaxHighlighterTest.kt`:

1. **Total coverage.** Reflect over `TypeSpecTokenTypes`' fields (or keep an explicit `ALL`
   TokenSet in `TypeSpecTokenSets` and assert against it); for each, assert
   `highlighter.getTokenHighlights(t)` is non-empty. Fails the moment M5 adds a token type
   without a colour.
2. **Whitespace maps to nothing.** `getTokenHighlights(TokenType.WHITE_SPACE)` is empty.
3. **Bad character is coloured.** `getTokenHighlights(TokenType.BAD_CHARACTER)` contains
   `HighlighterColors.BAD_CHARACTER` (via the `TSP_BAD_CHARACTER` fallback).
4. **Page ↔ highlighter agreement.** The set of `TextAttributesKey`s reachable from
   `getTokenHighlights` over all token types **equals** the set in
   `TypeSpecColorSettingsPage().attributeDescriptors.map { it.key }`. Both directions —
   catches an orphaned descriptor and an uncoloured token.
5. **External-name stability.** Assert the exact external-name strings from the table.
   Renaming one silently resets users' customised colours; this test makes that a deliberate act.
6. **Demo text is well-formed.** Lex `TypeSpecColorSettingsPage().demoText`; assert it
   reaches EOF with zero `BAD_CHARACTER`, and that every descriptor's key is actually
   produced by some token in it.

`src/test/kotlin/simpli/fyi/plugins/typespec/highlighting/TypeSpecHighlightingTest.kt`
(`BasePlatformTestCase`, `getTestDataPath() = "src/test/testData"`):

7. **End-to-end editor colouring, via `EditorHighlighterFactory`.**
   `myFixture.configureByFile("highlighting/basic.tsp")`, then:

   ```kotlin
   val vFile = myFixture.file.virtualFile
   val scheme = EditorColorsManager.getInstance().globalScheme
   val highlighter = EditorHighlighterFactory.getInstance()
       .createEditorHighlighter(vFile, scheme, project)
   highlighter.setText(myFixture.editor.document.charsSequence)
   val it = highlighter.createIterator(0)
   while (!it.atEnd()) { /* assert it.tokenType / it.textAttributesKeys / ranges */ ; it.advance() }
   ```

   Assert at minimum: the iterator reaches the end of the document; every non-whitespace
   token carries a non-empty `textAttributesKeys`; no token is `BAD_CHARACTER`; and a few
   spot-checked offsets (a keyword, a decorator, a string, a comment) carry the expected
   `TextAttributesKey`. This is the only test that proves the whole
   `FileType → SyntaxHighlighterFactory → LexerEditorHighlighter` chain is wired.

   Fixture `src/test/testData/highlighting/basic.tsp` = the kitchen sink, no annotation tags.

   > **Two APIs are banned in this milestone** — see
   > [ADR 0003](../adr/0003-parser-definition-timing.md) F3/D4:
   >
   > - **`myFixture.checkHighlighting(...)`** is driven by `HighlightInfo` from annotators
   >   and inspections. A lexer-only language produces none, so the call is *vacuous*: it can
   >   never fail and proves nothing about colouring.
   > - **`EditorTestUtil.testFileSyntaxHighlighting`** — recommended by the SDK docs and
   >   **actively wrong here**. It resolves the highlighter through `testFile.getFileType()`,
   >   which is `PlainTextFileType` with no `ParserDefinition` (F1). It would silently use
   >   `PlainSyntaxHighlighter` and assert against empty output.
   >
   > Note the factory call above takes the **`VirtualFile`**, whose `fileType` is still
   > `TypeSpecFileType`. That is precisely why it works and `EditorTestUtil` does not.

## Done when

```bash
./gradlew clean build test verifyPlugin
```
is green, **and** the manual check is reported: `./gradlew runIde`, open a `.tsp` file,
confirm colouring, switch Light ↔ Darcula, confirm no unreadable token, confirm
*Settings | Editor | Color Scheme | TypeSpec* lists every descriptor and live-previews edits.

## Risks / open questions

1. **Cosmetic, needs a look in `runIde`:** `DECORATOR` and `DIRECTIVE` both fall back to
   `METADATA`, so `@doc` and `#suppress` render identically. If that reads badly, candidates
   for `DIRECTIVE` are `DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL` or
   `DefaultLanguageHighlighterColors.CONSTANT`. **Do not change it without looking first** —
   the external name is permanent, the fallback is not.
2. `MULTILINE_STRING` falling back to `STRING` is intentional; some languages give
   triple-quoted strings their own key. Revisit after `runIde`.
3. ~~Whether `checkHighlighting` needs a registered `ParserDefinition`.~~ **Resolved** by
   `tsp-intellij-researcher` against ideaIC-2025.2.6.3 — see
   [ADR 0003](../adr/0003-parser-definition-timing.md). Summary: editor highlighting works
   fine with no `ParserDefinition` (the `EditorHighlighterFactory` path never touches PSI),
   so **M3 ships as-is**; but `checkHighlighting` is vacuous and
   `EditorTestUtil.testFileSyntaxHighlighting` is silently broken, hence the rewritten
   acceptance test 7 above. No `ParserDefinition` is added here or in M4; it is M5's first task.
4. `AttributesDescriptor` display names are user-visible; if the plugin is ever localised
   they must move to a message bundle. M0 deleted the template's bundle; recreating one is
   out of scope here.
