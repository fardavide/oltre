package dev.fardavide.oltre.client.design.text

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.EpithetAdjective
import dev.fardavide.oltre.core.EpithetNoun
import dev.fardavide.oltre.core.HostilityAxis
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WorldEpithet

// **The factory, and the whole of the type safety.** `Message`'s constructor is `internal`, so this
// object is the only way to name a catalogue entry — which means the signature written here *is* the
// contract, and `Strings.hullsInFleet("two")` is a compile error rather than a review comment.
//
// Locale-free on purpose. A `presentation` module builds its text long before anything knows which
// language will draw it, and `GameNotifications` builds text that the OS will draw hours later with
// nobody composing at all. The words live in `Translations`; only their *identity and arguments*
// live here.
//
// Every entry is a function even when it takes nothing (`Strings.build()`, not `Strings.build`),
// Davide's call: one shape across every call site, and an entry that grows an argument later is not
// a change to two hundred of them.
object Strings {

    // ── How the game writes numbers, durations and lists ─────────────────────────────────────

    // "6 owned · 1 idle · 5 away" — a run of clauses in the app's own punctuation. The separator is
    // an entry rather than a literal so a language that lists differently changes it once.
    fun clauses(parts: List<TextRes>): TextRes =
        TextRes.Joined(parts, separator = message(StringId.ClauseSeparator))

    // "1,450". English groups by thousands with a comma; Italian uses a point, which is exactly why
    // this is a message and not a `toString()`.
    fun groupedNumber(value: Long): TextRes = message(StringId.GroupedNumber, Arg.Number(value))

    // "+5" / "−5", with a true minus sign rather than a hyphen — at this size in a mono face a
    // hyphen reads as a dash between two figures.
    fun signed(value: Int): TextRes = message(StringId.Signed, Arg.Number(value.toLong()))

    // A fixed-point number: `scaled` over ten to the `decimals`, so 262 at two decimals is 2.62.
    // `trimTrailingZeros` is the caller's call rather than the language's — the Research screen
    // spends it to fit a four-number band at 320dp and the Galaxy screen deliberately does not, so
    // it is a property of the reading, not of English.
    fun decimal(scaled: Long, decimals: Int, trimTrailingZeros: Boolean): TextRes =
        message(StringId.Decimal, Arg.Decimal(scaled, decimals, trimTrailingZeros))

    // "42m"
    fun durationMinutes(minutes: Long): TextRes =
        message(StringId.DurationMinutes, Arg.Number(minutes))

    // "1h 04m"
    fun durationHoursMinutes(hours: Long, minutes: Long): TextRes =
        message(StringId.DurationHoursMinutes, Arg.Number(hours), Arg.Number(minutes))

    // "186h"
    fun durationHours(hours: Long): TextRes = message(StringId.DurationHours, Arg.Number(hours))

    // "18d 13h"
    fun durationDaysHours(days: Long, hours: Long): TextRes =
        message(StringId.DurationDaysHours, Arg.Number(days), Arg.Number(hours))

    // "00:12:04" — always three fields, so a countdown never changes width as it runs down.
    fun countdown(hours: Long, minutes: Long, seconds: Long): TextRes =
        message(StringId.Countdown, Arg.Number(hours), Arg.Number(minutes), Arg.Number(seconds))

    // "→ affordable 19:51" — the one line a watched row adds, wherever it is watched from.
    fun watchedAt(hour: Int, minute: Int): TextRes =
        message(StringId.WatchedAt, Arg.Number(hour.toLong()), Arg.Number(minute.toLong()))

    // ── Wall-clock instants ──────────────────────────────────────────────────────────────────

    // "done 11:23" — a facility, a technology or a hull finishing.
    fun doneAt(hour: Int, minute: Int): TextRes = clock(StringId.DoneAt, hour, minute)

    // "home 14:05" — a run's return.
    fun homeAt(hour: Int, minute: Int): TextRes = clock(StringId.HomeAt, hour, minute)

    // "lands 14:05" — a probe still in flight.
    fun landsAt(hour: Int, minute: Int): TextRes = clock(StringId.LandsAt, hour, minute)

    // "landed 14:05" — a run that has already arrived.
    fun landedAt(hour: Int, minute: Int): TextRes = clock(StringId.LandedAt, hour, minute)

    // "Probe landed 14:05" — the survey footer, which names the craft because the card it sits on
    // is about the world rather than about the probe.
    fun probeLandedAt(hour: Int, minute: Int): TextRes = clock(StringId.ProbeLandedAt, hour, minute)

    private fun clock(id: StringId, hour: Int, minute: Int): TextRes =
        message(id, Arg.Number(hour.toLong()), Arg.Number(minute.toLong()))

    // ── The design system's own words ────────────────────────────────────────────────────────

    // "LV 4" — the badge beside a row's name, and beside the sheet's heading.
    fun levelBadge(level: Int): TextRes = message(StringId.LevelBadge, Arg.Number(level.toLong()))

    // ── Vocabulary every screen shares ───────────────────────────────────────────────────────

    // Sentences run together into a paragraph. The space between them is an entry for the reason
    // the middot is: it is punctuation, and punctuation is a language's.
    fun sentences(parts: List<TextRes>): TextRes =
        TextRes.Joined(parts, separator = message(StringId.SentenceSeparator))

    // "metal" — inside a sentence, which is where the game usually says it.
    fun resourceName(kind: ResourceKind): TextRes = message(
        when (kind) {
            ResourceKind.METAL -> StringId.ResourceNameMetal
            ResourceKind.CRYSTAL -> StringId.ResourceNameCrystal
            ResourceKind.DEUTERIUM -> StringId.ResourceNameDeuterium
        },
    )

    // "Metal" — as a heading, on the two gather cards and the resource rail. A separate entry
    // rather than an `uppercase()` or a `replaceFirstChar`, because case is not a transformation a
    // language shares: Turkish capitalises "i" as "İ", and German capitalises nouns mid-sentence.
    fun resourceTitle(kind: ResourceKind): TextRes = message(
        when (kind) {
            ResourceKind.METAL -> StringId.ResourceTitleMetal
            ResourceKind.CRYSTAL -> StringId.ResourceTitleCrystal
            ResourceKind.DEUTERIUM -> StringId.ResourceTitleDeuterium
        },
    )

    // A bare number, written as the language writes numbers but with no grouping and no unit. The
    // entry exists so that a figure the sheet picks out is still a piece of catalogued text rather
    // than a `toString()` that escaped.
    fun plainNumber(value: Int): TextRes = message(StringId.PlainNumber, Arg.Number(value.toLong()))

    // "55%"
    fun percent(value: Int): TextRes = message(StringId.Percent, Arg.Number(value.toLong()))

    // "120/h" — a rate, and the slash is punctuation a language owns.
    fun perHour(amount: TextRes): TextRes = message(StringId.PerHour, Arg.Text(amount))

    // "+120/h"
    fun plusPerHour(amount: TextRes): TextRes = message(StringId.PlusPerHour, Arg.Text(amount))

    // "+1,200"
    fun plusAmount(amount: TextRes): TextRes = message(StringId.PlusAmount, Arg.Text(amount))

    // "[3:185:4]" — a world's address. It was a `private fun` in two presentation modules that rule
    // 5 stops seeing each other, kept in step by the frames rather than by the compiler; one entry
    // is what makes the Galaxy row and the sheet it raises unable to disagree.
    fun coordinate(galaxy: Int, system: Int, slot: Int): TextRes = message(
        StringId.CoordinateLabel,
        Arg.Number(galaxy.toLong()),
        Arg.Number(system.toLong()),
        Arg.Number(slot.toLong()),
    )

    // "1,240 metal" — a quantity and what it is made of.
    fun amountOfResource(amount: TextRes, kind: ResourceKind): TextRes =
        message(StringId.AmountOfResource, Arg.Text(amount), Arg.Text(resourceName(kind)))

    // "metal 1.24" — the other way round, and a separate entry rather than the same one reversed:
    // this is a *reading* of a world, where the noun labels the figure, and the one above is a
    // *quantity*, where the noun is its unit. A language may well order those two differently.
    fun resourceReading(kind: ResourceKind, value: TextRes): TextRes =
        message(StringId.ResourceReading, Arg.Text(resourceName(kind)), Arg.Text(value))

    // ── Shipyard ─────────────────────────────────────────────────────────────────────────────

    fun shipyardHeading(): TextRes = message(StringId.ShipyardHeading)

    fun shipyardNotYetBuiltHeading(): TextRes = message(StringId.ShipyardNotYetBuiltHeading)

    // The one sentence on this screen arguing against the purchase it is offering.
    fun shipyardNote(): TextRes = message(StringId.ShipyardNote)

    // "6 hulls" beside the section rule, "1 hull" at the start.
    fun hullsInFleet(count: Int): TextRes = message(StringId.HullsInFleet, Arg.Count(count))

    // The four clauses of the pool line. Four entries rather than one with a label argument,
    // because a language may not put the number where English does in all four.
    fun shipsOwned(count: Int): TextRes = message(StringId.ShipsOwned, Arg.Count(count))

    fun shipsIdle(count: Int): TextRes = message(StringId.ShipsIdle, Arg.Count(count))

    fun shipsAway(count: Int): TextRes = message(StringId.ShipsAway, Arg.Count(count))

    fun shipsBuilding(count: Int): TextRes = message(StringId.ShipsBuilding, Arg.Count(count))

    fun shipsQueued(count: Int): TextRes = message(StringId.ShipsQueued, Arg.Count(count))

    fun build(): TextRes = message(StringId.Build)

    // "in 1h 06m" — the ghost's contract everywhere in the app: a player who wants the thing they
    // cannot afford yet is told **when**, not told no.
    fun availableIn(wait: TextRes): TextRes = message(StringId.AvailableIn, Arg.Text(wait))

    // "—", for a binding resource with no production at all: "in 2,000,000h" is a worse lie than
    // nothing.
    fun availableNever(): TextRes = message(StringId.AvailableNever)

    // "needs a scout" — the probe footer, when the hull rather than the metal is what is short.
    fun probeNeedsScout(): TextRes = message(StringId.ProbeNeedsScout)

    // PLACEHOLDER copy, like every string the app says: content is Davide's.
    fun scoutName(): TextRes = message(StringId.ScoutName)

    fun scoutPurpose(): TextRes = message(StringId.ScoutPurpose)

    fun skiffName(): TextRes = message(StringId.SkiffName)

    fun skiffPurpose(): TextRes = message(StringId.SkiffPurpose)

    fun haulerName(): TextRes = message(StringId.HaulerName)

    fun haulerPurpose(): TextRes = message(StringId.HaulerPurpose)

    // ── The dispatch sheet ───────────────────────────────────────────────────────────────────

    // "no hazards" / "one hazard" / "two hazards" — the world's own half of the danger, in words
    // carrying their own arithmetic, so a row and a sheet can agree without either quoting a total.
    fun hazards(count: Int): TextRes = message(StringId.Hazards, Arg.Count(count))

    // "one hazard, 195 units out"
    fun hazardsAtDistance(hazards: TextRes, distance: TextRes): TextRes =
        message(StringId.HazardsAtDistance, Arg.Text(hazards), Arg.Text(distance))

    // The head of a world nobody has looked at: the system is charted, so the player knows a world
    // is there — and the world is not, so nothing about it can be priced.
    fun chartedUnsurveyed(): TextRes = message(StringId.ChartedUnsurveyed)

    fun dispatchUnsurveyedTitle(): TextRes = message(StringId.DispatchUnsurveyedTitle)

    fun dispatchUnsurveyedNote(slots: Int, worlds: Int): TextRes =
        message(StringId.DispatchUnsurveyedNote, Arg.Number(slots.toLong()), Arg.Count(worlds))

