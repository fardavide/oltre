package dev.fardavide.oltre.client.dispatch.presentation

import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

// **The one rule the two doors share, tested where it lives rather than through either of them.**
// Galaxy and Fleets both raise the dispatch sheet and rule 5 stops either seeing the other, so this
// is the only place the rule is written down once — and a `copy` that drifted here would drift on
// both tabs at once. `DispatchSheetBehaviourTest` and `FleetsFromStateBehaviourTest` assert that the
// screens actually call these; this asserts what they do.
class DispatchSelectionTest {

    @Test
    fun `a window drops the manifest so the sheet re-derives it`() {
        // A rung is a change of ask rather than a change of schedule: a longer stay means a smaller
        // fleet takes the same vein, so a count chosen against the old rung is arithmetic about a
        // run that no longer exists. Null is how the mapper is told to work it out again.
        val stepped = selection.copy(ships = 40)

        val homed = stepped.homingIn(24.hours)

        assertEquals(24.hours, homed.window)
        assertNull(homed.ships)
    }

    @Test
    fun `a currency drops the manifest for the same reason`() {
        // The other axis, and Davide's call of 2026-08-17 asked as an option beside the window: the
        // two deposits are different sizes, so a count suggested for one is about the wrong world.
        val stepped = selection.copy(ships = 40)

        val brought = stepped.bringingBack(ResourceKind.CRYSTAL)

        assertEquals(ResourceKind.CRYSTAL, brought.gathering)
        assertNull(brought.ships)
    }

    @Test
    fun `neither touches the other axis or the target`() {
        // The reset is one field. A rung that also moved the currency would be the sheet answering a
        // question nobody asked, and a target that moved would be a run sent somewhere else.
        val asked = DispatchSelection(
            at = target,
            gathering = ResourceKind.CRYSTAL,
            ships = 12,
            window = 3.hours,
        )

        assertEquals(ResourceKind.CRYSTAL, asked.homingIn(24.hours).gathering)
        assertEquals(3.hours, asked.bringingBack(ResourceKind.METAL).window)
        assertEquals(target, asked.homingIn(24.hours).at)
        assertEquals(target, asked.bringingBack(ResourceKind.METAL).at)
    }

    private val target = GalaxyCoordinate(galaxy = 3, system = 178, slot = 8)

    private val selection = DispatchSelection(at = target, gathering = null, ships = null, window = null)
}
