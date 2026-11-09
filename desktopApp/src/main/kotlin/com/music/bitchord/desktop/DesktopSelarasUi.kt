package com.music.bitchord.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Selaras inside BitChord: every page of the Selaras window, drawn the BitChord way.
 *
 * Opened from Settings › Equalizer (and from its own Selaras row). All of it edits the same
 * `~/.config/selaras/state.json` the Selaras window, its tray and `selaras-ctl` do, and is applied
 * system-wide by the Selaras daemon, so BitChord's own in-app equaliser stands aside while this is
 * in charge (see [DesktopApp]'s equaliser effect).
 *
 * The tabs follow the Selaras window: Equalizer, Tone & Dynamics, and its Settings tabs
 * (Headphones, Devices — "General" there —, Controls, Visualizer, Advanced). The one thing drawn
 * elsewhere is the projectM visualizer itself, which lives in the Selaras window: the switch here
 * opens it there.
 */
@Composable
internal fun DesktopSelarasDialog(onDismiss: () -> Unit) {
    val snapshot by Selaras.state.collectAsState()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(SelarasTab.EQUALIZER) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastId by remember { mutableLongStateOf(0L) }
    val say: (String) -> Unit = { toast = it; toastId++ }

    // Kept in step with the Selaras window, selaras-ctl, its shortcuts and the output device.
    LaunchedEffect(Unit) {
        Selaras.refresh(force = true)
        while (true) {
            delay(1500)
            Selaras.refresh()
        }
    }
    // Meters and spectrum while the page is open, as the Selaras window does while it is shown.
    DisposableEffect(Unit) {
        Selaras.Spectrum.start()
        onDispose { Selaras.Spectrum.stop() }
    }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            frame++
        }
    }
    LaunchedEffect(toastId) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 860) {
        val snap = snapshot
        Header(snap, frame, say)
        if (snap == null) {
            Text("Reading Selaras…", color = DesktopSecondary, modifier = Modifier.padding(horizontal = panelInset(22.dp), vertical = 24.dp))
        } else {
            if (!snap.daemonRunning) {
                Banner("The Selaras service isn't running, so changes won't be heard yet.", "Start") {
                    scope.launch { Selaras.startDaemon() }
                }
            }
            TabBar(tab, onSelect = { tab = it })
            Column(
                Modifier
                    .then(desktopPanelBody(cardMax = 620.dp))
                    .verticalScroll(rememberScrollState()),
            ) {
                when (tab) {
                    SelarasTab.EQUALIZER -> EqualizerTab(snap, frame, say, onOpenTone = { tab = SelarasTab.TONE })
                    SelarasTab.TONE -> ToneTab(snap, frame)
                    SelarasTab.HEADPHONES -> HeadphonesTab(snap, say)
                    SelarasTab.DEVICES -> DevicesTab(snap, say)
                    SelarasTab.CONTROLS -> ControlsTab(snap, say)
                    SelarasTab.VISUALIZER -> VisualizerTab(snap, say)
                    SelarasTab.ADVANCED -> AdvancedTab(snap)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = panelInset(20.dp), vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { Selaras.openApp() }) { Text("Open Selaras window", color = DesktopSecondary) }
            Text(
                toast.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(DesktopStrings["done", "Done"], color = Color.White) }
        }
    }
}

private enum class SelarasTab(val title: String) {
    EQUALIZER("Equalizer"),
    TONE("Tone & Dynamics"),
    HEADPHONES("Headphones"),
    DEVICES("Devices"),
    CONTROLS("Controls"),
    VISUALIZER("Visualizer"),
    ADVANCED("Advanced"),
}

// ---- Header and tabs -------------------------------------------------------------------------------

@Composable
private fun Header(snap: Selaras.Snapshot?, frame: Int, say: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = panelInset(22.dp), end = panelInset(18.dp), top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Selaras", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                snap?.let { "System-wide equalizer · ${it.deviceName}" } ?: "System-wide equalizer",
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Output meters, as in the Selaras sidebar.
        Column(Modifier.width(120.dp).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            frame.let { }
            MeterRow("L", Selaras.Spectrum.levelL)
            MeterRow("R", Selaras.Spectrum.levelR)
        }
        if (Selaras.visualizerAvailable) {
            TextButton(onClick = { say(Selaras.runAction("visualizer")) }) { Text("Visualizer", color = Color.White) }
        }
        val on = snap?.enabled ?: false
        Text(if (on) "EQ on" else "EQ off", style = MaterialTheme.typography.labelLarge, color = if (on) Color.White else DesktopSecondary)
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = on,
            onCheckedChange = { say(Selaras.runAction("toggle")) },
            enabled = snap != null,
            colors = desktopSwitchColors(),
        )
    }
}

@Composable
private fun MeterRow(label: String, db: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DesktopSecondary, modifier = Modifier.width(12.dp))
        Meter(dbFraction(db), Modifier.weight(1f).height(5.dp))
    }
}

