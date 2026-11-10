package com.music.bitchord.download

import android.content.Context
import android.net.Uri
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where one track's download stands (the phone's, see Downloads.kt). */
sealed interface DownloadState {
    data object Queued : DownloadState
    data class Running(val fraction: Float) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/** Offline downloads are Android-only for now: nothing is ever queued or saved on iOS. */
object Downloads {
    const val PLAYLIST_PREFIX = "local:playlist:"

    val active: StateFlow<Map<String, DownloadState>> = MutableStateFlow(emptyMap())
    val saved: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    val collections: StateFlow<Map<String, DownloadedCollection>> = MutableStateFlow(emptyMap())

    fun pageIdFor(id: String): String = PLAYLIST_PREFIX + id

    fun recordIdOf(browseId: String): String? =
        browseId.removePrefix(PLAYLIST_PREFIX).takeIf { it != browseId && it.isNotEmpty() && !it.startsWith("sp_local_") }

    fun cancel(videoId: String) = Unit
    fun dismissFailure(videoId: String) = Unit
    suspend fun savedUri(context: Context, videoId: String): Uri? = null
    suspend fun delete(context: Context, videoId: String): Boolean = false
}

data class DownloadedCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val playlist: Boolean,
    val songs: List<Song>,
)
