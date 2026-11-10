import AVFoundation
import AVKit
import BitChordCore
import BitChordKit
import MediaPlayer
import SwiftUI
import UIKit

/// The native half of playback, behind the Kotlin UI's `PlaybackBridge`: YouTubeKit resolves the
/// stream, AVPlayer plays it, Selaras runs on every buffer through an MTAudioProcessingTap, and the
/// lock screen / Control Center / AirPods buttons drive the Kotlin queue. Also presents what is
/// SwiftUI on iOS: the Selaras page, Google sign-in, toasts and the share sheet.
final class NativeAudio: NSObject, ObservableObject, PlaybackBridge {
    static let shared = NativeAudio()

    @Published var showSelaras = false
    @Published var showSignIn = false
    @Published var toastText: String?

    private let player = AVPlayer()
    private var listener: PlaybackListener?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var loadTask: Task<Void, Never>?
    private var artworkTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?
    private var loading = false
    private var loadedId: String?
    private var meta: (title: String, artist: String, album: String?, artwork: String?) = ("", "", nil, nil)
    private let volumeView = MPVolumeView(frame: CGRect(x: -2000, y: -2000, width: 1, height: 1))
    private let routePicker = AVRoutePickerView(frame: CGRect(x: -2000, y: -2000, width: 1, height: 1))

