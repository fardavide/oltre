package dev.fardavide.oltre.server

// **Who a colony belongs to, stated as the two things a provider actually tells us.** Nothing here
// performs I/O, reads a clock or knows what a socket is: it is the vocabulary the rest of the slice
// is written in, and it is in its own file for `Genesis.kt`'s reason — a unit test that named one of
// these must not drag a Ktor module or a JDBC driver in behind it.

// The provider's own name for the player, and **not their email** — `#106` §4, and Davide's call.
// Less to hold, less to leak, less to answer for under GDPR; and Apple hands out a private-relay
// address anyway, so the email buys nothing but a thing to lose.
//
// `subject` is opaque and provider-scoped: Apple and Google can both mint `"1234"` and mean two
// different people, which is why the pair travels together and why the table's unique index is on
// both columns rather than on the subject alone.
internal data class ProviderIdentity(val provider: ProviderName, val subject: String) {

    init {
        require(subject.isNotBlank()) { "a subject is what the provider called them, not an absence" }
    }
}

// The `players.provider` column, as a type rather than as a bare string. It is a wrapper for the
// reason `PlayerId` is one: three different strings end up in that column and only one of them is a
// real provider, so the compiler should be able to find every place that decides which.
@JvmInline
internal value class ProviderName(val value: String) {

    init {
        require(value.isNotBlank()) { "a provider has a name" }
    }

    companion object {

        // **What `#109` wrote into the column and this slice did not delete.** A request that names
        // its player in `X-Oltre-Player` still resolves to a row, under this name, and that path
        // exists only when no session key is configured — which is `./gradlew :server:run` and
        // nothing else. See `Authenticator.kt`, and trap 1 of `#110`: the header comes out at `#113`
        // when the client starts sending a bearer token, not here.
        val HEADER: ProviderName = ProviderName("header")
    }
}

// **The two providers, and it is both or neither** — `#106` §4. Apple mandates Sign in with Apple
// wherever a third-party social login is offered, so this is an enum with exactly two members and
// no configuration that can turn one of them off.
//
// The issuer and the key set are *facts about the provider* rather than configuration, so they are
// written down here; only the audiences are Davide's, and those come from the environment. A test
// builds its own `ProviderSpec` against a keypair it generated, which is the whole of why the spec
// is a parameter and these are only its defaults.
internal enum class IdentityProvider(
    val providerName: ProviderName,
    val issuers: Set<String>,
    val jwksUri: String,
    val audienceVariable: String,
) {

    APPLE(
        providerName = ProviderName("apple"),
        issuers = setOf("https://appleid.apple.com"),
        jwksUri = "https://appleid.apple.com/auth/keys",
        audienceVariable = "APPLE_CLIENT_IDS",
    ),

    // **Two spellings of one issuer, and both are Google's own.** Its discovery document says
    // `https://accounts.google.com` and its tokens have carried the bare host for years; Google's
    // own guidance is to accept either. A verifier that pinned one of them would work until the day
    // it did not, with nothing to say why.
    GOOGLE(
        providerName = ProviderName("google"),
        issuers = setOf("https://accounts.google.com", "accounts.google.com"),
        jwksUri = "https://www.googleapis.com/oauth2/v3/certs",
        audienceVariable = "GOOGLE_CLIENT_IDS",
    ),
}

// One provider, and what this deployment will accept from it.
//
// **`audiences` is a set and never a single value, and that is the one thing in this slice most
// likely to be got wrong.** A token's `aud` is the OAuth client it was minted for, and there is a
// client per platform: the Web client answers for both phones, and the desktop dev loop has one of
// its own. A single-audience check passes every test written against a generated keypair and then
// refuses the desktop build — which is the only build the behaviour and screenshot suites run.
internal data class ProviderSpec(
    val provider: IdentityProvider,
    val issuers: Set<String>,
    val audiences: Set<String>,
    val jwksUri: String,
) {

    init {
        require(issuers.isNotEmpty()) { "a token has to have been issued by somebody" }
        require(audiences.isNotEmpty()) { "a provider with no accepted audience accepts nothing" }
    }
}

// The signing key for the app's **own** session tokens, which is a different thing from anything a
// provider holds: the providers prove who somebody is once, and this is what says they are still
// the same somebody on the next request.
//
// **It is read from the environment and never generated.** A server that minted a key at startup
// would sign every player out on each scale-up from zero, and Cloud Run scales up from zero all day.
@JvmInline
internal value class SessionSigningKey(val value: String) {

    init {
        // HMAC-SHA256 is only as strong as its key, and Nimbus refuses a shorter one outright — so
        // the failure without this line is a stack trace at the first sign-in rather than at boot.
        require(value.encodeToByteArray().size >= MINIMUM_BYTES) {
            "a session signing key needs at least $MINIMUM_BYTES bytes and had ${value.encodeToByteArray().size}"
        }
    }

    private companion object {

        const val MINIMUM_BYTES = 32
    }
}

// Everything sign-in needs that this repository does not hold: the key, and who each provider will
// mint tokens for. **No value of any of these is ever committed** — they are Davide's, they live at
// `~/Documents/Keys/Oltre/identity/`, and `#111` is what mounts them from Secret Manager.
internal data class IdentityConfig(
    val signingKey: SessionSigningKey,
    val audiences: Map<IdentityProvider, Set<String>>,
) {

    fun specs(): Map<IdentityProvider, ProviderSpec> = audiences.mapValues { (provider, accepted) ->
        ProviderSpec(
            provider = provider,
            issuers = provider.issuers,
            audiences = accepted,
            jwksUri = provider.jwksUri,
        )
    }
}

private const val SESSION_KEY_VARIABLE = "SESSION_SIGNING_KEY"

// **How this server reads its configuration, which is `System.getenv` and nothing else** — the shape
// `Main.kt` set for `DATABASE_URL` and `PORT` at `#108`. `read` is a parameter rather than a call so
// that the rules below are judged by a plain unit test; `Main.kt` supplies the real one.
//
// **Null is "identity is not configured", and it is a state this server is allowed to be in.** With
// no key there is nothing to sign a session with, so `./gradlew :server:run` falls back to naming a
// player in a header exactly as it falls back to a colony that dies with the process. What must
// never happen is a *deployed* server doing that quietly, which is why `Main.kt` refuses to start
// when a `DATABASE_URL` is set and this is not.
//
// **Missing audiences with a key present is a failure and not a fallback**, and that is `#106` §4
// reaching the code: Apple mandates Sign in with Apple wherever Google is offered, so a deployment
// with one provider configured is a deployment that cannot pass review. Booting anyway would put a
// sign-in button on a screen with nothing behind it, which is the dead-control rule arriving on a
// server.
internal fun identityConfig(read: (String) -> String?): IdentityConfig? {
    val key = read(SESSION_KEY_VARIABLE)?.takeIf { it.isNotBlank() } ?: return null
    val audiences = IdentityProvider.entries.associateWith { provider ->
        val accepted = read(provider.audienceVariable).orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        require(accepted.isNotEmpty()) {
            "$SESSION_KEY_VARIABLE is set but ${provider.audienceVariable} is not: " +
                "sign-in is both providers or neither, so this server would ship a button with " +
                "nothing behind it"
        }
        accepted
    }
    return IdentityConfig(SessionSigningKey(key), audiences)
}
