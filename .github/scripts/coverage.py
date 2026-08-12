#!/usr/bin/env python3
"""Turn Kover + JUnit XML into a per-test-kind coverage summary, that summary into a Markdown
report with a delta against a baseline, and that delta into a merge verdict.

Three subcommands, because measuring, reporting and gating happen at different times: `collect`
runs once per test category (right after the Gradle pass that produced the XML, before the next
pass overwrites it), `render` runs once at the end against the accumulated summary, and `enforce`
runs last of all — after the comment is posted, so a blocked PR carries the reason why.

    coverage.py collect --category unit --kover-xml build/reports/kover/report.xml \
        --results-root . --out build/coverage/summary.json
    coverage.py render --current build/coverage/summary.json \
        --baseline build/coverage/baseline/summary.json --out build/coverage/comment.md \
        --verdict-out build/coverage/verdict.json
    coverage.py enforce --verdict build/coverage/verdict.json

Deliberately dependency-free: it runs on whatever Python the runner already has. Its gate
arithmetic is tested in `test_coverage.py`, which needs pytest and runs in the same CI job.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Order is the report's order, and "all" is last because it is the summary line.
CATEGORIES = ["unit", "integration", "screenshot", "behaviour", "all"]

LABELS = {
    "unit": "Unit",
    "integration": "Integration",
    "screenshot": "Screenshot",
    "behaviour": "Behaviour",
    "all": "All tests",
}

# Kover emits JaCoCo's counter vocabulary. INSTRUCTION is too fine to read and CLASS too coarse
# to move, so the report shows LINE and BRANCH and keeps the rest in the JSON for later.
COUNTERS = ["LINE", "BRANCH", "INSTRUCTION", "METHOD", "CLASS"]

COMMENT_MARKER = "<!-- oltre-coverage-report -->"

# The columns the gate judges. Both of the table's coverage columns — a test count is not a
# coverage value, and the per-package table is a diagnostic rather than a bar (a package that is
# new, deleted or renamed moves cells with no regression behind it; the totals catch what matters).
GATED_COUNTERS = ["line", "branch"]

# The gate judges to the precision the table prints. Without this a 0.01-point drop would fail a
# PR whose own report shows the delta as "±0" — the same tolerance `format_delta` uses to decide
# a number has not moved, so the verdict can never contradict the row above it.
GATE_EPSILON = 0.05


# --- collect ---------------------------------------------------------------------------------


def read_counters(element: ET.Element) -> dict:
    """Counters that are *direct* children of the element — nested packages carry their own."""
    counters = {}
    for counter in element.findall("counter"):
        kind = counter.get("type", "")
        if kind not in COUNTERS:
            continue
        counters[kind.lower()] = {
            "covered": int(counter.get("covered", 0)),
            "missed": int(counter.get("missed", 0)),
        }
    return counters


def read_kover_xml(path: Path) -> dict:
    """Report-level totals plus a per-package breakdown, or empty if the report is not there.

    A missing report is a real outcome, not a crash: a category with no tests at all produces
    no coverage, and the report should say 0% rather than fail the job.
    """
    if not path.is_file():
        return {"totals": {}, "packages": {}}
    root = ET.parse(path).getroot()
    packages = {}
    for package in root.findall("package"):
        name = (package.get("name") or "").replace("/", ".")
        if not name:
            continue
        packages[name] = read_counters(package)
    return {"totals": read_counters(root), "packages": packages}


def count_tests(results_root: Path) -> dict:
    """Sum every JUnit XML Gradle wrote under `**/build/test-results/`.

    Gradle wipes a Test task's results directory at the start of each run, so what is on disk
    after a filtered pass is that pass and nothing else.
    """
    total = skipped = failed = 0
    for xml in results_root.glob("**/build/test-results/**/TEST-*.xml"):
        try:
            root = ET.parse(xml).getroot()
        except ET.ParseError:
            # A half-written file from a crashed worker tells us nothing; the run it belongs to
            # will have failed the job on its own.
            continue
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            total += int(suite.get("tests", 0))
            skipped += int(suite.get("skipped", 0))
            failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
    return {"total": total, "skipped": skipped, "failed": failed}


def collect(args: argparse.Namespace) -> int:
    out = Path(args.out)
    summary = json.loads(out.read_text()) if out.is_file() else {"categories": {}, "packages": {}}

    report = read_kover_xml(Path(args.kover_xml))
    entry = dict(report["totals"])
    entry["tests"] = count_tests(Path(args.results_root))

    summary.setdefault("categories", {})[args.category] = entry
    summary.setdefault("packages", {})[args.category] = report["packages"]
    if args.commit:
        summary["commit"] = args.commit
    if args.ref:
        summary["ref"] = args.ref

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n")

    line = percent(entry.get("line"))
    shown = "n/a" if line is None else f"{line:.1f}%"
    print(f"{args.category}: line {shown}, {entry['tests']['total']} tests")
    return 0


# --- render ----------------------------------------------------------------------------------


def percent(counter: dict | None) -> float | None:
    """None when there is nothing to cover — which is not the same as 0% and must not read
    as it. `:client:design:core` has no branches; that is a dash, not a failing grade."""
    if not counter:
        return None
    total = counter["covered"] + counter["missed"]
    if total == 0:
        return None
    return 100.0 * counter["covered"] / total


def format_delta(current: float | None, baseline: float | None, unit: str = "") -> str:
    if current is None or baseline is None:
        return ""
    diff = current - baseline
    if abs(diff) < 0.05:
        return " ±0"
    arrow = "▲" if diff > 0 else "▼"
    return f" {arrow} {diff:+.1f}{unit}"


def format_count_delta(current: int, baseline: int | None) -> str:
    if baseline is None:
        return ""
    diff = current - baseline
    if diff == 0:
        return " ±0"
    return f" {'▲' if diff > 0 else '▼'} {diff:+d}"


def format_cell(counter: dict | None, base_counter: dict | None) -> str:
    value = percent(counter)
    if value is None:
        return "—"
    return f"{value:.1f}%{format_delta(value, percent(base_counter))}"


def category_rows(current: dict, baseline: dict) -> list[str]:
    rows = []
    for name in CATEGORIES:
        entry = current.get("categories", {}).get(name)
        if entry is None:
            continue
        base = baseline.get("categories", {}).get(name, {})
        tests = entry.get("tests", {})
        count = tests.get("total", 0)
        base_count = base.get("tests", {}).get("total") if base else None
        label = LABELS.get(name, name)
        emphasis = "**" if name == "all" else ""
        rows.append(
            f"| {emphasis}{label}{emphasis} "
            f"| {emphasis}{format_cell(entry.get('line'), base.get('line'))}{emphasis} "
            f"| {emphasis}{format_cell(entry.get('branch'), base.get('branch'))}{emphasis} "
            f"| {emphasis}{count}{format_count_delta(count, base_count)}{emphasis} |"
        )
    return rows


def package_rows(current: dict, baseline: dict) -> list[str]:
    """One row per package, one column per test kind — the table that answers "which kind of
    test is actually reaching this code", which the totals cannot."""
    packages = sorted(
        {name for by_category in current.get("packages", {}).values() for name in by_category}
    )
    rows = []
    for package in packages:
        cells = []
        for name in CATEGORIES:
            counter = current.get("packages", {}).get(name, {}).get(package, {}).get("line")
            base = baseline.get("packages", {}).get(name, {}).get(package, {}).get("line")
            value = percent(counter)
            if value is None:
                cells.append("—")
            elif name == "all":
                cells.append(f"{value:.1f}%{format_delta(value, percent(base))}")
            else:
                cells.append(f"{value:.0f}%")
        rows.append(f"| `{package}` | " + " | ".join(cells) + " |")
    return rows


