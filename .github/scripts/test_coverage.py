#!/usr/bin/env python3
"""Tests for the coverage gate.

The gate can block a merge, so its arithmetic is the one part of this directory that has to be
verified rather than eyeballed — and it is verified by the same job it gates.

`coverage.py` is loaded by path on purpose: `import coverage` would resolve to the widely
installed PyPI package of that name on any machine that has it.
"""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import pytest

_spec = importlib.util.spec_from_file_location(
    "oltre_coverage", Path(__file__).parent / "coverage.py"
)
coverage = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(coverage)


def _counter(covered: int, missed: int) -> dict:
    return {"covered": covered, "missed": missed}


def _summary(covered: int, missed: int, commit: str = "abc1234") -> dict:
    """A summary shaped like the real one, carrying a single `all`/line counter."""
    return {
        "categories": {"all": {"line": _counter(covered, missed), "tests": {"total": 1}}},
        "packages": {"all": {}},
        "commit": commit,
        "ref": "main",
    }


def _table(**categories: dict) -> dict:
    """A summary carrying whatever per-kind counters a test names, as (covered, missed) pairs:

        _table(all={"line": (95, 5), "branch": (80, 20)}, unit={"line": (90, 10)})
    """
    return {
        "categories": {
            name: {counter: _counter(*pair) for counter, pair in counters.items()}
            for name, counters in categories.items()
        },
        "packages": {},
        "commit": "abc1234",
        "ref": "main",
    }


def _fallen(verdict: dict) -> list[tuple[str, str]]:
    return [(check["category"], check["counter"]) for check in verdict["regressions"]]


