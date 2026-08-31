package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.client.save.data.Preferences
import dev.fardavide.oltre.client.save.data.PreferencesStore
import dev.fardavide.oltre.client.changelog.presentation.EnglishChangelog
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.PlayerProfile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

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

    // **The server read the request and said no**, which on this route almost always means a token
    // that has just expired — and the gate is the honest answer to that, not a red line on a sheet
    // asking again with a credential that will fail again. Nothing was deleted, and the app says so
    // by being somewhere the player can fix it.
    @Test
    fun `a deletion the server refuses goes back to the gate`() {
        val server = FakeOltreApi()
        app(saved = colony(), preferences = signedInWithApple(), api = server) {
            openTheSettings()
            openTheAccountDeletion()
            // **After the launch, not before**, or the app never gets past the gate to have a sheet
            // to press: a credential the server rejects on the opening sync is a different test, two
            // files up. What is under this one is the *deletion* being refused.
            server.error = ApiError.Unauthenticated
            pressTheDeleteButton()
            pressTheDeleteButton()

            waitUntilItReads("The galaxy is shared")
        }
    }

    // **A database that blinked is not a player signing out.** The arm above catches the whole
    // `ApiError` taxonomy on the argument that *"a token that has just expired is the case this is
    // really about"* — and that case does not reach it any more: the call is wrapped in
    // `sessions.renewing`, which renews and retries a `SessionExpired` and answers `Unauthenticated`
    // only when there is genuinely nothing left. What is left to arrive here is a server that failed,
    // and signing the player out of an account that is still there is the wrong answer twice over:
    // nothing was deleted, and the tap that asked is now three faces away.
    //
    // The sheet already has a face for a delete that did not land, and it is the one this uses.
    @Test
    fun `a deletion the server could not carry out leaves the player where they are`() {
        val server = FakeOltreApi()
        app(saved = colony(), preferences = signedInWithApple(), api = server) {
            openTheSettings()
            openTheAccountDeletion()
            server.error = ApiError.Internal("the players table blinked")
            pressTheDeleteButton()
            pressTheDeleteButton()

            waitUntilItReads("This cannot be held.")
            assertDeletionsAsked(0)
            assertDoesNotRead("The galaxy is shared")
        }
    }

    // **Offline with a token that has run out is still offline**, and this is the arm that says so on
    // the one route where getting it wrong would be worst: a deletion answered by signing the player
    // out would delete a *session* rather than an account, which is the opposite of what the tap
    // asked for — and it would look, from the outside, exactly like the deletion having worked.
    //
    // The credential is stale and nothing is answering, so the renewal cannot happen. The account is
    // exactly where it was; the sheet says so.
    @Test
    fun `deleting with a stale token and no network refuses rather than signing out`() {
        val server = FakeOltreApi().apply {
            session = session.copy(accessExpiresAt = TEST_NOW - 1.hours)
            offline = true
        }
        app(saved = colony(), preferences = signedInWithApple(), api = server) {
            openTheSettings()
            openTheAccountDeletion()
            pressTheDeleteButton()
            pressTheDeleteButton()

            waitUntilItReads("This cannot be held.")
            assertDeletionsAsked(0)
            assertDoesNotRead("The galaxy is shared")
        }
    }

    // **One account and one name**, which `accountSection` was already fixed for and these two faces
    // were not: both of them named the commander out of the catalogue's default rather than off the
    // account, so the one flow in this app that cannot be undone was addressed to somebody the player
    // is not.
    @Test
    fun `both faces of the deletion name the commander the strip names`() {
        app(saved = colony(), preferences = signedInWithApple(), api = serverHolding("Ada Lovelace")) {
            waitUntilItReads("Ada Lovelace")
            openTheSettings()
            openTheAccountDeletion()

            assertReads("Ada Lovelace ·")
            pressTheDeleteButton()

            assertReads("Delete Ada Lovelace?")
        }
    }

    // **The account is gone and so is everything the shell held about it.** The success arm cleared
    // eight things and not the profile, so the next account signed into on the same process wore the
    // deleted one's identity — and the first tap would have written that name onto a fresh `players`
    // row. The second read is refused deliberately: what the strip draws then is whatever the shell
    // still remembered, which has to be nothing.
    @Test
    fun `the deleted account's name does not follow the next sign-in`() {
        val server = serverHolding("Ada Lovelace")
        app(saved = colony(), preferences = signedInWithApple(), api = server) {
            waitUntilItReads("Ada Lovelace")
            openTheSettings()
            openTheAccountDeletion()
            pressTheDeleteButton()
            pressTheDeleteButton()
            waitUntilItReads("The galaxy is shared")

            server.profileError = ApiError.Internal("the profile route fell over")
            pressProvider(AuthProvider.APPLE)

            waitUntilItReads("Metal Mine")
            assertThePlayerStripReads("Dead Reckoning")
        }
    }

    // **The one call in this app whose late answer could delete somebody else's colony.** It is
    // launched on the composition root's scope, which outlives the session that made it — so a
    // deletion still out when the session ends comes back to a process that may have signed a
    // different player in, and its success arm signs *them* out and clears the save and the queue
    // off the disk. Nothing in the answer says which account it was about.
    //
    // The sequence is not exotic: a deletion that lands and loses its response leaves a token naming
    // a player who is gone, which is exactly what the next sync is refused for — so ending the
    // session under an outstanding deletion is the *ordinary* way this route fails.
    @Test
    fun `a deletion that answers after its session ended does not clear the next player's colony`() {
        val server = serverHolding("Ada Lovelace")
        app(saved = colony(), preferences = signedInWithApple(), api = server) {
            openTheSettings()
            openTheAccountDeletion()
            holdTheDeletions()
            pressTheDeleteButton()
            pressTheDeleteButton()
            dismissTheSettings()

            server.error = ApiError.Unauthenticated
            tapTheActionOn(BuildingType.METAL_MINE)
            waitUntilItReads("The galaxy is shared")

            server.error = null
            pressProvider(AuthProvider.APPLE)
            waitUntilItReads("Metal Mine")

            letTheDeletionsLand()

            assertReads("Metal Mine")
            assertDoesNotRead("The galaxy is shared")
        }
    }

    // A server that already holds a named account, which is what a commander who named themselves on
    // another device looks like on this one.
    private fun serverHolding(name: String): FakeOltreApi {
        val snapshot = colony()
        return FakeOltreApi().apply {
            colony = snapshot
            founds = snapshot
            replays = true
            profile = PlayerProfile(name = CommanderName(name), mark = null)
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
