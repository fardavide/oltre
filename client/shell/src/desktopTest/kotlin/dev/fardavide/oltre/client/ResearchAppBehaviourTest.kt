package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.AlertSettings
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.ClientVerb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The Research tab driven through the composition root**, which is the only place its two verbs
// and its two watch targets meet a server. `AdaptationBehaviourTest` drives the same screen against
// a `TestGame` — a harness that owns the colony directly — so it says nothing about what leaves the
// phone, and what leaves the phone is the whole of what changed at 0.21.
//
// Both branches are here because they are two verbs and two watch targets that look identical on
// screen and are matched back to the queue by different questions: a project by `Technology`, a
// ladder by `AdaptationTechnology`, and a row that is a `WatchTarget.Project` or a
// `WatchTarget.Ladder`. Nothing above `core` can tell them apart by looking.
class ResearchAppBehaviourTest {

    @Test
    fun `starting a project sends the verb the row names`() {
        app(saved = withLab()) {
            open(OltreTab.RESEARCH)

            startTheFirstProject()

            assertTrue(
                server.syncs().flatMap { it.envelopes }.any { it.verb is ClientVerb.StartResearch },
                "what left the phone: ${server.syncs().flatMap { it.envelopes }.map { it.verb }}",
            )
        }
    }

    // The applied branch's neighbour, and a different verb with the same shape on screen — which is
    // exactly why it is asserted separately rather than assumed from the one above.
    @Test
    fun `starting a ladder sends the adaptation verb rather than the research one`() {
        app(saved = withLab()) {
            open(OltreTab.RESEARCH)

            startTheThermalLadder()

            val sent = server.syncs().flatMap { it.envelopes }.map { it.verb }
            assertEquals(1, sent.count { it is ClientVerb.StartAdaptation }, "what left the phone: $sent")
            assertEquals(0, sent.count { it is ClientVerb.StartResearch }, "what left the phone: $sent")
        }
    }

    // **A square on a running row books an alert and changes no colony.** It is the same verb the
    // colony's rows send — `ToggleAlert` — carrying a target only this screen produces, and the
    // shell is where the two are wired together.
    @Test
    fun `asking to be told about a running project sends a watch for that project`() {
        app(saved = withLab()) {
            open(OltreTab.RESEARCH)
            startTheFirstProject()

            tapTheWatchOnTheFirstProject()

            val sent = server.syncs().flatMap { it.envelopes }.map { it.verb }
            assertEquals(1, sent.count { it is ClientVerb.ToggleAlert }, "what left the phone: $sent")
        }
    }

    // **A Robotics Factory at level 2 and enough of everything.** Level 1 opens the applied branch
    // and level 2 opens the ladders, so this one fixture reaches both — and being funded past every
    // price on the screen is what makes a row that offers nothing a rule rather than a bill.
    //
    // `CARRIED_FORWARD` because a per-row square is a per-item question: under a new colony's own
    // `BY_CATEGORY` the branch is announced by its kind and the row correctly draws no control at
    // all, which is the app's one answer to a control with nothing left to decide.
    private fun withLab(): GameSnapshot = GameSnapshot(
        lastUpdatedAt = TEST_NOW,
        debugUsed = false,
        state = GameState.initial(GalaxySeed(TEST_NOW.toEpochMilliseconds())).let { fresh ->
            fresh.copy(
                resources = Resources.of(metal = 5_000_000, crystal = 5_000_000, deuterium = 5_000_000),
                buildings = fresh.buildings.withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(2)),
                alerts = AlertSettings.CARRIED_FORWARD,
            )
        },
    )
}
