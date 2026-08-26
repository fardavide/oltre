package dev.fardavide.oltre.client.colony.ui

import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.HeldUiState
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
        verdict = TextRes("every mine at 90%"),
        terms = TextRes("400 produced · 440 drawn · 40 short"),
        coveredFraction = 400f / 440f,
        deficit = true,
    ),
    facilities = listOf(
        FacilityRowUiState(
            building = BuildingType.METAL_MINE,
            name = TextRes("Metal Mine"),
            compactName = TextRes("Metal Mine"),
            level = BuildingLevel(12),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("7,749"), short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("1,851"), short = false),
            ),
            duration = TextRes("2h 10m"),
            action = FacilityActionUiState.Upgrading(
                toLevel = BuildingLevel(13),
                countdown = TextRes("01:42:19"),
                progressPercent = 68,
                doneAt = TextRes("done 11:23"),
            ),
            power = FacilityPowerUiState(label = TextRes("−120"), supply = false),
            fix = null,
            watch = WatchUiState.Offered,
            // No verdict, because nobody is choosing: this level was decided on when the player
            // tapped, and its sheet keeps the one sentence that says what the level is.
            verdict = null,
            detail = FacilityDetailUiState(
                lines = listOf(
                    sheetLine(
                        words(TextRes("Your colony makes ")),
                        figure(TextRes("1,124/h")),
                        words(TextRes(" metal. At LV 13 it makes ")),
                        figure(TextRes("1,405/h")),
                        words(TextRes(".")),
                    ),
                ),
                ladder = emptyList(),
                pointer = null,
            ),
            finishedWhileAway = false,
            held = HeldUiState.NONE,
        ),
        FacilityRowUiState(
            building = BuildingType.SOLAR_PLANT,
            name = TextRes("Solar Plant"),
            compactName = TextRes("Solar Plant"),
            level = BuildingLevel(8),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("1,912"), short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("757"), short = false),
            ),
            duration = TextRes("1h 12m"),
            action = FacilityActionUiState.Upgrade,
            power = FacilityPowerUiState(label = TextRes("+400"), supply = true),
            fix = TextRes("→ LV 9 covers all 440 drawn"),
            watch = null,
            // A plant on a throttled colony is an income row like any other, and this is the frame
            // that says so: the same building reads as supply while there is headroom and as metal
            // once there is not.
            verdict = VerdictUiState(
                label = TextRes("+38/h metal · back in 4h 20m"),
                compactLabel = TextRes("+38/h metal"),
            ),
            detail = FacilityDetailUiState(
                lines = listOf(
                    sheetLine(
                        words(TextRes("Your plants supply ")),
                        figure(TextRes("400")),
                        words(TextRes(" energy. The colony draws ")),
                        figure(TextRes("440")),
                        words(TextRes(", so every mine is running at ")),
                        figure(TextRes("90%")),
                        words(TextRes(".")),
                    ),
                    sheetLine(
                        words(TextRes("This level lifts that, which is why it reads as ")),
                        figure(TextRes("+38/h")),
                        words(TextRes(" metal rather than as energy.")),
                    ),
                    sheetLine(
                        words(TextRes("Counted against everything the level costs, you are even after ")),
                        figure(TextRes("4h 20m")),
                        words(TextRes(".")),
                    ),
                ),
                ladder = emptyList(),
                pointer = null,
            ),
            finishedWhileAway = false,
            held = HeldUiState.NONE,
        ),
        FacilityRowUiState(
            building = BuildingType.DEUTERIUM_SYNTHESIZER,
            name = TextRes("Deuterium Synth."),
            compactName = TextRes("Deuterium Synth."),
            level = BuildingLevel(16),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("147,169"), short = true),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("48,997"), short = false),
            ),
            duration = TextRes("5h 40m"),
            action = FacilityActionUiState.AffordableIn(TextRes("in 3h 12m")),
            power = FacilityPowerUiState(label = TextRes("−320"), supply = false),
            fix = null,
            watch = WatchUiState.Offered,
            verdict = VerdictUiState(
                label = TextRes("+41/h deuterium · back in 61h"),
                compactLabel = TextRes("+41/h deuterium"),
            ),
            detail = FacilityDetailUiState(
                lines = listOf(
                    sheetLine(
                        words(TextRes("Your colony makes ")),
                        figure(TextRes("172/h")),
                        words(TextRes(" deuterium. At LV 17 it makes ")),
                        figure(TextRes("213/h")),
                        words(TextRes(".")),
                    ),
                    sheetLine(
                        words(TextRes("Counted against everything the level costs, you are even after ")),
                        figure(TextRes("61h")),
                        words(TextRes(".")),
                    ),
                ),
                ladder = emptyList(),
                pointer = null,
            ),
            finishedWhileAway = false,
            held = HeldUiState.NONE,
        ),
        FacilityRowUiState(
            building = BuildingType.NANITE_FACTORY,
            name = TextRes("Nanite Factory"),
            compactName = TextRes("Nanite Factory"),
            level = BuildingLevel(0),
            costs = listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("20,000"), short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("10,000"), short = false),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = TextRes("4,000"), short = false),
            ),
            duration = TextRes("2h 00m"),
            action = FacilityActionUiState.Locked(TextRes("Requires Robotics 10")),
            power = null,
            fix = null,
            watch = null,
            // The one verdict that is not about the next level of anything. It has to read at 42%
            // alpha while the building is still days out, which is why it is the shape of the curve
            // rather than a saving.
            verdict = VerdictUiState(
                label = TextRes("A 298h build takes 26h at LV 6"),
                compactLabel = TextRes("298h builds take 26h at LV 6"),
            ),
            detail = FacilityDetailUiState(
                lines = listOf(
                    sheetLine(
                        words(
                            Strings.sheetShortensDeepBuild(),
                        ),
                    ),
                    sheetLine(
                        words(TextRes("A level-30 Metal Mine takes ")),
                        figure(TextRes("298h")),
                        words(TextRes(" unaided. At 6 Nanite levels it takes ")),
                        figure(TextRes("26h")),
                        words(TextRes(".")),
                    ),
                    sheetLine(
                        words(TextRes("Your Robotics Factory is at ")),
                        figure(TextRes("7")),
                        words(TextRes(". 3 levels to go, and the first Nanite level costs ")),
                        figure(TextRes("2,000")),
                        words(TextRes(" metal.")),
                    ),
                ),
                ladder = emptyList(),
                pointer = SheetPointer(name = TextRes("Robotics Factory"), detail = TextRes("LV 7 → 8 · 41m")),
            ),
            finishedWhileAway = false,
            held = HeldUiState.NONE,
        ),
    ),
    returningFleet = ReturningFleetUiState(
        title = TextRes("Fleet returning"),
        subtitle = TextRes("from [1:42:7] · 12 cargo"),
        countdown = TextRes("02:11:40"),
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
    watching = TextRes("watching Deuterium Synth."),
    facilities = testColonyUiState.facilities.map { row ->
        when (row.action) {
            is FacilityActionUiState.Upgrading -> row.copy(watch = WatchUiState.Subscribed)
            is FacilityActionUiState.AffordableIn -> row.copy(watch = WatchUiState.Booked(TextRes("→ affordable 19:51")))
            FacilityActionUiState.Upgrade, is FacilityActionUiState.Locked -> row
        }
    },
)