class TestGateVerdict:
    def test_when_every_value_rises_then_it_passes(self):
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (96, 4), "branch": (81, 19)}),
            baseline=_table(all={"line": (95, 5), "branch": (80, 20)}),
        )
        # then
        assert verdict["status"] == "pass"

    def test_when_every_value_holds_exactly_at_the_baseline_then_it_passes(self):
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (95, 5)}, unit={"line": (90, 10)}),
            baseline=_table(all={"line": (95, 5)}, unit={"line": (90, 10)}),
        )
        # then
        assert verdict["status"] == "pass"

    def test_when_the_total_line_number_falls_then_it_fails(self):
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (94, 6)}),
            baseline=_table(all={"line": (95, 5)}),
        )
        # then
        assert verdict["status"] == "fail"
        assert _fallen(verdict) == [("all", "line")]

    def test_when_branch_coverage_falls_while_line_coverage_holds_then_it_fails(self):
        # given — the old gate judged the line number alone and would have let this through
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (95, 5), "branch": (78, 22)}),
            baseline=_table(all={"line": (95, 5), "branch": (80, 20)}),
        )
        # then
        assert verdict["status"] == "fail"
        assert _fallen(verdict) == [("all", "branch")]

    def test_when_one_kind_falls_while_the_total_holds_then_it_fails(self):
        # given — behaviour tests stop reaching code that unit tests picked up
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (95, 5)}, behaviour={"line": (40, 60)}),
            baseline=_table(all={"line": (95, 5)}, behaviour={"line": (50, 50)}),
        )
        # then
        assert verdict["status"] == "fail"
        assert _fallen(verdict) == [("behaviour", "line")]

    def test_when_several_values_fall_then_the_verdict_names_every_one(self):
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (94, 6), "branch": (78, 22)}, unit={"line": (88, 12)}),
            baseline=_table(all={"line": (95, 5), "branch": (80, 20)}, unit={"line": (90, 10)}),
        )
        # then
        assert sorted(_fallen(verdict)) == [
            ("all", "branch"),
            ("all", "line"),
            ("unit", "line"),
        ]

    def test_when_a_value_falls_then_the_verdict_carries_both_of_its_numbers(self):
        # when
        verdict = coverage.gate_verdict(
            current=_table(unit={"line": (88, 12)}),
            baseline=_table(unit={"line": (90, 10)}),
        )
        # then
        fallen = verdict["regressions"][0]
        assert fallen["current"] == 88.0
        assert fallen["baseline"] == 90.0

    def test_when_the_drop_is_finer_than_the_report_prints_then_it_passes(self):
        # given — the table would render both of these as 94.9% and the delta as "±0"
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (9488, 512)}),
            baseline=_table(all={"line": (9490, 510)}),
        )
        # then
        assert verdict["status"] == "pass"

    def test_given_a_project_below_ninety_five_when_every_value_holds_then_it_passes(self):
        # given — there is no floor any more; holding is enough, wherever the project sits
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (60, 40)}),
            baseline=_table(all={"line": (60, 40)}),
        )
        # then
        assert verdict["status"] == "pass"

    def test_given_a_project_above_ninety_five_when_a_value_falls_then_it_fails(self):
        # given — there is no slack down to a floor any more either
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (96, 4)}),
            baseline=_table(all={"line": (98, 2)}),
        )
        # then
        assert verdict["status"] == "fail"

    def test_given_a_kind_absent_from_the_baseline_when_judging_then_it_is_not_judged(self):
        # given — the first behaviour test the project ever had has nothing to be measured against
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (95, 5)}, behaviour={"line": (40, 60)}),
            baseline=_table(all={"line": (95, 5)}),
        )
        # then
        assert verdict["status"] == "pass"
        assert ("behaviour", "line") not in [(c["category"], c["counter"]) for c in verdict["checks"]]

    def test_given_a_kind_gone_from_this_run_when_judging_then_it_is_not_judged(self):
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (95, 5)}),
            baseline=_table(all={"line": (95, 5)}, behaviour={"line": (50, 50)}),
        )
        # then
        assert verdict["status"] == "pass"

    def test_given_nothing_to_cover_when_judging_then_that_value_is_not_judged(self):
        # given — a module with no branches at all is a dash in the table, not a failing grade
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (95, 5), "branch": (0, 0)}),
            baseline=_table(all={"line": (95, 5), "branch": (80, 20)}),
        )
        # then
        assert verdict["status"] == "pass"

    def test_given_no_baseline_when_judging_then_it_is_skipped(self):
        # when
        verdict = coverage.gate_verdict(current=_table(all={"line": (95, 5)}), baseline=None)
        # then
        assert verdict["status"] == "skipped"
        assert verdict["checks"] == []

    def test_given_nothing_comparable_when_judging_then_it_is_skipped(self):
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (95, 5)}), baseline=_table(unit={"line": (90, 10)})
        )
        # then
        assert verdict["status"] == "skipped"

    def test_when_judging_then_a_held_value_is_still_reported_as_a_check(self):
        # when
        verdict = coverage.gate_verdict(
            current=_table(all={"line": (96, 4)}),
            baseline=_table(all={"line": (95, 5)}),
        )
        # then
        assert [(c["category"], c["counter"], c["status"]) for c in verdict["checks"]] == [
            ("all", "line", "pass")
        ]


