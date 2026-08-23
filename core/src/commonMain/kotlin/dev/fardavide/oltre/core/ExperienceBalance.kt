package dev.fardavide.oltre.core

// PLACEHOLDER balance, like `PlaceholderBalance` and `SurveyBalance` — the numbers were fitted by
// the build against a measured target, and the target is Davide's: *"Let's imagine a 1-day player
// must be around Lv 3, 1-week lv 10, 2 weeks lv 15, 1 month lv 25. To give a very rough estimate. I
// imagine most of the actions give exp: upgrade, survey, travels, build ship, etc."* (2026-08-22).
//
// What is **decided** rather than fitted, and what the constants may not be tuned out from under:
//
// **A completion pays and a start does not.** Davide's call over the alternative. It is also what
// makes the gauge belong to this game rather than to a game with a progress bar in it: everything
// here happens while the app is closed, so a bar that only moved when a finger touched it would be
// the one reading on the screen that had nothing to do with being away. The player who checks in
// after a night finds the mines up, the probe home *and* the level moved, by the same clock.
//
// **A hull pays per hull, and small.** Davide's call, 2026-08-22, over "one order one award". The
// smallness is not a preference — it is what stops the level becoming a fleet counter. Hull
// purchases are paid for out of income and income compounds, so `:sim:run`'s thirty-day player owns
// 1,721 skiffs against 79 finished facilities; at a facility's price they would be four fifths of
// every point in the game by the end of the first month and more than that by the second.
//
// **What you did rather than what you own.** No award reads a cost, a cargo or a stock. A run home
// pays the same whether it lands 200 units or 200,000, because the run is the decision and the
// cargo is the economy's answer to it — and an award that scaled with the cargo would compound with
// the mines, which is the fleet-counter failure again wearing the other hat. What *does* scale is
// **depth**: a level-20 mine is worth more than a level-2 one, because it is a day of waiting rather
// than four minutes of it, and that is the one honest difference between two otherwise identical
// taps.
object ExperienceBalance {

    // ── What a completion is worth ───────────────────────────────────────────────────────────
    //
    // Read the two halves of each pair together: the base is *that you did it at all*, the per-level
    // term is *how far in you are*. The bases are within a small multiple of each other on purpose —
    // no verb in this game is meant to be the one you grind — and the ratios between them are the
    // only judgement in this block: a project is worth half again a facility level because there is
    // one slot for it and six for the other, and a survey is worth two facility levels because it is
    // the only verb that adds to the map.

    // A facility level, at level 1 and at every level after it. 20 a level is chosen against the
    // depth the first month actually reaches — the sim's player finishes 79 levels summing to 739,
    // so the depth term is roughly twice the base term over a month and the two ends of the game
    // stay legible against each other.
    const val BUILD_BASE: Long = 100
    const val BUILD_PER_LEVEL: Long = 20

    // Applied research and the three adaptation ladders, at the same price. They are the same kind
    // of thing from the level's point of view — a project that occupies a slot for hours — and
    // `AdaptationBalance` already prices its three ladders deliberately equal to each other for the
    // same reason. Dearer than a facility level because there are two slots in the game and six
    // facilities, so a project is the scarcer commitment.
    const val PROJECT_BASE: Long = 150
    const val PROJECT_PER_LEVEL: Long = 30

    // A probe landing. The dearest base in the table, because it is the only verb whose payoff is
    // information: everything else adds to a colony that already existed, and this adds to the map.
    const val SURVEY_BASE: Long = 200

    // Per world the probe actually found. A system that came back empty still paid its base — the
    // trip counted and the player chose it — but the ones that came back full are what the verb is
    // for, and this is the difference stated in points rather than in prose.
    const val SURVEY_PER_WORLD: Long = 50

    // A fleet coming home. Flat, and deliberately blind to the cargo, the distance and the window —
    // see the note above about what you did rather than what you own. `Event.FleetReturned.from` is
    // nullable besides, so a distance term would have no answer at all for a run folded forward by
    // the schema-8 migration.
    const val RUN_HOME: Long = 150

    // A hull off the slipway. An eighth of the shallowest facility level in the game, which is what
    // "small" was asked for — and `ExperienceTest` pins the ratio rather than the constant, so a
    // later round can move both without quietly losing the call that set them apart.
    const val HULL: Long = 15

