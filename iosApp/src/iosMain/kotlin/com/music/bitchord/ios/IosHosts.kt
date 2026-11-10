package com.music.bitchord.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.ui.AppUiHost
import com.music.bitchord.ui.player.AudioOutputDevice
import com.music.bitchord.ui.player.AudioOutputKind
import com.music.bitchord.ui.player.CanvasVideoSpec
import com.music.bitchord.ui.player.LyricsRomanizationResult
import com.music.bitchord.ui.player.LyricsTranslationResult
import com.music.bitchord.ui.player.OutputFormatUi
import com.music.bitchord.ui.player.PartyUi
import com.music.bitchord.ui.player.PlayerHost
import com.music.bitchord.ui.player.PlayerSettingsSource
import com.music.bitchord.ui.player.SystemVolume
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The shared pages' settings, answered from [IosSettings]. No downloads on iOS yet. */
object IosAppUiHost : AppUiHost {
    override val swipeToPlayNext: StateFlow<Boolean> = IosSettings.swipeToPlayNext
    override val homeRecentsViewType: StateFlow<LibraryViewType> = IosSettings.homeRecentsViewType
    override fun setHomeRecentsViewType(value: LibraryViewType) = IosSettings.setHomeRecentsViewType(value)
    override val pinnedPlaylists = IosSettings.pinnedPlaylists
    override val librarySort = IosSettings.librarySort

    @Composable
    override fun downloadedIds(): Set<String> = emptySet()
}

/** The shared player's platform half on iOS: output and volume through the Swift bridge. */
object IosPlayerHost : PlayerHost {
    override val settings: PlayerSettingsSource = IosSettings

    override fun cachedCanvas(song: Song): CanvasArtwork? = null
    override suspend fun canvasFor(song: Song): CanvasArtwork? = null

    @Composable
    override fun CanvasVideo(spec: CanvasVideoSpec, modifier: Modifier) = Unit

    @Composable
    override fun rememberRemoteArtworkUrl(song: Song?): String? = null

    override val volume: SystemVolume = object : SystemVolume {
        override val level = MutableStateFlow(0.5f)
        override fun set(level: Float) {
            this.level.value = level
            IosPlayer.bridge.setSystemVolume(level)
        }
        override fun refresh() {
            level.value = IosPlayer.bridge.systemVolume()
        }
    }

    @Composable
    override fun rememberAudioOutputs(): List<AudioOutputDevice> {
        val name by IosPlayer.outputName.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { volume.refresh() }
        val kind = when {
            name.contains("AirPods", true) || name.contains("Bluetooth", true) || name.contains("Buds", true) -> AudioOutputKind.BLUETOOTH
            name.contains("Headphones", true) -> AudioOutputKind.WIRED
            name.contains("USB", true) -> AudioOutputKind.USB
            name.contains("HDMI", true) || name.contains("AirPlay", true) -> AudioOutputKind.HDMI
            name == "iPhone" || name == "iPad" || name.contains("Speaker", true) -> AudioOutputKind.PHONE
            else -> AudioOutputKind.OTHER
        }
        return listOf(AudioOutputDevice(id = 0, name = name, kind = kind, isActive = true))
    }

    @Composable
    override fun rememberOutputPicker(onOpen: () -> Unit): () -> Unit = {
        onOpen()
        IosPlayer.bridge.openRoutePicker()
    }

    override fun selectAudioOutput(id: Int?) = IosPlayer.bridge.openRoutePicker()

    override val outputFormat: StateFlow<OutputFormatUi> = MutableStateFlow(OutputFormatUi(summary = "AAC / Opus · Selaras"))
    override val party: StateFlow<PartyUi> = MutableStateFlow(PartyUi())

    override suspend fun translateLyrics(trackId: String, lines: List<LyricLine>, targetLanguageTag: String) =
        LyricsTranslationResult.Unavailable

    override suspend fun romanizeLyrics(trackId: String, lines: List<LyricLine>, targetLanguageTag: String) =
        LyricsRomanizationResult.Unavailable

    override fun showMessage(message: String) = IosPlayer.bridge.toast(message)

    /** The phone's audio-pipeline dialog is Selaras on iOS. */
    @Composable
    override fun AudioPipelineDialog(hazeState: HazeState, isPlaying: Boolean, onDismiss: () -> Unit) {
        LaunchedEffect(Unit) {
            onDismiss()
            IosPlayer.bridge.openSelaras()
        }
    }
}
