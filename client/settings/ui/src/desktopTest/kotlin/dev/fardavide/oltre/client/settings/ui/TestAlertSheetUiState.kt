package dev.fardavide.oltre.client.settings.ui

import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildingType

// The sheet's states, built here rather than mapped from a colony — `:client:settings:presentation`
// is where the mapping is tested, and a `ui` module may not see it. What these fixtures are for is
// the *drawing*: which chip is lit, whether the panel exists, how a stacked ladder lands.
//
// Built out of the real catalogue entries rather than raw strings, so a frame is photographed with
// the words a player would actually read and a baseline moves when the copy does.
internal fun alertSheetUiState(
    mode: AlertMode,
    delivery: AlertDelivery,
    off: Set<AlertCategory> = emptySet(),
    timing: TextRes? = Strings.alertNextAt(hour = 17, minute = 42, updating = delivery == AlertDelivery.TOTAL),
): AlertSheetUiState = AlertSheetUiState(
    title = Strings.settingsTitle(),
    alertsLabel = Strings.alertsLabel(),
    modes = AlertMode.entries.map { entry ->
        AlertModeStep(
            mode = entry,
            label = when (entry) {
                AlertMode.PER_ITEM -> Strings.alertModePerItem()
                AlertMode.BY_CATEGORY -> Strings.alertModeByCategory()
            },
            selected = entry == mode,
        )
    },
    modeNote = Strings.alertModeNote(mode),
    categories = when (mode) {
        AlertMode.PER_ITEM -> null
        AlertMode.BY_CATEGORY -> AlertCategory.entries.map { category ->
            val on = category !in off
            AlertCategoryRow(
                category = category,
                label = Strings.alertCategoryName(category),
                note = if (category == AlertCategory.PRICE_REACHED) Strings.alertPriceWatchNote(on) else null,
                on = on,
                spoken = Strings.clauses(
                    listOf(Strings.alertCategoryName(category), Strings.alertBellState(on)),
                ),
            )
        }
    },
    deliveryLabel = Strings.deliveryLabel(),
    deliveries = AlertDelivery.entries.map { entry ->
        AlertDeliveryStep(delivery = entry, label = Strings.deliveryName(entry), selected = entry == delivery)
    },
    example = when (delivery) {
        // **The catalogue's own name, not `TextRes("Metal Mine")`** — a raw string resolves to itself
        // in every language, so a locale baseline over it would assert that English is still English.
        AlertDelivery.EACH -> Strings.reachedLevel(Strings.buildingFullName(BuildingType.METAL_MINE), 4)
        AlertDelivery.PER_CATEGORY -> Strings.alertGroupTitle(AlertCategory.FACILITIES, 3)
        // Two kinds, matching the mapper's own sample: what this stop is about is everything folding
        // into one notification, and one kind would illustrate the stop above it just as well.
        AlertDelivery.TOTAL -> Strings.clauses(
            listOf(
                Strings.alertCountClause(AlertCategory.FACILITIES, 2),
                Strings.alertCountClause(AlertCategory.HULLS, 1),
            ),
        )
    },
    // Absent under `EACH`, which is the one stop that needs no explaining — and the one state a
    // baseline of this sheet has to be able to show, because it is what the sheet is missing rather
    // than what it says.
    timing = timing.takeIf { delivery != AlertDelivery.EACH },
)
