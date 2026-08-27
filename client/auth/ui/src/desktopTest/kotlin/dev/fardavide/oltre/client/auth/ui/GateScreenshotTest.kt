package dev.fardavide.oltre.client.auth.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.client.design.text.AuthProviderName
import dev.fardavide.oltre.protocol.AuthProvider
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The first screen in the game that is not about a colony**, in the five states it has.
//
// The frames are the design's own — 393×759 and 320×759, the window between the status bar and the
// home indicator — and every one of them is a whole screen rather than a fragment, because the gate
// *is* the whole screen: no strip, no rail, no tab bar.
//
// **The starfield is deliberately absent.** It is the shell's chrome and the gate draws none of its
// own, so a baseline that included it would be photographing two things and would move whenever
// either did. `MainScaffoldScreenshotTest` is where the sky is held still.
@OptIn(ExperimentalTestApi::class)
class GateScreenshotTest {

    @Test
    fun `the whole screen at rest`() {
        capture(name = "gate_idle", state = idle())
    }

    // One muted sentence and nothing moves. A spinner here would be the first looping animation in
    // the product and would claim knowledge the app does not have.
    @Test
    fun `waiting on the server`() {
        capture(
            name = "gate_waiting",
            state = idle().copy(
                message = GateMessageUiState(
                    lead = Strings.signInWaitingLead(),
                    body = Strings.signInWaitingBody(),
                    tone = GateTone.WAITING,
                ),
            ),
        )
    }

    // **No signal and a service that is down are the same screen**, because they are the same
    // instruction. The block grows upward and the buttons do not move, which is what this frame is
    // for: compared with the one above, the two providers are in the same place.
    @Test
    fun `nothing answered`() {
        capture(
            name = "gate_no_answer",
            state = idle().copy(
                message = GateMessageUiState(
                    lead = Strings.signInNoAnswerLead(),
                    body = Strings.signInNoAnswerBody(),
                    tone = GateTone.FAILED,
                ),
            ),
        )
    }

    @Test
    fun `the provider refused or the player backed out`() {
        capture(
            name = "gate_refused",
            state = idle().copy(
                message = GateMessageUiState(
                    lead = Strings.signInRefusedLead(AuthProviderName.APPLE),
                    body = Strings.signInRefusedBody(AuthProviderName.GOOGLE),
                    tone = GateTone.FAILED,
                ),
            ),
        )
    }

    // The server's own number, in the app's own duration format. The digit does not tick.
    @Test
    fun `asked too often`() {
        capture(
            name = "gate_throttled",
            state = idle().copy(
                message = GateMessageUiState(
                    lead = Strings.signInThrottledLead(),
                    body = Strings.signInThrottledBody(41),
                    tone = GateTone.FAILED,
                ),
            ),
        )
    }

    // **The mark drops 88 → 76 and nothing else changes**, which is the whole of what this frame
    // holds: both button strings are mandated, so neither can shorten, and at 288dp of content they
    // still hold their text.
    @Test
    fun `the whole screen in a Slide Over window`() {
        capture(name = "gate_slide_over", state = idle(), width = SLIDE_OVER_WIDTH, compact = true)
    }

    // **The one platform that draws a single button**, and the frame that says it is a screen rather
    // than a gap: the remaining provider is full width and the foot is where it was.
    @Test
    fun `a platform that can complete one provider`() {
        capture(name = "gate_one_provider", state = idle(AuthProvider.GOOGLE))
    }

    // **And the one that can complete neither**, which is the desktop build without the Google
    // credential in its environment. Absence is the right answer for a provider that cannot finish,
    // but absence applied to both would leave a screen with no way out and nothing said about it —
    // so the block that grows above the buttons is standing where the buttons would have been. This
    // frame is the check that it does not read as a screen that failed to load.
    //
    // Not the design's: no canvas drew a build with no provider in it, and the copy is written in
    // the same voice rather than lifted. See the pull request.
    @Test
    fun `a platform that can complete no provider at all`() {
        capture(
            name = "gate_no_provider",
            state = idle().copy(
                providers = emptyList(),
                message = GateMessageUiState(
                    lead = Strings.signInNoProviderLead(),
                    body = Strings.signInNoProviderBody(),
                    tone = GateTone.FAILED,
                ),
            ),
        )
    }

    // **The second language on the one screen where two of the strings are not ours**, which is the
    // whole reason this frame exists: `Accedi con Apple` is Apple's Italian and `Accedi con Google`
    // is Google's, and a baseline is what stops either being quietly replaced by a translation of
    // the English.
    @Test
    fun `the whole screen in Italian`() {
        capture(name = "gate_italian", state = idle(), translations = Italian)
    }

    private fun capture(
        name: String,
        state: GateUiState,
        width: Int = PHONE_WIDTH,
        compact: Boolean = false,
        translations: Translations? = null,
    ) {
        runDesktopComposeUiTest(width = width, height = PHONE_HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = translations ?: English) {
                    Surface { Gate(uiState = state, compact = compact, onSignIn = {}) }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // **Built here rather than through the mapper**, and that is rule 4 rather than a preference: a
    // `ui` module may not see its own `presentation`, which is the whole reason a frame is handed a
    // declarative model. The mapping from a `GateState` into these fields is asserted by
    // `GateUiStateTest`, where it can be read as words instead of as pixels.
    private fun idle(vararg providers: AuthProvider): GateUiState = GateUiState(
        why = Strings.signInWhyLead(),
        whyFoot = Strings.signInWhyFoot(),
        foot = Strings.signInFoot(),
        providers = (if (providers.isEmpty()) AuthProvider.entries else providers.toList()).map {
            GateProviderUiState(provider = it, label = Strings.signInWith(it.spoken()))
        },
        message = null,
    )

    private fun AuthProvider.spoken(): AuthProviderName = when (this) {
        AuthProvider.APPLE -> AuthProviderName.APPLE
        AuthProvider.GOOGLE -> AuthProviderName.GOOGLE
    }
}

// The design's own frames: the window between the status bar and the home indicator, and the
// narrowest pane the app runs in.
private const val PHONE_WIDTH = 393
private const val PHONE_HEIGHT = 759
private const val SLIDE_OVER_WIDTH = 320
