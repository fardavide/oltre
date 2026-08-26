package dev.fardavide.oltre.client.colony.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.CostChip
import dev.fardavide.oltre.client.design.component.HeldAction
import dev.fardavide.oltre.client.design.component.HeldNote
import dev.fardavide.oltre.client.design.component.LevelDial
import dev.fardavide.oltre.client.design.component.OltreCardState
import dev.fardavide.oltre.client.design.component.RowVerdict
import dev.fardavide.oltre.client.design.component.WatchSquare
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.component.WatchableAction
import dev.fardavide.oltre.client.design.component.asSquare
import dev.fardavide.oltre.client.design.component.completionSweep
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.oltreCard
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.component.rememberCompletionSweep
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.icon.PowerMark
import dev.fardavide.oltre.core.BuildingType

// Facility rows per the mockup: per-resource affordability by colour, a duration chip on every
// unlockable row, time-until-affordable in the action slot instead of a dead button, and locked
// rows dimmed with their requirement. A facility that is building carries its own countdown and
// progress bar — upgrades run in parallel, so there is no single build to hoist into a card.
@Composable
fun FacilityList(
    facilities: List<FacilityRowUiState>,
    compact: Boolean,
    onUpgrade: (BuildingType) -> Unit,
    onToggleWatch: (BuildingType) -> Unit,
    onOpenDetail: (BuildingType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facilities.forEach { row ->
            FacilityRow(
                row = row,
                compact = compact,
                onUpgrade = onUpgrade,
                onToggleWatch = onToggleWatch,
                onOpenDetail = onOpenDetail,
            )
        }
    }
}

