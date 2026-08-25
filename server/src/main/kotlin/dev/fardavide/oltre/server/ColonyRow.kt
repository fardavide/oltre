package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.DecodeResult
import dev.fardavide.oltre.core.GameSave
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant

// **What a `colonies` row means, with no connection anywhere in it.** The file exists for the rule
// the `test-coverage` skill states and `Endpoints.kt` already follows: *a decision belongs where the
// kind of test that judges it can reach it.* Reading a snapshot back out of a column is a decision —
// three of them, one per `DecodeResult` — and left inside the JDBC call it would be judged only by a
// test that has a database, which is a slow test of the wrong thing. What the integration suite is
// then for is the SQL, which is the only part it can actually prove.

// **A row that will not decode is a failure and never an absence**, and the distinction is the whole
// reason this is a function rather than a `let`. `colonyOf` answering null means *"this player has no
// colony"*, which is what a first launch meets before the one-time upload — and what the client does
// next is found one. So a corrupt or unreadable row reported as null would mint a second galaxy over
// the top of a colony that is sitting right there, unreadable but not gone.
//
// Raising instead reaches `served`'s one `catch` and becomes `ApiError.Internal`, which is a 500 the
// player's client retries and an operator can go and look at. Neither answer gets the colony back;
// only one of them leaves it there to be got back.
internal fun colonyFrom(snapshotJson: String, version: Long): StoredColony =
    when (val decoded = GameSave.decode(snapshotJson)) {
        is DecodeResult.Success -> StoredColony(decoded.snapshot, ColonyVersion(version))
        is DecodeResult.Failure -> error("a stored colony could not be read: ${decoded.reason}")
        // Obsolete is not corruption — it is a save this build deliberately refuses to carry
        // forward — and it is worth its own message for the reason `core` gives it its own member:
        // an operator reading this in a log needs to know whether to restore a backup or to deploy
        // a build that still reads schema ${decoded.schemaVersion}.
        is DecodeResult.Obsolete ->
            error("a stored colony is on schema ${decoded.schemaVersion} which this build refuses: ${decoded.reason}")
    }

// `timestamptz` in, and it is `OffsetDateTime` at UTC rather than a `Timestamp` because a
// `java.sql.Timestamp` carries no zone at all: the driver would read the JVM's default one, and a
// server whose timezone is not the one the row was written under would move every audit column by
// hours. Nothing in the game reads these back — they are for whoever is holding a `psql` prompt —
// which is exactly why being quietly wrong in them would never be noticed.
//
// Seconds and nanoseconds rather than a millisecond epoch: `timestamptz` keeps microseconds, and
// rounding a colony's `lastUpdatedAt` on the way into a column that could have held it would be a
// loss taken for no reason.
internal fun Instant.atUtc(): OffsetDateTime =
    OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()), ZoneOffset.UTC)
