package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.Protocol
import dev.fardavide.oltre.protocol.SyncRequest
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import io.ktor.client.HttpClient
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

    override suspend fun foundColony(player: PlayerHandle): ApiResult<SyncResponse> =
        post("/v1/colony", player, emptyList())

    override suspend fun sync(player: PlayerHandle, envelopes: List<VerbEnvelope>): ApiResult<SyncResponse> =
        post("/v1/sync", player, envelopes)

    private suspend fun post(
        path: String,
        player: PlayerHandle,
        envelopes: List<VerbEnvelope>,
    ): ApiResult<SyncResponse> {
        val response = try {
            client.post(baseUrl + path) {
                header(Protocol.PLAYER_HEADER, player.value)
                contentType(ContentType.Application.Json)
                // **Encoded by hand rather than by content negotiation, and the server does the
                // same in the other direction and for the same reason** — see `admit` in
                // `Endpoints.kt`. `Protocol.json` is the one codec both ends speak, and the
                // properties that make it that one (`encodeDefaults`, and deliberately no
                // `ignoreUnknownKeys`) are the contract rather than a preference. A plugin
                // configured with a different dialect would be a client the server cannot read,
                // and nothing would say so.
                //
                // **The version is stated here and not by the caller.** Which contract this build
                // speaks is a fact about the build; a parameter would be an invitation to send the
                // wrong one.
                setBody(Protocol.json.encodeToString(SyncRequest(ApiVersion.CURRENT, envelopes)))
            }
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

        return if (response.status.isSuccess()) {
            decode(response) { ApiResult.Answered(Protocol.json.decodeFromString<SyncResponse>(it)) }
        } else {
            decode(response) { ApiResult.Refused(Protocol.json.decodeFromString<ApiError>(it)) }
        }
    }

    private suspend fun decode(
        response: HttpResponse,
        read: (String) -> ApiResult<SyncResponse>,
    ): ApiResult<SyncResponse> {
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
    private fun unreadable(response: HttpResponse, text: String): ApiResult<SyncResponse> =
        if (response.status.value >= 500) {
            ApiResult.Unreachable
        } else {
            ApiResult.Refused(ApiError.Malformed("${response.status.value} answered ${text.take(200)}"))
        }
}
