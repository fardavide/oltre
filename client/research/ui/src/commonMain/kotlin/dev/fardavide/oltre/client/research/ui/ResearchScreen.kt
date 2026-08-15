package dev.fardavide.oltre.client.research.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.component.RowSheet
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.Technology

// Two branches, six technologies, one project at a time between them. One list with a seam in it —
// not two screens, and not a screen with a switch on it. The seam should read the way the gap
// between the facility list and the fleet strip reads on Colony: a change of subject inside one
// instrument, rather than a change of place.
//
// Both branches are always visible, and that is the decision rather than a consequence of it: when
// a project is in flight the five rows that cannot start read the same wait and the sixth counts it
// down, on one screen, where the two numbers verify each other with nothing added to carry the
// explanation. A segmented control would buy three rows at a time, which is nothing, and spend a
// component the app does not have, a third fixed band in the vertical budget, and the locked branch
// behind a tap. Past the content cap the rows still get air rather than columns.
//
// **Six rows do not quite fit a phone** — ~734dp against ~683dp of content at 393x852, so this
// scrolls by about 50dp. The 0.3 design said they fit, on a 74dp row; the real row is 97dp. The
// figure to re-derive any future budget from is the one in `ResearchUiState`, not the sheet's.
@Composable
fun ResearchScreen(
    uiState: ResearchUiState,
    onStartResearch: (Technology) -> Unit,
    onStartAdaptation: (AdaptationTechnology) -> Unit,
    onToggleTechnologyWatch: (Technology) -> Unit,
    onToggleAdaptationWatch: (AdaptationTechnology) -> Unit,
    // Hoisted since the Sky pass — see the same parameter on `ColonyScreen`.
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A width decision, not a change of voice: below this a verdict drops its second clause and
        // the section rule shortens. Measured on the window rather than on the capped column,
        // because it is the window that is a Slide Over pane.
        val compact = maxWidth < OltreLayout.compactWidth
        // Held here rather than in the shell, and it is the reason `App.kt` keeps the parameter list
        // it has: what a sheet says is derived from the row ui-state this screen was already handed,
        // so nothing new crosses the module boundary and no callback is added to say "open this".
        var open by remember { mutableStateOf<OpenSheet?>(null) }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = OltreLayout.maxContentWidth)
                    .fillMaxWidth()
                    // Ahead of the padding: a tag placed after it marks the padded interior, so
                    // the bounds a test reads would be 32dp narrower than the column itself.
                    .testTag(ResearchTestTags.CONTENT)
                    .padding(16.dp),
            ) {
                SectionLabel(
                    text = "TECHNOLOGIES",
                    // Two slot rules want this one slot, and while a watch exists it wins. **The
                    // watch is the one that can change without the player looking** — it is shared
                    // with the Colony screen, so tapping a square there silently takes it off a row
                    // here, and the only defence against that is naming it where it can be read.
                    // "One project at a time" is learned in the first minute and is still legible
                    // from the five rows reading the same wait; the watch is legible from nothing.
                    rule = uiState.watching ?: if (compact) "one at a time" else "one project at a time",
                )
                TechnologyList(
                    technologies = uiState.technologies,
                    compact = compact,
                    onStartResearch = onStartResearch,
                    onToggleWatch = onToggleTechnologyWatch,
                    onOpenDetail = { open = OpenSheet.Project(it) },
                )
                // 22dp — the value that clears the fleet strip on Colony, which is what the system
                // already spends to mean "different subject". Not a divider and not a hairline:
                // the two branches are one list, and a rule through it would make them two. A
                // Spacer rather than a modifier on the label, because `SectionLabel` renders the
                // Colony's baselines too and this slice may not move its measurements.
                Spacer(modifier = Modifier.height(22.dp))
                SectionLabel(
                    text = "ADAPTATION",
                    // **A rule of its own since 0.12.1, where it used to be a pointer at the one
                    // above.** This read "the same slot" for eleven versions and the sentence was
                    // load-bearing: it existed to prevent a player reading the two headings as one
                    // of each. That reading is now the correct one, so the label says what this
                    // branch's own rule is, in the same words and the same shape as TECHNOLOGIES —
                    // which is what makes the pair legible as two rules rather than one repeated.
                    //
                    // PLACEHOLDER copy, like every other string this screen says: what the game says
                    // to a player is Davide's. The shape is not placeholder — a section whose rule
                    // is silently wrong is worse than one whose wording is provisional.
                    rule = if (compact) "one at a time" else "one ladder at a time",
                )
                AdaptationList(
                    ladders = uiState.adaptation,
                    compact = compact,
                    onStartAdaptation = onStartAdaptation,
                    onToggleWatch = onToggleAdaptationWatch,
                    onOpenDetail = { open = OpenSheet.Ladder(it) },
                )
            }
        }
        // Outside the scrolling column, because the sheet is not part of the list — it is what the
        // list opens. Cleared on every way out, and cleared before the action fires so the screen
        // the player lands back on is the one their tap changed.
        when (val sheet = open) {
            null -> Unit
            is OpenSheet.Project -> RowSheet(
                uiState = uiState.technologies.first { it.technology == sheet.technology }.sheet,
                onAct = {
                    open = null
                    onStartResearch(sheet.technology)
                },
                onDismiss = { open = null },
                contentModifier = Modifier.testTag(ResearchTestTags.SHEET),
                actionModifier = Modifier.testTag(ResearchTestTags.SHEET_ACTION),
            )
            is OpenSheet.Ladder -> RowSheet(
                uiState = uiState.adaptation.first { it.technology == sheet.technology }.sheet,
                onAct = {
                    open = null
                    onStartAdaptation(sheet.technology)
                },
                onDismiss = { open = null },
                contentModifier = Modifier.testTag(ResearchTestTags.SHEET),
                actionModifier = Modifier.testTag(ResearchTestTags.SHEET_ACTION),
            )
        }
    }
}

// Which row's sheet is open, held as the row's identity rather than as the sheet itself: the ui-state
// is rebuilt on every tick, and a sheet captured at the moment of the tap would stop counting down
// with the row behind it. Shaped like `FinishedWhileAway` next door because it names the same two
// branches, and sealed so the footer's button cannot start a ladder through the applied callback.
private sealed interface OpenSheet {

    data class Project(val technology: Technology) : OpenSheet

    data class Ladder(val technology: AdaptationTechnology) : OpenSheet
}
