package dev.fardavide.oltre.client.auth.data

import dev.fardavide.oltre.protocol.AuthProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// **The one thing about the desktop flow a machine here can check**, and it is the thing that decides
// whether the game can be opened at all: which buttons the gate draws.
//
// Everything below it — the loopback server, the browser, the token exchange — needs a browser and a
// Google account, which is a person's job. What is *not* a person's job is noticing that a build with
// no credential draws a button that cannot work, which is the failure this file exists against.
class DesktopSignInTest {

    // **No secret, no button.** Google calls this an *installed application* and its token endpoint
    // wants the client secret; the secret is not in the repository, so a machine without it in the
    // environment cannot complete the flow — and a gate that offered it anyway would open a browser
    // that never comes back.
    //
    // The environment is read at every call rather than captured once, so this asserts what a real
    // launch would find on a machine that has not sourced `identity.env`. CI is such a machine, which
    // is what makes this assertion meaningful there rather than incidental.
    @Test
    fun `should draw no provider on a machine with no credential`() {
        if (System.getenv(DESKTOP_SECRET) != null) return

        assertEquals(emptySet(), signInProviders())
    }

    // **Apple is absent whatever the environment holds**, and it is worth an assertion rather than a
    // comment: away from an Apple platform it is a browser flow whose Return URL Apple insists is
    // `https`, and the registered one is a server endpoint that does not exist. The day that endpoint
    // lands, this test is what has to be changed on purpose.
    @Test
    fun `should never draw Apple away from an Apple platform`() {
        assertEquals(false, AuthProvider.APPLE in signInProviders())
    }

    // **Nothing may throw past the gate**, which is the whole contract `ProviderSignIn` states: this
    // sits in front of the screen that gates the game, so an exception escaping it is not a degraded
    // app but an app that cannot be opened. Asking for Apple on desktop is the cheapest way to reach
    // the arm that answers rather than works.
    @Test
    fun `should answer rather than throw when a provider cannot be completed`() = runTest {
        assertEquals(SignInAttempt.Unreachable, defaultProviderSignIn().signIn(AuthProvider.APPLE))
    }
}

private const val DESKTOP_SECRET = "OLTRE_GOOGLE_DESKTOP_CLIENT_SECRET"
