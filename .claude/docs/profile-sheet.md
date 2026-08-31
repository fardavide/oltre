# Profile decision sheet — a name and a mark the player chooses

Opened by the build, 2026-08-30, on Davide's call: *"I want to allow users to set their name and
picture."*

It is the closing of a line `player-strip-sheet.md` §3 left open two years of releases ago and
0.21.0 changed the answer to. That sheet said a player-chosen name *"genuinely needs `core`, and is
out of scope here… a schema hop, a non-null-checked `CommanderName` value class, and a `null`
default written explicitly"*. **That prediction is now wrong in its most important half.** `core` is
the colony, and since #113 the colony is the server's; a name and a face are facts about an
*account*, which is a thing that did not exist when §3 was written. So there is no schema hop, no
`GameSave` migration, and nothing enters `core` at all — see §3 below.

---

## 1. What is settled, and by whom

| | Settled | By |
|---|---|---|
| A player can set a display name | yes | Davide, 2026-08-30 |
| The "picture" is **a drawn mark the player picks from a set**, never an uploaded photo | yes | Davide, 2026-08-30 |
| Both are held **server-side, on the `players` row** — not in the colony snapshot, not on the device | yes | Davide, 2026-08-30 |
| The editor is opened by **tapping the mark or the name in the player strip** | yes | Davide, 2026-08-30 |
| The name is **free text and not unique** — two commanders may share one | yes | Davide, 2026-08-30 |
| A player may also **compose** a mark from parts, beside the six presets | yes | Davide, 2026-08-30, on §6 of the return |
| The strip's affordance is the arrow (4c), not a chip face or a bare ripple | yes | Davide, 2026-08-30, in the design |
| How many marks, what they are, what the editor looks like, what a held edit says | **answered** | *A Name You Chose*, §6 below |
| The name's length bound, and what an empty one means | **answered** | the frame; §4 was right on both |

Davide was offered a settings-sheet face as the entry point and took the strip instead: *"Tapping on
the player's icon/name."* That is the more expensive of the two — the strip is chrome behind every
destination, so the affordance has to survive ten screens rather than sit inside one sheet — and it
is also the only one that answers *"where do I change that?"* without the player already knowing.

---

## 2. Why the picture is drawn and not uploaded

The rejected option is worth recording, because it is the one a reader will assume was meant.

A photo the player picks is a **bucket, a signed-upload endpoint, a picker on three platforms, a
downscale, a cache and a moderation answer** — and the moderation answer is not optional the moment
multiplayer arrives, which is the destination this whole account layer is pointed at. Against that,
the design system's own rule already says what the app's faces are made of: *"every icon in this app
is a `Canvas` path because a bitmap rasterises differently on the recording machine and the verifying
one"* (`player-strip-sheet.md` §4.4). A photo is the one thing a screenshot baseline cannot hold
still.

Sign-in provider pictures were rejected for a blunter reason: **Apple returns none.** Google returns
a URL, Apple returns a subject and — on the first sign-in only — a name. A face that exists for half
the players and can be changed by neither is worse than no face.

---

## 3. Why nothing goes into `core`, and why this is not a `ClientVerb`

`ClientVerb` is a closed registry with a sentence over it that reads as law: *"one member per
mutating function in `core`, and that correspondence is the whole specification."* `ClientVerbTest`
enforces it, and `offlineRule` will not compile without an answer for a new member.

**A profile has no such function and must not acquire one.** The three properties that make a verb a
verb are all absent:

- It does not mutate a `GameState`. The colony is the same colony before and after a rename.
- It cannot be *replayed*. Every verb is validated by advancing the authoritative colony to a claimed
  instant and re-applying it; a name has no instant it depends on and no `core` result that can
  refuse it.
- It is not per-colony. `players.id` outlives a colony — deleting an account and signing in again
  mints a fresh `id`, and a name attached to the snapshot would be resurrected by the one-time upload
  slice while the account it belonged to was gone.

So it is a **second small surface beside the sync pair**, exactly as `PlayerRepository` is a second
interface beside `ColonyRepository` and for the same reason: the two answer about different things
and only one of them has a version column.

### The shape, settled by the build

