package com.music.bitchord.selaras

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One filter in the chain, as `selaras.model.filter_list` describes it. */
class SelarasFilter(val label: String, val freq: Double, val q: Double, val gain: Double)

/**
 * Everything the audio thread needs, worked out once per edit on whoever made it ([Selaras]).
 * Immutable, so the audio thread can pick it up with one volatile read.
 */
class SelarasDspConfig(
    val enabled: Boolean,
    val filters: List<SelarasFilter>,
    /** Linear input gain of the left and right chains: preamp and balance together. */
    val gainL: Double,
    val gainR: Double,
    val limiter: Boolean,
    val limiterDb: Double,
    val compressor: Boolean,
    val compThresholdDb: Double,
    val compRatio: Double,
    val compAttackMs: Double,
    val compReleaseMs: Double,
    val compMakeupDb: Double,
)

/**
 * The RBJ cookbook biquad Selaras uses everywhere, as `(b0, b1, b2, a0, a1, a2)` — the same
 * shapes PipeWire's `bq_*` builtins realise, so the in-app sound matches the system-wide one.
 */
fun selarasBiquad(label: String, freq: Double, q: Double, gain: Double, sampleRate: Double): DoubleArray {
    val a = 10.0.pow(gain / 40)
    val w0 = 2 * PI * min(freq, sampleRate * 0.499) / sampleRate
    val cw = cos(w0)
    val sw = sin(w0)
    val alpha = sw / (2 * max(q, 1e-3))
    val sa = 2 * sqrt(a) * alpha
    return when (label) {
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

/**
 * Selaras running inside BitChord: preamp and balance, the EQ and headphone-profile biquads, then
 * compressor and limiter — the same chain the daemon builds in PipeWire, and a port of the macOS
 * engine's `selaras.dsp.Processor`.
 *
 * Works in place on interleaved float blocks. With the equaliser off, or with nothing to do, it
 * returns before touching a sample, so bit-exact paths stay bit-exact.
 *
 * One instance per signal path; it is not thread-safe, but it only ever reads [Selaras.dspConfig],
 * which edits replace wholesale.
 */
class SelarasDsp {
    private var sampleRate = 48000
    private var channels = 2

    private var config: SelarasDspConfig? = null
    private var builtFor = -1

    /** Normalised coefficients, five per section: b0 b1 b2 a1 a2. */
    private var coeffs = DoubleArray(0)
    private var sections = 0

    /** Transposed direct form II state, two per section per channel. */
    private var state = DoubleArray(0)

    private var compGainDb = 0.0
    private var compApplied = 1.0
    private var limGain = 1.0

    /** Whether the last block was changed at all, for stages after this one that care. */
    @Volatile var isShaping: Boolean = false
        private set

    fun configure(sampleRate: Int, channels: Int) {
        val rate = sampleRate.coerceAtLeast(8000)
        val ch = channels.coerceAtLeast(1)
        if (rate != this.sampleRate || ch != this.channels) {
            this.sampleRate = rate
            this.channels = ch
            config = null
            builtFor = -1
        }
        reset()
    }

    /** Clears filter memory and dynamics, e.g. after a seek. */
    fun reset() {
        state.fill(0.0)
        compGainDb = 0.0
        compApplied = 1.0
        limGain = 1.0
    }

    fun process(samples: FloatArray, offset: Int, frames: Int) {
        if (frames <= 0) return
        val cfg = Selaras.dspConfig
        if (cfg == null || !cfg.enabled) {
            isShaping = false
            return
        }
        if (cfg !== config || builtFor != sampleRate) rebuild(cfg)
        val ch = channels
        val end = offset + frames * ch

        var touched = false
        // Preamp and balance.
        if (abs(cfg.gainL - 1.0) > 1e-9 || abs(cfg.gainR - 1.0) > 1e-9) {
            touched = true
            val gl = cfg.gainL.toFloat()
            val gr = cfg.gainR.toFloat()
            val mid = ((cfg.gainL + cfg.gainR) / 2).toFloat()
            var i = offset
            while (i < end) {
                for (c in 0 until ch) {
                    samples[i + c] *= when {
                        ch == 1 -> mid
                        c % 2 == 0 -> gl
                        else -> gr
                    }
                }
                i += ch
            }
        }

        // The biquads, in double precision.
        if (sections > 0) {
            touched = true
            val co = coeffs
            val st = state
            for (c in 0 until ch) {
                var i = offset + c
                while (i < end) {
                    var x = samples[i].toDouble()
                    var s = 0
                    var z = c * sections * 2
                    while (s < sections) {
                        val b = s * 5
                        val y = co[b] * x + st[z]
                        st[z] = co[b + 1] * x - co[b + 3] * y + st[z + 1]
                        st[z + 1] = co[b + 2] * x - co[b + 4] * y
                        x = y
                        s++
                        z += 2
                    }
                    samples[i] = x.toFloat()
                    i += ch
                }
            }
            // A denormal or a runaway filter must never reach the speakers.
            for (k in st.indices) {
                val v = st[k]
                if (v.isNaN() || abs(v) > 1e6) st.fill(0.0)
                else if (abs(v) < 1e-25) st[k] = 0.0
            }
        }

        if (cfg.compressor) {
            touched = true
            compress(cfg, samples, offset, frames)
        } else {
            compGainDb = 0.0
            compApplied = 1.0
        }
        if (cfg.limiter) {
            if (limit(cfg, samples, offset, frames)) touched = true
        } else {
            limGain = 1.0
        }

        isShaping = touched
        Selaras.spectrum.feed(samples, offset, frames, ch, sampleRate)
    }

    private fun rebuild(cfg: SelarasDspConfig) {
        val active = cfg.filters.filter {
            !(it.label in SHAPING && abs(it.gain) < 0.01)
        }
        val fs = sampleRate.toDouble()
        val next = DoubleArray(active.size * 5)
        active.forEachIndexed { i, f ->
            val c = selarasBiquad(f.label, f.freq, f.q, f.gain, fs)
            val a0 = c[3]
            next[i * 5] = c[0] / a0
            next[i * 5 + 1] = c[1] / a0
            next[i * 5 + 2] = c[2] / a0
            next[i * 5 + 3] = c[4] / a0
            next[i * 5 + 4] = c[5] / a0
        }
        // Keep the filter memory when only gains moved; start clean when the chain changed shape.
        if (active.size != sections) {
            state = DoubleArray(active.size * 2 * channels)
        }
        coeffs = next
        sections = active.size
        config = cfg
        builtFor = sampleRate
    }

    /** RMS compressor on [SUB]-frame blocks, with the gain glided across each block. */
    private fun compress(cfg: SelarasDspConfig, x: FloatArray, offset: Int, frames: Int) {
        val ch = channels
        val ratio = max(1.0, cfg.compRatio)
        val dt = SUB.toDouble() / sampleRate
        val att = exp(-dt / max(1e-4, cfg.compAttackMs / 1000))
        val rel = exp(-dt / max(1e-4, cfg.compReleaseMs / 1000))
        var start = 0
        while (start < frames) {
            val n = min(SUB, frames - start)
            val from = offset + start * ch
            val to = from + n * ch
            var sum = 0.0
            for (i in from until to) sum += x[i].toDouble() * x[i]
            val level = 10 * log10(sum / (n * ch) + 1e-12)
            val target = if (level > cfg.compThresholdDb) -(level - cfg.compThresholdDb) * (1 - 1 / ratio) else 0.0
            val coef = if (target < compGainDb) att else rel
            compGainDb = coef * compGainDb + (1 - coef) * target
            val gain = 10.0.pow((compGainDb + cfg.compMakeupDb) / 20)
            ramp(x, from, n, compApplied, gain)
            compApplied = gain
            start += n
        }
    }

    /** Peak limiter: instant attack per block, 50 ms release. Returns whether it changed anything. */
    private fun limit(cfg: SelarasDspConfig, x: FloatArray, offset: Int, frames: Int): Boolean {
        val ch = channels
        val ceiling = 10.0.pow(cfg.limiterDb / 20)
        val rel = exp(-(SUB.toDouble() / sampleRate) / 0.05)
        var changed = false
        var start = 0
        while (start < frames) {
            val n = min(SUB, frames - start)
            val from = offset + start * ch
            val to = from + n * ch
            var peak = 1e-12
            for (i in from until to) peak = max(peak, abs(x[i].toDouble()))
            val need = min(1.0, ceiling / peak)
            if (need < limGain) {
                // Attack: the whole block at the new gain, so nothing in it gets past.
                limGain = need
                scale(x, from, to, limGain)
                changed = true
            } else {
                val previous = limGain
                limGain = rel * limGain + (1 - rel) * need
                if (limGain > 0.99999) limGain = 1.0
                if (previous < 1.0 || limGain < 1.0) {
                    ramp(x, from, n, previous, limGain)
                    changed = true
                }
            }
            start += n
        }
        return changed
    }

    private fun scale(x: FloatArray, from: Int, to: Int, gain: Double) {
        val g = gain.toFloat()
        for (i in from until to) x[i] *= g
    }

    /** Multiplies [n] frames from [from], gliding the gain from [g0] to [g1] so it never zips. */
    private fun ramp(x: FloatArray, from: Int, n: Int, g0: Double, g1: Double) {
        val ch = channels
        if (abs(g1 - g0) < 1e-9) {
            scale(x, from, from + n * ch, g1)
            return
        }
        val step = (g1 - g0) / n
        var g = g0
        var i = from
        repeat(n) {
            g += step
            val gf = g.toFloat()
            for (c in 0 until ch) x[i + c] *= gf
            i += ch
        }
    }

    private companion object {
        const val SUB = 64
        val SHAPING = setOf("bq_peaking", "bq_lowshelf", "bq_highshelf")
    }
}
