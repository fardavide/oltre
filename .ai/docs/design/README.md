# Design returns, archived

Frames as Claude Design returned them. The **prose record of every call is in the sheet the design
answers** — that is the durable half, and it is what to read first; these files are here so a frame can
be looked at rather than remembered.

**Copying a return down here is optional, and the default is not to** — Davide, 2026-08-16:
*"I don't think it's necessary, as they're on Claude Design already."* The project is the source, it
does not go away, and a frame is a rendering rather than a decision. What has to live in the repo is
the **prose record**, because that is the half written in this project's own voice and the half a
reader will not think to go looking for. So the table below is a convenience rather than an
obligation, and a return that is only in the project is not a gap.

| File | Answers | Read the calls in |
|---|---|---|
| `fleet-screens.dc.html` | the fleet slice — world row (1a/1b/1c), dispatch sheet, Fleets, Shipyard, the Colony strip | [`../fleet-sheet.md`](../fleet-sheet.md) §12 |
| `looks-near-is-near.dc.html` | the drawn map — the folded galaxy, the universe, the caption, what the ledger keeps | [`../drawn-map-sheet.md`](../drawn-map-sheet.md) |

`looks-near-is-near.dc.html` is argument **and** drawing in one canvas, unlike the fleet pair, so it is
archived whole. Its four component definitions are not — they are logic rather than frames, and the
sheet carries every constant they encode. Re-fetch them from the Claude Design project if the original
JavaScript is ever needed:

```
project aea4cd09-c111-4e9a-8b7d-c25cea371fd4
  GalaxyBands.dc.html   the fold: band geometry, drift, class draw, rings, hour ticks
  MapCaption.dc.html    the caption bar and its two forms
  GalaxyHead.dc.html    the worlds/map toggle and the galaxy chip
  LedgerHead.dc.html    the ledger header, after the chips and the sort came off
```

## Returns that live only in the project

Not copied, and none of them is an omission.

`Fleet Decision Sheet.dc.html` — argument rather than drawing, and §12 carries all seven of its calls,
its four dead premises, its ten rejections and its three open items in the repo's own voice.

`A Door Back.dc.html` — the Fleets landed section becoming a list of worlds you can go back to,
2026-08-16, issue #62. It is the worked example of the default above: its two rejected options, its
six calls and its two open items are in [`../decisions.md`](../decisions.md) under *The landed ledger
becomes a list of worlds*, and the frames it draws are baselined in the repo as screenshots
(`fleets_three_runs`, `fleets_three_runs_slide_over`, `fleets_dispatch`, `fleets_dispatch_no_ships`)
— which is a stronger record than an archived canvas, because a baseline fails when the screen stops
matching it.

```
project aea4cd09-c111-4e9a-8b7d-c25cea371fd4
  Fleet Decision Sheet.dc.html
  A Door Back.dc.html   1a/1b/1c, the six row states, and the four screens
  WorkedRow.dc.html     the 45dp row: the disc, the two lines, the compact form, the foot line
  WorkedList.dc.html    the section: the label, its trailing count, and Design's own day 21
```

## How to read a `.dc.html`

It is a Claude Design canvas: one `<section>` of absolutely-sized frames, each an iPhone-width
rendering, composed from `x-import` references into the `oltre-design-system` bundle. The design system
itself is **lifted from this repo** — `tokens/colors.css` is `OltreColors` verbatim — so a token in a
frame maps to a token in `OltreTheme.kt` rather than to something new that has to be invented.
