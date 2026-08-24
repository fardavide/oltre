package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import kotlinx.coroutines.launch

// **The changelog, as one face of the app's settings sheet.** Claude Design's *A Sky Per Build*,
// accepted 2026-08-23. Contents only, with no `OltreBottomSheet` of its own — the composition root
// raises one sheet and swaps what is inside it, so the changelog is the same drawing whether the
// gear reached it or a new build raised it by itself. That is §4's whole argument, and it is what
// makes "no sheet over a sheet" cost nothing.
//
// **Sideways is taught twice, and neither costs a word.** 18dp of the next card shows past the edge
// of this one — a rounded corner and a fragment of a different sky can only be another page — and the
// rail at the foot says how far the run goes before a finger moves. On the newest release nothing
// peeks to the left, so *the end of the run is drawn by the absence of a peek* and there is no
// first-page case in this file.
@Composable
fun ChangelogSheetContent(
    uiState: ChangelogUiState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { uiState.pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(ChangelogTestTags.SHEET)
            // No top padding: the sheet's own drag handle is the space above the title.
            .padding(bottom = SCREEN_PADDING),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SCREEN_PADDING, vertical = TITLE_GAP),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = uiState.title.resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = TITLE_SIZE,
                fontWeight = FontWeight.SemiBold,
            )
            // **The one number the sheet carries**, and it answers *how deep does this go* before the
            // first swipe. A `3 of 65` counter was refused: it is a fact about the list, and every
            // page already prints a fact about itself in 15sp bold.
            Text(
                text = uiState.depth.resolve(),
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = DEPTH_SIZE,
                modifier = Modifier.testTag(ChangelogTestTags.DEPTH),
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f),
            // Where the peek comes from: the page is narrower than the sheet by twice this, so its
            // neighbours show past both edges.
            contentPadding = PaddingValues(horizontal = if (compact) NARROW_INSET else INSET),
            pageSpacing = if (compact) NARROW_SPACING else SPACING,
            // Or the neighbour composes mid-fling and its sky pops in.
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Bottom,
        ) { page ->
            // **The page is measured, not assumed, and that is a fix rather than a preference.**
            // The first cut derived the column from a constant — 341dp at 393 and 284 in a Slide
            // Over pane — which is right at exactly those two widths and wrong at every other. A
            // 360dp phone is the common Android width and takes the wide branch (`compact` flips
            // *below* 360), so the card there is 286dp of column while the mark was handed 319:
            // `Modifier.size` clamps the Canvas to the card, the sky is still laid out over 319, and
            // **the limb — which spans 0..side by construction — draws past the card's own border**
            // into the gap toward the next page. Nothing clips a `Canvas`. At 560dp, the cap this
            // app puts on every column, it went the other way: 486dp of card holding a 319dp sky.
            //
            // Both frames in this module render at 393 and 320 with the same constants the code
            // used, so the suite could not see any of it.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                ChangelogPage(
                    uiState = uiState.pages[page],
                    // The mark is a square the width of the card's content column — the page less
                    // the card's own padding — so the picture is as wide as the words at every width
                    // the design drew for.
                    //
                    // **And it stops there, which is the height budget defending itself.** The mark
                    // is the only element that pays for the sheet's height, so a wider sheet is a
                    // taller card; at this app's 560dp column cap the sky would be 486dp and the
                    // page would outgrow the viewport it is bottom-aligned in — and nothing here
                    // scrolls, so what a page loses is its *last note*, starved by a `Column` with
                    // no room left. Measured: 560 is the only width where that happens, and it is
                    // the one the design never drew.
                    column = (maxWidth - CARD_PADDING * 2).coerceAtMost(MARK_MAX),
                )
            }
        }

        Box(modifier = Modifier.padding(horizontal = SCREEN_PADDING)) {
            ReleaseRail(
                count = uiState.pages.size,
                // The pager's own fractional offset, so the cap travels with the swipe instead of
                // stepping when a page settles. Nothing here animates on its own.
                position = pager.currentPage + pager.currentPageOffsetFraction,
                stops = uiState.minorLineStops,
                onPick = { index -> scope.launch { pager.animateScrollToPage(index) } },
            )
        }
    }
}

// The design's numbers. The insets are what leave 18dp of the next card showing past this one at
// 393dp and 12dp in a Slide Over pane — the peek that teaches the swipe. The page width they imply
// is *not* a constant here: see the pager above.
private val SCREEN_PADDING = 16.dp
private val TITLE_GAP = 13.dp
private val CARD_PADDING = 11.dp

private val INSET = 26.dp
private val SPACING = 8.dp

// The widest sky the design drew — the column a 393dp phone gives. Past it the mark holds and the
// card goes on widening around it, which is the only way a page that cannot scroll keeps its last
// line on a screen wider than a phone.
private val MARK_MAX = 319.dp

private val NARROW_INSET = 18.dp
private val NARROW_SPACING = 6.dp

private val TITLE_SIZE = 15.sp
private val DEPTH_SIZE = 10.5.sp
