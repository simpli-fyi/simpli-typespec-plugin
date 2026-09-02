package dev.tsp.intellij.typespec

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE: @NonNls String = "messages.TypeSpecBundle"

/**
 * Localisable strings for the TypeSpec plugin (display names, color settings
 * page labels, etc). Backed by `messages/TypeSpecBundle.properties`.
 */
object TypeSpecBundle : DynamicBundle(BUNDLE) {

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
