package dev.fardavide.oltre.client.alliance.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.OltreCardState
import dev.fardavide.oltre.client.design.component.RefusalBlock
import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.oltreCard
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes

// **The alliance destination, in the four faces `alliance-sheet.md` §7 settled** — and the screen
// that replaces the honest "Coming soon" the tab carried while the feature was being built.
//
// One shape, the one every destination already has: 16dp screen padding, 8dp between cards, 13dp
// between sections, on the 560dp centred column. Nothing here animates and nothing here decides —
// `:client:alliance:presentation` chose the face, the words and which controls may be pressed.

@Composable
fun AllianceScreen(
    state: AllianceUiState,
    actions: AllianceActions,
    // **The `All` chip's two-step face, in the flow rather than over it.** The delete face is a
    // sheet because it is reached from a sheet; this is reached from a chip halfway down a scrolling
    // destination, and a modal over it would take the pool figures the question is about off screen.
    // **One parameter, not three.** The face and its two answers have no meaning apart — there is no
    // confirm without a way to say yes and a way to say no — and three parameters that are always
    // passed together are three chances to pass two of them.
    confirm: ContributeConfirmUiState? = null,
    // **Red rather than amber, and that is the whole of what look-don't-act means here.** Amber
    // promises the tap will happen when the network is back; this game does not make that promise
    // about a pool somebody else is also paying into. The chips keep full strength and go on
    // answering, which is what stops the refusal turning them into dead controls.
    refusal: RefusalUiState? = null,
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(13.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag(AllianceTestTags.SCREEN)
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        confirm?.let { ContributeConfirm(it, actions.onConfirmContribute, actions.onKeepContribute) }
        refusal?.let { RefusalBlock(lead = it.lead, body = it.body) }
        when (state) {
            AllianceUiState.Held -> HeldFace()
            AllianceUiState.Asking -> Centred(Strings.allianceAsking(), AllianceTestTags.SEARCH_LINE)
            is AllianceUiState.Seeking -> {
                SearchFace(state.search, actions)
                FoundingFace(state.founding, actions)
            }
            is AllianceUiState.Waiting -> WaitingFace(state, actions)
            is AllianceUiState.Enlisted -> EnlistedFace(state, actions)
        }
    }
}

// **Every tap this destination can produce, in one record.** The screen is handed behaviour rather
// than reaching for it, which is what lets a screenshot test draw every face with no gateway in
// sight — and what keeps the module free of anything that knows a network exists.
data class AllianceActions(
    val onQueryChange: (String) -> Unit = {},
    val onRequestSeat: (SearchRowUiState) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onTagChange: (String) -> Unit = {},
    val onFound: () -> Unit = {},
    val onWithdraw: () -> Unit = {},
    val onAnswer: (PendingRowUiState, Boolean) -> Unit = { _, _ -> },
    val onRemove: (RosterRowUiState) -> Unit = {},
    val onContribute: (ContributeChipUiState) -> Unit = {},
    val onBuy: (ProjectRowUiState) -> Unit = {},
    val onDepart: () -> Unit = {},
    // The two answers to the `All` chip's question, here rather than beside it on the screen's own
    // parameter list: every other tap this destination makes is on this record, and a control whose
    // callback lives somewhere else is the one that gets forgotten.
    val onConfirmContribute: () -> Unit = {},
    val onKeepContribute: () -> Unit = {},
)

