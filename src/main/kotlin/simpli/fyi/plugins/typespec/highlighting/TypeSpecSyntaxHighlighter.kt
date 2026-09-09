package simpli.fyi.plugins.typespec.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import simpli.fyi.plugins.typespec.lexer.TypeSpecLexerAdapter
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenSets
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes
import org.jetbrains.annotations.NonNls

class TypeSpecSyntaxHighlighter : com.intellij.openapi.fileTypes.SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = TypeSpecLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        KEYS[tokenType] ?: TypeSpecColors.EMPTY

    companion object {

        private val OPERATOR_TOKENS: TokenSet = TokenSet.orSet(
            TypeSpecTokenSets.OPERATORS,
            TokenSet.create(TypeSpecTokenTypes.COLON, TypeSpecTokenTypes.EQ)
        )

        @NonNls
        private val KEYS: Map<IElementType, Array<TextAttributesKey>> = buildMap {
            put(TypeSpecTokenTypes.KEYWORD, pack(TypeSpecColors.KEYWORD))
            put(TypeSpecTokenTypes.IDENTIFIER, pack(TypeSpecColors.IDENTIFIER))

            put(TypeSpecTokenTypes.STRING, pack(TypeSpecColors.STRING))
            put(TypeSpecTokenTypes.MULTILINE_STRING, pack(TypeSpecColors.MULTILINE_STRING))
            put(TypeSpecTokenTypes.VALID_ESCAPE, pack(TypeSpecColors.VALID_ESCAPE))
            put(TypeSpecTokenTypes.INVALID_ESCAPE, pack(TypeSpecColors.INVALID_ESCAPE))
            put(TypeSpecTokenTypes.NUMBER, pack(TypeSpecColors.NUMBER))

            put(TypeSpecTokenTypes.LINE_COMMENT, pack(TypeSpecColors.LINE_COMMENT))
            put(TypeSpecTokenTypes.BLOCK_COMMENT, pack(TypeSpecColors.BLOCK_COMMENT))
            put(TypeSpecTokenTypes.DOC_COMMENT, pack(TypeSpecColors.DOC_COMMENT))

            put(TypeSpecTokenTypes.DECORATOR, pack(TypeSpecColors.DECORATOR))
            put(TypeSpecTokenTypes.AUGMENT_DECORATOR, pack(TypeSpecColors.DECORATOR))
            put(TypeSpecTokenTypes.AT, pack(TypeSpecColors.DECORATOR))
            put(TypeSpecTokenTypes.AT_AT, pack(TypeSpecColors.DECORATOR))

            put(TypeSpecTokenTypes.DIRECTIVE, pack(TypeSpecColors.DIRECTIVE))
            put(TypeSpecTokenTypes.HASH, pack(TypeSpecColors.DIRECTIVE))

            put(TypeSpecTokenTypes.LBRACE, pack(TypeSpecColors.BRACES))
            put(TypeSpecTokenTypes.RBRACE, pack(TypeSpecColors.BRACES))
            put(TypeSpecTokenTypes.HASH_BRACE, pack(TypeSpecColors.BRACES))

            put(TypeSpecTokenTypes.LPAREN, pack(TypeSpecColors.PARENTHESES))
            put(TypeSpecTokenTypes.RPAREN, pack(TypeSpecColors.PARENTHESES))

            put(TypeSpecTokenTypes.LBRACKET, pack(TypeSpecColors.BRACKETS))
            put(TypeSpecTokenTypes.RBRACKET, pack(TypeSpecColors.BRACKETS))
            put(TypeSpecTokenTypes.HASH_BRACKET, pack(TypeSpecColors.BRACKETS))

            put(TypeSpecTokenTypes.SEMICOLON, pack(TypeSpecColors.SEMICOLON))
            put(TypeSpecTokenTypes.COMMA, pack(TypeSpecColors.COMMA))
            put(TypeSpecTokenTypes.DOT, pack(TypeSpecColors.DOT))

            for (operator in OPERATOR_TOKENS.types) {
                put(operator, pack(TypeSpecColors.OPERATOR))
            }

            put(TokenType.BAD_CHARACTER, pack(TypeSpecColors.BAD_CHARACTER))
        }
    }
}
