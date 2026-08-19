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
 * Underground pass. Released 3 March 2003, written by Paul Gower and Thomas
 * Woode, and the longest quest Jagex ever put in Classic.
 *
 * King Lathas' scouts have found the way west: an ancient tunnel under the
 * Impassable Mountains that ends at the Well of Voyage. It ends there because
 * a madman calling himself the son of Zamorak has turned the well into the Pit
 * of the Damned, and Iban is not something you can fight. He was conjured into
 * a rag doll, and the only way to end him is to find that doll and put his
 * four elements back into it -- his shadow, his flesh, his blood and his
 * conscience -- and then throw it into his own pit.
 *
 * The quest spans four levels and two planes. Everything at y 3400-3560 is the
 * underground plane; the platforms, the dwarf camp and the spider nest are on
 * an unreachable corner of the surface plane at x 660-835, y 560-700, which is
 * where Jagex put them because Classic has only four planes and this dungeon
 * needed five levels.
 *
 *     Lathas         npc 512, (613,602), Ardougne castle
 *     Koftik         npcs 626 (713,582) outside the cave, 627 (702,3420) at
 *                    the bridge, 628 (723,3461) under the well, 629 (763,3441)
 *                    past the unicorn, 659 (676,3493) past the gate, and
 *                    650 (740,584) in the room you fall into at the end
 *     Niloof         npc 642, (759,661)      Klank   npc 648, (761,661)
 *     Kardia         npc 643, (773,3537)     Iban    npc 649, (804,3469)
 *     Paladins       npc 632 bearded (725,3416), npc 633 x2 (725,3414)
 *     Demons         645 Othainian, 646 Doomion, 647 Holthion
 *     Kalrag         npc 641, (727,581)      Souless npcs 644 and 655
 *
 *     Damp cloth 989   Arrow 984   Lit Arrow 985   Orbs of light 991-994
 *     Railing 995      Randas's journal 996        Unicorn horn 997
 *     Coat of Arms 998 red / 999 blue              Staff of Iban 1000
 *     Dwarf brew 1001  Ibans Ashes 1002            Cat 1003
 *     Doll of Iban 1004  Old Journal 1005          Klank's gauntlets 1006
 *     Iban's shadow 1007  Iban's conscience 1008
 *     Amulets 1009 Othainian / 1010 Doomion / 1011 Holthion
 *
 * What this class does and does not own is a deliberate line. The Underground
 * Pass is, mechanically, an Agility dungeon: well over a hundred Ledges,
 * Passages, Spiked pits, Piles of mud, Swamps and stalactite swings, plus a
 * twenty-five square Grill maze and two pick-lockable railings. None of that
 * is quest logic -- it is how the dungeon is traversed, it has to work for
 * anybody standing in it, and associateObject would take each one away from
 * every other handler for good. So this quest claims only the machinery that
 * exists for the quest and means nothing without it.
 *
 * That line was drawn expecting Agility and Thieving to pick the traversal up.
 * Thieving took the two jail railings and the pick-lockable shortcut and that
 * is all it was ever owed; Agility took nothing, because these obstacles are
 * not Agility shortcuts -- the recovered table lists exactly one Underground
 * Pass row, a Rock at level 1 for no experience, and the ledges and pits carry
 * no level and no payout at all. They are traversal, not skill.
 *
 * The traversal now exists and it is not in this file. Every ledge, rock, pile
 * of mud, passage, stone bridge and swamp in the dungeon is written down in
 * conf/server/locs/extras/ObjectAgilityLoc.xml.gz -- a hundred and fifty-six
 * crossings keyed by where the obstacle stands rather than by its id, because
 * the pass reuses the same eleven ledge ids up and down a dungeon a hundred and
 * eighty tiles across. ObjectAction reads it through the same handleAgility
 * that reads the courses. The grill maze is handleGrill in the same class.
 *
 * So the line drawn above held: the dungeon is walkable now and this quest did
 * not grow by a single associateObject to make it so.
 */
public class UndergroundPass extends Quest {

    private static final int UID = Quests.UNDERGROUND_PASS;

    private static final int LATHAS = 512;
    private static final int KOFTIK_CAVE = 626, KOFTIK_BRIDGE = 627, KOFTIK_WELL = 628;
    private static final int KOFTIK_UNICORN = 629, KOFTIK_END = 650, KOFTIK_GATE = 659;
    private static final int PALADIN_BEARD = 632, PALADIN = 633;
    private static final int KALRAG = 641, NILOOF = 642, KARDIA = 643;
    /**
     * A third trapped dwarf, alongside Niloof and Klank -- came looking for
     * rare ore, sealed in by Iban the same as the other two. Spawns in the
     * dwarf camp but had no dialogue at all before this fix; see
     * rsc-npc-no-purpose-sweep memory. Kept to the same "give food" beat
     * Niloof already has, since nothing more specific to Kamen was found on
     * a fetchable source.
     */
    private static final int KAMEN = 657;
    private static final int OTHAINIAN = 645, DOOMION = 646, HOLTHION = 647;
    private static final int KLANK = 648, IBAN = 649, DISCIPLE = 658;
    /**
     * North of West Ardougne, unrelated to the dwarf camp. Repairs a damaged
     * Staff of Iban (1031, not wieldable) into a working one (1000, the same
     * id this quest's own reward already uses) for 200,000gp, gated on this
     * quest being completed -- had no dialogue at all before this fix, see
     * rsc-npc-no-purpose-sweep memory.
     */
    private static final int DARK_MAGE = 667;
    private static final int BROKEN_STAFF = 1031;
    private static final int STAFF_REPAIR_COST = 200000;
    private static final int SOULESS_A = 644, SOULESS_B = 655;

    private static final int ROPE = 237, TINDERBOX = 166, BUCKET = 21;
    private static final int CLOTH = 989, ARROW = 984, LIT_ARROW = 985;
    private static final int ORB_A = 991, ORB_B = 992, ORB_C = 993, ORB_D = 994;
    private static final int RAILING = 995, JOURNAL = 996, HORN = 997;
    private static final int COAT_RED = 998, COAT_BLUE = 999;
    private static final int STAFF = 1000, BREW = 1001, ASHES = 1002, CAT = 1003;
    private static final int DOLL = 1004, OLD_JOURNAL = 1005, GAUNTLETS = 1006;
    private static final int SHADOW = 1007, CONSCIENCE = 1008;
    private static final int AMULET_OTH = 1009, AMULET_DOO = 1010, AMULET_HOL = 1011;
    private static final int DEATH_RUNE = 38, FIRE_RUNE = 31;

    /** The finished arrows a player might reasonably wrap the cloth around. */
    private static final int[] ARROWS = { 11, 638, 640, 642, 644, 646,
        574, 639, 641, 643, 645, 647 };

    private static final int CAVE = 725, CAVE_X = 715, CAVE_Y = 580;
    private static final int BRIDGE = 726, BRIDGE_CROSSED = 727;
    private static final int BRIDGE_X = 704, BRIDGE_Y = 3417;
    private static final int FIRE = 97;
    private static final int FIRE_A_X = 701, FIRE_A_Y = 3420;
    private static final int FIRE_B_X = 714, FIRE_B_Y = 3426;
    private static final int FIRE_C_X = 723, FIRE_C_Y = 3415;
    private static final int FURNACE = 813, FURNACE_X = 706, FURNACE_Y = 3453;
    private static final int WELL = 814, WELL_X = 727, WELL_Y = 3447;
    private static final int STALAGMITE = 818, STAL_X = 738, STAL_Y = 3452;
    private static final int CAGE_LEVER = 801;
    private static final int CAGE_SHUT = 802, CAGE_OPEN = 803;
    private static final int CAGE_X = 690, CAGE_Y = 3449;
    private static final int SOIL_A = 839, SOIL_A_X = 743, SOIL_A_Y = 3457;
    private static final int SOIL_B = 840, SOIL_B_X = 746, SOIL_B_Y = 3470;
    private static final int BOULDER = 867;
    private static final int BOULDER_X = 761, BOULDER_Y = 3500;
    private static final int CAGE_REMAINS = 871, REMAINS_X = 761, REMAINS_Y = 3499;
    // The unicorn's cage door -- a wall object, railings id 168. Searching it
    // is where the loose railing comes from.
    private static final int CAGE_RAILS = 168, CAGE_RAILS_X = 744, CAGE_RAILS_Y = 3499;

    /**
     * The bent railings into Kalrag's nest, on the surface-plane floor: two
     * wall objects side by side, both facing 0, so the squeeze crosses in y.
     */
    private static final int SPIDER_RAILS = 171, SPIDER_RAILS_Y = 607;
    /* The unicorn's room exists twice on the map, seventeen tiles apart: the
       west copy with the living unicorn and the boulder still up on the ledge,
       the east copy with the cage crushed and the boulder lying beside it.
       The ledges have to keep up the illusion that it is one room -- every way
       in targets whichever copy matches the quest, and the ways out land in
       the shared areas either copy connects to. */
    private static final int LEDGE_A = 862, LEDGE_B = 863, LEDGE_C = 864,
        LEDGE_D = 865, LEDGE_E = 866, LEDGE_F = 872;
    private static final int FLAMES = 830, FLAMES_X = 760, FLAMES_Y = 3416;
    private static final int GATE = 722, GATE_X = 764, GATE_Y = 3417;
    private static final int TOMB = 878, TOMB_X = 724, TOMB_Y = 654;
    private static final int BARREL = 880, BARREL_X = 764, BARREL_Y = 665;
    private static final int DOLL_CHEST = 885, DOLL_CHEST_X = 773, DOLL_CHEST_Y = 3538;
    private static final int SHADOW_CHEST = 912, SHADOW_CHEST_X = 799, SHADOW_CHEST_Y = 3533;
    private static final int WITCH_DOOR = 173, WITCH_DOOR_X = 773, WITCH_DOOR_Y = 3536;
    private static final int SOULESS_CAGE_A = 887, SOULESS_CAGE_A_X = 807, SOULESS_CAGE_A_Y = 3434;
    private static final int SOULESS_CAGE_B = 888;
    private static final int PIT = 913, PIT_X = 802, PIT_Y = 3469;
    private static final int CRATE = 868, CRATE_X = 725, CRATE_Y = 3463;
    private static final int GRILL_E = 836, GRILL_E_X = 762, GRILL_E_Y = 3463;
    private static final int TEMPLE = 784, TEMPLE_X = 798, TEMPLE_Y = 3469;

    /**
     * The temple's actual doors: object 869 ("Door", open) on the west face.
     * 784 itself only carries WalkTo/Examine, so a click on it never reaches
     * the quest -- every entrance goes through this door. 914 is its open form.
     */
    private static final int TEMPLE_DOOR = 869, TEMPLE_DOOR_X = 793, TEMPLE_DOOR_Y = 3469;
    private static final int TEMPLE_DOOR_OPEN = 914;

