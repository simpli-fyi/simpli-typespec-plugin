package simpli.fyi.plugins.typespec.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.TypeSpecLanguage

/**
 * The PSI file for `.tsp` files. Backed by the flat parser tree of `TypeSpecParserDefinition`
 * (ADR 0005 M4b) — a real `PsiFile` subclass so file language resolves to [TypeSpecLanguage]
 * instead of falling back to `PsiPlainTextFileImpl` (ADR 0003 F1 / ADR 0005).
 */
class TypeSpecFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TypeSpecLanguage.INSTANCE) {

    override fun getFileType() = TypeSpecFileType.INSTANCE

    override fun toString(): String = "TypeSpec File"
}
