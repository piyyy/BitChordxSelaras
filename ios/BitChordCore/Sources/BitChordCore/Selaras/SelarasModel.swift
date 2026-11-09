import Foundation

// Selaras' state, in the same JSON shape as `state.json` on Linux, Windows and Android
// (version 2), so presets and headphone profiles move between devices unchanged.

public struct SelarasLimiter: Codable, Hashable, Sendable {
    public var enabled: Bool = true
    public var threshold: Double = -1
    public init() {}
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        threshold = try c.decodeIfPresent(Double.self, forKey: .threshold) ?? -1
    }
}

public struct SelarasCompressor: Codable, Hashable, Sendable {
    public var enabled = false
    public var strength = 1
    public var threshold = -22.0
    public var ratio = 3.0
    public var attack = 15.0
    public var release = 150.0
    public var makeup = 4.0
    public init() {}
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? false
        strength = try c.decodeIfPresent(Int.self, forKey: .strength) ?? 1
        threshold = try c.decodeIfPresent(Double.self, forKey: .threshold) ?? -22
        ratio = try c.decodeIfPresent(Double.self, forKey: .ratio) ?? 3
        attack = try c.decodeIfPresent(Double.self, forKey: .attack) ?? 15
        release = try c.decodeIfPresent(Double.self, forKey: .release) ?? 150
        makeup = try c.decodeIfPresent(Double.self, forKey: .makeup) ?? 4
    }
}

/// A parametric band or an AutoEq filter: PK, LSC, HSC, LP, HP or NO.
public struct SelarasFilterSpec: Codable, Hashable, Sendable, Identifiable {
    public var type: String = "PK"
    public var fc: Double = 1000
    public var gain: Double = 0
    public var q: Double = 1
    public var on: Bool = true
    public var id: String { "\(type)-\(fc)-\(gain)-\(q)" }
    public init(type: String = "PK", fc: Double = 1000, gain: Double = 0, q: Double = 1, on: Bool = true) {
        self.type = type; self.fc = fc; self.gain = gain; self.q = q; self.on = on
    }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = (try c.decodeIfPresent(String.self, forKey: .type) ?? "PK").uppercased()
        fc = try c.decodeIfPresent(Double.self, forKey: .fc) ?? 1000
        gain = try c.decodeIfPresent(Double.self, forKey: .gain) ?? 0
        q = try c.decodeIfPresent(Double.self, forKey: .q) ?? 1
        on = try c.decodeIfPresent(Bool.self, forKey: .on) ?? true
    }
}

/// An AutoEq headphone correction.
public struct SelarasProfile: Codable, Hashable, Sendable {
    public var preamp: Double = 0
    public var filters: [SelarasFilterSpec] = []
    public init(preamp: Double, filters: [SelarasFilterSpec]) { self.preamp = preamp; self.filters = filters }
}

/// The part of the settings a preset holds.
public struct SelarasShape: Codable, Hashable, Sendable {
    public var eqMode = "graphic"
    public var bandCount = 10
    public var freqStart = 31.0
    public var freqEnd = 16000.0
    public var bands: [Double] = Array(repeating: 0, count: 10)
    public var parametric: [SelarasFilterSpec] = []
    public var bass = 0.0
    public var treble = 0.0
    public var bassFreq = 100.0
    public var trebleFreq = 8000.0

    enum CodingKeys: String, CodingKey {
        case eqMode = "eq_mode", bandCount = "band_count", freqStart = "freq_start", freqEnd = "freq_end"
        case bands, parametric, bass, treble, bassFreq = "bass_freq", trebleFreq = "treble_freq"
    }
    public init() {}
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        eqMode = try c.decodeIfPresent(String.self, forKey: .eqMode) ?? "graphic"
        bandCount = try c.decodeIfPresent(Int.self, forKey: .bandCount) ?? 10
        freqStart = try c.decodeIfPresent(Double.self, forKey: .freqStart) ?? 31
        freqEnd = try c.decodeIfPresent(Double.self, forKey: .freqEnd) ?? 16000
        bands = try c.decodeIfPresent([Double].self, forKey: .bands) ?? Array(repeating: 0, count: bandCount)
        parametric = try c.decodeIfPresent([SelarasFilterSpec].self, forKey: .parametric) ?? []
        bass = try c.decodeIfPresent(Double.self, forKey: .bass) ?? 0
        treble = try c.decodeIfPresent(Double.self, forKey: .treble) ?? 0
        bassFreq = try c.decodeIfPresent(Double.self, forKey: .bassFreq) ?? 100
        trebleFreq = try c.decodeIfPresent(Double.self, forKey: .trebleFreq) ?? 8000
    }
}

