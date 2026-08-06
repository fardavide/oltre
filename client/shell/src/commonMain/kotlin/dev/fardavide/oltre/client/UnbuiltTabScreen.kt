package dev.fardavide.oltre.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.OltreColors
import dev.fardavide.oltre.client.design.oltreMono

// What a tab shows before its slice lands. It is deliberately a real screen rather than a blank
// one: a player who taps Galaxy has to learn that the tab exists and is not built yet, and an
// empty black rectangle reads as a bug in the game rather than as a gap in it.
@Composable
internal fun UnbuiltTabScreen(tab: OltreTab, pendingWork: String, modifier: Modifier = Modifier) {
    val mono = oltreMono()
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TabIcon(tab = tab, tint = OltreColors.textTertiary, size = 44.dp)
        Text(
            text = tab.label.uppercase(),
            color = OltreColors.textSecondary,
            fontFamily = mono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = pendingWork,
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}
