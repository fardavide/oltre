package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

// A save is the whole simulation plus the instant it is accurate as of, and nothing else: every
// state between two events is recomputed exactly by advance(), so there is nothing else worth
// storing. The format lives in core rather than in the client because client and server must
// agree on it byte for byte once multiplayer lands.
@Serializable
data class GameSnapshot(
    val schemaVersion: Int = GameSave.SCHEMA_VERSION,
    val lastUpdatedAt: Instant,
    // Whether the debug menu has ever acted on this colony. Envelope rather than `GameState`,
    // because it is a fact about the save and not a rule of the simulation: `advance` never reads
    // it, nothing branches on it, and a colony that carries it plays exactly like one that does
    // not. It is in `core` at all for the reason the format is — client and server have to agree
    // on it byte for byte, and the day multiplayer lands this is the flag that says a colony's
    // clock was moved by hand.
    //
    // One-way, deliberately. Nothing clears it: a reset does not clear it either, because the
    // reset itself is a debug action and the new colony is one the menu created.
    val debugUsed: Boolean = false,
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
    // 8 — the fleet: an idle `ships` pool and the `runs` in flight, replacing `returningFleet`. The
    //     first hop that *removes* a key as well as adding two, and the first that has to rewrite
    //     entries already in the event log.
    // 7 — `debugUsed`, on the envelope rather than in the state. The first hop that adds nothing
    //     the simulation reads: it records that the debug menu touched this colony, so a save
    //     whose clock was moved by hand can be told apart from one that was played.
    // 6 — probes: `surveys`, the jobs in flight. What they write to — `galaxy.surveyed` — has
    //     existed since 4, so the hop adds the verb and not the record it fills.
    // 5 — the adaptation branch: three more levels on `research`, and `activeAdaptation` — the same
    //     empire-wide slot `activeResearch` uses, held by the other branch.
    // 4 — the galaxy: a seed, the home coordinate, the surveyed set and who holds what. Never the
    //     worlds themselves — they are regenerated from the seed, see `GalaxyGeneration.kt`.
    // 3 — the research branch: `research` levels and the single `activeResearch` slot.
    // 2 — parallel builds: the single `buildQueue` slot became `builds`, one job per facility.
    // 1 — first shipped format. OBSOLETE, deliberately: see OBSOLETE_SCHEMAS.
    const val SCHEMA_VERSION: Int = 8

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

    // One step per version, keyed by the version being migrated *from*, so a save several
    // versions behind is carried forward one hop at a time rather than by a special case per
    // starting point. Migrating is the default for a change that is only shape — retiring is for
    // a change that would hand back a colony no longer worth playing (see OBSOLETE_SCHEMAS).
    private val MIGRATIONS: Map<Int, (JsonObject) -> JsonObject> = mapOf(
        // 2 -> 3: research is purely additive. A colony saved before the branch existed has
        // researched nothing and has nothing running, which is exactly what a fresh `Research`
        // says — so there is no number to invent and nothing to rescale. Davide's call,
        // 2026-08-06: carry it forward rather than reset it.
        2 to { root ->
            root.withState(
                "research" to json.encodeToJsonElement(Research.serializer(), Research.initial()),
                "activeResearch" to JsonNull,
            )
        },
        // 3 -> 4: the galaxy is purely additive in the same sense research was — a colony saved
        // before the map existed has surveyed nothing and holds nothing but its own home world,
        // which is exactly what a fresh `GalaxyState` says. So this migrates rather than retires,
        // the default the persistence entry sets for a change that is only shape.
        3 to { root ->
            root.withState(
                "galaxy" to json.encodeToJsonElement(
                    GalaxyState.serializer(),
                    GalaxyState.initial(seedFor(root)),
                ),
            )
        },
        // 4 -> 5: the adaptation branch, additive in exactly the sense the two hops above were —
        // an empire saved before the ladders existed has climbed none of them and has nothing
        // running, which is what three zeroes and an empty slot say. Third time the answer is
        // migrate rather than retire, and for the third time because the change is only shape.
        4 to { root ->
            // The three ladders are *added to* the research record rather than replacing it: unlike
            // the 2 -> 3 hop, `research` already exists here and carries three applied levels the
            // player earned. Encoding a fresh `Research` would reset them.
            val existing: Map<String, JsonElement> =
                (root["state"] as? JsonObject)?.get("research") as? JsonObject ?: emptyMap()
            root.withState(
                "research" to JsonObject(existing + ADAPTATION_AT_ZERO.filterKeys { it !in existing }),
                "activeAdaptation" to JsonNull,
            )
        },
        // 5 -> 6: probes. An empire saved before the verb existed has none in flight, which is what
        // an empty list says — and the worlds it had already surveyed are on `galaxy.surveyed`
        // untouched, because a survey only ever *adds* to that set. Fourth hop, fourth time the
        // answer is migrate rather than retire, and the shallowest of the four: one absent key.
        5 to { root -> root.withState("surveys" to JsonArray(emptyList())) },
        // 6 -> 7: `debugUsed`, and the only hop in the table that is the identity function. The key
        // is on the envelope rather than in the state, and it carries a default — so a save written
        // before the flag existed decodes with `false`, which is the truth about it: nothing could
        // have debugged a colony saved by a build that had no debug menu. The entry is here rather
        // than absent because `migratedToCurrent` reads a missing step as "this build cannot get
        // there" and refuses the save; a hop that has nothing to do still has to say so.
        6 to { root -> root },
        // 7 -> 8: the fleet. Two keys added, one removed, and one rewrite inside the event log —
        // the deepest hop in the table, and the first that is not purely additive.
        //
        // **The granted skiff is a deliberate gift, and it settles a call the sheet left in two
        // places.** Every other hop writes the truthful zero for a thing that did not exist, and by
        // that standard this should write an empty pool. It does not, because in this slice there is
        // no way to *buy* a hull — `buildShips` is slice 3 — so a zero pool would hand an existing
        // colony a verb it cannot use and a Galaxy tab that still does nothing. One skiff is what a
        // colony founded a moment later gets, so this is the migration handing back the colony the
        // player would have had rather than inventing an asset.
        //
        // **`returningFleet` is dropped and its cargo credited, not folded into a run.** The
        // temptation is to turn it into a `FleetRun`, and it is wrong: the old `origin` is an
        // unbounded `Coordinates` carrying a *position*, the new `target` is a bounded
        // `GalaxyCoordinate` carrying a *slot*, and a fold would have to invent both a target the
        // save never recorded and a `gathering` nobody chose. Crediting the cargo is the truthful
        // answer, and it is total — there is no shape it can refuse. It also cannot happen in the
        // wild: **nothing in the repository has ever constructed a `ReturningFleet` outside test
        // code**, so the only saves this branch will ever run on are `GameSaveTest`'s own frozen
        // fixtures. Kept, and kept correct, for the reason the 6 -> 6 identity hop is kept.
        7 to { root ->
            val state = root["state"] as? JsonObject
            val fleet = state?.get("returningFleet") as? JsonObject
            root.withState(
                "ships" to buildJsonObject {
                    put("counts", buildJsonObject { put(ShipType.SKIFF.name, JsonPrimitive(1)) })
                },
                "runs" to JsonArray(emptyList()),
                "resources" to creditedWith(state?.get("resources") as? JsonObject, fleet?.get("cargo") as? JsonObject),
                "eventLog" to rewrittenLog(state?.get("eventLog") as? JsonArray),
            ).withoutState("returningFleet")
        },
    )

    // The three fine-unit fields of `Resources`, added term by term. A migration may not construct a
    // `Resources` and read its internals back out, so it does the arithmetic on the wire shape.
    private val FINE_FIELDS = listOf("metalFine", "crystalFine", "deuteriumFine")

    private fun creditedWith(resources: JsonObject?, cargo: JsonObject?): JsonElement {
        if (resources == null) return resources ?: JsonNull
        if (cargo == null) return resources
        val credited = FINE_FIELDS.associateWith { field ->
            val have = (resources[field] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            val landing = (cargo[field] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            JsonPrimitive(have + landing)
        }
        return JsonObject(resources + credited)
    }

    // `FleetReturned` is the one member whose payload changed shape rather than merely gaining a
    // sibling: `ships` went from a bare `Map<ShipType, Int>` to a `Ships`, which nests under
    // `counts`; `CARGO` became `SKIFF`; and `from` is a new non-defaulted nullable, so the key has to
    // be present even when the answer is "we do not know".
    //
    // **The discriminator is `"FleetReturned"`, PascalCase**, like every other member — a rewrite
    // written against a snake_case name would match nothing, silently skip the rename, and the save
    // would then fail to decode on an unknown enum constant, because the `Json` above does not set
    // `ignoreUnknownKeys`.
    private fun rewrittenLog(log: JsonArray?): JsonElement {
        if (log == null) return JsonArray(emptyList())
        return JsonArray(
            log.map { entry ->
                val event = entry as? JsonObject ?: return@map entry
                if ((event["type"] as? JsonPrimitive)?.content != "FleetReturned") return@map event
                val old = event["ships"] as? JsonObject ?: JsonObject(emptyMap())
                val renamed = old.mapKeys { (key, _) -> if (key == "CARGO") ShipType.SKIFF.name else key }
                JsonObject(
                    event +
                        ("ships" to buildJsonObject { put("counts", JsonObject(renamed)) }) +
                        ("from" to JsonNull),
                )
            },
        )
    }

    private val ADAPTATION_AT_ZERO: Map<String, JsonElement> = mapOf(
        "thermal" to JsonPrimitive(0),
        "gravitic" to JsonPrimitive(0),
        "atmospheric" to JsonPrimitive(0),
    )

    // The migration mints a galaxy, and a migration is a *pure function* — so the seed cannot come
    // from a clock or a random source. It is derived from the save's own `lastUpdatedAt` instead,
    // which is what makes decoding the same file twice hand back the same map. That is not a nicety:
    // a seed drawn at random would give the player a different galaxy every time the app reopened,
    // until the first commit happened to write one down.
    private fun seedFor(root: JsonObject): GalaxySeed {
        val stamp = (root["lastUpdatedAt"] as? JsonPrimitive)?.content.orEmpty()
        var hash = 0L
        for (character in stamp) hash = hash * 31 + character.code
        return GalaxySeed(hash)
    }

    fun encode(snapshot: GameSnapshot): String = json.encodeToString(snapshot)

    fun decode(text: String): DecodeResult {
        val parsed = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return DecodeResult.Failure("malformed save: ${e.message}")
        }
        val root = parsed as? JsonObject
        val version = root?.intOrNull("schemaVersion")
            ?: return DecodeResult.Failure("save carries no schema version")
        OBSOLETE_SCHEMAS[version]?.let { reason ->
            return DecodeResult.Obsolete(schemaVersion = version, reason = reason)
        }
        val element = migratedToCurrent(root, from = version)
            ?: return DecodeResult.Failure(
                "unsupported save schema $version, this build reads $SCHEMA_VERSION",
            )
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

    // Null when this build cannot get there: an unknown version, one from the future, or an
    // older one whose step is missing. Guessing is what the version check exists to prevent.
    private fun migratedToCurrent(root: JsonObject, from: Int): JsonObject? {
        if (from > SCHEMA_VERSION) return null
        var element = root
        var version = from
        while (version < SCHEMA_VERSION) {
            element = MIGRATIONS[version]?.invoke(element) ?: return null
            version += 1
            element = JsonObject(element + ("schemaVersion" to JsonPrimitive(version)))
        }
        return element
    }

    // A migration only ever adds to or rewrites the `state` object; the envelope's own version is
    // stamped by the loop above, so a step cannot forget to bump it.
    private fun JsonObject.withState(vararg entries: Pair<String, JsonElement>): JsonObject {
        val state = this["state"] as? JsonObject ?: return this
        return JsonObject(this + ("state" to JsonObject(state + entries)))
    }

    // `withState` only ever *adds*, because until schema 8 no hop ever needed to take a key away.
    // One does now, and without this the leftover `returningFleet` would make every legacy save
    // decode as `Failure("malformed save")` — the `Json` above sets only `encodeDefaults`, so
    // `ignoreUnknownKeys` is false and an unknown key is fatal rather than ignored.
    private fun JsonObject.withoutState(vararg keys: String): JsonObject {
        val state = this["state"] as? JsonObject ?: return this
        return JsonObject(this + ("state" to JsonObject(state - keys.toSet())))
    }

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
}
