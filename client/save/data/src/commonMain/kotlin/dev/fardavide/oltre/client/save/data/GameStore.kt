package dev.fardavide.oltre.client.save.data

import dev.fardavide.oltre.core.DecodeResult
import dev.fardavide.oltre.core.GameSave
import dev.fardavide.oltre.core.GameSnapshot

// Turns the text file into snapshots and back. A save that cannot be read is reported as no
// save at all: the reasons core distinguishes (corrupt, truncated, written by a newer build)
// all leave the player in the same place, at the start of a new colony.
class GameStore(private val file: SaveFile) {

    suspend fun load(): GameSnapshot? {
        val text = file.read() ?: return null
        return when (val decoded = GameSave.decode(text)) {
            is DecodeResult.Success -> decoded.snapshot
            is DecodeResult.Failure -> null
            // A colony from a schema this build has retired starts over. The reason travels with
            // the result so a "your colony was reset, here is why" notice can be built on it
            // without changing this layer; there is no such screen yet.
            is DecodeResult.Obsolete -> null
        }
    }

    suspend fun save(snapshot: GameSnapshot) {
        file.write(GameSave.encode(snapshot))
    }

    // Forget the colony. The next `load` answers null, which is the same answer an unreadable save
    // and a first launch already give — so a reset needs no new path through the shell, only the
    // one that already exists for opening the game with nothing saved.
    suspend fun clear() {
        file.clear()
    }
}
