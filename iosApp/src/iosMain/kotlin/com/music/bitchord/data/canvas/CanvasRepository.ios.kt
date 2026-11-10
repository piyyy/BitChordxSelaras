package com.music.bitchord.data.canvas

/**
 * Animated album covers (Apple Music / Spotify Canvas) need the phone's video player; iOS shows
 * the still artwork for now.
 */
object CanvasRepository {
    suspend fun canvasForAlbum(album: String, artist: String): CanvasArtwork? = null
}
