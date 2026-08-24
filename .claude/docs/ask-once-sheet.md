# Ask once — the first settings screen (0.18)

The Claude Design sheet *Ask Once*, accepted 2026-08-23, plus the three calls Davide took on it in
the same session. This is the prose record of a design round trip; the frames live in the Design
project, and what a reader needs from them is here because a citation in a code comment has to point
at something that can be opened.

The gear had answered `Coming soon` since 0.16. This is what it opens.

## The two settings

**Alerts** moves the question *tell me when this lands* up one level — from the job to the kind of
job.

| Stop | What it means |
|---|---|
| `Per item` | Every alert is asked for on the row it is about. Rows carry the square. |
| `By category` | Everything of a kind announces itself. Rows stop carrying the square. |

Under `By category` a panel of seven switches decides what the colony announces — **Facilities,
Research, Adaptations, Hulls, Probes, Fleet returns, Price reached**, in that order. `Price reached`
is last because it is the odd one in three ways, and it is the only row that carries a second line.

**Delivery** says how many notifications the answers arrive in: `One each`, `One per category`,
`One in total`.

## §1 — why a sheet

Two premises the design started with did not survive its own research, and both are recorded because
a session reading only the code would make the same two arguments again:

- **The debug panel is not the app's only modal.** The row sheet and the dispatch sheet are both
  modal bottom sheets and both shipped. So a settings sheet is not a new surface and needs no
  argument; it needs to *match* those two, down to the 32×3 handle.
- **There is already a multi-way control.** The dispatch sheet's `Home in` ladder has four stops. A
  three-way is that with one rung removed and a two-way is that with two, so neither control here is
  new vocabulary and the screen has one control idiom rather than two.

Weighed and not taken: **a full-screen destination**, which costs a navigation stack this app does
not have — the only place a back control fits is the gear's own 38dp square, so the control you
pressed becomes the control you press to undo it. And **a sixth tab**, which takes 320dp down to 53dp
a tab and puts a preferences screen in the same rank as the galaxy.

There is no Save, no Done and no X. Every control commits on tap, like every other control in the
app, so the ways out are the handle, the frame above, the gear again, and the platform's own back.

## §2 — the words

`Per item` / `By category`: two prepositional phrases of the same shape, eleven characters each.
Rejected: `ad-hoc` and `custom`, which describe the code; `manual` and `automatic`, which promise
that the game decides something, which it does not.

`Delivery` rather than `Grouping`: grouping names the mechanism, and only two of the three stops
group anything. Delivery is the question the player actually has — *how do these arrive*.

The three stops carry no prose. Each shows the string the phone would actually print, which is
shorter than any sentence explaining it, and the line under it says **when**, because with no time
window that is the part nobody can guess.

## §3 — what happens to the rows

Three calls, and the second is the interesting one.

**Call 1 — a missing square is a state these rows already have.** The by-category building row is the
row the design system already ships. Nothing is drawn; the control is absent, which is what a missing
square has meant on these rows since the watch slice.

**Call 2 — the price watch survives.** Every other category is a *kind of thing that happens*; a
price watch is a row the player had to point at, and *tell me when I can afford this one* has to be
told which one. So the switch decides whether the watch exists at all, and it is stated on the row
that governs it: `The square stays on rows you cannot afford.` becomes `Nothing watches for a price.`
when it is switched off — because off removes the watch rather than muting it.

**Call 3 — the hull card loses its third state.** That control has three — off, each hull, whole
order — and one category switch cannot carry three, so under `By category` the yard announces every
hull. It is not lost: `One per category` is *when the whole order is done*, exactly, for hulls and
for everything else. So the third state became a global preference rather than a per-order one, which
makes Delivery load-bearing for something the shipyard used to own.

### The property worth having, and why there are exactly seven

The categories map one-to-one onto the `FutureEvent` members `core` already predicts — `BuildCompletes`,
`ResearchCompletes`, `AdaptationCompletes`, `ShipsComplete`, `SurveyLands`, `FleetReturns`,
`AffordableAt`. That is not tidiness, it is the guarantee: **nothing the game can say is ungovernable,
and a new kind of news cannot ship without a switch**, because `FutureEvent.alertCategory` is an
exhaustive `when` that will not compile without one.

Research and adaptations stay separate although they read alike, because they are separate slots and
separate events. Davide split those queues at 0.12.2 precisely because the two are not the same
decision, and a settings screen that re-merged them would undo that in the one place a player looks to
say what they care about.

**And the per-row asks are not deleted when the mode changes, they are ignored.** `subscribed`,
`hullAlerts` and `announceFlights` stay exactly as they were in `GameState`, and switching back
restores every one of them. The alternative is a mode switch that empties a set — a destructive action
behind a two-way control, with no undo and nothing on screen to say it happened.

## §4 — what the phone says

Four levels, and the threshold is a rule rather than a guess.

