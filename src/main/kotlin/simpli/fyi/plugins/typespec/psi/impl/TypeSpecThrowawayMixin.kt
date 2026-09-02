package simpli.fyi.plugins.typespec.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import simpli.fyi.plugins.typespec.psi.TypeSpecThrowawayIface

/**
 * Hand-written base class proving the Grammar-Kit `mixin=` single-pass pattern works
 * across the Kotlin/generated-Java seam (ADR 0006 D7/F8, D10.1). Grammar-Kit only ever
 * sees this class name as a *string* attribute and emits an `extends` clause from it —
 * it never needs this class on the generator's own classpath, unlike `methods=[...]`
 * or `psiImplUtilClass`, which are banned in this repo's `.bnf` files for exactly that
 * reason (both resolve via `AsmHelper` reflection against the generator's classpath,
 * which cannot contain classes this Gradle build has not compiled yet).
 *
 * The method required by [TypeSpecThrowawayIface] is implemented directly here, as an
 * ordinary Kotlin method — Grammar-Kit is not asked to know it exists.
 *
 * Removed in M5b, which replaces this throwaway seam check with the real
 * `TypeSpecNamedElementMixin` (ADR 0004 D7).
 */
abstract class TypeSpecThrowawayMixin(node: ASTNode) :
    ASTWrapperPsiElement(node),
    TypeSpecThrowawayIface {

    override fun throwawaySeamMarker(): String = "seam-ok"
}
