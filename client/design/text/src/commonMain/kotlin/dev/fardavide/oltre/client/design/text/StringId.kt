package dev.fardavide.oltre.client.design.text

// Every message the game has. An `enum` rather than a sealed hierarchy for one reason: a `when` over
// it is exhaustive without an `else`, so **adding an entry breaks every language that has not
// translated it** — which is the only mechanism in this design that makes a second locale a
// compile-time obligation rather than a promise.
//
// The ids are grouped by where the words are said, not by what they say. An id is never reused
// across two surfaces even when the English happens to coincide: two screens that say "Build" today
// are two decisions, and a language that needs to distinguish them must be able to.
enum class StringId {

    // ── How the game writes numbers, durations and lists ─────────────────────────────────────
    //
    // `:client:design:format` does the arithmetic and these do the writing. The split is the
    // module's own: rounding a milli-g to two places is the same in every language, and whether the
    // point is a point is not.
    ClauseSeparator,
    GroupedNumber,
    Signed,
    Decimal,
    DurationMinutes,
    DurationHoursMinutes,
    DurationHours,
    DurationDaysHours,
    // **The two shortest waits the game writes, and the gate is the only thing that asks for
    // them.** Everything else in this list starts at a minute, because nothing else in the game
    // finishes sooner. A sign-in throttle does — the server sends a number of seconds — and the
    // design's rule is two units at most, so under a minute prints `41s` and over it prints
    // `4m 12s`.
    DurationSeconds,
    DurationMinutesSeconds,
    Countdown,
    WatchedAt,

    // ── Wall-clock instants, each with the word that says what happens then ──────────────────
    //
    // Five entries where one "HH:MM" would do, because the *word* is what differs and a language
    // may not put it where English does. "done 11:23" is a facility finishing, "home 14:05" is a
    // fleet returning; a shared entry would force both to read the same.
    DoneAt,
    HomeAt,
    LandsAt,
    LandedAt,
    ProbeLandedAt,

    // ── The design system's own words ───────────────────────────────────────────────────────
    LevelBadge,

    // ── Vocabulary every screen shares ──────────────────────────────────────────────────────
    SentenceSeparator,
    ResourceNameMetal,
    ResourceNameCrystal,
    ResourceNameDeuterium,
    ResourceTitleMetal,
    ResourceTitleCrystal,
    ResourceTitleDeuterium,
    PlainNumber,
    Percent,
    PerHour,
    PlusPerHour,
    PlusAmount,
    CoordinateLabel,
    AmountOfResource,
    ResourceReading,

    // ── Shipyard ────────────────────────────────────────────────────────────────────────────
    ShipyardHeading,
    ShipyardNotYetBuiltHeading,
    ShipyardNote,
    HullsInFleet,
    ShipsOwned,
    ShipsIdle,
    ShipsAway,
    ShipsBuilding,
    ShipsQueued,
    Build,
    AvailableIn,
    AvailableNever,
    ProbeNeedsScout,
    ScoutName,
    ScoutPurpose,
    SkiffName,
    SkiffPurpose,
    HaulerName,
    HaulerPurpose,

