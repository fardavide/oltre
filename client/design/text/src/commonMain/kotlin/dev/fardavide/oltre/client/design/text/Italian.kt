package dev.fardavide.oltre.client.design.text

// **The second language, and the first proof that the catalogue is one.** Until this file `English`
// was the only table there was, so nothing distinguished a translated string from a literal that had
// merely been moved — the pseudo-locale suite could show that a string had *escaped*, and only a real
// second language can show that an entry was written in the wrong shape to be translated at all.
//
// Three of #87's five grammar rows are answered by a constant swapped in a helper below — the
// grouping separator, the decimal separator, the day's unit letter. The other two needed more, and
// both are worth reading before changing anything here:
//
// - **The conjunction is not a table entry.** `e` becomes `ed` before a word starting with `e`, and a
//   separator is resolved without seeing what follows it. See the `resolve` override.
// - **Gender is not a rule this file applies; it is the shape the entries are written in.** `Miniera`
//   is feminine and `Sintetizzatore` is masculine, so no message here composes an article with a name
//   it was handed. Where English writes "Your next ${facility} takes", Italian puts a fixed head noun
//   in front — *livello*, *ricerca*, *costruzione* — and hangs the name off `di`, so the article
//   agrees with a word the entry chose and therefore knows. `SheetNextBuildTakes` and
//   `SheetMultipliesSupply` are the first two that needed it and the ones the rest follow.
//
// Davide's calls, 2026-08-19: **the six facilities, four technologies and four hulls get Italian
// names** — they are descriptive common nouns, and leaving them English would put untranslated words
// inside translated sentences — and **durations keep `h` and `m` and swap `d` for `g`**, because the
// first two read the same in Italian and the third is simply English.
//
// The register is the informal *tu* throughout, which is what English's "your colony" already is.
object Italian : Translations {

    // **`e` before a consonant, `ed` before another `e`.** The euphonic rule modern Italian still
    // keeps — the Crusca dropped it everywhere except before a word starting with `e-`, which in this
    // catalogue is `Estrazione` and will be whatever the next technology is called.
    //
    // It cannot be a table entry, because `ListLastSeparator` is resolved without seeing the term it
    // is about to precede. So the joined branch is overridden for exactly the shape `Strings.listed`
    // builds — recognisable by that separator, and by nothing else joining with it — and every other
    // joined run falls through to the interface's own resolution untouched.
    override fun resolve(text: TextRes): String = when {
        text is TextRes.Joined && text.separator == LIST_CONJUNCTION -> {
            val head = text.parts.dropLast(1).joinToString(separator = resolve(LIST_CONJUNCTION)) { resolve(it) }
            val last = resolve(text.parts.last())
            head + (if (last.startsWith('e', ignoreCase = true)) " ed " else " e ") + last
        }
        else -> super<Translations>.resolve(text)
    }

