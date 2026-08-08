package dev.fardavide.oltre.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.icon.PowerMark

// Chrome, like the tab bar below it, and here for the same reason: what it shows is the empire's,
// not one screen's, and it frames every destination. It moved out of :client:colony:presentation
// when Research landed as a second screen that shows it — a feature module cannot own a component
// another feature needs.
//
// It stays the shell's after 0.0.14 split the design system into layer modules. What went into
// :client:design is what has no owner at all; the rail has one, and it is this module. Only the
// bolt it draws was shared out, to :client:design:icon, because the *glyph* is owned by neither
// this rail nor the colony's facility cards.
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
                orb = OltreColors.metal,
                throttled = throttled,
            )
            ResourceCell(
                name = "CRYSTAL",
                value = uiState.crystal,
                rate = uiState.crystalRatePerHour,
                orb = OltreColors.crystal,
                throttled = throttled,
            )
            ResourceCell(
                name = "DEUTERIUM",
                value = uiState.deuterium,
                rate = uiState.deuteriumRatePerHour,
                orb = OltreColors.deuterium,
                throttled = throttled,
            )
        }
    }
}

// Two lines rather than three: the hue that used to be carried only by the stock's own column
// arrives as an orb beside the caption, and the rate comes up onto the stock's baseline. The cell
// loses 12dp, and so does every screen under the rail.
@Composable
private fun RowScope.ResourceCell(
    name: String,
    value: String,
    rate: String,
    orb: Color,
    throttled: Boolean,
) {
    val mono = oltreMono()
    Column(
        modifier = Modifier
            .weight(1f)
            // Ahead of the padding, like every other tagged column in the app: a tag after it
            // marks the padded interior, and what the rate has to fit inside is the cell.
            .testTag(ShellTestTags.resourceCell(name))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).background(orb, CircleShape))
            Text(
                text = name,
                color = OltreColors.textTertiary,
                fontFamily = mono,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        // FlowRow rather than Row, and the 320dp six-figure case in MainScaffoldLayoutBehaviourTest
        // is why: a Row gives the stock the whole line and measures the rate into what is left,
        // which at 320dp is 10dp of a 59dp string. The rate has to be able to fall under the stock
        // and take the cell back to the height it had before this change.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = value,
                color = OltreColors.text,
                fontFamily = mono,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alignByBaseline(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alignByBaseline().testTag(ShellTestTags.resourceRate(name)),
            ) {
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
}
