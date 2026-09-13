package dev.fardavide.oltre.client.alliance.domain

import dev.fardavide.oltre.protocol.AllianceSeats

val AllianceSeats.free: Int
    get() = cap - taken
