@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.music.bitchord.ioscompat

import platform.Foundation.NSDate
import platform.Foundation.NSRecursiveLock
import platform.Foundation.timeIntervalSince1970
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.TimeSource

/*
 * What the JVM gives BitChord's shared code for free (java.lang, kotlin.jvm), for the iOS build.
 * Each package that uses these gets a JvmImplicit.kt pointing here, because on the JVM they need
 * no import and the upstream sources must compile unchanged.
 */

@PublishedApi
internal val monitor = NSRecursiveLock()

/** `synchronized(lock) { }`: one process-wide recursive lock — coarse, but never wrong. */
inline fun <R> jvmSynchronized(block: () -> R): R {
    monitor.lock()
    try {
        return block()
    } finally {
        monitor.unlock()
    }
}

object JSystem {
    private val start = TimeSource.Monotonic.markNow()
    fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    fun nanoTime(): Long = start.elapsedNow().inWholeNanoseconds
    fun getProperty(key: String): String? = when (key) {
        "line.separator" -> "\n"
        "os.name" -> "iOS"
        else -> null
    }
    fun getProperty(key: String, default: String): String = getProperty(key) ?: default
    fun lineSeparator(): String = "\n"
    fun getenv(name: String): String? = null
}

object JCharset {
    val UTF_8 = java.nio.charset.Charset("UTF-8")
    val US_ASCII = java.nio.charset.Charset("US-ASCII")
    val ISO_8859_1 = java.nio.charset.Charset("ISO-8859-1")
}

/** java.lang.Character, as far as BitChord asks it. Each package's `Character` object extends this. */
open class JCharacterBase {
    val MIN_SUPPLEMENTARY_CODE_POINT = 0x10000
    val MIN_HIGH_SURROGATE = '\uD800'
    val MAX_HIGH_SURROGATE = '\uDBFF'

    fun isLetter(c: Char): Boolean = c.isLetter()
    fun isLetter(cp: Int): Boolean = codePointString(cp).let { it.isNotEmpty() && it[0].isLetter() } || isCjk(cp) || isHangul(cp) || isKana(cp)
    fun isDigit(c: Char): Boolean = c.isDigit()
    fun isDigit(cp: Int): Boolean = cp in 0..0xFFFF && cp.toChar().isDigit()
    fun isLetterOrDigit(c: Char): Boolean = c.isLetterOrDigit()
    fun isLetterOrDigit(cp: Int): Boolean = isLetter(cp) || isDigit(cp)
    fun isWhitespace(c: Char): Boolean = c.isWhitespace()
    fun isWhitespace(cp: Int): Boolean = cp in 0..0xFFFF && cp.toChar().isWhitespace()
    fun isSpaceChar(c: Char): Boolean = c == ' ' || c == ' ' || c.isWhitespace()
    fun isUpperCase(c: Char): Boolean = c.isUpperCase()
    fun isLowerCase(c: Char): Boolean = c.isLowerCase()
    fun toLowerCase(c: Char): Char = c.lowercaseChar()
    fun toUpperCase(c: Char): Char = c.uppercaseChar()
    fun charCount(cp: Int): Int = if (cp >= MIN_SUPPLEMENTARY_CODE_POINT) 2 else 1
    fun isSupplementaryCodePoint(cp: Int): Boolean = cp >= MIN_SUPPLEMENTARY_CODE_POINT
    fun isHighSurrogate(c: Char): Boolean = c.isHighSurrogate()
    fun isLowSurrogate(c: Char): Boolean = c.isLowSurrogate()
    fun isSurrogate(c: Char): Boolean = c.isSurrogate()
    fun toCodePoint(high: Char, low: Char): Int = ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
    fun toChars(cp: Int): CharArray = codePointString(cp).toCharArray()
    fun getType(c: Char): Int = if (c.category == CharCategory.NON_SPACING_MARK) NON_SPACING_MARK.toInt() else 0
    val NON_SPACING_MARK: Byte = 6

    val DIRECTIONALITY_UNDEFINED: Byte = -1
    val DIRECTIONALITY_LEFT_TO_RIGHT: Byte = 0
    val DIRECTIONALITY_RIGHT_TO_LEFT: Byte = 1
    val DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC: Byte = 2
    val DIRECTIONALITY_OTHER_NEUTRALS: Byte = 13

