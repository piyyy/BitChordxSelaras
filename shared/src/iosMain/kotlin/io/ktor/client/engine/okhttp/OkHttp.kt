package io.ktor.client.engine.okhttp

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/** `HttpClient(OkHttp) { engine { preconfigured = … } }` on iOS: the Darwin engine under the same name. */
class OkHttpConfig : HttpClientEngineConfig() {
    var preconfigured: okhttp3.OkHttpClient? = null
    fun config(block: Any.() -> Unit) {}
}

data object OkHttp : HttpClientEngineFactory<OkHttpConfig> {
    override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine {
        OkHttpConfig().apply(block)
        return Darwin.create()
    }
}
