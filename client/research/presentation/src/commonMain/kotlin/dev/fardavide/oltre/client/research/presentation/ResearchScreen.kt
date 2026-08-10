package dev.fardavide.oltre.client.research.presentation

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
// **Six rows do not quite fit a phone** — ~788dp against ~683dp of content at 393x852, so this
// scrolls by about 105dp. The 0.3 design said they fit, on a 74dp row; the real row is 106dp. The
// figure to re-derive any future budget from is the one in `ResearchUiState`, not the sheet's.
@Composable
fun ResearchScreen(
    uiState: ResearchUiState,
    onStartResearch: (Technology) -> Unit,
    onStartAdaptation: (AdaptationTechnology) -> Unit,
    // Hoisted since the Sky pass — see the same parameter on `ColonyScreen`.
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A width decision, not a change of voice: below this the effect line drops its trailing
        // noun and the section rule shortens. Measured on the window rather than on the capped
        // column, because it is the window that is a Slide Over pane.
        val compact = maxWidth < OltreLayout.compactWidth
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
                    // The single-slot rule has to be legible without a tap, and the label's
                    // trailing slot is the one place it costs nothing.
                    rule = if (compact) "one at a time" else "one project at a time",
                )
                TechnologyList(
                    technologies = uiState.technologies,
                    compact = compact,
                    onStartResearch = onStartResearch,
                )
                // 22dp — the value that clears the fleet strip on Colony, which is what the system
                // already spends to mean "different subject". Not a divider and not a hairline:
                // the two branches are one list, and a rule through it would make them two. A
                // Spacer rather than a modifier on the label, because `SectionLabel` renders the
                // Colony's baselines too and this slice may not move its measurements.
                Spacer(modifier = Modifier.height(22.dp))
                SectionLabel(
                    text = "ADAPTATION",
                    // A pointer rather than a rule of its own, and unchanged at 320dp because
                    // "slot" is the shortest true noun and has nothing to cut. Repeating "one
                    // project at a time" here would read as one of each — the exact
                    // misunderstanding the rule above exists to prevent.
                    rule = "the same slot",
                )
                AdaptationList(
                    ladders = uiState.adaptation,
                    compact = compact,
                    onStartAdaptation = onStartAdaptation,
                )
            }
        }
    }
}
