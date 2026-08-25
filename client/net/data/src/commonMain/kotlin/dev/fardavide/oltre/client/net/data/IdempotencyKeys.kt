package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.IdempotencyKey
import kotlin.random.Random

// **Where a key comes from.** `core` reads no clock and no random source, so the thing that mints
// one is whatever already holds both — the same reason `resume` mints the galaxy seed at the
// composition root rather than in the model.
//
// An interface rather than a function call inside the outbox so that a test can say which key was
// minted. That is not decoration: the property this whole mechanism exists for is that **a retry
// carries the key its first attempt carried**, and a test cannot assert that against a value it
// cannot predict.
fun interface IdempotencyKeys {

    fun mint(): IdempotencyKey
}

// 128 bits from the platform's random source, as hexadecimal.
//
// **Not a counter**, which is the obvious alternative and is wrong twice over: a counter restarts
// with the process unless it is persisted alongside the queue, and two installs of the same app
// would mint the same first key. **Not `Uuid`**, only because `kotlin.uuid` is still behind an
// opt-in and a wire value is a poor place to spend an experimental API.
//
// What the far end needs is uniqueness per player and nothing else — `applied_verbs` is keyed on
// `(idempotency_key, player_id)` — so 128 random bits is far more than the job asks for and costs
// nothing.
fun randomIdempotencyKeys(random: Random): IdempotencyKeys = IdempotencyKeys {
    fun half(): String = random.nextLong().toULong().toString(radix = 16).padStart(length = 16, padChar = '0')
    IdempotencyKey(half() + half())
}
