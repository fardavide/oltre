package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.JoinDecision
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

class InMemoryAllianceRosterTest {

    @Test
    fun `pending requests follow request time then private id across repeated reads`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val ids = listOf("founder", "zulu", "bravo", "alpha").map(::PlayerId).iterator()
        val players = InMemoryPlayerRepository(colonies, alliances, PlayerIds { ids.next() })
        val authenticator = HeaderAuthenticator(players)
        val accounts = listOf("founder", "zulu", "bravo", "alpha").map { subject ->
            assertIs<Caller.Known>(authenticator.identify(Credentials(null, subject))).player.also {
                colonies.found(it, freshColony())
            }
        }
        val founder = accounts.first()
        val alliance = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW)).alliance.alliance.id
        val requests = accounts.drop(1).mapIndexed { index, player ->
            val askedAt = TEST_NOW + (if (index == 0) 1 else 2).minutes
            val current = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, askedAt))
            assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(
                alliances.petition(player, alliance, askedAt, current.alliance.version),
            ).affiliation).petition.id
        }

        repeat(2) {
            assertEquals(listOf(requests[0], requests[2], requests[1]), assertIs<RosterRead.Present>(
                alliances.rosterOf(founder, TEST_NOW),
            ).pending?.map { it.id })
        }
    }

    @Test
    fun `roster order follows role then tenure then private id across repeated reads`() = runTest {
        val colonies = InMemoryColonyRepository()
        val alliances = InMemoryAllianceRepository(colonies)
        val ids = listOf("founder", "zulu", "bravo", "alpha", "admin").map(::PlayerId).iterator()
        val players = InMemoryPlayerRepository(colonies, alliances, PlayerIds { ids.next() })
        val authenticator = HeaderAuthenticator(players)
        val accounts = listOf("founder", "zulu", "bravo", "alpha", "admin").map { subject ->
            assertIs<Caller.Known>(authenticator.identify(Credentials(null, subject))).player.also {
                colonies.found(it, freshColony())
            }
        }
        val founder = accounts.first()
        val alliance = assertIs<Founded.Made>(alliances.found(founder, AllianceName("Fleet"), AllianceTag("FLT"), TEST_NOW)).alliance.alliance.id
        for ((index, player) in accounts.drop(1).withIndex()) {
            val joinedAt = TEST_NOW + (if (index == 0) 1 else if (index < 3) 2 else 3).minutes
            val current = assertIs<Affiliation.Enlisted>(alliances.allianceOf(founder, joinedAt))
            val petition = assertIs<Affiliation.Petitioning>(assertIs<AllianceChange.Applied>(
                alliances.petition(player, alliance, joinedAt, current.alliance.version),
            ).affiliation)
            assertIs<AllianceChange.Applied>(alliances.approve(
                founder, alliance, petition.petition.id, JoinDecision.ADMITTED, joinedAt, petition.alliance.version,
            ))
        }
        val admin = assertIs<Affiliation.Enlisted>(alliances.allianceOf(accounts.last(), TEST_NOW))
        assertIs<AllianceChange.Applied>(alliances.setRole(founder, alliance, admin.seat.id, AllianceRole.ADMIN, admin.alliance.version))
        val expected = listOf(accounts[0], accounts[4], accounts[1], accounts[3], accounts[2]).map { player ->
            assertIs<Affiliation.Enlisted>(alliances.allianceOf(player, TEST_NOW)).seat.id
        }

        repeat(2) {
            assertEquals(expected, assertIs<RosterRead.Present>(alliances.rosterOf(founder, TEST_NOW)).members.map { it.id })
        }
    }
}
