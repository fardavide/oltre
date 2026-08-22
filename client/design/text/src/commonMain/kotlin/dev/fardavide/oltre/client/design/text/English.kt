package dev.fardavide.oltre.client.design.text

// **The one language the game speaks, and the first one it has ever written down.** Until this
// module every string was a literal at the point it was drawn; what changed is not the words — every
// one of them below is the string that was already there, character for character, which is why no
// screenshot baseline moved — but that they are now in one place and reachable by id.
//
// The `when` is exhaustive with no `else`. That is the whole mechanism: a new `StringId` fails to
// compile here, so a second language cannot be half-written.
object English : Translations {

    override fun resolve(id: StringId, args: List<Arg>): String = when (id) {

        // ── How the game writes numbers, durations and lists ─────────────────────────────────
        StringId.ClauseSeparator -> " · "
        StringId.GroupedNumber -> args.number(0).toString().grouped()
        // A true minus sign rather than a hyphen, matching the design.
        StringId.Signed -> args.number(0).let { if (it < 0) "−${-it}" else "+$it" }
        StringId.Decimal -> args.decimal(0)
        StringId.DurationMinutes -> "${args.number(0)}m"
        StringId.DurationHoursMinutes -> "${args.number(0)}h ${args.number(1).pad2()}m"
        StringId.DurationHours -> "${args.number(0)}h"
        // The day is never padded, because "04d" reads like a countdown to a launch rather than a
        // wait; the hour beside it is, so a column of these stays tabular.
        StringId.DurationDaysHours -> "${args.number(0)}d ${args.number(1).pad2()}h"
        StringId.Countdown -> "${args.number(0).pad2()}:${args.number(1).pad2()}:${args.number(2).pad2()}"
        StringId.WatchedAt -> "→ affordable ${args.clock()}"

        // ── Wall-clock instants ──────────────────────────────────────────────────────────────
        StringId.DoneAt -> "done ${args.clock()}"
        StringId.HomeAt -> "home ${args.clock()}"
        StringId.LandsAt -> "lands ${args.clock()}"
        StringId.LandedAt -> "landed ${args.clock()}"
        StringId.ProbeLandedAt -> "Probe landed ${args.clock()}"

        // ── The design system's own words ────────────────────────────────────────────────────
        StringId.LevelBadge -> "LV ${args.number(0)}"

        // ── Vocabulary every screen shares ───────────────────────────────────────────────────
        StringId.SentenceSeparator -> " "
        StringId.ResourceNameMetal -> "metal"
        StringId.ResourceNameCrystal -> "crystal"
        StringId.ResourceNameDeuterium -> "deuterium"
        StringId.ResourceTitleMetal -> "Metal"
        StringId.ResourceTitleCrystal -> "Crystal"
        StringId.ResourceTitleDeuterium -> "Deuterium"
        StringId.PlainNumber -> args.number(0).toString()
        StringId.Percent -> "${args.number(0)}%"
        StringId.PerHour -> "${args.text(0)}/h"
        StringId.PlusPerHour -> "+${args.text(0)}/h"
        StringId.PlusAmount -> "+${args.text(0)}"
        StringId.CoordinateLabel -> "[${args.number(0)}:${args.number(1)}:${args.number(2)}]"
        StringId.AmountOfResource -> "${args.text(0)} ${args.text(1)}"
        StringId.ResourceReading -> "${args.text(0)} ${args.text(1)}"

        // ── Shipyard ─────────────────────────────────────────────────────────────────────────
        StringId.ShipyardHeading -> "HULLS"
        StringId.ShipyardNotYetBuiltHeading -> "NOT YET BUILT"
        StringId.ShipyardNote ->
            "Every hull costs the same, and the yard builds one at a time. A Metal Mine level " +
                "returns more per unit spent — the fleet is bought because it pays in the resource " +
                "you choose, not because it pays better."
        StringId.HullsInFleet -> args.count(0).let { "$it ${it.plural("hull", "hulls")}" }
        StringId.ShipsOwned -> "${args.count(0)} owned"
        StringId.ShipsIdle -> "${args.count(0)} idle"
        StringId.ShipsAway -> "${args.count(0)} away"
        StringId.ShipsBuilding -> "${args.count(0)} building"
        StringId.ShipsQueued -> "${args.count(0)} queued"
        StringId.Build -> "Build"
        StringId.AvailableIn -> "in ${args.text(0)}"
        StringId.AvailableNever -> "—"
        // The probe footer's answer when the fleet rather than the bank is what is short. It
        // names the shop rather than a wait, because unlike every other unaffordable state in
        // the game this one is not answered by standing still.
        StringId.ProbeNeedsScout -> "needs a scout"
        StringId.ScoutName -> "Scout"
        StringId.ScoutPurpose -> "No hold · the only hull that can survey"
        StringId.SkiffName -> "Skiff"
        // **The rate went when the drive arrived.** This read "10m + 1m per 10 units, one way"
        // until 0.15, which was true of a flight curve that had no research term in it. Propulsion
        // put one there, so any absolute figure on this card is wrong at every level but one — and
        // the clause the card is for is the *trade*, which is what the hauler beneath it answers.
        StringId.SkiffPurpose -> "One berth of hold · full speed"
        StringId.HaulerName -> "Hauler"
        StringId.HaulerPurpose -> "Four berths of hold, at half a skiff's speed."

        // ── The dispatch sheet ───────────────────────────────────────────────────────────────
        StringId.Hazards -> when (args.count(0)) {
            0 -> "no hazards"
            1 -> "one hazard"
            else -> "two hazards"
        }
        StringId.HazardsAtDistance -> "${args.text(0)}, ${args.text(1)}"
        StringId.ChartedUnsurveyed -> "charted · unsurveyed"
        StringId.DispatchUnsurveyedTitle -> "A hold cannot be priced from a world nobody has looked at."
        StringId.DispatchUnsurveyedNote ->
            "Richness and hazards need a survey. A probe surveys all ${args.number(0)} slots at " +
                "once, and this system holds ${args.count(1)} ${args.count(1).plural("world", "worlds")}."
        StringId.DispatchProbeOffer -> "${args.text(0)} metal · ${args.text(1)}."
        StringId.DispatchEverySkiffAwayTitle -> "Every hull is away."
        // **A world no window can reach**, which 0.15 made ordinary rather than exotic: base
        // flight speed halved, so two galaxy hops is 36h 20m out and back and the longest rung is
        // 24h. It is a refusal with a remedy, and the remedy is the drive — which is why the note
        // names it rather than the distance.
        // **The ordinary first check-in of a 0.15 colony**, not an edge case. Genesis grants no
        // hulls, the Shipyard puts the scout first because it is the cheapest and the only thing
        // that surveys, and a scout gathers nothing — so the first sheet a player opens on the
        // world their first probe charted is this one. It used to read "Every hull is away" over a
        // scout sitting idle in the yard, and then "Nothing is idle and nothing is out" under it.
        StringId.DispatchNoGatheringHullTitle -> "Nothing here can gather."
        StringId.DispatchNoGatheringHullNote -> "A scout charts a world; a skiff or a hauler is what lifts anything off it."
        StringId.DispatchOutOfReachTitle -> "Too far for any window."
        StringId.DispatchOutOfReachNote -> "No fleet can be back inside a day. Propulsion is what shortens the trip."
        StringId.DispatchAwayNote ->
            "${args.count(0)} ${args.count(0).plural("run is", "runs are")} out. " +
                "${args.text(1)} is inbound with ${args.text(2)} ${args.text(3)}."
        StringId.DispatchAwayMore -> "${args.count(0)} more behind it."
        StringId.DispatchAwayTail -> "A hull is idle only once it is home."
        StringId.DepositFull -> "deposit full"
        StringId.DepositEmpty -> "deposit empty"
        StringId.DepositStock -> "deposit ${args.text(0)}/${args.text(1)}"
        StringId.Richness -> "richness ${args.text(0)}"
        StringId.SkiffCount -> args.count(0).let { "$it ${it.plural("skiff", "skiffs")}" }
        // **The eleven strings of *Twice the Flight*.** Two rules hold across all of them — muted
        // states a rule that was already true, body states something that just changed; and each
        // control gets at most one note, where the clamp wins because it is about the run being sent.
        StringId.BerthCount -> args.count(0).let { "$it ${it.plural("berth", "berths")}" }
        StringId.PoolIdle -> "${args.text(0)} idle"
        StringId.ManifestPair -> "${args.text(0)} · ${args.text(1)}"
        StringId.OutAndBack -> "${args.text(0)} out and back"
        // The locked rung's second line: the hull that would fly it. Not a sentence — it is the
        // locked-facility idiom's requirement, in the same 9.5 caption.
        StringId.RungRequiresSkiffs -> "skiffs"
        StringId.LadderRungMoved -> "The hauler moved this run to ${args.text(0)}, the shortest window it fits."
        StringId.LadderShortestFit -> "${args.text(0)} is the shortest window the hauler fits."
        StringId.CellCounterfactual -> "Skiffs only lift ${args.text(0)} ${args.text(1)}, and only skiffs fly ${args.text(2)}."
        StringId.CellRungConsequence -> "The hauler lifts ${args.text(0)} ${args.text(1)} and lands at ${args.text(2)}."
        StringId.CellClamped -> "The hauler empties it. The ${args.text(0)} bring nothing."
        // **A count of one needs its own sentence, not the same one with a smaller number in it.**
        // The plural form reads "The 1 skiff bring nothing" at a pool of one hauler and one skiff —
        // which is a pool a colony passes through on its way to any mixed fleet, so it is the common
        // case rather than the edge. Dropping the digit is what makes it a sentence again.
        StringId.CellClampedOne -> "The hauler empties it. The skiff brings nothing."
        StringId.OfIdle -> "of ${args.count(0)} idle"
        StringId.LadderNote ->
            "${args.text(0)} out and back. No shorter window leaves ${args.number(1)} minutes on the surface."
        StringId.RungNote -> "The ${args.text(0)} window brings the same."
        StringId.ClampSubject ->
            args.count(0).let { "$it ${it.plural("skiff empties", "skiffs empty")} it." }
        StringId.ClampRestOrdinal -> "The ${args.number(0).ordinal()} brings nothing."
        StringId.ClampRestOthers ->
            args.count(0).let { "The other $it ${it.plural("brings", "bring")} nothing." }
        StringId.TheWholeDeposit -> "the whole deposit"
        // **What the slot beside the figure says since 0.15.0** — Design's copy list moves it from
        // the per-ship reading to the vein: what this run would leave behind. `449 each` is under
        // its *Retired* heading, because a mixed manifest has no answer for "each".
        StringId.VeinLeft -> "${args.text(0)} left in the ground"
        StringId.EachShip -> "${args.text(0)} each"
        StringId.LegOut -> "out ${args.text(0)}"
        StringId.LegOnStation -> "on station ${args.text(0)}"
        StringId.LegStation -> "station ${args.text(0)}"
        StringId.LegWorking -> "working ${args.text(0)}"
        StringId.LegHome -> "home ${args.text(0)}"
        StringId.DangerLevel -> "danger ${args.number(0)}"
        StringId.DangerNothingAdded -> "nothing added"
        StringId.DangerBonus -> "+${args.number(0)}% of the hold"
        StringId.YourOwnSystem -> "your own system"
        StringId.YourOwnSystemCapitalised -> "Your own system"
        StringId.AnotherGalaxy -> "another galaxy"
        StringId.UnitsOut -> "${args.text(0)} units out"
        StringId.BothDepositsEmpty -> "Both deposits are empty."
        StringId.ThisDepositEmpty -> "This deposit is empty."
        StringId.WaitingAsk ->
            "${args.text(0)} at ${args.text(1)} would lift ${args.text(2)} ${args.text(3)}."
        StringId.WaitingHoldsAgain -> "The world holds that much again in ${args.text(0)}."
        StringId.WaitingNeverHolds -> "No world this size ever holds that much."
        StringId.WaitingRemedy -> "Fewer skiffs, or a shorter window, is sooner."
        StringId.ControlBringBack -> "BRING BACK"
        StringId.ControlSend -> "SEND"
        StringId.ControlHomeIn -> "HOME IN"
        StringId.DispatchVerb -> "Dispatch"

        // ── What the game's things are called ────────────────────────────────────────────────
        StringId.BuildingNameMetalMine -> "Metal Mine"
        StringId.BuildingNameCrystalMine -> "Crystal Mine"
        StringId.BuildingNameDeuteriumSynthesizer -> "Deuterium Synth."
        StringId.BuildingNameSolarPlant -> "Solar Plant"
        StringId.BuildingNameRoboticsFactory -> "Robotics Factory"
        StringId.BuildingNameNaniteFactory -> "Nanite Factory"
        // **One name shortens at 320dp and the other five do not**, which is a measurement rather
        // than a style: at that width only "Robotics Factory" runs past the name column. The short
        // form is not invented either — it is what the Research screen already prints in
        // "Requires Robotics 10".
        StringId.BuildingCompactNameMetalMine -> "Metal Mine"
        StringId.BuildingCompactNameCrystalMine -> "Crystal Mine"
        StringId.BuildingCompactNameDeuteriumSynthesizer -> "Deuterium Synth."
        StringId.BuildingCompactNameSolarPlant -> "Solar Plant"
        StringId.BuildingCompactNameRoboticsFactory -> "Robotics"
        StringId.BuildingCompactNameNaniteFactory -> "Nanite Factory"
        StringId.ShipNameScout -> "scout"
        StringId.ShipNameSkiff -> "skiff"
        StringId.ShipNameHauler -> "hauler"
        StringId.ShipNameEscort -> "escort"
        StringId.ShipNameSettler -> "settler"
        StringId.ShipsOfType -> "${args.count(0)} ${args.text(1)}"

        // ── The Colony tab ───────────────────────────────────────────────────────────────────
        StringId.ColonyFacilitiesHeading -> "FACILITIES"
        StringId.PowerHeading -> "POWER"
        StringId.EnergyEveryMineStopped -> "every mine stopped"
        StringId.EnergyEveryMineAt -> "every mine at ${args.number(0)}%"
        StringId.EnergyBreakEven -> "break even"
        StringId.EnergyRoomForMineLevels ->
            args.count(0).let { "room for $it mine ${it.plural("level", "levels")}" }
        StringId.EnergyProduced -> "${args.text(0)} produced"
        StringId.EnergyDrawn -> "${args.text(0)} drawn"
        StringId.EnergyShort -> "${args.text(0)} short"
        StringId.EnergySpare -> "${args.text(0)} spare"
        StringId.OnStationAt -> "On station at ${args.text(0)}"
        StringId.FleetReturning -> "Fleet returning"
        StringId.FromTarget -> "from ${args.text(0)}"
        StringId.MoreAway -> "${args.count(0)} more away"
        StringId.PowerSupply -> "+${args.text(0)}"
        StringId.PowerDraw -> "−${args.text(0)}"
        StringId.SolarFix -> "→ LV ${args.number(0)} covers all ${args.text(1)} drawn"
        StringId.OutputGain -> "+${args.text(0)}/h ${args.text(1)}"
        StringId.BackIn -> "back in ${args.text(0)}"
        StringId.SuppliesMore -> "+${args.text(0)} supply"
        StringId.DrawAlreadyCovered -> "draw already covered"
        StringId.ThrottlesEveryMine -> "throttles every mine"
        StringId.SolarPlantCovers -> "Solar Plant ${args.number(0)} covers it"
        StringId.SavedPerBuild -> "−${args.text(0)} per build"
        StringId.GateClause -> "LV ${args.number(0)} → ${args.text(1)}"
        StringId.GateSummaryNanite -> "Nanite"
        StringId.GateSummaryAdaptationShort -> "adaptation"
        StringId.GateSummaryAdaptationLong -> "the three adaptation ladders"
        StringId.GateSummaryResearchShort -> "research"
        StringId.GateSummaryResearchLong -> "applied research"
        StringId.GateFacilityLong -> "${args.text(0)} · ${args.text(1)} metal"
        StringId.LadderStepHeld -> "${args.text(0)} · you have this"
        StringId.NaniteReliefLong ->
            "A ${args.text(0)} build takes ${args.text(1)} at LV ${args.number(2)}"
        StringId.NaniteReliefShort ->
            "${args.text(0)} builds take ${args.text(1)} at LV ${args.number(2)}"
        StringId.RequiresRobotics -> "Requires Robotics ${args.number(0)}"
        StringId.BecomesLevel -> "→ LV ${args.number(0)}"
        StringId.UpgradeVerb -> "Upgrade"
        StringId.PointerLevelStep -> "LV ${args.number(0)} → ${args.number(1)} · ${args.text(2)}"
        StringId.PointerBestBuy -> "LV ${args.number(0)} · back in ${args.text(1)}"

        // ── The Colony sheet's prose ─────────────────────────────────────────────────────────
        StringId.SheetMineMakes -> "Your colony makes "
        StringId.SheetMineAtLevel -> " ${args.text(0)}. At LV ${args.number(1)} it makes "
        // One entry for the bare stop that ends four of these sentences: a full stop is a full
        // stop, and three ids for it would be three chances to translate it differently.
        StringId.SheetFullStop -> "."
        StringId.SheetPlantsSupply -> "Your plants supply "
        StringId.SheetColonyDraws -> " energy. The colony draws "
        StringId.SheetSoEveryMineAt -> ", so every mine is running at "
        StringId.SheetThisLevelLifts -> "This level lifts that, which is why it reads as "
        StringId.SheetRatherThanEnergy -> " ${args.text(0)} rather than as energy."
        StringId.SheetPaybackPrefix -> "Counted against everything the level costs, you are even after "
        StringId.SheetSupplyNotLimiting -> "Supply is not what is limiting you, so a level that adds "
        StringId.SheetChangesNoRate -> " changes no rate."
        StringId.SheetPaysNextMineLevel -> "It starts to pay with the next mine level you take."
        StringId.SheetPaysWhenDrawPasses -> "It starts to pay when draw passes supply — about "
        StringId.SheetMoreMineLevelAway -> " more mine level away."
        StringId.SheetMoreMineLevelsAway -> " more mine levels away."
        StringId.SheetOneSpelled -> "one"
        StringId.SheetCannotPowerLevel ->
            "The colony cannot power this level. Taking it would throttle every mine you have " +
                "rather than raise anything."
        StringId.SheetPlantCarriesPrefix -> "A Solar Plant at LV "
        StringId.SheetPlantCarriesSuffix ->
            " carries the new draw. Build that first and this level becomes what it looks like."
        StringId.SheetShortensDeepBuild ->
            "Takes the late game's waits apart. It is the only thing in the game that shortens a deep build."
        StringId.SheetShortensEveryBuild ->
            "Shortens every build on this colony and every research in the empire. " +
                "It raises no output of its own."
        StringId.SheetNextBuildTakes -> "Your next ${args.text(0)} takes "
        StringId.SheetAtBuildingLevelTakes -> ". At ${args.text(0)} ${args.number(1)} it takes "
        StringId.SheetNaniteMineTakes -> "A level-${args.number(0)} Metal Mine takes "
        StringId.SheetNaniteUnaidedAt -> " unaided. At ${args.number(0)} Nanite levels it takes "
        StringId.SheetRoboticsIsAt -> "Your Robotics Factory is at "
        StringId.SheetLevelsToGo -> args.count(0).let {
            ". ${if (it == 1) "One level" else "$it levels"} to go, and the first Nanite level costs "
        }
        StringId.SheetMetalSuffix -> " metal."

        // ── The Research tab ─────────────────────────────────────────────────────────────────
        StringId.TechnologyNamePhotovoltaics -> "Photovoltaics"
        StringId.TechnologyNameExtraction -> "Extraction"
        StringId.TechnologyNameEnrichment -> "Enrichment"
        StringId.TechnologyNameProspecting -> "Prospecting"
        StringId.TechnologyNamePropulsion -> "Propulsion"
        StringId.AdaptationNameThermal -> "Thermal"
        StringId.AdaptationNameGravitic -> "Gravitic"
        StringId.AdaptationNameAtmospheric -> "Atmospheric"
        StringId.TechnologySubjectPhotovoltaics -> "Solar Plant output"
        StringId.TechnologySubjectExtraction -> "metal · crystal output"
        StringId.TechnologySubjectEnrichment -> "deuterium output"
        // PLACEHOLDER, and the one string this version owes: every other row names a rate the
        // colony produces, and this is the first whose payoff is measured in a run.
        StringId.TechnologySubjectProspecting -> "what a fleet lifts"
        // The one subject that is a *divisor* rather than a rate: the percentage is how much
        // further a hull travels in the same minute, which is why the noun is a speed and not an
        // output. Same standing as the row above — a placeholder until Davide names it.
        StringId.TechnologySubjectPropulsion -> "fleet speed"
        StringId.AdaptationUnitThermal -> "°C"
        StringId.AdaptationUnitGravitic -> "g"
        StringId.AdaptationUnitAtmospheric -> "atm"
        StringId.BuildingShortNameMetalMine -> "Metal Mine"
        StringId.BuildingShortNameCrystalMine -> "Crystal Mine"
        StringId.BuildingShortNameDeuteriumSynthesizer -> "Deuterium"
        StringId.BuildingShortNameSolarPlant -> "Solar Plant"
        StringId.BuildingShortNameRoboticsFactory -> "Robotics"
        StringId.BuildingShortNameNaniteFactory -> "Nanite"
        StringId.ResearchHeading -> "TECHNOLOGIES"
        StringId.AdaptationHeading -> "ADAPTATION"
        StringId.RuleOneProjectAtATime -> "one project at a time"
        StringId.RuleOneLadderAtATime -> "one ladder at a time"
        StringId.RuleOneAtATime -> "one at a time"
        StringId.ResearchVerb -> "Research"
        StringId.VerdictNothingThrottled -> "nothing while your mines are throttled"
        StringId.VerdictNothingThrottledCompact -> "nothing while throttled"
        StringId.VerdictNothingSurplus -> "nothing while you are in surplus"
        StringId.VerdictNothingSurplusCompact -> "nothing while in surplus"
        StringId.HaulGain -> "+${args.text(0)} a hull an hour on station"
        StringId.HaulGainCompact -> "+${args.text(0)} a hull an hour"
        // The drive's row, and the one verdict on this screen measured in hours saved rather
        // than units gained. Same standing as the two above: the figure is exact, the noun is
        // provisional until Davide names it.
        StringId.ReachGain -> "the next galaxy in ${args.text(0)}, from ${args.text(1)}"
        StringId.ReachGainCompact -> "next galaxy ${args.text(0)}"
        StringId.PlusPercent -> "+${args.number(0)}%"
        StringId.ToleranceBand -> "${args.text(0)} … ${args.text(1)}"
        StringId.Requires -> "Requires ${args.text(0)}"
        StringId.NamedLevel -> "${args.text(0)} ${args.number(1)}"
        StringId.ShortlistNothingVerb -> "Unlocks nothing you have surveyed"
        StringId.ShortlistNothing -> "Nothing you have surveyed"
        StringId.ShortlistVerb -> "Unlocks ${args.shortlistCounts()}"
        StringId.Shortlist -> args.shortlistCounts()

        // ── The Research sheet's prose ───────────────────────────────────────────────────────
        StringId.SheetSubjectPrefix -> "${args.text(0)}: "
        StringId.SheetArrow -> " → "
        StringId.SheetAtLevelOne -> " at LV 1."
        StringId.SheetToleranceSubject -> "${args.text(0)} tolerance: "
        StringId.SheetReachesNothing ->
            "Nothing you have surveyed is blocked by this band alone, so this level reaches no new world."
        StringId.SheetReachesPrefix -> "Of the worlds you have surveyed this level reaches "
        StringId.SheetReachesMiddle -> ", and "
        StringId.SheetReachesSuffix -> " of those are worth taking."
        StringId.SheetAndWouldMake -> " ${args.text(0)} and would make "
        StringId.SheetEachHullLifts -> "Each hull lifts "
        StringId.SheetAnHourOnStation -> " an hour on station and would lift "
        StringId.SheetPaysOnNextRun -> "It pays on the next run rather than on a clock at home."
        StringId.SheetTheNextGalaxyIs -> "The next galaxy is "
        StringId.SheetAwayAndWouldBe -> " away and would be "
        StringId.SheetPaysTheFurtherYouAim -> "It buys nothing next door and everything at the frontier."
        StringId.SheetColonyDrawsAnd -> " energy and the colony draws "
        StringId.SheetAtThatRatio -> "At that ratio ${args.text(0)}'s "
        StringId.SheetRoundsAway -> " rounds away before it reaches your stores."
        StringId.SheetPaysWhenPlantsCarry -> "It starts to pay when your plants carry the draw again."
        StringId.SheetMultipliesSupply ->
            "${args.text(0)} multiplies supply, and supply is not what is limiting you. At "
        StringId.SheetOutputDoesNotMove -> " your output does not move."
        StringId.SheetRequiresPrefix -> "Requires "

        // ── The Fleets tab ───────────────────────────────────────────────────────────────────
        StringId.ShipsScout -> args.count(0).let { "$it ${it.plural("scout", "scouts")}" }
        StringId.ShipsSkiff -> args.count(0).let { "$it ${it.plural("skiff", "skiffs")}" }
        StringId.ShipsHauler -> args.count(0).let { "$it ${it.plural("hauler", "haulers")}" }
        StringId.ShipsEscort -> args.count(0).let { "$it ${it.plural("escort", "escorts")}" }
        StringId.ShipsSettler -> args.count(0).let { "$it ${it.plural("settler", "settlers")}" }
        StringId.FleetsHeading -> "IN FLIGHT"
        StringId.FleetsAwayOf -> "${args.number(0)} of ${args.number(1)} away"
        StringId.FleetsNothingOut -> "Nothing is out. A run starts from a world on the Galaxy tab."
        StringId.WorkedHeading -> "WORLDS WORKED"
        StringId.WorkedNewestFirst -> "newest first"
        StringId.RunCount -> args.count(0).let { "$it ${it.plural("run", "runs")}" }
        StringId.UnrecordedRuns -> args.count(0).let {
            "$it earlier ${it.plural("run", "runs")} · ${args.text(1)} ${args.text(2)} · no target recorded"
        }
        StringId.DepositFullWord -> "full"
        StringId.DepositEmptyWord -> "empty"
        StringId.WorldRowPrefix -> "${args.text(0)} · ${args.text(1)}"
        StringId.WorldRowSeparator -> "${args.text(0)} · "

        // ── The Galaxy tab ───────────────────────────────────────────────────────────────────
        StringId.GalaxyLabel -> "G${args.number(0)}"
        StringId.GalaxyNamed -> "Galaxy ${args.number(0)}"
        StringId.GalaxiesCount -> args.count(0).let { "$it ${it.plural("galaxy", "galaxies")}" }
        StringId.SystemsCount -> "${args.text(0)} systems"
        StringId.SurveyedCount -> "${args.count(0)} surveyed"
        StringId.PinnedCount -> "${args.count(0)} pinned"
        StringId.NothingCharted -> "nothing charted"
        StringId.SystemAddressLabel -> "[${args.number(0)}:${args.number(1)}]"
        StringId.RelayEffect -> "Relay · contested · +18% range while held"
        StringId.DangerFromHere -> "danger ${args.number(0)} from here"
        StringId.ReachSingle -> "${args.text(0)} out and back"
        StringId.ReachRange -> "${args.text(0)}–${args.text(1)} out and back"
        // "20–26m" rather than "20m–26m": the near end loses its unit because the far end carries
        // one, which only works while both ends are minutes.
        StringId.ReachRangeMinutes -> "${args.number(0)}–${args.text(1)} out and back"
        StringId.StarClassDim -> "dim"
        StringId.StarClassStandard -> "standard"
        StringId.StarClassBright -> "bright"
        StringId.StarDetail -> args.count(1).let { "${args.text(0)} · $it ${it.plural("world", "worlds")}" }
        StringId.StarDetailCompact -> "${args.text(0)} · ${args.count(1)}"
        StringId.WorldCount -> args.count(0).let {
            if (it == 0) "no worlds" else "$it ${it.plural("world", "worlds")}"
        }
        StringId.WorldsSurveyedCount ->
            args.count(0).let { "$it ${it.plural("world", "worlds")} surveyed" }
        StringId.NoWorlds -> "no worlds"
        StringId.HomeNote -> "home"
        StringId.ProbeLandsIn -> "probe lands in ${args.text(0)}"
        StringId.ProbeFlight -> "probe ${args.text(0)}"
        StringId.ProbeFlightLabel -> "flight ${args.text(0)}"
        StringId.RunFlight -> "run ${args.text(0)}"
        StringId.NothingToSurvey -> "${args.number(0)} empty slots · nothing to survey"
        StringId.SurveyedAtGenesis -> "Surveyed at genesis"
        StringId.DispatchProbe -> "Dispatch probe"
        StringId.DispatchProbeCompact -> "Dispatch"
        StringId.FindSettleable -> "${args.count(0)} settleable"
        StringId.FindNearMiss -> "${args.count(0)} blocked at one axis"
        StringId.FindNone -> "none settleable"
        StringId.TemperatureReading -> "${args.text(0)}$UNIT_GAP°C"
        StringId.GravityReading -> "${args.text(0)}${UNIT_GAP}g"
        StringId.PressureReading -> "${args.text(0)}${UNIT_GAP}atm"
        StringId.FoundAgo -> "found ${args.text(0)} ago"
        StringId.AxisTemperature -> "temperature"
        StringId.AxisGravity -> "gravity"
        StringId.AxisPressure -> "pressure"
        // Adjective then noun, which is where English keeps them and is the whole reason this is one
        // entry over two arguments rather than a joined pair.
        StringId.WorldEpithet -> "${args.text(0)} ${args.text(1)}"
        StringId.EpithetNounFurnace -> "furnace"
        StringId.EpithetNounFrost -> "frost"
        StringId.EpithetNounGiant -> "giant"
        StringId.EpithetNounHusk -> "husk"
        StringId.EpithetNounShroud -> "shroud"
        StringId.EpithetNounWaste -> "waste"
        StringId.EpithetNounWorld -> "world"
        StringId.EpithetAdjectiveScorched -> "scorched"
        StringId.EpithetAdjectiveFrozen -> "frozen"
        StringId.EpithetAdjectiveIron -> "iron"
        StringId.EpithetAdjectiveHollow -> "hollow"
        StringId.EpithetAdjectiveVeiled -> "veiled"
        StringId.EpithetAdjectiveAirless -> "airless"
        StringId.EpithetAdjectiveAshen -> "ashen"
        StringId.EpithetAdjectiveDeep -> "deep"
        StringId.EpithetAdjectiveBrittle -> "brittle"
        StringId.EpithetAdjectiveDrowned -> "drowned"
        StringId.EpithetAdjectiveBare -> "bare"
        StringId.EpithetAdjectiveTemperate -> "temperate"

        StringId.NoteHome -> "Your colony."
        StringId.NoteOccupied -> "Held by ${args.text(0)}."
        StringId.NoteSettleable -> "Nothing here blocks a colony."
        StringId.NoteBarren -> "Yield ${args.text(0)}, worth it at ${args.text(1)}"
        StringId.NoteBarrenDiscovery -> "Passes every band, and not worth taking."
        StringId.NoteBlocked -> "Blocked."
        StringId.NoteWouldLandIt -> "${args.text(0)} ${args.number(1)} would land it."
        StringId.NoteSurveyed -> "Surveyed."
        StringId.WorthItAt -> "worth it at ${args.text(0)}"
        StringId.DepositFraction -> "${args.text(0)}/${args.text(1)}"
        StringId.LedgerEmptyHeadline -> "Every world a probe reaches lands here."
        StringId.LedgerEmptyDetail -> "You have surveyed nothing yet."
        StringId.LedgerNoMatchHeadline -> "No world you know is called that."
        StringId.LedgerNoMatchDetail -> "Names are unique in a galaxy, so a full name finds one place."
        StringId.VerdictWordHome -> "Home"
        StringId.VerdictWordOccupied -> "Occupied"
        StringId.VerdictWordBlocked -> "Blocked"
        StringId.VerdictWordBarren -> "Barren"
        StringId.VerdictWordSettleable -> "Settleable"
        StringId.DiscoveriesHeadingOne -> "SURVEYED"
        StringId.DiscoveriesHeadingMany -> "${args.count(0)} WORLDS SURVEYED"
        StringId.PinnedHeading -> "PINNED"
        StringId.RelayLabel -> "RELAY"
        StringId.LedgerModeWorlds -> "worlds"
        StringId.LedgerModeMap -> "map"
        StringId.SearchPlaceholder -> "name"
        StringId.BlockedAxisLine -> "${args.text(0)} ${args.text(1)}, you tolerate ${args.text(2)}"
        StringId.MiddotStandalone -> "·"
        StringId.OrbitSlot -> "${args.number(0)}"

        // ── The shell ────────────────────────────────────────────────────────────────────────
        StringId.TabColony -> "Colony"
        StringId.TabResearch -> "Research"
        StringId.TabShipyard -> "Shipyard"
        StringId.TabGalaxy -> "Galaxy"
        StringId.TabFleets -> "Fleets"
        StringId.StepperFewer -> "−"
        StringId.StepperMore -> "+"
        StringId.DepositGap -> " "
        StringId.NotificationChannelName -> "Colony events"
        StringId.NotificationChannelDescription ->
            "Tells you when a build or a research project has finished, and when a fleet lands."
        // A navigation term: a position computed from a known start, an elapsed time and a speed —
        // which is exactly and only what this game does to a colony on foreground. Title Case
        // because it is a proper noun, which is the one thing this app capitalises.
        StringId.PlayerDefaultName -> "Dead Reckoning"
        StringId.SettingsComingSoon -> "Coming soon"
        StringId.Watching -> "watching ${args.text(0)}"
        StringId.RatePerHour -> "+${args.text(0)}/h"
        StringId.ResourceRailMetal -> "METAL"
        StringId.ResourceRailCrystal -> "CRYSTAL"
        StringId.ResourceRailDeuterium -> "DEUTERIUM"

        // ── Notifications ────────────────────────────────────────────────────────────────────
        StringId.ListSeparator -> ", "
        // No Oxford comma, which is the prose style of everything else the game says.
        StringId.ListLastSeparator -> " and "
        StringId.UpgradesDoneTitle -> "${args.count(0).spelled()} upgrades are done"
        StringId.UpgradesDoneBody -> "${args.text(0)} — pick what your colony builds next."
        StringId.ReachedLevel -> "${args.text(0)} reached level ${args.number(1)}"
        StringId.BuildCompleteBody -> "Construction is complete — pick what your colony builds next."
        StringId.LabFreeBody -> "The lab is free — pick what your empire researches next."
        StringId.AdaptationOpenedBody -> "Worlds you could not settle may have opened up — check the galaxy."
        StringId.HullLeftYardTitle -> "A ${args.text(0)} has left the yard"
        StringId.HullLeftYardBody -> "It is in your fleet and ready to send."
        // **A digit where the upgrade group above spells its count**, and the asymmetry is the model's
        // rather than a slip. Completions are capped at eight by what a colony can have in flight, so
        // `spelled()` can cover every one of them; a yard queue has no cap at all — a check-in that
        // can pay for two hundred hulls buys two hundred — and a table that spelled this would need a
        // word for every number there is or an `else` that lies.
        StringId.HullOrderDoneTitle ->
            args.count(0).let { "$it ${it.plural("hull", "hulls")} have left the yard" }
        StringId.HullOrderDoneBody -> "Your ${args.text(0)} order is complete — they are in your fleet and ready to send."
        StringId.ProbeReachedTitle -> "Your probe reached ${args.text(0)}"
        StringId.ChartedNoneSettleable ->
            args.count(0).let { "$it ${it.plural("world", "worlds")} charted, none settleable." }
        StringId.ChartedSettleable -> args.count(0).let {
            "$it ${it.plural("world", "worlds")} charted, ${args.count(1)} settleable."
        }
        StringId.ShipsHomeTitle -> "Your ships are home"
        StringId.ShipsHomeBody -> "The cargo from ${args.text(0)} is in your stores."
        StringId.AffordableTitle -> "You can afford ${args.text(0)}"
        StringId.AffordableBody -> "The colony has the resources for level ${args.number(0)}."
        StringId.SystemAddressBare -> "${args.number(0)}:${args.number(1)}"
        StringId.BuildingFullNameMetalMine -> "Metal Mine"
        StringId.BuildingFullNameCrystalMine -> "Crystal Mine"
        StringId.BuildingFullNameDeuteriumSynthesizer -> "Deuterium Synthesizer"
        StringId.BuildingFullNameSolarPlant -> "Solar Plant"
        StringId.BuildingFullNameRoboticsFactory -> "Robotics Factory"
        StringId.BuildingFullNameNaniteFactory -> "Nanite Factory"
        StringId.ShipTitleNameScout -> "Scout"
        StringId.ShipTitleNameSkiff -> "Skiff"
        StringId.ShipTitleNameHauler -> "Hauler"
        StringId.ShipTitleNameEscort -> "Escort"
        StringId.ShipTitleNameSettler -> "Settler"
        StringId.AdaptationFullNameThermal -> "Thermal Adaptation"
        StringId.AdaptationFullNameGravitic -> "Gravitic Adaptation"
        StringId.AdaptationFullNameAtmospheric -> "Atmospheric Adaptation"
    }

