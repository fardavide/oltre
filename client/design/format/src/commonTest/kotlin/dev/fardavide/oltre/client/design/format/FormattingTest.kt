package dev.fardavide.oltre.client.design.format

import dev.fardavide.oltre.client.design.text.English
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// These conventions were private copies in two ui-state mappers before 0.0.14, tested only through
// the screens that used them — so nothing said what they were, and nothing would have failed if the
// two had drifted apart. Now there is one implementation, these say what it promises.
//
// **Every expectation is resolved through `English`, and the strings are unchanged from before the
// catalogue existed.** That is deliberate and is what makes this file the proof that #86 moved the
// words without rewriting them: this module now returns a `TextRes`, so a test that asserted on its
// `toString()` would pass while the game said something else entirely. Reading them back through the
// one language the app speaks asserts on what a player sees, which is what these always meant to.
//
// No commas, colons or full stops in the backtick *names*: Kotlin/Native rejects them, and this
// module has iOS targets, so a comma there compiles on the JVM and fails CI on iOS alone. The
// asserted strings are ordinary literals and carry colons freely.
class FormattingTest {

    @Test
    fun `a duration under an hour is minutes alone`() {
        assertEquals("42m", English.resolve(42.minutes.toChipLabel()))
    }

    @Test
    fun `an hour or more pads the minutes to two digits`() {
        assertEquals("1h 04m", English.resolve((1.hours + 4.minutes).toChipLabel()))
    }

    // The reason the rounding is up rather than nearest: a chip reading "0m" claims a thing is
    // free, and nothing in this game is.
    @Test
    fun `a sub-minute duration rounds up so a chip never reads zero`() {
        assertEquals("1m", English.resolve(1.seconds.toChipLabel()))
    }

    @Test
    fun `an exact minute does not round up to the next one`() {
        assertEquals("1m", English.resolve(60.seconds.toChipLabel()))
    }

    // ── The wait for a vein to be worth visiting again ──────────────────────────────────────
    //
    // Claude Design's three tiers, of which only the top one is new. Two units, never three, which is
    // the shape `toChipLabel` already has.

    @Test
    fun `a wait of a day or more reads in days and hours`() {
        assertEquals("18d 13h", English.resolve((18.days + 13.hours).toWaitLabel()))
        assertEquals("1d 00h", English.resolve(1.days.toWaitLabel()))
    }

    @Test
    fun `the hour is padded so a column of waits stays tabular and the day never is`() {
        // "04d" reads like a countdown to a launch; "4d 03h" reads like a wait.
        assertEquals("4d 03h", English.resolve((4.days + 3.hours).toWaitLabel()))
    }

    @Test
    fun `under a day a wait is the duration shape the app already has`() {
        assertEquals("4h 20m", English.resolve((4.hours + 20.minutes).toWaitLabel()))
        assertEquals("23h 59m", English.resolve((23.hours + 59.minutes).toWaitLabel()))
    }

    @Test
    fun `under an hour a wait is the countdown the app already has`() {
        // The tier this state usually sits in: a 1,450 vein puts a whole unit back every twenty
        // minutes, so a world that is dry to a small ask is usually minutes away.
        assertEquals("00:19:41", English.resolve((19.minutes + 41.seconds).toWaitLabel()))
        assertEquals("00:00:09", English.resolve(9.seconds.toWaitLabel()))
    }

    @Test
    fun `a wait never reads zero and never reads a bare day`() {
        assertEquals("00:00:01", English.resolve(0.seconds.toWaitLabel()))
        assertEquals("2d 00h", English.resolve((2.days + 30.minutes).toWaitLabel()))
    }

    @Test
    fun `a countdown is always three zero-padded fields`() {
        assertEquals("00:00:09", English.resolve(9L.toCountdown()))
    }

    @Test
    fun `a countdown carries hours past a day rather than wrapping`() {
        assertEquals("30:00:00", English.resolve((30L * 3600).toCountdown()))
    }

    @Test
    fun `thousands are grouped from the right`() {
        assertEquals("1,234,567", English.resolve(1_234_567L.groupedByThousands()))
    }

    @Test
    fun `a number below a thousand is left alone`() {
        assertEquals("999", English.resolve(999L.groupedByThousands()))
    }

    @Test
    fun `a group boundary does not gain a leading separator`() {
        assertEquals("1,000", English.resolve(1_000L.groupedByThousands()))
    }

    // The one line a watched row adds, wherever it is watched from. The padding is English's; that
    // the fields are a wall clock at all is this module's.
    @Test
    fun `a watched row names the wall-clock time it becomes affordable`() {
        assertEquals("→ affordable 19:51", English.resolve(watchedAtLabel(hour = 19, minute = 51)))
        assertEquals("→ affordable 07:05", English.resolve(watchedAtLabel(hour = 7, minute = 5)))
    }

