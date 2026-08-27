package dev.fardavide.oltre.client.auth.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.protocol.AuthProvider

// **What the gate draws, and nothing about how it got there.** The screen holds one decision and
// states its own cost: a mark, a name, two lines of why, the providers, and — when there is one — the
// thing that just did not happen.
//
// Nothing here scrolls and nothing is optional. There is no colony behind this screen yet, so it
// carries no strip, no rail and no tab bar.
data class GateUiState(
    val why: TextRes,
    val whyFoot: TextRes,
    // The one reassurance the screen is entitled to make. It is not a consent notice: nothing has
    // happened yet, and the foot says so rather than asking for agreement.
    val foot: TextRes,
    // **The providers this build can actually complete**, which is a platform fact and not a list the
    // screen is free to shorten. A button for a flow that opens a browser and never comes back is the
    // worst control a gate has available, so a provider that cannot finish is not drawn — see
    // `signInProviders`.
    val providers: List<GateProviderUiState>,
    // Null at rest. Everything else is a lead line, a body line, and which of the two colours the
    // lead takes.
    val message: GateMessageUiState?,
)

data class GateProviderUiState(val provider: AuthProvider, val label: TextRes)

// **The failure block, which sits above the buttons rather than over them.** The buttons never move,
// the message grows upward, and the two providers *are* the retry — so the screen needs no third
// button and gets none. Never a dialog, never a toast, never a code.
data class GateMessageUiState(val lead: TextRes, val body: TextRes, val tone: GateTone)

// Two tones, because there are two kinds of thing to say and only one of them is a failure. Waiting
// is a statement in the body colour; the other three are refusals in red, which in this app means
// *this cannot happen, and here is the fact that stops it*.
enum class GateTone {

    WAITING,
    FAILED,
}

object GateTestTags {

    const val SCREEN = "gate"
    const val MESSAGE_LEAD = "gate.message.lead"
    const val MESSAGE_BODY = "gate.message.body"

    fun provider(provider: AuthProvider): String = "gate.provider.${provider.name}"
}
