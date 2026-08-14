package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.StarClass

// **Ten rows against a thousand pages**, and where you decide where to probe next. Not a level you
// pass through — the strip still goes anywhere directly — so nothing here is a step on the way to
// somewhere; it is the one screen that answers *which quarter of the galaxy is worth a probe*.
//
// **The card's argument is that the bias needs no copy at all**, and that is what shapes every
// decision in this file. Twenty-five ticks whose height and alpha are star class say "this is a
// Deep" before a word is read, which is why the kind reaches the player exactly twice — as the last
// word of the generated name, and as the texture of the histogram. There is no kind chip, no badge,
// no pill and no coloured dot, and adding one would be a design change rather than an
// implementation detail.
//
// Two things belong to the caller and are deliberately not re-derived here: the rows arrive **sorted
// nearest first** — the index is a chooser, and coordinate order is what the strip is for — and the
// 16dp screen padding and 560dp cap are the page's, so this draws inside them rather than around
// itself.
@Composable
internal fun RegionIndex(
    galaxy: String,
    scope: String,
    rows: List<RegionRowUiState>,
    onOpenRegion: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The heading the world list, the Colony and Research already spend, with the count as its
        // rule. The rule is where the design's "Galaxy 3 · 10 regions · 250 systems" middot went —
        // `SectionLabel` made that trade once for every list in the app, and a fourth screen writing
        // its own title is how three greys become four.
        SectionLabel(text = galaxy.uppercase(), rule = scope)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            rows.forEach { row -> RegionRow(uiState = row, onOpenRegion = onOpenRegion) }
        }
    }
}

