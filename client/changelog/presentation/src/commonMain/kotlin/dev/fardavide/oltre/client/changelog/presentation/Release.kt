package dev.fardavide.oltre.client.changelog.presentation

import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import kotlinx.datetime.LocalDate

// One release, as a player is told about it. **Not the README entry shortened**: those run to twenty
// lines and are written for somebody reading a repository. This is what the release *did*, said to
// somebody who has never seen a diff.
//
// The strings are `String` rather than `TextRes`, and that is the one place this feature departs from
// "a ui model carries no bare strings". The reason is in `.claude/docs/changelog-sheet.md` §4: the
// changelog is a **document**, chosen per language before a page is built, so by the time a `Release`
// exists the language has already been answered. What reaches the ui state is a `TextRes.Raw`
// carrying exactly that.
data class Release(
    val version: ReleaseVersion,
    val date: LocalDate,
    // At most 40 characters. The budget is fixed in the design sheet rather than by the layout, and
    // `ReleaseBudgetTest` is what stops it being a comment.
    val headline: String,
    // One to three, at most 90 characters each. Three is what keeps the tallest page inside a 320dp
    // viewport, which is what keeps a page from scrolling.
    val notes: List<String>,
)

// One language's changelog. Two implementations, and a third language adds a third document beside
// them — the same shape `Translations` has, for the same reason.
//
// **What holds the two together is a test rather than a compiler**, and it holds more than a
// compiler could: `ChangelogTranslationTest` asserts the two documents carry the same versions in
// the same order, the same dates, and the same number of notes per release. An exhaustive `when`
// over an id can only catch the first of those.
interface ChangelogText {

    // Newest first, always. The sheet opens on `first()` and the rail's left end is it.
    val releases: List<Release>
}

// The same call `translationsFor` makes, and deliberately a second one rather than a field on
// `Translations`: the design system may not hold the game's content, and a changelog is content.
// English is the fallback for the same reason it is there — there is no locale that resolves to
// nothing, so there is no missing-changelog state to design.
fun changelogFor(languageTag: String): ChangelogText =
    when (languageTag.substringBefore('-').substringBefore('_').lowercase()) {
        "it" -> ItalianChangelog
        else -> EnglishChangelog
    }

// The one place a release is written down in a document, kept short so that an entry reads as the
// page it becomes rather than as a constructor call. `date` is an ISO string parsed at load: it is
// checked against the README by `ReleaseCatalogueIntegrationTest`, so a typo is a failing build
// rather than a wrong caption.
internal fun release(
    version: String,
    date: String,
    headline: String,
    vararg notes: String,
): Release = Release(
    version = requireNotNull(ReleaseVersion.parse(version)) { "$version is not a version" },
    date = LocalDate.parse(date),
    headline = headline,
    notes = notes.toList(),
)
