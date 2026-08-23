# Player strip decision sheet — 0.16, revised 0.17.1

> **§5 is superseded in two places by *A Name Above The Rail*, a Claude Design round trip accepted
> 2026-08-23 and shipped at 0.17.1.** The gauge is no longer a 72dp inline track — it is the strip's
> own 2dp bottom edge — and the notice is no longer printed on the bar, but a card above the tab bar
> for four seconds. Everything else in §5 shipped and still ships. The two reversals and what paid
> for them are in [`decisions.md`](decisions.md); §5 is kept as written, because a sheet retro-fitted
> to its successor is not a record of what was decided when.


Opened by the build, 2026-08-22, for Davide's call: *"I want to design and implement the following,
above resources: player icon, player name, current Lv, experience bar, settings button."* The
mechanics behind the numbers are explicitly deferred — *"we will plan and populate the rest in a
follow up task"* — so what this slice ships is the surface and nothing under it.

**The design half opened as a Claude Design round trip and closed without one.** §4 is the call as it
was about to be sent; §5 is what was drawn instead. Mid-session Davide said *"You have capability to
use Design directly now"* — so the same session read the design system, designed the strip, rendered
four compositions into the real chrome, looked at them, and implemented the synthesis.

**The rule that would have applied is still the rule.** A local session with no design and no way to
reach one commits, emits the prompt and waits — [`../rules/session-roles.md`](../rules/session-roles.md),
and Davide, 2026-08-21: *"Why did you open PR when you didn't have design ready??? This should never
happen!"* What changed here is not that the wait was skipped but that the round trip became a loop
inside one session. §4 was written before that and is kept as written, because the questions it names
are the questions §5 answers, and a call that is retro-fitted to its answer is not a record of
anything.

**What made it defensible is that nothing was decided on paper.** Four compositions were built,
rendered headlessly and read side by side; two of the decisions in §5 reversed themselves the moment
they were pictures rather than sentences. That is the same standard the round trip exists to enforce —
a frame is authoritative because somebody looked at it.

---

## 1. What is settled, and by whom

| | Settled | By |
|---|---|---|
| The strip exists, above the resource rail | yes | Davide, 2026-08-22 |
| It holds icon, name, level, experience bar, settings button | yes | Davide, 2026-08-22 |
| Level reads 0, experience reads 0 | yes | Davide, 2026-08-22 |
| The icon is invented rather than briefed | yes | Davide, 2026-08-22 — *"make one that fits the game well"* |
| The name is invented rather than briefed | yes | Davide, 2026-08-22 — *"choose a cool generic name"* |
| Tapping the settings button says **"Coming soon"** | yes | Davide, 2026-08-22, over the objection in §2 |
| Nothing is persisted: name, level and experience are client-side constants | yes | Davide, 2026-08-22 |
| Everything drawn — geometry, height, compaction, the notice's form | **no** | Claude Design, §4 |

---

## 2. The objection, raised and overruled

The design system's own content rules list **"Coming soon"** under *Never written*, beside "Under
construction" and "Oops", and its interaction rules say **"There is no disabled state"** — the app's
idiom for a thing that is not built is a flat declarative sentence in the room where it would be
(*"Every world a probe reaches lands here."*). Put to Davide on 2026-08-22 with the in-voice
alternative beside it; he took the literal reading. **So "Coming soon" is the copy**, and the
contradiction is resolved by amending the design system rather than by arguing with the ask — see
the last paragraph of §4.

Recorded because the rule is still written down in the Claude Design project and the next session
will read it and hesitate. It has been overruled once, on this string, deliberately.

Two smaller collisions come with the button and neither is a blocker:

- `App.kt`, `TranslationsFor.kt` and `TranslationsForTest.kt` each state as fact that there is *"no
  picker and no settings surface anywhere in the app"* (Davide, 2026-08-16, about the language).
  A settings button does not add a language picker, but it does falsify the sentence those three
  comments are written on. **The implementing PR amends all three**; the language call is untouched.
- `decisions.md` deleted the Galaxy header line with the reason *"a standing total would be the only
  header in Oltre stating empire state that is not about what is on screen"*. An identity strip is
  exactly that, and is now sanctioned to be. The earlier deletion stands on its own facts — that
  header was a number about a screen, this is a frame around every screen.

---

## 3. Why nothing goes into `core`

Nothing awards experience and nothing renames the player, so a stored field could only ever hold the
value it was migrated in with. Against that, the cost is a schema hop: `GameSave` pins its on-disk
shape byte-for-byte in a test, `ignoreUnknownKeys` is false, and a hop that is skipped makes every
older save refuse to decode. **Paying a migration for a constant is the wrong trade**, and the
migration it would force is one with no honest answer — an existing colony's experience is neither
zero (which confiscates a fortnight of play) nor a number invented at the keyboard.

