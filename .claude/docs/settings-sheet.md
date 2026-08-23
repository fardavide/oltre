# Settings decision sheet — 0.18

Opened by the build, 2026-08-23, for Davide's call: *"I want to add settings screen, for now it would
include only notifications"*, with two settings named in his own words and one of them explicitly
unnamed (*"Not sure how to call it"*).

**This is the slice that gives the 0.16 gear something behind it.** `PlayerStrip` has shipped a
settings button since 0.16.0 whose whole behaviour is a notice reading *"Coming soon"* — permitted
then, over the design system's own objection, precisely because it *says* it is not built. It is
built now, and the notice goes with it.

**The design half is a Claude Design round trip**, per [`../rules/session-roles.md`](../rules/session-roles.md)
— the exception 0.16 ran under was withdrawn on 2026-08-23. The prompt was delivered in-session and
is deliberately not kept in the repo (Davide, 2026-08-23): it is a delivery rather than a record, and
what has to survive it is the argument, which is this file. **§7 is the prompt's whole subject** —
anything not in it was settled here rather than asked. Everything in §1–§6 is buildable without a
frame.

---

## 1. What is settled, and by whom

| | Settled | By |
|---|---|---|
| A settings screen exists, opened by the strip's gear | yes | Davide, 2026-08-23 |
| It holds notifications and nothing else, this slice | yes | Davide, 2026-08-23 |
| Setting one: two ways of choosing what you hear about | yes | Davide, 2026-08-23 |
| Setting two: three ways of packaging what you hear | yes | Davide, 2026-08-23 |
| **In by-category mode the per-row bells are not drawn at all** | yes | Davide, 2026-08-23 |
| **The watch square keeps its price half in every mode** | yes | Davide, 2026-08-23 |
| **Grouped and summary have no window: one alert, at the last event's instant** | yes | Davide, 2026-08-23 |
| Seven categories, one per kind of news | yes | Davide, 2026-08-23 |
| What setting one is *called*, and what its two options are called | **no** | Claude Design, §7 |
| Everything drawn — the screen's form, the switches, the expanding section | **no** | Claude Design, §7 |
| Every string this slice adds | **no** | placeholder, as every notification string already is |

---

## 2. The two settings, in the shape the model holds them

### 2.1 Scope — *what you are told about*

Two values. Davide's words for them are **ad-hoc** and **custom**; both are words about how the
feature works rather than about what the player gets, and he has handed the naming to Claude Design
(§7). The code carries `NotificationScope.AD_HOC` / `BY_CATEGORY` and the UI carries a provisional
*Per item* / *By category* until the frame comes back.

- **`AD_HOC`** — exactly what ships today, unchanged in every particular. Nothing is announced that
  was not asked for on the row it is about: a facility or project's watch square, the Shipyard hull
  card's three-state bell, the bell beside Dispatch. This is the default and it is what every
  existing colony is already in.
- **`BY_CATEGORY`** — the seven switches in §3. A category that is on announces *every* job of that
  kind; a category that is off announces none.

**The per-job asks are not deleted when the mode changes, they are ignored.** `subscribed`,
`hullAlerts` and `announceFlights` stay exactly as they were in `GameState`, and switching back
restores every one of them. That is the cheap half of a decision the alternative makes expensive: a
mode switch that emptied the set would be a destructive action behind a two-way control.

### 2.2 Grouping — *how what you are told is packaged*

Three values, and they apply in **both** scopes — the two settings are independent, which is what
makes six combinations rather than five.

- **`SINGLE`** — one alert per piece of news, exactly as today, including today's two collapses: the
  five-minute completion chain, and the hull card's *when all done* order.
- **`GROUPED`** — **one alert per category, for everything pending in it**, fired at the instant the
  **last** of them lands.
- **`SUMMARY`** — **one alert for everything**, fired at the instant the last pending event of any
  kind lands.

### 2.3 The window question, asked and answered against the recommendation

Grouped and summary have to collapse alerts that land at different instants, and there were three
answers: reuse today's five-minute chain, use a longer one, or use none at all. **Davide took none**
(2026-08-23), with the cost stated in front of him.

The cost, written down because it is the thing the first install will find: **a mine finishing in ten
minutes is not announced until the research finishing in six hours is.** Grouped and summary do not
merely reduce how many alerts arrive, they delay the early ones to the instant of the latest — and
the notification is the only thing that brings a player back to a game whose whole loop is the
check-in. A player in summary mode with one long job in flight books exactly one pending alert.

Two things make it less alarming than that paragraph reads, and neither of them makes it wrong:

