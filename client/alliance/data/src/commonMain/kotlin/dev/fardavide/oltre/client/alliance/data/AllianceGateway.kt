package dev.fardavide.oltre.client.alliance.data

import dev.fardavide.oltre.client.alliance.domain.AllianceRosterReading
import dev.fardavide.oltre.client.alliance.domain.AllianceState
import dev.fardavide.oltre.client.net.data.ApiResult
import dev.fardavide.oltre.client.net.data.OltreApi
import dev.fardavide.oltre.client.net.data.SessionKeeper
import dev.fardavide.oltre.client.net.data.renewing
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSearchCursor
import dev.fardavide.oltre.protocol.AllianceSearchResponse
import dev.fardavide.oltre.protocol.AllianceStanding
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.JoinDecision
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.TreasuryResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AllianceGateway(
    private val api: OltreApi,
    private val sessions: SessionKeeper,
) {

    var standing: AllianceState = AllianceState.Unread
        private set

    private val membership = Mutex()

    suspend fun alliance(access: SessionToken): ApiResult<AllianceState> = membership.withLock {
        when (val answer = sessions.renewing(access) { read(it) }) {
            is ApiResult.Answered -> {
                standing = answer.value
                answer
            }
            is ApiResult.Refused -> answer
            ApiResult.Unreachable -> ApiResult.Unreachable
        }
    }

    suspend fun setMemberRole(
        access: SessionToken,
        member: AllianceMemberId,
        role: AllianceRole,
    ): ApiResult<AllianceState> = change(access) { api.setMemberRole(it, member, role) }

    suspend fun leaveAlliance(access: SessionToken): ApiResult<AllianceState> =
        change(access) { api.leaveAlliance(it) }

    suspend fun disbandAlliance(access: SessionToken): ApiResult<AllianceState> =
        change(access) { api.disbandAlliance(it) }

    suspend fun createAlliance(
        access: SessionToken,
        name: AllianceName,
        tag: AllianceTag,
    ): ApiResult<AllianceState> = change(access) { api.createAlliance(it, name, tag) }

    suspend fun renameAlliance(
        access: SessionToken,
        name: AllianceName,
        tag: AllianceTag,
    ): ApiResult<AllianceState> = change(access) { api.renameAlliance(it, name, tag) }

    suspend fun requestToJoin(access: SessionToken, alliance: AllianceId): ApiResult<AllianceState> =
        change(access) { api.requestToJoin(it, alliance) }

    suspend fun answerRequest(
        access: SessionToken,
        request: JoinRequestId,
        decision: JoinDecision,
    ): ApiResult<AllianceState> = change(access) { api.answerRequest(it, request, decision) }

    suspend fun removeMember(access: SessionToken, member: AllianceMemberId): ApiResult<AllianceState> =
        change(access) { api.removeMember(it, member) }

    suspend fun searchAlliances(
        access: SessionToken,
        query: String,
        cursor: AllianceSearchCursor?,
    ): ApiResult<AllianceSearchResponse> = sessions.renewing(access) { api.searchAlliances(it, query, cursor) }

    private suspend fun change(
        access: SessionToken,
        call: suspend (SessionToken) -> ApiResult<AllianceStanding>,
    ): ApiResult<AllianceState> = membership.withLock {
        when (val answer = sessions.renewing(access, call)) {
            is ApiResult.Answered -> {
                standing = when (val value = answer.value) {
                    AllianceStanding.Unaffiliated -> AllianceState.Unaffiliated
                    is AllianceStanding.Petitioning -> AllianceState.Petitioning(value.alliance)
                    is AllianceStanding.Enlisted -> AllianceState.Enlisted(
                        value.alliance, value.role, AllianceRosterReading.Unread,
                    )
                }
                ApiResult.Answered(standing)
            }
            is ApiResult.Refused -> answer
            ApiResult.Unreachable -> ApiResult.Unreachable
        }
    }

    private suspend fun read(access: SessionToken): ApiResult<AllianceState> = when (val answer = api.alliance(access)) {
        is ApiResult.Answered -> when (val value = answer.value) {
            AllianceStanding.Unaffiliated -> ApiResult.Answered(AllianceState.Unaffiliated)
            is AllianceStanding.Petitioning -> ApiResult.Answered(AllianceState.Petitioning(value.alliance))
            is AllianceStanding.Enlisted -> roster(access, value)
        }
        is ApiResult.Refused -> answer
        ApiResult.Unreachable -> ApiResult.Unreachable
    }

    private suspend fun roster(
        access: SessionToken,
        enlisted: AllianceStanding.Enlisted,
    ): ApiResult<AllianceState> = when (val answer = api.allianceRoster(access)) {
        is ApiResult.Answered -> ApiResult.Answered(AllianceState.Enlisted(
            enlisted.alliance, enlisted.role, AllianceRosterReading.Read(answer.value),
        ))
        is ApiResult.Refused -> answer
        ApiResult.Unreachable -> ApiResult.Unreachable
    }

    // ── The treasury ────────────────────────────────────────────────────────────────────────
    //
    // **Its own read, not folded into `alliance()` above**, and the split is the feature's own
    // shape rather than a convenience. The roster is what makes an alliance a *place* — no roster,
    // no screen — so `alliance()` is not answered until it lands. The treasury is a panel on that
    // screen: a server mid-deploy that refuses it leaves an alliance perfectly readable with its
    // pool unread, which is a state the face already draws, and folding it in would have meant
    // taking the roster away to say the pool could not be fetched.
    //
    // It also keeps the cost honest. `alliance()` is called on every launch and every idle minute
    // the standing is unread; a third request on that path would be paid by every check-in, and
    // most check-ins never open this tab.
    suspend fun treasury(access: SessionToken): ApiResult<TreasuryResponse> =
        sessions.renewing(access) { api.treasury(it) }

    // **Buying answers with the whole face**, on `AllianceResponse`'s own shape: the pool, the
    // level and the progress come back together, so a control never has to fire a second request to
    // know what it did.
    //
    // What it does *not* carry is the seat cap, which is on the alliance rather than the treasury —
    // so a purchase that widens the roster needs `alliance()` again to see it. That is the caller's
    // to do and the shell does it, rather than this method quietly making two requests.
    suspend fun buyProject(access: SessionToken, project: AllianceProject): ApiResult<TreasuryResponse> =
        sessions.renewing(access) { api.buyProject(it, project) }
}
