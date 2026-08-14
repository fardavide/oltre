package dev.fardavide.oltre.core

import kotlin.jvm.JvmInline

// A uniform draw in 0 … 1, carried as an integer over a fixed basis so the trait formulas below
// stay exact integer arithmetic on every platform. Inclusive of 1 at the top, which is what lets
// the published ranges land on their round numbers — gravity really reaches 2.75 g rather than
// stopping a thousandth short of it. The 1-in-10,001 bias that buys is far below anything the
// distribution targets can see.
@JvmInline
value class Uniform(val ofBasis: Int) {
    init {
        require(ofBasis in 0..BASIS) { "a uniform draw must be between 0 and $BASIS, was $ofBasis" }
    }

    companion object {
        const val BASIS: Int = 10_000
        val MAX: Uniform = Uniform(BASIS)
        fun ofPercent(percent: Int): Uniform = Uniform(percent * BASIS / 100)
    }
}

// How often each star class turns up in a region of a given temperament. The three always sum to
// 100, and it is a named type rather than three loose percentages so that `GalaxyBalanceTest` can
// pin a whole temperament in one assertion — the treatment every other published table here gets.
data class StarMix(val dimPercent: Int, val standardPercent: Int, val brightPercent: Int) {
    init {
        require(dimPercent + standardPercent + brightPercent == 100) {
            "a star mix must sum to 100, was $dimPercent + $standardPercent + $brightPercent"
        }
    }
}

// DECIDED balance, not placeholders — the same standing `ResearchBalance` has, and for the same
// reason: every number here comes from the galaxy decision sheet, and `GalaxyBalanceTest` pins its
// published tables value by value. Changing one of these is a design change, not a refactor.
//
// The shape of the galaxy, in one paragraph: **an easy world is a poor world.** Three hostility
// axes — temperature, gravity, pressure — each checked against a tolerance band the player widens
// with a separate adaptation ladder, and richness *derived* from those same three axes rather than
// rolled independently. That derivation is the whole pillar: if richness were independent the
// galaxy would contain easy-and-rich worlds and every other consideration would collapse into
// "take those". Hazards are named flags rather than a fourth axis, and the yield score exists to
// make the median world that passes every band score *below* the worth-it threshold, so surveying
// usually returns "not worth it".
object GalaxyBalance {

    // ── The coordinate space ─────────────────────────────────────────────────────────────────
    const val GALAXIES: Int = 4
    const val SYSTEMS_PER_GALAXY: Int = 250
    const val SLOTS_PER_SYSTEM: Int = 15
    const val TOTAL_SLOTS: Int = GALAXIES * SYSTEMS_PER_GALAXY * SLOTS_PER_SYSTEM

    // ── Regions: the map's only geography ────────────────────────────────────────────────────
    //
    // Ten of them, 25 systems each — about two hours wide at drive 0, which is a plausible night's
    // dispatch, and ten names a galaxy, which is learnable in a week. Davide's call, 2026-08-14.
    const val REGIONS_PER_GALAXY: Int = 10
    const val SYSTEMS_PER_REGION: Int = SYSTEMS_PER_GALAXY / REGIONS_PER_GALAXY

    // **A permutation of a fixed multiset, not ten independent draws**, and the difference is the
    // whole design. Ten draws would preserve the galaxy-wide star mix only in expectation, so a
    // per-seed test would have to widen its bands to admit the unlucky galaxy — and a galaxy that
    // rolled ten Settled regions is a galaxy this slice did nothing for. Shuffling a fixed list
    // makes the pooled distribution identical for *every* seed, and lets the game promise that
    // there is a Deep somewhere in yours.
    //
    // **Four, two, four.** `3 / 4 / 3` was the sheet's proposal and this is the build's call under
    // `galaxy-identity-sheet.md` §9.4, on two arguments:
    //
    // 1. **It pools to 32 / 36 / 32**, against `3 / 4 / 3`'s 29 / 42 / 29 — near enough the equal
    //    thirds that every §9 target was measured against to leave them alone.
    // 2. **It makes the map more characterful, not less**: only two regions in ten are the bland
    //    one, where `3 / 4 / 3` leaves four.
    //
    // **What it is *not* justified by is the settleable share, and an earlier draft of this comment
    // claimed it was.** `passes every band` is a count of about seventy-five worlds, so one map's
    // reading carries ±11% of Poisson noise and cannot tell a real shift from a fluctuation — which
    // is exactly the mistake that reading is there to invite. Both multisets measure identically on
    // the test seed. The row is now pinned across six maps instead of one, and every one of them is
    // inside 1 – 2%: see `GalaxyDistributionTest.passing every band holds its target across seeds`.
    val REGION_TEMPERAMENTS: List<RegionTemperament> = listOf(
        RegionTemperament.DEEP,
        RegionTemperament.DEEP,
        RegionTemperament.DEEP,
        RegionTemperament.DEEP,
        RegionTemperament.SETTLED,
        RegionTemperament.SETTLED,
        RegionTemperament.BURNING,
        RegionTemperament.BURNING,
        RegionTemperament.BURNING,
        RegionTemperament.BURNING,
    )

