import BitChordCore
import SwiftUI

@main
struct BitChordApp: App {
    @StateObject private var player = PlayerModel.shared
    @StateObject private var auth = AuthModel.shared
    @StateObject private var selaras = SelarasObservable()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(player)
                .environmentObject(auth)
                .environmentObject(selaras)
                .tint(Color(red: 0.98, green: 0.18, blue: 0.28))
                .onOpenURL { url in MusicLink.open(url, player: player) }
        }
    }
}

/// Bridges `SelarasStore` (BitChordCore, framework-free) to SwiftUI.
@MainActor
final class SelarasObservable: ObservableObject {
    let store = SelarasStore.shared
    @Published private(set) var revision = 0

    init() {
        store.onChange = { [weak self] in self?.revision += 1 }
    }

    var state: SelarasState { store.state }
    var settings: SelarasSettings { store.settings }
}

/// music.youtube.com and youtu.be links opened from outside the app.
enum MusicLink {
    @MainActor
    static func open(_ url: URL, player: PlayerModel) {
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let videoId = items.first { $0.name == "v" }?.value ?? (url.host == "youtu.be" ? url.lastPathComponent : nil)
        guard let videoId, videoId.count == 11 else { return }
        Task {
            if let queue = try? await YouTubeMusic.radio(videoId), let first = queue.first {
                player.play(queue, startAt: queue.firstIndex { $0.videoId == videoId } ?? 0)
                _ = first
            }
        }
    }
}

struct RootView: View {
    @EnvironmentObject var player: PlayerModel
    @State private var tab = UserDefaults.standard.integer(forKey: "debugTab")
    @State private var showPlayer = false

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { HomeView().bitchordDestinations() }.miniPlayer($showPlayer)
                .tabItem { Label("Home", systemImage: "house.fill") }.tag(0)
            NavigationStack { ExploreView().bitchordDestinations() }.miniPlayer($showPlayer)
                .tabItem { Label("Explore", systemImage: "square.grid.2x2.fill") }.tag(1)
            NavigationStack { LibraryView().bitchordDestinations() }.miniPlayer($showPlayer)
                .tabItem { Label("Library", systemImage: "music.note.list") }.tag(2)
            NavigationStack { SearchView().bitchordDestinations() }.miniPlayer($showPlayer)
                .tabItem { Label("Search", systemImage: "magnifyingglass") }.tag(3)
        }
        .sheet(isPresented: $showPlayer) {
            NowPlayingView()
                .presentationDragIndicator(.visible)
        }
        .task {
            // The content language and region chosen in Settings, before the first request.
            let d = UserDefaults.standard
            if let lang = d.string(forKey: "contentLanguage"), let reg = d.string(forKey: "contentRegion"), !lang.isEmpty, reg.count == 2 {
                await Innertube.shared.setLocale(language: lang, region: reg.uppercased())
            }
        }
        .alert("Playback error", isPresented: Binding(get: { player.error != nil }, set: { if !$0 { player.error = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(player.error ?? "") }
    }
}

private struct MiniPlayerInset: ViewModifier {
    @EnvironmentObject var player: PlayerModel
    @Binding var showPlayer: Bool

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) {
            if player.current != nil { MiniPlayer { showPlayer = true } }
        }
    }
}

extension View {
    /// The mini player above the tab bar; scrolling content is inset to clear it.
    func miniPlayer(_ show: Binding<Bool>) -> some View { modifier(MiniPlayerInset(showPlayer: show)) }
}

struct MiniPlayer: View {
    @EnvironmentObject var player: PlayerModel
    let onOpen: () -> Void

    var body: some View {
        if let song = player.current {
            HStack(spacing: 12) {
                ArtworkView(url: song.thumbnailUrl, size: 44)
                VStack(alignment: .leading, spacing: 1) {
                    Text(song.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text(song.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer()
                if player.isLoading {
                    ProgressView()
                } else {
                    Button { player.togglePlay() } label: {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.title2)
                    }
                }
                Button { player.next() } label: { Image(systemName: "forward.fill").font(.title3) }
            }
            .foregroundStyle(.primary)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(alignment: .bottom) {
                GeometryReader { geo in
                    Capsule().fill(Color.accentColor)
                        .frame(width: player.duration > 0 ? geo.size.width * player.currentTime / player.duration : 0, height: 2)
                }
                .frame(height: 2)
                .padding(.horizontal, 14)
            }
            .padding(.horizontal, 8)
            .padding(.bottom, 4)
            .contentShape(Rectangle())
            .onTapGesture(perform: onOpen)
        }
    }
}
