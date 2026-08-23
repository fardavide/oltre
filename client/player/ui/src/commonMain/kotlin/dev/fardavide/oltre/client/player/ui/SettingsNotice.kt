package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.fardavide.oltre.client.design.component.oltreCardShape
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings

// **What the gear answers, and the first auto-dismissing surface in the app.** Two words, no action,
// no dismiss, no icon, no scrim: it arrives because something was touched, it says the one thing
// there is to say, and it is gone.
//
// **Why it is not printed on the strip**, which is where 0.16 and 0.17 put it: two words sitting
// next to the player's name read as *this bar* is coming soon, rather than the gear at the end of
// it. A caused notice cannot be misread that way — nothing says it until the thing it is about is
// touched — and that is the whole of the argument for spending a new surface on it.
//
// It is deliberately not a component and deliberately not a `Snackbar`: there is one notice in this
// app and it says one string, so a host with a queue, an action slot and a duration enum would be
// infrastructure bought for a single sentence. What it borrows instead is the card — the same opaque
// fill and the same 14dp radius every row in the app wears — with the stronger of the two lines,
// because it sits over content rather than in a list of its own kind.
//
// **Placed by the frame, not by this.** It belongs above the tab bar, and only the composition root
// knows where the tab bar is; what arrives here is a modifier that has already decided that. The
// same root owns the four seconds, for the reason the resource rail's arrival window lives in `App`
// rather than in the rail — see `MainScaffold`.
@Composable
@NonRestartableComposable
fun SettingsNotice(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = NOTICE_SCREEN_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            // Centre-left, as every card in this app sets its own text.
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .testTag(PlayerTestTags.NOTICE)
                .widthIn(max = OltreLayout.maxContentWidth)
                .fillMaxWidth()
                .heightIn(min = NOTICE_HEIGHT)
                .border(NOTICE_BORDER_WIDTH, NOTICE_BORDER, oltreCardShape)
                .background(oltreCardSurface, oltreCardShape)
                .padding(horizontal = NOTICE_PADDING),
        ) {
            Text(
                text = Strings.settingsComingSoon().resolve(),
                color = OltreColors.text,
                fontFamily = oltreMono(),
                fontSize = NOTICE_SIZE,
                maxLines = 1,
            )
        }
    }
}
