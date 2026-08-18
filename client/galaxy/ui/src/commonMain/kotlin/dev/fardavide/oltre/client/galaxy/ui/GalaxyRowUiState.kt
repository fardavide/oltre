package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind

// **One row shape, six verdicts, two screens.** The system view and the ledger draw the same card —
// that is what makes the ledger cheap and what makes a world look like itself wherever you meet it.
//
// Six lines of possible content in a **fixed order that never reorders**: the disc and the name; the
// epithet and the address; the two deposits; one line per failing axis; the note. A block that has
// nothing to say is omitted and nothing takes its place.
//
// ── What 0.11 subtracted, and what each subtraction bought ───────────────────────────────────────
//
// The premise handed to Claude Design was that a name, an epithet and a portrait must displace
// something. They displace three things, and all three are things the disc says better:
//
// - **the orbit tag** — `hot · temperate · cold` was a three-value caption in a colour that meant
//   temperature and nothing else, the one place in the app where a hue was neither affordability nor
//   status. The disc's fill is the same fact at higher resolution, in the leading position, with no
//   word. `OrbitBand` went with it, and so did the list's band headings.
// - **the hazard words** — four hazards, four marks. They survive wherever there is room: the
//   dispatch sheet, the discovery card, the notification.
// - **the per-row round trip** — distance and the danger band are identical for all fifteen slots of
//   any *other* system, so the astronomy line under the header already said it. See `trailing`.
//
// Net: a surveyed row is the height it was at 0.9, and an unsurveyed row — 98% of them — is shorter.
sealed interface GalaxyRowUiState {

    // **The whole address rather than the slot, and that is a bug fix rather than a tidy-up.** The
    // ledger draws rows from every system at once, so a slot names as many worlds as there are
    // systems in the list — and a row that handed a tap nothing but its slot left the sheet to fill
    // in the other two thirds from whichever system the *map* was parked on. It priced the same slot
    // of that system instead, which on the screen the tab opens on is home: a full deposit on the
    // card and an empty one in the sheet, about two different worlds.
    val at: GalaxyCoordinate

    data class World(
        override val at: GalaxyCoordinate,
        // The headline, and the whole point of the slice. `Calianova VIII`.
        val name: TextRes,
        // **Rendered once, in one of two positions**, and which one is decided by the epithet alone:
        // with an epithet it sits on the subtitle line, without one the subtitle ceases to exist and
        // the address trails the headline. The coordinate never disappears — it is the address, and
        // the arithmetic, the eventual multiplayer chat and the ledger's own key all need it.
        val coordinate: TextRes,
        val portrait: WorldPortraitUiState,
        // Null on a world nobody has surveyed, which is the same permission the portrait's socket
        // carries — see `WorldPortraitUiState`, where the two are made one decision.
        val epithet: TextRes?,
        val verdict: WorldVerdictUiState,
        // The round trip, and **only in the ledger**, where rows come from many systems at once and
        // there is no shared header to state it. Null in the system view.
        //
        // It is also where the per-row reach would come back if it ever has to: inside *your own*
        // system the trip really does vary by slot — `5 × |slotA − slotB|` units — where for every
        // other system all fifteen slots are equidistant.
        val trailing: TextRes?,
        val deposits: DepositReadingUiState?,
        // Never empty on a `BLOCKED` row and always empty on every other, but carried on the row
        // rather than inside the verdict so that one row shape serves six verdicts.
        val requirements: List<BlockedAxisUiState>,
        val note: TextRes?,
    ) : GalaxyRowUiState

    // Not a world and not a card: a hairline and no fill, and not tappable. It states its effect and
    // stops — no holding mechanic exists until multiplayer, and a relay has no hold for a fleet to
    // fill either.
    data class Relay(
        override val at: GalaxyCoordinate,
        val coordinate: TextRes,
        val effect: TextRes,
    ) : GalaxyRowUiState
}

// Flat, because treatment 1b's payloads all moved onto the row itself.
//
// **`UNSURVEYED` has no word, and that is the design's one subtraction.** An empty socket where
// every surveyed row has a body is the state, stated in the position where the state belongs — and
// it bought back a colour, a ten-character reading and the row's whole right end on 98% of rows.
// The constant is kept rather than the case being deleted so that the decision stays arguable.
enum class WorldVerdictUiState(val word: TextRes?) {
    HOME(Strings.verdictWordHome()),
    OCCUPIED(Strings.verdictWordOccupied()),
    UNSURVEYED(null),
    BLOCKED(Strings.verdictWordBlocked()),
    BARREN(Strings.verdictWordBarren()),
    SETTLEABLE(Strings.verdictWordSettleable()),
}

// What is still in the ground. Metal then crystal, rail order, and **never deuterium** — a run
// cannot lift it, so a figure for it would be an offer the verb refuses.
//
// **No noun.** It is `metal full`, not `metal left`: roughly 98% of worlds have never been touched,
// and "left" would assert that somebody had taken some. A word at each end and a working fraction
// between them is what keeps an untouched galaxy reading as a shape the eye skips.
data class DepositReadingUiState(val metal: DepositItemUiState?, val crystal: DepositItemUiState?)

// The word takes the resource's colour and the reading takes the tone's, and the two are one
// unbreakable run: `metal` and `full` must never wrap apart.
data class DepositItemUiState(
    val resource: ResourceKind,
    val reading: TextRes,
    val tone: DepositTone,
)

enum class DepositTone { FULL, EMPTY, PARTIAL }

// "gravity 1.79, you tolerate 1.40 g — Gravitic 4". The unit is written once, on the tolerance:
// both numbers are the same axis and therefore the same unit, and the four characters that saves are
// what keep the technology on the line at 393dp.
//
// **`Blocked` naming its own remedy is the design's load-bearing detail** — it is what turns the
// galaxy screen into a reason to research, and the only thing connecting two tabs that otherwise
// never speak.
data class BlockedAxisUiState(
    val axis: TextRes,
    val reading: TextRes,
    val tolerated: TextRes,
    // "gravity 2.62, you tolerate 1.45 g" — the three above as the one line the row draws. Composed
    // by the mapper for the reason every other sentence is; the three parts stay because the sheet
    // and the tests read them individually.
    val clause: TextRes,
    // The ladder itself as well as the string it renders: the label is what the row prints, the
    // enum is what the tap target is keyed by, so the one place this row names a technology is not
    // a bare string.
    val technology: AdaptationTechnology,
    val label: TextRes,
)
