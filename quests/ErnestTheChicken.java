import org.rscdaemon.server.event.SingleEvent;
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
import org.rscdaemon.server.util.Formulae;

/**
 * Ernest the chicken.
 *
 * Veronica is waiting outside Draynor Manor. Her fiance went in to ask for
 * directions an hour ago and has not come out. He is on the top floor, and he is
 * a chicken: Professor Oddenstein tested his pouletmorph machine on him and the
 * house gremlins have since run off with the parts needed to turn him back. The
 * pressure gauge is at the bottom of the fountain behind piranhas, the rubber
 * tube is in a closet whose key is buried in the compost heap, and the oil can
 * is on the far side of the basement's lever maze.
 *
 * Four quest points and 300 coins. No experience.
 *
 * Three notes on how this is put together.
 *
 * The lever maze is the substantial piece. Nine doors, six levers, and no record
 * anywhere of which lever moved which door -- that wiring lived on Jagex's
 * server and was never in any data file. What survives is the route players
 * wrote down, and the mapping in {@link #LEVER_DOORS} is the one I derived from
 * it: pull the levers in the recorded order and every door the route asks for is
 * open at the moment it is needed, including the two that step three closes
 * again. It is a reconstruction, and it is honest to say so, but it reproduces
 * the recorded experience exactly.
 *
 * Door state is per-player, and the doors themselves never leave the world.
 * A maze door always renders closed; what a lever changes is whether Open
 * lets YOU through it (a momentary swing, like any other door) or answers
 * "The door is locked". That is OpenRSC's authentic model, and it is also
 * what a player sees: a previous revision unregistered opened doors from the
 * world, which drew the whole solved maze as a row of holes and revealed the
 * route at a glance -- reported live, with a screenshot of the holes.
 *
 * The piranhas and the bones-style counters are per-player and are not saved,
 * for the same reason no quest saves anything but its stage. Poisoning the
 * fountain and then logging out means poisoning it again.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class ErnestTheChicken extends Quest {

    public final static int UID = Quests.ERNEST_THE_CHICKEN;

    /** Veronica has asked for help. */
    private static final int STARTED = 1;
    /** Oddenstein has explained the chicken and named the three missing parts. */
    private static final int KNOWS_CHICKEN = 2;
    private static final int FINISHED = 3;

    private static final int VERONICA = 36;
    private static final int ODDENSTEIN = 38;
    private static final int CHICKEN = 91;
    private static final int ERNEST = 92;

    private static final int COINS = 10;
    private static final int PRESSURE_GAUGE = 175;
    private static final int FISH_FOOD = 176;
    private static final int POISON = 177;
    private static final int POISONED_FISH_FOOD = 178;
    private static final int OIL_CAN = 208;
    private static final int SPADE = 211;
    private static final int CLOSET_KEY = 212;
    private static final int RUBBER_TUBE = 213;

    private static final int REWARD_COINS = 300;
    private static final int FORTUNE_PRICE = 1;

    /** The fountain west of the manor, the one with something in it. */
    private static final int FOUNTAIN = 86;
    private static final int FOUNTAIN_X = 226;
    private static final int FOUNTAIN_Y = 565;

    /** The compost heap by the cabbage patch. */
    private static final int COMPOST = 134;
    private static final int COMPOST_X = 230;
    private static final int COMPOST_Y = 552;

    /** The closet behind the stairs. One door in the world uses this id. */
    private static final int CLOSET_DOOR = 35;
    private static final int CLOSET_DOOR_X = 212;
    private static final int CLOSET_DOOR_Y = 545;

    /** Oddenstein's room, for finding the chicken that is really Ernest. */
    private static final int LAB_MIN_X = 206;
    private static final int LAB_MAX_X = 218;
    private static final int LAB_MIN_Y = 2434;
    private static final int LAB_MAX_Y = 2448;

    /** How long Ernest stays a man before the machine wears off again. */
    private static final int ERNEST_LASTS = 60000;

    // ---------------------------------------------------------------- maze --

    /** The maze's way out. Climbing it resets this player's levers. */
    private static final int EXIT_LADDER = 130;
    private static final int EXIT_LADDER_X = 223, EXIT_LADDER_Y = 3385;

    /** LeverA through LeverF, in that order. */
    private static final int[] LEVERS = {124, 125, 126, 127, 128, 129};
    private static final int[][] LEVER_AT = {
        {225, 3386}, {222, 3382}, {222, 3378}, {223, 3375}, {229, 3375}, {230, 3376}
    };

    /**
     * The nine maze doors: id, x, y, direction.
     *
     * Direction 0 blocks north to south between (x,y) and (x,y-1); direction 1
     * blocks east to west between (x,y) and (x-1,y). Together they cut the maze
     * into a three by three grid of rooms.
     */
    private static final int[][] MAZE_DOORS = {
        {25, 225, 3376, 1},
        {26, 228, 3376, 1},
        {27, 225, 3379, 1},
        {28, 228, 3379, 1},
        {29, 228, 3382, 1},
        {30, 226, 3378, 0},
        {31, 229, 3378, 0},
        {32, 223, 3381, 0},
        {33, 226, 3381, 0}
    };

    /**
     * Which doors each lever throws, by index into MAZE_DOORS.
     *
     * Reconstructed from the recorded route, as the class comment says. Walk it
     * through from a closed maze and every step lands:
     *
     *   A, B          -> 32 and 27 open; leave by 32.
     *   D             -> 28, 31, 33 open; out through 27, then south by 33.
     *   A, B          -> 32 and 27 shut again; north by 33, west by 28, north by 31.
     *   E, F          -> 25 and 26 open; east by 26, then east by 25.
     *   C             -> 29 and 30 open; west by 25, west by 26.
     *   E             -> 25 shuts; east by 26, south by 30, south by 33.
     *   and 29 is open, which is the door the oil can is behind.
     */
    private static final int[][] LEVER_DOORS = {
        {7},        /* A -> 32 */
        {2},        /* B -> 27 */
        {4, 5},     /* C -> 29, 30 */
        {3, 6, 8},  /* D -> 28, 31, 33 */
        {0},        /* E -> 25 */
        {1}         /* F -> 26 */
    };

    /**
     * Which maze doors this player's levers have unlocked -- a bitmask in
     * persistent var slot 0, bit i for MAZE_DOORS[i]. Per-player, like the
     * reference: the doors stay registered (and visibly shut) for everyone,
     * always; this only decides whether Open lets this player pass. Persisted
     * so a logout mid-maze cannot trap anyone: you come back to exactly the
     * doors your levers had unlocked, which is also what OpenRSC's player
     * cache does.
     */
    private static final int VAR_DOORS = 0;

    private boolean doorOpen(int index) {
        return (getVar(VAR_DOORS, 0) & (1 << index)) != 0;
    }

    // ------------------------------------------------------------ per-quest --

    /** Whether the fountain's piranhas have been dealt with. Not persisted. */
    private boolean piranhasDead = false;

    public ErnestTheChicken(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Ernest the chicken");
        setFinalStage(FINISHED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Veronica is very worried. Her fiancee went into the big spooky manor house to ask for directions. An hour later and he's still not out yet.");
        setStartPoint("Gates of Draynor Manor");
        setSpeakTo("Veronica");
        setMissionLength("Medium");
        rewardItem(COINS, REWARD_COINS);

        associateNpc(VERONICA);
        associateNpc(ODDENSTEIN);
        associateNpc(ERNEST);
        associateObject(FOUNTAIN);
        associateObject(COMPOST);
        for (int lever : LEVERS) {
            associateObject(lever);
        }
        associateObject(EXIT_LADDER, EXIT_LADDER_X, EXIT_LADDER_Y);
        associateDoor(CLOSET_DOOR);
        for (int[] d : MAZE_DOORS) {
            associateDoor(d[0]);
        }
        // Only so that poison and fish food find each other; neither is claimed
        // for any other purpose, and QuestManager only pairs items when one
        // quest names both.
        associateItem(POISON);
        associateItem(FISH_FOOD);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Ernest the chicken quest");
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc && trigger == QuestTrigger.NPC_TALK) {
            switch (((Npc) entity).getID()) {
                case VERONICA:   talkToVeronica((Npc) entity);   break;
                case ODDENSTEIN: talkToOddenstein((Npc) entity); break;
                case ERNEST:     talkToErnest((Npc) entity);     break;
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2) {
            int maze = mazeDoorIndex(object);
            if (maze >= 0) {
                mazeDoor(object, maze);
            } else {
                closetDoor(object);
            }
            return;
        }
        // The fountain's and compost heap's commands are WalkTo/Search, so
        // Search arrives as the SECOND action; the levers are Pull/Inspect,
        // so Pull is the first.
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            if (object.getID() == FOUNTAIN) {
                searchFountain(object);
            } else if (object.getID() == COMPOST) {
                // Vanilla's refusal: the key only comes out with a spade.
                getOwner().getActionSender().sendMessage("I'm not looking through that with my hands");
            }
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        if (object.getID() == EXIT_LADDER && object.getX() == EXIT_LADDER_X
                && object.getY() == EXIT_LADDER_Y) {
            // Leaving the basement resets this player's levers and doors to
            // the shut state the route is written from -- the puzzle starts
            // over on every visit. The climb itself is the generic teleport.
            setVar(VAR_DOORS, 0);
            getOwner().teleport(getOwner().getX(),
                Formulae.getNewY(getOwner().getY(), true), false);
            return;
        }
        int lever = leverIndex(object);
        if (lever >= 0) {
            pullLever(lever);
        } else if (isLeverId(object.getID())) {
            // A lever of the same id somewhere else in the world -- there is one
            // more LeverA, far away. It is not part of this maze.
            getOwner().getActionSender().sendMessage("You pull the lever, but nothing happens");
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_ITEM) {
            mixPoison();
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_OBJECT && entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (object.getID() == FOUNTAIN) {
                useOnFountain(object, used);
                return;
            }
            if (object.getID() == COMPOST) {
                useOnCompost(object, used);
                return;
            }
        }
        triggerEntity(trigger, entity);
    }

    private boolean has(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private boolean hasAllThree() {
        return has(PRESSURE_GAUGE) && has(RUBBER_TUBE) && has(OIL_CAN);
    }

    // ----------------------------------------------------------- veronica --

    private void talkToVeronica(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("Thank you for rescuing Ernest")
             .player("Where is he now?")
             .npc("Oh he went off to talk to some green warty guy")
             .npc("I'm sure he'll be back soon")
             .start();
            return;
        }

        if (getStage() == KNOWS_CHICKEN) {
            c.npc("Have you found my sweetheart yet?")
             .player("Yes, he's a chicken")
             .npc("I know he's not exactly brave")
             .npc("But I think you're being a little harsh")
             .player("No no he's been turned into an actual chicken")
             .player("By a mad scientist")
             .npc("Eeeeek")
             .npc("My poor darling")
             .npc("Why must these things happen to us?")
             .player("Well I'm doing my best to turn him back")
             .npc("Well be quick")
             .npc("I'm sure being a chicken can't be good for him")
             .start();
            return;
        }

        if (questStarted()) {
            c.npc("Have you found my sweetheart yet?")
             .player("No, not yet")
             .start();
            return;
        }

        c.npc("Can you please help me?")
         .npc("I'm in a terrible spot of trouble")
         .options(new Choice("Aha, sounds like a quest. I'll help",
                             "No, I'm looking for something to kill") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Oooh you violent person you");
                     return;
                 }
                 c.npc("Yes yes I suppose it is a quest")
                  .npc("My fiance Ernest and I came upon this house here")
                  .npc("Seeing as we were a little lost")
                  .npc("Ernest decided to go in and ask for directions")
                  .npc("That was an hour ago")
                  .npc("That house looks very spooky")
                  .npc("Can you go and see if you can find him for me?")
                  .player("Ok, I'll see what I can do")
                  .npc("Thank you, thank you")
                  .npc("I'm very grateful")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(STARTED);
                      }
                  });
             }
         }.says(0, "Aha, sounds like a quest", "I'll help"));
        c.start();
    }

    // --------------------------------------------------------- oddenstein --

    private void talkToOddenstein(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Be careful in here")
         .npc("Lots of dangerous equipment in here");

        if (getStage() == KNOWS_CHICKEN) {
            partsProgress(c, npc);
            c.start();
            return;
        }

        if (!questStarted() || completed()) {
            c.options(new Choice("What does this machine do?", "Is this your house?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        machine(c);
                    } else {
                        tenant(c);
                    }
                }
            });
            c.start();
            return;
        }

        c.options(new Choice("I'm looking for a guy called Ernest",
                             "What does this machine do?",
                             "Is this your house?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    machine(c);
                    return;
                }
                if (option == 2) {
                    tenant(c);
                    return;
                }
                c.npc("Ah Ernest, top notch bloke")
                 .npc("He's helping me with my experiments")
                 .player("So you know where he is then?")
                 .npc("He's that chicken over there")
                 .player("Ernest is a chicken?")
                 .player("Are you sure?")
                 .npc("Oh he isn't normally a chicken")
                 .npc("Or at least he wasn't")
                 .npc("Until he helped me test my pouletmorph machine")
                 .npc("It was originally going to be called a transmutation machine")
                 .npc("But after testing Pouletmorph seems more appropriate")
                 .options(new Choice("I'm glad Veronica didn't actually get engaged to a chicken",
                                     "Change him back this instant") {
                     public void picked(int option, Conversation c) {
                         if (option == 0) {
                             c.npc("Who's Veronica?")
                              .player("Ernest's fiancee")
                              .player("She probably doesn't want to marry a chicken")
                              .npc("Ooh I dunno")
                              .npc("She could have free eggs for breakfast")
                              .player("I think you'd better change him back");
                         }
                         c.npc("Um it's not so easy")
                          .npc("My machine is broken")
                          .npc("And the house gremlins")
                          .npc("Have run off with some vital bits")
                          .player("Well I can look out for them")
                          .npc("That would be a help")
                          .npc("They'll be somewhere in the manor house or its grounds")
                          .npc("The gremlins never go further than the entrance gate")
                          .npc("I'm missing the pressure gauge and a rubber tube")
                          .npc("They've also taken my oil can")
                          .npc("Which I'm going to need to get this thing started again")
                          .then(new Effect() {
                              public void run(Conversation c) {
                                  setStage(KNOWS_CHICKEN);
                              }
                          });
                     }
                 });
            }
        });
        c.start();
    }

    private void machine(Conversation c) {
        c.npc("Nothing at the moment")
         .npc("As it's broken")
         .npc("It's meant to be a transmutation machine")
         .npc("It has also spent time as a time travel machine")
         .npc("And a dramatic lightning generator")
         .npc("And a thing for generating monsters");
    }

    private void tenant(Conversation c) {
        c.npc("No, I'm just one of the tenants")
         .npc("It belongs to the count")
         .npc("Who lives in the basement");
    }

    /** "Have you found anything yet?", and what happens when the answer is yes. */
    private void partsProgress(Conversation c, final Npc npc) {
        c.npc("Have you found anything yet?");

        if (hasAllThree()) {
            c.player("I have everything")
             .npc("Give em here then")
             .then(new Effect() {
                 public void run(Conversation c) {
                     Player p = c.getPlayer();
                     p.getInventory().remove(PRESSURE_GAUGE, 1);
                     p.getInventory().remove(RUBBER_TUBE, 1);
                     p.getInventory().remove(OIL_CAN, 1);
                     p.getActionSender().sendInventory();
                 }
             })
             /* Jagex's four lines, in place of the two invented ones we had
                ("Professor Oddenstein fixes his machine" / "He pulls the
                lever"). "Professer" is his own misspelling and the wiki marks
                it {{sic}}. */
             .message("You give a rubber tube, a pressure gauge and a can of oil to the Professer")
             .message("Oddenstein starts up the machine")
             .message("The machine hums and shakes")
             .message("Suddenly a ray shoots out of the machine at the chicken")
             .then(new Effect() {
                 public void run(Conversation c) {
                     transformChicken();
                     // Finished here rather than on Ernest's thank-you. In the
                     // real game his lines follow straight on from the
                     // professor's; here he is an npc who has to be walked over
                     // to and spoken to, and he is only a man for a minute. A
                     // player who is slow about it must not lose the quest.
                     setStage(getFinalStage());
                 }
             });
            return;
        }

        if (!has(PRESSURE_GAUGE) && !has(RUBBER_TUBE) && !has(OIL_CAN)) {
            c.player("I'm afraid I don't have any yet!")
             .npc("I need a rubber tube, a pressure gauge and a can of oil")
             .npc("Then your friend can stop being a chicken");
            return;
        }

        c.player("I have found some of the things you need:");
        if (has(OIL_CAN)) {
            c.player("I have a can of oil");
        }
        if (has(PRESSURE_GAUGE)) {
            c.player("I have a pressure gauge");
        }
        if (has(RUBBER_TUBE)) {
            c.player("I have a rubber tube");
        }
        c.npc("Well that's a start")
         .npc("You still need to find");
        if (!has(OIL_CAN)) {
            c.npc("A can of oil");
        }
        if (!has(RUBBER_TUBE)) {
            c.npc("A rubber tube");
        }
        if (!has(PRESSURE_GAUGE)) {
            c.npc("A Pressure Gauge");
        }
        c.player("OK I'll try and find them");
    }

    /**
     * Run the machine: the chicken goes, Ernest arrives, and a minute later it
     * wears off -- which it did in the real game too.
     */
    private void transformChicken() {
        final Npc chicken = world.getNpc(CHICKEN, LAB_MIN_X, LAB_MAX_X, LAB_MIN_Y, LAB_MAX_Y);
        int x = chicken != null ? chicken.getX() : 211;
        int y = chicken != null ? chicken.getY() : 2442;
        if (chicken != null) {
            world.unregisterNpc(chicken);
        }
        final Npc ernest = new Npc(ERNEST, x, y, x - 2, x + 2, y - 2, y + 2);
        ernest.setRespawn(false);
        world.registerNpc(ernest);
        world.getDelayedEventHandler().add(new SingleEvent(null, ERNEST_LASTS){

            public void action() {
                world.unregisterNpc(ernest);
                if (chicken != null) {
                    world.registerNpc(new Npc(chicken.getLoc()));
                }
            }
        });
        /* No line here. "Ernest is turned back into a man" was ours; Jagex
           narrates the change as "Suddenly a ray shoots out of the machine at
           the chicken" and then lets the player see the man standing there. */
    }

    private void talkToErnest(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Thank you sir")
            .npc("It was dreadfully irritating being a chicken")
            .npc("How can I ever thank you?")
            .player("Well a cash reward is always nice")
            .npc("Of course, of course")
            .start();
    }

    // ---------------------------------------------------------- the parts --

    /** Poison on fish food, in either order. */
    private void mixPoison() {
        Player p = getOwner();
        if (!has(POISON) || !has(FISH_FOOD)) {
            return;
        }
        p.getInventory().remove(POISON, 1);
        p.getInventory().remove(FISH_FOOD, 1);
        p.getInventory().add(new InvItem(POISONED_FISH_FOOD, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You poison the fish food");
    }

    private void useOnFountain(GameObject fountain, InvItem used) {
        Player p = getOwner();
        if (fountain.getX() != FOUNTAIN_X || fountain.getY() != FOUNTAIN_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (used == null || used.getID() != POISONED_FISH_FOOD) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        p.getInventory().remove(POISONED_FISH_FOOD, 1);
        p.getActionSender().sendInventory();
        if (this.piranhasDead) {
            p.getActionSender().sendMessage("The fish food sinks. Nothing is left alive to eat it");
            return;
        }
        this.piranhasDead = true;
        p.getActionSender().sendMessage("You put the poisoned fish food in the fountain");
        p.getActionSender().sendMessage("The piranhas in the fountain eat the fish food");
        p.getActionSender().sendMessage("The piranhas die");
    }

    private void searchFountain(GameObject fountain) {
        Player p = getOwner();
        if (fountain.getX() != FOUNTAIN_X || fountain.getY() != FOUNTAIN_Y) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        if (!this.piranhasDead) {
            p.getActionSender().sendMessage("You see something in the fountain");
            p.getActionSender().sendMessage("But there are piranhas in the way");
            return;
        }
        if (has(PRESSURE_GAUGE)) {
            p.getActionSender().sendMessage("You find nothing else in the fountain");
            return;
        }
        p.getInventory().add(new InvItem(PRESSURE_GAUGE, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You search the fountain and find a pressure gauge");
    }

    private void useOnCompost(GameObject heap, InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != SPADE) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (heap.getX() != COMPOST_X || heap.getY() != COMPOST_Y) {
            p.getActionSender().sendMessage("You dig through the compost and find nothing");
            return;
        }
        if (has(CLOSET_KEY)) {
            p.getActionSender().sendMessage("You dig through the compost and find nothing else");
            return;
        }
        p.getInventory().add(new InvItem(CLOSET_KEY, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You dig through the compost");
        p.getActionSender().sendMessage("You find a small key");
    }

    /**
     * The closet.
     *
     * The key works from either side, so it locks the player in as readily as it
     * lets them out -- which is what the walkthrough's "use the key on the door
     * again to leave" is describing.
     */
    private void closetDoor(GameObject door) {
        Player p = getOwner();
        if (door.getX() != CLOSET_DOOR_X || door.getY() != CLOSET_DOOR_Y) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        if (!has(CLOSET_KEY)) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        p.getActionSender().sendSound("opendoor");
        // Swing, not vanish: show the open doorframe (the same 2<->1 swap
        // ordinary doors use) until the closed door respawns.
        world.registerGameObject(new GameObject(door.getLocation(), 1, door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        // Direction 1: the door stands between x-1 and x, so stepping through
        // means crossing that line whichever side the player started on.
        p.teleport(p.getX() >= CLOSET_DOOR_X ? CLOSET_DOOR_X - 1 : CLOSET_DOOR_X, p.getY(), false);
    }

    // ----------------------------------------------------------- the maze --

    private static boolean isLeverId(int id) {
        for (int lever : LEVERS) {
            if (lever == id) {
                return true;
            }
        }
        return false;
    }

    /** Which of the six maze levers this is, or -1 for anything else. */
    private static int leverIndex(GameObject object) {
        for (int i = 0; i < LEVERS.length; i++) {
            if (object.getID() == LEVERS[i]
                    && object.getX() == LEVER_AT[i][0]
                    && object.getY() == LEVER_AT[i][1]) {
                return i;
            }
        }
        return -1;
    }

    private void pullLever(int lever) {
        Player p = getOwner();
        p.getActionSender().sendMessage("You pull the lever");
        int mask = getVar(VAR_DOORS, 0);
        for (int door : LEVER_DOORS[lever]) {
            mask ^= 1 << door;
        }
        setVar(VAR_DOORS, mask);
        p.getActionSender().sendSound("opendoor");
        p.getActionSender().sendMessage("You hear doors moving in the distance");
    }

    /** Which maze door this is, matched by id AND tile, or -1. */
    private static int mazeDoorIndex(GameObject door) {
        for (int i = 0; i < MAZE_DOORS.length; ++i) {
            int[] d = MAZE_DOORS[i];
            if (door.getID() == d[0] && door.getX() == d[1] && door.getY() == d[2]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Open clicked on a maze door. Unlocked for this player: the door swings
     * for a moment and steps them across, the same swap-and-restore every
     * other door uses. Locked: the answer the reference records.
     */
    private void mazeDoor(GameObject door, int index) {
        Player p = getOwner();
        if (!doorOpen(index)) {
            p.getActionSender().sendMessage("The door is locked");
            return;
        }
        p.getActionSender().sendSound("opendoor");
        // Swing, not vanish: show the open doorframe (the same 2<->1 swap
        // ordinary doors use) until the closed door respawns.
        world.registerGameObject(new GameObject(door.getLocation(), 1, door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        int[] d = MAZE_DOORS[index];
        if (d[3] == 1) {
            // Blocks east-west between (x,y) and (x-1,y).
            p.teleport(p.getX() >= d[1] ? d[1] - 1 : d[1], d[2], false);
        } else {
            // Blocks north-south between (x,y) and (x,y-1).
            p.teleport(d[1], p.getY() >= d[2] ? d[2] - 1 : d[2], false);
        }
    }
}
