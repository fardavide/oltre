package dev.fardavide.oltre.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.OltreColors
import dev.fardavide.oltre.client.design.OltreLayout
import dev.fardavide.oltre.client.design.oltreMono

// Chrome, like the tab bar below it, and here for the same reason: what it shows is the empire's,
// not one screen's, and it frames every destination. It moved out of :client:colony:presentation
// when Research landed as a second screen that shows it — a feature module cannot own a component
// another feature needs, and :client:design is tokens rather than components.
@Composable
internal fun ResourceRail(uiState: ResourceRailUiState, modifier: Modifier = Modifier) {
    // The bar itself is full-bleed — it reads as the top edge of the window — but its cells
    // stay on the same centred column as the content below, whatever the window's width.
    Box(
        modifier = modifier.fillMaxWidth().background(OltreColors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = OltreLayout.maxContentWidth)
                .fillMaxWidth()
                .testTag(ShellTestTags.RESOURCE_RAIL_CONTENT),
        ) {
            // The rates are already the throttled figures. What misled the player was not the
            // number but the absence of any mark saying it was being held down — a true rate
            // presented as an untroubled one. Recolouring costs no width, which matters in the
            // one component with none to spare.
            val throttled = uiState.throttled
            ResourceCell(
                name = "METAL",
                value = uiState.metal,
                rate = uiState.metalRatePerHour,
                throttled = throttled,
            )
            ResourceCell(
                name = "CRYSTAL",
                value = uiState.crystal,
                rate = uiState.crystalRatePerHour,
                throttled = throttled,
            )
            ResourceCell(
                name = "DEUTERIUM",
                value = uiState.deuterium,
                rate = uiState.deuteriumRatePerHour,
                throttled = throttled,
            )
        }
    }
}

@Composable
private fun RowScope.ResourceCell(name: String, value: String, rate: String, throttled: Boolean) {
    val mono = oltreMono()
    Column(modifier = Modifier.weight(1f).padding(horizontal = 11.dp, vertical = 9.dp)) {
        Text(
            text = name,
            color = OltreColors.textTertiary,
            fontFamily = mono,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(
            text = value,
            color = OltreColors.text,
            fontFamily = mono,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (throttled) {
                PowerMark(color = OltreColors.warn, width = 7.dp, height = 10.dp)
            }
            Text(
                text = rate,
                color = if (throttled) OltreColors.warn else OltreColors.ok,
                fontFamily = mono,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = if (throttled) 2.dp else 0.dp),
            )
        }
    }
}
