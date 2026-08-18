package dev.fardavide.oltre.client.design.format

import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import kotlin.time.Duration

// How the game measures numbers and durations for the player. Not tokens and not components — no
// Compose reaches this file, which is why it is its own module rather than a corner of one that
// needs the compiler plugin to build.
//
// These were private copies in the Colony's and Research's ui-state mappers, and the research copy
// carried a comment saying it was "the colony's conventions, deliberately unchanged". Sharing them
// is what turns that from a promise into something the compiler keeps: a check-in reads three
// screens in a row, and a duration that is written two ways across them reads as two different
// kinds of wait.
//
// **Every function here returns a `TextRes` and none of them returns a `String`, since #86.** The
// split that arrived with the catalogue runs straight through this module and is worth stating,
// because it is the reason the file still exists: *deciding which numbers to show is arithmetic, and
// writing them down is language.* Rounding a duration up so a chip never reads 0m, choosing days
// over hours past the day boundary, carrying a rounded fraction into the whole part — all of that is
// the same in every language and stays here. The unit letters, the padding, the thousands separator
// and the decimal point are English's, and moved to `Translations`.

// Mockup style: "1h 04m" / "42m"; sub-minute durations round up so a chip never reads 0m.
fun Duration.toChipLabel(): TextRes {
    val totalMinutes = (inWholeSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) Strings.durationHoursMinutes(hours, minutes) else Strings.durationMinutes(minutes)
}

// How long until a level has paid for itself, which is a different kind of duration from every
// other one the app writes and therefore reads differently.
//
// **Minutes matter under a day and stop mattering past one.** A technology that repays in 1h 42m is
// something you decide about now, so the minutes are the decision; a mine that repays in 102h is
// four days, and "102h 14m" is false precision on a number nobody will hold in their head. There is
// deliberately no day unit — the brief writes a 186-hour build as 186 hours, and hours stay
// comparable against each other all the way up where "4d 6h" against "1h 42m" does not.
fun Duration.toPaybackLabel(): TextRes =
    if (inWholeHours < HOURS_PER_DAY) toChipLabel() else Strings.durationHours(inWholeHours)

private const val HOURS_PER_DAY: Int = 24
private const val MINUTES_PER_HOUR: Int = 60
private const val SECONDS_PER_MINUTE: Int = 60
private const val SECONDS_PER_HOUR: Int = 3_600

