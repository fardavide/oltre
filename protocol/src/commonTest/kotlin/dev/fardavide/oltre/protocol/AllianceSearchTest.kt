package dev.fardavide.oltre.protocol

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// A name finds an alliance — `alliance-sheet.md` §6. The normalisation, the uniqueness and the prefix
// match are the search slice's own; this file pins only the shape of the query and its answer.
class AllianceSearchTest {

    @Test
    fun `a cursor minted by the server cannot be blank`() {
        assertFailsWith<IllegalArgumentException> { AllianceSearchCursor("") }
    }

    // The last page is stated explicitly rather than by an absent key, on `encodeDefaults`'
    // reasoning: an omitted key would be a second way to say the same thing.
    @Test
    fun `a search response with no next page states the cursor as null rather than omitting it`() {
        val response = AllianceSearchResponse(
            apiVersion = ApiVersion.CURRENT,
            query = "vanguard",
            results = emptyList(),
            nextCursor = null,
        )
        val encoded = Protocol.json.encodeToJsonElement(AllianceSearchResponse.serializer(), response) as JsonObject

        assertEquals(JsonNull, encoded["nextCursor"])
        assertEquals(response, Protocol.json.decodeFromJsonElement(AllianceSearchResponse.serializer(), encoded))
    }

    @Test
    fun `a search response with a next page survives the round trip`() {
        val response = AllianceSearchResponse(
            apiVersion = ApiVersion.CURRENT,
            query = "vanguard",
            results = listOf(
                Alliance(
                    id = AllianceId("vanguard-1"),
                    name = AllianceName("Vanguard"),
                    tag = AllianceTag("VAN"),
                    level = AllianceLevel(2),
                    seats = AllianceSeats(taken = 3, cap = 12),
                ),
            ),
            nextCursor = AllianceSearchCursor("page-2"),
        )
        val text = Protocol.json.encodeToString(AllianceSearchResponse.serializer(), response)

        assertEquals(response, Protocol.json.decodeFromString(AllianceSearchResponse.serializer(), text))
    }

    // An empty result is a sentence the client can build — "nothing matched 'vanguard'" — because the
    // query it asked with is echoed back rather than left to the caller to remember.
    @Test
    fun `an empty result still echoes the normalised query it answered`() {
        val response = AllianceSearchResponse(
            apiVersion = ApiVersion.CURRENT,
            query = "nonexistent",
            results = emptyList(),
            nextCursor = null,
        )

        assertEquals("nonexistent", response.query)
    }

    @Test
    fun `the query parameter names are pinned`() {
        assertEquals("q", Protocol.SEARCH_QUERY_PARAM)
        assertEquals("cursor", Protocol.SEARCH_CURSOR_PARAM)
    }
}