def uncovered_lines(summary: dict) -> int | None:
    counter = summary.get("categories", {}).get("all", {}).get("line")
    return counter["missed"] if counter else None


# --- gate ------------------------------------------------------------------------------------
#
# Every number in the per-kind table gates the merge — line and branch, for each of the five
# rows. There is no floor and no slack: a value may rise or hold, never fall. So a PR that lifts
# the total by covering new code while a behaviour test quietly stops reaching a screen is still
# blocked, which the old single-number gate could not see.
#
# The cost is real and accepted: renaming a test from one kind to another moves two rows, and the
# PR that does it has to leave both at least where it found them.


def gate_checks(current: dict, baseline: dict) -> list[dict]:
    """One entry per table value that both runs put a number on.

    A value only one side has is not a regression and not a pass — it is unjudgeable, and left
    out entirely rather than compared against a zero it never measured. A kind measured for the
    first time joins the ratchet on the next `main` run.
    """
    checks = []
    for category in CATEGORIES:
        entry = current.get("categories", {}).get(category, {})
        base = baseline.get("categories", {}).get(category, {})
        for counter in GATED_COUNTERS:
            now, before = percent(entry.get(counter)), percent(base.get(counter))
            if now is None or before is None:
                continue
            checks.append(
                {
                    "category": category,
                    "counter": counter,
                    "label": f"{LABELS.get(category, category)} {counter}",
                    "current": now,
                    "baseline": before,
                    "status": "pass" if now >= before - GATE_EPSILON else "fail",
                }
            )
    return checks