@Composable
private fun Meter(fraction: Float, modifier: Modifier, color: Color = DesktopAccent) {
    Canvas(modifier.clip(RoundedCornerShape(3.dp))) {
        drawRect(Color.White.copy(alpha = 0.12f))
        drawRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}

private fun dbFraction(db: Double): Float = ((db + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)

@Composable
private fun TabBar(tab: SelarasTab, onSelect: (SelarasTab) -> Unit) {
    val tabs = SelarasTab.entries.filter { it != SelarasTab.VISUALIZER || Selaras.visualizerAvailable }
    Row(
        Modifier
            .padding(horizontal = panelInset(22.dp), vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { option ->
            val selected = option == tab
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.92f) else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.Black else Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---- Equalizer -------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerTab(snap: Selaras.Snapshot, frame: Int, say: (String) -> Unit, onOpenTone: () -> Unit) {
    val s = snap.settings
    val profiles = snap.profiles
    val root = snap.root

    // Quick buttons, as chosen under Controls, plus the preamp chip that leads to Tone & Dynamics.
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp), vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Selaras.quickButtons(root).filter { it in Selaras.CHIP_ACTIONS }.forEach { aid ->
            val (title, value, on) = chipState(aid, s)
            StatusChip(title, value, on) { say(Selaras.runAction(aid)) }
        }
        StatusChip(
            if (s.selBool("auto_preamp", true)) "Preamp · auto" else "Preamp · manual",
            "${formatDb(Selaras.totalPreamp(s, profiles))} dB",
            false,
            dashed = true,
            onClick = onOpenTone,
        )
    }

    // Presets: previous, the row of them, next, save and the rest.
    var saving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val current = s["preset"] as? String ?: Selaras.CUSTOM
    Row(
        Modifier.fillMaxWidth().padding(horizontal = panelInset(18.dp), vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallButton("‹") { say(Selaras.runAction("prev-preset")) }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (current == Selaras.CUSTOM) Chip("Custom · unsaved", selected = true) {}
            Selaras.presetCycle(root).forEach { name ->
                Chip(name, selected = name == current) {
                    Selaras.pickPreset(name)
                    say("Preset: $name")
                }
            }
        }
        SmallButton("›") { say(Selaras.runAction("next-preset")) }
        TextButton(onClick = { saving = !saving }) {
            Text("Save preset", color = if (current == Selaras.CUSTOM) Color.White else DesktopSecondary, fontWeight = FontWeight.SemiBold)
        }
        Box {
            SmallButton("⋯") { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Export preset (.json)…") }, onClick = {
                    menu = false
                    exportPreset(s, say)
                })
                DropdownMenuItem(text = { Text("Import preset (.json)…") }, onClick = {
                    menu = false
                    importPreset(say)
                })
                DropdownMenuItem(text = { Text("Delete this preset") }, onClick = {
                    menu = false
                    say(Selaras.deleteCurrentPreset())
                })
            }
        }
    }
    if (saving) SavePresetRow(root, onDone = { name ->
        saving = false
        if (name != null) {
            Selaras.savePreset(name)
            say("Saved preset “$name”")
        }
    })

    // Frequency response, with the graphic or parametric editor under it.
    val graphic = s["eq_mode"] == "graphic"
    Row(
        Modifier.fillMaxWidth().padding(start = panelInset(22.dp), end = panelInset(22.dp), top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Frequency response", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Text("  ·  Preset: $current", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Segmented(listOf("graphic" to "Graphic", "parametric" to "Parametric"), if (graphic) "graphic" else "parametric") { mode ->
            if (mode != s["eq_mode"]) Selaras.editCustom { it["eq_mode"] = mode }
        }
    }
    ResponseGraph(s, profiles, snap.enabled, snap.root.selMap("options").selBool("spectrum", true), frame)

    if (graphic) {
        GraphicBands(s)
    } else {
        ParametricTable(s, say)
    }

    var layoutOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp), vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (graphic) {
                "${s.selInt("band_count", 10)} bands · ${formatHz(s.selDouble("freq_start", 31.0))} – ${formatHz(s.selDouble("freq_end", 16000.0))} · double-click a slider to reset it"
            } else {
                "${s.selList("parametric").size} parametric bands · edit type, frequency, gain and Q"
            },
            style = MaterialTheme.typography.bodySmall,
            color = DesktopSecondary,
            modifier = Modifier.weight(1f),
        )
        if (graphic) TextButton(onClick = { layoutOpen = !layoutOpen }) { Text("Bands", color = Color.White) }
        TextButton(onClick = {
            Selaras.pickPreset("Flat")
            say("Preset: Flat")
        }) { Text("Reset to flat", color = Color.White) }
    }
    if (graphic && layoutOpen) BandLayout(s)
}

private fun chipState(aid: String, s: Map<String, Any?>): Triple<String, String, Boolean> {
    val prof = s["profile"] as? String
    val lim = s.selMap("limiter")
    val comp = s.selMap("compressor")
    val bal = s.selDouble("balance", 0.0)
    return when (aid) {
        "profile" -> Triple("Headphone profile", prof ?: "Off", prof != null)
        "limiter" -> Triple("Limiter", if (lim.selBool("enabled", true)) "On" else "Off", lim.selBool("enabled", true))
        "compressor" -> Triple(
            "Compressor",
            if (comp.selBool("enabled", false)) listOf("Light", "Medium", "Strong")[comp.selInt("strength", 1).coerceIn(0, 2)] else "Off",
            comp.selBool("enabled", false),
        )
        "balance-center" -> Triple("Balance", formatBalance(bal), false)
        else -> Triple("Equalizer", "Reset to flat", false)
    }
}

