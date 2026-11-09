package com.music.bitchord.selaras

import java.io.File

/** The output Selaras' settings apply to right now: a stable key, and a name to show for it. */
data class SelarasDevice(val key: String?, val name: String)

/**
 * Where Selaras lives on this device, and what this device can do with it.
 *
 * Two shapes exist:
 * - **System-wide** ([systemWide] true), Linux with the Selaras daemon installed: BitChord edits the
 *   daemon's own `state.json` and signals it, and PipeWire does the filtering for every app.
 * - **In-app** (Windows, macOS, Android, Linux without the daemon): the very same state drives
 *   [SelarasDsp] inside BitChord's own audio path, so the sound is identical on every device.
 *
 * Everything past [currentDevice] has a harmless default, so an in-app platform only says where its
 * files go and which output is playing.
 */
interface SelarasPlatform {
    /** True when an external engine applies the state; false when BitChord does it itself. */
    val systemWide: Boolean

    /** Selaras' settings file. The format is Selaras' own, so presets and profiles move between devices. */
    val stateFile: File

    /** Where the AutoEq index is cached. */
    val cacheDir: File

    /** The output that is playing now, so each one can keep its own EQ. */
    fun currentDevice(): SelarasDevice

    /** One line for the page header: what is doing the equalising, and where. */
    val engineLabel: String get() = if (systemWide) "System-wide equalizer" else "Equalizer in BitChord"

    // ---- System-wide engine ---------------------------------------------------------------------

    /** Whether the external engine is up. In-app it always is: it is BitChord itself. */
    fun engineRunning(): Boolean = true

    /** Called after the state file is written, so the external engine reloads it. */
    fun afterWrite() {}

    /** Starts the external engine, when it is not running. */
    suspend fun startEngine() {}

    /** Whether limiter and compressor are available. In-app they always are. */
    val dynamicsAvailable: Boolean get() = true

    /** How to get them when they are not, shown in place of the dynamics controls. */
    val dynamicsHint: String get() = ""

    /** Feeds [spectrum] from the system's output while the page is open; null when BitChord feeds it. */
    fun startMonitor(spectrum: SelarasSpectrum): AutoCloseable? = null

    // ---- The standalone Selaras app, where it is installed --------------------------------------

    val hasSelarasApp: Boolean get() = false
    fun openApp(vararg args: String) {}
    val visualizerAvailable: Boolean get() = false
    suspend fun loginEnabled(): Boolean? = null
    suspend fun setLogin(on: Boolean) {}
    fun openShortcutSettings(): Boolean = false
    val skinPath: String? get() = null
    fun openSkin() {}

    /** Extra lines for the Advanced tab's engine note. */
    fun engineNotes(): List<String> = emptyList()
}
