import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.Formulae;

/**
 * Tutorial Island -- the fourteen instructors every new account walked past
 * before it ever saw Lumbridge.
 *
 * Written as a Quest rather than as fourteen npc handlers because it is one
 * sequence with one piece of saved state, and Quest is the only thing in this
 * server that already saves per-player state, claims npcs and scenery, and
 * survives a relog halfway through. It is deliberately given an id past
 * {@link Quests#FIRST_CUSTOM}, which keeps it off the quest tab and out of the
 * quest-point total -- Jagex never gave the tutorial a row there either.
 *
 * The dialogue is Jagex's, transcribed line for line from the recorded
 * transcripts of the live game, typos and all: "expereince", "contine", "door
 * bow", "unconvered", "oppurtunites". Nothing here is paraphrased. The one
 * exception is the skip prompt, which never existed and is ours.
 *
 * Two departures from the original, both forced and both documented:
 *
 *   * No doors. The real island was a corridor: each instructor unlocked the
 *     door to the next, and the doors were spawned by the tutorial script
 *     rather than stored in the map, so there is no door data left to restore
 *     -- GameObjectLoc has not one type-1 door on the island. The island is
 *     therefore one open room, and progress is gated on the stage rather than
 *     on geography. An instructor spoken to out of turn pulls the stage
 *     forward to their own point rather than refusing, because with no doors
 *     there is nothing to stop a player wandering, and a tutorial that sulks
 *     is worse than one that is generous.
 *
 *   * The boatman will always ferry you off. With no doors he cannot be the
 *     end of a corridor, so he is the exit instead.
 *
 * The skip: after a new player has chosen their appearance, PlayerAppearanceUpdater
 * reports "appearance-chosen" here and they are asked whether they want any of
 * this. Skipping finishes the quest, hands over the kit the fourteen
 * instructors would have handed over between them, and drops the player in
 * Lumbridge -- the same tile the boatman would have.
 */
public class TutorialIsland extends Quest {

    public final static int UID = Quests.TUTORIAL_ISLAND;

    // ------------------------------------------------------------- stages --

    /* -1, Quest's own "never started", means the skip prompt has not been
       answered yet. Everything from here is "on the island". */
    private static final int ON_ISLAND = 0;
    private static final int GUIDE_DONE = 1;
    private static final int CONTROLS_DONE = 2;
    private static final int COMBAT_ARMED = 3;
    private static final int COMBAT_TAUGHT = 4;
    private static final int RAT_KILLED = 5;
    private static final int COMBAT_DONE = 6;
    private static final int COOK_TAUGHT = 7;
    private static final int COOK_DONE = 8;
    private static final int FINANCE_DONE = 9;
    private static final int FISH_TAUGHT = 10;
    private static final int SHRIMP_CAUGHT = 11;
    private static final int FISH_DONE = 12;
    private static final int MINE_TAUGHT = 13;
    private static final int PROSPECTED = 14;
    private static final int PICK_GIVEN = 15;
    private static final int ORE_MINED = 16;
    private static final int MINE_DONE = 17;
    private static final int BANK_DONE = 18;
    private static final int QUESTS_DONE = 19;
    private static final int WILDERNESS_DONE = 20;
    private static final int MAGIC_TAUGHT = 21;
    private static final int RUNES_GIVEN = 22;
    private static final int CHICKEN_TARGET = 23;
    private static final int CHICKEN_CAST = 24;
    private static final int MAGIC_DONE = 25;
    private static final int FATIGUE_TAUGHT = 26;
    private static final int SLEPT = 27;
    private static final int FATIGUE_DONE = 28;
    private static final int COMMUNITY_DONE = 29;
    private static final int FINISHED = 30;

    // --------------------------------------------------------------- cast --

    private static final int RAT = 473;
    private static final int COMBAT_INSTRUCTOR = 474;
    private static final int GUIDE = 476;
    private static final int COOKING_INSTRUCTOR = 478;
    private static final int FISHING_INSTRUCTOR = 479;
    private static final int FINANCIAL_ADVISOR = 480;
    private static final int MINING_INSTRUCTOR = 482;
    private static final int BANK_ASSISTANT = 485;
    private static final int QUEST_ADVISOR = 489;
    private static final int WILDERNESS_GUIDE = 493;
    private static final int MAGIC_INSTRUCTOR = 494;
    private static final int COMMUNITY_INSTRUCTOR = 496;
    private static final int BOATMAN = 497;
    private static final int CONTROLS_GUIDE = 499;
    private static final int FATIGUE_EXPERT = 774;
    private static final int CHICKEN = 3;

    // ------------------------------------------------------------ scenery --

    /** The tutorial fishing spot, id 493. Nothing else in the world uses it. */
    private static final int FISH_SPOT = 493;
    /** The tutorial rock, id 496. Likewise tutorial-only. */
    private static final int ROCKS = 496;
    /** Bed. There are 153 of them in the world; only this one is ours. */
    private static final int BED = 15;
    private static final int BED_X = 222, BED_Y = 761;

    /*
     * The Range (491) is deliberately NOT claimed. InvUseOnObject already
     * treats it as a cooking object, so the ordinary cooking code -- exp, burn
     * chance, messages -- runs on it untouched, and the cooking instructor
     * reads the result out of the inventory instead. Claiming it would have
     * meant reimplementing cooking to teach cooking.
     *
     * The cupboard (56) and the chest (17) are not claimed either: the
     * transcript records that they can be searched but not one word of what is
     * said or found, and their ordinary scenery behaviour is a better answer
     * than an invented one.
     */

    // -------------------------------------------------------------- items --

