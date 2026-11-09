import AVFoundation
import BitChordCore
import MediaPlayer
import SwiftUI
import UIKit

enum RepeatMode: String, CaseIterable { case off, all, one }

/// Playback for the whole app: the queue, AVPlayer, Selaras on its audio, the lock screen and
/// Control Center, AutoPlay radio past the end of the queue, and per-output EQ as routes change.
@MainActor
final class PlayerModel: ObservableObject {
    static let shared = PlayerModel()

    @Published private(set) var queue: [Song] = []
    @Published private(set) var index: Int = 0
    @Published private(set) var isPlaying = false
    @Published private(set) var isLoading = false
    @Published private(set) var currentTime: TimeInterval = 0
    @Published private(set) var duration: TimeInterval = 0
    @Published var error: String?
    @Published var shuffle = false
    @Published var repeatMode: RepeatMode = .off
    @Published var autoplay: Bool = UserDefaults.standard.object(forKey: "autoplay") as? Bool ?? true {
        didSet { UserDefaults.standard.set(autoplay, forKey: "autoplay") }
    }
    @Published private(set) var lyrics: Lyrics?
    @Published private(set) var outputName = "iPhone"

    var current: Song? { queue.indices.contains(index) ? queue[index] : nil }
    var upNext: [Song] { queue.indices.contains(index + 1) ? Array(queue[(index + 1)...]) : [] }

    private let player = AVPlayer()
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var loadTask: Task<Void, Never>?
    private var unshuffled: [Song]?
    private var radioSeed: String?
    private var artworkTask: Task<Void, Never>?