    /** Strong direction by script range — what BitChord's RTL lyric check needs. */
    fun getDirectionality(c: Char): Byte = getDirectionality(c.code)
    fun getDirectionality(cp: Int): Byte = when {
        cp in 0x0590..0x05FF || cp in 0xFB1D..0xFB4F || cp in 0x07C0..0x08FF || cp in 0x10800..0x10FFF -> DIRECTIONALITY_RIGHT_TO_LEFT
        cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF -> DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        isLetter(cp) -> DIRECTIONALITY_LEFT_TO_RIGHT
        else -> DIRECTIONALITY_OTHER_NEUTRALS
    }
    fun codePointString(cp: Int): String = if (cp < 0x10000) {
        cp.toChar().toString()
    } else {
        val v = cp - 0x10000
        charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
    }
}

object JCharacter : JCharacterBase()

internal fun isCjk(cp: Int) = cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x20000..0x2FA1F || cp in 0xF900..0xFAFF
internal fun isHangul(cp: Int) = cp in 0xAC00..0xD7AF || cp in 0x1100..0x11FF || cp in 0x3130..0x318F
internal fun isKana(cp: Int) = cp in 0x3040..0x30FF || cp in 0x31F0..0x31FF || cp in 0xFF66..0xFF9F

    enum class JUnicodeScript {
        COMMON, LATIN, GREEK, CYRILLIC, ARMENIAN, HEBREW, ARABIC, DEVANAGARI, BENGALI, GURMUKHI, GUJARATI,
        TAMIL, TELUGU, KANNADA, MALAYALAM, THAI, LAO, TIBETAN, MYANMAR, GEORGIAN, HANGUL, ETHIOPIC, KHMER,
        MONGOLIAN, HIRAGANA, KATAKANA, BOPOMOFO, HAN, SINHALA, UNKNOWN;

        companion object {
            fun of(cp: Int): JUnicodeScript = when {
                cp < 0x41 -> COMMON
                cp in 0x41..0x5A || cp in 0x61..0x7A || cp in 0xC0..0x24F || cp in 0x1E00..0x1EFF || cp in 0x2C60..0x2C7F || cp in 0xA720..0xA7FF || cp in 0xFF21..0xFF5A -> if (cp == 0xD7 || cp == 0xF7) COMMON else LATIN
                cp in 0x370..0x3FF || cp in 0x1F00..0x1FFF -> GREEK
                cp in 0x400..0x52F -> CYRILLIC
                cp in 0x530..0x58F -> ARMENIAN
                cp in 0x590..0x5FF -> HEBREW
                cp in 0x600..0x6FF || cp in 0x750..0x77F || cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF -> ARABIC
                cp in 0x900..0x97F -> DEVANAGARI
                cp in 0x980..0x9FF -> BENGALI
                cp in 0xA00..0xA7F -> GURMUKHI
                cp in 0xA80..0xAFF -> GUJARATI
                cp in 0xB80..0xBFF -> TAMIL
                cp in 0xC00..0xC7F -> TELUGU
                cp in 0xC80..0xCFF -> KANNADA
                cp in 0xD00..0xD7F -> MALAYALAM
                cp in 0xD80..0xDFF -> SINHALA
                cp in 0xE00..0xE7F -> THAI
                cp in 0xE80..0xEFF -> LAO
                cp in 0xF00..0xFFF -> TIBETAN
                cp in 0x1000..0x109F -> MYANMAR
                cp in 0x10A0..0x10FF -> GEORGIAN
                cp in 0x1200..0x139F -> ETHIOPIC
                cp in 0x1780..0x17FF -> KHMER
                cp in 0x1800..0x18AF -> MONGOLIAN
                cp in 0x3040..0x309F -> HIRAGANA
                cp in 0x30A0..0x30FF || cp in 0x31F0..0x31FF || cp in 0xFF66..0xFF9F -> KATAKANA
                cp in 0x3100..0x312F || cp in 0x31A0..0x31BF -> BOPOMOFO
                isHangul(cp) -> HANGUL
                isCjk(cp) || cp in 0x2E80..0x2FDF || cp == 0x3005 || cp == 0x3007 -> HAN
                else -> COMMON
            }
        }
    }

    enum class JUnicodeBlock {
        BASIC_LATIN, LATIN_1_SUPPLEMENT, CJK_UNIFIED_IDEOGRAPHS, CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B, CJK_COMPATIBILITY_IDEOGRAPHS, CJK_SYMBOLS_AND_PUNCTUATION, HIRAGANA,
        KATAKANA, KATAKANA_PHONETIC_EXTENSIONS, HALFWIDTH_AND_FULLWIDTH_FORMS, HANGUL_SYLLABLES, HANGUL_JAMO,
        HANGUL_COMPATIBILITY_JAMO, BOPOMOFO, OTHER;

        companion object {
            fun of(cp: Int): JUnicodeBlock = when (cp) {
                in 0x0..0x7F -> BASIC_LATIN
                in 0x80..0xFF -> LATIN_1_SUPPLEMENT
                in 0x4E00..0x9FFF -> CJK_UNIFIED_IDEOGRAPHS
                in 0x3400..0x4DBF -> CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                in 0x20000..0x2A6DF -> CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                in 0xF900..0xFAFF -> CJK_COMPATIBILITY_IDEOGRAPHS
                in 0x3000..0x303F -> CJK_SYMBOLS_AND_PUNCTUATION
                in 0x3040..0x309F -> HIRAGANA
                in 0x30A0..0x30FF -> KATAKANA
                in 0x31F0..0x31FF -> KATAKANA_PHONETIC_EXTENSIONS
                in 0xFF00..0xFFEF -> HALFWIDTH_AND_FULLWIDTH_FORMS
                in 0xAC00..0xD7AF -> HANGUL_SYLLABLES
                in 0x1100..0x11FF -> HANGUL_JAMO
                in 0x3130..0x318F -> HANGUL_COMPATIBILITY_JAMO
                in 0x3100..0x312F -> BOPOMOFO
                else -> OTHER
            }

            fun of(c: Char): JUnicodeBlock = of(c.code)
        }
    }

