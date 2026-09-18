package dev.fardavide.oltre.client.alliance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.player.ui.IdentityMark

// **The member commands, drawn** — *The member commands*, returned 2026-09-18. Chrome and contents
// are split for `DeleteFaceContent`'s reason: the composition root raises one `OltreBottomSheet` and
// swaps what is in it, so a wrapper here would be a second way to raise the same panel.
//
// Nothing on this face animates. The crossing from the command step to the last step is the
// destination switch's own 210ms and belongs to whatever swaps the state, not to this.
@Composable
@NonRestartableComposable
fun MemberCommandsContent(
    uiState: MemberCommandsUiState,
    compact: Boolean,
    actions: MemberCommandsActions,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(13.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(AllianceTestTags.MEMBER_FACE)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        MemberCard(uiState.member)
        uiState.held?.let { HeldRequirement(it) }
        when (val step = uiState.step) {
            is MemberCommandsStepUiState.Commands -> CommandStep(uiState, step, actions)
            is MemberCommandsStepUiState.Confirm -> ConfirmStep(uiState, step, compact, actions)
        }
    }
}

// **Every tap this face can produce.** The face is handed behaviour rather than reaching for it,
// which is what lets a screenshot draw every state with no gateway in sight.
data class MemberCommandsActions(
    val onSetRole: (MemberCommandsUiState, RoleCommandUiState) -> Unit = { _, _ -> },
    // The first of the two taps: it asks again and sends nothing.
    val onAskKick: () -> Unit = {},
    val onKick: (MemberCommandsUiState) -> Unit = {},
    val onKeep: () -> Unit = {},
)

// The row that was tapped, redrawn at 44dp, with no title above it.
@Composable
@NonRestartableComposable
private fun MemberCard(state: MemberCardUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PANEL_EDGE, oltreCardShape)
            .background(oltreCardSurface, oltreCardShape)
            .padding(11.dp),
    ) {
        IdentityMark(mark = state.mark, color = OltreColors.accent, size = 44.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.name.resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(AllianceTestTags.MEMBER_NAME),
            )
            // A plain member draws nothing at all, which is the roster row's own rule — and the only
            // word that can reach here is `ADMIN`, in secondary. See `MemberCardUiState`.
            state.role?.let {
                Text(
                    text = it.resolve(),
                    color = OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.testTag(AllianceTestTags.MEMBER_ROLE),
                )
            }
        }
        Text(
            text = state.level.resolve(),
            color = OltreColors.textSecondary,
            fontFamily = oltreMono(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
    }
}

