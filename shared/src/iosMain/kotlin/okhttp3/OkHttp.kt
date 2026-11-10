@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package okhttp3

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.valueForHTTPHeaderField
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy
import java.util.concurrent.TimeUnit

/*
 * The slice of OkHttp BitChord's shared code calls, over NSURLSession. Synchronous calls block the
 * calling thread on a semaphore — they are only made from IO dispatchers, never the main thread.
 */

class HttpUrl internal constructor(private val text: String) {
    private val components: NSURLComponents get() = NSURLComponents(string = text)

    val host: String get() = components.host ?: ""
    val scheme: String get() = components.scheme ?: "https"
    val encodedPath: String get() = components.percentEncodedPath ?: "/"
    val encodedQuery: String? get() = components.percentEncodedQuery
    val query: String? get() = components.query
    val pathSegments: List<String> get() = (components.path ?: "").split('/').filter { it.isNotEmpty() }
    fun queryParameter(name: String): String? =
        components.queryItems?.firstOrNull { (it as NSURLQueryItem).name == name }?.let { (it as NSURLQueryItem).value }
    fun newBuilder(): Builder = Builder(text)
    fun resolve(link: String): HttpUrl? = NSURL.URLWithString(link, NSURL.URLWithString(text))?.absoluteString?.let(::HttpUrl)
    override fun toString() = text
    override fun equals(other: Any?) = other is HttpUrl && other.text == text
    override fun hashCode() = text.hashCode()

    class Builder internal constructor(base: String = "") {
        private val components = NSURLComponents(string = base)
        private val items = ArrayList<NSURLQueryItem>().apply {
            components.queryItems?.forEach { add(it as NSURLQueryItem) }
        }

        fun addQueryParameter(name: String, value: String?): Builder = apply { items.add(NSURLQueryItem(name, value)) }
        fun setQueryParameter(name: String, value: String?): Builder = apply {
            items.removeAll { it.name == name }
            items.add(NSURLQueryItem(name, value))
        }
        fun removeAllQueryParameters(name: String): Builder = apply { items.removeAll { it.name == name } }
        fun addPathSegment(segment: String): Builder = apply {
            val path = (components.path ?: "").trimEnd('/')
            components.path = "$path/$segment"
        }
        fun addPathSegments(segments: String): Builder = apply { segments.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) } }
        fun encodedPath(path: String): Builder = apply { components.percentEncodedPath = path }
        fun scheme(scheme: String): Builder = apply { components.scheme = scheme }
        fun host(host: String): Builder = apply { components.host = host }
        fun build(): HttpUrl {
            components.queryItems = items.ifEmpty { null }
            // NSURLComponents leaves '+' alone in query values; servers read it as a space.
            components.percentEncodedQuery = components.percentEncodedQuery?.replace("+", "%2B")
            return HttpUrl(components.string ?: "")
        }
    }

    companion object {
        fun String.toHttpUrl(): HttpUrl = toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: $this")
        fun String.toHttpUrlOrNull(): HttpUrl? =
            NSURLComponents(string = this).takeIf { it.scheme != null && it.host != null }?.let { HttpUrl(this) }
        fun get(url: String): HttpUrl = url.toHttpUrl()
        fun parse(url: String): HttpUrl? = url.toHttpUrlOrNull()
    }
}

class MediaType private constructor(private val text: String) {
    override fun toString() = text

    companion object {
        fun String.toMediaType(): MediaType = MediaType(this)
        fun String.toMediaTypeOrNull(): MediaType? = MediaType(this)
        fun get(text: String) = MediaType(text)
        fun parse(text: String): MediaType? = MediaType(text)
    }
}

open class RequestBody internal constructor(val bytes: ByteArray, val contentType: MediaType?) {
    fun contentType(): MediaType? = contentType
    fun contentLength(): Long = bytes.size.toLong()

    companion object {
        fun String.toRequestBody(contentType: MediaType? = null): RequestBody = RequestBody(encodeToByteArray(), contentType)
        fun ByteArray.toRequestBody(contentType: MediaType? = null): RequestBody = RequestBody(this, contentType)
        fun create(contentType: MediaType?, content: String) = RequestBody(content.encodeToByteArray(), contentType)
    }
}

class FormBody private constructor(text: String) :
    RequestBody(text.encodeToByteArray(), MediaType.run { "application/x-www-form-urlencoded".toMediaType() }) {
    class Builder {
        private val pairs = ArrayList<String>()
        fun add(name: String, value: String): Builder = apply {
            pairs.add(java.net.URLEncoder.encode(name) + "=" + java.net.URLEncoder.encode(value))
        }
        fun build(): FormBody = FormBody(pairs.joinToString("&"))
    }
}