@Composable
private fun SavePresetRow(root: Map<String, Any?>, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val taken = trimmed.isNotEmpty() && Selaras.presetNameTaken(root, trimmed)
    Column(
        Modifier
            .padding(horizontal = panelInset(22.dp), vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp),
    ) {
        Text("Save as preset", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Text("Saves the current EQ. It appears with the other presets.", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(name, { name = it }, "e.g. Late night podcast", Modifier.weight(1f)) {
                if (trimmed.isNotEmpty() && !taken) onDone(trimmed)
            }
            TextButton(onClick = { onDone(null) }) { Text("Cancel", color = DesktopSecondary) }
            TextButton(onClick = { onDone(trimmed) }, enabled = trimmed.isNotEmpty() && !taken) {
                Text("Save preset", color = if (trimmed.isNotEmpty() && !taken) Color.White else DesktopSecondary)
            }
        }
        if (taken) Text("A preset with this name already exists.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB340))
    }
}

@Composable
private fun BandLayout(s: Map<String, Any?>) {
    val count = s.selInt("band_count", 10)
    val start = s.selDouble("freq_start", 31.0)
    val end = s.selDouble("freq_end", 16000.0)
    Column(
        Modifier
            .padding(horizontal = panelInset(22.dp), vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp),
    ) {
        Text("Band layout", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Selaras.BAND_COUNTS.forEach { n ->
                Chip("$n bands", selected = n == count) { if (n != count) Selaras.relayout(n, start, end) }
            }
        }
        Spacer(Modifier.height(8.dp))
        LabeledNumber("First band (Hz)", start, 16.0, 1000.0, 1.0, 0) { v -> if (end > v * 2) Selaras.relayout(count, v, end) }
        LabeledNumber("Last band (Hz)", end, 2000.0, 22000.0, 100.0, 0) { v -> if (v > start * 2) Selaras.relayout(count, start, v) }
    }
}

@Composable
private fun GraphicBands(s: Map<String, Any?>) {
    val freqs = Selaras.bandFreqs(s)
    val bands = s.selDoubles("bands")
    val dense = freqs.size > 16
    val content: @Composable RowScope.() -> Unit = {
        freqs.forEachIndexed { index, hz ->
            val value = bands.getOrElse(index) { 0.0 }
            Column(
                if (dense) Modifier.width(40.dp) else Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    formatGain(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (abs(value) < 0.05) DesktopSecondary else DesktopAccent,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Fader(value) { updated ->
                    Selaras.editCustom { st ->
                        (st["bands"] as? MutableList<Any?>)?.let { if (index < it.size) it[index] = updated }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(formatHz(hz), style = MaterialTheme.typography.labelSmall, color = DesktopSecondary, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
    if (dense) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = panelInset(14.dp), vertical = 8.dp), content = content)
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(14.dp), vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, content = content)
    }
}

@Composable
private fun Fader(value: Double, onChange: (Double) -> Unit) {
    val range = Selaras.GAIN_LIMIT
    val latest by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    var lastTap by remember { mutableLongStateOf(0L) }
    val knob = 8.dp
    Canvas(
        Modifier
            .width(26.dp)
            .height(170.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    // Double-click resets the band, as in Selaras.
                    if (down.uptimeMillis - lastTap < 350) {
                        lastTap = 0L
                        change(0.0)
                        return@awaitEachGesture
                    }
                    lastTap = down.uptimeMillis
                    var last = latest
                    fun report(offset: Offset) {
                        val inset = knob.toPx()
                        val usable = (size.height - inset * 2).coerceAtLeast(1f)
                        val fraction = ((offset.y - inset) / usable).coerceIn(0f, 1f)
                        val raw = (1.0 - fraction * 2.0) * range
                        val snapped = if (abs(raw) < 0.4) 0.0 else (raw * 2).roundToInt() / 2.0
                        if (abs(snapped - last) > 0.001) {
                            last = snapped
                            change(snapped)
                        }
                    }
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) {
                            c.consume()
                            if (!moved) report(c.position)
                            break
                        }
                        if (c.positionChanged()) {
                            moved = true
                            c.consume()
                            report(c.position)
                        }
                    }
                }
            },
    ) {
        val inset = knob.toPx()
        val usable = size.height - inset * 2
        val centreY = inset + usable / 2
        val track = 4.dp.toPx()
        val centreX = size.width / 2
        val knobY = inset + usable * (1f - ((value / range).toFloat() + 1f) / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(centreX - track / 2, inset), Size(track, usable), CornerRadius(track / 2))
        if (abs(value) > 0.05) {
            drawRoundRect(DesktopAccent, Offset(centreX - track / 2, minOf(centreY, knobY)), Size(track, abs(centreY - knobY)), CornerRadius(track / 2))
        }
        drawLine(Color.White.copy(alpha = 0.35f), Offset(centreX - track, centreY), Offset(centreX + track, centreY), 1.dp.toPx())
        drawCircle(Color.Black.copy(alpha = 0.25f), inset + 1.5f, Offset(centreX, knobY + 1.5f))
        drawCircle(Color.White, inset, Offset(centreX, knobY))
    }
}

@Composable
private fun ParametricTable(s: Map<String, Any?>, say: (String) -> Unit) {
    val filters = s.selList("parametric").mapNotNull { it.asStringMap() }
    Column(
        Modifier
            .padding(horizontal = panelInset(22.dp), vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("#" to 22.dp, "ON" to 56.dp, "TYPE" to 110.dp, "FREQUENCY" to 140.dp, "GAIN" to 130.dp, "Q" to 120.dp).forEach { (t, w) ->
                Text(t, style = MaterialTheme.typography.labelSmall, color = DesktopSecondary, modifier = Modifier.width(w))
            }
        }
        if (filters.isEmpty()) {
            Text(
                "No bands yet — add one, or copy the graphic EQ",
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
        filters.forEachIndexed { i, f ->
            val type = f["type"] as? String ?: "PK"
            val cut = type in listOf("LP", "HP", "NO")
            Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary, modifier = Modifier.width(22.dp))
                Box(Modifier.width(56.dp)) {
                    Switch(f.selBool("on", true), { on -> Selaras.updateParametric(i) { it["on"] = on } }, colors = desktopSwitchColors())
                }
                Box(Modifier.width(110.dp)) {
                    var open by remember { mutableStateOf(false) }
                    TextButton(onClick = { open = true }) { Text(Selaras.TYPE_NAMES[type] ?: type, color = Color.White, maxLines = 1) }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        Selaras.PARAMETRIC_TYPES.forEach { t ->
                            DropdownMenuItem(text = { Text(Selaras.TYPE_NAMES[t] ?: t) }, onClick = {
                                open = false
                                Selaras.updateParametric(i) { it["type"] = t }
                            })
                        }
                    }
                }
                NumberStepper(f.selDouble("fc", 1000.0), 10.0, 22000.0, 1.0, 0, Modifier.width(140.dp)) { v -> Selaras.updateParametric(i) { it["fc"] = v } }
                NumberStepper(f.selDouble("gain", 0.0), -15.0, 15.0, 0.1, 1, Modifier.width(130.dp), enabled = !cut) { v -> Selaras.updateParametric(i) { it["gain"] = v } }
                NumberStepper(f.selDouble("q", 1.0), 0.1, 20.0, 0.01, 2, Modifier.width(120.dp)) { v -> Selaras.updateParametric(i) { it["q"] = v } }
                SmallButton("✕") { Selaras.deleteParametric(i) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(18.dp)), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = {
            Selaras.parametricFromGraphic()
            say("Copied the graphic bands")
        }) { Text("Copy from graphic", color = Color.White) }
        TextButton(onClick = {
            if (!Selaras.addParametric()) say("Up to ${Selaras.MAX_PARAMETRIC} parametric bands")
        }) { Text("+ Add band", color = Color.White) }
    }
}

@Composable
private fun ResponseGraph(s: Map<String, Any?>, profiles: Map<String, Any?>, enabled: Boolean, spectrum: Boolean, frame: Int) {
    val freqs = remember { Selaras.logFreqs(160) }
    val curve = remember(s, profiles) { Selaras.responseCurve(s, profiles, freqs) }
    Box(
        Modifier
            .padding(horizontal = panelInset(22.dp), vertical = 8.dp)
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            frame.let { }
            val span = 15.0
            fun y(db: Double) = (size.height / 2 - (db.coerceIn(-span, span) / span) * (size.height / 2 - 8)).toFloat()
            val lo = ln(20.0)
            val hi = ln(20000.0)
            fun x(f: Double) = ((ln(f) - lo) / (hi - lo) * size.width).toFloat()
            // Live spectrum behind the curve.
            if (spectrum && enabled) {
                val levels = Selaras.Spectrum.levels
                val w = size.width / Selaras.Spectrum.BARS
                levels.forEachIndexed { i, db ->
                    val h = dbFraction(db + 10) * size.height
                    drawRect(Color.White.copy(alpha = 0.07f), Offset(i * w + 1, size.height - h), Size(w - 2, h))
                }
            }
            listOf(100.0, 1000.0, 10000.0).forEach { f ->
                drawLine(Color.White.copy(alpha = 0.06f), Offset(x(f), 0f), Offset(x(f), size.height), 1.dp.toPx())
            }
            drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.dp.toPx())
            val path = Path()
            curve.forEachIndexed { i, db ->
                if (i == 0) path.moveTo(x(freqs[i]), y(db)) else path.lineTo(x(freqs[i]), y(db))
            }
            drawPath(path, if (enabled) DesktopAccent else DesktopSecondary, style = Stroke(width = 2.dp.toPx()))
        }
        if (!enabled) {
            Text(
                "Equalizer is off · sound passes through unchanged",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

// ---- Tone & Dynamics -------------------------------------------------------------------------------

@Composable
private fun ToneTab(snap: Selaras.Snapshot, frame: Int) {
    val s = snap.settings
    val profiles = snap.profiles

    Label("Tone")
    Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp)), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple("bass", "Bass", "Shelf below") to Selaras.BASS_FREQS, Triple("treble", "Treble", "Shelf above") to Selaras.TREBLE_FREQS)
            .forEach { (meta, freqs) ->
                val (key, name, shelf) = meta
                Card(Modifier.weight(1f)) {
                    SliderBlock(name, s.selDouble(key, 0.0), -Selaras.TONE_LIMIT, Selaras.TONE_LIMIT, { "${formatDb(it)} dB" }, 0.5) { v ->
                        Selaras.editCustom { it[key] = v }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(shelf, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary, modifier = Modifier.weight(1f))
                        SmallButton("−") { Selaras.stepToneFrequency(key, freqs, -1) }
                        Text(
                            formatHz(s.selDouble("${key}_freq", freqs.first())) + " Hz",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(72.dp),
                        )
                        SmallButton("+") { Selaras.stepToneFrequency(key, freqs, 1) }
                    }
                }
            }
    }

    Label("Gain & balance")
    Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp)), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.weight(1f)) {
            val auto = s.selBool("auto_preamp", true)
            val total = Selaras.totalPreamp(s, profiles)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Preamp", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text("Auto lowers gain just enough that boosts never clip", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
                }
                Text("Auto", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Switch(auto, Selaras::setAutoPreamp, colors = desktopSwitchColors())
            }
            SliderBlock(
                if (auto) "Preamp · auto" else "Preamp · manual",
                if (auto) total else s.selDouble("preamp", 0.0),
                -24.0, 12.0, { "${formatDb(if (auto) total else it)} dB" }, 0.1,
                enabled = !auto,
            ) { v -> Selaras.edit { _, st -> st["preamp"] = v } }
        }
        Card(Modifier.weight(1f)) {
            val bal = s.selDouble("balance", 0.0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Balance", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text("Shift the stereo image left or right", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
                }
                TextButton(onClick = { Selaras.runAction("balance-center") }, enabled = abs(bal) >= 0.5) {
                    Text("Center", color = if (abs(bal) >= 0.5) Color.White else DesktopSecondary)
                }
            }
            SliderBlock("Offset", bal, -100.0, 100.0, ::formatBalance, 1.0, detent = 2.0) { v ->
                Selaras.edit { _, st -> st["balance"] = v }
            }
        }
    }

    Label("Dynamics")
    val dyn = Selaras.dynamicsAvailable
    if (!dyn) {
        Text(
            "Install lsp-plugins-lv2 to enable the limiter and compressor:  sudo dnf install lsp-plugins-lv2",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFB340),
            modifier = Modifier.padding(horizontal = panelInset(22.dp), vertical = 4.dp),
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp)).graphicsLayer { alpha = if (dyn) 1f else 0.45f },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val lim = s.selMap("limiter")
        val limOn = lim.selBool("enabled", true)
        Card(Modifier.weight(1f)) {
            ToggleHeader("Limiter", "Catches peaks before they clip the output", limOn, dyn) { on ->
                Selaras.edit { _, st -> (st["limiter"] as? MutableMap<String, Any?>)?.set("enabled", on) }
            }
            Column(Modifier.graphicsLayer { alpha = if (limOn) 1f else 0.4f }) {
                frame.let { }
                val peak = maxOf(Selaras.Spectrum.levelL, Selaras.Spectrum.levelR)
                Row {
                    Text("Output peak", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary, modifier = Modifier.weight(1f))
                    Text(if (peak > -59) "${formatDb(peak)} dB" else "—", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Color(0xFFFFB340))
                }
                Meter(dbFraction(peak), Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp), Color(0xFFFFB340))
                SliderBlock("Ceiling", lim.selDouble("threshold", -1.0), -12.0, 0.0, { "${formatDb(it)} dB" }, 0.1, enabled = dyn) { v ->
                    Selaras.edit { _, st -> (st["limiter"] as? MutableMap<String, Any?>)?.set("threshold", v) }
                }
            }
        }
        val comp = s.selMap("compressor")
        val compOn = comp.selBool("enabled", false)
        Card(Modifier.weight(1f)) {
            ToggleHeader("Compressor", "Evens out loud and quiet parts · good for night listening", compOn, dyn) { on ->
                Selaras.edit { _, st -> (st["compressor"] as? MutableMap<String, Any?>)?.set("enabled", on) }
            }
            Box(Modifier.graphicsLayer { alpha = if (compOn) 1f else 0.45f }) {
                Segmented(listOf("0" to "Light", "1" to "Medium", "2" to "Strong"), comp.selInt("strength", 1).toString()) { k ->
                    if (compOn && dyn) Selaras.edit { _, st -> Selaras.setCompressorStrength(st, k.toInt()) }
                }
            }
        }
    }
}

