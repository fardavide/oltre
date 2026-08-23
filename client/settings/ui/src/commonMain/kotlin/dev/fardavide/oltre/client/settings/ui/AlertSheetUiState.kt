package dev.fardavide.oltre.client.settings.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode

// What the settings sheet draws, and the whole of it — the sheet decides nothing, which is why every
// label here is already a `TextRes` and every state is already a `Boolean`.
//
// **`categories` is nullable and that is the design's shape rather than a convenience.** Under
// `PER_ITEM` the panel is not collapsed, it does not exist: the option that owns it is not chosen, so
// there is nothing to draw and nothing to expand. A list that was empty in that case would be a
// panel with no rows in it, which is a different and much worse thing to look at.
data class AlertSheetUiState(
    val title: TextRes,
    val alertsLabel: TextRes,
    val modes: List<AlertModeStep>,
    // The one line the ladder carries: what the mode means, then what it does to the screen the
    // player came from.
    val modeNote: TextRes,
    val categories: List<AlertCategoryRow>?,
    val deliveryLabel: TextRes,
    val deliveries: List<AlertDeliveryStep>,
    // The string the phone would actually print under the chosen stop — shorter than any sentence
    // explaining it, and the one thing on the sheet that is a sample rather than a rule.
    val example: TextRes,
    // And when it would arrive, which is the only part nobody can guess.
    //
    // **Null under `EACH` and only there.** The answer at that stop is *whenever anything lands*, so
    // a line would be a sentence explaining the word `each`. Under the other two it is always
    // present — a colony with nothing in flight says so, because an empty space where a time was
    // reads as a control that failed.
    val timing: TextRes?,
)

// A chip in the two-stop ladder. Carries its own `AlertMode` so a tap is *this one* rather than *the
// other one* — a two-way ladder that reported only "the chip was tapped" would be a toggle wearing
// two labels, and a second tap on the lit chip would move it.
data class AlertModeStep(val mode: AlertMode, val label: TextRes, val selected: Boolean)

// A chip in the three-stop ladder. The same shape one stop wider, which is the point: this is the
// dispatch sheet's `Home in` ladder with one rung removed, so the screen has one control idiom
// rather than two.
data class AlertDeliveryStep(val delivery: AlertDelivery, val label: TextRes, val selected: Boolean)

// One of the seven bells. **The row is the target and the square is the drawing** — 38dp tall and
// the whole width answers — so the spoken label is a property of the row rather than of the square.
data class AlertCategoryRow(
    val category: AlertCategory,
    val label: TextRes,
    // **The only second line in the panel, on the only row that needs one.** Null on the other six,
    // because they say what they do in their own name; `PRICE_REACHED` is the one switch that
    // governs whether a control appears on rows elsewhere in the app, and that has to be said where
    // it is decided.
    val note: TextRes?,
    val on: Boolean,
    // Label and state, in that order, for a screen reader — "Facilities · alerts on".
    val spoken: TextRes,
)
