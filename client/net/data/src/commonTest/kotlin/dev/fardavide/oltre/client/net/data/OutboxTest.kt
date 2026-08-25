package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.VerbEnvelope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW: Instant = Instant.parse("2026-08-25T09:00:00Z")

private fun envelope(verb: ClientVerb, key: String, at: Instant = NOW): VerbEnvelope = VerbEnvelope(
    verb = verb,
    clientInstant = at,
    idempotencyKey = IdempotencyKey(key),
)

class OutboxTest {

    @Test
    fun `a player who has queued nothing has nothing outstanding`() = runTest {
        assertEquals(emptyList(), Outbox(FakeOutboxFile()).queued())
    }

    // **The whole reason this is a file and not a list in memory.** Two instances over the same
    // file are what an app killed and reopened looks like from in here — and *"a queued verb that
    // evaporates when the app is killed is worse than one that was refused"*.
    @Test
    fun `a queued verb survives the app being killed`() = runTest {
        // given
        val file = FakeOutboxFile()
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), "tap")

        // when — one outbox queues it and the process it lived in goes away
        Outbox(file).queue(queued)

        // then — a new one over the same file finds it
        assertEquals(listOf(queued), Outbox(file).queued())
    }

    // Order is load-bearing: a purchase and the dispatch it pays for are only replayable in the
    // sequence they happened, which is why `Replay.kt` walks the list rather than the set.
    @Test
    fun `verbs come back in the order they were tapped`() = runTest {
        // given
        val outbox = Outbox(FakeOutboxFile())
        val first = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), "first")
        val second = envelope(ClientVerb.StartResearch(Technology.PHOTOVOLTAICS), "second", NOW + 3.minutes)

        // when
        outbox.queue(first)
        outbox.queue(second)

        // then
        assertEquals(listOf(first, second), outbox.queued())
    }

    // `#106` §3's second row, and the one line of policy in this class. **Two representative verbs
    // rather than a table**, deliberately: the enumeration lives in `ClientVerb.offlineRule` as a
    // `when` with no `else`, so a thirteenth verb cannot compile without somebody deciding this,
    // and a list copied here would be a second place for one to go missing.
    @Test
    fun `a dispatch cannot be queued because the world it aims at may be somebody else's by then`() = runTest {
        // given
        val file = FakeOutboxFile()

        // when
        val result = Outbox(file).queue(envelope(ClientVerb.StartSurvey(SystemAddress(2, 118)), "survey"))

        // then — refused, and nothing was written
        assertEquals(QueueResult.NOT_QUEUEABLE, result)
        assertEquals(emptyList(), Outbox(file).queued())
        assertEquals(0, file.writeCount)
    }

    @Test
    fun `an upgrade can be queued because only this colony decides it`() = runTest {
        assertEquals(
            QueueResult.QUEUED,
            Outbox(FakeOutboxFile()).queue(envelope(ClientVerb.StartUpgrade(BuildingType.SOLAR_PLANT), "solar")),
        )
    }

    @Test
    fun `a verb the server judged leaves the queue`() = runTest {
        // given
        val outbox = Outbox(FakeOutboxFile())
        val stays = envelope(ClientVerb.StartResearch(Technology.PHOTOVOLTAICS), "stays")
        outbox.queue(envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), "goes"))
        outbox.queue(stays)

        // when
        outbox.answered(setOf(IdempotencyKey("goes")))

        // then
        assertEquals(listOf(stays), outbox.queued())
    }

    // A partial answer is safe because the rule is subtraction rather than replacement: what the
    // server did not mention is still outstanding, whatever else came back.
    @Test
    fun `a verb the server did not mention is still outstanding`() = runTest {
        // given
        val outbox = Outbox(FakeOutboxFile())
        val queued = envelope(ClientVerb.StartUpgrade(BuildingType.METAL_MINE), "mine")
        outbox.queue(queued)

        // when
        outbox.answered(setOf(IdempotencyKey("somebody else's key")))

        // then
        assertEquals(listOf(queued), outbox.queued())
    }

    // A drained queue and a queue that never existed are the same thing, so there is one way to say
    // it. The next `read` answers null exactly as a first launch does.
    @Test
    fun `an emptied outbox leaves nothing behind`() = runTest {
        // given
        val file = FakeOutboxFile()
        val outbox = Outbox(file)
        outbox.queue(envelope(ClientVerb.ToggleFlightAlerts, "bell"))

        // when
        outbox.answered(setOf(IdempotencyKey("bell")))

        // then
        assertNull(file.content)
        assertEquals(1, file.clearCount)
    }

    // The honest position rather than a comfortable one: this loses taps the player made, and there
    // is no way to recover them — a half-parsed queue is a queue whose order is unknown. What it
    // buys is that the app opens.
    @Test
    fun `an outbox that cannot be read is an empty one rather than a crash on launch`() = runTest {
        assertEquals(emptyList(), Outbox(FakeOutboxFile("{ this is not a queue")).queued())
    }

    @Test
    fun `an outbox written by a build that reshaped the envelope reads as empty`() = runTest {
        // `Protocol.json` has no `ignoreUnknownKeys`, deliberately — an unknown key is a
        // disagreement about the contract, and a queue misread is worse than a queue admitted lost.
        assertEquals(emptyList(), Outbox(FakeOutboxFile("""[{"verb":{"type":"Teleport"}}]""")).queued())
    }
}
