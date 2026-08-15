package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// **GalaxyNav with its second row replaced.** The strip and the scope caption survive unchanged; the
// bordered coordinate field and the accent HOME pill do not — two 32dp controls for facts that were
// never decisions, and between them they held the whole top of the tab for a number.
//
// What takes the row is the system's own **name**, and the order below it is load-bearing: name,
// then the demoted coordinate, then the region, then the astronomy line. The design rejected keeping
// the coordinate in the headline position outright — *"a list is scanned down its first column"* —
// and `3:165` in that column is an index where a place should be.
//
// **The region is the only accent string in the header, and that is the entire affordance.** No
// chevron, no underline, no button chrome: accent means *go tap this* everywhere else in the app, so
// one accent word in a block of grey already reads as a way out. It is also why nothing else here
// may take the colour, however much a coordinate would like to.
//
// **What it is a way out to moved at 0.12 and the pixels did not.** It opened the ten-row region
// index; it now opens the fold, framed on the system you were reading — which is the same
// destination stated better, because a band of the drawn galaxy *is* a region and the index existed
// only because ten names would not fit on one dimension.
//
// The component has no background, no border and no card — it sits on the window with the Starfield
// showing through — and no fixed height. 104dp is the budget the design frames it at, not a size:
// the intrinsic is ~88–90dp with a single-line region row, and the row is allowed to wrap.
@Composable
internal fun SystemHead(
    uiState: SystemHeadUiState,
    onSelectGalaxy: (Int) -> Unit,
    onOpenRegion: () -> Unit,
    // **Nothing here spends this, and that is the design's subtraction rather than an oversight.**
    // The pill went with GalaxyNav's second row, and `uiState.isHome` goes unread for the same
    // reason: home is carried by the astronomy line's own words — "your own system · 20m out and
    // back" — rather than by a colour, a weight or a control. Kept in the signature because the way
    // back is a decision above this component, not because this component is waiting to draw one.
    @Suppress("UNUSED_PARAMETER") onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalaxyStrip(
                galaxies = uiState.galaxies,
                onSelectGalaxy = onSelectGalaxy,
                modifier = Modifier.weight(1f),
            )
            // Takes its intrinsic width and the strip absorbs the remainder, which is what keeps the
            // caption's right edge flush without a text alignment anywhere in the row.
            Text(
                text = uiState.scope.uppercase(),
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 9.5.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        Identity(uiState = uiState, onOpenRegion = onOpenRegion)
    }
}

