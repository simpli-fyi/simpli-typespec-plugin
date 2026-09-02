package dev.tsp.intellij.typespec.psi

import com.intellij.psi.tree.TokenSet
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.AMP
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.AMP_AMP
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.ARROW
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.AT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.AT_AT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.AUGMENT_DECORATOR
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.BAR
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.BAR_BAR
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.BLOCK_COMMENT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.COLON_COLON
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.DECORATOR
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.DOC_COMMENT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.ELLIPSIS
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.EQ
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.EQ_EQ
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.EXCL
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.GE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.GT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.HASH_BRACE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.HASH_BRACKET
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.KEYWORD
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.LBRACE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.LBRACKET
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.LE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.LINE_COMMENT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.LPAREN
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.LT
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.MINUS
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.MULTILINE_STRING
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.NE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.PLUS
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.QUESTION
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.RBRACE
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.RBRACKET
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.RPAREN
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.SLASH
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.STAR
import dev.tsp.intellij.typespec.psi.TypeSpecTokenTypes.STRING

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
