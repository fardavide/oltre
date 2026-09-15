package dev.fardavide.oltre.client.alliance.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.JoinRequestId

// **What the alliance destination is, as four faces chosen by account state rather than by a
// toggle** — `alliance-sheet.md` §7. A player is in an alliance or is not; there is no control that
// switches between the two, because there is no state in which both are interesting.
//
// Every field is a `TextRes`, which is the module's own rule: nothing here is a `String` except at
// the leaf `Text(…)` draws, and a name a player typed travels as `TextRes.Raw`.
sealed interface AllianceUiState {

    // **The whole destination has no signal.** It is its own face and not a flag on the others,
    // because the alliance is entirely server-side: there is no cached standing worth drawing under
    // a stale stamp the way a roster would be, and a search field with nothing to search is not a
    // dimmed search field, it is a sentence.
    data object Held : AllianceUiState

    // Asked, and nothing has answered yet. Words rather than a spinner — nothing on this screen
    // animates.
    data object Asking : AllianceUiState

    // Not in one. A search, and the founding block beneath it — which the sheet wants to state the
    // price before anything is typed, and which says founding is free instead, because it is: no
    // route charges for it yet. The block will say what it costs when something takes it.
    data class Seeking(
        val search: SearchUiState,
        val founding: FoundingUiState,
    ) : AllianceUiState

    // Asked to join one, and waiting. **Nothing about it expires** (`alliance-sheet.md` §1.4), so
    // there is no countdown on this face and no word about time.
    data class Waiting(
        val name: TextRes,
        val tag: TextRes,
        val body: TextRes,
        val withdraw: TextRes,
    ) : AllianceUiState

    // In one.
    data class Enlisted(
        val header: AllianceHeadUiState,
        // Null for a plain member, and the nullability is the wire's: `null` means *not yours to
        // see* and an empty list means *nobody is waiting*, which are two different screens.
        val pending: PendingUiState?,
        val roster: List<RosterRowUiState>,
        val treasury: TreasuryUiState,
        // The one control on this face that is not about the pool: leaving, or — for a founder —
        // disbanding. Null when neither is open to this role, which is a founder with company.
        val departure: DepartureUiState?,
    ) : AllianceUiState
}

data class AllianceHeadUiState(
    val name: TextRes,
    val tag: TextRes,
    val level: TextRes,
    val seats: TextRes,
    // 0..100. The alliance's own gauge, on its own ladder — geometric where the player's is a
    // straight line, because an alliance's income is contributions and contributions compound.
    val progress: Int,
)

data class SearchUiState(
    val label: TextRes,
    val query: String,
    val state: SearchResultsUiState,
)

sealed interface SearchResultsUiState {

    // Nothing typed. States what a search is *for* rather than pretending to be empty.
    data class Idle(val line: TextRes) : SearchResultsUiState

    data class Asking(val line: TextRes) : SearchResultsUiState

    // Found nothing, naming the string in full ink and stopping. The founding block underneath is
    // what says where to go next.
    data class Empty(val line: TextRes) : SearchResultsUiState

    // **Told apart from `Empty` in words alone** — held dims what acts, never what informs.
    data class Held(val line: TextRes) : SearchResultsUiState

    data class Results(val rows: List<SearchRowUiState>) : SearchResultsUiState
}

// A result row carries the alliance and never its members. No join-policy word, because every
// alliance is joined the identical way and a field that never varies is a decision pretending to be
// data.
data class SearchRowUiState(
    val id: AllianceId,
    val name: TextRes,
    val tag: TextRes,
    val level: TextRes,
    val seats: TextRes,
    val action: TextRes,
    // False when the seats are full: the row still draws, because a full alliance is a fact worth
    // reading, and the control is simply absent rather than a button that answers no.
    val joinable: Boolean,
)

