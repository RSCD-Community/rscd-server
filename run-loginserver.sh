#!/bin/bash
# Launch the login server.
#
# Usage: ./run-loginserver.sh [conf/ls/Conf.xml]
#
# The login server is the only process that talks to the database. Every world
# needs one running; start it before ./run.sh. It is also what aggregates
# per-world player counts, so a multi-world operator runs one of these beside
# all the game servers.
#
# Unlike the game server's config, conf/ls/Conf.xml is REQUIRED: it holds the
# database credentials, which deliberately have no compiled-in fallback (a
# credential with a fallback is a credential that ends up committed).
# ./install.sh writes it for you; this script checks it exists and says how to
# make one by hand.
#
# Same shape as run.sh: relative paths, no --add-opens, any Java 8 or later.

cd "$(dirname "$0")" || exit 1

if [ -n "$JDK" ]; then
   JAVA="$JDK/bin/java"
elif [ -n "$JAVA_HOME" ]; then
   JAVA="$JAVA_HOME/bin/java"
else
   JAVA=java
fi

if ! command -v "$JAVA" >/dev/null 2>&1; then
   echo "" >&2
   echo "The login server needs Java, and 'java' was not found on this machine." >&2
   echo "" >&2
   echo "  Ubuntu/Debian:  sudo apt install default-jdk" >&2
   echo "  Fedora/RHEL:    sudo dnf install java-latest-openjdk-devel" >&2
   echo "  macOS:          brew install --cask temurin" >&2
   echo "  Anywhere else:  https://adoptium.net" >&2
   echo "" >&2
   echo "If Java is installed but not on your PATH, set JAVA_HOME to its folder." >&2
   exit 1
fi
# Existing is not the same as working: macOS ships a placeholder
# /usr/bin/java that is present before Java is installed and only prints
# an error when run. Catch that here with a friendly message instead of
# letting Apple's cryptic one through.
if ! "$JAVA" -version >/dev/null 2>&1; then
   echo "" >&2
   echo "A 'java' command exists on this machine, but it does not work." >&2
   echo "On macOS this means Java is not really installed yet -- Apple ships a" >&2
   echo "placeholder that only prints an error until the real thing is installed." >&2
   echo "" >&2
   echo "  macOS:          brew install --cask temurin" >&2
   echo "                  (no Homebrew? download the .pkg from https://adoptium.net)" >&2
   echo "  Anywhere else:  reinstall Java from https://adoptium.net" >&2
   exit 1
fi


if [ ! -f rscd.jar ]; then
   echo "rscd.jar is not built yet -- building it now (first run only)..."
   ./build.sh || exit 1
fi

if [ ! -f conf/ls/Conf.xml ] && [ -z "$1" ]; then
   echo "" >&2
   echo "There is no conf/ls/Conf.xml yet. The login server cannot start without" >&2
   echo "one -- it holds the database credentials, and those have no default." >&2
   echo "" >&2
   echo "The guided way:  ./install.sh   (sets up the database too)" >&2
   echo "The manual way:  cp conf/ls/Conf.xml.example conf/ls/Conf.xml" >&2
   echo "                 then edit mysql_user / mysql_pass in it." >&2
   exit 1
fi

if grep -q '^[[:space:]]*mysql_pass[[:space:]]*=[[:space:]]*CHANGEME[[:space:]]*$' conf/ls/Conf.xml 2>/dev/null; then
   echo "" >&2
   echo "conf/ls/Conf.xml still has the template password (mysql_pass = CHANGEME)." >&2
   echo "Edit it to match your real database user, or run ./install.sh to be" >&2
   echo "walked through the whole database setup." >&2
   exit 1
fi

mkdir -p logs

exec "$JAVA" ${JAVA_OPTS:--Xmx256m} -cp rscd.jar org.rscdaemon.ls.Server "$@"
