#!/bin/bash
# Guided first-time setup for an RSCD world.
#
# Usage: ./install.sh
#
# Takes a fresh clone to a runnable world: checks Java, builds the jar, sets
# up the MySQL database (creates it, creates a user, imports rscd.sql), and
# writes both config files from their templates. Asks before everything,
# explains everything, and is safe to re-run -- it detects what already
# exists and never overwrites a live database or config without asking.
#
# Nothing here is magic: every step prints the equivalent manual command, so
# this doubles as a worked example of the README's setup section.

set -u
cd "$(dirname "$0")"

say()  { printf '%s\n' "$*"; }
ask()  { local reply; read -r -p "$1" reply </dev/tty; printf '%s' "$reply"; }
fail() { printf '%s\n' "$*" >&2; exit 1; }

# This script is a conversation; it cannot run without a terminal to ask on.
# (Testing -r /dev/tty is not enough -- the permission bits pass even with no
# controlling terminal; only an actual open proves there is one.)
if ! (exec 3</dev/tty) 2>/dev/null; then
   fail "install.sh is interactive -- run it from a terminal, not from a pipe or a service."
fi

say ""
say "=============================================="
say " RSCD server setup"
say "=============================================="
say ""
say "This walks you through everything a world needs:"
say ""
say "  1. Java (to build and run the two server processes)"
say "  2. MySQL (the database that stores every account and character)"
say "  3. The database itself: created and loaded from rscd.sql"
say "  4. The two config files, written from their templates"
say ""
say "Nothing is changed without asking first, and re-running this later is"
say "safe. Press Ctrl+C at any point to stop."
say ""

# ---------------------------------------------------------------------------
# 1. Java
# ---------------------------------------------------------------------------

if [ -n "${JDK:-}" ]; then
   JBIN="$JDK/bin/"
elif [ -n "${JAVA_HOME:-}" ]; then
   JBIN="$JAVA_HOME/bin/"
else
   JBIN=""
fi

if ! command -v "${JBIN}javac" >/dev/null 2>&1; then
   say "Java check: no JDK found."
   say ""
   say "The server is written in Java, and building it needs the JDK (the"
   say "compiler, not just the runtime). Install one and run this again:"
   say ""
   say "  Ubuntu/Debian:  sudo apt install default-jdk"
   say "  Fedora/RHEL:    sudo dnf install java-latest-openjdk-devel"
   say "  macOS:          brew install --cask temurin"
   say "  Anywhere else:  https://adoptium.net (pick the JDK)"
   say ""
   fail "Stopped: install a JDK first, then re-run ./install.sh"
fi

# Existing is not the same as working: macOS ships a placeholder javac that
# is present before a JDK is installed and only prints an error when run.
if ! "${JBIN}javac" -version >/dev/null 2>&1; then
   say "Java check: a 'javac' command exists, but it does not work."
   say ""
   say "On macOS this means Java is not really installed yet -- Apple ships a"
   say "placeholder that only prints an error until the real thing is there."
   say ""
   say "  macOS:          brew install --cask temurin"
   say "                  (no Homebrew? download the .pkg from https://adoptium.net)"
   say "  Anywhere else:  reinstall Java from https://adoptium.net"
   say ""
   fail "Stopped: fix Java first, then re-run ./install.sh"
fi
say "Java check: found $("${JBIN}javac" -version 2>&1)."

if [ -f rscd.jar ]; then
   say "Build check: rscd.jar already built -- leaving it alone."
else
   say "Build check: rscd.jar not built yet. Building (this is ./build.sh)..."
   ./build.sh || fail "The build failed -- the errors above say why."
fi
say ""

# ---------------------------------------------------------------------------
# 2. MySQL
# ---------------------------------------------------------------------------

say "----------------------------------------------"
say " The database"
say "----------------------------------------------"
say ""
say "Everything permanent about a world -- accounts, characters, banks,"
say "quest progress -- lives in MySQL. The login server is the only process"
say "that connects to it."
say ""

if ! command -v mysql >/dev/null 2>&1; then
   say "The 'mysql' command was not found on this machine."
   say ""
   say "If the database will run HERE, install a MySQL (or MariaDB) server:"
   say ""
   say "  Ubuntu/Debian:  sudo apt install mysql-server"
   say "  Fedora/RHEL:    sudo dnf install mariadb-server && sudo systemctl enable --now mariadb"
   say "  macOS:          brew install mysql && brew services start mysql"
   say ""
   say "If the database runs on ANOTHER machine, you still need the client"
   say "here to import the schema (usually the mysql-client package)."
   say ""
   fail "Stopped: install MySQL first, then re-run ./install.sh"
