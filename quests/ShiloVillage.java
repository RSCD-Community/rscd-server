import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Shilo village. Released 27 January 2003, written by Tytn Hays, and the first
 * quest Jagex hung entirely off Agility: three of its obstacles want level 32
 * and there is no way round any of them.
 *
 * Rashiliyia, queen of the undead, has overrun the village and Mosol Rei is
 * holding the gate on his own. The way to stop her is archaeology: dig out the
 * buried temple of Ah Za Rhoon, bury the priest she hanged there, take the
 * bone he gives you and carve it into the key to her tomb, and put her own
 * remains on her son's dolmen so the spirit can rest.
 *
 * Everything below is on Karamja and its three dungeons, which live on the
 * underground plane at y + 2832:
 *
 *     Mosol                 npc 539, (382,852), outside the Shilo gate
 *     Trufitus              npc 517, (436,751), Tai Bwo Wannai
 *     Yanni                 npc 624, (415,833), the antique dealer
 *     Zadimus               npc 589, summoned at the statue
 *     Rashiliyia            npc 533, summoned at the Bervirius dolmen
 *     Nazastarool           npc 613 zombie, 614 skeleton, 615 ghost, all 83
 *
 *     Stone-Plaque          item 958      Tattered Scroll   item 959
 *     Crumpled Scroll       item 960      Tomb Notes        item 961
 *     Zadimus Corpse        item 962      Locating Crystal  item 972
 *     Sword Pommel          item 973      Bone Shard        item 974
 *     Bone Beads            item 976      Rashiliya Corpse  item 977
 *     Bone Key              item 835      Beads of the dead item 852
 *
 *     Bumpy Dirt            object 651, (353,816), into Ah Za Rhoon
 *     tree                  object 573, (351,781) and (350,781)
 *     Hillside Entrance     object 572, (351,780), spawned by this quest
 *     Well stacked rocks    object 688, (473,825), into Bervirius' tomb
 *     Rocks / Blockade      objects 710 and 691, the way onto Cairn Isle
 *     Handholds             object 690, (466,3675), out of Bervirius' tomb
 *     Tomb Dolmen           object 689 Bervirius, 724 Rashiliyia
 *     Smashed table         object 697, (355,3668), crafts into the raft
 *     Stone                 object 674, (359,3665), chisels into the plaque
 *     Pile of rubble        object 670 the link, 683 the Tattered Scroll
 *     Rotten Gallows        object 682, (359,3722), the Zadimus Corpse
 *     sacks                 object 783, (358,3709), the Crumpled Scroll
 *     Wet rocks             object 696, the second way out of Ah Za Rhoon
 *     Doors                 object 583, (348,3608), the tomb's only exit
 *     Metalic Dungeon Gate  object 577, (348,3615), forced by brute strength
 *     Rocks                 object 719, (348,3617), down into the tunnels
 *     Tomb Doors            object 794, (377,3632), opened with three bones
 *     A farm cart           object 613, (384,851), Mosol's warning note
 *
 * Object 572 has no placement in RSCD or in vanilla's own dump, because the
 * Hillside Entrance is not there until the trees in front of it are searched.
 * This quest registers it at (351,780), the position the wiki's location map
 * gives, the first time anyone finds it.
 *
 * Deviations from vanilla:
 * the Agility level checks are kept as Jagex wrote them and Agility does not
 * exist yet, so the quest cannot presently be finished; and the choking that
 * vanilla applies continuously inside Rashiliyia's tomb is applied here at the
 * three points the transcripts actually show it, because a per-tick hook for a
 * region is not something the quest framework offers.
 */
public class ShiloVillage extends Quest {

    private static final int UID = Quests.SHILO_VILLAGE;

    private static final int MOSOL = 539, TRUFITUS = 517, YANNI = 624;
    private static final int ZADIMUS = 589, RASHILIYIA = 533;
    private static final int NAZ_ZOMBIE = 613, NAZ_SKELETON = 614, NAZ_GHOST = 615;
    /**
     * Post-quest transport, not part of the quest's own plot -- only usable
     * once Shilo Village is complete. One-way to north of Brimhaven for
     * 500gp; there is no return cart back, per the real transcript. The driver
     * had no dialogue at all in upstream RSCDaemon.
     */
    private static final int CART_DRIVER = 619;
    private static final int CART_FARE = 500;
    /*
     * "North of Brimhaven" per the source -- no exact wiki tile found, so
     * this is anchored off TribalTotem.java's own verified Brimhaven
     * building (Kangai Mau, npc 332, (452,687)) and checked walkable via
     * tools/collision.py before use, not guessed blind. See
     * rsc-quest-completeness memory on why that check matters here.
     */
    private static final int BRIMHAVEN_X = 452, BRIMHAVEN_Y = 680;

    private static final int BONES = 20, CHISEL = 167, SPADE = 211, ROPE = 237;
    private static final int CANDLE = 599, LIT_CANDLE = 601;
    private static final int BONE_KEY = 835, BEADS = 852;
    private static final int PLAQUE = 958, TATTERED = 959, CRUMPLED = 960, NOTES = 961;
    private static final int ZADIMUS_CORPSE = 962, CRYSTAL = 972, POMMEL = 973;
    private static final int SHARD = 974, BONE_BEADS = 976, REMAINS_ITEM = 977;
    private static final int WIRE = 979, COINS = 10;

    private static final int DIRT = 651, DIRT_X = 353, DIRT_Y = 816;
    private static final int TREE = 573, TREE_A_X = 351, TREE_A_Y = 781;
    private static final int TREE_B_X = 350, TREE_B_Y = 781;
    private static final int HILLSIDE = 572, HILLSIDE_X = 351, HILLSIDE_Y = 780;
    private static final int STACKED = 688, STACKED_X = 473, STACKED_Y = 825;
    private static final int BLOCKADE = 691, BLOCKADE_X = 459, BLOCKADE_Y = 828;
    private static final int CLIFF = 710;
    private static final int HANDHOLDS = 690;
    private static final int DOLMEN_B = 689, DOLMEN_B_X = 464, DOLMEN_B_Y = 3672;
    private static final int TABLE = 697, TABLE_X = 355, TABLE_Y = 3668;
    private static final int STONE = 674, STONE_X = 359, STONE_Y = 3665;
    private static final int RUBBLE = 670, RUBBLE_A_X = 357, RUBBLE_A_Y = 3668;
    private static final int RUBBLE_B_X = 348, RUBBLE_B_Y = 3708;
    private static final int SCROLL_RUBBLE = 683, SCROLL_RUBBLE_X = 342, SCROLL_RUBBLE_Y = 3710;
    private static final int GALLOWS = 682, GALLOWS_X = 359, GALLOWS_Y = 3722;
    private static final int SACKS = 783, SACKS_X = 358, SACKS_Y = 3709;
    private static final int WET_ROCKS = 696;
    private static final int EXIT_DOORS = 583, EXIT_X = 348, EXIT_Y = 3608;
    private static final int METAL_GATE = 577, GATE_X = 348, GATE_Y = 3615;
    private static final int TOMB_ROCKS = 719;
    private static final int TOMB_DOORS = 794, TOMB_DOORS_X = 377, TOMB_DOORS_Y = 3632;
    private static final int DOLMEN_R = 724, DOLMEN_R_X = 378, DOLMEN_R_Y = 3622;
    private static final int CART = 613, CART_X = 384, CART_Y = 851;

    private static final int STATUE_X = 447, STATUE_Y = 751;

    /** Where the fissure under the Bumpy Dirt drops you. */
    private static final int AZR_X = 352, AZR_Y = 3653;
    /** Where the raft and the wet rocks put you back out, by the waterfall. */
    private static final int AZR_OUT_X = 348, AZR_OUT_Y = 810;
    /** The far side of the Ah Za Rhoon rubble, in each direction. */
    private static final int RUBBLE_OUT_A_X = 356, RUBBLE_OUT_A_Y = 3668;
    private static final int RUBBLE_OUT_B_X = 347, RUBBLE_OUT_B_Y = 3709;
    /** Inside Bervirius' tomb, and back out on Cairn Isle. */
    private static final int BERV_X = 466, BERV_Y = 3674;
    private static final int BERV_OUT_X = 472, BERV_OUT_Y = 825;
    /** Rashiliyia's tomb: the entrance chamber, and the jungle outside it. */
    private static final int TOMB_X = 348, TOMB_Y = 3612;
    /* The hillside tile, not the tree tile. Tree 573 stands on (351,781) and
     * (350,781) and is solid, so walking out of the tomb put the player inside
     * it; (351,780) is the mouth the player went in by. */
    private static final int TOMB_OUT_X = 351, TOMB_OUT_Y = 780;
    /** Past the Metalic gate, and the bottom of the rocks below it. */
    private static final int PAST_GATE_X = 348, PAST_GATE_Y = 3616;
    private static final int TUNNELS_X = 348, TUNNELS_Y = 3621;
    /** Inside the Shilo gates, where Mosol leaves you. */
    private static final int VILLAGE_X = 396, VILLAGE_Y = 851;

    private static final int DEFENSE = 1, STRENGTH = 2, HITS = 3, PRAYER = 5;
    private static final int CRAFTING = 12, AGILITY = 16;

    private static final int AGILITY_LEVEL = 32, CRAFT_LEVEL = 20;
    private static final int CARVE_EXP = 35, PLAQUE_EXP = 10;

    /**
     * What each of the quest's agility obstacles is worth.
     *
     * These used to be one shared figure with a comment claiming it was the
     * displayed award times four -- it was not; incExp takes the displayed
     * figure and the constant went straight into it, so every obstacle here
     * paid four times what Classic paid, and paid the same amount as every
     * other obstacle besides. The recovered table gives them each their own
     * number and three of them are worth nothing at all, which is why they are
     * written out separately rather than shared:
     *
     *     Pile of rubble, Ah Za Rhoon      2.5   ->  3
     *     Rock climb, Cairn Isle           2.5   ->  3
     *     Wet rocks, Ah Za Rhoon exit       25   -> 25
     *     Well stacked rocks                 0
     *     Handholds, Tomb of Bervirius       0
     *     Bridge blockade                    0
     *
     * The granite fissure and the rocks down into the tombs are not in the
     * table at all, in a table that lists the worthless obstacles explicitly,
     * so they pay nothing.
     */
    private static final int RUBBLE_EXP = 3, CLIFF_EXP = 3, WET_ROCKS_EXP = 25;
    private static final int STACKED_EXP = 0, BLOCKADE_EXP = 0, FISSURE_EXP = 0;
    private static final int TOMB_ROCKS_EXP = 0;

    private static final int BOSS_TIMEOUT = 250000;
    private static final int GHOST_TIMEOUT = 30000;

    /**
     * The quest, in order. Mosol starts it, Trufitus names the temple, and
     * every stage after that is a thing found rather than a thing said.
     */
    private static final int STARTED = 1, TRUFITUS_TOLD = 2, TEMPLE = 3,
        CORPSE_SHOWN = 4, BURIED = 5, SHARD_SHOWN = 6, BERVIRIUS = 7,
        NOTES_SHOWN = 8, MADE_BEADS = 9, ENTRANCE = 10, INSIDE = 11,
        DOORS_OPEN = 12, ZOMBIE_DEAD = 13, SKELETON_DEAD = 14, GHOST_DEAD = 15,
        REMAINS = 16, FINISHED = 17;

    /**
     * Scratch bits above the stage, for the state of the hole in the Bumpy
     * Dirt. Vanilla remembers that the rope is tied even after a logout, so
     * these persist with the stage rather than living in a field.
     */
    private static final int DUG = 32, LIT = 64, ROPED = 128;
    private static final int STAGE_MASK = 31;

    public ShiloVillage(Player owner, Integer uid) {
        super(owner, uid);
    }

