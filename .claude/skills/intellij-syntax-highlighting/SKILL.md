---
name: intellij-syntax-highlighting
description: The component-by-component recipe for adding a custom language with syntax highlighting to IntelliJ IDEA Community — Language, FileType, JFlex lexer, TokenTypes, SyntaxHighlighter, ColorSettingsPage, and the plugin.xml registrations. Use whenever implementing or debugging lexers, highlighters, token types, file type registration, or color scheme attributes.
---

# Custom language syntax highlighting in IntelliJ (Community-safe)

Verify exact signatures against the SDK docs before coding — ask `tsp-intellij-researcher`.
This skill is the *shape* of the solution and the checklist; it is not a substitute for the
current API reference.

## The minimum set of classes

Highlighting works with a **lexer only** — no parser, no PSI required. That's the first
milestone.

1. **`TypeSpecLanguage : Language("TypeSpec")`** — singleton, `INSTANCE`.
2. **`TypeSpecFileType : LanguageFileType(TypeSpecLanguage.INSTANCE)`** — name, description,
   default extension `tsp`, icon (16x16 SVG in `src/main/resources/icons/`).
3. **`TypeSpecTokenType : IElementType`** and a `TypeSpecTokenTypes` holder with one constant
   per lexical class (see `typespec-language` for the list).
4. **`_TypeSpecLexer.flex`** — JFlex spec in `src/main/grammars/`, wrapped by
   `TypeSpecLexerAdapter : FlexAdapter`.
5. **`TypeSpecSyntaxHighlighter : SyntaxHighlighterBase`** — returns the lexer and maps each
   `IElementType` to `TextAttributesKey[]`.
6. **`TypeSpecSyntaxHighlighterFactory : SyntaxHighlighterFactory`** — the thing actually
   registered in `plugin.xml`.
7. **`TypeSpecColorSettingsPage : ColorSettingsPage`** — makes the colors user-configurable
   under Settings | Editor | Color Scheme. Cheap, and reviewers expect it.

## plugin.xml registrations

```xml
<extensions defaultExtensionNs="com.intellij">
  <fileType name="TypeSpec" implementationClass="...TypeSpecFileType"
            fieldName="INSTANCE" language="TypeSpec" extensions="tsp"/>
  <lang.syntaxHighlighterFactory language="TypeSpec"
            implementationClass="...TypeSpecSyntaxHighlighterFactory"/>
  <colorSettingsPage implementation="...TypeSpecColorSettingsPage"/>
</extensions>
```

A class that isn't in `plugin.xml` does nothing at runtime and fails silently. Register in the
same change as the class.

## Colors: derive, don't invent

Map token types onto `DefaultLanguageHighlighterColors` so the language respects every user
theme:

```kotlin
val KEYWORD = createTextAttributesKey("TSP_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
val STRING  = createTextAttributesKey("TSP_STRING", DefaultLanguageHighlighterColors.STRING)
val COMMENT = createTextAttributesKey("TSP_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
val DECORATOR = createTextAttributesKey("TSP_DECORATOR", DefaultLanguageHighlighterColors.METADATA)
val NUMBER  = createTextAttributesKey("TSP_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
```

Never hard-code RGB values for the base token classes.

## Lexer rules that bite

- Return `TokenType.BAD_CHARACTER` for unmatched input — never let the lexer throw or hang.
  A lexer that can loop without consuming input freezes the editor.
- Whitespace must be `TokenType.WHITE_SPACE`.
- The lexer must be **restartable and incremental**: it is re-run from arbitrary offsets on
  every keystroke. Use JFlex states for nested/multi-line constructs (block comments, triple
  quoted strings) rather than external mutable state.
- Handle EOF inside every state — unterminated string/comment must produce a token, not an error.
- Generated lexer is a build artifact (Gradle `generateLexer`); edit the `.flex`, never the
  generated `.java`.

## Later milestones (do NOT do these in the highlighting milestone)

- `ParserDefinition` + Grammar-Kit `.bnf` → PSI, needed for structure view, resolve,
  find-usages, semantic highlighting.
- `Annotator` for semantic coloring (e.g. distinguishing model names from decorators).
- `BraceMatcher`, `Commenter` (`//`, `/* */`), `FoldingBuilder`, `SpellcheckingStrategy`.

## Community-edition gate

Everything above is `com.intellij.modules.platform` only. If a proposed approach requires
`com.intellij.platform.lsp.*`, that's Ultimate-only — the alternative for LSP is the
third-party **LSP4IJ** plugin dependency, and that is an architecture decision, not a
drive-by change.