    // "240 metal · 40m." — the probe the refusal hands back, priced by the caller's own footer.
    fun dispatchProbeOffer(cost: TextRes, flight: TextRes): TextRes =
        message(StringId.DispatchProbeOffer, Arg.Text(cost), Arg.Text(flight))

    fun dispatchEverySkiffAwayTitle(): TextRes = message(StringId.DispatchEverySkiffAwayTitle)

    fun dispatchNoGatheringHullTitle(): TextRes = message(StringId.DispatchNoGatheringHullTitle)

    fun dispatchNoGatheringHullNote(): TextRes = message(StringId.DispatchNoGatheringHullNote)

    fun dispatchOutOfReachTitle(): TextRes = message(StringId.DispatchOutOfReachTitle)

    fun dispatchOutOfReachNote(): TextRes = message(StringId.DispatchOutOfReachNote)

    // "3 runs are out. [3:185:4] is inbound with 1,240 metal."
    fun dispatchAwayNote(runs: Int, target: TextRes, cargo: TextRes, kind: ResourceKind): TextRes =
        message(
            StringId.DispatchAwayNote,
            Arg.Count(runs),
            Arg.Text(target),
            Arg.Text(cargo),
            Arg.Text(resourceName(kind)),
        )

    fun dispatchAwayMore(count: Int): TextRes = message(StringId.DispatchAwayMore, Arg.Count(count))

    fun dispatchAwayTail(): TextRes = message(StringId.DispatchAwayTail)


    fun depositFull(): TextRes = message(StringId.DepositFull)

    fun depositEmpty(): TextRes = message(StringId.DepositEmpty)

    // "deposit 620/1,798"
    fun depositStock(remaining: TextRes, cap: TextRes): TextRes =
        message(StringId.DepositStock, Arg.Text(remaining), Arg.Text(cap))

    // "richness 1.24"
    fun richness(value: TextRes): TextRes = message(StringId.Richness, Arg.Text(value))

    fun skiffCount(count: Int): TextRes = message(StringId.SkiffCount, Arg.Count(count))

    // "6 berths" — the stepper's value once a second hull type is idle, because a berth is a
    // distinction only two hulls create. With skiffs alone it counts skiffs, as it always did.
    fun berthCount(count: Int): TextRes = message(StringId.BerthCount, Arg.Count(count))

    // "1 hauler · 2 skiffs idle" — the pool beside SEND, which names hulls where the stepper names
    // hold, because those are the two different things a player has to hold in mind at once.
    fun poolIdle(manifest: TextRes): TextRes = message(StringId.PoolIdle, Arg.Text(manifest))

    fun manifestPair(first: TextRes, second: TextRes): TextRes =
        message(StringId.ManifestPair, Arg.Text(first), Arg.Text(second))

    fun outAndBack(trip: TextRes): TextRes = message(StringId.OutAndBack, Arg.Text(trip))

    fun rungRequiresSkiffs(): TextRes = message(StringId.RungRequiresSkiffs)

    fun ladderRungMoved(rung: TextRes): TextRes = message(StringId.LadderRungMoved, Arg.Text(rung))

    fun ladderShortestFit(rung: TextRes): TextRes = message(StringId.LadderShortestFit, Arg.Text(rung))

    // The one slot below the cells, in its three forms: what the other cell would lift, what it
    // would cost in rungs, and — winning over both — the clamp.
    // **The amount and the kind stay apart**, the way `waitingAsk` and `dispatchAwayNote` already
    // keep them. `amountOfResource` glues them with a bare space because it is a *chip*, and this
    // file says so at `AmountOfResource` — but these two are sentences, and Italian puts a `di`
    // between a figure and a noun inside one. Handed the pre-composed chip they read "sollevano solo
    // 449 metallo", which is the grammar of a label pasted into prose.
    fun cellCounterfactual(lift: TextRes, kind: ResourceKind, rung: TextRes): TextRes =
        message(StringId.CellCounterfactual, Arg.Text(lift), Arg.Text(resourceName(kind)), Arg.Text(rung))

    fun cellRungConsequence(lift: TextRes, kind: ResourceKind, rung: TextRes): TextRes =
        message(StringId.CellRungConsequence, Arg.Text(lift), Arg.Text(resourceName(kind)), Arg.Text(rung))

    fun cellClamped(idle: TextRes): TextRes = message(StringId.CellClamped, Arg.Text(idle))

    fun cellClampedOne(): TextRes = message(StringId.CellClampedOne)

    // "of 4 idle" — the pool the stepper is clamped to.
    fun ofIdle(count: Int): TextRes = message(StringId.OfIdle, Arg.Count(count))

    // "18h 20m out and back. No shorter window leaves 20 minutes on the surface."
    fun ladderNote(roundTrip: TextRes, minimumStationMinutes: Long): TextRes =
        message(StringId.LadderNote, Arg.Text(roundTrip), Arg.Number(minimumStationMinutes))

    // "The 12h window brings the same."
    fun rungNote(window: TextRes): TextRes = message(StringId.RungNote, Arg.Text(window))

    // "3 skiffs empty it." / "1 skiff empties it."
    fun clampSubject(count: Int): TextRes = message(StringId.ClampSubject, Arg.Count(count))

    // "The 4th brings nothing." — the ordinal is English's arithmetic, not the caller's.
    fun clampRestOrdinal(hulls: Int): TextRes = message(StringId.ClampRestOrdinal, Arg.Number(hulls.toLong()))

    // "The other 2 bring nothing."
    fun clampRestOthers(count: Int): TextRes = message(StringId.ClampRestOthers, Arg.Count(count))

    fun theWholeDeposit(): TextRes = message(StringId.TheWholeDeposit)

    // "870 left in the ground" — the slot beside the figure, which the per-ship reading used to
    // hold. What a run *leaves*, so the two forms are one sentence: this much left, or all of it.
    // The count rides along unread by English and agreed with by Italian — the same shape
    // `CellClampedOne` needed one defect earlier, on the other new entry of this release. A vein
    // regenerates continuously, so a remainder of exactly one is ordinary arithmetic rather than an
    // edge, and "1 rimasti nel terreno" is what it read.
    fun veinLeft(amount: TextRes, left: Long): TextRes =
        message(StringId.VeinLeft, Arg.Text(amount), Arg.Count(left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))

    // "449 each"
    fun eachShip(amount: TextRes): TextRes = message(StringId.EachShip, Arg.Text(amount))

    fun legOut(duration: TextRes): TextRes = message(StringId.LegOut, Arg.Text(duration))

    fun legOnStation(duration: TextRes): TextRes = message(StringId.LegOnStation, Arg.Text(duration))

    // The compact form, which drops a word rather than ellipsising the number.
    fun legStation(duration: TextRes): TextRes = message(StringId.LegStation, Arg.Text(duration))

    fun legWorking(duration: TextRes): TextRes = message(StringId.LegWorking, Arg.Text(duration))

    fun legHome(duration: TextRes): TextRes = message(StringId.LegHome, Arg.Text(duration))

    fun dangerLevel(danger: Int): TextRes = message(StringId.DangerLevel, Arg.Number(danger.toLong()))

    fun dangerNothingAdded(): TextRes = message(StringId.DangerNothingAdded)

    // "+70% of the hold" — danger *pays*, and the sign is carried by the word because a bare
    // percentage next to "danger" is read as a cost by anyone who has played anything.
    fun dangerBonus(percent: Int): TextRes = message(StringId.DangerBonus, Arg.Number(percent.toLong()))

    fun yourOwnSystem(): TextRes = message(StringId.YourOwnSystem)

    // The same three words, capitalised, because on the astronomy line they open the sentence and on
    // the dispatch sheet's danger line they sit inside one. Two entries rather than a `capitalize()`
    // for `resourceTitle`'s reason: case is not a transformation a language shares.
    fun yourOwnSystemCapitalised(): TextRes = message(StringId.YourOwnSystemCapitalised)

    fun anotherGalaxy(): TextRes = message(StringId.AnotherGalaxy)

    // "195 units out". Takes the *written* number rather than the figure, because the two callers
    // write it differently and both are right: the dispatch sheet quotes a distance inside a
    // sentence and the astronomy line quotes one that can run to four digits and is grouped.
    fun unitsOut(units: TextRes): TextRes = message(StringId.UnitsOut, Arg.Text(units))

    fun bothDepositsEmpty(): TextRes = message(StringId.BothDepositsEmpty)

    fun thisDepositEmpty(): TextRes = message(StringId.ThisDepositEmpty)

    // "3 skiffs at 12h would lift 1,240 metal."
    fun waitingAsk(ships: TextRes, window: TextRes, lift: TextRes, kind: ResourceKind): TextRes =
        message(
            StringId.WaitingAsk,
            Arg.Text(ships),
            Arg.Text(window),
            Arg.Text(lift),
            Arg.Text(resourceName(kind)),
        )

    fun waitingHoldsAgain(wait: TextRes): TextRes = message(StringId.WaitingHoldsAgain, Arg.Text(wait))

    fun waitingNeverHolds(): TextRes = message(StringId.WaitingNeverHolds)

    fun waitingRemedy(): TextRes = message(StringId.WaitingRemedy)

    // The three control headings, already in the case they are drawn in — see `resourceTitle`.
    fun controlBringBack(): TextRes = message(StringId.ControlBringBack)

    fun controlSend(): TextRes = message(StringId.ControlSend)

    fun controlHomeIn(): TextRes = message(StringId.ControlHomeIn)

    fun dispatchVerb(): TextRes = message(StringId.DispatchVerb)

    // ── What the game's things are called ────────────────────────────────────────────────────

    fun buildingName(building: BuildingType): TextRes = message(
        when (building) {
            BuildingType.METAL_MINE -> StringId.BuildingNameMetalMine
            BuildingType.CRYSTAL_MINE -> StringId.BuildingNameCrystalMine
            BuildingType.DEUTERIUM_SYNTHESIZER -> StringId.BuildingNameDeuteriumSynthesizer
            BuildingType.SOLAR_PLANT -> StringId.BuildingNameSolarPlant
            BuildingType.ROBOTICS_FACTORY -> StringId.BuildingNameRoboticsFactory
            BuildingType.NANITE_FACTORY -> StringId.BuildingNameNaniteFactory
        },
    )

    // The name a Slide Over pane has room for. Its own entry per facility rather than a fallback to
    // the full name, because *which* names a language can shorten is that language's to know.
    fun buildingCompactName(building: BuildingType): TextRes = message(
        when (building) {
            BuildingType.METAL_MINE -> StringId.BuildingCompactNameMetalMine
            BuildingType.CRYSTAL_MINE -> StringId.BuildingCompactNameCrystalMine
            BuildingType.DEUTERIUM_SYNTHESIZER -> StringId.BuildingCompactNameDeuteriumSynthesizer
            BuildingType.SOLAR_PLANT -> StringId.BuildingCompactNameSolarPlant
            BuildingType.ROBOTICS_FACTORY -> StringId.BuildingCompactNameRoboticsFactory
            BuildingType.NANITE_FACTORY -> StringId.BuildingCompactNameNaniteFactory
        },
    )

    // "skiff" — lower case, because it is a word inside a manifest rather than a heading.
    fun shipName(ship: ShipType): TextRes = message(
        when (ship) {
            ShipType.SCOUT -> StringId.ShipNameScout
            ShipType.SKIFF -> StringId.ShipNameSkiff
            ShipType.HAULER -> StringId.ShipNameHauler
            ShipType.ESCORT -> StringId.ShipNameEscort
            ShipType.SETTLER -> StringId.ShipNameSettler
        },
    )

    // "3 skiff" — a manifest clause, and deliberately not pluralised: it is a tally of a type in a
    // run of them, which is how the fleet strip has always written it.
    fun shipsOfType(count: Int, ship: ShipType): TextRes =
        message(StringId.ShipsOfType, Arg.Count(count), Arg.Text(shipName(ship)))

