package com.music.bitchord.selaras.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Which file the user is asked for: a Selaras preset (.json) or an AutoEq ParametricEQ.txt. */
enum class SelarasFileKind { PRESET, PROFILE }

/** The platform's own file dialogs: Swing on the desktop, the Storage Access Framework on Android. */
interface SelarasFiles {
    /** Asks where to save [text], suggesting [suggestedName]; reports the saved file's name. */
    fun save(suggestedName: String, text: String, onResult: (Result<String>) -> Unit)

    /** Asks for a file of [kind] and hands over its name and contents; nothing when cancelled. */
    fun open(kind: SelarasFileKind, onText: (name: String, text: String) -> Unit)
}

internal val LocalSelarasFiles = staticCompositionLocalOf<SelarasFiles> { error("No SelarasFiles") }

@Composable
internal expect fun rememberSelarasFiles(): SelarasFiles
