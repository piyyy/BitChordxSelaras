package com.music.bitchord.desktop

import java.util.concurrent.TimeUnit

/**
 * Linux HiDPI: under GNOME Wayland with XWayland native scaling, X clients see the monitor's real
 * pixels and are expected to scale themselves. GNOME says how much through `Xft.dpi` (192 = 2x),
 * but Java only looks at `GDK_SCALE`, so it draws everything at 1x and the UI comes out half size.
 *
 * Must run before anything touches AWT or JavaFX, because both read their scale once at start-up.
 * An explicit `GDK_SCALE`, `-Dsun.java2d.uiScale` or `-Dglass.gtk.uiScale` always wins.
 */
object DesktopLinuxScale {
    fun install() {
        if (!DesktopPlatform.isLinux) return
        if (!System.getenv("GDK_SCALE").isNullOrBlank()) return
        val scale = System.getenv("BITCHORD_SCALE")?.toIntOrNull() ?: detect() ?: return
        // Java2D on Linux only honours whole-number scales.
        if (scale < 2) return
        if (System.getProperty("sun.java2d.uiScale") == null) {
            System.setProperty("sun.java2d.uiScale", scale.toString())
        }
        if (System.getProperty("glass.gtk.uiScale") == null) {
            System.setProperty("glass.gtk.uiScale", scale.toString())
        }
    }

    /** Whole-number scale from the X resource database's `Xft.dpi`, or null when unknown. */
    private fun detect(): Int? = runCatching {
        val process = ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroy()
        val dpi = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Xft.dpi:") }
            ?.substringAfter(':')?.trim()?.toDoubleOrNull()
            ?: return@runCatching null
        // Round down: 1.5x (144 dpi) stays at 1x rather than jumping to an oversized 2x.
        (dpi / 96.0 + 0.01).toInt().takeIf { it >= 1 }
    }.getOrNull()
}
