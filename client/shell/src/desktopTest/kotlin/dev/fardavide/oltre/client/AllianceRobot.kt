package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.fardavide.oltre.client.alliance.ui.AllianceTestTags
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.net.data.AllianceRequest
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.JoinDecision
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The alliance's controls, driven through names rather than through node queries in a test body** —
// the taxonomy's own rule for a behaviour test, and the reason this file exists beside `AppRobot`
// rather than inside the suite that uses it.
@OptIn(ExperimentalTestApi::class)
internal class AllianceRobot(private val app: AppRobot) {

    private val test get() = app.test

    fun search(query: String) = apply {
        test.onNodeWithTag(AllianceTestTags.SEARCH_FIELD).performScrollTo().performTextInput(query)
        test.waitForIdle()
    }

    fun requestTheFirstSeat() = apply {
        test.onNodeWithText(English.resolve(Strings.allianceRequestSeat()))
            .performScrollTo()
            .performClick()
        test.waitForIdle()
    }

    fun typeAName(name: String) = apply {
        test.onNodeWithTag(AllianceTestTags.FOUND_NAME).performScrollTo().performTextInput(name)
        test.waitForIdle()
    }

    fun typeATag(tag: String) = apply {
        test.onNodeWithTag(AllianceTestTags.FOUND_TAG).performScrollTo().performTextInput(tag)
        test.waitForIdle()
    }

    fun found() = apply {
        test.onNodeWithTag(AllianceTestTags.FOUND_ACTION).performScrollTo().performClick()
        test.waitForIdle()
    }

