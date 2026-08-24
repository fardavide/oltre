package dev.fardavide.oltre.client.changelog.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The whole of "opens on game updated" — see `.claude/docs/changelog-sheet.md` §2 for the table
// these four cases are read off.
class ChangelogGateTest {

    @Test
    fun `a new build opens the sheet`() {
        assertTrue(
            shouldOpenChangelog(
                lastSeen = ReleaseVersion(0, 18, 0),
                current = ReleaseVersion(0, 19, 0),
                hasColony = true,
            ),
        )
    }

    @Test
    fun `a patch is a new build like any other`() {
        // Davide's call 2026-08-23 over the cheaper rule that only feature releases interrupt.
        assertTrue(
            shouldOpenChangelog(
                lastSeen = ReleaseVersion(0, 19, 0),
                current = ReleaseVersion(0, 19, 1),
                hasColony = true,
            ),
        )
    }

    @Test
    fun `the build that has already been read stays shut`() {
        assertFalse(
            shouldOpenChangelog(
                lastSeen = ReleaseVersion(0, 19, 0),
                current = ReleaseVersion(0, 19, 0),
                hasColony = true,
            ),
        )
    }

    @Test
    fun `a colony with nothing remembered is an upgrade and opens`() {
        // The case that exists exactly once: on the release that adds this feature every player
        // alive has no remembered version.
        assertTrue(
            shouldOpenChangelog(
                lastSeen = null,
                current = ReleaseVersion(0, 19, 0),
                hasColony = true,
            ),
        )
    }

    @Test
    fun `a first launch is not a changelog`() {
        // Same missing memory as the case above and the opposite answer: somebody who has never
        // played has nothing to be told changed.
        assertFalse(
            shouldOpenChangelog(
                lastSeen = null,
                current = ReleaseVersion(0, 19, 0),
                hasColony = false,
            ),
        )
    }

    @Test
    fun `a downgrade is a change and opens`() {
        // TestFlight hands out older builds and a phone restores an older backup. Rather than
        // deciding which direction counts as news the rule stays what it says it is.
        assertTrue(
            shouldOpenChangelog(
                lastSeen = ReleaseVersion(0, 19, 0),
                current = ReleaseVersion(0, 18, 0),
                hasColony = true,
            ),
        )
    }
}
