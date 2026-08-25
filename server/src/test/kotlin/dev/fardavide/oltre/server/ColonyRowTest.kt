package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.GameSave
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

        val stored = colonyFrom(GameSave.encode(colony), version = 7)

        assertEquals(StoredColony(colony, ColonyVersion(7)), stored)
    }

    @Test
    fun `a row that cannot be read is a failure rather than a player with no colony`() {
        // The distinction this file exists for. Null from `colonyOf` means *"no colony yet"*, and
        // what the client does next is found one — so a corrupt row reported as absent would mint a
        // second galaxy on top of a colony that is sitting right there, unreadable but not gone.
        // Raising instead reaches `served`'s one `catch`, which is a 500 and a line in a log.
        val failure = assertFailsWith<IllegalStateException> { colonyFrom("""{"not":"a save"}""", version = 3) }

        assertTrue("could not be read" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `a row this build refuses to carry forward says which schema it is on`() {
        // Obsolete is not corruption — it is a save `core` deliberately will not migrate — and it is
        // worth its own message because an operator reading it has a different decision to make:
        // restore a backup, or deploy a build that still reads schema 1.
        val ancient = """{"schemaVersion":1,"lastUpdatedAt":"$TEST_NOW"}"""

        val failure = assertFailsWith<IllegalStateException> { colonyFrom(ancient, version = 3) }

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
