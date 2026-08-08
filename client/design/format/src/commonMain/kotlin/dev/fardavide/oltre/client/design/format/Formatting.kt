package dev.fardavide.oltre.client.design.format

import kotlin.time.Duration

// How the game writes numbers and durations to the player. Not tokens and not components — no
// Compose reaches this file, which is why it is its own module rather than a corner of one that
// needs the compiler plugin to build.
//
// These were private copies in the Colony's and Research's ui-state mappers, and the research copy
// carried a comment saying it was "the colony's conventions, deliberately unchanged". Sharing them
// is what turns that from a promise into something the compiler keeps: a check-in reads three
// screens in a row, and a duration that is written two ways across them reads as two different
// kinds of wait.

// Mockup style: "1h 04m" / "42m"; sub-minute durations round up so a chip never reads 0m.
fun Duration.toChipLabel(): String {
    val totalMinutes = (inWholeSeconds + 59) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes.toString().padStart(2, '0')}m" else "${minutes}m"
}

// Zero-padded and always three fields, so a countdown never changes width as it runs down.
fun Long.toCountdown(): String {
    val hours = this / 3600
    val minutes = this % 3600 / 60
    val seconds = this % 60
    return "${hours.pad2()}:${minutes.pad2()}:${seconds.pad2()}"
}

// Public for the wall-clock times a row prints next to its countdown ("done 11:23"), which come
// from a `LocalDateTime` and are therefore Int.
fun Int.pad2(): String = toString().padStart(2, '0')

private fun Long.pad2(): String = toString().padStart(2, '0')

fun Long.groupedByThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()

// ── The three physical quantities the galaxy is measured in ──────────────────────────────────
//
// These were private to `GalaxyUiState` until the adaptation branch reached the Research screen.
// Both screens now print the same axes — Galaxy as a world's reading against a bound ("gravity
// 2.62, you tolerate 1.45 g"), Research as the band that bound comes from ("0.65 … 1.40 → 0.60 …
// 1.52 g") — and the second is only readable against the first if they are written identically.
// One implementation is what makes that true by construction rather than by two comments agreeing.

// A true minus sign rather than a hyphen, matching the design. Every screen in this app is numbers
// in a mono face, and a hyphen at this size reads as a dash between two figures.
fun Int.signed(): String = if (this < 0) "−${-this}" else "+$this"

// Two decimal places, which is what keeps a blocked line's four numbers on one row at 393dp. The
// scale is named by the caller rather than guessed from the magnitude: milli-g and parts-per-million
// overlap in range, so a formatter that sniffed which it had been given would be right until the
// day a world had a gravity of 0.15 g and a richness of 0.15.
fun Int.milli(): String = decimalOf(scale = 1_000)

fun Int.perMillion(): String = decimalOf(scale = 1_000_000)

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
fun Int.milliTrimmed(): String = milli().trimEnd('0').trimEnd('.')

// Rounded half up rather than truncated, matching `ResearchBalance.effectPercent`: a pressure of
// 0.016 atm reading as "0.01" understates a number the player is comparing against a band.
private fun Int.decimalOf(scale: Int): String {
    val magnitude = if (this < 0) -this else this
    val sign = if (this < 0) "−" else ""
    val hundredths = (magnitude % scale * 100 + scale / 2) / scale
    // Rounding 0.999 up carries into the whole part, which the two halves have to agree about.
    val whole = magnitude / scale + hundredths / 100
    return "$sign$whole.${(hundredths % 100).toString().padStart(2, '0')}"
}
