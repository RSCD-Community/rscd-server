import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.SingleEvent;
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
 * Witch's house. The first members' quest, released 27 February 2002.
 *
 * A boy in Taverley has kicked his ball over the wall of Nora T Hag's garden
 * and she has locked it in her shed. Getting it back means letting yourself
 * into her house, finding the magnet in her cellar, sticking it to her rat so
 * that the rat trips the magnetic lock on the back door, and then killing the
 * thing she keeps in the shed -- four times over, because it changes shape.
 *
 * Every id this quest touches occurs exactly once in the world, which is
 * unusual and makes it unusually safe: doors 69 to 73, scenery 255, 256 and
 * 259, npcs 240 to 242 and 244 to 247, items 537 to 540. Nothing here is
 * shared with another building, so claiming an id claims the right thing.
 *
 * The geography, read off the landscape's own wall bytes. Walls run on the
 * west edge of the tile they are recorded on, so:
 *
 *     x >= 358   the house           front door 69 at (363,494)
 *     x 356..357 a two-tile strip    inner door 70 at (358,495)
 *     x <= 355   the garden          doors 71 (356,495) and 72 (356,492)
 *     x 350..351, y 490..491         the shed, door 73 at (351,492)
 *
 * That strip between the house's inner wall and its outer wall is the "small
 * room" the walkthrough tells you to hide in, and it is why there are two
 * doors side by side in the outer wall: the rat opens 72, you come back in
 * through 71. Which of the two the rat opens is the one thing here that the
 * transcript and the walkthrough do not settle between them, and 72 is the
 * reading taken -- it is the door you would reach first coming out of the
 * house, and it leaves 71 as the door "next to" it that the walkthrough sends
 * you back through.
 *
 * Three deviations, all recorded rather than invented around:
 *
 *  - Nora T Hag has no NpcLoc, in RSCD's world or in Jagex's own 2001 data.
 *    She is not a resident; she is summoned by trying the shed door and put
 *    away again afterwards, which is the only behaviour consistent with a
 *    quest npc that nobody has ever met standing in her kitchen.
 *  - She catches you by where you are standing when she arrives, not by
 *    walking a route and looking. RSCD has no npc line of sight.
 *  - "If you flee from the battle, you must restart the fight" happens by
 *    itself and is not coded: the second, third and fourth forms are spawned
 *    with no respawn, so walking away leaves only the first form, which is
 *    Jagex's own permanent spawn at (351,490).
 *
 * The front door key is Jagex's own oddity and is left alone. The wiki says it
 * is under the door mat, and searching the mat does give you one; but Jagex's
 * ItemLoc also respawns key 538 upstairs at (362,1439) every thirty seconds,
 * next to the diary on the table at (362,1437). Both are in the world.
 *
 * Dialogue is Jagex's, from the recorded transcripts. Nora has only her two
 * three-line speeches, which is all she ever had.
 */
public class WitchsHouse extends Quest {

    public final static int UID = Quests.WITCHS_HOUSE;

    private static final int STARTED = 1;
    /** The rat is wearing the magnet and the back door has been tripped. */
    private static final int RAT_DONE = 2;
    /** Nora has come out and unlocked the shed. */
    private static final int SHED_OPEN = 3;
    private static final int FINISHED = 4;

    private static final int BOY = 240;
    private static final int RAT = 241;
    private static final int WITCH = 242;
    /** The shapeshifter's four forms, in the order it wears them. */
    private static final int[] FORMS = { 244, 245, 246, 247 };

    /** Nora's diary, a ground spawn on the upstairs table. Reading it is
        optional -- see readDiary(). */
    private static final int DIARY = 537;
    private static final int FRONT_DOOR_KEY = 538;
    private static final int BALL = 539;
    private static final int MAGNET = 540;
    private static final int CHEESE = 319;
    private static final int LEATHER_GLOVES = 16;
    private static final int ICE_GLOVES = 556;

    private static final int DOOR_MAT = 255;
    private static final int MAT_X = 363, MAT_Y = 494;
    private static final int GATE = 256;
    private static final int GATE_X = 363, GATE_Y = 3325;
    private static final int CUPBOARD_SHUT = 258;
    private static final int CUPBOARD_OPEN = 259;
    private static final int CUPBOARD_X = 362, CUPBOARD_Y = 3328;

