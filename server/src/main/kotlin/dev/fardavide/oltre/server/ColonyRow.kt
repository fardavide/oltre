package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.AllianceSpeedup
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
// **`allianceExperience` is the boon's whole delivery mechanism**, and it arrives here rather than
// being read separately because of where it must not be read. The sync route deliberately looks a
// membership up only when a request actually carries a `Contribute` — *"an unconditional membership
// lookup would put a second query on the hot path of the one route every check-in calls"* — and the
// boon needs the level on **every** sync. So it comes off the colony read itself, as a join, and
// costs no round trip at all.
//
// Null is *this player is in no alliance*, which is `AllianceSpeedup.NONE` and nineteen colonies in
// twenty.
//
// **The stored value is overwritten rather than trusted**, and that is the check-in rule rather than
// distrust of the column: what the snapshot holds is whatever the alliance was worth the *last* time
// this colony was advanced, and what is true now is what the alliance is worth now. Stamping it on
// read is what makes the boon begin at a member's next check-in — `alliance-sheet.md` §4.2 — and it
// is also what makes a member who has been away a week arrive to the level their alliance reached
// while they were gone.
internal fun colonyFrom(snapshotJson: String, version: Long, allianceExperience: Long?): StoredColony =
    when (val decoded = GameSave.decode(snapshotJson)) {
        is DecodeResult.Success -> StoredColony(
            decoded.snapshot.copy(
                state = decoded.snapshot.state.copy(
                    allianceSpeedup = allianceExperience
                        ?.let { AllianceBalance.speedupOf(it) }
                        ?: AllianceSpeedup.NONE,
                ),
            ),
            ColonyVersion(version),
        )
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