data class FoundingUiState(
    val label: TextRes,
    val body: TextRes,
    val nameLabel: TextRes,
    val tagLabel: TextRes,
    // **The shape the tag has to be, drawn whether or not anything has been typed.** The commit
    // control is absent until both fields hold something the contract accepts, and an absence with
    // nothing beside it is exactly the unanswerable question a greyed button would have been.
    val tagRule: TextRes,
    val name: String,
    val tag: String,
    // **What founding costs, read off the server rather than known here.** The balance lives in
    // `:server` so a deploy can retune it, which is the same division the project rows use for their
    // own prices — so this is null until the price has been read, and the block says so rather than
    // drawing a figure it is guessing at.
    val cost: TextRes,
    // False when the colony cannot cover the price. The control is **absent** rather than greyed and
    // the line below says why, which is the project row's own answer to the same question.
    val affordable: Boolean,
    val shortLine: TextRes,
    val action: TextRes,
    // **The refusal lands on the field rather than in a block**, and both fields can carry one at
    // once because one commit sends both. It clears on the first keystroke — the answer was about
    // the old string and the app cannot claim anything about the new one.
    val nameRefusal: TextRes?,
    val tagRefusal: TextRes?,
    // Absent rather than greyed until there is something to commit, which is `Save name`'s own
    // pattern on the identity face.
    val committable: Boolean,
)

data class PendingUiState(
    val label: TextRes,
    val rows: List<PendingRowUiState>,
    // A good state at full strength, naming where a request comes from, with no control and no word
    // about time.
    val empty: TextRes,
)

data class PendingRowUiState(
    val id: JoinRequestId,
    val name: TextRes,
    val level: TextRes,
    val accept: TextRes,
    // **Not red.** Red in this product has only ever been a refusal shown *to* the player; putting
    // it on the founder's own control would make answering look like damage.
    val decline: TextRes,
)

data class RosterRowUiState(
    val id: AllianceMemberId,
    val name: TextRes,
    val level: TextRes,
    // Null for a plain member, which is nineteen rows in twenty. The role rides the caption ramp
    // rather than a second badge: two badges on one row meaning different kinds of thing would be
    // the first time this app did that.
    val role: TextRes?,
    val removable: Boolean,
)

data class TreasuryUiState(
    val label: TextRes,
    // **Above the first tap, every time, never dismissible.** It is the whole of why a pool is safe
    // against alt accounts.
    val rule: TextRes,
    val pool: List<PoolRowUiState>,
    val contributed: TextRes,
    val chips: List<ContributeChipUiState>,
    val projectsLabel: TextRes,
    val projects: List<ProjectRowUiState>,
    val projectsEmpty: TextRes,
    // Set when the treasury route did not answer. The roster is still worth drawing, so this is a
    // line on the panel rather than a state of the whole face.
    val unread: TextRes?,
)

data class PoolRowUiState(val name: TextRes, val amount: TextRes)

// A share of the colony's own stock, with **the absolute figure it sends stated above the share**,
// floored rather than rounded: a chip that sends more than it states is the worst kind of wrong here.
data class ContributeChipUiState(
    val share: TextRes,
    val figure: TextRes,
    // What the tap actually sends, in whole units. Resolved here rather than on the wire, because a
    // share resolved by the server would be a different basket from the one the player read.
    val metal: Long,
    val crystal: Long,
    val deuterium: Long,
    // `All` gets the delete face's two-step confirm and the three fixed shares do not — Davide,
    // 2026-09-14. It is the one tap that empties a colony and nothing can undo it.
    val confirms: Boolean,
    val enabled: Boolean,
)

data class ProjectRowUiState(
    val project: AllianceProject,
    val name: TextRes,
    val effect: TextRes,
    val cost: TextRes,
    // One tracked word on a row that has been bought before. Null the first time.
    val bought: TextRes?,
    val action: TextRes,
    // Read against the **pool** rather than against personal stock. There is no time-until-
    // affordable on a project, because a treasury has no rate to compute one from — short of the
    // pool, the control is absent and the line says why.
    val affordable: Boolean,
    val shortLine: TextRes,
    val buyable: Boolean,
)

data class DepartureUiState(val action: TextRes, val disbands: Boolean)

// The two-step confirm `All` raises, in the delete face's grammar: the consequence stated before
// either tap, a ghost meaning no sitting first, and the danger control as the product's only filled
// red button.
data class ContributeConfirmUiState(
    val title: TextRes,
    val body: TextRes,
    val confirm: TextRes,
    val keep: TextRes,
)
