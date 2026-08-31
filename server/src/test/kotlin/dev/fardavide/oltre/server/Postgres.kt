package dev.fardavide.oltre.server

import io.zonky.test.db.postgres.junit.EmbeddedPostgresRules
import io.zonky.test.db.postgres.junit.SingleInstancePostgresRule
import java.sql.ResultSet
import javax.sql.DataSource

// **A real PostgreSQL 17, unpacked from a jar and run as a child process.** No daemon, no image
// pull, no `docker` on the machine — which is the whole reason it is this and not Testcontainers
// (Davide, 2026-08-25): there is no container runtime on the Mac that does the UI work, an
// unqualified `./gradlew check` runs every category, and a suite that cannot run locally is a suite
// that stops being run. See `decisions.md`.
//
// **`@ClassRule` and not `@BeforeTest`**, and the distinction is not cosmetic: this repository is on
// JUnit 4 (`kotlin-test` → `kotlin-test-junit` → `junit:junit`, and nothing calls
// `useJUnitPlatform()`), where `kotlin.test.BeforeTest` is a typealias for `@Before` and runs **per
// method**. Used there this would start and stop a database around every test in the file. A
// JUnit 5 `@RegisterExtension` would be worse than slow — the platform is not running, so it would
// silently never fire at all.
//
// The two `setLocaleConfig` calls are for a runner that is not this machine: `initdb` refuses to
// start with *"invalid locale settings"* when `LANG` and `LC_ALL` are both unset, which is the
// normal state of a minimal container. Zonky ships a Maven profile for exactly that and a Gradle
// build inherits none of it.
internal fun embeddedPostgres(): SingleInstancePostgresRule = EmbeddedPostgresRules.singleInstance()
    .customize { builder ->
        builder
            .setLocaleConfig("locale", "C")
            .setLocaleConfig("encoding", "UTF-8")
    }

// One database per test class and a clean one per test. `TRUNCATE` rather than dropping the schema,
// so what every test after the first runs against is the schema the *previous* test left — which is
// the state a deployed server is always in.
internal fun DataSource.emptyEveryTable() = execute("TRUNCATE players, colonies, applied_verbs")

// Only `the schema applies to an empty database` uses this, and only so that it can say "empty" and
// mean it.
internal fun DataSource.dropEveryTable() = execute("DROP TABLE IF EXISTS applied_verbs, colonies, players")

// What the schema actually left behind, read from the catalogue rather than from the file that was
// applied — a test that asserted the DDL text would only be reading its own input back.
internal fun DataSource.tableNames(): List<String> = rows(
    "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
)

internal fun DataSource.playerIdentities(): List<Pair<String, String>> =
    rows("SELECT provider, subject FROM players ORDER BY subject") { it.getString(1) to it.getString(2) }

internal fun DataSource.scalar(sql: String): String? = rows(sql).singleOrNull()

internal fun DataSource.rowsIn(table: String): Int = scalar("SELECT count(*) FROM $table")?.toInt() ?: 0

// **A player row, written straight in.** Since `#110` the `players` table is identity's and nothing
// else writes it — `PostgresColonyRepository.found` used to forge a row from the header value and no
// longer does, because a colony now hangs off somebody who actually signed in. `colonies.player_id`
// is a foreign key, so the colony suite has to put its two players there first, and it does it with
// SQL rather than through `PostgresPlayerRepository`: what that class does is its own file's
// business, and a fixture that went through it would fail two suites for one bug.
internal fun DataSource.givenPlayer(player: PlayerId) {
    connection.use { connection ->
        connection.prepareStatement(
            "INSERT INTO players (id, provider, subject, created_at) VALUES (?, 'header', ?, now())",
        ).use { statement ->
            statement.setString(1, player.value)
            statement.setString(2, player.value)
            statement.executeUpdate()
        }
    }
}

// **A mark document straight into the column, bypassing the codec.** The only way to reach the state
// a rollback puts the service in — a document written by a deploy that knew more parts than this
// build does — without a second `MarkPreset` member that exists solely to be unknown.
internal fun DataSource.writeMark(player: PlayerId, mark: String) {
    connection.use { connection ->
        connection.prepareStatement("UPDATE players SET mark = ?::jsonb WHERE id = ?").use { statement ->
            statement.setString(1, mark)
            statement.setString(2, player.value)
            statement.executeUpdate()
        }
    }
}

// **A name straight into the column, bypassing the contract.** `CommanderName`'s guards are what a
// *request* is checked against; the column is a `text` and holds whatever any deploy ever wrote —
// which is the state a rollback puts the service in, exactly as `writeMark` reaches for above. The
// only way there without a second `CommanderName` that exists solely to be illegal.
internal fun DataSource.writeName(player: PlayerId, name: String) {
    connection.use { connection ->
        connection.prepareStatement("UPDATE players SET display_name = ? WHERE id = ?").use { statement ->
            statement.setString(1, name)
            statement.setString(2, player.value)
            statement.executeUpdate()
        }
    }
}

private fun DataSource.execute(sql: String) {
    connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
}

private fun DataSource.rows(sql: String): List<String> = rows(sql) { it.getString(1) }

private fun <T> DataSource.rows(sql: String, read: (ResultSet) -> T): List<T> =
    connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                buildList {
                    while (rows.next()) add(read(rows))
                }
            }
        }
    }
