package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.text.TextRes

// A mid-game colony's stocks. The scaffold's tests are about the frame rather than the numbers in
// it, so they all share one rail instead of each inventing its own.
//
// Unthrottled, deliberately: these are the frame's tests, and a power shortage is a state of the
// rail rather than of the scaffold around it. What the amber marks look like is asserted where it
// belongs, by the `resource_rail_throttled` baseline.
//
// **Every cell is settled** — `lastSeenStock` equals `stock`, so the roll has nowhere to travel and
// the rail draws its final figures on the first frame. That is what the frame's tests are about; a
// rail caught mid-roll is a different assertion and belongs to the one baseline that makes it.
internal val testResourceRailUiState = ResourceRailUiState(
    metal = settled(stock = 15_534, ratePerHour = "+950/h"),
    crystal = settled(stock = 6_286, ratePerHour = "+304/h"),
    deuterium = settled(stock = 732, ratePerHour = "+72/h"),
    throttled = false,
)

// The case the merged stock-and-rate line has to survive. Once the rate sits beside the stock
// rather than under it, the pair is what has to fit the cell — and at 320dp, a third of that is
// not enough for six figures plus a rate. These are the stocks the design measured the overflow
// with, so they are the ones the assertion uses.
internal val sixFigureResourceRailUiState = ResourceRailUiState(
    metal = settled(stock = 147_169, ratePerHour = "+12,400/h"),
    crystal = settled(stock = 89_412, ratePerHour = "+6,180/h"),
    deuterium = settled(stock = 112_006, ratePerHour = "+900/h"),
    throttled = false,
)

// **The case the compact rule exists for, and the one the six-figure fixture above cannot make.**
// There every cell overflows, so the bar stacks whether or not anything told it to. Here the
// deuterium cell fits a Slide Over's ~85dp on one line — "740 +90/h" is about 65dp at these sizes —
// while the other two do not, which is the ordinary shape of a colony that has been mining for a
// day. Left to the measurement the bar comes out one line taller in two cells than in the third.
internal val lopsidedResourceRailUiState = ResourceRailUiState(
    metal = settled(stock = 482_910, ratePerHour = "+12,400/h"),
    crystal = settled(stock = 198_340, ratePerHour = "+6,180/h"),
    deuterium = settled(stock = 740, ratePerHour = "+90/h"),
    throttled = false,
)

// A cell with nothing to announce: what the player last saw is what the colony holds.
private fun settled(stock: Long, ratePerHour: String) =
    ResourceStockUiState(stock = stock, lastSeenStock = stock, ratePerHour = TextRes(ratePerHour))