    public void define() {
        setUID(UID);
        setName("Shilo village");
        setFinalStage(FINISHED);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("Undead have overrun Shilo village. Dig out the buried temple of Ah Za Rhoon, uncover the legend of Rashiliyia queen of the dead, and lay her spirit to rest so the village can open its gates again.");
        setStartPoint("Outside the Shilo gate");
        setSpeakTo("Mosol Rei");
        requireQuest(Quests.JUNGLE_POTION);
        requireLevel(AGILITY, AGILITY_LEVEL);
        requireLevel(CRAFTING, CRAFT_LEVEL);
        rewardExp(CRAFTING, 125, 125);
        rewardOther("Access to Shilo village");
        rewardOther("Use of the cart ride from Shilo to north of Brimhaven");

        associateNpc(MOSOL);
        associateNpc(TRUFITUS);
        associateNpc(YANNI);
        associateNpc(CART_DRIVER);
        associateNpc(ZADIMUS);
        associateNpc(RASHILIYIA);
        associateNpc(NAZ_ZOMBIE);
        associateNpc(NAZ_SKELETON);
        associateNpc(NAZ_GHOST);

        associateObject(DIRT, DIRT_X, DIRT_Y);
        associateObject(TREE, TREE_A_X, TREE_A_Y);
        associateObject(TREE, TREE_B_X, TREE_B_Y);
        associateObject(HILLSIDE, HILLSIDE_X, HILLSIDE_Y);
        associateObject(STACKED, STACKED_X, STACKED_Y);
        associateObject(BLOCKADE, BLOCKADE_X, BLOCKADE_Y);
        associateObject(CLIFF);
        associateObject(HANDHOLDS);
        associateObject(DOLMEN_B, DOLMEN_B_X, DOLMEN_B_Y);
        associateObject(TABLE, TABLE_X, TABLE_Y);
        associateObject(STONE, STONE_X, STONE_Y);
        associateObject(RUBBLE, RUBBLE_A_X, RUBBLE_A_Y);
        associateObject(RUBBLE, RUBBLE_B_X, RUBBLE_B_Y);
        associateObject(SCROLL_RUBBLE, SCROLL_RUBBLE_X, SCROLL_RUBBLE_Y);
        associateObject(GALLOWS, GALLOWS_X, GALLOWS_Y);
        associateObject(SACKS, SACKS_X, SACKS_Y);
        associateObject(WET_ROCKS);
        associateObject(EXIT_DOORS, EXIT_X, EXIT_Y);
        associateObject(METAL_GATE, GATE_X, GATE_Y);
        associateObject(TOMB_ROCKS);
        associateObject(TOMB_DOORS, TOMB_DOORS_X, TOMB_DOORS_Y);
        associateObject(DOLMEN_R, DOLMEN_R_X, DOLMEN_R_Y);
        associateObject(CART, CART_X, CART_Y);

        // Read, Look, Bury and Activate all arrive as ITEM_COMMAND. The chisel
        // and the bronze wire are claimed only so that the three ITEM_ON_ITEM
        // pairs below can fire: both halves have to be associated, and neither
        // of them carries an inventory command of its own to lose.
        associateItem(PLAQUE);
        associateItem(TATTERED);
        associateItem(CRUMPLED);
        associateItem(NOTES);
        associateItem(ZADIMUS_CORPSE);
        associateItem(REMAINS_ITEM);
        associateItem(CRYSTAL);
        associateItem(BONE_KEY);
        associateItem(SHARD);
        associateItem(POMMEL);
        associateItem(BONE_BEADS);
        associateItem(CHISEL);
        associateItem(WIRE);

        // Dropping the remains lets Rashiliyia go: watched, not owned, so that
        // nothing else about the item changes.
        associateDroppedItem(REMAINS_ITEM);
    }

    public void completeQuest() {
        grantRewards();
        Player p = getOwner();
        p.getActionSender().sendMessage("Well Done!");
        p.getActionSender().sendMessage("Well done.You have completed the Shilo Village Quest");
        p.getActionSender().sendMessage("You gain some experience in crafting.");
    }

    // ------------------------------------------------------------- helpers --

    private int stage() {
        return getStage() & STAGE_MASK;
    }

    private boolean at(int s) {
        return stage() == s;
    }

    private boolean past(int s) {
        return questStarted() && stage() >= s;
    }

    /** Move to a stage, carrying the state of the hole along. */
    private void step(int s) {
        // An unstarted quest reads stage -1, which carries every hole bit at
        // once -- the same landmine mark() already guards against.
        setStage(s | ((questStarted() ? getStage() : 0) & (DUG | LIT | ROPED)));
    }

    /** Set one of the scratch bits without disturbing the stage. */
    private void mark(int bit) {
        setStage((questStarted() ? getStage() : 0) | bit);
    }

