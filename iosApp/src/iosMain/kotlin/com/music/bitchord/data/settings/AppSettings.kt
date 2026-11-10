package com.music.bitchord.data.settings

import com.music.bitchord.ios.IosSettings
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The slice of the phone's AppSettings the Android UI files compiled for iOS read, backed by
 * [IosSettings] (NSUserDefaults).
 */
object AppSettings {
    val liquidGlass get() = IosSettings.liquidGlass
    val reduceAnimation get() = IosSettings.reduceAnimation
    val reduceDynamicBlur get() = IosSettings.reduceDynamicBlur
    val animatedCanvas get() = IosSettings.animatedCanvas
    val canvasOverCellular get() = IosSettings.canvasOverCellular
    val prioritizeSpotifyCanvas get() = IosSettings.prioritizeSpotifyCanvas
    val signatureApiEnabled = MutableStateFlow(false)
    val signatureApiUrl = MutableStateFlow("")
    val detailSongSorts = MutableStateFlow<Map<String, SongSort>>(emptyMap())

    fun setLiquidGlass(value: Boolean) = IosSettings.set(IosSettings.liquidGlass, "liquidGlass", value)
    fun setReduceAnimation(value: Boolean) = IosSettings.set(IosSettings.reduceAnimation, "reduceAnimation", value)
    fun setReduceDynamicBlur(value: Boolean) = IosSettings.set(IosSettings.reduceDynamicBlur, "reduceDynamicBlur", value)
}

/** Stable persisted ordering for each on-device music library. */
enum class LocalMusicSort {
    TITLE_ASC,
    TITLE_DESC,
    DATE_ADDED,
    DATE_MODIFIED,
}

/** Ordering for the track list on an album or playlist page (the phone's, see DetailScreen.kt). */
enum class SongSort {
    DEFAULT,
    TITLE_ASC,
    TITLE_DESC,
    DATE_ADDED_ASC,
    DATE_ADDED_DESC,
}
