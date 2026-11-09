import XCTest
@testable import BitChordCore

/// Against the real music.youtube.com. Off by default (network, and YouTube changes):
///   BITCHORD_LIVE=1 swift test --package-path ios/BitChordCore --filter LiveTests
final class LiveTests: XCTestCase {
    override func setUp() async throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["BITCHORD_LIVE"] == "1", "live tests are opt-in")
    }

    func testSearchHomeRadioAndPages() async throws {
        // e.g. BITCHORD_LOCALE=id-ID, the way an Indonesian iPhone asks.
        if let locale = ProcessInfo.processInfo.environment["BITCHORD_LOCALE"]?.split(separator: "-"), locale.count == 2 {
            await Innertube.shared.setLocale(language: String(locale[0]), region: String(locale[1]))
        }
        let songs = try await YouTubeMusic.search("radiohead let down", filter: .songs).rows
            .compactMap { if case .track(let s) = $0 { return s }; return nil }
        XCTAssertFalse(songs.isEmpty)
        let first = try XCTUnwrap(songs.first)

        let home = try await YouTubeMusic.home()
        XCTAssertFalse(home.shelves.isEmpty)

        let radio = try await YouTubeMusic.radio(first.videoId)
        XCTAssertGreaterThan(radio.count, 5)

        if let albumId = first.albumId {
            let album = try await YouTubeMusic.page(albumId)
            XCTAssertFalse(album.songs.isEmpty)
            XCTAssertNotNil(album.header)
        }
        if let artistId = first.artistId {
            let artist = try await YouTubeMusic.artist(artistId)
            XCTAssertFalse(artist.songs.isEmpty)
        }
        let suggestions = try await YouTubeMusic.suggestions("radioh")
        XCTAssertFalse(suggestions.isEmpty)

        let lyrics = await LyricsService.lyrics(for: first)
        XCTAssertNotNil(lyrics)
        print("LIVE OK:", first.title, "—", first.artist, "| home shelves:", home.shelves.count,
              "| radio:", radio.count, "| lyrics:", lyrics?.source ?? "none", lyrics?.isSynced == true ? "(synced)" : "")
    }
}
