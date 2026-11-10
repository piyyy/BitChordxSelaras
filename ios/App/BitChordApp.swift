import BitChordCore
import BitChordKit
import SwiftUI

/// BitChord on iOS: the Android app's own Compose UI (Kotlin, `BitChordKit`) full screen, with the
/// native pieces around it — audio and Selaras (`NativeAudio`), Google sign-in, toasts.
@main
struct BitChordApp: App {
    @StateObject private var audio = NativeAudio.shared
    @StateObject private var selaras = SelarasObservable()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(audio)
                .environmentObject(selaras)
                .preferredColorScheme(nil)
        }
    }
}

/// Bridges `SelarasStore` (BitChordCore, framework-free) to SwiftUI.
@MainActor
final class SelarasObservable: ObservableObject {
    let store = SelarasStore.shared
    @Published private(set) var revision = 0

    init() {
        store.onChange = { [weak self] in self?.revision += 1 }
    }

    var state: SelarasState { store.state }
    var settings: SelarasSettings { store.settings }
}

struct RootView: View {
    @EnvironmentObject var audio: NativeAudio

    var body: some View {
        ComposeHost()
            .ignoresSafeArea()
            .sheet(isPresented: $audio.showSelaras) {
                NavigationStack {
                    ScrollView { SelarasView().padding(.horizontal) }
                        .navigationTitle("Selaras")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .confirmationAction) { Button("Done") { audio.showSelaras = false } }
                        }
                }
                .presentationDragIndicator(.visible)
            }
            .sheet(isPresented: $audio.showSignIn) {
                NavigationStack {
                    SignInView { audio.showSignIn = false }
                        .ignoresSafeArea(edges: .bottom)
                        .navigationTitle("Sign in to YouTube Music")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) { Button("Cancel") { audio.showSignIn = false } }
                        }
                }
            }
            .overlay(alignment: .top) {
                if let text = audio.toastText {
                    Text(text)
                        .font(.subheadline.weight(.medium))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(.regularMaterial, in: Capsule())
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
    }
}

/// The Kotlin UI's view controller.
struct ComposeHost: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(bridge: NativeAudio.shared)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
