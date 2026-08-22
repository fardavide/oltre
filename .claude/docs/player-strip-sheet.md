# Player strip decision sheet — 0.16

Opened by the build, 2026-08-22, for Davide's call: *"I want to design and implement the following,
above resources: player icon, player name, current Lv, experience bar, settings button."* The
mechanics behind the numbers are explicitly deferred — *"we will plan and populate the rest in a
follow up task"* — so what this slice ships is the surface and nothing under it.

**The design half is a Claude Design round trip and it has not returned yet.** §4 is the call; §5 is
what comes back. Nothing in `client/` is written until it does, and no pull request opens on half of
it — [`../rules/session-roles.md`](../rules/session-roles.md), and Davide, 2026-08-21: *"Why did you
open PR when you didn't have design ready??? This should never happen!"*

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

So: the name is a pure function of the galaxy seed already on disk, chosen from a catalogue of
candidates through `Strings` so Italian gets its own list rather than a translation of English's;
level and experience are `0`. When they become real they should be **derived** — a fold over the
event log, which already records every completed build, project, ladder, survey and run — and that
is the follow-up task's call to make, not this one's.

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
call, so the next reader finds a rule that matches the app.

---

## 5. What came back

*Open.* Filled in when the design returns; the frames are archived per
[`design/README.md`](design/README.md)'s default, which is not to copy them down.
