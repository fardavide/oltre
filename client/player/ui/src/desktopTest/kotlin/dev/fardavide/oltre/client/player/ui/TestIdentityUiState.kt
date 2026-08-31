package dev.fardavide.oltre.client.player.ui

import dev.fardavide.oltre.client.design.text.MarkBodyName
import dev.fardavide.oltre.client.design.text.MarkPathName
import dev.fardavide.oltre.client.design.text.MarkPresetName
import dev.fardavide.oltre.client.design.text.MarkTerminusName
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark

// The two faces as a mapper would hand them over, so a frame and a robot are looking at the same thing.
//
// **The four `when`s below are not the mapper and must not become it.** Translating `:protocol`'s enums
// into the catalogue's own is `:client:profile:presentation`'s job, and it is deliberately not this
// module's — a fixture needs *some* words to draw, so it writes them here where a reader can see they
// are a stand-in. If the real mapper ever disagrees with these, nothing in this module notices, which
// is exactly what the screenshot skill's note about hand-built fixtures is warning about.

internal const val COMMITTED_NAME = "Ada Lovelace"

// Twenty-four characters exactly, which is the bound the field stops at. `NameFieldBehaviourTest`
// asserts the length rather than trusting it: the frame's "at the bound" state is only that state if
// this string really is the full length, and a miscount would draw `23/24` in the baseline forever.
internal const val NAME_AT_THE_BOUND = "The Contingency Of Ashes"

internal fun identityFaceUiState(
    chosen: MarkPreset? = MarkPreset.THRESHOLD,
    committed: String = COMMITTED_NAME,
    draft: String = committed,
    requirement: TextRes? = null,
): IdentityFaceUiState = IdentityFaceUiState(
    cells = MarkPreset.entries.map { preset -> MarkCellUiState(preset = preset, chosen = preset == chosen) },
    markName = if (chosen == null) composedMarkName(COMPOSED_MARK) else Strings.markName(presetName(chosen)),
    composed = chosen == null,
    committed = committed,
    draft = draft,
    requirement = requirement,
)

internal fun markComposeFaceUiState(
    mark: PlayerMark.Composed = COMPOSED_MARK,
    requirement: TextRes? = null,
): MarkComposeFaceUiState = MarkComposeFaceUiState(
    mark = mark,
    markName = composedMarkName(mark),
    requirement = requirement,
    bodies = MarkBody.entries.map { body -> MarkBodyChoice(body = body, name = Strings.markBodyName(bodyName(body))) },
    paths = MarkPath.entries.map { path -> MarkPathChoice(path = path, name = Strings.markPathName(pathName(path))) },
    termini = MarkTerminus.entries.map { terminus ->
        MarkTerminusChoice(terminus = terminus, name = Strings.markTerminusName(terminusName(terminus)))
    },
)

// The one composition that is also a preset, which is what the compose face opens on.
internal val COMPOSED_MARK = PlayerMark.Composed(
    body = MarkBody.LIMB,
    path = MarkPath.RISING,
    terminus = MarkTerminus.DOT,
)

// "Your mark · Limb · Rising · Dot" — the noun and the three parts, joined the way the catalogue joins
// a list. Both faces draw this same line, so both fixtures build it here.
internal fun composedMarkName(mark: PlayerMark.Composed): TextRes = Strings.clauses(
    listOf(
        Strings.markComposedName(),
        Strings.markBodyName(bodyName(mark.body)),
        Strings.markPathName(pathName(mark.path)),
        Strings.markTerminusName(terminusName(mark.terminus)),
    ),
)

private fun presetName(preset: MarkPreset): MarkPresetName = when (preset) {
    MarkPreset.THRESHOLD -> MarkPresetName.THRESHOLD
    MarkPreset.TERMINATOR -> MarkPresetName.TERMINATOR
    MarkPreset.APHELION -> MarkPresetName.APHELION
    MarkPreset.SEXTANT -> MarkPresetName.SEXTANT
    MarkPreset.WAKE -> MarkPresetName.WAKE
    MarkPreset.SOUNDING -> MarkPresetName.SOUNDING
}

private fun bodyName(body: MarkBody): MarkBodyName = when (body) {
    MarkBody.LIMB -> MarkBodyName.LIMB
    MarkBody.TERMINATOR -> MarkBodyName.TERMINATOR
    MarkBody.ORBIT -> MarkBodyName.ORBIT
    MarkBody.WAKE -> MarkBodyName.WAKE
}

private fun pathName(path: MarkPath): MarkPathName = when (path) {
    MarkPath.RISING -> MarkPathName.RISING
    MarkPath.TRANSFER -> MarkPathName.TRANSFER
    MarkPath.TWIN -> MarkPathName.TWIN
    MarkPath.NONE -> MarkPathName.NONE
}

private fun terminusName(terminus: MarkTerminus): MarkTerminusName = when (terminus) {
    MarkTerminus.DOT -> MarkTerminusName.DOT
    MarkTerminus.RING -> MarkTerminusName.RING
    MarkTerminus.NONE -> MarkTerminusName.NONE
}