@Composable
private fun FacilityRow(
    row: FacilityRowUiState,
    compact: Boolean,
    onUpgrade: (BuildingType) -> Unit,
    onToggleWatch: (BuildingType) -> Unit,
    onOpenDetail: (BuildingType) -> Unit,
) {
    val mono = oltreMono()
    val locked = row.action is FacilityActionUiState.Locked
    val sweep = rememberCompletionSweep(play = row.finishedWhileAway)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ColonyTestTags.card(row.building))
            // The card body opens the sheet, and a locked one opens it too — stating the Nanite
            // Factory's payoff on day one is the whole reason that row is worth a tap while it is
            // still dim. The button and the square are inner clickables and win their own taps.
            //
            // Ahead of the fill, as everywhere else: `pressable` scales what is drawn inside it, and
            // a background declared first is drawn outside.
            .pressable(shape = oltreCardShape) { onOpenDetail(row.building) }
            // **A held *action* takes the whole surface; a held square does not.** The design's own
            // frame is the authority: a card whose upgrade is queued is amber, and a card whose bell
            // is queued keeps its ordinary fill and says so in its foot. The surface answers *is this
            // row's own thing outstanding*, and a bell is about being told rather than about the row.
            .oltreCard(if (row.held.action) OltreCardState.HELD else row.action.cardState())
            // Over the fill and over the content, which is what makes it read as light falling on
            // the card rather than as a shape drawn on it.
            .completionSweep(sweep)
            // After the card, not before it — see the same ordering in TechnologyList. An alpha
            // ahead of the fill dims the card and lets the starfield through a locked row; here
            // the card stays solid and only its content recedes.
            .alpha(if (locked) 0.42f else 1f)
            // **No `animateContentSize` here, and 0.13.2 is where that was tried and taken back
            // out**, for the reason `oltreCard` no longer settles its colours either. Booking a
            // watch adds a line to this card and it was worth animating; the trouble is that it is
            // not the only thing that does. The power `fix` line appears when a plant falls behind
            // its draw, the action block swaps a price for a countdown when a build starts, and the
            // verdict comes and goes — all of them re-derived once a second by `App.kt` with nobody
            // touching the phone. A modifier measuring this node cannot tell a tap from a tick, so
            // it would animate a colony reporting on itself, which is the one thing this app may
            // not draw. The ledger's worlds list keeps its own `animateContentSize`, because a
            // search box is only ever changed by a player.
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                // Slide Over is 320dp — narrower than any phone, and reachable now that the app
                // multitasks on iPad. A long name has to give way there: it truncates, while the
                // level badge keeps its one line rather than wrapping "LV" above its number.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (compact) row.compactName else row.name).resolve(),
                        color = OltreColors.text,
                        fontFamily = mono,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        // The level the row arrived at, except for the half-second while a
                        // completion band is still short of the badge — see `CompletionSweep`.
                        // The number changes behind the light, so the eye is pulled to the badge
                        // by the sweep and finds the new level already there.
                        text = Strings.levelBadge(if (sweep.settled) row.level.value else row.level.value - 1).resolve(),
                        color = OltreColors.textSecondary,
                        fontFamily = mono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(start = 7.dp)
                            .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    // Nothing joins line one. At 320dp the name, the level badge and the action
                    // already use every pixel here, and a third element truncates the facility
                    // name — which is load-bearing. The energy mark goes on line two instead.
                }
                // Each branch states its own lines in order, because the order is the card: the
                // "→ becomes" slot is line two, and the price line is the one below it.
                when (val action = row.action) {
                    // No mark and no fix. A locked facility is not built, so it draws nothing —
                    // there is nothing to attribute and nothing to fight the 42% dim. The verdict
                    // goes *under* the requirement here and only here: what the row is waiting for
                    // has to be read before what it would be worth.
                    is FacilityActionUiState.Locked -> {
                        Text(
                            text = action.reason.resolve(),
                            color = OltreColors.textSecondary,
                            fontFamily = mono,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        VerdictOrHeld(row = row, compact = compact)
                    }
                    // The one row with no verdict, and the only state where nobody is choosing: the
                    // decision was made when the player tapped, so the slot belongs to the arrow.
                    is FacilityActionUiState.Upgrading -> {
                        // The accent line keeps the target level and finish time; the draw joins
                        // it after a gap, in amber against the accent. The bar is untouched.
                        TermsLine {
                            Text(
                                text = action.becomes().resolve(),
                                color = OltreColors.accent,
                                fontFamily = mono,
                                fontSize = 10.5.sp,
                            )
                            row.power?.let { PowerTerm(power = it) }
                        }
                        row.fix?.let { FixLine(fix = it, mono = mono) }
                        // A running row has no verdict, so nothing is displaced — but its square can
                        // still be held, and the direction has to be said somewhere.
                        VerdictOrHeld(row = row, compact = compact)
                    }
                    FacilityActionUiState.Upgrade,
                    is FacilityActionUiState.AffordableIn,
                    -> {
                        VerdictOrHeld(row = row, compact = compact)
                        row.fix?.let { FixLine(fix = it, mono = mono) }
                        TermsLine {
                            row.costs.forEach { chip -> CostChip(chip = chip) }
                            Text(
                                text = row.duration.resolve(),
                                color = OltreColors.textSecondary,
                                fontFamily = mono,
                                fontSize = 10.5.sp,
                            )
                            // Last on the price line, set exactly like a cost chip and tinted
                            // like one: a draw is a price, the difference being that the chips
                            // are charged once and this is charged continuously.
                            row.power?.let { PowerTerm(power = it) }
                        }
                    }
                }
                // Last on the card, under the price it is about. Accent text and not an accent
                // border: the border means something is in flight, and a watched row is not doing
                // anything — it is booked.
                (row.watch as? WatchUiState.Booked)?.let { booked ->
                    Text(
                        text = booked.affordableAt.resolve(),
                        color = OltreColors.accent,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        // 6dp where the card's other lines take 4dp: this one answers the square to
                        // its right rather than the line above it, and the extra 2dp is what stops
                        // it reading as a fourth term of the price.
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            // **The amber ghost wins over every other action state.** Whatever the row would
            // otherwise offer, a queued upgrade replaces it with `Held` — a target, not a disabled
            // control: pressing it withdraws the request. It is one branch ahead of the `when` rather
            // than four branches inside it, because the queue is a fact about the row and not about
            // which of its four states the colony happens to be in.
            if (row.held.action) {
                HeldAction(
                    onClick = { onUpgrade(row.building) },
                    modifier = Modifier.testTag(ColonyTestTags.card(row.building) + HELD_SUFFIX),
                )
            } else when (val action = row.action) {
                FacilityActionUiState.Upgrade -> Text(
                    text = Strings.upgradeVerb().resolve(),
                    color = Color.White,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    // `pressable` ahead of the fill rather than after it: it scales what is drawn
                    // inside it, and a background declared first is drawn outside, which would
                    // shrink the word and leave the blue behind.
                    modifier = Modifier
                        .pressable(shape = oltreActionShape) { onUpgrade(row.building) }
                        .background(OltreColors.accent, oltreActionShape)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                // The ghost time, and beside it the square that books an alert for the instant it
                // names. The square sits *outside* the ghost rather than inside it, because the two
                // are different things: one is how long you have to wait, the other is whether you
                // want to be told when the wait is over.
                is FacilityActionUiState.AffordableIn -> WatchableAction(
                    watch = row.watch?.asSquare(held = row.held.watch),
                    stacked = compact,
                    onToggleWatch = { onToggleWatch(row.building) },
                    watchModifier = Modifier.testTag(ColonyTestTags.watch(row.building)),
                ) {
                    Text(
                        text = action.label.resolve(),
                        color = OltreColors.textTertiary,
                        fontFamily = mono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.16f), oltreActionShape)
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
                // How long is left, and how far round it has got. The two used to sit at opposite
                // ends of the card — a countdown here and a 3dp bar under everything — and the bar
                // was the widest thing on a running row while saying the least. The dial says the
                // same fraction in a tenth of the ink and takes the level with it.
                // The square joins the pair the running row already draws, and joins it **last**:
                // it is the rightmost thing on every row that has one, which is what lets the eye
                // run down the column and see what it has asked about. Nothing else on a running
                // row changes — the design's own words are "the square is the only difference".
                is FacilityActionUiState.Upgrading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 11.dp),
                ) {
                    Text(
                        text = action.countdown.resolve(),
                        color = OltreColors.text,
                        fontFamily = mono,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LevelDial(level = row.level.value, percent = action.progressPercent)
                    row.watch?.let { watch ->
                        WatchSquare(
                            state = watch.asSquare(held = row.held.watch),
                            onClick = { onToggleWatch(row.building) },
                            // Never stacked: a running row's action is a line of three things, and
                            // its card is taller than 44dp already.
                            stacked = false,
                            modifier = Modifier.testTag(ColonyTestTags.watch(row.building)),
                        )
                    }
                }
                is FacilityActionUiState.Locked -> Unit
            }
        }
    }
}