class Headers internal constructor(private val pairs: List<Pair<String, String>>) {
    operator fun get(name: String): String? = pairs.lastOrNull { it.first.equals(name, ignoreCase = true) }?.second
    fun values(name: String): List<String> = pairs.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }
    fun names(): Set<String> = pairs.map { it.first }.toSet()
    val size: Int get() = pairs.size
    fun toMultimap(): Map<String, List<String>> = pairs.groupBy({ it.first.lowercase() }, { it.second })
}

class Request internal constructor(
    val url: HttpUrl,
    val method: String,
    internal val headerPairs: List<Pair<String, String>>,
    val body: RequestBody?,
) {
    val headers: Headers get() = Headers(headerPairs)
    fun header(name: String): String? = headers[name]
    fun newBuilder(): Builder = Builder().also { b ->
        b.url(url); b.method(method, body); headerPairs.forEach { b.addHeader(it.first, it.second) }
    }

    class Builder {
        private var url: HttpUrl? = null
        private var method = "GET"
        private var body: RequestBody? = null
        private val headers = ArrayList<Pair<String, String>>()

        fun url(url: String): Builder = apply { this.url = HttpUrl.run { url.toHttpUrl() } }
        fun url(url: HttpUrl): Builder = apply { this.url = url }
        fun header(name: String, value: String): Builder = apply {
            headers.removeAll { it.first.equals(name, ignoreCase = true) }
            headers.add(name to value)
        }
        fun addHeader(name: String, value: String): Builder = apply { headers.add(name to value) }
        fun removeHeader(name: String): Builder = apply { headers.removeAll { it.first.equals(name, ignoreCase = true) } }
        fun get(): Builder = apply { method = "GET"; body = null }
        fun head(): Builder = apply { method = "HEAD"; body = null }
        fun post(body: RequestBody): Builder = apply { method = "POST"; this.body = body }
        fun put(body: RequestBody): Builder = apply { method = "PUT"; this.body = body }
        fun patch(body: RequestBody): Builder = apply { method = "PATCH"; this.body = body }
        fun delete(body: RequestBody? = null): Builder = apply { method = "DELETE"; this.body = body }
        fun method(method: String, body: RequestBody?): Builder = apply { this.method = method; this.body = body }
        fun tag(tag: Any?): Builder = this
        fun cacheControl(control: Any?): Builder = this
        fun build(): Request = Request(url ?: error("url == null"), method, headers.toList(), body)
    }
}

class ResponseBody internal constructor(private val data: ByteArray, private val type: String?) : AutoCloseable {
    fun string(): String = data.decodeToString()
    fun bytes(): ByteArray = data
    fun contentLength(): Long = data.size.toLong()
    fun contentType(): MediaType? = type?.let { MediaType.run { it.toMediaType() } }
    override fun close() {}
}

class Response internal constructor(
    val request: Request,
    val code: Int,
    val message: String,
    private val headerPairs: List<Pair<String, String>>,
    val body: ResponseBody?,
) : AutoCloseable {
    val isSuccessful: Boolean get() = code in 200..299
    val isRedirect: Boolean get() = code in 300..399
    val headers: Headers get() = Headers(headerPairs)
    fun header(name: String, default: String? = null): String? = headers[name] ?: default
    fun peekBody(byteCount: Long): ResponseBody = body ?: ResponseBody(ByteArray(0), null)
    override fun close() {}
}

interface Callback {
    fun onFailure(call: Call, e: java.io.IOException)
    fun onResponse(call: Call, response: Response)
}

interface Call {
    fun request(): Request
    fun execute(): Response
    fun enqueue(responseCallback: Callback)
    fun cancel()
    val isCanceled: Boolean
}

class Dispatcher {
    var maxRequestsPerHost: Int = 16
    var maxRequests: Int = 64
}

class ConnectionPool(maxIdle: Int = 5, keepAlive: Long = 5, unit: TimeUnit = TimeUnit.MINUTES)

fun interface Interceptor {
    fun intercept(chain: Chain): Response
    interface Chain {
        fun request(): Request
        fun proceed(request: Request): Response
    }
}

