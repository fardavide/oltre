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
import dev.fardavide.oltre.client.design.component.OltreBottomSheet
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
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsSheet(
    face: SheetFace,
    alerts: AlertSheetUiState,
    changelog: ChangelogUiState,
    build: BuildRowUiState,
    compact: Boolean,
    onDismiss: () -> Unit,
    onOpenChangelog: () -> Unit,
    onSelectMode: (AlertMode) -> Unit,
    onToggleCategory: (AlertCategory) -> Unit,
    onSelectDelivery: (AlertDelivery) -> Unit,
    modifier: Modifier = Modifier,
) {
    OltreBottomSheet(onDismiss = onDismiss, modifier = modifier) {
        AnimatedContent(
            targetState = face,
            transitionSpec = {
                // 210ms and 16dp of travel, one-shot, which is the destination switch's own figure —
                // deliberately, because it is the same event: one surface replaced in place inside
                // chrome that does not move. The handle, the radius and the scrim are all outside
                // this and none of them animate.
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
