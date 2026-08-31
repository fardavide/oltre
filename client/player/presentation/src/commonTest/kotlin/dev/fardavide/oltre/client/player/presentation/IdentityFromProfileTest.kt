package dev.fardavide.oltre.client.player.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.MarkBodyName
import dev.fardavide.oltre.client.design.text.MarkPathName
import dev.fardavide.oltre.client.design.text.MarkPresetName
import dev.fardavide.oltre.client.design.text.MarkTerminusName
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The account's two facts becoming the two faces that change them.
//
// **What is under test that nothing else can see is the pairing of two vocabularies.** `:protocol`
// has four enums and `:client:design:text` has four of its own, deliberately distinct, and this
// module is the one place they meet — so a preset renamed on one side and not the other is a
// compile error here and a wrong word everywhere else.
class IdentityFromProfileTest {

    // ── The strip ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should draw the catalogue's own name for an account that has chosen none`() {
        assertEquals(Strings.playerDefaultName(), genesis().toPlayerStripUiState(unchosen()).name)
    }

    @Test
    fun `should draw a chosen name as text nothing can translate`() {
        // A name a keyboard produced has no entry in any table, which is what `TextRes.Raw` is for —
        // and asserting the wrapper rather than the string is what keeps this a claim about *kind*.
        val named = PlayerProfile(name = CommanderName("Ada Lovelace"), mark = null)

        val name = genesis().toPlayerStripUiState(named).name

        assertEquals("Ada Lovelace", English.resolve(name))
        assertEquals(TextRes("Ada Lovelace"), name)
    }

    // **The name the settings sheet's Account row draws too**, which is why the substitution is a
    // function of its own rather than a line inside the strip's mapper: two places deciding what an
    // account with no chosen name is called is one place too many, and only one of them would have
    // moved on the day a player renamed themselves.
    @Test
    fun `should call the account the same thing wherever it is drawn`() {
        val named = PlayerProfile(name = CommanderName("Ada"), mark = null)

        assertEquals(genesis().toPlayerStripUiState(named).name, named.spokenName())
        assertEquals(genesis().toPlayerStripUiState(unchosen()).name, unchosen().spokenName())
    }

    @Test
    fun `should wear the threshold mark until the account says otherwise`() {
        // **A default is a mark rather than an absence** — `PlayerProfile` argues it and this is the
        // line that acts on it. Two commanders may already share a name, so an unchosen mark drawn
        // any differently from a chosen one would be a claim the server cannot make.
        assertEquals(THRESHOLD, genesis().toPlayerStripUiState(unchosen()).mark)
    }

    @Test
    fun `should wear the mark the account chose`() {
        val chosen = PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.SEXTANT))

        assertEquals(PlayerMark.Preset(MarkPreset.SEXTANT), genesis().toPlayerStripUiState(chosen).mark)
    }

    @Test
    fun `should wear a composed mark exactly as it was assembled`() {
        val chosen = PlayerProfile(name = null, mark = COMPOSED)

        assertEquals(COMPOSED, genesis().toPlayerStripUiState(chosen).mark)
    }

    // **An account this device has not read draws exactly what an account that has chosen nothing
    // draws**, which is what lets the shell hold a nullable without a third state on screen: a strip
    // that showed a spinner or a blank would be reporting on the app rather than on the player. What
    // must never treat the two alike is the write — see `App`'s `profile` and `profileRequirement`.
    @Test
    fun `should draw the default for an account this device has not read`() {
        val strip = genesis().toPlayerStripUiState(null)

        assertEquals(Strings.playerDefaultName(), strip.name)
        assertEquals(THRESHOLD, strip.mark)
    }

    @Test
    fun `should open the composer on threshold for an account this device has not read`() {
        assertEquals(COMPOSED, null.toMarkComposeFaceUiState(requirement = null).mark)
    }

    // ── The identity face ────────────────────────────────────────────────────────────────────

    @Test
    fun `should offer every preset the wire can carry`() {
        val cells = unchosen().toIdentityFaceUiState(draft = "", requirement = null).cells

        assertEquals(MarkPreset.entries, cells.map { it.preset })
    }

    @Test
    fun `should light the cell of the preset the account wears`() {
        val chosen = PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.WAKE))

        val cells = chosen.toIdentityFaceUiState(draft = "", requirement = null).cells

        assertEquals(listOf(MarkPreset.WAKE), cells.filter { it.chosen }.map { it.preset })
    }

    @Test
    fun `should light the threshold cell for an account that has chosen nothing`() {
        // The strip draws `Threshold` for a null mark, so the grid has to agree with it: a face that
        // opened with nothing lit would be telling the player their mark is not one of these six.
        val cells = unchosen().toIdentityFaceUiState(draft = "", requirement = null).cells

        assertEquals(listOf(MarkPreset.THRESHOLD), cells.filter { it.chosen }.map { it.preset })
    }

    @Test
    fun `should light no cell at all when the mark is composed`() {
        val chosen = PlayerProfile(name = null, mark = COMPOSED)

        val uiState = chosen.toIdentityFaceUiState(draft = "", requirement = null)

        assertTrue(uiState.cells.none { it.chosen }, "a composed mark lit a cell in the preset grid")
        assertTrue(uiState.composed, "a composed mark did not light the compose row")
    }

    @Test
    fun `should say the mark is not composed when it is a preset`() {
        assertFalse(unchosen().toIdentityFaceUiState(draft = "", requirement = null).composed)
    }

    @Test
    fun `should name a preset in the catalogue's own word`() {
        val chosen = PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.APHELION))

        assertEquals(
            Strings.markName(MarkPresetName.APHELION),
            chosen.toIdentityFaceUiState(draft = "", requirement = null).markName,
        )
    }

    @Test
    fun `should spell a composed mark out as the noun and its three parts`() {
        val chosen = PlayerProfile(name = null, mark = COMPOSED)

        assertEquals(
            Strings.clauses(
                listOf(
                    Strings.markComposedName(),
                    Strings.markBodyName(MarkBodyName.LIMB),
                    Strings.markPathName(MarkPathName.RISING),
                    Strings.markTerminusName(MarkTerminusName.DOT),
                ),
            ),
            chosen.toIdentityFaceUiState(draft = "", requirement = null).markName,
        )
    }

    @Test
    fun `should leave an account with no name an empty field`() {
        // Empty rather than `Dead Reckoning`: the placeholder is what says what saving nothing gives
        // you, and a field pre-filled with the default would make the save button appear for a name
        // the player never typed.
        val uiState = unchosen().toIdentityFaceUiState(draft = "", requirement = null)

        assertEquals("", uiState.committed)
        assertEquals("", uiState.draft)
    }

    @Test
    fun `should hand the field the name that is committed`() {
        val named = PlayerProfile(name = CommanderName("Ada"), mark = null)

        assertEquals("Ada", named.toIdentityFaceUiState(draft = "Ada", requirement = null).committed)
    }

    @Test
    fun `should carry the draft it was handed rather than the committed name`() {
        // The draft is the shell's, because only the shell survives the face being swapped for the
        // composer and back. What the mapper must not do is quietly replace it with what is saved.
        val named = PlayerProfile(name = CommanderName("Ada"), mark = null)

        assertEquals("Ad", named.toIdentityFaceUiState(draft = "Ad", requirement = null).draft)
    }

    @Test
    fun `should carry the requirement through untouched`() {
        val held = Strings.profileHeldRequirement(hour = 11, minute = 31)

        assertEquals(held, unchosen().toIdentityFaceUiState(draft = "", requirement = held).requirement)
    }

    @Test
    fun `should say nothing about the network when there is signal`() {
        assertNull(unchosen().toIdentityFaceUiState(draft = "", requirement = null).requirement)
    }

    // **A null draft is a field nobody has typed in**, and it draws what is committed. That is what
    // took the seeding out of the shell: the launch, a gate sign-in and the retry all learn a name,
    // and every one of them used to write it into the draft — which would land on top of typing
    // already in progress the moment a read arrived while the sheet was open.
    @Test
    fun `should draw the committed name in a field nobody has typed in`() {
        val named = PlayerProfile(name = CommanderName("Ada"), mark = null)

        assertEquals("Ada", named.toIdentityFaceUiState(draft = null, requirement = null).draft)
    }

    // **An empty draft is not the same as no draft**, which is the whole reason the parameter is
    // nullable rather than defaulted: clearing the field is the way out of a name somebody regrets,
    // and a mapper that read empty as *nothing typed* would put the old name straight back.
    @Test
    fun `should keep an emptied field empty rather than refilling it`() {
        val named = PlayerProfile(name = CommanderName("Ada"), mark = null)

        assertEquals("", named.toIdentityFaceUiState(draft = "", requirement = null).draft)
    }

    // ── The composer ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `should open the composer on the mark the account is already wearing`() {
        val chosen = PlayerProfile(name = null, mark = ORBITING)

        assertEquals(ORBITING, chosen.toMarkComposeFaceUiState(requirement = null).mark)
    }

    @Test
    fun `should open the composer on threshold's own composition for an account that has chosen nothing`() {
        assertEquals(COMPOSED, unchosen().toMarkComposeFaceUiState(requirement = null).mark)
    }

    @Test
    fun `should open the composer on threshold when the preset worn cannot be composed`() {
        // Four of the six presets are shapes the grammar has no parts for, so there is nothing to
        // carry across — and the one preset that is also a composition is where the face opens.
        for (preset in MarkPreset.entries) {
            val chosen = PlayerProfile(name = null, mark = PlayerMark.Preset(preset))
            val opened = chosen.toMarkComposeFaceUiState(requirement = null).mark

            assertEquals(preset.asComposed() ?: COMPOSED, opened, "the composer opened wrongly on $preset")
        }
    }

    @Test
    fun `should offer every part the wire can carry`() {
        val uiState = unchosen().toMarkComposeFaceUiState(requirement = null)

        assertEquals(MarkBody.entries, uiState.bodies.map { it.body })
        assertEquals(MarkPath.entries, uiState.paths.map { it.path })
        assertEquals(MarkTerminus.entries, uiState.termini.map { it.terminus })
    }

    @Test
    fun `should name every part in the catalogue's own words`() {
        val uiState = unchosen().toMarkComposeFaceUiState(requirement = null)

        assertEquals(Strings.markBodyName(MarkBodyName.ORBIT), uiState.bodies.single { it.body == MarkBody.ORBIT }.name)
        assertEquals(Strings.markPathName(MarkPathName.TWIN), uiState.paths.single { it.path == MarkPath.TWIN }.name)
        assertEquals(
            Strings.markTerminusName(MarkTerminusName.RING),
            uiState.termini.single { it.terminus == MarkTerminus.RING }.name,
        )
    }

    // **The composer wears the same card the grid does**, because its eleven chips commit the same
    // way. It carried no requirement at all until the offline face was found to leave every one of
    // them lit and answering nothing.
    @Test
    fun `should carry the requirement through to the composer untouched`() {
        val held = Strings.profileHeldRequirement(hour = 11, minute = 31)

        assertEquals(held, unchosen().toMarkComposeFaceUiState(requirement = held).requirement)
    }

    @Test
    fun `should say nothing to the composer about the network when there is signal`() {
        assertNull(unchosen().toMarkComposeFaceUiState(requirement = null).requirement)
    }

    @Test
    fun `should spell the composer's own line the way the grid's is spelled`() {
        // One line and one vocabulary: the card above the ladders and the line under the grid say the
        // same thing about the same mark, so a face that named it differently would be two answers.
        val chosen = PlayerProfile(name = null, mark = ORBITING)

        assertEquals(
            chosen.toIdentityFaceUiState(draft = "", requirement = null).markName,
            chosen.toMarkComposeFaceUiState(requirement = null).markName,
        )
    }

    // ── The three slots, swapped one at a time ───────────────────────────────────────────────

    @Test
    fun `should keep the other two slots when the body is swapped`() {
        val chosen = PlayerProfile(name = null, mark = COMPOSED)

        assertEquals(COMPOSED.copy(body = MarkBody.WAKE), chosen.withBody(MarkBody.WAKE))
    }

    @Test
    fun `should keep the other two slots when the path is swapped`() {
        val chosen = PlayerProfile(name = null, mark = COMPOSED)

        assertEquals(COMPOSED.copy(path = MarkPath.TWIN), chosen.withPath(MarkPath.TWIN))
    }

    @Test
    fun `should keep the other two slots when the terminus is swapped`() {
        val chosen = PlayerProfile(name = null, mark = COMPOSED)

        assertEquals(COMPOSED.copy(terminus = MarkTerminus.RING), chosen.withTerminus(MarkTerminus.RING))
    }

    @Test
    fun `should drop the terminus with the path it was the end of`() {
        // `PlayerMark.Composed` throws on the pair rather than allowing it, so a mapper that copied
        // the path across would take the app down at the tap rather than compile wrongly.
        val chosen = PlayerProfile(name = null, mark = COMPOSED)

        assertEquals(
            PlayerMark.Composed(body = MarkBody.LIMB, path = MarkPath.NONE, terminus = MarkTerminus.NONE),
            chosen.withPath(MarkPath.NONE),
        )
    }

    @Test
    fun `should swap the body of a mark that has no path at all`() {
        // The other half of the same guard: with no path the terminus is already `NONE`, so this must
        // carry it rather than reaching for the one the last mark had.
        val pathless = PlayerProfile(
            name = null,
            mark = PlayerMark.Composed(body = MarkBody.LIMB, path = MarkPath.NONE, terminus = MarkTerminus.NONE),
        )

        assertEquals(
            PlayerMark.Composed(body = MarkBody.ORBIT, path = MarkPath.NONE, terminus = MarkTerminus.NONE),
            pathless.withBody(MarkBody.ORBIT),
        )
    }

    @Test
    fun `should never assemble an illegal mark out of a legal one`() {
        // Every swap of every slot from every legal mark: forty marks times eleven taps, and none of
        // them may throw.
        //
        // **The terminus row used to be skipped when the path was `NONE`, and the exemption was the
        // defect.** The argument for it was that the composer draws no terminus ladder over a
        // pathless mark, so there is no chip to tap — which is true of the *drawn* mark and false of
        // the one the edit is applied to: the chips come from what the last answer left behind, and a
        // terminus tapped inside a `withPath(NONE)` write's round trip lands on the row that write
        // produced. A test that skipped the case could not see it, and `PlayerMark.Composed` threw
        // under the finger that tapped.
        for (mark in everyLegalMark()) {
            val worn = PlayerProfile(name = null, mark = mark)
            MarkBody.entries.forEach { worn.withBody(it) }
            MarkPath.entries.forEach { worn.withPath(it) }
            MarkTerminus.entries.forEach { worn.withTerminus(it) }
        }
    }

    // **What a terminus means over a mark whose path has gone**, which is the question the crash
    // above was hiding. The design settles it in one sentence — *"a terminus is the end of a path, so
    // a mark with no path has none"* — and the only reading that does not invent something is to
    // leave the mark alone: resurrecting the path the player just cleared, to give the terminus
    // something to end, would undo a tap they made deliberately.
    @Test
    fun `should leave a mark with no path exactly where it is when a terminus is chosen`() {
        val pathless = PlayerProfile(
            name = null,
            mark = PlayerMark.Composed(body = MarkBody.ORBIT, path = MarkPath.NONE, terminus = MarkTerminus.NONE),
        )

        assertEquals(
            PlayerMark.Composed(body = MarkBody.ORBIT, path = MarkPath.NONE, terminus = MarkTerminus.NONE),
            pathless.withTerminus(MarkTerminus.RING),
        )
    }

    @Test
    fun `should start composing from threshold when the account wears a preset the grammar cannot make`() {
        val chosen = PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.SOUNDING))

        assertEquals(COMPOSED.copy(body = MarkBody.WAKE), chosen.withBody(MarkBody.WAKE))
    }

    private fun unchosen(): PlayerProfile = PlayerProfile(name = null, mark = null)

    // The colony half of the strip, which this file never asks a question about: the level and the
    // gauge are `PlayerStripFromStateTest`'s, and what is under test here is the account above them.
    private fun genesis(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private fun everyLegalMark(): List<PlayerMark.Composed> = buildList {
        for (body in MarkBody.entries) {
            add(PlayerMark.Composed(body = body, path = MarkPath.NONE, terminus = MarkTerminus.NONE))
            for (path in MarkPath.entries.filter { it != MarkPath.NONE }) {
                for (terminus in MarkTerminus.entries) {
                    add(PlayerMark.Composed(body = body, path = path, terminus = terminus))
                }
            }
        }
    }

    private companion object {

        val THRESHOLD = PlayerMark.Preset(MarkPreset.THRESHOLD)

        // The one composition that is also a preset, which is what the composer opens on.
        val COMPOSED = PlayerMark.Composed(
            body = MarkBody.LIMB,
            path = MarkPath.RISING,
            terminus = MarkTerminus.DOT,
        )

        val ORBITING = PlayerMark.Composed(
            body = MarkBody.ORBIT,
            path = MarkPath.TRANSFER,
            terminus = MarkTerminus.RING,
        )
    }
}
