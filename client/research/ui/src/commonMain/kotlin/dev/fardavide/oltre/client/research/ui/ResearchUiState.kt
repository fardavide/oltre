package dev.fardavide.oltre.client.research.ui

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.HeldUiState
import dev.fardavide.oltre.client.design.component.RowSheetUiState
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology

// **What the Research tab draws, and nothing about how it is derived.** The mapping from `GameState`
// into these types is `:client:research:presentation`, which depends on this module rather than the
// other way round. The three `core` enums here are the judgement the rule allows: a row is *keyed*
// by the technology it offers, and a screen carrying a `String` there would hand its callbacks back
// a name to parse.

// Two branches, six rows, one screen — and the flat list *is* the tech tree, which is still the
// strongest argument against drawing one. Two lists rather than one of six, because an applied
// level multiplies a per-hour rate and an adaptation level widens a band in °C, g or atm: sorting
// them into one column would make two kinds of thing look like one.
//
// When a project is in flight, five rows read the same wait and the sixth counts it down, and the
// two numbers verify each other with nothing added to carry the explanation. That is what one
// screen buys and what neither a segmented control nor a sixth tab can buy at any price.
//
// **They very nearly fit and do not quite.** The 0.3 design put six rows at ~610dp against ~708dp
// of content on a 393x852 phone; it measured the row at 74dp and the real Compose row was 106dp, so
// the true figure was ~788dp and the screen scrolled by about 105dp. The argument survives — five of
// the six rows and the countdown are on screen together, which is the part that has to be true —
// but it is now an argument about a screen that scrolls a little, and the design sheet should know.
// See `decisions.md`.
//
// **The gap halved when the verdict arrived.** Every row is 97dp now, measured, because one line of
// consequence replaced the applied row's effect line and the ladder's effect *and* shortlist lines —
// so the tallest frame is 734dp and the screen scrolls by about 50dp. The two branches are also the
// same height for the first time, which is what "a running ladder looks exactly like a running
// technology" was always supposed to mean.
data class ResearchUiState(
    val technologies: List<TechnologyRowUiState>,
    val adaptation: List<AdaptationRowUiState>,
    // "watching Metal Mine" beside the TECHNOLOGIES heading, and null when nothing is watched. The
    // watch is one slot shared with the colony, so this names a facility as readily as a technology
    // — and it is handed in for the same reason the Colony screen's copy is: what a facility is
    // called belongs to the screen that draws facilities.
    val watching: TextRes?,
)

// Which project landed between the instant the save was written and the instant the app came back.
// One or the other, never both — and since 0.12.2 that is a rule about the *announcement* rather
// than about the colony. Both branches can now land while the app is closed, exactly as several
// facilities always could; `arrivalOf` takes the last completion of any kind for the reason it
// always did, that four bands crossing four cards at once is a light show.
sealed interface FinishedWhileAway {
    data class Project(val technology: Technology) : FinishedWhileAway
    data class Ladder(val technology: AdaptationTechnology) : FinishedWhileAway
}

data class TechnologyRowUiState(
    val technology: Technology,
    val name: TextRes,
    val level: TechLevel,
    // No longer drawn on the card — the verdict took that line, because two lines of numbers about
    // the same level is where a dense row becomes an unreadable one. It is still carried, because
    // the sheet states both halves of it and the sheet is derived from here.
    val effect: EffectUiState,
    // What the next level is worth to this empire now. Null only while the row is in flight, where
    // the decision has already been made and the slot belongs to the finish line, and at a ceiling
    // there is no next level to price.
    val verdict: VerdictUiState?,
    val costs: List<CostChipUiState>,
    val duration: TextRes,
    val action: ResearchActionUiState,
    // What the card body opens: the arithmetic behind the verdict, the ladder of what the level
    // gates, and the numbers the verdict displaced. Derived here rather than in the screen because
    // an inert row's pointer names the best buy on the *screen*, which no single row can know.
    val sheet: RowSheetUiState,
    // The square beside the ghost time. Null unless this row is one the empire cannot *pay* for —
    // a row held up only by a busy slot has nothing to be told about, because the resources are
    // already there. See `WatchUiState`.
    val watch: WatchUiState?,
    // True on at most one row in the whole app, and only for the first couple of seconds after a
    // launch. See the same field on the colony's facility rows — it is a fact about this launch
    // rather than about the empire.
    val finishedWhileAway: Boolean,
    // **What this row has asked for and the server has not answered.** A fact about the queue rather
    // than about the empire, which is why it sits beside the empire's own fields — see the same field
    // on the colony's facility rows.
    val held: HeldUiState,
)

