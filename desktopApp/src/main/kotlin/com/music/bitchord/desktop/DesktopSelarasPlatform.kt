package com.music.bitchord.desktop

import com.music.bitchord.selaras.SelarasDevice
import com.music.bitchord.selaras.SelarasPlatform
import com.music.bitchord.selaras.SelarasSpectrum
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The output device BitChord's engine has open, for Selaras' per-device EQ in in-app mode. */
internal object DesktopSelarasOutput {
    @Volatile var name: String? = null
}

/**
 * Picks how Selaras runs on this computer: system-wide through the Selaras daemon on Linux where it
 * is installed, otherwise inside BitChord's own audio path (Windows, macOS, Linux without it).
 */
internal object DesktopSelarasPlatform {
    private val home = System.getProperty("user.home")
    private val os = System.getProperty("os.name").lowercase()
    private val isLinux = "linux" in os
    private val isWindows = "windows" in os
    private val isMac = "mac" in os

    private fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    /** Finds a command on PATH, or where a per-user or packaged install puts it. */
    fun executable(name: String): File? {
        val dirs = (System.getenv("PATH") ?: "").split(File.pathSeparator) + listOf("$home/.local/bin", "/usr/bin", "/usr/local/bin")
        return dirs.asSequence().filter { it.isNotBlank() }.map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }
    }

    /** `BITCHORD_SELARAS=app` keeps the EQ inside BitChord even where the Selaras service is installed. */
    fun create(): SelarasPlatform = when {
        env("BITCHORD_SELARAS") == "app" -> InApp()
        isLinux && executable("selaras-daemon") != null -> LinuxDaemon()
        else -> InApp()
    }

    // ---- In BitChord ---------------------------------------------------------------------------

    private class InApp : SelarasPlatform {
        private val base: File = when {
            isWindows -> File(env("APPDATA") ?: "$home/AppData/Roaming", "BitChord/selaras")
            isMac -> File("$home/Library/Application Support/BitChord/selaras")
            else -> File(env("XDG_CONFIG_HOME") ?: "$home/.config", "bitchord/selaras")
        }
        override val systemWide = false
        override val stateFile = File(base, "state.json")
        override val cacheDir: File = when {
            isWindows -> File(env("LOCALAPPDATA") ?: "$home/AppData/Local", "BitChord/selaras")
            isMac -> File("$home/Library/Caches/BitChord/selaras")
            else -> File(env("XDG_CACHE_HOME") ?: "$home/.cache", "bitchord/selaras")
        }

        override fun currentDevice(): SelarasDevice {
            val name = DesktopSelarasOutput.name?.takeIf { it.isNotBlank() }
            return if (name == null) SelarasDevice(null, "System default output") else SelarasDevice("out:$name", name)
        }
    }

    // ---- System-wide, through the Selaras daemon ------------------------------------------------

    private class LinuxDaemon : SelarasPlatform {
        private val configDir = File(env("XDG_CONFIG_HOME") ?: "$home/.config", "selaras")
        private val runtimeDir = File(
            env("SELARAS_RUNTIME_DIR") ?: ((env("XDG_RUNTIME_DIR") ?: System.getProperty("java.io.tmpdir")) + "/selaras"),
        )
        private val pidFile = File(runtimeDir, "daemon.pid")

        override val systemWide = true
        override val stateFile = File(configDir, "state.json")
        override val cacheDir = File(env("XDG_CACHE_HOME") ?: "$home/.cache", "selaras")

        override fun currentDevice(): SelarasDevice {
            val name = exec(listOf("pactl", "get-default-sink"))?.trim()?.takeIf { it.isNotEmpty() }
                ?: return SelarasDevice(null, "No output")
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
            return SelarasDevice(if (port != null) "$name#$port" else name, desc)
        }

        private fun daemonPid(): Long? = runCatching {
            val pid = pidFile.readText().trim().toLong()
            val cmdline = File("/proc/$pid/cmdline").readBytes().toString(Charsets.UTF_8)
            pid.takeIf { "selaras" in cmdline }
        }.getOrNull()

        override fun engineRunning() = daemonPid() != null

        override fun afterWrite() {
            val pid = daemonPid() ?: return
            exec(listOf("kill", "-HUP", pid.toString()))
        }

        override suspend fun startEngine() {
            withContext(Dispatchers.IO) {
                val viaSystemd = executable("systemctl") != null &&
                    exec(listOf("systemctl", "--user", "start", "selaras.service")) != null &&
                    exec(listOf("systemctl", "--user", "is-active", "selaras.service"))?.trim() == "active"
                if (!viaSystemd) {
                    executable("selaras-daemon")?.let {
                        runCatching { ProcessBuilder(it.path).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start() }
                    }
                }
            }
        }

        private fun lv2Paths() = (env("LV2_PATH")?.split(":")?.filter { it.isNotBlank() } ?: emptyList()) +
            listOf("/usr/lib/lv2", "/usr/lib64/lv2", "/usr/local/lib/lv2", "/usr/local/lib64/lv2", "$home/.lv2")

        /** PipeWire 1.4+ loads LV2 through its own plugin, which Fedora ships separately. */
        private fun pipewireLv2(): Boolean {
            val graphs = listOf("/usr/lib64", "/usr/lib", "/usr/lib/x86_64-linux-gnu", "/usr/local/lib64", "/usr/local/lib")
                .map { File(it, "spa-0.2/filter-graph") }
            if (graphs.none { it.isDirectory }) return true
            return graphs.any { File(it, "libspa-filter-graph-plugin-lv2.so").exists() }
        }

        private val lspFound get() = lv2Paths().any { File(it, "lsp-plugins.lv2/limiter_stereo.ttl").exists() }

        override val dynamicsAvailable: Boolean get() = lspFound && pipewireLv2()

        override val dynamicsHint: String
            get() = when {
                !lspFound -> "Install the LSP plugins to enable the limiter and compressor:  sudo dnf install lsp-plugins-lv2"
                else -> "Install PipeWire's LV2 support to enable the limiter and compressor:  sudo dnf install pipewire-module-filter-chain-lv2"
            }

        override fun startMonitor(spectrum: SelarasSpectrum): AutoCloseable? {
            val p = runCatching {
                ProcessBuilder(
                    "parec", "-d", "@DEFAULT_MONITOR@", "--raw", "--format=float32le", "--channels=2",
                    "--rate=$RATE", "--latency-msec=30", "--client-name=BitChord Selaras spectrum", "--stream-name=Spectrum",
                ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            }.getOrNull() ?: return null
            Thread({
                val raw = ByteArray(1024 * 8)
                val floats = FloatArray(1024 * 2)
                val input = p.inputStream
                try {
                    while (true) {
                        var read = 0
                        while (read < raw.size) {
                            val n = input.read(raw, read, raw.size - read)
                            if (n < 0) return@Thread
                            read += n
                        }
                        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                        spectrum.feed(floats, 0, 1024, 2, RATE)
                    }
                } catch (_: Exception) {
                } finally {
                    p.destroy()
                }
            }, "selaras-spectrum").apply { isDaemon = true }.start()
            return AutoCloseable { p.destroy() }
        }

        override val hasSelarasApp: Boolean get() = executable("selaras") != null

        override fun openApp(vararg args: String) {
            val exe = executable("selaras") ?: return
            runCatching {
                ProcessBuilder(listOf(exe.path) + args).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            }
        }

        override val visualizerAvailable: Boolean by lazy {
            listOf("/usr/lib64", "/usr/lib", "/usr/local/lib", "/usr/local/lib64", "/usr/lib/x86_64-linux-gnu")
                .any { File(it, "libprojectM-4.so.4").exists() }
        }

        override suspend fun loginEnabled(): Boolean? = withContext(Dispatchers.IO) {
            if (executable("systemctl") == null) null
            else exec(listOf("systemctl", "--user", "is-enabled", "selaras.service"))?.trim() == "enabled"
        }

        override suspend fun setLogin(on: Boolean) {
            withContext(Dispatchers.IO) { exec(listOf("systemctl", "--user", if (on) "enable" else "disable", "selaras.service")) }
        }

        override fun openShortcutSettings(): Boolean {
            for (cmd in listOf(listOf("systemsettings", "kcm_keys"), listOf("kcmshell6", "kcm_keys"), listOf("gnome-control-center", "keyboard"))) {
                if (executable(cmd[0]) != null) {
                    runCatching { ProcessBuilder(cmd).start() }
                    return true
                }
            }
            return false
        }

        override val skinPath: String get() = File(configDir, "style.css").path

        override fun openSkin() {
            val skin = File(configDir, "style.css")
            runCatching {
                if (!skin.exists()) {
                    configDir.mkdirs()
                    skin.writeText("/* Selaras skin: GTK4 CSS, loaded on start (Settings › Advanced › Reload). */\n")
                }
                ProcessBuilder("xdg-open", skin.path).start()
            }
        }

        override fun engineNotes(): List<String> = listOf(
            "Dynamics: " + if (dynamicsAvailable) "available" else "missing LV2 support",
            "Visualizer: " + if (visualizerAvailable) "libprojectM 4 found" else "libprojectM 4 missing",
        )

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

        private companion object {
            const val RATE = 48000
        }
    }
}