def gate_verdict(current: dict, baseline: dict | None) -> dict:
    checks = gate_checks(current, baseline) if baseline is not None else []
    regressions = [check for check in checks if check["status"] == "fail"]
    if not checks:
        status = "skipped"
    elif regressions:
        status = "fail"
    else:
        status = "pass"
    return {"status": status, "checks": checks, "regressions": regressions}


def values(count: int) -> str:
    return "1 value" if count == 1 else f"{count} values"


def regression_lines(verdict: dict) -> list[str]:
    return [
        f"- **{check['label']}**: {check['current']:.1f}%, below the "
        f"{check['baseline']:.1f}% it held on `main`."
        for check in verdict["regressions"]
    ]


def verdict_sentence(verdict: dict) -> str:
    """What the PR comment leads with — the only part of the report anyone has to act on."""
    if verdict["status"] == "skipped":
        # Almost always a cache miss. It also covers the case where a baseline exists but shares
        # no value with this run, which is why the sentence does not promise which one it was.
        return (
            "⚠️ **The coverage gate did not run** — nothing in this run has a `main` baseline "
            "to compare against, so nothing was enforced."
        )
    if verdict["status"] == "pass":
        return (
            f"✅ **Coverage gate passed** — all {values(len(verdict['checks']))} in the table "
            f"hold at or above the last `main` run."
        )
    return "\n".join(
        [
            f"❌ **Coverage gate failed** — {values(len(verdict['regressions']))} fell below the "
            f"last `main` run:",
            "",
            *regression_lines(verdict),
            "",
            "Cover what this branch added. No number in the table may go down, whatever the "
            "others do.",
        ]
    )