fi
say "MySQL check: found $(mysql --version | head -1)."
say ""

say "Where does the database live?"
DB_HOST=$(ask "  Host [127.0.0.1]: ");  DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=$(ask "  Port [3306]: ");       DB_PORT=${DB_PORT:-3306}
DB_NAME=$(ask "  Database name [rscd]: "); DB_NAME=${DB_NAME:-rscd}
DB_USER=$(ask "  Database user for the server [rscd]: "); DB_USER=${DB_USER:-rscd}

# The password never goes on a command line (visible in ps) -- it goes into a
# temporary defaults file handed to the client, deleted on exit.
while :; do
   read -r -s -p "  Password for that user (typing is hidden): " DB_PASS </dev/tty; say ""
   [ -n "$DB_PASS" ] || { say "  An empty password is not accepted -- pick one."; continue; }
   # These four characters would need escaping in the SQL and sed below, and a
   # password is the wrong place to be clever about quoting. Letters, digits
   # and the rest of the punctuation table are all fine.
   case "$DB_PASS" in
      *"'"*|*'\'*|*'|'*|*'&'*) say "  Avoid ' \\ | and & in the password -- pick another."; continue ;;
   esac
   read -r -s -p "  Same password again: " DB_PASS2 </dev/tty; say ""
   [ "$DB_PASS" = "$DB_PASS2" ] && break
   say "  They did not match -- try again."
done
say ""

CREDS=$(mktemp) || fail "Could not create a temporary file."
chmod 600 "$CREDS"
trap 'rm -f "$CREDS"' EXIT
{
   printf '[client]\n'
   printf 'host=%s\n' "$DB_HOST"
   printf 'port=%s\n' "$DB_PORT"
   printf 'user=%s\n' "$DB_USER"
   printf 'password=%s\n' "$DB_PASS"
} > "$CREDS"

game_mysql() { mysql --defaults-extra-file="$CREDS" "$@"; }

# ---------------------------------------------------------------------------
# 3. Create the database and user (or confirm they exist)
# ---------------------------------------------------------------------------

if game_mysql -e "USE \`$DB_NAME\`" 2>/dev/null; then
   say "Connection check: '$DB_USER' can already reach database '$DB_NAME' -- nothing to create."
else
   say "That user cannot reach '$DB_NAME' yet (normal on a first install)."
   say "Creating both needs a MySQL admin. How do you run MySQL as admin here?"
   say ""
   say "  1. sudo mysql            (the default on Ubuntu/Debian installs)"
   say "  2. mysql -u root -p      (root with a password)"
   say "  3. I'll create the database and user myself, then re-run this"
   say ""
   CHOICE=$(ask "Pick 1, 2 or 3 [1]: "); CHOICE=${CHOICE:-1}

   # SQL notes: utf8mb4 matches what rscd.sql declares per table; the user is
   # created for both localhost and % so the site or a second machine can share
   # it -- tighten the host part later if you want (GRANT docs cover it).
   ADMIN_SQL=$(cat <<EOF
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASS';
CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASS';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'%';
FLUSH PRIVILEGES;
EOF
)
   case "$CHOICE" in
      1)
         say "Running: sudo mysql  (you may be asked for YOUR password by sudo)"
         printf '%s\n' "$ADMIN_SQL" | sudo mysql || fail "Could not create the database as admin -- the message above says why."
         ;;
      2)
         say "Running: mysql -u root -p -h $DB_HOST -P $DB_PORT  (MySQL will ask for root's password)"
         printf '%s\n' "$ADMIN_SQL" | mysql -u root -p -h "$DB_HOST" -P "$DB_PORT" || fail "Could not create the database as root -- the message above says why."
         ;;
      3)
         say ""
         say "Run these as your MySQL admin, then re-run ./install.sh:"
         say ""
         printf '%s\n' "$ADMIN_SQL" | sed "s/IDENTIFIED BY '.*'/IDENTIFIED BY '<the password you chose>'/"
         exit 0
         ;;
      *) fail "Not a 1, 2 or 3 -- re-run ./install.sh." ;;
   esac

   game_mysql -e "USE \`$DB_NAME\`" 2>/dev/null \
      || fail "The database was created but '$DB_USER' still cannot reach it. Check the GRANT lines above."
   say "Created database '$DB_NAME' and user '$DB_USER'."