So: the name is one catalogue entry, the level and the experience are `0`, and all three live in
`:client:player:ui`. When the numbers become real they should be **derived** — a fold over the event
log, which already records every completed build, project, ladder, survey and run — and that is the
follow-up task's call to make, not this one's.

> **Closed at 0.17, and this section was right about the wrong half.** Davide, 2026-08-22: *"make it
> so next time I start the game it gives me experience for everything I did before."*
>
> The objection above — that a migration has no honest answer for an existing colony's experience —
> was true only while there was no fold to answer it with. 0.17 wrote one, and the 15 → 16 hop uses
> it: an existing colony is credited with exactly what its own event log is worth, which is neither
> zero nor a number invented at the keyboard. **So the field this section refused now exists**, and
> `SCHEMA_VERSION` moved after all.
>
> What did not survive is the sentence *"when the numbers become real they should be derived"*. They
> were, for one draft, and Davide overruled it on the day: *"the more the player progresses, the more
> it will be intensive to infer the level."* Derivation is the migration's job and the test's; the
> game reads a stored total.
>
> The name is untouched and is still a constant. The mapper is in `:client:player:presentation`, the
> module this feature's build file said it would grow on the day there was something to map. See
> [`experience-sheet.md`](experience-sheet.md).

**The name was going to be a function of the galaxy seed and is not**, which is the one thing in this
sheet that §5 overturned rather than filled in. Seeding it would have given every colony its own
callsign for free, off a value already on disk. What it would also have done is assert an identity the
save cannot back: nobody chose the seed, there is no level, no history and no rename behind it, so a
name derived from it is a fact about a random number wearing a person's clothes. The same argument
retired the seeded *mark*, and it is worth stating once for both — **the day identity earns variation
it should be drawn for it, not derived from a number nobody picked.** The cost of getting this wrong
was not abstract either: a per-seed name is a generator, a property test and a screenshot baseline per
variant, bought for a slice whose level is zero.

A player-*chosen* name genuinely needs `core`, and is out of scope here. When it lands it is a
schema hop, a non-null-checked `CommanderName` value class, and a `null` default written explicitly
rather than leaned on.

---

## 4. The call — what Claude Design is being asked

Sent 2026-08-22. The frame is authoritative about all of it; none of it may be invented at the
keyboard.

1. **The height, in dp.** Every dp the strip takes comes off the destination below it. A phone leaves
   about 650dp after the rail, the tab bar and two safe-area insets, and 0.12.0 shipped a galaxy map
   whose only control was off the bottom of the screen from exactly this arithmetic — with the suite
   green, because the suite's own `DESTINATION_HEIGHT` was a hand-derived constant that nobody moved.
2. **One surface or two** — does the strip share the rail's band under a single hairline, or carry
   its own edge above the rail's? Opaque either way: the starfield runs under every destination and
   an alpha fill would put stars inside chrome.
3. **320dp.** The rail already stacks below 360dp. Does the strip compact, and what gives — the name,
   the bar's label, the level's position? Italian is longer than English at every width.
4. **The mark.** Drawn, almost certainly: every icon in this app is a `Canvas` path because a bitmap
   rasterises differently on the recording machine and the verifying one. If it varies with the save's
   seed, the frame says what varies and what does not.
5. **The experience bar's form** — the 3dp track the run cards use, a ring around the mark like
   `LevelDial`, or something new with its own geometry. And whether it fills once on entry.
6. **The settings affordance.** A gear in a `PressableFace` claiming 44dp over a smaller face is the
   app's shape for a text-less control; the frame gives the paths, the size and the tint. There is no
   `enabled` parameter anywhere in this design system, so the button is drawn live and the tap
   answers.
7. **The notice.** No snackbar, toast, banner or host exists in this app, and there is no `Scaffold`
   to hang one on. The frame chooses its form, its position, and how long it stays.
8. **Register** — is the name a row name at 13.5sp, or rail-scale? Is the level a `LV 0` badge or
   bare digits?

And one housekeeping item, because the project is the source of truth for its own rules: **strike
"Coming soon" from the *Never written* list** in the design system's readme and note the date and the
call, so the next reader finds a rule that matches the app. **Done, 2026-08-22** — the rule now reads
that it came off the list by Davide's call, that the settings button is the only sanctioned use, and
that the idiom it replaced still governs everything else. `guidelines/player-strip.card.html` went up
beside it.

---

## 5. The design, settled

