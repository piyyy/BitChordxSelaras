import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
import Crypto

/// YouTube Music's own API, spoken as the WEB_REMIX client — a port of the Android/desktop
/// `Innertube`. Signed out it answers as a visitor; signed in, with the music.youtube.com cookie
/// jar, every request carries the SAPISIDHASH Google expects from the web player.
public actor Innertube {
    public static let shared = Innertube()

    static let musicBase = "https://music.youtube.com/youtubei/v1"
    static let musicOrigin = "https://music.youtube.com"
    static let clientVersion = "1.20250101.01.00"
    static let clientId = "67"
    static let userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15"

    private let session: URLSession
    private(set) var visitorData: String?
    /// The music.youtube.com cookie header, when signed in.
    private(set) var cookie: String?
    /// Which signed-in account in the jar, and which brand channel of it.
    private var authUser = "0"
    private var pageId: String?
    private var dataSyncId: String?

    public var language: String = Locale.current.language.languageCode?.identifier ?? "en"
    public var region: String = Locale.current.region?.identifier ?? "US"

    public init(session: URLSession = .shared) {
        self.session = session
    }

    public var isSignedIn: Bool { cookie.map { $0.contains("SAPISID") || $0.contains("PAPISID") } ?? false }

    public func setCookie(_ value: String?) {
        cookie = value?.isEmpty == true ? nil : value
        pageId = nil
        dataSyncId = nil
    }

    public func setLocale(language: String, region: String) {
        self.language = language
        self.region = region
    }

    /// Which brand channel requests are made as.
    public func selectChannel(pageId: String?, dataSyncId: String?, authUser: String?) {
        self.pageId = pageId
        self.dataSyncId = dataSyncId
        if let authUser { self.authUser = authUser }
    }

    // MARK: Endpoints

    public func browse(_ browseId: String, params: String? = nil) async throws -> JSONObject {
        var body: JSONObject = ["browseId": browseId]
        if let params { body["params"] = params }
        return try await post("browse", body: body)
    }

    public func browseContinuation(_ token: String) async throws -> JSONObject {
        try await post("browse", query: ["ctoken": token, "continuation": token, "type": "next"], body: ["continuation": token])
    }

    public func next(videoId: String, playlistId: String? = nil) async throws -> JSONObject {
        try await post("next", body: [
            "videoId": videoId,
            "playlistId": playlistId ?? "RDAMVM\(videoId)",
            "isAudioOnly": true,
        ])
    }

    public func search(_ query: String, params: String? = nil, anonymous: Bool = true) async throws -> JSONObject {
        var body: JSONObject = ["query": query]
        if let params { body["params"] = params }
        return try await post("search", body: body, anonymous: anonymous)
    }

    public func searchContinuation(_ token: String) async throws -> JSONObject {
        try await post("search", query: ["ctoken": token, "continuation": token, "type": "next"], body: ["continuation": token], anonymous: true)
    }

    public func searchSuggestions(_ input: String) async throws -> JSONObject {
        try await post("music/get_search_suggestions", body: ["input": input])
    }

    public func accountMenu() async throws -> JSONObject {
        try requireSession()
        return try await post("account/account_menu", body: [:])
    }

    public func rate(videoId: String, status: LikeStatus) async throws {
        try requireSession()
        let endpoint: String
        switch status {
        case .like: endpoint = "like/like"
        case .dislike: endpoint = "like/dislike"
        case .indifferent: endpoint = "like/removelike"
        }
        _ = try await post(endpoint, body: ["target": ["videoId": videoId]])
    }

    public func ratePlaylist(playlistId: String, saved: Bool) async throws {
        try requireSession()
        _ = try await post(saved ? "like/like" : "like/removelike", body: ["target": ["playlistId": playlistId]])
    }

    public func setSubscribed(channelId: String, subscribed: Bool) async throws {
        try requireSession()
        _ = try await post(subscribed ? "subscription/subscribe" : "subscription/unsubscribe", body: ["channelIds": [channelId]])
    }

    public func sendFeedback(_ token: String) async throws {
        try requireSession()
        _ = try await post("feedback", body: ["feedbackTokens": [token]])
    }

    public func createPlaylist(title: String, description: String = "", privacy: String = "PRIVATE", videoIds: [String] = []) async throws -> String? {
        try requireSession()
        var body: JSONObject = ["title": title, "description": description, "privacyStatus": privacy]
        if !videoIds.isEmpty { body["videoIds"] = videoIds }
        return try await post("playlist/create", body: body).s("playlistId")
    }

    public func deletePlaylist(_ playlistId: String) async throws {
        try requireSession()
        _ = try await post("playlist/delete", body: ["playlistId": Self.bare(playlistId)])
    }

    public func addToPlaylist(_ playlistId: String, videoIds: [String]) async throws {
        try requireSession()
        let actions = videoIds.map { ["action": "ACTION_ADD_VIDEO", "addedVideoId": $0] as JSONObject }
        _ = try await post("browse/edit_playlist", body: ["playlistId": Self.bare(playlistId), "actions": actions])
    }

    public func removeFromPlaylist(_ playlistId: String, entries: [(videoId: String, setVideoId: String)]) async throws {
        try requireSession()
        let actions = entries.map { ["action": "ACTION_REMOVE_VIDEO", "removedVideoId": $0.videoId, "setVideoId": $0.setVideoId] as JSONObject }
        _ = try await post("browse/edit_playlist", body: ["playlistId": Self.bare(playlistId), "actions": actions])
    }

    public func renamePlaylist(_ playlistId: String, title: String) async throws {
        try requireSession()
        _ = try await post("browse/edit_playlist", body: ["playlistId": Self.bare(playlistId), "actions": [["action": "ACTION_SET_PLAYLIST_NAME", "playlistName": title]]])
    }

    /// Playlist writes take the raw id, not the `VL…` browse id.
    static func bare(_ playlistId: String) -> String { playlistId.hasPrefix("VL") ? String(playlistId.dropFirst(2)) : playlistId }

    private func requireSession() throws {
        guard isSignedIn else { throw InnertubeError.notSignedIn }
    }

    // MARK: Plumbing

    func post(_ endpoint: String, query: [String: String] = [:], body extra: JSONObject, anonymous: Bool = false) async throws -> JSONObject {
        var components = URLComponents(string: "\(Self.musicBase)/\(endpoint)")!
        components.queryItems = [URLQueryItem(name: "prettyPrint", value: "false"), URLQueryItem(name: "hl", value: language)]
            + query.map { URLQueryItem(name: $0.key, value: $0.value) }
        var request = URLRequest(url: components.url!)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue(acceptLanguage, forHTTPHeaderField: "Accept-Language")
        request.setValue(Self.musicOrigin, forHTTPHeaderField: "X-Origin")
        request.setValue(Self.musicOrigin, forHTTPHeaderField: "Origin")
        request.setValue(Self.musicOrigin + "/", forHTTPHeaderField: "Referer")
        request.setValue(Self.clientId, forHTTPHeaderField: "X-YouTube-Client-Name")
        request.setValue(Self.clientVersion, forHTTPHeaderField: "X-YouTube-Client-Version")
        if let visitorData { request.setValue(visitorData, forHTTPHeaderField: "X-Goog-Visitor-Id") }
        let signed = !anonymous && cookie != nil
        if signed, let cookie {
            request.setValue(cookie, forHTTPHeaderField: "Cookie")
            request.setValue(authUser, forHTTPHeaderField: "X-Goog-AuthUser")
            if let pageId { request.setValue(pageId, forHTTPHeaderField: "X-Goog-PageId") }
            if let sapisid = Self.sapisid(from: cookie) {
                request.setValue(Self.sapisidHash(sapisid), forHTTPHeaderField: "Authorization")
            }
        }
        var client: JSONObject = ["clientName": "WEB_REMIX", "clientVersion": Self.clientVersion, "hl": language, "gl": region]
        if let visitorData { client["visitorData"] = visitorData }
        var user: JSONObject = ["lockedSafetyMode": false]
        if signed, let dataSyncId { user["onBehalfOfUser"] = dataSyncId }
        var body = extra
        body["context"] = ["client": client, "user": user, "request": ["useSsl": true]] as JSONObject
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        // On cellular a reset is weather, not information; HTTP errors are answers.
        var attempt = 0
        while true {
            do {
                let (data, response) = try await session.data(for: request)
                if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                    throw InnertubeError.http(http.statusCode, String(decoding: data.prefix(300), as: UTF8.self))
                }
                let json = try JSON.parse(data)
                if visitorData == nil, let v = json.o("responseContext").s("visitorData") { visitorData = v }
                return json
            } catch let error as URLError where attempt < 2 && error.code != .timedOut && error.code != .cancelled {
                attempt += 1
                try await Task.sleep(nanoseconds: UInt64(500_000_000 * attempt))
            }
        }
    }

    private var acceptLanguage: String { "\(language)-\(region),\(language);q=0.9,en;q=0.8" }

    /// The API-signing secret, under any of the three names Google sets it as.
    static func sapisid(from cookieHeader: String) -> String? {
        var jar: [String: String] = [:]
        for entry in cookieHeader.split(separator: ";") {
            let pair = entry.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            if pair.count == 2, !pair[0].isEmpty, !pair[1].isEmpty { jar[pair[0]] = pair[1] }
        }
        return ["SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID"].lazy.compactMap { jar[$0] }.first
    }

    static func sapisidHash(_ sapisid: String, origin: String = musicOrigin, now: Date = Date()) -> String {
        let timestamp = Int(now.timeIntervalSince1970)
        let digest = Insecure.SHA1.hash(data: Data("\(timestamp) \(sapisid) \(origin)".utf8))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        return "SAPISIDHASH \(timestamp)_\(hex)"
    }
}
