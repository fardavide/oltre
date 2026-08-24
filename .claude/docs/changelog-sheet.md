# What changed — the in-game changelog (0.19)

> **The round trip closed.** Claude Design returned *A Sky Per Build* on 2026-08-24 and it is
> accepted and built. §§1–5 below were written before the frames existed and are kept as the brief;
> everything the design decided is in **"What came back"** at the foot of this file, which is what
> the code cites.

The game has shipped 65 times in nineteen days and has never told a player so. The README carries
every one of those entries and no phone has ever displayed it. This is the surface that does:
**a bottom sheet of horizontally paged releases, newest first, one page per version, raised by the
gear and raised by itself the first time a new build is opened.**

Tracked as [#115](https://github.com/fardavide/oltre/issues/115), which carries the design prompt
and is labelled `Needs Design` until the frames come back.

Davide's request, 2026-08-23: *"I wanna add in game changelog. It must open on game updated and in
settings. We may need design handoff, but I want a bottom sheet with horizontally scrollable page,
one per version. It must include old releases as well, with version and date. Nice if we can make it
a bit more visual/graphic."*

## The four calls, taken before a line was written

Asked and answered in session, 2026-08-23. Each had a cheaper option on the table and each was
overruled towards the more complete one, which is worth recording because three of them cost
something forever.

| | The call | The cheaper thing it refused |
|---|---|---|
| **History** | **Every version, all 65.** Patches get a page of their own — `0.4.3` is a page, not a footnote under `0.4.0`. | 18 feature releases plus one page for the `0.0.x` era. |
| **Language** | **English and Italian, both, translated.** | English only, the game's one untranslated surface. |
| **Auto-open** | **Any version change.** `0.19.0 → 0.19.1` raises the sheet exactly as `0.18 → 0.19` does. | Feature releases only, so a bug-fix release stays quiet. |
| **The graphic** | **Drawn from the version number**, in Compose, from no asset. Every future release gets its mark free. | An icon per release from the existing set; or real illustration, and a drawing blocking every release. |

**Three of those four are a standing tax and the fourth is what avoids one.** All-65 × two
languages is ~130 pages of copy to write once; *any version change* means the sheet is a thing a
patch release has to be worth raising; **both languages means every future release writes its page
twice, or it does not ship.** The graphic is the one call that scales without a person: a mark
derived from `major.minor.patch` needs nobody the day `0.31.2` is cut.

## §1 — why a sheet, and why this one is not a new surface

The same argument [`ask-once-sheet.md`](ask-once-sheet.md) §1 made for the settings panel, and it is
stronger here. A changelog is read once, in full, and then dismissed forever — it is the definition
of a thing that must not become a destination. `OltreBottomSheet` is the app's only way to raise a
panel, this is its fourth caller, and a sheet is the only shape that needs no back control in an app
with no navigation stack.

**What is new is the paging.** Nothing in Oltre scrolls sideways today: every list runs down, the
galaxy pans in two axes and nothing pages. So the horizontal swipe is the one gesture this feature
introduces, and the design has to answer what makes a page look like one of many — see the prompt.

## §2 — where it opens from, and how it opens itself

**Two doors, one sheet.**

1. **The gear.** The gear already opens the settings sheet, and settings is where a changelog is
   looked for when it is looked for on purpose. So it is reached *through* that sheet rather than
   beside it, which is the part the design has to draw — the app has never put a sheet over a sheet
   and should probably not start.
2. **A new build.** The app remembers the version it last showed a changelog for. On a launch where
   that differs from the version now running, the sheet raises itself on the newest page.

**The rule has three inputs and not two**, because of a wrinkle that exists exactly once: on 0.19.0
*every* player has no remembered version, and so does every fresh install. They must not be treated
alike — an upgrade should see what changed, and somebody who has never played has nothing to be told
about. The save file is what separates them:

| remembered version | a colony on disk | what happens |
|---|---|---|
| none | yes | **opens** — an upgrade from a build older than this feature |
| none | no | **records only** — a first launch is not a changelog |
| differs from current | either | **opens** |
| equal to current | either | nothing |

The remembered version is written **when the sheet is dismissed**, not when it is raised: a sheet
killed by a crash or a task switch has not been read.

It goes in `Preferences`, not in `GameSave` — the file that already holds what the app remembers
about *itself* rather than about the colony, which is unversioned, never migrates, and whose loss
costs one tap. Losing it here costs one extra showing of the changelog, which is the correct price.

## §3 — what a page holds, and the budget the copy is written to

**The budget is set here rather than by the frame**, because 130 pages of copy cannot be rewritten
when a layout moves. Design accommodates this; it does not reduce it.

- **Version** — `0.18.0`, verbatim, always three parts.
- **Date** — the release date, formatted per language. New to the catalogue: the app has never
  written a calendar date, only clock times and durations.
- **Headline** — one line, ≤ 40 characters. What the release *was*.
- **Notes** — one to three lines, ≤ 90 characters each. Never four.
- **The mark** — drawn from the version number, no asset.

Sixty-five pages at that budget is a hard read end to end and is meant to be: the newest page is the
one anybody sees, and the sixty-fourth is there for the player who swipes because the swiping is
pleasant.

**The copy is a condensation, not a copy-paste.** README entries run to twenty lines and are written
for somebody reading a repository. `0.0.7`'s page is not its README entry shortened; it is what that
release did, said to a player who has never seen a diff.

## §4 — where the copy lives, and why not in `StringId`

This is the one place the implementation departs from the literal reading of the language call, and
it does so to keep the guarantee rather than the mechanism.

The catalogue's discipline is an exhaustive `when` over `StringId`: a new id fails to compile in
every language until it is translated. Applied here that is **~260 enum constants and two ~260-line
`when` branches**, with each release's four lines scattered across three files and nowhere near the
release they belong to. `English.kt` and `Italian.kt` would roughly double, and every future release
would edit three files far apart.

So the changelog is written as **two documents rather than 260 catalogue entries** — one list of
releases per language, each release's page in one place, in `:client:changelog:presentation`. What
replaces the compiler is a test asserting the two documents carry **the same versions, the same
dates and the same number of notes**, which is a *stronger* guarantee than the enum gives: the `when`
can only catch a missing id, and this catches a release Italian never got, a date that drifted, and a
page that lost a line in translation.

The version and the date stay in the global catalogue, because a date's *format* is exactly what that
catalogue is for.

## §5 — the mark, and what makes it not decoration

Davide asked for *"a bit more visual/graphic"* and chose the option with no artwork in it. So each
page carries something drawn from the numbers of the version itself — deterministic, dense enough
that sixty-five of them differ, and cheap enough that `0.31.2` gets one without anybody being asked.

What the design decides is the *language*; what the code guarantees is that it is a pure function of
`(major, minor, patch)` and nothing else. Not of the date, not of an index into the list, not of a
hand-kept table — anything of those turns the free mark back into a per-release chore, which is the
one thing this call was made to avoid.

## What implementation had to decide

Recorded here because the code cites this file.

**The current version is the head of the catalogue.** There is no generated `BuildConfig` in this
build and adding source generation for one string is not worth it. `Releases.all.first().version` is
what the app is running, and a test asserts it equals `libs.versions.oltre` — so a version bump with
no changelog page fails the build rather than shipping a sheet that never opens.

**The catalogue is checked against the README.** A desktop test reads `README.md` and asserts every
`### x.y.z — date` heading has a page with that exact date, and that no page exists for a release the
README does not have. Sixty-five hand-written entries and a permanent per-release obligation is
precisely the shape that rots quietly; this is what makes it loud.

**Version numbers are not contiguous and the test must not assume it.** There is no `0.0.12` entry in
the README — the release exists and the changelog skips it.

## Open, and deliberately

- **A player who skipped three builds sees no sign of it.** The sheet opens at the newest and has no
  unseen state. Design raised it and did not decide it; the cheapest honest version is a second,
  dimmer cap on the rail at the build last shown — which is one integer that is *already* being
  stored. Worth doing the day somebody misses two releases and says so.
- **1.0.0 empties the sky.** It falls out of the rule rather than being chosen — minor and patch are
  both zero, so there is nothing but the one world the major finished — and it is the single most
  visible frame the mark will ever draw. Accepted on purpose: the alternative is one clause carrying
  the previous line's bodies forward, and that clause is the beginning of a table.
- **Whether the sheet remembers where it was left.** It opens on the newest page every time.
- **The 0.17 settings frames are stale by ~170dp.** They were drawn at 573dp of content and the sheet
  is full height; the build row lands in exactly that space, so the two changes are one change
  whenever that sheet is next redrawn.

Closed by the round trip: *the order of the doors* (§4 — the sheet swaps its own contents) and *what
a patch release's page says* (every page carries a player-facing line; the releases whose README
entry is about CI are written for a player or they say plainly that nothing on screen changed, which
is what `0.4.1` and `0.0.14` do).

