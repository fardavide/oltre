package dev.fardavide.oltre.client.notifications.data

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

// Desktop raises no alert — an alert about a countdown you are looking at is noise — so it prints
// the schedule instead. That print *is* the desktop implementation of the check-in loop: the only
// way the dev loop can see that the right alerts, at the right instants, are being derived from the
// state. Worth a test for the same reason the string on a lock screen is.
class PrintingNotificationSchedulerTest {

    @Test
    fun `an empty schedule says so rather than printing nothing at all`() = runTest {
        // Silence and "nothing pending" look identical in a terminal only if you already know the
        // scheduler ran. The distinction is the whole value of printing.
        val printed = capturingOutput {
            defaultNotificationScheduler().replaceAll(emptyList())
        }

        assertTrue("nothing pending" in printed, "was '$printed'")
    }

    @Test
    fun `a pending alert is printed with the instant it will fire at`() = runTest {
        val notification = LocalNotification(
            id = "build-METAL_MINE",
            // Its own tray entry, which is every alert but the one `AlertDelivery.TOTAL` books.
            collapseId = "build-METAL_MINE",
            title = "Metal Mine reached level 2",
            body = "Construction is complete.",
            at = Instant.fromEpochSeconds(3_600),
        )

        val printed = capturingOutput {
            defaultNotificationScheduler().replaceAll(listOf(notification))
        }

        assertTrue("1 pending" in printed, "was '$printed'")
        assertTrue(notification.title in printed, "the title is what identifies the alert")
        assertTrue("${notification.at}" in printed, "the instant is the thing being checked")
    }

    // There is no tray on the dev loop, so what is worth seeing is that the launch asked — the same
    // argument as "nothing pending" above.
    @Test
    fun `clearing the tray says so even though desktop has no tray`() = runTest {
        val printed = capturingOutput {
            defaultNotificationScheduler().clearDelivered()
        }

        assertTrue("tray cleared" in printed, "was '$printed'")
    }

    private suspend fun capturingOutput(block: suspend () -> Unit): String {
        val captured = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(captured, true))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return captured.toString()
    }
}
