package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceRosterResponse
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ApiError
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock

internal suspend fun readAllianceRoster(
    alliances: AllianceRepository,
    authenticator: Authenticator,
    clock: Clock,
    credentials: Credentials,
): Answer = answering {
    val player = when (val caller = authenticator.identify(credentials)) {
        is Caller.Refused -> return@answering Answer.Failed(HttpStatusCode.Unauthorized, caller.error)
        is Caller.Known -> caller.player
    }
    when (val roster = alliances.rosterOf(player, clock.now())) {
        is RosterRead.Refused -> Answer.Failed(
            if (roster.error == ApiError.NoColony) HttpStatusCode.NotFound else HttpStatusCode.Conflict,
            roster.error,
        )
        is RosterRead.Present -> Answer.Roster(
            HttpStatusCode.OK,
            AllianceRosterResponse(ApiVersion.CURRENT, roster.members, roster.pending),
        )
    }
}
