package com.music.bitchord.selaras

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
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
 * BitChord's equaliser is Selaras, on every device.
 *
 * Selaras keeps everything in one `state.json`: per-output settings, presets, AutoEq headphone
 * profiles and options. This object holds that state, edits it the way the Selaras window does and
 * writes it back. What *applies* it depends on the [SelarasPlatform]:
 * - system-wide on Linux, where the Selaras daemon rebuilds its PipeWire filter-chain on SIGHUP;
 * - in BitChord everywhere else, where every edit becomes a [SelarasDspConfig] that [SelarasDsp]
 *   picks up on the audio thread.
 *
 * Everything here mirrors `selaras/model.py` and `selaras/actions.py`: the per-device settings,
 * the built-in presets, the band layout, the biquads drawn as the response curve and the AutoEq
 * lookup. Keys this side does not know about are carried through untouched.
 */
object Selaras {

    @Volatile private var platformOrNull: SelarasPlatform? = null

    /** The platform, once [install] has run. */
    val platform: SelarasPlatform
        get() = platformOrNull ?: error("Selaras.install() has not run")

    val installed: Boolean get() = platformOrNull != null

    /** Whether BitChord itself applies the EQ (true everywhere but Linux with the daemon). */
    val inApp: Boolean get() = platformOrNull?.systemWide == false

    /** Live spectrum and meters for the page. */
    val spectrum = SelarasSpectrum()

    /**
     * What [SelarasDsp] runs, or null when it should stay out of the way (system-wide mode, or
     * before the state is read).
     */
    @Volatile var dspConfig: SelarasDspConfig? = null
        private set

    /** Sets up Selaras for this device and reads its state in the background. Idempotent. */
    fun install(platform: SelarasPlatform) {
        synchronized(lock) {
            if (platformOrNull != null) return
            platformOrNull = platform
        }
        scope.launch { refresh(force = true) }
    }

    /** The output changed: re-read which device is playing, so its own EQ takes over. */
    fun deviceChanged() {
        if (platformOrNull == null) return
        scope.launch { refresh() }
    }

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

    private val stateFile: File get() = platform.stateFile

    /** Re-reads the file and the current output. Cheap enough to call every couple of seconds. */
    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        val p = platformOrNull ?: return@withContext
        val device = runCatching { p.currentDevice() }.getOrDefault(SelarasDevice(null, "No output"))
        val modified = p.stateFile.lastModified()
        synchronized(lock) {
            if (!dirty && (force || modified != lastSeenModified || _state.value == null)) {
                root = loadState()
                lastSeenModified = modified
            }
            deviceKey = device.key
            deviceName = device.name
            // As the Selaras window does, so Devices can show a name rather than a sink id.
            val key = device.key
            if (key != null && root.selMap("device_names")[key] != device.name) root.mmap("device_names")[key] = device.name
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
        val p = platformOrNull
        _state.value = Snapshot(deepCopy(root), deviceKey, deviceName, p?.engineRunning() ?: false, revision)
        dspConfig = if (p == null || p.systemWide) null else buildDspConfig(root, settingsFor(root, deviceKey))
    }

    /** The audio thread's view of the current output's settings. */
    private fun buildDspConfig(root: Map<String, Any?>, s: Map<String, Any?>): SelarasDspConfig {
        val profiles = root.selMap("profiles")
        val pre = 10.0.pow(totalPreamp(s, profiles) / 20)
        val bal = s.selDouble("balance", 0.0).coerceIn(-100.0, 100.0) / 100
        val lim = s.selMap("limiter")
        val comp = s.selMap("compressor")
        return SelarasDspConfig(
            enabled = root.selBool("enabled", true),
            filters = filterList(s, profiles).map { SelarasFilter(it.label, it.freq, it.q, it.gain) },
            gainL = pre * (1 - max(0.0, bal)),
            gainR = pre * (1 - max(0.0, -bal)),
            limiter = lim.selBool("enabled", true),
            limiterDb = lim.selDouble("threshold", -1.0).coerceIn(-24.0, 0.0),
            compressor = comp.selBool("enabled", false),
            compThresholdDb = comp.selDouble("threshold", -22.0),
            compRatio = comp.selDouble("ratio", 3.0),
            compAttackMs = comp.selDouble("attack", 15.0),
            compReleaseMs = comp.selDouble("release", 150.0),
            compMakeupDb = comp.selDouble("makeup", 4.0),
        )
    }

