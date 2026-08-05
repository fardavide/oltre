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
import dev.fardavide.oltre.client.colony.presentation.ResourceRail
import dev.fardavide.oltre.client.colony.presentation.toColonyUiState
import dev.fardavide.oltre.client.design.OltreTheme
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.advance
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// The shell is the impure boundary: it reads the clock and ticks the UI. Game state itself
// only ever moves through core's advance(state, from, to).
@Composable
fun App(modifier: Modifier = Modifier) {
    OltreTheme {
        Surface(modifier.fillMaxSize()) {
            val start = remember { Clock.System.now() }
            val initial = remember { GameState.initial() }
            var now by remember { mutableStateOf<Instant>(start) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1.seconds)
                    now = Clock.System.now()
                }
            }
            val uiState = advance(initial, from = start, to = now).toColonyUiState(now = now)
            ResourceRail(
                uiState = uiState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }
}
