package com.music.bitchord.selaras

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Selaras on Android: the state lives in the app's private files, and the EQ runs in BitChord's own
 * audio path ([com.music.bitchord.playback.audio.DspChain]), so it needs no root and no system
 * equaliser. Each output — speaker, wired, USB, each Bluetooth device — keeps its own settings.
 */
class AndroidSelarasPlatform private constructor(context: Context) : SelarasPlatform {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override val systemWide = false
    override val stateFile = File(app.filesDir, "selaras/state.json")
    override val cacheDir = File(app.cacheDir, "selaras")
    override val engineLabel = "Equalizer in BitChord"

    /**
     * Android has no "default output" to ask for, so this is the route media would take: a
     * Bluetooth headset over wired over USB over the speaker, which is how the platform routes music.
     */
    override fun currentDevice(): SelarasDevice {
        val outputs = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
        val best = outputs.filter { rank(it) > 0 }.maxByOrNull(::rank)
            ?: return SelarasDevice("speaker", "Phone speaker")
        val product = best.productName?.toString()?.trim().orEmpty()
        return when (best.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID ->
                SelarasDevice("bt:${product.ifEmpty { best.address }}", product.ifEmpty { "Bluetooth" } + " · Bluetooth")
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY ->
                SelarasDevice("usb:${product.ifEmpty { "usb" }}", product.ifEmpty { "USB audio" } + " · USB")
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                SelarasDevice("wired", "Wired headphones")
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC ->
                SelarasDevice("hdmi", "HDMI")
            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE ->
                SelarasDevice("line", "Line out")
            AudioDeviceInfo.TYPE_DOCK -> SelarasDevice("dock", "Dock")
            else -> SelarasDevice("speaker", "Phone speaker")
        }
    }

    private fun rank(d: AudioDeviceInfo): Int = when (d.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID -> 6
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> 5
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> 4
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> 3
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_DOCK -> 2
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 1
        else -> 0
    }

    override fun engineNotes(): List<String> = listOf("No root or system equalizer needed")

    companion object {
        /** Installs Selaras for this process and follows output changes. Safe to call more than once. */
        fun install(context: Context) {
            if (Selaras.installed) return
            val platform = AndroidSelarasPlatform(context)
            Selaras.install(platform)
            runCatching {
                platform.audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = Selaras.deviceChanged()
                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = Selaras.deviceChanged()
                }, Handler(Looper.getMainLooper()))
            }
        }
    }
}
