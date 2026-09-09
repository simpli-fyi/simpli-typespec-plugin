package simpli.fyi.plugins.typespec.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecDecoratorReferenceHost

/**
 * The single place that turns already-escaped text into a detached [TypeSpecIdentifier] node
 * (ADR 0012 D4). Every write path — [simpli.fyi.plugins.typespec.resolve.TypeSpecIdentifierManipulator]
 * and, through it, `TypeSpecNamedElementMixin.setName` — funnels through [createIdentifier];
 * there is one escaper ([TypeSpecNames.escape]) and one factory that trusts its output.
 */
object TypeSpecElementFactory {

    /**
     * Builds a throwaway, non-physical `.tsp` file from `model <text> {}` and extracts its sole
     * [TypeSpecIdentifier]. [text] must already be the escaped output of [TypeSpecNames.escape] —
     * this factory never escapes.
     *
     * The three checks below each throw [IncorrectOperationException] naming [text]: this is the
     * mechanism that proves the text about to be written lexes as exactly one `IDENTIFIER` token,
     * not an approximation of it.
     *
     * @throws IncorrectOperationException if the dummy file fails to parse cleanly, does not
     *   contain exactly one [TypeSpecIdentifier], or that identifier's text is not exactly [text].
     */
    fun createIdentifier(project: Project, text: String): TypeSpecIdentifier {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("__tsp_rename_dummy__.tsp", TypeSpecFileType.INSTANCE, "model $text {}")

        if (PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) {
            throw IncorrectOperationException("\"$text\" does not parse as a TypeSpec identifier")
        }

        val identifiers = PsiTreeUtil.findChildrenOfType(file, TypeSpecIdentifier::class.java)
        if (identifiers.size != 1) {
            throw IncorrectOperationException(
                "\"$text\" did not produce exactly one identifier (found ${identifiers.size})",
            )
        }

        val identifier = identifiers.first()
        if (identifier.text != text) {
            throw IncorrectOperationException("\"$text\" was not consumed in full (got \"${identifier.text}\")")
        }

        return identifier
    }

    /**
     * Builds a throwaway, non-physical `.tsp` file containing exactly one `DECORATOR` or
     * `AUGMENT_DECORATOR` token and extracts that token's own PSI leaf (M6.5j, plan 07, ADR
     * 0012 D4). [text] is the whole `@Ns.name` / `@@Ns.name` string, already spliced with the
     * new segment — this factory never escapes and never splices; it only proves the result
     * re-lexes as one token, the same guarantee [createIdentifier] gives for identifiers.
     *
     * The dummy source differs by token kind because `_TypeSpecLexer.flex`'s two decorator
     * patterns head two different grammar productions
     * ([TypeSpecDecoratorReferenceHost]'s KDoc): a bare `DECORATOR` prefixes a declaration
     * (`decorator_application`), while `AUGMENT_DECORATOR` heads its own statement
     * (`augment_decorator_statement`) with a parenthesised argument list. [isAugment] is the
     * caller's own token-type check ([simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes.AUGMENT_DECORATOR]
     * vs `DECORATOR`) — never guessed from [text]'s `@`/`@@` prefix, so a mismatch fails loudly
     * instead of silently trying the wrong dummy shape.
     *
     * @throws IncorrectOperationException if the dummy file fails to parse cleanly, does not
     *   contain exactly one decorator token, or that token's text is not exactly [text].
     */
    fun createDecoratorToken(project: Project, text: String, isAugment: Boolean): PsiElement {
        val dummySource = if (isAugment) "$text(\"x\");" else "$text model Dummy {}"
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("__tsp_rename_dummy__.tsp", TypeSpecFileType.INSTANCE, dummySource)

        if (PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) {
            throw IncorrectOperationException("\"$text\" does not parse as a TypeSpec decorator")
        }

        val hosts = PsiTreeUtil.findChildrenOfType(file, TypeSpecDecoratorReferenceHost::class.java)
        if (hosts.size != 1) {
            throw IncorrectOperationException(
                "\"$text\" did not produce exactly one decorator usage (found ${hosts.size})",
            )
        }

        val tokenNode = hosts.first().node.findChildByType(DECORATOR_TOKENS)
            ?: throw IncorrectOperationException("\"$text\" did not produce a decorator token")
        val token = tokenNode.psi
        if (token.text != text) {
            throw IncorrectOperationException("\"$text\" was not consumed in full (got \"${token.text}\")")
        }

        return token
    }

    private val DECORATOR_TOKENS: TokenSet =
        TokenSet.create(TypeSpecTokenTypes.DECORATOR, TypeSpecTokenTypes.AUGMENT_DECORATOR)
}