// The one row on this screen that gates anything, and therefore the only sheet in the game with a
// ladder in it. Not part of the colony above: adding a fifth row would change what every wide-window
// baseline is a picture of, and what this fixture is for is the sheet rather than the list.
internal val roboticsFacilityRow = FacilityRowUiState(
    building = BuildingType.ROBOTICS_FACTORY,
    name = TextRes("Robotics Factory"),
    compactName = TextRes("Robotics"),
    level = BuildingLevel(3),
    costs = listOf(
        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("1,350"), short = false),
        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("405"), short = false),
        CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = TextRes("675"), short = false),
    ),
    duration = TextRes("1h 04m"),
    action = FacilityActionUiState.Upgrade,
    power = null,
    fix = null,
    watch = null,
    verdict = VerdictUiState(label = TextRes("−20m per build · LV 10 → Nanite"), compactLabel = TextRes("−20m per build")),
    detail = FacilityDetailUiState(
        lines = listOf(
            sheetLine(
                words(
                    Strings.sheetShortensEveryBuild(),
                ),
            ),
            sheetLine(
                words(TextRes("Your next Metal Mine takes ")),
                figure(TextRes("1h 37m")),
                words(TextRes(". At Robotics Factory 4 it takes ")),
                figure(TextRes("1h 18m")),
                words(TextRes(".")),
            ),
        ),
        // Two held and one ahead, which is the whole reason the levels already bought are on the
        // ladder at all: it is how you learn that gating is a thing this row does.
        ladder = listOf(
            SheetLadderStep(level = TextRes("LV 1"), opens = TextRes("applied research · you have this"), held = true),
            SheetLadderStep(level = TextRes("LV 2"), opens = TextRes("the three adaptation ladders · you have this"), held = true),
            SheetLadderStep(level = TextRes("LV 10"), opens = TextRes("Nanite Factory · 2,000 metal"), held = false),
        ),
        pointer = null,
    ),
    finishedWhileAway = false,
    held = HeldUiState.NONE,
)

// A plant with headroom still to spare, which is the frame the whole design is about: a row whose
// honest verdict is "nothing", and which therefore has to end on the row to read instead.
internal val inertPlantFacilityRow = FacilityRowUiState(
    building = BuildingType.SOLAR_PLANT,
    name = TextRes("Solar Plant"),
    compactName = TextRes("Solar Plant"),
    level = BuildingLevel(4),
    costs = listOf(
        CostChipUiState(kind = ResourceKind.METAL, amount = TextRes("253"), short = false),
        CostChipUiState(kind = ResourceKind.CRYSTAL, amount = TextRes("101"), short = false),
    ),
    duration = TextRes("18m"),
    action = FacilityActionUiState.AffordableIn(TextRes("in 42m")),
    power = null,
    fix = null,
    watch = WatchUiState.Offered,
    verdict = VerdictUiState(label = TextRes("+50 supply · draw already covered"), compactLabel = TextRes("+50 supply")),
    detail = FacilityDetailUiState(
        lines = listOf(
            sheetLine(
                words(TextRes("Your plants supply ")),
                figure(TextRes("200")),
                words(TextRes(" energy. The colony draws ")),
                figure(TextRes("120")),
                words(TextRes(".")),
            ),
            sheetLine(
                words(TextRes("Supply is not what is limiting you, so a level that adds ")),
                figure(TextRes("+50")),
                words(TextRes(" changes no rate.")),
            ),
            sheetLine(
                words(TextRes("It starts to pay when draw passes supply — about ")),
                figure(TextRes("8")),
                words(TextRes(" more mine levels away.")),
            ),
        ),
        ladder = emptyList(),
        pointer = SheetPointer(name = TextRes("Metal Mine"), detail = TextRes("LV 5 · back in 2h 06m")),
    ),
    finishedWhileAway = false,
    held = HeldUiState.NONE,
)
