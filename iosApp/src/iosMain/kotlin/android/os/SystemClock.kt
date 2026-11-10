package android.os

import kotlin.time.TimeSource

/** Milliseconds since an arbitrary point that never jumps — what the phone's timers count in. */
object SystemClock {
    private val start = TimeSource.Monotonic.markNow()
    fun elapsedRealtime(): Long = start.elapsedNow().inWholeMilliseconds
    fun uptimeMillis(): Long = elapsedRealtime()
}
