package simpli.fyi.plugins.typespec.editor

import com.intellij.lang.Commenter

/**
 * Line (`//`) and block (`/* */`) comment support for `.tsp` files.
 *
 * Resolved via `LanguageCommenters.forLanguage(file.getLanguage())`, which is PSI-driven:
 * it requires the file's language to actually be `TypeSpec`, not the `PlainTextLanguage`
 * fallback that occurs without a `ParserDefinition` (ADR 0003 F1). See
 * `TypeSpecParserDefinition` and ADR 0005, which corrects ADR 0003 F5's "not PSI-driven"
 * claim — that claim was false and this class was non-functional until the
 * `ParserDefinition` landed.
 */
class TypeSpecCommenter : Commenter {

    override fun getLineCommentPrefix(): String = "//"

    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