**Two new routes, `GET /v1/profile` and `POST /v1/profile`, and nothing added to `SyncResponse`.**

The first draft of this section put the profile on `SyncResponse` to save a round trip on a cold
start that measures 4.9 s. It is wrong, and the reason is worth writing down because it will be
proposed again: **`Protocol.json` sets `encodeDefaults` and deliberately does not set
`ignoreUnknownKeys`, and `RequiredFieldsTest` pins that nothing on this wire has a default.** A field
added to `SyncResponse` is therefore not a compatible addition — it is a response the 0.21.0 client
already on TestFlight cannot decode at all, which would force `OLDEST_SERVED` to 2 and strand every
install that has not updated.

**A new route costs none of that**, because an older client simply never calls it — the same
reasoning `ApiVersion` already records for adding a verb. So the read is its own request, issued
beside the sync rather than after it, and `ApiVersion.CURRENT` does not move in this slice.

- **Write is `POST /v1/profile`**, admitted through the same `Authenticator` and answering the same
  `ApiError` taxonomy. Not a verb, not in the outbox, not idempotency-keyed — a rename is naturally
  idempotent, and a second identical write is the same row.
- **The columns are on `players`, nullable, with no default.** Null is *"has not chosen"*, and it is
  what every account that predates this slice reads. The strip keeps `Strings.playerDefaultName()` —
  `Dead Reckoning` — as what it draws when the answer is null, so nothing on screen changes for a
  player who never opens the editor.

### What a rename does with no signal

**It refuses, in the app's own amber held language, and does not queue.** The outbox exists for verbs
the server can validate by replay; a profile write has nothing to replay against, so queueing one
would mean inventing a second, weaker outbox for one field. #113 already shipped the vocabulary for
this — ten controls that go amber when `held`, and two that refuse in red — and a rename is squarely
in the first group: it can wait, and nothing about the colony is wrong while it does.

Consequence worth stating out loud rather than discovering: **the editor is unreachable-but-visible
offline**, not absent. §5 asks Claude Design what it says.

---

## 4. Open, and argued rather than called

1. **The name's bounds.** A proposal, not a decision: trimmed of surrounding whitespace, 1–24
   characters after trimming, no uniqueness, no blocklist. 24 is not arbitrary — the strip gives the
   name every pixel left over after a 20dp mark, a `LV n` badge and a 38dp gear, and at 320dp that is
   about 148dp, which is roughly 24 monospace characters at 13.5sp before the ellipsis does the rest.
   A bound the strip cannot draw is a bound that ships as a lie.
2. **What an empty name means.** Clearing the field is either *"go back to `Dead Reckoning`"* (null on
   the wire) or a refusal. The first is kinder and is one fewer error state to draw; it is also the
   only one that gives a player a way *out* of a name they regret. Recommended, not called.
3. **Whether the name is ever shown to another player.** Nothing is, yet. The answer changes nothing
   in this slice and everything in the one that adds a blocklist — recorded so that the absence of a
   filter here is a deferral rather than an omission.

---

## 5. The call — what Claude Design is being asked

The frame is authoritative about all of it; none of it may be invented at the keyboard. The prompt
sent is reproduced in §6.

1. **The mark set.** How many, and what they are. `PlayerMark` today is one drawing — a world with a
   trajectory that has already left it, in the icon set's 24-unit box at stroke 1.6 — and its own file
   says *"the day identity earns variation it should be drawn for it, not derived from a number nobody
   picked."* This is that day. Whether colour is a second axis or the set is the whole choice.
2. **The editor's form.** The app has **two** modals — `OltreBottomSheet` and the dispatch sheet — and
   a settings sheet that already wears four faces. Is this a fifth face, its own sheet, or a
   destination?
