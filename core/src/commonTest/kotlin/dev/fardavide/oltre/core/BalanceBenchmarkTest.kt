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

        val actualLines = actual.lines()
        val goldenLines = BalanceBenchmarkGolden.PAGE.lines()
        val moved = (0 until maxOf(actualLines.size, goldenLines.size))
            .filter { actualLines.getOrNull(it) != goldenLines.getOrNull(it) }

        fail(
            buildString {
                appendLine("The balance surface moved: ${moved.size} of ${goldenLines.size} lines differ.")
                appendLine()
                appendLine("This is not a bug. Read every line below, decide whether it is what you wanted,")
                appendLine("then paste the new page into BalanceBenchmarkGolden and write up the round.")
                appendLine()
                for (index in moved.take(MAX_REPORTED_LINES)) {
                    appendLine("  line ${index + 1}")
                    appendLine("    was  ${goldenLines.getOrNull(index) ?: "(absent)"}")
                    appendLine("    now  ${actualLines.getOrNull(index) ?: "(absent)"}")
                }
                if (moved.size > MAX_REPORTED_LINES) {
                    appendLine("  … and ${moved.size - MAX_REPORTED_LINES} more")
                }
                appendLine()
                appendLine("── the new page, to paste into BalanceBenchmarkGolden ──")
                appendLine(actual)
            },
        )
    }

    private companion object {

        // Enough to read a balance round in the failure itself; past that the pasteable page below
        // is the better thing to look at, and a thousand-line assertion message is unreadable in a
        // CI log on four targets at once.
        const val MAX_REPORTED_LINES: Int = 40
    }
}
