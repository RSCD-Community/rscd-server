import org.rscdaemon.server.model.ChatMessage;
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
 * Watchtower. Released 7 May 2003, written by Ian Taylor.
 *
 * The tower north-west of Yanille keeps the ogres of the Mendip hills out by
 * magic, and the four crystals that power the spell have been stolen. Chewed
 * grey fingernails in a bush outside say a skavid did it, and skavids serve
 * ogres, so the quest is really about getting into Gu'Tanoth: befriending the
 * three chieftains who hate each other, bribing four sets of guards, learning
 * enough of the skavid language to be answered, and finally poisoning the six
 * shaman who run the city.
 *
 *     Watchtower wizard  npc 672, (636-639, 2625-2627) -- the tower's top floor
 *     Og                 npc 680, (662,738)   -- wants his gold back from Toban
 *     Grew               npc 681, (663,759)   -- wants Gorad's tooth
 *     Toban              npc 682, (606,803)   -- wants dragon bones
 *     Gorad              npc 683, (607,794)   -- level 78, drops the tooth
 *     Ogre guard         npc 675 south-east gate (gold bar), 676 north-west
 *                        gate (ogre relic), 677 battlement (rock cake),
 *                        684 enclave cave (nightshade), 697 rock jump (20gp)
 *     City Guard         npc 691, (634-636, 793-797) -- the riddle
 *     Skavid             npc 708 shy (teaches), 678/707/696/709 the four to
 *                        answer, 679 the mad ones who hold the second crystal
 *     Ogre Shaman        npc 673, six of them in the enclave
 *
 *     Fingernails 1036   Robe 1234    Armour 1235   Dagger 1236  eye patch 1237
 *     Powering crystal1 1037, 2 1152, 3 1153, 4 1154
 *     Stolen gold 1040   Ogre tooth 1043   Ogre relic 1044   Skavid map 1045
 *     Key 1047           Ogre relic part 1048 (Og), 1049 (Grew), 1050 (Toban)
 *     Ground bat bones 1051   Unfinished potion 1052   Ogre potion 1053
 *     Magic ogre potion 1054  Rock cake 1061   nightshade 1086
 *     Shaman robe 1087        Spell scroll 1181
 *
 * Three things this quest needs were missing from the server rather than from
 * the quest, and were added outside this file because they are not quest
 * machinery: a mortar recipe for bat bones (InvUseOnItem.doGrind), the two
 * ogre-potion rows in ItemHerbSecond, and the rope swing to Grew's island --
 * tying the rope is generic (InvUseOnObject, object 662) and swinging is
 * Agility's (objects 663 and 664, level 30 for 12.5).
 *
 * The five skavid caves are pockets of underground map with no route to them in
 * any surviving data, so which surface cave leads to which pocket is a choice
 * made here and not a fact recovered from vanilla. It is written down in the
 * report; the pairing used is the nearest pocket to each entrance, which also
 * happens to give each of the five caves exactly one skavid to answer.
 */
public class Watchtower extends Quest {
    public Watchtower(Player owner, Integer uid) {
        super(owner, uid);
    }

    private static final int UID = Quests.WATCHTOWER;

    // --------------------------------------------------------------- npcs --

    private static final int WIZARD = 672, SHAMAN = 673;
    private static final int GUARD_GOLD = 675, GUARD_RELIC = 676;
    private static final int GUARD_CAKE = 677, GUARD_CAVE = 684, GUARD_JUMP = 697;
    private static final int CITY_GUARD = 691;
    private static final int OG = 680, GREW = 681, TOBAN = 682, GORAD = 683;
    private static final int SKAVID_SHY = 708;
    private static final int SKAVID_A = 678, SKAVID_B = 707;
    private static final int SKAVID_C = 696, SKAVID_D = 709;
    private static final int SKAVID_MAD = 679;

    // -------------------------------------------------------------- items --

    private static final int COINS = 10, DEATH_RUNE = 38, GOLD_BAR = 172;
    private static final int GUAM = 444, GUAM_POTION = 454;
    private static final int VIAL = 464, DRAGON_BONES = 814, JANGERBERRIES = 936;
    private static final int CANDLE_LIT = 601;
    private static final int FINGERNAILS = 1036, CRYSTAL1 = 1037;
    private static final int STOLEN_GOLD = 1040, OGRE_TOOTH = 1043;
    private static final int OGRE_RELIC = 1044, SKAVID_MAP = 1045, CHEST_KEY = 1047;
    private static final int PART_OG = 1048, PART_GREW = 1049, PART_TOBAN = 1050;
    private static final int GROUND_BAT_BONES = 1051, OGRE_POTION_HALF = 1052;
    private static final int OGRE_POTION = 1053, MAGIC_OGRE_POTION = 1054;
    private static final int ROCK_CAKE = 1061, NIGHTSHADE = 1086, SHAMAN_ROBE = 1087;
    private static final int CRYSTAL2 = 1152, CRYSTAL3 = 1153, CRYSTAL4 = 1154;
    private static final int SPELL_SCROLL = 1181;
    private static final int CLUE_ROBE = 1234, CLUE_ARMOUR = 1235;
    private static final int CLUE_DAGGER = 1236, CLUE_PATCH = 1237;

    private static final int[] PICKAXES = { 156, 1258, 1259, 1260, 1261, 1262 };

    // ------------------------------------------------------------ scenery --

    private static final int HANDHOLDS = 658;
    /** The twenty-six bushes in the tower grounds that hold nothing. */
    private static final int BUSH_EMPTY = 960;
    private static final int BUSH_NAILS = 961, BUSH_DAGGER = 990;
    private static final int BUSH_ROBE = 991, BUSH_PATCH = 992, BUSH_ARMOUR = 993;
    private static final int CAVE_A = 949, CAVE_TOBAN = 950, CAVE_C = 970;
    private static final int CAVE_B = 971, CAVE_D = 972, CAVE_E = 975;
    private static final int TOBAN_LADDER = 997;
    /** The island tunnel, (668,825): a one-way shortcut home to the tower. */
    private static final int TUNNEL_CAVE = 998;
    private static final int TOBAN_CHEST = 978;
    private static final int GATE_SE = 988, GATE_NW = 989;
    private static final int JUMP_SOUTH = 995, JUMP_NORTH = 996;
    private static final int ENCLAVE_CAVE = 955, ENCLAVE_EXIT = 1024;
    private static final int ROCK_OF_DALGROTH = 1026;
    private static final int LEVER = 1014;

    // -------------------------------------------------------------- doors --

    private static final int BATTLEMENT = 195;
    private static final int EXIT_D = 187, EXIT_C = 188, EXIT_B = 189;
    private static final int EXIT_A = 191, EXIT_E = 192;

    // -------------------------------------------------- placements claimed --

    private static final int CAVE_C_X = 649, CAVE_C_Y = 770;
    private static final int LADDER_X = 604, LADDER_Y = 803;
    private static final int CHEST_X = 606, CHEST_Y = 804;
    private static final int DALGROTH_X = 646, DALGROTH_Y = 3615;
    private static final int ENCLAVE_CAVE_X = 664, ENCLAVE_CAVE_Y = 788;
    private static final int ENCLAVE_EXIT_X = 648, ENCLAVE_EXIT_Y = 3604;
    private static final int GATE_SE_X = 630, GATE_SE_Y = 793;
    private static final int GATE_NW_X = 666, GATE_NW_Y = 772;

    // -------------------------------------------------------- destinations --

    /* Every tile below is a landing point chosen here, not one recovered from
     * vanilla: the client's own teleport tables did not survive and the server
     * has no route data for any of it. Each was picked off the landscape as a
     * walkable tile beside the thing it belongs to. */
    private static final int TOWER_X = 637, TOWER_Y = 736;
    private static final int TOBAN_X = 605, TOBAN_Y = 802;
    private static final int TOBAN_BACK_X = 624, TOBAN_BACK_Y = 807;
    private static final int ENCLAVE_X = 662, ENCLAVE_Y = 3620;
    private static final int ENCLAVE_OUT_X = 663, ENCLAVE_OUT_Y = 787;

    /* Cave mouth on the surface, then the pocket it opens into. The order is
     * A, B, C, D, E and matches SKAVID_A..SKAVID_D plus the mad ones in E. */
    private static final int[][] CAVES = {
        { CAVE_A, 630, 788, 629, 3574 },
        { CAVE_B, 638, 779, 638, 3564 },
        { CAVE_C, CAVE_C_X, CAVE_C_Y, 650, 3555 },
        { CAVE_D, 628, 778, 627, 3592 },
        { CAVE_E, 647, 812, 646, 3595 },
    };

    /** Cave exit door, then the surface tile it comes out on. */
    private static final int[][] EXITS = {
        { EXIT_A, 630, 789 },
        { EXIT_B, 638, 778 },
        { EXIT_C, 649, 769 },
        { EXIT_D, 629, 778 },
        { EXIT_E, 646, 811 },
    };

    /** The six shaman spawn tiles, so each one can only be destroyed once. */
    private static final int[][] SHAMANS = {
        { 648, 3607 }, { 655, 3612 }, { 633, 3615 },
        { 660, 3619 }, { 642, 3622 }, { 653, 3622 },
    };

    // ------------------------------------------------------------- skills --

    private static final int MAGIC = 6, MINING = 14, HERBLAW = 15, AGILITY = 16;
    /**
     * Slot 17 is thieving; Formulae.statArray called it "quest" until task #38
     * fixed the label. The rock cake is stolen from a counter, so its
     * experience is written there.
     */
    private static final int THIEVING = 17;

    // ------------------------------------------------------------- stages --

    private static final int STARTED = 1, NAILS = 2, RELIC = 3, HAVE_MAP = 4,
            LEARNING = 5, SPOKEN = 6, GOT_CRYSTAL2 = 7, ENCLAVE = 8,
            POTION_TOLD = 9, POTION_MADE = 10, SHAMANS_DEAD = 11, ROCK = 12,
            ALL_GIVEN = 13, FINISHED = 14;
    private static final int STAGE_MASK = 31;

