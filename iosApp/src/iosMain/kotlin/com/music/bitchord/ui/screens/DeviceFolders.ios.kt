package com.music.bitchord.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.ui.graphics.vector.ImageVector

// From the phone's LibraryPhoneParts.kt: the device folders' glyphs. iOS has none of the folders
// yet (downloads, device music, cache), so only their ids are kept for the pages that ask.

fun deviceFolderIcon(browseId: String): ImageVector? = when (browseId) {
    "local:downloads" -> Icons.Rounded.Download
    "local:all" -> Icons.Rounded.Folder
    CACHE_FOLDER_BROWSE_ID -> Icons.Rounded.Storage
    else -> null
}

const val SPOTIFY_BROWSE_ID = "app:spotify"

const val CACHE_FOLDER_BROWSE_ID = "local:cache"
