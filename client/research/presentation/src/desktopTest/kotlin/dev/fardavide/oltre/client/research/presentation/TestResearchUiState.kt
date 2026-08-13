package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.RowSheetUiState
import dev.fardavide.oltre.client.design.component.SheetAction
import dev.fardavide.oltre.client.design.component.SheetFooter
import dev.fardavide.oltre.client.design.component.SheetLadderStep
import dev.fardavide.oltre.client.design.component.SheetPointer
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.component.figure
import dev.fardavide.oltre.client.design.component.sheetLine
import dev.fardavide.oltre.client.design.component.words
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.LadderShortlist
import dev.fardavide.oltre.core.LevelPurpose
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// The three states the design specifies, written out rather than derived from a GameState so a
// baseline changes only when the *screen* changes. The numbers are the ones the balance really
// produces at the colony each frame describes — durations included, which is why a couple of them
// read one minute longer than the decision sheet's tables: the chip ceils, the tables round.

// Day 1, before either gate. Both branches are legible before a single level exists: six dimmed
// rows spelling out what they want. The flat list is the tech tree, and the second block does the
// same job one branch further out — before the map has shown a single hostile world, the screen has
// said that hostile worlds are a thing you buy your way past, and roughly what that will cost.
internal val beforeTheGateUiState = ResearchUiState(
    // Nothing watched: the watch has its own frames, so these four stay the screens they were.
    watching = null,
    adaptation = lockedLadders(),
    technologies = listOf(
        technologyRow(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = 0,
            effect = photovoltaicsEffect(current = null, next = "+10%"),
            verdict = surplusVerdict(),
            costs = costs(metal = "300", crystal = "150", deuterium = "100", short = null),
            duration = "1h 00m",
            action = ResearchActionUiState.Locked("Requires Robotics 1"),
            watch = null,
        ),
        technologyRow(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = 0,
            effect = extractionEffect(current = null, next = "+8%"),
            verdict = outputVerdict(ResourceKind.METAL, from = 90, to = 97, payback = 1_090.minutes),
            costs = costs(metal = "600", crystal = "400", deuterium = "200", short = null),
            duration = "1h 30m",
            action = ResearchActionUiState.Locked("Requires Robotics 1"),
            watch = null,
        ),
        technologyRow(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = 0,
            effect = enrichmentEffect(current = null, next = "+14%"),
            verdict = outputVerdict(ResourceKind.DEUTERIUM, from = 15, to = 17, payback = 2_500.minutes),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "2h 30m",
            action = ResearchActionUiState.Locked("Requires Extraction 3"),
            watch = null,
        ),
        technologyRow(
            technology = Technology.PROSPECTING,
            name = "Prospecting",
            level = 0,
            effect = prospectingEffect(current = null, next = "+10%"),
            verdict = haulVerdict(from = 60, to = 66),
            costs = costs(metal = "800", crystal = "300", deuterium = "200", short = null),
            duration = "2h 00m",
            action = ResearchActionUiState.Locked("Requires Extraction 1"),
            watch = null,
        ),
    ),
)

// Day 4, Robotics 1. Photovoltaics is affordable, Extraction is short deuterium, Enrichment is
// still dimmed behind its requirement. Nothing is running, so no row carries a countdown — and the
// three ladders are still behind Robotics 2, which is four of six rows dimmed. That is the state a
// new player meets, and it is the strongest argument for putting both branches on one screen: under
// a segmented control these three would be behind a tap, so the player would learn nothing until
// they went looking for something they do not know exists.
internal val nothingRunningUiState = ResearchUiState(
    // Nothing watched: the watch has its own frames, so these four stay the screens they were.
    watching = null,
    adaptation = lockedLadders(),
    technologies = listOf(
        technologyRow(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = 2,
            effect = photovoltaicsEffect(current = "+21%", next = "+33%"),
            verdict = surplusVerdict(),
            costs = costs(metal = "675", crystal = "338", deuterium = "225", short = null),
            duration = "2h 36m",
            action = ResearchActionUiState.Start,
            watch = null,
        ),
        technologyRow(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = 2,
            effect = extractionEffect(current = "+17%", next = "+26%"),
            verdict = outputVerdict(ResourceKind.METAL, from = 320, to = 345, payback = 6_000.minutes),
            costs = costs(metal = "1,350", crystal = "900", deuterium = "450", short = ResourceKind.DEUTERIUM),
            duration = "3h 53m",
            action = ResearchActionUiState.AvailableIn("in 1h 45m"),
            watch = WatchUiState.Offered,
        ),
        technologyRow(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = 0,
            effect = enrichmentEffect(current = null, next = "+14%"),
            verdict = outputVerdict(ResourceKind.DEUTERIUM, from = 29, to = 33, payback = 12_500.minutes),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "2h 10m",
            action = ResearchActionUiState.Locked("Requires Extraction 3"),
            watch = null,
        ),
        technologyRow(
            technology = Technology.PROSPECTING,
            name = "Prospecting",
            level = 0,
            effect = prospectingEffect(current = null, next = "+10%"),
            verdict = haulVerdict(from = 60, to = 66),
            costs = costs(metal = "800", crystal = "300", deuterium = "200", short = null),
            duration = "2h 00m",
            action = ResearchActionUiState.Locked("Requires Extraction 1"),
            watch = null,
        ),
    ),
)

