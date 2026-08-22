package dev.fardavide.oltre.client.player.ui

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import kotlin.test.Test
import kotlin.test.assertEquals

// What every launch that ships actually gets. There is no mapper to test — the name, the level and
// the experience are constants this slice — so what is worth pinning is that they are the *right*
// constants, and above all that they are catalogue entries rather than words typed into a screen.
class PlayerStripUiStateTest {

    @Test
    fun `should open at level zero with no experience`() {
        val state = playerStripUiState()

        assertEquals(Strings.levelBadge(0), state.level)
        assertEquals(0, state.experiencePercent)
    }

    @Test
    fun `should take the name from the catalogue rather than from a literal`() {
        // The assertion is `Strings.playerDefaultName()` and not the string it resolves to, on the
        // rule `TextRes` exists for: a test asserts on meaning, so this keeps passing when the
        // wording changes and fails when the *message* does. A `TextRes.Raw` here would be the
        // shape of the bug — a name no language can ever disagree with.
        assertEquals(Strings.playerDefaultName(), playerStripUiState().name)
    }

    @Test
    fun `should give the name words in both languages`() {
        // Italian keeps the English callsign deliberately — see `Italian.kt` — and this is what
        // makes that a decision rather than a gap somebody forgot to fill.
        val name = playerStripUiState().name

        assertEquals("Dead Reckoning", English.resolve(name))
        assertEquals("Dead Reckoning", Italian.resolve(name))
    }

    @Test
    fun `should be the same state every time it is asked for`() {
        // Nothing in it is derived from a clock, a seed or a save, and that is the whole of §3 of
        // the decision sheet expressed as a test: the day this stops being true is the day the
        // feature has earned a `presentation` module, and this fails to say so.
        assertEquals(playerStripUiState(), playerStripUiState())
    }
}
