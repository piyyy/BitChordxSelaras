package com.music.bitchord.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.music.bitchord.selaras.ui.LocalSelarasPage
import com.music.bitchord.selaras.ui.SelarasPanel

/**
 * Settings › Equalizer: the Selaras page (from :selarasUi) in the desktop's dialog shell.
 *
 * Selaras is BitChord's equaliser on every platform. On Linux with the Selaras service it shapes
 * the whole system; everywhere else it runs in BitChord's own audio path (see [DesktopAudioEngine]).
 */
@Composable
internal fun DesktopSelarasDialog(onDismiss: () -> Unit) {
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 860) {
        CompositionLocalProvider(LocalSelarasPage provides LocalDesktopPanelIsPage.current) {
            SelarasPanel(
                onDismiss = onDismiss,
                bodyModifier = desktopPanelBody(cardMax = 620.dp),
                doneLabel = DesktopStrings["done", "Done"],
            )
        }
    }
}
