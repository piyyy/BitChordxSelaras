import BitChordCore
import SwiftUI

/// The full-screen player: artwork, transport, and three panes under it — lyrics, queue, Selaras.
struct NowPlayingView: View {
    @EnvironmentObject var player: PlayerModel
    @State private var pane: Pane? = nil
    @State private var scrubbing: Double?

    enum Pane: String { case lyrics, queue, equalizer }

    var body: some View {
        if let song = player.current {
            VStack(spacing: 18) {
                if let pane {
                    HStack(spacing: 12) {
                        ArtworkView(url: song.thumbnailUrl, size: 56, corner: 6)
                        titles(song)
                    }
                    .padding(.top, 24)
                    Group {
                        switch pane {
                        case .lyrics: LyricsPane()
                        case .queue: QueuePane()
                        case .equalizer: SelarasView(compactHeader: true)
                        }
                    }
                    .frame(maxHeight: .infinity)
                } else {
                    Spacer(minLength: 12)
                    ArtworkView(url: song.thumbnailUrl, size: min(UIScreen.main.bounds.width - 48, 360), corner: 14)
                        .shadow(radius: 20, y: 8)
                        .scaleEffect(player.isPlaying ? 1 : 0.9)
                        .animation(.spring(duration: 0.4), value: player.isPlaying)
                    Spacer(minLength: 12)
                    titles(song)
                }
                progress
                transport
                HStack {
                    paneButton(.lyrics, "quote.bubble")
                    Spacer()
                    Text(player.outputName).font(.caption).foregroundStyle(.secondary)
                    Spacer()
                    paneButton(.equalizer, "slider.vertical.3")
                    paneButton(.queue, "list.bullet")
                }
                .padding(.bottom, 8)
            }
            .padding(.horizontal, 24)
            .background {
                ArtworkView(url: song.thumbnailUrl, size: 600, corner: 0)
                    .blur(radius: 60).opacity(0.45).ignoresSafeArea()
                    .overlay(Color(.systemBackground).opacity(0.55).ignoresSafeArea())
            }
        } else {
            Text("Nothing playing").foregroundStyle(.secondary)
        }
    }

    private func titles(_ song: Song) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(song.title).font(.title3.bold()).lineLimit(1)
                Text(song.artist).font(.body).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Menu {
                SongMenuItems(song: song)
            } label: {
                Image(systemName: "ellipsis.circle.fill").font(.title2).symbolRenderingMode(.hierarchical)
            }
        }
    }

    private var progress: some View {
        VStack(spacing: 4) {
            Slider(
                value: Binding(get: { scrubbing ?? player.currentTime }, set: { scrubbing = $0 }),
                in: 0...max(player.duration, 1),
                onEditingChanged: { editing in
                    if !editing, let t = scrubbing { player.seek(to: t); scrubbing = nil }
                }
            )
            HStack {
                Text(format(scrubbing ?? player.currentTime))
                Spacer()
                Text("-" + format(max(player.duration - (scrubbing ?? player.currentTime), 0)))
            }
            .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
        }
    }

    private var transport: some View {
        HStack {
            Button { player.toggleShuffle() } label: {
                Image(systemName: "shuffle").foregroundStyle(player.shuffle ? Color.accentColor : .secondary)
            }
            Spacer()
            Button { player.previous() } label: { Image(systemName: "backward.fill").font(.title) }
            Spacer()
            Button { player.togglePlay() } label: {
                ZStack {
                    if player.isLoading { ProgressView().controlSize(.large) } else {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.system(size: 44))
                    }
                }
                .frame(width: 64, height: 64)
            }
            Spacer()
            Button { player.next() } label: { Image(systemName: "forward.fill").font(.title) }
            Spacer()
            Button { player.cycleRepeat() } label: {
                Image(systemName: player.repeatMode == .one ? "repeat.1" : "repeat")
                    .foregroundStyle(player.repeatMode == .off ? .secondary : Color.accentColor)
            }
        }
        .foregroundStyle(.primary)
    }

    private func paneButton(_ p: Pane, _ icon: String) -> some View {
        Button { withAnimation(.spring(duration: 0.35)) { pane = pane == p ? nil : p } } label: {
            Image(systemName: icon).font(.title3).padding(8)
                .background(pane == p ? Color.secondary.opacity(0.25) : .clear, in: Circle())
        }
        .foregroundStyle(.primary)
    }

    private func format(_ t: TimeInterval) -> String {
        guard t.isFinite else { return "0:00" }
        let s = Int(t)
        return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String(format: "%d:%02d", s / 60, s % 60)
    }
}

/// Synced lyrics follow the song and can be tapped to seek; plain lyrics just scroll.
struct LyricsPane: View {
    @EnvironmentObject var player: PlayerModel

    var body: some View {
        if let lyrics = player.lyrics {
            if lyrics.isSynced {
                let active = lyrics.lineIndex(at: player.currentTime + 0.25)
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 18) {
                            ForEach(Array(lyrics.synced.enumerated()), id: \.offset) { i, line in
                                Text(line.text.isEmpty ? "♪" : line.text)
                                    .font(.title2.bold())
                                    .foregroundStyle(i == active ? Color.primary : Color.secondary.opacity(0.6))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .id(i)
                                    .onTapGesture { player.seek(to: line.time) }
                            }
                            Text("Lyrics: \(lyrics.source)").font(.caption).foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 40)
                    }
                    .onChange(of: active) { _, i in
                        if let i { withAnimation(.easeInOut) { proxy.scrollTo(i, anchor: .center) } }
                    }
                }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(lyrics.plain).font(.title3.weight(.semibold))
                        Text("Lyrics: \(lyrics.source)").font(.caption).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        } else {
            ContentUnavailableView("No lyrics", systemImage: "quote.bubble", description: Text("Looking for lyrics, or none were found for this song."))
        }
    }
}

struct QueuePane: View {
    @EnvironmentObject var player: PlayerModel

    var body: some View {
        List {
            Section {
                Toggle("AutoPlay similar music", isOn: $player.autoplay)
            }
            Section("Up next") {
                ForEach(player.upNext) { song in
                    SongRow(song: song) { player.jump(to: song) }
                }
                .onDelete { player.remove(at: $0) }
                .onMove { player.move(from: $0, to: $1) }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.editMode, .constant(.active))
    }
}
