package dev.fardavide.oltre.client.save.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// Turns the preferences file into a record and back, the way `GameStore` does for the colony —
// same shape, same division of labour, one `SaveFile` underneath and nothing impure above it.
class PreferencesStore(private val file: SaveFile) {

    // Every failure a player can hit answers `Preferences.NONE`: no file yet, bytes that cannot be
    // read, text that is not JSON, JSON without the field. They are distinguishable and it would
    // buy nothing to distinguish them — this is `GameStore.load`'s argument at a lower stake still.
    // A save that cannot be read costs a colony and is worth a decision; a preference that cannot
    // be read costs a first tap, and the only alternative on offer is crashing on launch over it.
    suspend fun load(): Preferences {
        val text = file.read() ?: return Preferences.NONE
        return try {
            json.decodeFromString(Preferences.serializer(), text)
        } catch (_: SerializationException) {
            Preferences.NONE
        }
    }

    // Best effort, exactly like `SaveFile.write` and for its reason: there is no surface to report
    // a failed write to, the next change writes the whole record again, and a full disk is not
    // worth ending a session over.
    suspend fun save(preferences: Preferences) {
        file.write(json.encodeToString(Preferences.serializer(), preferences))
    }

    private companion object {

        // `ignoreUnknownKeys` because this file has no schema version and so cannot migrate: a
        // build that remembers more than this one writes keys this one has never heard of, and the
        // choice is between reading the fields we do know and throwing the whole file away. The
        // save format takes the opposite line — `GameSave` leaves unknown keys fatal, because
        // silently misreading a colony is worse than admitting it is unreadable — and the
        // difference is the stake: dropping a key here forgets a view, not a week of play.
        // **`explicitNulls = false` is what makes a nullable field here mean "not chosen yet".**
        // Without it a nullable property is still a *required* one on the way in: a file written
        // before a field existed throws `MissingFieldException`, which is a `SerializationException`,
        // which `load` answers `NONE` to — so adding one field would have quietly cost every player
        // on disk their galaxy landing. Measured, 2026-08-23, by the test that says so.
        //
        // It is the read half that matters; the write half is a bonus that costs nothing — a null is
        // simply not written, and `ignoreUnknownKeys` above already covers the other direction.
        //
        // The save format takes the opposite line on every one of these, deliberately: `GameSave`
        // leaves unknown keys fatal and migrates by schema number, because silently misreading a
        // colony is worse than admitting it is unreadable. The difference is the stake — a
        // misread preference costs a first tap.
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