// Day 9, Robotics 4, and the frame the whole decision is for: the gate has just opened, six rows
// fit without a scroll, four of them can be started right now, and starting any one stops the other
// five. The metal-heavy colony can afford Thermal and Atmospheric outright; Gravitic wants 2,400
// metal it does not have, which is the sheet's design showing through — the ladder you can afford
// first is the one your colony is already good at.
internal val gateOpenUiState = ResearchUiState(
    // Nothing watched: the watch has its own frames, so these four stay the screens they were.
    watching = null,
    technologies = listOf(
        technologyRow(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = 2,
            effect = photovoltaicsEffect(current = "+21%", next = "+33%"),
            verdict = surplusVerdict(),
            costs = costs(metal = "675", crystal = "338", deuterium = "225", short = null),
            // 2h 17m, not the design sheet's 2h 35m: at Robotics 4 the balance really produces
            // 137 minutes for Photovoltaics 3. A frame is only worth signing off if its numbers are
            // the ones the game will offer — and this one sits four rows above three ladders
            // reading 3h 02m, which is exactly the comparison the shared slot asks a player to make.
            duration = "2h 17m",
            action = ResearchActionUiState.Start,
            watch = null,
        ),
        technologyRow(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = 4,
            effect = extractionEffect(current = "+36%", next = "+47%"),
            verdict = extractionAtNine(),
            costs = costs(metal = "3,038", crystal = "2,025", deuterium = "1,013", short = ResourceKind.METAL),
            duration = "5h 41m",
            action = ResearchActionUiState.AvailableIn("in 1h 16m"),
            watch = WatchUiState.Offered,
        ),
        technologyRow(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = 0,
            effect = enrichmentEffect(current = null, next = "+14%"),
            verdict = enrichmentAtNine(),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "1h 54m",
            action = ResearchActionUiState.Start,
            watch = null,
        ),
        technologyRow(
            technology = Technology.PROSPECTING,
            name = "Prospecting",
            level = 0,
            effect = prospectingEffect(current = null, next = "+10%"),
            verdict = haulVerdict(from = 60, to = 66),
            costs = costs(metal = "800", crystal = "300", deuterium = "200", short = null),
            duration = "2h 00m",
            action = ResearchActionUiState.Locked("Requires Extraction 1"),
            watch = null,
        ),
    ),
    adaptation = listOf(
        adaptationRow(
            technology = AdaptationTechnology.THERMAL,
            name = "Thermal",
            level = 0,
            effect = bandEffect(current = "−30 … +45", next = "−44 … +59", unit = "°C"),
            costs = costs(metal = "900", crystal = "600", deuterium = "900", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 0, worthTaking = 0),
            action = ResearchActionUiState.Start,
            watch = null,
        ),
        adaptationRow(
            technology = AdaptationTechnology.GRAVITIC,
            name = "Gravitic",
            level = 0,
            effect = bandEffect(current = "0.65 … 1.40", next = "0.60 … 1.52", unit = "g"),
            costs = costs(metal = "2,400", crystal = "900", deuterium = "200", short = ResourceKind.METAL),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 5, worthTaking = 1),
            action = ResearchActionUiState.AvailableIn("in 36m"),
            watch = WatchUiState.Offered,
        ),
        adaptationRow(
            technology = AdaptationTechnology.ATMOSPHERIC,
            name = "Atmospheric",
            level = 0,
            effect = bandEffect(current = "0.5 … 2.6", next = "0.44 … 3.5", unit = "atm"),
            costs = costs(metal = "850", crystal = "1,600", deuterium = "250", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 3, worthTaking = 0),
            action = ResearchActionUiState.Start,
            watch = null,
        ),
    ),
)

