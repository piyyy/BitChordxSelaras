package com.music.bitchord.ios

import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.player.PlayerPlatform
import platform.UIKit.UIViewController

/**
 * The whole BitChord UI as one view controller, for the SwiftUI app to host. Called once, from
 * Swift, with the bridge that plays the audio.
 */
fun MainViewController(bridge: PlaybackBridge): UIViewController {
    AppUi.install(IosAppUiHost)
    PlayerPlatform.install(IosPlayerHost)
    IosPlayer.install(bridge)
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.build()
    }
    IosAccount.restore(bridge.storedCookie())
    return ComposeUIViewController { IosApp() }
}

/** For Swift: a sign-in finished (or a sign-out), with the new cookie or null. */
fun signedIn(cookie: String?) = IosAccount.onCookie(cookie)
