import Foundation

/// Null-safe navigation over `JSONSerialization` output, the way the Kotlin parser's `o`/`a`/`s`
/// helpers walk InnerTube responses: every step answers nil rather than throwing, because the
/// shape of these responses drifts and a missing branch is normal, not an error.
public typealias JSONObject = [String: Any]

extension Optional where Wrapped == Any {
    func o(_ key: String) -> JSONObject? { (self as? JSONObject)?[key] as? JSONObject }
    func a(_ key: String) -> [Any]? { (self as? JSONObject)?[key] as? [Any] }
    func s(_ key: String) -> String? {
        switch (self as? JSONObject)?[key] {
        case let v as String: return v
        case let v as NSNumber: return v.stringValue
        default: return nil
        }
    }
    /// All runs' text joined: how InnerTube spells a label.
    func runs() -> String {
        (a("runs") ?? []).map { Optional<Any>.some($0).s("text") ?? "" }.joined()
    }
    func firstRunText() -> String? {
        guard let first = a("runs")?.first else { return nil }
        return Optional<Any>.some(first).s("text")
    }
}

extension Dictionary where Key == String, Value == Any {
    func o(_ key: String) -> JSONObject? { self[key] as? JSONObject }
    func a(_ key: String) -> [Any]? { self[key] as? [Any] }
    func s(_ key: String) -> String? { Optional<Any>.some(self).s(key) }
    func runs() -> String { Optional<Any>.some(self).runs() }
    func firstRunText() -> String? { Optional<Any>.some(self).firstRunText() }
}

extension Optional where Wrapped == JSONObject {
    func o(_ key: String) -> JSONObject? { self?[key] as? JSONObject }
    func a(_ key: String) -> [Any]? { self?[key] as? [Any] }
    func s(_ key: String) -> String? { self.map { Optional<Any>.some($0).s(key) } ?? nil }
    func runs() -> String { self?.runs() ?? "" }
    func firstRunText() -> String? { self?.firstRunText() }
}

enum JSON {
    static func parse(_ data: Data) throws -> JSONObject {
        guard let object = try JSONSerialization.jsonObject(with: data) as? JSONObject else {
            throw InnertubeError.badResponse("not a JSON object")
        }
        return object
    }

    /// Depth-first collection of a named renderer, in document order.
    static func collect(_ root: Any?, _ name: String) -> [JSONObject] {
        var out: [JSONObject] = []
        func walk(_ node: Any) {
            if let object = node as? JSONObject {
                if let hit = object[name] as? JSONObject { out.append(hit) }
                for value in object.values { walk(value) }
            } else if let array = node as? [Any] {
                for value in array { walk(value) }
            }
        }
        if let root { walk(root) }
        return out
    }

    /// The first string under `key` anywhere in the subtree.
    static func findString(_ root: Any?, _ key: String) -> String? {
        if let object = root as? JSONObject {
            if let value = object[key] as? String { return value }
            for value in object.values { if let hit = findString(value, key) { return hit } }
        } else if let array = root as? [Any] {
            for value in array { if let hit = findString(value, key) { return hit } }
        }
        return nil
    }

    /// Whether `MUSIC_EXPLICIT_BADGE` appears anywhere under the node.
    static func hasExplicitBadge(_ node: Any?) -> Bool {
        if let s = node as? String { return s == "MUSIC_EXPLICIT_BADGE" }
        if let object = node as? JSONObject { return object.values.contains { hasExplicitBadge($0) } }
        if let array = node as? [Any] { return array.contains { hasExplicitBadge($0) } }
        return false
    }
}

extension Array where Element == Any {
    /// The last thumbnail is the largest; see `Artwork` for resizing.
    var bestThumbnail: String? { Optional<Any>.some(last as Any).s("url") }

    /// Catalogue art is square; a music-video upload's is widescreen.
    var isNotSquare: Bool {
        guard let last else { return false }
        let o = Optional<Any>.some(last)
        guard let w = o.s("width").flatMap(Double.init), let h = o.s("height").flatMap(Double.init), w > 0, h > 0 else { return false }
        let ratio = w / h
        return !(0.85...1.15).contains(ratio)
    }
}

public enum InnertubeError: Error, LocalizedError {
    case badResponse(String)
    case http(Int, String)
    case notSignedIn
    case unplayable(String)

    public var errorDescription: String? {
        switch self {
        case .badResponse(let m): return "Unexpected response: \(m)"
        case .http(let code, let m): return "HTTP \(code): \(m)"
        case .notSignedIn: return "Sign in to YouTube Music to do that"
        case .unplayable(let m): return m
        }
    }
}