    // ── The Colony tab ───────────────────────────────────────────────────────────────────────

    fun colonyFacilitiesHeading(): TextRes = message(StringId.ColonyFacilitiesHeading)

    fun powerHeading(): TextRes = message(StringId.PowerHeading)

    fun energyEveryMineStopped(): TextRes = message(StringId.EnergyEveryMineStopped)

    fun energyEveryMineAt(percent: Int): TextRes =
        message(StringId.EnergyEveryMineAt, Arg.Number(percent.toLong()))

    fun energyBreakEven(): TextRes = message(StringId.EnergyBreakEven)

    fun energyRoomForMineLevels(levels: Long): TextRes =
        message(StringId.EnergyRoomForMineLevels, Arg.Count(levels.toInt()))

    fun energyProduced(amount: TextRes): TextRes = message(StringId.EnergyProduced, Arg.Text(amount))

    fun energyDrawn(amount: TextRes): TextRes = message(StringId.EnergyDrawn, Arg.Text(amount))

    fun energyShort(amount: TextRes): TextRes = message(StringId.EnergyShort, Arg.Text(amount))

    fun energySpare(amount: TextRes): TextRes = message(StringId.EnergySpare, Arg.Text(amount))

    fun onStationAt(target: TextRes): TextRes = message(StringId.OnStationAt, Arg.Text(target))

    fun fleetReturning(): TextRes = message(StringId.FleetReturning)

    fun fromTarget(target: TextRes): TextRes = message(StringId.FromTarget, Arg.Text(target))

    fun moreAway(count: Int): TextRes = message(StringId.MoreAway, Arg.Count(count))

    fun powerSupply(amount: TextRes): TextRes = message(StringId.PowerSupply, Arg.Text(amount))

    fun powerDraw(amount: TextRes): TextRes = message(StringId.PowerDraw, Arg.Text(amount))

    // "→ LV 5 covers all 1,200 drawn"
    fun solarFix(level: Int, drawn: TextRes): TextRes =
        message(StringId.SolarFix, Arg.Number(level.toLong()), Arg.Text(drawn))

    // "+1,200/h metal"
    fun outputGain(perHour: TextRes, kind: ResourceKind): TextRes =
        message(StringId.OutputGain, Arg.Text(perHour), Arg.Text(resourceName(kind)))

    // "back in 1h 42m"
    fun backIn(payback: TextRes): TextRes = message(StringId.BackIn, Arg.Text(payback))

    fun suppliesMore(amount: TextRes): TextRes = message(StringId.SuppliesMore, Arg.Text(amount))

    fun drawAlreadyCovered(): TextRes = message(StringId.DrawAlreadyCovered)

    fun throttlesEveryMine(): TextRes = message(StringId.ThrottlesEveryMine)

    fun solarPlantCovers(level: Int): TextRes =
        message(StringId.SolarPlantCovers, Arg.Number(level.toLong()))

    // "−12m per build"
    fun savedPerBuild(saved: TextRes): TextRes = message(StringId.SavedPerBuild, Arg.Text(saved))

    // "LV 2 → adaptation"
    fun gateClause(level: Int, opens: TextRes): TextRes =
        message(StringId.GateClause, Arg.Number(level.toLong()), Arg.Text(opens))

    fun gateSummaryNanite(): TextRes = message(StringId.GateSummaryNanite)

    fun gateSummaryAdaptationShort(): TextRes = message(StringId.GateSummaryAdaptationShort)

    fun gateSummaryAdaptationLong(): TextRes = message(StringId.GateSummaryAdaptationLong)

    fun gateSummaryResearchShort(): TextRes = message(StringId.GateSummaryResearchShort)

    fun gateSummaryResearchLong(): TextRes = message(StringId.GateSummaryResearchLong)

    // "Robotics Factory · 2,000 metal"
    fun gateFacilityLong(name: TextRes, metal: TextRes): TextRes =
        message(StringId.GateFacilityLong, Arg.Text(name), Arg.Text(metal))

    // "applied research · you have this"
    fun ladderStepHeld(opens: TextRes): TextRes = message(StringId.LadderStepHeld, Arg.Text(opens))

    // "A 186h build takes 42h at LV 10"
    fun naniteReliefLong(unaided: TextRes, helped: TextRes, level: Int): TextRes =
        message(StringId.NaniteReliefLong, Arg.Text(unaided), Arg.Text(helped), Arg.Number(level.toLong()))

    fun naniteReliefShort(unaided: TextRes, helped: TextRes, level: Int): TextRes =
        message(StringId.NaniteReliefShort, Arg.Text(unaided), Arg.Text(helped), Arg.Number(level.toLong()))

    fun requiresRobotics(level: Int): TextRes = message(StringId.RequiresRobotics, Arg.Number(level.toLong()))

    // "→ LV 13" — the head of the accent line a running row carries.
    fun becomesLevel(level: Int): TextRes = message(StringId.BecomesLevel, Arg.Number(level.toLong()))

    fun upgradeVerb(): TextRes = message(StringId.UpgradeVerb)

    // "LV 4 → 5 · 2h 30m"
    fun pointerLevelStep(from: Int, to: Int, wait: TextRes): TextRes = message(
        StringId.PointerLevelStep,
        Arg.Number(from.toLong()),
        Arg.Number(to.toLong()),
        Arg.Text(wait),
    )

    // "LV 5 · back in 1h 42m"
    fun pointerBestBuy(level: Int, payback: TextRes): TextRes =
        message(StringId.PointerBestBuy, Arg.Number(level.toLong()), Arg.Text(payback))

    // ── The Colony sheet's prose ─────────────────────────────────────────────────────────────
    //
    // Fragments, and `StringId` says at length why. Named for the line they belong to rather than
    // for the words in them, so a translator reading the table can at least see which sentence a
    // fragment is part of.

    fun sheetMineMakes(): TextRes = message(StringId.SheetMineMakes)

    fun sheetMineAtLevel(kind: ResourceKind, level: Int): TextRes =
        message(StringId.SheetMineAtLevel, Arg.Text(resourceName(kind)), Arg.Number(level.toLong()))

    fun sheetFullStop(): TextRes = message(StringId.SheetFullStop)

    fun sheetPlantsSupply(): TextRes = message(StringId.SheetPlantsSupply)

    fun sheetColonyDraws(): TextRes = message(StringId.SheetColonyDraws)

    fun sheetSoEveryMineAt(): TextRes = message(StringId.SheetSoEveryMineAt)

    fun sheetThisLevelLifts(): TextRes = message(StringId.SheetThisLevelLifts)

    fun sheetRatherThanEnergy(kind: ResourceKind): TextRes =
        message(StringId.SheetRatherThanEnergy, Arg.Text(resourceName(kind)))

    fun sheetPaybackPrefix(): TextRes = message(StringId.SheetPaybackPrefix)

    fun sheetSupplyNotLimiting(): TextRes = message(StringId.SheetSupplyNotLimiting)

    fun sheetChangesNoRate(): TextRes = message(StringId.SheetChangesNoRate)

    fun sheetPaysNextMineLevel(): TextRes = message(StringId.SheetPaysNextMineLevel)

    fun sheetPaysWhenDrawPasses(): TextRes = message(StringId.SheetPaysWhenDrawPasses)

    fun sheetMoreMineLevelAway(): TextRes = message(StringId.SheetMoreMineLevelAway)

    fun sheetMoreMineLevelsAway(): TextRes = message(StringId.SheetMoreMineLevelsAway)

    // "one", spelled — the crossing line writes the smallest number as a word, because "1 more mine
    // level away" reads as a measurement and this is an estimate.
    fun sheetOneSpelled(): TextRes = message(StringId.SheetOneSpelled)

    fun sheetCannotPowerLevel(): TextRes = message(StringId.SheetCannotPowerLevel)

    fun sheetPlantCarriesPrefix(): TextRes = message(StringId.SheetPlantCarriesPrefix)

    fun sheetPlantCarriesSuffix(): TextRes = message(StringId.SheetPlantCarriesSuffix)

    fun sheetShortensDeepBuild(): TextRes = message(StringId.SheetShortensDeepBuild)

    fun sheetShortensEveryBuild(): TextRes = message(StringId.SheetShortensEveryBuild)

    fun sheetNextBuildTakes(on: TextRes): TextRes = message(StringId.SheetNextBuildTakes, Arg.Text(on))

    fun sheetAtBuildingLevelTakes(building: TextRes, level: Int): TextRes =
        message(StringId.SheetAtBuildingLevelTakes, Arg.Text(building), Arg.Number(level.toLong()))

    fun sheetNaniteMineTakes(level: Int): TextRes =
        message(StringId.SheetNaniteMineTakes, Arg.Number(level.toLong()))

    fun sheetNaniteUnaidedAt(naniteLevels: Int): TextRes =
        message(StringId.SheetNaniteUnaidedAt, Arg.Number(naniteLevels.toLong()))

    fun sheetRoboticsIsAt(): TextRes = message(StringId.SheetRoboticsIsAt)

    // ". Two levels to go, and the first Nanite level costs "
    fun sheetLevelsToGo(levels: Int): TextRes = message(StringId.SheetLevelsToGo, Arg.Count(levels))

    fun sheetMetalSuffix(): TextRes = message(StringId.SheetMetalSuffix)

    // ── The Research tab ─────────────────────────────────────────────────────────────────────

    fun technologyName(technology: Technology): TextRes = message(
        when (technology) {
            Technology.PHOTOVOLTAICS -> StringId.TechnologyNamePhotovoltaics
            Technology.EXTRACTION -> StringId.TechnologyNameExtraction
            Technology.ENRICHMENT -> StringId.TechnologyNameEnrichment
            Technology.PROSPECTING -> StringId.TechnologyNameProspecting
            Technology.PROPULSION -> StringId.TechnologyNamePropulsion
        },
    )

    fun adaptationName(technology: AdaptationTechnology): TextRes = message(
        when (technology) {
            AdaptationTechnology.THERMAL -> StringId.AdaptationNameThermal
            AdaptationTechnology.GRAVITIC -> StringId.AdaptationNameGravitic
            AdaptationTechnology.ATMOSPHERIC -> StringId.AdaptationNameAtmospheric
        },
    )

    // What a technology's percentage is a percentage *of* — "metal · crystal output".
    fun technologySubject(technology: Technology): TextRes = message(
        when (technology) {
            Technology.PHOTOVOLTAICS -> StringId.TechnologySubjectPhotovoltaics
            Technology.EXTRACTION -> StringId.TechnologySubjectExtraction
            Technology.ENRICHMENT -> StringId.TechnologySubjectEnrichment
            Technology.PROSPECTING -> StringId.TechnologySubjectProspecting
            Technology.PROPULSION -> StringId.TechnologySubjectPropulsion
        },
    )

    // "°C", "g", "atm" — a unit is a word in some languages and a symbol in others, so it is an
    // entry rather than a constant.
    fun adaptationUnit(technology: AdaptationTechnology): TextRes = message(
        when (technology) {
            AdaptationTechnology.THERMAL -> StringId.AdaptationUnitThermal
            AdaptationTechnology.GRAVITIC -> StringId.AdaptationUnitGravitic
            AdaptationTechnology.ATMOSPHERIC -> StringId.AdaptationUnitAtmospheric
        },
    )

