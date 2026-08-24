package dev.fardavide.oltre.client.changelog.presentation

import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// The document as the sheet reads it. Two mappings, and the only thing either of them computes is
// which pages open a minor line — everything else is the document's own order and the document's own
// words, which is what makes this short.
class ChangelogUiStateMapperTest {

    @Test
    fun `the pages are the releases in the order the document lists them`() {
        val uiState = EnglishChangelog.toChangelogUiState()

        assertEquals(
            EnglishChangelog.releases.map { it.version },
            uiState.pages.map { it.version },
        )
    }

    @Test
    fun `the depth is the number of releases`() {
        val uiState = EnglishChangelog.toChangelogUiState()

        assertEquals(English.resolve(uiState.depth), "${EnglishChangelog.releases.size} releases")
    }

    @Test
    fun `a date is written the way the language writes it`() {
        // The one thing on a page that is not carried through verbatim. English abbreviates the
        // month and puts the day first; Italian does the same in lower case, which is what the two
        // catalogue tables hold.
        val english = EnglishChangelog.toChangelogUiState().pages.last()
        val italian = ItalianChangelog.toChangelogUiState().pages.last()

        assertEquals("5 Aug 2026", English.resolve(english.date))
        assertEquals("5 ago 2026", Italian.resolve(italian.date))
    }

    @Test
    fun `a stop is a release that opens a minor line`() {
        // `patch == 0` and nothing else, which is what lets the rail keep no table. Asserted against
        // the document rather than against a fixture, because the property is about the run.
        val uiState = EnglishChangelog.toChangelogUiState()

        assertEquals(
            EnglishChangelog.releases.indices.filter { EnglishChangelog.releases[it].version.patch == 0 },
            uiState.minorLineStops,
        )
        assertTrue(uiState.minorLineStops.isNotEmpty())
    }

    @Test
    fun `every page carries the words its release was written with`() {
        val uiState = EnglishChangelog.toChangelogUiState()

        for ((page, release) in uiState.pages.zip(EnglishChangelog.releases)) {
            assertEquals(release.headline, English.resolve(page.headline))
            assertEquals(release.notes, page.notes.map { English.resolve(it) })
        }
    }

    @Test
    fun `the build row is the newest release`() {
        val row = EnglishChangelog.toBuildRowUiState()
        val newest = EnglishChangelog.releases.first()

        assertEquals(newest.version, row.version)
        assertEquals(newest.headline, English.resolve(row.headline))
    }

    @Test
    fun `the build row reads as one sentence`() {
        // The only place the version and what it did are said together, which is what a screen
        // reader gets instead of two lines.
        val row = EnglishChangelog.toBuildRowUiState()
        val newest = EnglishChangelog.releases.first()

        assertEquals(
            "Version ${newest.version.printed} — ${newest.headline}. What changed.",
            English.resolve(row.spoken),
        )
    }

    @Test
    fun `the language is chosen from the tag the platform hands back`() {
        // The same shape `translationsFor` has, and the same fallback: there is no locale that
        // resolves to nothing, so there is no missing-changelog state to design.
        assertEquals(ItalianChangelog, changelogFor("it"))
        assertEquals(ItalianChangelog, changelogFor("it-CH"))
        assertEquals(ItalianChangelog, changelogFor("it_IT"))
        assertEquals(EnglishChangelog, changelogFor("en"))
        assertEquals(EnglishChangelog, changelogFor("de-DE"))
        assertEquals(EnglishChangelog, changelogFor(""))
    }

    @Test
    fun `a release is written down with the version it names`() {
        // `release(…)` parses the string it is given, so a typo in a document is a failure at the
        // moment the document is read rather than a page that quietly sorts to the wrong end.
        val written = release("0.4.3", "2026-08-10", "x", "y")

        assertEquals(ReleaseVersion(0, 4, 3), written.version)
        assertEquals(2026, written.date.year)
        assertEquals(listOf("y"), written.notes)
    }

    @Test
    fun `a version a document cannot write is a failure rather than a page`() {
        // **Loudly, and at the moment the document is read.** A version that does not parse would
        // otherwise become a page that sorts to the wrong end of a run the whole sheet assumes is
        // ordered — and the rail, the gate and the build row all read that order.
        val thrown = assertFailsWith<IllegalArgumentException> {
            release("0.4", "2026-08-10", "x", "y")
        }

        assertTrue(thrown.message.orEmpty().contains("0.4"), "the failure does not name the version")
    }

    @Test
    fun `a date a document cannot write is a failure too`() {
        assertFailsWith<IllegalArgumentException> { release("0.4.3", "2026-13-40", "x", "y") }
    }
}