// **Why nothing can be pressed, in the fleet strip's surface** — the same pair a held card already
// uses, because a held command and a fleet in transit are the same claim about two kinds of thing.
@Composable
@NonRestartableComposable
private fun HeldRequirement(state: MemberHeldUiState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AllianceTestTags.MEMBER_HELD)
            .border(1.dp, OltreColors.warn.copy(alpha = 0.22f), oltreCardShape)
            .background(HELD_FILL, oltreCardShape)
            .padding(11.dp),
    ) {
        Text(
            text = state.lead.resolve(),
            color = OltreColors.warn,
            fontFamily = oltreMono(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Fact(state.body, OltreColors.textSecondary)
    }
}

@Composable
private fun CommandStep(
    uiState: MemberCommandsUiState,
    step: MemberCommandsStepUiState.Commands,
    actions: MemberCommandsActions,
) {
    val held = uiState.held != null
    // The reading stays at full strength while held: reading is not acting.
    Fact(step.reading, OltreColors.textSecondary, AllianceTestTags.MEMBER_READING)
    step.role?.let { role ->
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Filled(
                text = role.action,
                onClick = { actions.onSetRole(uiState, role) },
                tag = AllianceTestTags.MEMBER_ROLE_ACTION,
                held = held,
                modifier = Modifier.fillMaxWidth(),
            )
            // The whole of the promotion's confirmation, which is why it needs no second step.
            Fact(role.note, OltreColors.textTertiary, AllianceTestTags.MEMBER_ROLE_NOTE)
        }
    }
    // **Outlined danger, never filled, on this step.** Filled red means *the next tap is the
    // irreversible one*, and this tap only asks again.
    DangerButton(
        text = step.kick,
        onClick = actions.onAskKick,
        tag = AllianceTestTags.MEMBER_KICK,
        filled = false,
        held = held,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConfirmStep(
    uiState: MemberCommandsUiState,
    step: MemberCommandsStepUiState.Confirm,
    compact: Boolean,
    actions: MemberCommandsActions,
) {
    val held = uiState.held != null
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Fact(step.consequence, OltreColors.danger, AllianceTestTags.MEMBER_CONSEQUENCE)
        Fact(step.aftermath, OltreColors.textSecondary, AllianceTestTags.MEMBER_AFTERMATH)
    }
    // **At 288dp of content the pair stacks and the destructive action goes last**, which is
    // `DeleteFaceContent`'s own stacking verbatim: on a row the no is first because it is read
    // first, and in a column the thumb is at the bottom.
    //
    // The returned document asks for the reverse at this width — the no last, *"the delete face's
    // own stacking"* — and that reason is the one thing it gets wrong about the face it is citing.
    // Following its stated intent rather than its stated order is what keeps the app's two two-step
    // confirms from stacking opposite ways. Flagged for Davide: if the document's literal reading is
    // what he wants, both faces move together, not this one alone.
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Keep(step.keep, actions.onKeep, Modifier.fillMaxWidth())
            DangerButton(
                text = step.kick,
                onClick = { actions.onKick(uiState) },
                tag = AllianceTestTags.MEMBER_KICK_CONFIRM,
                filled = true,
                held = held,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Keep(step.keep, actions.onKeep, Modifier.weight(1f))
            DangerButton(
                text = step.kick,
                onClick = { actions.onKick(uiState) },
                tag = AllianceTestTags.MEMBER_KICK_CONFIRM,
                filled = true,
                held = held,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── The small pieces ─────────────────────────────────────────────────────────────────────────

@Composable
@NonRestartableComposable
private fun Fact(text: TextRes, color: Color, tag: String = AllianceTestTags.UNNAMED) {
    Text(
        text = text.resolve(),
        color = color,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 17.sp,
        modifier = Modifier.testTag(tag),
    )
}

// **The no is never held.** Dismissing a face the network cannot serve has to keep working, and
// keeping somebody is the one answer that asks the server nothing.
@Composable
@NonRestartableComposable
private fun Keep(text: TextRes, onKeep: () -> Unit, modifier: Modifier) {
    Text(
        text = text.resolve(),
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .testTag(AllianceTestTags.MEMBER_KEEP)
            .heightIn(min = 44.dp)
            .pressable(shape = oltreActionShape, onClick = onKeep)
            .border(1.dp, HAIRLINE, oltreActionShape)
            .padding(horizontal = 11.dp, vertical = 13.dp),
    )
}

// The app's other action treatment, unmodified: ordinary administration, reversible in one tap.
@Composable
@NonRestartableComposable
private fun Filled(
    text: TextRes,
    onClick: () -> Unit,
    tag: String,
    held: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.resolve(),
        color = OltreColors.background,
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .testTag(tag)
            .heightIn(min = 44.dp)
            .let { if (held) it else it.pressable(shape = oltreActionShape, onClick = onClick) }
            .background(OltreColors.accent.copy(alpha = if (held) LOCKED else 1f), oltreActionShape)
            .padding(horizontal = 11.dp, vertical = 13.dp),
    )
}

// Red once as an outline and once filled, and never a third way — `DeleteFace`'s rule, and the
// meaning it now carries: filled red is the tap the person it lands on cannot undo.
@Composable
@NonRestartableComposable
private fun DangerButton(
    text: TextRes,
    onClick: () -> Unit,
    tag: String,
    filled: Boolean,
    held: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = if (held) LOCKED else 1f
    Text(
        text = text.resolve(),
        color = if (filled) OltreColors.background else OltreColors.danger.copy(alpha = alpha),
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .testTag(tag)
            .heightIn(min = 44.dp)
            .let { if (held) it else it.pressable(shape = oltreActionShape, onClick = onClick) }
            .background(
                if (filled) OltreColors.danger.copy(alpha = alpha) else Color.Transparent,
                oltreActionShape,
            )
            .border(
                1.dp,
                OltreColors.danger.copy(alpha = if (filled) 0f else 0.45f * alpha),
                oltreActionShape,
            )
            .padding(horizontal = 11.dp, vertical = 13.dp),
    )
}

private val HAIRLINE = Color.White.copy(alpha = 0.16f)
private val PANEL_EDGE = Color.White.copy(alpha = 0.09f)

// `OltreCardState.HELD`'s own fill — warn at 6% over the background, to the same hex.
private val HELD_FILL = Color(0xFF141111)

// What a control that cannot act drops to. The card and the reading keep full strength.
private const val LOCKED = 0.42f
