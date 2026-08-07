#!/usr/bin/env python3
"""Turn Kover + JUnit XML into a per-test-kind coverage summary, and that summary into a
Markdown report with a delta against a baseline.

Two subcommands, because measuring and reporting happen at different times: `collect` runs once
per test category (right after the Gradle pass that produced the XML, before the next pass
overwrites it), `render` runs once at the end against the accumulated summary.

    coverage.py collect --category unit --kover-xml build/reports/kover/report.xml \
        --results-root . --out build/coverage/summary.json
    coverage.py render --current build/coverage/summary.json \
        --baseline build/coverage/baseline/summary.json --out build/coverage/comment.md

Deliberately dependency-free: it runs on whatever Python the runner already has.
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
        "<sub>Reporting only, no threshold gates the build. "
        "Categories are class-name suffixes; see the `test-coverage` skill.</sub>"
    )

    text = "\n".join(lines) + "\n"
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text)
    print(text)
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

    render_parser = sub.add_parser("render", help="write the Markdown report")
    render_parser.add_argument("--current", required=True)
    render_parser.add_argument("--baseline")
    render_parser.add_argument("--out", required=True)
    render_parser.set_defaults(func=render)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
