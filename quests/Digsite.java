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
import org.rscdaemon.server.util.DataConversions;

/**
 * Digsite. Released 9 July 2003, written by Ian Taylor.
 *
 * An archaeological dig east of Varrock, run as a school. Nobody may put a
 * trowel in the ground without an earth sciences certificate, so the quest is
 * really three exams: fail the first one, find the three students' lost rock
 * samples, and they will feed you the answers a level at a time. Passing all
 * three buys the right to dig the level 3 sites, where the Talisman of Zaros
 * comes out of the soil -- and that buys permission to use the mine shafts,
 * under which the Saradominists buried an altar to a god nobody has heard of.
 *
 *     Examiner              npc 723, five of them in the Exam Centre
 *     Archaeological expert npc 728, "Terry balando", museum storage upstairs
 *     Curator               npc 39,  Varrock museum -- stamps the letter
 *     Workman               npc 722 on the surface, 738 underground
 *     Guide                 npc 726, the panning teacher by the river
 *     Student               npc 725 green shirt, 727 orange, 724 pink
 *
 *     Panning tray 1111 empty, 1112 with nuggets, 1113 with mud
 *     Rock pick 1114   Specimen brush 1115   Specimen jar 1116
 *     Rock Sample 1117 green, 1148 orange, 1149 pink
 *     gold Nuggets 1118   Book of experimental chemistry 1141
 *     Level 1/2/3 Certificate 1142/1143/1144   Trowel 1145
 *     Stamped letter 1146   Unstamped letter 1147   Cracked rock Sample 1150
 *     Ammonium Nitrate 1160   Nitroglycerin 1161   chest key 1164
 *     Unidentified powder 1171   Scroll 1173   stone tablet 1174
 *     Talisman of Zaros 1175   Explosive compound 1176
 *     Mixed chemicals 1178 (two) and 1180 (three)   Ground charcoal 1179
 *     Unidentified liquid 1232   Arcenia root 1284
 *
 * The three students are told apart by their shirts, which is how the wiki
 * transcripts name them and the only thing that distinguishes them. NPCDef
 * settles it: 725 is topColour 52224 (green) over 15658576 (yellow), 727 is
 * 16737792 (orange), 724 is 16036851 (pink). Each hands over one answer per
 * exam, always in the same order, which is why one two-bit counter per student
 * is enough state for all nine answers.
 *
 * Which of the three Rock Samples belongs to which student is not recorded
 * anywhere -- the three item defs are identical but for their sprites -- so the
 * assignment here (1117 green, 1148 orange, 1149 pink) is a choice.
 *
 * Digging is gated by site. The 113 soil tiles fall into nine clusters, and
 * each cluster's level was read off the signpost standing in it and checked
 * against the wiki's prose description of where each level's sites are; both
 * buried skeletons land in level 3 sites, as the wiki says they should. The
 * boxes in SITES are those clusters, and every one of the 113 tiles falls in
 * exactly one of them.
 *
 * The underground exists twice, the second copy exactly 48 tiles further south.
 * That is not a duplicate: the copies differ in precisely the ways the quest
 * needs them to. In the first, the only corridor between the north chamber and
 * the temple -- two tiles wide, at (13,3337) and (14,3337) -- is filled with
 * the searchable Bricks 1096 and five plain ones, and there are no skeletons.
 * In the second the searchable Bricks are gone, those same tiles are clear, the
 * plain bricks have been scattered to the sides as rubble, and all thirty-six
 * skeletons and the spider are standing behind them. The two copies are the
 * before and after of the explosion, so lighting the compound moves the player
 * from the first into the second. This is read off the map data, not chosen.
 *
 * Grinding charcoal was added to InvUseOnItem.doGrind rather than claiming the
 * pestle and mortar here, following the bat bones precedent set by Watchtower
 * -- one of several things about this quest that had to be decided rather
 * than recovered.
 */
public class Digsite extends Quest {
    public Digsite(Player owner, Integer uid) {
        super(owner, uid);
    }

    private static final int UID = Quests.DIGSITE;

    // --------------------------------------------------------------- npcs --

    private static final int CURATOR = 39;
    private static final int WORKMAN = 722, EXAMINER = 723;
    private static final int STUDENT_PINK = 724, STUDENT_GREEN = 725;
    private static final int GUIDE = 726, STUDENT_ORANGE = 727;
    private static final int EXPERT = 728, WORKMAN_DOWN = 738;

    /** Green, orange, pink -- the order the counters are stored in. */
    private static final int GREEN = 0, ORANGE = 1, PINK = 2;
    private static final int[] STUDENT_NPC = { STUDENT_GREEN, STUDENT_ORANGE, STUDENT_PINK };
    private static final int[] SAMPLE = { 1117, 1148, 1149 };

    // -------------------------------------------------------------- items --

    private static final int COINS = 10, GLOVES = 16, BOOTS = 17, BONES = 20;
    private static final int IRON_DAGGER = 28, NEEDLE = 39, POT = 135, JUG = 140;
    private static final int CLAY = 149, COPPER_ORE = 150, GOLD_ORE = 152;
    private static final int TINDERBOX = 166, GOLD_BAR = 172, SPADE = 211;
    private static final int PIE_DISH = 251, RAT_TAIL = 271, ROPE = 237;
    private static final int CHOCOLATE_SLICE = 336, MED_BLACK_HELMET = 470;
    private static final int VIAL = 465, PURPLE_DYE = 516, BROKEN_GLASS_2 = 778;
    private static final int ROTTEN_APPLES = 801, BRONZE_SPEAR = 827;
    private static final int FRUIT_BLAST = 866, UNCUT_JADE = 890, UNCUT_OPAL = 891;
    private static final int OPAL = 894, CHARCOAL = 983, IRON_KNIFE = 1075;
    private static final int CUP_OF_TEA = 739;

    private static final int TRAY_EMPTY = 1111, TRAY_GOLD = 1112, TRAY_MUD = 1113;
    private static final int ROCK_PICK = 1114, BRUSH = 1115, JAR = 1116;
    private static final int NUGGETS = 1118, BOOK = 1141;
    private static final int CERT1 = 1142, CERT2 = 1143, CERT3 = 1144;
    private static final int TROWEL = 1145, LETTER_STAMPED = 1146, LETTER_PLAIN = 1147;
    private static final int CRACKED_SAMPLE = 1150, BELT_BUCKLE = 1151;
    private static final int OLD_BOOT = 1155, ARMOUR_A = 1157, ARMOUR_B = 1158;
    private static final int RUSTY_SWORD = 1159, NITRATE = 1160, NITRO = 1161;
    private static final int OLD_TOOTH = 1162, CHEST_KEY = 1164, BROKEN_ARROW = 1165;
    private static final int BUTTONS = 1166, BROKEN_STAFF = 1167, VASE = 1168;
    private static final int CERAMIC = 1169, BROKEN_GLASS_1 = 1170, POWDER = 1171;
    private static final int SCROLL = 1173, TABLET = 1174, TALISMAN = 1175;
    private static final int COMPOUND = 1176, MIX2 = 1178, GROUND_CHARCOAL = 1179;
    private static final int MIX3 = 1180, LIQUID = 1232, ROOT = 1284;

    // ------------------------------------------------------------ scenery --

    private static final int SKELETON_W = 1049, SPECIMEN_TRAY = 1052;
    private static final int WINCH_WEST = 1053, SKELETON_N = 1057;
    private static final int PANNING_POINT = 1058;
    private static final int SIGN_TRAINING = 1060, SIGN_L1 = 1061;
    private static final int SIGN_L2 = 1062, SIGN_L3 = 1063;
    private static final int SOIL_A = 1065, SOIL_B = 1066, SOIL_C = 1067;
    private static final int BUSH_EMPTY = 1072, BUSH_SAMPLE = 1073;
    private static final int CUPBOARD_SHUT = 1074, SACKS_EMPTY = 1075;
    private static final int SACKS_JAR = 1076, CUPBOARD = 1078;
    private static final int BARREL_SHUT = 1082, BARREL_OPEN = 1083;
    private static final int CHEST_POWDER_OPEN = 1084, CHEST_POWDER = 1085;
    private static final int BOOKCASE = 1090, WINCH_NE = 1095;
    private static final int BRICKS = 1096, ROPE_SMALL = 1097, ROPE_MAIN = 1098;
    private static final int BRICKS_PRIMED = 1103;
    private static final int CHEST_EXAM = 1104, CHEST_EXAM_OPEN = 1105;
    private static final int SACKS_CAVE = 1150;

    // ------------------------------------------------------------- skills --

    private static final int MINING = 14, HERBLAW = 15, AGILITY = 16;
    /**
     * Slot 17 is thieving; Formulae.statArray labelled it "quest" until task
     * #38 fixed it. Pickpocketing the workmen writes here.
     *
     * Vanilla also demands Thieving 25 before the pockets open at all. Thieving
     * is now implemented (task #36), and the quest's own start requirement is
     * declared in define() below; this file's pickpocket() still does not gate
     * the workmen's pockets on level the way NpcCommand's general pickpocket
     * does, because they are this quest's own reimplementation (see
     * associateNpcCommand's javadoc) and were never given a level check of
     * their own to begin with. That is unchanged here.
     */
    private static final int THIEVING = 17;

    // ------------------------------------------------------------- stages --

    private static final int STARTED = 1;
    /** The stamped letter has been handed in; exams are available. */
    private static final int EXAMS = 2;
    private static final int PASSED1 = 3, PASSED2 = 4, PASSED3 = 5;
    /** The Talisman of Zaros is out of the ground. */
    private static final int GOT_TALISMAN = 6;
    /** The expert has written the permission scroll. */
    private static final int GOT_SCROLL = 7;
    /** A workman has taken the scroll; the winches work. */
    private static final int PERMIT = 8;
    /** The corridor to the temple is open. */
    private static final int BLASTED = 9;
    private static final int FINISHED = 10;
    private static final int STAGE_MASK = 15;

    /** The first exam has been sat and failed, so the students will talk. */
    private static final int TRIED1 = 1 << 4;
    /** Two bits per student: how many of their three answers are known. */
    private static final int[] CNT_SHIFT = { 5, 7, 9 };
    private static final int CNT_MASK = (3 << 5) | (3 << 7) | (3 << 9);
    /** The pink student has been paid her opal. */
    private static final int OPAL_PAID = 1 << 11;
    private static final int GOT_BOOK = 1 << 12;
    private static final int GOT_PICK = 1 << 13;
    private static final int GOT_CRACKED = 1 << 14;
    private static final int GOT_BRUSH = 1 << 15;
    private static final int GOT_JAR = 1 << 16;
    private static final int GOT_KEY = 1 << 17;
    private static final int GOT_POWDER = 1 << 18;
    /** A closed barrel has been prised open with the trowel. */
    private static final int BARREL_PRISED = 1 << 19;
    /** The explosive compound is packed against the bricks. */
    private static final int PRIMED = 1 << 20;
    private static final int ROPE_NE = 1 << 21, ROPE_W = 1 << 22;
    private static final int CH_NITRATE = 1 << 23, CH_CHARCOAL = 1 << 24;
    private static final int CH_ROOT = 1 << 25;
    /**
     * The guide has had his cup of tea. Panning is a permanent activity rather
     * than a quest step, so this one bit has to outlive the quest.
     */
    private static final int TEA = 1 << 26;
    /**
     * A second talisman has bought the mine shafts back after the quest, which
     * is how vanilla lets a finished player return to the underground workman.
     */
    private static final int POST_PERMIT = 1 << 27;
    /** One bit per student: they have asked for their sample. */
    private static final int[] ASKED = { 1 << 28, 1 << 29, 1 << 30 };

