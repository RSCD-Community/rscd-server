#!/bin/bash
# Compile the server and pack rscd.jar.
#
# Usage: ./build.sh
#
# Replaces "ant compile" for anyone who does not have Ant. build.xml still
# works and still does the same thing; this needs nothing but a JDK, which
# matters when the point is that a hobbyist can clone and run.
#
# Everything is relative to this script. Java comes from JDK, then JAVA_HOME,
# then PATH.
#
# build.xml compiles with target="1.5", which no modern javac accepts (8 is the
# floor). Java 8 is targeted instead (--release 8 on a modern JDK,
# -source/-target on JDK 8 itself, which predates the --release flag); set
# RELEASE to change it.

set -e
cd "$(dirname "$0")"

if [ -n "$JDK" ]; then
   JBIN="$JDK/bin/"
elif [ -n "$JAVA_HOME" ]; then
   JBIN="$JAVA_HOME/bin/"
else
   JBIN=""
fi

JAVAC="${JBIN}javac"
JAR="${JBIN}jar"
RELEASE="${RELEASE:-8}"

if ! command -v "$JAVAC" >/dev/null 2>&1; then
   echo "" >&2
   echo "Building needs a JDK (the Java compiler), and 'javac' was not found." >&2
   echo "" >&2
   echo "  Ubuntu/Debian:  sudo apt install default-jdk" >&2
   echo "  Fedora/RHEL:    sudo dnf install java-latest-openjdk-devel" >&2
   echo "  macOS:          brew install --cask temurin" >&2
   echo "  Anywhere else:  https://adoptium.net (pick the JDK, not the JRE)" >&2
   echo "" >&2
   echo "If a JDK is installed but not on your PATH, set JAVA_HOME to its folder." >&2
   exit 1
fi
# Existing is not the same as working: macOS ships a placeholder
# /usr/bin/javac that is present before Java is installed and only prints
# an error when run. Catch that here with a friendly message instead of
# letting Apple's cryptic one through.
if ! "$JAVAC" -version >/dev/null 2>&1; then
   echo "" >&2
   echo "A 'javac' command exists on this machine, but it does not work." >&2
   echo "On macOS this means Java is not really installed yet -- Apple ships a" >&2
   echo "placeholder that only prints an error until the real thing is installed." >&2
   echo "" >&2
   echo "  macOS:          brew install --cask temurin" >&2
   echo "                  (no Homebrew? download the .pkg from https://adoptium.net)" >&2
   echo "  Anywhere else:  reinstall Java from https://adoptium.net" >&2
   exit 1
fi


# JDK 8's javac has no --release flag (it arrived in JDK 9), so pick the
# spelling this javac understands. "javac 1.8.0_431" is 8; "javac 17.0.2" is 17.
JAVAC_VERSION=$("$JAVAC" -version 2>&1 | sed 's/^javac //; s/^1\.//; s/[.-].*//')
if ! [ "${JAVAC_VERSION:-0}" -ge 8 ] 2>/dev/null; then
   echo "This javac reports version \"$("$JAVAC" -version 2>&1)\"; Java 8 is the oldest supported." >&2
   echo "Install a newer JDK from https://adoptium.net" >&2
   exit 1
fi
if [ "$JAVAC_VERSION" = "8" ]; then
   TARGET_FLAGS="-source $RELEASE -target $RELEASE"
else
   TARGET_FLAGS="--release $RELEASE"
fi

rm -rf build
mkdir -p build
find src -name '*.java' > build/sources.txt
"$JAVAC" -nowarn $TARGET_FLAGS -d build @build/sources.txt
rm -f build/sources.txt

rm -f rscd.jar
"$JAR" cf rscd.jar -C build .

CLASSES=$("$JAR" tf rscd.jar | grep -c '\.class$')
echo "rscd.jar packed: $CLASSES classes"
echo ""
echo "First time here? ./install.sh walks you through the database and config."
echo "Already set up?  ./run-loginserver.sh first, then ./run.sh in another terminal."
