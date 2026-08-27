package dev.fardavide.oltre.client.auth.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.IdToken
import java.security.SecureRandom

actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }

// Credential Manager raises a system sheet, so it needs an **Activity** and not the application
// context — the one place in this repository where that is true, and the reason this host differs
// from `AndroidNotificationHost` and `AndroidShakeHost` by one word.
//
// Cleared in `onDestroy`, which is not tidiness: an Activity held past its own death is a leaked
// window, and the process outlives it whenever the app is backgrounded.
object AndroidSignInHost {

    var activity: Activity? = null
}

// **Google alone, and Apple is absent for the reason the desktop build's is** — its Android flow is a
// browser round trip through `https://api.oltre.space/v1/auth/apple/callback`, which is a server
// endpoint `#113` puts out of scope by name. Apple imposes no obligation to offer it here: guideline
// 4.8 is an App Store rule and this is not one.
actual fun signInProviders(): Set<AuthProvider> = setOf(AuthProvider.GOOGLE)

actual fun defaultProviderSignIn(): ProviderSignIn = ProviderSignIn { provider ->
    val activity = AndroidSignInHost.activity
    if (provider != AuthProvider.GOOGLE || activity == null) return@ProviderSignIn SignInAttempt.Unreachable

    // **The nonce is drawn here and never leaves**: Credential Manager puts it in the token's claim
    // and the server compares. Raw rather than hashed — that is Apple's shape and this is Google's.
    val secrets = SignInSecrets(::secureRandomBytes)
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(
            // **The *server* client id, which is the Web one**, so the token's audience is the
            // audience `api.oltre.space` already accepts. The Android client ids are bound to the
            // signing certificate and are named nowhere in this repository, which is what the
            // provisioning walkthrough says and why it says it.
            GetSignInWithGoogleOption.Builder(OltreOAuth.GOOGLE_WEB_CLIENT_ID)
                .setNonce(secrets.raw)
                .build(),
        )
        .build()

    try {
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        SignInAttempt.Signed(IdToken(token), secrets.nonceFor(NonceShape.RAW))
    } catch (_: GetCredentialCancellationException) {
        // The player dismissed the sheet. One sentence covers this and a declined consent, because
        // the app cannot always tell them apart and an accusation is worse than a fact.
        SignInAttempt.Refused
    } catch (_: NoCredentialException) {
        // No Google account on the device, or Play Services cannot offer one. **Refused rather than
        // unreachable**, and the difference is the instruction: waiting will not help, and the
        // sentence that names the other provider is the useful one.
        SignInAttempt.Refused
    } catch (_: GetCredentialException) {
        // Everything else Credential Manager raises, which is dominated by *"could not reach
        // Google"*. `Unreachable` is the honest reading and it is also the safe one: it tells the
        // player to try again, where a refusal would tell them to try something else.
        SignInAttempt.Unreachable
    } catch (_: IllegalArgumentException) {
        // `createFrom` on a credential that is not a Google ID token. It cannot happen with one
        // option in the request, and it is caught anyway: nothing may throw past this function, or
        // the gate stops being a screen and becomes a crash.
        SignInAttempt.Unreachable
    }
}
