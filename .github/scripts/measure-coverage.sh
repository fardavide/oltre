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
