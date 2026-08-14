package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            """{"schemaVersion":12,"lastUpdatedAt":"1970-01-01T00:00:00Z","debugUsed":false,"state":{""" +
                """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
                """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
                """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
                """"builds":{},"research":{"photovoltaics":0,"extraction":0,"enrichment":0,""" +
                """"prospecting":0,""" +
                // The three adaptation ladders, in the same record as the three applied
                // technologies: what the empire knows is one thing however it was learned.
                """"thermal":0,"gravitic":0,"atmospheric":0},""" +
                // Two fields, one slot — at most one of them is ever anything but null.
                """"activeResearch":null,"activeAdaptation":null,""" +
                // The whole galaxy, in one line: a seed, where home is, the handful of worlds the
                // home system holds, and who owns what. Four thousand seven hundred worlds of
                // traits are absent on purpose — they are regenerated from that seed.
                //
                // **The home coordinate moved at 0.5.1 and the schema did not**, which is the one
                // case this pin has to be able to tell apart from a format break. `home` and
                // `surveyed` are *content* genesis computes, not keys: a rule that starts a colony
                // in a different system rewrites these two lines and changes nothing any installed
                // build can or cannot read. Nobody's map moved either — home has been stored since
                // schema 4 and no migration recomputes it, so an already-founded colony keeps the
                // system it was founded in. The frozen `VERSION_*` fixtures below still carry
                // [3:165:7] for exactly that reason, and must never be rewritten to agree with
                // this one.
                """"galaxy":{"seed":20260807,"home":{"galaxy":3,"system":171,"slot":7},""" +
                """"surveyed":[{"galaxy":3,"system":171,"slot":1},{"galaxy":3,"system":171,"slot":2},""" +
                """{"galaxy":3,"system":171,"slot":4},{"galaxy":3,"system":171,"slot":7},""" +
                """{"galaxy":3,"system":171,"slot":8},{"galaxy":3,"system":171,"slot":10},""" +
                """{"galaxy":3,"system":171,"slot":11}],""" +
                """"ownership":[{"at":{"galaxy":3,"system":171,"slot":7},"holder":"player"}],""" +
                """"deposits":[],"pinned":[]},""" +
                // Probes in flight. Empty at genesis, and the only key schema 6 added — what a
                // survey writes to is `galaxy.surveyed` above, which has been there since 4.
                """"surveys":[],""" +
                // The fleet, in the two keys schema 8 traded `returningFleet` for: the idle pool,
                // which opens holding the one granted skiff, and the runs in flight, which at
                // genesis are none.
                """"ships":{"counts":{"SKIFF":1}},"runs":[],""" +
                // The slipway, which schema 10 added as one hop. Empty at genesis and on every
                // colony saved before hulls took time to make — every one of those was handed its
                // hull in the same call it paid for it, so there is nothing on disk to fold in.
                """"yard":[],""" +
                // The square, which schema 9 added as one hop: the row whose price is watched, and
                // the jobs whose landing was asked about. Both empty at genesis and on every colony
                // that has never tapped a bell. Neither is a job — they schedule nothing and
                // `advance` applies neither.
                """"watching":null,"subscribed":[],"eventLog":[]}}""",
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
                // The newest discriminator, and the first one a real player's save can carry from
                // the day it ships — every hull bought writes one, where no production build has
                // ever appended a `FleetReturned` with the old `CARGO` name in it.
                Event.ShipsBuilt(ships = Ships.of(ShipType.SKIFF, 1), at = EPOCH),
            ),
        )

        // when
        val encoded = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))

        // then
        assertTrue(encoded.contains(""""type":"BuildStarted""""), encoded)
        assertTrue(encoded.contains(""""type":"BuildCompleted""""), encoded)
        assertTrue(encoded.contains(""""type":"ResearchStarted""""), encoded)
        assertTrue(encoded.contains(""""type":"ResearchCompleted""""), encoded)
        assertTrue(encoded.contains(""""type":"ShipsBuilt""""), encoded)
        assertTrue(encoded.contains(""""ships":{"counts":{"SKIFF":1}}"""), encoded)
    }

    @Test
    fun `a colony with hulls on the slipway survives a round trip`() {
        // The queue is what a purchase moves now, and it is the schema-10 key. Two hulls rather than
        // one, so the round trip carries the *chaining* as well as the shape: a decode that lost the
        // order, or rounded an instant, would land on `GameState.init`'s serial rule rather than on
        // an assertion here.
        val state = assertIs<BuildShipsResult.Started>(
            buildShips(
                GameState.initial().copy(resources = Resources.of(metal = 100_000, crystal = 100_000)),
                Ships.of(ShipType.SKIFF, 2),
                at = EPOCH,
            ),
        ).state
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        assertEquals(snapshot, decoded)
        assertEquals(listOf(ShipType.SKIFF, ShipType.SKIFF), decoded.state.yard.map { it.ship })
        assertEquals(Ships.of(ShipType.SKIFF, 1), decoded.state.ships)
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
    fun `a run in flight survives a round trip with its target and manifest`() {
        // given
        val state = GameState.initial().copy(runs = listOf(fleetRun(returnsAt = EPOCH + 4.hours)))
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        // then
        assertEquals(state.runs, decoded.state.runs)
    }

    @Test
    fun `an idle pool and the runs in flight both survive a round trip`() {
        // The schema 8 round trip, and it has to be both halves at once: the fleet a player owns is
        // the pool plus every run's manifest, so a save that carried one and dropped the other would
        // hand back a smaller fleet than it was given without failing to decode.
        val state = GameState.initial().copy(
            ships = Ships(mapOf(ShipType.SKIFF to 3, ShipType.HAULER to 2)),
            runs = listOf(fleetRun(returnsAt = EPOCH + 4.hours), fleetRun(returnsAt = EPOCH + 9.hours)),
        )
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        // then
        assertEquals(snapshot, decoded)
        assertEquals(GameSave.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(Ships(mapOf(ShipType.SKIFF to 3, ShipType.HAULER to 2)), decoded.state.ships)
        assertEquals(2, decoded.state.runs.size)
    }

    @Test
    fun `a fleet that landed while the app was closed has unloaded on reopening`() {
        // given a run carrying metal, returning an hour after the save
        val returnsAt = EPOCH + 1.hours
        val state = GameState.initial().copy(runs = listOf(fleetRun(returnsAt = returnsAt)))

        // when
        val reloaded = assertIs<DecodeResult.Success>(
            GameSave.decode(GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))),
        ).snapshot
        val resumed = advance(reloaded.state, from = reloaded.lastUpdatedAt, to = returnsAt + 1.minutes)

        // then — the hold is credited and the hulls are back in the pool
        assertEquals(emptyList(), resumed.runs)
        assertTrue(resumed.eventLog.any { it is Event.FleetReturned })
        assertTrue(resumed.resources.metal >= CARGO_METAL)
        assertEquals(Ships(mapOf(ShipType.SKIFF to 15, ShipType.HAULER to 1)), resumed.ships)
    }

    @Test
    fun `ship types keep their names on disk`() {
        // given — the manifest is a map keyed by enum, so the constant names are on-disk keys
        val state = GameState.initial().copy(runs = listOf(fleetRun(returnsAt = EPOCH + 1.hours)))

        // when
        val encoded = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))

        // then
        assertTrue(encoded.contains(""""SKIFF":14"""), encoded)
        assertTrue(encoded.contains(""""HAULER":1"""), encoded)
    }

    @Test
    fun `a save with an impossible fleet decodes to a failure`() {
        // given — hand-edited to a negative ship count, which `Ships` forbids
        val state = GameState.initial().copy(runs = listOf(fleetRun(returnsAt = EPOCH + 1.hours)))
        val tampered = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = state))
            .replace(""""SKIFF":14""", """"SKIFF":-14""")

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

        // then — the 500 metal it had plus the 500 the inbound fleet was carrying. The 7 -> 8 hop
        // drops the fleet and credits its hold: the old `origin` is a position and the new `target`
        // is a slot, so folding it into a run would have to invent both a target and a `gathering`
        // the save never recorded.
        assertEquals(BuildingLevel(4), decoded.state.buildings.metalMine)
        assertEquals(1_000L, decoded.state.resources.metal)
        assertEquals(BuildingLevel(5), decoded.state.builds.getValue(BuildingType.METAL_MINE).toLevel)
        assertEquals(1, decoded.state.eventLog.size)
        assertEquals(emptyList(), decoded.state.runs)
        assertEquals(Ships.of(ShipType.SKIFF, 1), decoded.state.ships)
        assertEquals(Research.initial(), decoded.state.research)
    }

    @Test
    fun `a migrated colony keeps accruing from the instant it was saved`() {
        // The point of migrating rather than retiring: the colony is still the same colony.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_2_IDLE)).snapshot

        // when
        val resumed = advance(decoded.state, from = decoded.lastUpdatedAt, to = EPOCH + 6.hours)

        // then — everything the clock touches, which is the whole claim. The galaxy is compared
        // separately below rather than here: a migrated save mints its seed from its own
        // `lastUpdatedAt`, so it is deliberately not the seed a fresh colony gets.
        val fresh = advance(GameState.initial(), from = EPOCH, to = EPOCH + 6.hours)
        assertEquals(fresh.resources, resumed.resources)
        assertEquals(fresh.buildings, resumed.buildings)
        assertEquals(fresh.builds, resumed.builds)
        assertEquals(fresh.eventLog, resumed.eventLog)
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

        // then — and from then on it is a current save like any other. Written against
        // `SCHEMA_VERSION` rather than a literal: the claim is "the version it was migrated to",
        // and a literal here has to be edited by every hop that has nothing to do with it.
        assertTrue(rewritten.startsWith("""{"schemaVersion":${GameSave.SCHEMA_VERSION}"""), rewritten)
        assertEquals(decoded, assertIs<DecodeResult.Success>(GameSave.decode(rewritten)).snapshot)
    }

    @Test
    fun `a colony saved before the debug menu existed did not use it`() {
        // The 6 -> 7 hop is the identity function, so this is the test that it is the *right*
        // identity function: the key is absent from every save ever written by an older build, and
        // the value that stands in for it has to be the one that is true of all of them.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_6_IDLE)).snapshot

        assertEquals(GameSave.SCHEMA_VERSION, decoded.schemaVersion)
        assertFalse(decoded.debugUsed)
    }

    @Test
    fun `a colony the debug menu touched says so on disk and still says so after a reload`() {
        // given
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, debugUsed = true, state = GameState.initial())

        // when
        val encoded = GameSave.encode(snapshot)

        // then — the flag is the whole point of the hop, so it has to survive the round trip that
        // every other field is pinned against
        assertTrue(encoded.contains(""""debugUsed":true"""), encoded)
        assertTrue(assertIs<DecodeResult.Success>(GameSave.decode(encoded)).snapshot.debugUsed)
    }

    @Test
    fun `the flag is about the save and not about the simulation`() {
        // Stated as a test because it is the argument for putting the flag on the envelope: two
        // colonies that differ only in whether the menu touched them are the same colony to
        // `advance`, and nothing downstream may start branching on it.
        val played = GameSnapshot(lastUpdatedAt = EPOCH, debugUsed = false, state = GameState.initial())
        val debugged = played.copy(debugUsed = true)

        assertEquals(played.state, debugged.state)
        assertEquals(
            advance(played.state, from = EPOCH, to = EPOCH + 6.hours),
            advance(debugged.state, from = EPOCH, to = EPOCH + 6.hours),
        )
    }

    @Test
    fun `the version 3 fixture is the string 0_0_14 actually wrote`() {
        // Byte-for-byte the string 0.0.14 pinned in `the on-disk shape is pinned`, with the stock of
        // a colony not yet opened. A fixture written from memory would only prove that made-up JSON
        // migrates, which is the one thing nobody needs to know.
        assertEquals(
            """{"schemaVersion":3,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
                """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
                """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
                """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
                """"builds":{},"research":{"photovoltaics":0,"extraction":0,"enrichment":0},""" +
                """"activeResearch":null,"returningFleet":null,"eventLog":[]}}""",
            VERSION_3_IDLE,
        )
    }

    @Test
    fun `a version 3 colony is carried forward rather than reset`() {
        // The galaxy is purely additive, exactly as research was: a colony saved before the map
        // existed has surveyed nothing and holds nothing but its own home world, which is what a
        // fresh GalaxyState says. So there is no number to invent and nothing to rescale.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_3_IDLE)).snapshot

        assertEquals(GameSave.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(EmpireId.PLAYER, decoded.state.galaxy.holderOf(decoded.state.galaxy.home))
    }

    @Test
    fun `a migrated colony wakes up with its own home system surveyed and nothing else`() {
        // Two tiers, from the first launch: charted everywhere, surveyed only at home. That is what
        // makes the screen browsable on the day it ships without pretending fleets exist.
        val galaxy = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_3_IDLE)).snapshot.state.galaxy

        assertTrue(galaxy.home in galaxy.surveyed, "home must be surveyed")
        assertTrue(
            galaxy.surveyed.all { it.galaxy == galaxy.home.galaxy && it.system == galaxy.home.system },
            "nothing outside the home system may start surveyed, was ${galaxy.surveyed}",
        )
        // and every surveyed coordinate really holds a world
        assertTrue(galaxy.surveyed.all { worldAt(galaxy.seed, it) != null })
    }

    @Test
    fun `the same version 3 save migrates to the same galaxy every time`() {
        // The load-bearing property of minting the seed from the save's own contents rather than
        // from a clock: a player who reopens the app before the first commit is written must not be
        // handed a different map each time.
        val once = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_3_IDLE)).snapshot.state.galaxy
        val again = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_3_IDLE)).snapshot.state.galaxy

        assertEquals(once, again)
    }

    @Test
    fun `two version 3 saves from different instants get different galaxies`() {
        // and the flip side: the seed still varies, so two players do not share a map.
        val mine = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_3_IDLE)).snapshot.state.galaxy
        val theirs = assertIs<DecodeResult.Success>(
            GameSave.decode(VERSION_3_IDLE.replace("1970-01-01T00:00:00Z", "1999-12-31T23:59:59Z")),
        ).snapshot.state.galaxy

        assertTrue(mine.seed != theirs.seed, "two save instants must not mint the same seed")
    }

    @Test
    fun `a played version 3 colony keeps everything it had`() {
        // given a save with levelled buildings, a stock, a build running, research done and running,
        // a fleet inbound and an event log — everything the migration must carry across untouched

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_3_FULL)).snapshot

        // then
        assertEquals(BuildingLevel(4), decoded.state.buildings.metalMine)
        // 500 in the store plus the 500 the 7 -> 8 hop credited from the inbound fleet's hold
        assertEquals(1_000L, decoded.state.resources.metal)
        assertEquals(BuildingLevel(5), decoded.state.builds.getValue(BuildingType.METAL_MINE).toLevel)
        assertEquals(TechLevel(3), decoded.state.research.extraction)
        assertEquals(Technology.EXTRACTION, checkNotNull(decoded.state.activeResearch).technology)
        assertEquals(1, decoded.state.eventLog.size)
        assertEquals(emptyList(), decoded.state.runs)
        assertEquals(Ships.of(ShipType.SKIFF, 1), decoded.state.ships)
    }

    @Test
    fun `the version 4 fixture is the string 0_0_16 actually wrote`() {
        // Byte-for-byte the string 0.0.16 pinned in `the on-disk shape is pinned`, with the stock of
        // a colony not yet opened. A fixture written from memory would only prove that made-up JSON
        // migrates, which is the one thing nobody needs to know.
        assertEquals(
            """{"schemaVersion":4,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
                """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
                """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
                """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
                """"builds":{},"research":{"photovoltaics":0,"extraction":0,"enrichment":0},""" +
                """"activeResearch":null,""" +
                """"galaxy":{"seed":20260807,"home":{"galaxy":3,"system":165,"slot":7},""" +
                """"surveyed":[{"galaxy":3,"system":165,"slot":7},{"galaxy":3,"system":165,"slot":8},""" +
                """{"galaxy":3,"system":165,"slot":10},{"galaxy":3,"system":165,"slot":13}],""" +
                """"ownership":[{"at":{"galaxy":3,"system":165,"slot":7},"holder":"player"}]},""" +
                """"returningFleet":null,"eventLog":[]}}""",
            VERSION_4_IDLE,
        )
    }

    @Test
    fun `a version 4 colony is carried forward with three ladders at zero and an empty slot`() {
        // The adaptation branch is additive in exactly the sense research and the galaxy were: an
        // empire saved before the ladders existed has climbed none of them and has nothing running.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_4_IDLE)).snapshot

        assertEquals(GameSave.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(AdaptationLevels.NONE, decoded.state.research.adaptationLevels())
        assertEquals(null, decoded.state.activeAdaptation)
    }

    @Test
    fun `the 4 to 5 hop adds to the research record rather than replacing it`() {
        // The failure this exists to catch: encoding a fresh `Research` into the migrated save
        // would carry the three new ladders across and silently reset the two levels the player
        // actually earned. Unlike the 2 -> 3 hop, `research` already exists at version 4.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_4_FULL)).snapshot

        assertEquals(TechLevel(2), decoded.state.research.photovoltaics)
        assertEquals(TechLevel(3), decoded.state.research.extraction)
        assertEquals(AdaptationLevels.NONE, decoded.state.research.adaptationLevels())
    }

    @Test
    fun `a played version 4 colony keeps everything it had including the map`() {
        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_4_FULL)).snapshot

        // then
        assertEquals(BuildingLevel(4), decoded.state.buildings.metalMine)
        // 500 in the store plus the 500 the 7 -> 8 hop credited from the inbound fleet's hold
        assertEquals(1_000L, decoded.state.resources.metal)
        assertEquals(BuildingLevel(5), decoded.state.builds.getValue(BuildingType.METAL_MINE).toLevel)
        assertEquals(Technology.EXTRACTION, checkNotNull(decoded.state.activeResearch).technology)
        assertEquals(GalaxySeed(20_260_807), decoded.state.galaxy.seed)
        assertEquals(GalaxyCoordinate(galaxy = 3, system = 165, slot = 7), decoded.state.galaxy.home)
        assertEquals(4, decoded.state.galaxy.surveyed.size)
        assertEquals(1, decoded.state.eventLog.size)
    }

    @Test
    fun `a version 4 save whose slot was busy still holds one project after the hop`() {
        // The invariant the 4 -> 5 hop could break if it invented an adaptation job: version 4 has
        // an applied project running, so the migrated state must leave the other field empty or
        // `GameState.init` refuses to build it at all.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_4_FULL)).snapshot

        assertEquals(
            checkNotNull(decoded.state.activeResearch).completesAt,
            decoded.state.researchSlotFreesAt,
        )
    }

    @Test
    fun `a version 5 colony is carried forward with no probes in flight`() {
        // The fourth additive hop, and the shallowest: an empire saved before the verb existed has
        // dispatched nothing, which is what an empty list says.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_5_FULL)).snapshot

        assertEquals(GameSave.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(emptyList(), decoded.state.surveys)
    }

    @Test
    fun `the 5 to 6 hop keeps the map the player had already surveyed`() {
        // The failure this exists to catch: `surveyed` is what a probe writes to, so a hop that
        // introduced the verb by minting a fresh `GalaxyState` would delete the very thing the
        // verb produces — and unlike a reset research level, nothing in the game can earn it back
        // except by paying again for information the player already owned.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_5_FULL)).snapshot

        assertEquals(GalaxySeed(20_260_807), decoded.state.galaxy.seed)
        assertEquals(GalaxyCoordinate(galaxy = 3, system = 165, slot = 7), decoded.state.galaxy.home)
        assertEquals(4, decoded.state.galaxy.surveyed.size)
        assertEquals(TechLevel(3), decoded.state.research.gravitic)
    }

    @Test
    fun `a colony saved before worlds could run dry wakes up with every vein full`() {
        // The 9 -> 10 hop is additive and writes an empty list — which is not a placeholder but the
        // statement itself, because an absent deposit entry *is* a full world. The test that matters
        // is therefore not that the key arrived but that the map reads full through it.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_5_FULL)).snapshot
        val galaxy = decoded.state.galaxy
        val worked = galaxy.surveyed.first { it != galaxy.home }

        assertEquals(emptyList(), galaxy.deposits)
        assertEquals(
            galaxy.depositCap(worked, ResourceKind.METAL),
            galaxy.remaining(worked, ResourceKind.METAL, EPOCH),
        )
    }

    @Test
    fun `a colony saved before the fleet existed wakes up with one skiff and nothing out`() {
        // The 7 -> 8 hop is the only one in the table that grants something rather than writing the
        // truthful zero, and it is deliberate: nothing in this slice can *buy* a hull, so an empty
        // pool would hand an existing colony a verb it could never use. One skiff is what a colony
        // founded a moment later gets.
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_6_IDLE)).snapshot

        assertEquals(GameSave.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(Ships.of(ShipType.SKIFF, 1), decoded.state.ships)
        assertEquals(emptyList(), decoded.state.runs)
    }

    @Test
    fun `the 7 to 8 hop rewrites the fleet entries already in the event log`() {
        // given a legacy save whose log already holds a `FleetReturned` in the old shape — a bare
        // `Map<ShipType, Int>` under `ships`, a `CARGO` key and no `from` at all. Not a frozen
        // capture: no shipped build ever wrote one, because nothing outside test code ever
        // constructed a `ReturningFleet`. It is here because the rewrite is total and has to be —
        // an entry left in the old shape decodes as an unknown enum constant and takes the whole
        // save down with it.
        val logged = VERSION_6_IDLE.replace(
            """"eventLog":[]""",
            """"eventLog":[{"type":"FleetReturned","ships":{"CARGO":14},""" +
                """"cargo":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
                """"at":"1970-01-01T01:00:00Z"}]""",
        )

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(logged)).snapshot

        // then — the hull is renamed and the coordinate is honestly unknown rather than invented
        val returned = assertIs<Event.FleetReturned>(decoded.state.eventLog.single())
        assertEquals(Ships.of(ShipType.SKIFF, 14), returned.ships)
        assertEquals(null, returned.from)
        assertEquals(500L, returned.cargo.metal)
    }

    @Test
    fun `a colony saved before hulls took time wakes up with an empty slipway`() {
        // The 9 -> 10 hop, and the truthful zero rather than the 7 -> 8 hop's deliberate gift: every
        // hull that colony ever bought was handed over in the same call it paid for, so there is
        // nothing in flight to fold in and nothing to invent.
        //
        // Built by taking the keys back out of a current save rather than frozen, because a
        // schema-9 save is exactly a current one without them — both hops add keys and rewrite
        // nothing. **Three keys rather than one since 0.10.0**: schema 11 landed deposits and the
        // fourth technology's level on top of the yard, so a fixture that only removed `yard` would
        // be a schema-10 save wearing a 9, and would exercise one hop instead of two. **Four since
        // 0.11.0**, which landed pins — and note what keeps this honest: the version this subtracts
        // *from* has to move with `SCHEMA_VERSION`, or the replace silently stops matching and the
        // fixture is a current save claiming to be old.
        val beforeTheYard = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial()))
            .replace(""""schemaVersion":12""", """"schemaVersion":9""")
            .replace(""""yard":[],""", "")
            .replace(""","deposits":[]""", "")
            .replace(""","pinned":[]""", "")
            .replace(""""prospecting":0,""", "")

        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(beforeTheYard)).snapshot

        assertEquals(GameSave.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(emptyList(), decoded.state.yard)
        // And the fleet it already owned is untouched. The hull base went up tenfold in the version
        // that added this key, and a migration that re-priced a fleet the player earned under the
        // old rules would be confiscating it.
        assertEquals(Ships.of(ShipType.SKIFF, 1), decoded.state.ships)
    }

    @Test
    fun `a save whose slipway serves two hulls at once is refused rather than half-read`() {
        // The rule `GameState.init` states about the queue, met by a hand-edited file: two jobs
        // overlapping is the one shape a serial yard cannot hold, and `futureEvents` relies on it
        // being impossible rather than merely unusual.
        val overlapping = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial()))
            .replace(
                """"yard":[]""",
                """"yard":[""" +
                    """{"ship":"SKIFF","startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T02:00:00Z"},""" +
                    """{"ship":"SKIFF","startedAt":"1970-01-01T01:00:00Z","completesAt":"1970-01-01T03:00:00Z"}]""",
            )

        assertIs<DecodeResult.Failure>(GameSave.decode(overlapping))
    }

    @Test
    fun `a save claiming both projects at once is refused rather than half-read`() {
        // A hand-edited file, and the one shape `GameState.init` exists to reject. The failure has
        // to be a Failure and not an exception escaping `decode`.
        val both = GameSave.encode(
            GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial().researching(Technology.EXTRACTION, EPOCH)),
        ).replace(
            """"activeAdaptation":null""",
            """"activeAdaptation":{"technology":"THERMAL","toLevel":1,""" +
                """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T03:00:00Z"}""",
        )

        assertIs<DecodeResult.Failure>(GameSave.decode(both))
    }

    @Test
    fun `a galaxy survives a round trip with its seed and everything the player changed`() {
        // given a colony that has surveyed a world outside its home system and taken it
        val fresh = GameState.initial()
        val taken = GalaxyCoordinate(galaxy = fresh.galaxy.home.galaxy, system = 200, slot = 5)
        val state = fresh.copy(
            galaxy = fresh.galaxy.copy(
                surveyed = fresh.galaxy.surveyed + taken,
                ownership = fresh.galaxy.ownership + WorldOwnership(at = taken, holder = EmpireId("kepler")),
            ),
        )
        val snapshot = GameSnapshot(lastUpdatedAt = EPOCH, state = state)

        // when
        val decoded = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot

        // then
        assertEquals(snapshot, decoded)
        assertEquals(EmpireId("kepler"), decoded.state.galaxy.holderOf(taken))
        assertTrue(taken in decoded.state.galaxy.surveyed)
    }

    @Test
    fun `a pinned world survives a round trip and a legacy colony wakes up with none`() {
        // The galaxy identity slice's only on-disk cost. Both halves in one test because they are
        // one claim: the field round-trips, and the 11 -> 12 hop writes the empty set that is the
        // truth about a colony saved before there was anywhere to pin anything from.
        val fresh = GameState.initial()
        val target = GalaxyCoordinate(galaxy = fresh.galaxy.home.galaxy, system = 200, slot = 5)
        val snapshot = GameSnapshot(
            lastUpdatedAt = EPOCH,
            state = fresh.copy(
                galaxy = fresh.galaxy.copy(
                    surveyed = fresh.galaxy.surveyed + target,
                    pinned = setOf(target),
                ),
            ),
        )

        val roundTripped = assertIs<DecodeResult.Success>(GameSave.decode(GameSave.encode(snapshot))).snapshot
        val legacy = assertIs<DecodeResult.Success>(GameSave.decode(VERSION_5_FULL)).snapshot

        assertEquals(setOf(target), roundTripped.state.galaxy.pinned)
        assertEquals(emptySet(), legacy.state.galaxy.pinned)
    }

    @Test
    fun `the worlds themselves are never written to disk`() {
        // The reason the save can afford a galaxy at all. If a trait name ever appears in the
        // encoded string, something started serialising the map.
        val encoded = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial()))

        for (absent in listOf("gravity", "pressure", "temperature", "richness", "fields", "hazard")) {
            assertTrue(!encoded.contains(absent, ignoreCase = true), "$absent leaked into the save: $encoded")
        }
    }

    @Test
    fun `a save whose galaxy claims someone else owns home decodes to a failure`() {
        // given — hand-edited so the home world is held by another empire, which GalaxyState forbids
        val tampered = GameSave.encode(GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial()))
            .replace(""""holder":"player"""", """"holder":"kepler"""")

        // when / then
        assertIs<DecodeResult.Failure>(GameSave.decode(tampered))
    }

    private fun fleetRun(returnsAt: Instant): FleetRun = FleetRun(
        target = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9),
        ships = Ships(mapOf(ShipType.SKIFF to 14, ShipType.HAULER to 1)),
        gathering = ResourceKind.METAL,
        cargo = Resources.of(metal = CARGO_METAL),
        dispatchedAt = returnsAt - 1.hours,
        returnsAt = returnsAt,
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

        // Frozen captures of the 0.0.14 on-disk format — the saves sitting on the builds that
        // shipped between 0.0.12 and 0.0.14. Read them, never rewrite them.
        const val VERSION_3_IDLE = """{"schemaVersion":3,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"builds":{},"research":{"photovoltaics":0,"extraction":0,"enrichment":0},""" +
            """"activeResearch":null,"returningFleet":null,"eventLog":[]}}"""

        // A version 3 colony that had actually been played, research included — the branch that
        // 0.0.12 added is the thing this format has and version 2 did not.
        const val VERSION_3_FULL = """{"schemaVersion":3,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":4,"crystalMine":3,"deuteriumSynthesizer":1,""" +
            """"solarPlant":2,"roboticsFactory":1,"naniteFactory":0},""" +
            """"builds":{"METAL_MINE":{"building":"METAL_MINE","toLevel":5,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T00:50:00Z"}},""" +
            """"research":{"photovoltaics":2,"extraction":3,"enrichment":0},""" +
            """"activeResearch":{"technology":"EXTRACTION","toLevel":4,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T05:00:00Z"},""" +
            """"returningFleet":{"ships":{"CARGO":14},""" +
            """"cargo":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
            """"origin":{"galaxy":2,"system":117,"position":9},"arrivesAt":"1970-01-01T01:00:00Z"},""" +
            """"eventLog":[{"type":"BuildStarted","building":"METAL_MINE","toLevel":5,""" +
            """"at":"1970-01-01T00:00:00Z"}]}}"""

        // Frozen captures of the 0.0.16 on-disk format — the saves sitting on the builds that
        // shipped between 0.0.15 and 0.0.16, which are the first with a galaxy in them. Read them,
        // never rewrite them.
        const val VERSION_4_IDLE = """{"schemaVersion":4,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"builds":{},"research":{"photovoltaics":0,"extraction":0,"enrichment":0},""" +
            """"activeResearch":null,""" +
            """"galaxy":{"seed":20260807,"home":{"galaxy":3,"system":165,"slot":7},""" +
            """"surveyed":[{"galaxy":3,"system":165,"slot":7},{"galaxy":3,"system":165,"slot":8},""" +
            """{"galaxy":3,"system":165,"slot":10},{"galaxy":3,"system":165,"slot":13}],""" +
            """"ownership":[{"at":{"galaxy":3,"system":165,"slot":7},"holder":"player"}]},""" +
            """"returningFleet":null,"eventLog":[]}}"""

        // A frozen capture of the 0.2.1 on-disk format — byte for byte the string `the on-disk
        // shape is pinned` asserted before the debug menu landed, which makes it the save every
        // already-installed build is holding. Its whole job is to have no `debugUsed` key.
        const val VERSION_6_IDLE = """{"schemaVersion":6,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":1080000000,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":1,"crystalMine":1,"deuteriumSynthesizer":1,""" +
            """"solarPlant":1,"roboticsFactory":0,"naniteFactory":0},""" +
            """"builds":{},"research":{"photovoltaics":0,"extraction":0,"enrichment":0,""" +
            """"thermal":0,"gravitic":0,"atmospheric":0},""" +
            """"activeResearch":null,"activeAdaptation":null,""" +
            """"galaxy":{"seed":20260807,"home":{"galaxy":3,"system":165,"slot":7},""" +
            """"surveyed":[{"galaxy":3,"system":165,"slot":7},{"galaxy":3,"system":165,"slot":8},""" +
            """{"galaxy":3,"system":165,"slot":10},{"galaxy":3,"system":165,"slot":13}],""" +
            """"ownership":[{"at":{"galaxy":3,"system":165,"slot":7},"holder":"player"}]},""" +
            """"surveys":[],""" +
            """"returningFleet":null,"eventLog":[]}}"""

        // A version 4 colony that had actually been played, with a map it had started changing:
        // three surveyed worlds beyond the home system is the thing this format has and version 3
        // did not, and the research levels are what the 4 -> 5 hop must not reset.
        const val VERSION_4_FULL = """{"schemaVersion":4,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":4,"crystalMine":3,"deuteriumSynthesizer":1,""" +
            """"solarPlant":2,"roboticsFactory":1,"naniteFactory":0},""" +
            """"builds":{"METAL_MINE":{"building":"METAL_MINE","toLevel":5,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T00:50:00Z"}},""" +
            """"research":{"photovoltaics":2,"extraction":3,"enrichment":0},""" +
            """"activeResearch":{"technology":"EXTRACTION","toLevel":4,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T05:00:00Z"},""" +
            """"galaxy":{"seed":20260807,"home":{"galaxy":3,"system":165,"slot":7},""" +
            """"surveyed":[{"galaxy":3,"system":165,"slot":7},{"galaxy":3,"system":165,"slot":8},""" +
            """{"galaxy":3,"system":165,"slot":10},{"galaxy":3,"system":165,"slot":13}],""" +
            """"ownership":[{"at":{"galaxy":3,"system":165,"slot":7},"holder":"player"}]},""" +
            """"returningFleet":{"ships":{"CARGO":14},""" +
            """"cargo":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
            """"origin":{"galaxy":2,"system":117,"position":9},"arrivesAt":"1970-01-01T01:00:00Z"},""" +
            """"eventLog":[{"type":"BuildStarted","building":"METAL_MINE","toLevel":5,""" +
            """"at":"1970-01-01T00:00:00Z"}]}}"""

        // A version 5 colony mid-game, with a ladder climbed and a map partly surveyed: the two
        // things the 5 -> 6 hop must carry across untouched. The applied slot is busy and the
        // adaptation slot is empty, which is the only combination `GameState.init` accepts.
        const val VERSION_5_FULL = """{"schemaVersion":5,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
            """"resources":{"metalFine":1800000000,"crystalFine":0,"deuteriumFine":0},""" +
            """"buildings":{"metalMine":4,"crystalMine":3,"deuteriumSynthesizer":1,""" +
            """"solarPlant":2,"roboticsFactory":4,"naniteFactory":0},""" +
            """"builds":{},""" +
            """"research":{"photovoltaics":2,"extraction":3,"enrichment":0,""" +
            """"thermal":0,"gravitic":3,"atmospheric":1},""" +
            """"activeResearch":{"technology":"EXTRACTION","toLevel":4,""" +
            """"startedAt":"1970-01-01T00:00:00Z","completesAt":"1970-01-01T05:00:00Z"},""" +
            """"activeAdaptation":null,""" +
            """"galaxy":{"seed":20260807,"home":{"galaxy":3,"system":165,"slot":7},""" +
            """"surveyed":[{"galaxy":3,"system":165,"slot":7},{"galaxy":3,"system":165,"slot":8},""" +
            """{"galaxy":3,"system":165,"slot":10},{"galaxy":3,"system":165,"slot":13}],""" +
            """"ownership":[{"at":{"galaxy":3,"system":165,"slot":7},"holder":"player"}]},""" +
            """"returningFleet":null,"eventLog":[]}}"""

        // A colony that had actually been played: levelled buildings, a stock, a fleet on its
        // way home and an event log — everything the migration must carry across untouched.
        const val VERSION_1_FULL ="""{"schemaVersion":1,"lastUpdatedAt":"1970-01-01T00:00:00Z","state":{""" +
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
