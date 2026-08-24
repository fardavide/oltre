package dev.fardavide.oltre.client.changelog.ui

import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.design.text.TextRes

// What the sheet draws, and it decides nothing: which releases exist, in what order, and what each
// one says all arrive from `:client:changelog:presentation`.
data class ChangelogUiState(
    val title: TextRes,
    // *"65 releases"*, in the title row. The design's answer to a page counter: the one number worth
    // showing is how deep the run goes, and it is worth showing **before** the first swipe rather
    // than as a `3 of 65` that repeats what the page already says in 15sp bold.
    val depth: TextRes,
    // Newest first, which is also the pager's own order — page 0 is today. That is why the rail's
    // left end is the newest release and not the oldest: it is the order of the pages rather than a
    // timeline, and drawing it as a timeline would put 0.0.1 on the wrong side of it.
    val pages: List<ChangelogPageUiState>,
    // Which pages open a minor line — `patch == 0`, so the rail keeps no table of its own and a
    // release added tomorrow needs nothing here changed.
    val minorLineStops: List<Int>,
)

// One release. The card hugs it rather than filling the page, so a one-note release is a short card
// and never an empty one — the air a full-height sheet has to hold belongs to the sheet.
data class ChangelogPageUiState(
    // The version itself rather than a rendering of it, because two things on the page are drawn
    // from it: the line that prints `0.18.0` and the sky above it. Module rule 4 allows a ui model to
    // name its own feature's domain type, and this is the case it names.
    val version: ReleaseVersion,
    val date: TextRes,
    val headline: TextRes,
    // One to three, and never four. The cap is what keeps the tallest page inside the viewport at
    // 320dp, which is what keeps a page from scrolling — see `.claude/docs/changelog-sheet.md` §3.
    val notes: List<TextRes>,
)
