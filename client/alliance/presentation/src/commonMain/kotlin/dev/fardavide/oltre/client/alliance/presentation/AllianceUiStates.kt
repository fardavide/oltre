package dev.fardavide.oltre.client.alliance.presentation

import dev.fardavide.oltre.client.alliance.domain.AllianceRosterReading
import dev.fardavide.oltre.client.alliance.domain.AllianceState
import dev.fardavide.oltre.client.alliance.domain.canAnswerJoinRequests
import dev.fardavide.oltre.client.alliance.domain.canDisband
import dev.fardavide.oltre.client.alliance.domain.canLeave
import dev.fardavide.oltre.client.alliance.domain.canCommand
import dev.fardavide.oltre.client.alliance.ui.AllianceHeadUiState
import dev.fardavide.oltre.client.alliance.ui.AllianceUiState
import dev.fardavide.oltre.client.alliance.ui.ContributeActionUiState
import dev.fardavide.oltre.client.alliance.ui.ContributeConfirmUiState
import dev.fardavide.oltre.client.alliance.ui.ContributeShare
import dev.fardavide.oltre.client.alliance.ui.ContributeShareUiState
import dev.fardavide.oltre.client.alliance.ui.ContributeUiState
import dev.fardavide.oltre.client.alliance.ui.DepartureUiState
import dev.fardavide.oltre.client.alliance.ui.FoundingUiState
import dev.fardavide.oltre.client.alliance.ui.PendingRowUiState
import dev.fardavide.oltre.client.alliance.ui.PendingUiState
import dev.fardavide.oltre.client.alliance.ui.PoolRowUiState
import dev.fardavide.oltre.client.alliance.ui.ProjectRowUiState
import dev.fardavide.oltre.client.alliance.ui.RosterRowUiState
import dev.fardavide.oltre.client.alliance.ui.SearchResultsUiState
import dev.fardavide.oltre.client.alliance.ui.SearchRowUiState
import dev.fardavide.oltre.client.alliance.ui.SearchUiState
import dev.fardavide.oltre.client.alliance.ui.TreasuryUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.ExperienceBalance
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceMember
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSearchResponse
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ExperienceReading
import dev.fardavide.oltre.protocol.JoinRequest
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.TreasuryResponse

// **Where the alliance's four faces are chosen, and the only place they are.** The screen renders
// what it is handed; every rule about *which* face, which control and which sentence is here.

// What the whole destination is, given what the gateway last read and what the player has typed.
//
// `search` and `founding` are the screen's own state rather than the gateway's, which is why they
// arrive as arguments: a typed query is not a fact about the account and survives no round trip.
fun allianceUiState(
    standing: AllianceState,
    reachable: Boolean,
    search: SearchUiState,
    founding: FoundingUiState,
    colony: Resources,
    // Null is *not read yet*, which the treasury panel says in a line of its own rather than the
    // whole face waiting on it — see `AllianceGateway.treasury` for why the two reads are separate.
    treasury: TreasuryResponse?,
    // Which stop of the contribution ladder the player is on. Screen state like `search` and
    // `founding` above, and for the same reason: it survives no round trip and is nobody's business
    // but this tab's. Null is *they have not picked*, which `contributeLadder` resolves.
    picked: ContributeShare?,
): AllianceUiState {
    // **Held outranks everything and is checked first.** With no network the server never answered,
    // so nothing on the face is red and nothing is stale — there is simply no alliance state to draw.
    if (!reachable) return AllianceUiState.Held
    // **A `when` over the sealed type with no `else`**, so a fifth standing cannot be added without
    // somebody deciding what it looks like. The first cut was a `when {}` of `is` guards with an
    // `else` under it, which compiles, reads the same, and leaves an arm nothing can ever take.
    return when (standing) {
        AllianceState.Unread -> AllianceUiState.Asking
        AllianceState.Unaffiliated -> AllianceUiState.Seeking(search, founding)
        is AllianceState.Petitioning -> AllianceUiState.Waiting(
            name = TextRes(standing.alliance.name.value),
            tag = TextRes(standing.alliance.tag.value),
            body = Strings.allianceWaitingBody(),
            withdraw = Strings.allianceWithdraw(),
        )
        is AllianceState.Enlisted -> standing.face(colony, treasury, picked)
    }
}

