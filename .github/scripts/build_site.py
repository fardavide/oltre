#!/usr/bin/env python3
"""Render the landing page at oltre.space from the release that is actually published.

    gh release view --json tagName,publishedAt,url,assets > release.json
    build_site.py build --repo-root . --release release.json --out build/site

The page has one job — hand somebody the game — so everything on it is derived rather than
typed. The version, the date, the APK link and its size come from the published release; the
notes come from the README changelog entry for that same version, which is the source every
release body is already built from. Nothing here holds a second copy of anything.

**The release is the input rather than the version catalogue**, and that is the whole safety
argument. `main` carries the next version the moment the bump merges, but the APK for it does not
exist until `release-android.yml` has finished building and uploading one — so a page built from
the catalogue would advertise a download that 404s for the twenty minutes in between. Asking
GitHub what is published cannot describe a release that is not there.

The other way the page can break is an image that does not load, which is the same defect wearing
a different hat. Every local file the markup names is copied in from `ASSETS` below, and a
reference the build did not satisfy fails the build.

Deliberately dependency-free — it runs on whatever Python the runner already has. Its arithmetic
is tested in `test_build_site.py`, which runs in the Coverage job alongside the other two.
"""

from __future__ import annotations

import argparse
import html as html_module
import importlib.util
import json
import re
import shutil
import sys
from datetime import datetime
from pathlib import Path

# The changelog parser lives in the release script, because the release body and this page are
# the same words in two places. Loaded by path rather than imported, for the same reason the
# tests do it: an `import` would be at the mercy of whatever else is on the runner's path.
_spec = importlib.util.spec_from_file_location(
    "oltre_android_release", Path(__file__).parent / "android_release.py"
)
android_release = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(android_release)

REPO_URL = "https://github.com/fardavide/oltre"
SITE_URL = "https://oltre.space/"

# What the page draws, and where it comes from. The screenshots are the committed Roborazzi
# baselines — the same images the README shows — so the site is never a hand-made mock-up of a
# screen the app no longer has. Rename a baseline and this build fails rather than the page.
ASSETS = {
    "icon.svg": "art/icon/threshold.svg",
    "icon.png": "art/icon/android/play-store-512.png",
    "shots/colony.png": "client/colony/ui/src/desktopTest/screenshots/colony_screen_watching_phone.png",
    "shots/galaxy.png": "client/galaxy/presentation/src/desktopTest/screenshots/galaxy_ledger.png",
    "shots/fleets.png": "client/fleets/ui/src/desktopTest/screenshots/fleets_three_runs.png",
    "shots/research.png": "client/research/ui/src/desktopTest/screenshots/research_watching_phone.png",
}

