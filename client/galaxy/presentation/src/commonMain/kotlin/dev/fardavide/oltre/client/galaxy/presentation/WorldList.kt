package dev.fardavide.oltre.client.galaxy.presentation

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// Every world is the same card: the coordinate, the verdict word, the orbit band on the right, and
// whatever the verdict earns beneath it. The verdict word takes the only colour on the row.
@Composable
internal fun WorldList(
    bands: List<OrbitBandUiState>,
    compact: Boolean,
    onOpenResearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        bands.forEach { band ->
            SectionLabel(text = band.band.heading.uppercase())
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 13.dp),
            ) {
                band.rows.forEach { row ->
                    WorldRow(row = row, compact = compact, onOpenResearch = onOpenResearch)
                }
            }
        }
    }
}

@Composable
private fun WorldRow(row: WorldRowUiState, compact: Boolean, onOpenResearch: () -> Unit) {
    // The one row that is not a card. It is not tappable, so it does not get the surface every
    // tappable thing in the app has — a hairline and no fill instead.
    if (row.verdict is VerdictUiState.Relay) {
        RelayRow(row = row, relay = row.verdict)
        return
    }

    val home = row.verdict is VerdictUiState.Home
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (home) OltreColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.09f),
                RoundedCornerShape(14.dp),
            )
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(14.dp))
            .testTag(GalaxyTestTags.row(row.slot))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Header(row = row, compact = compact)
        // At 320dp the yield leaves the header rather than being cut by it: a coordinate, a verdict
        // word, a yield and an orbit tag do not fit on one line at that width, and the header's
        // ellipsis would land on the *number* — "BARREN yield 0…". Abbreviation may drop a noun; it
        // may never truncate a figure, which is the one thing on the row a player is comparing.
        val movedYield = if (compact) row.verdict.yieldLabel() else null
        movedYield?.let { Detail(text = it, color = OltreColors.textSecondary) }
        when (val verdict = row.verdict) {
            is VerdictUiState.Home -> {
                Detail(text = verdict.axes, color = OltreColors.textSecondary)
                Detail(text = verdict.detail, color = OltreColors.textTertiary)
            }
            is VerdictUiState.Occupied -> Detail(text = verdict.holder, color = OltreColors.textSecondary)
            // Two facts, both free: the coordinate and the orbit, both already in the header. The
            // shortest card in the app, because a screen of them is the normal case.
            VerdictUiState.Unsurveyed -> Unit
            is VerdictUiState.Blocked -> {
                BlockedAxes(slot = row.slot, failures = verdict.failures, onOpenResearch = onOpenResearch)
                Detail(text = verdict.calibration, color = OltreColors.textSecondary)
                Detail(text = verdict.detail, color = OltreColors.textTertiary)
            }
            is VerdictUiState.Barren -> {
                Detail(text = verdict.threshold, color = OltreColors.textSecondary)
                Detail(text = verdict.detail, color = OltreColors.textTertiary)
            }
            is VerdictUiState.Settleable -> {
                Detail(text = verdict.richness, color = OltreColors.textSecondary)
                Detail(text = verdict.detail, color = OltreColors.textTertiary)
            }
            is VerdictUiState.Relay -> Unit // answered above, before the card
        }
    }
}

