package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fardavide.oltre.client.design.OltreTheme

@Composable
fun App(modifier: Modifier = Modifier) {
    OltreTheme {
        Surface(modifier) {
            Box {}
        }
    }
}