    private static final int FRONT_DOOR = 69;
    private static final int BACK_DOOR = 72;
    private static final int SHED_DOOR = 73;
    /** The generic swinging-door frame every quest uses to show a door open. */
    private static final int OPEN_DOOR = 11;

    /** The strip between the house's two west walls. */
    private static final int HIDE_MIN_X = 356, HIDE_MAX_X = 357;
    private static final int HIDE_MIN_Y = 491, HIDE_MAX_Y = 497;

    /** Where the shapeshifter lives, and where each form replaces the last. */
    private static final int SHED_X = 351, SHED_Y = 490;

    /**
     * Set when the fourth form dies, cleared when the ball is lifted. The
     * first form is Jagex's permanent spawn and respawns on its own timer, so
     * "no form alive" cannot be the test for having beaten the thing -- it can
     * be back before the fight it replaced is even over.
     */
    private static final String SHIFTER_FLAG = "witch_shifter_beaten";

    /** How long the rat stays out for the cheese. */
    private static final int RAT_STAYS = 15000;

    /** Nora comes out of the house and stands between it and the shed. */
    private static final int WITCH_X = 353, WITCH_Y = 492;
    /** How long she is out for, and how long you have to be hidden by. */
    private static final int WITCH_DELAY = 6000;
    private static final int WITCH_LEAVES = 12000;

    /** Where she puts you if she finds you: outside the crafting guild. */
    private static final int GUILD_X = 348, GUILD_Y = 606;

    private static final int SHOCK_DAMAGE = 6;
    private static final int HITS = 3; /* skill index */

