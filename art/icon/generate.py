#!/usr/bin/env python3
"""Regenerate every platform icon asset from the SVG masters in this directory.

    python3 art/icon/generate.py

Masters (hand-authored, the only files to edit):
    threshold.svg          full-bleed colour artwork — the icon
    threshold-tinted.svg   greyscale on opaque black, for the iOS 18 tinted appearance

Everything this script writes is generated output; do not edit it by hand.

Requires macOS (`iconutil`, for .icns), Google Chrome (SVG rasterising) and Pillow (.ico).
"""

from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ART = Path(__file__).resolve().parent
ROOT = ART.parent.parent

MASTER = ART / "threshold.svg"
MASTER_TINTED = ART / "threshold-tinted.svg"

IOS_APPICON = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
DESKTOP_ICONS = ROOT / "client/shell/icons"
ANDROID_STAGE = ART / "android"

CHROME = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")

# The one opaque full-bleed rect in the master. Dropping it yields the artwork on
# transparency, which is what an Android adaptive-icon foreground layer needs. The
# planet body is a separate opaque circle and must survive — it occludes the arc.
BACKGROUND_RECT = '<rect width="1024" height="1024" fill="#05070D"/>'
BACKGROUND_COLOUR = "#05070D"

# Android adaptive icons are cropped by a launcher-chosen mask; only a *circle* of 66 of
# the 108dp canvas is guaranteed visible. Used full-bleed, the arc's luminous head lands
# ~237/1024 outside that circle and a round launcher crops it off entirely, which is the
# one element the composition cannot lose. So the scene is scaled about bottom-centre:
# the limb still bleeds off the bottom and sides, and the head moves comfortably inside
# the safe circle (measured at 237 against a radius of 313). The full-bleed atmosphere
# layers — background, sky glow, vignette — are deliberately left unscaled so no seam
# appears where the scaled content ends.
ANDROID_CONTENT_SCALE = 0.78
ANDROID_SAFE_RADIUS = 313  # 33 of 108dp, expressed in the master's 1024 space

ANDROID_LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ANDROID_ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# The scene sits between the sky glow and the vignette in the master.
SKY_RECT = '<rect width="1024" height="1024" fill="url(#sky)"/>'
VIGNETTE_RECT = '<rect width="1024" height="1024" fill="url(#vig)"/>'


def reframe_for_android(svg: str) -> str:
    """Scale the scene about bottom-centre so nothing load-bearing leaves the safe circle."""
    s = ANDROID_CONTENT_SCALE
    open_g = f'{SKY_RECT}\n<g transform="translate(512,1024) scale({s}) translate(-512,-1024)">'
    return svg.replace(SKY_RECT, open_g, 1).replace(VIGNETTE_RECT, f"</g>\n{VIGNETTE_RECT}", 1)

ICNS_SIZES = [16, 32, 64, 128, 256, 512, 1024]
ICO_SIZES = [16, 24, 32, 48, 64, 128, 256]


def render(svg: str, size: int, out: Path, transparent: bool = False) -> Path:
    """Rasterise SVG source to a square PNG of `size` px via headless Chrome."""
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "page.html"
        page.write_text(
            "<style>html,body{margin:0;padding:0;background:transparent}"
            f"svg{{display:block;width:{size}px;height:{size}px}}</style>{svg}"
        )
        subprocess.run(
            [
                str(CHROME), "--headless", "--disable-gpu", "--hide-scrollbars",
                "--force-device-scale-factor=1", f"--screenshot={out}",
                f"--window-size={size},{size}",
                f"--default-background-color={'00000000' if transparent else 'FF05070D'}",
                str(page),
            ],
            check=True, capture_output=True,
        )
    img = Image.open(out)
    if img.size != (size, size):  # Chrome pads to a minimum window; crop back.
        img.crop((0, 0, size, size)).save(out)
    return out


def write_icns(svg: str, out: Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        iconset = Path(tmp) / "oltre.iconset"
        iconset.mkdir()
        for size in ICNS_SIZES:
            if size <= 512:
                render(svg, size, iconset / f"icon_{size}x{size}.png")
            if size >= 32:
                render(svg, size, iconset / f"icon_{size // 2}x{size // 2}@2x.png")
        out.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["iconutil", "-c", "icns", str(iconset), "-o", str(out)], check=True)


def write_ico(svg: str, out: Path) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        largest = render(svg, 256, Path(tmp) / "ico.png")
        out.parent.mkdir(parents=True, exist_ok=True)
        Image.open(largest).save(out, sizes=[(s, s) for s in ICO_SIZES])


