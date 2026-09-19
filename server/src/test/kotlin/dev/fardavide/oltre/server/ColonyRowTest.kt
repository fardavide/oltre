package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.AllianceSpeedup
import dev.fardavide.oltre.core.GameSave
import dev.fardavide.oltre.protocol.AllianceLevel
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

// What a `colonies` row means, judged without a database — which is the whole reason the row mapping
// is a file of its own rather than three lines inside a JDBC call. Whether the SQL that fills the
// row is right is `PostgresColonyRepositoryIntegrationTest`'s question, and it is a different one.
class ColonyRowTest {

    @Test
    fun `a row is the colony it was stored as, at the version the row holds`() {
        val colony = establishedColony()

        val stored = colonyFrom(GameSave.encode(colony), version = 7, alliance = null)

        assertEquals(StoredColony(colony, ColonyVersion(7)), stored)
    }

    // ── What the alliance takes off, stamped on the way out ──────────────────────────────────

    @Test
    fun `a colony in no alliance has nothing taken off`() {
        val stored = colonyFrom(GameSave.encode(establishedColony()), version = 1, alliance = null)

        assertEquals(AllianceSpeedup.NONE, stored.snapshot.state.allianceSpeedup)
    }

    @Test
    fun `a colony in an alliance is stamped with what its level takes off`() {
        val stored = colonyFrom(
            GameSave.encode(establishedColony()),
            version = 1,
            alliance = AllianceStanding(experienceForLevel(2), logisticsBought = 0),
        )

        assertEquals(4, stored.snapshot.state.allianceSpeedup.percent)
    }

    // **What the pool bought arrives by the same door the level does**, which is the point of the
    // join carrying a standing rather than an experience: a member whose alliance spent on Shared
    // Logistics meets the wider number on their next check-in, with nothing about the purchase on
    // their screen and nothing in `core` that has heard of a project.
    @Test
    fun `a colony is stamped with what the pool bought as well as what the level earned`() {
        val stored = colonyFrom(
            GameSave.encode(establishedColony()),
            version = 1,
            alliance = AllianceStanding(experienceForLevel(2), logisticsBought = 3),
        )

        assertEquals(7, stored.snapshot.state.allianceSpeedup.percent)
    }

    private fun experienceForLevel(level: Int): Long {
        var earned = 0L
        repeat(level) { earned += AllianceBalance.spanOf(AllianceLevel(it)) }
        return earned
    }

    // **The stored value is overwritten rather than trusted**, which is the check-in rule: what the
    // column holds is whatever the alliance was worth the last time this colony was advanced, and
    // what is true now is what the alliance is worth now. A member who has been away a week arrives
    // to the level their alliance reached while they were gone — and a member whose alliance
    // disbanded arrives to nothing, from the same line.
    @Test
    fun `a stale boon in the column is replaced by what the alliance is worth now`() {
        val stale = establishedColony().let { it.copy(state = it.state.copy(allianceSpeedup = AllianceSpeedup(30))) }

        val rejoined = colonyFrom(GameSave.encode(stale), version = 1, alliance = null)

        assertEquals(AllianceSpeedup.NONE, rejoined.snapshot.state.allianceSpeedup)
    }

    @Test
    fun `a row that cannot be read is a failure rather than a player with no colony`() {
        // The distinction this file exists for. Null from `colonyOf` means *"no colony yet"*, and
        // what the client does next is found one — so a corrupt row reported as absent would mint a
        // second galaxy on top of a colony that is sitting right there, unreadable but not gone.
        // Raising instead reaches `served`'s one `catch`, which is a 500 and a line in a log.
        val failure = assertFailsWith<IllegalStateException> {
            colonyFrom("""{"not":"a save"}""", version = 3, alliance = null)
        }

        assertTrue("could not be read" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `a row this build refuses to carry forward says which schema it is on`() {
        // Obsolete is not corruption — it is a save `core` deliberately will not migrate — and it is
        // worth its own message because an operator reading it has a different decision to make:
        // restore a backup, or deploy a build that still reads schema 1.
        val ancient = """{"schemaVersion":1,"lastUpdatedAt":"$TEST_NOW"}"""

        val failure = assertFailsWith<IllegalStateException> {
            colonyFrom(ancient, version = 3, alliance = null)
        }

        assertTrue("schema 1" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `an instant reaches a timestamptz column at UTC and keeps its nanoseconds`() {
        // Nothing in the game reads these columns back — they are for whoever is holding a `psql`
        // prompt — which is exactly why being quietly wrong in them would never be noticed. A
        // `java.sql.Timestamp` would have been read at the JVM's default zone, and a server whose
        // timezone is not the one the row was written under would move every audit column by hours.
        val instant = Instant.parse("2026-08-25T12:00:00.123456789Z")

        val stamped = instant.atUtc()

        assertEquals(OffsetDateTime.parse("2026-08-25T12:00:00.123456789Z"), stamped)
        assertEquals(ZoneOffset.UTC, stamped.offset)
    }
}
