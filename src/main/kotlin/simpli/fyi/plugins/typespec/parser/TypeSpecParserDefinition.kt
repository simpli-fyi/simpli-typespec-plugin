package simpli.fyi.plugins.typespec.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import simpli.fyi.plugins.typespec.lexer.TypeSpecLexerAdapter
import simpli.fyi.plugins.typespec.psi.TypeSpecElementTypes
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenSets
import simpli.fyi.plugins.typespec.psi.TypeSpecTypes

/**
 * `ParserDefinition` for `.tsp` files (ADR 0005 M4b; real grammar wired in M5b). Gives `.tsp`
 * files a real [TypeSpecFile] PSI backed by the generated Grammar-Kit parser instead of the
 * `PsiPlainTextFileImpl` fallback described in ADR 0003 F1 — language-keyed extension points
 * such as `lang.commenter` resolve by *file language*, not file type, and were silently dead
 * without this.
 *
 * `getFileNodeType()`, `createFile`, `getCommentTokens`, `getStringLiteralElements` and
 * `getWhitespaceTokens` are unchanged from M4b (ADR 0006 F7: `TypeSpecElementTypes.FILE` stays
 * the same `IFileElementType` *instance* the generated parser's root rule (`typespec_file`) is
 * handed at parse time — Grammar-Kit never generates its own file element type).
 */
class TypeSpecParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = TypeSpecLexerAdapter()

    override fun createParser(project: Project?): PsiParser = TypeSpecParser()

    override fun getFileNodeType(): IFileElementType = TypeSpecElementTypes.FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.WHITE_SPACE

    override fun getCommentTokens(): TokenSet = TypeSpecTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = TypeSpecTokenSets.STRINGS

    override fun createElement(node: ASTNode): PsiElement = TypeSpecTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = TypeSpecFile(viewProvider)
}
