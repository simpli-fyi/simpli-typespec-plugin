---
name: typespec-language
description: Reference for the TypeSpec (.tsp) language surface — keywords, literals, decorators, directives, comments, operators — and the token categories a lexer/highlighter must produce. Use when defining token types, writing the JFlex spec, building test fixtures, or deciding how a construct should be colored.
---

# TypeSpec language surface for lexing

TypeSpec is Microsoft's API description language (`.tsp`), the successor to Cadl.
**Ground truth**, in this order — verify against it before finalizing the token set:

- `microsoft/typespec` → `packages/compiler/src/core/scanner.ts` (the real token enum)
- `microsoft/typespec` → the TextMate grammar (`grammars/typespec.json`) — a ready-made
  mapping of constructs to scopes, useful as a cross-check for what deserves its own color
- https://typespec.io/docs/ — language reference

Treat the list below as a starting point to verify, not as authoritative.

## Token categories to emit

| Category | Examples |
|---|---|
| Keyword | `import`, `using`, `namespace`, `model`, `op`, `interface`, `enum`, `union`, `alias`, `scalar`, `dec`, `fn`, `extern`, `const`, `is`, `extends`, `valueof`, `typeof`, `return`, `void`, `never`, `unknown` |
| Boolean / null literal | `true`, `false`, `null` |
| Identifier | `foo`, and **backtick-quoted** identifiers: `` `if` ``, `` `has space` `` |
| Decorator | `@doc`, `@service` — and **augment decorators** `@@doc` |
| Directive | `#suppress "..."`, `#deprecated "..."` — start with `#`, line-scoped |
| String | `"..."` with `\` escapes; **triple-quoted** `"""..."""` multi-line; string *templates* with `${expr}` interpolation |
| Number | integers, decimals, exponents, hex/binary if the scanner supports them |
| Line comment | `// ...` |
| Block comment | `/* ... */` |
| Doc comment | `/** ... */` — color separately from block comments; contains `@param`/`@returns` tags |
| Punctuation | `{ } ( ) [ ] < > , ; : =` |
| Operators / sigils | `?` (optional), `...` (spread), `\|` (union), `&` (intersection), `/` (path sep in imports), `.` (member) |

## Constructs that trip lexers

1. **`@` vs `@@`** — augment decorators must not lex as two decorators.
2. **`<` `>`** — template parameters (`model Page<T>`), not comparison. A lexer-only
   highlighter treats them as punctuation; disambiguation is a parser-milestone concern.
3. **Triple-quoted strings** — `"""` opens a distinct lexer state; a lone `"` inside is
   content. Indentation of the closing `"""` is significant to the compiler but not to coloring.
4. **String interpolation** `${...}` — decide explicitly: color the whole literal as a string
   (simplest, fine for M1) or emit sub-tokens for the interpolation. Record the choice.
5. **Backtick identifiers** — must lex as identifier, not as a string.
6. **Block comments do not nest** in most C-family scanners — verify TypeSpec's behavior in
   `scanner.ts` rather than assuming.
7. **Directives** (`#suppress`) look like nothing else in the language; give them their own token.

## Fixture snippet for tests

```tsp
import "@typespec/http";
using Http;

@service(#{ title: "Widget Service" })
namespace DemoService;

/** A widget. */
@doc("the widget model")
model Widget<T extends string> {
  id: string;
  weight?: int32 = 10;
  `if`: boolean;
  description: string | null;
  ...CommonProps;
}

@@doc(Widget.id, "the id");

#suppress "deprecated" "legacy"
op read(@path id: string): Widget<string> | Error;

enum Color { Red: "red", Green: "green" }

const text = """
  multi
  line
""";
// trailing line comment
/* block
   comment */
```
