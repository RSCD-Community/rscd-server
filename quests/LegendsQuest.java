import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

/**
 * Legend's quest. Released 24 September 2003, the last quest RuneScape Classic
 * ever got and the largest.
 *
 * The Legends Guild will not have you until you have mapped the Kharazi
 * Jungle, made a friend of the tribe that lives in it, and brought back
 * something of theirs worth putting in the main hall. None of those three is
 * what it looks like. The map is a crafting exam. The friend is a spirit who
 * only comes when you swing a bull roarer at the trees. And the gift has to be
 * carved out of a tree that will not grow without water from a spring a demon
 * has been sitting on for four hundred years, which is the actual quest: three
 * separate fights with Nezikchened, each one arranged differently from the
 * last by what you chose to do with a dagger.
 *
 * Requirements, all checked by the guard at the gate: 107 quest points, and
 * Hero's Quest, Family Crest, Shilo Village, Underground Pass and Waterfall
 * Quest complete. The skill requirements -- Agility 50, Crafting 50, Herblaw
 * 45, Magic 56, Mining 52, Prayer 42, Smithing 50, Strength 50, Thieving 50,
 * Woodcutting 50 -- are not checked at the gate by anything. They are checked
 * where they bite, one obstacle at a time, which is how RuneScape Classic did
 * it and why a player can start this quest and get stuck a long way in.
 *
 *     Sir Radimus Erkle    npc 735 in his study, npc 785 in the main hall
 *     Legends Guild Guard  npc 736, two of them on the gate
 *     Gujuo                npc 764, comes to the bull roarer and to nothing else
 *     Jungle Forester      npc 765, three of them along the jungle's north edge
 *     Ungadulu             npc 766 behind the flames, 767 while possessed
 *     Nezikchened          npc 769, level 172, fought three times
 *     Echned Zekin         npc 740, the ghost on the rock
 *     Viyeldi              npc 772, the mage under the hat
 *     San Tojalon 663, Irvig Senay 761, Ranalph Devere 762 -- the three heroes
 *
 * Ten of the npcs in that list have no spawn anywhere in the world: they are
 * summoned by the quest and unregistered again, which is why almost all of them
 * were free for RSCD to hand to invented minigames. Gujuo had been given a
 * "turn in eleven keys for a prize" handler and the possessed Ungadulu a "would
 * you like to play a game?" lever puzzle; both are unregistered in
 * conf/server/NpcHandlers.xml now, because an NpcHandler beats a quest to
 * NPC_TALK every time and neither npc could otherwise be reached.
 *
 * Three placements were missing from the world and have been added:
 * object 1080, the Legends Hall Doors, at (512,540); and DoorDefs 211 and 212,
 * the Ruined wall and the Ancient Wall, at (466,3719) and (463,3723). The
 * landscape has a plain wall on both of those tiles rather than the door, which
 * is a discrepancy this quest works around and does not fix. All three, and
 * every landing tile chosen below, were decided rather than recovered.
 *
 * WHERE THE WORDS COME FROM. Every line spoken in this file is copied from the
 * recovered transcripts: the per-quest dump for Sir Radimus Erkle, and the
 * npc-level pages Transcript:Gujuo, Transcript:Ungadulu, Transcript:Echned
 * Zekin, Transcript:Nezikchened, Transcript:Viyeldi, Transcript:Jungle
 * Forester, Transcript:Legends Guild Guard, Transcript:San Tojalon,
 * Transcript:Irvig Senay and Transcript:Ranalph Devere. The first pass at this
 * file did not: it paraphrased the whole quest in a voice of its own, and that
 * is why an earlier audit called it the one bad case. Every spoken line has since been replaced.
 *
 * Jagex's spelling is kept exactly as recorded, and there is a lot of it --
 * "Grettings", "Greetins", "beatifull", "gratefull", "apreciate", "problably",
 * "possesion", "comit", "forver", "vulerable", "eligable", "viscious",
 * "wraithlike", "disapears", "thoughtfull", "incase", "myway", "inhis", and
 * the doubled "to to" in the forester's line about escaping the jungle.
 * Correcting any of them would be a quieter kind of invention. Two things were
 * repaired: "from some distance awa", a transcriber's truncated word rather
 * than anything the client could have displayed, and the {{sic}} markers
 * themselves, which are the wiki's and not the game's.
 *
 * WHAT IS STILL OURS, and there is not much of it left:
 *
 *   - Four spoken lines, all of them Radimus's, all of them answers to
 *     situations the transcript never records: turning away a player who got
 *     past the guard without qualifying, noticing a player cannot pay his
 *     thirty gold, and the hall refusing a non-member. They fill gaps; they do
 *     not replace anything recorded.
 *   - One menu option, "Jump through the flames.", which exists because this
 *     server needs a way into a room the real client let a player simply walk
 *     into.
 *   - All the scenery and item narration -- roughly ninety lines. The wiki
 *     records what npcs say and does not systematically record what a barrel
 *     looks like when you pull at it, so these had nothing to copy.
 *
 * Three lines each hero says as he dies are recorded by the wiki as {{sic}} --
 * the transcriber saw a line and could not read it -- and are left out rather
 * than guessed at.
 *
 * The one thing here that could not be done from a quest at all is Siegfried
 * Erkle's shop, which is supposed to refuse non-members. It could not go in a
 * quest because associating npc 779 would take him away from ShopKeeper and
 * make a shop into a quest step, and he is not part of the quest -- he is a
 * shop that reads its answer. He now has a handler of his own,
 * npchandler/SiegfriedErkle, which asks completed(LEGENDS_QUEST) and turns
 * non-members away at the door. Nothing in this file is involved beyond
 * finishing.
 */
public class LegendsQuest extends Quest {
    public LegendsQuest(Player owner, Integer uid) {
        super(owner, uid);
    }

    private static final int UID = Quests.LEGENDS_QUEST;

    // --------------------------------------------------------------- npcs --

    private static final int RADIMUS = 735, RADIMUS_HALL = 785;
    private static final int GUARD = 736, GUJUO = 764, FORESTER = 765;
    private static final int UNGADULU = 766, UNGADULU_POSSESSED = 767;
    private static final int ECHNED = 740, NEZIKCHENED = 769, VIYELDI = 772;
    private static final int SAN_TOJALON = 663, IRVIG_SENAY = 761;
    private static final int RANALPH_DEVERE = 762;

    /** The four things the bull roarer can call up instead of Gujuo. */
    private static final int JUNGLE_SAVAGE = 776, KARAMJA_WOLF = 775;
    private static final int OOMLIE_BIRD = 777, JUNGLE_SPIDER = 521;
    private static final int[] ROARER_BEASTS = {
        JUNGLE_SAVAGE, KARAMJA_WOLF, OOMLIE_BIRD, JUNGLE_SPIDER
    };

    // -------------------------------------------------------------- items --

    private static final int COINS = 10, PICKAXE_BRONZE = 156, DIAMOND = 161;
    private static final int RUBY = 162, EMERALD = 163, SAPPHIRE = 164;
    private static final int HAMMER = 168, GOLD_BAR = 172, ROPE = 237;
    private static final int VIAL_OF_WATER = 464, VIAL_EMPTY = 465;
    private static final int RUNE_AXE = 405;
    private static final int UNPOWERED_ORB = 611, LOCKPICK = 714;
    private static final int SNAKE_WEED = 816, HERB_UNIDENTIFIED = 817;
    private static final int ARDRIGAL = 818, SOUL_RUNE = 825;
    private static final int RED_TOPAZ = 892, JADE = 893, OPAL = 894;
    private static final int MIND_RUNE = 35, EARTH_RUNE = 34, LAW_RUNE = 42;
    private static final int PAPYRUS = 982, CHARCOAL = 983;

    private static final int SCROLLS = 1163, SCROLLS_DONE = 1233;
    private static final int MACHETTE = 1172, BULL_ROARER = 1177;
    private static final int SKETCH = 1246;
    private static final int BOWL = 1188, BOWL_PURE = 1189, BOWL_PLAIN = 1287;
    private static final int BOWL_BLESSED = 1266, BOWL_BLESSED_PURE = 1267;
    private static final int BOWL_BLESSED_PLAIN = 1286;
    private static final int REED = 1249, SEED = 1182, SEED_GERMINATED = 1254;
    private static final int TOTEM = 1183, TOTEM_GILDED = 1265;
    private static final int BOOK_OF_BINDING = 1238;
    private static final int VIAL_ENCHANTED = 1240, VIAL_HOLY = 1239;
    private static final int HOLY_FORCE = 1257, FIRE_PASS = 1250;
    private static final int DARK_DAGGER = 1255, DAGGER_GLOWING = 1256;
    private static final int CRYSTAL_CHUNK = 1219, CRYSTAL_LUMP = 1220;
    private static final int CRYSTAL_HUNK = 1221;
    private static final int CRYSTAL_RED = 1222, CRYSTAL_GLOWING = 1231;
    private static final int WIZARD_HAT = 1264;
    private static final int NOTES_CRATE = 1241, NOTES_TABLE = 1242;
    private static final int NOTES_BED = 1243, SHAMANS_TOME = 1244;
    private static final int GUJUO_POTION = 1253;
    private static final int SOLUTION_SNAKE = 1251, SOLUTION_ARDRIGAL = 1252;

    // ------------------------------------------------------------ scenery --

    private static final int CUPBOARD = 1149, HALL_DOORS = 1080;
    private static final int TOTEM_GOOD = 1170, TOTEM_EVIL = 1169;
    private static final int BOOKCASE = 931, CRUDE_DESK = 1032, CRATE = 1144;
    private static final int TABLE = 1161, CRUDE_BED = 1162;
    private static final int CAVE_IN_EAST = 1158, CAVE_IN_WEST = 1159;
    private static final int WOODEN_DOORS = 1160;
    private static final int BOULDER_A = 1117, BOULDER_B = 1184, BOULDER_C = 1185;
    /** The remains of a large rock -- what a smashed boulder leaves behind. */
    private static final int SMASHED_ROCK = 1143;
    private static final int METAL_GATE = 1033, CARVED_ROCK = 1037;
    private static final int BURIED_REMAINS = 1168, DARK_GATE = 1165;
    private static final int BARREL = 1178, WOODEN_BEAM = 1156;
    private static final int ROPE_DOWN = 1157, ROPE_UP = 1167;
    private static final int STAIRS_A = 1114, STAIRS_B = 1123;
    private static final int STAIRS_C = 1124, STAIRS_D = 1125;
    private static final int WALKWAY_A = 558, WALKWAY_B = 559;
    private static final int WALKWAY_C = 560, WALKWAY_D = 561;
    private static final int LAVA_FURNACE = 1146, DRAGONS_EYE = 1148;
    private static final int CAVERNOUS_OPENING = 1145, ECHNED_ROCK = 1116;
    private static final int KHARAZI_ROCK = 1151, SHALLOW_WATER = 582;
    private static final int TALL_REEDS = 1163, FERTILE_EARTH = 1113;
    private static final int JUNGLE_VINE = 564, PALM_TREE = 553;

    /*
     * The wall of dense vegetation along the Kharazi jungle's north border --
     * the only way in. All 76 placements sit in the band x 354-477, y 865-872;
     * none of these ids appears anywhere else in the world. The plants
     * ("Thick vegetation") fall to the machette; the trees and palms take any
     * axe, and the wiki notes a path can be found through plants alone. Every
     * cut leaves the Jungle tree stump.
     *
     * The stump's def is type 1, a full tile block, and the client computes
     * its own collision from the same defs -- so a "cleared" tile can never
     * be walked onto by pathing, on either side of the wire. Passage is
     * therefore movement the server performs: a successful cut steps you onto
     * the tile you cleared, and Walk on a stump steps you onto that one.
     */
    private static final int JUNGLE_PLANT = 1086;
    private static final int JUNGLE_TREE_A = 1091, JUNGLE_TREE_B = 1092;
    private static final int JUNGLE_PALM_A = 1099, JUNGLE_PALM_B = 1100;
    private static final int JUNGLE_STUMP = 1087;
    private static final int LOGS = 14;
    /** The wiki's figure for a successful cut, in displayed experience. */
    private static final int CUT_EXP = 25;

    /** The five stages a planted seed passes through, in order. */
    private static final int YOMMI_BABY = 1112, YOMMI = 1107, YOMMI_GROWN = 1108;
    private static final int YOMMI_CHOPPED = 1109, YOMMI_TRIMMED = 1110;
    private static final int YOMMI_TOTEM = 1111;

    private static final int FLAMEWALL = 210, RUINED_WALL = 211, ANCIENT_WALL = 212;

    /**
     * The stairs and the walkway pay 5 across and 1.25 for falling off.
     *
     * incExp takes the displayed figure, and the counter is whole numbers, so
     * the quarter rounds half up the way every other agility award does.
     */
    private static final int CLIMB_EXP = 5, CLIMB_FAIL_EXP = 1;

    // -------------------------------------------------------------- skills --

    private static final int ATTACK = 0, DEFENSE = 1, STRENGTH = 2, HITS = 3;
    private static final int PRAYER = 5, MAGIC = 6, WOODCUT = 8, FIREMAKING = 11;
    private static final int CRAFTING = 12, SMITHING = 13, MINING = 14;
    private static final int HERBLAW = 15, AGILITY = 16;
    /* Slot 17 is thieving; Formulae.statArray called it "quest" until task 38.
     * Radimus trains it as one of his twelve. */
    private static final int THIEVING = 17;

    // ------------------------------------------------------- places on the map --

    /*
     * x grows westward in RuneScape Classic, so the jungle's eastern third is
     * its lowest x. The three sectors are the jungle split in three across its
     * whole width; the map only asks that you stand in each of them once, and
     * the wiki is explicit that you do not have to go far in.
     */
    private static final int JUNGLE_MIN_X = 340, JUNGLE_MAX_X = 480;
    private static final int JUNGLE_MIN_Y = 866, JUNGLE_MAX_Y = 912;
    private static final int SECTOR_EAST_MAX = 386, SECTOR_MID_MAX = 433;

    /** Inside the ring of flames: Ungadulu's cell. */
    private static final int FLAME_MIN_X = 451, FLAME_MAX_X = 455;
    private static final int FLAME_MIN_Y = 3705, FLAME_MAX_Y = 3711;

    /*
     * Every landing tile below was chosen from the cave's walkable floor rather
     * than recovered -- nothing in the transcripts records where you come out.
     */
    private static final int FLAME_OUT_X = 453, FLAME_OUT_Y = 3703;
    private static final int FLAME_IN_X = 453, FLAME_IN_Y = 3705;
    private static final int CAVE_MOUTH_X = 461, CAVE_MOUTH_Y = 3700;
    private static final int JUNGLE_MOUTH_X = 452, JUNGLE_MOUTH_Y = 874;
    /*
     * The bookcase passage crosses a band of solid rock (x 444-448 at y 3702):
     * searching the shelves lands you west of it, and the west Cave entrance
     * crack (1159) is the way back to the shelf side. (449,3702), the old
     * single landing, is a one-tile pocket east of that band whose only
     * opening routes back to the cave mouth.
     */
    private static final int BOOKCASE_WEST_X = 444, BOOKCASE_WEST_Y = 3699;
    private static final int BOOKCASE_EAST_X = 452, BOOKCASE_EAST_Y = 3702;
    private static final int BARREL_ROOM_X = 471, BARREL_ROOM_Y = 3709;
    private static final int DEPTHS_X = 427, DEPTHS_Y = 3708;

    /**
     * The seven Carved Rocks, in the order the riddle puts them: the row of
     * four running southeast, then the row of three, also running southeast.
     * Index into GEM_ORDER.
     */
    private static final int[][] CARVED_AT = {
        { 471, 3722 }, { 469, 3728 }, { 464, 3730 }, { 460, 3737 },
        { 474, 3730 }, { 471, 3734 }, { 466, 3739 }
    };
    private static final int[] GEM_ORDER = {
        OPAL, JADE, RED_TOPAZ, SAPPHIRE, EMERALD, RUBY, DIAMOND
    };
    private static final String[] GEM_NAMES = {
        "Opal", "Jade", "Red topaz", "Sapphire", "Emerald", "Ruby", "Diamond"
    };

    /** Soul, mind, earth, law, law -- S, M, E, L, L. */
    private static final int[] SMELL = {
        SOUL_RUNE, MIND_RUNE, EARTH_RUNE, LAW_RUNE, LAW_RUNE
    };
    /*
     * The ancient wall's geography: the interactive wall face is on the west
     * side of the rock band, the magical doorway that leads back out is on the
     * east face, and going through in either direction crosses the band.
     */
    private static final int DOORWAY_X = 466, DOORWAY_Y = 3723;
    private static final int WALL_EAST_X = 467, WALL_EAST_Y = 3723;
    private static final int WALL_WEST_X = 462, WALL_WEST_Y = 3723;
    /** The diagonal ruined wall the replay jumps, west of the rune door. */
    private static final int JUMP_WALL_X = 456, JUMP_WALL_Y = 3728;

    /* Names and slot words exactly as the recorded playthrough prints them --
       note "second slot" where every other line says just the ordinal. */
    private static final String[] RUNE_NAMES = {
        "Soul-Rune", "Mind-Rune", "Earth-Rune", "Law-Rune", "Law-Rune"
    };
    private static final String[] RUNE_ORDINALS = {
        "first", "second slot", "third", "fourth", "fifth"
    };

    /** The three heroes, and the crystal each of them is carrying. */
    private static final int[] HERO_NPC = { SAN_TOJALON, IRVIG_SENAY, RANALPH_DEVERE };
    private static final int[] HERO_CRYSTAL = { CRYSTAL_CHUNK, CRYSTAL_LUMP, CRYSTAL_HUNK };
    private static final String[] HERO_NAME = { "San Tojalon", "Irvig Senay", "Ranalph Devere" };

    /** Radimus's four training menus, three skills each. */
    private static final int[][] TRAIN_MENU = {
        { ATTACK, DEFENSE, STRENGTH },
        { HITS, PRAYER, MAGIC },
        { WOODCUT, CRAFTING, SMITHING },
        { HERBLAW, AGILITY, THIEVING }
    };
    private static final String[][] TRAIN_LABEL = {
        { "* Attack *", "* Defense *", "* Strength *" },
        { "* Hits *", "* Prayer *", "* Magic *" },
        { "* Woodcutting *", "* Crafting *", "* Smithing *" },
        { "* Herblaw *", "* Agility *", "* Thieving *" }
    };
    private static final String[] TRAIN_NAME = {
        "Attack", "Defense", "Strength", "Hits", "", "Prayer", "Magic", "",
        "", "", "", "", "Crafting", "Smithing", "", "Herblaw", "Agility", "Thieving"
    };

    // ------------------------------------------------------- stage and bits --

    /*
     * Twenty-five stages and twenty-four working bits will not fit in one int
     * without reuse, so two groups are used twice, each time separated by more
     * than half the quest:
     *
     *   bits 5-7   the three mapped sectors, cleared the moment the map is
     *              finished at MAPPED (stage 2) and never set again;
     *   bits 22-24 the three heroes' crystals underground, cleared when the
     *              totem pole is carved at TOTEM_CUT (stage 20) and then reused
     *              to remember which of the three has already been beaten in
     *              the last fight.
     *
     * Bit 31 is the sign bit and is not used: getStage() < 0 is how the base
     * class says "not started".
     *
     * completed() is exact equality against the final stage, so every one of
     * these has to be gone by the time Radimus finishes the training. With
     * twenty-four of them there is no legalising the survivors as extra final
     * stages the way Digsite does, so completion is a plain setStage(FINISHED)
     * that clears the lot. Nothing here needs to outlive the quest: the flame
     * wall, the Ancient Wall and the Legends Hall doors all read past(), which
     * stays true forever once the quest is over.
     */
    private static final int STAGE_MASK = 0x0000001F;
    private static final int BITS = ~STAGE_MASK & 0x7FFFFFFF;

    private static final int MAP_SHIFT = 5, MAP_MASK = 0x000000E0;
    private static final int SKETCH_BIT = 0x00000100;
    private static final int ASKED_WATER = 0x00000200;
    private static final int RUNE_SHIFT = 10, RUNE_MASK = 0x00001C00;
    private static final int GEM_SHIFT = 13, GEM_MASK = 0x000FE000;
    private static final int DAGGER_GIVEN = 0x00100000;
    private static final int VIYELDI_DEAD = 0x00200000;
    private static final int HERO_SHIFT = 22, HERO_MASK = 0x01C00000;
    private static final int BRAVE = 0x02000000;
    private static final int ROPE_ON_BEAM = 0x04000000;
    private static final int TRAIN_SHIFT = 27, TRAIN_MASK = 0x38000000;

    private static final int STARTED = 0;      // Radimus has handed over the scrolls
    private static final int MAPPED = 1;       // all three sectors drawn
    private static final int ROARER = 2;       // the forester has swapped the map for a roarer
    private static final int MET_GUJUO = 3;    // the roarer has called Gujuo up once
    private static final int SAW_SHAMAN = 4;   // Ungadulu has been seen through the flames
    private static final int KNOWS_WATER = 5;  // Gujuo has explained the bowl and the spring
    private static final int BLESSED = 6;      // the golden bowl has been blessed
    private static final int BOOK = 7;         // all seven gems placed, book in hand
    private static final int DEMON1 = 8;       // Nezikchened beaten inside the flames
    private static final int SEEDS = 9;        // Ungadulu has handed over the seeds
    private static final int GERMINATED = 10;  // seeds watered, the jungle spring dry
    private static final int KNOWS_HERBS = 11; // Gujuo has named Snake Weed and Ardrigal
    private static final int DEEP = 12;        // down the rope into the lower caverns
    private static final int PROPHECY = 13;    // Viyeldi has recited it
    private static final int CRYSTAL = 14;     // three pieces fused in the lava furnace
    private static final int EYE = 15;         // the crystal charged on the dragon's eye
    private static final int OPENED = 16;      // the cavernous opening answered to it
    private static final int MET_ECHNED = 17;  // the ghost on the rock, dagger in hand
    private static final int DEMON2 = 18;      // Nezikchened beaten at the spring
    private static final int WATERED = 19;     // the spring drawn from again
    private static final int TOTEM_CUT = 20;   // a Yommi tree carved into a pole
    private static final int DEMON3 = 21;      // Nezikchened beaten at the evil totem
    private static final int REPLACED = 22;    // the good totem stands in its place
    private static final int GIFT = 23;        // Gujuo's gilded totem pole
    private static final int HANDED_IN = 24;   // Radimus has taken both
    private static final int FINISHED = 25;

    /** Not persisted: which npc the quest summoned and is watching. */
    private transient Npc summoned = null;

    // ------------------------------------------------------------- define --

