package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.client.save.data.Preferences
import dev.fardavide.oltre.client.save.data.PreferencesStore
import dev.fardavide.oltre.client.changelog.presentation.EnglishChangelog
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.protocol.AuthProvider
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

// **App Review guideline 5.1.1(v), driven end to end.** *"If your app supports account creation, you
// must also offer account deletion within the app"* — a hard App Store gate, not a TestFlight one,
// and one a screenshot cannot check: what has to be true is that the row is reachable, that crossing
// two faces asks the server, and that what comes back is the gate.
//
// **The second face is what this file is most about.** A deletion nobody can stumble into is the
// whole design: the row is body-weight and muted, the face it opens is all reading, and only the last
// one carries a filled red button.
class DeleteAccountAppBehaviourTest {

    @Test
    fun `the account section is drawn for a device that is signed in`() {
        app(saved = colony(), preferences = signedInWithApple()) {
            openTheSettings()

            assertReads("Signed in with Apple")
            assertReads("Delete account")
            assertReads("The colony goes with it.")
        }
    }

    // **No account section on a device with nothing remembered**, which is the honest answer rather
    // than an empty row: there is nothing to administer.
    @Test
    fun `a device that does not know its provider draws no account section`() {
        app(saved = colony()) {
            openTheSettings()

            assertDoesNotRead("Delete account")
        }
    }

    // The face that arrives is all reading and no consequence: four rows of what exists in the
    // colony's own numbers, then the fact the numbers cannot teach.
    @Test
    fun `the row opens a face that states what the account holds`() {
        app(saved = colony(), preferences = signedInWithApple()) {
            openTheSettings()
            openTheAccountDeletion()

            assertReads("The account goes")
            assertReads("It starts a new, empty colony.")
            // Nothing has been asked of the server by reading.
            assertDeletionsAsked(0)
        }
    }

    // **`Keep it` is first in the row and dismissal is a no.** The last step is the only face that
    // can do anything, and backing out of it costs nothing.
    @Test
    fun `keeping the account on the last step asks the server nothing`() {
        app(saved = colony(), preferences = signedInWithApple()) {
            openTheSettings()
            openTheAccountDeletion()
            pressTheDeleteButton()
            keepTheAccount()

            assertDeletionsAsked(0)
            assertReads("The account goes")
        }
    }

    // **Two faces to cross, and then it happens.** What comes back is the gate, because the account
    // that held the colony is gone and there is nothing left to open.
    @Test
    fun `crossing both faces deletes the account and goes back to the gate`() {
        app(saved = colony(), preferences = signedInWithApple()) {
            openTheSettings()
            openTheAccountDeletion()
            pressTheDeleteButton()
            pressTheDeleteButton()

            waitUntilItReads("The galaxy is shared")
            assertDeletionsAsked(1)
            assertDoesNotRead("Metal Mine")
        }
    }

    // **Deleting an account needs the network, so it refuses exactly as a dispatch does.** Nothing in
    // the brief said so; the design drew it and it is right — the account is removed on the server,
    // and the server has to answer.
    @Test
    fun `deleting with no network refuses and says which fact stops it`() {
        app(
            saved = colony(),
            preferences = signedInWithApple(),
            api = FakeOltreApi().apply { offline = true },
        ) {
            openTheSettings()
            openTheAccountDeletion()
            pressTheDeleteButton()
            pressTheDeleteButton()

            waitUntilItReads("This cannot be held.")
            assertReads("the server has to answer")
            assertDeletionsAsked(0)
        }
    }

    private fun signedInWithApple(): PreferencesStore {
        val store = PreferencesStore(InMemorySaveFile())
        runBlocking {
            store.save(
                Preferences(
                    galaxyLanding = null,
                    // Already read, for `changelogAlreadyRead`'s reason: a sheet raised over the app
                    // would be a scrim between this test and every control it taps.
                    lastSeenVersion = EnglishChangelog.releases.first().version.printed,
                    provider = AuthProvider.APPLE.name,
                    lastReachedAt = null,
                ),
            )
        }
        return store
    }

    private fun colony(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        debugUsed = false,
        state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds())),
    )
}