## What came back — *A Sky Per Build*, accepted 2026-08-24

Five questions went out and five came back answered. What follows is what the code implements; the
frames are `ChangelogSheet`, `ChangelogPage`, `VersionMark` and `ReleaseRail` in the Claude Design
project, with the argument on the *A Sky Per Build* canvas.

**1 — The card hugs the release; the sheet holds the air.** A full-height sheet over five lines of
copy is 90% air however it is arranged, so the page does not fill it: the card is as tall as its
release and sits at the foot of the viewport, and the slack collects under the title. A one-note page
is a **short card, not an empty one**. The two things that must not jump between pages are the rail
and the reading position of the last note, and bottom-aligning is what holds both.

**2 — The mark is a distance, not an identifier.** `minor + patch` bodies on a golden-angle spiral
over a world's limb: the first `minor` filled because a minor line is settled, the rest hollow
because a patch rides on one that is not. The bearing carries `i + patch` and the radius `i / N`, so
**a patch re-lays the whole sky rather than adding a dot to it** — which is also the distinctness
proof, since two versions can only draw the same sky if they are the same release. A major empties
the sky and puts a finished world on the limb.

The rule is in `:client:changelog:domain`, not in the drawing, and the design asked for that in as
many words: *"a 20dp screenshot diff cannot state where the ink is."* `VersionSkyTest` asserts every
body inside the box, no two bodies touching at page size, and the limb never crossing the sky —
across every version the project could plausibly reach, not the sixty-six it has.

