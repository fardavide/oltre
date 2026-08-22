package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The three numeric claims the strip makes that a picture cannot fail on.** A baseline says the
// frame changed; none of these would change it in a way anybody could read, and two of them are
// invariants rather than measurements — the day a constant moves and breaks one, the screenshot
// diff would look like a nudge.
class PlayerStripGeometryTest {

    @Test
    fun `the settings target fits inside the strip`() {
        // **The one that matters.** `WatchSquare` settled it for the whole app: *"a child placed
        // outside its parent's bounds does not reliably receive touch, which is why Material's own
        // `minimumInteractiveComponentSize` expands the layout rather than overflowing it."* So a
        // target taller than the band it sits in is either a tap that misses or a band that has
        // silently grown to fit it — and the band's height is the most expensive number in this
        // design. Raising the target to the 44dp everyone reaches for first fails here, loudly,
        // instead of costing another 6dp off every screen.
        assertTrue(
            GEAR_TARGET <= STRIP_HEIGHT,
            "the gear claims $GEAR_TARGET inside a $STRIP_HEIGHT strip, so it overflows its parent",
        )
    }

    @Test
    fun `the mark fits inside the strip with the rail's own padding either side`() {
        // Where 38 comes from, stated as arithmetic rather than left in a comment: a 20dp mark and
        // the resource rail's own 9dp above and below it. It is what makes the two tiers of chrome
        // rhyme instead of merely stacking, and it is the first thing a taller mark would break.
        assertEquals(STRIP_HEIGHT, MARK_SIZE + RAIL_VERTICAL_PADDING * 2)
    }

    @Test
    fun `compacting the gauge actually makes it narrower`() {
        // The compact branch exists to give the name room below 360dp. A pair of constants that
        // drifted equal would leave the branch in place, doing nothing, and no frame would say so —
        // the two widths are 24dp apart in a bar nobody measures with a ruler.
        assertTrue(
            GAUGE_WIDTH_COMPACT < GAUGE_WIDTH,
            "the compact gauge is $GAUGE_WIDTH_COMPACT against $GAUGE_WIDTH, so compacting costs width instead of saving it",
        )
    }

    @Test
    fun `the gauge is never asked to draw outside its track`() {
        // `fillMaxWidth` throws outside 0..1. Nothing awards experience yet, so the only figures
        // this has ever seen are 0 and the ones a frame hands it — which is exactly the state in
        // which a clamp is easy to delete as dead code and expensive to be wrong about later.
        assertEquals(0f, experienceFraction(0))
        assertEquals(1f, experienceFraction(100))
        assertEquals(0.62f, experienceFraction(62))
        assertEquals(0f, experienceFraction(-5), "a negative reading must floor rather than throw")
        assertEquals(1f, experienceFraction(140), "an over-full reading must cap rather than throw")
    }

    private companion object {

        // `ResourceRail`'s own cell padding, restated because this module cannot see it and the
        // rhyme is the reason for the number.
        val RAIL_VERTICAL_PADDING = 9.dp
    }
}