    /** Where the cave entrance drops you, and where Koftik leads you back out. */
    /*
     * Standing tiles, not object tiles. Three of these used to be the object's
     * own square -- the crumbled rock at (672,3420), the far end of the old
     * bridge at (707,3417) and whatever stands at (740,585) -- and every one of
     * those is solid. Teleporting a player into solid scenery leaves them
     * walled in with no way to walk out, which is what happened to anyone who
     * entered the cave. Each is now the walkable tile beside it.
     */
    private static final int PASS_X = 673, PASS_Y = 3420;
    /** Across the burnt bridge. */
    private static final int OVER_BRIDGE_X = 709, OVER_BRIDGE_Y = 3419;
    /** Down the well, into the second level. */
    private static final int WELL_DOWN_X = 723, WELL_DOWN_Y = 3462;
    /** Through the Gate of Iban, onto the platforms. */
    private static final int PAST_GATE_X = 770, PAST_GATE_Y = 3417;
    /**
     * The Ardounge wall gateway, the official way through the quarantine wall
     * once this quest is under way. It is two objects facing each other across
     * the wall line at x=623: 622 on the East Ardougne side and 624 on the
     * West. Listed among Biohazard's rewards, but its transcript gates it on
     * this quest, so it lives here.
     */
    private static final int WALL_GATE = 450;
    private static final int WALL_GATE_E_X = 622, WALL_GATE_W_X = 624;
    private static final int WALL_GATE_Y = 588;
    /** Into the Zamorakian temple, and the room you are thrown into after. */
    private static final int TEMPLE_IN_X = 795, TEMPLE_IN_Y = 3469;
    private static final int AFTER_X = 740, AFTER_Y = 586;

    private static final int ATTACK = 0, HITS = 3, RANGED = 4, AGILITY = 16;
    private static final int RANGED_LEVEL = 25;

    private static final int STARTED = 1, KOFTIK = 2, ENTERED = 3, CLOTH_GIVEN = 4,
        BRIDGE_DOWN = 5, ORBS_DONE = 6, DOWN_WELL = 7, UNICORN = 8, GATE_OPEN = 9,
        DWARVES = 10, HAVE_DOLL = 11, HAS_SHADOW = 12, HAS_ASHES = 13,
        HAS_BLOOD = 14, DOLL_DONE = 15, IBAN_DEAD = 16, FINISHED = 17;

    private static final int STAGE_MASK = 31;

    /**
     * Scratch bits above the stage. Only the stage int is persisted, so every
     * piece of state that has to survive a logout is packed in beside it: which
     * of the four orbs have gone into the furnace, what has been fed to the
     * Flames of zamorak, and whether Kardia has taken her cat.
     */
    private static final int ORB1 = 32, ORB2 = 64, ORB3 = 128, ORB4 = 256;
    private static final int ORBS_ALL = ORB1 | ORB2 | ORB3 | ORB4;
    private static final int COAT1 = 512, COAT2 = 1024, COAT3 = 2048;
    private static final int COATS_ALL = COAT1 | COAT2 | COAT3;
    /** The unicorn horn has gone into the Flames of zamorak. */
    private static final int HORN_IN = 4096;
    /** Kardia has been given her cat and will open her door. */
    private static final int CAT_TAKEN = 8192;
    /** The Koftik at the gate has said his piece and gone quiet. */
    private static final int KNOCKED = 16384;
    /** The Tomb of Iban has been doused in dwarf brew and will now light. */
    private static final int SOAKED = 32768;
    /** A rope is tied to the stalagmite. */
    private static final int ROPED = 65536;
    /**
     * The four elements of the doll, one bit each, because vanilla accepts
     * them in any order. The old linear stages HAS_SHADOW..DOLL_DONE are
     * still honoured when reading (saves from before the change), but new
     * progress is recorded here and the stage only moves when the doll is
     * complete.
     */
    private static final int E_SHADOW = 131072, E_ASHES = 262144,
        E_BLOOD = 524288, E_CONS = 1048576;
    /** The crate by the well has given up its food. */
    private static final int CRATE_DONE = 2097152;
    /** The bearded paladin has handed over his rations. */
    private static final int PAL_FED = 4194304;
    /** A rope is tied to the high east wall grill and stays there. */
    private static final int GRILL_ROPED = 8388608;
    private static final int BITS = ORBS_ALL | COATS_ALL | HORN_IN | CAT_TAKEN
        | KNOCKED | SOAKED | ROPED
        | E_SHADOW | E_ASHES | E_BLOOD | E_CONS | CRATE_DONE | PAL_FED
        | GRILL_ROPED;

    public UndergroundPass(Player owner, Integer uid) {
        super(owner, uid);
    }

    public void define() {
        setUID(UID);
        setName("Underground pass");
        setFinalStage(FINISHED);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("King Lathas wants the ancient pass under the western mountains reopened, but Iban, the self-styled son of Zamorak, holds it. Restore his four elements to the doll he was conjured into and throw it into his own pit.");
        setStartPoint("Ardougne castle");
        setSpeakTo("King Lathas");
        requireQuest(Quests.BIOHAZARD);
        requireLevel(RANGED, RANGED_LEVEL);
        rewardExp(ATTACK, 500, 50);
        rewardExp(AGILITY, 500, 50);
        rewardOther("The Staff of Iban, 15 death runes and 30 fire runes, found on Iban's remains");

        // King Lathas ends Biohazard and starts this quest, so both claim him
        // and both are dispatched when he is talked to. The two are guarded to
        // be mutually exclusive: Biohazard speaks only until it is complete,
        // this one only once it is.
        // @share npc 512 with Biohazard
        associateNpc(LATHAS);
        associateNpc(KOFTIK_CAVE);
        associateNpc(KOFTIK_BRIDGE);
        associateNpc(KOFTIK_WELL);
        associateNpc(KOFTIK_UNICORN);
        associateNpc(KOFTIK_GATE);
        associateNpc(KOFTIK_END);
        associateNpc(NILOOF);
        associateNpc(KLANK);
        associateNpc(KAMEN);
        associateNpc(KARDIA);
        associateNpc(DARK_MAGE);
        associateNpc(PALADIN_BEARD);
        associateNpc(PALADIN);
        associateNpc(OTHAINIAN);
        associateNpc(DOOMION);
        associateNpc(HOLTHION);
        associateNpc(KALRAG);
        associateNpc(DISCIPLE);

        associateObject(CAVE, CAVE_X, CAVE_Y);
        associateObject(BRIDGE, BRIDGE_X, BRIDGE_Y);
        associateObject(BRIDGE_CROSSED, BRIDGE_X, BRIDGE_Y);
        // Object 97 is the ordinary camp fire and there are 67 of them in the
        // world. Only the three beside the bridge are claimed, by tile, so the
        // other sixty-four still cook.
        associateObject(FIRE, FIRE_A_X, FIRE_A_Y);
        associateObject(FIRE, FIRE_B_X, FIRE_B_Y);
        associateObject(FIRE, FIRE_C_X, FIRE_C_Y);
        associateObject(FURNACE, FURNACE_X, FURNACE_Y);
        associateObject(WELL, WELL_X, WELL_Y);
        associateObject(STALAGMITE, STAL_X, STAL_Y);
        associateObject(CAGE_LEVER);
        associateObject(CAGE_SHUT, CAGE_X, CAGE_Y);
        associateObject(CAGE_OPEN, CAGE_X, CAGE_Y);
        associateObject(SOIL_A, SOIL_A_X, SOIL_A_Y);
        associateObject(SOIL_B, SOIL_B_X, SOIL_B_Y);
        associateObject(BOULDER);
        associateObject(CAGE_REMAINS, REMAINS_X, REMAINS_Y);
        // Railings id 168 also fences the dwarf camp; only the unicorn's cage
        // door is claimed.
        associateDoor(CAGE_RAILS, CAGE_RAILS_X, CAGE_RAILS_Y);
        associateDoor(SPIDER_RAILS, 727, SPIDER_RAILS_Y);
        associateDoor(SPIDER_RAILS, 728, SPIDER_RAILS_Y);
        associateObject(LEDGE_A, 733, 3496);
        associateObject(LEDGE_A, 750, 3496);
        associateObject(LEDGE_B, 749, 3497);
        associateObject(LEDGE_B, 766, 3497);
        associateObject(LEDGE_C, 732, 3494);
        associateObject(LEDGE_D, 739, 3502);
        associateObject(LEDGE_D, 756, 3502);
        associateObject(LEDGE_E, 728, 3498);
        associateObject(LEDGE_F, 765, 3439);
        associateObject(CRATE, CRATE_X, CRATE_Y);
        associateObject(GRILL_E, GRILL_E_X, GRILL_E_Y);
        associateObject(FLAMES, FLAMES_X, FLAMES_Y);
        associateObject(GATE, GATE_X, GATE_Y);
        associateObject(TOMB, TOMB_X, TOMB_Y);
        associateObject(BARREL, BARREL_X, BARREL_Y);
        associateObject(DOLL_CHEST, DOLL_CHEST_X, DOLL_CHEST_Y);
        associateObject(SHADOW_CHEST, SHADOW_CHEST_X, SHADOW_CHEST_Y);
        // Door 173 is a wall object -- it dispatches through the door table,
        // not the scenery one. It is a plain door and there is another one in
        // Draynor; only Kardia's is claimed.
        associateDoor(WITCH_DOOR, WITCH_DOOR_X, WITCH_DOOR_Y);
        associateObject(SOULESS_CAGE_A, SOULESS_CAGE_A_X, SOULESS_CAGE_A_Y);
        associateObject(SOULESS_CAGE_B);
        associateObject(PIT, PIT_X, PIT_Y);
        associateObject(TEMPLE, TEMPLE_X, TEMPLE_Y);
        associateObject(TEMPLE_DOOR, TEMPLE_DOOR_X, TEMPLE_DOOR_Y);
        associateObject(WALL_GATE, WALL_GATE_E_X, WALL_GATE_Y);
        associateObject(WALL_GATE, WALL_GATE_W_X, WALL_GATE_Y);

        associateItem(CLOTH);
        associateItem(ARROW);
        associateItem(LIT_ARROW);
        associateItem(ORB_A);
        associateItem(ORB_B);
        associateItem(ORB_C);
        associateItem(ORB_D);
        associateItem(RAILING);
        associateItem(JOURNAL);
        associateItem(HORN);
        associateItem(COAT_RED);
        associateItem(COAT_BLUE);
        associateItem(BREW);
        associateItem(ASHES);
        associateItem(CAT);
        associateItem(DOLL);
        associateItem(OLD_JOURNAL);
        associateItem(SHADOW);
        associateItem(CONSCIENCE);
        associateItem(AMULET_OTH);
        associateItem(AMULET_DOO);
        associateItem(AMULET_HOL);
        // The broken staff (1031) is claimed by this quest's own repair NPC
        // (Dark Mage), but its "wield" command had no handler, so trying to
        // wield it before repair silently did nothing.
        associateItem(BROKEN_STAFF);
        // Finished arrows carry no inventory command and take part in no
        // built-in recipe -- fletching pairs feathers 381 with shafts 280, not
        // with these -- so claiming them only enables the damp cloth pairing.
        for (int i = 0; i < ARROWS.length; i++) {
            associateItem(ARROWS[i]);
        }
    }

