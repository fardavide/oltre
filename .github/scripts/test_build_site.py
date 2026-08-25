#!/usr/bin/env python3
"""Tests for the landing page build.

The page at oltre.space is the only place a player who is not on TestFlight can get the game, so
the two things it must never do are advertise a download that 404s and draw a screenshot that
does not load. Both are checked here rather than by looking at the deployed site: a build that
cannot find one of its sources fails, and a page that references a file the build did not write
fails.

`build_site.py` is loaded by path for the same reason `coverage.py` is: an `import` would be at
the mercy of whatever else is on the runner's path.
"""

from __future__ import annotations

import importlib.util
import re
from pathlib import Path

import pytest

_spec = importlib.util.spec_from_file_location(
    "oltre_build_site", Path(__file__).parent / "build_site.py"
)
build_site = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(build_site)


README = """\
# Oltre

Some prose about the game.

## Changelog

### 0.20.1 — 2026-08-24

- **Opening the game clears the alerts it has already sent you.** Everything waiting in the tray
  is about something the colony is now showing you properly.
- A second note, with `a code span` and *an aside* in it.

### 0.20.0 — 2026-08-24

- **The galaxy is dark now.**
"""

RELEASE = {
    "tagName": "v0.20.1",
    "publishedAt": "2026-08-24T17:44:58Z",
    "url": "https://github.com/fardavide/oltre/releases/tag/v0.20.1",
    "assets": [
        {
            "name": "oltre-0.20.1.apk",
            "size": 10153027,
            "url": "https://github.com/fardavide/oltre/releases/download/v0.20.1/oltre-0.20.1.apk",
        }
    ],
}


# A stand-in for `site/index.html` that names every field the build supplies and two of the files
# it copies. It has to name all of them: the build refuses to hand a template a value it does not
# use, so a partial template here would fail for a reason that has nothing to do with the test.
TEMPLATE = """\
<link rel="canonical" href="{{site_url}}">
<link rel="icon" href="icon.svg">
<img src="shots/colony.png">
<h1>{{version}}</h1>
<time datetime="{{date_iso}}">{{date}}</time>
<a href="{{apk_url}}">{{tag}} · {{apk_size}}</a>
{{changelog}}
<a href="{{releases_url}}">every release</a>
<a href="{{repo_url}}">source</a>
"""


def fake_repo(root: Path, template: str = TEMPLATE) -> Path:
    """A repository just complete enough to build the site out of: the template, the static
    files it ships beside, the README, and an empty file at every path the asset manifest names.
    """
    (root / "site" / "static").mkdir(parents=True)
    (root / "site" / "index.html").write_text(template, encoding="utf-8")
    (root / "site" / "static" / "CNAME").write_text("oltre.space\n", encoding="utf-8")
    (root / "site" / "static" / ".nojekyll").write_text("", encoding="utf-8")
    (root / "README.md").write_text(README, encoding="utf-8")
    for source in build_site.ASSETS.values():
        path = root / source
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"x")
    return root


class TestHumanSize:
    def test_when_the_file_is_megabytes_then_it_is_read_in_megabytes(self):
        # The APK's size sits under the download button, where it answers "will this finish on
        # my data plan?" — so one decimal is the whole precision anyone wants.
        assert build_site.human_size(10153027) == "10.2 MB"

    def test_when_the_file_is_kilobytes_then_it_is_read_in_kilobytes(self):
        assert build_site.human_size(48000) == "48 kB"

    def test_when_the_file_is_tiny_then_it_is_read_in_bytes(self):
        assert build_site.human_size(512) == "512 B"


class TestDates:
    def test_when_stamping_the_release_then_the_machine_readable_date_is_the_day(self):
        assert build_site.date_iso("2026-08-24T17:44:58Z") == "2026-08-24"

    def test_when_stamping_the_release_then_the_readable_date_names_the_month(self):
        # Spelled from a table rather than through `%B`, so a runner with a different locale
        # cannot publish a page that says "24 agosto 2026" in the middle of English prose.
        assert build_site.date_human("2026-08-24T17:44:58Z") == "24 August 2026"


class TestApkAsset:
    def test_when_the_release_carries_an_apk_then_it_is_found(self):
        # when
        asset = build_site.apk_asset(RELEASE)
        # then
        assert asset["name"] == "oltre-0.20.1.apk"

    def test_given_a_release_with_no_apk_when_looking_for_one_then_it_raises(self):
        # A page whose download button points at nothing is worse than a page with no button.
        with pytest.raises(ValueError, match="APK"):
            build_site.apk_asset({"tagName": "v0.20.1", "assets": []})

    def test_given_two_apks_when_looking_for_one_then_it_raises(self):
        # Nothing here can choose between them, and guessing publishes the wrong build.
        release = {"tagName": "v0.20.1", "assets": [{"name": "a.apk"}, {"name": "b.apk"}]}
        with pytest.raises(ValueError, match="APK"):
            build_site.apk_asset(release)


