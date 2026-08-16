package dev.fardavide.oltre.client.fleets.presentation

import dev.fardavide.oltre.client.fleets.ui.FleetsUiState
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **What the card has to be able to say about a manifest it did not choose.** `startRun` will only
// dispatch hulls the colony owns and only the skiff is buildable — but `FleetRun` accepts any
// `Ships`, a save carries whatever it carries, and slice 4 puts a second hull in a real manifest.
// A screen that could only name a skiff would be a screen that breaks on the release that ships the
// hauler, and nothing in the other tests would notice.
class FleetsManifestTest {

    @Test
    fun `every hull the model can carry has a name on the card`() {
        for (type in ShipType.entries) {
            val one = manifestOf(Ships.of(type, 1))
            val many = manifestOf(Ships.of(type, 3))

            assertTrue(one.startsWith("1 "), "$type reads '$one'")
            assertTrue(many.startsWith("3 "), "$type reads '$many'")
            // Singular and plural are different strings, which is the whole reason the count is
            // passed in rather than the name being fixed.
            assertTrue(many.substringBefore(" ·") != one.substringBefore(" ·").replace("1 ", "3 "))
        }
    }

    @Test
    fun `a mixed manifest names both hulls in ship order`() {
        val manifest = manifestOf(Ships(mapOf(ShipType.HAULER to 1, ShipType.SKIFF to 2)))

        assertTrue(manifest.startsWith("2 skiffs · 1 hauler · "), manifest)
    }

    @Test
    fun `a landing that brought deuterium is reported as deuterium`() {
        // Unreachable through `startRun` — a run's `gathering` is guarded to metal or crystal — but
        // `Event.FleetReturned` is the wider type and a ledger that answered "+0 metal" about a
        // landing that brought something would be lying about the one thing it exists to report.
        val state = GameState.initial(SEED).copy(
            eventLog = listOf(
                Event.FleetReturned(
                    from = GalaxyCoordinate(galaxy = 3, system = 171, slot = 10),
                    ships = Ships.of(ShipType.SKIFF, 1),
                    cargo = Resources.of(deuterium = 7),
                    at = EPOCH,
                ),
            ),
        )

        val row = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC).worked!!.rows.single()

        assertEquals("7 deuterium", row.total)
        assertEquals(ResourceKind.DEUTERIUM, row.kind)
    }

    @Test
    fun `a landing that brought nothing still names a resource rather than crashing`() {
        // A run with no target and no cargo, which is the emptiest thing the log can hold. It has no
        // world to be a row of, so what it reaches is the foot line — and that line still has to
        // name a resource rather than divide by an absence.
        val state = GameState.initial(SEED).copy(
            eventLog = listOf(
                Event.FleetReturned(
                    from = null,
                    ships = Ships.of(ShipType.SKIFF, 1),
                    cargo = Resources.of(),
                    at = EPOCH,
                ),
            ),
        )

        val worked = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC).worked!!

        assertEquals("1 earlier run · 0 metal · no target recorded", worked.unrecorded)
    }

    @Test
    fun `a run gathering crystal says crystal on its card`() {
        // The run card's own resource word, which every other test in this file reaches with metal —
        // so the crystal arm of the `when` was written and never executed.
        val state = GameState.initial(SEED).copy(
            runs = listOf(
                FleetRun(
                    target = GalaxyCoordinate(galaxy = 3, system = 171, slot = 10),
                    ships = Ships.of(ShipType.SKIFF, 1),
                    gathering = ResourceKind.CRYSTAL,
                    cargo = Resources.of(crystal = 52),
                    dispatchedAt = EPOCH,
                    returnsAt = EPOCH + 3.hours,
                ),
            ),
        )

        val card = state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC).runs.single()

        assertEquals("1 skiff · 52 crystal", card.manifest)
    }

    // **No deuterium card test, and the absence is the point.** `FleetRun`'s own constructor throws
    // — *"a run never gathers deuterium"* — so `core` makes the state unrepresentable and the third
    // arm of the card's `when` is dead by invariant rather than by omission. Kotlin needs the arm for
    // exhaustiveness; nothing in the game can reach it, and a test that constructed one would be
    // asserting against a colony that cannot exist.

    private fun manifestOf(ships: Ships): String {
        val state = GameState.initial(SEED).copy(
            runs = listOf(
                FleetRun(
                    target = GalaxyCoordinate(galaxy = 3, system = 171, slot = 10),
                    ships = ships,
                    gathering = ResourceKind.METAL,
                    cargo = Resources.of(metal = 132),
                    dispatchedAt = EPOCH,
                    returnsAt = EPOCH + 3.hours,
                ),
            ),
        )
        return state.toFleetsUiState(now = EPOCH, timeZone = TimeZone.UTC).runs.single().manifest
    }

    private companion object {
        val SEED = GalaxySeed(20_260_807L)
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
