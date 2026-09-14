package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.fardavide.oltre.client.alliance.ui.AllianceTestTags
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.net.data.AllianceRequest
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
        test.onNodeWithText(English.resolve(dev.fardavide.oltre.client.design.text.Strings.allianceRequestSeat()))
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

    fun assertSaysSeats(taken: Int, cap: Int) = apply {
        assertReads(dev.fardavide.oltre.client.design.text.Strings.allianceSeatsLine(taken, cap))
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

    fun assertBoughtAProject() = apply {
        val bought = app.server.allianceRequests().filterIsInstance<AllianceRequest.BuyProject>()
        assertEquals(1, bought.size, "projects bought: $bought")
    }

    fun assertAnsweredARequest(admitted: Boolean) = apply {
        val answers = app.server.allianceRequests().filterIsInstance<AllianceRequest.AnswerRequest>()
        assertEquals(1, answers.size, "answers sent: $answers")
        val expected = if (admitted) JoinDecision.ADMITTED else JoinDecision.DECLINED
        assertEquals(expected, answers.single().decision)
    }
}
