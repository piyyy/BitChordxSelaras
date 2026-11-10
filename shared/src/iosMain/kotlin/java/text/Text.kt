package java.text

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.decomposedStringWithCompatibilityMapping
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCompatibilityMapping
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName

object Normalizer {
    enum class Form { NFD, NFC, NFKD, NFKC }

    @Suppress("CAST_NEVER_SUCCEEDS")
    fun normalize(text: CharSequence, form: Form): String {
        val ns = text.toString() as NSString
        return when (form) {
            Form.NFD -> ns.decomposedStringWithCanonicalMapping
            Form.NFC -> ns.precomposedStringWithCanonicalMapping
            Form.NFKD -> ns.decomposedStringWithCompatibilityMapping
            Form.NFKC -> ns.precomposedStringWithCompatibilityMapping
        }
    }

    fun isNormalized(text: CharSequence, form: Form) = normalize(text, form) == text.toString()
}

class SimpleDateFormat(pattern: String, locale: java.util.Locale = java.util.Locale.getDefault()) {
    private val formatter = NSDateFormatter().apply {
        dateFormat = pattern
        this.locale = NSLocale.localeWithLocaleIdentifier(locale.toLanguageTag().replace('-', '_'))
    }

    var timeZone: java.util.TimeZone? = null
        set(value) {
            field = value
            value?.let { formatter.timeZone = NSTimeZone.timeZoneWithName(it.id) ?: formatter.timeZone }
        }

    fun format(date: java.util.Date): String =
        formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(date.time / 1000.0))

    fun parse(text: String): java.util.Date? =
        formatter.dateFromString(text)?.let { java.util.Date((it.timeIntervalSince1970 * 1000).toLong()) }
}

class ParseException(message: String?, val errorOffset: Int = 0) : Exception(message)
