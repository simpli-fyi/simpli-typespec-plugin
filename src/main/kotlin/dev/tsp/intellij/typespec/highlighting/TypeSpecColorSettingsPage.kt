package dev.tsp.intellij.typespec.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.tsp.intellij.typespec.TypeSpecBundle
import dev.tsp.intellij.typespec.TypeSpecIcons
import javax.swing.Icon

class TypeSpecColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon = TypeSpecIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = TypeSpecSyntaxHighlighter()

    override fun getDemoText(): String =
        TypeSpecColorSettingsPage::class.java.getResourceAsStream("/colorSettings/demo.tsp.txt")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Missing resource /colorSettings/demo.tsp.txt")

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = TypeSpecBundle.message("color.page.displayName")

    companion object {
        private val DESCRIPTORS: Array<AttributesDescriptor> = arrayOf(
            AttributesDescriptor(TypeSpecBundle.message("color.comments.line"), TypeSpecColors.LINE_COMMENT),
            AttributesDescriptor(TypeSpecBundle.message("color.comments.block"), TypeSpecColors.BLOCK_COMMENT),
            AttributesDescriptor(TypeSpecBundle.message("color.comments.doc"), TypeSpecColors.DOC_COMMENT),

            AttributesDescriptor(TypeSpecBundle.message("color.strings.string"), TypeSpecColors.STRING),
            AttributesDescriptor(TypeSpecBundle.message("color.strings.multilineString"), TypeSpecColors.MULTILINE_STRING),
            AttributesDescriptor(TypeSpecBundle.message("color.strings.validEscape"), TypeSpecColors.VALID_ESCAPE),
            AttributesDescriptor(TypeSpecBundle.message("color.strings.invalidEscape"), TypeSpecColors.INVALID_ESCAPE),

            AttributesDescriptor(TypeSpecBundle.message("color.keyword"), TypeSpecColors.KEYWORD),
            AttributesDescriptor(TypeSpecBundle.message("color.identifier"), TypeSpecColors.IDENTIFIER),
            AttributesDescriptor(TypeSpecBundle.message("color.number"), TypeSpecColors.NUMBER),

            AttributesDescriptor(TypeSpecBundle.message("color.metadata.decorator"), TypeSpecColors.DECORATOR),
            AttributesDescriptor(TypeSpecBundle.message("color.metadata.directive"), TypeSpecColors.DIRECTIVE),

            AttributesDescriptor(TypeSpecBundle.message("color.bracesAndOperators.braces"), TypeSpecColors.BRACES),
            AttributesDescriptor(TypeSpecBundle.message("color.bracesAndOperators.parentheses"), TypeSpecColors.PARENTHESES),
            AttributesDescriptor(TypeSpecBundle.message("color.bracesAndOperators.brackets"), TypeSpecColors.BRACKETS),
            AttributesDescriptor(TypeSpecBundle.message("color.bracesAndOperators.semicolon"), TypeSpecColors.SEMICOLON),
            AttributesDescriptor(TypeSpecBundle.message("color.bracesAndOperators.comma"), TypeSpecColors.COMMA),
            AttributesDescriptor(TypeSpecBundle.message("color.bracesAndOperators.dot"), TypeSpecColors.DOT),
            AttributesDescriptor(TypeSpecBundle.message("color.bracesAndOperators.operator"), TypeSpecColors.OPERATOR),

            AttributesDescriptor(TypeSpecBundle.message("color.badCharacter"), TypeSpecColors.BAD_CHARACTER),
        )
    }
}
