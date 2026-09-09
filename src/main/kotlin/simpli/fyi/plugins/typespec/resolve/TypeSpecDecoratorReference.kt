package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.tree.TokenSet
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.psi.TypeSpecElementFactory
import simpli.fyi.plugins.typespec.psi.TypeSpecNames
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes

/**
 * One dotted segment of a `@Ns.name` / `@@Ns.name` decorator
 * ([ADR 0009](../../../../../../../../docs/adr/0009-decorator-reference-strategy.md) option B,
 * [plan 05](../../../../../../../../docs/plans/05-import-and-decorator-navigation.md) M5.6d).
 * [element] is the `decorator_application`/`augment_decorator_statement` host node —
 * [TypeSpecDecoratorReferenceHost] hangs one instance of this class per segment on it, each with
 * its own [range]. [names] is the full already-split, already-stripped segment list (decorator
 * segments cannot be backticked); [index] is which of them this reference resolves.
 *
 * `TypeSpec.<caret>OpenAPI.info` resolves the "OpenAPI" segment (index 1) to the namespace;
 * `TypeSpec.OpenAPI.<caret>info` resolves "info" (index 2) to the `extern dec info` declaration
 * — both through [TypeSpecResolver.multiResolve]'s name-based entry point (plan 06 M6.5c),
 * exactly the way [TypeSpecReference] resolves an `identifier` PSI segment.
 */
class TypeSpecDecoratorReference(
    element: PsiElement,
    range: TextRange,
    private val names: List<String>,
    private val index: Int,
) : PsiPolyVariantReferenceBase<PsiElement>(element, range) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, RESOLVER, /* needToPreventRecursion = */ true, incompleteCode)
            ?: ResolveResult.EMPTY_ARRAY

    // Bare standard-library decorators (`@doc`, `@key`, ...) and any decorator whose owning
    // library a file does not import never resolve — a soft reference keeps that a non-event
    // instead of painting the file red (ADR 0009 §Consequences, mirrors ADR 0004 D3).
    override fun isSoft(): Boolean = true

    override fun getVariants(): Array<Any> = emptyArray()

    /**
     * Overridden rather than left to [com.intellij.psi.PsiReferenceBase]'s default (M6.5j, plan
     * 07 §M6.5j approach 2). That default routes through `ElementManipulators.getManipulator`
     * for [getElement] — here the `decorator_application` / `augment_decorator_statement`
     * **host**, not an identifier — and no manipulator is registered for it: the whole `@Ns.name`
     * / `@@Ns.name` name lives inside a single `DECORATOR`/`AUGMENT_DECORATOR` lexer token (ADR
     * 0009 option B), so there is nothing an [com.intellij.psi.ElementManipulators]-based rename
     * could correctly target. Instead this splices [newElementName] into that token's own text
     * at [rangeInElement] — which [element]'s [simpli.fyi.plugins.typespec.psi.impl.TypeSpecDecoratorReferenceHost]
     * already computed per segment, prefix and every `.` skipped — and asks
     * [TypeSpecElementFactory.createDecoratorToken] to prove the spliced text re-lexes as
     * exactly one token before anything is written.
     *
     * A backticked segment is never representable here: the `.flex` decorator patterns are
     * `"@" {QualifiedName}` / `"@@" {QualifiedName}` over *bare* identifiers only (ADR 0012 F6).
     * If [TypeSpecNames.escape] would change [newElementName]'s spelling — i.e. it needs
     * backticks, or is otherwise unrepresentable — this throws before touching anything, so a
     * `dec`/`fn` rename to an unspellable name is a clean refusal (file byte-identical) rather
     * than a corrupt `@` usage.
     * [simpli.fyi.plugins.typespec.refactoring.TypeSpecDecoratorRenameInputValidator] exists so
     * the dialog refuses this case up front instead of failing mid-refactoring.
     */
    override fun handleElementRename(newElementName: String): PsiElement {
        if (TypeSpecNames.escape(newElementName) != newElementName) {
            throw IncorrectOperationException(
                "\"$newElementName\" needs backticks to be a valid TypeSpec name, but a " +
                    "decorator usage (\"@Ns.name\") only accepts a bare identifier",
            )
        }

        val tokenNode = element.node.findChildByType(DECORATOR_TOKENS)
            ?: throw IncorrectOperationException(
                "\"${element.text}\" has no decorator token to rename",
            )
        val token = tokenNode.psi
        val isAugment = tokenNode.elementType === TypeSpecTokenTypes.AUGMENT_DECORATOR

        // `rangeInElement` is relative to `element` (the host); TypeSpecDecoratorReferenceHost
        // asserts the token starts at offset 0 within the host, so the same range applies
        // unchanged to the token's own text.
        val newTokenText = token.text.replaceRange(rangeInElement.startOffset, rangeInElement.endOffset, newElementName)
        val replacement = TypeSpecElementFactory.createDecoratorToken(element.project, newTokenText, isAugment)
        return token.replace(replacement)
    }

    private companion object {
        val DECORATOR_TOKENS: TokenSet = TokenSet.create(
            TypeSpecTokenTypes.DECORATOR,
            TypeSpecTokenTypes.AUGMENT_DECORATOR,
        )
        val RESOLVER = ResolveCache.PolyVariantResolver<TypeSpecDecoratorReference> { ref, _ ->
            TypeSpecResolver.multiResolve(ref.names, ref.index, ref.element)
        }
    }
}
