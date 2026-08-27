package dev.fardavide.oltre.client.auth.presentation

import dev.fardavide.oltre.client.auth.ui.GateMessageUiState
import dev.fardavide.oltre.client.auth.ui.GateProviderUiState
import dev.fardavide.oltre.client.auth.ui.GateTone
import dev.fardavide.oltre.client.auth.ui.GateUiState
import dev.fardavide.oltre.client.design.text.AuthProviderName
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.protocol.AuthProvider

// **Where the gate is**, and it is five states because there are five different things to say and no
// more. Everything the screen shows is a function of this and of which providers the platform can
// complete; nothing else about the sign-in reaches a composable.
sealed interface GateState {

    // At rest. The screen has nothing to report and the two providers are the whole of it.
    data object Idle : GateState

    // A provider sheet is up, or its answer is on its way to the server. **One state for both**,
    // because they are one instruction — wait — and the app cannot draw the difference honestly: it
    // does not know whether a sheet the player is looking at will be answered.
    data object Waiting : GateState

    // Nothing answered. No signal and a service that is down are the same screen.
    data object NoAnswer : GateState

    // The provider refused, or the player backed out. One member, and it carries which button was
    // pressed because the sentence names it and names the other one.
    data class Refused(val provider: AuthProvider) : GateState

    // **The server sent a number of seconds and the screen prints it, recomputed rather than counted
    // down.** No timers, ever, is a system rule older than this screen, and a digit moving on its own
    // at the gate would be the one place in the product that broke it. Zero and below is its own
    // sentence — *you can ask again now* — because re-enabling a button silently is a control changing
    // meaning while nobody is looking.
    data class Throttled(val retryInSeconds: Int) : GateState
}

// The screen, in the words the design settled. Every branch is one sentence and its lead colour; the
// two lines of *why* and the foot never change, because they are the screen's own subject rather than
// a report on what just happened.
fun GateState.toGateUiState(providers: Set<AuthProvider>): GateUiState = GateUiState(
    why = Strings.signInWhyLead(),
    whyFoot = Strings.signInWhyFoot(),
    foot = Strings.signInFoot(),
    // **Apple first, because this is iPhone first** — and because the HIG asks that its button not be
    // below the others. Ordered here rather than by the platform's own set, so a platform that
    // answered them in the other order could not quietly reorder the screen.
    providers = AuthProvider.entries
        .filter { it in providers }
        .map { GateProviderUiState(provider = it, label = Strings.signInWith(it.spoken())) },
    // **A gate with no button says why, and that sentence outranks every other one.** Absence is the
    // right answer for a provider this build cannot finish — a button opening a browser that never
    // comes back is the worst control a gate has — but absence applied to all of them leaves two
    // lines of *why* and no way forward, which reads as the app having failed to load rather than as
    // a deliberate gap. Nothing else can be true at the same time: with nothing to press, no other
    // state is reachable.
    message = if (providers.isEmpty()) {
        GateMessageUiState(
            lead = Strings.signInNoProviderLead(),
            body = Strings.signInNoProviderBody(),
            tone = GateTone.FAILED,
        )
    } else {
        message()
    },
)

private fun GateState.message(): GateMessageUiState? = when (this) {
    GateState.Idle -> null

    GateState.Waiting -> GateMessageUiState(
        lead = Strings.signInWaitingLead(),
        body = Strings.signInWaitingBody(),
        // Not a failure, so not red. It is a statement in the body colour, and nothing on the screen
        // moves while it is up.
        tone = GateTone.WAITING,
    )

    GateState.NoAnswer -> GateMessageUiState(
        lead = Strings.signInNoAnswerLead(),
        body = Strings.signInNoAnswerBody(),
        tone = GateTone.FAILED,
    )

    is GateState.Refused -> GateMessageUiState(
        lead = Strings.signInRefusedLead(provider.spoken()),
        // **The other one**, which is the next thing to try and is already on the screen. With one
        // provider available there is no other one to name, so the sentence names the same button
        // again — which is honest: it *is* the thing to try again.
        body = Strings.signInRefusedBody(provider.other().spoken()),
        tone = GateTone.FAILED,
    )

    is GateState.Throttled -> GateMessageUiState(
        lead = Strings.signInThrottledLead(),
        body = if (retryInSeconds > 0) {
            Strings.signInThrottledBody(retryInSeconds)
        } else {
            Strings.signInAskAgainNow()
        },
        tone = GateTone.FAILED,
    )
}

// The two vocabularies meeting, and this is the only place they do. `:client:design:text` names no
// wire type — a table of words has no business on the contract's compile classpath — and `:protocol`
// names no words, so the mapping is one `when` in a presentation module rather than a dependency
// either of them would have had to carry.
private fun AuthProvider.spoken(): AuthProviderName = when (this) {
    AuthProvider.APPLE -> AuthProviderName.APPLE
    AuthProvider.GOOGLE -> AuthProviderName.GOOGLE
}

private fun AuthProvider.other(): AuthProvider = when (this) {
    AuthProvider.APPLE -> AuthProvider.GOOGLE
    AuthProvider.GOOGLE -> AuthProvider.APPLE
}
