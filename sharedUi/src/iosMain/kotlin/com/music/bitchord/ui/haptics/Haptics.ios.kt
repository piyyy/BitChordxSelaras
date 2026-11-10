package com.music.bitchord.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * The phone's haptic rhythms (Haptics.android.kt) on the Taptic Engine: each beat a light tick,
 * a soft low tick or a rigid click at the same relative strength, spaced as on Android.
 */
actual class Haptics {
    private val tick = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val lowTick = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft)
    private val click = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)

    actual fun play(haptic: Haptic) {
        var at = 0L
        for ((kind, scale, gapMs) in rhythmOf(haptic)) {
            at += gapMs
            val generator = when (kind) {
                0 -> tick
                1 -> lowTick
                else -> click
            }
            generator.prepare()
            if (at == 0L) {
                generator.impactOccurredWithIntensity(scale.toDouble())
            } else {
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, at * 1_000_000L), dispatch_get_main_queue()) {
                    generator.impactOccurredWithIntensity(scale.toDouble())
                }
            }
        }
    }
}

@Composable
actual fun rememberHaptics(): Haptics = remember { Haptics() }

// kind: 0 tick, 1 low tick, 2 click — the same beats as Android's rhythmOf().
private fun rhythmOf(haptic: Haptic): List<Triple<Int, Float, Long>> = when (haptic) {
    Haptic.Tick -> listOf(Triple(0, 0.35f, 0L))
    Haptic.Tap -> listOf(Triple(2, 0.5f, 0L))
    Haptic.Select -> listOf(Triple(0, 0.4f, 0L), Triple(2, 0.75f, 18L))
    Haptic.ToggleOn -> listOf(Triple(0, 0.4f, 0L), Triple(2, 0.9f, 14L))
    Haptic.ToggleOff -> listOf(Triple(2, 0.75f, 0L), Triple(0, 0.3f, 14L))
    Haptic.SkipNext -> listOf(Triple(0, 0.4f, 0L), Triple(0, 0.55f, 16L), Triple(2, 0.7f, 16L))
    Haptic.SkipPrevious -> listOf(Triple(2, 0.7f, 0L), Triple(0, 0.55f, 16L), Triple(0, 0.4f, 16L))
    Haptic.Resume -> listOf(Triple(1, 0.5f, 0L), Triple(2, 0.85f, 22L))
    Haptic.Pause -> listOf(Triple(2, 0.85f, 0L), Triple(1, 0.4f, 22L))
    Haptic.Expand -> listOf(Triple(0, 0.3f, 0L), Triple(0, 0.45f, 12L), Triple(2, 0.6f, 12L))
}
