package dev.fardavide.oltre.client.auth.data

import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.IdToken
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
// **Four Objective-C *categories*, imported by name.** `setHTTPMethod`, `setHTTPBody`, `setValue` and
// the completion-handler overload of `dataTaskWithRequest` are not members of their classes — they
// are category methods, which Kotlin/Native generates as top-level extension functions in
// `platform.Foundation`. Without these lines they read as unresolved on a type that plainly has them,
// which is the single most confusing error this file can produce.
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomBytes(count: Int): ByteArray {
    val bytes = ByteArray(count)
    bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, count.toULong(), it.addressOf(0)) }
    return bytes
}

// **Both, and this is the platform where both are required.** Guideline 4.8 asks for Sign in with
// Apple wherever a third-party sign-in is offered, and the HIG's presentation rules — no smaller than
// the others, no scrolling to reach it — are what the design already answers. Apple is first on the
// screen for the same reason.
actual fun signInProviders(): Set<AuthProvider> = setOf(AuthProvider.APPLE, AuthProvider.GOOGLE)

actual fun defaultProviderSignIn(): ProviderSignIn = ProviderSignIn { provider ->
    val secrets = SignInSecrets(::secureRandomBytes)
    when (provider) {
        AuthProvider.APPLE -> appleSignIn(secrets)
        AuthProvider.GOOGLE -> googleSignIn(secrets)
    }
}

// **The native sheet, which is the only Apple flow that needs no browser and no return URL.** It is
// also the reason Apple is absent from the other two platforms: everything here is `AuthenticationS
// ervices` doing the round trip inside the process.
//
// **The nonce goes in hashed.** Apple puts what it is handed into the claim, and `SignInSecrets` is
// what knows that — see `NonceShape`.
private suspend fun appleSignIn(secrets: SignInSecrets): SignInAttempt =
    suspendCancellableCoroutine { continuation ->
        val request = ASAuthorizationAppleIDProvider().createRequest().apply {
            nonce = secrets.hashed
            // **No scopes, deliberately.** The server reads `sub` and nothing else, and a name-and-
            // email prompt asks the player to give up something the game does not keep — which is
            // also what the published App Privacy answers say. Asking for less is the whole of the
            // privacy story here.
            requestedScopes = null
        }

        val handler = object : NSObject(),
            ASAuthorizationControllerDelegateProtocol,
            ASAuthorizationControllerPresentationContextProvidingProtocol {

            override fun authorizationController(
                controller: ASAuthorizationController,
                didCompleteWithAuthorization: ASAuthorization,
            ) {
                val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
                val token = credential?.identityToken?.utf8()
                continuation.resume(
                    if (token.isNullOrBlank()) {
                        // A credential with no identity token is not something the player did — it
                        // is Apple answering oddly, and waiting is the only useful instruction.
                        SignInAttempt.Unreachable
                    } else {
                        SignInAttempt.Signed(IdToken(token), secrets.nonceFor(NonceShape.HASHED))
                    },
                )
            }

            override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
                // **1001 is *canceled* and it is the only code that means the player chose.** Every
                // other one — 1000 unknown, 1004 failed, 1005 notInteractive — is the app or the
                // service, and the sentence for those is *wait*. 1000 in particular is what a missing
                // `com.apple.developer.applesignin` entitlement produces, which is the dead control
                // the provisioning walkthrough's step 47 exists to prevent.
                continuation.resume(
                    if (didCompleteWithError.code == APPLE_CANCELED) {
                        SignInAttempt.Refused
                    } else {
                        SignInAttempt.Unreachable
                    },
                )
            }

            override fun presentationAnchorForAuthorizationController(
                controller: ASAuthorizationController,
            ): ASPresentationAnchor = keyWindow()
        }

        val controller = ASAuthorizationController(authorizationRequests = listOf(request))
        controller.delegate = handler
        controller.presentationContextProvider = handler
        // The controller holds neither of these strongly, so the handler has to outlive this frame
        // and the continuation is what keeps it alive: it captures `handler`, and nothing resumes
        // until one of the two callbacks fires.
        continuation.invokeOnCancellation { controller.delegate = null }
        controller.performRequests()
    }