fi
say ""

# ---------------------------------------------------------------------------
# 4. Import the schema
# ---------------------------------------------------------------------------

[ -f rscd.sql ] || fail "rscd.sql is missing from this checkout -- it ships with the repository."

# A live world must never have its tables re-imported underneath it: rscd.sql
# starts with CREATE TABLE statements that would collide, but checking first
# lets us say something human instead of letting MySQL's error speak.
if game_mysql -e "SELECT 1 FROM \`$DB_NAME\`.rscd_players LIMIT 1" >/dev/null 2>&1; then
   say "Schema check: '$DB_NAME' already has the game tables -- skipping the"
   say "import so an existing world's data stays untouched."
else
   say "Importing rscd.sql into '$DB_NAME' (schema plus starter data: forum"
   say "boards and roles; no accounts)..."
   game_mysql "$DB_NAME" < rscd.sql || fail "The import failed -- the message above says why."
   say "Imported."
fi
say ""

# ---------------------------------------------------------------------------
# 5. Write the two config files
# ---------------------------------------------------------------------------

say "----------------------------------------------"
say " Config files"
say "----------------------------------------------"
say ""

# conf/ls/Conf.xml -- the login server's, holds the database credentials.
write_ls_conf() {
   # The template, with the credential lines and any commented defaults that
   # the user's answers differ from filled in. sed on the known keys keeps
   # every explanatory comment in the file it writes.
   sed -e "s|^mysql_user = .*|mysql_user = $DB_USER|" \
       -e "s|^mysql_pass = .*|mysql_pass = $DB_PASS|" \
       -e "s|^mysql_host = .*|mysql_host = $DB_HOST|" \
       -e "s|^mysql_port = .*|mysql_port = $DB_PORT|" \
       -e "s|^mysql_db   = .*|mysql_db   = $DB_NAME|" \
       conf/ls/Conf.xml.example > conf/ls/Conf.xml
   chmod 600 conf/ls/Conf.xml
   say "Wrote conf/ls/Conf.xml (readable only by you -- it holds the password)."
}

if [ -f conf/ls/Conf.xml ]; then
   OVER=$(ask "conf/ls/Conf.xml already exists. Overwrite it with these answers? [y/N]: ")
   case "$OVER" in y|Y|yes|YES) write_ls_conf ;; *) say "Kept the existing conf/ls/Conf.xml." ;; esac
else
   write_ls_conf
fi

# conf/server/Conf.xml -- the game server's. Optional (the defaults run a
# local world), but naming the world is the one thing everybody wants to do.
if [ ! -f conf/server/Conf.xml ]; then
   say ""
   WORLD_NAME=$(ask "What is this world called? [RSCD Community]: ")
   WORLD_NAME=${WORLD_NAME:-RSCD Community}
   sed -e "s|^server_name     = .*|server_name     = $WORLD_NAME|" \
       conf/server/Conf.xml.example > conf/server/Conf.xml
   say "Wrote conf/server/Conf.xml. Everything else in it (rates, ports, the"
   say "public world list) is off or on authentic defaults; the comments in"
   say "the file explain each setting when you want them."
else
   say "conf/server/Conf.xml already exists -- leaving it alone."
fi

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------

say ""
say "=============================================="
say " Done. To start your world:"
say "=============================================="
say ""
say "  Terminal 1:   ./run-loginserver.sh"
say "  Terminal 2:   ./run.sh"
say ""
say "The first ./run.sh will generate this world's server_key and ask you to"
say "confirm it -- that is expected; the key is how the community world list"
say "tells worlds apart, and it stays private to you."
say ""
say "Then point a client at it: in the rscd-community-client repository,"
say "./run.sh, and your world is 127.0.0.1 on the Worlds screen's custom"
say "entry (or set address/port in its settings.ini)."
say ""
say "Optional extras, whenever you want them:"
say "  - The community website (accounts, hiscores, forums): the rscd-www"
say "    repository, pointed at this same database."
say "  - A public listing on the community world list: heartbeat = true in"
say "    conf/server/Conf.xml -- the comments there explain it."