// A region in four lines and a drawing: what it is called, where it is, what it is made of, what
// that is worth, how much of it you have seen, and how far away it starts.
//
// **Three steps of brightness inside 10.5sp is the whole of the card's hierarchy**, and it has to
// survive any later tidying: the bias is the brightest small text here, the strategy fact one step
// down, and the surveyed/nearest pair one step further. Flatten any of the three and the card
// becomes a paragraph.
//
// Nothing here asks for tabular figures, where the design does on three of the four lines: the app
// is one bundled monospaced face and its digits are already one width, so `tnum` would be the first
// font feature in the codebase and would change nothing on any of them.
@Composable
private fun RegionRow(uiState: RegionRowUiState, onOpenRegion: (Int) -> Unit) {
    val mono = oltreMono()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // No test tag: `GalaxyTestTags` carries nothing for a region and this file does not add
            // one. The integrator hangs it here, ahead of the padding as every tagged card in the
            // app does, so the bounds a layout test reads are the card's rather than its interior.
            //
            // `pressable` ahead of the fill and the border, as everywhere else since the Sky pass:
            // it scales what is drawn inside it, and a background declared first is drawn outside.
            // The design specifies no press state at all; this is the one the app already committed
            // to for a card you tap, rather than a new one invented here.
            .testTag(GalaxyTestTags.regionRow(uiState.region))
            .pressable { onOpenRegion(uiState.region) }
            .border(
                1.dp,
                // The active card, which is the only thing `isHome` says about the border — and it
                // is plain white at 9% otherwise, against ticks drawn from #E9EDF5. **Two bases in
                // one card, deliberately not unified**: the border is chrome and the ticks are a
                // reading, and making them one colour would tie a hairline to a star class.
                if (uiState.isHome) OltreColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.09f),
                RoundedCornerShape(14.dp),
            )
            // Opaque rather than white 4.5%, for the reason every card in the app is: the starfield
            // sits behind the content column, and an alpha fill lets stars through where they read
            // as dust on the card's surface.
            .background(oltreCardSurface, RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // **The only place the kind word reaches the player as text** — "Almiaren Deep" — so it
            // truncates rather than wrapping, and the address beside it never yields. A name is
            // recognised from its first characters; a coordinate is useless without its last.
            Text(
                text = uiState.name,
                color = OltreColors.text,
                fontFamily = mono,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                text = uiState.range,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Histogram(ticks = uiState.histogram)
        // The one row that wraps, because it is the one row made of two independent readings rather
        // than of a sentence. 5dp between wrapped lines and 7dp between the parts, so a fact pushed
        // onto a second line still reads as belonging to the bias above it.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = uiState.bias,
                color = OltreColors.text,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.alignByBaseline(),
            )
            // **Two middots on one line in two inks.** This one is the joint between two readings
            // and is faint; the one inside the fact string is part of the sentence and takes the
            // sentence's colour. Same glyph, two jobs, and the joint being faint is what keeps the
            // bias reading as the brighter of the pair.
            Text(
                text = "·",
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.alignByBaseline(),
            )
            // The region's whole strategy in five words, and true from the first launch because star
            // class is charted and charted is free. **"deuterium good" is not deuterium-coloured**:
            // hue is the affordability channel on cost chips and means nothing here.
            Text(
                text = uiState.fact,
                color = OltreColors.textSecondary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.alignByBaseline(),
            )
        }
        // **Both in the same ink, and that is a decision rather than an oversight**: how much of a
        // region you have read and how far it starts are two halves of one appetite, and lifting
        // either would invent a hierarchy the card does not have. "none surveyed" gets no colour of
        // its own for the same reason — a screen of them is what the tab is *for*, so it must read
        // as appetite rather than as error.
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            // No maxLines and no ellipsis, where the name above has both: the card grows by a line
            // rather than losing the end of a count, which is the trade the world row's deposit line
            // already takes.
            Text(
                text = uiState.known,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                text = uiState.nearest,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

// **The bias, drawn.** One tick per system of the region, standing on a common baseline so that
// class reads as height alone — which makes a Deep a visibly short dark run and a Blaze a tall
// bright one, with no legend, no axis and no word of copy.
//
// Columns are equal fractions of whatever width the card is given rather than a fixed pitch: 13.5dp
// a system at 393dp and 10.6dp in a 320dp Slide Over, with a 1dp tick centred in each. The ticks
// stay 1dp at both.
//
// **These constants are this card's and are not the strip's.** `RegionStrip` draws the same idea at
// galaxy scale — 250 systems over 22dp, with its own heights and its own marks for a probe and a
// pin — and the two tables look enough alike that sharing them is the obvious mistake. They are
// different drawings of different densities and they move independently.
@Composable
private fun Histogram(ticks: List<RegionTickUiState>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(BAND_HEIGHT)) {
        val step = size.width / ticks.size
        ticks.forEachIndexed { index, tick ->
            val home = tick.isHome
            val width = (if (home) HOME_TICK_WIDTH else TICK_WIDTH).toPx()
            val height = (if (home) HOME_TICK_HEIGHT else tick.starClass.tickHeight()).toPx()
            drawRect(
                // Accent for your own star, and nothing else on the card takes it — one mark in a
                // field of 25 is what makes home findable without a label. The rest are the text
                // colour at three alphas rather than white: white would be a fourth ink in a card
                // whose whole point is three.
                color = if (home) OltreColors.accent else OltreColors.text.copy(alpha = tick.starClass.tickAlpha()),
                // Grounded on the band's bottom edge. The home tick is a dp taller than the band and
                // therefore **spends that dp in the 7dp gap above rather than being clipped or
                // shortened to fit** — it is the one mark allowed to escape, exactly as the ring is
                // on a world portrait.
                topLeft = Offset(x = (index + 0.5f) * step - width / 2f, y = size.height - height),
                size = Size(width = width, height = height),
            )
        }
    }
}

// Dim is short and faint because that is what dim means — the same reading the strip's ticks give,
// and kept here for the same reason it was kept there: it makes the *desirable* class the least
// visible mark on the card, and inverting it would teach a lie the player then carries forever.
private fun StarClass.tickHeight(): Dp = when (this) {
    StarClass.DIM -> 4.dp
    StarClass.STANDARD -> 8.dp
    StarClass.BRIGHT -> 13.dp
}

// The second half of the same fact. Height alone survives a glance at 10dp a column; alpha alone
// survives a glance at any width. Told twice, a run of 25 is legible in both directions.
private fun StarClass.tickAlpha(): Float = when (this) {
    StarClass.DIM -> 0.30f
    StarClass.STANDARD -> 0.42f
    StarClass.BRIGHT -> 0.66f
}

// The band the classes are measured against: 13dp of bright tick plus the dp that keeps it off the
// line above.
private val BAND_HEIGHT = 14.dp

private val TICK_WIDTH = 1.dp

// Two dp wide against every other tick's one, as well as accent — so home survives being the same
// height as a bright star and survives being read in a hurry.
private val HOME_TICK_WIDTH = 2.dp
private val HOME_TICK_HEIGHT = 15.dp
