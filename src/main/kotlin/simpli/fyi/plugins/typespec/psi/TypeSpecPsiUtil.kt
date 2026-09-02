package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Plain Kotlin helper functions shared by [simpli.fyi.plugins.typespec.psi.impl.TypeSpecNamedElementMixin].
 * Deliberately **not** a `psiImplUtilClass` — it is never referenced from `TypeSpec.bnf`'s
 * `methods=[...]` (banned repo-wide, ADR 0006 D7/F8); the mixin calls these as ordinary Kotlin
 * functions on its own classpath, so there is no generator-classpath resolution involved at all.
 */
object TypeSpecPsiUtil {

    /**
     * Finds the [TypeSpecIdentifier] that names a declaration, for both shapes M5b's named
     * elements come in:
     *  - a direct [TypeSpecIdentifier] child (`model_statement`, `model_property`,
     *    `template_parameter`) — checked *first*, since `model_property` also has a direct
     *    [TypeSpecQualifiedName] child (its *type*, e.g. `string`) that must not be mistaken
     *    for the name.
     *  - a direct [TypeSpecQualifiedName] child only (`namespace_statement`, dotted
     *    `namespace Foo.Bar.Baz`) — the **last** segment is the name, per
     *    [ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D7.
     */
    fun findNameIdentifier(element: PsiElement): TypeSpecIdentifier? {
        // Direct `TypeSpecIdentifier` child first: `model_statement`, `model_property`, and
        // `template_parameter` all have one directly (the declaration's own name) — for
        // `model_property` specifically, checking `TypeSpecQualifiedName` first would
        // wrongly resolve to the property's *type* (e.g. `string`), which is also a direct
        // child. Only `namespace_statement` has no direct identifier at all (dotted
        // `namespace Foo.Bar` is *only* a qualified name), so it falls through below.
        val identifier = PsiTreeUtil.getChildOfType(element, TypeSpecIdentifier::class.java)
        if (identifier != null) {
            return identifier
        }
        val qualifiedName = PsiTreeUtil.getChildOfType(element, TypeSpecQualifiedName::class.java)
        return qualifiedName?.let {
            PsiTreeUtil.getChildrenOfTypeAsList(it, TypeSpecIdentifier::class.java).lastOrNull()
        }
    }

    /**
     * Strips surrounding backticks from a name's text (`` `foo` `` -> `foo`), leaving a bare
     * name untouched. A backticked identifier is always at least two characters (the two
     * backticks); anything shorter cannot be a backtick pair and is returned as-is.
     */
    fun stripBackticks(text: String?): String? {
        if (text == null) return null
        return if (text.length >= 2 && text.startsWith("`") && text.endsWith("`")) {
            text.substring(1, text.length - 1)
        } else {
            text
        }
    }
}
