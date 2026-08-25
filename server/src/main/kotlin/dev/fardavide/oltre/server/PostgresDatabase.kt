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
// instances Cloud Run is allowed to run at once — the product of the two is what Neon sees.
private const val MAX_CONNECTIONS = 5

internal fun connectionPool(url: String): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = url
        maximumPoolSize = MAX_CONNECTIONS
        poolName = "oltre"
    },
)

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
