package androidx.activity.compose

import androidx.compose.runtime.Composable

/**
 * Android's system back, for the phone's pages compiled for iOS. iOS has no back button; the
 * top bar's back (FrostedTopBar's onBack) is the way out of a page, so this has nothing to catch.
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) = Unit
