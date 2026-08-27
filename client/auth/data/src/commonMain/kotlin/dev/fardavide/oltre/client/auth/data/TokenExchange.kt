package dev.fardavide.oltre.client.auth.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// **The last leg of a browser flow, in the two halves it splits into cleanly.** Building the body and
// reading the answer are arithmetic over strings and live here, where a test runs them; posting the
// bytes is a socket and lives in each platform's `actual`, where nothing here can.
//
// **No Ktor, and that is deliberate rather than an omission.** `:client:net:data` is *"the only module
// in `client/` that opens a socket"* and that sentence is worth keeping close to true: what opens one
// here is `HttpURLConnection` on the two JVM platforms and `NSURLSession` on Apple, each inside the
// same `actual` that already owns the browser. An engine dependency here would be a second transport
// in the app whose failure modes nobody had thought about.

// The form body Google's token endpoint takes. **`client_secret` is present only where the client type
// requires one** — Google's iOS client is a public client and has none, its desktop client is an
// *installed application* whose secret is documented as not confidential and is still mandatory.
internal fun tokenRequestBody(
    clientId: String,
    clientSecret: String?,
    code: String,
    verifier: String,
    redirectUri: String,
): String = buildList {
    add("grant_type" to "authorization_code")
    add("client_id" to clientId)
    clientSecret?.let { add("client_secret" to it) }
    add("code" to code)
    add("code_verifier" to verifier)
    add("redirect_uri" to redirectUri)
}.joinToString("&") { (name, value) -> "${name.urlEncoded()}=${value.urlEncoded()}" }

// **The ID token, or null for every other thing that body could be.** A token endpoint answers with
// JSON on success and with JSON on failure, so *"did it parse"* is not the question — *"is there an
// `id_token` in it"* is.
//
// Null rather than a thrown exception on malformed input, because the caller has exactly one thing to
// do about every way this can go wrong and `SignInAttempt` has a member for it.
internal fun readIdToken(body: String): String? = try {
    (Json.parseToJsonElement(body) as? JsonObject)
        ?.get("id_token")
        ?.jsonPrimitive
        ?.content
        ?.takeIf { it.isNotBlank() }
} catch (_: IllegalArgumentException) {
    // What `parseToJsonElement` throws on anything that is not JSON — an HTML error page from a
    // captive portal is the case this is really about, and it is `Unreachable` rather than a crash.
    null
}
