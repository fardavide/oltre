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

    // The physical quantities below were private to the Galaxy mapper until the adaptation branch
    // put the same three axes on the Research screen. A band on Research is read against a world
    // reading on Galaxy — "you tolerate 1.45 g" against "0.65 … 1.40 g" — so the two screens
    // printing them two ways would not be a style question, it would be the app contradicting
    // itself about a number the player is comparing.

    @Test
    fun `a signed integer takes a true minus rather than a hyphen`() {
        assertEquals("−30", (-30).signed())
        assertEquals("+45", 45.signed())
    }

    // Zero is not a negative quantity and reads as a gain, which is what the tolerance bands and
    // the temperature readings both want.
    @Test
    fun `zero signs positive`() {
        assertEquals("+0", 0.signed())
    }

    @Test
    fun `a milli quantity is two decimal places`() {
        assertEquals("1.40", 1_400.milli())
        assertEquals("0.65", 650.milli())
        assertEquals("2.60", 2_600.milli())
    }

    // Padded rather than trimmed, both halves of every band: a column of "1.4" over "0.65" stops
    // being a column, and this face is tabular precisely so the decimal points line up.
    @Test
    fun `a trailing zero is kept so two readings align`() {
        assertEquals("0.50", 500.milli())
        assertEquals("3.50", 3_500.milli())
    }

    @Test
    fun `a milli quantity carries its sign`() {
        assertEquals("−0.05", (-50).milli())
    }

    @Test
    fun `a per-million quantity is two decimal places on its own scale`() {
        assertEquals("0.92", 920_000.perMillion())
        assertEquals("1.00", 1_000_000.perMillion())
    }

    // Rounded half up rather than truncated, matching `ResearchBalance.effectPercent`: 0.016 atm
    // reading as "0.01" understates a number being compared against a band.
    @Test
    fun `hundredths round half up`() {
        assertEquals("0.02", 16.milli())
    }

    // The carry the two halves of the formatter have to agree about — a truncating whole part
    // beside a rounding fraction prints 0.100 as "0.00".
    @Test
    fun `rounding a fraction up carries into the whole part`() {
        assertEquals("1.00", 999.milli())
    }

    // The trimmed variant exists for exactly one line — the pressure band on a Research row — and
    // for a width reason rather than a taste one. See `milliTrimmed`.
    @Test
    fun `a trimmed milli quantity drops the zeros it does not need`() {
        assertEquals("0.5", 500.milliTrimmed())
        assertEquals("2.6", 2_600.milliTrimmed())
        assertEquals("3.5", 3_500.milliTrimmed())
    }

    @Test
    fun `a trimmed milli quantity keeps the digits it does need`() {
        assertEquals("0.44", 440.milliTrimmed())
        assertEquals("1.52", 1_520.milliTrimmed())
    }

    // A whole number loses its point as well as its zeros, rather than reading "3." — which is the
    // only case where trimming twice matters.
    @Test
    fun `a whole trimmed quantity drops the decimal point too`() {
        assertEquals("3", 3_000.milliTrimmed())
    }

    @Test
    fun `a trimmed milli quantity carries its sign`() {
        assertEquals("−0.5", (-500).milliTrimmed())
    }

    // ── Payback ──────────────────────────────────────────────────────────────────────────────

    // The shortest payback in the game is the one the research branch is sold on. An hours-only
    // format would print it as "1h" and delete the difference between it and the mine beside it.
    @Test
    fun `a payback under a day keeps its minutes`() {
        assertEquals("1h 42m", (1.hours + 42.minutes).toPaybackLabel())
        assertEquals("42m", 42.minutes.toPaybackLabel())
    }

    @Test
    fun `a payback of a day or more is whole hours`() {
        assertEquals("102h", 102.hours.toPaybackLabel())
        assertEquals("24h", (24.hours + 30.minutes).toPaybackLabel())
    }

    // The boundary itself, stated: the last minute of the first day still carries its minutes.
    @Test
    fun `the day boundary is where the minutes stop mattering`() {
        assertEquals("23h 59m", (23.hours + 59.minutes).toPaybackLabel())
    }

    // There is no day unit anywhere in the app and this is the format most tempted by one. Hours
    // stay comparable against each other; "4d 6h" against "1h 42m" does not.
    @Test
    fun `a payback of several days is still written in hours`() {
        assertEquals("186h", 186.hours.toPaybackLabel())
    }
}
