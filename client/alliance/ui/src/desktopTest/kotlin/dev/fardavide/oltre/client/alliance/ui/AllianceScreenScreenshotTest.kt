package dev.fardavide.oltre.client.alliance.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceProject
import dev.fardavide.oltre.protocol.JoinRequestId
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **The four faces this destination has**, drawn from hand-built state with no gateway anywhere near
// them — which is the point of the screen taking a `AllianceUiState` rather than reaching for one.
//
// Both languages on the two faces with the most words on them, because Italian is the one that has
// broken a layout before and the treasury has the longest sentence in the feature on it.
@OptIn(ExperimentalTestApi::class)
class AllianceScreenScreenshotTest {

    @Test
    fun `looking for one, with nothing typed`() {
        capture("alliance_seeking", seeking(SearchResultsUiState.Idle(Strings.allianceSearchIdle())))
    }

    @Test
    fun `looking for one, with results`() {
        capture("alliance_seeking_results", seeking(SearchResultsUiState.Results(listOf(FERRO, THRESHOLD))))
    }

    // Nothing found, and the string named in full ink — with the founding block underneath saying
    // where to go next, which is the whole reason the empty state stops rather than suggesting.
    @Test
    fun `looking for one, and finding nothing`() {
        capture(
            "alliance_seeking_empty",
            seeking(SearchResultsUiState.Empty(Strings.allianceSearchEmpty(TextRes("Aphelion")))),
        )
    }

    // **The first refusal in this app a finger can reach**, on the field rather than in a block.
    @Test
    fun `a name and a tag that are already somebody's`() {
        capture(
            "alliance_founding_refused",
            AllianceUiState.Seeking(
                search = SEARCH.copy(state = SearchResultsUiState.Idle(Strings.allianceSearchIdle())),
                founding = FOUNDING.copy(
                    name = "Ferro Alto",
                    tag = "FRA",
                    nameRefusal = Strings.allianceNameTaken(TextRes("Ferro Alto")),
                    tagRefusal = Strings.allianceTagTaken(TextRes("FRA")),
                    committable = false,
                ),
            ),
        )
    }

    @Test
    fun `waiting on an answer`() {
        capture("alliance_waiting", WAITING)
    }

    @Test
    fun `in one, as its founder`() {
        capture("alliance_enlisted", enlisted(), height = 1_400)
    }

    @Test
    fun `in one, as its founder, in Italian`() {
        capture("alliance_enlisted_it", enlisted(), translations = Italian, height = 1_400)
    }

    @Test
    fun `in one, in a Slide Over window`() {
        capture("alliance_enlisted_slide_over", enlisted(), width = SLIDE_OVER_WIDTH, height = 1_500)
    }

    // A pool short of the project it is saving for: the cost goes red and the control is absent
    // rather than answering no.
    @Test
    fun `a project the pool cannot cover`() {
        capture(
            "alliance_project_short",
            enlisted(
                projects = listOf(CHARTER.copy(affordable = false, buyable = false)),
                pool = listOf(
                    PoolRowUiState(Strings.resourceName(ResourceKind.METAL), TextRes("4,210")),
                    PoolRowUiState(Strings.resourceName(ResourceKind.CRYSTAL), TextRes("960")),
                    PoolRowUiState(Strings.resourceName(ResourceKind.DEUTERIUM), TextRes("120")),
                ),
            ),
            height = 1_400,
        )
    }

    // `All` raising the delete face's two-step grammar, which is the one tap on this screen that
    // confirms — Davide, 2026-09-14.
    @Test
    fun `everything you have`() {
        capture(
            "alliance_confirm_all",
            enlisted(),
            confirm = ContributeConfirmUiState(
                title = Strings.allianceConfirmAllTitle(),
                body = Strings.allianceConfirmAllBody(),
                confirm = Strings.allianceConfirmAllAction(),
                keep = Strings.allianceConfirmAllKeep(),
            ),
            height = 1_600,
        )
    }

    // **Red rather than amber**, because a contribution cannot be queued: the chips keep full
    // strength and the sentence says the resources never left.
    @Test
    fun `a contribution that did not reach the pool`() {
        capture(
            "alliance_contribution_refused",
            enlisted(),
            refusal = RefusalUiState(
                lead = Strings.refusedContributionLead(),
                body = Strings.refusedContributionBody(),
            ),
            height = 1_500,
        )
    }

