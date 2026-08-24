package dev.fardavide.oltre.client.changelog.presentation

import dev.fardavide.oltre.client.changelog.ui.BuildRowUiState
import dev.fardavide.oltre.client.changelog.ui.ChangelogPageUiState
import dev.fardavide.oltre.client.changelog.ui.ChangelogUiState
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes

// The document, as the sheet draws it. Two mappings and no decisions: the order is the document's,
// the copy is the document's, and the only thing computed here is which pages open a minor line.
fun ChangelogText.toChangelogUiState(): ChangelogUiState = ChangelogUiState(
    title = Strings.changelogTitle(),
    depth = Strings.changelogDepth(releases.size),
    pages = releases.map { release ->
        ChangelogPageUiState(
            version = release.version,
            date = Strings.releaseDate(
                day = release.date.dayOfMonth,
                month = release.date.monthNumber,
                year = release.date.year,
            ),
            // **`Raw`, and this is the one surface where that is right.** Every other `TextRes.Raw`
            // in the app is text from outside the catalogue *because it cannot be translated* — a
            // generated world name, a coordinate. This is translated, just not by `StringId`: the
            // document was chosen by language before this ran, so what is left is a string that has
            // already answered the question `TextRes` exists to defer.
            headline = TextRes(release.headline),
            notes = release.notes.map { note -> TextRes(note) },
        )
    },
    // `patch == 0` and nothing else, so the rail keeps no table and a release cut tomorrow needs
    // nothing changed here.
    minorLineStops = releases.indices.filter { index -> releases[index].version.patch == 0 },
)

// The settings sheet's door. The newest release is what the row is about — the version the player is
// running — so it reads the head of the document and nothing else.
fun ChangelogText.toBuildRowUiState(): BuildRowUiState {
    val newest = releases.first()
    return BuildRowUiState(
        label = Strings.buildLabel(),
        version = newest.version,
        headline = TextRes(newest.headline),
        spoken = Strings.buildRowSpoken(
            version = TextRes(newest.version.printed),
            headline = TextRes(newest.headline),
        ),
    )
}