// **The whole face, not a dimming of another one.** The alliance is entirely server-side, so with no
// network there is no standing to draw stale and nothing to grey: there is a sentence, at full
// strength, because reading is not acting.
@Composable
private fun HeldFace() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).testTag(AllianceTestTags.HELD),
    ) {
        Text(
            text = Strings.allianceSearchHeld().resolve(),
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

@Composable
private fun Centred(text: TextRes, tag: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
        Text(
            text = text.resolve(),
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun SearchFace(state: SearchUiState, actions: AllianceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(state.label)
        // 44dp, the name-field idiom rather than the galaxy's 28dp filter: this one asks a server.
        Field(
            value = state.query,
            onChange = actions.onQueryChange,
            tag = AllianceTestTags.SEARCH_FIELD,
        )
        when (val results = state.state) {
            is SearchResultsUiState.Idle -> Note(results.line, AllianceTestTags.SEARCH_LINE)
            is SearchResultsUiState.Asking -> Note(results.line, AllianceTestTags.SEARCH_LINE)
            is SearchResultsUiState.Empty -> Note(results.line, AllianceTestTags.SEARCH_LINE, full = true)
            is SearchResultsUiState.Held -> Note(results.line, AllianceTestTags.SEARCH_LINE)
            is SearchResultsUiState.Results -> results.rows.forEachIndexed { index, row ->
                SearchRow(row, index, actions)
            }
        }
    }
}

@Composable
private fun SearchRow(row: SearchRowUiState, index: Int, actions: AllianceActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AllianceTestTags.row(AllianceTestTags.SEARCH_ROW, index))
            .oltreCard(OltreCardState.ACTIONABLE)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title(row.name)
                Tag(row.tag)
            }
            Caption(row.seats)
        }
        Badge(row.level)
        // **Absent rather than disabled on a full alliance.** The row is still worth reading; a
        // control that answers no is the thing this product does not ship.
        if (row.joinable) Ghost(row.action, onClick = { actions.onRequestSeat(row) })
    }
}

@Composable
private fun FoundingFace(state: FoundingUiState, actions: AllianceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(state.label)
        Note(state.body)
        Caption(state.nameLabel)
        Field(state.name, actions.onNameChange, AllianceTestTags.FOUND_NAME, refused = state.nameRefusal != null)
        state.nameRefusal?.let { Refusal(it, AllianceTestTags.FOUND_NAME_REFUSAL) }
        Caption(state.tagLabel)
        Field(state.tag, actions.onTagChange, AllianceTestTags.FOUND_TAG, refused = state.tagRefusal != null)
        state.tagRefusal?.let { Refusal(it, AllianceTestTags.FOUND_TAG_REFUSAL) }
        // Absent, never greyed, while there is nothing to commit or a refusal stands.
        if (state.committable) {
            Ghost(state.action, onClick = actions.onFound, tag = AllianceTestTags.FOUND_ACTION)
        }
    }
}

@Composable
private fun WaitingFace(state: AllianceUiState.Waiting, actions: AllianceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Strings.allianceWaitingOn(state.name).resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(AllianceTestTags.WAITING_LINE),
            )
            Tag(state.tag)
        }
        Note(state.body)
        Ghost(state.withdraw, onClick = actions.onWithdraw, tag = AllianceTestTags.WITHDRAW)
    }
}

@Composable
private fun EnlistedFace(state: AllianceUiState.Enlisted, actions: AllianceActions) {
    Head(state.header)
    state.pending?.let { pending ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(pending.label)
            if (pending.rows.isEmpty()) {
                Note(pending.empty, AllianceTestTags.PENDING_EMPTY, full = true)
            } else {
                pending.rows.forEachIndexed { index, row -> PendingRow(row, index, actions) }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        SectionLabel(Strings.allianceRosterLabel())
        Column(modifier = Modifier.fillMaxWidth().oltreCard(OltreCardState.ACTIONABLE)) {
            state.roster.forEachIndexed { index, row -> RosterRow(row, index, actions) }
        }
    }
    Treasury(state.treasury, actions)
    state.departure?.let { departure ->
        Ghost(departure.action, onClick = actions.onDepart, tag = AllianceTestTags.DEPARTURE, danger = true)
    }
}

@Composable
private fun Head(state: AllianceHeadUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.name.resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).testTag(AllianceTestTags.HEAD_NAME),
            )
            Tag(state.tag)
            Badge(state.level, tag = AllianceTestTags.HEAD_LEVEL)
        }
        Caption(state.seats, tag = AllianceTestTags.HEAD_SEATS)
        // The alliance's own gauge, on its own ladder. Drawn rather than counted in words for the
        // strip's reason: a share of a level is a length, not a number worth reading.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.09f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.progress.coerceIn(0, 100) / 100f)
                    .height(2.dp)
                    .background(OltreColors.accent),
            )
        }
    }
}

