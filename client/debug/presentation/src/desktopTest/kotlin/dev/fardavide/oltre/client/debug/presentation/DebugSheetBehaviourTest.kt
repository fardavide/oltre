package dev.fardavide.oltre.client.debug.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DebugSheetBehaviourTest {

    @Test
    fun `tapping skip ahead asks the shell to skip`() {
        // given
        var skips = 0

        // when
        debugSheet(report = buildingReport, onSkipAhead = { skips++ }) {
            assertIsOpen()
            skipAhead()
        }

        // then
        assertEquals(1, skips)
    }

    @Test
    fun `the skip row names what it will land on and how far away it is`() {
        // A jump through time you cannot preview is one you cannot trust, so the label states the
        // destination before the tap rather than after it.
        debugSheet(report = buildingReport) {
            assertSkipOffers("METAL_MINE → 2 · 1h 04m")
        }
    }

    @Test
    fun `an idle colony is told there is nothing to skip to`() {
        // The fallback case, and the reason the action is total: with nothing in flight there is no
        // next event, and the row says so rather than looking broken.
        debugSheet(report = idleReport) {
            assertSkipOffers("nothing in flight · +1h 00m")
        }
    }

    @Test
    fun `one tap does not wipe the colony`() {
        // The only destructive thing in the app, on a panel that opens by shaking the phone. One
        // stray tap must not cost somebody their evening.
        var resets = 0

        debugSheet(onReset = { resets++ }) {
            tapReset()
        }

        assertEquals(0, resets)
    }

    @Test
    fun `the first tap arms the reset and says so`() {
        debugSheet {
            assertResetSays("RESET COLONY")
            assertResetWarns("deletes the save and starts a new galaxy")
            tapReset()
            assertResetSays("TAP AGAIN TO WIPE")
            assertResetWarns("this cannot be undone")
        }
    }

    @Test
    fun `two taps wipe the colony`() {
        // given
        var resets = 0

        // when
        debugSheet(onReset = { resets++ }) {
            tapReset()
            tapReset()
        }

        // then
        assertEquals(1, resets)
    }

    @Test
    fun `tapping close dismisses the panel`() {
        // given
        var dismissals = 0

        // when
        debugSheet(onDismiss = { dismissals++ }) {
            close()
        }

        // then
        assertEquals(1, dismissals)
    }

    @Test
    fun `the inspector reports a colony that has never been debugged`() {
        debugSheet(report = idleReport) {
            assertReads("debug used", "no")
            assertReads("skipped by", "—")
            assertReads("schema", "7")
            assertReads("galaxy seed", "20260807")
            assertReads("research slot", "free")
        }
    }

    @Test
    fun `the inspector reports a colony that was skipped`() {
        // The two clocks and the gap between them, which is the whole reason the inspector exists:
        // on a device there is otherwise no way to see that game time has left wall time behind.
        debugSheet(report = skippedReport) {
            assertReads("debug used", "yes")
            assertReads("skipped by", "4h 00m")
            assertReads("game time", "1970-01-01T04:00:00Z")
            assertReads("wall time", "1970-01-01T00:00:00Z")
        }
    }

    @Test
    fun `the inspector reports what is in flight`() {
        debugSheet(report = buildingReport) {
            assertReads("builds", "1")
            assertReads("event log", "1")
            assertReads("probes", "0")
            assertReads("fleet", "none")
        }
    }
}
