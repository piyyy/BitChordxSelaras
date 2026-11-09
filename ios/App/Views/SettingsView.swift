import BitChordCore
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var auth: AuthModel
    @EnvironmentObject var player: PlayerModel
    @Environment(\.dismiss) private var dismiss
    @State private var showSignIn = false
    @AppStorage("contentLanguage") private var language = Locale.current.language.languageCode?.identifier ?? "en"
    @AppStorage("contentRegion") private var region = Locale.current.region?.identifier ?? "US"

    var body: some View {
        NavigationStack {
            Form {
                Section("Account") {
                    if auth.signedIn {
                        HStack {
                            ArtworkView(url: auth.account?.thumbnailUrl, size: 44, circle: true)
                            VStack(alignment: .leading) {
                                Text(auth.account?.name ?? "Signed in")
                                if let email = auth.account?.email { Text(email).font(.caption).foregroundStyle(.secondary) }
                            }
                        }
                        Button("Sign out", role: .destructive) { Task { await auth.signOut() } }
                    } else {
                        Button("Sign in to YouTube Music") { showSignIn = true }
                    }
                }
                Section("Playback") {
                    Toggle("AutoPlay similar music", isOn: $player.autoplay)
                    NavigationLink("Equalizer · Selaras") { SelarasView().padding(.horizontal).navigationTitle("Equalizer") }
                }
                Section("Content") {
                    TextField("Language (e.g. en, id)", text: $language)
                    TextField("Region (e.g. US, ID)", text: $region)
                }
                Section("About") {
                    LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-")
                    Link("BitChord on GitHub", destination: URL(string: "https://github.com/kushagrasinghx/BitChord")!)
                    Text("BitChord is free software under the GNU GPL v3. Not affiliated with YouTube or Google.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Settings")
            .toolbar { Button("Done") { dismiss() } }
            .onChange(of: language) { _, _ in applyLocale() }
            .onChange(of: region) { _, _ in applyLocale() }
            .sheet(isPresented: $showSignIn) {
                NavigationStack {
                    SignInView { showSignIn = false }
                        .navigationTitle("Sign in").navigationBarTitleDisplayMode(.inline)
                        .toolbar { Button("Cancel") { showSignIn = false } }
                }
            }
        }
    }

    private func applyLocale() {
        let lang = language.trimmingCharacters(in: .whitespaces)
        let reg = region.trimmingCharacters(in: .whitespaces).uppercased()
        guard !lang.isEmpty, reg.count == 2 else { return }
        Task { await Innertube.shared.setLocale(language: lang, region: reg) }
    }
}
