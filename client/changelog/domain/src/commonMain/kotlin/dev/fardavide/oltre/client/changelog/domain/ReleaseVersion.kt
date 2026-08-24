package dev.fardavide.oltre.client.changelog.domain

// **A release, as three integers rather than as the string it is written with.** The string is what
// `gradle/libs.versions.toml` holds and what a preferences file remembers; everything this feature
// does with a version — ordering sixty-five pages, deciding whether a build is new, drawing a mark
// from it — is arithmetic, and text does none of it. `"0.9.0" < "0.10.0"` is false as text and true
// as a release, which alone is the argument.
//
// Deliberately not a `Comparable<ReleaseVersion>` by hand: the `compareTo` below is the only order
// this type has, and a data class gives equality and hashing for free.
data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<ReleaseVersion> {

    // Named `printed` rather than overriding `toString`, so the one place a version becomes text is
    // a call somebody wrote. A version reaches a screen and a preferences file, and both should be
    // reading a documented format rather than whatever `toString` happens to be today.
    val printed: String get() = "$major.$minor.$patch"

    override fun compareTo(other: ReleaseVersion): Int = when {
        major != other.major -> major compareTo other.major
        minor != other.minor -> minor compareTo other.minor
        else -> patch compareTo other.patch
    }

    companion object {

        // **Null for anything that is not exactly three non-negative numbers**, because every caller
        // already has a good answer for "this is not a version": the preferences file treats it as
        // nothing remembered, which is the same answer a first launch gets. Throwing would turn a
        // file written by a build that never heard of this feature into a crash on launch —
        // `PreferencesStore.load` makes the same call one layer down and for the same stake.
        fun parse(text: String): ReleaseVersion? {
            val parts = text.split('.')
            if (parts.size != 3) return null
            val numbers = parts.map { part -> part.toIntOrNull() ?: return null }
            if (numbers.any { it < 0 }) return null
            return ReleaseVersion(major = numbers[0], minor = numbers[1], patch = numbers[2])
        }
    }
}