    private init() {
        player.automaticallyWaitsToMinimizeStalling = true
        configureSession()
        configureRemote()
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.25, preferredTimescale: 600), queue: .main) { [weak self] time in
            MainActor.assumeIsolated {
                guard let self else { return }
                self.currentTime = time.seconds.isFinite ? time.seconds : 0
                if let d = self.player.currentItem?.duration.seconds, d.isFinite, d > 0 { self.duration = d }
                self.isPlaying = self.player.timeControlStatus == .playing || self.player.rate > 0
            }
        }
        updateRoute()
    }

    // MARK: Queue

    /// Plays `songs` from `index`, replacing the queue.
    func play(_ songs: [Song], startAt start: Int = 0, radio: Bool = false) {
        guard songs.indices.contains(start) else { return }
        queue = songs
        index = start
        unshuffled = nil
        shuffle = false
        radioSeed = radio ? songs[start].videoId : nil
        load()
    }

    /// One track now, with its radio mix after it.
    func playRadio(_ song: Song) {
        play([song], radio: true)
        extendWithRadio(from: song)
    }

    func playNext(_ song: Song) {
        if queue.isEmpty { return play([song]) }
        queue.insert(song, at: min(index + 1, queue.count))
    }

    func addToQueue(_ songs: [Song]) {
        if queue.isEmpty { return play(songs) }
        queue.append(contentsOf: songs)
    }

    func remove(at offsets: IndexSet) {
        // Offsets are into `upNext`.
        let absolute = IndexSet(offsets.map { $0 + index + 1 })
        queue.remove(atOffsets: absolute)
    }

    func move(from source: IndexSet, to destination: Int) {
        var next = upNext
        next.move(fromOffsets: source, toOffset: destination)
        queue = Array(queue[...index]) + next
    }

    func jump(to song: Song) {
        guard let i = queue.firstIndex(of: song) else { return }
        index = i
        load()
    }

    func toggleShuffle() {
        shuffle.toggle()
        guard let current else { return }
        if shuffle {
            unshuffled = queue
            var rest = queue
            rest.remove(at: index)
            queue = [current] + rest.shuffled()
            index = 0
        } else if let original = unshuffled {
            queue = original
            index = original.firstIndex(of: current) ?? 0
            unshuffled = nil
        }
    }

    func cycleRepeat() {
        let all = RepeatMode.allCases
        repeatMode = all[(all.firstIndex(of: repeatMode)! + 1) % all.count]
    }

    // MARK: Transport

    func togglePlay() {
        if player.currentItem == nil, current != nil { return load() }
        if isPlaying { pause() } else { resume() }
    }

    func resume() {
        try? AVAudioSession.sharedInstance().setActive(true)
        player.play()
        isPlaying = true
        updateNowPlaying()
    }

    func pause() {
        player.pause()
        isPlaying = false
        updateNowPlaying()
    }

    func next() {
        if repeatMode == .one { repeatMode = .all }
        advance(userInitiated: true)
    }

    func previous() {
        if currentTime > 3 || index == 0 {
            seek(to: 0)
        } else {
            index -= 1
            load()
        }
    }

    func seek(to seconds: TimeInterval) {
        player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        currentTime = seconds
        updateNowPlaying()
    }

    private func advance(userInitiated: Bool) {
        if !userInitiated && repeatMode == .one {
            seek(to: 0)
            return resume()
        }
        if index + 1 < queue.count {
            index += 1
            load()
        } else if repeatMode == .all, !queue.isEmpty {
            index = 0
            load()
        } else if autoplay, let last = current {
            Task {
                await extendWithRadioNow(from: last)
                if index + 1 < queue.count {
                    index += 1
                    load()
                } else {
                    pause()
                }
            }
        } else {
            pause()
            seek(to: 0)
        }
    }

    // MARK: Loading

    private func load() {
        guard let song = current else { return }
        loadTask?.cancel()
        player.pause()
        isLoading = true
        error = nil
        currentTime = 0
        duration = TimeInterval(song.durationSeconds ?? 0)
        lyrics = nil
        updateNowPlaying()
        loadTask = Task { [weak self] in
            guard let self else { return }
            do {
                let url = try await StreamResolver.shared.url(for: song.videoId)
                try Task.checkCancellation()
                let asset = AVURLAsset(url: url)
                let item = AVPlayerItem(asset: asset)
                item.preferredForwardBufferDuration = 30
                item.audioMix = await SelarasTap.audioMix(for: item)
                try Task.checkCancellation()
                self.observeEnd(of: item)
                self.player.replaceCurrentItem(with: item)
                self.resume()
                self.isLoading = false
                self.prefetchNext()
                self.loadLyrics(for: song)
                if self.upNext.count < 3, self.autoplay { self.extendWithRadio(from: song) }
            } catch is CancellationError {
            } catch {
                guard !Task.isCancelled else { return }
                self.isLoading = false
                self.error = error.localizedDescription
                // A track that will not play should not stop the music.
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                if !Task.isCancelled, self.current == song { self.advance(userInitiated: false) }
            }
        }
    }

    private func observeEnd(of item: AVPlayerItem) {
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.advance(userInitiated: false) }
        }
    }

    private func prefetchNext() {
        guard let next = upNext.first else { return }
        Task { await StreamResolver.shared.prefetch(next.videoId) }
    }

    private func loadLyrics(for song: Song) {
        Task {
            let found = await LyricsService.lyrics(for: song)
            if self.current == song { self.lyrics = found }
        }
    }

    private func extendWithRadio(from song: Song) {
        Task { await extendWithRadioNow(from: song) }
    }

    private func extendWithRadioNow(from song: Song) async {
        guard let mix = try? await YouTubeMusic.radio(song.videoId) else { return }
        let known = Set(queue.map(\.videoId))
        let fresh = mix.filter { !known.contains($0.videoId) && !$0.isVideo }
        queue.append(contentsOf: fresh)
    }

    // MARK: Audio session, routes and the lock screen

    private func configureSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, policy: .longFormAudio)
        NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] note in
            MainActor.assumeIsolated {
                guard let self else { return }
                self.updateRoute()
                // Headphones pulled out: pause, as every music app does.
                if let raw = note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
                   AVAudioSession.RouteChangeReason(rawValue: raw) == .oldDeviceUnavailable {
                    self.pause()
                }
            }
        }
        NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            MainActor.assumeIsolated {
                guard let self, let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                      let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
                if type == .began {
                    self.isPlaying = false
                } else if let opts = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt,
                          AVAudioSession.InterruptionOptions(rawValue: opts).contains(.shouldResume) {
                    self.resume()
                }
            }
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
        outputName = output.portType == .builtInSpeaker ? "iPhone speaker" : name
        SelarasStore.shared.setDevice(key: key, name: outputName)
    }

    private func configureRemote() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in self?.resume(); return .success }
        center.pauseCommand.addTarget { [weak self] _ in self?.pause(); return .success }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in self?.togglePlay(); return .success }
        center.nextTrackCommand.addTarget { [weak self] _ in self?.next(); return .success }
        center.previousTrackCommand.addTarget { [weak self] _ in self?.previous(); return .success }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let e = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.seek(to: e.positionTime)
            return .success
        }
    }

    private func updateNowPlaying() {
        guard let song = current else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: song.title,
            MPMediaItemPropertyArtist: song.artist,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTime,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
        ]
        if let album = song.albumName { info[MPMediaItemPropertyAlbumTitle] = album }
        if let existing = MPNowPlayingInfoCenter.default().nowPlayingInfo,
           existing[MPMediaItemPropertyTitle] as? String == song.title,
           let art = existing[MPMediaItemPropertyArtwork] {
            info[MPMediaItemPropertyArtwork] = art
        } else {
            loadArtwork(for: song)
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func loadArtwork(for song: Song) {
        artworkTask?.cancel()
        guard let url = Artwork.url(song.thumbnailUrl, size: 600) else { return }
        artworkTask = Task {
            guard let (data, _) = try? await URLSession.shared.data(from: url), let image = UIImage(data: data),
                  !Task.isCancelled, self.current == song else { return }
            let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
            info[MPMediaItemPropertyArtwork] = artwork
            MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        }
    }
}