// The same fields as an applied row, and deliberately its own type rather than a widened one:
// the two branches carry different technology enums, and a sum type in the identity field would
// make every reader answer for a branch it does not render. What it is *not* is a different row —
// the screen draws both through one composable, because from three rows away a running ladder has
// to look exactly like a running technology.
data class AdaptationRowUiState(
    val technology: AdaptationTechnology,
    val name: TextRes,
    val level: TechLevel,
    val effect: EffectUiState,
    // The shortlist wearing the name every row now uses for the same slot. It is derived from the
    // field below rather than authored twice — the shortlist *was* the verdict, and this design is
    // that sentence repeated on the other twelve rows.
    val verdict: VerdictUiState?,
    val costs: List<CostChipUiState>,
    val duration: TextRes,
    val action: ResearchActionUiState,
    // The counts behind the verdict, which the sheet states as a sentence. Never null on this
    // branch: a ladder that would unlock nothing reports zero rather than going quiet, because
    // "Thermal 1 unlocks nothing" is the sentence that makes the other two mean something.
    val shortlist: ShortlistUiState,
    val sheet: RowSheetUiState,
    // The applied branch's field, unchanged: the two rows share one composable, so a watched ladder
    // has to be drawn by the same parts as a watched technology.
    val watch: WatchUiState?,
    val finishedWhileAway: Boolean,
    val held: HeldUiState,
)

// What the next level of this ladder would buy, counted over the worlds the player has **already
// surveyed** — and therefore the line that makes surveying a decision rather than a bookmark.
//
// Without it the optimal play is to wait: `verdictFor` re-derives against current levels and
// `GalaxyState.surveyed` is monotone, so surveying later returns strictly better-labelled rows for
// the same price. This inverts the order — survey, read the shortlist, then commit the one shared
// slot — so the information has to arrive before the purchase rather than after it.
//
// It also answers, for the first time, a question the three ladders could never pose. Priced at
// 1 : 2 : 3 they cost identically, so with four home worlds surveyed the choice between Thermal,
// Gravitic and Atmospheric is arbitrary. With fifty worlds surveyed it is arithmetic.
data class ShortlistUiState(
    val unlocks: Int,
    // The honest half. Most worlds a ladder unlocks are still not worth taking, by construction —
    // a line that sold the count without this would be selling the ladder on a number the player
    // cannot spend.
    val worthTaking: Int,
    val label: TextRes,
    // 320dp drops the adjective, never a figure. Both counts are what the player is comparing
    // across the three rows, and the effect line above it abbreviates by the same rule.
    val compactLabel: TextRes,
)

// What the technology does now and what the next level would make it — both halves, because
// level 8 is only meaningful against level 7.
//
// **It was the row's second line until 0.6.0 and it is the sheet's first sentence now.** The verdict
// took the slot, and the two of them could not share it: two lines of numbers about the same level
// is where a dense row becomes an unreadable one. That move is also why this no longer carries a
// short form — the shortening existed because 320dp could not fit "metal · crystal output" beside a
// watch square, and the sheet is a full-width surface with no square on it.
data class EffectUiState(
    // Absent at level 0 on the applied branch: there is no "now" to compare against yet. Never
    // absent on an adaptation row — a tolerance band exists at level 0 where a production bonus
    // does not, which is why Enrichment 0 reads "→ +14%" and Gravitic 0 reads
    // "0.65 … 1.40 → 0.60 … 1.52 g".
    val current: TextRes?,
    val next: TextRes,
    val subject: TextRes,
)

sealed interface ResearchActionUiState {
    data object Start : ResearchActionUiState

    // One number with one meaning: when you can start this. It is the later of "when can I pay
    // for it" and "when does the slot free", and the player never has to know which it was.
    data class AvailableIn(val label: TextRes) : ResearchActionUiState
    data class Locked(val reason: TextRes) : ResearchActionUiState

    data class Running(
        val toLevel: TechLevel,
        val countdown: TextRes,
        val progressPercent: Int,
        val doneAt: TextRes,
    ) : ResearchActionUiState
}

// The shortlist wearing the name the rest of the app now uses for the same slot. Nothing about the
// copy changes: the shortlist *was* the verdict, and this design is that sentence repeated on the
// twelve rows that did not have one.
//
// **Here rather than one layer up**, unlike the other three mappings this screen makes: both sides
// are models this module already owns, so nothing about it reads a `GameState`. It is the same call
// `FacilityRowUiState.toRowSheetUiState` makes on the Colony tab.
// The accent line a running row carries, authored once and read twice: the card draws it in the
// "→ becomes" slot and the sheet repeats it where the verdict would have been. **Here rather than
// inside the composable** — the Colony's own `becomes()` is the same function for the same reason:
// joining two clauses with the app's separator is a decision about language, and a `ui` module
// draws rather than decides.
internal fun ResearchActionUiState.Running.becomes(): TextRes =
    Strings.clauses(listOf(Strings.becomesLevel(toLevel.value), doneAt))

fun ShortlistUiState.toVerdictUiState(): VerdictUiState =
    VerdictUiState(label = label, compactLabel = compactLabel)
