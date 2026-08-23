package dev.fardavide.oltre.client.changelog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseVersionTest {

    @Test
    fun `a version reads back as the three numbers it was written with`() {
        val version = ReleaseVersion(major = 0, minor = 18, patch = 0)

        assertEquals("0.18.0", version.printed)
    }

    @Test
    fun `parsing recovers the version that was printed`() {
        assertEquals(ReleaseVersion(major = 0, minor = 4, patch = 3), ReleaseVersion.parse("0.4.3"))
    }

    @Test
    fun `a version with fewer than three parts is not a version`() {
        assertNull(ReleaseVersion.parse("0.18"))
    }

    @Test
    fun `a version with more than three parts is not a version`() {
        assertNull(ReleaseVersion.parse("0.18.0.1"))
    }

    @Test
    fun `a part that is not a number is not a version`() {
        assertNull(ReleaseVersion.parse("0.18.x"))
    }

    @Test
    fun `a negative part is not a version`() {
        // Not pedantry: the minus is what `split` leaves behind on a malformed string rather than
        // something anybody types.
        assertNull(ReleaseVersion.parse("0.-1.0"))
    }

    @Test
    fun `nothing at all is not a version`() {
        // What a preferences file written by a build that had never heard of this feature hands back.
        assertNull(ReleaseVersion.parse(""))
    }

    @Test
    fun `a later minor beats an earlier one`() {
        assertTrue(ReleaseVersion(0, 18, 0) > ReleaseVersion(0, 17, 1))
    }

    @Test
    fun `a later patch beats an earlier one`() {
        assertTrue(ReleaseVersion(0, 17, 1) > ReleaseVersion(0, 17, 0))
    }

    @Test
    fun `a later major beats everything below it`() {
        assertTrue(ReleaseVersion(1, 0, 0) > ReleaseVersion(0, 99, 99))
    }

    @Test
    fun `ten sorts above nine rather than beside one`() {
        // The reason this is three integers and not a string: `"0.9.0" < "0.10.0"` is false as text
        // and true as a release. Sixty-five pages sorted the other way would put the whole 0.1x era
        // in the middle of the list.
        assertTrue(ReleaseVersion(0, 10, 0) > ReleaseVersion(0, 9, 0))
    }
}
