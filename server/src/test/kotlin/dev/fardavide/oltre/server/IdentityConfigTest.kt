package dev.fardavide.oltre.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// **How this server reads its configuration, which is `System.getenv` and nothing else** — the shape
// `Main.kt` set at `#108` for `DATABASE_URL` and `PORT`. `read` is a parameter so the rules are
// judged here rather than by setting environment variables in a test process.
//
// **No value of any credential is in this repository**, and this file is where that is most obviously
// true: every string below is invented. The real ones are Davide's, they live outside the repo, and
// `#111` mounts them from Secret Manager.
class IdentityConfigTest {

    @Test
    fun `no signing key at all is not a failure but a server that cannot sign anybody in`() {
        // `./gradlew :server:run` with nothing in the environment, which is what the dev loop is.
        // `Main.kt` is what makes sure a *deployed* server can never be in this state.
        assertNull(identityConfig { null })
    }

    @Test
    fun `a blank signing key reads as no signing key`() {
        // A variable set to the empty string is how a misconfigured deploy usually arrives, and
        // treating it as a key would fail later and less clearly.
        assertNull(identityConfig { if (it == "SESSION_SIGNING_KEY") "   " else "client" })
    }

    @Test
    fun `a configured server accepts every client id it was given`() {
        val config = identityConfig(
            environment(
                "SESSION_SIGNING_KEY" to TEST_SIGNING_KEY.value,
                "APPLE_CLIENT_IDS" to "dev.fardavide.oltre.signin,dev.fardavide.oltre",
                "GOOGLE_CLIENT_IDS" to "$WEB_CLIENT,$DESKTOP_CLIENT",
            ),
        )

        assertEquals(
            mapOf(
                IdentityProvider.APPLE to setOf("dev.fardavide.oltre.signin", "dev.fardavide.oltre"),
                IdentityProvider.GOOGLE to setOf(WEB_CLIENT, DESKTOP_CLIENT),
            ),
            config?.audiences,
        )
    }

    @Test
    fun `whitespace and empty entries around the separators are dropped`() {
        // A list pasted out of a console is a list with spaces in it and often a trailing comma,
        // and an audience with a leading space matches nothing while looking exactly right.
        val config = identityConfig(
            environment(
                "SESSION_SIGNING_KEY" to TEST_SIGNING_KEY.value,
                "APPLE_CLIENT_IDS" to " one , two ,",
                "GOOGLE_CLIENT_IDS" to "three",
            ),
        )

        assertEquals(setOf("one", "two"), config?.audiences?.get(IdentityProvider.APPLE))
    }

    @Test
    fun `a key with no apple client ids refuses to be a configuration at all`() {
        // **`#106` §4: it is both providers or neither.** Apple mandates Sign in with Apple wherever
        // a third-party login is offered, so a server configured for Google alone is one that cannot
        // pass review — and booting it would put a sign-in button on a screen with nothing behind it.
        val failure = assertFailsWith<IllegalArgumentException> {
            identityConfig(
                environment(
                    "SESSION_SIGNING_KEY" to TEST_SIGNING_KEY.value,
                    "GOOGLE_CLIENT_IDS" to WEB_CLIENT,
                ),
            )
        }

        assertEquals(true, failure.message?.contains("APPLE_CLIENT_IDS"))
    }

    @Test
    fun `a key with no google client ids refuses just as flatly`() {
        assertFailsWith<IllegalArgumentException> {
            identityConfig(
                environment(
                    "SESSION_SIGNING_KEY" to TEST_SIGNING_KEY.value,
                    "APPLE_CLIENT_IDS" to "dev.fardavide.oltre",
                ),
            )
        }
    }

    @Test
    fun `a signing key too short to sign with fails at boot rather than at the first sign-in`() {
        assertFailsWith<IllegalArgumentException> {
            identityConfig(
                environment(
                    "SESSION_SIGNING_KEY" to "short",
                    "APPLE_CLIENT_IDS" to "a",
                    "GOOGLE_CLIENT_IDS" to "b",
                ),
            )
        }
    }

    @Test
    fun `the specs a configuration builds carry the providers' own issuers and key sets`() {
        // The audiences are Davide's and come from the environment; the issuer and the key-set URL
        // are facts about Apple and Google and are written down in `IdentityProvider`. A deployment
        // cannot get either of the second pair wrong, which is the point of the split.
        val config = identityConfig(
            environment(
                "SESSION_SIGNING_KEY" to TEST_SIGNING_KEY.value,
                "APPLE_CLIENT_IDS" to "dev.fardavide.oltre",
                "GOOGLE_CLIENT_IDS" to WEB_CLIENT,
            ),
        )

        val apple = config?.specs()?.getValue(IdentityProvider.APPLE)
        assertEquals(setOf("https://appleid.apple.com"), apple?.issuers)
        assertEquals("https://appleid.apple.com/auth/keys", apple?.jwksUri)
        assertEquals(setOf("dev.fardavide.oltre"), apple?.audiences)
    }

    // ── The vocabulary's own guards ───────────────────────────────────────────────────────────

    @Test
    fun `an identity that names nobody cannot be built`() {
        // Three strings mean "a player" in this module — the id, the subject, and whatever was in
        // `X-Oltre-Player` — and an empty one of any of them would key a row nobody can reach again.
        assertFailsWith<IllegalArgumentException> { ProviderIdentity(ProviderName("google"), "") }
        assertFailsWith<IllegalArgumentException> { ProviderName("  ") }
    }

    @Test
    fun `a provider that would accept nothing or trust nobody cannot be built`() {
        // Both of these are servers that look configured and refuse every token, which is the
        // failure a `Set` makes silent unless the emptiness is refused up front.
        assertFailsWith<IllegalArgumentException> {
            ProviderSpec(IdentityProvider.GOOGLE, issuers = emptySet(), audiences = setOf("a"), jwksUri = "u")
        }
        assertFailsWith<IllegalArgumentException> {
            ProviderSpec(IdentityProvider.GOOGLE, issuers = setOf("i"), audiences = emptySet(), jwksUri = "u")
        }
    }

    private fun environment(vararg entries: Pair<String, String>): (String) -> String? {
        val values = entries.toMap()
        return { values[it] }
    }
}