    private boolean marked(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void say(String line) {
        getOwner().getActionSender().sendMessage(line);
    }

    private boolean holds(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private boolean wearing(int id) {
        return getOwner().getInventory().wielding(id);
    }

    private void give(int id) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, 1));
        p.getActionSender().sendInventory();
    }

    private void take(int id) {
        Player p = getOwner();
        p.getInventory().remove(id, 1);
        p.getActionSender().sendInventory();
    }

    private void hurt(int damage) {
        Player p = getOwner();
        p.setLastDamage(damage);
        p.setHits(p.getHits() - damage);
        for (Player viewer : p.getViewArea().getPlayersInView()) {
            viewer.informOfModifiedHits(p);
        }
        p.getActionSender().sendStat(HITS);
        if (p.getHits() <= 0) {
            p.killedBy(null, false);
        }
    }

    private void reward(int skill, int exp) {
        if (exp <= 0) {
            return;
        }
        Player p = getOwner();
        p.incExp(skill, exp, false);
        p.getActionSender().sendStat(skill);
    }

    private boolean near(int x, int y, int radius) {
        Player p = getOwner();
        return Math.abs(p.getX() - x) <= radius && Math.abs(p.getY() - y) <= radius;
    }

    /**
     * The Agility check Jagex put on every obstacle in this quest. Kept as it
     * was written even though nothing can pass it yet: Agility is task 37, and
     * quietly lowering the bar would make the quest finishable in a way vanilla
     * never was.
     */
    private boolean agile() {
        if (getOwner().getMaxStat(AGILITY) >= AGILITY_LEVEL) {
            return true;
        }
        say("You need an agility level of " + AGILITY_LEVEL + " to do this");
        return false;
    }

    private boolean canCraft() {
        if (getOwner().getMaxStat(CRAFTING) >= CRAFT_LEVEL) {
            return true;
        }
        say("You need a crafting level of " + CRAFT_LEVEL + " to do this");
        return false;
    }

    /** Half the obstacles here fail on a roll rather than on a level. */
    private boolean lucky() {
        return Math.random() < 0.6D;
    }

    private boolean jungleDone() {
        return getOwner().getQuestManager().completed(Quests.JUNGLE_POTION);
    }

    /**
     * Put the player on the far side of something along the x axis. The
     * obstacles between Shilo and Cairn Isle are climbed in both directions
     * and nothing records which way round the player is, so the position
     * decides it.
     */
    private void cross(int x, int y) {
        Player p = getOwner();
        p.teleport(p.getX() <= x - 1 ? x + 1 : x - 1, y, false);
    }

    /**
     * Choking, the way vanilla punishes going into Rashiliyia's tomb without
     * her son's beads round your neck.
     */
    private boolean choked() {
        if (wearing(BEADS)) {
            return false;
        }
        say("@red@You feel invisible hands starting to choke you...");
        hurt(4 + (int) (Math.random() * 5));
        return true;
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof GameObject) {
            scenery(trigger, (GameObject) entity, used);
            return;
        }
        if (entity instanceof InvItem) {
            InvItem item = (InvItem) entity;
            if (trigger == QuestTrigger.ITEM_COMMAND) {
                command(item.getID());
            } else if (trigger == QuestTrigger.ITEM_ON_ITEM && used != null) {
                pair(item.getID(), used.getID());
            } else if (trigger == QuestTrigger.ITEM_DROP) {
                dropped(item.getID());
            }
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.NPC_KILLED) {
            bossKilled(npc.getID());
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_NPC && used != null) {
            if (npc.getID() == TRUFITUS) {
                showTrufitus(npc, used.getID());
            } else if (npc.getID() == YANNI) {
                sellYanni(npc, used.getID());
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case MOSOL: mosol(npc); break;
            case TRUFITUS: trufitus(npc); break;
            case YANNI: yanni(npc); break;
            case CART_DRIVER: cartDriver(npc); break;
            default: break;
        }
    }

    private void scenery(QuestTrigger trigger, GameObject object, InvItem used) {
        switch (object.getID()) {
            case DIRT: bumpyDirt(trigger, used); return;
            case TREE: searchTree(trigger); return;
            case HILLSIDE: hillside(trigger, used); return;
            case STACKED: stackedRocks(trigger); return;
            case CLIFF: cliffRocks(trigger, object); return;
            case BLOCKADE: blockade(trigger); return;
            case HANDHOLDS: handholds(trigger); return;
            case DOLMEN_B: bervDolmen(trigger, used); return;
            case TABLE: smashedTable(trigger); return;
            case STONE: azrStone(trigger, used); return;
            case RUBBLE: azrRubble(trigger, object); return;
            case SCROLL_RUBBLE: scrollRubble(trigger); return;
            case GALLOWS: gallows(trigger); return;
            case SACKS: azrSacks(trigger); return;
            case WET_ROCKS: wetRocks(trigger); return;
            case EXIT_DOORS: exitDoors(trigger, used); return;
            case METAL_GATE: metalGate(trigger); return;
            case TOMB_ROCKS: tombRocks(trigger); return;
            case TOMB_DOORS: tombDoors(trigger, used); return;
            case DOLMEN_R: rashDolmen(trigger, used); return;
            case CART: farmCart(trigger); return;
            default: return;
        }
    }

    // --------------------------------------------------------------- Mosol --

    private void mosol(final Npc npc) {
        if (completed()) {
            mosolAfter(npc);
            return;
        }
        if (questStarted()) {
            mosolDuring(npc);
            return;
        }
        Conversation c = new Conversation(getOwner(), npc);
        c.message("Mosol seems to be looking around very cautiously.");
        c.message("He jumps a little when you approach and talk to him.");
        c.npc("Run! Run for your life!");
        c.npc("Save yourself!");
        c.npc("I'll keep them back as long as I can...");
        c.options(new Choice("Why do I need to run?", "Who are you?",
            "Yeah..Ok, I'm running!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: whyRun(c); break;
                    case 1: whoAreYou(c); break;
                    default:
                        c.npc("God speed to you my friend!");
                        break;
                }
            }
        });
        c.start();
    }

    private void whyRun(Conversation c) {
        c.npc("Your very life is in danger!");
        c.npc("Rashiliyia has returned and we are all doomed!");
        c.options(new Choice("Rashiliyia? Who is she?",
            "What danger is there around here?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Can you not see Bwana?");
                    c.npc("This whole area is infested with the Living dead.");
                    return;
                }
                c.npc("Rashiliyia? She is the Queen of the dead!");
                c.npc("She has returned and has bought a plague of undead with her.");
                c.npc("They now occupy our village and we have them trapped.");
                c.npc("We warn people like yourself to stay away!");
                c.options(new Choice("What can we do?",
                    "Uh, sounds nasty, just the kind of thing I want to avoid!") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.message("Mosol casts a disaproving glance at you");
                            c.npc("Quite right, bwana, please make all haste!");
                            c.npc("Before your spine turns to water as we speak.");
                            return;
                        }
                        theWitchDoctor(c);
                    }
                }.says(1, "Uh, it sounds nasty, just the kind of thing I want to avoid!"));
            }
        });
    }

    private void whoAreYou(Conversation c) {
        c.npc("I am Mosol Rei, a jungle warrior.");
        c.npc("I used to live in this village.");
        c.npc("But it is too dangerous for you to stay around here!");
        c.options(new Choice("Mosel Rei, that's a nice name.",
            "What danger is there around here?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.message("Mosol looks at you and shakes his head in bewilderment.");
                    c.npc("Thanks! But you really should leave!");
                    return;
                }
                c.npc("Can you not see Bwana?");
                c.npc("This whole area is infested with the Living dead.");
            }
        }.says(0, "Mosol Rei, that's a nice name."));
    }

    /**
     * The one branch that starts the quest. Jungle potion is the requirement,
     * and it is Trufitus who cannot help without it, so the warning is given
     * either way and only the errand is withheld.
     */
    private void theWitchDoctor(Conversation c) {
        c.npc("We are doing all that we can just to keep the undead at bay!");
        c.npc("The village is covered in a deadly green mist.");
        c.npc("If you go into the village, a terrible sickness will befall you.");
        c.npc("And the undead creatures are even stonger beyond the gates.");
        c.npc("My guess is that it has something to do with the legend of Rashiliyia.");
        c.npc("But you would need to speak to the Witch Doctor in the Tai Bwo Wannai village.");
        c.npc("To get more details about that.");
        c.npc("I really have to go now and fight these undead!");
        c.npc("Before they take over the world!");
        c.then(new Effect() {
            public void run(Conversation c) {
                if (!questStarted()) {
                    if (jungleDone()) {
                        step(STARTED);
                    } else {
                        say("You would have to earn the Witch Doctor's trust before he would speak of this.");
                    }
                }
            }
        });
    }

    private void mosolDuring(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Oh are you still here?");
        c.npc("The undead seem to be getting stronger!");
        c.options(new Choice("Why are the undead here?", "What can we do?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    theWitchDoctor(c);
                    return;
                }
                c.npc("Rashiliyia! The Queen of the dead has risen!");
                c.npc("She is the mother of the undead creatures that roam this land.");
                c.npc("But I know nothing of the legend that surounds her");
                c.options(new Choice("Legend you say?",
                    "I don't think this is something I can help with at the moment!") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("Ok, I understand, you may as well be on your way then.");
                            return;
                        }
                        c.npc("Yes. I said it was a legend that I know nothing about.");
                        c.options(new Choice("Oh come on, you must know something!",
                            "Maybe you know someone who does know something?",
                            "Oh, Ok, sorry for bothering you") {
                            public void picked(int option, Conversation c) {
                                if (option == 0) {
                                    c.message("Mosol lowers his brows in deep concentration");
                                    c.npc("Well, let me have a think?");
                                    c.message("He scratches his head.");
                                    c.npc("Hmmm, there was something I think that might help...");
                                    c.npc("No, sorry, it's gone.");
                                    return;
                                }
                                if (option == 2) {
                                    c.npc("Ok, perhaps you'd like to be on your way now?");
                                    return;
                                }
                                c.npc("My guess is that this has something to do with the legend of Rashiliyia.");
                                c.npc("But you need to speak to the Witch Doctor in 'Tai Bwo Wannai' village.");
                                c.npc("To get more details about that.");
                                c.npc("I really have to go now and fight these undead");
                                c.npc("Before they take over the world!");
                            }
                        });
                    }
                });
            }
        });
        c.start();
    }

    private void mosolAfter(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Hello Effendi,");
        c.npc("We have removed the threat of Rashiliyia and even though");
        c.npc("there are still some random outbreaks of undead activity,");
        c.npc("we are more than able to deal with it.");
        c.npc("You can now enter Shilo village.");
        c.npc("Please follow me...");
        c.options(new Choice("Yes, OK, I'll go into the village!",
            "I think I'll see it some other time.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("You decide to stay where you are.");
                    return;
                }
                c.message("Mosol leads you into the village.");
                c.npc("Have a nice time!");
                c.message("Mosol leaves you by the gate and walks back out into the jungle.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        c.getPlayer().teleport(VILLAGE_X, VILLAGE_Y, false);
                    }
                });
            }
        });
        c.start();
    }

    // ------------------------------------------------------------ Trufitus --

    private void trufitus(final Npc npc) {
        Player p = getOwner();
        if (completed()) {
            Conversation c = new Conversation(p, npc);
            c.player("Greetings");
            c.npc("Hello Bwana.");
            c.npc("I conclude that you have been succesful.");
            c.npc("Mosol sent word that the village is clearing of Zombies.");
            c.npc("You have done us all a great dead!");
            c.npc("Why not go and visit him and have a look around Shilo");
            c.npc("village. You may find some interesting things there!");
            c.start();
            return;
        }
        if (!questStarted()) {
            // Jungle potion owns Trufitus before this quest does; it is a
            // separate Quest class and has already claimed his npc id, so both
            // are dispatched and this one has nothing to say yet.
            return;
        }
        if (at(STARTED)) {
            trufitusOpening(npc);
            return;
        }
        if (at(BURIED)) {
            trufitusBuried(npc);
            return;
        }
        if (past(BERVIRIUS) && !past(MADE_BEADS)) {
            trufitusTomb(npc);
            return;
        }
        if (past(MADE_BEADS) && !past(REMAINS)) {
            trufitusLocating(npc);
            return;
        }
        if (past(REMAINS)) {
            trufitusRemains(npc);
            return;
        }
        trufitusHelp(npc);
    }

    /**
     * The conversation that turns Mosol's rumour into a place to dig.
     *
     * This is the largest branching tree Jagex wrote for any single npc, and
     * ours used to be one path through it: two questions, then the legend,
     * then two questions about the temple, then the quest stage. Everything
     * else - the evacuation, the minions, the queen's resting place, whether
     * Mosol is even to be trusted - was simply gone, and the lines that did
     * survive had been welded together out of order (Mosol's four lines came
     * out as one speech, and "If only there was a way to defeat her!" was
     * lifted out of the evacuation branch into the "nothing we can do" one).
     *
     * Rebuilt whole from Transcript:Trufitus, "During Shilo Village quest".
     * Every label below is spoken by the player verbatim, so no says() is
     * needed anywhere in this tree. Several options loop back to a node that
     * has already been visited - those are plain recursive calls, which is
     * safe because a picked() body only queues steps, it does not play them.
     */
    private void trufitusOpening(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("Greetings.");
        c.npc("Greetings Bwana!");
        c.npc("You look like you have some serious news...");
        c.player("Well, I think I may have.");
        c.player("I have just spoken to Mosol Rei and he says that");
        c.player("Rashiliyia has returned...");
        c.npc("Oh dear, it is more serious than I imagined.");
        c.options(new Choice("How are you anyway my friend?",
            "What do you know about Rashiliyia?",
            "What do you know about Mosol Rei?") {
            public void picked(int option, Conversation c) {
                if (option == 1) { aboutRashiliyia(c); return; }
                if (option == 2) { aboutMosol(c); return; }
                // Pleasantries first, then the same two questions again. The
                // small talk is the only thing this branch adds.
                c.npc("I'm very well thanks.");
                c.options(new Choice("What do you know about Rashiliyia?",
                    "What do you know about Mosol Rei?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { aboutRashiliyia(c); return; }
                        aboutMosol(c);
                    }
                });
            }
        });
        c.start();
    }

    private void aboutRashiliyia(Conversation c) {
        c.npc("Hmmm, it's been a long time since I heard that name.");
        c.npc("She is the Queen of the Undead.");
        c.npc("and a more fearsome enemy you will be unlikely to find.");
        c.npc("I fear that you bring me news that she has returned to plague us once again?");
        c.npc("Alas I know of no weakness that she has.");
        c.options(new Choice("So there is nothing we can do?",
            "Should I start to evacuate the island?",
            "Mosol Rei said something about a legend?") {
            public void picked(int option, Conversation c) {
                if (option == 1) { evacuate(c); return; }
                if (option == 2) { theLegend(c); return; }
                c.npc("Not that I can think of");
                c.options(new Choice("Oh, ok!", "Should I start to evacuate the island?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { ohOk(c); return; }
                        evacuate(c);
                    }
                });
            }
        });
    }

    private void aboutMosol(Conversation c) {
        c.npc("I know he is a brave warrior, he lives in a village south of here.");
        c.npc("Your journeys have taken you far!");
        c.options(new Choice("What do you know about Rashiliyia?", "Do you trust him?") {
            public void picked(int option, Conversation c) {
                if (option == 0) { aboutRashiliyia(c); return; }
                c.npc("He is a little headstrong, but for the right reasons.");
                c.npc("I think he is generally to be trusted.");
                c.options(new Choice("What do you know about Rashiliyia?",
                    "Mosol Rei said something about a legend?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { aboutRashiliyia(c); return; }
                        theLegend(c);
                    }
                });
            }
        });
    }

    private void evacuate(Conversation c) {
        c.npc("Yes, that may be a good idea");
        c.npc("Many people could die!");
        c.npc("If only there was a way to defeat her!");
        c.options(new Choice("Mosol Rei said something about a legend?",
            "Will you pack your things now?") {
            public void picked(int option, Conversation c) {
                if (option == 0) { theLegend(c); return; }
                c.npc("I will wait and see what will happen.");
                c.npc("Maybe she does not have the power to strike too far from her resting place?");
                c.npc("But there are many things that I need to do now");
                c.options(new Choice("Is her resting place important?", "Oh, ok!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { restingPlace(c); return; }
                        ohOk(c);
                    }
                });
            }
        });
    }

    /** The polite way out of the evacuation talk. */
    private void ohOk(Conversation c) {
        c.npc("Yes, it's a bit sad really, I liked that village.");
        c.message("Trufitus seems deeply touched...");
        c.npc("Well, I hope you will excuse me, but I need to get back to my studies.");
    }

    /**
     * Why the bones matter - which is the whole reason the quest ends where
     * it does. Reachable again from "Does she have any weaknesses?" below.
     */
    private void restingPlace(Conversation c) {
        c.npc("Only a few people ever reported seeing a ghost like wraith");
        c.npc("It only ever appeared in the place where her bones were laid to rest");
        c.npc("Of course, she only has to get one of her minions to move the bones");
        c.npc("And she has a new land to unleash her undead plague.");
        c.options(new Choice("What are minions?", "What are onions?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Minions are the fiendish undead creatures that she controls.");
                    c.npc("She has very few living worshippers, but they need to be dealt with at some point");
                    c.npc("Usually a strong creature of some sort will be guarding her remains");
                    c.npc("And of course, she is a very powerful spell caster herself");
                    c.npc("Not to be tackled lightly");
                } else {
                    // Jagex wrote the mishearing out in full, and gives him a
                    // slightly different answer afterwards - "the bones"
                    // rather than "her remains", and no line about her being a
                    // spell caster. Both versions are kept as recorded.
                    c.message("Trufitus looks at you blankly");
                    c.npc("Surely you mean Minions?");
                    c.player("Yes of course, I mean Minions, what made you think I said Onions?");
                    c.message("Trufitus frowns at you but continues about...minions...");
                    c.npc("Minions are the fiendish undead creatures that Rashiliyia controls.");
                    c.npc("She has very few living worshippers, but they need to be dealt with at some point");
                    c.npc("Usually a strong creature of some sort will be guarding the bones");
                    c.npc("And it is not to be tackled lightly");
                }
                c.options(new Choice("Thanks for the information!",
                    "Does she have any weaknesses?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { thanksForInfo(c); return; }
                        c.npc("I am not sure, but the legend about her certainly is long");
                        c.npc("It's a pity that the temple of Ah Za Rhoon has crumbled");
                        c.npc("as there my be some clues that could help us to defeat her.");
                        c.npc("Usually, the largest problem is locating her resting place.");
                        c.options(new Choice("Why was it called Ah Za Rhoon?",
                            "Is her resting place important?") {
                            public void picked(int option, Conversation c) {
                                if (option == 0) { whyAhZaRhoon(c); return; }
                                restingPlace(c);
                            }
                        });
                    }
                });
            }
        });
    }

    /** He does not remember telling you anything. Reachable from two places. */
    private void thanksForInfo(Conversation c) {
        c.npc("What information?");
        c.message("Trufitus looks at you blankly, then wanders off.");
        c.npc("Hmmm, well, you are welcome bwana.");
    }

    /**
     * The legend, which is what actually moves the quest on. The hint fires
     * here rather than in one of the temple sub-answers, because those now
     * loop back into each other and would have fired it more than once.
     */
    private void theLegend(Conversation c) {
        c.npc("Ah, yes, there is a legend, but it is lost in the midst of antiquity...");
        c.npc("The last place to hold any details regarding this mystery");
        c.npc("was in the temple of Ah-Za_Rhoon");
        c.npc("And that has long since vanished, it crumbled into dust.");
        c.then(new Effect() {
            public void run(Conversation c) {
                if (at(STARTED)) {
                    step(TRUFITUS_TOLD);
                    say("@gre@You could start searching the jungle for the buried temple.");
                }
            }
        });
        c.options(new Choice("Why was it called Ah Za Rhoon?",
            "Do you know anything more about the temple?") {
            public void picked(int option, Conversation c) {
                if (option == 0) { whyAhZaRhoon(c); return; }
                moreAboutTemple(c);
            }
        });
    }

    private void whyAhZaRhoon(Conversation c) {
        c.npc("It is from an ancient language.");
        c.npc("The direct translation is...");
        c.npc("'Magnificence floating on water'");
        c.npc("But my research makes me believe that the temple was built on land");
        c.npc("And most likely between large bodies of water, for example large lakes.");
        c.npc("However, many people have searched for the temple, and have failed.");
        c.npc("I would hate to see you waste your time on a pointless search like that.");
        c.options(new Choice("Thanks for the information!",
            "Do you know anything more about the temple?") {
            public void picked(int option, Conversation c) {
                if (option == 0) { thanksForInfo(c); return; }
                moreAboutTemple(c);
            }
        });
    }

    private void moreAboutTemple(Conversation c) {
        c.npc("Not much");
        c.npc("I would say that is about it...");
        c.npc("Even the great priest Zadimus who built the temple did not survive.");
        c.npc("Some say that Rashiliyia caused the temple to colapse.");
        c.npc("She was angry at Zadimus for not returning her affections.");
        c.npc("She was a great sorceress even before they met.");
        c.options(new Choice("Tell me more", "Are there any traps there?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("I don't know anymore.");
                    c.npc("You're very demanding aren't you!");
                    return;
                }
                c.npc("How am I supposed to know?");
                c.npc("Alot of what I know is most probably wrong");
                c.npc("But some of it seems right to me.");
                c.npc("Excuse me but I must get back to my studies.");
            }
        });
    }

    /**
     * The "I need help with X" menu, which he keeps for the whole quest.
     *
     * Jagex wrote this as a menu that RE-OPENS after every answer, minus the
     * topic you just asked about, until you pick "Ok, thanks!". Ours answered
     * once and hung up, so four of the five answers were unreachable in any
     * one conversation and "Ok, thanks!" - the only line that closes it -
     * existed nowhere at all.
     *
     * It is also two menus, not one. Before Zadimus' corpse has been shown he
     * only knows about the temple; afterwards the full five topics open up.
     * The old single menu had blended the two, and had taken its Zadimus
     * answer from a THIRD tree entirely - the one he uses after Bervirius'
     * tomb is found, which is rebuilt separately in trufitusTomb().
     *
     * Rebuilt from Transcript:Trufitus, sections "After exploring Ah Za Rhoon"
     * and "After showing him Zadimus Corpse".
     */
    private void trufitusHelp(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("Greetings...");
        c.npc("Greetings Bwana, you have been away!");
        c.npc("The situation with Rashiliyia is worsening!");
        c.npc("I pray that you have some good news for me.");
        if (!past(TEMPLE)) {
            // He has nothing to say about Zadimus, Bervirius or the queen's
            // resting place until you have shown him the corpse. The player's
            // "I think I found the temple" line is held back until the temple
            // really has been found - the transcript only records this menu
            // being reached that way.
            if (past(TRUFITUS_TOLD)) {
                c.player("I think I found the temple of Ah Za Rhoon.");
            }
            c.options(new Choice("I have some items that I need help with.",
                "I need some help with the Temple of Ah Za Rhoon.") {
                public void picked(int option, Conversation c) {
                    if (option == 0) { helpItems(c); return; }
                    c.npc("If you have found the temple, you should search it");
                    c.npc("thoroughly and see if there are any clues about");
                    c.npc("Rashiliyia.");
                    identifyTail(c);
                }
            });
            c.start();
            return;
        }
        c.player("I wonder if you could give me some more help.");
        c.npc("If there is anything else I can help you with?");
        c.npc("Perhaps you should see if you can locate");
        c.npc("Bervirius' tomb.");
        c.options(new Choice("I have some items that I need help with.",
            "I need help with Zadimus.", "I need help with Bervirius.",
            // Jagex misspells the queen's name in these labels, and does so
            // differently from one menu to the next: Rashliyia, Rashilyia,
            // Rashlilia. The player always speaks "Rashliyia", so wherever the
            // label diverges there is a says() to put the spoken line back.
            "I need help with Rashliyia.",
            "I need some help with the Temple of Ah Za Rhoon.") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: helpItems(c); break;
                    case 1: helpZadimus(c); break;
                    case 2: helpBervirius(c); break;
                    case 3: helpRashliyia(c); break;
                    default: helpTemple(c); break;
                }
            }
        });
        c.start();
    }

    /** He asks for the same four things whichever way you get him onto items. */
    private void identifyTail(Conversation c) {
        c.npc("We need to identify that the place you have found");
        c.npc("is indeed Ah Za Rhoon.");
        c.npc("Any scrolls or information about Rashiliyias Kin would be helpful");
        c.npc("Have you got any items concerning Rashiliyia?");
        c.npc("If so, please show me them.");
        c.npc("There must be something relating to Zadimus at the temple");
        c.npc("Did you find anything? If so, let me see it.");
        c.npc("And best of luck!");
    }

    /** The one branch of the help menu that does not re-open it. */
    private void helpItems(Conversation c) {
        c.npc("Well, just let me see the item and I'll help as much as I can.");
        identifyTail(c);
    }

    private void helpZadimus(Conversation c) {
        c.npc("Zadimus is a spirit yearning for freedom.");
        c.npc("Bury him in a sacred place to release his spirit.");
        // Items is not offered here; the sacred ground question takes its slot.
        c.options(new Choice("Is there any sacred ground around here?",
            "I need help with Bervirius.", "I need help with Rashliyia.",
            "I need some help with the Temple of Ah Za Rhoon.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: sacredGround(c); break;
                    case 1: helpBervirius(c); break;
                    case 2: helpRashliyia(c); break;
                    case 3: helpTemple(c); break;
                    default: helpDone(c); break;
                }
            }
        });
    }

    private void helpBervirius(Conversation c) {
        c.npc("Bervirius is the Son of Rashiliyia.");
        c.npc("His tomb may hold some clues as to how");
        c.npc("Rashiliyia may be defeated.");
        c.options(new Choice("I need help with Zadimus.",
            "I have some items that I need help with.",
            "I need help with Rashliyia.",
            "I need some help with the Temple of Ah Za Rhoon.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: helpZadimus(c); break;
                    case 1: helpItems(c); break;
                    case 2: helpRashliyia(c); break;
                    case 3: helpTemple(c); break;
                    default: helpDone(c); break;
                }
            }
        });
    }

    private void helpRashliyia(Conversation c) {
        c.npc("We need to find Rashiliyia's resting place");
        c.npc("and learn how to put her spirit to rest.");
        c.npc("You may find some clues to her resting place");
        c.npc("in Ah Za Rhoon or Bervirius Tomb.");
        c.options(new Choice("I need help with Zadimus.",
            "I have some items that I need help with.",
            "I need help with Bervirius.",
            "I need some help with the Temple of Ah Za Rhoon.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: helpZadimus(c); break;
                    case 1: helpItems(c); break;
                    case 2: helpBervirius(c); break;
                    case 3: helpTemple(c); break;
                    default: helpDone(c); break;
                }
            }
        });
    }

    private void helpTemple(Conversation c) {
        c.npc("If you have found the temple, you should search it");
        c.npc("thoroughly and see if there are any clues about");
        c.npc("Rashiliyia.");
        c.options(new Choice("I need help with Rashlilia.",
            "I need help with Zadimus.",
            "I have some items that I need help with.",
            "I need help with Bervirius.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: helpRashliyia(c); break;
                    case 1: helpZadimus(c); break;
                    case 2: helpItems(c); break;
                    case 3: helpBervirius(c); break;
                    default: helpDone(c); break;
                }
            }
        }.says(0, "I need help with Rashliyia."));
    }

    /** Asked of him twice - once about Zadimus, once holding the corpse. */
    private void sacredGround(Conversation c) {
        c.npc("The ground in the centre of the village is very sacred to us");
        c.npc("Maybe you could try there ?");
    }

    /** The only way out of the help menu. */
    private void helpDone(Conversation c) {
        c.npc("You're quite welcome Bwana.");
    }

    /** His other sign-off, the one the item conversations end on. */
    private void moreThanWelcome(Conversation c) {
        c.npc("You're more than welcome Bwana!");
        c.npc("Good luck for the rest of your quest.");
    }

    /** The two halves of the necklace hint, either order, until "Thanks!". */
    private void pommelHow(Conversation c) {
        c.options(new Choice("How do I make a bronze necklace?",
            "What should I put on the necklace?") {
            public void picked(int option, Conversation c) {
                if (option == 1) { pommelWhatOn(c); return; }
                c.npc("Well, Bwana, I would guess that you would need");
                c.npc("to get some bronze metal and work it into something");
                c.npc("that could be turned into a necklace?");
                c.options(new Choice("What should I put on the necklace?", "Thanks!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { pommelWhatOn(c); return; }
                        moreThanWelcome(c);
                    }
                });
            }
        });
    }

    private void pommelWhatOn(Conversation c) {
        c.npc("Perhaps Zadimus's clue has the answer?");
        c.npc("Now, what was it that he said again?");
        c.npc("Something about kin and keys?");
        c.options(new Choice("How do I make a bronze necklace?", "Thanks!") {
            public void picked(int option, Conversation c) {
                if (option == 1) { moreThanWelcome(c); return; }
                c.npc("Well, Bwana, I would guess that you would need");
                c.npc("to get some bronze metal and work it into something");
                c.npc("that could be turned into a necklace?");
                c.options(new Choice("What should I put on the necklace?", "Thanks!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { pommelWhatOn(c); return; }
                        moreThanWelcome(c);
                    }
                });
            }
        });
    }

    private void remainsWhat(Conversation c) {
        c.options(new Choice("What should I do with them?",
            "Can you take them off my hands?") {
            public void picked(int option, Conversation c) {
                if (option == 1) { remainsRefuse(c); return; }
                c.npc("Hmm, I'm not exactly sure...");
                c.npc("perhaps there is a clue in one");
                c.npc("of the artifacts you have found?");
                c.options(new Choice("Can you take them off my hands?", "Thanks!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { remainsRefuse(c); return; }
                        moreThanWelcome(c);
                    }
                });
            }
        });
    }

    private void remainsRefuse(Conversation c) {
        c.npc("I dare not take them, I may be taken");
        c.npc("over by the evil spirit of Rashiliyia!");
        c.options(new Choice("What should I do with them?", "Thanks!") {
            public void picked(int option, Conversation c) {
                if (option == 1) { moreThanWelcome(c); return; }
                c.npc("Hmm, I'm not exactly sure...");
                c.npc("perhaps there is a clue in one");
                c.npc("of the artifacts you have found?");
                c.options(new Choice("Can you take them off my hands?", "Thanks!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { remainsRefuse(c); return; }
                        moreThanWelcome(c);
                    }
                });
            }
        });
    }

    private void trufitusBuried(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("I have just buried Zadimus's corpse.");
        c.npc("Something seems different about you. You look like");
        c.npc("you have seen a ghost?");
        c.player("It just so happens that I have!");
        c.npc("Oh! So you managed to bury Zadimus's Corpse?");
        c.player("Yes, it was pretty grisly!");
        c.options(new Choice("The spirit said something about keys and kin?",
            "The spirit ramled on-about some nonsense.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Oh, so it most likely was not very important then?");
                    return;
                }
                keysAndKin(c);
            }
        }.says(1, "The spirit rambled on about some nonsense."));
        c.start();
    }

    /**
     * "Why do you think it is significant ?" - and then, when you answer it,
     * "What makes you say that?" on top.
     *
     * The wiki's indentation for the "No reason really." branch is ambiguous:
     * it is drawn as a sibling of the keys-and-kin answers, but the line it
     * gets back - "Well why are you showing it to me then?" - only answers the
     * question about significance, not the question about what the ghost said.
     * It is placed at both levels here, which is the only reading that leaves
     * every recorded line reachable and no menu with a single entry on it.
     */
    private void shardWhy(Conversation c) {
        c.options(new Choice("It appeared when I buried Zadimus's Corpse.",
            "No reason really.") {
            public void picked(int option, Conversation c) {
                if (option == 1) { shardNoReason(c); return; }
                shardGaveYou(c);
            }
        });
    }

    private void shardGaveYou(Conversation c) {
        c.npc("Ah, interesting, so you think that Zadimus gave you the bone?");
        c.npc("What makes you say that?");
        c.options(new Choice("He said something after he gave it to me.",
            "No reason really.") {
            public void picked(int option, Conversation c) {
                if (option == 1) { shardNoReason(c); return; }
                c.npc("What did he say?");
                c.options(new Choice("The spirit said something about keys and kin?",
                    "The spirit ramled on-about some nonsense.",
                    "I'm not sure.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("Oh, so it most likely was not very important then?");
                            return;
                        }
                        if (option == 2) { shardNotSure(c); return; }
                        keysAndKin(c);
                    }
                }.says(1, "The spirit rambled on about some nonsense."));
            }
        });
    }

    private void shardNoReason(Conversation c) {
        c.npc("Well why are you showing it to me then?");
        c.options(new Choice("It appeared when I buried Zadimus's Corpse.",
            "I'm not sure.") {
            public void picked(int option, Conversation c) {
                if (option == 0) { shardGaveYou(c); return; }
                shardNotSure(c);
            }
        });
    }

    private void shardNotSure(Conversation c) {
        c.npc("Oh, right.");
        c.npc("Come back and talk with me if you get an idea.");
    }

    private void keysAndKin(Conversation c) {
        c.npc("Hmmm, maybe it's a clue of some kind?");
        c.npc("Well, Rashiliyias only kin, Bervirius, is entombed");
        c.npc("on a small island which lies to the South West.");
        c.npc("I will do some research into this as well.");
        c.npc("But I think we must take this clue literally");
        c.npc("and get some item that belonged to Bervirius");
        c.npc("as it may be the only way to approach Rashiliyia.");
        c.then(new Effect() {
            public void run(Conversation c) {
                if (at(BURIED)) {
                    step(SHARD_SHOWN);
                }
            }
        });
    }

    /**
     * Once the tomb is found he keeps a second, larger help tree - the one
     * that owns the long answer about Zadimus the high priest of Zamorak, and
     * the "Bervitius" misspelling. Both used to sit in trufitusHelp(), which
     * meant they showed up far too early and this conversation had nothing
     * after its two opening lines: you told him you had found the tomb, he
     * congratulated you, and it ended.
     *
     * Note the third opening branch. Telling him you found nothing and then
     * admitting you were joking is a route Jagex wrote out in full, and it
     * lands in the same help tree as the honest answer.
     *
     * Rebuilt from Transcript:Trufitus, "After finding the Tomb of Bervirius".
     */
    private void trufitusTomb(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("Greetings...");
        c.npc("Greetings Bwana, did you find the tomb of Bervirius?");
        c.options(new Choice("Yes, I found his tomb.", "No, I didn't find a thing.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("That is a shame Bwana, we really do need to act against");
                    c.npc("Rashiliyia soon if we are ever to stand a chance of defeating her.");
                    c.options(new Choice("Actually I did find the tomb, I was just joking.",
                        "I actually need help with something else.",
                        "I didn't find anything in the tomb.") {
                        public void picked(int option, Conversation c) {
                            if (option == 1) { somethingElse(c); return; }
                            if (option == 2) { nothingInTomb(c); return; }
                            c.npc("Well, Bwana, this is no laughing matter.");
                            c.npc("We need to take this very seriously and act now!");
                            c.npc("If you have found any items at the tomb that you need help");
                            c.npc("with please let me see them and I will help as much as I can.");
                            c.options(new Choice("I didn't find anything in the tomb.",
                                "I actually need help with something else.") {
                                public void picked(int option, Conversation c) {
                                    if (option == 0) { nothingInTomb(c); return; }
                                    somethingElse(c);
                                }
                            });
                        }
                    });
                    return;
                }
                c.npc("That is truly great news Bwana!");
                c.npc("You are certainly very resourceful.");
                c.npc("If you have found any items that you need help with");
                c.npc("please let me see them and I will help as much as I can.");
                c.options(new Choice("I actually need help with something else.",
                    "I didn't find anything in the tomb.") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { somethingElse(c); return; }
                        nothingInTomb(c);
                    }
                });
            }
        });
        c.start();
    }

    private void nothingInTomb(Conversation c) {
        c.npc("Maybe you need to look around a little more.");
        c.npc("There must be some small detail at least that can help us");
        c.options(new Choice("I have some items that I need some help with.",
            "I actually need help with something else.") {
            public void picked(int option, Conversation c) {
                if (option == 0) { tombItems(c); return; }
                somethingElse(c);
            }
        }.says(0, "I have some items that I need help with."));
    }

    /** The head of the post-tomb help tree. */
    private void somethingElse(Conversation c) {
        c.npc("What could I possibly help you with Bwana?");
        c.options(new Choice("I need help with Rashliyia.",
            "I need help with Zadimus.",
            "I have some items that I need help with.",
            "I need help with Bervitius.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: tombRashliyia(c); break;
                    case 1: tombZadimus(c); break;
                    case 2: tombItems(c); break;
                    case 3: tombBervirius(c); break;
                    default: helpDone(c); break;
                }
            }
        }.says(3, "I need help with Bervirius."));
    }

    private void tombZadimus(Conversation c) {
        c.npc("All I know is that Zadimus was a high priest of Zamorak,");
        c.npc("Rashiliyia loved him but he did not return her affections.");
        c.npc("When she become a more powerful sorceress, she attacked the");
        c.npc("Ah Za Rhoon temple to Zamorak that Zadimus built and");
        c.npc("reduced it to rubble. What his fate was, I do not know.");
        c.npc("If you find anything relating to him at the temple of");
        c.npc("Ah Za Rhoon, please let me see it.");
        c.options(new Choice("Is there any sacred ground around here?",
            "I need help with Bervirius.", "I need help with Rashilyia.",
            "I need some help with the Temple of Ah Za Rhoon.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: sacredGround(c); break;
                    case 1: tombBervirius(c); break;
                    case 2: tombRashliyia(c); break;
                    case 3: tombTemple(c); break;
                    default: helpDone(c); break;
                }
            }
        }.says(2, "I need help with Rashliyia."));
    }

    private void tombTemple(Conversation c) {
        c.npc("If you have found the temple, you should search it");
        c.npc("thoroughly and see if there are any clues about");
        c.npc("Rashiliyia.");
        c.options(new Choice("I need help with Rashlilia.",
            "I need help with Zadimus.",
            "I have some items that I need help with.",
            "I need help with Bervirius.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: tombRashliyia(c); break;
                    case 1: tombZadimus(c); break;
                    case 2: tombItems(c); break;
                    case 3: tombBervirius(c); break;
                    default: helpDone(c); break;
                }
            }
        }.says(0, "I need help with Rashliyia."));
    }

    private void tombRashliyia(Conversation c) {
        c.npc("We need to find Rashiliyia's resting place");
        c.npc("and learn how to put her spirit to rest.");
        c.npc("You may find some clues to her resting place");
        c.npc("in Ah Za Rhoon or Bervirius Tomb.");
        c.options(new Choice("I need help with Zadimus.",
            "I have some items that I need help with.",
            "I need help with Bervirius.",
            "I need some help with the Temple of Ah Za Rhoon.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: tombZadimus(c); break;
                    case 1: tombItems(c); break;
                    case 2: tombBervirius(c); break;
                    case 3: tombTemple(c); break;
                    default: helpDone(c); break;
                }
            }
        });
    }

    /**
     * After the tomb he stops reciting the whole "we need to identify the
     * place you have found" speech and just asks to see the item.
     */
    private void tombItems(Conversation c) {
        c.npc("Well, just let me see the item and I'll help as much as I can.");
        c.options(new Choice("I need help with Zadimus.",
            "I need help with Bervitius.", "I need help with Rashilyia.",
            "I need some help with the Temple of Ah Za Rhoon.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: tombZadimus(c); break;
                    case 1: tombBervirius(c); break;
                    case 2: tombRashliyia(c); break;
                    case 3: tombTemple(c); break;
                    default: helpDone(c); break;
                }
            }
        }.says(1, "I need help with Bervirius.")
         .says(2, "I need help with Rashliyia."));
    }

    private void tombBervirius(Conversation c) {
        c.npc("Bervirius is the Son of Rashiliyia.");
        c.npc("His tomb may hold some clues as to how");
        c.npc("Rashiliyia may be defeated.");
        c.options(new Choice("I need help with Zadimus.",
            "I have some items that I need help with.",
            "I need help with Rashilyia.",
            "I need some help with the Temple of Ah Za Rhoon.", "Ok, thanks!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: tombZadimus(c); break;
                    case 1: tombItems(c); break;
                    case 2: tombRashliyia(c); break;
                    case 3: tombTemple(c); break;
                    default: helpDone(c); break;
                }
            }
        }.says(2, "I need help with Rashliyia."));
    }

    private void trufitusLocating(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("You may want to start looking for Rashiliyia's Tomb.");
        c.npc("Do you need extra help with locating it?");
        c.options(new Choice("Yes please.", "No thanks, I've got a good idea where it is.",
            "I get choked when I go into Rashiliyias Tomb.") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:
                        c.npc("You may like to start checking North of Ah Za Rhoon.");
                        c.npc("There must be some clue as to what to look for when locating");
                        c.npc("the tomb. Was there anything else at the tomb of Bervirius?");
                        break;
                    case 1:
                        c.npc("Well, that is very good Bwana,");
                        c.npc("perhaps you should locate it already?");
                        break;
                    default:
                        c.npc("Maybe you have missed something, a special clue?");
                        c.npc("It might be worth searching the temple of Ah Za Rhoon again.");
                        c.npc("Or go back to Bervirius Tomb");
                        c.npc("for a more thorough search.");
                        break;
                }
            }
        });
        c.start();
    }

    private void trufitusRemains(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("Hello");
        c.npc("Greetings again Bwana.");
        c.npc("I hope that you have managed to locate Rashiliyias Tomb.");
        c.npc("Again, if you found any interesting items, please show");
        c.npc("them to me.");
        c.options(new Choice("What should I do now?", "Thanks!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("You're more than welcome Bwana!");
                    c.npc("Good luck for the rest of your quest.");
                    return;
                }
                c.message("Trufitus scratches his head.");
                c.npc("Well Bwana, if you have Rashiliyias remains,");
                c.npc("you need to find a way to put her spirit to rest.");
                c.npc("Perhaps there was a clue with one of the artifacts");
                c.npc("that you have?");
                c.npc("Why not have a look through the artifacts that you have");
                c.npc("found and see if there is something clue that might help?");
                c.npc("If you do not have her remains,");
                c.npc("you will need to find them.");
            }
        });
        c.start();
    }

    /** Everything the player can put in Trufitus' hands. */
    private void showTrufitus(final Npc npc, int id) {
        Conversation c = new Conversation(getOwner(), npc);
        switch (id) {
            case CRUMPLED:
                c.message("You hand the crumpled scroll to Trufitus.");
                c.player("Have a look at this, tell me what you think.");
                c.npc("I am speechless Bwana, this is truly ancient.");
                c.npc("Where did you find it?");
                c.player("In an underground building of some sort.");
                c.npc("You must truly have found the temple of Ah Za Rhoon!");
                c.npc("The scroll gives some interesting details about");
                c.npc("Rashiliyia, some things I didn't know before.");
                c.message("Trufitus gives back the scroll.");
                // The ward is the actual hint towards the beads, and it was
                // being given away unasked. Jagex makes you ask for it.
                c.options(new Choice("Anything that can help?", "Ok, thanks!") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) { helpDone(c); return; }
                        c.npc("Hmmm, well just that part about the wards..");
                        c.message("Trufitus seems to drift off in thought.");
                        c.npc("It may be possible to make a ward like that?");
                        c.npc("But what is the best thing to make it from?");
                        c.npc("Perhaps you'll get some clues from other items?");
                    }
                });
                break;
            case PLAQUE:
                c.message("You hand over the Stone Plaque to Trufitus.");
                c.player("Can you decipher this please?");
                c.npc("This is an ancient artifact!");
                c.message("Trufitus looks at the item in awe.");
                c.npc("I can certainly try!");
                c.npc("Hmm, incredible, it seems very ancient,");
                c.npc("and mentions something about Zadimus and Ah Za Rhoon.");
                c.npc("It says,'Here lies the traitor Zadimus, let his spirit");
                c.npc("be forever tormented'");
                c.message("Trufitus hands the Stone Plaque back");
                c.npc("If you have found anything else that you need help with");
                c.npc("please just let me know.");
                break;
            case TATTERED:
                c.player("What do you make of this?");
                c.npc("Truly amazing Bwana, this scroll must be ancient.");
                c.npc("I am unsure if I get any more meaning from it than you though.");
                c.npc("Perhaps Bervirius' tomb is still accessible?");
                c.message("Trufitus hands the Tattered scroll back to you.");
                break;
            case ZADIMUS_CORPSE:
                c.message("You show Trufitus the corpse.");
                c.player("What do you make of this?");
                c.npc("! GASP !");
                c.npc("That's incredible, where did you find it?");
                c.player("I found the corpse in a decomposing gallows");
                c.player("I get a very strange feeling every time I try to bury the body");
                c.npc("Hmmm, that sounds very strange");
                c.npc("I sense a spirit in torment, you should try to bury the remains.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (at(TEMPLE)) {
                            step(CORPSE_SHOWN);
                        }
                    }
                });
                // Where to bury it is a question you have to ask. The other
                // half of this menu - asking him to take the corpse off you,
                // and his reason for refusing - was missing entirely.
                c.options(new Choice("Is there any sacred ground around here?",
                    "Can you dispose of this for me?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { sacredGround(c); return; }
                        c.message("Trufitus pulls away");
                        c.npc("I dare not touch it. I am a spiritual man and");
                        c.npc("the spirit of this being may possess me and");
                        c.npc("turn me into a minion of Rashiliyia.");
                    }
                });
                break;
            case SHARD:
                c.message("You show Trufitus the Bone Shard.");
                c.player("Could you have a look at this please ?");
                c.message("Trufitus looks at the object for a moment.");
                c.npc("It looks like a simple shard of bone.");
                c.npc("Why do you think it is significant ?");
                // He asks twice why the shard matters, and ours answered both
                // questions for the player and skipped straight to the clue.
                shardWhy(c);
                break;
            case NOTES:
                c.message("You hand the notes over to Trufitus.");
                c.npc("Hmm, these notes are quite extraordinary Bwana.");
                c.npc("They give location details of Rashiliyias tomb,");
                c.npc("and some information on how to use the crystal.");
                c.npc("The information is quite specific, North of Ah Za Rhoon!");
                c.npc("That's a great place to start looking!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (at(BERVIRIUS)) {
                            step(NOTES_SHOWN);
                        }
                    }
                });
                break;
            case CRYSTAL:
                c.message("You show Trufitus the Locating Crystal");
                c.npc("This is incredible Bwana,");
                c.player("It is?");
                c.npc("Absolutely!");
                c.npc("This will help you to locate the entrance to Rashiliyia's tomb.");
                c.npc("Simply activate it when you think you are near, and it should");
                c.npc("glow different colours to show how near you are.");
                break;
            case POMMEL:
                c.message("You show Trufitus the sword pommel.");
                c.npc("It is a very nice item Bwana.");
                c.npc("It may be just what we need to gain access to Rashiliyias tomb.");
                c.npc("While you were away, I did some research");
                c.npc("Rashiliyia would spare the lives of those who wore bronze necklaces.");
                c.npc("This item may have some significance to Bervirius.");
                c.npc("Perhaps you can craft something from it that can help?");
                c.npc("My guess is that you will need some protection to enter her tomb!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (at(BERVIRIUS)) {
                            step(NOTES_SHOWN);
                        }
                    }
                });
                // Half of this was being recited unasked and the other half -
                // what actually goes ON the necklace, which is the whole point
                // of the beads - was not here at all.
                pommelHow(c);
                break;
            case BONE_KEY:
                c.player("Have a look at this!");
                c.npc("This is amazing Bwana,the level of detail is incredible.");
                c.npc("Where did you find it?");
                // Ours answered his question for the player and then gave the
                // reply that belongs to the OTHER option, so the whole "does
                // the key work?" branch had never been reachable.
                c.options(new Choice("I made it from the bone shard that Zadimus gave me.",
                    "Do you know what it opens?") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("You must already know what it opens to have carved it");
                            c.npc("so pefectly.");
                            c.npc("Perhaps in your travels you have come");
                            c.npc("across some unique doors with a unique lock");
                            c.npc("I hope this helps with your quest.");
                            return;
                        }
                        c.npc("How very inventive Bwana.");
                        c.npc("You must have seen the lock to have crafted it so well.");
                        c.npc("Does the key work?");
                        c.options(new Choice("Yes and I explored some sort of cavern.",
                            "I don't know, I haven't tried it yet.") {
                            public void picked(int option, Conversation c) {
                                if (option == 1) {
                                    c.npc("It may be an idea to try it and then scout out the area.");
                                    c.npc("If it relates to Rashiliyia, it might help us to defeat her.");
                                    return;
                                }
                                c.npc("How interesting Bwana, did you find anything?");
                                c.options(new Choice("Not really.",
                                    "Yes, I found lots of things.") {
                                    public void picked(int option, Conversation c) {
                                        if (option == 1) {
                                            c.npc("If you let me see them Bwana,");
                                            c.npc("perhaps I can offer you some extra information.");
                                            return;
                                        }
                                        c.npc("Maybe you should go back and try to find some more things.");
                                        c.npc("Show me any other items that you may have.");
                                        c.npc("We need any clue to locate Rashiliyia's resting place.");
                                    }
                                });
                            }
                        }.says(0, "Yes and I explored inside some sort of cavern."));
                    }
                });
                break;
            case REMAINS_ITEM:
                c.message("You show Trufitus the remains...");
                c.player("Could you have a look at this..");
                c.npc("This is truly incredible bwana...");
                c.npc("so these are the remains of the dread queen Rashiliyia?");
                c.player("Yes, I think so.");
                // Both answers re-open the menu; only "Thanks!" ends it, and
                // "Thanks!" was not offered anywhere.
                remainsWhat(c);
                break;
            default:
                c.npc("I am sorry Bwana, that means nothing to me.");
                break;
        }
        c.start();
    }

    // ------------------------------------------------------------- Yanni ----

    private void yanni(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("Hello there!");
        c.npc("Greetings Bwana!");
        c.npc("My name is Yanni and I buy and sell antiques");
        c.npc("and other interesting items.");
        c.npc("If you have any interesting items that you might");
        c.npc("want to sell me, please let me see them and I'll");
        c.npc("offer you a fair price.");
        c.npc("Would you like me to have a look at your items");
        c.npc("and give you a quote?");
        c.options(new Choice("Yes please!", "Maybe some other time?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Sure thing.");
                    c.npc("Have a nice day Bwana.");
                    return;
                }
                c.npc("Great Bwana!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        quote(c.getNpc());
                    }
                });
            }
        });
        c.start();
    }

    // -------------------------------------------------------- Cart Driver ---

    private void cartDriver(final Npc npc) {
        Player p = getOwner();
        if (!completed()) {
            Conversation c = new Conversation(p, npc);
            c.player("Hello there!");
            c.npc("Sorry Bwana, I only run the cart for those who've");
            c.npc("finished with Shilo Village.");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.player("Hello!");
        c.npc("Hello Bwana!");
        c.npc("I am offering a cart ride to Brimhaven if you're interested!");
        c.npc("It will cost " + CART_FARE + " Gold");
        c.options(new Choice("Yes, that sounds great!", "No thanks.") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("Ok Bwana, let me know if you change your mind.");
                    return;
                }
                Player pl = c.getPlayer();
                if (pl.getInventory().countId(COINS) < CART_FARE) {
                    c.npc("Sorry, but it looks as if you don't have enough money.");
                    c.npc("Come back and see me when you have enough for the ride.");
                    return;
                }
                c.npc("Great!");
                c.npc("Just hop into the cart then and we'll go!");
                c.message("You Hop into the cart and the driver urges the horses on.");
                c.message("You take a taxing journey through the jungle to Brimhaven.");
                c.message("You feel fatigued from the journey, but at least");
                c.message("you didn't have to walk all that distance.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player pl = c.getPlayer();
                        pl.getInventory().remove(COINS, CART_FARE);
                        pl.getActionSender().sendInventory();
                        pl.teleport(BRIMHAVEN_X, BRIMHAVEN_Y, false);
                    }
                });
            }
        }.says(0, "Yes please, I'd like to go to Brimhaven!"));
        c.start();
    }

    /**
     * Yanni looks over what you are carrying and names a price for each thing
     * he wants. He is quoted from directly rather than through sayNpcMessage,
     * which reads the player's current npc: this runs from the tail of a
     * conversation, by which time that may already have been cleared.
     */
    private void quote(Npc npc) {
        if (npc == null) {
            return;
        }
        boolean any = false;
        if (holds(BONE_KEY)) {
            offer(npc, "I'll give you 100 Gold for the Bone Key.");
            any = true;
        }
        if (holds(PLAQUE)) {
            offer(npc, "I'll give you 100 Gold for the Stone-Plaque.");
            any = true;
        }
        if (holds(TATTERED)) {
            offer(npc, "I'll give you 100 Gold for your tattered scroll");
            any = true;
        }
        if (holds(CRUMPLED)) {
            offer(npc, "I'll give you 100 Gold for your crumpled scroll");
            any = true;
        }
        if (holds(NOTES)) {
            offer(npc, "I'll give you 100 Gold for your Bervirius Tomb Notes.");
            any = true;
        }
        if (holds(CRYSTAL)) {
            offer(npc, "WOW! I'll give you 500 Gold for your Locating Crystal!");
            any = true;
        }
        if (holds(BEADS) || wearing(BEADS)) {
            offer(npc, "Great I'll give you 1000 Gold for your Beads of the Dead.");
            any = true;
        }
        if (!any) {
            offer(npc, "Sorry Bwana, you have nothing I am interested in.");
            return;
        }
        offer(npc, "Those are the items I am interested in Bwana.");
        offer(npc, "If you want to sell me those items, simply show them to me.");
    }

    private void offer(Npc npc, String line) {
        getOwner().informOfNpcMessage(new ChatMessage(npc, line, getOwner()));
    }

    private void sellYanni(final Npc npc, int id) {
        int price;
        String what;
        switch (id) {
            case BONE_KEY: price = 100; what = "the Bone Key"; break;
            case PLAQUE: price = 100; what = "the Stone Plaque"; break;
            case TATTERED: price = 100; what = "the Tattered Scroll"; break;
            case CRUMPLED: price = 100; what = "the crumpled Scroll"; break;
            case NOTES: price = 100; what = "the Bervirius Tomb Notes"; break;
            case CRYSTAL: price = 500; what = "the Locating Crystal"; break;
            case BEADS: price = 1000; what = "Beads of the Dead"; break;
            default: return;
        }
        final int paid = price;
        final int item = id;
        final String sold = what;
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Great item, here's " + paid + " Gold for it.");
        c.message("You sell " + sold + ".");
        c.then(new Effect() {
            public void run(Conversation c) {
                take(item);
                Player p = c.getPlayer();
                p.getInventory().add(new InvItem(COINS, paid));
                p.getActionSender().sendInventory();
            }
        });
        c.start();
    }

    // ------------------------------------------------------- the Bumpy Dirt --

    private void bumpyDirt(QuestTrigger trigger, InvItem used) {
        if (completed()) {
            say("The entrance seems to have caved in.");
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            digging(used == null ? -1 : used.getID());
            return;
        }
        say("It looks as if something is buried here.");
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            if (marked(DUG)) {
                fissure();
                return;
            }
            say("It looks quite big, you may need some tools to excavate further.");
        }
    }

    private void digging(int id) {
        if (!past(TRUFITUS_TOLD)) {
            // Nothing marks this dirt as the temple until Trufitus has said
            // the name out loud, so vanilla simply will not let you start.
            say("It looks as if something is buried here.");
            return;
        }
        switch (id) {
            case SPADE:
                if (marked(DUG)) {
                    fissure();
                    return;
                }
                say("You dig a small hole and almost immediately hit granite");
                say("You excavate the hole a bit more and see that there is a small fissure");
                say("that you might just be able to crawl through.");
                mark(DUG);
                fissure();
                return;
            case CANDLE:
                // The guide is explicit that the candle has to be lit first,
                // and that it is spent: "use your candle (after lighting it
                // with your tinderbox) ... The candle will disappear after it
                // is used."
                say("You will need to light the candle first.");
                return;
            case LIT_CANDLE:
                if (!marked(DUG)) {
                    say("It looks as if something is buried here.");
                    return;
                }
                say("You hold the candle to the fissure and see that");
                say("there is quite a large drop after you get through the hole.");
                say("Some rope might help here");
                take(LIT_CANDLE);
                mark(LIT);
                return;
            case ROPE:
                if (!marked(LIT)) {
                    say("You cannot see anywhere to attach the rope.");
                    return;
                }
                if (marked(ROPED)) {
                    say("You see that a rope is attached nearby");
                    return;
                }
                say("You see where to attach the rope very clearly.");
                say("You secure it well.");
                take(ROPE);
                mark(ROPED);
                return;
            default:
                say("Nothing interesting happens.");
                return;
        }
    }

    private void fissure() {
        say("Do you want to try to crawl through the fissure?");
        if (marked(ROPED)) {
            say("You see that a rope is attached nearby");
        }
        Conversation c = new Conversation(getOwner(), null);
        c.options(new Choice("Yes, I'll give it a go!", "No thanks, it looks a bit dark!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("You think better of attempting to squeeze your body into the fissure.");
                    c.player("It looked very dangerous, and dark...");
                    c.player("scarey!");
                    return;
                }
                c.message("You start to contort your body...");
                c.message("With some difficulty you manage to push your body");
                c.message("through the small crack in the rock.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        crawlIn();
                    }
                });
            }
        });
        c.start();
    }

    private void crawlIn() {
        Player p = getOwner();
        if (marked(ROPED)) {
            say("You squeeze through the fissure in the granite");
            say("And once through, you cleverly use the rope to slowly lower");
            say("yourself to the floor.");
            sayMessage("Yay!");
            reward(AGILITY, FISSURE_EXP);
        } else {
            say("As you squeeze out of the hole...");
            say("you realize that there is a huge drop underneath you");
            say("you begin falling...");
            sayMessage("Ahhhhh!");
            hurt(4 + (int) (Math.random() * 6));
        }
        p.teleport(AZR_X, AZR_Y, false);
        if (at(TRUFITUS_TOLD)) {
            step(TEMPLE);
        }
    }

    // ----------------------------------------------------------- Ah Za Rhoon --

    private void smashedTable(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("This table has seen better days");
            return;
        }
        say("You may be able to turn this delapidated table into");
        say("something that could help you to get out of this place.");
        say("What would you like to try and turn this table into?");
        Conversation c = new Conversation(getOwner(), null);
        c.options(new Choice("A ladder", "A crude raft", "A pole vault") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.message("Your experience in crafting tells you that");
                    c.message("there isn't enough wood to complete this task.");
                    return;
                }
                if (option == 2) {
                    c.message("You happily start hacking away at the table");
                    c.message("But realise that you won't have enough woood to properly finish the item off!");
                    c.player("Oops! Not enough wood left to do anything else with the table!");
                    c.message("There isn't enough wood left in this table to make anything!");
                    return;
                }
                c.message("You see that this table already looks very sea worthy");
                c.message("it takes virtually no time at all to help fix it into.");
                c.message("a crude raft.");
                c.message("You place it carefully on the water!");
                c.message("You board the raft!");
                c.message("You push off!");
                c.player("Weeeeeeee!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        c.getPlayer().teleport(AZR_OUT_X, AZR_OUT_Y, false);
                    }
                });
            }
        });
        c.start();
    }

    private void azrStone(QuestTrigger trigger, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != CHISEL) {
                say("Nothing interesting happens.");
                return;
            }
            if (holds(PLAQUE)) {
                say("You already have the plaque from this stone.");
                return;
            }
            say("You carefully chisel the carved face away from the stone.");
            give(PLAQUE);
            reward(CRAFTING, PLAQUE_EXP);
            return;
        }
        say("There are markings carved into this stone.");
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("A chisel might free the carved face from the rest of it.");
        }
    }

    /**
     * The rubble between the two halves of Ah Za Rhoon. Both placements are the
     * same obstacle seen from its two ends, so which one was clicked decides
     * where the player comes out.
     */
    private void azrRubble(QuestTrigger trigger, GameObject object) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("Rocks that have caved in");
            return;
        }
        final boolean fromFirst = object.getY() == RUBBLE_A_Y;
        if (!agile()) {
            return;
        }
        say("You contort your body and prepare to squirm, worm like, into the hole.");
        if (!lucky()) {
            say("You managed to get yourself stuck.");
            say("You have to wrench yourself free to get out.");
            say("You manage to pull yourself out, but hurt yourself in the process.");
            say("Maybe you'll have better luck next time?");
            hurt(2 + (int) (Math.random() * 4));
            return;
        }
        say("You struggle through the narrow crevice in the rocks");
        say("and drop to your feet into a narrow underground corridor");
        reward(AGILITY, RUBBLE_EXP);
        getOwner().teleport(fromFirst ? RUBBLE_OUT_B_X : RUBBLE_OUT_A_X,
            fromFirst ? RUBBLE_OUT_B_Y : RUBBLE_OUT_A_Y, false);
    }

    private void scrollRubble(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("Rocks that have caved in");
            return;
        }
        if (holds(TATTERED)) {
            say("You search the rubble but find nothing more.");
            return;
        }
        say("Amongst the rubble you find a rolled up scroll,");
        say("tattered with age. You place it in your inventory.");
        give(TATTERED);
    }

    private void azrSacks(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("Yep they're sacks");
            return;
        }
        if (holds(CRUMPLED)) {
            say("There is nothing under the sack!");
            return;
        }
        say("You find a tattatered, very ornate scroll.");
        say("Which you place carefully in your inventory.");
        give(CRUMPLED);
    }

    private void gallows(QuestTrigger trigger) {
        boolean got = holds(ZADIMUS_CORPSE) || past(BURIED);
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("You take a look at the Gallows.");
            say("The gallows look pretty eerie.");
            if (got) {
                say("An empty noose swings eerily in the half light of the tomb.");
            } else {
                say("A grisly sight meets your eyes. A human corpse hangs from the noose.");
                say("His hands have been tied behind his back.");
            }
            return;
        }
        say("You search the gallows.");
        if (got) {
            say("The gallows look pretty eerie. You search but find nothing.");
            return;
        }
        say("You find a human corpse hanging in the noose.");
        say("It looks as if the corpse will be removed easily.");
        say("Would you like to remove the corpse from the noose?");
        Conversation c = new Conversation(getOwner(), null);
        c.options(new Choice("Yes, I may find something else on the corpse",
            "I don't think so it might animate and attack me!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("You move away from the corpse quietly and slowly...");
                    c.message("...you have an eerie feeling about this!");
                    c.player("** Gulp! **");
                    return;
                }
                c.message("You gently support the frame of the skeleton and lift the skull through the noose.");
                c.message("You find an old sack and place the skeleton in this.");
                c.message("Maybe Trufitus can give you some tips on what to do with it.");
                c.message("You sense that there is a spirit that needs to be put to rest.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        give(ZADIMUS_CORPSE);
                    }
                });
            }
        });
        c.start();
    }

    private void wetRocks(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("The rocks are wet with the spray of the waterfall.");
            return;
        }
        if (!lucky()) {
            say("Your foot slips on the wet rock and you fall back.");
            hurt(1 + (int) (Math.random() * 3));
            return;
        }
        say("You clamber up the wet rocks beside the falling water");
        say("and haul yourself out into the daylight.");
        reward(AGILITY, WET_ROCKS_EXP);
        getOwner().teleport(AZR_OUT_X, AZR_OUT_Y, false);
    }

    // ---------------------------------------------------- burying Zadimus ----

    private void buryZadimus() {
        if (!near(STATUE_X, STATUE_Y, 2)) {
            say("You feel an uneartly compunction to bury this corpse!");
            say("You hear a ghostly wailing sound coming from the corpse");
            say("and a whispering voice says,");
            say("@yel@'Zadimus: Let me rest in a sacred place and assist you I will'");
            return;
        }
        Player p = getOwner();
        say("You feel an uneartly compunction to bury this corpse!");
        say("You hear an unearthly moaning sound as you see");
        say("an apparition materialises right in front of you.");
        take(ZADIMUS_CORPSE);

        final Npc ghost = new Npc(ZADIMUS, p.getX(), p.getY() + 1,
            p.getX() - 1, p.getX() + 1, p.getY() - 1, p.getY() + 1);
        ghost.setRespawn(false);
        world.registerNpc(ghost);

        Conversation c = new Conversation(p, ghost);
        c.npc("You have released me from my torture, and now I shall aid you");
        c.npc("You seek to dispell the one who tortured and killed me");
        c.npc("Remember this...");
        c.npc("'I am the key, but only kin may approach her.'");
        c.message("The apparition disapears into the ground where you buried the corpse.");
        c.message("You see the ground in front of you shake");
        c.message("as a shard of bone forces its way to the surface.");
        c.message("You take the bone shard and place it in your inventory.");
        c.then(new Effect() {
            public void run(Conversation c) {
                world.unregisterNpc(ghost);
                give(SHARD);
                if (at(CORPSE_SHOWN) || at(TEMPLE)) {
                    step(BURIED);
                }
            }
        });
        c.start();
    }

    // ------------------------------------------------- Cairn Isle and beyond --

    private void cliffRocks(QuestTrigger trigger, GameObject object) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("A rocky outcrop");
            return;
        }
        if (!agile()) {
            return;
        }
        if (!lucky()) {
            say("You slither back down the rocks, grazing yourself.");
            hurt(1 + (int) (Math.random() * 3));
            return;
        }
        say("You scramble up the rocky outcrop.");
        reward(AGILITY, CLIFF_EXP);
        cross(object.getX(), getOwner().getY());
    }

    private void blockade(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("The bridge is blocked by a heap of fallen rock.");
            return;
        }
        if (!agile()) {
            return;
        }
        if (!lucky()) {
            say("You misjudge the jump and land badly.");
            hurt(1 + (int) (Math.random() * 3));
            return;
        }
        say("You leap over the blockade and land on the far side of the bridge.");
        reward(AGILITY, BLOCKADE_EXP);
        cross(BLOCKADE_X, BLOCKADE_Y);
    }

    private void stackedRocks(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("Rocks that have been stacked uniformly.");
            return;
        }
        if (completed()) {
            say("This tomb entrance seems to be completely flooded.");
            say("A great sense of peace pervades in this area.");
            return;
        }
        say("You investigate the rocks and find a dank,narrow crawl-way.");
        say("Do you want to crawl into this dank, dark, narrow,");
        say("possibly dangerous hole?");
        Conversation c = new Conversation(getOwner(), null);
        c.options(new Choice("Yes please, I can think of nothing nicer !",
            "No way could you get me to go in there !") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("You decide that the surface is the place for you!");
                    return;
                }
                c.message("You contort your body and prepare to squirm, worm like, into the hole.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        intoBervirius();
                    }
                });
            }
        });
        c.start();
    }

    private void intoBervirius() {
        if (!agile()) {
            return;
        }
        if (!lucky()) {
            say("You managed to get yourself stuck.");
            say("You have to wrench yourself free to get out.");
            say("You manage to pull yourself out, but hurt yourself in the process.");
            say("Maybe you'll have better luck next time?");
            hurt(2 + (int) (Math.random() * 4));
            return;
        }
        say("You struggle through the narrow crevice in the rocks");
        say("and drop to your feet into a narrow underground corridor");
        reward(AGILITY, STACKED_EXP);
        getOwner().teleport(BERV_X, BERV_Y, false);
    }

    private void handholds(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("I wonder if I can climb up these");
            return;
        }
        if (!agile()) {
            return;
        }
        if (!lucky()) {
            say("You lose your grip and slide back down.");
            hurt(1 + (int) (Math.random() * 3));
            return;
        }
        say("You climb up the wall");
        say("And climb out into the daylight");
        getOwner().teleport(BERV_OUT_X, BERV_OUT_Y, false);
    }

    private void bervDolmen(QuestTrigger trigger, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used != null && used.getID() == REMAINS_ITEM) {
                layToRest();
                return;
            }
            say("Nothing interesting happens.");
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("The Dolmen is intricately decorated with the family");
            say("symbol of two crossed palm trees .");
            say("You can see that there are some items on the Dolmen.");
            return;
        }
        say("The Dolmen is intricately decorated with the symbol of");
        say("two crossed palm trees. It might be the family crest?");
        say("You can see that there are some items on the Dolmen.");
        boolean any = false;
        if (!holds(POMMEL)) {
            say("You find a rusty sword with an ivory pommel.");
            say("You take the pommel and place it into your inventory.");
            give(POMMEL);
            any = true;
        }
        if (!holds(CRYSTAL)) {
            say("You find a Crystal Sphere");
            give(CRYSTAL);
            any = true;
        }
        if (!holds(NOTES)) {
            say("You find some writing on the dolmen,");
            say("you grab some nearby scraps of delicate paper together");
            say("and copy the text as best you can and collect");
            say("them together as a scroll");
            give(NOTES);
            any = true;
        }
        if (!any) {
            say("There is nothing else here for you.");
        }
        if (at(SHARD_SHOWN)) {
            step(BERVIRIUS);
        }
    }

    /** Putting Rashiliyia on her son's dolmen, which is the end of the quest. */
    private void layToRest() {
        if (!at(REMAINS)) {
            return;
        }
        Player p = getOwner();
        say("You carefully place Rashiliyia's remains on the Dolmen.");
        say("You feel a strange vibration in the air.");
        take(REMAINS_ITEM);

        final Npc queen = new Npc(RASHILIYIA, p.getX(), p.getY() + 1,
            p.getX() - 1, p.getX() + 1, p.getY() - 1, p.getY() + 1);
        queen.setRespawn(false);
        world.registerNpc(queen);

        Conversation c = new Conversation(p, queen);
        c.npc("You have my gratitude for releasing my spirit.");
        c.npc("I have suffered a vengeful and evil existence.");
        c.npc("I was tricked by Zamorak. He returned my son to me as an undead Creature.");
        c.npc("My hatred and bitterness corrupted me.");
        c.npc("I tried too destroy all life...now I am released.");
        c.npc("And am grateful to contemplate eternal rest...");
        c.message("Without warning the spirit of Rashiliyia disapears.");
        c.then(new Effect() {
            public void run(Conversation c) {
                world.unregisterNpc(queen);
                // Outright, not step(): the ending has to be exactly FINISHED
                // or completed() never says so, and the scratch bits recording
                // the state of the hole must go with it.
                setStage(FINISHED);
            }
        });
        c.start();
    }

    // ------------------------------------------------- Rashiliyia's tomb ----

    private void searchTree(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("A jungle tree, thick with creepers.");
            return;
        }
        if (past(ENTRANCE)) {
            // The entrance is spawned rather than loaded, so it is gone after a
            // restart. Searching again puts it back rather than stranding
            // anyone who was mid-quest when the server went down.
            revealEntrance();
            say("You have already cleared the creepers from the doors.");
            return;
        }
        if (!past(MADE_BEADS)) {
            say("You search the tree but find nothing of interest.");
            return;
        }
        say("You pull back the creepers growing over the tree");
        say("and find that they hide a pair of great stone doors");
        say("set into the hillside behind.");
        revealEntrance();
        step(ENTRANCE);
    }

    /**
     * Register the Hillside Entrance. It has no placement anywhere -- vanilla's
     * own object dump has none either -- because in vanilla it is not there
     * until it is found, so this quest puts it where the wiki's location map
     * says it stands.
     */
    private void revealEntrance() {
        Point at = Point.location(HILLSIDE_X, HILLSIDE_Y);
        if (world.getTile(HILLSIDE_X, HILLSIDE_Y).hasGameObject()) {
            return;
        }
        world.registerGameObject(new GameObject(at, HILLSIDE, 0, 0));
    }

    private void hillside(QuestTrigger trigger, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != BONE_KEY) {
                say("Nothing interesting happens.");
                return;
            }
            unlockEntrance();
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT1 && holds(BONE_KEY)) {
            unlockEntrance();
            return;
        }
        say("Large doors that seem to lead into the hillside");
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("Set into the doors is a lock the like of which you have never seen.");
            say("It is carved from bone.");
        }
    }

    private void unlockEntrance() {
        say("You try the key with the lock.");
        say("As soon as you push the key into the lock.");
        say("A shimmering light dances over the doors, before you can blink, the doors creak open.");
        say("You feel a strange force pulling you inside.");
        say("The doors close behind you with the sound of crunching bone.");
        say("Before you stretches a winding tunnel blocked by an ancient gate.");
        getOwner().teleport(TOMB_X, TOMB_Y, false);
        if (at(ENTRANCE)) {
            step(INSIDE);
        }
    }

    private void exitDoors(QuestTrigger trigger, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != BONE_KEY) {
                say("Nothing interesting happens.");
                return;
            }
            choked();
            say("You unlock the doors with the key");
            say("The doors creak open revealing bright day light.");
            say("You walk outside into the warmth of the Jungle heat.");
            getOwner().teleport(TOMB_OUT_X, TOMB_OUT_Y, false);
            return;
        }
        if (holds(BONE_KEY)) {
            choked();
            say("You unlock the doors with the key");
            say("The doors creak open revealing bright day light.");
            say("You walk outside into the warmth of the Jungle heat.");
            getOwner().teleport(TOMB_OUT_X, TOMB_OUT_Y, false);
            return;
        }
        say("Perhaps you should give them a push");
        say("The doors are locked from this side. There is a lock carved from bone.");
    }

    private void metalGate(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("This huge metal gate bars the way further...");
            say("There is an intense and unpleasant feeling from this place.");
            say("And you can see why, shadowy flying creatures seem to hover in the still dark air.");
            return;
        }
        say("You push the gates...they're very stiff...");
        say("They won't budge with a normal push.");
        say("Do you want to try to force them open with brute strength?");
        Conversation c = new Conversation(getOwner(), null);
        c.options(new Choice("Yes, I'm very strong, I'll force them open.",
            "No, I'm having second thoughts.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("You decide against forcing the gates.");
                    return;
                }
                c.message("You ripple your muscles...preparing too exert yourself...");
                c.player("Hup!");
                c.message("You brace yourself against the doors...");
                c.player("Urghhhhh!");
                c.message("You start to force against the gate..");
                c.player("Arghhhhhhh!");
                c.message("You push and push,");
                c.player("Shhhhhhhshshehshsh");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        forceGate();
                    }
                });
            }
        });
        c.start();
    }

    private void forceGate() {
        Player p = getOwner();
        if (!lucky()) {
            say("but run out of steam before you're able to force the gates open.");
            say("The effort of trying to force the gates reduces your strength temporarily");
            p.setCurStat(STRENGTH, Math.max(0, p.getCurStat(STRENGTH) - 1));
            p.getActionSender().sendStat(STRENGTH);
            return;
        }
        say("You just manage to force the gates open slightly,");
        say("just enough to force yourself through.");
        p.teleport(PAST_GATE_X, PAST_GATE_Y, false);
    }

    private void tombRocks(QuestTrigger trigger) {
        Player p = getOwner();
        boolean down = p.getY() < TUNNELS_Y;
        if (down && choked()) {
            say("@red@You simply cannot concentrate enough to climb down the rocks.");
            return;
        }
        if (!agile()) {
            return;
        }
        say("You carefully try to pick you way down the rocks.");
        say("You manage to carefully clamber down.");
        reward(AGILITY, TOMB_ROCKS_EXP);
        p.teleport(down ? TUNNELS_X : PAST_GATE_X, down ? TUNNELS_Y : PAST_GATE_Y, false);
    }

    private void tombDoors(QuestTrigger trigger, InvItem used) {
        Player p = getOwner();
        if (past(DOORS_OPEN)) {
            say("The tomb doors stand open.");
            p.teleport(TOMB_DOORS_X, p.getY() >= TOMB_DOORS_Y ? TOMB_DOORS_Y - 1 : TOMB_DOORS_Y, false);
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != BONES) {
                say("Nothing interesting happens.");
                return;
            }
            if (p.getInventory().countId(BONES) < 3) {
                say("You place a bone in one of the hollows in the doors,");
                say("but there are more hollows than you have bones.");
                return;
            }
            // Bones do not stack, and Inventory.remove clears one slot per
            // call whatever amount it is passed, so this has to be done three
            // times rather than as remove(BONES, 3).
            p.getInventory().remove(BONES, 1);
            p.getInventory().remove(BONES, 1);
            p.getInventory().remove(BONES, 1);
            p.getActionSender().sendInventory();
            say("You place three bones into the hollows carved in the doors.");
            say("The doors grind slowly apart.");
            step(DOORS_OPEN);
            p.teleport(TOMB_DOORS_X, TOMB_DOORS_Y - 1, false);
            return;
        }
        say("The doors will not move.");
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("Three hollows have been carved into the stone,");
            say("each one about the size and shape of a bone.");
        }
    }

    private void rashDolmen(QuestTrigger trigger, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            say("Nothing interesting happens.");
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            say("An ancient construct for displaying the bones of the deceased");
            return;
        }
        if (choked()) {
            return;
        }
        if (past(GHOST_DEAD)) {
            if (holds(REMAINS_ITEM) || completed()) {
                say("There is nothing left on the dolmen.");
                return;
            }
            say("You see something appear on the Dolmen");
            say("You take the remains of Rashiliyia.");
            give(REMAINS_ITEM);
            if (at(GHOST_DEAD)) {
                step(REMAINS);
            }
            return;
        }
        if (!past(DOORS_OPEN)) {
            say("The dolmen is bare.");
            return;
        }
        spawnBoss(NAZ_ZOMBIE);
    }

    // ------------------------------------------------------- the Nazastarool --

    private void spawnBoss(final int id) {
        Player p = getOwner();
        final Npc beast = new Npc(id, p.getX(), p.getY() + 1,
            p.getX() - 6, p.getX() + 6, p.getY() - 6, p.getY() + 6);
        beast.setRespawn(false);
        world.registerNpc(beast);
        if (id == NAZ_ZOMBIE) {
            p.informOfNpcMessage(new ChatMessage(beast, "Who dares disturb Rashiliyias' rest?", p));
            p.informOfNpcMessage(new ChatMessage(beast, "I am Nazastarool!", p));
            p.informOfNpcMessage(new ChatMessage(beast, "Prepare to die!", p));
        } else if (id == NAZ_SKELETON) {
            p.informOfNpcMessage(new ChatMessage(beast, "Quake in fear, for I am reborn!", p));
            p.informOfNpcMessage(new ChatMessage(beast, "Your death will be swift.", p));
        } else {
            p.informOfNpcMessage(new ChatMessage(beast, "Nazastarool returns with vengeance!", p));
            p.informOfNpcMessage(new ChatMessage(beast, "Soon you will serve Rashiliyia!", p));
        }
        beast.attackPlayer(p);
        world.getDelayedEventHandler().add(new SingleEvent(null, BOSS_TIMEOUT) {
            public void action() {
                if (beast.getID() == id) {
                    world.unregisterNpc(beast);
                }
            }
        });
    }

    /**
     * Each form falls into the next. NPC_KILLED arrives after the npc has been
     * unregistered, so there is nobody left to talk and the whole scene is
     * messages.
     */
    private void bossKilled(int id) {
        switch (id) {
            case NAZ_ZOMBIE:
                if (!at(DOORS_OPEN)) {
                    return;
                }
                say("You defeat Nazastarool and the corpse falls to");
                say("the ground. The bones start to move again and");
                say("soon they reform into a grisly giant skeleton.");
                choked();
                step(ZOMBIE_DEAD);
                spawnBoss(NAZ_SKELETON);
                return;
            case NAZ_SKELETON:
                if (!at(ZOMBIE_DEAD)) {
                    return;
                }
                say("You defeat the Nazastarool Skeleton as the corpse falls to");
                say("the ground. An ethereal form starts taking shape above the");
                say("bones and you soon face the vengeful ghost of Nazastarool");
                step(SKELETON_DEAD);
                spawnBoss(NAZ_GHOST);
                return;
            case NAZ_GHOST:
                if (!at(SKELETON_DEAD)) {
                    return;
                }
                say("@yel@Nazastarool: May you perish in the fires of Zamoraks furnace!");
                say("@yel@Nazastarool: May Rashiliyias Curse be upon you!");
                say("You see something appear on the Dolmen");
                step(GHOST_DEAD);
                return;
            default:
                return;
        }
    }

    // ---------------------------------------------------------- farm cart ----

    private void farmCart(QuestTrigger trigger) {
        say("You approach the cart and see undead creatures gathering by the village gates.");
        say("There is a note attached to the cart.");
        say("The note says,");
        say("Danger deadly green mist do not enter if you value your life");
        Npc mosol = null;
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            if (n.getID() == MOSOL) {
                mosol = n;
                break;
            }
        }
        if (mosol != null) {
            getOwner().informOfNpcMessage(
                new ChatMessage(mosol, "You must be a maniac to go in there!", getOwner()));
        }
    }

    // -------------------------------------------------------- item commands --

    private void command(int id) {
        switch (id) {
            case PLAQUE: readPlaque(); return;
            case TATTERED: readTattered(); return;
            case CRUMPLED: readCrumpled(); return;
            case NOTES: readNotes(); return;
            case ZADIMUS_CORPSE: buryZadimus(); return;
            case REMAINS_ITEM: say("Nothing interesting happens"); return;
            case CRYSTAL: activate(); return;
            case BONE_KEY: say("The key is intricately carved out of bone."); return;
            case SHARD: say("A slender piece of bone, snapped clean at one end."); return;
            default: return;
        }
    }

    private void readPlaque() {
        say("The markings are very intricate. It's a very strange language.");
        say("The meaning of it evades you though.");
    }

    private void readTattered() {
        say("This looks like part of a scroll about someone called Berverius..");
        say("Would you like to read it?");
        Conversation c = new Conversation(getOwner(), null);
        c.options(new Choice("Yes please.", "No thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("You decide not to open the scroll but instead put it carefully back into your inventory.");
                    return;
                }
                c.message("Bervirius, son of King Danthalas, was killed in battle.");
                c.message("His devout Mother Rashiliyia was so heartbroken that she");
                c.message("swore fealty to Zamorak if he would return her son to her.");
                c.message("Bervirius returned as an undead creature and terrorized the");
                c.message("King and Queen. Many guards died fighting the Undead");
                c.message("Berverious, eventually the undead Bervirius was set on fire and");
                c.message("soon only the bones remained.");
                c.message("His remains were taken far to the South, and then towards the");
                c.message("setting sun to a tomb that is surrounded by and level with the");
                c.message("sea. The only remedy for containing the spirits of witches and");
                c.message("undead.");
            }
        });
        c.start();
    }

    private void readCrumpled() {
        say("This looks like part of a scroll about Rashiliyia");
        say("Would you like to read it?");
        Conversation c = new Conversation(getOwner(), null);
        c.options(new Choice("Yes please!", "No thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("You decide to leave the scroll well alone.");
                    return;
                }
                c.message("Rashiliyia's rage went unchecked.");
                c.message("She killed without mercy for revenge of her sons life.");
                c.message("Like a spectre through the night she entered houses and one by");
                c.message("one quietly strangled life from the occupants.");
                c.message("It is said that only a handful survived, protected by a necklace");
                c.message("wards to keep the Witch Queen at bay.");
            }
        });
        c.start();
    }

    private void readNotes() {
        say("This scroll is a collection of writings..");
        say("Some of them are just scraps of papyrus with what looks like random scribblings.");
        say("Which would you like to read?");
        Conversation c = new Conversation(getOwner(), null);
        c.options(new Choice("Tattered Yellow papyrus", "Decayed White papyrus",
            "Crusty Orange papyrus") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.message("...and rest like your mother who is silent in the peace of her");
                    c.message("tomb far to the North of Ah Za Rhoon. Near the sea, and under");
                    c.message("the hills deep in the underground to watch all of nature from the");
                    c.message("darkness of her final resting place.");
                    return;
                }
                if (option == 1) {
                    c.message("... Rashiliyia did so love objects of beauty. Her tomb was");
                    c.message("adorned with crystals that glowed brightly when near to each");
                    c.message("other.");
                    return;
                }
                c.message("...the sphere is activated when power of a spiritual nature is");
                c.message("expended upon it, this can be very draining on the body...");
            }
        });
        c.start();
    }

    private void activate() {
        say("You feel the crystal trying to draw upon your spiritual energy.");
        say("Do you want to let it.");
        Conversation c = new Conversation(getOwner(), null);
        c.options(new Choice("Yes, that seems fine.", "No, it sounds a bit dangerous.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("You decide not to allow the crystal to draw spiritual energy from your body.");
                    return;
                }
                c.then(new Effect() {
                    public void run(Conversation c) {
                        drawOnCrystal();
                    }
                });
            }
        });
        c.start();
    }

    private void drawOnCrystal() {
        Player p = getOwner();
        if (p.getCurStat(PRAYER) < 10) {
            say("You have no spiritual energy that the crystal can draw from.");
            say("You need to have at least 10 prayer points for it to work.");
            return;
        }
        p.setCurStat(PRAYER, p.getCurStat(PRAYER) - (1 + (int) (Math.random() * 2)));
        p.getActionSender().sendStat(PRAYER);
        int away = Math.max(Math.abs(p.getX() - HILLSIDE_X), Math.abs(p.getY() - HILLSIDE_Y));
        if (away <= 2) {
            say("The crystal blazes brilliantly.");
        } else if (away <= 6) {
            say("The crystal is very bright.");
        } else if (away <= 12) {
            say("The crystal glows brightly");
        } else if (away <= 25) {
            say("The crystal glows feintly");
        } else {
            say("Nothing seems different about the Crystal.");
        }
    }

    /**
     * The three chisel recipes, and the one stringing. Both halves of a pair
     * have to be claimed for this to fire at all, so nothing here can reach an
     * ordinary use of a chisel.
     */
    private void pair(int a, int b) {
        int other = a == CHISEL ? b : (b == CHISEL ? a : -1);
        if (other == POMMEL) {
            carveBeads();
            return;
        }
        if (other == SHARD) {
            carveKey();
            return;
        }
        if ((a == BONE_BEADS && b == WIRE) || (a == WIRE && b == BONE_BEADS)) {
            stringBeads();
            return;
        }
        // Any other pairing of two claimed items lands here, and the built-in
        // handlers are skipped once a quest has taken the pair, so this has to
        // answer for itself.
        say("Nothing interesting happens.");
    }

    private void carveBeads() {
        if (!past(NOTES_SHOWN)) {
            say("You're not quite sure what to make with this.");
            say("Perhaps Trufitus can tell you more about it?");
            return;
        }
        if (!canCraft()) {
            return;
        }
        say("You prepare the ivory pommel and the chisel to start crafting...");
        say("You successfully craft some of the ivory into beads.");
        say("They may look good as part of a necklace.");
        take(POMMEL);
        give(BONE_BEADS);
        reward(CRAFTING, CARVE_EXP);
    }

    private void carveKey() {
        if (!past(ENTRANCE)) {
            say("You're not quite sure what to make with this.");
            say("Perhaps it will come to you as you discover more about Rashiliyia?");
            return;
        }
        if (!canCraft()) {
            return;
        }
        say("You carefully carve the shard of bone into the shape of a key.");
        take(SHARD);
        give(BONE_KEY);
        reward(CRAFTING, CARVE_EXP);
    }

    private void stringBeads() {
        if (!canCraft()) {
            return;
        }
        say("You successfully craft the beads and Bronze Wire");
        say("into a necklace which you name, 'Beads of the dead'");
        take(BONE_BEADS);
        take(WIRE);
        give(BEADS);
        reward(CRAFTING, CARVE_EXP);
        if (at(NOTES_SHOWN)) {
            step(MADE_BEADS);
        }
    }

    /**
     * Dropping the remains lets Rashiliyia go, and the stage falls back to the
     * one before the corpse was taken so that her dolmen will yield it again --
     * vanilla does not make the player fight the Nazastarool a second time.
     */
    private void dropped(int id) {
        if (id != REMAINS_ITEM || !at(REMAINS)) {
            return;
        }
        say("The bones turn to dust and forms into the shape of a human figure.");
        say("The figure turns to you and you hear a cackling, croaky voice on the air.");
        say("@yel@Rashiliyia: Many thanks for releasing me!");
        say("@yel@Rashiliyia: Please excuse me, I must attend to my plans!");
        say("The figure turns and soars away quickly disapearing into the distance.");
        step(GHOST_DEAD);
    }
}