class TestVersionOf:
    def test_when_reading_the_version_off_a_tag_then_the_v_is_dropped(self):
        assert build_site.version_of("v0.20.1") == "0.20.1"

    def test_given_a_tag_that_is_not_a_version_when_reading_it_then_it_raises(self):
        with pytest.raises(ValueError, match="release-2026"):
            build_site.version_of("release-2026")


class TestBullets:
    def test_when_the_entry_is_a_list_then_each_dash_starts_a_note(self):
        # when
        notes = build_site.bullets("- One.\n- Two.")
        # then
        assert notes == ["One.", "Two."]

    def test_given_a_note_that_wraps_when_splitting_then_its_lines_are_one_note(self):
        # given — the README wraps at 100 columns, which is a fact about the file rather than
        # about the note.
        entry = "- A note that runs on\n  past the margin\n- The next one."
        # when
        notes = build_site.bullets(entry)
        # then
        assert notes == ["A note that runs on past the margin", "The next one."]

    def test_given_blank_lines_when_splitting_then_they_are_ignored(self):
        assert build_site.bullets("- One.\n\n- Two.\n") == ["One.", "Two."]

    def test_given_a_line_that_is_neither_when_splitting_then_it_raises(self):
        # The renderer below understands exactly one shape. Anything else would be dropped
        # silently from the page, which is the failure nobody notices.
        with pytest.raises(ValueError, match="Unexpected"):
            build_site.bullets("- One.\nA paragraph the renderer cannot draw.")


class TestInline:
    def test_when_the_text_has_html_in_it_then_it_is_escaped(self):
        assert build_site.inline("a < b & c") == "a &lt; b &amp; c"

    def test_when_the_text_is_bold_then_it_is_strong(self):
        assert build_site.inline("**loud**") == "<strong>loud</strong>"

    def test_when_the_text_is_italic_then_it_is_emphasised(self):
        assert build_site.inline("an *aside* here") == "an <em>aside</em> here"

    def test_when_the_text_has_a_code_span_then_it_is_code(self):
        assert build_site.inline("`1–25`") == "<code>1–25</code>"

    def test_given_a_code_span_with_markup_in_it_when_rendering_then_the_markup_is_literal(self):
        # A code span quotes what the game draws, and the game draws asterisks sometimes.
        assert build_site.inline("`**x**`") == "<code>**x**</code>"

    def test_given_bold_beside_italic_when_rendering_then_neither_eats_the_other(self):
        assert build_site.inline("**a** and *b*") == "<strong>a</strong> and <em>b</em>"


class TestRenderNotes:
    def test_when_rendering_an_entry_then_it_is_a_list_the_page_can_style(self):
        # when
        html = build_site.render_notes("- **One.** Two.\n- Three.")
        # then
        assert '<ul class="notes">' in html
        assert "<li><strong>One.</strong> Two.</li>" in html
        assert "<li>Three.</li>" in html


class TestFill:
    def test_when_the_template_names_a_field_then_it_is_replaced(self):
        assert build_site.fill("<p>{{who}}</p>", {"who": "Oltre"}) == "<p>Oltre</p>"

    def test_given_a_field_the_build_did_not_supply_when_filling_then_it_raises(self):
        # A page that ships `{{apk_url}}` as text is a broken download and a broken page.
        with pytest.raises(ValueError, match="apk_url"):
            build_site.fill("<a href={{apk_url}}>", {"who": "Oltre"})

    def test_given_a_field_the_template_does_not_use_when_filling_then_it_raises(self):
        # The other direction: a renamed placeholder leaves the old value quietly unused.
        with pytest.raises(ValueError, match="stale"):
            build_site.fill("<p>{{who}}</p>", {"who": "Oltre", "stale": "x"})


class TestLocalRefs:
    def test_when_the_page_points_at_its_own_files_then_they_are_collected(self):
        # given
        html = '<img src="shots/colony.png"><link href="icon.svg">'
        # when / then
        assert build_site.local_refs(html) == {"shots/colony.png", "icon.svg"}

    def test_when_the_page_points_somewhere_else_then_it_is_not_collected(self):
        # given — absolute links leave the site and fragments never do.
        html = '<a href="https://github.com/fardavide/oltre">s</a><a href="#top">t</a>'
        # when / then
        assert build_site.local_refs(html) == set()


