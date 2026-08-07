package dev.fardavide.oltre.client

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
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.notifications.data.defaultNotificationScheduler
import dev.fardavide.oltre.client.research.presentation.ResearchScreen
import dev.fardavide.oltre.client.research.presentation.toResearchUiState
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.defaultSaveFile
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startResearch
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
@Composable
fun App(
    store: GameStore = remember { GameStore(defaultSaveFile()) },
    notifications: GameNotifications = remember { GameNotifications(defaultNotificationScheduler()) },
    modifier: Modifier = Modifier,
) {
    OltreTheme {
        Surface(modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            // Null until the save has been read. Rendering a fresh colony first and swapping it
            // for the real one a frame later would flash wrong numbers at the player.
            var session by remember { mutableStateOf<GameSession?>(null) }

            LaunchedEffect(Unit) {
                val resumed = resume(store.load(), now = Clock.System.now())
                session = resumed
                // Commit immediately, save included: a player who opens the game once and
                // closes it must still come back to hours of production, and on a first launch
                // there is no saved instant to accrue from until one is written. The same
                // opening also books the alerts for whatever was already in flight — a colony
                // restored from disk has a schedule that no longer exists on the device.
                resumed.commit(store, notifications)
            }

            val current = session
            if (current != null) {
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1.seconds)
                        val previous = session ?: continue
                        // The wall clock can step backwards (NTP, the user changing device
                        // time); core's advance requires to >= from, so the boundary clamps.
                        val now = maxOf(Clock.System.now(), previous.lastUpdatedAt)
                        val next = GameSession(
                            state = advance(previous.state, from = previous.lastUpdatedAt, to = now),
                            lastUpdatedAt = now,
                        )
                        session = next
                        if (next.hasNewEventsSince(previous)) next.commit(store, notifications)
                    }
                }

                // Both actions follow the same path, and it is the only safe one: bring the
                // simulation up to the instant the player acted, ask core to apply the action at
                // that instant, then commit if the event log grew. Acting on a stale state would
                // spend resources the colony has not accrued yet.
                fun act(transition: (GameState, Instant) -> GameState) {
                    val at = maxOf(Clock.System.now(), current.lastUpdatedAt)
                    val advanced = advance(current.state, from = current.lastUpdatedAt, to = at)
                    val next = GameSession(state = transition(advanced, at), lastUpdatedAt = at)
                    session = next
                    if (next.hasNewEventsSince(current)) scope.launch { next.commit(store, notifications) }
                }

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
                        )
                    },
                )
            }
        }
    }
}
