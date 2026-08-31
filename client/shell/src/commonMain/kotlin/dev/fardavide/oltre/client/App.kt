package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.Box
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
import dev.fardavide.oltre.client.auth.data.ProviderSignIn
import dev.fardavide.oltre.client.auth.data.SignInAttempt
import dev.fardavide.oltre.client.auth.data.defaultProviderSignIn
import dev.fardavide.oltre.client.auth.data.signInProviders
import dev.fardavide.oltre.client.auth.presentation.DeleteFace
import dev.fardavide.oltre.client.auth.presentation.GateState
import dev.fardavide.oltre.client.auth.presentation.toDeleteFaceUiState
import dev.fardavide.oltre.client.auth.presentation.toGateUiState
import dev.fardavide.oltre.client.auth.ui.Gate
import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.design.text.AuthProviderName
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.net.data.ActOutcome
import dev.fardavide.oltre.client.net.data.ApiResult
import dev.fardavide.oltre.client.net.data.ColonySync
import dev.fardavide.oltre.client.net.data.Credential
import dev.fardavide.oltre.client.net.data.IdempotencyKeys
import dev.fardavide.oltre.client.net.data.defaultOltreApi
import dev.fardavide.oltre.client.net.data.OltreApi
import dev.fardavide.oltre.client.net.data.Outbox
import dev.fardavide.oltre.client.net.data.OutboxFile
import dev.fardavide.oltre.client.net.data.RetryPolicy
import dev.fardavide.oltre.client.net.data.SessionKeeper
import dev.fardavide.oltre.client.net.data.SessionStore
import dev.fardavide.oltre.client.net.data.renewing
import dev.fardavide.oltre.client.net.data.SyncOutcome
import dev.fardavide.oltre.client.net.data.WithdrawResult
import dev.fardavide.oltre.client.net.data.randomIdempotencyKeys
import dev.fardavide.oltre.client.net.domain.HeldActions
import dev.fardavide.oltre.client.save.data.defaultOutboxFile
import dev.fardavide.oltre.client.save.data.defaultSessionFile
import dev.fardavide.oltre.client.settings.ui.AccountUiState
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.offlineRule
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.toLocalDateTime
import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.changelog.domain.shouldOpenChangelog
import dev.fardavide.oltre.client.changelog.presentation.ChangelogText
import dev.fardavide.oltre.client.changelog.presentation.changelogFor
import dev.fardavide.oltre.client.changelog.presentation.toBuildRowUiState
import dev.fardavide.oltre.client.changelog.presentation.toChangelogUiState
import dev.fardavide.oltre.client.colony.presentation.toColonyUiState
import dev.fardavide.oltre.client.colony.ui.ColonyScreen
import dev.fardavide.oltre.client.debug.data.ShakeDetector
import dev.fardavide.oltre.client.debug.data.defaultShakeDetector
import dev.fardavide.oltre.client.debug.domain.DebugClock
import dev.fardavide.oltre.client.debug.domain.debugReport
import dev.fardavide.oltre.client.debug.ui.DebugSheet
import dev.fardavide.oltre.client.design.component.OltreBottomSheet
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
import dev.fardavide.oltre.client.player.presentation.spokenName
import dev.fardavide.oltre.client.player.presentation.toIdentityFaceUiState
import dev.fardavide.oltre.client.player.presentation.toMarkComposeFaceUiState
import dev.fardavide.oltre.client.player.presentation.toPlayerStripUiState
import dev.fardavide.oltre.client.player.presentation.withBody
import dev.fardavide.oltre.client.player.presentation.withPath
import dev.fardavide.oltre.client.player.presentation.withTerminus
import dev.fardavide.oltre.client.research.presentation.toResearchUiState
import dev.fardavide.oltre.client.research.ui.ResearchScreen
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.Preferences
import dev.fardavide.oltre.client.save.data.PreferencesStore
import dev.fardavide.oltre.client.save.data.defaultPreferencesFile
import dev.fardavide.oltre.client.save.data.defaultSaveFile
import dev.fardavide.oltre.client.settings.presentation.toAlertSheetUiState
import dev.fardavide.oltre.client.shipyard.presentation.toShipyardUiState
import dev.fardavide.oltre.client.shipyard.ui.ShipyardScreen
import dev.fardavide.oltre.client.tilt.data.TiltSource
import dev.fardavide.oltre.client.tilt.data.defaultTiltSource
import dev.fardavide.oltre.client.tilt.domain.Tilt
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.buildShips
import dev.fardavide.oltre.core.setAlertDelivery
import dev.fardavide.oltre.core.setAlertMode
import dev.fardavide.oltre.core.startAdaptation
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.core.toggleAlert
import dev.fardavide.oltre.core.toggleAlertCategory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone

// How long the rail goes on having somewhere to roll from. The roll itself takes 900ms; this is
// comfortably past it and well short of anything a player would still be reading.
private val ARRIVAL_WINDOW: Duration = 2.seconds

// The resolution the chrome line prints at, and therefore the resolution the instant behind it is
// worth storing at. See `arrive`.
private const val MILLIS_PER_MINUTE: Long = 60_000