3. **The app's first text field.** There is no text input anywhere in Oltre — no `TextField`, no
   `BasicTextField`, no `KeyboardOptions`, nothing. So this is a new design-system component from
   nothing: its resting state, its focused state, its caret, its clear affordance, its character
   counter if it has one, and what it does when the keyboard covers it.

   > **This was wrong, and the correction is worth more than the claim was.** There *is* one text
   > input: `SearchField` in `client/galaxy/ui/.../LedgerHead.kt`, a `BasicTextField` with a
   > `decorationBox` that draws its placeholder *behind* the field so the caret is never swapped out.
   > It is 28dp, borderless-with-a-line, and it filters a list. What remains true is the part that
   > mattered to the ask: **nothing in this repo has ever met a soft keyboard** — no
   > `KeyboardOptions`, no `ImeAction`, no `FocusRequester`, no `imePadding`, and the only
   > `WindowInsets` call in the whole client is `MainScaffold`'s `safeDrawing`, which is outside the
   > sheet's window entirely. So the frame's answer stands and gains a precedent to copy: the
   > decoration-box idiom, `cursorBrush = SolidColor(...)`, and a border that switches on content.
4. **The strip becomes tappable.** Which region — the mark alone, the mark and the name, or the whole
   left cluster — and how it says it is pressable at all, given that the gear beside it is the only
   control on the strip today and the design system has no disabled state and no hover.
5. **The held face.** What the editor says when there is no signal, in the amber language *Nothing Is
   Local Now* settled.
6. **The save affordance.** Commit-on-tap like every control on the settings face, or an explicit
   confirm? A text field is the first control in this app where the two genuinely differ.

## 6. What the frame answered

*A Name You Chose*, returned 2026-08-30. Every number below is the frame's; none of it was chosen at
the keyboard. The canvas also carries §Seven, five tables of every value with where each came from —
read it there rather than duplicating it here.

### The seven answers

1. **Six marks, one hue, and the set is the whole choice.** Threshold plus five, each a different
   *silhouette* at 20dp rather than five versions of one idea: one diagonal, one centred disc, one
   horizontal, one corner, one nest of arcs, one vertical. Three primitives at most, stroke 1.6, no
   fill but the terminus dot. **Colour is not a second axis** — every hue in this app already means
   something about affordability or state, and a player-picked amber mark would sit 40dp from an
   amber fleet strip meaning something else. Six baselines, not twenty-four.
2. **A fifth face on `OltreBottomSheet`.** Not its own sheet (a second surface with its own IME
   handling to get wrong, for one caller) and not a destination (there are five, the bar is full at
   320dp, and a screen unreachable from the tab bar breaks the rule this product keeps most
   strictly). **Header, mark, name — and the order is mechanical**: the field is last so the keyboard
   pushes it onto the keys with the save button in the 44dp between.
3. **The field is the card at the button's radius.** 44dp tall, `#101218` inside 1dp white 9% at 9dp
   corners, 11dp leading padding, text in the strip's own 13.5sp SemiBold. Focus swaps the line for
   accent 45%. **No floating label, no underline, no error state** — a name that cannot collide with
   anybody cannot be rejected.
4. **The whole left cluster, and an arrow.** Mark, name and badge are one target taking the bar's
   full 38dp for touch, ending 7dp before the gear, marked by `→` at 12.5sp tertiary — the character
   the product already uses in *"→ LV 13"*. Davide took 4c on 2026-08-30 over a 34dp chip face and a
   bare ripple. **The strip stays 38dp and `DESTINATION_HEIGHT` does not move**, which is the whole
   reason this affordance and not the other two.
5. **Held borrows the locked card.** The grid and the field drop to 42%, the requirement goes above
   them in amber on the fleet strip's surface, and the save button is *absent* because there is
   nothing to save. The sheet still opens — *"a player who taps their own name deserves to see what
   they would be choosing."* Nothing is red.
6. **The mark commits on tap; the name gets a button.** The one control in this app where the two
   genuinely differ, and it splits rather than picking one: a tap is a tap, and a name has no
   keystroke meaning *done*, so `Save name` appears only when the draft differs from what is
   committed — **absent, never disabled**.
7. **An empty field is a preview, not an error.** §4.2's recommendation, taken and improved: the
   placeholder is always `Dead Reckoning` at tertiary *in the same size and weight as real text*, so
   an emptied field is already showing what saving it does.

### The composer, which the brief did not ask for

§Six of the return proposes a **mark grammar** beside the six presets — four bodies, four paths,
three termini, drawn in three fixed regions of the 24-unit box so no two parts can occupy the same
ink. **Forty legal marks from eleven drawings**, because a terminus is the end of a path and a mark
with no path has none: 4 × (3 × 3 + 1).

