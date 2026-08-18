package dev.fardavide.oltre.client.research.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.CostChip
import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.LevelDial
import dev.fardavide.oltre.client.design.component.OltreCardState
import dev.fardavide.oltre.client.design.component.RowVerdict
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchSquare
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.component.WatchableAction
import dev.fardavide.oltre.client.design.component.completionSweep
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.oltreCard
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.component.rememberCompletionSweep
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology

// The facility list is the model and the row adds exactly one line to it. Reading order is
// unchanged: identity, then consequence, then price.
@Composable
internal fun TechnologyList(
    technologies: List<TechnologyRowUiState>,
    compact: Boolean,
    onStartResearch: (Technology) -> Unit,
    onToggleWatch: (Technology) -> Unit,
    onOpenDetail: (Technology) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        technologies.forEach { row ->
            ProjectRow(
                name = row.name,
                level = row.level,
                verdict = row.verdict,
                costs = row.costs,
                duration = row.duration,
                action = row.action,
                watch = row.watch,
                finishedWhileAway = row.finishedWhileAway,
                cardTag = ResearchTestTags.card(row.technology),
                rowTag = ResearchTestTags.row(row.technology),
                actionTag = ResearchTestTags.action(row.technology),
                watchTag = ResearchTestTags.watch(row.technology),
                compact = compact,
                onOpenDetail = { onOpenDetail(row.technology) },
                onStart = { onStartResearch(row.technology) },
                onToggleWatch = { onToggleWatch(row.technology) },
            )
        }
    }
}

// The same call, three rows further down. **No new component and no change to the existing one** —
// the band line is three different strings in the same three slots, so every alpha, weight and
// colour is the applied row's. That is load-bearing rather than tidy: from three rows away a
// running ladder is the answer to why nothing else can start, and it can only be read as that
// answer if it looks exactly like a running technology.
@Composable
internal fun AdaptationList(
    ladders: List<AdaptationRowUiState>,
    compact: Boolean,
    onStartAdaptation: (AdaptationTechnology) -> Unit,
    onToggleWatch: (AdaptationTechnology) -> Unit,
    onOpenDetail: (AdaptationTechnology) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ladders.forEach { row ->
            ProjectRow(
                name = row.name,
                level = row.level,
                verdict = row.verdict,
                costs = row.costs,
                duration = row.duration,
                action = row.action,
                watch = row.watch,
                finishedWhileAway = row.finishedWhileAway,
                cardTag = ResearchTestTags.card(row.technology),
                rowTag = ResearchTestTags.row(row.technology),
                actionTag = ResearchTestTags.action(row.technology),
                watchTag = ResearchTestTags.watch(row.technology),
                compact = compact,
                onOpenDetail = { onOpenDetail(row.technology) },
                onStart = { onStartAdaptation(row.technology) },
                onToggleWatch = { onToggleWatch(row.technology) },
            )
        }
    }
}

