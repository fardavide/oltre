package dev.fardavide.oltre.client.debug.ui

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DebugSheetBehaviourTest {

    @Test
    fun `holding skip ahead asks the shell to skip`() {
        // given
        var skips = 0

        // when
        debugSheet(report = buildingReport, onSkipAhead = { skips++ }) {
            assertIsOpen()
            holdSkip()
        }

        // then
        assertEquals(1, skips)
    }

    @Test
    fun `a tap does not skip`() {
        // The change in one test. This panel opens by shaking the phone — a gesture a pocket can
        // perform — so neither verb on it may be one stray tap away.
        var skips = 0

        debugSheet(report = buildingReport, onSkipAhead = { skips++ }) {
            tapSkip()
        }

        assertEquals(0, skips)
    }

    @Test
    fun `both verbs show how far through the hold they are`() {
        // Asserted as "the bar is there" rather than as a width: the row renders its progress, and
        // how far it has got at any instant is the animation's business rather than the design's.
        debugSheet(report = buildingReport) {
            assertShowsProgress(DebugTestTags.SKIP)
            assertShowsProgress(DebugTestTags.RESET)
        }
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
    fun `every kind of thing that can happen next has a name on the row`() {
        // The sheet writes the destination with a `when` over the sealed `FutureEvent`, and until
        // this existed the compiler was the only thing that had ever looked at four of its five
        // branches. Enum names rather than the notifications' display names, deliberately: this is a
        // developer tool, and `NANITE_FACTORY` is the string that matches the code being read.
        nextEventReports.forEach { (event, expected) ->
            debugSheet(report = buildingReport.copy(nextEvent = event)) {
                assertSkipOffers("$expected · 1h 00m")
            }
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
    fun `a tap does not wipe the colony`() {
        // The only destructive thing in the app, on a panel that opens by shaking the phone. One
        // stray tap must not cost somebody their evening.
        var resets = 0

        debugSheet(onReset = { resets++ }) {
            tapReset()
        }

        assertEquals(0, resets)
    }

    @Test
    fun `holding wipes the colony`() {
        // given
        var resets = 0

        // when
        debugSheet(onReset = { resets++ }) {
            holdReset()
        }

        // then
        assertEquals(1, resets)
    }

    @Test
    fun `the reset row says what it will do without needing to be armed first`() {
        // The two-tap arming is gone, so the warning is no longer a state the row has to be put
        // into — it is just what the row says.
        debugSheet {
            assertResetSays("RESET COLONY")
            assertResetWarns("deletes the save and starts a new galaxy")
        }
    }

    @Test
    fun `the panel really is a bottom sheet`() {
        // The one test about the chrome rather than the contents. It asserts the contents arrive
        // inside a `ModalBottomSheet` at all — the drag, the scrim and the enter animation are
        // Material's own and are not this repository's to re-test.
        debugBottomSheet(report = buildingReport) {
            assertIsOpen()
            assertSkipOffers("METAL_MINE → 2 · 1h 04m")
        }
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
