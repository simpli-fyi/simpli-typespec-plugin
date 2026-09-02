package simpli.fyi.plugins.typespec

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class TypeSpecFileType private constructor() : LanguageFileType(TypeSpecLanguage.INSTANCE) {

    override fun getName(): String = "TypeSpec"

    override fun getDescription(): String = "TypeSpec API description language"

    override fun getDefaultExtension(): String = "tsp"

    override fun getIcon(): Icon = TypeSpecIcons.FILE

    companion object {
        @JvmStatic
        val INSTANCE = TypeSpecFileType()
    }
}