    // A slot holds a world more often in the middle of a system than at its edges, which averages
    // 4.75 worlds per system and ~4,750 galaxy-wide. The inner band is also where the temperature
    // formula puts the habitable orbits, so the worlds worth looking at are where the worlds are.
    private const val INNER_SLOT_FIRST: Int = 4
    private const val INNER_SLOT_LAST: Int = 10
    private const val INNER_OCCUPANCY_PERCENT: Int = 45
    private const val OUTER_OCCUPANCY_PERCENT: Int = 20

    fun occupancyPercent(slot: Int): Int =
        if (slot in INNER_SLOT_FIRST..INNER_SLOT_LAST) INNER_OCCUPANCY_PERCENT else OUTER_OCCUPANCY_PERCENT

    // One system in 40 carries a relay in an unoccupied slot. Inert in 0.2 — no holding mechanic is
    // designed, and the screen may label one but may not make it tappable. It is generated anyway
    // because the *stream* has to exist from the start: a relay added in two years' time shifts
    // nothing only if nothing else was ever drawn from its sub-seed.
    const val RELAY_SYSTEM_IN: Int = 40

    // One world in two hundred wears a ring — about six in a galaxy, twenty-three across the map.
    // Rare enough to be worth remarking on, common enough that a fortnight's play meets one.
    const val RING_IN: Int = 200

    // ── Temperature: a function of the orbit, which is why the coordinate is worth having ────
    //
    // Position *is* a trait, so the charted map is readable before anything has been surveyed and
    // "the outer slots are where the deuterium is" becomes something a player learns rather than
    // something the UI tells them.
    const val TEMPERATURE_JITTER: Int = 20
    private const val TEMPERATURE_AT_ORBIT_ZERO: Int = 220
    private const val TEMPERATURE_FALL_PER_SLOT: Int = 28

    fun starOffset(starClass: StarClass): Int = when (starClass) {
        StarClass.DIM -> -40
        StarClass.STANDARD -> 0
        StarClass.BRIGHT -> 40
    }

    // **DECIDED at 0.10, and it closes the open call this function used to carry.** Until then it
    // read *"ASSUMED, NOT DECIDED … this slice takes equal thirds and says so"*, with the mix
    // recorded as open in `balance-log.md`. The mix is now a consequence of the region temperaments
    // rather than a number of its own: `4 × Deep, 2 × Settled, 4 × Burning` pools to 32 / 36 / 32.
    //
    // The same sentence that made equal thirds safe is what makes this safe — **because the
    // habitable orbits shift with the offset, each class passes the temperature band on ~25% of its
    // worlds either way**, so the galaxy-wide verdict distribution barely moves whatever the class
    // mix is. That property is why star class could be given a regional bias at all and why gravity
    // and pressure could not: they are threshold crossings on a fixed distribution with no
    // compensating coordinate. `galaxy-identity-sheet.md` §1.3 has the argument.
    fun starClass(temperament: RegionTemperament, percent: Int): StarClass {
        val mix = starMix(temperament)
        return when {
            percent < mix.dimPercent -> StarClass.DIM
            percent < mix.dimPercent + mix.standardPercent -> StarClass.STANDARD
            else -> StarClass.BRIGHT
        }
    }

