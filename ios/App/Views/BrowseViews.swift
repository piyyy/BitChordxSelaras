import BitChordCore
import SwiftUI

// MARK: Home

struct HomeView: View {
    @State private var shelves: [HomeShelf] = []
    @State private var continuation: String?
    @State private var loading = false
    @State private var error: Error?
    @State private var showSettings = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 28) {
                ForEach(shelves) { shelf in ShelfView(shelf: shelf) }
                if continuation != nil {
                    ProgressView().frame(maxWidth: .infinity).onAppear { Task { await more() } }
                }
            }
            .padding(.vertical)
        }
        .overlay {
            if shelves.isEmpty {
                if let error {
                    LoadErrorView(error: error, title: "Couldn't load Home") { Task { await reload() } }
                } else {
                    ProgressView()
                }
            }
        }
        .navigationTitle("Home")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { showSettings = true } label: { Image(systemName: "gearshape") }
            }
        }
        .sheet(isPresented: $showSettings) { SettingsView() }
        .task { if shelves.isEmpty { await reload() } }
        .refreshable { await reload() }
    }

    private func reload() async {
        do {
            let page = try await YouTubeMusic.home()
            shelves = page.shelves
            continuation = page.continuation
            error = nil
        } catch is CancellationError {
        } catch let e as URLError where e.code == .cancelled {
        } catch {
            self.error = error
        }
    }

    private func more() async {
        guard !loading, let token = continuation else { return }
        loading = true
        defer { loading = false }
        if let page = try? await YouTubeMusic.moreHome(token) {
            shelves += page.shelves
            continuation = page.continuation
        } else {
            continuation = nil
        }
    }
}

// MARK: Explore

struct ExploreView: View {
    var body: some View {
        Loadable(load: {
            async let releases = YouTubeMusic.shelves("FEmusic_new_releases")
            async let charts = YouTubeMusic.shelves("FEmusic_charts")
            async let moods = YouTubeMusic.moodsAndGenres()
            return (try await releases, (try? await charts) ?? [], (try? await moods) ?? [])
        }) { (value: ([HomeShelf], [HomeShelf], [MoodGenreSection])) in
            let (releases, charts, moods) = value
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 28) {
                    ForEach(releases + charts) { ShelfView(shelf: $0) }
                    ForEach(moods) { section in
                        VStack(alignment: .leading, spacing: 10) {
                            Text(section.title).font(.title3.bold()).padding(.horizontal)
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
                                ForEach(section.items) { mood in
                                    NavigationLink(value: Route.moodGenre(mood)) {
                                        HStack(spacing: 0) {
                                            Rectangle().fill(color(mood.stripeColor)).frame(width: 6)
                                            Text(mood.title).font(.subheadline.weight(.semibold)).lineLimit(2)
                                                .padding(.horizontal, 10)
                                            Spacer(minLength: 0)
                                        }
                                        .frame(height: 48)
                                        .background(Color.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal)
                        }
                    }
                }
                .padding(.vertical)
            }
        }
        .navigationTitle("Explore")
    }

    private func color(_ argb: UInt32?) -> Color {
        guard let v = argb else { return .accentColor }
        return Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
}

struct MoodGenreView: View {
    let mood: MoodGenre
    var body: some View {
        Loadable(load: { try await YouTubeMusic.shelves(mood.browseId, params: mood.params) }) { shelves in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 28) { ForEach(shelves) { ShelfView(shelf: $0) } }.padding(.vertical)
            }
        }
        .navigationTitle(mood.title)
    }
}

/// A shelf's "See all".
struct ShelfPageView: View {
    let shelf: HomeShelf
    var body: some View {
        Group {
            if let id = shelf.moreBrowseId {
                Loadable(load: {
                    let response = try await Innertube.shared.browse(id, params: shelf.moreParams)
                    let items = InnertubeParser.parseLibraryItems(response).items
                    return items.isEmpty ? shelf.items : items
                }) { items in grid(items) }
            } else {
                grid(shelf.items)
            }
        }
        .navigationTitle(shelf.title)
    }

    private func grid(_ items: [ShelfItem]) -> some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 14)], spacing: 18) {
                ForEach(items) { ShelfCard(item: $0, siblings: items) }
            }
            .padding()
        }
    }
}

// MARK: Album / playlist

