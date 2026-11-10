package java.util

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.countryCode
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.localizedStringForLanguageCode
import platform.Foundation.localizedStringForCountryCode
import platform.Foundation.preferredLanguages

/** java.util.Locale over NSLocale, the parts BitChord reads. */
class Locale(language: String, country: String = "", val variant: String = "") {
    val language: String = language.lowercase()
    val country: String = country.uppercase()
    val script: String = ""

    fun toLanguageTag(): String = when {
        language.isEmpty() -> "und"
        country.isEmpty() -> language
        else -> "$language-$country"
    }

    private fun ns(locale: Locale?): NSLocale =
        NSLocale.localeWithLocaleIdentifier((locale ?: getDefault()).toLanguageTag().replace('-', '_'))

    fun getDisplayLanguage(inLocale: Locale): String = ns(inLocale).localizedStringForLanguageCode(language) ?: language
    fun getDisplayCountry(inLocale: Locale): String = ns(inLocale).localizedStringForCountryCode(country) ?: country
    fun getDisplayName(inLocale: Locale): String =
        if (country.isEmpty()) getDisplayLanguage(inLocale) else "${getDisplayLanguage(inLocale)} (${getDisplayCountry(inLocale)})"
    val displayLanguage: String get() = getDisplayLanguage(getDefault())
    val displayCountry: String get() = getDisplayCountry(getDefault())
    val displayName: String get() = getDisplayName(getDefault())
    fun getDisplayLanguage(): String = displayLanguage
    fun getDisplayName(): String = displayName
    val isO3Language: String get() = language
    val iSO3Language: String get() = language

    override fun equals(other: Any?) = other is Locale && other.language == language && other.country == country
    override fun hashCode() = language.hashCode() * 31 + country.hashCode()
    override fun toString() = if (country.isEmpty()) language else "${language}_$country"

    companion object {
        val ROOT = Locale("")
        val US = Locale("en", "US")
        val UK = Locale("en", "GB")
        val ENGLISH = Locale("en")
        val JAPANESE = Locale("ja")
        val KOREAN = Locale("ko")
        val CHINESE = Locale("zh")
        val SIMPLIFIED_CHINESE = Locale("zh", "CN")
        val TRADITIONAL_CHINESE = Locale("zh", "TW")
        val GERMAN = Locale("de")
        val FRENCH = Locale("fr")

        private var override: Locale? = null

        fun getDefault(): Locale = override ?: run {
            val current = NSLocale.currentLocale
            val lang = (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-') ?: current.languageCode
            Locale(lang, current.countryCode ?: "")
        }

        fun setDefault(locale: Locale) { override = locale }

        fun forLanguageTag(tag: String): Locale {
            val parts = tag.replace('_', '-').split('-').filter { it.isNotEmpty() }
            if (parts.isEmpty() || parts[0] == "und") return ROOT
            val country = parts.drop(1).firstOrNull { it.length == 2 || (it.length == 3 && it.all(Char::isDigit)) } ?: ""
            return Locale(parts[0], country)
        }

        fun getAvailableLocales(): Array<Locale> = arrayOf(US, ENGLISH)
        fun getISOCountries(): Array<String> = arrayOf()
        fun getISOLanguages(): Array<String> = arrayOf()
    }
}
