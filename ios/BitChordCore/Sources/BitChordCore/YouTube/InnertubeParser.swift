import Foundation

/// InnerTube responses, read into BitChord's models. A port of the Android/desktop
/// `InnertubeParser`: structured paths where the layout is stable (search, home), a recursive
/// scan where it is not (playlists, library), and the same rules for telling a song from a
/// music-video upload and for crediting rows that do not name their artist.
public enum InnertubeParser {

    // MARK: Search

    public static func parseSearchPage(_ response: JSONObject, includeVideos: Bool = false) -> SearchPage {
        let cards = JSON.collect(response, "musicCardShelfRenderer")
        let topResults: [SearchResult] = includeVideos ? [] : cards.compactMap { card in
            if let song = parseCardShelfSong(card) { return .topTrack(song) }
            if let browse = parseCardShelfBrowse(card) { return .browse(browse) }
            return nil
        }
        // Rows inside an artist's promoted card are billed by the card.
        var cardCredits: [(JSONObject, Credits)] = []
        if !includeVideos {
            for card in cards {
                guard let credit = cardShelfCredit(card) else { continue }
                for row in JSON.collect(card, "musicResponsiveListItemRenderer") { cardCredits.append((row, credit)) }
            }
        }
        let rows = JSON.collect(response, "musicResponsiveListItemRenderer")
        var seen = Set<String>()
        var parsed: [SearchResult] = []
        for result in topResults {
            switch result {
            case .topTrack(let song):
                if !song.isVideo && seen.insert("v:" + song.videoId).inserted { parsed.append(result) }
            case .browse(let item):
                if seen.insert("b:" + item.browseId).inserted { parsed.append(result) }
            case .track: break
            }
        }
        for renderer in rows {
            // Browse rows first: an album row also carries a "play album" videoId.
            if let browse = parseBrowseItem(renderer) {
                if seen.insert("b:" + browse.browseId).inserted { parsed.append(.browse(browse)) }
            } else {
                let fallback = cardCredits.first { NSDictionary(dictionary: $0.0).isEqual(to: renderer) }?.1 ?? Credits()
                if let song = parseResponsiveListItem(renderer, fallback: fallback),
                   song.isVideo == includeVideos, seen.insert("v:" + song.videoId).inserted {
                    parsed.append(.track(song))
                }
            }
        }
        return SearchPage(rows: parsed, continuation: continuationToken(response))
    }

    public static func parseSearchSuggestions(_ response: JSONObject) -> [String] {
        var out: [String] = []
        for renderer in JSON.collect(response, "searchSuggestionRenderer") {
            let query = renderer.o("navigationEndpoint").o("searchEndpoint").s("query") ?? renderer.o("suggestion").runs()
            if !query.trimmingCharacters(in: .whitespaces).isEmpty && !out.contains(query) { out.append(query) }
        }
        return out
    }

    static func parseBrowseItem(_ renderer: JSONObject) -> BrowseItem? {
        guard let endpoint = renderer.o("navigationEndpoint").o("browseEndpoint"),
              let browseId = endpoint.s("browseId") else { return nil }
        let pageType = endpoint.o("browseEndpointContextSupportedConfigs").o("browseEndpointContextMusicConfig").s("pageType") ?? ""
        let columns = renderer.a("flexColumns") ?? []
        let title = flexText(columns, 0)
        if title.isBlank { return nil }
        let subtitle = flexText(columns, 1)
        if isVideoWord(title) || isVideoWord(subtitle) { return nil }
        return BrowseItem(
            browseId: browseId, title: title, subtitle: subtitle,
            thumbnailUrl: thumbnails(renderer.o("thumbnail")).bestThumbnail,
            type: browseType(pageType)
        )
    }

    // MARK: Home and shelves

    public static func parseHome(_ response: JSONObject) -> [HomeShelf] {
        sectionContents(response).compactMap { section in
            let s = Optional<Any>.some(section)
            if let carousel = s.o("musicCarouselShelfRenderer") { return carouselShelf(carousel) }
            if let shelf = s.o("musicShelfRenderer") { return plainShelf(shelf) }
            return nil
        }
    }

    public static func parseHomeContinuation(_ root: Any) -> [HomeShelf] {
        var out: [HomeShelf] = []
        func walk(_ node: Any) {
            if let object = node as? JSONObject {
                if let c = object["musicCarouselShelfRenderer"] as? JSONObject, let shelf = carouselShelf(c) { out.append(shelf) }
                if let p = object["musicShelfRenderer"] as? JSONObject, let shelf = plainShelf(p) { out.append(shelf) }
                for v in object.values { walk(v) }
            } else if let array = node as? [Any] {
                array.forEach(walk)
            }
        }
        walk(root)
        return out
    }

