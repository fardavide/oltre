package dev.fardavide.oltre.client.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.fardavide.oltre.client.design.component.RefusalBlock
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.TextRes

// **Two faces deep, and red arrives as an outline before it is ever filled.** The warning face is all
// reading and no consequence; the last step carries the only filled red button in the product and
// names the *colony* rather than the account, because the colony is the thing being lost.
//
// Chrome and contents are split for `AlertSheetContent`'s reason: the composition root raises one
// sheet and swaps what is in it, so a wrapper here would be a second way to raise the same panel.
@Composable
@NonRestartableComposable
fun DeleteFaceContent(
    uiState: DeleteFaceUiState,
    compact: Boolean,
    onKeep: () -> Unit,
    onAct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DeleteTestTags.FACE)
            // It scrolls for the settings face's reason and one more of its own: the four fact rows
            // wrap their values at 320dp, and a colony with a long name makes the first of them two
            // lines. A sheet that clipped the button that says *Keep it* would be the worst possible
            // place for this app to run out of room.
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = uiState.title.resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Body(text = uiState.intro, color = OltreColors.textSecondary)
        }

        // **One card with hairlines, not four cards**, which is the settings panel's rule and the same
        // reading of it: this is one account said four ways rather than four decisions.
        if (uiState.facts.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .border(1.dp, PANEL_EDGE, oltreCardShape)
                    .background(oltreCardSurface, oltreCardShape)
                    .padding(horizontal = 12.dp),
            ) {
                uiState.facts.forEachIndexed { index, fact ->
                    FactRow(fact = fact, first = index == 0, compact = compact)
                }
            }
        }

        // Body colour rather than muted, and it is the one line on the face that takes it: everything
        // above is what exists, and this is what will not come back.
        Body(text = uiState.second, color = OltreColors.text)

        uiState.refusal?.let {
            RefusalBlock(lead = it.lead, body = it.body, modifier = Modifier.testTag(DeleteTestTags.REFUSAL))
        }

        Spacer(modifier = Modifier.height(6.dp))

        // At 288dp of content the pair stacks and **the destructive action goes last**, which is the
        // reverse of the row: on a row *Keep it* is first because it is read first, and in a column
        // the thumb is at the bottom.
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.keep?.let { KeepButton(label = it, onKeep = onKeep, modifier = Modifier.fillMaxWidth()) }
                ActionButton(uiState = uiState, onAct = onAct, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.keep?.let { KeepButton(label = it, onKeep = onKeep, modifier = Modifier.weight(1f)) }
                ActionButton(uiState = uiState, onAct = onAct, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
@NonRestartableComposable
private fun FactRow(fact: DeleteFactUiState, first: Boolean, compact: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!first) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HAIRLINE))
        // **The label column survives at 320 and the value wraps under it**, which is the ladder's own
        // move: nothing is cut, the row stacks. Every number and every name is kept, which is the rule.
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                FactLabel(fact.label, modifier = Modifier)
                FactValue(fact.value)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FactLabel(fact.label, modifier = Modifier.width(74.dp))
                FactValue(fact.value)
            }
        }
    }
}

@Composable
@NonRestartableComposable
private fun FactLabel(label: TextRes, modifier: Modifier) {
    Text(
        text = label.resolve(),
        color = OltreColors.textTertiary,
        fontFamily = oltreMono(),
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
@NonRestartableComposable
private fun FactValue(value: TextRes) {
    Text(
        text = value.resolve(),
        color = OltreColors.text,
        fontFamily = oltreMono(),
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

@Composable
@NonRestartableComposable
private fun KeepButton(label: TextRes, onKeep: () -> Unit, modifier: Modifier) {
    Text(
        text = label.resolve(),
        color = OltreColors.text,
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .testTag(DeleteTestTags.KEEP)
            .pressable(shape = oltreActionShape, onClick = onKeep)
            .border(1.dp, HAIRLINE, oltreActionShape)
            .padding(vertical = 14.dp),
    )
}

// Red once as an outline and once filled, and never a third way. The ghost is *an action that is not
// yet the action*, which is exactly what the warning face's button is.
@Composable
@NonRestartableComposable
private fun ActionButton(uiState: DeleteFaceUiState, onAct: () -> Unit, modifier: Modifier) {
    Text(
        text = uiState.action.resolve(),
        color = if (uiState.destructive) OltreColors.background else OltreColors.danger,
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .testTag(DeleteTestTags.ACTION)
            .pressable(shape = oltreActionShape, onClick = onAct)
            .background(
                if (uiState.destructive) OltreColors.danger else Color.Transparent,
                oltreActionShape,
            )
            .border(1.dp, OltreColors.danger.copy(alpha = if (uiState.destructive) 0f else 0.45f), oltreActionShape)
            .padding(vertical = 14.dp),
    )
}

@Composable
@NonRestartableComposable
private fun Body(text: TextRes, color: Color) {
    Text(
        text = text.resolve(),
        color = color,
        fontFamily = oltreMono(),
        fontSize = 11.sp,
        lineHeight = 18.sp,
    )
}

private val HAIRLINE = Color.White.copy(alpha = 0.16f)
private val PANEL_EDGE = Color.White.copy(alpha = 0.09f)