    // The whole face with no signal — a sentence at full strength rather than a dimmed screen,
    // because reading is not acting and there is nothing cached here worth a stale stamp.
    @Test
    fun `no signal at all`() {
        capture("alliance_held", AllianceUiState.Held)
    }

    // Asked and not yet answered. Words rather than a spinner: nothing on this screen animates.
    @Test
    fun `asking the server`() {
        capture("alliance_asking", AllianceUiState.Asking)
    }

    // A member rather than a founder: no pending list at all — `null` on the wire means *not yours
    // to see*, which is a different screen from *nobody is waiting*. And a way out, which a founder
    // with company does not get.
    @Test
    fun `in one as a plain member`() {
        capture(
            "alliance_enlisted_member",
            enlisted().copy(
                pending = null,
                roster = ROSTER.map { it.copy(removable = false) },
                departure = DepartureUiState(action = Strings.allianceLeaveAction(), disbands = false),
            ),
            height = 1_200,
        )
    }

    // The good empty state, at full strength, naming where a request comes from — with no control
    // and no word about time.
    @Test
    fun `nobody is waiting`() {
        capture(
            "alliance_nobody_waiting",
            enlisted().let { it.copy(pending = it.pending?.copy(rows = emptyList())) },
            height = 1_300,
        )
    }

    // Nothing left to build: the catalogue empties rather than offering a row that takes the pool
    // and moves nothing.
    @Test
    fun `nothing left to build`() {
        capture("alliance_projects_done", enlisted(projects = emptyList()), height = 1_300)
    }

    // A colony with almost nothing in it: three chips floor to zero and do not press, which is
    // `core`'s own `NothingOffered` refusal arriving one layer earlier.
    @Test
    fun `a colony with nothing much to give`() {
        capture(
            "alliance_chips_inert",
            enlisted().let { face ->
                face.copy(
                    treasury = face.treasury.copy(
                        chips = CHIPS.mapIndexed { index, chip -> chip.copy(enabled = index == 3) },
                    ),
                )
            },
            height = 1_300,
        )
    }

