@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.music.bitchord.ios.port

/**
 * java.lang names the phone's UI files use without an import. The iOS build copies those files
 * (see iosApp/build.gradle.kts, `androidPort`) and adds `import com.music.bitchord.ios.port.*`.
 */
typealias System = com.music.bitchord.ioscompat.JSystem
typealias Math = com.music.bitchord.ioscompat.JMath

inline fun String.format(vararg args: Any?): String = com.music.bitchord.ioscompat.jvmFormat(this, args)