- The set is re-derived from scratch on **every** discrete transition and on every foreground, so the
  moment anything changes, the collapsed alert is rebooked at the new last instant. It is never
  stale, only late.
- It is trivially reversible: the whole of the window is one `Duration` and one `chainedWithin` call
  already in `GameNotifications.kt`. If an install says summary mode is too quiet, a window goes back
  in without touching the model.

**Recorded as Davide's call, not the build's recommendation** — the build recommended the five-minute
chain. `.claude/docs/balance-log.md` is where a round that changes it belongs.

---

## 3. The seven categories

One per kind of news the game can currently deliver, mapping one-to-one onto the `FutureEvent`
members `core` already predicts. That is the property worth having: **nothing the game can say is
ungovernable, and a new kind of news cannot ship without a switch**, because `FutureEvent.category()`
is an exhaustive `when` that will not compile without one.

| Category | The events | Today's ad-hoc control |
|---|---|---|
| Facilities | `BuildCompletes` | the Colony row's watch square |
| Research | `ResearchCompletes` | the Research row's watch square |
| Adaptations | `AdaptationCompletes` | the ladder row's watch square |
| Hulls | `ShipsComplete` | the hull card's three-state bell |
| Probes | `SurveyLands` | the bell beside Dispatch |
| Fleet returns | `FleetReturns` | the bell beside Dispatch |
| Price reached | `AffordableAt` | the watch square on a stalled row |

Research and adaptations are separate switches although they read alike, for the reason they are
separate slots and separate events: Davide split the queues at 0.12.2 precisely because the two are
not the same decision, and a settings screen that re-merged them would be undoing that in the one
place a player looks to say what they care about.

**All seven start on.** The first switch into by-category mode is a working state rather than
silence, which is the difference between a mode and a mode that looks broken.

---

## 4. What by-category mode does to the screens that have bells

Davide's call: **the categories replace the per-item controls.** In `BY_CATEGORY` a running row does
not draw a square, a hull card does not draw its bell, and the dispatch sheet does not draw one
either. There is no disabled state anywhere in this app, and a control whose answer is now given
somewhere else is a control with nothing to do.

**This costs almost nothing to build, and that is not luck.** `WatchUiState` is already nullable and
already documents null as *the absence of a control* — an affordable row, a locked row and a row with
no net income all have no square today. So the five presentation modules emit `null` into a state the
UI has always had to draw, and no row layout changes.

### 4.1 The one thing categories cannot express, and how it survives

The watch square is one control with two meanings — on a **running** row it subscribes to the
completion, on a **stalled** row it points the game's single affordability watch at that row. A
category can replace the first. Nothing can replace the second, because *Price reached* has to be
told **which row**, and "tell me whenever I can afford anything" is not a setting, it is every row in
the game firing constantly.

So, Davide's call: **the square keeps its price half in every mode.**

| Row, in `BY_CATEGORY` | Square |
|---|---|
| running | **none** — its category answers now |
| stalled, *Price reached* on | the square, exactly as today |
| stalled, *Price reached* off | **none** — it would book an alert that is gated off |

The third line is the one that keeps the rule this repo does not bend: a switch that is off must not
leave a control that looks operable and books nothing.

---

## 5. Where the settings live, and why not in the save

**In the preferences file, not in `GameState`.** `Preferences` says of itself that it holds
"everything the app remembers that is not the colony", and this is that: which alerts a *device*
raises is not a rule of the simulation, does not have to travel to a server, and must not make every
colony on disk migrate for a field `advance` never reads. Adding it to `GameState` would cost a
schema hop for nothing.

The field is nullable — `notifications: NotificationSettings?` — for `galaxyLanding`'s exact reason
and one more. `Preferences` carries no schema version and never migrates, so a *required* field added
today makes every preferences file already on disk fail to decode, and `PreferencesStore.load`
answers `Preferences.NONE` to a failure: an existing player would silently lose their galaxy landing
to a settings screen. Null means "never chosen" and resolves to the default.

**The types themselves are `core`'s.** Not the client's, and this is the one arrangement in which no
new cross-feature dependency appears anywhere in the graph:

- `:client:save:data` already depends on `core`, so `Preferences` holds the real type rather than the
  `String?` dance `galaxyLanding` has to do for an enum that lives in a presentation module.
- `:client:notifications:data` already depends on `core`, so the gate reads the settings directly.
- `:client:settings:presentation` will depend on `core` like every feature does.

