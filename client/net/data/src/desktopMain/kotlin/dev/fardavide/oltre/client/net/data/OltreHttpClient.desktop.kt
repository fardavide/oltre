package dev.fardavide.oltre.client.net.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

// The same engine Android runs, which is the point: the desktop build is a dev loop for the phone,
// so a transport bug found here is a transport bug fixed there.
actual fun oltreHttpClient(): HttpClient = HttpClient(OkHttp) { oltreDefaults() }
