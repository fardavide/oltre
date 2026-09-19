package dev.fardavide.oltre.client

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.net.data.AllianceRoute
import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.AllianceSpeedup
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

            alliance.pickShare(0).contribute()

            // 10% of 42,100 / 9,600 / 1,200, **floored** — which is the whole promise the control
            // makes: the basket the line above it spelled is the one that left.
            alliance.assertContributed(metal = 4_210, crystal = 960, deuterium = 120)
        }
    }

    // **Picking a stop sends nothing**, which is the contract the two-step control is for: the row
    // above decides how much and the one control below it is the only thing that pays anything in.
    // Under the four chips a finger that landed on `50%` had already spent half a colony.
    @Test
    fun `picking a share moves nothing until the control under it is pressed`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.pickShare(2)

            alliance.assertNothingContributed()
            // And the control now states the stop that was picked: half of 42,100 / 9,600 / 1,200.
            alliance.assertWillSend(metal = 21_050, crystal = 4_800, deuterium = 600)
        }
    }

    // **The alliance is named on the control that sends** (Davide, 2026-09-15: *"it is not even clear
    // that you're actually donating your resources to the Lions"*). The button is the last thing read
    // before resources leave a colony for good.
    @Test
    fun `the control that sends names the alliance it sends to`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)

            AllianceRobot(this).assertReads(Strings.allianceContributeAction(TextRes("Ferro Alto")))
        }
    }

    // **The pool moves on the tap rather than on the next launch**, which is what it did not do: the
    // sync answers with a colony and nothing else — the pool is a second route — so the panel went on
    // drawing the figures the tab opened with for the life of the process, and the only way to watch
    // a contribution land was to restart the app.
    @Test
    fun `a contribution lands on the pool without leaving the screen`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.pickShare(0).contribute()

            // 486,300 / 232,900 / 71,400, the richer for the 4,210 / 960 / 120 that just left.
            alliance.assertPoolReads(metal = 490_510, crystal = 233_860, deuterium = 71_520)
            // And this member's own standing with it, priced on the game's 1 : 2 : 3: 42,000 + 6,490.
            alliance.assertReads(Strings.alliancePaidIn(Strings.groupedNumber(48_490)))
        }
    }

    // **`All` asks first, and the three fixed shares do not** — Davide, 2026-09-14. It is the one tap
    // that empties a colony and nothing in this game takes it back out of the pool.
    @Test
    fun `everything you have asks before it goes`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.pickShare(3).contribute()

            alliance.assertReads(Strings.allianceConfirmAllTitle())
            alliance.assertNothingContributed()
        }
    }

    @Test
    fun `confirming it sends the whole stock`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.pickShare(3).contribute().confirmContributingEverything()

            alliance.assertContributed(metal = 42_100, crystal = 9_600, deuterium = 1_200)
        }
    }

    @Test
    fun `keeping it sends nothing and clears the question`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.pickShare(3).contribute().keepIt()

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

            alliance.pickShare(0).contribute()

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

    // **The second row is a live control and it sends its own project** — the dead-control rule, on
    // the one thing `#168` added that a finger can reach. With a catalogue of one, *a project was
    // bought* and *this project was bought* were the same sentence; with two, a row wired to the
    // offer above it would pass the first and fail a player.
    //
    // What it buys is not asserted here on purpose: the boon is a number the server computes and
    // stamps on the next colony read, so the effect is `AllianceTreasuryEndpointsTest`'s to prove and
    // this is the tap's.
    @Test
    fun `the second project in the catalogue sends its own name`() {
        app(saved = colony(), api = enlisted()) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            alliance.assertReads(Strings.allianceProjectLogistics())

            alliance.buyTheSecondProject()

            alliance.assertBoughtProject(AllianceProject.SHARED_LOGISTICS)
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
    // **The hole the design draws rather than hides** — `alliance-sheet.md`'s first open item, and
    // until now no test had ever put it on screen. A founder cannot leave while anybody else is on
    // the roster, and handing an alliance on is an act nobody has designed, so they are offered
    // *nothing*: no Leave, no Disband. That is three states in one composition, each of which was a
    // `when` arm no behaviour test reached —
    //
    //  - a founder with company gets no departure control at all;
    //  - an **admin**'s role is drawn on their roster row, where a plain member's is not;
    //  - a member whose colony predates the experience column reads `LV 0` rather than blank, which
    //    is an honest unknown rather than *this player has done nothing*.
    @Test
    fun `a founder with company is offered no way out, beside an admin and an unread level`() {
        val server = enlisted().apply {
            allianceStanding = AllianceStanding.Enlisted(
                ALLIANCE.copy(seats = AllianceSeats(taken = 3, cap = 12)),
                AllianceRole.FOUNDER,
            )
            allianceRoster = ROSTER.copy(
                members = ROSTER.members + listOf(
                    MEMBER.copy(
                        id = AllianceMemberId("seat-3"),
                        profile = PlayerProfile(name = CommanderName("Ferro Secondo"), mark = null),
                        role = AllianceRole.ADMIN,
                    ),
                    MEMBER.copy(experience = ExperienceReading.Unknown),
                ),
                pending = emptyList(),
            )
        }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.assertRosterNames("Dead Reckoning", "Ferro Secondo", "Slow Burn")
            alliance.assertRosterRowReads(row = 1, text = Strings.allianceRoleAdmin())
            alliance.assertRosterRowReads(row = 2, text = Strings.levelBadge(0))
            alliance.assertNoWayOut()
        }
    }

    // **The alliance as an admin sees it**, which is the third viewer role and the one no test had
    // ever taken. Every enlisted test above views as a founder or as a plain member, so half of
    // `AlliancePowers` — every arm that begins `ADMIN ->` — had never run on a screen.
    //
    // What an admin is, stated as three differences from the founder beside them:
    //
    //  - they **answer petitions**, so the pending section is theirs too;
    //  - they **remove plain members and nobody else** — not the founder, not another admin, which
    //    is the one place `canRemove` reads its target's role rather than its own;
    //  - they **leave** rather than disband, because the alliance is not theirs to end.
    @Test
    fun `an admin answers petitions, commands only plain members, and leaves rather than disbands`() {
        val server = enlisted().apply {
            allianceStanding = AllianceStanding.Enlisted(
                ALLIANCE.copy(seats = AllianceSeats(taken = 3, cap = 12)),
                AllianceRole.ADMIN,
            )
            allianceRoster = ROSTER.copy(
                members = ROSTER.members + listOf(
                    MEMBER.copy(
                        id = AllianceMemberId("seat-2"),
                        profile = PlayerProfile(name = CommanderName("Ferro Secondo"), mark = null),
                        role = AllianceRole.ADMIN,
                    ),
                    MEMBER,
                ),
            )
        }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            // The queue is drawn, because an admin is somebody who can answer it.
            alliance.assertReads(Strings.allianceAccept())
            // The founder's row and the other admin's answer nothing; the plain member's does.
            alliance.assertRowPresses(row = 0, presses = false)
            alliance.assertRowPresses(row = 1, presses = false)
            alliance.assertRowPresses(row = 2, presses = true)
            // And the way out is Leave, never Disband.
            alliance.assertReads(Strings.allianceLeaveAction())
            alliance.assertDoesNotRead(Strings.allianceDisbandAction())
        }
    }

    // **A pending list sent to a plain member is not believed.** On the wire `null` means *not
    // yours to see*, so a member never receives one — which is exactly why the client withholds the
    // section on the **role** rather than on the null. That guard had never been exercised: every
    // member-viewer test is handed `pending = null`, so the safe half short-circuits before the
    // question is asked, and a server that leaked the queue would have drawn it.
    @Test
    fun `a member is not shown a queue they could not answer, even if the server sends one`() {
        val server = enlisted().apply {
            allianceStanding = AllianceStanding.Enlisted(ALLIANCE, AllianceRole.MEMBER)
        }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            // `ROSTER` carries a petition, and this viewer is a plain member.
            alliance.assertDoesNotRead(Strings.alliancePendingLabel())
            alliance.assertDoesNotRead(Strings.allianceAccept())
            // The rest of the alliance is still theirs to read.
            alliance.assertReads(Strings.allianceTreasuryRule())
        }
    }

    // **The face every cold open goes through, and the one nothing had ever drawn.** Until the
    // server answers, the destination is neither seeking nor enlisted — it is a sentence, in words
    // rather than a spinner, because nothing on this screen animates. Ten lines of `Centred` that no
    // behaviour test had rendered, on the state a player meets before any other.
    @Test
    fun `the tab says it is asking until the server answers`() {
        val server = unaffiliated()
        server.holdAlliance(AllianceRoute.STANDING)
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.assertReads(Strings.allianceAsking())
            // Not the search, not the founding block — there is no standing yet to draw either from.
            alliance.assertDoesNotRead(Strings.allianceFoundBody())

            server.answerAlliance(AllianceRoute.STANDING)
            alliance.settle()

            alliance.assertReads(Strings.allianceFoundBody())
        }
    }

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

    // **The bug `#164` names, from the finger that met it.** Every refusal that is not a taken name
    // or tag used to set both field flags to `false` and change nothing: the player pressed *Found
    // it*, the server answered, and the app did not move. This is the case the two tests above did
    // not cover, and it is the one the dead-control rule is about — a control that is not dead in the
    // diff, only in the cases the diff does not reach.
    @Test
    fun `a refusal about neither string is said in the block rather than swallowed`() {
        val saved = founderColony()
        val server = unaffiliated(saved).apply { createAllianceError = ApiError.AllianceFoundingUnaffordable }
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FRA").found()

            alliance.assertReads(Strings.refusedAllianceLead())
            alliance.assertReads(Strings.refusedAllianceShortBody())
        }
    }

    // **What it does to the button, which is the other half of `#164`'s design prompt** — and the
    // answer is the one the contribution refusal already shipped: the control keeps full strength and
    // goes on answering, because a refusal that disabled the thing it is about would replace a silent
    // no-op with an unanswerable one. The typed name and tag are still good and are still there.
    @Test
    fun `the block leaves the fields and the control exactly as they were`() {
        val saved = founderColony()
        val server = unaffiliated(saved).apply { createAllianceError = ApiError.Internal("a bad day") }
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.typeAName("Ferro Alto").typeATag("FRA").found()

            alliance.assertReads(Strings.refusedAllianceServerBody())
            alliance.assertCanFound()
        }
    }

    // **Every refusal the server can send, through the real screen and out the other side.**
    // `AllianceRefusalUiStateTest` proves the mapping; this proves the *wiring* carries all of it —
    // the flag the shell holds, the call site that reads it, the slot on the destination and the
    // block that draws it. With one error scripted per case they were the same test twelve times;
    // as a walk it is one sentence: **no refusal leaves this screen silent, and each says its own
    // thing.**
    //
    // The three cases below it are the ones with a second claim to make — what the block does to
    // the fields, and what takes it down — and they stay named for that reason.
    @Test
    fun `no refusal the server can send leaves the screen silent`() {
        for ((error, body) in EVERY_REFUSAL) {
            val saved = founderColony()
            val server = unaffiliated(saved).apply { createAllianceError = error }
            app(saved = saved, api = server) {
                open(OltreTab.ALLIANCE)
                val alliance = AllianceRobot(this)

                alliance.typeAName("Ferro Alto").typeATag("FRA").found()

                alliance.assertReads(Strings.refusedAllianceLead())
                alliance.assertReads(body)
            }
        }
    }

    // The block goes with the tap that follows it, which is `dispatch`'s own rule: a sentence about
    // a tap the player has moved past is furniture.
    @Test
    fun `asking again takes the block down`() {
        val saved = founderColony()
        val server = unaffiliated(saved).apply { createAllianceError = ApiError.Internal("a bad day") }
        app(saved = saved, api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            alliance.typeAName("Ferro Alto").typeATag("FRA").found()
            alliance.assertReads(Strings.refusedAllianceServerBody())
            server.createAllianceError = null

            alliance.found()

            alliance.assertDoesNotRead(Strings.refusedAllianceServerBody())
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
    fun `a founder kicks a member from the roster`() {
        val server = enlisted().apply { allianceRoster = ROSTER.copy(members = ROSTER.members + MEMBER) }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.kickTheSecondMember()

            alliance.assertRemovedAMember()
        }
    }

    // **The first tap sends nothing.** A kick is the one command whose subject cannot undo it, so it
    // asks again — and the whole point of that step is that stopping there leaves the roster alone.
    @Test
    fun `asking to kick and then keeping them sends nothing`() {
        val server = enlisted().apply { allianceRoster = ROSTER.copy(members = ROSTER.members + MEMBER) }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.openMember(row = 1)
            alliance.assertAsking()
            alliance.askToKick()
            alliance.assertOnTheLastStep()
            alliance.keepThem()

            alliance.assertAsking()
            alliance.assertKeptEverybody()
        }
    }

    // **The control that had no caller.** `canSetRole` and `setMemberRole` shipped with #138 and
    // #141 and nothing on any screen ever reached them, which is the defect Davide found on
    // 2026-09-18: *"the alliance screen lacks the button to promote a user to admin"*.
    @Test
    fun `a founder promotes a member to admin`() {
        val server = enlisted().apply { allianceRoster = ROSTER.copy(members = ROSTER.members + MEMBER) }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.openMember(row = 1)
            alliance.assertRoleCommand(Strings.allianceMemberPromote())
            alliance.setRole()

            alliance.assertPromoted()
        }
    }

    // **The other direction, and the card that says which one this is.** An admin's row draws
    // `ADMIN` under the name and the blue command reads Demote — the same face inverted, which is
    // the only thing that says which way the rank moves.
    @Test
    fun `a founder demotes an admin`() {
        val server = enlisted().apply {
            allianceRoster = ROSTER.copy(members = ROSTER.members + MEMBER.copy(role = AllianceRole.ADMIN))
        }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.openMember(row = 1)
            alliance.assertMemberFaceReads(Strings.allianceRoleAdmin())
            alliance.assertRoleCommand(Strings.allianceMemberDemote())
            alliance.setRole()

            alliance.assertPromoted(AllianceRole.MEMBER)
        }
    }

    // **A command with no signal says why, instead of being a control that does nothing.** Both
    // commands ask the server and neither can be queued — a seat and a rank are the alliance's, not
    // the phone's — so the face states the requirement and dims what acts while the card and the
    // reading stay at full strength. This is the arm the no-dead-control rule is actually about.
    @Test
    fun `a command that cannot reach the server says so rather than doing nothing`() {
        val server = enlisted().apply { allianceRoster = ROSTER.copy(members = ROSTER.members + MEMBER) }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)
            alliance.openMember(row = 1)
            server.offline = true

            alliance.setRole()

            alliance.assertMemberFaceReads(Strings.allianceMemberHeldLead())
            alliance.assertMemberFaceReads(Strings.allianceMemberHeldBody())
            // The reading is not a control, so it is not dimmed and it is still there to read.
            alliance.assertMemberFaceReads(Strings.allianceMemberLastSeen(TextRes("1m")))
        }
    }

    // A member taps a row and nothing happens, which is the design's answer rather than a face made
    // of the sentence explaining its own emptiness: they never saw an arrow, so nothing is absent.
    @Test
    fun `a plain member opens no face on anybody`() {
        val server = enlisted(role = AllianceRole.MEMBER)
            .apply { allianceRoster = ROSTER.copy(members = ROSTER.members + MEMBER, pending = null) }
        app(saved = colony(), api = server) {
            open(OltreTab.ALLIANCE)
            val alliance = AllianceRobot(this)

            alliance.assertRowPresses(row = 0, presses = false)
            alliance.openMember(row = 0)

            alliance.assertNoMemberFace()
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

    // ── What the alliance takes off, end to end ──────────────────────────────────────────────
    //
    // **The one thing no other test in this slice can prove.** The boon is written by the server
    // onto the snapshot, carried on `GameState`, and the *level* that names it comes from a
    // completely different read — the alliance standing. Every unit test either has the state or has
    // the level; only a launch has both, and only a launch proves the shell puts them together.

    @Test
    fun `a colony in an alliance is told once what it takes off, on the screen that builds`() {
        val server = enlistedWithHelp()
        app(saved = server.colony, api = server) {
            open(OltreTab.COLONY)

            // Level 7 on the standing, 14% on the snapshot — the two halves meeting.
            assertReads("−14% · your alliance")
        }
    }

    // Nineteen colonies in twenty, and the assertion is that the screen is exactly the screen it was.
    @Test
    fun `a colony in no alliance is told nothing about one`() {
        app(saved = colony(), api = unaffiliated().apply { colony = this@AllianceAppBehaviourTest.colony() }) {
            open(OltreTab.COLONY)

            assertDoesNotRead("your alliance")
            assertDoesNotRead("−14%")
        }
    }

    // Research carries the same figure joined to its own rule, and the attribution is what drops —
    // the heading and the neighbouring clause already say what the percentage acts on.
    @Test
    fun `the research screen carries the figure beside the rule it already had`() {
        val server = enlistedWithHelp()
        app(saved = server.colony, api = server) {
            open(OltreTab.RESEARCH)

            assertReads("−14%")
            assertReads("one project at a time")
        }
    }

    // A server-written boon reaching a colony, which is what a sync actually delivers.
    private fun enlistedWithHelp(): FakeOltreApi = enlisted().apply {
        val helped = colony().let { it.copy(state = it.state.copy(allianceSpeedup = AllianceSpeedup(14))) }
        colony = helped
        founds = helped
    }

    private fun colony(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        debugUsed = false,
        // Round figures so the four chips' shares are exact and the assertions above read as the
        // arithmetic rather than as numbers taken off a run.
        state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds()))
            .copy(resources = Resources.of(metal = 42_100, crystal = 9_600, deuterium = 1_200)),
    )

    private fun enlisted(role: AllianceRole = AllianceRole.FOUNDER): FakeOltreApi = FakeOltreApi().apply {
        colony = this@AllianceAppBehaviourTest.colony()
        founds = colony
        replays = true
        allianceStanding = AllianceStanding.Enlisted(ALLIANCE, role)
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

        // **Every member of `ApiError` a refusal can carry, paired with the sentence it earns.**
        // Written out rather than derived: `ApiError` is sealed and has no `entries`, and a list
        // that quietly missed one would let exactly the refusal nobody thought about go back to
        // saying nothing — which is the bug `#164` is.
        //
        // The two that are absent are absent on purpose. `AllianceNameTaken` and `AllianceTagTaken`
        // draw no block at all, because they have a field to land under; the two tests above this
        // one are theirs.
        val EVERY_REFUSAL: List<Pair<ApiError, TextRes>> = listOf(
            ApiError.AllianceFoundingUnaffordable to Strings.refusedAllianceShortBody(),
            ApiError.AllianceTreasuryShort to Strings.refusedAllianceShortBody(),
            ApiError.AlreadyInAnAlliance to Strings.refusedAllianceStandingBody(),
            ApiError.NotInAnAlliance to Strings.refusedAllianceStandingBody(),
            ApiError.AllianceRoleTooLow to Strings.refusedAllianceStandingBody(),
            ApiError.AllianceFull to Strings.refusedAllianceStandingBody(),
            ApiError.NoSuchAlliance to Strings.refusedAllianceStandingBody(),
            ApiError.StaleAlliance to Strings.refusedAllianceStandingBody(),
            ApiError.UnsupportedApiVersion(ApiVersion.CURRENT, ApiVersion.CURRENT) to
                Strings.refusedAllianceOutdatedBody(),
            ApiError.Unauthenticated to Strings.refusedAllianceServerBody(),
            ApiError.SessionExpired to Strings.refusedAllianceServerBody(),
            ApiError.NoColony to Strings.refusedAllianceServerBody(),
            ApiError.StaleColony to Strings.refusedAllianceServerBody(),
            ApiError.TooManyRequests(retryAfterSeconds = 30) to Strings.refusedAllianceServerBody(),
            ApiError.Malformed(detail = "that did not parse") to Strings.refusedAllianceServerBody(),
            ApiError.Internal(detail = "a bad day") to Strings.refusedAllianceServerBody(),
        )

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
                AllianceProjectOffer(
                    project = AllianceProject.SHARED_LOGISTICS,
                    cost = Resources.of(metal = 40_000, crystal = 20_000, deuterium = 10_000),
                    affordable = true,
                    timesBought = 0,
                ),
            ),
        )
    }
}
