package com.music.bitchord.ios

import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.ui.player.PlayerSettingsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSUserDefaults

/**
 * The iOS app's settings: what the shared player and pages read (named as the phone's
 * AppSettings has them), kept in NSUserDefaults. Features iOS doesn't have yet (Automix,
 * Canvas video, USB DACs, nerd stats) answer with their "off" value.
 */
object IosSettings : PlayerSettingsSource {
    private val defaults = NSUserDefaults.standardUserDefaults

    private fun bool(key: String, default: Boolean): MutableStateFlow<Boolean> =
        MutableStateFlow(if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else default)

    private fun string(key: String, default: String): MutableStateFlow<String> =
        MutableStateFlow(defaults.stringForKey(key) ?: default)

    fun set(flow: MutableStateFlow<Boolean>, key: String, value: Boolean) {
        flow.value = value
        defaults.setBool(value, key)
    }

    // ---- What the player reads ----------------------------------------------------------------

    override val animatedCanvas = MutableStateFlow(false)
    override val audioQualityCellular = MutableStateFlow(AudioQuality.MEDIUM)
    override val audioQualityWifi = MutableStateFlow(AudioQuality.MEDIUM)
    override val canvasOverCellular = MutableStateFlow(false)
    override val fullBleedArtwork = bool("fullBleedArtwork", false)
    override val hideSongStatus = bool("hideSongStatus", false)
    override val showQueueHistory = bool("showQueueHistory", true)
    override val hideVolumeBar = bool("hideVolumeBar", false)
    override val hideStatusBar = MutableStateFlow(false)
    override val lastPlayerScreen = MutableStateFlow(
        runCatching { LastPlayerScreen.valueOf(defaults.stringForKey("lastPlayerScreen") ?: "MAIN") }.getOrDefault(LastPlayerScreen.MAIN),
    )
    override val legacyMeshGradient = bool("legacyMeshGradient", false)
    override val lyricsBlur = bool("lyricsBlur", true)
    override val lyricsOffsetMs = MutableStateFlow(defaults.integerForKey("lyricsOffsetMs").toInt())
    override val lyricsSourceOrder: StateFlow<List<LyricsSource>> = MutableStateFlow(LyricsSource.offered)
    override val meteredConnection = MutableStateFlow<Boolean?>(null)
    override val preferUsbDac = MutableStateFlow(false)
    override val prioritizeSpotifyCanvas = MutableStateFlow(false)
    override val reduceAnimation = bool("reduceAnimation", false)
    override val reduceDynamicBlur = bool("reduceDynamicBlur", false)
    override val showNerdStats = MutableStateFlow(false)
    override val smartAnalysis = MutableStateFlow(SmartAnalysis())
    override val smartFadeEnabled = MutableStateFlow(false)
    override val smartMixInProgress = MutableStateFlow(false)
    override val smartMixBlend = MutableStateFlow<MixBlend?>(null)
    override val smartTransitionWindow = MutableStateFlow<TransitionWindow?>(null)
    override val spotifyCanvasAutoHide = MutableStateFlow(true)
    override val syncedLyrics = bool("syncedLyrics", true)
    override val translationLanguage = string("translationLanguage", "")
    override val versionAlignmentInProgress = MutableStateFlow(false)

    override fun setLastPlayerScreen(value: LastPlayerScreen) {
        lastPlayerScreen.value = value
        defaults.setObject(value.name, "lastPlayerScreen")
    }

    override fun setLyricsOffsetMs(value: Int) {
        lyricsOffsetMs.value = value
        defaults.setInteger(value.toLong(), "lyricsOffsetMs")
    }

    // ---- What the pages read ------------------------------------------------------------------

    val swipeToPlayNext = bool("swipeToPlayNext", true)
    val homeRecentsViewType = MutableStateFlow(
        runCatching { LibraryViewType.valueOf(defaults.stringForKey("homeRecentsViewType") ?: "GRID") }.getOrDefault(LibraryViewType.GRID),
    )
    val pinnedPlaylists = MutableStateFlow<List<String>>(emptyList())
    val librarySort = MutableStateFlow(LibrarySort.DEFAULT)

    fun setHomeRecentsViewType(value: LibraryViewType) {
        homeRecentsViewType.value = value
        defaults.setObject(value.name, "homeRecentsViewType")
    }

    // ---- The app's own ------------------------------------------------------------------------

    val autoplay = bool("autoplay", true)
    val darkTheme = bool("darkTheme", true)

    /** Content language and region for YouTube Music, "" = follow the phone. */
    val contentLanguage = string("contentLanguage", "")
    val contentRegion = string("contentRegion", "")

    var searchHistory: List<String>
        get() = (defaults.stringArrayForKey("searchHistory") as? List<*>)?.filterIsInstance<String>().orEmpty()
        set(value) = defaults.setObject(value.take(30), "searchHistory")
}