    /** Og has handed over his chest key and wants his gold back. */
    private static final int OG_TASK = 32;
    /** Og has his gold and has parted with his piece of the statue. */
    private static final int OG_DONE = 64;
    /** Grew has asked for one of Gorad's teeth. */
    private static final int GREW_TASK = 128;
    /** Grew has the tooth, and has given the crystal and his piece. */
    private static final int GREW_DONE = 256;
    /** Toban has asked for a set of dragon bones to chew on. */
    private static final int TOBAN_TASK = 512;
    /** Toban has the bones and has parted with his piece. */
    private static final int TOBAN_DONE = 1024;
    /** The north-west gate guard has seen the assembled relic. */
    private static final int GATE_RELIC = 2048;
    /** The battlement guard has had his rock cake. */
    private static final int GATE_CAKE = 4096;
    /** The rock-jump guards have been paid their twenty coins. */
    private static final int GATE_PAID = 8192;
    /** The south-east gate guard has had his bar of gold. */
    private static final int GATE_GOLD = 16384;
    /** The four ordinary skavids, one bit each, answered correctly. */
    private static final int SK_A = 32768, SK_B = 65536;
    private static final int SK_C = 131072, SK_D = 262144;
    private static final int SK_ALL = SK_A | SK_B | SK_C | SK_D;
    /** One bit per shaman spawn, so the same one cannot be poisoned twice. */
    private static final int SHAMAN_SHIFT = 19;
    private static final int SHAMAN_MASK = 63 << SHAMAN_SHIFT;
    /** The spell scroll has been read and the teleport learnt. */
    private static final int SCROLL_READ = 1 << 25;

    private static final int BITS = OG_TASK | OG_DONE | GREW_TASK | GREW_DONE
            | TOBAN_TASK | TOBAN_DONE | GATE_RELIC | GATE_CAKE | GATE_PAID
            | GATE_GOLD | SK_ALL | SHAMAN_MASK | SCROLL_READ;

    public void define() {
        setUID(UID);
        setName("Watchtower");
        // completed() is exact equality against the final stage, so the working
        // bits have to be gone by the time the lever is thrown. One survives it:
        // the scroll is only handed over at the end, so reading it necessarily
        // happens after the quest is finished, and the read has to be remembered
        // or the teleport would have nothing to gate on. Hence a second final
        // stage rather than a bit that would quietly un-complete the quest.
        setFinalStage(FINISHED);
        addFinalStage(FINISHED | SCROLL_READ);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("The magic shield that keeps the ogres of Gu'Tanoth away from Yanille has failed: the four crystals powering it have been stolen. Recover them for the Watchtower wizard and reactivate the tower.");
        setStartPoint("The Watchtower north-west of Yanille");
        setSpeakTo("Watchtower wizard");
        requireLevel(AGILITY, 30);
        requireLevel(MAGIC, 14);
        requireLevel(MINING, 40);
        requireLevel(HERBLAW, 14);
        /*
         * The payout happens at the lever, not in completeQuest(): reading the
         * spell scroll afterwards lands on the second final stage, which runs
         * completeQuest() again, so anything granted there would be paid twice.
         * The grants stay imperative in lever() and are only described here.
         */
        rewardOther("5000 coins, magic experience and a spell scroll from the Watchtower wizard");
        rewardOther("The Watchtower teleport spell, learnt by reading the scroll");

        associateNpc(WIZARD);
        associateNpc(SHAMAN);
        associateNpc(GUARD_GOLD);
        associateNpc(GUARD_RELIC);
        associateNpc(GUARD_CAKE);
        associateNpc(GUARD_CAVE);
        associateNpc(GUARD_JUMP);
        associateNpc(CITY_GUARD);
        associateNpc(OG);
        associateNpc(GREW);
        associateNpc(TOBAN);
        associateNpc(GORAD);
        associateNpc(SKAVID_SHY);
        associateNpc(SKAVID_A);
        associateNpc(SKAVID_B);
        associateNpc(SKAVID_C);
        associateNpc(SKAVID_D);
        associateNpc(SKAVID_MAD);

        associateObject(HANDHOLDS);
        // All twenty-six empty bushes stand in the tower grounds and all five
        // that hold a clue exist nowhere else, so these are safe to claim by
        // id. Searching a bush has no built-in behaviour to displace.
        associateObject(BUSH_EMPTY);
        associateObject(BUSH_NAILS);
        associateObject(BUSH_DAGGER);
        associateObject(BUSH_ROBE);
        associateObject(BUSH_PATCH);
        associateObject(BUSH_ARMOUR);
        associateObject(CAVE_A);
        associateObject(CAVE_B);
        associateObject(CAVE_D);
        associateObject(CAVE_E);
        associateObject(CAVE_TOBAN);
        // Cave 970 stands twice: (649,770) is a skavid cave and (640,786) is
        // not, so only the one placement is claimed.
        associateObject(CAVE_C, CAVE_C_X, CAVE_C_Y);
        associateObject(TOBAN_LADDER);
        associateObject(TUNNEL_CAVE, 668, 825);
        associateObject(TOBAN_CHEST);
        associateObject(GATE_SE);
        associateObject(GATE_NW);
        /*
         * Not the counter. The rock cake is Thieving's, not this quest's:
         * it is a stall like the Ardougne six, it empties and restocks the
         * same way, and vanilla lets you keep stealing from it long after
         * the quest is over. Claiming it here would have frozen it at the
         * one cake the quest needs. See defs/extras/ObjectStall.xml.gz.
         */
        associateObject(JUMP_SOUTH);
        associateObject(JUMP_NORTH);
        associateObject(ENCLAVE_CAVE);
        associateObject(ENCLAVE_EXIT);
        associateObject(ROCK_OF_DALGROTH);
        // The lever stands in the tower's top room and in the identical copy of
        // that room buried at (492,3520), which vanilla also ships. Claiming
        // the id makes both work rather than leaving the copy dead.
        associateObject(LEVER);

        associateDoor(BATTLEMENT);
        associateDoor(EXIT_A);
        associateDoor(EXIT_B);
        associateDoor(EXIT_C);
        associateDoor(EXIT_D);
        associateDoor(EXIT_E);

        // Only four items are claimed, and every one of them either has a
        // command this quest reimplements or is half of a pair this quest wants
        // offered to it. Nightshade, jangerberries, the rock cake and both
        // kinds of bones are deliberately not claimed: InvActionHandler hands a
        // claimed item's command to the quest and stops, so claiming any of
        // them would quietly make it inedible or unburiable for every player.
        // Nothing is lost by that, because ITEM_ON_NPC and ITEM_ON_OBJECT
        // dispatch on what the item was used on, not on the item.
        associateItem(SPELL_SCROLL);
        associateItem(SHAMAN_ROBE);
        associateItem(GUAM_POTION);
        associateItem(GROUND_BAT_BONES);
    }

    public void completeQuest() {
        Player p = getOwner();
        p.getActionSender().sendMessage("Well done.You have completed the Watchtower Quest");
    }

    // ------------------------------------------------------------- helpers --

    private int stage() {
        return getStage() & STAGE_MASK;
    }

    private boolean at(int s) {
        return questStarted() && stage() == s;
    }

    private boolean past(int s) {
        return questStarted() && stage() >= s;
    }

    private void step(int s) {
        // An unstarted quest reads stage -1, and -1 & BITS is every bit at
        // once -- the first step() call would bake in all the scratch state.
        int cur = questStarted() ? getStage() : 0;
        setStage(s | (cur & BITS));
    }

    private void mark(int bit) {
        setStage((questStarted() ? getStage() : 0) | bit);
    }

