package dev.fardavide.oltre.client.player.presentation

import dev.fardavide.oltre.client.design.text.MarkBodyName
import dev.fardavide.oltre.client.design.text.MarkPathName
import dev.fardavide.oltre.client.design.text.MarkPresetName
import dev.fardavide.oltre.client.design.text.MarkTerminusName
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.player.ui.IdentityFaceUiState
import dev.fardavide.oltre.client.player.ui.MarkBodyChoice
import dev.fardavide.oltre.client.player.ui.MarkCellUiState
import dev.fardavide.oltre.client.player.ui.MarkComposeFaceUiState
import dev.fardavide.oltre.client.player.ui.MarkPathChoice
import dev.fardavide.oltre.client.player.ui.MarkTerminusChoice
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile

// **The account, as the two faces that change it** — and, one file over, as the strip that wears it.
//
// **`PlayerProfile` is the receiver rather than the argument, which is the opposite of every other
// mapper in this repository, and the difference is real.** The others answer *what does this colony
// look like*; these answer *who is this*, which is a fact about an account that outlives every colony
// hung off it. The strip is the one state that reads both, and its mapper takes the colony as an
// argument for exactly that reason — see `PlayerStripFromState`.

// **A null receiver is an account this device has not read**, and every function in this file takes
// one. It is not the same thing as an account that has chosen nothing, and only one of the two may be
// written over: `POST /v1/profile` replaces the row whole, so a write assembled from an assumed-empty
// profile deletes whatever the account actually held. What the two states share is what they *draw* —
// `Threshold` and `Dead Reckoning` — which is why the substitutions below take a null receiver
// happily; what tells them apart is the requirement the shell hands in, and the shell is where the
// nullable stops anything being sent. See `App`'s `profile` and `profileRequirement`.

// The picker: six silhouettes, the word for whichever is worn, a door to the composer, and the name.
//
// **A null draft is the committed name**, which is the field's resting state said once rather than
// seeded into the shell three times. A draft is what a player typed and there is none until they
// type: `App` holds `null` until `onNameChange`, and every event that ends an edit puts it back — so
// a name arriving from the server cannot land on top of typing that is still in progress.
fun PlayerProfile?.toIdentityFaceUiState(draft: String?, requirement: TextRes?): IdentityFaceUiState {
    val worn = worn()
    val committed = this?.name?.value.orEmpty()
    return IdentityFaceUiState(
        // **The offered set is stated here, which is what `IdentityFaceUiState.cells` asks for**: a
        // preset retired from the grid is one accounts still wear and the wire still serves, so the
        // two lists move on different days and this is the line the first of them moves on. Today it
        // is all six, in the order `:protocol` declares them — which is the order the frame drew.
        cells = MarkPreset.entries.map { preset ->
            MarkCellUiState(preset = preset, chosen = worn == PlayerMark.Preset(preset))
        },
        markName = worn.spoken(),
        // Not derivable from `cells` and deliberately its own field: every cell being unlit is also
        // what a mark this build has no drawing for would look like, and those must not read alike.
        composed = worn is PlayerMark.Composed,
        // **Empty rather than `Dead Reckoning` for an account that has chosen nothing.** The default
        // is what the *placeholder* says, and it says it while the player is looking at it; seeding
        // the field with it would put a name nobody typed in front of a save button.
        committed = committed,
        draft = draft ?: committed,
        requirement = requirement,
    )
}

