package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.SheetLadderStep
import dev.fardavide.oltre.client.design.component.SheetPointer
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.component.figure
import dev.fardavide.oltre.client.design.component.sheetLine
import dev.fardavide.oltre.client.design.component.words
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ResourceKind

// A mid-game colony: two builds running in parallel, a fleet on its way home, and rows in every
// action state. Shared by the layout assertions and the wide-window baselines.
//
// The energy figures are the ones these four rows actually add up to — solar 8 supplies 400
// against metal 12 and deuterium 16 drawing 120 and 320 — so the marks on the cards, the terms
// under the track and the fix line can all be checked against each other by eye in a baseline.
internal val testColonyUiState = ColonyUiState(
    // Short of power, so the wide baselines carry the whole vocabulary at once: the amber tail on
    // the indicator, the throttled rates on the rail, the marks that attribute the cut, and the
    // one line that says what ends it.
    energy = EnergyUiState(
        verdict = "every mine at 90%",
        terms = "400 produced · 440 drawn · 40 short",
        coveredFraction = 400f / 440f,
        deficit = true,
    ),
    facilities = listOf(
        FacilityRowUiState(
            building = BuildingType.METAL_MINE,
            name = "Metal Mine",
            compactName = "Metal Mine",
            level = BuildingLevel(12),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "7,749", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "1,851", short = false),
            ),
            duration = "2h 10m",
            action = FacilityActionUiState.Upgrading(
                toLevel = BuildingLevel(13),
                countdown = "01:42:19",
                progressPercent = 68,
                doneAt = "done 11:23",
            ),
            power = FacilityPowerUiState(label = "−120", supply = false),
            fix = null,
            watch = WatchUiState.Offered,
            // No verdict, because nobody is choosing: this level was decided on when the player
            // tapped, and its sheet keeps the one sentence that says what the level is.
            verdict = null,
            detail = FacilityDetailUiState(
                lines = listOf(
                    sheetLine(
                        words("Your colony makes "),
                        figure("1,124/h"),
                        words(" metal. At LV 13 it makes "),
                        figure("1,405/h"),
                        words("."),
                    ),
                ),
                ladder = emptyList(),
                pointer = null,
            ),
            finishedWhileAway = false,
        ),
        FacilityRowUiState(
            building = BuildingType.SOLAR_PLANT,
            name = "Solar Plant",
            compactName = "Solar Plant",
            level = BuildingLevel(8),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "1,912", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "757", short = false),
            ),
            duration = "1h 12m",
            action = FacilityActionUiState.Upgrade,
            power = FacilityPowerUiState(label = "+400", supply = true),
            fix = "→ LV 9 covers all 440 drawn",
            watch = null,
            // A plant on a throttled colony is an income row like any other, and this is the frame
            // that says so: the same building reads as supply while there is headroom and as metal
            // once there is not.
            verdict = VerdictUiState(
                label = "+38/h metal · back in 4h 20m",
                compactLabel = "+38/h metal",
            ),
            detail = FacilityDetailUiState(
                lines = listOf(
                    sheetLine(
                        words("Your plants supply "),
                        figure("400"),
                        words(" energy. The colony draws "),
                        figure("440"),
                        words(", so every mine is running at "),
                        figure("90%"),
                        words("."),
                    ),
                    sheetLine(
                        words("This level lifts that, which is why it reads as "),
                        figure("+38/h"),
                        words(" metal rather than as energy."),
                    ),
                    sheetLine(
                        words("Counted against everything the level costs, you are even after "),
                        figure("4h 20m"),
                        words("."),
                    ),
                ),
                ladder = emptyList(),
                pointer = null,
            ),
            finishedWhileAway = false,
        ),
        FacilityRowUiState(
            building = BuildingType.DEUTERIUM_SYNTHESIZER,
            name = "Deuterium Synth.",
            compactName = "Deuterium Synth.",
            level = BuildingLevel(16),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "147,169", short = true),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "48,997", short = false),
            ),
            duration = "5h 40m",
            action = FacilityActionUiState.AffordableIn("in 3h 12m"),
            power = FacilityPowerUiState(label = "−320", supply = false),
            fix = null,
            watch = WatchUiState.Offered,
            verdict = VerdictUiState(
                label = "+41/h deuterium · back in 61h",
                compactLabel = "+41/h deuterium",
            ),
            detail = FacilityDetailUiState(
                lines = listOf(
                    sheetLine(
                        words("Your colony makes "),
                        figure("172/h"),
                        words(" deuterium. At LV 17 it makes "),
                        figure("213/h"),
                        words("."),
                    ),
                    sheetLine(
                        words("Counted against everything the level costs, you are even after "),
                        figure("61h"),
                        words("."),
                    ),
                ),
                ladder = emptyList(),
                pointer = null,
            ),
            finishedWhileAway = false,
        ),
        FacilityRowUiState(
            building = BuildingType.NANITE_FACTORY,
            name = "Nanite Factory",
            compactName = "Nanite Factory",
            level = BuildingLevel(0),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "20,000", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "10,000", short = false),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "4,000", short = false),
            ),
            duration = "2h 00m",
            action = FacilityActionUiState.Locked("Requires Robotics 10"),
            power = null,
            fix = null,
            watch = null,
            // The one verdict that is not about the next level of anything. It has to read at 42%
            // alpha while the building is still days out, which is why it is the shape of the curve
            // rather than a saving.
            verdict = VerdictUiState(
                label = "A 298h build takes 26h at LV 6",
                compactLabel = "298h builds take 26h at LV 6",
            ),
            detail = FacilityDetailUiState(
                lines = listOf(
                    sheetLine(
                        words(
                            "Takes the late game's waits apart. It is the only thing in the game " +
                                "that shortens a deep build.",
                        ),
                    ),
                    sheetLine(
                        words("A level-30 Metal Mine takes "),
                        figure("298h"),
                        words(" unaided. At 6 Nanite levels it takes "),
                        figure("26h"),
                        words("."),
                    ),
                    sheetLine(
                        words("Your Robotics Factory is at "),
                        figure("7"),
                        words(". 3 levels to go, and the first Nanite level costs "),
                        figure("2,000"),
                        words(" metal."),
                    ),
                ),
                ladder = emptyList(),
                pointer = SheetPointer(name = "Robotics Factory", detail = "LV 7 → 8 · 41m"),
            ),
            finishedWhileAway = false,
        ),
    ),
    returningFleet = ReturningFleetUiState(
        title = "Fleet returning",
        subtitle = "from [1:42:7] · 12 cargo",
        countdown = "02:11:40",
    ),
    // The square is offered on the one row that is waiting on its stocks, and nothing holds the
    // watch. The watched reading is its own frame — see `watchedColonyUiState`.
    watching = null,
)