| Level | Title | Body |
|---|---|---|
| One thing | `Metal Mine reached level 4` | the singleton alert's own body |
| Several, one kind | `3 facilities are done` | the kinds involved |
| Several, several kinds | `3 fleets · 2 facilities` | per-kind names, joined by the middot |
| More than the title holds | `3 fleets · 2 facilities · +2` | as above |

Past one category the design drops the verbs and joins counts with the middot the app already owns as
a separator. The reason is translation rather than brevity: **a verb costs a plural agreement and
*and* costs a conjunction, and those are the two places English and Italian disagree in every string
you write.** Levels three and four need no agreement, no article and no word order, so they are the
same length in both tables. Level two is the only one that needs per-count forms, and it is worth
knowing that it is the only one.

`Research` is the category and `project` is the count noun, because *2 researches* is not English.

**The `+n` rule is the design's and its number is not.** The sheet says *take categories until the
next one would pass 28 characters*, and calls 28 *"a measurement to take on a device"*. A character
budget cannot be spent on a `TextRes`: the title is not a string until `Translations` resolves it,
hours later and in whichever language the device is set to, so a rule measured in English would
silently compact a different set of categories in Italian. The code takes **two categories**, which
reproduces both of the sheet's own drawn examples. See `TITLE_CATEGORIES`.

## §6 — where a premise did not survive, and what Davide did about it

The sheet's own §6 measured `One in total` as drawn — one alert, held until the last pending thing
lands — against the reference colony, and reported the arithmetic: a mine finishing at 12:04 is not
announced until Ion Drive lands at **17:42**. Five hours and thirty-eight minutes of silence in a
game played in five-minute check-ins. *"A player will not conclude grouping is working; they will
conclude notifications are broken."* It recommended a five-minute window, or a cap on the wait.

**Davide took neither and replaced the rule.** 2026-08-23:

> One in total ALWAYS keeps a single notification, upcoming notifications only update it; Metal Mine
> upgraded at 12:04, and notification shows only that, then one ship is ready at 12:37, and the
> notification updates to show Metal Mine + 1 ship; second ship ready at 13:09, notification updates
> to show Mine + 2 ships.

So it fires at **every** instant and each firing replaces the one before it. The failure §6 measured
cannot occur: the player is never told late, and the tray never holds more than one.

Two consequences he also settled:

- **`Price reached` folds into the running total**, overruling the sheet's *never grouped*. It is
  defensible under this stop and nowhere else — the news is already on the lock screen, so there is
  nothing to be late for. `One per category` keeps the exemption.
- **The five-minute chain is not one of the three stops.** It stays where it has been since 0.5.0,
  because it is a dedupe for things landing in the same breath rather than a delivery rule. That is
  also what makes `One each` byte-for-byte what 0.17 did, and the migration a true no-op.

## The defaults, and the hop

> use single notification only for new saves, previous ones keep the current behavior
> — Davide, 2026-08-23

A new colony opens on `By category · One in total`: everything announces itself, and it arrives as
one notification. The two halves pay for each other — seven categories on is the loudest this app can
be under any other delivery.

Schema 17 carries an existing save forward on `Per item · One each`, which is exactly what 0.17 did.
It is the first hop in the save table that could have made a colony *louder*: schemas 9, 14 and 15
each silenced something nobody had asked for, and silence is always the truthful answer to *what did
a player with no control decide?* — but seven categories switched on is not a thing anybody chose.

## What the round trip was for

The prompt that opened it is not in this repository and deliberately so (Davide, 2026-08-23): a
prompt is a *delivery*, and the durable half of a round trip is the argument it was built from, which
is this file. So this section is the record of what was actually asked of Claude Design — everything
else in this document was settled here or by Davide, without a frame.

1. **The screen's form** — a full destination with a way back, or a sheet over the frame. §1 is the
   answer and the reasoning it came back with.
2. **The name of setting one and of its two options.** Davide's own words were `ad-hoc` and `custom`
   and he said he was not sure; `Per item` / `By category` is the frame's answer. §2.
3. **The expanding panel** — seven switches that appear when the second option is chosen, how they
   arrive, and what the two options look like while one of them owns a panel.
4. **A three-state control**, which the app did not have: it has two-way controls and a three-state
   bell and no radio group anywhere. The answer was the dispatch sheet's `Home in` ladder with a rung
   removed, which is why this screen has one control idiom rather than two.
5. **Whether the screen explains the cost of grouping**, against a standing rule that this app does
   not explain its own controls in prose. It does not: each stop shows the string the phone would
   print, and the line under it says *when*.

## What implementation had to decide

Three things the frames could not answer, recorded here because the code cites this document:

