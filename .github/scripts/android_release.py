#!/usr/bin/env python3
"""Decide what an Android release is called and what it says, from the two files that already
know: the version catalogue and the README changelog.

    android_release.py plan --catalog gradle/libs.versions.toml --readme README.md \
        --commit "$GITHUB_SHA" --body-out build/release/body.md >> "$GITHUB_OUTPUT"

One subcommand, because there is one decision. It prints `key=value` lines for `$GITHUB_OUTPUT`
and writes the release body to a file, so the workflow never re-derives a name from a shell
expression — every string the release wears is decided here, where it is tested.

The changelog lookup is deliberately strict: a version with no entry raises rather than
publishing an empty release. The versioning convention already requires the entry; this is the
step that stops the requirement being optional, and it fails before the tag is created rather
than after.

Deliberately dependency-free — it runs on whatever Python the runner already has. Its
arithmetic is tested in `test_android_release.py`, which runs in the Coverage job alongside
`test_coverage.py`.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# minSdk, written the way a person reads it. Kept beside the string that quotes it so the two
# cannot drift silently — if `android-minSdk` in the catalogue moves, this line moves with it.
MIN_ANDROID_VERSION = "Android 8.0"

INSTALL_NOTE = (
    f"**Install** — {MIN_ANDROID_VERSION} or newer. Download the APK below and open it; "
    "Android asks once for permission to install from your browser. Updates install over the "
    "top, so the colony survives them."
)


def read_version(catalog: str) -> str:
    """The `oltre` version from the catalogue's `[versions]` table.

    Scoped to the table rather than matched anywhere in the file: `[libraries]` and `[plugins]`
    hold entries whose lines also start with a name and an `=`, and the project version is not
    something to find by luck.
    """
    in_versions = False
    for line in catalog.splitlines():
        stripped = line.strip()
        if stripped.startswith("["):
            in_versions = stripped == "[versions]"
            continue
        if not in_versions:
            continue
        match = re.match(r'oltre\s*=\s*"([^"]+)"', stripped)
        if match:
            return match.group(1)
    raise ValueError("No `oltre` version in the catalogue's [versions] table")


def tag_name(version: str) -> str:
    return f"v{version}"


def asset_name(version: str) -> str:
    return f"oltre-{version}.apk"


def release_title(version: str) -> str:
    return f"Oltre {version}"


def release_notes(readme: str, version: str) -> str:
    """The changelog entry for `version`, without its heading.

    The heading shape is the versioning convention's: `### <version> — <YYYY-MM-DD>`. Only the
    version is matched, so the date is free to be whatever the entry says.
    """
    lines = readme.splitlines()
    start = None
    for index, line in enumerate(lines):
        if re.match(rf"###\s+{re.escape(version)}\s*(—|-|$)", line):
            start = index + 1
            break
    if start is None:
        raise ValueError(
            f"No changelog entry for {version} in the README. Every version carries one — "
            "see the `versioning` skill."
        )

    body: list[str] = []
    for line in lines[start:]:
        # Any heading ends the entry: `###` is the next version, `##` the next section.
        if line.startswith("##"):
            break
        body.append(line)
    return "\n".join(body).strip()


def plan(catalog: str, readme: str, commit: str | None = None) -> dict[str, str]:
    version = read_version(catalog)
    body = f"{release_notes(readme, version)}\n\n---\n\n{INSTALL_NOTE}"
    if commit:
        # A sideloaded APK carries no metadata a player can read back, and the tag alone moves
        # if it is ever re-cut. The commit is the one durable answer to "what is this build?".
        body += f"\n\nBuilt from {commit}."
    return {
        "version": version,
        "tag": tag_name(version),
        "asset": asset_name(version),
        "title": release_title(version),
        "body": body,
    }


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    plan_parser = subparsers.add_parser("plan", help="Print the release's names, write its body")
    plan_parser.add_argument("--catalog", type=Path, required=True)
    plan_parser.add_argument("--readme", type=Path, required=True)
    plan_parser.add_argument("--commit", default=None)
    plan_parser.add_argument("--body-out", type=Path, required=True)

    args = parser.parse_args(argv)

    result = plan(
        args.catalog.read_text(encoding="utf-8"),
        args.readme.read_text(encoding="utf-8"),
        commit=args.commit,
    )
    args.body_out.parent.mkdir(parents=True, exist_ok=True)
    args.body_out.write_text(result.pop("body"), encoding="utf-8")
    for key, value in result.items():
        print(f"{key}={value}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