    // The requirement line's short forms — "Requires Robotics 1" — read at a glance, where the full
    // facility name buys nothing. A third naming of the same six things, and deliberately: what a
    // row calls a facility, what a Slide Over pane calls it and what a requirement calls it are
    // three decisions that happen to coincide five times out of six in English.
    fun buildingShortName(building: BuildingType): TextRes = message(
        when (building) {
            BuildingType.METAL_MINE -> StringId.BuildingShortNameMetalMine
            BuildingType.CRYSTAL_MINE -> StringId.BuildingShortNameCrystalMine
            BuildingType.DEUTERIUM_SYNTHESIZER -> StringId.BuildingShortNameDeuteriumSynthesizer
            BuildingType.SOLAR_PLANT -> StringId.BuildingShortNameSolarPlant
            BuildingType.ROBOTICS_FACTORY -> StringId.BuildingShortNameRoboticsFactory
            BuildingType.NANITE_FACTORY -> StringId.BuildingShortNameNaniteFactory
        },
    )

    fun researchHeading(): TextRes = message(StringId.ResearchHeading)

    fun adaptationHeading(): TextRes = message(StringId.AdaptationHeading)

    fun ruleOneProjectAtATime(): TextRes = message(StringId.RuleOneProjectAtATime)

    fun ruleOneLadderAtATime(): TextRes = message(StringId.RuleOneLadderAtATime)

    // What 320dp shortens both rules to.
    fun ruleOneAtATime(): TextRes = message(StringId.RuleOneAtATime)

    fun researchVerb(): TextRes = message(StringId.ResearchVerb)

    fun verdictNothingThrottled(): TextRes = message(StringId.VerdictNothingThrottled)

    fun verdictNothingThrottledCompact(): TextRes = message(StringId.VerdictNothingThrottledCompact)

    fun verdictNothingSurplus(): TextRes = message(StringId.VerdictNothingSurplus)

    fun verdictNothingSurplusCompact(): TextRes = message(StringId.VerdictNothingSurplusCompact)

    // PLACEHOLDER copy — the one verdict on the Research screen whose noun Claude Design left to
    // Davide. The figure is exact and the shape matches its neighbours.
    fun haulGain(amount: TextRes): TextRes = message(StringId.HaulGain, Arg.Text(amount))

    fun haulGainCompact(amount: TextRes): TextRes = message(StringId.HaulGainCompact, Arg.Text(amount))

    // "the next galaxy in 9h 20m, from 18h 20m" — the drive's row. The *new* figure leads and the
    // old one trails, unlike the two above which state a delta: a delta of hours off a flight is a
    // number with nothing to be a fraction of, where the pair says the whole thing at a glance.
    fun reachGain(to: TextRes, from: TextRes): TextRes =
        message(StringId.ReachGain, Arg.Text(to), Arg.Text(from))

    fun reachGainCompact(to: TextRes): TextRes = message(StringId.ReachGainCompact, Arg.Text(to))

    // "+14%" — always signed positive, unlike `signed`: an effect is a gain or it is not printed.
    fun plusPercent(value: Int): TextRes = message(StringId.PlusPercent, Arg.Number(value.toLong()))

    // "−30 … +45" — "…" is the one new glyph in the product and it appears in this line alone: an
    // en dash is unreadable against a leading minus, and the word "to" puts English inside a line
    // of numbers.
    fun toleranceBand(min: TextRes, max: TextRes): TextRes =
        message(StringId.ToleranceBand, Arg.Text(min), Arg.Text(max))

    // "Requires Robotics 1"
    fun requires(subject: TextRes): TextRes = message(StringId.Requires, Arg.Text(subject))

    // "Robotics 1" — a thing and the level of it wanted.
    fun namedLevel(name: TextRes, level: Int): TextRes =
        message(StringId.NamedLevel, Arg.Text(name), Arg.Number(level.toLong()))

    // The adaptation shortlist, whole rather than in fragments: the non-breaking spaces that bind
    // each count to its qualifier are English's typography, and a language that wraps differently
    // needs to be able to place them differently.
    fun shortlistNothingVerb(): TextRes = message(StringId.ShortlistNothingVerb)

    fun shortlistNothing(): TextRes = message(StringId.ShortlistNothing)

    fun shortlistVerb(unlocks: Int, worthTaking: Int): TextRes =
        message(StringId.ShortlistVerb, Arg.Count(unlocks), Arg.Count(worthTaking))

    fun shortlist(unlocks: Int, worthTaking: Int): TextRes =
        message(StringId.Shortlist, Arg.Count(unlocks), Arg.Count(worthTaking))

    // ── The Research sheet's prose ───────────────────────────────────────────────────────────

    fun sheetSubjectPrefix(subject: TextRes): TextRes =
        message(StringId.SheetSubjectPrefix, Arg.Text(subject))

    fun sheetArrow(): TextRes = message(StringId.SheetArrow)

    fun sheetAtLevelOne(): TextRes = message(StringId.SheetAtLevelOne)

    fun sheetToleranceSubject(unit: TextRes): TextRes =
        message(StringId.SheetToleranceSubject, Arg.Text(unit))

    fun sheetReachesNothing(): TextRes = message(StringId.SheetReachesNothing)

    fun sheetReachesPrefix(): TextRes = message(StringId.SheetReachesPrefix)

    fun sheetReachesMiddle(): TextRes = message(StringId.SheetReachesMiddle)

    fun sheetReachesSuffix(): TextRes = message(StringId.SheetReachesSuffix)

    fun sheetAndWouldMake(kind: ResourceKind): TextRes =
        message(StringId.SheetAndWouldMake, Arg.Text(resourceName(kind)))

    fun sheetEachHullLifts(): TextRes = message(StringId.SheetEachHullLifts)

    fun sheetAnHourOnStation(): TextRes = message(StringId.SheetAnHourOnStation)

    fun sheetPaysOnNextRun(): TextRes = message(StringId.SheetPaysOnNextRun)

    // The drive's three sheet clauses. A round trip rather than a one-way flight, because a run is
    // what the player commits to and the window is what it eats.
    fun sheetTheNextGalaxyIs(): TextRes = message(StringId.SheetTheNextGalaxyIs)

    fun sheetAwayAndWouldBe(): TextRes = message(StringId.SheetAwayAndWouldBe)

    fun sheetPaysTheFurtherYouAim(): TextRes = message(StringId.SheetPaysTheFurtherYouAim)

    fun sheetColonyDrawsAnd(): TextRes = message(StringId.SheetColonyDrawsAnd)

    fun sheetAtThatRatio(name: TextRes): TextRes = message(StringId.SheetAtThatRatio, Arg.Text(name))

    fun sheetRoundsAway(): TextRes = message(StringId.SheetRoundsAway)

    fun sheetPaysWhenPlantsCarry(): TextRes = message(StringId.SheetPaysWhenPlantsCarry)

    fun sheetMultipliesSupply(name: TextRes): TextRes =
        message(StringId.SheetMultipliesSupply, Arg.Text(name))

    fun sheetOutputDoesNotMove(): TextRes = message(StringId.SheetOutputDoesNotMove)

    fun sheetRequiresPrefix(): TextRes = message(StringId.SheetRequiresPrefix)

    // ── The Fleets tab ───────────────────────────────────────────────────────────────────────

    // "3 skiffs" / "1 skiff" — pluralised, unlike the Colony strip's tally: a run card names its
    // manifest in prose where the strip lists types. One entry per hull rather than a shared one
    // with the noun as an argument, because a plural is a property of the noun.
    fun ships(count: Int, ship: ShipType): TextRes = message(
        when (ship) {
            ShipType.SCOUT -> StringId.ShipsScout
            ShipType.SKIFF -> StringId.ShipsSkiff
            ShipType.HAULER -> StringId.ShipsHauler
            ShipType.ESCORT -> StringId.ShipsEscort
            ShipType.SETTLER -> StringId.ShipsSettler
        },
        Arg.Count(count),
    )

    fun fleetsHeading(): TextRes = message(StringId.FleetsHeading)

    // "5 of 6 away"
    fun fleetsAwayOf(away: Int, owned: Int): TextRes =
        message(StringId.FleetsAwayOf, Arg.Number(away.toLong()), Arg.Number(owned.toLong()))

    fun fleetsNothingOut(): TextRes = message(StringId.FleetsNothingOut)

    fun workedHeading(): TextRes = message(StringId.WorkedHeading)

    fun workedNewestFirst(): TextRes = message(StringId.WorkedNewestFirst)

    // "11 runs"
    fun runCount(count: Int): TextRes = message(StringId.RunCount, Arg.Count(count))

    // "3 earlier runs · 402 metal · no target recorded"
    fun unrecordedRuns(count: Int, total: TextRes, kind: ResourceKind): TextRes = message(
        StringId.UnrecordedRuns,
        Arg.Count(count),
        Arg.Text(total),
        Arg.Text(resourceName(kind)),
    )

    // The deposit reading on a worked row, which is the same idiom as the dispatch chip with the
    // word "deposit" taken off — the row's own column already says which vein it is.
    fun depositFullWord(): TextRes = message(StringId.DepositFullWord)

    fun depositEmptyWord(): TextRes = message(StringId.DepositEmptyWord)

    // "[3:185:4] · 2 runs"
    fun worldRowPrefix(address: TextRes, runs: TextRes): TextRes =
        message(StringId.WorldRowPrefix, Arg.Text(address), Arg.Text(runs))

    // The trailing " · " a worked row draws between its prefix and the resource word, which are two
    // `Text`s in two colours rather than one string.
    fun worldRowSeparator(prefix: TextRes): TextRes =
        message(StringId.WorldRowSeparator, Arg.Text(prefix))

    // ── The Galaxy tab ───────────────────────────────────────────────────────────────────────

    // "G3" — the galaxy chip, the tab and the universe disc all name a galaxy this way.
    fun galaxyLabel(galaxy: Int): TextRes = message(StringId.GalaxyLabel, Arg.Number(galaxy.toLong()))

    // "Galaxy 3" — the universe caption, which has the room the chip does not.
    fun galaxyNamed(galaxy: Int): TextRes = message(StringId.GalaxyNamed, Arg.Number(galaxy.toLong()))

    fun galaxiesCount(count: Int): TextRes = message(StringId.GalaxiesCount, Arg.Count(count))

    fun systemsCount(count: TextRes): TextRes = message(StringId.SystemsCount, Arg.Text(count))

    fun surveyedCount(count: Int): TextRes = message(StringId.SurveyedCount, Arg.Count(count))

    fun pinnedCount(count: Int): TextRes = message(StringId.PinnedCount, Arg.Count(count))

    // "61 of 250 charted" — fog's whole readout, on the head's count line and on a galaxy disc.
    // Deliberately **not** a second progression gauge: the player strip counts what you are, this
    // counts what you have looked at.
    fun chartedOfSystems(charted: TextRes, systems: TextRes): TextRes =
        message(StringId.ChartedOfSystems, Arg.Text(charted), Arg.Text(systems))

    // The third knowledge tier, named. It sits under `charted` the way `Unsurveyed` sits under
    // `surveyed`, and it is the only word the caption has for a star nobody has been near.
    fun unchartedWord(): TextRes = message(StringId.UnchartedWord)

    // "charts 25 systems" — the fog yield, and the reason a far star is worth more than a near one.
    fun chartsSystems(count: Int): TextRes = message(StringId.ChartsSystems, Arg.Count(count))

    // "75 systems out" — an uncharted star has no name to print, so its distance is what the
    // caption's trailing figure says instead of a coordinate it has already used as the name.
    fun systemsOut(count: Int): TextRes = message(StringId.SystemsOut, Arg.Count(count))

    // "1–25" — a band whose name the light has not reached yet says where it is instead. The en
    // dash is the house form for a range, as in `reachRange` above.
    fun systemRange(from: Int, to: Int): TextRes =
        message(StringId.SystemRange, Arg.Number(from.toLong()), Arg.Number(to.toLong()))

    // "[3:185]" — a system rather than a world, so two fields rather than three.
    fun systemAddress(galaxy: Int, system: Int): TextRes =
        message(StringId.SystemAddressLabel, Arg.Number(galaxy.toLong()), Arg.Number(system.toLong()))

