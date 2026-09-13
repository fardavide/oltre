package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.WorldVerdict
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.verdictFor
import dev.fardavide.oltre.core.worldAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.days

class ProbeReceiptBehaviourTest {

    @Test
    fun `opening a surveyed system reports the paid landing even when nothing is settleable`() {
        val target = targetWith { verdicts ->
            verdicts.none { it is WorldVerdict.Settleable || it is WorldVerdict.Blocked && it.failures.size == 1 }
        }
        val landed = landedAt(target)
        val receipt = landed.eventLog.filterIsInstance<Event.SurveyCompleted>().single { it.target == target }

        galaxyScreen(state = landed) {
            scrubTo(target.system)
            openTheSelectedSystem()

            assertTheFooterReads(English.resolve(Strings.worldsSurveyedCount(receipt.worldsFound)))
            assertTheFooterReads(English.resolve(Strings.findNone()))
            assertTheFooterReads("landed")
            assertTheFooterDoesNotRead("genesis")
            assertOffersNoFlight()
        }
    }

    @Test
    fun `opening a landed probe reports worlds blocked on one axis as near misses`() {
        val target = targetWith { verdicts ->
            verdicts.none { it is WorldVerdict.Settleable } &&
                verdicts.any { it is WorldVerdict.Blocked && it.failures.size == 1 }
        }
        val landed = landedAt(target)
        val nearMisses = verdictsAt(target, landed).count { it is WorldVerdict.Blocked && it.failures.size == 1 }

        galaxyScreen(state = landed) {
            scrubTo(target.system)
            openTheSelectedSystem()

            assertTheFooterReads(English.resolve(Strings.findNearMiss(nearMisses)))
            assertTheFooterDoesNotRead(English.resolve(Strings.findNone()))
            assertOffersNoFlight()
        }
    }

    @Test
    fun `opening a landed probe names the settleable worlds the flight found`() {
        val target = targetWith { verdicts -> verdicts.any { it is WorldVerdict.Settleable } }
        val landed = landedAt(target)
        val settleable = verdictsAt(target, landed).count { it is WorldVerdict.Settleable }

        galaxyScreen(state = landed) {
            scrubTo(target.system)
            openTheSelectedSystem()

            assertTheFooterReads(English.resolve(Strings.findSettleable(settleable)))
            assertTheFooterDoesNotRead(English.resolve(Strings.findNone()))
            assertOffersNoFlight()
        }
    }
}

private fun targetWith(matches: (List<WorldVerdict>) -> Boolean): SystemAddress =
    (1..GalaxyBalance.SYSTEMS_PER_GALAXY).asSequence()
        .filter { it != testGameState.galaxy.home.system }
        .map { SystemAddress(galaxy = testGameState.galaxy.home.galaxy, system = it) }
        .first { target ->
            val surveyed = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .map { GalaxyCoordinate(target.galaxy, target.system, it) }
                .filter { worldAt(testGameState.galaxy.seed, it) != null }
            val inspected = testGameState.copy(
                galaxy = testGameState.galaxy.copy(surveyed = testGameState.galaxy.surveyed + surveyed),
            )
            val verdicts = verdictsAt(target, inspected)
            verdicts.isNotEmpty() && matches(verdicts)
        }

private fun verdictsAt(target: SystemAddress, state: GameState): List<WorldVerdict> =
    (1..GalaxyBalance.SLOTS_PER_SYSTEM).mapNotNull { slot ->
        worldAt(state.galaxy.seed, GalaxyCoordinate(target.galaxy, target.system, slot))
    }.map { verdictFor(it, state) }

private fun landedAt(target: SystemAddress): GameState {
    val departure = FIXTURE_NOW - 3.days
    val started = assertIs<StartSurveyResult.Started>(
        startSurvey(testGameState.copy(resources = Resources.of(metal = 100_000)), target, at = departure),
    )
    val landed = advance(started.state, from = departure, to = FIXTURE_NOW)
    assertEquals(emptyList(), landed.surveys)
    return landed
}
