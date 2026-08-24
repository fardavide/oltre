package dev.fardavide.oltre.client.save.data

// Everything the app remembers that is not the colony. Deliberately *not* part of `GameSave`:
// `:core` is the pure simulation and where a tab last landed is no rule of it, and folding a
// preference into the snapshot would make every colony on disk migrate for a field the simulation
// never reads. So it gets a file of its own, and losing that file costs a player a landing view
// rather than a colony — which is why this record carries no schema version and never migrates.
//
// **Not `@Serializable` since 0.19**, and that is what lets it stay strict. A second field arrived,
// and a required field added to a serialized record turns every file written by an older build into
// a parse failure — which here means a player who upgrades silently loses the landing they chose.
// `PreferencesStore` decodes a record of its own with a default per field and maps it to this one,
// so the file is additive forever and this record keeps the property the style rule asks for: no
// defaults in the constructor, so a caller building one has to answer every field.
data class Preferences(
    // Which of the Galaxy tab's two views the player last used, as the *name* of a view rather
    // than the view itself. The enum it stands for lives in `:client:galaxy:presentation`, and
    // module rule 4 forbids a `data` module from seeing a `presentation` one — so the composition
    // root is what maps the two, writing the name on the way down and resolving it on the way up.
    // A `String?` here is the honest shape of that: this module carries a name through and has no
    // opinion about what it names. Null is a player who has never chosen, and a name this build
    // cannot resolve is the same answer again, which is what makes a downgrade harmless.
    val galaxyLanding: String?,
    // The version whose changelog the player has read, as the *string* a release is written with
    // rather than as the three numbers it means — `:client:changelog:domain` owns `ReleaseVersion`
    // and module rule 2 keeps a data module out of a feature's domain anyway. It is `galaxyLanding`'s
    // argument again: this module carries a value through and has no opinion about what it names, so
    // a version this build cannot parse reads as nothing remembered, which is also what a first
    // launch gets. Null is a player who has never been shown a changelog.
    val lastSeenVersion: String?,
) {

    companion object {

        // A first launch, and every failure `PreferencesStore.load` swallows. Named rather than
        // defaulted into the constructor, so a caller building preferences has to say what it
        // wants in every field and the compiler catches the one it forgot.
        val NONE: Preferences = Preferences(galaxyLanding = null, lastSeenVersion = null)
    }
}
