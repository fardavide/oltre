package dev.fardavide.oltre.client.alliance.presentation

import dev.fardavide.oltre.client.alliance.domain.AllianceRosterReading
import dev.fardavide.oltre.client.alliance.domain.AllianceState
import dev.fardavide.oltre.client.alliance.domain.canAnswerJoinRequests
import dev.fardavide.oltre.client.alliance.domain.canDisband
import dev.fardavide.oltre.client.alliance.domain.canLeave
import dev.fardavide.oltre.client.alliance.domain.canRemove
import dev.fardavide.oltre.client.alliance.ui.AllianceHeadUiState
import dev.fardavide.oltre.client.alliance.ui.AllianceUiState
import dev.fardavide.oltre.client.alliance.ui.ContributeChipUiState
import dev.fardavide.oltre.client.alliance.ui.ContributeConfirmUiState
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
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceSearchResponse
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
        is AllianceState.Enlisted -> standing.face(colony, treasury)
    }
}

private fun AllianceState.Enlisted.face(colony: Resources, treasury: TreasuryResponse?): AllianceUiState.Enlisted {
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
        treasury = treasuryFace(treasury, colony),
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
    removable = viewer.canRemove(role),
)

// **The viewer's own level is computed from raw experience with this build's ladder**, never sent
// as a number by the server — `AllianceRoster.kt` argues it: a roster where one row's badge came
// from another build's curve could contradict the strip on the same screen.
private fun ExperienceReading.badge(): TextRes = when (this) {
    is ExperienceReading.Known -> Strings.levelBadge(ExperienceBalance.levelFor(earned).value)
    // A member whose colony has not been written since the column existed. An honest unknown rather
    // than a zero, which would read as *this player has done nothing*.
    ExperienceReading.Unknown -> Strings.levelBadge(0)
}

private fun PlayerProfile.drawnName(): TextRes {
    val chosen = name ?: return Strings.playerDefaultName()
    return TextRes(chosen.value)
}

private fun treasuryFace(treasury: TreasuryResponse?, colony: Resources): TreasuryUiState = TreasuryUiState(
    label = Strings.allianceTreasuryLabel(),
    rule = Strings.allianceTreasuryRule(),
    // **One safe call each, on the one thing that is actually nullable.** `pool`, `contributed` and
    // `projects` are all non-null on a `TreasuryResponse`, so the second `?.` in each of these asked
    // a question with one answer and left a branch nothing could take.
    pool = treasury?.pool.orEmptyRows(),
    contributed = Strings.alliancePaidIn(
        if (treasury == null) TextRes("0") else treasury.contributed.groupedByThousands(),
    ),
    chips = contributeChips(colony, live = treasury != null),
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

// **The ladder, and the whole of what a chip promises.** Each share is taken of the colony's own
// stock and **floored**, so the figure printed above the share is exactly what leaves — a chip that
// sends more than it states is the worst kind of wrong on a control nothing can undo.
//
// `All` is a word rather than 100%, because reaching for everything is not arithmetic, and it is the
// one chip that confirms.
internal fun contributeChips(colony: Resources, live: Boolean): List<ContributeChipUiState> {
    val shares = SHARES.map { percent ->
        chip(
            share = Strings.allianceShare(percent),
            metal = colony.metal * percent / 100,
            crystal = colony.crystal * percent / 100,
            deuterium = colony.deuterium * percent / 100,
            confirms = false,
            live = live,
        )
    }
    return shares + chip(
        share = Strings.allianceShareAll(),
        metal = colony.metal,
        crystal = colony.crystal,
        deuterium = colony.deuterium,
        confirms = true,
        live = live,
    )
}

private fun chip(
    share: TextRes,
    metal: Long,
    crystal: Long,
    deuterium: Long,
    confirms: Boolean,
    live: Boolean,
): ContributeChipUiState = ContributeChipUiState(
    share = share,
    // **Digits in the rail's own order and no resource names**, which is a departure from the way
    // every other cost in the app is written and is forced by the width: four chips across 393dp
    // leave about 88dp each, and *"4,210 Metal · 960 Crystal · 120 Deuterium"* is three wrapped
    // lines on every one of them. The names are recoverable — the pool rows directly above list the
    // three in this order, with their names — and what a chip must not lose is the *figures*, since
    // those are the promise it makes.
    figure = Strings.clauses(
        listOf(metal.groupedByThousands(), crystal.groupedByThousands(), deuterium.groupedByThousands()),
    ),
    metal = metal,
    crystal = crystal,
    deuterium = deuterium,
    confirms = confirms,
    // **A chip that would send nothing does not press**, which is `core`'s `NothingOffered` refusal
    // arriving one layer earlier: a control that appears to work and changes nothing is the dead
    // control in its purest form, and a colony at ten metal has three chips that round to zero.
    enabled = live && (metal > 0 || crystal > 0 || deuterium > 0),
)

private fun AllianceProject.title(): TextRes = when (this) {
    AllianceProject.CHARTER_EXPANSION -> Strings.allianceProjectCharter()
}

private fun AllianceProject.effect(): TextRes = when (this) {
    AllianceProject.CHARTER_EXPANSION -> Strings.allianceProjectCharterEffect()
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
): FoundingUiState = FoundingUiState(
    label = Strings.allianceFoundLabel(),
    body = Strings.allianceFoundBody(),
    nameLabel = Strings.allianceFoundName(),
    tagLabel = Strings.allianceFoundTag(),
    name = name,
    tag = tag,
    action = Strings.allianceFoundAction(),
    nameRefusal = if (nameTaken) Strings.allianceNameTaken(TextRes(name)) else null,
    tagRefusal = if (tagTaken) Strings.allianceTagTaken(TextRes(tag)) else null,
    // **Absent while a refusal stands**, which is the same absence `Save name` already uses: the
    // answer was about the string that was there, and committing it again would ask the same
    // question and get the same answer.
    committable = name.isNotBlank() && tag.isNotBlank() && !nameTaken && !tagTaken,
)

// What the chip actually sends, as the type `core` charges against. It lives here rather than on the
// chip because `Resources` is `core`'s and `:client:alliance:ui` draws without ever meeting a
// `GameState` — the chip carries three numbers and this is the one place they become a basket.
fun ContributeChipUiState.basket(): Resources =
    Resources.of(metal = metal, crystal = crystal, deuterium = deuterium)

fun contributeConfirmUiState(): ContributeConfirmUiState = ContributeConfirmUiState(
    title = Strings.allianceConfirmAllTitle(),
    body = Strings.allianceConfirmAllBody(),
    confirm = Strings.allianceConfirmAllAction(),
    keep = Strings.allianceConfirmAllKeep(),
)

// The three fixed shares. Small, repeatable, and each printing the figure it sends — the friction is
// on `All`, which is the tap that empties a colony.
private val SHARES = listOf(10, 25, 50)