    private static final int BITS = TRIED1 | CNT_MASK | OPAL_PAID | GOT_BOOK
            | GOT_PICK | GOT_CRACKED | GOT_BRUSH | GOT_JAR | GOT_KEY
            | GOT_POWDER | BARREL_PRISED | PRIMED | ROPE_NE | ROPE_W
            | CH_NITRATE | CH_CHARCOAL | CH_ROOT | TEA | POST_PERMIT
            | ASKED[0] | ASKED[1] | ASKED[2];

    // ----------------------------------------------------------- the dig --

    /** Training, level 1, level 2 and level 3 sites: x1, x2, y1, y2, level. */
    private static final int[][] SITES = {
        { 10, 14, 495, 498, 3 },   // northernmost, buried skeleton 1057
        { 13, 16, 503, 509, 2 },   // around the north-east winch
        { 19, 28, 504, 509, 2 },   // north-west corner
        { 24, 27, 513, 517, 2 },   // around the western winch
        { 13, 17, 516, 524, 1 },   // large eastern
        { 19, 21, 516, 526, 1 },   // middle
        { 23, 28, 518, 524, 3 },   // west, buried skeleton 1049
        { 12, 17, 525, 529, 0 },   // training, west
        { 23, 28, 525, 529, 0 },   // training, east
    };

    private static final int[] LOOT_TRAINING = {
        BROKEN_ARROW, COINS, CRACKED_SAMPLE, CHARCOAL, VASE, -1, -1, -1,
    };
    private static final int[] LOOT_L1 = {
        BONES, BROKEN_GLASS_1, BUTTONS, COPPER_ORE, OLD_BOOT, OPAL,
        OLD_TOOTH, ROTTEN_APPLES, RUSTY_SWORD, VASE,
    };
    private static final int[] LOOT_L2 = {
        BONES, BROKEN_GLASS_2, BROKEN_STAFF, CLAY, ARMOUR_A, ARMOUR_B,
        JUG, OLD_BOOT, POT, PURPLE_DYE, RAT_TAIL,
    };
    private static final int[] LOOT_L3 = {
        BELT_BUCKLE, BONES, BROKEN_ARROW, BROKEN_STAFF, BRONZE_SPEAR, BUTTONS,
        CERAMIC, CLAY, COINS, IRON_KNIFE, MED_BLACK_HELMET, NEEDLE, OLD_BOOT,
        OLD_TOOTH, PIE_DISH, PURPLE_DYE,
    };
    private static final int[] LOOT_TRAY = {
        BONES, BROKEN_ARROW, BROKEN_GLASS_1, CERAMIC, CHARCOAL, COINS,
        CRACKED_SAMPLE, IRON_DAGGER, -1, -1,
    };
    private static final int[] LOOT_PAN = {
        COINS, COINS, COINS, NUGGETS, NUGGETS, UNCUT_OPAL, UNCUT_JADE,
        160 /* uncut sapphire */, SAMPLE[ORANGE], -1, -1, -1,
    };

    // ------------------------------------------------------ the two caves --

    /* The two winches, the tile each one drops the player onto, and the tile
     * each rope puts them back on. The winches are geographically crossed --
     * the wiki notes as much in its own trivia -- so the north-east one is the
     * small cave-in with the workmen and the western one is the temple. Both
     * landing tiles sit beside the rope that climbs back out; the surface tiles
     * are the walkable square next to each winch. */
    private static final int NE_WINCH_X = 14, NE_WINCH_Y = 505;
    private static final int W_WINCH_X = 26, W_WINCH_Y = 516;
    private static final int SMALL_CAVE_X = 26, SMALL_CAVE_Y = 3346;
    /* Beside the rope, not under it. Rope 1098 stands on (19,3338) and the
     * tile below it is rock, so the winch used to drop the player into the
     * wall; (20,3338) is the cave floor, and +CAVE_OFFSET lands the same way
     * in the blasted copy of the room. */
    private static final int MAIN_CAVE_X = 20, MAIN_CAVE_Y = 3338;
    /** Same tile in the copy of the cave that the explosion opens. */
    private static final int CAVE_OFFSET = 48;
    /** Where the blast leaves the player: the mouth of the cleared corridor. */
    private static final int BLAST_X = 11, BLAST_Y = 3387;

    // --------------------------------------------------------------- exam --

    /* Per question: the section heading, the question, three wrong answers, and
     * the right one. When the right one is known it displaces the first wrong
     * answer and takes the slot named in SLOT. */
    private static final String[][] EXAM1 = {
        { "Question 1 - Earth sciences overview...",
          "Can you tell me what earth sciences is ?",
          "The study of gardening, planting and fruiting vegetation",
          "The study of planets, and the history of forming worlds",
          "The combination of all skills applied to the working of the earth",
          "The study of the earth, It's contents and It's history" },
        { "Question 2 - Elligibility",
          "Can you tell me what people are allowed to use the digsite ?",
          "Magic users, miners and their escorts",
          "Professors, students and workmen only",
          "Local residents, and contractors only",
          "All that have passed the appropriate earth sciences exam" },
        { "Question 3 - Health and safety",
          "Can you tell me the proper safety points when working in a digsite ?",
          "Heat-resistant clothing to be worn at all times",
          "Overcoats and facemasks to be worn at all times",
          "Protective clothing to be worn, tools kept away from site",
          "Gloves and boots to be worn at all times, proper tools must be used" },
    };
    private static final String[][] EXAM2 = {
        { "Question 1 - Sample transportation",
          "Can you tell me how we transport samples ?",
          "Samples cut and cleaned before transportation",
          "Samples ground and suspended in an acid solution",
          "Samples to be left at digsite for examination",
          "Samples taken in rough form, kept only in sealed containers" },
        { "Question 2 - handling of finds ?",
          "Can you tell me about the handling of finds ?",
          "Finds must not be handled by anyone",
          "Finds to be given to the site workmen",
          "Finds are kept together for safekeeping",
          "Finds must be carefully handled, and gloves worn" },
        { "Question 3 - Rockpick usage",
          "Can you tell me the proper usage for a rockpick ?",
          "Strike rock repeatedly until powdered",
          "Rockpick must be used flat and with strong force",
          "Rockpicks to be used only in emergencies",
          "Always handle with care, strike the rock cleanly on it's cleaving point" },
    };
    private static final String[][] EXAM3 = {
        { "Question 1 - Sample preparation",
          "Can you tell me how we prepare samples ?",
          "Samples may be mixed together safely",
          "Sample types catalogued and carried by hand only",
          "Samples not to be prepared by any means",
          "Samples cleaned and carried only in specimen jars" },
        { "Question 2 - Specimen brush use",
          "What is the proper way to use the specimen brush ?",
          "Brush quickly using a wet brush",
          "Brush pre-cleaned samples only",
          "Brush quickly and with force",
          "Brush carefully and slowly, using short strokes" },
        { "Question 3 - Advanced techniques",
          "Can you tell me the proper technique for dealing with bones ?",
          "Bones must not be taken from the digsite",
          "Bones must be suspended in a sterile solution",
          "Bones to be ground and tested for mineral content",
          "Handle bones very carefully, and keep away from other samples" },
    };
    private static final String[][][] EXAMS_ALL = { EXAM1, EXAM2, EXAM3 };
    /** Where the right answer sits once it is known. */
    private static final int[][] SLOT = { { 0, 2, 1 }, { 2, 0, 1 }, { 0, 0, 2 } };
    /** Which student supplies each answer. */
    private static final int[][] TUTOR = {
        { GREEN, ORANGE, PINK }, { ORANGE, PINK, GREEN }, { PINK, GREEN, ORANGE },
    };
    /** The lead-in each student gives before reciting an answer. */
    private static final String[][] LEAD = {
        { "The study of earthsciences is:", "Correct rockpick usage:",
          "Specimen brush use:" },
        { "The elligible people to use the digsite are:",
          "Correct sample transportation:",
          "The proper technique for handling bones is:" },
        { "The proper health and safety points are:", "Finds handling:",
          "Sample preparation:" },
    };
    /** What the student actually says, which is not always the exam wording. */
    private static final String[][] RECITE = {
        { "The study of the earth, It's contents and It's history",
          "Always handle with care, strike the rock cleanly on it's cleaving point",
          "Brush carefully and slowly, use short strokes" },
        { "All that have passed the appropriate earth sciences exams",
          "Samples taken in rough form, kept only in sealed containers",
          "Handle bones very carefully, and keep away from other samples" },
        { "Gloves and boots to be worn at all times, proper tools must be used",
          "Finds must be carefully handled, and gloves worn",
          "Samples cleaned and carried only in specimen jars" },
    };

    // ------------------------------------------------------------- define --

