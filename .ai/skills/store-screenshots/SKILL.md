---
name: store-screenshots
description: The exact canvases Oltre's App Store screenshots are delivered at — one iPhone size, one iPad size, nothing else. Use before generating, resizing, or exporting any App Store screenshot.
when_to_use: >
  Use whenever producing screenshots for the App Store listing — "make the App Store
  screenshots", "export screenshots for the store", "resize these for App Store Connect" — or
  when App Store Connect rejects a screenshot's dimensions.
---

# App Store screenshots

| Slot | Canvas (portrait) | Delivered as |
|---|---|---|
| iPhone 6.5" | **1284 × 2778** | `iphone_65_<n>_<screen>.jpg` |
| iPad 13" | 2064 × 2752 | `ipad_13_<n>_<screen>.jpg` |

- **iPhone is one set, at 1284 × 2778.** Produce no other iPhone size — not 6.9" (1320 × 2868),
  not 6.7", not 5.5". The 6.5" slot is the one the listing uses, and App Store Connect rejects any
  other size dropped into it.
- **Resize a taller capture by width, then centre-crop the height.** A 6.9" simulator capture
  (1320 × 2868) is a touch taller than 6.5": scale to 1284 wide, then crop to 2778 high —
  `sips --resampleWidth 1284 <files> --out <dir>`, then
  `sips --cropToHeightWidth 2778 1284 <files>`. Never stretch to fit: the ratios differ.
- **Check every file before handing it over** with `sips -g pixelWidth -g pixelHeight <files>`, and
  look at one: the header and the tab bar must still be whole after the crop.
