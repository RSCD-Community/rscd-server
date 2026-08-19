import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.SingleEvent;
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
 * Tourist trap. Released 14 April 2003, written by Tytn Hays.
 *
 * Irena's daughter Ana went into the Kharidian desert as a tourist and did not
 * come back, because the Desert Mining Camp takes anyone who wanders near it
 * and puts them to work. The way in is not a fight you can win -- the mercenary
 * guards gang up -- so it is done by noticing that the Mercenary Captain never
 * fights his own battles, goading him into a duel he loses, and then walking in
 * dressed as a slave.
 *
 * The quest is a long chain of favours: the slave wants desert clothes for his
 * robes, the mine guard wants a Tenti pineapple before he will let you deeper,
 * the Bedabin chief wants Captain Siad's technical plans before he will part
 * with a pineapple, and the plans turn out to be for the throwing dart, which
 * is the reward the quest is really about.
 *
 *     Irena          npc 538, (64,738), outside the Shantay Pass
 *     Ana            npc 554, (68,3605), the deepest part of the mine
 *     Mercenary Captain  npc 669, three spawns east of the camp gate
 *     Mercenary      npc 668 general, 670 outside the Mining Cave (pineapple),
 *                    690 at the Lift Platform, 692 outside the mine jail
 *     Mining Slave   npc 671, sixteen of them; 737 is the one that escapes
 *     Al Shabim      npc 700, (172,807), the big Bedabin tent
 *     Captain Siad   npc 702, (85,1747) -- floor 1, so (85,803) upstairs
 *     Bedabin Nomad Guard npc 703, (170,794), outside the forge tent
 *     Mining Cart Driver  npc 711, (88,807)
 *
 *     Metal Key 1021          Cell Door Key 1098      Wrought iron key 1097
 *     Bedobin Copy Key 1059   Technical Plans 1060    Tenti Pineapple 1058
 *     Prototype dart tip 1071 Prototype Throwing Dart 1014
 *     Mining Barrel 1038      Ana in a Barrel 1039
 *     Slaves Robe Top 1023    Slaves Robe Bottom 1022
 *
 * Two npcs in this quest have no spawn anywhere in the world, in RSCD and in
 * vanilla alike: 710 Draft Mercenary Guard and 737 Escaping Mining Slave. They
 * are not missing data -- they are spawned by the quest and despawned again,
 * which is why they are claimed here but never looked for on the map.
 *
 * Desert heat lives here too, though it applies to every player in the desert
 * whether or not they ever start the quest -- the whole-server sweep pattern
 * is Gertrude's cat's kitten ticker. Jagex shipped the heat with this quest in
 * April 2003; the Gertrude's Cat update broke it on 28 July 2003 and it was
 * never fixed. The project's rule is the intended game, not the shipped bugs,
 * so it works here. The shape is wiki-attested: periodic thirst south of the
 * pass, an automatic drink from a carried waterskin, Hits damage when there is
 * nothing to drink, refills from a knife on a cactus or a water vessel on the
 * skin, and desert clothes slowing the thirst down. Every number and every
 * message in it is ours -- no replay of the fifteen weeks it worked survives
 * (the ones we found either skip the desert walks or postdate the breakage),
 * so the constants sit together below, ready to be retuned the day one turns
 * up.
 */
public class TouristTrap extends Quest {

    private static final int UID = Quests.TOURIST_TRAP;

    private static final int IRENA = 538, ANA = 554;
    private static final int MERC = 668, CAPTAIN = 669, MERC_CAVE = 670;
    private static final int SLAVE = 671, MERC_LIFT = 690, MERC_JAIL = 692;
    private static final int SHABIM = 700, SIAD = 702, NOMAD_GUARD = 703;
    private static final int DRAFT_GUARD = 710, CART_DRIVER = 711;
    /**
     * A separate water-seller under Al Shabim, distinct from the Nomad Guard
     * (703) who blocks the forge tent. Sells three water items at a fixed
     * unconditional price, no quest gate -- a genuine vanilla pricing quirk
     * is kept here on purpose: the waterskin's OWN option text quotes 20gp,
     * but the actual charge is 25gp. The seller had no dialogue at all
     * in upstream RSCDaemon.
     */
    private static final int WATER_SELLER = 701;
    private static final int WATER_JUG = 141, WATER_BUCKET = 50, WATERSKIN = 1016;
    private static final int WATER_JUG_PRICE = 5, WATER_BUCKET_PRICE = 20, WATERSKIN_PRICE = 25;
    private static final int ESCAPING_SLAVE = 737;

    private static final int COINS = 10, BRONZE_BAR = 169, FEATHER = 381;

    /** What the cart driver settles on after the player opens with ten. */
    private static final int CART_BRIBE = 100;
    private static final int DESERT_BOOTS = 990, DESERT_ROBE = 1019, DESERT_SHIRT = 1020;
    private static final int SLAVE_BOTTOM = 1022, SLAVE_TOP = 1023;
    private static final int METAL_KEY = 1021, CELL_KEY = 1098, WROUGHT_KEY = 1097;
    private static final int COPY_KEY = 1059, PLANS = 1060, PINEAPPLE = 1058;
    private static final int DART_TIP = 1071, PROTO_DART = 1014, BRONZE_DART = 1013;
    private static final int BARREL = 1038, ANA_BARREL = 1039;
    /* The four skins that still hold something. 1085 is the empty one and the
     * guards have no reason to throw it away. */
    private static final int[] WATER_SKINS = { 1016, 1082, 1083, 1084 };

    // -------------------------------------------------------- desert heat --
    /* Full -> mostly full -> mostly empty -> mouthful left -> empty. A drink
     * or a refill moves one step along this chain. */
    private static final int[] SKIN_CHAIN = { 1016, 1082, 1083, 1084, 1085 };
    private static final int FULL_SKIN = 1016, EMPTY_SKIN = 1085;
    private static final int KNIFE = 13;
    /* Bucket of water, jug of water, bowl of water -- and what each becomes
     * when poured out. */
    private static final int[] WATER_VESSELS = { 50, 141, 342 };
    private static final int[] EMPTY_VESSELS = { 21, 140, 341 };
    private static final int CACTUS = 35, DRIED_CACTUS = 1028;
    /* The heat zone: the desert south of the Shantay Pass gate line (y=734).
     * x 198 onward is Tutorial Island; every cactus and every piece of quest
     * content sits inside x 51-186, y 735-814. The y range also keeps the
     * upstairs floors and the mine out of the sun on its own. */
    private static final int DESERT_MIN_X = 40, DESERT_MAX_X = 196;
    private static final int DESERT_MIN_Y = 735, DESERT_MAX_Y = 830;
    /* Shade inside the zone, as {minX, maxX, minY, maxY}: the walled Mining
     * Camp compound and the two Bedabin tents. The sun stops at the door. */
    private static final int[][] SHADE = {
        { 80, 91, 799, 813 },    // the Mining Camp compound
        { 170, 175, 804, 809 },  // the big Bedabin tent
        { 169, 172, 791, 793 },  // the Bedabin forge tent
    };
    /* Tuning. All of it ours -- see the header. Three minutes of walking per
     * drink comes out at four or five full skins over the quest, which is
     * what the wiki-era walkthroughs budget; each worn piece of desert kit
     * stretches the interval by a minute. */
    private static final int HEAT_PULSE_MS = 30000;
    private static final int THIRST_SECONDS = 180;
    private static final int CLOTHING_SECONDS = 60;
    private static final int HEAT_DAMAGE = 2;
    private static final int CACTUS_REGROW_MS = 120000;
    private static boolean heatArmed = false;
    /** Seconds spent in the sun since the last drink. Per-player because the
     * quest instance is; deliberately not persisted -- logging out is shade. */
    private int sunSeconds = 0;
    /* Item 986 is the load of rocks a slave mines; scenery 1030 is the rock
     * face he mines it from. Both are Tourist trap's and nothing else uses
     * either -- see mineRocks(). */
    private static final int ROCKS = 986;

    private static final int IRON_GATE = 932, GATE_X = 92, GATE_Y = 807;
    private static final int SAND_A = 944, SAND_B = 945;
    private static final int WOODEN_DOORS = 958;
    private static final int DOORS_X = 81, DOORS_Y = 801;
    private static final int MINE_CAVE_A = 963, MINE_CAVE_B = 964;
    private static final int LIFT = 966, LIFT_X = 89, LIFT_Y = 810;
    private static final int BARRELS = 967;
    private static final int MINE_CART = 976;
    private static final int LIFT_PLATFORM = 977, PLATFORM_X = 72, PLATFORM_Y = 3636;
    private static final int ROCK_FACE = 1030;
    private static final int BOOKCASE = 1004;
    private static final int CAPTAINS_CHEST = 1005, CHEST_X = 85, CHEST_Y = 1746;
    private static final int ANVIL = 1006, ANVIL_X = 170, ANVIL_Y = 792;
    /* Scenery 1023 and item 1023 are different tables; the desk and the slave
     * robe top share a number and nothing else. */
    private static final int DESK = 1023, DESK_X = 86, DESK_Y = 1746;
    private static final int ESCAPE_CART = 1025, CART_X = 84, CART_Y = 807;

    private static final int JAIL_DOOR = 177, WINDOW = 178, MINE_JAIL_DOOR = 180;

    /* Landing tiles. Chosen from the object placements, not derived from
     * collision. */
    private static final int OUTSIDE_GATE_X = 93, OUTSIDE_GATE_Y = 807;
    private static final int INSIDE_GATE_X = 91, INSIDE_GATE_Y = 807;
    private static final int MINE_X = 82, MINE_Y = 3632;
    private static final int SURFACE_X = 81, SURFACE_Y = 802;
    /* South of the cave mouth, not inside it: the mouth is a three-tile solid
     * pillar and (77,3641) is the middle of it. */
    private static final int DEEP_X = 77, DEEP_Y = 3642;
    private static final int SHALLOW_X = 82, SHALLOW_Y = 3638;
    /* The two Mine Cart objects, (62,3639) and (56,3631), are the ends of one
     * set of rails; riding either lands you beside the other. Ana is walked to
     * from the far end, not ridden to -- she stands at (68,3605) and no cart
     * runs to her. */
    private static final int CART_A_X = 63, CART_A_Y = 3639;
    private static final int CART_B_X = 57, CART_B_Y = 3631;
    private static final int CELL_X = 89, CELL_Y = 802;
    /* The cell is the two columns x 88-89. Door 177 sits on the east edge of
     * (88,801), so (88,801) is the cell side of it and (87,801) the free
     * side. */
    private static final int CELL_MIN_X = 88;
    private static final int OUT_OF_CELL_X = 87, OUT_OF_CELL_Y = 801;
    /* The window is in the west wall, on the east edge of (90,802) -- the
     * cell side of it is (89,802). Squeezing through puts you on the ledge
     * at (90,799), and the way down from there is the two climbable rock
     * piles: 953 at (91,801) crosses ledge<->(92,801), 954 at (92,800)
     * crosses (92,801)<->(93,798), the open ground north-west of the jail.
     * Both piles climb in either direction; the window squeezes both ways
     * too, so nobody strands themselves on the rocks. */
    private static final int LEDGE_X = 90, LEDGE_Y = 799;
    private static final int WINDOW_IN_X = 89, WINDOW_IN_Y = 802;
    private static final int ROCKS_LOW = 953, ROCKS_HIGH = 954;
    private static final int ROCKS_MID_X = 92, ROCKS_MID_Y = 801;
    private static final int ROCKS_OUT_X = 93, ROCKS_OUT_Y = 798;
    private static final int FREEDOM_X = 66, FREEDOM_Y = 740;
    /* The mine jail. Door 180 sits on the west edge of (72,3626), so the two
     * tiles it separates are (71,3626) outside and (72,3626) in. The two jail
     * guards, npc 692, stand at (70,3625) and (70,3627) -- outside, which is
     * how the inside is known to be east. */
    private static final int MINE_JAIL_X = 72, MINE_JAIL_Y = 3626;
    private static final int OUT_OF_MINE_JAIL_X = 71, OUT_OF_MINE_JAIL_Y = 3626;
    /* Open desert north-east of the camp. Chosen from an npc spawn tile, like
     * the other landing tiles here, because it is known to be walkable; the
     * transcript only says "the desert". */
    private static final int STRANDED_X = 131, STRANDED_Y = 769;

    private static final int HITS = 3, FLETCHING = 9, SMITHING = 13, AGILITY = 16;
    /* Slot 17 is thieving. Formulae.statArray labelled it "quest" until task
     * #38 fixed it. Tourist trap is one of the two quests that award thieving
     * experience, so it writes there. */
    private static final int THIEVING = 17;

    private static final int STARTED = 1, CAPTAIN_DEAD = 2, SLAVE_DEAL = 3,
        ROBES = 4, PINEAPPLE_ASKED = 5, SHABIM_DEAL = 6, HAVE_PLANS = 7,
        FORGE_OK = 8, DART_MADE = 9, HAVE_PINEAPPLE = 10, DEEP_MINE = 11,
        ANA_MET = 12, ANA_ON_LIFT = 13, ANA_UP = 14, CART_READY = 15,
        ESCAPED = 16, FINISHED = 17;

    private static final int STAGE_MASK = 31;

    /** The captain has been watched, which is what unlocks the taunt. */
    private static final int WATCHED = 32;
    /** The slave's handcuffs are picked and he is waiting on desert clothes. */
    private static final int CUFFS_OFF = 64;
    /** The first of the two reward skills has been chosen. */
    private static final int REWARD1 = 128;
    /** Captain Siad is at the window looking for a fire that is not there. */
    private static final int SIAD_BUSY = 256;
    /** The dart plans have been handed over and darts can be fletched. */
    private static final int DARTS_LEARNT = 512;
    /**
     * Which bet the player has with the guards on the captain's duel: 0 for
     * none, then 1 to 4 for the five, ten, fifteen and twenty gold stakes.
     */
    private static final int BET_MASK = 1024 | 2048 | 4096, BET_SHIFT = 10;
    /** The guards have been paid off for the mess the dead captain left. */
    private static final int SETTLED = 8192;
    /**
     * The captain's bookcase has been searched, which is the only way to learn
     * that he is a sailing man and the only thing that unlocks the one line of
     * flattery he will actually stop working for.
     */
    private static final int BOOKS_SEEN = 16384;
    private static final int BITS = WATCHED | CUFFS_OFF | REWARD1 | SIAD_BUSY
        | DARTS_LEARNT | BET_MASK | SETTLED | BOOKS_SEEN;

    /** What the guards pay back on a winning stake, and what that nets. */
    private static final int[] STAKE = { 5, 10, 15, 20 };
    private static final int[] PAYOUT = { 6, 12, 19, 30 };
    private static final int[] WINNINGS = { 1, 2, 4, 10 };

    public TouristTrap(Player owner, Integer uid) {
        super(owner, uid);
    }

    public void define() {
        setUID(UID);
        setName("Tourist trap");
        setFinalStage(FINISHED);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("Irena's daughter Ana went into the Kharidian desert and never came back. Find her in the Desert Mining Camp and smuggle her out past the mercenary guards.");
        setStartPoint("Outside the Shantay Pass");
        setSpeakTo("Irena");
        requireLevel(SMITHING, 20);
        requireLevel(FLETCHING, 10);
        rewardOther("Experience in two skills of your choice from Fletching, Agility, Smithing and Thieving");
        rewardOther("The ability to make throwing darts");

        associateNpc(IRENA);
        associateNpc(ANA);
        associateNpc(MERC);
        /* He carries "watch", the only one in Classic that does. */
        associateNpcCommand(CAPTAIN);
        associateNpc(MERC_CAVE);
        associateNpc(SLAVE);
        associateNpc(MERC_LIFT);
        associateNpc(MERC_JAIL);
        associateNpc(SHABIM);
        associateNpc(SIAD);
        associateNpc(NOMAD_GUARD);
        associateNpc(DRAFT_GUARD);
        associateNpc(CART_DRIVER);
        associateNpc(WATER_SELLER);
        associateNpc(ESCAPING_SLAVE);

        associateObject(IRON_GATE);
        associateObject(SAND_A);
        associateObject(SAND_B);
        associateObject(WOODEN_DOORS);
        associateObject(MINE_CAVE_A);
        associateObject(MINE_CAVE_B);
        associateObject(LIFT);
        associateObject(BARRELS);
        associateObject(MINE_CART);
        associateObject(LIFT_PLATFORM);
        associateObject(ROCK_FACE);
        associateObject(BOOKCASE);
        associateObject(CAPTAINS_CHEST);
        associateObject(ANVIL);
        associateObject(DESK);
        associateObject(ESCAPE_CART);
        /* The jail's escape rocks; these two piles exist nowhere else. */
        associateObject(ROCKS_LOW);
        associateObject(ROCKS_HIGH);

        associateDoor(JAIL_DOOR);
        associateDoor(WINDOW);
        associateDoor(MINE_JAIL_DOOR);

        associateItem(METAL_KEY);
        associateItem(CELL_KEY);
        associateItem(COPY_KEY);
        associateItem(PLANS);
        associateItem(PINEAPPLE);
        associateItem(DART_TIP);
        associateItem(PROTO_DART);
        associateItem(BARREL);
        associateItem(ANA_BARREL);
        // Feathers are claimed only so that feather-on-dart-tip becomes a pair
        // this quest owns. InvUseOnItem offers a pair to the quests first and
        // only falls through to its own recipes when no single quest has
        // claimed both halves, so ordinary fletching -- feather on arrow shaft
        // -- is untouched, because the shaft is nobody's.
        associateItem(FEATHER);

        /* Desert heat. The cactus is claimed everywhere it grows -- it only
         * carries WalkTo and Examine, so the claim takes nothing away, and
         * cutting one open for water in Al Kharid is as sensible as in the
         * deep desert. The vessels and the partial skins are claimed so that
         * water-on-skin becomes a pair this quest owns; none of the six has
         * an inventory command to lose, and no other quest claims any of
         * them. The full skin stays unclaimed: nothing pours into a full
         * skin, and pair() answers every claimed pairing regardless. */
        associateObject(CACTUS);
        for (int i = 0; i < WATER_VESSELS.length; i++) {
            associateItem(WATER_VESSELS[i]);
        }
        for (int i = 1; i < SKIN_CHAIN.length; i++) {
            associateItem(SKIN_CHAIN[i]);
        }
        armHeat();
    }

