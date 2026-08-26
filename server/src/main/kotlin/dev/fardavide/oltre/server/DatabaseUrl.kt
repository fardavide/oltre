package dev.fardavide.oltre.server

import java.net.URI
import java.net.URISyntaxException

// **What `DATABASE_URL` is allowed to be, and it is not what `#109` assumed.** That slice fed the
// variable straight to HikariCP as a JDBC URL, which is the one form no database provider hands out:
// Neon, Supabase, Railway, Fly and Heroku all print a **libpq** URI —
// `postgresql://user:password@host/database?sslmode=require` — because that is what `psql` takes.
//
// The two are not interchangeable and the failure is total rather than subtle. A libpq URI has no
// `jdbc:` scheme, so HikariCP finds no driver for it at all; and the PostgreSQL driver does not read
// credentials out of an authority, so `user:password@host` would be parsed as a *hostname* even if
// the scheme were fixed. `#111` found it by being handed a real connection string and reading it.
//
// **So the variable takes either, and the conversion is here rather than in `PostgresDatabase.kt`.**
// That file is excluded from the unit coverage pass on the condition that *nothing in it decides
// anything*, and this decides something — which is exactly the drift that exclusion's own comment
// warns would be quietly hidden by it.
//
// Nothing here reads a clock, opens a socket or touches a driver: it is string arithmetic with a
// `…Test` per rule, and `connectionPool` is what does something with the answer.

// A connection, taken apart. **The credentials come out of the URL rather than staying in it**, and
// that is not tidying: HikariCP has `username` and `password` properties, so lifting them out avoids
// re-encoding a password into a query string and hoping both ends agree on how — which is the step a
// password containing `&`, `+` or a space would fail at, silently, months later.
internal data class DatabaseConnection(val jdbcUrl: String, val username: String?, val password: String?)

private const val VARIABLE = "DATABASE_URL"

// The schemes a provider prints. `postgres://` is the older spelling and Heroku made it the common
// one; both mean the same thing and refusing one would be refusing it for no reason.
private val LIBPQ_SCHEMES = setOf("postgresql", "postgres")

private const val JDBC_PREFIX = "jdbc:"

internal fun databaseConnection(databaseUrl: String): DatabaseConnection {
    val trimmed = databaseUrl.trim()
    require(trimmed.isNotBlank()) { "$VARIABLE is set to nothing at all" }

    // **Already a JDBC URL, so it is left exactly as it is.** The embedded Postgres the integration
    // suite runs against prints one of these, and so does anybody who has already done this
    // conversion by hand — and a URL somebody built deliberately is not one to rewrite.
    if (trimmed.startsWith(JDBC_PREFIX, ignoreCase = true)) {
        return DatabaseConnection(jdbcUrl = trimmed, username = null, password = null)
    }

    val uri = try {
        URI(trimmed)
    } catch (e: URISyntaxException) {
        throw IllegalArgumentException("$VARIABLE is not a url: ${e.message}", e)
    }

    require(uri.scheme?.lowercase() in LIBPQ_SCHEMES) {
        "$VARIABLE names the scheme ${uri.scheme} rather than one of $LIBPQ_SCHEMES or a $JDBC_PREFIX url"
    }
    val host = uri.host?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("$VARIABLE names no host")
    // A libpq URI's path is the database name with a leading slash. Empty means the caller is relying
    // on a default that this server does not have, and connecting to whatever turns up is worse than
    // refusing: it is how a colony ends up in a database nobody meant to write to.
    val database = uri.path.orEmpty().removePrefix("/").takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("$VARIABLE names no database")

    // `getUserInfo` rather than `getRawUserInfo`: a password with a `/`, an `@` or a space in it is
    // percent-encoded in the URI, and what HikariCP wants is the password itself.
    val credentials = uri.userInfo?.split(':', limit = 2)

    val port = if (uri.port == -1) "" else ":${uri.port}"
    val query = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()

    return DatabaseConnection(
        // The query is carried across **raw**, because it is already in the encoding both this and a
        // JDBC URL use, and because it is where `sslmode` lives — a parameter Neon requires and one
        // that would be a plaintext connection to somebody else's database if it were dropped.
        jdbcUrl = "${JDBC_PREFIX}postgresql://$host$port/$database$query",
        username = credentials?.firstOrNull()?.takeIf { it.isNotBlank() },
        password = credentials?.getOrNull(1)?.takeIf { it.isNotBlank() },
    )
}