// Day 9, Robotics 4. The running row takes the accent border, its target level and finish time,
// the countdown and the bar. The other two carry the time until they can start: Extraction waits
// on deuterium, Enrichment waits on the slot — and the player never has to know which.
//
// The three ladders wait on the slot, so all three read the same "in 1h 13m" the countdown four
// rows up is counting. That is the point of one screen rather than two: the number verifies itself,
// with nothing added to carry it.
internal val oneProjectInFlightUiState = ResearchUiState(
    // Nothing watched: the watch has its own frames, so these four stay the screens they were.
    watching = null,
    adaptation = listOf(
        adaptationRow(
            technology = AdaptationTechnology.THERMAL,
            name = "Thermal",
            level = 0,
            effect = bandEffect(current = "−30 … +45", next = "−44 … +59", unit = "°C"),
            costs = costs(metal = "900", crystal = "600", deuterium = "900", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 0, worthTaking = 0),
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
            watch = null,
        ),
        adaptationRow(
            technology = AdaptationTechnology.GRAVITIC,
            name = "Gravitic",
            level = 0,
            effect = bandEffect(current = "0.65 … 1.40", next = "0.60 … 1.52", unit = "g"),
            costs = costs(metal = "2,400", crystal = "900", deuterium = "200", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 5, worthTaking = 1),
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
            watch = null,
        ),
        adaptationRow(
            technology = AdaptationTechnology.ATMOSPHERIC,
            name = "Atmospheric",
            level = 0,
            effect = bandEffect(current = "0.5 … 2.6", next = "0.44 … 3.5", unit = "atm"),
            costs = costs(metal = "850", crystal = "1,600", deuterium = "250", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 3, worthTaking = 0),
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
            watch = null,
        ),
    ),
    technologies = listOf(
        technologyRow(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = 3,
            effect = photovoltaicsEffect(current = "+33%", next = "+46%"),
            // No verdict at all, and it is the one state where that is right: the decision was made
            // when the player tapped Research, and the slot belongs to the finish line below.
            verdict = null,
            costs = costs(metal = "1,013", crystal = "506", deuterium = "338", short = null),
            duration = "3h 02m",
            action = ResearchActionUiState.Running(
                toLevel = TechLevel(4),
                countdown = "01:12:44",
                progressPercent = 60,
                doneAt = "done 11:23",
            ),
            watch = WatchUiState.Offered,
        ),
        technologyRow(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = 4,
            effect = extractionEffect(current = "+36%", next = "+47%"),
            verdict = extractionAtNine(),
            costs = costs(metal = "3,038", crystal = "2,025", deuterium = "1,013", short = ResourceKind.DEUTERIUM),
            duration = "5h 41m",
            action = ResearchActionUiState.AvailableIn("in 3h 55m"),
            watch = WatchUiState.Offered,
        ),
        technologyRow(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = 0,
            effect = enrichmentEffect(current = null, next = "+14%"),
            verdict = enrichmentAtNine(),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "1h 54m",
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
            watch = null,
        ),
        technologyRow(
            technology = Technology.PROSPECTING,
            name = "Prospecting",
            level = 0,
            effect = prospectingEffect(current = null, next = "+10%"),
            verdict = haulVerdict(from = 60, to = 66),
            costs = costs(metal = "800", crystal = "300", deuterium = "200", short = null),
            duration = "2h 00m",
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
            watch = null,
        ),
    ),
)

// The gate-open frame with the watch on the one ladder the colony is short of metal for. Three
// things are different from the frame above and they are the whole slice: the heading has given its
// trailing slot up to name the watched row, the square is lit, and the card says the instant.
//
// Derived from `gateOpenUiState` rather than written out, unlike every other fixture here, because
// what it asserts is a *difference* — spelled out in full it would be a second copy of eleven rows
// that could drift from the frame it is supposed to be compared against.
internal val watchedUiState = gateOpenUiState.copy(
    watching = "watching Gravitic",
    adaptation = gateOpenUiState.adaptation.map { row ->
        if (row.watch == null) row else row.copy(watch = WatchUiState.Booked("→ affordable 12:55"))
    },
)

// A project in flight with the player asked to be told when it lands, beside two ladders that have
// not been asked about. **The square is the only difference between them**, which is the whole of
// what a subscribed running row shows — its accent line already says when it is due.
internal val subscribedUiState = oneProjectInFlightUiState.copy(
    technologies = oneProjectInFlightUiState.technologies.map { row ->
        if (row.action is ResearchActionUiState.Running) row.copy(watch = WatchUiState.Subscribed) else row
    },
)

