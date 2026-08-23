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

  # **Wipe Kover's state between passes, or the passes contaminate each other.**
  #
  # The report filters in the root `build.gradle.kts` are *per category* — the unit pass drops
  # composables, the screenshot pass drops `core` and every non-drawing layer — and they are the
  # reason those two rows measure what they claim to. What a pass must therefore never do is read
  # anything an earlier pass produced. Two things have to be deleted for that, and a third has to
  # be switched off; all three are here because each of them has failed on its own.
  #
  # `*/build/kover` holds the per-module artifacts and the `.ic` binary reports. Deleting it makes
  # every pass rebuild under its own filters and genuinely re-run its tests. `build/reports/kover`
  # holds the XML this script then reads: it is one file overwritten five times, so a root
  # `koverXmlReport` that was ever UP-TO-DATE would hand the collector the *previous* pass's report
  # with nothing to say it had.
  #
  # **Deleting an output does not change a cache key, which is the part that took a while.** PR #65
  # measured 83.6% and then 84.6% on one unchanged commit against a `main` baseline of 85.6%, and
  # the deletion above was the fix; it is not sufficient. With `org.gradle.caching=true`, Gradle
  # answers a deleted output by restoring it **from the build cache** rather than by re-running the
  # task — and a `Test` task's `.ic` is one of its outputs. So a pass can be handed binary coverage
  # recorded under a *different* pass's test filter, and the report it builds is filtered correctly
  # over the wrong data. PR #104 is where that showed: 25 of 28 test tasks came back `FROM-CACHE` in
  # every pass, against zero cache hits in the `main` run that set the baseline they were compared
  # to, and the screenshot row came out a point low with `core` — excluded by name in that pass —
  # somehow listed at 68%. The same commit measured on a machine with a cold cache passed.
  #
  # `--no-build-cache` is therefore not belt-and-braces: **a measurement whose result depends on
  # what happened to be in a cache is not a measurement.** It costs this job roughly nine minutes
  # and buys the only property that matters here, which is that a developer's machine and CI
  # measure the same commit the same way. The configuration cache is deliberately left on:
  # `testCategory` is read through `providers.gradleProperty`, which is a tracked input, so it is
  # already invalidated at every pass boundary — the log says so, and turning it off would buy five
  # configuration phases and nothing else.
  find . -type d -path '*/build/kover' -prune -exec rm -rf {} +
  rm -rf build/reports/kover

  # Built as one always-non-empty array: expanding an empty one under `set -u` is an error on
  # the bash 3.2 that ships with macOS, and this script should run on Davide's machine too.
  args=("${ROBORAZZI_OFF[@]}")
  [ "${category}" = "all" ] || args+=(-Poltre.testCategory="${category}")
  ./gradlew koverXmlReport --no-build-cache "${args[@]}"

  python3 .github/scripts/coverage.py collect \
    --category "${category}" \
    --kover-xml "${KOVER_XML}" \
    --results-root . \
    --out "${SUMMARY}" \
    --ref "${REF}"

  # **The evidence, kept.** The five passes write one `report.xml` in turn, so by the time a number
  # is disputed the report behind it has been overwritten four times — which is why PR #104's
  # screenshot row took five investigators to *not* explain. Copying each pass's XML out costs
  # nothing and turns the next occurrence into one look at a file. CI uploads these as an artifact;
  # see the Coverage job in `ci.yml`.
  cp "${KOVER_XML}" "build/coverage/report-${category}.xml"

  echo "::endgroup::"
done

echo "Wrote ${SUMMARY}"
