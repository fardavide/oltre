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
import dev.fardavide.oltre.client.debug.presentation.DebugSheet
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.galaxy.presentation.GalaxyScreen
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.notifications.data.defaultNotificationScheduler
import dev.fardavide.oltre.client.research.presentation.ResearchScreen
import dev.fardavide.oltre.client.research.presentation.toResearchUiState
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.defaultSaveFile
import dev.fardavide.oltre.client.tilt.data.TiltSource
import dev.fardavide.oltre.client.tilt.data.defaultTiltSource
import dev.fardavide.oltre.client.tilt.domain.Tilt
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.startAdaptation
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.startUpgrade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// How long the rail goes on having somewhere to roll from. The roll itself takes 900ms; this is
// comfortably past it and well short of anything a player would still be reading.
private val ARRIVAL_WINDOW: Duration = 2.seconds

// The shell is the impure boundary: it reads the clock, reads and writes the save file, books
// the local notifications, ticks the UI, and holds the current session. Game state itself only
// ever moves through core's advance/startUpgrade.
//
// Since 0.2.5 it reads the clock through a `DebugClock` rather than directly. At ×1 — every launch
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
    // The other device service, and a parameter for the same reason: a test or an entry point can
    // hand in a different one, and desktop's `actual` reports a sky that never leans.
    tiltSource: TiltSource = remember { defaultTiltSource() },
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
            // What this launch found, in two halves that are forgotten at two different moments —
            // because the two things that announce them live in two different places.
            //
            // The stocks the player last saw. The rail is chrome and is composed from the first
            // frame, so this one is always seen and is dropped on a timer.
            var lastSeen by remember { mutableStateOf<Resources?>(null) }
            // The one job that landed while the app was closed. **Not on a timer**: the screen that
            // announces it may not be the one the app opens on, and a research project's row does
            // not exist until the player taps the tab. It is dropped by whichever destination shows
            // it, the moment it has been shown — and by the first action the player takes, because
            // once they have changed the colony themselves, "while you were away" is old news.
            var finishedWhileAway by remember { mutableStateOf<AwayCompletion?>(null) }

            LaunchedEffect(shakeDetector) {
                shakeDetector.shakes().collect { debugOpen = true }
            }

            // How the device is being held, for the starfield behind every destination.
            //
            // **`mutableStateOf` written here and read only inside a draw lambda**, which is the
            // whole reason this is affordable at all. Compose invalidates by phase: a snapshot read
            // that happens during composition schedules a recomposition, and one that happens
            // during draw schedules only a redraw. This value is never read in a composable body —
            // it is handed down as `{ tilt }` and unwrapped inside `Canvas`'s `DrawScope` — so a
            // sensor sample repaints a hundred and one circles and touches nothing else. Read it in
            // the body instead and every lean would recompose the whole destination, screen
            // included, fifty times a second.
            //
            // Started once and collected for as long as the app is composed. `TiltSource` stops the
            // sensor when collection ends, and the flow goes quiet by itself whenever the phone is
            // still, which is most of the time.
            var tilt by remember { mutableStateOf(Tilt.NONE) }
            LaunchedEffect(tiltSource) {
                tiltSource.tilts().collect { tilt = it }
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
                // Before the session is published, so the first frame the player sees is already
                // the one that knows what to announce. Setting it afterwards would compose the rail
                // once with nothing to roll from and only then hand it a starting figure — which is
                // a roll that begins in the wrong place.
                arrivalOf(saved = saved?.state, resumed = resumed.state)?.let { arrival ->
                    lastSeen = arrival.lastSeen
                    finishedWhileAway = arrival.finished
                }
                session = resumed
                // Commit immediately, save included: a player who opens the game once and
                // closes it must still come back to hours of production, and on a first launch
                // there is no saved instant to accrue from until one is written. The same
                // opening also books the alerts for whatever was already in flight — a colony
                // restored from disk has a schedule that no longer exists on the device.
                resumed.commit(store, notifications, clock)
            }

            // The roll's window. Without it the rail would roll a second time from a figure nobody
            // has been looking at since — `lastSeen` feeds a `remember`ed animation, so it only has
            // to survive long enough for that animation to start.
            LaunchedEffect(lastSeen) {
                if (lastSeen != null) {
                    delay(ARRIVAL_WINDOW)
                    lastSeen = null
                }
            }

            val current = session
            if (current != null) {
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1.seconds)
                        val previous = session ?: continue
                        val next = previous.ticked(debugClock, wallClock = Clock.System.now())
                        session = next
                        if (next.hasNewEventsSince(previous)) next.commit(store, notifications, debugClock)
                    }
                }

                // Both actions follow the same path, and it is the only safe one: bring the
                // simulation up to the instant the player acted, ask core to apply the action at
                // that instant, then commit if the event log grew. Acting on a stale state would
                // spend resources the colony has not accrued yet.
                fun act(transition: (GameState, Instant) -> GameState) {
                    // Whatever landed while the app was closed stops being news the moment the
                    // player changes the colony themselves.
                    finishedWhileAway = null
                    val next = current.acting(debugClock, wallClock = Clock.System.now(), transition = transition)
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
                    val outcome = current.skipped(debugClock, wallClock = Clock.System.now())
                    debugClock = outcome.clock
                    session = outcome.session
                    scope.launch { outcome.session.commit(store, notifications, outcome.clock) }
                }

                // Delete, then resume from nothing — which is exactly the path a first launch
                // takes, so a reset is a first launch rather than a second way of building one.
                fun reset() {
                    scope.launch {
                        store.clear()
                        val outcome = resetColony(wallClock = Clock.System.now())
                        debugClock = outcome.clock
                        session = outcome.session
                        outcome.session.commit(store, notifications, outcome.clock)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MainScaffold(
                        resources = current.state.toResourceRailUiState(lastSeen = lastSeen),
                        tilt = { tilt },
                        colony = { scroll ->
                            val finishedFacility = (finishedWhileAway as? AwayCompletion.Facility)?.building
                            // Consumed by the screen that shows it. Effects run after the frame that
                            // composed them, and the row latches the announcement while it composes
                            // — so by the time this clears it, the sweep is already running and
                            // cannot be cut short. What it prevents is the second showing.
                            if (finishedFacility != null) {
                                LaunchedEffect(finishedFacility) { finishedWhileAway = null }
                            }
                            ColonyScreen(
                                scrollState = scroll,
                                uiState = current.state.toColonyUiState(
                                    now = current.lastUpdatedAt,
                                    timeZone = TimeZone.currentSystemDefault(),
                                    finishedWhileAway = finishedFacility,
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
                        research = { scroll ->
                            // The reason `finishedWhileAway` is not on a timer: this row does not
                            // exist until the player taps the tab, which may be a minute after the
                            // launch that has something to tell them about it.
                            val finishedProject = finishedWhileAway?.toResearchArrival()
                            if (finishedProject != null) {
                                LaunchedEffect(finishedProject) { finishedWhileAway = null }
                            }
                            ResearchScreen(
                                scrollState = scroll,
                                uiState = current.state.toResearchUiState(
                                    now = current.lastUpdatedAt,
                                    timeZone = TimeZone.currentSystemDefault(),
                                    finishedWhileAway = finishedProject,
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
                        galaxy = { scroll, openResearch ->
                            GalaxyScreen(
                                scrollState = scroll,
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
