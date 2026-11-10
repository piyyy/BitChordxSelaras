package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.music.bitchord.androidPluralNames
import com.music.bitchord.androidStringNames
import com.music.bitchord.ioscompat.jvmFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.preferredLanguages
import platform.Foundation.stringWithContentsOfFile

/**
 * Android's `stringResource` for the phone UI files the iOS build compiles unchanged: the ids in
 * [com.music.bitchord.R] index tables generated from app/src/main/res (ios/kmp/gen-android-strings.py),
 * shipped in the app bundle, and resolved for the device's language like Android does.
 */
@Composable
fun stringResource(id: Int): String = remember(id) { AndroidStrings.string(id) }

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String =
    jvmFormat(stringResource(id), formatArgs)

@Composable
fun pluralStringResource(id: Int, count: Int): String = remember(id, count) { AndroidStrings.plural(id, count) }

@Composable
fun pluralStringResource(id: Int, count: Int, vararg formatArgs: Any): String =
    jvmFormat(pluralStringResource(id, count), formatArgs)

object AndroidStrings {
    private class Table(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private val fallback: Table by lazy { load("default") ?: Table(emptyMap(), emptyMap()) }

    private val localized: Table? by lazy {
        @Suppress("UNCHECKED_CAST")
        val preferred = NSLocale.preferredLanguages as List<String>
        preferred.asSequence().flatMap { qualifiersFor(it).asSequence() }.firstNotNullOfOrNull { load(it) }
    }

    fun string(id: Int): String {
        val name = androidStringNames.getOrNull(id) ?: return ""
        return localized?.strings?.get(name) ?: fallback.strings[name] ?: name
    }

    fun string(id: Int, vararg args: Any?): String = jvmFormat(string(id), args)

    fun plural(id: Int, count: Int): String {
        val name = androidPluralNames.getOrNull(id) ?: return ""
        val forms = localized?.plurals?.get(name) ?: fallback.plurals[name] ?: return name
        val quantity = if (count == 1) "one" else "other"
        return forms[quantity] ?: forms["other"] ?: forms.values.firstOrNull() ?: name
    }

    /** Android resource qualifiers to try for one of iOS's preferred languages ("id-ID", "zh-Hant-TW"…). */
    private fun qualifiersFor(tag: String): List<String> {
        val parts = tag.split('-', '_')
        val lang = parts.first().lowercase()
        if (lang == "en") return listOf("default")
        return when (lang) {
            "zh" -> {
                val hant = parts.any { it.equals("Hant", true) } || parts.any { it.uppercase() in setOf("TW", "HK", "MO") }
                when {
                    !hant -> listOf("zh")
                    parts.any { it.uppercase() == "HK" || it.uppercase() == "MO" } -> listOf("zh-rHK", "b+zh+Hant", "zh-rTW")
                    else -> listOf("zh-rTW", "b+zh+Hant", "zh-rHK")
                }
            }
            "iw" -> listOf("he")
            "in" -> listOf("id")
            else -> listOf(lang)
        }
    }

    private fun load(qualifier: String): Table? {
        val name = "android-strings-$qualifier"
        val bundle = NSBundle.mainBundle
        val path = bundle.pathForResource(name, "json")
            ?: bundle.pathForResource(name, "json", "AndroidStrings")
            ?: return null
        val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: return null
        return runCatching {
            val root = Json.parseToJsonElement(text).jsonObject
            val strings = (root["s"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            val plurals = (root["p"] as? JsonObject)?.mapValues { (_, forms) ->
                forms.jsonObject.mapValues { it.value.jsonPrimitive.content }
            }.orEmpty()
            Table(strings, plurals)
        }.getOrNull()
    }
}
