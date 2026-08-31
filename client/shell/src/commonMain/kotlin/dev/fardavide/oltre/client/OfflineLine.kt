package dev.fardavide.oltre.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.PlayerProfile
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// **The one new piece of chrome the offline era adds**, and the whole of what it says: the network
// fact and the count. It **never carries the state of a control** — the design rejected that outright,
// because a banner saying three things are held cannot say *which* three and the player is looking at
// the switch rather than at the top of the screen.
//
// Null is the ordinary case, and it is an absence rather than an empty bar: a colony with signal has
// no line, and the 22dp goes back to the destination.
data class OfflineLineUiState(val text: TextRes)

// When the line appears and what it says.
//
// **Both halves are required and neither is derivable from the other.** `since` is the last instant
// the server answered, which only the shell knows because only the shell asks; `held` is how many taps
// are outstanding, which only the outbox knows. A line with one of them would be either a fact with no
// number or a number with no time.
//
// **Null when the server has answered**, whatever is in the queue: a queue that is draining is not an
// offline colony, and a line that stayed up while three verbs were in flight would be reporting on the
// app rather than on the network.
//
// **And null when it has never answered on this launch**, which is the case worth stating: a first
// launch with no signal never reaches this screen at all — the gate is still up, saying so in full
// sentences. A line reading *"no network since —"* would be the app inventing an instant.
internal fun offlineLine(
    reachable: Boolean,
    since: Instant?,
    held: Int,
    timeZone: TimeZone,
    compact: Boolean,
): OfflineLineUiState? {
    if (reachable || since == null) return null
    val at = since.toLocalDateTime(timeZone)
    return OfflineLineUiState(
        text = Strings.offlineSince(hour = at.hour, minute = at.minute, held = held, compact = compact),
    )
}

// **The same two facts, asked by the identity face**, and it lives beside the banner rather than in
// the composition root because it *is* the same question: what does the app say about there being no
// network, from the only two things it knows about that. The answers differ in one word and for one
// reason — the banner counts what is waiting, and a rename never queues, so there is nothing to count
// (see `profile-sheet.md` §3).
//
// **Null is what makes the controls live**, which is the whole of why this returns a `TextRes?` rather
// than a sentence and a flag: both identity faces read their held state off the presence of this card,
// so a face with nothing to say is a face whose eighteen controls can commit.
//
// **Two reasons a rename cannot happen, and the second one is not about the network.** `profile` is
// null while this device has not read the account — see `App` — and a whole-row write assembled
// without it is `POST /v1/profile` replacing a name nobody read with a null. So an unread profile
// raises the same amber card the offline state does, and the lead is the only part that differs: the
// offline one can name the minute the server was last seen, and this one has no minute to name.
//
// The order matters. A device that is both offline and unread is offline first, because *"no network
// since 09:41"* is the more actionable of the two true sentences — and the `since == null` arm answers
// the way the banner's does, since a device with a colony has reached the server at least once.
internal fun profileRequirement(
    profile: PlayerProfile?,
    reachable: Boolean,
    since: Instant?,
    timeZone: TimeZone,
): TextRes? {
    val offline = if (reachable || since == null) {
        null
    } else {
        val at = since.toLocalDateTime(timeZone)
        Strings.profileHeldRequirement(hour = at.hour, minute = at.minute)
    }
    return if (profile == null) offline ?: Strings.profileUnreadRequirement() else offline
}

// 22dp between the rail and the destination, in the rail's own surface so nothing shows through it.
//
// **`DESTINATION_HEIGHT` moves with this**, and that is not a note — it is the defect 0.12.0 shipped:
// a bar was added to the frame and the constant the galaxy's robot measures against was not, so the
// map's only control was off the bottom of the screen and every test still passed. The bar is here and
// the arithmetic is in `GalaxyRobot`.
@Composable
@NonRestartableComposable
internal fun OfflineLine(uiState: OfflineLineUiState) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(OFFLINE_LINE_HEIGHT)
            .background(RAIL)
            .testTag(ShellTestTags.OFFLINE),
    ) {
        Text(
            text = uiState.text.resolve(),
            // Amber, because the queue is amber: a thing of yours is out there and has not landed,
            // said about the connection this time. Not red — nothing here has been refused.
            color = OltreColors.warn,
            fontFamily = oltreMono(),
            fontSize = 10.5.sp,
            maxLines = 1,
        )
    }
}

internal val OFFLINE_LINE_HEIGHT: Dp = 22.dp

// The rail's own fill, so the line reads as part of the chrome above it rather than as a strip laid
// over the destination. Composited rather than an alpha for `oltreCardSurface`'s reason: the starfield
// sits under every destination, and an alpha here would put stars inside the chrome.
private val RAIL = Color(0xFF0A0E18)
