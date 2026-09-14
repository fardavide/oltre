package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceResponse
import dev.fardavide.oltre.protocol.FoundingPriceResponse
import dev.fardavide.oltre.protocol.AllianceSearchResponse
import dev.fardavide.oltre.protocol.AllianceSearchCursor
import dev.fardavide.oltre.protocol.AllianceId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Base64
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceStanding
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.CreateAllianceRequest
import dev.fardavide.oltre.protocol.JoinAllianceRequest
import dev.fardavide.oltre.protocol.AnswerJoinRequest
import dev.fardavide.oltre.protocol.RenameAllianceRequest
import dev.fardavide.oltre.protocol.SetMemberRoleRequest
import dev.fardavide.oltre.protocol.KickMemberRequest
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.DeserializationStrategy

// ── Search ──

internal suspend fun searchAlliances(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    credentials: Credentials,
    q: String?,
    cursor: String?,
): Answer = answering {
    when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return@answering Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> Unit
    }
    if (q == null) return@answering Answer.Failed(HttpStatusCode.BadRequest, ApiError.Malformed("search requires a non-blank query"))
    val query = CanonicalAllianceName.normalised(q)
    if (query.value.isBlank()) return@answering Answer.Failed(HttpStatusCode.BadRequest, ApiError.Malformed("search requires a non-blank query"))
    if ('\u0000' in query.value) return@answering Answer.Failed(HttpStatusCode.BadRequest, ApiError.Malformed("search query contains a NUL character"))
    val after = if (cursor == null) null else try {
        AllianceSearchCursor(cursor).decoded(query)
    } catch (_: IllegalArgumentException) {
        return@answering Answer.Failed(HttpStatusCode.BadRequest, ApiError.Malformed("invalid search cursor"))
    } catch (_: IOException) {
        return@answering Answer.Failed(HttpStatusCode.BadRequest, ApiError.Malformed("invalid search cursor"))
    }
    val results = alliances.search(query, after, SEARCH_PAGE_SIZE + 1)
    val page = results.take(SEARCH_PAGE_SIZE)
    val next = if (results.size > SEARCH_PAGE_SIZE) {
        AllianceSearchPosition.from(page.last()).encoded(query)
    } else null
    Answer.Alliances(HttpStatusCode.OK, AllianceSearchResponse(ApiVersion.CURRENT, query.value, page.map { it.alliance }, next))
}

private const val SEARCH_PAGE_SIZE = 20
private const val SEARCH_CURSOR_ENCODING = 1
// NFKC can expand each of a display name's 32 characters into 18, and the cursor carries
// both the resulting name and its prefix. Leave room for their UTF encoding and Base64.
private const val MAX_SEARCH_CURSOR_LENGTH = 8_192

internal suspend fun readAlliance(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
): Answer = answering {
    val player = when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return@answering Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> caller.player
    }
    val standing = when (val affiliation = alliances.allianceOf(player, clock.now())) {
        Affiliation.Unaffiliated -> AllianceStanding.Unaffiliated
        is Affiliation.Petitioning -> AllianceStanding.Petitioning(affiliation.alliance.alliance)
        is Affiliation.Enlisted -> AllianceStanding.Enlisted(affiliation.alliance.alliance, affiliation.seat.role)
    }
    Answer.Alliance(HttpStatusCode.OK, AllianceResponse(ApiVersion.CURRENT, standing))
}

// `GET /v1/alliance/founding` — what founding one costs today. **A route rather than a constant the
// client holds**, so the balance round can move the price with a deploy; see `AllianceBalance
// .FOUNDING_PRICE` and `FoundingPriceResponse`.
//
// It authenticates and then reads no state at all, which is deliberate: the price is a fact about the
// world rather than about the caller, so a player deciding whether to save up is not also asking the
// database a question.
internal suspend fun foundingPrice(
    authenticator: Authenticator,
    credentials: Credentials,
): Answer = answering {
    when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> Answer.FoundingPrice(
            HttpStatusCode.OK,
            FoundingPriceResponse(ApiVersion.CURRENT, AllianceBalance.FOUNDING_PRICE),
        )
    }
}