    // ── The dispatch sheet ──────────────────────────────────────────────────────────────────
    Hazards,
    HazardsAtDistance,
    ChartedUnsurveyed,
    DispatchUnsurveyedTitle,
    DispatchUnsurveyedNote,
    DispatchProbeOffer,
    DispatchEverySkiffAwayTitle,
    DispatchNoGatheringHullTitle,
    DispatchNoGatheringHullNote,
    DispatchOutOfReachTitle,
    DispatchOutOfReachNote,
    DispatchAwayNote,
    DispatchAwayMore,
    DispatchAwayTail,
    DepositFull,
    DepositEmpty,
    DepositStock,
    Richness,
    SkiffCount,
    BerthCount,
    PoolIdle,
    ManifestPair,
    OutAndBack,
    RungRequiresSkiffs,
    LadderRungMoved,
    LadderShortestFit,
    CellCounterfactual,
    CellRungConsequence,
    CellClamped,
    CellClampedOne,
    OfIdle,
    LadderNote,
    RungNote,
    ClampSubject,
    ClampRestOrdinal,
    ClampRestOthers,
    TheWholeDeposit,
    EachShip,
    VeinLeft,
    LegOut,
    LegOnStation,
    LegStation,
    LegWorking,
    LegHome,
    DangerLevel,
    DangerNothingAdded,
    DangerBonus,
    YourOwnSystem,
    YourOwnSystemCapitalised,
    AnotherGalaxy,
    UnitsOut,
    BothDepositsEmpty,
    ThisDepositEmpty,
    WaitingAsk,
    WaitingHoldsAgain,
    WaitingNeverHolds,
    WaitingRemedy,
    ControlBringBack,
    ControlSend,
    ControlHomeIn,
    DispatchVerb,

    // ── What the game's things are called ───────────────────────────────────────────────────
    //
    // Six facilities and four hulls, each with its own id rather than one entry taking the enum,
    // because a name is a word and words are what a catalogue holds. The compact forms are six
    // entries too even though five of them read the same in English: which names a language can
    // shorten is that language's to know, and a `compactName` that fell back to the full one in the
    // table would be English's answer imposed on every other.
    BuildingNameMetalMine,
    BuildingNameCrystalMine,
    BuildingNameDeuteriumSynthesizer,
    BuildingNameSolarPlant,
    BuildingNameRoboticsFactory,
    BuildingNameNaniteFactory,
    BuildingCompactNameMetalMine,
    BuildingCompactNameCrystalMine,
    BuildingCompactNameDeuteriumSynthesizer,
    BuildingCompactNameSolarPlant,
    BuildingCompactNameRoboticsFactory,
    BuildingCompactNameNaniteFactory,
    ShipNameScout,
    ShipNameSkiff,
    ShipNameHauler,
    ShipNameEscort,
    ShipNameSettler,
    ShipsOfType,

    // ── The Colony tab ──────────────────────────────────────────────────────────────────────
    ColonyFacilitiesHeading,
    PowerHeading,
    EnergyEveryMineStopped,
    EnergyEveryMineAt,
    EnergyBreakEven,
    EnergyRoomForMineLevels,
    EnergyProduced,
    EnergyDrawn,
    EnergyShort,
    EnergySpare,
    OnStationAt,
    FleetReturning,
    FromTarget,
    MoreAway,
    PowerSupply,
    PowerDraw,
    SolarFix,
    OutputGain,
    BackIn,
    SuppliesMore,
    DrawAlreadyCovered,
    ThrottlesEveryMine,
    SolarPlantCovers,
    SavedPerBuild,
    GateClause,
    GateSummaryNanite,
    GateSummaryAdaptationShort,
    GateSummaryAdaptationLong,
    GateSummaryResearchShort,
    GateSummaryResearchLong,
    GateFacilityLong,
    LadderStepHeld,
    NaniteReliefLong,
    NaniteReliefShort,
    RequiresRobotics,
    BecomesLevel,
    UpgradeVerb,
    PointerLevelStep,
    PointerBestBuy,

