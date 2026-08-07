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