private fun AllianceState.Enlisted.face(
    colony: Resources,
    treasury: TreasuryResponse?,
    picked: ContributeShare?,
): AllianceUiState.Enlisted {
    val members = (roster as? AllianceRosterReading.Read)?.value
    return AllianceUiState.Enlisted(
        header = alliance.head(treasury),
        // **Null for a plain member and a list for a founder**, which is the wire's own distinction
        // rather than one invented here: a member is not shown an empty queue they could not answer.
        pending = members?.pending?.takeIf { role.canAnswerJoinRequests() }?.let { pending ->
            PendingUiState(
                label = Strings.alliancePendingLabel(),
                rows = pending.map { it.row() },
                empty = Strings.alliancePendingEmpty(),
            )
        },
        roster = members?.members.orEmpty().map { it.row(viewer = role) },
        treasury = treasuryFace(treasury, colony, TextRes(alliance.name.value), picked),
        departure = departure(role, seats = alliance.seats.taken),
    )
}

private fun Alliance.head(treasury: TreasuryResponse?): AllianceHeadUiState = AllianceHeadUiState(
    name = TextRes(name.value),
    tag = TextRes(tag.value),
    level = Strings.allianceLevelBadge(level.value),
    seats = Strings.allianceSeatsLine(seats.taken, seats.cap),
    // Zero when the treasury has not been read, which draws an empty track rather than a guess. The
    // level beside it is on the alliance itself and is always known.
    //
    // **One safe call, not two.** `TreasuryResponse.progress` is not nullable, so `treasury
    // ?.progress?.let` asked a question with one possible answer — a branch nothing could ever take.
    progress = if (treasury == null) 0 else (treasury.progress.intoLevel * 100 / treasury.progress.span).toInt(),
)

private fun JoinRequest.row(): PendingRowUiState = PendingRowUiState(
    id = id,
    name = profile.drawnName(),
    level = experience.badge(),
    accept = Strings.allianceAccept(),
    decline = Strings.allianceDecline(),
)

private fun AllianceMember.row(viewer: AllianceRole): RosterRowUiState = RosterRowUiState(
    id = id,
    name = profile.drawnName(),
    level = experience.badge(),
    role = when (role) {
        AllianceRole.FOUNDER -> Strings.allianceRoleFounder()
        AllianceRole.ADMIN -> Strings.allianceRoleAdmin()
        // **A plain member draws nothing at all**, which is correct for nineteen rows in twenty.
        AllianceRole.MEMBER -> null
    },
    mark = profile.mark,
    // **The same expression as the face behind it**, which is the point: `canRemove` alone used to
    // decide whether the row carried a control, and it was the wrong question — a founder may also
    // *promote* a member, and nothing on this screen ever asked.
    pressable = viewer.canCommand(role),
)

// **The viewer's own level is computed from raw experience with this build's ladder**, never sent
// as a number by the server — `AllianceRoster.kt` argues it: a roster where one row's badge came
// from another build's curve could contradict the strip on the same screen.
internal fun ExperienceReading.badge(): TextRes = when (this) {
    is ExperienceReading.Known -> Strings.levelBadge(ExperienceBalance.levelFor(earned).value)
    // A member whose colony has not been written since the column existed. An honest unknown rather
    // than a zero, which would read as *this player has done nothing*.
    ExperienceReading.Unknown -> Strings.levelBadge(0)
}

internal fun PlayerProfile.drawnName(): TextRes {
    val chosen = name ?: return Strings.playerDefaultName()
    return TextRes(chosen.value)
}