class TestRender:
    def _render(self, tmp_path: Path, current: dict, baseline: dict | None) -> tuple[str, dict]:
        current_path = tmp_path / "summary.json"
        current_path.write_text(json.dumps(current))
        baseline_path = tmp_path / "baseline.json"
        if baseline is not None:
            baseline_path.write_text(json.dumps(baseline))
        comment = tmp_path / "comment.md"
        verdict = tmp_path / "verdict.json"
        exit_code = coverage.main(
            [
                "render",
                "--current", str(current_path),
                "--baseline", str(baseline_path),
                "--out", str(comment),
                "--verdict-out", str(verdict),
            ]
        )
        assert exit_code == 0
        return comment.read_text(), json.loads(verdict.read_text())

    def test_when_coverage_regresses_then_the_comment_says_the_gate_failed(self, tmp_path):
        # when
        text, verdict = self._render(
            tmp_path, current=_summary(covered=940, missed=60), baseline=_summary(950, 50)
        )
        # then
        assert verdict["status"] == "fail"
        assert "gate failed" in text.lower()

    def test_when_coverage_regresses_then_the_comment_names_the_value_that_fell(self, tmp_path):
        # when
        text, _ = self._render(
            tmp_path, current=_summary(covered=940, missed=60), baseline=_summary(950, 50)
        )
        # then
        assert "All tests line" in text
        assert "94.0%" in text
        assert "95.0%" in text

    def test_when_a_single_kind_regresses_then_the_comment_names_that_kind(self, tmp_path):
        # when
        text, verdict = self._render(
            tmp_path,
            current=_table(all={"line": (95, 5)}, unit={"line": (88, 12)}),
            baseline=_table(all={"line": (95, 5)}, unit={"line": (90, 10)}),
        )
        # then
        assert verdict["status"] == "fail"
        assert "Unit line" in text

    def test_when_rendering_then_the_footer_names_no_floor(self, tmp_path):
        # when
        text, _ = self._render(
            tmp_path, current=_summary(covered=960, missed=40), baseline=_summary(950, 50)
        )
        # then
        assert "95%" not in text

    def test_when_coverage_improves_then_the_comment_says_the_gate_passed(self, tmp_path):
        # when
        text, verdict = self._render(
            tmp_path, current=_summary(covered=960, missed=40), baseline=_summary(950, 50)
        )
        # then
        assert verdict["status"] == "pass"
        assert "gate passed" in text.lower()

    def test_given_no_baseline_when_rendering_then_the_comment_says_the_gate_was_skipped(
        self, tmp_path
    ):
        # when
        text, verdict = self._render(
            tmp_path, current=_summary(covered=940, missed=60), baseline=None
        )
        # then
        assert verdict["status"] == "skipped"
        assert "gate did not run" in text.lower()

    def test_when_rendering_then_the_old_reporting_only_note_is_gone(self, tmp_path):
        # when
        text, _ = self._render(
            tmp_path, current=_summary(covered=960, missed=40), baseline=_summary(950, 50)
        )
        # then
        assert "no threshold gates the build" not in text


class TestEnforce:
    def _enforce(self, tmp_path: Path, verdict: dict) -> int:
        path = tmp_path / "verdict.json"
        path.write_text(json.dumps(verdict))
        return coverage.main(["enforce", "--verdict", str(path)])

    def test_when_the_verdict_is_a_failure_then_it_exits_non_zero(self, tmp_path):
        # when
        code = self._enforce(
            tmp_path,
            {
                "status": "fail",
                "checks": [],
                "regressions": [
                    {
                        "category": "unit",
                        "counter": "line",
                        "label": "Unit line",
                        "current": 88.0,
                        "baseline": 90.0,
                        "status": "fail",
                    }
                ],
            },
        )
        # then
        assert code == 1

    def test_when_the_verdict_is_a_failure_then_it_prints_what_fell(self, tmp_path, capsys):
        # when
        self._enforce(
            tmp_path,
            {
                "status": "fail",
                "checks": [],
                "regressions": [
                    {
                        "category": "behaviour",
                        "counter": "branch",
                        "label": "Behaviour branch",
                        "current": 60.0,
                        "baseline": 66.0,
                        "status": "fail",
                    }
                ],
            },
        )
        # then
        assert "Behaviour branch" in capsys.readouterr().err

    def test_when_the_verdict_is_a_pass_then_it_exits_zero(self, tmp_path):
        # when
        code = self._enforce(tmp_path, {"status": "pass", "checks": [], "regressions": []})
        # then
        assert code == 0

    def test_when_the_verdict_is_skipped_then_it_exits_zero(self, tmp_path):
        # when
        code = self._enforce(tmp_path, {"status": "skipped", "checks": [], "regressions": []})
        # then
        assert code == 0

    def test_given_no_verdict_file_when_enforcing_then_it_exits_non_zero(self, tmp_path):
        # given — a missing verdict means the render step never ran; that is not a pass
        # when
        code = coverage.main(["enforce", "--verdict", str(tmp_path / "absent.json")])
        # then
        assert code == 1


if __name__ == "__main__":
    raise SystemExit(pytest.main([__file__]))