/** `String.format`, the printf subset BitChord uses: %s %d %f %x %c %b %e %%, flags `-0+,` and width/precision. */
fun jvmFormat(format: String, args: Array<out Any?>): String {
    val out = StringBuilder()
    var argIndex = 0
    var i = 0
    while (i < format.length) {
        val c = format[i]
        if (c != '%') { out.append(c); i++; continue }
        i++
        if (i >= format.length) break
        var flags = ""
        while (i < format.length && format[i] in "-0+, #") { flags += format[i]; i++ }
        var width = 0
        while (i < format.length && format[i].isDigit()) { width = width * 10 + (format[i] - '0'); i++ }
        var precision = -1
        if (i < format.length && format[i] == '.') {
            i++; precision = 0
            while (i < format.length && format[i].isDigit()) { precision = precision * 10 + (format[i] - '0'); i++ }
        }
        if (i >= format.length) break
        val conv = format[i++]
        val text: String = when (conv) {
            '%' -> "%"
            'n' -> "\n"
            else -> {
                val arg = args.getOrNull(argIndex++)
                when (conv) {
                    's', 'S' -> (arg?.toString() ?: "null").let { if (precision >= 0) it.take(precision) else it }.let { if (conv == 'S') it.uppercase() else it }
                    'd' -> {
                        val n = (arg as? Number)?.toLong() ?: 0L
                        val digits = abs(n).toString().let { if (',' in flags) groupThousands(it) else it }
                        sign(n < 0, flags) + digits
                    }
                    'x', 'X' -> ((arg as? Number)?.toLong() ?: 0L).toString(16).let { if (conv == 'X') it.uppercase() else it }
                    'c' -> when (arg) { is Char -> arg.toString(); is Number -> arg.toInt().toChar().toString(); else -> "" }
                    'b' -> (arg != null && arg != false).toString()
                    'f' -> {
                        val d = (arg as? Number)?.toDouble() ?: 0.0
                        fixed(abs(d), if (precision < 0) 6 else precision, ',' in flags).let { sign(d < 0, flags) + it }
                    }
                    'e' -> {
                        val d = (arg as? Number)?.toDouble() ?: 0.0
                        if (d == 0.0) fixed(0.0, if (precision < 0) 6 else precision, false) + "e+00" else {
                            var exp = kotlin.math.floor(kotlin.math.log10(abs(d))).toInt()
                            val mant = d / 10.0.pow(exp)
                            sign(d < 0, flags) + fixed(abs(mant), if (precision < 0) 6 else precision, false) +
                                "e" + (if (exp < 0) "-" else "+") + abs(exp).toString().padStart(2, '0')
                        }
                    }
                    else -> arg?.toString() ?: "null"
                }
            }
        }
        val padded = when {
            text.length >= width -> text
            '-' in flags -> text.padEnd(width)
            '0' in flags && conv in "dfxXe" -> {
                val signChar = text.firstOrNull()?.takeIf { it == '-' || it == '+' }
                if (signChar != null) signChar + text.drop(1).padStart(width - 1, '0') else text.padStart(width, '0')
            }
            else -> text.padStart(width)
        }
        out.append(padded)
    }
    return out.toString()
}

private fun sign(negative: Boolean, flags: String) = when {
    negative -> "-"
    '+' in flags -> "+"
    ' ' in flags -> " "
    else -> ""
}

