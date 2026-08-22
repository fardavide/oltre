package dev.fardavide.oltre.client.design.text

// **The whole of "how the game chooses its language."** Davide's call, 2026-08-16: the system locale,
// no picker, no design round trip — so the decision reduces to this function, and the shell's job is
// to read a tag and hand the answer to the two things that need it.
//
// That call also said *no settings surface*, and 0.16.0 put a settings button on the frame. **It does
// not reopen this.** What was settled is that the language is the device's and there is nothing to
// choose; a settings screen that never existed was the evidence for that rather than the reason. When
// there is one, the language still will not be in it.
//
// Pure, and in this module rather than in the shell, for the reason everything else here is: the
// notification scheduler needs the same answer as the UI and cannot see a `CompositionLocal`, so the
// mapping has to live where both of them can reach it and where it can be tested without a device.
//
// **Total by construction.** English is the fallback rather than one entry among several, so there is
// no locale that resolves to nothing and no missing-translation state to design. A third language
// adds a branch here and a table beside `Italian`; nothing else moves.
fun translationsFor(languageTag: String): Translations =
    when (languageTag.languageSubtag()) {
        "it" -> Italian
        else -> English
    }

// The language subtag and nothing else. Region is dropped rather than consulted — `it-CH` is the same
// Italian as `it-IT`, because there is no per-region catalogue and there should not be one — and both
// separators are accepted because a platform hands back whichever it prefers: a BCP 47 tag uses `-`
// and a JVM `Locale.toString()` uses `_`.
//
// Matched whole rather than by prefix, so `ita` and `italian` are not Italian. Neither is a tag any
// platform hands back here, and a prefix match is the kind of thing that is right until the day it
// silently is not.
private fun String.languageSubtag(): String = substringBefore('-').substringBefore('_').lowercase()
