package dev.fardavide.oltre.client.changelog.ui

import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion

object ChangelogTestTags {

    // On the contents rather than on the chrome, `SettingsTestTags.SHEET`'s precedent: the sheet is
    // raised by the composition root now, so the contents are the only thing that is the changelog
    // whichever route reached it.
    const val SHEET = "changelog-sheet"

    // How deep the run goes, in the title row. Tagged because it is the one number the sheet carries
    // that is about the *list* rather than about a release.
    const val DEPTH = "changelog-depth"

    const val RAIL = "changelog-rail"

    // The sky on a page. Tagged for one assertion and it is not a cosmetic one: the mark is a square
    // of exactly the card's column, and until 0.19 the column was a constant that was right at two
    // widths and wrong at every other. A drawing with no handle on it is a drawing whose *size* no
    // test can ask about.
    const val MARK = "changelog-mark"

    // The door from settings. Tagged on the row rather than on the mark, because the whole 44dp
    // answers — the mark is 29 and carries no target of its own.
    const val BUILD_ROW = "changelog-build-row"

    // Keyed by the version rather than by the index, so an assertion says which release it expected
    // to be looking at instead of which stop in a list that grows every few days.
    fun page(version: ReleaseVersion): String = "changelog-page-${version.printed}"
}