    private fun capture(
        name: String,
        state: AllianceUiState,
        translations: Translations = English,
        width: Int = PHONE_WIDTH,
        height: Int = 700,
        confirm: ContributeConfirmUiState? = null,
        refusal: RefusalUiState? = null,
    ) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = translations) {
                    Surface {
                        AllianceScreen(
                            state = state,
                            actions = AllianceActions(),
                            confirm = confirm,
                            refusal = refusal,
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private fun seeking(results: SearchResultsUiState): AllianceUiState.Seeking =
        AllianceUiState.Seeking(search = SEARCH.copy(state = results), founding = FOUNDING)

    private fun enlisted(
        projects: List<ProjectRowUiState> = listOf(CHARTER),
        pool: List<PoolRowUiState> = POOL,
    ): AllianceUiState.Enlisted = AllianceUiState.Enlisted(
        header = HEAD,
        pending = PendingUiState(
            label = Strings.alliancePendingLabel(),
            rows = listOf(PENDING),
            empty = Strings.alliancePendingEmpty(),
        ),
        roster = ROSTER,
        treasury = TreasuryUiState(
            label = Strings.allianceTreasuryLabel(),
            rule = Strings.allianceTreasuryRule(),
            pool = pool,
            contributed = Strings.alliancePaidIn(TextRes("42,000")),
            chips = CHIPS,
            projectsLabel = Strings.allianceProjectsLabel(),
            projects = projects,
            projectsEmpty = Strings.allianceProjectsEmpty(),
            unread = null,
        ),
        departure = null,
    )

    private companion object {
        const val PHONE_WIDTH = 393
        const val SLIDE_OVER_WIDTH = 320

        val SEARCH = SearchUiState(
            label = Strings.allianceSearchLabel(),
            query = "",
            state = SearchResultsUiState.Idle(Strings.allianceSearchIdle()),
        )

        val FOUNDING = FoundingUiState(
            label = Strings.allianceFoundLabel(),
            body = Strings.allianceFoundBody(),
            nameLabel = Strings.allianceFoundName(),
            tagLabel = Strings.allianceFoundTag(),
            name = "",
            tag = "",
            action = Strings.allianceFoundAction(),
            nameRefusal = null,
            tagRefusal = null,
            committable = false,
        )

        val FERRO = SearchRowUiState(
            id = AllianceId("ferro"),
            name = TextRes("Ferro Alto"),
            tag = TextRes("FRA"),
            level = Strings.allianceLevelBadge(7),
            seats = Strings.allianceSeatsLine(12, 20),
            action = Strings.allianceRequestSeat(),
            joinable = true,
        )

        // Full, so the row draws with no control at all — a fact worth reading, and not a button
        // that answers no.
        val THRESHOLD = SearchRowUiState(
            id = AllianceId("threshold"),
            name = TextRes("Threshold Compact"),
            tag = TextRes("THR"),
            level = Strings.allianceLevelBadge(3),
            seats = Strings.allianceSeatsLine(8, 8),
            action = Strings.allianceRequestSeat(),
            joinable = false,
        )

        val WAITING = AllianceUiState.Waiting(
            name = TextRes("Ferro Alto"),
            tag = TextRes("FRA"),
            body = Strings.allianceWaitingBody(),
            withdraw = Strings.allianceWithdraw(),
        )

        val HEAD = AllianceHeadUiState(
            name = TextRes("Ferro Alto"),
            tag = TextRes("FRA"),
            level = Strings.allianceLevelBadge(7),
            seats = Strings.allianceSeatsLine(3, 12),
            progress = 62,
        )

        val PENDING = PendingRowUiState(
            id = JoinRequestId("petition-1"),
            name = TextRes("Aphelion Drift"),
            level = Strings.levelBadge(11),
            accept = Strings.allianceAccept(),
            decline = Strings.allianceDecline(),
        )

        val ROSTER = listOf(
            RosterRowUiState(
                id = AllianceMemberId("seat-1"),
                name = TextRes("Dead Reckoning"),
                level = Strings.levelBadge(16),
                role = Strings.allianceRoleFounder(),
                removable = false,
            ),
            RosterRowUiState(
                id = AllianceMemberId("seat-2"),
                name = TextRes("Slow Burn"),
                level = Strings.levelBadge(9),
                role = Strings.allianceRoleAdmin(),
                removable = true,
            ),
            // A plain member draws no role at all, which is correct for nineteen rows in twenty.
            RosterRowUiState(
                id = AllianceMemberId("seat-3"),
                name = TextRes("Hard Vacuum"),
                level = Strings.levelBadge(4),
                role = null,
                removable = true,
            ),
        )

        val POOL = listOf(
            PoolRowUiState(Strings.resourceName(ResourceKind.METAL), TextRes("486,300")),
            PoolRowUiState(Strings.resourceName(ResourceKind.CRYSTAL), TextRes("232,900")),
            PoolRowUiState(Strings.resourceName(ResourceKind.DEUTERIUM), TextRes("71,400")),
        )

        // Each chip states the absolute figure it sends above the share, floored.
        val CHIPS = listOf(
            chip(Strings.allianceShare(10), "4,210 · 960 · 120", confirms = false),
            chip(Strings.allianceShare(25), "10,525 · 2,400 · 300", confirms = false),
            chip(Strings.allianceShare(50), "21,050 · 4,800 · 600", confirms = false),
            chip(Strings.allianceShareAll(), "42,100 · 9,600 · 1,200", confirms = true),
        )

        val CHARTER = ProjectRowUiState(
            project = AllianceProject.CHARTER_EXPANSION,
            name = Strings.allianceProjectCharter(),
            effect = Strings.allianceProjectCharterEffect(),
            cost = TextRes("20,000 Metal · 10,000 Crystal · 5,000 Deuterium"),
            bought = Strings.allianceProjectBought(2),
            action = Strings.allianceProjectBuy(),
            affordable = true,
            shortLine = Strings.allianceProjectShort(),
            buyable = true,
        )

        private fun chip(share: TextRes, figure: String, confirms: Boolean) = ContributeChipUiState(
            share = share,
            figure = TextRes(figure),
            metal = 0,
            crystal = 0,
            deuterium = 0,
            confirms = confirms,
            enabled = true,
        )
    }
}
