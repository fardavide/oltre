# Design returns, archived

Frames as Claude Design returned them. The **prose record of every call is in the sheet the design
answers** — that is the durable half, and it is what to read first; these files are here so a frame can
be looked at rather than remembered.

| File | Answers | Read the calls in |
|---|---|---|
| `fleet-screens.dc.html` | the fleet slice — world row (1a/1b/1c), dispatch sheet, Fleets, Shipyard, the Colony strip | [`../fleet-sheet.md`](../fleet-sheet.md) §12 |

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
