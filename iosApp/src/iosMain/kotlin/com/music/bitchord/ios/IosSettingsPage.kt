package com.music.bitchord.ios

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.screens.AccountCard
import com.music.bitchord.ui.screens.DestructiveRow
import com.music.bitchord.ui.screens.ROW_INSET
import com.music.bitchord.ui.screens.RowDivider
import com.music.bitchord.ui.screens.SegmentedControl
import com.music.bitchord.ui.screens.SettingsGroup
import com.music.bitchord.ui.screens.SettingsRow

/**
 * Settings, built from the phone's own rows, groups and account card (lifted out of
 * SettingsSheet.kt by ios/kmp/gen-settings-rows.py) and its strings — the sections whose
 * settings iOS actually has, in the phone's order.
 */
@Composable
internal fun IosSettingsPage(padding: PaddingValues, listState: LazyListState) {
    val signedIn by IosAccount.signedIn.collectAsStateWithLifecycle()
    val account by IosAccount.account.collectAsStateWithLifecycle()
    val theme by IosSettings.themeMode.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val liquidGlass by AppSettings.liquidGlass.collectAsStateWithLifecycle()
    val fullBleed by IosSettings.fullBleedArtwork.collectAsStateWithLifecycle()
    val legacyMesh by IosSettings.legacyMeshGradient.collectAsStateWithLifecycle()
    val synced by IosSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsBlur by IosSettings.lyricsBlur.collectAsStateWithLifecycle()
    val swipeToPlayNext by IosSettings.swipeToPlayNext.collectAsStateWithLifecycle()
    val hideVolumeBar by IosSettings.hideVolumeBar.collectAsStateWithLifecycle()
    val showQueueHistory by IosSettings.showQueueHistory.collectAsStateWithLifecycle()
    val hideSongStatus by IosSettings.hideSongStatus.collectAsStateWithLifecycle()
    val glassSupported = isGlassSupported()

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
    ) {
        item {
            SettingsGroup(topSpacing = 8.dp) {
                AccountCard(
                    signedIn = signedIn,
                    account = account,
                    onSignIn = { IosPlayer.bridge.openSignIn() },
                    grouped = true,
                )
                if (signedIn) {
                    RowDivider()
                    DestructiveRow(label = stringResource(R.string.sign_out)) { IosPlayer.bridge.signOut() }
                }
            }
        }

        item {
            SettingsGroup(header = stringResource(R.string.playback)) {
                SettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.equalizer),
                    subtitle = stringResource(R.string.equalizer_subtitle),
                    onClick = { IosPlayer.bridge.openSelaras() },
                )
            }
        }

        item {
            SettingsGroup(header = stringResource(R.string.appearance)) {
                SettingsRow(icon = Icons.Rounded.Brightness4, title = stringResource(R.string.theme))
                val labels = listOf(stringResource(R.string.system), stringResource(R.string.light), stringResource(R.string.dark))
                SegmentedControl(
                    options = labels,
                    selectedIndex = IosSettings.THEMES.indexOf(theme).coerceAtLeast(0),
                    onSelect = { IosSettings.setThemeMode(IosSettings.THEMES[it]) },
                    modifier = Modifier.padding(start = ROW_INSET, end = ROW_INSET, bottom = 14.dp),
                )
                RowDivider()
                ToggleRow(Icons.Rounded.MotionPhotosOff, R.string.reduce_animation, R.string.reduce_animation_subtitle, reduceAnimation) {
                    AppSettings.setReduceAnimation(it)
                }
                RowDivider()
                ToggleRow(Icons.Rounded.BlurOff, R.string.reduce_dynamic_blur, R.string.reduce_dynamic_blur_subtitle, reduceDynamicBlur) {
                    AppSettings.setReduceDynamicBlur(it)
                }
                RowDivider()
                ToggleRow(
                    Icons.Rounded.AutoAwesome,
                    R.string.liquid_glass,
                    if (glassSupported) R.string.liquid_glass_subtitle else R.string.liquid_glass_unavailable,
                    liquidGlass,
                    enabled = glassSupported,
                ) { AppSettings.setLiquidGlass(it) }
                RowDivider()
                ToggleRow(Icons.Rounded.Fullscreen, R.string.full_screen_cover_art, R.string.full_screen_cover_art_subtitle, fullBleed) {
                    IosSettings.set(IosSettings.fullBleedArtwork, "fullBleedArtwork", it)
                }
                RowDivider()
                ToggleRow(Icons.Rounded.Gradient, R.string.legacy_mesh_gradient, R.string.legacy_mesh_gradient_subtitle, legacyMesh) {
                    IosSettings.set(IosSettings.legacyMeshGradient, "legacyMeshGradient", it)
                }
                RowDivider()
                ToggleRow(Icons.AutoMirrored.Rounded.Notes, R.string.synced_lyrics, R.string.synced_lyrics_subtitle, synced) {
                    IosSettings.set(IosSettings.syncedLyrics, "syncedLyrics", it)
                }
                RowDivider()
                ToggleRow(Icons.Rounded.BlurOn, R.string.blur_unfocused_lyrics, R.string.blur_unfocused_lyrics_subtitle, lyricsBlur) {
                    IosSettings.set(IosSettings.lyricsBlur, "lyricsBlur", it)
                }
            }
        }

        item {
            SettingsGroup(header = stringResource(R.string.miscellaneous)) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = stringResource(R.string.play_next_on_swipe),
                    subtitle = stringResource(if (swipeToPlayNext) R.string.swipe_plays_next else R.string.swipe_adds_to_queue),
                    trailing = { PrimarySwitch(swipeToPlayNext) { IosSettings.set(IosSettings.swipeToPlayNext, "swipeToPlayNext", it) } },
                    onClick = { IosSettings.set(IosSettings.swipeToPlayNext, "swipeToPlayNext", !swipeToPlayNext) },
                )
                RowDivider()
                ToggleRow(Icons.AutoMirrored.Rounded.VolumeOff, R.string.hide_volume_bar, R.string.hide_volume_bar_subtitle, hideVolumeBar) {
                    IosSettings.set(IosSettings.hideVolumeBar, "hideVolumeBar", it)
                }
                RowDivider()
                ToggleRow(Icons.Rounded.History, R.string.show_queue_history, R.string.show_queue_history_subtitle, showQueueHistory) {
                    IosSettings.set(IosSettings.showQueueHistory, "showQueueHistory", it)
                }
                RowDivider()
                ToggleRow(Icons.Rounded.VisibilityOff, R.string.hide_song_status, R.string.hide_song_status_subtitle, hideSongStatus) {
                    IosSettings.set(IosSettings.hideSongStatus, "hideSongStatus", it)
                }
            }
        }

        item {
            Text(
                "BitChord for iOS · BitChordxSelaras",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PrimarySwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** The phone's switch row: icon, title, subtitle, a primary-coloured switch, the whole row tappable. */
@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: Int,
    subtitle: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingsRow(
        icon = icon,
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        enabled = enabled,
        trailing = { PrimarySwitch(checked, enabled, onChange) },
        onClick = { onChange(!checked) },
    )
}