**Davide took it, 2026-08-30: both.** What it changes, and what made it worth asking about:

- **The wire is not an enum any more.** `PlayerMark` is a sealed pair — `Preset(MarkPreset)` or
  `Composed(body, path, terminus)` — because *four of the six presets are shapes the grammar cannot
  make*: a centred disc, a full-width ellipse, a 12.4-unit arc, a full-height plumb line. The return
  says it plainly: *"the column is a tuple plus a preset id, not a tuple alone."* Only `THRESHOLD` is
  both, which is why the compose face opens on it.
- **`players.mark` is `jsonb` rather than four columns.** Three of four would be null for every
  preset, and the cross-column invariant is one the type already states. Nothing shipped, so this is
  a reshape and not the migration the return budgeted for.
- **The cost inverts.** A fixed set pays one baseline per mark; a grammar pays one per *part* —
  eleven — plus twelve pairwise geometry assertions, one per body-and-path pair.

### What is now settled that §4 left open

- **The bound is 24** and the field is what enforces it: silent to 17, `18/24` from 18, and at 24 the
  field stops accepting rather than refusing. The counter **never changes hue** — amber means held,
  red means short, and running out of characters is neither.
- **An empty name is `Dead Reckoning` again**, stated by the placeholder while the player is looking
  at it rather than by a rule they have to discover.
- **The name is still never shown to another player**, and the return's own note says nothing checks
  and nothing should.

### Two places the frame said two things, and how Davide read them

Both surfaced in implementation rather than in review, which is the argument for building a frame
rather than agreeing with it. Both were put to Davide on 2026-08-30 and both were settled as built.

1. **The held field.** §Two drops *"the grid and the field"* to 42%; the field's own table gives held
   a 1dp amber-22% border over an amber-6% fill. They cannot both be literal — at 42% that border
   composites to about 9% and the specified face does nothing. **Davide: the amber face stays at full
   strength.** So the 42% falls on the grid, the mark-name line and the compose row, and the field
   keeps its own treatment. The reading is the one in which every number the frame wrote still does
   something, and it puts the explanation on the one element the player was about to touch.
2. **The tap target.** §2 says the target *"ends 7dp before the gear"*; §3 says in bold that *"the
   cluster hugs its contents"*. Both are true only when the row is full. **Davide: hug.** The
   consequence is visible and worth stating rather than discovering — **the level badge now follows
   the name instead of sitting against the gear**, which is why three of the four existing strip
   baselines moved by more than the arrow's own ink. The bolded instruction decided it, and so did
   the rejected 4a: a 34dp chip face is drawn around contents, never around a gap.

### The fourteen open notes, and who owns them

The return closes with fourteen items. Three need Davide and are not the build's to take:

1. **A first-run naming step is not drawn.** A first sign-in lands on the colony already wearing
   `Threshold` and `Dead Reckoning`. If a naming step is wanted, it is a new frame and a new
   argument.
2. **A composed mark has no noun.** The grid line spells the tuple — *"Your mark · Limb · Rising ·
   Dot"* — and if a composed mark ever has to appear in a notification or a ledger line, that is a
   new decision.
3. **What the server may still refuse is not designed.** Length is bounded by the field and there is
   no blocklist by decision; any *other* refusal has no frame.

The rest are the build's, and two of them change files outside this slice:

- **The caret is an exemption from the no-animation rule** — 1.5 × 19dp, accent, at the platform's
  blink rate — and the rule should say so **in writing**, along with the platform's own selection
  handles. That is an edit to the design system's own readme, not a code comment.
- **`OltreBottomSheet` has never had to scroll or bottom-align**, and with the keyboard up this face
  is taller than the room. The return notes the settings face — which is longer — has the same
  problem the moment anything on it gains a field.
- **The IME lift cannot be baselined.** The desktop dev loop has no software keyboard, so 6a and 6b
  are the specification and **the check is manual, on a phone, in both languages**.
- The sheet is sized by subtraction with the keyboard up, so **no constant for it belongs in the
  suite**: the rail's height is font-metric driven and any written figure is a dp or two out on the
  device.
