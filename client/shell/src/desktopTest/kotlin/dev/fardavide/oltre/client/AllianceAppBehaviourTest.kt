package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceLevel
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceProgress
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.AllianceProjectOffer
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceRosterResponse
import dev.fardavide.oltre.protocol.AllianceSearchResponse
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceStanding
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.TreasuryResponse
import dev.fardavide.oltre.core.Experience
import kotlin.test.Test

// **The treasury, driven end to end** — `#144`'s own merge bar: the resources leave the colony, the
// control refuses with a sentence rather than doing nothing when there is no signal, and a project
// is purchasable with an effect visible without leaving the screen.
//
// Every assertion about what *left the phone* goes through the fake server rather than through the
// screen, because a chip that redrew the rail and sent nothing would look identical to one that
// worked — which is the failure the whole offline era makes possible.
@OptIn(ExperimentalTestApi::class)
class AllianceAppBehaviourTest {

    @Test
    fun `a share of the stock leaves the colony and the pool has it`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.contribute(chip = 0)

            // 10% of 42,100 / 9,600 / 1,200, **floored** — which is the whole promise the chip
            // makes: the figure it printed is the basket that left.
            alliance.assertContributed(metal = 4_210, crystal = 960, deuterium = 120)
        }
    }

    // **`All` asks first, and the three fixed shares do not** — Davide, 2026-09-14. It is the one tap
    // that empties a colony and nothing in this game takes it back out of the pool.
    @Test
    fun `everything you have asks before it goes`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.contribute(chip = 3)

            alliance.assertReads(Strings.allianceConfirmAllTitle())
            alliance.assertNothingContributed()
        }
    }

    @Test
    fun `confirming it sends the whole stock`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.contribute(chip = 3).confirmContributingEverything()

            alliance.assertContributed(metal = 42_100, crystal = 9_600, deuterium = 1_200)
        }
    }

    @Test
    fun `keeping it sends nothing and clears the question`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.contribute(chip = 3).keepIt()

            alliance.assertNothingContributed()
            alliance.assertNothingToConfirm()
        }
    }

    // **Red rather than amber, and the resources stay put** — a contribution is `LOOK_DONT_ACT`,
    // because membership is a fact about other people that `core` cannot replay. A control that
    // silently did nothing here is the failure the whole no-dead-control rule exists to prevent.
    @Test
    fun `with no signal the chip refuses with a sentence rather than queueing`() {
        val server = enlisted()
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            server.offline = true

            alliance.contribute(chip = 0)

            alliance.assertReads(Strings.refusedContributionLead())
            alliance.assertReads(Strings.refusedContributionBody())
        }
    }

    // §8's second condition: at least one project is purchasable and its effect is legible on a
    // screen this slice draws — the roster header, which goes from twelve seats to fourteen.
    @Test
    fun `a project is bought and the roster header says so`() {
        val server = enlisted()
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            alliance.assertSaysSeats(taken = 1, cap = 12)
            // What the server hands back once the charter is bought: two more seats on the alliance,
            // and the pool the poorer for it.
            server.allianceStanding = AllianceStanding.Enlisted(ALLIANCE.copy(seats = AllianceSeats(1, 14)), AllianceRole.FOUNDER)

            alliance.buyTheFirstProject()

            alliance.assertBoughtAProject()
            alliance.assertSaysSeats(taken = 1, cap = 14)
        }
    }

    @Test
    fun `a founder answers the one request that is waiting`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.answerTheFirstRequest(admit = true)

            // **What left the phone, not what the screen did with the answer.** The fake hands back
            // the roster it was scripted with, so the row is still drawn — and that is the right
            // thing to assert here anyway: the control's job is to send the decision, and whether
            // the list then shrinks is the server's answer rather than the tap's.
            alliance.assertAnsweredARequest(admitted = true)
        }
    }

    // **The good empty state, at full strength and naming where a request comes from** — a founder
    // with nobody waiting is not an error and gets no control.
    @Test
    fun `nobody waiting is a sentence rather than a hole`() {
        val server = enlisted().apply { allianceRoster = ROSTER.copy(pending = emptyList()) }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)

            AllianceRobot(this).assertReads(Strings.alliancePendingEmpty())
        }
    }

    // Not in one: a search and the founding block under it, saying what founding costs before
    // anything is typed — which today is nothing, because no route charges for it.
    @Test
    fun `a player in no alliance is offered a search and a way to found one`() {
        app(saved = colony(), api = unaffiliated()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.assertReads(Strings.allianceSearchIdle())
            alliance.assertReads(Strings.allianceFoundBody())
        }
    }

    @Test
    fun `a search that finds nothing names the string and stops`() {
        val server = unaffiliated()
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.search("Aphelion")

            alliance.assertReads(Strings.allianceSearchEmpty(TextRes("Aphelion")))
        }
    }

    @Test
    fun `founding one sends the name and the tag that were typed`() {
        val (saved, server) = founding()
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FRA").found()

            alliance.assertFounded(name = "Ferro Alto", tag = "FRA")
        }
    }

    // ── What founding costs ──────────────────────────────────────────────────────────────────

    // **The price is stated before anything is typed** — `alliance-sheet.md` §7 — and it is the price
    // the server advertises rather than one the client holds a copy of.
    @Test
    fun `the founding block states what an alliance costs`() {
        val (saved, server) = founding()
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)

            AllianceRobot(this).assertReads(Strings.allianceFoundPrice(PRICE_LINE))
        }
    }

    // **The price leaves the colony, and the phone reads it back.** Founding is the one alliance act
    // that spends a colony and the charge happens on the server, so the tap is followed by a sync —
    // without it the rail would go on showing stock the server had already taken until the next
    // minute tick, on the screen the tap was made from.
    @Test
    fun `founding one charges the colony and reads the charged one back`() {
        val (saved, server) = founding()
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            server.allianceStanding = AllianceStanding.Enlisted(ALLIANCE, AllianceRole.FOUNDER)
            server.allianceRoster = ROSTER.copy(pending = emptyList())

            alliance.typeAName("Ferro Alto").typeATag("FRA").found()

            // 500,000 of each, less the 200,000 / 100,000 / 50,000 the block said it would cost.
            alliance.assertColonyCharged(metal = 300_000, crystal = 400_000, deuterium = 450_000)
            alliance.assertReadTheColonyBack()
        }
    }

    // **A colony that cannot pay is offered no control at all**, which is the same absence a tag the
    // contract refuses earns — and the line beside it is what makes the absence answerable.
    @Test
    fun `a colony that cannot cover the price offers no control and says why`() {
        app(saved = colony(), api = unaffiliated()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FRA")

            alliance.assertCannotFound()
            alliance.assertReads(Strings.allianceFoundShort())
            alliance.assertFoundedNothing()
        }
    }

    // A price the server has not answered for withholds the control rather than guessing at a
    // figure: a button offered over an unknown price is one the route would refuse.
    @Test
    fun `a price the server did not answer for says so and offers no control`() {
        val saved = founderColony()
        val server = unaffiliated(saved).apply { foundingPriceError = ApiError.Internal("no price today") }
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FRA")

            alliance.assertReads(Strings.allianceFoundPriceUnread())
            alliance.assertCannotFound()
        }
    }

    // **Founding puts you inside the thing you founded, roster and pool and all** — and until this,
    // it did not. Every alliance act answers with a standing and nothing else, so the roster the
    // gateway hands back is `Unread`; the face drew an alliance with an empty roster and a treasury
    // that said it had not been read, for the life of the process. That is what *"I create an
    // alliance and it doesn't work"* looked like from the outside.
    @Test
    fun `founding one lands the player in it with a roster and a pool`() {
        val (saved, server) = founding()
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            // What the server answers a founder with: a seat, the roster it made, and an empty pool.
            server.allianceStanding = AllianceStanding.Enlisted(ALLIANCE, AllianceRole.FOUNDER)
            server.allianceRoster = ROSTER.copy(pending = emptyList())
            server.treasury = TREASURY

            alliance.typeAName("Ferro Alto").typeATag("FRA").found()

            alliance.assertSaysSeats(taken = 1, cap = 12)
            alliance.assertRosterNames("Dead Reckoning")
            alliance.assertReads(Strings.allianceTreasuryRule())
        }
    }

    // The same hole from the other end: admitting somebody used to take the whole roster off the
    // screen, because the answer carries a standing and the roster behind it went unread.
    @Test
    fun `answering a request leaves the roster on screen`() {
        val server = enlisted()
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            server.allianceRoster = ROSTER.copy(members = ROSTER.members + MEMBER, pending = emptyList())

            alliance.answerTheFirstRequest(admit = true)

            alliance.assertRosterNames("Dead Reckoning", "Slow Burn")
        }
    }

    // Leaving takes the pool with it. Without this the next founding block would open over the
    // previous alliance's figures, which is somebody else's money on your screen.
    @Test
    fun `leaving forgets the pool it could read`() {
        val server = enlisted()
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            alliance.assertReads(Strings.allianceTreasuryRule())
            server.allianceStanding = AllianceStanding.Unaffiliated

            alliance.depart()

            alliance.assertReads(Strings.allianceFoundBody())
            alliance.assertDoesNotRead(Strings.allianceTreasuryRule())
        }
    }

    // **Absent, never greyed.** A name with no tag is not a thing that can be committed, so there is
    // no control to press rather than one that answers no.
    @Test
    fun `a name with no tag offers no control at all`() {
        app(saved = colony(), api = unaffiliated()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto")

            alliance.assertCannotFound()
        }
    }

    // **The first refusal in this app a finger can reach**, and it lands on the field: the answer was
    // about the string that was there, so the value stays editable.
    @Test
    fun `a name somebody already has is refused on the field`() {
        val saved = founderColony()
        val server = unaffiliated(saved).apply { createAllianceError = ApiError.AllianceNameTaken }
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FRA").found()

            alliance.assertReads(Strings.allianceNameTaken(TextRes("Ferro Alto")))
            alliance.assertCannotFound()
        }
    }

    @Test
    fun `typing again clears the refusal and offers the control back`() {
        val saved = founderColony()
        val server = unaffiliated(saved).apply { createAllianceError = ApiError.AllianceTagTaken }
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            alliance.typeAName("Ferro Alto").typeATag("FRA").found()

            alliance.typeATag("X")

            alliance.assertCanFound()
        }
    }

    @Test
    fun `asking to join sends the alliance that was tapped`() {
        val server = unaffiliated().apply {
            allianceSearch = AllianceSearchResponse(ApiVersion.CURRENT, "ferro", listOf(ALLIANCE), null)
        }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.search("Ferro").requestTheFirstSeat()

            alliance.assertAskedToJoin(ALLIANCE.id)
        }
    }

    // Nothing about a petition expires (`alliance-sheet.md` §1.4), so the waiting face has no
    // countdown and no word about time — only a way to take it back.
    @Test
    fun `a pending petition offers a way to withdraw it`() {
        val server = unaffiliated().apply { allianceStanding = AllianceStanding.Petitioning(ALLIANCE) }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.assertReads(Strings.allianceWaitingBody())
            alliance.withdraw()

            alliance.assertDetached()
        }
    }

    @Test
    fun `a founder removes a member from the roster`() {
        val server = enlisted().apply { allianceRoster = ROSTER.copy(members = ROSTER.members + MEMBER) }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.removeTheSecondMember()

            alliance.assertRemovedAMember()
        }
    }

    // ── What happens when the far end is not there ───────────────────────────────────────────
    //
    // **Every alliance control is server-backed**, so the arms below are most of what this feature
    // does on a train — and a control that answered nothing at all is the failure the whole
    // no-dead-control rule exists to prevent.

    @Test
    fun `a search that cannot reach the server leaves the field alone`() {
        val server = unaffiliated()
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            server.offline = true

            alliance.search("Ferro")

            alliance.assertReads(Strings.allianceSearchHeld())
        }
    }

    // A refusal on a search is not a refusal on a name — there is nothing to put on a field, so the
    // results simply do not arrive and the idle line stands.
    @Test
    fun `a refused search shows no results and no refusal on a field`() {
        val server = unaffiliated().apply { searchAlliancesError = ApiError.TooManyRequests(retryAfterSeconds = 30) }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.search("Ferro")

            alliance.assertReads(Strings.allianceSearchIdle())
        }
    }

    @Test
    fun `founding with no signal leaves the fields untouched`() {
        val (saved, server) = founding()
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            alliance.typeAName("Ferro Alto").typeATag("FRA")
            server.offline = true

            alliance.found()

            // Nothing red: the server never answered, so there is nothing to say about the strings.
            alliance.assertReads(Strings.allianceSearchHeld())
        }
    }

    // **A tag the contract refuses offers no control at all**, which is the defect this replaces: the
    // control used to appear the moment both fields held anything, and the tap swallowed the
    // contract's refusal — so *Found it* over `frz` was a button that did nothing when pressed. The
    // rule sits beside the field either way, so the absence is answerable rather than mute.
    @Test
    fun `a tag the contract refuses offers no control and says what it wants`() {
        app(saved = colony(), api = unaffiliated()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("frz")

            alliance.assertCannotFound()
            alliance.assertReads(Strings.allianceFoundTagRule())
            alliance.assertFoundedNothing()
        }
    }

    // Two letters is a tag on the way to being one; three is the shortest the contract takes.
    @Test
    fun `a tag shorter than the contract takes offers no control`() {
        app(saved = colony(), api = unaffiliated()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FR")

            alliance.assertCannotFound()
        }
    }

    // **The bound is a fact the field enforces**, `NameField`'s rule: a fifth character is declined
    // rather than accepted and refused afterwards, so what is on screen is always sendable.
    @Test
    fun `the tag field stops at four characters`() {
        val (saved, server) = founding()
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FRAL").typeATag("T").found()

            alliance.assertFounded(name = "Ferro Alto", tag = "FRAL")
        }
    }

    // The treasury route refusing does not take the roster away with it: the alliance is still a
    // place, and the panel says its pool has not been read.
    @Test
    fun `a treasury the server refused leaves the alliance readable`() {
        val server = enlisted().apply { treasuryError = ApiError.Internal("no treasury today") }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.assertReads(Strings.allianceTreasuryUnread())
            alliance.assertSaysSeats(taken = 1, cap = 12)
        }
    }

    // **The arc a player actually walks, in one composition.** Every test above opens the tab on one
    // face; this one types, is answered, is answered again, and ends up somewhere else — which is
    // the only way the screen has to decide whether to redraw rather than simply being drawn.
    @Test
    fun `the tab follows the account from searching to a seat`() {
        val server = unaffiliated()
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            alliance.assertReads(Strings.allianceSearchIdle())

            // Nothing found, then something.
            alliance.search("Aphelion")
            alliance.assertReads(Strings.allianceSearchEmpty(TextRes("Aphelion")))
            server.allianceSearch = AllianceSearchResponse(ApiVersion.CURRENT, "ferro", listOf(ALLIANCE), null)
            alliance.search("Ferro")

            // Asked, and then let in — the standing the server answers with is what moves the face.
            server.allianceStanding = AllianceStanding.Petitioning(ALLIANCE)
            alliance.requestTheFirstSeat()
            alliance.assertReads(Strings.allianceWaitingBody())

            server.allianceStanding = AllianceStanding.Enlisted(ALLIANCE, AllianceRole.MEMBER)
            server.allianceRoster = ROSTER.copy(pending = null)
            alliance.withdraw()

            // A plain member: a roster, a treasury, and a way out. No pending list at all, because
            // `null` on the wire means *not yours to see*.
            alliance.assertReads(Strings.allianceTreasuryRule())
            alliance.assertReads(Strings.allianceLeaveAction())
        }
    }

    // **A colony that can pay for an alliance**, which `colony()` deliberately cannot: its figures
    // are round so the contribute chips' shares read as arithmetic, and 42,100 metal is nowhere near
    // the 200,000 founding costs. Every test about the founding block takes this one; the treasury
    // tests keep theirs.
    private fun founderColony(): GameSnapshot = colony().let { poor ->
        poor.copy(state = poor.state.copy(resources = Resources.of(metal = 500_000, crystal = 500_000, deuterium = 500_000)))
    }

    private fun colony(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        debugUsed = false,
        // Round figures so the four chips' shares are exact and the assertions above read as the
        // arithmetic rather than as numbers taken off a run.
        state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds()))
            .copy(resources = Resources.of(metal = 42_100, crystal = 9_600, deuterium = 1_200)),
    )

    private fun enlisted(): FakeOltreApi = FakeOltreApi().apply {
        colony = this@AllianceAppBehaviourTest.colony()
        founds = colony
        replays = true
        allianceStanding = AllianceStanding.Enlisted(ALLIANCE, AllianceRole.FOUNDER)
        allianceRoster = ROSTER
        treasury = TREASURY
    }

    // **The server's copy has to be as rich as the saved one**, because the sync on the way in is
    // authoritative: a test that seeded a colony able to pay and left the fake holding the poor one
    // would watch the rail overwrite it a frame later and then wonder where the control went.
    private fun unaffiliated(saved: GameSnapshot = colony()): FakeOltreApi = FakeOltreApi().apply {
        colony = saved
        founds = colony
        replays = true
        allianceStanding = AllianceStanding.Unaffiliated
        allianceSearch = AllianceSearchResponse(ApiVersion.CURRENT, "Aphelion", emptyList(), null)
    }

    // The pair every founding test opens with: a colony that can pay, on the phone and on the server.
    private fun founding(): Pair<GameSnapshot, FakeOltreApi> =
        founderColony().let { rich -> rich to unaffiliated(rich) }

    private companion object {
        // The founding price as the block writes it — `AllianceBalance.FOUNDING_PRICE` through the
        // catalogue, so the assertion is about the figure the server charges rather than a string.
        val PRICE_LINE = Strings.clauses(
            listOf(
                Strings.amountOfResource(Strings.groupedNumber(200_000), ResourceKind.METAL),
                Strings.amountOfResource(Strings.groupedNumber(100_000), ResourceKind.CRYSTAL),
                Strings.amountOfResource(Strings.groupedNumber(50_000), ResourceKind.DEUTERIUM),
            ),
        )

        val ALLIANCE = Alliance(
            id = AllianceId("ferro-alto"),
            name = AllianceName("Ferro Alto"),
            tag = AllianceTag("FRA"),
            level = AllianceLevel(7),
            seats = AllianceSeats(taken = 1, cap = 12),
        )

        val MEMBER = AllianceMember(
            id = AllianceMemberId("seat-2"),
            profile = PlayerProfile(name = CommanderName("Slow Burn"), mark = null),
            role = AllianceRole.MEMBER,
            experience = ExperienceReading.Known(Experience(4_380)),
            lastSyncedAt = TEST_NOW,
        )

        val ROSTER = AllianceRosterResponse(
            apiVersion = ApiVersion.CURRENT,
            members = listOf(
                AllianceMember(
                    id = AllianceMemberId("seat-1"),
                    profile = PlayerProfile(name = CommanderName("Dead Reckoning"), mark = null),
                    role = AllianceRole.FOUNDER,
                    experience = ExperienceReading.Known(Experience(54_300)),
                    lastSyncedAt = TEST_NOW,
                ),
            ),
            pending = listOf(
                dev.fardavide.oltre.protocol.JoinRequest(
                    id = dev.fardavide.oltre.protocol.JoinRequestId("petition-1"),
                    profile = PlayerProfile(name = CommanderName("Aphelion Drift"), mark = null),
                    experience = ExperienceReading.Known(Experience(27_200)),
                    askedAt = TEST_NOW,
                ),
            ),
        )

        val TREASURY = TreasuryResponse(
            apiVersion = ApiVersion.CURRENT,
            pool = Resources.of(metal = 486_300, crystal = 232_900, deuterium = 71_400),
            contributed = 42_000,
            progress = AllianceProgress(level = AllianceLevel(7), earned = 486_300, intoLevel = 62_000, span = 100_000),
            projects = listOf(
                AllianceProjectOffer(
                    project = AllianceProject.CHARTER_EXPANSION,
                    cost = Resources.of(metal = 20_000, crystal = 10_000, deuterium = 5_000),
                    affordable = true,
                    timesBought = 0,
                ),
            ),
        )
    }
}
