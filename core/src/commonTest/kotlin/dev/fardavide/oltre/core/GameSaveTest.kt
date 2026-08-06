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
            """{"schemaVersion":2,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
                """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
                """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
                """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
                """"builds":{},"returningFleet":null,"eventLog":[]}}""",
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
            ),
        )

        // when
        val encoded = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))

        // then
        assertTrue(encoded.contains(""""type":"BuildStarted""""), encoded)
        assertTrue(encoded.contains(""""type":"BuildCompleted""""), encoded)
    }

    @Test
    fun `garbage decodes to a failure instead of throwing`() {
        assertIs<DecodeResult.Failure>(GameSave.decode("not json at all"))
        assertIs<DecodeResult.Failure>(GameSave.decode(""))
        // a version 1 envelope with no state migrates cleanly and still fails to decode
        assertIs<DecodeResult.Failure>(GameSave.decode("""{"schemaVersion":1}"""))
        assertIs<DecodeResult.Failure>(GameSave.decode("""{"lastUpdatedAt":"1970-01-01T00:00:00Z"}"""))
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
    fun `a version 1 save migrates its single build slot into the per-facility map`() {
        // given a save written by 0.0.7, when one facility at a time could build
        val version1 = """{"schemaVersion":1,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"buildQueue":{"building":"METAL_MINE","toLevel":2,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T00:20:00Z"},""" +
            """"returningFleet":null,"eventLog":[]}}"""

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(version1)).snapshot

        // then the queued job keeps its identity, keyed by the facility it was raising
        assertEquals(
            mapOf(
                BuildingType.METAL_MINE to BuildJob(
                    building = BuildingType.METAL_MINE,
                    toLevel = BuildingLevel(2),
                    startedAt = EPOCH,
                    completesAt = EPOCH + 20.minutes,
                ),
            ),
            decoded.state.builds,
        )
        assertEquals(SCHEMA_VERSION_CURRENT, decoded.schemaVersion)
    }

    @Test
    fun `a version 1 save with nothing building migrates to an empty build map`() {
        // given
        val version1 = """{"schemaVersion":1,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":0,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"buildQueue":null,"returningFleet":null,"eventLog":[]}}"""

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(version1)).snapshot

        // then
        assertTrue(decoded.state.builds.isEmpty())
    }

    @Test
    fun `a migrated colony keeps building through the reload`() {
        // given a version 1 save whose build lands 20 minutes after it was written
        val version1 = """{"schemaVersion":1,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":0,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"buildQueue":{"building":"METAL_MINE","toLevel":2,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T00:20:00Z"},""" +
            """"returningFleet":null,"eventLog":[]}}"""

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(version1)).snapshot
        val resumed = advance(decoded.state, from = decoded.lastUpdatedAt, to = EPOCH + 21.minutes)

        // then
        assertEquals(BuildingLevel(2), resumed.buildings.metalMine)
        assertTrue(resumed.builds.isEmpty())
        assertTrue(resumed.eventLog.any { it is Event.BuildCompleted })
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
        const val SCHEMA_VERSION_CURRENT = 2
        const val CARGO_METAL = 500L
    }
}