    // ── The Colony sheet's prose ────────────────────────────────────────────────────────────
    //
    // **These are sentence fragments, and that is the one place in the catalogue where a translator
    // will feel the design rather than the words.** A sheet line is a sentence with its *figures
    // picked out* — the numbers are drawn in the body colour so the arithmetic is scannable — and
    // the component owns what "picked out" looks like, so the mapper has to hand it the sentence
    // already broken at the figures. Whole-sentence entries would need `resolve` to return spans
    // rather than a `String`, which is a change to the interface every surface implements, and it
    // would move the one thing #86 promised not to move: the frames.
    //
    // What that costs is real and worth naming: a language that orders these clauses differently
    // cannot reorder them from the table alone. That is the pseudo-locale suite's job to surface
    // and #87's to answer.
    SheetMineMakes,
    SheetMineAtLevel,
    SheetFullStop,
    SheetPlantsSupply,
    SheetColonyDraws,
    SheetSoEveryMineAt,
    SheetThisLevelLifts,
    SheetRatherThanEnergy,
    SheetPaybackPrefix,
    SheetSupplyNotLimiting,
    SheetChangesNoRate,
    SheetPaysNextMineLevel,
    SheetPaysWhenDrawPasses,
    SheetMoreMineLevelAway,
    SheetMoreMineLevelsAway,
    SheetOneSpelled,
    SheetCannotPowerLevel,
    SheetPlantCarriesPrefix,
    SheetPlantCarriesSuffix,
    SheetShortensDeepBuild,
    SheetShortensEveryBuild,
    SheetNextBuildTakes,
    SheetAtBuildingLevelTakes,
    SheetNaniteMineTakes,
    SheetNaniteUnaidedAt,
    SheetRoboticsIsAt,
    SheetLevelsToGo,
    SheetMetalSuffix,

    // ── The Research tab ────────────────────────────────────────────────────────────────────
    TechnologyNamePhotovoltaics,
    TechnologyNameExtraction,
    TechnologyNameEnrichment,
    TechnologyNameProspecting,
    TechnologyNamePropulsion,
    AdaptationNameThermal,
    AdaptationNameGravitic,
    AdaptationNameAtmospheric,
    TechnologySubjectPhotovoltaics,
    TechnologySubjectExtraction,
    TechnologySubjectEnrichment,
    TechnologySubjectProspecting,
    TechnologySubjectPropulsion,
    AdaptationUnitThermal,
    AdaptationUnitGravitic,
    AdaptationUnitAtmospheric,
    BuildingShortNameMetalMine,
    BuildingShortNameCrystalMine,
    BuildingShortNameDeuteriumSynthesizer,
    BuildingShortNameSolarPlant,
    BuildingShortNameRoboticsFactory,
    BuildingShortNameNaniteFactory,
    ResearchHeading,
    AdaptationHeading,
    RuleOneProjectAtATime,
    RuleOneLadderAtATime,
    RuleOneAtATime,
    ResearchVerb,
    VerdictNothingThrottled,
    VerdictNothingThrottledCompact,
    VerdictNothingSurplus,
    VerdictNothingSurplusCompact,
    HaulGain,
    HaulGainCompact,
    ReachGain,
    ReachGainCompact,
    PlusPercent,
    ToleranceBand,
    Requires,
    NamedLevel,
    ShortlistNothingVerb,
    ShortlistNothing,
    ShortlistVerb,
    Shortlist,

    // ── The Research sheet's prose ──────────────────────────────────────────────────────────
    SheetSubjectPrefix,
    SheetArrow,
    SheetAtLevelOne,
    SheetToleranceSubject,
    SheetReachesNothing,
    SheetReachesPrefix,
    SheetReachesMiddle,
    SheetReachesSuffix,
    SheetAndWouldMake,
    SheetEachHullLifts,
    SheetAnHourOnStation,
    SheetPaysOnNextRun,
    SheetTheNextGalaxyIs,
    SheetAwayAndWouldBe,
    SheetPaysTheFurtherYouAim,
    SheetColonyDrawsAnd,
    SheetAtThatRatio,
    SheetRoundsAway,
    SheetPaysWhenPlantsCarry,
    SheetMultipliesSupply,
    SheetOutputDoesNotMove,
    SheetRequiresPrefix,

    // ── The Fleets tab ──────────────────────────────────────────────────────────────────────
    ShipsScout,
    ShipsSkiff,
    ShipsHauler,
    ShipsEscort,
    ShipsSettler,
    FleetsHeading,
    FleetsAwayOf,
    FleetsNothingOut,
    WorkedHeading,
    WorkedNewestFirst,
    RunCount,
    UnrecordedRuns,
    DepositFullWord,
    DepositEmptyWord,
    WorldRowPrefix,
    WorldRowSeparator,

