package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.design.component.PressableFace
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.TextRes

// **The door from settings**, and the first place the mark appears — at 29dp, where it does identity
// rather than readout. Claude Design's §4: the row sits at the foot of the settings column under a
// `BUILD` label, and tapping it replaces the sheet's contents with the changelog. The 29dp mark on
// the row you tap is the same drawing as the 319dp one you land on, which is the whole of the
// transition's continuity.
//
// **No chevron.** The app has no back stack and this row leads to no destination — it swaps a sheet's
// face. A chevron would promise a hierarchy to come back up.
//
// It lives in this module rather than in `:client:settings:ui` because it is made of changelog: the
// version it prints and the headline under it are the newest page of the catalogue, and the mark is
// this feature's own drawing. The composition root is what puts it on the settings sheet — which is
// why `:client:settings:ui` does not know it exists.
@Composable
@NonRestartableComposable
fun BuildRow(uiState: BuildRowUiState, onOpenChangelog: () -> Unit, modifier: Modifier = Modifier) {
    val spoken = uiState.spoken.resolve()
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = uiState.label)
        PressableFace(
            onClick = onOpenChangelog,
            shape = ROW_SHAPE,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ChangelogTestTags.BUILD_ROW)
                .semantics { contentDescription = spoken },
            faceModifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MARK_GAP),
                modifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT),
            ) {
                VersionMark(version = uiState.version, size = MARK)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = uiState.version.printed,
                        color = OltreColors.text,
                        fontFamily = oltreMono(),
                        fontSize = VERSION_SIZE,
                    )
                    Text(
                        text = uiState.headline.resolve(),
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = HEADLINE_SIZE,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// What the row says. The version is the type rather than a rendering of it, for `ChangelogPageUiState`'s
// reason: the row prints it *and* draws it.
data class BuildRowUiState(
    val label: TextRes,
    val version: ReleaseVersion,
    val headline: TextRes,
    // The row read as one sentence, for a screen reader — the one place the version and what it did
    // are said together rather than as two lines.
    val spoken: TextRes,
)

private val ROW_HEIGHT = 44.dp
private val MARK = 29.dp
private val MARK_GAP = 11.dp
private val VERSION_SIZE = 12.5.sp
private val HEADLINE_SIZE = 10.5.sp

// The settings sheet's own row shape, so a press here is clipped the way a press on a category row
// is.
private val ROW_SHAPE = RoundedCornerShape(10.dp)
