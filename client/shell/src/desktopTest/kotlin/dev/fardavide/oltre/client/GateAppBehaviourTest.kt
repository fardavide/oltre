package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.auth.data.ProviderSignIn
import dev.fardavide.oltre.client.auth.data.SignInAttempt
import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.SignInNonce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

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

    // **And once the wait has passed the button works again**, which is the other half and the one a
    // player actually experiences. `retryAfterSeconds = 0` is the window already over — the server's
    // own way of saying *ask again now* — so the second tap has to reach it rather than be answered
    // by a number that has expired. A gate that latched on the first refusal would be a control that
    // stops working and never says why.
    @Test
    fun `a wait that has already passed does not hold the second tap back`() {
        val server = FakeOltreApi().apply { error = ApiError.TooManyRequests(retryAfterSeconds = 0) }
        app(saved = null, signedIn = false, api = server) {
            pressProvider(AuthProvider.APPLE)
            assertReads("You can ask again now.")

            pressProvider(AuthProvider.APPLE)

            assertEquals(2, server.signIns().size)
        }
    }

    // **A credential the server rejects mid-session ends where one rejected at launch does.** The
    // path is different — this one arrives through a *tap* rather than through the opening sync, so
    // it is `ActOutcome.Failed` rather than `SyncOutcome.Failed` — and the honest answer is the same
    // screen. What it must not be is a tap that queues, looks held, and is never sent.
    @Test
    fun `a tap the server rejects the credential for returns to the gate`() {
        val server = FakeOltreApi().apply { colony = this@GateAppBehaviourTest.colony() }
        app(saved = colony(), api = server) {
            waitUntilItReads("Metal Mine")

            server.error = ApiError.Unauthenticated
            tapTheActionOn(BuildingType.METAL_MINE)

            waitUntilItReads("The galaxy is shared")
        }
    }

    // **A first launch with no signal cannot enter the game**, and the honest form of that is the
    // gate saying so rather than an empty colony nobody can trust.
    @Test
    fun `a signed-in device with no colony and no network stays on the gate`() {
        app(saved = null, api = FakeOltreApi().apply { offline = true }) {
            // **Waited for rather than asserted immediately**, because this is the one screen in the
            // app that is genuinely still deciding when the launch goes idle: a sync with no signal
            // is three attempts over four seconds, and until the third the honest thing on screen is
            // *"Signing in."*
            waitUntilItReads("The server did not answer.")
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

    // **Every existing TestFlight tester takes this path on their first launch of this release**, and
    // it is the reason it is worth its own test rather than being an edge of the one below: they have
    // a colony on disk from a build that had no accounts, and no session file, because until this
    // release there was nothing to have one of.
    //
    // The colony must not open. Nothing they tap could reach a server that has never heard of them,
    // so a screen full of live-looking controls would be the dead-control rule's worst case handed to
    // the only person testing the build. The gate is the screen that says what is wrong and holds the
    // one control that fixes it.
    //
    // **The save stays on disk**, which is the whole of what makes this safe: signing in founds a new
    // colony on the server, and the slice that lands the upload still has the old one to send.
    @Test
    fun `a save from before there were accounts opens on the gate rather than on itself`() {
        app(saved = colony(), signedIn = false) {
            assertReads("The galaxy is shared")
            assertDoesNotRead("Metal Mine")
        }
    }

    // **The credential can die while a save is on disk, and that is the one way back to this screen
    // after the first time.** A refresh token expires, is refused, or is revoked on another device;
    // the app opens on the colony it last agreed to — which is right — and then the server says it
    // has never heard of this session.
    //
    // What it must not do is stay there. A colony on screen whose every tap is dropped is the worst
    // failure this product can have: the controls all look operable, nothing says otherwise, and the
    // player concludes the game is broken in a way they cannot describe. The gate is a screen that
    // *says* what is wrong and has the one control that fixes it.
    @Test
    fun `a session the server no longer honours returns the player to the gate`() {
        app(
            saved = colony(),
            api = FakeOltreApi().apply {
                colony = this@GateAppBehaviourTest.colony()
                error = ApiError.Unauthenticated
            },
        ) {
            waitUntilItReads("The galaxy is shared")
            assertDoesNotRead("Metal Mine")
        }
    }

    // **And the way back is the ordinary one**, which is the other half: the gate a dead credential
    // lands on is the same gate, with the same two buttons, and pressing one opens the colony the
    // server has been holding all along. A screen that could only be reached and not left would be a
    // more elaborate way of stranding somebody.
    @Test
    fun `signing in again after an expired session opens the colony`() {
        val server = FakeOltreApi().apply {
            colony = this@GateAppBehaviourTest.colony()
            error = ApiError.Unauthenticated
        }
        app(saved = colony(), api = server) {
            waitUntilItReads("The galaxy is shared")

            server.error = null
            pressProvider(AuthProvider.APPLE)

            assertReads("Metal Mine")
        }
    }

    // **And a platform with none says so**, which is the same rule taken to the end rather than a
    // separate one. Drawing no button is right for a provider that cannot finish; drawing no button
    // *at all* leaves a screen the player cannot leave, so the absence needs a sentence or it reads
    // as a failure to load.
    //
    // The desktop build reaches this whenever the Google credential is not in its environment —
    // which is to say, on a dev loop that forgot to source it. That is the case worth a test: the
    // person most likely to see a mute gate is the one who would assume the app was broken.
    @Test
    fun `a platform with no provider at all says why rather than drawing nothing`() {
        app(saved = null, signedIn = false, providers = emptySet()) {
            assertProviderNotOffered(AuthProvider.APPLE)
            assertProviderNotOffered(AuthProvider.GOOGLE)
            assertReads("There is no way to sign in here.")
        }
    }

    // **Offline is not signed out**, and telling the two apart is the whole reason `ApiResult`
    // splits `Refused` from `Unreachable`. A player on a train whose hour-long access token ran out
    // has a ninety-day refresh token that will work perfectly well when the signal comes back — so
    // a renewal nobody answered must leave it exactly where it is.
    //
    // The failure this guards is the expensive one: signing somebody out because they went through a
    // tunnel. It costs them the session, the queue's promise that the tap happens when the network
    // returns, and it hands them a sign-in screen at the one moment they cannot use it.
    @Test
    fun `no signal and a stale access token holds the tap rather than signing the player out`() {
        val server = FakeOltreApi().apply {
            colony = this@GateAppBehaviourTest.colony()
            session = session.copy(accessExpiresAt = TEST_NOW - 1.hours)
            offline = true
        }
        app(saved = colony(), api = server) {
            tapTheActionOn(BuildingType.METAL_MINE)

            waitUntilItReads("Upgrade held.")
            assertOfflineLine(showing = true)
            assertDoesNotRead("The galaxy is shared")
        }
    }

    // **A save cannot rule out a colony deleted somewhere else**, so *the server has never heard of
    // you* is answered by asking it to found one rather than by a screen. `found` is idempotent, so
    // the fallback costs a round trip and can never mint a second galaxy — which is the property
    // that makes it safe to take without asking the player anything.
    @Test
    fun `a device whose colony the server has never heard of founds one`() {
        val server = FakeOltreApi().apply {
            colony = null
            founds = this@GateAppBehaviourTest.colony()
        }
        app(saved = colony(), api = server) {
            waitUntilItReads("Metal Mine")

            assertEquals(1, server.foundings().size)
        }
    }

    // **The server read the sign-in and said no**, which is a different path from the platform
    // refusing: the token was produced, it travelled, and the far end rejected it. One sentence for
    // both, because the player's next move is the same either way.
    @Test
    fun `a sign-in the server refuses leaves the gate up`() {
        val server = FakeOltreApi().apply { error = ApiError.Unauthenticated }
        app(saved = null, signedIn = false, api = server) {
            pressProvider(AuthProvider.GOOGLE)

            assertReads("Google did not sign you in.")
            assertDoesNotRead("Metal Mine")
        }
    }

    // And a server nobody reached, which is the other half: the platform vouched, and then the
    // request never arrived. No signal and a service that is down are one screen.
    @Test
    fun `a sign-in that reaches nobody says the server did not answer`() {
        val server = FakeOltreApi().apply { offline = true }
        app(saved = null, signedIn = false, api = server) {
            pressProvider(AuthProvider.GOOGLE)

            assertReads("The server did not answer.")
        }
    }

    private fun colony(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        debugUsed = false,
        state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds())),
    )
}
