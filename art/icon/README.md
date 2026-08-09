# App icon — "Threshold"

The lit curve of a world across the bottom of the frame, and one luminous trajectory rising out
from behind it into empty space. *Oltre* means *beyond*; the icon is the crossing of an edge.

Built only from the palette committed in
[`OltreColors`](../../client/design/src/commonMain/kotlin/dev/fardavide/oltre/client/design/OltreTheme.kt),
so the icon and the app read as one thing.

## Masters — the only files to edit

| File | What |
|---|---|
| `threshold.svg` | Full-bleed colour artwork. The icon. |
| `threshold-tinted.svg` | Greyscale on opaque black, for iOS 18's tinted appearance. |

The tinted variant is drawn separately rather than desaturated: the colour artwork separates the
limb from the arc by **hue** (cool cyan against warm amber), and iOS discards hue entirely when it
tints. Desaturating fuses the two into one smear, so the tinted master re-separates them by
**luminance**.

There is deliberately no `dark` appearance. The default artwork is already near-black by design,
and iOS fills a dark variant's background with a generic system gradient — which would replace the
void the composition depends on.

## Regenerating

```bash
python3 art/icon/generate.py
```

Needs macOS (`iconutil`), Google Chrome and Pillow. Everything below is generated output —
regenerate it, never hand-edit it.

| Target | Path |
|---|---|
| iOS | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` |
| Desktop | `client/shell/icons/oltre.{icns,ico,png}` |
| Android | `art/icon/android/` — generated here, copied into `androidApp/src/main/res/` |

## Android

The generator writes to `art/icon/android/`; the app module reads from its own source set, so the
`mipmap-*/` directories and `values/ic_launcher_background.xml` are **copied** into
`androidApp/src/main/res/` after a regeneration. `art/icon/android/README.md` has the two steps.

The adaptive-icon foreground is the artwork with its background rect removed, and the background
layer is the flat `#05070D` it was drawn against — so the two recompose to exactly the flat icon
(verified to under 1/255 mean channel difference).

**The Android assets are framed differently from every other platform, deliberately.** A launcher
picks its own mask and only a *circle* of 66 of the 108dp canvas is guaranteed to survive it. Used
full-bleed, the arc's luminous head falls outside that circle and a round launcher — the Pixel
default — crops it off completely, leaving an arc that runs off the edge into nothing. So the scene
is scaled to 78% about bottom-centre: the limb still bleeds off the bottom and sides, and the head
moves to 237 from the centre against a safe radius of 313. The full-bleed atmosphere layers
(background, sky glow, vignette) are left unscaled so no seam shows where the scaled content ends.

Play Store listing art is shown uncropped, so `play-store-512.png` keeps the original framing.
