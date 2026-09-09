package simpli.fyi.plugins.typespec.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

/**
 * TextAttributesKeys for TypeSpec highlighting.
 *
 * Every key is derived from [DefaultLanguageHighlighterColors] (or
 * [HighlighterColors] for BAD_CHARACTER) via [createTextAttributesKey] so the
 * language respects every colour scheme out of the box. Never hard-code RGB
 * values here.
 *
 * The external name strings are written into users' persisted colour scheme
 * files once a user customises a colour — treat them as a permanent, public
 * API. Do not rename them.
 */
object TypeSpecColors {

    @JvmField
    val KEYWORD: TextAttributesKey =
        createTextAttributesKey("TSP_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)

    @JvmField
    val IDENTIFIER: TextAttributesKey =
        createTextAttributesKey("TSP_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)

    @JvmField
    val STRING: TextAttributesKey =
        createTextAttributesKey("TSP_STRING", DefaultLanguageHighlighterColors.STRING)

    @JvmField
    val MULTILINE_STRING: TextAttributesKey =
        createTextAttributesKey("TSP_MULTILINE_STRING", DefaultLanguageHighlighterColors.STRING)

    @JvmField
    val VALID_ESCAPE: TextAttributesKey =
        createTextAttributesKey("TSP_VALID_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)

    @JvmField
    val INVALID_ESCAPE: TextAttributesKey =
        createTextAttributesKey("TSP_INVALID_ESCAPE", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)

    @JvmField
    val NUMBER: TextAttributesKey =
        createTextAttributesKey("TSP_NUMBER", DefaultLanguageHighlighterColors.NUMBER)

    @JvmField
    val LINE_COMMENT: TextAttributesKey =
        createTextAttributesKey("TSP_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

    @JvmField
    val BLOCK_COMMENT: TextAttributesKey =
        createTextAttributesKey("TSP_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)

    @JvmField
    val DOC_COMMENT: TextAttributesKey =
        createTextAttributesKey("TSP_DOC_COMMENT", DefaultLanguageHighlighterColors.DOC_COMMENT)

    @JvmField
    val DECORATOR: TextAttributesKey =
        createTextAttributesKey("TSP_DECORATOR", DefaultLanguageHighlighterColors.METADATA)

    @JvmField
    val DIRECTIVE: TextAttributesKey =
        createTextAttributesKey("TSP_DIRECTIVE", DefaultLanguageHighlighterColors.METADATA)

    @JvmField
    val BRACES: TextAttributesKey =
        createTextAttributesKey("TSP_BRACES", DefaultLanguageHighlighterColors.BRACES)

    @JvmField
    val PARENTHESES: TextAttributesKey =
        createTextAttributesKey("TSP_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)

    @JvmField
    val BRACKETS: TextAttributesKey =
        createTextAttributesKey("TSP_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)

    @JvmField
    val SEMICOLON: TextAttributesKey =
        createTextAttributesKey("TSP_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)

    @JvmField
    val COMMA: TextAttributesKey =
        createTextAttributesKey("TSP_COMMA", DefaultLanguageHighlighterColors.COMMA)

    @JvmField
    val DOT: TextAttributesKey =
        createTextAttributesKey("TSP_DOT", DefaultLanguageHighlighterColors.DOT)

    @JvmField
    val OPERATOR: TextAttributesKey =
        createTextAttributesKey("TSP_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)

    @JvmField
    val BAD_CHARACTER: TextAttributesKey =
        createTextAttributesKey("TSP_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

    @JvmField
    val EMPTY: Array<TextAttributesKey> = emptyArray()
}
