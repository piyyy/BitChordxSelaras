package java.net

import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.stringByRemovingPercentEncoding

object URLEncoder {
    private val unreserved = NSCharacterSet.characterSetWithCharactersInString(
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.*",
    )

    /** application/x-www-form-urlencoded, as Java spells it: space is '+'. */
    @Suppress("CAST_NEVER_SUCCEEDS")
    fun encode(s: String, charset: Any? = null): String =
        (s.replace(" ", "\u0000") as NSString).stringByAddingPercentEncodingWithAllowedCharacters(unreserved)
            ?.replace("%00", "+") ?: s
}

object URLDecoder {
    @Suppress("CAST_NEVER_SUCCEEDS")
    fun decode(s: String, charset: Any? = null): String =
        (s.replace("+", " ") as NSString).stringByRemovingPercentEncoding ?: s
}

class URISyntaxException(input: String, reason: String) : Exception("$reason: $input")

class URI(private val text: String) {
    private val url: NSURL = NSURL.URLWithString(text) ?: throw URISyntaxException(text, "Malformed URI")

    val scheme: String? get() = url.scheme
    val host: String? get() = url.host
    val path: String? get() = url.path
    val rawPath: String? get() = url.path
    val query: String? get() = url.query
    val rawQuery: String? get() = url.query
    val fragment: String? get() = url.fragment
    val port: Int get() = url.port?.intValue ?: -1
    val userInfo: String? get() = url.user
    fun toURL(): URL = URL(text)
    fun resolve(other: String): URI = URI(NSURL.URLWithString(other, url)?.absoluteString ?: other)
    override fun toString() = text
}

class URL(private val text: String) {
    override fun toString() = text
    fun toURI() = URI(text)
    val host: String? get() = URI(text).host
    val path: String? get() = URI(text).path
    val protocol: String? get() = URI(text).scheme
}

class UnknownHostException(message: String? = null) : kotlinx.io.IOException(message)
class SocketTimeoutException(message: String? = null) : kotlinx.io.IOException(message)
class ConnectException(message: String? = null) : kotlinx.io.IOException(message)
class SocketException(message: String? = null) : kotlinx.io.IOException(message)
