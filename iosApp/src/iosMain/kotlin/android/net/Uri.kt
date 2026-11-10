package android.net

/** android.net.Uri for the phone's UI files on iOS: a parsed string, scheme and path. */
class Uri private constructor(private val text: String) {
    val scheme: String? get() = text.substringBefore(':', "").takeIf { it.isNotEmpty() && ':' in text }
    val path: String? get() = text.substringAfter("://", text).let { rest -> "/" + rest.substringAfter('/', "") }
    val lastPathSegment: String? get() = text.substringAfterLast('/').takeIf { it.isNotEmpty() }

    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is Uri && other.text == text
    override fun hashCode(): Int = text.hashCode()

    companion object {
        fun parse(text: String): Uri = Uri(text)
        val EMPTY: Uri = Uri("")
    }
}