// How many one-second ticks between attempts to drain a queue that could not be sent. See the tick
// loop, which is the only thing in the app that notices the network coming back.
private const val TICKS_PER_RETRY: Int = 60

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
    // **The one thing in the app whose language is chosen the same way and not by the same table.**
    // The changelog is a document per language rather than a catalogue of ids —
    // `.claude/docs/changelog-sheet.md` §4 — so it is picked from the same locale by a second
    // function, beside `translationsFor` rather than inside it. A parameter for the reason
    // `translations` is one: a test hands in whichever language the frame is about.
    changelog: ChangelogText = remember { changelogFor(Locale.current.toLanguageTag()) },
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
    // ── The colony is not here any more ──────────────────────────────────────────────────────
    //
    // **Five seams rather than one, and every one of them is what keeps the suite off the network.**
    // `App` is what the behaviour tests launch end to end, so a transport it built for itself would
    // put every one of them on a socket pointed at production — which is `#106` §8's whole point, and
    // the reason `FakeOltreApi` exists a slice before anything used it.
    api: OltreApi = remember { defaultOltreApi(OLTRE_BASE_URL) },
    // Two more files beside the save and the preferences, and separate from both for the reasons
    // their own ports state: a corrupt session costs a sign-in, a corrupt outbox costs taps.
    sessionStore: SessionStore = remember { SaveFileSessionStore(defaultSessionFile()) },
    outboxFile: OutboxFile = remember { SaveFileOutbox(defaultOutboxFile()) },
    // **The platform's half of the gate**, and the only thing in the app that opens a window somebody
    // else drew. A test hands in a lambda; a platform hands in whatever it has.
    signIn: ProviderSignIn = remember { defaultProviderSignIn() },
    // **Which buttons this build can complete**, which is a platform fact and not a preference — see
    // `signInProviders`. A provider that cannot finish is not drawn, because a button that opens a
    // browser which never comes back is the worst control a gate has available.
    providers: Set<AuthProvider> = remember { signInProviders() },
    // What makes a retry safe. A parameter for `IdempotencyKeys`' own reason: the property the whole
    // mechanism exists for is that a second attempt carries the first attempt's key, and no test can
    // assert that against a value it cannot predict.
    keys: IdempotencyKeys = remember { randomIdempotencyKeys(Random.Default) },
    modifier: Modifier = Modifier,
) {
    // Handed to the theme rather than provided around it: `OltreTheme` is what every frame, preview
    // and behaviour test already wraps itself in, so the ambient belongs inside it — see there.
    OltreTheme(translations = translations) {
        Surface(modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()

            // ── What is between this app and the colony ──────────────────────────────────────
            //
            // Built here and nowhere else. Three objects, and the layering is the whole design:
            // `Outbox` is the queue on disk, `SessionKeeper` is the credential and the only thing
            // that answers `SessionExpired`, and `ColonySync` is the one member above the transport
            // that decides anything. Every screen below asks `ColonySync` a question; none of them
            // holds a token, and none of them can.
            val outbox = remember(outboxFile) { Outbox(outboxFile) }
            val sessions = remember(api, sessionStore, wallClock) {
                SessionKeeper(api = api, store = sessionStore, clock = wallClock)
            }
            val colony = remember(api, outbox, keys, wallClock, sessions) {
                ColonySync(
                    api = api,
                    outbox = outbox,
                    keys = keys,
                    // **The wall clock and not the debug one.** `clientInstant` is a claim about when
                    // the player tapped, and the server clamps it into a window it can see; a colony
                    // skipped four hours forward would be claiming to have acted in the future, which
                    // the clamp would simply throw away. The debug menu moves *game* time.
                    clock = wallClock,
                    retry = RetryPolicy.DEFAULT,
                    sessions = sessions,
                )
            }

            // Null until the save has been read. Rendering a fresh colony first and swapping it
            // for the real one a frame later would flash wrong numbers at the player.
            var session by remember { mutableStateOf<GameSession?>(null) }
            // **Where the gate is, and it is the whole of what that screen decides.** `Idle` until a
            // provider is pressed; every other member is something that has just happened.
            var gate by remember { mutableStateOf<GateState>(GateState.Idle) }
            // **What the phone has accepted and the server has not**, read off the outbox after every
            // answer rather than tracked alongside it: the file is the state, exactly as it is inside
            // `Outbox`, and a second copy here is a second thing that can disagree with the disk.
            var held by remember { mutableStateOf(HeldActions.NONE) }
            // **The last instant the server answered, and whether it did last time.** Two fields
            // rather than one nullable, because they answer two different questions and only the pair
            // can say *"no network since 11:31"* — see `offlineLine`, which refuses to draw with one.
            var lastReachedAt by remember { mutableStateOf<Instant?>(null) }
            var reachable by remember { mutableStateOf(true) }
            // **What the last tap on a verb that cannot be held aimed at.** A fact about a tap rather
            // than about the world: it is cleared by the next tap, and a sheet reopened later has
            // nothing to say. Two of them, because the two verbs are refused on two different screens
            // and a single field would put a run's sentence on a probe's card.
            //
            // **The target rather than the sentence**, because the sentence has a clause that a Slide
            // Over pane drops — and how wide the window is is not known until `BoxWithConstraints`
            // below. Holding the fact and writing the words where the width is known is the same move
            // this file already makes for the watched row's label.
            var refusedRun by remember { mutableStateOf<GalaxyCoordinate?>(null) }
            var refusedProbe by remember { mutableStateOf(false) }
            // Whether the account deletion has been refused, which is the third refusal and the one
            // with a face of its own.
            var deleteRefused by remember { mutableStateOf(false) }
            // **When the server said to ask again, as an instant rather than as a countdown.** No
            // timers, ever, is older than this screen, so the wait is *recomputed when it is asked
            // for*: every impatient tap re-states the line with the seconds actually left, which also
            // makes each one visibly spend part of the wait. Nothing on the screen moves on its own.
            var throttledUntil by remember { mutableStateOf<Instant?>(null) }
            // Which provider signed this device in, for the two sentences that name it. Null until a
            // session exists, which is also when the Account section is absent.
            var provider by remember { mutableStateOf<AuthProvider?>(null) }
            // **Who is playing, as opposed to what their colony is.** This is the account rather than
            // the colony: it survives a colony being deleted and it is not in the save.
            //
            // **Null is *not read*, and it is a different thing from a profile whose two fields are
            // null.** That second state is an account that has chosen nothing, and the strip draws it
            // as `Threshold` and `Dead Reckoning` — so on screen the two are the same picture and
            // there is no spinner and no third face. What tells them apart is that **only one of them
            // may be written over**: `POST /v1/profile` replaces the row whole rather than merging, so
            // a `copy()` off an assumed-empty profile sends `{name: null}` and the server writes SQL
            // NULL over a name the account holds on every other device, silently and with no undo.
            //
            // This started as `PlayerProfile(null, null)` and that is exactly what happened — after a
            // gate sign-in, after a launch that began `Credential.Unreachable`, and after a read the
            // server simply refused. The nullable is what makes the compiler refuse the write instead
            // of a comment asking somebody to remember: there is no `profile.copy(…)` to reach for,
            // and the one function that resolves it is `wearMark` below. The face is held while this
            // is null, so nothing can reach that resolution with nothing to resolve.
            var profile by remember { mutableStateOf<PlayerProfile?>(null) }
            // **The draft is the shell's, and it has to be.** The identity face is swapped out
            // whole when the composer opens, so a draft the face remembered would be lost by going to
            // pick a mark and coming back.
            //
            // **Null is *the player has not typed*, and the mapper draws the committed name for it.**
            // A field seeded with what the server answered would be a fourth writer of this string —
            // the launch, the sign-in and the retry all learn a name — and every one of them would
            // land on top of typing already in progress. With null meaning *follow what is committed*
            // there is nothing to seed, and the events that end an edit put it back: the sheet
            // closing, and a **name** landing on the server. A mark landing must not, which is the
            // frame's own split and what `writeProfile`'s `renaming` says out loud.
            var nameDraft by remember { mutableStateOf<String?>(null) }
            // **One profile write at a time, because the row goes up whole.** The mark commits as it
            // is touched and the name has a button — the frame's own split — which makes them the one
            // pair of controls in this app that can be pressed inside each other's round trip. Two
            // independent `launch`es each posting the whole row means the later answer wins with the
            // *earlier* row's other field in it, so a name saved during a mark's flight put the
            // pre-tap mark back. Fair by construction: `Mutex` hands the lock out in the order it was
            // asked for, so the writes land in the order the taps happened.
            //
            // **And it belongs to a *session*, which is why it is a replaceable object rather than a
            // `remember`ed `Mutex`.** A write is launched on the scope above, which outlives
            // `endSession` and the branch leaving composition, so its answer can arrive at a process
            // that has since signed a different player in. One lock for the life of the process made
            // that worse in two ways at once: the orphan held it across the session change, so the
            // new player's first tap waited on a dead session's lock for as long as the far end took
            // and then met `?: return@withLock` and was dropped without a word.
            //
            // Replacing it is what says *this belongs to nobody now* in one assignment: the identity
            // of the object is the identity of the session, so an answer can ask whether it is still
            // the one that was launched, and the next session's writes queue behind nothing. The
            // deletion asks the same question for the same reason — see `AccountWork`.
            var accountWork by remember { mutableStateOf(AccountWork()) }
            // How far ahead of the wall clock this colony is running. Replaced rather than mutated,
            // so a skip is one state change that the tick loop and the commit both read.
            var debugClock by remember { mutableStateOf(DebugClock()) }
            var debugOpen by remember { mutableStateOf(false) }
            // The app's other modal, held here for the reason the debug sheet's flag is: both are
            // raised over whatever destination is showing, and a sheet is not a destination.
            //
            // **A flag rather than the count 0.16 used.** What the gear opened then was a notice on a
            // four-second timer, and a second tap had to restart the window rather than stack a
            // second bar — which a `Boolean` could not express. A sheet has no window: it is up until
            // something closes it, and the second tap is one of the things that closes it.
            //
            // **Since 0.19 it is a face rather than a flag**, because the same sheet carries two of
            // them: null is closed, and the two faces are the settings ladders and the changelog.
            // See `SettingsSheet` for why that is one sheet and not two.
            var sheetFace by remember { mutableStateOf<SheetFace?>(null) }
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
            // **The whole record, beside the one field of it the frame reads.** Since 0.19 the
            // preferences file holds two things, and a write is always about one of them — so the
            // other has to come from somewhere. Keeping the loaded record here is what makes
            // `copy(…)` possible: without it, saving a landing would have to invent a value for the
            // version whose changelog has been read, and inventing one is how a player gets told
            // twice or never.
            var remembered by remember { mutableStateOf(Preferences.NONE) }

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

            // Whether this launch found a colony on disk. `null` until the save has been read, which
            // is a third state and a load-bearing one — see the changelog gate below.
            var hadSave by remember { mutableStateOf<Boolean?>(null) }
            var preferencesLoaded by remember { mutableStateOf(false) }
            // **Whether there is a session, and `null` until the file has been read** — a third state
            // for `hadSave`'s reason. Drawing the gate before the store has answered would flash a
            // sign-in screen at a player who is signed in, which is the one thing that would make the
            // gate feel like a failure rather than a threshold.
            var signedIn by remember { mutableStateOf<Boolean?>(null) }

            // **A session ending, in one place — and it is one place because it was five.**
            //
            // Five arms end a session: a colony refused for a dead credential, a profile write
            // refused for one, a profile write with no credential left to make, and both of
            // `deleteAccount`'s. Every one of them wrote the same handful of assignments out by
            // hand, and when the account's *name and mark* were added they were added to exactly one
            // — so a session that ended through any of the other four left the previous player's
            // identity sitting in memory, where the strip drew it over whoever signed in next.
            //
            // **`profile` and `nameDraft` are the pair that makes that a data-loss bug rather than a
            // wrong drawing.** `POST /v1/profile` replaces the row whole, so a mark tapped before the
            // new account's read lands posts the *old* account's name over it, on every device, with
            // no undo. Null is *not read*, which is the one state nothing can be written from.
            //
            // **`gate = GateState.Idle` for all five**, including the two that used to leave it
            // wherever it was: what the gate reports is what has just happened to a *sign-in
            // attempt*, and a session that ended somewhere else has nothing to report about one.
            //
            // What is deliberately not here is anything about the *files*. Forgetting who is playing
            // is not the same act as deleting what they had, and only `deleteAccount` does the
            // second — see there, where the save and the queue go in a stated order.
            //
            // **The sixth thing it puts down is not state but a claim on the future**: a profile
            // write or a deletion already in flight belongs to the session that made it, and a fresh
            // `AccountWork` is what makes every arm of its answer a no-op. See `writeProfile`.
            suspend fun endSession() {
                sessions.forget()
                signedIn = false
                session = null
                sheetFace = null
                gate = GateState.Idle
                profile = null
                nameDraft = null
                accountWork = AccountWork()
            }

            // **What every answer from the server does**, in one place because every answer does the
            // same four things and a second copy is a second chance to forget one of them.
            //
            // The order is load-bearing: reachability first, so the chrome line is right even when
            // nothing else changed; then the queue, because a sync that landed has drained it; then
            // the colony, which is the authoritative one and replaces whatever this device thought.
            suspend fun arrive(outcome: SyncOutcome, clock: DebugClock, wall: Instant) {
                when (outcome) {
                    is SyncOutcome.Synced -> {
                        reachable = true
                        val at = clock.now(wall)
                        lastReachedAt = at
                        // **Written down, because the line it feeds outlives a launch.** A player who
                        // opened the app on a train and closed it is still offline the next morning,
                        // and *"no network since 11:31"* is only answerable by something that was
                        // remembered. Held in memory alone it would read *never* on every cold start
                        // and the line would be missing exactly when it is most needed.
                        //
                        // **Stored to the minute, which is what makes the write rare rather than
                        // per-tap.** The line prints `HH:MM`, so anything finer is a disk write that
                        // cannot change a pixel — and every tap syncs, so without the truncation this
                        // would rewrite a preferences file on every button in the game.
                        val minute = (at.toEpochMilliseconds() / MILLIS_PER_MINUTE * MILLIS_PER_MINUTE).toString()
                        if (remembered.lastReachedAt != minute) {
                            val next = remembered.copy(lastReachedAt = minute)
                            remembered = next
                            preferences.save(next)
                        }
                        held = HeldActions(outbox.queued())
                        // **`resume` and not a bare assignment**: the snapshot is stamped at the
                        // instant the *server* wrote it, and the phone has to advance it to now the
                        // same way it advances a save. Anything else opens a colony that has not
                        // caught up to its own clock.
                        val resumed = resume(outcome.colony, now = clock.now(wall))
                        session = resumed
                        resumed.commit(store, notifications, clock)
                    }

                    // **Nothing to say and nothing to undo** — see `SyncOutcome.NotNow`, which is one
                    // member for *nobody answered* and *another device won the race* because they are
                    // one instruction. The queue is intact and the colony on screen is the last one
                    // the server agreed to.
                    SyncOutcome.NotNow -> {
                        reachable = false
                        held = HeldActions(outbox.queued())
                        // **A first launch with no signal has no colony to open**, and the gate is
                        // the only screen that can say so — *"your colony runs there, so there is no
                        // offline start."* A device with a save never reaches this: it is already
                        // showing the colony the server last agreed to.
                        if (session == null) gate = GateState.NoAnswer
                    }

                    // **The server answered, and the answer is not about any one verb.** Only one of
                    // them reaches a screen: `Unauthenticated` means the credential is gone and the
                    // gate is the honest place to be. Everything else is a colony that is still the
                    // last one agreed to, so the app goes on showing it — an app that emptied itself
                    // because a version check failed would be worse than one that is a few minutes
                    // out of date.
                    is SyncOutcome.Failed -> {
                        reachable = true
                        lastReachedAt = clock.now(wall)
                        // **The colony comes off the screen with the session**, which is the half
                        // that was once missing here: the gate only draws when there is no session,
                        // so signing out and leaving the colony up produced the worst state this app
                        // can be in — every control looking operable, every tap dropped, nothing on
                        // screen saying why. `endSession` is what says all of it at once now.
                        //
                        // The save is untouched. This is the last colony the server agreed to and it
                        // stays on disk — signing in again re-founds idempotently and opens it.
                        if (outcome.error == ApiError.Unauthenticated) endSession()
                    }
                }
            }

            // **Who is playing, asked wherever a session begins** — which is three places and was one.
            //
            // A route of its own rather than a field on `SyncResponse`: `Profile.kt` argues why, and
            // the short form is that this wire refuses unknown keys, so a field added to a shipped
            // response is a response the build already on somebody's phone cannot decode.
            //
            // **Its failure is not free, which is what the first cut of this had wrong.** *"The one
            // request in this launch whose failure costs nothing"* was true only of the strip, which
            // draws `Threshold` and `Dead Reckoning` either way; it was false of the editor behind it,
            // because a write is built out of the whole profile. So a read that does not land leaves
            // `profile` null, the face amber, and the tick loop asking again.
            //
            // **`renewing` and not a bare `api.profile`**, which is the second thing the first cut
            // had wrong and the harder one to see: `SessionKeeper.current()` decides on this
            // device's clock arithmetic, so a phone running behind the server hands over a token it
            // believes is in date and is answered `SessionExpired`. Read straight, that arm below
            // discards the answer and the account stays unread until the tick loop's minute is up —
            // about a server that replied immediately. See `SessionKeeper.renewing`.
            suspend fun readProfile(access: SessionToken) {
                when (val answer = sessions.renewing(access) { api.profile(it) }) {
                    is ApiResult.Answered -> profile = answer.value
                    // Nothing said and nothing signed out. A refusal here is almost always a token the
                    // sync is about to be refused for too, and *that* is where the gate is answered —
                    // twice would be two answers to one fact. What this arm leaves behind is the null
                    // above, which is the honest state and the one nothing can be written from.
                    is ApiResult.Refused, ApiResult.Unreachable -> Unit
                }
            }

            // The release this build is, which is the head of the changelog: there is no generated
            // `BuildConfig` in this build and one string does not earn source generation.
            // `ReleaseCatalogueIntegrationTest` is what keeps the two in step — it fails the build if
            // the head of the catalogue is not `libs.versions.oltre`.
            val runningVersion = changelog.releases.first().version

            // Writes down that this build's changelog has been read. Called when the sheet is
            // dismissed rather than when it is raised: a panel killed by a crash or a task switch has
            // not been read, and showing it once more costs a swipe.
            fun markChangelogSeen() {
                if (remembered.lastSeenVersion == runningVersion.printed) return
                val next = remembered.copy(lastSeenVersion = runningVersion.printed)
                remembered = next
                scope.launch { preferences.save(next) }
            }

            // **Every way out goes through here**, and that is not tidiness: the gear is one of the
            // four exits the design names, and the first cut of this let it close the sheet without
            // marking the changelog read — so a player who dismissed with the gear was shown the same
            // release again on the next launch, for ever. Caught by `ChangelogAppBehaviourTest`
            // rather than by an install, which is the whole reason the exits are one function.
            fun closeSheet() {
                sheetFace = null
                markChangelogSeen()
                // **A name half typed is not a name**, so the field goes back to what the account
                // actually holds — which, with null meaning *nothing typed*, is what the mapper draws
                // anyway. Without this, closing the sheet and reopening it would show a draft the
                // player abandoned, over a save button offering to commit it.
                nameDraft = null
            }


            // **"It must open on game updated"** (Davide, 2026-08-23). The rule is
            // `shouldOpenChangelog` and every branch of it is a test; what is here is the two things
            // it needs that only the composition root has — the file that remembers, and whether
            // there is a colony at all.
            //
            // **Keyed on both files**, because neither answer is in either one alone: the version
            // last shown is in the preferences and whether there is a colony at all is in the save,
            // and on the release that adds this feature the second is the entire question — a player
            // upgrading from 0.18 and a fresh install both arrive with nothing remembered.
            //
            // `null` on either side means the file has not come back yet, so this does nothing until
            // both have.
            LaunchedEffect(preferencesLoaded, hadSave) {
                val existing = hadSave ?: return@LaunchedEffect
                if (!preferencesLoaded) return@LaunchedEffect
                val open = shouldOpenChangelog(
                    lastSeen = remembered.lastSeenVersion?.let { ReleaseVersion.parse(it) },
                    current = runningVersion,
                    hasColony = existing,
                )
                // A first launch records the version without ever showing the sheet, so the *next*
                // build is news and this one is not. Everything else waits for the sheet to be
                // dismissed before it writes — a panel killed by a task switch has not been read.
                if (open) sheetFace = SheetFace.CHANGELOG else markChangelogSeen()
            }

            LaunchedEffect(Unit) {
                // **First, and before anything is read.** Every alert the OS is holding is about
                // something this launch is about to show properly, so it has already done its job —
                // and on iPhone it is the only lever `One in total` has, because a delivered
                // notification cannot be updated while the app is shut (#120).
                //
                // Here rather than in `commit` deliberately: this fires when the player *opens* the
                // app, and a clear folded into the commit would fire on every tap and — the tick loop
                // outliving the foreground on Android — in the background, wiping an alarm seconds
                // after it posted. See `NotificationScheduler.clearDelivered`.
                //
                // **What it does not cover is a warm resume**, because this effect runs once per
                // composition and returning from the background does not start a new one. On the
                // delivery target that is the smaller half — iOS terminates backgrounded apps freely,
                // and a notification tapped after one is a cold launch — but it is a real gap, and
                // closing it needs a foreground signal the shell does not have today.
                // **First, and in the same effect as everything else** — it had one of its own until
                // 0.21 and could not keep it: the sync below now *writes* a preference, so a load
                // racing it would read the file back and put the older value on screen. Two effects
                // that both touch one record is one effect.
                val loaded = preferences.load()
                remembered = loaded
                galaxyLanding = loaded.galaxyLanding.toGalaxyLanding()
                provider = loaded.provider.toAuthProvider()
                // **Read back rather than started from nothing**, which is the whole point of writing
                // it down: a cold launch with no signal has to be able to say *since when*.
                lastReachedAt = loaded.lastReachedAt?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
                preferencesLoaded = true

                notifications.clearDelivered()
                val saved = store.load()
                hadSave = saved != null
                // Read before anything is drawn, so the first frame a player sees already knows what
                // it is waiting on. A card that appeared white and turned amber a frame later would
                // be the app changing its mind in front of them.
                held = HeldActions(outbox.queued())
                val wall = wallClock.now()
                // The offset comes out of the save's own instant, which is the whole reason the
                // debug clock needs no file of its own: a colony written down in the future was
                // skipped there, and resuming at ×1 would freeze it until the wall clock caught up.
                val clock = DebugClock.resuming(saved?.lastUpdatedAt, wallClock = wall)
                debugClock = clock

                // **The colony the server last agreed to opens the game while the server is asked
                // again.** That is the whole of what a save is for now: it is not the truth any more,
                // it is the last truth this device was told, and opening on it is why a player on a
                // train sees their colony rather than a spinner.
                if (saved != null) {
                    val resumed = resume(saved, now = clock.now(wall))
                    // Before the session is published, so the first frame the player sees is already
                    // the one that knows what to announce. Setting it afterwards would compose the
                    // rail once with nothing to roll from and only then hand it a starting figure —
                    // which is a roll that begins in the wrong place.
                    arrivalOf(saved = saved.state, resumed = resumed.state)?.let { arrival ->
                        lastSeen = arrival.lastSeen
                        finishedWhileAway = arrival.finished
                    }
                    session = resumed
                    // Commit immediately, save included: a player who opens the game once and
                    // closes it must still come back to hours of production. The same opening also
                    // books the alerts for whatever was already in flight — a colony restored from
                    // disk has a schedule that no longer exists on the device.
                    resumed.commit(store, notifications, clock)
                }

                // **Nobody has signed in on this device, so nothing else happens until they do.**
                // `current()` is not a read of a flag: it renews an access token that has run out and
                // answers `Gone` only when the refresh token has run out too, or was refused, or
                // there was never one. All three are the gate.
                //
                // **`Unreachable` is none of those and falls through deliberately.** A renewal
                // nobody answered is a train, not a sign-out: the session is on disk, so the launch
                // carries on and the sync below fails the ordinary offline way — the colony stays
                // up, the queue is held, and the chrome line says the network is out. Reading it as
                // *signed out* is what left a player one tunnel away from the gate.
                val credential = sessions.current()
                when (credential) {
                    Credential.Gone -> {
                        signedIn = false
                        // **And the colony comes back off the screen, which is the case every
                        // existing tester takes on their first launch of this release.** They have a
                        // save from a build that had no accounts and no session file, because until
                        // now there was nothing to have one of — so the resume above published a
                        // colony that nothing they tap could reach a server about. The gate draws
                        // only when there is no session, so leaving this out hands the one person
                        // testing the build a screen full of live-looking controls and no way to
                        // sign in.
                        //
                        // **The save is untouched**: the file stays where it is, so the slice that
                        // lands the one-time upload still has it to send. What is discarded is the
                        // *claim* that it is the colony the game is running.
                        session = null
                        return@LaunchedEffect
                    }

                    is Credential.Held, Credential.Unreachable -> signedIn = true
                }
                // **The gate stays up while the first colony is fetched, and says so.** Only on a
                // device with no save: one that has a colony is already showing it, and a waiting
                // line over a working game would be the app reporting on itself.
                //
                // **Before the account is read, and the order is the whole of it.** `signedIn` is
                // set two lines up and there is no suspension point between there and here, so no
                // frame can compose in between. Putting the profile's round trip in that gap — which
                // is what the first cut did — draws the *live* sign-in gate, buttons and all, to a
                // player who is already signed in, for as long as the server takes to answer.
                if (saved == null) gate = GateState.Waiting
                // **Beside the colony rather than inside it**, and the credential this launch already
                // resolved rather than a second `current()`: `Unreachable` falls through above, and
                // asking again here would spend a second failed renewal to learn what is in hand.
                if (credential is Credential.Held) readProfile(credential.access)
                // **`sync` when this device holds a colony and `found` when it does not**, and the
                // difference is not a shortcut: `found` sends no envelopes, so a launch that always
                // founded would leave a queue written before the app was closed sitting on disk until
                // the player next tapped something. `sync` is the call that drains it.
                //
                // **`NoColony` falls through to founding**, which is the case a save cannot rule out:
                // the colony this device holds may have been deleted on another one, and the honest
                // answer to *the server has never heard of you* is to ask it to found one. `found` is
                // idempotent, so the fallback costs a round trip and can never mint a second galaxy.
                val opened = if (saved == null) colony.found() else colony.sync()
                if (opened is SyncOutcome.Failed && opened.error == ApiError.NoColony) {
                    arrive(colony.found(), clock, wall)
                } else {
                    arrive(opened, clock, wall)
                }
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

            // **The gate, and it stays up until there is both a session and a colony.** Those are two
            // facts and the screen answers to both: a player who has signed in and has no colony yet
            // is still waiting for the server, and the design's `waiting` state is exactly that
            // sentence. A device with a save skips it entirely — the colony it already holds is the
            // last one the server agreed to, and opening on that is the whole reason a save survives.
            fun signInWith(chosen: AuthProvider) {
                // **Recomputed, never counted down.** A tap inside the window re-states the line with
                // the seconds that are actually left and goes no further; the request is not made,
                // which is what makes the number honest rather than decorative.
                throttledUntil?.let { until ->
                    val left = (until - wallClock.now()).inWholeSeconds.toInt()
                    if (left > 0) {
                        gate = GateState.Throttled(left)
                        return
                    }
                    throttledUntil = null
                }
                gate = GateState.Waiting
                scope.launch {
                    when (val attempt = signIn.signIn(chosen)) {
                        // One sentence for a refusal and for a cancellation, because the platforms
                        // frequently cannot tell them apart and an accusation is worse than a fact.
                        SignInAttempt.Refused -> gate = GateState.Refused(chosen)
                        SignInAttempt.Unreachable -> gate = GateState.NoAnswer
                        is SignInAttempt.Signed -> {
                            // **Two calls rather than one taking a provider, because the provider is
                            // the path** — the two tokens are verified against different issuers,
                            // audiences and key sets. See `OltreApi`.
                            val answer = when (chosen) {
                                AuthProvider.APPLE -> api.signInWithApple(attempt.idToken, attempt.nonce)
                                AuthProvider.GOOGLE -> api.signInWithGoogle(attempt.idToken, attempt.nonce)
                            }
                            when (answer) {
                                is ApiResult.Answered -> {
                                    // Written before anything reads it, so a process killed on the
                                    // next line still has the session rather than sending the player
                                    // back here.
                                    sessions.adopt(answer.value)
                                    provider = chosen
                                    val next = remembered.copy(provider = chosen.name)
                                    remembered = next
                                    preferences.save(next)
                                    signedIn = true
                                    // **A sign-in is a session beginning, so it reads the account** —
                                    // and this line is the whole of what the gate was missing. The
                                    // launch effect ran once, before this screen existed, so a player
                                    // who arrived here wore `Dead Reckoning` over an account called
                                    // something else and the first mark they tapped wrote a null name
                                    // over it. Before the colony, so the first frame that has a strip
                                    // already has the right name on it.
                                    readProfile(answer.value.accessToken)
                                    val wall = wallClock.now()
                                    arrive(colony.found(), debugClock, wall)
                                }

                                is ApiResult.Refused -> gate = when (val error = answer.error) {
                                    // The server sends a number and the screen prints it, in the
                                    // app's own duration format.
                                    is ApiError.TooManyRequests -> {
                                        throttledUntil = wallClock.now() + error.retryAfterSeconds.seconds
                                        GateState.Throttled(error.retryAfterSeconds)
                                    }
                                    // Everything else the server can say about a sign-in is a
                                    // sign-in that did not happen, and the player's next move is the
                                    // same either way: try again, or use the other one.
                                    else -> GateState.Refused(chosen)
                                }

                                ApiResult.Unreachable -> gate = GateState.NoAnswer
                            }
                        }
                    }
                }
            }

            val current = session
            if (current == null) {
                // Nothing at all until both files have answered. A gate that flashed at a player who
                // is signed in would make the threshold feel like a failure.
                if (signedIn != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // The starfield is the frame's, not a screen's — see `MainScaffold`. The gate
                        // draws none of its own, so there is one sky in the app rather than two that
                        // have to be kept in step.
                        Starfield(scrollOffset = { 0f }, tilt = { lean.value })
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            Gate(
                                uiState = gate.toGateUiState(providers),
                                compact = maxWidth < OltreLayout.compactWidth,
                                onSignIn = { signInWith(it) },
                            )
                        }
                    }
                }
            } else {
                LaunchedEffect(Unit) {
                    var ticks = 0
                    // **Whether the minute's errand is still out**, and it is the whole of what keeps
                    // the retry below from becoming a flood. One at a time and no queue: a minute
                    // that arrives while the last request is still unanswered is a minute with
                    // nothing to add, because the question it would ask is the one already in flight.
                    var asking = false
                    while (true) {
                        delay(1.seconds)
                        val previous = session ?: continue
                        val next = previous.ticked(debugClock, wallClock = wallClock.now())
                        session = next
                        if (next.hasNewEventsSince(previous)) next.commit(store, notifications, debugClock)

                        // **Something has to try again, or the amber is a lie.** A held card says
                        // *"it starts when the network is back"*, and until 0.21 nothing in the app
                        // would have noticed the network coming back: the queue drained on a launch
                        // and on a tap and at no other moment, so a player who regained signal
                        // mid-session sat with three amber cards until they touched one.
                        //
                        // **Only while the last attempt failed, and no longer only while something
                        // is queued.** The count was in this condition until the profile arrived,
                        // and it was the reason the identity face had no way out: a rename queues
                        // *nothing* — `profile-sheet.md` §3 — so a write that met a dead network
                        // left `reachable` false with `held.count` at zero and this line could never
                        // fire for it. Meanwhile the card it raised drew every control on both
                        // identity faces as a press-less `Box`, so there was no finger-reachable
                        // path back either. A state with no exit is the one thing this product
                        // treats as worse than a crash, and the count is what created it.
                        //
                        // **It is still not a poll.** `reachable` is true whenever the server last
                        // answered, so a colony with signal never reaches this line at all; what is
                        // widened is *which* kind of failure gets asked about again, not how often.
                        // The cadence is unchanged and is argued the same way it always was: one
                        // request a minute, because a minute is the resolution the chrome line
                        // prints at and a player cannot see a difference finer than the clock they
                        // are being shown. A device genuinely offline spends one empty sync a minute
                        // — which is exactly what it already spent with one thing in the queue.
                        ticks++
                        if (ticks % TICKS_PER_RETRY != 0) continue
                        if (asking || (reachable && profile != null)) continue
                        // **Launched beside the clock rather than in front of it**, which is the one
                        // thing this loop must never spend: the coroutine that asks is the coroutine
                        // that advances the session, so a request it *awaits* is a colony that stops
                        // accruing, stops finishing what is in flight and stops booking what
                        // finishes. `RetryPolicy.DEFAULT` is three attempts four seconds apart and
                        // each is bounded only by the twenty-second request timeout, so awaiting one
                        // costs four seconds against a server that refuses fast and a minute against
                        // a network that takes the call and never speaks. A player watching a
                        // countdown would see it stop.
                        //
                        // **Reachable before it is launched**, so the child is only ever built for a
                        // minute that has something to ask — and `asking` is what keeps it to one at
                        // a time. A child of this effect, so it dies with the destination exactly as
                        // the loop does.
                        asking = true
                        launch {
                            try {
                                if (!reachable) arrive(colony.sync(), debugClock, wallClock.now())
                                // **And the account, which is the other thing a launch can fail to
                                // get.** The read has no queue behind it, so nothing above notices it
                                // missing — and until this, a launch whose profile never arrived
                                // stayed that way for the life of the process: the strip drew the
                                // default over a named account and the editor stayed amber with
                                // nothing ever trying again. Same minute as the queue's retry and the
                                // same argument: it is the resolution the chrome line prints at.
                                if (profile == null) {
                                    when (val credential = sessions.current()) {
                                        is Credential.Held -> readProfile(credential.access)
                                        // No credential and no network are both answered elsewhere —
                                        // the sync above is what turns a dead one into a gate. Asking
                                        // twice would be two answers to one fact.
                                        Credential.Gone, Credential.Unreachable -> Unit
                                    }
                                }
                            } finally {
                                asking = false
                            }
                        }
                    }
                }

                // **Which control a refused verb belongs to.** A `when` with no `else`, so a
                // thirteenth verb cannot be added without somebody deciding what it does when it
                // cannot be kept — which is `offlineRule`'s own discipline one layer up.
                fun refuse(verb: ClientVerb) {
                    when (verb) {
                        is ClientVerb.StartRun -> refusedRun = verb.target
                        is ClientVerb.StartSurvey -> refusedProbe = true

                        // The other ten are queued rather than refused, so nothing here can reach
                        // them — `Outbox.queue` reads the same rule and writes them to the file. The
                        // arm says so out loud rather than trusting it.
                        is ClientVerb.StartUpgrade,
                        is ClientVerb.StartResearch,
                        is ClientVerb.StartAdaptation,
                        is ClientVerb.BuildShips,
                        is ClientVerb.ToggleAlert,
                        is ClientVerb.CycleHullAlert,
                        ClientVerb.ToggleFlightAlerts,
                        is ClientVerb.SetAlertMode,
                        is ClientVerb.ToggleAlertCategory,
                        is ClientVerb.SetAlertDelivery,
                        -> Unit
                    }
                }

                // **Every tap goes through here now, and it does two things rather than one.**
                //
                // *Locally, and first.* Bring the simulation up to the instant the player acted, ask
                // `core` to apply the action at that instant, and show it. That is the path this
                // function has always taken and none of it moves: acting on a stale state would spend
                // resources the colony has not accrued yet.
                //
                // *And onwards.* The same action as a `ClientVerb`, through the outbox and the wire.
                // The server runs the same deterministic `core` over the same claimed instant, so the
                // colony that comes back is the one already on screen — which is what makes showing
                // the tap immediately honest rather than optimistic.
                //
                // **The two galaxy-touching verbs are not shown until the server agrees**, and that
                // is the whole of what look-don't-act means on a screen: a run and a probe aim at a
                // world somebody else may now hold, so drawing one as sent would be the promise this
                // game refuses to make. `offlineRule` decides, in `:protocol`, and is never
                // re-derived here — the same discipline `Outbox.queue` keeps.
                // **Which outstanding request this verb's control is already carrying, if any.** A
                // `when` with no `else` for `refuse`'s reason: a thirteenth verb has to answer where
                // its control's held state lives, or the amber ghost it grows will have no way back.
                //
                // The two that cannot be queued answer null by construction — nothing writes them to
                // the file — and `HeldActions` drops them on the way in anyway.
                fun heldKey(verb: ClientVerb): IdempotencyKey? = when (verb) {
                    is ClientVerb.StartUpgrade -> held.upgrade(verb.building)
                    is ClientVerb.StartResearch -> held.research(verb.technology)
                    is ClientVerb.StartAdaptation -> held.adaptation(verb.technology)
                    // The first hull type in the manifest, which is the only one a card can be: every
                    // finger in the app buys one hull at a time.
                    is ClientVerb.BuildShips -> verb.ships.counts.keys.firstNotNullOfOrNull { held.build(it) }
                    is ClientVerb.ToggleAlert -> held.watch(verb.target)
                    is ClientVerb.CycleHullAlert -> held.hullAlert(verb.ship)
                    ClientVerb.ToggleFlightAlerts -> held.flightAlert
                    is ClientVerb.SetAlertMode -> held.alertMode?.key
                    is ClientVerb.ToggleAlertCategory -> held.alertCategory(verb.category)
                    is ClientVerb.SetAlertDelivery -> held.alertDelivery?.key
                    is ClientVerb.StartRun, is ClientVerb.StartSurvey -> null
                }

                fun dispatch(verb: ClientVerb) {
                    // **The previous refusal goes with the tap that follows it**, whichever control
                    // it was on: a sentence about a tap the player has moved past is furniture.
                    refusedRun = null
                    refusedProbe = false
                    deleteRefused = false
                    scope.launch {
                        val wall = wallClock.now()
                        when (val outcome = colony.act(verb)) {
                            is ActOutcome.Synced ->
                                arrive(SyncOutcome.Synced(outcome.colony, outcome.rejected), debugClock, wall)

                            // On disk before this returned. The card the tap came from is amber from
                            // the next frame, because `held` is what every mapper reads.
                            ActOutcome.Queued -> {
                                reachable = false
                                held = HeldActions(outbox.queued())
                            }

                            // **Refused, and the sentence is the whole of what the player gets** —
                            // which is the point rather than a consolation: the control answered, it
                            // named the fact that stops it, and it will answer again the same way.
                            // A control that silently did nothing is the failure this whole rule
                            // exists to prevent.
                            ActOutcome.NotQueueable -> {
                                reachable = false
                                refuse(verb)
                            }

                            is ActOutcome.Failed ->
                                arrive(SyncOutcome.Failed(outcome.error), debugClock, wall)
                        }
                    }
                }

                // **The amber ghost's tap.** Nothing has been sent, so taking it back costs nobody an
                // apology and needs no server to agree — it is a deletion from a file.
                //
                // `ALREADY_SENT` is not an error and is deliberately silent: the queue drained between
                // the tap that held the verb and this one, so the thing the player asked for is on its
                // way to happening, which is what they wanted. Re-reading the outbox is what makes the
                // card change shape either way, and the sync that follows is what fetches the colony
                // the flush produced.
                fun withdraw(key: IdempotencyKey?) {
                    if (key == null) return
                    scope.launch {
                        val result = outbox.withdraw(key)
                        held = HeldActions(outbox.queued())
                        if (result == WithdrawResult.ALREADY_SENT) {
                            arrive(colony.sync(), debugClock, wallClock.now())
                        }
                    }
                }

                // **A tap the phone can answer for itself.** Every `QUEUE_AND_VALIDATE` verb comes
                // through here: the colony moves now, the outbox takes the verb, and the server
                // reconciles later. The transition is the optimistic half and it is always run —
                // which is why this takes one and `ask` below does not.
                //
                // It answers `true` unconditionally, and that is not a stub: the outbox takes a
                // queueable verb whatever the network is doing, so a tap that came through here was
                // kept by definition. `ask` is where the answer can be `false`.
                fun send(verb: ClientVerb, transition: (GameState, Instant) -> GameState): Boolean {
                    // **A tap on a held control withdraws it**, which is the amber ghost's whole
                    // behaviour and is answered here rather than at six call sites: the ui-state that
                    // drew the ghost was derived from the same `held`, so the two cannot disagree
                    // about which control is outstanding. A second callback per row would be ten new
                    // lambdas threaded through five screens for one line of logic.
                    heldKey(verb)?.let {
                        withdraw(it)
                        // Withdrawn rather than sent, and the sheet has nothing to stay open for.
                        return true
                    }
                    // Whatever landed while the app was closed stops being news the moment the
                    // player changes the colony themselves.
                    finishedWhileAway = null
                    val next = current.acting(debugClock, wallClock = wallClock.now(), transition = transition)
                    session = next
                    if (next.hasNewEventsSince(current)) {
                        scope.launch { next.commit(store, notifications, debugClock) }
                    }
                    dispatch(verb)
                    return true
                }

                // **A tap only the server can answer**, which is what `LOOK_DONT_ACT` means: a run
                // and a probe both touch the galaxy, and a galaxy two players share cannot be guessed
                // at on a phone. So there is no optimistic transition — the colony moves when the
                // answer arrives — and this is a separate function rather than a null argument to
                // `send` because a caller that offered one would be writing a lambda that can never
                // run. It was, for two verbs and thirty lines, until the coverage report said so.
                //
                // **It answers whether the tap was kept**, and that answer has to be synchronous
                // because one screen acts on it: the dispatch sheet closes on a run that was kept and
                // stays up on one that was refused, and it decides in the frame the button was
                // pressed. `reachable` is what it can know at that instant. The late case it cannot
                // see is a server that is there and refuses the run on its own freshness window; that
                // arrives through `refuse` a moment later and lands on the map card's line, which is
                // the row form the design drew for exactly the case where there is no sheet to put a
                // block in.
                //
                // Nothing is withdrawn here: neither verb is ever written to the outbox, so neither
                // can be held, and `heldKey` answers null for both by construction.
                fun ask(verb: ClientVerb): Boolean {
                    finishedWhileAway = null
                    dispatch(verb)
                    return reachable
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
                    // **A tap on a held control withdraws it rather than queueing a second verb**,
                    // and that is the design's rule for the amber ghost applied to the amber square:
                    // the request is the thing on screen, so tapping it takes it back. Without this,
                    // two taps would queue two toggles that cancel out and leave a control that looks
                    // outstanding for ever.
                    //
                    // Asked through `heldKey` like every other control rather than through `held`
                    // directly — one function answers *which envelope is this control's* for all
                    // twelve verbs, so a control cannot match a different envelope from the one the
                    // mapper drew its ghost from.
                    heldKey(ClientVerb.ToggleAlert(target))?.let { return withdraw(it) }
                    val next = current.alerting(debugClock, wallClock = wallClock.now(), target = target)
                    session = next
                    scope.launch { next.commit(store, notifications, debugClock) }
                    // **`dispatch` and not `send`**, because the four controls that write no event
                    // have already applied their own transition above — `send` would re-apply an
                    // identity one to the session this composition captured and throw the tap away.
                    dispatch(ClientVerb.ToggleAlert(target))
                }

                // The Shipyard's square, and the same shape for the same reasons — a hull card is
                // asked about with `cycleHullAlert` rather than `toggleAlert` because a queue has two
                // questions where a row has one, and everything else about this verb is `alert`'s:
                // it writes no event, so it has to commit unconditionally or the alert it just booked
                // would never reach the platform.
                fun alertHull(ship: ShipType) {
                    heldKey(ClientVerb.CycleHullAlert(ship))?.let { return withdraw(it) }
                    val next = current.alertingHull(debugClock, wallClock = wallClock.now(), ship = ship)
                    session = next
                    scope.launch { next.commit(store, notifications, debugClock) }
                    // **`dispatch` and not `send`**, because the four controls that write no event
                    // have already applied their own transition above — `send` would re-apply an
                    // identity one to the session this composition captured and throw the tap away.
                    dispatch(ClientVerb.CycleHullAlert(ship))
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
                    heldKey(ClientVerb.ToggleFlightAlerts)?.let { return withdraw(it) }
                    val next = current.alertingFlights(debugClock, wallClock = wallClock.now())
                    session = next
                    scope.launch { next.commit(store, notifications, debugClock) }
                    // **`dispatch` and not `send`**, because the four controls that write no event
                    // have already applied their own transition above — `send` would re-apply an
                    // identity one to the session this composition captured and throw the tap away.
                    dispatch(ClientVerb.ToggleFlightAlerts)
                }

                // The settings sheet's three controls, and they share one verb because they are one
                // kind of thing: a standing answer being changed, pointing at no job and reading no
                // stock. Same shape as the three above and committing unconditionally for the same
                // reason — nothing here writes an event, and what has to survive is the schedule that
                // `notifications.sync` books inside `commit`.
                fun prefer(verb: ClientVerb, transition: (GameState) -> GameState) {
                    // **The amber ghost's tap, answered here** — for `send`'s reason and by the same
                    // function. The three call sites below each asked `held` a question of their own
                    // shape (`held.alertMode?.key`, `held.alertCategory(category)`), which was a
                    // second way of asking what `heldKey` already answers for every verb there is:
                    // three chances to match the wrong envelope, and three arms of an exhaustive
                    // `when` that nothing could reach.
                    heldKey(verb)?.let { return withdraw(it) }
                    val next = current.preferring(debugClock, wallClock = wallClock.now(), transition = transition)
                    session = next
                    scope.launch { next.commit(store, notifications, debugClock) }
                    dispatch(verb)
                }

                // **App Review guideline 5.1.1(v), and the one call in the app whose success is an
                // absence.** The server answers `204` and it answers `204` the second time too, so
                // there is nothing to decode and nothing to reconcile — what is left is to forget
                // everything this device held and go back to the gate.
                //
                // **The local files go too, and in that order.** The session first, because a device
                // that still had one would sync a colony that no longer exists and be answered
                // `Unauthenticated`; then the save and the queue, because the account they belonged to
                // is gone and a colony with no account is a colony nothing can ever agree to again.
                //
                // **Offline it refuses**, in the same grammar a dispatch does: the account is removed
                // on the server, and the server has to answer.
                //
                // **And it belongs to the session that pressed it**, which is `writeProfile`'s
                // mechanism applied where getting it wrong is worst. A deletion that lands and loses
                // its response leaves a token naming a player who is gone, and the next sync is
                // refused for exactly that — so *the session ending under an outstanding deletion is
                // this route's ordinary failure*, not a corner. Answered blind, the success arm signs
                // out whoever is current now and clears **their** save and queue off the disk.
                fun deleteAccount() {
                    val mine = accountWork
                    scope.launch {
                        val credential = sessions.current()
                        // A renewal is a round trip, so the session can have ended inside it — and
                        // the `Gone` arm below would then sign out whoever is current now.
                        if (accountWork !== mine) return@launch
                        val token = when (credential) {
                            is Credential.Held -> credential.access

                            // No credential to delete with. The gate is where that is answered, and
                            // it is where the next launch would have gone anyway. The sheet comes
                            // down with it, which `endSession` says for every path and which matters
                            // most here: what is raised is the deletion face, and leaving it set
                            // would put a *delete my account* screen over the first colony the
                            // player signed back into.
                            Credential.Gone -> {
                                endSession()
                                return@launch
                            }

                            // **Nothing was asked and nobody said no**, so the account is exactly
                            // where it was. This is the offline refusal the sheet already draws —
                            // signing the player out here would delete a session rather than an
                            // account, which is the opposite of what the tap asked for.
                            Credential.Unreachable -> {
                                deleteRefused = true
                                reachable = false
                                return@launch
                            }
                        }
                        // **`renewing`, and it costs nothing to apply here** — this gap is older than
                        // the profile pair and predates this slice: a phone whose clock runs behind
                        // the server's could be answered `SessionExpired` on the one call in the app
                        // that cannot be repeated by accident, and that arm below would have drawn
                        // *no network* over an account that was never deleted. Same rule, same one
                        // line. See `SessionKeeper.renewing`.
                        val answer = sessions.renewing(token) { api.deleteAccount(it) }
                        // **The answer, weighed against the session that asked for it** — see above.
                        // The account is deleted either way and nothing here can undo that; what this
                        // decides is whether anybody on this device is entitled to act on it.
                        if (accountWork !== mine) return@launch
                        when (answer) {
                            is ApiResult.Answered -> {
                                // **Who was playing goes with the account they were**, which is what
                                // `endSession` carries — and the fake server had been careful about
                                // the very same thing before the app was: `FakeOltreApi
                                // .deleteAccount` resets its own profile because *"the profile hangs
                                // off the account rather than the colony"*.
                                //
                                // **First, and the order is stated rather than incidental.** The
                                // session goes before the files, because a device that still had one
                                // would sync a colony that no longer exists and be answered
                                // `Unauthenticated`.
                                endSession()
                                store.clear()
                                outboxFile.clear()
                                // **Written down *and* kept**, which is what every other writer of
                                // this record does and this one did not: `remembered` is the whole
                                // preferences row and the next `copy(…)` off it is built from
                                // whatever is in memory. Saving without keeping left a provider in
                                // memory that the file no longer held, so the next save of any other
                                // field would have written the deleted account's provider back.
                                val forgotten = remembered.copy(provider = null)
                                remembered = forgotten
                                preferences.save(forgotten)
                                // The two this path has beyond an ordinary sign-out, because it
                                // deletes rather than forgets: a queue aimed at a colony that is
                                // gone, and the provider named on a sheet there is no longer an
                                // account behind.
                                held = HeldActions.NONE
                                provider = null
                            }

                            // **The server read the request and said no, and there are two of those.**
                            //
                            // `Unauthenticated` is a credential that has gone — an account already
                            // deleted on another device is the case it is really about — and the gate
                            // is the honest answer, so the colony comes off the screen with it. The
                            // save stays exactly where it is; nothing was deleted here, and signing
                            // in again opens the same colony.
                            //
                            // **Everything else is a server that failed, and the whole taxonomy used
                            // to land in the first arm.** The comment justifying that named a token
                            // that had just expired — which cannot reach here any more: the call
                            // above is wrapped in `renewing`, so a `SessionExpired` is renewed and
                            // retried, and only a credential with nothing left comes back as
                            // `Unauthenticated`. What is left is `Internal` and its neighbours: a
                            // database that blinked, with the account still there and the tap that
                            // asked about it three faces back. Signing the player out for that is
                            // wrong twice — it deletes a session instead of an account, and it hides
                            // the one fact they need, which is that nothing happened.
                            //
                            // So it is the face this sheet already has for a delete that did not
                            // land. The card says the account is removed on the server and to try
                            // again when the network is back; the second half is a shade off for a
                            // server that answered, and it is the same *"cheaper lie"* `writeProfile`
                            // states out loud — the design says plainly that what the server may
                            // still refuse is not designed, so a red frame here would be invented at
                            // the keyboard. `reachable` is deliberately not touched: the server did
                            // answer, and putting the offline line over the whole app for one refused
                            // route would be a second lie with a much wider blast radius.
                            is ApiResult.Refused -> if (answer.error == ApiError.Unauthenticated) {
                                endSession()
                            } else {
                                deleteRefused = true
                            }

                            ApiResult.Unreachable -> {
                                reachable = false
                                deleteRefused = true
                            }
                        }
                    }
                }

                // **A name and a mark, written where they live.** `deleteAccount`'s shape exactly —
                // resolve a credential, ask, answer the three arms — and for the same reason: both
                // are about the *account*, which is a thing the outbox has no way to speak about.
                //
                // **Not a `ClientVerb`, not queued, and it cannot be held.** A verb is a mutating
                // function in `core` that the server validates by replaying it against the colony at
                // a claimed instant; a rename mutates no `GameState` and has no instant to be replayed
                // against, so queueing one would mean inventing a second, weaker outbox for two
                // fields. `profile-sheet.md` §3 settles it, and what a player gets instead is the
                // amber card the face already draws.
                //
                // **What comes back is what is drawn**, rather than what was sent: the server writes
                // the row and reads it back, so a device that guessed would be drawing its own claim.
                //
                // **`renaming` is the draft this write was assembled from, and `null` is *not a
                // rename*.** It exists for one line at the bottom: the draft may only be ended by a
                // *name* landing, and only by the one that landed. This function is the shared path
                // for five controls and four of them are marks, so resetting it on every answer threw
                // away what a player had typed the moment they picked a picture — which contradicts
                // the frame's own split, *the mark commits on tap and the name has a button*, and the
                // note over `nameDraft` that already said so.
                //
                // **A string rather than a flag**, because *which* rename landed is the question a
                // flag could not answer: the field is live for the whole round trip, so a boolean
                // cleared whatever had been typed since. A rename whose draft was null has nothing to
                // clear either way, which is why the two meanings can share one `null`.
                //
                // **`edit` rather than a finished row, and one write at a time**, which is the whole
                // of the answer to two taps racing. The two controls that reach here are the one pair
                // in this app that can be pressed inside each other's round trip — a mark commits as
                // it is touched and a name has a button — and each posts the *whole* row. Built from
                // a snapshot taken at tap time and fired as an independent `launch`, the name write
                // carried the pre-tap mark and the server wrote it back over the one that had just
                // landed. The mutex serialises them and the lambda is applied *inside* it, so the
                // second payload is assembled from what the first left behind rather than from what
                // the composition happened to be holding when a finger went down.
                //
                // **Not a compare-and-set, and that is a decision rather than an omission.**
                // `colonies` has a version column because two devices can both advance one colony and
                // a lost verb is a lost tap; `players` has none because a profile is two fields a
                // player types on the phone in front of them, and *last writer wins on the whole row*
                // is exactly what "what they most recently chose" means. A version here would buy
                // defence against one player renaming themselves on two phones inside one round trip,
                // and would cost a refusal the design explicitly does not have — *"what the server may
                // still refuse is not designed"* (`profile-sheet.md` §6) — plus the re-read and the
                // retry behind it. The race that was real was on this device, in one process, and it
                // is the one being fixed.
                //
                // **Which session it belongs to is captured at the tap and asked again after every
                // suspension**, which is the other half of `AccountWork` and is not defensive
                // decoration. The scope this runs on outlives `endSession`, so between the tap and
                // the answer the process may have signed a *different* player in — and every arm
                // below writes to state that session owns. Read straight, the refusal arm signed the
                // new player out of an account they had just signed into, and the success arm drew
                // the previous account's row over theirs, from which the next tap would have posted
                // it whole. An answer that belongs to a finished session belongs to nobody.
                fun writeProfile(renaming: String?, edit: (PlayerProfile) -> PlayerProfile) {
                    val mine = accountWork
                    scope.launch {
                        mine.lock.withLock {
                            if (accountWork !== mine) return@withLock
                            // **The floor, and it is inside the lock for the reason the lock exists**:
                            // the row a payload is built from is the one the last write left behind.
                            //
                            // Unreachable rather than a branch that decides anything — while the
                            // profile is unread `profileRequirement` is non-null, so both faces are
                            // held and every cell and chip is drawn as a `Box` with no press on it.
                            // Written this way round because a face that silently swallowed the tap
                            // would be the dead control this product refuses, and one that crashed
                            // would be worse.
                            val read = profile ?: return@withLock
                            val next = edit(read)
                            val credential = sessions.current()
                            // A renewal is a round trip, so the session can have ended inside it —
                            // and the `Gone` arm below would then sign out whoever is current now.
                            if (accountWork !== mine) return@withLock
                            val token = when (credential) {
                                is Credential.Held -> credential.access

                                // No credential to write with, which is the gate — and where the next
                                // launch would have gone anyway. The sheet comes down with it, and
                                // the account this device was drawing goes too: the face is raised
                                // from state rather than from a stack, so anything left set here is
                                // waiting to reappear over whatever colony the player next signs
                                // into. `endSession` says all of it in one place.
                                Credential.Gone -> {
                                    endSession()
                                    return@withLock
                                }

                                // **Nothing was asked and nobody said no**, so the name is exactly
                                // where it was. Signing the player out here would answer a renewal
                                // nobody heard by deleting a session, which is `deleteAccount`'s own
                                // trap in a place where it would be even harder to notice.
                                // `reachable` is what puts the amber card over the two controls, so
                                // the face says so on the next frame rather than swallowing the tap.
                                Credential.Unreachable -> {
                                    reachable = false
                                    return@withLock
                                }
                            }
                            // **`reachable` moves and `lastReachedAt` does not**, which is deliberate
                            // and is the one place this function departs from `arrive`. The flag is
                            // what puts the amber card over these two controls, so it has to answer
                            // to this request; the *instant* is written to the preferences file as
                            // well as to memory, and a second writer that only moved the memory copy
                            // would disagree with the disk on the next launch. `arrive` owns it, and
                            // what it names — the last time this device and the server agreed about
                            // the colony — is still true after a rename.
                            //
                            // **`renewing` and not a bare `api.setProfile`**: `current()` above
                            // decides on this device's clock arithmetic, and the case it cannot cover
                            // is the server disagreeing. Answered straight, a `SessionExpired` fell
                            // into the `else` arm below and put the amber card up — *"no network
                            // since 09:41"* about a server that had just replied — and the retry
                            // re-sent the token that had been refused.
                            val answer = sessions.renewing(token) { api.setProfile(it, next) }
                            // **The answer, weighed against the session that asked for it.** The
                            // request has left either way and nothing can recall it; what this
                            // decides is whether anybody on this device is still entitled to act on
                            // what came back.
                            if (accountWork !== mine) return@withLock
                            when (answer) {
                                is ApiResult.Answered -> {
                                    reachable = true
                                    profile = answer.value
                                    // **The edit ends, and only a name can end it — and only the name
                                    // that was actually sent.** The field goes back to following what
                                    // is committed, which is also what makes the save button go away:
                                    // it is present only while the draft differs from what is
                                    // committed, and after a name write those are one string. A mark
                                    // landing leaves the draft exactly where the player left it.
                                    //
                                    // **And so does a keystroke made during the round trip.** The
                                    // field stays live for the whole of it, so clearing unconditionally
                                    // threw away everything typed between the tap and the answer —
                                    // silently, and in front of somebody who was still typing. What
                                    // ends is the edit that landed, which is the draft this write was
                                    // assembled from and no other.
                                    if (renaming != null && nameDraft == renaming) nameDraft = null
                                }

                                // **The server read it and said no.** One refusal has a screen:
                                // `Unauthenticated` is a credential that has gone, and the gate is
                                // the honest place to be. `renewing` answers in that word too, for a
                                // token the server called expired twice over.
                                //
                                // **Every other refusal has no frame, and that is not the same as
                                // having no consequence** — which is what this arm used to be. It set
                                // `reachable` back to true, which is the flag that takes the amber
                                // card away, so the face was redrawn with all eighteen controls live
                                // and the tap that had just been refused looked exactly like a tap
                                // that had landed.
                                //
                                // So the write is left in **the state this face already has for
                                // *that did not land***: amber, quiet, and explained. That is a
                                // deliberate choice between two imperfect answers rather than a new
                                // one invented at the keyboard — the design says in as many words
                                // that *"what the server may still refuse is not designed"*, so
                                // drawing a red refusal here would be inventing a frame, and the cost
                                // of this one is stated plainly: the chrome line will say *no network
                                // since* about a server that did in fact answer. It is the cheaper
                                // lie, and it is now a *temporary* one — the tick loop's minute asks
                                // again and takes the card away when the answer comes back, which is
                                // what makes this a wait rather than a dead end. `status.md` carries
                                // the ask for a frame.
                                is ApiResult.Refused -> if (answer.error == ApiError.Unauthenticated) {
                                    reachable = true
                                    endSession()
                                } else {
                                    reachable = false
                                }

                                ApiResult.Unreachable -> reachable = false
                            }
                        }
                    }
                }

                // **The only door to a mark write.** Five controls used to say `profile.copy(mark =
                // …)` for themselves, which is five chances to build a whole-row write out of a
                // profile nobody read — and `POST /v1/profile` replaces rather than merges, so each
                // of those was the account's name deleted everywhere. There is no `copy` to reach for
                // now: what leaves here is a *description* of the edit, applied inside `writeProfile`
                // against whatever the last answer left behind.
                //
                // A whole mark rather than a slot, because that is what the wire carries: the swap
                // that can assemble an illegal one — a terminus over a path that has gone — is the
                // mapper's to refuse rather than this file's to remember.
                fun wearMark(mark: (PlayerProfile) -> PlayerMark) {
                    writeProfile(renaming = null) { it.copy(mark = mark(it)) }
                }

                // The one control on either face that does not commit as it is touched — a mark is a
                // tap, and a name has no keystroke that means *done*.
                //
                // **Trimmed here and refused on the wire**, which is the division `CommanderName`
                // states: a value that arrives untrimmed came from something that did not agree about
                // the shape. An emptied field is `null` rather than a refusal — the only way out of a
                // name somebody regrets, and the placeholder says so while they are looking at it.
                //
                // **The draft is read at the tap and the rest of the row is not**, which is the split
                // the race made visible: what the player typed is a fact about *this* tap and can only
                // be captured here, and everything else in the row belongs to whatever landed last. A
                // null draft is a field showing what is already committed, so it resolves against the
                // fresh row inside the write and the button that would send it is not drawn anyway.
                //
                // **The untrimmed draft goes down as well as the trimmed name**, and the two are not
                // the same string: what is *sent* is trimmed, and what the field is *showing* is what
                // has to be recognised when the answer comes back. Comparing against the trimmed form
                // would count a trailing space typed during the round trip as nothing typed.
                fun saveName() {
                    val sent = nameDraft
                    val typed = sent?.trim()
                    writeProfile(renaming = sent) { read ->
                        val name = typed ?: read.name?.value.orEmpty()
                        read.copy(name = if (name.isEmpty()) null else CommanderName(name))
                    }
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
                    scope.launch { outcome.session.commit(store, notifications, outcome.clock) }
                }

                // Delete, then resume from nothing — which is exactly the path a first launch
                // takes, so a reset is a first launch rather than a second way of building one.
                fun reset() {
                    scope.launch {
                        store.clear()
                        val outcome = resetColony(wallClock = wallClock.now())
                        debugClock = outcome.clock
                        session = outcome.session
                        outcome.session.commit(store, notifications, outcome.clock)
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
                    val dispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Boolean =
                        { target, gathering, ships, window ->
                            ask(ClientVerb.StartRun(target, gathering, ships, window))
                        }
                    val compact = maxWidth < OltreLayout.compactWidth
                    val watching = current.state.watching?.watchingLabel(compact = compact)
                    // **The sentence is written where the width is known**, which is what the two
                    // refusal facts above are held as facts for: a run's clause is dropped in a Slide
                    // Over pane rather than ellipsised, and only this scope can say whether it is one.
                    val runRefusal = refusedRun?.let { target ->
                        RefusalUiState(
                            lead = Strings.refusedRunLead(),
                            body = Strings.refusedRunBody(
                                target = Strings.coordinate(target.galaxy, target.system, target.slot),
                                compact = compact,
                            ),
                        )
                    }
                    val probeRefusal = if (!refusedProbe) {
                        null
                    } else {
                        RefusalUiState(
                            lead = Strings.refusedProbeLead(),
                            body = Strings.refusedProbeBody(compact = compact),
                        )
                    }

                    MainScaffold(
                        // The one new piece of chrome, and null on a colony with signal — see
                        // `offlineLine`, which refuses to draw with half its facts.
                        offline = offlineLine(
                            reachable = reachable,
                            since = lastReachedAt,
                            held = held.count,
                            timeZone = TimeZone.currentSystemDefault(),
                            compact = compact,
                        ),
                        // **Read off the save and off the account, which is the strip's whole
                        // subject.** The level and the gauge are `current.state.experience`, so a
                        // colony carried forward from 0.16 opens on the level it had already earned;
                        // the name and the mark are the profile, which is not in the save at all and
                        // outlives the colony it is drawn above. See `core`'s `Experience.kt` and
                        // `PlayerProfile`.
                        player = current.state.toPlayerStripUiState(profile),
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
                                    held = held,
                                ),
                                onUpgrade = { building ->
                                    send(ClientVerb.StartUpgrade(building)) { state, at ->
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
                                    held = held,
                                ),
                                onStartResearch = { technology ->
                                    send(ClientVerb.StartResearch(technology)) { state, at ->
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
                                    send(ClientVerb.StartAdaptation(technology)) { state, at ->
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
                                    val next = remembered.copy(galaxyLanding = chosen.name)
                                    remembered = next
                                    scope.launch { preferences.save(next) }
                                },
                                onDispatchProbe = { target -> ask(ClientVerb.StartSurvey(target)) },
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
                                held = held,
                                // The map card's own refusal and the sheet's run refusal, which are
                                // two different sentences on two different controls of one tab.
                                refusal = probeRefusal ?: runRefusal,
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
                                    held = held,
                                ),
                                onBuild = { type ->
                                    send(ClientVerb.BuildShips(Ships.of(type, 1))) { state, at ->
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
                                held = held,
                                // No probe on this tab — a world a fleet has been sent to was
                                // surveyed in order to be dispatched to — so the only refusal the
                                // sheet it raises can carry is a run's.
                                refusal = runRefusal,
                            )
                        },
                        // **Tapping the gear again closes what it opened**, which is one of the four
                        // ways out the design names and the only one that is a control rather than a
                        // gesture. The strip is still on screen behind the scrim, so it is reachable
                        // exactly when it needs to be.
                        // **The gear always opens the settings face**, whatever the sheet was last
                        // wearing. A gear that reopened the changelog because that is where you left
                        // it would be a control whose meaning depends on history.
                        //
                        // **And with a second opener on the same bar, both keep exactly that shape:
                        // open my face when nothing is up, and close whatever is up otherwise.** The
                        // alternative — each control swapping the sheet to its own face — was
                        // rejected because of where these two controls *are*. They are on the strip,
                        // and the strip is behind the scrim: a `ModalBottomSheet` covers the window,
                        // so a tap that lands here while a sheet is up is a tap outside the sheet.
                        // The scrim already answers that gesture by dismissing, and these two arms
                        // are the same answer said by the two controls it happens to cover. A gear
                        // that swapped the identity face for the settings face would make a tap
                        // outside a sheet mean *dismiss* everywhere in the app except two 38dp
                        // squares.
                        //
                        // Both go through `closeSheet` rather than clearing the face, which is not
                        // tidiness: it is also what marks the changelog read, and a player who
                        // dismissed from here without it would be shown the same release again on
                        // every launch, for ever.
                        onOpenSettings = {
                            if (sheetFace == null) sheetFace = SheetFace.SETTINGS else closeSheet()
                        },
                        // **The left cluster, and the only way to the identity face.** It opens on
                        // the grid rather than on the field, which is the frame's order: header,
                        // mark, name, so the keyboard has somewhere to push the one control that
                        // needs it.
                        onOpenProfile = {
                            if (sheetFace == null) sheetFace = SheetFace.IDENTITY else closeSheet()
                        },
                    )

                    // **The app's second modal, beside its first — and since 0.19 it has two faces.**
                    // Every control on the settings face commits on tap and none of them writes an
                    // event, so all three go through `prefer` rather than `act` — see there.
                    sheetFace?.let { face ->
                        // **One card for both identity faces**, computed once here rather than twice
                        // below: they are one editor with its contents swapped, so a requirement that
                        // could differ between them would be the sheet saying two things about one
                        // fact. It is also what puts the grid, the ladders and the field at 42% and
                        // takes the save button away — there is nothing to queue, so an editor that
                        // cannot commit says what it is waiting for rather than accepting a tap it
                        // could not honour.
                        val requirement = profileRequirement(
                            profile = profile,
                            reachable = reachable,
                            since = lastReachedAt,
                            timeZone = TimeZone.currentSystemDefault(),
                        )
                        // One callback for every way out — the handle, the scrim, the system back —
                        // so the sheet cannot be dismissed by a route this does not hear about.
                        //
                        // **Dismissing is what marks the changelog read**, from either face:
                        // reaching the settings ladders means passing the build row, and a player
                        // who opened the gear has seen which version they are on.
                        OltreBottomSheet(onDismiss = { closeSheet() }) {
                            SettingsSheetFace(
                                face = face,
                                alerts = current.state.toAlertSheetUiState(
                                    now = current.lastUpdatedAt,
                                    timeZone = TimeZone.currentSystemDefault(),
                                    held = held,
                                    // Null draws no Account section, which is the honest answer on a
                                    // build that has no account to administer.
                                    account = provider?.let {
                                        accountSection(
                                            provider = it,
                                            // **The name the strip is wearing, not the default.**
                                            // The row reads "Dead Reckoning · since 14/8", and a
                                            // player who has just renamed themselves would otherwise
                                            // find the old name on the sheet the rename sits two
                                            // faces from. One account, one name.
                                            name = profile.spokenName(),
                                            state = current.state,
                                            timeZone = TimeZone.currentSystemDefault(),
                                        )
                                    },
                                ),
                                changelog = changelog.toChangelogUiState(),
                                build = changelog.toBuildRowUiState(),
                                // The same window every other compact decision in this file reads,
                                // and the one the design measured the stacked ladder and the 12dp
                                // peek against.
                                compact = maxWidth < OltreLayout.compactWidth,
                                onOpenChangelog = { sheetFace = SheetFace.CHANGELOG },
                                onSelectMode = { mode ->
                                    prefer(ClientVerb.SetAlertMode(mode)) { setAlertMode(it, mode) }
                                },
                                onToggleCategory = { category ->
                                    prefer(ClientVerb.ToggleAlertCategory(category)) {
                                        toggleAlertCategory(it, category)
                                    }
                                },
                                // **The four doors of the deletion flow**, and the third and fourth
                                // faces the one sheet can wear. `delete` is built only when there is
                                // an account, which is also the only way either face is reachable.
                                delete = provider?.let {
                                    current.state.toDeleteFaceUiState(
                                        face = if (sheetFace == SheetFace.DELETE_CONFIRM) {
                                            DeleteFace.CONFIRM
                                        } else {
                                            DeleteFace.WARN
                                        },
                                        provider = it,
                                        offline = deleteRefused,
                                        // **The name the strip is wearing**, for `accountSection`'s
                                        // reason and on the one flow where getting it wrong is worst:
                                        // both faces of an irreversible deletion were addressed to
                                        // `Dead Reckoning` whatever the player had called themselves.
                                        // One account, one name, one place it is decided.
                                        name = profile.spokenName(),
                                    )
                                },
                                onOpenDelete = { sheetFace = SheetFace.DELETE_WARN },
                                // **The warn face's button crosses to the last step; the last step's
                                // does the thing.** One callback for both, because the sheet knows
                                // which face it is wearing and a second one would be a second place
                                // to get the order wrong.
                                onDeleteAccount = {
                                    if (sheetFace == SheetFace.DELETE_WARN) {
                                        sheetFace = SheetFace.DELETE_CONFIRM
                                    } else {
                                        deleteAccount()
                                    }
                                },
                                // *Keep it* is first in the row and dismissal is a no, so this goes
                                // back to the reading face rather than out of the sheet.
                                onKeepAccount = {
                                    deleteRefused = false
                                    sheetFace = SheetFace.DELETE_WARN
                                },
                                onSelectDelivery = { delivery ->
                                    prefer(ClientVerb.SetAlertDelivery(delivery)) {
                                        setAlertDelivery(it, delivery)
                                    }
                                },
                                // **The two identity faces, built from the account rather than from
                                // the colony** — which is why they take no `GameState` and why the
                                // strip's own state is the only one in the app that reads both.
                                identity = profile.toIdentityFaceUiState(
                                    draft = nameDraft,
                                    requirement = requirement,
                                ),
                                // **Every tap commits, like every other control in this app.** There
                                // is no confirm on a picture — a mark is a tap, and the one thing on
                                // these two faces that is not is the name, which has no keystroke
                                // meaning *done*.
                                onChooseMark = { preset -> wearMark { PlayerMark.Preset(preset) } },
                                onComposeMark = { sheetFace = SheetFace.MARK_COMPOSE },
                                onNameChange = { typed -> nameDraft = typed },
                                onSaveName = { saveName() },
                                // **The same card the identity face wears**, because the composer's
                                // eleven chips commit exactly as its six cells do. It had none, so
                                // offline every part stayed lit and every tap did nothing at all.
                                markCompose = profile.toMarkComposeFaceUiState(requirement = requirement),
                                // Three slots and three whole marks — see `wearMark`.
                                onChooseBody = { body -> wearMark { it.withBody(body) } },
                                onChoosePath = { path -> wearMark { it.withPath(path) } },
                                onChooseTerminus = { terminus -> wearMark { it.withTerminus(terminus) } },
                            )
                        }
                    }

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

// **One signed-in account's outstanding work, as an object whose identity answers *whose is this?***
//
// It carries a lock, and the lock is only half of what it is for. The other half is that
// `endSession` replaces it: a request launched under the session that has just ended holds a
// reference to the old one, so comparing the two is how an answer that came back to a process which
// has since signed somebody else in is recognised — and dropped, rather than signing the new player
// out, drawing the previous account's row over theirs, or clearing their colony off the disk.
//
// A class rather than two fields beside each other — a `Mutex` and a counter — because the two would
// have to be replaced together and forgetting one is silent. Referential identity is the whole
// mechanism, so it has no `equals` and must not grow one.
private class AccountWork {

    // **One profile write at a time, and fair.** `Mutex` hands the lock out in the order it was asked
    // for, so two taps inside each other's round trip land in the order the fingers went down — which
    // is what makes a payload assembled inside the lock, from the row the last answer left behind,
    // correct rather than lucky.
    //
    // Only the profile pair takes it. A deletion is not ordered against a rename — it ends the
    // account both of them are about — so making it wait would buy nothing and would park the one
    // control in the app that cannot be repeated behind a request that may take the whole timeout.
    val lock: Mutex = Mutex()
}

// **The composition root is where the two vocabularies meet**, and that is not an accident of layout:
// `:client:save:data` may not see a `presentation` module, so the file stores the name of the landing
// and this is the one place that knows what the name means. An unreadable or unknown value is the
// map, which is also what a first launch gets — so a preferences file corrupted between builds costs
// a player one tap rather than a wrong screen.
private fun String?.toGalaxyLanding(): GalaxyLanding =
    GalaxyLanding.entries.firstOrNull { it.name == this } ?: GalaxyLanding.MAP

// **The Account section, and the two rows it holds.** Built here because it is the one part of the
// settings sheet that is not about the colony: who is signed in is the composition root's to know,
// and `:client:settings:presentation` may not learn it — a `GameState` has never carried an account
// and putting one in it would be the wire reaching into the simulation.
private fun accountSection(
    provider: AuthProvider,
    // **Handed in rather than read, and it is the strip's own.** This row and the strip are two
    // drawings of one account, so the name arrives from the one mapper that decides what an account
    // with no chosen name is called — a second `Strings.playerDefaultName()` here would be a second
    // place that decision is made, and the day a player renamed themselves only one of them moved.
    name: TextRes,
    state: GameState,
    timeZone: TimeZone,
): AccountUiState {
    // **The first thing this colony ever did**, which is the closest thing to a founding date the
    // save carries: `GameState.initial` writes no event, so a colony that has done nothing has no
    // date and the row says the name alone. Honest rather than invented — the alternative is stamping
    // *now*, which would move every time the sheet was opened.
    val since = state.eventLog.firstOrNull()?.at?.toLocalDateTime(timeZone)
    return AccountUiState(
        label = Strings.accountLabel(),
        provider = Strings.accountSignedInWith(provider.spoken()),
        name = if (since == null) name else Strings.accountSince(name, day = since.dayOfMonth, month = since.monthNumber),
        deleteLabel = Strings.deleteAccountRow(),
        deleteNote = Strings.deleteAccountRowNote(),
    )
}

private fun AuthProvider.spoken(): AuthProviderName = when (this) {
    AuthProvider.APPLE -> AuthProviderName.APPLE
    AuthProvider.GOOGLE -> AuthProviderName.GOOGLE
}

// **The composition root is where the two vocabularies meet**, exactly as it is for the galaxy's
// landing one function up: `:client:save:data` carries a name through and has no opinion about what
// it names, so resolving it is this file's. A name this build cannot resolve is nobody signed in —
// which is the same answer a first launch gets, and is what makes a downgrade harmless.
private fun String?.toAuthProvider(): AuthProvider? =
    AuthProvider.entries.firstOrNull { it.name == this }
