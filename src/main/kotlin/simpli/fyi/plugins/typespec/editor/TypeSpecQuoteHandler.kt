package simpli.fyi.plugins.typespec.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes

/**
 * Auto-close/auto-skip quotes for `"..."` and `"""..."""` string literals.
 *
 * Verified to work without a `ParserDefinition` (ADR 0003 F5): `TypedQuoteImpl.getQuoteHandler`
 * falls back to `viewProvider.baseLanguage`, which is correctly `TypeSpecLanguage` per ADR 0003
 * F1, and then operates on editor-highlighter tokens.
 */
class TypeSpecQuoteHandler : SimpleTokenSetQuoteHandler(
    TypeSpecTokenTypes.STRING,
    TypeSpecTokenTypes.MULTILINE_STRING,
)