    override fun resolve(id: StringId, args: List<Arg>): String = when (id) {

        // ── How the game writes numbers, durations and lists ─────────────────────────────────
        StringId.ClauseSeparator -> " · "
        StringId.GroupedNumber -> args.number(0).toString().grouped()
        // The one figure that is not the language's: a true minus sign is the design's, and it is the
        // same glyph in every language this app will ever speak.
        StringId.Signed -> args.number(0).let { if (it < 0) "−${-it}" else "+$it" }
        StringId.Decimal -> args.decimal(0)
        StringId.DurationMinutes -> "${args.number(0)}m"
        StringId.DurationHoursMinutes -> "${args.number(0)}h ${args.number(1).pad2()}m"
        StringId.DurationHours -> "${args.number(0)}h"
        // `g` for *giorni*, and the padding is English's decision rather than English's grammar: the
        // day is never padded and the hour beside it always is, so a column of these stays tabular.
        StringId.DurationDaysHours -> "${args.number(0)}g ${args.number(1).pad2()}h"
        StringId.Countdown -> "${args.number(0).pad2()}:${args.number(1).pad2()}:${args.number(2).pad2()}"
        StringId.WatchedAt -> "→ acquistabile ${args.clock()}"

        // ── Wall-clock instants ──────────────────────────────────────────────────────────────
        //
        // Each of these is a verb or a noun with no gender to agree with, which is deliberate: the
        // five subjects are a facility, a fleet, a probe, a run and a probe again, and three of them
        // are feminine while two are not.
        StringId.DoneAt -> "termina ${args.clock()}"
        StringId.HomeAt -> "rientro ${args.clock()}"
        StringId.LandsAt -> "arriva ${args.clock()}"
        StringId.LandedAt -> "arrivata ${args.clock()}"
        StringId.ProbeLandedAt -> "Sonda arrivata ${args.clock()}"

        // ── The design system's own words ────────────────────────────────────────────────────
        // `LV` abbreviates *livello* as readily as it abbreviates "level", so the badge is unchanged.
        StringId.LevelBadge -> "LV ${args.number(0)}"

        // ── Vocabulary every screen shares ───────────────────────────────────────────────────
        StringId.SentenceSeparator -> " "
        StringId.ResourceNameMetal -> "metallo"
        StringId.ResourceNameCrystal -> "cristallo"
        StringId.ResourceNameDeuterium -> "deuterio"
        StringId.ResourceTitleMetal -> "Metallo"
        StringId.ResourceTitleCrystal -> "Cristallo"
        StringId.ResourceTitleDeuterium -> "Deuterio"
        StringId.PlainNumber -> args.number(0).toString()
        StringId.Percent -> "${args.number(0)}%"
        StringId.PerHour -> "${args.text(0)}/h"
        StringId.PlusPerHour -> "+${args.text(0)}/h"
        StringId.PlusAmount -> "+${args.text(0)}"
        StringId.CoordinateLabel -> "[${args.number(0)}:${args.number(1)}:${args.number(2)}]"
        // No `di` between the figure and the noun, unlike every one of these inside a sentence: this
        // is a chip and a manifest clause, where Italian writes the unit the way a receipt does.
        StringId.AmountOfResource -> "${args.text(0)} ${args.text(1)}"
        StringId.ResourceReading -> "${args.text(0)} ${args.text(1)}"

        // ── Shipyard ─────────────────────────────────────────────────────────────────────────
        StringId.ShipyardHeading -> "SCAFI"
        StringId.ShipyardNotYetBuiltHeading -> "NON ANCORA COSTRUITI"
        StringId.ShipyardNote ->
            "Ogni scafo costa lo stesso, e il cantiere ne costruisce uno alla volta. Un livello di " +
                "Miniera di Metallo rende di più per unità spesa — la flotta si compra perché rende " +
                "nella risorsa che scegli tu, non perché rende di più."
        StringId.HullsInFleet -> args.count(0).let { "$it ${it.plural("scafo", "scafi")}" }
        // **Five places rather than five participles**, and that is the gender rule doing its work
        // rather than an embellishment: this line is drawn on a hull's own card, so its subject is
        // whichever of `scialuppa`, `cargo`, `scorta` and `colono` that card is about — two feminine
        // and two masculine. "6 possedute" would be right on half of them, and a catalogue cannot
        // know which half. A place agrees with nothing.
        //
        // **They are also as short as this could honestly be made, and it is still not short enough**
        // — see `shipyard_six_hulls_slide_over_it`, where the third clause is ellipsised away at
        // 320dp and the English frame beside it has room to spare. `6 totali · 1 in porto · 5 fuori`
        // is 31 characters against English's 25, under a hull name that grew from `Skiff` to
        // `Scialuppa`. It is #38 arriving where #87 said it would, and it wants a compact pool line
        // rather than worse Italian: the participles were 40 characters and the words below are what
        // is left after every preposition that could go, went.
        StringId.ShipsOwned -> "${args.count(0)} totali"
        StringId.ShipsIdle -> "${args.count(0)} in porto"
        StringId.ShipsAway -> "${args.count(0)} fuori"
        StringId.ShipsBuilding -> "${args.count(0)} in corso"
        StringId.ShipsQueued -> "${args.count(0)} in coda"
        StringId.Build -> "Costruisci"
        StringId.AvailableIn -> "tra ${args.text(0)}"
        StringId.AvailableNever -> "—"
        StringId.ProbeNeedsScout -> "serve un esploratore"
        StringId.ScoutName -> "Esploratore"
        StringId.ScoutPurpose -> "Nessuna stiva · l'unico scafo che può esplorare"
        StringId.SkiffName -> "Scialuppa"
        StringId.SkiffPurpose -> "Una stiva di carico · velocità piena"
        StringId.HaulerName -> "Cargo"
        StringId.HaulerPurpose -> "Quattro stive di carico, a metà della velocità di una scialuppa."

        // ── The dispatch sheet ───────────────────────────────────────────────────────────────
        StringId.Hazards -> when (args.count(0)) {
            0 -> "nessun pericolo"
            1 -> "un pericolo"
            else -> "due pericoli"
        }
        StringId.HazardsAtDistance -> "${args.text(0)}, ${args.text(1)}"
        StringId.ChartedUnsurveyed -> "mappato · non rilevato"
        StringId.DispatchUnsurveyedTitle ->
            "Non si può valutare una stiva su un mondo che nessuno ha guardato."
        StringId.DispatchUnsurveyedNote ->
            "Ricchezza e pericoli vogliono un rilevamento. Una sonda rileva in una volta tutti e " +
                "${args.number(0)} gli slot, e questo sistema contiene ${args.count(1)} " +
                "${args.count(1).plural("mondo", "mondi")}."
        StringId.DispatchProbeOffer -> "${args.text(0)} metallo · ${args.text(1)}."
        StringId.DispatchEverySkiffAwayTitle -> "Ogni scialuppa è in missione."
        StringId.DispatchAwayNote ->
            "${args.count(0)} ${args.count(0).plural("corsa è", "corse sono")} in volo. " +
                "${args.text(1)} sta rientrando con ${args.text(2)} di ${args.text(3)}."
        StringId.DispatchAwayMore -> "Altre ${args.count(0)} dietro."
        StringId.DispatchAwayTail -> "Uno scafo è in porto solo quando è rientrato."
        StringId.DispatchNothingIdle -> "Niente è in porto e niente è in missione."
        StringId.DepositFull -> "giacimento pieno"
        StringId.DepositEmpty -> "giacimento vuoto"
        StringId.DepositStock -> "giacimento ${args.text(0)}/${args.text(1)}"
        StringId.Richness -> "ricchezza ${args.text(0)}"
        StringId.SkiffCount -> args.count(0).let { "$it ${it.plural("scialuppa", "scialuppe")}" }
        StringId.BerthCount -> args.count(0).let { "$it ${it.plural("stiva", "stive")}" }
        StringId.PoolIdle -> "${args.text(0)} in porto"
        StringId.ManifestPair -> "${args.text(0)} · ${args.text(1)}"
        StringId.OutAndBack -> "${args.text(0)} andata e ritorno"
        StringId.RungRequiresSkiffs -> "scialuppe"
        StringId.LadderRungMoved -> "Il cargo ha spostato questa corsa a ${args.text(0)}, la finestra più breve in cui entra."
        StringId.LadderShortestFit -> "${args.text(0)} è la finestra più breve in cui entra il cargo."
        StringId.CellCounterfactual -> "Le scialuppe sollevano solo ${args.text(0)}, e solo loro volano in ${args.text(1)}."
        StringId.CellRungConsequence -> "Il cargo solleva ${args.text(0)} e atterra a ${args.text(1)}."
        StringId.CellClamped -> "Il cargo la svuota. Le ${args.text(0)} non portano nulla."
        StringId.OfIdle -> "di ${args.count(0)} in porto"
        StringId.LadderNote ->
            "${args.text(0)} andata e ritorno. Nessuna finestra più corta lascia " +
                "${args.number(1)} minuti sulla superficie."
        StringId.RungNote -> "La finestra da ${args.text(0)} porta lo stesso."
        // "lo" is the deposit — *il giacimento* — which is masculine in every sentence on this sheet,
        // so this one pronoun is safe where an article on a hull's name would not be.
        StringId.ClampSubject ->
            args.count(0).let { "$it ${it.plural("scialuppa lo svuota", "scialuppe lo svuotano")}." }
        StringId.ClampRestOrdinal -> "La ${args.number(0).ordinal()} non porta nulla."
        StringId.ClampRestOthers -> args.count(0).let {
            if (it == 1) "L'altra non porta nulla." else "Le altre $it non portano nulla."
        }
        StringId.TheWholeDeposit -> "l'intero giacimento"
        StringId.EachShip -> "${args.text(0)} ciascuna"
        StringId.LegOut -> "andata ${args.text(0)}"
        StringId.LegOnStation -> "in stazione ${args.text(0)}"
        StringId.LegStation -> "stazione ${args.text(0)}"
        StringId.LegWorking -> "lavoro ${args.text(0)}"
        StringId.LegHome -> "ritorno ${args.text(0)}"
        StringId.DangerLevel -> "pericolo ${args.number(0)}"
        StringId.DangerNothingAdded -> "non aggiunge nulla"
        StringId.DangerBonus -> "+${args.number(0)}% della stiva"
        StringId.YourOwnSystem -> "il tuo sistema"
        StringId.YourOwnSystemCapitalised -> "Il tuo sistema"
        StringId.AnotherGalaxy -> "un'altra galassia"
        StringId.UnitsOut -> "${args.text(0)} unità di distanza"
        StringId.BothDepositsEmpty -> "Tutti e due i giacimenti sono vuoti."
        StringId.ThisDepositEmpty -> "Questo giacimento è vuoto."
        // **Second person, and that is agreement being designed around rather than applied.** The
        // manifest arrives as text — `1 scialuppa` or `3 scialuppe` — so a verb agreeing with it
        // would have to know a number this entry was not given, and "1 scialuppa solleverebbero" is
        // what happens when it guesses. English is invariant here and gets away with it; Italian
        // makes the player the subject, which is the register the rest of this sheet already uses.
        StringId.WaitingAsk ->
            "Con ${args.text(0)} a ${args.text(1)} solleveresti ${args.text(2)} di ${args.text(3)}."
        StringId.WaitingHoldsAgain -> "Il mondo ne contiene altrettanto tra ${args.text(0)}."
        StringId.WaitingNeverHolds -> "Nessun mondo di queste dimensioni ne contiene mai così tanto."
        StringId.WaitingRemedy -> "Meno scialuppe, o una finestra più corta, arriva prima."
        StringId.ControlBringBack -> "RIPORTA"
        StringId.ControlSend -> "INVIA"
        StringId.ControlHomeIn -> "RIENTRO TRA"
        StringId.DispatchVerb -> "Invia"

        // ── What the game's things are called ────────────────────────────────────────────────
        //
        // **Where the width problem starts.** Italian runs 15–30% longer than English and these are
        // the names every screen repeats, so the compact forms below do far more work here than they
        // do in `English` — five of the six shorten there where only one of them does in English.
        StringId.BuildingNameMetalMine -> "Miniera di Metallo"
        StringId.BuildingNameCrystalMine -> "Miniera di Cristallo"
        // Abbreviated in the full form too, exactly as English abbreviates "Deuterium Synth.":
        // "Sintetizzatore di Deuterio" is 26 characters against English's 22 and does not fit the
        // name column at any width the app is drawn at.
        StringId.BuildingNameDeuteriumSynthesizer -> "Sintetizz. Deuterio"
        StringId.BuildingNameSolarPlant -> "Centrale Solare"
        StringId.BuildingNameRoboticsFactory -> "Fabbrica Robotica"
        StringId.BuildingNameNaniteFactory -> "Fabbrica di Naniti"
        StringId.BuildingCompactNameMetalMine -> "Miniera Metallo"
        StringId.BuildingCompactNameCrystalMine -> "Miniera Cristallo"
        StringId.BuildingCompactNameDeuteriumSynthesizer -> "Sintetizz. Deut."
        StringId.BuildingCompactNameSolarPlant -> "Centrale Solare"
        StringId.BuildingCompactNameRoboticsFactory -> "Robotica"
        StringId.BuildingCompactNameNaniteFactory -> "Naniti"
        StringId.ShipNameScout -> "esploratore"
        StringId.ShipNameSkiff -> "scialuppa"
        StringId.ShipNameHauler -> "cargo"
        StringId.ShipNameEscort -> "scorta"
        StringId.ShipNameSettler -> "colono"
        // **English leaves this noun singular and Italian cannot**: a tally is still a number and a
        // noun, and "3 scialuppa" is not a phrase. The plural it needs is already in the table — in
        // Italian a tally of a type says exactly what a count of them says — so this entry *is* that
        // entry rather than a second copy of four nouns.
        StringId.ShipsOfType -> resolve(args.shipCountId(1), listOf(args[0]))

        // ── The Colony tab ───────────────────────────────────────────────────────────────────
        StringId.ColonyFacilitiesHeading -> "STRUTTURE"
        StringId.PowerHeading -> "ENERGIA"
        StringId.EnergyEveryMineStopped -> "ogni miniera è ferma"
        StringId.EnergyEveryMineAt -> "ogni miniera al ${args.number(0)}%"
        StringId.EnergyBreakEven -> "in pari"
        StringId.EnergyRoomForMineLevels -> args.count(0).let {
            "spazio per $it ${it.plural("livello di miniera", "livelli di miniera")}"
        }
        StringId.EnergyProduced -> "${args.text(0)} prodotta"
        StringId.EnergyDrawn -> "${args.text(0)} assorbita"
        StringId.EnergyShort -> "${args.text(0)} in meno"
        StringId.EnergySpare -> "${args.text(0)} in avanzo"
        StringId.OnStationAt -> "In stazione su ${args.text(0)}"
        StringId.FleetReturning -> "Flotta in rientro"
        StringId.FromTarget -> "da ${args.text(0)}"
        // *Nave* is feminine and is the word Italian reaches for when it means ships in general, so
        // the agreement here is with the category rather than with any one hull's name.
        StringId.MoreAway -> "altre ${args.count(0)} in missione"
        StringId.PowerSupply -> "+${args.text(0)}"
        StringId.PowerDraw -> "−${args.text(0)}"
        StringId.SolarFix -> "→ LV ${args.number(0)} copre tutti i ${args.text(1)} assorbiti"
        StringId.OutputGain -> "+${args.text(0)}/h ${args.text(1)}"
        StringId.BackIn -> "rientri in ${args.text(0)}"
        StringId.SuppliesMore -> "+${args.text(0)} fornitura"
        StringId.DrawAlreadyCovered -> "assorbimento già coperto"
        StringId.ThrottlesEveryMine -> "rallenta ogni miniera"
        StringId.SolarPlantCovers -> "Centrale Solare ${args.number(0)} lo copre"
        StringId.SavedPerBuild -> "−${args.text(0)} per costruzione"
        StringId.GateClause -> "LV ${args.number(0)} → ${args.text(1)}"
        StringId.GateSummaryNanite -> "Naniti"
        StringId.GateSummaryAdaptationShort -> "adattamento"
        StringId.GateSummaryAdaptationLong -> "le tre scale di adattamento"
        StringId.GateSummaryResearchShort -> "ricerca"
        StringId.GateSummaryResearchLong -> "ricerca applicata"
        StringId.GateFacilityLong -> "${args.text(0)} · ${args.text(1)} metallo"
        StringId.LadderStepHeld -> "${args.text(0)} · ce l'hai"
        StringId.NaniteReliefLong ->
            "Una costruzione da ${args.text(0)} richiede ${args.text(1)} al LV ${args.number(2)}"
        StringId.NaniteReliefShort ->
            "Costruzioni da ${args.text(0)} richiedono ${args.text(1)} al LV ${args.number(2)}"
        StringId.RequiresRobotics -> "Richiede Robotica ${args.number(0)}"
        StringId.BecomesLevel -> "→ LV ${args.number(0)}"
        StringId.UpgradeVerb -> "Migliora"
        StringId.PointerLevelStep -> "LV ${args.number(0)} → ${args.number(1)} · ${args.text(2)}"
        StringId.PointerBestBuy -> "LV ${args.number(0)} · rientri in ${args.text(1)}"

        // ── The Colony sheet's prose ─────────────────────────────────────────────────────────
        //
        // Fragments, and `StringId` says at length why they are fragments. What #86 could not know
        // and this file can report: **every one of these sentences survives the clause order English
        // fixed.** Italian puts its subject, verb and complement where English does; the two figures
        // land in the two places the component picks out, and nothing here wanted reordering.
        //
        // That is a finding rather than a guarantee. A language that fronts its complement would need
        // `resolve` to return spans, which is a change to the interface every surface implements.
        StringId.SheetMineMakes -> "La tua colonia produce "
        StringId.SheetMineAtLevel -> " di ${args.text(0)}. Al LV ${args.number(1)} ne produce "
        StringId.SheetFullStop -> "."
        StringId.SheetPlantsSupply -> "Le tue centrali forniscono "
        StringId.SheetColonyDraws -> " di energia. La colonia ne assorbe "
        StringId.SheetSoEveryMineAt -> ", quindi ogni miniera lavora al "
        StringId.SheetThisLevelLifts -> "Questo livello lo alza, ed è per questo che si legge come "
        StringId.SheetRatherThanEnergy -> " di ${args.text(0)} invece che come energia."
        StringId.SheetPaybackPrefix -> "Contato su tutto quello che il livello costa, rientri dopo "
        StringId.SheetSupplyNotLimiting -> "Non è la fornitura a limitarti, quindi un livello che aggiunge "
        StringId.SheetChangesNoRate -> " non cambia nessun ritmo."
        StringId.SheetPaysNextMineLevel -> "Inizia a rendere con il prossimo livello di miniera che prendi."
        StringId.SheetPaysWhenDrawPasses ->
            "Inizia a rendere quando l'assorbimento supera la fornitura — circa "
        StringId.SheetMoreMineLevelAway -> " livello di miniera più in là."
        StringId.SheetMoreMineLevelsAway -> " livelli di miniera più in là."
        // *un*, not *uno*: the noun it counts is `livello`, and this entry is the reason the figure
        // is spelled at all — "1 livello di miniera più in là" reads as a measurement where the
        // sentence is an estimate.
        StringId.SheetOneSpelled -> "un"
        StringId.SheetCannotPowerLevel ->
            "La colonia non può alimentare questo livello. Prenderlo rallenterebbe ogni miniera che " +
                "hai invece di alzare qualcosa."
        StringId.SheetPlantCarriesPrefix -> "Una Centrale Solare al LV "
        StringId.SheetPlantCarriesSuffix ->
            " regge il nuovo assorbimento. Costruisci prima quella e questo livello diventa quello " +
                "che sembra."
        StringId.SheetShortensDeepBuild ->
            "Smonta le attese del gioco avanzato. È l'unica cosa nel gioco che accorcia una " +
                "costruzione profonda."
        StringId.SheetShortensEveryBuild ->
            "Accorcia ogni costruzione su questa colonia e ogni ricerca nell'impero. " +
                "Non alza nessuna produzione sua."
        // **The first message that needed the head-noun rule**, and the one the rest of the file
        // follows. English writes "Your next ${facility} takes", where the article belongs to a name
        // the entry was handed; Italian cannot, because `Miniera` is feminine and `Sintetizzatore` is
        // not. `livello` is masculine, it is chosen here, and the name hangs off it with `di`.
        StringId.SheetNextBuildTakes -> "Il prossimo livello di ${args.text(0)} richiede "
        StringId.SheetAtBuildingLevelTakes -> ". Con ${args.text(0)} ${args.number(1)} richiede "
        StringId.SheetNaniteMineTakes -> "Una Miniera di Metallo di livello ${args.number(0)} richiede "
        StringId.SheetNaniteUnaidedAt -> " senza aiuto. Con ${args.number(0)} livelli di Naniti richiede "
        StringId.SheetRoboticsIsAt -> "La tua Fabbrica Robotica è a "
        StringId.SheetLevelsToGo -> args.count(0).let {
            ". ${if (it == 1) "Manca un livello" else "Mancano $it livelli"}, e il primo livello di Naniti costa "
        }
        StringId.SheetMetalSuffix -> " di metallo."

        // ── The Research tab ─────────────────────────────────────────────────────────────────
        StringId.TechnologyNamePhotovoltaics -> "Fotovoltaico"
        StringId.TechnologyNameExtraction -> "Estrazione"
        StringId.TechnologyNameEnrichment -> "Arricchimento"
        StringId.TechnologyNameProspecting -> "Prospezione"
        StringId.TechnologyNamePropulsion -> "Propulsione"
        // *Adattamento* is masculine, so all three agree with it and none of them has to be told so.
        StringId.AdaptationNameThermal -> "Termico"
        StringId.AdaptationNameGravitic -> "Gravitico"
        StringId.AdaptationNameAtmospheric -> "Atmosferico"
        StringId.TechnologySubjectPhotovoltaics -> "produzione Centrale Solare"
        StringId.TechnologySubjectExtraction -> "produzione metallo · cristallo"
        StringId.TechnologySubjectEnrichment -> "produzione deuterio"
        // PLACEHOLDER in English, and therefore placeholder here: this is the one row whose noun
        // Claude Design left to Davide, so the Italian is a translation of a string that is itself
        // still open.
        StringId.TechnologySubjectProspecting -> "quanto solleva una flotta"
        // Placeholder in English and therefore placeholder here, exactly as the row above.
        StringId.TechnologySubjectPropulsion -> "velocità della flotta"
        StringId.AdaptationUnitThermal -> "°C"
        StringId.AdaptationUnitGravitic -> "g"
        StringId.AdaptationUnitAtmospheric -> "atm"
        StringId.BuildingShortNameMetalMine -> "Miniera Metallo"
        StringId.BuildingShortNameCrystalMine -> "Miniera Cristallo"
        StringId.BuildingShortNameDeuteriumSynthesizer -> "Deuterio"
        StringId.BuildingShortNameSolarPlant -> "Centrale Solare"
        StringId.BuildingShortNameRoboticsFactory -> "Robotica"
        StringId.BuildingShortNameNaniteFactory -> "Naniti"
        StringId.ResearchHeading -> "TECNOLOGIE"
        StringId.AdaptationHeading -> "ADATTAMENTO"
        StringId.RuleOneProjectAtATime -> "un progetto alla volta"
        StringId.RuleOneLadderAtATime -> "una scala alla volta"
        StringId.RuleOneAtATime -> "uno alla volta"
        StringId.ResearchVerb -> "Ricerca"
        StringId.VerdictNothingThrottled -> "niente finché le tue miniere sono rallentate"
        StringId.VerdictNothingThrottledCompact -> "niente se rallentate"
        StringId.VerdictNothingSurplus -> "niente finché sei in avanzo"
        StringId.VerdictNothingSurplusCompact -> "niente se in avanzo"
        StringId.HaulGain -> "+${args.text(0)} per scafo ogni ora in stazione"
        StringId.HaulGainCompact -> "+${args.text(0)} per scafo ogni ora"
        StringId.ReachGain -> "la galassia accanto in ${args.text(0)}, da ${args.text(1)}"
        StringId.ReachGainCompact -> "galassia accanto ${args.text(0)}"
        StringId.PlusPercent -> "+${args.number(0)}%"
        StringId.ToleranceBand -> "${args.text(0)} … ${args.text(1)}"
        StringId.Requires -> "Richiede ${args.text(0)}"
        StringId.NamedLevel -> "${args.text(0)} ${args.number(1)}"
        StringId.ShortlistNothingVerb -> "Non sblocca nulla che hai rilevato"
        StringId.ShortlistNothing -> "Nulla che hai rilevato"
        StringId.ShortlistVerb -> "Sblocca ${args.shortlistCounts()}"
        StringId.Shortlist -> args.shortlistCounts()

        // ── The Research sheet's prose ───────────────────────────────────────────────────────
        StringId.SheetSubjectPrefix -> "${args.text(0)}: "
        StringId.SheetArrow -> " → "
        StringId.SheetAtLevelOne -> " al LV 1."
        StringId.SheetToleranceSubject -> "tolleranza ${args.text(0)}: "
        StringId.SheetReachesNothing ->
            "Niente di quello che hai rilevato è bloccato solo da questa banda, quindi questo " +
                "livello non raggiunge nessun mondo nuovo."
        StringId.SheetReachesPrefix -> "Dei mondi che hai rilevato questo livello ne raggiunge "
        StringId.SheetReachesMiddle -> ", e di questi "
        StringId.SheetReachesSuffix -> " valgono la pena."
        StringId.SheetAndWouldMake -> " di ${args.text(0)} e ne produrrebbe "
        StringId.SheetEachHullLifts -> "Ogni scafo solleva "
        StringId.SheetAnHourOnStation -> " all'ora in stazione e ne solleverebbe "
        StringId.SheetPaysOnNextRun -> "Rende alla prossima corsa invece che su un orologio a casa."
        StringId.SheetTheNextGalaxyIs -> "La galassia accanto dista "
        StringId.SheetAwayAndWouldBe -> " e disterebbe "
        StringId.SheetPaysTheFurtherYouAim -> "Non compra nulla qui accanto e tutto alla frontiera."
        StringId.SheetColonyDrawsAnd -> " di energia e la colonia ne assorbe "
        // The head-noun rule again, on a technology this time: *ricerca* is feminine, it is chosen
        // here, and `Fotovoltaico` hangs off it without an article of its own.
        StringId.SheetAtThatRatio -> "A quel rapporto la ricerca ${args.text(0)} guadagna "
        StringId.SheetRoundsAway -> " che si perde negli arrotondamenti prima di arrivare ai tuoi depositi."
        StringId.SheetPaysWhenPlantsCarry ->
            "Inizia a rendere quando le tue centrali reggono di nuovo l'assorbimento."
        StringId.SheetMultipliesSupply ->
            "La ricerca ${args.text(0)} moltiplica la fornitura, e non è la fornitura a limitarti. A "
        StringId.SheetOutputDoesNotMove -> " la tua produzione non si muove."
        StringId.SheetRequiresPrefix -> "Richiede "

        // ── The Fleets tab ───────────────────────────────────────────────────────────────────
        //
        // Four entries and only three of them inflect: *cargo* is a loanword and Italian leaves those
        // alone, so "3 cargo" is the plural. That is the reason a plural is a property of the noun
        // rather than a rule the language applies to all of them.
        StringId.ShipsScout -> args.count(0).let { "$it ${it.plural("esploratore", "esploratori")}" }
        StringId.ShipsSkiff -> args.count(0).let { "$it ${it.plural("scialuppa", "scialuppe")}" }
        StringId.ShipsHauler -> "${args.count(0)} cargo"
        StringId.ShipsEscort -> args.count(0).let { "$it ${it.plural("scorta", "scorte")}" }
        StringId.ShipsSettler -> args.count(0).let { "$it ${it.plural("colono", "coloni")}" }
        StringId.FleetsHeading -> "IN VOLO"
        StringId.FleetsAwayOf -> "${args.number(0)} su ${args.number(1)} in missione"
        StringId.FleetsNothingOut ->
            "Niente è in missione. Una corsa parte da un mondo nella scheda Galassia."
        StringId.WorkedHeading -> "MONDI LAVORATI"
        StringId.WorkedNewestFirst -> "dal più recente"
        StringId.RunCount -> args.count(0).let { "$it ${it.plural("corsa", "corse")}" }
        StringId.UnrecordedRuns -> args.count(0).let {
            "$it ${it.plural("corsa precedente", "corse precedenti")} · ${args.text(1)} " +
                "${args.text(2)} · nessun obiettivo registrato"
        }
        StringId.DepositFullWord -> "pieno"
        StringId.DepositEmptyWord -> "vuoto"
        StringId.WorldRowPrefix -> "${args.text(0)} · ${args.text(1)}"
        StringId.WorldRowSeparator -> "${args.text(0)} · "

        // ── The Galaxy tab ───────────────────────────────────────────────────────────────────
        StringId.GalaxyLabel -> "G${args.number(0)}"
        StringId.GalaxyNamed -> "Galassia ${args.number(0)}"
        StringId.GalaxiesCount -> args.count(0).let { "$it ${it.plural("galassia", "galassie")}" }
        StringId.SystemsCount -> "${args.text(0)} sistemi"
        StringId.SurveyedCount -> "${args.count(0)} rilevati"
        StringId.PinnedCount -> "${args.count(0)} fissati"
        StringId.NothingCharted -> "niente mappato"
        StringId.SystemAddressLabel -> "[${args.number(0)}:${args.number(1)}]"
        StringId.RelayEffect -> "Relè · conteso · +18% di portata mentre lo tieni"
        StringId.DangerFromHere -> "pericolo ${args.number(0)} da qui"
        StringId.ReachSingle -> "${args.text(0)} andata e ritorno"
        StringId.ReachRange -> "${args.text(0)}–${args.text(1)} andata e ritorno"
        // The near end loses its unit because the far end carries one, exactly as it does in English
        // — and for the same reason, it only works while both ends are minutes.
        StringId.ReachRangeMinutes -> "${args.number(0)}–${args.text(1)} andata e ritorno"
        StringId.StarClassDim -> "debole"
        StringId.StarClassStandard -> "normale"
        StringId.StarClassBright -> "brillante"
        StringId.StarDetail -> args.count(1).let { "${args.text(0)} · $it ${it.plural("mondo", "mondi")}" }
        StringId.StarDetailCompact -> "${args.text(0)} · ${args.count(1)}"
        StringId.WorldCount -> args.count(0).let {
            if (it == 0) "nessun mondo" else "$it ${it.plural("mondo", "mondi")}"
        }
        StringId.WorldsSurveyedCount ->
            args.count(0).let { "$it ${it.plural("mondo rilevato", "mondi rilevati")}" }
        StringId.NoWorlds -> "nessun mondo"
        StringId.HomeNote -> "casa"
        StringId.ProbeLandsIn -> "la sonda arriva tra ${args.text(0)}"
        StringId.ProbeFlight -> "sonda ${args.text(0)}"
        StringId.ProbeFlightLabel -> "volo ${args.text(0)}"
        StringId.RunFlight -> "corsa ${args.text(0)}"
        StringId.NothingToSurvey -> "${args.number(0)} slot vuoti · niente da rilevare"
        StringId.SurveyedAtGenesis -> "Rilevato alla genesi"
        StringId.DispatchProbe -> "Invia sonda"
        StringId.DispatchProbeCompact -> "Invia"
        StringId.FindSettleable -> "${args.count(0)} colonizzabili"
        StringId.FindNearMiss -> "${args.count(0)} bloccati su un asse"
        StringId.FindNone -> "nessuno colonizzabile"
        StringId.TemperatureReading -> "${args.text(0)}$UNIT_GAP°C"
        StringId.GravityReading -> "${args.text(0)}${UNIT_GAP}g"
        StringId.PressureReading -> "${args.text(0)}${UNIT_GAP}atm"
        StringId.FoundAgo -> "trovato ${args.text(0)} fa"
        StringId.AxisTemperature -> "temperatura"
        StringId.AxisGravity -> "gravità"
        StringId.AxisPressure -> "pressione"
        // **Noun first, and the adjective agrees with it** — which is the whole reason `WorldEpithet`
        // is one entry over two arguments rather than a joined pair. English writes "veiled furnace";
        // Italian writes "fornace velata", and would write "gelo ferreo" for the same adjective
        // against a masculine noun.
        //
        // The agreement is a rule rather than a second column of words: Italian's first-class
        // adjectives end `-o` in the masculine and `-a` in the feminine, and its second class ends
        // `-e` and does not decline at all. So the table holds the masculine — the form a dictionary
        // holds — and `feminised` applies the rule. `fragile` and `irrespirabile` pass through it
        // untouched, which is the rule working rather than an exception to it.
        StringId.WorldEpithet ->
            "${args.text(1)} ${args.text(0).feminised(args.epithetNounIsFeminine(1))}"
        StringId.EpithetNounFurnace -> "fornace"
        StringId.EpithetNounFrost -> "gelo"
        StringId.EpithetNounGiant -> "gigante"
        StringId.EpithetNounHusk -> "guscio"
        // *Coltre* rather than *sudario*: a shroud here is a smothering blanket of atmosphere, not a
        // burial cloth, and the world it names is the one you cannot breathe on rather than a grave.
        StringId.EpithetNounShroud -> "coltre"
        StringId.EpithetNounWaste -> "landa"
        StringId.EpithetNounWorld -> "mondo"
        StringId.EpithetAdjectiveScorched -> "riarso"
        StringId.EpithetAdjectiveFrozen -> "ghiacciato"
        StringId.EpithetAdjectiveIron -> "ferreo"
        StringId.EpithetAdjectiveHollow -> "cavo"
        StringId.EpithetAdjectiveVeiled -> "velato"
        StringId.EpithetAdjectiveAirless -> "irrespirabile"
        StringId.EpithetAdjectiveAshen -> "cinereo"
        StringId.EpithetAdjectiveDeep -> "profondo"
        StringId.EpithetAdjectiveBrittle -> "fragile"
        StringId.EpithetAdjectiveDrowned -> "sommerso"
        StringId.EpithetAdjectiveBare -> "spoglio"
        StringId.EpithetAdjectiveTemperate -> "temperato"

        // Every one of these describes a world, and *mondo* is masculine — which is what makes the
        // participles below safe where the same word next to a hull's name would not be.
        StringId.NoteHome -> "La tua colonia."
        StringId.NoteOccupied -> "Occupato da ${args.text(0)}."
        StringId.NoteSettleable -> "Niente qui blocca una colonia."
        StringId.NoteBarren -> "Resa ${args.text(0)}, ne vale la pena a ${args.text(1)}"
        StringId.NoteBarrenDiscovery -> "Passa ogni banda, e non vale la pena prenderlo."
        StringId.NoteBlocked -> "Bloccato."
        StringId.NoteWouldLandIt -> "${args.text(0)} ${args.number(1)} lo sbloccherebbe."
        StringId.NoteSurveyed -> "Rilevato."
        StringId.WorthItAt -> "ne vale la pena a ${args.text(0)}"
        StringId.DepositFraction -> "${args.text(0)}/${args.text(1)}"
        StringId.LedgerEmptyHeadline -> "Ogni mondo che una sonda raggiunge finisce qui."
        StringId.LedgerEmptyDetail -> "Non hai ancora rilevato niente."
        StringId.LedgerNoMatchHeadline -> "Nessun mondo che conosci si chiama così."
        StringId.LedgerNoMatchDetail ->
            "I nomi sono unici in una galassia, quindi un nome completo trova un posto solo."
        StringId.VerdictWordHome -> "Casa"
        StringId.VerdictWordOccupied -> "Occupato"
        StringId.VerdictWordBlocked -> "Bloccato"
        StringId.VerdictWordBarren -> "Arido"
        StringId.VerdictWordSettleable -> "Colonizzabile"
        StringId.DiscoveriesHeadingOne -> "RILEVATO"
        StringId.DiscoveriesHeadingMany -> "${args.count(0)} MONDI RILEVATI"
        StringId.PinnedHeading -> "FISSATI"
        StringId.RelayLabel -> "RELÈ"
        StringId.LedgerModeWorlds -> "mondi"
        StringId.LedgerModeMap -> "mappa"
        StringId.SearchPlaceholder -> "nome"
        StringId.BlockedAxisLine -> "${args.text(0)} ${args.text(1)}, tolleri ${args.text(2)}"
        StringId.MiddotStandalone -> "·"
        StringId.OrbitSlot -> "${args.number(0)}"

        // ── The shell ────────────────────────────────────────────────────────────────────────
        StringId.TabColony -> "Colonia"
        StringId.TabResearch -> "Ricerca"
        StringId.TabShipyard -> "Cantiere"
        StringId.TabGalaxy -> "Galassia"
        StringId.TabFleets -> "Flotte"
        StringId.StepperFewer -> "−"
        StringId.StepperMore -> "+"
        StringId.DepositGap -> " "
        StringId.NotificationChannelName -> "Eventi della colonia"
        StringId.NotificationChannelDescription ->
            "Ti dice quando una costruzione o una ricerca è finita, e quando una flotta rientra."
        StringId.Watching -> "osservi ${args.text(0)}"
        StringId.RatePerHour -> "+${args.text(0)}/h"
        StringId.ResourceRailMetal -> "METALLO"
        StringId.ResourceRailCrystal -> "CRISTALLO"
        StringId.ResourceRailDeuterium -> "DEUTERIO"

        // ── Notifications ────────────────────────────────────────────────────────────────────
        StringId.ListSeparator -> ", "
        // The default form. Which of `e` and `ed` actually reaches the screen is decided by the
        // `resolve` override above, because it depends on the word that comes next.
        StringId.ListLastSeparator -> " e "
        StringId.UpgradesDoneTitle -> "${args.count(0).spelled()} miglioramenti sono pronti"
        StringId.UpgradesDoneBody -> "${args.text(0)} — scegli cosa costruisce la tua colonia adesso."
        StringId.ReachedLevel -> "${args.text(0)} ha raggiunto il livello ${args.number(1)}"
        StringId.BuildCompleteBody ->
            "La costruzione è finita — scegli cosa costruisce la tua colonia adesso."
        StringId.LabFreeBody -> "Il laboratorio è libero — scegli cosa ricerca il tuo impero adesso."
        StringId.AdaptationOpenedBody ->
            "Mondi che non potevi colonizzare potrebbero essersi aperti — controlla la galassia."
        // No article on the hull's name, and no participle agreeing with it: a title that opens with
        // the name is idiomatic Italian and it is the only form that reads for all four hulls.
        StringId.HullLeftYardTitle -> "${args.text(0)} ha lasciato il cantiere"
        StringId.HullLeftYardBody -> "Ora fa parte della tua flotta e può partire."
        StringId.ProbeReachedTitle -> "La tua sonda ha raggiunto ${args.text(0)}"
        StringId.ChartedNoneSettleable -> args.count(0).let {
            "$it ${it.plural("mondo mappato", "mondi mappati")}, nessuno colonizzabile."
        }
        StringId.ChartedSettleable -> args.count(0).let {
            "$it ${it.plural("mondo mappato", "mondi mappati")}, ${args.count(1)} " +
                "${args.count(1).plural("colonizzabile", "colonizzabili")}."
        }
        StringId.ShipsHomeTitle -> "Le tue navi sono rientrate"
        StringId.ShipsHomeBody -> "Il carico da ${args.text(0)} è nei tuoi depositi."
        StringId.AffordableTitle -> "Puoi permetterti ${args.text(0)}"
        StringId.AffordableBody -> "La colonia ha le risorse per il livello ${args.number(0)}."
        StringId.SystemAddressBare -> "${args.number(0)}:${args.number(1)}"
        StringId.BuildingFullNameMetalMine -> "Miniera di Metallo"
        StringId.BuildingFullNameCrystalMine -> "Miniera di Cristallo"
        StringId.BuildingFullNameDeuteriumSynthesizer -> "Sintetizzatore di Deuterio"
        StringId.BuildingFullNameSolarPlant -> "Centrale Solare"
        StringId.BuildingFullNameRoboticsFactory -> "Fabbrica Robotica"
        StringId.BuildingFullNameNaniteFactory -> "Fabbrica di Naniti"
        StringId.ShipTitleNameScout -> "Esploratore"
        StringId.ShipTitleNameSkiff -> "Scialuppa"
        StringId.ShipTitleNameHauler -> "Cargo"
        StringId.ShipTitleNameEscort -> "Scorta"
        StringId.ShipTitleNameSettler -> "Colono"
        StringId.AdaptationFullNameThermal -> "Adattamento Termico"
        StringId.AdaptationFullNameGravitic -> "Adattamento Gravitico"
        StringId.AdaptationFullNameAtmospheric -> "Adattamento Atmosferico"
    }

