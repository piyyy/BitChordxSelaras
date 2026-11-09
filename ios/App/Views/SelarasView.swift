import BitChordCore
import SwiftUI
import UniformTypeIdentifiers

/// Selaras on iOS: the same tabs as the desktop and Android page, drawn natively.
struct SelarasView: View {
    var compactHeader = false
    @EnvironmentObject var selaras: SelarasObservable
    @State private var tab = Tab.equalizer
    @State private var toast: String?

    enum Tab: String, CaseIterable, Identifiable {
        case equalizer = "Equalizer", tone = "Tone & Dynamics", headphones = "Headphones", devices = "Devices"
        var id: String { rawValue }
    }

    private var store: SelarasStore { selaras.store }

    var body: some View {
        let _ = selaras.revision
        VStack(spacing: 0) {
            header
            Picker("Section", selection: $tab) {
                ForEach(Tab.allCases) { Text($0 == .tone ? "Tone" : $0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.vertical, 8)
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch tab {
                    case .equalizer: EqualizerTab(say: say)
                    case .tone: ToneTab()
                    case .headphones: HeadphonesTab(say: say)
                    case .devices: DevicesTab()
                    }
                }
                .padding(.bottom, 24)
            }
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast).font(.callout).padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.thickMaterial, in: Capsule()).padding(.bottom, 12)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .onAppear { store.spectrum.active = true }
        .onDisappear { store.spectrum.active = false }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                if !compactHeader { Text("Selaras").font(.title2.bold()) }
                Text("Equalizer · \(store.deviceName)").font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Toggle("Equalizer", isOn: Binding(get: { selaras.state.enabled }, set: { store.setEnabled($0) }))
                .labelsHidden()
        }
    }

    private func say(_ text: String) {
        withAnimation { toast = text }
        Task {
            try? await Task.sleep(nanoseconds: 2_200_000_000)
            withAnimation { if toast == text { toast = nil } }
        }
    }
}

// MARK: Equalizer

private struct EqualizerTab: View {
    let say: (String) -> Void
    @EnvironmentObject var selaras: SelarasObservable
    @State private var saving = false
    @State private var newName = ""
    @State private var importing = false
    @State private var exportDoc: PresetDocument?

    var body: some View {
        let _ = selaras.revision
        let store = selaras.store
        let s = store.settings
        let state = store.state

        // Quick chips.
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip("Headphone profile", s.profile ?? "Off", s.profile != nil) { say(store.runAction("profile")) }
                chip("Limiter", s.limiter.enabled ? "On" : "Off", s.limiter.enabled) { say(store.runAction("limiter")) }
                chip("Compressor", s.compressor.enabled ? ["Light", "Medium", "Strong"][min(max(s.compressor.strength, 0), 2)] : "Off", s.compressor.enabled) {
                    say(store.runAction("compressor"))
                }
                chip("Preamp", String(format: "%+.1f dB", SelarasModel.totalPreamp(s, profiles: state.profiles)), false) {}
            }
        }

        // Presets.
        HStack {
            Text("Presets").font(.headline)
            Spacer()
            Menu {
                Button("Save as preset…") { saving = true }
                Button("Export preset…") {
                    let name = s.preset == SelarasModel.custom ? "My preset" : s.preset
                    if let data = try? SelarasModel.exportPreset(name: name, shape: s.shape) { exportDoc = PresetDocument(data: data, name: name) }
                }
                Button("Import preset…") { importing = true }
                Button("Delete this preset", role: .destructive) { say(store.deleteCurrentPreset()) }
            } label: { Image(systemName: "ellipsis.circle").font(.title3) }
        }
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if s.preset == SelarasModel.custom { pill("Custom", selected: true) {} }
                ForEach(SelarasModel.presetNames(state), id: \.self) { name in
                    pill(name, selected: name == s.preset) { store.pickPreset(name); say("Preset: \(name)") }
                }
            }
        }

        ResponseGraph(settings: s, profiles: state.profiles, enabled: state.enabled, spectrum: store.spectrum, showSpectrum: state.options.spectrum)

        Picker("Mode", selection: Binding(get: { s.shape.eqMode }, set: { m in store.editCustom { $0.shape.eqMode = m } })) {
            Text("Graphic").tag("graphic")
            Text("Parametric").tag("parametric")
        }
        .pickerStyle(.segmented)

        if s.shape.eqMode == "graphic" {
            GraphicBands(settings: s)
            HStack {
                Menu("\(s.shape.bandCount) bands") {
                    ForEach(SelarasModel.bandCounts, id: \.self) { n in
                        Button("\(n) bands") { store.editCustom { SelarasModel.relayout(&$0, count: n, start: $0.shape.freqStart, end: $0.shape.freqEnd) } }
                    }
                }
                Spacer()
                Button("Reset to flat") { store.pickPreset("Flat"); say("Preset: Flat") }
            }
            .font(.subheadline)
        } else {
            ParametricList(settings: s, say: say)
        }
        Color.clear.frame(height: 0)
            .alert("Save preset", isPresented: $saving) {
                TextField("Name", text: $newName)
                Button("Save") {
                    let name = newName.trimmingCharacters(in: .whitespaces)
                    if name.isEmpty || store.presetNameTaken(name) { say("Pick a new name") } else { store.savePreset(name); say("Saved “\(name)”") }
                    newName = ""
                }
                Button("Cancel", role: .cancel) { newName = "" }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .data]) { result in
                guard case .success(let url) = result else { return }
                let access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                do { say("Imported “\(try store.importPreset(Data(contentsOf: url)))”") } catch { say(error.localizedDescription) }
            }
            .fileExporter(isPresented: Binding(get: { exportDoc != nil }, set: { if !$0 { exportDoc = nil } }),
                          document: exportDoc, contentType: .json, defaultFilename: exportDoc?.name ?? "preset") { result in
                if case .success = result { say("Exported") }
            }
    }

    private func chip(_ title: String, _ value: String, _ on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.caption2).foregroundStyle(.secondary)
                Text(value).font(.subheadline.weight(.medium)).lineLimit(1)
            }
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(on ? Color.accentColor.opacity(0.22) : Color.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }
}

