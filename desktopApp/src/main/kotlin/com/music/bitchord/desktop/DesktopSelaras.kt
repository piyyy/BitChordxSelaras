package com.music.bitchord.desktop

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * BitChord's equaliser, handed over to Selaras.
 *
 * Selaras is a system-wide PipeWire equaliser: a small daemon (`selaras-daemon`, run as the
 * `selaras.service` user unit) owns a filter-chain and rebuilds it whenever its state file changes
 * and it receives SIGHUP. That file is the whole interface, so BitChord edits the very same
 * `~/.config/selaras/state.json` the Selaras window and `selaras-ctl` do, and every one of them
 * stays in step with the others.
 *
 * Everything here mirrors `selaras/model.py` and `selaras/actions.py`: the per-device settings,
 * the built-in presets, the band layout, the biquads drawn as the response curve and the AutoEq
 * lookup. Keys this side does not know about are carried through untouched.
 */
internal object Selaras {

    // ---- Locations, as selaras.model._dirs() and RUNTIME_DIR ----------------------------------

    private val home = System.getProperty("user.home")
    private val configDir = File(env("XDG_CONFIG_HOME") ?: "$home/.config", "selaras")
    private val cacheDir = File(env("XDG_CACHE_HOME") ?: "$home/.cache", "selaras")
    private val stateFile = File(configDir, "state.json")
    private val runtimeDir = File(
        env("SELARAS_RUNTIME_DIR") ?: ((env("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir")) + "/selaras"),
    )
    private val pidFile = File(runtimeDir, "daemon.pid")

    private fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    private val isLinux = System.getProperty("os.name").contains("Linux", ignoreCase = true)

    /** Finds a Selaras command on PATH, or where a per-user or packaged install puts it. */
    fun executable(name: String): File? {
        val dirs = (System.getenv("PATH") ?: "").split(File.pathSeparator) + listOf("$home/.local/bin", "/usr/bin", "/usr/local/bin")
        return dirs.asSequence().filter { it.isNotBlank() }.map { File(it, name) }.firstOrNull { it.canExecute() }
    }

    /** Whether this machine has Selaras at all. Without it BitChord keeps its own equaliser. */
    val installed: Boolean by lazy { isLinux && executable("selaras-daemon") != null }

    // ---- Constants, as selaras.model ------------------------------------------------------------

    const val CUSTOM = "Custom"
    const val GAIN_LIMIT = 15.0
    const val TONE_LIMIT = 12.0
    private const val SAMPLE_RATE = 48000.0
    private const val SHELF_Q = 0.707
    private const val DEFAULT_START = 31.0
    private const val DEFAULT_END = 16000.0
    private val CLASSIC_FREQS = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    val BUILTIN_PRESETS: LinkedHashMap<String, List<Double>> = linkedMapOf(
        "Flat" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        "Bass Boost" to listOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
        "Deep Bass" to listOf(8, 7, 5, 2, -1, -1, 0, 0, 0, 0),
        "Treble Boost" to listOf(0, 0, 0, 0, 0, 0, 1, 3, 5, 6),
        "Vocal" to listOf(-2, -2, -1, 1, 3, 4, 4, 2, 0, -1),
        "Rock" to listOf(5, 4, 2, -1, -2, -1, 2, 3, 4, 4),
        "Pop" to listOf(-1, 0, 2, 3, 4, 3, 1, 0, -1, -1),
        "Electronic" to listOf(5, 4, 1, 0, -2, 1, 0, 1, 4, 5),
        "Jazz" to listOf(3, 2, 1, 2, -1, -1, 0, 1, 2, 3),
        "Classical" to listOf(4, 3, 2, 1, -1, -1, 0, 2, 3, 4),
        "Loudness" to listOf(5, 3, 0, 0, -1, 0, -1, 0, 3, 4),
        "Acoustic" to listOf(3, 3, 2, 1, 1, 1, 2, 2, 2, 1),
        "V-Shape" to listOf(5, 4, 2, 0, -2, -2, 0, 2, 4, 5),
        "Podcast" to listOf(-4, -3, -1, 1, 2, 3, 3, 1, -1, -3),
    ).mapValuesTo(LinkedHashMap()) { (_, v) -> v.map { it.toDouble() } }

    private val PRESET_KEYS = listOf(
        "eq_mode", "band_count", "freq_start", "freq_end", "bands", "parametric",
        "bass", "treble", "bass_freq", "treble_freq",
    )

    val COMPRESSOR_STRENGTHS = listOf(
        mapOf("threshold" to -18.0, "ratio" to 2.0, "attack" to 20.0, "release" to 200.0, "makeup" to 2.0),
        mapOf("threshold" to -22.0, "ratio" to 3.0, "attack" to 15.0, "release" to 150.0, "makeup" to 4.0),
        mapOf("threshold" to -28.0, "ratio" to 5.0, "attack" to 10.0, "release" to 100.0, "makeup" to 7.0),
    )

    private val FILTER_LABELS = mapOf(
        "PK" to "bq_peaking", "LSC" to "bq_lowshelf", "HSC" to "bq_highshelf",
        "LS" to "bq_lowshelf", "HS" to "bq_highshelf", "PEQ" to "bq_peaking",
        "LP" to "bq_lowpass", "HP" to "bq_highpass", "NO" to "bq_notch",
    )

    fun defaultSettings(): MutableMap<String, Any?> = mutableMapOf(
        "preset" to "Flat",
        "eq_mode" to "graphic",
        "band_count" to 10L, "freq_start" to DEFAULT_START, "freq_end" to DEFAULT_END,
        "bands" to MutableList<Any?>(10) { 0.0 },
        "parametric" to mutableListOf<Any?>(),
        "bass" to 0.0, "treble" to 0.0, "bass_freq" to 100.0, "treble_freq" to 8000.0,
        "preamp" to 0.0, "auto_preamp" to true,
        "profile" to null,
        "balance" to 0.0,
        "limiter" to mutableMapOf<String, Any?>("enabled" to true, "threshold" to -1.0),
        "compressor" to mutableMapOf<String, Any?>(
            "enabled" to false, "strength" to 1L, "threshold" to -22.0, "ratio" to 3.0,
            "attack" to 15.0, "release" to 150.0, "makeup" to 4.0,
        ),
    )

