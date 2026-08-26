package dev.fardavide.oltre.client.auth.data

// **Two functions the platforms all need and none of them agrees about.** Both are written here, in
// `commonMain`, rather than as `expect`/`actual` over each platform's own library — and that is the
// point of the module's split: this is arithmetic, arithmetic is what a test can execute, and the
// alternative is three implementations of a hash that no machine in this repository can compare.

// **SHA-256, from FIPS 180-4.** Two callers need it and both are load-bearing:
//
// - **Apple's nonce.** The native flow is handed the *hash* of the raw nonce and puts that in the
//   token's claim, so the client has to compute it and send the same value to the server. Get it
//   wrong and every Apple sign-in is refused with a nonce mismatch — a gate that fails for a reason
//   nobody on the device can see.
// - **PKCE's `S256` challenge**, which is this over the verifier, for the two browser flows.
//
// Pinned by `Sha256Test` against the standard vectors, which is the whole reason it is here.
internal fun sha256(message: ByteArray): ByteArray {
    // The first thirty-two bits of the fractional parts of the cube roots of the first sixty-four
    // primes. Constants of the algorithm; nothing here is a choice.
    val k = ROUND_CONSTANTS
    val h = INITIAL_STATE.copyOf()

    // The padding is the specification's: a single 1 bit, zeros up to 56 mod 64, then the length in
    // bits as a big-endian 64-bit integer.
    val bitLength = message.size.toLong() * BITS_PER_BYTE
    val padded = ByteArray(paddedSize(message.size))
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (index in 0 until Long.SIZE_BYTES) {
        val shift = (Long.SIZE_BITS - BITS_PER_BYTE) - index * BITS_PER_BYTE
        padded[padded.size - Long.SIZE_BYTES + index] = (bitLength ushr shift).toByte()
    }

    val schedule = IntArray(ROUNDS)
    var block = 0
    while (block < padded.size) {
        for (index in 0 until WORDS_PER_BLOCK) {
            val at = block + index * Int.SIZE_BYTES
            schedule[index] = (padded[at].toInt() and BYTE_MASK shl 24) or
                (padded[at + 1].toInt() and BYTE_MASK shl 16) or
                (padded[at + 2].toInt() and BYTE_MASK shl 8) or
                (padded[at + 3].toInt() and BYTE_MASK)
        }
        for (index in WORDS_PER_BLOCK until ROUNDS) {
            val s0 = schedule[index - 15].rotateRight(7) xor
                schedule[index - 15].rotateRight(18) xor
                (schedule[index - 15] ushr 3)
            val s1 = schedule[index - 2].rotateRight(17) xor
                schedule[index - 2].rotateRight(19) xor
                (schedule[index - 2] ushr 10)
            schedule[index] = schedule[index - 16] + s0 + schedule[index - 7] + s1
        }

        var a = h[0]
        var b = h[1]
        var c = h[2]
        var d = h[3]
        var e = h[4]
        var f = h[5]
        var g = h[6]
        var hh = h[7]

        for (index in 0 until ROUNDS) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choose = (e and f) xor (e.inv() and g)
            val temp1 = hh + s1 + choose + k[index] + schedule[index]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + majority
            hh = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h[0] += a
        h[1] += b
        h[2] += c
        h[3] += d
        h[4] += e
        h[5] += f
        h[6] += g
        h[7] += hh
        block += BYTES_PER_BLOCK
    }

    val digest = ByteArray(h.size * Int.SIZE_BYTES)
    h.forEachIndexed { word, value ->
        for (index in 0 until Int.SIZE_BYTES) {
            val shift = (Int.SIZE_BITS - BITS_PER_BYTE) - index * BITS_PER_BYTE
            digest[word * Int.SIZE_BYTES + index] = (value ushr shift).toByte()
        }
    }
    return digest
}

// The lower-case hex of a digest, which is the form Apple's nonce claim takes. Hex rather than
// base64url here and base64url below, because the two callers are told different things by their own
// specifications and converging them would be this repository inventing a format.
internal fun ByteArray.hex(): String = buildString(size * 2) {
    this@hex.forEach { byte ->
        val value = byte.toInt() and BYTE_MASK
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0x0F])
    }
}

// **Base64url with no padding**, which is what RFC 7636 asks of a PKCE challenge and a verifier —
// and hand-written rather than reached for, because the standard library's encoder is behind an
// opt-in whose shape has moved between Kotlin versions and this is twenty lines that cannot.
internal fun ByteArray.base64Url(): String {
    val out = StringBuilder((size + 2) / 3 * 4)
    var index = 0
    while (index < size) {
        val remaining = size - index
        val b0 = this[index].toInt() and BYTE_MASK
        val b1 = if (remaining > 1) this[index + 1].toInt() and BYTE_MASK else 0
        val b2 = if (remaining > 2) this[index + 2].toInt() and BYTE_MASK else 0
        val triple = (b0 shl 16) or (b1 shl 8) or b2
        out.append(BASE64_URL_ALPHABET[triple ushr 18 and 0x3F])
        out.append(BASE64_URL_ALPHABET[triple ushr 12 and 0x3F])
        // No `=` padding, and the two guards are what drop it: a group of one byte writes two
        // characters and a group of two writes three, which is exactly what "unpadded" means.
        if (remaining > 1) out.append(BASE64_URL_ALPHABET[triple ushr 6 and 0x3F])
        if (remaining > 2) out.append(BASE64_URL_ALPHABET[triple and 0x3F])
        index += 3
    }
    return out.toString()
}

// Percent-encoding for a query value, to RFC 3986's unreserved set. Written here for `hex`'s reason:
// every platform has one, no two of them agree about `+` against `%20`, and a redirect URI that is
// encoded one way on Android and another on desktop is a mismatch nobody can see from the app.
internal fun String.urlEncoded(): String = buildString {
    encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and BYTE_MASK
        val char = value.toChar()
        if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in UNRESERVED_PUNCTUATION) {
            append(char)
        } else {
            append('%')
            append(HEX_DIGITS[value ushr 4].uppercaseChar())
            append(HEX_DIGITS[value and 0x0F].uppercaseChar())
        }
    }
}

private fun paddedSize(length: Int): Int {
    val withMarkerAndLength = length + 1 + Long.SIZE_BYTES
    val remainder = withMarkerAndLength % BYTES_PER_BLOCK
    return if (remainder == 0) withMarkerAndLength else withMarkerAndLength + (BYTES_PER_BLOCK - remainder)
}

private const val BITS_PER_BYTE = 8
private const val BYTE_MASK = 0xFF
private const val BYTES_PER_BLOCK = 64
private const val WORDS_PER_BLOCK = 16
private const val ROUNDS = 64
private const val UNRESERVED_PUNCTUATION = "-._~"
private const val HEX_DIGITS = "0123456789abcdef"
private const val BASE64_URL_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private val INITIAL_STATE = intArrayOf(
    0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
    0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
)

private val ROUND_CONSTANTS = intArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
    0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
    0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
    0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
    0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
    0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
    0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
    0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
)
