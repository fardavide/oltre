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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono

// **Treatment 1b: a row leads with what you can do about it today.** One rule generates all six
// verdicts — Blocked and Barren lead with richness because their verdict is not an offer, and the
// other four lead with the verdict because it is. See `VerdictUiState` for the argument and for what
// the treatment subtracted.
@Composable
internal fun WorldList(
    bands: List<OrbitBandUiState>,
    compact: Boolean,
    onOpenResearch: () -> Unit,
    onOpenWorld: (Int) -> Unit,
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
                    WorldRow(
                        row = row,
                        compact = compact,
                        onOpenResearch = onOpenResearch,
                        onOpenWorld = onOpenWorld,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldRow(
    row: WorldRowUiState,
    compact: Boolean,
    onOpenResearch: () -> Unit,
    onOpenWorld: (Int) -> Unit,
) {
    // The one row that is not a card. It is not tappable, so it does not get the surface every
    // tappable thing in the app has — a hairline and no fill instead.
    if (row.verdict is VerdictUiState.Relay) {
        RelayRow(row = row, relay = row.verdict)
        return
    }

    val home = row.verdict is VerdictUiState.Home
    // **Whether this row raises a sheet is `startRun`'s rule, restated once.** Your own world, a
    // world somebody holds and a relay all refuse outright, so a row that offered a sheet for one of
    // them would be the screen promising something the verb would throw away the moment it was used.
    // Everything else is a target — including `Blocked`, which is the whole mechanic: hostility gates
    // settling and never gathering, so the commonest verdict on the map is an ordinary destination.
    val target = row.verdict.isRunnable()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (home) OltreColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.09f),
                RoundedCornerShape(14.dp),
            )
            .background(oltreCardSurface, RoundedCornerShape(14.dp))
            .testTag(GalaxyTestTags.row(row.slot))
            .clickable(enabled = target) { onOpenWorld(row.slot) }
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Header(row = row)
        row.deposits?.let { DepositLine(deposits = it, compact = compact) }
        when (val verdict = row.verdict) {
            is VerdictUiState.Home -> Note(text = verdict.note)
            is VerdictUiState.Occupied -> Note(text = verdict.note)
            // Its yield, richness and fields, unchanged. The deposits are a line *above* this rather
            // than a replacement for it: what a settler wants to know about a world and what a run
            // can lift out of it are different questions, and this row is the only one that answers
            // both.
            is VerdictUiState.Settleable -> Note(text = verdict.note)
            // Two facts, both free: the coordinate and the orbit, both already in the header. The
            // shortest card in the app, because a screen of them is the normal case — and it says
            // nothing about hazards because it knows nothing about them.
            VerdictUiState.Unsurveyed -> Unit
            is VerdictUiState.Blocked -> {
                FleetReading(reading = verdict.reading, compact = compact)
                BlockedAxes(
                    slot = row.slot,
                    failures = verdict.failures,
                    onOpenResearch = onOpenResearch,
                )
            }

            is VerdictUiState.Barren -> {
                FleetReading(reading = verdict.reading, compact = compact)
                // The same slot the blocked axes take, with one clause instead of a list: Barren
                // fails no band at all, it fails the bar. No technology, because no ladder widens a
                // yield — which is why this line has nothing flush right and Blocked's does.
                BlockedLine(
                    lead = null,
                    body = verdict.threshold,
                    failure = null,
                    slot = row.slot,
                    onOpenResearch = onOpenResearch,
                )
            }
            is VerdictUiState.Relay -> Unit // answered above, before the card
        }
    }
}

