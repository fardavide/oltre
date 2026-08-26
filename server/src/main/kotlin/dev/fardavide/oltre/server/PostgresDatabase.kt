package dev.fardavide.oltre.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

// **Everything that needs a live connection to be executed at all**, kept in one file with
// `PostgresColonyRepository` and deliberately holding no decision of its own. Nothing here branches
// on a colony, a version or a verb: it opens connections, runs statements and hands rows back. What
// a row *means* is `ColonyRow.kt`, what a sync does when it loses is `Endpoints.kt`, and both of
// those are reachable by a plain unit test — which is the split the `test-coverage` skill asks for
// and the one `#108` made when the rules were moved out of `OltreServer.kt`.

// The pool. Small on purpose: Neon's free tier meters connections rather than queries, Cloud Run
// runs many short-lived instances of this process rather than one long one, and the load being
// sized for is a handful of players at two to four requests a day. A pool that is larger than the
// concurrency it serves is a pool that holds a scarce thing open to no end.
//
// **`#111` owns whether this number is right**, because it is the slice that knows how many
// instances Cloud Run is allowed to run at once — the product of the two is what Neon sees. Answered:
// `--max-instances=3`, so fifteen at the absolute ceiling and far below Neon's limit.
private const val MAX_CONNECTIONS = 5

// **Zero, and it is the line that keeps the free tier free.** HikariCP's default is
// `minimumIdle == maximumPoolSize`, which means a *fixed* pool: five connections opened at startup and
// held for the life of the process, with `idleTimeout` ignored entirely.
//
// That default and `#111`'s keep-warm ping would have combined into a bill. The ping keeps a Cloud
// Run instance alive around the clock so that a player never meets a cold start — and an instance
// alive with a fixed pool is five connections open to Neon around the clock, which means Neon's
// compute **never autosuspends**. Neon's free plan meters *compute hours* and cannot have autosuspend
// disabled, so a colony nobody was playing would have burned the month's allowance and suspended the
// project. Neither half is wrong on its own; together they are an outage with a fortnight's fuse.
//
// So the pool drains to nothing when nothing is happening, and a sync opens a connection when it
// needs one. **That costs a connection setup on the first request after a quiet spell** — a TLS
// handshake to Neon, plus Neon's own wake-up if it has suspended. At the load `#106` §6 sized for,
// two to four requests per player per day, that is the right way round: the alternative is paying for
// a connection to be held open all day so that four of them are marginally faster.
private const val MINIMUM_IDLE = 0

// How long an idle connection is kept before the pool lets it go. HikariCP refuses anything under ten
// seconds and falls back to its ten-minute default, which here would be ten minutes of Neon compute
// after every sync — so this is a floor being respected rather than a number being tuned.
private const val IDLE_TIMEOUT_MILLIS = 10_000L

// **`DATABASE_URL` is whatever the provider printed**, and turning that into something a JDBC driver
// will take is `DatabaseUrl.kt`'s — deliberately, because it is a decision and this file is excluded
// from the unit coverage pass on the condition that it holds none.
internal fun connectionPool(url: String): HikariDataSource {
    val connection = databaseConnection(url)
    return HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = connection.jdbcUrl
            // Set only when the URL actually carried one. HikariCP treats a `null` username as
            // "not configured" and an empty one as a username, and the second would fail to
            // authenticate against a server that would have accepted the first.
            connection.username?.let { username = it }
            connection.password?.let { password = it }
            maximumPoolSize = MAX_CONNECTIONS
            minimumIdle = MINIMUM_IDLE
            idleTimeout = IDLE_TIMEOUT_MILLIS
            poolName = "oltre"
        },
    )
}

// **The whole migration story, and it is one file applied at startup.** Every statement in
// `schema.sql` is `IF NOT EXISTS`, so applying it to a database that already has the schema is a
// no-op rather than an error — which is what makes it safe on a host that starts this process again
// every time it scales up from zero.
//
// A framework would buy an ordered ladder of migrations, and `#106` §5.4 is explicit that this does
// not want one yet: there is one shape, nothing has ever been deployed, and the day a column has to
// change is the day to argue for the tool rather than to have inherited it.
internal fun DataSource.applySchema() {
    val ddl = PostgresColonyRepository::class.java.getResourceAsStream("/schema.sql")
        ?.use { it.reader().readText() }
        ?: error("schema.sql is not on the classpath, so this build has no schema to apply")
    connection.use { connection -> connection.createStatement().use { it.execute(ddl) } }
}

// **JDBC blocks and a Ktor handler must not**, so every statement this module runs goes through here
// and lands on the IO dispatcher. Missing it would not fail a test — it would quietly hold Netty's
// event loop for the length of a network round trip to Neon.
//
// A transaction rather than a bare connection for reads as well as writes, because one path is
// easier to be right about than two: a single-statement transaction costs a `BEGIN` and a `COMMIT`
// that nothing needs, and at two to four requests per player per day that is a price worth not
// thinking about again.
internal suspend fun <T> DataSource.transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
    connection.use { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (failure: Throwable) {
            // Rolled back and rethrown, never swallowed: `served` has one `catch` and turns anything
            // that reaches it into `ApiError.Internal`, which is a 500 the client retries and an
            // operator can go and look at. A store that answered "fine" to a failed write would
            // instead take the player's money and lose the building.
            connection.rollback()
            throw failure
        }
    }
}

internal fun <T> Connection.query(sql: String, bind: PreparedStatement.() -> Unit, read: (ResultSet) -> T): T =
    prepareStatement(sql).use { statement ->
        statement.bind()
        statement.executeQuery().use(read)
    }

internal fun Connection.update(sql: String, bind: PreparedStatement.() -> Unit): Int =
    prepareStatement(sql).use { statement ->
        statement.bind()
        statement.executeUpdate()
    }
