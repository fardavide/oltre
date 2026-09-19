package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.protocol.Alliance
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceSeats
import dev.fardavide.oltre.protocol.AllianceTag
import kotlin.time.Instant

// Row meaning is independent of JDBC.
//
// **The level and the cap are computed here rather than stored, and that is the treasury slice
// arriving.** Until now this function wrote `AllianceLevel(0)` and a flat `SEAT_CAP`, with a comment
// saying the balance slice would replace both. The level is now derived from `experience`, which is
// the column the pool's writer maintains — deriving a level from a stored total is not folding, and
// folding is summing a ledger, which is the thing Davide rejected for the player gauge.
//
// The cap comes out of the same level plus whatever `seats_bought` the pool paid for, which is what
// makes the first contribution visible on the roster header before any project exists.
internal fun allianceFrom(
    id: AllianceId,
    name: AllianceName,
    tag: AllianceTag,
    version: AllianceVersion,
    createdAt: Instant,
    experience: Long,
    taken: Int,
    // Defaulted to an empty treasury, which is what every alliance founded before this slice holds
    // and what `schema.sql`'s `DEFAULT 0` writes for every row that predates the columns. The
    // Postgres reader passes both; the tests that are about roles and succession do not have to
    // carry a pool they are not asking about.
    pool: Resources = Resources.of(),
    projects: ProjectsBought = ProjectsBought.NONE,
): StoredAlliance {
    val progress = AllianceBalance.progressOf(experience)
    return StoredAlliance(
        Alliance(
            id = id,
            name = name,
            tag = tag,
            level = progress.level,
            // **Clamped up to whatever is actually taken, and the clamp is load-bearing.**
            // `AllianceSeats` refuses to be built with more seats taken than its cap, so a roster
            // that grew under an older cap — or one the balance round lowers — would make the whole
            // response undecodable rather than merely odd. `Alliance.kt` names that exact residual
            // beside the guard; this is where it is paid.
            seats = AllianceSeats(
                taken = taken,
                cap = maxOf(AllianceBalance.seatCap(progress.level, projects.seats), taken),
            ),
        ),
        version,
        createdAt,
        experience,
        pool,
        projects,
    )
}