    private void unmark(int bit) {
        setStage(getStage() & ~bit);
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

    private int count(int id) {
        return getOwner().getInventory().countId(id);
    }

    private void give(int id) {
        give(id, 1);
    }

    private void give(int id, int amount) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, amount));
        p.getActionSender().sendInventory();
    }

    private void take(int id) {
        take(id, 1);
    }

    private void take(int id, int amount) {
        Player p = getOwner();
        p.getInventory().remove(id, amount);
        p.getActionSender().sendInventory();
    }

    private void offer(Npc npc, String line) {
        getOwner().informOfNpcMessage(new ChatMessage(npc, line, getOwner()));
    }

    private boolean holdsPickaxe() {
        for (int i = 0; i < PICKAXES.length; i++) {
            if (holds(PICKAXES[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Step across something. Both tiles are on the far side of each other, so
     * the player lands on whichever of the two is further from where they are
     * standing; that makes one pair of coordinates serve a gate in both
     * directions without asking which way anybody was facing.
     */
    private void across(int ax, int ay, int bx, int by) {
        Player p = getOwner();
        int da = Math.abs(p.getX() - ax) + Math.abs(p.getY() - ay);
        int db = Math.abs(p.getX() - bx) + Math.abs(p.getY() - by);
        if (da < db) {
            p.teleport(bx, by, false);
        } else {
            p.teleport(ax, ay, false);
        }
    }

    private boolean hasAllCrystals() {
        return holds(CRYSTAL1) && holds(CRYSTAL2) && holds(CRYSTAL3) && holds(CRYSTAL4);
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2
                    || trigger == QuestTrigger.ITEM_ON_DOOR) {
                door(object);
            } else {
                scenery(trigger, object, used);
            }
            return;
        }
        if (entity instanceof InvItem) {
            InvItem item = (InvItem) entity;
            if (trigger == QuestTrigger.ITEM_COMMAND) {
                command(item.getID());
            } else if (trigger == QuestTrigger.ITEM_PICKUP && item.getID() == SHAMAN_ROBE) {
                takenRobe();
            } else if (trigger == QuestTrigger.ITEM_ON_ITEM && used != null) {
                pair(item.getID(), used.getID());
            }
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.NPC_KILLED) {
            if (npc.getID() == GORAD && marked(GREW_TASK) && !holds(OGRE_TOOTH)) {
                give(OGRE_TOOTH);
                say("You knock one of the ogre's teeth out as he falls");
            }
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_NPC) {
            itemOnNpc(npc, used);
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case WIZARD: wizard(npc); break;
            case SHAMAN: shamanTalk(npc); break;
            case OG: og(npc); break;
            case GREW: grew(npc); break;
            case TOBAN: toban(npc); break;
            case GORAD: gorad(npc); break;
            case GUARD_RELIC: northWestGate(npc); break;
            case GUARD_GOLD: southEastGate(npc); break;
            case GUARD_CAKE: battlementGuard(npc); break;
            case GUARD_JUMP: jumpGuard(npc); break;
            case GUARD_CAVE: caveGuard(npc); break;
            case CITY_GUARD: cityGuard(npc); break;
            case SKAVID_SHY: shySkavid(npc); break;
            case SKAVID_A: skavid(npc, SK_A, "Cur bidith...", 2, "Ig", "Bidith Ig"); break;
            case SKAVID_B: skavid(npc, SK_B, "Gor cur...", 1, "Ar", "Ar cur"); break;
            case SKAVID_C: skavid(npc, SK_C, "Bidith tanath...", 0, "Cur", "Cur tanath"); break;
            case SKAVID_D: skavid(npc, SK_D, "Tanath gor...", 3, "Nod", "Gor nod"); break;
            case SKAVID_MAD: madSkavid(npc); break;
            default: break;
        }
    }

    private void scenery(QuestTrigger trigger, GameObject object, InvItem used) {
        switch (object.getID()) {
            case HANDHOLDS: handholds(); return;
            case BUSH_EMPTY: say("You search the bush"); say("You find nothing"); return;
            case BUSH_NAILS: bush(FINGERNAILS, "some fingernails"); return;
            case BUSH_DAGGER: bush(CLUE_DAGGER, "a dagger"); return;
            case BUSH_ROBE: bush(CLUE_ROBE, "a robe"); return;
            case BUSH_PATCH: bush(CLUE_PATCH, "an eye patch"); return;
            case BUSH_ARMOUR: bush(CLUE_ARMOUR, "some armour"); return;
            case CAVE_TOBAN: tobanCave(); return;
            case TOBAN_LADDER: tobanLadder(); return;
            case TUNNEL_CAVE:
                say("You enter the cave");
                getOwner().teleport(605, 803, false);
                say("Wow! that tunnel went a long way");
                return;
            case TOBAN_CHEST: tobanChest(); return;
            case GATE_SE: gate(GATE_GOLD, object); return;
            case GATE_NW: gate(GATE_RELIC, object); return;
            case JUMP_SOUTH: case JUMP_NORTH: rockJump(object.getID()); return;
            case ENCLAVE_CAVE: enclaveCave(); return;
            case ENCLAVE_EXIT: leaveEnclave(); return;
            case ROCK_OF_DALGROTH: dalgroth(trigger); return;
            case LEVER: lever(); return;
            default: skavidCave(object); return;
        }
    }

    private void door(GameObject door) {
        if (door.getID() == BATTLEMENT) {
            if (!marked(GATE_CAKE)) {
                say("The ogre guards block your way over the battlement");
                return;
            }
            say("You climb over the battlement");
            across(664, 811, 666, 811);
            return;
        }
        for (int i = 0; i < EXITS.length; i++) {
            if (EXITS[i][0] == door.getID()) {
                Player p = getOwner();
                say("You leave the cave");
                p.teleport(EXITS[i][1], EXITS[i][2], false);
                return;
            }
        }
    }

    // -------------------------------------------------- the tower and clues --

    private void handholds() {
        Player p = getOwner();
        if (p.getMaxStat(AGILITY) < 18) {
            say("You need an agility level of 18 to climb these");
            return;
        }
        say("You climb up the wall");
        say("And climb in through the window");
        // Through the window is upstairs. The tower's ground floor at
        // (637,736) is solid stone -- the handholds run up the outside of it --
        // and the room the window opens into is the first floor, the one with
        // the shields on the wall and the ladder up to the wizard.
        p.teleport(TOWER_X, TOWER_Y + 944, false);
        // 12.5 in Classic, and 12.5 is what the recovered table says; the
        // server's counter is whole numbers, so it rounds up the way every
        // other half-point agility award does.
        p.incExp(AGILITY, 13, false);
        p.getActionSender().sendStat(AGILITY);
    }

    private void bush(int id, String what) {
        say("You search the bush");
        if (holds(id)) {
            say("You find nothing");
            return;
        }
        if (getOwner().getInventory().full()) {
            say("You find " + what + ", but you have no room to carry it");
            return;
        }
        say("You find " + what);
        give(id);
    }

    // ----------------------------------------------------- Watchtower wizard --

    private void wizard(final Npc npc) {
        Player p = getOwner();
        if (completed()) {
            wizardAfter(npc);
            return;
        }
        if (!questStarted()) {
            wizardStart(npc);
            return;
        }
        if (at(STARTED)) {
            if (holds(FINGERNAILS)) {
                nails(npc);
                return;
            }
            Conversation c = new Conversation(p, npc);
            c.npc("Hello again");
            c.npc("Did you find anything of interest ?");
            c.player("No nothing yet");
            c.npc("Oh dear oh dear");
            c.npc("There must be something somewhere");
            c.start();
            return;
        }
        if (at(NAILS)) {
            wizardTribes(npc);
            return;
        }
        if (at(RELIC)) {
            if (marked(GATE_RELIC)) {
                Conversation c = new Conversation(p, npc);
                c.npc("How are you doing with the ogres ?");
                c.player("I have gained entry to the city");
                c.npc("Already ? excellent!");
                c.player("I still can't navigate the skavid caves");
                c.npc("You need a map of some kind...");
                c.npc("I bet one of the ogres has one");
                c.player("Okay thanks, I'll go and find out");
                c.start();
                return;
            }
            wizardWayIn(npc);
            return;
        }
        if (at(HAVE_MAP) || at(LEARNING) || at(SPOKEN) || at(GOT_CRYSTAL2)) {
            wizardCaves(npc);
            return;
        }
        if (at(ENCLAVE)) {
            potionRecipe(npc);
            return;
        }
        if (at(POTION_TOLD)) {
            if (holds(OGRE_POTION)) {
                empower(npc);
                return;
            }
            Conversation c = new Conversation(p, npc);
            c.npc("Any more news ?");
            c.player("Can you tell me again what I need for the potion ?");
            c.npc("Yes indeed, you need some guam leaves,");
            c.npc("Jangerberries and ground bat bones");
            c.npc("Then the potion can be powered with magic");
            c.npc("And the ogre shaman can be destroyed");
            c.start();
            return;
        }
        if (at(POTION_MADE)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Hello again");
            c.npc("Did the potion work ?");
            c.player("I am still working to rid us of these shaman...");
            c.npc("May you have sucess in your task");
            c.start();
            return;
        }
        if (at(SHAMANS_DEAD)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Hello again");
            c.npc("Did the potion work ?");
            c.player("Indeed it did!");
            c.player("I wiped out those ogre shaman!");
            c.player("I am looking for another crystal");
            c.npc("I am sure the cave holds the final one");
            c.npc("Look for the source of the shaman power...");
            c.npc("You may need something heavy to crack this boulder...");
            c.player("Okay I will go and have a look");
            c.start();
            return;
        }
        if (at(ROCK)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Well, how did it go ?");
            c.npc("Have you found any more crystals ?");
            if (hasAllCrystals()) {
                c.player("Yes, here it is!");
                c.npc("Wonderful!");
                c.npc("Show it to me so I can confirm it's the real thing...");
            } else {
                c.player("I did have the crystal but I lost it");
                c.npc("Dissappointing, dissappointing...");
                c.npc("Well there's not much I can do...");
                c.npc("You had better go back and search the area again");
            }
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("The system is not activated yet");
        c.npc("Throw the switch to start it...");
        c.start();
    }

    private void wizardStart(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Oh my Oh my!");
        c.picker(new Choice("What's the matter ?",
                             "You wizards are always complaining") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("You wizards are always complaining");
                    c.npc("Complaining ?.... complaining !");
                    c.npc("What folks these days don't realize");
                    c.npc("Is that if it wasn't for us wizards");
                    c.npc("This entire world would be overrun");
                    c.npc("With every creature that walks this world!");
                    c.message("The wizard angrily walks away");
                    return;
                }
                c.player("What's the matter ?");
                c.npc("Oh dear oh dear");
                c.npc("Darn and drat");
                c.npc("We try hard to keep this town protected");
                c.npc("But how can we do that when the watchtower isn't working ?");
                c.player("What do you mean it isn't working ?");
                c.npc("The watchtower here works by the power of a magical device");
                c.npc("An ancient spell designed to ward off ogres");
                c.npc("That has been in place here for many moons");
                c.npc("The exact knowledge of the spell is lost to us now");
                c.npc("But the essence of the spell");
                c.npc("Has been infused into 4 powering crystals");
                c.npc("To keep the tower protected from the hordes in the mendips...");
                c.picker(new Choice("So how come the spell dosen't work ?",
                                     "I'm not interested in the rantings of an old wizard") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.player("I'm not interested in the rantings of an old wizard");
                            c.message("The wizard gives you a suspicious look");
                            return;
                        }
                        c.player("So how come the spell dosen't work ?");
                        c.npc("The crystals! the crystals!");
                        c.npc("They have been taken!");
                        c.player("Taken...");
                        c.npc("Stolen!");
                        c.player("Stolen...");
                        c.npc("Yes, yes! do I have to repeat myself ?");
                        c.message("The wizard seems very stressed...");
                        c.picker(new Choice("Can I be of help ?",
                                             "I'm not sure I can help",
                                             "I'm not intersted") {
                            public void picked(int option, Conversation c) {
                                if (option == 1) {
                                    c.player("I'm not sure I can help");
                                    c.npc("Oh dear what am I to do ?");
                                    c.npc("The safety of this whole area is in jeopardy!");
                                    return;
                                }
                                if (option == 2) {
                                    c.player("I'm not interested");
                                    c.npc("That's typical nowadays");
                                    c.npc("Its left to us wizards to do all the work...");
                                    c.message("The wizard is not impressed");
                                    return;
                                }
                                c.player("Can I be of help ?");
                                c.npc("Help ?");
                                c.npc("Oh wonderful dear traveller");
                                c.npc("Yes I could do with an extra pair of eyes here");
                                c.player("???");
                                c.npc("There must be some evidence of what has happened somewhere");
                                c.npc("Perhaps you could assist me in searching for clues");
                                c.player("I would be happy to");
                                c.npc("Try searching the surrounding area");
                                c.then(new Effect() {
                                    public void run(Conversation c) {
                                        step(STARTED);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
        c.start();
    }

    private void nails(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Hello again");
        c.npc("Did you find anything of interest ?");
        c.player("Have a look at these");
        c.npc("Interesting, very interesting");
        c.npc("Long nails...grey in colour");
        c.npc("Well chewed...");
        c.npc("Of course, they belong to a skavid");
        c.take(FINGERNAILS, 1);
        c.player("A skavid ?");
        c.npc("A servant race to the ogres");
        c.npc("Gray depressed looking creatures");
        c.npc("Always loosing nails, teeth and hair");
        c.npc("They inhabit the caves in the mendip hills");
        c.npc("They normally keep to themselves though");
        c.npc("It's unusual for them to venture from their caves");
        c.npc("It's no good searching the caves");
        c.npc("Well, not yet anyway");
        c.player("Why not ?");
        c.npc("They are deep and complex");
        c.npc("The only way you will navigate the caves is to have a map or something");
        c.npc("It may be that the ogres have one");
        c.player("And how do you know that ?");
        c.npc("Well... I don't");
        c.picker(new Choice("So what do I do ?", "I won't bother then") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I won't bother then");
                    c.npc("Won't bother, won't bother ?");
                    c.npc("...Perhaps this quest is too hard for you");
                    c.message("The wizard walks away");
                    return;
                }
                c.player("So what do I do ?");
                c.npc("You need to be fearless");
                c.npc("And gain entrance to Gu'Tanoth the city of ogres");
                c.npc("And find out how to navigate the caves");
                c.player("That sounds scary");
                c.npc("Ogres are nasty creatures yes");
                c.npc("Only a strong warrior, and a clever one at that");
                c.npc("Can get the better of the ogres...");
                c.player("What do I need to do to get into the city");
                c.npc("Well the guards need to be dealt with");
                c.npc("You could start by checking out the ogre settlements around here");
                c.npc("Tribal ogres often hate their neighbours...");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        step(NAILS);
                    }
                });
            }
        });
        c.start();
    }

    private void wizardTribes(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("How's it going ?");
        c.picker(new Choice("I am having difficulty with the tribes",
                             "I have everything under control") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I have everything under control");
                    c.npc("Good, good! I will expect the crystals back shortly then...");
                    return;
                }
                c.player("I am having difficulty with the tribes");
                c.npc("Talk to them face to face");
                c.npc("And don't show any fear");
                c.npc("Make sure you are rested and well-fed");
                c.npc("And fight the good fight!");
            }
        });
        c.start();
    }

    private void wizardWayIn(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Ah the warrior returns");
        c.npc("Have you found a way into Gu'Tanoth yet ?");
        c.player("I can't get past the guards");
        c.npc("Well, ogres dislike others apart from their kind");
        c.npc("What you need is some form of proof of friendship");
        c.npc("Something to trick them into believing you are their friend");
        c.npc("...Which shouldn't be too hard considering their intelligence!");
        if (holds(OGRE_RELIC)) {
            c.start();
            return;
        }
        c.picker(new Choice("I have lost the relic you gave me",
                             "I will find my way in, no problem") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I will find my way in, no problem");
                    c.npc("Yes, I'm sure you will...good luck");
                    return;
                }
                c.player("I have lost the relic you gave me");
                c.npc("What! lost the relic ? How careless!");
                c.npc("It's a good job I copied that design then...");
                c.npc("You can take this copy instead, its just as good");
                c.give(new InvItem(OGRE_RELIC, 1));
            }
        });
        c.start();
    }

    private void wizardCaves(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (at(HAVE_MAP) || at(LEARNING)) {
            c.npc("How is the quest going ?");
            c.player("I have worked out the guard's puzzle");
            c.npc("My my! a wordsmith as well as a hero!");
            c.picker(new Choice("I am still trying to navigate the skavid caves",
                                 "I am trying to get into the shaman's cave",
                                 "It is going well") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("I am still trying to navigate the skavid caves");
                        c.npc("Take some illumination with you or else it will be dark!");
                        return;
                    }
                    if (option == 1) {
                        c.player("I am trying to get into the shaman's cave");
                        c.npc("Yes it will be well-guarded");
                        c.npc("Hmmm, let me see...");
                        c.npc("Ah yes, I gather some ogres are allergic to certain herbs...");
                        c.npc("Now what was it ?");
                        c.npc("It had white berries and blue leaves.... I remember that!");
                        c.npc("You should try looking through some of the caves...");
                        return;
                    }
                    c.player("It is going well");
                    c.npc("Thats good to hear");
                    c.npc("We are much closer to fixing the tower now");
                }
            });
            c.start();
            return;
        }
        c.npc("Hello again, how do you fare?");
        c.picker(new Choice("It goes well, I can now navigate the skavid caves",
                             "I am now ready for the shaman") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.player("It goes well, I can now navigate the skavid caves");
                    c.npc("That is good news");
                    c.npc("Let me know if you find anything of interest...");
                    return;
                }
                c.player("I am now ready for the shaman");
                c.npc("Remember all I told you, you must distract the guard somehow");
                c.npc("The herbs with blue leaves and berries is what you are looking for");
                c.npc("This herb is very poisonous however, handle it carefully");
                c.npc("Also, be on your guard in that cave");
                c.npc("Who know what monsters may be present in that awful place");
            }
        });
        c.start();
    }

    private void potionRecipe(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("I have found the cave of ogre shaman");
        c.player("But I cannot touch them!");
        c.npc("That is because of their magical powers");
        c.npc("We must fight them with their own methods");
        c.npc("Do not speak to them!");
        c.npc("I suggest a potion...");
        c.npc("Collect some guam leaves");
        c.npc("and some jangerberries");
        c.npc("And mix in some ground bat bones");
        c.npc("It is essential to return it to me before you use it");
        c.npc("So I can empower it with my magic");
        c.npc("Be very careful how you mix it, its extremely volatile");
        c.npc("Mixing ingredients of this type in the wrong order can cause explosions!");
        c.npc("I hope you've been brushing up in herblaw and magic ?");
        c.npc("I must warn you that only experienced magicians can use this potion");
        c.npc("It is too dangerous in the hands of the unskilled...");
        c.then(new Effect() {
            public void run(Conversation c) {
                step(POTION_TOLD);
            }
        });
        c.start();
    }

    private void empower(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Any more news ?");
        c.player("Yes I have made the potion");
        c.npc("That's great news, let me infuse it with magic...");
        c.message("The wizard mutters strange words over the liquid");
        c.take(OGRE_POTION, 1);
        c.give(new InvItem(MAGIC_OGRE_POTION, 1));
        c.npc("Here it is, a dangerous substance");
        c.npc("I must remind you that this potion can only be used");
        c.npc("If your magic ability is high enough");
        c.then(new Effect() {
            public void run(Conversation c) {
                step(POTION_MADE);
            }
        });
        c.start();
    }

    private void wizardAfter(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (marked(SCROLL_READ)) {
            c.npc("Greetings friend");
            c.npc("I trust all is well with you ?");
            c.npc("Yanilee is safe at last!");
            c.start();
            return;
        }
        c.npc("Hello again adventurer");
        c.npc("Thanks again for your help in keeping us safe");
        c.picker(new Choice("I lost the scroll you gave me", "That's okay") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("That's okay");
                    c.npc("We are always in your debt...");
                    return;
                }
                c.player("I lost the scroll you gave me");
                if (holds(SPELL_SCROLL)) {
                    c.npc("Ho ho ho! a comedian to the finish!");
                    c.npc("There it is, in your backpack!");
                    return;
                }
                c.npc("Never mind, have another...");
                c.give(new InvItem(SPELL_SCROLL, 1));
            }
        });
        c.start();
    }

    // ------------------------------------------------------ the chieftains --

    private void og(final Npc npc) {
        Player p = getOwner();
        if (completed() || !past(NAILS)) {
            say("The ogre is not interested in you anymore");
            return;
        }
        if (marked(OG_DONE)) {
            Conversation c = new Conversation(p, npc);
            c.npc("It's the little rat again");
            c.picker(new Choice("Do you have any other tasks for me ?",
                                 "I have lost the relic part you gave me") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("Do you have any other tasks for me ?");
                        c.npc("No, I have no more tasks for you, now go away");
                        return;
                    }
                    c.player("I have lost the relic part you gave me");
                    if (holds(PART_OG) || past(RELIC)) {
                        c.npc("Are you blind! I can see you have it even from here!");
                        return;
                    }
                    c.npc("Grrr, why do I bother ?");
                    c.npc("It's a good job I have another part!");
                    c.give(new InvItem(PART_OG, 1));
                }
            });
            c.start();
            return;
        }
        if (marked(OG_TASK)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Where is my gold from that traitor toban?");
            c.picker(new Choice("I have your gold", "I haven't got it yet",
                                 "I have lost the key!") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.player("I haven't got it yet");
                        c.npc("Don't come back until you have it");
                        c.npc("Unless you want to be on tonight's menu!");
                        return;
                    }
                    if (option == 2) {
                        c.player("I have lost the key!");
                        if (holds(CHEST_KEY)) {
                            c.npc("Oh yeah! what's that then ?");
                            c.message("It seems you still have the key...");
                            return;
                        }
                        c.npc("Idiot! take another and don't lose it!");
                        c.give(new InvItem(CHEST_KEY, 1));
                        return;
                    }
                    c.player("I have your gold");
                    if (!holds(STOLEN_GOLD)) {
                        c.npc("That is not what I want rat!");
                        c.npc("If you want to impress me");
                        c.npc("Then get the gold I asked for!");
                        return;
                    }
                    c.npc("Well well, the little rat has got it!");
                    c.npc("take this to show the little rat is a friend to the ogres");
                    c.npc("Hahahahaha!");
                    c.take(STOLEN_GOLD, 1);
                    c.message("The ogre gives you part of a horrible statue");
                    c.give(new InvItem(PART_OG, 1));
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            mark(OG_DONE);
                        }
                    });
                }
            });
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("Why are you here little rat ?");
        c.picker(new Choice("I seek entrance to the city of ogres",
                             "I have come to kill you") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I have come to kill you");
                    c.npc("Kill me eh ?");
                    c.npc("you shall be crushed like the vermin you are!");
                    c.npc("Guards!!");
                    return;
                }
                c.player("I seek entrance to the city of ogres");
                c.npc("You have no business there!");
                c.npc("Just a minute...maybe if you did something for me I might help you get in...");
                c.player("What can I do to help an ogre ?");
                c.npc("South East of here there is another settlement");
                c.npc("The name of the chieftan is Toban");
                c.npc("He stole some gold from me");
                c.npc("And I want it back!");
                c.npc("Here is a key to the chest it's in");
                c.npc("If you bring it here");
                c.npc("I may reward you...");
                c.give(new InvItem(CHEST_KEY, 1));
                c.then(new Effect() {
                    public void run(Conversation c) {
                        mark(OG_TASK);
                    }
                });
            }
        });
        c.start();
    }

    private void grew(final Npc npc) {
        Player p = getOwner();
        if (completed() || !past(NAILS)) {
            say("The ogre has nothing to say at the moment...");
            return;
        }
        if (marked(GREW_DONE)) {
            Conversation c = new Conversation(p, npc);
            c.npc("What are you doing here morsel ?");
            c.picker(new Choice("Can I do anything else for you ?",
                                 "I've lost the relic part you gave me",
                                 "I've lost the crystal you gave me") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("Can I do anything else for you ?");
                        c.npc("I have nothing left for you but the cooking pot!");
                        return;
                    }
                    if (option == 1) {
                        c.player("I've lost the relic part you gave me");
                        if (holds(PART_GREW) || past(RELIC)) {
                            c.npc("You lie to me morsel!");
                            return;
                        }
                        c.npc("Stupid morsel, I have another");
                        c.npc("Take it and go now before I lose my temper");
                        c.give(new InvItem(PART_GREW, 1));
                        return;
                    }
                    c.player("I've lost the crystal you gave me");
                    if (holds(CRYSTAL1)) {
                        c.npc("How dare you lie to me Morsel!");
                        c.npc("I will finish you now!");
                        return;
                    }
                    c.npc("I suppose you want another ?");
                    c.npc("I suppose just this once I could give you my copy...");
                    c.give(new InvItem(CRYSTAL1, 1));
                }
            });
            c.start();
            return;
        }
        if (marked(GREW_TASK)) {
            Conversation c = new Conversation(p, npc);
            c.npc("The morsel is back");
            c.npc("Does it have our tooth for us ?");
            if (!holds(OGRE_TOOTH)) {
                c.player("Err, I don't have it");
                c.npc("Morsel, you dare to return without the tooth!");
                c.npc("Either you are a fool, or want to be eaten!");
                c.start();
                return;
            }
            c.player("I have it");
            c.npc("It's got it, good good");
            c.npc("That should annoy gorad wonderfully");
            c.npc("Heheheheh!");
            c.npc("Heres a token of my gratitude");
            c.npc("Some old gem I stole from Gorad...");
            c.npc("And an old part of a statue");
            c.npc("Heheheheh!");
            c.take(OGRE_TOOTH, 1);
            c.message("The ogre hands you a large crystal");
            c.give(new InvItem(CRYSTAL1, 1));
            c.message("The ogre gives you part of a statue");
            c.give(new InvItem(PART_GREW, 1));
            c.then(new Effect() {
                public void run(Conversation c) {
                    mark(GREW_DONE);
                }
            });
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("What do you want tiny morsel ?");
        c.npc("You would look good on my plate");
        c.player("I want to enter the city of ogres");
        c.npc("Perhaps I should eat you instead ?");
        c.picker(new Choice("Don't eat me, I can help you",
                             "You will have to kill me first") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("You will have to kill me first");
                    c.npc("That can be arranged - guards!!");
                    return;
                }
                c.player("Don't eat me, I can help you");
                c.npc("What can a morsel like you do for me ?");
                c.player("I am a mighty adventurer");
                c.player("Slayer of monsters and user of magic powers");
                c.npc("Well well, perhaps the morsel can help after all...");
                c.npc("If you think you're tough");
                c.npc("Find Gorad my enemy in the south east settlement");
                c.npc("And knock one of his teeth out!");
                c.npc("Heheheheh!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        mark(GREW_TASK);
                    }
                });
            }
        });
        c.start();
    }

    private void toban(final Npc npc) {
        Player p = getOwner();
        if (completed() || !past(NAILS)) {
            say("He is busy at the moment...");
            return;
        }
        if (marked(TOBAN_DONE)) {
            Conversation c = new Conversation(p, npc);
            c.npc("The small thing returns, what do you want now ?");
            c.picker(new Choice("I seek another task",
                                 "I can't find the relic part you gave me") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("I seek another task");
                        c.npc("Have you arrived for dinner ?");
                        c.npc("Ha ha ha! begone small thing!");
                        return;
                    }
                    c.player("I can't find the relic part you gave me");
                    if (holds(PART_TOBAN) || past(RELIC)) {
                        c.npc("Small thing, you lie to me!");
                        c.npc("I always says that small things are big trouble...");
                        return;
                    }
                    c.npc("Small thing, how could you be so careless ?");
                    c.npc("Here, take this one");
                    c.give(new InvItem(PART_TOBAN, 1));
                }
            });
            c.start();
            return;
        }
        if (marked(TOBAN_TASK)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Ha ha ha! small thing returns");
            c.npc("Did you bring the dragon bone ?");
            if (!holds(DRAGON_BONES)) {
                c.player("I have nothing for you");
                c.npc("Then you shall get nothing from me!");
                c.start();
                return;
            }
            c.player("When I say I will get something I get it!");
            c.npc("Ha ha ha! small thing has done it");
            c.npc("Toban is glad, take this...");
            c.take(DRAGON_BONES, 1);
            c.message("The ogre gives you part of a statue");
            c.give(new InvItem(PART_TOBAN, 1));
            c.then(new Effect() {
                public void run(Conversation c) {
                    mark(TOBAN_DONE);
                }
            });
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("What do you want small thing ?");
        c.picker(new Choice("I seek entrance to the city of ogres",
                             "Die creature") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("Die creature");
                    c.npc("Ha ha ha! it thinks it's a match for toban does it ?");
                    return;
                }
                c.player("I seek entrance to the city of ogres");
                c.npc("Ha ha ha! you'll never get in there");
                c.player("I fear not for that city");
                c.npc("Bold words for a thing so small");
                c.player("I could do something for you...");
                c.npc("Ha ha ha! this creature thinks it can help me!");
                c.npc("I would eat you now, but for your puny size");
                c.npc("Prove to me your might");
                c.npc("Bring me the bones of a dragon to chew on");
                c.npc("And I may spare you from a painful death");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        mark(TOBAN_TASK);
                    }
                });
            }
        });
        c.start();
    }

    private void gorad(final Npc npc) {
        Player p = getOwner();
        if (!past(NAILS)) {
            say("Gorad is busy, try again later");
            return;
        }
        if (marked(GREW_TASK) && !marked(GREW_DONE)) {
            Conversation c = new Conversation(p, npc);
            c.player("I've come to knock your teeth out!");
            c.npc("How dare you utter that foul language in my prescence!");
            c.npc("You shall die quickly vermin");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.player("Hello");
        c.npc("Do you know who you are talking to ?");
        c.picker(new Choice("A big ugly brown creature...",
                             "I don't know who you are") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I don't know who you are");
                    c.npc("I am Gorad - who you are dosen't matter");
                    c.npc("Go now and you may live another day!");
                    return;
                }
                c.player("A big ugly brown creature...");
                c.npc("The impudence! take that...");
                c.player("Ouch!");
                c.message("The ogre punched you hard in the face!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player p = c.getPlayer();
                        p.setCurStat(3, Math.max(1, p.getCurStat(3) - 8));
                        p.getActionSender().sendStat(3);
                    }
                });
            }
        });
        c.start();
    }

    // -------------------------------------------------------- Toban's island --

    private void tobanCave() {
        Player p = getOwner();
        say("You enter the cave");
        p.teleport(TOBAN_X, TOBAN_Y, false);
        say("Wow! that tunnel went a long way");
    }

    private void tobanLadder() {
        Player p = getOwner();
        say("You climb down the ladder");
        p.teleport(TOBAN_BACK_X, TOBAN_BACK_Y, false);
    }

    private void tobanChest() {
        if (!holds(CHEST_KEY)) {
            say("The chest is locked");
            say("Perhaps somebody has the key");
            return;
        }
        say("You unlock the chest with the key");
        if (holds(STOLEN_GOLD) || marked(OG_DONE)) {
            say("You open the chest");
            say("There is nothing left inside");
            return;
        }
        say("You open the chest");
        say("Inside is a pile of gold");
        give(STOLEN_GOLD);
        say("You take the gold");
    }

    /* Grew's island is reached by the tree swing and the tree swing is not this
       quest's. Tying a rope to tree 662 is generic item-on-object work, in
       InvUseOnObject; the roped tree 663 and the island tree 664 are an Agility
       shortcut, level 30 for 12.5, in ObjectAgility.xml.gz. Claiming any of the
       three here would have taken a shortcut off the skill, the way the rock
       cake counter above was nearly taken off Thieving.

       Watchtower requires 30 Agility, and this crossing is why. */

    // ------------------------------------------------------------ Gu'Tanoth --

    /* Both city gates cross by which side of the gate line the player is
       standing on, not by nearest-corner distance -- the click's walk-up can
       leave the player level with the gate, where "nearest" picks the side
       they are already on and the teleport goes backwards. */
    private void gate(int bit, GameObject gate) {
        if (!marked(bit)) {
            say("The ogre guards will not let you through the gate");
            return;
        }
        say("You go through the gate");
        Player p = getOwner();
        if (gate.getID() == GATE_SE) {
            // spans x 630-631 at y 793; crossing is north-south
            int x = Math.max(630, Math.min(631, p.getX()));
            p.teleport(x, p.getY() <= 793 ? 794 : 792, false);
        } else {
            // spans x 666, y 772-773; crossing is east-west
            int y = Math.max(772, Math.min(773, p.getY()));
            /* approaching from the west stops at x 665 (the gate line at 666
               is only open on the y773 tile, reachable from the east side) */
            p.teleport(p.getX() <= 665 ? 667 : 665, y, false);
        }
    }

    private void northWestGate(final Npc npc) {
        Player p = getOwner();
        if (marked(GATE_RELIC)) {
            Conversation c = new Conversation(p, npc);
            c.npc("It's the small creature");
            c.npc("You may pass");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        if (!past(NAILS)) {
            c.npc("Stop creature!");
            c.npc("Only ogres and their friends allowed in this city");
            c.npc("Show me a sign of companionship");
            c.npc("And you may pass...");
            c.npc("Until then, back to whence you came!");
            c.message("The guard pushes you back down the hill");
            c.start();
            return;
        }
        c.npc("Well, what proof of friendship did you bring ?");
        if (!holds(OGRE_RELIC)) {
            c.player("I don't have anything");
            c.npc("Why have you returned with no proof of companionship ?");
            c.npc("Back to whence you came!");
            c.message("The guard pushes you back down the hill");
            c.start();
            return;
        }
        c.player("I have a relic from a chieftan");
        c.npc("It's got the statue of Dalgroth");
        c.npc("Welcome to Gu'Tanoth");
        c.npc("Friend of the ogres");
        c.message("The ogre guard lets you pass");
        c.then(new Effect() {
            public void run(Conversation c) {
                mark(GATE_RELIC);
                c.getPlayer().teleport(667, 773, false);
            }
        });
        c.start();
    }

    private void southEastGate(final Npc npc) {
        Player p = getOwner();
        if (marked(GATE_GOLD)) {
            Conversation c = new Conversation(p, npc);
            c.npc("I know you creature, you may pass");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        if (!past(NAILS)) {
            c.npc("Halt!");
            c.npc("You cannot pass here");
            c.player("I am a friend to ogres");
            c.npc("You will be my friend only with gold");
            c.npc("Bring me a bar of pure gold and i will let you pass");
            c.npc("For now - begone!");
            c.message("The guard pushes you outside the city");
            c.start();
            return;
        }
        c.npc("Creature, did you bring me the gold ?");
        if (!holds(GOLD_BAR)) {
            c.player("No I don't have it");
            c.npc("No gold, no passage");
            c.npc("get out of this city!");
            c.message("The guard pushes you outside the city");
            c.start();
            return;
        }
        c.player("Here it is");
        c.npc("It's brought it!");
        c.npc("On your way");
        c.take(GOLD_BAR, 1);
        c.message("The ogre guard lets you pass");
        c.then(new Effect() {
            public void run(Conversation c) {
                mark(GATE_GOLD);
            }
        });
        c.start();
    }

    private void battlementGuard(final Npc npc) {
        Player p = getOwner();
        if (marked(GATE_CAKE)) {
            Conversation c = new Conversation(p, npc);
            c.npc("It's that creature again");
            c.npc("This time we will let it go...");
            c.message("You climb over the battlement");
            c.then(new Effect() {
                public void run(Conversation c) {
                    across(664, 811, 666, 811);
                }
            });
            c.start();
            return;
        }
        if (holds(ROCK_CAKE)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Stop creature!... Oh its you");
            c.npc("Well what have you got for us then ?");
            c.player("How about this ?");
            c.message("You give the guard a rock cake");
            c.take(ROCK_CAKE, 1);
            c.npc("Well well, looks at this");
            c.npc("My favourite, rock cake!");
            c.npc("Okay we will let it through");
            c.message("You climb over the battlement");
            c.then(new Effect() {
                public void run(Conversation c) {
                    mark(GATE_CAKE);
                    across(664, 811, 666, 811);
                }
            });
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("Oi! where do you think you are going ?");
        c.npc("You are for the cooking pot!");
        c.picker(new Choice("But I am a friend to ogres...", "Not if I can help it") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("Not if I can help it");
                    c.npc("You can help by being tonight's dinner...");
                    c.npc("Or you can go away, now what shall it be ?");
                    return;
                }
                c.player("But I am a friend to ogres...");
                c.npc("Prove it to us with a gift");
                c.npc("Get us something from the market");
                c.player("Like what ?");
                c.npc("Surprise us...");
            }
        });
        c.start();
    }

    private void jumpGuard(final Npc npc) {
        Player p = getOwner();
        if (marked(GATE_PAID)) {
            Conversation c = new Conversation(p, npc);
            c.npc("On you go little thing");
            c.message("You daringly jump across the chasm");
            c.then(new Effect() {
                public void run(Conversation c) {
                    across(648, 798, 647, 804);
                }
            });
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("Oi! Little thing, if you want to cross here");
        c.npc("You can pay me first - 20 gold pieces!");
        c.player("20 gold pieces to jump off a bridge!!?");
        c.npc("That's what I said, like it or lump it");
        c.picker(new Choice("Okay i'll pay it", "Forget it, i'm not paying") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("Forget it, i'm not paying");
                    c.npc("In that case you're not crossing");
                    c.message("The guard blocks your path");
                    return;
                }
                c.player("Okay i'll pay it");
                c.npc("A wise choice little thing");
                if (count(COINS) < 20) {
                    c.npc("And where is your money ? Grrrr!");
                    c.npc("Do you want to get hurt or something ?");
                    return;
                }
                c.take(COINS, 20);
                c.message("You daringly jump across the chasm");
                c.player("Phew! I just made it");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        mark(GATE_PAID);
                        across(648, 798, 647, 804);
                    }
                });
            }
        });
        c.start();
    }

    /**
     * The broken bridge south of Gu'Tanoth, which is two rocks facing each
     * other across the gap: 995 on the bridge and 996 on the platform.
     *
     * Both need level 30 Agility, but only the jump from the bridge pays --
     * 12.5, so 13 here -- and the one back from the platform pays nothing.
     * That is what the recovered table says, and it is the usual Classic
     * arrangement for a shortcut that only counts in the hard direction.
     */
    private void rockJump(int id) {
        Player p = getOwner();
        if (!marked(GATE_PAID)) {
            say("The ogre guards block your path");
            say("They want paying before you can cross");
            return;
        }
        if (p.getMaxStat(AGILITY) < 30) {
            say("You need an agility level of 30 to jump that");
            return;
        }
        say("You daringly jump across the chasm");
        across(648, 798, 647, 804);
        if (id == JUMP_SOUTH) {
            p.incExp(AGILITY, 13, false);
            p.getActionSender().sendStat(AGILITY);
        }
    }

    private void cityGuard(final Npc npc) {
        Player p = getOwner();
        if (past(HAVE_MAP)) {
            Conversation c = new Conversation(p, npc);
            c.npc("What is it ?");
            c.picker(new Choice("Do you have any other riddles for me ?",
                                 "I have lost the map you gave me") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("Do you have any other riddles for me ?");
                        c.npc("Yes, what looks good on a plate with salad ?");
                        c.picker(new Choice("I don't know...", "A nice pizza ?") {
                            public void picked(int option, Conversation c) {
                                if (option == 1) {
                                    c.player("A nice pizza ?");
                                    c.npc("Grr.. think you are a comedian eh ?");
                                    c.npc("Get lost!");
                                    return;
                                }
                                c.player("I don't know...");
                                c.npc("You!!!");
                                c.npc("Now go and bother me no more...");
                            }
                        });
                        return;
                    }
                    c.player("I have lost the map you gave me");
                    if (holds(SKAVID_MAP)) {
                        c.npc("Are you blind ? what is that you are carrying ?");
                        c.player("Oh, that map....");
                        return;
                    }
                    c.npc("What's the point ? take this copy and bother me no more!");
                    c.give(new InvItem(SKAVID_MAP, 1));
                }
            });
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("Grrrr, what business have you here ?");
        c.player("I am on an errand...");
        c.npc("So what do you want with me ?");
        c.picker(new Choice("I seek passage into the skavid caves",
                             "I am an ogre killer come to destroy you!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I am an ogre killer come to destroy you!");
                    c.npc("I would like to see you try!");
                    return;
                }
                c.player("I seek passage into the skavid caves");
                c.npc("Is that so...");
                c.npc("You humour me small thing, answer this riddle and I will help you...");
                c.npc("I want you to bring me an item");
                c.npc("I will give you all the letters of this item, you work out what it is...");
                c.npc("My first is in days, but not in years");
                c.npc("My second is in evil, and also in tears");
                c.npc("My third is in all, but not in none");
                c.npc("My fourth is in hot, but not in sun");
                c.npc("My fifth is in heaven, and also in hate");
                c.npc("My sixth is in fearing, but not in fate");
                c.npc("My seventh is in plush, but not in place");
                c.npc("My eighth is in nine, but not in eight");
                c.npc("My last is in earth, and also in in great");
                c.npc("My whole is an object, that magic will make");
                c.npc("It brings wrack and ruin to all in it's wake...");
                c.npc("Now how long I wonder, will this riddle take ?");
            }
        });
        c.start();
    }

    // --------------------------------------------------------- skavid caves --

    private void skavidCave(GameObject object) {
        Player p = getOwner();
        for (int i = 0; i < CAVES.length; i++) {
            if (CAVES[i][0] != object.getID()) {
                continue;
            }
            if (!holds(SKAVID_MAP)) {
                say("There's no way I can find my way through without a map of some kind");
                return;
            }
            if (!holds(CANDLE_LIT)) {
                say("It's pitch black in there");
                say("I will need a light to find my way around");
                return;
            }
            say("You enter the cave");
            p.teleport(CAVES[i][3], CAVES[i][4], false);
            return;
        }
    }

    private void shySkavid(final Npc npc) {
        Player p = getOwner();
        if (completed()) {
            Conversation c = new Conversation(p, npc);
            c.npc("Ah master...");
            c.npc("You did well to master our language...");
            c.start();
            return;
        }
        if (past(SPOKEN)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Master, my kinsmen tell me you have learned skavid");
            c.npc("You should speak to the mad ones in their cave...");
            c.start();
            return;
        }
        if (past(LEARNING)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Master, how are you doing learning our language ?");
            c.player("I am studying the speech of your kind...");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("Tanath cur, tanath cur");
        c.player("???");
        c.npc("Don't hurt me, don't hurt me!");
        c.player("Stop moaning creature");
        c.player("I know about you skavids");
        c.player("You serve those monsters the ogres");
        c.npc("Please dont touch me!");
        c.player("You have something that belongs to me...");
        c.npc("I don't have anything, please believe me!");
        c.player("Somehow I find your words hard to believe");
        c.npc("I'm begging your kindness, I don't have it!");
        c.options(new Choice("Okay okay i'm not going to hurt you",
                             "I don't believe you hand it over!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I don't believe you, hand it over!");
                    c.npc("Ahhhhh, help!");
                    c.message("The skavid runs away...");
                    c.player("Oh great...I've scared it off!");
                    return;
                }
                c.player("Okay, okay i'm not going to hurt you");
                c.npc("Thank you kind sir");
                c.npc("I'll tells you where that things you wants is...");
                c.npc("The mad skavids have it in their cave in the city");
                c.npc("You will have to learn skavid");
                c.npc("Otherwise they will not talks to you");
                c.npc("Make sure you remembers all that you hear");
                c.npc("Let me tells you the most common skavid words...");
                c.npc("Ar");
                c.npc("Nod");
                c.npc("Gor");
                c.npc("Ig");
                c.npc("Cur");
                c.npc("That will gets you started...");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        step(LEARNING);
                    }
                });
            }
        });
        c.start();
    }

    /**
     * One of the four skavids that has to be answered.
     *
     * The five words the shy skavid teaches are always offered in the same
     * order -- Cur, Ar, Ig, Nod, Gor -- so a skavid is described by which of
     * those five is right for it, what it says first, and what it says back.
     */
    private void skavid(final Npc npc, final int bit, String greeting,
                        final int correct, final String answer, final String reply) {
        Player p = getOwner();
        if (!past(LEARNING)) {
            Conversation c = new Conversation(p, npc);
            c.npc(greeting);
            c.player("???");
            c.message("The skavid is trying to communicate...");
            c.message("You don't know any skavid words yet!");
            c.start();
            return;
        }
        if (marked(bit)) {
            Conversation c = new Conversation(p, npc);
            c.npc(reply + "...");
            c.message("You have already talked to this skavid");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc(greeting);
        c.message("The skavid is trying to communicate...");
        c.options(new Choice("Cur", "Ar", "Ig", "Nod", "Gor") {
            public void picked(int option, Conversation c) {
                if (option != correct) {
                    c.npc("???");
                    c.message("It seems that was the wrong reply");
                    return;
                }
                c.npc(answer);
                c.npc(reply);
                c.message("It seems the skavid understood you");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        mark(bit);
                        if ((getStage() & SK_ALL) == SK_ALL && at(LEARNING)) {
                            step(SPOKEN);
                        }
                    }
                });
            }
        });
        c.start();
    }

    /** The four things the mad skavids may open with, and the right answer. */
    private static final String[] MAD_SAYS =
        { "Bidith Ig...", "Ar cur...", "Cur tanath...", "Gor nod..." };
    private static final int[] MAD_ANSWER = { 0, 4, 2, 3 };

    private void madSkavid(final Npc npc) {
        Player p = getOwner();
        if (past(GOT_CRYSTAL2)) {
            Conversation c = new Conversation(p, npc);
            c.npc("What, you gots the crystal...");
            c.picker(new Choice("But I've lost it!", "Oh okay then") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.player("Oh okay then");
                        c.npc("I'll be on my way then");
                        return;
                    }
                    c.player("But I've lost it!");
                    if (holds(CRYSTAL2) || completed()) {
                        c.npc("I have no more for you!");
                        return;
                    }
                    c.npc("All right, take this one then...");
                    c.message("The skavid gives you a crystal");
                    c.give(new InvItem(CRYSTAL2, 1));
                }
            });
            c.start();
            return;
        }
        if (!past(SPOKEN)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Gor nod, ar bidith!");
            c.message("The skavid is trying to communicate...");
            c.message("You don't know enough skavid to answer");
            c.start();
            return;
        }
        final int which = (int) (Math.random() * MAD_SAYS.length);
        Conversation c = new Conversation(p, npc);
        c.npc(MAD_SAYS[which]);
        c.options(new Choice("Cur", "Ar", "Bidith", "Tanath", "Gor") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Grrr!");
                    c.message("It seems your response has upset the skavid");
                    return;
                }
                if (option != MAD_ANSWER[which]) {
                    c.npc("???");
                    c.message("The response was wrong");
                    return;
                }
                c.npc("Heh-heh! So you speak a little skavid eh?");
                c.npc("I'm impressed, here take this prize...");
                c.message("The skavid gives you a crystal");
                c.give(new InvItem(CRYSTAL2, 1));
                c.then(new Effect() {
                    public void run(Conversation c) {
                        step(GOT_CRYSTAL2);
                    }
                });
            }
        });
        c.start();
    }

    // ------------------------------------------------------- ogre enclave --

    private void caveGuard(final Npc npc) {
        Player p = getOwner();
        if (completed()) {
            say("The guard is occupied at the moment");
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("What do you want ?");
        c.picker(new Choice("I want to go in there", "I want to rid the world of ogres") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I want to rid the world of ogres");
                    c.npc("You dare mock me creature!!!");
                    return;
                }
                c.player("I want to go in there");
                c.npc("Oh you do, do you ?");
                c.npc("How about no ?");
            }
        });
        c.start();
    }

    private void enclaveCave() {
        /* Vanilla shuts this entrance once the shaman are gone. It cannot shut
         * the moment the sixth one dies, because the Rock of Dalgroth is inside
         * and is only mineable after they are all dead -- a player who stepped
         * out to tell the wizard would be locked out of the rest of his own
         * quest. So the wall goes up when the quest is over, not when the last
         * shaman falls. The second nightshade spawn in the caves covers the
         * return trip. */
        if (completed()) {
            say("The ogres have blocked this entrance now");
            return;
        }
        Npc guard = world.getNpc(GUARD_CAVE, 658, 668, 784, 794);
        if (guard != null) {
            offer(guard, "No you don't!");
        }
        say("The ogre guard blocks the entrance");
        say("You will have to distract him somehow");
    }

    private void leaveEnclave() {
        Player p = getOwner();
        say("You leave the cave");
        p.teleport(ENCLAVE_OUT_X, ENCLAVE_OUT_Y, false);
    }

    private void takenRobe() {
        if (at(GOT_CRYSTAL2)) {
            say("This robe belonged to one of the ogre shaman");
            say("The wizard will want to see this");
            step(ENCLAVE);
        }
    }

    private void shamanTalk(final Npc npc) {
        Player p = getOwner();
        offer(npc, "Grr! how dare you talk to us");
        offer(npc, "We will destroy you!");
        say("A magic blast comes from the shaman");
        say("You are badly injured by the blast");
        p.setCurStat(3, Math.max(1, p.getCurStat(3) - 20));
        p.getActionSender().sendStat(3);
    }

    private void poisonShaman(Npc npc) {
        Player p = getOwner();
        if (!past(POTION_MADE)) {
            // A magic ogre potion is meant to prove the wizard's recipe was
            // followed, but potions change hands like anything else in the
            // inventory -- so a traded one is checked against this player's
            // own progress too, the same as every chieftain checks NAILS.
            return;
        }
        if (p.getMaxStat(MAGIC) < 14) {
            say("You are not experienced enough with magic to use this potion");
            return;
        }
        int index = -1;
        for (int i = 0; i < SHAMANS.length; i++) {
            if (npc.getLoc() != null && npc.getLoc().startX() == SHAMANS[i][0]
                    && npc.getLoc().startY() == SHAMANS[i][1]) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            say("Nothing happens");
            return;
        }
        int bit = 1 << (SHAMAN_SHIFT + index);
        if (marked(bit)) {
            say("This shaman has already been destroyed");
            return;
        }
        mark(bit);
        say("There is a bright flash");
        say("The ogre dissolves into spirit form");
        int done = countShamans();
        if (done == 1) {
            say("Thats one destroyed...");
        } else if (done == 2) {
            say("Thats the second one gone...");
        } else if (done == 3) {
            say("Thats the next one dealt with...");
        } else if (done == 4) {
            say("There goes another one...");
        } else if (done == 5) {
            say("Thats five, only one more left now...");
        } else {
            say("You hear a scream...");
            say("The shaman dissolves before your eyes!");
            say("A crystal drops from the hand of the dissappearing ogre!");
            say("You snatch it up quickly");
            take(MAGIC_OGRE_POTION);
            give(CRYSTAL3);
            step(SHAMANS_DEAD);
        }
    }

    private int countShamans() {
        int n = 0;
        for (int i = 0; i < SHAMANS.length; i++) {
            if (marked(1 << (SHAMAN_SHIFT + i))) {
                n++;
            }
        }
        return n;
    }

    private void dalgroth(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("You examine the rock for ores...");
            say("This rock contains a crystal!");
            return;
        }
        if (!past(SHAMANS_DEAD)) {
            say("I can't touch it");
            say("Perhaps it is linked with the shaman some way ?");
            return;
        }
        if (holds(CRYSTAL4)) {
            say("I already have this crystal");
            say("There is no benefit to getting another");
            return;
        }
        if (!holdsPickaxe()) {
            say("You need a pickaxe to mine this rock");
            return;
        }
        if (p.getMaxStat(MINING) < 40) {
            say("You need a mining level of 40 to mine this rock");
            return;
        }
        say("You have a swing at the rock!");
        say("You swing your pick at the rock...");
        say("A crack appears in the rock and you prize a crystal out");
        give(CRYSTAL4);
        if (at(SHAMANS_DEAD)) {
            step(ROCK);
        }
    }

    // -------------------------------------------------------------- potion --

    private void pair(int a, int b) {
        Player p = getOwner();
        if ((a == GUAM_POTION && b == GROUND_BAT_BONES)
                || (a == GROUND_BAT_BONES && b == GUAM_POTION)) {
            /* The bones go in last. Vanilla punishes this exact mistake, which
             * is the only wrong order the wizard warns about that can be made
             * out of two things this quest owns. */
            say("You mix the ground bones into the unfinished potion");
            say("The mixture bubbles violently and explodes!");
            take(GUAM_POTION);
            take(GROUND_BAT_BONES);
            p.setCurStat(3, Math.max(1, p.getCurStat(3) - 10));
            p.getActionSender().sendStat(3);
        }
    }

    private void command(int id) {
        if (id == SPELL_SCROLL) {
            readScroll();
            return;
        }
        if (id == SHAMAN_ROBE) {
            say("You search the robe");
            say("It has been left behind by one of the ogre shaman");
            takenRobe();
        }
    }

    /**
     * Reading the spell scroll, which is the whole of Watchtower teleport's
     * unlock. A second scroll says nothing but the last line -- the first three
     * are the lesson and there is nothing left to learn.
     */
    private void readScroll() {
        if (!marked(SCROLL_READ)) {
            say("You memorise what is written on the scroll");
            say("You can now cast the Watchtower teleport spell");
            say("Provided you have the required runes and magic level");
            mark(SCROLL_READ);
        }
        take(SPELL_SCROLL);
        say("The scroll crumbles to dust");
    }

    /**
     * Published for SpellHandler. Watchtower teleport is learnt from the scroll,
     * not from the lever, so finishing the quest is necessary and not enough.
     */
    public boolean reached(String key) {
        return "watchtower-teleport".equals(key) && marked(SCROLL_READ);
    }

    // --------------------------------------------------------- items on npcs --

    private void itemOnNpc(final Npc npc, InvItem used) {
        if (used == null) {
            return;
        }
        int id = used.getID();
        if (npc.getID() == WIZARD) {
            wizardGiven(npc, id);
            return;
        }
        if (npc.getID() == CITY_GUARD && id == DEATH_RUNE && !past(HAVE_MAP)) {
            Conversation c = new Conversation(getOwner(), npc);
            c.player("I worked it out!");
            c.npc("Well well.. the imp has done it!");
            c.npc("Thanks for the rune");
            c.npc("This is what you be needing...");
            c.take(DEATH_RUNE, 1);
            c.message("The guard gives you a map");
            c.give(new InvItem(SKAVID_MAP, 1));
            c.then(new Effect() {
                public void run(Conversation c) {
                    step(HAVE_MAP);
                }
            });
            c.start();
            return;
        }
        if (npc.getID() == GUARD_RELIC && id == OGRE_RELIC) {
            northWestGate(npc);
            return;
        }
        if (npc.getID() == GUARD_GOLD && id == GOLD_BAR) {
            southEastGate(npc);
            return;
        }
        if (npc.getID() == GUARD_CAKE && id == ROCK_CAKE) {
            battlementGuard(npc);
            return;
        }
        if (npc.getID() == GUARD_CAVE && id == NIGHTSHADE) {
            nightshade(npc);
            return;
        }
        if (npc.getID() == SHAMAN && id == MAGIC_OGRE_POTION) {
            poisonShaman(npc);
            return;
        }
        say("Nothing interesting happens");
    }

    private void nightshade(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            say("The ogres have blocked this entrance now");
            return;
        }
        say("You give the guard some nightshade");
        offer(npc, "What is this!!!");
        offer(npc, "Arrrgh! I cannot stand this plant!");
        offer(npc, "Ahhh, it burns! it burns!!!");
        take(NIGHTSHADE);
        say("You run past the guard while he's busy...");
        p.teleport(ENCLAVE_X, ENCLAVE_Y, false);
    }

    private void wizardGiven(final Npc npc, int id) {
        if (id == PART_OG || id == PART_GREW || id == PART_TOBAN) {
            relicPart(npc, id);
            return;
        }
        if (id == OGRE_RELIC) {
            Conversation c = new Conversation(getOwner(), npc);
            c.player("What is this ?");
            c.npc("It is the ogre statue I finished for you...");
            c.start();
            return;
        }
        if (id == FINGERNAILS && at(STARTED)) {
            nails(npc);
            return;
        }
        if (id == SHAMAN_ROBE) {
            // takenRobe() only steps to ENCLAVE from GOT_CRYSTAL2, exactly
            // like ITEM_PICKUP does; potionRecipe() used to run regardless of
            // that, so showing a robe (found on the ground, same as picking
            // one up) could hand a fresh account POTION_TOLD outright.
            if (at(GOT_CRYSTAL2)) {
                takenRobe();
            }
            if (at(ENCLAVE)) {
                potionRecipe(npc);
            } else {
                say("Nothing interesting happens");
            }
            return;
        }
        if (id == OGRE_POTION_HALF || id == GUAM_POTION) {
            Conversation c = new Conversation(getOwner(), npc);
            c.npc("No no, the potion is not complete yet...");
            c.start();
            return;
        }
        if (id == OGRE_POTION) {
            empower(npc);
            return;
        }
        if (id == CRYSTAL1 || id == CRYSTAL2 || id == CRYSTAL3) {
            crystal(npc, id);
            return;
        }
        if (id == CRYSTAL4) {
            lastCrystal(npc);
            return;
        }
        if (id == CLUE_ROBE || id == CLUE_ARMOUR || id == CLUE_DAGGER || id == CLUE_PATCH) {
            Conversation c = new Conversation(getOwner(), npc);
            c.npc("Let me see...");
            c.npc("No, sorry this is not evidence");
            c.npc("You need to keep searching im afraid");
            c.start();
            return;
        }
        say("Nothing interesting happens");
    }

    private void relicPart(final Npc npc, final int id) {
        final boolean og = id == PART_OG;
        final boolean grew = id == PART_GREW;
        Conversation c = new Conversation(getOwner(), npc);
        if (og) {
            c.player("I got given this by an ogre");
            c.npc("Good good,a part of an ogre relic");
        } else if (grew) {
            c.player("I had this given to me");
            c.npc("It's part of an ogre relic");
        } else {
            c.player("An ogre gave me this");
            c.npc("Ah, it's part of an old ogre statue");
        }
        if (past(RELIC)) {
            c.npc("I already have that part...");
            c.start();
            return;
        }
        boolean last = holds(PART_OG) && holds(PART_GREW) && holds(PART_TOBAN);
        if (!last) {
            c.npc("There may be more parts to find...");
            c.npc("I'll keep this for later");
            c.start();
            return;
        }
        c.npc("Excellent! that seems to be all the pieces");
        c.npc("Now I can assemble it...");
        c.npc("Hmm, yes it is as I thought...");
        c.npc("A statue symbolising an ogre warrior of old");
        c.npc("Well, if you ever wanted to make friends with an ogre");
        c.npc("Then this is the item to have!");
        c.take(PART_OG, 1);
        c.take(PART_GREW, 1);
        c.take(PART_TOBAN, 1);
        c.message("The wizard gives you a complete statue");
        c.give(new InvItem(OGRE_RELIC, 1));
        c.then(new Effect() {
            public void run(Conversation c) {
                step(RELIC);
            }
        });
        c.start();
    }

    private void crystal(final Npc npc, int id) {
        Conversation c = new Conversation(getOwner(), npc);
        if (id == CRYSTAL1) {
            c.player("Wizard, look what I have found");
            c.npc("Well done! well done!");
            c.npc("That's a crystal found!");
            c.npc("You are clever");
        } else if (id == CRYSTAL2) {
            c.player("Wizard, I have another crystal");
            c.npc("Superb!");
            c.npc("Keep up the good work");
        } else {
            c.player("Wizard, here is another crystal");
            c.npc("I must say i'm impressed");
            c.npc("May Saradomin speed you in finding them all");
        }
        c.npc("Hold onto it until you have all four...");
        c.npc("Keep searching for the others");
        c.npc("If you've dropped any...");
        c.npc("Then you will need to go back to where you got it from");
        c.start();
    }

    private void lastCrystal(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (past(ALL_GIVEN)) {
            c.npc("More crystals ?");
            c.npc("I don't need any more now...");
            c.start();
            return;
        }
        if (!hasAllCrystals()) {
            c.npc("Well done! Well done!");
            c.npc("Keep searching for the others");
            c.npc("If you've dropped any...");
            c.npc("Then you will need to go back to where you got it from");
            c.start();
            return;
        }
        c.player("This is the last one");
        c.npc("Magnificent!");
        c.npc("At last you've brought all the crystals");
        c.npc("Now the shield generator can be activated again");
        c.npc("And once again Yanille will be safe");
        c.npc("From the threat of the ogres");
        c.npc("Throw the lever to activate the system...");
        c.then(new Effect() {
            public void run(Conversation c) {
                step(ALL_GIVEN);
            }
        });
        c.start();
    }

    // --------------------------------------------------------------- lever --

    private void lever() {
        Player p = getOwner();
        if (completed()) {
            say("You pull the lever");
            say("The magic forcefield is already active");
            return;
        }
        if (!at(ALL_GIVEN)) {
            say("You pull the lever");
            say("Nothing happens");
            return;
        }
        if (!hasAllCrystals()) {
            say("You pull the lever");
            say("Nothing happens");
            say("The wizard needs all four crystals before this will work");
            return;
        }
        final Npc wizard = world.getNpc(WIZARD, 630, 645, 2620, 2635);
        say("You pull the lever");
        take(CRYSTAL1);
        take(CRYSTAL2);
        take(CRYSTAL3);
        take(CRYSTAL4);
        say("The magic forcefield activates");
        if (wizard != null) {
            offer(wizard, "Marvellous! it works!");
            offer(wizard, "The town will now be safe");
            offer(wizard, "Your help was invaluable");
            offer(wizard, "Take this payment as a token of my gratitude...");
        }
        give(COINS, 5000);
        say("The wizard gives you 5000 pieces of gold");
        say("The wizard lays his hands on you...");
        say("You feel magic power increasing");
        p.incExp(MAGIC, (p.getMaxStat(MAGIC) + 1) * 250, false);
        p.getActionSender().sendStat(MAGIC);
        give(SPELL_SCROLL);
        if (wizard != null) {
            offer(wizard, "Here is a special item for you...");
            offer(wizard, "It's a new spell");
            offer(wizard, "Read the scroll and you will be able");
            offer(wizard, "To teleport yourself to here magically...");
        }
        setStage(FINISHED);
    }
}
