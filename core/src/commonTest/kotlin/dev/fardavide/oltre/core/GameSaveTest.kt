package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class GameSaveTest {

    @Test
    fun `a fresh colony survives a round trip unchanged`() {
        // given
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial())

        // when
        val decoded = GameSave.decode(GameSave.encode(snapshot))

        // then
        assertEquals(snapshot, assertIs<DecodeResult.Success>(decoded).snapshot)
    }

    @Test
    fun `a colony mid-build survives a round trip with its queue and event log`() {
        // given
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded(BuildingType.METAL_MINE), BuildingType.METAL_MINE, at = EPOCH),
        ).state
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = started)

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        // then
        assertEquals(snapshot, decoded)
        assertEquals(started.builds, decoded.state.builds)
        assertEquals(started.eventLog, decoded.state.eventLog)
    }

    @Test
    fun `sub-millisecond instants round trip exactly`() {
        // given — the wall clock carries nanoseconds; truncating them on save would make a
        // reloaded colony differ from the one that was written.
        val precise = Instant.fromEpochSeconds(1_700_000_000, nanosecondAdjustment = 123_456_789)
        val snapshot = GameSnapshot(lastUpdatedAt = precise, state = GameState.initial())

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        // then
        assertEquals(precise, decoded.lastUpdatedAt)
    }

    @Test
    fun `the on-disk shape is pinned`() {
        // given
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial())

        // when
        val encoded = GameSave.encode(snapshot)

        // then — changing this string changes what every already-installed app reads, so it
        // must come with a SCHEMA_VERSION bump and a migration, never as a silent edit.
        assertEquals(
            """{"schemaVersion":3,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
                """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
                """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
                """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
                """"builds":{},"research":{"photovoltaics":0,"extraction":0,"enrichment":0},""" +
                """"activeResearch":null,"returningFleet":null,"eventLog":[]}}""",
            encoded,
        )
    }

    @Test
    fun `events keep their names on disk`() {
        // given
        val state = GameState.initial().copy(
            eventLog = listOf(
                Event.BuildStarted(building = BuildingType.SOLAR_PLANT, toLevel = BuildingLevel(2), at = EPOCH),
                Event.BuildCompleted(building = BuildingType.SOLAR_PLANT, newLevel = BuildingLevel(2), at = EPOCH),
                Event.ResearchStarted(technology = Technology.EXTRACTION, toLevel = TechLevel(1), at = EPOCH),
                Event.ResearchCompleted(technology = Technology.EXTRACTION, newLevel = TechLevel(1), at = EPOCH),
            ),
        )

        // when
        val encoded = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))

        // then
        assertTrue(encoded.contains(""""type":"BuildStarted""""), encoded)
        assertTrue(encoded.contains(""""type":"BuildCompleted""""), encoded)
        assertTrue(encoded.contains(""""type":"ResearchStarted""""), encoded)
        assertTrue(encoded.contains(""""type":"ResearchCompleted""""), encoded)
    }

    @Test
    fun `technologies keep their names on disk`() {
        // given — the research levels are named fields and the enum names key the events
        val state = GameState.initial().researching(Technology.ENRICHMENT, at = EPOCH)

        // when
        val encoded = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))

        // then
        assertTrue(encoded.contains(""""photovoltaics":0"""), encoded)
        assertTrue(encoded.contains(""""technology":"ENRICHMENT""""), encoded)
    }

    @Test
    fun `a colony mid-research survives a round trip with its slot and levels`() {
        // given a colony that has finished one project and started another
        val done = GameState.initial().copy(
            research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(3)),
        )
        val state = done.researching(Technology.ENRICHMENT, at = EPOCH)
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        // then
        assertEquals(snapshot, decoded)
        assertEquals(TechLevel(3), decoded.state.research.extraction)
        assertEquals(state.activeResearch, decoded.state.activeResearch)
    }

    @Test
    fun `research queued before the app closed completes while it is closed`() {
        // given
        val started = GameState.initial().researching(Technology.EXTRACTION, at = EPOCH)
        val completesAt = checkNotNull(started.activeResearch).completesAt

        // when
        val reloaded = assertIs<DecodeResult.Success>(
            GameSave.decode(GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = started))),
        ).snapshot
        val resumed = advance(reloaded.state, from = reloaded.lastUpdatedAt, to = completesAt + 1.minutes)

        // then
        assertEquals(TechLevel(1), resumed.research.extraction)
        assertEquals(null, resumed.activeResearch)
        assertTrue(resumed.eventLog.any { it is Event.ResearchCompleted })
    }

    @Test
    fun `garbage decodes to a failure instead of throwing`() {
        assertIs<DecodeResult.Failure>(GameSave.decode("not json at all"))
        assertIs<DecodeResult.Failure>(GameSave.decode(""))
        assertIs<DecodeResult.Failure>(GameSave.decode("""{"lastUpdatedAt":"1970-01-01T00:00:00Z"}"""))
        assertIs<DecodeResult.Failure>(GameSave.decode("""{"schemaVersion":"not a number"}"""))
    }

    @Test
    fun `a retired version is answered without reading the body`() {
        // A version 1 envelope is retired on its version alone, so a truncated one is reported
        // as Obsolete rather than as garbage. Both start a fresh colony; this pins which answer
        // the version check gives, since it runs before anything is decoded.
        assertIs<DecodeResult.Obsolete>(GameSave.decode("""{"schemaVersion":1}"""))
    }

    @Test
    fun `a truncated save decodes to a failure instead of throwing`() {
        // given — the app was killed mid-write
        val encoded = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial()))

        // when
        val decoded = GameSave.decode(encoded.substring(0, encoded.length / 2))

        // then
        assertIs<DecodeResult.Failure>(decoded)
    }

    @Test
    fun `a save from a newer schema is refused rather than guessed at`() {
        // given
        val encoded = GameSave.encode(
            GameSnapshot(
                schemaVersion = GameSave.SCHEMA_VERSION + 1,
                lastUpdatedAt = EPOCH,
                state = GameState.initial(),
            ),
        )

        // when
        val decoded = GameSave.decode(encoded)

        // then
        assertIs<DecodeResult.Failure>(decoded)
    }

    @Test
    fun `a save that breaks a model invariant decodes to a failure`() {
        // given — hand-edited to a negative building level
        val tampered = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial()))
            .replace(""""metalMine":1""", """"metalMine":-1""")

        // when
        val decoded = GameSave.decode(tampered)

        // then
        assertIs<DecodeResult.Failure>(decoded)
    }

    @Test
    fun `a reloaded colony keeps accruing from the instant it was saved`() {
        // given — the whole point of persistence: the time the app was closed still counts.
        val saved = GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial())
        val reopenedAt = EPOCH + 6.hours

        // when
        val reloaded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(saved))).snapshot
        val resumed = advance(reloaded.state, from = reloaded.lastUpdatedAt, to = reopenedAt)

        // then
        assertEquals(advance(saved.state, from = EPOCH, to = reopenedAt), resumed)
        assertTrue(resumed.resources.metal > 0)
    }

    @Test
    fun `a build queued before the app closed completes while it is closed`() {
        // given
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded(BuildingType.METAL_MINE), BuildingType.METAL_MINE, at = EPOCH),
        ).state
        val completesAt = checkNotNull(started.builds[BuildingType.METAL_MINE]).completesAt

        // when
        val reloaded = assertIs<DecodeResult.Success>(
            GameSave.decode(GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = started))),
        ).snapshot
        val resumed = advance(reloaded.state, from = reloaded.lastUpdatedAt, to = completesAt + 1.minutes)

        // then
        assertEquals(BuildingLevel(2), resumed.buildings.metalMine)
        assertTrue(resumed.builds.isEmpty())
        assertTrue(resumed.eventLog.any { it is Event.BuildCompleted })
    }

    @Test
    fun `a fleet in flight survives a round trip with its origin and manifest`() {
        // given
        val state = GameState.initial().copy(returningFleet = fleet(arrivesAt = EPOCH + 4.hours))
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        // then
        assertEquals(state.returningFleet, decoded.state.returningFleet)
    }

    @Test
    fun `a fleet that landed while the app was closed has unloaded on reopening`() {
        // given a fleet carrying metal, arriving an hour after the save
        val arrivesAt = EPOCH + 1.hours
        val state = GameState.initial().copy(returningFleet = fleet(arrivesAt = arrivesAt))

        // when
        val reloaded = assertIs<DecodeResult.Success>(
            GameSave.decode(GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))),
        ).snapshot
        val resumed = advance(reloaded.state, from = reloaded.lastUpdatedAt, to = arrivesAt + 1.minutes)

        // then
        assertEquals(null, resumed.returningFleet)
        assertTrue(resumed.eventLog.any { it is Event.FleetReturned })
        assertTrue(resumed.resources.metal >= CARGO_METAL)
    }

    @Test
    fun `ship types keep their names on disk`() {
        // given — the manifest is a map keyed by enum, so the constant names are on-disk keys
        val state = GameState.initial().copy(returningFleet = fleet(arrivesAt = EPOCH + 1.hours))

        // when
        val encoded = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))

        // then
        assertTrue(encoded.contains(""""CARGO":14"""), encoded)
        assertTrue(encoded.contains(""""CRUISER":1"""), encoded)
    }

    @Test
    fun `a save with an impossible fleet decodes to a failure`() {
        // given — hand-edited to a negative ship count, which ReturningFleet forbids
        val state = GameState.initial().copy(returningFleet = fleet(arrivesAt = EPOCH + 1.hours))
        val tampered = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))
            .replace(""""CARGO":14""", """"CARGO":-14""")

        // when / then
        assertIs<DecodeResult.Failure>(GameSave.decode(tampered))
    }

    @Test
    fun `the version 1 fixture is the string 0_0_7 actually wrote`() {
        // The reset is only worth as much as the fixture that triggers it, so the fixture is not
        // written from memory: this is byte-for-byte the string 0.0.7 pinned in `the on-disk
        // shape is pinned` (git ecbe518), with the stock of a colony not yet opened. If a future
        // edit has to change it, it is not a 0.0.7 save any more and this stops proving anything.
        assertEquals(
            """{"schemaVersion":1,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
                """"resources":{"metalFine":0,"crystalFine":0,"deuteriumFine":0},""" +
                """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
                """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
                """"buildQueue":null,"returningFleet":null,"eventLog":[]}}""",
            VERSION_1_IDLE,
        )
    }

    @Test
    fun `a version 1 save is retired rather than carried forward`() {
        // when
        val decoded = GameSave.decode(VERSION_1_IDLE)

        // then — the rebalance is too deep for a colony grown at the old rates to survive it
        assertEquals(1, assertIs<DecodeResult.Obsolete>(decoded).schemaVersion)
    }

    @Test
    fun `a played version 1 colony is retired however far it got`() {
        // given a save with levelled buildings, a stock, a fleet inbound and an event log

        // when
        val decoded = GameSave.decode(VERSION_1_FULL)

        // then — nothing about how much was built earns an exemption
        assertIs<DecodeResult.Obsolete>(decoded)
    }

    @Test
    fun `a version 1 save mid-build is retired with its queue`() {
        // when / then
        assertIs<DecodeResult.Obsolete>(GameSave.decode(VERSION_1_BUILDING))
    }

    @Test
    fun `a retired save says why so the player can be told`() {
        // when
        val obsolete = assertIs<DecodeResult.Obsolete>(GameSave.decode(VERSION_1_IDLE))

        // then — the reason is the payload; a bare failure would leave nothing to show
        assertTrue(obsolete.reason.isNotBlank())
        assertTrue(obsolete.reason.contains("rebalance"), obsolete.reason)
    }

    @Test
    fun `a retired save is not confused with a broken one`() {
        // A reset the player was promised and a reset caused by a corrupt file are different
        // events, and only one of them is worth explaining. Both start a fresh colony, so the
        // types are the only thing keeping them apart.
        assertIs<DecodeResult.Failure>(GameSave.decode("not json at all"))
        assertIs<DecodeResult.Failure>(GameSave.decode(""))
        assertIs<DecodeResult.Failure>(GameSave.decode(VERSION_1_IDLE.replace(""""schemaVersion":1""", """"schemaVersion":9""")))
        assertIs<DecodeResult.Obsolete>(GameSave.decode(VERSION_1_IDLE))
    }

    @Test
    fun `the current format still loads so the reset is version 1 only`() {
        // given — the retirement must not take the current format with it
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial())

        // when
        val decoded = GameSave.decode(GameSave.encode(snapshot))

        // then
        assertEquals(snapshot, assertIs<DecodeResult.Success>(decoded).snapshot)
    }

    @Test
    fun `the version 2 fixture is the string 0_0_11 actually wrote`() {
        // The migration is only worth as much as the save that triggers it, so the fixture is not
        // written from memory: this is byte-for-byte the string 0.0.11 pinned in `the on-disk
        // shape is pinned`, with the stock of a colony not yet opened.
        assertEquals(
            """{"schemaVersion":2,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
                """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
                """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
                """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
                """"builds":{},"returningFleet":null,"eventLog":[]}}""",
            VERSION_2_IDLE,
        )
    }

    @Test
    fun `a version 2 colony is carried forward rather than reset`() {
        // Research is purely additive, so unlike the 0.0.8 rebalance there is nothing about the
        // old save that the new rules make unplayable. Davide's call, 2026-08-06.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_2_IDLE)).snapshot

        // then
        assertEquals(GameSave.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(Research.initial(), decoded.state.research)
        assertEquals(null, decoded.state.activeResearch)
    }

    @Test
    fun `a played version 2 colony keeps everything it had`() {
        // given a save with levelled buildings, a stock, a build running, a fleet inbound and an
        // event log — everything the migration must carry across untouched

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_2_FULL)).snapshot

        // then
        assertEquals(BuildingLevel(4), decoded.state.buildings.metalMine)
        assertEquals(500L, decoded.state.resources.metal)
        assertEquals(BuildingLevel(5), decoded.state.builds.getValue(BuildingType.METAL_MINE).toLevel)
        assertEquals(1, decoded.state.eventLog.size)
        assertEquals(
            Coordinates(galaxy = 2, system = 117, position = 9),
            checkNotNull(decoded.state.returningFleet).origin,
        )
        assertEquals(Research.initial(), decoded.state.research)
    }

    @Test
    fun `a migrated colony keeps accruing from the instant it was saved`() {
        // The point of migrating rather than retiring: the colony is still the same colony.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_2_IDLE)).snapshot

        // when
        val resumed = advance(decoded.state, from = decoded.lastUpdatedAt, to = EPOCH + 6.hours)

        // then
        assertEquals(advance(GameState.initial(), from = EPOCH, to = EPOCH + 6.hours), resumed)
    }

    @Test
    fun `a migrated colony can start research immediately`() {
        // given a version 2 save whose Robotics Factory already clears the gate, with the 300 /
        // 150 / 100 a first Photovoltaics costs
        val readyToResearch = VERSION_2_IDLE
            .replace(""""roboticsFactory":0""", """"roboticsFactory":1""")
            .replace(""""deuteriumFine":0""", """"deuteriumFine":3600000000""")
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(readyToResearch)).snapshot

        // when
        val started = startResearch(decoded.state, Technology.PHOTOVOLTAICS, at = EPOCH)

        // then — the migrated slot is empty, not absent
        assertEquals(TechLevel(1), assertIs<StartResearchResult.Started>(started).state.project().toLevel)
    }

    @Test
    fun `a migrated save re-encodes at the current version`() {
        // given
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_2_IDLE)).snapshot

        // when — the shell writes the snapshot back on the first commit after loading
        val rewritten = GameSave.encode(decoded)

        // then — and from then on it is a version 3 save like any other
        assertTrue(rewritten.startsWith("""{"schemaVersion":3"""), rewritten)
        assertEquals(decoded, assertIs<DecodeResult.Success>(GameSave.decode(rewritten)).snapshot)
    }

    private fun fleet(arrivesAt: Instant): ReturningFleet = ReturningFleet(
        ships = mapOf(ShipType.CARGO to 14, ShipType.CRUISER to 1),
        cargo = Resources.of(metal = CARGO_METAL),
        origin = Coordinates(galaxy = 2, system = 117, position = 9),
        arrivesAt = arrivesAt,
    )

    private fun funded(building: BuildingType): GameState {
        val cost = PlaceholderBalance.upgradeCost(building, BuildingLevel(2))
        return GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal, deuterium = cost.deuterium),
        )
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
        const val CARGO_METAL = 500L

        // Frozen captures of the 0.0.7 on-disk format — the saves already sitting on installed
        // builds. Read them, never rewrite them: an edit here silences the migration tests
        // instead of fixing them.
        const val VERSION_1_IDLE = """{"schemaVersion":1,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":0,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"buildQueue":null,"returningFleet":null,"eventLog":[]}}"""

        const val VERSION_1_BUILDING = """{"schemaVersion":1,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":0,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"buildQueue":{"building":"METAL_MINE","toLevel":2,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T00:20:00Z"},""" +
            """"returningFleet":null,"eventLog":[]}}"""

        // Frozen captures of the 0.0.11 on-disk format — the saves sitting on the builds that
        // shipped between 0.0.8 and 0.0.11. Read them, never rewrite them.
        const val VERSION_2_IDLE = """{"schemaVersion":2,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"builds":{},"returningFleet":null,"eventLog":[]}}"""

        // A version 2 colony that had actually been played: levelled buildings, a stock, a job in
        // the parallel build map, a fleet on its way home and an event log — everything the
        // migration must carry across untouched.
        const val VERSION_2_FULL = """{"schemaVersion":2,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":4,"crystalMine":3,"deuteriumSynthesizer":1,""" +
            """"solarPlant":2,"roboticsFactory":0,"naniteFactory":0},""" +
            """"builds":{"METAL_MINE":{"building":"METAL_MINE","toLevel":5,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T00:50:00Z"}},""" +
            """"returningFleet":{"ships":{"CARGO":14},""" +
            """"cargo":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
            """"origin":{"galaxy":2,"system":117,"position":9},"arrivesAt":"1970-01-01T01:00:00Z"},""" +
            """"eventLog":[{"type":"BuildStarted","building":"METAL_MINE","toLevel":5,""" +
            """"at":"1970-01-01T00:00:00Z"}]}}"""

        // A colony that had actually been played: levelled buildings, a stock, a fleet on its
        // way home and an event log — everything the migration must carry across untouched.
        const val VERSION_1_FULL = """{"schemaVersion":1,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":4,"crystalMine":3,"deuteriumSynthesizer":1,""" +
            """"solarPlant":2,"roboticsFactory":0,"naniteFactory":0},""" +
            """"buildQueue":{"building":"METAL_MINE","toLevel":5,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T00:50:00Z"},""" +
            """"returningFleet":{"ships":{"CARGO":14},""" +
            """"cargo":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
            """"origin":{"galaxy":2,"system":117,"position":9},"arrivesAt":"1970-01-01T01:00:00Z"},""" +
            """"eventLog":[{"type":"BuildStarted","building":"METAL_MINE","toLevel":5,""" +
            """"at":"1970-01-01T00:00:00Z"}]}}"""
    }
}