    // Moderate rather than strong — Davide's call, 2026-08-14. A Deep is clearly cold and a Settled
    // still reads like the map did before, so a region keeps some texture instead of collapsing
    // into a single fact.
    fun starMix(temperament: RegionTemperament): StarMix = when (temperament) {
        RegionTemperament.DEEP -> StarMix(dimPercent = 60, standardPercent = 30, brightPercent = 10)
        RegionTemperament.SETTLED -> StarMix(dimPercent = 20, standardPercent = 60, brightPercent = 20)
        RegionTemperament.BURNING -> StarMix(dimPercent = 10, standardPercent = 30, brightPercent = 60)
    }

    fun temperature(slot: Int, starClass: StarClass, jitter: Int): Temperature = Temperature(
        TEMPERATURE_AT_ORBIT_ZERO - TEMPERATURE_FALL_PER_SLOT * slot + starOffset(starClass) + jitter,
    )

    // ── Gravity and pressure: skewed, so the extremes stay rare ──────────────────────────────
    private const val GRAVITY_FLOOR_MILLI_G: Int = 150
    private const val GRAVITY_SPAN_MILLI_G: Int = 2_600
    const val MAX_GRAVITY_MILLI_G: Int = GRAVITY_FLOOR_MILLI_G + GRAVITY_SPAN_MILLI_G

    // 0.15 + 2.6 u², so the median world is ~0.8 g and a 2 g world is genuinely uncommon.
    fun gravity(uniform: Uniform): Gravity {
        val u = uniform.ofBasis.toLong()
        val basis = Uniform.BASIS.toLong()
        return Gravity((GRAVITY_FLOOR_MILLI_G + GRAVITY_SPAN_MILLI_G * u * u / (basis * basis)).toInt())
    }

    private const val PRESSURE_SPAN_MILLI_ATM: Int = 12_000

    // 12 u³ — cubic rather than quadratic, so a thick atmosphere is rarer than a heavy world. That
    // is deliberate: crystal is the resource the sheet wanted hardest to reach.
    fun pressure(uniform: Uniform): Pressure {
        val u = uniform.ofBasis.toLong()
        val basis = Uniform.BASIS.toLong()
        return Pressure((PRESSURE_SPAN_MILLI_ATM * u * u * u / (basis * basis * basis)).toInt())
    }

    // The one thing an axis does beyond the tolerance check and richness, because it has an obvious
    // home and no other: heavy worlds are big worlds. Gravity is therefore the cost and the reward
    // twice over — rich in metal, roomy, and the hardest to stand on.
    private const val FIELDS_FLOOR: Int = 80
    private const val FIELDS_SPAN: Int = 180

    fun fields(gravity: Gravity): Int = FIELDS_FLOOR + FIELDS_SPAN * gravity.milliG / MAX_GRAVITY_MILLI_G

    // ── Richness: derived from the axes, never rolled ────────────────────────────────────────
    const val RICHNESS_BASIS: Int = 1_000_000
    private const val RICHNESS_FLOOR: Int = 600_000
    private const val RICHNESS_SPAN: Int = 500_000
    private const val RICHNESS_MIN: Int = 600_000
    private const val RICHNESS_MAX: Int = 1_600_000

    // The value of each axis at which its resource reaches 1.1 — the reference points the sheet's
    // three formulas are written against.
    private const val METAL_REFERENCE_GRAVITY: Int = 1_400
    private const val CRYSTAL_REFERENCE_PRESSURE: Int = 3_000
    private const val DEUTERIUM_REFERENCE_TEMPERATURE: Int = 20
    private const val DEUTERIUM_REFERENCE_SPAN: Int = 60

    fun metalRichness(gravity: Gravity): Richness =
        richnessAbove(RICHNESS_SPAN.toLong() * gravity.milliG / METAL_REFERENCE_GRAVITY)

    fun crystalRichness(pressure: Pressure): Richness =
        richnessAbove(RICHNESS_SPAN.toLong() * pressure.milliAtm / CRYSTAL_REFERENCE_PRESSURE)

    // The coldest worlds hold the deuterium, which is the resource the research branch already made
    // scarce. The branch that gates research is therefore gated by the map.
    fun deuteriumRichness(temperature: Temperature): Richness = richnessAbove(
        RICHNESS_SPAN.toLong() * (DEUTERIUM_REFERENCE_TEMPERATURE - temperature.celsius) / DEUTERIUM_REFERENCE_SPAN,
    )

