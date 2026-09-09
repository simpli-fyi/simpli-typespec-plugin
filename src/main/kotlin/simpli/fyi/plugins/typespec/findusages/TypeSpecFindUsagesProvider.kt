package simpli.fyi.plugins.typespec.findusages

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.tree.TokenSet
import simpli.fyi.plugins.typespec.lexer.TypeSpecLexerAdapter
import simpli.fyi.plugins.typespec.psi.TypeSpecAliasStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecEnumStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecInterfaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecModelProperty
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecOpStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecScalarStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecTemplateParameter
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenSets
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes
import simpli.fyi.plugins.typespec.psi.TypeSpecUnionStatement

/**
 * Find Usages for TypeSpec declarations
 * ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D6,
 * [plan 02](../../../../../../../../docs/plans/02-navigation.md)).
 *
 * Requires **no** working [simpli.fyi.plugins.typespec.resolve.TypeSpecIdentifierManipulator] —
 * that manipulator only backs `PsiReference.handleElementRename` (rename, M6.5). Find Usages
 * runs `ReferencesSearch` (word index prefilter, then `PsiReference.isReferenceTo()` per
 * candidate — both already implemented by [simpli.fyi.plugins.typespec.resolve.TypeSpecReference]
 * / [simpli.fyi.plugins.typespec.resolve.TypeSpecResolver] since M5.5a) and never touches the
 * manipulator. This class alone is what turns on the *provider* side — without it, "Find Usages"
 * on a `.tsp` declaration reports "Cannot search for usages".
 */
class TypeSpecFindUsagesProvider : FindUsagesProvider {

    // Platform contract: "MUST be thread-safe, otherwise you should return a new instance of
    // your scanner" — DefaultWordsScanner wraps a mutable Lexer, so a fresh instance is
    // returned on every call, never hoisted into a field or companion object (ADR 0004 § Post-
    // M4b review item 6).
    override fun getWordsScanner(): WordsScanner =
        DefaultWordsScanner(
            TypeSpecLexerAdapter(),
            TokenSet.create(TypeSpecTokenTypes.IDENTIFIER),
            TypeSpecTokenSets.COMMENTS,
            TypeSpecTokenSets.STRINGS,
        )

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is PsiNameIdentifierOwner

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is TypeSpecModelStatement -> "model"
        is TypeSpecEnumStatement -> "enum"
        is TypeSpecUnionStatement -> "union"
        is TypeSpecInterfaceStatement -> "interface"
        is TypeSpecAliasStatement -> "alias"
        is TypeSpecScalarStatement -> "scalar"
        is TypeSpecOpStatement -> "op"
        is TypeSpecNamespaceStatement -> "namespace"
        is TypeSpecModelProperty -> "model property"
        is TypeSpecTemplateParameter -> "template parameter"
        else -> ""
    }

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? PsiNameIdentifierOwner)?.name ?: ""

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        (element as? PsiNameIdentifierOwner)?.name ?: ""
}