    // ── The Galaxy tab ──────────────────────────────────────────────────────────────────────
    GalaxyLabel,
    GalaxyNamed,
    GalaxiesCount,
    SystemsCount,
    SurveyedCount,
    PinnedCount,
    ChartedOfSystems,
    UnchartedWord,
    ChartsSystems,
    SystemsOut,
    SystemRange,
    SystemAddressLabel,
    RelayEffect,
    DangerFromHere,
    ReachSingle,
    ReachRange,
    ReachRangeMinutes,
    StarClassDim,
    StarClassStandard,
    StarClassBright,
    StarDetail,
    StarDetailCompact,
    WorldCount,
    WorldsSurveyedCount,
    NoWorlds,
    HomeNote,
    ProbeLandsIn,
    ProbeFlight,
    ProbeFlightLabel,
    RunFlight,
    NothingToSurvey,
    SurveyedAtGenesis,
    DispatchProbe,
    DispatchProbeCompact,
    FindSettleable,
    FindNearMiss,
    FindNone,
    TemperatureReading,
    GravityReading,
    PressureReading,
    FoundAgo,
    AxisTemperature,
    AxisGravity,
    AxisPressure,
    // ── What a world is called, in two words ────────────────────────────────────────────────
    //
    // **The entry that made a second language a `core` change.** These were English literals inside
    // `WorldEpithet` until 0.14.0, reaching the UI as a `TextRes.Raw` and therefore untranslatable by
    // construction — which is exactly what the Italian ledger showed, a column of `veiled furnace`
    // under Italian headings. What `core` knows is the *distinction* between six nouns and twelve
    // adjectives; which words those are, and in which order they go, is this table's.
    //
    // `WorldEpithet` is one entry over two arguments rather than a joined pair, because the order is
    // the language's: English writes "veiled furnace" and Italian writes "fornace velata".
    WorldEpithet,
    EpithetNounFurnace,
    EpithetNounFrost,
    EpithetNounGiant,
    EpithetNounHusk,
    EpithetNounShroud,
    EpithetNounWaste,
    EpithetNounWorld,
    EpithetAdjectiveScorched,
    EpithetAdjectiveFrozen,
    EpithetAdjectiveIron,
    EpithetAdjectiveHollow,
    EpithetAdjectiveVeiled,
    EpithetAdjectiveAirless,
    EpithetAdjectiveAshen,
    EpithetAdjectiveDeep,
    EpithetAdjectiveBrittle,
    EpithetAdjectiveDrowned,
    EpithetAdjectiveBare,
    EpithetAdjectiveTemperate,