    public void completeQuest() {
        grantRewards();
        Player p = getOwner();
        p.getActionSender().sendMessage("Well Done!");
        p.getActionSender().sendMessage("Well done.You have completed the Underground pass quest");
        p.getActionSender().sendMessage("You gain some experience in attack and agility.");
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

    private boolean marked(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    /** The doll is in hand and still incomplete: elements can be gathered. */
    private boolean collecting() {
        return stage() >= HAVE_DOLL && stage() < DOLL_DONE;
    }

    /**
     * Whether an element is on the doll, reading both the new any-order bits
     * and the linear stages that recorded the same thing before them.
     */
    private boolean elem(int bit) {
        if (marked(bit)) {
            return true;
        }
        switch (bit) {
            case E_SHADOW: return past(HAS_SHADOW);
            case E_ASHES: return past(HAS_ASHES);
            case E_BLOOD: return past(HAS_BLOOD);
            default: return past(DOLL_DONE);
        }
    }

    private void addElem(int bit) {
        mark(bit);
        if (elem(E_SHADOW) && elem(E_ASHES) && elem(E_BLOOD) && elem(E_CONS)
                && collecting()) {
            say("the doll shivers in your hands");
            say("it is finished");
            step(DOLL_DONE);
        }
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
        p.setCurStat(HITS, Math.max(0, p.getCurStat(HITS) - damage));
        p.getActionSender().sendStat(HITS);
    }

    private boolean biohazardDone() {
        return getOwner().getQuestManager().completed(Quests.BIOHAZARD);
    }

    /** The one skill requirement Jagex put on the quest itself. */
    private boolean canRange() {
        if (getOwner().getMaxStat(RANGED) >= RANGED_LEVEL) {
            return true;
        }
        say("You need a ranged level of " + RANGED_LEVEL + " to do this");
        return false;
    }

    private void swap(int fromId, int toId, int x, int y) {
        Point at = Point.location(x, y);
        int dir = 0;
        int type = 0;
        if (world.getTile(x, y).hasGameObject()) {
            GameObject cur = world.getTile(x, y).getGameObject();
            if (cur.getID() == toId) {
                return;
            }
            /* The replacement keeps the old object's facing -- the opened
               cage spawned at direction 0 and stood a quarter turn out from
               the shut one it replaced. */
            dir = cur.getDirection();
            type = cur.getType();
        }
        world.registerGameObject(new GameObject(at, toId, dir, type));
    }

    private int countBits(int mask) {
        int n = 0;
        for (int b = mask; b != 0; b >>= 1) {
            n += b & 1;
        }
        return n;
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
            killed(npc.getID());
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_NPC) {
            if (used != null && used.getID() == DOLL && npc.getID() == NILOOF) {
                showNiloof(npc);
            } else {
                say("Nothing interesting happens.");
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case LATHAS: lathas(npc); break;
            case KOFTIK_CAVE: koftikCave(npc); break;
            case KOFTIK_BRIDGE: koftikBridge(npc); break;
            case KOFTIK_WELL: koftikWell(npc); break;
            case KOFTIK_UNICORN: koftikUnicorn(npc); break;
            case KOFTIK_GATE: koftikGate(npc); break;
            case KOFTIK_END: koftikEnd(npc); break;
            case NILOOF: niloof(npc); break;
            case KLANK: klank(npc); break;
            case KAMEN: kamen(npc); break;
            case DARK_MAGE: darkMage(npc); break;
            case KARDIA: say("kardia doesn't seem interested in talking"); break;
            case PALADIN_BEARD: paladin(npc, true); break;
            case PALADIN: paladin(npc, false); break;
            case DISCIPLE: disciple(npc); break;
            default: break;
        }
    }

    private void scenery(QuestTrigger trigger, GameObject object, InvItem used) {
        switch (object.getID()) {
            case CAVE: caveEntrance(trigger); return;
            case BRIDGE: case BRIDGE_CROSSED: oldBridge(trigger, used); return;
            case FIRE: campFire(trigger, used); return;
            case FURNACE: furnace(trigger, used); return;
            case WELL: theWell(trigger); return;
            case STALAGMITE: stalagmite(trigger, used); return;
            case CAGE_LEVER: cageLever(trigger); return;
            case CAGE_SHUT: say("the cage is shut"); return;
            case CAGE_OPEN: say("the cage stands open"); return;
            case SOIL_A: case SOIL_B: dugSoil(trigger); return;
            case BOULDER: boulder(trigger, used); return;
            case CAGE_REMAINS: unicornCage(trigger); return;
            case CAGE_RAILS: cageRailings(trigger); return;
            case SPIDER_RAILS: spiderRailings(object); return;
            case LEDGE_A: case LEDGE_B: case LEDGE_C:
            case LEDGE_D: case LEDGE_E: case LEDGE_F:
                ledge(trigger, object); return;
            case CRATE: crate(trigger); return;
            case GRILL_E: wallGrill(trigger, used); return;
            case FLAMES: flames(trigger, used); return;
            case GATE: gateOfIban(trigger); return;
            case TOMB: tombOfIban(trigger, used); return;
            case BARREL: brewBarrel(trigger, used); return;
            case DOLL_CHEST: dollChest(trigger); return;
            case SHADOW_CHEST: shadowChest(trigger); return;
            case WITCH_DOOR: witchDoor(trigger, used); return;
            case SOULESS_CAGE_A: case SOULESS_CAGE_B: soulessCage(trigger); return;
            case PIT: pitOfTheDamned(trigger, used); return;
            case TEMPLE: zamorakianTemple(trigger); return;
            case TEMPLE_DOOR: templeDoor(trigger); return;
            case WALL_GATE: wallGateway(trigger); return;
            default: return;
        }
    }

    // -------------------------------------------------------- King Lathas --

    private void lathas(final Npc npc) {
        Player p = getOwner();
        if (!biohazardDone()) {
            // Biohazard owns Lathas until it is finished, and it is a Quest of
            // its own that has already claimed him, so this one stays quiet.
            return;
        }
        if (completed()) {
            Conversation c = new Conversation(p, npc);
            c.player("hello king lathas");
            c.npc("greetings my friend, and thank you again");
            c.npc("the mages are working on the well as we speak");
            c.start();
            return;
        }
        if (at(IBAN_DEAD)) {
            Conversation c = new Conversation(p, npc);
            c.npc("the traveller returns..any news?");
            c.player("indeed, the quest is complete lathas");
            c.player("i have defeated iban and his undead minions");
            c.npc("incrediable, you are a truly awesome warrior");
            c.npc("now we can begin to restore the well");
            c.then(new Effect() {
                public void run(Conversation c) {
                    setStage(FINISHED);
                }
            });
            c.start();
            return;
        }
        if (questStarted()) {
            Conversation c = new Conversation(p, npc);
            c.player("hello king lanthas");
            c.npc("traveller, how are you managing down there?");
            c.player("it's a pretty nasty place but i'm ok");
            c.npc("well keep up the good work");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.player("hello king lathas");
        c.npc("adventurer, thank saradomin for your arrival");
        c.player("have your scouts found a way though the mountains");
        c.npc("Not quite, we found a path to where we expected..");
        c.npc("..to find the 'well of voyage' an ancient portal to west runescape");
        c.npc("however over the past era's a cluster of cultists");
        c.npc("have settled there, run by a madman named iban");
        c.player("iban?");
        c.npc("a crazy loon who claims to be the son of zamorok");
        c.npc("go meet my main tracker koftik, he will help you");
        c.npc("he waits for you at the west side of west ardounge");
        c.npc("we must find a way through these caverns..");
        c.npc("if we are to stop my brother tyras");
        c.player("i'll do my best lathas");
        c.npc("a warning traveller the ungerground pass..");
        c.npc("is lethal, we lost many men exploring those caverns");
        c.npc("go preparred with food and armour or you won't last long");
        c.then(new Effect() {
            public void run(Conversation c) {
                if (!questStarted()) {
                    step(STARTED);
                }
            }
        });
        c.start();
    }

    // ------------------------------------------------------------- Koftik --

    private void koftikCave(final Npc npc) {
        if (!questStarted()) {
            say("koftik doesn't seem interested in talking");
            return;
        }
        if (past(IBAN_DEAD)) {
            koftikAfter(npc);
            return;
        }
        if (past(KOFTIK)) {
            Conversation c = new Conversation(getOwner(), npc);
            if (past(DOWN_WELL)) {
                c.player("hello koftik");
                c.npc("it scares me in there");
                c.npc("the voices, don't you hear them?");
                c.player("you'll be ok koftik");
            } else if (past(BRIDGE_DOWN)) {
                c.player("hello koftik");
                c.npc("once your over the bridge keep going...");
                c.npc("..straight ahead, i'll meet you further up");
            } else {
                c.npc("i know it's scary in there");
                c.npc("but you'll have to go in alone");
                c.npc("i'll catch up as soon as i can");
            }
            c.start();
            return;
        }
        Conversation c = new Conversation(getOwner(), npc);
        c.player("hello there, are you the kings scout?");
        c.npc("that i am brave adventurer");
        c.npc("King lathas informed me that you need to cross these mountains");
        c.npc("i'm afraid you'll have to go through the ancient underground pass");
        c.player("That's ok, i've travelled through many a cave in my time");
        c.npc("these caves are different..they're filled with the spirit of Zamorak");
        c.npc("You can feel it as you wind your way round the stalactites..");
        c.npc("an icy chill that penetrate's the very fabric of your being");
        c.npc("not so many travellers come down here these days...");
        c.npc("...but there are some who are still foolhardy enough");
        c.options(new Choice("i'll take my chances", "tell me more") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("I remember seeing one such warrior. Going by the name of Randas...");
                    c.npc("..he stood tall and proud like an elven king...");
                    c.npc("..that same pride made him vulnerable to Zamorak's calls...");
                    c.npc("..Randas's worthy desire to be a great and mighty warrior...");
                    c.npc("..also made him corruptible to Zamorak's promises of glory...");
                    c.npc("..Zamorak showed him a way to achieve his goals, by appealing...");
                    c.npc("..to that most base and dark nature that resides in all of us...");
                    c.player("what happened to him?");
                    c.npc("no one knows");
                } else {
                    c.npc("ok traveller, i'll catch up with you by the bridge");
                }
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (at(STARTED)) {
                            step(KOFTIK);
                        }
                    }
                });
            }
        });
        c.start();
    }

    private void koftikBridge(final Npc npc) {
        Player p = getOwner();
        if (past(BRIDGE_DOWN)) {
            Conversation c = new Conversation(p, npc);
            c.player("hi koftik");
            if (!holds(CLOTH)) {
                c.message("koftik gives you a damp cloth");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        give(CLOTH);
                    }
                });
            }
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.player("koftik, how can we cross the bridge?");
        c.npc("i'm not sure, seems as if others were here before us though");
        c.npc("i found this cloth amongst the charred remains of arrows");
        c.player("charred arrows?");
        c.npc("they must have been trying to burn something");
        c.player("or someone!");
        c.player("interesting, we better keep our eyes open");
        c.npc("There also seems to the remains of a diary");
        c.options(new Choice("not to worry, probably just kid litter", "what does it say?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("well..maybe?");
                } else {
                    c.message("@red@it seems to be written by the adventurer Randas, it reads...");
                    c.message("@red@It began as a whisper in my ears. Dismissing the sounds...");
                    c.message("@red@..as the whistling of the wind, I steeled myself against...");
                    c.message("@red@..these forces and continued on my way");
                    c.message("@red@But the whispers became moans...");
                    c.message("@red@at once fearsome and enticing like the call of some beautiful siren");
                    c.message("@red@Join us! The voices cried, Join us!");
                    c.message("@red@Your greatness lies within you, but only Zamorak can unlock your potential");
                    c.player("it sounds like randas was losing it");
                }
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (!holds(CLOTH)) {
                            say("koftik gives you a damp cloth");
                            give(CLOTH);
                        }
                        if (at(ENTERED) || at(KOFTIK)) {
                            step(CLOTH_GIVEN);
                        }
                    }
                });
            }
        }.says(0, "not to worry, probably just litter"));
        c.start();
    }

    private void koftikWell(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("hello koftik");
        c.npc("how are you bearing adventurer?");
        c.player("i'm still alive, and you?");
        c.npc("cold, i can feel it in my blood, so cold");
        c.message("koftik seems to be poorly");
        c.player("where do we go now koftik?");
        c.npc("straight on again, more winding passages");
        c.npc("more lethal traps, more blood and more pain");
        c.npc("blood..pain.. hee hee, more blood.. hee hee");
        c.player("are you sure you're ok?");
        c.npc("erm..yes..i'll be fine, just go ahead i'll catch up");
        c.start();
    }

    private void koftikUnicorn(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("hello koftik");
        if (past(UNICORN)) {
            c.npc("are you ok?, i heard a rumble further down the cavern");
            c.npc("i thought the whole place was going to cave in");
            c.player("im fine");
        } else {
            c.npc("keep back foul beast of the nigh.. ,wait, it's you!");
            c.player("as far as i know");
        }
        c.npc("i assumed you were dead, or worse");
        c.player("i've managed to survive so far");
        c.npc("the passsage ahead's blocked ,but you should be able to get through");
        c.npc("i'll follow behind");
        c.npc("aaaaaarrgghhh");
        c.player("what's wrong?");
        c.npc("it's the voices, can't you hear them?");
        c.npc("they wont leave be");
        c.npc("i feel him calling to me");
        c.start();
    }

    private void koftikGate(final Npc npc) {
        if (marked(KNOCKED)) {
            say("The Koftik does not appear interested in talking");
            return;
        }
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("traveller is that you?.. my friend on a mission");
        c.player("koftik, you're still here, you should leave");
        c.npc("leave?...leave?..this is my home now");
        c.npc("home with my lord, he talks to me, he's my friend");
        c.message("koftik seems to be in a weak state of mind");
        c.player("koftik you really should leave these caverns");
        c.npc("not now, we're all the same down here");
        c.npc("now there's just you and those dwarfs to be converted");
        c.player("dwarfs?");
        c.npc("foolish dwarfs, still believing that they can resist");
        c.npc("no one resists iban, go traveller");
        c.npc("the dwarfs to the south, they're not safe in the south");
        c.npc("we'll show them, go slay them m'lord");
        c.npc("he'll be so proud, that's all i want");
        c.player("i'll pray for you");
        c.then(new Effect() {
            public void run(Conversation c) {
                // The gate Koftik says his piece exactly once; the bit doubles
                // as the record that the dwarves have been pointed out.
                mark(KNOCKED);
            }
        });
        c.start();
    }

    /** The Koftik you land beside when the temple throws you out. */
    private void koftikEnd(final Npc npc) {
        if (!past(IBAN_DEAD)) {
            say("koftik doesn't seem interested in talking");
            return;
        }
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("traveller, where am i?, i can't remeber a thing");
        c.player("we were losing you to ibans influence");
        c.npc("what?..of corse, the voices");
        c.npc("but they've stopped, what happened?");
        c.player("ibans dead, i destroyed him");
        c.npc("you've done well, now we must inform the king");
        c.npc("he'll have to send in some high mages to...");
        c.npc("reserrect the well of voyage");
        c.npc("follow me, i'll lead you out");
        c.player("at last!, i've had enough of caves");
        c.message("koftik leads you back up through the winding caverns");
        c.message("and back to the cave entrance");
        c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(CAVE_X - 2, CAVE_Y + 2, false);
            }
        });
        c.start();
    }

    private void koftikAfter(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("thanks for getting me out koftik");
        c.npc("always a pleasure squire");
        c.npc("have you informed the king about iban?");
        c.options(new Choice("no, not yet", "yes, i've told him") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("traveller this is no time to linger");
                    c.npc("the king must know that ibans dead");
                    c.npc("this is a truly historical moment for ardounge");
                    return;
                }
                c.npc("good to hear, the sooner we find king Tyras..");
                c.npc("the better");
            }
        });
        c.start();
    }

    // ------------------------------------------------------ the dwarf camp --

    private void niloof(final Npc npc) {
        Player p = getOwner();
        if (completed()) {
            say("the dwarf seems to be busy");
            return;
        }
        if (past(HAVE_DOLL)) {
            Conversation c = new Conversation(p, npc);
            c.player("hi niloof");
            c.npc("traveller, thank the stars you're still around");
            c.npc("i thought your time had come");
            c.player("i've still a few years in me yet");
            if (!holds(DOLL)) {
                c.npc("i found something i think you need traveller");
                c.player("the doll?");
                c.npc("i found it while slaying some of the souless, here");
                c.message("niloof gives you the doll of iban");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        give(DOLL);
                    }
                });
            }
            c.player("it's about time i delt with iban");
            c.npc("good luck to you, you'll need it");
            c.npc("may the strength of the elders be with you");
            c.player("take care niloof");
            c.start();
            return;
        }
        if (past(DWARVES)) {
            Conversation c = new Conversation(p, npc);
            c.player("hello niloof");
            c.npc("so you still live, not many survive down here");
            c.player("as i can see");
            c.npc("don't stay too long traveller");
            c.npc("ibans calls will soon penetrate your delicate human mind");
            c.npc("and you'll also become one of his minions");
            c.npc("you must go above and find the witch kardia");
            c.npc("she holds the secret to ibans destruction");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("back away..back away..wait..");
        c.npc("..you're human!");
        c.player("that's right, i'm on a quest for king lathas");
        c.player("we need to find a way through these caverns");
        c.npc("ha ha, listen up, we came here as miners decades ago");
        c.npc("completely unaware of the evil that lurked in the caverns");
        c.npc("there's no way through, not while iban still rules");
        c.npc("he controls the gateway,the only way to the other side");
        c.player("what gateway?");
        c.npc("it once stood as the the 'well of voyage'");
        c.npc("a gateway to west runescape");
        c.npc("now ibans moulded it into a pit of the damned");
        c.npc("a portal to zamoraks darkest realms");
        c.npc("he sends his followers there, never to return");
        c.npc("only once iban is destroyed can the well be restored");
        c.player("but how?");
        c.npc("if i knew, i would have slain him already");
        c.npc("seek out the witch, his guide , his only confidante");
        c.npc("only she knows how to rid us of iban");
        c.npc("she lives on the platforms above, we dare not go there");
        c.npc("here, take some food to aid your journey");
        c.message("Niloof give you some food");
        c.player("thanks niloof, take care");
        c.npc("you too");
        c.then(new Effect() {
            public void run(Conversation c) {
                Player pl = c.getPlayer();
                pl.getInventory().add(new InvItem(325, 2));
                pl.getInventory().add(new InvItem(132, 2));
                pl.getActionSender().sendInventory();
                if (at(GATE_OPEN)) {
                    step(DWARVES);
                }
            }
        });
        c.start();
    }

    /**
     * Niloof explains what the doll is once he has seen it. This is the branch
     * the wiki files under "after getting the doll", and it is what tells the
     * player the four elements are a thing at all.
     */
    private void showNiloof(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("niloof, i found the witch's house");
        c.npc("and...?");
        c.player("i found a strange book and this..");
        c.message("you show niloof the strange doll");
        c.npc("the witches rag doll, this here be black magic traveller");
        c.npc("iban was magically conjured in that very item");
        c.npc("his four elements of bieng are guarded somewhere in this cave");
        c.npc("his shadow, his flesh, his conscience and his blood");
        c.npc("if you can retrieve these, with the flask...");
        c.npc("you will be able destroy iban...");
        c.npc("and ressurect the 'well of voyage'");
        c.start();
    }

    private void kamen(final Npc npc) {
        Player p = getOwner();
        if (completed()) {
            say("the dwarf seems to be busy");
            return;
        }
        if (past(DWARVES)) {
            Conversation c = new Conversation(p, npc);
            c.player("hello kamen");
            c.npc("still breathing, good");
            c.npc("came down here looking for rare ore, decades back");
            c.npc("iban sealed us in before we ever found any");
            c.npc("go on, don't waste your time on an old miner");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.npc("hic..careful now..");
        c.npc("..oh, a human, been a while since i've seen one of those");
        c.player("i'm looking for a way through, for king lathas");
        c.npc("came here for the ore myself, decades ago");
        c.npc("never found it, iban's had us penned in ever since");
        c.npc("nothing left to do but drink..hic");
        c.npc("here, have a swig of my home brew");
        c.options(new Choice("no thanks kamen, i'll pass", "cheers, don't mind if i do") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("suit yourself..hic..more for me");
                    return;
                }
                c.message("you take a sip of the strong brew");
                c.message("it tastes horrific and burns your throat");
                c.npc("ha ha..puts hairs on your chest doesn't it");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        hurt(5);
                    }
                });
            }
        });
        c.start();
    }

    private void darkMage(final Npc npc) {
        Player p = getOwner();
        if (!completed()) {
            Conversation c = new Conversation(p, npc);
            c.player("hello");
            c.npc("i deal in staffs of iban, once you've proven yourself worthy");
            c.npc("come back when king lathas trusts you with that kind of power");
            c.start();
            return;
        }
        if (!p.getInventory().wielding(BROKEN_STAFF) && p.getInventory().countId(BROKEN_STAFF) < 1) {
            Conversation c = new Conversation(p, npc);
            c.player("hello");
            c.npc("bring me a damaged staff of iban and enough coin");
            c.npc("and i'll have it working again in no time");
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.player("can you repair this staff for me?");
        c.npc("that i can, for " + STAFF_REPAIR_COST + " coins");
        c.options(new Choice("yes, repair it", "not right now") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    return;
                }
                Player pl = c.getPlayer();
                if (pl.getInventory().countId(10) < STAFF_REPAIR_COST) {
                    c.npc("come back when you have the coin");
                    return;
                }
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player pl = c.getPlayer();
                        pl.getInventory().remove(BROKEN_STAFF, 1);
                        pl.getInventory().remove(10, STAFF_REPAIR_COST);
                        pl.getInventory().add(new InvItem(STAFF, 1));
                        pl.getActionSender().sendInventory();
                    }
                });
                c.npc("there you go, good as new");
            }
        });
        c.start();
    }

    private void klank(final Npc npc) {
        Player p = getOwner();
        if (past(HAVE_DOLL)) {
            if (holds(GAUNTLETS) || wearing(GAUNTLETS)) {
                klankSpare(npc);
                return;
            }
            Conversation c = new Conversation(p, npc);
            c.player("hi klank");
            c.npc("traveller,I hear you plan to destroy iban");
            c.player("that's right");
            c.npc("i have a gift for you, they may help");
            c.npc("i crafted these long ago to protect myself...");
            c.npc("from the teeth of the souless, their bite is vicous");
            c.npc("i haven't seen a another pair which can with stand their jaws");
            c.message("klank gives you a pair of gaunlets");
            c.message("and a tinderbox");
            c.player("thanks klank");
            c.npc("good luck traveller, give iban a slap for me");
            c.then(new Effect() {
                public void run(Conversation c) {
                    give(GAUNTLETS);
                    if (!holds(TINDERBOX)) {
                        give(TINDERBOX);
                    }
                }
            });
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.player("hello my good man");
        c.npc("Good day to you outsider");
        c.npc("i'm klank, i'm the only blacksmith still alive down here");
        c.npc("infact we're the only ones that haven't yet turned");
        c.npc("if you're not carefull you'll become one of them too");
        c.player("who?.. ibans followers");
        c.npc("they're not followers, they're slaves, they're the souless");
        c.options(new Choice("what happened to them?", "no wonder their breath was soo bad") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("they were normal once, adventurers, treasure hunters");
                    c.npc("but men are weak, they couldn't ignore the vocies");
                    c.npc("now they all seem to think with one conscience..");
                    c.npc("as if they're being controlled by one being");
                    c.player("iban?");
                    c.npc("maybe?... maybe zamorak himself");
                    c.npc("those who try and fight it...");
                    c.npc("iban locks in cages, until their minds are too weak to resist");
                    c.npc("eventually they all fall to his control");
                } else {
                    c.npc("you think this is funny.. eh");
                    c.player("not really, just trying to lighten up the conversation");
                }
                c.npc("here take this, i don't need it");
                c.message("klank gives you a tinderbox");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        if (!holds(TINDERBOX)) {
                            give(TINDERBOX);
                        }
                        if (at(GATE_OPEN)) {
                            step(DWARVES);
                        }
                    }
                });
            }
        }.says(1, "no wonder they're breath was soo bad"));
        c.start();
    }

    private void klankSpare(final Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.player("hello klank");
        c.npc("hello again adventurer, so you're still around");
        c.player("still here!");
        c.options(new Choice("have you anymore gauntlets?", "take care klank") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("you too adventurer");
                    return;
                }
                c.npc("well..yes, but they're not cheap to make");
                c.npc("i'll have to sell you a pair");
                c.player("how much?");
                c.npc("5000 coins");
                c.options(new Choice("5000, you must be joking", "ok then, i'll take a pair") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("we don't joke down here, friend");
                            return;
                        }
                        if (c.getPlayer().getInventory().countId(10) < 5000) {
                            c.player("oh dear, i haven't enough money");
                            c.npc("sorry, i can't sell them any cheaper than that");
                            return;
                        }
                        c.message("you give klank 5000 coins...");
                        c.message("...and klank gives you a pair of guanletts");
                        c.npc("there you go..i hope they help");
                        c.player("i'll see you around klank");
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                Player pl = c.getPlayer();
                                pl.getInventory().remove(10, 5000);
                                pl.getInventory().add(new InvItem(GAUNTLETS, 1));
                                pl.getActionSender().sendInventory();
                            }
                        });
                    }
                });
            }
        });
        c.start();
    }

    // ------------------------------------------------- paladins and demons --

    private void paladin(final Npc npc, boolean bearded) {
        Player p = getOwner();
        if (marked(KNOCKED) && past(GATE_OPEN)) {
            // Once you have been through the gate the paladins take you for
            // one of Iban's own and stop talking.
            Conversation c = new Conversation(p, npc);
            c.player("hello");
            c.npc("you again, die zamorakian scum");
            c.then(new Effect() {
                public void run(Conversation c) {
                    c.stop();
                    npc.attackPlayer(c.getPlayer());
                }
            });
            c.start();
            return;
        }
        if (!bearded) {
            say("The Paladin does not appear interested in talking");
            return;
        }
        Conversation c = new Conversation(p, npc);
        c.player("hello paladin");
        boolean fed = marked(PAL_FED);
        if (past(UNICORN)) {
            if (!fed) {
                c.npc("you've done well to get this far traveller, here eat");
                c.message("the paladin gives you some food");
                c.player("thanks");
            }
            c.npc("you should leave this place now traveller");
            c.npc("i heard the crashing of rocks further down the cavern");
            c.npc("iban must be restless");
            c.npc("i have no doubt that zamorak still controls these caverns");
        } else {
            c.npc("traveller, what are you doing in this most unholy place?");
            c.player("i'm looking for safe route through the caverns");
            c.player("under order of king lathas");
            if (!fed) {
                c.npc("you've done well to come this far, here eat");
                c.message("the paladin gives you some food");
            }
            c.npc("There's no doubt Iban still controls these caverns..");
            c.npc("we've also been looking for a passage through..");
        }
        c.npc("a little further on lies the great door of iban..");
        c.npc("we've tried everything, but it will not let us enter..");
        c.npc("leave now before iban awakes and it's too late");
        if (!fed) {
            c.then(new Effect() {
                public void run(Conversation c) {
                    if (marked(PAL_FED)) {
                        return;
                    }
                    Player pl = c.getPlayer();
                    /* 259 meat pie, 346 stew, 138 bread, 474 attack potion,
                       483 restore prayer potion -- the recorded ration. */
                    pl.getInventory().add(new InvItem(259, 2));
                    pl.getInventory().add(new InvItem(346, 1));
                    pl.getInventory().add(new InvItem(138, 2));
                    pl.getInventory().add(new InvItem(474, 1));
                    pl.getInventory().add(new InvItem(483, 1));
                    pl.getActionSender().sendInventory();
                    mark(PAL_FED);
                }
            });
        }
        c.start();
    }

    private void disciple(final Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        c.player("hi");
        if (wearing(702) && wearing(703)) {
            c.npc("hail the great one, my lord iban");
            c.npc("i die for you again and again");
            c.player("is that possible?");
            c.npc("under iban anything is possible");
            c.npc("death is only the beginning");
            c.start();
            return;
        }
        c.npc("an imposter....die scum");
        c.then(new Effect() {
            public void run(Conversation c) {
                c.stop();
                npc.attackPlayer(c.getPlayer());
            }
        });
        c.start();
    }

    /**
     * The coats of arms and the demon amulets are the only things in Classic
     * that go straight into the inventory on a kill rather than dropping.
     */
    private void killed(int id) {
        switch (id) {
            case PALADIN_BEARD:
                say("the paladin slumps to the floor");
                say("you search his body");
                if (holds(COAT_RED)) {
                    say("but find nothing");
                    return;
                }
                say("and find a paladin coat of arms");
                give(COAT_RED);
                return;
            case PALADIN:
                say("the paladin slumps to the floor");
                say("you search his body");
                if (getOwner().getInventory().countId(COAT_BLUE) >= 2) {
                    say("but find nothing");
                    return;
                }
                say("and find a paladin coat of arms");
                give(COAT_BLUE);
                return;
            case OTHAINIAN: demonAmulet(AMULET_OTH); return;
            case DOOMION: demonAmulet(AMULET_DOO); return;
            case HOLTHION: demonAmulet(AMULET_HOL); return;
            case KALRAG: kalrag(); return;
            case DISCIPLE:
                // The only source of the zamorak robes the temple door asks
                // for: the disciples wear them, so their bodies give them up.
                // Once Iban is dead they matter for one thing only: a player
                // who lost the staff can take a broken one off their remains
                // and carry it to the dark mage.
                if (past(IBAN_DEAD)) {
                    say("you search the diciples remains");
                    if (holds(STAFF) || wearing(STAFF) || holds(BROKEN_STAFF)) {
                        say("but find nothing");
                        return;
                    }
                    say("and find a staff of iban");
                    give(BROKEN_STAFF);
                    return;
                }
                say("you search the disciples body");
                if (holds(702) && holds(703)) {
                    say("but find nothing you need");
                    return;
                }
                say("and strip it of the robes of zamorak");
                if (!holds(702)) {
                    give(702);
                }
                if (!holds(703)) {
                    give(703);
                }
                return;
            default: return;
        }
    }

    private void demonAmulet(int amulet) {
        if (!past(HAVE_DOLL)) {
            // Before Niloof has explained the doll the demons are just demons
            // and leave nothing but ashes.
            return;
        }
        if (holds(amulet)) {
            return;
        }
        say("you search the demons remains");
        say("and find a strange amulet");
        give(amulet);
    }

    private void kalrag() {
        if (!collecting() || elem(E_BLOOD)) {
            return;
        }
        if (!holds(DOLL)) {
            say("the spiders blood soaks into the ground");
            say("if only you had the doll with you");
            return;
        }
        say("you smear the doll with the spiders poisoned blood");
        addElem(E_BLOOD);
    }

    // ------------------------------------------------- the pass, level one --

    private void caveEntrance(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("It doesn't look very inviting");
            return;
        }
        if (!questStarted()) {
            say("You must first complete the biohazard quest...");
            say("...before you can enter");
            return;
        }
        say("you cautiously enter the cave");
        getOwner().teleport(PASS_X, PASS_Y, false);
        if (at(KOFTIK) || at(STARTED)) {
            step(ENTERED);
        }
    }

    private void campFire(QuestTrigger trigger, InvItem used) {
        if (trigger != QuestTrigger.ITEM_ON_OBJECT) {
            say("A fire someone left burning");
            return;
        }
        if (used == null || used.getID() != ARROW) {
            say("Nothing interesting happens.");
            return;
        }
        say("you light the cloth on the arrow");
        take(ARROW);
        give(LIT_ARROW);
    }

    private void oldBridge(QuestTrigger trigger, InvItem used) {
        /* The world ships the bridge already fallen (727, the id with the
           "cross" command), because the burnt state was a world-object swap
           and every restart raised the bridge again, stranding anyone whose
           BRIDGE_DOWN stage said the lit arrow was already spent. The quest
           gates it instead: crossing is refused until the arrow has been
           fired at it. */
        if (past(BRIDGE_DOWN) && trigger != QuestTrigger.ITEM_ON_OBJECT) {
            crossBridge(trigger);
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != LIT_ARROW) {
                say("Nothing interesting happens.");
                return;
            }
            if (!canRange()) {
                return;
            }
            say("you fire the lit arrow at the bridge");
            say("the old rope catches light");
            say("and the bridge collapses into the chasm below");
            say("the remains form a walkway across");
            take(LIT_ARROW);
            swap(BRIDGE, BRIDGE_CROSSED, BRIDGE_X, BRIDGE_Y);
            if (at(CLOTH_GIVEN) || at(ENTERED)) {
                step(BRIDGE_DOWN);
            }
            getOwner().teleport(OVER_BRIDGE_X, OVER_BRIDGE_Y, false);
            return;
        }
        say("That's been there a while");
        say("the bridge is far too rotten to take your weight");
        say("the ropes holding it look dry as tinder");
    }

    private void crossBridge(QuestTrigger trigger) {
        Player p = getOwner();
        say("you pick your way across the fallen bridge");
        // East is the same tile the first crossing uses: (709,3419), the
        // open ground by the lever.  The bridge row itself east of the rock
        // at (707,3417) is a walled pocket with no way out on foot.
        if (p.getX() <= BRIDGE_X - 1) {
            p.teleport(OVER_BRIDGE_X, OVER_BRIDGE_Y, false);
        } else {
            p.teleport(BRIDGE_X - 3, BRIDGE_Y, false);
        }
    }

    /**
     * The four orbs of light. Each one goes into the furnace once; the fourth
     * lights the way on and lets the well be entered.
     */
    private void furnace(QuestTrigger trigger, InvItem used) {
        if (trigger != QuestTrigger.ITEM_ON_OBJECT) {
            say("Charred bones are slowly burning inside");
            return;
        }
        if (used == null) {
            return;
        }
        int bit;
        switch (used.getID()) {
            case ORB_A: bit = ORB1; break;
            case ORB_B: bit = ORB2; break;
            case ORB_C: bit = ORB3; break;
            case ORB_D: bit = ORB4; break;
            default:
                say("Nothing interesting happens.");
                return;
        }
        if (marked(bit)) {
            say("you have already given that orb to the furnace");
            return;
        }
        say("you throw the glowing orb into the furnace");
        say("its light quickly dims and then dies");
        say("you feel a cold shudder run down your spine");
        take(used.getID());
        mark(bit);
        int done = countBits(getStage() & ORBS_ALL);
        if (done < 4) {
            say("you sense that " + (4 - done) + " more are needed");
            return;
        }
        say("the furnace roars and the whole cavern shudders");
        say("the last trace of goodness in this place is gone");
        if (at(BRIDGE_DOWN)) {
            step(ORBS_DONE);
        }
    }

    /** The old supply crate abandoned by the well. One meal, once. */
    private void crate(QuestTrigger trigger) {
        say("you search the crate");
        if (marked(CRATE_DONE)) {
            say("but you find nothing");
            return;
        }
        say("inside you find some food");
        Player p = getOwner();
        p.getInventory().add(new InvItem(357, 2));
        p.getInventory().add(new InvItem(259, 2));
        p.getActionSender().sendInventory();
        mark(CRATE_DONE);
    }

    /**
     * The high wall grill east of the paladin platforms (836). Too high to
     * reach until a rope has been tied to it; the rope stays on the grill,
     * so one tie serves every later crossing. Its twin 838 hangs low enough
     * to climb bare-handed and stays in the agility table.
     */
    private void wallGrill(QuestTrigger trigger, InvItem used) {
        Player p = getOwner();
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != ROPE) {
                say("Nothing interesting happens.");
                return;
            }
            if (marked(GRILL_ROPED)) {
                say("a rope is already tied to the grill");
                return;
            }
            say("you tie the rope to the grill..");
            say("..and poke it through to the otherside");
            take(ROPE);
            mark(GRILL_ROPED);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("A metal grill set high in the wall");
            return;
        }
        if (!marked(GRILL_ROPED)) {
            say("the wall grill is too high");
            say("you can't quite reach");
            return;
        }
        say("you use the rope tied to the grill to pull yourself up");
        say("you then climb across the grill to the otherside");
        p.teleport(GRILL_E_X, p.getY() < GRILL_E_Y ? 3472 : GRILL_E_Y - 1, false);
    }

    private void theWell(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("The remains of a warrior slump over the straps");
            return;
        }
        if (!past(ORBS_DONE)) {
            // The well guards itself while the orbs still burn: their light
            // is the "positive force" and it throws the player back out.
            say("you climb into the well");
            say("from below an icy blast of air chills you to your bones");
            say("a mystical force seems to blast you back out of the well");
            say("there must be a positive force near by!");
            hurt(Math.max(1, getOwner().getCurStat(HITS) / 5));
            return;
        }
        say("you climb into the well");
        say("you feel the grip of icy hands all around you...");
        say("..slowly dragging you further down into the caverns");
        getOwner().teleport(WELL_DOWN_X, WELL_DOWN_Y, false);
        if (at(ORBS_DONE)) {
            step(DOWN_WELL);
        }
    }

    private void stalagmite(QuestTrigger trigger, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != ROPE) {
                say("Nothing interesting happens.");
                return;
            }
            say("you tie one end of the rope to the stalagmite");
            say("and the other around your waist");
            take(ROPE);
            mark(ROPED);
            return;
        }
        say("you search the stalagmite");
        if (!marked(ROPED)) {
            say("but find nothing");
            return;
        }
        say("you untie your rope and place it in your satchel");
        give(ROPE);
        setStage(getStage() & ~ROPED);
    }

    /**
     * One pull is the whole passage: the railing lifts, the player is walked
     * through to the pocket between the cage and the rocks, and everything
     * settles shut again behind them. The raised cage is only a moment of
     * scenery -- it never stays open, so a restart has nothing to forget.
     */
    private void cageLever(QuestTrigger trigger) {
        say("you pull on the lever");
        say("you hear a loud mechanical churning");
        say("as the huge railing raises to the cave roof");
        say("the cage lowers behind you");
        GameObject shut = world.getTile(CAGE_X, CAGE_Y).getGameObject();
        swap(CAGE_SHUT, CAGE_OPEN, CAGE_X, CAGE_Y);
        getOwner().teleport(690, 3451, false);
        if (shut != null && shut.getID() == CAGE_SHUT) {
            world.delayedSpawnObject(shut.getLoc(), 2000);
        }
    }

    private void dugSoil(QuestTrigger trigger) {
        // Objects 839 and 840 carry "search" as their first command, so both
        // act triggers mean the same thing here. They are the two mouths of
        // one tunnel under the paladin platform wall; which end the player
        // is at decides where the crawl comes out.
        say("you search through the loose soil");
        say("and find a tunnel leading away under the wall");
        say("it looks just wide enough to crawl into");
        say("you climb into the small tunnel");
        say("and crawl into a small dark passage");
        if (getOwner().getY() < 3465) {
            getOwner().teleport(747, 3470, false);
        } else {
            getOwner().teleport(744, 3458, false);
        }
    }

    // ------------------------------------------------------- the unicorn ---

    private void boulder(QuestTrigger trigger, InvItem used) {
        if (trigger != QuestTrigger.ITEM_ON_OBJECT) {
            say("Could be dangerous!");
            return;
        }
        if (used == null || used.getID() != RAILING) {
            say("Nothing interesting happens.");
            return;
        }
        if (past(UNICORN)) {
            say("the boulder has already gone over the edge");
            return;
        }
        say("you use the pole as leverage...");
        say("..and tip the bolder onto its side");
        say("it tumbles down the slope");
        take(RAILING);
        // The map already draws the aftermath -- the cage remains lie crushed
        // down the slope. Only the boulder itself moves: gone for a few
        // seconds, then back for the next player.
        if (world.getTile(BOULDER_X, BOULDER_Y).hasGameObject()) {
            final GameObject rock = world.getTile(BOULDER_X, BOULDER_Y).getGameObject();
            if (rock.getID() == BOULDER) {
                world.unregisterGameObject(rock);
                world.getDelayedEventHandler().add(new SingleEvent(null, 10000) {
                    public void action() {
                        world.registerGameObject(new GameObject(rock.getLocation(),
                            BOULDER, rock.getDirection(), rock.getType()));
                    }
                });
            }
        }
        if (at(DOWN_WELL)) {
            step(UNICORN);
        }
    }

    private void ledge(QuestTrigger trigger, GameObject o) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        boolean rolled = past(UNICORN);
        int x, y;
        switch (o.getID()) {
            /* Ways out of either room copy land in the shared areas... */
            case LEDGE_A: x = 731; y = 3494; break;                 // the pass
            case LEDGE_B: x = 763; y = 3442; break;                 // high area
            case LEDGE_D: x = 728; y = 3499; break;                 // boulder area
            /* ...and ways in pick the copy the quest says is there. */
            case LEDGE_C: x = rolled ? 751 : 734; y = 3496; break;  // from the pass
            case LEDGE_F: x = rolled ? 765 : 748; y = 3497; break;  // from high area
            case LEDGE_E: x = rolled ? 755 : 738; y = 3501; break;  // from boulder area
            default: return;
        }
        say("you climb the ledge..");
        getOwner().teleport(x, y, false);
        say("you drop down to the cave floor");
    }

    private void cageRailings(QuestTrigger trigger) {
        if (trigger == QuestTrigger.DOOR_ACT1) {
            // pick lock
            say("you attempt to pick the lock");
            say("the cage door has been sealed shut");
            say("the poor unicorn has no way to escape");
            return;
        }
        // search
        say("you search the cage");
        if (past(UNICORN)) {
            say("but you find nothing");
            return;
        }
        if (holds(RAILING)) {
            say("but you find nothing else of interest");
            return;
        }
        say("you find a loose railing lying on the floor");
        give(RAILING);
    }

    /**
     * The way into Kalrag's nest: search the bars, find the gap, squeeze
     * through. Open-RSC gates the squeeze on being far enough in to need the
     * spider's blood (or not having started at all); before that the gap is
     * "too tight".
     */
    private void spiderRailings(final GameObject rails) {
        final Player p = getOwner();
        Conversation c = new Conversation(p, null);
        c.message("you search the bars");
        if (!questStarted() || past(DOWN_WELL) || holds(DOLL)) {
            c.message("there's a gap big enough to squeeze through");
            c.message("would you like to try");
            c.picker(new Choice("nope", "yes, lets do it") {
                public void picked(int option, Conversation c) {
                    if (option != 1) {
                        return;
                    }
                    c.message("you squeeze through the old railings");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            Player pl = c.getPlayer();
                            int y = pl.getY() == SPIDER_RAILS_Y
                                ? SPIDER_RAILS_Y - 1 : SPIDER_RAILS_Y;
                            pl.teleport(rails.getX(), y, false);
                        }
                    });
                }
            });
        } else {
            c.message("but you can't quite squeeze through");
        }
        c.start();
    }

    private void unicornCage(QuestTrigger trigger) {
        // Object 871 carries "Search" as its first command.
        say("you search the cage");
        if (holds(HORN)) {
            say("but you find nothing");
            return;
        }
        if (!past(UNICORN)) {
            say("you find a loose railing lying on the floor");
            if (!holds(RAILING)) {
                give(RAILING);
            }
            return;
        }
        say("amongst the wreckage you find the unicorns horn");
        give(HORN);
    }

    // -------------------------------------------- the flames and the gate --

    private void flames(QuestTrigger trigger, InvItem used) {
        if (trigger != QuestTrigger.ITEM_ON_OBJECT) {
            say("a great cauldron of black fire");
            if (trigger == QuestTrigger.OBJECT_ACT1) {
                int coats = countBits(getStage() & COATS_ALL);
                say("the flames call for the symbols of three paladins");
                say("and for the horn of a creature of pure good");
                say("so far you have given " + coats + " of the three");
                if (marked(HORN_IN)) {
                    say("and the horn");
                }
            }
            return;
        }
        if (used == null) {
            return;
        }
        int id = used.getID();
        if (id == COAT_RED || id == COAT_BLUE) {
            /* The flames are particular: the crest of the bearded captain
               (red, COAT1) and one from each of his two men (blue, COAT2 and
               COAT3). Saves from before this distinction may have the bits
               filled generically; they are honoured as they stand. */
            int bit;
            if (id == COAT_RED) {
                bit = !marked(COAT1) ? COAT1 : 0;
            } else {
                bit = !marked(COAT2) ? COAT2 : (!marked(COAT3) ? COAT3 : 0);
            }
            if (bit == 0) {
                say("the flames have already taken that crest");
                return;
            }
            say("you throw the coat of arms into the flames");
            say("the fire spits and hisses");
            take(id);
            mark(bit);
            checkGate();
            return;
        }
        if (id == HORN) {
            if (marked(HORN_IN)) {
                say("the flames have taken the horn already");
                return;
            }
            say("you throw the unicorns horn into the flames");
            say("a plume of white smoke rises from the cauldron");
            take(HORN);
            mark(HORN_IN);
            checkGate();
            return;
        }
        if (id == STAFF) {
            // The staff's 25-cast tank refills here and nowhere else.
            say("you hold the staff above the well");
            say("and feel the power of zamorak flow through you");
            note("staff-recharge");
            return;
        }
        say("Nothing interesting happens.");
    }

    // ------------------------------------------------------- staff charges --

    /** Persisted casts-remaining for the Staff of Iban. */
    private static final int VAR_STAFF_CASTS = 0;
    private static final int STAFF_CAST_CAP = 25;

    /**
     * SpellHandler's window into the staff. "staff-charged" answers whether a
     * cast may go ahead; "staff-cast" burns one charge; "staff-recharge"
     * refills the tank (the flames above do it through the same key). A staff
     * whose owner has never touched the counter is treated as fully charged,
     * so the quest reward works out of the box.
     */
    public boolean reached(String key) {
        if ("staff-charged".equals(key)) {
            return getVar(VAR_STAFF_CASTS, STAFF_CAST_CAP) > 0;
        }
        return false;
    }

    public void note(String key) {
        if ("staff-cast".equals(key)) {
            int left = getVar(VAR_STAFF_CASTS, STAFF_CAST_CAP);
            if (left > 0) {
                setVar(VAR_STAFF_CASTS, left - 1);
            }
        } else if ("staff-recharge".equals(key)) {
            setVar(VAR_STAFF_CASTS, STAFF_CAST_CAP);
        }
    }

    private void checkGate() {
        if (countBits(getStage() & COATS_ALL) < 3 || !marked(HORN_IN)) {
            return;
        }
        say("the flames die away to nothing");
        say("and from the west you hear a great lock turn");
    }

    /**
     * The gateway through the Ardougne quarantine wall.
     *
     * King Tyras orders it opened when this quest starts, and it is the only
     * way west that does not involve Edmond's sewer or Omart's rope. Before
     * the quest it does not budge. It is the mourners' gate, so one of them
     * waves the player through; the two standing beside it are npcs 451 and
     * 491, and whichever is nearest speaks.
     */
    private void wallGateway(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("A huge set of heavy wooden doors");
            return;
        }
        say("you pull on the large wooden doors");
        if (!questStarted()) {
            say("but it will not open");
            return;
        }
        say("you open it and walk through");
        p.teleport(p.getX() >= WALL_GATE_W_X ? WALL_GATE_E_X : WALL_GATE_W_X,
                   WALL_GATE_Y, false);
        Npc mourner = world.getNpc(451, 618, 628, 584, 594);
        if (mourner == null) {
            mourner = world.getNpc(491, 618, 628, 584, 594);
        }
        if (mourner != null) {
            p.informOfNpcMessage(new ChatMessage(mourner, "go through", p));
        }
    }

    private void gateOfIban(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("It doesn't look very inviting");
            return;
        }
        say("you pull on the great door");
        if (countBits(getStage() & COATS_ALL) < 3 || !marked(HORN_IN)) {
            say("the door refuses to open");
            return;
        }
        say("from behind the door you hear cry's and moans");
        say("the door slowly creeks open");
        say("you walk into the darkness");
        getOwner().teleport(PAST_GATE_X, PAST_GATE_Y, false);
        if (at(UNICORN)) {
            step(GATE_OPEN);
        }
    }

    // ----------------------------------------------- Kardia and the doll ---

    private void witchDoor(QuestTrigger trigger, InvItem used) {
        Player p = getOwner();
        if (trigger == QuestTrigger.ITEM_ON_DOOR) {
            if (used == null || used.getID() != CAT) {
                say("Nothing interesting happens.");
                return;
            }
            if (marked(CAT_TAKEN)) {
                say("the witch already has her cat");
                return;
            }
            say("you place the cat by the door");
            say("you knock on the door and hide around the corner");
            say("the witch takes the cat inside");
            take(CAT);
            mark(CAT_TAKEN);
            return;
        }
        if (trigger == QuestTrigger.DOOR_ACT2) {
            say("you knock on the door");
            if (!marked(CAT_TAKEN)) {
                say("there is no reply");
                return;
            }
            say("there is no reply");
            say("inside you can hear the witch talking to the cat");
            return;
        }
        if (!marked(CAT_TAKEN)) {
            kardiaBlast();
            return;
        }
        say("you open the door");
        say("and walk through");
        say("the witch is busy talking to the cat");
        p.teleport(WITCH_DOOR_X,
            p.getY() >= WITCH_DOOR_Y ? WITCH_DOOR_Y - 1 : WITCH_DOOR_Y, false);
    }

    private void kardiaBlast() {
        Player p = getOwner();
        say("@yel@Kardia the Witch: get away...far away from here");
        say("the witch raises her hands above her");
        say("@yel@Kardia the Witch: haa haa.. die mortal");
        hurt(8 + (int) (Math.random() * 8));
    }

    private void dollChest(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1 && trigger != QuestTrigger.OBJECT_ACT2) {
            return;
        }
        say("you search the chest");
        if (past(HAVE_DOLL)) {
            say("but there is nothing left inside");
            return;
        }
        say("inside you find a strange rag doll");
        say("and an old journal");
        say("...and two potions");
        give(DOLL);
        give(OLD_JOURNAL);
        give(486);
        give(477);
        if (at(DWARVES)) {
            step(HAVE_DOLL);
        }
    }

    private void shadowChest(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1 && trigger != QuestTrigger.OBJECT_ACT2) {
            return;
        }
        if (holds(SHADOW) || elem(E_SHADOW)) {
            say("the chest is empty");
            return;
        }
        if (!holds(AMULET_OTH) || !holds(AMULET_DOO) || !holds(AMULET_HOL)) {
            say("the chest is locked by three strange keyholes");
            say("each one is shaped like an amulet");
            return;
        }
        say("you place the three amulets into the keyholes");
        say("the lid of the chest swings open");
        say("inside is a phial of dark mystical liquid");
        take(AMULET_OTH);
        take(AMULET_DOO);
        take(AMULET_HOL);
        give(SHADOW);
    }

    private void soulessCage(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1 && trigger != QuestTrigger.OBJECT_ACT2) {
            say("Seems to be mechanical");
            return;
        }
        if (!wearing(GAUNTLETS)) {
            say("you reach into the cage");
            say("the souless sinks its teeth into your hands");
            say("you pull back sharply");
            hurt(3 + (int) (Math.random() * 5));
            return;
        }
        say("you reach into the cage");
        if (holds(CONSCIENCE) || elem(E_CONS)) {
            say("but find nothing");
            return;
        }
        // Only one cage in the row hides the dove, and which one is not
        // recorded anywhere, so the search is a roll as it is in vanilla.
        if (Math.random() < 0.25D) {
            say("amongst the filth you find the remains of a dove");
            give(CONSCIENCE);
            return;
        }
        say("but find nothing");
    }

    // ------------------------------------------- the tomb and the brew -----

    private void brewBarrel(QuestTrigger trigger, InvItem used) {
        boolean withBucket = trigger == QuestTrigger.ITEM_ON_OBJECT
            && used != null && used.getID() == BUCKET;
        if (trigger != QuestTrigger.OBJECT_ACT1 && !withBucket) {
            say("Its stinks of alcohol");
            return;
        }
        if (!withBucket && !holds(BUCKET)) {
            say("you need a bucket first");
            return;
        }
        say("you poor some of the strong brew into your bucket");
        take(BUCKET);
        give(BREW);
    }

    private void tombOfIban(QuestTrigger trigger, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null) {
                return;
            }
            if (used.getID() == BREW) {
                say("you pour the strong alcohol over the tomb");
                take(BREW);
                mark(SOAKED);
                return;
            }
            if (used.getID() == TINDERBOX) {
                say("you try to set alight to the tomb");
                if (!marked(SOAKED)) {
                    say("but it will not light");
                    return;
                }
                say("it bursts into flames");
                say("you search through the remains");
                if (holds(ASHES) || elem(E_ASHES)) {
                    say("but the ashes are long gone");
                    return;
                }
                say("and find the ashes of ibans corpse");
                give(ASHES);
                setStage(getStage() & ~SOAKED);
                return;
            }
            say("Nothing interesting happens.");
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("A clay shrine to lord iban");
            return;
        }
        say("you try to open the door of the tomb");
        say("but the door refuses to open");
        say("you hear a noise from below");
        say("@yel@leave me be");
        sayMessage("aaarrgghhh");
        hurt(6 + (int) (Math.random() * 6));
    }

    // ------------------------------------------------- Iban and the pit ----

    /**
     * The doors on the temple's west face. Opening from outside runs the
     * temple sequence; from inside they just let you back out. The open frame
     * (914) stands in for a few seconds either way.
     */
    private void templeDoor(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("Scary!");
            return;
        }
        if (world.getTile(TEMPLE_DOOR_X, TEMPLE_DOOR_Y).hasGameObject()) {
            final GameObject door = world.getTile(TEMPLE_DOOR_X, TEMPLE_DOOR_Y).getGameObject();
            if (door.getID() == TEMPLE_DOOR) {
                world.unregisterGameObject(door);
                world.registerGameObject(new GameObject(door.getLocation(),
                    TEMPLE_DOOR_OPEN, door.getDirection(), door.getType()));
                world.getDelayedEventHandler().add(new SingleEvent(null, 3000) {
                    public void action() {
                        if (world.getTile(TEMPLE_DOOR_X, TEMPLE_DOOR_Y).hasGameObject()) {
                            GameObject open = world.getTile(TEMPLE_DOOR_X, TEMPLE_DOOR_Y).getGameObject();
                            if (open.getID() == TEMPLE_DOOR_OPEN) {
                                world.unregisterGameObject(open);
                            }
                        }
                        world.registerGameObject(new GameObject(door.getLocation(),
                            TEMPLE_DOOR, door.getDirection(), door.getType()));
                    }
                });
            }
        }
        if (p.getX() > TEMPLE_DOOR_X - 1) {
            // Inside, leaving.
            say("you pull open the large doors");
            say("and walk out of the temple");
            p.teleport(TEMPLE_DOOR_X - 1, TEMPLE_DOOR_Y, false);
            return;
        }
        zamorakianTemple(QuestTrigger.OBJECT_ACT1);
    }

    private void zamorakianTemple(QuestTrigger trigger) {
        Player p = getOwner();
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            say("Scary!");
            return;
        }
        if (past(IBAN_DEAD)) {
            say("the temple is a heap of broken stone");
            return;
        }
        if (!wearing(702) || !wearing(703)) {
            say("you pull open the large doors");
            say("and walk into the temple");
            say("Iban seems to sense danger");
            say("@yel@Iban: who dares bring the witches magic into my temple");
            say("his eyes fixate on you as he raises his arm");
            say("@yel@Iban: an imposter dares desecrate this sacred place..");
            say("@yel@Iban: ..home to the only true child of zamorak");
            say("@yel@Iban: join the damned, mortal");
            say("iban raises his staff to the air");
            say("a blast of energy comes from ibans staff");
            say("you are hit by ibans magic bolt");
            sayMessage("aarrgh");
            say("@yel@Iban: die foolish mortal");
            say("you're blasted back to the door");
            hurt(10 + (int) (Math.random() * 10));
            return;
        }
        say("you pull open the large doors");
        say("and walk into the temple");
        p.teleport(TEMPLE_IN_X, TEMPLE_IN_Y, false);
        if (at(DOLL_DONE) && holds(DOLL)) {
            // Iban feels the witch's magic come through the door. One bolt
            // meets the player on the threshold; the pit is a few steps away
            // and the doll had better go into it quickly.
            say("Iban seems to sense danger");
            say("@yel@Iban: who dares bring the witches magic into my temple");
            say("his eyes fixate on you as he raises his arm");
            say("a blast of energy comes from ibans staff");
            say("you are hit by ibans magic bolt");
            sayMessage("aarrgh");
            hurt(Math.max(1, p.getCurStat(HITS) / 10 + 4));
        }
    }

    private void pitOfTheDamned(QuestTrigger trigger, InvItem used) {
        if (trigger != QuestTrigger.ITEM_ON_OBJECT) {
            say("a black pit, and something moves far down in it");
            return;
        }
        if (used == null || used.getID() != DOLL) {
            say("Nothing interesting happens.");
            return;
        }
        if (!at(DOLL_DONE)) {
            say("you hold the doll over the pit");
            say("but nothing happens, the doll is not yet complete");
            return;
        }
        destroyIban();
    }

    private void destroyIban() {
        Player p = getOwner();
        say("you throw the doll of iban into the pit");
        say("@yel@Iban: what's happening?, it's dark here... so dark");
        say("@yel@Iban: im falling into the dark, what have you done?");
        say("ibans falls to his knees clutching his throat");
        say("@yel@Iban: noooooooo!");
        say("iban slumps motionless to the floor");
        say("a roar comes from the pit of the damned");
        say("the infamous iban has finally gone to rest");
        take(DOLL);
        say("amongst ibans remains you find his staff..");
        say("...and some runes");
        // The staff and the runes come off Iban's body, which is where the
        // text says they come from; completeQuest awards only the experience.
        p.getInventory().add(new InvItem(STAFF, 1));
        p.getInventory().add(new InvItem(DEATH_RUNE, 15));
        p.getInventory().add(new InvItem(FIRE_RUNE, 30));
        p.getActionSender().sendInventory();
        say("suddently around you rocks crash to the floor..");
        say("..as the ground begins to shake");
        say("the temple walls begin to collapse in");
        say("and you're thrown from the temple platform");
        step(IBAN_DEAD);
        p.teleport(AFTER_X, AFTER_Y, false);
    }

    // -------------------------------------------------------- item actions --

    private void command(int id) {
        switch (id) {
            case JOURNAL: randasJournal(); return;
            case OLD_JOURNAL: oldJournal(); return;
            case DOLL: searchDoll(); return;
            case BROKEN_STAFF: wieldBrokenStaff(); return;
            default: return;
        }
    }

    /**
     * Wielding the broken Staff of Iban before the Dark Mage has repaired it.
     * Had no case here at all, so the command was completely silent.
     */
    private void wieldBrokenStaff() {
        say("the staff is broken");
        say("you must have a dark mage repair it");
        say("before it can be used");
    }

    private void randasJournal() {
        say("@red@It began as a whisper in my ears. Dismissing the sounds...");
        say("@red@..as the whistling of the wind, I steeled myself against...");
        say("@red@..these forces and continued on my way");
        say("@red@But the whispers became moans...");
        say("@red@Join us! The voices cried, Join us!");
        say("@red@Your greatness lies within you, but only Zamorak can unlock your potential..");
        say("the last pages describe four orbs of light");
        say("and a furnace that will take them");
    }

    /**
     * Kardia's journal, page for page as the recovered footage and the wiki's
     * period screenshots show it: a chapter menu, then full-screen windows of
     * red text. Closing a window is client-side and never reaches the server,
     * so multi-page chapters turn their own pages on a timer.
     */
    private static final String[] JOURNAL_INTRO = {
        "@red@Gather round, all ye followers of the dark arts.%"
            + "@red@Read carefully the words that I hereby inscribe,%"
            + "@red@as I detail the heady brew that is responsible for my%"
            + "@red@greatest creation yet. I am Kardia, the most wretched%"
            + "@red@witch in all the land,scorned by beauty and the world.%"
            + "@red@See what I have created: the most powerful force%"
            + "@red@of darkness ever to be seen in human form!" };

    private static final String[] JOURNAL_IBAN = {
        "@red@Iban was a Black Knight who had learned to fight under the "
            + "great Darkquerius himself. Together they had taken on the "
            + "might of the White Knights, and the blood of a hundred "
            + "soldiers had been wiped from Iban's sword.% %"
            + "@red@In many respects Iban was not so different from the White "
            + "Knights that he so mercilessly slaughtered: noble and "
            + "educated with a taste for the finer things in life. But there "
            + "was something that made him different: ambition. No, not "
            + "the simple desire to succeed or lead one's fellow man. "
            + "This was an ambition that hungered for something beyond "
            + "the mortal realm",
        "@red@..that was almost godlike in its insatiability.% %"
            + "@red@But therein lay the essence of his darkness. At its most "
            + "base level, Iban's fundamental impulse was a desire to "
            + "control the hearts and minds of his fellow man. To take "
            + "them beyond the pale of mere allegiance, and corrupt "
            + "them into a pure force for evil.% %"
            + "@red@This was the fantasy that chased him in his dreams. A "
            + "whole legion of soul-less beings, their minds demented "
            + "from the sheer power that he had channelled through to "
            + "them.%"
            + "@red@But dreams was all they ever were. As a mere mortal- "
            + "heroic though he was- this was an ambition that Iban was "
            + "unable to achieve. Meeting his demise in the White "
            + "Knights' now famous Dawn Ascent, Iban died with the "
            + "bitter taste of failure in his mouth. Little did he know that "
            + "his death was only the beginning." };

    private static final String[] JOURNAL_RESSURECTION = {
        "@red@I knew of Iban's life, though of course we had never met.%"
            + "@red@And using the power of my dark arts, I vowed to%"
            + "@red@resurrect this once great warrior. I would raise him again,%"
            + "@red@to fulfill the promise of his human life: to be a%"
            + "@red@Master of the Undead." };

    private static final String[] JOURNAL_ELEMENTS = {
        "@red@Ibans Flesh%"
            + "@red@Taking a small doll to represent Iban, I smeared my effigy%"
            + "@red@with the four crucial elements that constitute a life.%"
            + "@red@Rooting around the desolate battlefield, I had been able to%"
            + "@red@steal a piece of Iban's cold flesh.%"
            + "@red@Now clasping some in my own hand, I smeared it over%"
            + "@red@my miniature idol, all the while chanting Iban's name.",
        "@red@Ibans Blood%"
            + "@red@I also needed some blood. By now, Iban's body was just a%"
            + "@red@hardened vessel-his life blood had literally drained from%"
            + "@red@him. But these caverns are home to the giant spider,%"
            + "@red@a venomous creature that is known to feed on human%"
            + "@red@blood.  Killing one of these spiders, I wiped my carved doll%"
            + "@red@in its blood.",
        "@red@Ibans Shadow% %"
            + "@red@Then came the hard part: recreating the parts of a man "
            + "that cannot be seen or touched: those intangible things "
            + "that are life itself. Using all the mystical force that I could "
            + "muster, I performed the ancient ritual of Incantia, a spell "
            + "so powerful that it nearly stole the life from my frail and "
            + "withered body. Opening my eyes again, I saw the three "
            + "demons that had been summoned. Standing in a triangle, "
            + "their energy was focused on the doll. These demons "
            + "would be the keepers of Iban's shadow. Black as night, "
            + "their shared spirit would follow his undead body like an "
            + "angel of death.",
        "@red@Ibans conscience% %"
            + "@red@Finally, I had to construct that most unique thing, the one "
            + "element which seperates man from every other beast- his "
            + "conscience. A zombie does not need a mind: his is a "
            + "mindless destruction, borne of simple bloodlust. But for all "
            + "of Iban's life, he himself choose to take the evil path- "
            + "driven by such a monstrous ambition. This is what gave "
            + "him such potential- potential that I would now harness to "
            + "the fullest.",
        "@red@Locked inside a old wooden cage sat a beautiful white "
            + "dove. A symbol of freedom and hope, but oblivious to the "
            + "darkness of the world- just like a newborn child. Taking it "
            + "from the cage, I cradled the creature in my hands, "
            + "stroking its downy feathers. With Iban's resurrection "
            + "almost complete, I looked into the bird's innocent blue "
            + "eyes. Placing a gentle kiss upon its head, I then strangled "
            + "the bird- extinguishing its life between my pustulating "
            + "fingers. Truly this dead dove would now be Iban's "
            + "conscience: a mind that started with the innocence of "
            + "every other living creature, but chose to follow evil.",
        "@red@Crumbling some of the dove's bones onto the doll, I cast "
            + "my mind's eye onto Iban's body. My ritual was complete, "
            + "soon he would be coming to life. I, Kardia, had resurrected "
            + "the legendary Iban, the most powerful evil being ever to "
            + "take human form. And I alone knew that the same "
            + "process that I had used to create him, was also capable "
            + "of destroying him.% %"
            + "@red@But now I was exhausted. As I closed my eyes to sleep, I "
            + "was settled by a strange feeling of contentment "
            + "anticipation of the evil that Iban would soon unleash." };

    private void oldJournal() {
        Conversation c = new Conversation(getOwner(), null);
        c.message("the journal is old and covered in dust");
        c.message("inside are several chapters...");
        c.picker(new Choice("intro", "iban", "the ressurection", "the four elements") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0: journalPages(JOURNAL_INTRO); break;
                    case 1: journalPages(JOURNAL_IBAN); break;
                    case 2: journalPages(JOURNAL_RESSURECTION); break;
                    case 3: journalPages(JOURNAL_ELEMENTS); break;
                    default: break;
                }
            }
        });
        c.start();
    }

    private void journalPages(String[] pages) {
        getOwner().getActionSender().sendAlert(pages[0], true);
        for (int i = 1; i < pages.length; i++) {
            final String page = pages[i];
            world.getDelayedEventHandler().add(new SingleEvent(getOwner(), 15000 * i) {
                public void action() {
                    if (!this.owner.isRemoved()) {
                        this.owner.getActionSender().sendAlert(page, true);
                    }
                }
            });
        }
    }

    private void searchDoll() {
        say("you look over the doll");
        boolean any = false;
        if (elem(E_SHADOW)) {
            say("it has been soaked in a dark mystical liquid");
            any = true;
        }
        if (elem(E_ASHES)) {
            say("it has been rubbed with ashes");
            any = true;
        }
        if (elem(E_BLOOD)) {
            say("it is smeared with a spiders poisoned blood");
            any = true;
        }
        if (elem(E_CONS)) {
            say("and the bones of a dove are bound inside it");
            any = true;
        }
        if (past(DOLL_DONE)) {
            say("the doll is complete");
            return;
        }
        if (!any) {
            say("it is a plain thing of sticks and cloth");
        }
    }

    /**
     * The three elements that are added to the doll by hand. The blood is the
     * odd one out: Kalrag's death smears it on without being asked.
     */
    private void pair(int a, int b) {
        int other = a == DOLL ? b : (b == DOLL ? a : -1);
        if (other == SHADOW) {
            if (!collecting() || elem(E_SHADOW)) {
                say("Nothing interesting happens.");
                return;
            }
            say("you pour the dark liquid over the doll");
            say("it soaks in and is gone");
            take(SHADOW);
            addElem(E_SHADOW);
            return;
        }
        if (other == ASHES) {
            if (!collecting() || elem(E_ASHES)) {
                say("Nothing interesting happens.");
                return;
            }
            say("you rub the ashes into the cloth of the doll");
            take(ASHES);
            addElem(E_ASHES);
            return;
        }
        if (other == CONSCIENCE) {
            if (!collecting() || elem(E_CONS)) {
                say("Nothing interesting happens.");
                return;
            }
            say("you bind the remains of the dove inside the doll");
            take(CONSCIENCE);
            addElem(E_CONS);
            return;
        }
        if ((a == CLOTH && isArrow(b)) || (b == CLOTH && isArrow(a))) {
            wrapArrow(isArrow(a) ? a : b);
            return;
        }
        say("Nothing interesting happens.");
    }

    private boolean isArrow(int id) {
        for (int i = 0; i < ARROWS.length; i++) {
            if (ARROWS[i] == id) {
                return true;
            }
        }
        return false;
    }

    private void wrapArrow(int arrowId) {
        say("you wrap the damp cloth around the head of the arrow");
        take(CLOTH);
        getOwner().getInventory().remove(arrowId, 1);
        give(ARROW);
    }
}
