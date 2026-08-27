package dev.fardavide.oltre.client.auth.data

// **The public half of what was provisioned on 2026-08-25**, in the repository because it is public
// by construction: an OAuth client id travels in every redirect a browser makes, and Apple's two are
// already in `iosApp/project.yml`. `.claude/docs/identity-provisioning.md`'s inventory records
// `google.env` as *"None secret"* and the server's own audiences are repository **variables** rather
// than secrets for exactly this reason.
//
// **What is not here is the one thing that is a secret**, and it is the desktop client's — see
// `DefaultProviderSignIn.desktop.kt`, which reads it from the environment the dev loop already
// sources and draws no Google button at all when it is absent.
//
// **Four client ids and not one, because Google issues one per client type** and the audience of the
// token the server has to believe is whichever one minted it. The server accepts a list
// (`GOOGLE_CLIENT_IDS`), which is what makes a fourth platform a settings change.
internal object OltreOAuth {

    // The audience the two phones' tokens carry. Android's Credential Manager is handed this as the
    // *server* client id and mints a token for it, which is why the Android client ids are named
    // nowhere in this repository and need not be.
    const val GOOGLE_WEB_CLIENT_ID =
        "640101315805-lm195vosbjoj417f8c3dsp9phaadv6h7.apps.googleusercontent.com"

    // The iOS client, a public client with no secret. Its redirect is the reversed form below, which
    // is the scheme `iosApp/project.yml` registers — step 48 of the provisioning walkthrough, and the
    // one whose absence is a browser that completes the flow and never comes back.
    const val GOOGLE_IOS_CLIENT_ID =
        "640101315805-rb18lnl9jevi2jact1mbartc82in9u85.apps.googleusercontent.com"

    const val GOOGLE_IOS_REDIRECT =
        "com.googleusercontent.apps.640101315805-rb18lnl9jevi2jact1mbartc82in9u85:/oauth2redirect"

    // The desktop client. Google calls this an *installed application*, its secret is documented as
    // not confidential, and it still has to be sent to the token endpoint — which is why the desktop
    // build reads it from the environment rather than carrying it.
    const val GOOGLE_DESKTOP_CLIENT_ID =
        "640101315805-n76952mam9k325g6gi20vibti6o49bkb.apps.googleusercontent.com"

    const val GOOGLE_AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth"

    const val GOOGLE_TOKEN = "https://oauth2.googleapis.com/token"

    // `openid` alone would be enough for the server, which reads `sub` and nothing else. `email` is
    // asked for because the consent sheet is much less alarming when it says what it is for, and
    // because the token's `email` claim is what a support conversation will need the day one happens.
    // It is **not retained** — see the App Privacy answers in the provisioning walkthrough, which say
    // so and are wrong the moment a raw token is logged.
    const val GOOGLE_SCOPE = "openid email"
}
