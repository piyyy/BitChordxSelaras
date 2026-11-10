package androidx.compose.ui.platform

import android.content.Context
import android.content.IosContext
import androidx.compose.runtime.staticCompositionLocalOf

/** Android's LocalContext for the phone's UI files compiled for iOS. */
val LocalContext = staticCompositionLocalOf<Context> { IosContext }