    // ── Reading the arguments back out ───────────────────────────────────────────────────────
    //
    // The same casts `English` makes and safe for the same reason: `Message`'s constructor is
    // internal, so the only things that reach here are the arguments a `Strings` signature named.

    private fun List<Arg>.number(index: Int): Long = (this[index] as Arg.Number).value

    private fun List<Arg>.count(index: Int): Int = (this[index] as Arg.Count).value

    private fun List<Arg>.text(index: Int): String = resolve((this[index] as Arg.Text).value)

    // Which of the five hull nouns a tally was handed. Reading the argument's own id rather than its
    // resolved words is the same safety the casts above rely on: `Strings.shipsOfType` takes a
    // `ShipType`, so what arrives here is always one of these five.
    private fun List<Arg>.shipCountId(index: Int): StringId =
        when (((this[index] as Arg.Text).value as TextRes.Message).id) {
            StringId.ShipNameScout -> StringId.ShipsScout
            StringId.ShipNameSkiff -> StringId.ShipsSkiff
            StringId.ShipNameHauler -> StringId.ShipsHauler
            StringId.ShipNameEscort -> StringId.ShipsEscort
            StringId.ShipNameSettler -> StringId.ShipsSettler
            else -> error("a ship tally was handed something that is not a ship name")
        }

