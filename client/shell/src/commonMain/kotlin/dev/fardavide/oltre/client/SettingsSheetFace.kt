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
import dev.fardavide.oltre.client.auth.ui.DeleteFaceContent
import dev.fardavide.oltre.client.auth.ui.DeleteFaceUiState
import dev.fardavide.oltre.client.player.ui.IdentityFaceContent
import dev.fardavide.oltre.client.player.ui.IdentityFaceUiState
import dev.fardavide.oltre.client.player.ui.MarkComposeFaceContent
import dev.fardavide.oltre.client.player.ui.MarkComposeFaceUiState
import dev.fardavide.oltre.client.settings.ui.AlertSheetContent
import dev.fardavide.oltre.client.settings.ui.AlertSheetUiState
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus

// **One sheet with six faces, and that is still the whole of Claude Design's §4.** The gear raises it
// on the settings face; a new build raises it on the changelog face; the strip's left cluster raises
// it on the identity face; the build row, the account row and the compose row each cross from one
// face to another. Nothing stacks, nothing resizes, and there is no back — the ways out are the
// handle, the scrim and the system gesture, exactly as they are for every other sheet in the app.
//
// **Two faces was the design's number and six is the same arrangement**, which is worth saying because
// the growth looks like drift: every face added since has arrived the way the changelog did — as
// contents swapped inside the one sheet — rather than as a second surface. The three doors §4 refuses
// are refused six times over.
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
    // The two doors out of the account, and the one action in the app that cannot be undone. `delete`
    // is null wherever there is no account face to draw, which is the same nullability the sheet's own
    // Account section carries and for the same reason.
    delete: DeleteFaceUiState?,
    onOpenDelete: () -> Unit,
    onKeepAccount: () -> Unit,
    onDeleteAccount: () -> Unit,
    // ── Who is playing, and the mark they wear ───────────────────────────────────────────────
    //
    // **Not nullable, unlike `delete`, and the difference is the whole reason that one is.** There is
    // no account section to be absent here: a strip is drawn whenever there is a session, an account
    // that has chosen nothing wears `Threshold` and is called `Dead Reckoning`, and both faces are
    // therefore always drawable. A nullable state would be a `?.let` swallowing the one case that
    // cannot happen, and swallowing it silently.
    identity: IdentityFaceUiState,
    onChooseMark: (MarkPreset) -> Unit,
    onComposeMark: () -> Unit,
    onNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    // The composer, which is the first face in this app opened by another face rather than by a
    // control on the frame. It is still the same sheet swapping its contents, so there is still no
    // sheet over a sheet and still no back stack — the way out is the handle or the scrim.
    markCompose: MarkComposeFaceUiState,
    onChooseBody: (MarkBody) -> Unit,
    onChoosePath: (MarkPath) -> Unit,
    onChooseTerminus: (MarkTerminus) -> Unit,
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
                onDeleteAccount = onOpenDelete,
                build = { BuildRow(uiState = build, onOpenChangelog = onOpenChangelog) },
            )
            SheetFace.CHANGELOG -> ChangelogSheetContent(uiState = changelog, compact = compact)
            // **The third and fourth faces, and they arrive the way the changelog does** — the sheet
            // swaps its contents in place, so there is still no sheet over a sheet and no back stack.
            //
            // `delete` cannot be null here by construction: the only way to reach either face is the
            // Account row, which is only drawn when there is an account. Drawing nothing rather than
            // asserting, because a crash on the way to a deletion screen is the worst possible place
            // for one — and `DeleteAccountBehaviourTest` is what makes the pairing a fact.
            SheetFace.DELETE_WARN, SheetFace.DELETE_CONFIRM -> delete?.let {
                DeleteFaceContent(
                    uiState = it,
                    compact = compact,
                    onKeep = onKeepAccount,
                    onAct = onDeleteAccount,
                )
            }
            // **The fifth and sixth faces, and the first pair the *frame* does not open.** The gear
            // raises the settings face and a new build raises the changelog; these two are raised by
            // the strip's left cluster and then by each other, which is the one thing about them that
            // is new. Everything else is the arrangement this file already had: one sheet, contents
            // swapped in place, no stack and no back.
            SheetFace.IDENTITY -> IdentityFaceContent(
                uiState = identity,
                compact = compact,
                onChooseMark = onChooseMark,
                onComposeMark = onComposeMark,
                onNameChange = onNameChange,
                onSaveName = onSaveName,
            )
            // **No `compact`, and that is `MarkComposeFaceContent`'s call rather than an omission
            // here**: nothing on that face is measured differently at 320dp, so a parameter would be
            // a knob nothing turns.
            SheetFace.MARK_COMPOSE -> MarkComposeFaceContent(
                uiState = markCompose,
                onChooseBody = onChooseBody,
                onChoosePath = onChoosePath,
                onChooseTerminus = onChooseTerminus,
            )
        }
    }
}

// Which face the one sheet is wearing. An enum rather than two flags, because "both at once" is not
// a state and a `Boolean` pair would let somebody write it.
enum class SheetFace {

    SETTINGS,
    CHANGELOG,

    // All reading and no consequence: four rows of what the account holds, then the fact the numbers
    // cannot teach. Red begins here, as an outline.
    DELETE_WARN,

    // The last step, and the only filled red button in the product.
    DELETE_CONFIRM,

    // A name you chose and a mark you picked, opened by the strip's left cluster.
    IDENTITY,

    // Forty marks out of eleven drawings, opened by the row under the grid — and the only face in
    // this app another face opens.
    MARK_COMPOSE,
}

private const val SWAP_MILLIS = 210

// The design asks for 16dp of travel in the direction of the swap. Compose's slide specs are given a
// width and return an offset, so the fraction is how much of the sheet 16dp is — near enough at every
// width the app runs at, and it keeps the movement proportional rather than fixed on a desktop window
// three times the width of a phone.
private const val SLIDE_FRACTION = 24
