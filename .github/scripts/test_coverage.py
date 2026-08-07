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


class TestRequiredCoverage:
    def test_when_the_baseline_is_below_the_floor_then_the_baseline_is_required(self):
        # when
        required = coverage.required_coverage(baseline=90.0, floor=95.0)
        # then
        assert required == 90.0

    def test_when_the_baseline_is_above_the_floor_then_the_floor_is_required(self):
        # when
        required = coverage.required_coverage(baseline=98.0, floor=95.0)
        # then
        assert required == 95.0

    def test_given_no_baseline_when_asking_what_is_required_then_nothing_is(self):
        # when
        required = coverage.required_coverage(baseline=None, floor=95.0)
        # then
        assert required is None


class TestGateVerdict:
    def test_when_coverage_rises_then_it_passes(self):
        # when
        verdict = coverage.gate_verdict(current=95.4, baseline=94.9, floor=95.0)
        # then
        assert verdict["status"] == "pass"

    def test_when_coverage_holds_exactly_at_the_baseline_then_it_passes(self):
        # when
        verdict = coverage.gate_verdict(current=94.9, baseline=94.9, floor=95.0)
        # then
        assert verdict["status"] == "pass"

    def test_when_coverage_falls_below_a_baseline_under_the_floor_then_it_fails(self):
        # when
        verdict = coverage.gate_verdict(current=94.5, baseline=94.9, floor=95.0)
        # then
        assert verdict["status"] == "fail"
        assert verdict["required"] == 94.9

    def test_given_a_baseline_above_the_floor_when_coverage_falls_toward_it_then_it_passes(self):
        # when
        verdict = coverage.gate_verdict(current=96.0, baseline=98.0, floor=95.0)
        # then
        assert verdict["status"] == "pass"
        assert verdict["required"] == 95.0

    def test_given_a_baseline_above_the_floor_when_coverage_falls_through_it_then_it_fails(self):
        # when
        verdict = coverage.gate_verdict(current=94.0, baseline=98.0, floor=95.0)
        # then
        assert verdict["status"] == "fail"
        assert verdict["required"] == 95.0

    def test_when_coverage_lands_exactly_on_the_floor_then_it_passes(self):
        # when
        verdict = coverage.gate_verdict(current=95.0, baseline=98.0, floor=95.0)
        # then
        assert verdict["status"] == "pass"

    def test_when_the_drop_is_finer_than_the_report_prints_then_it_passes(self):
        # given — the table would render both of these as 94.9% and the delta as "±0"
        # when
        verdict = coverage.gate_verdict(current=94.88, baseline=94.90, floor=95.0)
        # then
        assert verdict["status"] == "pass"

    def test_given_no_baseline_when_judging_then_it_is_skipped(self):
        # when
        verdict = coverage.gate_verdict(current=94.9, baseline=None, floor=95.0)
        # then
        assert verdict["status"] == "skipped"
        assert verdict["required"] is None

    def test_given_nothing_to_cover_when_judging_then_it_is_skipped(self):
        # when
        verdict = coverage.gate_verdict(current=None, baseline=94.9, floor=95.0)
        # then
        assert verdict["status"] == "skipped"

    def test_when_judging_then_the_verdict_carries_both_numbers(self):
        # when
        verdict = coverage.gate_verdict(current=94.5, baseline=94.9, floor=95.0)
        # then
        assert verdict["current"] == 94.5
        assert verdict["baseline"] == 94.9
        assert verdict["floor"] == 95.0


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

    def test_when_coverage_regresses_then_the_comment_names_the_number_to_clear(self, tmp_path):
        # when
        text, _ = self._render(
            tmp_path, current=_summary(covered=940, missed=60), baseline=_summary(950, 50)
        )
        # then
        assert "95.0%" in text

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
            tmp_path, {"status": "fail", "current": 94.5, "baseline": 94.9, "required": 94.9}
        )
        # then
        assert code == 1

    def test_when_the_verdict_is_a_pass_then_it_exits_zero(self, tmp_path):
        # when
        code = self._enforce(
            tmp_path, {"status": "pass", "current": 95.4, "baseline": 94.9, "required": 94.9}
        )
        # then
        assert code == 0

    def test_when_the_verdict_is_skipped_then_it_exits_zero(self, tmp_path):
        # when
        code = self._enforce(
            tmp_path, {"status": "skipped", "current": 94.9, "baseline": None, "required": None}
        )
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