    public static func parseMoodAndGenres(_ response: JSONObject) -> [MoodGenreSection] {
        let parsed: [MoodGenreSection] = sectionContents(response).compactMap { section in
            guard let grid = Optional<Any>.some(section).o("gridRenderer") else { return nil }
            let title = grid.o("header").o("gridHeaderRenderer").o("title").runs()
            let items: [MoodGenre] = (grid.a("items") ?? []).compactMap { item in
                guard let button = Optional<Any>.some(item).o("musicNavigationButtonRenderer"),
                      let endpoint = button.o("clickCommand").o("browseEndpoint") ?? button.o("navigationEndpoint").o("browseEndpoint"),
                      let browseId = endpoint.s("browseId") else { return nil }
                let label = button.o("buttonText").runs()
                if label.isBlank { return nil }
                return MoodGenre(
                    title: label, browseId: browseId, params: endpoint.s("params"),
                    stripeColor: button.o("solid").s("leftStripeColor").flatMap { UInt64($0) }.map { UInt32(truncatingIfNeeded: $0) }
                )
            }
            return title.isBlank || items.isEmpty ? nil : MoodGenreSection(title: title, items: items)
        }
        // A signed-in page leads with "For you", shortcuts to buttons the later grids hold.
        return parsed.enumerated().filter { index, section in
            let later = Set(parsed.dropFirst(index + 1).flatMap { $0.items.map { $0.browseId + "|" + ($0.params ?? "") } })
            let shortcuts = section.items.contains { later.contains($0.browseId + "|" + ($0.params ?? "")) }
            return !shortcuts && section.title.lowercased() != "for you"
        }.map(\.element)
    }

    static func carouselShelf(_ carousel: JSONObject) -> HomeShelf? {
        let header = carousel.o("header").o("musicCarouselShelfBasicHeaderRenderer")
        let title = header.o("title").runs()
        let strapline = header.o("strapline").runs()
        if isVideoWord(title) { return nil }
        let items: [ShelfItem] = (carousel.a("contents") ?? []).compactMap { item in
            let i = Optional<Any>.some(item)
            if let two = parseTwoRowItem(i.o("musicTwoRowItemRenderer")) { return two }
            if let song = parseResponsiveListItem(i.o("musicResponsiveListItemRenderer")), !song.isVideo {
                return ShelfItem(title: song.title, subtitle: song.artist, thumbnailUrl: song.thumbnailUrl, videoId: song.videoId, browseId: nil)
            }
            return parseArtistRow(i.o("musicResponsiveListItemRenderer"))
        }
        let more = moreEndpoint(header)
        return items.isEmpty ? nil : HomeShelf(
            title: title.isBlank ? "For you" : title, items: items, strapline: strapline,
            moreBrowseId: more.s("browseId"), moreParams: more.s("params")
        )
    }

    static func plainShelf(_ shelf: JSONObject) -> HomeShelf? {
        let title = shelf.o("title").runs()
        if isVideoWord(title) { return nil }
        let items: [ShelfItem] = (shelf.a("contents") ?? []).compactMap {
            parseResponsiveListItem(Optional<Any>.some($0).o("musicResponsiveListItemRenderer"))
        }.filter { !$0.isVideo }.map {
            ShelfItem(title: $0.title, subtitle: $0.artist, thumbnailUrl: $0.thumbnailUrl, videoId: $0.videoId, browseId: nil)
        }
        return items.isEmpty ? nil : HomeShelf(title: title.isBlank ? "For you" : title, items: items)
    }

    private static func moreEndpoint(_ header: JSONObject?) -> JSONObject? {
        if let e = (header.o("title").a("runs") ?? []).lazy.compactMap({ Optional<Any>.some($0).o("navigationEndpoint").o("browseEndpoint") }).first { return e }
        if let e = header.o("moreContentButton").o("buttonRenderer").o("navigationEndpoint").o("browseEndpoint") { return e }
        if let e = header.o("moreContentButton").o("musicMoreContentButtonRenderer").o("navigationEndpoint").o("browseEndpoint") { return e }
        if let e = (header.a("endIcons") ?? []).lazy.compactMap({ icon -> JSONObject? in
            let b = Optional<Any>.some(icon).o("musicNavigationButtonRenderer")
            return b.o("clickCommand").o("browseEndpoint") ?? b.o("navigationEndpoint").o("browseEndpoint")
        }).first { return e }
        return header.o("navigationEndpoint").o("browseEndpoint")
    }

    // MARK: Artist

