package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

// A save is the whole simulation plus the instant it is accurate as of, and nothing else: every
// state between two events is recomputed exactly by advance(), so there is nothing else worth
// storing. The format lives in core rather than in the client because client and server must
// agree on it byte for byte once multiplayer lands.
@Serializable
data class GameSnapshot(
    val schemaVersion: Int = GameSave.SCHEMA_VERSION,
    val lastUpdatedAt: Instant,
    val state: GameState,
)

sealed interface DecodeResult {

    data class Success(val snapshot: GameSnapshot) : DecodeResult

    // Missing keys, corrupt text, a broken model invariant or a schema this build cannot read.
    // Core only states the reason; deciding what to do about it is the caller's business.
    data class Failure(val reason: String) : DecodeResult

    // A save this build deliberately refuses to carry forward. Distinct from Failure on purpose:
    // a corrupt save is an accident and an obsolete one is a decision, and a caller that wants
    // to tell the player which happened can only do so if the two are different answers.
    data class Obsolete(val schemaVersion: Int, val reason: String) : DecodeResult
}

// Pure text in, pure text out — the file itself is the caller's problem, which keeps core free
// of I/O.
object GameSave {

    // Bump whenever the on-disk shape changes, then either migrate the old shape here or
    // declare it obsolete. An unknown version is never guessed at: silently misreading a colony
    // is worse than admitting the save is unreadable.
    //
    // 2 — parallel builds: the single `buildQueue` slot became `builds`, one job per facility.
    // 1 — first shipped format. OBSOLETE, deliberately: see OBSOLETE_SCHEMAS.
    const val SCHEMA_VERSION: Int = 2

    // Versions this build refuses to carry forward, and why the player is told. A rebalance
    // this deep does not survive a shape-only migration: a colony grown at the old rates keeps
    // stocks the new curves would take weeks to earn, so migrating its shape would hand back a
    // colony that is no longer playable rather than preserving one. Davide's call, 2026-08-06.
    private val OBSOLETE_SCHEMAS: Map<Int, String> = mapOf(
        1 to "saved before the 0.0.8 rebalance, when the economy ran at 60x these rates",
    )

    private val json = Json {
        // schemaVersion carries a default, and a save that does not spell out its own version
        // is a save no future build can migrate.
        encodeDefaults = true
    }

    fun encode(snapshot: GameSnapshot): String = json.encodeToString(snapshot)

    fun decode(text: String): DecodeResult {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return DecodeResult.Failure("malformed save: ${e.message}")
        }
        val version = (element as? JsonObject)?.intOrNull("schemaVersion")
            ?: return DecodeResult.Failure("save carries no schema version")
        OBSOLETE_SCHEMAS[version]?.let { reason ->
            return DecodeResult.Obsolete(schemaVersion = version, reason = reason)
        }
        if (version != SCHEMA_VERSION) {
            return DecodeResult.Failure(
                "unsupported save schema $version, this build reads $SCHEMA_VERSION",
            )
        }
        val snapshot = try {
            json.decodeFromJsonElement(GameSnapshot.serializer(), element)
        } catch (e: SerializationException) {
            return DecodeResult.Failure("malformed save: ${e.message}")
        } catch (e: IllegalArgumentException) {
            // Model invariants live in constructors (non-negative building levels, in-range
            // stocks), so a hand-edited file fails here instead of poisoning the simulation.
            return DecodeResult.Failure("invalid save: ${e.message}")
        }
        return DecodeResult.Success(snapshot)
    }

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
}
