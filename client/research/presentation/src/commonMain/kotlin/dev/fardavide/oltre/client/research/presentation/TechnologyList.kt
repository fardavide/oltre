package dev.fardavide.oltre.client.research.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.CostChip
import dev.fardavide.oltre.client.design.component.ProgressBar
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.Technology

// The facility list is the model and the row adds exactly one line to it. Reading order is
// unchanged: identity, then consequence, then price.
@Composable
internal fun TechnologyList(
    technologies: List<TechnologyRowUiState>,
    compact: Boolean,
    onStartResearch: (Technology) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        technologies.forEach { row ->
            TechnologyRow(row = row, compact = compact, onStartResearch = onStartResearch)
        }
    }
}

@Composable
private fun TechnologyRow(
    row: TechnologyRowUiState,
    compact: Boolean,
    onStartResearch: (Technology) -> Unit,
) {
    val mono = oltreMono()
    val locked = row.action is ResearchActionUiState.Locked
    val running = row.action as? ResearchActionUiState.Running
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.42f else 1f)
            .border(
                1.dp,
                if (running != null) OltreColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.09f),
                RoundedCornerShape(14.dp),
            )
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
            .testTag(ResearchTestTags.row(row.technology))
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Every technology name is one word, so nothing here truncates at 320dp — but
                    // the guard stays, because a future name is not this slice's promise to keep.
                    Text(
                        text = row.name,
                        color = OltreColors.text,
                        fontFamily = mono,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "LV ${row.level.value}",
                        color = OltreColors.textSecondary,
                        fontFamily = mono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(start = 7.dp)
                            .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                when (val action = row.action) {
                    is ResearchActionUiState.Locked -> Text(
                        text = action.reason,
                        color = OltreColors.textSecondary,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // In flight the effect line is replaced, exactly as a facility row drops its
                    // costs while it builds: what you want mid-project is when, not what — and
                    // "→ LV 4" already says what.
                    is ResearchActionUiState.Running -> Text(
                        text = "→ LV ${action.toLevel.value} · ${action.doneAt}",
                        color = OltreColors.accent,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    ResearchActionUiState.Start,
                    is ResearchActionUiState.AvailableIn,
                    -> {
                        EffectLine(effect = row.effect, compact = compact)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            row.costs.forEach { chip -> CostChip(chip = chip) }
                            Text(
                                text = row.duration,
                                color = OltreColors.textSecondary,
                                fontFamily = mono,
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                }
            }
            when (val action = row.action) {
                ResearchActionUiState.Start -> Text(
                    text = "Research",
                    color = Color.White,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(OltreColors.accent, RoundedCornerShape(9.dp))
                        .clickable { onStartResearch(row.technology) }
                        .testTag(ResearchTestTags.action(row.technology))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                is ResearchActionUiState.AvailableIn -> Text(
                    text = action.label,
                    color = OltreColors.textTertiary,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(9.dp))
                        .testTag(ResearchTestTags.action(row.technology))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                is ResearchActionUiState.Running -> Text(
                    text = action.countdown,
                    color = OltreColors.text,
                    fontFamily = mono,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .testTag(ResearchTestTags.action(row.technology)),
                )
                is ResearchActionUiState.Locked -> Unit
            }
        }
        if (running != null) {
            ProgressBar(percent = running.progressPercent)
        }
    }
}

// "+36% → +47% metal · crystal output": current in secondary weight, next in body weight, using
// the arrow that already means "becomes" in "→ LV 13".
@Composable
private fun EffectLine(effect: EffectUiState, compact: Boolean) {
    val mono = oltreMono()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        effect.current?.let { current ->
            Text(text = current, color = OltreColors.textSecondary, fontFamily = mono, fontSize = 10.5.sp)
        }
        Text(text = "→", color = OltreColors.textTertiary, fontFamily = mono, fontSize = 10.5.sp)
        Text(
            text = effect.next,
            color = OltreColors.text,
            fontFamily = mono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (compact) effect.compactSubject else effect.subject,
            color = OltreColors.textSecondary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
