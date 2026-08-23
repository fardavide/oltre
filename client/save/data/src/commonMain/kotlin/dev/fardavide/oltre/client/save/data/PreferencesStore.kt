package dev.fardavide.oltre.client.save.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
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
            json.decodeFromString(Record.serializer(), text).toPreferences()
        } catch (_: SerializationException) {
            Preferences.NONE
        }
    }

    // Best effort, exactly like `SaveFile.write` and for its reason: there is no surface to report
    // a failed write to, the next change writes the whole record again, and a full disk is not
    // worth ending a session over.
    suspend fun save(preferences: Preferences) {
        file.write(json.encodeToString(Record.serializer(), preferences.toRecord()))
    }

    // **The file's shape, which is not the record's.** Every field defaults to absent, so a file
    // written by a build that knew fewer preferences still reads — the *forward* half of what
    // `ignoreUnknownKeys` does backwards. Without it, adding a field would make an older file fail to
    // parse and take the fields it did carry down with it: a player upgrading would lose the galaxy
    // landing they chose, over a preference they had never had the chance to set.
    //
    // Kept private and separate from `Preferences` so the tolerance lives here rather than in the
    // record every caller builds — `Preferences` stays strict, and the compiler still catches a
    // caller that forgot a field.
    @Serializable
    private data class Record(
        val galaxyLanding: String? = null,
        val lastSeenVersion: String? = null,
    )

    private fun Record.toPreferences(): Preferences =
        Preferences(galaxyLanding = galaxyLanding, lastSeenVersion = lastSeenVersion)

    private fun Preferences.toRecord(): Record =
        Record(galaxyLanding = galaxyLanding, lastSeenVersion = lastSeenVersion)

    private companion object {

        // `ignoreUnknownKeys` because this file has no schema version and so cannot migrate: a
        // build that remembers more than this one writes keys this one has never heard of, and the
        // choice is between reading the fields we do know and throwing the whole file away. The
        // save format takes the opposite line — `GameSave` leaves unknown keys fatal, because
        // silently misreading a colony is worse than admitting it is unreadable — and the
        // difference is the stake: dropping a key here forgets a view, not a week of play.
        val json = Json { ignoreUnknownKeys = true }
    }
}