class OkHttpClient internal constructor(
    val callTimeoutMillis: Long,
    val connectTimeoutMillis: Long,
    val readTimeoutMillis: Long,
    val followRedirects: Boolean,
) {
    constructor() : this(0, 20_000, 30_000, true)

    fun newBuilder(): Builder = Builder(this)
    fun newCall(request: Request): Call = SessionCall(this, request)

    class Builder internal constructor(from: OkHttpClient?) {
        constructor() : this(null)

        private var call = from?.callTimeoutMillis ?: 0L
        private var connect = from?.connectTimeoutMillis ?: 20_000L
        private var read = from?.readTimeoutMillis ?: 30_000L
        private var redirects = from?.followRedirects ?: true

        fun callTimeout(timeout: Long, unit: TimeUnit): Builder = apply { call = unit.toMillis(timeout) }
        fun connectTimeout(timeout: Long, unit: TimeUnit): Builder = apply { connect = unit.toMillis(timeout) }
        fun readTimeout(timeout: Long, unit: TimeUnit): Builder = apply { read = unit.toMillis(timeout) }
        fun writeTimeout(timeout: Long, unit: TimeUnit): Builder = this
        fun callTimeout(d: kotlin.time.Duration): Builder = apply { call = d.inWholeMilliseconds }
        fun connectTimeout(d: kotlin.time.Duration): Builder = apply { connect = d.inWholeMilliseconds }
        fun readTimeout(d: kotlin.time.Duration): Builder = apply { read = d.inWholeMilliseconds }
        fun retryOnConnectionFailure(retry: Boolean): Builder = this
        fun followRedirects(follow: Boolean): Builder = apply { redirects = follow }
        fun followSslRedirects(follow: Boolean): Builder = this
        fun dispatcher(dispatcher: Dispatcher): Builder = this
        fun connectionPool(pool: ConnectionPool): Builder = this
        fun addInterceptor(interceptor: Interceptor): Builder = this
        fun addNetworkInterceptor(interceptor: Interceptor): Builder = this
        fun cookieJar(jar: Any?): Builder = this
        fun cache(cache: Any?): Builder = this
        fun build(): OkHttpClient = OkHttpClient(call, connect, read, redirects)
    }
}

private val session: NSURLSession by lazy {
    NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.defaultSessionConfiguration)
}

private class SessionCall(private val client: OkHttpClient, private val request: Request) : Call {
    private var task: NSURLSessionDataTask? = null
    override var isCanceled = false
        private set

    override fun request() = request

    private fun urlRequest(): NSMutableURLRequest {
        val timeout = listOf(client.callTimeoutMillis, client.readTimeoutMillis).filter { it > 0 }.minOrNull() ?: 30_000L
        val r = NSMutableURLRequest(uRL = NSURL(string = request.url.toString()))
        r.setHTTPMethod(request.method)
        r.setTimeoutInterval(timeout / 1000.0)
        request.headerPairs.forEach { (k, v) -> r.setValue(v, forHTTPHeaderField = k) }
        request.body?.let { body ->
            if (r.valueForHTTPHeaderField("Content-Type") == null) body.contentType?.let { r.setValue(it.toString(), forHTTPHeaderField = "Content-Type") }
            r.setHTTPBody(body.bytes.toNSData())
        }
        return r
    }

    private fun start(done: (Response?, java.io.IOException?) -> Unit) {
        val t = session.dataTaskWithRequest(urlRequest()) { data: NSData?, response: NSURLResponse?, error: NSError? ->
            if (error != null || response !is NSHTTPURLResponse) {
                done(null, java.io.IOException(error?.localizedDescription ?: "No response"))
            } else {
                val headers = response.allHeaderFields.entries.map { (k, v) -> k.toString() to v.toString() }
                val type = headers.firstOrNull { it.first.equals("Content-Type", true) }?.second
                done(
                    Response(request, response.statusCode.toInt(), "", headers, ResponseBody(data?.toByteArray() ?: ByteArray(0), type)),
                    null,
                )
            }
        }
        task = t
        t.resume()
    }

    override fun execute(): Response {
        val semaphore = dispatch_semaphore_create(0)
        var result: Response? = null
        var failure: java.io.IOException? = null
        start { r, e -> result = r; failure = e; dispatch_semaphore_signal(semaphore) }
        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)
        failure?.let { throw it }
        return result ?: throw java.io.IOException("No response")
    }

    override fun enqueue(responseCallback: Callback) {
        start { r, e -> if (r != null) responseCallback.onResponse(this, r) else responseCallback.onFailure(this, e ?: java.io.IOException("No response")) }
    }

    override fun cancel() {
        isCanceled = true
        task?.cancel()
    }
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