private fun groupThousands(digits: String): String =
    digits.reversed().chunked(3).joinToString(",").reversed()

private fun fixed(value: Double, precision: Int, group: Boolean): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return "Infinity"
    val scale = 10.0.pow(precision)
    val scaled = (value * scale).roundToLong()
    val whole = scaled / scale.toLong().coerceAtLeast(1)
    val frac = if (precision == 0) 0L else scaled % scale.toLong()
    val wholeText = whole.toString().let { if (group) groupThousands(it) else it }
    return if (precision == 0) wholeText else wholeText + "." + frac.toString().padStart(precision, '0')
}

object JMath {
    const val PI = kotlin.math.PI
    const val E = kotlin.math.E
    fun abs(v: Int) = kotlin.math.abs(v)
    fun abs(v: Long) = kotlin.math.abs(v)
    fun abs(v: Float) = kotlin.math.abs(v)
    fun abs(v: Double) = kotlin.math.abs(v)
    fun max(a: Int, b: Int) = kotlin.math.max(a, b)
    fun max(a: Long, b: Long) = kotlin.math.max(a, b)
    fun max(a: Float, b: Float) = kotlin.math.max(a, b)
    fun max(a: Double, b: Double) = kotlin.math.max(a, b)
    fun min(a: Int, b: Int) = kotlin.math.min(a, b)
    fun min(a: Long, b: Long) = kotlin.math.min(a, b)
    fun min(a: Float, b: Float) = kotlin.math.min(a, b)
    fun min(a: Double, b: Double) = kotlin.math.min(a, b)
    fun round(v: Double): Long = kotlin.math.round(v).toLong()
    fun round(v: Float): Int = kotlin.math.round(v).toInt()
    fun floor(v: Double) = kotlin.math.floor(v)
    fun ceil(v: Double) = kotlin.math.ceil(v)
    fun sqrt(v: Double) = kotlin.math.sqrt(v)
    fun pow(a: Double, b: Double) = a.pow(b)
    fun log(v: Double) = kotlin.math.ln(v)
    fun log10(v: Double) = kotlin.math.log10(v)
    fun exp(v: Double) = kotlin.math.exp(v)
    fun sin(v: Double) = kotlin.math.sin(v)
    fun cos(v: Double) = kotlin.math.cos(v)
    fun atan2(y: Double, x: Double) = kotlin.math.atan2(y, x)
    fun floorMod(a: Int, b: Int) = a.mod(b)
    fun floorMod(a: Long, b: Long) = a.mod(b)
    fun floorDiv(a: Long, b: Long) = kotlin.math.floor(a.toDouble() / b).toLong()
    fun signum(v: Double) = kotlin.math.sign(v)
    fun signum(v: Float) = kotlin.math.sign(v)
    fun hypot(a: Double, b: Double) = kotlin.math.hypot(a, b)
    fun toDegrees(r: Double) = r * 180.0 / PI
    fun toRadians(d: Double) = d * PI / 180.0
}

object JThread {
    fun sleep(millis: Long) {
        platform.posix.usleep((millis.coerceAtLeast(0) * 1000).toUInt())
    }
    fun currentThread(): JThread = this
    val name: String get() = if (platform.Foundation.NSThread.isMainThread) "main" else "worker"
    fun interrupt() {}
    val isInterrupted: Boolean get() = false
}

/** java.util.TreeSet as far as `sortedSetOf` needs: a mutable set iterated in natural order. */
class SortedMutableSet<T : Comparable<T>>(initial: Collection<T> = emptyList()) : AbstractMutableSet<T>() {
    private val items = ArrayList<T>()
    init { initial.forEach { add(it) } }
    override val size: Int get() = items.size
    override fun add(element: T): Boolean {
        val i = items.binarySearch(element)
        if (i >= 0) return false
        items.add(-i - 1, element)
        return true
    }
    override fun iterator(): MutableIterator<T> = items.iterator()
    fun first(): T = items.first()
    fun last(): T = items.last()
}

/** java.lang.ThreadLocal, process-wide: BitChord only uses it to tag log lines with a track. */
class JThreadLocal<T> {
    private var value: T? = null
    private var set = false
    protected open fun initialValue(): T? = null
    fun get(): T = (if (set) value else initialValue()) as T
    fun set(v: T) { value = v; set = true }
    fun remove() { value = null; set = false }

    companion object {
        fun <T> withInitial(supplier: () -> T): JThreadLocal<T> = JThreadLocal<T>().also { it.set(supplier()) }
    }
}
