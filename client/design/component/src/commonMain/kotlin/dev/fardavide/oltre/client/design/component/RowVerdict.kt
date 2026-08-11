package dev.fardavide.oltre.client.design.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// What the level is worth to you now, in the slot the adaptation ladder has spent on its shortlist
// since that line shipped. Every row on Colony and Research carries one, and it answers the same
// question in the same shape every time — which is what makes three technologies comparable at a
// glance, and the first time they have been.
//
// **Muted, never accent.** A verdict is a statement, and accent in this app means "go tap this" and
// nothing else. An accent string that is not a target is a worse violation than a demoted one; that
// was settled at 0.0.18 and this line does not reopen it.
//
// One line, always, and it truncates rather than wraps: the row's height budget was set by the
// adaptation row and this design spends exactly that. What a narrow window drops is the second
// clause, chosen by the mapper — see `VerdictUiState`.
@Composable
fun RowVerdict(verdict: VerdictUiState, compact: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (compact) verdict.compactLabel else verdict.label,
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(top = 4.dp),
    )
}

// Two strings rather than one, and the pair is the 320dp rule made into data: a verdict is authored
// as one clause or two, and the second is **dropped** in a Slide Over pane rather than ellipsised
// mid-word — the same call the app already makes when it writes "Deuterium Synth." rather than
// truncating it.
//
// Nothing a dropped clause said is lost: it is repeated in the sheet the row opens. That is the one
// place this design leaks, and it is a knowing leak — the alternative is a "more" glyph on thirteen
// rows, which costs more than the leak does.
data class VerdictUiState(val label: String, val compactLabel: String)
