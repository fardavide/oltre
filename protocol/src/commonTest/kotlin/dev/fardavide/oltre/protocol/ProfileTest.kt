package dev.fardavide.oltre.protocol

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// **What a player chose to be called and what face they chose to wear** — the wire half, which is
// the only half either end shares. See `profile-sheet.md` §3 for why none of this is a `ClientVerb`
// and why it is two routes of its own rather than a field on `SyncResponse`.
class ProfileTest {

    // ── The name ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a name is what the player typed and not an absence`() {
        assertFailsWith<IllegalArgumentException> { CommanderName("") }
        assertFailsWith<IllegalArgumentException> { CommanderName("   ") }
    }

    // The trim is the *client's* job and the refusal is the contract's, for the reason every guard
    // in this module is written that way: a value that arrives untrimmed came from something that
    // did not agree about the shape, and quietly fixing it would let two clients disagree about
    // whether "Ada " and "Ada" are one commander or two.
    @Test
    fun `a name that was not trimmed before it was sent is refused`() {
        assertFailsWith<IllegalArgumentException> { CommanderName(" Ada") }
        assertFailsWith<IllegalArgumentException> { CommanderName("Ada ") }
        assertFailsWith<IllegalArgumentException> { CommanderName("Ada\n") }
    }

    @Test
    fun `a name longer than the field accepts is refused`() {
        val longest = "a".repeat(CommanderName.MAX_LENGTH)
        assertEquals(longest, CommanderName(longest).value)
        assertFailsWith<IllegalArgumentException> { CommanderName("a".repeat(CommanderName.MAX_LENGTH + 1)) }
    }

    @Test
    fun `the bound is the one the field enforces`() {
        // *A Name You Chose* §Three: the counter shows from 18 and the field stops accepting at 24.
        // A contract that allowed a name the field cannot produce would be a bound nobody enforces.
        assertEquals(24, CommanderName.MAX_LENGTH)
    }

    @Test
    fun `one character is a name`() {
        assertEquals("V", CommanderName("V").value)
    }

    // A name is text a person typed rather than an identifier, so nothing here narrows the alphabet:
    // spaces inside it are the point, and an Italian or an emoji commander is a commander.
    @Test
    fun `a name may hold spaces and anything else a keyboard makes`() {
        assertEquals("Ada di Notte", CommanderName("Ada di Notte").value)
        assertEquals("Ada ☄", CommanderName("Ada ☄").value)
    }

    // ── The mark ──────────────────────────────────────────────────────────────────────────────

    // The reason these are enums on the wire rather than the drawings themselves: the paths live in
    // `:client:player:ui` as a `Canvas`, because a bitmap rasterises differently on the machine that
    // records a baseline and the machine that verifies it. A server has to keep answering the build
    // already on somebody's phone, so renaming a constant is free and changing these is a wire break.
    @Test
    fun `the preset wire names are pinned`() {
        assertEquals(
            listOf("THRESHOLD", "TERMINATOR", "APHELION", "SEXTANT", "WAKE", "SOUNDING"),
            MarkPreset.entries.map { it.name },
        )
    }

    @Test
    fun `the part wire names are pinned`() {
        assertEquals(listOf("LIMB", "TERMINATOR", "ORBIT", "WAKE"), MarkBody.entries.map { it.name })
        assertEquals(listOf("RISING", "TRANSFER", "TWIN", "NONE"), MarkPath.entries.map { it.name })
        assertEquals(listOf("DOT", "RING", "NONE"), MarkTerminus.entries.map { it.name })
    }

    // **A terminus is the end of a path, so a mark with no path has none** — *A Name You Chose* §Six,
    // and it is an `init` rather than a rule the two screens each remember. The composer does not
    // draw the terminus ladder when the path is `NONE`, which means an illegal pair can only arrive
    // from something that is not the composer.
    @Test
    fun `a mark with no path cannot have a terminus`() {
        assertFailsWith<IllegalArgumentException> {
            PlayerMark.Composed(body = MarkBody.LIMB, path = MarkPath.NONE, terminus = MarkTerminus.DOT)
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerMark.Composed(body = MarkBody.ORBIT, path = MarkPath.NONE, terminus = MarkTerminus.RING)
        }
    }

    @Test
    fun `a mark with no path and no terminus is a body on its own`() {
        val quiet = PlayerMark.Composed(body = MarkBody.ORBIT, path = MarkPath.NONE, terminus = MarkTerminus.NONE)

        assertEquals(MarkBody.ORBIT, quiet.body)
    }

    // The design's own arithmetic, asserted rather than quoted: 4 × (3 × 3 + 1) = 40. It is the
    // number that justified the composer over a wider fixed set, so it is worth a test that fails
    // if a part is ever added without the count being re-argued.
    @Test
    fun `there are forty legal composed marks`() {
        val legal = MarkBody.entries.flatMap { body ->
            MarkPath.entries.flatMap { path ->
                MarkTerminus.entries.mapNotNull { terminus ->
                    runCatching { PlayerMark.Composed(body, path, terminus) }.getOrNull()
                }
            }
        }

        assertEquals(40, legal.size)
        assertEquals(legal.size, legal.distinct().size)
    }

    // **Threshold is the one preset the composer can also make**, which is why the compose face opens
    // on it. The other five are silhouettes the grammar cannot produce — a centred disc, a full-width
    // ellipse, a 12.4-unit arc, a full-height plumb line — and that is the argument for keeping both.
    @Test
    fun `threshold is limb rising dot`() {
        assertEquals(
            PlayerMark.Composed(MarkBody.LIMB, MarkPath.RISING, MarkTerminus.DOT),
            MarkPreset.THRESHOLD.asComposed(),
        )
        MarkPreset.entries.filter { it != MarkPreset.THRESHOLD }.forEach {
            assertTrue(it.asComposed() == null, "$it is not a composition and must not claim to be one")
        }
    }

    // ── The profile ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a profile nobody has filled in states both absences out loud`() {
        val empty = PlayerProfile(name = null, mark = null)
        val encoded = Protocol.json.encodeToJsonElement(PlayerProfile.serializer(), empty) as JsonObject

        // Explicit nulls rather than absent keys, which is `encodeDefaults`' whole point one type
        // down: a reader must not have to tell "has not chosen" from "this build does not say".
        assertEquals(JsonNull, encoded["name"])
        assertEquals(JsonNull, encoded["mark"])
        assertEquals(empty, Protocol.json.decodeFromJsonElement(PlayerProfile.serializer(), encoded))
    }

    @Test
    fun `a preset profile survives the round trip`() {
        val chosen = PlayerProfile(
            name = CommanderName("Ada di Notte"),
            mark = PlayerMark.Preset(MarkPreset.SEXTANT),
        )
        val text = Protocol.json.encodeToString(PlayerProfile.serializer(), chosen)

        assertEquals(chosen, Protocol.json.decodeFromString(PlayerProfile.serializer(), text))
    }

    @Test
    fun `a composed profile survives the round trip`() {
        val chosen = PlayerProfile(
            name = CommanderName("Navigazione Stimata"),
            mark = PlayerMark.Composed(MarkBody.WAKE, MarkPath.TWIN, MarkTerminus.RING),
        )
        val text = Protocol.json.encodeToString(PlayerProfile.serializer(), chosen)

        assertEquals(chosen, Protocol.json.decodeFromString(PlayerProfile.serializer(), text))
    }

    // A preset and a composition are the same field and must be told apart on the wire, which is
    // what the discriminator is for. Pinned for `ClientVerb`'s reason.
    @Test
    fun `the two kinds of mark are told apart by a pinned discriminator`() {
        val preset = Protocol.json.encodeToJsonElement(
            PlayerMark.serializer(),
            PlayerMark.Preset(MarkPreset.WAKE),
        ) as JsonObject
        val composed = Protocol.json.encodeToJsonElement(
            PlayerMark.serializer(),
            PlayerMark.Composed(MarkBody.LIMB, MarkPath.RISING, MarkTerminus.DOT),
        ) as JsonObject

        assertEquals(JsonPrimitive("Preset"), preset["type"])
        assertEquals(JsonPrimitive("Composed"), composed["type"])
    }

    // The guard runs on decode as well as on construction, which is what turns a modified client
    // into `ApiError.Malformed` at the server's edge rather than a row holding a name the strip
    // cannot draw. `readRequest` maps exactly this exception.
    @Test
    fun `a name that breaks the bound is refused on the way in as well as on the way out`() {
        val overlong = JsonObject(
            mapOf(
                "name" to JsonPrimitive("a".repeat(CommanderName.MAX_LENGTH + 1)),
                "mark" to JsonNull,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            Protocol.json.decodeFromJsonElement(PlayerProfile.serializer(), overlong)
        }
    }

    @Test
    fun `an illegal composition is refused on the way in as well`() {
        val body = """{"type":"Composed","body":"LIMB","path":"NONE","terminus":"DOT"}"""

        assertFailsWith<IllegalArgumentException> {
            Protocol.json.decodeFromString(PlayerMark.serializer(), body)
        }
    }

    @Test
    fun `a set request carries the whole profile rather than the part that moved`() {
        val request = SetProfileRequest(
            apiVersion = ApiVersion.CURRENT,
            profile = PlayerProfile(name = CommanderName("Ada"), mark = PlayerMark.Preset(MarkPreset.THRESHOLD)),
        )
        val text = Protocol.json.encodeToString(SetProfileRequest.serializer(), request)

        assertEquals(request, Protocol.json.decodeFromString(SetProfileRequest.serializer(), text))
    }

    @Test
    fun `a profile response says which contract answered it`() {
        val response = ProfileResponse(
            apiVersion = ApiVersion.CURRENT,
            profile = PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.SOUNDING)),
        )
        val text = Protocol.json.encodeToString(ProfileResponse.serializer(), response)

        assertEquals(response, Protocol.json.decodeFromString(ProfileResponse.serializer(), text))
    }
}
