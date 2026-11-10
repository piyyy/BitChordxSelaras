package java.math

/** Exact decimal text of a long, shifted — the one use NerdStats has (Hz → MHz). */
class BigDecimal private constructor(private val unscaled: Long, private val scale: Int) {
    constructor(value: Long) : this(value, 0)
    constructor(value: Int) : this(value.toLong(), 0)

    fun movePointLeft(n: Int) = BigDecimal(unscaled, scale + n)
    fun movePointRight(n: Int) = BigDecimal(unscaled, scale - n)

    fun stripTrailingZeros(): BigDecimal {
        var u = unscaled; var s = scale
        while (s > 0 && u != 0L && u % 10 == 0L) { u /= 10; s-- }
        return BigDecimal(u, s)
    }

    fun toPlainString(): String {
        if (scale <= 0) return (unscaled.toString() + "0".repeat(-scale))
        val negative = unscaled < 0
        val digits = kotlin.math.abs(unscaled).toString().padStart(scale + 1, '0')
        val text = digits.dropLast(scale) + "." + digits.takeLast(scale)
        return if (negative) "-$text" else text
    }

    override fun toString() = toPlainString()
}
