package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.buildShips
import dev.fardavide.oltre.core.cycleHullAlert
import dev.fardavide.oltre.core.setAlertDelivery
import dev.fardavide.oltre.core.setAlertMode
import dev.fardavide.oltre.core.startAdaptation
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.core.toggleAlert
import dev.fardavide.oltre.core.toggleAlertCategory
import dev.fardavide.oltre.core.toggleFlightAlerts
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.VerbRefusal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.hours

class ApplyVerbTest {

    private val state = freshColony().state

    // ── The twelve arms ───────────────────────────────────────────────────────────────────────
    //
    // What is being guarded is the wiring and nothing else: a verb routed to the wrong `core`
    // function type-checks, applies, and hands back a colony that changed in a way nobody asked
    // for. The rules themselves are `core`'s and are tested there, so each of these asserts that
    // the outcome is exactly what calling the function by hand produces.

    @Test
    fun `start upgrade is routed to the facility the verb names`() {
        val outcome = applyVerb(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), state, TEST_NOW)

        assertEquals(startUpgrade(state, BuildingType.METAL_MINE, TEST_NOW).outcome(), outcome)
    }

    @Test
    fun `start research is routed to the technology the verb names`() {
        val outcome = applyVerb(ClientVerb.StartResearch(Technology.PHOTOVOLTAICS), state, TEST_NOW)

        assertEquals(startResearch(state, Technology.PHOTOVOLTAICS, TEST_NOW).outcome(), outcome)
    }

    @Test
    fun `start adaptation is routed to the ladder the verb names`() {
        val outcome = applyVerb(ClientVerb.StartAdaptation(AdaptationTechnology.THERMAL), state, TEST_NOW)

        assertEquals(startAdaptation(state, AdaptationTechnology.THERMAL, TEST_NOW).outcome(), outcome)
    }

    @Test
    fun `build ships is routed with the manifest the verb carries`() {
        val manifest = Ships.of(ShipType.SKIFF, 1)

        val outcome = applyVerb(ClientVerb.BuildShips(manifest), state, TEST_NOW)

        assertEquals(buildShips(state, manifest, TEST_NOW).outcome(), outcome)
    }

    @Test
    fun `start run is routed with all four of its subjects`() {
        val outcome = applyVerb(
            ClientVerb.StartRun(
                target = GalaxyCoordinate(galaxy = 1, system = 1, slot = 1),
                gathering = ResourceKind.METAL,
                ships = Ships.NONE,
                window = 6.hours,
            ),
            state,
            TEST_NOW,
        )

        // A fresh colony owns no hull at all, so the empty manifest is refused before anything
        // about the target is even looked at.
        assertEquals(VerbOutcome.Refused(VerbRefusal.NO_SUCH_SHIPS), outcome)
    }

    @Test
    fun `start survey is routed to the system the verb names`() {
        val outcome = applyVerb(ClientVerb.StartSurvey(SystemAddress(galaxy = 1, system = 1)), state, TEST_NOW)

        // No scout, because genesis grants no hull — the first one is the first purchase.
        assertEquals(VerbOutcome.Refused(VerbRefusal.NO_IDLE_SCOUT), outcome)
    }

    @Test
    fun `toggle alert is routed to the row the verb names`() {
        val target = WatchTarget.Facility(BuildingType.SOLAR_PLANT)

        val outcome = applyVerb(ClientVerb.ToggleAlert(target), state, TEST_NOW)

        assertEquals(VerbOutcome.Accepted(toggleAlert(state, target)), outcome)
    }

    @Test
    fun `cycle hull alert is routed to the hull the verb names`() {
        val outcome = applyVerb(ClientVerb.CycleHullAlert(ShipType.SKIFF), state, TEST_NOW)

        assertEquals(VerbOutcome.Accepted(cycleHullAlert(state, ShipType.SKIFF)), outcome)
    }

    @Test
    fun `toggle flight alerts is routed to the standing bell`() {
        val outcome = applyVerb(ClientVerb.ToggleFlightAlerts, state, TEST_NOW)

        assertEquals(VerbOutcome.Accepted(toggleFlightAlerts(state)), outcome)
    }

    @Test
    fun `set alert mode is routed with the mode the verb carries`() {
        val outcome = applyVerb(ClientVerb.SetAlertMode(AlertMode.BY_CATEGORY), state, TEST_NOW)

        assertEquals(VerbOutcome.Accepted(setAlertMode(state, AlertMode.BY_CATEGORY)), outcome)
    }

    @Test
    fun `toggle alert category is routed to the category the verb names`() {
        val outcome = applyVerb(ClientVerb.ToggleAlertCategory(AlertCategory.FACILITIES), state, TEST_NOW)

        assertEquals(VerbOutcome.Accepted(toggleAlertCategory(state, AlertCategory.FACILITIES)), outcome)
    }

    @Test
    fun `set alert delivery is routed with the delivery the verb carries`() {
        val outcome = applyVerb(ClientVerb.SetAlertDelivery(AlertDelivery.TOTAL), state, TEST_NOW)

        assertEquals(VerbOutcome.Accepted(setAlertDelivery(state, AlertDelivery.TOTAL)), outcome)
    }

    // ── The six flattenings ───────────────────────────────────────────────────────────────────
    //
    // `VerbRefusal` is `core`'s six refusable result types collapsed into fifteen constants, and a
    // mapping that is wrong is a player told the wrong reason — the one thing the flattening was
    // allowed to cost, since the verb rides on the envelope, was precision and never accuracy.
    // Constructed rather than provoked: every refusal is a `data object`, so the arm is reachable
    // in one line where reaching it through the rules would take a colony built to fail.

    @Test
    fun `a colony already climbing that facility refuses the upgrade`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.ALREADY_UPGRADING), StartUpgradeResult.AlreadyUpgrading.outcome())
    }

    @Test
    fun `an upgrade the stores cannot pay for is refused`() {
        assertEquals(
            VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES),
            StartUpgradeResult.InsufficientResources.outcome(),
        )
    }

    @Test
    fun `an upgrade whose gate is not met is refused`() {
        assertEquals(
            VerbOutcome.Refused(VerbRefusal.REQUIREMENTS_NOT_MET),
            StartUpgradeResult.RequirementsNotMet.outcome(),
        )
    }

    @Test
    fun `a busy research slot refuses the project`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.SLOT_BUSY), StartResearchResult.SlotBusy.outcome())
    }

    @Test
    fun `a project the stores cannot pay for is refused`() {
        assertEquals(
            VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES),
            StartResearchResult.InsufficientResources.outcome(),
        )
    }

    @Test
    fun `a project whose gate is not met is refused`() {
        assertEquals(
            VerbOutcome.Refused(VerbRefusal.REQUIREMENTS_NOT_MET),
            StartResearchResult.RequirementsNotMet.outcome(),
        )
    }

    @Test
    fun `a busy adaptation slot refuses the ladder`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.SLOT_BUSY), StartAdaptationResult.SlotBusy.outcome())
    }

    @Test
    fun `a ladder the stores cannot pay for is refused`() {
        assertEquals(
            VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES),
            StartAdaptationResult.InsufficientResources.outcome(),
        )
    }

    @Test
    fun `a ladder whose gate is not met is refused`() {
        assertEquals(
            VerbOutcome.Refused(VerbRefusal.REQUIREMENTS_NOT_MET),
            StartAdaptationResult.RequirementsNotMet.outcome(),
        )
    }

    @Test
    fun `an empty manifest is refused rather than built`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.NOTHING_TO_BUILD), BuildShipsResult.NothingToBuild.outcome())
    }

    @Test
    fun `a hull with no price is refused rather than invented`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.NOT_FOR_SALE), BuildShipsResult.NotForSale.outcome())
    }

    @Test
    fun `a manifest the stores cannot pay for is refused`() {
        assertEquals(
            VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES),
            BuildShipsResult.InsufficientResources.outcome(),
        )
    }

    @Test
    fun `a run to a world nobody has looked at is refused`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.UNSURVEYED), StartRunResult.Unsurveyed.outcome())
    }

    @Test
    fun `a run to a world that cannot be gathered from is refused`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.NOT_A_VALID_TARGET), StartRunResult.NotAValidTarget.outcome())
    }

    @Test
    fun `a run the idle pool cannot fill is refused`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.NO_SUCH_SHIPS), StartRunResult.NoSuchShips.outcome())
    }

    @Test
    fun `a run carrying a hull with no hold is refused`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.NOT_A_GATHERING_HULL), StartRunResult.NotAGatheringHull.outcome())
    }

    @Test
    fun `a window that cannot bring the fleet home is refused`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.WINDOW_TOO_SHORT), StartRunResult.WindowTooShort.outcome())
    }

    @Test
    fun `a world with nothing left of what was asked for is refused`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.DEPLETED), StartRunResult.Depleted.outcome())
    }

    @Test
    fun `a system a probe is already on its way to is refused`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.ALREADY_SURVEYING), StartSurveyResult.AlreadySurveying.outcome())
    }

    @Test
    fun `a system already known is refused`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.ALREADY_SURVEYED), StartSurveyResult.AlreadySurveyed.outcome())
    }

    @Test
    fun `a survey with no idle scout is refused`() {
        assertEquals(VerbOutcome.Refused(VerbRefusal.NO_IDLE_SCOUT), StartSurveyResult.NoIdleScout.outcome())
    }

    @Test
    fun `a survey the stores cannot pay for is refused`() {
        assertEquals(
            VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES),
            StartSurveyResult.InsufficientResources.outcome(),
        )
    }

    @Test
    fun `every accepted result carries the colony core produced and not the one it was given`() {
        // The `Started` arm of all six, which the twelve routing tests above cannot all reach: a
        // fresh colony has no Robotics Factory and no hull, so five of the six refuse before they
        // can succeed. Constructed rather than earned, exactly as the refusals are — what is being
        // guarded is that the arm hands back the state it was carrying and not `state`.
        val produced = toggleFlightAlerts(state)

        assertEquals(VerbOutcome.Accepted(produced), StartUpgradeResult.Started(produced).outcome())
        assertEquals(VerbOutcome.Accepted(produced), StartResearchResult.Started(produced).outcome())
        assertEquals(VerbOutcome.Accepted(produced), StartAdaptationResult.Started(produced).outcome())
        assertEquals(VerbOutcome.Accepted(produced), BuildShipsResult.Started(produced).outcome())
        assertEquals(VerbOutcome.Accepted(produced), StartRunResult.Started(produced).outcome())
        assertEquals(VerbOutcome.Accepted(produced), StartSurveyResult.Started(produced).outcome())
        assertNotEquals(state, produced)
    }
}