    private static final int WOODEN_SHIELD = 4;
    private static final int BRONZE_LONG_SWORD = 70;
    private static final int BRONZE_PICKAXE = 156;
    private static final int TIN_ORE = 202;
    private static final int RAW_SHRIMP = 349;
    private static final int NET = 376;
    private static final int RAW_RAT_MEAT = 503;
    private static final int COOKED_MEAT = 132;
    private static final int BURNT_MEAT = 134;
    private static final int AIR_RUNE = 33;
    private static final int MIND_RUNE = 35;
    private static final int SLEEPING_BAG = 1263;

    /* How many of each rune the magic instructor hands over. The transcript
       does not say, and no source records it; five of each is enough to miss
       with and try again. */
    private static final int RUNE_COUNT = 5;

    private static final int FISHING = 10, MINING = 14;
    private static final int SHRIMP_EXP = 10;   /* ObjectFishing: raw shrimp */
    private static final int TIN_EXP = 18;      /* ObjectMining: tin */

    private static final int WIND_STRIKE = 0;

    /** Where the boat lands, and where a skipper is put: Lumbridge. */
    private static final int LUMBRIDGE_X = 122, LUMBRIDGE_Y = 647;

    public TutorialIsland(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Tutorial Island");
        setFinalStage(FINISHED);

        associateNpc(GUIDE);
        associateNpc(CONTROLS_GUIDE);
        associateNpc(COMBAT_INSTRUCTOR);
        associateNpc(RAT);
        associateNpc(COOKING_INSTRUCTOR);
        associateNpc(FINANCIAL_ADVISOR);
        associateNpc(FISHING_INSTRUCTOR);
        associateNpc(MINING_INSTRUCTOR);
        associateNpc(BANK_ASSISTANT);
        associateNpc(QUEST_ADVISOR);
        associateNpc(WILDERNESS_GUIDE);
        associateNpc(MAGIC_INSTRUCTOR);
        associateNpc(FATIGUE_EXPERT);
        associateNpc(COMMUNITY_INSTRUCTOR);
        associateNpc(BOATMAN);
        /* The chicken is the wind strike target, and spellCast() is only
           dispatched to a quest that claimed the npc -- there is no
           by-placement form for npcs. So every chicken in the game is claimed,
           which costs nothing: a chicken has no npc handler, so talking to one
           did nothing before this and does nothing now. */
        associateNpc(CHICKEN);

        associateObject(FISH_SPOT);
        associateObject(ROCKS);
        associateObject(BED, BED_X, BED_Y);
    }

    public void completeQuest() {
        Player p = getOwner();
        p.teleport(LUMBRIDGE_X, LUMBRIDGE_Y, false);
        p.getActionSender().sendMessage("@gre@Welcome to Lumbridge, and to the game.");
    }

    // ---------------------------------------------------------- the skip --

    /**
     * Offer the skip, once, immediately after a new player has picked their
     * face.
     *
     * Reported from PlayerAppearanceUpdater rather than from login, because
     * "after they choose their appearance" is when the appearance screen is
     * out of the way and a menu can actually be seen. Guarded on the quest
     * never having started so that the Make over mage, who opens the same
     * screen, cannot re-offer it years later.
     */
    public void note(String key) {
        if (!"appearance-chosen".equals(key) || questStarted() || completed()) {
            return;
        }
        final Player p = getOwner();
        String[] options = new String[]{
            "Show me around, I'm new here",
            "I've played before, take me straight to Lumbridge"
        };
        p.setMenuHandler(new MenuHandler(options){
            public void handleReply(final int option, String reply) {
                /*
                 * MenuReplyHandler has already cleared the handler, and it is
                 * calling us from the network thread. Answering "skip" ends in
                 * a teleport, so hand the work to the game loop the same way
                 * every other menu-driven teleport in the server does.
                 */
                world.getDelayedEventHandler().add(new ShortEvent(p){
                    public void action() {
                        if (option == 1) {
                            grantTutorialKit();
                            setStage(FINISHED);
                        } else {
                            setStage(ON_ISLAND);
                            this.owner.getActionSender().sendMessage(
                                "@gre@Speak to the guide to begin.");
                        }
                    }
                });
            }
        });
        p.getActionSender().sendMenu(options);
    }

    /**
     * Everything the fourteen instructors hand over between them.
     *
     * Given to a skipper so that skipping costs only the lesson and not the
     * equipment -- a player who skips and a player who sits through it should
     * arrive in Lumbridge carrying the same things.
     */
    private void grantTutorialKit() {
        Player p = getOwner();
        p.getInventory().add(new InvItem(BRONZE_LONG_SWORD, 1));
        p.getInventory().add(new InvItem(WOODEN_SHIELD, 1));
        p.getInventory().add(new InvItem(NET, 1));
        p.getInventory().add(new InvItem(BRONZE_PICKAXE, 1));
        p.getInventory().add(new InvItem(SLEEPING_BAG, 1));
        p.getInventory().add(new InvItem(AIR_RUNE, RUNE_COUNT));
        p.getInventory().add(new InvItem(MIND_RUNE, RUNE_COUNT));
        p.getActionSender().sendInventory();
    }

