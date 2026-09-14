package dev.fardavide.oltre.client.alliance.domain

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceRosterResponse

sealed interface AllianceState {

    data object Unread : AllianceState

    data object Unaffiliated : AllianceState

    data class Petitioning(val alliance: Alliance) : AllianceState

    // **No treasury on here, deliberately.** The roster is what makes an alliance a place and is
    // read with the standing; the pool is a panel on the screen that standing produces, read on its
    // own — see `AllianceGateway.treasury`. Putting it here would have made every launch pay for a
    // request most check-ins never look at.
    data class Enlisted(
        val alliance: Alliance,
        val role: AllianceRole,
        val roster: AllianceRosterReading,
    ) : AllianceState
}

sealed interface AllianceRosterReading {

    data object Unread : AllianceRosterReading

    data class Read(val value: AllianceRosterResponse) : AllianceRosterReading
}
