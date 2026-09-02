package simpli.fyi.plugins.typespec.editor

import com.intellij.lang.Commenter

/**
 * Line (`//`) and block (`/* */`) comment support for `.tsp` files.
 *
 * Not PSI-driven — works with the lexer-only setup shipped through M4
 * (see ADR 0003 F5).
 */
class TypeSpecCommenter : Commenter {

    override fun getLineCommentPrefix(): String = "//"

    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