    // ----------------------------------------------------------- dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_KILLED) {
                killed(npc);
            } else if (trigger == QuestTrigger.NPC_TALK) {
                talk(npc);
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        switch (object.getID()) {
            case FISH_SPOT:
                if (trigger == QuestTrigger.OBJECT_ACT1) {
                    fish();
                }
                break;
            case ROCKS:
                if (trigger == QuestTrigger.OBJECT_ACT1) {
                    mine();
                } else if (trigger == QuestTrigger.OBJECT_ACT2) {
                    prospect();
                }
                break;
            case BED:
                if (trigger == QuestTrigger.OBJECT_ACT1) {
                    rest();
                }
                break;
        }
    }

    private void talk(Npc npc) {
        switch (npc.getID()) {
            case GUIDE:                guide(npc); break;
            case CONTROLS_GUIDE:       controls(npc); break;
            case COMBAT_INSTRUCTOR:    combat(npc); break;
            case COOKING_INSTRUCTOR:   cooking(npc); break;
            case FINANCIAL_ADVISOR:    finance(npc); break;
            case FISHING_INSTRUCTOR:   fishing(npc); break;
            case MINING_INSTRUCTOR:    mining(npc); break;
            case BANK_ASSISTANT:       banker(npc); break;
            case QUEST_ADVISOR:        questAdvisor(npc); break;
            case WILDERNESS_GUIDE:     wilderness(npc); break;
            case MAGIC_INSTRUCTOR:     magic(npc); break;
            case FATIGUE_EXPERT:       fatigue(npc); break;
            case COMMUNITY_INSTRUCTOR: community(npc); break;
            case BOATMAN:              boatman(npc); break;
        }
    }

    private void killed(Npc npc) {
        if (npc.getID() == RAT && getStage() >= COMBAT_ARMED && getStage() < RAT_KILLED) {
            setStage(RAT_KILLED);
        }
    }

    /**
     * The chicken took a wind strike. The instructor's next line is "If you
     * kill chicken or not", so landing the spell is the whole test -- whether
     * the bird survived it is not.
     */
    public void spellCast(Npc npc, int spellId, int damage) {
        if (npc.getID() == CHICKEN && spellId == WIND_STRIKE && getStage() == CHICKEN_TARGET) {
            setStage(CHICKEN_CAST);
            getOwner().getActionSender().sendMessage("@gre@Now speak to the magic instructor again.");
        }
    }

    // -------------------------------------------------------------- guide --

    private void guide(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (getStage() >= GUIDE_DONE) {
            c.npc("There are several guides and advisors on the island")
             .npc("Speak to them")
             .npc("They will teach you about the various aspects of the game")
             .start();
            return;
        }
        c.npc("Welcome to the world of runescape")
         .npc("My job is to help newcomers find their feet here")
         .player("Ah good, let's get started")
         .npc("when speaking to characters such as myself")
         .npc("Sometimes options will appear in the top left corner of the screen")
         .npc("left click on one of them to continue the conversation")
         .options(new Choice("So what else can you tell me?",
                             "What other controls do I have?") {
             public void picked(int option, Conversation c) {
                 /* Jagex wrote the second one as "door bow". Left as found. */
                 c.npc(option == 0 ? "I suggest you go through the door now"
                                   : "I suggest you go through the door bow")
                  .npc("There are several guides and advisors on the island")
                  .npc("Speak to them")
                  .npc("They will teach you about the various aspects of the game")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(GUIDE_DONE);
                      }
                  });
             }
         })
         .start();
    }

    private void controls(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (getStage() >= CONTROLS_DONE) {
            c.npc("Now carry on to speak to the combat instructor").start();
            return;
        }
        c.npc("Hello I'm here to tell you more about the game's controls")
         .npc("Most of your options and character information")
         .npc("can be accessed by the menus in the top right corner of the screen")
         .npc("moving your mouse over the map icon")
         .npc("which is the second icon from the right")
         .npc("gives you a view of the area you are in")
         .npc("clicking on this map is an effective way of walking around")
         .npc("though if the route is blocked, for example by a closed door")
         .npc("then your character won't move")
         .npc("Also notice the compass on the map which may be of help to you")
         .player("Thankyou for your help")
         .npc("Now carry on to speak to the combat instructor")
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(CONTROLS_DONE);
             }
         })
         .start();
    }

    // ------------------------------------------------------------- combat --

    private void combat(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);

        if (getStage() < COMBAT_ARMED) {
            c.npc("Aha a new recruit")
             .npc("I'm here to teach you the basics of fighting")
             .npc("First of all you need weapons")
             .npc("look after these well")
             .give(new InvItem(WOODEN_SHIELD, 1))
             .give(new InvItem(BRONZE_LONG_SWORD, 1))
             .npc("These items will now have appeared in your inventory")
             .npc("You can access them by selecting the bag icon in the menu bar")
             .npc("which can be found in the top right hand corner of the screen")
             .npc("To wield your weapon and shield left click on them within your inventory")
             .npc("their box will go red to show you are wearing them")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(COMBAT_ARMED);
                 }
             })
             .start();
            return;
        }

        if (getStage() == COMBAT_ARMED && carriedUnwielded()) {
            /* "Talking to him again without equipping items". Carrying them
               loose is the only thing that gets the nag: the transcript's next
               section is headed "after you have either dropped or equipped the
               items", so throwing them away moves you on just as wielding
               them does. */
            c.npc("You need to wield your equipment")
             .npc("You can access them by selecting the bag icon in the menu bar")
             .npc("which can be found in the top right hand corner of the screen")
             .npc("To wield your weapon and shield left click on them within your inventory")
             .npc("their box will go red to show you are wearing them")
             .start();
            return;
        }

        if (getStage() < RAT_KILLED) {
            c.npc("Today we're going to be killing giant rats")
             .npc(ratNear(), "squeek")
             .npc("move your move over a rat you will see it is level 7")
             .npc("You will see that it's level is written in green")
             .npc("If it is green this mean you have a strong chance of killing it")
             .npc("creatures with their name in red should probably be avoided")
             .npc("As this indicates they are tougher than you")
             .npc("left click on the rat to attack it")
             .then(new Effect() {
                 public void run(Conversation c) {
                     if (getStage() < COMBAT_TAUGHT) {
                         setStage(COMBAT_TAUGHT);
                     }
                 }
             })
             .start();
            return;
        }

        c.npc("Well done you're a born fighter")
         .npc("As you kill things")
         .npc("Your combat experience will go up")
         .npc("this expereince will slowly cause you to get tougher")
         .npc("eventually you will be able to take on stronger enemies")
         .npc("Such as those found in dungeons")
         .npc("Now contine to the building to the northeast")
         .then(new Effect() {
             public void run(Conversation c) {
                 if (getStage() < COMBAT_DONE) {
                     setStage(COMBAT_DONE);
                 }
             }
         })
         .start();
    }

    /** True while either handout is still sitting loose in the bag. */
    private boolean carriedUnwielded() {
        return loose(BRONZE_LONG_SWORD) || loose(WOODEN_SHIELD);
    }

    private boolean loose(int id) {
        return getOwner().getInventory().countId(id) > 0
            && !getOwner().getInventory().wielding(id);
    }

    /**
     * A rat to squeak the one line the transcript gives it. Null is fine --
     * Conversation.npc(Npc, String) with a null speaker falls back to the npc
     * the conversation is with, which is the instructor.
     */
    private Npc ratNear() {
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            if (n.getID() == RAT) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ cooking --

    private void cooking(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);

        if (getStage() >= COOK_DONE) {
            c.npc("There are many other sorts of food you can cook")
             .npc("As your cooking level increases you will be able to cook even more")
             .npc("Some of these dishes are more complicated to prepare")
             .npc("If you want to know more about cookery")
             .npc("You could consult the online manual")
             .npc("Now proceed through the next door")
             .start();
            return;
        }

        /* Cooking happens on the range, which this quest does not own, so
           there is no trigger to hang the result on -- the instructor reads
           the inventory instead, exactly as the cook does in Cook's
           assistant. */
        if (getStage() == COOK_TAUGHT && p.getInventory().countId(COOKED_MEAT) > 0) {
            c.player("I've cooked the meat correctly this time")
             .npc("Very well done")
             .npc("Now you can tell whether you need to eat or not")
             .npc("look in your stats menu")
             .npc("Click on bar graph icon in the menu bar")
             .npc("Your stats are low right now")
             .npc("As you use the various skills, these stats will increase")
             .npc("If you look at your hits you will see 2 numbers")
             .npc("The number on the right is your hits when you are at full health")
             .npc("The number on the left is your current hits")
             .npc("If the number on the left is lower eat some food to be healed")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(COOK_DONE);
                 }
             })
             .start();
            return;
        }

        if (getStage() == COOK_TAUGHT && p.getInventory().countId(BURNT_MEAT) > 0
                && p.getInventory().countId(RAW_RAT_MEAT) <= 0) {
            c.player("I burnt the meat")
             .npc("Well i'm sure you'll get the hang of it soon")
             .npc("Let's try again")
             .npc("Here's another piece of meat to cook")
             .give(new InvItem(RAW_RAT_MEAT, 1))
             .start();
            return;
        }

        c.npc("looks like you've been fighting")
         .npc("If you get hurt in a fight")
         .npc("You will slowly heal")
         .npc("Eating food will heal you much more quickly")
         .npc("I'm here to show you some simple cooking");
        if (p.getInventory().countId(RAW_RAT_MEAT) > 0) {
            /* The rat drops raw rat meat every time, so this is the ordinary
               path and the handout below is the fallback. */
            c.npc("I see you have bought your own meat")
             .npc("good stuff");
        } else {
            c.npc("First you need something to cook")
             .give(new InvItem(RAW_RAT_MEAT, 1));
        }
        c.npc("ok cook it on the range")
         .npc("To use an item you are holding")
         .npc("Open your inventory and click on the item you wish to use")
         .npc("Then click on whatever you wish to use it on")
         .npc("In this case use it on the range")
         .then(new Effect() {
             public void run(Conversation c) {
                 if (getStage() < COOK_TAUGHT) {
                     setStage(COOK_TAUGHT);
                 }
             }
         })
         .start();
    }

    // ------------------------------------------------------------ finance --

    private void finance(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello there")
            .npc("I'm your designated financial advisor")
            .player("That's good because I don't have any money at the moment")
            .player("How do I get rich?")
            .npc("There are many different ways to make money in runescape")
            .npc("for example certain monsters will drop a bit of loot")
            .npc("To start with killing men and goblins might be a good idea")
            .npc("Some higher level monsters will drop quite a lot of treasure")
            .npc("several of runescapes skills are good money making skills")
            .npc("two of these skills are mining and fishing")
            .npc("there are instructors on the island who will help you with this")
            .npc("using skills and combat to make money is a good plan")
            .npc("because using a skill also slowly increases your level in that skill")
            .npc("A high level in a skill opens up many more oppurtunites")
            .npc("Some other ways of making money include taking quests and tasks")
            .npc("You can find these by talking to certain game controlled characters")
            .npc("Our quest advisors will tell you about this")
            .npc("Sometimes you will find items lying around")
            .npc("Selling these to the shops makes some money too")
            .npc("Now continue through the next door")
            .then(new Effect() {
                public void run(Conversation c) {
                    if (getStage() < FINANCE_DONE) {
                        setStage(FINANCE_DONE);
                    }
                }
            })
            .start();
    }

    // ------------------------------------------------------------ fishing --

    private void fishing(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);

        if (getStage() >= FISH_DONE) {
            c.npc("Go through the next door to continue with the tutorial now").start();
            return;
        }

        if (getStage() == SHRIMP_CAUGHT) {
            c.npc("Well done you can now continue with the tutorial")
             .npc("first You can cook the shrimps on my fire here if you like")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(FISH_DONE);
                 }
             })
             .start();
            return;
        }

        if (getStage() == FISH_TAUGHT) {
            c.npc("Left click on that sparkling water if you have net")
             .npc("then you can catch some shrimp if you have net");
            if (p.getInventory().countId(NET) <= 0) {
                c.player("I have lost my net")
                 .npc("Hmm a good fisherman doesn't lose his net")
                 .npc("Ah well heres another one")
                 .give(new InvItem(NET, 1));
            }
            c.start();
            return;
        }

        c.player("Hi are you here to tell me how to catch fish?")
         .npc("Yes that's right, you're a smart one")
         .npc("Fishing is a useful skill")
         .npc("You can sell high level fish for lots of money")
         .npc("Or of course cook it and eat it to heal yourself")
         .npc("Unfortunately you'll have to start off catching shrimps")
         .npc("Till your fishing level gets higher")
         .npc("you'll need this")
         .give(new InvItem(NET, 1))
         .npc("Go catch some shrimp")
         .npc("left click on that sparkling piece of water")
         .npc("While you have the net in your inventory you might catch some fish")
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(FISH_TAUGHT);
             }
         })
         .start();
    }

    /**
     * The tutorial shrimp. Object 493 has no ObjectFishing entry -- it is a
     * one-off prop, not a fishing spot -- so the catch is scripted here rather
     * than rolled. It never fails: a lesson that can fail teaches the wrong
     * thing on the first try.
     */
    private void fish() {
        Player p = getOwner();
        if (p.getInventory().countId(NET) <= 0) {
            p.getActionSender().sendMessage("@gry@ You need a net to catch these fish.");
            return;
        }
        p.getActionSender().sendSound("fish");
        p.getActionSender().sendMessage("@pnk@ You attempt to catch some fish");
        p.getInventory().add(new InvItem(RAW_SHRIMP, 1));
        p.getActionSender().sendMessage("@pnk@ You catch a Raw Shrimp.");
        p.getActionSender().sendInventory();
        p.incExp(FISHING, SHRIMP_EXP, true);
        p.getActionSender().sendStat(FISHING);
        if (getStage() == FISH_TAUGHT) {
            setStage(SHRIMP_CAUGHT);
        }
    }

    // ------------------------------------------------------------- mining --

    private void mining(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);

        if (getStage() >= MINE_DONE) {
            c.npc("If at a later date you find a rock with copper ore")
             .npc("you can take the copper ore and tin ore to a furnace")
             .npc("use them on the furnace to make bronze bars")
             .npc("which you can then either sell")
             .npc("or use on anvils with a hammer")
             .npc("To make weapons")
             .npc("as your mining and smithing levels grow")
             .npc("you will be able to mine various exciting new metals")
             .npc("now go through the next door to speak to the bankers")
             .start();
            return;
        }

        if (getStage() == ORE_MINED) {
            c.npc("very good")
             .npc("If at a later date you find a rock with copper ore")
             .npc("you can take the copper ore and tin ore to a furnace")
             .npc("use them on the furnace to make bronze bars")
             .npc("which you can then either sell")
             .npc("or use on anvils with a hammer")
             .npc("To make weapons")
             .npc("as your mining and smithing levels grow")
             .npc("you will be able to mine various exciting new metals")
             .npc("now go through the next door to speak to the bankers")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(MINE_DONE);
                 }
             })
             .start();
            return;
        }

        if (getStage() == PICK_GIVEN) {
            /* The transcript's "Player loses their pickaxe" section records
               the player's line and nothing the instructor says back, so he
               says nothing back -- he just hands over another one. */
            if (p.getInventory().countId(BRONZE_PICKAXE) <= 0) {
                c.player("I have lost my pickaxe")
                 .give(new InvItem(BRONZE_PICKAXE, 1));
            }
            c.npc("Now hit those rocks").start();
            return;
        }

        if (getStage() == PROSPECTED) {
            c.player("There's tin ore in that rock")
             .npc("Yes, thats what's in there")
             .npc("Ok you need to get that in out of the rock")
             .npc("First of all you need a pick")
             .npc("And here we have a pick")
             .give(new InvItem(BRONZE_PICKAXE, 1))
             .npc("Now hit those rocks")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(PICK_GIVEN);
                 }
             })
             .start();
            return;
        }

        if (getStage() == MINE_TAUGHT) {
            c.player("Hello again")
             .npc("You haven't prospected that rock yet")
             .npc("Right click on it and select prospect")
             .start();
            return;
        }

        c.player("Good day to you")
         .npc("hello I'm a veteran miner!")
         .npc("I'm here to show you how to mine")
         .npc("If you want to quickly find out what is in a rock you can prospect it")
         .npc("right click on this rock here")
         .npc("And select prospect")
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(MINE_TAUGHT);
             }
         })
         .start();
    }

    /**
     * Prospecting the tutorial rock, from Transcript:Tin rock (Tutorial
     * Island). The instructor's rock does not just name its ore -- it goes on
     * to teach the two things about prospecting a player needs to know before
     * they leave the island. All five lines are one message each.
     */
    private void prospect() {
        Player p = getOwner();
        p.getActionSender().sendMessage("@que@You examine the rock for ores...");
        p.getActionSender().sendMessage("@que@This rock contains tin ore");
        p.getActionSender().sendMessage("@que@Sometimes you won't find the ore but trying again may find it");
        p.getActionSender().sendMessage("@que@If a rock contains high level ore");
        p.getActionSender().sendMessage("@que@You will not find it until you increase your mining level");
        if (getStage() == MINE_TAUGHT) {
            setStage(PROSPECTED);
        }
    }

    /**
     * How many times this player has got tin out of the rock.
     *
     * The tutorial rock stops giving after three, and says so. There is
     * nowhere in a save to keep this, so it lives for the session -- a player
     * who logs out on the island and comes back gets three more. That is the
     * whole cost of not adding a column for a message that only ever appears
     * once in a character's life.
     */
    private int oresMined = 0;

    /**
     * The tutorial rock, from Transcript:Tin rock (Tutorial Island).
     *
     * Object 496 has no ObjectMining entry, so the tutorial scripts it. Three
     * things here are deliberately not the mainland wording. The pickaxe
     * refusal says "the rock", not "this rock". The failure is not "you only
     * succeed in scratching the rock" but a gentler two-liner that tells a new
     * player to keep going -- and the second half of it opens lower case.
     * "Thats enough mining for now" is missing its apostrophe. All four are
     * Jagex's; leave them alone.
     *
     * It can fail. The old version always yielded, which quietly threw away
     * the one place in the game where a player is told what a failed swing
     * means.
     */
    private void mine() {
        Player p = getOwner();
        // miningAxeIDs already ends with the bronze pickaxe the instructor
        // hands over, so this covers a player who arrived carrying a better one.
        boolean pick = false;
        int axeId = -1;
        for (int id : Formulae.miningAxeIDs) {
            if (p.getInventory().countId(id) > 0) {
                pick = true;
                axeId = id;
                break;
            }
        }
        if (!pick) {
            p.getActionSender().sendMessage("You need a pickaxe to mine the rock");
            return;
        }
        if (this.oresMined >= 3) {
            p.getActionSender().sendMessage("@que@Thats enough mining for now");
            return;
        }
        p.getActionSender().sendSound("mine");
        p.getActionSender().sendMessage("@que@You swing your pick at the rock...");
        if (!Formulae.getOre(1, p.getCurStat(MINING), axeId)) {
            p.getActionSender().sendMessage("@que@You fail to make any real impact on the rock");
            p.getActionSender().sendMessage("@que@keep trying and you will get some ore");
            return;
        }
        ++this.oresMined;
        p.getInventory().add(new InvItem(TIN_ORE, 1));
        p.getActionSender().sendMessage("@que@You manage to obtain some tin ore");
        p.getActionSender().sendInventory();
        p.incExp(MINING, TIN_EXP, true);
        p.getActionSender().sendStat(MINING);
        if (getStage() == PICK_GIVEN) {
            setStage(ORE_MINED);
        }
    }

    // --------------------------------------------------------------- bank --

    /**
     * The Bank assistant, 485.
     *
     * He used to be wired to the ordinary Bankers handler, which meant he
     * opened a bank and said not one of his own lines. He is a Tutorial Island
     * npc and nothing else -- his only three spawns are on the island -- so he
     * has been taken out of NpcHandlers.xml and given back his script, bank
     * and all.
     */
    private void banker(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Hello welcome to the bank of runescape")
         .npc("You can deposit your items in banks")
         .npc("This allows you to own much more equipment")
         .npc("Than can be fitted in your inventory")
         .npc("It will also keep your items safe")
         .npc("So you won't lose them when you die")
         .npc("You can withdraw deposited items from any bank in the world");
        if (getStage() >= BANK_DONE) {
            c.npc("Now proceed through the next door");
        }
        c.options(new Choice("Can I access my bank account please?",
                             "Okay thankyou for your help") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc(getOwner().isMale() ? "Certainly Sir" : "Certainly Miss")
                      .then(new Effect() {
                          public void run(Conversation c) {
                              c.getPlayer().setAccessingBank(true);
                              c.getPlayer().getActionSender().showBank();
                          }
                      });
                 } else {
                     c.npc("Not a problem");
                 }
                 c.then(new Effect() {
                     public void run(Conversation c) {
                         if (getStage() < BANK_DONE) {
                             setStage(BANK_DONE);
                         }
                     }
                 });
             }
         })
         .start();
    }

    // ------------------------------------------------------------- quests --

    private void questAdvisor(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Greetings traveller")
            .npc("If you're interested in a bit of adventure")
            .npc("I can recommend going on a good quest")
            .npc("There are many secrets to be unconvered")
            .npc("And wrongs to be set right")
            .npc("If you talk to the various characters in the game")
            .npc("Some of them will give you quests")
            .options(new Choice("What sort of quests are there to do?",
                                "Can you recommend any quests?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("If you select the bar graph in the menu bar")
                         .npc("And then select the quests tabs")
                         .npc("You will see a lists of quests")
                         .npc("quests you have completed will show up in green")
                         .npc("You can only do each quest once")
                         .player("Thank you for the advice")
                         .npc("good questing traveller");
                    } else {
                        c.npc("Well I hear the cook in Lumbridge castle is having some problems")
                         .npc("When you get to Lumbridge, go into the castle there")
                         .npc("Find the cook and have a chat with him")
                         .player("Okay thanks for the advice");
                    }
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            if (getStage() < QUESTS_DONE) {
                                setStage(QUESTS_DONE);
                            }
                        }
                    });
                }
            })
            .start();
    }

    // --------------------------------------------------------- wilderness --

    private void wilderness(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hi are you someone who like to fight other players?")
            .npc("Granted it has big risks")
            .npc("but it can be very rewarding too")
            .options(new Choice("Yes I'm up for a bit of a fight",
                                "I'd prefer to avoid that") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("Then don't stray into the wilderness")
                         .npc("That is the area of the game where you can attack other players");
                        return;
                    }
                    c.npc("Then the wilderness is the place for you")
                     .npc("That is the area of the game where you can attack other players")
                     .npc("Be careful though")
                     .npc("Other players can be a lot more dangerous than monsters")
                     .npc("they will be much more persistent in chasing after you")
                     .npc("Especially when they hunt in groups")
                     .options(new Choice("Where is this wilderness?",
                                         "What happens when I die?") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 whereIsTheWilderness(c);
                                 whatHappensWhenIDie(c);
                             } else {
                                 whatHappensWhenIDie(c);
                                 whereIsTheWilderness(c);
                             }
                             c.npc("Now proceed through the next door")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      if (getStage() < WILDERNESS_DONE) {
                                          setStage(WILDERNESS_DONE);
                                      }
                                  }
                              });
                         }
                     });
                }
            })
            .start();
    }

    private void whereIsTheWilderness(Conversation c) {
        c.npc("Once you get into the main player area head north")
         .npc("then you will eventually reach the wilderness")
         .npc("The deeper you venture into the wilderness")
         .npc("The greater the level range of players who can attack you")
         .npc("So if you go in really deep")
         .npc("Players much stronger than you can attack you");
    }

    private void whatHappensWhenIDie(Conversation c) {
        c.npc("normally when you die")
         .npc("you will lose all of the items in your inventory")
         .npc("Except the three most valuable")
         .npc("You never keep stackable items like coins and runes")
         .npc("which is why it is a good idea to leave things in the bank")
         .npc("However if you attack another player")
         .npc("You will get a skull above your head for twenty minutes")
         .npc("If you die with a skull above your head you lose your entire inventory");
    }

    // -------------------------------------------------------------- magic --

    private void magic(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);

        if (getStage() >= CHICKEN_CAST) {
            c.npc("Well done")
             .npc("As you get a higher magic level")
             .npc("You will be able to cast all sorts of interesting spells")
             .npc("Now go through the next door");
            if (getStage() < MAGIC_DONE) {
                c.then(new Effect() {
                    public void run(Conversation c) {
                        setStage(MAGIC_DONE);
                    }
                });
            }
            c.start();
            return;
        }

        if (getStage() == CHICKEN_TARGET) {
            c.npc("To shoot a wind strike at a chicken")
             .npc("select the book icon in the menu bar")
             .npc("then click on the yellow wind strike text")
             .npc("then left click on the chicken to cast the spell")
             .npc(chickenNear(), "cluck")
             .start();
            return;
        }

        if (getStage() == RUNES_GIVEN) {
            c.npc(chickenNear(), "cluck")
             .npc("Aha a chicken")
             .npc("An ideal wind strike target")
             .npc("ok click on the wind strike spell in your spell list")
             .npc("then click on the chicken to chose it as a target")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(CHICKEN_TARGET);
                 }
             })
             .start();
            return;
        }

        if (getStage() == MAGIC_TAUGHT) {
            c.player("I don't have the runes to cast wind strike")
             .npc("How do you expect to do magic without runes?")
             .npc("Ok I shall have to provide you with runes")
             .give(new InvItem(AIR_RUNE, RUNE_COUNT))
             .give(new InvItem(MIND_RUNE, RUNE_COUNT))
             .npc("Ok look at your spell list now")
             .npc("You will see you have runes for the spell")
             .npc("And it shows up in yellow in your list")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(RUNES_GIVEN);
                 }
             })
             .start();
            return;
        }

        c.npc("there's good magic potential in this one")
         .npc("Yes definitely something I can work with")
         .player("Hmm are you talking about me?")
         .npc("Yes that is the one of which I speak")
         .npc("Ok move your mouse over the book icon on the menu bar")
         .npc("this is your magic menu")
         .npc("You will see at level 1 magic you can only cast wind strike")
         .npc("move your mouse over the wind strike text")
         .npc("If you look at the bottom of the magic window")
         .npc("You will see more information about the spell")
         .npc("runes required for the spell have two numbers over them")
         .npc("The first number is how many runes you have")
         .npc("The second is how many runes the spell requires")
         .npc("Speak to me again when you have checked this")
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(MAGIC_TAUGHT);
             }
         })
         .start();
    }

    private Npc chickenNear() {
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            if (n.getID() == CHICKEN) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ fatigue --

    private void fatigue(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (getStage() >= FATIGUE_DONE) {
            /* Jagex's own trailing quote mark on the last line. Left as found. */
            c.npc("When you use your skills you will slowly get fatigued")
             .npc("If you look at your stats menu you will see a fatigue stat")
             .npc("When your fatigue reaches 100 percent then you will be very tired")
             .npc("You won't be able to concentrate enough to gain experience in your skills")
             .npc("To reduce your fatigue you can either eat some food or go to sleep")
             .npc("Click on a bed or sleeping bag to go sleep")
             .npc("Then follow the instructions to wake up")
             .npc("You can now go through the next door\"")
             .start();
            return;
        }

        if (getStage() == SLEPT) {
            c.npc("How are you feeling now?")
             .player("I feel much better rested now")
             .npc("Tell you what, I'll give you this useful sleeping bag")
             .give(new InvItem(SLEEPING_BAG, 1))
             .npc("So you can rest anywhere")
             .npc("This saves you the trouble of finding a bed")
             .npc("but you will need to sleep longer to restore your fatigue fully")
             .npc("You can now go through the next door\"")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(FATIGUE_DONE);
                 }
             })
             .start();
            return;
        }

        c.player("Hi I'm feeling a little tired after all this learning")
         .npc("Yes when you use your skills you will slowly get fatigued")
         .npc("If you look on your stats menu you will see a fatigue stat")
         .npc("When your fatigue reaches 100 percent then you will be very tired")
         .npc("You won't be able to concentrate enough to gain experience in your skills")
         .npc("To reduce your fatigue you will need to go to sleep")
         .npc("Click on the bed to go to sleep")
         .npc("Then follow the instructions to wake up")
         .npc("When you are done talk to me again")
         .then(new Effect() {
             public void run(Conversation c) {
                 if (getStage() < FATIGUE_TAUGHT) {
                     setStage(FATIGUE_TAUGHT);
                 }
             }
         })
         .start();
    }

    /**
     * The bed at (222,761).
     *
     * Beds have no rest handler anywhere in this server -- clicking one has
     * always done nothing -- so this quest implements its own for the one bed
     * the tutorial needs. Making every bed in the world work is a separate
     * job; claiming this single placement leaves the other 152 exactly as they
     * were.
     *
     * The sleep screen is skipped and the fatigue simply clears, matching the
     * sleeping bag in InvActionHandler. Fatigue itself is untouched.
     */
    private void rest() {
        final Player p = getOwner();
        p.getActionSender().sendMessage("@pnk@ You rest in the bed");
        p.setFatigue(0);
        p.getActionSender().sendFatigue();
        p.getActionSender().sendMessage("@pnk@ You wake up - feeling refreshed");
        if (getStage() == FATIGUE_TAUGHT) {
            setStage(SLEPT);
        }
    }

    // ---------------------------------------------------------- community --

    /**
     * The community instructor asks to be asked twice.
     *
     * The transcript is explicit that answering only one of his two branches
     * "will end the dialogue but not give access through the next door", so
     * both have to be heard. Rather than spend two saved stages on it, the
     * conversation offers the other branch as soon as the first is finished
     * and only marks him done when both have been.
     */
    private void community(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("You're almost ready to go out into the main game area")
         .npc("When you get out there")
         .npc("You will be able to interact with thousands of other players")
         .options(new Choice("How can I communicate with other players?",
                             "Are there rules on ingame behaviour?",
                             "goodbye then") {
             public void picked(int option, Conversation c) {
                 if (option == 2) {
                     c.npc("Good luck");
                     return;
                 }
                 final boolean askedChat = option == 0;
                 if (askedChat) {
                     communication(c);
                 } else {
                     conduct(c);
                 }
                 c.options(new Choice(askedChat ? "Are there rules on ingame behaviour?"
                                                : "How can I communicate with other players?",
                                      "goodbye then") {
                     public void picked(int option, Conversation c) {
                         if (option == 1) {
                             c.npc("Good luck");
                             return;
                         }
                         if (askedChat) {
                             conduct(c);
                         } else {
                             communication(c);
                         }
                         c.player("goodbye then")
                          .npc("Good luck")
                          .then(new Effect() {
                              public void run(Conversation c) {
                                  if (getStage() < COMMUNITY_DONE) {
                                      setStage(COMMUNITY_DONE);
                                  }
                              }
                          });
                     }
                 });
             }
         })
         .start();
    }

    private void communication(Conversation c) {
        c.npc("typing in the game window will bring up chat")
         .npc("Which players in the nearby area will be able to see")
         .npc("If you want to speak to a particular friend anywhere in the game")
         .npc("You will be able to select the smiley face icon")
         .npc("then click to add a friend, and type in your friend's name")
         .npc("If that player is logged in on the same would as you")
         .npc("their name will go green")
         .npc("If they are logged in on a different world their name will go yellow")
         .npc("clicking on their name will allow you to send a message");
    }

    private void conduct(Conversation c) {
        c.npc("Yes you should read the rules of conduct on our front page")
         .npc("To make sure you do nothing to get yourself banned")
         .npc("but as a general guide always try to be courteous to people in game")
         .npc("Remember the people in the game are real people somewhere")
         .npc("With real feelings")
         .npc("If you go round being abusive or causing trouble")
         .npc("your character could quickly be the one in trouble");
    }

    // ------------------------------------------------------------ boatman --

    /**
     * The way off.
     *
     * He will take anyone who asks, at any point. On the real island the door
     * behind him was the last of thirteen and he could only be reached by
     * finishing them all; with the doors gone there is nothing to enforce that
     * and nothing to be gained by refusing -- a player who wants out has the
     * skip prompt's answer available to them anyway.
     */
    private void boatman(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello my job is to take you to the main game area")
            .npc("It's only a short row")
            .npc("I shall take you to the small town of Lumbridge")
            .npc("In the kingdom of Misthalin")
            .options(new Choice("Ok I'm ready to go", "I'm not done here yet") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("Ok come back when you are ready");
                        return;
                    }
                    c.npc("Lets go then")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             setStage(FINISHED);
                         }
                     });
                }
            })
            .start();
    }

    /**
     * Published so that code outside a quest can ask whether a player is still
     * in the tutorial -- see Quest.reached for why this is by name.
     */
    public boolean reached(String key) {
        if ("finished".equals(key)) {
            return completed();
        }
        if ("on-island".equals(key)) {
            return questStarted() && !completed();
        }
        return false;
    }
}