    // Three of the seven epithet nouns are feminine, and there is no reading them off the words: the
    // ones ending `-e` split both ways — `fornace` is feminine and `gigante` is masculine — which is
    // exactly why gender is carried by the entry rather than inferred from the string. Read by id for
    // `shipCountId`'s reason: `Strings.worldEpithet` takes an `EpithetNoun`, so what arrives is one of
    // these seven.
    private fun List<Arg>.epithetNounIsFeminine(index: Int): Boolean =
        when (((this[index] as Arg.Text).value as TextRes.Message).id) {
            StringId.EpithetNounFurnace,
            StringId.EpithetNounShroud,
            StringId.EpithetNounWaste,
            -> true
            else -> false
        }

    // The rule, and it is the whole of Italian adjective agreement for this class: `-o` becomes `-a`,
    // and a word that does not end in `-o` does not decline for gender at all.
    private fun String.feminised(feminine: Boolean): String =
        if (feminine && endsWith('o')) dropLast(1) + "a" else this

    // Italian has English's two forms and picks between them on the same number, so this reads the
    // same. What differs is what the callers hand it: no Italian noun pluralises by adding a letter,
    // and one of them — `cargo` — does not pluralise at all.
    private fun Int.plural(one: String, many: String): String = if (this == 1) one else many