    public static func parseArtistPage(_ response: JSONObject) -> ArtistPage {
        let header = response["header"]
        let credit = Credits(artistName: artistName(header))
        var songs: [Song] = []
        var moreSongs: String?
        var shelves: [HomeShelf] = []
        for section in sectionContents(response) {
            let s = Optional<Any>.some(section)
            if let shelf = s.o("musicShelfRenderer") {
                for row in shelf.a("contents") ?? [] {
                    if let song = parseResponsiveListItem(Optional<Any>.some(row).o("musicResponsiveListItemRenderer"), fallback: credit) { songs.append(song) }
                }
                if moreSongs == nil {
                    moreSongs = Optional<Any>.some(shelf.o("title").a("runs")?.first as Any).o("navigationEndpoint").o("browseEndpoint").s("browseId")
                }
            }
            if let carousel = s.o("musicCarouselShelfRenderer") {
                let h = carousel.o("header").o("musicCarouselShelfBasicHeaderRenderer")
                let title = h.o("title").runs()
                if isVideoWord(title) { continue }
                let items = (carousel.a("contents") ?? []).compactMap { parseTwoRowItem(Optional<Any>.some($0).o("musicTwoRowItemRenderer")) }
                    .filter { $0.browseId != nil }
                let more = moreEndpoint(h)
                if !title.isBlank && !items.isEmpty {
                    shelves.append(HomeShelf(title: title, items: items, moreBrowseId: more.s("browseId"), moreParams: more.s("params")))
                }
            }
        }
        return ArtistPage(
            songs: songs, moreSongsBrowseId: moreSongs, shelves: shelves,
            thumbnailUrl: artistThumbnail(header), name: credit.artistName,
            description: parseDescription(response), subscriberCountText: subscriberCount(header),
            monthlyListenerCount: Optional<Any>.some(header as Any).o("musicImmersiveHeaderRenderer").o("monthlyListenerCount").firstRunText(),
            subscription: subscription(header)
        )
    }

    private static func subscription(_ header: Any?) -> SubscriptionState? {
        guard let immersive = Optional<Any>.some(header as Any).o("musicImmersiveHeaderRenderer") else { return nil }
        for button in [immersive.o("subscriptionButton2").o("subscribeButtonRenderer"), immersive.o("subscriptionButton").o("subscribeButtonRenderer")].compactMap({ $0 }) {
            guard let subscribed = button["subscribed"] as? Bool else { continue }
            let channelId = button.s("channelId") ?? (button.a("serviceEndpoints") ?? []).lazy.compactMap {
                Optional<Any>.some($0).o("subscribeEndpoint").a("channelIds")?.first as? String
            }.first
            if let channelId { return SubscriptionState(channelId: channelId, subscribed: subscribed) }
        }
        return nil
    }

    private static func subscriberCount(_ header: Any?) -> String? {
        let immersive = Optional<Any>.some(header as Any).o("musicImmersiveHeaderRenderer")
        let b2 = immersive.o("subscriptionButton2").o("subscribeButtonRenderer")
        let b1 = immersive.o("subscriptionButton").o("subscribeButtonRenderer")
        return b2.o("subscriberCountWithSubscribeText").firstRunText()
            ?? b1.o("longSubscriberCountText").firstRunText()
            ?? b1.o("shortSubscriberCountText").firstRunText()
    }

    private static func artistName(_ header: Any?) -> String? {
        let h = Optional<Any>.some(header as Any)
        guard let renderer = h.o("musicImmersiveHeaderRenderer") ?? h.o("musicVisualHeaderRenderer") else { return nil }
        let title = renderer.o("title").runs()
        return title.isBlank ? nil : title
    }

    private static func artistThumbnail(_ header: Any?) -> String? {
        guard let header else { return nil }
        let h = Optional<Any>.some(header)
        let immersive = h.o("musicImmersiveHeaderRenderer")
        let visual = h.o("musicVisualHeaderRenderer")
        let renderer = (immersive.o("thumbnail") ?? visual.o("foregroundThumbnail") ?? visual.o("thumbnail")).o("musicThumbnailRenderer")
            ?? JSON.collect(header, "musicThumbnailRenderer").first
        return (renderer.o("thumbnail").a("thumbnails") ?? []).bestThumbnail
    }

    // MARK: Albums, playlists, library

    /// Every track row with a videoId anywhere in the response, credited by the page's header.
    public static func collectSongsDeep(_ root: Any) -> [Song] {
        let credit = pageCredit(root)
        var out: [Song] = []
        var index: [String: Int] = [:]
        for renderer in JSON.collect(root, "musicResponsiveListItemRenderer") {
            guard let song = parseResponsiveListItem(renderer, fallback: credit) else { continue }
            if let i = index[song.videoId] { out[i] = song } else { index[song.videoId] = out.count; out.append(song) }
        }
        return out
    }

    public struct PlaylistShelfPage { public var songs: [Song]; public var suggested: [Song]; public var continuation: String? }

