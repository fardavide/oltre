package dev.fardavide.oltre.client.debug.data

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultShakeDetectorTest {

    @Test
    fun `a desktop never shakes and its detector completes rather than hanging`() = runTest {
        // Two claims, and the second is the load-bearing one. A detector that never emits is the
        // desktop answer — the menu opens on a key chord there instead — but `App` collects this
        // flow for the lifetime of the composition, so it also has to *finish* rather than sit on a
        // suspended collector forever. `toList` returning at all is that assertion.
        assertEquals(emptyList(), defaultShakeDetector().shakes().toList())
    }
}
