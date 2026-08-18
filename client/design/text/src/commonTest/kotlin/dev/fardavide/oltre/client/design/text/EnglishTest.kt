package dev.fardavide.oltre.client.design.text

import kotlin.test.Test
import kotlin.test.assertEquals

// The words, and specifically the ones that used to be English grammar compiled into
// `:client:design:format`. Every expectation here is the string that module already produced — this
// is a *move*, and a test that let one character drift would be letting 92 screenshot baselines
// drift with it.
class EnglishTest {

    @Test
    fun `should group a number by thousands`() {
        assertEquals("1,450", English.resolve(Strings.groupedNumber(1_450)))
        assertEquals("12,000,000", English.resolve(Strings.groupedNumber(12_000_000)))
    }

    @Test
    fun `should leave a number under a thousand ungrouped`() {
        assertEquals("999", English.resolve(Strings.groupedNumber(999)))
        assertEquals("0", English.resolve(Strings.groupedNumber(0)))
    }

    @Test
    fun `should group a negative number without grouping its sign`() {
        assertEquals("-1,450", English.resolve(Strings.groupedNumber(-1_450)))
    }

    @Test
    fun `should write a signed number with a true minus sign`() {
        assertEquals("+5", English.resolve(Strings.signed(5)))
        assertEquals("−5", English.resolve(Strings.signed(-5)))
        assertEquals("+0", English.resolve(Strings.signed(0)))
    }

    @Test
    fun `should write a fixed-point number with a point`() {
        assertEquals("2.62", English.resolve(Strings.decimal(262, decimals = 2, trimTrailingZeros = false)))
        assertEquals("0.05", English.resolve(Strings.decimal(5, decimals = 2, trimTrailingZeros = false)))
        assertEquals("−1.40", English.resolve(Strings.decimal(-140, decimals = 2, trimTrailingZeros = false)))
    }

    @Test
    fun `should trim a fixed-point number when asked`() {
        assertEquals("0.5", English.resolve(Strings.decimal(50, decimals = 2, trimTrailingZeros = true)))
        assertEquals("0.44", English.resolve(Strings.decimal(44, decimals = 2, trimTrailingZeros = true)))
        // Trimming takes the separator with it when nothing is left of the fraction.
        assertEquals("1", English.resolve(Strings.decimal(100, decimals = 2, trimTrailingZeros = true)))
        assertEquals("20", English.resolve(Strings.decimal(2_000, decimals = 2, trimTrailingZeros = true)))
    }

    @Test
    fun `should write a duration in the mockup style`() {
        assertEquals("42m", English.resolve(Strings.durationMinutes(42)))
        assertEquals("1h 04m", English.resolve(Strings.durationHoursMinutes(1, 4)))
        assertEquals("186h", English.resolve(Strings.durationHours(186)))
        assertEquals("18d 13h", English.resolve(Strings.durationDaysHours(18, 13)))
        assertEquals("4d 06h", English.resolve(Strings.durationDaysHours(4, 6)))
    }

    @Test
    fun `should write a countdown in three fields of two`() {
        assertEquals("00:00:01", English.resolve(Strings.countdown(0, 0, 1)))
        assertEquals("12:34:56", English.resolve(Strings.countdown(12, 34, 56)))
    }

    @Test
    fun `should write the watched line with a padded wall clock`() {
        assertEquals("→ affordable 19:51", English.resolve(Strings.watchedAt(19, 51)))
        assertEquals("→ affordable 07:05", English.resolve(Strings.watchedAt(7, 5)))
    }
}
