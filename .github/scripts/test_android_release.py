#!/usr/bin/env python3
"""Tests for the Android release plan.

The plan decides what gets published and under which tag, and a wrong answer is not a red
build — it is a release with the wrong label, or an APK that installs over nothing. A published
release cannot be un-published, so the arithmetic is verified before it runs, in the same job
that runs it.

`android_release.py` is loaded by path for the same reason `coverage.py` is: an `import` would
be at the mercy of whatever else is on the runner's path.
"""

from __future__ import annotations

import importlib.util
from pathlib import Path

import pytest

_spec = importlib.util.spec_from_file_location(
    "oltre_android_release", Path(__file__).parent / "android_release.py"
)
android_release = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(android_release)


CATALOG = """\
[versions]
# Project version — single source, referenced by every module and the changelog.
oltre = "0.2.0"

agp = "9.3.1"
android-compileSdk = "36"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
"""

README = """\
# Oltre

Some prose about the game.

## Changelog

### 0.2.0 — 2026-08-09

- **Oltre installs on Android.** The APK is attached to every release.
- A second line of the same entry.

### 0.1.1 — 2026-08-08

- **Crystal accrues half again as fast.**
"""


class TestReadVersion:
    def test_when_the_catalogue_names_a_version_then_it_is_read(self):
        # when
        version = android_release.read_version(CATALOG)
        # then
        assert version == "0.2.0"

    def test_given_a_version_key_outside_the_versions_table_when_reading_then_it_is_ignored(self):
        # given — a library happens to be called `oltre` too. Only `[versions]` names the project.
        catalog = '[versions]\noltre = "0.2.0"\n\n[libraries]\noltre = "9.9.9"\n'
        # when
        version = android_release.read_version(catalog)
        # then
        assert version == "0.2.0"

    def test_given_a_key_that_merely_starts_with_oltre_when_reading_then_it_is_not_mistaken(self):
        # given
        catalog = '[versions]\noltre-plugin = "9.9.9"\noltre = "0.2.0"\n'
        # when
        version = android_release.read_version(catalog)
        # then
        assert version == "0.2.0"

    def test_given_no_version_at_all_when_reading_then_it_raises(self):
        # when / then
        with pytest.raises(ValueError, match="oltre"):
            android_release.read_version('[versions]\nagp = "9.3.1"\n')


class TestNames:
    def test_when_naming_the_tag_then_it_is_the_version_with_a_v(self):
        assert android_release.tag_name("0.2.0") == "v0.2.0"

    def test_when_naming_the_asset_then_it_carries_the_version(self):
        # The file lands in a phone's Downloads folder next to everything else ever downloaded,
        # so it says what it is and which build it is.
        assert android_release.asset_name("0.2.0") == "oltre-0.2.0.apk"

    def test_when_naming_the_release_then_it_reads_as_a_version_of_the_game(self):
        assert android_release.release_title("0.2.0") == "Oltre 0.2.0"


class TestReleaseNotes:
    def test_when_the_changelog_has_the_version_then_its_entry_is_the_notes(self):
        # when
        notes = android_release.release_notes(README, "0.2.0")
        # then
        assert notes == (
            "- **Oltre installs on Android.** The APK is attached to every release.\n"
            "- A second line of the same entry."
        )

    def test_given_an_older_version_when_reading_the_notes_then_the_right_entry_is_found(self):
        # given — the changelog is newest-first, so the wanted entry is not the first one.
        # when
        notes = android_release.release_notes(README, "0.1.1")
        # then
        assert notes == "- **Crystal accrues half again as fast.**"

    def test_given_the_last_entry_in_the_file_when_reading_the_notes_then_it_ends_cleanly(self):
        # given — nothing follows the entry, so the scan has to stop at the end of the file
        # rather than at the next heading.
        readme = "## Changelog\n\n### 0.2.0 — 2026-08-09\n\n- Only entry.\n"
        # when
        notes = android_release.release_notes(readme, "0.2.0")
        # then
        assert notes == "- Only entry."

    def test_given_a_following_top_level_heading_when_reading_the_notes_then_it_stops_there(self):
        # given — a section after the changelog must not be swept into the release body.
        readme = (
            "## Changelog\n\n### 0.2.0 — 2026-08-09\n\n- Only entry.\n\n## Licence\n\nMIT.\n"
        )
        # when
        notes = android_release.release_notes(readme, "0.2.0")
        # then
        assert notes == "- Only entry."

    def test_given_no_entry_for_the_version_when_reading_the_notes_then_it_raises(self):
        # The versioning convention says every bump carries a changelog entry. Publishing is
        # where that stops being a convention: a release with no entry says nothing to the
        # person deciding whether to install it, so the release does not happen.
        with pytest.raises(ValueError, match="0.9.9"):
            android_release.release_notes(README, "0.9.9")


class TestPlan:
    def test_when_planning_then_every_name_the_workflow_needs_is_decided_in_one_place(self):
        # when
        plan = android_release.plan(CATALOG, README)
        # then
        assert plan["version"] == "0.2.0"
        assert plan["tag"] == "v0.2.0"
        assert plan["asset"] == "oltre-0.2.0.apk"
        assert plan["title"] == "Oltre 0.2.0"

    def test_when_planning_then_the_body_carries_the_changelog_and_how_to_install_it(self):
        # when
        body = android_release.plan(CATALOG, README)["body"]
        # then
        assert "- **Oltre installs on Android.**" in body
        assert "Android 8.0" in body

    def test_when_planning_then_the_body_says_which_commit_it_was_built_from(self):
        # A sideloaded APK has no build metadata anyone can read back, so the release page is
        # the only place the commit is recorded.
        # when
        body = android_release.plan(CATALOG, README, commit="abc1234")["body"]
        # then
        assert "abc1234" in body
