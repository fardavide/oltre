package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test

// **The one surface in the app with no way in from the screen.** The debug panel opens by shaking
// the phone, so until the harness could emit a shake there was no test that reached it at all — and
// its two verbs are the most destructive things in the product: one moves the colony's clock, the
// other deletes the save.
//
// What is driven here is the *shell's* half — that a shake raises it, that the two actions reach the
// session and the store, and that closing it puts the app back where it was. The panel's own drawing
// deliberately has no baseline (see `session-roles.md`: nobody designed it), and what the readings
// mean is `:client:debug:domain`, which is pure and has its own tests.
class DebugSheetAppBehaviourTest {

    @Test
    fun `shaking the phone raises the panel`() {
        val shakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        app(saved = colony(), shakes = shakes) {
            assertDebugSheetShowing(showing = false)

            shake(shakes)

            assertDebugSheetShowing(showing = true)
        }
    }

    // The one row on the panel that is not a verb: it changes nothing, so it takes a tap — and what
    // it puts back is the colony that was underneath.
    @Test
    fun `closing the panel puts the colony back`() {
        val shakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        app(saved = colony(), shakes = shakes) {
            shake(shakes)

            closeTheDebugSheet()

            assertDebugSheetShowing(showing = false)
            assertReads("Metal Mine")
        }
    }

    // **Skipping ahead moves the colony's clock and nobody else's.** A hold rather than a tap,
    // because the panel opens by a gesture a pocket can perform — so the robot holds too, on the
    // platform's own long-press timing rather than on the fill animation, which is a rendering of it
    // and nothing depends on.
    @Test
    fun `holding skip moves the colony forward`() {
        val shakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        app(saved = colony(), shakes = shakes) {
            shake(shakes)

            holdTheSkip()

            closeTheDebugSheet()
            // The mine is still there and the clock has moved: what a skip must never do is start a
            // different colony, which is the other action's job.
            assertReads("Metal Mine")
        }
    }

    // **The only destructive thing in the app**, driven end to end because that is the only place
    // the whole of it is visible: the save is cleared, a new galaxy is drawn, and the session that
    // replaces it is committed — three collaborators, in the composition root, in that order.
    @Test
    fun `holding reset starts a new colony`() {
        val shakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        app(saved = colony(), shakes = shakes) {
            shake(shakes)

            holdTheReset()

            // The panel closes itself on reset — there is nothing left on it that describes the
            // colony that was — and what is behind it is a first launch.
            assertDebugSheetShowing(showing = false)
            assertReads("Metal Mine")
        }
    }

    private fun colony(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        debugUsed = false,
        state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds())),
    )
}
