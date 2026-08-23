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
    fun `the strip costs a destination the row and the edge and nothing else`() {
        // **The number every screen in the app pays**, and the one this design spends most carefully.
        // A 393×852 phone leaves 759dp between the insets; the tab bar takes 55 and the rail 52, so
        // a destination gets what is left after this. `DESTINATION_HEIGHT` in `GalaxyRobot` is the
        // other end of that arithmetic and is hand-derived — 0.12.0 shipped a galaxy map whose only
        // control was off the bottom of the screen because nobody moved it, with every frame green.
        assertEquals(PHONE_DESTINATION, WINDOW - TAB_BAR - RAIL - (STRIP_HEIGHT + GAUGE_HEIGHT))
    }

    @Test
    fun `the edge is the hairline it replaced, one dp thicker`() {
        // The gauge is the bar's own bottom edge now, and the whole of the argument for it is that it
        // costs the row no width. What it must not do is start reading as a bar in its own right: two
        // dp against the app's 1dp hairline is the step the design took, and a third would make the
        // chrome look like it had grown a rule.
        assertTrue(
            GAUGE_HEIGHT <= HAIRLINE * 2,
            "the edge is $GAUGE_HEIGHT against a $HAIRLINE hairline, which is a bar rather than an edge",
        )
    }

    // **Three tests left with the notice at 0.18**, and they are worth a line rather than a silent
    // deletion: they held its 44dp target against the gear's 38, its stronger border against the
    // app's hairline, and its four-second window against being a flash or furniture. Every one of
    // them was about a card that said `Coming soon`. What replaces them is `AlertSheetBehaviourTest`
    // and the sheet's own baselines — the gear opens something now, so what is worth pinning is what
    // it opens rather than how long the apology stayed.
    @Test
    fun `the gauge is never asked to draw outside its track`() {
        // `fillMaxWidth` throws outside 0..1. The mapper one layer up is free to hand this an
        // un-normalised figure the day a level's requirement changes, which is exactly the state in
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

        // The app's one hairline, restated for the same reason: `:client:design:core` holds the
        // colour and every bar in the app draws it a dp thick.
        val HAIRLINE = 1.dp

        // Material's own floor for a touch target, restated because this module cannot see it and
        // because the interesting thing about it here is which of two controls can afford it.
        val HIT_TARGET_MIN = 44.dp

        // The frame's own arithmetic, as the design states it. A 393×852 phone leaves 759dp between
        // the status bar and the home indicator; the two other tiers of chrome are measured rather
        // than pinned, so they are restated here as the design measured them.
        val WINDOW = 759.dp
        val TAB_BAR = 55.dp
        val RAIL = 52.dp

        // What a destination is left with, and what every galaxy baseline is captured at.
        val PHONE_DESTINATION = 612.dp
    }
}