    // ── Reading the arguments back out ───────────────────────────────────────────────────────
    //
    // The casts are safe by construction rather than by check: `Message`'s constructor is internal,
    // so the only things that reach here are the arguments a `Strings` signature named.

    private fun List<Arg>.number(index: Int): Long = (this[index] as Arg.Number).value

    private fun List<Arg>.count(index: Int): Int = (this[index] as Arg.Count).value

    private fun List<Arg>.text(index: Int): String = resolve((this[index] as Arg.Text).value)

    // English has two forms and picks between them on one. A language with three or four writes its
    // own helper — which is the reason a count is its own `Arg` rather than a number that happens to
    // sit next to a noun.
    private fun Int.plural(one: String, many: String): String = if (this == 1) one else many

    // Two through **eight**, which is every group this game can produce: six facilities build in
    // parallel, one slot holds an applied project and a second holds a ladder beside it. Nine is
    // unreachable, so the `else` is a fall-through rather than a case.
    private fun Int.spelled(): String = when (this) {
        2 -> "Two"
        3 -> "Three"
        4 -> "Four"
        5 -> "Five"
        6 -> "Six"
        7 -> "Seven"
        else -> "Eight"
    }

    // "5 worlds, 1 worth taking" — the shared tail of both shortlist entries. **U+00A0 between each
    // count and its qualifier**, exactly as the Galaxy screen binds a value to its unit: if a longer
    // count pushes this to two lines it has to break at the comma, because "1 worth" stranded above
    // "taking" reads as a defect where a clause break reads as a wrap.
    private fun List<Arg>.shortlistCounts(): String {
        val worlds = count(0).let { "$it ${it.plural("world", "worlds")}" }
        val worth = count(1).let { if (it == 0) "none" else "$it" }
        return "$worlds, $worth${UNIT_GAP}worth${UNIT_GAP}taking"
    }

