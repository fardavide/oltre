# Design returns, archived

Frames as Claude Design returned them. The **prose record of every call is in the sheet the design
answers** — that is the durable half, and it is what to read first; these files are here so a frame can
be looked at rather than remembered.

| File | Answers | Read the calls in |
|---|---|---|
| `fleet-screens.dc.html` | the fleet slice — world row (1a/1b/1c), dispatch sheet, Fleets, Shipyard, the Colony strip | [`../fleet-sheet.md`](../fleet-sheet.md) §12 |
| `looks-near-is-near.dc.html` | the drawn map — the folded galaxy, the universe, the caption, what the ledger keeps | [`../drawn-map-sheet.md`](../drawn-map-sheet.md) |


`looks-near-is-near.dc.html` is argument **and** drawing in one canvas, unlike the fleet pair, so it is
archived whole. Its four component definitions are not — they are logic rather than frames, and the
sheet carries every constant they encode. Re-fetch them from the Claude Design project if the original
JavaScript is ever needed:

`A Door Back.dc.html` — the Fleets landed section becoming a list of worlds, 2026-08-16, issue #62 —
is **not copied yet**, and that is a gap rather than a decision: its six calls, its three rejected
options and its two open items are in `decisions.md` under *The landed ledger becomes a list of
worlds*, which is the durable half, but the frames themselves are only in the Claude Design project.
Copy them down when convenient:

```
project aea4cd09-c111-4e9a-8b7d-c25cea371fd4
  A Door Back.dc.html   1a/1b/1c, the six row states, and the four screens
  WorkedRow.dc.html     the 45dp row: the disc, the two lines, the compact form, the foot line
  WorkedList.dc.html    the section: the label, its trailing count, and Design's own day 21
```

```
project aea4cd09-c111-4e9a-8b7d-c25cea371fd4
  GalaxyBands.dc.html   the fold: band geometry, drift, class draw, rings, hour ticks
  MapCaption.dc.html    the caption bar and its two forms
  GalaxyHead.dc.html    the worlds/map toggle and the galaxy chip
  LedgerHead.dc.html    the ledger header, after the chips and the sort came off
```

`Fleet Decision Sheet.dc.html` is deliberately **not** copied: it is argument rather than drawing, and
§12 carries all seven of its calls, its four dead premises, its ten rejections and its three open items
in the repo's own voice. Re-fetch it from the Claude Design project if the original wording is ever
needed:

```
project aea4cd09-c111-4e9a-8b7d-c25cea371fd4 · Fleet Decision Sheet.dc.html
```

## How to read a `.dc.html`

It is a Claude Design canvas: one `<section>` of absolutely-sized frames, each an iPhone-width
rendering, composed from `x-import` references into the `oltre-design-system` bundle. The design system
itself is **lifted from this repo** — `tokens/colors.css` is `OltreColors` verbatim — so a token in a
frame maps to a token in `OltreTheme.kt` rather than to something new that has to be invented.
