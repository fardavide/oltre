package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import dev.fardavide.oltre.client.design.OltreTheme
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.defaultSaveFile
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startUpgrade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

// The shell is the impure boundary: it reads the clock, reads and writes the save file, ticks
// the UI, and holds the current session. Game state itself only ever moves through core's
// advance/startUpgrade.
@Composable
fun App(
    store: GameStore = remember { GameStore(defaultSaveFile()) },
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
                // Save immediately, save included: a player who opens the game once and closes
                // it must still come back to hours of production, and on a first launch there
                // is no saved instant to accrue from until one is written.
                store.save(resumed.toSnapshot())
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
                        if (next.hasNewEventsSince(previous)) store.save(next.toSnapshot())
                    }
                }

                ColonyScreen(
                    uiState = current.state.toColonyUiState(
                        now = current.lastUpdatedAt,
                        timeZone = TimeZone.currentSystemDefault(),
                    ),
                    onUpgrade = { building ->
                        val at = maxOf(Clock.System.now(), current.lastUpdatedAt)
                        val advanced = advance(current.state, from = current.lastUpdatedAt, to = at)
                        val next = GameSession(
                            state = when (val result = startUpgrade(advanced, building, at = at)) {
                                is StartUpgradeResult.Started -> result.state
                                StartUpgradeResult.QueueBusy,
                                StartUpgradeResult.InsufficientResources,
                                StartUpgradeResult.RequirementsNotMet,
                                -> advanced
                            },
                            lastUpdatedAt = at,
                        )
                        session = next
                        if (next.hasNewEventsSince(current)) scope.launch { store.save(next.toSnapshot()) }
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }
}
