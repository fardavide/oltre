package dev.fardavide.oltre.client.debug.domain

import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GameSave
import dev.fardavide.oltre.core.GameState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class DebugReportTest {

    @Test
    fun `a fresh colony reports itself as idle and untouched`() {
        val report = report(state = freshColony())

        assertEquals(0, report.eventLogSize)
        assertEquals(0, report.buildsInFlight)
        assertEquals(0, report.surveysInFlight)
        assertFalse(report.researchSlotBusy)
        assertFalse(report.fleetInbound)
        assertFalse(report.debugUsed)
        assertNull(report.nextEvent)
    }

    @Test
    fun `the gap between the two clocks is reported rather than left to be worked out`() {
        val report = report(gameTime = EPOCH + 4.hours, wallTime = EPOCH)

        assertEquals(4.hours, report.skippedBy)
        assertEquals(EPOCH + 4.hours, report.gameTime)
        assertEquals(EPOCH, report.wallTime)
    }

    @Test
    fun `a colony running on ordinary time reports no skip`() {
        assertEquals(Duration.ZERO, report(gameTime = EPOCH, wallTime = EPOCH).skippedBy)
    }

    @Test
    fun `a device clock that jumped forward is clamped rather than shown as a colony running backwards`() {
        assertEquals(Duration.ZERO, report(gameTime = EPOCH, wallTime = EPOCH + 2.hours).skippedBy)
    }

    @Test
    fun `a colony mid-build names what happens next`() {
        val report = report(state = buildingColony())

        assertEquals(1, report.buildsInFlight)
        assertIs<FutureEvent.BuildCompletes>(report.nextEvent)
        assertTrue(report.eventLogSize > 0)
    }

    @Test
    fun `what happens next is what skipping would land on`() {
        // Two readings of the same list, and a menu that showed one while skipping used the other
        // would be worse than either being wrong.
        val state = buildingColony()

        assertEquals(skipAhead(state, EPOCH).to, report(state = state).nextEvent?.at)
    }

    @Test
    fun `the schema version is the one this build writes`() {
        // Read off `GameSave` rather than copied, so the inspector cannot go stale the next time the
        // format moves — which is the one number a reader most needs to trust on a device.
        assertEquals(GameSave.SCHEMA_VERSION, report().schemaVersion)
    }

    @Test
    fun `the galaxy seed is reported so a map can be reproduced in the sim`() {
        assertEquals(TEST_GALAXY_SEED.value, report().galaxySeed)
    }

    @Test
    fun `a colony the menu has touched says so`() {
        assertTrue(report(debugUsed = true).debugUsed)
    }
}

private fun report(
    state: GameState = freshColony(),
    gameTime: Instant = EPOCH,
    wallTime: Instant = EPOCH,
    debugUsed: Boolean = false,
): DebugReport = debugReport(state, gameTime = gameTime, wallTime = wallTime, debugUsed = debugUsed)