    // The relay's one sentence. It states its effect and stops: no holding mechanic exists until
    // multiplayer, and a relay has no hold for a fleet to fill either.
    fun relayEffect(): TextRes = message(StringId.RelayEffect)

    // "danger 2 from here" — the astronomy line's fuller form, which drops "from here" when the
    // whole line will not fit and falls back to `dangerLevel`.
    fun dangerFromHere(danger: Int): TextRes =
        message(StringId.DangerFromHere, Arg.Number(danger.toLong()))

    // "20m out and back"
    fun reachSingle(trip: TextRes): TextRes = message(StringId.ReachSingle, Arg.Text(trip))

    // "1h 04m–2h 12m out and back"
    fun reachRange(from: TextRes, to: TextRes): TextRes =
        message(StringId.ReachRange, Arg.Text(from), Arg.Text(to))

    // "20–26m out and back" — the collapsed form, and it is the *language's* collapse rather than
    // the mapper's: which unit may be elided from the near end of a range is a fact about how the
    // language writes durations. The caller says only that both ends are minutes, by calling this.
    fun reachRangeMinutes(fromMinutes: Long, to: TextRes): TextRes =
        message(StringId.ReachRangeMinutes, Arg.Number(fromMinutes), Arg.Text(to))

    fun starClassName(starClass: StarClass): TextRes = message(
        when (starClass) {
            StarClass.DIM -> StringId.StarClassDim
            StarClass.STANDARD -> StringId.StarClassStandard
            StarClass.BRIGHT -> StringId.StarClassBright
        },
    )

    // "standard · 3 worlds", and the compact form drops the noun rather than a figure.
    fun starDetail(starClass: StarClass, worlds: Int): TextRes =
        message(StringId.StarDetail, Arg.Text(starClassName(starClass)), Arg.Count(worlds))

    fun starDetailCompact(starClass: StarClass, worlds: Int): TextRes =
        message(StringId.StarDetailCompact, Arg.Text(starClassName(starClass)), Arg.Count(worlds))

    // "no worlds" at zero, which is a different sentence rather than a zero.
    fun worldCount(count: Int): TextRes = message(StringId.WorldCount, Arg.Count(count))

    fun worldsSurveyedCount(count: Int): TextRes =
        message(StringId.WorldsSurveyedCount, Arg.Count(count))

    fun noWorlds(): TextRes = message(StringId.NoWorlds)

    fun homeNote(): TextRes = message(StringId.HomeNote)

    fun probeLandsIn(wait: TextRes): TextRes = message(StringId.ProbeLandsIn, Arg.Text(wait))

    // "probe 4h 40m" — the caption's trailing control.
    fun probeFlight(duration: TextRes): TextRes = message(StringId.ProbeFlight, Arg.Text(duration))

    // "flight 4h 40m" — the offer's own line, which names the leg rather than the craft because the
    // card above it is already about the probe.
    fun probeFlightLabel(duration: TextRes): TextRes =
        message(StringId.ProbeFlightLabel, Arg.Text(duration))

    // "run 9h 20m"
    fun runFlight(duration: TextRes): TextRes = message(StringId.RunFlight, Arg.Text(duration))

    fun nothingToSurvey(slots: Int): TextRes =
        message(StringId.NothingToSurvey, Arg.Number(slots.toLong()))

    fun surveyedAtGenesis(): TextRes = message(StringId.SurveyedAtGenesis)

    fun dispatchProbe(): TextRes = message(StringId.DispatchProbe)

    fun dispatchProbeCompact(): TextRes = message(StringId.DispatchProbeCompact)

    fun findSettleable(count: Int): TextRes = message(StringId.FindSettleable, Arg.Count(count))

    fun findNearMiss(count: Int): TextRes = message(StringId.FindNearMiss, Arg.Count(count))

    fun findNone(): TextRes = message(StringId.FindNone)

    // "+18 °C", "1.45 g", "0.92 atm" — **the space before each unit is U+00A0**, so a line that has
    // to wrap never leaves "atm" alone on one. That is English's typography and it lives in the
    // table with the unit.
    fun temperatureReading(value: TextRes): TextRes =
        message(StringId.TemperatureReading, Arg.Text(value))

    fun gravityReading(value: TextRes): TextRes = message(StringId.GravityReading, Arg.Text(value))

    fun pressureReading(value: TextRes): TextRes = message(StringId.PressureReading, Arg.Text(value))

    // "found 5h 12m ago"
    fun foundAgo(elapsed: TextRes): TextRes = message(StringId.FoundAgo, Arg.Text(elapsed))

    fun axisName(axis: HostilityAxis): TextRes = message(
        when (axis) {
            HostilityAxis.TEMPERATURE -> StringId.AxisTemperature
            HostilityAxis.GRAVITY -> StringId.AxisGravity
            HostilityAxis.PRESSURE -> StringId.AxisPressure
        },
    )

    // "veiled furnace" — what a world *is*, in two words derived from its three axes. One entry over
    // two arguments rather than a `clauses` of them, because the order is the language's: English
    // puts the adjective first and Italian puts it second, agreeing with the noun.
    fun worldEpithet(epithet: WorldEpithet): TextRes = message(
        StringId.WorldEpithet,
        Arg.Text(epithetAdjective(epithet.adjective)),
        Arg.Text(epithetNoun(epithet.noun)),
    )

    // Separate entries rather than one per pair, because there are six nouns and twelve adjectives
    // and only about seventy of the seventy-two combinations occur — and a catalogue of pairs would
    // be a catalogue nobody could check against the galaxy.
    private fun epithetNoun(noun: EpithetNoun): TextRes = message(
        when (noun) {
            EpithetNoun.FURNACE -> StringId.EpithetNounFurnace
            EpithetNoun.FROST -> StringId.EpithetNounFrost
            EpithetNoun.GIANT -> StringId.EpithetNounGiant
            EpithetNoun.HUSK -> StringId.EpithetNounHusk
            EpithetNoun.SHROUD -> StringId.EpithetNounShroud
            EpithetNoun.WASTE -> StringId.EpithetNounWaste
            EpithetNoun.WORLD -> StringId.EpithetNounWorld
        },
    )

    private fun epithetAdjective(adjective: EpithetAdjective): TextRes = message(
        when (adjective) {
            EpithetAdjective.SCORCHED -> StringId.EpithetAdjectiveScorched
            EpithetAdjective.FROZEN -> StringId.EpithetAdjectiveFrozen
            EpithetAdjective.IRON -> StringId.EpithetAdjectiveIron
            EpithetAdjective.HOLLOW -> StringId.EpithetAdjectiveHollow
            EpithetAdjective.VEILED -> StringId.EpithetAdjectiveVeiled
            EpithetAdjective.AIRLESS -> StringId.EpithetAdjectiveAirless
            EpithetAdjective.ASHEN -> StringId.EpithetAdjectiveAshen
            EpithetAdjective.DEEP -> StringId.EpithetAdjectiveDeep
            EpithetAdjective.BRITTLE -> StringId.EpithetAdjectiveBrittle
            EpithetAdjective.DROWNED -> StringId.EpithetAdjectiveDrowned
            EpithetAdjective.BARE -> StringId.EpithetAdjectiveBare
            EpithetAdjective.TEMPERATE -> StringId.EpithetAdjectiveTemperate
        },
    )

    fun noteHome(): TextRes = message(StringId.NoteHome)

    fun noteOccupied(holder: TextRes): TextRes = message(StringId.NoteOccupied, Arg.Text(holder))

    fun noteSettleable(): TextRes = message(StringId.NoteSettleable)

    // "Yield 0.71, worth it at 0.92" — naming the threshold is what makes a run of Barren answers
    // read as calibration rather than as bad luck.
    fun noteBarren(yield: TextRes, threshold: TextRes): TextRes =
        message(StringId.NoteBarren, Arg.Text(yield), Arg.Text(threshold))

    fun noteBarrenDiscovery(): TextRes = message(StringId.NoteBarrenDiscovery)

    fun noteBlocked(): TextRes = message(StringId.NoteBlocked)

    // "Gravitic 9 would land it."
    fun noteWouldLandIt(technology: TextRes, level: Int): TextRes =
        message(StringId.NoteWouldLandIt, Arg.Text(technology), Arg.Number(level.toLong()))

    fun noteSurveyed(): TextRes = message(StringId.NoteSurveyed)

    fun worthItAt(threshold: TextRes): TextRes = message(StringId.WorthItAt, Arg.Text(threshold))

    // "174/819" — a deposit read as a fraction, because 120 of 600 and 120 of 2,400 are the same
    // number and not the same target.
    fun depositFraction(remaining: TextRes, cap: TextRes): TextRes =
        message(StringId.DepositFraction, Arg.Text(remaining), Arg.Text(cap))

    fun ledgerEmptyHeadline(): TextRes = message(StringId.LedgerEmptyHeadline)

    fun ledgerEmptyDetail(): TextRes = message(StringId.LedgerEmptyDetail)

    fun ledgerNoMatchHeadline(): TextRes = message(StringId.LedgerNoMatchHeadline)

    fun ledgerNoMatchDetail(): TextRes = message(StringId.LedgerNoMatchDetail)

    // The one word at the right end of a world row. `UNSURVEYED` has none — an empty socket is the
    // state, stated in the position the state belongs in.
    fun verdictWordHome(): TextRes = message(StringId.VerdictWordHome)

    fun verdictWordOccupied(): TextRes = message(StringId.VerdictWordOccupied)

    fun verdictWordBlocked(): TextRes = message(StringId.VerdictWordBlocked)

    fun verdictWordBarren(): TextRes = message(StringId.VerdictWordBarren)

    fun verdictWordSettleable(): TextRes = message(StringId.VerdictWordSettleable)

    // "SURVEYED" over one discovery card, "3 WORLDS SURVEYED" over several. Two entries rather than
    // a count of one, because the singular heading names no number at all.
    fun discoveriesHeadingOne(): TextRes = message(StringId.DiscoveriesHeadingOne)

    fun discoveriesHeadingMany(count: Int): TextRes =
        message(StringId.DiscoveriesHeadingMany, Arg.Count(count))

    fun pinnedHeading(): TextRes = message(StringId.PinnedHeading)

    fun relayLabel(): TextRes = message(StringId.RelayLabel)

    fun ledgerModeWorlds(): TextRes = message(StringId.LedgerModeWorlds)

    fun ledgerModeMap(): TextRes = message(StringId.LedgerModeMap)

    fun searchPlaceholder(): TextRes = message(StringId.SearchPlaceholder)

    // "gravity 2.62, you tolerate 1.45 g" — the unit is written once, on the tolerance, because both
    // figures are the same axis and the four characters that saves keep the ladder on the line.
    fun blockedAxisLine(axis: TextRes, reading: TextRes, tolerated: TextRes): TextRes =
        message(StringId.BlockedAxisLine, Arg.Text(axis), Arg.Text(reading), Arg.Text(tolerated))

    // The middot on its own, between two `Text`s in two colours rather than inside one string.
    fun middot(): TextRes = message(StringId.MiddotStandalone)

    fun orbitSlot(slot: Int): TextRes = message(StringId.OrbitSlot, Arg.Number(slot.toLong()))

    // ── The shell ────────────────────────────────────────────────────────────────────────────

    // The five destinations. Named one by one rather than through `OltreTab`, because that enum
    // lives in `:client:shell` and nothing may depend on the shell — rule 7.
    fun tabColony(): TextRes = message(StringId.TabColony)

    fun tabResearch(): TextRes = message(StringId.TabResearch)

    fun tabShipyard(): TextRes = message(StringId.TabShipyard)

    fun tabGalaxy(): TextRes = message(StringId.TabGalaxy)