    // Clamped at both ends and it matters at both: pressure reaches 12 atm and temperature reaches
    // -260 °C, either of which runs the raw formula far past the band. Without the clamp a single
    // extreme world would outscore every balanced one.
    private fun richnessAbove(span: Long): Richness =
        Richness((RICHNESS_FLOOR + span).coerceIn(RICHNESS_MIN.toLong(), RICHNESS_MAX.toLong()).toInt())

    // ── Yield: is this world worth taking ────────────────────────────────────────────────────
    //
    // Each richness weighted by that resource's share of the reference colony's *priced* output —
    // the 698 / 224 / 72 per hour at 1 : 2 : 3 from `balance-log.md`, which is 51 / 33 / 16. So the
    // score means "worth it to this economy" rather than "big numbers".
    const val METAL_WEIGHT_PERCENT: Int = 51
    const val CRYSTAL_WEIGHT_PERCENT: Int = 33
    const val DEUTERIUM_WEIGHT_PERCENT: Int = 16
    private const val HAZARD_PENALTY: Int = 50_000

    // The median world that passes every tolerance band scores below this, so **the median
    // settleable world is Barren** — by construction, because that is the design. If a survey
    // usually paid off, surveying would be a tax rather than a decision.
    //
    // Raised from 0.90 at 0.0.15. Tightening the two tolerance bands landed `passes every band`
    // inside 1–2% but left `passes and clears 0.90` at 0.58% against a ≤0.5% bound, and the
    // threshold is the one lever that moves that row *without* touching which worlds pass — so the
    // three axes keep the comparable pass rates §1 needs. The property it exists for still holds
    // with room to spare: the median passing world scores 0.85.
    val WORTH_IT_THRESHOLD: YieldScore = YieldScore(920_000)

    fun yieldScore(traits: WorldTraits): YieldScore {
        val weighted = METAL_WEIGHT_PERCENT.toLong() * traits.metalRichness.perMillion +
            CRYSTAL_WEIGHT_PERCENT.toLong() * traits.crystalRichness.perMillion +
            DEUTERIUM_WEIGHT_PERCENT.toLong() * traits.deuteriumRichness.perMillion
        return YieldScore((weighted / 100 - HAZARD_PENALTY.toLong() * traits.hazards.size).toInt())
    }

    // ── Hazards ──────────────────────────────────────────────────────────────────────────────
    const val ONE_HAZARD_PERCENT: Int = 35
    const val TWO_HAZARD_PERCENT: Int = 10

    // ── Tolerance: what the species handles, and what each ladder buys ───────────────────────
    //
    // Gravity and pressure were tightened at 0.0.15 — 0.55…1.45 g became 0.65…1.40, and 0.4…3.0 atm
    // became 0.5…2.6 — after `:sim:run` measured the sheet's own §8 constants against its §9
    // targets for the first time. Davide's call, delegated to the build on 2026-08-07; the round is
    // written up in `balance-log.md`.
    //
    // Why these two and not temperature: temperature was already the tightest axis at 25.4%, and
    // its band is the one tied to the slot formula that makes position a trait. Bringing the other
    // two **down to meet it** — 25.5% and 25.4% — lands `passes every band` inside 1–2% and
    // `settleable` under 0.5%, and leaves all three axes gating a near-identical share. That last
    // part is the point: §1's whole argument for three ladders is that *which one you push first*
    // is a real choice, which stops being true the moment one axis blocks everything.
    //
    // The yield model was deliberately not touched. Its own prediction — a median passing world at
    // 0.84 against a 0.90 threshold — landed almost exactly, so the thing that was wrong was which
    // worlds pass, not what they are worth.
    private const val BASE_TEMPERATURE_MIN: Int = -30
    private const val BASE_TEMPERATURE_MAX: Int = 45
    private const val BASE_GRAVITY_MIN: Int = 650
    private const val BASE_GRAVITY_MAX: Int = 1_400
    private const val BASE_PRESSURE_MIN: Int = 500
    private const val BASE_PRESSURE_MAX: Int = 2_600