@Composable
private fun Header(row: WorldRowUiState, compact: Boolean) {
    val mono = oltreMono()
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = row.coordinate,
            color = OltreColors.text,
            fontFamily = mono,
            fontSize = 13.5.sp,
            maxLines = 1,
            softWrap = false,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        ) {
            Text(
                text = row.verdict.word(),
                color = row.verdict.hue(),
                fontFamily = mono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                maxLines = 1,
                softWrap = false,
            )
            row.verdict.yieldLabel().takeIf { !compact }?.let { label ->
                Text(
                    text = label,
                    color = OltreColors.textTertiary,
                    fontFamily = mono,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    // Never reached at either width the app ships against — the compact branch
                    // above moves this line out of the header rather than letting it be cut.
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = row.band.label.uppercase(),
            color = row.band.hue(),
            fontFamily = mono,
            fontSize = 9.5.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// One line per failing axis: the axis and its two readings on the left, the technology flush right
// in its own column. The brief's sentence — "gravity 2.4 g, you tolerate 1.45 g, Gravitic
// Adaptation 8 would land it" — wraps to three lines at 393dp, and three of those is the wall this
// row must not be. It keeps the clause order and loses the verb.
@Composable
private fun BlockedAxes(slot: Int, failures: List<BlockedAxisUiState>, onOpenResearch: () -> Unit) {
    val mono = oltreMono()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        failures.forEach { failure ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${failure.axis} ${failure.reading}, you tolerate ${failure.tolerated}",
                    color = OltreColors.textSecondary,
                    fontFamily = mono,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                // Accent again, and tappable — one decision rather than two. 0.0.16 demoted this to
                // textSecondary for exactly one reason, that Research could not sell what the string
                // named, and this slice ends that reason. But restoring the colour alone would break
                // the rule harder than the demotion did: accent is the screen's only "go tap this"
                // signal, so an accent string that is not a target is worse than a tertiary string
                // that is not a target. Ship both or ship neither.
                //
                // The target is the string, not the row: the row belongs to the world — survey now,
                // claim later — and a whole-row deep link would take that away from the thing the
                // player usually wants. It selects the Research tab and stops there. Under a second
                // section on one screen the ADAPTATION rows are already on screen when they arrive,
                // so there is no scroll target, no highlighted row and no arrival state to design.
                Text(
                    text = failure.label,
                    color = OltreColors.accent,
                    fontFamily = mono,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    softWrap = false,
                    // **The padding is inside the clickable, and that ordering is the whole point.**
                    // A 10.5sp line box is 15dp tall; every other target in the app is 30–32dp —
                    // the Research button is 11sp plus 7dp of vertical padding, the steppers are
                    // 32dp square. Put the padding before `clickable` and the hit rectangle is the
                    // glyphs alone, which on the delivery target is a third of the 44pt iOS
                    // minimum. Accent means "go tap this"; a target this small is the promise
                    // breaking on contact, and a near miss lands on a card that is deliberately
                    // not clickable, so nothing at all happens.
                    //
                    // The vertical padding is 6dp rather than the 7dp the Research button uses, so
                    // that a three-axis card's lines still clear each other by the 5dp this Column
                    // spaces them with rather than visibly loosening.
                    modifier = Modifier
                        .clickable(onClick = onOpenResearch)
                        .testTag(GalaxyTestTags.adaptation(slot, failure.technology))
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RelayRow(row: WorldRowUiState, relay: VerdictUiState.Relay) {
    val mono = oltreMono()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
            .testTag(GalaxyTestTags.row(row.slot))
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = row.coordinate,
                color = OltreColors.textSecondary,
                fontFamily = mono,
                fontSize = 13.5.sp,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "RELAY",
                color = OltreColors.accent,
                fontFamily = mono,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            Text(
                text = "CONTESTED",
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 9.5.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        Detail(text = relay.effect, color = OltreColors.textTertiary)
    }
}

@Composable
private fun Detail(text: String, color: Color) {
    Text(text = text, color = color, fontFamily = oltreMono(), fontSize = 10.5.sp, lineHeight = 15.sp)
}

private fun VerdictUiState.word(): String = when (this) {
    is VerdictUiState.Home -> "HOME"
    is VerdictUiState.Occupied -> "OCCUPIED"
    VerdictUiState.Unsurveyed -> "UNSURVEYED"
    is VerdictUiState.Blocked -> "BLOCKED"
    is VerdictUiState.Barren -> "BARREN"
    is VerdictUiState.Settleable -> "SETTLEABLE"
    is VerdictUiState.Relay -> "RELAY"
}

// No colour for Occupied or Barren: another empire holding a world is not a warning, and a thin
// world is a fact rather than a state. Unsurveyed is faint but not dimmed — it keeps the full card.
private fun VerdictUiState.hue(): Color = when (this) {
    is VerdictUiState.Home -> OltreColors.accent
    is VerdictUiState.Occupied -> OltreColors.textSecondary
    VerdictUiState.Unsurveyed -> OltreColors.textTertiary
    is VerdictUiState.Blocked -> OltreColors.danger
    is VerdictUiState.Barren -> OltreColors.textSecondary
    is VerdictUiState.Settleable -> OltreColors.ok
    is VerdictUiState.Relay -> OltreColors.accent
}

// Every surveyed verdict that has one, which now includes `Blocked`: a row that named the cost and
// never the worth was half a verdict, and on this screen it was the half the player sees most.
private fun VerdictUiState.yieldLabel(): String? = when (this) {
    is VerdictUiState.Blocked -> yieldLabel
    is VerdictUiState.Barren -> yieldLabel
    is VerdictUiState.Settleable -> yieldLabel
    else -> null
}
