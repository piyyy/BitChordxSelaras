import Foundation

/// One credited artist, with the page they open when they have one.
public struct ArtistRef: Hashable, Codable, Sendable {
    public var name: String
    public var browseId: String?
    public init(name: String, browseId: String?) { self.name = name; self.browseId = browseId }
}

/// A playable track, as every list in the app holds it.
public struct Song: Identifiable, Hashable, Codable, Sendable {
    public var videoId: String
    public var title: String
    public var artist: String
    public var thumbnailUrl: String?
    public var durationText: String?
    public var artistId: String?
    public var artists: [ArtistRef]
    public var albumId: String?
    public var albumName: String?
    /// Only a row inside a playlist carries one: what "remove from playlist" needs.
    public var setVideoId: String?
    public var isVideo: Bool
    public var isExplicit: Bool

    public var id: String { videoId }

    public init(
        videoId: String, title: String, artist: String, thumbnailUrl: String? = nil,
        durationText: String? = nil, artistId: String? = nil, artists: [ArtistRef] = [],
        albumId: String? = nil, albumName: String? = nil, setVideoId: String? = nil,
        isVideo: Bool = false, isExplicit: Bool = false
    ) {
        self.videoId = videoId; self.title = title; self.artist = artist
        self.thumbnailUrl = thumbnailUrl; self.durationText = durationText
        self.artistId = artistId; self.artists = artists; self.albumId = albumId
        self.albumName = albumName; self.setVideoId = setVideoId
        self.isVideo = isVideo; self.isExplicit = isExplicit
    }

    /// "3:45" as seconds, when the row stated a runtime.
    public var durationSeconds: Int? {
        guard let text = durationText else { return nil }
        let parts = text.split(separator: ":").compactMap { Int($0) }
        guard !parts.isEmpty else { return nil }
        return parts.reduce(0) { $0 * 60 + $1 }
    }
}

public enum BrowseType: String, Codable, Sendable { case album, artist, playlist, other }

/// An album, artist or playlist card.
public struct BrowseItem: Identifiable, Hashable, Codable, Sendable {
    public var browseId: String
    public var title: String
    public var subtitle: String
    public var thumbnailUrl: String?
    public var type: BrowseType
    public var id: String { browseId }

    public init(browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) {
        self.browseId = browseId; self.title = title; self.subtitle = subtitle
        self.thumbnailUrl = thumbnailUrl; self.type = type
    }
}

/// A card on a shelf: either something to play (`videoId`) or somewhere to go (`browseId`).
public struct ShelfItem: Identifiable, Hashable, Codable, Sendable {
    public var title: String
    public var subtitle: String
    public var thumbnailUrl: String?
    public var videoId: String?
    public var browseId: String?
    public var id: String { browseId ?? videoId ?? title }

    public init(title: String, subtitle: String, thumbnailUrl: String?, videoId: String?, browseId: String?) {
        self.title = title; self.subtitle = subtitle; self.thumbnailUrl = thumbnailUrl
        self.videoId = videoId; self.browseId = browseId
    }

    /// What kind of page a browse id opens, from its prefix.
    public var browseType: BrowseType {
        guard let id = browseId else { return .other }
        if id.hasPrefix("MPRE") || id.hasPrefix("OLAK") { return .album }
        if id.hasPrefix("UC") || id.hasPrefix("FEmusic_library_privately_owned_artist") { return .artist }
        if id.hasPrefix("VL") || id.hasPrefix("PL") || id.hasPrefix("RD") { return .playlist }
        return .other
    }

    /// The card as a song to play, its subtitle reduced to the artist.
    public var asSong: Song? {
        guard let videoId else { return nil }
        return Song(videoId: videoId, title: title, artist: InnertubeParser.artistFromSubtitle(subtitle), thumbnailUrl: thumbnailUrl)
    }
}

public struct HomeShelf: Identifiable, Hashable, Sendable {
    public var title: String
    public var items: [ShelfItem]
    public var strapline: String
    public var moreBrowseId: String?
    public var moreParams: String?
    public var id: String { title + (items.first?.id ?? "") }

    public init(title: String, items: [ShelfItem], strapline: String = "", moreBrowseId: String? = nil, moreParams: String? = nil) {
        self.title = title; self.items = items; self.strapline = strapline
        self.moreBrowseId = moreBrowseId; self.moreParams = moreParams
    }
}

public struct HomePage: Sendable {
    public var shelves: [HomeShelf]
    public var continuation: String?
}

