package java.util

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone


object Collections {
    fun <T> unmodifiableList(list: List<T>): List<T> = list.toList()
    fun <T> unmodifiableSet(set: Set<T>): Set<T> = set.toSet()
    fun <K, V> unmodifiableMap(map: Map<K, V>): Map<K, V> = map.toMap()
    fun <K, V> synchronizedMap(map: MutableMap<K, V>): MutableMap<K, V> = java.util.concurrent.ConcurrentHashMap<K, V>().also { it.putAll(map) }
    fun <T> synchronizedList(list: MutableList<T>): MutableList<T> = list
    fun <T> emptyList(): List<T> = kotlin.collections.emptyList()
    fun <T> shuffle(list: MutableList<T>) = list.shuffle()
    fun <T> reverse(list: MutableList<T>) = list.reverse()
    fun <T> swap(list: MutableList<T>, i: Int, j: Int) { val t = list[i]; list[i] = list[j]; list[j] = t }
    fun <T> newSetFromMap(map: MutableMap<T, Boolean>): MutableSet<T> = LinkedHashSet()
}

/** Every call site wants a fresh random v4 id as a string. */
class UUID private constructor(private val text: String) {
    override fun toString() = text
    override fun equals(other: Any?) = other is UUID && other.text == text
    override fun hashCode() = text.hashCode()

    companion object {
        fun randomUUID(): UUID = UUID(platform.Foundation.NSUUID().UUIDString.lowercase())
        fun fromString(s: String): UUID = UUID(s.lowercase())
        fun nameUUIDFromBytes(bytes: ByteArray): UUID {
            val d = java.security.MessageDigest.getInstance("MD5").digest(bytes)
            d[6] = ((d[6].toInt() and 0x0f) or 0x30).toByte()
            d[8] = ((d[8].toInt() and 0x3f) or 0x80).toByte()
            val hex = d.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            return UUID("${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}")
        }
    }
}

class Date(val time: Long = com.music.bitchord.ioscompat.JSystem.currentTimeMillis()) {
    fun getTime(): Long = time
}

class TimeZone private constructor(val id: String) {
    companion object {
        fun getTimeZone(id: String) = TimeZone(id)
        fun getDefault() = TimeZone(NSTimeZone.localTimeZone.name)
    }
}

object Base64 {
    class Encoder internal constructor(private val url: Boolean, private val pad: Boolean) {
        fun encodeToString(bytes: ByteArray): String {
            val codec = if (url) kotlin.io.encoding.Base64.UrlSafe else kotlin.io.encoding.Base64.Default
            val s = codec.encode(bytes)
            return if (pad) s else s.trimEnd('=')
        }
        fun encode(bytes: ByteArray): ByteArray = encodeToString(bytes).encodeToByteArray()
        fun withoutPadding() = Encoder(url, false)
    }

    class Decoder internal constructor(private val url: Boolean) {
        fun decode(text: String): ByteArray {
            val codec = (if (url) kotlin.io.encoding.Base64.UrlSafe else kotlin.io.encoding.Base64.Default)
                .withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT_OPTIONAL)
            return codec.decode(text.filterNot { it == '\n' || it == '\r' })
        }
        fun decode(bytes: ByteArray): ByteArray = decode(bytes.decodeToString())
    }

    fun getEncoder() = Encoder(false, true)
    fun getUrlEncoder() = Encoder(true, true)
    fun getMimeEncoder() = Encoder(false, true)
    fun getDecoder() = Decoder(false)
    fun getUrlDecoder() = Decoder(true)
    fun getMimeDecoder() = Decoder(false)
}