// **Google without the SDK**, and the trade is worth stating. `GoogleSignIn` for iOS would be a
// CocoaPods or SPM dependency inside a *generated* Xcode project — a change nothing in this
// repository could compile-check — against a system sheet whose whole contribution is the same OAuth
// round trip this does in forty lines. `ASWebAuthenticationSession` is what the SDK uses underneath,
// the redirect is the reversed client id the provisioning walkthrough's step 48 already registers,
// and the flow is PKCE, which is what makes a public client safe.
private suspend fun googleSignIn(secrets: SignInSecrets): SignInAttempt {
    val redirect = suspendCancellableCoroutine<String?> { continuation ->
        val authorize = AuthorizeRequest(
            endpoint = OltreOAuth.GOOGLE_AUTHORIZE,
            clientId = OltreOAuth.GOOGLE_IOS_CLIENT_ID,
            redirectUri = OltreOAuth.GOOGLE_IOS_REDIRECT,
            scope = OltreOAuth.GOOGLE_SCOPE,
            secrets = secrets,
        ).url()

        val url = NSURL.URLWithString(authorize)
        if (url == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val session = ASWebAuthenticationSession(
            uRL = url,
            callbackURLScheme = OltreOAuth.GOOGLE_IOS_REDIRECT.substringBefore(':'),
        ) { callback, _ -> continuation.resume(callback?.absoluteString) }

        val anchor = object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
            override fun presentationAnchorForWebAuthenticationSession(
                session: ASWebAuthenticationSession,
            ): ASPresentationAnchor = keyWindow()
        }
        session.presentationContextProvider = anchor
        // `false` is what makes signing in a second time not ask again: the session shares Safari's
        // cookies, so a player already signed in to Google on the phone taps once.
        session.prefersEphemeralWebBrowserSession = false
        continuation.invokeOnCancellation { session.cancel() }
        if (!session.start()) continuation.resume(null)
    } ?: return SignInAttempt.Refused

    return when (val result = readRedirect(redirect, secrets)) {
        RedirectResult.Refused -> SignInAttempt.Refused
        RedirectResult.Unusable -> SignInAttempt.Unreachable
        is RedirectResult.Code -> exchange(result.value, secrets)
    }
}

// The last leg, on `NSURLSession` rather than on an engine — see `TokenExchange.kt` for why this
// module opens its own socket rather than borrowing `:client:net:data`'s.
@OptIn(ExperimentalForeignApi::class)
private suspend fun exchange(code: String, secrets: SignInSecrets): SignInAttempt =
    suspendCancellableCoroutine { continuation ->
        val body = tokenRequestBody(
            clientId = OltreOAuth.GOOGLE_IOS_CLIENT_ID,
            // Google's iOS client is a public client and has none. Sending an empty one is not the
            // same as sending nothing — the endpoint refuses it — which is why the parameter is
            // nullable rather than a string that might be blank.
            clientSecret = null,
            code = code,
            verifier = secrets.verifier,
            redirectUri = OltreOAuth.GOOGLE_IOS_REDIRECT,
        )
        val url = NSURL.URLWithString(OltreOAuth.GOOGLE_TOKEN)
        if (url == null) {
            continuation.resume(SignInAttempt.Unreachable)
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest(uRL = url).apply {
            setHTTPMethod("POST")
            setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
            setHTTPBody(body.toNSData())
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(
            request = request,
            completionHandler = { data: NSData?, _: NSURLResponse?, _: NSError? ->
                val token = data?.utf8()?.let(::readIdToken)
                continuation.resume(
                    if (token == null) {
                        SignInAttempt.Unreachable
                    } else {
                        SignInAttempt.Signed(IdToken(token), secrets.nonceFor(NonceShape.RAW))
                    },
                )
            },
        )
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }

// The window both sheets are presented over. `ASPresentationAnchor` is a `UIWindow` and the app has
// exactly one — the shell's — so this is a lookup rather than a choice.
private fun keyWindow(): ASPresentationAnchor = UIApplication.sharedApplication.windows
    .filterIsInstance<UIWindow>()
    .firstOrNull { it.isKeyWindow() }
    ?: UIWindow()

@OptIn(ExperimentalForeignApi::class)
private fun NSData.utf8(): String? =
    platform.Foundation.NSString.create(data = this, encoding = NSUTF8StringEncoding) as String?

@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData {
    val bytes = encodeToByteArray()
    return bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
}

// `ASAuthorizationError.canceled`. Named rather than inlined, because `1001` at a comparison site is
// the difference between *the player said no* and *the app is broken* and nothing else says which.
private const val APPLE_CANCELED: Long = 1001