// ── The frames that are *of* a sheet ─────────────────────────────────────────────────────────
//
// Three, because a sheet is not one drawing: what it carries depends on which of the row's states
// opened it, and between them these three put every part of the component on a baseline. The row
// frames above carry the shape of a sheet and none of its content, deliberately — the screen never
// draws one, so a fixture that spelled twenty-one of them out would be twenty-one blocks of prose
// no baseline and no assertion ever reads.

// The inert reading, which is the whole reason the sheet exists: a row whose verdict is "nothing"
// has to be able to show its arithmetic, or the player is being told no without being told why. It
// is also the only shape that carries a pointer at a row it can buy instead.
internal val inertSheetUiState = RowSheetUiState(
    name = "Photovoltaics",
    level = 2,
    verdict = surplusVerdict().label,
    lines = listOf(
        sheetLine(
            words("Your plants supply "),
            figure("550"),
            words(" energy. The colony draws "),
            figure("380"),
            words("."),
        ),
        sheetLine(
            words("Photovoltaics multiplies supply, and supply is not what is limiting you. At "),
            figure("+33%"),
            words(" your output does not move."),
        ),
        sheetLine(
            words("It starts to pay when draw passes supply — about "),
            figure("17"),
            words(" more mine levels away."),
        ),
    ),
    ladder = emptyList(),
    pointer = SheetPointer(name = "Enrichment", detail = "LV 1 · back in 138h"),
    footer = SheetFooter(
        costs = costs(metal = "675", crystal = "338", deuterium = "225", short = null),
        duration = "2h 17m",
        action = SheetAction.Live("Research"),
    ),
)

// The gated reading: the one applied row that opens something, so the one whose sheet has a ladder
// on it — and the row is past the rung, which is what puts the aside on it. Its heading carries the
// verdict's *first clause only*, because the second is what the ladder below already says.
internal val gatedSheetUiState = RowSheetUiState(
    name = "Extraction",
    level = 4,
    verdict = extractionAtNine().compactLabel,
    lines = listOf(
        sheetLine(
            words("metal · crystal output: "),
            figure("+36%"),
            words(" → "),
            figure("+47%"),
            words("."),
        ),
        sheetLine(
            words("Your colony makes "),
            figure("730/h"),
            words(" metal and would make "),
            figure("789/h"),
            words("."),
        ),
        sheetLine(
            words("Counted against everything the level costs, you are even after "),
            figure("96h"),
            words("."),
        ),
    ),
    ladder = listOf(SheetLadderStep(level = "LV 3", opens = "Enrichment · you have this", held = true)),
    pointer = null,
    footer = SheetFooter(
        costs = costs(metal = "3,038", crystal = "2,025", deuterium = "1,013", short = ResourceKind.METAL),
        duration = "5h 41m",
        action = SheetAction.Ghost("in 1h 16m"),
    ),
)

// The locked reading, and the argument for making a dimmed row tappable at all: it has no price to
// show and nothing to press, so what it has instead is what the band would become and one line
// naming the row that would open it.
internal val lockedSheetUiState = RowSheetUiState(
    name = "Thermal",
    level = 0,
    verdict = "Requires Robotics 2",
    lines = listOf(
        sheetLine(
            words("°C tolerance: "),
            figure("−30 … +45"),
            words(" → "),
            figure("−44 … +59"),
            words("."),
        ),
        sheetLine(words("Requires "), figure("Robotics 2"), words(".")),
    ),
    ladder = emptyList(),
    pointer = SheetPointer(name = "Robotics Factory", detail = "LV 1 → 2 · 1h 08m"),
    footer = null,
)