    /**
     * Writes coalesced to one every 40 ms, as the Selaras window does: a band dragged across its
     * whole travel is a few dozen states, and only the last of each burst needs to reach the engine.
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
                    val file = stateFile
                    val dir = file.absoluteFile.parentFile
                    dir.mkdirs()
                    val tmp = File(dir, file.name + ".tmp")
                    tmp.writeText(text, Charsets.UTF_8)
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                    synchronized(lock) { lastSeenModified = file.lastModified() }
                }
                runCatching { platform.afterWrite() }
                delay(40)
            }
        }
    }

    // ---- The engine -----------------------------------------------------------------------------

    /** Starts the external engine (system-wide mode) and re-reads the state. */
    suspend fun startDaemon() = withContext(Dispatchers.IO) {
        platformOrNull?.startEngine()
        delay(800)
        refresh(force = true)
    }

    /** Opens the full Selaras window, where it is installed. */
    fun openApp(vararg args: String) {
        platformOrNull?.openApp(*args)
    }

    // ---- Persistence, as selaras.model.load_state ----------------------------------------------

    private fun loadState(): MutableMap<String, Any?> {
        val state = defaultState()
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val data = fromJson(Json.parseToJsonElement(stateFile.readText(Charsets.UTF_8))) as? MutableMap<String, Any?>
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

    internal data class Biquad(val label: String, val freq: Double, val q: Double, val gain: Double)

    internal fun filterList(s: Map<String, Any?>, profiles: Map<String, Any?>): List<Biquad> {
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

    private fun coeffs(b: Biquad): DoubleArray = selarasBiquad(b.label, b.freq, b.q, b.gain, SAMPLE_RATE)

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
    private val ENTRY = Regex("""^- \[(.+?)\]\(\./(.+)\) by (.+)$""")
    private val FILTER_RE = Regex(
        """Filter\s*\d*:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val PREAMP_RE = Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
    private var index: List<AutoEqEntry>? = null

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "Selaras-Equalizer")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            conn.disconnect()
        }
    }

    /** The AutoEq results index, cached where Selaras caches it and refreshed weekly. */
    suspend fun autoEqIndex(): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        index?.let { return@withContext it }
        val cacheDir = platform.cacheDir
        val file = File(cacheDir, "autoeq-index.md")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 7L * 24 * 3600 * 1000
        if (!fresh) {
            try {
                val text = httpGet(AUTOEQ_RAW + "INDEX.md")
                cacheDir.mkdirs()
                file.writeText(text, Charsets.UTF_8)
            } catch (e: Exception) {
                if (!file.exists()) throw e
            }
        }
        file.readLines(Charsets.UTF_8).mapNotNull { line ->
            ENTRY.matchEntire(line.trim())?.let {
                AutoEqEntry(it.groupValues[1], it.groupValues[2], it.groupValues[3])
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
        val folder = URLDecoder.decode(entry.path, "UTF-8")
        val leaf = folder.substringAfterLast('/')
        val encoded = "$folder/$leaf ParametricEQ.txt".split('/')
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
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

    /** Limiter and compressor: always in-app; in system-wide mode only with the LV2 plugins. */
    val dynamicsAvailable: Boolean get() = platformOrNull?.dynamicsAvailable ?: true

    /** The projectM visualizer of the Selaras window, where that is installed. */
    val visualizerAvailable: Boolean get() = platformOrNull?.visualizerAvailable ?: false

    suspend fun loginEnabled(): Boolean? = withContext(Dispatchers.IO) { platformOrNull?.loginEnabled() }

    suspend fun setLogin(on: Boolean) = withContext(Dispatchers.IO) { platformOrNull?.setLogin(on) }

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

    /** Keeps an AutoEq `ParametricEQ.txt` as a headphone profile named after its file, and uses it. */
    fun importProfileText(fileName: String, text: String): String {
        val profile = parseAutoEq(text)
        val stem = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
        val name = stem.replace(Regex("""\s*ParametricEQ$"""), "").trim().ifEmpty { stem.ifEmpty { "Imported profile" } }
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

    /** The Selaras window's skin file, where that window exists. */
    fun openSkin() {
        platformOrNull?.openSkin()
    }
    val skinPath: String? get() = platformOrNull?.skinPath

    /** The desktop's own keyboard-shortcut settings, where the Selaras service registers its keys. */
    fun openShortcutSettings(): Boolean = platformOrNull?.openShortcutSettings() ?: false

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

fun Map<String, Any?>.selMap(key: String): Map<String, Any?> = (this[key] as? Map<*, *>)?.asMap() ?: emptyMap()
fun Map<String, Any?>.selList(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()
fun Map<String, Any?>.selDouble(key: String, default: Double) = (this[key] as? Number)?.toDouble() ?: default
fun Map<String, Any?>.selInt(key: String, default: Int) = (this[key] as? Number)?.toInt() ?: default
fun Map<String, Any?>.selBool(key: String, default: Boolean) = this[key] as? Boolean ?: default
fun Map<String, Any?>.selDoubles(key: String) = selList(key).map { (it as? Number)?.toDouble() ?: 0.0 }
