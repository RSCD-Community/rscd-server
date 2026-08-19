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

/**
 * Temple of Ikov. Released 17 June 2002, written by Paul Gower.
 *
 * Lucien wants the Staff of Armadyl out of the tunnels under the ruined temple
 * north east of Ardougne. The guardians who have kept it there for generations
 * would rather he did not have it, and the last thing the quest asks is which
 * of the two the player would rather disappoint.
 *
 *     Lucien           npc 360, (615,587), the Flying Horse Inn in Ardougne
 *     Lucien (monster) npc 364, (163,468), his hut north west of Varrock
 *     Fire warrior     npc 361, (542,3299), caged behind railings
 *     guardian         npc 362 and 363, the north chamber, (540-549, 3273-3282)
 *     winelda          npc 365, (552,3292), the south bank of the lava
 *
 *     Pendant of Lucien  item 721, gets through the Room of Fear
 *     Boots              item 722, (543,3374), gets across the bridge
 *     Ice Arrows         item 723, (560,3352) and (563,3354)
 *     Lever              item 724, (551,3327), goes in the bracket
 *     Staff of Armadyl   item 725, (540,3273)
 *     Pendant of Armadyl item 726, the guardians', lets Lucien be attacked
 *     shiny Key          item 732, (533,3302), past the lesser demons
 *
 * The dungeon is a sequence of six locked doors and the whole quest is the
 * business of opening them, so most of this file is door 104 through door 110:
 *
 *     104 (533,3342)  the Room of Fear      -- Pendant of Lucien, worn
 *     105 (546,3328)  the bridge over lava  -- Boots, worn, going west only
 *     106 (536,3349)  the ice spider cave   -- the trapped lever, pulled safely
 *     107 (545,3307)  the fire warrior      -- the Lever fitted and pulled
 *     108 (546,3302)  past the fire warrior -- the fire warrior dead
 *     110 (554,449)   the surface short cut -- the shiny Key
 *     109 (161,465)   Lucien's hut          -- the quest started
 *
 * Deviations:
 *
 *  - Door 109, Lucien's hut, is held shut until the quest is started. Like
 *    Elena's front door in Biohazard this is a reconstruction and not a
 *    recovered behaviour: no surviving transcript mentions the door, and 109 is
 *    a plain unnamed door in the defs. Npc 364 is spawned with the world, so
 *    without it the hut north west of Varrock is a room with Lucien already
 *    sitting in it, for a player who has not been told he exists. He says
 *    nothing before the quest starts, so this costs no progression either way.
 *
 *  - The 42 Thieving requirement is enforced again now that Thieving is
 *    implemented. It sits where vanilla put it: on the trapped lever itself
 *    (see searchForTraps()), not as a gate on starting the quest.
 *
 *  - Vanilla only lets Ice Arrows be fired from a yew or magic bow. Nothing in
 *    RangeEvent knows about bow-specific ammunition, so nothing here enforces
 *    it either; it belongs with the arrow table, not with this quest.
 *
 *  - Lighting a candle while standing in the dark room moves the player to the
 *    lit one in vanilla. It is not done here: candles are lit by the ordinary
 *    tinderbox rule in InvUseOnItem, and claiming the tinderbox and the candle
 *    to catch that moment would take candle-lighting away from the whole rest
 *    of the game. The stairs out of the dark room are two steps away, so the
 *    cost is a short walk.
 *
 *  - The trapped lever's own messages are mine. Jagex's are not recorded
 *    anywhere -- only the rule, that searching for traps first is what stops
 *    the damage.
 *
 *  - The fire warrior's reprieve message is mine, for the same reason, and for
 *    the same reason as Chronozon's in Family crest.
 *
 *  - Lucien's second opening option, "Yep lots of heroes about here", is in the
 *    transcript with its reply missing. It is offered and answered with
 *    silence rather than with something invented.
 *
 *  - The webs in the lit room need no code. Door 24 already has a generic cut
 *    in InvUseOnObject, which is where it belongs: they are ordinary webs.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class TempleOfIkov extends Quest {

    public final static int UID = Quests.TEMPLE_OF_IKOV;

    // ----------------------------------------------------------------- ids --

    private static final int LUCIEN = 360;
    private static final int LUCIEN_MONSTER = 364;
    private static final int FIRE_WARRIOR = 361;
    private static final int GUARDIAN_MAN = 362, GUARDIAN_WOMAN = 363;
    private static final int WINELDA = 365;

    private static final int PENDANT_LUCIEN = 721;
    private static final int BOOTS = 722;
    private static final int ICE_ARROWS = 723;
    private static final int LEVER_PIECE = 724;
    private static final int STAFF = 725;
    private static final int PENDANT_ARMADYL = 726;
    private static final int SHINY_KEY = 732;
    private static final int LIMPWURT = 220;
    private static final int LIMPWURT_NEEDED = 20;

    /** Anything that is alight. Candle, black candle, torch. */
    private static final int[] LIGHTS = { 601, 602, 774 };

    private static final int RANGED = 4, FLETCHING = 9, HITS = 3, THIEVING = 17;

    // --------------------------------------------------------------- doors --

    /** Doorframe. What an open door looks like, the same as WallObjectAction's. */
    private static final int OPEN_DOOR = 11;

    private static final int FEAR_DOOR = 104, FEAR_DOOR_X = 533, FEAR_DOOR_Y = 3342;
    private static final int BRIDGE_DOOR = 105, BRIDGE_DOOR_X = 546, BRIDGE_DOOR_Y = 3328;
    private static final int ICE_CAVE_DOOR = 106, ICE_CAVE_DOOR_X = 536, ICE_CAVE_DOOR_Y = 3349;
    private static final int BRACKET_DOOR = 107, BRACKET_DOOR_X = 545, BRACKET_DOOR_Y = 3307;
    private static final int WARRIOR_GATE = 108, WARRIOR_GATE_X = 546, WARRIOR_GATE_Y = 3302;
    private static final int EXIT_DOOR = 110, EXIT_DOOR_X = 554, EXIT_DOOR_Y = 449;
    private static final int HUT_DOOR = 109, HUT_DOOR_X = 161, HUT_DOOR_Y = 465;
    private static final int ODD_WALL = 22, ODD_WALL_X = 545, ODD_WALL_Y = 3283;

    // -------------------------------------------------------------- scenery --

    private static final int TRAPPED_LEVER = 361, TRAPPED_LEVER_X = 533, TRAPPED_LEVER_Y = 3305;
    /** Vanilla's own gate on this lever, now that Thieving exists to check. */
    private static final int TRAP_LEVEL = 42;
    private static final int BRACKET = 367, FIXED_LEVER = 368;
    private static final int BRACKET_X = 544, BRACKET_Y = 3307;
    private static final int STAIRS_DOWN = 370, STAIRS_DOWN_X = 537, STAIRS_DOWN_Y = 3337;
    private static final int STAIRS_UP_LIT = 369, STAIRS_UP_LIT_X = 534, STAIRS_UP_LIT_Y = 3371;
    private static final int STAIRS_UP_DARK = 41, STAIRS_UP_DARK_X = 515, STAIRS_UP_DARK_Y = 3370;

    /** How long the fitted lever stays in the bracket before it falls out. */
    private static final int LEVER_HOLDS = 10000;

    // ------------------------------------------------------------ landings --

    /* (537,3338) carries the Room of Fear's own wall and is solid; the room is
     * the open ground west of it. */
    private static final int FEAR_ROOM_X = 536, FEAR_ROOM_Y = 3338;
    /* Two tiles south of the stairs, the same way DARK_ROOM sits two south of
     * its own. (534,3372) is rock. */
    private static final int LIT_ROOM_X = 534, LIT_ROOM_Y = 3373;
    private static final int DARK_ROOM_X = 515, DARK_ROOM_Y = 3372;
    private static final int RAVINE_X = 545, RAVINE_Y = 3333;
    private static final int ICE_CAVE_MOUTH_X = 536, ICE_CAVE_MOUTH_Y = 3349;
    private static final int NORTH_BANK_X = 552, NORTH_BANK_Y = 3288;

    /** The two tiles Ice Arrows may be taken from, and nowhere else. */
    private static final int[][] ICE_ARROW_AT = { { 560, 3352 }, { 563, 3354 } };

    /** Up to 16, which is what the fall off the bridge is recorded as hitting for. */
    private static final int FALL_DAMAGE = 16;

    // -------------------------------------------------------------- stages --

    private static final int STARTED = 1;        /* Lucien handed over the pendant */
    private static final int ICE_DOOR = 2;       /* the trapped lever has been pulled */
    private static final int WARRIOR_DOOR = 4;   /* the bracket lever has been pulled */
    private static final int WARRIOR_DEAD = 8;   /* the fire warrior is down */
    private static final int JOINED = 16;        /* sided with the guardians */
    private static final int DONE = 32;

    /**
     * The two endings.
     *
     * Every bit below JOINED is on the way to both of them -- the ice arrows
     * cannot be reached without the trapped lever, the fire warrior cannot be
     * reached without the bracket, and the guardians cannot be reached without
     * the fire warrior -- so the only thing that tells the endings apart is
     * whether the player took the guardians' pendant. Both values are set
     * outright at the end, so has(JOINED) answers "which side" during the quest
     * and after it in the same way.
     */
    private static final int FINISHED_GUARDIAN = STARTED | ICE_DOOR | WARRIOR_DOOR | WARRIOR_DEAD | JOINED | DONE;
    private static final int FINISHED_LUCIEN = STARTED | ICE_DOOR | WARRIOR_DOOR | WARRIOR_DEAD | DONE;

    // ---------------------------------------------------------- per-player --

    /** Whether the trapped lever has been searched. Not persisted; nor was it. */
    private boolean trapDisarmed = false;

    /** The fire warrior an ice arrow has opened up, and nobody else. */
    private Npc warriorStruck = null;

    public TempleOfIkov(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Temple of Ikov");
        setFinalStage(FINISHED_GUARDIAN);
        addFinalStage(FINISHED_LUCIEN);

        associateNpc(LUCIEN);
        associateNpc(LUCIEN_MONSTER);
        associateNpc(FIRE_WARRIOR);
        associateNpc(GUARDIAN_MAN);
        associateNpc(GUARDIAN_WOMAN);
        associateNpc(WINELDA);

        associateDoor(FEAR_DOOR, FEAR_DOOR_X, FEAR_DOOR_Y);
        associateDoor(BRIDGE_DOOR, BRIDGE_DOOR_X, BRIDGE_DOOR_Y);
        associateDoor(ICE_CAVE_DOOR, ICE_CAVE_DOOR_X, ICE_CAVE_DOOR_Y);
        associateDoor(BRACKET_DOOR, BRACKET_DOOR_X, BRACKET_DOOR_Y);
        associateDoor(WARRIOR_GATE, WARRIOR_GATE_X, WARRIOR_GATE_Y);
        associateDoor(EXIT_DOOR, EXIT_DOOR_X, EXIT_DOOR_Y);
        associateDoor(HUT_DOOR, HUT_DOOR_X, HUT_DOOR_Y);
        associateDoor(ODD_WALL, ODD_WALL_X, ODD_WALL_Y);

        // All by placement. Stairs 41 stands in twenty other places and ladder
        // 5 in two hundred; the levers and the bracket are unique but are
        // claimed the same way for the sake of saying so.
        associateObject(TRAPPED_LEVER, TRAPPED_LEVER_X, TRAPPED_LEVER_Y);
        associateObject(BRACKET, BRACKET_X, BRACKET_Y);
        associateObject(FIXED_LEVER, BRACKET_X, BRACKET_Y);
        associateObject(STAIRS_DOWN, STAIRS_DOWN_X, STAIRS_DOWN_Y);
        associateObject(STAIRS_UP_LIT, STAIRS_UP_LIT_X, STAIRS_UP_LIT_Y);
        associateObject(STAIRS_UP_DARK, STAIRS_UP_DARK_X, STAIRS_UP_DARK_Y);

        associateItem(ICE_ARROWS);
        associateItem(STAFF);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("A mysterious stranger called Lucien asks you to go on a mission deep under the Temple of Ikov in central Kandarin. He wants you to retrieve an artifact known as the Staff of Armadyl. Towards the end of the quest you are presented with a choice on how to complete the quest.");
        setStartPoint("Flying Horse Inn, Ardougne");
        setSpeakTo("Lucien");
        setMissionLength("Long");
        // The manual's two minimum requirements. Ranged has no start gate in
        // this code and none is added here -- no valid reason for that was
        // ever on record, but nowhere in this quest is a level check on
        // Ranged known to have bitten, so it stays a declared prerequisite
        // only. Thieving's real gate is the trapped lever itself, in
        // searchForTraps() below, now that the skill exists (see the header).
        requireLevel(THIEVING, TRAP_LEVEL);
        requireLevel(RANGED, 35);
        // Both endings pay the same experience; which side the player took only
        // decides who they answer to afterwards.
        rewardExp(RANGED, 500, 250);
        rewardExp(FLETCHING, 500, 250);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("@gre@Well done you have completed the temple of Ikov quest");
    }

    // ------------------------------------------------------------- helpers --

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    /**
     * Every bit but STARTED is progress through the dungeon, and the dungeon is
     * behind Lucien's pendant, so none of them can be reached before he hands it
     * over. Saying so here rather than at each of the five call sites keeps a
     * stray trigger from starting the quest at, say, stage 8 -- started, with
     * the fire warrior already dead and no way to have got to him.
     */
    private void set(int bit) {
        if (!questStarted() && bit != STARTED) {
            return;
        }
        setStage(questStarted() ? getStage() | bit : bit);
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

    /** Whether the player is carrying anything that is alight. */
    private boolean carryingLight() {
        for (int id : LIGHTS) {
            if (holds(id)) {
                return true;
            }
        }
        return false;
    }

    private void hurt(int damage) {
        Player p = getOwner();
        p.setCurStat(HITS, Math.max(0, p.getCurStat(HITS) - damage));
        p.getActionSender().sendStat(HITS);
    }

    /**
     * Open a door for a moment and step the player to the far side of it.
     *
     * A door facing 0 stands between (x,y) and (x,y-1); one facing 1 stands
     * between (x,y) and (x-1,y). Lifted from Dragon slayer, which lifted it
     * from WallObjectAction: the open frame is cosmetic and the crossing is
     * the teleport, so the tile never has to be unblocked and reblocked.
     */
    private void walkThrough(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), OPEN_DOOR,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        if (door.getDirection() == 0) {
            p.teleport(door.getX(), p.getY() >= door.getY() ? door.getY() - 1 : door.getY(), false);
        } else {
            p.teleport(p.getX() >= door.getX() ? door.getX() - 1 : door.getX(), door.getY(), false);
        }
    }

    private boolean at(GameObject object, int x, int y) {
        return object.getX() == x && object.getY() == y;
    }

    /**
     * A guardian still standing within five tiles of the player, or null.
     *
     * Five tiles, not the whole chamber: seven guardians respawn there every
     * thirty seconds, so a cleared-room rule can never be satisfied. The
     * guardians chase whoever they attack, which is the intended way through
     * -- draw them off, then take the staff while none stands close enough
     * to object. "You may have to kill many of the Guardians of Armadyl
     * before you are able to obtain it."
     */
    private Npc nearestGuardian() {
        Player p = getOwner();
        Npc best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Npc n : p.getViewArea().getNpcsInView()) {
            if ((n.getID() == GUARDIAN_MAN || n.getID() == GUARDIAN_WOMAN) && n.getHits() > 0) {
                int dist = Math.max(Math.abs(n.getX() - p.getX()),
                                    Math.abs(n.getY() - p.getY()));
                if (dist <= 5 && dist < bestDist) {
                    best = n;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (object.getType() == 1) {
                door(trigger, object);
            } else {
                scenery(trigger, object, used);
            }
            return;
        }
        if (entity instanceof Item) {
            if (trigger == QuestTrigger.ITEM_PICKUP && ((Item) entity).getID() == ICE_ARROWS) {
                say("Suddenly your surroundings change");
                getOwner().teleport(ICE_CAVE_MOUTH_X, ICE_CAVE_MOUTH_Y, false);
            }
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.NPC_KILLED) {
            if (npc.getID() == FIRE_WARRIOR) {
                this.warriorStruck = null;
                set(WARRIOR_DEAD);
            } else if (npc.getID() == LUCIEN_MONSTER) {
                lucienBeaten(npc);
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case LUCIEN: lucien(npc); break;
            case LUCIEN_MONSTER: lucienMonster(npc); break;
            case GUARDIAN_MAN:
            case GUARDIAN_WOMAN: guardian(npc); break;
            case WINELDA: winelda(npc); break;
            default: break;
        }
    }

    // --------------------------------------------------------------- doors --

    private void door(QuestTrigger trigger, GameObject door) {
        if (trigger != QuestTrigger.DOOR_ACT1) {
            return;
        }
        switch (door.getID()) {
            case FEAR_DOOR: fearDoor(door); break;
            case BRIDGE_DOOR: bridgeDoor(door); break;
            case ICE_CAVE_DOOR: lockedUntil(door, has(ICE_DOOR)); break;
            case BRACKET_DOOR: lockedUntil(door, has(WARRIOR_DOOR)); break;
            case WARRIOR_GATE: warriorGate(door); break;
            case EXIT_DOOR: lockedUntil(door, holds(SHINY_KEY)); break;
            /*
             * Lucien's hut, shut until the quest is under way. Npc 364 stands
             * in it from the moment the world boots, so without this a player
             * can find the man in his hut before ever meeting him at the Flying
             * Horse Inn -- and the whole point of 364 is that he is somewhere
             * else, later, once you already know who he is. questStarted() and
             * not completed(), so the hut stays open once it has been opened.
             */
            case HUT_DOOR: lockedUntil(door, questStarted()); break;
            case ODD_WALL:
                say("You push the wall");
                walkThrough(door);
                break;
            default: break;
        }
    }

    private void lockedUntil(GameObject door, boolean open) {
        if (!open) {
            say("The door is locked");
            return;
        }
        walkThrough(door);
    }

    /**
     * The Room of Fear.
     *
     * The pendant has to be worn, not carried: it is what Lucien hands it over
     * for, and the whole of the rest of the quest is on the other side of it.
     */
    private void fearDoor(GameObject door) {
        if (!wearing(PENDANT_LUCIEN)) {
            say("As you reach to open the door");
            say("A great terror comes over you");
            say("You decide you'll not open this door today");
            return;
        }
        walkThrough(door);
    }

    /**
     * The bridge over the lava.
     *
     * The bridge is landscape -- overlay 12, four tiles at (543-544, 3327-3328),
     * with lava either side of it -- and landscape has no trigger, so this door
     * at its east end is where the weight is tested. Beyond the door is a single
     * tile, (545,3328), walled in north and south by railings: the only ways out
     * of it are back through this door and west across the bridge, which is what
     * makes the door and the crossing the same question.
     *
     * Tested westbound only. A player standing at x >= 546 is in the room and
     * about to cross; one at 545 has already crossed and is on their way out,
     * and stopping them there would only shut them in.
     *
     * x increases westward, so >= is the room and < is the far side.
     */
    private void bridgeDoor(GameObject door) {
        Player p = getOwner();
        if (p.getX() < door.getX() || wearing(BOOTS)) {
            walkThrough(door);
            return;
        }
        say("Your weight is too much for the bridge to hold");
        say("You fall through the bridge");
        say("The lava singes you");
        hurt(1 + (int) (Math.random() * FALL_DAMAGE));
        p.teleport(RAVINE_X, RAVINE_Y, false);
    }

    /**
     * The door beside the fire warrior's cage.
     *
     * He does not have to be fought to be got past -- he has to be dead, and
     * the door is how the game says so.
     */
    private void warriorGate(GameObject door) {
        if (!has(WARRIOR_DEAD)) {
            say("The fire warrior's eyes glow");
            say("The fire warrior glares at the door");
            say("The door handle is too hot to handle");
            return;
        }
        walkThrough(door);
    }

    // ------------------------------------------------------------- scenery --

    private void scenery(QuestTrigger trigger, GameObject object, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (object.getID() == BRACKET && at(object, BRACKET_X, BRACKET_Y)) {
                fitLever(object, used);
            }
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            if (object.getID() == TRAPPED_LEVER) {
                searchForTraps();
            } else {
                // Claiming an object takes its second command away from
                // ObjectAction as well as its first, so the claim has to answer
                // for both. Family crest's wording, for the same reason.
                say("Nothing interesting happens");
            }
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        switch (object.getID()) {
            case TRAPPED_LEVER: pullTrappedLever(); break;
            case FIXED_LEVER: pullFixedLever(); break;
            case BRACKET: say("Theres something missing here"); break;
            case STAIRS_DOWN: goDown(); break;
            case STAIRS_UP_LIT:
            case STAIRS_UP_DARK:
                getOwner().teleport(FEAR_ROOM_X, FEAR_ROOM_Y, false);
                break;
            default: break;
        }
    }

    /**
     * The stairs out of the Room of Fear.
     *
     * They lead to two different rooms and the light decides which. The lit one
     * has the boots in it behind a web; the dark one has nothing, which is the
     * point of it.
     */
    private void goDown() {
        Player p = getOwner();
        if (carryingLight()) {
            p.teleport(LIT_ROOM_X, LIT_ROOM_Y, false);
            say("Your flame lights up the room");
            return;
        }
        p.teleport(DARK_ROOM_X, DARK_ROOM_Y, false);
        say("You cannot see any further into the room");
        say("It is too dark");
    }

    /**
     * The trapped lever, which unlocks the ice spider cave.
     *
     * In vanilla this is where the 42 Thieving requirement sits, and now that
     * the skill is implemented this is where it is checked: below level 42 the
     * search comes up empty, the same lever stays trapped, and pulling it
     * still springs the needle in pullTrappedLever() below.
     */
    private void searchForTraps() {
        if (this.trapDisarmed) {
            say("You have already disabled the trap on this lever");
            return;
        }
        if (getOwner().getCurStat(THIEVING) < TRAP_LEVEL) {
            say("You search the lever for traps");
            say("You aren't quite skilled enough to disarm it safely");
            return;
        }
        this.trapDisarmed = true;
        say("You search the lever for traps");
        say("You find one, and disable it");
    }

    private void pullTrappedLever() {
        if (!this.trapDisarmed) {
            say("You reach for the lever");
            say("A needle springs out of the mechanism and jabs you");
            hurt(1 + (int) (Math.random() * 8));
            return;
        }
        getOwner().getActionSender().sendSound("opendoor");
        say("You pull the lever");
        say("You hear a door unlock in the distance");
        set(ICE_DOOR);
    }

    /**
     * The lever bracket, at (544,3307), and the door it throws beside it.
     *
     * Fitting the piece turns the bracket into a lever for a few seconds and
     * spends the item. Pulling it in time opens door 107 for good; missing it
     * means walking back over the bridge for another one. Both halves are
     * Jagex's and both are why the piece respawns every forty-five seconds.
     */
    private void fitLever(GameObject bracket, InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != LEVER_PIECE) {
            say("Nothing interesting happens");
            return;
        }
        p.getInventory().remove(LEVER_PIECE, 1);
        p.getActionSender().sendInventory();
        say("You fit the lever into the bracket");
        world.registerGameObject(new GameObject(bracket.getLocation(), FIXED_LEVER,
            bracket.getDirection(), bracket.getType()));
        world.delayedSpawnObject(bracket.getLoc(), LEVER_HOLDS);
    }

    private void pullFixedLever() {
        getOwner().getActionSender().sendSound("opendoor");
        say("You pull the lever");
        say("The door beside you swings open");
        set(WARRIOR_DOOR);
    }

    // -------------------------------------------------------- fire warrior --

    /**
     * An arrow has landed on the fire warrior.
     *
     * Only the ice one counts, and only against the warrior it hit: a respawn
     * is a different npc and starts closed again. Everything after the first
     * ice arrow may be ordinary arrows, which is why this records the npc
     * rather than a count.
     */
    public void rangedShot(Npc npc, int arrowId, int damage) {
        if (npc.getID() != FIRE_WARRIOR || arrowId != ICE_ARROWS) {
            return;
        }
        this.warriorStruck = npc;
    }

    /**
     * He cannot die until an ice arrow has opened him up.
     *
     * The refusal leaves him on one hit point with the fight still running,
     * which is the same reprieve Chronozon gets and for the same reason: the
     * quest has a fact about the fight that the combat code has no business
     * knowing.
     */
    public boolean refusesKill(Npc npc) {
        if (npc.getID() != FIRE_WARRIOR) {
            return false;
        }
        if (npc.equals(this.warriorStruck)) {
            return false;
        }
        say("The fire warrior shrugs off the blow");
        say("He can only be killed with a weapon of ice");
        return true;
    }

    // --------------------------------------------------------------- items --

    /**
     * The Staff of Armadyl and the Ice Arrows are both things the game says no
     * to more often than yes.
     *
     * The arrows may only be taken off their two spawns -- once they have been
     * fired and have landed on the floor they are gone -- and the staff may
     * only be taken by someone who has not sided with the guardians and has
     * drawn or cut down every guardian within five tiles of it.
     */
    public boolean refusesPickup(InvItem item) {
        Player p = getOwner();
        if (item.getID() == ICE_ARROWS) {
            for (int[] spawn : ICE_ARROW_AT) {
                if (p.getX() == spawn[0] && p.getY() == spawn[1]) {
                    return false;
                }
            }
            say("You can only take ice arrows from the cave of ice spiders");
            say("In the temple of Ikov");
            return true;
        }
        if (item.getID() != STAFF) {
            return false;
        }
        if (holds(STAFF)) {
            say("I already have one of those");
            return true;
        }
        if (has(JOINED) || completed()) {
            say("I shouldn't steal this");
            return true;
        }
        final Npc guardian = nearestGuardian();
        if (guardian != null) {
            new Conversation(p, guardian)
                .npc("That is not thine to take")
                .then(new Effect() {
                    public void run(Conversation c) {
                        c.stop();
                        guardian.attackPlayer(c.getPlayer());
                    }
                })
                .start();
            return true;
        }
        return false;
    }

    /**
     * Lucien in his hut will not be attacked without the guardians' pendant.
     *
     * He is level 21 and every player who reaches him could kill him; the only
     * thing between them and the wrong ending is that he talks them out of it.
     */
    public boolean refusesAttack(Npc npc) {
        if (npc.getID() != LUCIEN_MONSTER || wearing(PENDANT_ARMADYL)) {
            return false;
        }
        new Conversation(getOwner(), npc)
            .npc("I'm sure you don't want to attack me really")
            .npc("I am your friend")
            .message("You decide you don't want to attack Lucien really")
            .message("He is your friend")
            .start();
        return true;
    }

    // -------------------------------------------------------------- Lucien --

    private void lucien(Npc npc) {
        Player p = getOwner();
        if (!questStarted()) {
            lucienOpening(npc);
            return;
        }
        // He says this during the quest and after it; the pendant is reissued
        // for as long as he is standing there, which is forever.
        Conversation c = new Conversation(p, npc).npc("I thought I told you not to meet me here again");
        if (holds(PENDANT_LUCIEN) || wearing(PENDANT_LUCIEN)) {
            c.player("Yes you did, sorry").start();
            return;
        }
        c.options(new Choice("I lost that pendant you gave me", "Yes you did sorry") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    return;
                }
                c.npc("Hmm")
                 .npc("Imbecile")
                 .message("Lucien gives you another pendant")
                 .give(new InvItem(PENDANT_LUCIEN, 1));
            }
        }).start();
    }

    private void lucienOpening(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("I come seeking a hero who can help me")
            .options(new Choice("I am a hero", "Yep lots of heroes about here") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        // Jagex's reply is not recorded. See the header.
                        return;
                    }
                    c.npc("I need someone who can enter the tunnels under the deserted temple of Ikov")
                     .npc("Near Hemenster, to the north of here")
                     .npc("Kill the fire warrior of Lesarkus")
                     .npc("And retrieve the staff of Armardyl")
                     .options(new Choice("Why can't you do it yourself?",
                                         "That sounds like fun",
                                         "That sounds too dangerous for me",
                                         "How much will you pay me?") {
                        public void picked(int option, Conversation c) {
                            switch (option) {
                                case 1: hired(c); return;
                                case 2: tooDangerous(c); return;
                                case 3: howMuch(c); return;
                                default: break;
                            }
                            c.npc("The guardians of the staff of Armardyl fear me")
                             .npc("They know my kind is powerful")
                             .npc("So they have set up magical wards against are race")
                             .options(new Choice("How much will you pay me?",
                                                 "That sounds like fun",
                                                 "Who are your kind?",
                                                 "That sounds too dangerous for me") {
                                public void picked(int option, Conversation c) {
                                    switch (option) {
                                        case 0: howMuch(c); break;
                                        case 1: hired(c); break;
                                        case 3: tooDangerous(c); break;
                                        default:
                                            c.npc("An ancient and powerful race")
                                             .npc("Back in the second age we held great influence in this world")
                                             .npc("There are few of us left now");
                                    }
                                }
                            });
                        }
                    }.says(0, "Why can't you do that yourself?"));
                }
            })
            .start();
    }

    /** The branch that starts the quest and hands over the pendant. */
    private void hired(Conversation c) {
        c.npc("Well it's not that easy")
         .npc("The fire warrior can only be killed with a weapon of ice")
         .npc("And there are many other traps and hazards in those tunnels")
         .player("Well I am brave I shall give it a go")
         .npc("Take this pendant you will need it to get through the chamber of fear")
         .give(new InvItem(PENDANT_LUCIEN, 1))
         .npc("It is not safe for me to linger here much longer")
         .npc("When you have done meet me in the forest north of Varrock")
         .npc("I have a small holding up there")
         .then(new Effect() {
             public void run(Conversation c) {
                 set(STARTED);
             }
         });
    }

    private void tooDangerous(Conversation c) {
        c.npc("Fortune favours the bold");
    }

    private void howMuch(Conversation c) {
        c.npc("Ah the mercenary type I see")
         .player("It's a living")
         .npc("I shall adequately reward you")
         .npc("With both money and power")
         .player("Sounds rather too vague for me");
    }

    // ------------------------------------------------------ Lucien, at home --

    private void lucienMonster(Npc npc) {
        Player p = getOwner();
        if (!questStarted()) {
            // Lucien only moves out to this hut after being hired at the
            // Flying Horse Inn -- nobody has asked the player for the staff
            // yet, so there is nothing for him to say here.
            return;
        }
        if (completed()) {
            say("You have already completed this quest");
            return;
        }
        if (!holds(STAFF)) {
            new Conversation(p, npc)
                .npc("Have you got the staff of Armadyl yet?")
                .player("No not yet")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Have you got the staff of Armadyl yet?")
            .options(new Choice("Yes here it is", "No not yet") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.message("You give the staff to Lucien")
                     .take(STAFF, 1)
                     .npc("Muhahahaha")
                     .npc("Already I can feel the power of this staff running through my limbs")
                     .npc("Soon I shall be exceedingly powerful")
                     .npc("I suppose you would like a reward now")
                     .npc("I shall grant you much power")
                     .message("A glow eminates from Lucien's helmet")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             setStage(FINISHED_LUCIEN);
                         }
                     })
                     .npc("I must be away now to make preparations for my conquest")
                     .npc("Muhahahaha");
                }
            })
            .start();
    }

    /**
     * The other ending.
     *
     * Nothing is said. NPC_KILLED is fired from Npc.killedBy after the npc has
     * been unregistered and removed, so there is no longer a mouth to put words
     * in, and his last words are not recorded anywhere to put in it. The one
     * message is completeQuest()'s.
     *
     * Guarded on JOINED as well as on completed(): only the guardians' pendant
     * lets him be attacked at all, so anyone standing over him has it, but the
     * quest should not finish itself on a bit it never set.
     */
    private void lucienBeaten(Npc npc) {
        if (completed() || !has(JOINED)) {
            return;
        }
        setStage(FINISHED_GUARDIAN);
    }

    // ----------------------------------------------------------- guardians --

    private void guardian(final Npc npc) {
        Player p = getOwner();
        if (completed()) {
            if (has(JOINED)) {
                new Conversation(p, npc)
                    .player("I have defeated Lucien")
                    .npc("Well done")
                    .npc("We can only hope that will keep him quiet for a while")
                    .start();
            } else {
                new Conversation(p, npc)
                    .npc("Get away from here")
                    .npc("Thou evil agent of Lucien")
                    .start();
            }
            return;
        }
        if (wearing(PENDANT_LUCIEN)) {
            new Conversation(p, npc)
                .npc("Ahh tis a foul agent of Lucien")
                .npc("Get ye from our master's house")
                .then(attack(npc))
                .start();
            return;
        }
        if (holds(STAFF)) {
            new Conversation(p, npc)
                .npc("Stop")
                .npc("You cannot take the staff of Armadyl")
                .then(attack(npc))
                .start();
            return;
        }
        if (has(JOINED)) {
            guardianAfterJoining(npc);
            return;
        }
        new Conversation(p, npc)
            .npc("Thou dost venture deep in the tunnels")
            .npc("It has been many a year since someone has passed thus far")
            .options(new Choice("I seek the staff of Armadyl",
                                "Out of my way fool",
                                "Who are you?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        seekTheStaff(c, npc);
                    } else if (option == 1) {
                        outOfMyWay(c, npc);
                    } else {
                        whoAreYou(c, npc);
                    }
                }
            })
            .start();
    }

    private Effect attack(final Npc npc) {
        // attackPlayer() will not touch a player the dialogue still has marked
        // busy, so the conversation is stopped rather than left to run down.
        return new Effect() {
            public void run(Conversation c) {
                c.stop();
                npc.attackPlayer(c.getPlayer());
            }
        };
    }

    private void guardianAfterJoining(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc).npc("Any luck against Lucien?");
        if (holds(PENDANT_ARMADYL) || wearing(PENDANT_ARMADYL)) {
            c.player("Not yet").npc("Well good luck on your quest").start();
            return;
        }
        c.options(new Choice("Not yet", "No I've lost the pendant you gave me") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Well good luck on your quest");
                    return;
                }
                c.npc("Thou art a careless buffoon")
                 .npc("Have another one")
                 .message("The guardian gives you a pendant")
                 .give(new InvItem(PENDANT_ARMADYL, 1));
            }
        }).start();
    }

    private void seekTheStaff(Conversation c, final Npc npc) {
        c.npc("We guard that here")
         .npc("As did our fathers")
         .npc("And our father's fathers")
         .npc("Why dost thou seeketh it?")
         .options(new Choice("A guy named Lucien is paying me",
                             "Just give it to me",
                             "I am a collector of rare and powerful artifacts") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    workingForLucien(c, npc);
                } else if (option == 1) {
                    c.npc("The staff is a sacred object")
                     .npc("Not to be given away to anyone who asks");
                } else {
                    notYoursToCollect(c);
                }
            }
        }.says(2, "I am a collector of rare and powerful objects"));
    }

    /**
     * The reply is shared by two options that both speak the collector line --
     * the collector option itself, and "Ok that's nice to know", which answers
     * with it by mistake. Both carry it on their own says(), so it is not
     * repeated here.
     */
    private void notYoursToCollect(Conversation c) {
        c.npc("The staff is not yours to collect");
    }

    private void outOfMyWay(Conversation c, final Npc npc) {
        c.npc("I may be a fool, but I will not step aside")
         .options(new Choice("Why not?",
                             "Then I must strike you down",
                             "Then I guess I will turn back") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Only members of our order are allowed further");
                } else if (option == 1) {
                    c.then(attack(npc));
                }
            }
        });
    }

    private void whoAreYou(Conversation c, final Npc npc) {
        c.npc("I am a guardian of Armadyl")
         .npc("We have kept this place safe and holy")
         .npc("For many generations")
         .npc("Many evil souls would like to get their hands on what lies here")
         .npc("Especially the Mahjarrat")
         .options(new Choice("What is an Armadyl?",
                             "Who are the Mahjarrat?",
                             "Wow you must be old") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    whatIsAnArmadyl(c);
                } else if (option == 1) {
                    whoAreTheMahjarrat(c, npc);
                } else {
                    c.npc("No no, I have not guarded here for all those generations")
                     .npc("Many generations of my family have though");
                }
            }
        });
    }

    private void whatIsAnArmadyl(Conversation c) {
        c.npc("Armadyl is our God")
         .npc("We are his servants")
         .npc("Who have the honour to stay here")
         .npc("And guard his artifacts")
         .npc("Till he needs them to smite his enemies")
         .options(new Choice("Ok that's nice to know",
                             "Someone told me there were only three gods") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    // Jagex's, and plainly a mistake in Jagex's: this option
                    // answers with the collector's reply. Left as it was.
                    notYoursToCollect(c);
                    return;
                }
                c.player("Saradomin, Zamorak and Guthix")
                 .npc("Was that someone a Saradominist?")
                 .npc("I hear Saradominism is the principle doctrine")
                 .npc("Out in the world currently")
                 .npc("They only Acknowledge those three gods")
                 .npc("They are wrong")
                 .npc("Depending on what you define as a god")
                 .npc("We are aware of at least twenty");
            }
        }.says(0, "I am a collector of rare and powerful objects"));
    }

    private void whoAreTheMahjarrat(Conversation c, final Npc npc) {
        c.npc("Ancient powerful beings")
         .npc("They are very evil")
         .npc("They were said to once dominate this plane of existance")
         .npc("Zamorak was said to once have been of their stock")
         .npc("They are few in number and have less power these days")
         .npc("Some still have presence in this world in their liche forms")
         .npc("Mahjarrat such as Lucien and Azzanadra would become extremely powerful")
         .npc("If they got their hands on the staff of Armadyl")
         .options(new Choice("Did you say Lucien?",
                             "You had better guard it well then") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("Don't fret, for we shall");
                    return;
                }
                c.player("He's the one who sent me to fetch the staff");
                workingForLucien(c, npc);
            }
        }.says(1, "You had better guard it well them"));
    }

    /**
     * The fork the whole quest is built around.
     *
     * Admitting who sent you is the only way to the guardians' offer, and two
     * of the three answers to it are a fight.
     */
    private void workingForLucien(Conversation c, final Npc npc) {
        c.npc("Thou art working for him?")
         .npc("Thy fool")
         .npc("Quick you must be cleansed to save your soul")
         .options(new Choice("How dare you call me a fool?",
                             "Erm I think I'll be leaving now",
                             "Yes I could do with a bath") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.player("I will work for who I please")
                     .npc("This one is too far gone")
                     .npc("He must be cut down to stop the spread of the blight")
                     .then(attack(npc));
                } else if (option == 1) {
                    c.npc("We cannot allow an agent of Lucien to roam free")
                     .then(attack(npc));
                } else {
                    cleansed(c, npc);
                }
            }
        });
    }

    private void cleansed(Conversation c, final Npc npc) {
        c.message("The guardian splashes holy water over you")
         .npc("That should do the trick")
         .npc("Now you say that Lucien sent you to retrieve the staff")
         .npc("He must not get a hold of it")
         .npc("He would become too powerful with the staff")
         .npc("Hast thou heard of the undead necromancer?")
         .npc("Who raised an undead army against Varrock a few years past")
         .npc("That was Lucien")
         .npc("If thou knowest where to find him maybe you can help us against him")
         .options(new Choice("Ok I will help",
                             "No I shan't turn against my employer",
                             "I need time to consider this") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("This one is too far gone")
                     .npc("He must be cut down to stop the spread of the blight")
                     .then(attack(npc));
                    return;
                }
                if (option == 2) {
                    c.npc("Come back when you have made your choice");
                    return;
                }
                c.npc("So you know where he lurks?")
                 .player("Yes")
                 .npc("He must be growing in power again if he is after the staff")
                 .npc("If you can defeat him, it may weaken him for a time")
                 .npc("You will need to use this pendant to even be able to attack him")
                 .message("The guardian gives you a pendant")
                 .give(new InvItem(PENDANT_ARMADYL, 1))
                 .then(new Effect() {
                     public void run(Conversation c) {
                         set(JOINED);
                     }
                 });
            }
        });
    }

    // ------------------------------------------------------------- winelda --

    /**
     * The toll across the lava. Twenty raw roots, every crossing, and the
     * certificates are no good to her -- she wants something to chew on.
     */
    private void winelda(Npc npc) {
        Player p = getOwner();
        if (p.getInventory().countId(LIMPWURT) >= LIMPWURT_NEEDED) {
            new Conversation(p, npc)
                .player("I have the 20 limpwurt roots, now transport me please")
                .npc("Oh marverlous")
                .npc("Brace yourself then")
                .take(LIMPWURT, LIMPWURT_NEEDED)
                .then(new Effect() {
                    public void run(Conversation c) {
                        c.getPlayer().teleport(NORTH_BANK_X, NORTH_BANK_Y, false);
                    }
                })
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Hehe in a bit of a pickle are we?")
            .npc("Want to be getting over the nasty lava stream do we?")
            .options(new Choice("Not really, no", "Yes we do", "Yes I do") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Hehe ye'll come back later")
                         .npc("They always come back later");
                        return;
                    }
                    c.npc("Well keep it under your helmet")
                     .npc("But I'm knowing some useful magic tricks")
                     .npc("I could get you over there easy as that")
                     .player("Okay get me over there")
                     .npc("Okay brace yourself")
                     .npc("Actually no no")
                     .npc("Why should I do it for free")
                     .npc("Bring me a bite to eat and I'll be a touch more helpful")
                     .npc("How about some nice tasty limpwurt roots to chew on")
                     .npc("Yes yes that's good, bring me 20 limpwurt roots and over you go");
                }
            })
            .start();
    }
}
