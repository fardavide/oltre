package dev.fardavide.oltre.client.settings.presentation

import dev.fardavide.oltre.client.design.format.watchedAtLabel
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.settings.ui.AlertCategoryRow
import dev.fardavide.oltre.client.settings.ui.AlertDeliveryStep
import dev.fardavide.oltre.client.settings.ui.AlertModeStep
import dev.fardavide.oltre.client.net.domain.HeldActions
import dev.fardavide.oltre.client.settings.ui.AccountUiState
import dev.fardavide.oltre.client.settings.ui.AlertSheetUiState
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.announcedEvents
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// The settings sheet, read off the colony it is about.
//
// Almost all of it is a straight rendering of `GameState.alerts` — which chip is lit, which of the
// seven bells are on — and one line is not: `timing` says when the next alert is actually due, which
// is a fact about this colony rather than about the setting.
fun GameState.toAlertSheetUiState(
    now: Instant,
    timeZone: TimeZone,
    // Ten of the app's held controls, three of them here. Defaulted to an empty queue — a colony with
    // signal — and the shell hands in the real one.
    held: HeldActions = HeldActions.NONE,
    // **Who is signed in, which nothing in a `GameState` has ever known.** Null draws no Account
    // section at all, which is what every frame built from a fixture wants and is also the honest
    // answer on a build that has no account: there is nothing to administer.
    account: AccountUiState? = null,
): AlertSheetUiState = AlertSheetUiState(
    title = Strings.settingsTitle(),
    alertsLabel = Strings.alertsLabel(),
    modes = AlertMode.entries.map { mode ->
        AlertModeStep(
            mode = mode,
            label = when (mode) {
                AlertMode.PER_ITEM -> Strings.alertModePerItem()
                AlertMode.BY_CATEGORY -> Strings.alertModeByCategory()
            },
            selected = alerts.mode == mode,
            asked = held.alertMode?.asked == mode,
        )
    },
    // **The held line displaces the note**, which is the sheet's own rule for the two lines under a
    // ladder: what the mode means was already true, and what you asked for has not landed.
    modeNote = held.alertMode?.let { Strings.heldLadderNote(Strings.alertModeName(it.asked)) }
        ?: Strings.alertModeNote(alerts.mode),
    // **Null rather than an empty list under `PER_ITEM`.** The panel is not collapsed there — it does
    // not exist, because the option that owns it is not chosen — and the two are different things to
    // look at. See `AlertSheetUiState`.
    categories = when (alerts.mode) {
        AlertMode.PER_ITEM -> null
        AlertMode.BY_CATEGORY -> AlertCategory.entries.map {
            it.toRow(on = it in alerts.categories, held = held.alertCategory(it) != null)
        }
    },
    deliveryLabel = Strings.deliveryLabel(),
    deliveries = AlertDelivery.entries.map { delivery ->
        AlertDeliveryStep(
            delivery = delivery,
            label = Strings.deliveryName(delivery),
            selected = alerts.delivery == delivery,
            asked = held.alertDelivery?.asked == delivery,
        )
    },
    example = alerts.delivery.example(),
    // The held line displaces *when*, for the mode note's reason one control up.
    timing = held.alertDelivery?.let { Strings.heldLadderNote(Strings.deliveryName(it.asked)) }
        ?: timing(now = now, timeZone = timeZone),
    account = account,
)

private fun AlertCategory.toRow(on: Boolean, held: Boolean): AlertCategoryRow {
    val label = Strings.alertCategoryName(this)
    return AlertCategoryRow(
        category = this,
        label = label,
        // The only second line in the panel — until a row is held, and then every one of the seven
        // has one, because the square cannot say which way the request went and this is the row that
        // can. It displaces the price watch's own exception line, which is the more urgent of the two.
        note = when {
            // Not `!on`: the tap applied `toggleAlertCategory` to the session before this ran, so
            // `on` already *is* the request. See `asSquare` for the rule and why the inverted reading
            // looked right.
            held -> Strings.heldTurning(on = on)
            this == AlertCategory.PRICE_REACHED -> Strings.alertPriceWatchNote(on)
            else -> null
        },
        on = on,
        held = held,
        spoken = Strings.clauses(listOf(label, Strings.alertBellState(on))),
    )
}

// **A worked sample rather than this colony's own next alert**, and that is deliberate: the sheet has
// to say what the stop *means*, and a colony with one build running would illustrate `One per
// category` and `One in total` with the same sentence — which is exactly the distinction the line
// exists to draw. The instant below is the live half; this is the shape.
//
// Built from the same catalogue entries the scheduler uses, so a sample can never drift into
// promising a sentence the game cannot produce.
private fun AlertDelivery.example(): TextRes = when (this) {
    AlertDelivery.EACH -> Strings.reachedLevel(Strings.buildingFullName(BuildingType.METAL_MINE), EXAMPLE_LEVEL)
    AlertDelivery.PER_CATEGORY -> Strings.alertGroupTitle(AlertCategory.FACILITIES, EXAMPLE_FACILITIES)
    // **Two kinds and not one**, which is the whole difference from the stop above it: the point of
    // this one is that everything folds into a single notification, and a sample with one kind in it
    // would illustrate `One per category` just as well.
    AlertDelivery.TOTAL -> Strings.clauses(
        listOf(
            Strings.alertCountClause(AlertCategory.FACILITIES, EXAMPLE_TOTAL_FACILITIES),
            Strings.alertCountClause(AlertCategory.HULLS, EXAMPLE_HULLS),
        ),
    )
}

// **The one live line on the sheet, and the reason it reads `announcedEvents` rather than counting
// jobs.** What a player wants to know is when the next buzz is, and that is not the next thing to
// happen: it is the next thing to happen *that they have asked about*, under the settings they are
// looking at. The two differ the moment a switch is off, which is the moment the line is worth having.
//
// Absent under `EACH` — the answer there is "whenever anything lands", which needs no explaining and
// is what the design leaves blank.
private fun GameState.timing(now: Instant, timeZone: TimeZone): TextRes? {
    // Nothing under `EACH`, which is the design's own call: the answer there is *whenever anything
    // lands*, and a line saying so would be a sentence explaining the word `each`.
    if (alerts.delivery == AlertDelivery.EACH) return null
    val next = announcedEvents(this, now = now).minByOrNull { it.at } ?: return Strings.alertNothingPending()
    val local = next.at.toLocalDateTime(timeZone)
    return Strings.alertNextAt(
        hour = local.hour,
        minute = local.minute,
        updating = alerts.delivery == AlertDelivery.TOTAL,
    )
}

private const val EXAMPLE_LEVEL: Int = 4
private const val EXAMPLE_FACILITIES: Int = 3
private const val EXAMPLE_TOTAL_FACILITIES: Int = 2
private const val EXAMPLE_HULLS: Int = 1
