package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.LadderShortlist
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology

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
        TechnologyRowUiState(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = TechLevel(0),
            effect = photovoltaicsEffect(current = null, next = "+10%"),
            costs = costs(metal = "300", crystal = "150", deuterium = "100", short = null),
            duration = "1h 00m",
            action = ResearchActionUiState.Locked("Requires Robotics 1"),
            watch = null,
            finishedWhileAway = false,
        ),
        TechnologyRowUiState(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = TechLevel(0),
            effect = extractionEffect(current = null, next = "+8%"),
            costs = costs(metal = "600", crystal = "400", deuterium = "200", short = null),
            duration = "1h 30m",
            action = ResearchActionUiState.Locked("Requires Robotics 1"),
            watch = null,
            finishedWhileAway = false,
        ),
        TechnologyRowUiState(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = TechLevel(0),
            effect = enrichmentEffect(current = null, next = "+14%"),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "2h 30m",
            action = ResearchActionUiState.Locked("Requires Extraction 3"),
            watch = null,
            finishedWhileAway = false,
        ),
    ),
)

// Day 4, Robotics 2. Photovoltaics is affordable, Extraction is short deuterium, Enrichment is
// still dimmed behind its requirement. Nothing is running, so no row carries a countdown — and the
// three ladders are still behind Robotics 4, which is four of six rows dimmed. That is the state a
// new player meets, and it is the strongest argument for putting both branches on one screen: under
// a segmented control these three would be behind a tap, so the player would learn nothing until
// they went looking for something they do not know exists.
internal val nothingRunningUiState = ResearchUiState(
    // Nothing watched: the watch has its own frames, so these four stay the screens they were.
    watching = null,
    adaptation = lockedLadders(),
    technologies = listOf(
        TechnologyRowUiState(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = TechLevel(2),
            effect = photovoltaicsEffect(current = "+21%", next = "+33%"),
            costs = costs(metal = "675", crystal = "338", deuterium = "225", short = null),
            duration = "2h 36m",
            action = ResearchActionUiState.Start,
            watch = null,
            finishedWhileAway = false,
        ),
        TechnologyRowUiState(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = TechLevel(2),
            effect = extractionEffect(current = "+17%", next = "+26%"),
            costs = costs(metal = "1,350", crystal = "900", deuterium = "450", short = ResourceKind.DEUTERIUM),
            duration = "3h 53m",
            action = ResearchActionUiState.AvailableIn("in 1h 45m"),
            watch = WatchUiState.Offered,
            finishedWhileAway = false,
        ),
        TechnologyRowUiState(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = TechLevel(0),
            effect = enrichmentEffect(current = null, next = "+14%"),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "2h 10m",
            action = ResearchActionUiState.Locked("Requires Extraction 3"),
            watch = null,
            finishedWhileAway = false,
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
        TechnologyRowUiState(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = TechLevel(2),
            effect = photovoltaicsEffect(current = "+21%", next = "+33%"),
            costs = costs(metal = "675", crystal = "338", deuterium = "225", short = null),
            // 2h 17m, not the design sheet's 2h 35m: at Robotics 4 the balance really produces
            // 137 minutes for Photovoltaics 3. A frame is only worth signing off if its numbers are
            // the ones the game will offer — and this one sits four rows above three ladders
            // reading 3h 02m, which is exactly the comparison the shared slot asks a player to make.
            duration = "2h 17m",
            action = ResearchActionUiState.Start,
            watch = null,
            finishedWhileAway = false,
        ),
        TechnologyRowUiState(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = TechLevel(4),
            effect = extractionEffect(current = "+36%", next = "+47%"),
            costs = costs(metal = "3,038", crystal = "2,025", deuterium = "1,013", short = ResourceKind.METAL),
            duration = "5h 41m",
            action = ResearchActionUiState.AvailableIn("in 1h 16m"),
            watch = WatchUiState.Offered,
            finishedWhileAway = false,
        ),
        TechnologyRowUiState(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = TechLevel(0),
            effect = enrichmentEffect(current = null, next = "+14%"),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "1h 54m",
            action = ResearchActionUiState.Start,
            watch = null,
            finishedWhileAway = false,
        ),
    ),
    adaptation = listOf(
        AdaptationRowUiState(
            technology = AdaptationTechnology.THERMAL,
            name = "Thermal",
            level = TechLevel(0),
            effect = bandEffect(current = "−30 … +45", next = "−44 … +59", unit = "°C"),
            costs = costs(metal = "900", crystal = "600", deuterium = "900", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 0, worthTaking = 0),
            action = ResearchActionUiState.Start,
            watch = null,
            finishedWhileAway = false,
        ),
        AdaptationRowUiState(
            technology = AdaptationTechnology.GRAVITIC,
            name = "Gravitic",
            level = TechLevel(0),
            effect = bandEffect(current = "0.65 … 1.40", next = "0.60 … 1.52", unit = "g"),
            costs = costs(metal = "2,400", crystal = "900", deuterium = "200", short = ResourceKind.METAL),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 5, worthTaking = 1),
            action = ResearchActionUiState.AvailableIn("in 36m"),
            watch = WatchUiState.Offered,
            finishedWhileAway = false,
        ),
        AdaptationRowUiState(
            technology = AdaptationTechnology.ATMOSPHERIC,
            name = "Atmospheric",
            level = TechLevel(0),
            effect = bandEffect(current = "0.5 … 2.6", next = "0.44 … 3.5", unit = "atm"),
            costs = costs(metal = "850", crystal = "1,600", deuterium = "250", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 3, worthTaking = 0),
            action = ResearchActionUiState.Start,
            watch = null,
            finishedWhileAway = false,
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
        AdaptationRowUiState(
            technology = AdaptationTechnology.THERMAL,
            name = "Thermal",
            level = TechLevel(0),
            effect = bandEffect(current = "−30 … +45", next = "−44 … +59", unit = "°C"),
            costs = costs(metal = "900", crystal = "600", deuterium = "900", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 0, worthTaking = 0),
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
            watch = null,
            finishedWhileAway = false,
        ),
        AdaptationRowUiState(
            technology = AdaptationTechnology.GRAVITIC,
            name = "Gravitic",
            level = TechLevel(0),
            effect = bandEffect(current = "0.65 … 1.40", next = "0.60 … 1.52", unit = "g"),
            costs = costs(metal = "2,400", crystal = "900", deuterium = "200", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 5, worthTaking = 1),
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
            watch = null,
            finishedWhileAway = false,
        ),
        AdaptationRowUiState(
            technology = AdaptationTechnology.ATMOSPHERIC,
            name = "Atmospheric",
            level = TechLevel(0),
            effect = bandEffect(current = "0.5 … 2.6", next = "0.44 … 3.5", unit = "atm"),
            costs = costs(metal = "850", crystal = "1,600", deuterium = "250", short = null),
            duration = "3h 02m",
            shortlist = shortlist(unlocks = 3, worthTaking = 0),
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
            watch = null,
            finishedWhileAway = false,
        ),
    ),
    technologies = listOf(
        TechnologyRowUiState(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = TechLevel(3),
            effect = photovoltaicsEffect(current = "+33%", next = "+46%"),
            costs = costs(metal = "1,013", crystal = "506", deuterium = "338", short = null),
            duration = "3h 02m",
            action = ResearchActionUiState.Running(
                toLevel = TechLevel(4),
                countdown = "01:12:44",
                progressPercent = 60,
                doneAt = "done 11:23",
            ),
            watch = null,
            finishedWhileAway = false,
        ),
        TechnologyRowUiState(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = TechLevel(4),
            effect = extractionEffect(current = "+36%", next = "+47%"),
            costs = costs(metal = "3,038", crystal = "2,025", deuterium = "1,013", short = ResourceKind.DEUTERIUM),
            duration = "5h 41m",
            action = ResearchActionUiState.AvailableIn("in 3h 55m"),
            watch = WatchUiState.Offered,
            finishedWhileAway = false,
        ),
        TechnologyRowUiState(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = TechLevel(0),
            effect = enrichmentEffect(current = null, next = "+14%"),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "1h 54m",
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
            watch = null,
            finishedWhileAway = false,
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

// All three behind the one gate, which is what stops any of them deciding the first ladder for the
// player. A locked row is name, level and requirement — no band line, because the row does not
// explain what a tolerance band is before you can buy one. The place that teaches the concept is a
// blocked world on the Galaxy tab, where it is attached to a real reading rather than to an idea.
private fun lockedLadders(): List<AdaptationRowUiState> = listOf(
    AdaptationRowUiState(
        technology = AdaptationTechnology.THERMAL,
        name = "Thermal",
        level = TechLevel(0),
        effect = bandEffect(current = "−30 … +45", next = "−44 … +59", unit = "°C"),
        costs = costs(metal = "900", crystal = "600", deuterium = "900", short = null),
        duration = "3h 02m",
        shortlist = shortlist(unlocks = 0, worthTaking = 0),
        action = ResearchActionUiState.Locked("Requires Robotics 4"),
        watch = null,
        finishedWhileAway = false,
    ),
    AdaptationRowUiState(
        technology = AdaptationTechnology.GRAVITIC,
        name = "Gravitic",
        level = TechLevel(0),
        effect = bandEffect(current = "0.65 … 1.40", next = "0.60 … 1.52", unit = "g"),
        costs = costs(metal = "2,400", crystal = "900", deuterium = "200", short = null),
        duration = "3h 02m",
        shortlist = shortlist(unlocks = 5, worthTaking = 1),
        action = ResearchActionUiState.Locked("Requires Robotics 4"),
        watch = null,
        finishedWhileAway = false,
    ),
    AdaptationRowUiState(
        technology = AdaptationTechnology.ATMOSPHERIC,
        name = "Atmospheric",
        level = TechLevel(0),
        effect = bandEffect(current = "0.5 … 2.6", next = "0.44 … 3.5", unit = "atm"),
        costs = costs(metal = "850", crystal = "1,600", deuterium = "250", short = null),
        duration = "3h 02m",
        shortlist = shortlist(unlocks = 3, worthTaking = 0),
        action = ResearchActionUiState.Locked("Requires Robotics 4"),
        watch = null,
        finishedWhileAway = false,
    ),
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
    compactSubject = unit,
)

private fun photovoltaicsEffect(current: String?, next: String) = EffectUiState(
    current = current,
    next = next,
    subject = "Solar Plant output",
    compactSubject = "Solar Plant",
)

private fun extractionEffect(current: String?, next: String) = EffectUiState(
    current = current,
    next = next,
    subject = "metal · crystal output",
    compactSubject = "metal · crystal",
)

private fun enrichmentEffect(current: String?, next: String) = EffectUiState(
    current = current,
    next = next,
    subject = "deuterium output",
    compactSubject = "deuterium",
)

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
