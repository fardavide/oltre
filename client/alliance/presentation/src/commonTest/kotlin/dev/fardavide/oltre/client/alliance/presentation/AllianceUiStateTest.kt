package dev.fardavide.oltre.client.alliance.presentation

import dev.fardavide.oltre.client.alliance.domain.AllianceRosterReading
import dev.fardavide.oltre.client.alliance.domain.AllianceState
import dev.fardavide.oltre.client.alliance.ui.AllianceUiState
import dev.fardavide.oltre.client.alliance.ui.ContributeActionUiState
import dev.fardavide.oltre.client.alliance.ui.FoundingUiState
import dev.fardavide.oltre.client.alliance.ui.SearchResultsUiState
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.core.ExperienceBalance
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
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.JoinRequest
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.TreasuryResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

// **Which of four faces a standing is, and which controls each one offers.** The screen renders what
// it is handed, so every rule about that is here — and every one of them is a plain function over
// data, which is what makes this a unit test rather than a behaviour one.
class AllianceUiStateTest {

    // **Held outranks everything and is checked first.** With no network the server never answered,
    // so there is no standing to draw stale and nothing on the face is red.
    @Test
    fun `no network is the whole face rather than a dimming of another one`() {
        val state = face(standing = enlisted(), reachable = false)

        assertEquals(AllianceUiState.Held, state)
    }

    @Test
    fun `an unread standing is asking rather than an empty search`() {
        assertEquals(AllianceUiState.Asking, face(standing = AllianceState.Unread))
    }

    @Test
    fun `in no alliance is a search and a way to found one`() {
        val state = assertIs<AllianceUiState.Seeking>(face(standing = AllianceState.Unaffiliated))

        assertIs<SearchResultsUiState.Idle>(state.search.state)
        assertEquals(Strings.allianceFoundBody(), state.founding.body)
    }

    @Test
    fun `waiting names the alliance and says nothing about time`() {
        val state = assertIs<AllianceUiState.Waiting>(
            face(standing = AllianceState.Petitioning(ALLIANCE)),
        )

        assertEquals(TextRes("Ferro Alto"), state.name)
        assertEquals(Strings.allianceWaitingBody(), state.body)
    }

    // ── The roster ───────────────────────────────────────────────────────────────────────────

