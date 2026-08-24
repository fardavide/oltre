package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve

// **One release, and the card hugs it.** Claude Design's *A Sky Per Build* §1: a full-height sheet
// over five lines of copy is 90% air however it is arranged, so the page does not fill it — the card
// is as tall as its release and sits at the foot of the viewport, and the slack collects under the
// title where the sheet is empty anyway.
//
// **A one-note page is a short card rather than an empty one**, which is the whole reason the height
// is not fixed. The two things that must not jump between pages are the rail underneath and the
// reading position of the last note; bottom-aligning is what holds both, at the cost of the version
// line sitting lower on a sparse release than on a dense one — visible only mid-swipe, where it
// reads as one card being shorter than another, which is true.
//
// Reading down: the sky, the limb across the foot of it, the version with the date at the other end
// of the same line, the headline, then one to three notes ruled off from each other. **The limb is
// the only separator the page needs** between the picture and the copy — it is already in every mark
// and it draws the line for free.
//
// `@NonRestartableComposable` for `AlertSheet`'s reason, measured there: this reads nothing and
// forwards what it is handed, so a restart scope of its own could do nothing its caller's does not,
// and the skipping machinery Compose would generate is branches against a coverage gate with no
// slack.
@Composable
@NonRestartableComposable
fun ChangelogPage(uiState: ChangelogPageUiState, column: Dp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ChangelogTestTags.page(uiState.version))
            .border(1.dp, HAIRLINE, oltreCardShape)
            .background(oltreCardSurface, oltreCardShape)
            .padding(CARD_PADDING),
    ) {
        // The one element that pays for the sheet's height, and it pays for all of it: a square the
        // full width of the column.
        VersionMark(
            version = uiState.version,
            size = column,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = MARK_GAP),
        )

        // **The version leads because it is what the page is**, and the date is a caption at the far
        // end of the same line rather than a chip — a chip is an affordance, and the date does
        // nothing.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = uiState.version.printed,
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = VERSION_SIZE,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = uiState.date.resolve(),
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = DATE_SIZE,
                maxLines = 1,
            )
        }

        // **A fixed slot rather than a fixed line count**, and the difference is what stops the notes
        // moving between pages: 40 characters is one line at 393dp and two at 320, so the headline
        // owns two lines' worth of height everywhere and a short one simply leaves the second empty.
        Text(
            text = uiState.headline.resolve(),
            color = OltreColors.text,
            fontFamily = oltreMono(),
            fontSize = HEADLINE_SIZE,
            fontWeight = FontWeight.SemiBold,
            lineHeight = HEADLINE_LINE,
            modifier = Modifier.padding(top = HEADLINE_GAP).heightIn(min = HEADLINE_SLOT),
        )

        // **Hairlines inside one card, never three cards** — the settings sheet's own move for its
        // seven switches, and for the same reason: this is one release said three ways rather than
        // three things.
        for (note in uiState.notes) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = NOTE_GAP).height(1.dp).background(HAIRLINE))
            Text(
                text = note.resolve(),
                color = OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = NOTE_SIZE,
                lineHeight = NOTE_LINE,
                modifier = Modifier.padding(top = NOTE_GAP),
            )
        }
    }
}

// The design's numbers. The card's own padding is the app's card padding, unchanged, which is what
// makes the column 319dp at 393 and 262 in a Slide Over pane.
private val CARD_PADDING = 11.dp
private val MARK_GAP = 5.dp
private val HEADLINE_GAP = 5.dp
private val NOTE_GAP = 9.dp

private val VERSION_SIZE = 15.sp
private val DATE_SIZE = 10.5.sp
private val HEADLINE_SIZE = 12.5.sp
private val HEADLINE_LINE = 17.5.sp
private val NOTE_SIZE = 11.sp
private val NOTE_LINE = 17.sp

// Two lines of headline, so the notes under it never move between one page and the next.
private val HEADLINE_SLOT = 36.dp

// The card's own edge and the rule between two notes are the same hairline, which is what makes
// three notes read as one card rather than as a stack.
private val HAIRLINE = Color.White.copy(alpha = 0.09f)
