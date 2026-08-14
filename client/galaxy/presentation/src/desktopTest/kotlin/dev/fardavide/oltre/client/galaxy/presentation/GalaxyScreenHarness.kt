package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRobot
import dev.fardavide.oltre.client.galaxy.ui.PHONE_WIDTH
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The harness for the stateful screen, and the half of the Galaxy robot that could not go into
// `:client:galaxy:ui-testing`: a ui-layer module may not depend on a presentation one, and
// `GalaxyScreen` is here. The assertions are shared — `GalaxyRobot` itself is that module's — and
// what this adds is the second way of putting the screen on the glass.
// The stateful screen, which is a different subject from the page above rather than a fuller
// version of it: *which* world has its sheet up is this feature's own state — a `remember` keyed on
// the seed and the system — so a tap that raises a sheet, and a dispatch that puts it away again,
// can only be asserted from here. Everything else hands `GalaxyPage` a frame that already has one.
@OptIn(ExperimentalTestApi::class)
fun galaxyScreen(
    state: GameState,
    onOpenResearch: () -> Unit = {},
    onDispatchProbe: (SystemAddress) -> Unit = {},
    onDispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Unit = { _, _, _, _ -> },
    block: GalaxyRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = PHONE_WIDTH, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    GalaxyScreen(
                        state = state,
                        now = FIXTURE_NOW,
                        timeZone = TimeZone.UTC,
                        onOpenResearch = onOpenResearch,
                        onDispatchProbe = onDispatchProbe,
                        onDispatchRun = onDispatchRun,
                    )
                }
            }
        }
        GalaxyRobot(this).block()
    }
}

// Frozen, because the footer runs a countdown and a state read against a wall clock would differ
// from itself every second. Epoch in UTC, so a landing time is arithmetic rather than a fact about
// the machine that ran the test.
internal val FIXTURE_NOW: Instant = Instant.fromEpochMilliseconds(0)

// The colony every frame in `TestGalaxyUiState` describes, for the tests that drive the stateful
// screen rather than a mapped frame: which world has its sheet up is `GalaxyScreen`'s own state, so
// a tap that raises one is only a tap that raises one from here.
internal val testGameState: GameState = GameState.initial(GalaxySeed(20_260_807))