func pill(_ text: String, selected: Bool, action: @escaping () -> Void) -> some View {
    Button(action: action) {
        Text(text).font(.subheadline.weight(.medium))
            .padding(.horizontal, 14).padding(.vertical, 7)
            .background(selected ? Color.primary : Color.secondary.opacity(0.14), in: Capsule())
            .foregroundStyle(selected ? Color(.systemBackground) : .primary)
    }
    .buttonStyle(.plain)
}

struct PresetDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    var name: String
    init(data: Data, name: String) { self.data = data; self.name = name }
    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
        name = "preset"
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

/// The frequency response, with the live spectrum behind it.
private struct ResponseGraph: View {
    let settings: SelarasSettings
    let profiles: [String: SelarasProfile]
    let enabled: Bool
    let spectrum: SelarasSpectrum
    let showSpectrum: Bool
    private static let freqs = SelarasModel.logFreqs(160)

    var body: some View {
        let curve = SelarasModel.responseCurve(settings, profiles: profiles, freqs: Self.freqs)
        TimelineView(.animation(minimumInterval: 1.0 / 25, paused: !(enabled && showSpectrum))) { _ in
            Canvas { ctx, size in
                let span = 15.0
                func y(_ db: Double) -> CGFloat { size.height / 2 - CGFloat(min(max(db, -span), span) / span) * (size.height / 2 - 8) }
                func x(_ f: Double) -> CGFloat { CGFloat((log(f) - log(20)) / (log(20000) - log(20))) * size.width }
                if enabled && showSpectrum {
                    let levels = spectrum.levels
                    let w = size.width / CGFloat(levels.count)
                    for (i, db) in levels.enumerated() {
                        let h = CGFloat(min(max((db + 70) / 60, 0), 1)) * size.height
                        ctx.fill(Path(CGRect(x: CGFloat(i) * w + 1, y: size.height - h, width: w - 2, height: h)), with: .color(.primary.opacity(0.08)))
                    }
                }
                for f in [100.0, 1000, 10000] {
                    ctx.stroke(Path { $0.move(to: CGPoint(x: x(f), y: 0)); $0.addLine(to: CGPoint(x: x(f), y: size.height)) }, with: .color(.primary.opacity(0.07)))
                }
                ctx.stroke(Path { $0.move(to: CGPoint(x: 0, y: y(0))); $0.addLine(to: CGPoint(x: size.width, y: y(0))) }, with: .color(.primary.opacity(0.25)))
                var path = Path()
                for (i, db) in curve.enumerated() {
                    let p = CGPoint(x: x(Self.freqs[i]), y: y(db))
                    if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
                }
                ctx.stroke(path, with: .color(enabled ? .accentColor : .secondary), lineWidth: 2)
            }
        }
        .frame(height: 150)
        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        .overlay {
            if !enabled { Text("Equalizer is off").font(.caption).padding(8).background(.thinMaterial, in: Capsule()) }
        }
    }
}

/// The graphic EQ: one vertical slider per band.
private struct GraphicBands: View {
    let settings: SelarasSettings
    @EnvironmentObject var selaras: SelarasObservable

