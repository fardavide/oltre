#!/usr/bin/env bash
# Measure line/branch coverage once per test category, and once for everything together.
#
# Five Gradle passes, not one: coverage is a property of the tests that ran, so the only way to
# say what the *behaviour* tests reach — as opposed to what the whole suite reaches — is to run
# them alone and read the report. `-Poltre.testCategory` filters every Test task in the build
# (see the root build.gradle.kts); Kover then reports on whatever executed.
#
# Each pass overwrites the same report.xml, so the summary is folded in between passes rather
# than at the end.
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

SUMMARY="build/coverage/summary.json"
KOVER_XML="build/reports/kover/report.xml"
REF="${GITHUB_REF_NAME:-local}"

rm -rf build/coverage
mkdir -p build/coverage

# Roborazzi stays off in every pass. This job measures what the screenshot tests *reach*; the
# "Screenshot tests" job owns whether the baselines still match, and one cause failing two jobs
# tells nobody anything the first failure did not.
ROBORAZZI_OFF=(
  -Proborazzi.test.record=false
  -Proborazzi.test.verify=false
  -Proborazzi.test.compare=false
)

# "all" runs first so that the expensive compilation is out of the way and the four filtered
# passes only re-run tests.
for category in all unit integration screenshot behaviour; do
  echo "::group::Coverage — ${category}"

  # **Wipe Kover's per-module state between passes, or the passes contaminate each other.**
  #
  # The report filters in the root `build.gradle.kts` are *per category* — the unit pass drops
  # composables, the screenshot pass drops `core` and every non-drawing layer — and they are the
  # reason those two rows measure what they claim to. But `-Poltre.testCategory` is not an input to
  # Kover's `koverGenerateArtifact` tasks, so an artifact produced by an earlier pass is UP-TO-DATE
  # in a later one and gets reused **with the earlier pass's filters baked into it**. The `all` pass
  # runs first and applies no filters at all, so what it leaves behind is precisely the unfiltered
  # artifact the two filtered passes must not read.
  #
  # It does not fail loudly, which is what makes it worth this much comment: the report is still
  # produced and still looks reasonable, only with `core`'s 1,611 lines silently back in the
  # screenshot denominator — the row reads 61% instead of 85.6% and nothing says why. Whether it
  # happens comes down to incidental up-to-dateness, so it differs between a fresh CI workspace and
  # a warm one, and between two runs of the same commit: PR #65 measured 83.6% and then 84.6% on
  # one unchanged commit, against a `main` baseline of 85.6%, and the gate blocked a merge over a
  # number that was never about the code.
  #
  # Deleting the directory makes every pass rebuild its own artifacts under its own filters. The
  # `.ic` binary reports go with it, so each pass genuinely re-runs its tests rather than restoring
  # a cached result — which is the point, and costs little because every pass ran them anyway.
  find . -type d -path '*/build/kover' -prune -exec rm -rf {} +

  # Built as one always-non-empty array: expanding an empty one under `set -u` is an error on
  # the bash 3.2 that ships with macOS, and this script should run on Davide's machine too.
  args=("${ROBORAZZI_OFF[@]}")
  [ "${category}" = "all" ] || args+=(-Poltre.testCategory="${category}")
  ./gradlew koverXmlReport "${args[@]}"

  python3 .github/scripts/coverage.py collect \
    --category "${category}" \
    --kover-xml "${KOVER_XML}" \
    --results-root . \
    --out "${SUMMARY}" \
    --ref "${REF}"

  echo "::endgroup::"
done

echo "Wrote ${SUMMARY}"
