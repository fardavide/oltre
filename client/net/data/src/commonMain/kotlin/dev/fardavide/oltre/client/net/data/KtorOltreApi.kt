package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.RefreshRequest
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.SignInNonce
import dev.fardavide.oltre.protocol.SignInRequest
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

// **The only place in `client/` that knows what a socket is**, and it decides nothing: it encodes,
// posts, reads a status line and decodes. What to do about each answer is `ColonySync`'s, which is
// therefore reachable by a plain unit test — `#108`'s move on the other side of the wire, made
// again here.
//
// The client and the base URL are parameters for the reason every seam in this repository is one.
// `#111` supplies the deployed URL; nothing here knows it, and a test hands in a `MockEngine` that
// answers from a lambda so that the encode, the header, the status line and the decode are all
// exercised with nothing listening.
class KtorOltreApi(
    private val client: HttpClient,
    private val baseUrl: String,
) : OltreApi {

    // **The unauthenticated surface**, and the one that carries no session because it is what makes
    // one. Two methods because the provider is the path — see `OltreApi` and `Auth.kt`.
    override suspend fun signInWithApple(idToken: IdToken, nonce: SignInNonce): ApiResult<SessionResponse> =
        signIn("/v1/auth/apple", idToken, nonce)

    override suspend fun signInWithGoogle(idToken: IdToken, nonce: SignInNonce): ApiResult<SessionResponse> =
        signIn("/v1/auth/google", idToken, nonce)

    override suspend fun refresh(refreshToken: SessionToken): ApiResult<SessionResponse> =
        send(SessionResponse.serializer()) {
            post("/v1/auth/refresh", RefreshRequest(ApiVersion.CURRENT, refreshToken), RefreshRequest.serializer())
        }

    // **The one call with no body in either direction.** `204` is the success and it is also what a
    // second attempt gets, so there is nothing to decode and nothing to negotiate — which is why
    // this route alone states no `ApiVersion`.
    override suspend fun deleteAccount(access: SessionToken): ApiResult<Unit> = send(null) {
        client.delete(baseUrl + "/v1/account") { bearer(access) }
    }

    override suspend fun foundColony(access: SessionToken): ApiResult<SyncResponse> =
        sync("/v1/colony", access, emptyList())

    override suspend fun sync(access: SessionToken, envelopes: List<VerbEnvelope>): ApiResult<SyncResponse> =
        sync("/v1/sync", access, envelopes)

    private suspend fun signIn(
        path: String,
        idToken: IdToken,
        nonce: SignInNonce,
    ): ApiResult<SessionResponse> = send(SessionResponse.serializer()) {
        post(path, SignInRequest(ApiVersion.CURRENT, idToken, nonce), SignInRequest.serializer())
    }

    private suspend fun sync(
        path: String,
        access: SessionToken,
        envelopes: List<VerbEnvelope>,
    ): ApiResult<SyncResponse> = send(SyncResponse.serializer()) {
        post(path, SyncRequest(ApiVersion.CURRENT, envelopes), SyncRequest.serializer()) { bearer(access) }
    }

    private suspend fun <T> post(
        path: String,
        body: T,
        serializer: kotlinx.serialization.SerializationStrategy<T>,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = client.post(baseUrl + path) {
        configure()
        contentType(ContentType.Application.Json)
        // **Encoded by hand rather than by content negotiation, and the server does the same in the
        // other direction and for the same reason** — see `admit` in `Endpoints.kt`. `Protocol.json`
        // is the one codec both ends speak, and the properties that make it that one
        // (`encodeDefaults`, and deliberately no `ignoreUnknownKeys`) are the contract rather than a
        // preference. A plugin configured with a different dialect would be a client the server
        // cannot read, and nothing would say so.
        //
        // **The version is stated here and not by the caller.** Which contract this build speaks is
        // a fact about the build; a parameter would be an invitation to send the wrong one.
        setBody(Protocol.json.encodeToString(serializer, body))
    }

    // **A standard `Authorization: Bearer …`**, and both halves of the string are `Protocol`'s
    // rather than spelled out here — a header this end got wrong by one character would read exactly
    // like a player who never signed in, which is the failure that moved the name into that module.
    private fun HttpRequestBuilder.bearer(access: SessionToken) {
        header(Protocol.AUTHORIZATION_HEADER, Protocol.BEARER_PREFIX + access.value)
    }

    // **One shape for every call**, because every one of them fails in exactly the same three ways
    // and the arms below are the whole of what this class decides. `null` for the deserializer is
    // the `204` case: a success carrying no body, which has to be read as one rather than as a body
    // that failed to parse.
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> send(
        deserializer: kotlinx.serialization.DeserializationStrategy<T>?,
        request: suspend () -> HttpResponse,
    ): ApiResult<T> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            // Not a failure and not ours — `served()`'s arm on the server, for its reason. A
            // cancelled request is the caller having gone away, and swallowing it would break the
            // coroutine trying to unwind.
            throw e
        } catch (_: IOException) {
            // **Every way a request can fail to happen arrives here**: no route to the host, a
            // refused connection, a name that does not resolve, a socket that closed mid-body, and
            // both timeouts. They are one answer because there is one thing to do about them — the
            // queue is intact and this is worth trying again.
            //
            // **Everything else propagates, deliberately.** Catching `Exception` here would be the
            // easy way to be sure, and it would turn a programming error into *"you are offline"* —
            // a state the app would then sit in forever, retrying something that will never work.
            //
            // That makes the width of `IOException` load-bearing, and it is **checked rather than
            // assumed for each engine**, because an engine that threw anything else would make
            // every tap made with no signal propagate out of `act` instead of reaching the outbox.
            // OkHttp is checked by a test — `OltreApiIntegrationTest` refuses a real connection on a
            // real socket. Darwin cannot be run by any machine in this repository, so it was read
            // off the artifact instead: `ktor-client-darwin:3.5.2`'s klib declares
            // `DarwinHttpRequestException(origin: NSError) : IOException`. **A device install is
            // still what settles it**, and it is the same shape as every other iOS-only claim here.
            return ApiResult.Unreachable
        }

        if (response.status.isSuccess() && deserializer == null) {
            return ApiResult.Answered(Unit as T)
        }
        return decode(response) { text ->
            if (response.status.isSuccess()) {
                ApiResult.Answered(Protocol.json.decodeFromString(deserializer!!, text))
            } else {
                ApiResult.Refused(Protocol.json.decodeFromString<ApiError>(text))
            }
        }
    }

    private suspend fun <T> decode(
        response: HttpResponse,
        read: (String) -> ApiResult<T>,
    ): ApiResult<T> {
        val text = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // The status line arrived and the body did not. Nothing was read, so nothing was
            // judged, and the answer is the one every other half-delivered request gets.
            return ApiResult.Unreachable
        }

        return try {
            read(text)
        } catch (_: SerializationException) {
            unreadable(response, text)
        } catch (_: IllegalArgumentException) {
            // `SerializationException` is itself an `IllegalArgumentException`, so this second arm
            // is for the model's own guards: `SyncResponse`'s two `init` checks, a blank
            // `IdempotencyKey`, a coordinate off the edge of the map. `Sync.kt` asks for exactly
            // this — a response that cannot be read *coherently* is turned into `Malformed` here,
            // the way `GameSave.decode` turns a broken model invariant into a `Failure`, rather
            // than one the client renders half of.
            unreadable(response, text)
        }
    }

    // **What an unreadable body means depends on who sent it, and getting this wrong would be
    // expensive on the host this ships to.** Cloud Run scales to zero, so the first request after
    // an idle spell can be answered by the load balancer rather than by the server — a `502` or a
    // `503` carrying HTML, from something that never saw the colony. Read as `Malformed` that would
    // surface as an error and, worse, `ColonySync` would treat it as a settled answer and not queue
    // the verb. It is `Unreachable`, which is what it is.
    //
    // A `4xx` is the other way round: something *did* read the request and formed an opinion about
    // it, and a body this build cannot parse there is a disagreement about the contract — which is
    // what `ApiError.Malformed` is for. `detail` is a diagnostic and never player copy, exactly as
    // on the server: every word the game says is a `TextRes` built through `Strings`, and a
    // transport cannot build one.
    private fun <T> unreadable(response: HttpResponse, text: String): ApiResult<T> =
        if (response.status.value >= 500) {
            ApiResult.Unreachable
        } else {
            ApiResult.Refused(ApiError.Malformed("${response.status.value} answered ${text.take(200)}"))
        }
}
