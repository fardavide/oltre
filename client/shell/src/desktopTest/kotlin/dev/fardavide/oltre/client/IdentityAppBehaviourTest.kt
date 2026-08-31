package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.changelog.presentation.EnglishChangelog
import dev.fardavide.oltre.client.net.data.FakeOltreApi
import dev.fardavide.oltre.client.save.data.Preferences
import dev.fardavide.oltre.client.save.data.PreferencesStore
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

// **A name you chose, driven from the strip to the server and back.** The two faces have their own
// behaviour tests in `:client:player:ui`, against a scene that owns its draft and answers its own
// callbacks — what is under test here is everything those cannot see: that the cluster opens the
// face at all, that a tap leaves the phone, that what comes back is what is drawn, and that with no
// signal none of it happens and the face says so.
//
// **A rename is not a `ClientVerb` and this file is what proves the consequence**: nothing is queued
// and nothing is held, so the assertion after every offline tap is that the server was asked nothing
// at all.
class IdentityAppBehaviourTest {

    @Test
    fun `the player cluster opens the identity face`() {
        app(saved = colony()) {
            openTheProfile()

            assertIdentityShowing()
        }
    }

    // **The account, not the colony.** A profile the server holds is drawn on the strip behind every
    // destination — which is the read this slice adds to the launch, and the only thing on the frame
    // that does not come off the save.
    @Test
    fun `a name the account already holds is what the strip wears`() {
        app(saved = colony(), api = serverHolding(named("Ada Lovelace"))) {
            waitUntilItReads("Ada Lovelace")

            assertThePlayerStripReads("Ada Lovelace")
        }
    }

    // **What every account founded before this slice reads**, and the whole of what a null means: not
    // *this build does not say*, but *has not chosen*. Nothing on screen changes for a player who
    // never opens the editor.
    @Test
    fun `an account that has chosen nothing is still called Dead Reckoning`() {
        app(saved = colony()) {
            assertThePlayerStripReads("Dead Reckoning")
        }
    }

