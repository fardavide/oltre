#!/bin/sh
# Xcode Cloud custom build script — runs before every xcodebuild action.
#
# Pins the bundle build number to Xcode Cloud's own run number. Xcode Cloud assigns each build
# a monotonically increasing integer, and App Store Connect rejects a CFBundleVersion that
# repeats within a release train (all builds sharing one CFBundleShortVersionString). The
# checked-in CURRENT_PROJECT_VERSION is a placeholder, so without this every archive would
# upload as build 1 and every upload after the first would be refused.
#
# Info.plist declares CFBundleVersion as $(CURRENT_PROJECT_VERSION), so rewriting the build
# setting is enough. agvtool is not used: it requires VERSIONING_SYSTEM = apple-generic, which
# this project does not set, and would silently no-op.
set -eu

if [ -z "${CI_BUILD_NUMBER:-}" ]; then
    echo "note: CI_BUILD_NUMBER unset — not an Xcode Cloud build, leaving the version alone"
    exit 0
fi

PBXPROJ="${CI_PRIMARY_REPOSITORY_PATH:?}/iosApp/iosApp.xcodeproj/project.pbxproj"
if [ ! -f "$PBXPROJ" ]; then
    echo "error: no project at $PBXPROJ" >&2
    exit 1
fi

sed -i '' -E "s/CURRENT_PROJECT_VERSION = [0-9]+;/CURRENT_PROJECT_VERSION = ${CI_BUILD_NUMBER};/g" "$PBXPROJ"

# A silent no-op here ships a duplicate build number, so assert the rewrite landed in every
# build configuration rather than trusting sed's exit status.
written=$(grep -c "CURRENT_PROJECT_VERSION = ${CI_BUILD_NUMBER};" "$PBXPROJ" || true)
if [ "$written" -lt 1 ]; then
    echo "error: failed to set CURRENT_PROJECT_VERSION — the pbxproj format changed?" >&2
    exit 1
fi
echo "note: build number set to ${CI_BUILD_NUMBER} in ${written} build configuration(s)"
