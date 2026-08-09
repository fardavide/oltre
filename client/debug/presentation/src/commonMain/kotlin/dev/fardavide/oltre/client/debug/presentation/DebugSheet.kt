package dev.fardavide.oltre.client.debug.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
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
            SectionLabel(text = "DEBUG", rule = "hold to act")

            SkipAction(report = report, onConfirm = onSkipAhead)
            Spacer(modifier = Modifier.height(8.dp))
            ResetAction(onConfirm = onReset)

            Spacer(modifier = Modifier.height(22.dp))
            SectionLabel(text = "STATE", rule = "read only")
            Readings(report)

            Spacer(modifier = Modifier.height(14.dp))
            CloseRow(onClick = onDismiss)
        }
    }
}

// The label states where the hold will land *before* it lands, because the whole action is a jump
// through time and a jump you cannot preview is one you cannot trust.
@Composable
private fun SkipAction(report: DebugReport, onConfirm: () -> Unit) {
    val next = report.nextEvent
    HoldRow(
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
        onConfirm = onConfirm,
    )
}

// The only destructive thing in the app. A colony is hours of somebody's evening, and this panel
// opens by *shaking the phone* — so it takes a deliberate hold rather than a tap, like everything
// else here.
@Composable
private fun ResetAction(onConfirm: () -> Unit) {
    HoldRow(
        label = "RESET COLONY",
        detail = "deletes the save and starts a new galaxy",
        tint = OltreColors.danger,
        tag = DebugTestTags.RESET,
        onConfirm = onConfirm,
    )
}

// Hold, do not tap. Both verbs on this panel change the colony — one moves its clock, the other
// deletes it — and the panel is opened by shaking the phone, which is a gesture a pocket can
// perform. Davide's call, 2026-08-09: a hold for both, rather than the two-tap arming 0.2.5 shipped
// for reset alone.
//
// **The confirm comes from the gesture, not from the bar.** `onLongPress` fires on the platform's
// own long-press timing; the fill is a rendering of that same duration and nothing depends on it.
// Driving it the other way round — confirm when the animation completes — would have made the
// action's correctness a property of an animation, and would have left it testable only by driving
// the test clock by hand.
@Composable
private fun HoldRow(
    label: String,
    detail: String,
    tint: Color,
    tag: String,
    onConfirm: () -> Unit,
) {
    val mono = oltreMono()
    val haptics = LocalHapticFeedback.current
    // The platform's figure rather than one of ours, so the bar finishes exactly when the gesture
    // fires on whichever device this is.
    val holdMillis = LocalViewConfiguration.current.longPressTimeoutMillis.toInt()
    var holding by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        // Linear, because the bar is a clock rather than a flourish: it is telling you how much
        // longer to hold, and an eased one would lie about that at both ends. Releasing drains it
        // quickly, so an abandoned hold reads as abandoned rather than as still counting.
        animationSpec = tween(durationMillis = if (holding) holdMillis else RELEASE_MILLIS, easing = LinearEasing),
        label = "hold",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .pointerInput(tag) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        // Whether it ended in a release or a cancel, the bar drains — the gesture
                        // decides what happened, and this only stops it filling.
                        tryAwaitRelease()
                        holding = false
                    },
                    onLongPress = {
                        // The one moment worth a buzz: the action has just happened, and on a phone
                        // held at arm's length that is the only feedback that arrives without
                        // reading anything.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                    },
                )
            },
    ) {
        // `matchParentSize` rather than `fillMaxSize`, so the bar takes the row's height without
        // having any say in it — the row is still sized by its two lines of text.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .testTag(DebugTestTags.fill(tag))
                    .background(tint.copy(alpha = 0.22f)),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(
                text = label,
                color = tint,
                fontFamily = mono,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.testTag(DebugTestTags.label(tag)),
            )
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

// Quick enough that an abandoned hold is obviously abandoned, slow enough to be seen at all.
private const val RELEASE_MILLIS: Int = 140

// The one row on the panel that is not a verb: it changes nothing, so it takes a tap.
@Composable
private fun CloseRow(onClick: () -> Unit) {
    Text(
        text = "CLOSE",
        color = OltreColors.textSecondary,
        fontFamily = oltreMono(),
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DebugTestTags.CLOSE)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    )
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
