package dev.fardavide.oltre.sim

import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.advance
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// Headless balancing harness. Never ships. Grows fast-forward scenarios as core gains rules.
fun main() {
    val start = Instant.fromEpochMilliseconds(0)
    var state = GameState.initial()
    for (day in 1..7) {
        state = advance(state, from = start + (day - 1).days, to = start + day.days)
        println("day $day: metal=${state.resources.metal}")
    }
}
