package java.nio.charset

/** Every charset BitChord names is UTF-8 compatible for its purposes; the name is kept for show. */
class Charset(val name: String) {
    fun name(): String = name
    override fun toString(): String = name

    companion object {
        fun forName(name: String): Charset = Charset(name)
        fun defaultCharset(): Charset = Charset("UTF-8")
    }
}

object StandardCharsets {
    val UTF_8 = Charset("UTF-8")
    val US_ASCII = Charset("US-ASCII")
    val ISO_8859_1 = Charset("ISO-8859-1")
}
