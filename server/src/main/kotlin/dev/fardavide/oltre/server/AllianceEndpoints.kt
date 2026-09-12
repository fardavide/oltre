package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceResponse
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
    when (val founded = alliances.found(player, request.name, request.tag, clock.now())) {
        is Founded.Refused -> Answer.Failed(HttpStatusCode.Conflict, founded.error)
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