public enum SearchResult: Identifiable, Hashable, Sendable {
    case track(Song)
    case topTrack(Song)
    case browse(BrowseItem)

    public var id: String {
        switch self {
        case .track(let s), .topTrack(let s): return "v:" + s.videoId
        case .browse(let b): return "b:" + b.browseId
        }
    }
}

public struct SearchPage: Sendable {
    public var rows: [SearchResult]
    public var continuation: String?
}

/// The filters YouTube Music's search tabs send as `params`.
public enum SearchFilter: String, CaseIterable, Identifiable, Sendable {
    case all, songs, videos, albums, artists, playlists

    public var id: String { rawValue }
    public var title: String {
        switch self {
        case .all: return "All"
        case .songs: return "Songs"
        case .videos: return "Videos"
        case .albums: return "Albums"
        case .artists: return "Artists"
        case .playlists: return "Playlists"
        }
    }
    public var params: String? {
        switch self {
        case .all: return nil
        case .songs: return "EgWKAQIIAWoKEAkQChAFEAMQBA=="
        case .videos: return "EgWKAQIQAWoKEAkQChAFEAMQBA=="
        case .albums: return "EgWKAQIYAWoKEAkQChAFEAMQBA=="
        case .artists: return "EgWKAQIgAWoKEAkQChAFEAMQBA=="
        case .playlists: return "EgWKAQIoAWoKEAkQChAFEAMQBA=="
        }
    }
}

public struct SubscriptionState: Hashable, Sendable {
    public var channelId: String
    public var subscribed: Bool
}

public struct ArtistPage: Sendable {
    public var songs: [Song]
    public var moreSongsBrowseId: String?
    public var shelves: [HomeShelf]
    public var thumbnailUrl: String?
    public var name: String?
    public var description: String?
    public var subscriberCountText: String?
    public var monthlyListenerCount: String?
    public var subscription: SubscriptionState?
}

public struct BrowseHeader: Sendable {
    public var title: String
    public var subtitle: String
    public var thumbnailUrl: String?
}

/// An album or playlist page, header and tracks.
public struct BrowsePage: Sendable {
    public var header: BrowseHeader?
    public var songs: [Song]
    public var suggested: [Song]
    public var description: String?
    public var continuation: String?
    public var shelves: [HomeShelf]
    public var libraryPlaylistId: String?
    public var saved: Bool?
    public var owned: Bool?
}

public struct MoodGenre: Identifiable, Hashable, Sendable {
    public var title: String
    public var browseId: String
    public var params: String?
    public var stripeColor: UInt32?
    public var id: String { browseId + (params ?? "") }
}

public struct MoodGenreSection: Identifiable, Hashable, Sendable {
    public var title: String
    public var items: [MoodGenre]
    public var id: String { title }
}

public struct Account: Hashable, Sendable {
    public var name: String
    public var email: String
    public var thumbnailUrl: String?
}

public enum LikeStatus: String, Sendable { case like = "LIKE", dislike = "DISLIKE", indifferent = "INDIFFERENT" }

public struct SongMenu: Sendable {
    public var likeStatus: LikeStatus?
    public var inLibrary: Bool
    public var addToLibraryToken: String?
    public var removeFromLibraryToken: String?
}

public struct UserPlaylist: Identifiable, Hashable, Sendable {
    public var playlistId: String
    public var title: String
    public var subtitle: String
    public var thumbnailUrl: String?
    public var id: String { playlistId }
}

/// Artwork at the size it is drawn: Google image URLs take their size in the URL itself.
public enum Artwork {
    public static func url(_ raw: String?, size: Int) -> URL? {
        guard var s = raw, !s.isEmpty else { return nil }
        if s.hasPrefix("//") { s = "https:" + s }
        if let range = s.range(of: #"=w\d+-h\d+[^/]*$"#, options: .regularExpression) {
            s.replaceSubrange(range, with: "=w\(size)-h\(size)-l90-rj")
        } else if let range = s.range(of: #"=s\d+[^/]*$"#, options: .regularExpression) {
            s.replaceSubrange(range, with: "=s\(size)")
        } else if s.contains("i.ytimg.com") {
            // Video thumbnails: the high-quality square-ish frame.
            if let r = s.range(of: #"/[a-z0-9_]+\.jpg.*$"#, options: .regularExpression) {
                s.replaceSubrange(r, with: size > 300 ? "/hqdefault.jpg" : "/mqdefault.jpg")
            }
        }
        return URL(string: s)
    }
}