    override init() {
        super.init()
        player.automaticallyWaitsToMinimizeStalling = true
        configureSession()
        configureRemote()
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.25, preferredTimescale: 600), queue: .main) { [weak self] _ in
            self?.report()
        }
        updateRoute()
    }

    // MARK: PlaybackBridge

    func setListener(listener: PlaybackListener) {
        self.listener = listener
        listener.onOutputChanged(name: currentOutputName())
    }

    func load(videoId: String, title: String, artist: String, album: String?, artworkUrl: String?, startMs: Int64, playWhenReady: Bool) {
        loadTask?.cancel()
        player.pause()
        loading = true
        loadedId = videoId
        meta = (title, artist, album, artworkUrl)
        updateNowPlaying(duration: 0, position: 0, playing: false)
        loadArtwork(artworkUrl, for: videoId)
        report()
        loadTask = Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let url = try await StreamResolver.shared.url(for: videoId)
                try Task.checkCancellation()
                let item = AVPlayerItem(asset: AVURLAsset(url: url))
                item.preferredForwardBufferDuration = 30
                item.audioMix = await SelarasTap.audioMix(for: item)
                try Task.checkCancellation()
                self.observeEnd(of: item)
                self.player.replaceCurrentItem(with: item)
                if startMs > 0 {
                    await self.player.seek(to: CMTime(value: startMs, timescale: 1000))
                }
                self.loading = false
                if playWhenReady { self.play() }
                self.report()
            } catch is CancellationError {
            } catch {
                guard !Task.isCancelled, self.loadedId == videoId else { return }
                self.loading = false
                self.listener?.onError(message: "Couldn't play “\(title)”: \(error.localizedDescription)")
                self.report()
                // A track that will not play should not stop the music.
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                if !Task.isCancelled, self.loadedId == videoId { self.listener?.onEnded() }
            }
        }
    }

    func prefetch(videoId: String) {
        Task { await StreamResolver.shared.prefetch(videoId) }
    }

    func play() {
        try? AVAudioSession.sharedInstance().setActive(true)
        player.play()
        report()
    }

    func pause() {
        player.pause()
        report()
    }

    func seekTo(positionMs: Int64) {
        player.seek(to: CMTime(value: positionMs, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            DispatchQueue.main.async { self?.report() }
        }
    }

    func stop() {
        loadTask?.cancel()
        player.replaceCurrentItem(with: nil)
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        report()
    }

    func setQueueButtons(hasPrevious: Bool, hasNext: Bool) {
        let center = MPRemoteCommandCenter.shared()
        center.nextTrackCommand.isEnabled = hasNext
        center.previousTrackCommand.isEnabled = true
    }

    func openSelaras() { DispatchQueue.main.async { self.showSelaras = true } }
    func openSignIn() { DispatchQueue.main.async { self.showSignIn = true } }

    func signOut() {
        Task { @MainActor in await AuthModel.shared.signOut() }
    }

    func storedCookie() -> String? { Keychain.read("ytm-cookie") }

    func toast(message: String) {
        DispatchQueue.main.async {
            withAnimation { self.toastText = message }
            self.toastTask?.cancel()
            self.toastTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                if !Task.isCancelled { withAnimation { self.toastText = nil } }
            }
        }
    }

    func share(text: String) {
        DispatchQueue.main.async {
            guard let root = Self.topController() else { return }
            let sheet = UIActivityViewController(activityItems: [URL(string: text) ?? text], applicationActivities: nil)
            sheet.popoverPresentationController?.sourceView = root.view
            root.present(sheet, animated: true)
        }
    }

    func currentOutputName() -> String {
        guard let output = AVAudioSession.sharedInstance().currentRoute.outputs.first else { return UIDevice.current.model }
        return output.portType == .builtInSpeaker || output.portType == .builtInReceiver ? UIDevice.current.model : output.portName
    }

    func openRoutePicker() {
        DispatchQueue.main.async {
            if self.routePicker.superview == nil { Self.topController()?.view.addSubview(self.routePicker) }
            (self.routePicker.subviews.first { $0 is UIButton } as? UIButton)?.sendActions(for: .touchUpInside)
        }
    }

    func systemVolume() -> Float { AVAudioSession.sharedInstance().outputVolume }

    func setSystemVolume(level: Float) {
        DispatchQueue.main.async {
            if self.volumeView.superview == nil { Self.topController()?.view.addSubview(self.volumeView) }
            (self.volumeView.subviews.first { $0 is UISlider } as? UISlider)?.value = level
        }
    }

    // MARK: Sign-in

    func cookieChanged(_ cookie: String?) {
        showSignIn = false
        listener?.onCookie(cookie: cookie)
    }

    // MARK: State to Kotlin

    private func report() {
        let duration = player.currentItem?.duration.seconds ?? 0
        let position = player.currentTime().seconds
        let playing = player.timeControlStatus == .playing || (player.rate > 0 && player.timeControlStatus != .paused)
        let buffering = loading || player.timeControlStatus == .waitingToPlayAtSpecifiedRate
        listener?.onState(
            isPlaying: playing,
            isLoading: buffering,
            positionMs: position.isFinite ? Int64(position * 1000) : 0,
            durationMs: duration.isFinite ? Int64(duration * 1000) : 0
        )
        updateNowPlaying(duration: duration.isFinite ? duration : 0, position: position.isFinite ? position : 0, playing: playing)
    }

    private func observeEnd(of item: AVPlayerItem) {
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            self?.listener?.onEnded()
        }
    }

    // MARK: Session, route, Selaras device

    private func configureSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, policy: .longFormAudio)
        NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] note in
            guard let self else { return }
            self.updateRoute()
            // Headphones pulled out: pause, as every music app does.
            if let raw = note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
               AVAudioSession.RouteChangeReason(rawValue: raw) == .oldDeviceUnavailable {
                self.pause()
            }
        }
        NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            guard let self, let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
            if type == .ended, let opts = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt,
               AVAudioSession.InterruptionOptions(rawValue: opts).contains(.shouldResume) {
                self.play()
            }
            self.report()
        }
    }

    /// Selaras keeps one EQ per output: speaker, wired, USB, each Bluetooth or AirPlay device.
    private func updateRoute() {
        guard let output = AVAudioSession.sharedInstance().currentRoute.outputs.first else { return }
        let name = output.portName
        let key: String
        switch output.portType {
        case .builtInSpeaker, .builtInReceiver: key = "speaker"
        case .headphones, .lineOut: key = "wired"
        case .bluetoothA2DP, .bluetoothLE, .bluetoothHFP: key = "bt:\(name)"
        case .usbAudio: key = "usb:\(name)"
        case .airPlay: key = "airplay:\(name)"
        case .HDMI: key = "hdmi"
        case .carAudio: key = "car:\(name)"
        default: key = "out:\(name)"
        }
        SelarasStore.shared.setDevice(key: key, name: output.portType == .builtInSpeaker ? "iPhone speaker" : name)
        listener?.onOutputChanged(name: currentOutputName())
    }

    // MARK: Lock screen

    private func configureRemote() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in self?.play(); return .success }
        center.pauseCommand.addTarget { [weak self] _ in self?.pause(); return .success }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            if self.player.timeControlStatus == .paused { self.play() } else { self.pause() }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in self?.listener?.onRemoteNext(); return .success }
        center.previousTrackCommand.addTarget { [weak self] _ in self?.listener?.onRemotePrevious(); return .success }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let e = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.seekTo(positionMs: Int64(e.positionTime * 1000))
            return .success
        }
    }

    private var lastInfoSecond = -1

    private func updateNowPlaying(duration: Double, position: Double, playing: Bool) {
        guard loadedId != nil else { return }
        // Once a second is plenty; the system interpolates with the rate.
        let second = Int(position)
        if second == lastInfoSecond, MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] as? Double == (playing ? 1.0 : 0.0) { return }
        lastInfoSecond = second
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        if info[MPMediaItemPropertyTitle] as? String != meta.title { info[MPMediaItemPropertyArtwork] = nil }
        info[MPMediaItemPropertyTitle] = meta.title
        info[MPMediaItemPropertyArtist] = meta.artist
        info[MPMediaItemPropertyAlbumTitle] = meta.album
        info[MPMediaItemPropertyPlaybackDuration] = duration
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = position
        info[MPNowPlayingInfoPropertyPlaybackRate] = playing ? 1.0 : 0.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func loadArtwork(_ urlString: String?, for videoId: String) {
        artworkTask?.cancel()
        guard let urlString, let url = URL(string: urlString) else { return }
        artworkTask = Task { @MainActor [weak self] in
            guard let (data, _) = try? await URLSession.shared.data(from: url), let image = UIImage(data: data),
                  !Task.isCancelled, self?.loadedId == videoId else { return }
            var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
    }

    static func topController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
