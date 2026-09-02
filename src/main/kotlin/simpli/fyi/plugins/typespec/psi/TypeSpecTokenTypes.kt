package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.tree.IElementType

object TypeSpecTokenTypes {

    // Trivia / comments
    @JvmField val LINE_COMMENT = TypeSpecTokenType("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = TypeSpecTokenType("BLOCK_COMMENT")
    @JvmField val DOC_COMMENT = TypeSpecTokenType("DOC_COMMENT")

    // Literals
    @JvmField val STRING = TypeSpecTokenType("STRING")
    @JvmField val MULTILINE_STRING = TypeSpecTokenType("MULTILINE_STRING")
    @JvmField val VALID_ESCAPE = TypeSpecTokenType("VALID_ESCAPE")
    @JvmField val INVALID_ESCAPE = TypeSpecTokenType("INVALID_ESCAPE")
    @JvmField val NUMBER = TypeSpecTokenType("NUMBER")

    // Names
    @JvmField val IDENTIFIER = TypeSpecTokenType("IDENTIFIER")
    @JvmField val KEYWORD = TypeSpecTokenType("KEYWORD")

    // Metadata
    @JvmField val DECORATOR = TypeSpecTokenType("DECORATOR")
    @JvmField val AUGMENT_DECORATOR = TypeSpecTokenType("AUGMENT_DECORATOR")
    @JvmField val DIRECTIVE = TypeSpecTokenType("DIRECTIVE")

    // Punctuation
    @JvmField val LBRACE = TypeSpecTokenType("LBRACE")
    @JvmField val RBRACE = TypeSpecTokenType("RBRACE")
    @JvmField val LPAREN = TypeSpecTokenType("LPAREN")
    @JvmField val RPAREN = TypeSpecTokenType("RPAREN")
    @JvmField val LBRACKET = TypeSpecTokenType("LBRACKET")
    @JvmField val RBRACKET = TypeSpecTokenType("RBRACKET")
    @JvmField val HASH_BRACE = TypeSpecTokenType("HASH_BRACE")
    @JvmField val HASH_BRACKET = TypeSpecTokenType("HASH_BRACKET")
    @JvmField val HASH = TypeSpecTokenType("HASH")
    @JvmField val SEMICOLON = TypeSpecTokenType("SEMICOLON")
    @JvmField val COMMA = TypeSpecTokenType("COMMA")
    @JvmField val DOT = TypeSpecTokenType("DOT")
    @JvmField val ELLIPSIS = TypeSpecTokenType("ELLIPSIS")
    @JvmField val COLON = TypeSpecTokenType("COLON")
    @JvmField val COLON_COLON = TypeSpecTokenType("COLON_COLON")
    @JvmField val AT = TypeSpecTokenType("AT")
    @JvmField val AT_AT = TypeSpecTokenType("AT_AT")

    // Operators
    @JvmField val LT = TypeSpecTokenType("LT")
    @JvmField val GT = TypeSpecTokenType("GT")
    @JvmField val LE = TypeSpecTokenType("LE")
    @JvmField val GE = TypeSpecTokenType("GE")
    @JvmField val EQ = TypeSpecTokenType("EQ")
    @JvmField val EQ_EQ = TypeSpecTokenType("EQ_EQ")
    @JvmField val NE = TypeSpecTokenType("NE")
    @JvmField val ARROW = TypeSpecTokenType("ARROW")
    @JvmField val AMP = TypeSpecTokenType("AMP")
    @JvmField val AMP_AMP = TypeSpecTokenType("AMP_AMP")
    @JvmField val BAR = TypeSpecTokenType("BAR")
    @JvmField val BAR_BAR = TypeSpecTokenType("BAR_BAR")
    @JvmField val QUESTION = TypeSpecTokenType("QUESTION")
    @JvmField val EXCL = TypeSpecTokenType("EXCL")
    @JvmField val PLUS = TypeSpecTokenType("PLUS")
    @JvmField val MINUS = TypeSpecTokenType("MINUS")
    @JvmField val STAR = TypeSpecTokenType("STAR")
    @JvmField val SLASH = TypeSpecTokenType("SLASH")

    // ADR 0006 D5/F6: the bridge a Grammar-Kit `tokenTypeFactory` calls into so the
    // generated parser references the *same* IElementType instances the JFlex lexer
    // above already emits, instead of minting new (and therefore never-matching)
    // duplicates. Keyed by debug name for every constant above; literal-token *text*
    // keys (e.g. "{" -> LBRACE) are added alongside the real `.bnf` literal tokens
    // that need them (M5b/M5c) — none exist yet, since M5a's throwaway rule only
    // references IDENTIFIER, a regexp token keyed by name.
    private val byKey: Map<String, IElementType> = mapOf(
        "LINE_COMMENT" to LINE_COMMENT,
        "BLOCK_COMMENT" to BLOCK_COMMENT,
        "DOC_COMMENT" to DOC_COMMENT,
        "STRING" to STRING,
        "MULTILINE_STRING" to MULTILINE_STRING,
        "VALID_ESCAPE" to VALID_ESCAPE,
        "INVALID_ESCAPE" to INVALID_ESCAPE,
        "NUMBER" to NUMBER,
        "IDENTIFIER" to IDENTIFIER,
        "KEYWORD" to KEYWORD,
        "DECORATOR" to DECORATOR,
        "AUGMENT_DECORATOR" to AUGMENT_DECORATOR,
        "DIRECTIVE" to DIRECTIVE,
        "LBRACE" to LBRACE,
        "RBRACE" to RBRACE,
        "LPAREN" to LPAREN,
        "RPAREN" to RPAREN,
        "LBRACKET" to LBRACKET,
        "RBRACKET" to RBRACKET,
        "HASH_BRACE" to HASH_BRACE,
        "HASH_BRACKET" to HASH_BRACKET,
        "HASH" to HASH,
        "SEMICOLON" to SEMICOLON,
        "COMMA" to COMMA,
        "DOT" to DOT,
        "ELLIPSIS" to ELLIPSIS,
        "COLON" to COLON,
        "COLON_COLON" to COLON_COLON,
        "AT" to AT,
        "AT_AT" to AT_AT,
        "LT" to LT,
        "GT" to GT,
        "LE" to LE,
        "GE" to GE,
        "EQ" to EQ,
        "EQ_EQ" to EQ_EQ,
        "NE" to NE,
        "ARROW" to ARROW,
        "AMP" to AMP,
        "AMP_AMP" to AMP_AMP,
        "BAR" to BAR,
        "BAR_BAR" to BAR_BAR,
        "QUESTION" to QUESTION,
        "EXCL" to EXCL,
        "PLUS" to PLUS,
        "MINUS" to MINUS,
        "STAR" to STAR,
        "SLASH" to SLASH,
    )

    /**
     * Resolves a token *name* (or, for literal tokens once any exist, its literal
     * *text*) to the identical [IElementType] instance the JFlex lexer emits.
     * **Never** mints a new instance — a silently-minted duplicate would produce a
     * parser that can never match its own lexer, with a symptom (every node is an
     * error element) that points nowhere near the cause (ADR 0006 D5).
     *
     * `@JvmStatic` is mandatory: the generated code is Java calling into this Kotlin
     * `object` statically (ADR 0006 D5).
     */
    @JvmStatic
    fun fromNameOrText(key: String): IElementType =
        byKey[key] ?: throw IllegalArgumentException("Unknown TypeSpec token key: $key")
}