// Forty marks from eleven drawings, as three ladders and the thing they make.
//
// **The requirement is the composer's too, and it was the one face without it.** Eleven chips that
// stay lit with nothing behind them are eleven controls that answer a tap with nothing a player can
// perceive — the failure this product treats as worse than a crash — so the composer wears the same
// card the identity face does, from the same fact, at the same 42%.
fun PlayerProfile?.toMarkComposeFaceUiState(requirement: TextRes?): MarkComposeFaceUiState {
    val composing = composing()
    return MarkComposeFaceUiState(
        mark = composing,
        markName = composing.spoken(),
        requirement = requirement,
        bodies = MarkBody.entries.map { body ->
            MarkBodyChoice(body = body, name = Strings.markBodyName(body.spoken()))
        },
        paths = MarkPath.entries.map { path ->
            MarkPathChoice(path = path, name = Strings.markPathName(path.spoken()))
        },
        // Always the three, even while the path is `NONE` and the ladder is not drawn: the words do
        // not stop existing because a row is off screen.
        termini = MarkTerminus.entries.map { terminus ->
            MarkTerminusChoice(terminus = terminus, name = Strings.markTerminusName(terminus.spoken()))
        },
    )
}

// **A tap on a chip is a whole mark rather than a slot**, because that is what the wire carries and
// what the strip then wears. Three functions rather than one over a slot enum, for the reason
// `MarkBodyChoice` and its two neighbours are three records: these cannot be handed each other's
// arguments, where one function taking a `MarkPart` could be.
//
// **All three are total, and that is a requirement rather than a property they happen to have.** The
// shell applies them *inside* the write lock, against whatever row the last answer left behind —
// while the chips a finger is aiming at were drawn from the row before it. So the mark an edit lands
// on is not the mark the tap was made over, and no sequence of taps may be able to assemble one
// `PlayerMark.Composed` refuses to be built as. A swap that could is not a wrong drawing, it is the
// app going down under the finger that tapped.
fun PlayerProfile.withBody(body: MarkBody): PlayerMark.Composed = composing().copy(body = body)

// Choosing `None` clears the terminus with it, which is exactly what the ladder disappearing means.
fun PlayerProfile.withPath(path: MarkPath): PlayerMark.Composed {
    val composing = composing()
    return if (path == MarkPath.NONE) {
        PlayerMark.Composed(body = composing.body, path = MarkPath.NONE, terminus = MarkTerminus.NONE)
    } else {
        composing.copy(path = path)
    }
}

// **A terminus over a mark with no path leaves the mark exactly where it is.** The design settles
// what the pair means in one sentence — *"a terminus is the end of a path, so a mark with no path has
// none"* — and coercing the terminus to `NONE` and ignoring the tap are the same answer here, because
// a pathless composition already carries `NONE`. What is not defensible is the third reading: giving
// the terminus a path to end would put back the one the player had just cleared, undoing a tap they
// made deliberately.
//
// This used to be a bare `copy`, on the argument that the composer draws no terminus ladder while the
// path is `NONE` so there is no chip to tap. That is true of the mark on *screen* and false of the
// mark this is applied to — a terminus tapped inside a `withPath(NONE)` write's round trip lands on
// the row that write produced, and the `require` in `Composed`'s `init` threw.
fun PlayerProfile.withTerminus(terminus: MarkTerminus): PlayerMark.Composed {
    val composing = composing()
    return if (composing.path == MarkPath.NONE) composing else composing.copy(terminus = terminus)
}

// **What the account is called, with the default substituted once**, and public because the strip is
// not the only thing that draws it: the settings sheet's Account row says the same name over a
// founding date, and two places deciding what an account with no chosen name is called is one place
// too many. A name a player typed is untranslatable by construction, which is what `TextRes.Raw`
// says — and it is drawn no differently from the catalogue entry beside it, because `Dead Reckoning`
// is a name rather than a placeholder.
fun PlayerProfile?.spokenName(): TextRes = this?.name?.let { TextRes(it.value) } ?: Strings.playerDefaultName()

// **What the account wears, with the default substituted once.** `PlayerProfile` argues why the
// substitution belongs where the mark is drawn rather than on the wire — a default is a mark and not
// an absence, and two commanders may already share a name — and this is the single place it happens,
// so the grid, the strip and the line under the grid cannot disagree about it.
internal fun PlayerProfile?.worn(): PlayerMark = this?.mark ?: PlayerMark.Preset(MarkPreset.THRESHOLD)

