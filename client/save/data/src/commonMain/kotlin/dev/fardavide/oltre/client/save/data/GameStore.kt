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
        }
    }

    suspend fun save(snapshot: GameSnapshot) {
        file.write(GameSave.encode(snapshot))
    }
}