// Takes the row's parts rather than either row type, which is what makes "identical" a fact rather
// than a promise two composables make separately. The tags and the callbacks are passed in because
// they are the only things the two branches genuinely differ about — and since the verdict took the
// slot the band line and the shortlist line used to share, that is now the whole of the difference.
@Composable
private fun ProjectRow(
    name: String,
    level: TechLevel,
    // The one line of consequence a row carries, and the same line on both branches: an applied
    // level is worth so much an hour, a ladder level is worth so many worlds. Null while the row is
    // in flight, where the accent line below says what the slot is for instead.
    verdict: VerdictUiState?,
    costs: List<CostChipUiState>,
    duration: String,
    action: ResearchActionUiState,
    // Null on every row with no instant to book. See `WatchUiState`.
    watch: WatchUiState?,
    // True on at most one row in the whole app, and only just after a launch: this is the project
    // that landed while it was closed. See `CompletionSweep`.
    finishedWhileAway: Boolean,
    cardTag: String,
    rowTag: String,
    actionTag: String,
    watchTag: String,
    compact: Boolean,
    onOpenDetail: () -> Unit,
    onStart: () -> Unit,
    onToggleWatch: () -> Unit,
) {
    val mono = oltreMono()
    val locked = action is ResearchActionUiState.Locked
    val sweep = rememberCompletionSweep(play = finishedWhileAway)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Ahead of the fill and the border, as everywhere else — a `graphicsLayer` transforms
            // what is drawn inside it, and a background declared first is drawn outside. A **locked**
            // row is tappable too: the sheet is where a row the player cannot buy yet says what it
            // would be worth when they can.
            .pressable(shape = oltreCardShape) { onOpenDetail() }
            .oltreCard(action.cardState())
            // Over the fill and over the content, so it reads as light falling on the card.
            .completionSweep(sweep)
            .testTag(cardTag)
            // After the card, not before it, and that ordering is the whole point: an alpha placed
            // ahead of the fill dims the card itself, which turns the one opaque thing on the
            // screen translucent again and lets the starfield through it. A locked row would then
            // have stars inside it, reading as dust on the card rather than as space behind it —
            // the exact effect the opaque fills exist to prevent. Here the card stays solid and
            // only what is written on it recedes.
            .alpha(if (locked) 0.42f else 1f)
            // The colony's argument, and the same booking: a watched row gains its `→ affordable`
            // line, and the card takes the extra height over the length of a tap rather than
            // between two frames. See `FacilityList`.
            .animateContentSize()
            .padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tagged separately from the card above it, and the split is the tap: the card merges
            // its descendants' semantics the moment it becomes clickable, so a Robot reading what
            // the row *says* wants the column that says it rather than the target it presses.
            Column(modifier = Modifier.weight(1f).testTag(rowTag)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Every technology name is one word, so nothing here truncates at 320dp — but
                    // the guard stays, because a future name is not this slice's promise to keep.
                    Text(
                        text = name,
                        color = OltreColors.text,
                        fontFamily = mono,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        // The level the row arrived at, except while a completion band is still
                        // short of the badge — the number changes behind the light.
                        text = "LV ${if (sweep.settled) level.value else level.value - 1}",
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
                }
                when (action) {
                    // The requirement, and under it what clearing the requirement would be worth —
                    // the same pair the Colony's locked Nanite row carries, at the same 42% dim.
                    // A locked row used to be name, level and requirement, on the argument that a
                    // consequence you cannot buy is noise; this design's second hard case is
                    // exactly that argument being wrong. *Whether it is worth pushing Robotics for*
                    // is the only question a gate leaves open, and the row now answers it.
                    is ResearchActionUiState.Locked -> {
                        Text(
                            text = action.reason,
                            color = OltreColors.textSecondary,
                            fontFamily = mono,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        verdict?.let { RowVerdict(verdict = it, compact = compact) }
                    }
                    // In flight the effect line is replaced, exactly as a facility row drops its
                    // costs while it builds: what you want mid-project is when, not what — and
                    // "→ LV 4" already says what.
                    is ResearchActionUiState.Running -> Text(
                        text = "→ LV ${action.toLevel.value} · ${action.doneAt}",
                        color = OltreColors.accent,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    ResearchActionUiState.Start,
                    is ResearchActionUiState.AvailableIn,
                    -> {
                        // **One line where there used to be two**, and it is the design's one
                        // stated exception: two lines of numbers about the same level is where a
                        // dense row becomes an unreadable one. The percentages and the tolerance
                        // bands are not lost — they are the first thing the sheet says, where there
                        // is width to state both halves of each.
                        //
                        // The 320dp pane drops the second clause; a square does not. The clause is
                        // what the player compares across three rows, and `RowVerdict` truncates
                        // rather than wraps, so the row can carry both beside a square where the
                        // effect line it replaced could not.
                        verdict?.let { RowVerdict(verdict = it, compact = compact) }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            costs.forEach { chip -> CostChip(chip = chip) }
                            Text(
                                text = duration,
                                color = OltreColors.textSecondary,
                                fontFamily = mono,
                                fontSize = 10.5.sp,
                            )
                        }
                        // Last on the card, under the price it is about, and the same line the
                        // colony's watched row carries — see `watchedAtLabel`. Accent text and not
                        // an accent border: the border means in flight, this means booked.
                        (watch as? WatchUiState.Booked)?.let { booked ->
                            Text(
                                text = booked.affordableAt,
                                color = OltreColors.accent,
                                fontFamily = mono,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            when (action) {
                ResearchActionUiState.Start -> Text(
                    text = "Research",
                    color = Color.White,
                    fontFamily = mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    // `pressable` ahead of the fill rather than after it — see the same ordering
                    // on the colony's Upgrade button.
                    modifier = Modifier
                        .pressable(shape = oltreActionShape) { onStart() }
                        .background(OltreColors.accent, oltreActionShape)
                        .testTag(actionTag)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                )
                // The ghost time, and — only when the wait is about the price rather than about the
                // slot — the square that books an alert for it. See `watchOn`.
                is ResearchActionUiState.AvailableIn -> WatchableAction(
                    watch = watch,
                    stacked = compact,
                    onToggleWatch = onToggleWatch,
                    watchModifier = Modifier.testTag(watchTag),
                ) {
                    Text(
                        text = action.label,
                        color = OltreColors.textTertiary,
                        fontFamily = mono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.16f), oltreActionShape)
                            .testTag(actionTag)
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
                // The same pair the colony's running row draws, in the same order and at the same
                // gaps: how long is left, and how far round it has got. Identical by construction is
                // the whole point — from three rows away a running ladder, a running technology and
                // a running facility have to be one thing.
                is ResearchActionUiState.Running -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 11.dp),
                ) {
                    Text(
                        text = action.countdown,
                        color = OltreColors.text,
                        fontFamily = mono,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag(actionTag),
                    )
                    LevelDial(level = level.value, percent = action.progressPercent)
                    // Last, so the square is the rightmost thing on every row that has one — see the
                    // colony's running row, which draws the same three in the same order.
                    watch?.let {
                        WatchSquare(
                            watched = it != WatchUiState.Offered,
                            onClick = onToggleWatch,
                            stacked = false,
                            modifier = Modifier.testTag(watchTag),
                        )
                    }
                }
                is ResearchActionUiState.Locked -> Unit
            }
        }
    }
}

// What the card is made of is the design system's; which of its three states a project is in is
// this feature's, and only this feature can say. It reads a production technology and an adaptation
// ladder with the same four branches because ProjectRow draws them with the same composable — which
// is what makes "a running ladder looks exactly like a running technology" a fact rather than two
// promises. Exhaustive, so a fifth action cannot be added without deciding how its card reads.
private fun ResearchActionUiState.cardState(): OltreCardState = when (this) {
    ResearchActionUiState.Start -> OltreCardState.ACTIONABLE
    // A locked row and a row waiting on its stocks are the same card: both are waiting, and the
    // difference between them is already told by the dim and by the reason line.
    is ResearchActionUiState.AvailableIn,
    is ResearchActionUiState.Locked,
    -> OltreCardState.WAITING
    is ResearchActionUiState.Running -> OltreCardState.RUNNING
}