    public void completeQuest() {
        Player p = getOwner();
        p.getActionSender().sendMessage("Well Done!");
        p.getActionSender().sendMessage("Well done.You have completed the 'Tourist Trap' Quest");
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

    private int field(int mask, int shift) {
        return questStarted() ? (getStage() & mask) >> shift : 0;
    }

    private void setField(int mask, int shift, int value) {
        setStage((getStage() & ~mask) | ((value << shift) & mask));
    }

    /** 0 for no bet, else an index into STAKE/PAYOUT/WINNINGS plus one. */
    private int bet() {
        return field(BET_MASK, BET_SHIFT);
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
        give(id, 1);
    }

    private void give(int id, int amount) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, amount));
        p.getActionSender().sendInventory();
    }

    private void take(int id) {
        Player p = getOwner();
        p.getInventory().remove(id, 1);
        p.getActionSender().sendInventory();
    }

    private void offer(Npc npc, String line) {
        getOwner().informOfNpcMessage(new ChatMessage(npc, line, getOwner()));
    }

    private boolean disguised() {
        return wearing(SLAVE_TOP) && wearing(SLAVE_BOTTOM);
    }

    private boolean hasDesertClothes() {
        return holds(DESERT_SHIRT) && holds(DESERT_ROBE) && holds(DESERT_BOOTS);
    }

    /**
     * Anything a mercenary catches you at ends the same way. Armour and weapons
     * are not confiscated -- vanilla only says the guards search you -- so this
     * moves the player and nothing else.
     */
    private void jail(String reason) {
        Player p = getOwner();
        if (reason != null) {
            say(reason);
        }
        say("The Guards search you!");
        // "manhandlded" and "to a cell" are Jagex's, recorded identically in
        // both the Mining Slave and the Mining Cart Driver transcripts.
        say("More guards rush to catch you.");
        say("You are roughed up a bit by the guards as you're manhandlded to a cell.");
        say("@yel@Mercenary: Into the cell you go! I hope this teaches you a lesson.");
        p.teleport(CELL_X, CELL_Y, false);
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
            }
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.NPC_KILLED) {
            if (npc.getID() == CAPTAIN) {
                captainDies();
            }
            return;
        }
        if (trigger == QuestTrigger.NPC_COMMAND) {
            if (npc.getID() == CAPTAIN) {
                watchCaptain();
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
            case IRENA: irena(npc); break;
            case ANA: ana(npc); break;
            case CAPTAIN: captain(npc); break;
            case SLAVE: miningSlave(npc); break;
            case MERC_CAVE: caveMercenary(npc); break;
            case MERC_LIFT: liftMercenary(npc); break;
            case MERC_JAIL: jailMercenary(npc); break;
            case MERC: mercenary(npc); break;
            case SHABIM: alShabim(npc); break;
            case SIAD: captainSiad(npc); break;
            case NOMAD_GUARD: nomadGuard(npc); break;
            case CART_DRIVER: cartDriver(npc); break;
            case WATER_SELLER: waterSeller(npc); break;
            case DRAFT_GUARD: say("@yel@Mercenary: Get back to work!"); break;
            case ESCAPING_SLAVE: say("@yel@Escaping Mining Slave: Right, I'm off! Good luck!"); break;
            default: break;
        }
    }

    private void scenery(QuestTrigger trigger, GameObject object, InvItem used) {
        switch (object.getID()) {
            case IRON_GATE: ironGate(trigger); return;
            case SAND_A: case SAND_B: disturbedSand(trigger); return;
            case WOODEN_DOORS: woodenDoors(trigger); return;
            case MINE_CAVE_A: case MINE_CAVE_B: miningCave(trigger); return;
            case ROCK_FACE: mineRocks(trigger); return;
            case LIFT: surfaceLift(trigger); return;
            case BARRELS: barrels(trigger); return;
            case MINE_CART: mineCart(trigger); return;
            case LIFT_PLATFORM: liftPlatform(trigger, used); return;
            case BOOKCASE: bookcase(trigger); return;
            case CAPTAINS_CHEST: captainsChest(trigger, used); return;
            case ANVIL: experimentalAnvil(trigger, used); return;
            case DESK: desk(trigger); return;
            case ESCAPE_CART: escapeCart(trigger, used); return;
            case JAIL_DOOR: jailDoor(trigger); return;
            case MINE_JAIL_DOOR: mineJailDoor(trigger); return;
            case WINDOW: cellWindow(trigger); return;
            case CACTUS: cactus(trigger, object, used); return;
            case ROCKS_LOW: case ROCKS_HIGH: escapeRocks(object); return;
            default: return;
        }
    }

    /** The two climbable piles between the cell ledge and freedom. Which
     * side you land on is which side you climbed from. */
    private void escapeRocks(GameObject object) {
        Player p = getOwner();
        say("You climb over the rocks");
        if (object.getID() == ROCKS_LOW) {
            boolean fromLedge = p.getX() <= object.getX();
            p.teleport(fromLedge ? ROCKS_MID_X : LEDGE_X,
                       fromLedge ? ROCKS_MID_Y : LEDGE_Y, false);
        } else {
            boolean fromMid = p.getX() <= object.getX();
            p.teleport(fromMid ? ROCKS_OUT_X : ROCKS_MID_X,
                       fromMid ? ROCKS_OUT_Y : ROCKS_MID_Y, false);
        }
    }

    // -------------------------------------------------------- desert heat --

    /**
     * The whole-server sweep, once per pulse. Every online player's copy of
     * this quest gets a look; the event has no owner, so no logout removes
     * it. The pattern is Gertrude's cat's kitten ticker.
     */
    private void armHeat() {
        synchronized (TouristTrap.class) {
            if (heatArmed) {
                return;
            }
            heatArmed = true;
            world.getDelayedEventHandler().add(new DelayedEvent(null, HEAT_PULSE_MS) {
                public void run() {
                    for (Player p : world.getPlayers()) {
                        Quest q = p.getQuestManager().getQuest(UID);
                        if (q instanceof TouristTrap) {
                            ((TouristTrap) q).sunPulse();
                        }
                    }
                }
            });
        }
    }

    /** One pulse of desert sun. Leaving the desert is shade: the clock
     * resets rather than pauses, so stepping back through the pass always
     * buys a full interval. */
    private void sunPulse() {
        Player p = getOwner();
        if (p == null || !inDesert(p)) {
            sunSeconds = 0;
            return;
        }
        sunSeconds += HEAT_PULSE_MS / 1000;
        if (sunSeconds < THIRST_SECONDS + CLOTHING_SECONDS * desertKit(p)) {
            return;
        }
        sunSeconds = 0;
        p.getActionSender().sendMessage("The heat of the desert is unbearable...");
        for (int i = SKIN_CHAIN.length - 2; i >= 0; i--) {
            if (p.getInventory().countId(SKIN_CHAIN[i]) > 0) {
                p.getInventory().remove(SKIN_CHAIN[i], 1);
                p.getInventory().add(new InvItem(SKIN_CHAIN[i + 1], 1));
                p.getActionSender().sendInventory();
                p.getActionSender().sendMessage("You take a drink of water from your waterskin");
                return;
            }
        }
        p.getActionSender().sendMessage("@red@You have no water left and the sun beats down on you");
        p.setLastDamage(HEAT_DAMAGE);
        p.setHits(p.getHits() - HEAT_DAMAGE);
        for (Player viewer : p.getViewArea().getPlayersInView()) {
            viewer.informOfModifiedHits(p);
        }
        p.getActionSender().sendStat(HITS);
        if (p.getHits() <= 0) {
            p.killedBy(null, false);
        }
    }

    private boolean inDesert(Player p) {
        for (int i = 0; i < SHADE.length; i++) {
            if (p.getX() >= SHADE[i][0] && p.getX() <= SHADE[i][1]
                && p.getY() >= SHADE[i][2] && p.getY() <= SHADE[i][3]) {
                return false;
            }
        }
        return p.getX() >= DESERT_MIN_X && p.getX() <= DESERT_MAX_X
            && p.getY() >= DESERT_MIN_Y && p.getY() <= DESERT_MAX_Y;
    }

    /** How much desert kit is actually worn -- shirt, robe, boots. */
    private int desertKit(Player p) {
        int worn = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            InvItem item = p.getInventory().get(i);
            if (item == null || !item.isWielded()) {
                continue;
            }
            int id = item.getID();
            if (id == DESERT_SHIRT || id == DESERT_ROBE || id == DESERT_BOOTS) {
                worn++;
            }
        }
        return worn;
    }

    /**
     * A knife on a cactus squeezes one dose of sap into the emptiest skin
     * carried, and the cactus stands dry for a couple of minutes afterwards
     * -- the Dried Cactus is object 1028, in the vanilla defs with no
     * placement anywhere: Jagex built it for exactly this and never wired
     * it in, which is the same story as the heat itself.
     */
    private void cactus(QuestTrigger trigger, GameObject object, InvItem used) {
        Player p = getOwner();
        if (trigger != QuestTrigger.ITEM_ON_OBJECT || used == null) {
            return;
        }
        if (used.getID() != KNIFE) {
            say("Nothing interesting happens.");
            return;
        }
        for (int i = SKIN_CHAIN.length - 1; i >= 1; i--) {
            if (p.getInventory().countId(SKIN_CHAIN[i]) > 0) {
                say("You cut into the cactus with your knife");
                say("and squeeze the sap into your waterskin");
                p.getInventory().remove(SKIN_CHAIN[i], 1);
                p.getInventory().add(new InvItem(SKIN_CHAIN[i - 1], 1));
                p.getActionSender().sendInventory();
                dryOut(object);
                return;
            }
        }
        say("You cut into the cactus with your knife");
        say("but you have nothing to catch the sap in");
    }

    /** Swap the cut cactus for the dried one, and back again later. */
    private void dryOut(final GameObject cut) {
        /* Unregister BEFORE constructing the replacement: GameObject's
         * constructor already claims the tile, so building the dried cactus
         * first put it in the slot -- and the unregister of the cut one then
         * wiped it back out (see ActiveTile.remove), which is why cut cacti
         * used to vanish outright instead of standing dry. Every other
         * quest's swap already runs in this order. */
        world.unregisterGameObject(cut);
        final GameObject dried = new GameObject(cut.getLocation(), DRIED_CACTUS,
                cut.getDirection(), cut.getType());
        world.registerGameObject(dried);
        world.getDelayedEventHandler().add(new SingleEvent(null, CACTUS_REGROW_MS) {
            public void action() {
                world.unregisterGameObject(dried);
                world.registerGameObject(new GameObject(dried.getLocation(), CACTUS,
                        dried.getDirection(), dried.getType()));
            }
        });
    }

    /** Which vessel of water this is, or -1. */
    private int vesselIndex(int id) {
        for (int i = 0; i < WATER_VESSELS.length; i++) {
            if (WATER_VESSELS[i] == id) {
                return i;
            }
        }
        return -1;
    }

    /** True for any skin that has room -- everything on the chain but full. */
    private boolean refillable(int id) {
        for (int i = 1; i < SKIN_CHAIN.length; i++) {
            if (SKIN_CHAIN[i] == id) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- Irena --

    private void irena(final Npc npc) {
        Player p = getOwner();
        if (completed()) {
            Conversation c = new Conversation(p, npc);
            c.message("Irena seems happy now that her daughter has returned home.");
            c.npc("Thanks so much for returning my daughter to me.");
            c.npc("I expect that she will go on another trip soon though.");
            c.npc("She is the adventurous type...a bit like yourself really!");
            c.npc("Ok, see you around then!");
            c.start();
            return;
        }
        if (holds(ANA_BARREL) && questStarted()) {
            returnAna(npc);
            return;
        }
        if (at(ESCAPED)) {
            /* Ana is home but the rewards were interrupted. chooseReward's
             * own comment promises exactly this resume -- "a player who logs
             * out between the two must come back to Irena offering 'one of
             * the following areas'" -- but no branch ever offered it: without
             * the barrel in the pack she fell through to 'bring my daughter
             * back', with the daughter already in the house. */
            Conversation c = new Conversation(p, npc);
            c.npc("Thank you again for returning my daughter to me!");
            chooseReward(c);
            c.start();
            return;
        }
        if (questStarted()) {
            Conversation c = new Conversation(p, npc);
            c.npc("Please bring my daughter back to me.");
            c.npc("She is most likely lost in the Desert somewhere.");
            c.npc("I miss her so much....");
            c.npc("*Sob*");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.message("Irena seems to be very upset and cries as you start to approach her.");
        c.npc("Boo hoo, oh dear, my only daughter....");
        c.player("What's the matter?");
        c.npc("Oh dear...my daughter, Ana, has gone missing in the desert.");
        c.npc("I fear that she is lost, or perhaps...*sob* even worse.");
        c.options(new Choice("When did she go into the desert?",
                             "What did she go into the desert for?",
                             "Is there a reward if I get her back?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("*Sob*");
                    c.npc("She went in there just a few days ago,");
                    c.npc("She said she would be back yesterday.");
                    c.npc("And she's not...");
                } else if (option == 1) {
                    c.npc("She was just travelling, a tourist you might say.");
                    c.npc("*Sob* She said she would be safe and now she could be..");
                    c.message("Irena's bottom lip trembles a little.");
                    c.npc("*Whhhhhaaaaa*");
                    c.message("Irena cries her heart out in front of you.");
                } else {
                    c.npc("Well, yes, you'll have my gratitude young man.");
                    c.npc("And I'm sure that Ana will also be very pleased!");
                    c.npc("And I may see if I can get a small reward together...");
                    c.npc("But I cannot promise anything.");
                    c.npc("So does that mean that you'll look for her then?");
                }
                askToHelp(c);
            }
        });
        c.start();
    }

    private void askToHelp(Conversation c) {
        c.options(new Choice("I'll look for your daughter.", "No, sorry, I'm just too busy!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Oh really, can't I persuade you in anyway?");
                    return;
                }
                c.npc("That would be very good of you.");
                c.npc("You would have the gratitude of a very loving mother.");
                c.npc("That's really very nice of you!");
                c.npc("She was wearing a red silk scarf when she left.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (!questStarted()) {
                            step(STARTED);
                        }
                    }
                });
            }
        });
    }

    private void returnAna(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Hey, great you've found Ana!");
        c.message("You show Irena the barrel with Ana in it.");
        c.message("@gre@Ana: Hey great, there's my Mum!");
        c.message("@gre@Ana: Great! Thanks for getting me out of that mine!");
        c.message("@gre@Ana: And that barrel wasn't too bad anyway!");
        c.message("@gre@Ana: Pop by again sometime, I'm sure we'll have a barrel of laughs!");
        c.message("@gre@Ana: Oh! I nearly forgot, here's a key I found in the tunnels.");
        c.message("@gre@Ana: It might be of some use to you, not sure what it opens.");
        c.message("@gre@Ana: Hi Mum!");
        c.message("@gre@Ana: Sorry, I have to go now!");
        c.npc("Thank you very much for returning my daughter to me.");
        c.npc("I'm really very grateful...");
        c.npc("I would like to reward you for your bravery and daring.");
        c.then(new Effect() {
            public void run(Conversation c) {
                take(ANA_BARREL);
                if (!holds(WROUGHT_KEY)) {
                    give(WROUGHT_KEY);
                }
                step(ESCAPED);
            }
        });
        chooseReward(c);
        c.start();
    }

    /**
     * The reward is chosen twice and the same skill may be picked both times.
     * The first choice is remembered in a bit rather than kept on the stack,
     * because a player who logs out between the two must come back to Irena
     * offering "one of the following areas" rather than starting over.
     */
    private void chooseReward(Conversation c) {
        c.npc(marked(REWARD1)
            ? "I can offer you increased knowledge in one of the following areas."
            : "I can offer you increased knowledge in two of the following areas.");
        c.options(new Choice("Fletching.", "Agility.", "Smithing.", "Thieving") {
            public void picked(int option, Conversation c) {
                int skill = option == 0 ? FLETCHING
                          : option == 1 ? AGILITY
                          : option == 2 ? SMITHING : THIEVING;
                String name = option == 0 ? "Fletching"
                            : option == 1 ? "Agility"
                            : option == 2 ? "Smithing" : "Thieving";
                award(skill, name);
                if (marked(REWARD1)) {
                    setStage(FINISHED);
                    return;
                }
                mark(REWARD1);
                c.message("Ok, now choose your second skill.");
                chooseReward(c);
            }
        });
    }

    private void award(int skill, String name) {
        Player p = getOwner();
        say("You advance your stat in " + name + ".");
        p.incExp(skill, (p.getMaxStat(skill) + 1) * 150, false);
        p.getActionSender().sendStat(skill);
    }

    // ------------------------------------------------- getting in the camp --

    private void watchCaptain() {
        say("You watch the Mercenary Captain for a while...");
        if (past(CAPTAIN_DEAD)) {
            say("He has rather lost his swagger since you beat him.");
            return;
        }
        say("Whenever there is trouble he calls his guards over...");
        say("...and stands well back while they deal with it.");
        say("It seems the captain never fights his own battles.");
        mark(WATCHED);
    }

    private void captain(final Npc npc) {
        Player p = getOwner();
        if (past(CAPTAIN_DEAD)) {
            new Conversation(p, npc)
                .npc("Move along now...we've had enough of your sort!")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        // Recorded on approach, and we printed nothing.
        c.message("You approach the Mercenary Captain.");
        c.options(new Choice("Hello.", "You there!", "Hey ugly!") {
            public void picked(int option, Conversation c) {
                if (option == 2) {
                    heyUgly(c);
                    return;
                }
                if (option == 1) {
                    /*
                     * "You there!" opens its OWN tree. It used to print these
                     * four lines and then fall through into "Be off Effendi"
                     * and the "Hello." option pair, which is not what vanilla
                     * does -- the two openers share nothing. The fall-through
                     * both handed the player the wrong menu and made the whole
                     * I'm-lost / what-are-you-guarding sub-tree, 27 recorded
                     * lines, unreachable.
                     */
                    c.npc("How dare you talk to me like that!");
                    c.npc("Explain your business quickly...");
                    c.npc("or my guards will slay you where you stand.");
                    c.message("Some guards close in around you.");
                    youThere(c);
                    return;
                }
                c.npc("Be off Effendi, you are not wanted around here.");
                // The space before the apostrophe is the page's, and it is
                // {{sic}}-marked there, so it is attested game text.
                c.options(new Choice("That's rude, I ought to teach you some manners.",
                                     "I 'll offer you something in return for your time.") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            thatsRude(c);
                            return;
                        }
                        c.npc("Hmmm, oh yes, what might that be?");
                        offerCaptain(c);
                    }
                });
            }
        });
        c.start();
    }

    /**
     * A guard's line, not the captain's.
     *
     * Several of the beats below are attributed "Mercenary:" on the transcript
     * rather than "Mercenary Captain:" -- it is a guard who leans in and
     * whispers, precisely so the captain will not hear him. Delivering those
     * with c.npc() put them in the captain's mouth, which turns the joke inside
     * out: he ends up standing over you saying the thing being said behind his
     * back. The rest of this file already uses this prefix idiom.
     */
    private void mercenary(Conversation c, String line) {
        c.message("@yel@Mercenary: " + line);
    }

    /**
     * "Hey ugly!"
     *
     * This branch used to end in jail(null) -- searched, roughed up and
     * teleported into the cell. Vanilla does not jail anybody here. The
     * transcript ends the scene after eight more lines of guard dialogue and no
     * relocation at all: the guards make a show of it and tell you to clear
     * off. That is the same joke as the "That's rude" branch, where the guard
     * whispers instead of fighting, and the invented jail was flattening it.
     *
     * jail() only teleports -- it sets no stage and grants nothing -- so
     * dropping it here changes where the player ends up and nothing else.
     *
     * The lines say "again", "What are you doing here again?", "Didn't I tell
     * you to get out of here!", which implies a first-visit variant the wiki
     * never captured. The recorded path is built as the only path rather than
     * inventing the other half of it.
     */
    private void heyUgly(Conversation c) {
        c.npc("I will not tolerate such insults..");
        c.npc("Guards, kill him.");
        c.message("The captain marches away in disgust leaving his guards to tackle you.");
        c.message("The guard approaches you again kicks you slightly.");
        c.player("Ow!");
        mercenary(c, "Take that you mad child of a dog!");
        c.message("The guard leans closer to you and says in a low voice.");
        mercenary(c, "What are you doing here again?");
        mercenary(c, "Didn't I tell you to get out of here!");
        mercenary(c, "Now get lost, properly this time!");
        mercenary(c, "Or we may be forced to see his orders through properly.");
    }

    /** "You there!" -- its own tree, not the "Hello." one. */
    private void youThere(Conversation c) {
        c.picker(new Choice("I'm lost, can you help me?", "What are you guarding?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    whatAreYouGuarding(c);
                    return;
                }
                c.player("I'm lost, can you help me?");
                c.message("The captain smiles broadly and with a sickening voice says.");
                c.npc("We are not a charity effendi,");
                c.npc("Be off with you before I have your head removed from your body.");
                // Falls to a second pair rather than ending.
                c.picker(new Choice("What are you guarding?", "You don't scare me!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) { whatAreYouGuarding(c); } else { youDontScareMe(c); }
                    }
                });
            }
        });
    }

    private void whatAreYouGuarding(Conversation c) {
        c.player("What are you guarding?");
        c.npc("Effendi...");
        c.npc("For just one second, imagine that it's none of your business!");
        /*
         * The page writes "oneat a time" and does NOT mark it {{sic}}, while
         * marking four other typos on the same page. That inconsistency reads
         * as a wiki slip rather than a Jagex one, so it ships corrected.
         *
         * Noted rather than done silently because it is the opposite of the
         * call made for Brother Jered, where a misspelling was kept. The
         * difference is evidence: Jered's was confirmed against config46.jag,
         * and there is nothing to confirm this against.
         */
        c.npc("Also imagine having your limbs pulled from your body one at a time.");
        c.npc("Now, what was the question again?");
        c.picker(new Choice("Do you have sand in your ears, I said, 'What are you guarding?'",
                            "You don't scare me!") {
            public void picked(int option, Conversation c) {
                if (option == 0) { sandInYourEars(c); } else { youDontScareMe(c); }
            }
        });
    }

    /**
     * Both of the captain's dump endings go through intoTheDesert(), which
     * already teleports to the stranding point and already takes the player's
     * water. No new state and no new constants for either.
     *
     * Its cart wording differs from the captain's page -- "bundled into the
     * back of a cart and blindfolded" against "grabed and manhandled onto a
     * cart" -- and both are attested, on different pages. That is a real
     * variant rather than a paraphrase to correct, so the existing pair stands
     * and this does not fork the helper to say it twice.
     */
    private void sandInYourEars(Conversation c) {
        c.player("Do you have sand in your ears, I said, 'What are you guarding?'");
        c.npc("Why....you ignorant, rude and eternally damned infidel,");
        c.message("The captain seems very agitated with what you just said.");
        c.npc("Guards, kill this infidel!");
        guardsRoughYouUp(c);
    }

    private void youDontScareMe(Conversation c) {
        c.player("You don't scare me!");
        c.npc("Well, perhaps I can try a little harder.");
        c.npc("Guards, kill this infidel.");
        guardsRoughYouUp(c);
    }

    /**
     * The shared tail of both dump endings.
     *
     * "The guards grab you and rough you up a bit." is attributed to a
     * Mercenary in one copy on the page and marked as a message in the other.
     * They cannot both be right, and the message is: it is third-person
     * narration about the guards, so a guard cannot be its speaker. roughedUp()
     * elsewhere in this file already treats its own variant that way.
     *
     * The page also writes the guard's first line as "Mercenary: Guard: Ok,
     * that does it!" and marks it {{Sic}}. That is a doubled speaker prefix in
     * the transcription rather than game text.
     */
    private void guardsRoughYouUp(Conversation c) {
        c.message("An angry guard approaches you and whips out his sword.");
        mercenary(c, "Ok, that does it!");
        mercenary(c, "You're in serious trouble now!");
        mercenary(c, "Ok men, we need to teach this person a thing or two");
        mercenary(c, "about desert survival techniques.");
        c.message("The guards grab you and rough you up a bit.");
        intoTheDesert(c);
    }

    /** "That's rude, I ought to teach you some manners." */
    private void thatsRude(Conversation c) {
        c.npc("Oh yes! How might you do that?");
        c.npc("You seem little more than a gutter dweller.");
        c.npc("How could you teach me manners?");
        c.picker(new Choice("With my right fist and a good deal of force.",
                            "Err, sorry, I thought I was talking to someone else.") {
            public void picked(int option, Conversation c) {
                if (option == 0) { rightFist(c); } else { errSorry(c); }
            }
        });
    }

    private void rightFist(Conversation c) {
        // The option label and the spoken line genuinely disagree in vanilla --
        // "right fist" on the menu, "good right arm" out loud. Both kept.
        c.player("With my good right arm and a good deal of force.");
        c.npc("Oh yes, ready your weapon then!");
        c.npc("I'm sure you won't mind if my men join in?");
        c.npc("Har, har, har!");
        c.npc("Guards, kill this gutter dwelling slime.");
        c.message("A guard approaches you and looks very angry, he slaps you across the face.");
        mercenary(c, "Prepare to die effendi!");
        c.message("The guard leans close and whispers");
        // These two were shipped merged into one line. Vanilla has them apart.
        mercenary(c, "Are you mad effendi!");
        mercenary(c, "This is your last chance.");
        mercenary(c, "Leave now and never come back.");
        mercenary(c, "Or I'll introduce you to my friend.");
        c.message("The guard half draws his fearsome looking scimitar.");
        mercenary(c, "And we'll be pleased to clean the mess up after you've been dispatched.");
    }

    /**
     * "Err, sorry, I thought I was talking to someone else."
     *
     * Built verbatim, including an attribution that is probably wrong.
     *
     * The page gives all thirteen lines to the Mercenary Captain, but the
     * content is plainly the guard's: he pretends to hit you, then says in a
     * low voice that the captain is decrepit and does not notice. The captain
     * cannot be saying that about himself, and the identical beat in the
     * neighbouring branch is labelled Mercenary.
     *
     * It is still built as recorded. "The game shipped it wrong and we match
     * it" is exactly the Brother Jered case, and reattributing thirteen lines
     * on a reading of the content -- however obvious the reading -- is
     * overruling the only source we have. A replay settles it; a hunch does
     * not. If one ever does, these become mercenary() calls.
     */
    private void errSorry(Conversation c) {
        c.player("Err, sorry, I thought I was talking to someone else.");
        c.npc("Well, Effendi, you do need to be carefull of what you say to people.");
        c.npc("Or they may take it the wrong way.");
        c.npc("Thankfully, I'm very understanding.");
        c.npc("I'll just let me guards deal with you.");
        c.npc("Guards, teach this desert weed some manners.");
        c.message("A guard approaches you and pretends to start hiting you.");
        c.npc("Take that you infidel!");
        c.message("The guard leans closer to you and says in a low voice.");
        c.npc("We're sick of having to kill every lunatic that comes along");
        c.npc("and insults the captain, it makes such a mess.");
        c.npc("Thankfully, he's a bit decrepid so he doesn't notice");
        c.npc("so please, buzz off and don't come here again.");
    }

    private void offerCaptain(Conversation c) {
        c.picker(new Choice("I have some gold.", "There must be something I can do for you?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    // Label and spoken line differ by one word -- the same
                    // quirk as "right fist"/"good right arm" above.
                    c.player("There must be something that I can do for you?");
                    theHeadOfAlZabaBhasim(c);
                    return;
                }
                c.player("I have some gold.");
                c.npc("Ha, ha, ha! You come to a mining camp and offer us gold!");
                c.npc("Thanks effendi, but we have all the gold that we'll ever need.");
                c.npc("Now be off with you,");
                c.npc("before we reduce you to a bloody mess on the sand.");
                /*
                 * The gold refusal ENDS here in vanilla and offers a pair. It
                 * used to fall straight through into the "Captain ponders"
                 * beat, so the quest's central offer arrived unprompted, as if
                 * the captain had answered a question nobody asked -- and the
                 * second option, which is the player's way to push back, did
                 * not exist at all.
                 */
                c.picker(new Choice("There must be something I can do for you?",
                                    "You don't scare me!") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            youDontScareMe(c);
                            return;
                        }
                        c.player("There must be something that I can do for you?");
                        theHeadOfAlZabaBhasim(c);
                    }
                });
            }
        });
    }

    private void theHeadOfAlZabaBhasim(Conversation c) {
        c.message("The Captain ponders a moment and then looks at you critically.");
        c.npc("You could bring me the head of Al Zaba Bhasim.");
        c.npc("He is the leader of the notorius desert bandits, they plague us daily.");
        c.npc("You should find them west of here.");
        c.npc("You should have no problem in finishing them all off.");
        c.npc("Do this for me and maybe I will consider helping you.");
        c.options(new Choice("Consider it done.", "I don't think I can do that.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Good...run along then.");
                    c.npc("You stand around flapping your tongue chatting like an insane camel.");
                    return;
                }
                c.npc("Hmm, well yes, I did consider that you might not be right for the job.");
                c.npc("Be off with you then before I turn my men loose on you.");
                taunt(c);
            }
        });
    }

    /**
     * The taunt is only offered to a player who has used the captain's "watch"
     * command, because watching him is how you learn he has never fought
     * anybody himself. That command reaches this quest through the NPC_COMMAND
     * trigger, which was added for it -- npc 669 is the only npc in Classic
     * that carries "watch".
     */
    private void taunt(Conversation c) {
        if (!marked(WATCHED)) {
            c.message("You have nothing more to say to him.");
            return;
        }
        // picker() rather than options(): the second label and the line the
        // player actually speaks differ in vanilla, the same way they do in
        // the "right fist" and "something I can do for you" branches.
        c.picker(new Choice("I guess you can't fight your own battles then?",
                            "Ok, I'll move on.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("Ok, I'll be moving along then.");
                    c.npc("Effendi, I think you'll find that is the");
                    c.npc("wisest decision you have made today.");
                    return;
                }
                c.player("I guess you can't fight your own battles then?");
                c.message("The men around you fall silent and the Captain silently fumes.");
                c.message("All eyes turn to the Captain...");
                c.npc("Very well, if you're challenging me, let's get on with it!");
                c.message("The guards gather around to watch the fight.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Npc captain = c.getNpc();
                        c.stop();
                        if (captain != null) {
                            captain.attackPlayer(c.getPlayer());
                        }
                    }
                });
            }
        });
    }

    private void captainDies() {
        say("You kill the captain!");
        say("The mercenary captain drops a metal key on the floor.");
        say("You quickly grab the key and add it to your inventory.");
        if (!holds(METAL_KEY)) {
            give(METAL_KEY);
        }
        if (at(STARTED)) {
            step(CAPTAIN_DEAD);
        }
    }

    private void ironGate(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("A heavy iron gate, locked and guarded.");
            return;
        }
        if (!holds(METAL_KEY)) {
            say("The gate is locked.");
            say("@yel@Mercenary: Only camp staff through here, effendi.");
            return;
        }
        boolean outside = p.getX() >= GATE_X;
        if (outside && !disguised() && armed()) {
            jail("@yel@Mercenary: An armed stranger! Guards!");
            return;
        }
        say("You unlock the gate with the metal key and slip through.");
        if (outside) {
            p.teleport(INSIDE_GATE_X, INSIDE_GATE_Y, false);
        } else {
            p.teleport(OUTSIDE_GATE_X, OUTSIDE_GATE_Y, false);
        }
    }

    /**
     * Walking into the camp carrying weapons or armour is what gets a player
     * thrown into the cell. Only what is actually worn counts, which is what
     * the walkthrough means by "unequip your weapon and all your armour".
     */
    private boolean armed() {
        Player p = getOwner();
        for (int i = 0; i < p.getInventory().size(); i++) {
            InvItem item = p.getInventory().get(i);
            if (item == null || !item.isWielded()) {
                continue;
            }
            int id = item.getID();
            if (id == SLAVE_TOP || id == SLAVE_BOTTOM || id == DESERT_SHIRT
                    || id == DESERT_ROBE || id == DESERT_BOOTS) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void disturbedSand(QuestTrigger trigger) {
        if (trigger == QuestTrigger.OBJECT_ACT1) {
            say("Someone has passed this way recently.");
            return;
        }
        say("You search the disturbed sand.");
        say("Cart tracks lead away to the south.");
        if (questStarted()) {
            say("Whoever took Ana came this way.");
        }
    }

    private void jailDoor(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger == QuestTrigger.DOOR_ACT2 || trigger == QuestTrigger.OBJECT_ACT2) {
            say("A heavy cell door.");
            return;
        }
        if (!holds(CELL_KEY)) {
            say("The cell door is locked.");
            return;
        }
        say("You unlock the cell door.");
        boolean inCell = p.getX() >= CELL_MIN_X;
        p.teleport(inCell ? OUT_OF_CELL_X : CELL_MIN_X, inCell ? OUT_OF_CELL_Y : 801, false);
    }

    /**
     * The mine jail's gate. It has no key -- the way out is fifteen loads of
     * rocks handed to the guard, which is jailMercenary()'s business -- so from
     * either side this only ever says so.
     */
    private void mineJailDoor(QuestTrigger trigger) {
        if (trigger == QuestTrigger.DOOR_ACT2 || trigger == QuestTrigger.OBJECT_ACT2) {
            say("A heavy barred gate.");
            return;
        }
        if (!inMineJail()) {
            say("@yel@Mercenary: Hey, move away from that gate!");
            return;
        }
        say("@yel@Mercenary: Hey, move away from the gate.");
        say("@yel@Mercenary: If you wanna get out, you're gonna have to mine for it.");
    }

    private void cellWindow(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.OBJECT_ACT1) {
            say("A small barred window.");
            return;
        }
        say("You search the window.");
        say("The bars are loose enough to squeeze through.");
        if (p.getX() <= WINDOW_IN_X) {
            say("You climb out of the window onto the rocks behind the jail.");
            p.teleport(LEDGE_X, LEDGE_Y, false);
        } else {
            say("You squeeze back in through the window.");
            p.teleport(WINDOW_IN_X, WINDOW_IN_Y, false);
        }
    }

    private void desk(QuestTrigger trigger) {
        if (trigger == QuestTrigger.OBJECT_ACT1) {
            say("A large desk covered in paperwork.");
            return;
        }
        say("You search the desk.");
        if (holds(CELL_KEY)) {
            say("You find nothing else of interest.");
            return;
        }
        say("You find a small key and pocket it.");
        give(CELL_KEY);
    }

    // ------------------------------------------------------ the mine slave --

    private void miningSlave(final Npc npc) {
        Player p = getOwner();
        if (past(ROBES) && !holds(SLAVE_TOP) && !wearing(SLAVE_TOP)) {
            lostRobes(npc);
            return;
        }
        if (past(ROBES)) {
            new Conversation(p, npc)
                .npc("Not much to do here but mine all day long.")
                .start();
            return;
        }
        if (marked(CUFFS_OFF)) {
            tradeClothes(npc);
            return;
        }
        // He remembers agreeing the trade. Without this the whole "you look
        // like a new recruit" introduction replayed on every visit, which is
        // what the SLAVE_DEAL stage was being set for and then never read.
        if (past(SLAVE_DEAL)) {
            Conversation again = new Conversation(p, npc);
            again.npc("Hello again, are you ready to unlock my chains?");
            again.options(new Choice("Yeah, Ok, let's give it a go.",
                                     "I need to do some other things first.") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("Ok, fair enough, let me know when you want to give it another go.");
                        return;
                    }
                    c.npc("Great!");
                    pickLock(c, 1);
                }
            });
            again.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("You look like a new 'recruit'.");
        c.npc("How long have you been here?");
        c.options(new Choice("I've just arrived.", "Oh, I've been here ages.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Yeah, it looks like it as well.");
                    slavePlan(c);
                    return;
                }
                c.npc("That's funny, I haven't seen you around here before.");
                c.npc("You're clothes look too clean for you to have been here ages.");
                c.options(new Choice("Ok, you caught me out.",
                                     "The guards allow me to clean my clothes.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("Oh, a special relationship with the guards heh?");
                            c.npc("How very nice of them.");
                            c.npc("Maybe you could persuade them to let me out of here?");
                            c.message("The slave swaggers of with a sarcastic smirk on his face.");
                            return;
                        }
                        c.npc("Ah ha! I knew it! A new recruit then?");
                        slavePlan(c);
                    }
                });
            }
        });
        c.start();
    }

    /** His escape plan, which both openings arrive at. */
    private void slavePlan(Conversation c) {
        c.npc("It's a shame that I won't be around long enough to get to know you.");
        c.npc("I'm making a break for it today.");
        c.npc("I have a plan to get out of here!");
        c.npc("It's amazing in it's sophistication.");
        c.options(new Choice("What are those big wooden doors in the corner of the compound?",
                             "Oh yes, that sounds interesting.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("They lead to an underground mine,");
                    c.npc("but you really don't want to go down there.");
                    c.npc("I've only seen slaves and guards go down there,");
                    c.npc("I never see the slaves come back up.");
                    c.npc("At least up here you have a nice view and a bit of sun.");
                    c.message("The slave smiles at you happily and then goes back to his work.");
                    return;
                }
                c.npc("Yes, it is actually.");
                c.npc("I have all the details figured out except for one.");
                c.options(new Choice("What's that then?", "Oh, that's a shame.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.player("Still, 'worse things happen at sea right?'");
                            c.npc("You've obviously never worked as a slave");
                            c.npc("...in a mining camp...");
                            c.npc("...in the middle of the desert");
                            c.player("Well I suppose I'd better be getting on my way now...");
                            c.message("The slave nods in agreement and goes back to work.");
                            return;
                        }
                        c.message("The slave shakes his arms and the chains rattle loudly.");
                        c.npc("These bracelets, I can't seem to get them off.");
                        c.npc("If I could get them off, I'd be able to climb my way");
                        c.npc("out of here.");
                        c.options(new Choice("I can try to undo them for you.",
                                             "That's ridiculous, you're talking rubbish.") {
                            public void picked(int option, Conversation c) {
                                if (option == 0) {
                                    slaveBargain(c);
                                    return;
                                }
                                c.npc("No, it's true, I can make a break for it");
                                c.npc("If I can just get these bracelets off.");
                                c.options(new Choice("Good luck!",
                                                     "I can try to undo them for you.") {
                                    public void picked(int option, Conversation c) {
                                        if (option == 0) {
                                            c.npc("Thanks...same to you.");
                                            return;
                                        }
                                        slaveBargain(c);
                                    }
                                });
                            }
                        });
                    }
                    // Jagex trails the option off with an ellipsis when it is
                    // spoken but not in the menu label.
                }.says(1, "Oh, that's a shame..."));
            }
        });
    }

    /**
     * He works out that the offer has a price attached, and the player either
     * names it or backs off.
     */
    private void slaveBargain(Conversation c) {
        c.npc("Really, that would be great...");
        c.message("The slave looks at you strangely.");
        c.npc("Hang on a minute...I suppose you want something for doing this?");
        c.npc("The last time I did a trade in this place,");
        c.npc("I nearly lost the shirt from my back!");
        c.options(new Choice("It's funny you should say that...", "That sounds awful.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Yeah, bunch of no hopers, tried to rob me blind.");
                    c.npc("But I guess that's what you get when you deal with convicts.");
                    return;
                }
                c.message("The slave looks at you blankly.");
                c.npc("Yeah, go on!");
                strikeDeal(c);
            }
        }.says(0, "It's funny you should say that actually."));
    }

    private void strikeDeal(Conversation c) {
        c.player("If I can get the chains off, you have to give me something, ok?");
        c.npc("Sure, what do you want?");
        c.player("I want your clothes!");
        c.player("I can dress like a slave and gain access to the mine area to scout it out.");
        c.npc("Blimey! You're either incredibly brave or incredibly stupid.");
        c.npc("But what would I wear if you take my clothes?");
        c.npc("Get me some nice desert clothes and I'll think about it?");
        c.npc("Do you still want to try and undo the locks for me?");
        c.then(new Effect() {
            public void run(Conversation c) {
                if (at(CAPTAIN_DEAD)) {
                    step(SLAVE_DEAL);
                }
            }
        });
        c.options(new Choice("Yeah, Ok, let's give it a go.", "I need to do some other things first.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Ok, fair enough, let me know when you want to give it another go.");
                    return;
                }
                c.npc("Great!");
                pickLock(c, 1);
            }
        });
    }

    /**
     * Picking the handcuffs. Two failures in a row and a guard notices, which
     * is the one place in the quest where failing twice is worse than failing
     * once, so the attempt number is carried rather than rolled fresh.
     *
     * The second attempt is offered, not taken: Jagex asks "would you like
     * another go?" and means it, and since the answer to that question is what
     * decides whether the player ends up in a cell, taking it for them is not
     * a shortcut but a different outcome. Neither option is echoed as speech -
     * the transcript records a server message after each, and no player line.
     */
    private void pickLock(Conversation c, final int attempt) {
        c.message("You use some nearby bits of wood and wire to try and pick the lock.");
        c.then(new Effect() {
            public void run(Conversation c) {
                if (Math.random() < 0.5D) {
                    c.message("You hear a satisfying 'click' as you tumble the lock mechanism.");
                    c.npc("Great! You did it!");
                    c.npc("I need a desert shirt, robe and boots if you want these clothes off me.");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            mark(CUFFS_OFF);
                        }
                    });
                    return;
                }
                c.message("You fail!");
                if (attempt >= 2) {
                    c.message("A nearby guard spots you!");
                    c.npc("Oh oh!");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            c.stop();
                            jail("@yel@Mercenary: Oi, what are you two doing?");
                        }
                    });
                    return;
                }
                c.message("You didn't manage to pick the lock this time, would you like another go?");
                c.picker(new Choice("Yeah, I'll give it another go.",
                                    "I'll try something different instead.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.message("You decide to try something else.");
                            c.npc("Are you givin in already?");
                            c.player("I just want to try something else.");
                            c.npc("Ok, if you want to try again, let me know.");
                            return;
                        }
                        pickLock(c, attempt + 1);
                    }
                });
            }
        });
    }

    private void tradeClothes(final Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        c.npc("Do you have the Desert Clothes yet?");
        if (!hasDesertClothes()) {
            c.npc("I need a desert shirt, robe and boots if you want these clothes off me.");
            c.start();
            return;
        }
        c.npc("Great! You have the Desert Clothes!");
        c.message("The slave starts getting undressed right in front of you.");
        c.npc("Ok, here's the clothes, I won't need them anymore.");
        c.message("The slave gives you his dirty, flea infested robe.");
        c.message("The slave gives you his muddy, sweat soaked shirt.");
        c.message("@yel@Escaping Mining Slave: Right, I'm off! Good luck!");
        c.player("Yeah, good luck to you too!");
        c.then(new Effect() {
            public void run(Conversation c) {
                take(DESERT_SHIRT);
                take(DESERT_ROBE);
                take(DESERT_BOOTS);
                give(SLAVE_TOP);
                give(SLAVE_BOTTOM);
                step(ROBES);
            }
        });
        c.start();
    }

    private void lostRobes(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Oh bother, I was caught by the guards again...");
        c.npc("Listen, if you can get me some Desert Clothes,");
        c.npc("I'll trade you for my slaves clothes again..");
        c.npc("Do you want to trade?");
        c.options(new Choice("Yes, I'll trade.", "No thanks...") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Ok, fair enough, let me know if you change your mind though.");
                    return;
                }
                if (!hasDesertClothes()) {
                    c.npc("I need a desert shirt, robe and boots if you want these clothes off me.");
                    return;
                }
                c.npc("Great! You have the Desert Clothes!");
                c.message("The slave gives you his dirty, flea infested robe.");
                c.message("The slave gives you his muddy, sweat soaked shirt.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        take(DESERT_SHIRT);
                        take(DESERT_ROBE);
                        take(DESERT_BOOTS);
                        give(SLAVE_TOP);
                        give(SLAVE_BOTTOM);
                    }
                });
            }
        });
        c.start();
    }

    // ------------------------------------------------------- the mine gate --

    private void woodenDoors(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("You watch the doors for a while.");
            say("Slaves and guards go down. Only guards come back up.");
            return;
        }
        if (!disguised()) {
            say("@yel@Mercenary: Oi! Only slaves go down there, and you're no slave.");
            return;
        }
        boolean above = p.getY() < 2000;
        say("You heave the wooden doors open and climb through.");
        if (above) {
            p.teleport(MINE_X, MINE_Y, false);
        } else {
            p.teleport(SURFACE_X, SURFACE_Y, false);
        }
    }

    /**
     * npc 670, the guard outside the Mining Cave, who will not let anyone past
     * him and can be bought with a pineapple.
     *
     * His opening menu was missing before this: the conversation started at
     * "And what do you think you're doing?", which is ours, and jumped straight
     * to the second menu. It now opens where Transcript:Mercenary opens it.
     *
     * The option that ends the conversation politely is recorded twice as "Yes
     * sire, we understand each other perfectly." and once, as the player's
     * echoed line, as "Yes sir". Those are the same string in the client, so
     * one of the two is a transcription slip; the doubled reading is kept.
     */
    private void caveMercenary(final Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("That pineapple was just delicious, many thanks.")
                .start();
            return;
        }
        if (past(DEEP_MINE)) {
            new Conversation(p, npc)
                .npc("That pineapple was just delicious, many thanks.")
                .npc("I don't suppose you could get me another?")
                .message("The guard looks at you pleadingly.")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("Yeah, what do you want?");
        c.options(new Choice("I'd like to mine in a different area.", "Er nothing really.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Ok...so move along and get on with your work.");
                    return;
                }
                c.npc("Oh, so you want to work in another area of the mine heh?");
                c.message("The guard seems quite pleased with his rhetorical question.");
                c.npc("Well, I can understand that, a change is as good as a rest they say.");
                hintAtPineapple(c);
            }
        });
        c.start();
    }

    private void hintAtPineapple(Conversation c) {
        c.options(new Choice("Yes sir, you're quite right sir.",
                             "Huh, fat chance of a rest for me.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("You miserable whelp!");
                    c.npc("Get back to work!");
                    c.message("The guard cuffs you around head.");
                    return;
                }
                c.npc("Of course I'm right...");
                c.npc("And what goes around comes around as they say.");
                c.npc("And it's been absolutely ages since I've had anything different to eat.");
                c.npc("What I wouldn't give for some ripe and juicy pineapple for a change.");
                c.npc("And those Tenti's have the best pineapple in this entire area.");
                c.message("The guard winks at you.");
                c.npc("I'm sure you get my meaning...");
                c.options(new Choice("How am I going to get some pineapples around here?",
                                     "What are the 'Tenti's'?",
                                     "Yes sire, we understand each other perfectly.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("Well, you really don't come from around here do you?");
                            c.npc("The tenti's are what we call the nomadic people west of here");
                            c.npc("They live in tents, so we call them the tenti's");
                            c.npc("They have great pineapples!");
                            c.npc("I'm sure you get my meaning...");
                        }
                        if (option != 2) {
                            c.npc("Well, that's not my problem is it?");
                            c.npc("Also, I know that you slaves trade your items down here.");
                            c.npc("I'm sure that if you're resourceful enough, you'll come up with the goods.");
                            c.npc("Now, get along and do some work, before we're both in for it.");
                        } else {
                            c.npc("Ok, good then.");
                            c.message("The guard moves back to his post and winks at you knowingly.");
                        }
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                if (at(ROBES)) {
                                    step(PINEAPPLE_ASKED);
                                }
                            }
                        });
                    }
                }.says(2, "Yes sir, we understand each other perfectly."));
            }
        });
    }

    private void miningCave(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("A dark opening leading deeper into the mine.");
            return;
        }
        if (!past(DEEP_MINE)) {
            say("@yel@Mercenary: Hey! Nobody goes down there without permission.");
            return;
        }
        if (holds(ANA_BARREL)) {
            say("@yel@Mercenary: Hey, where d'ya think you're going with that Barrel?");
            say("A guard comes over and takes the barrel off you.");
            say("@yel@Mercenary: 'Cor! This barrel is really heavy!");
            say("@yel@Mercenary: Have you been mining lead?");
            say("@yel@Mercenary: Har, har har!");
            say("@gre@Ana: How rude! Why I ought to teach you a lesson.");
            say("@yel@Mercenary: What was that!");
            say("The guards kick the barrel open.!");
            say("@gre@Ana: How dare you say that I'm as heavy as lead?");
            say("The guards drag Ana of and then throw you into a cell.");
            say("Guards: Into the cell you go!");
            say("I hope this teaches you a lesson.");
            take(ANA_BARREL);
            step(DEEP_MINE);
            p.teleport(CELL_X, CELL_Y, false);
            return;
        }
        boolean deep = p.getY() >= DEEP_Y;
        p.teleport(deep ? SHALLOW_X : DEEP_X, deep ? SHALLOW_Y : DEEP_Y, false);
    }

    // ------------------------------------------------------ the Bedabin ----

    private void alShabim(final Npc npc) {
        Player p = getOwner();
        if (past(DART_MADE) && holds(PROTO_DART)) {
            handOverDart(npc);
            return;
        }
        if (past(HAVE_PINEAPPLE) && !holds(PINEAPPLE) && !past(DEEP_MINE)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Hello Effendi!");
            c.npc("Many thanks with your help previously Effendi!");
            c.player("I am looking for a pineapple.");
            c.npc("Here is another pineapple, try not to lose this one.");
            c.then(new Effect() {
                public void run(Conversation c) {
                    give(PINEAPPLE);
                }
            });
            c.start();
            return;
        }
        if (past(HAVE_PINEAPPLE)) {
            new Conversation(p, npc)
                .npc("Hello Effendi!")
                .npc("Many thanks with your help previously Effendi!")
                .start();
            return;
        }
        if (holds(PLANS) && past(SHABIM_DEAL)) {
            makeWeaponDeal(npc);
            return;
        }
        if (past(SHABIM_DEAL)) {
            Conversation c = new Conversation(p, npc);
            c.npc("Hello Effendi!");
            c.npc("How are things going Effendi?");
            c.options(new Choice("Very well thanks!", "Not so good actually!", "I've lost the key!") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("I really need those plans!");
                        return;
                    }
                    if (option == 1) {
                        c.npc("Bring me the plans from Captain Siad's office...they're in a chest.");
                        return;
                    }
                    if (holds(COPY_KEY)) {
                        c.npc("You have not lost it at all, Effendi!");
                        return;
                    }
                    c.npc("How very careless of you!");
                    c.npc("Here is another key, don't lose it this time!");
                    c.message("Al Shabim gives you another key.");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            give(COPY_KEY);
                        }
                    });
                }
            });
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("Hello Effendi!");
        c.npc("I am Al Shabim, greetings on behalf of the Bedabin nomads.");
        if (!past(PINEAPPLE_ASKED)) {
            c.options(new Choice("What is this place?", "Goodbye!") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("Very well, good day Effendi!");
                        return;
                    }
                    describeCamp(c);
                }
            });
            c.start();
            return;
        }
        c.options(new Choice("I am looking for a pineapple.", "What is this place?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    describeCamp(c);
                    return;
                }
                c.npc("Oh yes, well that is interesting.");
                c.npc("Our sweet pineapples are renowned throughout the whole of Kharid!");
                c.npc("And I'll give you one if you do me a favour?");
                c.player("Yes?");
                c.npc("Captain Siad at the mining camp is holding some secret information.");
                c.npc("It is very important to us and we would like you to get it for us.");
                c.npc("It gives details of an interesting, yet ancient weapon.");
                c.npc("All you have to do is gain access to his private room upstairs.");
                c.npc("We have a key for the chest that contains this information.");
                c.npc("Are you interested in our deal?");
                c.options(new Choice("Yes, I'm interested.", "Not at the moment.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("Very well Effendi!");
                            return;
                        }
                        c.npc("That's great Effendi!");
                        c.npc("Here is a copy of the key that should give you access to the chest.");
                        /* The key changes hands on the line that says so, not
                         * after the speech: a conversation that dies in its
                         * last lines -- a walk-off, a server restart -- must
                         * not have played the promise and kept the key. */
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                if (!holds(COPY_KEY)) {
                                    give(COPY_KEY);
                                }
                                if (at(PINEAPPLE_ASKED)) {
                                    step(SHABIM_DEAL);
                                }
                            }
                        });
                        c.npc("Bring us back the plans inside the chest, they should be sealed.");
                        c.npc("All haste to you Effendi!");
                    }
                });
            }
        });
        c.start();
    }

    private void describeCamp(Conversation c) {
        c.npc("This is the home of the Bedabin,");
        c.npc("We're a peaceful tribe of desert dwellers.");
        c.npc("Some idiots call us 'Tenti's', a childish name borne of ignorance.");
        c.npc("We're renowned for surviving in the harshest desert climate.");
        c.npc("We also grow the 'Bedabin ambrosia.'...");
        c.npc("A pineapple of such delicious sumptiousness that it defies description.");
        c.npc("Take a look around our camp if you like!");
    }

    private void makeWeaponDeal(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Hello Effendi!");
        c.npc("Aha! I see you have the plans.");
        c.npc("This is great!");
        c.npc("However, these plans do indeed look very technical");
        c.npc("My people have further need of your skills.");
        c.npc("If you can help us to manufacture this item,");
        c.npc("we will share it's secret with you.");
        c.npc("Does this deal interest you effendi?");
        c.options(new Choice("Yes, I'm very interested.", "No, sorry.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("As you wish effendi!");
                    c.npc("Come back if you change your mind!");
                    return;
                }
                if (!holds(BRONZE_BAR) || getOwner().getInventory().countId(FEATHER) < 10) {
                    c.npc("Great, we need the following items.");
                    c.npc("A bar of pure bronze and 10 feathers.");
                    c.npc("Bring them to me and we'll continue to make the item.");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            if (at(HAVE_PLANS) || at(SHABIM_DEAL)) {
                                step(HAVE_PLANS);
                            }
                        }
                    });
                    return;
                }
                c.npc("Aha! I see you have the items we need!");
                c.npc("Ok Effendi, you need to follow the plans.");
                c.npc("You will need some special tools for this...");
                c.npc("There is a forge in the other tent.");
                c.npc("You have my permision to use it, but show the plans to the guard.");
                c.npc("Please bring me the item when it is finished.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        // The dispatch above now requires past(SHABIM_DEAL) to
                        // even reach this method, but the check is repeated
                        // here too: a bronze bar and ten feathers are ordinary
                        // tradeable items, and this is the line that actually
                        // unlocks the forge.
                        if (past(SHABIM_DEAL)) {
                            step(FORGE_OK);
                        }
                    }
                });
            }
        });
        c.start();
    }

    private void handOverDart(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Hello Effendi!");
        c.npc("Wonderful, I see you have made the new weapon!");
        c.message("You show Al Shabim the prototype dart.");
        c.npc("This is truly fantastic Effendi!");
        if (holds(PLANS)) {
            c.npc("We will take the technical plans for the weapon as well.");
            c.message("You hand over the technical plans for the weapon.");
        }
        c.npc("We are forever grateful for this gift.");
        c.npc("My advisors have discovered some secrets which we will share with you.");
        c.message("Al Shabim's advisors show you some advanced techniques for making the new weapon.");
        c.npc("Oh, and here is your pineapple!");
        c.npc("Please accept this selection of six bronze throwing darts");
        c.npc("as a token of our appreciation.");
        c.then(new Effect() {
            public void run(Conversation c) {
                take(PROTO_DART);
                if (holds(PLANS)) {
                    take(PLANS);
                }
                if (holds(COPY_KEY)) {
                    say("@yel@Al Shabim: I'll take that key off your hands as well effendi!");
                    say("@yel@Al Shabim: Many thanks!");
                    take(COPY_KEY);
                }
                give(PINEAPPLE);
                give(BRONZE_DART, 6);
                mark(DARTS_LEARNT);
                say("@gre@You can now make a new weapon type: Throwing dart.");
                step(HAVE_PINEAPPLE);
            }
        });
        c.start();
    }

    private void nomadGuard(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (past(HAVE_PINEAPPLE)) {
            c.npc("Sorry, but you can't use the tent without permission.");
            c.npc("But thanks for your help to the Bedabin people.");
            c.start();
            return;
        }
        if (past(FORGE_OK)) {
            c.npc("Ok, you can go in, Al Shabim has told me about you.");
            c.start();
            return;
        }
        c.npc("Sorry, this is a private tent, no one is allowed in.");
        c.npc("Orders of Al Shabim...");
        c.start();
    }

    // ------------------------------------------------------ Captain Siad ---

    /*
     * npc 702, and the second-largest conversation in the quest. Like the
     * mercenary, he has his own wiki page and none of it was in the per-quest
     * dump, so the first version of this method was six branches of guesswork
     * where the real thing is a five-way tree with four ways into a cell.
     *
     * The one thing here that is not simply transcribed is which of the two
     * "Fire!Fire!" outcomes plays; see fireFire().
     */
    private void captainSiad(final Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        c.message("The captain looks up from his work as you address him.");
        if (completed()) {
            c.npc("I don't have time to talk to you.")
             .npc("Move along please!")
             .start();
            return;
        }
        c.npc("What are you doing in here?");
        c.options(new Choice("I wanted to have a chat?",
                             "What's it got to do with you?",
                             "Prepare to die!",
                             "All the slaves have broken free!",
                             "Fire!Fire!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: wantedAChat(c); break;
                    case 1: whatsItToYou(c); break;
                    case 2: prepareToDie(c); break;
                    case 3: slavesBrokeFree(c); break;
                    default: fireFire(c); break;
                }
            }
        });
        c.start();
    }

    private void wantedAChat(Conversation c) {
        c.npc("You don't belong in here, get out!");
        c.options(new Choice("But I just need two minutes of your time?",
                             "Prepare to die!",
                             "All the slaves have broken free!",
                             "Fire!Fire!",
                             "You seem to have a lot of books!") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: twoMinutes(c); break;
                    case 1: prepareToDie(c); break;
                    case 2: slavesBrokeFree(c); break;
                    case 3: fireFire(c); break;
                    default: lotsOfBooks(c); break;
                }
            }
        });
    }

    private void twoMinutes(Conversation c) {
        c.npc("Well, ok, but very quickly.");
        c.npc("I am a very busy person you know!");
        c.options(new Choice("Well, er...erm, I err...",
                             "Oh my, a dragon just flew straight past your window!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    theDragon(c);
                    return;
                }
                spitItOut(c);
            }
        });
    }

    private void spitItOut(Conversation c) {
        c.npc("Come on, spit it out!");
        c.npc("Right that's it!");
        c.npc("Guards!");
        officeJail(c, false, false);
    }

    private void theDragon(Conversation c) {
        c.npc("Really! Where?");
        c.npc("I don't see any dragons young man?");
        c.npc("Now, please get out of my office, I have work to do.");
        c.message("The Captain goes back to his work.");
    }

    private void lotsOfBooks(Conversation c) {
        c.npc("Yes, I do. Now please get to the point?");
        /* The sailing question is only there once the bookcase has been read;
         * there is no other way to know what he collects. */
        String[] options = marked(BOOKS_SEEN)
            ? new String[] { "How long have you been interested in books?",
                             "I could get you some books!",
                             "So you're interested in sailing?" }
            : new String[] { "How long have you been interested in books?",
                             "I could get you some books!" };
        c.options(new Choice(options) {
            public void picked(int option, Conversation c) {
                String chosen = getOptions()[option];
                if (chosen.startsWith("So you're")) {
                    aboutSailing(c);
                    return;
                }
                if (chosen.startsWith("I could get")) {
                    c.npc("Oh, really!");
                    c.npc("Sorry, not interested!");
                    c.npc("GUARDS!");
                    officeJail(c, true, false);
                    return;
                }
                c.npc("Long enough to know when someone is stalling!");
                c.npc("Ok, that's it, get out!");
                c.npc("Guards!");
                officeJail(c, false, false);
            }
        });
    }

    /**
     * The only way to get him away from his desk that does not end in a cell.
     * The transcript says the option reads "So you're interested in sailing?"
     * but that the player says "So, you're interested in sailing?"; options()
     * echoes the option text, so the comma is lost. That is the one word of
     * this conversation this server cannot reproduce.
     */
    private void aboutSailing(Conversation c) {
        c.message("The captain's interest seems to perk up.");
        c.npc("Well, yes actually...");
        c.npc("It's been a passion of mine for some years...");
        c.options(new Choice("I could tell by the cut of your jib.",
                             "Not much sailing to be done around here though?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("The captain frowns slightly...");
                    c.npc("Well of course there isn't, we're surrounded by desert.");
                    c.npc("Now, why are you here exactly?");
                    c.options(new Choice("Oh my, a dragon just flew straight past your window!",
                                         "Well, er...erm, I err...") {
                        public void picked(int option, Conversation c) {
                            if (option == 0) {
                                theDragon(c);
                                return;
                            }
                            spitItOut(c);
                        }
                    });
                    return;
                }
                c.npc("Oh yes? Really?");
                c.message("The Captain looks flattered.");
                c.npc("Well, you know, I was quite the catch in my day you know!");
                c.message("The captain starts rambling on about his days as a salty sea dog.");
                c.message("He looks quite distracted...");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        mark(SIAD_BUSY);
                    }
                });
            }
        });
    }

    private void whatsItToYou(Conversation c) {
        c.npc("This happens to be my office.");
        c.npc("Now explain yourself before I run you through!");
        c.options(new Choice("The guard downstairs said you were lonely.",
                             "I need to service your chest.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    serviceTheChest(c);
                    return;
                }
                c.message("The captain gives you a puzzled look.");
                c.npc("Well, I most certainly am not lonely!");
                c.npc("I'm an incredibly busy man you know!");
                c.npc("Now, get to the point, what do you want?");
                c.options(new Choice("Well, er...erm, I err...",
                                     "I need to service your chest.") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            spitItOut(c);
                            return;
                        }
                        serviceTheChest(c);
                    }
                });
            }
        });
    }

    private void serviceTheChest(Conversation c) {
        c.npc("You need to what?");
        c.player("I need to service your chest?");
        c.npc("There's nothing wrong with the chest, it's fine, now get out!");
        c.options(new Choice("I'm here to take your plans, hand them over now or I'll kill you!",
                             "Fire!Fire!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    fireFire(c);
                    return;
                }
                c.npc("Don't be silly!");
                c.npc("I'm going to teach you a lesson!");
                c.npc("Guards! Guards!");
                officeJail(c, false, true);
            }
        });
    }

    private void prepareToDie(Conversation c) {
        c.npc("I'll teach you a lesson!");
        c.npc("Guards! Guards!");
        officeJail(c, false, true);
    }

    private void slavesBrokeFree(Conversation c) {
        c.npc("Don't talk rubbish, the warning siren isn't sounding.");
        c.npc("Now state your business before I have you thrown out.");
        c.options(new Choice("The guard downstairs said you were lonely.",
                             "I need to service your chest.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    serviceTheChest(c);
                    return;
                }
                c.message("The captain gives you a puzzled look.");
                c.npc("Well, I most certainly am not lonely!");
                c.npc("I'm an incredibly busy man you know!");
                c.npc("Now, get to the point, what do you want?");
                c.options(new Choice("Well, er...erm, I err...",
                                     "I need to service your chest.") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            spitItOut(c);
                            return;
                        }
                        serviceTheChest(c);
                    }
                });
            }
        });
    }

    /**
     * The fire lie, which is the other way to get him away from his desk.
     *
     * The transcript records two outcomes and is explicit that it does not know
     * which condition picks between them -- its own footnote says the previous
     * transcript was unclear about it. The reading taken here is that the lie
     * works the first time and only the first time: he gets up and looks out of
     * the window, and if you tell him again while he is already at it he
     * answers "Where's the fire? I don't see any fire?", which is what a man
     * says after he has looked and found nothing. The sub-branch under it --
     * "Oh yes, you're right, they must have put it out!" -- only makes sense
     * as a reply to a man who has already checked, which is what decided it.
     */
    private void fireFire(Conversation c) {
        if (!marked(SIAD_BUSY)) {
            c.message("The captain seems distracted with what you just said.");
            c.message("The captain looks out of the window to see if is a fire.");
            c.then(new Effect() {
                public void run(Conversation c) {
                    mark(SIAD_BUSY);
                }
            });
            return;
        }
        c.npc("Where's the fire?");
        c.npc("I don't see any fire?");
        c.options(new Choice("It's down in the lower mines, sound the alarm!",
                             "Oh yes,  you're right, they must have put it out!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Good, now perhaps you can leave me in peace?");
                    c.npc("After all I do have some work to do.");
                    c.options(new Choice("Er, yes Ok then.",
                                         "Well, er...erm, I err...") {
                        public void picked(int option, Conversation c) {
                            if (option == 1) {
                                spitItOut(c);
                                return;
                            }
                            c.npc("Good!");
                            c.npc("Please remove yourself from my office.");
                            c.message("The Captain goes back to his desk and starts studying.");
                        }
                    });
                    return;
                }
                soundTheAlarm(c);
            }
        });
    }

    private void soundTheAlarm(Conversation c) {
        c.npc("You go and sound the alarm, I can't see anything wrong with the mine.");
        c.npc("Have you seen the fire yourself?");
        c.options(new Choice("Yes actually!", "Er, no, one of the slaves told me.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Well...you can't believe them, they're all a bunch of convicts.");
                    c.npc("Anyway, it doesn't look as if there is a fire down there.");
                    c.npc("So I'm going to get on with my work.");
                    c.npc("Please remove yourself from my office.");
                    c.message("The Captain goes back to his desk and starts studying.");
                    return;
                }
                c.npc("Well, why didn't you raise the alarm?");
                c.options(new Choice("I don't know where the alarm is.",
                                     "I was so concerned for your safety that I rushed to save you.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.npc("Well, that's very good of you.");
                            c.npc("But as you can see, I am very fine and well thanks!");
                            c.npc("Now, please leave so that I can get back to my work.");
                            c.message("The Captain goes back to his desk.");
                            return;
                        }
                        c.npc("That's the most ridiculous thing I've heard.");
                        c.npc("Who are you? Where do you come from?");
                        c.npc("It doesn't matter...");
                        c.message("The Captain shouts the guards...");
                        c.npc("Guards!");
                        c.npc("Show this person out!");
                        officeJail(c, true, false);
                    }
                });
            }
        });
    }

    /**
     * The four ways this conversation ends in a cell. They differ only in
     * whether the guards find the gate key on you and whether the transcript
     * prints "You are under attack!" -- and as everywhere else in this quest,
     * that line prints and nothing actually attacks; see mercenary().
     *
     * Losing the main gate key is real, and it is not a softlock: the metal key
     * comes off the mercenary captain, who respawns, so another can always be
     * taken off him -- see captainDies().
     */
    private void officeJail(Conversation c, final boolean takeKey,
                            boolean attacked) {
        c.message("The Guards search you!");
        if (takeKey) {
            c.message("The guards find the main gate key and remove it!");
        }
        if (attacked) {
            c.message("You are under attack!");
        }
        c.message("Some guards rush to help the captain.");
        c.message("You are roughed up a bit by the guards as you're manhandlded into a cell.");
        c.message("@yel@Guards: Into the cell you go! I hope this teaches you a lesson.");
        c.then(new Effect() {
            public void run(Conversation c) {
                Player p = c.getPlayer();
                if (takeKey && p.getInventory().countId(METAL_KEY) > 0) {
                    p.getInventory().remove(METAL_KEY, 1);
                    p.getActionSender().sendInventory();
                }
                p.teleport(CELL_X, CELL_Y, false);
            }
        });
    }

    private void bookcase(QuestTrigger trigger) {
        if (trigger == QuestTrigger.OBJECT_ACT1) {
            say("The captain seems to collect lots of books!");
            return;
        }
        say("You notice several books on the subject of Sailing.");
        if (questStarted()) {
            mark(BOOKS_SEEN);
        }
    }

    private void captainsChest(QuestTrigger trigger, InvItem used) {
        boolean withKey = trigger == QuestTrigger.ITEM_ON_OBJECT
            && used != null && used.getID() == COPY_KEY;
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("A heavy chest with a good lock on it.");
            return;
        }
        if (!withKey && !holds(COPY_KEY)) {
            say("The chest is locked.");
            return;
        }
        if (!past(SHABIM_DEAL)) {
            // The copy key opens the lock, but a copy key is an ordinary
            // tradeable item -- Al Shabim's own deal, not the key, is what
            // actually proves this player has a reason to be going through
            // the captain's chest. Without this, PLANS could be looted by
            // anyone who ever got hold of a copy key, skipping the whole
            // chain that is supposed to lead here.
            say("You don't have any reason to go through the captain's things.");
            return;
        }
        if (holds(PLANS) || past(FORGE_OK)) {
            say("You open the chest, but there is nothing left inside.");
            return;
        }
        if (!marked(SIAD_BUSY)) {
            /* Transcript:Captain Siad, "Trying to open the Captains Chest
             * without distracting him". What was here before was four invented
             * lines saying the same thing at four times the length. */
            say("The captains spots you before you manage to open the chest...");
            say("@yel@Captain Siad: I don't have time to talk to you.");
            say("@yel@Captain Siad: Move along please!");
            return;
        }
        say("With the captain's back turned you unlock the chest.");
        say("Inside is a bundle of sealed plans. You take them.");
        give(PLANS);
        unmark(SIAD_BUSY);
        if (at(SHABIM_DEAL)) {
            step(HAVE_PLANS);
        }
    }

    // ------------------------------------------------------ the dart forge --

    private void experimentalAnvil(QuestTrigger trigger, InvItem used) {
        Player p = getOwner();
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("A strange anvil, covered in unfamiliar tools.");
            return;
        }
        if (!past(FORGE_OK)) {
            say("@yel@Bedabin Nomad Guard: Sorry, this is a private tent, no one is allowed in.");
            return;
        }
        if (!holds(PLANS)) {
            say("You will need the plans in front of you to work from.");
            return;
        }
        if (!holds(BRONZE_BAR)) {
            say("You need a bronze bar to work with.");
            return;
        }
        if (p.getMaxStat(SMITHING) < 20) {
            say("You need a smithing level of 20 to make this");
            return;
        }
        say("You follow the plans and hammer the bronze bar into a strange thin point.");
        take(BRONZE_BAR);
        if (Math.random() < 0.25D) {
            say("The metal splits under the hammer and is ruined.");
            return;
        }
        say("You have made a prototype dart tip.");
        give(DART_TIP);
    }

    /**
     * Ten feathers onto the tip finishes the dart. The finished dart is a
     * throwing weapon, so the fletching level Jagex put on the quest is checked
     * here rather than at the anvil.
     */
    private void pair(int a, int b) {
        Player p = getOwner();
        int vessel = vesselIndex(a) >= 0 ? a : (vesselIndex(b) >= 0 ? b : -1);
        int skin = refillable(a) ? a : (refillable(b) ? b : -1);
        if (vessel != -1 && skin != -1 && vessel != skin) {
            /* One vessel is one dose: the skin holds four drinks and a
             * vessel holds one consumption's worth everywhere else in the
             * game, so pouring it in moves the skin a single step up the
             * chain and empties the vessel. */
            take(vessel);
            give(EMPTY_VESSELS[vesselIndex(vessel)]);
            for (int i = 1; i < SKIN_CHAIN.length; i++) {
                if (skin == SKIN_CHAIN[i]) {
                    take(skin);
                    give(SKIN_CHAIN[i - 1]);
                    break;
                }
            }
            say("You pour the water into the waterskin");
            return;
        }
        boolean tipAndFeather = (a == DART_TIP && b == FEATHER)
                             || (a == FEATHER && b == DART_TIP);
        if (!tipAndFeather) {
            say("Nothing interesting happens.");
            return;
        }
        if (p.getMaxStat(FLETCHING) < 10) {
            say("You need a fletching level of 10 to make this");
            return;
        }
        if (p.getInventory().countId(FEATHER) < 10) {
            say("You need 10 feathers to finish the dart.");
            return;
        }
        say("You attach ten feathers to the prototype dart tip.");
        p.getInventory().remove(FEATHER, 10);
        take(DART_TIP);
        give(PROTO_DART);
        p.getActionSender().sendInventory();
        say("You have made a prototype throwing dart.");
        if (at(FORGE_OK)) {
            step(DART_MADE);
        }
    }

    private void command(int id) {
        if (id == ANA_BARREL) {
            // "Look" on the barrel with Ana still inside it -- claimed via
            // associateItem() but had no case here, so it was silent.
            say("Ana looks pretty angry, she starts shouting at you");
            say("Get me out of here!");
            say("Do you hear me!");
            return;
        }
        if (id != PLANS) {
            return;
        }
        if (marked(DARTS_LEARNT)) {
            say("Some technical looking plans...");
            return;
        }
        say("The plans look very technical! But you can see that this item");
        say("will require a bronze bar and at least 10 feathers.");
    }

    // --------------------------------------------------------------- Ana ---

    private void ana(final Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        if (past(ANA_MET)) {
            c.npc("Have you thought of a way to get me out of here yet?");
            c.npc("The only thing that leaves this place is the rock we mine.");
            c.start();
            return;
        }
        c.npc("Hello there, I don't think I've seen you before.");
        c.options(new Choice("What's your name?", "No, I'm new here!") {
            public void picked(int option, Conversation c) {
                /* The stage steps the moment she is answered, not after her
                 * twenty timed lines: a player who walked off mid-speech was
                 * left holding a barrelable Ana at the wrong stage, and the
                 * lift guard's traded-barrel gate then refused their own
                 * honest work. */
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (at(DEEP_MINE)) {
                            step(ANA_MET);
                        }
                    }
                });
                if (option == 0) {
                    c.npc("My name? Oh, how sweet, my name is Ana,");
                    c.npc("I come from Al Kharid, thought the desert might be interesting.");
                    c.npc("What a surprise I got!");
                    c.player("Do you want to go back to Al Kharid?");
                    c.npc("Sure, I miss my Mum, her name is Irena and she is probably waiting for me.");
                    c.npc("how do you propose we get out of here though?");
                } else {
                    c.npc("I thought so you know!");
                    c.npc("How do you like the hospitality down here?");
                    c.npc("Not exactly Al Kharid Inn style is it?");
                }
                c.player("I want to try and get you out of here.");
                c.npc("Wow! You're brave. How do you propose we do that?");
                c.player("Have you got any suggestions?");
                c.npc("Hmmm, let me think...");
                c.npc("No, sorry...");
                c.npc("The only thing that gets out of here is the rock that we mine.");
                c.player("How does the rock get out?");
                c.npc("Well, in this section we mine it,");
                c.npc("Then someone else scoops it into a barrel.");
                c.npc("The barrels are loaded onto a mine cart.");
                c.npc("Then they're desposited near the surface lift.");
                c.npc("But that's not going to help us, is it?");
                c.player("Where would I get one of those barrels from?");
                c.npc("Well, you would get one from around by the lift area.");
                c.npc("But why would you want one of those?");
                c.npc("Hmmm, just don't get any funny ideas...");
                c.npc("I am not going to get into one of those barrels!");
            }
        });
        c.start();
    }

    private void itemOnNpc(Npc npc, InvItem used) {
        if (used == null) {
            return;
        }
        int id = used.getID();
        if (npc.getID() == ANA && id == BARREL) {
            barrelAna();
            return;
        }
        if (npc.getID() == MERC_CAVE && id == PINEAPPLE) {
            feedMercenary();
            return;
        }
        if (npc.getID() == MERC_CAVE && isPineapple(id)) {
            say("@yel@Mercenary: Oh great!");
            say("The guard rolls his eyes in glee.");
            say("and takes a bite of the pineapple");
            say("His face turns from pleasure to pain as he spits the mouthful of pineapple out.");
            say("@yel@Mercenary: Yeuch!");
            say("@yel@Mercenary: That's awful! That's not Tenti pineapple,");
            say("@yel@Mercenary: Get me some Tenti pineapple if you know what's good for you.");
            take(id);
            return;
        }
        if (npc.getID() == NOMAD_GUARD && id == PLANS) {
            say("@yel@Bedabin Nomad Guard: Hmm, those plans look interesting.");
            if (past(FORGE_OK)) {
                say("@yel@Bedabin Nomad Guard: Ok, you can go in, Al Shabim has told me about you.");
                return;
            }
            say("@yel@Bedabin Nomad Guard: Go and show them to Al Shabim...");
            say("@yel@Bedabin Nomad Guard: I'm sure he'll be pleased to see them.");
            return;
        }
        if (npc.getID() == SHABIM && id == PLANS) {
            say("Al Shabim takes the technical plans off you.");
            say("@yel@Al Shabim: Thanks for the technical plans Effendi!");
            say("@yel@Al Shabim: We've been lost without them!");
            take(PLANS);
            return;
        }
        say("Nothing interesting happens.");
    }

    private boolean isPineapple(int id) {
        return id == 748 || id == 749 || id == 861 || id == 862;
    }

    private void feedMercenary() {
        say("@yel@Mercenary: Great! Just what I've been looking for!");
        say("@yel@Mercenary: Mmmmmmm, delicious!!");
        say("@yel@Mercenary: Oh, this is soo nice!");
        say("@yel@Mercenary: Mmmmm, *SLURP*");
        say("@yel@Mercenary: Yummmm....Oh yes, this is great.");
        say("The guard waves you past and goes back to his pineapple.");
        take(PINEAPPLE);
        if (at(HAVE_PINEAPPLE)) {
            step(DEEP_MINE);
        }
    }

    private void barrelAna() {
        if (holds(ANA_BARREL)) {
            say("You already have Ana in a barrel, you can't get two in there!");
            return;
        }
        say("@gre@Ana: Hey, what do you think you're doing?");
        say("@gre@Ana: Harumph!");
        say("Shush...It's for your own good!");
        say("You manage to squeeze Ana into the barrel,");
        say("despite her many complaints.");
        take(BARREL);
        give(ANA_BARREL);
    }

    // ------------------------------------------- barrels, lift and cart ----

    private void barrels(QuestTrigger trigger) {
        if (trigger == QuestTrigger.OBJECT_ACT1) {
            say("A stack of barrels used for hauling ore.");
            return;
        }
        say("You search the barrels.");
        if (past(ANA_ON_LIFT) && !past(ANA_UP) && getOwner().getY() < 2000) {
            say("You find the barrel you sent up, with Ana still inside it.");
            give(ANA_BARREL);
            step(ANA_UP);
            return;
        }
        if (holds(BARREL) || holds(ANA_BARREL)) {
            say("You already have a barrel.");
            return;
        }
        say("You take one of the empty mining barrels.");
        give(BARREL);
    }

    private void mineCart(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger == QuestTrigger.OBJECT_ACT1) {
            say("A mine cart on a set of rails.");
            return;
        }
        if (!past(ANA_MET) && !past(DEEP_MINE)) {
            say("You have no reason to ride the cart.");
            return;
        }
        say("You climb onto the mine cart.");
        say("It rattles off along the rails and comes to a stop.");
        boolean nearA = p.getX() >= 60;
        p.teleport(nearA ? CART_B_X : CART_A_X, nearA ? CART_B_Y : CART_A_Y, false);
    }

    private void liftPlatform(QuestTrigger trigger, InvItem used) {
        boolean withAna = trigger == QuestTrigger.ITEM_ON_OBJECT
            && used != null && used.getID() == ANA_BARREL;
        if (trigger == QuestTrigger.OBJECT_ACT2 && !withAna) {
            say("A wooden platform on ropes, for hauling barrels to the surface.");
            return;
        }
        if (!withAna && !holds(ANA_BARREL)) {
            aboutTheLift();
            return;
        }
        if (past(ANA_ON_LIFT)) {
            say("The barrel is already on its way to the surface.");
            return;
        }
        gregarious();
    }

    /**
     * Touching the lift with no barrel gets the guard's attention rather than a
     * message about the lift, because he is standing over it. He opens with a
     * different line here than he does when he is spoken to -- "Hey there, what
     * do you want?" against "Yes, what do you want?" -- which is why the two
     * are separate methods.
     */
    private void aboutTheLift() {
        Conversation c = new Conversation(getOwner(), null);
        c.message("@yel@Mercenary: Hey there, what do you want?");
        c.options(new Choice("What is this thing?", "Can I use this?", "Ok, thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 2) {
                    return;
                }
                if (option == 0) {
                    c.message("@yel@Mercenary: It is quite clearly a lift.");
                    c.message("@yel@Mercenary: Any fool can see that it's used to transport rock to the surface.");
                }
                c.message("@yel@Mercenary: Of course not, you'd be doing me out of a job.");
                c.message("@yel@Mercenary: Anyway, you haven't got any barrels that need to go to the surface.");
                c.message("@yel@Mercenary: Now, move along and get some work done before you get a good beating.");
            }
        });
        c.start();
    }

    private void liftMercenary(final Npc npc) {
        if (holds(ANA_BARREL)) {
            gregarious();
            return;
        }
        if (completed()) {
            new Conversation(getOwner(), npc)
                .npc("Move along please, don't want any trouble today!")
                .start();
            return;
        }
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Yes, what do you want?");
        c.options(new Choice("Nothing thanks - sorry for disturbing you.",
                             "Your head on a stick.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Well...I guess that's Ok, get on your way though.");
                    return;
                }
                headOnAStick(c);
            }
        });
        c.start();
    }

    /**
     * Ana cannot keep quiet inside the barrel, and the guard hears her. The
     * only answer that works is to pass her insult off as a compliment.
     */
    private void gregarious() {
        Player p = getOwner();
        if (!past(ANA_MET)) {
            // An "Ana in a barrel" is a plain tradeable item like any other,
            // so holding one is not proof this player ever met her down in
            // the mine. Without this check, a barrel handed off by someone
            // else could be walked straight to the lift, skipping DEEP_MINE
            // and everything before it.
            say("Nothing interesting happens.");
            return;
        }
        Conversation c = new Conversation(p, null);
        c.message("The guard notices the barrel (with Ana in it) that you're carrying.");
        c.message("@yel@Mercenary: Hey, that Barrel looks heavy, do you need a hand?");
        c.options(new Choice("Yes please.", "No thanks, I can manage.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("@yel@Mercenary: Ok, fair enough, I was only offering.");
                    return;
                }
                c.message("The guard comes over and helps you. He takes one end of the barrel.");
                c.message("@yel@Mercenary: Blimey! This is heavy!");
                c.message("@gre@Ana in a barrel: Why you cheeky....!");
                c.message("The guard looks around suprised at Ana's outburst.");
                c.message("@yel@Mercenary: What was that?");
                c.player("Oh, it was nothing.");
                c.message("@yel@Mercenary: I could have sworn I heard something!");
                c.message("@gre@Ana in a barrel: Yes you did you ignaramus.");
                c.message("@yel@Mercenary: What was that you said?");
                c.options(new Choice("I said you were very gregarious!", "Oh, nothing.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.message("@yel@Mercenary: I heard you say something, now spit it out!");
                            return;
                        }
                        c.message("@gre@Ana in a barrel: You creep!");
                        c.message("@yel@Mercenary: Oh, right, how very nice of you to say so.");
                        c.message("The guard seems flattered.");
                        c.message("@yel@Mercenary: Anyway, let's get this barrel up to the surface, plenty more work to you to do!");
                        c.message("The guard places the barrel carefully on the lift platform.");
                        c.message("@yel@Mercenary: Oh, there's no one operating the lift up top, hope this barrel isn't urgent?");
                        c.message("@yel@Mercenary: You'd better get back to work!");
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                take(ANA_BARREL);
                                step(ANA_ON_LIFT);
                            }
                        });
                    }
                });
            }
        });
        c.start();
    }

    private void surfaceLift(QuestTrigger trigger) {
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("A rope lift running down into the mine.");
            return;
        }
        say("You operate the lift.");
        if (!past(ANA_ON_LIFT)) {
            say("The platform comes up empty.");
            return;
        }
        if (past(ANA_UP)) {
            say("The platform comes up empty.");
            return;
        }
        say("The platform rises with a single heavy barrel on it.");
        say("You heave the barrel off and set it down by the others.");
    }

    private void cartDriver(final Npc npc) {
        Player p = getOwner();
        if (past(CART_READY)) {
            new Conversation(p, npc)
                .npc("I'm not hanging around here, there's a riot coming!")
                .start();
            return;
        }
        if (completed()) {
            new Conversation(p, npc)
                .npc("Not much to do here but mine all day long.")
                .npc("Don't trouble me, can't you see I'm busy?")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.message("The cart driver seems to be festidiously cleaning his cart.");
        c.message("It doesn't look as if he wants to be disturbed.");
        c.options(new Choice("Hello.", "Nice cart.", "Pssst...") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    niceCart(c);
                    return;
                }
                if (option == 2) {
                    pssst(c, 1);
                    return;
                }
                c.npc("Can't you see I'm busy?");
                c.npc("Now get out of here!");
                c.options(new Choice("Oh, ok, sorry.", "Nice cart.", "Pssst....") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            niceCart(c);
                            return;
                        }
                        if (option == 2) {
                            pssst(c, 1);
                            return;
                        }
                        // He is quoted saying his own job title back at the
                        // player here and nowhere else; it reads like a stray
                        // speaker tag that shipped, so it is kept.
                        c.npc("Driver: Look just leave me alone!");
                        c.message("The cart driver goes back to his work.");
                    }
                });
            }
        }.says(0, "Hello"));
        c.start();
    }

    /**
     * Hissing at him. He ignores it however many times it is tried, and the
     * only way out of the loop is to shout, which is what actually gets his
     * attention.
     */
    private void pssst(Conversation c, final int level) {
        c.message(level == 1 ? "The cart driver completely ignores you."
                             : "The driver completely ignores you.");
        c.options(new Choice(level == 1 ? "Psssst..." : "Psssssst...",
                             "Pssssssssttt!!!") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    pssst(c, level + 1);
                    return;
                }
                c.message("The cart driver turns around quickly to face you.");
                c.npc("What!");
                c.npc("Can't you see I'm busy?");
                c.options(new Choice("Oh, ok, sorry.", "Shhshh!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("Look just leave me alone!");
                        } else {
                            c.npc("Shush yourself!");
                        }
                        c.message("The cart driver goes back to his work.");
                    }
                }.says(1, "Shhshhh!"));
            }
        });
    }

    /** Flattering the cart, which is the only thing he cares about. */
    private void niceCart(Conversation c) {
        c.message("The cart driver looks around at you and tries to weigh you up.");
        c.npc("Hmmm.");
        c.message("He tuts to himself and starts checking the wheels.");
        c.npc("Tut !");
        c.options(new Choice("I wonder if you could help me?",
                             "One wagon wheel says to the other, 'I'll see you around'.",
                             "Can I help you at all?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    wagonWheel(c);
                    return;
                }
                if (option == 2) {
                    canIHelp(c);
                    return;
                }
                c.npc("Sorry friend, I'm busy, go bug the guards,");
                c.npc("I'm sure they'll give ya the time of day.");
                c.message("The cart driver chuckles to himself.");
                c.options(new Choice("Can I help you at all?",
                                     "Can you get me the heck out of here please?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            canIHelp(c);
                            return;
                        }
                        c.npc("No way, and if you bug me again, I'm gonna call the guards.");
                    }
                });
            }
        }.says(1, "One wagon wheel says to the other,'I'll see you around'."));
    }

    private void canIHelp(Conversation c) {
        c.npc("I'm quite capable thanks...");
        c.npc("Now get lost before I call the guards.");
        c.options(new Choice("Can you get me the heck out of here please?",
                             "I could help, I know a lot about carts.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("No way, and if you bug me again, I'm gonna call the guards.");
                    return;
                }
                c.npc("Are you saying I don't know anything about carts?");
                c.npc("Why you cheeky little....");
                c.message("The cart driver seems mortally offended...");
                c.message("his temper explodes as he shouts the guards.");
                c.npc("Guards! Guards!");
                c.message("You quickly slope away and hide from the guards.");
            }
        });
    }

    /**
     * The joke that thaws him. Both halves have to land: the setup only opens
     * the follow-up option, and the punchline is what makes him listen.
     */
    private void wagonWheel(Conversation c) {
        c.message("The cart driver smirks a little.");
        c.message("He starts checking the steering on the cart.");
        c.options(new Choice("'One good turns deserves another'") {
            public void picked(int option, Conversation c) {
                c.message("The cart driver smiles a bit and then turns to you.");
                c.npc("Are you trying to get me fired?");
                c.options(new Choice("No", "Yes", "Fired...no, shot perhaps!") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("It certainly sounds like it, now leave me alone.");
                            c.npc("If you bug me again, I'm gonna call the guards.");
                            c.message("The cart driver goes back to his work.");
                            return;
                        }
                        if (option == 1) {
                            c.npc("And why would you want to do a crazy thing like that for?");
                            c.npc("I ought to teach you a lesson!");
                            c.npc("Guards! Guards!");
                            c.message("You quickly slope away and hide from the guards.");
                            return;
                        }
                        c.npc("Ha ha ha! You're funny!");
                        c.message("The cart driver checks that the guards aren't watching him.");
                        c.npc("What're you in fer?");
                        whatFor(c);
                    }
                });
            }
            // The apostrophe that should close the quote is a full stop in the
            // spoken line but not in the menu label.
        }.says(0, "'One good turn deserves another."));
    }

    private void whatFor(Conversation c) {
        c.options(new Choice("Oh, I'm not supposed to be here at all actually.",
                             "I'm in for murder, so you'd better get me out of here!",
                             "In for a penny in for a pound.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Hmmm, interesting...let me guess.");
                    c.npc("You're completely innocent...");
                    c.npc("like all the other inmates in here.");
                    c.npc("Ha ha ha!");
                    c.message("The Cart driver goes back to his work.");
                    return;
                }
                if (option == 1) {
                    c.npc("Hmm, well, I wonder what the guards are gonna say about that!");
                    c.npc("Guards! Guards!");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            if (Math.random() < 0.5D) {
                                c.message("You quickly slope away and hide from the guards.");
                                return;
                            }
                            c.message("Some guards notice you and come over.");
                            c.then(new Effect() {
                                public void run(Conversation c) {
                                    c.stop();
                                    jail("@yel@Mercenary: Oi, what are you two doing?");
                                }
                            });
                        }
                    });
                    return;
                }
                c.message("The cart driver laughs at your pun...");
                c.npc("Ha ha ha, oh Stoppit!");
                c.message("The cart driver seems much happier now.");
                c.npc("What can I do for you anyway?");
                askDriver(c);
            }
        });
    }

    private void waterSeller(final Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        c.player("Hello, effendi.");
        c.npc("Greetings, effendi. Care to buy some water?");
        c.options(new Choice("A jug of water, please (" + WATER_JUG_PRICE + "gp)",
                             "A bucket of water, please (" + WATER_BUCKET_PRICE + "gp)",
                             "A waterskin, please (" + WATER_BUCKET_PRICE + "gp)",
                             "No thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 3) {
                    c.npc("As you wish, effendi.");
                    return;
                }
                final int itemId, price;
                switch (option) {
                    case 0: itemId = WATER_JUG; price = WATER_JUG_PRICE; break;
                    case 1: itemId = WATER_BUCKET; price = WATER_BUCKET_PRICE; break;
                    default:
                        /*
                         * The real quirk: the waterskin's own menu line above
                         * quotes WATER_BUCKET_PRICE (20gp), same as the wiki's
                         * dialogue option text, but WATERSKIN_PRICE (25gp) is
                         * what actually gets charged. Kept as-is, not a bug.
                         */
                        itemId = WATERSKIN; price = WATERSKIN_PRICE; break;
                }
                Player pl = c.getPlayer();
                if (pl.getInventory().countId(COINS) < price) {
                    c.npc("Sorry effendi, you don't have enough coins.");
                    return;
                }
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player pl = c.getPlayer();
                        pl.getInventory().remove(COINS, price);
                        pl.getInventory().add(new InvItem(itemId, 1));
                        pl.getActionSender().sendInventory();
                    }
                });
                c.npc("There you go, effendi.");
            }
        });
        c.start();
    }

    private void askDriver(Conversation c) {
        c.options(new Choice("Well, you see, it's like this...",
                             "Can you smuggle me out on your cart?",
                             "Can you smuggle my friend Ana out on your cart?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.message("The cart driver points at a nearby guard.");
                    c.npc("Ask that man over there if it's OK and I'll consider it!");
                    c.npc("Ha ha ha!");
                    return;
                }
                if (option == 2) {
                    c.npc("As long as your friend is a barrel full of rocks.");
                    c.npc("I don't think it would be a problem at all!");
                    c.npc("Ha ha ha!");
                    return;
                }
                c.npc("yeah!");
                c.options(new Choice("Prison riot in ten minutes, get your cart out of here!",
                                     "There's ten gold in it for you if you leave now - no questions asked.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            bribeDriver(c);
                            return;
                        }
                        riotDriver(c);
                    }
                });
            }
        }.says(2, "Can you smuggle my friend out on your cart?"));
    }

    /**
     * Buying his cooperation. He names his own price, and calling it without
     * the coins is what gets the guards shouted for - the check is his, not a
     * courtesy, so there is no "you don't have enough" let-off here.
     */
    private void bribeDriver(Conversation c) {
        c.npc("If you're going to bribe me, at least make it worth my while.");
        c.npc("Now, let's say 100 Gold pieces should we?");
        c.npc("Ha ha ha!");
        c.options(new Choice("A hundred it is!", "Forget it!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("Ok, fair enough!");
                    c.npc("But don't bother me anymore.");
                    c.message("The cart driver goes back to work.");
                    return;
                }
                c.npc("Great!");
                if (c.getPlayer().getInventory().countId(COINS) < CART_BRIBE) {
                    c.npc("You little cheat, trying to trick me!");
                    c.npc("I'll show you!");
                    c.npc("Guards! Guards!");
                    c.message("You quickly slope away and hide from the guards.");
                    return;
                }
                c.npc("Ok, get in the back of the cart then!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player pl = c.getPlayer();
                        pl.getInventory().remove(COINS, CART_BRIBE);
                        pl.getActionSender().sendInventory();
                        if (past(ANA_UP)) {
                            step(CART_READY);
                        }
                    }
                });
            }
        }.says(0, "A hundred it is."));
    }

    /** Scaring him into leaving, with or without the player aboard. */
    private void riotDriver(Conversation c) {
        c.message("The cart driver seems visibly shaken...");
        c.npc("Oh, right..yes...yess, Ok...");
        c.message("The cart driver quickly starts preparing the cart.");
        c.options(new Choice("Good luck!", "You can't leave me here, I'll get killed!") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Yeah, you too!");
                    c.message("The cart sets off at a hectic pace.");
                    c.message("The guards at the gate get suspiscious and search the cart.");
                    c.message("They find Ana in the Barrel and take her back into the mine.");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            // Ana goes back to the mine and must be barrelled
                            // and lifted all over again. Both branches here
                            // only mean anything once Ana has genuinely been
                            // raised to the surface -- otherwise this whole
                            // exchange is just banter with the cart driver and
                            // moves nothing.
                            if (holds(ANA_BARREL)) {
                                take(ANA_BARREL);
                            }
                            if (past(ANA_UP)) {
                                step(DEEP_MINE);
                            }
                        }
                    });
                    return;
                }
                c.npc("Oh, right...ok, you'd better jump in the cart then!");
                c.npc("Quickly!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (past(ANA_UP)) {
                            step(CART_READY);
                        }
                    }
                });
            }
        });
    }

    private void escapeCart(QuestTrigger trigger, InvItem used) {
        Player p = getOwner();
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != ANA_BARREL) {
                say("Nothing interesting happens.");
                return;
            }
            say("You heave the barrel onto the cart, out of sight under the sacking.");
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT1) {
            say("A cart, loaded and ready to leave the camp.");
            return;
        }
        if (!past(CART_READY)) {
            say("The driver is in no hurry to go anywhere.");
            return;
        }
        if (!holds(ANA_BARREL)) {
            say("You are not leaving without Ana.");
            return;
        }
        say("You climb into the cart and pull the sacking over yourself.");
        say("The cart rumbles out through the camp gates.");
        say("The guards wave the driver through without a second look.");
        say("Some time later the cart stops at the Shantay Pass and you climb out.");
        p.teleport(FREEDOM_X, FREEDOM_Y, false);
    }

    // ------------------------------------------------------- the mercenary --

    /*
     * npc 668, the guard who stands around outside the camp, and the largest
     * single conversation in the quest. Everything below is copied from
     * Transcript:Mercenary; the first version of this method paraphrased it in
     * about a dozen lines and invented three of them before an audit
     * caught it.
     *
     * He has five states, all of them recorded: carrying Ana, after the quest,
     * after the captain's death with the bill unpaid, after the bill is paid,
     * and the bribe tree that everything else hangs off.
     *
     * TWO THINGS ARE OURS HERE, and neither is speech.
     *
     * The bet is only offered once the quest has started. A quest's whole
     * per-player storage is its stage word, and that word does not exist until
     * the quest begins -- an unstarted quest reads -1, which no bit can be
     * written into. Jagex could remember a bet from a player who had never
     * spoken to Irena; this server cannot, so rather than take money it would
     * lose, it does not offer the wager.
     *
     * "You are under attack!" is printed where the transcript prints it, but no
     * guard actually attacks and nothing is confiscated. That matches jail(),
     * which has always worked the same way, and is noted there.
     */
    private void mercenary(final Npc npc) {
        Player p = getOwner();
        if (holds(ANA_BARREL)) {
            new Conversation(p, npc).npc("Move along now...").start();
            return;
        }
        if (completed()) {
            new Conversation(p, npc).npc("What're you looking at?").start();
            return;
        }
        if (past(CAPTAIN_DEAD)) {
            if (marked(SETTLED)) {
                new Conversation(p, npc)
                    .npc("Move along now..we've had enough of your sort!")
                    .start();
            } else if (bet() > 0) {
                collectBet(npc);
            } else {
                cleanupBill(npc);
            }
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("Yeah, what do you want?");
        firstQuestion(c);
        c.start();
    }

    /**
     * The opening menu. The transcript gates the Ana question on having been
     * through the whole first branch, which is the transcriber describing when
     * it appeared rather than why; the reason it appears is that until Irena
     * asks you to look for her daughter you do not know there is an Ana. That
     * is what is checked here.
     *
     * The transcript records no way out of this menu, but a Classic option menu
     * cannot be dismissed, so there must have been one. The exit used is the
     * one Jagex wrote a level further down -- "Ok, thanks." answered with
     * "Yeah, whatever!" -- rather than a new line of ours.
     */
    private void firstQuestion(Conversation c) {
        String[] options = questStarted()
            ? new String[] { "What is this place?", "What are you guarding?",
                             "I'm looking for a woman called Ana, have you seen her?",
                             "Ok, thanks." }
            : new String[] { "What is this place?", "What are you guarding?",
                             "Ok, thanks." };
        c.options(new Choice(options) {
            public void picked(int option, Conversation c) {
                String chosen = getOptions()[option];
                if (chosen.startsWith("What is this place")) {
                    c.npc("It's none of your business now get lost.");
                    askBribe(c, "Perhaps five gold coins will make it my business?",
                             "It certainly will!", "The guard takes the five gold coins.");
                } else if (chosen.startsWith("What are you guarding")) {
                    c.npc("Get lost before I chop off your head!");
                    askBribe(c, "Perhaps these five gold coins will sweeten your mood?",
                             "Well, it certainly will help...",
                             "The guard takes the five gold coins.");
                } else if (chosen.startsWith("I'm looking")) {
                    c.npc("No, now get lost!");
                    // The one offer of the three whose menu label is not what
                    // the player says: the word "gold" is missing from the
                    // list and present out loud.
                    askBribe(c, "Perhaps five coins will help you remember?",
                             "Perhaps five gold coins will help you remember?",
                             "Hmm, it might help!", "The guards takes the five gold coins.");
                } else {
                    goodbye(c);
                }
            }
        });
    }

    /** "Yeah, whatever!" -- the one ending this whole tree has. */
    private void goodbye(Conversation c) {
        c.npc("Yeah, whatever!");
    }

    /**
     * Every one of his three opening answers is a brush-off that five gold
     * coins will fix, and each has its own way of taking the money.
     */
    private void askBribe(Conversation c, String offer, final String reply,
                          final String taken) {
        askBribe(c, offer, offer, reply, taken);
    }

    private void askBribe(Conversation c, String offer, String spoken,
                          final String reply, final String taken) {
        c.options(new Choice(offer, "Ok, thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    goodbye(c);
                    return;
                }
                if (coins() < 5) {
                    c.npc("Don't try to fool me, you don't have five gold coins!");
                    c.npc("Before you try to bribe someone, make sure you have the money effendi!");
                    c.npc("Guards, guards!");
                    c.message("You are under attack!");
                    c.message("Nearby guards quickly grab you and rough you up a bit.");
                    intoTheDesert(c);
                    return;
                }
                c.npc(reply);
                c.message(taken);
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player pl = c.getPlayer();
                        pl.getInventory().remove(COINS, 5);
                        pl.getActionSender().sendInventory();
                    }
                });
                c.npc("Now then, what did you want to know?");
                paidQuestion(c);
            }
        }.says(0, spoken));
    }

    /** The same three questions, asked of a guard who has been paid. */
    private void paidQuestion(Conversation c) {
        String[] options = questStarted()
            ? new String[] { "What is this place?", "What are you guarding?",
                             "I'm looking for a woman called Ana, have you seen her?",
                             "Ok, thanks." }
            : new String[] { "What is this place?", "What are you guarding?",
                             "Ok, thanks." };
        c.options(new Choice(options) {
            public void picked(int option, Conversation c) {
                String chosen = getOptions()[option];
                if (chosen.startsWith("What is this place")) {
                    thisPlace(c);
                } else if (chosen.startsWith("What are you guarding")) {
                    guarding(c);
                } else if (chosen.startsWith("I'm looking")) {
                    aboutAna(c);
                } else {
                    goodbye(c);
                }
            }
        });
    }

    private void thisPlace(Conversation c) {
        c.npc("It's just a mining camp. Prisoners are sent here from Al Kharid.");
        c.npc("They serve out their sentence by mining.");
        c.npc("Most prisoners will end their days here, surrounded by desert.");
        c.player("So you could almost say that they got their... 'just desserts'");
        c.npc("You could say that...");
        c.message("There is an awkward pause");
        c.npc("But it wouldn't be very funny.");
        c.message("There is another awkward pause.");
        c.player("When they talk about the silence of the desert,");
        c.player("this must be what they mean.");
        c.message("The guard starts losing interest in the conversation.");
        c.options(new Choice("Can I take a look around the place?", "Ok thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    goodbye(c);
                    return;
                }
                lookAround(c);
            }
        });
    }

    /**
     * The hint that the whole quest turns on. The captain has the only key, and
     * he will have you killed unless he has a use for you -- which is what
     * sends the player off after Al Zaba Bhasim, and, down the other fork, what
     * gets the guards talking about how little they think of him.
     */
    private void lookAround(Conversation c) {
        c.npc("Not really. The Captain won't let you in the compound.");
        c.npc("He's the only one who has the key to the gate.");
        c.npc("And if you talk to him, he'll probably just order us to kill you.");
        c.npc("Unless...");
        c.options(new Choice("Does the Captain order you to kill a lot of people?",
                             "Unless what?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    killsALot(c);
                    return;
                }
                c.npc("Unless he has a use for you.");
                c.npc("He's been trying to track down a someone called 'Al Zaba Bhasim'.");
                c.npc("You could offer to catch him and that might put you in his good books?");
                c.options(new Choice("Where would I find this Al Zaba Bhasim?", "Ok thanks.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            goodbye(c);
                            return;
                        }
                        whereBhasim(c);
                    }
                });
            }
        });
    }

    private void whereBhasim(Conversation c) {
        c.npc("Well, he could be anywhere, he's a nomadic desert dweller.");
        c.npc("However, he is frequently to be found to the west in the");
        c.npc("hospitality of the tenti's.");
        c.options(new Choice("The Tenti's, who are they?", "Ok thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    goodbye(c);
                    return;
                }
                c.npc("Well, we're not really sure what they're proper name is.");
                c.npc("But they live in tents so we call them the 'Tenti's'.");
                c.options(new Choice("Is Al Zaba Bhasim very tough?", "Ok thanks.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            goodbye(c);
                            return;
                        }
                        c.npc("Well, I'm not sure, but by all accounts, he is a slippery fellow.");
                        c.npc("The Captain has been trying to capture him for years.");
                        c.npc("A bit of a waste of time if you ask me.");
                        walksOff(c);
                    }
                });
            }
        });
    }

    private void guarding(Conversation c) {
        c.npc("Well, if you have to know, we're making sure that no prisoners get out.");
        c.message("The guard gives you a disaproving look.");
        c.npc("And to make sure that unauthorised people don't get in.");
        c.message("The guard looks around nervously.");
        c.npc("You'd better go now before the Captain orders us to kill you.");
        c.options(new Choice("Does the Captain order you to kill a lot of people?",
                             "Ok Thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    goodbye(c);
                    return;
                }
                killsALot(c);
            }
        });
    }

    private void aboutAna(Conversation c) {
        c.npc("Hmm, well, we get a lot of people in here.");
        c.npc("But not many women though...");
        c.npc("Saw one come in last week....");
        c.npc("But I don't know if it's the woman you're looking for?");
        c.options(new Choice("What is this place?", "What are you guarding?", "Ok, thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    thisPlace(c);
                } else if (option == 1) {
                    guarding(c);
                } else {
                    goodbye(c);
                }
            }
        });
    }

    private void killsALot(Conversation c) {
        c.message("The guard snorts.");
        c.npc("*Snort*");
        c.npc("Just about anyone who talks to him.");
        c.npc("Unless he has a use for you, he'll probably just order us to kill you.");
        c.npc("And it's such a horrible job cleaning up the mess afterwards.");
        c.options(new Choice("Not to mention the senseless waste of human life.",
                             "Ok thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    goodbye(c);
                    return;
                }
                c.npc("Heh?");
                c.message("The guard looks at you with a confused stare...");
                c.options(new Choice("It doesn't sound as if you respect your Captain much.",
                                     "Ok thanks.") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            goodbye(c);
                            return;
                        }
                        noRespect(c);
                    }
                }.says(0, "It doesn't sound is if you respect your Captain much."));
            }
        });
    }

    /**
     * His men do not rate him either, and they will take money on it. This is
     * the second half of what the "watch" command tells the player, in words
     * instead of in behaviour.
     */
    private void noRespect(Conversation c) {
        c.npc("Well, to be honest.");
        c.message("The guard looks around conspiratorially.");
        c.npc("We think he's not exactly as brave as he makes out.");
        c.npc("But we have to follow his orders.");
        c.npc("If someone called him a coward,");
        c.npc("or managed to trick him into a one-on-one duel.");
        c.npc("Many of us bet that he'll be slaughtered in double quick time.");
        c.npc("And all the men agreed that they wouldn't intervene.");
        if (!questStarted()) {
            goodbye(c);
            return;
        }
        c.options(new Choice("Can I have a bet on that?", "Ok Thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    goodbye(c);
                    return;
                }
                placeBet(c);
            }
        });
    }

    private void placeBet(Conversation c) {
        if (bet() > 0) {
            c.npc("Sorry, we've already taken your bet, wouldn't want any cheating now.");
            c.npc("Anyway, I have to get back to work. See ya around...");
            return;
        }
        c.npc("Well, if you think you stand a chance, sure.");
        c.npc("But remember, if he gives us an order, we have to obey.");
        c.options(new Choice("I'll bet 5 gold that I win.", "I'll bet 10 gold that I win.",
                             "I'll bet 15 gold that I win.", "I'll bet 20 gold that I win.",
                             "Ok, thanks.") {
            public void picked(int option, Conversation c) {
                if (option == 4) {
                    goodbye(c);
                    return;
                }
                final int which = option;
                if (coins() < STAKE[which]) {
                    walksOff(c);
                    return;
                }
                c.npc("Great, I'll take that bet.");
                c.message("You hand over " + STAKE[which] + " gold coins.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player pl = c.getPlayer();
                        pl.getInventory().remove(COINS, STAKE[which]);
                        pl.getActionSender().sendInventory();
                        setField(BET_MASK, BET_SHIFT, which + 1);
                    }
                });
                c.npc("Ok, if you win, you'll get " + PAYOUT[which] + " gold back.");
            }
        });
    }

    /** How he gets out of any conversation he has had enough of. */
    private void walksOff(Conversation c) {
        c.npc("Anyway, I have to get going, I do have work to do.");
        c.message("The guard walks off.");
    }

    /**
     * The guards honour the bet and then charge the winner the stake back for
     * cleaning up, so the whole wager is worth a gold piece or ten. That is the
     * joke, and it is why they walk off laughing.
     */
    private void collectBet(Npc npc) {
        final int which = bet() - 1;
        new Conversation(getOwner(), npc)
            .player("Hey, I've come to collect my bet!")
            .npc("Well, I guess congratulations are in order.")
            .player("Thanks!")
            .npc("And we'll only charge the paltry sum of..erm...")
            .message("The guards starts to do some mental calculations...")
            .message("You can see his brow furrow and he starts to sweat profusely")
            .npc((which == 0 ? "Five" : String.valueOf(STAKE[which]))
                 + " gold for cleaning up the mess.")
            .npc("You have won " + WINNINGS[which]
                 + (WINNINGS[which] == 1 ? " Gold piece!" : " Gold pieces!"))
            .npc("Well done..!")
            .npc("Ha, ha, ha ha!")
            .then(new Effect() {
                public void run(Conversation c) {
                    give(COINS, WINNINGS[which]);
                    setField(BET_MASK, BET_SHIFT, 0);
                    mark(SETTLED);
                }
            })
            .message("The guards walk off chuckling to themselves.")
            .start();
    }

    /**
     * A player who never took the bet gets billed for the mess instead, and can
     * haggle the twenty down to fifteen. Refusing, or not having it, ends with
     * a long walk back from the middle of the desert.
     */
    private void cleanupBill(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Well, you've killed our Captain.");
        c.npc("I guess you've proved yourself in combat.");
        c.npc("However, you've left a horrible mess now.");
        c.npc("And it's gonna cost you for us to clean it up.");
        c.npc("Let's say 20 gold and we won't have to get rough with you?");
        c.options(new Choice("Yeah, ok, I'll give you 20 gold.",
                             "I'll give you 15, that's all you're gettin'",
                             "You can whistle for you money, I'll take you all on.") {
            public void picked(int option, Conversation c) {
                if (option == 2) {
                    c.npc("Ok, that's it, we're gonna teach you a lesson.");
                    roughedUp(c);
                    return;
                }
                final int fee = option == 0 ? 20 : 15;
                if (coins() < fee) {
                    c.npc("You don't have the gold and now we're gonna teach you a lesson.");
                    roughedUp(c);
                    return;
                }
                c.npc(option == 0 ? "Good! Seeya, we have some cleaning to do."
                                  : "Ok, we'll take fifteen, you push a hard bargain!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player pl = c.getPlayer();
                        pl.getInventory().remove(COINS, fee);
                        pl.getActionSender().sendInventory();
                        mark(SETTLED);
                    }
                });
            }
        }.says(2, "You can whistle for your money, I'll take you all on."));
        c.start();
    }

    private void roughedUp(Conversation c) {
        c.message("The Guards search you!");
        c.message("You are under attack!");
        c.npc("Guards, guards!");
        c.message("Nearby guards quickly grab you and rough you up a bit.");
        intoTheDesert(c);
    }

    /**
     * The camp's other punishment. The cell is for people caught inside; people
     * caught outside get driven out and dumped, and if they are carrying water
     * the guards take that too, which in the middle of the Kharidian desert is
     * most of the point.
     */
    private void intoTheDesert(Conversation c) {
        c.npc("Let's see how good you are with desert survival techniques!");
        c.message("You're bundled into the back of a cart and blindfolded...");
        c.message("Sometime later you wake up in the desert.");
        if (hasWater()) {
            c.message("@yel@Draft Mercenary Guard: You won't be needing that water any more!");
            c.message("The guards throw your water away...");
            c.then(new Effect() {
                public void run(Conversation c) {
                    Player pl = c.getPlayer();
                    for (int i = 0; i < WATER_SKINS.length; i++) {
                        while (pl.getInventory().countId(WATER_SKINS[i]) > 0) {
                            pl.getInventory().remove(WATER_SKINS[i], 1);
                        }
                    }
                    pl.getActionSender().sendInventory();
                }
            });
        }
        c.message("The guards move off in the cart leaving you stranded in the desert.");
        c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(STRANDED_X, STRANDED_Y, false);
            }
        });
    }

    private int coins() {
        return getOwner().getInventory().countId(COINS);
    }

    private boolean hasWater() {
        for (int i = 0; i < WATER_SKINS.length; i++) {
            if (holds(WATER_SKINS[i])) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------- the mine jail --

    /*
     * npc 692, the two guards at (70,3625) and (70,3627), and the gate they
     * watch. Nothing in this build puts a player behind that gate -- every
     * punishment here goes to the surface cell, which has a window to climb out
     * of -- so this is a room with a door and a way out and no way in. The
     * words are Jagex's and the way out is the recorded one.
     */
    private boolean inMineJail() {
        Player p = getOwner();
        return p.getX() >= MINE_JAIL_X && p.getY() >= 3615 && p.getY() <= 3634;
    }

    private void jailMercenary(final Npc npc) {
        Player p = getOwner();
        if (!inMineJail()) {
            Conversation c = new Conversation(p, npc);
            c.npc("Yes, what do you want?");
            c.options(new Choice("What are you guarding?",
                                 "Oh, nothing sorry for disturbing you.",
                                 "Your head on a stick.") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("I'm guarding troublesome prisoners.");
                        c.npc("They think they can get away with attacking the guards.");
                        c.npc("Well, we taught them a thing or two.");
                        return;
                    }
                    if (option == 1) {
                        c.npc("I should think so to, now get back to work.");
                        return;
                    }
                    headOnAStick(c);
                }
            });
            c.start();
            return;
        }
        if (holds(ROCKS) && p.getInventory().countId(ROCKS) >= 15) {
            new Conversation(p, npc)
                .player("Hey, I have your rocks here, let me out.")
                .npc("Ok, ok, come on out.")
                .message("The guard unlocks the gate and lets you out.")
                .then(new Effect() {
                    public void run(Conversation c) {
                        Player pl = c.getPlayer();
                        pl.getInventory().remove(ROCKS, 15);
                        pl.getActionSender().sendInventory();
                        pl.teleport(OUT_OF_MINE_JAIL_X, OUT_OF_MINE_JAIL_Y, false);
                    }
                })
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Hey, move away from the gate.")
            .npc("If you wanna get out, you're gonna have to mine for it.")
            .npc("You're gonna have to bring me 15 loads of rocks - in one go!")
            .npc("And then I'll let you out.")
            .npc("You can go back and work with the other slaves then!")
            .start();
    }

    /**
     * Both the lift guard and the jail guard offer the same rude answer and
     * both react to it the same way, so they share it.
     */
    private void headOnAStick(Conversation c) {
        c.npc("Why you ungrateful whelp...I'll teach you some manners.");
        c.message("The guard shouts for help.");
        c.message("You are under attack!");
        c.message("Other guards start arriving.");
        c.npc("Get him men!");
        c.message("The guards rough you up a bit and then drag you to a cell.");
        c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(CELL_X, CELL_Y, false);
            }
        });
    }

    /**
     * Scenery 1030, "Strange rocks - who knows why they're wanted?", twenty
     * seven of them lining the mine's walls. Nothing in this server touched
     * them until now: they are not in ObjectMining.xml and no quest claimed
     * them, so the one thing every slave in this camp does all day did nothing
     * at all. They give item 986, the load of rocks the jail guard wants
     * fifteen of.
     *
     * No pickaxe and no mining level is asked for. The camp issues its slaves
     * whatever they mine with and the transcript never mentions a pick, and a
     * player locked in the mine jail without one would have no way out.
     */
    private void mineRocks(QuestTrigger trigger) {
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("Strange rocks - who knows why they're wanted?");
            return;
        }
        say("You hack at the rock face.");
        say("You break off a load of rocks.");
        give(ROCKS);
    }
}
