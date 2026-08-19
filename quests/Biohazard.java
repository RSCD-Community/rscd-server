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
 * Biohazard. Released 23 October 2002, written by Paul Gower and Thomas Woode.
 *
 * The sequel to Plague city. Elena is home but the mourners kept her
 * distillator, so the quest is a burglary of the mourner quarters followed by
 * a courier run across the map to Varrock, and it ends with the discovery that
 * the Ardougne plague was never real.
 *
 *     Elena          npc 483, (605,573), her house in East Ardougne
 *     Jerico         npc 486, (580,586), next to the Ardougne chapel
 *     Omart          npc 484, (624,616), outside the south east corner of the wall
 *     Kilron         npc 487, (629,613), the West Ardougne side of the same corner
 *     Mourner        npc 492, (631,573), on the door of the mourner quarters
 *     Mourner        npc 502, four of them inside the quarters, level 25
 *     Mourner        npc 495, (632,1514), upstairs, level 22, carries the key
 *     nurse sarah    npc 500, (647,603), the nurses' hut
 *     Chemist        npc 504, (344,665), the west house in Rimmington
 *     Chancy         npc 505 (340,657) Rimmington, npc 509 (94,528) Varrock
 *     Hops           npc 506 (344,657) Rimmington, npc 510 (93,529) Varrock
 *     DeVinci        npc 507 (346,658) Rimmington, npc 511 (92,528) Varrock
 *     Guard          npc 503, (94,521), the gate into south east Varrock
 *     Guidor's wife  npc 488, (84,534)
 *     Guidor         npc 508, (82,534), behind her
 *     king Lathas    npc 512, (613,602), Ardougne castle
 *
 *     Messenger pigeons  item 799, (574,583) (574,585) (574,586), Jerico's yard
 *     Bird feed          item 800, Jerico's cupboard
 *     Rotten apples      item 801, (642,565) and (650,564)
 *     Doctors gown       item 802, nurse sarah's cupboard
 *     Bronze key         item 803, off the level 22 mourner
 *     Distillator        item 804, the confiscated goods crate
 *     Priest robe / gown items 807 and 808, the Varrock clothes shop
 *     Liquid Honey       item 809, Ethenea 810, Sulphuric Broline 811
 *     Plague sample      item 812, Touch paper 813
 *     king lathas Amulet item 826, the reward
 *
 *     Watch tower    object 494, (622,585)
 *     cupboard       object 500, (581,587), Jerico's -- bird feed
 *     cupboard       object 510, (648,602), nurse sarah's -- doctors gown
 *     Cooking pot    object 502, (635,564), the mourners' stew
 *     Door           door 138, (633,573), the way into the mourner quarters
 *     gate           object 504, (631,1514), the bronze key gate upstairs
 *     crate          object 505, (627,1514), the confiscated goods
 *     gate           object 513, (93,521), the guarded gate in Varrock
 *     Door           door 145, (83,534), Guidor's sick room
 *     Door           door 152, (608,573), Elena's front door
 *
 * Deviations:
 *
 *  - Elena's front door is held shut until Plague city is complete. This is a
 *    reconstruction, not a recovered behaviour: no transcript or wiki page
 *    records the door, and door 152 is a plain unnamed door in the defs. It is
 *    here because the alternative is worse -- npc 483 stands in that house from
 *    the moment the world boots, so a player who has not rescued Elena yet can
 *    walk in and find her at home, days before the quest that goes looking for
 *    her. She is dialogue-inert until then, so nothing breaks either way; what
 *    is at stake is only whether the story survives contact with the map.
 *
 *  - The Thieving reward is paid into stat 17, which is the right index. The
 *    label was wrong on the server -- Formulae.statArray[17] read "quest" --
 *    until task 38 corrected it. The client always said "Thieving", so nothing
 *    a player could see was ever wrong; the only thing the bad label broke was
 *    the setstat command.
 *
 *  - The plague sample disintegrates on any teleport ("the plague sample is
 *    too delicate... / it disintegrates in the crossing"), matching vanilla --
 *    the hook is Formulae.teleportContraband, called by the spell teleports
 *    and the charged dragonstone amulet, shared with Pirate's Treasure's
 *    rum-cannot-leave-Karamja rule. Elena replaces a lost sample as before.
 *
 *  - The level 25 mourners inside the quarters attack on being spoken to
 *    without the gown, which is what the transcript records. In vanilla they
 *    are also aggressive on sight. Nothing here can express "aggressive only
 *    under a condition", so walking past them silently is possible.
 *
 *  - Which errand boy is carrying which vial is remembered two ways. The three
 *    correct pairings are stage bits and survive a logout; a wrong pairing is
 *    a field on this object and does not, so a player who logs out after
 *    handing DeVinci the honey gets the Varrock boy's ordinary "doesn't feel
 *    like talking" instead of his story about the painting. The vial is gone
 *    either way, which is the part that matters, and Elena replaces it.
 *
 *  - The mourner on the quarters door speaks through game messages rather than
 *    a chat bubble when the door is opened. The door trigger has no npc to
 *    hand and the wandering mourner may be anywhere in his patrol; Plague city
 *    prints Elena's cell line the same way.
 *
 *  - Invented, because Jagex's are not recorded anywhere: the bird feed
 *    cupboard's two lines (modelled on the doctors gown cupboard, which is),
 *    the bronze key gate's lines (modelled on the Observatory dungeon gate),
 *    releasing the pigeons with no seed on the tower, and the line on finding
 *    the key. Everything else is Jagex's, from the recorded transcripts.
 *
 *  - The Ardounge wall gateway, object 450, is listed among this quest's
 *    rewards but its transcript gates it on Underground pass, not on this
 *    quest. It is left alone here and belongs to that quest when it is written.
 */
public class Biohazard extends Quest {

    public final static int UID = Quests.BIOHAZARD;

    // ----------------------------------------------------------------- npcs --

    private static final int ELENA = 483;
    private static final int JERICO = 486;
    private static final int OMART = 484;
    private static final int KILRON = 487;
    private static final int DOOR_MOURNER = 492;
    private static final int HOUSE_MOURNER = 502;
    private static final int SICK_MOURNER = 495;
    private static final int NURSE = 500;
    private static final int CHEMIST = 504;
    private static final int CHANCY_RIM = 505, HOPS_RIM = 506, DEVINCI_RIM = 507;
    private static final int CHANCY_VAR = 509, HOPS_VAR = 510, DEVINCI_VAR = 511;
    private static final int GATE_GUARD = 503;
    private static final int GUIDOR = 508, GUIDOR_WIFE = 488;
    private static final int LATHAS = 512;

    // ---------------------------------------------------------------- items --

    private static final int PIGEONS = 799;
    private static final int BIRD_FEED = 800;
    private static final int ROTTEN_APPLES = 801;
    private static final int GOWN = 802;
    private static final int BRONZE_KEY = 803;
    private static final int DISTILLATOR = 804;
    private static final int PRIEST_ROBE = 807, PRIEST_GOWN = 808;
    private static final int HONEY = 809, ETHENEA = 810, BROLINE = 811;
    private static final int SAMPLE = 812;
    private static final int TOUCH_PAPER = 813;
    private static final int AMULET = 826;

    /** In the order the errand boys offer them, which is the transcript's order. */
    private static final int[] VIALS = { ETHENEA, HONEY, BROLINE };
    private static final String[] VIAL_NAMES = { "ethenea", "liquid honey", "sulphuric broline" };

    // -------------------------------------------------------------- scenery --

    private static final int WATCH_TOWER = 494, WATCH_TOWER_X = 622, WATCH_TOWER_Y = 585;
    private static final int FEED_CUPBOARD = 500, FEED_CUPBOARD_SHUT = 499;
    private static final int FEED_CUPBOARD_X = 581, FEED_CUPBOARD_Y = 587;
    private static final int GOWN_CUPBOARD = 510, GOWN_CUPBOARD_SHUT = 509;
    private static final int GOWN_CUPBOARD_X = 648, GOWN_CUPBOARD_Y = 602;
    private static final int COOKING_POT = 502, COOKING_POT_X = 635, COOKING_POT_Y = 564;
    private static final int KEY_GATE = 504, KEY_GATE_X = 631, KEY_GATE_Y = 1514;
    private static final int GOODS_CRATE = 505, GOODS_CRATE_X = 627, GOODS_CRATE_Y = 1514;
    private static final int VARROCK_GATE = 513, VARROCK_GATE_X = 93, VARROCK_GATE_Y = 521;

    private static final int QUARTERS_DOOR = 138, QUARTERS_DOOR_X = 633, QUARTERS_DOOR_Y = 573;
    private static final int GUIDOR_DOOR = 145, GUIDOR_DOOR_X = 83, GUIDOR_DOOR_Y = 534;
    private static final int ELENA_DOOR = 152, ELENA_DOOR_X = 608, ELENA_DOOR_Y = 573;

    /** Doorframe. What an open door looks like, the same as WallObjectAction's. */
    private static final int OPEN_DOOR = 11;

    /** How long a closed cupboard or an opened door stays that way. */
    private static final int SWING_BACK = 5000;

    // ------------------------------------------------------------ landings --

    /** Over the wall from Omart, next to Kilron, inside West Ardougne. */
    private static final int WEST_SIDE_X = 628, WEST_SIDE_Y = 613;
    /** Back over from Kilron, next to Omart, outside the wall. */
    private static final int EAST_SIDE_X = 625, EAST_SIDE_Y = 616;

    private static final int THIEVING = 17;

    // -------------------------------------------------------------- stages --

    private static final int STARTED = 1;      /* Elena asked for the distillator */
    private static final int JERICO_MET = 2;   /* Jerico named Omart */
    private static final int FEED = 4;         /* seed thrown on the watch tower */
    private static final int DISTRACTED = 8;   /* pigeons released, guards busy */
    private static final int CROSSED = 16;     /* been over the wall on the ladder */
    private static final int STEW = 32;        /* rotten apples in the cooking pot */
    private static final int CRATE = 64;       /* the crate has been searched */
    private static final int SAMPLED = 128;    /* Elena handed over sample and vials */
    private static final int PAPER = 256;      /* the chemist handed over touch paper */
    private static final int TRUTH = 512;      /* Guidor found nothing in the sample */
    private static final int TOLD = 1024;      /* Elena sent the player to the king */
    private static final int DONE = 2048;

    /**
     * Every bit at once.
     *
     * Four of them -- JERICO_MET, FEED, DISTRACTED and CROSSED -- are the rope
     * ladder, and the rope ladder is optional: the Plague city sewer pipe still
     * reaches West Ardougne during this quest, so a player may do the whole
     * burglary without ever meeting Omart. That is why the king sets this value
     * outright rather than folding DONE into whatever has been collected. It is
     * also what clears the errand boy bits below, which are scratch.
     */
    private static final int FINISHED = STARTED | JERICO_MET | FEED | DISTRACTED
        | CROSSED | STEW | CRATE | SAMPLED | PAPER | TRUTH | TOLD | DONE;

    /**
     * One per errand boy: he is carrying the vial he was supposed to be given.
     *
     * Above FINISHED and never part of it. Set when the right vial is handed
     * over in Rimmington and cleared when it is handed back in Varrock, so they
     * are normally zero by the time the quest ends; a player who never collects
     * one would otherwise be left one bit short of finishing forever, which is
     * the other reason the king assigns FINISHED rather than or-ing DONE in.
     */
    private static final int CARRY_CHANCY = 4096;
    private static final int CARRY_DEVINCI = 8192;
    private static final int CARRY_HOPS = 16384;

    // ---------------------------------------------------------- per-player --

    /**
     * The vial id each errand boy was actually handed, in the order Chancy,
     * DeVinci, Hops. Not persisted: it only chooses which of two speeches the
     * Varrock half of the pair gives, and losing it costs the speech and
     * nothing else. See the deviation note in the class comment.
     */
    private final int[] given = new int[3];

    public Biohazard(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Biohazard");
        setFinalStage(FINISHED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Second part of an ongoing adventure. Help Elena discover the truth about the infamous ardounge plague. Smuggle test samples across ardounge to Elena's old mentor.");
        setStartPoint("East Ardounge");
        setSpeakTo("Elena");
        setMissionLength("Long");
        requireQuest(Quests.PLAGUE_CITY);
        rewardItem(AMULET, 1);
        rewardExp(THIEVING, 500, 50);

        associateNpc(ELENA);
        associateNpc(JERICO);
        associateNpc(OMART);
        associateNpc(KILRON);
        associateNpc(DOOR_MOURNER);
        associateNpc(HOUSE_MOURNER);
        associateNpc(SICK_MOURNER);
        associateNpc(NURSE);
        associateNpc(CHEMIST);
        associateNpc(CHANCY_RIM);
        associateNpc(HOPS_RIM);
        associateNpc(DEVINCI_RIM);
        associateNpc(CHANCY_VAR);
        associateNpc(HOPS_VAR);
        associateNpc(DEVINCI_VAR);
        associateNpc(GUIDOR);
        associateNpc(GUIDOR_WIFE);
        associateNpc(LATHAS);

        // The guard on the Varrock gate is deliberately not claimed. He has
        // nothing to say on his own; he speaks when the gate is opened, and
        // the gate is the thing that belongs to this quest.

        associateObject(WATCH_TOWER, WATCH_TOWER_X, WATCH_TOWER_Y);
        associateObject(FEED_CUPBOARD, FEED_CUPBOARD_X, FEED_CUPBOARD_Y);
        associateObject(FEED_CUPBOARD_SHUT, FEED_CUPBOARD_X, FEED_CUPBOARD_Y);
        associateObject(GOWN_CUPBOARD, GOWN_CUPBOARD_X, GOWN_CUPBOARD_Y);
        associateObject(GOWN_CUPBOARD_SHUT, GOWN_CUPBOARD_X, GOWN_CUPBOARD_Y);
        associateObject(COOKING_POT, COOKING_POT_X, COOKING_POT_Y);
        associateObject(KEY_GATE, KEY_GATE_X, KEY_GATE_Y);
        associateObject(GOODS_CRATE, GOODS_CRATE_X, GOODS_CRATE_Y);
        associateObject(VARROCK_GATE, VARROCK_GATE_X, VARROCK_GATE_Y);

        associateItem(PIGEONS);

        associateDoor(QUARTERS_DOOR, QUARTERS_DOOR_X, QUARTERS_DOOR_Y);
        associateDoor(GUIDOR_DOOR, GUIDOR_DOOR_X, GUIDOR_DOOR_Y);
        associateDoor(ELENA_DOOR, ELENA_DOOR_X, ELENA_DOOR_Y);
    }

    public void completeQuest() {
        grantRewards();
        Player p = getOwner();
        p.getActionSender().sendMessage("king lathas gives you a magic amulet");
        p.getActionSender().sendMessage("@gre@you have completed the biohazard quest");
    }

    // ------------------------------------------------------------- helpers --

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    /**
     * Turn a bit on.
     *
     * Nothing but STARTED may be the first thing that happens: every other bit
     * is a step of a quest Elena has to have asked for, and a stray trigger
     * from a player who has never spoken to her would otherwise leave the quest
     * started at some stage in the middle of itself.
     */
    private void set(int bit) {
        if (!questStarted() && bit != STARTED) {
            return;
        }
        setStage(questStarted() ? getStage() | bit : bit);
    }

    private void clear(int bit) {
        if (questStarted()) {
            setStage(getStage() & ~bit);
        }
    }

    private void say(String line) {
        getOwner().getActionSender().sendMessage(line);
    }

    /**
     * A line from an npc who is not the one being talked to.
     *
     * A chat bubble if he is there to put one over, and Plague city's prefixed
     * message if he is not.
     */
    private void speak(Npc npc, String line) {
        Player p = getOwner();
        if (npc == null) {
            say("Guard: " + line);
            return;
        }
        p.informOfNpcMessage(new ChatMessage(npc, line, p));
    }

    private boolean holds(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private boolean wearing(int id) {
        return getOwner().getInventory().wielding(id);
    }

    private void take(int id) {
        Player p = getOwner();
        p.getInventory().remove(id, 1);
        p.getActionSender().sendInventory();
    }

    private void give(int id) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, 1));
        p.getActionSender().sendInventory();
    }

    private boolean plagueCityDone() {
        return getOwner().getQuestManager().completed(Quests.PLAGUE_CITY);
    }

    /** Whether the player is dressed as a priest, which takes both halves. */
    private boolean inPriestly() {
        return wearing(PRIEST_ROBE) && wearing(PRIEST_GOWN);
    }

    /** The nearest npc of an id that is in view, or null. */
    private Npc inView(int id) {
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            if (n.getID() == id) {
                return n;
            }
        }
        return null;
    }

    /**
     * Open a door for a moment and step the player to the far side of it.
     *
     * A door facing 0 stands between (x,y) and (x,y-1); one facing 1 stands
     * between (x,y) and (x-1,y). The same as Temple of Ikov's, which is Dragon
     * slayer's, which is WallObjectAction's: the open frame is cosmetic and the
     * crossing is the teleport.
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

    /**
     * Step through a gate that is scenery rather than a door.
     *
     * Both gates in this quest are two tiles tall and block one column: the
     * mourners' faces 0 and separates (631,y) from (630,y), the Varrock one
     * faces 4 and separates (94,y) from (93,y). Passing is the same teleport a
     * door gets, kept on whichever of the gate's two rows the player is stood.
     */
    private void stepThroughGate(GameObject gate, int nearX, int farX) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        int y = p.getY() == gate.getY() + 1 ? gate.getY() + 1 : gate.getY();
        p.teleport(p.getX() >= nearX ? farX : nearX, y, false);
    }

    /**
     * Shut a cupboard, and put it back the way it was found a few seconds later.
     *
     * Only ever called on the placed object, never on the one this spawns: a
     * GameObject built from a Location rather than from a GameObjectLoc invents
     * a loc carrying its own id, so respawning from the shut one's loc would
     * leave the cupboard shut permanently.
     */
    private void closeCupboard(GameObject cupboard, int shut) {
        world.registerGameObject(new GameObject(cupboard.getLocation(), shut,
            cupboard.getDirection(), cupboard.getType()));
        world.delayedSpawnObject(cupboard.getLoc(), SWING_BACK);
    }

    /**
     * Open a shut cupboard again before it swings open on its own.
     *
     * Nothing is scheduled here. A shut cupboard can only exist because
     * closeCupboard put it there, so the respawn of the real one is already
     * queued and will re-register the same thing this does.
     */
    private void reopenCupboard(GameObject cupboard, int open) {
        world.registerGameObject(new GameObject(cupboard.getLocation(), open,
            cupboard.getDirection(), cupboard.getType()));
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
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_COMMAND && ((InvItem) entity).getID() == PIGEONS) {
                releasePigeons();
            }
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.NPC_KILLED) {
            if (npc.getID() == SICK_MOURNER) {
                mournerKilled();
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case ELENA: elena(npc); break;
            case JERICO: jerico(npc); break;
            case OMART: omart(npc); break;
            case KILRON: kilron(npc); break;
            case DOOR_MOURNER: doorMourner(npc); break;
            case HOUSE_MOURNER: houseMourner(npc); break;
            case SICK_MOURNER: sickMourner(npc); break;
            case NURSE: nurse(npc); break;
            case CHEMIST: chemist(npc); break;
            case CHANCY_RIM: errandBoy(npc, 0); break;
            case DEVINCI_RIM: errandBoy(npc, 1); break;
            case HOPS_RIM: errandBoy(npc, 2); break;
            case CHANCY_VAR: collector(npc, 0); break;
            case DEVINCI_VAR: collector(npc, 1); break;
            case HOPS_VAR: collector(npc, 2); break;
            case GUIDOR_WIFE: guidorsWife(npc); break;
            case GUIDOR: guidor(npc); break;
            case LATHAS: lathas(npc); break;
            default: break;
        }
    }

    // --------------------------------------------------------------- doors --

    private void door(QuestTrigger trigger, GameObject door) {
        if (trigger != QuestTrigger.DOOR_ACT1) {
            return;
        }
        if (door.getID() == QUARTERS_DOOR) {
            quartersDoor(door);
        } else if (door.getID() == GUIDOR_DOOR) {
            guidorDoor(door);
        } else if (door.getID() == ELENA_DOOR) {
            elenaDoor(door);
        }
    }

    /**
     * Elena's front door, shut while she is still a prisoner in West Ardougne.
     *
     * Elena is two people: npc 465 behind the plague wall, and npc 483 stood in
     * this house. 483 is spawned from the start of the world like every other
     * npc, so without this the player can walk in on the woman they have not
     * rescued yet, in the house they are about to be told to visit. She has
     * nothing to say before Plague city is finished, so nothing progresses --
     * but seeing her is the spoiler, not talking to her.
     *
     * The condition is Plague city rather than a stage of this quest because
     * the house has to stay open afterwards forever, including for a player who
     * never starts Biohazard.
     */
    private void elenaDoor(GameObject door) {
        if (!plagueCityDone()) {
            say("the door is locked");
            return;
        }
        walkThrough(door);
    }

    /**
     * The way into the mourner quarters.
     *
     * Locked until the stew is spoiled and then guarded by the mourner on the
     * step, who lets a doctor in and nobody else. The gown has to be worn, not
     * carried, and has to be worn again every time.
     */
    private void quartersDoor(GameObject door) {
        if (!has(STEW)) {
            say("the door is locked");
            say("inside you can hear the mourners eating");
            say("you need to distract them from their stew");
            return;
        }
        if (!wearing(GOWN)) {
            say("the mourner is refusing to open the door");
            return;
        }
        say("Mourner: in you go doc");
        say("You go through the door");
        walkThrough(door);
    }

    /** Guidor's sick room. His wife only lets a priest past. */
    private void guidorDoor(GameObject door) {
        if (!inPriestly()) {
            say("Guidor's wife: I'm afraid I'm not letting people see him now");
            return;
        }
        walkThrough(door);
    }

    // ------------------------------------------------------------- scenery --

    private void scenery(QuestTrigger trigger, GameObject object, InvItem used) {
        switch (object.getID()) {
            case WATCH_TOWER: watchTower(trigger, used); return;
            case FEED_CUPBOARD: feedCupboard(trigger, object); return;
            case GOWN_CUPBOARD: gownCupboard(trigger, object); return;
            case FEED_CUPBOARD_SHUT:
            case GOWN_CUPBOARD_SHUT:
                if (trigger == QuestTrigger.OBJECT_ACT1) {
                    say("you open the cupboard");
                    reopenCupboard(object, object.getID() == FEED_CUPBOARD_SHUT
                        ? FEED_CUPBOARD : GOWN_CUPBOARD);
                }
                return;
            case COOKING_POT: cookingPot(trigger, used); return;
            case KEY_GATE: keyGate(trigger, object, used); return;
            case GOODS_CRATE: goodsCrate(trigger); return;
            case VARROCK_GATE: varrockGate(trigger, object); return;
            default: return;
        }
    }

    /**
     * The watch tower over the Ardougne wall.
     *
     * "approach" is all the tower does on its own; the seed goes on it and the
     * pigeons go after the seed.
     */
    private void watchTower(QuestTrigger trigger, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != BIRD_FEED) {
                say("Nothing interesting happens");
                return;
            }
            say("you throw a hand full of seeds onto the Watch tower");
            say("the mourners do not seem to notice");
            set(FEED);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        say("Mourner: keep away civilian");
        say("what's it to you?");
        say("Mourner: the tower's here for your protection");
    }

    /**
     * Releasing the pigeons.
     *
     * They are the item with the command on it -- the pigeon cage, item 798, is
     * never obtainable -- and they only do anything once there is something on
     * the tower for them to go for.
     */
    private void releasePigeons() {
        if (!has(FEED)) {
            say("you had better not release them yet");
            say("there is nothing up there to hold their interest");
            return;
        }
        say("you open the cage");
        say("the pigeons fly towards the watch tower");
        say("they begin pecking at the bird feed");
        say("the mourners are frantically trying to scare the pigeons away");
        take(PIGEONS);
        set(DISTRACTED);
    }

    /** Jerico's cupboard, which is where his bird feed lives. */
    private void feedCupboard(QuestTrigger trigger, GameObject cupboard) {
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("you close the cupboard");
            closeCupboard(cupboard, FEED_CUPBOARD_SHUT);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        say("you search the cupboard");
        if (!questStarted() || completed() || holds(BIRD_FEED)) {
            say("but find nothing of interest");
            return;
        }
        say("inside you find some bird feed");
        give(BIRD_FEED);
    }

    /** nurse sarah's cupboard. The gown only appears once a doctor is wanted. */
    private void gownCupboard(QuestTrigger trigger, GameObject cupboard) {
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("you close the cupboard");
            closeCupboard(cupboard, GOWN_CUPBOARD_SHUT);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        say("you search the cupboard");
        if (!has(STEW) || holds(GOWN)) {
            say("but find nothing of interest");
            return;
        }
        say("inside you find a doctor's gown");
        give(GOWN);
    }

    /** The mourners' stew. */
    private void cookingPot(QuestTrigger trigger, InvItem used) {
        if (trigger != QuestTrigger.ITEM_ON_OBJECT) {
            return;
        }
        if (used == null || used.getID() != ROTTEN_APPLES) {
            say("Nothing interesting happens");
            return;
        }
        take(ROTTEN_APPLES);
        say("you place the rotten apples in the pot");
        say("they quickly dissolve into the stew");
        say("that wasn't very nice");
        set(STEW);
    }

    /**
     * The gate upstairs in the quarters.
     *
     * The key is not consumed -- nothing says it is -- so holding it is what
     * keeps the gate open, and a player who drops it can kill the mourner for
     * another one.
     */
    private void keyGate(QuestTrigger trigger, GameObject gate, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != BRONZE_KEY) {
                say("Nothing interesting happens");
                return;
            }
            say("The gate unlocks");
            stepThroughGate(gate, KEY_GATE_X, KEY_GATE_X - 1);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        if (!holds(BRONZE_KEY)) {
            say("The gate is locked");
            return;
        }
        stepThroughGate(gate, KEY_GATE_X, KEY_GATE_X - 1);
    }

    /**
     * The confiscated goods crate.
     *
     * Tested on the inventory rather than on the stage so that a player who
     * loses the distillator can come back for another one, which is what the
     * transcript's condition says and what Elena's "you haven't" line implies.
     */
    private void goodsCrate(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            return;
        }
        say("you search the crate");
        if (holds(DISTILLATOR) || has(SAMPLED)) {
            say("it's empty");
            return;
        }
        say("and find elena's distillator");
        give(DISTILLATOR);
        set(CRATE);
    }

    /**
     * The guarded gate into south east Varrock.
     *
     * The search is only on the way in and only while the vials matter. He
     * takes one of each vial he finds, which is why the three of them travel
     * with the chemist's errand boys instead.
     */
    private void varrockGate(QuestTrigger trigger, GameObject gate) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        Player p = getOwner();
        boolean goingIn = p.getX() > VARROCK_GATE_X;
        if (goingIn && has(SAMPLED) && !has(TRUTH)) {
            // The guard himself is not claimed -- he has nothing to say when
            // spoken to -- so his two lines come out of the gate. He gets a
            // chat bubble if he is in view and a prefixed message if he is not,
            // which is possible: the gate is clickable from further away than
            // he is visible.
            Npc guard = inView(GATE_GUARD);
            speak(guard, "Halt. I need to conduct a search on you");
            speak(guard, "There have been reports of a someone bringing a virus into Varrock");
            for (int i = 0; i < VIALS.length; i++) {
                if (holds(VIALS[i])) {
                    take(VIALS[i]);
                    say("He takes the vial of " + VIAL_NAMES[i] + " from you");
                }
            }
        }
        stepThroughGate(gate, VARROCK_GATE_X + 1, VARROCK_GATE_X);
    }

    // --------------------------------------------------------------- Elena --

    private void elena(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello elena")
                .npc("hey, how are you?")
                .player("good thanks, yourself?")
                .npc("not bad, let me know when you hear from king lathas again")
                .player("will do")
                .start();
            return;
        }
        if (has(TOLD)) {
            new Conversation(p, npc)
                .player("hello elena")
                .npc("you must go to king lathas immediately")
                .start();
            return;
        }
        if (has(TRUTH)) {
            new Conversation(p, npc)
                .npc("You're back! So what did Guidor say?")
                .player("Nothing")
                .npc("What?")
                .player("He said that there is no plague")
                .npc("So what, this thing has all been a big hoax?")
                .player("Or maybe we're about to uncover something huge")
                .npc("Then I think this thing may be bigger than both of us")
                .player("What do you mean?")
                .npc("I mean that you need to go right to the top")
                .npc("You need to see the King of east Ardougne")
                .then(new Effect() {
                    public void run(Conversation c) {
                        set(TOLD);
                    }
                })
                .start();
            return;
        }
        if (has(SAMPLED)) {
            elenaAgain(npc);
            return;
        }
        if (holds(DISTILLATOR)) {
            elenaHandover(npc);
            return;
        }
        if (!questStarted()) {
            if (!plagueCityDone()) {
                return;
            }
            elenaOpening(npc);
            return;
        }
        if (has(CRATE)) {
            new Conversation(p, npc)
                .npc("so, have you managed to retrieve my distillator?")
                .player("i'm afraid not")
                .npc("Oh, you haven't")
                .npc("People may be dying even as we speak")
                .start();
            return;
        }
        if (has(CROSSED)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("you're back, did you find the distillator?")
                .player("i'm afraid not")
                .npc("i can't test the samples without the distillator")
                .npc("please don't give up until you find it")
                .start();
            return;
        }
        if (has(DISTRACTED)) {
            new Conversation(p, npc)
                .player("elena i've distracted the guards at the watch tower")
                .npc("yes, i saw")
                .npc("quickly meet with jerico's friends and cross the wall")
                .npc("before the pigeons fly off")
                .start();
            return;
        }
        if (has(JERICO_MET)) {
            new Conversation(p, npc)
                .player("hello elena, i've spoken to jerico")
                .npc("was he able to help?")
                .player("he has two friends who will help me cross the wall")
                .player("but first i need to distract the watch tower")
                .npc("hmmm, could be tricky")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello elena")
            .npc("hello brave adventurer")
            .npc("any luck finding the distillator")
            .player("no i'm afraid not")
            .npc("speak to jerico, he will help you to cross the wall")
            .npc("he lives next to the chapel")
            .start();
    }

    private void elenaOpening(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("good to see you, elena")
            .npc("you too, thanks for freeing me")
            .npc("it's just a shame the mourners confiscated my equipment")
            .player("what did they take?")
            .npc("my distillator, I can't test any plague samples without it")
            .npc("they're holding it in the mourner quarters in west ardounge")
            .npc("i must somehow retrieve that distillator")
            .npc("if i am to find a cure for this awful affliction")
            .options(new Choice("i'll try to retrieve it for you", "well, good luck") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("thanks traveller");
                        return;
                    }
                    c.npc("i was hoping you would say that")
                     .npc("unfortunately they discovered the tunnel and filled it in")
                     .npc("we need another way over the wall")
                     .player("any ideas?")
                     .npc("my father's friend jerico is in communication with west ardounge")
                     .npc("he might be able to help")
                     .npc("he lives next to the chapel")
                     .then(new Effect() {
                        public void run(Conversation c) {
                            set(STARTED);
                        }
                    });
                }
            })
            .start();
    }

    /**
     * Handing over the distillator.
     *
     * She takes it and the whole of the second half of the quest comes out of
     * this one conversation: the sample, the three reagents, and the errand to
     * Rimmington and then Varrock.
     */
    private void elenaHandover(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("so, have you managed to retrieve my distillator?")
            .npc("You have - that's great!")
            .npc("Now can you pass me those refraction agents please?")
            .message("You hand Elena the distillator and an assortment of vials")
            .take(DISTILLATOR, 1)
            .player("These look pretty fancy")
            .npc("Well, yes and no. The liquid honey isn't worth so much")
            .npc("But the others are- especially this colourless ethenea")
            .npc("And be careful with the sulphuric broline- it's highly poisonous")
            .player("You're not kidding- I can smell it from here")
            .message("Elena puts the agents through the distillator")
            .npc("I don't understand...the touch paper hasn't changed colour at all")
            .npc("You'll need to go and see my old mentor Guidor. He lives in Varrock")
            .npc("Take these vials and this sample to him")
            .message("elena gives you three vials and a sample in a tin container")
            .give(new InvItem(ETHENEA, 1))
            .give(new InvItem(BROLINE, 1))
            .give(new InvItem(HONEY, 1))
            .give(new InvItem(SAMPLE, 1))
            .npc("But first you'll need some more touch-paper. Go and see the chemist in Rimmington")
            .npc("Just don't get into any fights, and be careful who you speak to")
            .npc("Those vials are fragile, and plague carriers don't tend to be too popular")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(SAMPLED);
                }
            })
            .start();
    }

    private void elenaAgain(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("what are you doing back here")
            .options(new Choice(
                    "I just find it hard to say goodbye sometimes",
                    "I'm afraid I've lost some of the stuff you gave me...",
                    "i've forgotten what i need to do") {
                // "I've you lost" is Jagex's, recorded in Transcript:Elena.
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Yes...I have feelings for you too...")
                         .npc("Now get to work!");
                        return;
                    }
                    if (option == 2) {
                        c.npc("go to rimmington and get some touch paper from the chemist")
                         .npc("use his errand boys to smuggle the vials into varrock")
                         .npc("then go to varrock and take the sample to guidor, my old mentor")
                         .player("ok, i'll get to it");
                        return;
                    }
                    // The transcript names the three vials. The sample is
                    // replaced with them: it is the thing Guidor cannot work
                    // without, it breaks the same ways, and "Elena replaces
                    // your items" does not exclude it.
                    c.npc("That's alright, I've got plenty")
                     .message("Elena replaces your items");
                    if (!holds(ETHENEA)) {
                        c.give(new InvItem(ETHENEA, 1));
                    }
                    if (!holds(BROLINE)) {
                        c.give(new InvItem(BROLINE, 1));
                    }
                    if (!holds(HONEY)) {
                        c.give(new InvItem(HONEY, 1));
                    }
                    if (!holds(SAMPLE)) {
                        c.give(new InvItem(SAMPLE, 1));
                    }
                    c.npc("OK so that's the colourless ethenea...")
                     .npc("Some highly toxic sulphuric broline...")
                     .npc("And some bog-standard liquid honey...")
                     .player("Great. I'll be on my way");
                }
            }.says(1, "I'm afraid I've you lost some of the stuff that you gave me"))
            .start();
    }

    // -------------------------------------------------------------- Jerico --

    private void jerico(Npc npc) {
        Player p = getOwner();
        if (completed() || has(CRATE)) {
            say("jerico is busy looking for his bird feed");
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("can i help you?")
                .player("just passing by")
                .start();
            return;
        }
        if (has(CROSSED)) {
            new Conversation(p, npc)
                .player("hello again jerico")
                .npc("so you've returned traveller")
                .npc("did you get what you wanted")
                .player("not yet")
                .npc("omart will be waiting by the wall")
                .npc("In case you need to cross again")
                .start();
            return;
        }
        if (has(DISTRACTED)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("the guards are distracted by the birds")
                .npc("you must go now")
                .npc("quickly traveller")
                .start();
            return;
        }
        if (has(JERICO_MET)) {
            new Conversation(p, npc)
                .player("hello jerico")
                .npc("hello again")
                .npc("you'll need someway to distract the watch tower")
                .npc("otherwise you'll be caught for sure")
                .player("any ideas?")
                .npc("sorry, try asking omart")
                .npc("i really must get back to feeding the messenger birds")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello jerico")
            .npc("hello, i've been expecting you")
            .npc("elena tells me you need to cross the wall")
            .player("that's right")
            .npc("my messenger pigeons help me communicate with friends over the wall")
            .npc("i have arranged for two friends to aid you with a rope ladder")
            .npc("omart is waiting for you at the southend of the wall")
            .npc("be careful, if the mourners catch you the punishment will be severe")
            .player("thanks jerico")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(JERICO_MET);
                }
            })
            .start();
    }

    // --------------------------------------------------- over and back again --

    private void omart(Npc npc) {
        Player p = getOwner();
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("hello")
                .player("how are you?")
                .npc("fine thanks")
                .start();
            return;
        }
        if (completed() || has(SAMPLED)) {
            new Conversation(p, npc)
                .player("hello omart")
                .npc("hello adventurer")
                .npc("i'm afraid it's too risky to use the ladder again")
                .npc("but I believe that edmond's working on another tunnel")
                .start();
            return;
        }
        if (!has(DISTRACTED)) {
            new Conversation(p, npc)
                .player("omart, jerico said you might be able to help me")
                .npc("he informed me of your problem traveller")
                .npc("i would be glad to help, i have a rope ladder")
                .npc("and my associate, kilron, is waiting on the other side")
                .player("good stuff")
                .npc("unfortunately we can't risk it with the watch tower so close")
                .npc("so first we need to distract the guards in the tower")
                .player("how?")
                .npc("try asking jerico, if he's not too busy with his pigeons")
                .npc("I'll be waiting here for you")
                .start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        if (has(CROSSED)) {
            c.player("hello omart")
             .npc("hello traveller")
             .npc("the guards are still distracted if you wish to cross the wall");
        } else {
            c.npc("well done, the guards are having real trouble with those birds")
             .npc("you must go now traveller, it's your only chance")
             .message("Omart calls to his associate")
             .npc("Kilron!")
             .message("he throws one end of the rope ladder over the wall")
             .npc("go now traveller");
        }
        c.options(new Choice("ok lets do it", "I'll be back soon") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("don't take long")
                     .npc("the mourners will soon be rid of those birds");
                    return;
                }
                climb(c, WEST_SIDE_X, WEST_SIDE_Y);
            }
        })
        .start();
    }

    private void kilron(Npc npc) {
        Player p = getOwner();
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("hello")
                .player("how are you?")
                .npc("busy")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello kilron")
            .npc("hello traveller")
            .npc("do you need to go back over?")
            .options(new Choice("not yet kilron", "yes i do") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("okay, just give me the word");
                        return;
                    }
                    c.npc("okay, quickly now");
                    climb(c, EAST_SIDE_X, EAST_SIDE_Y);
                }
            })
            .start();
    }

    /** The rope ladder, in either direction. */
    private void climb(Conversation c, final int x, final int y) {
        c.message("you climb up the rope ladder")
         .message("and drop down on the other side")
         .then(new Effect() {
            public void run(Conversation c) {
                set(CROSSED);
                c.getPlayer().teleport(x, y, false);
            }
        });
    }

    // ------------------------------------------------------------ mourners --

    /** The mourner on the door of the quarters. */
    private void doorMourner(Npc npc) {
        Player p = getOwner();
        if (!questStarted() || completed() || has(CRATE)) {
            say("the mourner doesn't feel like talking");
            return;
        }
        if (wearing(GOWN)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("oh dear oh dear")
                .npc("i feel terrible, i think it was the stew")
                .player("you should be more careful with your ingredients")
                .npc("there is one mourner who's really sick resting upstairs")
                .npc("you should see to him first")
                .player("ok i'll see what i can do")
                .start();
            return;
        }
        if (has(STEW)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("oh dear oh dear")
                .npc("i feel terrible, i think it was the stew")
                .player("you should be more careful with your ingredients")
                .npc("i need a doctor")
                .npc("the nurses' hut is to the south west")
                .npc("go now and bring us a doctor, that's an order")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .player("are these the mourner quarters?")
            .npc("yes, why?, what do you want?")
            .player("i need to go inside")
            .npc("they'll be busy feasting all day")
            .player("really, even with the food shortages in west ardounge")
            .npc("we've no food shortage, just the civilians")
            .options(new Choice(
                    "can i join the feast?",
                    "you should be ashamed of yourself",
                    "well, enjoy your meal") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("don't be so obsurd")
                         .player("but why not?")
                         .npc("because i don't like your face");
                        return;
                    }
                    if (option == 1) {
                        c.player("there are families here starving, you should be protecting them")
                         .npc("that sounds like a lot of hard work")
                         .npc("i tell you what, i'll give it some consideration while i'm enjoying my stew");
                        return;
                    }
                    c.npc("we will, oh and if you get hungry...")
                     .npc("..there are some rotten apples around the corner - help yourself!");
                }
            })
            .start();
    }

    /** The level 25 mourners inside. A doctor is welcome; nobody else is. */
    private void houseMourner(final Npc npc) {
        Player p = getOwner();
        if (wearing(GOWN)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello doc, i feel terrible")
                .npc("i think it was the stew")
                .player("be more careful with your ingredients next time")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("how did you get in here?")
            .npc("this is a restricted area")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.stop();
                    npc.attackPlayer(c.getPlayer());
                }
            })
            .start();
    }

    /**
     * The level 22 mourner upstairs.
     *
     * Every one of the three answers ends the same way, because none of them is
     * anything a doctor would say. Attacking him without a word does just as
     * well and is what most players did.
     */
    private void sickMourner(final Npc npc) {
        Player p = getOwner();
        if (!questStarted() || completed() || has(CRATE)) {
            say("the mourner is sick");
            say("he doesn't feel like talking");
            return;
        }
        new Conversation(p, npc)
            .player("hello there")
            .npc("you're here at last")
            .npc("i don't know what i've eaten")
            .npc("but i feel like i'm on death's door")
            .player("hmm... interesting, sounds like food poisoning")
            .npc("yes, i'd figured that out already")
            .npc("what can you give me to help")
            .options(new Choice(
                    "just hold your breath and count to ten",
                    "the best i can do is pray for you",
                    "there's nothing i can do, it's fatal") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("what, how will that help?")
                         .npc("what kind of doctor are you?")
                         .player("erm .. i'm new, i just started");
                    } else if (option == 1) {
                        c.npc("prey for me?");
                    } else {
                        c.npc("no, i'm too young to die")
                         .npc("i've never even had a girlfriend")
                         .player("that's life for you")
                         .npc("wait a minute, where's your equipment?")
                         .player("it's..erm , at home");
                    }
                    c.npc("you're no doctor");
                    if (option == 1) {
                        c.npc("an impostor");
                    }
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            c.stop();
                            npc.attackPlayer(c.getPlayer());
                        }
                    });
                }
            })
            .start();
    }

    /**
     * The bronze key.
     *
     * NPC_KILLED fires after the npc has been unregistered, so there is nothing
     * to say it to; the key simply appears, which is how vanilla describes it.
     */
    private void mournerKilled() {
        if (!questStarted() || completed() || holds(BRONZE_KEY) || has(SAMPLED)) {
            return;
        }
        say("@gre@you find a bronze key on the mourner");
        give(BRONZE_KEY);
    }

    // --------------------------------------------------------- nurse sarah --

    private void nurse(Npc npc) {
        Player p = getOwner();
        if (!questStarted() || completed() || has(CRATE)) {
            say("nurse sarah doesn't feel like talking");
            return;
        }
        if (has(STEW)) {
            new Conversation(p, npc)
                .player("hello nurse")
                .npc("oh hello there")
                .npc("im afraid i can't stop and talk")
                .npc("a group of mourners have became ill with food poisoning")
                .npc("i need to go over and see what i can do")
                .player("hmmm, strange that!")
                .start();
            return;
        }
        if (!has(CROSSED)) {
            say("nurse sarah doesn't feel like talking");
            return;
        }
        new Conversation(p, npc)
            .player("hello nurse")
            .npc("i don't know how much longer i can cope here")
            .player("what? is the plague getting to you?")
            .npc("no, strangely enough the people here don't seem to be affected")
            .npc("it's just the awful living conditions that are making people ill")
            .player("i was under the impression that every one here was affected")
            .npc("me too, but it doesn't seem to be the case")
            .start();
    }

    // ------------------------------------------------------------- chemist --

    private void chemist(Npc npc) {
        Player p = getOwner();
        if (completed() || !has(SAMPLED)) {
            say("The chemist is busy at the moment");
            return;
        }
        if (has(PAPER)) {
            Conversation c = new Conversation(p, npc)
                .player("hello again")
                .npc("oh hello, do you need more touch paper?");
            if (holds(TOUCH_PAPER)) {
                c.player("no i just wanted to say hello")
                 .npc("oh, ok then ... hello")
                 .player("hi");
            } else {
                c.player("yes please")
                 .npc("ok there you go")
                 .message("the chemist gives you some touch paper")
                 .give(new InvItem(TOUCH_PAPER, 1));
            }
            c.start();
            return;
        }
        if (!holds(SAMPLE) || !holds(ETHENEA) || !holds(BROLINE) || !holds(HONEY)) {
            say("The chemist is busy at the moment");
            return;
        }
        new Conversation(p, npc)
            .npc("Sorry, I'm afraid we're just closing now, you'll have to come back another time")
            .options(new Choice(
                    "This can't wait,I'm carrying a plague sample that desperately needs analysis",
                    "It's OK I'm Elena's friend") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        confiscate(c);
                        return;
                    }
                    c.npc("Oh, well that's different then. Must be pretty important to come all this way")
                     .npc("How's everyone doing there anyway? Wasn't there was some plague scare")
                     .options(new Choice(
                            "that's why I'm here: I need some more touch paper for this plague sample",
                            "Who knows... I just need some touch paper for a guy called Guidor") {
                        public void picked(int option, Conversation c) {
                            if (option == 0) {
                                confiscate(c);
                                return;
                            }
                            c.npc("Guidor? This one's on me then- the poor guy. Sorry about the interrogation")
                             .npc("It's just that there's been rumours of a man travelling with a plague on him")
                             .npc("They're even doing spot checks in Varrock: it's a pharmeceutical disaster")
                             .player("Oh right...so am I going to be OK carrying these three vials with me?")
                             .npc("With touch paper as well? You're asking for trouble")
                             .npc("You'd be better using my errand boys outside- give them a vial each")
                             .npc("They're not the most reliable people in the world")
                             .npc("One's a painter, one's a gambler, and one's a drunk")
                             .npc("Still, if you pay peanuts you'll get monkeys, right?")
                             .npc("And it's better than entering Varrock with half a laborotory in your napsack")
                             .npc("OK- thanks for your help, I know that Elena appreciates it")
                             .npc("Yes well don't stand around here gassing")
                             .npc("You'd better hurry if you want to see Guidor")
                             .npc("He won't be around for much longer")
                             .message("He gives you the touch paper")
                             .give(new InvItem(TOUCH_PAPER, 1))
                             .then(new Effect() {
                                public void run(Conversation c) {
                                    set(PAPER);
                                }
                            });
                        }
                    });
                }
            })
            .start();
    }

    /** Mentioning the sample to a chemist loses the sample. */
    private void confiscate(Conversation c) {
        c.npc("You idiot! A plague sample should be confined to a lab")
         .npc("I'm taking it off you- I'm afraid it's the only responsible thing to do")
         .message("He takes the plague sample from you")
         .take(SAMPLE, 1);
    }

    // --------------------------------------------------------- errand boys --

    /** Chancy the gambler, DeVinci the painter, Hops the drunk, in that order. */
    private static final int[] CARRY = { CARRY_CHANCY, CARRY_DEVINCI, CARRY_HOPS };
    /** The vial each of them can be trusted with, in the same order. */
    private static final int[] TRUSTED = { HONEY, ETHENEA, BROLINE };

    private boolean carrying(int boy) {
        return has(CARRY[boy]) || this.given[boy] != 0;
    }

    /** The Rimmington half of the pair, who takes a vial to Varrock. */
    private void errandBoy(Npc npc, final int boy) {
        Player p = getOwner();
        if (completed() || !has(PAPER) || has(TRUTH)) {
            idle(boy, true);
            return;
        }
        if (carrying(boy)) {
            Conversation c = new Conversation(p, npc);
            if (boy == 0) {
                c.npc("look, I've got your vial, but I'm not taking two")
                 .npc("I always like to play the percentages");
            } else if (boy == 1) {
                c.npc("Oh, it's you again")
                 .npc("Please don't distract me now, I'm contemplating the sublime");
            } else {
                c.npc("I suppose I'd better get going")
                 .npc("I'll meet you at the The dancing donkey inn");
            }
            c.start();
            return;
        }
        Conversation c = new Conversation(p, npc);
        if (boy == 0) {
            c.player("Hello, I've got a vial for you to take to Varrock")
             .npc("Tssch... that chemist asks a lot for the wages he pays")
             .player("Maybe you should ask him for more money")
             .npc("Nah...I just use my initiative here and there");
        } else if (boy == 1) {
            c.player("Hello.i hear you're an errand boy for the chemist")
             .npc("Well that's my day job yes")
             .npc("But I don't necessarily define my identity in such black and white terms")
             .player("Good for you")
             .player("Now can you take a vial to Varrock for me?")
             .npc("Go on then");
        } else {
            c.player("Hi,I've got something for you to take to Varrock")
             .npc("Sounds like pretty thirsty work")
             .player("Well, there's a pub in Varrock if you're desperate")
             .npc("Don't worry, I'm a pretty resourceful fellow you know");
        }
        c.options(new Choice(
                "You give him the vial of ethenea",
                "You give him the vial of liquid honey",
                "You give him the vial of sulphuric broline") {
            public void picked(int option, Conversation c) {
                handOver(c, boy, option);
            }
        })
        .start();
    }

    private void handOver(Conversation c, final int boy, final int slot) {
        final int vial = VIALS[slot];
        if (!holds(vial)) {
            // Hops names the vial he has not been given; the other two do not.
            c.message(boy == 2
                ? "You have no " + VIAL_NAMES[slot] + " to give"
                : "You can't give him what you don't have");
            return;
        }
        c.message("You give him the vial of " + VIAL_NAMES[slot])
         .take(vial, 1)
         .then(new Effect() {
            public void run(Conversation c) {
                Biohazard.this.given[boy] = vial;
                if (vial == TRUSTED[boy]) {
                    set(CARRY[boy]);
                }
            }
        });
        if (boy == 0) {
            c.player("Right.I'll see you later in the dancing donkey inn")
             .npc("Be lucky");
        } else if (boy == 1) {
            c.npc("OK. We're meeting at the dancing donkey in Varrock right?")
             .player("That's right.");
        } else {
            c.player("OK. I'll see you in Varrock")
             .npc("Sure. I'm a regular at the The dancing donkey inn as it happens");
        }
    }

    /** The Varrock half of the pair, in the Dancing Donkey Inn. */
    private void collector(Npc npc, final int boy) {
        Player p = getOwner();
        if (completed() || !has(PAPER)) {
            idle(boy, false);
            return;
        }
        if (has(CARRY[boy])) {
            Conversation c = new Conversation(p, npc);
            if (boy == 0) {
                c.player("Hi.Thanks for doing that")
                 .npc("No problem")
                 .message("He gives you the vial of liquid honey")
                 .give(new InvItem(HONEY, 1))
                 .npc("Next time give me something more valuable")
                 .npc("I couldn't get anything for this on the blackmarket")
                 .player("That was the idea");
            } else if (boy == 1) {
                c.npc("Hello again")
                 .npc("I hope your journey was as pleasant as mine")
                 .player("Well, it's always sunny in Runescape, as they say")
                 .npc("OK. Here it is")
                 .message("He gives you the vial of ethenea")
                 .give(new InvItem(ETHENEA, 1))
                 .player("Thanks. You've been a big help");
            } else {
                c.player("Hello. How was your journey?")
                 .npc("Pretty thirst-inducing actually...")
                 .player("Please tell me that you haven't drunk the contents")
                 .npc("Oh the gods no! What do you take me for?")
                 .npc("Besides, the smell kind of put me off")
                 .npc("Here's your vial anyway")
                 .message("He gives you the vial of sulphuric broline")
                 .give(new InvItem(BROLINE, 1))
                 .player("Thanks. I'll leave you to your drink now");
            }
            c.then(new Effect() {
                public void run(Conversation c) {
                    clear(CARRY[boy]);
                    Biohazard.this.given[boy] = 0;
                }
            })
            .start();
            return;
        }
        if (this.given[boy] == 0) {
            idle(boy, false);
            return;
        }
        Conversation c = new Conversation(p, npc);
        if (boy == 0) {
            c.player("Hi.Thanks for doing that")
             .npc("No problem. I've got some money for you actually")
             .player("What do you mean?")
             .npc("Well it turns out that that potion you gave me was quite valuable...")
             .player("What?")
             .npc("And I know that I probably shouldn't have sold it...")
             .npc("But some friends and I were having a little wager- the odds were just too good")
             .player("You sold my vial and gambled with the money?")
             .npc("Actually, yes... but praise be to Saradomin, because I won!")
             .npc("So all's well that ends well right?")
             .options(new Choice(
                    "No. Nothing could be further from the truth",
                    "You have no idea of what you have just done") {
                public void picked(int option, Conversation c) {
                    c.npc(option == 0
                        ? "Well there's no pleasing some people"
                        : "Ignorance is bliss I'm afraid");
                }
            });
        } else if (boy == 1) {
            c.npc("Hello again")
             .npc("I hope your journey was as pleasant as mine")
             .player("Yep. Anyway, I'll take the package off you now")
             .npc("Package? That's a funny way to describe a liquid of such exquisite beauty")
             .options(new Choice(
                    "I'm getting a bad feeling about this",
                    "Just give me the stuff now please") {
                public void picked(int option, Conversation c) {
                    c.player("You do still have it don't you?")
                     .npc("Absolutely")
                     .npc("Its' just not stored in a vial anymore")
                     .player("What?")
                     .npc("Instead it has been liberated")
                     .npc("And it now gleams from the canvas of my latest epic:")
                     .npc("The Majesty of Varrock")
                     .player("That's great")
                     .player("Thanks to you I'll have to walk back to East Ardougne to get another vial")
                     .npc("Well you can't put a price on art");
                }
            });
        } else {
            c.player("Hello. How was your journey?")
             .npc("Pretty thirst-inducing actually...")
             .player("Please tell me that you haven't drunk the contents")
             .npc("Of course I can tell you that I haven't drunk the contents")
             .npc("But I'd be lying")
             .npc("Sorry about that me old mucker- can I get you a drink?")
             .player("No, I think you've done enough for now");
        }
        c.then(new Effect() {
            public void run(Conversation c) {
                Biohazard.this.given[boy] = 0;
            }
        })
        .start();
    }

    /** What each of the six of them says when there is nothing to say. */
    private void idle(int boy, boolean rimmington) {
        if (boy == 0) {
            say(rimmington ? "Chancy doesn't feel like talking" : "chancy doesn't feel like talking");
        } else if (boy == 1) {
            say(rimmington
                ? "Devinci does not feel sufficiently moved to talk"
                : "devinci doesn't feel like talking");
        } else {
            say(rimmington ? "He is not in a fit state to talk" : "Hops doesn't feel like talking");
        }
    }

    // -------------------------------------------------------------- Guidor --

    private void guidorsWife(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("oh hello, i can't chat now")
                .npc("i have to keep an eye on my husband")
                .npc("he's very ill")
                .player("i'm sorry to hear that")
                .start();
            return;
        }
        if (!has(SAMPLED)) {
            // She has a talk-to option and nothing to say before the quest.
            return;
        }
        if (has(TRUTH)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("hello there")
                .npc("i fear guidor may not be long for this world")
                .start();
            return;
        }
        if (inPriestly()) {
            new Conversation(p, npc)
                .npc("Father, thank heavens you're here. My husband is very ill")
                .npc("Perhaps you could go and perform his final ceremony")
                .player("I'll see what I can do")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("Hello, I'm a friend of Elena, here to see Guidor")
            .npc("I'm afraid...(she sobs)... that Guidor is not long for this world")
            .npc("So I'm not letting people see him now")
            .player("I'm really sorry to hear about Guidor...")
            .player("but I do have some very important business to attend to")
            .npc("You heartless rogue. What could be more important than Guidor's life?")
            .npc("...A life spent well, if not always wisely...")
            .npc("I just hope that Saradomin shows mercy on his soul")
            .player("Guidor is a religious man?")
            .npc("Oh god no. But I am")
            .npc("if only i could get him to see a priest")
            .start();
    }

    private void guidor(Npc npc) {
        Player p = getOwner();
        if (completed() || has(TRUTH)) {
            new Conversation(p, npc)
                .player("hello again guidor")
                .npc("well hello traveller")
                .npc("i still can't understand why they would lie about the plague")
                .player("it's strange, anyway how are you doing?")
                .npc("i'm hanging in there")
                .player("good for you")
                .start();
            return;
        }
        if (!has(SAMPLED)) {
            return;
        }
        new Conversation(p, npc)
            .player("Hello,you must be Guidor. I understand that you are unwell")
            .npc("Is my wife asking priests to visit me now?")
            .npc("I'm a man of science, for god's sake!")
            .npc("Ever since she heard rumours of a plague carrier travelling from Ardougne")
            .npc("she's kept me under house arrest")
            .npc("Of course she means well, and I am quite frail now...")
            .npc("So what brings you here?")
            .options(new Choice(
                    "I've come to ask your assistance in stopping a plague that could kill thousands",
                    "Oh, nothing, I was just going to bless your room, and I've done that now. Goodbye") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        return;
                    }
                    c.player("Well it's funny you should ask actually...")
                     .npc("So you're the plague carrier!")
                     .options(new Choice(
                            "No! Well, yes... but no exactly. It's contained in a sealed unit from elena",
                            "I've been sent by your old pupil Elena, she's trying to halt the virus") {
                        public void picked(int option, Conversation c) {
                            analyse(c);
                        }
                    }.says(0, "No! Well, yes... but not exactly. It's contained in a sealed unit from elena"));
                }
            })
            .start();
    }

    /**
     * The test itself.
     *
     * Guidor asks for the sample, then the three reagents, then the touch
     * paper, and stops at the first one that is missing. The checks are made
     * here, when the branch is taken, so they read the inventory the player
     * actually walked in with.
     */
    private void analyse(Conversation c) {
        c.npc("Elena eh?")
         .player("Yes. She wants you to analyse it")
         .player("You might be the only one that can help")
         .npc("Right then. Sounds like we'd better get to work!");
        if (!holds(SAMPLE)) {
            c.npc("Seems like you don't actually HAVE the plague sample")
             .npc("It's a long way to come empty-handed...")
             .npc("And quite a long way back too");
            return;
        }
        c.player("I have the plague sample")
         .npc("Now I'll be needing some liquid honey,some sulphuric broline,and then...")
         .player("...some ethenea?")
         .npc("Indeed!");
        if (!holds(HONEY) || !holds(BROLINE) || !holds(ETHENEA)) {
            c.npc("Look,I need all three reagents to test the plague sample")
             .npc("Come back when you've got them");
            return;
        }
        if (!holds(TOUCH_PAPER)) {
            c.npc("Oh. You don't have any touch-paper")
             .npc("And so I won't be able to help you after all");
            return;
        }
        c.message("You give him the vials and the touch paper")
         .take(SAMPLE, 1)
         .take(HONEY, 1)
         .take(BROLINE, 1)
         .take(ETHENEA, 1)
         .take(TOUCH_PAPER, 1)
         .npc("Now I'll just apply these to the sample and...")
         .npc("I don't get it...the touch paper has remained the same")
         .options(new Choice(
                "That's why Elena wanted you to do it- because she wasn't sure what was happening",
                "So what does that mean exactly?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("That's why Elena wanted you to do it- because she wasn't sure what was happening");
                }
                c.npc("Well that's just it. Nothing has happened")
                 .npc("I don't know what this sample is, but it certainly isn't toxic")
                 .player("So what about the plague?")
                 .npc("Don't you understand, there is no plague!")
                 .npc("I'm very sorry, I can see that you've worked hard for this...")
                 .npc("...but it seems that someone has been lying to you")
                 .npc("The only question is...")
                 .npc("...why?")
                 .then(new Effect() {
                    public void run(Conversation c) {
                        set(TRUTH);
                    }
                });
            }
        });
    }

    // --------------------------------------------------------- king Lathas --

    private void lathas(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            // Underground pass starts here. Lathas belongs to both quests, so
            // both are dispatched when he is talked to; this one falls silent
            // the moment Biohazard is over and UndergroundPass takes the king.
            // @share npc 512 with UndergroundPass
            return;
        }
        if (!has(TOLD)) {
            say("the king is too busy to talk");
            return;
        }
        new Conversation(p, npc)
            .player("I assume that you are the King of east Ardougne?")
            .npc("You assume correctly- but where do you get such impertinence?")
            .player("I get it from finding out that the plague is a hoax")
            .npc("A hoax, I've never heard such a ridiculous thing...")
            .player("I have evidence- from Guidor in Varrock")
            .npc("Ah... I see. Well then you are right about the plague")
            .npc("But I did it for the good of my people")
            .player("When is it ever good to lie to people like that?")
            .npc("When it protects them from a far greater danger- a fear too big to fathom")
            .options(new Choice(
                    "I don't understand...",
                    "Well I've wasted enough of my time here") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("No time is ever wasted- thanks for all you've done");
                        return;
                    }
                    c.npc("Their King, tyras, journeyed out to the West, on a voyage of discovery")
                     .npc("But he was captured by the Dark Lord")
                     .npc("The Dark Lord agreed to spare his life, but only on one condition...")
                     .npc("That he would drink from the chalice of eternity")
                     .player("So what happened?")
                     .npc("The chalice corrupted him. He joined forces with the Dark Lord...")
                     .npc("...The embodiment of pure evil, banished all those years ago...")
                     .npc("And so I erected this wall, not just to protect my people")
                     .npc("But to protect all the people of Runescape")
                     .npc("Because now, with the King of West Ardougne...")
                     .npc("...The dark lord has an ally on the inside")
                     .npc("So I'm sorry that I lied about the plague")
                     .npc("I just hope that you can understand my reasons")
                     .player("Well at least I know now. But what can we do about it?")
                     .npc("Nothing at the moment")
                     .npc("I'm waiting for my scouts to come back")
                     .npc("They will tell us how we can get through the mountains")
                     .npc("When this happens, can I count on your support?")
                     .player("Absolutely")
                     .npc("Thank the gods. Let me give you this amulet")
                     .npc("Think of it as a thank you, for all that you have done")
                     .npc("...but know that one day it may turn red")
                     .npc("...Be ready for this moment")
                     .npc("And to help, I give you permission to use my training area")
                     .npc("It's located just to the north west of ardounge")
                     .npc("There you can prepare for the challenge ahead")
                     .player("OK. There's just one thing I don't understand")
                     .player("How do you know so much about King Tyras")
                     .npc("How could I not do?")
                     .npc("He was my brother")
                     .then(new Effect() {
                        public void run(Conversation c) {
                            setStage(FINISHED);
                        }
                    });
                }
            })
            .start();
    }
}