    // **An ordinary space, and it is meant to be U+00A0.** Three files said in as many words that
    // this character binds a value to its unit so a wrapping line never leaves "atm" alone on one —
    // and all three shipped a plain `' '`. Only the Galaxy screen's `NBSP` constant held the real
    // thing, and nothing referenced it.
    //
    // Reproduced rather than fixed, deliberately: #86 promised to move the words without changing
    // one of them, and a character swap here changes what four screens render and what their
    // baselines photograph. It is Davide's to call, and it is a two-character diff once he has.
    private const val UNIT_GAP: Char = ' '

    // "1st", "2nd", "3rd", "4th" — English's own irregularity, and the reason the caller passes the
    // number rather than a word it had to build itself.
    private fun Long.ordinal(): String = when (this % 10) {
        1L -> if (this % 100 == 11L) "${this}th" else "${this}st"
        2L -> if (this % 100 == 12L) "${this}th" else "${this}nd"
        3L -> if (this % 100 == 13L) "${this}th" else "${this}rd"
        else -> "${this}th"
    }

    // Every wall-clock entry is the same two padded fields with a different word in front of it.
    private fun List<Arg>.clock(): String = "${number(0).pad2()}:${number(1).pad2()}"

    // Rendering a fixed-point number is where a language shows most: the separator is English's
    // point, and the trimming is the caller's request carried out with it.
    private fun List<Arg>.decimal(index: Int): String {
        val arg = this[index] as Arg.Decimal
        val magnitude = if (arg.scaled < 0) -arg.scaled else arg.scaled
        val unit = POWERS_OF_TEN[arg.decimals]
        val sign = if (arg.scaled < 0) "−" else ""
        val rendered = "$sign${magnitude / unit}$DECIMAL_SEPARATOR" +
            (magnitude % unit).toString().padStart(arg.decimals, '0')
        return if (arg.trimTrailingZeros) rendered.trimEnd('0').trimEnd(DECIMAL_SEPARATOR) else rendered
    }

    // English groups by thousands with a comma; Italian will not.
    private fun String.grouped(): String {
        val negative = startsWith('-')
        val digits = if (negative) drop(1) else this
        val grouped = digits.reversed().chunked(GROUP_SIZE).joinToString(GROUPING_SEPARATOR).reversed()
        return if (negative) "-$grouped" else grouped
    }

    private fun Long.pad2(): String = toString().padStart(2, '0')

    private const val DECIMAL_SEPARATOR: Char = '.'
    private const val GROUPING_SEPARATOR: String = ","
    private const val GROUP_SIZE: Int = 3
    private val POWERS_OF_TEN: List<Long> = listOf(1, 10, 100, 1_000)
}