    // The physical quantities below were private to the Galaxy mapper until the adaptation branch
    // put the same three axes on the Research screen. A band on Research is read against a world
    // reading on Galaxy — "you tolerate 1.45 g" against "0.65 … 1.40 g" — so the two screens
    // printing them two ways would not be a style question, it would be the app contradicting
    // itself about a number the player is comparing.

    @Test
    fun `a signed integer takes a true minus rather than a hyphen`() {
        assertEquals("−30", English.resolve((-30).signed()))
        assertEquals("+45", English.resolve(45.signed()))
    }

    // Zero is not a negative quantity and reads as a gain, which is what the tolerance bands and
    // the temperature readings both want.
    @Test
    fun `zero signs positive`() {
        assertEquals("+0", English.resolve(0.signed()))
    }

    @Test
    fun `a milli quantity is two decimal places`() {
        assertEquals("1.40", English.resolve(1_400.milli()))
        assertEquals("0.65", English.resolve(650.milli()))
        assertEquals("2.60", English.resolve(2_600.milli()))
    }

    // Padded rather than trimmed, both halves of every band: a column of "1.4" over "0.65" stops
    // being a column, and this face is tabular precisely so the decimal points line up.
    @Test
    fun `a trailing zero is kept so two readings align`() {
        assertEquals("0.50", English.resolve(500.milli()))
        assertEquals("3.50", English.resolve(3_500.milli()))
    }

    @Test
    fun `a milli quantity carries its sign`() {
        assertEquals("−0.05", English.resolve((-50).milli()))
    }

    @Test
    fun `a per-million quantity is two decimal places on its own scale`() {
        assertEquals("0.92", English.resolve(920_000.perMillion()))
        assertEquals("1.00", English.resolve(1_000_000.perMillion()))
    }

    // Rounded half up rather than truncated, matching `ResearchBalance.effectPercent`: 0.016 atm
    // reading as "0.01" understates a number being compared against a band.
    @Test
    fun `hundredths round half up`() {
        assertEquals("0.02", English.resolve(16.milli()))
    }

    // The carry the two halves of the formatter have to agree about — a truncating whole part
    // beside a rounding fraction prints 0.100 as "0.00".
    @Test
    fun `rounding a fraction up carries into the whole part`() {
        assertEquals("1.00", English.resolve(999.milli()))
    }

    // The trimmed variant exists for exactly one line — the pressure band on a Research row — and
    // for a width reason rather than a taste one. See `milliTrimmed`.
    @Test
    fun `a trimmed milli quantity drops the zeros it does not need`() {
        assertEquals("0.5", English.resolve(500.milliTrimmed()))
        assertEquals("2.6", English.resolve(2_600.milliTrimmed()))
        assertEquals("3.5", English.resolve(3_500.milliTrimmed()))
    }

    @Test
    fun `a trimmed milli quantity keeps the digits it does need`() {
        assertEquals("0.44", English.resolve(440.milliTrimmed()))
        assertEquals("1.52", English.resolve(1_520.milliTrimmed()))
    }

    // A whole number loses its point as well as its zeros, rather than reading "3." — which is the
    // only case where trimming twice matters.
    @Test
    fun `a whole trimmed quantity drops the decimal point too`() {
        assertEquals("3", English.resolve(3_000.milliTrimmed()))
    }

    @Test
    fun `a trimmed milli quantity carries its sign`() {
        assertEquals("−0.5", English.resolve((-500).milliTrimmed()))
    }

    // ── Payback ──────────────────────────────────────────────────────────────────────────────

    // The shortest payback in the game is the one the research branch is sold on. An hours-only
    // format would print it as "1h" and delete the difference between it and the mine beside it.
    @Test
    fun `a payback under a day keeps its minutes`() {
        assertEquals("1h 42m", English.resolve((1.hours + 42.minutes).toPaybackLabel()))
        assertEquals("42m", English.resolve(42.minutes.toPaybackLabel()))
    }

    @Test
    fun `a payback of a day or more is whole hours`() {
        assertEquals("102h", English.resolve(102.hours.toPaybackLabel()))
        assertEquals("24h", English.resolve((24.hours + 30.minutes).toPaybackLabel()))
    }

    // The boundary itself, stated: the last minute of the first day still carries its minutes.
    @Test
    fun `the day boundary is where the minutes stop mattering`() {
        assertEquals("23h 59m", English.resolve((23.hours + 59.minutes).toPaybackLabel()))
    }

    // There is no day unit anywhere in the app and this is the format most tempted by one. Hours
    // stay comparable against each other; "4d 6h" against "1h 42m" does not.
    @Test
    fun `a payback of several days is still written in hours`() {
        assertEquals("186h", English.resolve(186.hours.toPaybackLabel()))
    }
}
