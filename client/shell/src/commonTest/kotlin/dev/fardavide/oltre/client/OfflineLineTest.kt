package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.text.English
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// **The one new piece of chrome the offline era adds**, and the three questions it can get wrong: is
// it there at all, what time does it name, and how many actions does it count.
class OfflineLineTest {

    @Test
    fun `should draw nothing while the server is answering`() {
        assertNull(
            offlineLine(reachable = true, since = REACHED, held = 3, timeZone = TimeZone.UTC, compact = false),
        )
    }

    // A queue that is draining is not an offline colony. A line that stayed up while three verbs were
    // in flight would be reporting on the app rather than on the network.
    @Test
    fun `should draw nothing while the server is answering even with a queue`() {
        assertNull(
            offlineLine(reachable = true, since = REACHED, held = 9, timeZone = TimeZone.UTC, compact = false),
        )
    }

    // **A first launch with no signal never reaches this screen at all** — the gate is still up. A
    // line reading "no network since —" would be the app inventing an instant.
    @Test
    fun `should draw nothing when the server has never answered`() {
        assertNull(
            offlineLine(reachable = false, since = null, held = 3, timeZone = TimeZone.UTC, compact = false),
        )
    }

    @Test
    fun `should name the instant the server last answered`() {
        val line = offlineLine(
            reachable = false,
            since = REACHED,
            held = 3,
            timeZone = TimeZone.UTC,
            compact = false,
        )

        assertEquals("No network since 11:31 · 3 actions held", English.resolve(line!!.text))
    }

    // The design's rule at 320: the noun goes and both numbers stay.
    @Test
    fun `should drop the noun and keep both numbers in a narrow window`() {
        val line = offlineLine(
            reachable = false,
            since = REACHED,
            held = 3,
            timeZone = TimeZone.UTC,
            compact = true,
        )

        assertEquals("No network since 11:31 · 3 held", English.resolve(line!!.text))
    }

    // One tap is the ordinary case and the plural is what the design drew. Both forms are reachable
    // from a real colony and the catalogue has to say both.
    @Test
    fun `should count one action in the singular`() {
        val line = offlineLine(
            reachable = false,
            since = REACHED,
            held = 1,
            timeZone = TimeZone.UTC,
            compact = false,
        )

        assertEquals("No network since 11:31 · 1 action held", English.resolve(line!!.text))
    }

    // **An offline colony with nothing queued is a real state and it says so.** The player has not
    // tapped anything since the signal went; the fact that matters is still the network, and a line
    // that appeared only once something was held would leave them wondering why the numbers had
    // stopped moving.
    @Test
    fun `should draw the line with nothing held at all`() {
        val line = offlineLine(
            reachable = false,
            since = REACHED,
            held = 0,
            timeZone = TimeZone.UTC,
            compact = false,
        )

        assertEquals("No network since 11:31 · 0 actions held", English.resolve(line!!.text))
    }

    private companion object {

        // 11:31 UTC, which is the instant the design's reference colony last had signal.
        val REACHED: Instant = Instant.fromEpochSeconds(0) + 11.hours + 31.minutes
    }
}
