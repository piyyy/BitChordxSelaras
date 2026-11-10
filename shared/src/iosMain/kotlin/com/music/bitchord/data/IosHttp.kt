package com.music.bitchord.data

import okhttp3.OkHttpClient

/** iOS stand-in for the JVM's shared OkHttp client (see jvmSharedMain/data/Http.kt). */
object Http {
    val client: OkHttpClient = OkHttpClient()
}
