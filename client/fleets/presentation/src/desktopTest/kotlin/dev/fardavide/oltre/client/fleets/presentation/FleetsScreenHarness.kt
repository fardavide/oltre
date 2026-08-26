package dev.fardavide.oltre.client.fleets.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.fleets.ui.FleetsRobot
import dev.fardavide.oltre.client.fleets.ui.PHONE_WIDTH
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Ships
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The *stateful* screen, composed. It lives here rather than in `:client:fleets:ui-testing` for the
// reason `GalaxyScreenHarness` does: a ui-layer module may not depend on a presentation one, and
// `FleetsScreen` is where the sheet's own state lives.
//
// Everything the page harness next door can assert, it should — this is only for the claims that are
// about the screen *holding* something: that a tap raises the sheet, and that what leaves is the
// rendered offer rather than the selection.
@OptIn(ExperimentalTestApi::class)
internal fun fleetsScreen(
    state: GameState,
    now: Instant = Instant.fromEpochMilliseconds(0),
    since: Instant = now,
    width: Int = PHONE_WIDTH,
    // `true` — the tap was kept, which is what a colony with signal always answers and is the frame
    // every test here is about. A harness that wants a refused run says `false` and gets a sheet that
    // stays up, which is the whole of the behaviour.
    onDispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Boolean = { _, _, _, _ -> true },
    onToggleAnnounce: () -> Unit = {},
    block: FleetsRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    FleetsScreen(
                        state = state,
                        now = now,
                        since = since,
                        timeZone = TimeZone.UTC,
                        onDispatchRun = onDispatchRun,
                        onToggleAnnounce = onToggleAnnounce,
                    )
                }
            }
        }
        FleetsRobot(this).block()
    }
}
