package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import simpli.fyi.plugins.typespec.psi.TypeSpecElementFactory
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier
import simpli.fyi.plugins.typespec.psi.TypeSpecNames

/**
 * The only place a [TypeSpecIdentifier]'s text is ever written (ADR 0012 D4). Registered on
 * `lang.elementManipulator` since M5.5; this class only gained a real body in M6.5g.
 *
 * Does **not** override [getRangeInElement]: the inherited default from
 * [AbstractElementManipulator] (`TextRange(0, textLength)`) is what keeps this manipulator in
 * agreement with `TypeSpecReference`, whose own `getRangeInElement()` is also
 * `TextRange(0, element.textLength)`. [ADR 0012 F1](../../../../../../../../docs/adr/0012-rename-refactoring.md)
 * — `PsiReferenceBase.handleElementRename` passes the *reference's* range, not this
 * manipulator's, so on the rename path [handleContentChange] is called with a range spanning the
 * whole identifier **including its backticks**. Narrowing the range to the inside of the
 * backticks — the idiom string-literal manipulators use — would splice two characters off every
 * backticked name.
 */
class TypeSpecIdentifierManipulator : AbstractElementManipulator<TypeSpecIdentifier>() {
    override fun handleContentChange(
        element: TypeSpecIdentifier,
        range: TextRange,
        newContent: String,
    ): TypeSpecIdentifier {
        require(range.startOffset >= 0 && range.endOffset <= element.textLength) {
            "Range $range is outside of element text \"${element.text}\""
        }
        // Splice first, escape second (ADR 0012 D2): TypeSpecNames.escape unescapes before
        // re-escaping, so this is safe whether `range` is the full identifier (rename) or a
        // sub-range (partial edit).
        val spliced = element.text.replaceRange(range.startOffset, range.endOffset, newContent)
        val newText = TypeSpecNames.escape(spliced)
        val replacement = TypeSpecElementFactory.createIdentifier(element.project, newText)
        // PsiElement.replace, NOT node.treeParent.replaceChild: `replacement` is still parented in
        // TypeSpecElementFactory's dummy file, and replace() is the documented path -- it routes
        // through ChangeUtil / CodeEditUtil, copying the node across trees, marking it generated
        // and reporting the edit to the PSI-to-document synchroniser. A raw replaceChild skips all
        // of that (ADR 0012 F13).
        //
        // Do not restore the raw call on the grounds that it appeared to work: nothing in the
        // suite would catch you. Plan 08 Pin 5 tried to build that regression pin and came back
        // negative -- the old call left PSI, Document and disk in agreement -- so F13 rests on
        // API correctness plus one correlation (an EDT SlowOperations assertion that stopped
        // appearing when this changed), not on a demonstrated divergence.
        return element.replace(replacement) as TypeSpecIdentifier
    }
}