    var body: some View {
        let freqs = SelarasModel.bandFreqs(settings.shape)
        let bands = settings.shape.bands
        let dense = freqs.count > 12
        let content = HStack(spacing: dense ? 4 : 0) {
            ForEach(freqs.indices, id: \.self) { i in
                VStack(spacing: 6) {
                    Text(gain(bands[safe: i] ?? 0)).font(.caption2.monospacedDigit()).foregroundStyle(.secondary)
                    VerticalSlider(value: bands[safe: i] ?? 0, range: SelarasModel.gainLimit) { v in
                        selaras.store.editCustom { s in if i < s.shape.bands.count { s.shape.bands[i] = v } }
                    }
                    .frame(width: 28, height: 170)
                    Text(hz(freqs[i])).font(.caption2).foregroundStyle(.secondary).lineLimit(1).fixedSize()
                }
                .frame(maxWidth: dense ? nil : .infinity)
                .frame(width: dense ? 36 : nil)
            }
        }
        if dense { ScrollView(.horizontal, showsIndicators: false) { content } } else { content }
    }

    private func gain(_ v: Double) -> String { abs(v) < 0.05 ? "0" : String(format: "%+.1f", v).replacingOccurrences(of: ".0", with: "") }
}

/// A fader that snaps to half-decibels and resets on double tap.
private struct VerticalSlider: View {
    let value: Double
    let range: Double
    let onChange: (Double) -> Void

    var body: some View {
        GeometryReader { geo in
            let h = geo.size.height
            let knobY = h / 2 - CGFloat(value / range) * (h / 2 - 10)
            ZStack {
                Capsule().fill(Color.secondary.opacity(0.2)).frame(width: 4)
                Capsule().fill(Color.accentColor).frame(width: 4, height: abs(knobY - h / 2))
                    .position(x: geo.size.width / 2, y: (knobY + h / 2) / 2)
                Rectangle().fill(Color.secondary.opacity(0.4)).frame(width: 12, height: 1).position(x: geo.size.width / 2, y: h / 2)
                Circle().fill(Color.white).shadow(radius: 2).frame(width: 18, height: 18).position(x: geo.size.width / 2, y: knobY)
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { g in
                let fraction = min(max((g.location.y - 10) / (h - 20), 0), 1)
                var v = (1 - 2 * Double(fraction)) * range
                v = abs(v) < 0.4 ? 0 : (v * 2).rounded() / 2
                if abs(v - value) > 0.001 { onChange(v) }
            })
            .onTapGesture(count: 2) { onChange(0) }
        }
    }
}

private struct ParametricList: View {
    let settings: SelarasSettings
    let say: (String) -> Void
    @EnvironmentObject var selaras: SelarasObservable

    var body: some View {
        let store = selaras.store
        VStack(spacing: 10) {
            if settings.shape.parametric.isEmpty {
                Text("No bands yet — add one, or copy the graphic EQ").font(.subheadline).foregroundStyle(.secondary)
            }
            ForEach(Array(settings.shape.parametric.enumerated()), id: \.offset) { i, f in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Band \(i + 1)").font(.headline)
                        Menu(SelarasModel.typeNames[f.type] ?? f.type) {
                            ForEach(SelarasModel.parametricTypes, id: \.self) { t in
                                Button(SelarasModel.typeNames[t] ?? t) { update(i) { $0.type = t } }
                            }
                        }
                        Spacer()
                        Toggle("", isOn: Binding(get: { f.on }, set: { v in update(i) { $0.on = v } })).labelsHidden()
                        Button(role: .destructive) { store.editCustom { $0.shape.parametric.remove(at: i) } } label: { Image(systemName: "trash") }
                    }
                    stepper("Frequency", f.fc, "%.0f Hz", 10...22000, step: f.fc < 200 ? 1 : f.fc < 2000 ? 10 : 100) { v in update(i) { $0.fc = v } }
                    stepper("Gain", f.gain, "%+.1f dB", -15...15, step: 0.5) { v in update(i) { $0.gain = v } }
                        .disabled(["LP", "HP", "NO"].contains(f.type))
                    stepper("Q", f.q, "%.2f", 0.1...20, step: 0.05) { v in update(i) { $0.q = v } }
                }
                .padding(12)
                .background(Color.secondary.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
            }
            HStack {
                Button("Copy from graphic") { store.editCustom { SelarasModel.parametricFromGraphic(&$0) }; say("Copied the graphic bands") }
                Spacer()
                Button("+ Add band") {
                    if settings.shape.parametric.count >= SelarasModel.maxParametric { return say("Up to \(SelarasModel.maxParametric) bands") }
                    store.editCustom { $0.shape.parametric.append(SelarasFilterSpec()) }
                }
            }
            .font(.subheadline)
        }
    }

    private func update(_ i: Int, _ block: @escaping (inout SelarasFilterSpec) -> Void) {
        selaras.store.editCustom { s in if i < s.shape.parametric.count { block(&s.shape.parametric[i]) } }
    }

    private func stepper(_ title: String, _ value: Double, _ format: String, _ range: ClosedRange<Double>, step: Double, set: @escaping (Double) -> Void) -> some View {
        Stepper(value: Binding(get: { value }, set: { set(min(max($0, range.lowerBound), range.upperBound)) }), in: range, step: step) {
            HStack { Text(title); Spacer(); Text(String(format: format, value)).monospacedDigit().foregroundStyle(.secondary) }
        }
    }
}

