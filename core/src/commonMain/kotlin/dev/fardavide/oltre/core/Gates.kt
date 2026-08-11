package dev.fardavide.oltre.core

// What a level of this building or technology opens for whoever reaches it.
//
// Every gate in the game is expressed exactly once, at the point that enforces it —
// `ResearchBalance.requirementFor`, `AdaptationBalance.requirementFor`, and the Nanite Factory's
// own check in `startUpgrade`. This inverts that index rather than restating it, so a gate that
// moves in the balance moves on the screen with it. The alternative, a hand-written table, is how
// the docs came to say "Robotics Factory 4" for a year after `AdaptationBalance.GATE` became 2.
data class Gate(val level: Int, val opens: GateSubject)

// Sealed and shaped like `WatchTarget`, because it names the same three kinds of thing a player can
// be waiting for. A reader has to answer for all three, which is what stops a fourth gate arriving
// silently.
sealed interface GateSubject {

    data class Facility(val building: BuildingType) : GateSubject

    data class Project(val technology: Technology) : GateSubject

    data class Ladder(val technology: AdaptationTechnology) : GateSubject
}

// In the order the player reaches them, which is the order a ladder is read in.
fun gatesOf(building: BuildingType): List<Gate> = buildList {
    if (building == BuildingType.ROBOTICS_FACTORY) {
        add(
            Gate(
                level = PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT,
                opens = GateSubject.Facility(BuildingType.NANITE_FACTORY),
            ),
        )
    }
    for (technology in Technology.entries) {
        val requirement = ResearchBalance.requirementFor(technology)
        if (requirement is ResearchRequirement.Facility && requirement.building == building) {
            add(Gate(level = requirement.level.value, opens = GateSubject.Project(technology)))
        }
    }
    for (technology in AdaptationTechnology.entries) {
        val requirement = AdaptationBalance.requirementFor(technology)
        if (requirement is ResearchRequirement.Facility && requirement.building == building) {
            add(Gate(level = requirement.level.value, opens = GateSubject.Ladder(technology)))
        }
    }
}.sortedBy { it.level }

fun gatesOf(technology: Technology): List<Gate> = Technology.entries
    .mapNotNull { other ->
        val requirement = ResearchBalance.requirementFor(other)
        if (requirement is ResearchRequirement.Tech && requirement.technology == technology) {
            Gate(level = requirement.level.value, opens = GateSubject.Project(other))
        } else {
            null
        }
    }
    .sortedBy { it.level }
