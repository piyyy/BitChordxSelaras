package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import com.music.bitchord.data.model.Song

/** Network-share (WebDAV/SMB) artwork is Android-only; every iOS track carries its own URL. */
@Composable
fun rememberRemoteArtworkUrl(song: Song?): String? = song?.thumbnailUrl