    private fun defaultState(): MutableMap<String, Any?> = mutableMapOf(
        "version" to 2L, "enabled" to true, "default" to defaultSettings(),
        "devices" to mutableMapOf<String, Any?>(), "device_names" to mutableMapOf<String, Any?>(),
        "presets" to mutableMapOf<String, Any?>(), "profiles" to mutableMapOf<String, Any?>(),
        "options" to mutableMapOf<String, Any?>(),
    )

    // ---- The live state -------------------------------------------------------------------------

    /** What the page draws: the whole state file plus which output it applies to right now. */
    data class Snapshot(
        val root: Map<String, Any?>,
        val deviceKey: String?,
        val deviceName: String,
        val daemonRunning: Boolean,
        /** Bumped on every change, so a Compose read of a deep map still recomposes. */
        val revision: Long,
    ) {
        val enabled: Boolean get() = root.selBool("enabled", true)
        val perDevice: Boolean get() = root.selMap("options").selBool("per_device", true)
        val settings: Map<String, Any?> get() = Selaras.settingsFor(root, deviceKey)
        val userPresets: List<String> get() = root.selMap("presets").keys.sorted()
        val profiles: Map<String, Any?> get() = root.selMap("profiles")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<Snapshot?>(null)
    val state: StateFlow<Snapshot?> = _state

    private var root: MutableMap<String, Any?> = defaultState()
    private var deviceKey: String? = null
    private var deviceName: String = "No output"
    private var revision = 0L
    private var lastSeenModified = 0L
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private var writer: Job? = null
    private val lock = Any()

    /** Set by an edit until the writer has put it on disk, so a poll never reloads over it. */
    private var dirty = false

    /** Re-reads the file and the current output. Cheap enough to call every couple of seconds. */
    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        val (key, name) = currentDevice()
        val modified = stateFile.lastModified()
        synchronized(lock) {
            if (!dirty && (force || modified != lastSeenModified || _state.value == null)) {
                root = loadState()
                lastSeenModified = modified
            }
            deviceKey = key
            deviceName = name
            // As the Selaras window does, so Devices can show a name rather than a sink id.
            if (key != null && root.selMap("device_names")[key] != name) root.mmap("device_names")[key] = name
            publish()
        }
    }

    /** Edits the state for the current output, the way the Selaras window's `editable()` does. */
    fun edit(block: (root: MutableMap<String, Any?>, settings: MutableMap<String, Any?>) -> Unit) {
        synchronized(lock) {
            block(root, ensureDevice(root, deviceKey))
            dirty = true
            publish()
        }
        ensureWriter()
        writes.trySend(Unit)
    }

    /** Edits the state without touching the current output's settings (options, presets, devices). */
    fun editState(block: (root: MutableMap<String, Any?>) -> Unit) {
        synchronized(lock) {
            block(root)
            dirty = true
            publish()
        }
        ensureWriter()
        writes.trySend(Unit)
    }

    /** As [edit], and marks the curve as no longer being a saved preset. */
    fun editCustom(block: (settings: MutableMap<String, Any?>) -> Unit) = edit { _, s ->
        block(s)
        s["preset"] = CUSTOM
    }

    fun setEnabled(on: Boolean) = edit { r, _ -> r["enabled"] = on }

    private fun publish() {
        revision++
        _state.value = Snapshot(deepCopy(root), deviceKey, deviceName, daemonPid() != null, revision)
    }

