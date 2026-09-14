package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
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

    // Not in one: a search and the founding block under it, so the price is known before anything is
    // typed.
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
        val server = unaffiliated()
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FRA").found()

            alliance.assertFounded(name = "Ferro Alto", tag = "FRA")
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
        val server = unaffiliated().apply { createAllianceError = ApiError.AllianceNameTaken }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FRA").found()

            alliance.assertReads(Strings.allianceNameTaken(TextRes("Ferro Alto")))
            alliance.assertCannotFound()
        }
    }

    @Test
    fun `typing again clears the refusal and offers the control back`() {
        val server = unaffiliated().apply { createAllianceError = ApiError.AllianceTagTaken }
        app(saved = colony(), api = server) {
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
        val server = unaffiliated()
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            alliance.typeAName("Ferro Alto").typeATag("FRA")
            server.offline = true

            alliance.found()

            // Nothing red: the server never answered, so there is nothing to say about the strings.
            alliance.assertReads(Strings.allianceSearchHeld())
        }
    }

    // **A name the contract itself refuses never becomes a request.** A tag has to be uppercase
    // ASCII, so `frz` is not a tag and `AllianceTag`'s own guard is what says so.
    @Test
    fun `a tag the contract refuses is never sent`() {
        app(saved = colony(), api = unaffiliated()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("frz").found()

            alliance.assertFoundedNothing()
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

    private fun unaffiliated(): FakeOltreApi = FakeOltreApi().apply {
        colony = this@AllianceAppBehaviourTest.colony()
        founds = colony
        replays = true
        allianceStanding = AllianceStanding.Unaffiliated
        allianceSearch = AllianceSearchResponse(ApiVersion.CURRENT, "Aphelion", emptyList(), null)
    }

    private companion object {
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
