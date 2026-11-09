package com.music.bitchord.selaras

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The live spectrum and L/R peak meters on the Selaras page, as `selaras.spectrum` draws them.
 *
 * Fed with the processed output: by [SelarasDsp] when BitChord does the equalising, or by the
 * platform's monitor of the system output when an external engine does. Only works while someone
 * is looking ([start]/[stop]), so playback pays nothing for it the rest of the time.
 */
class SelarasSpectrum {

    private val window = DoubleArray(N) { 0.5 - 0.5 * cos(2 * PI * it / (N - 1)) }
    private val cosTable = DoubleArray(N / 2) { cos(-2 * PI * it / N) }
    private val sinTable = DoubleArray(N / 2) { sin(-2 * PI * it / N) }
    private val buf = DoubleArray(N)
    private val re = DoubleArray(N)
    private val im = DoubleArray(N)
    private var filled = 0
    private var peakL = 0.0
    private var peakR = 0.0
    private val lock = Any()

    @Volatile private var users = 0
    @Volatile private var lastFeed = 0L
    private var monitor: AutoCloseable? = null

    /** Whether anyone is looking; feeders skip all work otherwise. */
    val active: Boolean get() = users > 0

    @Volatile private var bars = DoubleArray(BARS) { FLOOR }
    @Volatile private var meterL = FLOOR
    @Volatile private var meterR = FLOOR

    private val stale: Boolean get() = System.nanoTime() - lastFeed > 400_000_000L

    val levels: DoubleArray get() = if (stale) DoubleArray(BARS) { FLOOR } else bars
    val levelL: Double get() = if (stale) FLOOR else meterL
    val levelR: Double get() = if (stale) FLOOR else meterR

    fun start(platform: SelarasPlatform?) {
        synchronized(lock) {
            users++
            if (users == 1 && platform != null && platform.systemWide) {
                monitor = runCatching { platform.startMonitor(this) }.getOrNull()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            users = (users - 1).coerceAtLeast(0)
            if (users == 0) {
                runCatching { monitor?.close() }
                monitor = null
            }
        }
    }

    /**
     * Interleaved samples, [frames] of them from [offset]. Cheap when nobody is looking; otherwise one
     * 2048-point FFT every 1024 frames, which is a few tens of microseconds.
     */
    fun feed(samples: FloatArray, offset: Int, frames: Int, channels: Int, sampleRate: Int) {
        if (!active || frames <= 0 || channels <= 0) return
        synchronized(lock) {
            var i = offset
            repeat(frames) {
                val l = samples[i].toDouble()
                val r = if (channels > 1) samples[i + 1].toDouble() else l
                peakL = max(peakL, abs(l))
                peakR = max(peakR, abs(r))
                buf[filled++] = (l + r) * 0.5
                i += channels
                if (filled == N) {
                    analyse(sampleRate)
                    System.arraycopy(buf, HOP, buf, 0, N - HOP)
                    filled = N - HOP
                }
            }
            lastFeed = System.nanoTime()
        }
    }

    private fun analyse(rate: Int) {
        meterL = max(20 * log10(max(peakL, 1e-5)), meterL - 1.5)
        meterR = max(20 * log10(max(peakR, 1e-5)), meterR - 1.5)
        peakL = 0.0
        peakR = 0.0
        for (k in 0 until N) {
            re[k] = buf[k] * window[k]
            im[k] = 0.0
        }
        fft()
        val old = bars
        val sr = rate.coerceAtLeast(8000).toDouble()
        bars = DoubleArray(BARS) { b ->
            val lo = (EDGES[b] * N / sr).toInt()
            val hi = max(lo + 1, (EDGES[b + 1] * N / sr).toInt())
            var peak = 0.0
            for (k in lo until min(hi, N / 2)) peak = max(peak, sqrt(re[k] * re[k] + im[k] * im[k]))
            max(20 * log10(peak / (N / 4) + 1e-9), old[b] - 2.5)
        }
    }

    private fun fft() {
        var j = 0
        for (i in 1 until N) {
            var bit = N shr 1
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
        while (size <= N) {
            val half = size / 2
            val step = N / size
            for (start in 0 until N step size) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val ar = re[b] * wr - im[b] * wi
                    val ai = re[b] * wi + im[b] * wr
                    re[b] = re[a] - ar
                    im[b] = im[a] - ai
                    re[a] += ar
                    im[a] += ai
                }
            }
            size *= 2
        }
    }

    companion object {
        const val BARS = 64
        const val FLOOR = -90.0
        private const val N = 2048
        private const val HOP = N / 2
        private const val LO = 20.0
        private const val HI = 20000.0
        private val EDGES = DoubleArray(BARS + 1) { LO * (HI / LO).pow(it.toDouble() / BARS) }
    }
}
