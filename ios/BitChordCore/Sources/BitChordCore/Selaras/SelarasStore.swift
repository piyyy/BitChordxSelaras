import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Selaras' state on this device: loads and saves `state.json`, edits the current output's
/// settings the way the Selaras window does, and hands the audio thread a fresh
/// `SelarasDSPConfig` after every edit.
public final class SelarasStore: @unchecked Sendable {
    public static let shared = SelarasStore()

    private let lock = NSLock()
    private var _state = SelarasState()
    private var _deviceKey: String?
    private var _deviceName = "iPhone"
    private var _dspConfig: SelarasDSPConfig?
    private var saveWork: DispatchWorkItem?
    private let io = DispatchQueue(label: "selaras.store")

    public var fileURL: URL
    public var cacheURL: URL
    /// Called on the main queue after every change, for the UI to redraw.
    public var onChange: (() -> Void)?
    public let spectrum = SelarasSpectrum()

    public init(fileURL: URL? = nil, cacheURL: URL? = nil) {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        self.fileURL = fileURL ?? support.appendingPathComponent("selaras/state.json")
        self.cacheURL = cacheURL ?? (FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())).appendingPathComponent("selaras")
        load()
    }

    // MARK: Reading

    public var state: SelarasState { lock.lock(); defer { lock.unlock() }; return _state }
    public var deviceKey: String? { lock.lock(); defer { lock.unlock() }; return _deviceKey }
    public var deviceName: String { lock.lock(); defer { lock.unlock() }; return _deviceName }
    public var settings: SelarasSettings { lock.lock(); defer { lock.unlock() }; return _state.settings(for: _deviceKey) }
    /// What the audio thread runs; nil before anything is loaded.
    public var dspConfig: SelarasDSPConfig? { lock.lock(); defer { lock.unlock() }; return _dspConfig }

    public func load() {
        var loaded = SelarasState()
        if let data = try? Data(contentsOf: fileURL), let decoded = try? JSONDecoder().decode(SelarasState.self, from: data) {
            loaded = decoded
        }
        lock.lock()
        _state = loaded
        rebuild()
        lock.unlock()
        notify()
    }

    /// The output changed (speaker, headphones, Bluetooth…): its own settings take over.
    public func setDevice(key: String?, name: String) {
        lock.lock()
        _deviceKey = key
        _deviceName = name
        if let key, _state.deviceNames[key] != name { _state.deviceNames[key] = name }
        rebuild()
        lock.unlock()
        notify()
    }

    // MARK: Editing

    /// Edits the current output's settings (created from the default the first time).
    public func edit(_ block: (inout SelarasState, inout SelarasSettings) -> Void) {
        lock.lock()
        var s = _state.settings(for: _deviceKey)
        block(&_state, &s)
        if _state.options.perDevice, let key = _deviceKey { _state.devices[key] = s } else { _state.defaults = s }
        rebuild()
        lock.unlock()
        changed()
    }

    /// As `edit`, and marks the curve as no longer being a saved preset.
    public func editCustom(_ block: (inout SelarasSettings) -> Void) {
        edit { _, s in
            block(&s)
            s.preset = SelarasModel.custom
        }
    }

    /// Edits state that is not one output's settings: options, presets, devices.
    public func editState(_ block: (inout SelarasState) -> Void) {
        lock.lock()
        block(&_state)
        rebuild()
        lock.unlock()
        changed()
    }

    public func setEnabled(_ on: Bool) { editState { $0.enabled = on } }

    public func pickPreset(_ name: String) {
        edit { state, s in SelarasModel.applyPreset(name, to: &s, state: state) }
    }

    public func step(preset direction: Int) -> String {
        var picked = ""
        edit { state, s in
            let names = SelarasModel.presetNames(state)
            let current = names.firstIndex(of: s.preset)
            let index = current.map { ($0 + direction + names.count) % names.count } ?? (direction > 0 ? 0 : names.count - 1)
            picked = names[index]
            SelarasModel.applyPreset(picked, to: &s, state: state)
        }
        return picked
    }

    public func savePreset(_ name: String) {
        edit { state, s in
            state.presets[name] = s.shape
            s.preset = name
        }
    }

    public func presetNameTaken(_ name: String) -> Bool {
        let lower = name.lowercased()
        return lower == SelarasModel.custom.lowercased() || SelarasModel.presetNames(state).contains { $0.lowercased() == lower }
    }

    @discardableResult
    public func deleteCurrentPreset() -> String {
        var msg = "Only your own presets can be deleted"
        editState { state in
            let name = state.settings(for: self._deviceKey).preset
            guard state.presets[name] != nil else { return }
            state.presets[name] = nil
            if state.defaults.preset == name { state.defaults.preset = SelarasModel.custom }
            for key in state.devices.keys where state.devices[key]?.preset == name { state.devices[key]?.preset = SelarasModel.custom }
            msg = "Deleted preset “\(name)”"
        }
        return msg
    }

    public func importPreset(_ data: Data) throws -> String {
        let (name, shape) = try SelarasModel.importPreset(data)
        edit { state, s in
            state.presets[name] = shape
            s.shape = shape
            s.preset = name
        }
        return name
    }

    public func importProfile(fileName: String, text: String) throws -> String {
        let profile = try SelarasModel.parseAutoEq(text)
        var stem = (fileName as NSString).deletingPathExtension
        stem = stem.replacingOccurrences(of: #"\s*ParametricEQ$"#, with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)
        let name = stem.isEmpty ? "Imported profile" : stem
        edit { state, s in
            state.profiles[name] = profile
            s.profile = name
        }
        return name
    }

    public func deleteProfile(_ name: String) {
        editState { state in
            state.profiles[name] = nil
            if state.defaults.profile == name { state.defaults.profile = nil }
            for key in state.devices.keys where state.devices[key]?.profile == name { state.devices[key]?.profile = nil }
        }
    }

    /// Runs a Selaras quick action ("toggle", "limiter"…) and says what it did.
    @discardableResult
    public func runAction(_ action: String) -> String {
        switch action {
        case "toggle":
            var on = false
            editState { $0.enabled.toggle(); on = $0.enabled }
            return on ? "Equalizer on" : "Equalizer off"
        case "next-preset": return "Preset: " + step(preset: 1)
        case "prev-preset": return "Preset: " + step(preset: -1)
        case "flat":
            pickPreset("Flat")
            return "Preset: Flat"
        case "balance-center":
            edit { _, s in s.balance = 0 }
            return "Balance centered"
        case "limiter":
            var on = false
            edit { _, s in s.limiter.enabled.toggle(); on = s.limiter.enabled }
            return "Limiter " + (on ? "on" : "off")
        case "compressor":
            var on = false
            edit { _, s in s.compressor.enabled.toggle(); on = s.compressor.enabled }
            return "Compressor " + (on ? "on" : "off")
        case "profile":
            var msg = ""
            edit { state, s in
                if let p = s.profile {
                    s.lastProfile = p
                    s.profile = nil
                    msg = "Headphone profile off"
                } else if let last = s.lastProfile, state.profiles[last] != nil {
                    s.profile = last
                    msg = "Headphone profile: \(last)"
                } else {
                    msg = "No headphone profile to turn on"
                }
            }
            return msg
        default:
            return "Unknown action: \(action)"
        }
    }

    // MARK: AutoEq

    private var autoEqIndexCache: [SelarasModel.AutoEqEntry]?

    /// The AutoEq results index, cached and refreshed weekly.
    public func autoEqIndex() async throws -> [SelarasModel.AutoEqEntry] {
        if let cached = autoEqIndexCache { return cached }
        let file = cacheURL.appendingPathComponent("autoeq-index.md")
        let attributes = try? FileManager.default.attributesOfItem(atPath: file.path)
        let modified = attributes?[.modificationDate] as? Date
        if modified == nil || Date().timeIntervalSince(modified!) > 7 * 24 * 3600 {
            do {
                let (data, _) = try await URLSession.shared.data(from: URL(string: SelarasModel.autoEqRaw + "INDEX.md")!)
                try FileManager.default.createDirectory(at: cacheURL, withIntermediateDirectories: true)
                try data.write(to: file, options: .atomic)
            } catch {
                if modified == nil { throw error }
            }
        }
        let text = try String(contentsOf: file, encoding: .utf8)
        let entries = SelarasModel.parseAutoEqIndex(text)
        autoEqIndexCache = entries
        return entries
    }

    /// Downloads an entry's ParametricEQ.txt and uses it as this output's headphone profile.
    public func installProfile(_ entry: SelarasModel.AutoEqEntry) async throws {
        guard let url = SelarasModel.autoEqURL(entry) else { throw SelarasError.message("Bad AutoEq path") }
        let (data, response) = try await URLSession.shared.data(from: url)
        if let http = response as? HTTPURLResponse, http.statusCode != 200 { throw SelarasError.message("HTTP \(http.statusCode)") }
        let profile = try SelarasModel.parseAutoEq(String(decoding: data, as: UTF8.self))
        edit { state, s in
            state.profiles[entry.profileName] = profile
            s.profile = entry.profileName
        }
    }

    // MARK: Internals

    /// Call with the lock held.
    private func rebuild() {
        _dspConfig = SelarasDSPConfig(state: _state, settings: _state.settings(for: _deviceKey))
    }

    private func changed() {
        notify()
        scheduleSave()
    }

    private func notify() {
        guard let onChange else { return }
        if Thread.isMainThread { onChange() } else { DispatchQueue.main.async(execute: onChange) }
    }

    /// Writes coalesced to one per 300 ms: a slider drag is dozens of edits.
    private func scheduleSave() {
        saveWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.saveNow() }
        saveWork = work
        io.asyncAfter(deadline: .now() + 0.3, execute: work)
    }

    public func saveNow() {
        let snapshot = state
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(snapshot) else { return }
        try? FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: fileURL, options: .atomic)
    }
}
