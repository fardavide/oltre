package dev.fardavide.oltre.client

import kotlin.test.Test
import kotlin.test.assertEquals

// The tags themselves are never asserted from a behaviour test — a test that taps
// `ShellTestTags.tab(OltreTab.SHIPS)` and one that reads `"tab-ships"` off a screenshot would both
// pass if the derivation broke the same way — so this is the one place the string each function
// actually produces is pinned.
class ShellTestTagsTest {

    @Test
    fun `a tab's tag is its lowercased enum name`() {
        assertEquals("tab-colony", ShellTestTags.tab(OltreTab.COLONY))
        assertEquals("tab-ships", ShellTestTags.tab(OltreTab.SHIPS))
        assertEquals("tab-alliance", ShellTestTags.tab(OltreTab.ALLIANCE))
    }

    @Test
    fun `a resource cell's tag is the lowercased resource name`() {
        assertEquals("resource-cell-metal", ShellTestTags.resourceCell("Metal"))
    }

    @Test
    fun `a resource rate's tag is the lowercased resource name`() {
        assertEquals("resource-rate-deuterium", ShellTestTags.resourceRate("Deuterium"))
    }

    @Test
    fun `a Ships mode's tag is its lowercased enum name`() {
        assertEquals("ships-mode-shipyard", ShellTestTags.shipsMode(ShipsMode.SHIPYARD))
        assertEquals("ships-mode-fleets", ShellTestTags.shipsMode(ShipsMode.FLEETS))
    }
}
