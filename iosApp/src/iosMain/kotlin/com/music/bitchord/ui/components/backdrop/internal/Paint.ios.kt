package com.music.bitchord.ui.components.backdrop.internal

import androidx.compose.ui.graphics.Paint
import com.music.bitchord.ui.components.backdrop.RuntimeShader
import com.music.bitchord.ui.components.backdrop.asComposeShader
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

/** Android's BlurMaskFilter(radius, NORMAL); Skia converts its radius to a sigma the same way. */
internal fun Paint.blur(radius: Float) {
    asFrameworkPaint().maskFilter =
        if (radius > 0f) MaskFilter.makeBlur(FilterBlurMode.NORMAL, 0.57735f * radius + 0.5f) else null
}

internal fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    asFrameworkPaint().shader = runtimeShader?.asComposeShader()
}
