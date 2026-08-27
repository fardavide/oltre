package dev.fardavide.oltre.client.auth.presentation

import dev.fardavide.oltre.client.auth.ui.DeleteFactUiState
import dev.fardavide.oltre.client.auth.ui.DeleteFaceUiState
import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.design.text.AuthProviderName
import dev.fardavide.oltre.client.design.text.DeleteFactKind
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.playerProgress
import dev.fardavide.oltre.protocol.AuthProvider

// **Which of the two faces the sheet is wearing.** Two constants rather than a boolean, because
// *"true"* at a call site would not say which one, and the two are crossed in one direction only.
enum class DeleteFace {

    // All reading and no consequence: four rows of what exists, then the fact the numbers cannot
    // teach. Red begins here, as an outline.
    WARN,

    // The last step, and the only filled red button in the product.
    CONFIRM,
}

// **The face, from the colony it is about.** Reading *84 systems surveyed* is what makes the second
// sentence land, which is the whole reason the numbers are the colony's own rather than a warning
// written in the abstract.
//
// `offline` is the shell's to know and is a parameter rather than a fold: whether the server can be
// reached is a fact about the network, and nothing in a `GameState` has ever known one.
fun GameState.toDeleteFaceUiState(
    face: DeleteFace,
    provider: AuthProvider,
    offline: Boolean,
): DeleteFaceUiState {
    val name = Strings.playerDefaultName()
    val spoken = provider.spoken()
    return DeleteFaceUiState(
        title = if (face == DeleteFace.CONFIRM) Strings.deleteConfirmTitle(name) else Strings.deleteFaceTitle(),
        intro = if (face == DeleteFace.CONFIRM) Strings.deleteConfirmIntro() else Strings.deleteFaceIntro(),
        // **Gone on the last step**, because they were for reading and this face is for deciding.
        facts = if (face == DeleteFace.CONFIRM) emptyList() else facts(name),
        second = if (face == DeleteFace.CONFIRM) {
            Strings.deleteConfirmSecond(spoken)
        } else {
            Strings.deleteFaceSecond()
        },
        refusal = if (offline) {
            RefusalUiState(lead = Strings.refusedDeleteLead(), body = Strings.refusedDeleteBody())
        } else {
            null
        },
        keep = if (face == DeleteFace.CONFIRM) Strings.deleteKeep() else null,
        action = if (face == DeleteFace.CONFIRM) Strings.deleteConfirmAction() else Strings.deleteFaceAction(),
        destructive = face == DeleteFace.CONFIRM,
    )
}

// What the account holds, in four rows and in the colony's own numbers.
private fun GameState.facts(name: TextRes): List<DeleteFactUiState> = listOf(
    DeleteFactUiState(
        label = Strings.deleteFactLabel(DeleteFactKind.COLONY),
        // **Built facilities rather than all six**, because a facility at level 0 is one the colony
        // does not have — the Colony tab draws it as a locked row for exactly that reason.
        value = Strings.deleteFactColony(
            name = name,
            facilities = BuildingType.entries.count { buildings.levelOf(it).value > 0 },
            level = playerProgress().level.value,
        ),
    ),
    DeleteFactUiState(
        label = Strings.deleteFactLabel(DeleteFactKind.FLEET),
        // **Every hull, not the idle pool.** `ships` is what is at home; a run in flight carries its
        // own manifest, and a player about to delete an account is owed the whole fleet rather than
        // the part of it that happens to be in orbit.
        value = Strings.deleteFactFleet(hulls = wholeFleet().spoken(), runs = runs.size),
    ),
    DeleteFactUiState(
        label = Strings.deleteFactLabel(DeleteFactKind.MAP),
        value = Strings.deleteFactMap(
            // Systems rather than worlds: a survey is asked for by system and a player counts them
            // that way. `surveyed` holds coordinates, so the distinct addresses are the systems.
            surveyed = galaxy.surveyed.map { it.galaxy to it.system }.distinct().size,
            pinned = galaxy.pinned.size,
        ),
    ),
    DeleteFactUiState(
        label = Strings.deleteFactLabel(DeleteFactKind.RESEARCH),
        // **Levels rather than ladders**, which is what "9 projects" means on a screen where five
        // technologies can each be at level three: it is nine things bought, and that is the number a
        // player recognises as what they spent.
        value = Strings.deleteFactResearch(
            projects = Technology.entries.sumOf { research.levelOf(it).value },
            adaptations = AdaptationTechnology.entries.sumOf { research.levelOf(it).value },
        ),
    ),
)

private fun GameState.wholeFleet(): Ships =
    runs.fold(ships) { total, run -> total + run.ships }

// "4 skiffs and 1 hauler", in the language's own list punctuation. Hull types in their declared order
// rather than the map's, so two colonies with the same fleet read the same.
//
// **Null on a colony with no fleet**, which is a first launch rather than an edge case: the opening
// stock buys a hull and does not grant one. `Strings.listed` has no grammar for an empty list, and
// the row says so in a sentence instead — see `Strings.deleteFactFleet`.
private fun Ships.spoken(): TextRes? = ShipType.entries
    .mapNotNull { type -> counts[type]?.let { Strings.ships(it, type) } }
    .takeIf { it.isNotEmpty() }
    ?.let(Strings::listed)

private fun AuthProvider.spoken(): AuthProviderName = when (this) {
    AuthProvider.APPLE -> AuthProviderName.APPLE
    AuthProvider.GOOGLE -> AuthProviderName.GOOGLE
}
