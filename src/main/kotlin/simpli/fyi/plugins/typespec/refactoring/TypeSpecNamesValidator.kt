package simpli.fyi.plugins.typespec.refactoring

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import simpli.fyi.plugins.typespec.psi.TypeSpecKeywords
import simpli.fyi.plugins.typespec.psi.TypeSpecNames

/**
 * Wires [TypeSpecKeywords] and [TypeSpecNames] into the platform's `lang.namesValidator` EP
 * (M6.5f, ADR 0012). No rename UI depends on this yet — `RenameUtil.isValidName` and the
 * rename dialog are what actually consult it, both arriving in M6.5g/h.
 */
class TypeSpecNamesValidator : NamesValidator {

    override fun isKeyword(name: String, project: Project?): Boolean {
        return name in TypeSpecKeywords.ALL
    }

    /**
     * True for anything [TypeSpecNames.escape] can represent, including keywords and names
     * with spaces — those become backtick-wrapped. False only for the empty string and for
     * text containing a backtick, `\r`, or `\n`, none of which is representable in either
     * shape the lexer accepts.
     */
    override fun isIdentifier(name: String, project: Project?): Boolean {
        return runCatching { TypeSpecNames.escape(name) }.isSuccess
    }
}