def render(args: argparse.Namespace) -> int:
    current = json.loads(Path(args.current).read_text())
    baseline_path = Path(args.baseline) if args.baseline else None
    has_baseline = baseline_path is not None and baseline_path.is_file()
    baseline = json.loads(baseline_path.read_text()) if has_baseline else {}

    lines = [
        COMMENT_MARKER,
        "### Test coverage",
        "",
        "| Test kind | Line | Branch | Tests |",
        "|---|---|---|---|",
    ]
    lines += category_rows(current, baseline)
    lines.append("")

    # Directly under the table, because it is the one line that can cost someone a merge.
    verdict = gate_verdict(current=current, baseline=baseline if has_baseline else None)
    lines.append(verdict_sentence(verdict))
    lines.append("")

    missed = uncovered_lines(current)
    base_missed = uncovered_lines(baseline) if has_baseline else None
    if missed is not None:
        # Spelled out rather than arrowed: fewer uncovered lines is the good direction, and a
        # "▼" next to a number reads as a regression however it is meant.
        if base_missed is None or base_missed == missed:
            trend = ""
        elif missed < base_missed:
            trend = f" — {base_missed - missed} fewer than the baseline."
        else:
            trend = f" — {missed - base_missed} more than the baseline."
        lines.append(f"**{missed} uncovered lines** across the project{trend or '.'}")
        lines.append("")

    rows = package_rows(current, baseline)
    if rows:
        header = " | ".join(LABELS[name] for name in CATEGORIES)
        lines += [
            "<details><summary>Line coverage by package, per test kind</summary>",
            "",
            f"| Package | {header} |",
            "|---" * (len(CATEGORIES) + 1) + "|",
            *rows,
            "",
            "</details>",
            "",
        ]

    if has_baseline:
        origin = baseline.get("commit", "unknown")[:7]
        lines.append(f"Δ against `{baseline.get('ref', 'main')}` @ `{origin}`.")
    else:
        lines.append(
            "_No baseline yet — deltas appear once this workflow has run on `main`._"
        )
    lines.append("")
    lines.append(
        "<sub>No line or branch number in the table at the top may fall below the last `main` "
        "run — every row, not just the total. The per-package breakdown is a diagnostic and is "
        "not gated. Categories are class-name suffixes; see the `test-coverage` skill.</sub>"
    )

    text = "\n".join(lines) + "\n"
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text)
    print(text)

    # Written rather than returned as an exit code: the comment has to reach the PR before the
    # gate closes, so `enforce` is a separate step that runs after it.
    if args.verdict_out:
        verdict_out = Path(args.verdict_out)
        verdict_out.parent.mkdir(parents=True, exist_ok=True)
        verdict_out.write_text(json.dumps(verdict, indent=2, sort_keys=True) + "\n")
    return 0


# --- enforce ---------------------------------------------------------------------------------


def enforce(args: argparse.Namespace) -> int:
    path = Path(args.verdict)
    if not path.is_file():
        # No verdict means `render` never ran. Silence is not consent.
        print(f"No verdict at {path} — the report step did not run.", file=sys.stderr)
        return 1

    verdict = json.loads(path.read_text())
    status = verdict.get("status")
    if status == "fail":
        fallen = verdict["regressions"]
        print(
            f"Coverage gate failed: {len(fallen)} value(s) below the last `main` run.",
            file=sys.stderr,
        )
        for check in fallen:
            print(
                f"  {check['label']}: {check['current']:.1f}%, was {check['baseline']:.1f}%",
                file=sys.stderr,
            )
        return 1
    if status == "skipped":
        print("Coverage gate skipped: nothing in this run has a baseline to compare against.")
        return 0
    print(f"Coverage gate passed: all {len(verdict['checks'])} values hold.")
    return 0


# --- entry point -----------------------------------------------------------------------------


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    collect_parser = sub.add_parser("collect", help="fold one category's XML into the summary")
    collect_parser.add_argument("--category", required=True, choices=CATEGORIES)
    collect_parser.add_argument("--kover-xml", required=True)
    collect_parser.add_argument("--results-root", default=".")
    collect_parser.add_argument("--out", required=True)
    collect_parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA", ""))
    collect_parser.add_argument("--ref", default="")
    collect_parser.set_defaults(func=collect)

    render_parser = sub.add_parser("render", help="write the Markdown report and the verdict")
    render_parser.add_argument("--current", required=True)
    render_parser.add_argument("--baseline")
    render_parser.add_argument("--out", required=True)
    render_parser.add_argument("--verdict-out")
    render_parser.set_defaults(func=render)

    enforce_parser = sub.add_parser("enforce", help="exit non-zero if the gate failed")
    enforce_parser.add_argument("--verdict", required=True)
    enforce_parser.set_defaults(func=enforce)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
