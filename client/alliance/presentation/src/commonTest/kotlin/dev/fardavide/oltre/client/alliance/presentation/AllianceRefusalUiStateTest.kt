package dev.fardavide.oltre.client.alliance.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// **What a refused alliance act says, which until `#164` was nothing.** `App.kt`'s founding arm set
// two field flags and stopped, so every refusal that was not a taken name or tag left the player
// pressing *Found it*, the server answering, and the screen not moving — the silent no-op the project
// forbids outright, reached through a control that is not dead in the diff, only in the cases the
// diff does not cover.
//
// It is a `when` over a sealed type in a `presentation` module precisely so this file can exist: in
// the shell the same decision would be reachable only by a behaviour test driving a fake server into
// one refusal at a time.
class AllianceRefusalUiStateTest {

    // **The floor, and the only test here that would have failed before the fix.** Everything else
    // below is about saying the *right* thing; this is about saying anything at all.
    @Test
    fun `every refusal the server can send reaches a sentence`() {
        for (error in EVERY_ERROR) {
            if (error == ApiError.AllianceNameTaken || error == ApiError.AllianceTagTaken) continue
            val refusal = assertNotNull(allianceRefusalUiState(error), "$error says nothing")
            assertTrue(English.resolve(refusal.body).isNotBlank(), "$error has an empty body")
        }
    }

    // **The two that do have somewhere to land are the two that stay silent here.** A block and a
    // field line about the same refusal would say it twice, and the field line is the better of the
    // two: it is under the box holding the string it is about, and it clears on the next keystroke.
    @Test
    fun `a taken name or tag draws no block because it has a field`() {
        assertNull(allianceRefusalUiState(ApiError.AllianceNameTaken))
        assertNull(allianceRefusalUiState(ApiError.AllianceTagTaken))
    }

    @Test
    fun `nothing refused draws nothing`() {
        assertNull(allianceRefusalUiState(null))
    }

    // One lead for all of them: the non-event is the same sentence whichever act was refused, and
    // the act is not a fact this function has.
    @Test
    fun `every refusal leads with the same non-event`() {
        val leads = EVERY_ERROR.mapNotNull { allianceRefusalUiState(it)?.lead }.toSet()

        assertEquals(setOf(Strings.refusedAllianceLead()), leads)
    }

    // **The split is what a player can do next**, and it is the whole argument for four bodies
    // rather than one: wait and ask again, go and look at where you stand, update the app, or
    // nothing. A single generic sentence would have been this bug with a sentence painted over it.
    @Test
    fun `a price that moved says the colony still has it`() {
        assertEquals(
            Strings.refusedAllianceShortBody(),
            assertNotNull(allianceRefusalUiState(ApiError.AllianceFoundingUnaffordable)).body,
        )
    }

    @Test
    fun `a roster that moved elsewhere says to go and look`() {
        for (error in listOf(ApiError.AlreadyInAnAlliance, ApiError.AllianceRoleTooLow, ApiError.AllianceFull)) {
            assertEquals(
                Strings.refusedAllianceStandingBody(),
                assertNotNull(allianceRefusalUiState(error)).body,
                "$error",
            )
        }
    }

    // `#163`'s subject through a different door, and its own arm because *update the app* is an
    // action and *ask again* is not.
    @Test
    fun `a build the server has outgrown is told to update rather than to retry`() {
        val refusal = assertNotNull(
            allianceRefusalUiState(ApiError.UnsupportedApiVersion(ApiVersion(5), ApiVersion(5))),
        )

        assertEquals(Strings.refusedAllianceOutdatedBody(), refusal.body)
    }

    // **Nothing on this destination tells a signed-in player to sign in**, even for the two
    // refusals that are about a session: the gate owns that sentence and raises its own face, and
    // advice about a control that is not on the screen is the dead-control rule in its quietest form.
    @Test
    fun `a session refusal does not point at a control this screen does not have`() {
        for (error in listOf(ApiError.Unauthenticated, ApiError.SessionExpired)) {
            val body = English.resolve(assertNotNull(allianceRefusalUiState(error)).body)
            assertTrue("sign in" !in body.lowercase(), "$error points at the gate: $body")
        }
    }

    private companion object {

        // Every member of the sealed hierarchy, written out rather than derived: `ApiError` is not
        // an enum and has no `entries`, and a list that missed one would let exactly the refusal
        // nobody thought about go back to saying nothing.
        val EVERY_ERROR: List<ApiError> = listOf(
            ApiError.Unauthenticated,
            ApiError.SessionExpired,
            ApiError.UnsupportedApiVersion(ApiVersion(5), ApiVersion(5)),
            ApiError.NoColony,
            ApiError.StaleColony,
            ApiError.TooManyRequests(retryAfterSeconds = 30),
            ApiError.Malformed(detail = "that did not parse"),
            ApiError.AllianceNameTaken,
            ApiError.AllianceTagTaken,
            ApiError.AllianceFull,
            ApiError.NoSuchAlliance,
            ApiError.NotInAnAlliance,
            ApiError.AlreadyInAnAlliance,
            ApiError.AllianceRoleTooLow,
            ApiError.StaleAlliance,
            ApiError.AllianceTreasuryShort,
            ApiError.AllianceFoundingUnaffordable,
            ApiError.Internal(detail = "the server had a bad day"),
        )
    }
}
