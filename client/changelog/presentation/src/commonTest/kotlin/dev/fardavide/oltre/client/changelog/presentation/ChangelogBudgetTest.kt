package dev.fardavide.oltre.client.changelog.presentation

import kotlin.test.Test
import kotlin.test.assertTrue

// **The budget, made real.** Claude Design raised it as a finding rather than as a nicety: *"it needs
// a test that fails the build when a headline runs past 40 or a note past 90 — otherwise the budget
// is a comment, and comments do not hold."*
//
// It holds harder than a layout check would, and that is the point of putting it here. The page slot
// is two lines high whatever the language does, so a 60-character headline would not clip, would not
// overflow, and would not fail a screenshot — it would simply push the notes down on one page out of
// sixty-five, and nobody would see it until they swiped past it.
class ChangelogBudgetTest {

    @Test
    fun `no headline runs past forty characters`() {
        forEachRelease { language, release ->
            assertTrue(
                release.headline.length <= HEADLINE_BUDGET,
                "$language ${release.version.printed}: headline is ${release.headline.length} characters",
            )
        }
    }

    @Test
    fun `no note runs past ninety characters`() {
        forEachRelease { language, release ->
            for (note in release.notes) {
                assertTrue(
                    note.length <= NOTE_BUDGET,
                    "$language ${release.version.printed}: a note is ${note.length} characters",
                )
            }
        }
    }

    @Test
    fun `every release says between one and three things`() {
        // Three is what keeps the tallest page inside a 320dp viewport — which is what keeps a page
        // from scrolling, which is the reason the cap exists rather than a taste.
        //
        // One is the other half and it is the more important one: **a page with no notes would be a
        // headline over an empty card**, and a swipe that lands on nothing is the sheet's version of
        // a dead control.
        forEachRelease { language, release ->
            assertTrue(
                release.notes.size in 1..MAX_NOTES,
                "$language ${release.version.printed}: ${release.notes.size} notes",
            )
        }
    }

    @Test
    fun `nothing is written twice on the same page`() {
        // A copy-paste between two notes of one release reads as a stutter and is invisible in a
        // diff of sixty-five entries.
        forEachRelease { language, release ->
            assertTrue(
                release.notes.distinct().size == release.notes.size,
                "$language ${release.version.printed} says the same thing twice",
            )
        }
    }

    @Test
    fun `no page is padded with a headline that is also a note`() {
        forEachRelease { language, release ->
            assertTrue(
                release.notes.none { it == release.headline },
                "$language ${release.version.printed} repeats its headline as a note",
            )
        }
    }

    private fun forEachRelease(assertion: (String, Release) -> Unit) {
        for ((language, changelog) in everyChangelog) {
            for (release in changelog.releases) assertion(language, release)
        }
    }

    private companion object {

        const val HEADLINE_BUDGET = 40
        const val NOTE_BUDGET = 90
        const val MAX_NOTES = 3
    }
}

// Every document there is. A third language added without being listed here is a language nothing
// checks — `CatalogueTest` keeps the same list for the same reason, and `changelogFor` is the other
// place a third one has to be remembered.
internal val everyChangelog: List<Pair<String, ChangelogText>> = listOf(
    "English" to EnglishChangelog,
    "Italian" to ItalianChangelog,
)