    NoteHome,
    NoteOccupied,
    NoteSettleable,
    NoteBarren,
    NoteBarrenDiscovery,
    NoteBlocked,
    NoteWouldLandIt,
    NoteSurveyed,
    WorthItAt,
    DepositFraction,
    LedgerEmptyHeadline,
    LedgerEmptyDetail,
    LedgerNoMatchHeadline,
    LedgerNoMatchDetail,
    VerdictWordHome,
    VerdictWordOccupied,
    VerdictWordBlocked,
    VerdictWordBarren,
    VerdictWordSettleable,
    DiscoveriesHeadingOne,
    DiscoveriesHeadingMany,
    PinnedHeading,
    RelayLabel,
    LedgerModeWorlds,
    LedgerModeMap,
    SearchPlaceholder,
    BlockedAxisLine,
    MiddotStandalone,
    OrbitSlot,
    // ── The shell ───────────────────────────────────────────────────────────────────────────
    TabColony,
    TabResearch,
    TabShipyard,
    TabGalaxy,
    TabFleets,
    Watching,
    RatePerHour,
    ResourceRailMetal,
    ResourceRailCrystal,
    ResourceRailDeuterium,
    // ── Notifications ───────────────────────────────────────────────────────────────────────
    //
    // **The one surface with no composition anywhere near it.** These are written into the OS's own
    // database hours before they are read, which is why `Translations` is a plain object the shell
    // hands to `GameNotifications` rather than a `CompositionLocal`.
    ListSeparator,
    ListLastSeparator,
    UpgradesDoneTitle,
    UpgradesDoneBody,
    ReachedLevel,
    BuildCompleteBody,
    LabFreeBody,
    AdaptationOpenedBody,
    HullLeftYardTitle,
    HullLeftYardBody,
    HullOrderDoneTitle,
    HullOrderDoneBody,
    ProbeReachedTitle,
    ChartedNoneSettleable,
    ChartedSettleable,
    ShipsHomeTitle,
    ShipsHomeBody,
    AffordableTitle,
    AffordableBody,
    SystemAddressBare,
    BuildingFullNameMetalMine,
    BuildingFullNameCrystalMine,
    BuildingFullNameDeuteriumSynthesizer,
    BuildingFullNameSolarPlant,
    BuildingFullNameRoboticsFactory,
    BuildingFullNameNaniteFactory,
    ShipTitleNameScout,
    ShipTitleNameSkiff,
    ShipTitleNameHauler,
    ShipTitleNameEscort,
    ShipTitleNameSettler,
    AdaptationFullNameThermal,
    AdaptationFullNameGravitic,
    AdaptationFullNameAtmospheric,
    StepperFewer,
    StepperMore,
    DepositGap,
    NotificationChannelName,
    NotificationChannelDescription,
    PlayerDefaultName,
    // **What several things of one kind are called when they arrive together**, one entry per
    // category. Seven entries rather than one with a noun argument, because the verb is not the same
    // in either table — a facility *is done*, a hull *has left the yard*, a fleet *is home* — and a
    // shared frame would force one verb on all of them or make the noun carry the sentence.
    //
    // Never resolved with a count below two: one of anything is that thing's own singleton alert.
    AlertGroupFacilities,
    AlertGroupResearch,
    AlertGroupAdaptations,
    AlertGroupHulls,
    AlertGroupProbes,
    AlertGroupFleetReturns,
    AlertGroupPriceReached,
    // The same seven as a bare clause — "3 fleets" — for the title that has more than one kind in it.
    // No verb and no conjunction, which is the design's rule and is what keeps a two-language title
    // the same shape: a verb costs a plural agreement and *and* costs a conjunction, and those are the
    // two places English and Italian disagree in every string.
    AlertCountFacilities,
    AlertCountResearch,
    AlertCountAdaptations,
    AlertCountHulls,
    AlertCountProbes,
    AlertCountFleetReturns,
    AlertCountPriceReached,
    // "+2", where the number counts *categories* the title had no room for rather than things.
    AlertMoreCategories,
    // ── The settings sheet ──────────────────────────────────────────────────────────────────
    //
    // The first preferences surface in the app. `SettingsComingSoon` used to be the whole of it and
    // left the catalogue with this slice — the design system lists "Coming soon" under *Never
    // written*, and the exception logged at 0.16 is closed rather than carried.
    SettingsTitle,
    AlertsLabel,
    AlertModePerItem,
    AlertModeByCategory,
    AlertModePerItemNote,
    AlertModeByCategoryNote,
    AlertCategoryFacilities,
    AlertCategoryResearch,
    AlertCategoryAdaptations,
    AlertCategoryHulls,
    AlertCategoryProbes,
    AlertCategoryFleetReturns,
    AlertCategoryPriceReached,
    AlertPriceWatchOn,
    AlertPriceWatchOff,
    AlertBellOn,
    AlertBellOff,
    DeliveryLabel,
    DeliveryEach,
    DeliveryPerCategory,
    DeliveryTotal,
    AlertNextAt,
    AlertNextAtTotal,
    AlertNothingPending,