class TestBuild:
    def test_when_building_then_the_page_carries_the_published_release(self, tmp_path):
        # given
        root = fake_repo(tmp_path / "repo")
        # when
        build_site.build(root, RELEASE, tmp_path / "out")
        # then
        page = (tmp_path / "out" / "index.html").read_text(encoding="utf-8")
        assert "0.20.1" in page
        assert "24 August 2026" in page
        assert "10.2 MB" in page
        assert RELEASE["assets"][0]["url"] in page

    def test_when_building_then_the_notes_are_the_readme_entry_for_that_release(self, tmp_path):
        # The README is the source of every release note the project has; the site does not get
        # a second copy that can drift from it.
        # given
        root = fake_repo(tmp_path / "repo")
        # when
        build_site.build(root, RELEASE, tmp_path / "out")
        # then
        page = (tmp_path / "out" / "index.html").read_text(encoding="utf-8")
        assert "Opening the game clears the alerts" in page
        assert "The galaxy is dark now" not in page

    def test_when_building_then_the_static_files_ride_along(self, tmp_path):
        # `CNAME` is what keeps oltre.space pointed here, and `.nojekyll` is what stops Pages
        # trying to run the output through Jekyll.
        # given
        root = fake_repo(tmp_path / "repo")
        # when
        build_site.build(root, RELEASE, tmp_path / "out")
        # then
        assert (tmp_path / "out" / "CNAME").read_text(encoding="utf-8").strip() == "oltre.space"
        assert (tmp_path / "out" / ".nojekyll").exists()

    def test_when_building_then_every_asset_the_page_names_is_beside_it(self, tmp_path):
        # given
        root = fake_repo(tmp_path / "repo")
        # when
        build_site.build(root, RELEASE, tmp_path / "out")
        # then
        assert (tmp_path / "out" / "shots" / "colony.png").exists()
        assert (tmp_path / "out" / "icon.svg").exists()

    def test_given_a_moved_screenshot_when_building_then_it_raises(self, tmp_path):
        # A screenshot baseline is renamed by whoever renames the test, and nothing about that
        # change would otherwise mention this page.
        # given
        root = fake_repo(tmp_path / "repo")
        (root / next(iter(build_site.ASSETS.values()))).unlink()
        # when / then
        with pytest.raises(FileNotFoundError):
            build_site.build(root, RELEASE, tmp_path / "out")

    def test_given_a_page_naming_a_file_the_build_does_not_write_when_building_then_it_raises(
        self, tmp_path
    ):
        # given — the manifest and the markup have to agree, and only one of them is checked by
        # opening the site in a browser.
        root = fake_repo(tmp_path / "repo", TEMPLATE + '<img src="shots/nowhere.png">')
        # when / then
        with pytest.raises(ValueError, match="shots/nowhere.png"):
            build_site.build(root, RELEASE, tmp_path / "out")

    def test_given_an_existing_output_when_building_then_nothing_stale_survives(self, tmp_path):
        # The output is pushed to `gh-pages` wholesale, so a file left behind by an earlier
        # build would be published forever.
        # given
        root = fake_repo(tmp_path / "repo")
        out = tmp_path / "out"
        out.mkdir()
        (out / "ghost.html").write_text("boo", encoding="utf-8")
        # when
        build_site.build(root, RELEASE, out)
        # then
        assert not (out / "ghost.html").exists()


class TestTheRealSite:
    """The page in `site/`, built from this repository rather than from a fixture.

    Everything above tests the machinery against templates written to exercise it. This one
    catches the mistakes that only the real files can make: a placeholder renamed in the markup
    and not in the build, a screenshot baseline that moved, a changelog entry written in a shape
    the renderer cannot draw. All three are invisible until the site is deployed, which is after
    the only review anyone gives it.
    """

    def test_when_the_readme_gains_an_entry_then_the_page_can_still_draw_it(self):
        # Only the newest entry is ever on the page, so a shape the renderer cannot draw would
        # not fail until the release that needs it — days after the pull request that wrote it,
        # and on a job nobody is watching. Every entry is checked instead, on every change.
        # given
        root = Path(__file__).resolve().parents[2]
        readme = (root / "README.md").read_text(encoding="utf-8")
        versions = re.findall(r"^###\s+(\d+\.\d+\.\d+)\s+—", readme, flags=re.MULTILINE)
        assert len(versions) > 60, "the changelog should have every release in it"
        # when / then
        for version in versions:
            build_site.render_notes(build_site.android_release.release_notes(readme, version))

    def test_when_building_the_real_page_then_it_renders(self, tmp_path):
        # given — the version `main` carries, which is the one the README is guaranteed to have
        # an entry for. What is published lags it by however long a release build takes.
        root = Path(__file__).resolve().parents[2]
        version = build_site.android_release.read_version(
            (root / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
        )
        release = {
            "tagName": f"v{version}",
            "publishedAt": "2026-08-24T17:44:58Z",
            "assets": [{"name": f"oltre-{version}.apk", "size": 10153027, "url": "https://x/a.apk"}],
        }
        # when
        build_site.build(root, release, tmp_path / "out")
        # then
        page = (tmp_path / "out" / "index.html").read_text(encoding="utf-8")
        assert version in page
        assert "{{" not in page