// All three behind the one gate, which is what stops any of them deciding the first ladder for the
// player. A locked row is name, level, requirement **and verdict**: the tolerance band it used to
// carry is gone into the sheet, and what stands in that slot is the count of worlds the level would
// reach — which is the only thing that answers whether the gate is worth pushing for. The band is
// still taught where it always was, on a blocked world on the Galaxy tab, attached to a real
// reading rather than to an idea.
private fun lockedLadders(): List<AdaptationRowUiState> = listOf(
    adaptationRow(
        technology = AdaptationTechnology.THERMAL,
        name = "Thermal",
        level = 0,
        effect = bandEffect(current = "−30 … +45", next = "−44 … +59", unit = "°C"),
        costs = costs(metal = "900", crystal = "600", deuterium = "900", short = null),
        duration = "3h 02m",
        shortlist = shortlist(unlocks = 0, worthTaking = 0),
        action = ResearchActionUiState.Locked("Requires Robotics 2"),
        watch = null,
    ),
    adaptationRow(
        technology = AdaptationTechnology.GRAVITIC,
        name = "Gravitic",
        level = 0,
        effect = bandEffect(current = "0.65 … 1.40", next = "0.60 … 1.52", unit = "g"),
        costs = costs(metal = "2,400", crystal = "900", deuterium = "200", short = null),
        duration = "3h 02m",
        shortlist = shortlist(unlocks = 5, worthTaking = 1),
        action = ResearchActionUiState.Locked("Requires Robotics 2"),
        watch = null,
    ),
    adaptationRow(
        technology = AdaptationTechnology.ATMOSPHERIC,
        name = "Atmospheric",
        level = 0,
        effect = bandEffect(current = "0.5 … 2.6", next = "0.44 … 3.5", unit = "atm"),
        costs = costs(metal = "850", crystal = "1,600", deuterium = "250", short = null),
        duration = "3h 02m",
        shortlist = shortlist(unlocks = 3, worthTaking = 0),
        action = ResearchActionUiState.Locked("Requires Robotics 2"),
        watch = null,
    ),
)

// A factory rather than twelve more literals, and the sheet is why: every row now carries one, and
// a hand-written sheet on a row no baseline opens would be a copy of a rule with nothing checking
// it. The level arrives as an Int because the sheet states it as one.
private fun technologyRow(
    technology: Technology,
    name: String,
    level: Int,
    effect: EffectUiState,
    verdict: VerdictUiState?,
    costs: List<CostChipUiState>,
    duration: String,
    action: ResearchActionUiState,
    watch: WatchUiState?,
): TechnologyRowUiState = TechnologyRowUiState(
    technology = technology,
    name = name,
    level = TechLevel(level),
    effect = effect,
    verdict = verdict,
    costs = costs,
    duration = duration,
    action = action,
    sheet = rowSheet(
        name = name,
        level = level,
        verdict = verdict,
        action = action,
        costs = costs,
        duration = duration,
    ),
    watch = watch,
    // No frame in this file is the launch that found something finished — the sweep has its own,
    // and a band baked into one of these would assert an animation rather than a screen.
    finishedWhileAway = false,
)

// The ladder's verdict is not passed in: it *is* the shortlist, so taking it from anywhere else
// would let a frame show two different sentences about one count.
private fun adaptationRow(
    technology: AdaptationTechnology,
    name: String,
    level: Int,
    effect: EffectUiState,
    costs: List<CostChipUiState>,
    duration: String,
    shortlist: ShortlistUiState,
    action: ResearchActionUiState,
    watch: WatchUiState?,
): AdaptationRowUiState = AdaptationRowUiState(
    technology = technology,
    name = name,
    level = TechLevel(level),
    effect = effect,
    verdict = shortlist.toVerdictUiState().takeIf { action !is ResearchActionUiState.Running },
    costs = costs,
    duration = duration,
    action = action,
    shortlist = shortlist,
    sheet = rowSheet(
        name = name,
        level = level,
        verdict = shortlist.toVerdictUiState(),
        action = action,
        costs = costs,
        duration = duration,
    ),
    watch = watch,
    finishedWhileAway = false,
)

// The heading and the footer of the sheet a row opens, and none of its prose — see the note above
// the three sheet frames. Those two are the parts that are facts about the *row* rather than about
// the sheet: the sentence the player has just read, which on a locked row is its requirement and on
// a running one its finish line, and the price with the row's own action beside it.
private fun rowSheet(
    name: String,
    level: Int,
    verdict: VerdictUiState?,
    action: ResearchActionUiState,
    costs: List<CostChipUiState>,
    duration: String,
): RowSheetUiState = RowSheetUiState(
    name = name,
    level = level,
    verdict = when (action) {
        is ResearchActionUiState.Locked -> action.reason
        is ResearchActionUiState.Running -> "→ LV ${action.toLevel.value} · ${action.doneAt}"
        ResearchActionUiState.Start,
        is ResearchActionUiState.AvailableIn,
        -> verdict?.label.orEmpty()
    },
    lines = emptyList(),
    ladder = emptyList(),
    pointer = null,
    footer = when (action) {
        is ResearchActionUiState.Locked,
        is ResearchActionUiState.Running,
        -> null
        ResearchActionUiState.Start ->
            SheetFooter(costs = costs, duration = duration, action = SheetAction.Live("Research"))
        is ResearchActionUiState.AvailableIn ->
            SheetFooter(costs = costs, duration = duration, action = SheetAction.Ghost(action.label))
    },
)

