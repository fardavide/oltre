package dev.fardavide.oltre.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// **The gap `#111` found by being handed a real connection string.** `#109` fed `DATABASE_URL`
// straight to HikariCP as a JDBC URL, and no provider prints one of those: Neon, Supabase, Railway,
// Fly and Heroku all give a libpq URI, because that is what `psql` takes. Pasting what the console
// shows would have produced a revision that never boots — loudly, and after a four-minute deploy.
//
// **No real credential is anywhere in this file.** The passwords below are obviously invented, which
// is the same rule the provider keypairs in `Tokens.kt` follow.

private const val NEON = "postgresql://colonist:not-a-real-password@ep-example-123.eu-central-1.aws.neon.tech/oltre?sslmode=require"

class DatabaseUrlTest {

    // The whole point of the file, in the shape the console actually prints.
    @Test
    fun `a libpq url becomes a jdbc url with the credentials lifted out`() {
        val connection = databaseConnection(NEON)

        assertEquals(
            "jdbc:postgresql://ep-example-123.eu-central-1.aws.neon.tech/oltre?sslmode=require",
            connection.jdbcUrl,
        )
        assertEquals("colonist", connection.username)
        assertEquals("not-a-real-password", connection.password)
    }

    // **`sslmode` is the parameter that must survive**, and it is why the query is carried across
    // rather than rebuilt. Dropped, this would be a plaintext connection to somebody else's database
    // — and Neon would refuse it, which is the good outcome and not one to rely on.
    @Test
    fun `every query parameter survives the conversion`() {
        val connection = databaseConnection("$NEON&channel_binding=require&application_name=oltre")

        assertTrue("sslmode=require" in connection.jdbcUrl, connection.jdbcUrl)
        assertTrue("channel_binding=require" in connection.jdbcUrl, connection.jdbcUrl)
        assertTrue("application_name=oltre" in connection.jdbcUrl, connection.jdbcUrl)
    }

    // Heroku's spelling, and the one most copied-from-elsewhere strings carry. Refusing it would be
    // refusing the same URL for the sake of two characters.
    @Test
    fun `the shorter scheme means the same thing`() {
        assertEquals(
            "jdbc:postgresql://host.example/oltre",
            databaseConnection("postgres://host.example/oltre").jdbcUrl,
        )
    }

    @Test
    fun `a port is carried across and an absent one is not invented`() {
        assertEquals("jdbc:postgresql://host.example:6543/oltre", databaseConnection("postgresql://host.example:6543/oltre").jdbcUrl)
        assertEquals("jdbc:postgresql://host.example/oltre", databaseConnection("postgresql://host.example/oltre").jdbcUrl)
    }

    // **A password is percent-encoded in a URI and is not a percent-encoded thing to HikariCP.** This
    // is the case that would have gone wrong quietly if the credentials had been re-encoded into a
    // query string instead of lifted out: a password with a `/`, an `@` or a space is exactly what a
    // generated one eventually contains.
    @Test
    fun `a password that had to be escaped comes back as itself`() {
        val connection = databaseConnection("postgresql://colonist:p%40ss%2Fword%20here@host.example/oltre")

        assertEquals("p@ss/word here", connection.password)
    }

    // What the integration suite hands it, and what somebody who did this conversion by hand would.
    // A URL built deliberately is not one to rewrite.
    @Test
    fun `a jdbc url is left exactly as it is`() {
        val given = "jdbc:postgresql://localhost:5432/postgres?user=postgres"

        val connection = databaseConnection(given)

        assertEquals(given, connection.jdbcUrl)
        assertNull(connection.username)
        assertNull(connection.password)
    }

    // A URL that carries its credentials some other way — a `.pgpass`, IAM auth, a query parameter.
    // Absent is not the same as empty, and HikariCP is only told about what was actually there.
    @Test
    fun `a url with no credentials in it names nobody`() {
        val connection = databaseConnection("postgresql://host.example/oltre")

        assertNull(connection.username)
        assertNull(connection.password)
    }

    @Test
    fun `a user with no password is a user`() {
        val connection = databaseConnection("postgresql://colonist@host.example/oltre")

        assertEquals("colonist", connection.username)
        assertNull(connection.password)
    }

    @Test
    fun `spacing around the whole thing is not part of it`() {
        assertEquals(
            "jdbc:postgresql://host.example/oltre",
            databaseConnection("  postgresql://host.example/oltre\n").jdbcUrl,
        )
    }

    // ── The four ways it is refused, and each one names the variable ──────────────────────────
    //
    // A refusal here is a revision that never becomes ready, which Cloud Run answers by leaving the
    // previous one serving — so the message is read in a deploy log by somebody looking at a failed
    // deploy, and it has to say which variable.

    @Test
    fun `nothing at all is refused`() {
        assertTrue(VARIABLE in assertFailsWith<IllegalArgumentException> { databaseConnection("   ") }.message.orEmpty())
    }

    // **A `mysql://` or an `http://` is the shape of a variable set from the wrong secret**, which is
    // one `--set-secrets` typo away and is worth a sentence rather than a driver-not-found stack.
    @Test
    fun `a scheme this server does not speak is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> { databaseConnection("mysql://host.example/oltre") }

        assertTrue(VARIABLE in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    @Test
    fun `a url naming no host is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> { databaseConnection("postgresql:///oltre") }

        assertTrue(VARIABLE in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    // **Connecting to whatever turns up is worse than refusing**, because it is how a colony ends up
    // in a database nobody meant to write to and nothing says so until somebody goes looking.
    @Test
    fun `a url naming no database is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> { databaseConnection("postgresql://host.example") }

        assertTrue(VARIABLE in thrown.message.orEmpty(), thrown.message.orEmpty())
    }

    @Test
    fun `something that is not a url at all is refused`() {
        val thrown = assertFailsWith<IllegalArgumentException> { databaseConnection("host.example:not a port/oltre") }

        assertTrue(VARIABLE in thrown.message.orEmpty(), thrown.message.orEmpty())
    }
}

private const val VARIABLE = "DATABASE_URL"
