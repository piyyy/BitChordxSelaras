package com.music.bitchord.data.webdav

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Uploading device files to WebDAV is Android-only: no iOS track is a device file. */
object WebDavUploads {
    sealed interface TrackState {
        data object Queued : TrackState
        data class Running(val fraction: Float) : TrackState
        data object Done : TrackState
        data class Failed(val reason: String) : TrackState
        data object Skipped : TrackState
    }

    val active: StateFlow<Map<String, TrackState>> = MutableStateFlow(emptyMap())

    fun isUploadable(song: Song): Boolean = false
}