// The same colony with both halves of the square lit, which is what makes this the frame the whole
// slice is about: **the two states look identical and say different things.** The row waiting on its
// stores gains a line naming the instant, because nothing else on the card says it. The row that is
// building gains nothing at all, because its own accent line already does.
internal val watchedColonyUiState = testColonyUiState.copy(
    watching = "watching Deuterium Synth.",
    facilities = testColonyUiState.facilities.map { row ->
        when (row.action) {
            is FacilityActionUiState.Upgrading -> row.copy(watch = WatchUiState.Subscribed)
            is FacilityActionUiState.AffordableIn -> row.copy(watch = WatchUiState.Booked("→ affordable 19:51"))
            FacilityActionUiState.Upgrade, is FacilityActionUiState.Locked -> row
        }
    },
)

// The one row on this screen that gates anything, and therefore the only sheet in the game with a
// ladder in it. Not part of the colony above: adding a fifth row would change what every wide-window
// baseline is a picture of, and what this fixture is for is the sheet rather than the list.
internal val roboticsFacilityRow = FacilityRowUiState(
    building = BuildingType.ROBOTICS_FACTORY,
    name = "Robotics Factory",
    compactName = "Robotics",
    level = BuildingLevel(3),
    costs = listOf(
        CostChipUiState(kind = ResourceKind.METAL, amount = "1,350", short = false),
        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "405", short = false),
        CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "675", short = false),
    ),
    duration = "1h 04m",
    action = FacilityActionUiState.Upgrade,
    power = null,
    fix = null,
    watch = null,
    verdict = VerdictUiState(label = "−20m per build · LV 10 → Nanite", compactLabel = "−20m per build"),
    detail = FacilityDetailUiState(
        lines = listOf(
            sheetLine(
                words(
                    "Shortens every build on this colony and every research in the empire. " +
                        "It raises no output of its own.",
                ),
            ),
            sheetLine(
                words("Your next Metal Mine takes "),
                figure("1h 37m"),
                words(". At Robotics Factory 4 it takes "),
                figure("1h 18m"),
                words("."),
            ),
        ),
        // Two held and one ahead, which is the whole reason the levels already bought are on the
        // ladder at all: it is how you learn that gating is a thing this row does.
        ladder = listOf(
            SheetLadderStep(level = "LV 1", opens = "applied research · you have this", held = true),
            SheetLadderStep(level = "LV 2", opens = "the three adaptation ladders · you have this", held = true),
            SheetLadderStep(level = "LV 10", opens = "Nanite Factory · 2,000 metal", held = false),
        ),
        pointer = null,
    ),
    finishedWhileAway = false,
)

// A plant with headroom still to spare, which is the frame the whole design is about: a row whose
// honest verdict is "nothing", and which therefore has to end on the row to read instead.
internal val inertPlantFacilityRow = FacilityRowUiState(
    building = BuildingType.SOLAR_PLANT,
    name = "Solar Plant",
    compactName = "Solar Plant",
    level = BuildingLevel(4),
    costs = listOf(
        CostChipUiState(kind = ResourceKind.METAL, amount = "253", short = false),
        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "101", short = false),
    ),
    duration = "18m",
    action = FacilityActionUiState.AffordableIn("in 42m"),
    power = null,
    fix = null,
    watch = WatchUiState.Offered,
    verdict = VerdictUiState(label = "+50 supply · draw already covered", compactLabel = "+50 supply"),
    detail = FacilityDetailUiState(
        lines = listOf(
            sheetLine(
                words("Your plants supply "),
                figure("200"),
                words(" energy. The colony draws "),
                figure("120"),
                words("."),
            ),
            sheetLine(
                words("Supply is not what is limiting you, so a level that adds "),
                figure("+50"),
                words(" changes no rate."),
            ),
            sheetLine(
                words("It starts to pay when draw passes supply — about "),
                figure("8"),
                words(" more mine levels away."),
            ),
        ),
        ladder = emptyList(),
        pointer = SheetPointer(name = "Metal Mine", detail = "LV 5 · back in 2h 06m"),
    ),
    finishedWhileAway = false,
)
