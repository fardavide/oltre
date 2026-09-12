package dev.fardavide.oltre.server

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal object AllianceRules {

    fun isActive(lastSyncedAt: Instant, now: Instant): Boolean = lastSyncedAt >= now - INACTIVITY

    private val INACTIVITY = 30.days
}
