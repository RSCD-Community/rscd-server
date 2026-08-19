#!/bin/bash
# Launch the game server.
#
# Usage: ./run.sh [conf/server/Conf.xml]
#
# The game server needs the login server running first (./run-loginserver.sh
# in another terminal) -- that is the only process that talks to the database,
# and this one connects to it on startup. This script checks and says so
# rather than letting the connection failure speak for itself.
#
# Everything is relative to this script, and the server resolves its config and
# its definition files against the working directory, so this cd is load-bearing
# -- not tidiness.
#
# conf/server/Conf.xml is OPTIONAL: without it every setting falls back to a
# sensible local default (loopback addresses, authentic 1x rates). Copy
# conf/server/Conf.xml.example to conf/server/Conf.xml when you want to change
# something.
#
# No --add-opens. The server used to need one because XStream allocated the
# model classes through sun.reflect.ReflectionFactory; it reads its definitions
# with server/util/XmlObjects now, which touches no JDK internal, so any Java 8
# or later runs it as-is.

cd "$(dirname "$0")" || exit 1

# Local, uncommitted settings (e.g. RSCD_QUESTS_HOOK below).
if [ -f run.local.env ]; then
   . ./run.local.env
fi

if [ -n "$JDK" ]; then
   JAVA="$JDK/bin/java"
elif [ -n "$JAVA_HOME" ]; then
   JAVA="$JAVA_HOME/bin/java"
else
   JAVA=java
fi

if ! command -v "$JAVA" >/dev/null 2>&1; then
   echo "" >&2
   echo "The server needs Java, and 'java' was not found on this machine." >&2
   echo "" >&2
   echo "  Ubuntu/Debian:  sudo apt install default-jdk" >&2
   echo "  Fedora/RHEL:    sudo dnf install java-latest-openjdk-devel" >&2
   echo "  macOS:          brew install --cask temurin" >&2
   echo "  Anywhere else:  https://adoptium.net" >&2
   echo "" >&2
   echo "A JDK rather than a bare JRE is worth having on a server: with one," >&2
   echo "quests dropped into quests/ as .java source compile themselves at boot." >&2
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

if [ ! -f conf/server/Conf.xml ]; then
   echo "Note: no conf/server/Conf.xml -- running on the built-in defaults"
   echo "(local addresses, authentic rates). To change the world's name, rates"
   echo "or ports:  cp conf/server/Conf.xml.example conf/server/Conf.xml"
fi

# The login server must be up before this process starts, or every login will
# fail. Read where Conf.xml says it is (defaults: localhost 34522), then probe.
LS_HOST=$(sed -n 's/^[[:space:]]*ls_ip[[:space:]]*=[[:space:]]*//p' conf/server/Conf.xml 2>/dev/null | tail -1)
LS_PORT=$(sed -n 's/^[[:space:]]*ls_port[[:space:]]*=[[:space:]]*//p' conf/server/Conf.xml 2>/dev/null | tail -1)
LS_HOST="${LS_HOST:-localhost}"
LS_PORT="${LS_PORT:-34522}"
if ! (exec 3<>"/dev/tcp/$LS_HOST/$LS_PORT") 2>/dev/null; then
   echo "" >&2
   echo "Nothing is listening at $LS_HOST:$LS_PORT -- that is where the login" >&2
   echo "server should be, and the game server cannot run without it." >&2
   echo "" >&2
   echo "Start it in another terminal first:  ./run-loginserver.sh" >&2
   echo "(First time here? ./install.sh sets up the database it needs.)" >&2
   exit 1
fi

mkdir -p logs

# RSCD_QUESTS_HOOK: command QuestLoader runs after it (re)compiles any quest
# source at boot -- quest classes feed the website's game data, so pointing
# this at a site generator keeps the site's quest and beastiary data in step
# automatically. Unset (the default), nothing runs.
EXTRA_OPTS=()
if [ -n "$RSCD_QUESTS_HOOK" ]; then
   EXTRA_OPTS+=("-Drscd.quests.hook=$RSCD_QUESTS_HOOK")
fi

exec "$JAVA" ${JAVA_OPTS:--Xmx512m} "${EXTRA_OPTS[@]}" -cp rscd.jar org.rscdaemon.server.Server "$@"
