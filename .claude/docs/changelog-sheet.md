# What changed — the in-game changelog (0.19)

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

- **The order of the doors.** Whether the gear's sheet gets a row that swaps its contents for the
  changelog, dismisses and re-raises, or something else, is the design's to answer. The app has never
  stacked a sheet on a sheet.
- **What a patch release's page says when it changed nothing a player can see.** Several `0.x.y`
  entries in the README are about coverage gates and CI. Those pages get a player-facing line or they
  get the release's honest *"nothing you can see, but"* voice; either way no page is empty, because a
  page you can swipe to and that says nothing is the sheet's version of a dead control.
- **Whether the sheet remembers where it was left.** It opens on the newest page every time today.

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
