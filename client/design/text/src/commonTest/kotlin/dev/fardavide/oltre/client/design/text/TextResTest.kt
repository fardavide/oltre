package dev.fardavide.oltre.client.design.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// The properties the whole framework is bought for. Not the catalogue — `EnglishTest` pins the
// words; this pins the shape they are carried in.
class TextResTest {

    @Test
    fun `should carry the value when built from a bare string`() {
        // given / when
        val text = TextRes("Ionian Reach")

        // then
        assertEquals(TextRes.Raw("Ionian Reach"), text)
    }

    // The property the ticket asks review to protect: a test asserts on *meaning*. This is what
    // makes `assertEquals(Strings.durationHours(3), row.label)` survive a rewording and fail on a
    // rewrite.
    @Test
    fun `should be equal to the same entry with the same arguments`() {
        // given / when
        val one = Strings.durationHours(3)
        val other = Strings.durationHours(3)

        // then
        assertEquals(one, other)
    }

    @Test
    fun `should not be equal to the same entry with different arguments`() {
        // given / when
        val one = Strings.durationHours(3)
        val other = Strings.durationHours(4)

        // then
        assertNotEquals(one, other)
    }

    @Test
    fun `should not be equal to a different entry with the same arguments`() {
        // given / when
        val hours = Strings.durationHours(3)
        val minutes = Strings.durationMinutes(3)

        // then
        assertNotEquals(hours, minutes)
    }

    // A count and a plain number are the same integer and mean different things — one selects a
    // plural form and the other does not — so they must not compare equal by accident.
    @Test
    fun `should not equate a count with a plain number`() {
        // given / when
        val count: Arg = Arg.Count(3)
        val number: Arg = Arg.Number(3)

        // then
        assertNotEquals(count, number)
    }

    @Test
    fun `should join parts with the separator between them`() {
        // given
        val parts = listOf(TextRes("one"), TextRes("two"), TextRes("three"))

        // when
        val text = TextRes.Joined(parts, separator = TextRes(" · "))

        // then
        assertEquals("one · two · three", English.resolve(text))
    }

    @Test
    fun `should join nothing to the empty string`() {
        // given
        val text = TextRes.Joined(parts = emptyList(), separator = TextRes(" · "))

        // then
        assertEquals("", English.resolve(text))
    }

    // The separator is text like any other, so a locale that punctuates a list differently changes
    // one catalogue entry rather than every mapper that builds one.
    @Test
    fun `should resolve the separator through the catalogue too`() {
        // given
        val text = Strings.clauses(listOf(TextRes("6 owned"), TextRes("1 idle")))

        // then
        assertEquals("6 owned · 1 idle", English.resolve(text))
    }

    @Test
    fun `should resolve a raw string to itself`() {
        assertEquals("Kepler-442", English.resolve(TextRes("Kepler-442")))
    }
}