    // The four chips, by position: 10%, 25%, 50% and `All`. Index rather than words because the
    // share is a number in one language and the same number in the other, and the fourth is a word.
    fun contribute(chip: Int) = apply {
        test.onNodeWithTag(AllianceTestTags.row(AllianceTestTags.CHIP, chip)).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun confirmContributingEverything() = apply {
        test.onNodeWithTag(AllianceTestTags.CONFIRM_CONTRIBUTE).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun keepIt() = apply {
        test.onNodeWithTag(AllianceTestTags.KEEP_CONTRIBUTE).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun buyTheFirstProject() = apply {
        test.onNodeWithTag(AllianceTestTags.row(AllianceTestTags.PROJECT_BUY, 0)).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun answerTheFirstRequest(admit: Boolean) = apply {
        val tag = if (admit) AllianceTestTags.ACCEPT else AllianceTestTags.DECLINE
        test.onNodeWithTag(AllianceTestTags.row(tag, 0)).performScrollTo().performClick()
        test.waitForIdle()
    }

    // ── What it reads ────────────────────────────────────────────────────────────────────────

    fun assertReads(text: TextRes) = apply {
        test.onNodeWithText(English.resolve(text)).performScrollTo().assertIsDisplayed()
    }

    fun assertDoesNotRead(text: TextRes) = apply {
        test.onNodeWithText(English.resolve(text)).assertDoesNotExist()
    }

    fun assertSaysSeats(taken: Int, cap: Int) = apply {
        assertReads(Strings.allianceSeatsLine(taken, cap))
    }

    // **Who is actually drawn on the roster**, which is the half that went missing: every alliance
    // act answers with a standing alone, so a screen that did not follow it with a roster read drew
    // a card with no rows in it and no way to tell that from an alliance of nobody.
    //
    // By row rather than by text, because the player strip carries the signed-in commander's name
    // too — a bare text query matches both and cannot tell a roster from a chrome line.
    fun assertRosterNames(vararg names: String) = apply {
        names.forEachIndexed { index, name ->
            test.onNodeWithTag(AllianceTestTags.row(AllianceTestTags.ROSTER_ROW, index))
                .performScrollTo()
                .assertIsDisplayed()
                .assert(hasAnyDescendant(hasText(name)))
        }
    }

    fun depart() = apply {
        test.onNodeWithTag(AllianceTestTags.DEPARTURE).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun assertNothingToConfirm() = apply {
        test.onNodeWithTag(AllianceTestTags.CONFIRM_CONTRIBUTE).assertDoesNotExist()
    }

    // ── What actually left the phone ─────────────────────────────────────────────────────────
    //
    // The half a screenshot cannot see, and the one that matters most here: a chip that redrew the
    // rail and sent nothing would look identical.

    fun assertContributed(metal: Long, crystal: Long, deuterium: Long) = apply {
        val sent = contributions()
        assertEquals(1, sent.size, "contributions sent: $sent")
        assertEquals(metal, sent.single().amount.metal, "metal")
        assertEquals(crystal, sent.single().amount.crystal, "crystal")
        assertEquals(deuterium, sent.single().amount.deuterium, "deuterium")
    }

    fun assertNothingContributed() = apply {
        assertTrue(contributions().isEmpty(), "a contribution left the phone: ${contributions()}")
    }

    private fun contributions(): List<ClientVerb.Contribute> = app.server.syncs()
        .flatMap { it.envelopes }
        .map { it.verb }
        .filterIsInstance<ClientVerb.Contribute>()

    // **What the server holds once the founding is answered.** The charge is the server's — `core`
    // takes the price out inside the route's transaction — so the colony on the fake is the honest
    // place to read it, exactly as the contribution assertions above read the sync envelopes.
    fun assertColonyCharged(metal: Long, crystal: Long, deuterium: Long) = apply {
        val held = checkNotNull(app.server.colony) { "the server holds no colony" }.state.resources
        assertEquals(metal, held.metal, "metal")
        assertEquals(crystal, held.crystal, "crystal")
        assertEquals(deuterium, held.deuterium, "deuterium")
    }

    // And that the phone went back for it. Without the sync the tap fires, the rail keeps drawing
    // stock the server has already spent, and nothing on screen says otherwise until the next
    // minute tick.
    fun assertReadTheColonyBack() = apply {
        val founded = app.server.allianceRequests().indexOfFirst { it is AllianceRequest.Create }
        assertTrue(founded >= 0, "nothing was founded")
        assertTrue(app.server.syncs().isNotEmpty(), "the colony was never read back after founding")
    }

    fun assertBoughtAProject() = apply {
        val bought = app.server.allianceRequests().filterIsInstance<AllianceRequest.BuyProject>()
        assertEquals(1, bought.size, "projects bought: $bought")
    }

    fun withdraw() = apply {
        test.onNodeWithTag(AllianceTestTags.WITHDRAW).performScrollTo().performClick()
        test.waitForIdle()
    }

    // The second roster row, which is the first one a founder may remove — their own is a plain
    // readout rather than a dead control.
    fun removeTheSecondMember() = apply {
        test.onNodeWithText(English.resolve(Strings.allianceRemove())).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun assertCanFound() = apply {
        test.onNodeWithTag(AllianceTestTags.FOUND_ACTION).assertIsDisplayed()
    }

    fun assertCannotFound() = apply {
        test.onNodeWithTag(AllianceTestTags.FOUND_ACTION).assertDoesNotExist()
    }

    fun assertFounded(name: String, tag: String) = apply {
        val created = app.server.allianceRequests().filterIsInstance<AllianceRequest.Create>()
        assertEquals(1, created.size, "alliances founded: $created")
        assertEquals(name, created.single().name.value)
        assertEquals(tag, created.single().tag.value)
    }

    fun assertFoundedNothing() = apply {
        val created = app.server.allianceRequests().filterIsInstance<AllianceRequest.Create>()
        assertTrue(created.isEmpty(), "an alliance was founded: $created")
    }

    fun assertAskedToJoin(alliance: AllianceId) = apply {
        val asked = app.server.allianceRequests().filterIsInstance<AllianceRequest.RequestToJoin>()
        assertEquals(listOf(alliance), asked.map { it.alliance })
    }

    fun assertDetached() = apply {
        val left = app.server.allianceRequests().filterIsInstance<AllianceRequest.Leave>()
        assertEquals(1, left.size, "detachments: $left")
    }

    fun assertRemovedAMember() = apply {
        val removed = app.server.allianceRequests().filterIsInstance<AllianceRequest.RemoveMember>()
        assertEquals(1, removed.size, "removals: $removed")
    }

    fun assertAnsweredARequest(admitted: Boolean) = apply {
        val answers = app.server.allianceRequests().filterIsInstance<AllianceRequest.AnswerRequest>()
        assertEquals(1, answers.size, "answers sent: $answers")
        val expected = if (admitted) JoinDecision.ADMITTED else JoinDecision.DECLINED
        assertEquals(expected, answers.single().decision)
    }
}