    public void define() {
        setUID(UID);
        setName("Legend's Quest");
        setFinalStage(FINISHED);

        associateNpc(RADIMUS);
        associateNpc(RADIMUS_HALL);
        associateNpc(GUARD);
        associateNpc(GUJUO);
        associateNpc(FORESTER);
        associateNpc(UNGADULU);
        associateNpc(UNGADULU_POSSESSED);
        associateNpc(ECHNED);
        associateNpc(NEZIKCHENED);
        associateNpc(VIYELDI);
        associateNpc(SAN_TOJALON);
        associateNpc(IRVIG_SENAY);
        associateNpc(RANALPH_DEVERE);

        associateObject(CUPBOARD);
        associateObject(HALL_DOORS);
        associateObject(TOTEM_GOOD);
        associateObject(TOTEM_EVIL);
        associateObject(BOOKCASE);
        associateObject(CRUDE_DESK);
        associateObject(CRATE);
        associateObject(TABLE);
        associateObject(CRUDE_BED);
        associateObject(CAVE_IN_EAST);
        associateObject(CAVE_IN_WEST);
        associateObject(WOODEN_DOORS);
        associateObject(BOULDER_A);
        associateObject(BOULDER_B);
        associateObject(BOULDER_C);
        associateObject(METAL_GATE);
        associateObject(CARVED_ROCK);
        associateObject(BURIED_REMAINS);
        associateObject(DARK_GATE);
        associateObject(BARREL);
        associateObject(WOODEN_BEAM);
        associateObject(ROPE_DOWN);
        associateObject(ROPE_UP);
        associateObject(STAIRS_A);
        associateObject(STAIRS_B);
        associateObject(STAIRS_C);
        associateObject(STAIRS_D);
        associateObject(WALKWAY_A);
        associateObject(WALKWAY_B);
        associateObject(WALKWAY_C);
        associateObject(WALKWAY_D);
        associateObject(LAVA_FURNACE);
        associateObject(DRAGONS_EYE);
        associateObject(CAVERNOUS_OPENING);
        associateObject(ECHNED_ROCK);
        associateObject(KHARAZI_ROCK);
        associateObject(SHALLOW_WATER);
        associateObject(TALL_REEDS);
        associateObject(FERTILE_EARTH);
        associateObject(JUNGLE_PLANT);
        associateObject(JUNGLE_TREE_A);
        associateObject(JUNGLE_TREE_B);
        associateObject(JUNGLE_PALM_A);
        associateObject(JUNGLE_PALM_B);
        associateObject(JUNGLE_STUMP);
        associateObject(YOMMI_BABY);
        associateObject(YOMMI);
        associateObject(YOMMI_GROWN);
        associateObject(YOMMI_CHOPPED);
        associateObject(YOMMI_TRIMMED);
        associateObject(YOMMI_TOTEM);

        // @share object 564 with JunglePotion
        // @share object 553 with JunglePotion
        // Snake Weed grows on the Jungle Vine and Ardrigal on the PalmTree, and
        // they are the two halves of both Trufitus's potion and Gujuo's. Both
        // quests are handed the search; the one that is currently asking for
        // that herb spawns it and the other says there is nothing there.
        associateObject(JUNGLE_VINE);
        associateObject(PALM_TREE);

        associateDoor(FLAMEWALL);
        associateDoor(RUINED_WALL);
        associateDoor(ANCIENT_WALL);

        /*
         * Claimed for a right-click command this quest reimplements: the two
         * maps read, the roarer swings, the sketch and the four sets of notes
         * read, the seeds and the gilded pole are inspected, the potion is
         * drunk, the holy water is thrown and the holy force is cast. The rest
         * are claimed only so that they can be offered as one half of an item
         * pair, which needs both halves claimed, and none of those has a
         * command to swallow.
         */
        associateItem(SCROLLS);
        associateItem(SCROLLS_DONE);
        associateItem(BULL_ROARER);
        associateItem(SKETCH);
        associateItem(SEED);
        associateItem(SEED_GERMINATED);
        associateItem(TOTEM_GILDED);
        associateItem(BOOK_OF_BINDING);
        associateItem(VIAL_HOLY);
        associateItem(HOLY_FORCE);
        associateItem(GUJUO_POTION);
        associateItem(NOTES_CRATE);
        associateItem(NOTES_TABLE);
        associateItem(NOTES_BED);
        associateItem(SHAMANS_TOME);
        associateItem(BOWL);
        associateItem(BOWL_BLESSED);
        associateItem(BOWL_BLESSED_PURE);
        associateItem(BOWL_PLAIN);
        associateItem(BOWL_BLESSED_PLAIN);
        associateItem(BOWL_PURE);
        associateItem(REED);
        associateItem(GOLD_BAR);
        associateItem(VIAL_OF_WATER);
        associateItem(VIAL_EMPTY);
        associateItem(VIAL_ENCHANTED);
        associateItem(SNAKE_WEED);
        associateItem(ARDRIGAL);
        associateItem(SOLUTION_SNAKE);
        associateItem(SOLUTION_ARDRIGAL);
        associateItem(DARK_DAGGER);
        associateItem(DAGGER_GLOWING);
        associateItem(CRYSTAL_CHUNK);
        associateItem(CRYSTAL_LUMP);
        associateItem(CRYSTAL_HUNK);
        associateItem(CRYSTAL_RED);
        associateItem(CRYSTAL_GLOWING);
        associateItem(TOTEM);
        associateItem(FIRE_PASS);
        // The hat, so that lifting it comes here and can be refused.
        associateItem(WIZARD_HAT);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("The Legends Guild will not have you until you have mapped the Kharazi Jungle, made a friend of the tribe that lives in it, and brought back something of theirs worth putting in the main hall.");
        setStartPoint("The Legends Guild");
        setSpeakTo("Grand Vizier Radimus Erkle");
        require("107 quest points");
        requireQuest(Quests.HEROS_QUEST);
        requireQuest(Quests.FAMILY_CREST);
        requireQuest(Quests.SHILO_VILLAGE);
        requireQuest(Quests.UNDERGROUND_PASS);
        requireQuest(Quests.WATERFALL_QUEST);
        rewardOther("Membership of the Legends Guild");
        rewardOther("Four picks of experience, each worth (level + 1) x 150 in a skill chosen from Radimus's twelve");
    }

