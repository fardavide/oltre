package dev.fardavide.oltre.client.auth.presentation

import dev.fardavide.oltre.client.auth.ui.GateTone
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.protocol.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// **Seven states and one screen**, and every assertion here is about the sentence rather than about
// the picture: the gate is the one surface in the app where the words *are* the design.
class GateUiStateTest {

    @Test
    fun `should say nothing at rest`() {
        assertNull(GateState.Idle.toGateUiState(BOTH).message)
    }

    // Apple first, because this is iPhone first — and because the HIG asks that its button not sit
    // below the others. Ordered here rather than by the platform's own set, so a platform that
    // answered them the other way round could not quietly reorder the screen.
    @Test
    fun `should draw Apple above Google`() {
        val providers = GateState.Idle.toGateUiState(BOTH).providers

        assertEquals(listOf(AuthProvider.APPLE, AuthProvider.GOOGLE), providers.map { it.provider })
    }

    // **A provider this build cannot complete is not drawn at all.** A button that opens a browser
    // which never comes back is the worst control a gate has available, so absence is the answer —
    // which is what `signInProviders` is for and why this takes a set rather than deriving one.
    @Test
    fun `should draw only the providers the platform can complete`() {
        val providers = GateState.Idle.toGateUiState(setOf(AuthProvider.GOOGLE)).providers

        assertEquals(listOf(AuthProvider.GOOGLE), providers.map { it.provider })
    }

    // **A screen with no control on it has to say why, and this is the one state that can produce
    // one.** Absence is the right answer for a provider this build cannot finish — the test above is
    // that rule — but absence applied to *every* provider leaves a gate with two lines of why, a
    // foot, and no way forward at all. That is worse than a dead control rather than a smaller
    // version of it: a dead button at least tells the player where to press, so a mute gate reads as
    // the app having failed to load.
    //
    // Only the desktop build can reach it, and only without the credential in its environment — but
    // that is the build the dev loop runs, so it is reachable by whoever is most likely to be
    // deceived by it.
    @Test
    fun `should say the build cannot sign anybody in when it draws no provider at all`() {
        val screen = GateState.Idle.toGateUiState(emptySet())
        val message = screen.message

        assertEquals(emptyList(), screen.providers)
        assertEquals(GateTone.FAILED, message?.tone)
        assertEquals("There is no way to sign in here.", English.resolve(message!!.lead))
        assertEquals(
            "This build has no sign-in provider. Use the iPhone or Android build.",
            English.resolve(message.body),
        )
    }

    // Not a failure, so not red: a statement in the body colour, and nothing on the screen moves
    // while it is up. A spinner here would claim knowledge the app does not have.
    @Test
    fun `should state that it is waiting without calling it a failure`() {
        val message = GateState.Waiting.toGateUiState(BOTH).message

        assertEquals(GateTone.WAITING, message?.tone)
        assertEquals("Signing in.", English.resolve(message!!.lead))
        assertEquals("Waiting for the server to answer.", English.resolve(message.body))
    }

    // **The only place in the product that states the rule out loud**: the colony runs there, so
    // there is no offline start.
    @Test
    fun `should say why there is no offline start`() {
        val message = GateState.NoAnswer.toGateUiState(BOTH).message

        assertEquals(GateTone.FAILED, message?.tone)
        assertEquals("The server did not answer.", English.resolve(message!!.lead))
        assertTrue("no offline start" in English.resolve(message.body))
    }

    // The provider is named because the player pressed it; the alternative is named because it is
    // the next thing to try — and both are already on the screen.
    @Test
    fun `should name the provider that refused and the one to try instead`() {
        val message = GateState.Refused(AuthProvider.APPLE).toGateUiState(BOTH).message

        assertEquals("Apple did not sign you in.", English.resolve(message!!.lead))
        assertTrue("use Google" in English.resolve(message.body))
    }

    @Test
    fun `should name Apple as the alternative when Google refused`() {
        val message = GateState.Refused(AuthProvider.GOOGLE).toGateUiState(BOTH).message

        assertEquals("Google did not sign you in.", English.resolve(message!!.lead))
        assertTrue("use Apple" in English.resolve(message.body))
    }

    // **The alternative has to be a button that is on the screen.** Android draws Google alone, so
    // naming Apple there is an instruction to press something that is not drawn — the dead-control
    // rule in its quietest form, where the sentence works and the player cannot. Naming the same
    // provider again is honest: it *is* the thing to try.
    @Test
    fun `should name the same provider again when it is the only one drawn`() {
        val message = GateState.Refused(AuthProvider.GOOGLE)
            .toGateUiState(setOf(AuthProvider.GOOGLE))
            .message

        assertTrue("use Google" in English.resolve(message!!.body))
    }

    // **The refusal no provider had anything to do with.** A build older than the oldest the server
    // serves is turned away before its token is read, and every retry is turned away identically —
    // so naming the provider blames the one link in the chain that worked, and *try again* becomes
    // an instruction that cannot succeed.
    @Test
    fun `should tell a build the server has outgrown to update rather than blaming a provider`() {
        val message = GateState.Outdated.toGateUiState(BOTH).message

        assertEquals(GateTone.FAILED, message?.tone)
        assertEquals("This version of Oltre is too old.", English.resolve(message!!.lead))
        assertTrue("update the app" in English.resolve(message.body))
    }

    // **The same refusal the other way round, and the opposite instruction.** A build newer than the
    // server is a deploy that has not landed yet: nothing is wrong with the app, waiting is what
    // works, and telling this player to update would send them to a store holding nothing newer.
    @Test
    fun `should say the server has not caught up when this build is the newer one`() {
        val message = GateState.ServerBehind.toGateUiState(BOTH).message

        assertEquals(GateTone.FAILED, message?.tone)
        assertEquals("The server has not caught up.", English.resolve(message!!.lead))
        assertTrue("in a few minutes" in English.resolve(message.body))
    }

    // Under a minute prints one unit; over it prints two. The committed format one order of
    // magnitude down, and the only place in the game that writes seconds.
    @Test
    fun `should print the wait in the app's own duration format`() {
        assertEquals(
            "Ask again in 41s.",
            English.resolve(GateState.Throttled(41).toGateUiState(BOTH).message!!.body),
        )
        assertEquals(
            "Ask again in 4m 12s.",
            English.resolve(GateState.Throttled(252).toGateUiState(BOTH).message!!.body),
        )
    }

    // **Re-enabling the button silently is what this string exists instead of.** A control that
    // changes meaning while nobody is looking is the one thing the no-timers rule is protecting.
    @Test
    fun `should say the window has passed rather than going quiet`() {
        assertEquals(
            "You can ask again now.",
            English.resolve(GateState.Throttled(0).toGateUiState(BOTH).message!!.body),
        )
    }

    // **The two strings the game does not own**, and the one assertion in this file that is about a
    // second language: Apple and Google publish these and mandate them, so a table that translated
    // them itself would be putting the game's voice on the one object the game does not own.
    @Test
    fun `should use the platform's own wording in every language`() {
        val providers = GateState.Idle.toGateUiState(BOTH).providers

        assertEquals(listOf("Sign in with Apple", "Sign in with Google"), providers.map { English.resolve(it.label) })
        assertEquals(listOf("Accedi con Apple", "Accedi con Google"), providers.map { Italian.resolve(it.label) })
    }

    private companion object {

        val BOTH = setOf(AuthProvider.APPLE, AuthProvider.GOOGLE)
    }
}