private fun treasuryFace(
    treasury: TreasuryResponse?,
    colony: Resources,
    alliance: TextRes,
    picked: ContributeShare?,
): TreasuryUiState = TreasuryUiState(
    label = Strings.allianceTreasuryLabel(),
    rule = Strings.allianceTreasuryRule(),
    // **One safe call each, on the one thing that is actually nullable.** `pool`, `contributed` and
    // `projects` are all non-null on a `TreasuryResponse`, so the second `?.` in each of these asked
    // a question with one answer and left a branch nothing could take.
    pool = treasury?.pool.orEmptyRows(),
    contributed = Strings.alliancePaidIn(
        if (treasury == null) TextRes("0") else treasury.contributed.groupedByThousands(),
    ),
    contribute = contributeLadder(colony, alliance, picked, live = treasury != null),
    projectsLabel = Strings.allianceProjectsLabel(),
    projects = treasury?.projects.orEmpty().map { offer ->
        ProjectRowUiState(
            project = offer.project,
            name = offer.project.title(),
            effect = offer.project.effect(),
            cost = offer.cost.line(),
            bought = offer.timesBought.takeIf { it > 0 }?.let { Strings.allianceProjectBought(it) },
            action = Strings.allianceProjectBuy(),
            affordable = offer.affordable,
            shortLine = Strings.allianceProjectShort(),
            buyable = offer.affordable,
        )
    },
    projectsEmpty = Strings.allianceProjectsEmpty(),
    unread = if (treasury == null) Strings.allianceTreasuryUnread() else null,
)

private fun Resources?.orEmptyRows(): List<PoolRowUiState> = this?.rows().orEmpty()

private fun Resources.rows(): List<PoolRowUiState> = ResourceKind.entries.map { kind ->
    PoolRowUiState(name = Strings.resourceName(kind), amount = amountOf(kind).groupedByThousands())
}

private fun Resources.line(): TextRes = Strings.clauses(
    ResourceKind.entries
        .filter { amountOf(it) > 0 }
        .map { Strings.amountOfResource(amountOf(it).groupedByThousands(), it) },
)

private fun Resources.amountOf(kind: ResourceKind): Long = when (kind) {
    ResourceKind.METAL -> metal
    ResourceKind.CRYSTAL -> crystal
    ResourceKind.DEUTERIUM -> deuterium
}

// **The ladder, and the whole of what the control under it promises.** Each stop takes its share of
// the colony's own stock and **floors** it, so the basket the line spells is exactly what leaves — a
// control that sends more than it states is the worst kind of wrong on a tap nothing can undo.
//
// **`picked` is null when the player has not chosen, and the ladder opens on a tenth** — the
// gentlest stop on the one control in this app that cannot be taken back. It is never moved off what
// the player picked, not even when their stop floors to nothing and a later one would send
// something: on a control this size, a selection that quietly slid one along is worse than an absent
// button with a sentence under it.
internal fun contributeLadder(
    colony: Resources,
    alliance: TextRes,
    picked: ContributeShare?,
    live: Boolean,
): ContributeUiState {
    val selected = picked ?: ContributeShare.A_TENTH
    val basket = colony.share(selected)
    return ContributeUiState(
        shares = ContributeShare.entries.map { share ->
            ContributeShareUiState(
                share = share,
                label = share.label(),
                selected = share == selected,
                // **A stop that would send nothing does not press**, which is `core`'s
                // `NothingOffered` refusal arriving one layer earlier: a control that appears to work
                // and changes nothing is the dead control in its purest form, and a colony at ten
                // metal floors three of these four to zero.
                enabled = live && colony.share(share) != Resources.of(),
            )
        },
        action = when {
            !live -> ContributeActionUiState.Unread
            basket != Resources.of() ->
                basket.offer(alliance, confirms = selected == ContributeShare.EVERYTHING)

            // **Two sentences, because they are two different facts** and each is exactly true where
            // it stands: a colony with nothing in it has nothing to give at any stop, and a colony
            // with a little in it has stops that floor to zero while `All` still works. One sentence
            // covering both would have to be vague about which.
            colony == Resources.of() -> ContributeActionUiState.Short(Strings.allianceContributeNothing())
            else -> ContributeActionUiState.Short(Strings.allianceShareRoundsToNothing())
        },
    )
}