    fun tabFleets(): TextRes = message(StringId.TabFleets)

    // The stepper's two faces. **A true minus sign, not a hyphen** — the same glyph `signed` spends,
    // and the reason it is an entry rather than a literal in the sheet: which mark a language uses
    // for "one fewer" is the language's, and one of the two is already in the table.
    fun stepperFewer(): TextRes = message(StringId.StepperFewer)

    fun stepperMore(): TextRes = message(StringId.StepperMore)

    // The gap a worked row leaves between the resource it is coloured by and the reading beside it.
    // Two `Text`s in two colours, so the space between them cannot live inside either.
    fun depositGap(): TextRes = message(StringId.DepositGap)

    // Android's notification channel, which the player reads in the *system's* settings rather than
    // in the game. PLACEHOLDER copy, like the alerts themselves.
    fun notificationChannelName(): TextRes = message(StringId.NotificationChannelName)

    fun notificationChannelDescription(): TextRes = message(StringId.NotificationChannelDescription)

    // "watching Metal Mine" — the one clause in the app that names a row on a screen it is not being
    // read from.
    fun watching(row: TextRes): TextRes = message(StringId.Watching, Arg.Text(row))

    // "+1,240/h" — the rail's rate, which is a statement about now rather than a quantity.
    fun ratePerHour(amount: TextRes): TextRes = message(StringId.RatePerHour, Arg.Text(amount))

    // The rail's three headings, in the case they are drawn in. Their own entries rather than
    // `resourceTitle` uppercased: the rail is a fixed piece of chrome and its labels are set in
    // caps by the design, which is a decision about *this* surface.
    fun resourceRailLabel(kind: ResourceKind): TextRes = message(
        when (kind) {
            ResourceKind.METAL -> StringId.ResourceRailMetal
            ResourceKind.CRYSTAL -> StringId.ResourceRailCrystal
            ResourceKind.DEUTERIUM -> StringId.ResourceRailDeuterium
        },
    )

    // ── Notifications ────────────────────────────────────────────────────────────────────────

    // "Metal Mine, Solar Plant and Extraction" — commas between, "and" before the last, and no
    // Oxford comma. **Two separators rather than a `joinToString`**, which is what makes the whole
    // convention a language's: a locale that uses the serial comma changes `ListSeparator`, and one
    // that puts the conjunction elsewhere changes `ListLastSeparator`.
    //
    // Never called with fewer than two: a group is two or more by construction.
    fun listed(parts: List<TextRes>): TextRes = TextRes.Joined(
        parts = listOf(
            TextRes.Joined(parts.dropLast(1), separator = message(StringId.ListSeparator)),
            parts.last(),
        ),
        separator = message(StringId.ListLastSeparator),
    )

    // "Three upgrades are done" — spelled rather than printed as a digit, because the game prints
    // digits for levels and this is a count in a sentence.
    fun upgradesDoneTitle(count: Int): TextRes = message(StringId.UpgradesDoneTitle, Arg.Count(count))

    fun upgradesDoneBody(subjects: TextRes): TextRes =
        message(StringId.UpgradesDoneBody, Arg.Text(subjects))

    // "Metal Mine reached level 4" — one entry for all three completions, because it is one sentence.
    fun reachedLevel(subject: TextRes, level: Int): TextRes =
        message(StringId.ReachedLevel, Arg.Text(subject), Arg.Number(level.toLong()))

    fun buildCompleteBody(): TextRes = message(StringId.BuildCompleteBody)

    fun labFreeBody(): TextRes = message(StringId.LabFreeBody)

    fun adaptationOpenedBody(): TextRes = message(StringId.AdaptationOpenedBody)

    fun hullLeftYardTitle(ship: TextRes): TextRes = message(StringId.HullLeftYardTitle, Arg.Text(ship))

    fun hullLeftYardBody(): TextRes = message(StringId.HullLeftYardBody)

    // "5 hulls have left the yard" — the whole order as one sentence, for the player who tapped a
    // hull card once rather than twice. Never called with fewer than two: an order of one is the
    // singleton alert above, exactly as a group of one is the thing itself.
    fun hullOrderDoneTitle(hulls: Int): TextRes = message(StringId.HullOrderDoneTitle, Arg.Count(hulls))

    fun hullOrderDoneBody(ship: TextRes): TextRes = message(StringId.HullOrderDoneBody, Arg.Text(ship))

    fun probeReachedTitle(system: TextRes): TextRes =
        message(StringId.ProbeReachedTitle, Arg.Text(system))

    // **Two entries and both are the design rather than a formatting convenience**: an alert that
    // only ever counted worlds would read as a payoff nearly every time it fired, and saying "none"
    // plainly is what makes "1 settleable" mean anything when it arrives.
    fun chartedNoneSettleable(worlds: Int): TextRes =
        message(StringId.ChartedNoneSettleable, Arg.Count(worlds))

    fun chartedSettleable(worlds: Int, settleable: Int): TextRes =
        message(StringId.ChartedSettleable, Arg.Count(worlds), Arg.Count(settleable))

    fun shipsHomeTitle(): TextRes = message(StringId.ShipsHomeTitle)

    fun shipsHomeBody(target: TextRes): TextRes = message(StringId.ShipsHomeBody, Arg.Text(target))

    fun affordableTitle(subject: TextRes): TextRes =
        message(StringId.AffordableTitle, Arg.Text(subject))

    fun affordableBody(level: Int): TextRes =
        message(StringId.AffordableBody, Arg.Number(level.toLong()))

    // "3:185" — no slot and no brackets: a probe is aimed at a star, and there is nothing for a
    // bracket to separate a bare system from.
    fun systemAddressBare(galaxy: Int, system: Int): TextRes =
        message(StringId.SystemAddressBare, Arg.Number(galaxy.toLong()), Arg.Number(system.toLong()))

    // The unabbreviated names, which the lock screen has the room for and a row does not.
    fun buildingFullName(building: BuildingType): TextRes = message(
        when (building) {
            BuildingType.METAL_MINE -> StringId.BuildingFullNameMetalMine
            BuildingType.CRYSTAL_MINE -> StringId.BuildingFullNameCrystalMine
            BuildingType.DEUTERIUM_SYNTHESIZER -> StringId.BuildingFullNameDeuteriumSynthesizer
            BuildingType.SOLAR_PLANT -> StringId.BuildingFullNameSolarPlant
            BuildingType.ROBOTICS_FACTORY -> StringId.BuildingFullNameRoboticsFactory
            BuildingType.NANITE_FACTORY -> StringId.BuildingFullNameNaniteFactory
        },
    )

    // Capitalised, unlike the Colony strip's lower-case tally: this one opens a lock-screen title.
    fun shipTitleName(ship: ShipType): TextRes = message(
        when (ship) {
            ShipType.SCOUT -> StringId.ShipTitleNameScout
            ShipType.SKIFF -> StringId.ShipTitleNameSkiff
            ShipType.HAULER -> StringId.ShipTitleNameHauler
            ShipType.ESCORT -> StringId.ShipTitleNameEscort
            ShipType.SETTLER -> StringId.ShipTitleNameSettler
        },
    )

    // With the word the Galaxy screen's blocked rows drop to save eleven characters they do not
    // have: "Gravitic reached level 3" on its own does not say what kind of thing climbed.
    fun adaptationFullName(technology: AdaptationTechnology): TextRes = message(
        when (technology) {
            AdaptationTechnology.THERMAL -> StringId.AdaptationFullNameThermal
            AdaptationTechnology.GRAVITIC -> StringId.AdaptationFullNameGravitic
            AdaptationTechnology.ATMOSPHERIC -> StringId.AdaptationFullNameAtmospheric
        },
    )

    // **What the player is called before anything has let them choose.** A catalogue entry rather
    // than a `TextRes.Raw`, even though it is a proper noun and both tables spell it the same:
    // `Raw` is for text from *outside* the catalogue — a generated system name, a world's epithet —
    // and this is authored copy. A language that wants its own callsign changes one line here.
    fun playerDefaultName(): TextRes = message(StringId.PlayerDefaultName)

    // **`settingsComingSoon` used to live here and left with 0.18.** It was the one string in the
    // game that broke its own voice rules and it did so on the record — the design system lists
    // "Coming soon" under *Never written*, Davide took the literal reading over `Settings land here.`
    // on 2026-08-22, and the note said the design system was what would get amended. The gear opens
    // something now, so the exception is closed rather than carried and the never-written list is
    // whole again. See `.claude/docs/player-strip-sheet.md` §2.

    // ── Alerts arriving together ────────────────────────────────────────────────────────────
    //
    // What a group of one kind is called — "3 facilities are done". **Never called with fewer than
    // two**: one of anything is that thing's own singleton alert, which is the same rule
    // `upgradesDoneTitle` and `hullOrderDoneTitle` are written to.
    //
    // A `when` over the category rather than a noun argument, because the verb differs per kind in
    // both tables and a shared frame would force one verb on all seven.
    fun alertGroupTitle(category: AlertCategory, count: Int): TextRes = message(
        when (category) {
            AlertCategory.FACILITIES -> StringId.AlertGroupFacilities
            AlertCategory.RESEARCH -> StringId.AlertGroupResearch
            AlertCategory.ADAPTATIONS -> StringId.AlertGroupAdaptations
            AlertCategory.HULLS -> StringId.AlertGroupHulls
            AlertCategory.PROBES -> StringId.AlertGroupProbes
            AlertCategory.FLEET_RETURNS -> StringId.AlertGroupFleetReturns
            AlertCategory.PRICE_REACHED -> StringId.AlertGroupPriceReached
        },
        Arg.Count(count),
    )

    // "3 fleets" — a count and a noun, for the title that holds more than one kind. Unlike the group
    // titles above this one *is* called with a count of one, because a title reading
    // "3 facilities · 1 fleet" is one fleet and two kinds.
    fun alertCountClause(category: AlertCategory, count: Int): TextRes = message(
        when (category) {
            AlertCategory.FACILITIES -> StringId.AlertCountFacilities
            // **`project`, not `research`**, and it is the design's note rather than a slip: the
            // category is named Research to match the tab, and "2 researches" is not English.
            AlertCategory.RESEARCH -> StringId.AlertCountResearch
            AlertCategory.ADAPTATIONS -> StringId.AlertCountAdaptations
            AlertCategory.HULLS -> StringId.AlertCountHulls
            AlertCategory.PROBES -> StringId.AlertCountProbes
            AlertCategory.FLEET_RETURNS -> StringId.AlertCountFleetReturns
            AlertCategory.PRICE_REACHED -> StringId.AlertCountPriceReached
        },
        Arg.Count(count),
    )

    // "+2", counting the kinds the title had no room for. A count of *categories* rather than of
    // things, so that a title reading "+2" always answers "two more kinds of thing".
    fun alertMoreCategories(count: Int): TextRes =
        message(StringId.AlertMoreCategories, Arg.Count(count))

    // ── The settings sheet ──────────────────────────────────────────────────────────────────

    // The gear's own accessibility label, promoted to the title of what it opens. **Not
    // Notifications**: the second section is not far off and this one should not have to be renamed.
    fun settingsTitle(): TextRes = message(StringId.SettingsTitle)

    fun alertsLabel(): TextRes = message(StringId.AlertsLabel)

    // The two-chip ladder. Two prepositional phrases of the same shape, which is what lets the pair
    // explain itself without a sentence under each one.
    fun alertModePerItem(): TextRes = message(StringId.AlertModePerItem)

    fun alertModeByCategory(): TextRes = message(StringId.AlertModeByCategory)

