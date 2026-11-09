import Foundation

/// What the screens ask for, in the app's own terms — the iOS counterpart of `YtMusicRepository`.
public enum YouTubeMusic {
    static var api: Innertube { Innertube.shared }

    // MARK: Home and Explore

    public static func home() async throws -> HomePage {
        let response = try await api.browse("FEmusic_home")
        return HomePage(shelves: InnertubeParser.parseHome(response), continuation: InnertubeParser.continuationToken(response))
    }

    public static func moreHome(_ token: String) async throws -> HomePage {
        let response = try await api.browseContinuation(token)
        return HomePage(shelves: InnertubeParser.parseHomeContinuation(response), continuation: InnertubeParser.continuationToken(response))
    }

    /// New releases, charts and the rest of Explore, as shelves.
    public static func shelves(_ browseId: String, params: String? = nil) async throws -> [HomeShelf] {
        InnertubeParser.parseHome(try await api.browse(browseId, params: params))
    }

    public static func moodsAndGenres() async throws -> [MoodGenreSection] {
        InnertubeParser.parseMoodAndGenres(try await api.browse("FEmusic_moods_and_genres"))
    }

    // MARK: Search

    public static func search(_ query: String, filter: SearchFilter = .all) async throws -> SearchPage {
        let response = try await api.search(query, params: filter.params, anonymous: !(await api.isSignedIn))
        return InnertubeParser.parseSearchPage(response, includeVideos: filter == .videos)
    }

    public static func moreSearch(_ token: String, filter: SearchFilter) async throws -> SearchPage {
        InnertubeParser.parseSearchPage(try await api.searchContinuation(token), includeVideos: filter == .videos)
    }

    public static func suggestions(_ input: String) async throws -> [String] {
        InnertubeParser.parseSearchSuggestions(try await api.searchSuggestions(input))
    }

    // MARK: Pages

    public static func artist(_ browseId: String) async throws -> ArtistPage {
        InnertubeParser.parseArtistPage(try await api.browse(browseId))
    }

    /// An album or playlist page. An album's browse page may list only a preview, so its backing
    /// playlist is read too when it holds more.
    public static func page(_ browseId: String) async throws -> BrowsePage {
        let response = try await api.browse(browseId)
        let header = InnertubeParser.parseBrowseHeader(response)
        let shelf = InnertubeParser.parsePlaylistShelf(response)
        var songs = shelf?.songs ?? InnertubeParser.collectSongsDeep(response)
        var continuation = shelf?.continuation
        if browseId.hasPrefix("MPRE"), let playlistId = InnertubeParser.parseAlbumPlaylistId(response) {
            if let full = try? await api.browse("VL" + playlistId),
               let fullShelf = InnertubeParser.parsePlaylistShelf(full), fullShelf.songs.count > songs.count {
                // The playlist rows lack the album credit; lend it from the album page.
                let credit = songs.first
                songs = fullShelf.songs.map { song in
                    var s = song
                    if s.albumId == nil { s.albumId = browseId; s.albumName = header?.title }
                    if s.artist == "Unknown artist", let credit { s.artist = credit.artist; s.artistId = credit.artistId }
                    if s.thumbnailUrl == nil { s.thumbnailUrl = header?.thumbnailUrl }
                    return s
                }
                continuation = fullShelf.continuation
            }
        }
        if browseId.hasPrefix("MPRE") {
            songs = songs.map { var s = $0; if s.thumbnailUrl == nil { s.thumbnailUrl = header?.thumbnailUrl }; return s }
        }
        let library = InnertubeParser.parseLibraryState(response)
        return BrowsePage(
            header: header, songs: songs, suggested: shelf?.suggested ?? [],
            description: InnertubeParser.parseDescription(response), continuation: continuation,
            shelves: InnertubeParser.parseHomeContinuation(response.o("contents").o("twoColumnBrowseResultsRenderer").o("secondaryContents") as Any)
                .filter { shelf in shelf.items.contains { $0.browseId != nil } },
            libraryPlaylistId: library?.playlistId, saved: library?.saved,
            owned: browseId.hasPrefix("VL") ? InnertubeParser.parsePlaylistOwned(response) : nil
        )
    }

    public static func morePage(_ token: String) async throws -> (songs: [Song], continuation: String?) {
        let response = try await api.browseContinuation(token)
        if let shelf = InnertubeParser.parsePlaylistShelf(response) { return (shelf.songs, shelf.continuation) }
        return (InnertubeParser.collectSongsDeep(response), InnertubeParser.continuationToken(response))
    }

    // MARK: Playback

    /// The radio mix that follows a track, for AutoPlay; the first entry is the track itself.
    public static func radio(_ videoId: String, playlistId: String? = nil) async throws -> [Song] {
        InnertubeParser.parseWatchQueue(try await api.next(videoId: videoId, playlistId: playlistId))
    }

    public static func songMenu(_ videoId: String) async throws -> SongMenu? {
        InnertubeParser.parseSongMenu(try await api.next(videoId: videoId), videoId: videoId)
    }

    /// YouTube Music's own (unsynced) lyrics for a track.
    public static func lyrics(_ videoId: String) async throws -> (text: String, source: String?)? {
        let next = try await api.next(videoId: videoId)
        guard let browseId = InnertubeParser.lyricsBrowseId(next) else { return nil }
        return InnertubeParser.parseLyrics(try await api.browse(browseId))
    }

    // MARK: Library (signed in)

    public static let likedMusic = "VLLM"

    public static let libraryFeeds: [(title: String, browseId: String)] = [
        ("Playlists", "FEmusic_liked_playlists"),
        ("Albums", "FEmusic_liked_albums"),
        ("Artists", "FEmusic_library_corpus_track_artists"),
        ("Subscriptions", "FEmusic_library_corpus_artists"),
        ("Podcasts", "FEmusic_library_non_music_audio_list"),
    ]

    public static func libraryItems(_ browseId: String) async throws -> [ShelfItem] {
        var (items, token) = InnertubeParser.parseLibraryItems(try await api.browse(browseId))
        var pages = 0
        while let t = token, pages < 10 {
            let more = InnertubeParser.parseLibraryItems(try await api.browseContinuation(t))
            items += more.items.filter { item in !items.contains { $0.id == item.id } }
            token = more.continuation
            pages += 1
        }
        return items
    }

    public static func librarySongs() async throws -> [Song] {
        InnertubeParser.collectSongsDeep(try await api.browse("FEmusic_liked_videos"))
    }

    public static func history() async throws -> [Song] {
        InnertubeParser.collectSongsDeep(try await api.browse("FEmusic_history"))
    }

    public static func userPlaylists() async throws -> [UserPlaylist] {
        InnertubeParser.parseUserPlaylists(try await libraryItems("FEmusic_liked_playlists"))
    }

    public static func account() async throws -> Account? {
        InnertubeParser.parseAccount(try await api.accountMenu())
    }
}