// The two rows a day-nine colony has priced twice over, named because two frames show the same
// colony at the same moment and a second copy of these figures could drift from the first.
private fun extractionAtNine(): VerdictUiState =
    outputVerdict(ResourceKind.METAL, from = 730, to = 789, payback = 5_787.minutes)

private fun enrichmentAtNine(): VerdictUiState =
    outputVerdict(ResourceKind.DEUTERIUM, from = 45, to = 51, payback = 8_333.minutes)

// The figures are frozen by hand like every other number in this file; the sentence around them
// comes from the mapper, for the reason `shortlist` below gives.
private fun outputVerdict(kind: ResourceKind, from: Long, to: Long, payback: Duration): VerdictUiState =
    checkNotNull(
        LevelPurpose.Output(kind = kind, from = from, to = to, payback = payback)
            .toVerdictUiState(throttled = false),
    )

// Every colony in this file is in surplus, so Photovoltaics is worth nothing on all four frames.
// The two members are what it would take to stop being true and neither reaches the row's line —
// the verdict is the one in the game with no figures in it at all.
private fun surplusVerdict(): VerdictUiState =
    checkNotNull(
        LevelPurpose.Inert(suppliesMore = 5, mineLevelsSpare = 17)
            .toVerdictUiState(throttled = false),
    )

// The counts are frozen by hand like every other number in this file, so a baseline moves only when
// the *screen* moves. The sentence around them is not — it comes from the mapper, because the one
// time a fixture in this repo wrote its own strings they drifted from the mapper's formatting within
// the hour and the images asserted text the app would never produce.
//
// Three deliberately different answers across the three ladders, so one frame carries every shape
// the line has: a pair worth reading, a run of unlocks with none over the bar, and a zero. The zero
// is the point of the row existing at all — "Thermal unlocks nothing" is what makes the other two
// mean something.
private fun shortlist(unlocks: Int, worthTaking: Int) = LadderShortlist(
    // Neither is drawn: the line names no technology and no level, both of which are already on the
    // row above it. They are here because `LadderShortlist` is core's type and states the whole
    // answer rather than the half this screen renders.
    technology = AdaptationTechnology.THERMAL,
    nextLevel = TechLevel(1),
    unlocks = unlocks,
    worthTaking = worthTaking,
).toUiState()

// The unit is the compact form of itself: a band line is digits, units and relations, so unlike the
// applied line there is nothing in it a narrower window could drop.
private fun bandEffect(current: String, next: String, unit: String) = EffectUiState(
    current = current,
    next = next,
    subject = unit,
)

private fun photovoltaicsEffect(current: String?, next: String) = EffectUiState(
    current = current,
    next = next,
    subject = "Solar Plant output",
)

private fun extractionEffect(current: String?, next: String) = EffectUiState(
    current = current,
    next = next,
    subject = "metal · crystal output",
)

private fun enrichmentEffect(current: String?, next: String) = EffectUiState(
    current = current,
    next = next,
    subject = "deuterium output",
)

// The fourth row's subject is the one on this screen that names no resource: what it multiplies is
// what a hull lifts, not what the colony makes. PLACEHOLDER copy — see `ResearchUiState`.
private fun prospectingEffect(current: String?, next: String) = EffectUiState(
    current = current,
    next = next,
    subject = "what a fleet lifts",
)

// Quoted per hull per hour in priced units, which is the only figure that is a property of the
// technology rather than of a fleet or a target.
private fun haulVerdict(from: Long, to: Long): VerdictUiState =
    checkNotNull(LevelPurpose.Haul(from = from, to = to).toVerdictUiState(throttled = false))

private fun costs(
    metal: String,
    crystal: String,
    deuterium: String,
    short: ResourceKind?,
): List<CostChipUiState> = listOf(
    CostChipUiState(kind = ResourceKind.METAL, amount = metal, short = short == ResourceKind.METAL),
    CostChipUiState(kind = ResourceKind.CRYSTAL, amount = crystal, short = short == ResourceKind.CRYSTAL),
    CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = deuterium, short = short == ResourceKind.DEUTERIUM),
)
