package simpli.fyi.plugins.typespec.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import com.intellij.psi.tree.TokenSet
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes
import simpli.fyi.plugins.typespec.resolve.TypeSpecDecoratorReference

/**
 * Hand-written base class satisfying `getReferences()` for both `decorator_application`
 * (`@Ns.name`) and `augment_decorator_statement` (`@@Ns.name`)
 * ([ADR 0009](../../../../../../../../../docs/adr/0009-decorator-reference-strategy.md) option B,
 * [plan 05](../../../../../../../../../docs/plans/05-import-and-decorator-navigation.md) M5.6d).
 * Grammar-Kit only ever sees this class name as a *string* `mixin=` attribute — same technique
 * as [TypeSpecImportStatementMixin] and [TypeSpecNamedElementMixin] — never loaded by the
 * generator itself.
 *
 * `_TypeSpecLexer.flex` keeps emitting all of `@Ns.name` / `@@Ns.name` as **one**
 * `DECORATOR`/`AUGMENT_DECORATOR` token, unchanged. This class instead splits that token's own
 * text on `.` (after dropping the `@`/`@@` prefix — 1 or 2 characters, told apart by the token's
 * element type, never hardcoded per rule) and returns one [TypeSpecDecoratorReference] per
 * segment, each with a `rangeInElement` that skips the prefix and every `.` separator. Both
 * rules share this one class — no per-rule subclass — because the token is found generically
 * (by [TypeSpecTokenTypes.DECORATOR]/[TypeSpecTokenTypes.AUGMENT_DECORATOR] element type, via
 * [ASTNode.findChildByType]), never through either rule's own generated typed accessor.
 *
 * Adding `mixin=` here does not rename either generated `...Impl` class or its element type, so
 * no parser goldens change (ADR 0009 §Consequences) — the highlighting path is untouched too:
 * this class does not implement `SyntaxHighlighter`, and nothing under
 * `src/main/kotlin/**/highlighting/` is part of this milestone's diff.
 */
abstract class TypeSpecDecoratorReferenceHost(node: ASTNode) : ASTWrapperPsiElement(node) {

    override fun getReferences(): Array<PsiReference> {
        val tokenNode = node.findChildByType(DECORATOR_TOKENS) ?: return PsiReference.EMPTY_ARRAY
        val token = tokenNode.psi
        // Both grammar productions put the token first
        // (`decorator_application ::= DECORATOR decorator_argument_list?`,
        // `augment_decorator_statement ::= AUGMENT_DECORATOR '(' ...`) — computed, not assumed
        // (plan 05 M5.6d risk note).
        val tokenOffset = token.startOffsetInParent
        check(tokenOffset == 0) {
            "decorator token expected to be this node's first child (ADR 0009 option B)"
        }

        val prefixLength = if (tokenNode.elementType === TypeSpecTokenTypes.AUGMENT_DECORATOR) 2 else 1
        val text = token.text
        if (text.length <= prefixLength) return PsiReference.EMPTY_ARRAY

        // One pass: collect (name, range) per dotted segment, skipping the prefix and every '.'.
        val segments = mutableListOf<Pair<String, TextRange>>()
        var segmentStart = prefixLength
        var i = prefixLength
        while (i <= text.length) {
            if (i == text.length || text[i] == '.') {
                if (i > segmentStart) {
                    segments += text.substring(segmentStart, i) to TextRange(tokenOffset + segmentStart, tokenOffset + i)
                }
                segmentStart = i + 1
            }
            i++
        }
        if (segments.isEmpty()) return PsiReference.EMPTY_ARRAY

        val names = segments.map { it.first }
        return segments.mapIndexed { index, (_, range) ->
            TypeSpecDecoratorReference(this, range, names, index)
        }.toTypedArray()
    }

    private companion object {
        val DECORATOR_TOKENS: TokenSet = TokenSet.create(TypeSpecTokenTypes.DECORATOR, TypeSpecTokenTypes.AUGMENT_DECORATOR)
    }
}
