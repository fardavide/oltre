#!/bin/sh
# Xcode Cloud custom build script — runs after the repository is cloned, before dependency
# resolution. Xcode Cloud finds this directory because it sits next to iosApp.xcodeproj.
#
# Why it exists: Xcode Cloud's build environment "includes tools that are part of macOS and
# Xcode — for example, Python — and additionally Homebrew". macOS has shipped no JDK since
# 2015, so the "Compile Kotlin Framework" build phase would fail with
#   Unable to locate a Java Runtime / Command PhaseScriptExecution failed ... exit code 65
#
# Homebrew is the mechanism Apple documents for third-party tools here, and `sudo` is not
# available — so openjdk stays keg-only and /usr/libexec/java_home will never resolve it.
# That is deliberate: the build phase in project.yml probes the keg path directly instead.
set -eu

if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    echo "note: a JDK 21 is already registered with the system — nothing to install"
    exit 0
fi

BREW=$(command -v brew || echo /opt/homebrew/bin/brew)
if [ ! -x "$BREW" ]; then
    echo "error: Homebrew not found; cannot provision a JDK" >&2
    exit 1
fi

echo "note: installing openjdk@21 via Homebrew"
"$BREW" install --quiet openjdk@21

JAVA_HOME="$("$BREW" --prefix)/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
if [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "error: expected a JDK at $JAVA_HOME after install, found none" >&2
    exit 1
fi

# Informational only — this export dies with the script. Nothing exported here reaches the
# xcodebuild phase, which is why the build phase resolves JAVA_HOME on its own.
echo "note: JDK ready at $JAVA_HOME"
"$JAVA_HOME/bin/java" -version
