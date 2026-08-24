package dev.fardavide.oltre.client

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fardavide.oltre.client.changelog.ui.BuildRow
import dev.fardavide.oltre.client.changelog.ui.BuildRowUiState
import dev.fardavide.oltre.client.changelog.ui.ChangelogSheetContent
import dev.fardavide.oltre.client.changelog.ui.ChangelogUiState
import dev.fardavide.oltre.client.settings.ui.AlertSheetContent
import dev.fardavide.oltre.client.settings.ui.AlertSheetUiState
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode

// **One sheet with two faces, and that is the whole of Claude Design's §4.** The gear raises it on
// the settings face; a new build raises it on the changelog face; the build row at the foot of the
// settings column crosses from one to the other. Nothing stacks, nothing resizes, and there is no
// back — the ways out are the handle, the scrim and the system gesture, exactly as they are for every
// other sheet in the app.
//
// **The three doors it refuses**, each for a stated reason: a sheet over a sheet (two scrims, two
// handles, and a drag that is ambiguous about which sheet it belongs to); dismiss-and-re-raise (half
// a second in which the frame behind flashes back for no reason); and a two-cell switch in the header
// (which would make the changelog a permanent half of settings — and on a first launch there is no
// settings sheet to switch back to, so the control would be absent on one route or lying on the
// other).
//
// It is in the shell because it is the one composable that has to know both features exist. That is
// the composition root's job and the reason nothing depends on it.
//
// **Only the face lives here, and `App` raises the sheet around it.** There was a wrapper — an
// `OltreBottomSheet` holding this, taking eleven parameters and forwarding ten — and it was worth
// deleting twice over. It said nothing its call site does not (`App` already raises the debug sheet
// itself), and a pass-through composable is the one shape in Compose that *cannot* be covered: a
// `ModalBottomSheet` renders into a scene root of its own, so no frame can photograph it, while the
// compiler emits `$changed` bookkeeping for every parameter it forwards. Sixty branches nothing could
// ever execute, for a function whose whole body was a call.
//
// **The split it leaves is the one every sheet in this app already makes**: chrome at the call site,
// contents in a composable a test tree can reach and a camera can see. The whole of the 210ms lives
// below, so it costs the transition nothing.
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsSheetFace(
    face: SheetFace,
    alerts: AlertSheetUiState,
    changelog: ChangelogUiState,
    build: BuildRowUiState,
    compact: Boolean,
    onOpenChangelog: () -> Unit,
    onSelectMode: (AlertMode) -> Unit,
    onToggleCategory: (AlertCategory) -> Unit,
    onSelectDelivery: (AlertDelivery) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = face,
        modifier = modifier,
        transitionSpec = {
            // 210ms and 16dp of travel, one-shot, which is the destination switch's own figure —
            // deliberately, because it is the same event: one surface replaced in place inside
            // chrome that does not move. The handle, the radius and the scrim are all outside this
            // and none of them animate.
            val spec = tween<Float>(durationMillis = SWAP_MILLIS)
            (slideInHorizontally(tween(SWAP_MILLIS)) { width -> width / SLIDE_FRACTION } +
                fadeIn(spec)) togetherWith
                (slideOutHorizontally(tween(SWAP_MILLIS)) { width -> -width / SLIDE_FRACTION } +
                    fadeOut(spec))
        },
        label = "settings sheet face",
    ) { shown ->
        when (shown) {
            SheetFace.SETTINGS -> AlertSheetContent(
                uiState = alerts,
                compact = compact,
                onSelectMode = onSelectMode,
                onToggleCategory = onToggleCategory,
                onSelectDelivery = onSelectDelivery,
                build = { BuildRow(uiState = build, onOpenChangelog = onOpenChangelog) },
            )
            SheetFace.CHANGELOG -> ChangelogSheetContent(uiState = changelog, compact = compact)
        }
    }
}

// Which face the one sheet is wearing. An enum rather than two flags, because "both at once" is not
// a state and a `Boolean` pair would let somebody write it.
enum class SheetFace {

    SETTINGS,
    CHANGELOG,
}

private const val SWAP_MILLIS = 210

// The design asks for 16dp of travel in the direction of the swap. Compose's slide specs are given a
// width and return an offset, so the fraction is how much of the sheet 16dp is — near enough at every
// width the app runs at, and it keeps the movement proportional rather than fixed on a desktop window
// three times the width of a phone.
private const val SLIDE_FRACTION = 24
