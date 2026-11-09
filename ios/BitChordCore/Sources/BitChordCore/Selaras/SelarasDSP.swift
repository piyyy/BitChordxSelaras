import Foundation

/// What the audio thread runs, worked out once per edit. Immutable, so the audio thread can take it
/// with one locked read and keep using it.
public final class SelarasDSPConfig: @unchecked Sendable {
    public let enabled: Bool
    public let filters: [SelarasBiquadSpec]
    public let gainL: Double
    public let gainR: Double
    public let limiter: Bool
    public let limiterDb: Double
    public let compressor: Bool
    public let compThresholdDb: Double
    public let compRatio: Double
    public let compAttackMs: Double
    public let compReleaseMs: Double
    public let compMakeupDb: Double

    public init(state: SelarasState, settings s: SelarasSettings) {
        let pre = pow(10, SelarasModel.totalPreamp(s, profiles: state.profiles) / 20)
        let bal = min(max(s.balance, -100), 100) / 100
        enabled = state.enabled
        filters = SelarasModel.filterList(s, profiles: state.profiles)
        gainL = pre * (1 - max(0, bal))
        gainR = pre * (1 - max(0, -bal))
        limiter = s.limiter.enabled
        limiterDb = min(max(s.limiter.threshold, -24), 0)
        compressor = s.compressor.enabled
        compThresholdDb = s.compressor.threshold
        compRatio = s.compressor.ratio
        compAttackMs = s.compressor.attack
        compReleaseMs = s.compressor.release
        compMakeupDb = s.compressor.makeup
    }
}

/// Selaras running inside BitChord: preamp and balance, the EQ and headphone-profile biquads, then
/// compressor and limiter — the chain the Linux daemon builds in PipeWire, and the same code as the
/// Android/desktop `SelarasDsp`, working on planar (one buffer per channel) float audio.
///
/// Not thread-safe: one instance per audio path, called from that path's render thread only.
public final class SelarasProcessor {
    private var sampleRate: Double = 48000
    private var channels = 2
    private var config: SelarasDSPConfig?
    private var builtFor: Double = 0

    /// b0 b1 b2 a1 a2 per section.
    private var coeffs: [Double] = []
    private var sections = 0
    /// Two state values per section per channel (transposed direct form II).
    private var state: [Double] = []

    private var compGainDb = 0.0
    private var compApplied = 1.0
    private var limGain = 1.0

    /// Where the processed audio is sent for the meters and spectrum, when someone is looking.
    public var spectrum: SelarasSpectrum?
    /// Where the current config comes from; usually the shared store.
    public var configSource: () -> SelarasDSPConfig? = { SelarasStore.shared.dspConfig }

    public private(set) var isShaping = false

    public init() {}

    public func configure(sampleRate: Double, channels: Int) {
        let rate = max(sampleRate, 8000)
        let ch = max(channels, 1)
        if rate != self.sampleRate || ch != self.channels {
            self.sampleRate = rate
            self.channels = ch
            config = nil
            builtFor = 0
        }
        reset()
    }

    public func reset() {
        for i in state.indices { state[i] = 0 }
        compGainDb = 0
        compApplied = 1
        limGain = 1
    }

    /// Processes `frames` frames in place. `buffers[c]` is channel c.
    public func process(_ buffers: [UnsafeMutablePointer<Float>], frames: Int) {
        guard frames > 0, !buffers.isEmpty else { return }
        guard let cfg = configSource(), cfg.enabled else {
            isShaping = false
            return
        }
        if cfg !== config || builtFor != sampleRate { rebuild(cfg) }
        let ch = min(buffers.count, channels)
        var touched = false

        // Preamp and balance.
        if abs(cfg.gainL - 1) > 1e-9 || abs(cfg.gainR - 1) > 1e-9 {
            touched = true
            for c in 0..<ch {
                let g = Float(ch == 1 ? (cfg.gainL + cfg.gainR) / 2 : (c % 2 == 0 ? cfg.gainL : cfg.gainR))
                let p = buffers[c]
                for i in 0..<frames { p[i] *= g }
            }
        }

        // The biquads, in double precision.
        if sections > 0 {
            touched = true
            coeffs.withUnsafeBufferPointer { co in
                state.withUnsafeMutableBufferPointer { st in
                    for c in 0..<ch {
                        let p = buffers[c]
                        let base = c * sections * 2
                        for i in 0..<frames {
                            var x = Double(p[i])
                            var z = base
                            for s in 0..<sections {
                                let b = s * 5
                                let y = co[b] * x + st[z]
                                st[z] = co[b + 1] * x - co[b + 3] * y + st[z + 1]
                                st[z + 1] = co[b + 2] * x - co[b + 4] * y
                                x = y
                                z += 2
                            }
                            p[i] = Float(x)
                        }
                    }
                    // A denormal or a runaway filter must never reach the speakers.
                    for k in 0..<st.count {
                        let v = st[k]
                        if v.isNaN || abs(v) > 1e6 { for j in 0..<st.count { st[j] = 0 }; break }
                        if abs(v) < 1e-25 { st[k] = 0 }
                    }
                }
            }
        }

        if cfg.compressor {
            touched = true
            compress(cfg, buffers, ch, frames)
        } else {
            compGainDb = 0
            compApplied = 1
        }
        if cfg.limiter {
            if limit(cfg, buffers, ch, frames) { touched = true }
        } else {
            limGain = 1
        }
        isShaping = touched
        spectrum?.feed(buffers, channels: ch, frames: frames, sampleRate: sampleRate)
    }