    /// A playlist's own tracks, separated from YouTube's "Suggestions" shelf.
    public static func parsePlaylistShelf(_ root: JSONObject) -> PlaylistShelfPage? {
        guard let scope: JSONObject = root.o("continuationContents")
            ?? root.o("contents").o("twoColumnBrowseResultsRenderer").o("secondaryContents") else { return nil }
        let playlistScope = scope.o("musicPlaylistShelfContinuation") ?? JSON.collect(scope, "musicPlaylistShelfRenderer").first
        let suggestionShelves = JSON.collect(scope, "musicShelfRenderer").filter { $0.o("title").runs() == "Suggestions" }
        if playlistScope == nil && suggestionShelves.isEmpty { return nil }
        let credit = pageCredit(root)
        var known = Set<String>()
        let songs = (playlistScope.map { JSON.collect($0, "musicResponsiveListItemRenderer") } ?? [])
            .compactMap { parseResponsiveListItem($0, fallback: credit) }
            .filter { known.insert($0.videoId).inserted }
        var seenSuggested = Set<String>()
        let suggested = suggestionShelves.flatMap { JSON.collect($0, "musicResponsiveListItemRenderer") }
            .compactMap { parseResponsiveListItem($0, fallback: credit) }
            .filter { !known.contains($0.videoId) && seenSuggested.insert($0.videoId).inserted }
        return PlaylistShelfPage(songs: songs, suggested: suggested, continuation: playlistScope.map { continuationToken($0) } ?? nil)
    }

    public static func parseLibraryItems(_ root: Any) -> (items: [ShelfItem], continuation: String?) {
        var out: [ShelfItem] = []
        var seen = Set<String>()
        for renderer in JSON.collect(root, "musicTwoRowItemRenderer") {
            if let item = parseTwoRowItem(renderer), let id = item.browseId, seen.insert(id).inserted { out.append(item) }
        }
        for renderer in JSON.collect(root, "musicResponsiveListItemRenderer") {
            if let item = parseBrowseItem(renderer), seen.insert(item.browseId).inserted {
                out.append(ShelfItem(title: item.title, subtitle: item.subtitle, thumbnailUrl: item.thumbnailUrl, videoId: nil, browseId: item.browseId))
            }
        }
        return (out, continuationToken(root))
    }

    public static func parseUserPlaylists(_ items: [ShelfItem]) -> [UserPlaylist] {
        items.compactMap { item in
            guard let id = item.browseId, id.hasPrefix("VL") else { return nil }
            if ["LM", "SE", "RD", "OLAK", "MPRE"].contains(where: { id.hasPrefix("VL" + $0) }) { return nil }
            return UserPlaylist(playlistId: String(id.dropFirst(2)), title: item.title, subtitle: item.subtitle, thumbnailUrl: item.thumbnailUrl)
        }
    }

    /// Token for the next page, in either of the two formats YouTube uses.
    public static func continuationToken(_ root: Any) -> String? {
        let endpoint = JSON.collect(root, "continuationItemRenderer").first.o("continuationEndpoint")
        if let token = (endpoint.o("continuationCommand")
            ?? (endpoint.o("commandExecutorCommand").a("commands") ?? []).lazy.compactMap { Optional<Any>.some($0).o("continuationCommand") }.first).s("token") {
            return token
        }
        return JSON.collect(root, "nextContinuationData").first.s("continuation")
    }

    public static func parseBrowseHeader(_ root: Any) -> BrowseHeader? {
        guard let header = headerRenderer(root) else { return nil }
        let title = header.o("title").runs()
        if title.isBlank { return nil }
        var lines: [String] = []
        for key in headerCreditLines {
            let line = header.o(key).runs()
            if !line.isBlank && !lines.contains(line) { lines.append(line) }
        }
        return BrowseHeader(
            title: title, subtitle: lines.joined(separator: " • "),
            thumbnailUrl: (JSON.collect(header, "musicThumbnailRenderer").first.o("thumbnail").a("thumbnails") ?? []).bestThumbnail
        )
    }

    /// The playlist that backs an album's full track list (`OLAK5uy_…`).
    public static func parseAlbumPlaylistId(_ root: JSONObject) -> String? {
        if let header = headerRenderer(root) {
            for renderer in JSON.collect(header, "musicPlayButtonRenderer") + JSON.collect(header, "buttonRenderer") {
                if let id = playlistIdFromPlayAction(renderer) { return id }
            }
        }
        guard let canonical = root.o("microformat").o("microformatDataRenderer").s("urlCanonical"),
              let range = canonical.range(of: "list=") else { return nil }
        let id = canonical[range.upperBound...].split(separator: "&").first.map(String.init) ?? ""
        return id.isEmpty ? nil : id
    }

    private static func playlistIdFromPlayAction(_ node: JSONObject) -> String? {
        let endpoint = node.o("playNavigationEndpoint") ?? node.o("navigationEndpoint") ?? node
        return endpoint.o("watchPlaylistEndpoint").s("playlistId") ?? endpoint.o("watchEndpoint").s("playlistId")
    }

    public static func parseDescription(_ root: Any) -> String? {
        let shelf = JSON.collect(root, "musicDescriptionShelfRenderer").first.o("description").runs()
        if !shelf.isBlank { return shelf }
        for name in headerRenderers + ["musicImmersiveHeaderRenderer"] {
            let text = JSON.collect(root, name).first.o("description").runs()
            if !text.isBlank { return text }
        }
        return nil
    }