    // Two through eight, which is every group this game can produce. **Invariable, which is the one
    // place Italian is simpler than English here**: `due` through `otto` do not decline, so the
    // gender of what is being counted cannot reach them. Only `uno` would, and a group is two or more
    // by construction.
    private fun Int.spelled(): String = when (this) {
        2 -> "Due"
        3 -> "Tre"
        4 -> "Quattro"
        5 -> "Cinque"
        6 -> "Sei"
        7 -> "Sette"
        else -> "Otto"
    }

    // "5 mondi, 1 da prendere" — the shared tail of both shortlist entries, and the gap between each
    // count and its qualifier is `English`'s to the character. See `UNIT_GAP`.
    private fun List<Arg>.shortlistCounts(): String {
        val worlds = count(0).let { "$it ${it.plural("mondo", "mondi")}" }
        val worth = count(1).let { if (it == 0) "nessuno" else "$it" }
        return "$worlds, $worth${UNIT_GAP}da${UNIT_GAP}prendere"
    }

    // **An ordinary space, and it is meant to be U+00A0** — reproduced from `English` rather than
    // fixed, for exactly the reason stated there: swapping it changes what four screens render and
    // what their baselines photograph, and it is Davide's to call. A second language is not the place
    // to quietly settle a question the first one left open.
    private const val UNIT_GAP: Char = ' '

