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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fardavide.oltre.client.colony.presentation.ColonyScreen
import dev.fardavide.oltre.client.colony.presentation.toColonyUiState
import dev.fardavide.oltre.client.design.OltreTheme
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startUpgrade
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The shell is the impure boundary: it reads the clock, ticks the UI, and holds the current
// state. Game state itself only ever moves through core's advance/startUpgrade.
@Composable
fun App(modifier: Modifier = Modifier) {
    OltreTheme {
        Surface(modifier.fillMaxSize()) {
            var lastUpdated by remember { mutableStateOf<Instant>(Clock.System.now()) }
            var state by remember { mutableStateOf(GameState.initial()) }
            var now by remember { mutableStateOf<Instant>(lastUpdated) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1.seconds)
                    // The wall clock can step backwards (NTP, the user changing device time);
                    // core's advance requires to >= from, so the boundary clamps.
                    now = maxOf(Clock.System.now(), lastUpdated)
                    state = advance(state, from = lastUpdated, to = now)
                    lastUpdated = now
                }
            }
            ColonyScreen(
                uiState = state.toColonyUiState(now = now),
                onUpgrade = { building ->
                    val at = maxOf(Clock.System.now(), lastUpdated)
                    val current = advance(state, from = lastUpdated, to = at)
                    state = when (val result = startUpgrade(current, building, at = at)) {
                        is StartUpgradeResult.Started -> result.state
                        StartUpgradeResult.QueueBusy,
                        StartUpgradeResult.InsufficientResources,
                        StartUpgradeResult.RequirementsNotMet,
                        -> current
                    }
                    lastUpdated = at
                    now = at
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }
}
