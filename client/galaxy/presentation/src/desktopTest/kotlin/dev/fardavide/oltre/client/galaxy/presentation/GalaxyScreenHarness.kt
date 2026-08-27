package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRobot
import dev.fardavide.oltre.client.galaxy.ui.PHONE_WIDTH
import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
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
    // The tab lands on the map unless a preferences file says otherwise, and a test that wants the
    // worlds list may either say so here or tap the switch — which is the same choice a player has.
    landing: GalaxyLanding = GalaxyLanding.MAP,
    onLandingChange: (GalaxyLanding) -> Unit = {},
    onOpenResearch: () -> Unit = {},
    onDispatchProbe: (SystemAddress) -> Unit = {},
    // `true` — the tap was kept, which is what a colony with signal always answers and is the frame
    // every test here is about. A harness that wants a refused run says `false` and gets a sheet that
    // stays up, which is the whole of the behaviour.
    onDispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Boolean = { _, _, _, _ -> true },
    onToggleAnnounce: () -> Unit = {},
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
                        landing = landing,
                        onLandingChange = onLandingChange,
                        onOpenResearch = onOpenResearch,
                        onDispatchProbe = onDispatchProbe,
                        onDispatchRun = onDispatchRun,
                        onToggleAnnounce = onToggleAnnounce,
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
//
// **The skiff is put here rather than inherited, since 0.11.3.** Genesis used to grant one and this
// was `GameState.initial` alone; a colony now buys its first hull, and a fixture with an empty pool
// would raise the dispatch sheet in its *refusal* state — so every frame on this tab, and every
// baseline recorded from one, would have quietly become a picture of a screen that cannot send a
// run. One hull is what these frames have always described, so this states it instead of receiving
// it, and no baseline moves.
//
// **The scout joins it for exactly the same reason at 0.15**, one version later in the same story: a
// probe flies a hull now, so a fixture with no `SCOUT` would draw every probe footer on this tab in
// its *"needs a scout"* state. These frames have always described a colony that can send a probe —
// several of them are of a probe already in the air — so the pool has to say so.
//
// **And the alert settings join them at 0.18, third in the same story.** A new colony now asks about
// alerts by *kind*, and under that mode the dispatch sheet carries no bell at all — a flight is
// announced by its kind, so the control has nothing left to decide. Left inherited, every frame on
// this tab would have quietly become a picture of a sheet with three controls where these baselines
// describe four, and `galaxy_dispatch_announced` — a frame that exists to show the bell *lit* —
// would have become a picture of no bell. `dispatchByCategoryUiState` is the fixture for the other
// mode, and it has one frame of its own.
internal val testGameState: GameState = GameState.initial(GalaxySeed(20_260_807))
    .copy(
        ships = Ships(mapOf(ShipType.SKIFF to 1, ShipType.SCOUT to 1)),
        alerts = AlertSettings.CARRIED_FORWARD,
    )

// The same colony under a new save's own settings, for the one frame that shows what the sheet looks
// like when the bell has gone.
internal val byCategoryGameState: GameState = testGameState.copy(alerts = AlertSettings.NEW_COLONY)