struct BrowsePageView: View {
    let browseId: String
    let title: String
    @EnvironmentObject var player: PlayerModel
    @EnvironmentObject var auth: AuthModel
    @State private var page: BrowsePage?
    @State private var error: String?
    @State private var loadingMore = false
    @State private var saved: Bool?

    var body: some View {
        List {
            if let page {
                Section {
                    VStack(spacing: 12) {
                        ArtworkView(url: page.header?.thumbnailUrl ?? page.songs.first?.thumbnailUrl, size: 220, corner: 12)
                        Text(page.header?.title ?? title).font(.title2.bold()).multilineTextAlignment(.center)
                        if let sub = page.header?.subtitle, !sub.isEmpty {
                            Text(sub).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        }
                        HStack(spacing: 12) {
                            Button { player.play(page.songs) } label: { Label("Play", systemImage: "play.fill").frame(maxWidth: .infinity) }
                                .buttonStyle(.borderedProminent)
                            Button {
                                let shuffled = page.songs.shuffled()
                                player.play(shuffled)
                            } label: { Label("Shuffle", systemImage: "shuffle").frame(maxWidth: .infinity) }
                                .buttonStyle(.bordered)
                        }
                        .disabled(page.songs.isEmpty)
                        if let description = page.description {
                            Text(description).font(.footnote).foregroundStyle(.secondary).lineLimit(4)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .listRowSeparator(.hidden)
                }
                Section {
                    ForEach(Array(page.songs.enumerated()), id: \.element.id) { i, song in
                        SongRow(song: song, showArtwork: !browseId.hasPrefix("MPRE"), index: browseId.hasPrefix("MPRE") ? i + 1 : nil) {
                            player.play(page.songs, startAt: i)
                        }
                    }
                    if page.continuation != nil {
                        ProgressView().frame(maxWidth: .infinity).onAppear { Task { await more() } }
                    }
                }
                if !page.suggested.isEmpty {
                    Section("Suggestions") {
                        ForEach(page.suggested) { song in SongRow(song: song) { player.play([song], radio: true) } }
                    }
                }
                ForEach(page.shelves) { shelf in
                    Section { ShelfView(shelf: shelf).listRowInsets(EdgeInsets()) }
                }
            } else if let error {
                ContentUnavailableView("Couldn't load", systemImage: "exclamationmark.triangle", description: Text(error))
            } else {
                ProgressView().frame(maxWidth: .infinity)
            }
        }
        .listStyle(.plain)
        .navigationTitle(page?.header?.title ?? title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if auth.signedIn, let id = page?.libraryPlaylistId, let saved {
                Button {
                    Task {
                        try? await Innertube.shared.ratePlaylist(playlistId: id, saved: !saved)
                        self.saved = !saved
                    }
                } label: { Image(systemName: saved ? "bookmark.fill" : "bookmark") }
            }
        }
        .task {
            guard page == nil else { return }
            do {
                page = try await YouTubeMusic.page(browseId)
                saved = page?.saved
            } catch { self.error = error.localizedDescription }
        }
    }

    private func more() async {
        guard !loadingMore, let token = page?.continuation else { return }
        loadingMore = true
        defer { loadingMore = false }
        if let next = try? await YouTubeMusic.morePage(token) {
            let known = Set(page?.songs.map(\.videoId) ?? [])
            page?.songs += next.songs.filter { !known.contains($0.videoId) }
            page?.continuation = next.continuation
        } else {
            page?.continuation = nil
        }
    }
}

// MARK: Artist

struct ArtistView: View {
    let browseId: String
    let name: String
    @EnvironmentObject var player: PlayerModel
    @EnvironmentObject var auth: AuthModel
    @State private var subscribed: Bool?

    var body: some View {
        Loadable(load: { try await YouTubeMusic.artist(browseId) }) { page in
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(spacing: 10) {
                        ArtworkView(url: page.thumbnailUrl, size: 180, circle: true)
                        Text(page.name ?? name).font(.largeTitle.bold()).multilineTextAlignment(.center)
                        if let listeners = page.monthlyListenerCount ?? page.subscriberCountText {
                            Text(listeners).font(.subheadline).foregroundStyle(.secondary)
                        }
                        HStack {
                            Button { player.play(page.songs) } label: { Label("Play", systemImage: "play.fill") }
                                .buttonStyle(.borderedProminent)
                            if let first = page.songs.first {
                                Button { player.playRadio(first) } label: { Label("Radio", systemImage: "dot.radiowaves.left.and.right") }
                                    .buttonStyle(.bordered)
                            }
                            if auth.signedIn, let sub = page.subscription {
                                let on = subscribed ?? sub.subscribed
                                Button {
                                    Task {
                                        try? await Innertube.shared.setSubscribed(channelId: sub.channelId, subscribed: !on)
                                        subscribed = !on
                                    }
                                } label: { Text(on ? "Subscribed" : "Subscribe") }
                                    .buttonStyle(.bordered)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)

                    if !page.songs.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text("Top songs").font(.title3.bold())
                                Spacer()
                                if let more = page.moreSongsBrowseId {
                                    NavigationLink(value: Route.page(browseId: more, title: "\(page.name ?? name) · Songs")) { Text("See all") }
                                }
                            }
                            ForEach(Array(page.songs.enumerated()), id: \.element.id) { i, song in
                                SongRow(song: song) { player.play(page.songs, startAt: i) }
                                Divider()
                            }
                        }
                        .padding(.horizontal)
                    }
                    ForEach(page.shelves) { ShelfView(shelf: $0) }
                    if let about = page.description {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("About").font(.title3.bold())
                            Text(about).font(.callout).foregroundStyle(.secondary)
                        }
                        .padding(.horizontal)
                    }
                }
                .padding(.vertical)
            }
        }
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: Search

