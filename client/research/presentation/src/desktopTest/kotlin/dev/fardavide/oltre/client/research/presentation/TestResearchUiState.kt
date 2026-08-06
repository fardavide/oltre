package dev.fardavide.oltre.client.research.presentation

import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology

// The three states the design specifies, written out rather than derived from a GameState so a
// baseline changes only when the *screen* changes. The numbers are the ones the balance really
// produces at the colony each frame describes — durations included, which is why a couple of them
// read one minute longer than the decision sheet's tables: the chip ceils, the tables round.

// Day 1, before the gate. The branch is legible before a single level exists: three dimmed rows
// spelling out what they want. The flat list is the tech tree.
internal val beforeTheGateUiState = ResearchUiState(
    technologies = listOf(
        TechnologyRowUiState(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = TechLevel(0),
            effect = photovoltaicsEffect(current = null, next = "+10%"),
            costs = costs(metal = "300", crystal = "150", deuterium = "100", short = null),
            duration = "1h 00m",
            action = ResearchActionUiState.Locked("Requires Robotics 1"),
        ),
        TechnologyRowUiState(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = TechLevel(0),
            effect = extractionEffect(current = null, next = "+8%"),
            costs = costs(metal = "600", crystal = "400", deuterium = "200", short = null),
            duration = "1h 30m",
            action = ResearchActionUiState.Locked("Requires Robotics 1"),
        ),
        TechnologyRowUiState(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = TechLevel(0),
            effect = enrichmentEffect(current = null, next = "+14%"),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "2h 30m",
            action = ResearchActionUiState.Locked("Requires Extraction 3"),
        ),
    ),
)

// Day 4, Robotics 2. Photovoltaics is affordable, Extraction is short deuterium, Enrichment is
// still dimmed behind its requirement. Nothing is running, so no row carries a countdown.
internal val nothingRunningUiState = ResearchUiState(
    technologies = listOf(
        TechnologyRowUiState(
            technology = Technology.PHOTOVOLTAICS,
            name = "Photovoltaics",
            level = TechLevel(2),
            effect = photovoltaicsEffect(current = "+21%", next = "+33%"),
            costs = costs(metal = "675", crystal = "338", deuterium = "225", short = null),
            duration = "2h 36m",
            action = ResearchActionUiState.Start,
        ),
        TechnologyRowUiState(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = TechLevel(2),
            effect = extractionEffect(current = "+17%", next = "+26%"),
            costs = costs(metal = "1,350", crystal = "900", deuterium = "450", short = ResourceKind.DEUTERIUM),
            duration = "3h 53m",
            action = ResearchActionUiState.AvailableIn("in 1h 45m"),
        ),
        TechnologyRowUiState(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = TechLevel(0),
            effect = enrichmentEffect(current = null, next = "+14%"),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "3h 15m",
            action = ResearchActionUiState.Locked("Requires Extraction 3"),
        ),
    ),
)

// Day 9, Robotics 4. The running row takes the accent border, its target level and finish time,
// the countdown and the bar. The other two carry the time until they can start: Extraction waits
// on deuterium, Enrichment waits on the slot — and the player never has to know which.
internal val oneProjectInFlightUiState = ResearchUiState(
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
        ),
        TechnologyRowUiState(
            technology = Technology.EXTRACTION,
            name = "Extraction",
            level = TechLevel(4),
            effect = extractionEffect(current = "+36%", next = "+47%"),
            costs = costs(metal = "3,038", crystal = "2,025", deuterium = "1,013", short = ResourceKind.DEUTERIUM),
            duration = "5h 41m",
            action = ResearchActionUiState.AvailableIn("in 3h 55m"),
        ),
        TechnologyRowUiState(
            technology = Technology.ENRICHMENT,
            name = "Enrichment",
            level = TechLevel(0),
            effect = enrichmentEffect(current = null, next = "+14%"),
            costs = costs(metal = "500", crystal = "700", deuterium = "200", short = null),
            duration = "1h 54m",
            action = ResearchActionUiState.AvailableIn("in 1h 13m"),
        ),
    ),
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
