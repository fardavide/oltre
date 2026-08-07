package dev.fardavide.oltre.client.design.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// These conventions were private copies in two ui-state mappers before 0.0.14, tested only through
// the screens that used them — so nothing said what they were, and nothing would have failed if the
// two had drifted apart. Now there is one implementation, these say what it promises.
//
// No commas, colons or full stops in the backtick *names*: Kotlin/Native rejects them, and this
// module has iOS targets, so a comma there compiles on the JVM and fails CI on iOS alone. The
// asserted strings are ordinary literals and carry colons freely.
class FormattingTest {

    @Test
    fun `a duration under an hour is minutes alone`() {
        assertEquals("42m", 42.minutes.toChipLabel())
    }

    @Test
    fun `an hour or more pads the minutes to two digits`() {
        assertEquals("1h 04m", (1.hours + 4.minutes).toChipLabel())
    }

    // The reason the rounding is up rather than nearest: a chip reading "0m" claims a thing is
    // free, and nothing in this game is.
    @Test
    fun `a sub-minute duration rounds up so a chip never reads zero`() {
        assertEquals("1m", 1.seconds.toChipLabel())
    }

    @Test
    fun `an exact minute does not round up to the next one`() {
        assertEquals("1m", 60.seconds.toChipLabel())
    }

    @Test
    fun `a countdown is always three zero-padded fields`() {
        assertEquals("00:00:09", 9L.toCountdown())
    }

    @Test
    fun `a countdown carries hours past a day rather than wrapping`() {
        assertEquals("30:00:00", (30L * 3600).toCountdown())
    }

    @Test
    fun `thousands are grouped from the right`() {
        assertEquals("1,234,567", 1_234_567L.groupedByThousands())
    }

    @Test
    fun `a number below a thousand is left alone`() {
        assertEquals("999", 999L.groupedByThousands())
    }

    @Test
    fun `a group boundary does not gain a leading separator`() {
        assertEquals("1,000", 1_000L.groupedByThousands())
    }

    @Test
    fun `a wall-clock field is padded to two digits`() {
        assertEquals("07", 7.pad2())
        assertEquals("23", 23.pad2())
    }
}