/// One output's settings.
public struct SelarasSettings: Codable, Hashable, Sendable {
    public var preset = "Flat"
    public var shape = SelarasShape()
    public var preamp = 0.0
    public var autoPreamp = true
    public var profile: String?
    public var lastProfile: String?
    public var balance = 0.0
    public var limiter = SelarasLimiter()
    public var compressor = SelarasCompressor()

    enum CodingKeys: String, CodingKey {
        case preset, preamp, autoPreamp = "auto_preamp", profile, lastProfile = "last_profile", balance, limiter, compressor
    }
    public init() {}
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        preset = try c.decodeIfPresent(String.self, forKey: .preset) ?? "Flat"
        shape = try SelarasShape(from: decoder)
        preamp = try c.decodeIfPresent(Double.self, forKey: .preamp) ?? 0
        autoPreamp = try c.decodeIfPresent(Bool.self, forKey: .autoPreamp) ?? true
        profile = try c.decodeIfPresent(String.self, forKey: .profile)
        lastProfile = try c.decodeIfPresent(String.self, forKey: .lastProfile)
        balance = try c.decodeIfPresent(Double.self, forKey: .balance) ?? 0
        limiter = try c.decodeIfPresent(SelarasLimiter.self, forKey: .limiter) ?? SelarasLimiter()
        compressor = try c.decodeIfPresent(SelarasCompressor.self, forKey: .compressor) ?? SelarasCompressor()
        if shape.bands.count != shape.bandCount {
            shape.bands = SelarasModel.resampleGains(
                SelarasModel.layoutFreqs(count: shape.bands.count, start: 31, end: 16000),
                shape.bands, SelarasModel.bandFreqs(shape)
            )
        }
    }
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(preset, forKey: .preset)
        try shape.encode(to: encoder)
        try c.encode(preamp, forKey: .preamp)
        try c.encode(autoPreamp, forKey: .autoPreamp)
        try c.encode(profile, forKey: .profile)
        try c.encodeIfPresent(lastProfile, forKey: .lastProfile)
        try c.encode(balance, forKey: .balance)
        try c.encode(limiter, forKey: .limiter)
        try c.encode(compressor, forKey: .compressor)
    }
}

public struct SelarasOptions: Codable, Hashable, Sendable {
    public var perDevice = true
    public var spectrum = true
    public var quickButtons = ["profile", "limiter", "compressor"]
    enum CodingKeys: String, CodingKey { case perDevice = "per_device", spectrum, quickButtons = "quick_buttons" }
    public init() {}
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        perDevice = try c.decodeIfPresent(Bool.self, forKey: .perDevice) ?? true
        spectrum = try c.decodeIfPresent(Bool.self, forKey: .spectrum) ?? true
        quickButtons = try c.decodeIfPresent([String].self, forKey: .quickButtons) ?? ["profile", "limiter", "compressor"]
    }
}

public struct SelarasState: Codable, Hashable, Sendable {
    public var version = 2
    public var enabled = true
    public var defaults = SelarasSettings()
    public var devices: [String: SelarasSettings] = [:]
    public var deviceNames: [String: String] = [:]
    public var presets: [String: SelarasShape] = [:]
    public var profiles: [String: SelarasProfile] = [:]
    public var options = SelarasOptions()

    enum CodingKeys: String, CodingKey {
        case version, enabled, defaults = "default", devices, deviceNames = "device_names", presets, profiles, options
    }
    public init() {}
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 2
        enabled = try c.decodeIfPresent(Bool.self, forKey: .enabled) ?? true
        defaults = try c.decodeIfPresent(SelarasSettings.self, forKey: .defaults) ?? SelarasSettings()
        devices = try c.decodeIfPresent([String: SelarasSettings].self, forKey: .devices) ?? [:]
        deviceNames = try c.decodeIfPresent([String: String].self, forKey: .deviceNames) ?? [:]
        presets = try c.decodeIfPresent([String: SelarasShape].self, forKey: .presets) ?? [:]
        profiles = try c.decodeIfPresent([String: SelarasProfile].self, forKey: .profiles) ?? [:]
        options = try c.decodeIfPresent(SelarasOptions.self, forKey: .options) ?? SelarasOptions()
    }

    /// The settings for an output: its own with per-device EQ on, otherwise the default.
    public func settings(for device: String?) -> SelarasSettings {
        guard options.perDevice, let device, let own = devices[device] else { return defaults }
        return own
    }
}

