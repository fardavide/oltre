package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceResponse
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceStanding
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.CreateAllianceRequest
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock

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
        is Founded.Made -> Answer.Alliance(
            HttpStatusCode.Created,
            AllianceResponse(ApiVersion.CURRENT, AllianceStanding.Enlisted(founded.alliance.alliance, AllianceRole.FOUNDER)),
        )
        is Founded.AlreadyFounded -> Answer.Alliance(
            HttpStatusCode.OK,
            AllianceResponse(ApiVersion.CURRENT, AllianceStanding.Enlisted(founded.alliance.alliance, AllianceRole.FOUNDER)),
        )
    }
}
