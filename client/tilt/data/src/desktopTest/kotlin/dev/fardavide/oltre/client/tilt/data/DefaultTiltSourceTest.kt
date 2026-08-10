package dev.fardavide.oltre.client.tilt.data

import dev.fardavide.oltre.client.tilt.domain.Tilt
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// The only platform half of this module a test can reach, and the one worth reaching. The Android
// and iOS sources are `SensorManager` and `CoreMotion` with no seam a test can get at without
// Robolectric or an instrumented run — the same argument the notification schedulers and the shake
// detector make, and what replaces the test there is an install.
//
// This one is different because the answer it gives is a promise the rest of the repository leans
// on: every screenshot baseline is recorded on desktop, so if this ever reported anything but
// level, forty-one baselines would start drifting for a reason nobody would find on the diff.
class DefaultTiltSourceTest {

    @Test
    fun `desktop reports level and then nothing`() = runTest {
        val tilts = defaultTiltSource().tilts().toList()

        assertEquals(listOf(Tilt.NONE), tilts)
    }
}
