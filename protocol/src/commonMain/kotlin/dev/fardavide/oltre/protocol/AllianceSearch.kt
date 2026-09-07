package dev.fardavide.oltre.protocol

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// `GET /v1/alliance/search?q=…&cursor=…` — a name finds an alliance, `alliance-sheet.md` §6. The
// normalisation, the uniqueness, the prefix match and the rate limit are the search slice's; this
// slice states only the shape of the request and the answer.

// A page marker, minted by the server. **Opaque by construction** — the client hands it back and
// nothing on this side looks inside — on `IdempotencyKey`'s stance: the shape of the string is the
// minting server's business, and pinning it would strand the day the search moves from a keyset cursor
// to an offset.
@Serializable
@JvmInline
value class AllianceSearchCursor(val value: String) {

    init {
        require(value.isNotBlank()) { "a search cursor is minted by the server and cannot be blank" }
    }
}

// **`query` is the normalised query echoed back**, a plain `String` rather than an `AllianceName`
// because what comes back is the *normalised* form and normalisation is the search slice's column
// rather than a value this wire guards. It is what lets an empty result be a sentence — *"nothing
// matched 'vanguard'"* — rather than a blank panel, and what lets a client discard an answer to a
// query the player has already retyped.
//
// **`nextCursor` is nullable-and-required**: `null` means that was the last page, and the key is
// written out explicitly because `Protocol.json` sets `encodeDefaults`. Omitting the key on the last
// page would be a second way to say the same thing, which is the ambiguity `RequiredFieldsTest` exists
// to prevent.
@Serializable
data class AllianceSearchResponse(
    val apiVersion: ApiVersion,
    val query: String,
    val results: List<Alliance>,
    val nextCursor: AllianceSearchCursor?,
)
