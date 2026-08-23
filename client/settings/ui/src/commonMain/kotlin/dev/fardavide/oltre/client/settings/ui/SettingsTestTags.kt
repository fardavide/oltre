package dev.fardavide.oltre.client.settings.ui

import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode

object SettingsTestTags {

    // On the *contents* rather than on the chrome, so it names the same thing whether a test is
    // driving the real sheet or the contents on their own — `DispatchTestTags.SHEET`'s precedent.
    const val SHEET = "settings-sheet"

    // The panel of seven, present only under `BY_CATEGORY`. **Its absence is an assertion**: the
    // panel does not exist in the other mode rather than being collapsed, so a test that looks for
    // this tag and does not find it has measured the design's own rule.
    const val PANEL = "settings-alert-panel"

    // What the phone would print, and when. Two tags because they answer two questions and one of
    // them is absent from no state — a colony with nothing in flight still says so.
    const val EXAMPLE = "settings-delivery-example"
    const val TIMING = "settings-delivery-timing"

    // Keyed by the stop rather than by the label, for `DispatchTestTags.window`'s reason: a label is
    // a `TextRes` whose text depends on which table a test happens to be running under.
    fun mode(mode: AlertMode): String = "settings-mode-${mode.name.lowercase()}"

    fun delivery(delivery: AlertDelivery): String = "settings-delivery-${delivery.name.lowercase()}"

    // **The row, not the square.** The whole 38dp width answers, which is what lets the square stay
    // at the colony's own 29dp without carrying a 44dp hit area on its own — so a test that tapped a
    // tag on the square would be testing a target the design deliberately did not build.
    fun category(category: AlertCategory): String = "settings-category-${category.name.lowercase()}"
}
