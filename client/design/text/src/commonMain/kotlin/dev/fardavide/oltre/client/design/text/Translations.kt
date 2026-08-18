package dev.fardavide.oltre.client.design.text

// One language's words. **`Strings` is the factory and this is the table** — the two halves are
// deliberately different objects, because a `presentation` module builds text long before anything
// knows which language will draw it, and a notification is written into the OS's own database hours
// before it is read.
//
// Each implementation resolves an exhaustive `when` over `StringId`, so **a new id fails to compile
// in every language until it has been translated** — the same no-`else` discipline `core` holds
// itself to, applied to copy.
interface Translations {

    // The shape, which no language overrides: a raw string is itself, a joined run is its parts, and
    // a catalogue entry is the one thing a language actually has to answer.
    fun resolve(text: TextRes): String = when (text) {
        is TextRes.Raw -> text.value
        is TextRes.Message -> resolve(text.id, text.args)
        is TextRes.Joined -> text.parts.joinToString(separator = resolve(text.separator)) { resolve(it) }
    }

    // The words. `args` is a `List` rather than a typed record because the type safety is spent at
    // the *call site* — `Strings` is the only thing that can build a `Message`, so by the time an
    // argument reaches here it is the one the signature named.
    fun resolve(id: StringId, args: List<Arg>): String
}