def write_ios(svg: str, tinted: str | None) -> None:
    shutil.rmtree(IOS_APPICON, ignore_errors=True)
    IOS_APPICON.mkdir(parents=True)
    render(svg, 1024, IOS_APPICON / "icon-1024.png")

    images = ['{"filename":"icon-1024.png","idiom":"universal","platform":"ios","size":"1024x1024"}']
    if tinted is not None:
        # Apple requires the tinted appearance to be opaque greyscale on black; the
        # system maps its luminance onto the user's tint. No dark appearance is shipped:
        # the default artwork is already near-black by design, and a dark variant would
        # have to drop its background for a generic system gradient.
        render(tinted, 1024, IOS_APPICON / "icon-1024-tinted.png")
        images.append(
            '{"appearances":[{"appearance":"luminosity","value":"tinted"}],'
            '"filename":"icon-1024-tinted.png","idiom":"universal","platform":"ios",'
            '"size":"1024x1024"}'
        )

    (IOS_APPICON / "Contents.json").write_text(
        '{\n  "images" : [\n    ' + ",\n    ".join(images) +
        '\n  ],\n  "info" : {\n    "author" : "xcode",\n    "version" : 1\n  }\n}\n'
    )


def write_android(svg: str, foreground: str) -> None:
    shutil.rmtree(ANDROID_STAGE, ignore_errors=True)
    framed, framed_foreground = reframe_for_android(svg), reframe_for_android(foreground)
    for density, size in ANDROID_LEGACY.items():
        render(framed, size, ANDROID_STAGE / f"mipmap-{density}/ic_launcher.png")
    for density, size in ANDROID_ADAPTIVE.items():
        render(framed_foreground, size,
               ANDROID_STAGE / f"mipmap-{density}/ic_launcher_foreground.png", transparent=True)
    # Play Store listing art is shown uncropped, so it keeps the original full-bleed framing.
    render(svg, 512, ANDROID_STAGE / "play-store-512.png")

    (ANDROID_STAGE / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    for name in ("ic_launcher", "ic_launcher_round"):
        (ANDROID_STAGE / "mipmap-anydpi-v26" / f"{name}.xml").write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background" />\n'
            '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
            '</adaptive-icon>\n'
        )
    (ANDROID_STAGE / "values").mkdir(parents=True, exist_ok=True)
    (ANDROID_STAGE / "values/ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{BACKGROUND_COLOUR}</color>\n</resources>\n'
    )
    (ANDROID_STAGE / "README.md").write_text(
        "# Android launcher icon — staged\n\n"
        "Generated by `art/icon/generate.py`. There is no Android application module yet\n"
        "(`:client:shell` is an Android *library*), so these are not wired into a build.\n\n"
        "When the app module lands, copy `mipmap-*/` and `values/` into its `src/main/res/`\n"
        "and point `android:icon`/`android:roundIcon` at `@mipmap/ic_launcher`.\n\n"
        "The foreground is the artwork on transparency; the background is the flat\n"
        f"`{BACKGROUND_COLOUR}` the artwork was drawn against, so the two recompose exactly.\n"
    )


def main() -> int:
    if not CHROME.exists():
        print(f"error: Chrome not found at {CHROME}", file=sys.stderr)
        return 1
    if not MASTER.exists():
        print(f"error: missing master {MASTER}", file=sys.stderr)
        return 1

    svg = MASTER.read_text()
    if BACKGROUND_RECT not in svg:
        print("error: background rect not found in master — the layer split would be wrong.",
              file=sys.stderr)
        return 1
    foreground = svg.replace(BACKGROUND_RECT, "", 1)

    tinted = MASTER_TINTED.read_text() if MASTER_TINTED.exists() else None
    if tinted is None:
        print("warning: no threshold-tinted.svg — iOS tinted appearance will be skipped")

    write_ios(svg, tinted)
    print(f"ios      -> {IOS_APPICON.relative_to(ROOT)}")
    write_icns(svg, DESKTOP_ICONS / "oltre.icns")
    write_ico(svg, DESKTOP_ICONS / "oltre.ico")
    render(svg, 512, DESKTOP_ICONS / "oltre.png")
    print(f"desktop  -> {DESKTOP_ICONS.relative_to(ROOT)}")
    write_android(svg, foreground)
    print(f"android  -> {ANDROID_STAGE.relative_to(ROOT)} (staged, no module yet)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
