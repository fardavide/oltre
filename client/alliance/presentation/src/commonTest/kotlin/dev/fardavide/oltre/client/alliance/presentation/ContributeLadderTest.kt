package dev.fardavide.oltre.client.alliance.presentation

import dev.fardavide.oltre.client.alliance.ui.ContributeActionUiState
import dev.fardavide.oltre.client.alliance.ui.ContributeShare
import dev.fardavide.oltre.client.alliance.ui.ContributeUiState
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.Resources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// **The ladder's arithmetic, which is the one thing on this screen a player is entitled to hold the
// app to.** The line states a basket and the control under it sends one, and those two must be the
// same numbers: a control that sends more than it states is the worst kind of wrong on a tap nothing
// can undo.
class ContributeLadderTest {

    @Test
    // No comma in this name — Kotlin/Native rejects one inside a backtick in a `commonTest` source
    // set of a module with Apple targets, where the JVM accepts it. It has taken CI down twice.
    fun `the four stops are a tenth a quarter a half and everything`() {
        val colony = Resources.of(metal = 1_000, crystal = 400, deuterium = 200)

        val sent = ContributeShare.entries.map { ladder(colony, picked = it).offered() }

        assertEquals(listOf(100L, 250L, 500L, 1_000L), sent.map { it.metal })
        assertEquals(listOf(40L, 100L, 200L, 400L), sent.map { it.crystal })
        assertEquals(listOf(20L, 50L, 100L, 200L), sent.map { it.deuterium })
    }

    // **Floored, never rounded.** 33 at 10% is 3.3, and a control that sent 4 would be sending more
    // than the line above it says.
    @Test
    fun `a share that does not divide evenly rounds down`() {
        val offered = ladder(Resources.of(metal = 33, crystal = 7, deuterium = 1)).offered()

        assertEquals(3L, offered.metal)
        assertEquals(0L, offered.crystal)
        assertEquals(0L, offered.deuterium)
    }

    // `All` is the only one that confirms — Davide, 2026-09-14. It is the tap that empties a colony.
    @Test
    fun `only everything asks first`() {
        val colony = Resources.of(metal = 1_000)

        val confirms = ContributeShare.entries.map { ladder(colony, picked = it).offered().confirms }

        assertEquals(listOf(false, false, false, true), confirms)
    }

    // **The player picked, so the player's stop stands.** Nothing slides it along, which is the whole
    // reason the ladder takes a selection rather than resolving one per frame.
    @Test
    fun `the control sends the stop that was picked`() {
        val offered = ladder(Resources.of(metal = 1_000), picked = ContributeShare.A_HALF).offered()

        assertEquals(Resources.of(metal = 500), offered.basket())
    }

    // The gentlest stop on the one control in this app that cannot be taken back.
    @Test
    fun `the ladder opens on a tenth`() {
        val shares = ladder(Resources.of(metal = 1_000)).shares

        assertEquals(listOf(true, false, false, false), shares.map { it.selected })
    }

    // **The alliance is named on the control that sends**, not only in the header three cards up.
    @Test
    fun `the control names the alliance it sends to`() {
        val offered = ladder(Resources.of(metal = 1_000)).offered()

        assertEquals(Strings.allianceContributeAction(TextRes("Ferro Alto")), offered.action)
    }

    // **`core`'s `NothingOffered` refusal, one layer earlier.** A colony at four metal has three
    // stops that floor to nothing, and a stop that presses and changes nothing is the dead control in
    // its purest form.
    @Test
    fun `a stop that would send nothing does not press`() {
        val shares = ladder(Resources.of(metal = 4)).shares

        assertEquals(listOf(false, true, true, true), shares.map { it.enabled })
    }

    // A colony with no metal at all still has two resources worth paying in, so the stop presses on
    // the strength of either of the other two.
    @Test
    fun `a stop presses on any one of the three`() {
        assertTrue(ladder(Resources.of(crystal = 1_000)).shares.first().enabled)
        assertTrue(ladder(Resources.of(deuterium = 1_000)).shares.first().enabled)
    }

    // **Absent rather than greyed, with the sentence that makes the absence answerable** — and the
    // sentence is about *this stop*, because a bigger one would still work.
    @Test
    fun `a stop that rounds to nothing offers no control and says which fact it is`() {
        val action = ladder(Resources.of(metal = 4)).action

        assertEquals(ContributeActionUiState.Short(Strings.allianceShareRoundsToNothing()), action)
    }

    // And the other fact: nothing anywhere on the ladder would send anything, which is a different
    // sentence because the answer to it is *come back later* rather than *pick a bigger share*.
    @Test
    fun `an empty colony presses nothing at all and says there is nothing to give`() {
        val ladder = ladder(Resources.of())

        assertTrue(ladder.shares.none { it.enabled }, "a colony with nothing offered something")
        assertEquals(ContributeActionUiState.Short(Strings.allianceContributeNothing()), ladder.action)
    }

    // With the pool unread there is nothing to pay into yet, so the ladder is inert — and it is drawn
    // rather than absent, because the figures are still true about the colony.
    @Test
    fun `the ladder does not press before the pool has been read`() {
        val ladder = ladder(Resources.of(metal = 1_000), live = false)

        assertEquals(4, ladder.shares.size)
        assertTrue(ladder.shares.none { it.enabled })
        assertEquals(ContributeActionUiState.Unread, ladder.action)
    }

    @Test
    fun `what the control sends is the basket core is charged`() {
        val offered = ladder(Resources.of(metal = 1_000, crystal = 400)).offered()

        assertEquals(Resources.of(metal = 100, crystal = 40), offered.basket())
    }
}

// Top-level rather than private to the class above, because `AllianceUiStateTest` builds ladders too
// and a second copy of the same three arguments is a second place the default can drift.
internal fun ladder(
    colony: Resources,
    picked: ContributeShare? = null,
    live: Boolean = true,
): ContributeUiState = contributeLadder(colony, TextRes("Ferro Alto"), picked, live)

// The offer, or a failure naming what was there instead — which is what every test above is about,
// and what an `as` cast would have reported as a `ClassCastException` with nothing in it.
internal fun ContributeUiState.offered(): ContributeActionUiState.Offered = assertIs(action)
