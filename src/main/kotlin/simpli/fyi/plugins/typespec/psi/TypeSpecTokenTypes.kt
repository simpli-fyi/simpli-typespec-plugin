package simpli.fyi.plugins.typespec.psi

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
}
