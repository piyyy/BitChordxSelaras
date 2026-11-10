package android.os

/**
 * The phone UI's API-level checks, answered for iOS: Compose on iOS draws through Skia, which has
 * what Android 12/13 added for the liquid glass (render effects, runtime shaders) — see the iOS
 * backdrop files in com.music.bitchord.ui.components.backdrop.
 */
object Build {
    object VERSION {
        const val SDK_INT: Int = 35
    }

    object VERSION_CODES {
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val S_V2 = 32
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
    }
}
