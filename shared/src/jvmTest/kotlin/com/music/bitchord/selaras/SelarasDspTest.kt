package com.music.bitchord.selaras

import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SelarasDspTest {

    private object TestPlatform : SelarasPlatform {
        val dir: File = Files.createTempDirectory("selaras-test").toFile()
        @Volatile var device = SelarasDevice("out:Speakers", "Speakers")
        override val systemWide = false
        override val stateFile = File(dir, "state.json")
        override val cacheDir = File(dir, "cache")
        override fun currentDevice() = device
    }

    @BeforeTest
    fun setUp() = runBlocking {
        Selaras.install(TestPlatform)
        TestPlatform.device = SelarasDevice("out:Speakers", "Speakers")
        Selaras.refresh(force = true)
        Selaras.setEnabled(true)
        Selaras.pickPreset("Flat")
        Selaras.edit { _, s ->
            @Suppress("UNCHECKED_CAST")
            (s["limiter"] as MutableMap<String, Any?>)["enabled"] = false
            @Suppress("UNCHECKED_CAST")
            (s["compressor"] as MutableMap<String, Any?>)["enabled"] = false
            s["profile"] = null
            s["balance"] = 0.0
        }
    }

    private fun sine(hz: Double, frames: Int, amp: Double = 0.25, rate: Int = 48000) =
        FloatArray(frames * 2) { i -> (amp * sin(2 * PI * hz * (i / 2) / rate)).toFloat() }

    private fun rmsDb(x: FloatArray, from: Int = x.size / 2): Double {
        var sum = 0.0
        for (i in from until x.size) sum += x[i].toDouble() * x[i]
        return 20 * log10(sqrt(sum / (x.size - from)) + 1e-12)
    }

    private fun run(x: FloatArray): FloatArray {
        val dsp = SelarasDsp()
        dsp.configure(48000, 2)
        dsp.process(x, 0, x.size / 2)
        return x
    }

    @Test
    fun flatEqLeavesSamplesBitIdentical() {
        val input = sine(1000.0, 4800)
        val output = run(input.copyOf())
        assertTrue(input.contentEquals(output), "flat Selaras must not touch a single sample")
    }

    @Test
    fun switchedOffLeavesSamplesBitIdentical() {
        Selaras.pickPreset("Bass Boost")
        Selaras.setEnabled(false)
        val input = sine(60.0, 4800)
        val output = run(input.copyOf())
        assertTrue(input.contentEquals(output))
    }

    @Test
    fun bassBoostLiftsTheBassAndNotTheTreble() {
        Selaras.pickPreset("Bass Boost")
        Selaras.edit { _, s -> s["auto_preamp"] = false; s["preamp"] = 0.0 }
        val low = rmsDb(run(sine(40.0, 48000))) - rmsDb(sine(40.0, 48000))
        val high = rmsDb(run(sine(10000.0, 48000))) - rmsDb(sine(10000.0, 48000))
        assertTrue(low > 4.0, "40 Hz should rise by several dB, rose $low")
        assertTrue(abs(high) < 0.5, "10 kHz should stay put, moved $high")
    }

    @Test
    fun responseCurveMatchesWhatTheDspDoes() {
        Selaras.pickPreset("Vocal")
        Selaras.edit { _, s -> s["auto_preamp"] = false; s["preamp"] = 0.0 }
        val snap = Selaras.state.value!!
        for (hz in listOf(62.0, 1000.0, 2000.0, 8000.0)) {
            val drawn = Selaras.responseCurve(snap.settings, snap.profiles, listOf(hz)).single()
            val heard = rmsDb(run(sine(hz, 48000))) - rmsDb(sine(hz, 48000))
            assertEquals(drawn, heard, 0.3, "at $hz Hz the graph says $drawn dB, the DSP does $heard dB")
        }
    }

    @Test
    fun limiterKeepsPeaksUnderTheCeiling() {
        Selaras.edit { _, s ->
            @Suppress("UNCHECKED_CAST")
            (s["limiter"] as MutableMap<String, Any?>).apply { put("enabled", true); put("threshold", -3.0) }
        }
        val out = run(sine(200.0, 48000, amp = 0.99))
        val ceiling = Math.pow(10.0, -3.0 / 20)
        assertTrue(out.all { abs(it) <= ceiling + 1e-4 }, "peak ${out.maxOf { abs(it) }} over ceiling $ceiling")
    }

    @Test
    fun eachOutputKeepsItsOwnEq() = runBlocking {
        Selaras.pickPreset("Rock")
        TestPlatform.device = SelarasDevice("out:Headphones", "Headphones")
        Selaras.refresh()
        Selaras.pickPreset("Classical")
        TestPlatform.device = SelarasDevice("out:Speakers", "Speakers")
        Selaras.refresh()
        assertEquals("Rock", Selaras.state.value!!.settings["preset"])
        TestPlatform.device = SelarasDevice("out:Headphones", "Headphones")
        Selaras.refresh()
        assertEquals("Classical", Selaras.state.value!!.settings["preset"])
    }

    @Test
    fun stateIsWrittenInSelarasFormat() = runBlocking {
        Selaras.pickPreset("Jazz")
        kotlinx.coroutines.delay(300)
        val text = TestPlatform.stateFile.readText()
        assertTrue("\"version\": 2" in text && "\"Jazz\"" in text, text.take(300))
    }
}