Drawn 2026-08-22 in the session, against the design system's own tokens, and looked at rather than
reasoned about: four compositions were built into the real chrome — the shipped rail, destination and
tab bar — rendered, and read side by side. What follows is the synthesis, and the numbers are the
frame.

**One row, 38dp.** Padding 9dp top and bottom, matching the rail's own, so the two tiers rhyme rather
than merely stack. Content is a 20dp mark, the name, a `LV 0` badge, a 72dp gauge and the gear, all on
one baseline row. 38dp of a ~650dp destination is 5.8%, and it takes the top chrome from ~55dp to
~93dp — the honest price of the ask, and the reason nothing here is two lines.

**Its own surface, with the rail's own edge repeated.** Same `OltreColors.surface`, a 1dp `white 9%`
hairline between strip and rail, and the rail keeps its own below. Two tiers of one material: the
player and the stockpile are different subjects and the same kind of thing. Opaque throughout — the
starfield runs under every destination and an alpha fill would put stars inside chrome.

**The mark is one drawing, the same for every save.** A world, and a trajectory that has already left
it, and the one filled dot the icon rules permit for where it got to — the app icon's own idea at
glyph scale. 24-unit viewBox, stroke 1.6, accent, 20dp. **The trajectory does not touch the limb**,
and that gap is load-bearing rather than decorative: a stroke that meets the circle turns the whole
mark into a magnifier, which was visible the moment it was rendered and invisible while it was being
described.

**It is deliberately not seeded.** A mark that varied with the galaxy seed would assert an identity
the save cannot yet back, and would buy a generator, a property test and a baseline per variant for a
slice whose level is zero. The day identity earns variation it should be drawn for it.

**The gauge is 72dp × 3dp**, `white 9%` track, 2dp radius, accent fill, right of the badge. Borrowed
measurements, not the component: `ProgressBar` bakes in `fillMaxWidth()` and a 10dp top padding and
takes no width, so calling it here would be a fork. Borrowed knowingly — a track that visibly *ends*
is a scale, where one running to the edge of a card is a timer, and this is the same instrument
reading a standing quantity rather than a job.

**At zero it is an empty track and nothing else is drawn.** The one-shot 900ms fill is wired exactly
as everything else in the app wires it, and at experience 0 its target is 0 — so the animation has
zero amplitude and the strip is motionless on every launch that ships. The first frame that ever moves
here is the first frame after something awards experience.

**The gear's target is 38 × 38dp, not 44.** `WatchSquare` settled this already and in as many words:
*"a child placed outside its parent's bounds does not reliably receive touch, which is why Material's
own `minimumInteractiveComponentSize` expands the layout rather than overflowing it"* — so a 44dp
claim inside a 38dp band either fails to receive the tap or grows the band to 44dp. The same file's
own remedy applies: claim the axis you can afford and say so. 38dp is larger than the 29dp square that
already ships stacked.

**The notice displaces rather than overlays.** Tapping the gear replaces the badge and the gauge —
the two things that are not real yet — with `Coming soon` at 10.5sp `textSecondary`, in the slot
immediately left of the gear that raised it, for 2,000ms. No pill, no border, no new surface, no
scrim: the app has no snackbar and this does not become the first one. It is the arrival roll's shape
— state with an explicit clearing rule — rather than a component with a duration.

**At 320dp** the name ellipsises and the gauge shortens to 48dp; the height does not move. Measured:
11 + 20 + 7 + name + 7 + 34 + 7 + 48 + 38 = 172dp of furniture, leaving 148dp for a name that needs
113dp. `Prossimamente` at 10.5sp is 82dp against the 89dp cluster it replaces, so the notice fits at
the narrow width in both languages.

**The name is `Dead Reckoning`**, 13.5sp SemiBold `text`. It is a navigation term — a position
computed from a known start, an elapsed time and a speed — which is precisely and only what this
game's simulation does on foreground. Deliberately not the rail's 15sp SemiBold, so the strip does not
read as a fourth statistic. Alternates offered and not taken: `Cold Start`, `Long Silence`,
`Last Bearing`, `Slow Light`.

**The level is `LevelBadge` unchanged** — `Strings.levelBadge(0)`, 10sp on `white 9%` at 4dp radius.

### What this costs elsewhere

`DESTINATION_HEIGHT` in `GalaxyRobot.kt` is a hand-derived constant, currently 650, and the strip
moves it to 612. Every galaxy frame is captured at it, so those baselines are re-recorded as part of
this slice rather than left describing a device that does not exist — which is the exact failure that
shipped 0.12.0 with an unreachable map control while the suite stayed green.

The frames are in the Claude Design project; per [`design/README.md`](design/README.md) the default is
not to copy them down, and this section is the durable half.