    // ── The changelog sheet ─────────────────────────────────────────────────────────────────
    //
    // Five entries, and deliberately only five: the sixty-five releases themselves are two
    // documents in `:client:changelog:presentation` rather than two hundred and sixty ids here.
    // `.claude/docs/changelog-sheet.md` §4 is the argument — the short of it is that an exhaustive
    // `when` can only catch a *missing* id, while the paired documents let a test catch a release
    // Italian never got, a date that drifted and a page that lost a line in translation.
    //
    // What stays here is what the catalogue is actually for: the chrome, and a date. `ReleaseDate`
    // is the first calendar date the game has ever written — every other instant it prints is a
    // clock time or a duration — and the month name is exactly the kind of thing a table owns.
    ChangelogTitle,
    ChangelogDepth,
    ReleaseDate,
    BuildLabel,
    BuildRowSpoken,

    // ── The gate ────────────────────────────────────────────────────────────────────────────
    //
    // The first screen in the game that is not about a colony, and the only one with two objects on
    // it that Oltre does not own. **The two provider strings are in the catalogue because the
    // platforms translate them and we do not** — `Accedi con Apple` is Apple's Italian, not ours, and
    // a `Raw` here would have shipped the English to every language. Everything else on the screen is
    // the game's own voice.
    //
    // `SignIn…` rather than `Gate…`, because `GateClause` and its five siblings above are about a
    // *requirement* gating a facility, and a reader who has to disambiguate a prefix will eventually
    // get it wrong.
    SignInWhyLead,
    SignInWhyFoot,
    SignInFoot,
    SignInWithApple,
    SignInWithGoogle,
    SignInWaitingLead,
    SignInWaitingBody,
    SignInNoAnswerLead,
    SignInNoAnswerBody,
    SignInRefusedLead,
    SignInRefusedBody,
    SignInThrottledLead,
    SignInThrottledBody,
    SignInAskAgainNow,
    // **The one thing the gate has to say when it has nothing to offer.** Every other message here
    // reports on something the player did; this one reports on the build itself, and it exists
    // because a screen drawing no provider at all would otherwise be two lines of *why* with no way
    // forward and no explanation of the absence.
    SignInNoProviderLead,
    SignInNoProviderBody,
    // The two providers by name, and the one place in the catalogue where every language says the
    // same thing on purpose: these are trademarks, and a table that could translate one is a table
    // that will. They are entries rather than `TextRes.Raw` because they are *arguments* to two
    // sentences the languages do order differently.
    ProviderApple,
    ProviderGoogle,

    // ── Accepted, and not a fact yet ─────────────────────────────────────────────────────────
    //
    // The amber half of the offline era. Every one of these is a **card's foot or a row's second
    // line** — the third carrier of held, after a surface and a face — and each says which way the
    // request went, because that is the one thing a 29dp square cannot.
    HeldButton,
    HeldUpgradeFoot,
    HeldStartFoot,
    HeldBuildFoot,
    HeldBuildAndAlertFoot,
    HeldWatchOnFoot,
    HeldWatchOffFoot,
    HeldAnnounceFoot,
    // The direction, said plainly, for every bell whose row has no richer sentence of its own.
    HeldTurningOn,
    HeldTurningOff,
    HeldLadderNote,
    // The one new piece of chrome in this era, and the only line in the app that carries a fact
    // about the *network* rather than about the colony. It never carries the state of a control.
    OfflineSince,
    OfflineSinceCompact,

    // ── Refused outright ─────────────────────────────────────────────────────────────────────
    //
    // The red half. Three verbs aim at something the server owns and this phone cannot promise —
    // two at a shared galaxy, one at the account itself — so they refuse at the tap and name the
    // fact that stops them. Never a code, never a dialog, never an apology.
    RefusedRunLead,
    RefusedRunBody,
    RefusedRunBodyCompact,
    RefusedProbeLead,
    RefusedProbeBody,
    RefusedProbeBodyCompact,
    RefusedDeleteLead,
    RefusedDeleteBody,