    /// Saved state of an album or playlist page, and the playlist id that toggles it.
    public static func parseLibraryState(_ root: Any) -> (playlistId: String, saved: Bool)? {
        let buttons = JSON.collect(root, "musicResponsiveHeaderRenderer").first.a("buttons") ?? []
        guard let save = buttons.lazy.compactMap({ Optional<Any>.some($0).o("toggleButtonRenderer") }).first(where: isSaveToggle),
              save.s("isDisabled") != "true" else { return nil }
        let play = buttons.lazy.compactMap { Optional<Any>.some($0).o("musicPlayButtonRenderer").o("playNavigationEndpoint") }.first
        guard let id = play.o("watchPlaylistEndpoint").s("playlistId") ?? play.o("watchEndpoint").s("playlistId") else { return nil }
        return (id, save.s("isToggled") == "true")
    }

    public static func parsePlaylistOwned(_ root: Any) -> Bool? {
        if !JSON.collect(root, "musicEditablePlaylistDetailHeaderRenderer").isEmpty { return true }
        guard let header = JSON.collect(root, "musicResponsiveHeaderRenderer").first else { return nil }
        if JSON.collect(header, "menuNavigationItemRenderer").contains(where: { ["DELETE", "EDIT"].contains($0.o("icon").s("iconType") ?? "") }) { return true }
        return !(header.a("buttons") ?? []).contains { isSaveToggle(Optional<Any>.some($0).o("toggleButtonRenderer")) }
    }

    private static func isSaveToggle(_ toggle: JSONObject?) -> Bool {
        toggle.o("defaultIcon").s("iconType") == "BOOKMARK_BORDER" || toggle.o("toggledIcon").s("iconType") == "BOOKMARK"
    }

    // MARK: Watch queue and menus

    /// The radio mix after a track: `next` response rows.
    public static func parseWatchQueue(_ root: Any) -> [Song] {
        var out: [Song] = []
        var seen = Set<String>()
        for renderer in JSON.collect(root, "playlistPanelVideoRenderer") {
            guard let videoId = renderer.s("videoId"), seen.insert(videoId).inserted else { continue }
            let title = renderer.o("title").runs()
            if title.isBlank { continue }
            let bylineRuns = renderer.o("longBylineText").a("runs") ?? []
            let byline = bylineRuns.map { Optional<Any>.some($0).s("text") ?? "" }
            let artist = byline.prefix { !$0.contains("•") }.joined().trimmingCharacters(in: .whitespaces)
            let credits = creditsOf(bylineRuns)
            let length = renderer.o("lengthText").runs()
            out.append(Song(
                videoId: videoId, title: title, artist: artist,
                thumbnailUrl: (renderer.o("thumbnail").a("thumbnails") ?? []).bestThumbnail,
                durationText: length.isBlank ? nil : length,
                artistId: credits.artistId, artists: credits.artists,
                albumId: credits.albumId, albumName: credits.albumName,
                isVideo: byline.contains { $0.lowercased().contains("views") },
                isExplicit: JSON.hasExplicitBadge(renderer["badges"])
            ))
        }
        return out
    }

    /// The lyrics tab of a `next` response: the browse id `get lyrics` needs.
    public static func lyricsBrowseId(_ next: JSONObject) -> String? {
        for tab in JSON.collect(next, "tabRenderer") {
            let endpoint = tab.o("endpoint").o("browseEndpoint")
            let pageType = endpoint.o("browseEndpointContextSupportedConfigs").o("browseEndpointContextMusicConfig").s("pageType") ?? ""
            if pageType == "MUSIC_PAGE_TYPE_TRACK_LYRICS", let id = endpoint.s("browseId") { return id }
        }
        return nil
    }

    /// Plain lyrics off a lyrics browse response, with their source line.
    public static func parseLyrics(_ response: JSONObject) -> (text: String, source: String?)? {
        guard let shelf = JSON.collect(response, "musicDescriptionShelfRenderer").first else { return nil }
        let text = shelf.o("description").runs()
        if text.isBlank { return nil }
        let footer = shelf.o("footer").runs()
        return (text, footer.isBlank ? nil : footer)
    }

    public static func parseSongMenu(_ root: Any, videoId: String) -> SongMenu? {
        guard let row = JSON.collect(root, "playlistPanelVideoRenderer").first(where: { $0.s("videoId") == videoId }) else { return nil }
        let like = JSON.collect(row, "likeButtonRenderer").first.s("likeStatus").flatMap(LikeStatus.init(rawValue:))
        let libraryIcons: Set<String> = ["LIBRARY_ADD", "LIBRARY_REMOVE", "LIBRARY_SAVED"]
        let toggle = JSON.collect(row, "toggleMenuServiceItemRenderer").first { t in
            feedbackToken(t, "defaultServiceEndpoint") != nil &&
                (libraryIcons.contains(t.o("defaultIcon").s("iconType") ?? "") || libraryIcons.contains(t.o("toggledIcon").s("iconType") ?? ""))
        }
        let defaultAdds = toggle.o("defaultIcon").s("iconType") == "LIBRARY_ADD"
        let defaultToken = toggle.flatMap { feedbackToken($0, "defaultServiceEndpoint") }
        let toggledToken = toggle.flatMap { feedbackToken($0, "toggledServiceEndpoint") }
        return SongMenu(
            likeStatus: like, inLibrary: toggle != nil && !defaultAdds,
            addToLibraryToken: defaultAdds ? defaultToken : toggledToken,
            removeFromLibraryToken: defaultAdds ? toggledToken : defaultToken
        )
    }