    // Italian's ordinals are regular where English's are not: one suffix, and it agrees in gender
    // with what it counts. `ª` is feminine because the thing being ordered is a `scialuppa`.
    private fun Long.ordinal(): String = "${this}ª"

    // Every wall-clock entry is the same two padded fields with a different word in front of it.
    private fun List<Arg>.clock(): String = "${number(0).pad2()}:${number(1).pad2()}"

    // Where the language shows most, and the reason `Arg.Decimal` carries an integer rather than a
    // rendered string: the separator is Italian's comma, and a pre-rendered "2.62" would have baked
    // English into the argument before this file ever saw it.
    private fun List<Arg>.decimal(index: Int): String {
        val arg = this[index] as Arg.Decimal
        val magnitude = if (arg.scaled < 0) -arg.scaled else arg.scaled
        val unit = POWERS_OF_TEN[arg.decimals]
        val sign = if (arg.scaled < 0) "−" else ""
        val rendered = "$sign${magnitude / unit}$DECIMAL_SEPARATOR" +
            (magnitude % unit).toString().padStart(arg.decimals, '0')
        return if (arg.trimTrailingZeros) rendered.trimEnd('0').trimEnd(DECIMAL_SEPARATOR) else rendered
    }

    // The other half of the same decision: a language that groups with a point cannot also point its
    // decimals, so these two constants only ever move together.
    private fun String.grouped(): String {
        val negative = startsWith('-')
        val digits = if (negative) drop(1) else this
        val grouped = digits.reversed().chunked(GROUP_SIZE).joinToString(GROUPING_SEPARATOR).reversed()
        return if (negative) "-$grouped" else grouped
    }

    private fun Long.pad2(): String = toString().padStart(2, '0')

    // The conjunction as a value, so the `resolve` override recognises the one joined shape it is
    // about by comparing against the entry itself rather than against the words it resolves to.
    private val LIST_CONJUNCTION: TextRes = message(StringId.ListLastSeparator)

    private const val DECIMAL_SEPARATOR: Char = ','
    private const val GROUPING_SEPARATOR: String = "."
    private const val GROUP_SIZE: Int = 3
    private val POWERS_OF_TEN: List<Long> = listOf(1, 10, 100, 1_000)
}