// **The first two-way control in the app**, and the two halves are equal grid columns rather than
// flex children so neither is 2dp wider than the other. `Decline` is not red: red has only ever been
// a refusal shown *to* the player.
@Composable
private fun PendingRow(row: PendingRowUiState, index: Int, actions: AllianceActions) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AllianceTestTags.row(AllianceTestTags.PENDING_ROW, index))
            .oltreCard(OltreCardState.ACTIONABLE)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title(row.name, modifier = Modifier.weight(1f))
            Badge(row.level)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Filled(
                row.accept,
                onClick = { actions.onAnswer(row, true) },
                tag = AllianceTestTags.row(AllianceTestTags.ACCEPT, index),
                modifier = Modifier.weight(1f),
            )
            Ghost(
                row.decline,
                onClick = { actions.onAnswer(row, false) },
                tag = AllianceTestTags.row(AllianceTestTags.DECLINE, index),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RosterRow(row: RosterRowUiState, index: Int, actions: AllianceActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(AllianceTestTags.row(AllianceTestTags.ROSTER_ROW, index))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Title(row.name)
            // The role rides the caption ramp rather than a second badge — see `RosterRowUiState`.
            row.role?.let { Caption(it) }
        }
        Badge(row.level)
        if (row.removable) Ghost(Strings.allianceRemove(), onClick = { actions.onRemove(row) })
    }
}

@Composable
private fun Treasury(state: TreasuryUiState, actions: AllianceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(state.label)
        // **At full strength, above the first tap, every time this face is drawn.**
        Text(
            text = state.rule.resolve(),
            color = OltreColors.text,
            fontFamily = oltreMono(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag(AllianceTestTags.TREASURY_RULE),
        )
        state.unread?.let { Note(it) }
        Column(modifier = Modifier.fillMaxWidth().oltreCard(OltreCardState.ACTIONABLE)) {
            state.pool.forEachIndexed { index, row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag(AllianceTestTags.row(AllianceTestTags.POOL_ROW, index))
                        .padding(horizontal = 12.dp),
                ) {
                    Caption(row.name, modifier = Modifier.weight(1f))
                    Text(
                        text = row.amount.resolve(),
                        color = OltreColors.text,
                        fontFamily = oltreMono(),
                        fontSize = 15.sp,
                    )
                }
            }
        }
        Caption(state.contributed, tag = AllianceTestTags.CONTRIBUTED)
        // **`IntrinsicSize.Min`, so all four chips are as tall as the tallest.** The figures wrap to
        // different numbers of lines — `All` is the longest and `10%` the shortest — and without
        // this the shares sit at four different heights across one row, which reads as four
        // different kinds of control rather than one ladder.
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            state.chips.forEachIndexed { index, chip ->
                Chip(chip, index, actions, Modifier.weight(1f).fillMaxHeight())
            }
        }
        SectionLabel(state.projectsLabel)
        if (state.projects.isEmpty()) {
            Note(state.projectsEmpty, full = true)
        } else {
            state.projects.forEachIndexed { index, project -> ProjectRow(project, index, actions) }
        }
    }
}

// Each chip states the absolute figure it sends **above** the share, floored — see
// `ContributeChipUiState`.
@Composable
private fun Chip(chip: ContributeChipUiState, index: Int, actions: AllianceActions, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // The figure at the top and the share at the foot, so the four shares read as one row.
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .heightIn(min = 44.dp)
            .testTag(AllianceTestTags.row(AllianceTestTags.CHIP, index))
            .let { if (chip.enabled) it.pressable(oltreActionShape) { actions.onContribute(chip) } else it }
            .background(Color.White.copy(alpha = if (chip.enabled) 0.16f else 0.06f), oltreActionShape)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Text(
            text = chip.figure.resolve(),
            color = if (chip.enabled) OltreColors.text else OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = chip.share.resolve(),
            color = if (chip.enabled) OltreColors.text else OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProjectRow(row: ProjectRowUiState, index: Int, actions: AllianceActions) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AllianceTestTags.row(AllianceTestTags.PROJECT_ROW, index))
            .oltreCard(if (row.affordable) OltreCardState.ACTIONABLE else OltreCardState.WAITING)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title(row.name, modifier = Modifier.weight(1f))
            row.bought?.let { Badge(it) }
        }
        Note(row.effect)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.cost.resolve(),
                color = if (row.affordable) OltreColors.text else OltreColors.danger,
                fontFamily = oltreMono(),
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            // Short of the pool, the control is simply absent and the cost goes red. There is no
            // time-until-affordable on a project, because a treasury has no rate to compute one from.
            if (row.buyable) {
                Ghost(
                    row.action,
                    onClick = { actions.onBuy(row) },
                    tag = AllianceTestTags.row(AllianceTestTags.PROJECT_BUY, index),
                )
            }
        }
        if (!row.affordable) Note(row.shortLine)
    }
}

