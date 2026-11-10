@file:Suppress("unused", "NOTHING_TO_INLINE", "UNUSED_PARAMETER", "EXTENSION_SHADOWED_BY_MEMBER")

package com.music.bitchord.ios.port

import com.music.bitchord.ioscompat.JCharset
import com.music.bitchord.ioscompat.jvmFormat

/**
 * java.lang names the phone's UI files use without an import. The iOS build copies those files
 * (see iosApp/build.gradle.kts, `androidPort`) and adds `import com.music.bitchord.ios.port.*`.
 */
typealias System = com.music.bitchord.ioscompat.JSystem
typealias Math = com.music.bitchord.ioscompat.JMath

inline fun String.format(vararg args: Any?): String = jvmFormat(this, args)
inline fun String.format(locale: java.util.Locale?, vararg args: Any?): String = jvmFormat(this, args)
inline fun String.Companion.format(format: String, vararg args: Any?): String = jvmFormat(format, args)
inline fun String.Companion.format(locale: java.util.Locale?, format: String, vararg args: Any?): String = jvmFormat(format, args)
inline fun String.lowercase(locale: java.util.Locale): String = lowercase()
inline fun String.uppercase(locale: java.util.Locale): String = uppercase()
inline fun Char.lowercase(locale: java.util.Locale): String = lowercase()
inline fun Char.uppercase(locale: java.util.Locale): String = uppercase()
inline fun Char.titlecase(locale: java.util.Locale): String = titlecase()
inline fun String.replaceFirstChar(locale: java.util.Locale, transform: (Char) -> CharSequence): String = replaceFirstChar(transform)
inline fun String.toByteArray(charset: java.nio.charset.Charset = JCharset.UTF_8): ByteArray = encodeToByteArray()
inline fun ByteArray.toString(charset: java.nio.charset.Charset): String = decodeToString()
fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? = get(key) ?: run { put(key, value); null }
fun <K, V> Map<K, V>.getOrDefault(key: K, default: V): V = get(key) ?: default
val kotlinx.coroutines.Dispatchers.IO: kotlinx.coroutines.CoroutineDispatcher
    get() = kotlinx.coroutines.Dispatchers.Default
