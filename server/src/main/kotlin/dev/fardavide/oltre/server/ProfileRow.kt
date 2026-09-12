package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.CommanderName
import dev.fardavide.oltre.protocol.PlayerMark
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.Protocol

// **Neither column can throw on the way out, and that is one rule rather than two.** A value written
// by a *newer* deploy is a state the service can genuinely be in — a rollback is one command and
// `#111` exercised one — and a read path that raised on it would turn a routine downgrade into
// `ApiError.Internal` for every request that account makes, including the sync. Degrading is the
// only answer that leaves the player with a game.
//
// It also catches the row an operator edited by hand, which is the other way columns like these go
// wrong and the reason the `catch` is on `Exception` rather than on the serializer's own type.
//
// **What the player sees when it degrades**, which is the half worth writing down: exactly what an
// account that has chosen nothing sees. A mark that will not read draws `THRESHOLD` and a name that
// will not read reads `Dead Reckoning` — the strip's own substitution for null, not a placeholder
// invented here — and the next save from the editor writes a pair this build can hold. Nothing on
// screen says *error*, because from the player's side nothing has gone wrong that they can act on.
//
// **The name half was missing and the argument above applied to it unchanged.** `CommanderName`'s
// guards are what a *request* is checked against — `readRequest` turns them into `ApiError.Malformed`
// — but `display_name` is a `text` column holding whatever any deploy ever wrote, and a bound that
// moves is exactly the rollback this comment was written for.
internal fun profileFrom(name: String?, mark: String?): PlayerProfile = PlayerProfile(
    name = name?.let { held -> runCatching { CommanderName(held) }.getOrNull() },
    mark = mark?.let { document ->
        runCatching { Protocol.json.decodeFromString(PlayerMark.serializer(), document) }.getOrNull()
    },
)
