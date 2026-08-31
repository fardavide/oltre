package dev.fardavide.oltre.client.player.ui

import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus

// **Handles for the controls on the two identity faces, and separate from `PlayerTestTags` because
// they answer about a different thing.** That object names the pieces of the strip — chrome that is on
// screen behind every destination — where these name the pieces of a sheet the strip opens. One object
// holding both would put a tag for a control that exists for ten seconds beside a tag for a bar that is
// never not there.
//
// Public for `SettingsTestTags`' reason: the composition root raises the sheet, so the behaviour test
// that proves tapping the strip lands on *this* face lives in `:client:shell` and needs a handle it can
// name. Nothing here is reachable any other way — a mark cell is a drawing with no text in it, and the
// clear glyph is two strokes.
object IdentityTestTags {

    const val FACE = "identity-face"
    const val COMPOSE_FACE = "identity-compose-face"

    const val NAME = "identity-name"
    const val CLEAR = "identity-name-clear"
    const val COUNTER = "identity-name-counter"

    // **Absent rather than disabled when there is nothing to save**, so what a test asks this tag is
    // whether the node exists at all.
    const val SAVE = "identity-save"

    const val COMPOSE_ROW = "identity-compose-row"
    const val REQUIREMENT = "identity-requirement"

    // The whole third ladder, tagged as one, because what the design says about it is about the ladder
    // and not about a chip: with no path there is no terminus, so the row is not drawn.
    const val TERMINUS_LADDER = "identity-terminus-ladder"

    fun cell(preset: MarkPreset): String = "identity-cell-${preset.name}"

    fun body(body: MarkBody): String = "identity-body-${body.name}"

    fun path(path: MarkPath): String = "identity-path-${path.name}"

    fun terminus(terminus: MarkTerminus): String = "identity-terminus-${terminus.name}"
}