    /// Interleaved convenience, for tests and for any path that hands interleaved audio.
    public func processInterleaved(_ samples: inout [Float], channels: Int) {
        let frames = samples.count / max(channels, 1)
        var planar = (0..<channels).map { c in (0..<frames).map { samples[$0 * channels + c] } }
        var pointers: [UnsafeMutablePointer<Float>] = []
        for c in 0..<channels {
            let p = UnsafeMutablePointer<Float>.allocate(capacity: frames)
            p.initialize(from: planar[c], count: frames)
            pointers.append(p)
        }
        process(pointers, frames: frames)
        for c in 0..<channels {
            for i in 0..<frames { planar[c][i] = pointers[c][i] }
            pointers[c].deallocate()
        }
        for i in 0..<frames { for c in 0..<channels { samples[i * channels + c] = planar[c][i] } }
    }

    private func rebuild(_ cfg: SelarasDSPConfig) {
        let shaping: Set<String> = ["bq_peaking", "bq_lowshelf", "bq_highshelf"]
        let active = cfg.filters.filter { !(shaping.contains($0.label) && abs($0.gain) < 0.01) }
        var next = [Double](repeating: 0, count: active.count * 5)
        for (i, f) in active.enumerated() {
            let c = SelarasModel.biquad(f, sampleRate: sampleRate)
            let a0 = c[3]
            next[i * 5] = c[0] / a0
            next[i * 5 + 1] = c[1] / a0
            next[i * 5 + 2] = c[2] / a0
            next[i * 5 + 3] = c[4] / a0
            next[i * 5 + 4] = c[5] / a0
        }
        // Keep the filter memory when only gains moved; start clean when the chain changed shape.
        if active.count != sections { state = [Double](repeating: 0, count: active.count * 2 * channels) }
        coeffs = next
        sections = active.count
        config = cfg
        builtFor = sampleRate
    }

    private static let sub = 64

    private func compress(_ cfg: SelarasDSPConfig, _ b: [UnsafeMutablePointer<Float>], _ ch: Int, _ frames: Int) {
        let ratio = max(1, cfg.compRatio)
        let dt = Double(Self.sub) / sampleRate
        let att = exp(-dt / max(1e-4, cfg.compAttackMs / 1000))
        let rel = exp(-dt / max(1e-4, cfg.compReleaseMs / 1000))
        var start = 0
        while start < frames {
            let n = min(Self.sub, frames - start)
            var sum = 0.0
            for c in 0..<ch { for i in start..<(start + n) { let v = Double(b[c][i]); sum += v * v } }
            let level = 10 * log10(sum / Double(n * ch) + 1e-12)
            let target = level > cfg.compThresholdDb ? -(level - cfg.compThresholdDb) * (1 - 1 / ratio) : 0
            let coef = target < compGainDb ? att : rel
            compGainDb = coef * compGainDb + (1 - coef) * target
            let gain = pow(10, (compGainDb + cfg.compMakeupDb) / 20)
            ramp(b, ch, start, n, compApplied, gain)
            compApplied = gain
            start += n
        }
    }

    private func limit(_ cfg: SelarasDSPConfig, _ b: [UnsafeMutablePointer<Float>], _ ch: Int, _ frames: Int) -> Bool {
        let ceiling = pow(10, cfg.limiterDb / 20)
        let rel = exp(-(Double(Self.sub) / sampleRate) / 0.05)
        var changed = false
        var start = 0
        while start < frames {
            let n = min(Self.sub, frames - start)
            var peak = 1e-12
            for c in 0..<ch { for i in start..<(start + n) { peak = max(peak, abs(Double(b[c][i]))) } }
            let need = min(1, ceiling / peak)
            if need < limGain {
                // Attack: the whole block at the new gain, so nothing in it gets past.
                limGain = need
                let g = Float(limGain)
                for c in 0..<ch { for i in start..<(start + n) { b[c][i] *= g } }
                changed = true
            } else {
                let previous = limGain
                limGain = rel * limGain + (1 - rel) * need
                if limGain > 0.99999 { limGain = 1 }
                if previous < 1 || limGain < 1 {
                    ramp(b, ch, start, n, previous, limGain)
                    changed = true
                }
            }
            start += n
        }
        return changed
    }

