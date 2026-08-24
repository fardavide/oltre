package dev.fardavide.oltre.client.changelog.ui

import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.design.text.TextRes

// A run with the shape the real one has and none of its length: the newest release, a patch under
// it, a minor line below that, and the first week at the far end. Enough for the rail to have ticks
// and gaps, and short enough that a frame of it is a frame of a page rather than of a scroll.
//
// Written here rather than taken from `:client:changelog:presentation`, and that is the module rule
// rather than a preference — a `ui` module cannot see a `presentation` one. It is also the better
// test: these frames are about the drawing, and they should not move because somebody rewrote a
// headline.
internal fun testChangelogUiState(pages: List<ChangelogPageUiState> = testPages()): ChangelogUiState =
    ChangelogUiState(
        title = TextRes("Changelog"),
        depth = TextRes("${pages.size} releases"),
        pages = pages,
        minorLineStops = pages.indices.filter { pages[it].version.patch == 0 },
    )

internal fun testPages(): List<ChangelogPageUiState> = listOf(
    testPage(
        version = "0.18.0",
        date = "23 Aug 2026",
        headline = "The gear opens something",
        notes = listOf(
            "Alerts can be asked for by category instead of row by row.",
            "Delivery says how many notifications the answers arrive in.",
        ),
    ),
    testPage(
        version = "0.17.1",
        date = "23 Aug 2026",
        headline = "Your name gets the whole line",
        notes = listOf("The experience gauge became the strip's own bottom edge."),
    ),
    testPage(
        version = "0.17.0",
        date = "23 Aug 2026",
        headline = "The gauge fills",
        notes = listOf(
            "Everything you finish now pays experience.",
            "Opening this build credits everything you did before the level existed.",
            "A day in is about level 3, a week 10, a month 25.",
        ),
    ),
    testPage(
        version = "0.0.3",
        date = "5 Aug 2026",
        headline = "The economy is real",
        notes = listOf("Six buildings, a build queue, and energy that mines are throttled by."),
    ),
)

internal fun testBuildRowUiState(): BuildRowUiState = BuildRowUiState(
    label = TextRes("BUILD"),
    version = testPages().first().version,
    headline = TextRes("The gear opens something"),
    spoken = TextRes("Version 0.18.0 — The gear opens something. What changed."),
)

internal fun testPage(
    version: String,
    date: String,
    headline: String,
    notes: List<String>,
): ChangelogPageUiState = ChangelogPageUiState(
    version = requireNotNull(ReleaseVersion.parse(version)),
    date = TextRes(date),
    headline = TextRes(headline),
    notes = notes.map { TextRes(it) },
)
