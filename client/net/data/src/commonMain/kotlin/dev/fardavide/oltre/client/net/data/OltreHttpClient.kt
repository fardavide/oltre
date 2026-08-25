package dev.fardavide.oltre.client.net.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// The transport, built once at the composition root. One function per platform because **an engine
// is the platform**: Darwin is `NSURLSession`, which is the only stack that gets iOS's cellular and
// background behaviour right, and OkHttp answers for both JVM targets so Android and desktop run
// the same client.
//
// The configuration below is shared, which is the whole reason this is an `expect fun` returning a
// built client rather than an `expect` engine the common code wraps: a client built around an
// engine it did not create does not close it, and a timeout policy that lived in three files would
// drift in three directions.
expect fun oltreHttpClient(): HttpClient

// **How long a colony is worth waiting for before the answer is "later".** Both are invented here
// and both are meant to move once there is a deployment to measure — `#111` is the slice that can.
//
// Ten seconds to open a connection and twenty for the whole request, which is chosen around this
// backend rather than as a general default: Cloud Run scales to zero, so `#106` §6 budgets **one to
// two seconds of cold start** on the first request after an idle spell, and a timeout tight enough
// to cut that off would turn every morning's first check-in into a queue. Twenty is far past any
// warm request and far short of a player deciding the app is broken.
//
// What the pair is actually for is the failure the outbox cannot see: a socket that opens and then
// says nothing. Without a deadline that request never returns, so nothing is ever queued, no
// backoff ever starts, and the tap simply hangs.
private val CONNECT_TIMEOUT: Duration = 10.seconds

private val REQUEST_TIMEOUT: Duration = 20.seconds

internal fun HttpClientConfig<*>.oltreDefaults() {
    // **Ktor's default, stated out loud because it is load-bearing.** With `expectSuccess = true` a
    // `4xx` throws instead of returning, `KtorOltreApi` would never read the body, and every
    // designed answer in `ApiError` — *"sign in again"*, *"update the app"*, *"found a colony
    // first"* — would arrive as the same exception. The taxonomy only works if the status line is
    // data.
    expectSuccess = false

    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT.inWholeMilliseconds
        requestTimeoutMillis = REQUEST_TIMEOUT.inWholeMilliseconds
    }
}
