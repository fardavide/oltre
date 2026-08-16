package dev.fardavide.oltre.client.fleets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.fardavide.oltre.client.world.ui.WorldPortrait
import dev.fardavide.oltre.core.ResourceKind

// **Where you have been, and the door back.** Claude Design, 2026-08-16 — the section stopped being
// a ledger of runs and became a list of worlds, because *"eleven runs are five worlds"* and a row
// that is a world can carry what a single landing never had.
//
// **Tappable by geometry: no card, no accent, no chevron.** Hairline-ruled rows under a ruled label
// is a table, which is a different idiom from the run cards above and a quieter one. The affordance
// is the disc — *"a face makes a row an object, and objects open"* — and accent is deliberately not
// spent, because accent means *go tap this* and the thing behind a row here is usually a countdown.
// A chevron was drawn and dropped: nothing in Oltre has one, and the right end of every row in the
// app is a value.
@Composable
internal fun WorkedList(
    uiState: WorkedListUiState,
    compact: Boolean,
    onOpenWorld: (dev.fardavide.oltre.core.GalaxyCoordinate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(
            text = "WORLDS WORKED",
            rule = if (compact) uiState.compactTrailing else uiState.trailing,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            uiState.rows.forEach { row ->
                Hairline()
                WorkedRow(uiState = row, compact = compact, onClick = { onOpenWorld(row.at) })
            }
            // **No disc, and that is the whole mechanism.** A landing with no target is not a world,
            // so in a list of worlds it cannot be a row — and since the disc is what says *this
            // opens*, its absence says the rest.
            uiState.unrecorded?.let { note ->
                Hairline()
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FleetsTestTags.UNRECORDED)
                        .defaultMinSize(minHeight = 30.dp)
                        .padding(vertical = 5.dp),
                ) {
                    Text(
                        text = note,
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

// 45dp, and the added ink over the four-row ledger it replaces is exactly one step of type (12.5
// against 10.5) and 24dp of world. Everything else on the row was already there.
@Composable
private fun WorkedRow(uiState: WorkedWorldUiState, compact: Boolean, onClick: () -> Unit) {
    val mono = oltreMono()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Ahead of the click, so the tag names the row rather than the ripple's interior.
            .testTag(FleetsTestTags.world(uiState.at))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(vertical = 4.dp),
    ) {
        // The affordance and half the content at once: fill is temperature, drawn diameter is
        // gravity, banding is pressure, and the marks are the hazards — the same disc the Galaxy row
        // carries at 26dp, which is why it moved to `:client:world:ui` when this list arrived.
        WorldPortrait(uiState = uiState.portrait, box = if (compact) 22.dp else 24.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = uiState.name,
                    color = OltreColors.text,
                    fontFamily = mono,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // The lifetime total, in its resource's own hue — the same channel the cost chips
                // and the rail spend, so a number in crystal blue is a crystal number wherever it
                // appears.
                Text(
                    text = uiState.total,
                    color = uiState.kind.tint(),
                    fontFamily = mono,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${if (compact) uiState.compactPrefix else uiState.prefix} · ",
                        color = OltreColors.textTertiary,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = uiState.kind.label(),
                        color = uiState.kind.tint(),
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = " ${uiState.deposit}",
                        // The one reading on the row that can say a door leads nowhere, and the only
                        // place danger red appears in this section.
                        color = if (uiState.depositIsEmpty) OltreColors.danger else OltreColors.text,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The only conditional element on the row. It carries the verb, because a bare clock
                // in Oltre is a countdown.
                uiState.landed?.let { landed ->
                    Text(
                        text = landed,
                        color = OltreColors.textTertiary,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

// The rule sits *above* each row rather than between them, so the first row is separated from the
// section label exactly as every later one is separated from its predecessor. That is what makes the
// section read as a table rather than as a list that happens to have lines in it.
@Composable
private fun Hairline() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.09f)))
}

private fun ResourceKind.tint() = when (this) {
    ResourceKind.METAL -> OltreColors.metal
    ResourceKind.CRYSTAL -> OltreColors.crystal
    ResourceKind.DEUTERIUM -> OltreColors.deuterium
}

private fun ResourceKind.label(): String = when (this) {
    ResourceKind.METAL -> "metal"
    ResourceKind.CRYSTAL -> "crystal"
    ResourceKind.DEUTERIUM -> "deuterium"
}