// Four fixed choices and therefore a segmented control, unchanged in what it is for since 0.2.0. The
// count is the model's — the design hardcodes `[1,2,3,4]`, but a component that draws what it is
// handed cannot be wrong about it, and a galaxy outside 1..4 is simply four unselected cells.
//
// **Three of these numbers moved and none of them is a typo.** The shipped GalaxyNav had drifted to
// a 6dp track at 0.06 white with 4dp cells; the design's own figures are a 4dp track at 0.09 with a
// literal 3dp cell inside 2dp of padding. That last 1dp is deliberate concentricity — the chip's
// corner has to sit inside the track's, and matching them reads as a chip that has burst its frame.
@Composable
private fun GalaxyStrip(galaxies: List<GalaxyTabUiState>, onSelectGalaxy: (Int) -> Unit, modifier: Modifier) {
    val mono = oltreMono()
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(4.dp))
            .padding(2.dp),
    ) {
        galaxies.forEach { galaxy ->
            Text(
                text = galaxy.label,
                color = if (galaxy.selected) OltreColors.accent else OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 9.5.sp,
                fontWeight = if (galaxy.selected) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    // No fill at all when unselected rather than a transparent one: the track's own
                    // 0.09 is what an inactive cell shows, and a second layer over it is a chance
                    // for the two to disagree.
                    .then(
                        if (galaxy.selected) {
                            Modifier.background(OltreColors.accent.copy(alpha = 0.22f), RoundedCornerShape(3.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelectGalaxy(galaxy.galaxy) }
                    .testTag(GalaxyTestTags.galaxy(galaxy.galaxy))
                    // Inside the click, so the tap area is the cell rather than the two characters
                    // printed in the middle of it.
                    .padding(vertical = 4.dp),
            )
        }
    }
}

// Three lines, 5dp apart, and every gap in this file is one the mockup measured — 9, 8, 7, 5. The
// design system says in as many words that this is not a 4/8 grid, so none of them rounds.
@Composable
private fun Identity(uiState: SystemHeadUiState, onOpenRegion: () -> Unit) {
    val mono = oltreMono()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        // **Baselines rather than bottoms.** 15sp and 10.5sp bottom-aligned sit a descender apart,
        // which reads as the coordinate having slipped; `alignByBaseline` is what the design means
        // by a subtitle sharing the headline's line. `ResourceRail` aligns its figures the same way.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The only 15sp string and the only `text` one in the component. Not uppercased and not
            // tracked: it is a name, and tracking a name is what turns it back into a label.
            Text(
                text = uiState.system,
                color = OltreColors.text,
                fontFamily = mono,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                // 1.15 of 15sp. One of only two lines here that declares a line height at all — see
                // the note on the astronomy line for why the rest are left to the font.
                lineHeight = 17.25.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            // **The name yields, never the address.** No weight and no wrap, so the coordinate is
            // measured at its full width first and the ellipsis lands in the name — a truncated
            // `Calianov…` is still a place, where `3:16…` is a wrong answer.
            Text(
                text = uiState.coordinate,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline().testTag(GalaxyTestTags.COORDINATE),
            )
        }
        // **Three spans and never one string.** The separator is faint and the detail is muted, so a
        // middot inside `detail` itself — "dim · 5 worlds" — is a *different* grey from the one that
        // joins the region to it. Concatenated, the line would flatten to whichever colour won.
        //
        // It wraps rather than truncating: the detail clause is the only place the world count
        // survives at all since the rows stopped saying "Unsurveyed", so nothing on this line may be
        // clipped to save a pixel.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // **Uppercased here rather than in the model.** The design's own source strings are
            // title case — "Almiaren Deep" is what a region is called — and the shout is a
            // rendering decision, exactly as it is in CSS.
            //
            // The tap target is the drawn line box, ~13dp, against a design system that asks for
            // 44dp: reaching that would need 15.75dp of claim above and below, and the gaps the
            // mockup measured are 5dp each, so every route to 44 moves a baseline the design fixed.
            // The most this block can give without moving one is ~23dp. Left as it is, and flagged
            // rather than fudged — the expansion is a layout decision above this component, and
            // there is no tag for it in `GalaxyTestTags` either.
            Text(
                text = uiState.region.uppercase(),
                color = OltreColors.accent,
                fontFamily = mono,
                fontSize = 9.5.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.testTag(GalaxyTestTags.REGION).clickable(onClick = onOpenRegion),
            )
            Text(
                text = "·",
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 9.5.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = uiState.detail.uppercase(),
                color = OltreColors.textSecondary,
                fontFamily = mono,
                fontSize = 9.5.sp,
                letterSpacing = 1.sp,
            )
        }
        // **Deliberately not uppercased and not tracked, unlike the line directly above it.** That
        // contrast is the hierarchy: the region line is a label and this is a sentence, and shouting
        // it would flatten the block into one grey band. It is one string the mapper composed —
        // per-system, which is the whole reason the world rows no longer carry a round trip each.
        //
        // 1.45 of 10.5sp, because it is the one line here allowed to run to two. Everything else
        // leaves `lineHeight` unset, which hands the line box to JetBrains Mono's own ~1.32em — the
        // metric the design's 104dp budget was measured against, and the single easiest place in
        // this file to drift by declaring something tidier.
        Text(
            text = uiState.astronomy,
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            lineHeight = 15.225.sp,
            modifier = Modifier.fillMaxWidth().testTag(GalaxyTestTags.ASTRONOMY),
        )
    }
}

// Tabular numerals are declared on the coordinate and the astronomy line in the design and are not
// declared anywhere here, for the reason nothing else in the app declares them: the family is
// JetBrains Mono, every glyph is already one advance wide, and a `tnum` feature on a monospaced
// font asks for what it already has.
