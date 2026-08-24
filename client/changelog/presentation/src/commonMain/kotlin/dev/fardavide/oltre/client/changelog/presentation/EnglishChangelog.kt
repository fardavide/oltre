package dev.fardavide.oltre.client.changelog.presentation

// **Every release Oltre has shipped, newest first.** Condensed from the README's changelog, which is
// the record; this is the reading. Every entry is one headline of at most 40 characters and one to
// three notes of at most 90, and the budget is the design's rather than the layout's — see
// `.claude/docs/changelog-sheet.md` §3 and `ChangelogBudgetTest`.
//
// **Adding a release means adding it here and in `ItalianChangelog`**, or the build fails: the
// version has to match `libs.versions.oltre`, the date has to match the README's own heading, and
// the two documents have to agree. That is three tests, and they are the whole reason a per-release
// obligation this small can be trusted to survive a year.
//
// There is no 0.0.12. The release exists and the README skips it, so the run is not contiguous and
// nothing here assumes it is.
object EnglishChangelog : ChangelogText {

    override val releases: List<Release> = listOf(
        release(
            "0.20.1", "2026-08-24", "The tray empties when you open the game",
            "Alerts you have already been shown are cleared when the colony comes back up.",
            "Alerts still to come are untouched.",
        ),
        release(
            "0.20.0", "2026-08-24", "The galaxy is dark now",
            "A star nobody has been near is a point of light and nothing else.",
            "A probe charts everything nearer than where it went, and an hour further.",
            "You open on 61 systems of 250, and your ships light the rest.",
        ),
        release(
            "0.19.0", "2026-08-24", "The game tells you what changed",
            "Every version Oltre has shipped, one page each, newest first.",
            "It opens itself on a new build and not again.",
            "Each page draws its own sky from the version number.",
        ),
        release(
            "0.18.0", "2026-08-23", "The gear opens something",
            "Alerts can be asked for by category instead of row by row.",
            "Delivery says how many notifications the answers arrive in.",
            "New colonies start louder and quieter at once.",
        ),
        release(
            "0.17.1", "2026-08-23", "Your name gets the whole line",
            "The experience gauge became the strip's own bottom edge.",
            "The longest name now fits whole in the narrowest window.",
        ),
        release(
            "0.17.0", "2026-08-23", "The gauge fills",
            "Everything you finish now pays experience.",
            "Opening this build credits everything you did before the level existed.",
            "A day in is about level 3, a week 10, a month 25.",
        ),
        release(
            "0.16.0", "2026-08-22", "The game knows who is playing",
            "A strip above the resources carries your mark, your name and your level.",
            "You are Dead Reckoning, at level 0.",
        ),
        release(
            "0.15.4", "2026-08-22", "Every alert is one you asked for",
            "A bell beside Dispatch: tap it and that flight says so when it lands.",
            "A flight you did not ask about no longer buzzes.",
        ),
        release(
            "0.15.3", "2026-08-22", "Hulls say when they are done",
            "A bell beside Build: once for the whole order, twice for every hull.",
            "Each hull is asked about separately.",
        ),
        release(
            "0.15.2", "2026-08-21", "Four crashes and a miscount",
            "A hauler is no longer offered for a world no window lets it fly to.",
            "A fleet of haulers is counted in berths again.",
            "Italian: the deposit is masculine and a vein of one agrees with itself.",
        ),
        release(
            "0.15.1", "2026-08-21", "Distant worlds stop closing the app",
            "A world nothing can reach inside a day now says so and points at Propulsion.",
            "A fleet of haulers alone can be sent again.",
        ),
        release(
            "0.15.0", "2026-08-21", "Distance costs, and a drive buys it back",
            "Flying anywhere costs twice what it did, so the far map is out of reach.",
            "Propulsion hands window rungs back to worlds that refused them.",
            "Surveying costs a ship, and the Scout is the cheapest thing in the game.",
        ),
        release(
            "0.14.0", "2026-08-19", "The game speaks Italian",
            "Every word, on a phone set to Italian, with nothing to switch on.",
            "Numbers, worlds and the fleet are Italian too.",
        ),
        release(
            "0.13.2", "2026-08-18", "Buttons stop flashing a square",
            "A tap highlight now stops where the control does.",
            "Switching tabs is a move rather than a cut.",
            "Nothing loops, and nothing runs on its own.",
        ),
        release(
            "0.13.1", "2026-08-17", "The sheet opens on a fleet that fills",
            "Past a certain size every extra skiff comes home empty.",
            "Hold − or + to run the count.",
        ),
        release(
            "0.13.0", "2026-08-16", "The Fleets tab has a door back",
            "One row per world your fleet has worked, newest landing first.",
            "Tap one to send another run.",
            "A world whose vein you finished says empty on the row.",
        ),
        release(
            "0.12.2", "2026-08-16", "Adaptation stops taking turns",
            "Each branch has its own queue, so a ladder and a technology run side by side.",
        ),
        release(
            "0.12.1", "2026-08-16", "The bar under the map is back",
            "The map folds into the room left after the bar rather than taking it first.",
        ),
        release(
            "0.12.0", "2026-08-15", "The star map lands",
            "Two hundred and fifty stars in ten named bands, drawn on one screen.",
            "Near on the map is near in the game.",
            "One tap up shows all four galaxies and what a run to each costs.",
        ),
        release(
            "0.11.3", "2026-08-15", "A new colony starts with no ship",
            "The first hull is the first thing you buy rather than something issued.",
        ),
        release(
            "0.11.2", "2026-08-15", "Worlds hold four times as much",
            "A doorstep world carries 5,800 metal; a hazardous one up to 15,950.",
            "A fleet still empties a world in a day — it takes four hulls to do it.",
        ),
        release(
            "0.11.1", "2026-08-15", "The ledger opens the world you tap",
            "It opened the same slot of whichever system the map was last on.",
        ),
        release(
            "0.11.0", "2026-08-14", "The map has places, not addresses",
            "Every system and world has a name, generated from your seed.",
            "A surveyed world has a drawn face where every channel is a real trait.",
            "Ten named regions per galaxy, each genuinely different.",
        ),
        release(
            "0.10.1", "2026-08-14", "Every hull costs the same",
            "A flat 800 metal and 200 crystal at every depth.",
            "What makes a fleet expensive is the yard, which builds one at a time.",
        ),
        release(
            "0.10.0", "2026-08-13", "Worlds run out",
            "Every world holds a finite vein, and a run takes from it.",
            "A world comes back at five percent a day.",
            "Prospecting: every hull pulls more out of every world.",
        ),
        release(
            "0.9.0", "2026-08-13", "Ships take time to build",
            "A hull goes on the slipway with a countdown, and orders queue.",
            "Ships cost ten times what they did.",
            "You are told when a hull leaves the yard.",
        ),
        release(
            "0.8.0", "2026-08-12", "You can buy ships",
            "The Shipyard is a price list, and the Fleets tab shows what is away.",
            "No tab says nothing here yet any more.",
        ),
        release(
            "0.7.2", "2026-08-12", "Dangerous worlds pay more",
            "A run brings home three times what it used to.",
            "Danger adds a third to the hold where it used to take a tenth.",
        ),
        release(
            "0.7.1", "2026-08-12", "The dispatch sheet is a real sheet",
            "It covers the window, the handle drags, and a scroll on it is its own.",
        ),
        release(
            "0.7.0", "2026-08-12", "You can send a ship somewhere",
            "Pick the resource, the hulls and how long until they are home.",
            "A world you cannot live on is still worth going to.",
            "A run is free: the hull was the price.",
        ),
        release(
            "0.6.0", "2026-08-11", "Every row says what it is worth",
            "One line per row: what the level hands you, and when you have it back.",
            "Tap a row to open the arithmetic behind the verdict.",
            "Nothing about the balance moves.",
        ),
        release(
            "0.5.2", "2026-08-11", "The Nanite Factory does something",
            "It is the only thing that shortens a deep build.",
            "Past level 18 every further level costs more waiting than it earns.",
            "The first fortnight is untouched to the minute.",
        ),
        release(
            "0.5.1", "2026-08-11", "Your neighbours are worth a look",
            "A new colony starts beside somewhere it could almost stand.",
            "The three adaptation ladders open at Robotics 2 instead of 4.",
        ),
        release(
            "0.5.0", "2026-08-10", "Every alert is one you asked for",
            "Tap a bell and the game tells you the moment that lands, or is affordable.",
            "Several finishing together arrive as one alert.",
        ),
        release(
            "0.4.4", "2026-08-10", "The sky leans the right way round",
            "Drop the right edge and the stars now go the way you tip it.",
        ),
        release(
            "0.4.3", "2026-08-10", "A sideways lean answers as readily",
            "Rolling now travels as far as tipping, from every pose a hand rests in.",
            "There is no longer an edge to the effect.",
            "Put the phone down and the sky stops.",
        ),
        release(
            "0.4.2", "2026-08-10", "The sky behind the game leans",
            "Three planes of stars slide against each other as you tilt the phone.",
            "Off entirely if you have asked your phone for less movement.",
        ),
        release(
            "0.4.1", "2026-08-10", "The fleet was measured",
            "A gathering run brings back half what it used to.",
            "Nothing you can see or do changes in this build.",
        ),
        release(
            "0.4.0", "2026-08-10", "The colony floats over a sky",
            "A hundred and one stars, and none of them move on their own.",
            "A running row wears a dial instead of a bar.",
            "The app tells you what happened while you were away.",
        ),
        release(
            "0.3.0", "2026-08-10", "A fleet, under the game",
            "Ships can work a surveyed world and bring cargo back.",
            "You can work a world you could never live on.",
            "No screen offers it yet.",
        ),
        release(
            "0.2.7", "2026-08-09", "The first hour is a different game",
            "Opening upgrades cost a tenth, and the first taps land in two minutes.",
            "The discount runs out where the galaxy opens.",
        ),
        release(
            "0.2.6", "2026-08-09", "The debug menu asks to be held",
            "Skipping and deleting take a hold rather than a stray tap.",
            "The panel is a proper bottom sheet.",
        ),
        release(
            "0.2.5", "2026-08-09", "A debug menu",
            "Shake the phone to skip the colony forward or start it again.",
            "It ships to everyone, so it works on TestFlight.",
        ),
        release(
            "0.2.4", "2026-08-09", "Nothing can quietly overflow",
            "Every cost, duration and stock refuses to come back negative.",
            "A colony left for years no longer breaks on the way back.",
            "The adaptation ladders join the opening discount.",
        ),
        release(
            "0.2.3", "2026-08-09", "The whole opening is discounted",
            "Everything in the first days costs a third of full price at level one.",
            "It runs out where the galaxy opens.",
            "The Research tab opens on day one instead of day two.",
        ),
        release(
            "0.2.2", "2026-08-09", "Upgrades stop outgrowing your income",
            "A build now takes about as long as earning it does, at every depth.",
            "Skipping the Robotics Factory no longer costs you two days.",
        ),
        release(
            "0.2.1", "2026-08-09", "Oltre runs on Android",
            "The same colony, research and galaxy, on Android 8.0 or newer.",
            "Every version is downloadable, with the APK on the release page.",
        ),
        release(
            "0.2.0", "2026-08-09", "You can send a probe",
            "A footer under the orbit map: what it costs and how long the flight takes.",
            "The ± buttons are gone: a band shows all 250 systems at once.",
            "Every build now takes as long as it costs.",
        ),
        release(
            "0.1.2", "2026-08-09", "The galaxy can be explored",
            "A probe flies for hours and every world around that star is surveyed.",
            "No screen offers the dispatch yet.",
        ),
        release(
            "0.1.1", "2026-08-08", "Crystal accrues half again as fast",
            "30/h becomes 36/h at level 1, and every level rises in step.",
            "The Crystal Mine stops being the worst buy on the screen.",
        ),
        release(
            "0.1.0", "2026-08-08", "The rail says it in one line less",
            "Each resource carries its colour as an orb, with the rate beside the stock.",
            "Six identical rectangles become a foreground and a background.",
            "There are stars behind the game.",
        ),
        release(
            "0.0.18", "2026-08-08", "You can buy an adaptation ladder",
            "Thermal, Gravitic and Atmospheric are on sale under the technologies.",
            "Tap the technology on a blocked world to go and buy it.",
            "A ladder shows the band it widens.",
        ),
        release(
            "0.0.17", "2026-08-07", "Three ladders, under the game",
            "Each level widens the tolerance band on its own axis.",
            "No screen sells them yet.",
        ),
        release(
            "0.0.16", "2026-08-07", "A blocked world says what it is worth",
            "The yield sits beside the verdict, so a world you cannot reach is priced.",
            "Each row counts the bands it fails.",
        ),
        release(
            "0.0.15", "2026-08-07", "The galaxy exists",
            "Four galaxies of 250 systems, all from one number saved with your colony.",
            "An easy world is a poor world.",
            "A world you cannot settle says what would change that.",
        ),
        release(
            "0.0.14", "2026-08-07", "Written once instead of twice",
            "Nothing changed for the player: every screen draws what it drew before.",
            "Two copies of what a price looks like are two things that can drift.",
        ),
        release(
            "0.0.13", "2026-08-06", "Research is playable",
            "Three technologies, one project at a time, and a different answer each week.",
            "Your mines tell you when they are running at half power.",
            "Your stocks now follow you across the app.",
        ),
        release(
            "0.0.11", "2026-08-06", "The game has five destinations",
            "Colony, Research, Shipyard, Galaxy and Fleets, from the first launch.",
            "The four that are not built say what will be there.",
        ),
        release(
            "0.0.10", "2026-08-06", "The game tells you to come back",
            "Every build and every fleet books an alert at the moment it lands.",
            "The version on TestFlight is the version in the repo.",
        ),
        release(
            "0.0.9", "2026-08-06", "A real iPad app",
            "It fills the screen, resizes, and works in Split View and Stage Manager.",
            "Past a phone's width the content caps and centres.",
        ),
        release(
            "0.0.8", "2026-08-06", "Upgrades run in parallel",
            "Every facility builds on its own, with its own countdown and bar.",
            "The economy was rescaled to numbers you can hold in your head.",
            "Colonies from 0.0.7 start over.",
        ),
        release(
            "0.0.7", "2026-08-06", "Your colony survives the app closing",
            "Levels, stocks, builds and fleets come back, and the hours are credited.",
            "A corrupted save starts a fresh colony instead of crashing.",
        ),
        release(
            "0.0.6", "2026-08-06", "Returning fleets are visible",
            "An amber strip carries where it is from and a live countdown.",
            "Cargo joins your stores when it lands.",
        ),
        release(
            "0.0.5", "2026-08-06", "The Colony screen is playable",
            "Every facility lists its level, its cost and how long it takes.",
            "The resource you are short of turns red.",
        ),
        release(
            "0.0.4", "2026-08-06", "Oltre has a face",
            "A planet's lit limb with one trajectory rising past it.",
        ),
        release(
            "0.0.3", "2026-08-05", "The economy is real",
            "Six buildings, a build queue, and energy that mines are throttled by.",
            "Storage caps, and the sim harness runs a week in milliseconds.",
        ),
        release(
            "0.0.2", "2026-08-05", "Metal accrues in real time",
            "The first vertical slice through every layer of the game.",
        ),
        release(
            "0.0.1", "2026-08-05", "The first commit",
            "A monorepo, CI, and a branch that has to be reviewed.",
        ),
    )
}
