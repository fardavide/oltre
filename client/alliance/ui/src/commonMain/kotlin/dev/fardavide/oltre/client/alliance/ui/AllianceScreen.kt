package dev.fardavide.oltre.client.alliance.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings

// The alliance's tab, ahead of the feature it names. `alliance-sheet.md` §7 settled where the
// alliance lives — the tab bought by merging Shipyard and Fleets into Ships — and left the real
// screens (roster, search, treasury, refusal) for a later slice (`#143`). This is what fills the
// tab in the meantime: a real screen, in the alliance's own module, stating plainly that the
// feature is not built yet — the honest fourth state a control may ship in, per the global
// no-dead-control rule, rather than an absence or a debug-looking placeholder.
//
// No control on this screen. There is nothing to tap, so there is no dead-control surface to
// answer for — an explanation is not a control, and this one commits to nothing it cannot keep.
//
// Decides nothing, so it takes no state beyond the scroll every destination gets — `module-rules`'s
// own test for whether a feature needs a `presentation` module, and the same one `:client:debug:ui`
// already passes.
@Composable
fun AllianceScreen(scrollState: ScrollState = rememberScrollState(), modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .testTag(AllianceTestTags.SCREEN)
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = Strings.allianceComingSoonTitle().resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(AllianceTestTags.TITLE),
            )
            Text(
                text = Strings.allianceComingSoonBody().resolve(),
                color = OltreColors.textSecondary,
                fontFamily = oltreMono(),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(AllianceTestTags.BODY),
            )
        }
    }
}