And it is where the vocabulary belongs on the merits, not only on the graph. `core` already holds
`HullAlert`, which is an enum about *which of two ways the player wants to be told about hulls*, and
`FutureEvent.Completion.target()`, which exists in `core` for the stated reason that it is the same
correspondence read in the other direction. `NotificationCategory` is that argument again: it is a
partition of `core`'s own event hierarchy, and a copy of it in the client is a copy that can drift.

What does **not** move into `core`: the copy, the ordering on screen, and the labels. Those are
`:client:design:text` and the frame.

---

## 6. What the alerts say

Placeholder copy, like every notification string in this game — what a notification says is
player-facing content and therefore Davide's call. The *rules* below are the design.

### 6.1 The compaction ladder

Davide's instruction: *"if only Metal Mine is completed, for example, we show 'Metal Mine upgraded to
lv x', the more info we need to show, the more we compact to fit everything, otherwise we show more
details."* So it is a ladder rather than a mode, and each rung is decided by how much there is to
say rather than by which setting is on:

| What is pending | What fires |
|---|---|
| one event | **the event's own alert, verbatim** — "Metal Mine reached level 4" |
| several, all one category | **the category's count and the full list** — "Three upgrades are done" / "Metal Mine, Solar Plant and Extraction — …" |
| several, two or three categories | **a clause per category, with its verb** — "3 fleets are home and 2 upgrades are done" |
| several, four or more categories | **a clause per category, bare** — "3 fleets, 2 upgrades, 1 probe, 4 hulls" |

The first rung is a rule this file already keeps twice — the upgrade group and the hull order both
fall back to the singleton at a count of one, on the argument that *a count is only worth saying when
there is more than one thing to count*. The fourth rung is the only new idea, and the threshold
between it and the third is the softest number in this sheet: three categories is a guess at what a
lock screen holds, and it is Design's to move.

`Strings.listed` already writes the join — commas between, "and" before the last, no Oxford comma,
both separators in the catalogue so a locale can change them.

### 6.2 Identity, which is the part that has to be right

Every alert this game books has an id derived from its subject, because that is what makes
`replaceAll` idempotent — the same colony always produces the same alerts. The two new shapes keep
it:

- **grouped** — `"category-<CATEGORY>"`. A category is fixed vocabulary and there is at most one of
  these per category, so it is unique by construction and stable across every sync. Note this is
  *stronger* than the existing upgrade group's id, which has to fall back on its instant because its
  subject is a set that changes when one more row is subscribed. A category's membership can change
  the same way; its name cannot.
- **summary** — `"summary"`. There is one.

### 6.3 What the platform ceiling does now

Nothing, in two of the three settings. `IOS_PENDING_REQUEST_LIMIT` is 64 and the trim order that
protects the bounded kinds exists for `SINGLE` alone: grouped books at most seven requests and
summary books at most one. The trim stays exactly as it is and is simply not reached — which is worth
stating, because a reader finding the partition and the three `take` calls should know they now
answer to one branch.

---

## 7. What the round trip is for

Everything above can be built and tested without a picture. What cannot:

1. **The screen's form.** A full-screen destination with a way back, or a sheet over the frame like
   the debug panel. The app has exactly one modal today and it is a debug affordance, so there is no
   precedent to follow — and the gear is in the top-right of a strip that frames every destination.
2. **The name of setting one, and of its two options.** Davide's own words are `ad-hoc` and `custom`;
   he has said he is not sure, and this is the call a frame settles better than a sentence.
3. **The expanding section.** Seven switches that appear when the second option is chosen — how they
   arrive, whether the screen scrolls, what the two options look like while one of them owns a panel.
4. **Three options for grouping, which is one control with three states** — the app has two-way
   controls and a three-state bell, and no radio group anywhere.
5. **What the screen says about the cost of grouping.** §2.3 is a real trade the player is making;
   whether a screen explains it, and in how many words, is a design decision with a rule against it
   (this app does not explain its own controls in prose).

---

## 8. Open, and nobody's yet

- **The mode switch is retroactive and silent.** Turning *Facilities* off does not withdraw the alert
  already sitting with the OS until the next sync — which happens on the next transition or
  foreground, so in practice within one action. Same window every other number in this game lives
  with, and it is written down here rather than fixed.
- **Nothing on the settings screen says whether the OS is even permitting notifications.** Android 13
  and iOS both gate on a runtime grant this app asks for elsewhere, and a settings screen full of
  switches that the system has muted is the one way this feature can look like it works and not. Out
  of scope for this slice, and it is the first thing the next one should take.
- **Grouping is global, not per category.** "Summary for probes, single for research" is not
  expressible. Nobody has asked for it.