Three languages were weighed and refused: a **hash** (0.17.0 and 0.18.0 would look like strangers),
**three arcs** (every sweep needs a denominator and there is none — and the app already has one
completion gauge, on the player strip), and **a bar of ticks** (a mark that repeats the label is a
second label that cannot be read).

**3 — Sideways is taught by the edge, and by an edge that is missing.** 18dp of the next card at
393dp, 12dp at 320. On the newest release nothing peeks to the left, so **the end of the run is drawn
by the absence of a peek** and there is no first-page case in the code. Position is a rail with one
tick per minor line — `patch == 0`, a pure function of the versions, so it keeps no table — and it
scrubs, because sixty-five swipes is not a way to reach the first week.

**4 — The settings sheet swaps once, and there is no way back.** No sheet over a sheet and no back
stack: the sheet is already at full height, so its contents are replaced in place in 210ms and
nothing resizes. The price is that returning to the two ladders costs a dismiss and a tap on the
gear; what it buys is **one changelog face, identical however it was raised**, so the first-launch
route and the settings route are the same code and the same drawing.

**5 — Prehistory draws itself.** `0.0.x` has minor 0, so every body on those pages is a patch and
every one is hollow, and the rail's far end carries its only long tickless stretch. No sepia, no
label, no second layout — the rule already says it.

### What implementation found that the frames could not

- **The gear is behind the scrim.** The design lists the ways out as the handle, the scrim and the
  system gesture, and the 0.18 code comment claims the gear is a fourth. It is not: a
  `ModalBottomSheet` covers the window, so the strip underneath consumes nothing while the sheet is
  up. This mattered because dismissal is what marks a release read — the first cut let the gear close
  the sheet **without** marking it, which would have shown a player the same release for ever. Every
  exit now goes through one function. `AlertSheetAppBehaviourTest` had been "dismissing" with the
  gear since 0.18 without ever depending on the sheet actually closing.
- **A full-height sheet cannot be dismissed by a test that taps its scrim**, because the scrim's
  *centre* is behind the sheet and `performClick` aims at a node's middle. The robot performs the
  scrim's own semantics action instead.
- **The harness had to be told the news is old.** A colony on disk with nothing remembered is an
  upgrade, so the sheet raises itself over the whole app — which broke eighteen existing tests that
  then tapped controls behind a scrim. `app()` now opens a build whose changelog has been read, and
  the changelog's own tests say what the file holds every time.
- **A second field in `Preferences` would have cost the first one.** A required field added to a
  serialized record turns every file an older build wrote into a parse failure, so an upgrading
  player would have silently lost their galaxy landing. The store decodes a record with a default per
  field; `Preferences` itself stays strict.
- **The page has to be measured, not assumed** — and this one shipped in the first cut. The column
  was taken from the design's own numbers as constants (341dp at 393, 284 in a Slide Over pane), and
  they are right at exactly those two widths. `compact` flips *below* 360dp, so a 360dp Android phone
  — the commonest width there is — took the wide branch and laid a **319dp sky inside a 286dp card**:
  `Modifier.size` clamps the Canvas, the sky keeps its own geometry, and the limb, which spans its
  whole box by construction, draws past the card's border into the gap toward the next page. At
  560dp, this app's own column cap, it went the other way — 486dp of card holding a 319dp mark.
  Both frames in the module render at 393 and 320 with the same constants the code used, so **the
  suite could not see it**; what catches it now is a behaviour test that measures the mark against
  the card at six real widths, and it fails against the old code by 33dp.
- **Measuring the page then exposed the height, which is the same mistake in the other axis.** The
  mark is a square of the column, so a wider sheet is a *taller* card — and at 560dp the page grew
  past the viewport it is bottom-aligned in. Nothing on a page scrolls, so what a page loses in that
  state is not its picture but **its last note**, starved by a `Column` with no room left; a check on
  the card's position cannot see it, because the card still starts at zero. Two answers, both of them
  the sky giving way rather than the words: the mark **stops growing at 319dp**, the widest the design
  drew, and it takes only the height the copy leaves — so a landscape iPhone, a 393dp-tall window the
  design never drew at all, still lands every line.

## What the round trip is for

The record of what was actually asked of Claude Design, per the convention that the prompt is a
delivery and the argument is what stays here.

1. **The page** — what one release looks like: version, date, headline, up to three notes and a mark,
   at 393dp and at 320dp.
2. **The mark's language** — what is drawn from three integers such that sixty-five of them are
   distinguishable and none of them is a chart.
3. **Paging** — what tells a player there is another page, in an app where nothing has ever moved
   sideways, and what carries the position across sixty-five stops without becoming sixty-five dots.
4. **The door from settings** — how the gear's sheet leads to this one without stacking a sheet on a
   sheet.
5. **The oldest pages** — whether a page from `0.0.3` is drawn the same as `0.18.0`, given that its
   mark is nearly empty by construction.
