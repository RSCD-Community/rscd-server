import java.util.ListIterator;

import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Waterfall quest. Released 24 September 2002, written by Thomas Woode.
 *
 * Almera's boy has gone treasure hunting in the river and cannot swim. Looking
 * for him gets the player as far as the tourist centre, whose bookcase holds
 * the history that turns the errand into the quest: Baxtorian hid a treasure
 * under his own waterfall, and only his wife Glarial could follow him in.
 *
 *     Almera 470   (656,448)      hudon 471   (664,464)
 *     golrie 475   (666,3526)     gerald 481  (654,500)
 *
 *     log raft 464   (660,449)    behind Almera's house
 *     leaflessTree   462 (662,463), 463 (662,467), 482 (659,471)
 *     Waterfall 469  (659,3304)   the ledge outside the dungeon
 *     Bookcase 470   (650,1435)   upstairs in the tourist centre
 *     Gravestone 479 (631,476)    Glarial's tomb, needs the pebble
 *     cupboard 507   (634,3295)   Glarial's urn
 *     Tomb 467       (646,3305)   Glarial's amulet
 *     crate 481      (655,3535)   large key, under the gnome village
 *     gate 480       (663,3530)   Golrie's cell
 *     crate 492      (650,3290)   an old key, waterfall dungeon
 *     Door 135       (668,3281)   the chalice corridor, locked
 *     doors 471      (659,3303)   the dungeon mouth, needs the amulet worn
 *
 * The chalice chamber is built twice, once out of reach and once in reach, and
 * the ritual moves the player between them:
 *
 *     out of reach   chalice 484 (665,3273)  statue 483 (665,3270)
 *                    stands 473..478 at (664,3271) (666,3271) (662,3273)
 *                                       (668,3273) (664,3276) (666,3276)
 *     in reach       chalice 485 (647,3269)  statue 483 (647,3266)
 *                    the same six stands, eighteen tiles east and four north
 *                    exit doors 486 (649,3275)
 *
 * The stage is a bit set. Three bits per stand hold which runes are on it, so
 * the eighteen placements survive being flushed out of the dungeon -- which is
 * what the real quest did, and the reason a player who has to fetch a second
 * amulet does not have to lay the runes again.
 *
 * Deviations:
 *
 *  - Being flushed puts the player north of the tourist centre at (650,485).
 *    Jagex's own accounts disagree with each other about where you wash up --
 *    the amulet page says near the fishing guild, the doors transcript says
 *    the tourist centre -- so the transcript wins, being the more specific.
 *
 *  - The odd looking wall in Glarial's tomb is left to WallObjectAction, which
 *    already knows that the landscape puts the same room on both sides of it.
 *    Nothing in the tomb is unreachable without it.
 *
 *  - The book prints its four pages into the chat. In the real client it
 *    opened a parchment window with a page turn.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class WaterfallQuest extends Quest {

    public final static int UID = Quests.WATERFALL_QUEST;

    private static final int ALMERA = 470, HUDON = 471;
    /**
     * The Tourist Information Centre south of the falls -- Almera's own
     * dialogue (has(MET_HUDON) branch) already sends the player there, but
     * Hadley himself had no dialogue at all before this fix, see
     * rsc-npc-no-purpose-sweep memory.
     */
    private static final int HADLEY = 472;
    private static final int GOLRIE = 475, GERALD = 481;

    private static final int RAFT = 464;
    private static final int[] TREES = { 462, 463, 482 };
    private static final int FALLS = 469;
    private static final int BOOKCASE = 470;
    private static final int GRAVESTONE = 479;
    private static final int CUPBOARD = 507, CUPBOARD_X = 634, CUPBOARD_Y = 3295;
    private static final int TOMB = 467;
    private static final int KEY_CRATE = 481, OLD_KEY_CRATE = 492;
    private static final int CELL_GATE = 480;
    private static final int DUNGEON_DOORS = 471;
    private static final int CHALICE_DOOR = 135, DOOR_X = 668, DOOR_Y = 3281;
    private static final int[] STANDS = { 473, 474, 475, 476, 477, 478 };
    private static final int STATUE = 483;
    private static final int CHALICE_HIGH = 484, CHALICE_LOW = 485;
    private static final int CHAMBER_EXIT = 486;

    private static final int AIR = 33, WATER = 32, EARTH = 34;
    private static final int[] RUNES = { AIR, WATER, EARTH };
    private static final String[] RUNE_NAMES = { "air rune", "Water Rune", "earth rune" };

    private static final int DIAMOND = 161, GOLD_BAR = 172, ROPE = 237;
    private static final int AMULET = 782, PEBBLE = 787, BOOK = 788;
    private static final int LARGE_KEY = 789, SEEDS = 796, OLD_KEY = 797;
    private static final int URN = 805;

    private static final int ATTACK = 0, STRENGTH = 2, HITS = 3;
    private static final int RANGED = 4, AGILITY = 16;

    /** Where the river puts you when it has had enough of you. */
    private static final int SHORE_X = 650, SHORE_Y = 485;
    /*
     * The three mounds in the river, and all three are unwalkable tiles ON
     * PURPOSE. The whole of the Baxtorian channel is impassable water in the
     * landscape -- there is no walkable square anywhere between the two banks
     * -- so a player put on a mound cannot take a step, which is exactly the
     * trap the quest is: the only way off is the dead tree beside you and a
     * rope. Each of the three sits next to its own tree (462 at (662,463),
     * 463 at (662,467), 482 at (659,471)) and clicking a tree does not need
     * walkable ground under you, so this works and moving them to the bank
     * would delete the crossing.
     */
    private static final int ISLAND1_X = 661, ISLAND1_Y = 463;
    private static final int ISLAND2_X = 662, ISLAND2_Y = 466;
    /* (659,470): level with the last tree at (659,471) -- one tile east and
       the click on it walks you nowhere useful. */
    private static final int ISLAND3_X = 659, ISLAND3_Y = 470;
    private static final int LEDGE_X = 659, LEDGE_Y = 3305;
    private static final int INSIDE_X = 659, INSIDE_Y = 3302;
    private static final int TOMB_X = 631, TOMB_Y = 3307;
    private static final int RITUAL_DX = -18, RITUAL_DY = -4;

    private static final int STARTED = 1;
    private static final int MET_HUDON = 2;
    private static final int READ_BOOK = 4;
    private static final int SEEN_GRAVE = 8;
    /** Stand i rune j lives at bit 4 + i*3 + j. */
    private static final int RUNE_SHIFT = 4;
    private static final int RUNES_ALL = 4194288;   /* bits 4..21 */
    private static final int STATUE_SET = 4194304;  /* bit 22 */
    private static final int FINISHED = 8388608;    /* bit 23 */

    public WaterfallQuest(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Waterfall quest");
        setFinalStage(FINISHED);
        associateNpc(ALMERA);
        associateNpc(HUDON);
        associateNpc(GOLRIE);
        associateNpc(GERALD);
        associateNpc(HADLEY);
        associateObject(RAFT);
        for (int i = 0; i < TREES.length; i++) {
            associateObject(TREES[i]);
        }
        associateObject(FALLS);
        associateObject(BOOKCASE);
        associateObject(GRAVESTONE);
        associateObject(CUPBOARD, CUPBOARD_X, CUPBOARD_Y);
        associateObject(TOMB);
        associateObject(KEY_CRATE);
        associateObject(OLD_KEY_CRATE);
        associateObject(CELL_GATE);
        associateObject(DUNGEON_DOORS);
        associateDoor(CHALICE_DOOR, DOOR_X, DOOR_Y);
        for (int i = 0; i < STANDS.length; i++) {
            associateObject(STANDS[i]);
        }
        associateObject(STATUE);
        associateObject(CHALICE_HIGH);
        associateObject(CHALICE_LOW);
        associateObject(CHAMBER_EXIT);
        /* Only for the book's "read" command. */
        associateItem(BOOK);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Investigate the death of elven leaders of old. Search for the elf king baxtorian's tomb and discover the mysterious hidden treasure of the waterfall.");
        setStartPoint("House near Baxtorian falls");
        setSpeakTo("Almera");
        setMissionLength("Long");
        require("Fight Level 110 giants");
        rewardItem(SEEDS, 40);
        /* Two of each, as two separate items, exactly as they were given. */
        rewardItem(DIAMOND, 1);
        rewardItem(DIAMOND, 1);
        rewardItem(GOLD_BAR, 1);
        rewardItem(GOLD_BAR, 1);
        rewardExp(STRENGTH, 250, 225);
        rewardExp(ATTACK, 250, 225);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Baxtorian waterfall quest");
    }

    // ------------------------------------------------------------- helpers --

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        setStage(questStarted() ? getStage() | bit : bit);
    }

    private boolean holding(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private void give(int id, int amount) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, amount));
        p.getActionSender().sendInventory();
    }

    private void take(int id, int amount) {
        Player p = getOwner();
        p.getInventory().remove(id, amount);
        p.getActionSender().sendInventory();
    }

    private void hurt(int damage) {
        Player p = getOwner();
        p.setCurStat(HITS, Math.max(0, p.getCurStat(HITS) - damage));
        p.getActionSender().sendStat(HITS);
    }

    private int runeBit(int stand, int rune) {
        return 1 << (RUNE_SHIFT + (stand * 3) + rune);
    }

    private int standIndex(int id) {
        for (int i = 0; i < STANDS.length; i++) {
            if (STANDS[i] == id) {
                return i;
            }
        }
        return -1;
    }

    private int runeIndex(int id) {
        for (int i = 0; i < RUNES.length; i++) {
            if (RUNES[i] == id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Over the falls and out. Four damage is what the recorded run took, and
     * it is a flat cost rather than a share of the player's hits.
     */
    private void flush() {
        Player p = getOwner();
        p.getActionSender().sendMessage("ouch!");
        hurt(4);
        p.getActionSender().sendMessage("you tumble over the water fall");
        p.getActionSender().sendMessage("and are washed up by the river side");
        p.teleport(SHORE_X, SHORE_Y, false);
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            Npc npc = (Npc) entity;
            switch (npc.getID()) {
                case ALMERA: almera(npc); return;
                case HUDON:  hudon(npc); return;
                case GOLRIE: golrie(npc); return;
                case GERALD: gerald(npc); return;
                case HADLEY: hadley(npc); return;
            }
            return;
        }
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_COMMAND) {
                readBook();
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.ITEM_ON_DOOR) {
            chaliceDoor(used);
            return;
        }
        if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2) {
            chaliceDoor(null);
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            itemOnObject(object, used);
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT1) {
            act1(object);
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            /* The old crate is the one quest object whose Search is its second
               command (WalkTo / Search) -- every other one searches on the
               first. */
            if (object.getID() == OLD_KEY_CRATE) {
                oldKeyCrate();
                return;
            }
            /* "jump to next" on the first two trees. */
            for (int i = 0; i < TREES.length; i++) {
                if (TREES[i] == object.getID()) {
                    getOwner().getActionSender().sendMessage("the tree is too far off to jump to");
                    getOwner().getActionSender().sendMessage("you need someway to pull yourself across");
                    return;
                }
            }
        }
    }

    private void act1(GameObject object) {
        Player p = getOwner();
        int id = object.getID();
        for (int i = 0; i < TREES.length; i++) {
            if (TREES[i] == id) {
                p.getActionSender().sendMessage("you jump into the wild rapids");
                flush();
                return;
            }
        }
        switch (id) {
            case RAFT:           board(); return;
            case FALLS:          p.getActionSender().sendMessage("you jump into the wild rapids");
                                 flush(); return;
            case BOOKCASE:       bookcase(); return;
            case GRAVESTONE:     readGravestone(); return;
            case CUPBOARD:       cupboard(); return;
            case TOMB:           tomb(); return;
            case KEY_CRATE:      largeKeyCrate(); return;
            case OLD_KEY_CRATE:  oldKeyCrate(); return;
            case CELL_GATE:      cellGate(object); return;
            case DUNGEON_DOORS:  dungeonDoors(); return;
            case CHALICE_LOW:    touchChalice(); return;
            case CHAMBER_EXIT:   p.getActionSender().sendSound("opendoor");
                                 p.getActionSender().sendMessage("you go through the doors");
                                 /* Back out where the chamber was entered: the
                                    exit doors sit where the corridor door 135
                                    stands in the room's other copy, not at the
                                    dungeon mouth. */
                                 p.teleport(DOOR_X, DOOR_Y, false); return;
        }
    }

    private void itemOnObject(GameObject object, InvItem used) {
        Player p = getOwner();
        if (used == null) {
            return;
        }
        int id = object.getID();
        for (int i = 0; i < TREES.length; i++) {
            if (TREES[i] == id) {
                rope(i, used);
                return;
            }
        }
        int stand = standIndex(id);
        if (stand >= 0) {
            placeRune(stand, used);
            return;
        }
        switch (id) {
            case GRAVESTONE:  pebble(used); return;
            case STATUE:      amuletOnStatue(used); return;
            case CHALICE_LOW: urnOnChalice(used); return;
        }
        p.getActionSender().sendMessage("Nothing interesting happens");
    }

    // ---------------------------------------------------------- the river --

    private void board() {
        Player p = getOwner();
        if (!questStarted()) {
            p.getActionSender().sendMessage("The raft belongs to the house nearby");
            return;
        }
        p.getActionSender().sendMessage("you board the raft");
        p.getActionSender().sendMessage("and push off from the bank");
        p.getActionSender().sendMessage("the current is much stronger than you thought");
        p.getActionSender().sendMessage("@red@the raft smashes into a small island");
        p.teleport(ISLAND1_X, ISLAND1_Y, false);
        /* Hudon stands across the shore, out of click-to-talk pathing range,
           and the walkthrough has the plea play out the moment you crash --
           so the scene is scripted, the same as the Grand Tree jail. */
        if (!has(MET_HUDON)) {
            Npc hudon = world.getNpc(HUDON,
                ISLAND1_X - 8, ISLAND1_X + 8, ISLAND1_Y - 8, ISLAND1_Y + 8);
            if (hudon != null) {
                hudon(hudon);
            }
        }
    }

    /**
     * The first two trees are a crossing and keep the rope; the third is a
     * climb down into the falls and eats it.
     */
    private void rope(int tree, InvItem used) {
        Player p = getOwner();
        if (used.getID() != ROPE) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        p.getActionSender().sendMessage("you tie one end of the rope around the tree");
        if (tree < 2) {
            p.getActionSender().sendMessage("you tie the other end into a loop");
            p.getActionSender().sendMessage("and throw it towards the other dead tree");
            p.getActionSender().sendMessage("the rope loops around the tree");
            p.getActionSender().sendMessage("you lower yourself into the rapidly flowing stream");
            p.incExp(RANGED, 25, false);
            p.incExp(AGILITY, 8, false);
            p.getActionSender().sendStat(RANGED);
            p.getActionSender().sendStat(AGILITY);
            p.getActionSender().sendMessage("you manage to pull yourself over to the land mound");
            if (tree == 0) {
                p.teleport(ISLAND2_X, ISLAND2_Y, false);
            } else {
                p.teleport(ISLAND3_X, ISLAND3_Y, false);
            }
            return;
        }
        take(ROPE, 1);
        p.getActionSender().sendMessage("and begin to lower yourself down the waterfall");
        p.getActionSender().sendMessage("you gently drop to the rock below");
        p.incExp(STRENGTH, 50, false);
        p.incExp(AGILITY, 20, false);
        p.getActionSender().sendStat(STRENGTH);
        p.getActionSender().sendStat(AGILITY);
        p.getActionSender().sendMessage("under the waterfall there is a secret passage");
        p.teleport(LEDGE_X, LEDGE_Y, false);
    }

    // --------------------------------------------------------- the history --

    private void bookcase() {
        Player p = getOwner();
        if (holding(BOOK)) {
            p.getActionSender().sendMessage("you search the bookcase");
            p.getActionSender().sendMessage("but find nothing else of interest");
            return;
        }
        p.getActionSender().sendMessage("you search the bookcase");
        p.getActionSender().sendMessage("and find a book on elven history");
        give(BOOK, 1);
    }

    /**
     * The book is interactive, recorded on video: opening it prints two lines
     * about the missing pages and then offers the four chapter titles as a
     * click menu, each chapter reading out into the chat at dialogue pace.
     * The original chat-burst version fired every line in one tick and the
     * client swallowed it, which read as the book doing nothing at all.
     */
    private void readBook() {
        Player p = getOwner();
        p.getActionSender().sendMessage("the book is old with many pages missing");
        p.getActionSender().sendMessage("a few are translated from elven into common tongue");
        if (questStarted() && !completed()) {
            set(READ_BOOK);
        }
        new Conversation(p, null)
            .options(new Choice("the missing relics", "the sonnet of baxtorian",
                                "the power of nature", "ode to eternity") {
                /* Each chapter is a "Click here to close window" modal, text
                   transcribed verbatim from recorded footage of the original
                   client -- it is fuller than the OpenRSC rendering of the
                   same book. */
                public void picked(int option, Conversation c) {
                    /* Whole paragraphs, no forced breaks: the client centres
                       and word-wraps them itself, which is what the original
                       pages look like. "% %" is a blank line -- a bare "%%"
                       trips the wrap loop and draws the second '%' literally,
                       because the break consumes only the character it broke
                       on. */
                    String page = null;
                    switch (option) {
                        case 0:
                            page = "@yel@The missing relics% %"
                                + "Many artifacts of elven history were lost after the second age. "
                                + "The greatest loss to our collections of elf history were the "
                                + "hidden treasures of Baxtorian.% %"
                                + "Some believe these treasures are still unclaimed, but it is more "
                                + "commonly believed that dwarf miners recovered the treasure at "
                                + "the beginning of the third age.% %"
                                + "Another great loss was Glarial's pebble, a key which allowed her "
                                + "ancestors to visit her tomb. The stone was stolen by a gnome "
                                + "family over a century ago.% %"
                                + "It is believed that the gnomes ancestor Gorlie still has the stone "
                                + "hidden in the caves under the gnome tree village.";
                            break;
                        case 1:
                            page = "@yel@The sonnet of Baxtorian% %"
                                + "The love between Baxtorian and Glarial was said to have lasted "
                                + "over a century. They lived a peaceful life learning and teaching "
                                + "the laws of nature.% %"
                                + "When Baxtorian's kingdom was invaded by the dark forces he left "
                                + "on a five year campaign. He returned to find his people "
                                + "slaughtered and his wife taken by the enemy.% %"
                                + "After years of searching for his love he finally gave up, he "
                                + "returned to the home he made for himself and Glarial under the "
                                + "baxtorian waterfall. Once he entered he never returned.% %"
                                + "Only glarial had the power to also enter the waterfall. Since "
                                + "Baxtorian entered no one but her can follow him in, it's as if the "
                                + "powers of nature still work to protect him.";
                            break;
                        case 2:
                            page = "@yel@The power of nature% %"
                                + "Glarial and Baxtorian were masters of nature. Trees would grow, "
                                + "mountains form and rivers flood all to there command. Baxtorian "
                                + "in particular had perfected rune lore. It was said that he could "
                                + "use the stones to control water, earth and air.";
                            break;
                        case 3:
                            page = "@yel@Ode to eternity% %"
                                + "A short piece written by Baxtorian himself% %"
                                + "What care I for this mortal coil, where treasures are yet so frail, "
                                + "for it is you that is my life blood, the wine to my holy grail% %"
                                + "and if i see the judgement day, when the gods fill the air with "
                                + "dust, i'll happily choke on your memory, as my kingdom turns to "
                                + "rust.";
                            break;
                    }
                    if (page != null) {
                        c.getPlayer().getActionSender().sendAlert(page, true);
                    }
                }
            })
            .start();
    }

    // ----------------------------------------------------- the gnome caves --

    private void largeKeyCrate() {
        Player p = getOwner();
        if (!has(READ_BOOK) || holding(LARGE_KEY)) {
            p.getActionSender().sendMessage("the crate is empty");
            return;
        }
        p.getActionSender().sendMessage("you search the crate");
        p.getActionSender().sendMessage("and find a large key");
        give(LARGE_KEY, 1);
    }

    /**
     * Golrie has locked himself in and lost his own key, so the whole scene
     * hangs off the gate rather than off him. He is three tiles away and does
     * the talking when he can be seen.
     */
    private void cellGate(final GameObject gate) {
        Player p = getOwner();
        /* Leaving is never gated: a player inside the cell must always be able
           to let themselves back out, whatever the quest state says. */
        if (p.getY() <= 3529) {
            p.getActionSender().sendSound("opendoor");
            p.getActionSender().sendMessage("you open the gate and step through");
            world.registerGameObject(new GameObject(gate.getLocation(),
                181, gate.getDirection(), gate.getType()));
            world.delayedSpawnObject(gate.getLoc(), 1000);
            p.teleport(663, 3531, false);
            return;
        }
        Npc him = nearby(GOLRIE);
        if (him == null) {
            p.getActionSender().sendMessage("The gate is locked");
            return;
        }
        if (!has(READ_BOOK) || holding(PEBBLE) || completed()) {
            new Conversation(p, him)
                .npc("what are you doing down here")
                .npc("leave before you get yourself into trouble")
                .start();
            return;
        }
        if (!holding(LARGE_KEY)) {
            new Conversation(p, him)
                .player("are you ok?")
                .npc("it's just those blasted hobgoblins")
                .npc("i locked myself in here for protection")
                .npc("but i've left the key somewhere")
                .npc("and now i'm stuck")
                .player("okay, i'll have a look for a key")
                .start();
            return;
        }
        new Conversation(p, him)
            .player("are you ok?")
            .npc("it's just those blasted hobgoblins")
            .npc("i locked myself in here for protection")
            .npc("but i've left the key somewhere")
            .npc("and now i'm stuck")
            .player("i found a key")
            .npc("well don't wait all day")
            .npc("give it a try")
            .message("you unlock the gate and step inside")
            .take(LARGE_KEY, 1)
            /* Swing the gate open and actually go through: 181 is the
               open-gate model doGate swaps in, delayedSpawnObject shuts it
               again a second later, and the teleport is what moves the player
               -- the room is the pocket north of the gate at (663,3530), and
               the closed gate blocks the pathfinder, which is right: no quest,
               no entry. Same treatment as the Bar crawl gate. */
            .then(new Effect() {
                public void run(Conversation c) {
                    Player pl = c.getPlayer();
                    pl.getActionSender().sendSound("opendoor");
                    world.registerGameObject(new GameObject(gate.getLocation(),
                        181, gate.getDirection(), gate.getType()));
                    world.delayedSpawnObject(gate.getLoc(), 1000);
                    pl.teleport(663, pl.getY() >= 3530 ? 3529 : 3531, false);
                }
            })
            .player("is your name golrie?")
            .npc("that's me")
            .npc("i've been stuck in here for weeks")
            .npc("those goblins are trying to steal my families heirlooms")
            .npc("my grandad gave me all sorts of old junk")
            .player("do you mind if i have a look?")
            .npc("no, of course not")
            .player("could i take this old pebble?")
            .npc("oh that, yes have it")
            .npc("it's just some old elven junk i believe")
            .give(new InvItem(PEBBLE, 1))
            .npc("well thanks again for the key")
            .npc("i think i'll wait in here until those goblins get bored and leave")
            .player("okay, take care golrie")
            .start();
    }

    private void golrie(Npc npc) {
        Player p = getOwner();
        if (!has(READ_BOOK) || completed()) {
            new Conversation(p, npc)
                .npc("what are you doing down here")
                .npc("leave before you get yourself into trouble")
                .start();
            return;
        }
        if (holding(PEBBLE)) {
            new Conversation(p, npc)
                .npc("i think i'll wait in here until those goblins get bored and leave")
                .start();
            return;
        }
        /* Losing the pebble is recoverable: he hands out another. */
        new Conversation(p, npc)
            .player("golrie, i've lost that old pebble")
            .npc("no matter, there's plenty of old junk down here")
            .npc("take another, it's no use to me")
            .give(new InvItem(PEBBLE, 1))
            .start();
    }

    // -------------------------------------------------------- Glarial's tomb --

    private void readGravestone() {
        Player p = getOwner();
        p.getActionSender().sendMessage("the grave is covered in elven script");
        p.getActionSender().sendMessage("some of the writing is in common tongue, it reads");
        p.getActionSender().sendMessage("here lies glarial, wife of baxtorian");
        p.getActionSender().sendMessage("true friend of nature in life and death");
        p.getActionSender().sendMessage("may she now rest knowing");
        p.getActionSender().sendMessage("only visitors with peaceful intent can enter");
    }

    private void pebble(InvItem used) {
        Player p = getOwner();
        if (used.getID() != PEBBLE) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        p.getActionSender().sendMessage("you place the pebble in the gravestones small indent");
        p.getActionSender().sendMessage("it fits perfectly");
        if (armed()) {
            p.getActionSender().sendMessage("but nothing happens");
            return;
        }
        p.getActionSender().sendMessage("You hear a loud creek");
        p.getActionSender().sendMessage("the stone slab slides back revealing a ladder down");
        p.getActionSender().sendMessage("you climb down to an underground passage");
        if (questStarted() && !completed()) {
            set(SEEN_GRAVE);
        }
        p.teleport(TOMB_X, TOMB_Y, false);
    }

    /** Glarial only lets in visitors with peaceful intent, meaning no kit. */
    private boolean armed() {
        ListIterator<InvItem> i = getOwner().getInventory().iterator();
        while (i.hasNext()) {
            if (i.next().isWieldable()) {
                return true;
            }
        }
        return false;
    }

    private void cupboard() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the cupboard");
        if (holding(URN)) {
            p.getActionSender().sendMessage("it's empty");
            return;
        }
        p.getActionSender().sendMessage("and find a metel urn");
        give(URN, 1);
    }

    private void tomb() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the coffin");
        if (holding(AMULET)) {
            p.getActionSender().sendMessage("it's empty");
            p.getActionSender().sendMessage("you close the coffin");
            return;
        }
        p.getActionSender().sendMessage("inside you find a small amulet");
        p.getActionSender().sendMessage("you take the amulet and close the coffin");
        give(AMULET, 1);
    }

    // ---------------------------------------------------- waterfall dungeon --

    private void dungeonDoors() {
        Player p = getOwner();
        p.getActionSender().sendMessage("the doors begin to open");
        if (!p.getInventory().wielding(AMULET)) {
            p.getActionSender().sendMessage("suddenly the corridor floods");
            p.getActionSender().sendMessage("flushing you back into the river");
            flush();
            return;
        }
        p.getActionSender().sendMessage("You go through the door");
        p.getActionSender().sendMessage("the doors close behind you");
        p.teleport(INSIDE_X, INSIDE_Y, false);
    }

    private void oldKeyCrate() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the crate");
        if (holding(OLD_KEY)) {
            p.getActionSender().sendMessage("but you find nothing");
            return;
        }
        p.getActionSender().sendMessage("and find an old key");
        give(OLD_KEY, 1);
    }

    /**
     * The corridor door. It is claimed at its own coordinates, so the rest of
     * the world's ordinary doors of the same id are untouched.
     */
    private void chaliceDoor(InvItem used) {
        Player p = getOwner();
        if (used != null && used.getID() != OLD_KEY) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!holding(OLD_KEY)) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        p.getActionSender().sendSound("opendoor");
        p.getActionSender().sendMessage("you open the door with the key");
        p.getActionSender().sendMessage("You go through the door");
        /* The door stands between (668,3281) and (668,3280). Once the ground
           has been raised the chamber the player must arrive in is the other
           copy of the room, shifted by the ritual offset -- otherwise a player
           flushed out after the statue would return to a chalice they can
           never reach. */
        if (p.getY() >= DOOR_Y && has(STATUE_SET)) {
            p.teleport(DOOR_X + RITUAL_DX, DOOR_Y - 1 + RITUAL_DY, false);
            return;
        }
        p.teleport(DOOR_X, p.getY() >= DOOR_Y ? DOOR_Y - 1 : DOOR_Y, false);
    }

    // ---------------------------------------------------------- the ritual --

    private void placeRune(int stand, InvItem used) {
        Player p = getOwner();
        int rune = runeIndex(used.getID());
        if (rune < 0) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        int bit = runeBit(stand, rune);
        if (has(bit)) {
            p.getActionSender().sendMessage("you have already placed a " + RUNE_NAMES[rune] + " here");
            return;
        }
        if (!questStarted()) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        take(RUNES[rune], 1);
        p.getActionSender().sendMessage("you place the " + RUNE_NAMES[rune] + " on the stand");
        p.getActionSender().sendMessage("the rune stone crumbles into dust");
        set(bit);
    }

    private void amuletOnStatue(InvItem used) {
        Player p = getOwner();
        if (used.getID() != AMULET) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        /* The ground is already up. Both copies of the statue share an id, so
           without this a spare amulet used on the raised room's statue would
           be eaten and the player shifted clean out of the chamber. */
        if (has(STATUE_SET)) {
            p.getActionSender().sendMessage("you place the amulet around the statue");
            p.getActionSender().sendMessage("but nothing happens");
            return;
        }
        if ((getStage() & RUNES_ALL) != RUNES_ALL) {
            p.getActionSender().sendMessage("you place the amulet around the statue");
            p.getActionSender().sendMessage("but nothing happens");
            p.getActionSender().sendMessage("the stands around you are still bare");
            return;
        }
        take(AMULET, 1);
        p.getActionSender().sendMessage("you place the amulet around the statue");
        p.getActionSender().sendMessage("you hear a loud rumble beneath you");
        p.getActionSender().sendMessage("the ground raises up before you");
        set(STATUE_SET);
        p.teleport(p.getX() + RITUAL_DX, p.getY() + RITUAL_DY, false);
    }

    private void touchChalice() {
        Player p = getOwner();
        if (completed()) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        p.getActionSender().sendMessage("as you touch the chalice it tips over");
        p.getActionSender().sendMessage("it falls to the floor");
        p.getActionSender().sendMessage("you hear a gushing of water");
        p.getActionSender().sendMessage("water floods into the cavern");
        flush();
    }

    private void urnOnChalice(InvItem used) {
        Player p = getOwner();
        if (used.getID() != URN) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (completed()) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        take(URN, 1);
        p.getActionSender().sendMessage("you carefully poor the ashes in the chalice");
        p.getActionSender().sendMessage("as you remove the baxtorian treasure");
        p.getActionSender().sendMessage("the chalice remains standing");
        p.getActionSender().sendMessage("inside you find a mithril case");
        p.getActionSender().sendMessage("containing 40 seeds");
        p.getActionSender().sendMessage("two diamond's and two gold bars");
        setStage(FINISHED);
    }

    // --------------------------------------------------------------- talk --

    private Npc nearby(int id) {
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            if (n.getID() == id) {
                return n;
            }
        }
        return null;
    }

    private void almera(Npc npc) {
        Player p = getOwner();
        if (completed() || has(SEEN_GRAVE)) {
            new Conversation(p, npc)
                .player("hello almera")
                .npc("hello adventurer")
                .npc("how's your treasure hunt going?")
                .player("oh, i'm just sight seeing")
                .npc("no adventurer stays here this long just to sight see")
                .npc("but your business is yours alone")
                .npc("if you need to use the raft go ahead")
                .npc("but please try not crash it this time")
                .player("thanks almera")
                .start();
            return;
        }
        if (has(READ_BOOK)) {
            new Conversation(p, npc)
                .player("hello again almera")
                .npc("well hello again brave adventurer")
                .npc("are you enjoying the tranquil scenery of these parts?")
                .player("yes, very relaxing")
                .npc("well i'm glad to hear it")
                .npc("the authorities wanted to dig up this whole area for a mine")
                .npc("but the few locals who lived here wouldn't budge and they gave up")
                .player("good for you")
                .npc("good for all of us")
                .start();
            return;
        }
        if (has(MET_HUDON)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("well hello, you're still around then")
                .player("i saw hudon by the river but he refused to come back with me")
                .npc("yes he told me")
                .npc("the foolish lad came in drenched to the bone")
                .npc("he had fallen into the waterfall, lucky he wasn't killed")
                .npc("now he can spend the rest of the summer in his room")
                .player("any ideas on what i could do while i'm here?")
                .npc("why don't you visit the tourist centre south of the waterfall?")
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .player("hello almera")
                .npc("hello brave adventurer")
                .npc("have you seen my boy yet?")
                .player("i'm afraid not, but i'm sure he hasn't gone far")
                .npc("i do hope so")
                .npc("you can't be too careful these days")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello madam")
            .npc("ah, hello there")
            .npc("nice to see an outsider for a change")
            .npc("are you busy young man?, i have a problem")
            .options(new Choice("how can i help?", "i'm afraid i'm in a rush") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("oh okay, never mind");
                        return;
                    }
                    c.npc("it's my son hudon, he's always getting into trouble")
                     .npc("the boy's convinced there's hidden treasure in the river")
                     .npc("and i'm a bit worried about his safety")
                     .npc("the poor lad can't even swim")
                     .player("i could go and take a look for you if you like")
                     .npc("would you kind sir?")
                     .npc("you can use the small raft out back if you wish")
                     .npc("do be careful, the current down stream is very strong")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(STARTED);
                         }
                     });
                }
            })
            .start();
    }

    private void hadley(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("ah, sir, back again i see")
                .npc("you'll be pleased to know glarial's remains were recovered")
                .npc("and finally brought home, where they belong")
                .npc("there's a page about it upstairs, if you're interested")
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("welcome, sir, to the tourist information centre")
                .npc("here for the treasure, are you? everyone is")
                .player("is there really treasure down there?")
                .npc("decades of treasure hunters have come through here")
                .npc("not one of them ever found a thing, sir")
                .npc("if you want my advice, take a look upstairs at the archives")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("welcome, sir, to the tourist information centre")
            .npc("care to hear the legend of baxtorian and glarial?")
            .options(new Choice("yes please", "not right now") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("suit yourself, sir");
                        return;
                    }
                    c.npc("long ago, the elf king baxtorian fought off invaders here")
                     .npc("he returned from battle to find his love glarial captured")
                     .npc("in his grief he retreated into the sanctuary under the falls")
                     .npc("and he never came out again, sir")
                     .npc("only glarial herself could ever follow him in there");
                }
            })
            .start();
    }

    private void hudon(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("you stole my treasure i saw you")
                .player("i'll make sure it goes to a good cause")
                .npc("hmmmm")
                .start();
            return;
        }
        if (has(SEEN_GRAVE)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("not you still, why don't you give up?")
                .player("and miss all the fun!")
                .npc("you do understand that anything you find you have to share it with me")
                .player("why's that?")
                .npc("because i told you about the treasure")
                .player("well, i wouldn't count on it")
                .npc("that's not fair")
                .player("neither is life kid")
                .start();
            return;
        }
        if (has(READ_BOOK)) {
            new Conversation(p, npc)
                .player("hello hudon")
                .npc("oh it's you")
                .npc("trying to find my treasure again are you?")
                .player("i didn't know it belonged to you")
                .npc("it will do when i find it")
                .npc("i just need to get into this blasted waterfall")
                .npc("i've been washed downstream three times already")
                .start();
            return;
        }
        if (has(MET_HUDON)) {
            new Conversation(p, npc)
                .player("so your still here")
                .npc("i'll find that treasure soon")
                .npc("just you wait and see")
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .player("hello son, are you okay?")
                .npc("it looks like you need the help")
                .player("your mum sent me to find you")
                .npc("don't play nice with me")
                .npc("i know your looking for the treasure")
                .player("where is this treasure you talk of?")
                .npc("just because i'm small doesn't mean i'm dumb")
                .npc("if i told you, you would take it all for yourself")
                .player("maybe i could help")
                .npc("i'm fine alone")
                .then(new Effect() {
                    public void run(Conversation c) {
                        set(MET_HUDON);
                    }
                })
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello there")
            .npc("what do you want?")
            .player("nothing, just passing by")
            .start();
    }

    private void gerald(Npc npc) {
        Player p = getOwner();
        if (questStarted()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello traveller")
                .npc("are you here to fish or to hunt for treasure?")
                .player("why do you say that?")
                .npc("adventurers pass through here every week")
                .npc("they never find anything though")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello there")
            .npc("good day to you traveller")
            .npc("are you here to fish or just looking around?")
            .npc("i've caught some beauties down here")
            .player("really")
            .npc("the last one was this big")
            .start();
    }
}