    // ── What a level costs ───────────────────────────────────────────────────────────────────
    //
    // **The curve is a straight line in the level and therefore a quadratic in the total**, and that
    // shape is what the four marks ask for rather than a convention borrowed from elsewhere.
    //
    // The measurement is the reason. `:sim:run`'s experience report runs one player for thirty days
    // and the finding is that **experience accrues almost exactly linearly in time** — about 4,600 a
    // day, holding within a few percent from day one to day thirty. That is the check-in loop
    // working: a player with five minutes has roughly the same amount of *deciding* to do in the
    // first week as in the fifth, even though what they are deciding about has grown by two orders
    // of magnitude. Davide's marks, meanwhile, are a power law — 3, 10, 15 and 25 sit on
    // `3 x days^0.62` to within a level at every mark — so experience per level has to grow like
    // `level^0.6`, and a straight line is the integer curve that tracks it over the range anybody
    // will play. A geometric ladder, which is what most games use here, would be far too steep: it
    // is the right shape for a game whose *income* is the score, and in this one the income is not.
    //
    // Fitted, then rounded to something legible. The marks it lands on are in
    // `.claude/docs/balance-log.md` round 32 and pinned by `ExperienceTest`.
    const val LEVEL_BASE: Long = 1_100
    const val LEVEL_STEP: Long = 360

    // Six of the twelve members of `Event` are worth nothing and every one of them is named. There
    // is no `else` here on purpose: a thirteenth event has to be priced by whoever adds it, and a
    // default would let it be worth zero by omission — which is the one failure this table can have
    // that nobody would ever see, because a level that is slightly too low looks exactly like a
    // level that is correct.
    fun awardFor(event: Event): Experience = when (event) {
        is Event.BuildCompleted -> Experience(BUILD_BASE + BUILD_PER_LEVEL * event.newLevel.value)
        is Event.ResearchCompleted -> Experience(PROJECT_BASE + PROJECT_PER_LEVEL * event.newLevel.value)
        is Event.AdaptationCompleted -> Experience(PROJECT_BASE + PROJECT_PER_LEVEL * event.newLevel.value)
        is Event.SurveyCompleted -> Experience(SURVEY_BASE + SURVEY_PER_WORLD * event.worldsFound)
        is Event.FleetReturned -> Experience(RUN_HOME)
        is Event.ShipsBuilt -> Experience(HULL * event.ships.total)
        // The commitments. Each one's partner above is what pays for it.
        is Event.BuildStarted,
        is Event.ResearchStarted,
        is Event.AdaptationStarted,
        is Event.SurveyStarted,
        is Event.FleetDispatched,
        is Event.ShipsOrdered,
        -> Experience.NONE
    }

    // What it costs to leave `level` behind. Never zero, so the gauge always has somewhere to go.
    fun spanOf(level: PlayerLevel): Experience =
        Experience(LEVEL_BASE + checkedTimes(LEVEL_STEP, level.value.toLong()) { "span of level $level" })

    // The total a player has to have earned to be standing on `level`. The sum of every span below
    // it, closed rather than walked: `base x L + step x L x (L - 1) / 2`.
    fun thresholdOf(level: PlayerLevel): Experience {
        val steps = level.value.toLong()
        val flat = checkedTimes(LEVEL_BASE, steps) { "threshold of $level" }
        val rising = checkedTimes(
            checkedTimes(LEVEL_STEP, steps) { "threshold of $level" },
            steps - 1,
        ) { "threshold of $level" } / 2
        return Experience(flat + if (steps == 0L) 0 else rising)
    }

    // The inverse of `thresholdOf`, without walking the ladder.
    //
    // `threshold(L) <= earned` rearranges to `step x L^2 + (2 x base - step) x L - 2 x earned <= 0`,
    // whose positive root is the answer. **Integer Newton rather than `sqrt`**, for `Curves.kt`'s own
    // reason: `core` is pure and has to give the same answer on every target it compiles for, and a
    // float root landing a hair under a perfect square would hand two platforms different levels for
    // the same save. The root floors, so the estimate can be a step low; the two loops below settle
    // it and are bounded by one iteration each in practice.
    fun levelFor(earned: Experience): PlayerLevel {
        val linear = 2 * LEVEL_BASE - LEVEL_STEP
        val discriminant = checkedTimes(linear, linear) { "level of $earned" } +
            checkedTimes(8 * LEVEL_STEP, earned.points) { "level of $earned" }
        var level = ((integerRoot(discriminant) - linear) / (2 * LEVEL_STEP)).toInt().coerceAtLeast(0)
        while (thresholdOf(PlayerLevel(level + 1)) <= earned) level += 1
        while (level > 0 && thresholdOf(PlayerLevel(level)) > earned) level -= 1
        return PlayerLevel(level)
    }

    fun progressFor(earned: Experience): PlayerProgress {
        val level = levelFor(earned)
        val start = thresholdOf(level)
        return PlayerProgress(
            level = level,
            earned = earned,
            intoLevel = Experience(earned.points - start.points),
            span = spanOf(level),
        )
    }
}
