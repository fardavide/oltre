package dev.fardavide.oltre.client.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.component.oltreActionShape
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.icon.AppleMark
import dev.fardavide.oltre.client.design.icon.GoogleMark
import dev.fardavide.oltre.client.design.icon.OltreMark
import dev.fardavide.oltre.protocol.AuthProvider

// **The threshold, and the only screen in Oltre that is not about a colony.** No strip, no rail, no
// tab bar — there is nothing behind it yet, so there is no chrome to carry. Nothing scrolls and
// nothing is optional: one decision, and the screen states its own cost.
//
// **The background is deliberately transparent.** The starfield behind every destination is the
// shell's chrome, and a gate that drew its own would be a second sky that had to be kept in step with
// the first.
//
// The two provider buttons are the only objects in the product that are not Oltre's. Everything above
// them — the mark, the name, the why, and every failure line — is, which is what puts the seam between
// the game and the platform rather than through the middle of the game.
@Composable
fun Gate(
    uiState: GateUiState,
    compact: Boolean,
    onSignIn: (AuthProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().testTag(GateTestTags.SCREEN).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1.15 against 1, so the mark sits above the optical centre rather than on it. A block of
        // text centred on a tall screen reads as having slipped downward; the design's own weights.
        Spacer(modifier = Modifier.weight(1.15f).heightIn(min = 22.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 88 at the ordinary width and 76 in a Slide Over pane, and **it is the only thing on the
            // screen that changes**: both button strings are mandated, so neither can shorten.
            OltreMark(
                limb = OltreColors.textSecondary,
                trajectory = OltreColors.accent,
                size = if (compact) 76.dp else 88.dp,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    // **A `Raw` and not a catalogue entry**, which is what `TextRes.Raw` is for: the
                    // product's name is untranslatable *by construction* rather than by coincidence,
                    // and an id here would be an invitation to a translator who must decline it.
                    text = APP_NAME.resolve(),
                    color = OltreColors.text,
                    fontFamily = oltreMono(),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Why(text = uiState.why, color = OltreColors.textSecondary, size = 12.sp)
                Why(text = uiState.whyFoot, color = OltreColors.textTertiary, size = 11.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f).heightIn(min = 22.dp))

        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // **Above the buttons rather than over them.** The buttons never move, the message grows
            // upward, and the two providers are themselves the retry — so there is no third button.
            uiState.message?.let { message ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = message.lead.resolve(),
                        color = when (message.tone) {
                            GateTone.WAITING -> OltreColors.text
                            GateTone.FAILED -> OltreColors.danger
                        },
                        fontFamily = oltreMono(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 17.sp,
                        modifier = Modifier.testTag(GateTestTags.MESSAGE_LEAD),
                    )
                    Text(
                        text = message.body.resolve(),
                        color = OltreColors.textSecondary,
                        fontFamily = oltreMono(),
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.testTag(GateTestTags.MESSAGE_BODY),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.providers.forEach { ProviderButton(provider = it, onSignIn = onSignIn) }
            }

            Text(
                text = uiState.foot.resolve(),
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// **The one measurement Apple binds and the one this file therefore computes rather than chooses.**
// The HIG prefers the system font and permits a custom one; what it does *not* leave open is the
// proportion — the title is 43% of the button's height. Davide's call, recorded in `decisions.md`, is
// JetBrains Mono for both buttons: one font in the whole product, and a knowing deviation from
// Google's written Roboto Medium rule on an OAuth app that is deliberately in *Testing*.
//
// The height is 44dp because that is the touch target this app already spends everywhere else, so the
// title lands at 18.92sp — which is large next to the body text and is the point: this is the only
// decision on the screen.
@Composable
@NonRestartableComposable
private fun ProviderButton(provider: GateProviderUiState, onSignIn: (AuthProvider) -> Unit) {
    val apple = provider.provider == AuthProvider.APPLE
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .height(BUTTON_HEIGHT)
            .testTag(GateTestTags.provider(provider.provider))
            .pressable(shape = oltreActionShape) { onSignIn(provider.provider) }
            // Apple's fill and Google's are both mandated and neither is the app's. Apple is white
            // with no border; Google is its own near-black with its own grey hairline.
            .background(if (apple) Color.White else GOOGLE_FILL, oltreActionShape)
            .then(
                if (apple) Modifier else Modifier.border(1.dp, GOOGLE_EDGE, oltreActionShape),
            ),
    ) {
        if (apple) AppleMark(color = Color.Black, size = 20.dp) else GoogleMark(size = 19.dp)
        Text(
            text = provider.label.resolve(),
            color = if (apple) Color.Black else GOOGLE_INK,
            fontFamily = oltreMono(),
            fontSize = TITLE_SIZE,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
@NonRestartableComposable
private fun Why(text: TextRes, color: Color, size: TextUnit) {
    Box(modifier = Modifier.widthIn(max = 300.dp)) {
        Text(
            text = text.resolve(),
            color = color,
            fontFamily = oltreMono(),
            fontSize = size,
            lineHeight = size * 1.6f,
            textAlign = TextAlign.Center,
        )
    }
}

private val APP_NAME = TextRes("Oltre")

private val BUTTON_HEIGHT = 44.dp

// Apple's proportion, computed rather than written, so the two cannot drift apart if the height moves.
private val TITLE_SIZE = (BUTTON_HEIGHT.value * 0.43f).sp

private val GOOGLE_FILL = Color(0xFF131314)
private val GOOGLE_EDGE = Color(0xFF8E918F)
private val GOOGLE_INK = Color(0xFFE3E3E3)