struct SearchView: View {
    @EnvironmentObject var player: PlayerModel
    @State private var query = ""
    @State private var submitted = ""
    @State private var filter: SearchFilter = .all
    @State private var results: [SearchResult] = []
    @State private var continuation: String?
    @State private var suggestions: [String] = []
    @State private var loading = false
    @State private var error: String?
    @AppStorage("searchHistory") private var historyRaw = ""

    private var history: [String] { historyRaw.split(separator: "\n").map(String.init) }

    var body: some View {
        List {
            if submitted.isEmpty {
                if !suggestions.isEmpty {
                    Section {
                        ForEach(suggestions, id: \.self) { s in
                            Button { submit(s) } label: { Label(s, systemImage: "magnifyingglass") }.foregroundStyle(.primary)
                        }
                    }
                } else if !history.isEmpty {
                    Section {
                        ForEach(history, id: \.self) { s in
                            Button { submit(s) } label: { Label(s, systemImage: "clock.arrow.circlepath") }.foregroundStyle(.primary)
                        }
                        Button("Clear history", role: .destructive) { historyRaw = "" }
                    } header: { Text("Recent searches") }
                }
            } else {
                Section {
                    Picker("Filter", selection: $filter) {
                        ForEach(SearchFilter.allCases) { Text($0.title).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .listRowSeparator(.hidden)
                }
                if loading && results.isEmpty {
                    ProgressView().frame(maxWidth: .infinity)
                } else if let error, results.isEmpty {
                    Text(error).foregroundStyle(.secondary)
                } else if results.isEmpty {
                    Text("No results for “\(submitted)”").foregroundStyle(.secondary)
                }
                ForEach(results) { result in row(result) }
                if continuation != nil {
                    ProgressView().frame(maxWidth: .infinity).onAppear { Task { await more() } }
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle("Search")
        .searchable(text: $query, prompt: "Songs, artists, albums")
        .onSubmit(of: .search) { submit(query) }
        .onChange(of: query) { _, value in
            if value.isEmpty { submitted = ""; results = [] }
            Task { await suggest(value) }
        }
        .onChange(of: filter) { _, _ in Task { await run() } }
    }

    @ViewBuilder
    private func row(_ result: SearchResult) -> some View {
        switch result {
        case .track(let song), .topTrack(let song):
            SongRow(song: song) {
                let songs = results.compactMap { if case .track(let s) = $0 { return s }; if case .topTrack(let s) = $0 { return s }; return nil }
                if filter == .songs { player.play(songs, startAt: songs.firstIndex(of: song) ?? 0) } else { player.playRadio(song) }
            }
        case .browse(let item):
            NavigationLink(value: item.route) {
                HStack(spacing: 12) {
                    ArtworkView(url: item.thumbnailUrl, size: 48, circle: item.type == .artist)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title).lineLimit(1)
                        Text(item.subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
            }
        }
    }

    private func submit(_ text: String) {
        let q = text.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        query = q
        submitted = q
        suggestions = []
        historyRaw = ([q] + history.filter { $0 != q }).prefix(20).joined(separator: "\n")
        Task { await run() }
    }

    private func run() async {
        guard !submitted.isEmpty else { return }
        loading = true
        error = nil
        results = []
        defer { loading = false }
        do {
            let page = try await YouTubeMusic.search(submitted, filter: filter)
            results = page.rows
            continuation = page.continuation
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func more() async {
        guard let token = continuation, !loading else { return }
        loading = true
        defer { loading = false }
        if let page = try? await YouTubeMusic.moreSearch(token, filter: filter) {
            let known = Set(results.map(\.id))
            results += page.rows.filter { !known.contains($0.id) }
            continuation = page.continuation
        } else {
            continuation = nil
        }
    }

    private func suggest(_ text: String) async {
        guard submitted.isEmpty || text != submitted, text.count >= 2 else { suggestions = []; return }
        try? await Task.sleep(nanoseconds: 250_000_000)
        guard text == query else { return }
        suggestions = (try? await YouTubeMusic.suggestions(text)) ?? []
        if text != submitted { submitted = "" }
    }
}

// MARK: Library

struct LibraryView: View {
    @EnvironmentObject var auth: AuthModel
    @State private var showSignIn = false

    var body: some View {
        Group {
            if auth.signedIn {
                List {
                    Section {
                        NavigationLink(value: Route.page(browseId: YouTubeMusic.likedMusic, title: "Liked Music")) {
                            Label("Liked Music", systemImage: "hand.thumbsup.fill")
                        }
                        NavigationLink { HistoryView() } label: { Label("History", systemImage: "clock") }
                        NavigationLink { LibrarySongsView() } label: { Label("Songs", systemImage: "music.note") }
                    }
                    ForEach(YouTubeMusic.libraryFeeds, id: \.browseId) { feed in
                        Section(feed.title) { LibraryFeedRows(browseId: feed.browseId) }
                    }
                }
                .navigationTitle("Library")
            } else {
                ContentUnavailableView {
                    Label("Your library", systemImage: "music.note.list")
                } description: {
                    Text("Sign in to YouTube Music to see your playlists, liked songs, albums and history.")
                } actions: {
                    Button("Sign in") { showSignIn = true }.buttonStyle(.borderedProminent)
                }
                .navigationTitle("Library")
            }
        }
        .sheet(isPresented: $showSignIn) {
            NavigationStack {
                SignInView { showSignIn = false }
                    .navigationTitle("Sign in")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar { Button("Cancel") { showSignIn = false } }
            }
        }
    }
}

private struct LibraryFeedRows: View {
    let browseId: String
    @State private var items: [ShelfItem]?

    var body: some View {
        Group {
            if let items {
                if items.isEmpty { Text("Nothing here yet").foregroundStyle(.secondary) }
                ForEach(items) { item in
                    if let route = item.route {
                        NavigationLink(value: route) {
                            HStack(spacing: 12) {
                                ArtworkView(url: item.thumbnailUrl, size: 48, circle: item.browseType == .artist)
                                VStack(alignment: .leading) {
                                    Text(item.title).lineLimit(1)
                                    Text(item.subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                                }
                            }
                        }
                    }
                }
            } else {
                ProgressView()
            }
        }
        .task { if items == nil { items = (try? await YouTubeMusic.libraryItems(browseId)) ?? [] } }
    }
}

private struct HistoryView: View {
    @EnvironmentObject var player: PlayerModel
    var body: some View {
        Loadable(load: { try await YouTubeMusic.history() }) { songs in
            List(Array(songs.enumerated()), id: \.element.id) { i, song in
                SongRow(song: song) { player.play(songs, startAt: i) }
            }
            .listStyle(.plain)
        }
        .navigationTitle("History")
    }
}

private struct LibrarySongsView: View {
    @EnvironmentObject var player: PlayerModel
    var body: some View {
        Loadable(load: { try await YouTubeMusic.librarySongs() }) { songs in
            List(Array(songs.enumerated()), id: \.element.id) { i, song in
                SongRow(song: song) { player.play(songs, startAt: i) }
            }
            .listStyle(.plain)
        }
        .navigationTitle("Songs")
    }
}
