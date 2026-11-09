import BitChordCore
import Security
import SwiftUI
import WebKit

/// The YouTube Music sign-in: the music.youtube.com cookie jar, kept in the Keychain and handed to
/// `Innertube` so library, likes and history work as the account.
@MainActor
final class AuthModel: ObservableObject {
    static let shared = AuthModel()

    @Published private(set) var signedIn = false
    @Published private(set) var account: Account?

    private init() {
        if let cookie = Keychain.read("ytm-cookie") {
            signedIn = true
            Task {
                await Innertube.shared.setCookie(cookie)
                await refreshAccount()
            }
        }
    }

    /// Reads the cookies the sign-in web view collected and keeps them.
    func completeSignIn(from store: WKHTTPCookieStore) async {
        let cookies = await store.allCookies()
        let relevant = cookies.filter { $0.domain.hasSuffix("youtube.com") }
        let header = relevant.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
        guard header.contains("SAPISID") || header.contains("PAPISID") else { return }
        Keychain.write("ytm-cookie", header)
        await Innertube.shared.setCookie(header)
        signedIn = true
        await refreshAccount()
    }

    func signOut() async {
        Keychain.delete("ytm-cookie")
        await Innertube.shared.setCookie(nil)
        let store = WKWebsiteDataStore.default()
        let records = await store.dataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes())
        await store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), for: records.filter {
            $0.displayName.contains("google") || $0.displayName.contains("youtube")
        })
        signedIn = false
        account = nil
    }

    func refreshAccount() async {
        account = try? await YouTubeMusic.account()
    }
}

/// Google's sign-in page, finishing once it lands back on music.youtube.com.
struct SignInView: UIViewRepresentable {
    var onDone: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onDone: onDone) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        let view = WKWebView(frame: .zero, configuration: config)
        view.navigationDelegate = context.coordinator
        view.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
        let url = URL(string: "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F")!
        view.load(URLRequest(url: url))
        return view
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        let onDone: () -> Void
        private var finished = false
        init(onDone: @escaping () -> Void) { self.onDone = onDone }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            guard !finished, webView.url?.host == "music.youtube.com" else { return }
            let store = webView.configuration.websiteDataStore.httpCookieStore
            Task { @MainActor in
                await AuthModel.shared.completeSignIn(from: store)
                if AuthModel.shared.signedIn {
                    self.finished = true
                    self.onDone()
                }
            }
        }
    }
}

/// The few Keychain calls the sign-in needs.
enum Keychain {
    static func write(_ key: String, _ value: String) {
        delete(key)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    static func read(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(_ key: String) {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key] as CFDictionary)
    }
}
