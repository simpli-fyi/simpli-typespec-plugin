package simpli.fyi.plugins.typespec

import com.intellij.lang.Language

class TypeSpecLanguage private constructor() : Language("TypeSpec") {

    override fun getDisplayName(): String = "TypeSpec"

    companion object {
        @JvmStatic
        val INSTANCE = TypeSpecLanguage()
    }
}
