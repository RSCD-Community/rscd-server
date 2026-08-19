# RSCD Server

**The RuneScape Classic Daemon** — a login server and a game server, descended
from RSCDaemon and rebuilt for authenticity. Java, `org.rscdaemon.*`, and **no
third-party jars at all**: Connector/J was replaced by
`server/util/sql/MysqlClient`, MINA by `server/util/net`, and XStream by
`XmlObjects`.

Run this and you are running a world. Players reach it with the
[community client](https://github.com/RSCD-Community/rscd-community-client),
which plays on any world it is pointed at — including yours, whether or not you
ever list it publicly.

---

## Run a world

You need a **JDK 8 or newer** and a **MySQL** server. No Ant, no Maven, no
libraries to fetch.

**The easy way** — the guided installer checks your machine, builds the jar,
creates the database, imports the schema, and writes both config files, asking
before every step:

```sh
git clone https://github.com/RSCD-Community/rscd-server.git
cd rscd-server
./install.sh
```

**The manual way** — the same steps by hand:

```sh
cp conf/ls/Conf.xml.example     conf/ls/Conf.xml     # database credentials
cp conf/server/Conf.xml.example conf/server/Conf.xml # world name, ports, rates
mysql -u <user> -p <database> < rscd.sql             # schema + seed data

./build.sh              # compiles and packs rscd.jar
./run-loginserver.sh    # login server first: it owns the database
./run.sh                # then the game server
```

Windows runs the same show through `build.bat`, `run-loginserver.bat` and
`run.bat`. Every launcher checks its own prerequisites first and says in plain
words what is missing and how to get it — a bare `./run.sh` on a fresh clone
even builds the jar itself if a JDK is present.

Both config templates are the real files with the secrets removed, so every
comment and default is still there to read. Neither live file is in the
repository — one holds the database password, the other holds this world's
`server_key`. `conf/server/Conf.xml` is optional for a first local run:
without it the game server uses its compiled defaults (loopback addresses,
authentic 1x rates).

On first boot the server refuses to start on the shipped
`change-me-before-first-boot` server key, generates a real one, writes it to
your config and asks you to restart. That is deliberate: an unset key is the one
value it exists to reject.

### The database (`rscd.sql`)

One schema, two tenants that deliberately share it: the game tables (`rscd_*`,
touched only by the login server) and the community-site tables (`user`,
`forum_*`, roles, sessions — used by
[`rscd-www`](https://github.com/RSCD-Community/rscd-www) when you run the
website too). There are no foreign keys between the two groups —
`rscd_players.owner` is a plain int matching `user.id` — so a world without a
website runs fine, and so does a website without a world.

It ships seed data only, never accounts: eight forum boards and the Admin and
Member roles. To make your own account an admin once you have registered it
through the site:

```sql
INSERT INTO user_role (user_id, role_id)
SELECT id, 1 FROM user WHERE name = 'YourName';   -- role 1 is Admin
```

Written for MySQL 5.7. It imports on MySQL 8 and MariaDB as well — the integer
display widths it carries are deprecated there, but accepted.

## Ports

| Service | Port |
|---|---|
| Login server | 34522 |
| Query/status | 8181 |
| Game world | 43594 (standard RSC) |
| WebSocket bridge | 43595 (`ws_port` in Conf.xml; 0 disables) |

The WebSocket bridge is the game-world protocol wrapped in WebSocket framing
so browsers can connect; once unwrapped, a browser player is indistinguishable
from a TCP player. It speaks plain `ws://` — for `wss://`, terminate TLS at a
reverse proxy and forward the raw stream to 43595.

MySQL listens wherever your own installation put it (3306 by default); the
server connects to whatever `conf/server/Conf.xml` names, it does not run a
database of its own.

**The query/status socket on 8181 is deliberately unauthenticated.** Keep it
bound to localhost or firewalled.

## Layout

| Folder | What it is |
|---|---|
| `src/` | Both daemons. `ls.*` is the login server, `server.*` the game server. |
| `conf/` | The only config there is: `conf/ls/Conf.xml` and `conf/server/Conf.xml`, plus every definition and world file under `conf/server/`. |
| `cache_data/` | The client assets this world serves — the 15 files `mudclient.gamefiles` asks for, plus the world map. See below. |
| `quests/` | Quests, loaded from this directory at runtime — not from the classpath. |
| `rscd.sql` | The complete database: schema plus seed data, imported once by `install.sh` or by hand. |

To *edit* the definition and world files — items, npcs, drop tables, spawns,
sprites, the landscape — use
[`rscd-toolkit`](https://github.com/RSCD-Community/rscd-toolkit), which points
at a checkout of this repository (clone them side by side and it finds
`../rscd-server` on its own) and keeps the paired server/client copies of each
definition in sync when it saves.

## Config

Each process has exactly one config file, and it is the one that process looks
for by default. Both launchers take an optional path to override it.

- **`conf/ls/Conf.xml`** — MySQL host/port/db/user/pass, login-server ports.
- **`conf/server/Conf.xml`** — world name, addresses, ports, `exp_mult`,
  `server_version`, and the community world-list block (off by default).
  - `server_name` is what the Worlds screen and the login message show. Change
    it if you are running your own world.
  - `server_version` is **1200**; our client reports 1218.
  - `exp_mult` is **1**, authentic RSC. The original build ran 15x.
  - `server_key` ships as the `change-me-before-first-boot` sentinel, described
    above.

**Pass a path the process can actually resolve.** Both `Server.main`s and
`MapGenerator` used to run the argument through `File.getName()`, throwing the
directory away — so any config outside the working directory silently failed to
load and every setting fell back to the values compiled into `Config`. On the
login server that meant `localhost:2628` as `iggie`, credentials dead since
2011; on the game server it meant the sentinel server key. Fixed in all three,
and a missing config now says so instead of failing quietly later.

## Listing your world publicly (optional)

A world is perfectly usable unlisted — players type its address. To appear on
the [Play Game](https://rscd-community.org/play/) page and in the client's
Worlds screen, enable the world-list block in `conf/server/Conf.xml` and the
server heartbeats itself into the community registry every 60 seconds.

Registration is automatic: a heartbeat from a key nobody has seen registers it,
and going quiet delists it. There is no form and no approval queue. The registry
answers with a nonce and then connects to the address you advertised to ask for
it back, which is what stops anyone listing somebody else's address under a name
of their choosing.

### Letting browsers reach your world

The desktop client dials `address:server_port` directly. A browser cannot open a
raw socket at all, so it goes through the WebSocket bridge instead — and it can
only find yours if you say where it is:

```xml
<ws_url>wss://your-site.example/ws</ws_url>
```

This is **not** derived from `ws_port`. `ws_port` is what the bridge binds to on
loopback; `ws_url` is what a browser can actually reach, after your reverse
proxy. Only you know that, so only you can set it. A worked example, matching
the port table above:

```apache
ProxyPass /ws ws://127.0.0.1:43595/ timeout=1800
```

The registry accepts `wss://` only. That is not pedantry: a page served over
HTTPS cannot open a plaintext `ws://` socket — the browser blocks it before the
connection exists — so a `ws://` listing would be one that no player on the site
could ever use. Leave `ws_url` empty and your world simply is not playable in a
browser, which is a fine thing to choose; the desktop client is unaffected.

## Client assets (`cache_data/`)

**A world owns the assets its players run on.** `cache_data/` holds the 15 files
`mudclient.gamefiles` asks for plus the world map, and it ships with this
repository rather than with the client. The client ships no game assets at all:
it downloads them from whatever `cache_url` the world it joined advertises.

That is what makes one client able to play on many servers. Change an item, an
npc or the map here, and players on this world see it on next launch without a
new client — and players on someone else's world are unaffected, because they
are downloading someone else's `cache_data/`.

The files are served over HTTP by a web host, not by this process. Where the
site and the world run on one box that is a symlink from the document root:

```sh
ln -s ../rscd-server/cache_data cache_data
```

Where they do not, copy or rsync the directory to the web host and point
`cache_url` at it. Either way `cache_url` in `conf/server/Conf.xml` is what the
world tells clients, and it is the only thing that has to be right.

**Definitions exist twice on purpose**, in two formats for two readers: the
server walks `conf/server/defs/*.xml.gz`, the client reads
`cache_data/*.xml.data`. Change one and change the other, or the two will
disagree about the same world.

## Architecture notes worth knowing before you edit

- **`ls.Server.error()` logs; it does not exit.** It used to call
  `System.exit(1)` for any `Exception`, from any of its forty-odd call sites, so
  one malformed packet from one client took the login server down and dropped
  every player on every world — a browser pointed at the query port was enough
  (`NumberFormatException: For input string: "GET"`). Startup failures that
  leave nothing to serve call `ls.Server.fatal(what, cause)` instead, which is
  explicit about exiting. Add new failure paths to `error()` unless nobody is
  connected yet.
- **The login server owns the database. The game server does not.** They are
  separate JVM processes. `org.rscdaemon.ls.Server.db` is assigned inside
  `ls.Server.main()`, so it is `null` in the game server process — any
  game-server code reaching for it throws NPE. Game→LS communication goes over
  the LS protocol (`server/packetbuilder/loginserver/` →
  `ls/packethandler/loginserver/`).
- **Online-player tracking was never implemented upstream.** `rscd_players.world`
  defaulted to `1`, so every account looked permanently logged into world 1,
  while `rscd_worlds` sat empty. The `rscd_players.world` half is now maintained
  by `ls.model.World` (NULL = not in a world). Populating `rscd_worlds` still
  needs the world's location/ip/port, which the registration packet does not
  carry.
- **Spell ids are wire-level: the client's table and the server's must be the
  same list.** The client sends the *index* of the spell in its own `SpellDef`
  table and the server looks that index up in its own. Ignis Isle replaced the
  server's table with a 45-spell list of its own while the client kept RSCD
  v25's 49-spell one, so the two disagreed at **46 of 49 ids** — casting Varrock
  teleport arrived as Earth bolt. The server's table is back to v25's, which is
  what the client has always shipped. **If you change one, change both:**
  `conf/server/defs/SpellDef.xml.gz`, which this server reads, and
  `cache_data/SpellDef.xml.data`, which a client downloads. Both are in this
  repository — that is the point of the server owning `cache_data/`.
  - `conf/server/defs/extras/SpellAggressiveLvl.xml.gz` is keyed by spell id
    too. It is the cheapest check that an id space is right: all 16 keys should
    land on the elemental attacks with strengths 1–16.
  - Ignis Isle's own spells (Healing Auras, Heal Other, Blackout) are **kept but
    dormant**, reached only through a `getSpellType() == 7/8/9` gate. When they
    come back they should be **appended after id 48**, never renumbered into the
    vanilla range.
- **Quests load from `quests/`, not from the classpath.** `QuestLoader` listed
  the `.class` files there and then loaded them with `Class.forName()`, which
  ignores the directory and searches the classpath — so unless you remembered
  `-cp build:quests:lib/*`, every quest threw `ClassNotFoundException` and
  startup printed `0 Quest class files loaded`. It now loads through a
  `URLClassLoader` rooted at the directory it just listed. Three things follow:
  - **Dropping a `.java` into `quests/` is enough.** Anything without an
    up-to-date `.class` beside it is compiled first, against the server's own
    classpath. On a JRE this is skipped with a message and already-compiled
    quests still load.
  - **A quest that cannot be used is named and skipped, not left to fail later.**
    `QuestManager` rebuilds every quest for every player who logs in, so a class
    with no `public Quest(Player, Integer)` constructor would otherwise throw
    once per login forever.
  - **UIDs must be unique, and now that is enforced.** A quest's
    `public static int UID` is its database identifier; two quests sharing one
    means each overwrites the other's saved progress. Set the directory
    elsewhere with `-Drscd.quests=`.
- **A spawn's start tile is the centre of its roam box, not a placed position.**
  Jagex derived it: `(390..392, 3731..3733)` starts at `(391, 3732)`. When a box
  straddles water or lava the computed centre lands in it, so two vanilla spawns
  ask to stand where nothing can stand. `World.registerNpc` used to print
  `Fucked Npc:` and add the npc anyway. It now moves the spawn to the nearest
  unblocked tile inside the *same* box and writes the correction back into the
  `NPCLoc`, because `Npc.remove()` respawns from that same object.
  `NpcLoc.xml.gz` is left as Jagex wrote it — the data is authentic, the
  derivation is what was wrong, and the engine is where that belongs.

## The rest of the project

| Repository | What it is |
|---|---|
| [`rscd-community-client`](https://github.com/RSCD-Community/rscd-community-client) | The desktop client players use. Ships no game assets. |
| [`rscd-toolkit`](https://github.com/RSCD-Community/rscd-toolkit) | The world editor — items, npcs, drops, spawns, sprites, landscape. Edits this repository's files in place. |
| [`rscd-www`](https://github.com/RSCD-Community/rscd-www) | [rscd-community.org](https://rscd-community.org) — account manager, forums, hiscores, beastiary, the 2003 manual, and browser play. |

## Licence

**Apache-2.0.** Full text in [`LICENSE`](LICENSE), attribution and lineage in
[`NOTICE`](NOTICE). Read `NOTICE` before forking — it names RSCDaemon's original
authors, records that no licence text was ever attached to that code, and
separates what came from upstream (the engine skeleton) from what was written
here (everything a player touches). That attribution travels with any fork.

RuneScape is a trademark of Jagex Ltd. This project is not affiliated with or
endorsed by Jagex. See
[what this project claims and does not](https://rscd-community.org/about/).
