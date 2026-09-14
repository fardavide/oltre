package dev.fardavide.oltre.client.alliance.presentation

import dev.fardavide.oltre.core.Resources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// **The ladder's arithmetic, which is the one thing on this screen a player is entitled to hold the
// app to.** Each chip prints a figure and then sends a basket, and those two must be the same
// number: a chip that sends more than it states is the worst kind of wrong on a control nothing can
// undo.
class ContributeChipTest {

    @Test
    // No comma in this name — Kotlin/Native rejects one inside a backtick in a `commonTest` source
    // set of a module with Apple targets, where the JVM accepts it. It has taken CI down twice.
    fun `the three shares are a tenth a quarter and a half and the fourth is everything`() {
        val chips = contributeChips(Resources.of(metal = 1_000, crystal = 400, deuterium = 200), live = true)

        assertEquals(
            listOf(100L, 250L, 500L, 1_000L),
            chips.map { it.metal },
        )
        assertEquals(listOf(40L, 100L, 200L, 400L), chips.map { it.crystal })
        assertEquals(listOf(20L, 50L, 100L, 200L), chips.map { it.deuterium })
    }

    // **Floored, never rounded.** 33 at 10% is 3.3, and a chip that sent 4 would be sending more
    // than the figure above it says.
    @Test
    fun `a share that does not divide evenly rounds down`() {
        val chips = contributeChips(Resources.of(metal = 33, crystal = 7, deuterium = 1), live = true)

        assertEquals(3L, chips.first().metal)
        assertEquals(0L, chips.first().crystal)
        assertEquals(0L, chips.first().deuterium)
    }

    // `All` is the only one that confirms — Davide, 2026-09-14. It is the tap that empties a colony.
    @Test
    fun `only everything asks first`() {
        val chips = contributeChips(Resources.of(metal = 1_000), live = true)

        assertEquals(listOf(false, false, false, true), chips.map { it.confirms })
    }

    // **`core`'s `NothingOffered` refusal, one layer earlier.** A colony at four metal has three
    // chips that floor to nothing, and a chip that presses and changes nothing is the dead control
    // in its purest form.
    @Test
    fun `a chip that would send nothing does not press`() {
        val chips = contributeChips(Resources.of(metal = 4), live = true)

        assertEquals(listOf(false, true, true, true), chips.map { it.enabled })
    }

    @Test
    fun `an empty colony presses nothing at all`() {
        val chips = contributeChips(Resources.of(), live = true)

        assertTrue(chips.none { it.enabled }, "a colony with nothing offered something")
    }

    // With the pool unread there is nothing to pay into yet, so the ladder is inert — and it is
    // drawn rather than absent, because the figures are still true about the colony.
    @Test
    fun `the ladder does not press before the pool has been read`() {
        val chips = contributeChips(Resources.of(metal = 1_000), live = false)

        assertEquals(4, chips.size)
        assertFalse(chips.any { it.enabled })
    }

    @Test
    fun `what a chip sends is the basket core is charged`() {
        val chip = contributeChips(Resources.of(metal = 1_000, crystal = 400), live = true).first()

        assertEquals(Resources.of(metal = 100, crystal = 40), chip.basket())
    }
}
