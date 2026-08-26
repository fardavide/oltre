package dev.fardavide.oltre.client.auth.data

import kotlin.test.Test
import kotlin.test.assertEquals

// **The published vectors, and they are the whole reason this hash is written in Kotlin rather than
// reached for three times.** A wrong SHA-256 does not fail loudly: Apple answers a nonce mismatch and
// the gate refuses every sign-in for a reason nobody holding the phone can see.
class EncodingTest {

    @Test
    fun `should hash the empty message to the published digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256(ByteArray(0)).hex(),
        )
    }

    @Test
    fun `should hash abc to the published digest`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".encodeToByteArray()).hex(),
        )
    }

    // 56 characters, which is one byte past the point where the padding needs a second block. The
    // block boundary is where a hand-written implementation goes wrong and where nothing else would
    // notice.
    @Test
    fun `should hash a message that spills into a second block`() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).hex(),
        )
    }

    // Exactly 64 bytes: the length lands on the boundary, so the padding is a whole extra block. The
    // other case a hand-written implementation gets wrong.
    @Test
    fun `should hash a message that is exactly one block`() {
        assertEquals(
            "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            sha256(ByteArray(64) { 'a'.code.toByte() }).hex(),
        )
    }

    // RFC 7636 appendix B, which is the vector the whole of PKCE is checked against.
    @Test
    fun `should produce the challenge the PKCE specification publishes`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", sha256(verifier.encodeToByteArray()).base64Url())
    }

    // The three group sizes, because the padding is what an unpadded encoder has to get right and
    // each one exercises a different tail.
    @Test
    fun `should encode base64url without padding`() {
        assertEquals("", ByteArray(0).base64Url())
        assertEquals("AQ", byteArrayOf(1).base64Url())
        assertEquals("AQI", byteArrayOf(1, 2).base64Url())
        assertEquals("AQID", byteArrayOf(1, 2, 3).base64Url())
    }

    // The two bytes that make base64 and base64url differ, which is the whole point of the alphabet.
    @Test
    fun `should encode base64url with no plus and no slash`() {
        assertEquals("--8", byteArrayOf(0xFB.toByte(), 0xEF.toByte()).base64Url())
        assertEquals("__8", byteArrayOf(0xFF.toByte(), 0xFF.toByte()).base64Url())
    }

    @Test
    fun `should percent-encode everything outside the unreserved set`() {
        assertEquals("a-b._~9", "a-b._~9".urlEncoded())
        assertEquals("a%20b", "a b".urlEncoded())
        assertEquals("a%3Ab%2Fc", "a:b/c".urlEncoded())
        // Multi-byte, because a URL is bytes and an encoder that walked characters would produce
        // something the provider reads as a different string.
        assertEquals("%C3%A8", "è".urlEncoded())
    }
}