// The coordinate, the verdict word, the orbit. **One shape on all six verdicts since 0.9**, which is
// what Claude Design's Decision 1 bought: the richness pair used to take this slot on `Blocked` and
// `Barren`, and the deposits replaced it on a line of their own — so the header stopped being two
// different things and `Settleable` stopped being a special case.
//
// Treatment 1b's rule survives the change rather than being undone by it. It said *a row leads with
// what you can do about it today*, and put richness in this slot because the verdict was not an
// offer. What you can do is still send a hold; the numbers that price one are now the stocks, and
// they read better on a line that can wrap than in a slot that cannot.
@Composable
private fun Header(row: WorldRowUiState) {
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
        Text(
            text = row.verdict.word(),
            color = row.verdict.hue(),
            fontFamily = mono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
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

// What is left in the ground, on a line of its own — which is why nothing drops at the compact width.
// The header's children are single-line and unwrappable; this one wraps, so the card grows by a line
// rather than losing one, and `GalaxyScreenBehaviourTest` already pins that as the rule.
@Composable
private fun DepositLine(deposits: DepositReadingUiState, compact: Boolean) {
    val mono = oltreMono()
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            text = deposits.metal,
            color = OltreColors.metal,
            fontFamily = mono,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
        )
        // At 320dp the second deposit goes, which is the rule this row already spends twice — what
        // leaves is the reading you were not going to act on, and both are still on the sheet the row
        // raises, where the choice between them is actually made.
        if (!compact) {
            Text(
                text = deposits.crystal,
                color = OltreColors.crystal,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

// The hazards on the left carrying their own arithmetic, the round trip flush right and fainter.
// Neither needs a survey to be *asked* for and both need one to be answered, which is why they are
// on the row rather than under the header where the astronomy lives.
@Composable
private fun FleetReading(reading: FleetReadingUiState, compact: Boolean) {
    val mono = oltreMono()
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = reading.hazards,
            color = OltreColors.textSecondary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f),
        )
        // At 320dp the crystal richness moved out of the header and onto this line's left-hand end
        // would be a second reflow; the round trip is what goes instead, because it is the only
        // figure here that the sheet restates in full three lines later.
        if (!compact) {
            Text(
                text = reading.reach,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

// One line per failing axis: the axis and its two readings on the left, the technology flush right
// in its own column. The brief's sentence — "gravity 2.4 g, you tolerate 1.45 g, Gravitic
// Adaptation 8 would land it" — wraps to three lines at 393dp, and three of those is the wall this
// row must not be. It keeps the clause order and loses the verb.
//
// **The first line carries the verdict as its opening clause**, which is where the badge went.
@Composable
private fun BlockedAxes(slot: Int, failures: List<BlockedAxisUiState>, onOpenResearch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        failures.forEachIndexed { index, failure ->
            BlockedLine(
                // **The lead retired at 0.9.** It existed because the verdict word had given up this
                // row's header to the richness pair; the header carries the word again, so a second
                // statement of it here would be the row saying "Blocked" twice.
                lead = null,
                body = "${failure.axis} ${failure.reading}, you tolerate ${failure.tolerated}",
                failure = failure,
                slot = slot,
                onOpenResearch = onOpenResearch,
            )
        }
    }
}

@Composable
private fun BlockedLine(
    lead: String?,
    body: String,
    failure: BlockedAxisUiState?,
    slot: Int,
    onOpenResearch: () -> Unit,
) {
    val mono = oltreMono()
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = leadIn(lead = lead, body = body),
            fontFamily = mono,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f),
        )
        // Accent again, and tappable — one decision rather than two. 0.0.16 demoted this to
        // textSecondary for exactly one reason, that Research could not sell what the string named,
        // and 0.0.18 ended that reason. Restoring the colour alone would break the rule harder than
        // the demotion did: accent is the screen's only "go tap this" signal, so an accent string
        // that is not a target is worse than a tertiary string that is not a target.
        //
        // **The target is the string, and since this slice the rest of the row is a target too** —
        // for a different destination. The two do not compete: the technology goes to the tab that
        // sells the ladder, and the card goes to the sheet that sends a ship *without* it. That is
        // the row stating both of its answers, which is exactly what treatment 1b is for.
        failure?.let {
            Text(
                text = failure.label,
                color = OltreColors.accent,
                fontFamily = mono,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                softWrap = false,
                // **The padding is inside the clickable, and that ordering is the whole point.** A
                // 10.5sp line box is 15dp tall; every other target in the app is 30–32dp. Put the
                // padding before `clickable` and the hit rectangle is the glyphs alone, which on the
                // delivery target is a third of the 44pt iOS minimum. Accent means "go tap this"; a
                // target this small is the promise breaking on contact — and a near miss now lands
                // on the card, which opens a sheet about a completely different decision.
                //
                // The vertical padding is 6dp rather than the 7dp the Research button uses, so that
                // a three-axis card's lines still clear each other by the 5dp this Column spaces
                // them with rather than visibly loosening.
                modifier = Modifier
                    .clickable(onClick = onOpenResearch)
                    .testTag(GalaxyTestTags.adaptation(slot, failure.technology))
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
            )
        }
    }
}

// The verdict in the muted ink and the reading a step fainter behind it. The demotion is the point:
// in 1b the blockage is the *second* half of a sentence whose first half is what you can act on, so
// it must not read as loudly as the badge it replaced.
private fun leadIn(lead: String?, body: String): AnnotatedString = buildAnnotatedString {
    lead?.let { withStyle(SpanStyle(color = OltreColors.textSecondary)) { append(it) } }
    withStyle(SpanStyle(color = OltreColors.textTertiary)) { append(body) }
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
        Note(text = relay.effect, color = OltreColors.textTertiary)
    }
}

@Composable
private fun Note(text: String, color: Color = OltreColors.textSecondary) {
    Text(text = text, color = color, fontFamily = oltreMono(), fontSize = 10.5.sp, lineHeight = 16.sp)
}

// Non-null exactly on the two verdicts that lead with richness, which is the one place the treatment
// is decided — the header, the presence of the hazards line and the "Blocked · " clause all follow
// from this and cannot drift apart from each other.
private fun VerdictUiState.fleetReading(): FleetReadingUiState? = when (this) {
    is VerdictUiState.Blocked -> reading
    is VerdictUiState.Barren -> reading
    else -> null
}

// `startRun`'s three outright refusals, restated as the rule for whether the card is a target. Home
// and Occupied are refused by the verb; the relay never reaches here.
private fun VerdictUiState.isRunnable(): Boolean = when (this) {
    is VerdictUiState.Blocked,
    is VerdictUiState.Barren,
    is VerdictUiState.Settleable,
    VerdictUiState.Unsurveyed,
    -> true
    is VerdictUiState.Home,
    is VerdictUiState.Occupied,
    is VerdictUiState.Relay,
    -> false
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

// No colour for Occupied: another empire holding a world is not a warning. Unsurveyed is faint but
// not dimmed — it keeps the full card. Blocked and Barren never reach this in 1b, because their slot
// in the header is the richness pair; the two entries are kept so the `when` stays exhaustive over a
// sealed hierarchy rather than swallowing a future verdict in an `else`.
private fun VerdictUiState.hue(): Color = when (this) {
    is VerdictUiState.Home -> OltreColors.accent
    is VerdictUiState.Occupied -> OltreColors.textSecondary
    VerdictUiState.Unsurveyed -> OltreColors.textTertiary
    is VerdictUiState.Blocked -> OltreColors.danger
    is VerdictUiState.Barren -> OltreColors.textSecondary
    is VerdictUiState.Settleable -> OltreColors.ok
    is VerdictUiState.Relay -> OltreColors.accent
}