// **The basket spelled out, resource names and all**, which is how every other cost in this app is
// written and what the four-chip row could not afford: at ~88dp a stop, *"4,210 Metal · 960 Crystal ·
// 120 Deuterium"* wrapped to three ragged lines on every one of them. Full width, it is one.
//
// Zeroes are dropped, which `line()` already does for a project's cost — a basket that says
// *"0 Deuterium"* is a figure nobody needs to read.
private fun Resources.offer(alliance: TextRes, confirms: Boolean): ContributeActionUiState.Offered =
    ContributeActionUiState.Offered(
        basket = line(),
        action = Strings.allianceContributeAction(alliance),
        metal = metal,
        crystal = crystal,
        deuterium = deuterium,
        confirms = confirms,
    )

// **Floored, by integer division**, which is the promise: what the line above the control states is
// what leaves. `EVERYTHING` is the colony itself rather than `100 / 100` of it, so no rounding can
// come between *all* and all.
private fun Resources.share(share: ContributeShare): Resources = when (share) {
    ContributeShare.A_TENTH -> scaled(10)
    ContributeShare.A_QUARTER -> scaled(25)
    ContributeShare.A_HALF -> scaled(50)
    ContributeShare.EVERYTHING -> this
}

private fun Resources.scaled(percent: Int): Resources = Resources.of(
    metal = metal * percent / 100,
    crystal = crystal * percent / 100,
    deuterium = deuterium * percent / 100,
)

// `All` is a word rather than 100%, because reaching for everything is not arithmetic.
private fun ContributeShare.label(): TextRes = when (this) {
    ContributeShare.A_TENTH -> Strings.allianceShare(10)
    ContributeShare.A_QUARTER -> Strings.allianceShare(25)
    ContributeShare.A_HALF -> Strings.allianceShare(50)
    ContributeShare.EVERYTHING -> Strings.allianceShareAll()
}

private fun AllianceProject.title(): TextRes = when (this) {
    AllianceProject.CHARTER_EXPANSION -> Strings.allianceProjectCharter()
    AllianceProject.SHARED_LOGISTICS -> Strings.allianceProjectLogistics()
}

private fun AllianceProject.effect(): TextRes = when (this) {
    AllianceProject.CHARTER_EXPANSION -> Strings.allianceProjectCharterEffect()
    AllianceProject.SHARED_LOGISTICS -> Strings.allianceProjectLogisticsEffect()
}

// **The founder cannot leave while anybody else is on the roster, and the frame draws that hole
// rather than hiding it** — `alliance-sheet.md`'s first open item. Handing an alliance on is a new
// act nobody has designed, so a founder with company is offered nothing rather than a control that
// refuses.
// **`canDisband()` is not re-asked in the second arm, and the absence is deliberate.** The two
// powers are complements — a founder may not leave and everybody else may — so anything reaching
// the second arm is the founder, and asking again was a condition with one possible answer.
private fun departure(role: AllianceRole, seats: Int): DepartureUiState? = when {
    role.canLeave() -> DepartureUiState(action = Strings.allianceLeaveAction(), disbands = false)
    seats <= 1 -> DepartureUiState(action = Strings.allianceDisbandAction(), disbands = true)
    else -> null
}

fun searchUiState(query: String, answer: AllianceSearchResponse?, asking: Boolean, reachable: Boolean): SearchUiState =
    SearchUiState(
        label = Strings.allianceSearchLabel(),
        query = query,
        state = when {
            !reachable -> SearchResultsUiState.Held(Strings.allianceSearchHeld())
            query.isBlank() -> SearchResultsUiState.Idle(Strings.allianceSearchIdle())
            asking -> SearchResultsUiState.Asking(Strings.allianceAsking())
            answer == null -> SearchResultsUiState.Idle(Strings.allianceSearchIdle())
            answer.results.isEmpty() -> SearchResultsUiState.Empty(Strings.allianceSearchEmpty(TextRes(query)))
            else -> SearchResultsUiState.Results(answer.results.map { it.searchRow() })
        },
    )