    private static func feedbackToken(_ node: JSONObject, _ endpoint: String) -> String? {
        node.o(endpoint).o("feedbackEndpoint").s("feedbackToken")
    }

    public static func parseAccount(_ response: Any) -> Account? {
        guard let header = JSON.collect(response, "activeAccountHeaderRenderer").first else { return nil }
        let name = header.o("accountName").runs()
        if name.isBlank { return nil }
        var email = header.o("email").runs()
        if email.isBlank { email = header.o("email").s("simpleText") ?? "" }
        if email.isBlank { email = header.o("channelHandle").runs() }
        return Account(name: name, email: email, thumbnailUrl: (header.o("accountPhoto").a("thumbnails") ?? []).bestThumbnail)
    }

    // MARK: Renderers

    struct Credits {
        var artistId: String?
        var artistName: String?
        var artists: [ArtistRef] = []
        var albumId: String?
        var albumName: String?
    }

    static func parseResponsiveListItem(_ renderer: JSONObject?, fallback: Credits = Credits()) -> Song? {
        guard let renderer else { return nil }
        guard let videoId = renderer.o("playlistItemData").s("videoId")
            ?? renderer.o("overlay").o("musicItemThumbnailOverlayRenderer").o("content")
                .o("musicPlayButtonRenderer").o("playNavigationEndpoint").o("watchEndpoint").s("videoId") else { return nil }
        let columns = renderer.a("flexColumns") ?? []
        let title = flexText(columns, 0)
        if title.isBlank { return nil }
        let subtitleRuns = columns.count > 1 ? (Optional<Any>.some(columns[1]).o("musicResponsiveListItemFlexColumnRenderer").o("text").a("runs") ?? []) : []
        let subtitle = subtitleRuns.map { Optional<Any>.some($0).s("text") ?? "" }.joined()
        let parts = subtitle.components(separatedBy: " • ").filter { !$0.isBlank }
        let duration = parts.last.flatMap { matches($0, durationPattern) ? $0 : nil }
            ?? (renderer.a("fixedColumns") ?? []).lazy.compactMap { column -> String? in
                let t = Optional<Any>.some(column).o("musicResponsiveListItemFixedColumnRenderer").o("text").runs()
                return matches(t, durationPattern) ? t : nil
            }.first
        let rowType = parts.first { typeWords.contains($0.lowercased()) }?.lowercased()
        let artist = parts.first { !matches($0, durationPattern) && !typeWords.contains($0.lowercased()) && !matches($0, tallyPattern) }
        let credits = creditsOf(columns.flatMap { Optional<Any>.some($0).o("musicResponsiveListItemFlexColumnRenderer").o("text").a("runs") ?? [] })
        let credited = artistNamesFromRuns(subtitleRuns)
        let thumbs = thumbnails(renderer.o("thumbnail"))
        return Song(
            videoId: videoId, title: title,
            artist: credited ?? credits.artistName.flatMap { $0.isBlank ? nil : $0 } ?? artist ?? fallback.artistName ?? "Unknown artist",
            thumbnailUrl: thumbs.bestThumbnail, durationText: duration,
            artistId: credits.artistId ?? fallback.artistId,
            artists: credits.artists.isEmpty ? fallback.artists : credits.artists,
            albumId: credits.albumId ?? fallback.albumId, albumName: credits.albumName ?? fallback.albumName,
            setVideoId: renderer.o("playlistItemData").s("playlistSetVideoId"),
            isVideo: rowType == "video" || thumbs.isNotSquare,
            isExplicit: JSON.hasExplicitBadge(renderer["badges"])
        )
    }