    // **Every tap commits**, like every other control in this app — there is no confirm on a picture.
    @Test
    fun `choosing a mark writes it to the server`() {
        app(saved = colony()) {
            openTheProfile()
            chooseTheMark(MarkPreset.SEXTANT)

            assertProfilesWritten(PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.SEXTANT)))
        }
    }

    // The other half of the same tap: what comes back is what is drawn, so the grid the player is
    // still looking at is lit on the cell they pressed — and the line under it says its name.
    @Test
    fun `a mark that landed is the one the face goes on showing`() {
        app(saved = colony()) {
            openTheProfile()
            chooseTheMark(MarkPreset.APHELION)

            assertReads("Aphelion")
        }
    }

    // **The name has a button, and the mark does not.** A tap is a tap; a name has no keystroke that
    // means *done*, so this is the one control on the face that does not commit as it is touched.
    @Test
    fun `saving a name writes it to the server`() {
        app(saved = colony()) {
            openTheProfile()
            typeAName("Ada")
            saveTheName()

            assertProfilesWritten(PlayerProfile(name = CommanderName("Ada"), mark = null))
        }
    }

    @Test
    fun `a name that landed is what the strip wears`() {
        app(saved = colony()) {
            openTheProfile()
            typeAName("Ada")
            saveTheName()

            assertThePlayerStripReads("Ada")
        }
    }

    // **Present only while there is something to save, and absent rather than disabled.** Both halves
    // are here because the second is what a write is worth: after the answer lands, the draft and the
    // committed name are one string and the button has nothing left to do.
    @Test
    fun `the save button is absent before a keystroke and gone again after a write`() {
        app(saved = colony()) {
            openTheProfile()
            assertSaveOffered(false)

            typeAName("Ada")
            assertSaveOffered(true)

            saveTheName()
            assertSaveOffered(false)
        }
    }

    // **Clearing is the way out of a name somebody regrets**, and an empty field is a preview of
    // `Dead Reckoning` rather than an error — so what goes up the wire is a null and what comes back
    // is the default the strip draws for one. The only route to a null this app has, and it is the
    // reason the write replaces the profile whole rather than patching the half that moved.
    @Test
    fun `an emptied field puts the name back to nothing`() {
        app(saved = colony(), api = serverHolding(named("Ada"))) {
            waitUntilItReads("Ada")
            openTheProfile()
            clearTheName()
            saveTheName()

            assertProfilesWritten(PlayerProfile(name = null, mark = null))
            assertThePlayerStripReads("Dead Reckoning")
        }
    }

    // ── The composer, which is the first face in this app another face opens ─────────────────

    @Test
    fun `the row under the grid opens the composer`() {
        app(saved = colony()) {
            openTheProfile()
            openTheComposer()

            assertComposerShowing()
            assertIdentityShowing(showing = false)
        }
    }

    // **A chip is the whole mark with one slot swapped**, so what leaves the phone is a composition
    // and not a part — and it opens on `Threshold`'s own tuple, which is the one preset the grammar
    // can make.
    @Test
    fun `choosing a body writes the whole mark`() {
        app(saved = colony()) {
            openTheProfile()
            openTheComposer()
            chooseTheBody(MarkBody.ORBIT)

            assertProfilesWritten(
                PlayerProfile(
                    name = null,
                    mark = PlayerMark.Composed(
                        body = MarkBody.ORBIT,
                        path = MarkPath.RISING,
                        terminus = MarkTerminus.DOT,
                    ),
                ),
            )
        }
    }

    // **A terminus is the end of a path, and `PlayerMark.Composed` throws rather than allowing the
    // pair.** So the tap that takes the path away has to take the terminus with it — and getting this
    // wrong is not a wrong drawing, it is the app going down under the finger that tapped it.
    @Test
    fun `taking the path away takes the terminus with it`() {
        app(saved = colony()) {
            openTheProfile()
            openTheComposer()
            chooseThePath(MarkPath.NONE)

            assertProfilesWritten(
                PlayerProfile(
                    name = null,
                    mark = PlayerMark.Composed(
                        body = MarkBody.LIMB,
                        path = MarkPath.NONE,
                        terminus = MarkTerminus.NONE,
                    ),
                ),
            )
            assertComposerShowing()
        }
    }

    // **The tap sequence that took the app down.** The chips are drawn from what the last answer left
    // behind, so the terminus ladder is still on screen for the whole of a `withPath(NONE)` write's
    // round trip — and the edit is applied *inside* the lock, against the row that write produced.
    // So a terminus tapped in that window meets a mark with no path, which `PlayerMark.Composed`
    // refuses to be built as.
    //
    // **Nothing on the face may be able to assemble one.** The edit answers the mark unchanged rather
    // than resurrecting the path the player just cleared, so what goes up is the same row a second
    // time — which `profile-sheet.md` §3 already blesses: *"a second identical write is the same
    // row."*
    @Test
    fun `a terminus tapped inside a pathless write's round trip builds no illegal mark`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null))
        app(saved = colony(), api = server) {
            openTheProfile()
            openTheComposer()

            holdTheProfileWrites()
            chooseThePath(MarkPath.NONE)
            chooseTheTerminus(MarkTerminus.RING)
            letTheProfileWritesLand()

            val pathless = PlayerProfile(
                name = null,
                mark = PlayerMark.Composed(
                    body = MarkBody.LIMB,
                    path = MarkPath.NONE,
                    terminus = MarkTerminus.NONE,
                ),
            )
            assertProfilesWritten(pathless, pathless)
            assertComposerShowing()
        }
    }

    // **There is no way back from the composer and the way out is the frame** — so coming back means
    // dismissing and opening the strip again, and what has to be true then is that the grid knows
    // what was assembled. The line under it is what says so: a composed mark has no noun of its own,
    // so *"Your mark"* followed by the three parts is a sentence only a composition produces, and a
    // face that had fallen back to a preset would be naming one of the six instead.
    @Test
    fun `a composed mark is what the grid shows when the strip is opened again`() {
        app(saved = colony()) {
            openTheProfile()
            openTheComposer()
            chooseTheBody(MarkBody.ORBIT)
            dismissTheSettings()
            openTheProfile()

            assertIdentityShowing()
            assertReads("Your mark")
            assertReads("Orbit")
            assertDoesNotRead("Threshold")
        }
    }

    // ── With no signal ───────────────────────────────────────────────────────────────────────

    // **The sheet still opens**, which is the design's own call: *"a player who taps their own name
    // deserves to see what they would be choosing."* What changes is that the two controls go quiet
    // and the card above them says why.
    @Test
    fun `the face opens with no signal and says what it is waiting for`() {
        app(saved = colony(), api = FakeOltreApi().apply { offline = true }) {
            openTheProfile()

            waitUntilTheProfileIsHeld()
            assertIdentityShowing()
        }
    }

    // **A rename does not queue and there is no second outbox**: the tap is refused in the app's own
    // amber language, and what makes that a refusal rather than a silence is that the server was
    // asked nothing at all.
    @Test
    fun `a mark cannot be chosen with no signal and nothing is queued`() {
        app(saved = colony(), api = FakeOltreApi().apply { offline = true }) {
            openTheProfile()
            waitUntilTheProfileIsHeld()
            chooseTheMark(MarkPreset.SEXTANT)

            assertProfilesWritten()
            assertVerbsSent(0)
        }
    }

    // **The save button is absent offline because there is nothing to save**, which is the same rule
    // that takes it away with an unchanged draft: a control that could not act does not ship.
    @Test
    fun `there is nothing to save with no signal`() {
        app(saved = colony(), api = FakeOltreApi().apply { offline = true }) {
            openTheProfile()
            waitUntilTheProfileIsHeld()

            assertSaveOffered(false)
        }
    }

    // **A stale credential and no network is a train, on the launch as well as on the write.** The
    // renewal cannot happen, so the profile is never asked for either — and what has to be true is
    // that the app carries on showing the colony with the editor reachable and quiet, rather than
    // reading *nobody replied* as *signed out*. The tap here lands on a cell that has already gone
    // quiet, which is the point: there is nothing for it to send and nothing that pretends there is.
    @Test
    fun `a stale token and no network keeps the player where they are`() {
        val server = FakeOltreApi().apply {
            session = session.copy(accessExpiresAt = TEST_NOW - 1.hours)
            offline = true
        }
        app(saved = colony(), api = server) {
            openTheProfile()
            waitUntilTheProfileIsHeld()
            chooseTheMark(MarkPreset.SEXTANT)

            assertProfilesWritten()
            assertDoesNotRead("The galaxy is shared")
            assertIdentityShowing()
        }
    }

    // **The signal going while the face is open**, which is the case the two above cannot reach: the
    // grid is live because nothing has failed yet, so the tap really is made and really is answered.
    // What answers it is the amber card arriving — a tap that changed nothing and said nothing would
    // be the one failure this product treats as worse than a crash.
    @Test
    fun `a tap that meets a dead network is answered in amber rather than swallowed`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null))
        app(saved = colony(), api = server) {
            openTheProfile()
            assertProfileHeld(false)

            server.offline = true
            chooseTheMark(MarkPreset.SEXTANT)

            waitUntilTheProfileIsHeld()
            assertProfilesWritten()
        }
    }

    // **A renewal nobody answered is a train**, and this is the arm that says so on the write rather
    // than on the launch: the stored credential has run out, the network is gone, so there is no way
    // to ask and nothing was asked. Signing the player out here would answer *nobody replied* by
    // deleting the session, which is the opposite of what the tap meant.
    @Test
    fun `a renewal nobody answers refuses the tap rather than signing the player out`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null)).apply {
            session = session.copy(accessExpiresAt = TEST_NOW - 1.hours)
        }
        app(saved = colony(), api = server) {
            openTheProfile()
            assertProfileHeld(false)

            server.offline = true
            chooseTheMark(MarkPreset.SEXTANT)

            waitUntilTheProfileIsHeld()
            assertProfilesWritten()
            assertDoesNotRead("The galaxy is shared")
        }
    }

    // **The one refusal this route can meet from a finger.** The field bounds the name and
    // `CommanderName` refuses anything the field could not have produced, so what is left is a
    // credential that has gone — and the gate is the honest answer to that, exactly as it is
    // everywhere else in this app. The colony comes off the screen with it: the gate only draws when
    // there is no session, and a colony left behind one would be live-looking controls over nothing.
    @Test
    fun `a write the server refuses with a dead credential goes back to the gate`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null))
        app(saved = colony(), api = server) {
            openTheProfile()
            typeAName("Ada")
            server.error = ApiError.Unauthenticated
            saveTheName()

            waitUntilItReads("The galaxy is shared")
            assertIdentityShowing(showing = false)
        }
    }

    // ── A session that begins somewhere other than a launch ──────────────────────────────────

    // **The gate is a session beginning, and a session beginning is an account to read.** Until this
    // there was exactly one `api.profile` call in the app, inside the launch effect and behind a
    // credential the gate has not minted yet — so a player who signed in here wore `Dead Reckoning`
    // over an account called something else, for the whole of that process.
    @Test
    fun `signing in at the gate reads the account that was signed into`() {
        app(saved = null, api = serverHolding(named("Ada Lovelace")), signedIn = false) {
            pressProvider(AuthProvider.APPLE)

            waitUntilItReads("Ada Lovelace")
            assertThePlayerStripReads("Ada Lovelace")
        }
    }

    // **The half that makes the one above a data-loss bug rather than a cosmetic one.** `POST
    // /v1/profile` replaces the row whole — `Profile.kt` argues why it must — so the first mark
    // tapped after a gate sign-in sent `{name: null}` over a name the account already held and the
    // server wrote SQL NULL. Every device lost it and nothing said so.
    @Test
    fun `a mark chosen after a gate sign-in keeps the name the account already holds`() {
        app(saved = null, api = serverHolding(named("Ada Lovelace")), signedIn = false) {
            pressProvider(AuthProvider.APPLE)
            waitUntilItReads("Ada Lovelace")
            openTheProfile()
            chooseTheMark(MarkPreset.SEXTANT)

            assertProfilesWritten(
                PlayerProfile(
                    name = CommanderName("Ada Lovelace"),
                    mark = PlayerMark.Preset(MarkPreset.SEXTANT),
                ),
            )
        }
    }

    // **A launch that could not read the account has to ask again**, or the strip is wrong until the
    // process is killed. The tick loop is the only thing in this app that notices the network coming
    // back — it did so for the queue and for nothing else.
    @Test
    fun `a launch that could not read the account asks again when the network comes back`() {
        val server = serverHolding(named("Ada Lovelace")).apply { offline = true }
        app(saved = colony(), api = server) {
            assertThePlayerStripReads("Dead Reckoning")

            server.offline = false

            waitUntilItDoesNotRead("Dead Reckoning")
            assertThePlayerStripReads("Ada Lovelace")
        }
    }

    // **A profile nobody read is a profile nothing may write over.** The read is refused once and the
    // colony arrives normally — so the app is online, the face is reachable and the only thing
    // missing is the one fact a whole-row write is built out of. It wears the amber it already has
    // for a control that genuinely cannot act.
    @Test
    fun `an account that could not be read holds the face rather than writing over it`() {
        val server = serverHolding(named("Ada Lovelace")).apply {
            profileError = ApiError.Internal("the profile route fell over")
        }
        app(saved = colony(), api = server) {
            openTheProfile()

            assertProfileHeld(true)
            chooseTheMark(MarkPreset.SEXTANT)

            assertProfilesWritten()
        }
    }

    // ── What the server said no to ───────────────────────────────────────────────────────────

    // **A write the server refused did not land**, and the face must not go on looking as though it
    // might. Only `Unauthenticated` reached a screen; every other refusal set `reachable` back to
    // true — which is the flag that takes the amber card away — and then did nothing at all.
    @Test
    fun `a write refused for a reason nobody designed does not leave the face live`() {
        val server = serverHolding(named("Ada Lovelace"))
        app(saved = colony(), api = server) {
            waitUntilItReads("Ada Lovelace")
            openTheProfile()
            assertProfileHeld(false)

            server.profileError = ApiError.Internal("the profile route fell over")
            chooseTheMark(MarkPreset.SEXTANT)

            waitUntilTheProfileIsHeld()
            // Nothing was written, so nothing was lost: the name the account holds is still drawn.
            assertThePlayerStripReads("Ada Lovelace")
        }
    }

    // **A credential that has gone takes the sheet with it.** The `Unauthenticated` arm clears the
    // face and this one did not, so the sheet was still raised the next time a session existed — over
    // a colony the player had just signed back into and had asked for nothing.
    @Test
    fun `a credential that has gone takes the sheet with it`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null)).apply {
            // Expired on arrival, so every credential resolution goes through a renewal — which is
            // the one thing that can answer `Gone` while the app is up.
            session = session.copy(accessExpiresAt = TEST_NOW - 1.hours)
        }
        app(saved = colony(), api = server) {
            openTheProfile()
            assertIdentityShowing()

            server.error = ApiError.Unauthenticated
            chooseTheMark(MarkPreset.SEXTANT)
            waitUntilItReads("The galaxy is shared")

            server.error = null
            pressProvider(AuthProvider.APPLE)

            waitUntilItReads("Metal Mine")
            assertIdentityShowing(showing = false)
        }
    }

    // ── The way back out of held ─────────────────────────────────────────────────────────────

    // **A held face has to have an exit, and until this it had none.** A profile write queues
    // nothing, so `held.count` stays at zero and the tick loop's retry — the only thing in this app
    // that notices the network coming back — could never fire for it; and with the card up every
    // control on both faces is drawn as a press-less `Box`, so nothing a finger could reach was left
    // to heal it either. A player whose rename met a dead network sat in a face with no way out for
    // the rest of the process, network or no network.
    @Test
    fun `a face held by a dead network goes live again when the network comes back`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null))
        app(saved = colony(), api = server) {
            openTheProfile()
            assertProfileHeld(false)

            server.offline = true
            chooseTheMark(MarkPreset.SEXTANT)
            waitUntilTheProfileIsHeld()

            server.offline = false
            waitUntilTheProfileIsLive()

            // And the control the card was over is a control again.
            chooseTheMark(MarkPreset.SEXTANT)
            assertProfilesWritten(PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.SEXTANT)))
        }
    }

    // ── The token the phone thought was good ─────────────────────────────────────────────────
    //
    // `SessionKeeper.current()` decides on this device's own clock arithmetic with a one-minute
    // margin. A phone whose clock is behind the server's opens a window where that arithmetic says
    // *in date* and the server answers `SessionExpired` — which is the one error the player is never
    // meant to read, and which only `ColonySync.drain` knew how to answer.

    @Test
    fun `a write the server says is expired renews the session rather than reporting no network`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null))
        app(saved = colony(), api = server) {
            openTheProfile()
            assertProfileHeld(false)

            server.transientErrors += ApiError.SessionExpired
            chooseTheMark(MarkPreset.SEXTANT)

            // The write landed on the renewed credential, and nothing told the player the network
            // was out about a server that had just answered.
            assertProfilesWritten(PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.SEXTANT)))
            assertProfileHeld(false)
        }
    }

    // **Launched with no save, so the colony can only arrive *after* the account has been read** —
    // which is what makes this an assertion about the read rather than about the tick loop. The
    // minute's retry does eventually cover a dropped profile, and that is exactly why the assertion
    // is immediate: a name that takes a minute to appear is a name the player watched change.
    @Test
    fun `an account read the server says is expired renews the session rather than dropping the name`() {
        val server = serverHolding(named("Ada Lovelace"))
        server.transientErrors += ApiError.SessionExpired

        app(saved = null, api = server) {
            waitUntilItReads("Metal Mine")

            assertThePlayerStripReads("Ada Lovelace")
        }
    }

    // ── What a session ending has to take with it ────────────────────────────────────────────
    //
    // Five arms end a session and every one of them has to put down the account it was drawn for.
    // The name and the mark are the half that makes leaving them behind a data-loss bug rather than
    // a wrong drawing: `POST /v1/profile` replaces the row whole, so a mark tapped before the *next*
    // account's read lands posts the previous player's name over it.

    @Test
    fun `a colony refused for a dead credential forgets the name it was drawn under`() {
        val server = serverHolding(named("Ada Lovelace"))
        app(saved = colony(), api = server) {
            waitUntilItReads("Ada Lovelace")

            server.error = ApiError.Unauthenticated
            tapTheActionOn(BuildingType.METAL_MINE)
            waitUntilItReads("The galaxy is shared")

            // Somebody else signs in on the same phone, and this device cannot read who they are —
            // which is the only state in which the leak is visible rather than overwritten a
            // moment later.
            server.error = null
            server.profileError = ApiError.Internal("the profile route fell over")
            pressProvider(AuthProvider.APPLE)

            waitUntilItReads("Metal Mine")
            assertThePlayerStripReads("Dead Reckoning")
        }
    }

    @Test
    fun `a write refused for a dead credential forgets the name it was drawn under`() {
        val server = serverHolding(named("Ada Lovelace"))
        app(saved = colony(), api = server) {
            waitUntilItReads("Ada Lovelace")
            openTheProfile()

            server.error = ApiError.Unauthenticated
            chooseTheMark(MarkPreset.SEXTANT)
            waitUntilItReads("The galaxy is shared")

            server.error = null
            server.profileError = ApiError.Internal("the profile route fell over")
            pressProvider(AuthProvider.APPLE)

            waitUntilItReads("Metal Mine")
            assertThePlayerStripReads("Dead Reckoning")
        }
    }

    // ── Two writes in flight ─────────────────────────────────────────────────────────────────

    // **The whole row goes up on every tap, so two taps racing is one of them undone.** The mark
    // commits as it is touched and the name has a button, which is the frame's own split — and it is
    // also the one pair of controls in this app that can be pressed inside each other's round trip.
    // Built from a snapshot taken at tap time, the name write carried the *pre-tap* mark and the
    // server wrote it back over the one that had just landed.
    @Test
    fun `a name saved inside a mark's round trip does not undo the mark`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null))
        app(saved = colony(), api = server) {
            openTheProfile()

            holdTheProfileWrites()
            chooseTheMark(MarkPreset.SEXTANT)
            typeAName("Ada")
            saveTheName()
            letTheProfileWritesLand()

            assertProfilesWritten(
                PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.SEXTANT)),
                PlayerProfile(name = CommanderName("Ada"), mark = PlayerMark.Preset(MarkPreset.SEXTANT)),
            )
        }
    }

    // ── A write that outlives the session it was made under ──────────────────────────────────
    //
    // **The composition root's scope survives a session ending, and every one of these is what that
    // costs when nothing checks.** A profile write is launched on the `Surface`'s scope, which
    // outlives `endSession` and the branch leaving composition — so its answer can arrive at a
    // process that has since signed a *different* player in. Nothing in the answer says which session
    // asked, and every arm of it writes to state the current session owns.

    // **The worst of the three**, because the player is dumped back to the gate seconds after signing
    // in with nothing on screen saying why. `Unauthenticated` is exactly what the server answers for
    // a valid token naming a player who is gone — deliberately not `SessionExpired`, so nothing
    // renews it and nothing swallows it — and the arm that reads it signs out whatever session is
    // current *now*.
    @Test
    fun `a write refused after its session ended does not sign the next one out`() {
        val server = serverHolding(named("Ada Lovelace"))
        app(saved = colony(), api = server) {
            waitUntilItReads("Ada Lovelace")
            openTheProfile()

            holdTheProfileWrites()
            chooseTheMark(MarkPreset.SEXTANT)
            dismissTheSettings()

            // The session ends under the write, which is what `deleteAccount` does on purpose and
            // what a colony refused for a dead credential does by accident.
            server.error = ApiError.Unauthenticated
            tapTheActionOn(BuildingType.METAL_MINE)
            waitUntilItReads("The galaxy is shared")

            // Somebody signs in on the same phone, and the app opens on their colony.
            server.error = null
            pressProvider(AuthProvider.APPLE)
            waitUntilItReads("Metal Mine")

            // Only now does the orphan answer, and it answers about an account that is gone.
            server.profileError = ApiError.Unauthenticated
            letTheProfileWritesLand()

            assertReads("Metal Mine")
            assertDoesNotRead("The galaxy is shared")
        }
    }

    // **The success arm has the same hole and it is quieter.** What comes back is the *previous*
    // account's row, and the shell drew it — so the strip wore somebody else's name and the next mark
    // tapped would have posted that whole row over the account actually signed in.
    @Test
    fun `a write that lands after its session ended does not put the old account back on the strip`() {
        val server = serverHolding(named("Ada Lovelace"))
        app(saved = colony(), api = server) {
            waitUntilItReads("Ada Lovelace")
            openTheProfile()

            holdTheProfileWrites()
            chooseTheMark(MarkPreset.SEXTANT)
            dismissTheSettings()

            server.error = ApiError.Unauthenticated
            tapTheActionOn(BuildingType.METAL_MINE)
            waitUntilItReads("The galaxy is shared")

            server.error = null
            server.profile = named("Grace Hopper")
            pressProvider(AuthProvider.APPLE)
            waitUntilItReads("Grace Hopper")

            letTheProfileWritesLand()

            assertThePlayerStripReads("Grace Hopper")
            assertDoesNotRead("Ada Lovelace")
        }
    }

    // **The lock belongs to the session too, and this is what it costs when it does not.** One mutex
    // for the life of the process means the orphan holds it across the session change: the new
    // player's first tap waits on a dead session's lock for as long as the far end takes, and then
    // meets a floor that drops it silently. Asserted on what *left the phone* rather than on what
    // landed, because with the write held neither of them lands — and only the first can tell a tap
    // that was sent from a tap the shell is still sitting on.
    @Test
    fun `a tap in a new session does not queue behind a write the last one left in flight`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null))
        app(saved = colony(), api = server) {
            openTheProfile()

            holdTheProfileWrites()
            chooseTheMark(MarkPreset.SEXTANT)
            dismissTheSettings()

            server.error = ApiError.Unauthenticated
            tapTheActionOn(BuildingType.METAL_MINE)
            waitUntilItReads("The galaxy is shared")

            server.error = null
            pressProvider(AuthProvider.APPLE)
            waitUntilItReads("Metal Mine")

            openTheProfile()
            chooseTheMark(MarkPreset.WAKE)

            assertProfilesTaken(
                PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.SEXTANT)),
                PlayerProfile(name = null, mark = PlayerMark.Preset(MarkPreset.WAKE)),
            )
        }
    }

    // ── The frame between a session and the account behind it ────────────────────────────────

    // **The gate draws whenever there is no colony**, and reading the account is a whole round trip
    // between a device learning it is signed in and the colony arriving. With no suspension point
    // between the two assignments no frame could compose in between; with one, a player who is
    // already signed in is shown the live sign-in screen for the length of a request.
    @Test
    fun `a device that is already signed in waits rather than drawing the gate while the account is read`() {
        val server = serverHolding(named("Ada Lovelace"))
        server.holdProfileReads()

        app(saved = null, api = server) {
            assertReads("Signing in.")

            letTheProfileReadLand()

            waitUntilItReads("Ada Lovelace")
        }
    }

    // ── The composer, offline ────────────────────────────────────────────────────────────────

    // **Eleven chips are eleven controls**, and the identity face's own rule reaches every one of
    // them: a tap that cannot leave the phone is answered in amber rather than swallowed. The
    // composer had no held state at all, so every part stayed lit and every tap did nothing.
    @Test
    fun `a tap on a part chip that meets a dead network is answered in amber rather than swallowed`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null))
        app(saved = colony(), api = server) {
            openTheProfile()
            openTheComposer()
            assertProfileHeld(false)

            server.offline = true
            chooseTheBody(MarkBody.ORBIT)

            waitUntilTheProfileIsHeld()
            assertProfilesWritten()
            assertComposerShowing()
        }
    }

    // ── The draft, and what may end it ───────────────────────────────────────────────────────

    // **Only a name landing ends the edit.** `writeProfile` is the one path five controls take and
    // four of them are marks, so a draft reset on every answer threw away what a player had typed the
    // moment they picked a picture — which contradicts the frame's own split: the mark commits on
    // tap and the name has a button.
    @Test
    fun `a mark chosen while a name is being typed keeps what was typed`() {
        app(saved = colony()) {
            openTheProfile()
            typeAName("Ada")
            chooseTheMark(MarkPreset.SEXTANT)

            assertSaveOffered(true)
            assertReads("Ada")
        }
    }

    // **And only the name that was actually sent ends it.** The field is fully live for the whole
    // round trip — nothing dims it, nothing takes the keyboard away — so a player goes on typing
    // while the answer is out. Clearing the draft on any name landing threw those keystrokes away
    // silently, in front of somebody still typing, and put the shorter name back under their cursor.
    @Test
    fun `keystrokes made while a name is landing survive the answer`() {
        val server = serverHolding(PlayerProfile(name = null, mark = null))
        app(saved = colony(), api = server) {
            openTheProfile()
            typeAName("Ada")

            holdTheProfileWrites()
            saveTheName()
            typeAName(" Lovelace")
            letTheProfileWritesLand()

            assertReads("Ada Lovelace")
            assertSaveOffered(true)
        }
    }

    // ── The way out ──────────────────────────────────────────────────────────────────────────

    // **Every exit goes through `closeSheet`**, and this is the one assertion that can tell that from
    // a face cleared by hand: dismissing is also what marks the release read, so a player who
    // dismissed from here without it would be shown the same changelog on every launch, for ever.
    @Test
    fun `dismissing the identity face marks the changelog read`() {
        val store = PreferencesStore(InMemorySaveFile())
        runBlocking {
            store.save(
                Preferences(
                    galaxyLanding = null,
                    // Nothing remembered, which is what an upgrade looks like: the changelog raises
                    // itself over the app and is dismissed first, so what this test is about is the
                    // *second* dismissal — from a face this slice added.
                    lastSeenVersion = null,
                    provider = null,
                    lastReachedAt = null,
                ),
            )
        }

        app(saved = colony(), preferences = store) {
            dismissTheSettings()
            openTheProfile()
            assertIdentityShowing()
            dismissTheSettings()

            assertIdentityShowing(showing = false)
        }

        assertEquals(
            EnglishChangelog.releases.first().version.printed,
            runBlocking { store.load() }.lastSeenVersion,
        )
    }

    // **A draft nobody committed is not a name**, so closing the sheet puts the field back to what
    // the account holds. Without it, reopening would show an abandoned draft over a save button
    // offering to commit it.
    @Test
    fun `a draft that was abandoned is gone when the face is opened again`() {
        app(saved = colony()) {
            openTheProfile()
            typeAName("Ada")
            dismissTheSettings()
            openTheProfile()

            assertSaveOffered(false)
            assertDoesNotRead("Ada")
        }
    }

    private fun colony(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        debugUsed = false,
        state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds())),
    )

    private fun named(name: String): PlayerProfile = PlayerProfile(name = CommanderName(name), mark = null)

    // A server that already holds a profile, which is what an account that has been named on another
    // device looks like on this one. The colony half is `app`'s own default said again, because
    // handing in a server of one's own replaces it whole.
    private fun serverHolding(profile: PlayerProfile): FakeOltreApi {
        val snapshot = colony()
        return FakeOltreApi().apply {
            colony = snapshot
            founds = snapshot
            replays = true
            this.profile = profile
        }
    }
}
