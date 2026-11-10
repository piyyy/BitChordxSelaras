package android.view

/** What PlayerSheetMotion touches on the player sheet's window. iOS sheets have no window animations to quiet. */
class Window {
    class LayoutParams {
        var windowAnimations: Int = 0
    }

    val attributes = LayoutParams()

    fun setWindowAnimations(resId: Int) {
        attributes.windowAnimations = resId
    }
}
