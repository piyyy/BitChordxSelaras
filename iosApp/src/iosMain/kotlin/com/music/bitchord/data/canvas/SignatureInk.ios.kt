package com.music.bitchord.data.canvas

import coil3.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/** The phone lifts a scanned signature off its paper; on iOS the scan is shown as delivered for now. */
object SignatureInkTransformation : Transformation() {
    override val cacheKey = "signature-ink-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = input
}
