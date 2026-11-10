package com.music.bitchord.data.settings

import com.music.bitchord.ios.IosSettings

/**
 * The slice of the phone's AppSettings the Android UI files compiled for iOS read, backed by
 * [IosSettings] (NSUserDefaults).
 */
object AppSettings {
    val liquidGlass get() = IosSettings.liquidGlass
    val reduceAnimation get() = IosSettings.reduceAnimation
    val reduceDynamicBlur get() = IosSettings.reduceDynamicBlur

    fun setLiquidGlass(value: Boolean) = IosSettings.set(IosSettings.liquidGlass, "liquidGlass", value)
    fun setReduceAnimation(value: Boolean) = IosSettings.set(IosSettings.reduceAnimation, "reduceAnimation", value)
    fun setReduceDynamicBlur(value: Boolean) = IosSettings.set(IosSettings.reduceDynamicBlur, "reduceDynamicBlur", value)
}
