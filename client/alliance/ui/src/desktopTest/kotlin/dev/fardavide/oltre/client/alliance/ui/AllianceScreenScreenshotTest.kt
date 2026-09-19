package dev.fardavide.oltre.client.alliance.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.design.core.OltreMotion
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
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
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

    // **The state a new player actually opens the tab in**, and until now nothing had drawn it: the
    // colony cannot cover 200,000 metal on day one, so the cost goes red, a line says the colony is
    // short, and there is no control to press. `alliance-sheet.md` §7 — *absent, never greyed*.
    @Test
    fun `founding one the colony cannot pay for`() {
        capture(
            "alliance_founding_short",
            AllianceUiState.Seeking(
                search = SEARCH.copy(state = SearchResultsUiState.Idle(Strings.allianceSearchIdle())),
                founding = FOUNDING.copy(
                    name = "Ferro Alto",
                    tag = "FRA",
                    affordable = false,
                    committable = false,
                ),
            ),
        )
    }

    // The other end of the same block, and the only frame in which *Found it* exists at all: both
    // fields hold something the contract accepts and the colony can cover the price.
    @Test
    fun `founding one that is ready to commit`() {
        capture(
            "alliance_founding_ready",
            AllianceUiState.Seeking(
                search = SEARCH.copy(state = SearchResultsUiState.Idle(Strings.allianceSearchIdle())),
                founding = FOUNDING.copy(name = "Ferro Alto", tag = "FRA", committable = true),
            ),
        )
    }

    @Test
    fun `waiting on an answer`() {
        capture("alliance_waiting", WAITING)
    }

    // **320dp, which is the width the design measures against** — a Slide Over pane, and the one the
    // alliance sheet names as the constraint the whole destination was chosen under. The search and
    // the founding block are the two faces with fields on them, so they are where a narrow window
    // shows first.
    @Test
    fun `looking for one, in a Slide Over window`() {
        capture(
            "alliance_seeking_slide_over",
            seeking(SearchResultsUiState.Results(listOf(FERRO, THRESHOLD))),
            width = SLIDE_OVER_WIDTH,
            height = 900,
        )
    }

    @Test
    fun `waiting on an answer, in a Slide Over window`() {
        capture("alliance_waiting_slide_over", WAITING, width = SLIDE_OVER_WIDTH)
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
    //
    // **Both rows since `#168`, because affordability is per row and a pool short of everything is
    // the honest picture of this pool.** 4,210 metal covers neither entry.
    @Test
    fun `a project the pool cannot cover`() {
        capture(
            "alliance_project_short",
            enlisted(
                projects = listOf(
                    CHARTER.copy(affordable = false, buyable = false),
                    LOGISTICS.copy(affordable = false, buyable = false),
                ),
                pool = listOf(
                    PoolRowUiState(Strings.resourceName(ResourceKind.METAL), TextRes("4,210")),
                    PoolRowUiState(Strings.resourceName(ResourceKind.CRYSTAL), TextRes("960")),
                    PoolRowUiState(Strings.resourceName(ResourceKind.DEUTERIUM), TextRes("120")),
                ),
            ),
            height = 1_500,
        )
    }

    // **The state the second entry invented: one row offering and the next refusing, adjacent.** A
    // catalogue of one could never draw it, and it is the frame that proves `affordable` is read per
    // row rather than once for the panel — the charter's control is there, the logistics' is not,
    // and the red cost under the second says which.
    @Test
    fun `a pool that covers one entry and not the next`() {
        capture(
            "alliance_project_mixed",
            enlisted(
                projects = listOf(CHARTER, LOGISTICS.copy(affordable = false, buyable = false)),
                pool = listOf(
                    PoolRowUiState(Strings.resourceName(ResourceKind.METAL), TextRes("24,800")),
                    PoolRowUiState(Strings.resourceName(ResourceKind.CRYSTAL), TextRes("11,300")),
                    PoolRowUiState(Strings.resourceName(ResourceKind.DEUTERIUM), TextRes("6,100")),
                ),
            ),
            height = 1_500,
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
                // No arrows anywhere: a member's roster is plainly a readout, with no absence to
                // explain because they never saw one.
                roster = ROSTER.map { it.copy(pressable = false) },
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

    // A colony with almost nothing in it: three stops floor to zero and do not press, which is
    // `core`'s own `NothingOffered` refusal arriving one layer earlier — and the picked one is among
    // them, so the control is absent and the line under the ladder says which fact that is.
    @Test
    fun `a colony with nothing much to give`() {
        capture(
            "alliance_ladder_short",
            enlisted().let { face ->
                face.copy(
                    treasury = face.treasury.copy(
                        contribute = ContributeUiState(
                            shares = ladder(
                                selected = ContributeShare.A_TENTH,
                                enabled = { it == ContributeShare.EVERYTHING },
                            ),
                            action = ContributeActionUiState.Short(Strings.allianceShareRoundsToNothing()),
                        ),
                    ),
                )
            },
            height = 1_300,
        )
    }

    // **The frames the screen reaches by *changing*, rather than by being drawn once.** Every other
    // capture here composes the screen one time; a destination a player actually uses recomposes —
    // they type, they are answered, they tap, they land in an alliance — and a composable invoked
    // once has never had to decide whether to skip.
    //
    // This walks the whole arc in one composition: nothing typed, then asking, then results, then
    // enlisted. The last frame is what is pinned; the ones before it are what the walk is for.
    @Test
    fun `the arc from an empty search to a seat`() {
        runDesktopComposeUiTest(width = PHONE_WIDTH, height = 1_400) {
            mainClock.autoAdvance = false
            var state by mutableStateOf<AllianceUiState>(seeking(SearchResultsUiState.Idle(Strings.allianceSearchIdle())))
            var asking by mutableStateOf<ContributeConfirmUiState?>(null)
            var refused by mutableStateOf<RefusalUiState?>(null)
            setContent {
                OltreTheme(translations = English) {
                    Surface {
                        // **Called with only what it needs**, so the screen's own defaults are
                        // exercised as well as the arguments: a composable is only ever asked to
                        // skip when something it was handed has changed and something else has not.
                        AllianceScreen(
                            state = state,
                            actions = AllianceActions(),
                            confirm = asking,
                            refusal = refused,
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)

            for (next in walk()) {
                state = next
                mainClock.advanceTimeBy(SETTLED_MILLIS)
            }
            // The two things that come and go over the top of a face that is not changing — which is
            // the other half of what recomposition is for.
            for (question in listOf(CONFIRM, null, CONFIRM)) {
                asking = question
                mainClock.advanceTimeBy(SETTLED_MILLIS)
            }
            for (refusal in listOf(REFUSAL, null)) {
                refused = refusal
                mainClock.advanceTimeBy(SETTLED_MILLIS)
            }
            asking = null
            mainClock.advanceTimeBy(SETTLED_MILLIS)

            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/alliance_recomposed.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private fun walk(): List<AllianceUiState> = listOf(
        seeking(SearchResultsUiState.Asking(Strings.allianceAsking())),
        seeking(SearchResultsUiState.Empty(Strings.allianceSearchEmpty(TextRes("Aphelion")))),
        seeking(SearchResultsUiState.Results(listOf(FERRO, THRESHOLD))),
        AllianceUiState.Held,
        AllianceUiState.Asking,
        WAITING,
        enlisted(projects = emptyList()),
        enlisted(),
    )

    // **The ladder mid-gesture, which is the only motion this destination has** — and the only way to
    // photograph that it is a motion at all. A cross-fade and a snap are identical once they have
    // settled, so a capture taken after `SETTLED_MILLIS` would pass whether or not the fills animate;
    // this one stops the clock halfway through the 210ms, where a snapped stop would already be fully
    // accent and a settling one is part way there.
    @Test
    fun `picking a stop crosses rather than snaps`() {
        runDesktopComposeUiTest(width = PHONE_WIDTH, height = 1_300) {
            mainClock.autoAdvance = false
            var state by mutableStateOf<AllianceUiState>(enlisted())
            setContent {
                OltreTheme(translations = English) {
                    Surface { AllianceScreen(state = state, actions = AllianceActions()) }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)

            // The half that a finger does: a different stop, and a basket that reflows under it.
            state = enlisted().let { face ->
                face.copy(
                    treasury = face.treasury.copy(
                        contribute = ContributeUiState(
                            shares = ladder(selected = ContributeShare.A_HALF),
                            action = ContributeActionUiState.Offered(
                                basket = TextRes("21,050 Metal · 4,800 Crystal · 600 Deuterium"),
                                action = Strings.allianceContributeAction(TextRes("Ferro Alto")),
                                metal = 21_050,
                                crystal = 4_800,
                                deuterium = 600,
                                confirms = false,
                            ),
                        ),
                    ),
                )
            }
            mainClock.advanceTimeBy(HALF_A_SWITCH_MILLIS)

            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/alliance_ladder_crossing.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // **The screen as the shell actually calls it** — with a hoisted scroll state and a modifier,
    // which is what a destination inside `MainScaffold` gets. Every other capture here leans on the
    // defaults, so without this one half of the screen's own parameter list is never supplied.
    @Test
    fun `drawn the way the scaffold draws it`() {
        runDesktopComposeUiTest(width = PHONE_WIDTH, height = 1_400) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = English) {
                    Surface {
                        AllianceScreen(
                            state = enlisted(),
                            actions = AllianceActions(),
                            confirm = null,
                            refusal = null,
                            scrollState = rememberScrollState(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/alliance_scaffolded.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
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
        // The catalogue as it actually ships since `#168`: two rows, in the enum's own order.
        projects: List<ProjectRowUiState> = listOf(CHARTER, LOGISTICS),
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
            contribute = CONTRIBUTE,
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

        // Halfway through `OltreMotion.SWITCH_MILLIS`, which is where a cross-fade and a snap look
        // different. Named rather than inline so the frame is understood as *mid-gesture* rather than
        // as a number somebody picked.
        const val HALF_A_SWITCH_MILLIS = OltreMotion.SWITCH_MILLIS / 2L

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
            tagRule = Strings.allianceFoundTagRule(),
            // The real price, so the frame photographs the figure the game charges.
            cost = Strings.allianceFoundPrice(
                Strings.clauses(
                    listOf(
                        Strings.amountOfResource(Strings.groupedNumber(200_000), ResourceKind.METAL),
                        Strings.amountOfResource(Strings.groupedNumber(100_000), ResourceKind.CRYSTAL),
                        Strings.amountOfResource(Strings.groupedNumber(50_000), ResourceKind.DEUTERIUM),
                    ),
                ),
            ),
            affordable = true,
            shortLine = Strings.allianceFoundShort(),
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

        // A founder's roster: every row but their own carries the `→`.
        val ROSTER = listOf(
            RosterRowUiState(
                id = AllianceMemberId("seat-1"),
                name = TextRes("Dead Reckoning"),
                level = Strings.levelBadge(16),
                role = Strings.allianceRoleFounder(),
                mark = PlayerMark.Preset(MarkPreset.THRESHOLD),
                pressable = false,
            ),
            RosterRowUiState(
                id = AllianceMemberId("seat-2"),
                name = TextRes("Slow Burn"),
                level = Strings.levelBadge(9),
                role = Strings.allianceRoleAdmin(),
                mark = PlayerMark.Preset(MarkPreset.APHELION),
                pressable = true,
            ),
            // A plain member draws no role at all, which is correct for nineteen rows in twenty —
            // and no mark either, so the baseline records what `DEFAULT_PLAYER_MARK` draws.
            RosterRowUiState(
                id = AllianceMemberId("seat-3"),
                name = TextRes("Hard Vacuum"),
                level = Strings.levelBadge(4),
                role = null,
                mark = null,
                pressable = true,
            ),
        )

        val POOL = listOf(
            PoolRowUiState(Strings.resourceName(ResourceKind.METAL), TextRes("486,300")),
            PoolRowUiState(Strings.resourceName(ResourceKind.CRYSTAL), TextRes("232,900")),
            PoolRowUiState(Strings.resourceName(ResourceKind.DEUTERIUM), TextRes("71,400")),
        )

        // The ladder picks; the control under it states the basket in full and names where it goes.
        val CONTRIBUTE = ContributeUiState(
            shares = ladder(selected = ContributeShare.A_TENTH),
            action = ContributeActionUiState.Offered(
                basket = TextRes("4,210 Metal · 960 Crystal · 120 Deuterium"),
                action = Strings.allianceContributeAction(TextRes("Ferro Alto")),
                metal = 4_210,
                crystal = 960,
                deuterium = 120,
                confirms = false,
            ),
        )

        val CONFIRM = ContributeConfirmUiState(
            title = Strings.allianceConfirmAllTitle(),
            body = Strings.allianceConfirmAllBody(),
            confirm = Strings.allianceConfirmAllAction(),
            keep = Strings.allianceConfirmAllKeep(),
        )

        val REFUSAL = RefusalUiState(
            lead = Strings.refusedContributionLead(),
            body = Strings.refusedContributionBody(),
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

        // **The second entry, and it carries no tracked word**, which is the pairing worth
        // photographing: the catalogue that ships holds a row that has been bought before beside one
        // that has not, and `bought` being null is the only difference between them.
        val LOGISTICS = ProjectRowUiState(
            project = AllianceProject.SHARED_LOGISTICS,
            name = Strings.allianceProjectLogistics(),
            effect = Strings.allianceProjectLogisticsEffect(),
            cost = TextRes("40,000 Metal · 20,000 Crystal · 10,000 Deuterium"),
            bought = null,
            action = Strings.allianceProjectBuy(),
            affordable = true,
            shortLine = Strings.allianceProjectShort(),
            buyable = true,
        )

        // The four stops, with one of them lit. `enabled` is a parameter because the one frame that
        // is about an almost-empty colony needs three of them dark.
        private fun ladder(
            selected: ContributeShare,
            enabled: (ContributeShare) -> Boolean = { true },
        ): List<ContributeShareUiState> = ContributeShare.entries.map { share ->
            ContributeShareUiState(
                share = share,
                label = when (share) {
                    ContributeShare.A_TENTH -> Strings.allianceShare(10)
                    ContributeShare.A_QUARTER -> Strings.allianceShare(25)
                    ContributeShare.A_HALF -> Strings.allianceShare(50)
                    ContributeShare.EVERYTHING -> Strings.allianceShareAll()
                },
                selected = share == selected,
                enabled = enabled(share),
            )
        }
    }
}
