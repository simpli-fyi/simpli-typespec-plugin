package simpli.fyi.plugins.typespec.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes

/**
 * Pairs `{}`, `()`, `[]`, and the value-literal openers `#{` / `#[` (which close with
 * plain `}` / `]` per the lexer, see `_TypeSpecLexer.flex`).
 *
 * `BraceMatchingUtil.getBraceMatcher` resolves via `LanguageBraceMatching.forLanguage` off
 * `IElementType.getLanguage()`, which holds here because every [TypeSpecTokenTypes] constant
 * is a `TypeSpecTokenType` constructed with `TypeSpecLanguage.INSTANCE`. ADR 0003 D5's "at
 * risk" status is resolved by ADR 0005 D4: with `TypeSpecParserDefinition` now landed, file
 * language resolves correctly and brace matching is automated-test-verified
 * (`TypeSpecBraceMatcherTest`), not merely asserted as likely.
 */
class TypeSpecBraceMatcher : PairedBraceMatcher {

    private val pairs = arrayOf(
        BracePair(TypeSpecTokenTypes.LBRACE, TypeSpecTokenTypes.RBRACE, true),
        BracePair(TypeSpecTokenTypes.LPAREN, TypeSpecTokenTypes.RPAREN, false),
        BracePair(TypeSpecTokenTypes.LBRACKET, TypeSpecTokenTypes.RBRACKET, false),
        BracePair(TypeSpecTokenTypes.HASH_BRACE, TypeSpecTokenTypes.RBRACE, true),
        BracePair(TypeSpecTokenTypes.HASH_BRACKET, TypeSpecTokenTypes.RBRACKET, false),
    )

    override fun getPairs(): Array<BracePair> = pairs

    override fun isPairedBracesAllowedBeforeType(
        lbraceType: IElementType,
        contextType: IElementType?,
    ): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