// What the card is made of is the design system's; which of its three states a facility is in is
// this feature's, and only this feature can say. Exhaustive, so a fifth action cannot be added
// without deciding how its card reads.
private fun FacilityActionUiState.cardState(): OltreCardState = when (this) {
    FacilityActionUiState.Upgrade -> OltreCardState.ACTIONABLE
    // A locked facility and one waiting on its stocks are the same card: both are waiting, and the
    // difference between them is already told by the dim and by the reason line.
    is FacilityActionUiState.AffordableIn,
    is FacilityActionUiState.Locked,
    -> OltreCardState.WAITING
    is FacilityActionUiState.Upgrading -> OltreCardState.RUNNING
}

// The card's second line: a handful of short terms separated by a gap, which wraps rather than
// clipping. It has to — a level-16 Deuterium Synthesizer costs six digits of metal, and at 320dp
// that plus a duration plus the energy mark is wider than the column it sits in.
@Composable
private fun TermsLine(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        // The same 4dp the card already puts between its lines, so a wrapped term reads as part
        // of the line it came from rather than as a line of its own.
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
        content = content,
    )
}

// Green, because it is a statement about production restored, and because it pairs with the
// supply mark on the same card. Not a call to action and not a reason to reorder the list:
// spatial memory is the one thing a game of five-minute check-ins cannot afford to break.
@Composable
private fun FixLine(fix: TextRes, mono: FontFamily) {
    Text(
        text = fix.resolve(),
        color = OltreColors.ok,
        fontFamily = mono,
        fontSize = 10.5.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// The mark and a red cost chip can share this line without conflict, because they are different
// channels: red is the resource you are short of, amber is the energy you have outgrown.
@Composable
private fun PowerTerm(power: FacilityPowerUiState) {
    val tint = if (power.supply) OltreColors.ok else OltreColors.warn
    Row(verticalAlignment = Alignment.CenterVertically) {
        PowerMark(color = tint)
        Text(
            text = power.label.resolve(),
            color = tint,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

// **Held displaces the verdict rather than adding a line**, which is the design's own fix for the map
// card's bell generalised: a held card has no countdown and no bar, so the slot the verdict was in is
// free — and *what you asked for and have not got* is the more urgent of the two things a card could
// say about itself.
//
// Three callers, one rule. Written as a function rather than three copies because the day the rule
// changes it has to change once.
@Composable
private fun VerdictOrHeld(row: FacilityRowUiState, compact: Boolean) {
    val line = row.held.line
    if (line == null) {
        row.verdict?.let { RowVerdict(verdict = it, compact = compact) }
    } else {
        HeldNote(text = line)
    }
}

// The tag the ghost carries, so a behaviour test can press the thing that withdraws rather than the
// thing that would have started an upgrade. Suffixed onto the card's own tag, so a row's controls
// stay findable from one name.
internal const val HELD_SUFFIX = ".held"
