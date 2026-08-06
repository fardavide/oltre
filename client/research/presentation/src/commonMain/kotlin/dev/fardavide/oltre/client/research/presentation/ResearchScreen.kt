package dev.fardavide.oltre.client.research.presentation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.OltreColors
import dev.fardavide.oltre.client.design.OltreLayout
import dev.fardavide.oltre.client.design.oltreMono
import dev.fardavide.oltre.core.Technology

// One branch, three technologies, one project at a time. Three rows leave most of a phone empty
// and that stays empty: a branch that fills the screen is a branch with filler in it. Past the
// content cap the rows get air rather than columns — nothing reflows and nothing new appears.
@Composable
fun ResearchScreen(
    uiState: ResearchUiState,
    onStartResearch: (Technology) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A width decision, not a change of voice: below this the effect line drops its trailing
        // noun and the section rule shortens. Measured on the window rather than on the capped
        // column, because it is the window that is a Slide Over pane.
        val compact = maxWidth < OltreLayout.compactWidth
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
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
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, rule: String) {
    val mono = oltreMono()
    Row(modifier = Modifier.padding(bottom = 9.dp, start = 2.dp)) {
        Text(
            text = text,
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Text(
            text = " · $rule",
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            maxLines = 1,
        )
    }
}
