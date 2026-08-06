#!/bin/sh
# Xcode Cloud custom build script — runs before every xcodebuild action.
#
# Pins both halves of the shipped version to their real sources, because the values checked into
# project.pbxproj are placeholders that regenerating the project is the only supported way to
# change (`xcodegen generate`, from iosApp/project.yml — never by hand).
#
#   CURRENT_PROJECT_VERSION  <- Xcode Cloud's own run number. It assigns each build a
#       monotonically increasing integer, and App Store Connect rejects a CFBundleVersion that
#       repeats within a release train (all builds sharing one CFBundleShortVersionString).
#       Without this every archive would upload as build 1 and every upload after the first
#       would be refused.
#
#   MARKETING_VERSION        <- the `oltre` version in gradle/libs.versions.toml, the repo's
#       single source for it. project.yml carries a copy for local builds, and the copy inside
#       the generated project goes stale the moment a version bump lands without a machine that
#       can run xcodegen — which is how 0.0.8 came to be labelled 0.0.7. Reading the catalogue
#       here makes that class of drift unshippable rather than merely unlikely.
#
# Info.plist declares both as $(...) references, so rewriting the build settings is enough.
# agvtool is not used: it requires VERSIONING_SYSTEM = apple-generic, which this project does
# not set, and would silently no-op.
set -eu

if [ -z "${CI_BUILD_NUMBER:-}" ]; then
    echo "note: CI_BUILD_NUMBER unset — not an Xcode Cloud build, leaving the version alone"
    exit 0
fi

REPO="${CI_PRIMARY_REPOSITORY_PATH:?}"
PBXPROJ="$REPO/iosApp/iosApp.xcodeproj/project.pbxproj"
CATALOG="$REPO/gradle/libs.versions.toml"
if [ ! -f "$PBXPROJ" ]; then
    echo "error: no project at $PBXPROJ" >&2
    exit 1
fi
if [ ! -f "$CATALOG" ]; then
    echo "error: no version catalogue at $CATALOG" >&2
    exit 1
fi

MARKETING_VERSION=$(sed -n -E 's/^oltre[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$CATALOG" | head -1)
if [ -z "$MARKETING_VERSION" ]; then
    echo "error: could not read the 'oltre' version from $CATALOG" >&2
    exit 1
fi

sed -i '' -E "s/CURRENT_PROJECT_VERSION = [0-9]+;/CURRENT_PROJECT_VERSION = ${CI_BUILD_NUMBER};/g" "$PBXPROJ"
sed -i '' -E "s/MARKETING_VERSION = [0-9][0-9.]*;/MARKETING_VERSION = ${MARKETING_VERSION};/g" "$PBXPROJ"

# A silent no-op here ships a duplicate build number or a mislabelled release, so assert both
# rewrites landed in every build configuration rather than trusting sed's exit status.
written=$(grep -c "CURRENT_PROJECT_VERSION = ${CI_BUILD_NUMBER};" "$PBXPROJ" || true)
if [ "$written" -lt 1 ]; then
    echo "error: failed to set CURRENT_PROJECT_VERSION — the pbxproj format changed?" >&2
    exit 1
fi
labelled=$(grep -c "MARKETING_VERSION = ${MARKETING_VERSION};" "$PBXPROJ" || true)
if [ "$labelled" -lt 1 ]; then
    echo "error: failed to set MARKETING_VERSION — the pbxproj format changed?" >&2
    exit 1
fi
echo "note: shipping ${MARKETING_VERSION} (${labelled} configuration(s)) as build ${CI_BUILD_NUMBER} (${written} configuration(s))"
