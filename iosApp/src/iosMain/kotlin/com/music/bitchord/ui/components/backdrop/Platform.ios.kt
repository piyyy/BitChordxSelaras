package com.music.bitchord.ui.components.backdrop

/** Skia on iOS has both (Android needs 12 for render effects, 13 for runtime shaders). */
fun isRenderEffectSupported(): Boolean = true

fun isRuntimeShaderSupported(): Boolean = true
