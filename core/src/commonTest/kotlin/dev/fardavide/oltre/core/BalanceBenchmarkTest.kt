package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.fail

// **The gate that makes a balance change visible.**
//
// `BalanceBenchmark` renders the whole balance surface as a page of player-visible readings; this
// asserts that page has not moved. It is one assertion and it is not a band — it is an equality,
// deliberately, and that is the opposite of what `OpeningBalanceTest` and `CheckInBalanceTest` do.
//
// The three are answers to three different questions and none of them replaces another:
//
// | | asks | fails when |
// |---|---|---|
// | `OpeningBalanceTest` | is the opening still playable | the shape breaks |
// | `CheckInBalanceTest` | is a five-minute session still worth opening | the shape breaks |
// | this | **what exactly did that change do** | anything at all moves |
//
// A band is the right instrument for a guardrail and the wrong one for a review: the readings a
// band lets through are exactly the ones a designer wants to see before agreeing to them. 0.5.1 is
// the case — it moved a gate by two levels and re-homed every colony, every test in the repository
// passed, and the review had prose in front of it rather than numbers. The same change against this
// file is thirty moved lines in the pull request.
//
// ── When this fails ──────────────────────────────────────────────────────────────────────────
//
// **It is not a bug and it is usually not a mistake.** A failure here means a balance number moved,
// which is what balance work is. The workflow is:
//
// 1. Read the diff the failure prints. Every line is a player-visible reading, so it can be read
//    as *"the adaptation branch now opens at hour 30 instead of hour 12"* rather than as a constant.
// 2. Decide whether that is what was wanted. This is the review, and it is the whole point.
// 3. If it is, paste the new page into `BalanceBenchmarkGolden` and say what it bought in a
//    `balance-log.md` round.
//
// What must **not** happen is step 3 without step 2. A golden pasted without being read is a golden
// that records a regression instead of catching one — which is exactly the failure mode the bands
// next door exist to backstop, and why both instruments are here rather than either alone.
class BalanceBenchmarkTest {

    @Test
    fun `the balance surface is the one that was signed off`() {
        val actual = BalanceBenchmark.render()
        if (actual == BalanceBenchmarkGolden.PAGE) return

        // **Compared as rows, not by position.** The first cut walked the two pages by index, which
        // is right only while every change is a value moving inside a row that stayed put. Add or
        // remove one row and everything below it shifts, so a two-line deletion reported *105 of 160
        // lines differ* — true, useless, and precisely the moment a reviewer needs the diff to be
        // readable.
        val before = rowsOf(BalanceBenchmarkGolden.PAGE)
        val after = rowsOf(actual)
        val keys = (before.keys + after.keys).filter { before[it] != after[it] }

        fail(
            buildString {
                appendLine("The balance surface moved: ${keys.size} rows differ.")
                appendLine()
                appendLine("This is not a bug. Read every line below, decide whether it is what you wanted,")
                appendLine("then paste the new page into BalanceBenchmarkGolden and write up the round.")
                appendLine()
                for (key in keys.take(MAX_REPORTED_LINES)) {
                    val was = before[key]
                    val now = after[key]
                    when {
                        was != null && now != null -> appendLine("  $key: $was -> $now")
                        now == null -> appendLine("  $key: $was  (row gone)")
                        else -> appendLine("  $key: $now  (row is new)")
                    }
                }
                if (keys.size > MAX_REPORTED_LINES) {
                    appendLine("  … and ${keys.size - MAX_REPORTED_LINES} more")
                }
                appendLine()
                appendLine("── the new page, to paste into BalanceBenchmarkGolden ──")
                appendLine(actual)
            },
        )
    }

    // The page as `section ▸ label` to value. **Both halves of that key are load-bearing, and each
    // was learned by the diff silently dropping rows** — which is the worst thing a diff can do,
    // because a short one looks like a small change rather than a broken instrument.
    //
    // *Label*, split after the row's own indent. Splitting on the first run of two spaces anywhere
    // gives every indented row the empty label, and keying by that collapsed all of them into one
    // entry: 11 of 48 changed rows shown.
    //
    // *Section*, because labels repeat across sections. `day 7` and `day 14` are rows in both
    // `[progression]` and `[horizon]` with different columns, so a label-only key kept one and threw
    // the other away.
    //
    // Hand-rolled rather than a lookbehind regex: `core` compiles for Kotlin/Native as well as the
    // JVM and this test runs on both, so it uses only what both are certain to agree on.
    private fun rowsOf(page: String): Map<String, String> {
        val rows = mutableMapOf<String, String>()
        var section = ""
        for (line in page.lines()) {
            if (line.isBlank() || line.startsWith("#")) continue
            if (line.startsWith("[")) {
                section = line.substringBefore(']').removePrefix("[")
                continue
            }
            val start = line.indexOfFirst { !it.isWhitespace() }
            val gap = line.indexOf("  ", start)
            val label = if (gap < 0) line.trim() else line.substring(0, gap).trim()
            val value = if (gap < 0) "" else line.substring(gap).trim()
            rows["$section ▸ $label"] = value
        }
        return rows
    }

    private companion object {

        // Enough that a whole-page change still shows nearly every pair. **40 was too few and the
        // rows it dropped were the worst ones to drop**: the page is ordered by horizon, so the
        // truncated tail was `[horizon]` — the late-game readings a re-scaling round exists to move.
        // The page itself is around 160 rows and follows in the same message, so the only cost of a
        // higher cap is a longer CI log.
        const val MAX_REPORTED_LINES: Int = 120
    }
}
