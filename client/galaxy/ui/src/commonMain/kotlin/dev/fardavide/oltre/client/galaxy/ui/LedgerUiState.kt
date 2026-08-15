package dev.fardavide.oltre.client.galaxy.ui

// **The worlds list keeps the two jobs a list is better at than a map, and lost the three it was
// never able to do.** Davide, 2026-08-15, having played 0.11.0: *"the filters and search are
// useless"*. Claude Design diagnosed it before replacing it, and found a floor under our own
// reading: **the ledger's rows are worlds and the outbound question is about systems.** A probe is
// aimed at a star. So the ledger could not answer "where next" filtered, sorted or neither — wrong
// unit, before you ever reach a control.
//
// What went, and why each:
//
// - **The filter chips.** They narrowed a list that is fourteen rows long on a day-21 save, and
//   every axis they filtered on — distance, verdict, region — is now something you can *see* on the
//   fold rather than something you request.
// - **The sort.** A sort ranks on one axis, and "where next" is distance against class against
//   region against what is still unknown. The map holds all four at once, which is the one thing a
//   drawing does that an ordering cannot.
//
// What stayed: the search, because it matches names you have learned, which is the definition of a
// list job — it was useless on the old landing screen for a reason that was never about search, in
// that you were being asked to type the name of a place you had not been to yet. And the pins, which
// gained a second job: the system holding a pinned world is the one the map writes a name against.
data class LedgerHeadUiState(
    val mode: LedgerMode,
    // **Always visible, never a mode.** Names are unique inside a galaxy, so a full name returns one
    // row and a system name returns its worlds — the only place in the app where typing beats
    // tapping, and the literal answer to "two pages before".
    val query: String,
    // Null on the orbit page, which is a reading of one system rather than a list with a length.
    val count: String?,
)

enum class LedgerMode { WORLDS, MAP }

// **Nothing here persists except pins**, and pins live on `GalaxyState` rather than in this model.
// Claude Design's rule, 2026-08-14: *"a filter that outlives the check-in that set it is a screen
// lying about what it holds."* So the mode, the query, the chip set and the sort are navigation —
// they sit beside `at` in `GalaxyScreen` and die with the check-in.
data class LedgerBodyUiState(
    // Worlds surveyed inside the span the app just advanced through. **Derived, not stored**: the
    // event log already carries `SurveyCompleted` with an instant, and the worlds of a surveyed
    // system are regenerable from the seed — so "new" costs the save nothing and cannot fire twice.
    val discoveries: List<DiscoveryCardUiState>,
    // Sorted to the top under their own heading. A pin that changes *where a thing is* is what a pin
    // means — which is why no row grows a pin glyph and pinning is a control on the dispatch sheet.
    val pinned: List<GalaxyRowUiState.World>,
    val rows: List<GalaxyRowUiState.World>,
    // What to say when the filters exclude everything: which filter is doing the excluding and what
    // dropping it would return. The `time-until-affordable` pattern applied to a query — never a
    // dead end, always the next number.
    val emptiness: LedgerEmptinessUiState?,
)

data class LedgerEmptinessUiState(val headline: String, val detail: String)

// The survey moment. **A section of the ledger rather than a layer over it** — a card you must
// dismiss is a tax on the one thing the app promises, and three of them is that tax three times.
// Scrolling past is the dismissal.
//
// One discovery gets the large portrait and the labelled axis column; **two or more degrade to the
// compact card**, because at three the thing to protect is the scroll rather than the ceremony.
data class DiscoveryCardUiState(
    val world: String,
    val coordinate: String,
    val epithet: String,
    val portrait: WorldPortraitUiState.Surveyed,
    val temperature: String,
    val gravity: String,
    val pressure: String,
    val note: String,
    // "found 5 days ago", and **not** "found day 9": nothing in `GameState` carries a genesis
    // instant, so a day number is not derivable — where elapsed-since is, straight off the
    // `SurveyCompleted` event that put the world in the set.
    val found: String,
)
