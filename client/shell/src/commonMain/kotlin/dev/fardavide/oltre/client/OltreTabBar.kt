package dev.fardavide.oltre.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono

// The mockup's five-destination bottom bar. Full-bleed like the resource rail — it reads as the
// bottom edge of the window — with its tabs on the same centred column as the content above, so
// an iPad does not push them out to the screen edges.
@Composable
internal fun OltreTabBar(
    selected: OltreTab,
    onSelect: (OltreTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(OltreColors.background)) {
        // The mockup separates the bar from the content with a gradient scrim, which is a
        // statement that the list scrolls under it. This list stops at the bar, so a hairline
        // says the same thing without pretending to an overlap that is not there.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.09f)),
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .widthIn(max = OltreLayout.maxContentWidth)
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 9.dp),
            ) {
                OltreTab.entries.forEach { tab ->
                    TabItem(
                        tab = tab,
                        selected = tab == selected,
                        onSelect = { onSelect(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: OltreTab, selected: Boolean, onSelect: () -> Unit) {
    val tint = if (selected) OltreColors.accent else OltreColors.textTertiary
    Column(
        // An equal share rather than the mockup's fixed 66px: five fixed tabs overflow a 320dp
        // Slide Over pane, which the app has had to survive since it became a real iPad app.
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
            .testTag(ShellTestTags.tab(tab))
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabIcon(tab = tab, tint = tint)
        TabLabel(text = tab.label, tint = tint)
    }
}

@Composable
private fun TabLabel(text: TextRes, tint: Color) {
    Text(
        text = text.resolve(),
        color = tint,
        fontFamily = oltreMono(),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}