// ---- Headphones ------------------------------------------------------------------------------------

@Composable
private fun HeadphonesTab(snap: Selaras.Snapshot, say: (String) -> Unit) {
    val s = snap.settings
    val prof = s["profile"] as? String
    val p = snap.profiles[prof ?: ""].asStringMap()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Selaras.AutoEqEntry>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Label("Headphone profile")
    Card(Modifier.padding(horizontal = panelInset(22.dp))) {
        Text(prof ?: "No headphone profile", style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (p != null) {
                "${p.selList("filters").size} AutoEq filters · preamp ${formatDb(p.selDouble("preamp", 0.0))} dB · applied under the EQ"
            } else {
                "Pick your headphones from the AutoEq database (8,800+ models)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = DesktopSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Text("Saved profiles", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("None", selected = prof == null) {
                Selaras.selectProfile(null)
                say("Headphone profile off")
            }
            snap.profiles.keys.sorted().forEach { name ->
                Chip(name, selected = name == prof) {
                    Selaras.selectProfile(name)
                    say("Headphone profile: $name")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { importProfile(say) }) { Text("Import file…", color = Color.White) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                if (prof == null) {
                    say("No headphone profile is selected")
                } else {
                    Selaras.deleteProfile(prof)
                    say("Removed “$prof”")
                }
            }) { Text("Remove", color = DesktopDestructive) }
        }
    }

    Label("Browse AutoEq database")
    DesktopSearchField(
        query = query,
        onQueryChange = { query = it },
        onSearch = {
            if (busy || query.isBlank()) return@DesktopSearchField
            busy = true
            status = "Searching AutoEq…"
            scope.launch {
                runCatching { Selaras.search(Selaras.autoEqIndex(), query) }
                    .onSuccess {
                        results = it
                        status = if (it.isEmpty()) "No headphones match “$query”" else null
                    }
                    .onFailure { status = "Couldn't load the AutoEq list: ${it.message}" }
                busy = false
            }
        },
        placeholder = "Headphone model, then press Enter",
        modifier = Modifier.padding(horizontal = panelInset(22.dp)),
    )
    status?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary, modifier = Modifier.padding(horizontal = panelInset(22.dp), vertical = 6.dp))
    }
    Column(Modifier.padding(horizontal = panelInset(14.dp), vertical = 4.dp)) {
        results.forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy) {
                        busy = true
                        status = "Downloading ${entry.name}…"
                        scope.launch {
                            runCatching { Selaras.installProfile(entry) }
                                .onSuccess {
                                    status = null
                                    say("Using “${entry.profileName}”")
                                    results = emptyList()
                                    query = ""
                                }
                                .onFailure { status = "Download failed: ${it.message}" }
                            busy = false
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(10.dp))
                Text(entry.source.substringBefore(" on "), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
    Note(
        "A headphone profile corrects your headphones' frequency response (AutoEq, 8,800+ models). " +
            "It is applied under your EQ, so presets and the bands still work on top of it.",
    )
}

// ---- Devices (Selaras' "General") -------------------------------------------------------------------

@Composable
private fun DevicesTab(snap: Selaras.Snapshot, say: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val o = snap.root.selMap("options")
    var login by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { login = Selaras.loginEnabled() }

    Label("Startup")
    ToggleRow("Launch at login", "Start Selaras and its equalizer when you sign in", login ?: false) { on ->
        login = on
        scope.launch { Selaras.setLogin(on) }
        say(if (on) "Starts at login" else "Won't start at login")
    }
    ToggleRow("Tray icon", "Click it for the tray panel · scroll on it to change preset", o.selBool("tray_icon", true)) { on ->
        Selaras.setOption("tray_icon", on)
    }

    Label("Outputs")
    ToggleRow("Per-device EQ", "Each output (speaker, headphones, Bluetooth) keeps its own EQ", o.selBool("per_device", true)) { on ->
        Selaras.setOption("per_device", on)
    }
    ToggleRow("Pause on disconnect", "Pause players when headphones are unplugged", o.selBool("pause_on_disconnect", false)) { on ->
        Selaras.setOption("pause_on_disconnect", on)
    }
    ToggleRow("Resume on connect", "Play again when headphones or Bluetooth reconnect", o.selBool("resume_on_connect", false)) { on ->
        Selaras.setOption("resume_on_connect", on)
    }

    Label("Remembered devices")
    val devices = snap.root.selMap("devices")
    val names = snap.root.selMap("device_names")
    var confirm by remember { mutableStateOf<String?>(null) }
    if (devices.isEmpty()) {
        Note("No remembered devices. Selaras will remember an output the next time you adjust its EQ.")
    }
    devices.keys.sorted().forEach { key ->
        val d = devices.selMap(key)
        Card(Modifier.padding(horizontal = panelInset(22.dp), vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(names[key] as? String ?: key, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (key == snap.deviceKey) "Active · " else "") + "Preset ${d["preset"] ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (key == snap.deviceKey) Color.White else DesktopSecondary,
                    )
                }
                if (confirm != key) TextButton(onClick = { confirm = key }) { Text("Forget", color = DesktopDestructive) }
            }
            if (confirm == key) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Forget this device? Its saved EQ will be removed.", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirm = null }) { Text("Cancel", color = DesktopSecondary) }
                    TextButton(onClick = {
                        confirm = null
                        Selaras.forgetDevice(key)
                        say("Device forgotten")
                    }) { Text("Forget device", color = DesktopDestructive, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ---- Controls ---------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsTab(snap: Selaras.Snapshot, say: (String) -> Unit) {
    val root = snap.root
    val quick = Selaras.quickButtons(root)
    Row(Modifier.fillMaxWidth().padding(end = panelInset(22.dp)), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { Label("Quick buttons") }
        Text(
            "${quick.count { it in Selaras.CHIP_ACTIONS }} of ${Selaras.CHIP_ACTIONS.size} shown on the Equalizer",
            style = MaterialTheme.typography.bodySmall,
            color = DesktopSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    Note("Status buttons shown under the Equalizer title")
    FlowRow(Modifier.padding(horizontal = panelInset(18.dp)), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Selaras.CHIP_ACTIONS.forEach { aid ->
            Chip((if (aid in quick) "✓ " else "") + (Selaras.ACTIONS[aid]?.first ?: aid), selected = aid in quick) { Selaras.toggleQuick(aid) }
        }
    }

    Label("Favourite presets")
    Note("One-tap presets in the tray panel · up to 3")
    val favs = (root.selMap("options")["favourites"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    FlowRow(
        Modifier.padding(horizontal = panelInset(18.dp)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Selaras.presetCycle(root).forEach { name ->
            Chip((if (name in favs) "★ " else "☆ ") + name, selected = name in favs) {
                if (!Selaras.toggleFavourite(name)) say("Up to 3 favourites — remove one first")
            }
        }
    }

    Row(Modifier.fillMaxWidth().padding(end = panelInset(18.dp)), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { Label("Keyboard shortcuts") }
        TextButton(onClick = { if (!Selaras.openShortcutSettings()) say("Open your desktop's keyboard shortcut settings") }) {
            Text("Edit shortcuts", color = Color.White)
        }
    }
    Note("All start with Alt + Super and work even when the window is closed (registered by the Selaras service through the desktop's shortcut portal).")
    Card(Modifier.padding(horizontal = panelInset(22.dp))) {
        Selaras.ACTIONS.forEach { (_, meta) ->
            val (text, trigger) = meta
            val last = trigger.substringAfterLast('+')
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                Text(
                    if (trigger.isEmpty()) "—" else (KEY_NAMES[last] ?: last.uppercase()),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (trigger.isEmpty()) DesktopSecondary else Color.White,
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }

    Label("Command line")
    val cmds = listOf("selaras-ctl toggle", "selaras-ctl next-preset", "selaras-ctl preset:Rock", "selaras-ctl status")
    Card(Modifier.padding(horizontal = panelInset(22.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                cmds.forEach { Text("$ $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Color.White) }
            }
            TextButton(onClick = {
                runCatching {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(java.awt.datatransfer.StringSelection(cmds.joinToString("\n")), null)
                }
                say("Commands copied")
            }) { Text("Copy", color = Color.White) }
        }
    }
}

private val KEY_NAMES = mapOf("Right" to "→", "Left" to "←", "Up" to "↑", "Down" to "↓")

// ---- Visualizer -------------------------------------------------------------------------------------

@Composable
private fun VisualizerTab(snap: Selaras.Snapshot, say: (String) -> Unit) {
    val o = snap.root.selMap("options")
    Label("Visualizer")
    ToggleRow("Show visualizer", "MilkDrop presets (projectM) driven by what is playing · shown in the Selaras window", o.selBool("visualizer", false)) { on ->
        Selaras.setOption("visualizer", on)
        if (on) Selaras.openApp("--visualizer")
        say(if (on) "Visualizer on" else "Visualizer off")
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp), vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Style", style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text("Background keeps the controls on top · Full window fills it", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        }
        Box(Modifier.width(260.dp)) {
            Segmented(listOf("background" to "Background", "full" to "Full window"), o["visualizer_style"] as? String ?: "background") {
                Selaras.setOption("visualizer_style", it)
            }
        }
    }
    LabeledNumber("Change visual every (seconds)", o.selDouble("visualizer_interval", 20.0), 5.0, 300.0, 5.0, 0) {
        Selaras.setOption("visualizer_interval", it.roundToInt().toLong())
    }
    ToggleRow("Blur background", "Softens the visual behind the controls", o.selBool("visualizer_blur", true)) { Selaras.setOption("visualizer_blur", it) }
    Column(Modifier.padding(horizontal = panelInset(22.dp))) {
        SliderBlock("Dimming", o.selDouble("visualizer_dim", 0.35), 0.0, 0.85, { "${(it * 100).roundToInt()}%" }, 0.05) {
            Selaras.setOption("visualizer_dim", (it * 100).roundToInt() / 100.0)
        }
    }
    Note("Full window: Esc goes back · N or → next visual · F11 or double-click for fullscreen.")
}

// ---- Advanced ---------------------------------------------------------------------------------------

@Composable
private fun AdvancedTab(snap: Selaras.Snapshot) {
    val o = snap.root.selMap("options")
    Label("Display")
    ToggleRow("Spectrum in the graph", "Live bars behind the response curve", o.selBool("spectrum", true)) { Selaras.setOption("spectrum", it) }
    Label("Audio")
    LabeledNumber("Resampler quality · 4 = PipeWire default · 10 = high · 14 = best (more CPU)", o.selDouble("resample_quality", 10.0), 0.0, 14.0, 1.0, 0) {
        Selaras.setOption("resample_quality", it.roundToInt().toLong())
    }
    Label("Appearance")
    Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp), vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Custom skin", style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text("${Selaras.skinPath} · GTK CSS for the Selaras window, loaded when it starts", style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        }
        TextButton(onClick = Selaras::openSkin) { Text("Open", color = Color.White) }
    }
    Label("Engine")
    Note(
        "Dynamics plugins: " + (if (Selaras.dynamicsAvailable) "lsp-plugins-lv2 found" else "lsp-plugins-lv2 missing") +
            " · Visualizer: " + (if (Selaras.visualizerAvailable) "libprojectM 4 found" else "libprojectM 4 missing (Fedora ships version 3)") +
            " · Service: " + (if (snap.daemonRunning) "running" else "stopped"),
    )
}

// ---- Files ------------------------------------------------------------------------------------------

private fun chooser(title: String, filter: FileNameExtensionFilter, dir: File? = null) = JFileChooser(dir ?: File(System.getProperty("user.home"))).apply {
    dialogTitle = title
    fileFilter = filter
}

private fun exportPreset(s: Map<String, Any?>, say: (String) -> Unit) {
    val preset = s["preset"] as? String
    val name = if (preset == null || preset == Selaras.CUSTOM) "My preset" else preset
    val c = chooser("Export preset", FileNameExtensionFilter("Selaras preset (*.json)", "json"))
    c.selectedFile = File(c.currentDirectory, "$name.json")
    if (c.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
    runCatching { c.selectedFile.writeText(Selaras.exportPreset(name, s)) }
        .onSuccess { say("Exported ${c.selectedFile.name}") }
        .onFailure { say("Could not export: ${it.message}") }
}

private fun importPreset(say: (String) -> Unit) {
    val c = chooser("Import preset", FileNameExtensionFilter("Selaras preset (*.json)", "json"))
    if (c.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
    runCatching { Selaras.importPreset(c.selectedFile.readText()) }
        .onSuccess { say("Imported preset “$it”") }
        .onFailure { say("Could not import: ${it.message}") }
}

private fun importProfile(say: (String) -> Unit) {
    val autoEq = File(System.getProperty("user.home"), "AutoEq").takeIf { it.isDirectory }
    val c = chooser("Import AutoEq ParametricEQ.txt", FileNameExtensionFilter("AutoEq parametric EQ (*.txt)", "txt"), autoEq)
    if (c.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
    runCatching { Selaras.importProfileFile(c.selectedFile) }
        .onSuccess { say("Imported “$it”") }
        .onFailure { say("Could not import: ${it.message}") }
}

// ---- Small pieces -----------------------------------------------------------------------------------

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        color = DesktopSecondary,
        modifier = Modifier.padding(start = panelInset(22.dp), top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary, modifier = Modifier.padding(horizontal = panelInset(22.dp), vertical = 6.dp))
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text, maxLines = 1) }, colors = desktopChipColors())
}

@Composable
private fun StatusChip(title: String, value: String, on: Boolean, dashed: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 120.dp, max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .then(if (dashed) Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = DesktopSecondary, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = if (on) Color.White else Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) Color.White.copy(alpha = 0.92f) else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Color.Black else Color.White, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = panelInset(18.dp), vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DesktopDestructive.copy(alpha = 0.16f))
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action, color = Color.White, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ToggleHeader(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        }
        Switch(checked, onChange, enabled = enabled, colors = desktopSwitchColors())
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable { onChange(!checked) }
            .padding(horizontal = panelInset(22.dp), vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        }
        Spacer(Modifier.width(14.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = desktopSwitchColors())
    }
}

@Composable
private fun SliderBlock(
    title: String,
    value: Double,
    min: Double,
    max: Double,
    format: (Double) -> String,
    snap: Double,
    detent: Double = 0.0,
    enabled: Boolean = true,
    onChange: (Double) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = DesktopSecondary)
        }
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { raw ->
                var v = (raw / snap).roundToInt() * snap
                if (abs(v) <= detent) v = 0.0
                v = (v * 100).roundToInt() / 100.0
                if (abs(v - value) > 1e-9) onChange(v)
            },
            enabled = enabled,
            valueRange = min.toFloat()..max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = DesktopAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun TextInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onEnter: () -> Unit) {
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
        )
    }
}

/** A typed number with − and + beside it, committed on Enter or when it loses focus — Selaras' spin buttons. */
@Composable
private fun NumberStepper(
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    digits: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCommit: (Double) -> Unit,
) {
    val shown = String.format(Locale.ROOT, "%.${digits}f", value)
    var text by remember(shown) { mutableStateOf(shown) }
    fun commit(v: Double?) {
        val c = (v ?: value).coerceIn(min, max)
        val rounded = Math.round(c * Math.pow(10.0, digits.toDouble())) / Math.pow(10.0, digits.toDouble())
        text = String.format(Locale.ROOT, "%.${digits}f", rounded)
        if (abs(rounded - value) > 1e-9) onCommit(rounded)
    }
    Row(modifier.graphicsLayer { alpha = if (enabled) 1f else 0.4f }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled) { commit(value - step) }, contentAlignment = Alignment.Center) {
            Text("−", color = Color.White)
        }
        Box(
            Modifier
                .weight(1f)
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused && text != shown) commit(text.replace(',', '.').toDoubleOrNull()) }
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                            commit(text.replace(',', '.').toDoubleOrNull())
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled) { commit(value + step) }, contentAlignment = Alignment.Center) {
            Text("+", color = Color.White)
        }
    }
}

@Composable
private fun LabeledNumber(title: String, value: Double, min: Double, max: Double, step: Double, digits: Int, width: Dp = 160.dp, onCommit: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(22.dp), vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
        NumberStepper(value, min, max, step, digits, Modifier.width(width), onCommit = onCommit)
    }
}

// ---- Formatting -------------------------------------------------------------------------------------

private fun formatDb(v: Double): String = when {
    abs(v) < 0.05 -> "0"
    v > 0 -> String.format(Locale.ROOT, "+%.1f", v)
    else -> String.format(Locale.ROOT, "%.1f", v)
}

private fun formatGain(v: Double): String = formatDb(v).removeSuffix(".0")

private fun formatBalance(v: Double): String = when {
    abs(v) < 0.5 -> "Center"
    v < 0 -> "L ${abs(v).roundToInt()}%"
    else -> "R ${v.roundToInt()}%"
}

private fun formatHz(hz: Double): String = when {
    hz >= 1000 -> {
        val k = hz / 1000
        if (abs(k - k.roundToInt()) < 0.05) "${k.roundToInt()}k" else String.format(Locale.ROOT, "%.1fk", k)
    }
    else -> hz.roundToInt().toString()
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asStringMap(): Map<String, Any?>? = this as? Map<String, Any?>
