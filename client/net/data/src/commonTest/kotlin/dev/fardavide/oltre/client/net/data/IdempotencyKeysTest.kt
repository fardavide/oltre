package dev.fardavide.oltre.client.net.data

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdempotencyKeysTest {

    @Test
    fun `a minted key is 128 bits of hexadecimal`() {
        // given / when
        val key = randomIdempotencyKeys(Random(seed = 7)).mint().value

        // then — the length is the point rather than the digits: a shorter key would be a narrower
        // guarantee, and this one has to be unique for as long as `applied_verbs` keeps a row.
        assertEquals(32, key.length)
        assertTrue(key.all { it in "0123456789abcdef" }, "not hexadecimal: $key")
    }

    @Test
    fun `two keys minted in a row are not the same key`() {
        // given
        val keys = randomIdempotencyKeys(Random(seed = 7))

        // when / then
        assertEquals(2, setOf(keys.mint(), keys.mint()).size)
    }

    // A key whose leading bits happen to be zero still has to be full width, or two keys that
    // differ only in how many digits they were written with are two keys. `padStart` is what does
    // it and this is what notices if it goes — driven from a source that hands out nothing but
    // zeroes, because the case is otherwise one mint in sixteen and a test that waits for it is a
    // test that sometimes passes for the wrong reason.
    @Test
    fun `a key with leading zeroes is still full width`() {
        assertEquals("0".repeat(32), randomIdempotencyKeys(FakeRandom()).mint().value)
    }
}

private class FakeRandom : Random() {

    override fun nextBits(bitCount: Int): Int = 0
}