    const val THERMAL_WIDENING_PER_LEVEL: Int = 14
    const val GRAVITIC_LOWER_WIDENING_PER_LEVEL: Int = 50
    const val GRAVITIC_UPPER_WIDENING_PER_LEVEL: Int = 120
    const val ATMOSPHERIC_LOWER_WIDENING_PER_LEVEL: Int = 60
    const val ATMOSPHERIC_UPPER_WIDENING_PER_LEVEL: Int = 900

    // Each level widens exactly its own axis. That separation is the mechanic — an empire that
    // pushed Thermal and one that pushed Gravitic are looking at two different maps.
    fun tolerance(adaptation: AdaptationLevels): Tolerance = Tolerance(
        temperature = ToleranceBand(
            min = BASE_TEMPERATURE_MIN - THERMAL_WIDENING_PER_LEVEL * adaptation.thermal,
            max = BASE_TEMPERATURE_MAX + THERMAL_WIDENING_PER_LEVEL * adaptation.thermal,
        ),
        gravity = ToleranceBand(
            min = BASE_GRAVITY_MIN - GRAVITIC_LOWER_WIDENING_PER_LEVEL * adaptation.gravitic,
            max = BASE_GRAVITY_MAX + GRAVITIC_UPPER_WIDENING_PER_LEVEL * adaptation.gravitic,
        ),
        pressure = ToleranceBand(
            min = BASE_PRESSURE_MIN - ATMOSPHERIC_LOWER_WIDENING_PER_LEVEL * adaptation.atmospheric,
            max = BASE_PRESSURE_MAX + ATMOSPHERIC_UPPER_WIDENING_PER_LEVEL * adaptation.atmospheric,
        ),
    )

    // The lowest level of `axis.adaptation` whose band contains `value` — 0 when the unaided
    // species already tolerates it. This is what lets a `Blocked` verdict read as a shopping list
    // rather than as a wall, and every value the generator can produce is reachable: the widest
    // gap in the published ranges is the coldest orbit, which 17 levels of Thermal closes.
    fun levelThatTolerates(axis: HostilityAxis, value: Int): Int {
        val unaided = tolerance(AdaptationLevels.NONE).bandOf(axis)
        return when {
            value < unaided.min -> ceilDiv(unaided.min - value, lowerWideningPerLevel(axis))
            value > unaided.max -> ceilDiv(value - unaided.max, upperWideningPerLevel(axis))
            else -> 0
        }
    }

    // The whole bill for one world, in levels: what `levelThatTolerates` says on each axis, added
    // up. Not a cost and not a duration — those are `AdaptationBalance`'s, and they depend on which
    // ladders the levels land on. This is the coarser question *how far away is this world*, which
    // is what genesis asks when it picks a home system and what `:sim:run` measures the opening by.
    //
    // Zero for a world the unaided species already tolerates, so it reads as a distance rather than
    // as a verdict — a `Barren` world and a `Settleable` one are both at zero, because both are
    // worlds the player can already stand on.
    fun levelsToTolerate(traits: WorldTraits): Int =
        HostilityAxis.entries.sumOf { axis -> levelThatTolerates(axis, traits.axisValue(axis)) }

    private fun lowerWideningPerLevel(axis: HostilityAxis): Int = when (axis) {
        HostilityAxis.TEMPERATURE -> THERMAL_WIDENING_PER_LEVEL
        HostilityAxis.GRAVITY -> GRAVITIC_LOWER_WIDENING_PER_LEVEL
        HostilityAxis.PRESSURE -> ATMOSPHERIC_LOWER_WIDENING_PER_LEVEL
    }

    // Deliberately not the same as the lower widening on two of the three axes: a level of Gravitic
    // buys more headroom above than below, because the heavy worlds are the ones worth reaching.
    private fun upperWideningPerLevel(axis: HostilityAxis): Int = when (axis) {
        HostilityAxis.TEMPERATURE -> THERMAL_WIDENING_PER_LEVEL
        HostilityAxis.GRAVITY -> GRAVITIC_UPPER_WIDENING_PER_LEVEL
        HostilityAxis.PRESSURE -> ATMOSPHERIC_UPPER_WIDENING_PER_LEVEL
    }

    private fun ceilDiv(numerator: Int, denominator: Int): Int = (numerator + denominator - 1) / denominator
}