    /**
     * Writes coalesced to one every 40 ms, as the Selaras window does: a band dragged across its
     * whole travel is a few dozen states, and only the last of each burst needs to reach PipeWire.
     */
    private fun ensureWriter() {
        if (writer?.isActive == true) return
        writer = scope.launch {
            for (ignored in writes) {
                val text = synchronized(lock) {
                    dirty = false
                    encode(root)
                }
                runCatching {
                    configDir.mkdirs()
                    val tmp = File(configDir, "state.tmp")
                    tmp.writeText(text, StandardCharsets.UTF_8)
                    Files.move(tmp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    synchronized(lock) { lastSeenModified = stateFile.lastModified() }
                }
                signalDaemon()
                delay(40)
            }
        }
    }

    // ---- The daemon -----------------------------------------------------------------------------

    private fun daemonPid(): Long? = runCatching {
        val pid = pidFile.readText().trim().toLong()
        val cmdline = File("/proc/$pid/cmdline").readBytes().toString(StandardCharsets.UTF_8)
        pid.takeIf { "selaras" in cmdline }
    }.getOrNull()

    private fun signalDaemon() {
        val pid = daemonPid() ?: return
        exec(listOf("kill", "-HUP", pid.toString()))
    }

    /** Starts the service, or the bare daemon where there is no user unit for it. */
    suspend fun startDaemon() = withContext(Dispatchers.IO) {
        val viaSystemd = executable("systemctl") != null &&
            exec(listOf("systemctl", "--user", "start", "selaras.service")) != null &&
            exec(listOf("systemctl", "--user", "is-active", "selaras.service"))?.trim() == "active"
        if (!viaSystemd) {
            executable("selaras-daemon")?.let {
                runCatching { ProcessBuilder(it.path).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start() }
            }
        }
        delay(800)
        refresh(force = true)
    }

    /** Opens the full Selaras window, for what this page leaves out (parametric EQ, layouts, skins). */
    fun openApp(vararg args: String) {
        val exe = executable("selaras") ?: return
        runCatching {
            ProcessBuilder(listOf(exe.path) + args).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        }
    }

    // ---- Devices, as selaras.model.current_device -----------------------------------------------

    private fun currentDevice(): Pair<String?, String> {
        val name = exec(listOf("pactl", "get-default-sink"))?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null to "No output"
        var desc = name
        var port: String? = null
        runCatching {
            val sinks = Json.parseToJsonElement(exec(listOf("pactl", "-f", "json", "list", "sinks")) ?: "[]")
            for (sink in (sinks as? JsonArray).orEmpty()) {
                val s = sink as? JsonObject ?: continue
                if ((s["name"] as? JsonPrimitive)?.content != name) continue
                desc = (s["description"] as? JsonPrimitive)?.content ?: name
                port = (s["active_port"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                for (p in (s["ports"] as? JsonArray).orEmpty()) {
                    val po = p as? JsonObject ?: continue
                    val pd = (po["description"] as? JsonPrimitive)?.content
                    if ((po["name"] as? JsonPrimitive)?.content == port && !pd.isNullOrBlank()) desc = "$desc · $pd"
                }
            }
        }
        return (if (port != null) "$name#$port" else name) to desc
    }

    private fun exec(cmd: List<String>): String? = runCatching {
        val proc = ProcessBuilder(cmd).redirectErrorStream(false).start()
        val out = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
            null
        } else {
            out
        }
    }.getOrNull()

    // ---- Persistence, as selaras.model.load_state ----------------------------------------------

    private fun loadState(): MutableMap<String, Any?> {
        val state = defaultState()
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val data = fromJson(Json.parseToJsonElement(stateFile.readText(StandardCharsets.UTF_8))) as? MutableMap<String, Any?>
            if (data != null) {
                val opts = (state["options"] as MutableMap<String, Any?>) + data.selMap("options")
                state.putAll(data)
                state["options"] = opts.toMutableMap()
            }
        }
        for (key in listOf("devices", "device_names", "presets", "profiles", "options")) {
            if (state[key] !is MutableMap<*, *>) state[key] = mutableMapOf<String, Any?>()
        }
        if (state["default"] !is MutableMap<*, *>) state["default"] = defaultSettings()
        migrate(state.mmap("default"))
        state.mmap("devices").values.forEach { if (it is MutableMap<*, *>) migrate(it.asMutable()) }
        return state
    }

    private fun migrate(s: MutableMap<String, Any?>) {
        val base = defaultSettings()
        for ((k, v) in base) {
            if (k !in s) {
                s[k] = v
            } else if (v is MutableMap<*, *>) {
                val inner = (s[k] as? MutableMap<*, *>)?.asMutable() ?: continue
                for ((kk, vv) in v) if (kk as String !in inner) inner[kk] = vv
            }
        }
        val bands = s.selDoubles("bands")
        if (bands.size != s.selInt("band_count", 10)) {
            s["bands"] = resampleGains(classicOr(bands.size), bands, bandFreqs(s)).toMutableList<Any?>()
        }
    }

    private fun classicOr(n: Int): List<Double> =
        if (n == 10) CLASSIC_FREQS else bandFreqs(mapOf("band_count" to n.toLong(), "freq_start" to DEFAULT_START, "freq_end" to DEFAULT_END))

    fun settingsFor(root: Map<String, Any?>, device: String?): Map<String, Any?> {
        if (!root.selMap("options").selBool("per_device", true)) return root.selMap("default")
        val devices = root.selMap("devices")
        return if (device != null && device in devices) devices.selMap(device) else root.selMap("default")
    }

    private fun ensureDevice(root: MutableMap<String, Any?>, device: String?): MutableMap<String, Any?> {
        if (device == null || !root.selMap("options").selBool("per_device", true)) return root.mmap("default")
        val devices = root.mmap("devices")
        if (device !in devices) devices[device] = deepCopy(root.selMap("default"))
        return devices.mmap(device)
    }

    // ---- Presets, as selaras.actions -------------------------------------------------------------

    fun applyPreset(root: MutableMap<String, Any?>, s: MutableMap<String, Any?>, name: String): Boolean {
        val user = root.selMap("presets")[name] as? Map<*, *>
        val shape: Map<String, Any?> = when {
            user != null -> deepCopy(user.asMap())
            name in BUILTIN_PRESETS -> {
                val layout = mapOf("band_count" to s["band_count"], "freq_start" to s["freq_start"], "freq_end" to s["freq_end"])
                layout + mapOf(
                    "eq_mode" to "graphic",
                    "bands" to resampleGains(CLASSIC_FREQS, BUILTIN_PRESETS.getValue(name), bandFreqs(layout)).toMutableList<Any?>(),
                    "parametric" to deepCopy(s["parametric"] ?: mutableListOf<Any?>()),
                    "bass" to 0.0, "treble" to 0.0,
                    "bass_freq" to s["bass_freq"], "treble_freq" to s["treble_freq"],
                )
            }
            else -> return false
        }
        s.putAll(shape.filterKeys { it in PRESET_KEYS })
        s["preset"] = name
        return true
    }

    fun setCompressorStrength(s: MutableMap<String, Any?>, level: Int) {
        val l = level.coerceIn(0, 2)
        val comp = s.mmap("compressor")
        comp.putAll(COMPRESSOR_STRENGTHS[l])
        comp["strength"] = l.toLong()
    }

    // ---- Graphic layout and DSP, as selaras.model ----------------------------------------------

    fun bandFreqs(s: Map<String, Any?>): List<Double> {
        val n = s.selInt("band_count", 10)
        val lo = s.selDouble("freq_start", DEFAULT_START)
        val hi = s.selDouble("freq_end", DEFAULT_END)
        if (n == 10 && lo == DEFAULT_START && hi == DEFAULT_END) return CLASSIC_FREQS
        return List(n) { i -> (lo * (hi / lo).pow(i.toDouble() / (n - 1)) * 10).roundToInt() / 10.0 }
    }

    private fun bandQ(s: Map<String, Any?>): Double {
        val n = s.selInt("band_count", 10)
        val bw = log2(s.selDouble("freq_end", DEFAULT_END) / s.selDouble("freq_start", DEFAULT_START)) / (n - 1)
        return sqrt(2.0.pow(bw)) / (2.0.pow(bw) - 1)
    }

    private fun resampleGains(src: List<Double>, gains: List<Double>, dst: List<Double>): List<Double> {
        val xs = src.map { ln(it) }
        return dst.map { f ->
            val x = ln(f)
            when {
                x <= xs.first() -> gains.first()
                x >= xs.last() -> gains.last()
                else -> {
                    val i = (0 until xs.size - 1).first { xs[it] <= x && x <= xs[it + 1] }
                    val t = (x - xs[i]) / (xs[i + 1] - xs[i])
                    ((gains[i] + t * (gains[i + 1] - gains[i])) * 10).roundToInt() / 10.0
                }
            }
        }
    }

    private data class Biquad(val label: String, val freq: Double, val q: Double, val gain: Double)

    private fun filterList(s: Map<String, Any?>, profiles: Map<String, Any?>): List<Biquad> {
        val out = mutableListOf(
            Biquad("bq_lowshelf", s.selDouble("bass_freq", 100.0), SHELF_Q, s.selDouble("bass", 0.0)),
            Biquad("bq_highshelf", s.selDouble("treble_freq", 8000.0), SHELF_Q, s.selDouble("treble", 0.0)),
        )
        if (s["eq_mode"] == "graphic") {
            val q = bandQ(s)
            bandFreqs(s).zip(s.selDoubles("bands")).forEach { (f, g) -> out += Biquad("bq_peaking", f, q, g) }
        } else {
            for (flt in s.selList("parametric").mapNotNull { (it as? Map<*, *>)?.asMap() }) {
                val on = flt.selBool("on", true)
                var label = FILTER_LABELS[flt["type"]] ?: "bq_peaking"
                if (!on && label in listOf("bq_lowpass", "bq_highpass", "bq_notch")) label = "bq_peaking"
                out += Biquad(label, flt.selDouble("fc", 1000.0), flt.selDouble("q", 1.0), if (on) flt.selDouble("gain", 0.0) else 0.0)
            }
        }
        val prof = (profiles[s["profile"] as? String ?: ""] as? Map<*, *>)?.asMap()
        prof?.selList("filters")?.mapNotNull { (it as? Map<*, *>)?.asMap() }?.forEach { f ->
            out += Biquad(FILTER_LABELS[f["type"]] ?: "bq_peaking", f.selDouble("fc", 1000.0), f.selDouble("q", 0.707), f.selDouble("gain", 0.0))
        }
        return out
    }

    private fun coeffs(b: Biquad): DoubleArray {
        val a = 10.0.pow(b.gain / 40)
        val w0 = 2 * PI * min(b.freq, SAMPLE_RATE * 0.499) / SAMPLE_RATE
        val cw = cos(w0)
        val sw = sin(w0)
        val alpha = sw / (2 * max(b.q, 1e-3))
        val sa = 2 * sqrt(a) * alpha
        return when (b.label) {
            "bq_peaking" -> doubleArrayOf(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
            "bq_lowpass" -> doubleArrayOf((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
            "bq_highpass" -> doubleArrayOf((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
            "bq_notch" -> doubleArrayOf(1.0, -2 * cw, 1.0, 1 + alpha, -2 * cw, 1 - alpha)
            "bq_lowshelf" -> doubleArrayOf(
                a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw),
                a * ((a + 1) - (a - 1) * cw - sa), (a + 1) + (a - 1) * cw + sa,
                -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa,
            )
            else -> doubleArrayOf(
                a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw),
                a * ((a + 1) + (a - 1) * cw - sa), (a + 1) - (a - 1) * cw + sa,
                2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa,
            )
        }
    }

    private fun magDb(c: DoubleArray, f: Double): Double {
        val w = 2 * PI * f / SAMPLE_RATE
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = c[0] + c[1] * c1 + c[2] * c2
        val ni = -(c[1] * s1 + c[2] * s2)
        val dr = c[3] + c[4] * c1 + c[5] * c2
        val di = -(c[4] * s1 + c[5] * s2)
        return 10 * log10(max((nr * nr + ni * ni) / max(dr * dr + di * di, 1e-30), 1e-30))
    }

    private fun active(b: Biquad) = b.gain != 0.0 || b.label in listOf("bq_lowpass", "bq_highpass", "bq_notch")

    fun logFreqs(n: Int = 200, lo: Double = 20.0, hi: Double = 20000.0) = List(n) { lo * (hi / lo).pow(it.toDouble() / (n - 1)) }

    /** The curve the chain will draw, in dB at each of [freqs]. */
    fun responseCurve(s: Map<String, Any?>, profiles: Map<String, Any?>, freqs: List<Double>, includePreamp: Boolean = true): List<Double> {
        val cs = filterList(s, profiles).filter(::active).map(::coeffs)
        val pre = if (includePreamp) totalPreamp(s, profiles) else 0.0
        return freqs.map { f -> pre + cs.sumOf { magDb(it, f) } }
    }

    fun totalPreamp(s: Map<String, Any?>, profiles: Map<String, Any?>): Double {
        if (s.selBool("auto_preamp", true)) {
            val peak = responseCurve(s, profiles, logFreqs(160), includePreamp = false).max()
            return -(max(0.0, peak) * 100).roundToInt() / 100.0
        }
        val prof = (profiles[s["profile"] as? String ?: ""] as? Map<*, *>)?.asMap()
        val base = prof?.selDouble("preamp", 0.0) ?: 0.0
        return ((base + s.selDouble("preamp", 0.0)) * 100).roundToInt() / 100.0
    }

    // ---- AutoEq, as selaras.autoeqdb ------------------------------------------------------------

    data class AutoEqEntry(val name: String, val path: String, val source: String) {
        /** The name Selaras stores the profile under: "Model (measurer)". */
        val profileName: String get() = "$name (${source.substringBefore(" on ")})"
    }

    private const val AUTOEQ_RAW = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/"
    private val ENTRY = Regex("""^- \[(?<name>.+?)\]\(\./(?<path>.+)\) by (?<source>.+)$""")
    private val FILTER_RE = Regex(
        """Filter\s*\d*:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val PREAMP_RE = Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
    private var index: List<AutoEqEntry>? = null

    private fun httpGet(url: String): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "Selaras-Equalizer")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        } finally {
            conn.disconnect()
        }
    }

    /** The AutoEq results index, cached where Selaras caches it and refreshed weekly. */
    suspend fun autoEqIndex(): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        index?.let { return@withContext it }
        val file = File(cacheDir, "autoeq-index.md")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 7L * 24 * 3600 * 1000
        if (!fresh) {
            try {
                val text = httpGet(AUTOEQ_RAW + "INDEX.md")
                cacheDir.mkdirs()
                file.writeText(text, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                if (!file.exists()) throw e
            }
        }
        file.readLines(StandardCharsets.UTF_8).mapNotNull { line ->
            ENTRY.matchEntire(line.trim())?.let {
                AutoEqEntry(it.groups["name"]!!.value, it.groups["path"]!!.value, it.groups["source"]!!.value)
            }
        }.also { index = it }
    }

    fun search(entries: List<AutoEqEntry>, query: String, limit: Int = 40): List<AutoEqEntry> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries.asSequence().filter { e ->
            val hay = "${e.name} ${e.source}".lowercase()
            words.all { it in hay }
        }.take(limit).toList()
    }

    /** Downloads the entry's ParametricEQ.txt and keeps it as a Selaras headphone profile. */
    suspend fun installProfile(entry: AutoEqEntry) = withContext(Dispatchers.IO) {
        val folder = URLDecoder.decode(entry.path, StandardCharsets.UTF_8)
        val leaf = folder.substringAfterLast('/')
        val encoded = "$folder/$leaf ParametricEQ.txt".split('/')
            .joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20") }
        val profile = parseAutoEq(httpGet(AUTOEQ_RAW + encoded))
        edit { root, s ->
            root.mmap("profiles")[entry.profileName] = profile
            s["profile"] = entry.profileName
        }
    }

    private fun parseAutoEq(text: String): MutableMap<String, Any?> {
        val preamp = PREAMP_RE.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val filters = mutableListOf<Any?>()
        for (m in FILTER_RE.findAll(text)) {
            val (on, rawKind, fc, gain, q) = m.destructured
            val kind = mapOf("LS" to "LSC", "HS" to "HSC", "PEQ" to "PK")[rawKind.uppercase()] ?: rawKind.uppercase()
            if (!on.equals("ON", ignoreCase = true) || kind !in FILTER_LABELS) continue
            filters += mutableMapOf<String, Any?>(
                "type" to kind, "fc" to fc.toDouble(),
                "gain" to (gain.toDoubleOrNull() ?: 0.0), "q" to (q.toDoubleOrNull() ?: 0.707),
            )
        }
        require(filters.isNotEmpty()) { "No parametric filters found" }
        return mutableMapOf("preamp" to preamp, "filters" to filters.take(32).toMutableList())
    }

    // ---- Capabilities -----------------------------------------------------------------------------

    private fun lv2Paths() = (env("LV2_PATH")?.split(":")?.filter { it.isNotBlank() } ?: emptyList()) +
        listOf("/usr/lib/lv2", "/usr/lib64/lv2", "/usr/local/lib/lv2", "/usr/local/lib64/lv2", "$home/.lv2")

    /** Limiter and compressor are LSP plugins; without them the daemon leaves both out of the chain. */
    val dynamicsAvailable: Boolean
        get() = lv2Paths().any { File(it, "lsp-plugins.lv2/limiter_stereo.ttl").exists() }

    /** The visualizer is libprojectM 4, drawn by the Selaras window. */
    val visualizerAvailable: Boolean by lazy {
        listOf("/usr/lib64", "/usr/lib", "/usr/local/lib", "/usr/local/lib64", "/usr/lib/x86_64-linux-gnu")
            .any { File(it, "libprojectM-4.so.4").exists() }
    }

    suspend fun loginEnabled(): Boolean = withContext(Dispatchers.IO) {
        exec(listOf("systemctl", "--user", "is-enabled", "selaras.service"))?.trim() == "enabled"
    }

    suspend fun setLogin(on: Boolean) = withContext(Dispatchers.IO) {
        exec(listOf("systemctl", "--user", if (on) "enable" else "disable", "selaras.service"))
    }

    // ---- Actions, as selaras.actions ------------------------------------------------------------

    /** id to (label, default global shortcut). */
    val ACTIONS: LinkedHashMap<String, Pair<String, String>> = linkedMapOf(
        "toggle" to ("Equalizer on/off" to "LOGO+ALT+e"),
        "next-preset" to ("Next preset" to "LOGO+ALT+Right"),
        "prev-preset" to ("Previous preset" to "LOGO+ALT+Left"),
        "profile" to ("Headphone profile on/off" to "LOGO+ALT+h"),
        "limiter" to ("Limiter on/off" to "LOGO+ALT+l"),
        "compressor" to ("Compressor on/off" to "LOGO+ALT+c"),
        "balance-center" to ("Center balance" to ""),
        "flat" to ("Flat" to "LOGO+ALT+0"),
        "visualizer" to ("Visualizer" to "LOGO+ALT+v"),
    )
    val CHIP_ACTIONS = listOf("profile", "limiter", "compressor", "balance-center", "flat")
    val DEFAULT_QUICK = listOf("profile", "limiter", "compressor")
    val BAND_COUNTS = listOf(5, 8, 10, 12, 15, 16, 24, 31, 32)
    val BASS_FREQS = listOf(60.0, 80.0, 100.0, 120.0, 150.0, 200.0, 250.0)
    val TREBLE_FREQS = listOf(4000.0, 5000.0, 6000.0, 8000.0, 10000.0, 12000.0)
    val PARAMETRIC_TYPES = listOf("PK", "LSC", "HSC", "LP", "HP", "NO")
    val TYPE_NAMES = mapOf("PK" to "Peak", "LSC" to "Low shelf", "HSC" to "High shelf", "LP" to "Low-pass", "HP" to "High-pass", "NO" to "Notch")
    const val MAX_PARAMETRIC = 32

    fun presetCycle(root: Map<String, Any?>): List<String> = BUILTIN_PRESETS.keys.toList() + root.selMap("presets").keys.sorted()

    fun quickButtons(root: Map<String, Any?>): List<String> =
        (root.selMap("options")["quick_buttons"] as? List<*>)?.filterIsInstance<String>() ?: DEFAULT_QUICK

    /** Runs a Selaras action on the current output and returns what it did, as selaras-ctl prints it. */
    fun runAction(action: String): String {
        if (action == "visualizer") {
            openApp("--visualizer")
            return "Visualizer"
        }
        var msg = ""
        if (action == "toggle") {
            editState { r ->
                r["enabled"] = !r.selBool("enabled", true)
                msg = if (r.selBool("enabled", true)) "Equalizer on" else "Equalizer off"
            }
            return msg
        }
        edit { root, s ->
            msg = when (action) {
                "next-preset", "prev-preset" -> {
                    val names = presetCycle(root)
                    val cur = names.indexOf(s["preset"])
                    val step = if (action == "next-preset") 1 else -1
                    val name = if (cur >= 0) names[(cur + step + names.size) % names.size] else if (step > 0) names.first() else names.last()
                    applyPreset(root, s, name)
                    "Preset: $name"
                }
                "flat" -> {
                    applyPreset(root, s, "Flat")
                    "Preset: Flat"
                }
                "profile" -> {
                    val prof = s["profile"] as? String
                    val last = s["last_profile"] as? String
                    when {
                        prof != null -> {
                            s["last_profile"] = prof
                            s["profile"] = null
                            "Headphone profile off"
                        }
                        last != null && last in root.selMap("profiles") -> {
                            s["profile"] = last
                            "Headphone profile: $last"
                        }
                        else -> "No headphone profile to turn on"
                    }
                }
                "limiter", "compressor" -> {
                    val d = s.mmap(action)
                    d["enabled"] = !d.selBool("enabled", false)
                    "${action.replaceFirstChar { it.uppercase() }} ${if (d.selBool("enabled", false)) "on" else "off"}"
                }
                "balance-center" -> {
                    s["balance"] = 0.0
                    "Balance centered"
                }
                else -> "Unknown action: $action"
            }
        }
        return msg
    }

    fun pickPreset(name: String) = edit { root, s -> applyPreset(root, s, name) }

    // ---- Presets: save, delete, import, export ---------------------------------------------------

    fun presetNameTaken(root: Map<String, Any?>, name: String): Boolean {
        val lower = name.lowercase()
        return lower == CUSTOM.lowercase() || presetCycle(root).any { it.lowercase() == lower }
    }

    fun savePreset(name: String) = edit { root, s ->
        root.mmap("presets")[name] = PRESET_KEYS.associateWithTo(LinkedHashMap<String, Any?>()) { deepCopy(s[it]) }
        s["preset"] = name
    }

    /** Deletes the current preset when it is one of the user's own; false otherwise. */
    fun deleteCurrentPreset(): String {
        var msg = "Only your own presets can be deleted"
        editState { root ->
            val name = settingsFor(root, deviceKey)["preset"] as? String ?: return@editState
            if (name !in root.selMap("presets")) return@editState
            root.mmap("presets").remove(name)
            (listOf(root.mmap("default")) + root.mmap("devices").values.filterIsInstance<Map<*, *>>().map { it.asMutable() })
                .forEach { if (it["preset"] == name) it["preset"] = CUSTOM }
            (root.mmap("options")["favourites"] as? MutableList<Any?>)?.remove(name)
            msg = "Deleted preset “$name”"
        }
        return msg
    }

    fun exportPreset(name: String, s: Map<String, Any?>): String = encode(
        linkedMapOf(
            "format" to "selaras-preset", "version" to 2L, "name" to name,
            "settings" to PRESET_KEYS.associateWithTo(LinkedHashMap<String, Any?>()) { s[it] },
        ),
    )

    /** Reads a Selaras preset file, keeps it with the user's presets and switches to it. */
    fun importPreset(text: String): String {
        @Suppress("UNCHECKED_CAST")
        val data = fromJson(Json.parseToJsonElement(text)) as? Map<String, Any?>
        require(data != null && data["format"] == "selaras-preset") { "Not a Selaras preset file" }
        val base = defaultSettings()
        val shape = LinkedHashMap<String, Any?>()
        PRESET_KEYS.forEach { shape[it] = base[it] }
        data.selMap("settings").forEach { (k, v) -> if (k in PRESET_KEYS) shape[k] = deepCopy(v) }
        require(shape["eq_mode"] == "graphic" || shape["eq_mode"] == "parametric") { "Unknown EQ mode in preset" }
        val count = shape.selInt("band_count", 10)
        shape["band_count"] = count.toLong()
        require(shape.selList("bands").size == count) { "Band count does not match the gains in the preset" }
        shape["parametric"] = shape.selList("parametric").mapNotNull { (it as? Map<*, *>)?.asMap() }
            .map(::validateFilter).take(MAX_PARAMETRIC).toMutableList<Any?>()
        var name = (data["name"] as? String)?.takeIf { it.isNotBlank() } ?: "Imported"
        if (name in BUILTIN_PRESETS || name == CUSTOM) name = "$name (imported)"
        edit { root, s ->
            root.mmap("presets")[name] = shape
            s.putAll(deepCopy(shape))
            s["preset"] = name
        }
        return name
    }

    fun validateFilter(f: Map<String, Any?>): MutableMap<String, Any?> {
        val kind = (f["type"] as? String)?.uppercase()?.takeIf { it in PARAMETRIC_TYPES } ?: "PK"
        return mutableMapOf(
            "type" to kind,
            "fc" to f.selDouble("fc", 1000.0).coerceIn(10.0, 22000.0),
            "gain" to f.selDouble("gain", 0.0).coerceIn(-GAIN_LIMIT, GAIN_LIMIT),
            "q" to f.selDouble("q", 1.0).coerceIn(0.1, 20.0),
            "on" to f.selBool("on", true),
        )
    }

    // ---- Band layout and the parametric table ----------------------------------------------------

    fun relayout(count: Int, start: Double, end: Double) = editCustom { s ->
        val old = bandFreqs(s)
        s["band_count"] = count.toLong()
        s["freq_start"] = start
        s["freq_end"] = end
        s["bands"] = resampleGains(old, s.selDoubles("bands"), bandFreqs(s)).toMutableList<Any?>()
    }

    fun addParametric(): Boolean {
        var added = false
        editCustom { s ->
            val list = s["parametric"] as? MutableList<Any?> ?: mutableListOf<Any?>().also { s["parametric"] = it }
            if (list.size < MAX_PARAMETRIC) {
                list += mutableMapOf<String, Any?>("type" to "PK", "fc" to 1000.0, "gain" to 0.0, "q" to 1.0, "on" to true)
                added = true
            }
        }
        return added
    }

    fun updateParametric(index: Int, block: (MutableMap<String, Any?>) -> Unit) = editCustom { s ->
        val f = ((s["parametric"] as? MutableList<Any?>)?.getOrNull(index) as? Map<*, *>)?.asMutable() ?: return@editCustom
        block(f)
    }

    fun deleteParametric(index: Int) = editCustom { s ->
        (s["parametric"] as? MutableList<Any?>)?.let { if (index in it.indices) it.removeAt(index) }
    }

    fun parametricFromGraphic() = editCustom { s ->
        val q = (bandQ(s) * 100).roundToInt() / 100.0
        s["parametric"] = bandFreqs(s).zip(s.selDoubles("bands")).take(MAX_PARAMETRIC).map { (f, g) ->
            mutableMapOf<String, Any?>("type" to "PK", "fc" to f, "gain" to g, "q" to q, "on" to true)
        }.toMutableList<Any?>()
    }

    // ---- Tone and gain ---------------------------------------------------------------------------

    fun stepToneFrequency(key: String, freqs: List<Double>, step: Int) = editCustom { s ->
        val cur = s.selDouble("${key}_freq", freqs.first())
        val idx = freqs.indices.minBy { kotlin.math.abs(freqs[it] - cur) }
        s["${key}_freq"] = freqs[(idx + step).coerceIn(0, freqs.size - 1)]
    }

    /** Turning auto off keeps what it was doing as the manual starting point, as in Selaras. */
    fun setAutoPreamp(on: Boolean) = edit { root, s ->
        if (!on) {
            val profiles = root.selMap("profiles")
            val prof = (profiles[s["profile"] as? String ?: ""] as? Map<*, *>)?.asMap()
            s["preamp"] = ((totalPreamp(s, profiles) - (prof?.selDouble("preamp", 0.0) ?: 0.0)) * 10).roundToInt() / 10.0
        }
        s["auto_preamp"] = on
    }

    // ---- Headphone profiles ----------------------------------------------------------------------

    fun selectProfile(name: String?) = edit { _, s -> s["profile"] = name }

    fun importProfileFile(file: File): String {
        val profile = parseAutoEq(file.readText(StandardCharsets.UTF_8))
        val stem = file.nameWithoutExtension
        val name = stem.replace(Regex("""\s*ParametricEQ$"""), "").trim().ifEmpty { stem }
        edit { root, s ->
            root.mmap("profiles")[name] = profile
            s["profile"] = name
        }
        return name
    }

    fun deleteProfile(name: String) = editState { root ->
        root.mmap("profiles").remove(name)
        (listOf(root.mmap("default")) + root.mmap("devices").values.filterIsInstance<Map<*, *>>().map { it.asMutable() })
            .forEach { if (it["profile"] == name) it["profile"] = null }
    }

    // ---- Options and devices ---------------------------------------------------------------------

    fun setOption(key: String, value: Any?) = editState { root -> root.mmap("options")[key] = value }

    fun toggleQuick(action: String) = editState { root ->
        val q = quickButtons(root).toMutableList()
        if (action in q) q.remove(action) else q.add(action)
        root.mmap("options")["quick_buttons"] = q.toMutableList<Any?>()
    }

    /** Up to three favourites, for the tray panel; false when that would be a fourth. */
    fun toggleFavourite(name: String): Boolean {
        var ok = true
        editState { root ->
            val favs = (root.selMap("options")["favourites"] as? List<*>)?.filterIsInstance<String>()?.toMutableList() ?: mutableListOf()
            when {
                name in favs -> favs.remove(name)
                favs.size >= 3 -> ok = false
                else -> favs.add(name)
            }
            root.mmap("options")["favourites"] = favs.toMutableList<Any?>()
        }
        return ok
    }

    fun forgetDevice(key: String) = editState { root -> root.mmap("devices").remove(key) }

    /** The Selaras window's skin file, created with a comment the first time. */
    fun openSkin() {
        val skin = File(configDir, "style.css")
        runCatching {
            if (!skin.exists()) {
                configDir.mkdirs()
                skin.writeText("/* Selaras skin: GTK4 CSS, loaded on start (Settings › Advanced › Reload). */\n")
            }
            ProcessBuilder("xdg-open", skin.path).start()
        }
    }
    val skinPath: String get() = File(configDir, "style.css").path

    /** KDE's shortcut settings where they exist, GNOME's keyboard panel otherwise. */
    fun openShortcutSettings(): Boolean {
        for (cmd in listOf(listOf("systemsettings", "kcm_keys"), listOf("kcmshell6", "kcm_keys"), listOf("gnome-control-center", "keyboard"))) {
            if (executable(cmd[0]) != null) {
                runCatching { ProcessBuilder(cmd).start() }
                return true
            }
        }
        return false
    }

    // ---- Live spectrum and meters, as selaras.spectrum -------------------------------------------

    /** Reads the default sink's monitor through parec while the page is open. */
    object Spectrum {
        const val BARS = 64
        private const val RATE = 48000
        private const val N = 2048
        private const val LO = 20.0
        private const val HI = 20000.0

        val barFreqs = List(BARS) { LO * (HI / LO).pow((it + 0.5) / BARS) }
        private val edges = DoubleArray(BARS + 1) { LO * (HI / LO).pow(it.toDouble() / BARS) }
        private val window = DoubleArray(N) { 0.5 - 0.5 * cos(2 * PI * it / (N - 1)) }

        @Volatile var levels = DoubleArray(BARS) { -90.0 }
            private set
        @Volatile var levelL = -90.0
            private set
        @Volatile var levelR = -90.0
            private set

        @Volatile private var proc: Process? = null
        private var users = 0

        @Synchronized fun start() {
            users++
            if (proc != null) return
            val p = runCatching {
                ProcessBuilder(
                    "parec", "-d", "@DEFAULT_MONITOR@", "--raw", "--format=float32le", "--channels=2",
                    "--rate=$RATE", "--latency-msec=30", "--client-name=BitChord Selaras spectrum", "--stream-name=Spectrum",
                ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            }.getOrNull() ?: return
            proc = p
            Thread({ loop(p) }, "selaras-spectrum").apply { isDaemon = true }.start()
        }

        @Synchronized fun stop() {
            users = (users - 1).coerceAtLeast(0)
            if (users > 0) return
            proc?.destroy()
            proc = null
            levels = DoubleArray(BARS) { -90.0 }
            levelL = -90.0
            levelR = -90.0
        }

        private fun loop(p: Process) {
            val hop = N / 2
            val buf = DoubleArray(N)
            val raw = ByteArray(hop * 8)
            val input = p.inputStream
            val re = DoubleArray(N)
            val im = DoubleArray(N)
            try {
                while (proc === p) {
                    var read = 0
                    while (read < raw.size) {
                        val n = input.read(raw, read, raw.size - read)
                        if (n < 0) return
                        read += n
                    }
                    val bb = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    var peakL = 0.0
                    var peakR = 0.0
                    System.arraycopy(buf, hop, buf, 0, N - hop)
                    for (i in 0 until hop) {
                        val l = bb.getFloat(i * 8).toDouble()
                        val r = bb.getFloat(i * 8 + 4).toDouble()
                        peakL = max(peakL, kotlin.math.abs(l))
                        peakR = max(peakR, kotlin.math.abs(r))
                        buf[N - hop + i] = (l + r) * 0.5
                    }
                    levelL = max(20 * log10(max(peakL, 1e-5)), levelL - 1.5)
                    levelR = max(20 * log10(max(peakR, 1e-5)), levelR - 1.5)
                    for (i in 0 until N) {
                        re[i] = buf[i] * window[i]
                        im[i] = 0.0
                    }
                    fft(re, im)
                    val old = levels
                    levels = DoubleArray(BARS) { b ->
                        val lo = (edges[b] * N / RATE).toInt()
                        val hi = max(lo + 1, (edges[b + 1] * N / RATE).toInt())
                        var peak = 0.0
                        for (k in lo until min(hi, N / 2)) peak = max(peak, sqrt(re[k] * re[k] + im[k] * im[k]))
                        max(20 * log10(peak / (N / 4) + 1e-9), old[b] - 2.5)
                    }
                }
            } catch (_: Exception) {
            } finally {
                p.destroy()
            }
        }

        private fun fft(re: DoubleArray, im: DoubleArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j or bit
                if (i < j) {
                    var t = re[i]; re[i] = re[j]; re[j] = t
                    t = im[i]; im[i] = im[j]; im[j] = t
                }
            }
            var size = 2
            while (size <= n) {
                val half = size / 2
                val ang = -2 * PI / size
                for (start in 0 until n step size) {
                    for (k in 0 until half) {
                        val wr = cos(ang * k)
                        val wi = sin(ang * k)
                        val ar = re[start + k + half] * wr - im[start + k + half] * wi
                        val ai = re[start + k + half] * wi + im[start + k + half] * wr
                        re[start + k + half] = re[start + k] - ar
                        im[start + k + half] = im[start + k] - ai
                        re[start + k] += ar
                        im[start + k] += ai
                    }
                }
                size *= 2
            }
        }
    }

    // ---- JSON <-> plain maps --------------------------------------------------------------------

    private fun fromJson(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonObject -> e.mapValuesTo(LinkedHashMap()) { fromJson(it.value) }
        is JsonArray -> e.mapTo(mutableListOf()) { fromJson(it) }
        is JsonPrimitive -> when {
            e.isString -> e.content
            e.booleanOrNull != null -> e.booleanOrNull
            !e.content.contains('.') && !e.content.contains('e', true) && e.longOrNull != null -> e.longOrNull
            else -> e.doubleOrNull
        }
    }

    private fun toJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to toJson(x) })
        is List<*> -> JsonArray(v.map(::toJson))
        is Boolean -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v.toLong())
        is Long -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v.toDouble())
        else -> JsonPrimitive(v.toString())
    }

    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    private fun encode(root: Map<String, Any?>) = pretty.encodeToString(JsonElement.serializer(), toJson(root))

    @Suppress("UNCHECKED_CAST")
    private fun <T> deepCopy(v: T): T = when (v) {
        is Map<*, *> -> v.entries.associateTo(LinkedHashMap<String, Any?>()) { (k, x) -> k.toString() to deepCopy(x) } as T
        is List<*> -> v.mapTo(mutableListOf()) { deepCopy(it) } as T
        else -> v
    }
}

// ---- Typed reads of the loose state maps -----------------------------------------------------------

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>.asMutable() = this as MutableMap<String, Any?>

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>.asMap() = this as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
private fun MutableMap<String, Any?>.mmap(key: String): MutableMap<String, Any?> =
    (this[key] as? MutableMap<String, Any?>) ?: mutableMapOf<String, Any?>().also { this[key] = it }

internal fun Map<String, Any?>.selMap(key: String): Map<String, Any?> = (this[key] as? Map<*, *>)?.asMap() ?: emptyMap()
internal fun Map<String, Any?>.selList(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()
internal fun Map<String, Any?>.selDouble(key: String, default: Double) = (this[key] as? Number)?.toDouble() ?: default
internal fun Map<String, Any?>.selInt(key: String, default: Int) = (this[key] as? Number)?.toInt() ?: default
internal fun Map<String, Any?>.selBool(key: String, default: Boolean) = this[key] as? Boolean ?: default
internal fun Map<String, Any?>.selDoubles(key: String) = selList(key).map { (it as? Number)?.toDouble() ?: 0.0 }