// `POST /v1/alliance`. **Founding costs the colony `AllianceBalance.FOUNDING_PRICE`**, and the charge
// and the alliance land in one transaction inside `found` — see `AllianceRepository.found` for why
// the store owns the pair and `foundAlliance` in `core` for what a charge actually is.
//
// **The price is passed rather than looked up in there**, so the balance stays out of the store and
// the one place that decides what founding costs is this line.
//
// **No retry loop, unlike `buyAllianceProject`.** That one takes an optimistic version and can lose
// it; this one reads the colony under a row lock it holds to the commit, so there is no window to
// lose and nothing to try again.
//
// **A retried founding pays once.** `found` answers `AlreadyFounded` before it reaches the charge, so
// a client whose 201 was lost on the way home gets its alliance back for free — which is the same
// idempotence `foundColony` has one route over, arriving at the route that has no envelope to hang an
// `IdempotencyKey` on.
internal suspend fun foundAlliance(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
): Answer = answering {
    val player = when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return@answering Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> caller.player
    }
    val request = when (val read = readRequest(CreateAllianceRequest.serializer(), body) { it.apiVersion }) {
        is Read.No -> return@answering read.answer
        is Read.Yes -> read.value
    }
    val founded = alliances.found(
        player = player,
        name = request.name,
        tag = request.tag,
        now = clock.now(),
        price = AllianceBalance.FOUNDING_PRICE,
    )
    when (founded) {
        is Founded.Refused -> Answer.Failed(founded.error.foundingStatus(), founded.error)
        is Founded.Made -> Answer.Alliance(
            HttpStatusCode.Created,
            AllianceResponse(ApiVersion.CURRENT, AllianceStanding.Enlisted(founded.alliance.alliance, AllianceRole.FOUNDER)),
        )
        is Founded.AlreadyFounded -> Answer.Alliance(
            HttpStatusCode.OK,
            AllianceResponse(ApiVersion.CURRENT, AllianceStanding.Enlisted(founded.alliance.alliance, founded.role)),
        )
    }
}

// **`409` for everything except a colony that is not there**, which is `TreasuryRead.answer()`'s own
// split said for the other route: a taken name, an account already in an alliance and a colony too
// poor are all well-formed requests from an allowed caller that the world's state refuses, and that
// is what 409 means. A player with no colony at all has asked about something that does not exist.
//
// **Not `402 Payment Required` for the price**, which is the tempting one: that status is about
// paying *the service*, and reading it as "your colony is short" would be a second meaning nothing
// else on this server uses.
private fun ApiError.foundingStatus(): HttpStatusCode =
    if (this == ApiError.NoColony) HttpStatusCode.NotFound else HttpStatusCode.Conflict

internal suspend fun petitionAlliance(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
): Answer = answering {
    val player = when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return@answering Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> caller.player
    }
    val request = when (val read = readRequest(JoinAllianceRequest.serializer(), body) { it.apiVersion }) {
        is Read.No -> return@answering read.answer
        is Read.Yes -> read.value
    }
    repeat(WRITE_ATTEMPTS) {
        val now = clock.now()
        val target = when (val lookup = alliances.alliance(request.alliance, now)) {
            AllianceLookup.Absent -> return@answering Answer.Failed(HttpStatusCode.NotFound, ApiError.NoSuchAlliance)
            is AllianceLookup.Present -> lookup.alliance
        }
        when (val change = alliances.petition(player, request.alliance, now, target.version)) {
            AllianceChange.Stale -> Unit
            is AllianceChange.Refused -> return@answering Answer.Failed(HttpStatusCode.Conflict, change.error)
            is AllianceChange.Applied -> {
                val pending = change.affiliation
                check(pending is Affiliation.Petitioning) { "a petition was written but could not be read" }
                return@answering Answer.Alliance(HttpStatusCode.OK, AllianceResponse(ApiVersion.CURRENT, AllianceStanding.Petitioning(pending.alliance.alliance)))
            }
        }
    }
    Answer.Failed(HttpStatusCode.Conflict, ApiError.StaleAlliance)
}

internal suspend fun detachAlliance(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
): Answer = answering {
    val player = when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return@answering Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> caller.player
    }
    changedAllianceFor(alliances, clock, player) { target, _ -> alliances.detach(player, target.alliance.id, target.version) }
}

internal suspend fun answerAlliancePetition(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
): Answer = requestedAlliance(alliances, authenticator, clock, credentials, body, AnswerJoinRequest.serializer(), { it.apiVersion }) { player, target, request, now ->
    alliances.approve(player, target.alliance.id, request.request, request.decision, now, target.version)
}

