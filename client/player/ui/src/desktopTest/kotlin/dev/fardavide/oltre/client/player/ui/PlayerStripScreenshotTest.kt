package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **Every frame goes through the strip's own state, never through a tap.** A `performClick` before a
// capture bakes the press indication into the baseline and pins it there forever — and since 0.18
// there is nothing on the strip a tap changes anyway: both controls report, and the frame answers
// somewhere else.
@OptIn(ExperimentalTestApi::class)
class PlayerStripScreenshotTest {

    @Test
    fun `player strip at rest`() {
        capture(name = "player_strip")
    }

    // The narrowest window the app has to survive. **Nothing gives**, which is the point of the
    // frame: with the gauge on the bar's edge the name has the whole row at 320dp as well as at 393,
    // and there is no compact rule left here to photograph.
    @Test
    fun `player strip in a Slide Over window`() {
        capture(name = "player_strip_slide_over", width = SLIDE_OVER_WIDTH)
    }

    // A colony several levels in, and the only frame in which the edge under the strip is lit at all.
    // What it is for is the objection the design records against itself — that a line pinned under a
    // bar reads as loading — which can only be judged by looking at it.
    @Test
    fun `player strip once there is something to show`() {
        capture(
            name = "player_strip_levelled",
            uiState = PlayerStripUiState(
                name = Strings.playerDefaultName(),
                mark = PlayerMark.Preset(MarkPreset.THRESHOLD),
                level = Strings.levelBadge(7),
                experiencePercent = 62,
            ),
        )
    }

    // The longest name the design drew, at the width where it would have been cut. With the 72dp
    // inline track this ellipsised; the frame is what says the edge gauge bought the whole name back.
    // It is also the one frame in which the cluster reaches its cap, so it is where the arrow's
    // 14.5dp is actually spent — everywhere else the cluster hugs and the name measures itself.
    @Test
    fun `player strip carrying the longest name drawn, in a Slide Over window`() {
        capture(
            name = "player_strip_long_name",
            width = SLIDE_OVER_WIDTH,
            uiState = PlayerStripUiState(
                // The longest of the alternates the design drew, and a `Raw` rather than a catalogue
                // entry because it is not the app's name — it is the width case, and a name is
                // untranslatable by construction anyway.
                name = TextRes("Contingency Of Ash"),
                mark = PlayerMark.Preset(MarkPreset.THRESHOLD),
                level = Strings.levelBadge(7),
                experiencePercent = 62,
            ),
        )
    }

    // **A mark out of the grammar rather than out of the set**, and the only frame in this module
    // that photographs one. Every other baseline here wears `THRESHOLD`, which is the one preset the
    // composer can also make — so without this the strip's whole drawing could be pinned by pictures
    // that never left the default. An orbit, a transfer and a ring: three parts none of which
    // `THRESHOLD` uses, so a strip that ignored its state would be visibly the wrong picture rather
    // than a subtly different one.
    @Test
    fun `player strip wearing a mark the player composed`() {
        capture(
            name = "player_strip_composed_mark",
            uiState = newColonyPlayerStrip.copy(
                mark = PlayerMark.Composed(
                    body = MarkBody.ORBIT,
                    path = MarkPath.TRANSFER,
                    terminus = MarkTerminus.RING,
                ),
            ),
        )
    }

    // **The narrowest window in the other language, and what it pins is that there is nothing to
    // translate.** The strip draws exactly two strings and the Italian catalogue gives both of them
    // the same words — `Dead Reckoning` is a callsign that table keeps in English deliberately, and
    // `LV 0` is `LV 0` — so this file is byte-identical to `player_strip_slide_over.png` today. That
    // is the frame rather than a reason to skip it: it is captured at the same state and the same
    // width as its English twin, so the *only* difference between the two is the locale, and the day
    // they stop matching is the day something localisable arrived on the bar without anybody deciding
    // it should. `Contingency Of Ash` could not make this assertion — a name is a `TextRes.Raw`, and
    // a raw string says nothing about a language.
    @Test
    fun `player strip in Italian in a Slide Over window`() {
        capture(name = "player_strip_italian_slide_over", width = SLIDE_OVER_WIDTH, translations = Italian)
    }

    private fun capture(
        name: String,
        width: Int = PHONE_WIDTH,
        uiState: PlayerStripUiState = newColonyPlayerStrip,
        translations: Translations = English,
    ) {
        runDesktopComposeUiTest(width = width, height = STRIP_FRAME_HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = translations) {
                    Surface {
                        // Filling the window is what pins the capture: `captureRoboImage` on
                        // `onRoot()` photographs the root node's measured bounds rather than the
                        // window, so without this the image would be the strip's own height and a
                        // one-pixel disagreement between two machines would fail on dimensions
                        // before it ever compared a pixel. The rail's own baselines learned this.
                        Box(modifier = Modifier.fillMaxSize()) {
                            PlayerStrip(uiState = uiState, onOpenSettings = {}, onOpenProfile = {})
                        }
                    }
                }
            }
            // Past the one-shot fill, so the edge is photographed where it settles rather than
            // wherever the first frame caught it.
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private companion object {

        // Taller than the 40dp strip by a clear band of window, so nothing is clipped and the edge
        // along the bottom has background under it to be an edge against.
        const val STRIP_FRAME_HEIGHT = 60
    }
}

// **The notice's two baselines left with 0.18**, and so did the two files they photographed. They
// held a card that said `Coming soon` in two languages, at two widths — the gear has a sheet behind
// it now, and what is worth a baseline is that sheet. See `:client:settings:ui`.
