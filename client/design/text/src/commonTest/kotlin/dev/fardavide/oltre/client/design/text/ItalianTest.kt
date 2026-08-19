package dev.fardavide.oltre.client.design.text

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Technology
import kotlin.test.Test
import kotlin.test.assertEquals

// **The grammar, which is the half of a second language that is not vocabulary.** `CatalogueTest`
// already proves every entry resolves; what it cannot prove is that the rules around the words are
// Italian's rather than English's transliterated — a separator swapped, a plural picked on the wrong
// number, a conjunction that reads wrong before a vowel.
//
// Each of these is one of the five rows in #87's grammar table, and each was a real answer rather
// than a copy of `English`.
class ItalianTest {

    // ── Numbers ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should group a number by thousands with a point`() {
        assertEquals("1.450", Italian.resolve(Strings.groupedNumber(1_450)))
        assertEquals("12.000.000", Italian.resolve(Strings.groupedNumber(12_000_000)))
    }

    @Test
    fun `should leave a number under a thousand ungrouped`() {
        assertEquals("999", Italian.resolve(Strings.groupedNumber(999)))
        assertEquals("0", Italian.resolve(Strings.groupedNumber(0)))
    }

    @Test
    fun `should group a negative number without grouping its sign`() {
        assertEquals("-1.450", Italian.resolve(Strings.groupedNumber(-1_450)))
    }

    // The one figure that is not the language's own: a true minus sign is the design's, and it is
    // the same glyph in every language the app will ever speak.
    @Test
    fun `should write a signed number with a true minus sign`() {
        assertEquals("+5", Italian.resolve(Strings.signed(5)))
        assertEquals("−5", Italian.resolve(Strings.signed(-5)))
        assertEquals("+0", Italian.resolve(Strings.signed(0)))
    }

    // **The separator swaps with the grouping one**, which is the pair of decisions a language makes
    // together: a locale that groups with a point cannot also point its decimals.
    @Test
    fun `should write a fixed-point number with a comma`() {
        assertEquals("2,62", Italian.resolve(Strings.decimal(262, decimals = 2, trimTrailingZeros = false)))
        assertEquals("0,05", Italian.resolve(Strings.decimal(5, decimals = 2, trimTrailingZeros = false)))
        assertEquals("−1,40", Italian.resolve(Strings.decimal(-140, decimals = 2, trimTrailingZeros = false)))
    }

    @Test
    fun `should trim a fixed-point number when asked`() {
        assertEquals("0,5", Italian.resolve(Strings.decimal(50, decimals = 2, trimTrailingZeros = true)))
        assertEquals("0,44", Italian.resolve(Strings.decimal(44, decimals = 2, trimTrailingZeros = true)))
        // Trimming takes the separator with it when nothing is left of the fraction — and the
        // separator it takes is the comma, not English's point.
        assertEquals("1", Italian.resolve(Strings.decimal(100, decimals = 2, trimTrailingZeros = true)))
        assertEquals("20", Italian.resolve(Strings.decimal(2_000, decimals = 2, trimTrailingZeros = true)))
    }

    // ── Durations ────────────────────────────────────────────────────────────────────────────

    // Davide's call, 2026-08-19: **h and m stay, d becomes g.** The first two are read the same in
    // Italian and a player has seen them on every clock they own; `d` for *giorni* is the one that
    // is simply English.
    @Test
    fun `should write a duration with Italian unit letters`() {
        assertEquals("42m", Italian.resolve(Strings.durationMinutes(42)))
        assertEquals("1h 04m", Italian.resolve(Strings.durationHoursMinutes(1, 4)))
        assertEquals("186h", Italian.resolve(Strings.durationHours(186)))
        assertEquals("18g 13h", Italian.resolve(Strings.durationDaysHours(18, 13)))
        assertEquals("4g 06h", Italian.resolve(Strings.durationDaysHours(4, 6)))
    }

    @Test
    fun `should write a countdown in three fields of two`() {
        assertEquals("00:00:01", Italian.resolve(Strings.countdown(0, 0, 1)))
        assertEquals("12:34:56", Italian.resolve(Strings.countdown(12, 34, 56)))
    }

    // ── Plurals ──────────────────────────────────────────────────────────────────────────────

    // Italian has English's two forms and picks between them on the same number, so what is being
    // tested here is not the *rule* but that each noun's plural was actually written: `scialuppa`
    // and `mondo` do not pluralise the same way, and neither takes an `s`.
    @Test
    fun `should pluralise a noun on its count`() {
        assertEquals("1 scialuppa", Italian.resolve(Strings.skiffCount(1)))
        assertEquals("3 scialuppe", Italian.resolve(Strings.skiffCount(3)))
        assertEquals("1 mondo", Italian.resolve(Strings.worldCount(1)))
        assertEquals("4 mondi", Italian.resolve(Strings.worldCount(4)))
        assertEquals("1 galassia", Italian.resolve(Strings.galaxiesCount(1)))
        assertEquals("2 galassie", Italian.resolve(Strings.galaxiesCount(2)))
    }

    // Zero is its own sentence rather than a plural, exactly as it is in English.
    @Test
    fun `should say no worlds rather than zero worlds`() {
        assertEquals("nessun mondo", Italian.resolve(Strings.worldCount(0)))
    }

    // ── Spelled numbers ──────────────────────────────────────────────────────────────────────

    // The one place Italian is *easier* than English: `due` through `otto` are invariable, so the
    // gender of what is being counted cannot reach them. Only `uno` declines, and this entry is
    // never called below two — a group is two or more by construction.
    @Test
    fun `should spell the count in an upgrade group`() {
        assertEquals("Due miglioramenti sono pronti", Italian.resolve(Strings.upgradesDoneTitle(2)))
        assertEquals("Cinque miglioramenti sono pronti", Italian.resolve(Strings.upgradesDoneTitle(5)))
        assertEquals("Otto miglioramenti sono pronti", Italian.resolve(Strings.upgradesDoneTitle(8)))
    }

    // ── The conjunction, which is the one rule that needed more than a table entry ────────────

    // **`e` before a consonant, `ed` before another `e`.** This is the euphonic rule modern Italian
    // still keeps — the Crusca dropped it everywhere except before a word starting with `e-`, which
    // in this catalogue is `Estrazione` and will be whatever the next technology is called.
    //
    // It cannot be answered by `ListLastSeparator` alone: a separator is resolved without seeing what
    // follows it. So `Italian` overrides the joined branch for this one shape — see there.
    @Test
    fun `should join a list with e`() {
        assertEquals(
            "Miniera di Metallo, Centrale Solare e Fabbrica Robotica",
            Italian.resolve(
                Strings.listed(
                    listOf(TextRes("Miniera di Metallo"), TextRes("Centrale Solare"), TextRes("Fabbrica Robotica")),
                ),
            ),
        )
    }

    @Test
    fun `should join a list with ed before a word starting with e`() {
        assertEquals(
            "Miniera di Metallo, Centrale Solare ed Estrazione",
            Italian.resolve(
                Strings.listed(
                    listOf(TextRes("Miniera di Metallo"), TextRes("Centrale Solare"), TextRes("Estrazione")),
                ),
            ),
        )
    }

    @Test
    fun `should join a pair with the same rule`() {
        assertEquals(
            "Estrazione e Arricchimento",
            Italian.resolve(Strings.listed(listOf(TextRes("Estrazione"), TextRes("Arricchimento")))),
        )
        assertEquals(
            "Arricchimento ed Estrazione",
            Italian.resolve(Strings.listed(listOf(TextRes("Arricchimento"), TextRes("Estrazione")))),
        )
    }

    // The other joined shapes are untouched by that override, and this is what says so: a run of
    // clauses is punctuation rather than a conjunction, and a language that changed it here would be
    // changing the middot on the Shipyard's pool line.
    @Test
    fun `should leave a run of clauses to its own separator`() {
        assertEquals(
            "6 totali · 1 in porto",
            Italian.resolve(Strings.clauses(listOf(Strings.shipsOwned(6), Strings.shipsIdle(1)))),
        )
    }

    // ── Where a hull's own name would have needed an agreement ───────────────────────────────

    // The pool line is drawn on one hull's card, so its subject is whichever of the four that card
    // is about — two feminine and two masculine. A preposition and a place agree with neither.
    @Test
    fun `should write the pool line without agreeing with any hull`() {
        assertEquals("6 totali", Italian.resolve(Strings.shipsOwned(6)))
        assertEquals("1 in porto", Italian.resolve(Strings.shipsIdle(1)))
        assertEquals("5 fuori", Italian.resolve(Strings.shipsAway(5)))
        assertEquals("2 in corso", Italian.resolve(Strings.shipsBuilding(2)))
        assertEquals("1 in coda", Italian.resolve(Strings.shipsQueued(1)))
    }

    // **English leaves this noun singular and Italian cannot.** "3 skiff" is a tally; "3 scialuppa"
    // is not a phrase. The plural was already in the table, so the tally resolves to it — including
    // for `cargo`, which is a loanword and therefore does not inflect at all.
    @Test
    fun `should pluralise a tally of a hull type`() {
        assertEquals("3 scialuppe", Italian.resolve(Strings.shipsOfType(3, ShipType.SKIFF)))
        assertEquals("1 scialuppa", Italian.resolve(Strings.shipsOfType(1, ShipType.SKIFF)))
        assertEquals("3 cargo", Italian.resolve(Strings.shipsOfType(3, ShipType.HAULER)))
        assertEquals("1 cargo", Italian.resolve(Strings.shipsOfType(1, ShipType.HAULER)))
    }

    // ── Gender, which is the rule the catalogue is *shaped* by rather than the one it applies ──

    // **No message composes an article with a name.** `Miniera` is feminine and `Sintetizzatore` is
    // masculine, so `la ${name}` is unwritable from a table — and rather than carry a gender on every
    // `Arg.Text`, every such sentence puts a fixed head noun in front and hangs the name off `di`.
    // The article then agrees with the head noun, which the entry chose and therefore knows.
    //
    // These two are the first messages that needed it, and the ones the rest follow.
    @Test
    fun `should hang a facility name off a head noun rather than an article`() {
        assertEquals(
            "Il prossimo livello di Miniera di Metallo richiede ",
            Italian.resolve(Strings.sheetNextBuildTakes(Strings.buildingName(BuildingType.METAL_MINE))),
        )
        assertEquals(
            "Il prossimo livello di Sintetizz. Deuterio richiede ",
            Italian.resolve(Strings.sheetNextBuildTakes(Strings.buildingName(BuildingType.DEUTERIUM_SYNTHESIZER))),
        )
    }

    @Test
    fun `should hang a technology name off a head noun rather than an article`() {
        assertEquals(
            "La ricerca Fotovoltaico moltiplica la fornitura, e non è la fornitura a limitarti. A ",
            Italian.resolve(Strings.sheetMultipliesSupply(Strings.technologyName(Technology.PHOTOVOLTAICS))),
        )
    }
}
