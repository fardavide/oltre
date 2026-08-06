package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
}

// Pure text in, pure text out — the file itself is the caller's problem, which keeps core free
// of I/O.
object GameSave {

    // Bump whenever the on-disk shape changes, and migrate here. An unknown version is never
    // guessed at: silently misreading a colony is worse than admitting the save is unreadable.
    //
    // 2 — parallel builds: the single `buildQueue` slot became `builds`, one job per facility.
    // 1 — first shipped format.
    const val SCHEMA_VERSION: Int = 2

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
        val current = when (version) {
            SCHEMA_VERSION -> element
            1 -> element.jsonObject.migrateOneToTwo()
            else -> return DecodeResult.Failure(
                "unsupported save schema $version, this build reads $SCHEMA_VERSION",
            )
        }
        val snapshot = try {
            json.decodeFromJsonElement(GameSnapshot.serializer(), current)
        } catch (e: SerializationException) {
            return DecodeResult.Failure("malformed save: ${e.message}")
        } catch (e: IllegalArgumentException) {
            // Model invariants live in constructors (non-negative building levels, in-range
            // stocks), so a hand-edited file fails here instead of poisoning the simulation.
            return DecodeResult.Failure("invalid save: ${e.message}")
        }
        return DecodeResult.Success(snapshot)
    }

    // v1 held at most one build; v2 holds one per facility, keyed by the facility. A queued job
    // already names the building it was raising, so the key is in the data — nothing is invented
    // and nothing is dropped. Anything malformed is left alone for the decoder to report, so the
    // migration never turns a broken save into a plausible one.
    private fun JsonObject.migrateOneToTwo(): JsonElement {
        val state = this["state"] as? JsonObject ?: return this
        val queued = state["buildQueue"]
        val builds = when {
            queued == null || queued is JsonNull -> JsonObject(emptyMap())
            else -> {
                val building = (queued as? JsonObject)?.get("building") as? JsonPrimitive ?: return this
                JsonObject(mapOf(building.content to queued))
            }
        }
        return JsonObject(
            this + mapOf(
                "schemaVersion" to JsonPrimitive(SCHEMA_VERSION),
                "state" to JsonObject(state - "buildQueue" + ("builds" to builds)),
            ),
        )
    }

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
}