    // ── The account, and the door out of it ──────────────────────────────────────────────────
    //
    // App Review guideline 5.1.1(v) asks for the door; what is here is more than it asks for, and
    // the extra is the second fact — that signing in again with the same account starts an empty
    // colony. Numbers cannot teach that and it is the thing a player most needs to know.
    AccountLabel,
    AccountSignedInWith,
    AccountSince,
    DeleteAccountRow,
    DeleteAccountRowNote,
    DeleteFaceTitle,
    DeleteFaceIntro,
    DeleteFaceSecond,
    DeleteFaceAction,
    DeleteFactColonyLabel,
    DeleteFactFleetLabel,
    DeleteFactMapLabel,
    DeleteFactResearchLabel,
    DeleteFactColony,
    DeleteFactFleet,
    // **The fleet row on a colony that has none**, which is not an edge case: a first launch opens
    // with no hull at all — the first one is the first purchase — so this is the *ordinary* reading
    // for a player who signs in and immediately changes their mind. A list of nothing has no
    // grammar, so it gets a sentence rather than a joined run.
    DeleteFactFleetEmpty,
    DeleteFactMap,
    DeleteFactResearch,
    DeleteConfirmTitle,
    DeleteConfirmIntro,
    DeleteConfirmSecond,
    DeleteKeep,
    DeleteConfirmAction,

    // ── A name you chose, and a mark you picked ──────────────────────────────────────────────
    //
    // **The first surface whose words are mostly the names of drawings.** Six presets and eleven
    // parts, one id each and no shared entry taking a noun — the same call the six facilities and the
    // five hulls above are, and for the same reason: a name is a word, and words are what a catalogue
    // holds.
    //
    // The two `None`s are what turn that from a habit into a rule. English says one word for a path
    // that is absent and for a terminus that is absent; Italian says `Nessuna` for the first and
    // `Nessuno` for the second, because each agrees with the noun it stands in for. A shared entry
    // would have made that unsayable. `Terminator` is the same lesson from the other end — it is a
    // preset *and* a body part, two drawings, and a language that needs two words for them has to be
    // able to have them.
    //
    // `Dead Reckoning` is deliberately not here and must not be added. It is `PlayerDefaultName`
    // above, it is the player's own name rather than a phrase about one, and the single entry below
    // that says it takes it as an argument.
    ProfileTitle,
    ProfileMarkLabel,
    ProfileNameLabel,
    MarkNameThreshold,
    MarkNameTerminator,
    MarkNameAphelion,
    MarkNameSextant,
    MarkNameWake,
    MarkNameSounding,
    ProfileSaveName,
    ProfileEmptyName,
    // "18/24", and both figures are printed rather than counted: a length changes no word around it,
    // so this is two `Arg.Number` and never an `Arg.Count`.
    ProfileNameCounter,
    // **The held face says three things and none of them is the banner.** `OfflineSince` above ends
    // in a tally of what is waiting; nothing is waiting here, because a rename is the one edit of
    // this era that refuses instead of queueing — so the requirement, the explanation and the field's
    // one-line refusal are their own entries.
    ProfileHeldRequirement,
    // **The same card with the other reason on it.** `ProfileHeldRequirement` names the minute the
    // network was last seen; this one is for the state where there is nothing to name — the account
    // itself has not been read, so a whole-row write would be built out of a guess. Its own entry
    // rather than a reuse, because *"no network since 09:41"* would be a claim about the network that
    // this state is not making.
    ProfileUnreadRequirement,
    ProfileHeldBody,
    ProfileHeldFieldNote,
    MarkComposeRow,
    MarkComposedName,
    MarkSlotBody,
    MarkSlotPath,
    MarkSlotTerminus,
    MarkBodyLimb,
    MarkBodyTerminator,
    MarkBodyOrbit,
    MarkBodyWake,
    MarkPathRising,
    MarkPathTransfer,
    MarkPathTwin,
    MarkPathNone,
    MarkTerminusDot,
    MarkTerminusRing,
    MarkTerminusNone,
    MarkComposeFoot,
}
