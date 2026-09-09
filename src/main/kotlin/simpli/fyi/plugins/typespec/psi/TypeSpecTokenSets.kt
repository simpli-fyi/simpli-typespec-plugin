package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.tree.TokenSet
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.AMP
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.AMP_AMP
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.ARROW
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.AT
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.AT_AT
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.AUGMENT_DECORATOR
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.BAR
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.BAR_BAR
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.BLOCK_COMMENT
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.COLON_COLON
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.DECORATOR
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.DOC_COMMENT
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.ELLIPSIS
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.EQ
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.EQ_EQ
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.EXCL
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.GE
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.GT
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.HASH_BRACE
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.HASH_BRACKET
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.KEYWORD
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.LBRACE
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.LBRACKET
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.LE
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.LINE_COMMENT
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.LPAREN
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.LT
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.MINUS
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.MULTILINE_STRING
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.NE
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.PLUS
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.QUESTION
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.RBRACE
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.RBRACKET
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.RPAREN
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.SLASH
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.STAR
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.STRING

object TypeSpecTokenSets {
    @JvmField val COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT)
    @JvmField val STRINGS = TokenSet.create(STRING, MULTILINE_STRING)
    @JvmField val KEYWORDS = TokenSet.create(KEYWORD)
    @JvmField val BRACES = TokenSet.create(LBRACE, RBRACE, HASH_BRACE)
    @JvmField val PARENS = TokenSet.create(LPAREN, RPAREN)
    @JvmField val BRACKETS = TokenSet.create(LBRACKET, RBRACKET, HASH_BRACKET)
    @JvmField val OPERATORS = TokenSet.create(
        LT, GT, LE, GE, EQ, EQ_EQ, NE, ARROW, AMP, AMP_AMP,
        BAR, BAR_BAR, QUESTION, EXCL, PLUS, MINUS, STAR, SLASH,
        ELLIPSIS, COLON_COLON
    )
    @JvmField val DECORATORS = TokenSet.create(DECORATOR, AUGMENT_DECORATOR, AT, AT_AT)
}
