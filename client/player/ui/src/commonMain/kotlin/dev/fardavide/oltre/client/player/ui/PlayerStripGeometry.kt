package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// **The strip's numbers, and the one piece of arithmetic that reads them.** A separate file from the
// drawing, and the reason is a measurement rather than taste.
//
// A unit test cannot render a composable, so the unit pass excludes every `@Composable` by
// annotation. What it does *not* exclude is a file's top-level properties — and a class Kover never
// sees loaded reports only the lines it can attribute, so `PlayerStrip.kt` counted twenty
// uncoverable property initialisers and nothing else. Reaching for one of those constants from a
// unit test to cover them **loads the file class and makes it worse**: measured on this branch,
// asserting on `STRIP_HEIGHT` in place took the unit line from 91.42% to 91.31%, because loading
// `PlayerStripKt` brought forty-one lines of composable body into the denominator along with the
// twenty it covered.
//
// So the constants live where a test can reach them without dragging a drawing in behind them. That
// is the same shape `:client:design:icon` arrived at for the same reason — the bell's geometry came
// out of its `Canvas { }` lambda so it could be measured — and it is a better file either way: what
// the strip *is* separated from how it is drawn.
//
// Nothing here is `private`: the file is the seam, and a constant nothing outside can read is a
// constant that has to be re-typed by whatever wants to check it.

// 38dp: a 20dp mark with the resource rail's own 9dp of vertical padding above and below it, so the
// two tiers of chrome rhyme rather than merely stack. Pinned rather than measured — the gear's tap
// target is as tall as the strip, so a wrap-content row would take its height *from* the target.
internal val STRIP_HEIGHT = 38.dp

// **38dp square, not the 44 everyone reaches for first.** `WatchSquare` settled it for the whole
// app: a child placed outside its parent's bounds does not reliably receive touch, so a 44dp claim
// inside a 38dp band either misses the tap or grows the band — and the band's height is the most
// expensive number in this design. Its own remedy applies: claim the axis you can afford. This is
// still larger than the 29dp square that already ships stacked on a colony row.
internal val GEAR_TARGET = 38.dp

// How long the notice stays. Long enough to read eleven characters and be sure they were meant,
// short enough that it is gone before a player has decided to do something else.
internal const val NOTICE_MILLIS = 2_000L

internal val EDGE = 11.dp

// Asymmetric against `EDGE`, and not a mistake: the gear's target is 10dp wider than its face on
// each side, so 2dp here puts the *glyph* 12dp from the edge while the tappable area runs out to it.
internal val GEAR_EDGE = 2.dp
internal val GAP = 7.dp
internal val GEAR_RADIUS = 9.dp

// Deliberately not the rail's 15sp SemiBold: at the same size and weight as the three figures below
// it, the name reads as a fourth statistic rather than as whose statistics they are.
internal val NAME_SIZE = 13.5.sp
internal val NOTICE_SIZE = 10.5.sp

internal val BADGE_SIZE = 10.sp
internal val BADGE_RADIUS = 4.dp
internal val BADGE_PAD_X = 5.dp
internal val BADGE_PAD_Y = 1.dp

internal val GAUGE_WIDTH = 72.dp

// The one thing that gives below the compact width. The name ellipsises after it, which is the order
// the design wants: a name half-read is worse than a gauge that is shorter.
internal val GAUGE_WIDTH_COMPACT = 48.dp
internal val GAUGE_HEIGHT = 3.dp
internal val GAUGE_RADIUS = 2.dp

// The same white 9% the rail and the cards use, so every hairline, track and badge fill in the app
// is one decision rather than several that happen to match.
internal val HAIRLINE = Color.White.copy(alpha = 0.09f)
internal val HAIRLINE_WIDTH = 1.dp
internal val TRACK = Color.White.copy(alpha = 0.09f)
internal val BADGE_FILL = Color.White.copy(alpha = 0.09f)

// **How much of the track is lit, and the clamp is the point rather than defensiveness.** A reading
// is a reading: this module holds no rule saying experience cannot exceed a level's requirement, and
// the day something awards it, the mapper one layer up may hand this a figure it has not normalised.
// `fillMaxWidth` throws outside 0..1, so an unclamped fraction is a crash on the frame the number
// goes wrong rather than a bar that looks odd.
//
// Stated as a function rather than inlined so the clamp is a property instead of a promise — it is
// exactly the arithmetic a screenshot cannot fail on, because a bar drawn at 100% and a bar drawn at
// 140% are the same picture.
internal fun experienceFraction(percent: Int): Float = percent.coerceIn(0, FULL) / FULL.toFloat()

private const val FULL = 100
