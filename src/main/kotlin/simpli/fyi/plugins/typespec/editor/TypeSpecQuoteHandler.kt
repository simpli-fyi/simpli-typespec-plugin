package simpli.fyi.plugins.typespec.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import simpli.fyi.plugins.typespec.psi.TypeSpecTokenTypes

/**
 * Auto-close/auto-skip quotes for `"..."` and `"""..."""` string literals.
 *
 * `TypedQuoteImpl.getQuoteHandler` falls back to `viewProvider.baseLanguage`, which is
 * correctly `TypeSpecLanguage` per ADR 0003 F1 regardless of a `ParserDefinition`; it now
 * also resolves via `psiFile.language` since `TypeSpecParserDefinition` (ADR 0005) makes
 * that `TypeSpecLanguage` too.
 */
class TypeSpecQuoteHandler : SimpleTokenSetQuoteHandler(
    TypeSpecTokenTypes.STRING,
    TypeSpecTokenTypes.MULTILINE_STRING,
)
