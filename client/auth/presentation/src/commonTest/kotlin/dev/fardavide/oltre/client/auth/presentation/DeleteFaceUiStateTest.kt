package dev.fardavide.oltre.client.auth.presentation

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.startRun
import dev.fardavide.oltre.protocol.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **The four numbers are the argument**, which is what this file is for. The sheet's second sentence
// — *signing in again does not return any of it* — is a claim nobody weighs in the abstract; it lands
// because the rows above it say *84 systems surveyed* about the colony the player is looking at. A
// mapper that quietly counted the wrong thing would leave the sentence intact and the argument gone,
// and no screenshot would notice.
class DeleteFaceUiStateTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    @Test
    fun `should read the facts off the colony rather than warning in the abstract`() {
        val labels = colony().face(DeleteFace.WARN).facts.map { English.resolve(it.label) }

        assertEquals(listOf("COLONY", "FLEET", "MAP", "RESEARCH"), labels)
    }

    // **Built facilities rather than all six.** A facility at level 0 is one the colony does not
    // have — the Colony tab draws it as a locked row for exactly that reason — so counting the
    // catalogue would tell every player the same number on their first day and on their last.
    @Test
    fun `should count the facilities the colony has built rather than the ones it could`() {
        val two = colony().copy(
            buildings = Buildings.initial()
                .withLevel(BuildingType.CRYSTAL_MINE, BuildingLevel(0))
                .withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(0)),
        )

        assertTrue("2 facilities" in English.resolve(two.fact(DeleteFactRow.COLONY)))
    }

    @Test
    fun `should say one facility rather than one facilities`() {
        val one = colony().copy(
            buildings = BuildingType.entries.fold(Buildings.initial()) { built, type ->
                if (type == BuildingType.METAL_MINE) built else built.withLevel(type, BuildingLevel(0))
            },
        )

        assertTrue("1 facility," in English.resolve(one.fact(DeleteFactRow.COLONY)))
    }

    // **Every hull, not the idle pool.** A player about to delete an account is owed the whole fleet
    // rather than the part of it that happens to be in orbit at that second — and the run in flight
    // is counted twice on purpose: once as hulls, once as a run.
    @Test
    fun `should count the hulls that are away as well as the ones at home`() {
        val away = colony().copy(ships = Ships.of(ShipType.SKIFF, 4)).dispatchOne()

        val fleet = English.resolve(away.fact(DeleteFactRow.FLEET))

        assertTrue("4 skiffs" in fleet, "the whole fleet, not the idle pool: $fleet")
        assertTrue("1 run in flight" in fleet, fleet)
    }

    // **A first launch, not an edge case**: the opening stock buys a hull and does not grant one, so
    // the very first time this sheet can be opened there is no fleet to list. `Strings.listed` has no
    // grammar for an empty list, and a row that read *", and the 0 runs in flight"* would be the
    // mapper showing through.
    @Test
    fun `should say a colony with no fleet has none rather than printing an empty list`() {
        val fleetless = colony().copy(ships = Ships.NONE)

        assertEquals("No hulls yet, and nothing in flight.", English.resolve(fleetless.fact(DeleteFactRow.FLEET)))
    }

    // Systems rather than worlds: a survey is asked for by system and a player counts them that way,
    // so a home system with four worlds in it is one line of this row rather than four.
    @Test
    fun `should count surveyed systems rather than surveyed worlds`() {
        val state = colony()
        val systems = state.galaxy.surveyed.map { it.galaxy to it.system }.distinct().size

        assertTrue(state.galaxy.surveyed.size > systems, "the seed surveys one system with several worlds")
        assertTrue("$systems system" in English.resolve(state.fact(DeleteFactRow.MAP)))
    }

    // **Levels rather than ladders**, which is what "9 projects" means on a screen where five
    // technologies can each be at level three: nine things bought, which is the number a player
    // recognises as what they spent.
    @Test
    fun `should count research levels rather than the ladders they sit on`() {
        val researched = colony().copy(
            research = Research.initial()
                .withLevel(Technology.EXTRACTION, TechLevel(3))
                .withLevel(Technology.PROPULSION, TechLevel(2))
                .withLevel(AdaptationTechnology.THERMAL, TechLevel(1)),
        )

        assertEquals("5 projects and 1 adaptation", English.resolve(researched.fact(DeleteFactRow.RESEARCH)))
    }

    // **Gone on the last step**, because the rows were for reading and this face is for deciding. A
    // confirm face that still listed them would be asking the player to read the argument twice and
    // would bury the one control that does the thing.
    @Test
    fun `should drop the facts on the last step`() {
        assertEquals(emptyList(), colony().face(DeleteFace.CONFIRM).facts)
    }

    // Only one control in the product is a filled red button, and this is the tap it is for.
    @Test
    fun `should mark only the last step destructive`() {
        assertEquals(false, colony().face(DeleteFace.WARN).destructive)
        assertEquals(true, colony().face(DeleteFace.CONFIRM).destructive)
    }

    // **The way back exists only where there is something to go back from.** The warn face is a
    // reading and its dismissal is the sheet's own; the confirm face is a decision, so it carries the
    // other answer as a control of its own.
    @Test
    fun `should offer keeping it only on the last step`() {
        assertNull(colony().face(DeleteFace.WARN).keep)
        assertEquals("Keep it", English.resolve(assertNotNull(colony().face(DeleteFace.CONFIRM).keep)))
    }

    // The provider is named because it is the sign-in that will not bring any of this back — and
    // naming the *player's* provider rather than both is the difference between a warning and a fact
    // about their account.
    @Test
    fun `should name the provider the player signed in with`() {
        val apple = colony().face(DeleteFace.CONFIRM, provider = AuthProvider.APPLE)
        val google = colony().face(DeleteFace.CONFIRM, provider = AuthProvider.GOOGLE)

        assertTrue("Apple" in English.resolve(apple.second), English.resolve(apple.second))
        assertTrue("Google" in English.resolve(google.second), English.resolve(google.second))
    }

    // **The one verb in the app that cannot be held.** Deleting an account is `LOOK_DONT_ACT`: the
    // queue would promise the tap happens when the network is back, and this is the one request the
    // game will not promise offline. So the sheet says so instead of greying a button.
    @Test
    fun `should refuse the whole sheet when the server cannot be reached`() {
        val refusal = colony().face(DeleteFace.CONFIRM, offline = true).refusal

        assertEquals("This cannot be held.", English.resolve(assertNotNull(refusal).lead))
    }

    @Test
    fun `should carry no refusal when the server can be reached`() {
        assertNull(colony().face(DeleteFace.WARN).refusal)
        assertNull(colony().face(DeleteFace.CONFIRM).refusal)
    }

    // **The commander is the account's and not the catalogue's**, on both faces of the one flow in
    // this app that cannot be undone. This mapper read `Strings.playerDefaultName()` outright, so a
    // player who had named themselves was asked to confirm deleting somebody else — and the settings
    // sheet's own Account row two taps earlier said the right name, which is what makes the two
    // together a contradiction rather than a lapse.
    @Test
    fun `should name the commander the account chose rather than the default`() {
        val named = TextRes("Ada Lovelace")

        assertTrue("Ada Lovelace ·" in English.resolve(colony().face(DeleteFace.WARN, name = named).facts[0].value))
        assertEquals(
            "Delete Ada Lovelace?",
            English.resolve(colony().face(DeleteFace.CONFIRM, name = named).title),
        )
    }

    // The other half of the same claim: an account that has chosen nothing still reads as the name
    // the strip draws for it, because the substitution happens once, where the profile is.
    @Test
    fun `should say whatever name it is handed for an account that has chosen none`() {
        assertEquals(
            "Delete Dead Reckoning?",
            English.resolve(colony().face(DeleteFace.CONFIRM).title),
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    // Which row an assertion is about, so a test names the fact rather than an index into a list —
    // a positional `facts[2]` would go on passing while pointing at the wrong row.
    private enum class DeleteFactRow { COLONY, FLEET, MAP, RESEARCH }

    private fun GameState.face(
        face: DeleteFace,
        provider: AuthProvider = AuthProvider.APPLE,
        offline: Boolean = false,
        // What the shell hands in for an account that has chosen nothing — the strip's own
        // substitution, said here so the rest of this file goes on reading as it did.
        name: TextRes = Strings.playerDefaultName(),
    ) = toDeleteFaceUiState(face = face, provider = provider, offline = offline, name = name)

    private fun GameState.fact(row: DeleteFactRow) = face(DeleteFace.WARN).facts[row.ordinal].value

    private fun colony(): GameState = GameState.initial(GalaxySeed(SEED))

    // Genesis surveys the home system, so a neighbour of home is a legal target on turn one.
    private fun GameState.dispatchOne(): GameState {
        val target = galaxy.surveyed.filter { it != galaxy.home }.minByOrNull { it.slot }
            ?: error("the test seed's home system holds no world but home")
        return assertIs<StartRunResult.Started>(
            startRun(this, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 3.hours, t0),
        ).state
    }
}

private const val SEED = 20_260_826L