    private static func parseCardShelfSong(_ renderer: JSONObject) -> Song? {
        guard let videoId = renderer.o("onTap").o("watchEndpoint").s("videoId") else { return nil }
        let title = renderer.o("title").runs()
        if title.isBlank { return nil }
        let subtitleRuns = renderer.o("subtitle").a("runs") ?? []
        let parts = subtitleRuns.map { Optional<Any>.some($0).s("text") ?? "" }.joined().components(separatedBy: " • ").filter { !$0.isBlank }
        let duration = parts.last.flatMap { matches($0, durationPattern) ? $0 : nil }
        let rowType = parts.first { typeWords.contains($0.lowercased()) }?.lowercased()
        let credits = creditsOf(subtitleRuns)
        let artist = parts.first { !matches($0, durationPattern) && !typeWords.contains($0.lowercased()) && !matches($0, tallyPattern) }
        let thumbs = thumbnails(renderer.o("thumbnail"))
        return Song(
            videoId: videoId, title: title,
            artist: artistNamesFromRuns(subtitleRuns) ?? credits.artistName ?? artist ?? "Unknown artist",
            thumbnailUrl: thumbs.bestThumbnail, durationText: duration,
            artistId: credits.artistId, artists: credits.artists, albumId: credits.albumId, albumName: credits.albumName,
            isVideo: rowType == "video" || thumbs.isNotSquare,
            isExplicit: JSON.hasExplicitBadge(renderer["subtitleBadges"])
        )
    }

    private static func cardShelfCredit(_ card: JSONObject) -> Credits? {
        guard let endpoint = card.o("onTap").o("browseEndpoint") else { return nil }
        let pageType = endpoint.o("browseEndpointContextSupportedConfigs").o("browseEndpointContextMusicConfig").s("pageType") ?? ""
        guard pageType.contains("ARTIST") else { return nil }
        let name = card.o("title").runs()
        return name.isBlank ? nil : Credits(artistId: endpoint.s("browseId"), artistName: name)
    }

    private static func parseCardShelfBrowse(_ renderer: JSONObject) -> BrowseItem? {
        guard let endpoint = renderer.o("onTap").o("browseEndpoint"), let browseId = endpoint.s("browseId") else { return nil }
        let pageType = endpoint.o("browseEndpointContextSupportedConfigs").o("browseEndpointContextMusicConfig").s("pageType") ?? ""
        let title = renderer.o("title").runs()
        if title.isBlank { return nil }
        let subtitle = renderer.o("subtitle").runs()
        if isVideoWord(title) || isVideoWord(subtitle) { return nil }
        return BrowseItem(browseId: browseId, title: title, subtitle: subtitle, thumbnailUrl: thumbnails(renderer.o("thumbnail")).bestThumbnail, type: browseType(pageType))
    }

    private static func parseArtistRow(_ renderer: JSONObject?) -> ShelfItem? {
        guard let renderer else { return nil }
        let endpoint = renderer.o("navigationEndpoint").o("browseEndpoint")
        let pageType = endpoint.o("browseEndpointContextSupportedConfigs").o("browseEndpointContextMusicConfig").s("pageType") ?? ""
        guard pageType.contains("ARTIST"), let browseId = endpoint.s("browseId") else { return nil }
        let columns = renderer.a("flexColumns") ?? []
        let title = flexText(columns, 0)
        if title.isBlank { return nil }
        return ShelfItem(title: title, subtitle: flexText(columns, 1), thumbnailUrl: thumbnails(renderer.o("thumbnail")).bestThumbnail, videoId: nil, browseId: browseId)
    }

    static func parseTwoRowItem(_ renderer: JSONObject?) -> ShelfItem? {
        guard let renderer else { return nil }
        let title = renderer.o("title").runs()
        if title.isBlank { return nil }
        let endpoint = renderer.o("navigationEndpoint")
        let browseId = endpoint.o("browseEndpoint").s("browseId")
        // "Non-music audio track" cards carry MPED<videoId> instead of a watch endpoint.
        let videoId = endpoint.o("watchEndpoint").s("videoId") ?? browseId.flatMap { $0.hasPrefix("MPED") ? String($0.dropFirst(4)) : nil }
        let resolved = browseId.flatMap { $0.hasPrefix("MPED") ? nil : $0 }
        let thumbs = thumbnails(renderer.o("thumbnailRenderer"))
        let subtitle = renderer.o("subtitle").runs()
        if resolved == nil && videoId != nil && thumbs.isNotSquare { return nil }
        if let r = resolved, !["VL", "PL", "MPRE", "UC"].contains(where: { r.hasPrefix($0) }), isVideoWord(title) || isVideoWord(subtitle) {
            return nil
        }
        return ShelfItem(title: title, subtitle: subtitle, thumbnailUrl: thumbs.bestThumbnail, videoId: videoId, browseId: resolved)
    }

    /// Artist and album links in a run list, and every credited artist.
    static func creditsOf(_ runs: [Any]) -> Credits {
        var credits = Credits()
        for run in runs {
            let r = Optional<Any>.some(run)
            let browse = r.o("navigationEndpoint").o("browseEndpoint")
            guard let id = browse.s("browseId") else { continue }
            let pageType = browse.o("browseEndpointContextSupportedConfigs").o("browseEndpointContextMusicConfig").s("pageType") ?? ""
            let name = r.s("text") ?? ""
            if pageType.contains("ARTIST") {
                if !name.isBlank { credits.artists.append(ArtistRef(name: name, browseId: id)) }
                if credits.artistId == nil { credits.artistId = id; credits.artistName = name }
            } else if pageType.contains("ALBUM"), credits.albumId == nil {
                credits.albumId = id; credits.albumName = name
            }
        }
        return credits
    }