    // The same two entries reached by their value rather than by name, for the one caller that has an
    // `AlertMode` in hand and needs the chip's own words back: the held note says *which stop* was
    // asked for, and it must be the string on the chip rather than a second name for it.
    fun alertModeName(mode: AlertMode): TextRes = when (mode) {
        AlertMode.PER_ITEM -> alertModePerItem()
        AlertMode.BY_CATEGORY -> alertModeByCategory()
    }

    // The one line the ladder carries, and it has two clauses on purpose: what the mode means, then
    // what it does to the screen the player came from. The second clause is what stops a colony full
    // of rows that suddenly have no square reading as a bug.
    fun alertModeNote(mode: AlertMode): TextRes = message(
        when (mode) {
            AlertMode.PER_ITEM -> StringId.AlertModePerItemNote
            AlertMode.BY_CATEGORY -> StringId.AlertModeByCategoryNote
        },
    )

    fun alertCategoryName(category: AlertCategory): TextRes = message(
        when (category) {
            AlertCategory.FACILITIES -> StringId.AlertCategoryFacilities
            AlertCategory.RESEARCH -> StringId.AlertCategoryResearch
            AlertCategory.ADAPTATIONS -> StringId.AlertCategoryAdaptations
            AlertCategory.HULLS -> StringId.AlertCategoryHulls
            AlertCategory.PROBES -> StringId.AlertCategoryProbes
            AlertCategory.FLEET_RETURNS -> StringId.AlertCategoryFleetReturns
            AlertCategory.PRICE_REACHED -> StringId.AlertCategoryPriceReached
        },
    )

    // **The only second line in the panel, on the only row that needs one.** Every other category
    // switch decides whether a kind of news is announced; this one decides whether a *watch exists at
    // all*, so off states the consequence rather than the setting — nothing is muted, the row simply
    // stops carrying the square that books it.
    fun alertPriceWatchNote(on: Boolean): TextRes =
        message(if (on) StringId.AlertPriceWatchOn else StringId.AlertPriceWatchOff)

    // What a bell says when it is read aloud, on the row rather than on the square, because the row
    // is the target. Combined with the category's name through `clauses`, label first and state
    // second — "Facilities · alerts on".
    fun alertBellState(on: Boolean): TextRes =
        message(if (on) StringId.AlertBellOn else StringId.AlertBellOff)

    fun deliveryLabel(): TextRes = message(StringId.DeliveryLabel)

    // Three parallel names, so the set explains itself without prose under it. *Category* is
    // deliberately the same word the ladder above uses: it is the same seven things.
    fun deliveryName(delivery: AlertDelivery): TextRes = message(
        when (delivery) {
            AlertDelivery.EACH -> StringId.DeliveryEach
            AlertDelivery.PER_CATEGORY -> StringId.DeliveryPerCategory
            AlertDelivery.TOTAL -> StringId.DeliveryTotal
        },
    )

    // **The only thing on the sheet nobody could guess**, which is when the next buzz is. Absent
    // under `EACH`, where the answer is "whenever anything lands" and needs no explaining.
    //
    // `TOTAL` gets its own sentence rather than the same one, because its second clause is the whole
    // of what the stop means: the notification is not repeated, it is brought up to date.
    fun alertNextAt(hour: Int, minute: Int, updating: Boolean): TextRes = message(
        if (updating) StringId.AlertNextAtTotal else StringId.AlertNextAt,
        Arg.Number(hour.toLong()),
        Arg.Number(minute.toLong()),
    )

    // A colony with nothing in flight, which is most colonies most of the time. The honest answer
    // rather than a hidden line: an empty slot where a time was would read as a control that failed.
    fun alertNothingPending(): TextRes = message(StringId.AlertNothingPending)

    // ── The changelog sheet ──────────────────────────────────────────────────────────────────

    fun changelogTitle(): TextRes = message(StringId.ChangelogTitle)

    // "65 releases". `Count` rather than `Number` because it selects a form — the two are the same
    // integer and only one of them may change the words around it.
    fun changelogDepth(releases: Int): TextRes = message(StringId.ChangelogDepth, Arg.Count(releases))

    // **The first calendar date the game has ever written.** The month arrives as its number and the
    // language names it, which is the only shape that works: a pre-rendered "Aug" would have baked
    // English into the argument, and a `LocalDate` would have put a platform type in the catalogue.
    fun releaseDate(day: Int, month: Int, year: Int): TextRes = message(
        StringId.ReleaseDate,
        Arg.Number(day.toLong()),
        Arg.Number(month.toLong()),
        Arg.Number(year.toLong()),
    )

    fun buildLabel(): TextRes = message(StringId.BuildLabel)

    // The settings row read as one sentence. The version goes in as text rather than as a number
    // because it is three of them and the point is that it is a name.
    fun buildRowSpoken(version: TextRes, headline: TextRes): TextRes =
        message(StringId.BuildRowSpoken, Arg.Text(version), Arg.Text(headline))

    // ── The gate ────────────────────────────────────────────────────────────────────────────

    // Two lines of why, because there are exactly two facts and both are load-bearing: the galaxy is
    // shared, and the colony runs on the server. The second is what makes an account a mechanism
    // rather than a policy — and it is the sentence the offline failure needs to have already said.
    fun signInWhyLead(): TextRes = message(StringId.SignInWhyLead)

    fun signInWhyFoot(): TextRes = message(StringId.SignInWhyFoot)

    // The one reassurance the screen is entitled to make, and the reason it is not a consent notice:
    // nothing has happened yet, and the foot says so rather than asking for agreement.
    fun signInFoot(): TextRes = message(StringId.SignInFoot)

    // **The two strings the game does not own.** Apple mandates the wording and translates it
    // itself; Google's is near enough the same. They are catalogue entries so that each language
    // gets the platform's own — see `StringId`.
    fun signInWith(provider: AuthProviderName): TextRes = message(
        when (provider) {
            AuthProviderName.APPLE -> StringId.SignInWithApple
            AuthProviderName.GOOGLE -> StringId.SignInWithGoogle
        },
    )

    fun providerName(provider: AuthProviderName): TextRes = message(
        when (provider) {
            AuthProviderName.APPLE -> StringId.ProviderApple
            AuthProviderName.GOOGLE -> StringId.ProviderGoogle
        },
    )

    // One muted sentence and nothing moves. A spinner would be the first looping animation in the
    // product and would claim knowledge the app does not have: it cannot tell a slow answer from no
    // answer.
    fun signInWaitingLead(): TextRes = message(StringId.SignInWaitingLead)

    fun signInWaitingBody(): TextRes = message(StringId.SignInWaitingBody)

    // No signal and a service that is down are one screen, because they are one instruction: wait.
    // The second sentence is the only place in the product that states the rule out loud.
    fun signInNoAnswerLead(): TextRes = message(StringId.SignInNoAnswerLead)

    fun signInNoAnswerBody(): TextRes = message(StringId.SignInNoAnswerBody)

    // What the gate says when it can draw no provider at all — a fact about the build rather than
    // about anything the player just did, and the only reason the screen is not mute.
    fun signInNoProviderLead(): TextRes = message(StringId.SignInNoProviderLead)

    fun signInNoProviderBody(): TextRes = message(StringId.SignInNoProviderBody)

    // **One string for a refusal and for a cancellation**, because the app frequently cannot tell
    // them apart and an accusation is worse than a fact. The provider is named because the player
    // pressed it; the other one is named because it is the next thing to try.
    fun signInRefusedLead(provider: AuthProviderName): TextRes =
        message(StringId.SignInRefusedLead, Arg.Text(providerName(provider)))

    fun signInRefusedBody(alternative: AuthProviderName): TextRes =
        message(StringId.SignInRefusedBody, Arg.Text(providerName(alternative)))

    fun signInThrottledLead(): TextRes = message(StringId.SignInThrottledLead)

    // **The number does not tick.** No timers, ever, predates this screen, so the wait is recomputed
    // when it is asked for rather than counted down — which also makes each impatient tap visibly
    // spend part of it. Past the window the line changes rather than the button silently working.
    fun signInThrottledBody(seconds: Int): TextRes =
        message(StringId.SignInThrottledBody, Arg.Text(shortWait(seconds)))

    fun signInAskAgainNow(): TextRes = message(StringId.SignInAskAgainNow)

    // Two units at most, which is the committed format one order of magnitude down. Under a minute
    // there is one unit to print and printing `0m 41s` would be the app padding a wait to look
    // longer than it is.
    fun shortWait(seconds: Int): TextRes = if (seconds < SECONDS_PER_MINUTE) {
        message(StringId.DurationSeconds, Arg.Number(seconds.toLong()))
    } else {
        message(
            StringId.DurationMinutesSeconds,
            Arg.Number((seconds / SECONDS_PER_MINUTE).toLong()),
            Arg.Number((seconds % SECONDS_PER_MINUTE).toLong()),
        )
    }

    // ── Accepted, and not a fact yet ─────────────────────────────────────────────────────────

    // The amber ghost. Still a target — pressing it withdraws the request — which is why it is a
    // word rather than a state the button wears silently.
    fun heldButton(): TextRes = message(StringId.HeldButton)

    fun heldUpgradeFoot(): TextRes = message(StringId.HeldUpgradeFoot)

    // One entry for a project and an adaptation alike: both are *started*, both take the one slot,
    // and a card that said something different depending on which branch it was on would be
    // inventing a distinction the queue does not make.
    fun heldStartFoot(): TextRes = message(StringId.HeldStartFoot)

    // **Two entries because a hull card can hold two requests at once**, and the design draws the
    // pair as one sentence rather than as two lines: the build and the alert land together, which
    // is the fact a player needs and neither line says on its own.
    fun heldBuildFoot(withAlert: Boolean): TextRes =
        message(if (withAlert) StringId.HeldBuildAndAlertFoot else StringId.HeldBuildFoot)

    // The square says *not confirmed* and this says which way, because 29dp cannot.
    fun heldWatchFoot(on: Boolean): TextRes =
        message(if (on) StringId.HeldWatchOnFoot else StringId.HeldWatchOffFoot)

    // The dispatch sheet's bell, which has a richer thing to say than a direction: what being told
    // actually means, and that it waits on the run being confirmed.
    fun heldAnnounceFoot(on: Boolean): TextRes =
        if (on) message(StringId.HeldAnnounceFoot) else heldTurning(on = false)

    fun heldTurning(on: Boolean): TextRes =
        message(if (on) StringId.HeldTurningOn else StringId.HeldTurningOff)

    // **A ladder is the one held control that names a destination**, because it is the one that has
    // somewhere to go: the server's stop keeps accent and the asked-for stop takes amber, so the
    // note has to say which stop the amber chip is.
    fun heldLadderNote(stop: TextRes): TextRes = message(StringId.HeldLadderNote, Arg.Text(stop))

    // The one new piece of chrome in the offline era. It carries the network fact and the count and
    // **never the state of a control** — a banner that said three things were held could not say
    // which three, and the player is looking at the switch rather than at the top of the screen.
    fun offlineSince(hour: Int, minute: Int, held: Int, compact: Boolean): TextRes = message(
        if (compact) StringId.OfflineSinceCompact else StringId.OfflineSince,
        Arg.Number(hour.toLong()),
        Arg.Number(minute.toLong()),
        Arg.Count(held),
    )

    // ── Refused outright ─────────────────────────────────────────────────────────────────────

    // **A run and a probe aim at a shared galaxy, so neither can be held.** Everything else offline
    // is a bet against your own colony, which the server can settle; these are aimed at a world
    // somebody else may now hold, and a queue would be aiming at a snapshot.
    fun refusedRunLead(): TextRes = message(StringId.RefusedRunLead)

    fun refusedRunBody(target: TextRes, compact: Boolean): TextRes = message(
        if (compact) StringId.RefusedRunBodyCompact else StringId.RefusedRunBody,
        Arg.Text(target),
    )

    fun refusedProbeLead(): TextRes = message(StringId.RefusedProbeLead)

