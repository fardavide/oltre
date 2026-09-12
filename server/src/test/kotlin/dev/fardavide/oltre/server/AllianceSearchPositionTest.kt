package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AllianceSearchPositionTest {

    @Test
    fun `experience sorts descending before names and ids break equal name ties`() {
        val first = AllianceSearchPosition(2, CanonicalAllianceName("z"), AllianceId("z"))
        val second = AllianceSearchPosition(1, CanonicalAllianceName("a"), AllianceId("a"))
        val third = second.copy(id = AllianceId("b"))
        assertTrue(first < second)
        assertTrue(second < third)
        assertTrue(third > second)
        assertEquals(0, second.compareTo(second))
    }
}
