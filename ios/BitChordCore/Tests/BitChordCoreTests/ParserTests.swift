import XCTest
@testable import BitChordCore

/// The parser against real responses captured from music.youtube.com (web/fixtures).
final class ParserTests: XCTestCase {
    static let fixtures = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("web/fixtures")

    func load(_ name: String) throws -> JSONObject {
        let url = Self.fixtures.appendingPathComponent(name)
        try XCTSkipUnless(FileManager.default.fileExists(atPath: url.path), "fixture \(name) missing")
        return try JSON.parse(Data(contentsOf: url))
    }

    func testSearchSongs() throws {
        let page = InnertubeParser.parseSearchPage(try load("innertube/search-songs.json"))
        let songs = page.rows.compactMap { if case .track(let s) = $0 { return s }; return nil }
        XCTAssertGreaterThan(songs.count, 5)
        for song in songs {
            XCTAssertEqual(song.videoId.count, 11, song.title)
            XCTAssertFalse(song.title.isEmpty)
            XCTAssertNotEqual(song.artist, "Unknown artist", song.title)
            XCTAssertFalse(song.isVideo)
        }
        XCTAssertTrue(songs.contains { $0.durationText != nil })
    }

    func testSearchAllHasTopResultAndBrowseRows() throws {
        let page = InnertubeParser.parseSearchPage(try load("innertube/search-all.json"))
        XCTAssertFalse(page.rows.isEmpty)
        XCTAssertTrue(page.rows.contains { if case .browse = $0 { return true }; return false })
        XCTAssertEqual(Set(page.rows.map(\.id)).count, page.rows.count, "rows must be unique")
    }

    func testSearchAlbums() throws {
        let page = InnertubeParser.parseSearchPage(try load("innertube/search-albums.json"))
        let albums = page.rows.compactMap { if case .browse(let b) = $0 { return b }; return nil }
        XCTAssertGreaterThan(albums.count, 3)
        XCTAssertTrue(albums.allSatisfy { $0.type == .album })
    }

    func testSuggestions() throws {
        let suggestions = InnertubeParser.parseSearchSuggestions(try load("innertube/search-suggestions.json"))
        XCTAssertFalse(suggestions.isEmpty)
    }

    func testHome() throws {
        let response = try load("innertube/browse-home.json")
        let shelves = InnertubeParser.parseHome(response)
        // The first page holds two carousels (and a taste-builder, which is skipped); the rest come
        // through the continuation, which must be found.
        XCTAssertGreaterThanOrEqual(shelves.count, 2)
        XCTAssertNotNil(InnertubeParser.continuationToken(response))
        XCTAssertTrue(shelves.allSatisfy { !$0.items.isEmpty && !$0.title.isEmpty })
    }

    func testMoodsAndGenres() throws {
        let sections = InnertubeParser.parseMoodAndGenres(try load("innertube/browse-moods-and-genres.json"))
        XCTAssertFalse(sections.isEmpty)
        XCTAssertTrue(sections.allSatisfy { !$0.items.isEmpty })
    }

    func testAlbumRowsAreCreditedByTheHeader() throws {
        let response = try load("innertube/browse-album.json")
        let header = InnertubeParser.parseBrowseHeader(response)
        XCTAssertNotNil(header)
        let songs = InnertubeParser.collectSongsDeep(response)
        XCTAssertGreaterThan(songs.count, 3)
        XCTAssertTrue(songs.allSatisfy { $0.artist != "Unknown artist" }, songs.map(\.artist).joined(separator: ","))
    }

    func testPlaylist() throws {
        let response = try load("innertube/browse-playlist.json")
        let songs = InnertubeParser.parsePlaylistShelf(response)?.songs ?? InnertubeParser.collectSongsDeep(response)
        XCTAssertGreaterThan(songs.count, 5)
        XCTAssertNotNil(InnertubeParser.parseBrowseHeader(response))
    }

    func testArtist() throws {
        let page = InnertubeParser.parseArtistPage(try load("innertube/browse-artist.json"))
        XCTAssertNotNil(page.name)
        XCTAssertFalse(page.songs.isEmpty)
        XCTAssertFalse(page.shelves.isEmpty)
        XCTAssertTrue(page.songs.allSatisfy { $0.artist != "Unknown artist" })
    }

    func testRadioQueue() throws {
        let queue = InnertubeParser.parseWatchQueue(try load("innertube/next-radio.json"))
        XCTAssertGreaterThan(queue.count, 5)
        XCTAssertTrue(queue.allSatisfy { !$0.artist.isEmpty })
    }

    func testLRC() throws {
        let lines = LyricsService.parseLRC("[00:01.50] One\n[00:03.00][00:10.00] Two\nno stamp\n[01:00.25]Three")
        XCTAssertEqual(lines.map(\.text), ["One", "Two", "Two", "Three"])
        XCTAssertEqual(lines.last?.time ?? 0, 60.25, accuracy: 0.001)
        let lyrics = Lyrics(synced: lines, plain: "", source: "t")
        XCTAssertEqual(lyrics.lineIndex(at: 3.5), 1)
        XCTAssertNil(lyrics.lineIndex(at: 0.5))
    }

    func testLrclibFixture() throws {
        let object = try load("lyrics/lrclib-get.json")
        let lyrics = LyricsService.lyrics(from: object)
        XCTAssertNotNil(lyrics)
        XCTAssertTrue(lyrics?.isSynced ?? false)
    }

    func testSapisidHash() {
        let cookie = "PREF=x; __Secure-3PAPISID=abc/def; SID=1"
        XCTAssertEqual(Innertube.sapisid(from: cookie), "abc/def")
        let hash = Innertube.sapisidHash("abc", now: Date(timeIntervalSince1970: 1000))
        XCTAssertTrue(hash.hasPrefix("SAPISIDHASH 1000_"))
        XCTAssertEqual(hash.count, "SAPISIDHASH 1000_".count + 40)
    }

    func testArtwork() {
        let url = Artwork.url("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj", size: 544)
        XCTAssertEqual(url?.absoluteString, "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj")
    }
}