    fun refusedProbeBody(compact: Boolean): TextRes =
        message(if (compact) StringId.RefusedProbeBodyCompact else StringId.RefusedProbeBody)

    // Deleting an account needs the network too, so it refuses in the same grammar as a dispatch.
    // Nothing in the brief said so; the design drew it and it is right.
    fun refusedDeleteLead(): TextRes = message(StringId.RefusedDeleteLead)

    fun refusedDeleteBody(): TextRes = message(StringId.RefusedDeleteBody)

    // ── The account, and the door out of it ──────────────────────────────────────────────────

    fun accountLabel(): TextRes = message(StringId.AccountLabel)

    fun accountSignedInWith(provider: AuthProviderName): TextRes =
        message(StringId.AccountSignedInWith, Arg.Text(providerName(provider)))

    fun accountSince(name: TextRes, day: Int, month: Int): TextRes = message(
        StringId.AccountSince,
        Arg.Text(name),
        Arg.Number(day.toLong()),
        Arg.Number(month.toLong()),
    )

    // **Body-weight and muted, with the consequence as its verdict line** — red on a settings row is
    // a warning nobody asked for yet, and accent would mean *go tap this*.
    fun deleteAccountRow(): TextRes = message(StringId.DeleteAccountRow)

    fun deleteAccountRowNote(): TextRes = message(StringId.DeleteAccountRowNote)

    fun deleteFaceTitle(): TextRes = message(StringId.DeleteFaceTitle)

    fun deleteFaceIntro(): TextRes = message(StringId.DeleteFaceIntro)

    // **The fact the numbers cannot teach**, and the one Apple's requirement does not ask for. Both
    // providers are named whichever one signed you in, because the sentence is about what an account
    // is rather than about which one this is.
    fun deleteFaceSecond(): TextRes = message(StringId.DeleteFaceSecond)

    fun deleteFaceAction(): TextRes = message(StringId.DeleteFaceAction)

    // The four rows are for reading, in the colony's own numbers, and reading them is what makes the
    // second sentence land.
    fun deleteFactLabel(fact: DeleteFactKind): TextRes = message(
        when (fact) {
            DeleteFactKind.COLONY -> StringId.DeleteFactColonyLabel
            DeleteFactKind.FLEET -> StringId.DeleteFactFleetLabel
            DeleteFactKind.MAP -> StringId.DeleteFactMapLabel
            DeleteFactKind.RESEARCH -> StringId.DeleteFactResearchLabel
        },
    )

    fun deleteFactColony(name: TextRes, facilities: Int, level: Int): TextRes = message(
        StringId.DeleteFactColony,
        Arg.Text(name),
        Arg.Count(facilities),
        Arg.Number(level.toLong()),
    )

    // **Null hulls is the first launch rather than an edge case**, so it gets a sentence of its own:
    // a colony opens with no hull at all — the first one is the first purchase — and a list of
    // nothing has no grammar to join.
    fun deleteFactFleet(hulls: TextRes?, runs: Int): TextRes = if (hulls == null) {
        message(StringId.DeleteFactFleetEmpty)
    } else {
        message(StringId.DeleteFactFleet, Arg.Text(hulls), Arg.Count(runs))
    }

    fun deleteFactMap(surveyed: Int, pinned: Int): TextRes =
        message(StringId.DeleteFactMap, Arg.Count(surveyed), Arg.Count(pinned))

    fun deleteFactResearch(projects: Int, adaptations: Int): TextRes =
        message(StringId.DeleteFactResearch, Arg.Count(projects), Arg.Count(adaptations))

    // **The colony rather than the account**, because the colony is the thing being lost — and it is
    // the one proper noun on the face.
    fun deleteConfirmTitle(name: TextRes): TextRes =
        message(StringId.DeleteConfirmTitle, Arg.Text(name))

    fun deleteConfirmIntro(): TextRes = message(StringId.DeleteConfirmIntro)

    fun deleteConfirmSecond(provider: AuthProviderName): TextRes =
        message(StringId.DeleteConfirmSecond, Arg.Text(providerName(provider)))

    // First in the row and the wider read, because dismissal is a no.
    fun deleteKeep(): TextRes = message(StringId.DeleteKeep)

    fun deleteConfirmAction(): TextRes = message(StringId.DeleteConfirmAction)

    // ── A name you chose, and a mark you picked ──────────────────────────────────────────────

    fun profileTitle(): TextRes = message(StringId.ProfileTitle)

    fun profileMarkLabel(): TextRes = message(StringId.ProfileMarkLabel)

    fun profileNameLabel(): TextRes = message(StringId.ProfileNameLabel)

    // One entry per silhouette behind one exhaustive `when`, which is `epithetNoun`'s shape exactly:
    // a preset is a drawing somebody made, and a seventh one cannot reach a screen without the word
    // for it existing in every language first.
    fun markName(preset: MarkPresetName): TextRes = message(
        when (preset) {
            MarkPresetName.THRESHOLD -> StringId.MarkNameThreshold
            MarkPresetName.TERMINATOR -> StringId.MarkNameTerminator
            MarkPresetName.APHELION -> StringId.MarkNameAphelion
            MarkPresetName.SEXTANT -> StringId.MarkNameSextant
            MarkPresetName.WAKE -> StringId.MarkNameWake
            MarkPresetName.SOUNDING -> StringId.MarkNameSounding
        },
    )

    fun profileSaveName(): TextRes = message(StringId.ProfileSaveName)

    // **The default name is composed in rather than spelled out**, because there is exactly one
    // `Dead Reckoning` in the catalogue and a second copy is a second thing to keep in step. An
    // argument for `worldEpithet`'s reason too: English puts it in the middle of the sentence and
    // Italian puts it at the end, and only the tables know that.
    fun profileEmptyName(): TextRes =
        message(StringId.ProfileEmptyName, Arg.Text(playerDefaultName()))

    // "18/24". The bound is `CommanderName.MAX_LENGTH`'s to hold, and this module deliberately cannot
    // see the wire contract — so the counter prints the number it is handed rather than knowing one,
    // which is also what keeps the field and the counter from disagreeing.
    fun profileNameCounter(length: Int, max: Int): TextRes =
        message(StringId.ProfileNameCounter, Arg.Number(length.toLong()), Arg.Number(max.toLong()))

    // **Not `offlineSince`, though English reads nearly the same.** That one is the banner and ends in
    // a count of what is waiting; a rename never queues, so there is nothing to count. The two are
    // also not the same sentence in Italian — see the note in `Italian`.
    fun profileHeldRequirement(hour: Int, minute: Int): TextRes =
        clock(StringId.ProfileHeldRequirement, hour, minute)

    // **The same amber card for the other thing that stops a rename**: the account has not been read,
    // so there is no profile to build a whole-row write out of. No clock, because this state is not a
    // claim about the network — see `profileRequirement`, which chooses between the two.
    fun profileUnreadRequirement(): TextRes = message(StringId.ProfileUnreadRequirement)

    fun profileHeldBody(): TextRes = message(StringId.ProfileHeldBody)

    // The body's first sentence again, under the field, and its own entry rather than a substring of
    // the card's: the card explains and the field only refuses, so a language that wants a shorter
    // refusal in a 44dp row can have one without shortening the explanation.
    fun profileHeldFieldNote(): TextRes = message(StringId.ProfileHeldFieldNote)

    fun markComposeRow(): TextRes = message(StringId.MarkComposeRow)

    // What a composed mark is called — on the card, and again at the head of the grid line that spells
    // its parts through `clauses`. One entry, because that is one noun for one thing rather than two
    // surfaces coinciding: a composed mark has no name of its own, so this *is* the name.
    fun markComposedName(): TextRes = message(StringId.MarkComposedName)

    // The three slots, as three entries rather than one over a slot enum. Each row's chips come from a
    // different family, so nothing ever draws the three by looping — a key they would not be looked up
    // by is a key nobody needs.
    fun markSlotBody(): TextRes = message(StringId.MarkSlotBody)

    fun markSlotPath(): TextRes = message(StringId.MarkSlotPath)

    fun markSlotTerminus(): TextRes = message(StringId.MarkSlotTerminus)

    fun markBodyName(body: MarkBodyName): TextRes = message(
        when (body) {
            MarkBodyName.LIMB -> StringId.MarkBodyLimb
            MarkBodyName.TERMINATOR -> StringId.MarkBodyTerminator
            MarkBodyName.ORBIT -> StringId.MarkBodyOrbit
            MarkBodyName.WAKE -> StringId.MarkBodyWake
        },
    )

    // **`NONE` is a part and not an absence**, in both this family and the terminus one below: the
    // composer draws it as a chip the player taps, so it is named like the three beside it. The two
    // `None`s are different words in Italian, which is the whole reason they are different ids.
    fun markPathName(path: MarkPathName): TextRes = message(
        when (path) {
            MarkPathName.RISING -> StringId.MarkPathRising
            MarkPathName.TRANSFER -> StringId.MarkPathTransfer
            MarkPathName.TWIN -> StringId.MarkPathTwin
            MarkPathName.NONE -> StringId.MarkPathNone
        },
    )

    fun markTerminusName(terminus: MarkTerminusName): TextRes = message(
        when (terminus) {
            MarkTerminusName.DOT -> StringId.MarkTerminusDot
            MarkTerminusName.RING -> StringId.MarkTerminusRing
            MarkTerminusName.NONE -> StringId.MarkTerminusNone
        },
    )

    fun markComposeFoot(): TextRes = message(StringId.MarkComposeFoot)
}

// **Which of the two the catalogue is being asked about**, and it is this module's own enum rather
// than `:protocol`'s `AuthProvider` for the reason `:client:design:text` depends on nothing: a table
// of words has no business on the wire contract's compile classpath, and the day a third issuer
// exists the two lists move for different reasons. `:client:auth:presentation` is where they meet.
enum class AuthProviderName {

    APPLE,
    GOOGLE,
}

// The four rows of the deletion face. An enum rather than four functions because the face draws them
// as a list and a list wants a key, and because a fifth kind of thing an account holds must not be
// addable without the label being written.
enum class DeleteFactKind {

    COLONY,
    FLEET,
    MAP,
    RESEARCH,
}

// **The four families of drawing the identity editor has to name**, and they are this module's own
// enums rather than `:protocol`'s `MarkPreset`, `MarkBody`, `MarkPath` and `MarkTerminus` for exactly
// the reason `AuthProviderName` is not `AuthProvider`: a table of words has no business on the wire
// contract's compile classpath, and `:client:design:text` declares one production dependency on
// purpose.
//
// The gate's "the day a third issuer exists the two lists move for different reasons" is not a
// hypothetical here — it is already the *expected* shape of a mark set. A preset retired from the
// picker is a word this catalogue keeps saying, because accounts that chose it still wear it and the
// wire still serves it; a preset the design renames is a word that moves with no wire change at all.
// Two lists that move on different days are two lists. `:client:profile:presentation` is where they
// meet, as `:client:auth:presentation` is for the other pair.
enum class MarkPresetName {

    THRESHOLD,
    TERMINATOR,
    APHELION,
    SEXTANT,
    WAKE,
    SOUNDING,
}

enum class MarkBodyName {

    LIMB,
    TERMINATOR,
    ORBIT,
    WAKE,
}

enum class MarkPathName {

    RISING,
    TRANSFER,
    TWIN,
    NONE,
}

enum class MarkTerminusName {

    DOT,
    RING,
    NONE,
}

private const val SECONDS_PER_MINUTE: Int = 60

// `internal`, so the only route to a `Message` is a named entry above. Everything the catalogue can
// say is therefore a function somebody wrote a signature for.
internal fun message(id: StringId, vararg args: Arg): TextRes = TextRes.Message(id, args.toList())