    // **A plain member draws no role at all**, which is correct for nineteen rows in twenty. The role
    // rides the caption ramp rather than a second badge.
    @Test
    fun `only a founder and an admin carry a role`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted()))

        assertEquals(
            listOf(Strings.allianceRoleFounder(), Strings.allianceRoleAdmin(), null),
            state.roster.map { it.role },
        )
    }

    // **This build's ladder, from raw experience** — never a level the server computed, or a roster
    // could contradict the strip on the same screen.
    @Test
    fun `a member's badge is computed from their earned points`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted()))

        assertEquals(
            Strings.levelBadge(ExperienceBalance.levelFor(Experience(54_300)).value),
            state.roster.first().level,
        )
    }

    // A colony that has not been written since the column existed. An honest unknown rather than a
    // number nobody earned.
    @Test
    fun `an unknown reading still draws a badge`() {
        val unknown = ROSTER.copy(
            members = listOf(FOUNDER.copy(experience = ExperienceReading.Unknown)),
            pending = null,
        )
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(roster = unknown)))

        assertEquals(Strings.levelBadge(0), state.roster.single().level)
    }

    @Test
    fun `a player with no chosen name is drawn as the default`() {
        val anonymous = ROSTER.copy(
            members = listOf(FOUNDER.copy(profile = PlayerProfile(name = null, mark = null))),
            pending = null,
        )
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(roster = anonymous)))

        assertEquals(Strings.playerDefaultName(), state.roster.single().name)
    }

    // **The arrow is the only claim this roster makes, and it is conditional.** A founder sees it on
    // every row but their own; the fixture roster is founder, admin, member.
    @Test
    fun `a founder's rows press on everybody but themselves`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted()))

        assertEquals(listOf(false, true, true), state.roster.map { it.pressable })
    }

    @Test
    fun `an admin's rows press only on plain members`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(role = AllianceRole.ADMIN)))

        assertEquals(listOf(false, false, true), state.roster.map { it.pressable })
    }

    // No arrows anywhere, so the roster is plainly a readout and there is no absence to explain.
    @Test
    fun `a plain member's roster presses nowhere`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(role = AllianceRole.MEMBER)))

        assertTrue(state.roster.none { it.pressable })
    }

    // ── The pending list ─────────────────────────────────────────────────────────────────────

    // **Null means *not yours to see* and an empty list means *nobody is waiting*** — the wire's own
    // distinction, and two different screens.
    @Test
    fun `a plain member is shown no pending list at all`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(role = AllianceRole.MEMBER)))

        assertNull(state.pending)
    }

    @Test
    fun `a founder is shown the petitions and who they are from`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted()))

        assertEquals(1, state.pending?.rows?.size)
        assertEquals(TextRes("Aphelion Drift"), state.pending?.rows?.single()?.name)
    }

    // ── The treasury ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the pool is drawn as three rows in the rail's own order`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted()))

        assertEquals(3, state.treasury.pool.size)
        assertEquals(Strings.resourceName(dev.fardavide.oltre.core.ResourceKind.METAL), state.treasury.pool[0].name)
    }

    // **A pool the route has not answered for leaves a line rather than the whole face waiting**, and
    // the ladder does not press: there is nothing to pay into yet. The control under it says nothing
    // at all, because the line above is already saying it and one card saying it twice is furniture.
    @Test
    fun `an unread treasury says so once and offers no stop that presses`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(), treasury = null))

        assertEquals(Strings.allianceTreasuryUnread(), state.treasury.unread)
        assertTrue(state.treasury.contribute.shares.none { it.enabled })
        assertEquals(ContributeActionUiState.Unread, state.treasury.contribute.action)
        assertTrue(state.treasury.projects.isEmpty())
    }

    @Test
    fun `a treasury that answered has no unread line`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted()))

        assertNull(state.treasury.unread)
    }

    // **Read against the pool rather than against personal stock**, and the comparison is not the
    // obvious one: a pool rich in metal and empty of deuterium does not cover a basket of three.
    @Test
    fun `a project the pool cannot cover offers no control`() {
        val short = TREASURY.copy(
            projects = listOf(OFFER.copy(affordable = false)),
        )
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(), treasury = short))

        assertTrue(!state.treasury.projects.single().buyable)
        assertEquals(Strings.allianceProjectShort(), state.treasury.projects.single().shortLine)
    }

    @Test
    fun `a project bought before carries one tracked word and one bought never does not`() {
        val twice = TREASURY.copy(projects = listOf(OFFER.copy(timesBought = 2)))

        val first = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted()))
        val again = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(), treasury = twice))

        assertNull(first.treasury.projects.single().bought)
        assertEquals(Strings.allianceProjectBought(2), again.treasury.projects.single().bought)
    }

    @Test
    fun `the gauge is the share of this level rather than of the whole climb`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted()))

        assertEquals(62, state.header.progress)
    }

    @Test
    fun `an unread treasury draws an empty track rather than a guess`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(), treasury = null))

        assertEquals(0, state.header.progress)
    }

    // ── Leaving ──────────────────────────────────────────────────────────────────────────────

    // **The founder cannot leave while anybody else is on the roster, and the face draws that hole
    // rather than hiding it.** Handing an alliance on is an act nobody has designed.
    @Test
    fun `a founder with company is offered no way out`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted()))

        assertNull(state.departure)
    }

    @Test
    fun `a founder alone may disband`() {
        val alone = ALLIANCE.copy(seats = AllianceSeats(taken = 1, cap = 12))
        val state = assertIs<AllianceUiState.Enlisted>(
            face(standing = enlisted(alliance = alone, roster = ROSTER.copy(members = listOf(FOUNDER)))),
        )

        assertEquals(Strings.allianceDisbandAction(), state.departure?.action)
        assertTrue(state.departure?.disbands == true)
    }

    @Test
    fun `anybody who is not the founder may leave`() {
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(role = AllianceRole.MEMBER)))

        assertEquals(Strings.allianceLeaveAction(), state.departure?.action)
        assertTrue(state.departure?.disbands == false)
    }

    // ── Searching ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nothing typed states what a search is for`() {
        val search = searchUiState(query = "", answer = null, asking = false, reachable = true)

        assertEquals(Strings.allianceSearchIdle(), assertIs<SearchResultsUiState.Idle>(search.state).line)
    }

    @Test
    fun `waiting on the server reads as a sentence rather than a spinner`() {
        val search = searchUiState(query = "Ferro", answer = null, asking = true, reachable = true)

        assertEquals(Strings.allianceAsking(), assertIs<SearchResultsUiState.Asking>(search.state).line)
    }

    // Naming the string is what tells a player their spelling was read.
    @Test
    fun `finding nothing names the string in full`() {
        val empty = AllianceSearchResponse(ApiVersion.CURRENT, "Aphelion", emptyList(), null)
        val search = searchUiState(query = "Aphelion", answer = empty, asking = false, reachable = true)

        assertEquals(
            Strings.allianceSearchEmpty(TextRes("Aphelion")),
            assertIs<SearchResultsUiState.Empty>(search.state).line,
        )
    }

    // **A search the server refused is not a search that found nothing.** Nothing came back at all,
    // so the face goes back to saying what a search is *for* rather than naming a string as absent
    // when nobody ever looked for it.
    @Test
    fun `a query the server never answered reads as idle rather than empty`() {
        val search = searchUiState(query = "Ferro", answer = null, asking = false, reachable = true)

        assertEquals(Strings.allianceSearchIdle(), assertIs<SearchResultsUiState.Idle>(search.state).line)
    }

    // **Held is told apart from empty in words alone** — held dims what acts, never what informs.
    @Test
    fun `a search with no network says why rather than reading as empty`() {
        val search = searchUiState(query = "Ferro", answer = null, asking = false, reachable = false)

        assertEquals(Strings.allianceSearchHeld(), assertIs<SearchResultsUiState.Held>(search.state).line)
    }

    @Test
    fun `a full alliance is still worth reading and offers no control`() {
        val full = ALLIANCE.copy(seats = AllianceSeats(taken = 8, cap = 8))
        val answer = AllianceSearchResponse(ApiVersion.CURRENT, "Ferro", listOf(ALLIANCE, full), null)

        val search = searchUiState(query = "Ferro", answer = answer, asking = false, reachable = true)

        assertEquals(
            listOf(true, false),
            assertIs<SearchResultsUiState.Results>(search.state).rows.map { it.joinable },
        )
    }

    // ── Founding ─────────────────────────────────────────────────────────────────────────────
    //
    // **The colony can pay unless a test says otherwise**, because most of these are about names,
    // tags and refusals rather than about money — `founding` below defaults to a stock that covers
    // the price, and the four tests that care pass their own.

    @Test
    fun `nothing typed commits nothing`() {
        assertTrue(!founding(name = "", tag = "").committable)
        assertTrue(!founding(name = "Ferro", tag = "").committable)
    }

    @Test
    fun `a name and a tag commit`() {
        assertTrue(founding(name = "Ferro", tag = "FRA").committable)
    }

    // **The defect this pair exists for.** `committable` used to mean *both fields hold something*,
    // while the tap constructed an `AllianceTag` and swallowed the refusal — so a tag the contract
    // cannot accept drew a control that did nothing at all when pressed. The control is what has to
    // know, which means `committable` runs the contract's own check.
    @Test
    fun `a tag the contract refuses commits nothing`() {
        assertTrue(!founding(name = "Ferro", tag = "fra").committable)
        assertTrue(!founding(name = "Ferro", tag = "FR").committable)
        assertTrue(!founding(name = "Ferro", tag = "FR A").committable)
    }

    // The trim is the client's, per `AllianceName`'s own division, so a name typed with a trailing
    // space is a name rather than a refusal the player cannot see.
    @Test
    fun `a name the player has not trimmed still commits`() {
        assertTrue(founding(name = " Ferro ", tag = "FRA").committable)
    }

    // **Absent is only answerable if something says why**, so the rule rides the tag's own label
    // rather than appearing after a tap that never happens.
    @Test
    fun `the tag field states the shape it wants`() {
        assertEquals(Strings.allianceFoundTagRule(), founding(name = "", tag = "").tagRule)
    }

    // ── The price ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the block states what founding costs`() {
        val state = founding(name = "", tag = "")

        assertEquals(Strings.allianceFoundPrice(PRICE.drawn()), state.cost)
        assertTrue(state.affordable)
    }

    // **A colony short of any one resource cannot found**, and the control goes rather than greying.
    @Test
    fun `a colony short of the price is told so and offered no control`() {
        val poor = Resources.of(metal = 10_000_000, crystal = 10_000_000, deuterium = 49_999)

        val state = founding(name = "Ferro", tag = "FRA", colony = poor)

        assertTrue(!state.affordable)
        assertTrue(!state.committable)
        assertEquals(Strings.allianceFoundShort(), state.shortLine)
    }

    @Test
    fun `a colony holding exactly the price can found`() {
        assertTrue(founding(name = "Ferro", tag = "FRA", colony = PRICE).committable)
    }

    // **An unread price is drawn as unread rather than guessed at**, and it withholds the control:
    // the balance is the server's, and a figure invented here would be one nothing charges.
    @Test
    fun `a price nobody has read yet says so and commits nothing`() {
        val state = founding(name = "Ferro", tag = "FRA", price = null)

        assertEquals(Strings.allianceFoundPriceUnread(), state.cost)
        assertTrue(!state.affordable)
        assertTrue(!state.committable)
    }

    // **Both fields can be refused in the same answer**, because one commit sends both — and the
    // control is absent while a refusal stands rather than greyed.
    @Test
    fun `a refusal on either field names the string and withdraws the control`() {
        val refused = founding(name = "Ferro", tag = "FRA", nameTaken = true, tagTaken = true)

        assertEquals(Strings.allianceNameTaken(TextRes("Ferro")), refused.nameRefusal)
        assertEquals(Strings.allianceTagTaken(TextRes("FRA")), refused.tagRefusal)
        assertTrue(!refused.committable)
    }

    @Test
    fun `the two-step question names what it cannot undo`() {
        val confirm = contributeConfirmUiState()

        assertEquals(Strings.allianceConfirmAllTitle(), confirm.title)
        assertEquals(Strings.allianceConfirmAllKeep(), confirm.keep)
    }

    // **An alliance whose roster has not arrived is still an alliance.** The head is on the standing
    // and is always known, so the face draws rather than waiting — and a founder is shown no pending
    // list, because there is no answer about one yet.
    @Test
    fun `an unread roster draws the head and no rows`() {
        val unread = AllianceState.Enlisted(ALLIANCE, AllianceRole.FOUNDER, AllianceRosterReading.Unread)

        val state = assertIs<AllianceUiState.Enlisted>(face(standing = unread))

        assertEquals(emptyList(), state.roster)
        assertNull(state.pending)
        assertEquals(Strings.allianceSeatsLine(3, 12), state.header.seats)
    }

    // **The basket reads like every other cost in the app: a name beside each figure, and the empty
    // ones dropped.** The four-chip row could not do this — at ~88dp a stop there was room for digits
    // and nothing else, so the three numbers were positional and a zero had to be drawn to keep the
    // remaining figure under the right name. Full width, the names travel with the numbers and a
    // *"0 Metal"* nobody needs to read goes away.
    @Test
    fun `the basket names its resources and drops the ones that are nothing`() {
        val deuteriumOnly = ladder(Resources.of(deuterium = 500)).offered()

        assertEquals(
            Strings.clauses(listOf(Strings.amountOfResource(Strings.groupedNumber(50), ResourceKind.DEUTERIUM))),
            deuteriumOnly.basket,
        )
    }

    @Test
    fun `a project priced in one resource says so and no more`() {
        val single = TREASURY.copy(
            projects = listOf(OFFER.copy(cost = Resources.of(metal = 20_000))),
        )
        val state = assertIs<AllianceUiState.Enlisted>(face(standing = enlisted(), treasury = single))

        assertEquals(
            Strings.clauses(listOf(Strings.amountOfResource(Strings.groupedNumber(20_000), ResourceKind.METAL))),
            state.treasury.projects.single().cost,
        )
    }

    // ── The harness ──────────────────────────────────────────────────────────────────────────

    // Defaults to a price that has been read and a colony that can cover it, so a test about the
    // fields is not also a test about money. The four that are about money say so.
    private fun founding(
        name: String,
        tag: String,
        nameTaken: Boolean = false,
        tagTaken: Boolean = false,
        price: Resources? = PRICE,
        colony: Resources = RICH,
    ): FoundingUiState = foundingUiState(
        name = name,
        tag = tag,
        nameTaken = nameTaken,
        tagTaken = tagTaken,
        price = price,
        colony = colony,
    )

    // The cost line as the block draws it — resource names beside their figures, which is how every
    // other cost in the app reads and the opposite of a contribute chip's positional three.
    private fun Resources.drawn(): TextRes = Strings.clauses(
        ResourceKind.entries
            .filter { kind -> amountOf(kind) > 0 }
            .map { kind -> Strings.amountOfResource(Strings.groupedNumber(amountOf(kind)), kind) },
    )

    private fun Resources.amountOf(kind: ResourceKind): Long = when (kind) {
        ResourceKind.METAL -> metal
        ResourceKind.CRYSTAL -> crystal
        ResourceKind.DEUTERIUM -> deuterium
    }

    private fun face(
        standing: AllianceState,
        reachable: Boolean = true,
        treasury: TreasuryResponse? = TREASURY,
    ): AllianceUiState = allianceUiState(
        standing = standing,
        reachable = reachable,
        search = searchUiState(query = "", answer = null, asking = false, reachable = reachable),
        founding = founding(name = "", tag = ""),
        colony = Resources.of(metal = 42_100, crystal = 9_600, deuterium = 1_200),
        treasury = treasury,
        picked = null,
    )

    private fun enlisted(
        alliance: Alliance = ALLIANCE,
        role: AllianceRole = AllianceRole.FOUNDER,
        roster: AllianceRosterResponse = ROSTER,
    ): AllianceState.Enlisted = AllianceState.Enlisted(alliance, role, AllianceRosterReading.Read(roster))

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-14T09:00:00Z")

        val ALLIANCE = Alliance(
            id = AllianceId("ferro-alto"),
            name = AllianceName("Ferro Alto"),
            tag = AllianceTag("FRA"),
            level = AllianceLevel(7),
            seats = AllianceSeats(taken = 3, cap = 12),
        )

        val FOUNDER = AllianceMember(
            id = AllianceMemberId("seat-1"),
            profile = PlayerProfile(name = CommanderName("Dead Reckoning"), mark = null),
            role = AllianceRole.FOUNDER,
            experience = ExperienceReading.Known(Experience(54_300)),
            lastSyncedAt = NOW,
        )

        val ROSTER = AllianceRosterResponse(
            apiVersion = ApiVersion.CURRENT,
            members = listOf(
                FOUNDER,
                FOUNDER.copy(
                    id = AllianceMemberId("seat-2"),
                    profile = PlayerProfile(name = CommanderName("Slow Burn"), mark = null),
                    role = AllianceRole.ADMIN,
                ),
                FOUNDER.copy(
                    id = AllianceMemberId("seat-3"),
                    profile = PlayerProfile(name = CommanderName("Hard Vacuum"), mark = null),
                    role = AllianceRole.MEMBER,
                ),
            ),
            pending = listOf(
                JoinRequest(
                    id = JoinRequestId("petition-1"),
                    profile = PlayerProfile(name = CommanderName("Aphelion Drift"), mark = null),
                    experience = ExperienceReading.Known(Experience(27_200)),
                    askedAt = NOW,
                ),
            ),
        )

        // The real founding price — `AllianceBalance.FOUNDING_PRICE`, Davide's on 2026-09-13. A
        // tidier figure here would let the block pass while drawing a number no server sends.
        val PRICE = Resources.of(metal = 200_000, crystal = 100_000, deuterium = 50_000)

        val RICH = Resources.of(metal = 500_000, crystal = 500_000, deuterium = 500_000)

        val OFFER = AllianceProjectOffer(
            project = AllianceProject.CHARTER_EXPANSION,
            cost = Resources.of(metal = 20_000, crystal = 10_000, deuterium = 5_000),
            affordable = true,
            timesBought = 0,
        )

        val TREASURY = TreasuryResponse(
            apiVersion = ApiVersion.CURRENT,
            pool = Resources.of(metal = 486_300, crystal = 232_900, deuterium = 71_400),
            contributed = 42_000,
            progress = AllianceProgress(
                level = AllianceLevel(7),
                earned = 486_300,
                intoLevel = 62_000,
                span = 100_000,
            ),
            projects = listOf(OFFER),
        )
    }
}
