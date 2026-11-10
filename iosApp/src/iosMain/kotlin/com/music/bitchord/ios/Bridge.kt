package com.music.bitchord.ios

/**
 * What the Swift side of the app does for the Compose UI. Audio stays native: Swift resolves the
 * stream (YouTubeKit), plays it on AVPlayer with Selaras in an MTAudioProcessingTap, and owns the
 * lock screen, sign-in web view and the Selaras page. Implemented by `PlaybackBridgeImpl.swift`.
 */
interface PlaybackBridge {
    fun setListener(listener: PlaybackListener)

    /** Resolve and play [videoId] from [startMs]; replaces whatever was loaded. */
    fun load(
        videoId: String,
        title: String,
        artist: String,
        album: String?,
        artworkUrl: String?,
        startMs: Long,
        playWhenReady: Boolean,
    )

    /** Resolve [videoId] ahead of time so the next track starts at once. */
    fun prefetch(videoId: String)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()

    /** The lock screen's next/previous buttons follow the queue. */
    fun setQueueButtons(hasPrevious: Boolean, hasNext: Boolean)

    /** The Selaras equaliser page (SwiftUI), as a sheet. */
    fun openSelaras()

    /** Google sign-in (WKWebView); answers through [PlaybackListener.onCookie]. */
    fun openSignIn()

    fun signOut()

    /** The YouTube Music cookie kept in the Keychain from an earlier sign-in, or null. */
    fun storedCookie(): String?

    /** A short message over the app. */
    fun toast(message: String)

    /** Opens a URL (share sheet for music.youtube.com links). */
    fun share(text: String)

    /** The output's name for the player's device row: "iPhone", "AirPods Pro"… */
    fun currentOutputName(): String

    /** Shows AVRoutePickerView's menu. */
    fun openRoutePicker()

    /** System volume 0..1 (read only on iOS; the slider drives MPVolumeView). */
    fun systemVolume(): Float

    fun setSystemVolume(level: Float)
}

/** The Swift side's answers, on the main thread. */
interface PlaybackListener {
    fun onState(isPlaying: Boolean, isLoading: Boolean, positionMs: Long, durationMs: Long)
    fun onEnded()
    fun onError(message: String)
    fun onRemoteNext()
    fun onRemotePrevious()
    fun onCookie(cookie: String?)
    fun onOutputChanged(name: String)
}