// ── The small pieces ─────────────────────────────────────────────────────────────────────────

@Composable
private fun Field(value: String, onChange: (String) -> Unit, tag: String, refused: Boolean = false) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = OltreColors.text, fontFamily = oltreMono(), fontSize = 13.sp),
        cursorBrush = SolidColor(OltreColors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .testTag(tag)
            .border(
                1.dp,
                // Danger at 45%, the same alpha the focus line already uses. The fill is untouched:
                // the value stays editable, because the refusal is about the string that *was* there.
                if (refused) OltreColors.danger.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.14f),
                oltreCardShape,
            )
            .padding(horizontal = 12.dp, vertical = 13.dp),
    )
}

@Composable
private fun Refusal(text: TextRes, tag: String) {
    Text(
        text = text.resolve(),
        color = OltreColors.danger,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        modifier = Modifier.testTag(tag),
    )
}

// **Every one of these takes a tag rather than a nullable one**, and the reason is not the tag: an
// optional tag is a branch, and a branch only one caller ever takes is a branch nothing can cover.
// `AllianceTestTags.UNNAMED` is what an element nobody drives carries, which keeps the helpers
// straight-line and leaves the tag list honest about what a robot can reach.
@Composable
private fun Note(text: TextRes, tag: String = AllianceTestTags.UNNAMED, full: Boolean = false) {
    Text(
        text = text.resolve(),
        color = if (full) OltreColors.text else OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        lineHeight = 17.sp,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun Title(text: TextRes, modifier: Modifier = Modifier) {
    Text(
        text = text.resolve(),
        color = OltreColors.text,
        fontFamily = oltreMono(),
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
private fun Caption(text: TextRes, modifier: Modifier = Modifier, tag: String = AllianceTestTags.UNNAMED) {
    Text(
        text = text.resolve(),
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        modifier = modifier.testTag(tag),
    )
}

@Composable
private fun Tag(text: TextRes) {
    Text(
        text = text.resolve(),
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 7.dp),
    )
}

@Composable
private fun Badge(text: TextRes, tag: String = AllianceTestTags.UNNAMED) {
    Text(
        text = text.resolve(),
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.testTag(tag).padding(horizontal = 7.dp),
    )
}

@Composable
private fun Ghost(
    text: TextRes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = AllianceTestTags.UNNAMED,
    danger: Boolean = false,
) {
    val ink = if (danger) OltreColors.danger else OltreColors.text
    Text(
        text = text.resolve(),
        color = ink,
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier.testTag(tag)
            .heightIn(min = 44.dp)
            .pressable(shape = oltreActionShape, onClick = onClick)
            .border(1.dp, ink.copy(alpha = 0.45f), oltreActionShape)
            .padding(horizontal = 11.dp, vertical = 13.dp),
    )
}

@Composable
private fun Filled(
    text: TextRes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = AllianceTestTags.UNNAMED,
) {
    Text(
        text = text.resolve(),
        color = OltreColors.background,
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier.testTag(tag)
            .heightIn(min = 44.dp)
            .pressable(shape = oltreActionShape, onClick = onClick)
            .background(OltreColors.accent, oltreActionShape)
            .padding(horizontal = 11.dp, vertical = 13.dp),
    )
}

// The two-step confirm `All` raises, in the delete face's grammar: the consequence stated before
// either tap, a ghost meaning no sitting first, and the danger control as the only filled red button
// in the product.
@Composable
fun ContributeConfirm(state: ContributeConfirmUiState, onConfirm: () -> Unit, onKeep: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OltreColors.danger.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(
            text = state.title.resolve(),
            color = OltreColors.danger,
            fontFamily = oltreMono(),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Note(state.body)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Ghost(state.keep, onClick = onKeep, tag = AllianceTestTags.KEEP_CONTRIBUTE, modifier = Modifier.weight(1f))
            Text(
                text = state.confirm.resolve(),
                color = OltreColors.background,
                fontFamily = oltreMono(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .testTag(AllianceTestTags.CONFIRM_CONTRIBUTE)
                    .heightIn(min = 44.dp)
                    .pressable(shape = oltreActionShape, onClick = onConfirm)
                    .background(OltreColors.danger, oltreActionShape)
                    .padding(horizontal = 11.dp, vertical = 13.dp),
            )
        }
    }
}
