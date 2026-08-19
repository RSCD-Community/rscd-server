# Writing a quest

Drop a `.java` file in this directory and start the game server. It is compiled on
startup against the server's own classpath and loaded from here, so nothing needs
adding to `-cp` and nothing needs rebuilding. Startup prints what it did:

```
<-- Loading Quest class files -->

 - Compiled: MiningQuest.java
 - Loaded:   miningquest

<-- 1 Quest class files loaded -->
```

Edit the source and restart; a `.java` newer than its `.class` is recompiled. If a
quest is refused, the line says which one and why.

## The minimum

```java
import org.rscdaemon.server.quest.*;
import org.rscdaemon.server.model.*;

public class MyQuest extends Quest {
   /** The database identifier. It must not collide with another quest's. */
   public final static int UID = 3;

   public MyQuest(Player owner, Integer uid) {
      super(owner, UID);
   }

   public void define() {
      setName("My Quest");
      setFinalStage(100);
      associateNpc(77);     // triggerEntity() fires for these
      associateItem(207);
      associateObject(0);
   }

   public void completeQuest() {
      // Called when setStage(getFinalStage()) is reached.
   }
}
```

Four things are load-bearing, because `QuestLoader` checks each of them and will
skip the class with a reason rather than let it fail later:

- it extends `Quest`
- it is not abstract (a shared base class in this folder is fine — it is skipped,
  not treated as an error)
- it has a **public** `(Player, Integer)` constructor, because `QuestManager`
  rebuilds every quest for every player at login
- its `UID` is not already taken. Two quests sharing a UID means each overwrites
  the other's saved progress, so the second one alphabetically is refused.

A quest with no `UID` field still loads; it just opts out of the collision check.

## Menus (the old way)

`QuestMenu` sends the player a list of options and calls you back. None of the
seventeen vanilla quests use it -- they use `Conversation` and `Choice`, below --
but it is what `MiningQuest` and `TestQuest` are written against and it still
works:

```java
new QuestMenu(getOwner(), new String[] { "Yes.", "No thanks." }) {
   public void handleReply(final int option, String response) {
      if (option == 0) {
         sayMessage("Yes.");
         sleep(1200);
         sayNpcMessage("Good.");
      }
      getOwner().resetMenuHandler();   // not setMenuHandler(null) -- see below
   }
};
```

Clear a menu with `getOwner().resetMenuHandler()`. `setMenuHandler(null)` calls
`menuHandler.setOwner(this)` on the argument, so passing `null` can only throw an
NPE — both bundled quests did this, four times each.

`sleep(ms)` is real sleeping on the calling thread. `QuestManager.triggerEntity()`
starts a thread per triggered quest, so this blocks that quest's dialogue and not
the game loop.

## Ids

A quest's UID is its index into `mudclient.QUEST_NAMES`, mirrored in
`src/org/rscdaemon/server/quest/Quests.java`. Use the constant, never the number:

```java
public final static int UID = Quests.COOKS_ASSISTANT;
```

That id is what the client's F2 quest tab ticks and what `Quests.POINTS[id]`
awards, so it is not ours to choose for a Jagex quest. Quest points are handed out
by `Player.getQuestPoints()`, which sums the points of every finished quest --
`completeQuest()` must not award them itself or they are counted twice.

Ids from `Quests.FIRST_CUSTOM` (1000) upward are ours. They save and load like any
other but have nowhere to appear in the stock client's quest list.

## Stages

`completed()` is `stage == finalStage`, exactly. Not `>=`. So the final stage has
to be reached on the nose, and `setStage(finalStage)` is what fires
`completeQuest()` and redraws the quest tab and the stat panel.

Stages need not be contiguous, and several quests use that: Shield of Arrav
carries which gang the player joined in the stage number itself.

Progress is saved. `setStage()` writes through to the player, and the player's row
carries it to the database on the usual save.

## Talking

Dialogue is written with the `Conversation` builder in
`src/org/rscdaemon/server/quest/dialogue/`, not with `QuestMenu` and `sleep()`:

```java
new Conversation(getOwner(), npc)
    .npc("Can you help me?")
    .player("What do you need?")
    .options(new Choice("Yes, I'll help", "Not right now") {
        public void picked(int option, Conversation c) {
            if (option == 0) {
                c.npc("Thank you!").then(new Effect() {
                    public void run(Conversation c) {
                        setStage(STARTED);
                    }
                });
            }
        }
    })
    .start();
```

- `.npc()`, `.player()`, `.message()`, `.give()`, `.take(id, n)`, `.then(Effect)`
  and `.options(Choice)` queue steps; `.start()` runs them, one every 1500 ms.
- Inside `picked()` you are building more steps onto the same conversation at the
  cursor, so a `Choice` can offer a freshly-built menu -- including itself again.
- The client shows at most **ten** options. Build longer menus in stages.
- `.start()` sets the player busy and blocks the npc; `finish()` clears both.
  Anything that needs a free player -- starting a fight, for instance -- has to be
  deferred with a `SingleEvent`.
- `c.getPlayer()` inside a chain runs **at build time**, not in sequence. To say
  something conditional in the right place, use `.message()` or decide inside an
  `Effect`.

## Association is ownership