// ── How long until a world is worth visiting again ───────────────────────────────────────────
//
// Claude Design's three tiers, and only the top one is new. A deposit refills at 5% of its cap a day,
// so this one label has to carry everything from nineteen minutes to nineteen days without ever
// showing three units — which is why it delegates to the two shapes the app already has and adds a
// third only where they run out.
//
// **`toPaybackLabel` deliberately has no day unit and keeps none**: the brief writes a 186-hour build
// as 186 hours, and hours stay comparable against each other all the way up where "4d 6h" against
// "1h 42m" does not. That argument is about *comparing* payback figures in a column. A wait is not
// compared with anything — it answers "come back when?" once — so at eighteen days "18d 13h" is the
// readable form and "445h" is arithmetic homework.
//
// Below a day this is `toChipLabel`, and below an hour it is the running countdown — which is the
// tier the mechanic actually spends most of its time in, since a 1,450 vein puts a whole unit back
// every twenty minutes.
fun Duration.toWaitLabel(): TextRes = when {
    inWholeHours >= HOURS_PER_DAY -> Strings.durationDaysHours(inWholeDays, inWholeHours % HOURS_PER_DAY)
    inWholeHours >= 1 -> toChipLabel()
    // Ceiled to the second for `toChipLabel`'s own reason: a wait reading 00:00:00 claims a thing has
    // already happened, and this one has not.
    else -> ((inWholeMilliseconds + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).coerceAtLeast(1).toCountdown()
}

private const val MILLIS_PER_SECOND: Long = 1_000

// Three fields, so a countdown never changes width as it runs down — and hours carry past a day
// rather than wrapping, which is why this takes seconds rather than a `Duration`'s day part.
fun Long.toCountdown(): TextRes =
    Strings.countdown(
        hours = this / SECONDS_PER_HOUR,
        minutes = this % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
        seconds = this % SECONDS_PER_MINUTE,
    )

// "→ affordable 19:51" — the one line a watched row adds, and the same line whether the row is a
// facility, a technology or an adaptation ladder. Here rather than in either mapper for the reason
// the duration formats are here: one watch exists in the whole game, so a player who moves it
// between two screens is looking at what has to be one sentence, and two private copies of it are
// two sentences waiting to drift.
//
// Takes the clock fields rather than an instant, so this module still needs no date library — the
// caller has a `LocalDateTime` already.
fun watchedAtLabel(hour: Int, minute: Int): TextRes = Strings.watchedAt(hour = hour, minute = minute)

fun Long.groupedByThousands(): TextRes = Strings.groupedNumber(this)

// ── The three physical quantities the galaxy is measured in ──────────────────────────────────
//
// These were private to `GalaxyUiState` until the adaptation branch reached the Research screen.
// Both screens now print the same axes — Galaxy as a world's reading against a bound ("gravity
// 2.62, you tolerate 1.45 g"), Research as the band that bound comes from ("0.65 … 1.40 → 0.60 …
// 1.52 g") — and the second is only readable against the first if they are written identically.
// One implementation is what makes that true by construction rather than by two comments agreeing.

// A true minus sign rather than a hyphen, matching the design — English's business, not this
// module's, but the *choice to sign at all* is the reading and belongs to the caller.
fun Int.signed(): TextRes = Strings.signed(this)

// Two decimal places, which is what keeps a blocked line's four numbers on one row at 393dp. The
// scale is named by the caller rather than guessed from the magnitude: milli-g and parts-per-million
// overlap in range, so a formatter that sniffed which it had been given would be right until the
// day a world had a gravity of 0.15 g and a richness of 0.15.
fun Int.milli(): TextRes = decimalOf(scale = MILLI, trimTrailingZeros = false)

fun Int.perMillion(): TextRes = decimalOf(scale = PER_MILLION, trimTrailingZeros = false)

// The same quantity with the zeros it does not need taken off — "0.50" becomes "0.5", "0.44" stays.
//
// One caller, and a width reason rather than a taste one, so it is worth writing down. The pressure
// band on a Research row is the widest line the app draws: "0.50 … 2.60 → 0.44 … 3.50 atm" is 29
// monospace characters, and beside a ghost button at 320dp there is room for about 26. Padded, the
// unit is the part that gets cut, and "0.44 … 3.50 a…" is worse than any rounding. Trimmed, it is
// the design's own string and it fits.
//
// Deliberately *not* applied to gravity or to the Galaxy screen's readings: those fit at both
// widths, and a column of "1.4" over "0.65" stops being a column in a tabular face. That the two
// axes therefore print differently is the design's call, not an accident — see `decisions.md`.
fun Int.milliTrimmed(): TextRes = decimalOf(scale = MILLI, trimTrailingZeros = true)

private const val MILLI: Int = 1_000
private const val PER_MILLION: Int = 1_000_000
private const val HUNDREDTHS: Int = 100

// Rounded half up rather than truncated, matching `ResearchBalance.effectPercent`: a pressure of
// 0.016 atm reading as "0.01" understates a number the player is comparing against a band.
//
// The rounding is the whole of what this function does now — where the point goes and whether the
// zeros survive it are `Translations`'.
private fun Int.decimalOf(scale: Int, trimTrailingZeros: Boolean): TextRes {
    val magnitude = if (this < 0) -toLong() else toLong()
    val hundredths = (magnitude * HUNDREDTHS + scale / 2) / scale
    return Strings.decimal(
        scaled = if (this < 0) -hundredths else hundredths,
        decimals = 2,
        trimTrailingZeros = trimTrailingZeros,
    )
}