// MARK: Tone & dynamics

private struct ToneTab: View {
    @EnvironmentObject var selaras: SelarasObservable

    var body: some View {
        let _ = selaras.revision
        let store = selaras.store
        let s = store.settings
        GroupBox("Tone") {
            slider("Bass", s.shape.bass, -12...12, "%+.1f dB") { v in store.editCustom { $0.shape.bass = v } }
            Picker("Bass shelf", selection: Binding(get: { s.shape.bassFreq }, set: { v in store.editCustom { $0.shape.bassFreq = v } })) {
                ForEach(SelarasModel.bassFreqs, id: \.self) { Text("\(Int($0)) Hz").tag($0) }
            }
            slider("Treble", s.shape.treble, -12...12, "%+.1f dB") { v in store.editCustom { $0.shape.treble = v } }
            Picker("Treble shelf", selection: Binding(get: { s.shape.trebleFreq }, set: { v in store.editCustom { $0.shape.trebleFreq = v } })) {
                ForEach(SelarasModel.trebleFreqs, id: \.self) { Text("\(Int($0 / 1000)) kHz").tag($0) }
            }
        }
        GroupBox("Gain & balance") {
            Toggle("Auto preamp", isOn: Binding(get: { s.autoPreamp }, set: { on in
                store.edit { state, st in
                    if !on {
                        let base = st.profile.flatMap { state.profiles[$0]?.preamp } ?? 0
                        st.preamp = ((SelarasModel.totalPreamp(st, profiles: state.profiles) - base) * 10).rounded() / 10
                    }
                    st.autoPreamp = on
                }
            }))
            slider("Preamp", s.autoPreamp ? SelarasModel.totalPreamp(s, profiles: store.state.profiles) : s.preamp, -24...12, "%+.1f dB") { v in
                store.edit { _, st in st.preamp = v }
            }
            .disabled(s.autoPreamp)
            slider("Balance", s.balance, -100...100, "%.0f") { v in store.edit { _, st in st.balance = abs(v) < 2 ? 0 : v } }
        }
        GroupBox("Dynamics") {
            Toggle("Limiter", isOn: Binding(get: { s.limiter.enabled }, set: { v in store.edit { _, st in st.limiter.enabled = v } }))
            slider("Ceiling", s.limiter.threshold, -12...0, "%.1f dB") { v in store.edit { _, st in st.limiter.threshold = v } }
                .disabled(!s.limiter.enabled)
            Toggle("Compressor", isOn: Binding(get: { s.compressor.enabled }, set: { v in store.edit { _, st in st.compressor.enabled = v } }))
            Picker("Strength", selection: Binding(get: { s.compressor.strength }, set: { v in store.edit { _, st in SelarasModel.setCompressorStrength(v, &st) } })) {
                Text("Light").tag(0); Text("Medium").tag(1); Text("Strong").tag(2)
            }
            .pickerStyle(.segmented)
            .disabled(!s.compressor.enabled)
            Text("The compressor evens out loud and quiet parts — good for night listening.").font(.caption).foregroundStyle(.secondary)
        }
    }

    private func slider(_ title: String, _ value: Double, _ range: ClosedRange<Double>, _ format: String, set: @escaping (Double) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack { Text(title); Spacer(); Text(String(format: format, value)).monospacedDigit().foregroundStyle(.secondary) }
            Slider(value: Binding(get: { value }, set: { set(($0 * 10).rounded() / 10) }), in: range)
        }
    }
}

// MARK: Headphones

private struct HeadphonesTab: View {
    let say: (String) -> Void
    @EnvironmentObject var selaras: SelarasObservable
    @State private var query = ""
    @State private var results: [SelarasModel.AutoEqEntry] = []
    @State private var status: String?
    @State private var busy = false
    @State private var importing = false