// **Where the composer opens, and it is a different question from what the account wears.** Four of
// the six presets are shapes the grammar has no parts for — a centred disc, a full-width ellipse, a
// 12.4-unit arc, a full-height plumb line — so there is nothing to carry across and the face opens on
// the one preset that is also a composition. `MarkPreset.asComposed()` is where that correspondence is
// declared, and reading it here rather than restating the tuple is what keeps the two in step.
private fun PlayerProfile?.composing(): PlayerMark.Composed = when (val worn = worn()) {
    is PlayerMark.Composed -> worn
    is PlayerMark.Preset -> worn.preset.asComposed() ?: OPENS_ON
}

// The mark the composer opens on when the one worn cannot be carried into it. Checked rather than
// written out, so a `THRESHOLD` that stopped being a composition fails here instead of quietly
// opening the face on a tuple nobody chose.
private val OPENS_ON: PlayerMark.Composed = checkNotNull(MarkPreset.THRESHOLD.asComposed()) {
    "THRESHOLD is the one preset the composer can make and it answered none"
}

// **The line under the grid, and the line on the composer's card** — one function because it is one
// sentence about one mark, said on two faces. A preset has a noun of its own; a composed mark has
// none, so the noun is the word for *composed* and the sentence is its three parts after it.
internal fun PlayerMark.spoken(): TextRes = when (this) {
    is PlayerMark.Preset -> Strings.markName(preset.spoken())
    is PlayerMark.Composed -> Strings.clauses(
        listOf(
            Strings.markComposedName(),
            Strings.markBodyName(body.spoken()),
            Strings.markPathName(path.spoken()),
            Strings.markTerminusName(terminus.spoken()),
        ),
    )
}

// **The two vocabularies meeting, and this is the only place they do.** `:client:design:text` names no
// wire type — a table of words has no business on the contract's compile classpath — and `:protocol`
// names no words, so the four mappings are four `when`s in a presentation module rather than a
// dependency either of them would have had to carry. Exactly `AuthProvider.spoken()`'s shape, and
// exhaustive with no `else` for its reason: a seventh preset cannot reach a screen without somebody
// writing the word for it first.
private fun MarkPreset.spoken(): MarkPresetName = when (this) {
    MarkPreset.THRESHOLD -> MarkPresetName.THRESHOLD
    MarkPreset.TERMINATOR -> MarkPresetName.TERMINATOR
    MarkPreset.APHELION -> MarkPresetName.APHELION
    MarkPreset.SEXTANT -> MarkPresetName.SEXTANT
    MarkPreset.WAKE -> MarkPresetName.WAKE
    MarkPreset.SOUNDING -> MarkPresetName.SOUNDING
}

private fun MarkBody.spoken(): MarkBodyName = when (this) {
    MarkBody.LIMB -> MarkBodyName.LIMB
    MarkBody.TERMINATOR -> MarkBodyName.TERMINATOR
    MarkBody.ORBIT -> MarkBodyName.ORBIT
    MarkBody.WAKE -> MarkBodyName.WAKE
}

// `NONE` is a part rather than an absence in both this family and the one below: the composer draws
// it as a chip a player taps, so it is named like the three beside it.
private fun MarkPath.spoken(): MarkPathName = when (this) {
    MarkPath.RISING -> MarkPathName.RISING
    MarkPath.TRANSFER -> MarkPathName.TRANSFER
    MarkPath.TWIN -> MarkPathName.TWIN
    MarkPath.NONE -> MarkPathName.NONE
}

private fun MarkTerminus.spoken(): MarkTerminusName = when (this) {
    MarkTerminus.DOT -> MarkTerminusName.DOT
    MarkTerminus.RING -> MarkTerminusName.RING
    MarkTerminus.NONE -> MarkTerminusName.NONE
}
