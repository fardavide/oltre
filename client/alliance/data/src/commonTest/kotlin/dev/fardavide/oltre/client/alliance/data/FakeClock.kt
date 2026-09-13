package dev.fardavide.oltre.client.alliance.data

import kotlin.time.Clock
import kotlin.time.Instant

class FakeClock : Clock {
    override fun now(): Instant = Instant.parse("2026-08-26T09:00:00Z")
}
