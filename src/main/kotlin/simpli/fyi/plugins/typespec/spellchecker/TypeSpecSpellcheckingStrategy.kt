package simpli.fyi.plugins.typespec.spellchecker

import com.intellij.psi.PsiElement
import com.intellij.spellchecker.inspections.CommentSplitter
import com.intellij.spellchecker.inspections.IdentifierSplitter
import com.intellij.spellchecker.inspections.TextSplitter
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer
import com.intellij.spellchecker.tokenizer.TokenizerBase
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenSets
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes

/**
 * `spellchecker.support` for `.tsp` files (plan 03 M5c — ✅ RATIFIED, ships here; ADR 0006 F10,
 * ADR 0003 D3). Lives in `com.intellij.modules.spellchecker`, the plugin's second `<depends>`.
 *
 * The PSI tree built in this milestone is a flat, `ASTWrapperPsiElement`-based tree (ADR 0005
 * M4b) — there is no dedicated `PsiComment` class or string-literal PSI class to key off of, so
 * dispatch here is by raw lexer token type instead of by PSI element class:
 *  - comment tokens ([TypeSpecTokenSets.COMMENTS]) get [CommentSplitter] (handles `@param`-style
 *    doc-comment markup the same way other JetBrains language plugins do for doc comments);
 *  - string-literal tokens ([TypeSpecTokenSets.STRINGS]) get [TextSplitter] (plain natural-language
 *    text, no camelCase/snake_case splitting);
 *  - [TypeSpecTokenTypes.IDENTIFIER] leaves (every name, ordinary or backticked — see
 *    `_TypeSpecLexer.flex`) get [IdentifierSplitter], which splits camelCase/snake_case
 *    identifiers into individual words before spell-checking each one.
 * Everything else defers to [SpellcheckingStrategy.getTokenizer]'s default (`EMPTY_TOKENIZER`).
 */
class TypeSpecSpellcheckingStrategy : SpellcheckingStrategy() {

    private val commentTokenizer: Tokenizer<PsiElement> = TokenizerBase.create(CommentSplitter.getInstance())
    private val stringTokenizer: Tokenizer<PsiElement> = TokenizerBase.create(TextSplitter.getInstance())
    private val identifierTokenizer: Tokenizer<PsiElement> = TokenizerBase.create(IdentifierSplitter.getInstance())

    override fun getTokenizer(element: PsiElement): Tokenizer<*> {
        val elementType = element.node?.elementType ?: return super.getTokenizer(element)
        return when {
            TypeSpecTokenSets.COMMENTS.contains(elementType) -> commentTokenizer
            TypeSpecTokenSets.STRINGS.contains(elementType) -> stringTokenizer
            elementType == TypeSpecTokenTypes.IDENTIFIER -> identifierTokenizer
            else -> super.getTokenizer(element)
        }
    }
}
