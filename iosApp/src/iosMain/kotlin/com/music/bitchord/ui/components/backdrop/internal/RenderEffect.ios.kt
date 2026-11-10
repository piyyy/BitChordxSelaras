package com.music.bitchord.ui.components.backdrop.internal

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaColorFilter
import com.music.bitchord.ui.components.backdrop.RuntimeShader
import com.music.bitchord.ui.components.backdrop.asSkiaBuilder
import org.jetbrains.skia.ImageFilter

/** `this` first, then [other] — Android's createChainEffect(other, this). */
internal fun RenderEffect?.chain(other: RenderEffect): RenderEffect =
    if (this != null) {
        ImageFilter.makeCompose(other.asSkiaImageFilter(), this.asSkiaImageFilter()).asComposeRenderEffect()
    } else {
        other
    }

internal fun RuntimeShaderEffect(runtimeShader: RuntimeShader, uniformShaderName: String): RenderEffect =
    ImageFilter.makeRuntimeShader(runtimeShader.asSkiaBuilder(), uniformShaderName, null).asComposeRenderEffect()

internal fun ColorFilterEffect(renderEffect: RenderEffect?, colorFilter: ColorFilter): RenderEffect =
    ImageFilter.makeColorFilter(colorFilter.asSkiaColorFilter(), renderEffect?.asSkiaImageFilter(), null)
        .asComposeRenderEffect()
