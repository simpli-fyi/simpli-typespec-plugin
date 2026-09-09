package simpli.fyi.plugins.typespec.psi

/**
 * Every word `src/main/grammars/_TypeSpecLexer.flex` lexes as `KEYWORD` — active TypeSpec
 * keywords *and* reserved-but-unused ones (`macro`, `struct`, `trait`, `record`, `module`,
 * `prop`, `pub`, `sub`, …) alike. This is a hand-maintained mirror of the `.flex`, not a
 * reference to the TypeSpec language spec: a word missing here means [TypeSpecNames.escape]
 * would write it bare, and the lexer would then split it into a `KEYWORD` token rather than a
 * single `IDENTIFIER` (ADR 0012 D3).
 *
 * `TypeSpecKeywordsGuardTest` parses the `.flex` file and asserts this set stays in lockstep
 * with it in both directions.
 *
 * Verbatim, in source order, from `_TypeSpecLexer.flex` lines 79-145.
 */
object TypeSpecKeywords {

    val ALL: Set<String> = setOf(
        "import",
        "model",
        "scalar",
        "namespace",
        "interface",
        "union",
        "if",
        "else",
        "projection",
        "using",
        "op",
        "extends",
        "is",
        "enum",
        "alias",
        "dec",
        "fn",
        "valueof",
        "typeof",
        "const",
        "init",
        "true",
        "false",
        "return",
        "void",
        "never",
        "unknown",
        "extern",
        "auto",
        "internal",
        "statemachine",
        "macro",
        "package",
        "metadata",
        "env",
        "arg",
        "declare",
        "array",
        "struct",
        "record",
        "module",
        "mod",
        "sym",
        "context",
        "prop",
        "property",
        "scenario",
        "pub",
        "sub",
        "typeref",
        "trait",
        "this",
        "self",
        "super",
        "keyof",
        "with",
        "implements",
        "impl",
        "satisfies",
        "flag",
        "partial",
        "private",
        "public",
        "protected",
        "sealed",
        "local",
        "async",
    )
}
