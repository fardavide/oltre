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
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceRole
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

    // The four stops, by position: 10%, 25%, 50% and `All`. Index rather than words because the
    // share is a number in one language and the same number in the other, and the fourth is a word.
    //
    // **This sends nothing**, which is the whole of the split: picking changes what the control below
    // says it will do, and `contribute()` is the only thing on this destination that pays anything in.
    fun pickShare(stop: Int) = apply {
        test.onNodeWithTag(AllianceTestTags.row(AllianceTestTags.SHARE, stop)).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun contribute() = apply {
        test.onNodeWithTag(AllianceTestTags.CONTRIBUTE_ACTION).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun assertCannotContribute() = apply {
        test.onNodeWithTag(AllianceTestTags.CONTRIBUTE_ACTION).assertDoesNotExist()
    }

    // **What the screen says the next tap will send**, resource names and all — the promise the
    // control makes, as opposed to `assertContributed` below, which is what it then kept.
    fun assertWillSend(metal: Long, crystal: Long, deuterium: Long) = apply {
        val basket = Strings.clauses(
            listOf(
                Strings.amountOfResource(Strings.groupedNumber(metal), ResourceKind.METAL),
                Strings.amountOfResource(Strings.groupedNumber(crystal), ResourceKind.CRYSTAL),
                Strings.amountOfResource(Strings.groupedNumber(deuterium), ResourceKind.DEUTERIUM),
            ),
        )
        test.onNodeWithTag(AllianceTestTags.CONTRIBUTE_BASKET)
            .performScrollTo()
            .assert(hasText(English.resolve(basket)))
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

    // Let a held answer land. Its own name rather than a bare `waitForIdle` in a test body, for the
    // reason every other method here has one: a test says what happened, not how it was awaited.
    fun settle() = apply {
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
                .assert(carries(name))
        }
    }

    // Scoped to one roster row, for `assertRosterNames`' reason and then some: a level badge is the
    // worst thing to match by text alone, because the player strip carries the signed-in
    // commander's own — so `LV 0` on the chrome would answer for `LV 0` on a member.
    fun assertRosterRowReads(row: Int, text: TextRes) = apply {
        test.onNodeWithTag(AllianceTestTags.row(AllianceTestTags.ROSTER_ROW, row))
            .performScrollTo()
            .assert(carries(English.resolve(text)))
    }

    // **A roster row says its words two different ways depending on who is reading it**, and this is
    // what stops the tests noticing. A row that presses carries `Modifier.clickable`, which *merges*
    // its descendants' semantics into the row node — so the name is the row's own text. A row that
    // does not press has no merge, and the name is a descendant. Matching only one of the two made
    // every assertion about a founder's roster fail the moment the arrow shipped, on rows whose text
    // was plainly on screen.
    private fun carries(text: String) = hasText(text, substring = true) or hasAnyDescendant(hasText(text))

    // Whether one roster row answers a tap, which since 0.26 is the `→` rather than a control.
    // Row-scoped rather than counted, because *which* rows answer is the whole of what the
    // permission table decides — a count would pass with the right number of arrows on the wrong
    // people.
    fun assertRowPresses(row: Int, presses: Boolean) = apply {
        val node = test.onNodeWithTag(AllianceTestTags.row(AllianceTestTags.ROSTER_ROW, row)).performScrollTo()
        val arrow = carries("→")
        node.assert(if (presses) arrow else !arrow)
    }

    // ── The member commands ──────────────────────────────────────────────────────────────────

    fun openMember(row: Int) = apply {
        test.onNodeWithTag(AllianceTestTags.row(AllianceTestTags.ROSTER_ROW, row)).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun assertNoMemberFace() = apply {
        test.onNodeWithTag(AllianceTestTags.MEMBER_FACE).assertDoesNotExist()
    }

    fun assertMemberFaceReads(text: TextRes) = apply {
        test.onNodeWithTag(AllianceTestTags.MEMBER_FACE)
            .assert(hasAnyDescendant(hasText(English.resolve(text))))
    }

    fun assertRoleCommand(action: TextRes?) = apply {
        if (action == null) {
            test.onNodeWithTag(AllianceTestTags.MEMBER_ROLE_ACTION).assertDoesNotExist()
        } else {
            test.onNodeWithTag(AllianceTestTags.MEMBER_ROLE_ACTION)
                .assertIsDisplayed()
                .assert(hasText(English.resolve(action)))
        }
    }

    // **No `performScrollTo` on any of these four, unlike every control on the destination behind
    // them.** The face is in a `ModalBottomSheet`, which has no scrolling parent, and asking one to
    // scroll fails with *"Semantic Node has no parent layout with a Scroll SemanticsAction"* rather
    // than with anything about the control. The face is short by design — 399dp at its tallest — so
    // there is nothing to scroll to.
    fun setRole() = apply {
        test.onNodeWithTag(AllianceTestTags.MEMBER_ROLE_ACTION).performClick()
        test.waitForIdle()
    }

    fun askToKick() = apply {
        test.onNodeWithTag(AllianceTestTags.MEMBER_KICK).performClick()
        test.waitForIdle()
    }

    fun confirmKick() = apply {
        test.onNodeWithTag(AllianceTestTags.MEMBER_KICK_CONFIRM).performClick()
        test.waitForIdle()
    }

    fun keepThem() = apply {
        test.onNodeWithTag(AllianceTestTags.MEMBER_KEEP).performClick()
        test.waitForIdle()
    }

    // The first tap sends nothing — it only asks again. What tells the two steps apart is the filled
    // red, which exists on the last step and nowhere else.
    fun assertAsking() = apply {
        test.onNodeWithTag(AllianceTestTags.MEMBER_KICK_CONFIRM).assertDoesNotExist()
        test.onNodeWithTag(AllianceTestTags.MEMBER_KICK).assertIsDisplayed()
    }

    fun assertOnTheLastStep() = apply {
        test.onNodeWithTag(AllianceTestTags.MEMBER_KICK_CONFIRM).assertIsDisplayed()
        test.onNodeWithTag(AllianceTestTags.MEMBER_KEEP).assertIsDisplayed()
    }

    // **Absent, not greyed** — a founder who cannot leave and cannot disband is offered no control
    // rather than one that refuses. The hole is the design's; see `alliance-sheet.md`.
    fun assertNoWayOut() = apply {
        test.onNodeWithTag(AllianceTestTags.DEPARTURE).assertDoesNotExist()
    }

    fun depart() = apply {
        test.onNodeWithTag(AllianceTestTags.DEPARTURE).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun assertNothingToConfirm() = apply {
        test.onNodeWithTag(AllianceTestTags.CONFIRM_CONTRIBUTE).assertDoesNotExist()
    }

    // **What the panel says the pool holds**, by row and in the rail's own order. The mirror of
    // `assertContributed` below and the half it cannot see: a contribution that left the phone and
    // never came back to the screen looks identical from the envelope end.
    fun assertPoolReads(metal: Long, crystal: Long, deuterium: Long) = apply {
        listOf(metal, crystal, deuterium).forEachIndexed { index, amount ->
            test.onNodeWithTag(AllianceTestTags.row(AllianceTestTags.POOL_ROW, index))
                .performScrollTo()
                .assert(hasAnyDescendant(hasText(English.resolve(Strings.groupedNumber(amount)))))
        }
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

    // The second roster row, which is the first one a founder may command — their own is a plain
    // readout rather than a dead control. Two taps now, not one: the kick confirms.
    fun kickTheSecondMember() = apply {
        openMember(row = 1)
        askToKick()
        confirmKick()
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

    // **What the first tap of a kick has to prove: that it sent nothing.** Asserted against the
    // requests the server actually received rather than against the screen, because a face that
    // looked right while a route fired is exactly the failure a two-step confirm exists to prevent.
    fun assertKeptEverybody() = apply {
        val removed = app.server.allianceRequests().filterIsInstance<AllianceRequest.RemoveMember>()
        assertEquals(0, removed.size, "removals: $removed")
    }

    fun assertPromoted(role: AllianceRole = AllianceRole.ADMIN) = apply {
        val roles = app.server.allianceRequests().filterIsInstance<AllianceRequest.SetMemberRole>()
        assertEquals(1, roles.size, "role changes: $roles")
        assertEquals(role, roles.single().role)
    }

    fun assertAnsweredARequest(admitted: Boolean) = apply {
        val answers = app.server.allianceRequests().filterIsInstance<AllianceRequest.AnswerRequest>()
        assertEquals(1, answers.size, "answers sent: $answers")
        val expected = if (admitted) JoinDecision.ADMITTED else JoinDecision.DECLINED
        assertEquals(expected, answers.single().decision)
    }
}
