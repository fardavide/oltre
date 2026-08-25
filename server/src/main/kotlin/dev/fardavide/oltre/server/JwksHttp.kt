package dev.fardavide.oltre.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// **Every line in this slice that needs a socket to run at all, and no line that decides anything.**
// It is `PostgresDatabase.kt`'s file one layer over: nothing here branches on a key, a provider or a
// token — it asks for a document and hands back the text. When to ask is `JwksKeys`, what the text
// means is `rsaKeysFrom`, and both are reachable by a plain unit test.
//
// **The JDK's own client rather than a dependency.** `java.net.http` has been in the platform since
// 11, this module is JVM-only, and the whole of what is wanted is one GET with a timeout — so a
// catalogue line here would be dead weight in the sense `#112` meant it. Ktor's client is not on
// this module's classpath and there is no reason to put it there for six lines.

// Short, and the reason is where this runs. Cloud Run bills wall-clock per request, and a provider
// that has stopped answering must not turn one sign-in into a held instance: failing in five seconds
// gives the caller a 500 it can retry, where hanging gives it nothing and costs money to do so.
private val TIMEOUT: Duration = Duration.ofSeconds(5)

// One client for the process, not one per fetch. It owns a connection pool and a selector thread,
// so building one per request would open a new TLS session to Apple every time — the expensive half
// of the round trip, paid again for no reason.
private val client: HttpClient = HttpClient.newBuilder()
    .connectTimeout(TIMEOUT)
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

// **A non-200 raises**, which is the same call `ColonyRow.kt` makes about a row that will not decode:
// a body that is not a key set must never be handed on as if it were one, because `rsaKeysFrom`
// would then answer "no keys" and every player would be told their token is invalid. It reaches
// `served`'s one `catch` and becomes `ApiError.Internal`.
internal fun httpJwksSource(): JwksSource = JwksSource { uri ->
    withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create(uri)).timeout(TIMEOUT).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == HTTP_OK) { "$uri answered ${response.statusCode()} rather than a key set" }
        response.body()
    }
}

private const val HTTP_OK = 200