private fun Alliance.searchRow(): SearchRowUiState = SearchRowUiState(
    id = id,
    name = TextRes(name.value),
    tag = TextRes(tag.value),
    level = Strings.allianceLevelBadge(level.value),
    seats = Strings.allianceSeatsLine(seats.taken, seats.cap),
    action = Strings.allianceRequestSeat(),
    joinable = seats.taken < seats.cap,
)

fun foundingUiState(
    name: String,
    tag: String,
    nameTaken: Boolean,
    tagTaken: Boolean,
    // **What founding costs, and what the colony holds.** Null is *the price has not been read yet*,
    // which the block says in a line rather than guessing at a figure — the balance is `:server`'s so
    // it can be retuned by a deploy, exactly as a project's cost is.
    price: Resources?,
    colony: Resources,
): FoundingUiState = FoundingUiState(
    label = Strings.allianceFoundLabel(),
    body = Strings.allianceFoundBody(),
    nameLabel = Strings.allianceFoundName(),
    tagLabel = Strings.allianceFoundTag(),
    tagRule = Strings.allianceFoundTagRule(),
    name = name,
    tag = tag,
    cost = if (price == null) Strings.allianceFoundPriceUnread() else Strings.allianceFoundPrice(price.line()),
    // **Unknown reads as unaffordable, which is the safe direction.** A control offered over a price
    // nobody has read would be one the server refuses; a control withheld is one more tap once the
    // price lands.
    affordable = price != null && colony.covers(price),
    shortLine = Strings.allianceFoundShort(),
    action = Strings.allianceFoundAction(),
    nameRefusal = if (nameTaken) Strings.allianceNameTaken(TextRes(name)) else null,
    tagRefusal = if (tagTaken) Strings.allianceTagTaken(TextRes(tag)) else null,
    // **Absent while a refusal stands**, which is the same absence `Save name` already uses: the
    // answer was about the string that was there, and committing it again would ask the same
    // question and get the same answer.
    //
    // **And absent while the contract would refuse what is typed**, which is the fix rather than the
    // restatement. This read `name.isNotBlank() && tag.isNotBlank()` while the tap built an
    // `AllianceTag` inside a `runCatching` and dropped the refusal — so *Found it* appeared over a
    // lower-case tag and silently did nothing when it was pressed. The contract's own check is what
    // the control now asks, through the shared `refusalFor` both ends read, so the two can never
    // disagree about what is committable again.
    //
    // **And absent while the colony cannot pay**, which is the project row's own rule arriving on
    // the one control in this app that spends a colony on something outside it: the cost goes red,
    // the line says the colony is short, and there is no button to press. A control that could only
    // earn `AllianceFoundingUnaffordable` is a control that answers no.
    committable = foundable(name, tag) && !nameTaken && !tagTaken && price != null && colony.covers(price),
)

// **What the control and the tap both ask**, in one place so the answer cannot differ between them.
// The trim is the client's, per `AllianceName`'s own division of labour: a name typed with a
// trailing space is a name, and it is this layer that makes it one before the contract sees it.
fun foundable(name: String, tag: String): Boolean =
    AllianceName.refusalFor(name.trim()) == null && AllianceTag.refusalFor(tag) == null

// What the control actually sends, as the type `core` charges against. It lives here rather than on
// the ui-state because `Resources` is `core`'s and `:client:alliance:ui` draws without ever meeting
// one — the offer carries three numbers and this is the one place they become a basket.
fun ContributeActionUiState.Offered.basket(): Resources =
    Resources.of(metal = metal, crystal = crystal, deuterium = deuterium)

fun contributeConfirmUiState(): ContributeConfirmUiState = ContributeConfirmUiState(
    title = Strings.allianceConfirmAllTitle(),
    body = Strings.allianceConfirmAllBody(),
    confirm = Strings.allianceConfirmAllAction(),
    keep = Strings.allianceConfirmAllKeep(),
)