    /// Every name in the artist segment of a byline, linked or not.
    static func artistNamesFromRuns(_ runs: [Any]) -> String? {
        var groups: [[Any]] = [[]]
        for run in runs {
            if (Optional<Any>.some(run).s("text") ?? "").trimmingCharacters(in: .whitespaces) == "•" { groups.append([]) } else { groups[groups.count - 1].append(run) }
        }
        guard let group = groups.first(where: { g in
            g.contains { (Optional<Any>.some($0).o("navigationEndpoint").o("browseEndpoint").o("browseEndpointContextSupportedConfigs")
                .o("browseEndpointContextMusicConfig").s("pageType") ?? "").contains("ARTIST") }
        }) else { return nil }
        var names: [String] = []
        for (i, run) in group.enumerated() where i % 2 == 0 {
            let name = (Optional<Any>.some(run).s("text") ?? "").trimmingCharacters(in: .whitespaces)
            if !name.isEmpty && !names.contains(name) { names.append(name) }
        }
        return names.isEmpty ? nil : names.joined(separator: ", ")
    }

    /// Who a release page is billed to, off its header — rows on an album omit the artist.
    static func pageCredit(_ root: Any) -> Credits {
        guard let header = headerRenderer(root) else { return Credits() }
        let lines = headerCreditLines.map { header.o($0).a("runs") ?? [] }
        let parts = lines.flatMap { line -> [String] in
            let text = line.map { Optional<Any>.some($0).s("text") ?? "" }.joined()
            return text.components(separatedBy: " • ").flatMap { $0.components(separatedBy: " · ") }.map { $0.trimmingCharacters(in: .whitespaces) }
        }
        guard parts.contains(where: { releaseWords.contains($0.lowercased()) }) else { return Credits() }
        var credits = creditsOf(lines.flatMap { $0 })
        if let named = lines.lazy.compactMap(artistNamesFromRuns).first {
            credits.artistName = named
            return credits
        }
        credits.artistName = parts.first { !$0.isBlank && !typeWords.contains($0.lowercased()) && !matches($0, tallyPattern) && !matches($0, yearPattern) && !matches($0, durationPattern) }
        return credits
    }

    /// "Song • Chelsea Wolfe" → "Chelsea Wolfe".
    public static func artistFromSubtitle(_ subtitle: String) -> String {
        let parts = subtitle.components(separatedBy: " • ").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        return parts.first { !typeWords.contains($0.lowercased()) && !matches($0, tallyPattern) && !matches($0, durationPattern) } ?? subtitle
    }

    // MARK: Helpers

    private static func sectionContents(_ response: JSONObject) -> [Any] {
        let tab = response.o("contents").o("singleColumnBrowseResultsRenderer").a("tabs")?.first
        return Optional<Any>.some(tab as Any).o("tabRenderer").o("content").o("sectionListRenderer").a("contents") ?? []
    }

    private static func headerRenderer(_ root: Any) -> JSONObject? {
        headerRenderers.lazy.compactMap { JSON.collect(root, $0).first }.first
    }

    private static func flexText(_ columns: [Any], _ index: Int) -> String {
        guard index < columns.count else { return "" }
        return Optional<Any>.some(columns[index]).o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
    }

    private static func thumbnails(_ container: JSONObject?) -> [Any] {
        container.o("musicThumbnailRenderer").o("thumbnail").a("thumbnails") ?? []
    }

    private static func browseType(_ pageType: String) -> BrowseType {
        if pageType.contains("ALBUM") { return .album }
        if pageType.contains("ARTIST") { return .artist }
        if pageType.contains("PLAYLIST") { return .playlist }
        return .other
    }

    static let durationPattern = try! NSRegularExpression(pattern: #"^\d+:\d{2}(:\d{2})?$"#)
    static let yearPattern = try! NSRegularExpression(pattern: #"^\d{4}$"#)
    static let tallyPattern = try! NSRegularExpression(
        pattern: #"^[\d.,]+\s*[KMB]?\s+(plays|views|likes|songs|tracks|subscribers|hours?|minutes?|seconds?)\b.*$"#,
        options: [.caseInsensitive]
    )
    static let videoWord = try! NSRegularExpression(pattern: #"\bvideos?\b"#, options: [.caseInsensitive])
    static let releaseWords: Set<String> = ["album", "single", "ep"]
    static let typeWords: Set<String> = ["song", "video", "album", "single", "ep", "artist", "playlist", "podcast", "episode"]
    static let headerRenderers = ["musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer"]
    static let headerCreditLines = ["straplineTextOne", "subtitle"]

    static func matches(_ s: String, _ regex: NSRegularExpression) -> Bool {
        regex.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil
    }

    static func isVideoWord(_ s: String) -> Bool { matches(s, videoWord) }
}

extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
