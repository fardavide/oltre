package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.intl.Locale
import dev.fardavide.oltre.client.colony.presentation.toColonyUiState
import dev.fardavide.oltre.client.colony.ui.ColonyScreen
import dev.fardavide.oltre.client.debug.data.ShakeDetector
import dev.fardavide.oltre.client.debug.data.defaultShakeDetector
import dev.fardavide.oltre.client.debug.domain.DebugClock
import dev.fardavide.oltre.client.debug.domain.debugReport
import dev.fardavide.oltre.client.debug.ui.DebugSheet
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.client.design.text.translationsFor
import dev.fardavide.oltre.client.fleets.presentation.FleetsScreen
import dev.fardavide.oltre.client.fleets.presentation.toFleetsUiState
import dev.fardavide.oltre.client.galaxy.presentation.GalaxyLanding
import dev.fardavide.oltre.client.galaxy.presentation.GalaxyScreen
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.notifications.data.defaultNotificationScheduler
import dev.fardavide.oltre.client.player.presentation.toPlayerStripUiState
import dev.fardavide.oltre.client.research.presentation.toResearchUiState
import dev.fardavide.oltre.client.research.ui.ResearchScreen
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.Preferences
import dev.fardavide.oltre.client.save.data.PreferencesStore
import dev.fardavide.oltre.client.save.data.defaultPreferencesFile
import dev.fardavide.oltre.client.save.data.defaultSaveFile
import dev.fardavide.oltre.client.shipyard.presentation.toShipyardUiState
import dev.fardavide.oltre.client.shipyard.ui.ShipyardScreen
import dev.fardavide.oltre.client.tilt.data.TiltSource
import dev.fardavide.oltre.client.tilt.data.defaultTiltSource
import dev.fardavide.oltre.client.tilt.domain.Tilt
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.NotificationSettings
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.buildShips
import dev.fardavide.oltre.core.startAdaptation
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startRun
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.core.toggleAlert
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

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
    // A second file beside the save, holding what the app remembers about itself rather than about
    // the colony — one field today, which is which of the Galaxy tab's two lists it lands on. Kept
    // out of `GameStore` deliberately: a preference must never be able to cost somebody a colony,
    // and separate files mean a corrupt one of either kind takes only its own down.
    preferences: PreferencesStore = remember { PreferencesStore(defaultPreferencesFile()) },
    // **The one place the game's language is chosen**, and it is chosen once — from the device, with
    // no picker (Davide, 2026-08-16). That call read "no picker and no settings surface anywhere in
    // the app" until 0.16.0 put a settings button on the frame; the language half is untouched, and
    // `TranslationsFor` carries why. The shell reads the locale because it is the only place both
    // halves are in scope: the UI half reaches
    // the table through `OltreTheme`'s ambient, and `GameNotifications` takes it as a parameter
    // because it writes copy into the OS's own database hours before anything is composing.
    //
    // **Neither half needs a locale-change event, and that is the call paying for itself.** Both
    // platforms restart the app when the system language changes — Android recreates the activity on
    // the configuration change, iOS relaunches the process — so this is re-read and the `commit`
    // below re-books every alert on the way back in, through the path that already exists. What is
    // left is that alerts already sitting with the OS keep their old wording until the next
    // foreground, which is the same window every other number in this game lives with.
    //
    // Still a parameter, and still for the reason it was one before there was a second table: a test
    // hands in whichever language the frame is about.
    translations: Translations = remember { translationsFor(Locale.current.toLanguageTag()) },
    notifications: GameNotifications = remember(translations) {
        GameNotifications(defaultNotificationScheduler(), translations)
    },
    // A parameter rather than a call, so the desktop entry point can hand in its keyboard chord —
    // a laptop cannot be shaken, and desktop is the platform where the menu is most wanted.
    shakeDetector: ShakeDetector = remember { defaultShakeDetector() },
    // The other device service, and a parameter for the same reason: a test or an entry point can
    // hand in a different one, and desktop's `actual` reports a sky that never leans.
    tiltSource: TiltSource = remember { defaultTiltSource() },
    // **The last seam, and the one the suite was measurably wrong without.** Every other parameter
    // here exists so a test is not at the mercy of the machine it runs on; the clock was the hole in
    // that, and it cost more than the others would have.
    //
    // A launch with no save mints its galaxy from the instant it happened — `resume` derives the seed
    // from `now`, deliberately, so that a new colony gets a new map. Read through the wall clock,
    // that made `app(saved = null)` generate **a different galaxy on every run**, and `homeFor`'s walk
    // covered a different set of branches each time. Measured 2026-08-21: `GalaxyGeneration.kt` line
    // 342 flipped between covered and missed across runs of identical code, moving behaviour branch
    // coverage across the 66.85% rounding line — and the coverage gate is a ratchet with no slack, so
    // roughly every other pull request failed it for a number that was never about the code.
    //
    // `Clock.System` in production, a fixed instant in a test. Note this is the *wall* clock, still
    // read through `DebugClock` below, so a skip behaves exactly as it did.
    wallClock: Clock = Clock.System,
    modifier: Modifier = Modifier,
) {
    // Handed to the theme rather than provided around it: `OltreTheme` is what every frame, preview
    // and behaviour test already wraps itself in, so the ambient belongs inside it — see there.
    OltreTheme(translations = translations) {
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
            // **The map until the player says otherwise.** Claude Design's call for the landing
            // screen, with Davide's amendment that it should then follow whichever list was last
            // used. Read once, written on every switch, and the tab is composed with the map before
            // the file comes back — which is right rather than a race, because the map is the
            // default and a first launch has no file to wait for.
            var galaxyLanding by remember { mutableStateOf(GalaxyLanding.MAP) }
            // **What the player said about being interrupted**, read from the same file as the
            // landing above and held here for the same reason: it is not a fact about the colony, so
            // it is not in the session. Everything downstream of it is a rendering — which controls
            // a row draws, and which instants are worth booking.
            //
            // Defaulted to what every colony is already in, so the frames composed before the file
            // comes back are the frames 0.17 drew.
            var alerts by remember { mutableStateOf(NotificationSettings.DEFAULT) }

            LaunchedEffect(shakeDetector) {
                shakeDetector.shakes().collect { debugOpen = true }
            }

            // How the device is being held, for the starfield behind every destination.
            //
            // **A `State` handle rather than a `by` delegate, and that is not style.** Compose
            // invalidates by phase: a snapshot read during composition schedules a recomposition,
            // one during draw schedules only a redraw. This value must only ever be read in the
            // second place — it goes down as `{ lean.value }` and is unwrapped inside `Canvas`'s
            // `DrawScope`, so a sensor sample repaints a hundred and one circles and touches nothing
            // else. A delegate would make an accidental read in a composable body look like an
            // ordinary variable, and nothing would fail: the app would simply recompose every
            // destination fifty times a second. `.value` at every read site says out loud what is
            // being touched.
            //
            // The default `structuralEqualityPolicy` is the other half of the still-phone promise:
            // `TiltMonitor` snaps its values to a grid, so a hand that is not moving writes a `Tilt`
            // equal to the last one and Compose does not invalidate at all.
            //
            // Collected for as long as the app is composed, which is longer than the sensor actually
            // runs: iOS suspends a backgrounded app and Android has cut continuous sensors off for
            // background apps since API 28, so neither platform needs a lifecycle hook this
            // repository does not have. Gating on `LocalWindowInfo.isWindowFocused` would also stop
            // it under a pulled-down shade — worth doing the day somebody measures a battery cost,
            // and not worth the risk before then, since a focus flag that reads `false` on iOS would
            // silently switch the whole feature off with nothing to notice it.
            val lean = remember { mutableStateOf(Tilt.NONE) }
            LaunchedEffect(tiltSource) {
                tiltSource.tilts().collect { lean.value = it }
            }

            LaunchedEffect(Unit) {
                // **Read before the colony is, and in the same effect it is read in** — which is the
                // one ordering that is not a race. The launch's own `commit` a few lines down books
                // every alert for a colony restored from disk, and it has to book them the way the
                // player asked: a separate effect would leave the first sync of every launch running
                // on the defaults, and nothing would come back to redo it until the next transition.
                val stored = preferences.load()
                galaxyLanding = stored.galaxyLanding.toGalaxyLanding()
                // Null is a player who has never opened the settings screen, which is every player
                // until 0.18 — see `Preferences.notifications`.
                val settings = stored.notifications ?: NotificationSettings.DEFAULT
                alerts = settings
                val saved = store.load()
                val wall = wallClock.now()
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
                // The value this effect resolved rather than the state that mirrors it, so what is
                // booked is what was read off the file a few lines up whatever else has touched the
                // variable since.
                resumed.commit(store, notifications, settings, clock)
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
                        val next = previous.ticked(debugClock, wallClock = wallClock.now())
                        session = next
                        if (next.hasNewEventsSince(previous)) next.commit(store, notifications, alerts, debugClock)
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
                    val next = current.acting(debugClock, wallClock = wallClock.now(), transition = transition)
                    session = next
                    if (next.hasNewEventsSince(current)) {
                        scope.launch { next.commit(store, notifications, alerts, debugClock) }
                    }
                }

                // The square, and it is `act`'s shape with one difference that matters: **it commits
                // unconditionally.** Asking for an alert writes no event — nothing happened, a row
                // was pointed at — so `hasNewEventsSince` is false and `act` would decline to save.
                // The save is not the point either: booking the alert is, and the alert is only
                // booked by the `notifications.sync` inside `commit`. A square that lit up and told
                // nobody is the whole feature failing. Same shape as `skip()`, for the same reason.
                //
                // Which of the two things a tap means — watch this price, or tell me when this
                // lands — is core's to decide from the row's state, not the screen's to declare.
                // See `toggleAlert`, and `alerting` for why this one verb transitions before it
                // advances where every other one does the opposite.
                fun alert(target: WatchTarget) {
                    val next = current.alerting(debugClock, wallClock = wallClock.now(), target = target)
                    session = next
                    scope.launch { next.commit(store, notifications, alerts, debugClock) }
                }

                // The Shipyard's square, and the same shape for the same reasons — a hull card is
                // asked about with `cycleHullAlert` rather than `toggleAlert` because a queue has two
                // questions where a row has one, and everything else about this verb is `alert`'s:
                // it writes no event, so it has to commit unconditionally or the alert it just booked
                // would never reach the platform.
                fun alertHull(ship: ShipType) {
                    val next = current.alertingHull(debugClock, wallClock = wallClock.now(), ship = ship)
                    session = next
                    scope.launch { next.commit(store, notifications, alerts, debugClock) }
                }

                // The dispatch sheet's bell, and the map card's — one verb, because there is one
                // answer and both controls set it.
                //
                // **It commits, and unlike the two above it the commit is a save rather than a
                // booking.** Nothing is in flight when this is tapped, so `notifications.sync` has
                // nothing new to schedule; what has to survive is the *position of the control*,
                // which is the whole of what "the bell remembers" means. `act` would decline —
                // this writes no event — so it commits unconditionally, exactly as `alert` does.
                fun alertFlights() {
                    val next = current.alertingFlights(debugClock, wallClock = wallClock.now())
                    session = next
                    scope.launch { next.commit(store, notifications, alerts, debugClock) }
                }

                // The debug menu's one time verb. It is `act`'s shape with two differences, and
                // both are the point: the instant is chosen by the simulation rather than by the
                // clock, and it commits unconditionally — a skip that changed no event still moved
                // the colony's clock, and the offset only survives a relaunch because the save
                // records the instant it reached.
                fun skip() {
                    val outcome = current.skipped(debugClock, wallClock = wallClock.now())
                    debugClock = outcome.clock
                    session = outcome.session
                    scope.launch { outcome.session.commit(store, notifications, alerts, outcome.clock) }
                }

                // Delete, then resume from nothing — which is exactly the path a first launch
                // takes, so a reset is a first launch rather than a second way of building one.
                fun reset() {
                    scope.launch {
                        store.clear()
                        val outcome = resetColony(wallClock = wallClock.now())
                        debugClock = outcome.clock
                        session = outcome.session
                        outcome.session.commit(store, notifications, alerts, outcome.clock)
                    }
                }

                // `BoxWithConstraints` rather than `Box`, and for one string: the heading over both
                // lists names the watched row, and at a Slide Over's width the row calls itself
                // something shorter. The two destinations measure the same window for themselves —
                // this is the shell's own copy of that decision, and it exists because the label is
                // composed here rather than inside either screen.
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // One string for both destinations, because there is one watch: see
                    // `watchingLabel`.
                    // **One verb, two doors.** A run is raised from a world row on Galaxy and from
                    // a worked row on Fleets, and `startRun`'s five refusals are the same five either
                    // way — so this is declared once rather than pasted into both. The first cut of
                    // 0.13 did paste it, and the twenty lines the Fleets copy added were reachable by
                    // no test in the repository, which is how the coverage gate found them.
                    val dispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Unit =
                        { target, gathering, ships, window ->
                            act { state, at ->
                                when (
                                    val result = startRun(
                                        state = state,
                                        target = target,
                                        gathering = gathering,
                                        ships = ships,
                                        window = window,
                                        at = at,
                                    )
                                ) {
                                    is StartRunResult.Started -> result.state
                                    // None of the six is reachable from a finger: both sheets are
                                    // built so the verb is absent wherever the model would refuse.
                                    // This `when` says so out loud rather than trusting it — and
                                    // `NotAGatheringHull` is the newest of them, unreachable because
                                    // the manifest picker only ever offers hulls that have a hold.
                                    StartRunResult.Unsurveyed,
                                    StartRunResult.NotAValidTarget,
                                    StartRunResult.NoSuchShips,
                                    StartRunResult.NotAGatheringHull,
                                    StartRunResult.WindowTooShort,
                                    StartRunResult.Depleted,
                                    -> state
                                }
                            }
                        }
                    val watching = current.state.watching
                        ?.watchingLabel(compact = maxWidth < OltreLayout.compactWidth)

                    MainScaffold(
                        // **Read off the save now rather than declared here.** The level and the
                        // gauge are a fold over `current.state.eventLog`, so a colony carried
                        // forward from 0.16 opens on the level it had already earned and nothing
                        // was migrated to give it one. See `core`'s `Experience.kt`.
                        player = current.state.toPlayerStripUiState(),
                        resources = current.state.toResourceRailUiState(lastSeen = lastSeen),
                        tilt = { lean.value },
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
                                    watching = watching,
                                    alerts = alerts,
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
                                onToggleWatch = { building -> alert(WatchTarget.Facility(building)) },
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
                                    watching = watching,
                                    alerts = alerts,
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
                                // Two callbacks rather than one taking a `WatchTarget`, so the
                                // screen keeps speaking in its own two vocabularies exactly as its
                                // two start verbs do. Assembling the target is the shell's job,
                                // which is also the only place both branches are in scope.
                                onToggleTechnologyWatch = { technology ->
                                    alert(WatchTarget.Project(technology))
                                },
                                onToggleAdaptationWatch = { technology ->
                                    alert(WatchTarget.Ladder(technology))
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
                                // What the ledger's discovery section is measured from: the instant
                                // this launch advanced from, so a world surveyed while the app was
                                // closed is new and one surveyed before that is not.
                                since = current.resumedFrom,
                                timeZone = TimeZone.currentSystemDefault(),
                                onOpenResearch = openResearch,
                                landing = galaxyLanding,
                                onLandingChange = { chosen ->
                                    galaxyLanding = chosen
                                    // The whole record every time, because that is what a write is
                                    // here — there is no merge and no partial save, so a field left
                                    // out is a field cleared.
                                    scope.launch {
                                        preferences.save(
                                            Preferences(galaxyLanding = chosen.name, notifications = alerts),
                                        )
                                    }
                                },
                                onDispatchProbe = { target ->
                                    act { state, at ->
                                        when (val result = startSurvey(state, target, at = at)) {
                                            is StartSurveyResult.Started -> result.state
                                            StartSurveyResult.AlreadySurveying,
                                            StartSurveyResult.AlreadySurveyed,
                                            StartSurveyResult.NoIdleScout,
                                            StartSurveyResult.InsufficientResources,
                                            -> state
                                        }
                                    }
                                },
                                // **The fifth verb, reaching a finger for the first time.** `core`
                                // has carried `startRun` since 0.3.0 and nothing called it: the
                                // balance existed, the save format carried it, `advance` landed the
                                // cargo, and a player tapping a world got nothing at all.
                                //
                                // Three subjects rather than one, because they are three facets of
                                // one commitment — see `startRun`, which takes them the same way for
                                // the same reason. The five refusals are exhaustive and every one of
                                // them returns the state untouched: the sheet is built so that none
                                // is reachable from a finger, and this `when` is what says so out
                                // loud rather than trusting it.
                                onDispatchRun = dispatchRun,
                                onToggleAnnounce = { alertFlights() },
                                alerts = alerts,
                            )
                        },
                        // **The sixth verb, and the first shop in the game.** `FleetBalance.shipCost`
                        // has been priced, tested and pinned in the benchmark since 0.3.0 with no
                        // production caller at all — the fleet was one granted skiff that could
                        // never become two, which is most of why exploring paid so little.
                        //
                        // One hull a tap, not a quantity picker. `buildShips` takes a whole manifest
                        // and prices it rung by rung, which is what slice 4 will need the day a
                        // second hull is on sale; what a card can express today is "another one of
                        // these", and a stepper for a purchase you can simply repeat is a control
                        // bought for nothing.
                        shipyard = { scroll ->
                            ShipyardScreen(
                                scrollState = scroll,
                                uiState = current.state.toShipyardUiState(
                                    now = current.lastUpdatedAt,
                                    timeZone = TimeZone.currentSystemDefault(),
                                    alerts = alerts,
                                ),
                                onBuild = { type ->
                                    act { state, at ->
                                        when (val result = buildShips(state, Ships.of(type, 1), at = at)) {
                                            is BuildShipsResult.Started -> result.state
                                            // Exhaustive and every branch returns the state
                                            // untouched: the card is built so that none of the three
                                            // is reachable from a finger — the button is a ghost
                                            // while the price is short, the manifest is never empty,
                                            // and a hull with no price is drawn as a dimmed card
                                            // with nothing to press. This `when` is what says so out
                                            // loud rather than trusting it.
                                            BuildShipsResult.NothingToBuild,
                                            BuildShipsResult.NotForSale,
                                            BuildShipsResult.InsufficientResources,
                                            -> state
                                        }
                                    }
                                },
                                onToggleAlert = { type -> alertHull(type) },
                            )
                        },
                        // **It stopped being read-only at 0.13**, which is issue #62. A run in flight
                        // is still something to watch rather than something to change — there is no
                        // cancel and no recall anywhere in this game, and the cargo is fixed at
                        // dispatch — but the list of worlds you have worked is a door back to one,
                        // and it raises the same sheet the Galaxy tab raises.
                        fleets = { scroll ->
                            FleetsScreen(
                                scrollState = scroll,
                                state = current.state,
                                now = current.lastUpdatedAt,
                                // What the landing clock is measured from, exactly as it is on the
                                // Galaxy tab: a world that came home while the app was closed says
                                // so, and one that came home before that has nothing new to report.
                                since = current.resumedFrom,
                                timeZone = TimeZone.currentSystemDefault(),
                                // The same four lines the Galaxy tab spends, and deliberately not
                                // hoisted into one: `startRun`'s refusals are the sheet's own
                                // subject, and a shared lambda would put the two tabs' error
                                // handling in a place neither of them owns.
                                onDispatchRun = dispatchRun,
                                onToggleAnnounce = { alertFlights() },
                                alerts = alerts,
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

// **The composition root is where the two vocabularies meet**, and that is not an accident of layout:
// `:client:save:data` may not see a `presentation` module, so the file stores the name of the landing
// and this is the one place that knows what the name means. An unreadable or unknown value is the
// map, which is also what a first launch gets — so a preferences file corrupted between builds costs
// a player one tap rather than a wrong screen.
private fun String?.toGalaxyLanding(): GalaxyLanding =
    GalaxyLanding.entries.firstOrNull { it.name == this } ?: GalaxyLanding.MAP
