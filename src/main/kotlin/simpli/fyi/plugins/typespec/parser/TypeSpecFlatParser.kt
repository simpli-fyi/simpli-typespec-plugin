package simpli.fyi.plugins.typespec.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Transitional flat parser (ADR 0005 M4b, owned by M5 Task 0). It does not build a real
 * parse tree: it opens the root marker, consumes every lexer token as a flat leaf directly
 * under the file node, and closes it. No grammar, no composite element types, no PSI element
 * hierarchy. This exists solely so a `ParserDefinition` can exist at all (needed to make file
 * language resolve to TypeSpec — see ADR 0005) without pretending to have real syntax
 * structure. M5 Task 0 replaces this class's body with the generated Grammar-Kit parser.
 */
class TypeSpecFlatParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        marker.done(root)
        return builder.treeBuilt
    }
}
