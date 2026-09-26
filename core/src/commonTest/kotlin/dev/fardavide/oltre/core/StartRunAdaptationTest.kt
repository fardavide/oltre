package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class StartRunAdaptationTest {

    private val ships = Ships.of(ShipType.SKIFF, 1)
    private val at = Instant.fromEpochMilliseconds(0)

    @Test
    fun `a surveyed planet cannot be harvested before its adaptation requirements are met`() {
        val initial = GameState.initial()
        val neighbouringSystem = SystemAddress(
            galaxy = initial.galaxy.home.galaxy,
            system = initial.galaxy.home.system + 1,
        )
        val ships = Ships.of(ShipType.SKIFF, 1)
        val state = initial.copy(
            ships = ships,
            galaxy = initial.galaxy.copy(
                surveyed = initial.galaxy.surveyed +
                    GalaxyState.occupiedWorldsIn(initial.galaxy.seed, neighbouringSystem),
            ),
        )
        val target = state.galaxy.surveyed
            .mapNotNull { worldAt(state.galaxy.seed, it) }
            .first { verdictFor(it, state) is WorldVerdict.Blocked }

        val result = startRun(
            state = state,
            target = target.at,
            gathering = ResourceKind.METAL,
            ships = ships,
            window = 24.hours,
            at = Instant.fromEpochMilliseconds(0),
        )

        assertIs<StartRunResult.NotAValidTarget>(result)
    }

    @Test
    fun `one missing thermal level blocks metal and crystal harvesting`() {
        assertMissingLevelBlocksHarvesting(AdaptationTechnology.THERMAL)
    }

    @Test
    fun `one missing gravitic level blocks metal and crystal harvesting`() {
        assertMissingLevelBlocksHarvesting(AdaptationTechnology.GRAVITIC)
    }

    @Test
    fun `one missing atmospheric level blocks metal and crystal harvesting`() {
        assertMissingLevelBlocksHarvesting(AdaptationTechnology.ATMOSPHERIC)
    }

    @Test
    fun `meeting every adaptation requirement exactly permits metal harvesting`() {
        assertExactRequirementsPermitHarvesting(ResourceKind.METAL)
    }

    @Test
    fun `meeting every adaptation requirement exactly permits crystal harvesting`() {
        assertExactRequirementsPermitHarvesting(ResourceKind.CRYSTAL)
    }

    @Test
    fun `a planet requiring no adaptation permits metal and crystal harvesting without research`() {
        val state = surveyedState()
        val world = state.galaxy.surveyed
            .mapNotNull { worldAt(state.galaxy.seed, it) }
            .first {
                val verdict = verdictFor(it, state)
                verdict is WorldVerdict.Barren || verdict is WorldVerdict.Settleable
            }
        assertEquals(Research.initial(), state.research)

        for (resource in listOf(ResourceKind.METAL, ResourceKind.CRYSTAL)) {
            assertIs<StartRunResult.Started>(
                startRun(state, world.at, resource, ships, 24.hours, at),
                "$resource is available without adaptation on a tolerant planet",
            )
        }
    }

    @Test
    fun `meeting only one of several adaptation requirements still blocks harvesting`() {
        val state = surveyedState()
        val world = worldRequiringEveryAdaptation(state)
        val failure = assertIs<WorldVerdict.Blocked>(verdictFor(world, state)).failures.first()
        val partlyAdapted = state.copy(
            research = state.research.withLevel(failure.axis.adaptation, TechLevel(failure.closedAtLevel)),
        )

        for (resource in listOf(ResourceKind.METAL, ResourceKind.CRYSTAL)) {
            assertIs<StartRunResult.NotAValidTarget>(
                startRun(partlyAdapted, world.at, resource, ships, 24.hours, at),
                "$resource must wait for every required adaptation",
            )
        }
    }

    @Test
    fun `refusing a blocked planet preserves ships deposits resources and the event log`() {
        val state = surveyedState()
        val world = worldRequiringEveryAdaptation(state)
        val before = state.copy(
            galaxy = state.galaxy.copy(deposits = state.galaxy.deposits.toList()),
            runs = state.runs.toList(),
            eventLog = state.eventLog.toList(),
        )

        val result = startRun(state, world.at, ResourceKind.CRYSTAL, ships, 24.hours, at)

        assertIs<StartRunResult.NotAValidTarget>(result)
        assertEquals(before, state)
    }

    @Test
    fun `unlocking and over researching a planet leave its advertised deposit cap unchanged`() {
        val initial = surveyedState()
        val world = worldRequiringEveryAdaptation(initial)
        val advertised = initial.galaxy.depositCap(world.at, ResourceKind.METAL)
        val research = AdaptationTechnology.entries.fold(Research.initial()) { levels, technology ->
            levels.withLevel(technology, TechLevel(TechLevel.MAX))
        }
        val started = assertIs<StartRunResult.Started>(
            startRun(initial.copy(research = research), world.at, ResourceKind.METAL, ships, 24.hours, at),
        ).state

        assertEquals(advertised, started.galaxy.depositCap(world.at, ResourceKind.METAL))
        assertEquals(
            requireNotNull(advertised) - started.runs.single().cargo.metal,
            started.galaxy.remaining(world.at, ResourceKind.METAL, at),
        )
    }

    @Test
    fun `an old partially depleted save keeps its stock and refills against the new cap`() {
        val initial = surveyedState()
        val world = worldRequiringEveryAdaptation(initial)
        val legacy = initial.copy(galaxy = initial.galaxy.copy(deposits = listOf(
            WorldDeposit(world.at, 200 * Resources.FINE_PER_UNIT, 100 * Resources.FINE_PER_UNIT, at),
        )))
        val restored = assertIs<DecodeResult.Success>(GameSave.decode(
            GameSave.encode(GameSnapshot(lastUpdatedAt = at, state = legacy)),
        )).snapshot.state
        val cap = requireNotNull(restored.galaxy.depositCap(world.at, ResourceKind.METAL))

        assertEquals(200, restored.galaxy.remaining(world.at, ResourceKind.METAL, at))
        assertEquals(200 + cap / 20, restored.galaxy.remaining(world.at, ResourceKind.METAL, at + 1.days))
        assertEquals(cap, restored.galaxy.remaining(world.at, ResourceKind.METAL, at + 20.days))
    }

    @Test
    fun `a saved run to a blocked planet still returns its ships and cargo`() {
        val initial = surveyedState()
        val world = worldRequiringEveryAdaptation(initial)
        val adapted = initial.copy(research = researchMeetingRequirements(initial, world))
        val dispatched = assertIs<StartRunResult.Started>(
            startRun(adapted, world.at, ResourceKind.METAL, ships, 24.hours, at),
        ).state
        // Earlier builds could dispatch without adaptation. Preserve that already committed run.
        val legacy = dispatched.copy(
            research = Research.initial(),
            // Saved cargo is a promise made under the old reward curve, not a fresh estimate.
            runs = dispatched.runs.map { it.copy(cargo = Resources.of(metal = 123)) },
        )
        val saved = GameSave.encode(GameSnapshot(lastUpdatedAt = at, state = legacy))
        val restored = assertIs<DecodeResult.Success>(GameSave.decode(saved)).snapshot
        val run = restored.state.runs.single()
        assertIs<WorldVerdict.Blocked>(verdictFor(world, restored.state))
        assertEquals(123, run.cargo.metal)
        val productionOnly = advance(
            restored.state.copy(runs = emptyList()),
            from = restored.lastUpdatedAt,
            to = run.returnsAt,
        )

        val returned = advance(restored.state, from = restored.lastUpdatedAt, to = run.returnsAt)

        assertTrue(returned.runs.isEmpty())
        assertEquals(ships, returned.ships)
        assertEquals(productionOnly.resources.metal + run.cargo.metal, returned.resources.metal)
        assertEquals(productionOnly.resources.crystal + run.cargo.crystal, returned.resources.crystal)
        assertEquals(productionOnly.resources.deuterium + run.cargo.deuterium, returned.resources.deuterium)
        assertEquals(
            Event.FleetReturned(from = world.at, ships = ships, cargo = run.cargo, at = run.returnsAt),
            returned.eventLog.last(),
        )
    }

    private fun assertMissingLevelBlocksHarvesting(technology: AdaptationTechnology) {
        val initial = surveyedState()
        val world = worldRequiringEveryAdaptation(initial)
        val adapted = researchMeetingRequirements(initial, world)
        val state = initial.copy(
            research = adapted.withLevel(technology, TechLevel(adapted.levelOf(technology).value - 1)),
        )
        val failure = assertIs<WorldVerdict.Blocked>(verdictFor(world, state)).failures.single()
        assertEquals(technology, failure.axis.adaptation)

        for (resource in listOf(ResourceKind.METAL, ResourceKind.CRYSTAL)) {
            assertIs<StartRunResult.NotAValidTarget>(
                startRun(state, world.at, resource, ships, 24.hours, at),
                "$resource must wait for the final $technology level",
            )
        }
    }

    private fun assertExactRequirementsPermitHarvesting(resource: ResourceKind) {
        val initial = surveyedState()
        val world = worldRequiringEveryAdaptation(initial)
        val state = initial.copy(research = researchMeetingRequirements(initial, world))

        val result = startRun(state, world.at, resource, ships, 24.hours, at)

        val started = assertIs<StartRunResult.Started>(result)
        val run = started.state.runs.single()
        assertEquals(world.at, run.target)
        assertEquals(resource, run.gathering)
        assertTrue(run.cargo.metal + run.cargo.crystal > 0)
    }

    private fun surveyedState(): GameState {
        val initial = GameState.initial()
        val surveyed = (1..12).flatMap { distance ->
            GalaxyState.occupiedWorldsIn(
                initial.galaxy.seed,
                SystemAddress(initial.galaxy.home.galaxy, initial.galaxy.home.system + distance),
            )
        }
        return initial.copy(
            ships = ships,
            galaxy = initial.galaxy.copy(surveyed = initial.galaxy.surveyed + surveyed),
        )
    }

    private fun worldRequiringEveryAdaptation(state: GameState): World = state.galaxy.surveyed
        .mapNotNull { worldAt(state.galaxy.seed, it) }
        .first { world ->
            val verdict = verdictFor(world, state)
            verdict is WorldVerdict.Blocked && verdict.failures.size == AdaptationTechnology.entries.size
        }

    private fun researchMeetingRequirements(state: GameState, world: World): Research =
        assertIs<WorldVerdict.Blocked>(verdictFor(world, state)).failures.fold(state.research) { research, failure ->
            research.withLevel(failure.axis.adaptation, TechLevel(failure.closedAtLevel))
        }
}
