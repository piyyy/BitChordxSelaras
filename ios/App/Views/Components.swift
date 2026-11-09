import BitChordCore
import SwiftUI

/// Where a tap on a card or a credit goes.
enum Route: Hashable {
    case page(browseId: String, title: String)
    case artist(browseId: String, name: String)
    case shelf(HomeShelf)
    case moodGenre(MoodGenre)
}

extension ShelfItem {
    var route: Route? {
        guard let id = browseId else { return nil }
        return browseType == .artist ? .artist(browseId: id, name: title) : .page(browseId: id, title: title)
    }
}

extension BrowseItem {
    var route: Route {
        type == .artist ? .artist(browseId: browseId, name: title) : .page(browseId: browseId, title: title)
    }
}

struct ArtworkView: View {
    let url: String?
    var size: CGFloat = 52
    var corner: CGFloat = 6
    var circle = false

    var body: some View {
        AsyncImage(url: Artwork.url(url, size: Int(size * 3))) { phase in
            if let image = phase.image {
                image.resizable().scaledToFill()
            } else {
                Rectangle().fill(Color.secondary.opacity(0.15))
                    .overlay(Image(systemName: "music.note").foregroundStyle(.secondary))
            }
        }
        .frame(width: size, height: size)
        .clipShape(circle ? AnyShape(Circle()) : AnyShape(RoundedRectangle(cornerRadius: corner, style: .continuous)))
    }
}

/// One track in a list, with its long-press menu.
struct SongRow: View {
    let song: Song
    var showArtwork = true
    var index: Int?
    let onPlay: () -> Void
    @EnvironmentObject var player: PlayerModel

    var body: some View {
        Button(action: onPlay) {
            HStack(spacing: 12) {
                if let index {
                    Text("\(index)").font(.subheadline.monospacedDigit()).foregroundStyle(.secondary).frame(width: 24)
                } else if showArtwork {
                    ArtworkView(url: song.thumbnailUrl, size: 48)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(song.title).font(.body).lineLimit(1)
                        .foregroundStyle(player.current?.videoId == song.videoId ? Color.accentColor : .primary)
                    HStack(spacing: 4) {
                        if song.isExplicit { Image(systemName: "e.square.fill").font(.caption2) }
                        Text(song.artist).lineLimit(1)
                    }
                    .font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                if let d = song.durationText { Text(d).font(.caption.monospacedDigit()).foregroundStyle(.secondary) }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .contextMenu { SongMenuItems(song: song) }
    }
}

struct SongMenuItems: View {
    let song: Song
    @EnvironmentObject var player: PlayerModel
    @EnvironmentObject var auth: AuthModel

    var body: some View {
        Button { player.playNext(song) } label: { Label("Play next", systemImage: "text.insert") }
        Button { player.addToQueue([song]) } label: { Label("Add to queue", systemImage: "text.append") }
        Button { player.playRadio(song) } label: { Label("Start radio", systemImage: "dot.radiowaves.left.and.right") }
        if auth.signedIn {
            Button { Task { try? await Innertube.shared.rate(videoId: song.videoId, status: .like) } } label: {
                Label("Like", systemImage: "hand.thumbsup")
            }
        }
        if let id = song.artistId {
            NavigationLink(value: Route.artist(browseId: id, name: song.artist)) { Label("Go to artist", systemImage: "person") }
        }
        if let id = song.albumId {
            NavigationLink(value: Route.page(browseId: id, title: song.albumName ?? "Album")) { Label("Go to album", systemImage: "square.stack") }
        }
        ShareLink(item: URL(string: "https://music.youtube.com/watch?v=\(song.videoId)")!) { Label("Share", systemImage: "square.and.arrow.up") }
    }
}

/// A horizontal shelf of cards, as Home and artist pages draw them.
struct ShelfView: View {
    let shelf: HomeShelf
    @EnvironmentObject var player: PlayerModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    if !shelf.strapline.isEmpty { Text(shelf.strapline.uppercased()).font(.caption).foregroundStyle(.secondary) }
                    Text(shelf.title).font(.title3.bold())
                }
                Spacer()
                if shelf.moreBrowseId != nil {
                    NavigationLink(value: Route.shelf(shelf)) { Text("See all").font(.subheadline) }
                }
            }
            .padding(.horizontal)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 14) {
                    ForEach(shelf.items) { item in ShelfCard(item: item, siblings: shelf.items) }
                }
                .padding(.horizontal)
            }
        }
    }
}

struct ShelfCard: View {
    let item: ShelfItem
    var siblings: [ShelfItem] = []
    @EnvironmentObject var player: PlayerModel

    var body: some View {
        let card = VStack(alignment: .leading, spacing: 6) {
            ArtworkView(url: item.thumbnailUrl, size: 150, corner: 8, circle: item.browseType == .artist)
            Text(item.title).font(.subheadline.weight(.medium)).lineLimit(2)
            Text(item.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(width: 150, alignment: .leading)

        if let route = item.route {
            NavigationLink(value: route) { card }.buttonStyle(.plain)
        } else if let song = item.asSong {
            Button {
                let songs = siblings.compactMap(\.asSong)
                player.play(songs, startAt: songs.firstIndex(of: song) ?? 0)
            } label: { card }
            .buttonStyle(.plain)
            .contextMenu { SongMenuItems(song: song) }
        } else {
            card
        }
    }
}

/// Loading / error / content, for screens that fetch once.
struct Loadable<Value, Content: View>: View {
    let load: () async throws -> Value
    @ViewBuilder let content: (Value) -> Content
    @State private var value: Value?
    @State private var error: String?

    var body: some View {
        Group {
            if let value {
                content(value)
            } else if let error {
                ContentUnavailableView {
                    Label("Couldn't load", systemImage: "wifi.exclamationmark")
                } description: { Text(error) } actions: {
                    Button("Try again") { Task { await fetch() } }
                }
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task { if value == nil { await fetch() } }
        .refreshable { await fetch() }
    }

    private func fetch() async {
        do {
            value = try await load()
            error = nil
        } catch {
            if value == nil { self.error = error.localizedDescription }
        }
    }
}

extension View {
    /// The pages every tab can open.
    func bitchordDestinations() -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .page(let id, let title): BrowsePageView(browseId: id, title: title)
            case .artist(let id, let name): ArtistView(browseId: id, name: name)
            case .shelf(let shelf): ShelfPageView(shelf: shelf)
            case .moodGenre(let mood): MoodGenreView(mood: mood)
            }
        }
    }
}