    var body: some View {
        let _ = selaras.revision
        let store = selaras.store
        let s = store.settings
        let profiles = store.state.profiles
        GroupBox {
            VStack(alignment: .leading, spacing: 8) {
                Text(s.profile ?? "No headphone profile").font(.headline)
                if let p = s.profile.flatMap({ profiles[$0] }) {
                    Text("\(p.filters.count) AutoEq filters · preamp \(String(format: "%.1f", p.preamp)) dB · applied under the EQ").font(.caption).foregroundStyle(.secondary)
                } else {
                    Text("Pick your headphones from the AutoEq database (8,800+ models).").font(.caption).foregroundStyle(.secondary)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        pill("None", selected: s.profile == nil) { store.edit { _, st in st.profile = nil } }
                        ForEach(profiles.keys.sorted(), id: \.self) { name in
                            pill(name, selected: name == s.profile) { store.edit { _, st in st.profile = name } }
                        }
                    }
                }
                HStack {
                    Button("Import ParametricEQ.txt…") { importing = true }
                    Spacer()
                    if let p = s.profile { Button("Remove", role: .destructive) { store.deleteProfile(p); say("Removed “\(p)”") } }
                }
                .font(.subheadline)
            }
        }
        HStack {
            TextField("Search headphones (e.g. Sony WH-1000XM5)", text: $query)
                .textFieldStyle(.roundedBorder)
                .submitLabel(.search)
                .onSubmit(search)
            Button("Search", action: search).disabled(busy || query.isEmpty)
        }
        if let status { Text(status).font(.caption).foregroundStyle(.secondary) }
        ForEach(results) { entry in
            Button {
                busy = true
                status = "Downloading \(entry.name)…"
                Task {
                    do {
                        try await store.installProfile(entry)
                        say("Using “\(entry.profileName)”")
                        status = nil
                        results = []
                        query = ""
                    } catch {
                        status = "Download failed: \(error.localizedDescription)"
                    }
                    busy = false
                }
            } label: {
                HStack {
                    Text(entry.name).lineLimit(1)
                    Spacer()
                    Text(entry.source.components(separatedBy: " on ").first ?? "").font(.caption).foregroundStyle(.secondary)
                }
            }
            .disabled(busy)
            Divider()
        }
        Text("A headphone profile corrects your headphones' frequency response. It is applied under your EQ, so presets still work on top of it.")
            .font(.caption).foregroundStyle(.secondary)
            .fileImporter(isPresented: $importing, allowedContentTypes: [.plainText, .text, .data]) { result in
                guard case .success(let url) = result else { return }
                let access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                do {
                    let text = try String(contentsOf: url, encoding: .utf8)
                    say("Imported “\(try store.importProfile(fileName: url.lastPathComponent, text: text))”")
                } catch { say(error.localizedDescription) }
            }
    }

    private func search() {
        guard !busy, !query.isEmpty else { return }
        busy = true
        status = "Searching AutoEq…"
        Task {
            do {
                results = SelarasModel.searchAutoEq(try await selaras.store.autoEqIndex(), query)
                status = results.isEmpty ? "No headphones match “\(query)”" : nil
            } catch {
                status = "Couldn't load the AutoEq list: \(error.localizedDescription)"
            }
            busy = false
        }
    }
}

// MARK: Devices

private struct DevicesTab: View {
    @EnvironmentObject var selaras: SelarasObservable

    var body: some View {
        let _ = selaras.revision
        let store = selaras.store
        let state = store.state
        Toggle("Per-device EQ", isOn: Binding(get: { state.options.perDevice }, set: { v in store.editState { $0.options.perDevice = v } }))
        Text("Each output — iPhone speaker, wired, AirPods and other Bluetooth, AirPlay — keeps its own EQ.").font(.caption).foregroundStyle(.secondary)
        Toggle("Spectrum in the graph", isOn: Binding(get: { state.options.spectrum }, set: { v in store.editState { $0.options.spectrum = v } }))
        Text("Remembered devices").font(.headline).padding(.top, 8)
        if state.devices.isEmpty { Text("None yet. An output is remembered the first time you adjust its EQ.").font(.caption).foregroundStyle(.secondary) }
        ForEach(state.devices.keys.sorted(), id: \.self) { key in
            HStack {
                VStack(alignment: .leading) {
                    Text(state.deviceNames[key] ?? key)
                    Text((key == store.deviceKey ? "Active · " : "") + "Preset \(state.devices[key]?.preset ?? "-")").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Button("Forget", role: .destructive) { store.editState { $0.devices[key] = nil } }
            }
            Divider()
        }
    }
}

extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