internal suspend fun renameAlliance(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
): Answer = requestedAlliance(alliances, authenticator, clock, credentials, body, RenameAllianceRequest.serializer(), { it.apiVersion }) { player, target, request, _ ->
    alliances.rename(player, target.alliance.id, request.name, request.tag, target.version)
}

internal suspend fun setAllianceMemberRole(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
): Answer = requestedAlliance(alliances, authenticator, clock, credentials, body, SetMemberRoleRequest.serializer(), { it.apiVersion }) { player, target, request, _ ->
    alliances.setRole(player, target.alliance.id, request.member, request.role, target.version)
}

internal suspend fun removeAllianceMember(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
): Answer = requestedAlliance(alliances, authenticator, clock, credentials, body, KickMemberRequest.serializer(), { it.apiVersion }) { player, target, request, _ ->
    alliances.kick(player, target.alliance.id, request.member, target.version)
}

internal suspend fun disbandAlliance(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
): Answer = answering {
    val player = when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return@answering Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> caller.player
    }
    changedAllianceFor(alliances, clock, player) { target, _ -> alliances.disband(player, target.alliance.id, target.version) }
}

private suspend fun <T> requestedAlliance(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
    body: String,
    serializer: DeserializationStrategy<T>,
    versionOf: (T) -> ApiVersion,
    write: suspend (PlayerId, StoredAlliance, T, Instant) -> AllianceChange,
): Answer = answering {
    val player = when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return@answering Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> caller.player
    }
    val request = when (val read = readRequest(serializer, body, versionOf)) {
        is Read.No -> return@answering read.answer
        is Read.Yes -> read.value
    }
    changedAllianceFor(alliances, clock, player) { target, now -> write(player, target, request, now) }
}

private suspend fun changedAllianceFor(
    alliances: AllianceRepository,
    clock: Clock,
    player: PlayerId,
    write: suspend (StoredAlliance, Instant) -> AllianceChange,
): Answer {
    repeat(WRITE_ATTEMPTS) {
        val now = clock.now()
        val target = when (val affiliation = alliances.allianceOf(player, now)) {
            Affiliation.Unaffiliated -> return Answer.Failed(HttpStatusCode.Conflict, ApiError.NotInAnAlliance)
            is Affiliation.Enlisted -> affiliation.alliance
            is Affiliation.Petitioning -> affiliation.alliance
        }
        when (val change = write(target, now)) {
            AllianceChange.Stale -> Unit
            is AllianceChange.Refused -> return Answer.Failed(HttpStatusCode.Conflict, change.error)
            is AllianceChange.Applied -> return affiliationAnswer(change.affiliation)
        }
    }
    return Answer.Failed(HttpStatusCode.Conflict, ApiError.StaleAlliance)
}

private fun affiliationAnswer(affiliation: Affiliation): Answer.Alliance = Answer.Alliance(
    HttpStatusCode.OK,
    AllianceResponse(ApiVersion.CURRENT, when (affiliation) {
        Affiliation.Unaffiliated -> AllianceStanding.Unaffiliated
        is Affiliation.Enlisted -> AllianceStanding.Enlisted(affiliation.alliance.alliance, affiliation.seat.role)
        is Affiliation.Petitioning -> AllianceStanding.Petitioning(affiliation.alliance.alliance)
    }),
)

private fun AllianceSearchCursor.decoded(query: CanonicalAllianceName): AllianceSearchPosition {
    require(value.length <= MAX_SEARCH_CURSOR_LENGTH)
    val bytes = Base64.getUrlDecoder().decode(value)
    require(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == value)
    return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == SEARCH_CURSOR_ENCODING)
        require(input.readUTF() == query.value)
        val position = AllianceSearchPosition(input.readLong(), CanonicalAllianceName(input.readUTF()), AllianceId(input.readUTF()))
        require(position.experience >= 0)
        require('\u0000' !in position.name.value && '\u0000' !in position.id.value)
        require(position.name.value.startsWith(query.value))
        require(CanonicalAllianceName.normalised(position.name.value) == position.name)
        require(input.available() == 0)
        position
    }
}

private fun AllianceSearchPosition.encoded(query: CanonicalAllianceName): AllianceSearchCursor {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { output ->
        output.writeInt(SEARCH_CURSOR_ENCODING)
        output.writeUTF(query.value)
        output.writeLong(experience)
        output.writeUTF(name.value)
        output.writeUTF(id.value)
    }
    return AllianceSearchCursor(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray()))
}
