package androidx.compose.ui.platform

import androidx.compose.runtime.Composable

/** Android's screen Configuration, as far as the phone's sheets read it: the window in dp. */
class Configuration(val screenWidthDp: Int, val screenHeightDp: Int)

object LocalConfiguration {
    val current: Configuration
        @Composable get() {
            val size = LocalWindowInfo.current.containerSize
            val density = LocalDensity.current.density
            return Configuration((size.width / density).toInt(), (size.height / density).toInt())
        }
}