    public void define() {
        setUID(UID);
        setName("Digsite");
        /* completed() is exact equality against a final stage, so every working
         * bit has to be gone by the time the tablet changes hands. Two cannot
         * be: the guide's cup of tea makes panning permanent, and a second
         * talisman buys the mine shafts back after the quest is over. Both are
         * legalised as extra final stages rather than left as bits that would
         * quietly un-complete the quest. */
        setFinalStage(FINISHED);
        addFinalStage(FINISHED | TEA);
        addFinalStage(FINISHED | POST_PERMIT);
        addFinalStage(FINISHED | TEA | POST_PERMIT);

        associateNpc(EXAMINER);
        associateNpc(EXPERT);
        /* Their pockets are this quest's, not Thieving's. */
        associateNpcCommand(WORKMAN);
        associateNpcCommand(WORKMAN_DOWN);
        associateNpc(GUIDE);
        associateNpc(STUDENT_GREEN);
        associateNpc(STUDENT_ORANGE);
        associateNpc(STUDENT_PINK);
        // @share npc 39 with ShieldOfArrav
        // The curator stamps the letter and takes the certificates, both of
        // which are items used on him, and Shield of Arrav only ever answers
        // NPC_TALK and NPC_KILLED for him. Neither quest can see the other's
        // trigger, so the two claims do not collide.
        associateNpc(CURATOR);

        associateObject(BOOKCASE);
        associateObject(CUPBOARD);
        associateObject(CUPBOARD_SHUT);
        associateObject(CHEST_EXAM);
        associateObject(CHEST_EXAM_OPEN);
        associateObject(BUSH_EMPTY);
        associateObject(BUSH_SAMPLE);
        associateObject(SACKS_EMPTY);
        associateObject(SACKS_JAR);
        associateObject(SACKS_CAVE);
        associateObject(SPECIMEN_TRAY);
        associateObject(PANNING_POINT);
        associateObject(SIGN_TRAINING);
        associateObject(SIGN_L1);
        associateObject(SIGN_L2);
        associateObject(SIGN_L3);
        associateObject(SOIL_A);
        associateObject(SOIL_B);
        associateObject(SOIL_C);
        associateObject(SKELETON_N);
        associateObject(SKELETON_W);
        associateObject(BARREL_SHUT);
        associateObject(BARREL_OPEN);
        associateObject(CHEST_POWDER);
        associateObject(CHEST_POWDER_OPEN);
        associateObject(WINCH_NE);
        associateObject(WINCH_WEST);
        associateObject(ROPE_SMALL);
        associateObject(ROPE_MAIN);
        associateObject(BRICKS);
        associateObject(BRICKS_PRIMED);

        /* Nine of these are claimed for a right-click command this quest
         * reimplements; the other six are claimed so the chemistry can be
         * offered as an item pair, which needs both halves claimed. None of the
         * six has a command to lose. */
        associateItem(TRAY_EMPTY);
        associateItem(TRAY_GOLD);
        associateItem(TRAY_MUD);
        associateItem(BOOK);
        associateItem(CERT1);
        associateItem(CERT2);
        associateItem(CERT3);
        associateItem(SCROLL);
        associateItem(TABLET);
        associateItem(NITRO);
        associateItem(NITRATE);
        associateItem(GROUND_CHARCOAL);
        associateItem(ROOT);
        associateItem(MIX2);
        associateItem(MIX3);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("The examiners at the digsite east of Varrock let nobody put a trowel in the ground without an earth sciences certificate; pass all three exams and uncover what is buried beneath the site.");
        setStartPoint("The Exam Centre at the digsite east of Varrock");
        setSpeakTo("Examiner");
        /* Checked where it bites, at mixing the explosive chemicals. */
        requireLevel(HERBLAW, 10);
        /* RuneHQ (DigsiteQuestGuideCL.txt) and the wiki both list these
         * alongside Herblaw 10; like it, none of the three has a start gate
         * in this code -- they are declared for the manual and the reward
         * calculation, not enforced by talking to the examiner. */
        requireLevel(THIEVING, 25);
        requireLevel(AGILITY, 10);
        requireQuest(Quests.DRUIDIC_RITUAL);
        rewardItem(GOLD_BAR, 2);
        rewardExp(MINING, 300, 300);
        rewardExp(HERBLAW, 125, 125);
        rewardOther("Use of the digsite's mine shafts");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Digsite Quest");
    }

    // ------------------------------------------------------------ helpers --

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

    private int cnt(int student) {
        return questStarted() ? (getStage() >> CNT_SHIFT[student]) & 3 : 0;
    }

