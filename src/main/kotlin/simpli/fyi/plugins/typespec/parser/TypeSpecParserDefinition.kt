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

/**
 * Minimal, flat `ParserDefinition` (ADR 0005 M4b, amending ADR 0003 D1/D2). This exists
 * only so `.tsp` files get a real [TypeSpecFile] PSI (and therefore a file language of
 * `TypeSpec` instead of the `PsiPlainTextFileImpl` fallback described in ADR 0003 F1) —
 * language-keyed extension points such as `lang.commenter` resolve by *file language*, not
 * file type, and were silently dead without this.
 *
 * The parser body ([TypeSpecFlatParser]) is a **transitional placeholder, not a permanent
 * design**: it wraps every lexer token as a flat leaf with no grammar and no composite
 * element hierarchy. It is explicitly owned by M5 Task 0, which replaces it with the
 * generated Grammar-Kit parser. Per ADR 0003 F4 (binding, unrelaxed by ADR 0005): this class
 * must never subclass or delegate to `PlainTextParserDefinition` — that reintroduces exactly
 * the `PsiPlainTextFileImpl` fallback this class exists to avoid.
 */
class TypeSpecParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = TypeSpecLexerAdapter()

    override fun createParser(project: Project?): PsiParser = TypeSpecFlatParser()

    override fun getFileNodeType(): IFileElementType = TypeSpecElementTypes.FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.WHITE_SPACE

    override fun getCommentTokens(): TokenSet = TypeSpecTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = TypeSpecTokenSets.STRINGS

    override fun createElement(node: ASTNode): PsiElement =
        throw UnsupportedOperationException(
            "TypeSpecParserDefinition has no composite element types yet (ADR 0005 M4b) — " +
                "the flat parser never produces a node other than the file root. Encountering " +
                "this means M5's real grammar is needed, not a silently-wrong PSI element."
        )

    override fun createFile(viewProvider: FileViewProvider): PsiFile = TypeSpecFile(viewProvider)
}
