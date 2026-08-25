package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.protocol.IdempotencyKey
import kotlin.time.Instant

// Handwritten, per the repository's no-mocking-framework rule, and shaped exactly like
// `FakeSaveFile` one feature over — because the interface it doubles is shaped exactly like
// `SaveFile`, and for a reason `OutboxFile` states.
//
// **The one thing it is for is process death.** Two `Outbox` instances over the same file are what
// an app killed and reopened looks like from in here, and the counters are what say whether the
// queue was actually written or only remembered.
internal class FakeOutboxFile(initial: String? = null) : OutboxFile {

    var content: String? = initial
        private set

    var writeCount: Int = 0
        private set

    var clearCount: Int = 0
        private set

    override suspend fun read(): String? = content

    override suspend fun write(text: String) {
        content = text
        writeCount++
    }

    override suspend fun clear() {
        content = null
        clearCount++
    }
}

// Keys a test can name. The real mint is 128 random bits, which is right and untestable: the
// property that matters is that **a retry carries the key its first attempt carried**, and no
// assertion can say that about a value it cannot predict.
internal class FakeIdempotencyKeys(private val prefix: String) : IdempotencyKeys {

    private var minted = 0

    fun mintCount(): Int = minted

    override fun mint(): IdempotencyKey {
        minted++
        return IdempotencyKey("$prefix-$minted")
    }
}

// A colony to hand back. Nothing here reads it — this module never runs the game — so what matters
// about it is only that two of them can be told apart.
internal fun fakeColony(at: Instant, seed: Long = 20_260_825): GameSnapshot =
    GameSnapshot(lastUpdatedAt = at, state = GameState.initial(GalaxySeed(seed)))
