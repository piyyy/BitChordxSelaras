import XCTest
@testable import BitChordCore

/// The same checks as the Kotlin `SelarasDspTest`, so all platforms are held to one standard.
final class SelarasTests: XCTestCase {
    var store: SelarasStore!

    override func setUp() {
        let dir = URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("selaras-\(UUID().uuidString)")
        store = SelarasStore(fileURL: dir.appendingPathComponent("state.json"), cacheURL: dir)
        store.setDevice(key: "speaker", name: "Speaker")
        store.edit { _, s in
            s.limiter.enabled = false
            s.compressor.enabled = false
        }
    }

    private func sine(_ hz: Double, frames: Int, amp: Double = 0.25) -> [Float] {
        (0..<(frames * 2)).map { Float(amp * sin(2 * .pi * hz * Double($0 / 2) / 48000)) }
    }

    private func rmsDb(_ x: [Float]) -> Double {
        let tail = x[(x.count / 2)...]
        let sum = tail.reduce(0.0) { $0 + Double($1) * Double($1) }
        return 20 * log10(sqrt(sum / Double(tail.count)) + 1e-12)
    }

    private func run(_ x: [Float]) -> [Float] {
        let dsp = SelarasProcessor()
        let source = store!
        dsp.configSource = { source.dspConfig }
        dsp.configure(sampleRate: 48000, channels: 2)
        var y = x
        dsp.processInterleaved(&y, channels: 2)
        return y
    }

    func testFlatIsBitIdentical() {
        let x = sine(1000, frames: 4800)
        XCTAssertEqual(run(x), x)
    }

    func testOffIsBitIdentical() {
        store.pickPreset("Bass Boost")
        store.setEnabled(false)
        let x = sine(60, frames: 4800)
        XCTAssertEqual(run(x), x)
    }

    func testBassBoost() {
        store.pickPreset("Bass Boost")
        store.edit { _, s in s.autoPreamp = false; s.preamp = 0 }
        let low = rmsDb(run(sine(40, frames: 48000))) - rmsDb(sine(40, frames: 48000))
        let high = rmsDb(run(sine(10000, frames: 48000))) - rmsDb(sine(10000, frames: 48000))
        XCTAssertGreaterThan(low, 4)
        XCTAssertLessThan(abs(high), 0.5)
    }

    func testGraphMatchesSound() {
        store.pickPreset("Vocal")
        store.edit { _, s in s.autoPreamp = false; s.preamp = 0 }
        let s = store.settings
        for hz in [62.0, 1000, 2000, 8000] {
            let drawn = SelarasModel.responseCurve(s, profiles: store.state.profiles, freqs: [hz])[0]
            let heard = rmsDb(run(sine(hz, frames: 48000))) - rmsDb(sine(hz, frames: 48000))
            XCTAssertEqual(drawn, heard, accuracy: 0.3, "at \(hz) Hz")
        }
    }

    func testLimiterCeiling() {
        store.edit { _, s in s.limiter.enabled = true; s.limiter.threshold = -3 }
        let out = run(sine(200, frames: 48000, amp: 0.99))
        let ceiling = pow(10, -3.0 / 20)
        XCTAssertLessThanOrEqual(Double(out.map(abs).max() ?? 0), ceiling + 1e-4)
    }

    func testPerDevice() {
        store.pickPreset("Rock")
        store.setDevice(key: "bt:Buds", name: "Buds")
        store.pickPreset("Classical")
        store.setDevice(key: "speaker", name: "Speaker")
        XCTAssertEqual(store.settings.preset, "Rock")
        store.setDevice(key: "bt:Buds", name: "Buds")
        XCTAssertEqual(store.settings.preset, "Classical")
    }

    func testStateRoundTripsInSelarasFormat() throws {
        store.pickPreset("Jazz")
        store.saveNow()
        let text = try String(contentsOf: store.fileURL, encoding: .utf8)
        XCTAssertTrue(text.contains("\"version\" : 2"))
        XCTAssertTrue(text.contains("\"band_count\""))
        let reread = try JSONDecoder().decode(SelarasState.self, from: Data(text.utf8))
        XCTAssertEqual(reread.settings(for: "speaker").preset, "Jazz")
    }

    func testReadsALinuxStateFile() throws {
        let linux = """
        {"version": 2, "enabled": true, "default": {"preset": "Bass Boost", "eq_mode": "graphic", "band_count": 10,
         "freq_start": 31.0, "freq_end": 16000.0, "bands": [6,5,4,2,0,0,0,0,0,0], "parametric": [], "bass": 0.0,
         "treble": 0.0, "bass_freq": 100.0, "treble_freq": 8000.0, "preamp": 0.0, "auto_preamp": true, "profile": null,
         "balance": 0.0, "limiter": {"enabled": true, "threshold": -1.0}, "compressor": {"enabled": false, "strength": 1}},
         "devices": {}, "device_names": {}, "presets": {}, "profiles": {}, "options": {"per_device": true}}
        """
        let state = try JSONDecoder().decode(SelarasState.self, from: Data(linux.utf8))
        XCTAssertEqual(state.defaults.preset, "Bass Boost")
        XCTAssertEqual(state.defaults.shape.bands.first, 6)
        XCTAssertEqual(state.defaults.compressor.ratio, 3)
    }

    func testAutoEqParse() throws {
        let text = """
        Preamp: -6.2 dB
        Filter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
        Filter 2: ON PK Fc 2000 Hz Gain -3.1 dB Q 1.41
        Filter 3: OFF PK Fc 3000 Hz Gain 2 dB Q 1
        """
        let profile = try SelarasModel.parseAutoEq(text)
        XCTAssertEqual(profile.preamp, -6.2)
        XCTAssertEqual(profile.filters.map(\.type), ["LSC", "PK"])
    }

    func testPresetFileRoundTrip() throws {
        store.pickPreset("Rock")
        let data = try SelarasModel.exportPreset(name: "Mine", shape: store.settings.shape)
        let (name, shape) = try SelarasModel.importPreset(data)
        XCTAssertEqual(name, "Mine")
        XCTAssertEqual(shape.bands, store.settings.shape.bands)
    }
}
