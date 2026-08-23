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

    @Test
    fun `the notice claims the hit target the gear could not`() {
        // **The two halves of the same rule, and the point is that they disagree.** `WatchSquare`'s
        // finding is that a target may not overflow its parent, so the gear settles for 38dp because
        // the band it lives in is the most expensive number in this design. The notice has no band
        // and nothing above it to pay: it takes the 44dp Material asks for. Nothing on it is
        // tappable, so what the height buys is that it reads as a surface rather than as a line of
        // text that appeared — and a notice that quietly shrank to the gear's 38 would lose that
        // with no frame able to say which of the two was wrong.
        assertTrue(
            NOTICE_HEIGHT >= HIT_TARGET_MIN,
            "the notice is $NOTICE_HEIGHT tall, under the $HIT_TARGET_MIN this app spends where it can",
        )
        assertTrue(
            NOTICE_HEIGHT > GEAR_TARGET,
            "the notice is $NOTICE_HEIGHT against the gear's $GEAR_TARGET, so the constrained target " +
                "is the larger one and one of the two is wrong",
        )
    }

    @Test
    fun `the notice is drawn with the stronger of the app's two lines`() {
        // White 16% against the hairline's 9%. Every other card sits in a list of its own kind and
        // is separated by rhythm; this one floats over a screen and has only its own edge to say
        // where it starts. Stated as a comparison with the strip's own track rather than as a
        // literal, because a frame cannot fail on the difference between two near-black greys and an
        // alpha that drifted down to the hairline's would look like nothing at all.
        assertTrue(
            NOTICE_BORDER.alpha > TRACK.alpha,
            "the notice's line is ${NOTICE_BORDER.alpha} against the app's ${TRACK.alpha} hairline, " +
                "so it is not the stronger of the two",
        )
        assertEquals(NOTICE_BORDER_WIDTH, HAIRLINE)
    }

    @Test
    fun `the notice stays long enough to be read and not long enough to be furniture`() {
        // Bounded rather than pinned: the exact figure is the design's and may move, and a test that
        // restated it would only assert that a constant is itself. What must not happen is a notice
        // that is gone before it is read, or one that is still there when the player has moved on.
        assertTrue(
            SETTINGS_NOTICE_MILLIS in 2_000L..6_000L,
            "the notice stays ${SETTINGS_NOTICE_MILLIS}ms, which is either a flash or furniture",
        )
    }

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
