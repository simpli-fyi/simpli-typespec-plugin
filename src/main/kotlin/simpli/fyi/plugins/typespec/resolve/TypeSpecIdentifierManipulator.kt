package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier

/**
 * Registered but deliberately throwing
 * ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D6,
 * [plan 02](../../../../../../../../docs/plans/02-navigation.md) "Files to create" §
 * `resolve/TypeSpecIdentifierManipulator.kt`).
 *
 * `PsiReferenceBase.handleElementRename` routes through `ElementManipulators`, and with **no**
 * manipulator registered for [TypeSpecIdentifier] the platform logs a confusing "no manipulator"
 * error from unrelated code paths. A registered manipulator that throws a clear message is the
 * honest state until M6.5 implements rename.
 */
class TypeSpecIdentifierManipulator : AbstractElementManipulator<TypeSpecIdentifier>() {
    override fun handleContentChange(
        element: TypeSpecIdentifier,
        range: TextRange,
        newContent: String,
    ): TypeSpecIdentifier =
        throw IncorrectOperationException("Rename is not supported until M6.5 (ADR 0004 D6)")
}
