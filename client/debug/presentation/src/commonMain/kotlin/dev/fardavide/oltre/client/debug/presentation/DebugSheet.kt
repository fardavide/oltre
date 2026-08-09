package dev.fardavide.oltre.client.debug.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.debug.domain.DebugReport
import dev.fardavide.oltre.client.debug.domain.SKIP_FALLBACK
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.core.FutureEvent
import kotlin.time.Duration

// The debug menu. Not a tab and not a screen — a panel over whatever the player was looking at,
// because it is not part of the game and should never look like it is.
//
// **This is the one surface in the app with no design behind it** (Davide, 2026-08-09: the debug UI
// does not go through Claude Design). So it borrows the system's tokens — the palette, the bundled
// mono, the section label, the content cap — and invents nothing. That is the standard it is held
// to instead of a baseline: it should look like it belongs to Oltre without ever having been drawn.
//
// Dismissal is the explicit CLOSE row rather than a tap on the scrim. A debug panel that vanishes
// when you misjudge the edge of it, mid-way through reading a clock, is worse than one that needs a
// deliberate tap to leave — and the gesture that opens it is already a shake.
@Composable
fun DebugSheet(
    report: DebugReport,
    onSkipAhead: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(DebugTestTags.SCRIM)
            // Dim rather than hide: seeing the colony behind the panel is what makes "skip ahead"
            // legible as a thing that happened to *this* colony.
            .background(Color.Black.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = OltreLayout.maxContentWidth)
                .fillMaxWidth()
                .testTag(DebugTestTags.SHEET)
                .background(OltreColors.surface, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionLabel(text = "DEBUG", rule = "not a player feature")

            SkipAction(report = report, onClick = onSkipAhead)
            Spacer(modifier = Modifier.height(8.dp))
            ResetAction(onConfirm = onReset)

            Spacer(modifier = Modifier.height(22.dp))
            SectionLabel(text = "STATE", rule = "read only")
            Readings(report)

            Spacer(modifier = Modifier.height(14.dp))
            ActionRow(
                label = "CLOSE",
                detail = "",
                tint = OltreColors.textSecondary,
                tag = DebugTestTags.CLOSE,
                onClick = onDismiss,
            )
        }
    }
}

// The label states where the tap will land *before* it lands, because the whole action is a jump
// through time and a jump you cannot preview is one you cannot trust.
@Composable
private fun SkipAction(report: DebugReport, onClick: () -> Unit) {
    val next = report.nextEvent
    ActionRow(
        label = "SKIP AHEAD",
        detail = if (next != null) {
            "${next.describe()} · ${(next.at - report.gameTime).toChipLabel()}"
        } else {
            // Read off the constant rather than written out, so the sentence cannot drift from the
            // duration it describes.
            "nothing in flight · +${SKIP_FALLBACK.toChipLabel()}"
        },
        tint = OltreColors.accent,
        tag = DebugTestTags.SKIP,
        onClick = onClick,
    )
}

// Two taps, and the second one is the only destructive thing in the app. A colony is hours of
// somebody's evening; one stray tap on a panel that opens by *shaking the phone* is not a good
// enough reason to lose it.
@Composable
private fun ResetAction(onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    ActionRow(
        label = if (armed) "TAP AGAIN TO WIPE" else "RESET COLONY",
        detail = if (armed) "this cannot be undone" else "deletes the save and starts a new galaxy",
        tint = OltreColors.danger,
        tag = DebugTestTags.RESET,
        onClick = {
            if (armed) onConfirm() else armed = true
        },
    )
}

@Composable
private fun ActionRow(
    label: String,
    detail: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit,
) {
    val mono = oltreMono()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            // Clip before the click, so the ripple stops at the rounded corner rather than painting
            // the rectangle the row would otherwise occupy.
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            color = tint,
            fontFamily = mono,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.testTag(DebugTestTags.label(tag)),
        )
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 10.5.sp,
                modifier = Modifier.testTag(DebugTestTags.detail(tag)).padding(top = 3.dp),
            )
        }
    }
}

// Every line here answers a question that was previously only answerable by pulling the save off a
// device — which is why the inspector is worth as much as the two actions above it.
@Composable
private fun Readings(report: DebugReport) {
    Reading("game time", report.gameTime.toString())
    Reading("wall time", report.wallTime.toString())
    Reading("skipped by", if (report.skippedBy == Duration.ZERO) "—" else report.skippedBy.toChipLabel())
    Reading("debug used", if (report.debugUsed) "yes" else "no")
    Reading("schema", report.schemaVersion.toString())
    Reading("galaxy seed", report.galaxySeed.toString())
    Reading("event log", report.eventLogSize.toString())
    Reading("builds", report.buildsInFlight.toString())
    Reading("probes", report.surveysInFlight.toString())
    Reading("research slot", if (report.researchSlotBusy) "busy" else "free")
    Reading("fleet", if (report.fleetInbound) "inbound" else "none")
}

@Composable
private fun Reading(name: String, value: String) {
    val mono = oltreMono()
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = name,
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 10.5.sp,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        // The tag goes on the *value*, not on the row: what a test wants to say is "skipped by
        // reads 4h 00m", and a tag on the row would make that an assertion about a container
        // holding two strings, one of which is the label it just looked the row up by.
        Text(
            text = value,
            color = OltreColors.text,
            fontFamily = mono,
            fontSize = 10.5.sp,
            modifier = Modifier.testTag(DebugTestTags.reading(name)),
        )
    }
}

// Enum names rather than the display names the notifications use, deliberately: this is a developer
// tool, and `METAL_MINE` is the string that matches the code somebody is about to go and read.
private fun FutureEvent.describe(): String = when (this) {
    is FutureEvent.BuildCompletes -> "${building.name} → ${toLevel.value}"
    is FutureEvent.ResearchCompletes -> "${technology.name} → ${toLevel.value}"
    is FutureEvent.AdaptationCompletes -> "${technology.name} → ${toLevel.value}"
    is FutureEvent.SurveyLands -> "PROBE → ${target.galaxy}:${target.system}"
    is FutureEvent.FleetArrives -> "FLEET RETURNS"
}
