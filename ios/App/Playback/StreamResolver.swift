import BitChordCore
import Foundation
import YouTubeKit

/// The audio stream for a track. YouTubeKit does the extraction (player clients, signature
/// deciphering via JavaScriptCore); this picks the best stream AVPlayer can play natively — AAC in
/// MP4 — and remembers it until shortly before YouTube's URL expires.
actor StreamResolver {
    static let shared = StreamResolver()

    private struct Entry { let url: URL; let expires: Date }
    private var cache: [String: Entry] = [:]
    private var inFlight: [String: Task<URL, Error>] = [:]

    func url(for videoId: String) async throws -> URL {
        if let hit = cache[videoId], hit.expires > Date() { return hit.url }
        if let running = inFlight[videoId] { return try await running.value }
        let task = Task { try await Self.resolve(videoId) }
        inFlight[videoId] = task
        defer { inFlight[videoId] = nil }
        let url = try await task.value
        cache[videoId] = Entry(url: url, expires: Self.expiry(of: url))
        return url
    }

    /// Resolves ahead of time, so the next track starts without a wait.
    func prefetch(_ videoId: String) {
        Task { _ = try? await url(for: videoId) }
    }

    private static func resolve(_ videoId: String) async throws -> URL {
        let streams = try await YouTube(videoID: videoId).streams
        let audio = streams.filterAudioOnly()
        let native = audio.filter { $0.isNativelyPlayable }
        guard let best = (native.isEmpty ? audio : native).highestAudioBitrateStream() else {
            throw InnertubeError.unplayable("No playable audio stream for this track")
        }
        return best.url
    }

    /// googlevideo URLs say when they stop working; leave ten minutes of margin.
    private static func expiry(of url: URL) -> Date {
        let expire = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?
            .first { $0.name == "expire" }?.value.flatMap(TimeInterval.init)
        return expire.map { Date(timeIntervalSince1970: $0 - 600) } ?? Date().addingTimeInterval(3 * 3600)
    }
}
