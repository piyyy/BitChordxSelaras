package coil3.request

/**
 * coil3's `transformations()` is Android-only in this Coil; the phone's pages use it only to lift a
 * scanned signature off its paper, which iOS skips for now (see SignatureInk.ios.kt).
 */
fun ImageRequest.Builder.transformations(vararg transformations: Any): ImageRequest.Builder = this
