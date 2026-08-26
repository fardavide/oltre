package dev.fardavide.oltre.client

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.fardavide.oltre.client.auth.data.ProviderSignIn
import dev.fardavide.oltre.client.auth.data.SignInAttempt
import dev.fardavide.oltre.client.auth.ui.GateTestTags
import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.SignInNonce
import kotlin.test.Test
import kotlin.test.assertEquals

// **The screen that gates the whole game, driven end to end.** Everything here is about the one
// thing no unit test can reach: that pressing a button on a real gate produces a colony, and that
// every way it can fail produces a sentence rather than a control that did nothing.
//
// The risk this file is written against is stated in `#113`: *a broken sign-in is not a degraded
// app, it is an app that cannot be opened.* What it can check is the whole flow above the platform;
// what it cannot is the platform's own half, which is a device's job — see the pull request.
class GateAppBehaviourTest {

    @Test
    fun `a device nobody has signed in on opens on the gate`() {
        app(saved = null, signedIn = false) {
            assertReads("The galaxy is shared")
            // The colony is not behind it — there is no colony yet, which is the whole reason the
            // gate carries no strip and no tab bar.
            assertDoesNotRead("Metal Mine")
        }
    }

    @Test
    fun `a device that has signed in opens on the colony`() {
        app(saved = null) {
            assertReads("Metal Mine")
            assertDoesNotRead("The galaxy is shared")
        }
    }

    // **The whole flow in one test**: the platform vouches, the server mints a session, the colony is
    // founded, and the game opens. Every one of those four can fail and each has a test of its own
    // below; this is the one that says they compose.
    @Test
    fun `pressing a provider signs in and opens the colony`() {
        app(saved = null, signedIn = false) {
            pressProvider(AuthProvider.APPLE)

            assertReads("Metal Mine")
            assertEquals(1, server.signIns().size)
            assertEquals(AuthProvider.APPLE, server.signIns().single().provider)
        }
    }

    // The nonce is what stops a stolen token being replayed, and the one thing about it that can be
    // silently wrong is that it never leaves. Asserted here rather than trusted, because a client
    // that dropped it would work perfectly against a server that did not check.
    @Test
    fun `the nonce the platform bound the token to travels with it`() {
        app(saved = null, signedIn = false) {
            pressProvider(AuthProvider.GOOGLE)

            assertEquals(SignInNonce("fake-nonce"), server.signIns().single().nonce)
            assertEquals(IdToken("fake.id.token"), server.signIns().single().idToken)
        }
    }

    // **One sentence for a refusal and for a cancellation**, because the platforms frequently cannot
    // tell them apart and an accusation is worse than a fact.
    @Test
    fun `a provider that refuses leaves the gate up and names the other one`() {
        app(
            saved = null,
            signedIn = false,
            signIn = ProviderSignIn { SignInAttempt.Refused },
        ) {
            pressProvider(AuthProvider.APPLE)

            assertReads("Apple did not sign you in.")
            assertReads("use Google")
            assertDoesNotRead("Metal Mine")
        }
    }

    // No signal and a service that is down are the same screen, because they are the same
    // instruction: wait.
    @Test
    fun `a platform that cannot be reached says to try again with signal`() {
        app(
            saved = null,
            signedIn = false,
            signIn = ProviderSignIn { SignInAttempt.Unreachable },
        ) {
            pressProvider(AuthProvider.APPLE)

            assertReads("The server did not answer.")
            assertReads("no offline start")
        }
    }

    // **The server sends a number and the screen prints it**, in the app's own duration format. The
    // digit does not tick: nothing on this screen moves on its own.
    @Test
    fun `a throttled sign-in prints the wait the server sent`() {
        app(
            saved = null,
            signedIn = false,
            api = FakeOltreApi().apply { error = ApiError.TooManyRequests(retryAfterSeconds = 41) },
        ) {
            pressProvider(AuthProvider.APPLE)

            assertReads("Asked too often.")
            assertReads("Ask again in 41s.")
        }
    }

    // **The gate cannot be answered by waiting when the wait has not passed**, and the check is that
    // the second tap does not reach the server at all — which is what makes the number honest rather
    // than decorative.
    @Test
    fun `tapping again inside the window does not ask again`() {
        app(
            saved = null,
            signedIn = false,
            api = FakeOltreApi().apply { error = ApiError.TooManyRequests(retryAfterSeconds = 41) },
        ) {
            pressProvider(AuthProvider.APPLE)
            pressProvider(AuthProvider.APPLE)

            assertEquals(1, server.signIns().size)
        }
    }

    // **A first launch with no signal cannot enter the game**, and the honest form of that is the
    // gate saying so rather than an empty colony nobody can trust.
    @Test
    fun `a signed-in device with no colony and no network stays on the gate`() {
        app(saved = null, api = FakeOltreApi().apply { offline = true }) {
            assertReads("The server did not answer.")
            assertDoesNotRead("Metal Mine")
        }
    }

    // **A device with a save opens on it whatever the network is doing.** That is what a save is for
    // now: it is not the truth any more, it is the last truth this device was told.
    @Test
    fun `a signed-in device with a colony opens on it with no network at all`() {
        app(
            saved = GameSnapshot(
                lastUpdatedAt = TEST_NOW,
                debugUsed = false,
                state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds())),
            ),
            api = FakeOltreApi().apply { offline = true },
        ) {
            assertReads("Metal Mine")
            assertDoesNotRead("The galaxy is shared")
        }
    }

    // **Only the providers the platform can complete are drawn.** A button that opens a browser
    // which never comes back is the worst control a gate has available — see `signInProviders`, and
    // see the pull request for which platforms answer what.
    @Test
    fun `a platform with one provider draws one button`() {
        app(saved = null, signedIn = false, providers = setOf(AuthProvider.GOOGLE)) {
            assertProviderOffered(AuthProvider.GOOGLE)
            assertProviderNotOffered(AuthProvider.APPLE)
        }
    }
}

private fun AppRobot.pressProvider(provider: AuthProvider) = apply {
    test.onNodeWithTag(GateTestTags.provider(provider)).performClick()
    test.waitForIdle()
}

private fun AppRobot.assertProviderOffered(provider: AuthProvider) = apply {
    test.onNodeWithTag(GateTestTags.provider(provider)).assertIsDisplayed()
}

private fun AppRobot.assertProviderNotOffered(provider: AuthProvider) = apply {
    test.onNodeWithTag(GateTestTags.provider(provider)).assertDoesNotExist()
}
