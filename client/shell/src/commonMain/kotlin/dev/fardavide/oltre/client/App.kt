package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fardavide.oltre.client.colony.presentation.ColonyScreen
import dev.fardavide.oltre.client.colony.presentation.toColonyUiState
import dev.fardavide.oltre.client.debug.data.ShakeDetector
import dev.fardavide.oltre.client.debug.data.defaultShakeDetector
import dev.fardavide.oltre.client.debug.domain.DebugClock
import dev.fardavide.oltre.client.debug.domain.debugReport
import dev.fardavide.oltre.client.debug.domain.skipAhead
import dev.fardavide.oltre.client.debug.presentation.DebugSheet
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.galaxy.presentation.GalaxyScreen
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.notifications.data.defaultNotificationScheduler
import dev.fardavide.oltre.client.research.presentation.ResearchScreen
import dev.fardavide.oltre.client.research.presentation.toResearchUiState
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.defaultSaveFile
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startAdaptation
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.startUpgrade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The shell is the impure boundary: it reads the clock, reads and writes the save file, books
// the local notifications, ticks the UI, and holds the current session. Game state itself only
// ever moves through core's advance/startUpgrade.
//
// Since 0.2.2 it reads the clock through a `DebugClock` rather than directly. At ×1 — every launch
// until somebody shakes the phone — that is the identity function and nothing here behaves
// differently; after a skip it is what keeps the colony running ahead of the wall clock instead of
// frozen at the instant it was skipped to.
@Composable
fun App(
    store: GameStore = remember { GameStore(defaultSaveFile()) },
    notifications: GameNotifications = remember { GameNotifications(defaultNotificationScheduler()) },
    // A parameter rather than a call, so the desktop entry point can hand in its keyboard chord —
    // a laptop cannot be shaken, and desktop is the platform where the menu is most wanted.
    shakeDetector: ShakeDetector = remember { defaultShakeDetector() },
    modifier: Modifier = Modifier,
) {
    OltreTheme {
        Surface(modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            // Null until the save has been read. Rendering a fresh colony first and swapping it
            // for the real one a frame later would flash wrong numbers at the player.
            var session by remember { mutableStateOf<GameSession?>(null) }
            // How far ahead of the wall clock this colony is running. Replaced rather than mutated,
            // so a skip is one state change that the tick loop and the commit both read.
            var debugClock by remember { mutableStateOf(DebugClock()) }
            var debugOpen by remember { mutableStateOf(false) }

            LaunchedEffect(shakeDetector) {
                shakeDetector.shakes().collect { debugOpen = true }
            }

            LaunchedEffect(Unit) {
                val saved = store.load()
                val wall = Clock.System.now()
                // The offset comes out of the save's own instant, which is the whole reason the
                // debug clock needs no file of its own: a colony written down in the future was
                // skipped there, and resuming at ×1 would freeze it until the wall clock caught up.
                val clock = DebugClock.resuming(saved?.lastUpdatedAt, wallClock = wall)
                debugClock = clock
                val resumed = resume(saved, now = clock.now(wall))
                session = resumed
                // Commit immediately, save included: a player who opens the game once and
                // closes it must still come back to hours of production, and on a first launch
                // there is no saved instant to accrue from until one is written. The same
                // opening also books the alerts for whatever was already in flight — a colony
                // restored from disk has a schedule that no longer exists on the device.
                resumed.commit(store, notifications, clock)
            }

            val current = session
            if (current != null) {
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1.seconds)
                        val previous = session ?: continue
                        // The wall clock can step backwards (NTP, the user changing device
                        // time); core's advance requires to >= from, so the boundary clamps.
                        val now = maxOf(debugClock.now(Clock.System.now()), previous.lastUpdatedAt)
                        val next = previous.copy(
                            state = advance(previous.state, from = previous.lastUpdatedAt, to = now),
                            lastUpdatedAt = now,
                        )
                        session = next
                        if (next.hasNewEventsSince(previous)) next.commit(store, notifications, debugClock)
                    }
                }

                // Both actions follow the same path, and it is the only safe one: bring the
                // simulation up to the instant the player acted, ask core to apply the action at
                // that instant, then commit if the event log grew. Acting on a stale state would
                // spend resources the colony has not accrued yet.
                fun act(transition: (GameState, Instant) -> GameState) {
                    val at = maxOf(debugClock.now(Clock.System.now()), current.lastUpdatedAt)
                    val advanced = advance(current.state, from = current.lastUpdatedAt, to = at)
                    val next = current.copy(state = transition(advanced, at), lastUpdatedAt = at)
                    session = next
                    if (next.hasNewEventsSince(current)) {
                        scope.launch { next.commit(store, notifications, debugClock) }
                    }
                }

                // The debug menu's one time verb. It is `act`'s shape with two differences, and
                // both are the point: the instant is chosen by the simulation rather than by the
                // clock, and it commits unconditionally — a skip that changed no event still moved
                // the colony's clock, and the offset only survives a relaunch because the save
                // records the instant it reached.
                fun skip() {
                    val wall = Clock.System.now()
                    val at = maxOf(debugClock.now(wall), current.lastUpdatedAt)
                    val advanced = advance(current.state, from = current.lastUpdatedAt, to = at)
                    val target = skipAhead(advanced, now = at).to
                    val clock = debugClock.skippingTo(target, wallClock = wall)
                    debugClock = clock
                    val next = GameSession(
                        state = advance(advanced, from = at, to = target),
                        lastUpdatedAt = target,
                        debugUsed = true,
                    )
                    session = next
                    scope.launch { next.commit(store, notifications, clock) }
                }

                // Delete, then resume from nothing — which is exactly the path a first launch
                // takes, so a reset is a first launch rather than a second way of building one.
                // The offset goes with it: a new colony is not the old one's future, and starting
                // it hours ahead of the wall clock would be inheriting a debt it never ran up.
                fun reset() {
                    scope.launch {
                        store.clear()
                        val wall = Clock.System.now()
                        val clock = DebugClock()
                        debugClock = clock
                        // The flag survives the wipe, deliberately: the colony that comes back was
                        // made by the debug menu, and nothing in the game ever clears the mark.
                        val fresh = resume(saved = null, now = wall).copy(debugUsed = true)
                        session = fresh
                        fresh.commit(store, notifications, clock)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MainScaffold(
                        resources = current.state.toResourceRailUiState(),
                        colony = {
                            ColonyScreen(
                                uiState = current.state.toColonyUiState(
                                    now = current.lastUpdatedAt,
                                    timeZone = TimeZone.currentSystemDefault(),
                                ),
                                onUpgrade = { building ->
                                    act { state, at ->
                                        when (val result = startUpgrade(state, building, at = at)) {
                                            is StartUpgradeResult.Started -> result.state
                                            StartUpgradeResult.AlreadyUpgrading,
                                            StartUpgradeResult.InsufficientResources,
                                            StartUpgradeResult.RequirementsNotMet,
                                            -> state
                                        }
                                    }
                                },
                            )
                        },
                        research = {
                            ResearchScreen(
                                uiState = current.state.toResearchUiState(
                                    now = current.lastUpdatedAt,
                                    timeZone = TimeZone.currentSystemDefault(),
                                ),
                                onStartResearch = { technology ->
                                    act { state, at ->
                                        when (val result = startResearch(state, technology, at = at)) {
                                            is StartResearchResult.Started -> result.state
                                            StartResearchResult.SlotBusy,
                                            StartResearchResult.InsufficientResources,
                                            StartResearchResult.RequirementsNotMet,
                                            -> state
                                        }
                                    }
                                },
                                // The same path, the same shape, the same refusals. The two branches
                                // differ in what they buy, not in how they are bought — and a busy
                                // slot refuses both, whichever kind of project is holding it.
                                onStartAdaptation = { technology ->
                                    act { state, at ->
                                        when (val result = startAdaptation(state, technology, at = at)) {
                                            is StartAdaptationResult.Started -> result.state
                                            StartAdaptationResult.SlotBusy,
                                            StartAdaptationResult.InsufficientResources,
                                            StartAdaptationResult.RequirementsNotMet,
                                            -> state
                                        }
                                    }
                                },
                            )
                        },
                        // The galaxy stopped being read-only at 0.2.0: surveying is a colony action
                        // with no ship in it, so the fourth verb goes through the same path the other
                        // three do. Which system is on screen is still the feature's own navigation
                        // rather than the shell's — what it asks the shell for is the way to the
                        // Research tab, because a blocked world's remedy is a tap target and only the
                        // scaffold can change destination.
                        galaxy = { openResearch ->
                            GalaxyScreen(
                                state = current.state,
                                now = current.lastUpdatedAt,
                                timeZone = TimeZone.currentSystemDefault(),
                                onOpenResearch = openResearch,
                                onDispatchProbe = { target ->
                                    act { state, at ->
                                        when (val result = startSurvey(state, target, at = at)) {
                                            is StartSurveyResult.Started -> result.state
                                            StartSurveyResult.AlreadySurveying,
                                            StartSurveyResult.AlreadySurveyed,
                                            StartSurveyResult.InsufficientResources,
                                            -> state
                                        }
                                    }
                                },
                            )
                        },
                    )

                    if (debugOpen) {
                        DebugSheet(
                            report = debugReport(
                                state = current.state,
                                gameTime = current.lastUpdatedAt,
                                // Derived rather than read, so the two clocks on the panel differ
                                // by exactly the offset and cannot disagree with each other. It
                                // also keeps composition free of a clock read, which would be
                                // stale the frame after it happened.
                                wallTime = debugClock.toRealTime(current.lastUpdatedAt),
                                debugUsed = current.debugUsed,
                            ),
                            // Left open on purpose: skipping is the action you repeat, and watching
                            // the readings move is most of what the panel is for.
                            onSkipAhead = { skip() },
                            onReset = {
                                reset()
                                debugOpen = false
                            },
                            onDismiss = { debugOpen = false },
                        )
                    }
                }
            }
        }
    }
}
