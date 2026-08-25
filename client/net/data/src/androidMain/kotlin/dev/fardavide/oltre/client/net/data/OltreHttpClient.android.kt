package dev.fardavide.oltre.client.net.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

// OkHttp, which is what the platform ships with and what every Android networking stack is built
// on. Identical to the desktop half by design rather than by accident — see that file.
actual fun oltreHttpClient(): HttpClient = HttpClient(OkHttp) { oltreDefaults() }
