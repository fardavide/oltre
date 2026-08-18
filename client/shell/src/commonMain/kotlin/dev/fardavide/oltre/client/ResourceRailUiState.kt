package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Resources

// The three stocks and what they are earning per hour. The rates are the *effective* ones — after
// the building curve, the research multipliers and any energy deficit — because the rail's job is
// to say what the colony is actually making, not what it would make in ideal conditions.
data class ResourceRailUiState(
    val metal: ResourceStockUiState,
    val crystal: ResourceStockUiState,
    val deuterium: ResourceStockUiState,
    // Whether a power shortage is holding those rates down. The rates are already the throttled
    // figures; what misled the player was a true rate presented as an untroubled one. The Colony
    // screen explains the shortage — this is only the mark that says the numbers above are it.
    val throttled: Boolean,
)

// One cell's worth. The stock is a number rather than a string, and that is the Sky pass's one
// change to the rail: a value that rolls from the figure the player last saw to the one the colony
// has accrued to has to be arithmetic while it is moving, and a formatted string is the end of that
// journey rather than the middle of it. How a figure is *written* stays one decision in one place —
// `groupedByThousands`, called by the cell that draws it.
//
// The rate does not roll and stays a string. It is not a quantity that accumulated while the app
// was closed; it is a statement about now, and it was already true when the app went away.
data class ResourceStockUiState(
    val stock: Long,
    // What this stock read when the save was written, which is the last figure the player actually
    // saw. Equal to `stock` on a first launch and after the arrival window has passed — the roll is
    // then a roll of zero length, which is the same thing as no roll at all and needs no branch.
    val lastSeenStock: Long,
    val ratePerHour: TextRes,
)

// `lastSeen` is the saved colony's stocks — the reading the player was looking at when they closed
// the app. It is the shell's to supply because only the shell knows what was on disk, and it is
// dropped once the arrival window has passed so that nothing rolls a second time.
internal fun GameState.toResourceRailUiState(lastSeen: Resources? = null): ResourceRailUiState =
    ResourceRailUiState(
        metal = ResourceStockUiState(
            stock = resources.metal,
            lastSeenStock = lastSeen?.metal ?: resources.metal,
            ratePerHour = PlaceholderBalance.effectiveMetalProductionPerHour(buildings, research).toRate(),
        ),
        crystal = ResourceStockUiState(
            stock = resources.crystal,
            lastSeenStock = lastSeen?.crystal ?: resources.crystal,
            ratePerHour = PlaceholderBalance.effectiveCrystalProductionPerHour(buildings, research).toRate(),
        ),
        deuterium = ResourceStockUiState(
            stock = resources.deuterium,
            lastSeenStock = lastSeen?.deuterium ?: resources.deuterium,
            ratePerHour = PlaceholderBalance.effectiveDeuteriumProductionPerHour(buildings, research).toRate(),
        ),
        // Derived from core, not handed down from the Colony screen: energy is a rule, and the rail
        // sits above every destination including the ones that have no colony ui-state to ask.
        throttled = PlaceholderBalance.energyBalance(buildings, research).isDeficit,
    )

private fun Long.toRate(): TextRes = Strings.ratePerHour(groupedByThousands())