/// One filter of the chain, as `selaras.model.filter_list` lists it.
public struct SelarasBiquadSpec: Hashable, Sendable {
    public var label: String
    public var freq: Double
    public var q: Double
    public var gain: Double
}

/// Selaras' constants and its pure functions: presets, band layout, filters, response, AutoEq.
public enum SelarasModel {
    public static let custom = "Custom"
    public static let gainLimit = 15.0
    public static let toneLimit = 12.0
    public static let classicFreqs: [Double] = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]
    public static let bandCounts = [5, 8, 10, 12, 15, 16, 24, 31, 32]
    public static let bassFreqs: [Double] = [60, 80, 100, 120, 150, 200, 250]
    public static let trebleFreqs: [Double] = [4000, 5000, 6000, 8000, 10000, 12000]
    public static let parametricTypes = ["PK", "LSC", "HSC", "LP", "HP", "NO"]
    public static let typeNames = ["PK": "Peak", "LSC": "Low shelf", "HSC": "High shelf", "LP": "Low-pass", "HP": "High-pass", "NO": "Notch"]
    public static let maxParametric = 32

    public static let builtinPresets: [(name: String, gains: [Double])] = [
        ("Flat", [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]),
        ("Bass Boost", [6, 5, 4, 2, 0, 0, 0, 0, 0, 0]),
        ("Deep Bass", [8, 7, 5, 2, -1, -1, 0, 0, 0, 0]),
        ("Treble Boost", [0, 0, 0, 0, 0, 0, 1, 3, 5, 6]),
        ("Vocal", [-2, -2, -1, 1, 3, 4, 4, 2, 0, -1]),
        ("Rock", [5, 4, 2, -1, -2, -1, 2, 3, 4, 4]),
        ("Pop", [-1, 0, 2, 3, 4, 3, 1, 0, -1, -1]),
        ("Electronic", [5, 4, 1, 0, -2, 1, 0, 1, 4, 5]),
        ("Jazz", [3, 2, 1, 2, -1, -1, 0, 1, 2, 3]),
        ("Classical", [4, 3, 2, 1, -1, -1, 0, 2, 3, 4]),
        ("Loudness", [5, 3, 0, 0, -1, 0, -1, 0, 3, 4]),
        ("Acoustic", [3, 3, 2, 1, 1, 1, 2, 2, 2, 1]),
        ("V-Shape", [5, 4, 2, 0, -2, -2, 0, 2, 4, 5]),
        ("Podcast", [-4, -3, -1, 1, 2, 3, 3, 1, -1, -3]),
    ]

    public static let compressorStrengths: [(threshold: Double, ratio: Double, attack: Double, release: Double, makeup: Double)] = [
        (-18, 2, 20, 200, 2), (-22, 3, 15, 150, 4), (-28, 5, 10, 100, 7),
    ]

    static let filterLabels = [
        "PK": "bq_peaking", "LSC": "bq_lowshelf", "HSC": "bq_highshelf", "LS": "bq_lowshelf",
        "HS": "bq_highshelf", "PEQ": "bq_peaking", "LP": "bq_lowpass", "HP": "bq_highpass", "NO": "bq_notch",
    ]

    public static func presetNames(_ state: SelarasState) -> [String] {
        builtinPresets.map(\.name) + state.presets.keys.sorted()
    }

    // MARK: Layout

    public static func layoutFreqs(count: Int, start: Double, end: Double) -> [Double] {
        if count == 10 && start == 31 && end == 16000 { return classicFreqs }
        guard count > 1 else { return [start] }
        return (0..<count).map { i in (start * pow(end / start, Double(i) / Double(count - 1)) * 10).rounded() / 10 }
    }

    public static func bandFreqs(_ shape: SelarasShape) -> [Double] {
        layoutFreqs(count: shape.bandCount, start: shape.freqStart, end: shape.freqEnd)
    }

    static func bandQ(_ shape: SelarasShape) -> Double {
        let n = max(shape.bandCount, 2)
        let bw = log2(shape.freqEnd / shape.freqStart) / Double(n - 1)
        return sqrt(pow(2, bw)) / (pow(2, bw) - 1)
    }

    public static func resampleGains(_ src: [Double], _ gains: [Double], _ dst: [Double]) -> [Double] {
        guard !src.isEmpty, src.count == gains.count else { return Array(repeating: 0, count: dst.count) }
        let xs = src.map(log)
        return dst.map { f in
            let x = log(f)
            if x <= xs[0] { return gains[0] }
            if x >= xs[xs.count - 1] { return gains[gains.count - 1] }
            let i = (0..<(xs.count - 1)).first { xs[$0] <= x && x <= xs[$0 + 1] } ?? 0
            let t = (x - xs[i]) / (xs[i + 1] - xs[i])
            return ((gains[i] + t * (gains[i + 1] - gains[i])) * 10).rounded() / 10
        }
    }

    // MARK: Presets

    /// Applies a built-in or user preset to `s`; false when there is no such preset.
    @discardableResult
    public static func applyPreset(_ name: String, to s: inout SelarasSettings, state: SelarasState) -> Bool {
        if let user = state.presets[name] {
            s.shape = user
        } else if let builtin = builtinPresets.first(where: { $0.name == name }) {
            var shape = s.shape
            shape.eqMode = "graphic"
            shape.bands = resampleGains(classicFreqs, builtin.gains, bandFreqs(shape))
            shape.bass = 0
            shape.treble = 0
            s.shape = shape
        } else {
            return false
        }
        s.preset = name
        return true
    }

    public static func setCompressorStrength(_ level: Int, _ s: inout SelarasSettings) {
        let l = min(max(level, 0), 2)
        let p = compressorStrengths[l]
        s.compressor.threshold = p.threshold
        s.compressor.ratio = p.ratio
        s.compressor.attack = p.attack
        s.compressor.release = p.release
        s.compressor.makeup = p.makeup
        s.compressor.strength = l
    }

    public static func relayout(_ s: inout SelarasSettings, count: Int, start: Double, end: Double) {
        let old = bandFreqs(s.shape)
        s.shape.bandCount = count
        s.shape.freqStart = start
        s.shape.freqEnd = end
        s.shape.bands = resampleGains(old, s.shape.bands, bandFreqs(s.shape))
    }

    public static func parametricFromGraphic(_ s: inout SelarasSettings) {
        let q = (bandQ(s.shape) * 100).rounded() / 100
        s.shape.parametric = zip(bandFreqs(s.shape), s.shape.bands).prefix(maxParametric).map {
            SelarasFilterSpec(type: "PK", fc: $0.0, gain: $0.1, q: q)
        }
    }

    // MARK: Filters and response

    public static func filterList(_ s: SelarasSettings, profiles: [String: SelarasProfile]) -> [SelarasBiquadSpec] {
        var out = [
            SelarasBiquadSpec(label: "bq_lowshelf", freq: s.shape.bassFreq, q: 0.707, gain: s.shape.bass),
            SelarasBiquadSpec(label: "bq_highshelf", freq: s.shape.trebleFreq, q: 0.707, gain: s.shape.treble),
        ]
        if s.shape.eqMode == "graphic" {
            let q = bandQ(s.shape)
            for (f, g) in zip(bandFreqs(s.shape), s.shape.bands) {
                out.append(SelarasBiquadSpec(label: "bq_peaking", freq: f, q: q, gain: g))
            }
        } else {
            for f in s.shape.parametric {
                var label = filterLabels[f.type] ?? "bq_peaking"
                if !f.on && ["bq_lowpass", "bq_highpass", "bq_notch"].contains(label) { label = "bq_peaking" }
                out.append(SelarasBiquadSpec(label: label, freq: f.fc, q: f.q, gain: f.on ? f.gain : 0))
            }
        }
        if let name = s.profile, let profile = profiles[name] {
            for f in profile.filters {
                out.append(SelarasBiquadSpec(label: filterLabels[f.type] ?? "bq_peaking", freq: f.fc, q: f.q, gain: f.gain))
            }
        }
        return out
    }

    /// RBJ cookbook biquad, `(b0, b1, b2, a0, a1, a2)` — the shapes PipeWire's `bq_*` builtins realise.
    public static func biquad(_ f: SelarasBiquadSpec, sampleRate: Double) -> [Double] {
        let a = pow(10, f.gain / 40)
        let w0 = 2 * Double.pi * min(f.freq, sampleRate * 0.499) / sampleRate
        let cw = cos(w0), sw = sin(w0)
        let alpha = sw / (2 * max(f.q, 1e-3))
        let sa = 2 * sqrt(a) * alpha
        switch f.label {
        case "bq_peaking": return [1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a]
        case "bq_lowpass": return [(1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha]
        case "bq_highpass": return [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha]
        case "bq_notch": return [1, -2 * cw, 1, 1 + alpha, -2 * cw, 1 - alpha]
        case "bq_lowshelf":
            return [a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
                    (a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa]
        default:
            return [a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sa),
                    (a + 1) - (a - 1) * cw + sa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa]
        }
    }

    static func isActive(_ f: SelarasBiquadSpec) -> Bool {
        f.gain != 0 || ["bq_lowpass", "bq_highpass", "bq_notch"].contains(f.label)
    }

    static func magnitudeDb(_ c: [Double], _ f: Double, sampleRate: Double = 48000) -> Double {
        let w = 2 * Double.pi * f / sampleRate
        let c1 = cos(w), s1 = sin(w), c2 = cos(2 * w), s2 = sin(2 * w)
        let nr = c[0] + c[1] * c1 + c[2] * c2, ni = -(c[1] * s1 + c[2] * s2)
        let dr = c[3] + c[4] * c1 + c[5] * c2, di = -(c[4] * s1 + c[5] * s2)
        return 10 * log10(max((nr * nr + ni * ni) / max(dr * dr + di * di, 1e-30), 1e-30))
    }

    public static func logFreqs(_ n: Int = 200, lo: Double = 20, hi: Double = 20000) -> [Double] {
        (0..<n).map { lo * pow(hi / lo, Double($0) / Double(n - 1)) }
    }

    /// The curve the chain will draw, in dB at each frequency.
    public static func responseCurve(_ s: SelarasSettings, profiles: [String: SelarasProfile], freqs: [Double], includePreamp: Bool = true) -> [Double] {
        let cs = filterList(s, profiles: profiles).filter(isActive).map { biquad($0, sampleRate: 48000) }
        let pre = includePreamp ? totalPreamp(s, profiles: profiles) : 0
        return freqs.map { f in pre + cs.reduce(0) { $0 + magnitudeDb($1, f) } }
    }

    public static func totalPreamp(_ s: SelarasSettings, profiles: [String: SelarasProfile]) -> Double {
        if s.autoPreamp {
            let peak = responseCurve(s, profiles: profiles, freqs: logFreqs(160), includePreamp: false).max() ?? 0
            return -(max(0, peak) * 100).rounded() / 100
        }
        let base = s.profile.flatMap { profiles[$0]?.preamp } ?? 0
        return ((base + s.preamp) * 100).rounded() / 100
    }

    // MARK: AutoEq

    public struct AutoEqEntry: Hashable, Sendable, Identifiable {
        public var name: String
        public var path: String
        public var source: String
        public var id: String { path }
        /// The name Selaras stores the profile under: "Model (measurer)".
        public var profileName: String { "\(name) (\(source.components(separatedBy: " on ").first ?? source))" }
    }

    public static let autoEqRaw = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/"

    public static func parseAutoEqIndex(_ text: String) -> [AutoEqEntry] {
        let regex = try! NSRegularExpression(pattern: #"^- \[(.+?)\]\(\./(.+)\) by (.+)$"#)
        return text.split(separator: "\n").compactMap { raw in
            let line = raw.trimmingCharacters(in: .whitespaces)
            guard let m = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)), m.numberOfRanges == 4,
                  let a = Range(m.range(at: 1), in: line), let b = Range(m.range(at: 2), in: line), let c = Range(m.range(at: 3), in: line)
            else { return nil }
            return AutoEqEntry(name: String(line[a]), path: String(line[b]), source: String(line[c]))
        }
    }

    public static func searchAutoEq(_ entries: [AutoEqEntry], _ query: String, limit: Int = 40) -> [AutoEqEntry] {
        let words = query.lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
        guard !words.isEmpty else { return [] }
        return Array(entries.lazy.filter { e in
            let hay = "\(e.name) \(e.source)".lowercased()
            return words.allSatisfy { hay.contains($0) }
        }.prefix(limit))
    }

    /// The URL of an index entry's `ParametricEQ.txt`.
    public static func autoEqURL(_ entry: AutoEqEntry) -> URL? {
        let folder = entry.path.removingPercentEncoding ?? entry.path
        let leaf = folder.split(separator: "/").last.map(String.init) ?? folder
        let parts = "\(folder)/\(leaf) ParametricEQ.txt".split(separator: "/").map {
            String($0).addingPercentEncoding(withAllowedCharacters: .alphanumerics.union(CharacterSet(charactersIn: "-._~()"))) ?? String($0)
        }
        return URL(string: autoEqRaw + parts.joined(separator: "/"))
    }

    public static func parseAutoEq(_ text: String) throws -> SelarasProfile {
        let preampRegex = try! NSRegularExpression(pattern: #"Preamp:\s*(-?[\d.]+)\s*dB"#, options: .caseInsensitive)
        let filterRegex = try! NSRegularExpression(
            pattern: #"Filter\s*\d*:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?"#,
            options: .caseInsensitive
        )
        let range = NSRange(text.startIndex..., in: text)
        func group(_ m: NSTextCheckingResult, _ i: Int) -> String? {
            Range(m.range(at: i), in: text).map { String(text[$0]) }
        }
        let preamp = preampRegex.firstMatch(in: text, range: range).flatMap { group($0, 1) }.flatMap(Double.init) ?? 0
        var filters: [SelarasFilterSpec] = []
        for m in filterRegex.matches(in: text, range: range) {
            guard group(m, 1)?.uppercased() == "ON", let raw = group(m, 2)?.uppercased() else { continue }
            let kind = ["LS": "LSC", "HS": "HSC", "PEQ": "PK"][raw] ?? raw
            guard filterLabels[kind] != nil, let fc = group(m, 3).flatMap(Double.init) else { continue }
            filters.append(SelarasFilterSpec(type: kind, fc: fc, gain: group(m, 4).flatMap(Double.init) ?? 0, q: group(m, 5).flatMap(Double.init) ?? 0.707))
        }
        guard !filters.isEmpty else { throw SelarasError.message("No parametric filters found") }
        return SelarasProfile(preamp: preamp, filters: Array(filters.prefix(32)))
    }

    // MARK: Preset files

    public static func exportPreset(name: String, shape: SelarasShape) throws -> Data {
        struct File: Encodable { var format = "selaras-preset"; var version = 2; var name: String; var settings: SelarasShape }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(File(name: name, settings: shape))
    }

    public static func importPreset(_ data: Data) throws -> (name: String, shape: SelarasShape) {
        struct File: Decodable { var format: String?; var name: String?; var settings: SelarasShape? }
        let file = try JSONDecoder().decode(File.self, from: data)
        guard file.format == "selaras-preset", var shape = file.settings else { throw SelarasError.message("Not a Selaras preset file") }
        guard shape.eqMode == "graphic" || shape.eqMode == "parametric" else { throw SelarasError.message("Unknown EQ mode in preset") }
        guard shape.bands.count == shape.bandCount else { throw SelarasError.message("Band count does not match the gains in the preset") }
        shape.parametric = Array(shape.parametric.prefix(maxParametric)).map {
            SelarasFilterSpec(type: parametricTypes.contains($0.type) ? $0.type : "PK", fc: min(max($0.fc, 10), 22000),
                              gain: min(max($0.gain, -gainLimit), gainLimit), q: min(max($0.q, 0.1), 20), on: $0.on)
        }
        var name = (file.name ?? "").trimmingCharacters(in: .whitespaces)
        if name.isEmpty { name = "Imported" }
        if builtinPresets.contains(where: { $0.name == name }) || name == custom { name += " (imported)" }
        return (name, shape)
    }
}

public enum SelarasError: Error, LocalizedError {
    case message(String)
    public var errorDescription: String? { if case .message(let m) = self { return m }; return nil }
}