**One notification is two ids.** Neither platform will hold two *pending* requests under one
identifier — the second silently replaces the first while it is waiting — so a booking's id and a
tray entry's id are separate fields. Android's tray id genuinely replaces what is showing. **iOS
cannot**: it runs nothing in the background, so nothing can retract a delivered notification, and the
closest available is a thread identifier that collapses the run into one stack in Notification
Centre. **On iPhone `One in total` is one stack, not one notification.** Platform limit, not a
choice; the delivery target is the platform that cannot do it.

**The gate belongs to `core`.** It lived in `:client:notifications:data` from 0.5.0 on the design's
own instruction, and that reasoning is untouched — `futureEvents` is the mirror of what `advance`
will write to the log, and a build completes whether or not anybody asked. `announcedEvents` is a
second list *derived* from it. What moved it is that there are two readers now: the scheduler books
the alerts, and the sheet says when the next one is due. A preferences screen promising a buzz the
scheduler will not send is the worst thing this screen could do, and one rule is the only way to stop
it. `AlertSettings.asksOnRow` is the same argument for the square, which four presentation modules
read.

**The sheet scrolls.** By the design's own arithmetic it fits at both widths — 573dp at 393, clearing
the resource rail by 94; and 648 of the 652 a Slide Over pane has — so this does nothing today. It is
the sheet's own *"either the sheet scrolls at 320 from the next slice on, or the panel does; worth
deciding now rather than when it overflows"*, and four dp of headroom does not survive a second
section.

## Open, and deliberately

- **The permission slice is next.** Nothing on this sheet reflects a muted system, so a player can
  switch all seven on and hear nothing. That is the one state this screen lies about, and the design
  names it as the follow-up.
- **What does a grouped alert open?** There are no deep links, so all four levels land on Colony —
  while levels three and four name things on three different screens.
- **Do Probes and Fleet returns group together?** They are one mechanism and two categories. If the
  code ever groups by flight, a summary will say *2 fleets* for one of each.
- **`1 price` is a count of a thing that did not finish**, which the sheet objects to by name. It is
  in the catalogue because `One in total` folds every kind in, and there can only ever be one. Copy
  is Davide's.
- **28 characters is still unmeasured.** See §4.
- **Delivery is global, not per category.** *"One in total for probes, one each for research"* is not
  expressible. Nobody has asked for it.
- **The mode switch is retroactive but not instant.** Turning a category off does not withdraw an
  alert already sitting with the OS until the next sync — which is the next transition or the next
  foreground, so in practice within one action. Same window every other number in this game lives
  with, and it is written down here rather than fixed.
- **`One in total` on iPhone waits for a push server, and that is Davide's call** (2026-08-24). He hit
  it on a device — a Hauler at 16:29 and a second at 16:45 arrived as two entries under one stack,
  which is precisely what *"What implementation had to decide"* predicted, read back as a bug. The
  levers that exist locally were weighed and two declined: **folding a wider window** under
  `TOTAL` (the 5-minute chain is `oneEach`'s only) would have merged those two and nothing three hours
  apart, and **rewording the sheet** would only stop the app claiming what it cannot do. The third was
  taken and shipped at 0.20.1 — **clearing what has already been delivered when the app opens**, which
  bounds the stack to one check-in's worth without touching the reason there is one. What he wants is
  `apns-collapse-id`, which genuinely replaces a
  *delivered* notification and has no local equivalent — iOS's one hook that runs with the app shut,
  `UNNotificationServiceExtension`, fires for remote pushes only. So the fix is a server, the server
  is on the way for multiplayer, and until then nothing here moves.

  **What 0.20.1 does and does not buy.** `NotificationScheduler.clearDelivered` is a second method
  rather than a step inside `replaceAll`, and that is a correctness rule: a sync runs on every discrete
  transition, and on Android the shell's tick loop outlives the foreground — so a clearing sync would
  take down the alarm the system had posted seconds earlier. Only *opening the app* clears, which is a
  fact about attention that no amount of game state implies. It is called from `App`'s launch effect,
  so it covers a cold launch and **not** a warm resume: the effect runs once per composition and
  returning from the background does not start a new one. On iPhone that is the smaller half, because
  iOS terminates backgrounded apps freely and a notification tapped after one is a cold launch — but
  it is a real gap, and closing it needs a foreground signal the shell does not have.

  Two things follow that a later session should not have to rediscover. The copy is **knowingly**
  wrong on the delivery target — `AlertNextAtTotal` says the alert *"is brought up to date rather than
  repeated"*, which is true on Android and false on iPhone — and it stays until the server makes it
  true rather than being softened first. And the stack itself is working: the report came from a group
  the player had **expanded**, and collapsed it shows the newest entry alone, so `threadIdentifier` is
  already buying most of what the stop promises.

## The test the next round should run

Hand somebody the build with `By category` already chosen and ask what will happen when their mine
finishes. If they answer without opening the sheet, the seven bells did their job. Then ask what
happens under `One in total` — if they say *I get one message* and not *I get it late*, the delivery
rule is right and the screen is not what to revisit.