    /// Multiplies `n` frames from `start`, gliding from `g0` to `g1` so the gain never zips.
    private func ramp(_ b: [UnsafeMutablePointer<Float>], _ ch: Int, _ start: Int, _ n: Int, _ g0: Double, _ g1: Double) {
        let step = (g1 - g0) / Double(n)
        for c in 0..<ch {
            var g = g0
            for i in start..<(start + n) {
                g += step
                b[c][i] *= Float(g)
            }
        }
    }
}

/// Live spectrum and L/R peak meters for the Selaras page, fed with the processed audio.
public final class SelarasSpectrum: @unchecked Sendable {
    public static let bars = 64
    public static let floor = -90.0
    private static let n = 2048
    private static let hop = n / 2
    private static let edges: [Double] = (0...bars).map { 20 * pow(1000, Double($0) / Double(bars)) }

    private let lock = NSLock()
    private var buf = [Double](repeating: 0, count: SelarasSpectrum.n)
    private var re = [Double](repeating: 0, count: SelarasSpectrum.n)
    private var im = [Double](repeating: 0, count: SelarasSpectrum.n)
    private let window: [Double] = (0..<SelarasSpectrum.n).map { 0.5 - 0.5 * cos(2 * Double.pi * Double($0) / Double(SelarasSpectrum.n - 1)) }
    private let cosTable: [Double] = (0..<(SelarasSpectrum.n / 2)).map { cos(-2 * Double.pi * Double($0) / Double(SelarasSpectrum.n)) }
    private let sinTable: [Double] = (0..<(SelarasSpectrum.n / 2)).map { sin(-2 * Double.pi * Double($0) / Double(SelarasSpectrum.n)) }
    private var filled = 0
    private var peakL = 0.0, peakR = 0.0
    private var lastFeed = Date.distantPast
    private var _levels = [Double](repeating: SelarasSpectrum.floor, count: SelarasSpectrum.bars)
    private var _levelL = SelarasSpectrum.floor, _levelR = SelarasSpectrum.floor

    /// Whether anyone is looking; feeding is free otherwise.
    public var active = false

    public init() {}

    private var stale: Bool { Date().timeIntervalSince(lastFeed) > 0.4 }

    public var levels: [Double] { lock.lock(); defer { lock.unlock() }; return stale ? [Double](repeating: Self.floor, count: Self.bars) : _levels }
    public var levelL: Double { lock.lock(); defer { lock.unlock() }; return stale ? Self.floor : _levelL }
    public var levelR: Double { lock.lock(); defer { lock.unlock() }; return stale ? Self.floor : _levelR }

    public func feed(_ buffers: [UnsafeMutablePointer<Float>], channels: Int, frames: Int, sampleRate: Double) {
        guard active, frames > 0, channels > 0 else { return }
        guard lock.`try`() else { return }  // never block the audio thread
        defer { lock.unlock() }
        let right = channels > 1 ? 1 : 0
        for i in 0..<frames {
            let l = Double(buffers[0][i]), r = Double(buffers[right][i])
            peakL = max(peakL, abs(l))
            peakR = max(peakR, abs(r))
            buf[filled] = (l + r) * 0.5
            filled += 1
            if filled == Self.n {
                analyse(sampleRate)
                for k in 0..<(Self.n - Self.hop) { buf[k] = buf[k + Self.hop] }
                filled = Self.n - Self.hop
            }
        }
        lastFeed = Date()
    }

    private func analyse(_ rate: Double) {
        _levelL = max(20 * log10(max(peakL, 1e-5)), _levelL - 1.5)
        _levelR = max(20 * log10(max(peakR, 1e-5)), _levelR - 1.5)
        peakL = 0
        peakR = 0
        let n = Self.n
        for k in 0..<n { re[k] = buf[k] * window[k]; im[k] = 0 }
        fft()
        let old = _levels
        _levels = (0..<Self.bars).map { b in
            let lo = Int(Self.edges[b] * Double(n) / rate)
            let hi = max(lo + 1, Int(Self.edges[b + 1] * Double(n) / rate))
            var peak = 0.0
            if lo < n / 2 { for k in lo..<min(hi, n / 2) { peak = max(peak, sqrt(re[k] * re[k] + im[k] * im[k])) } }
            return max(20 * log10(peak / Double(n / 4) + 1e-9), old[b] - 2.5)
        }
    }

    private func fft() {
        let n = Self.n
        var j = 0
        for i in 1..<n {
            var bit = n >> 1
            while j & bit != 0 { j ^= bit; bit >>= 1 }
            j |= bit
            if i < j { re.swapAt(i, j); im.swapAt(i, j) }
        }
        var size = 2
        while size <= n {
            let half = size / 2, step = n / size
            var start = 0
            while start < n {
                for k in 0..<half {
                    let wr = cosTable[k * step], wi = sinTable[k * step]
                    let a = start + k, b = a + half
                    let ar = re[b] * wr - im[b] * wi
                    let ai = re[b] * wi + im[b] * wr
                    re[b] = re[a] - ar; im[b] = im[a] - ai
                    re[a] += ar; im[a] += ai
                }
                start += size
            }
            size *= 2
        }
    }
}
