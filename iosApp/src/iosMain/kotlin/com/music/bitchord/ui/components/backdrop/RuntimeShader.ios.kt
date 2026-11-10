package com.music.bitchord.ui.components.backdrop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * The backdrop's AGSL shaders, run as SkSL (the language AGSL is) through Skia's RuntimeEffect.
 * Android's RuntimeShader is the shader itself, mutated by its uniforms; Skia's builder is the
 * mutable half and makes an immutable shader — which every caller here asks for only after setting
 * the uniforms, so [asComposeShader] / the effects snapshot at the right moment.
 */
fun RuntimeShader(shaderString: String): RuntimeShader =
    SkiaRuntimeShader(RuntimeShaderBuilder(RuntimeEffect.makeForShader(shaderString)))

fun RuntimeShader.asComposeShader(): Shader = (this as SkiaRuntimeShader).builder.makeShader()

internal fun RuntimeShader.asSkiaBuilder(): RuntimeShaderBuilder = (this as SkiaRuntimeShader).builder

internal class SkiaRuntimeShader(val builder: RuntimeShaderBuilder) : RuntimeShader {
    override fun setFloatUniform(name: String, value: Float) = builder.uniform(name, value)
    override fun setFloatUniform(name: String, value1: Float, value2: Float) = builder.uniform(name, value1, value2)
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) =
        builder.uniform(name, value1, value2, value3)
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) =
        builder.uniform(name, value1, value2, value3, value4)
    override fun setFloatUniform(name: String, values: FloatArray) = builder.uniform(name, values)

    override fun setIntUniform(name: String, value: Int) = builder.uniform(name, value)
    override fun setIntUniform(name: String, value1: Int, value2: Int) = builder.uniform(name, value1, value2)
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) =
        builder.uniform(name, value1, value2, value3)
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) =
        builder.uniform(name, value1, value2, value3, value4)
    override fun setIntUniform(name: String, values: IntArray) = when (values.size) {
        1 -> builder.uniform(name, values[0])
        2 -> builder.uniform(name, values[0], values[1])
        3 -> builder.uniform(name, values[0], values[1], values[2])
        else -> builder.uniform(name, values[0], values[1], values[2], values[3])
    }

    override fun setColorUniform(name: String, color: Color) =
        builder.uniform(name, color.red, color.green, color.blue, color.alpha)
}

interface RuntimeShader {

    fun setFloatUniform(name: String, value: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float)
    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)
    fun setFloatUniform(name: String, values: FloatArray)

    fun setIntUniform(name: String, value: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int)
    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)
    fun setIntUniform(name: String, values: IntArray)

    fun setColorUniform(name: String, color: Color)
}
