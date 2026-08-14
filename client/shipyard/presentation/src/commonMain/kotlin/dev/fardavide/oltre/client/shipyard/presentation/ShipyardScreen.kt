package dev.fardavide.oltre.client.shipyard.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.ShipType

// **Two of five tabs said "nothing here yet" and this is one of them.** `fleet-sheet.md` §8 names
// that as a real part of what "feels empty" means, and it is the reason the shipyard and the fleets
// tab ship together: a shipyard that builds hulls with nowhere to send them is worse than the empty
// tab it replaces, and a fleets tab with a fleet of exactly one is a list that never has two rows.
//
// **A price list, not a hero panel** — Design's sixth call. At one hull this whole screen is one
// card and one sentence, which is on purpose: the tab has to be honest about being small rather than
// dress a single row up as a facility.
//
// It scrolls for the reason Research does — not because it needs to today, but because the thing
// below the fold arrives one hull at a time and a screen that only scrolls once it has to is a
// screen whose first overflow is a defect.
@Composable
fun ShipyardScreen(
    uiState: ShipyardUiState,
    onBuild: (ShipType) -> Unit,
    // Hoisted since the Sky pass — the starfield behind this destination shifts with the list in
    // front of it, so the frame has to be able to read how far the list has got.
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = OltreLayout.maxContentWidth)
                .fillMaxWidth()
                // Ahead of the padding: a tag placed after it marks the padded interior, so the
                // bounds a test reads would be 32dp narrower than the column itself.
                .testTag(ShipyardTestTags.CONTENT)
                .padding(16.dp),
        ) {
            // The fleet as one number, in the slot Research spends on its slot rule. It is the only
            // reading on this screen that is about the fleet rather than about a hull, which is
            // exactly why it belongs on the heading rather than on a card.
            SectionLabel(text = "HULLS", rule = uiState.fleet)
            HullList(hulls = uiState.hulls, onBuild = onBuild)
            // The sentence that has to exist at one hull, and the one thing on this screen arguing
            // against the purchase it is offering. What it is bought for is that it pays in the
            // resource you choose, which no mine does.
            //
            // **Rewritten at 0.10.1 because its first clause became false.** It opened with *"the next
            // hull costs half again as much as the last"*, which was the compounding curve stated to
            // the player; the price is flat now, so what the screen has to name instead is the thing
            // that does bound a fleet — the slipway, one hull at a time.
            //
            // PLACEHOLDER copy, like every string in the app: content is Davide's.
            Text(
                text = "Every hull costs the same, and the yard builds one at a time. A Metal Mine " +
                    "level returns more per unit spent — the fleet is bought because it pays in the " +
                    "resource you choose, not because it pays better.",
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 10.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 11.dp),
            )
            if (uiState.comingHulls.isNotEmpty()) {
                // 22dp — the value that clears the fleet strip on Colony and separates the two
                // branches on Research, which is what the system already spends to mean "different
                // subject". Not a divider: these are one list of hulls with a seam in it.
                Spacer(modifier = Modifier.height(22.dp))
                SectionLabel(text = "NOT YET BUILT")
                ComingHullList(hulls = uiState.comingHulls)
            }
        }
    }
}
