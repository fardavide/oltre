package dev.fardavide.oltre.client.alliance.domain

import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceRosterResponse

sealed interface AllianceState {

    data object Unread : AllianceState

    data object Unaffiliated : AllianceState

    data class Petitioning(val alliance: Alliance) : AllianceState

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