MONTHS = (
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

_TOKEN = re.compile(r"\{\{\s*([a-z_]+)\s*\}\}")
_REFERENCE = re.compile(r'(?:src|href)="([^"]*)"')
_CODE_SPAN = re.compile(r"`([^`]+)`")
_BOLD = re.compile(r"\*\*(.+?)\*\*")
_ITALIC = re.compile(r"\*([^*]+)\*")


def human_size(byte_count: int) -> str:
    """The size of a download, at the precision the person choosing to start it cares about."""
    if byte_count >= 1_000_000:
        return f"{byte_count / 1_000_000:.1f} MB"
    if byte_count >= 1_000:
        return f"{byte_count / 1_000:.0f} kB"
    return f"{byte_count} B"


def _published(published_at: str) -> datetime:
    return datetime.strptime(published_at, "%Y-%m-%dT%H:%M:%SZ")


def date_iso(published_at: str) -> str:
    return _published(published_at).strftime("%Y-%m-%d")


def date_human(published_at: str) -> str:
    """The release date in English, spelled from a table.

    `%B` asks the runner's locale what the month is called, and a runner set to anything but
    English would publish a page that changes language mid-sentence.
    """
    when = _published(published_at)
    return f"{when.day} {MONTHS[when.month - 1]} {when.year}"


def apk_asset(release: dict) -> dict:
    """The one APK attached to the release.

    Not `assets[0]`: the release is allowed to grow attachments, and the download button is the
    only reason anybody comes to this page. Zero is a broken button and two is a guess, so both
    stop the build.
    """
    apks = [asset for asset in release.get("assets", []) if asset["name"].endswith(".apk")]
    if len(apks) != 1:
        raise ValueError(
            f"Expected exactly one APK on {release.get('tagName')}, found {len(apks)}"
        )
    return apks[0]


def version_of(tag: str) -> str:
    match = re.fullmatch(r"v(\d+\.\d+\.\d+)", tag)
    if match is None:
        raise ValueError(f"Not a release tag: {tag}")
    return match.group(1)


def bullets(entry: str) -> list[str]:
    """A changelog entry split into its notes, each one line however it was wrapped.

    The README wraps at 100 columns, which is a fact about the file rather than about the note.
    Anything that is neither a bullet nor a continuation of one raises: this renderer draws one
    shape, and silently dropping the rest is the failure nobody would notice.
    """
    notes: list[str] = []
    for line in entry.splitlines():
        if not line.strip():
            continue
        if line.startswith("- "):
            notes.append(line[2:].strip())
        elif line.startswith("  ") and notes:
            notes[-1] += " " + line.strip()
        else:
            raise ValueError(f"Unexpected line in a changelog entry: {line!r}")
    return notes


def inline(text: str) -> str:
    """The changelog's inline markdown as HTML: bold, italics and code spans, nothing else.

    Escaped first, so nothing the README says can become markup, and code spans are lifted out
    before the emphasis passes run — a span quotes what the game draws, and the game draws
    asterisks sometimes.
    """
    escaped = html_module.escape(text, quote=False)

    spans: list[str] = []

    def keep(match: re.Match[str]) -> str:
        spans.append(match.group(1))
        return f"\x00{len(spans) - 1}\x00"

    marked = _CODE_SPAN.sub(keep, escaped)
    marked = _BOLD.sub(r"<strong>\1</strong>", marked)
    marked = _ITALIC.sub(r"<em>\1</em>", marked)
    for index, span in enumerate(spans):
        marked = marked.replace(f"\x00{index}\x00", f"<code>{span}</code>")
    return marked


def render_notes(entry: str) -> str:
    items = "\n".join(f"          <li>{inline(note)}</li>" for note in bullets(entry))
    return f'<ul class="notes">\n{items}\n        </ul>'


def fill(template: str, fields: dict[str, str]) -> str:
    """The template with every `{{field}}` replaced, and neither side allowed to drift.

    A field the template still names is a page that ships `{{apk_url}}` as text; a field nothing
    names is a value that was quietly renamed and is no longer on the page at all. Both are the
    kind of mistake you find by looking at the deployed site, which is too late.
    """
    named = set(_TOKEN.findall(template))
    rendered = _TOKEN.sub(lambda match: fields.get(match.group(1), match.group(0)), template)

    unfilled = sorted(set(_TOKEN.findall(rendered)))
    if unfilled:
        raise ValueError(f"The template names fields the build did not supply: {unfilled}")

    unused = sorted(set(fields) - named)
    if unused:
        raise ValueError(f"The build supplied fields the template does not use: {unused}")
    return rendered


def local_refs(page: str) -> set[str]:
    """Every file the page expects to find beside it. Links that leave the site are not ours."""
    return {
        reference
        for reference in _REFERENCE.findall(page)
        if reference
        and not reference.startswith(("http://", "https://", "//", "#", "mailto:"))
    }


def build(repo_root: Path, release: dict, out: Path) -> None:
    """Write the whole site into `out`, replacing anything already there.

    The output is pushed to `gh-pages` wholesale, so a file left behind by an earlier build
    would be published for as long as the site lives.
    """
    version = version_of(release["tagName"])
    apk = apk_asset(release)
    template = (repo_root / "site" / "index.html").read_text(encoding="utf-8")
    readme = (repo_root / "README.md").read_text(encoding="utf-8")

    page = fill(
        template,
        {
            "version": version,
            "tag": release["tagName"],
            "date": date_human(release["publishedAt"]),
            "date_iso": date_iso(release["publishedAt"]),
            "apk_url": apk["url"],
            "apk_size": human_size(apk["size"]),
            "changelog": render_notes(android_release.release_notes(readme, version)),
            "releases_url": f"{REPO_URL}/releases",
            "repo_url": REPO_URL,
            "site_url": SITE_URL,
        },
    )

    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    static = repo_root / "site" / "static"
    for source in sorted(static.iterdir()):
        shutil.copy2(source, out / source.name)

    for reference, source in ASSETS.items():
        destination = out / reference
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(repo_root / source, destination)

    missing = sorted(reference for reference in local_refs(page) if not (out / reference).exists())
    if missing:
        raise ValueError(f"The page names files the build did not write: {missing}")

    (out / "index.html").write_text(page, encoding="utf-8")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    build_parser = subparsers.add_parser("build", help="Render the site into a directory")
    build_parser.add_argument("--repo-root", type=Path, default=Path("."))
    build_parser.add_argument("--release", type=Path, required=True)
    build_parser.add_argument("--out", type=Path, required=True)

    args = parser.parse_args(argv)

    release = json.loads(args.release.read_text(encoding="utf-8"))
    build(args.repo_root, release, args.out)
    print(f"Built {release['tagName']} into {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
