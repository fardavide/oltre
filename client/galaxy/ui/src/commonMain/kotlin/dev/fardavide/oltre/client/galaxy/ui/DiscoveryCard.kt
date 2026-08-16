package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.world.ui.WorldPortrait

// **The survey moment, and a section of the ledger rather than a layer over it.** A card you must
// dismiss is a tax on the one thing the app promises, and three of them is that tax three times — so
// there is no scrim, no close control and no shadow here, and scrolling past is the dismissal.
// Claude Design's frames, 2026-08-14.
//
// ── Three things here are load-bearing and easy to get backwards ─────────────────────────────────
//
// 1. **The accent border marks the FULL card, not the compact one.** It reads inverted until you
//    say what it claims: accent-at-45% is the pair `OltreCardState.RUNNING` already spends on the
//    one lit thing on a screen, and here that claim is *this is the survey you just ran*. Two or
//    more discoveries and none of them is that, so the compact card takes the ordinary hairline.
//    No **string** on this card takes the accent, and none may: nothing here is tappable — no
//    chevron, no verb, no hazard word — the card is a readout and the ledger row underneath is
//    where a world is acted on.
// 2. **The compact disc is 44dp and therefore on the SMALL side of `WorldPortrait`'s `box >= 60`
//    gate.** It silently drops the ring, the storm cell, the fracture lines and the craters, and
//    caps its banding at two. That is the intent rather than a casualty — at 44dp those marks are
//    sub-pixel and read as noise, which is the same argument `WorldPortrait` makes for itself.
// 3. **The leadings are written out to three decimals because the card's height falls out of
//    them.** 132dp of content on the full card and 85.85dp on the compact one are the sums of
//    exactly these line boxes and the 7dp/11dp gaps between them; rounding 15.225 to 15 moves a
//    baseline the screenshots have recorded.
//
// Spacing is the design's measured 5 / 7 / 8 / 11, not a 4/8 grid — see the token file. Do not
// round 7 to 8.
//
// Two things left open on purpose. **No test tag**: `GalaxyTestTags` has no constant that names a
// discovery, and the ledger is what decides whether the section or each card is the node a robot
// looks for — so tagging is the integrator's, not this file's. And **no explicit tabular figures**:
// the design sets `font-variant-numeric: tabular-nums` belt-and-braces over a family that is
// already monospaced, and nothing in this app sets it, so following the repo keeps one convention
// rather than starting a second.
@Composable
internal fun DiscoveryCard(uiState: DiscoveryCardUiState, compact: Boolean, modifier: Modifier = Modifier) {
    // Opaque, never an alpha: the starfield sits behind the content column and a translucent card
    // lets stars through, where they read as dust on its surface. `oltreCardSurface` is that
    // composite precomputed, and the shape is the same 14dp radius every card in the app has.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (compact) HAIRLINE else LIVE_EDGE, oltreCardShape)
            .background(oltreCardSurface, oltreCardShape)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            // The text column outgrows the disc in both variants, so this decides where the disc
            // sits against it: beside the first two lines when there are five, centred when there
            // are three.
            verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            WorldPortrait(uiState = uiState.portrait, box = if (compact) 44.dp else 96.dp)
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // **The name truncates and the coordinate does not**, which at 320dp is the
                    // whole point of the row: a clipped name is still recognisable and a clipped
                    // address is a different address.
                    Text(
                        text = uiState.world,
                        color = OltreColors.text,
                        fontFamily = oltreMono(),
                        fontSize = if (compact) 13.5.sp else 15.sp,
                        lineHeight = if (compact) 15.525.sp else 17.25.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                    // No leading of its own — it takes the row's, which is what keeps it on the
                    // name's baseline at both title sizes.
                    Text(
                        text = uiState.coordinate,
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                // The one line in the header block allowed to wrap: an epithet is a phrase rather
                // than a figure, and clipping it would lose the noun that names the world's kind.
                Text(
                    text = uiState.epithet,
                    color = OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = 11.sp,
                    lineHeight = 15.4.sp,
                )
                if (compact) {
                    // A single string, not three Texts and not a FlowRow: the separator is the
                    // middle dot with **ordinary** spaces, while the non-breaking space inside each
                    // reading — `1.06 g` — arrives already set from `presentation`, so a value and
                    // its unit can never be split across a line.
                    Text(
                        text = "${uiState.temperature} · ${uiState.gravity} · ${uiState.pressure}",
                        color = OltreColors.text,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                        lineHeight = 14.7.sp,
                    )
                } else {
                    // 5dp on top of the column's own 7dp: the axis block is a second paragraph
                    // rather than a fourth line of the header, and 12dp is what says so.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    ) {
                        // Three, always, in this order — the same order `WorldPortrait` reads them
                        // in, so the column is a key to the disc beside it.
                        Axis(label = "temperature", value = uiState.temperature)
                        Axis(label = "gravity", value = uiState.gravity)
                        Axis(label = "pressure", value = uiState.pressure)
                    }
                }
            }
        }
        // **A weighted Row rather than a wrapping one, and the difference is one case.** The design
        // lets this row break so that a long note takes the full width and the stamp drops beneath
        // it, right-aligned; a weight keeps the stamp on the first line and narrows the note by its
        // width instead. Both keep the stamp at the right and the note intact, the break needs a
        // note about twice the length of any the mapper writes, and a Row is what every other pair
        // of a sentence and a trailing figure in this app already is.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // The verdict sentence, and the only thing on the card written in words rather than
            // read off the world.
            Text(
                text = uiState.note,
                color = OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                lineHeight = 15.225.sp,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                text = uiState.found,
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                lineHeight = 15.225.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

// **Lowercase, unspaced, and deliberately not a `SectionLabel`.** Three uppercase letterspaced
// headings stacked five dp apart would read as three sections of a screen rather than as the key to
// one disc — so the label is written at the same size and weight as the value it introduces and
// separated from it by colour alone.
@Composable
private fun Axis(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        // A hard 74dp that neither grows nor shrinks, measured rather than derived: "temperature"
        // is eleven characters of 10.5sp mono, about 69dp, so the longest of the three fits with
        // room to spare and all three values start on the same x at 320dp as at 393dp. Sized from
        // content instead, the column would move whenever the copy did.
        Text(
            text = label,
            color = OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            lineHeight = 14.7.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(74.dp).alignByBaseline(),
        )
        Text(
            text = value,
            color = OltreColors.text,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            lineHeight = 14.7.sp,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
    }
}

// Accent at 45%, which is `OltreCardState.RUNNING`'s edge and `DispatchSheet`'s selected control —
// see note 1. It is a border rather than a string, which is what keeps it inside the rule that
// accent means "go tap this": nothing on this card is a target.
private val LIVE_EDGE = OltreColors.accent.copy(alpha = 0.45f)

// The ordinary card edge, the same white 9% every unlit surface in the app carries.
private val HAIRLINE = Color.White.copy(alpha = 0.09f)