    public void completeQuest() {
        Player p = getOwner();
        p.getActionSender().sendMessage("@gre@Well done - you have completed the Legends Guild Quest!");
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

    private int field(int mask, int shift) {
        return questStarted() ? (getStage() & mask) >> shift : 0;
    }

    private void setField(int mask, int shift, int value) {
        setStage(getStage() & ~mask | value << shift & mask);
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

    /** Swap one item for another in place, which is most of this quest. */
    private void swap(int from, int to) {
        take(from);
        give(to);
    }

    private void hurt(int damage) {
        Player p = getOwner();
        p.setCurStat(HITS, Math.max(0, p.getCurStat(HITS) - damage));
        p.getActionSender().sendStat(HITS);
    }

    /**
     * Award agility experience, in the displayed figures the wiki quotes.
     *
     * Several obstacles in this quest are worth nothing at all -- the Kharazi
     * rock and the ruined wall are both recorded as zero -- so a zero award
     * is a real answer and not a missing one.
     */
    private void reward(int skill, int exp) {
        if (exp <= 0) {
            return;
        }
        Player p = getOwner();
        p.incExp(skill, exp, false);
        p.getActionSender().sendStat(skill);
    }

    /** One attempt in five goes wrong. Classic's real rate was never recorded. */
    private boolean lucky() {
        return DataConversions.random(0, 4) != 0;
    }

    /** The demon's prayer drain: four bites, two seconds apart, to the floor. */
    private void stepDrain(final int prayerTo) {
        final int start = getOwner().getCurStat(PRAYER);
        if (start <= prayerTo) {
            return;
        }
        for (int i = 1; i <= 4; i++) {
            final int to = start - (start - prayerTo) * i / 4;
            world.getDelayedEventHandler().add(new SingleEvent(getOwner(), i * 2000) {
                public void action() {
                    drain(PRAYER, to);
                }
            });
        }
    }

    private void drain(int skill, int to) {
        Player p = getOwner();
        if (p.getCurStat(skill) > to) {
            p.setCurStat(skill, to);
            p.getActionSender().sendStat(skill);
        }
    }

    /**
     * Say a line as an npc without going through sayNpcMessage().
     *
     * sayNpcMessage() blocks the npc and then unblocks it, and Npc.unblock()
     * clears the blocker's npc reference; a line said that way at the tail of
     * an Effect can therefore be talking about an npc that is no longer there.
     * Building the ChatMessage against an npc held in hand cannot.
     */
    private void offer(Npc npc, String line) {
        getOwner().informOfNpcMessage(new ChatMessage(npc, line, getOwner()));
    }

    private boolean inJungle() {
        Player p = getOwner();
        return p.getX() >= JUNGLE_MIN_X && p.getX() <= JUNGLE_MAX_X
            && p.getY() >= JUNGLE_MIN_Y && p.getY() <= JUNGLE_MAX_Y;
    }

    /** 0 eastern, 1 middle, 2 western, -1 outside the jungle entirely. */
    private int sector() {
        if (!inJungle()) {
            return -1;
        }
        int x = getOwner().getX();
        return x <= SECTOR_EAST_MAX ? 0 : x <= SECTOR_MID_MAX ? 1 : 2;
    }

    private boolean insideFlames() {
        Player p = getOwner();
        return p.getX() >= FLAME_MIN_X && p.getX() <= FLAME_MAX_X
            && p.getY() >= FLAME_MIN_Y && p.getY() <= FLAME_MAX_Y;
    }

    private void teleport(int x, int y) {
        getOwner().teleport(x, y, false);
    }

    private boolean holdsBlessedWater() {
        return holds(BOWL_BLESSED_PURE);
    }

    /** The npc of this id nearest the player, or null. */
    private Npc nearby(int id) {
        Player p = getOwner();
        return world.getNpc(id, p.getX() - 10, p.getX() + 10, p.getY() - 10, p.getY() + 10);
    }

    /**
     * Put an npc on the map beside the player for a while.
     *
     * The pattern is Merlin's crystal's and Shilo village's: a respawn-less Npc
     * registered by hand and unregistered again by a SingleEvent, because every
     * npc this quest fights or talks to down here has no spawn in the world at
     * all and has to be conjured where the player is standing.
     */
    private Npc summon(int id, int life) {
        Player p = getOwner();
        int x = p.getX();
        int y = p.getY();
        final Npc npc = new Npc(id, x, y, x - 1, x + 1, y - 1, y + 1);
        npc.setRespawn(false);
        world.registerNpc(npc);
        world.getDelayedEventHandler().add(new SingleEvent(null, life){

            public void action() {
                world.unregisterNpc(npc);
            }
        });
        return npc;
    }

    /** Put the scenery at an object's tile back to something else. */
    private void becomes(GameObject object, int newId) {
        world.registerGameObject(new GameObject(object.getLocation(), newId,
            object.getDirection(), object.getType()));
    }

    // ----------------------------------------------------------- dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof GameObject) {
            /*
             * A door is a GameObject like any other and only the trigger says
             * which it is -- the type on the loc says how it is drawn, not how
             * it was clicked, and a quest that read the type would get the
             * flame wall right and be wrong about anything unusual.
             */
            GameObject object = (GameObject) entity;
            if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2
                || trigger == QuestTrigger.ITEM_ON_DOOR) {
                door(trigger, object, used);
            } else {
                scenery(trigger, object, used);
            }
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
            killed(npc);
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
            case RADIMUS:            radimus(npc);        break;
            case RADIMUS_HALL:       radimusHall(npc);    break;
            case GUARD:              guard(npc);          break;
            case GUJUO:              gujuo(npc);          break;
            case FORESTER:           forester(npc);       break;
            case UNGADULU:           ungadulu(npc);       break;
            case ECHNED:             echned(npc);         break;
            case VIYELDI:            viyeldi(npc);        break;
            case SAN_TOJALON:
            case IRVIG_SENAY:
            case RANALPH_DEVERE:     hero(npc);           break;
            case NEZIKCHENED:
            case UNGADULU_POSSESSED: /* nothing to say */ break;
            default: break;
        }
    }

    // -------------------------------------------------------- Radimus Erkle --

    /**
     * The Grand Vizier in his study: the only npc who can start this quest, and
     * the only one who can end it apart from his double in the hall.
     */
    private void radimus(final Npc npc) {
        final Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Hello there! How are you enjoying the Legends Guild?")
                .message("Radimus looks busy...")
                .npc("Excuse me a moment won't you.")
                .npc("Do feel free to explore the rest of the building.")
                .start();
            return;
        }
        if (past(HANDED_IN)) {
            new Conversation(p, npc)
                .npc("Hello again, go through to the main Legends Guild Hall,")
                .npc("I'll meet you in there.")
                .npc("And we can discuss your reward !")
                .start();
            return;
        }
        if (questStarted()) {
            duringQuest(npc);
            return;
        }
        if (!eligible()) {
            /*
             * He does not turn anyone away himself -- the guard on the gate is
             * the one who checks -- but a player who walked in past the guard
             * and started talking has to be told something.
             */
            new Conversation(p, npc)
                .npc("Good day to you Sir !")
                .npc("I am afraid the guild is not yet open to you.")
                .npc("Do have a word with the guards on the gate.")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Good day to you Sir !")
            .npc("No doubt you are keen to become a member of the Legends Guild ?")
            .picker(new Choice("Yes actually, what's involved?",
                                "Maybe some other time.",
                                "Who are you?") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.player("Maybe some other time.");
                        c.npc("Ok, as you wish...");
                        return;
                    }
                    if (option == 2) {
                        c.player("Who are you?");
                        c.npc("My name is Radimus Erkle, I am the Grand Vizier of the Legends Guild.");
                        c.npc("Are you interested in becoming a member?");
                    } else {
                        c.player("Yes actually, what's involved ?");
                    }
                    briefing(c);
                }
            })
            .start();
    }

    /** The quest offer itself, reached from two of Radimus's three options. */
    private void briefing(Conversation c) {
        c.npc("Well, you need to complete a quest for us.");
        c.npc("You need to map an area called the Kharazi Jungle");
        c.npc("It is the unexplored southern part of Karamja Island.");
        c.npc("You also need to befriend a native from the Kharazi tribe");
        c.npc("in order to get a gift or token of friendship.");
        c.npc("We want to display it in the Legends Guild Main hall.");
        c.npc("Are you interested in this quest?");
        c.picker(new Choice("Yes, it sounds great!", "Not just at the moment.") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.player("Not just at the moment.");
                    c.npc("Very well, if you change your mind, please come back and see me.");
                    return;
                }
                c.player("Yes, it sounds great!");
                c.npc("Excellent!");
                c.npc("Ok, you'll need this starting map of the Kharazi Jungle.");
                c.message("Grand Vizier Erkle gives you some notes and a map.");
                step(STARTED);
                give(SCROLLS);
                c.npc("Complete this map when you get to the Kharazi Jungle.");
                c.npc("It's towards the southern most part of Karamja.");
                c.npc("You'll need additional papyrus and charcoal to complete the map.");
                c.npc("There are three different sectors of the Kharazi jungle to map.");
                c.message("Radimus shuffles around the back of his desk.");
                c.npc("It is likely to be very tough going.");
                c.npc("You'll need an axe and a machette to cut through");
                c.npc("the dense Kharazi jungle,collect a machette from the");
                c.npc("cupboard before you leave. Bring back some sort of token");
                c.npc("which we can display in the Guild.");
                c.npc("And very good luck to you !");
            }
        });
    }

    /**
     * "How is the quest going?", with the five things a player can have got
     * stuck on.
     *
     * RuneScape Classic menus hold five options and the transcript lists six,
     * one of which -- the lost map -- only appears when the map really is lost.
     * That is how it fits: the list is built to what the player is carrying.
     */
    private void duringQuest(final Npc npc) {
        final Player p = getOwner();
        final boolean lost = !holds(SCROLLS) && !holds(SCROLLS_DONE);
        String[] options = lost
            ? new String[] { "Terrible, I lost my map of the Kharazi Jungle.",
                             "It's Ok, but I have forgotten what to do.",
                             "I need another machete.",
                             "I've run out of Charcoal.",
                             "I've run out of Papyrus." }
            : new String[] { "It's Ok, but I have forgotten what to do.",
                             "I need another machete.",
                             "I've run out of Charcoal.",
                             "I've run out of Papyrus." };
        final int offset = lost ? 1 : 0;
        new Conversation(p, npc)
            .npc("Hello there, how is the quest going?")
            .options(new Choice(options) {
                public void picked(int option, Conversation c) {
                    if (lost && option == 0) {
                        lostMap(c);
                        return;
                    }
                    switch (option - offset) {
                        case 0:
                            c.player("It's Ok, but I have forgotten what to do.");
                            c.npc("Tut! How forgetful!");
                            c.npc("You need to find a way into the Kharazi jungle,");
                            c.npc("Then you need to explore and map that entire area.");
                            c.npc("While you're there, you need to make contact with any jungle natives.");
                            c.npc("Bring back a tribal gift from the natives");
                            c.npc("so that we can display it in the Legends Guild.");
                            c.npc("I hope that answers your question!");
                            break;
                        case 1:
                            c.player("I need another machete.");
                            c.npc("Well, just get another one from the cupboard.");
                            break;
                        case 2:
                            c.player("I've run out of Charcoal.");
                            c.npc("Well, get some more!");
                            c.npc("Be proactive and get some more from somewhere.");
                            c.message("Sir Radimus mutters under his breath.");
                            c.npc("It's hardly legendary if you fail a quest");
                            c.npc("because you can't find some charcoal!");
                            break;
                        default:
                            c.player("I've run out of Papyrus.");
                            c.npc("Well, get some more!");
                            c.npc("Be proactive and try to find some!");
                            c.message("Sir Radimus mutters under his breath.");
                            c.npc("It's hardly legendary if you fail a quest");
                            c.npc("because you can't find some papyrus!");
                            break;
                    }
                }
            })
            .start();
    }

    /**
     * A replacement map for thirty gold.
     *
     * The copy always comes out blank, and the three sector bits are cleared
     * with it: a player who lost a half-finished map starts the drawing again.
     */
    private void lostMap(Conversation c) {
        c.player("Terrible, I lost my map of the Kharazi Jungle.");
        c.npc("That is awful, well, luckily I have a copy here.");
        c.npc("But I need to charge you a copy fee of 30 gold pieces.");
        c.npc("Do you agree to pay?");
        c.picker(new Choice("Yes, I'll pay for it.", "No, I won't pay for it.") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.player("No, I won't pay for it.");
                    c.npc("Well, that's your decision, of course...");
                    c.npc("but you won't be able to complete the quest without it.");
                    c.npc("Excuse, me now won't you, I have other business to attend to.");
                    return;
                }
                c.player("Yes, I'll pay for it.");
                if (count(COINS) < 30) {
                    c.npc("Well, you don't seem to have thirty gold on you.");
                    c.npc("Do come back when you have.");
                    return;
                }
                take(COINS, 30);
                c.message("You hand over 30 gold coins.");
                setField(MAP_MASK, MAP_SHIFT, 0);
                if (past(MAPPED)) {
                    step(STARTED);
                }
                give(SCROLLS);
                c.npc("Ok, please don't lose this one..");
            }
        });
    }

    /**
     * The double in the main hall, who exists only to hand out the training.
     *
     * The quest does not finish when the totem changes hands: it finishes on
     * the fourth skill picked, which is why HANDED_IN and FINISHED are separate
     * stages and why the counter has to survive a logout in between.
     */
    private void radimusHall(final Npc npc) {
        final Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Hello there! How are you enjoying the Legends Guild?")
                .npc("Do feel free to explore the rest of the building.")
                .start();
            return;
        }
        if (!past(HANDED_IN)) {
            new Conversation(p, npc)
                .npc("Welcome to the Legends Guild Main Hall.")
                .npc("Members only I am afraid.")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        if (field(TRAIN_MASK, TRAIN_SHIFT) == 0) {
            c.npc("Welcome to the Legends Guild Main Hall.");
            c.npc("We have placed your Totem Pole as pride of place.");
            c.npc("All members of the Legends Guild will see it as they walk in.");
            c.npc("They will know that you were the person to bring it back.");
            c.npc("Congratulations, you're now a fully fledged member.");
            c.npc("I would like to to offer you some training.");
            c.npc("Which will increase your experience and abilities");
            c.npc("In four areas.");
        } else {
            c.npc("Hello again...");
        }
        c.npc("Would you like to train now?");
        c.picker(new Choice("Yes, I'll train now.",
                             "No, I've got something else to do at the moment.") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.player("No, I've got something else to do at the moment.");
                    c.npc("Very well young man.");
                    c.npc("Return when you are able, but don't leave it too long.");
                    c.npc("You'll benefit alot from this training.");
                    c.npc("Now, do excuse me while, I have other things to attend to.");
                    c.npc("Do feel free to explore the rest of the building.");
                    return;
                }
                c.player("Yes, I'll train now.");
                offerTraining(c, 0);
            }
        });
        c.start();
    }

    /**
     * One of Radimus's four training menus.
     *
     * Three skills and a door to the next menu, the fourth of which leads back
     * to the first, so all twelve skills are reachable from any of them. Each
     * pick is worth (level + 1) * 150 experience in that skill and drops the
     * remaining count by one; the fourth ends the quest.
     */
    private void offerTraining(Conversation c, final int menu) {
        int left = 4 - field(TRAIN_MASK, TRAIN_SHIFT);
        if (left <= 0) {
            return;
        }
        c.npc("You can choose " + left + (left == 1 ? " area" : " areas")
            + " to increase your abilities in.");
        final int next = (menu + 1) % TRAIN_MENU.length;
        c.options(new Choice(TRAIN_LABEL[menu][0], TRAIN_LABEL[menu][1],
                             TRAIN_LABEL[menu][2],
                             "--- Go to Skill Menu " + (next + 1) + " ----") {
            public void picked(int option, Conversation c) {
                if (option == 3) {
                    offerTraining(c, next);
                    return;
                }
                train(TRAIN_MENU[menu][option]);
                c.message("You receive some training and increase experience to your "
                    + TRAIN_NAME[TRAIN_MENU[menu][option]]);
                int done = field(TRAIN_MASK, TRAIN_SHIFT) + 1;
                setField(TRAIN_MASK, TRAIN_SHIFT, done);
                if (done < 4) {
                    offerTraining(c, 0);
                    return;
                }
                c.npc("Right, that's all the training I can offer.!");
                c.npc("Hope you're happy with your new skills.");
                c.npc("Excuse me now won't you ?");
                c.npc("Do feel free to explore the rest of the building.");
                /*
                 * Every working bit goes with this, which is the whole reason
                 * completion is a plain setStage: twenty-four of them could not
                 * be legalised as extra final stages.
                 */
                setStage(FINISHED);
                completeQuest();
            }
        });
    }

    private void train(int skill) {
        Player p = getOwner();
        p.incExp(skill, (p.getMaxStat(skill) + 1) * 150, false);
        p.getActionSender().sendStat(skill);
    }

    /** Everything the guard on the gate is actually looking at. */
    private boolean eligible() {
        Player p = getOwner();
        return p.getQuestPoints() >= 107
            && p.getQuestManager().completed(Quests.HEROS_QUEST)
            && p.getQuestManager().completed(Quests.FAMILY_CREST)
            && p.getQuestManager().completed(Quests.SHILO_VILLAGE)
            && p.getQuestManager().completed(Quests.UNDERGROUND_PASS)
            && p.getQuestManager().completed(Quests.WATERFALL_QUEST);
    }

    /**
     * How the player is addressed by the guard, who is very correct about it.
     *
     * The transcript writes the two forms as "[Sir/Ma'am]" everywhere except
     * one line, where it is "[sir/Maaam]" and carries the wiki's own {{sic}}.
     * Both are reproduced, the second one wrongly spelled on purpose.
     */
    private String sir() {
        return getOwner().isMale() ? "Sir" : "Ma'am";
    }

    private String sirSic() {
        return getOwner().isMale() ? "sir" : "Maaam";
    }

    /**
     * The two guards on the Legends Guild gate, from
     * Transcript:Legends Guild Guard.
     *
     * Four states, and only the first one has anything to say: before the quest
     * he answers questions, during it he nods, and after it he salutes. The
     * pass before this one had the first state's spine right and was missing
     * three of its four top-level questions and the whole of the eligible
     * player's branch, which is the one that actually unlocks the gate.
     */
    private void guard(final Npc npc) {
        final Player p = getOwner();
        if (completed() || past(HANDED_IN)) {
            new Conversation(p, npc)
                .message("The guards Salute you as you walk past.")
                .npc("! ! ! Attention ! ! !")
                .npc("Legends Guild Member Approaching")
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .message("A guard nods at you as you walk past.")
                .npc("Hope the quest is going well " + sir() + " !")
                .start();
            return;
        }
        new Conversation(p, npc)
            .message("You approach a nearby guard...")
            .npc("Yes " + sir() + ", how can I help you?")
            .picker(new Choice("What is this place?",
                                "How do I get in here?",
                                "Can I speak to someone in charge?",
                                "It's Ok thanks.") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        thisPlace(c);
                        return;
                    }
                    if (option == 1) {
                        c.player("How do I get in here?");
                        c.npc("Well " + sirSic() + ",");
                        c.npc("you'll need to be a legendary citizen of RuneScape.");
                        c.npc("If you want to use the Legends Hall,");
                        c.npc("you'll be invited to complete a quest.");
                        c.npc("Once you have completed that Quest,");
                        c.npc("you'll be a fully fledged member of the Guild.");
                        c.picker(new Choice("What is this place?",
                                             "Can I speak to someone in charge?",
                                             "Can I go on the quest?") {
                            public void picked(int option, Conversation c) {
                                if (option == 0) {
                                    thisPlace(c);
                                } else if (option == 1) {
                                    inCharge(c);
                                } else {
                                    onTheQuest(c);
                                }
                            }
                        });
                        return;
                    }
                    if (option == 2) {
                        inCharge(c);
                        return;
                    }
                    c.player("It's Ok thanks.");
                    c.npc("Very well " + sir() + " !");
                }
            })
            .start();
    }

    /** "What is this place?" -- and the two questions that follow from it. */
    private void thisPlace(Conversation c) {
        c.player("What is this place?");
        c.npc("This is the Legends Guild " + sirSic() + " !");
        c.npc("Legendary RuneScape citizens are invited on a quest");
        c.npc("in order to become members of the guild.");
        c.picker(new Choice("Can I go on the quest?", "What kind of quest is it?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    onTheQuest(c);
                } else {
                    whatKind(c);
                }
            }
        });
    }

    /** "Can I speak to someone in charge?" -- no, but here is his name. */
    private void inCharge(Conversation c) {
        c.player("Can I speak to someone in charge?");
        c.npc("Well, " + sir() + ",");
        c.npc("Radimus Erkle is the Grand Vizier of the Legends Guild.");
        c.npc("He's a very busy man.");
        c.npc("And he'll only talk to those people eligible for the quest.");
        c.picker(new Choice("Can I go on the quest?", "What kind of quest is it?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    onTheQuest(c);
                } else {
                    whatKind(c);
                }
            }
        });
    }

    /** "What kind of quest is it?" -- he genuinely does not know. */
    private void whatKind(Conversation c) {
        c.player("What kind of quest is it?");
        c.npc("Well, to be honest " + sir() + ", I'm not really sure.");
        c.npc("You'll need to talk to Grand Vizier Erkle to find that out.");
        c.picker(new Choice("Can I go on the quest?", "Thanks for your help.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    onTheQuest(c);
                    return;
                }
                c.player("Thanks for your help");
                c.npc("You're welcome..");
                c.message("The Guard marches off on patrol again.");
            }
        });
    }

    /**
     * "Can I go on the quest?" -- the one question the scroll of paper answers,
     * and the only place in this quest where the requirements are checked.
     */
    private void onTheQuest(Conversation c) {
        c.player("Can I go on the quest?");
        c.message("The guard gets out a scroll of paper and starts looking through it.");
        if (!eligible()) {
            c.npc("I'm very sorry,");
            c.npc("But you need to complete more quests before you qualify.");
            c.npc("You also need to have 107 quest points.");
            c.picker(new Choice("Which quests do I need to complete?", "Ok thanks.") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.player("Ok, thanks.");
                        c.npc("That's no problem...");
                        c.npc("Best of luck if you intend to become a member!");
                        return;
                    }
                    c.player("Which quests do I need to complete?");
                    c.npc("You need to complete the...");
                    c.npc("Hero's Quest.");
                    c.npc("Family Crest Quest.");
                    c.npc("Shilo Village Quest.");
                    c.npc("Underground Pass Quest.");
                    c.npc("Waterfall Quest.");
                    c.npc("You also need to have 107 Quest Points as well!");
                    c.npc("They don't call it the Legends Guild for nothing you know!");
                    c.npc("Best of luck if you intend to become a member!");
                }
            });
            return;
        }
        c.npc("Well, it looks as if you are eligable for the quest.");
        c.npc("Grand Vizier Erkle will give you the details about the quest.");
        c.npc("You can go and talk to him about it if you like?");
        c.picker(new Choice("Who is Grand Vizier Erkle?",
                             "Yes, I'd like to talk to Grand Vizier Erkle.",
                             "Some other time perhaps.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.player("Who is Grand Vizier Erkle?");
                    c.npc("He is the head of the Legends Guild.");
                    c.npc("His full name is Radimus Erkle.");
                    c.npc("Would you like to talk to him about the quest?");
                    c.picker(new Choice("Yes, I'd like to talk to Grand Vizier Erkle.",
                                         "Some other time perhaps.") {
                        public void picked(int option, Conversation c) {
                            if (option == 0) {
                                letThemIn(c);
                            } else {
                                c.player("Some other time perhaps");
                            }
                        }
                    });
                    return;
                }
                if (option == 1) {
                    letThemIn(c);
                    return;
                }
                c.player("Some other time perhaps");
            }
        });
    }

    /** He opens the gate, which is the whole reason the conversation exists. */
    private void letThemIn(Conversation c) {
        c.player("Yes, I'd like to talk to Grand Vizier Erkle.");
        c.npc("Ok, very well...");
        c.npc("You need to go into the building on the left, he's inhis study.");
        c.message("The guard unlocks the gate and opens it for you.");
        c.npc("Good Luck!");
    }

    // ----------------------------------------------------- Jungle Forester --

    /**
     * The three foresters on the jungle's northern edge, from
     * Transcript:Jungle Forester.
     *
     * He is not a quest-giver and not a gate; he is a man with a bull roarer in
     * his pocket who will swap it for a look at the finished map, and the
     * roarer is the only way anyone ever meets Gujuo. The pass before this one
     * gave him five lines in Gujuo's voice -- "Bwana" and all -- which he never
     * uses. He talks like a surveyor, because that is what he is.
     *
     * The transcript's two states are "Before the quest" and "During and
     * After", and the only difference between them is that the second knows
     * about the map. The wiki's {{sic}} on "myway", "thoughtfull" and "incase"
     * is kept, along with the doubled "to to" in the line about escaping the
     * jungle.
     */
    private void forester(final Npc npc) {
        final Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        c.npc("Hello friend, you're a long way from civilisation!");
        if (!questStarted()) {
            c.picker(new Choice("What do you do here?", "Who are you?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        foresterTrade(c);
                        return;
                    }
                    c.player("Who are you?");
                    c.npc("I'm a jungle forester,");
                    c.npc("Names mean little in this part of the world.");
                    c.picker(new Choice("What do you do here?",
                                         "How do I get into the jungle") {
                        public void picked(int option, Conversation c) {
                            if (option == 0) {
                                foresterTrade(c);
                                return;
                            }
                            foresterWayIn(c);
                            c.picker(new Choice("So someone managed to get into the Kharazi Jungle?",
                                                 "What do you do here?",
                                                 "Ok thanks") {
                                public void picked(int option, Conversation c) {
                                    if (option == 0) {
                                        c.player("So someone managed to get into the Jungle?");
                                        c.npc("Yes, he said he was from some place...near the Barbarian outpost.");
                                        c.npc("Mentioned something about a legend ?");
                                        c.npc("It meant nothing to me though.");
                                        return;
                                    }
                                    if (option == 1) {
                                        foresterTrade(c);
                                        return;
                                    }
                                    foresterBye(c);
                                }
                            });
                        }
                    });
                }
            });
            c.start();
            return;
        }
        c.picker(new Choice("How do I get into the jungle",
                             "What do you do here?",
                             "Have you seen any natives in the jungle?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    foresterTrade(c);
                    return;
                }
                if (option == 2) {
                    foresterNatives(c);
                    return;
                }
                foresterWayIn(c);
                c.picker(new Choice("Well, in fact I plan to map that area myself.",
                                     "Are you calling me foolish?",
                                     "Have you seen any natives in the jungle?",
                                     "Ok thanks") {
                    public void picked(int option, Conversation c) {
                        if (option == 1) {
                            c.player("Are you calling me foolish?");
                            c.npc("No, of course not...");
                            c.npc("Sorry, I have to be on myway...");
                            return;
                        }
                        if (option == 2) {
                            foresterNatives(c);
                            return;
                        }
                        if (option == 3) {
                            foresterBye(c);
                            return;
                        }
                        c.player("Well, in fact I plan to map that area myself.");
                        c.message("The forester looks very interested..");
                        c.npc("Oh, well, that sounds quite good actually...");
                        c.npc("Sorry if I sounded rude before, it just didn't seem like a good idea to me.");
                        c.npc("I guess I just wouldn't want to do it myself.");
                        c.npc("But a map of that area would certainly be a big task.");
                        c.npc("And it would certainly be very useful...");
                        c.message("The forester looks very thoughtfull");
                        c.npc("Hey, if you manage to complete it, be sure to let me take a look!");
                        c.npc("Well, best of luck with it, I'm sure you're going to need it.");
                        c.picker(new Choice("Do you have any other tips about the Kharazi jungle?",
                                             "Have you seen any natives in the jungle?",
                                             "Ok thanks") {
                            public void picked(int option, Conversation c) {
                                if (option == 0) {
                                    c.player("Do you have any other tips about the Kharazi jungle?");
                                    c.npc("Not really, but I would say be careful, it's a dangerous place.");
                                    c.npc("And good luck.");
                                    return;
                                }
                                if (option == 1) {
                                    foresterNatives(c);
                                    return;
                                }
                                foresterBye(c);
                            }
                        });
                    }
                });
            }
        });
        c.start();
    }

    /** "What do you do here?" -- and the hint that the map is worth something. */
    private void foresterTrade(Conversation c) {
        c.player("What do you do here?");
        c.npc("I'm a forester, and I specialise in exotic woods.");
        c.npc("I've not managed to penetrate the Kharazi jungle very far,");
        c.npc("but I have found some interesting specimens of trees.");
        c.npc("If you do happen to get into the Kharazi jungle, do come and let me know.");
        c.npc("I'd love to be able to safely navigate my own way in and out.");
    }

    /**
     * "How do I get into the jungle" -- he does not know, and says so at
     * length. The menu is short of the line the player actually speaks; both
     * wordings are Jagex's, so the label stays clipped and the line does not.
     */
    private void foresterWayIn(Conversation c) {
        c.player("How do I get into the Kharazi jungle?");
        c.npc("Well, I've not managed it yet,");
        c.npc("But I heard that someone managed to find a way in..");
        c.npc("But they only just managed to to escape the jungle with their lives.");
        c.npc("Apparently he was on a mission to map the area.");
        c.npc("How foolish is that?");
    }

    /** "Have you seen any natives in the jungle?" -- the roarer, described but not given. */
    private void foresterNatives(Conversation c) {
        c.player("Have you seen any natives in the jungle?");
        c.npc("Well, I've heard some funny sounds...");
        c.npc("And I think I've seen a native...but I'm not sure");
        c.npc("They generally don't like to be seen I guess...");
        c.npc("But I found an item that you might be interested in.");
        c.npc("You swing it above your head and it makes a strange sound,");
        c.npc("it seems to attract their attention.");
        c.picker(new Choice("Can I have the item please?", "Ok thanks") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    foresterBye(c);
                    return;
                }
                c.player("Can I have the item please?");
                c.npc("Well, I wish I could give it to you.");
                c.npc("However, I have grown fond of it.");
                c.npc("And it may help me incase I get lost in the jungle.");
                c.picker(new Choice("Will you trade something for it?", "Ok thanks") {
                    public void picked(int option, Conversation c) {
                        if (option != 0) {
                            foresterBye(c);
                            return;
                        }
                        c.player("Will you trade something for it?");
                        c.npc("Well, if you have something interesting, let me have a look at it");
                        c.npc("and I'll offer you something in return...");
                        c.npc("OK, I have to go now, but it's been nice talking with you.");
                    }
                });
            }
        });
    }

    private void foresterBye(Conversation c) {
        c.player("Ok thanks");
        c.npc("You're welcome!");
        c.npc("See you around...");
    }

    /**
     * Showing him the finished map, which is where the bull roarer comes from.
     *
     * He offers the roarer either way round -- volunteered if the player just
     * says yes, described in detail if they ask what is in it for them -- and a
     * player who refuses can come back and try again, since the map is not
     * taken and neither is the copy.
     */
    private void showForester(final Npc npc) {
        final Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .message("You show the completed map of Kharazi Jungle to the Forester.");
        if (past(ROARER)) {
            c.npc("It's a great map, thanks for letting me take a copy!")
             .npc("It has helped me out a number of times now.")
             .start();
            return;
        }
        c.npc("*Gasp*")
         .message("The jungle forester looks speechless.")
         .npc("This is very impressive!")
         .npc("I'm amazed, it's just great!")
         .npc("Do you mind if I make a copy of it, and I'll give you an item in return.")
         .picker(new Choice("Yes, go ahead make a copy!",
                             "What will you give me in return?",
                             "Sorry, I must complete my quest.") {
             public void picked(int option, Conversation c) {
                 if (option == 2) {
                     c.player("Sorry, I must complete my quest.");
                     c.npc("Very well friend, I understand, I must be on my way as well.");
                     c.message("The Jungle Forester seems a bit annoyed...and wanders off.");
                     return;
                 }
                 if (option == 1) {
                     c.player("What will you give me in return?");
                     c.npc("Well, I can offer you this?");
                     c.message("The Jungle Forester takes out a strange looking object.");
                     c.message("It looks like a wooden pole, with string attached to one end.");
                     c.message("And at the other end of the string is shaped piece of wood.");
                     c.npc("If you swing this above your head, it makes a strange sound.");
                     c.npc("I noticed that it attracts the attention of the natives.");
                     c.npc("Is it a deal? Can I make a copy of your map?");
                     c.picker(new Choice("Yes, go ahead make a copy!",
                                          "Sorry, I must complete my quest.") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 c.player("Sorry, I must complete my quest.");
                                 c.npc("Very well friend, I understand, I must be on my way as well.");
                                 c.message("The Jungle Forester seems a bit annoyed...and wanders off.");
                                 return;
                             }
                             foresterCopy(c);
                         }
                     });
                     return;
                 }
                 foresterCopy(c);
             }
         })
         .start();
    }

    /** He copies the map and hands over the roarer. The thanks really is said twice. */
    private void foresterCopy(Conversation c) {
        // The deal is done the moment the player agrees -- a walk-off during
        // the copying scene must not cost them the roarer.
        c.then(new Effect() {
            public void run(Conversation c) {
                give(BULL_ROARER);
                step(ROARER);
            }
        });
        c.player("Yes, go ahead make a copy!");
        c.npc("Many thanks friend.");
        c.message("The Jungle Forester takes out some parchment and some charcoal.");
        c.message("He studiously renders another copy of your map.");
        c.npc("Many thanks friend.");
        c.message("He takes out a strange looking object and hands it to you.");
        c.npc("Here, I won't be needing this any longer, and it may help you.");
        c.npc("Whenever I've used it before, it attracted the attention of jungle natives.");
    }

    // -------------------------------------------------------------- Gujuo --

    /**
     * The Kharazi tribesman, who is not there until the roarer is swung and is
     * gone again the moment the conversation ends.
     *
     * Every line here is the transcript's. Its spelling is left exactly as
     * recorded -- "Grettings Bwana", "Greetins", "beatifull", "gratefull",
     * "apreciate", "problably" -- because that is what a player saw on screen
     * and correcting it would be a quieter kind of invention. The one repair
     * is "we witnessed your fight with the Demon from some distance awa",
     * which is a transcriber's truncated word rather than anything Jagex could
     * have displayed, and is written "away".
     *
     * He carries nine conversations depending on where the quest has got to,
     * and three of them are load-bearing: the one that explains the golden
     * bowl and the sacred pool, the blessing itself, and the one that names
     * Snake weed and Ardrigal. A player who walks away from any of them can
     * swing the roarer again and get it back.
     */
    private void gujuo(final Npc npc) {
        final Player p = getOwner();
        if (past(REPLACED) && !past(GIFT)) {
            gift(npc);
            return;
        }
        if (past(GIFT)) {
            afterwardsGujuo(npc);
            return;
        }
        if (past(WATERED)) {
            grownTree(npc);
            return;
        }
        if (past(GERMINATED) && !past(KNOWS_HERBS)) {
            herbLesson(npc);
            return;
        }
        if (past(DEMON1) && !past(GERMINATED)) {
            afterFirstDemonGujuo(npc);
            return;
        }
        if (past(KNOWS_WATER) && !past(BLESSED) && holds(BOWL)) {
            blessing(npc, false);
            return;
        }
        if (past(SAW_SHAMAN) && !past(KNOWS_WATER)) {
            waterLesson(npc);
            return;
        }
        /*
         * Knows about the water but left without the sketch -- the lesson
         * repeats until the drawing is in hand, or there is no way to know
         * the bowl's shape.
         */
        if (past(KNOWS_WATER) && !past(BLESSED) && !marked(SKETCH_BIT)) {
            waterLesson(npc);
            return;
        }
        if (!past(MET_GUJUO)) {
            firstMeeting(npc);
            return;
        }
        if (past(DEEP)) {
            new Conversation(p, npc)
                .npc("I have visited Ungadulu in the caves, he is hard at work studying..")
                .npc("He looks well!")
                .npc("How is your quest Bwana ?")
                .player("I have found a way into the caves !")
                .npc("That's great Bwana, good luck with your quest...")
                .npc("and take care!")
                .options(new Choice("Do you know anything more about the caves?",
                                    "Who is Viyeldi?",
                                    "Ok thanks for your help.") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("I am sorry to say that I don't Bwana.");
                            c.npc("You will need to explore that area,");
                            c.npc("but use your wits, and you may be lucky.");
                        } else if (option == 1) {
                            c.npc("Well, I have heard that name before, perhaps from the eldars.");
                            c.npc("Ah, yes, I think that is the name of the wizard who first");
                            c.npc("went in search of the source.");
                            c.npc("Be wary of him Bwana, he may try to trick you.");
                        } else {
                            c.npc("You are more than welcome bwana...");
                        }
                        leaves(c);
                    }
                })
                .start();
            return;
        }
        /*
         * Everything that is not one of the scripted states -- the player has
         * met him and is between errands. He greets them and goes; the roarer
         * brings him back.
         */
        new Conversation(p, npc)
            .npc("How goes your quest to release Ungadulu Bwana?")
            .then(new Effect() {
                public void run(Conversation c) {
                    leaves(c);
                }
            })
            .start();
    }

    /**
     * Gujuo's parting line. Four of them, one at random, and then he is gone --
     * the transcript files them under "Despawn dialogue" and points every
     * conversation at them.
     */
    private void leaves(Conversation c) {
        String[] lines = {
            "I have to collect herbs now Bwana...",
            "I am tired Bwana, I must go and rest...",
            "I must visit my people now...",
            "I must go and hunt now Bwana..",
        };
        c.npc(lines[DataConversions.random(0, lines.length - 1)]);
    }

    /** The first swing of the roarer that he answers. */
    private void firstMeeting(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Grettings Bwana...")
            .npc("Why do you make such strange sounds and disturb the peace of the jungle?")
            .options(new Choice("I was hoping to attract the attention of a native.",
                                "Sorry, it was a mistake?") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Very good Bwana...however, it begs the question...");
                        c.npc("What are you doing in the Kharazi jungle");
                    } else {
                        c.npc("Well, it had the desired effect...");
                        c.npc("I am Gujuo, proud member of the Kharazi tribe.");
                        c.npc("What did you want to talk about Bwana ?");
                    }
                    c.options(new Choice("I want to develop friendly relations with your people.",
                                         "I'm lost, can you show me the way out?") {
                        public void picked(int option, Conversation c) {
                            if (option != 0) {
                                c.npc("Yes Bwana...");
                                c.npc("I can take you to the edge of the Kharazi jungle.");
                                c.npc("Would you like me to take you?");
                                c.options(new Choice("Yes Please...", "No thanks...") {
                                    public void picked(int option, Conversation c) {
                                        if (option == 0) {
                                            c.npc("Follow me...");
                                            c.then(new Effect() {
                                                public void run(Conversation c) {
                                                    teleport(JUNGLE_MOUTH_X, JUNGLE_MOUTH_Y);
                                                }
                                            });
                                            leaves(c);
                                            return;
                                        }
                                        c.npc("As you wish...");
                                        c.npc("Again, Bwana, What is it that brings you to the Kharazi jungle?");
                                        leaves(c);
                                    }
                                });
                                return;
                            }
                            c.npc("Very good Bwana...this is indeed a very pleasant gesture.");
                            c.npc("However, my people are very distributed throughout the Kharazi jungle.");
                            totemPole(c);
                        }
                    });
                }
            })
            .start();
    }

    /**
     * The chain that gets from "hello" to "there is a shaman in a cave".
     *
     * It is five nested single-option menus in the transcript. They are kept
     * as menus rather than flattened because a player who backs out of one of
     * them and swings the roarer again should land back at the top, and that
     * only works if the branch points are real.
     */
    private void totemPole(Conversation c) {
        c.options(new Choice("Can you get your people together ?") {
            public void picked(int option, Conversation c) {
                c.npc("All of my people normally congregate around a totem pole,");
                c.npc("But ours has been polluted by an evil spirit.");
                c.npc("It has been transformed,");
                c.npc("and now our people are afraid to approach it...");
                c.npc("We tried to drive the evil spirit out of the totem pole,");
                c.npc("but it does not seem to work.");
                c.options(new Choice("What can we do instead then?") {
                    public void picked(int option, Conversation c) {
                        c.npc("We could try to make a new totem pole.");
                        c.npc("However, we need to make it from the trunk of the");
                        c.npc("sacred Yommi tree.");
                        c.options(new Choice("How do we make the totem pole?") {
                            public void picked(int option, Conversation c) {
                                c.npc("First we need to plant a sacred Yommi tree..");
                                c.npc("It is a magical tree of great power, however, our Shaman..");
                                c.npc("Ungadulu is the only person with the seeds for this tree.");
                                c.npc("And I fear that it is impossible to get some seeds.");
                                c.npc("He is being held against his will in some caves in");
                                c.npc("north western part of the Kharazi jungle.");
                                releaseHim(c);
                            }
                        }.says(0, "How do we make a totem pole?"));
                    }
                });
            }
        });
    }

    /** The directions to the three rocks, and the only line that sets a stage. */
    private void releaseHim(Conversation c) {
        c.options(new Choice("I will release Ungadulu...",
                             "Oh well, sorry to hear about that ?") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("Yes Bwana, perhaps we will become friends sometime in the future...");
                    c.npc("But not today...");
                    c.npc("Ungadulu has problably lost his mind anyway...");
                    c.npc("it is most likely a lost cause...");
                    leaves(c);
                    return;
                }
                // Committed on the answer, not after the directions -- a
                // walk-off mid-speech must not unmeet Gujuo.
                c.then(new Effect() {
                    public void run(Conversation c) {
                        step(MET_GUJUO);
                    }
                });
                c.npc("You make me very happy Bwana...");
                c.npc("In the North western part of this Kharazi jungle area, near some great cliffs.");
                c.npc("You will find three rocks that form a triangle shape.");
                c.npc("They are flanked by the palm which also forms the divine geometry");
                c.npc("You will find that they cover a small entrance...");
                c.npc("That is where Ungadulu is being kept,");
                c.npc("If you can free him, he will entrust to you some of the sacred Yommi tree seeds.");
                leaves(c);
            }
        });
    }

    /**
     * Where the pure water comes from and what has to carry it, after the
     * player has looked through the flames and seen Ungadulu burning.
     *
     * The sketch of the bowl comes out of the "What kind of a vessel?" branch,
     * so a player who takes the other branches and leaves gets the knowledge
     * without the drawing and has to come back for it. That is how it is
     * recorded and it is not a bug.
     */
    private void waterLesson(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("How goes your quest to release Ungadulu Bwana?")
            .options(new Choice("Ungadulu looks strange.",
                                "I need to douse some flames with pure water.",
                                "Ungadulu called me 'Vacu', what does that mean?",
                                "Ok thanks for your help.") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Be wary Bwana.");
                        c.npc("There are many unknown spirits that reside in these dark areas.");
                        c.npc("You may be tricked by an unknown force...");
                        unknownForces(c);
                        return;
                    }
                    if (option == 2) {
                        c.npc("It seems that Ungadulu has started to lose his senses.");
                        c.npc("In our native and ancient history,");
                        c.npc("the Vacu were the servants of the evil spirits from the underworld.");
                        c.npc("Originally they were priests who had summoned spirits of our ancestors,");
                        c.npc("but they were enslaved...along with the rest of the village.");
                        c.npc("But this is ancient history and is most likely a myth,");
                        c.npc("a story told to frighten poorly behaved children...");
                        leaves(c);
                        return;
                    }
                    if (option == 3) {
                        c.npc("You are more than welcome bwana...");
                        leaves(c);
                        return;
                    }
                    // The lesson counts from the asking, not the last line.
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            step(KNOWS_WATER);
                            mark(ASKED_WATER);
                        }
                    });
                    c.npc("This sounds very strange Bwana...but maybe I can help.");
                    c.npc("There is a pool of water that is sacred to us...");
                    c.npc("It is located in the middle of the Kharazi jungle");
                    c.npc("The water contains special properties but it can only");
                    c.npc("be contained in a blessed vessel made from metal of the sun.");
                    c.npc("The water is difficult to get to,");
                    c.npc("but I am sure you will manage to claim some.");
                    sunMetal(c);
                }
            })
            .start();
    }

    /** "Take not anything as it might first appear." */
    private void unknownForces(Conversation c) {
        c.options(new Choice("What kind of unknown forces...") {
            public void picked(int option, Conversation c) {
                c.npc("Strange spirits that our forefathers summoned for visions.");
                c.npc("They haunt the underworld and caves that exist in this area.");
                c.npc("Take not anything as it might first appear.");
                c.options(new Choice("How did they summon the spirits?") {
                    public void picked(int option, Conversation c) {
                        c.npc("I am unlearned in such matters.");
                        c.npc("But I am told of sacred patterns that are scored on the ground");
                        c.npc("to bind the spirit and confine it...");
                        c.npc("But that is all I know.");
                        leaves(c);
                    }
                });
            }
        });
    }

    /** Gold, said without ever saying gold, and the drawing of the bowl. */
    private void sunMetal(Conversation c) {
        c.options(new Choice("Metal of the sun, what is that?",
                             "What kind of vessel?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("It is a bright and precious metal that is very rare.");
                    c.npc("It is the same glorious colour as the sun and it never loses");
                    c.npc("it's wonderous lustre...");
                    c.npc("A blessed vessel made of this metal protects the purity of the water.");
                    c.options(new Choice("Where can I find this metal?") {
                        public void picked(int option, Conversation c) {
                            c.npc("It is found in some rocks and must be extracted.");
                            c.npc("It has a magical ability over some men and women, it can posess them.");
                            c.npc("They fall within it's power and seek to gain more and more of this");
                            c.npc("precious metal for themselves.");
                            c.npc("A blessed vessel made of this metal protects the purity of the water.");
                            leaves(c);
                        }
                    });
                    return;
                }
                c.npc("A vessel made of sun metal, but it can be of any shape.");
                c.npc("However, it must be blessed.");
                c.npc("Similar to the picture I have already given you.");
                c.npc("Here, have this as an example...I pray that it will help you.");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (!marked(SKETCH_BIT)) {
                            mark(SKETCH_BIT);
                            give(SKETCH);
                        }
                    }
                });
                c.options(new Choice("How do I bless the bowl.") {
                    public void picked(int option, Conversation c) {
                        c.npc("When you have made a bowl, bring it to me and I will help.");
                        c.npc("But you need to ensure that you are devout and have faith.");
                        c.npc("Your ability in prayer will be thoroughly tested.");
                        leaves(c);
                    }
                });
            }
        }.says(1, "What kind of a vessel?"));
    }

    /**
     * The blessing: a prayer check and not a skill check.
     *
     * Forty-two prayer points, and failing spends them -- the guides warn
     * players to bring a prayer potion for exactly this. The chant is the
     * transcript's, three of his to two of the player's, and the player's
     * second line is longer than their first, which is how it is recorded.
     *
     * There are two openings, one for walking up to him carrying the bowl and
     * one for using the bowl on him, and they differ only in the greeting.
     */
    private void blessing(final Npc npc, boolean usedOnHim) {
        final Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        if (usedOnHim) {
            c.npc("Aha Bwana, well done, you have made the golden bowl.");
            c.npc("Would you like me to show you how to bless it.");
        } else {
            c.npc("Greetings Bwana.");
            c.npc("Ah I see you have the golden bowl !");
            c.npc("Would like me to show you how to bless it?");
        }
        c.options(new Choice("Yes please.", "No thanks, I'll wait.") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("Very well, let me know when you want to try?");
                    c.npc("How goes your quest to release Ungadulu Bwana?");
                    leaves(c);
                    return;
                }
                if (p.getCurStat(PRAYER) < 42) {
                    c.npc("Bwana, I am very sorry,");
                    c.npc("But you are too inexperienced to bless this bowl.");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            drain(PRAYER, 0);
                        }
                    });
                    leaves(c);
                    return;
                }
                // Blessed when the chant begins -- a walk-off mid-ritual must
                // not leave an unblessed bowl the player believes is done.
                c.then(new Effect() {
                    public void run(Conversation c) {
                        swap(BOWL, BOWL_BLESSED);
                        step(BLESSED);
                    }
                });
                c.npc("Very well Bwana...");
                c.npc("Ohhhhhmmmmmm");
                c.player("Oooooommmmmmmmmm");
                c.npc("Ohhhhhmmmmmm");
                c.player("Oooooohhhhmmmmmmmmmm");
                c.npc("Ohhhhhmmmmmm");
                leaves(c);
            }
        });
        c.start();
    }

    /** "Ungadulu is free, he was possesed by a demon and I killed it." */
    private void afterFirstDemonGujuo(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("How goes your Quest to release Ungadulu?")
            .options(new Choice("Ungadulu is free, he was possesed by a demon and I killed it.",
                                "I have the Yommi tree seeds.",
                                "What do I do now?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("You are indeed brave Bwana, a truly fearsome warrior to take on");
                        c.npc("Such an enemy! Well Done!");
                    } else if (option == 1) {
                        c.npc("That's great Bwana. Now you just need to germinate");
                        c.npc("the seeds and then plant them in some fertile soil.");
                        c.npc("I'm sure that Ungadulu has explained all this to you already.");
                    } else {
                        c.npc("If you have the Yommi tree seeds, you will need to germinate them.");
                        c.npc("You need to place the seeds into pure water.");
                        c.npc("And they will begin to sprout tiny shoots...");
                        c.npc("You can then plant them in fertile soil.");
                    }
                    leaves(c);
                }
            })
            .start();
    }

    /**
     * The pool has gone, and the way to the source of it.
     *
     * This is the conversation that hands over the bravery potion recipe, and
     * it is the only place Snake weed and Ardrigal are named, so it is the one
     * that has to be reachable again if a player walks off in the middle of it.
     */
    private void herbLesson(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("I have visited Ungadulu in the caves, he is hard at work studying..")
            .npc("He looks well!")
            .npc("How is your quest Bwana ?")
            .options(new Choice("The water pool has dried up and I need more water.",
                                "Where can I get more water for the Yommi tree?",
                                "I searched the catacombs thoroughly but found nothing else.",
                                "If I went in search of the source, could you help me?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("This is indeed a bad omen Bwana, that pool is sacred to us...");
                        c.npc("I have seen it and it is full of filth, it is not natural...");
                        c.npc("I suspect that some evil is at work here.");
                        leaves(c);
                        return;
                    }
                    if (option == 2) {
                        c.npc("Perhaps the location has been hidden or buried under a rubble?");
                        c.npc("These stories were told to me as a child by the village elders.");
                        c.npc("They were probably meant to frighten us away from the caves.");
                        c.npc("It could all just be a myth !");
                        c.npc("You should perhaps talk to Ungadulu, he may know something ?");
                        c.npc("Perhaps there is another way to get to the source of the stream?");
                        c.npc("But I am not sure where it is...");
                        leaves(c);
                        return;
                    }
                    if (option == 3) {
                        bravery(c);
                        return;
                    }
                    c.npc("If the pool of sacred water has dried up,");
                    c.npc("there may be a way to get to the source of the spring.");
                    c.npc("But it is said to be very, very dangerous.");
                    c.options(new Choice("Where is the source of pure water ?") {
                        public void picked(int option, Conversation c) {
                            c.npc("I am not sure...");
                            c.npc("But I have heard that deeper in the Catacombs where you found Ungadulu,");
                            c.npc("deep underground,");
                            c.npc("There is a terrible place guarded by the spirits of the undead.");
                            c.npc("Since they died trying to find the source of the stream,");
                            c.npc("They are cursed to guard it for all eternity.");
                            c.npc("The first to seek the source was said to be a high level sorcerer.");
                            c.npc("He created a powerfull spell in the caves,");
                            c.npc("Now, all those who venture near are overcome by a supernatural fear...");
                            c.npc("With all my heart Bwana, I would never go near such a place.");
                            bravery(c);
                        }
                    }.says(0, "Where is the source of the spring of pure water ?"));
                }
            }.says(0, "The water pool has dried up and I need more pure water."))
            .start();
    }

    /** The recipe, and the two herbs by name. KNOWS_HERBS is set here. */
    private void bravery(Conversation c) {
        // The recipe is learnt as he starts teaching it -- a walk-off must
        // not leave a player who heard the herbs but "has no idea" mixing.
        c.then(new Effect() {
            public void run(Conversation c) {
                step(KNOWS_HERBS);
            }
        });
        c.npc("Well, if you are sure you want to go.");
        c.npc("I will assist as much as I can.");
        c.npc("You will need the bravery of the Jungle lion,");
        c.npc("if you are to go into that forbidden place.");
        c.npc("I can give you the recipe for a potion to help with that.");
        c.npc("You will need to find two herbs, Snake weed and ardrigal.");
        c.npc("Add them both to a vial of water,");
        c.npc("and you will walk with the bravery of the lion.");
        c.options(new Choice("Where can I find Snake weed ?",
                             "Where can I find ardrigal.",
                             "Will I need this potion? I feel brave enough as I am.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Snake weed is usually found by swampy marshy areas.");
                    c.npc("It is not very common and it may be quite difficult to find.");
                    c.npc("There is some marsh to the south of Tai Bwo Wannai village,");
                    c.npc("The herb grows near Jungle Vines, so check all around very carefully.");
                } else if (option == 1) {
                    c.npc("Ardrigal is often found growing near to large groups of palms.");
                    c.npc("Such a collection exists in the North. If you head east out of");
                    c.npc("Tai Bwo Wannai village you should come across them.");
                    c.npc("The herb grows in the shade of the palm so check carefully.");
                } else {
                    c.npc("I would urge you to take it, Bwana,");
                    c.npc("I have heard that the caves are protected by supernatural");
                    c.npc("fear that renders even the bravest man to a trembling wreck.");
                    c.npc("You will need all your wits about you when dealing with");
                    c.npc("the terrors that exist down there.");
                }
                leaves(c);
            }
        });
    }

    /** Water drawn from the source, tree grown: how to turn it into a pole. */
    private void grownTree(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello Bwana, I am very pleased to see you again.")
            .npc("Things seem much happier now in the Kharazi Jungle.")
            .npc("I suspect that it is down to your good doings !")
            .options(new Choice("I found the source of the spring and I got the water.",
                                "How do I make the totem pole?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Great Bwana, you are truly a brave warrior.");
                        c.npc("Now you can try to grow the Yommi tree in earnest and make the totem pole.");
                        c.npc("You are indeed very brave Bwana,");
                        c.npc("We have noticed a difference in the Kharazi jungle,");
                        c.npc("The tree's seem to sing again.");
                        c.npc("And we have you to thank for it.");
                        leaves(c);
                        return;
                    }
                    c.npc("You will need to grow the Yommi tree to full height.");
                    c.npc("And then, before it rots. you must chop it down.");
                    c.npc("Once you have felled the tree, you need to trim the branches.");
                    c.npc("And finally, you need to craft the totem pole out of the trunk.");
                    c.npc("You'll need a very sharp, very tough axe to do all this.");
                    c.npc("But once you have completed the totem pole.");
                    c.npc("You will need to use it to replace a totem pole that already exists.");
                    c.npc("As they're all placed on sacred areas to my people.");
                    leaves(c);
                }
            })
            .start();
    }

    /** The gilded totem pole, which is the token Radimus asked for. */
    private void gift(final Npc npc) {
        new Conversation(getOwner(), npc)
            // The token changes hands up front -- a walk-off mid-praise must
            // not cost the player the gilded totem Radimus is waiting for.
            .then(new Effect() {
                public void run(Conversation c) {
                    give(TOTEM_GILDED);
                    step(GIFT);
                }
            })
            .npc("Greetins Bwana,")
            .npc("We witnessed your fight with the Demon from some distance away")
            .npc("My people are so pleased with your heroic efforts.")
            .npc("Your strength and ability as a warrior are Legendary.")
            .npc("Please accept this as a token of our appreciation.")
            .npc("Please, now consider yourself a friend of my people.")
            .npc("And visit us anytime.")
            .npc("I'll take those Germinated Yommi tree seeds to Ungadulu,")
            .npc("I'm sure he'll apreciate them.")
            .then(new Effect() {
                public void run(Conversation c) {
                    if (holds(SEED_GERMINATED)) {
                        take(SEED_GERMINATED, count(SEED_GERMINATED));
                    }
                    leaves(c);
                }
            })
            .start();
    }

    /**
     * After the gift, and after the quest: the same conversation both times,
     * except that only the unfinished one can replace a lost totem pole.
     */
    private void afterwardsGujuo(final Npc npc) {
        final boolean lost = !completed() && !holds(TOTEM_GILDED);
        String[] options = lost
            ? new String[] { "Do you have any news?",
                             "Where are all your people.",
                             "I've lost the tribal gift you gave me.",
                             "Ok thanks for your help." }
            : new String[] { "Do you have any news?",
                             "Where are all your people.",
                             "Ok thanks for your help." };
        new Conversation(getOwner(), npc)
            .npc("Good day Bwana.")
            .npc("The jungle is especially beatifull today isn't it?")
            .npc("My village people pass on their thanks to you.")
            .options(new Choice(options) {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Just that everything is fine in the jungle with us.");
                        c.npc("And that we are gratefull to you for your help.");
                    } else if (option == 1) {
                        c.npc("My people are all happy living in the jungle.");
                        c.npc("They are still afraid of strangers and will not approach");
                        c.npc("But they are around, none the less.");
                        c.npc("Your story has been woven into the fabric of our society.");
                        c.npc("And we all sing your many praises Bwana.");
                    } else if (lost && option == 2) {
                        c.npc("Well, that wasn't very nice of you.");
                        c.npc("It took us a long time to make that Totem pole.");
                        c.npc("Luckily, I made another one at the same time.");
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                give(TOTEM_GILDED);
                            }
                        });
                        return;
                    } else {
                        c.npc("You are more than welcome bwana...");
                        c.npc("I have work to do Bwana, I may see you again...");
                        return;
                    }
                    leaves(c);
                }
            })
            .start();
    }

    // ------------------------------------------------------------ Ungadulu --

    /**
     * The shaman, from the transcript at Transcript:Ungadulu.
     *
     * That page is not in the per-quest dialogue dump -- it is filed under the
     * npc, not the quest -- which is why an earlier pass here wrote his lines
     * instead of copying them. It is nearly complete: five states, a menu in
     * each, and the wiki's own {{sic}} marks on "possesion", "comit", "forver"
     * and "vulerable", all of which are kept.
     *
     * The one line with no source is the "Leave him to it." menu option, which
     * exists because this server needs a way out of a menu that the real client
     * gave the player for free.
     */
    private void ungadulu(final Npc npc) {
        final Player p = getOwner();
        if (!insideFlames()) {
            throughTheFlames(npc);
            return;
        }
        if (past(DEMON2)) {
            afterSecondDemon(npc);
            return;
        }
        if (marked(VIYELDI_DEAD)) {
            killedViyeldi(npc);
            return;
        }
        if (past(MET_ECHNED) || holds(DARK_DAGGER)) {
            metASpirit(npc);
            return;
        }
        if (past(GERMINATED)) {
            waterDriedUp(npc);
            return;
        }
        if (past(DEMON1)) {
            afterFirstDemon(npc);
            return;
        }
        /*
         * Inside the ring but the demon is still in him. There is no recorded
         * line for this -- the transcript goes straight from the flamewall to
         * the Book of Binding -- so he says the one thing he says everywhere
         * else in that state.
         */
        new Conversation(p, npc)
            .npc("Please run for your life...")
            .start();
    }

    /**
     * Investigating the flamewall, and every later look through it.
     *
     * The three "you look closely" lines are the object's, not his, and only
     * play the first time; after that the player already knows what they are
     * looking at.
     */
    private void throughTheFlames(final Npc npc) {
        final Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        if (!past(SAW_SHAMAN)) {
            c.message("You look closely at the flames, they seem to form a straight wall.")
             .message("Something about them looks very strange, they look completely supernatural.")
             .message("For example, they seem to appear to come from straight out of the ground.")
             .player("Mmmm, pretty!")
             .message("You see a white clad figure in the midst of the flames...")
             .message("You see a white robed figure gesturing to you.")
             .then(new Effect() {
                 public void run(Conversation c) {
                     step(SAW_SHAMAN);
                 }
             });
        }
        c.npc("Please come no closer...the flames will incinerate you.")
         .options(new Choice("How can I extinguish the flames?",
                             "Who are you?",
                             "Where do I get pure water from?",
                             "Jump through the flames.") {
            public void picked(int option, Conversation c) {
                if (option == 3) {
                    /*
                     * Not his. The real game put "jump" on the fire wall itself
                     * once the flames were doused; this server reaches the wall
                     * only through him, so the option lives here.
                     */
                    c.message("You throw yourself through the wall of fire.");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            hurt(DataConversions.random(8, 16));
                            teleport(FLAME_IN_X, FLAME_IN_Y);
                        }
                    });
                    return;
                }
                if (option == 1) {
                    c.npc("I am Ungadulu,trapped here many years now...");
                    c.npc("Leave these caves and save yourself...");
                    c.npc("Wait...get pure water from the pool...above lands...");
                    c.npc("Please Bwana, don't listen to me...run, save yourself...");
                    return;
                }
                if (option == 0) {
                    c.npc("Please don't try to extinguish...");
                    c.npc("Yes, douse the flames with water, pure water...foo...");
                    c.npc("Please, leave now...don't listen to me...");
                    c.npc("I beg you,leave now, don't touch the flames...");
                    return;
                }
                c.npc("Please, leave now...");
                c.npc("...from the above lands...hurry and release me...");
                c.npc("Leave here, please, go...now...");
                c.npc("Hurry, Vacu, the heat kills me...ha ha ha");
                c.message("The Shaman throws himself down on the floor and starts shaking.");
            }
        })
        .start();
    }

    /** Free of the demon: the seeds, how to grow them, and the fire pass. */
    private void afterFirstDemon(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Greetings bwana...many thanks for defeating the demon...")
            .npc("and releasing me from this dreadful possesion...")
            .npc("Pray tell me, what can I do to repay this great favour?")
            .options(new Choice("I need to collect some Yommi tree seeds for Gujuo.",
                                "How do I grow the Yommi tree.",
                                "What do you know about the pure water.",
                                "How do I get out of here?",
                                "Ok, thanks...") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        seeds(c);
                        return;
                    }
                    if (option == 1) {
                        c.npc("A good question Bwana...but it is essentially quite simple.");
                        c.npc("First you will need to soak the seeds in some pure water...");
                        c.npc("This will help to geminate the seed and begin the growing process.");
                        c.npc("The Yommi tree is sacred and is also slightly magical.");
                        c.npc("You need to seek out a patch of fertile earth.");
                        c.npc("Such places are located around the jungle and should give");
                        c.npc("the Yommi tree a good chance of survival.");
                        c.npc("The tree should show some remarkable growth quite early");
                        c.npc("But will slow down, you may be able to speed the process up");
                        c.npc("by watering the tree with more pure water, although");
                        c.npc("it can be difficult to find it.");
                        return;
                    }
                    if (option == 2) {
                        c.npc("Hmmm, the pure water is sacred to us.");
                        c.npc("It is from a sacred spring which is fed from deep underground.");
                        c.npc("It is said that the spring is protected by spirits of long");
                        c.npc("dead adventurers who went in search of the springs source..");
                        c.npc("But it is likely a myth and the source of the spring is buried");
                        c.npc("deep in the ground with no chance of access.");
                        return;
                    }
                    if (option == 3) {
                        wayOut(c);
                        return;
                    }
                    c.npc("My sincerest pleasure Bwana...");
                }
            })
            .start();
    }

    /** Three largish green seeds, once. */
    private void seeds(Conversation c) {
        if (past(SEEDS) && (holds(SEED) || holds(SEED_GERMINATED))) {
            c.npc("You already have some Yommi tree seeds, use those first..");
            c.npc("and let me know how you get along.");
            return;
        }
        c.npc("Oh, yes, Bwana...you will be doing a great favour to our people");
        c.npc("by doing this..however, you must know that it is a difficult task.");
        c.npc("the Yommi tree is difficult to grow. You must have a natural ability");
        c.npc("with such things to have a chance...");
        c.message("The Shaman holds out his gnarly old hand and reveals three largish green seeds.");
        c.npc("Here you go...");
        c.then(new Effect() {
            public void run(Conversation c) {
                give(SEED, 3);
                step(SEEDS);
            }
        });
        c.npc("Accept these with my gratitude...");
        c.npc("You'll need to soak them in pure water before planting them.");
        c.npc("I notice that you are already familiar with it");
        c.npc("to have passed the flaming Octagram.");
    }

    /** The Magical Fire Pass, or a reminder that he has already given it. */
    private void wayOut(Conversation c) {
        c.npc("Well, the way you came, but here...");
        if (holds(FIRE_PASS) || wears(FIRE_PASS)) {
            c.npc("Just use the Magical Fire Pass that I gave you to");
            c.npc("get past the flames...");
            c.npc("Then you should be able to find your way out through");
            c.npc("the cave entrance that you came in.");
            return;
        }
        c.message("The Shaman scrawls a some strange markings onto a piece of paper.");
        c.message("He hands the paper to you...");
        c.then(new Effect() {
            public void run(Conversation c) {
                give(FIRE_PASS);
            }
        });
        c.npc("This will allow you to pass the fire without harm in future.");
    }

    /** After the shallow water has dried up: the Viyeldi caves, and more seeds. */
    private void waterDriedUp(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello Bwana, how goes your quest with the Yommi tree?")
            .options(new Choice("I am on a quest to get more pure water.",
                                "What do you know about the source of the sacred water?",
                                "I need more Yommi tree seeds.",
                                "Ok, thanks...") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Well, good luck with your quest Bwana.");
                        c.npc("You may well find it worthwhile exploring these catacombs.");
                        c.npc("There is said to be an entrance to the Viyeldi caves.");
                        c.npc("Which is where the sacred source of the magic pool exists.");
                        c.npc("Beware though as it is said that the area is cursed.");
                        c.npc("Anyone who is killed seeking the sacred water,");
                        c.npc("will forever be sworn to protect it's secret.");
                        return;
                    }
                    if (option == 1) {
                        c.npc("It is said that the caves where the stream is located,");
                        c.npc("are littered with strange remains of a past civilisation.");
                        c.npc("The dwarves are said to have excavated the area in search");
                        c.npc("of the source of the sacred water.");
                        c.npc("Something bad must have happened because soon the area was cursed.");
                        c.npc("Anyone who entered the area looking for the source of the water,");
                        c.npc("And who died, would be forver cursed to protect the water...");
                        c.npc("...forever...");
                        return;
                    }
                    if (option == 2) {
                        moreSeeds(c);
                        return;
                    }
                    c.npc("My sincerest pleasure Bwana...");
                }
            })
            .start();
    }

    private void moreSeeds(Conversation c) {
        if (holds(SEED) || holds(SEED_GERMINATED)) {
            c.npc("You already have some Yommi tree seeds...");
            c.npc("Use those first and then come back to me if you need any more.");
            c.message("Ungadulu goes back to his studies.");
            return;
        }
        c.message("Ungadulu gives you some more seeds..");
        c.then(new Effect() {
            public void run(Conversation c) {
                give(SEED, 3);
            }
        });
        c.npc("Take more care of these this time around.");
    }

    /** After Echned has been met: the warning about spirits, and the dagger. */
    private void metASpirit(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello Bwana, how goes your quest to find the water ?")
            .options(new Choice("I met a spirit in the Viyeldi Caves.",
                                "The spirit told me to kill Viyeldi.",
                                "Do you know anything about daggers?",
                                "I need more Yommi tree seeds.",
                                "Ok, thanks...") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("You did well to come to me Bwana...");
                        c.npc("As I said, I am an expert in spirits of the underworld...");
                        c.npc("In most circumstances you should just ignore them.");
                        c.npc("However, beware as many spirits will try to trick you.");
                        return;
                    }
                    if (option == 1) {
                        c.npc("That sounds very strange Bwana,");
                        c.npc("I'm glad to see that you didn't comit such a foul act.");
                        c.npc("I can make a spell that would help you to defeat the spirit.");
                        c.npc("But I need an item that belongs to the spirit to make it work.");
                        c.npc("If you have something like that, please show it to me.");
                        c.npc("And I'll give you the spell.");
                        c.npc("Beware of everyone in these caves,");
                        c.npc("I was tricked very easily and was enslaved, as you well know.");
                        return;
                    }
                    if (option == 2) {
                        c.npc("I know something about them, especially magical daggers.");
                        c.npc("If you have a specific one, show it to me and I'll help");
                        c.npc("as much as I can.");
                        return;
                    }
                    if (option == 3) {
                        moreSeeds(c);
                        return;
                    }
                    c.npc("My sincerest pleasure Bwana...");
                }
            })
            .start();
    }

    /**
     * The player took Echned's word for it and killed Viyeldi.
     *
     * He hands over the Holy Force spell either way, which is what makes this
     * recoverable rather than a dead end -- and the transcript notes that he
     * says "I'll take that dagger from you now!" whether the player has a
     * dagger or not, so that line is said unconditionally.
     */
    private void killedViyeldi(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello Bwana, how goes your quest to find the water ?")
            .options(new Choice("I have killed Viyeldi!",
                                "What can we do?",
                                "Do you know anything about daggers?",
                                "Ok, thanks...") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Why on earth did you do that?");
                        c.message("The Shaman screams at you...");
                        c.player("A spirit called Echned Zekin said I had to avenge his spirit");
                        c.player("by killing Viyeldi if I wanted to get the pure water.");
                        c.message("The Shaman puts his head in his hands.");
                        c.npc("Bwana, you have been tricked by a spirit !");
                        c.npc("And you have done the worst thing imaginable.");
                        c.npc("Viyeldi was the sorcerer who controlled the Hero's who protect.");
                        c.npc("the source.");
                        c.npc("The spirits of these hero's are now free");
                        c.npc("to be controlled by other, more powerful forces.");
                        c.npc("Most likely the spirit that tricked you.");
                        return;
                    }
                    if (option == 1) {
                        if (holds(HOLY_FORCE)) {
                            c.npc("You can use that Holy Force spell to try and defeat the spirit.");
                            c.npc("Come back and let me know if I can help in any other way.");
                            return;
                        }
                        c.npc("I am not sure at this time Bwana.");
                        c.npc("Give me a few moments to think.");
                        c.npc("Hmmm....");
                        c.message("The Shaman looks as if he's thinking very deeply.");
                        c.message("The wizened old Shaman hands over a piece of paper.");
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                give(HOLY_FORCE);
                                if (holds(DARK_DAGGER)) {
                                    take(DARK_DAGGER);
                                }
                            }
                        });
                        c.npc("Take this spell and pray that you can defeat");
                        c.npc("this evil spirit before it's too late.");
                        c.npc("I'll take that dagger from you now!");
                        return;
                    }
                    if (option == 2) {
                        c.npc("I know something about them, especially magical daggers.");
                        c.npc("If you have a specific one, show it to me and I'll help");
                        c.npc("as much as I can.");
                        return;
                    }
                    c.npc("My sincerest pleasure Bwana...");
                }
            })
            .start();
    }

    /** The demon beaten at the spring: "You are truly a legend bwana." */
    private void afterSecondDemon(final Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Blessings on you Bwana.")
            .npc("Did you use the spell and kill the spirit?")
            .npc("Do you have the sacred water yet?")
            .message("The Shaman looks so excited about seeing you that he is about to burst.")
            .options(new Choice("Yes, I've killed the Spirit.",
                                "Yes, I've got the water.",
                                "What do I do now?",
                                "Ok, thanks...") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("The spirit actually turned out to be the Demon - Nezikchened.");
                        c.npc("That's truly a miracle Bwana,");
                        c.npc("very few come out of Viyeldi's caves alive.");
                        c.npc("And you managed to defeat Nezikchened a second time?");
                        c.npc("You are truly a legend bwana.");
                        c.npc("Do you have the sacred water yet?");
                        return;
                    }
                    if (option == 1) {
                        c.npc("That is truly great Bwana...well done!");
                        c.npc("You have the spirit of the jungle lion");
                        c.npc("Did you use the spell and kill the spirit?");
                        return;
                    }
                    if (option == 2) {
                        c.npc("Well, you should be able to plant the Yommi tree.");
                        c.npc("And then water it with the sacred water.");
                        c.npc("You should then be able to start making the Totem pole.");
                        c.npc("So long as you have banished the spirit");
                        c.npc("And managed to get some of the sacred water.");
                        return;
                    }
                    c.npc("My sincerest pleasure Bwana...");
                }
            })
            .start();
    }

    // ------------------------------------------------------- Echned Zekin --

    /**
     * The ghost on the rock, who wants Viyeldi dead and is prepared to be
     * patient about it, from Transcript:Echned Zekin.
     *
     * Like Ungadulu, he is filed under the npc rather than the quest, which is
     * why the pass before this one wrote him a short, brisk conversation of its
     * own instead of copying the recorded one. The real one is six states and
     * runs to well over a hundred lines, and the branch it opens -- kill
     * Viyeldi, or refuse and go back to Ungadulu -- is the only real fork in
     * the quest.
     *
     * The wiki's {{sic}} marks on "viscious", "wraithlike" and "disapears" are
     * kept. Two of his states end in a fight rather than a goodbye, and the
     * demon that comes out of him is the same Nezikchened as everywhere else.
     */
    private void echned(final Npc npc) {
        final Player p = getOwner();
        if (past(DEMON2)) {
            /* Ours. He is dead and the transcript stops recording him. */
            say("Nothing rises from the water now.");
            return;
        }
        Conversation c = new Conversation(p, npc)
            .message("A thick, green mist seems to emanate from the water...")
            .message("It slowly congeals into the shape of a body...")
            .message("Which slowly floats towards you.");
        if (holds(DAGGER_GLOWING)) {
            handOver(c, npc);
            c.start();
            return;
        }
        if (holds(HOLY_FORCE)) {
            somethingDifferent(c);
            c.start();
            return;
        }
        if (marked(VIYELDI_DEAD) && past(MET_ECHNED)) {
            c.npc("You have returned and I am ready for you...")
             .npc("I will now reveal myself and spell out your doom.")
             .then(new Effect() {
                 public void run(Conversation c) {
                     revealed();
                 }
             })
             .start();
            return;
        }
        if (past(MET_ECHNED)) {
            c.message("The shapeless entity of Echned Zekin appears in front of you.")
             .npc("Why do you return when your task is still incomplete?")
             .message("There is an undercurrent of anger in his voice.")
             .picker(new Choice("Who am I supposed to kill again?",
                                 "Er I've had second thoughts.",
                                 "I have to be going...") {
                 public void picked(int option, Conversation c) {
                     if (option == 0) {
                         c.player("Who am I supposed to kill again?");
                         c.npc("Avenge upon me the death of Viyeldi, the cruel.");
                         c.npc("And I will give you access to source...");
                         return;
                     }
                     if (option == 1) {
                         c.player("Er I've had second thoughts.");
                         c.npc("It is too late for second thoughts...");
                         c.npc("Do as you have agreed and return to me in all haste...");
                         c.npc("His presence tortures me so...");
                         return;
                     }
                     leaveEchned(c);
                 }
             })
             .start();
            return;
        }
        c.message("In a rasping, barely audible voice you hear the entity speak.")
         .npc("Who disturbs the rocks of Zekin?")
         .message("There seems to be something slightly familiar about this presence.")
         .picker(new Choice("Er...me?", "Who's asking?") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.player("Er...me?");
                     c.npc("So, you desire the water that flows here?");
                     c.picker(new Choice("Yes, I need it for my quest.",
                                          "Not really, I just wondered if I could push that big rock.") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 c.player("Yes, I need it for my quest.");
                                 c.npc("The water babbles so loudly and I am already so tortured.");
                                 c.npc("I cannot abide the sound so I have stoppered the streams...");
                                 c.npc("Care you not for my torment and pain?");
                             } else {
                                 c.player("Not really, I just wondered if I could push that big rock.");
                                 c.npc("The rock must remain, it stoppers the waters that babble.");
                                 c.npc("The noise troubles my soul and I seek some rest...");
                                 c.npc("rest from this terrible torture...");
                             }
                             c.picker(new Choice("Why are you tortured?",
                                                  "What can I do about that?") {
                                 public void picked(int option, Conversation c) {
                                     if (option == 0) {
                                         tortured(c);
                                     } else {
                                         theTask(c);
                                     }
                                 }
                             });
                         }
                     });
                     return;
                 }
                 c.player("Who's asking?");
                 c.message("The hooded, headless figure faces you...it's quite unnerving...");
                 c.npc("I am Echned Zekin...and I seek peace from my eternal torture...");
                 c.picker(new Choice("What can I do about that?",
                                      "Do I know you?",
                                      "Why are you tortured?") {
                     public void picked(int option, Conversation c) {
                         if (option == 0) {
                             theTask(c);
                             return;
                         }
                         if (option == 2) {
                             tortured(c);
                             return;
                         }
                         c.player("Do I know you?");
                         c.npc("I am long since dead and buried, lost in the passages of time.");
                         c.npc("Long since have my kin departed and have I been forgotten...");
                         c.npc("It is unlikely that you know me...");
                         c.npc("I am a poor tortured soul looking for rest and eternal peace...");
                         c.picker(new Choice("Why are you tortured?",
                                              "What can I do about that?") {
                             public void picked(int option, Conversation c) {
                                 if (option == 0) {
                                     tortured(c);
                                 } else {
                                     theTask(c);
                                 }
                             }
                         });
                     }
                 });
             }
         })
         .start();
    }

    /** "Why are you tortured?", which is the long answer to a short question. */
    private void tortured(Conversation c) {
        c.player("Why are you tortured?");
        c.npc("I was robbed of my life by a cruel man called Viyeldi");
        c.npc("And I hunger for revenge upon him....");
        c.npc("It is long since I have walked this world looking for him");
        c.npc("to haunt him and raise terror in his life...");
        c.npc("but tragedy of tragedies, his spirit is neither living or dead");
        c.npc("he serves the needs of the source.");
        c.npc("He died trying to collect the water from this stream,");
        c.npc("and now I hang in torment for eternity.");
        c.picker(new Choice("What can I do about that?",
                             "Can't I just get some water?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    theTask(c);
                    return;
                }
                c.player("Can't I just get some water?");
                c.npc("Yes, you may get some water, but first you must help me.");
                c.npc("Revenge is the only thing that keeps my spirit in this place");
                c.npc("help me take vengeance on Viyeldi and I will gladly remove");
                c.npc("the rocks and allow you access to the water");
                c.npc("What say you?");
                answer(c);
            }
        });
    }

    /** "What can I do about that?" -- the question that ends in a dagger. */
    private void theTask(Conversation c) {
        c.player("What can I do about that?");
        c.npc("I was brutally murdered by a viscious man called Viyeldi");
        c.npc("I sense his presence near by, but I know that he is no longer living");
        c.npc("My spirit burns with the need for revenge, I shall not rest while");
        c.npc("I sense his spirit still.");
        c.npc("If you seek the pure water, you must ensure he meets his end.");
        c.npc("If not, you will never see the source and your journey back must ye start.");
        c.npc("What is your answer? Will ye put an end to Viyeldi for me?");
        answer(c);
    }

    /** Yes or no, asked in three places and answered the same way in all of them. */
    private void answer(Conversation c) {
        c.picker(new Choice("I'll do what I must to get the water.",
                             "No, I won't take someone's life for you.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    accept(c);
                    return;
                }
                c.player("No, I won't take someone's life for you.");
                c.npc("Such noble thoughts, but Viyeldi is not alive.");
                c.npc("He is merely a vessel by which the power of the source");
                c.npc("protects itself.");
                c.npc("If that is your decision, so be it, but expect not to");
                c.npc("gain the water from this stream.");
            }
        });
    }

    /**
     * Taking the job, and the dagger that comes with it.
     *
     * The dagger arrives before the last confirmation rather than after it,
     * which is the transcript's order and not a mistake: a player who then says
     * "I've changed my mind" walks away holding it.
     */
    private void accept(Conversation c) {
        // The bargain is struck on the acceptance -- a walk-off during the
        // speech must not leave the player daggerless with Echned unmet.
        c.then(new Effect() {
            public void run(Conversation c) {
                if (!holds(DARK_DAGGER)) {
                    give(DARK_DAGGER);
                }
                step(MET_ECHNED);
            }
        });
        c.player("I'll do what I must to get the water.");
        c.message("The shapeless spirit seems to crackle with energy.");
        c.npc("You would release me from my torment and the source would");
        c.npc("be available to you.");
        c.npc("However, you must realise that this will be no easy task.");
        c.npc("I will furnish you with a weapon which will help you");
        c.npc("to achieve your aims...");
        c.npc("Here, take this...");
        c.message("The spiritless body waves an arm and in front of you appears");
        c.message("a dark black dagger made of pure obsidian.");
        c.npc("To complete this task you must use this weapon on Viyeldi.");
        c.message("You take the dagger and place it in your inventory.");
        c.npc("Use the dagger I have provided for you to complete this task.");
        c.npc("and then bring it to me when Viyeldi is dead.");
        c.picker(new Choice("Ok, I'll do it.", "I've changed my mind, I can't do it.") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.player("Ok, I'll do it.");
                    c.message("The formless shape shimmers brightly...");
                    c.npc("You will benefit from this decision, the source will be");
                    c.npc("opened to you");
                    c.npc("Bring the dagger back to me when you have completed this task.");
                    return;
                }
                c.player("I've changed my mind, I can't do it.");
                c.npc("The decision is yours but you will have no other way to");
                c.npc("get to the source.");
                c.npc("The pure water you seek will forever be out of your reach.");
                answer(c);
            }
        });
    }

    /** Handing over the dagger with Viyeldi's spirit in it, which is a trap. */
    private void handOver(Conversation c, final Npc npc) {
        c.npc("Aha, I see you have completed your task.");
        c.npc("I'll take that dagger from you now.");
        c.message("The formless shape of Echned Zekin takes the dagger from you.");
        c.message("As a ghostly hand envelopes the dagger, something seems to move");
        c.message("from the black weapon into the floating figure...");
        c.then(new Effect() {
            public void run(Conversation c) {
                take(DAGGER_GLOWING);
            }
        });
        c.npc("Aahhhhhhhhh! As I take the spirit of one departed,");
        c.npc("I will now reveal myself and spell out your doom.");
        c.then(new Effect() {
            public void run(Conversation c) {
                revealed();
            }
        });
    }

    /**
     * The demon coming out of the ghost without being asked to.
     *
     * This is the fight a player gets for handing the dagger over, or for
     * coming back after killing Viyeldi with nothing in hand. It is the same
     * second fight the Holy Force spell starts, reached the other way round.
     */
    private void revealed() {
        say("A terrible fear comes over you.");
        say("You are under attack!");
        raise(25, null, null, "You feel a terrible sense of loss...");
    }

    /**
     * "Something seems different about you..." -- what he says to a player
     * carrying the spell that will undo him, before they use it.
     */
    private void somethingDifferent(Conversation c) {
        c.npc("Something seems different about you...");
        c.npc("Your sense of purpose seems not bent to my will...");
        c.npc("Give me the dagger that you used to slay Viyeldi or taste my wrath!");
        c.picker(new Choice("I don't have the dagger.",
                             "I haven't slayed Viyeldi yet.",
                             "I have something else in mind!",
                             "I have to be going...") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.player("I don't have the dagger.");
                    c.message("The spirit seems to shake with anger...");
                    c.npc("Bring it to me with all haste.");
                    c.npc("Or torment and pain will I bring to you...");
                    c.npc("the spirit extends a wraithlike finger which touches you.");
                    c.npc("You feel a searing pain jolt through your body...");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            hurt(4);
                        }
                    });
                    return;
                }
                if (option == 1) {
                    c.player("I haven't slayed Viyeldi yet.");
                    c.npc("Go now and slay him, as you agreed.");
                    c.npc("If you are forfeit on this.");
                    c.npc("And I will take you as a replacement for Viyeldi !");
                    return;
                }
                if (option == 2) {
                    c.player("I have something else in mind!");
                    c.npc("You worthless Vacu, how dare you seek to trick me.");
                    c.npc("Go and slay Viyeldi as you promised");
                    c.npc("or I will layer upon you all the pain and");
                    c.npc("torment I have endured all these long years!");
                    return;
                }
                leaveEchned(c);
            }
        });
    }

    /** The one way out of both of his later menus. */
    private void leaveEchned(Conversation c) {
        c.player("I have to be going...");
        c.npc("Return swiftly with the weapon as soon as your task is complete.");
        c.message("The spirit slowly fades and then disapears.");
    }

    // ------------------------------------------------------------ Viyeldi --

    /**
     * The mage under the hat, who is dead and does not know it.
     *
     * The whole of Transcript:Viyeldi is one speech and it is a poem in
     * couplets. There is no second conversation recorded, so once he has said
     * it he says it again, and once he is dead the clothes animate and slump
     * without him -- both of those are the transcript's, not a fallback.
     */
    private void viyeldi(final Npc npc) {
        if (marked(VIYELDI_DEAD)) {
            say("Viyeldi falls silent...");
            say("...and the clothes slump to the floor.");
            return;
        }
        new Conversation(getOwner(), npc)
            // The prophecy counts from the first couplet; he will happily
            // recite it all again, so nothing is lost by stepping early.
            .then(new Effect() {
                public void run(Conversation c) {
                    step(PROPHECY);
                }
            })
            .message("And starts talking to you in a shrill, excited voice...")
            .npc("Beware adventurer, lest thee loses they head in search of source.")
            .npc("Bravery has thee been tested and not found wanting..")
            .message("The spirit wavers slightly and then stands proud...")
            .npc("But perilous danger waits for thee,")
            .npc("Tojalon, Senay and Devere makes three,")
            .npc("None hold malice but will test your might,")
            .npc("Pray that you do not lose this fight,")
            .npc("If however, you win this day,")
            .npc("Take heart that see the source you may,")
            .npc("Through dragons eye will you gain new heart,")
            .npc("To see the source and then depart.")
            .then(new Effect() {
                public void run(Conversation c) {
                    step(PROPHECY);
                }
            })
            .start();
    }

    /**
     * The three heroes will not be talked to.
     *
     * Each of their transcripts has an "Attempting to talk" section holding one
     * message and nothing else, and all three are written with the same stray
     * definite article -- "The Irvig Senay does not appear interested in
     * talking". That is Jagex building the line out of the npc's name, so it is
     * built the same way here rather than tidied.
     */
    private void hero(Npc npc) {
        say("The " + npc.getDef().getName() + " does not appear interested in talking");
    }

    // ---------------------------------------------------------- item on npc --

    private void itemOnNpc(Npc npc, InvItem used) {
        if (used == null) {
            return;
        }
        int id = used.getID();
        if (npc.getID() == RADIMUS && (id == TOTEM_GILDED || id == TOTEM
                                   || id == SCROLLS_DONE)) {
            showRadimus(npc, id);
            return;
        }
        if (npc.getID() == UNGADULU) {
            if (id == BOOK_OF_BINDING) {
                firstFight(npc);
                return;
            }
            if (id == VIAL_HOLY) {
                soak(npc);
                return;
            }
            if (id == DARK_DAGGER) {
                daggerToUngadulu(npc);
                return;
            }
        }
        if (npc.getID() == FORESTER && id == SCROLLS_DONE) {
            showForester(npc);
            return;
        }
        if (npc.getID() == ECHNED && id == HOLY_FORCE) {
            secondFight(npc);
            return;
        }
        if (npc.getID() == GUJUO && id == BOWL) {
            blessing(npc, true);
            return;
        }
        say("Nothing interesting happens");
    }

    /**
     * Handing Radimus the three things he might be shown.
     *
     * The gilded pole alone is not enough and the map alone is not enough; he
     * takes both together and only then opens the hall.
     */
    private void showRadimus(final Npc npc, int id) {
        final Player p = getOwner();
        if (id == TOTEM) {
            new Conversation(p, npc)
                .npc("Hello there, how is the quest going?")
                .npc("Hmmm, well, it is very impressive.")
                .npc("Especially since it looks very heavy...")
                .npc("However, it lacks a certain authenticity,")
                .npc("my guess is that you made it.")
                .npc("But I'm not sure why.")
                .npc("We would like to have a really nice display object")
                .npc("to put on display in the Legends Guild main hall.")
                .npc("Do you think you could get something more authentic ?")
                .start();
            return;
        }
        if (id == SCROLLS_DONE) {
            new Conversation(p, npc)
                .npc("Well done Sir, very well done...")
                .npc("However, you'll probably need it while you search")
                .npc("for natives of the Kharazi tribe in the Kharazi jungle.")
                .npc("Remember, we want a very special token of friendship from them.")
                .npc("To place in the Legends Guild.")
                .npc("I'll take the map off your hands once we get the")
                .npc("proof that you have met the natives.")
                .start();
            return;
        }
        if (!holds(SCROLLS_DONE)) {
            new Conversation(p, npc)
                .npc("Sir, this is truly amazing...")
                .npc("However, I need you to complete the map of the ,")
                .npc("Kharazi Jungle before your quest is complete.")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Sir, this is truly amazing...")
            .message("Radimus Erkle orders some guards to take the totem pole,")
            .message("into the main Legends Hall.")
            .then(new Effect() {
                public void run(Conversation c) {
                    take(TOTEM_GILDED);
                    take(SCROLLS_DONE);
                    step(HANDED_IN);
                }
            })
            .npc("That will take pride of place in the Legends Guild")
            .npc("As a reminder of your quest to gain entry.")
            .npc("And so that many other great adventurers can admire your bravery.")
            .npc("Well, it seems that you have completed the tasks I set you.")
            .npc("That map of the Kharazi jungle will be very helpful in future.")
            .npc("Congratulations, welcome to the Legends Guild.")
            .npc("Go through to the main Legends Guild building")
            .npc("and I will join you shortly.")
            .start();
    }

    /** Holy water thrown at Ungadulu weakens what is inside him. */
    private void soak(Npc npc) {
        take(VIAL_HOLY);
        say("You throw the holy watervial at Ungadulu.");
        offer(npc, "Vile serpent...you will pay for that...");
        offer(npc, "What...what happened...why am I all wet?");
    }

    /**
     * The dagger handed over instead of used: Viyeldi lives and the demon has
     * to be fought as itself.
     *
     * "I'll take that dagger from you now!" is his line in the other branch,
     * where the player has already killed Viyeldi; here he takes it as part of
     * making the spell, which is the same trade in the opposite order.
     */
    private void daggerToUngadulu(final Npc npc) {
        final Player p = getOwner();
        if (marked(DAGGER_GIVEN) || holds(HOLY_FORCE)) {
            say("He has already taken one of those from you.");
            return;
        }
        new Conversation(p, npc)
            .message("You hand the dagger over to the Shaman.")
            .message("The Shaman's face turns pale...")
            .npc("This dagger has been made for one purpose only...")
            .npc("Praise the gods that you brought it to me.")
            .npc("I can make you a spell with this item which will force the spirit")
            .npc("to reveal its true self.")
            .npc("Once activated, you will be able to attack it like")
            .npc("a normal creature.")
            .message("The shaman takes the dagger and gives you a folded piece of paper.")
            .then(new Effect() {
                public void run(Conversation c) {
                    take(DARK_DAGGER);
                    give(HOLY_FORCE);
                    mark(DAGGER_GIVEN);
                }
            })
            .npc("Use this spell on the Spirit.")
            .npc("It will force the spirit to show it's true self.")
            .npc("And it will also be vulerable to normal attacks.")
            .start();
    }

    // --------------------------------------------------------- the fights --

    /**
     * The demon, three times, and never twice on the same terms.
     *
     * He drains prayer over the opening of the fight rather than in one
     * instant: a majority the first time but never all of it -- the
     * walkthrough leans on having enough left to cast Paralyze Monster --
     * so the first fight's floor is a third of what the player walked in
     * with, and the second and third keep their recorded targets of
     * twenty-five and twelve (or nothing, depending on the dagger). The
     * four-bite pacing is the liberty. The npc is summoned rather than
     * spawned, because Nezikchened has no placement in the world.
     *
     * The opening line differs each time and comes from Transcript:Nezikchened,
     * so it is passed in rather than said here, along with the one he throws
     * out partway through. The transcript files that second line under "During
     * the fight" and gives no timing for it, so it lands ten seconds in.
     */
    private Npc raise(int prayerTo, String opener, final String during, final String mood) {
        Player p = getOwner();
        stepDrain(prayerTo);
        Npc demon = nearby(NEZIKCHENED);
        if (demon == null) {
            demon = summon(NEZIKCHENED, 600000);
        }
        this.summoned = demon;
        if (opener != null) {
            offer(demon, opener);
        }
        demon.attackPlayer(p);
        final Npc taunting = demon;
        world.getDelayedEventHandler().add(new SingleEvent(p, 10000) {

            public void action() {
                if (taunting.getHits() <= 0) {
                    return;
                }
                if (mood != null) {
                    say(mood);
                }
                if (during != null) {
                    offer(taunting, during);
                }
            }
        });
        return demon;
    }

    /**
     * Opening the book of binding in front of Ungadulu.
     *
     * All six lines are the transcript's. The possessed Ungadulu npc is ours --
     * the transcript describes the light falling on Ungadulu and the demon
     * forming, and this server needs something standing there while that
     * happens.
     */
    private void firstFight(final Npc npc) {
        final Player p = getOwner();
        if (!insideFlames()) {
            say("You cannot reach him through the flames.");
            return;
        }
        if (past(DEMON1)) {
            say("There is nothing left in him to bind.");
            return;
        }
        // The fight may already be running -- a player who fled and opens
        // the book again is re-engaging the demon, not summoning a second
        // reveal on top of the first.
        Npc demon = nearby(NEZIKCHENED);
        if (demon != null) {
            demon.attackPlayer(p);
            return;
        }
        if (nearby(UNGADULU_POSSESSED) != null) {
            return;
        }
        say("You open the book of binding in front of Ungadulu.");
        say("A blinding light fills the room...");
        say("A supernatural light falls on Ungadulu...");
        say("And a mighty demon forms in front of you...");
        summon(UNGADULU_POSSESSED, 6000);
        world.getDelayedEventHandler().add(new SingleEvent(p, 3000){

            public void action() {
                raise(p.getCurStat(PRAYER) / 3,
                      "Curse you foul intruder...your faith will help you little here.",
                      "'Ere near to death ye comes now that ye has meddled in my dealings..",
                      "A sense of hopelessness fills your body...");
            }
        });
    }

    /**
     * The Holy Force spell, cast on the spirit on the rock.
     *
     * Echned screams first and then is not Echned any more; the two speakers in
     * the middle of this are the whole point of the branch.
     *
     * The spell is not consumed. The transcript has a whole section for using
     * it a second or later time -- "Argghhhhh...not again....!" -- which it
     * could not have if the first use took the paper away, and a player who
     * hands the dagger over instead needs it back afterwards.
     *
     * The two transcripts disagree by one word on the demon's line here:
     * Transcript:Nezikchened has "Now I am revealed to you Vacu", Transcript:
     * Echned Zekin has "Now that I am revealed to you Vacu". The demon's own
     * page is followed.
     */
    private void secondFight(final Npc npc) {
        if (past(DEMON2)) {
            say("Nothing happens.");
            return;
        }
        say("You thrust the Holy Force spell in front of the spirit.");
        say("A bright, holy light streams out from the paper spell.");
        if (nearby(NEZIKCHENED) != null) {
            offer(npc, "Argghhhhh...not again....!");
            say("The spirit lets out an unearthly, blood curdling scream...");
            say("The spell seems to weaken the Demon.");
            raise(25, "So you have returned and I am prepared for you now!", null, null);
            return;
        }
        offer(npc, "Argghhhhh...noooooo!");
        say("The spirit lets out an unearthly, blood curdling scream...");
        raise(25, "Now I am revealed to you Vacu, so shall ye perish.", null,
              "A sense of fear comes over you ");
    }

    /**
     * The third fight, which is really up to four fights.
     *
     * A player who killed Viyeldi has to get past San Tojalon, Irvig Senay and
     * Ranalph Devere first, one at a time and each only once; a player who gave
     * the dagger to Ungadulu goes straight to the demon. The three hero bits
     * were the crystals underground and are cleared and reused here, which is
     * safe because the crystals stopped mattering twelve stages ago.
     *
     * The demon does not summon the heroes one at a time in the transcript --
     * he chants once and all three are called, and then the player fights them
     * in order. Here the chant is repeated in front of each one because the
     * transcript's own "Defeating San Tojalon" and "Defeating Irvig Senay"
     * sections repeat it too, which is how the recording says the game did it.
     */
    private void thirdFight() {
        Npc demon = nearby(NEZIKCHENED);
        if (demon == null) {
            demon = summon(NEZIKCHENED, 600000);
        }
        offer(demon, "Now you try to defile my sanctuary...I will teach thee!");
        if (marked(VIYELDI_DEAD)) {
            for (int i = 0; i < HERO_NPC.length; i++) {
                if ((field(HERO_MASK, HERO_SHIFT) & 1 << i) != 0) {
                    continue;
                }
                offer(demon, "You will pay for your disrespect by meeting some old friends...");
                say("The Demon starts chanting...");
                offer(demon, "Protectors of source, alive in death,");
                offer(demon, "do not rest while this Vacu draws breath!");
                say("The demon is summoning the dead hero's from the Viyeldi caves !");
                Npc hero = summon(HERO_NPC[i], 600000);
                this.summoned = hero;
                offer(hero, "Corrupted are we now that Viyeldi is slain..");
                hero.attackPlayer(getOwner());
                return;
            }
            say("The Demon screams in rage...");
            offer(demon, "Raarrrrghhhh!");
            raise(12, "I'll kill you myself !", "Your faith will help you little here.",
                  "You feel a great sense of loss...");
            return;
        }
        raise(getOwner().getCurStat(PRAYER), null, "Your faith will help you little here.",
              "You feel a great sense of loss...");
    }

    /**
     * Everything this quest cares about dying.
     *
     * NPC_KILLED arrives after the npc has already left the world, so nothing
     * here may touch it -- only the player's inventory and the stage.
     */
    private void killed(Npc npc) {
        int id = npc.getID();
        if (id == VIYELDI) {
            mark(VIYELDI_DEAD);
            if (holds(DARK_DAGGER)) {
                swap(DARK_DAGGER, DAGGER_GLOWING);
            }
            say("You see a flash as something travels from Viyeldi into the dagger.");
            say("The dagger seems to glow as Viyeldi crumpels to the floor.");
            say("Viyeldi falls silent...");
            say("...and the clothes slump to the floor.");
            return;
        }
        if (id == NEZIKCHENED) {
            demonFell();
            return;
        }
        for (int i = 0; i < HERO_NPC.length; i++) {
            if (HERO_NPC[i] != id) {
                continue;
            }
            heroFell(i);
            return;
        }
    }

    /**
     * The demon leaving, which he does three times and differently each time.
     *
     * The first two exits are the transcript's word for word. The first one is
     * a parting shot that does damage -- the bolt of energy and the burst of
     * flame both land, which is why this is the only death in the quest that
     * hurts the winner.
     */
    private void demonFell() {
        if (at(BOOK)) {
            step(DEMON1);
            say("Your opponent is retreating");
            say("The demon starts an incantation...");
            say("@yel@Nezikchened : @whi@But I will leave you with a taste of my power...");
            say("As he finishes the incantation a powerful bolt of energy strikes you.");
            hurt(8);
            say("@yel@Nezikchened : @whi@Haha hah ha ha ha ha....");
            say("The demon explodes in a powerful burst of flame that scorches you.");
            hurt(8);
            return;
        }
        if (at(MET_ECHNED)) {
            step(DEMON2);
            say("The Demon seems very angry now...");
            say("You deliver a final devastating blow to the demon, ");
            say("and it's unearthly frame crumbles into dust.");
            return;
        }
        if (at(TOTEM_CUT)) {
            step(DEMON3);
            say("You deliver the final killing blow to the foul demon.");
            say("The Demon crumbles into a pile of ash.");
            say("@yel@Nezikchened: @whi@Arrrghhhh.");
            say("@yel@Nezikchened: @whi@I am beaten by a mere mortal.");
            say("@yel@Nezikchened: @whi@I will revenge myself upon you...");
            Npc me = nearby(NEZIKCHENED);
            new Conversation(getOwner(), me)
                .player("Yeah, yeah, yeah !")
                .player("Heard it all before !")
                .start();
        }
    }

    /**
     * A hero has fallen, which means one of two entirely different things
     * depending on how far the quest has got.
     *
     * Underground he is guarding a crystal and dies politely -- "You have
     * proved yourself of the honour.." and a piece of crystal in midair. Above
     * ground he has been dragged back by the demon and dies screaming, and the
     * demon starts on the next one. Both sequences are the transcript's.
     */
    private void heroFell(int index) {
        if (at(TOTEM_CUT)) {
            setField(HERO_MASK, HERO_SHIFT, field(HERO_MASK, HERO_SHIFT) | 1 << index);
            say("A nerve tingling scream echoes around you as you slay the dead Hero.");
            say("@yel@" + HERO_NAME[index] + ": @whi@Ahhhggggh");
            say("@yel@" + HERO_NAME[index]
                + ": @whi@Forever must I live in this torment till this beast is slain...");
            thirdFight();
            return;
        }
        if (!past(PROPHECY) || past(CRYSTAL)) {
            return;
        }
        if ((field(HERO_MASK, HERO_SHIFT) & 1 << index) != 0) {
            return;
        }
        setField(HERO_MASK, HERO_SHIFT, field(HERO_MASK, HERO_SHIFT) | 1 << index);
        give(HERO_CRYSTAL[index]);
        say("Your opponent is retreating");
        say("A piece of crystal forms in midair and falls to the floor.");
        say("You place the crystal in your inventory.");
    }

    /**
     * Viyeldi will not be attacked by anyone not carrying the dagger meant for
     * him, and neither will anything else that only exists to be part of a
     * scripted fight.
     */
    public boolean refusesAttack(Npc npc) {
        int id = npc.getID();
        for (int i = 0; i < HERO_NPC.length; i++) {
            if (HERO_NPC[i] == id && !at(TOTEM_CUT)) {
                greet(npc, i);
                return false;
            }
        }
        if (id != VIYELDI) {
            return false;
        }
        if (holds(DARK_DAGGER) || wears(DARK_DAGGER)) {
            return false;
        }
        say("Your blows pass straight through him.");
        say("Only one blade in these caves can touch him");
        return true;
    }

    /**
     * What a hero says as the fight in the caves starts.
     *
     * The transcripts file these under "When he attacks the player", because in
     * the real game the three of them are aggressive and the player never gets
     * the first swing. Here they are said when the player walks up to swing,
     * which is the nearest moment this server has to a fight starting. The
     * words are theirs; the trigger is approximate, and that is the only thing
     * about these three that is.
     */
    private void greet(Npc hero, int index) {
        switch (index) {
            case 0:
                offer(hero, "You have entered the Viyeldi caves and your bravery must be tested.");
                say("You are under attack!");
                offer(hero, "Prepare yourself...San Tojalon will test your mettle.");
                break;
            case 1:
                offer(hero, "Greetings Brave warrior, destiny is upon you...");
                say("You are under attack!");
                offer(hero, "Ready your weapon and defend yourself.");
                break;
            default:
                offer(hero, "Upon my honour, I will defend till the end...");
                say("You are under attack!");
                offer(hero, "May your aim be true and the best of us win...");
                break;
        }
    }

    /**
     * The last word of anything this quest kills.
     *
     * It is asked while the npc is still standing, which is the only moment a
     * dying npc can still speak -- by the time NPC_KILLED arrives it has left
     * the world and {@link #killed} can only narrate. Nothing is ever refused
     * here; the method exists for the speech alone.
     *
     * Each hero has one more line after "You have proved yourself of the
     * honour..", and all three transcripts record it as {{sic}} -- the
     * transcriber saw a line and could not read it. It is left out rather than
     * guessed at.
     */
    public boolean refusesKill(Npc npc) {
        int id = npc.getID();
        if (id == NEZIKCHENED) {
            if (at(BOOK)) {
                offer(npc, "Ha ha ha...I shall return for you when the time is right.");
            } else if (at(MET_ECHNED)) {
                offer(npc, "Arrrgghhhhh, foul Vacu!");
                offer(npc, "You would bite the hand that feeds you!");
                offer(npc, "Very well, I will ready myself for our next encounter...");
            }
            return false;
        }
        for (int i = 0; i < HERO_NPC.length; i++) {
            if (HERO_NPC[i] == id && !at(TOTEM_CUT)) {
                offer(npc, "You have proved yourself of the honour..");
            }
        }
        return false;
    }

    /**
     * The blue wizards hat cannot be picked up, ever.
     *
     * Reaching for it is how Viyeldi is woken, and it is the only thing in the
     * quest that does something by being refused.
     */
    public boolean refusesPickup(InvItem item) {
        if (item.getID() != WIZARD_HAT) {
            return false;
        }
        if (!past(DEEP)) {
            say("You feel it would be wrong to take it.");
            return true;
        }
        say("Your hand passes through the hat as if it wasn't there.");
        say("Instantly the clothes begin to animate and then walk towards you.");
        say("The headless, spirit of Viyeldi animates and walks towards you.");
        if (marked(VIYELDI_DEAD)) {
            say("Viyeldi falls silent...");
            say("...and the clothes slump to the floor.");
            return true;
        }
        Npc mage = nearby(VIYELDI);
        if (mage == null) {
            mage = summon(VIYELDI, 600000);
        }
        viyeldi(mage);
        return true;
    }

    // -------------------------------------------------------- item commands --

    private void command(int id) {
        switch (id) {
            case SCROLLS:        drawMap();               break;
            case SCROLLS_DONE:   say("The map of the Kharazi Jungle is finished.");
                                 say("Every sector is drawn in charcoal.");
                                 break;
            case BULL_ROARER:    swing();                 break;
            case SKETCH:         say("A drawing of a wide, shallow bowl.");
                                 say("The note beside it reads: two bars of gold, hammered.");
                                 break;
            case SEED:           say("A hard little seed. Nothing is happening inside it.");
                                 break;
            case SEED_GERMINATED:say("The seed has split. Something pale is coming out of it.");
                                 break;
            case TOTEM_GILDED:   say("Every face of it is worked in gold leaf.");
                                 say("Sir Radimus Erkle would want to see this.");
                                 break;
            case BOOK_OF_BINDING:readBook();              break;
            case VIAL_HOLY:      throwVial();             break;
            case HOLY_FORCE:     castForce();             break;
            case GUJUO_POTION:   drinkPotion();           break;
            case NOTES_CRATE:    say("'Day forty. The fire will not go out and he will not come out of it.'");
                                 break;
            case NOTES_TABLE:    say("'It speaks with his mouth now. It called me by my mother's name.'");
                                 break;
            case NOTES_BED:      say("'Viyeldi has gone below with the three. None of them have come back.'");
                                 break;
            case SHAMANS_TOME:   say("'The Yommi will not take root in earth the demon has drunk from.'");
                                 say("'Break its hold on the spring, or plant nothing.'");
                                 break;
            // Both bowls carry "Empty" and were claimed via associateItem(),
            // but neither had a case here, so the command did nothing.
            case BOWL_PLAIN:     say("You empty the plain water out of the Golden Bowl.");
                                 swap(BOWL_PLAIN, BOWL);
                                 break;
            case BOWL_BLESSED_PLAIN:
                                 say("You empty the plain water out of the Blessed Golden Bowl.");
                                 swap(BOWL_BLESSED_PLAIN, BOWL_BLESSED);
                                 break;
            default: break;
        }
    }

    /**
     * "Read Scrolls" in the jungle: one sector per reading, three readings, a
     * papyrus and a charcoal each time, and Crafting 50 to hold the pen.
     *
     * The wiki warns that a failure costs the materials, so it does. The
     * chance is not recorded anywhere and one in four is a choice.
     */
    private void drawMap() {
        Player p = getOwner();
        if (!questStarted() || completed()) {
            say("It is a map of the southern part of Karamja.");
            return;
        }
        int here = sector();
        if (here < 0) {
            say("It is a map of the Kharazi Jungle, and it is mostly blank.");
            say("I will have to be standing in the jungle to fill it in.");
            return;
        }
        int done = field(MAP_MASK, MAP_SHIFT);
        if ((done & 1 << here) != 0) {
            say("This part of the jungle is already on the map.");
            return;
        }
        if (p.getCurStat(CRAFTING) < 50) {
            say("You need a crafting level of 50 to draw a map this fine.");
            return;
        }
        if (!holds(PAPYRUS) || !holds(CHARCOAL)) {
            say("I need some papyrus and some charcoal to draw with.");
            return;
        }
        take(PAPYRUS);
        take(CHARCOAL);
        if (DataConversions.random(0, 3) == 0) {
            say("The charcoal smudges and the sheet is ruined.");
            return;
        }
        done |= 1 << here;
        setField(MAP_MASK, MAP_SHIFT, done);
        say("You sketch this sector of the jungle onto the map.");
        if (done != 7) {
            return;
        }
        /*
         * The finished map replaces the blank one and the three sector bits go
         * with it -- they are needed again at bit 5 much later, for the tree.
         */
        setField(MAP_MASK, MAP_SHIFT, 0);
        swap(SCROLLS, SCROLLS_DONE);
        step(MAPPED);
        say("@gre@The map of the Kharazi Jungle is complete.");
        say("One of the foresters would want to see this.");
    }

    /**
     * "Swing" the bull roarer.
     *
     * In the jungle it calls Gujuo, or something with teeth, or nothing at all.
     * The wiki gives no odds; a half chance of Gujuo when the quest needs him
     * and a quarter otherwise is a choice, and so is the beast that turns up.
     */
    private void swing() {
        say("You start to swing the bullroarer above your head.");
        say("You feel a bit silly at first, but soon it makes an interesting sound.");
        if (!inJungle()) {
            say("Nothing much seems to happen though.");
            Npc forester = nearby(FORESTER);
            if (forester != null) {
                offer(forester, "You might like to use that when you get into the");
                offer(forester, "Kharazi jungle, it might attract more natives...");
            }
            return;
        }
        boolean wanted = !past(MET_GUJUO)
            || (past(SAW_SHAMAN) && !past(KNOWS_WATER))
            || (past(KNOWS_WATER) && !past(BLESSED) && holds(BOWL))
            || (past(GERMINATED) && !past(KNOWS_HERBS))
            || (past(REPLACED) && !past(GIFT));
        int roll = DataConversions.random(0, 3);
        if (wanted ? roll < 2 : roll == 0) {
            Npc gujuo = summon(GUJUO, 120000);
            say("A man steps out of the trees.");
            gujuo(gujuo);
            return;
        }
        if (roll == 3) {
            int id = ROARER_BEASTS[DataConversions.random(0, ROARER_BEASTS.length - 1)];
            Npc beast = summon(id, 300000);
            say("Something else heard it.");
            beast.attackPlayer(getOwner());
            return;
        }
        say("Nothing comes.");
    }

    /**
     * "read" the Book of Binding: a menu of its four sections. Three are
     * windows of lore; Enchanto is the spell that turns an empty vial into
     * one a blessed bowl can fill.
     */
    private void readBook() {
        say("You read the Book of Binding...");
        new Conversation(getOwner(), null)
            .options(new Choice("Arcana..", "Instructo...", "Defeati...", "Enchanto...") {
                public void picked(int option, Conversation c) {
                    bookSection(option, c);
                }
            })
            .start();
    }

    /*
     * The four sections, windows and all, transcribed from the recorded
     * playthrough -- "possesion", "percieved", "goodlight" and Defeati's
     * leading ". " are really what it prints.
     */
    private void bookSection(int option, Conversation c) {
        Player p = getOwner();
        if (option == 0) {
            p.getActionSender().sendAlert(
                "Arcana...% %"
                + "Use holy water to determine possesion, slight changes in "
                + "appearance may be percieved when doused.% %"
                + "Legendary Silverlight will help to defeat any demon by weakening it.% %"
                + "Be wary of any demon, it may have special forms of attack.% %"
                + "Use an Octagram shape to confine unearthly creatures of the "
                + "underworld - the perfect geometry confuses them.", true);
            return;
        }
        if (option == 1) {
            p.getActionSender().sendAlert(
                "Instructo...% %"
                + "To make Holy water enchant small vials to contain the magic water.% %"
                + "See later chapters for enchantment. Place sacred water into "
                + "vial and equip as any other missile.", true);
            return;
        }
        if (option == 2) {
            p.getActionSender().sendAlert(
                "Defeati...% %"
                + ". Hold the book of binding open to the possesed letting the "
                + "goodlight fall on them completely. Be prepared for as soon as "
                + "the beast is released it will strike and strike hard.", true);
            return;
        }
        say("You read the section entitled Enchanto...");
        say("This looks like an enchantment, it requires some magic and prayer to cast.");
        say("Would you like to try and cast this enchantment?");
        c.options(new Choice("Yes, I'll try.", "No, I don't think I'll bother.") {
            public void picked(int pick, Conversation cc) {
                if (pick == 0) {
                    enchanto();
                }
            }
        });
    }

    /** The Enchanto cast itself: an empty vial, ten prayer and ten magic. */
    private void enchanto() {
        Player p = getOwner();
        if (!holds(VIAL_EMPTY)) {
            say("This spell looks as if it needs some other components.");
            return;
        }
        if (p.getCurStat(PRAYER) < 10 || p.getCurStat(MAGIC) < 10) {
            say("You say the word and nothing at all happens.");
            say("You do not have the strength left for it.");
            return;
        }
        drain(PRAYER, p.getCurStat(PRAYER) - 10);
        drain(MAGIC, p.getCurStat(MAGIC) - 10);
        swap(VIAL_EMPTY, VIAL_ENCHANTED);
        say("You read the word 'Enchanto' aloud.");
        say("The vial goes cold in your hand.");
    }

    /** "Throw" the holy water, which only works on the shaman and only once. */
    private void throwVial() {
        if (!wears(VIAL_HOLY) && !holds(VIAL_HOLY)) {
            return;
        }
        Npc target = nearby(UNGADULU);
        if (target == null || past(DEMON1)) {
            say("There is nothing here worth wasting it on.");
            return;
        }
        soak(target);
    }

    /** "Cast" the Holy Force Spell, which is the second fight's opening move. */
    private void castForce() {
        Npc ghost = nearby(ECHNED);
        if (ghost == null) {
            say("You hold up the spell. Nothing here answers to it.");
            return;
        }
        secondFight(ghost);
    }

    /** "Drink" the Gujuo Potion. The effect is permanent and there is no message. */
    /**
     * All footage, including the player's own overhead lines between the
     * narration -- and "placibo", which is Jagex's spelling, not ours.
     */
    private void drinkPotion() {
        final Player p = getOwner();
        say("Are you sure you want to drink this?");
        new Conversation(p, null)
            .options(new Choice("Yes, I'm sure...", "No, I've had second thoughts...") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    take(GUJUO_POTION);
                    mark(BRAVE);
                    say("You drink the potion...");
                    sayMessage("Mmmm.....");
                    say("It tastes sort of strange...like fried oranges...");
                    sayMessage(".....!.....");
                    say("You feel somehow different...");
                    sayMessage("Let's just hope that this isn't a placibo!");
                }
            })
            .start();
    }

    // ------------------------------------------------------- item on item --

    /**
     * Both herbs into one vial of water, which is the Gujuo potion, and the
     * blessed water onto the seeds, which is the germination.
     *
     * The potion gives no herblaw experience: it is not a potion anyone else
     * knows how to make, and the transcripts record no experience drop.
     */
    private void pair(int a, int b) {
        Player p = getOwner();
        if (both(a, b, SNAKE_WEED, VIAL_OF_WATER)) {
            mix(SNAKE_WEED, SOLUTION_SNAKE);
            return;
        }
        if (both(a, b, ARDRIGAL, VIAL_OF_WATER)) {
            mix(ARDRIGAL, SOLUTION_ARDRIGAL);
            return;
        }
        if (both(a, b, ARDRIGAL, SOLUTION_SNAKE) || both(a, b, SNAKE_WEED, SOLUTION_ARDRIGAL)) {
            int herb = holds(ARDRIGAL) && a != SOLUTION_ARDRIGAL && b != SOLUTION_ARDRIGAL
                ? ARDRIGAL : SNAKE_WEED;
            int solution = herb == ARDRIGAL ? SOLUTION_SNAKE : SOLUTION_ARDRIGAL;
            if (p.getCurStat(HERBLAW) < 45) {
                say("You need a herblaw level of 45 to finish this.");
                return;
            }
            take(herb);
            swap(solution, GUJUO_POTION);
            say("You add the second herb. The mixture turns clear.");
            return;
        }
        if (both(a, b, BOWL_BLESSED_PURE, SEED)) {
            if (!holds(SEED)) {
                return;
            }
            int seeds = count(SEED);
            take(SEED, seeds);
            give(SEED_GERMINATED, seeds);
            swap(BOWL_BLESSED_PURE, BOWL_BLESSED);
            step(GERMINATED);
            say("You pour the water over the seeds. Every one of them splits open.");
            say("Somewhere behind you, the jungle pool stops running.");
            return;
        }
        if (both(a, b, BOWL_BLESSED_PURE, VIAL_ENCHANTED)) {
            swap(VIAL_ENCHANTED, VIAL_HOLY);
            say("You fill the enchanted vial from the bowl.");
            return;
        }
        if (both(a, b, HERB_UNIDENTIFIED, VIAL_OF_WATER)) {
            say("I should identify this herb first.");
            return;
        }
        // The quest claimed both items, so the ordinary handler never runs
        // and its default line has to be said here -- a wrong pairing used
        // to be dead silence.
        say("Nothing interesting happens");
    }

    private boolean both(int a, int b, int first, int second) {
        return (a == first && b == second) || (a == second && b == first);
    }

    private void mix(int herb, int solution) {
        Player p = getOwner();
        if (p.getCurStat(HERBLAW) < 45) {
            say("You need a herblaw level of 45 to do that.");
            return;
        }
        if (!past(KNOWS_HERBS)) {
            say("You have no idea what this would make.");
            return;
        }
        take(herb);
        swap(VIAL_OF_WATER, solution);
        say("You crush the herb into the vial.");
    }

    // ---------------------------------------------------------- the scenery --

    private void scenery(QuestTrigger trigger, GameObject object, InvItem used) {
        int id = object.getID();
        if (trigger == QuestTrigger.ITEM_ON_OBJECT && used != null) {
            itemOnObject(object, used);
            return;
        }
        boolean second = trigger == QuestTrigger.OBJECT_ACT2;
        switch (id) {
            case CUPBOARD:          cupboard();                       return;
            case HALL_DOORS:        if (second) { say("Nothing interesting happens"); }
                                    else { hallDoors(); }
                                    return;
            case BOOKCASE:          bookcase();                       return;
            case CRUDE_DESK:        found(SHAMANS_TOME, "desk");      return;
            case CRATE:             found(NOTES_CRATE, "crate");      return;
            case TABLE:             found(NOTES_TABLE, "table");      return;
            case CRUDE_BED:         found(NOTES_BED, "bed");          return;
            // The east crack is the way out of the cave entirely; the west one
            // is the near end of the bookcase passage and goes back to the
            // shelves. The way in from the surface is the rock crawl
            // (kharaziRock) -- neither crack is clickable from outside.
            case CAVE_IN_EAST:      if (second) { say("A crack in the rock, big enough to squeeze into."); }
                                    else {
                                        say("You squeeze up through the crack in the rock.");
                                        say("You emerge under the half buried rock, back in the jungle.");
                                        teleport(JUNGLE_MOUTH_X, JUNGLE_MOUTH_Y);
                                    }
                                    return;
            case CAVE_IN_WEST:      if (second) { say("A crack in the rock, big enough to squeeze into."); }
                                    else {
                                        say("You squeeze back through the passage.");
                                        teleport(BOOKCASE_EAST_X, BOOKCASE_EAST_Y);
                                    }
                                    return;
            case WOODEN_DOORS:      woodenDoors(object, second);      return;
            case BOULDER_A:
            case BOULDER_B:
            case BOULDER_C:         boulder(object, second);          return;
            case METAL_GATE:        metalGate(object, second);        return;
            case CARVED_ROCK:       carvedRock(object);               return;
            case BURIED_REMAINS:    say("It looks as if some poor unfortunate soul died here.");
                                    return;
            case DARK_GATE:         darkGate(second);                 return;
            case BARREL:            barrel(object, second);           return;
            case WOODEN_BEAM:       beam(object);                     return;
            case ROPE_DOWN:         descend();                        return;
            case ROPE_UP:           if (second) { say("Somebody has left a rope hanging here."); }
                                    else { teleport(BARREL_ROOM_X, BARREL_ROOM_Y); }
                                    return;
            case STAIRS_A:
            case STAIRS_B:
            case STAIRS_C:
            case STAIRS_D:          stairs(object, second);           return;
            case WALKWAY_A:
            case WALKWAY_B:
            case WALKWAY_C:
            case WALKWAY_D:         walkway(object, second);          return;
            case LAVA_FURNACE:      furnace(second);                  return;
            case DRAGONS_EYE:       say("A knot of brown rock, roughly the shape of an eye.");
                                    return;
            case CAVERNOUS_OPENING: opening();                        return;
            case ECHNED_ROCK:       echnedRock(second);               return;
            case KHARAZI_ROCK:      kharaziRock(second);              return;
            case SHALLOW_WATER:     shallowWater();                   return;
            case TALL_REEDS:        say("These tall reeds look nice and long,");
                                    say("with a long tube for a stem.");
                                    say("They reach all the way down to the water.");
                                    return;
            case FERTILE_EARTH:     say("A patch of dark, damp earth.");
                                    say("Something would grow here.");
                                    return;
            case TOTEM_EVIL:        say("The carvings on it have been cut over with something else.");
                                    say("Looking at it too long makes you feel watched.");
                                    return;
            case TOTEM_GOOD:        say("A totem pole of the Kharazi tribe.");
                                    return;
            case JUNGLE_PLANT:
            case JUNGLE_TREE_A:
            case JUNGLE_TREE_B:
            case JUNGLE_PALM_A:
            case JUNGLE_PALM_B:     jungleCut(object);                return;
            case JUNGLE_STUMP:      jungleStump(object);              return;
            case JUNGLE_VINE:       herbSearch(object, SNAKE_WEED, 0); return;
            case PALM_TREE:         herbSearch(object, HERB_UNIDENTIFIED, 1); return;
            case YOMMI_BABY:        say("A pale shoot, barely out of the ground.");
                                    say("It needs water, and not from anywhere.");
                                    return;
            case YOMMI:             say("A young Yommi tree. It is still growing.");
                                    return;
            case YOMMI_GROWN:       say("A full grown Yommi tree.");
                                    say("A rune axe would bring it down.");
                                    return;
            case YOMMI_CHOPPED:     say("The tree is down. The branches are still on it.");
                                    return;
            case YOMMI_TRIMMED:     say("A bare trunk, waiting to be carved.");
                                    return;
            case YOMMI_TOTEM:       liftTotem(object);                return;
            default: return;
        }
    }

    /** Radimus's cupboard, which holds an endless supply of machettes. */
    private void cupboard() {
        if (!questStarted() || completed()) {
            say("@gre@Sir Radimus Erkle: You're not authorised to open that cupboard.");
            return;
        }
        if (holds(MACHETTE)) {
            say("You already have a machette.");
            return;
        }
        give(MACHETTE);
        say("You take a machette from the cupboard.");
    }

    /** The doors into the main hall, which is where the quest actually ends. */
    private void hallDoors() {
        Player p = getOwner();
        if (!past(HANDED_IN) && !completed()) {
            say("You need to complete the Legends Guild Quest");
            say("before you can enter the Legends Guild");
            return;
        }
        say("You open the impressive wooden doors.");
        teleport(512, p.getY() >= 540 ? 539 : 541);
    }

    /** Backstory, one item each, and only ever once. */
    private void found(int id, String what) {
        say("You search the " + what + ".");
        if (holds(id) || !questStarted()) {
            say("There is nothing else in it.");
            return;
        }
        give(id);
        say("You find some papers.");
    }

    /** The bookcase that is not a bookcase. */
    private void bookcase() {
        say("You search the bookcase.");
        if (!past(SAW_SHAMAN)) {
            say("The shelves are empty. It does not seem worth moving.");
            return;
        }
        say("The whole case swings away from the wall.");
        say("There is a passage behind it.");
        teleport(BOOKCASE_WEST_X, BOOKCASE_WEST_Y);
    }

    /** Thieving 50 and a lockpick, or the doors stay shut. */
    private void woodenDoors(GameObject object, boolean second) {
        Player p = getOwner();
        if (!second) {
            say("The doors are locked.");
            return;
        }
        if (!holds(LOCKPICK)) {
            say("The lock is old and complicated.");
            say("I would need a lockpick for this.");
            return;
        }
        if (p.getCurStat(THIEVING) < 50) {
            say("You need a thieving level of 50 to pick this lock.");
            return;
        }
        say("You pick the lock and push the doors open.");
        // The doors stand on (441,3702) with the passage running north-south;
        // (440,3702) is the rock beside them. Landing on the doors' own tile
        // just left the player standing on them, so go through to whichever
        // side they are not on.
        teleport(441, p.getY() < 3703 ? 3703 : 3701);
    }

    /** Strength 50 and a pickaxe. The rubble is not put back. */
    /**
     * Smash to pieces, with a pick. The remains left behind (1143) are as
     * solid as the boulder to both pathfinders -- a type-1 def either way --
     * so passing the pieces is a teleport, and a moment later another rock
     * falls to seal the passage behind you, as the recorded playthrough
     * shows. The five seconds is the liberty: long enough to see the
     * remains, short enough that the message lands while you are still
     * walking away.
     */
    private void boulder(GameObject object, boolean second) {
        Player p = getOwner();
        if (!second) {
            say("A boulder, wedged across the passage.");
            return;
        }
        if (!anyPickaxe()) {
            say("I would need a pickaxe to break this up.");
            return;
        }
        if (p.getCurStat(STRENGTH) < 50) {
            say("You need a strength level of 50 to break this boulder.");
            return;
        }
        say("You take a good swing at the rock with your pick...");
        say("...and smash it into smaller pieces.");
        becomes(object, SMASHED_ROCK);
        int lx = Math.min(Math.max(p.getX(), object.getX()), object.getX() + 1);
        int ly = p.getY() <= object.getY() - 1 ? object.getY() + 2 : object.getY() - 1;
        if (object.getID() == BOULDER_B && ly == object.getY() - 1) {
            lx = 441;   // the middle boulder's north side is solid rock at x440
        }
        teleport(lx, ly);
        world.getDelayedEventHandler().add(new SingleEvent(null, 5000) {
            public void action() {
                world.registerGameObject(new GameObject(object.getLoc()));
                if (p.withinRange(object)) {
                    p.getActionSender().sendMessage(
                        "Another large rock falls down replacing the one that you smashed.");
                }
            }
        });
    }

    private boolean anyPickaxe() {
        int[] picks = { 156, 1258, 1259, 1260, 1261, 1262 };
        for (int i = 0; i < picks.length; i++) {
            if (holds(picks[i]) || wears(picks[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * The Heavy Metal Gate between the death wings and the boulders.
     *
     * The whole forcing ceremony is recorded, shouts and all -- the shouts
     * are the player's own, overhead -- and it plays every pass. The
     * liberties: the two-second beat between shoves, the strength refusal,
     * and the No option's wording, which the footage hides behind a panel.
     */
    private void metalGate(GameObject object, boolean second) {
        final Player p = getOwner();
        if (!second) {
            say("A heavy metal gate. There is no handle on it.");
            return;
        }
        say("You push the gates...they're very stiff...");
        say("They won't budge with a normal push.");
        say("Do you want to try to force them open with brute strength?");
        new Conversation(p, null)
            .options(new Choice("Yes, I'm very strong, I'll force them open.",
                                "No, I haven't got the muscles.") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    if (p.getCurStat(STRENGTH) < 50) {
                        say("You put your shoulder to the gate. It does not move.");
                        say("You need a strength level of 50 to shift this.");
                        return;
                    }
                    forceGates();
                }
            })
            .start();
    }

    /** The four shoves, two seconds apart, and through on the last. */
    private void forceGates() {
        final Player p = getOwner();
        say("You ripple your muscles...preparing too exert yourself...");
        sayMessage("Hup!");
        world.getDelayedEventHandler().add(new SingleEvent(p, 2000) {
            public void action() {
                say("You brace yourself against the doors...");
                sayMessage("Urghhhhh!");
            }
        });
        world.getDelayedEventHandler().add(new SingleEvent(p, 4000) {
            public void action() {
                say("You start to force against the gate..");
                sayMessage("Arghhhhhhh!");
            }
        });
        world.getDelayedEventHandler().add(new SingleEvent(p, 6000) {
            public void action() {
                say("You push and push,");
                sayMessage("Shhhhhhhshshehshsh");
            }
        });
        world.getDelayedEventHandler().add(new SingleEvent(p, 8000) {
            public void action() {
                say("You just manage to force the gates open slightly,");
                say("just enough to force yourself through.");
                // Only the gate's own two columns are clear on the boulder
                // side -- the tiles either side of them are solid rock.
                int lx = Math.min(Math.max(p.getX(), 440), 441);
                teleport(lx, p.getY() <= 3717 ? 3719 : 3717);
            }
        });
    }

    /**
     * The seven Carved Rocks, which take one gem each and only the right one.
     *
     * The riddle on the last rock is the only clue: "Once there were crystals
     * to make the pool shine, ordered in stature to retrieve what's mine."
     * Ordered in stature means by value, and the order runs southeast down each
     * of the two rows.
     */
    private void carvedRock(GameObject object) {
        int index = carvedIndex(object);
        if (index < 0) {
            say("A rock carved with a shallow bowl, standing in the water.");
            return;
        }
        int placed = field(GEM_MASK, GEM_SHIFT);
        if ((placed & 1 << index) != 0) {
            say("There is a gem in this one already.");
            return;
        }
        say("A rock carved with a shallow bowl, standing in the water.");
        say("Words are cut around the rim:");
        say("'Once there were crystals to make the pool shine,'");
        say("'Ordered in stature to retrieve what's mine.'");
    }

    private int carvedIndex(GameObject object) {
        for (int i = 0; i < CARVED_AT.length; i++) {
            if (object.getX() == CARVED_AT[i][0] && object.getY() == CARVED_AT[i][1]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The Dark Metal Gate, which reads as a hint and opens to a charged orb.
     *
     * The orb cast arrives at spellCast(GameObject,int) rather than here: it is
     * a spell aimed at scenery, not an item used on it, and the packet for that
     * had never been implemented before this quest needed it.
     *
     * The Open text is the footage's, verbatim. The search text is still a
     * liberty -- the footage never searches the gate.
     */
    private void darkGate(boolean second) {
        if (second) {
            say("You search the gate.");
            say("There is a socket in the middle of it, the size of a fist.");
            say("The metal around it is scorched, as though something burned there.");
            return;
        }
        say("This gate is fused with rock, it doesn't seem possible to open it.");
        say("But it does look slightly strange in some way.");
    }

    /**
     * The ten Strange Barrels, which do something different every time.
     *
     * The wiki lists explosions, monsters and supplies and gives no odds; the
     * table below is a choice, and the barrel goes back after a minute so the
     * room is not stripped permanently.
     */
    private void barrel(GameObject object, boolean second) {
        if (second) {
            say("A sealed barrel. Something inside it is moving.");
            return;
        }
        say("You smash the barrel open.");
        world.unregisterGameObject(object);
        world.delayedSpawnObject(object.getLoc(), 60000);
        int roll = DataConversions.random(0, 9);
        if (roll < 3) {
            say("It goes up in your face.");
            hurt(DataConversions.random(4, 12));
            return;
        }
        if (roll < 6) {
            Npc thing = summon(ROARER_BEASTS[DataConversions.random(0, ROARER_BEASTS.length - 1)], 300000);
            say("Something comes out of it.");
            thing.attackPlayer(getOwner());
            return;
        }
        int[] loot = { 546, 547, 548, 373, 374, 138, 140 };
        give(loot[DataConversions.random(0, loot.length - 1)]);
        say("There was something useful in this one.");
    }

    /**
     * The Wooden Beam over the hole, which will not be looked at properly by
     * anyone who has not drunk Gujuo's potion.
     */
    private void beam(GameObject object) {
        if (!marked(BRAVE)) {
            say("A beam laid across a hole in the floor.");
            say("The air coming up out of it makes your head swim");
            say("and you have to step back.");
            return;
        }
        if (marked(ROPE_ON_BEAM)) {
            /* The rope object does not survive a server restart: the world
               reloads the bare beam from GameObjectLoc while the player's
               flag persists, leaving them told the rope is tied with no way
               down. Looking at the beam puts the rope back. */
            say("Your rope is still tied to the beam.");
            becomes(object, ROPE_DOWN);
            return;
        }
        say("A beam laid across a hole in the floor.");
        say("It would hold a rope.");
    }

    /** Down the rope, which can go wrong and hurts when it does. */
    private void descend() {
        if (!marked(BRAVE)) {
            say("The air coming up the hole drives you back.");
            return;
        }
        say("You lower yourself into the dark.");
        if (DataConversions.random(0, 3) == 0) {
            say("Your grip goes and you land badly at the bottom.");
            hurt(DataConversions.random(12, 20));
        }
        teleport(DEPTHS_X, DEPTHS_Y);
        if (!past(DEEP)) {
            step(DEEP);
        }
    }

    /**
     * The route down through the lower caverns, which is two different
     * obstacles laid end to end: four Rock Hewn Stairs (1114, 1123, 1124,
     * 1125) and four Rocky Walkway tiles (558-561) in a row at y=3702, with
     * the stairs at either end of the run.
     *
     * They score the same -- level 50, five experience across, one and a
     * quarter for falling off -- and they differ in what a fall costs. The
     * stairs are a short drop and take two or three. The walkway is over a
     * gap and the recovered article puts it anywhere up to twenty-eight,
     * "based upon the player's hits", which is why it warns players to cross
     * it at full health.
     *
     * Both were previously worth nothing at all.
     */
    private void stairs(GameObject object, boolean second) {
        if (second) {
            say("Steps cut into the rock. They are worn almost smooth.");
            return;
        }
        if (!climb(object, 50)) {
            return;
        }
        if (!lucky()) {
            say("You slip and fall...");
            hurt(DataConversions.random(2, 3));
            reward(AGILITY, CLIMB_FAIL_EXP);
            return;
        }
        int[] ends = null;
        for (int[] pair : STAIR_ENDS) {
            if (pair[0] == object.getID()) {
                ends = pair;
                break;
            }
        }
        Player p = getOwner();
        int toUpper = (p.getX() - ends[1]) * (p.getX() - ends[1]) + (p.getY() - ends[2]) * (p.getY() - ends[2]);
        int toLower = (p.getX() - ends[3]) * (p.getX() - ends[3]) + (p.getY() - ends[4]) * (p.getY() - ends[4]);
        boolean down = toLower >= toUpper;
        say(down ? "You climb down the steps." : "You climb up the steps.");
        teleport(down ? ends[3] : ends[1], down ? ends[4] : ends[2]);
        reward(AGILITY, CLIMB_EXP);
    }

    /**
     * Where each set of stairs leads: object id, then the upper landing, then
     * the lower. Climbing sends you to whichever end you are not standing at
     * (the farther one). The landings match the walked route and the
     * collision map rather than the tile next to the stairs, because several
     * are separated from the stairs by solid rock -- 1114's ends sit either
     * side of the band at y3704-3705, 1125 crosses two blocked tiles -- and
     * 1123's lower end is walkway 558's own tile, where the balance sequence
     * starts.
     */
    private static final int[][] STAIR_ENDS = {
        { STAIRS_A, 426, 3706, 426, 3702 },
        { STAIRS_B, 423, 3700, 422, 3702 },
        { STAIRS_C, 418, 3702, 419, 3706 },
        { STAIRS_D, 419, 3706, 423, 3706 },
    };

    /** The four walkway tiles, which score as the stairs do and hurt more. */
    private void walkway(GameObject object, boolean second) {
        Player p = getOwner();
        if (second) {
            say("A precarious rocky walkway.");
            return;
        }
        if (!climb(object, 50)) {
            return;
        }
        if (!lucky()) {
            say("You slip and fall...");
            // The article gives six damage bands and no thresholds for them,
            // so the band is picked from the roll rather than the other way
            // round, and the roll is capped by hits the way the article says.
            int worst = Math.max(3, p.getCurStat(HITS) * 28 / 99);
            int damage = DataConversions.random(2, worst);
            if (damage <= 2) {
                say("...but you luckily avoid any damage.");
                reward(AGILITY, CLIMB_FAIL_EXP);
                return;
            }
            say(damage <= 5 ? "...and take a bit of damage."
              : damage <= 10 ? "...and take some damage."
              : damage <= 16 ? "...and take damage."
              : damage <= 22 ? "...and are injured."
              : "...and take some major damage.");
            hurt(damage);
            reward(AGILITY, CLIMB_FAIL_EXP);
            return;
        }
        say("You manage to keep your balance.");
        stepPast(object);
        reward(AGILITY, CLIMB_EXP);
    }

    /** The level check both of them share. */
    private boolean climb(GameObject object, int level) {
        if (getOwner().getCurStat(AGILITY) < level) {
            say("You need an agility level of " + level + " to get up there.");
            return false;
        }
        return true;
    }

    /** Step past a blocking tile, landing on whichever side you were not on. */
    private void stepPast(GameObject object) {
        Player p = getOwner();
        teleport(p.getX() >= object.getX() ? object.getX() - 1 : object.getX() + 1, object.getY());
    }

    /** Three crystals in, one out. */
    private void furnace(boolean second) {
        if (!second) {
            say("A furnace, cut straight into a vein of lava.");
            say("It has not been out in four hundred years.");
            return;
        }
        say("You search the furnace.");
        say("There are three shallow depressions in the stone lip.");
    }

    /** The cavernous opening, the mouth of the dragon on the map. */
    private void opening() {
        if (past(OPENED)) {
            say("You climb through the opening.");
            teleport(394, 3733);
            return;
        }
        say("A gap in the rock, and something is stopping you walking into it.");
        say("There is a socket beside it, cut to hold something round.");
    }

    /**
     * Echned Zekin's rock, which does three different things in sequence: it
     * summons him, it summons the demon, and then it lets go of the spring.
     */
    private void echnedRock(boolean second) {
        if (second) {
            say("A rock, dragged here and set down on purpose.");
            return;
        }
        if (past(DEMON2)) {
            say("You move the rock aside.");
            if (!past(WATERED)) {
                step(WATERED);
            }
            say("There is clean water underneath it.");
            say("It has not been touched in a very long time.");
            return;
        }
        if (!past(OPENED)) {
            say("The rock does not move.");
            return;
        }
        Npc ghost = nearby(ECHNED);
        if (ghost == null) {
            ghost = summon(ECHNED, 600000);
        }
        echned(ghost);
    }

    /**
     * The spring under Echned's rock, once the demon has let go of it. The
     * walkthroughs say to use the blessed bowl "on the water spot beneath
     * the rock"; the rock itself stays the clickable thing here, there is
     * no separate water object. The surface pool flows again from the same
     * moment, so later refills can also come from there through a reed.
     */
    private void sourceWater(int item) {
        if (!past(WATERED)) {
            say("Nothing interesting happens");
            return;
        }
        if (item == BOWL_BLESSED) {
            swap(BOWL_BLESSED, BOWL_BLESSED_PURE);
            say("You fill the bowl with pure water from under the rock.");
            return;
        }
        if (item == BOWL_BLESSED_PLAIN) {
            say("The bowl already has dirty water in it. Empty it first.");
            return;
        }
        say("Nothing interesting happens");
    }

    /** The three rocks in the jungle, and the crawl down into the caves. */
    private void kharaziRock(boolean second) {
        Player p = getOwner();
        if (!second) {
            say("A rock, half buried, with a gap under one edge.");
            return;
        }
        say("You search under the rock. There is a crawlspace.");
        if (!past(MET_GUJUO)) {
            say("It is far too tight to get into.");
            return;
        }
        if (p.getCurStat(AGILITY) < 50) {
            say("You need an agility level of 50 to get through there.");
            return;
        }
        if (DataConversions.random(0, 3) == 0) {
            say("You get stuck halfway and have to force your way back out.");
            hurt(5);
            return;
        }
        say("You squeeze through into the dark.");
        teleport(CAVE_MOUTH_X, CAVE_MOUTH_Y);
    }

    /** The shallow water, which dries up once the seeds are germinated. */
    private void shallowWater() {
        if (past(GERMINATED) && !past(WATERED)) {
            say("The pool is dry. There is nothing but cracked mud in it.");
            return;
        }
        say("A shallow pool, fed from somewhere under the trees.");
        say("The reeds by the edge reach right down into it.");
    }

    /**
     * Snake Weed on the vine, Ardrigal on the palm.
     *
     * Jungle potion answers this same search and spawns the same two herbs, so
     * this one stands down whenever that quest is currently asking for the herb
     * in question: otherwise a player doing both would get two of each.
     */
    private void herbSearch(GameObject object, int herb, int round) {
        Player p = getOwner();
        if (!past(KNOWS_HERBS) || completed()) {
            return;
        }
        if (p.getQuestManager().stageOf(Quests.JUNGLE_POTION) - 1 == round) {
            return;
        }
        if (holds(herb) || holds(GUJUO_POTION) || marked(BRAVE)) {
            say("You search but find nothing of interest");
            return;
        }
        world.registerItem(new Item(herb, object.getX(), object.getY(), 1, p));
        say("You find a herb");
    }

    /** Lifting the finished pole, which costs most of a player's strength. */
    private void liftTotem(GameObject object) {
        Player p = getOwner();
        if (holds(TOTEM)) {
            say("You are already carrying one of those, and it is quite enough.");
            return;
        }
        say("You heave the totem pole up onto your shoulder.");
        give(TOTEM);
        p.setCurStat(STRENGTH, Math.max(1, p.getCurStat(STRENGTH) - DataConversions.random(5, 7)));
        p.getActionSender().sendStat(STRENGTH);
        becomes(object, FERTILE_EARTH);
        if (!past(TOTEM_CUT)) {
            /*
             * The three hero bits were the crystals; from here they mean which
             * of the three has already been beaten in the last fight, so they
             * are cleared on the way into that stage.
             */
            setField(HERO_MASK, HERO_SHIFT, 0);
            step(TOTEM_CUT);
        }
    }

    // ----------------------------------------------------------- dense jungle --

    /**
     * Cutting into the Kharazi jungle.
     *
     * The vegetation wall never had a handler at all -- Chop reached the
     * generic woodcutting code, found no woodcutting def for these ids, and
     * returned without a word. Rebuilt to the wiki's record: any axe fells
     * the trees and palms for logs and 25 woodcutting experience, the
     * machette slashes the plants, and without the Radimus Scrolls the
     * jungle refuses you -- unless the quest is done, because the scrolls
     * are handed in at the end and the jungle stays open after.
     *
     * Messages shared with ordinary woodcutting keep that handler's exact
     * text and colour. The regrow clock is the one liberty: nobody recorded
     * it, so the jungle comes back on the ordinary tree's 30 seconds.
     */
    private void jungleCut(GameObject object) {
        Player p = getOwner();
        boolean tree = object.getID() != JUNGLE_PLANT;
        if (!completed() && !holds(SCROLLS) && !holds(SCROLLS_DONE)) {
            say("This jungle is far too thick, you'll need a special map to go further.");
            return;
        }
        int axe = -1;
        if (tree) {
            for (int a : Formulae.woodcuttingAxeIDs) {
                if (count(a) > 0) {
                    axe = a;
                    break;
                }
            }
            if (axe < 0) {
                say("@gry@ You need an axe to chop this tree down.");
                return;
            }
        } else if (!holds(MACHETTE)) {
            say("@gry@ You need a machette to cut through the dense vegetation.");
            return;
        }
        if (p.getFatigue() >= 100) {
            say("@gry@ You are too tired to cut the tree.");
            return;
        }
        say(tree
            ? "@pnk@ You swing your " + EntityHandler.getItemDef(axe).getName() + " at the tree..."
            : "@pnk@ You slash at the vegetation with your machette...");
        if (!lucky()) {
            say("@pnk@ You slip and fail to hit the tree.");
            return;
        }
        if (tree) {
            give(LOGS);
            say("@pnk@ You get some wood.");
        }
        p.incExp(WOODCUT, CUT_EXP, true);
        p.getActionSender().sendStat(WOODCUT);
        becomes(object, JUNGLE_STUMP);
        world.delayedSpawnObject(object.getLoc(), 30000);
        boolean entering = p.getY() < JUNGLE_MIN_Y && object.getY() >= JUNGLE_MIN_Y;
        teleport(object.getX(), object.getY());
        if (entering) {
            say("You manage to hack your way into the Kharazi Jungle.");
        }
    }

    /**
     * Walk on a stump. The stump blocks its tile exactly like the jungle it
     * replaced (a type-1 def, and the client's own collision agrees), so
     * walking "onto" it is something the server does for you. Anyone may
     * cross a stump somebody else cut, behind the same scrolls gate as
     * cutting, or a stump would be a hole in the jungle's own rule.
     */
    private void jungleStump(GameObject object) {
        if (!completed() && !holds(SCROLLS) && !holds(SCROLLS_DONE)) {
            say("This jungle is far too thick, you'll need a special map to go further.");
            return;
        }
        teleport(object.getX(), object.getY());
    }

    private boolean isAxe(int item) {
        for (int a : Formulae.woodcuttingAxeIDs) {
            if (item == a) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------- item on object --

    private void itemOnObject(GameObject object, InvItem used) {
        int id = object.getID();
        int item = used.getID();
        switch (id) {
            case TALL_REEDS:        cutReed(item);                     return;
            case SHALLOW_WATER:     fillFromPool(item);                return;
            case JUNGLE_PLANT:
            case JUNGLE_TREE_A:
            case JUNGLE_TREE_B:
            case JUNGLE_PALM_A:
            case JUNGLE_PALM_B:     if (item == MACHETTE || isAxe(item)) { jungleCut(object); }
                                    else { say("Nothing interesting happens"); }
                                    return;
            case CARVED_ROCK:       placeGem(object, item);            return;
            case LAVA_FURNACE:      feedFurnace(item);                 return;
            case DRAGONS_EYE:       chargeCrystal(item);               return;
            case CAVERNOUS_OPENING: openMouth(item);                   return;
            case WOODEN_BEAM:       tieRope(object, item);             return;
            case FERTILE_EARTH:     plant(object, item);               return;
            case YOMMI_BABY:        waterTree(object, item, YOMMI);    return;
            case YOMMI:             waterTree(object, item, YOMMI_GROWN); return;
            case YOMMI_GROWN:       axeTree(object, item, YOMMI_CHOPPED, "You chop the tree down."); return;
            case YOMMI_CHOPPED:     axeTree(object, item, YOMMI_TRIMMED, "You cut the branches off."); return;
            case YOMMI_TRIMMED:     axeTree(object, item, YOMMI_TOTEM, "You carve the trunk into a totem pole."); return;
            case TOTEM_EVIL:        replaceTotem(object, item);        return;
            case ECHNED_ROCK:       sourceWater(item);                 return;
            default:                say("Nothing interesting happens"); return;
        }
    }

    /** The machette on the reeds. Five is as many as anyone can carry. */
    private void cutReed(int item) {
        if (item != MACHETTE) {
            say("Nothing interesting happens");
            return;
        }
        if (count(REED) >= 5) {
            say("You have plenty of reeds already.");
            return;
        }
        give(REED);
        say("You cut a long reed and strip the leaves off it.");
    }

    /**
     * Drawing water, which is the fussiest step in the quest.
     *
     * The blessed bowl gets pure water and everything else gets pond water, and
     * the reed is what makes the difference: a bowl dipped straight in comes up
     * plain no matter what has been said over it.
     */
    private void fillFromPool(int item) {
        if (past(GERMINATED) && !past(WATERED)) {
            say("The pool is dry.");
            return;
        }
        if (item == BOWL_BLESSED || item == BOWL) {
            swap(item, item == BOWL ? BOWL_PLAIN : BOWL_BLESSED_PLAIN);
            say("You dip the bowl in the pool.");
            say("The water in it is dirty. That cannot be right.");
            return;
        }
        if (item != REED) {
            say("Nothing interesting happens");
            return;
        }
        if (holds(BOWL_BLESSED)) {
            take(REED);
            swap(BOWL_BLESSED, BOWL_BLESSED_PURE);
            say("You draw the water up through the reed and into the bowl.");
            return;
        }
        if (holds(VIAL_ENCHANTED)) {
            take(REED);
            swap(VIAL_ENCHANTED, VIAL_HOLY);
            say("You draw the water up through the reed and into the vial.");
            return;
        }
        if (holds(BOWL)) {
            take(REED);
            swap(BOWL, BOWL_PURE);
            say("You draw the water up through the reed and into the bowl.");
            say("Nothing about it feels any different.");
            return;
        }
        say("You have nothing worth filling.");
    }

    /**
     * One gem per rock, in the order the riddle sets.
     *
     * Every line here is transcribed from the recorded playthrough, including
     * "disapear". A placed gem hangs spinning over its rock for a few seconds
     * -- the rock's own tile is solid, so it cannot be picked back up.
     */
    private void placeGem(GameObject object, int item) {
        int index = carvedIndex(object);
        if (index < 0) {
            return;
        }
        int placed = field(GEM_MASK, GEM_SHIFT);
        say("You carefully move the gem closer to the rock.");
        if ((placed & 1 << index) != 0 || item != GEM_ORDER[index]) {
            say("but nothing happens...");
            return;
        }
        take(item);
        placed |= 1 << index;
        setField(GEM_MASK, GEM_SHIFT, placed);
        say("The " + GEM_NAMES[index] + " glows and starts spinning as it hovers above the rock.");
        Item shown = new Item(item, object.getX(), object.getY(), 1, getOwner());
        world.registerItem(shown);
        if (placed != 0x7F) {
            sleep(5000);
            world.unregisterItem(shown);
            return;
        }
        say("Suddenly all the crystals begin to glow very brightly.");
        say("The room is lit up with the bright light...");
        say("Soon, the light from all the crystals converges into a point.");
        say("And you see a strange book appear where the light is focused.");
        say("You pick the book up and place it in your inventory.");
        give(BOOK_OF_BINDING);
        say("All the crystals disapear...and the light fades...");
        world.unregisterItem(shown);
        if (!past(BOOK)) {
            step(BOOK);
        }
    }

    /** Three crystals into the furnace, one out. */
    private void feedFurnace(int item) {
        int index = -1;
        for (int i = 0; i < HERO_CRYSTAL.length; i++) {
            if (HERO_CRYSTAL[i] == item) {
                index = i;
            }
        }
        if (index < 0) {
            say("Nothing interesting happens");
            return;
        }
        take(item);
        say("You lay the crystal in one of the depressions.");
        if (holds(CRYSTAL_CHUNK) || holds(CRYSTAL_LUMP) || holds(CRYSTAL_HUNK)) {
            return;
        }
        give(CRYSTAL_RED);
        say("@gre@The three run together into one red crystal.");
        say("A voice in your head says: bring life to the dragons eye.");
        if (!past(CRYSTAL)) {
            step(CRYSTAL);
        }
    }

    private void chargeCrystal(int item) {
        if (item != CRYSTAL_RED) {
            say("Nothing interesting happens");
            return;
        }
        swap(CRYSTAL_RED, CRYSTAL_GLOWING);
        say("You press the crystal into the rock and it takes hold.");
        say("@gre@When you pull it out again it is glowing.");
        if (!past(EYE)) {
            step(EYE);
        }
    }

    private void openMouth(int item) {
        if (item != CRYSTAL_GLOWING) {
            say("Nothing interesting happens");
            return;
        }
        take(CRYSTAL_GLOWING);
        say("You set the glowing crystal into the socket.");
        say("@gre@Whatever was in the way of the opening is not there any more.");
        if (!past(OPENED)) {
            step(OPENED);
        }
    }

    private void tieRope(GameObject object, int item) {
        if (item != ROPE) {
            say("Nothing interesting happens");
            return;
        }
        if (!marked(BRAVE)) {
            say("You cannot get near enough to the beam to tie anything to it.");
            return;
        }
        if (marked(ROPE_ON_BEAM)) {
            say("Your rope is already tied there.");
            return;
        }
        take(ROPE);
        mark(ROPE_ON_BEAM);
        becomes(object, ROPE_DOWN);
        say("You tie the rope to the beam and drop the loose end into the hole.");
    }

    /**
     * A germinated seed into fertile earth.
     *
     * Herblaw 45, a rune axe in hand for what comes later, and a bowl of pure
     * water ready: the tree is a five minute job from planting to lifting, and
     * the patch goes back to bare earth if it is not finished.
     */
    private void plant(GameObject object, int item) {
        Player p = getOwner();
        if (item != SEED_GERMINATED) {
            if (item == SEED) {
                say("The seed is dead. It would have to be woken first.");
                return;
            }
            say("Nothing interesting happens");
            return;
        }
        if (p.getCurStat(HERBLAW) < 45) {
            say("You need a herblaw level of 45 to plant this properly.");
            return;
        }
        if (!holds(RUNE_AXE) && !wears(RUNE_AXE)) {
            say("There would be no point planting it without a rune axe.");
            return;
        }
        if (!holdsBlessedWater()) {
            say("It would die before you got back with the water.");
            return;
        }
        take(SEED_GERMINATED);
        becomes(object, YOMMI_BABY);
        /*
         * One revert, set here and not renewed at each step, so the patch
         * always comes back on its own. Five minutes is a choice: the wiki says
         * only that a tree can die if you are not quick enough.
         */
        world.delayedSpawnObject(object.getLoc(), 300000);
        say("You push the seed into the earth. Something is already coming up.");
    }

    private void waterTree(GameObject object, int item, int becomes) {
        if (item != BOWL_BLESSED_PURE) {
            if (item == BOWL_PURE || item == BOWL_PLAIN || item == BOWL_BLESSED_PLAIN) {
                say("You pour the water over it. Nothing happens at all.");
                return;
            }
            say("Nothing interesting happens");
            return;
        }
        swap(BOWL_BLESSED_PURE, BOWL_BLESSED);
        becomes(object, becomes);
        say(becomes == YOMMI_GROWN
            ? "The tree shoots up to its full height in front of you."
            : "The shoot thickens and puts out its first branches.");
    }

    private void axeTree(GameObject object, int item, int becomes, String line) {
        Player p = getOwner();
        if (item != RUNE_AXE) {
            say("Only a rune axe will bite into this wood.");
            return;
        }
        if (p.getCurStat(WOODCUT) < 50) {
            say("You need a woodcutting level of 50 to work this tree.");
            return;
        }
        becomes(object, becomes);
        say(line);
    }

    /**
     * The pole against the pole, which is both the third fight and, once it is
     * won, the end of it.
     */
    private void replaceTotem(GameObject object, int item) {
        if (item != TOTEM) {
            say("Nothing interesting happens");
            return;
        }
        if (past(REPLACED)) {
            say("The new pole is already standing.");
            return;
        }
        if (past(DEMON3)) {
            take(TOTEM);
            becomes(object, TOTEM_GOOD);
            step(REPLACED);
            say("@gre@You set your totem pole where the old one stood.");
            say("The jungle goes very quiet.");
            say("Swing the bull roarer. Somebody will want to see this.");
            return;
        }
        say("You attempt to replace the evil totem pole.");
        say("A black cloud emanates from the evil totem pole.");
        say("It slowly forms into the dread demon Nezikchened...");
        thirdFight();
    }

    // ------------------------------------------------------------- doors --

    private void door(QuestTrigger trigger, GameObject door, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_DOOR && used != null) {
            itemOnDoor(door, used);
            return;
        }
        boolean second = trigger == QuestTrigger.DOOR_ACT2;
        switch (door.getID()) {
            case FLAMEWALL:    flamewall(second);   return;
            case RUINED_WALL:  ruinedWall(second, door); return;
            case ANCIENT_WALL: ancientWall(second, door); return;
            default: return;
        }
    }

    /**
     * The ring of fire around Ungadulu.
     *
     * "Touch" walks a player who has the Magical Fire Pass straight through and
     * burns anyone else. "Investigate" is how the shaman is first met, and
     * offers the jump that gets you in the hard way.
     */
    private void flamewall(boolean second) {
        if (second) {
            Npc shaman = nearby(UNGADULU);
            if (shaman == null) {
                say("A wall of fire, standing up out of bare rock.");
                return;
            }
            throughTheFlames(shaman);
            return;
        }
        if (holds(FIRE_PASS) || wears(FIRE_PASS)) {
            say("The flames lean away from you as you walk through.");
            cross();
            return;
        }
        say("You put your hand into the flames.");
        hurt(DataConversions.random(6, 14));
        say("You pull it back out again.");
    }

    /** In if you are out, out if you are in. */
    private void cross() {
        if (insideFlames()) {
            teleport(FLAME_OUT_X, FLAME_OUT_Y);
            return;
        }
        teleport(FLAME_IN_X, FLAME_IN_Y);
    }

    /**
     * Two ruined walls, and only one of them is an obstacle.
     *
     * The one beside the ancient wall at (466,3719) is decoration: the way
     * past that area is the rune door, not a jump. The diagonal at
     * (456,3728) is the real crossing -- the replay jumps it and its fail
     * lines are recorded. The landscape carried it as a decorative byte with
     * no loc entity, so until now the map showed a gap where the wall
     * belongs. The odds and the fall are the liberties: nobody recorded
     * them, so a fifth of jumps clip the wall.
     */
    private void ruinedWall(boolean second, GameObject door) {
        Player p = getOwner();
        if (!second) {
            say("A wall, mostly fallen down. There is a gap at the top.");
            return;
        }
        if (door.getX() != JUMP_WALL_X || door.getY() != JUMP_WALL_Y) {
            say("Nothing interesting happens");
            return;
        }
        if (p.getCurStat(AGILITY) < 50) {
            say("You need an agility level of 50 to get over that.");
            return;
        }
        if (DataConversions.random(0, 4) == 0) {
            say("You fail to jump the wall properly and clip the wall with your legs.");
            say("You're spun around mid air and hit the floor heavily.");
            say("The fall knocks the wind out of you.");
            hurt(DataConversions.random(6, 14));
            return;
        }
        say("You jump the wall.");
        teleport(JUMP_WALL_X, p.getY() <= JUMP_WALL_Y ? JUMP_WALL_Y + 1 : JUMP_WALL_Y - 1);
    }

    /**
     * The Ancient Wall, which is a five letter word spelled in runes.
     *
     * Soul, mind, earth, law, law -- S, M, E, L, L -- and searching it reads
     * the riddle that spells the word. Once it is open it stays open: the
     * count is cleared with everything else at the end of the quest, so the
     * test after that is simply having reached the book.
     */
    private void ancientWall(boolean second, GameObject door) {
        if (door.getX() == DOORWAY_X) {
            magicDoorway();
            return;
        }
        int done = field(RUNE_MASK, RUNE_SHIFT);
        if (second) {
            /*
             * Search text and the riddle window transcribed from recorded
             * footage of the original client (Pieterjanvdhd, "Legends Quest
             * first go", 1:07:48). "% %" is a blank line; a bare "%%" draws
             * the second '%' literally.
             */
            say("You search the wall...");
            say("You find five slightly round depressions and some strange markings..");
            say("There is a lot of dirt and mould growing over the markings, but you clear it out.");
            say("After a while you manage to see that it is some form of message.");
            say("Would you like to read it.");
            new Conversation(getOwner(), null)
                .options(new Choice("Yes, I'll read it.", "No, I won't read it.") {
                    public void picked(int option, Conversation c) {
                        if (option != 0) {
                            return;
                        }
                        c.getPlayer().getActionSender().sendAlert(
                            "Place the five in order to pass% %"
                            + "or your life will dwindle until the last% %"
                            + "All five are stones of magical power% %"
                            + "Place them wrong and your fate will sour% %"
                            + "First is of the spirit of man or beast% %"
                            + "Second is the place where thoughts are born% %"
                            + "Third is the soil from which good things grow% %"
                            + "Four and five are the rules all men should know% %"
                            + "All put together make the word of a basic sense% %"
                            + "And from perspective help make maps from indifference.", true);
                    }
                })
                .start();
            return;
        }
        if (done < SMELL.length && !past(BOOK)) {
            say("The wall is solid. The depressions are empty.");
            return;
        }
        wallDoor();
    }

    private void itemOnDoor(GameObject door, InvItem used) {
        int item = used.getID();
        if (door.getID() == FLAMEWALL) {
            if (item != BOWL_BLESSED_PURE) {
                say("Nothing interesting happens");
                return;
            }
            swap(BOWL_BLESSED_PURE, BOWL_BLESSED);
            say("You throw the water across the flames.");
            say("A section of the fire goes out with a noise like a held breath.");
            cross();
            return;
        }
        if (door.getID() != ANCIENT_WALL || door.getX() == DOORWAY_X) {
            say("Nothing interesting happens");
            return;
        }
        int done = field(RUNE_MASK, RUNE_SHIFT);
        if (done >= SMELL.length) {
            say("Every depression is full.");
            return;
        }
        if (item != SMELL[done]) {
            take(item);
            say("The rune stone burns red hot in your hand, you drop it to the floor");
            hurt(DataConversions.random(3, 8));
            /* A wrong rune wipes the sequence: the word has to be spelled whole. */
            setField(RUNE_MASK, RUNE_SHIFT, 0);
            return;
        }
        take(item);
        done++;
        setField(RUNE_MASK, RUNE_SHIFT, done);
        /*
         * Placement lines transcribed from the same footage as the riddle,
         * including "second slot depression" -- that is really what it says.
         */
        say("You slide the " + RUNE_NAMES[done - 1] + " into the "
            + RUNE_ORDINALS[done - 1] + " depression...");
        say("It glows slightly and merges with the wall.");
        say("The letter '" + "SMELL".charAt(done - 1) + "' appears where the "
            + RUNE_NAMES[done - 1] + " merged with the door.");
        if (done == SMELL.length) {
            say("You see a small door outline starting to form in the wall.");
            say("And then a well formed door handle emerges, suddenly the door cracks open.");
            wallDoor();
        }
    }

    /**
     * The opened ancient wall, from the fifth rune and every click after.
     *
     * Only ever entered from the west: the wall face cannot be reached from
     * the pool side at all -- the way back is the magical doorway on the east
     * face of the same rock band.
     */
    private void wallDoor() {
        say("Would you like to go through?");
        new Conversation(getOwner(), null)
            .options(new Choice("Yes, I'll go through.", "No, I'll stay here.") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    teleport(WALL_EAST_X, WALL_EAST_Y);
                }
            })
            .start();
    }

    /**
     * The way back out of the pool chamber. Transit lines transcribed from
     * the recorded playthrough; it puts you back in front of the ancient
     * wall, which stays open once the word is spelled.
     */
    private void magicDoorway() {
        say("You walk into the darkness of the magical doorway.");
        say("You walk for a short way before pushing open another door.");
        say("You appear in a small walled cavern");
        say("There seems to be an exit to the south east.");
        teleport(WALL_WEST_X, WALL_WEST_Y);
    }

    // ------------------------------------------------------- spell on scenery --

    /**
     * A Charge Orb spell cast at the Dark Metal Gate.
     *
     * Any of the four will do. The orb is spent as one of the spell's runes and
     * is gone before this runs, which is why nothing here takes it: the gate
     * eats the charge and puts the player on the far side of it.
     */
    /**
     * The gates are a matched pair -- 474,3719 on the pool-room side and
     * 474,3715 on the barrel-room side -- and the cast carries you through
     * whichever one you are standing at, like the Heavy Metal Gate's shove.
     * Every line here is the footage's, including the dread speech that
     * plays the moment you arrive on the hole side; the return trip is not
     * in the footage, so it gets the crossing lines only.
     */
    public void spellCast(GameObject object, int spellId) {
        if (object.getID() != DARK_GATE) {
            say("Nothing interesting happens");
            return;
        }
        say("The orb shatters with the power of the magic.");
        say("The spell works and the gates open.");
        say("You magically appear in a different part of the cave system.");
        // Each landing is the far gate's own tile -- the only tile on either
        // side that is always clear. A Strange Barrel stands at 474,3714,
        // directly through the gate, and is only gone while smashed.
        if (object.getY() >= 3717) {
            teleport(474, 3715);
            say("It seems that the gate was a test of magical ability.");
            say("As soon as you enter this room, you are filled with dread.");
            say("In the centre of the room is a large gaping hole.");
            say("It goes down a long way...");
        } else {
            teleport(474, 3719);
        }
    }

    // -------------------------------------------------------------- notes --

    /**
     * Nothing outside this quest reports anything to it, so note() and
     * reached() are left as the base class has them.
     */
}
