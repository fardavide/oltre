package dev.fardavide.oltre.client.changelog.ui

import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.design.text.TextRes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// The models the sheet renders, and the two properties the sheet actually relies on.
//
// **A page is keyed by its release, not by where it sits in the list.** The run grows at the head
// every few days, so an index means something different after every release — a tag built from one
// would name a different page each time, and an assertion written against it would keep passing
// while looking at the wrong release.
class ChangelogUiStateTest {

    @Test
    fun `a page is named by the release it is about`() {
        assertEquals("changelog-page-0.18.0", ChangelogTestTags.page(ReleaseVersion(0, 18, 0)))
        assertNotEquals(
            ChangelogTestTags.page(ReleaseVersion(0, 18, 0)),
            ChangelogTestTags.page(ReleaseVersion(0, 17, 1)),
        )
    }

    @Test
    fun `two pages about the same release with the same words are the same page`() {
        // **Compose skips on equality**, and every field here is either a data class or a `TextRes`
        // — which compares like the string it replaced. A page rebuilt from the same document on a
        // recomposition is therefore equal to the one already on screen, and the sixty-six skies
        // behind them are not redrawn. A field that broke this would be invisible until a phone got
        // warm.
        assertEquals(page(), page())
    }

    @Test
    fun `a page about a different release is a different page`() {
        assertNotEquals(page(), page(version = ReleaseVersion(0, 17, 1)))
    }

    @Test
    fun `a page that lost a note is a different page`() {
        assertNotEquals(page(), page(notes = emptyList()))
    }

    private fun page(
        version: ReleaseVersion = ReleaseVersion(0, 18, 0),
        notes: List<TextRes> = listOf(TextRes("a note")),
    ): ChangelogPageUiState = ChangelogPageUiState(
        version = version,
        date = TextRes("23 Aug 2026"),
        headline = TextRes("The gear opens something"),
        notes = notes,
    )
}
