package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.AllianceProjectOffer
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.BuyProjectRequest
import dev.fardavide.oltre.protocol.TreasuryResponse
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock

// **Two routes onto one face** — what the pool holds, and spending it. `alliance-sheet.md` §1.3's
// rule at work: the alliance's five fields were argued to completeness before its routes shipped so
// that the treasury would have to be a new route rather than a sixth field, and a new route is free
// forever.

// `GET /v1/alliance/treasury`.
internal suspend fun readTreasury(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
): Answer = answering {
    val player = when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return@answering Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> caller.player
    }
    alliances.treasuryOf(player, clock.now()).answer()
}

// `POST /v1/alliance/projects`. Founder or admin; the repository checks the role, because it is the
// only thing holding the seat.
//
// **It retries a lost compare-and-set exactly as the sync does**, and for the same reason: two
// admins buying in the same second is one of them being told to read the pool again, not a failure
// worth showing anybody. `WRITE_ATTEMPTS` is shared rather than re-picked, so the two surfaces
// cannot drift into different patience.
internal suspend fun buyAllianceProject(
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
    val request = when (val read = readRequest(BuyProjectRequest.serializer(), body) { it.apiVersion }) {
        is Read.No -> return@answering read.answer
        is Read.Yes -> read.value
    }

    repeat(WRITE_ATTEMPTS) {
        val now = clock.now()
        // The version is read here rather than sent by the client, which is what every other
        // alliance act does: a client holding a version is a client that has to be told to refresh
        // one, and the alliance's own row is a read away.
        val current = when (val read = alliances.treasuryOf(player, now)) {
            is TreasuryRead.Refused -> return@answering read.answer()
            TreasuryRead.Stale -> return@repeat
            is TreasuryRead.Present -> read
        }
        when (val bought = alliances.buy(player, request.project, now, current.alliance.version)) {
            is TreasuryRead.Refused -> return@answering bought.answer()
            is TreasuryRead.Present -> return@answering bought.answer()
            TreasuryRead.Stale -> Unit
        }
    }
    Answer.Failed(HttpStatusCode.Conflict, ApiError.StaleAlliance)
}

// One place the three answers become statuses, so a read and a purchase cannot describe the same
// refusal two ways.
private fun TreasuryRead.answer(): Answer = when (this) {
    is TreasuryRead.Refused -> Answer.Failed(
        when (error) {
            ApiError.NotInAnAlliance, ApiError.NoSuchAlliance -> HttpStatusCode.NotFound
            ApiError.AllianceRoleTooLow -> HttpStatusCode.Forbidden
            // **Not 402 and not 400.** The request was well formed and the caller was allowed; the
            // pool is short, which is a fact about the alliance's state rather than about the
            // request — which is what `409` means and what `StaleColony` already uses it for.
            else -> HttpStatusCode.Conflict
        },
        error,
    )
    TreasuryRead.Stale -> Answer.Failed(HttpStatusCode.Conflict, ApiError.StaleAlliance)
    is TreasuryRead.Present -> Answer.Treasury(
        HttpStatusCode.OK,
        TreasuryResponse(
            apiVersion = ApiVersion.CURRENT,
            pool = alliance.pool,
            contributed = contributed,
            progress = AllianceBalance.progressOf(alliance.experience),
            projects = offers(),
        ),
    )
}

// **What is on sale, and what a project that can no longer do anything is instead: absent.** An
// entry that takes the pool and moves nothing is the dead control wearing a price tag, which is the
// failure the global rule calls worse than a crash — so an exhausted project leaves the list rather
// than sitting on it greyed with a price nobody should pay.
private fun TreasuryRead.Present.offers(): List<AllianceProjectOffer> = AllianceProject.entries
    .filterNot { AllianceBalance.isExhausted(it, alliance.alliance.level, alliance.seatsBought) }
    .map { project ->
        val cost = AllianceBalance.costOf(project, alliance.seatsBought)
        AllianceProjectOffer(
            project = project,
            cost = cost,
            affordable = alliance.pool.covers(cost),
            timesBought = alliance.seatsBought,
        )
    }
