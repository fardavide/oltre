package dev.fardavide.oltre.client.design.text

// Explicit, because `@JvmInline` is only auto-imported on the JVM: without it this file compiles
// everywhere except Kotlin/Native, which is the half of CI a desktop run never reaches.
import kotlin.jvm.JvmInline

// **What the game says, before anything knows how to draw it.** Named `TextRes` rather than `Text`
// because Compose's `Text` is in scope in every file that would use this — the collision would be
// with the type that is used most, and an import alias in the busiest files is not a name.
//
// A `UiState` field is a `TextRes` and never a `String`. The **only** `String` left in the UI half is
// the one `Translations.resolve` hands to `Text(…)` at the leaf, which is what "no bare strings till
// DS components" means in practice.
//
// Two properties are worth protecting in review, and both come free from `data class`:
//
// - **A test asserts on meaning, not on copy.** `assertEquals(Strings.hullsInFleet(3), head.label)`
//   passes when the wording changes and fails when the *message* changes.
// - **Equality is the `String`'s.** Every existing ui-state comparison, fixture and frame keeps
//   working, because a `TextRes` compares like the string it replaced.
sealed interface TextRes {

    // Text from outside the catalogue, and therefore untranslatable by construction: a generated
    // system name, a world's epithet, a value that came from a server. Reached through
    // `TextRes(value)`, which reads as a cast rather than as a wrapper — at a call site the bare
    // string should be the exception, not a ceremony.
    @JvmInline
    value class Raw(val value: String) : TextRes

    // A catalogue entry bound to its arguments. **The constructor is `internal` and the copy is
    // internal with it** (`@ConsistentCopyVisibility`, or `copy()` would be the hole the constructor
    // is not) — `Strings` is the only way to make one, which is what makes the typed-argument
    // requirement hold rather than merely be encouraged. `Strings.hullsInFleet("two")` does not
    // compile, and there is no way round it.
    @ConsistentCopyVisibility
    data class Message internal constructor(val id: StringId, val args: List<Arg>) : TextRes

    // Composition, so a row builds "6 owned · 1 idle · 5 away" without a catalogue entry per
    // combination of clauses. The separator is a `TextRes` like any other rather than a `String`,
    // so a locale that punctuates a list differently changes one entry instead of every mapper that
    // builds one — see `Strings.clauses`.
    data class Joined(val parts: List<TextRes>, val separator: TextRes) : TextRes

    companion object {

        operator fun invoke(value: String): TextRes = Raw(value)
    }
}

// The typed half of the contract. `Strings` names the argument types at the call site — where the
// mistake would actually be made — and these are what the catalogue reads back out.
sealed interface Arg {

    // A number that selects a plural form. Distinct from `Number` on purpose: they are the same
    // integer and mean different things, and only one of them may change the words around it.
    @JvmInline
    value class Count(val value: Int) : Arg

    // A number printed as digits, ungrouped: a level, an hour, a percentage. Grouping is a
    // *sentence* rather than a property of every number — `Strings.groupedNumber` is the entry that
    // asks for it, because "2,000h of payback" is not how this app writes a payback.
    @JvmInline
    value class Number(val value: Long) : Arg

    // A fixed-point number: `scaled` divided by ten to the `decimals`. 262 at two decimals is 2.62.
    // Carried as an integer rather than as a `Double` because that is how `core` stores every
    // physical quantity in the game, and because the decimal *separator* is the locale's — a
    // pre-rendered "2.62" would have baked English into the argument.
    data class Decimal(val scaled: Long, val decimals: Int, val trimTrailingZeros: Boolean) : Arg

    // Text inside text, which is how a message stays one entry when one of its terms is itself
    // translated — or is a world's name and therefore is not.
    @JvmInline
    value class Text(val value: TextRes) : Arg
}