    public WitchsHouse(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Witch's house");
        setFinalStage(FINISHED);
        associateNpc(BOY);
        associateNpc(RAT);
        associateNpc(WITCH);
        for (int i = 0; i < FORMS.length; i++) {
            associateNpc(FORMS[i]);
        }
        associateDroppedItem(CHEESE);
        associateItem(DIARY);
        associateItem(BALL);
        associateObject(DOOR_MAT);
        associateObject(GATE);
        associateObject(CUPBOARD_SHUT);
        associateObject(CUPBOARD_OPEN);
        associateDoor(FRONT_DOOR);
        associateDoor(BACK_DOOR);
        associateDoor(SHED_DOOR);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("A young boy who lives in Taverley has kicked his ball into the garden of a scary old lady. He asks you to get it back for him. This proves more difficult than it first sounds.");
        setStartPoint("Taverley");
        setSpeakTo("Boy");
        setMissionLength("Medium");
        require("Must kill a mystery lvl-54 monster");
        rewardExp(HITS, 325, 150);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Witch's house quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_TALK) {
                talkTo(npc);
            } else if (trigger == QuestTrigger.NPC_KILLED) {
                killed(npc);
            }
            return;
        }
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (object.getType() != 0) {
                door(trigger, object);
            } else {
                scenery(object);
            }
            return;
        }
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_DROP
                    && ((InvItem) entity).getID() == CHEESE) {
                cheeseDropped();
            } else if (trigger == QuestTrigger.ITEM_COMMAND
                    && ((InvItem) entity).getID() == DIARY) {
                readDiary();
            }
        }
    }

    /*
     * QuestManager always calls this three-argument form, whatever the
     * trigger, so anything it does not recognise must fall through to the
     * one above -- without that fall-through the boy, the doors and all the
     * rest of this quest are unreachable, which they were.
     */
    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_NPC && entity instanceof Npc
                && ((Npc) entity).getID() == RAT
                && used != null && used.getID() == MAGNET) {
            magnetOnRat((Npc) entity);
            return;
        }
        triggerEntity(trigger, entity);
    }

    // ---------------------------------------------------------------- boy --

    private void talkTo(Npc npc) {
        switch (npc.getID()) {
        case BOY:
            talkToBoy(npc);
            break;
        case WITCH:
            /*
             * She only ever appears with a script already running, so whatever
             * she has to say she has said. Clicking her says it again.
             */
            (caught() ? scolding(npc) : greeting(npc)).start();
            break;
        default:
            /* The rat has nothing to say and neither does the shapeshifter. */
            getOwner().getActionSender().sendMessage("The " + npc.getDef().getName()
                + " is not interested in talking");
        }
    }

    private void talkToBoy(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Thankyou for getting my ball back")
                .start();
            return;
        }
        if (questStarted()) {
            if (p.getInventory().countId(BALL) > 0) {
                new Conversation(p, npc)
                    .player("Hi I have got your ball back")
                    .player("It was harder than I thought it would be")
                    .npc("Thankyou very much")
                    .take(BALL, 1)
                    .then(new Effect() {
                        public void run(Conversation c) {
                            setStage(getFinalStage());
                        }
                    })
                    .start();
                return;
            }
            new Conversation(p, npc)
                .npc("Have you got my ball back yet?")
                .player("Not yet")
                .npc("Well it's in the shed in that garden")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("Hello young man")
            .options(new Choice("What's the matter?",
                                "Well if you're not going to answer, I'll go") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("I've kicked my ball over that wall, into that garden")
                     .npc("The old lady who lives there is scary")
                     .npc("She's locked the ball in her wooden shed")
                     .npc("Can you get my ball back for me please")
                     .options(new Choice("Ok I'll see what I can do",
                                         "Get it back yourself") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 return;
                             }
                             c.npc("Thankyou")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      setStage(STARTED);
                                  }
                              });
                         }
                     });
                }
            })
            .start();
    }

    // ----------------------------------------------------------- scenery --

    private void scenery(GameObject object) {
        switch (object.getID()) {
        case DOOR_MAT:
            searchMat(object);
            break;
        case GATE:
            openGate(object);
            break;
        case CUPBOARD_SHUT:
        case CUPBOARD_OPEN:
            searchCupboard(object);
            break;
        }
    }

    /**
     * The mat outside the front door. Its only command is "search", and Jagex
     * put the key under it, so anyone who searches it gets one -- the quest is
     * not a condition. A player who already has a key is told the mat is empty
     * rather than handed a second.
     */
    private void searchMat(GameObject object) {
        Player p = getOwner();
        if (object.getX() != MAT_X || object.getY() != MAT_Y) {
            p.getActionSender().sendMessage("You find nothing under the mat");
            return;
        }
        p.getActionSender().sendMessage("You look under the mat");
        if (p.getInventory().countId(FRONT_DOOR_KEY) > 0) {
            p.getActionSender().sendMessage("There is nothing else there");
            return;
        }
        if (!p.getInventory().canHold(new InvItem(FRONT_DOOR_KEY, 1))) {
            p.getActionSender().sendMessage("You don't have room for it");
            return;
        }
        p.getInventory().add(new InvItem(FRONT_DOOR_KEY, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You find a key");
    }

    /**
     * The cellar gate. It carries the current that runs the house's locks, so
     * it wants insulation on your hands. Ice gloves work as well as leather
     * ones, which is how the real game had it.
     */
    private void openGate(GameObject object) {
        Player p = getOwner();
        if (object.getX() != GATE_X || object.getY() != GATE_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        boolean insulated = p.getInventory().wielding(LEATHER_GLOVES)
                         || p.getInventory().wielding(ICE_GLOVES);
        if (!insulated) {
            p.getActionSender().sendMessage("You reach out to open the gate");
            p.getActionSender().sendMessage("@red@You get a nasty electric shock");
            p.setCurStat(HITS, Math.max(0, p.getCurStat(HITS) - SHOCK_DAMAGE));
            p.getActionSender().sendStat(HITS);
            return;
        }
        p.getActionSender().sendMessage("Your gloves protect you from the current");
        p.getActionSender().sendSound("opendoor");
        world.unregisterGameObject(object);
        world.delayedSpawnObject(object.getLoc(), 10000);
    }

    /**
     * The cellar cupboard, where the magnet is. It gives one out whenever the
     * player has none and has not yet used one, because being caught in the
     * garden costs you the magnet and the quest has to be finishable after
     * that -- which is exactly what the walkthrough says happens.
     */
    private void searchCupboard(GameObject object) {
        Player p = getOwner();
        if (object.getX() != CUPBOARD_X || object.getY() != CUPBOARD_Y) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        if (object.getID() == CUPBOARD_SHUT) {
            world.registerGameObject(new GameObject(object.getLocation(), CUPBOARD_OPEN,
                object.getDirection(), object.getType()));
            p.getActionSender().sendSound("opendoor");
            return;
        }
        if (getStage() >= RAT_DONE || p.getInventory().countId(MAGNET) > 0) {
            p.getActionSender().sendMessage("You search the cupboard but find nothing");
            return;
        }
        if (!p.getInventory().canHold(new InvItem(MAGNET, 1))) {
            p.getActionSender().sendMessage("You don't have room for it");
            return;
        }
        p.getInventory().add(new InvItem(MAGNET, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You find a magnet in the cupboard");
    }

    // ------------------------------------------------------------- doors --

    private void door(QuestTrigger trigger, GameObject door) {
        Player p = getOwner();
        switch (door.getID()) {
        case FRONT_DOOR:
            if (p.getInventory().countId(FRONT_DOOR_KEY) > 0) {
                walkThrough(door);
            } else {
                p.getActionSender().sendMessage("The door is locked");
            }
            break;
        case BACK_DOOR:
            if (getStage() >= RAT_DONE) {
                walkThrough(door);
            } else {
                p.getActionSender().sendMessage("The door is locked");
                p.getActionSender().sendMessage("It seems to be held by some sort of magnetic catch");
            }
            break;
        case SHED_DOOR:
            shedDoor(door);
            break;
        }
    }

    /**
     * The shed. Locked until Nora unlocks it, and trying it is what brings her
     * out -- so the failure is the whole mechanism, not a dead end.
     */
    private void shedDoor(GameObject door) {
        Player p = getOwner();
        if (getStage() >= SHED_OPEN || completed()) {
            boolean goingIn = p.getY() >= door.getY();
            walkThrough(door);
            if (goingIn) {
                setUponBy(liveForm(), 1200);
            }
            return;
        }
        p.getActionSender().sendMessage("The shed door is locked");
        if (getStage() != RAT_DONE || witchIsOut()) {
            return;
        }
        p.getActionSender().sendMessage("@gre@You hear footsteps coming from the house");
        releaseWitch();
    }

    /**
     * Swing a door and step through it, whichever side the player stands on.
     *
     * Direction 0 is a wall between y-1 and y and anything else a wall between
     * x-1 and x. The house doors (69, 71, 72) face east/west, but the shed
     * door faces north/south -- assuming east/west for all of them stepped a
     * player clicking the shed door sideways along the outside wall, which
     * made the shed impossible to enter.
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
     * Nora's diary, read from the inventory. Three entries in her own month of
     * "Pentember" that spell out the whole back half of the quest -- the
     * experiment moved to the shed, and exactly how the rat-and-magnet lock
     * Oddenstein fitted her is opened. Reading it is optional and consumes
     * nothing; the misspellings and "by zamorak is it contrived!" are Jagex's.
     *
     * A Conversation with no npc, the same as Bravek's scruffy note: paced a
     * line at a time, and @que@ lands each one in the quest history tab.
     */
    private void readDiary() {
        new Conversation(getOwner(), null)
            .message("@que@Pentember the 3rd")
            .message("@que@The experiment is going well - moved it to the wooden shed in the garden")
            .message("@que@It does too much damage in the house")
            .message("@que@Pentember the 6th")
            .message("@que@Don't want people getting in back garden to see the experiment")
            .message("@que@A guy called Professer Odenstein is fitting me a new security system")
            .message("@que@Pentember the 8th")
            .message("@que@The security system is done - by zamorak is it contrived!")
            .message("@que@Now to open my own back door")
            .message("@que@I lure a rat out of a hole in the back porch")
            .message("@que@I fit a magic curved piece of metal to its back")
            .message("@que@The rat goes back in the hole, and the door unlocks")
            .message("@que@The prof tells me that this is cutting edge technology!")
            .start();
    }

    // --------------------------------------------------------------- rat --

    /**
     * Cheese on the floor of the small room brings the rat out. Anywhere else
     * it is just cheese on the floor, which is what dropping cheese normally
     * is, so the quest says nothing.
     */
    private void cheeseDropped() {
        Player p = getOwner();
        if (!questStarted() || getStage() >= RAT_DONE) {
            return;
        }
        if (p.getX() < HIDE_MIN_X || p.getX() > HIDE_MAX_X
                || p.getY() < HIDE_MIN_Y || p.getY() > HIDE_MAX_Y) {
            return;
        }
        if (world.getNpc(RAT, HIDE_MIN_X, HIDE_MAX_X, HIDE_MIN_Y, HIDE_MAX_Y) != null) {
            return;
        }
        final int x = p.getX(), y = p.getY();
        final Npc rat = new Npc(RAT, x, y, x - 1, x + 1, y - 1, y + 1);
        rat.setRespawn(false);
        world.registerNpc(rat);
        p.getActionSender().sendMessage("A rat comes out to eat the cheese");
        eatCheese(x, y);
        /* Long enough to get the magnet out of the pack and onto it, short
         * enough that the rat is plainly here for the cheese and not for you.
         * Each visit costs a cheese, so the clock is part of the price. */
        world.getDelayedEventHandler().add(new SingleEvent(null, RAT_STAYS){
            public void action() {
                if (rat.getID() == RAT) {
                    world.unregisterNpc(rat);
                }
            }
        });
    }

    /** The rat came for the cheese, so the cheese goes: the freshest one on
        the tile it was dropped on. */
    private void eatCheese(int x, int y) {
        org.rscdaemon.server.model.ActiveTile tile = world.getTile(x, y);
        if (tile == null) {
            return;
        }
        for (Item item : tile.getItems()) {
            if (item.getID() == CHEESE && !item.isRemoved()) {
                world.unregisterItem(item);
                return;
            }
        }
    }

    /**
     * The magnet goes on the rat, the rat goes home, and the magnet trips the
     * catch on the back door on its way past. The diary upstairs is where the
     * player is supposed to have learnt that this would work; reading it is
     * optional and this does not check for it, because the real quest did not.
     */
    private void magnetOnRat(Npc rat) {
        Player p = getOwner();
        if (getStage() >= RAT_DONE) {
            p.getActionSender().sendMessage("The rat has already served its purpose");
            return;
        }
        p.getInventory().remove(MAGNET, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You attach the magnet to the rat");
        p.getActionSender().sendMessage("The rat runs off into a hole in the wall");
        p.getActionSender().sendMessage("@gre@You hear a click from the door");
        world.unregisterNpc(rat);
        setStage(RAT_DONE);
    }

    // -------------------------------------------------------------- Nora --

    private boolean witchIsOut() {
        return world.getNpc(WITCH, WITCH_X - 8, WITCH_X + 8, WITCH_Y - 8, WITCH_Y + 8) != null;
    }

    /**
     * Nora comes out. From six seconds in she is looking: any second she finds
     * the player in the garden they are scolded, teleported to the crafting
     * guild gate, and the magnet is confiscated -- the strip behind the house,
     * and the house itself, are the only places out of her sight. Last twelve
     * seconds without being seen and she unlocks the shed for her pet and goes
     * back in.
     *
     * One repeating event owns her whole visit, and the outcome is decided
     * before anything else runs, so she despawns whatever else happens -- the
     * old shape did all of this inside a single fire-once check, and anything
     * going wrong in it left her stood in the courtyard forever, catching
     * nobody.
     */
    private void releaseWitch() {
        final Npc witch = new Npc(WITCH, WITCH_X, WITCH_Y,
                                  WITCH_X - 2, WITCH_X + 2, WITCH_Y - 2, WITCH_Y + 2);
        witch.setRespawn(false);
        world.registerNpc(witch);
        world.getDelayedEventHandler().add(new DelayedEvent(getOwner(), 1000){
            private int seconds = 0;

            public void run() {
                this.seconds++;
                if (this.seconds >= WITCH_DELAY / 1000 && caught()) {
                    this.stop();
                    leave(witch);
                    setStage(STARTED);
                    getOwner().getInventory().remove(MAGNET, 1);
                    getOwner().getActionSender().sendInventory();
                    scolding(witch).start();
                    /* Her two lines take three seconds; the march waits for
                     * them, because npc speech only renders in view. */
                    world.getDelayedEventHandler().add(new SingleEvent(getOwner(), 3200){
                        public void action() {
                            getOwner().teleport(GUILD_X, GUILD_Y, false);
                            getOwner().getActionSender().sendMessage(
                                "The witch grabs you and marches you out of Taverley");
                        }
                    });
                    return;
                }
                if (this.seconds >= WITCH_LEAVES / 1000) {
                    this.stop();
                    leave(witch);
                    if (getStage() == RAT_DONE) {
                        setStage(SHED_OPEN);
                        greeting(witch).start();
                        getOwner().getActionSender().sendMessage("@gre@You hear the shed being unlocked");
                    }
                }
            }
        });
    }

    /** She has said her piece; a few seconds later she is gone. */
    private void leave(final Npc witch) {
        world.getDelayedEventHandler().add(new SingleEvent(null, 5000){
            public void action() {
                world.unregisterNpc(witch);
            }
        });
    }

    /** In the garden and not in the strip behind the house. */
    private boolean caught() {
        Player p = getOwner();
        return p.getX() <= HIDE_MAX_X
            && !(p.getX() >= HIDE_MIN_X && p.getY() >= HIDE_MIN_Y && p.getY() <= HIDE_MAX_Y);
    }

    private Conversation scolding(Npc witch) {
        return new Conversation(getOwner(), witch)
            .npc("Oi what are you doing in my garden?")
            .npc("Get out you pesky intruder");
    }

    private Conversation greeting(Npc witch) {
        return new Conversation(getOwner(), witch)
            .npc("How are you tonight my pretty?")
            .npc("Would you like some food?")
            .npc("Just wait there while I get some");
    }

    // ------------------------------------------------------ shapeshifter --

    /** Whichever form is alive in the shed right now, or null. */
    private Npc liveForm() {
        for (int i = 0; i < FORMS.length; i++) {
            Npc form = world.getNpc(FORMS[i], SHED_X - 2, SHED_X + 2,
                                              SHED_Y - 2, SHED_Y + 3);
            if (form != null) {
                return form;
            }
        }
        return null;
    }

    /**
     * The thing in the shed does not wait to be provoked. Its def is not
     * aggressive -- it cannot be, or it would fight through the shed wall at
     * passers-by -- so the quest sets it on anyone who comes in, and on anyone
     * who reaches for the ball. The short delay is for the walk-through to
     * land first; attackPlayer refuses a busy player.
     */
    private void setUponBy(final Npc form, int delay) {
        if (form == null) {
            return;
        }
        world.getDelayedEventHandler().add(new SingleEvent(getOwner(), delay){
            public void action() {
                if (!form.isRemoved()) {
                    form.attackPlayer(getOwner());
                }
            }
        });
    }

    /**
     * The ball stays where it is until the thing guarding it is dead --
     * all four times over. Reaching for it while any form is alive is what
     * provokes the attack, so nobody picks it up and strolls out.
     */
    public boolean refusesPickup(InvItem item) {
        if (item.getID() != BALL) {
            return false;
        }
        if (getOwner().getFlag(SHIFTER_FLAG) != 0) {
            /* The fourth form is down; the ball is earned, whatever the first
             * form's own respawn timer has done in the meantime. */
            getOwner().setFlag(SHIFTER_FLAG, 0);
            return false;
        }
        Npc form = liveForm();
        if (form == null) {
            return false;
        }
        getOwner().getActionSender().sendMessage(
            "As you reach for the ball the creature turns on you");
        setUponBy(form, 600);
        return true;
    }

    /**
     * The thing in the shed. Killing a form puts the next one in its place;
     * killing the last leaves the ball, which is a ground spawn of Jagex's own
     * at (351,491) and has been sitting there the whole time.
     *
     * The first form is Jagex's permanent spawn and respawns by itself. The
     * other three do not, so a player who runs away finds only the first form
     * waiting when they come back -- which is the rule the wiki records.
     */
    private void killed(Npc npc) {
        int next = -1;
        for (int i = 0; i + 1 < FORMS.length; i++) {
            if (FORMS[i] == npc.getID()) {
                next = FORMS[i + 1];
                break;
            }
        }
        if (next == -1) {
            if (npc.getID() == FORMS[FORMS.length - 1]) {
                getOwner().setFlag(SHIFTER_FLAG, 1);
                getOwner().getActionSender().sendMessage("The creature has no more shapes to take");
                getOwner().getActionSender().sendMessage("@gre@You can take the ball");
            }
            return;
        }
        final Npc form = new Npc(next, SHED_X, SHED_Y,
                                 SHED_X - 1, SHED_X + 1, SHED_Y - 1, SHED_Y + 1);
        form.setRespawn(false);
        world.registerNpc(form);
        getOwner().getActionSender().sendMessage("The creature changes shape");
        /* And comes straight back at you -- waiting out the post-kill busy
         * moment, since attackPlayer refuses a busy player. */
        setUponBy(form, 1800);
    }
}
