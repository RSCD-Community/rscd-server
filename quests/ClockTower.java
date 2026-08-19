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
 * Clock tower. Released 17 June 2002, written by Thomas Woode.
 *
 * Brother Kojo's clock has stopped and its four cogs are scattered through the
 * cellars under the tower. Each cog is a colour, each colour has a pole on
 * every one of the tower's four levels, and Kojo cannot remember which cog
 * belongs to which level. That is the whole quest: fetch four cogs, work out
 * which floor each one goes on.
 *
 *     colour  cog  found                                 belongs on
 *     blue    727  behind the odd looking wall (584,3457)  the cellar,   pole 362 (580,3470)
 *     black   728  in the fires at (608..609, 3463..3467)  ground floor, pole 365 (581, 639)
 *     red     729  the ogre room, (616,3484)               first floor,  pole 363 (582,1582)
 *     purple  730  inside the rat cage, (579,3474)         top floor,    pole 364 (581,2525)
 *
 * The four poles stand in a cross around the clock shaft on each level, at
 * y 637..639 on the ground floor and then 1581..1583, 2525..2527 and
 * 3469..3471 going up and down. Sixteen poles, four cogs, one arrangement.
 *
 * Progress is a bit set in the stage, because there are five independent things
 * to remember and only one number to remember them in. That is what stages are
 * for here -- setStage() writes through to the player and completed() is exact
 * equality, so a final stage of 127 is reached when, and only when, every bit
 * is set, whatever order they were set in.
 *
 * What the quest claims: Brother Kojo, the sixteen poles, the food trough and
 * the rat cage. Not the cogs on the ground -- ITEM_PICKUP needs the item
 * claimed, so it takes those four as well, but nothing else. The odd looking
 * wall at (584,3457) is left alone: WallObjectAction already knows it is one
 * of the five secret walls in the world that divide anything, and pushes the
 * player through it.
 *
 * Deviations, all of them things the world data cannot support:
 *
 *  - There are no rats. NpcLoc has no spawn of anything at all in the clock
 *    cellars -- no dungeon rats, no skeletons, no ogres -- so the poison kills
 *    an empty cage and the walk down is unopposed. The messages are still the
 *    real ones, and they will be true again the day the spawns are restored.
 *
 *  - The two gates at (590,3475) and (594,3475) are thrown by the levers at
 *    (590,3478) and (594,3478), world-shared the way the objects themselves
 *    are: lever and gate each swap between their two defs (pull 373 / push
 *    374, open 372 / shut 371) for everyone at once. With no rats to pen the
 *    mechanism pens nothing, but it works, and the trough can be reached
 *    either way.
 *
 *  - A cog put on the wrong pole is refused rather than left sitting there.
 *    Standing a cog on a pole would mean a per-player object, which the
 *    protocol has no room for, so the pole says it does not fit and the cog
 *    stays in the pack. The puzzle is unchanged; only being able to see your
 *    own mistake is lost.
 *
 *  - The black cog is red hot and cannot be carried. Vanilla wanted a bucket
 *    of water poured over it or ice gloves worn; picking it up unprotected
 *    burns the player and the cog stays where it was, answered through
 *    refusesPickup before the item ever leaves the ground.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class ClockTower extends Quest {

    public final static int UID = Quests.CLOCK_TOWER;

    private static final int KOJO = 366;

    /* Stage bits. */
    private static final int STARTED  =  1;
    private static final int BLUE     =  2;
    private static final int BLACK    =  4;
    private static final int RED      =  8;
    private static final int PURPLE   = 16;
    private static final int POISONED = 32;
    private static final int TOLD     = 64;
    private static final int FINISHED = STARTED | BLUE | BLACK | RED | PURPLE
                                      | POISONED | TOLD;
    /**
     * Finishing does not require POISONED: the poison step exists to clear
     * rats off the route to a cog, and with no rat spawns in the cellars a
     * player can honestly fetch all four cogs without ever touching the
     * trough. Both endings are declared final; the bit still records the
     * deed for whoever does it.
     */
    private static final int FINISHED_UNPOISONED = FINISHED - POISONED;

    private static final int POLE_BLUE = 362, POLE_RED = 363,
                             POLE_PURPLE = 364, POLE_BLACK = 365;
    private static final int TROUGH = 375;

    /* The rat-pen mechanism. Each lever stands over its own gate (same x)
     * and both are def pairs: the lever reads pull or push, the gate open or
     * shut, and throwing the lever swaps both for the whole world. */
    private static final int LEVER_PULL = 373, LEVER_PUSH = 374;
    private static final int GATE_OPEN = 372, GATE_SHUT = 371;
    private static final int[][] PENS = {
        { 590, 3478, 3475 },   /* lever x, lever y, gate y */
        { 594, 3478, 3475 }
    };
    private static final int RAT_CAGE = 111;      /* a DoorDef, not scenery */
    private static final int CAGE_X = 583, CAGE_Y = 3476;
    private static final int OPEN_DOOR = 11;

    private static final int COG_BLUE = 727, COG_BLACK = 728,
                             COG_RED = 729, COG_PURPLE = 730;
    private static final int[] COGS = { COG_BLUE, COG_BLACK, COG_RED, COG_PURPLE };
    private static final int RAT_POISON = 731;

    private static final int BUCKET_OF_WATER = 50, BUCKET = 21;
    private static final int ICE_GLOVES = 556;
    private static final int COINS = 10;

    /**
     * cog, its pole, and where that pole stands. One row per cog: the pole of
     * the right colour on the right floor, which is the only place the cog
     * goes.
     */
    private static final int[][] FITS = {
        /* cog,      pole,        x,   y  */
        { COG_BLUE,   POLE_BLUE,   580, 3470 },
        { COG_BLACK,  POLE_BLACK,  581,  639 },
        { COG_RED,    POLE_RED,    582, 1582 },
        { COG_PURPLE, POLE_PURPLE, 581, 2525 },
    };
    private static final int[] BIT_OF = { BLUE, BLACK, RED, PURPLE };

    public ClockTower(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Clock tower");
        setFinalStage(FINISHED);
        addFinalStage(FINISHED_UNPOISONED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Help the confused Brother Kojo find the missing cogs and fix his watch tower. Search the dungeon using brawn and brains to correctly place the four cogs.");
        setStartPoint("Just south of Ardounge");
        setSpeakTo("Brother Kojo");
        setMissionLength("Medium");
        rewardItem(COINS, 500);

        associateNpc(KOJO);
        associateObject(POLE_BLUE);
        associateObject(POLE_RED);
        associateObject(POLE_PURPLE);
        associateObject(POLE_BLACK);
        associateObject(TROUGH);
        for (int[] pen : PENS) {
            associateObject(LEVER_PULL, pen[0], pen[1]);
            associateObject(LEVER_PUSH, pen[0], pen[1]);
            associateObject(GATE_OPEN, pen[0], pen[2]);
            associateObject(GATE_SHUT, pen[0], pen[2]);
        }
        associateDoor(RAT_CAGE);
        /* Claimed only so that ITEM_PICKUP is dispatched for them. */
        for (int i = 0; i < COGS.length; i++) {
            associateItem(COGS[i]);
        }
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Clock tower quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            if (trigger == QuestTrigger.NPC_TALK) {
                talkToKojo((Npc) entity);
            }
            return;
        }
        if (entity instanceof InvItem) {
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2) {
            ratCage(object);
        } else if (object.getID() == LEVER_PULL || object.getID() == LEVER_PUSH) {
            throwPenLever(object);
        } else if (object.getID() == GATE_OPEN || object.getID() == GATE_SHUT) {
            swingPenGate(object);
        } else if (object.getID() == TROUGH) {
            trough(used);
        } else {
            pole(object, used);
        }
    }

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    /** Set a bit without disturbing the others. */
    private void gain(int bit) {
        setStage((questStarted() ? getStage() : 0) | bit);
    }

    // ---------------------------------------------------------- the rat pens --

    /** The gate under a lever, or null if it is not standing where expected. */
    private GameObject penGate(GameObject lever) {
        for (int[] pen : PENS) {
            if (lever.getX() == pen[0] && lever.getY() == pen[1]) {
                GameObject gate = world.getTile(pen[0], pen[2]).getGameObject();
                return gate;
            }
        }
        return null;
    }

    private void throwPenLever(GameObject lever) {
        Player p = getOwner();
        boolean pulling = lever.getID() == LEVER_PULL;
        p.getActionSender().sendMessage(pulling ? "You pull the lever" : "You push the lever");
        world.registerGameObject(new GameObject(lever.getLocation(),
            pulling ? LEVER_PUSH : LEVER_PULL, lever.getDirection(), lever.getType()));
        GameObject gate = penGate(lever);
        if (gate != null && (gate.getID() == GATE_OPEN || gate.getID() == GATE_SHUT)) {
            world.registerGameObject(new GameObject(gate.getLocation(),
                gate.getID() == GATE_OPEN ? GATE_SHUT : GATE_OPEN,
                gate.getDirection(), gate.getType()));
        }
        p.getActionSender().sendSound("opendoor");
        p.getActionSender().sendMessage("You hear a gate moving");
    }

    /** The gates answer to their own open and close commands as well. */
    private void swingPenGate(GameObject gate) {
        Player p = getOwner();
        boolean opening = gate.getID() == GATE_SHUT;
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(gate.getLocation(),
            opening ? GATE_OPEN : GATE_SHUT, gate.getDirection(), gate.getType()));
        p.getActionSender().sendMessage(opening ? "You open the gate" : "You close the gate");
    }

    // ------------------------------------------------------------- cogs --

    /**
     * One cog at a time, and the black one is hot.
     *
     * Answered before the item moves, so a refused cog never leaves the
     * floor -- it used to be taken and then confiscated, which despawned it
     * until the ground respawn timer brought it back.
     */
    public boolean refusesPickup(InvItem item) {
        Player p = getOwner();
        int id = item.getID();
        boolean cog = false;
        for (int i = 0; i < COGS.length; i++) {
            if (COGS[i] == id) {
                cog = true;
                break;
            }
        }
        if (!cog) {
            return false;
        }
        if (!questStarted()) {
            p.getActionSender().sendMessage("You have no idea what this is for");
            return true;
        }
        if (carriedCogs() > 0) {
            p.getActionSender().sendMessage("You can only carry one cog at a time");
            return true;
        }
        if (id != COG_BLACK) {
            return false;
        }
        if (p.getInventory().wielding(ICE_GLOVES)) {
            p.getActionSender().sendMessage("Your ice gloves protect you from the heat");
            return false;
        }
        if (p.getInventory().getLastIndexById(BUCKET_OF_WATER) > -1) {
            p.getInventory().remove(BUCKET_OF_WATER, 1);
            p.getInventory().add(new InvItem(BUCKET, 1));
            p.getActionSender().sendInventory();
            p.getActionSender().sendMessage("You pour the water over the cog");
            p.getActionSender().sendMessage("The cog hisses and cools");
            return false;
        }
        p.getActionSender().sendMessage("The cog is red hot from the fires");
        p.getActionSender().sendMessage("You burn your hands and drop it");
        p.setCurStat(3, Math.max(1, p.getCurStat(3) - 4));
        p.getActionSender().sendStat(3);
        return true;
    }

    private int carriedCogs() {
        int n = 0;
        for (int i = 0; i < COGS.length; i++) {
            n += getOwner().getInventory().countId(COGS[i]);
        }
        return n;
    }

    // ------------------------------------------------------------ poles --

    /**
     * A cog onto a pole. Clicking a bare pole says what is on it; using a cog
     * on it puts the cog there if it belongs there.
     */
    private void pole(GameObject pole, InvItem used) {
        Player p = getOwner();
        if (used == null) {
            p.getActionSender().sendMessage(placed(pole)
                ? "A cog is turning on this pole"
                : "This pole has no cog on it");
            return;
        }
        int fit = -1;
        for (int i = 0; i < FITS.length; i++) {
            if (FITS[i][0] == used.getID()) {
                fit = i;
                break;
            }
        }
        if (fit < 0) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!questStarted()) {
            p.getActionSender().sendMessage("You should ask the monk about this first");
            return;
        }
        if (pole.getID() != FITS[fit][1]
                || pole.getX() != FITS[fit][2] || pole.getY() != FITS[fit][3]) {
            p.getActionSender().sendMessage(placed(pole)
                ? "There is already a cog on this pole"
                : "The cog doesn't seem to fit this pole");
            return;
        }
        if (has(BIT_OF[fit])) {
            p.getActionSender().sendMessage("There is already a cog on this pole");
            return;
        }
        p.getInventory().remove(used.getID(), 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You put the cog on the pole");
        gain(BIT_OF[fit]);
        if (clockWhole() && !has(TOLD)) {
            p.getActionSender().sendMessage("@gre@The clock begins to tick");
            p.getActionSender().sendMessage("@gre@You should tell Brother Kojo");
        }
    }

    /** All four missing cogs are on their poles. */
    private boolean clockWhole() {
        return has(BLUE) && has(BLACK) && has(RED) && has(PURPLE);
    }

    /**
     * Whether a cog is turning on this pole. The four FITS placements turn
     * once their cog is placed; the other twelve poles in the tower were
     * never missing theirs -- each floor is short exactly one cog -- so they
     * always have one.
     */
    private boolean placed(GameObject pole) {
        for (int i = 0; i < FITS.length; i++) {
            if (pole.getID() == FITS[i][1]
                    && pole.getX() == FITS[i][2] && pole.getY() == FITS[i][3]) {
                return has(BIT_OF[i]);
            }
        }
        return true;
    }

    // -------------------------------------------------------- the rats --

    private void trough(InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != RAT_POISON) {
            p.getActionSender().sendMessage("It's for feeding the rat's");
            return;
        }
        if (!questStarted()) {
            p.getActionSender().sendMessage("There is no reason to do that");
            return;
        }
        p.getInventory().remove(RAT_POISON, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You pour the rat poison into the feeding trough");
        if (has(POISONED)) {
            return;
        }
        sayMessage("In their panic the rats bend and twist");
        sayMessage("The cage bars with their teeth");
        sayMessage("They're becoming weak, some have collapsed");
        sayMessage("The rats are eating the poison");
        sayMessage("They're becoming weak, some have collapsed");
        sayMessage("The rats are slowly dying");
        gain(POISONED);
    }

    /**
     * The rat cage. Its one command is "search", and until the rats have
     * chewed the bars there is nothing to find.
     */
    private void ratCage(GameObject door) {
        Player p = getOwner();
        if (door.getX() != CAGE_X || door.getY() != CAGE_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!has(POISONED)) {
            p.getActionSender().sendMessage("The bars are sound, you can't get through");
            return;
        }
        p.getActionSender().sendMessage("In a panic to escape, the rats have..");
        p.getActionSender().sendMessage("..bent the bars, you can just crawl through");
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), OPEN_DOOR,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        /* Faces east/west, so it stands between x-1 and x. */
        p.teleport(p.getX() >= door.getX() ? door.getX() - 1 : door.getX(),
            door.getY(), false);
    }

    // ----------------------------------------------------- Brother Kojo --

    private void talkToKojo(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("Hello again Brother Kojo")
                .npc("Oh hello there traveller")
                .npc("You've done a grand job with the clock")
                .npc("It's just like new")
                .start();
            return;
        }
        if (clockWhole() && !has(TOLD)) {
            new Conversation(p, npc)
                .player("Your clock is fixed")
                .npc("So it is! The town people will be delighted")
                .npc("Please take this for your trouble")
                .then(new Effect() {
                    public void run(Conversation c) {
                        setStage((questStarted() ? getStage() : 0) | TOLD);
                    }
                })
                .start();
            return;
        }
        if (questStarted()) {
            new Conversation(p, npc)
                .player("Hello again")
                .npc("Oh hello, are you having trouble?")
                .npc("The cogs are in four rooms below us")
                .npc("Place one cog on a pole on each")
                .npc("Of the four tower levels")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("Hello Monk")
            .npc("Hello traveller, I'm Brother Kojo")
            .npc("Do you know the time?")
            .player("No... Sorry")
            .npc("Oh dear, oh dear, I must fix the clock")
            .npc("The town people are becoming angry")
            .npc("Please could you help?")
            .options(new Choice("Ok old monk what can I do?", "Not now old monk") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Ok then");
                        return;
                    }
                    c.npc("Oh thank you kind sir")
                     .npc("In the cellar below you'll find four cogs")
                     .npc("They're too heavy for me, but you should")
                     .npc("Be able to carry them one at a time")
                     .npc("One goes on each floor")
                     .npc("But I can't remember which goes where")
                     .player("I'll do my best")
                     .npc("Be careful, strange beasts dwell in the cellars")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             setStage(STARTED);
                         }
                     });
                }
            })
            .start();
    }
}