    private void setCnt(int student, int value) {
        setStage((getStage() & ~(3 << CNT_SHIFT[student]))
                | (value << CNT_SHIFT[student]));
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

    private boolean wears(int id) {
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

    private int roll(int[] table) {
        return table[DataConversions.random(0, table.length - 1)];
    }

    /** The nearest workman, which is who does the shouting on this site. */
    private Npc nearbyWorkman() {
        Player p = getOwner();
        return world.getNpc(WORKMAN, p.getX() - 12, p.getX() + 12,
                            p.getY() - 12, p.getY() + 12);
    }

    /**
     * A workman tells the player off. Vanilla spawns one on top of the player
     * if none is in earshot; there is no clean way to conjure an npc from a
     * quest here, so out of earshot the lines arrive as plain messages instead.
     */
    private void scold(String[] lines) {
        Npc workman = nearbyWorkman();
        if (workman == null) {
            for (int i = 0; i < lines.length; i++) {
                say(lines[i]);
            }
            return;
        }
        Conversation c = new Conversation(getOwner(), workman);
        for (int i = 0; i < lines.length; i++) {
            c.npc(lines[i]);
        }
        c.start();
    }

    /** The dig level of the site the player is standing in, or -1 for none. */
    private int siteLevel(int x, int y) {
        for (int i = 0; i < SITES.length; i++) {
            if (x >= SITES[i][0] && x <= SITES[i][1]
                    && y >= SITES[i][2] && y <= SITES[i][3]) {
                return SITES[i][4];
            }
        }
        return -1;
    }

    private boolean underground() {
        return getOwner().getY() > 3000;
    }

    /** True once the mine shafts are open, before or after the quest. */
    private boolean mayUseWinch() {
        return completed() ? marked(POST_PERMIT) : past(PERMIT);
    }

    // ----------------------------------------------------------- dispatch --

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
        if (trigger == QuestTrigger.ITEM_ON_NPC) {
            itemOnNpc(npc, used);
            return;
        }
        if (trigger == QuestTrigger.NPC_COMMAND) {
            pickpocket(npc);
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case EXAMINER:       examiner(npc);      break;
            case EXPERT:         expert(npc);        break;
            case CURATOR:        curatorTalk(npc);   break;
            case GUIDE:          guide(npc);         break;
            case WORKMAN:        workman(npc);       break;
            case WORKMAN_DOWN:   workmanDown(npc);   break;
            case STUDENT_GREEN:  student(npc, GREEN);  break;
            case STUDENT_ORANGE: student(npc, ORANGE); break;
            case STUDENT_PINK:   student(npc, PINK);   break;
            default: break;
        }
    }

    private void scenery(QuestTrigger trigger, GameObject object, InvItem used) {
        int id = object.getID();
        if (trigger == QuestTrigger.ITEM_ON_OBJECT && used != null) {
            itemOnObject(id, object, used);
            return;
        }
        switch (id) {
            case BOOKCASE:          bookcase();            return;
            case CUPBOARD:
            case CUPBOARD_SHUT:     cupboard();            return;
            case CHEST_EXAM:
            case CHEST_EXAM_OPEN:   examCentreChest();     return;
            case BUSH_SAMPLE:       sampleBush();          return;
            case BUSH_EMPTY:        say("You search the bush");
                                    say("You find nothing"); return;
            case SACKS_JAR:         jarSacks();            return;
            case SACKS_EMPTY:       say("You search the sacks");
                                    say("You find nothing"); return;
            case SACKS_CAVE:        say("There is nothing under the sack!"); return;
            case SPECIMEN_TRAY:     searchTray();          return;
            case PANNING_POINT:     say("A shallow in the stream");
                                    say("I could pan for gold here with a panning tray");
                                    return;
            case SIGN_TRAINING:     say("This site is for training purposes only"); return;
            case SIGN_L1:           say("Level 1 digs only"); return;
            case SIGN_L2:           say("Level 2 digs only"); return;
            case SIGN_L3:           say("Level 3 digs only"); return;
            case SOIL_A: case SOIL_B: case SOIL_C:
                                    say("This is a patch of dug-over soil");
                                    say("I could dig here with the right tools");
                                    return;
            case SKELETON_N:
            case SKELETON_W:        say("You search the buried skeleton");
                                    say("It has been picked over already");
                                    return;
            case BARREL_OPEN:       openBarrel();          return;
            case CHEST_POWDER:
            case CHEST_POWDER_OPEN: powderChest();         return;
            case WINCH_NE:          winch(true);           return;
            case WINCH_WEST:        winch(false);          return;
            case ROPE_SMALL:        climb(NE_WINCH_X, NE_WINCH_Y); return;
            case ROPE_MAIN:         climb(W_WINCH_X, W_WINCH_Y);   return;
            case BRICKS:            say("It seems these were put here deliberately");
                                    say("Something powerful would be needed to shift them");
                                    return;
            default: return;
        }
    }

    // ---------------------------------------------------------- examiner --

    private void examiner(final Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        if (completed() || past(PASSED3)) {
            c.player("Hello");
            c.npc("Hi");
            c.npc("You have finished all the earth science exams now");
            c.npc("Congratulations on your graduation");
            c.npc("You now have free access to dig anywhere on the digsite");
            c.picker(new Choice("Thanks!", "I have lost my trowel!") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        lostTrowel(c);
                        return;
                    }
                    c.player("Thanks!");
                }
            });
            c.start();
            return;
        }
        if (!questStarted()) {
            c.player("Hello");
            c.npc("Ah hello there");
            c.npc("I am the resident lecturer on antiquities and artifacts");
            c.npc("I also set the earth sciences exams");
            c.player("earth sciences ?");
            c.npc("That is right dear");
            c.npc("The world of RuneScape holds many wonders beneath it's surface");
            c.picker(new Choice("an I take an exam ?", "Interesting...") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.player("Interesting...");
                        c.npc("You could gain much with an understanding of the world below");
                        return;
                    }
                    c.player("Can I take an exam ?");
                    c.npc("You can if you get this letter of recommendation stamped");
                    c.npc("By the curator of varrock museum");
                    c.player("Oh right, I'll see what I can do");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            step(STARTED);
                            give(LETTER_PLAIN);
                            say("The examiner hands you a letter");
                        }
                    });
                }
            });
            c.start();
            return;
        }
        if (at(STARTED)) {
            if (holds(LETTER_STAMPED)) {
                c.player("Hello");
                c.npc("Hello again");
                c.player("Here is the stamped letter you asked for");
                c.npc("Good good, we will begin the exam...");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        take(LETTER_STAMPED);
                        step(EXAMS);
                    }
                });
                exam(c, 1);
                c.start();
                return;
            }
            c.player("Hello");
            c.npc("Hello again");
            c.npc("I am still waiting for your stamped letter of recommendation");
            c.picker(new Choice("I have lost the letter you gave me",
                                 "All right I'll try and get it") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.player("All right I'll try and get it");
                        c.npc("I am sure you wont get any problems");
                        return;
                    }
                    c.player("I have lost the letter you gave me");
                    if (holds(LETTER_PLAIN)) {
                        c.npc("Oh now come on");
                        c.npc("You have it with you!");
                        return;
                    }
                    c.npc("That was foolish!");
                    c.npc("Take this one and keep it safe this time...");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            give(LETTER_PLAIN);
                        }
                    });
                }
            });
            c.start();
            return;
        }
        if (at(EXAMS)) {
            c.player("Hello");
            c.npc("Hello again");
            c.npc("Are you ready for another shot at the exam ?");
            c.picker(new Choice("Yes I certainly am", "No, not at the moment") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.player("Sorry, I didn't mean to disturb you...");
                        c.npc("Oh, no problem at all");
                        return;
                    }
                    c.player("Yes I certainly am");
                    exam(c, 1);
                }
            });
            c.start();
            return;
        }
        // Passed one or two exams; the next one is on offer.
        final int next = at(PASSED1) ? 2 : 3;
        c.player("Hello");
        c.npc(next == 2 ? "Hi there" : "Ah hello again");
        c.picker(new Choice(next == 2 ? "I am ready for the next exam section"
                                       : "I am ready for the last part of the exam",
                             "I am stuck on a question",
                             "Sorry, I didn't mean to disturb you...",
                             "I have lost my trowel!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I am stuck on a question");
                    c.npc("Well well, have you not been doing your studies ?");
                    c.npc("I am not going to give you the answers");
                    c.npc("Talk to the other students and remember the answers");
                    return;
                }
                if (option == 2) {
                    c.player("Sorry, I didn't mean to disturb you...");
                    c.npc("Oh, no problem at all");
                    return;
                }
                if (option == 3) {
                    lostTrowel(c);
                    return;
                }
                c.player(next == 2 ? "I am ready for the next exam section"
                                   : "I am ready for the last part of the exam");
                exam(c, next);
            }
        });
        c.start();
    }

    private void lostTrowel(Conversation c) {
        c.player("I have lost my trowel!");
        if (holds(TROWEL)) {
            c.npc("Really ?");
            c.npc("Look in your backpack and make sure first");
            return;
        }
        c.npc("Deary me.. that was a good one as well");
        c.npc("It's a good job I have another");
        c.npc("Here you go");
        c.then(new Effect() {
            public void run(Conversation c) {
                give(TROWEL);
            }
        });
    }

    // -------------------------------------------------------------- exam --

    private void exam(Conversation c, int level) {
        if (level == 1) {
            c.npc("Okay, we will start the first level exam:");
            c.npc("Earth sciences level 1 - Beginner");
        } else if (level == 2) {
            c.npc("Okay, this is the next part of the earth sciences exam");
            c.npc("Earth sciences level 2- Intermediate");
        } else {
            c.npc("Attention, this is the final part of the earth sciences exam");
            c.npc("Earth sciences 3 - Advanced");
        }
        ask(c, level, 0, new int[1]);
    }

    /*
     * A few exam answers are listed shorter in the menu than the player then
     * says them out loud. Transcript:Examiner records both wordings, so both
     * are kept: spoken form on the left, menu form on the right. Any answer
     * not listed here reads the same in the menu as it does spoken.
     */
    private static final String[][] MENU_LABEL = {
        { "The study of planets, and the history of forming worlds",
          "The study of planets, and the history of worlds" },
    };

    private static String menuLabel(String spoken) {
        for (int i = 0; i < MENU_LABEL.length; i++) {
            if (MENU_LABEL[i][0].equals(spoken)) {
                return MENU_LABEL[i][1];
            }
        }
        return spoken;
    }

    private void ask(Conversation c, final int level, final int q, final int[] score) {
        String[][] paper = EXAMS_ALL[level - 1];
        if (q > 0) {
            c.npc("Okay, next question...");
            c.npc("Earth sciences level " + level);
        }
        c.npc(paper[q][0]);
        c.npc(paper[q][1]);

        boolean known = cnt(TUTOR[level - 1][q]) >= level;
        final String[] options = new String[3];
        final int right;
        if (known) {
            // The known answer displaces the first wrong one and takes its own
            // slot in the list; the other two keep their order around it.
            right = SLOT[level - 1][q];
            int next = 3;
            for (int i = 0; i < 3; i++) {
                options[i] = (i == right) ? paper[q][5] : paper[q][next++];
            }
        } else {
            right = -1;
            options[0] = paper[q][2];
            options[1] = paper[q][3];
            options[2] = paper[q][4];
        }
        final String[] labels = new String[3];
        for (int i = 0; i < 3; i++) {
            labels[i] = menuLabel(options[i]);
        }
        /* picker(), not options(): the branch speaks the answer itself, and it
           speaks the full wording rather than the clipped menu one. */
        c.picker(new Choice(labels) {
            public void picked(int option, Conversation c) {
                c.player(options[option]);
                if (option == right) {
                    score[0]++;
                }
                if (q < 2) {
                    ask(c, level, q + 1, score);
                    return;
                }
                mark(c, level, score[0]);
            }
        });
    }

    private void mark(Conversation c, final int level, int score) {
        if (level == 3) {
            c.npc("Okay, that concludes level 3 Earthsciences exam");
            c.npc("Let me add up the results...");
        } else {
            c.npc("Okay, that covers level " + level + " Earthsciences exam");
            c.npc(level == 1 ? "Let's see how you did..." : "Let me add up your total...");
        }
        if (score == 3) {
            pass(c, level);
            return;
        }
        if (score == 0) {
            if (level == 1) {
                c.npc("Oh deary me!");
                c.npc("This is appalling, none correct at all!");
                c.npc("I suggest you go and study properly...");
                c.player("Oh dear...");
            } else if (level == 2) {
                c.npc("No no no!");
                c.npc("This will not do");
                c.npc("They are all wrong, start again!");
                c.player("Oh no!");
            } else {
                c.npc("I cannot believe this!");
                c.npc("Absolutely none right at all");
                c.npc("I doubt you did any research before you took this exam...");
                c.player("Ah...yes...erm...");
                c.player("I think I had better go and revise first!");
            }
        } else if (score == 1) {
            c.npc("You got 1 question correct");
            c.npc(level == 1 ? "Better luck next time"
                 : level == 2 ? "At least it's a start" : "Try harder!");
            c.player(level == 2 ? "Oh well..." : "Oh bother!");
        } else {
            c.npc("You got 2 questions correct");
            c.npc(level == 1 ? "Not bad, just a little more revision needed"
                 : level == 2 ? "Not too bad, but you can do better..."
                              : "A little more study and you will pass it");
            c.player(level == 1 ? "Oh well..."
                   : level == 2 ? "Nearly got it" : "I'm nearly there...");
        }
        if (level == 1) {
            c.then(new Effect() {
                public void run(Conversation c) {
                    // Failing the first exam is what sends the player to the
                    // students, so it is the thing that opens them up.
                    mark(TRIED1);
                }
            });
        }
    }

    private void pass(Conversation c, final int level) {
        if (level == 1) {
            c.npc("You got all the the questions correct, well done");
            c.player("Hey! Excellent!");
            c.npc("You have now passed the Earth sciences level 1 general exam");
            c.npc("Here is your certificate to prove it");
            c.npc("You also get a decent trowel to dig with");
            c.npc("Here you go...");
            c.message("The examiner hands you a trowel");
        } else if (level == 2) {
            c.npc("You got all the questions correct, well done!");
            c.player("Great, I'm getting good at this");
            c.npc("You have now passed the Earth sciences level 2 intermediate exam");
            c.npc("Here is your certificate");
        } else {
            c.npc("You got all the questions correct, well done!");
            c.player("Hooray!");
            c.npc("Congratulations, You have now passed the Earth sciences level 3 advanced exam");
            c.npc("Here is your level 3 certificate");
            c.player("I can dig wherever I want now...");
        }
        c.then(new Effect() {
            public void run(Conversation c) {
                step(PASSED1 + level - 1);
                give(CERT1 + level - 1);
                if (level == 1) {
                    give(TROWEL);
                }
            }
        });
    }

    // ----------------------------------------------------------- students --

    private void student(final Npc npc, final int who) {
        final Conversation c = new Conversation(getOwner(), npc);
        if (completed() || past(PASSED3)) {
            c.player("Hello there");
            if (cnt(who) > 0) {
                if (who == ORANGE) {
                    c.npc("Thanks a lot for finding my rock sample");
                    c.npc("See you again");
                } else {
                    c.npc("Thanks for your help, I'll pass these exams yet!");
                    c.npc("See you later");
                }
            } else {
                c.npc("I'm still studying for the earth sciences exam");
            }
            c.start();
            return;
        }
        if (!marked(TRIED1)) {
            c.player("Hello there");
            if (who == GREEN) {
                c.npc("Oh hi, i'm studying hard for an exam");
                c.player("What exam is that ?");
                c.npc("It's the earth sciences exam");
                c.player("Interesting...");
            } else if (who == ORANGE) {
                c.npc("Hello there, as you can see I am a student");
                c.player("What are you doing here ?");
                c.npc("Oh I'm studying for the earth sciences exam");
                c.player("Interesting...perhaps I should study it as well...");
            } else {
                c.npc("Hi there, I'm studying for the earth sciences exam");
                c.player("Interesting...This exam seems to be a popular one!");
            }
            c.start();
            return;
        }
        if (cnt(who) == 0) {
            if (holds(SAMPLE[who])) {
                handOverSample(c, who);
                c.start();
                return;
            }
            if (!marked(ASKED[who])) {
                askForSample(c, who);
                c.start();
                return;
            }
            noSampleYet(c, who);
            c.start();
            return;
        }
        /* The student has been paid in rock samples and now trades answers, one
         * per exam passed. The pink one charges an opal for her third. */
        int cap = 1 + (past(PASSED1) ? 1 : 0) + (past(PASSED2) ? 1 : 0);
        if (cnt(who) >= cap) {
            recite(c, who, cnt(who) - 1, false);
            c.start();
            return;
        }
        if (who == PINK && cap == 3 && !marked(OPAL_PAID)) {
            pinkWantsOpal(c);
            c.start();
            return;
        }
        setCnt(who, cnt(who) + 1);
        recite(c, who, cnt(who) - 1, true);
        c.start();
    }

    private void askForSample(Conversation c, int who) {
        c.player("Hello there");
        if (who == GREEN) {
            c.player("Can you help me with the earth sciences exams at all?");
            c.npc("Well...maybe I will if you help me with something");
            c.player("What's that ?");
            c.npc("I have lost my rock sample");
            c.player("What does it look like ?");
            c.npc("Err...like a rock!");
            c.player("Well that's not too helpful");
            c.player("Can you remember where you last had it ?");
            c.npc("It was around here for sure");
            c.npc("Maybe someone picked it up ?");
            c.player("Okay I'll have a look for you");
        } else if (who == ORANGE) {
            c.player("Can you help me with the earth science exams at all?");
            c.npc("I can't do anything unless I find my rock sample");
            c.player("Hey this rings a bell");
            c.npc("?");
            c.player("So if I find it you'll help me ?");
            c.npc("All I remember is that I was working near the tents when I lost it...");
            c.player("Okay I'll see what I can do");
        } else {
            c.player("Can you help me with the exams at all?");
            c.npc("I can if you help me...");
            c.player("How can I do that");
            c.npc("I have lost my rock sample");
            c.player("What you as well ?");
            c.npc("Err, yes it's gone somewhere");
            c.player("Do you know where you dropped it ?");
            c.npc("Well, I was doing a lot of walking that day...");
            c.npc("Oh yes, that's right...");
            c.npc("We were studying ceramics in fact");
            c.npc("I found some pottery...");
            c.npc("And it seemed to match the design that is on those large urns...");
            c.npc("...I was in the process of checking this out");
            c.npc("And when we got back to the centre...");
            c.npc("My rock sample had gone");
            c.player("Leave it to me, I'll find it");
            c.npc("Oh great!");
        }
        final int student = who;
        c.then(new Effect() {
            public void run(Conversation c) {
                mark(ASKED[student]);
            }
        });
    }

    private void noSampleYet(Conversation c, int who) {
        c.player("Hello there");
        c.player("How's the study going?");
        if (who == GREEN) {
            c.npc("Very well thanks");
            c.player("No sorry, not yet");
            c.npc("Oh well...");
            c.npc("I am sure it's been picked up");
            c.npc("Couldn't you try looking through some pockets ?");
        } else if (who == ORANGE) {
            c.npc("I'm getting there");
            c.npc("Have you found my rock sample yet ?");
            c.player("No sorry, not yet");
            c.npc("Oh dear, I hope it didn't fall into the stream");
            c.npc("I might never find it again...");
        } else {
            c.npc("Very well thanks");
            c.npc("Have you found my rock sample yet ?");
            c.player("No sorry, not yet");
            c.npc("I'm sure it's just outside the digsite somewhere...");
        }
    }

    private void handOverSample(Conversation c, final int who) {
        c.player("Hello there");
        if (who == GREEN) {
            c.player("Hi is this your rock sample ?");
            c.npc("Oh wow! you've found it!");
            c.npc("Thank you so much");
            c.npc("I'll be glad to tell you what I know about the exam");
        } else if (who == ORANGE) {
            c.player("Look what I found");
            c.npc("Excellent!");
            c.npc("I'm so happy");
            c.npc("Let me now help you with your exams...");
        } else {
            c.player("Guess what I found ?");
            c.npc("Hey! my sample!");
            c.npc("Thanks ever so much");
            c.npc("Let me help you with those questions now");
        }
        c.npc(LEAD[who][0]);
        c.npc(RECITE[who][0]);
        c.player(who == GREEN ? "Okay I'll remember that"
               : who == ORANGE ? "Thanks for the information"
                               : "Great, thanks for your advice");
        c.then(new Effect() {
            public void run(Conversation c) {
                take(SAMPLE[who]);
                setCnt(who, 1);
            }
        });
    }

    private void recite(Conversation c, int who, int index, boolean fresh) {
        c.player("Hello there");
        c.npc("How's it going ?");
        if (who == GREEN) {
            c.player("I need more help with the exam");
            c.npc("Well okay, this is what I have learned since I last spoke to you...");
        } else if (who == ORANGE) {
            c.player("There are more exam questions I'm stuck on");
            c.npc("Hey, I'll tell you what I've learned, that may help");
        } else {
            c.player("I am stuck on some more exam questions");
            c.npc("Okay, I'll tell you my latest notes...");
        }
        c.npc(LEAD[who][index]);
        c.npc(RECITE[who][index]);
        c.player(who == GREEN ? "Okay I'll remember that"
               : who == ORANGE ? "Thanks for the information"
                               : "Great, thanks for your advice");
    }

    /**
     * The pink student's third answer costs an opal. Vanilla has a documented
     * bug here -- speaking to her again after she has answered wipes the answer
     * and charges another opal for the same one -- which is deliberately not
     * reproduced: it destroys progress rather than adding anything.
     */
    private void pinkWantsOpal(Conversation c) {
        c.player("Hello there");
        if (!holds(OPAL) && !holds(UNCUT_OPAL)) {
            c.npc("How's it going ?");
            c.player("I am stuck on some more exam questions");
            c.npc("What, you want more help ?");
            c.player("Err... yes please!");
            c.npc("Well.. it's going to cost you...");
            c.player("Oh, well how much ?");
            c.npc("I'll tell you what I would like...");
            c.npc("A precious stone, I don't find many of these");
            c.npc("My favourite is an opal, they are beautiful");
            c.npc("...Just like me");
            c.npc("Tee hee hee !");
            c.player("I'll see if I can find one");
            return;
        }
        c.npc("Oh hi again");
        c.npc("Did you bring me that opal ?");
        c.player("would that opal look like this by any chance ?");
        c.npc("Wow, great you've found one");
        c.npc("This will look beautiful set in my necklace");
        c.npc("Thanks for that, now I'll tell you what I know...");
        c.npc(LEAD[PINK][2]);
        c.npc(RECITE[PINK][2]);
        c.player("Great, thanks for your advice");
        c.then(new Effect() {
            public void run(Conversation c) {
                // Sources disagree about which opal she wants -- the quest's
                // own item list says uncut, the level 1 dig table drops the cut
                // one -- so either is accepted, cut first.
                take(holds(OPAL) ? OPAL : UNCUT_OPAL);
                mark(OPAL_PAID);
                setCnt(PINK, 3);
            }
        });
    }

    // ------------------------------------------------------------ curator --

    private void curatorTalk(Npc npc) {
        /* Shield of Arrav owns the curator's conversation; this quest only ever
         * has things used on him, so NPC_TALK is left alone. */
    }

    private void curatorItem(final Npc npc, InvItem used) {
        final Conversation c = new Conversation(getOwner(), npc);
        int id = used.getID();
        if (id == LETTER_PLAIN) {
            c.player("I have been given this by the examiner at the digsite");
            c.player("Can you stamp this for me ?");
            c.npc("What have we here ?");
            c.npc("A letter of recommendation indeed");
            c.npc("Normally I wouldn't do this");
            c.npc("But in this instance I don't see why not");
            c.npc("There you go, good luck student...");
            c.npc("Be sure to come back and show me your certificates");
            c.npc("I would like to see how you get on");
            c.player("Okay, I will, thank, see you later");
            c.then(new Effect() {
                public void run(Conversation c) {
                    take(LETTER_PLAIN);
                    give(LETTER_STAMPED);
                }
            });
            c.start();
            return;
        }
        if (id == LETTER_STAMPED) {
            c.npc("No, I don't want it back, thankyou");
            c.start();
            return;
        }
        if (id == CERT1 || id == CERT2) {
            c.player(id == CERT1 ? "Look what I have been awarded"
                                 : "Look, I am level 2 now...");
            c.npc(id == CERT1 ? "Well that's great, well done" : "Excellent work!");
            c.npc("I'll take that for safekeeping");
            c.npc(id == CERT1 ? "Come and tell me when you are the next level"
                              : "Remember to come and see me when you have graduated");
            final int cert = id;
            c.then(new Effect() {
                public void run(Conversation c) {
                    take(cert);
                }
            });
            c.start();
            return;
        }
        if (id == CERT3) {
            c.player("Look at this certificate, curator...");
            c.npc("Well well, a level 3 graduate!");
            c.npc("I will keep your certificate safe for you");
            c.npc("I feel I must reward you for your work...");
            c.npc("What would you prefer, something to eat or drink ?");
            c.picker(new Choice("Something to eat please", "Something to drink please") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.player("Something to drink please");
                        c.npc("Certainly, have this...");
                        c.player("A cocktail ?");
                        c.npc("It's a new recipie from the gnome kingdom");
                        c.npc("You'll like it I'm sure");
                        c.player("Cheers!");
                        c.npc("Cheers!");
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                take(CERT3);
                                give(FRUIT_BLAST);
                            }
                        });
                        return;
                    }
                    c.player("Something to eat please");
                    c.npc("Very good, come and eat this cake I baked");
                    c.player("Yum, thanks!");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            take(CERT3);
                            give(CHOCOLATE_SLICE);
                        }
                    });
                }
            });
            c.start();
            return;
        }
        offer(npc, "I'm only interested in old stuff");
    }

    // -------------------------------------------------------------- guide --

    private void guide(final Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        c.player("Hello, who are you ?");
        c.npc("Hello, I am the panning guide");
        c.npc("I'm here to teach you how to pan for gold");
        c.player("Excellent!");
        c.npc("Let me explain how panning works...");
        c.npc("First You need a panning tray");
        c.npc("Use the tray in the panning points in the water");
        c.npc("Then examine your tray");
        c.npc("If you find any gold, take it to the expert");
        c.npc("Up in the museum storage facility");
        c.npc("He will calculate it's value for you");
        c.player("Okay thanks");
        c.start();
    }

    /** The guide stops anyone panning who has not bought him a cup of tea. */
    private void guideRefuses() {
        Player p = getOwner();
        Npc guide = world.getNpc(GUIDE, p.getX() - 12, p.getX() + 12,
                                 p.getY() - 12, p.getY() + 12);
        if (guide == null) {
            say("The panning guide will not let you pan here yet");
            return;
        }
        Conversation c = new Conversation(p, guide);
        c.npc("Hey! you can't pan yet!");
        c.player("Why not ?");
        c.npc("We do not allow the uninvited to pan here");
        c.picker(new Choice("So how do I become invited then ?", "Okay, forget it") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("Okay, forget it");
                    c.npc("You can of course use this place when you know what you are doing");
                    return;
                }
                c.player("So how do I become invited then ?");
                c.npc("I'm not supposed to let people pan here");
                c.npc("Unless they have permission from the authorities first");
                c.npc("Mind you I could let you have a go...");
                c.npc("If you're willing to do me a favour");
                c.player("What's that ?");
                c.npc("Well...to be honest...");
                c.npc("What I would really like...");
                c.npc("Is a nice cup of tea !");
                c.player("Tea !?");
                c.npc("Absolutely, I'm parched !");
                c.npc("If you could bring me on of those...");
                c.npc("I would be more than willing to let you pan here");
            }
        });
        c.start();
    }

    // ------------------------------------------------------------ workmen --

    private void workman(final Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        if (completed()) {
            c.npc("Ah it's the great archaeologist!");
            c.npc("Congratulations on your discovery");
            c.start();
            return;
        }
        c.player("Hello there");
        c.npc("Good day, what can I do for you ?");
        c.picker(new Choice("What do you do here ?", "Can I dig around here ?",
                             "I'm not sure...") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("Can I dig around here ?");
                    c.npc("You can only use the site you have the appropriate exam level for");
                    c.picker(new Choice("Appropriate exam level ?", "Oh, okay") {
                        public void picked(int option, Conversation c) {
                            if (option == 1) {
                                c.player("Oh, okay");
                                return;
                            }
                            c.player("Appropriate exam level ?");
                            c.npc("Yes, only persons with the correct certificate of earth sciences can dig here");
                            c.npc("A level 1 certificate will let you dig in a level 1 site and so on...");
                            c.player("oh, okay I understand");
                            c.picker(new Choice("I am already skilled in digging", "Thanks") {
                                public void picked(int option, Conversation c) {
                                    if (option == 1) {
                                        c.player("Thanks");
                                        return;
                                    }
                                    c.player("I am already skilled in digging");
                                    c.npc("Well that's nice for you...");
                                    c.npc("You can't dig around here without a certificate though");
                                }
                            });
                        }
                    });
                    return;
                }
                if (option == 2) {
                    c.player("I'm not sure...");
                    c.npc("Well, let me know when you are");
                    return;
                }
                c.player("What do you do here ?");
                c.npc("I am involved in various stages of the dig");
                c.npc("From the initial investigation to the installation of the mine shafts");
                c.player("Oh okay, thanks");
            }
        });
        c.start();
    }

    private void workmanDown(final Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        c.player("Hello");
        c.npc("Well well...");
        c.npc("I have a visitor");
        c.npc("What are you doing here ?");
        c.picker(new Choice("I have been invited to research here",
                             "I am not sure really",
                             "I'm here to get rich rich rich!") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("I am not sure really");
                    c.npc("A miner without a clue - how funny");
                    return;
                }
                if (option == 2) {
                    c.player("I'm here to get rich rich rich!");
                    c.npc("Oh, well don't forget what wealth and riches isn't everything...");
                    return;
                }
                c.player("I have been invited to research here");
                c.npc("Indeed you must be someone special to be allowed down here...");
                keyMenu(c);
            }
        });
        c.start();
    }

    private void keyMenu(Conversation c) {
        c.picker(new Choice("Do you know where to find a specimen jar ?",
                             "Do you know where to find a chest key") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.player("Do you know where to find a specimen jar ?");
                    c.npc("Hmmm, let me think...");
                    c.npc("Nope, can't help you there i'm afraid");
                    return;
                }
                c.player("Do you know where to find a chest key");
                c.npc("Yes I might have one...");
                beg(c, 0);
            }
        });
    }

    private static final String[] BEG_ASK = {
        "I don't suppose I could use it ?", "Please", "Aww...go on",
        "Pretty please!", "Pretty please with sugar on top!",
    };
    private static final String[] BEG_REPLY = {
        "Aww, but I need it...", "I am not so sure about this...",
        "Hmmm...well I don't know", "You are trying to change my mind", "",
    };

    private void beg(Conversation c, final int step) {
        if (step >= BEG_ASK.length) {
            c.npc("All right, all right!");
            c.npc("Stop begging I can't stand it.");
            c.npc("Here's the key...take care of it");
            c.player("Thanks");
            c.then(new Effect() {
                public void run(Conversation c) {
                    mark(GOT_KEY);
                    give(CHEST_KEY);
                }
            });
            return;
        }
        String[] opts = step == 0
            ? new String[] { BEG_ASK[0], "Can I buy it from you ?", "Hey that's my key!" }
            : new String[] { BEG_ASK[step], "Never mind" };
        final int here = step;
        /* picker() rather than options(): every branch below already speaks the
           line it was picked from, including the BEG_ASK entry the fall-through
           uses. options() would echo the label first and say all of it twice. */
        c.picker(new Choice(opts) {
            public void picked(int option, Conversation c) {
                if (here == 0 && option == 1) {
                    c.player("Can I buy it from you ?");
                    c.npc("Ooo no, I need it!");
                    return;
                }
                if (here == 0 && option == 2) {
                    c.player("Hey that's my key!");
                    c.npc("You don't think im going to fall for that do you ?");
                    c.npc("Get lost!");
                    return;
                }
                if (here > 0 && option == 1) {
                    c.player("Never mind");
                    return;
                }
                c.player(BEG_ASK[here]);
                if (BEG_REPLY[here].length() > 0) {
                    c.npc(BEG_REPLY[here]);
                }
                if (here == 3) {
                    c.player("Of course!");
                }
                beg(c, here + 1);
            }
        });
    }

    /**
     * Pickpocketing a workman. This is the only source of the green student's
     * rock sample and of the specimen brush, and it is also where the two ropes
     * for the winches come from.
     */
    private void pickpocket(Npc npc) {
        if (npc.getID() == WORKMAN_DOWN) {
            Conversation c = new Conversation(getOwner(), npc);
            c.npc("Hey! trying to steal from me are you ?");
            c.npc("What do you think I am - stupid or something !?");
            c.player("Err...sorry");
            c.start();
            return;
        }
        if (npc.getID() != WORKMAN) {
            return;
        }
        if (DataConversions.random(0, 3) == 0) {
            offer(npc, "Hey what do you think you're doing");
            say("You fail to pick the workman's pocket");
            npc.attackPlayer(getOwner());
            return;
        }
        getOwner().incExp(THIEVING, 22, true);
        getOwner().getActionSender().sendStat(THIEVING);
        if (marked(TRIED1) && cnt(GREEN) == 0 && !holds(SAMPLE[GREEN])) {
            say("You find a rock sample in the workman's pocket");
            give(SAMPLE[GREEN]);
            return;
        }
        if (past(PASSED3) && !marked(GOT_BRUSH) && !holds(BRUSH)) {
            say("You find a specimen brush in the workman's pocket");
            mark(GOT_BRUSH);
            give(BRUSH);
            return;
        }
        switch (DataConversions.random(0, 3)) {
            case 0:
                say("You find a coil of rope in the workman's pocket");
                give(ROPE);
                return;
            case 1:
                say("You steal some coins");
                give(COINS, DataConversions.random(3, 12));
                return;
            default:
                say("You find nothing in the workman's pocket");
                return;
        }
    }

    // ------------------------------------------------------------- expert --

    private void expert(final Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        if (completed()) {
            c.npc("Hello again");
            c.npc("I am now studying this mysterious altar and its inhabitants");
            c.npc("The markings are strange, but it refers to a god I have never");
            c.npc("heard of before named Zaros. It must be some pagan superstition.");
            c.npc("That was a great find, who knows what other secrets");
            c.npc("Lie buried beneath the surface of our land...");
            c.start();
            return;
        }
        c.player("Hello, who are you ?");
        c.npc("Good day to you");
        c.npc("My name is Terry balando");
        c.npc("I am an expert on digsite finds");
        c.npc("I am employed by the museum in varrock");
        c.npc("To oversee all finds in this digsite");
        c.npc("Anything you find must be reported to me");
        c.player("Oh, okay if I find anything of interest I will bring it here");
        c.npc("Very good");
        c.npc("Can I help you at all ?");
        c.picker(new Choice("I have something I need checking out", "No thanks",
                             "Can you tell me anything about the digsite?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("No thanks");
                    c.npc("Good, let me know if you find anything unusual");
                    return;
                }
                if (option == 2) {
                    c.player("Can you tell me anything about the digsite ?");
                    c.npc("Yes indeed, I am currently studying the lives of the settlers");
                    c.npc("During the end of the third age, this used to be a great city");
                    c.npc("It's inhabitants were humans, supporters of the god Saradomin");
                    c.npc("It's not recorded what happened to the community here");
                    c.npc("I suspect nobody has lived here for over a millennium!");
                    return;
                }
                c.player("I have something I need checking out");
                c.npc("Okay, give it to me and I'll have a look for you");
            }
        });
        c.start();
    }

    private void expertItem(final Npc npc, InvItem used) {
        final Conversation c = new Conversation(getOwner(), npc);
        final int id = used.getID();
        switch (id) {
            case TABLET:
                if (completed()) {
                    c.npc("I don't need another tablet");
                    c.npc("One is enough thank you!");
                    break;
                }
                if (!past(BLASTED)) {
                    c.npc("Where on earth did you get this ?");
                    break;
                }
                c.player("I found this in a hidden cavern beneath the digsite");
                c.npc("Incredible!");
                c.player("There is an altar down there");
                c.player("The place is crawling with skeletons!");
                c.npc("Yuck!");
                c.npc("This is an amazing discovery!");
                c.npc("All this while we were convinced...");
                c.npc("That no other race had lived here");
                c.npc("It seems the followers of Saradomin");
                c.npc("Have tried to cover up the evidence of the zaros altar");
                c.npc("This whole city must have been built over it!");
                c.npc("Thanks for your help");
                c.npc("Your sharp eyes have spotted what many have missed...");
                c.npc("Here, take this as your reward");
                c.message("The expert gives you 2 gold bars as payment");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        finish();
                    }
                });
                break;
            case TALISMAN:
                c.player("What about this ?");
                c.npc("Unusual...");
                c.npc("This object doesn't appear right...");
                c.npc("Hmmmm....");
                c.npc("I wonder...Let me check my guide...");
                c.npc("From the markings on it it seems to be");
                c.npc("a ceremonial ornament to a god named...");
                c.npc("Zaros? I have never heard of him before");
                c.npc("This is a great discovery, we know very little");
                c.npc("of the pagan gods that people worshipped");
                c.npc("in the olden days. There is some strange writing");
                c.npc("embossed upon it - it says");
                c.npc("'Zaros will return and wreak his vengeance");
                c.npc("upon Zamorak this pretender' - I wonder what");
                c.npc("it means by that? Some silly superstition probably.");
                c.npc("Still, I wonder what this is doing around here...");
                c.npc("I'll tell you what, as you have found this");
                c.npc("I will allow you to use the private dig shaft");
                c.npc("You obviously have a keen eye...");
                c.npc("Take this letter and give it to one of the workmen");
                c.npc("And they will allow you to use it");
                c.message("The expert hands you a letter");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        take(TALISMAN);
                        give(SCROLL);
                        if (!completed() && at(GOT_TALISMAN)) {
                            step(GOT_SCROLL);
                        }
                    }
                });
                break;
            case POWDER:
                c.player("Do you know what this power is ?");
                c.npc("Really you do find the most unusual items");
                c.npc("I know what this is...");
                c.npc("It's called ammonium nitrate - A strong chemical");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        take(POWDER);
                        give(NITRATE);
                    }
                });
                break;
            case LIQUID:
                c.player("Do you know what this is?");
                c.npc("Where did you get this ?");
                c.player("From one of the barrels at the digsite");
                c.npc("This is a dangerous liquid called nitroglycerin");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        take(LIQUID);
                        give(NITRO);
                        // A fresh phial starts a fresh mixture.
                        unmark(CH_NITRATE | CH_CHARCOAL | CH_ROOT);
                    }
                });
                break;
            case NUGGETS:
                c.player("I have these gold nuggets");
                if (count(NUGGETS) < 3) {
                    c.npc("I can't do much with these nuggets yet");
                    c.npc("Come back when you have 3, and I will exchange them with you");
                    c.player("Okay I will, thanks");
                    break;
                }
                c.message("You give the nuggets to the expert");
                c.npc("Good, that's 3, I can exchange them for normal gold now");
                c.npc("You can get this refined and make a profit!");
                c.player("Excellent!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        take(NUGGETS, 1);
                        take(NUGGETS, 1);
                        take(NUGGETS, 1);
                        give(GOLD_ORE);
                    }
                });
                break;
            case BELT_BUCKLE:
                c.player("Have a look at this unusual item");
                c.npc("Let me see..");
                c.npc("This is a belt buckle");
                c.npc("I should imagine it came from a guard");
                break;
            case BONES:
                c.player("Have a look at these bones");
                c.npc("Ah yes, a fine bone example");
                c.npc("No noticeable fractures, and in good condition");
                c.npc("There are common cow bones however");
                c.npc("They have no archaeological value");
                break;
            case BROKEN_ARROW:
                c.player("Have a look at this arrow");
                c.npc("No doubt this arrow was shot by a strong warrior");
                c.npc("It's split in half!");
                c.npc("It is not a valuable object though...");
                break;
            case BROKEN_GLASS_1:
            case BROKEN_GLASS_2:
                c.player("Have a look at this glass");
                c.npc("Hey you should be careful of that");
                c.npc("It might cut your fingers, throw it away!");
                break;
            case BROKEN_STAFF:
                c.player("Have a look at this staff");
                c.npc("Look at this...interesting");
                c.npc("This appears to belong to a cleric of some kind");
                c.npc("Certainly not a follower of saradomin however...");
                c.npc("I wonder if there was another civilization before the saradominists ?");
                break;
            case BUTTONS:
                c.player("I found these buttons");
                c.npc("Let's have a look");
                c.npc("Ah, I think these are from the nobility");
                c.npc("Perhaps a royal servant ?");
                c.npc("Not valuable but an unusual find for this area");
                break;
            case CERAMIC:
                c.player("I found some potery pieces");
                c.npc("Yes many parts are discovered");
                c.npc("The inhabitants of these parts were great potters...");
                c.player("You mean they were good at using potions ?");
                c.npc("No no silly - they were known for their skill with clay");
                break;
            case CRACKED_SAMPLE:
                c.player("I found this rock...");
                c.npc("What a shame it's cracked, this looks like it would have been a good sample");
                break;
            case ARMOUR_A:
                c.player("I found some old armour");
                c.npc("Hmm...unusual");
                c.npc("This armour dosen't seem to match with the other finds");
                c.npc("keep looking, this could be evidence of an older civilization!");
                break;
            case ARMOUR_B:
                c.player("I found some armour");
                c.npc("It looks like the wearer of this fought a mighty battle");
                break;
            case COMPOUND:
                c.player("What do you think about this ?");
                c.npc("What have you concocted now ?");
                c.npc("Just be careful when playing with chemicals...");
                break;
            case MIX2:
                c.player("Hey, look at this");
                c.npc("Hmmm, that looks dangerous...");
                c.npc("Handle it carefully and don't drop it!");
                break;
            case MIX3:
                c.player("See what I have done with the compound now");
                c.npc("Seriously, I think you have a death wish!");
                c.npc("What on earth are you going to do with that stuff ?");
                c.player("I'll find a use for it");
                break;
            case NEEDLE:
                c.player("found a needle");
                c.npc("Hmm yes, I wondered why this race were so well dressed!");
                c.npc("It looks like they had a mastery of needlework");
                break;
            case NITRO:
                c.player("Can you tell me any more about this ?");
                c.npc("nitroglycerin...this is a dangerous substance");
                c.npc("This is normally mixed with other chemicals");
                c.npc("To produce a potent compound...");
                c.npc("Be sure not to drop it!");
                c.npc("That stuff is highly volatile...");
                break;
            case OLD_BOOT:
                c.player("Have a look at this");
                c.npc("Ah yes, an old boot");
                c.npc("Not really an ancient artifact is it?");
                break;
            case OLD_TOOTH:
                c.player("Hey look at this");
                c.npc("Oh, an old tooth");
                c.npc("..It looks like it has come from a mighty being");
                break;
            case TRAY_EMPTY:
                c.npc("I have no need for panning trays");
                break;
            case TRAY_MUD:
                c.npc("Have you searched this tray yet?");
                c.player("Not that I remember");
                c.npc("It may contain something, I don't want to get my hands dirty");
                c.message("The expert hands the tray back to you");
                break;
            case TRAY_GOLD:
                c.npc("Did you realize there is something in this tray ?");
                c.player("Err, not really");
                c.npc("Check it out thoroughly first");
                c.message("The expert hands you back the tray");
                break;
            case SCROLL:
                c.npc("There's no point in giving me this back!");
                break;
            case ROTTEN_APPLES:
                c.player("I found these...");
                c.npc("Ew! throw them away this instant!");
                break;
            case RUSTY_SWORD:
                c.player("I found an old sword");
                c.npc("Oh, its very rusty isn't it ?");
                c.npc("I'm not sure this sword belongs here");
                c.npc("It looks very out of place...");
                break;
            case VASE:
                c.player("I found a case");
                c.npc("Ah yes these are commonly found in these parts");
                c.npc("Not a valuable item");
                break;
            default:
                if (id == SAMPLE[GREEN] || id == SAMPLE[ORANGE] || id == SAMPLE[PINK]) {
                    c.player("Have a look at this rock");
                    c.npc("This rock is not naturally formed");
                    c.npc("It looks like it might belong to someone...");
                    break;
                }
                c.player("What do you make of this ?");
                c.npc("That is of no interest to me I'm afraid");
                break;
        }
        c.start();
    }

    private void finish() {
        take(TABLET);
        // Everything but the guide's cup of tea has to be gone: the quest is
        // complete only on an exact match against a final stage.
        // setStage() sees the final stage and calls completeQuest() itself,
        // which pays the declared rewards; calling it here as well would pay
        // them twice.
        setStage(FINISHED | (getStage() & TEA));
    }

    // ------------------------------------------------------- item on npc --

    private void itemOnNpc(Npc npc, InvItem used) {
        if (used == null) {
            return;
        }
        switch (npc.getID()) {
            case CURATOR:
                curatorItem(npc, used);
                return;
            case EXPERT:
                expertItem(npc, used);
                return;
            case GUIDE:
                if (used.getID() == CUP_OF_TEA) {
                    Conversation c = new Conversation(getOwner(), npc);
                    c.npc("Ah! Lovely!");
                    c.npc("You can't beat a good cuppa...");
                    c.npc("You're free to pan all you want");
                    c.player("Thanks");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            take(CUP_OF_TEA);
                            mark(TEA);
                        }
                    });
                    c.start();
                    return;
                }
                offer(npc, "No thank you");
                return;
            case WORKMAN:
                if (used.getID() == SCROLL) {
                    scrollToWorkman(npc);
                    return;
                }
                offer(npc, "No thanks, I've got work to do");
                return;
            default:
                return;
        }
    }

    private void scrollToWorkman(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("Here, have a look at this...");
        c.npc("I give permission...blah de blah etc...");
        c.npc("Okay that's all in order, you may use the mineshafts now");
        c.npc("I'll have onto this scroll shall i ?");
        c.player("Thanks");
        c.then(new Effect() {
            public void run(Conversation c) {
                take(SCROLL);
                if (completed()) {
                    mark(POST_PERMIT);
                } else if (at(GOT_SCROLL)) {
                    step(PERMIT);
                }
            }
        });
        c.start();
    }

    // --------------------------------------------------------- item usage --

    private void command(int id) {
        switch (id) {
            case BOOK:
                say("Volatile chemicals - Notes on experimental chemistry.");
                say("In order to ease the mining Process, my colleagues and I");
                say("decided we needed something stronger than picks to delve");
                say("under the digsite. As I already had an intermediate knowledge");
                say("of herblaw, I experimented on certain chemicals, and invented");
                say("a compound of tremendous power, which, if subjected to a spark");
                say("would literally explode. We used vials of this compound with");
                say("great results, as it enabled us to reach further than ever before.");
                say("Here is what I have left of the compound's recipe:");
                say("1 measure of ammonium nitrate powder,");
                say("1 measure of nitroglycerin,");
                say("1 measure of ground charcoal.");
                say("1 measure of ?");
                say("Unfortunately the last ingredient was not written down, but we");
                say("understand that a certain root grows around these parts that");
                say("was used to very good effect...");
                return;
            case CERT1:
            case CERT2:
            case CERT3:
                say("It says:");
                say("The holder of this certificate has passed the level "
                        + (id - CERT1 + 1) + " exam in earth sciences");
                return;
            case SCROLL:
                say("It says 'I give permission for the bearer to use the mineshafts on site");
                say("Signed Terrance Balando, Archaeological expert, City of Varrock");
                return;
            case TABLET:
                say("It says:");
                say("Tremble mortal, before the altar of our dread lord zaros");
                return;
            case TRAY_EMPTY:
                say("You search the tray");
                say("There is nothing in it");
                return;
            case TRAY_MUD:
                searchMud();
                return;
            case TRAY_GOLD:
                say("You take the gold nuggets out of the tray");
                take(TRAY_GOLD);
                give(TRAY_EMPTY);
                give(NUGGETS);
                return;
            default:
                return;
        }
    }

    private void searchMud() {
        say("You search through the mud in your tray");
        int found = roll(LOOT_PAN);
        take(TRAY_MUD);
        if (found == NUGGETS) {
            say("You find some gold nuggets!");
            give(TRAY_GOLD);
            return;
        }
        give(TRAY_EMPTY);
        if (found < 0) {
            say("You find nothing but plain mud");
            return;
        }
        if (found == COINS) {
            int amount = DataConversions.random(0, 3);
            amount = amount == 0 ? 1 : amount == 1 ? 2 : amount == 2 ? 5 : 10;
            say("You find some coins in the mud");
            give(COINS, amount);
            return;
        }
        if (found == SAMPLE[ORANGE]) {
            if (!marked(TRIED1) || cnt(ORANGE) > 0 || holds(SAMPLE[ORANGE])) {
                say("You find nothing but plain mud");
                return;
            }
            say("You find a rock sample in the mud");
            give(SAMPLE[ORANGE]);
            return;
        }
        say("You find something in the mud");
        give(found);
    }

    /**
     * The four chemicals. Nitroglycerin takes the other three in any order,
     * gaining a name each time; which ones have gone in is remembered in bits,
     * because the item id only records how many.
     */
    private void pair(int a, int b) {
        int base = -1, added = -1;
        if (a == NITRO || a == MIX2 || a == MIX3) {
            base = a;
            added = b;
        } else if (b == NITRO || b == MIX2 || b == MIX3) {
            base = b;
            added = a;
        }
        if (base < 0) {
            return;
        }
        int bit;
        if (added == NITRATE) {
            bit = CH_NITRATE;
        } else if (added == GROUND_CHARCOAL) {
            bit = CH_CHARCOAL;
        } else if (added == ROOT) {
            bit = CH_ROOT;
        } else {
            return;
        }
        if (marked(bit)) {
            say("You have already added that to the mixture");
            return;
        }
        if (getOwner().getCurStat(HERBLAW) < 10) {
            say("You need a herblaw level of 10 to mix these chemicals");
            return;
        }
        mark(bit);
        take(added);
        take(base);
        int have = (marked(CH_NITRATE) ? 1 : 0) + (marked(CH_CHARCOAL) ? 1 : 0)
                 + (marked(CH_ROOT) ? 1 : 0);
        if (have >= 3) {
            say("You mix in the last ingredient");
            say("You have made an explosive compound");
            give(COMPOUND);
            unmark(CH_NITRATE | CH_CHARCOAL | CH_ROOT);
            return;
        }
        say("You add the ingredient to the mixture");
        give(have == 1 ? MIX2 : MIX3);
    }

    // ------------------------------------------------------ item on object --

    private void itemOnObject(int id, GameObject object, InvItem used) {
        int item = used.getID();
        switch (id) {
            case SOIL_A: case SOIL_B: case SOIL_C:
                dig(object, item);
                return;
            case SKELETON_N: case SKELETON_W:
                if (item == TROWEL) {
                    scold(new String[] { "Hey! that's fragile!",
                                         "Stop poking it around with that trowel!" });
                    say("Oh okay, sorry");
                    return;
                }
                return;
            case SPECIMEN_TRAY:
                if (item == SPADE) {
                    scold(new String[] { "Oi! what do you think you are doing ?",
                        "Don't you realize there are fragile specimens around here ?" });
                    return;
                }
                if (item == TROWEL) {
                    scold(new String[] { "Excuse me...",
                                         "No digging in the specimen trays please" });
                    return;
                }
                if (item == BRUSH) {
                    searchTray();
                }
                return;
            case PANNING_POINT:
                if (item == TRAY_EMPTY) {
                    pan();
                } else if (item == TRAY_MUD || item == TRAY_GOLD) {
                    say("You should empty your tray first");
                }
                return;
            case BARREL_SHUT:
                if (item == TROWEL) {
                    say("Great, it's opened it!");
                    mark(BARREL_PRISED);
                    say("You search the barrel");
                    say("The barrel has a foul-smelling liquid inside...");
                    say("I can't pick this up with my bare hands!");
                    say("I'll need something to put it in");
                    return;
                }
                if (item == VIAL) {
                    if (!marked(BARREL_PRISED)) {
                        say("The barrel has a lid on it");
                        say("I need something to prise it open with");
                        return;
                    }
                    fillVial();
                }
                return;
            case BARREL_OPEN:
                if (item == VIAL) {
                    fillVial();
                }
                return;
            case CHEST_POWDER:
            case CHEST_POWDER_OPEN:
                if (item == CHEST_KEY) {
                    powderChest();
                }
                return;
            case WINCH_NE:
                if (item == ROPE) {
                    tieRope(true);
                }
                return;
            case WINCH_WEST:
                if (item == ROPE) {
                    tieRope(false);
                }
                return;
            case BRICKS:
            case BRICKS_PRIMED:
                bricks(item);
                return;
            default:
                return;
        }
    }

    private void fillVial() {
        say("You fill the vial with the liquid");
        say("You close the barrel");
        take(VIAL);
        give(LIQUID);
        say("I'm not sure what this stuff is");
        say("I had better be very careful with it");
        say("I had better not spill any I think...");
    }

    // ------------------------------------------------------------ digging --

    private void dig(GameObject soil, int tool) {
        Player p = getOwner();
        int level = siteLevel(soil.getX(), soil.getY());
        if (level < 0) {
            return;
        }
        if (tool == SPADE) {
            scold(new String[] { "Oi! dont use that spade!",
                "What are you trying to do, destroy everything of value ?" });
            return;
        }
        if (tool != TROWEL && tool != ROCK_PICK) {
            return;
        }
        if (level == 2 && tool == TROWEL) {
            scold(new String[] { "Sorry, you must use a rockpick",
                                 "To dig in a level 2 site..." });
            return;
        }
        if (level != 2 && tool == ROCK_PICK) {
            scold(new String[] { "No no, rockpicks should only be used",
                                 "To dig in a level 2 site..." });
            return;
        }
        if (level >= 1 && !past(PASSED1 + level - 1)) {
            scold(new String[] { "Sorry, you can't dig here",
                "You need a level " + level + " certificate of earth sciences" });
            return;
        }
        if (level == 1) {
            if (!wears(GLOVES)) {
                scold(new String[] { "Hey, where are your gloves ?" });
                say("Err...I haven't got any");
                offerNearby("Well get some and put them on first!");
                return;
            }
            if (!wears(BOOTS)) {
                scold(new String[] { "Oi, no boots!", "No boots no digging!" });
                return;
            }
        }
        if (level == 3) {
            if (!holds(JAR)) {
                scold(new String[] { "Ahem! I don't see your sample jar",
                                     "You must carry one to be able to dig here..." });
                say("Oh, okay");
                return;
            }
            if (!holds(BRUSH)) {
                scold(new String[] { "Wait just a minute!", "I can't let you dig here",
                    "Unless you have a specimen brush with you", "Rules is rules!" });
                return;
            }
        }
        say("You dig into the soil...");
        int[] table = level == 0 ? LOOT_TRAINING
                    : level == 1 ? LOOT_L1
                    : level == 2 ? LOOT_L2 : LOOT_L3;
        int exp = level == 0 ? 13 : level == 1 ? 15 : level == 2 ? 18 : 20;
        p.incExp(MINING, exp, true);
        p.getActionSender().sendStat(MINING);

        if (level == 3) {
            /* The talisman comes out of a level 3 site. While the quest is
             * waiting on it the odds are generous, because there is nothing
             * else for the player to do; afterwards it is a rare find, which is
             * how a finished player buys the mine shafts back. */
            boolean wanted = at(PASSED3);
            if (DataConversions.random(0, wanted ? 3 : 15) == 0) {
                say("You find an unusual ornament");
                give(TALISMAN);
                if (wanted) {
                    step(GOT_TALISMAN);
                }
                return;
            }
        }
        int found = roll(table);
        if (found < 0) {
            say("You find nothing");
            return;
        }
        if (found == COINS) {
            int amount = level == 0 ? 1 : DataConversions.random(0, 1) == 0 ? 5 : 10;
            say("You find some coins");
            give(COINS, amount);
            return;
        }
        say("You find something buried in the soil");
        give(found);
    }

    private void offerNearby(String line) {
        Npc workman = nearbyWorkman();
        if (workman == null) {
            say(line);
            return;
        }
        offer(workman, line);
    }

    // ----------------------------------------------------------- scenery --

    private void bookcase() {
        say("You search the bookcase");
        if (marked(GOT_BOOK) || holds(BOOK)) {
            say("You find nothing of interest");
            return;
        }
        say("You find a book of experimental chemistry");
        mark(GOT_BOOK);
        give(BOOK);
    }

    private void cupboard() {
        say("You open the cupboard");
        if (marked(GOT_PICK) || holds(ROCK_PICK)) {
            say("You find nothing");
            say("You close the cupboard");
            return;
        }
        say("You find a rock pick");
        say("You close the cupboard");
        mark(GOT_PICK);
        give(ROCK_PICK);
    }

    private void examCentreChest() {
        say("You open the chest");
        say("You search it...");
        if (marked(GOT_CRACKED) || holds(CRACKED_SAMPLE)) {
            say("You find nothing");
            return;
        }
        say("You find a cracked rock sample");
        mark(GOT_CRACKED);
        give(CRACKED_SAMPLE);
    }

    private void sampleBush() {
        say("You search the bush");
        if (!marked(TRIED1) || cnt(PINK) > 0 || holds(SAMPLE[PINK])) {
            say("You find nothing");
            return;
        }
        say("You find a rock sample in the bush");
        give(SAMPLE[PINK]);
    }

    private void jarSacks() {
        say("You search the sacks");
        if (marked(GOT_JAR) || holds(JAR)) {
            say("You find nothing");
            return;
        }
        say("You find a specimen jar");
        mark(GOT_JAR);
        give(JAR);
    }

    private void searchTray() {
        Player p = getOwner();
        if (!holds(JAR)) {
            Npc workman = nearbyWorkman();
            if (workman == null) {
                say("You need a specimen jar to handle what is in the tray");
                return;
            }
            Conversation c = new Conversation(p, workman);
            c.npc("Oi! what are you doing ?");
            c.picker(new Choice("I am searching this tray", "I am on an errand") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.player("I am on an errand");
                        c.npc("Oh yeah? and whose errand is that then...");
                        c.npc("Where is your specimen jar then?");
                        c.player("Oh I don't have one");
                        c.npc("And you reckon you have been sent on an errand...");
                        c.npc("Without a specimen jar - no sorry I can't let you do that!");
                        return;
                    }
                    c.player("I am searching this tray");
                    c.npc("Oh you are, are you ?");
                    c.npc("Well, where's your specimen jar?");
                    c.player("Ah, I don't have one...");
                    c.npc("In that case how can you handle the specimens without it?");
                    c.npc("As you should know, specimens are to be kept in sealed specimen jars");
                    c.npc("To keep them safe and preserved...");
                    c.npc("Next time bring it along!");
                }
            });
            c.start();
            return;
        }
        if (!holds(BRUSH)) {
            scold(new String[] { "Wait just a minute!", "I can't let you search that",
                "Unless you have a specimen brush with you", "Rules is rules!" });
            return;
        }
        say("You brush through the sifted earth");
        p.incExp(MINING, 1, true);
        p.getActionSender().sendStat(MINING);
        int found = roll(LOOT_TRAY);
        if (found < 0) {
            say("You find nothing");
            return;
        }
        if (found == COINS) {
            say("You find a coin");
            give(COINS);
            return;
        }
        say("You find something in the tray");
        give(found);
    }

    private void openBarrel() {
        say("You search the barrel");
        say("The barrel has a foul-smelling liquid inside...");
        say("I can't pick this up with my bare hands!");
        say("I'll need something to put it in");
    }

    private void powderChest() {
        if (!holds(CHEST_KEY)) {
            say("The chest is locked");
            say("I need a key for this");
            return;
        }
        say("You unlock the chest");
        say("You search it...");
        if (marked(GOT_POWDER) || holds(POWDER)) {
            say("You find nothing");
            return;
        }
        say("You find a strange powder inside");
        mark(GOT_POWDER);
        give(POWDER);
    }

    private void pan() {
        Player p = getOwner();
        if (!marked(TEA)) {
            guideRefuses();
            return;
        }
        say("You scoop up some mud from the stream bed");
        take(TRAY_EMPTY);
        give(TRAY_MUD);
        p.incExp(MINING, 5, true);
        p.getActionSender().sendStat(MINING);
    }

    // ------------------------------------------------------- the winches --

    private void winch(boolean northEast) {
        if (!mayUseWinch()) {
            scold(new String[] { "Sorry, this area is private",
                "The only way you'll get to use these", "Is by impressing the expert",
                "Up in the centre", "Find something worthwhile...",
                "And he might let you use the winches", "Until then, get lost !" });
            return;
        }
        if (!marked(northEast ? ROPE_NE : ROPE_W)) {
            say("The winch has no rope on it");
            say("I need to tie a rope to this first");
            return;
        }
        descend(northEast);
    }

    private void tieRope(boolean northEast) {
        if (!mayUseWinch()) {
            scold(new String[] { "Sorry, this area is private",
                "The only way you'll get to use these", "Is by impressing the expert",
                "Up in the centre", "Find something worthwhile...",
                "And he might let you use the winches", "Until then, get lost !" });
            return;
        }
        if (marked(northEast ? ROPE_NE : ROPE_W)) {
            descend(northEast);
            return;
        }
        say("You tie the rope to the winch");
        take(ROPE);
        mark(northEast ? ROPE_NE : ROPE_W);
        descend(northEast);
    }

    private void descend(boolean northEast) {
        Player p = getOwner();
        say("You climb down the rope");
        p.incExp(AGILITY, 5, true);
        p.getActionSender().sendStat(AGILITY);
        if (northEast) {
            p.teleport(SMALL_CAVE_X, SMALL_CAVE_Y, false);
            return;
        }
        /* The temple half of the cave exists twice: sealed, and blown open.
         * Which one the winch drops into is what the explosion changes. */
        int shift = past(BLASTED) ? CAVE_OFFSET : 0;
        p.teleport(MAIN_CAVE_X, MAIN_CAVE_Y + shift, false);
    }

    private void climb(int x, int y) {
        say("You climb up the rope");
        getOwner().teleport(x, y, false);
    }

    private void bricks(int item) {
        if (item == COMPOUND) {
            if (marked(PRIMED)) {
                say("The compound is already packed against the bricks");
                return;
            }
            say("You pack the explosive compound against the bricks");
            say("The bricks are covered in the strange compound");
            say("Now I need a spark to set it off...");
            take(COMPOUND);
            mark(PRIMED);
            return;
        }
        if (item != TINDERBOX) {
            return;
        }
        if (!marked(PRIMED)) {
            say("Setting fire to a pile of bricks will not achieve much");
            return;
        }
        say("You light the compound and stand well back");
        say("There is an enormous explosion");
        say("The bricks are blown out of the passageway");
        unmark(PRIMED);
        if (!past(BLASTED) && !completed()) {
            step(BLASTED);
        }
        Player p = getOwner();
        p.teleport(BLAST_X, BLAST_Y, false);
        say("A path leads south into the darkness");
    }
}