`associateNpc`, `associateObject`, `associateItem` and `associateDoor` do not
subscribe to an entity -- they take it. A quest that associates npc 197 receives
every click on every Oracle in the world, and no other handler runs. So:

- **Guard on coordinates.** There are twenty-odd doors of id 2 in a single
  building. Claiming the id claims all of them; hand back the ones that are not
  yours by giving them their ordinary behaviour.
- **Or claim one placement.** `associateObject(id, x, y)` takes a single object
  and leaves every other one with that id alone. Use it whenever handing the
  behaviour back is not something a quest can do: rock 210 is the runite rock,
  so Jungle potion wanting the one at (428,819) must not claim the id or the
  rune rocks stop being mineable. `associateDoor(id, x, y)` does the same for a
  door: door 94 is the picklock door and Tribal totem only wants Handelmort's,
  so claiming the id would take the other three away from Thieving before
  Thieving is even written. There is no npc equivalent yet; add one the same way
  if a quest needs it.
- **Write placement claims out in full.** Collision checks read these calls
  straight out of the source, so a loop over an array of coordinates hides
  the claim from anything that scans for clashes.
- **You inherit the whole npc.** Taking the Oracle means reproducing everything
  she says when the quest is not running, because nothing else will say it.
- **Doors are a separate table.** `associateDoor(19)` and `associateObject(19)`
  claim different things: `DoorDef` and `GameObjectDef` both number from zero.
- **An npc handler wins.** `TalkToNpcHandler` tries `conf/server/NpcHandlers.xml`
  before quest dispatch, so registering a shopkeeper for an npc takes it away from
  any quest that claimed it. Check that file when a quest npc will not talk:
  Kaqemeex was registered to `GrimTele` and Doric to `Certer`, and both of their
  quests were silently dead because of it.

An npc that belongs to *two* quests can live in neither. Aggie, Wyson and Ned are
`NpcHandler`s in `src/org/rscdaemon/server/npchandler/` for that reason.

## Talking to another quest

Quest classes are compiled into the default package and loaded at runtime, so
nothing in `src/` can import one, and one quest cannot import another. Four
questions cross that line, all through `QuestManager`:

```java
qm.stageOf(Quests.PRINCE_ALI_RESCUE)     // -1 if not started
qm.completed(Quests.PRINCE_ALI_RESCUE)
qm.reached(Quests.DRAGON_SLAYER, "ship-ready")
qm.note(Quests.DRAGON_SLAYER, "ned-agreed")
```

`reached` and `note` are the named pair: a quest publishes the questions it will
answer and the events it will accept, and decides for itself what each means.
Ned asks Dragon slayer whether the ship floats and tells it that he agreed to
sail, without ever seeing one of its stage numbers.

**A quest may read another quest's progress. It may never write it.** `setStage()`
is called by the quest that owns it and by nothing else -- `note()` included, which
is a request, not an assignment.

## Where things are

- `Quest` is `src/org/rscdaemon/server/quest/Quest.java` -- `setStage`, `getStage`,
  `questStarted`, `completed`, `reached`, `note`, `sayMessage`, `sayNpcMessage`,
  `stopTalking`, and the `associate*`/`*Associated` pairs.
- `QuestTrigger` lists the trigger kinds `triggerEntity()` receives (`OBJECT_ACT1`,
  `OBJECT_ACT2`, `ITEM_ON_OBJECT`, `ITEM_ON_ITEM`, `DOOR_ACT1`, `NPC_KILLED`, and
  the rest).
- The seventeen free quests Jagex shipped are all here, and the members' quests
  are going in after them, in id order. Read one before writing one:
  `CooksAssistant` for the shape, `PiratesTreasure` for objects and doors,
  `ShieldOfArrav` for branching, `WitchsHouse` for a quest that spawns npcs and
  watches a drop, `DragonSlayer` for everything at once.
- `examples/MiningQuest.java` and `examples/TestQuest.java` are RSCD's originals.
  They are examples of the old API, not content, and they are in a subdirectory
  because `QuestLoader` only reads `quests/*.java` -- a directory deep enough not
  to be loaded and shallow enough to read. They were loading, and `TestQuest`
  claims Fred the Farmer, so Sheep Shearer and it were both answering him at
  once. Anything in this folder is live; put drafts in `examples/`.

## Dialogue comes from the transcripts

Every line in these quests is Jagex's, taken from recorded transcripts of the
original game. Where a line could not be recovered, the class
comment says so and says what was written instead. Keep that habit: a quest's
class comment is where its deviations from the real game are recorded, and every
one of them is a promise to fix it later.

## API drift

These quests predate the current server by some years. If you are porting an old
one, the renames that bit `TestQuest`:

| Old | Current |
| --- | --- |
| `QuestMenu.handleReply(int)` | `handleReply(int, String)` |
| `Player.getTalking()` | `Player.getNpc()` |
| `Player.incExp(skill, amount)` | `incExp(skill, amount, useFatigue)` |
| `Inventory.getFirstIndexById(id)` | `Inventory.remove(id, amount)`, or `getLastIndexById(id)` |
| `GameObject.getDef()` | `GameObject.getGameObjectDef()` |
| `setMenuHandler(null)` | `resetMenuHandler()` |
