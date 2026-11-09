import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// One timed line of synced lyrics.
public struct LyricLine: Hashable, Sendable, Identifiable {
    public var time: TimeInterval
    public var text: String
    public var id: String { "\(time)-\(text)" }
}

public struct Lyrics: Sendable {
    /// Timed lines when the source had them; empty for plain lyrics.
    public var synced: [LyricLine]
    public var plain: String
    public var source: String

    public var isSynced: Bool { !synced.isEmpty }

    /// The line playing at `time`.
    public func lineIndex(at time: TimeInterval) -> Int? {
        guard !synced.isEmpty, time >= synced[0].time else { return nil }
        var lo = 0, hi = synced.count - 1
        while lo < hi {
            let mid = (lo + hi + 1) / 2
            if synced[mid].time <= time { lo = mid } else { hi = mid - 1 }
        }
        return lo
    }
}

public enum LyricsService {
    /// Synced lyrics from LRCLIB, then YouTube Music's own plain lyrics.
    public static func lyrics(for song: Song) async -> Lyrics? {
        if let found = try? await lrclib(song) { return found }
        if let yt = try? await YouTubeMusic.lyrics(song.videoId) {
            return Lyrics(synced: [], plain: yt.text, source: yt.source ?? "YouTube Music")
        }
        return nil
    }

    static func lrclib(_ song: Song) async throws -> Lyrics? {
        var components = URLComponents(string: "https://lrclib.net/api/get")!
        var items = [
            URLQueryItem(name: "track_name", value: song.title),
            URLQueryItem(name: "artist_name", value: song.artists.first?.name ?? song.artist),
        ]
        if let album = song.albumName { items.append(URLQueryItem(name: "album_name", value: album)) }
        if let duration = song.durationSeconds { items.append(URLQueryItem(name: "duration", value: String(duration))) }
        components.queryItems = items
        if let hit = try await fetch(components.url!) { return hit }
        // No exact match: search, and take the closest runtime.
        var search = URLComponents(string: "https://lrclib.net/api/search")!
        search.queryItems = [URLQueryItem(name: "q", value: "\(song.title) \(song.artist)")]
        var request = URLRequest(url: search.url!)
        request.setValue("BitChord iOS (https://github.com/kushagrasinghx/BitChord)", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await URLSession.shared.data(for: request)
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [JSONObject] else { return nil }
        let target = Double(song.durationSeconds ?? 0)
        let best = rows
            .filter { ($0["syncedLyrics"] as? String)?.isEmpty == false || ($0["plainLyrics"] as? String)?.isEmpty == false }
            .min { abs(((($0["duration"] as? NSNumber)?.doubleValue) ?? 0) - target) < abs(((($1["duration"] as? NSNumber)?.doubleValue) ?? 0) - target) }
        return best.flatMap(Self.lyrics(from:))
    }

    private static func fetch(_ url: URL) async throws -> Lyrics? {
        var request = URLRequest(url: url)
        request.setValue("BitChord iOS (https://github.com/kushagrasinghx/BitChord)", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200,
              let object = try JSONSerialization.jsonObject(with: data) as? JSONObject else { return nil }
        return lyrics(from: object)
    }

    static func lyrics(from object: JSONObject) -> Lyrics? {
        let synced = parseLRC(object["syncedLyrics"] as? String ?? "")
        let plain = object["plainLyrics"] as? String ?? synced.map(\.text).joined(separator: "\n")
        if synced.isEmpty && plain.isBlank { return nil }
        return Lyrics(synced: synced, plain: plain, source: "LRCLIB")
    }

    /// `[mm:ss.xx] text` lines; several stamps on one line each get the text.
    public static func parseLRC(_ text: String) -> [LyricLine] {
        let stamp = try! NSRegularExpression(pattern: #"\[(\d+):(\d+(?:\.\d+)?)\]"#)
        var out: [LyricLine] = []
        for raw in text.split(separator: "\n", omittingEmptySubsequences: false) {
            let line = String(raw)
            let ns = line as NSString
            let matches = stamp.matches(in: line, range: NSRange(location: 0, length: ns.length))
            guard let last = matches.last else { continue }
            let body = ns.substring(from: last.range.location + last.range.length).trimmingCharacters(in: .whitespaces)
            for m in matches {
                let minutes = Double(ns.substring(with: m.range(at: 1))) ?? 0
                let seconds = Double(ns.substring(with: m.range(at: 2))) ?? 0
                out.append(LyricLine(time: minutes * 60 + seconds, text: body))
            }
        }
        return out.sorted { $0.time < $1.time }
    }
}
