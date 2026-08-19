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
 * Tribal totem. Released 30 April 2002, written by Paul Gower.
 *
 * Lord Handelmort brought the Rantuki tribe's totem back from Karamja and put
 * it in his private museum. Kangai Mau would like it back. Handelmort's mansion
 * has no door anyone can walk to, so the way in is to post yourself there.
 *
 *     Kangai Mau        npc 332, (452,687), the Shrimp and Parrot in Brimhaven
 *     Wizard Cromperty  npc 333, (545,576), north-east Ardougne
 *     RPDT employee     npc 334, (559,615), the parcel depot
 *     Horacio           npc 335, (560,579), Handelmort's gardener
 *
 *     the depot crates  object 290 at (557,614) and (557,615), both empty
 *     the label crate   object 329, (559,617), holding Cromperty's block
 *     the posted crate  object 328, (558,617) in the depot and (560,588)
 *                       in the mansion, which is where it is delivered to
 *     combination door  door 98, (561,586), the passcode is BRAD
 *     picklock door     door 94, (565,586), thieving 21
 *     trapped stairs    object 331, (563,587), "Go up" and "Search for traps"
 *     the chest         object 332, (560,1531)
 *
 *     address label     item 704      tribal totem   item 705
 *     tourist guide     item 706, ground spawn at (563,600), respawn 30
 *
 * The crates are all id 290 in the world except the two the quest needs, which
 * carry ids of their own -- 329 with exactly one placement and 328 with two,
 * the depot one and the mansion one. Jagex numbered the crate you post and the
 * crate it becomes the same, so the object that arrives in the mansion is
 * literally the object that left the depot.
 *
 * Deviations:
 *
 *  - The passcode is chosen from a list of names rather than dialled in. RSC
 *    put four lettered dials on the door; the client has no interface for that
 *    and inventing one is client work, not quest work. What the puzzle actually
 *    asks -- do you know Handelmort's middle name -- survives intact.
 *
 *  - Picklocking door 94 checks thieving and awards its 15 experience here,
 *    because Thieving is not implemented yet and "Pick lock" reaches
 *    WallObjectAction and falls straight through. The door is claimed by its
 *    one placement, not by its id, so the other three picklock doors are left
 *    for whoever writes the skill.
 *
 *  - The chest at (560,1531) is object 332 in the world. An earlier note here
 *    claimed Jagex shipped 333, the closed variant, and that the placement
 *    needed correcting to match; the vanilla world data refutes that
 *    directly -- vanilla has 332 at this tile and there is no 333 anywhere in
 *    the world at all. The world was already right. Searching 332 is the
 *    whole of it here.
 *
 *  - The guard dogs outside the mansion are already in the world and are left
 *    alone; nothing in this quest fights.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class TribalTotem extends Quest {

    public final static int UID = Quests.TRIBAL_TOTEM;

    private static final int KANGAI_MAU = 332, CROMPERTY = 333;
    private static final int RPDT = 334, HORACIO = 335;

    private static final int EMPTY_CRATE = 290;
    private static final int LABEL_CRATE = 329;
    private static final int POST_CRATE = 328;
    private static final int POST_CRATE_X = 558, POST_CRATE_Y = 617;
    private static final int CODE_DOOR = 98;
    private static final int LOCK_DOOR = 94, LOCK_X = 565, LOCK_Y = 586;
    private static final int STAIRS = 331, STAIRS_X = 563, STAIRS_Y = 587;
    private static final int CHEST = 332;

    private static final int LABEL = 704, TOTEM = 705, GUIDE = 706;
    private static final int SWORDFISH = 370, SWORDFISH_COUNT = 5;

    /* Skill 17, thieving. Formulae.statArray called it "quest" until task 38;
       the index was always thieving's. */
    private static final int THIEVING = 17;
    private static final int PICK_LEVEL = 21, PICK_EXP = 15;

    private static final int DEPOT_X = 558, DEPOT_Y = 615;
    private static final int MANSION_X = 560, MANSION_Y = 589;
    private static final int UPSTAIRS_X = 563, UPSTAIRS_Y = 1534;
    private static final int SEWER_X = 569, SEWER_Y = 3438;

    private static final String PASSCODE = "BRAD";

    private static final int STARTED = 1;
    private static final int LABELLED = 2;   /* label stuck on the crate */
    private static final int DELIVERED = 4;  /* and the RPDT took it away */
    private static final int GOT_TOTEM = 8;  /* the chest has been searched */
    private static final int HANDED_IN = 16;
    private static final int FINISHED = 31;

    public TribalTotem(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Tribal totem");
        setFinalStage(FINISHED);
        associateNpc(KANGAI_MAU);
        associateNpc(CROMPERTY);
        associateNpc(RPDT);
        associateNpc(HORACIO);
        /* Spelled out rather than looped, so tools/quest_clash.py can read the
           claims: it parses these calls out of the source and a loop over an
           array hides them from it. */
        associateObject(EMPTY_CRATE, 557, 614);
        associateObject(EMPTY_CRATE, 557, 615);
        associateObject(LABEL_CRATE);
        associateObject(POST_CRATE);
        associateObject(STAIRS);
        associateObject(CHEST);
        associateDoor(CODE_DOOR);
        associateDoor(LOCK_DOOR, LOCK_X, LOCK_Y);
        associateItem(GUIDE);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Lord Handelmort of Ardougne is collector of exotic artifacts. A recent addition to his private collection is a strange looking totem from Karamja. The Rantuki tribe are not happy about the recent disaperance of their totem.");
        setStartPoint("Brimhaven");
        setSpeakTo("Kangai Mau");
        setMissionLength("Medium");
        requireLevel(THIEVING, PICK_LEVEL);
        rewardItem(SWORDFISH, SWORDFISH_COUNT);
        rewardExp(THIEVING, 200, 75);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Tribal totem quest");
    }

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        setStage((questStarted() ? getStage() : 0) | bit);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_COMMAND) {
            readGuide();
            return;
        }
        if (entity instanceof GameObject) {
            GameObject object = (GameObject) entity;
            if (trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2) {
                door(object, trigger == QuestTrigger.DOOR_ACT2);
            } else {
                object(object, trigger, used);
            }
            return;
        }
        if (!(entity instanceof Npc) || trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (((Npc) entity).getID()) {
            case KANGAI_MAU: kangaiMau((Npc) entity); break;
            case CROMPERTY:  cromperty((Npc) entity); break;
            case RPDT:       rpdt((Npc) entity);      break;
            case HORACIO:    horacio((Npc) entity);   break;
            default: break;
        }
    }

    /**
     * The guide. Its whole point is one line of it, so the whole of it is not
     * worth printing: what a player needs is the name.
     */
    private void readGuide() {
        Player p = getOwner();
        p.getActionSender().sendMessage("You read the tourist guide");
        p.getActionSender().sendMessage("@gre@A history of Ardougne");
        p.getActionSender().sendMessage("The mansion north of the market belongs to");
        p.getActionSender().sendMessage("Lord Franis Bradley Handelmort");
        p.getActionSender().sendMessage("a noted explorer and collector of antiquities");
    }

    // ------------------------------------------------------------ scenery --

    private void object(GameObject object, QuestTrigger trigger, InvItem used) {
        Player p = getOwner();
        int id = object.getID();
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            label(object, used);
            return;
        }
        if (id == CHEST) {
            chest();
            return;
        }
        if (id == STAIRS) {
            stairs(trigger == QuestTrigger.OBJECT_ACT2);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            return; /* the crates' only command is Search, and it is the second */
        }
        if (id == EMPTY_CRATE) {
            p.getActionSender().sendMessage("You search the crate");
            p.getActionSender().sendMessage("It is empty");
            return;
        }
        if (id == LABEL_CRATE) {
            labelCrate();
            return;
        }
        if (id == POST_CRATE) {
            p.getActionSender().sendMessage("You search the crate");
            p.getActionSender().sendMessage("It is sealed shut");
            if (object.getX() == POST_CRATE_X && object.getY() == POST_CRATE_Y && has(LABELLED)) {
                p.getActionSender().sendMessage("It is addressed to Lord Handelmort");
            }
        }
    }

    private void labelCrate() {
        Player p = getOwner();
        if (!questStarted()) {
            p.getActionSender().sendMessage("You search the crate");
            p.getActionSender().sendMessage("It is sealed shut");
            return;
        }
        if (p.getInventory().countId(LABEL) > 0 || has(LABELLED)) {
            p.getActionSender().sendMessage("You search the crate");
            p.getActionSender().sendMessage("There is nothing else here");
            return;
        }
        if (!p.getInventory().canHold(new InvItem(LABEL, 1))) {
            p.getActionSender().sendMessage("You don't have room for that");
            return;
        }
        p.getInventory().add(new InvItem(LABEL, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You search the crate");
        p.getActionSender().sendMessage("@gre@You find an address label");
        p.getActionSender().sendMessage("It is addressed to Lord Handelmort");
    }

    private void label(GameObject object, InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != LABEL) {
            return;
        }
        if (object.getID() != POST_CRATE
                || object.getX() != POST_CRATE_X || object.getY() != POST_CRATE_Y) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (has(LABELLED)) {
            p.getActionSender().sendMessage("This crate is already addressed");
            return;
        }
        p.getInventory().remove(LABEL, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You stick the label on the crate");
        p.getActionSender().sendMessage("It is now addressed to Lord Handelmort");
        set(LABELLED);
    }

    /**
     * The stairs. Searching them finds the trap; the search is remembered for
     * the session only, which is the same length of time it takes to walk up
     * them. Anyone who forgets falls into the Ardougne sewers.
     */
    private void stairs(boolean searching) {
        Player p = getOwner();
        if (searching) {
            p.setFlag("totem.trapsafe", 1);
            p.getActionSender().sendMessage("You search the stairs");
            p.getActionSender().sendMessage("@gre@You find a hidden trap");
            p.getActionSender().sendMessage("You carefully disarm it");
            return;
        }
        if (p.getFlag("totem.trapsafe") < 1) {
            p.getActionSender().sendMessage("You step on a hidden trap");
            p.getActionSender().sendMessage("@red@The floor gives way beneath you!");
            int damage = Math.max(1, p.getCurStat(3) / 5);
            p.setCurStat(3, Math.max(0, p.getCurStat(3) - damage));
            p.getActionSender().sendStat(3);
            p.teleport(SEWER_X, SEWER_Y, false);
            return;
        }
        p.teleport(UPSTAIRS_X, UPSTAIRS_Y, false);
    }

    private void chest() {
        Player p = getOwner();
        if (has(GOT_TOTEM) || p.getInventory().countId(TOTEM) > 0) {
            p.getActionSender().sendMessage("You search the chest");
            p.getActionSender().sendMessage("It is empty");
            return;
        }
        if (!p.getInventory().canHold(new InvItem(TOTEM, 1))) {
            p.getActionSender().sendMessage("You don't have room for that");
            return;
        }
        p.getInventory().add(new InvItem(TOTEM, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You search the chest");
        p.getActionSender().sendMessage("@gre@You find the tribal totem");
        if (questStarted()) {
            set(GOT_TOTEM);
        }
    }

    // -------------------------------------------------------------- doors --

    private void door(GameObject door, boolean second) {
        if (door.getID() == LOCK_DOOR) {
            picklock(door, second);
        } else {
            passcode(door);
        }
    }

    /** Direction 0 walls stand between (x,y) and (x,y-1). */
    private void step(GameObject door) {
        Player p = getOwner();
        if (door.getDirection() == 0) {
            p.teleport(door.getX(),
                p.getY() >= door.getY() ? door.getY() - 1 : door.getY(), false);
        } else {
            p.teleport(p.getX() >= door.getX() ? door.getX() - 1 : door.getX(),
                door.getY(), false);
        }
    }

    private void picklock(GameObject door, boolean second) {
        Player p = getOwner();
        if (!second) {
            p.getActionSender().sendMessage("The door is locked");
            p.getActionSender().sendMessage("Maybe you could pick the lock");
            return;
        }
        if (p.getMaxStat(THIEVING) < PICK_LEVEL) {
            p.getActionSender().sendMessage("You need a thieving level of "
                + PICK_LEVEL + " to pick this lock");
            return;
        }
        p.getActionSender().sendMessage("You attempt to pick the lock...");
        sleep(1500);
        p.getActionSender().sendMessage("@gre@The lock clicks open");
        p.incExp(THIEVING, PICK_EXP, false);
        p.getActionSender().sendStat(THIEVING);
        p.getActionSender().sendSound("opendoor");
        step(door);
    }

    private void passcode(final GameObject door) {
        Player p = getOwner();
        new Conversation(p, null)
            .message("The door has four lettered dials set into it")
            .message("What do you set them to?")
            .options(new Choice("BRAD", "FRAN", "MORT", "LORD",
                                "HAND", "GARY", "DOBS", "PAUL") {
                public void picked(int option, Conversation c) {
                    String tried = this.getOptions()[option];
                    if (!PASSCODE.equals(tried)) {
                        c.message("You set the dials to " + tried)
                         .message("Nothing happens");
                        return;
                    }
                    c.message("You set the dials to " + tried)
                     .message("@gre@The lock clicks open")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             getOwner().getActionSender().sendSound("opendoor");
                             step(door);
                         }
                     });
                }
            })
            .start();
    }

    // ------------------------------------------------------------ talkers --

    private void kangaiMau(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("greetings esteemed thief")
                .start();
            return;
        }
        if (questStarted()) {
            if (p.getInventory().countId(TOTEM) > 0) {
                new Conversation(p, npc)
                    .npc("Have you got our totem back?")
                    .player("Yes I have")
                    .take(TOTEM, 1)
                    .npc("Thank you brave adventurer")
                    .npc("Here have some freshly cooked Karamja fish")
                    .npc("Caught specially by our people")
                    .then(new Effect() {
                        public void run(Conversation c) {
                            /* Handing the totem back is the quest, whatever
                               route got it out of the mansion. The heist can
                               legitimately skip the crate bits (the doors are
                               puzzles, not stage gates), so completion cannot
                               depend on them. */
                            set(FINISHED);
                        }
                    })
                    .start();
                return;
            }
            new Conversation(p, npc)
                .npc("Have you got our totem back?")
                .player("No it's not that easy")
                .npc("Bah, you no good")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Hello I Kangai Mau")
            .npc("Of the Rantuki tribe")
            .options(new Choice("And what are you doing in Brimhaven?",
                                "I'm in search of adventure",
                                "Who are the Rantuki tribe?") {
                public void picked(int option, Conversation c) {
                    if (option == 2) {
                        c.npc("A proud and noble tribe of Karamja")
                         .npc("Now we are few")
                         .npc("Men come from across sea")
                         .npc("And settle on our hunting grounds");
                        return;
                    }
                    if (option == 1) {
                        c.npc("Adventure is something I may be able to give");
                        theMission(c);
                        return;
                    }
                    c.npc("I looking for someone brave")
                     .npc("To go on important mission for me")
                     .npc("Someone skilled in thievery and sneaking about")
                     .npc("I am told I can find such people in Brimhaven")
                     .options(new Choice("Tell me of this mission, I may be able to help",
                                         "Yep I have heard there are many of that type here") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 return;
                             }
                             theMission(c);
                         }
                     });
                }
            })
            .start();
    }

    private void theMission(Conversation c) {
        c.npc("I need someone to go on a mission")
         .npc("To the city of Ardougne")
         .npc("There you will need to find the house of Lord Handelmort")
         .npc("In his house he has our tribal totem")
         .npc("We need it back")
         .options(new Choice("Ok I will get it back",
                             "Why does he have it?",
                             "How can I find Handelmort's house?") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Lord Handelmort is an Ardougnese explorer")
                      .npc("Which mean he think he allowed to come and steal our stuff")
                      .npc("To put in his private museum");
                     return;
                 }
                 if (option == 2) {
                     c.player("Ardougne is a big place")
                      .npc("I don't know Ardougne");
                     return;
                 }
                 c.then(new Effect() {
                     public void run(Conversation c) {
                         setStage(STARTED);
                     }
                 });
             }
         });
    }

    private void rpdt(Npc npc) {
        Player p = getOwner();
        if (!has(LABELLED) || has(DELIVERED)) {
            new Conversation(p, npc)
                .npc("Welcome to RPDT")
                .player("Thank you very much")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Welcome to RPDT")
            .options(new Choice("So when are you going to deliver this crate?",
                                "Thank you, it's interesting in here") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("I suppose I could do it now")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             set(DELIVERED);
                             c.getPlayer().getActionSender().sendMessage(
                                 "@gre@The crate is loaded up and taken away");
                         }
                     });
                }
            })
            .start();
    }

    private void cromperty(Npc npc) {
        Player p = getOwner();
        /*
         * The essence-mine line lives in Tribal totem's file because the
         * conversation dispatcher gives an npc one owner and Cromperty has
         * been this quest's since it was written; Rune mysteries only lends
         * him the teleport. The option appears for finishers of that quest
         * and goes through EssenceMine like every other teleporter wizard.
         */
        final boolean essence = p.getQuestManager()
            .completed(org.rscdaemon.server.quest.Quests.RUNE_MYSTERIES);
        String[] opening = essence
            ? new String[]{"So what have you invented?",
                           "Two jobs, thats got to be tough",
                           "Well I shall leave you to your inventing",
                           "Can you teleport me to the rune essence mine?"}
            : new String[]{"So what have you invented?",
                           "Two jobs, thats got to be tough",
                           "Well I shall leave you to your inventing"};
        new Conversation(p, npc)
            .npc("Hello there")
            .npc("My name is Cromperty")
            .npc("I am a wizard and an inventor")
            .options(new Choice(opening) {
                public void picked(int option, Conversation c) {
                    if (option == 2) {
                        return;
                    }
                    if (option == 3) {
                        c.npc("Senventior disthine molenko!")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 c.stop();
                                 org.rscdaemon.server.model.EssenceMine
                                     .teleportIn(c.getPlayer(), 333);
                             }
                         });
                        return;
                    }
                    if (option == 1) {
                        c.npc("Not when you combine them it isn't")
                         .npc("I invent magic things");
                    }
                    c.npc("My latest inevention is my patent pending teleport block")
                     .npc("Stand on this block here")
                     .npc("I do a bit of the old hocus pocus")
                     .npc("And abracadabra you end up on the other teleport block")
                     .options(new Choice("Can I be teleported please?",
                                         "So where is the other block?",
                                         "Who are the RPDT?",
                                         "Well done, that's very clever") {
                         public void picked(int option, Conversation c) {
                             if (option == 1) {
                                 c.npc("I would guess somewhere between here and the wizards tower in Misthalin")
                                  .npc("All I know is it hasn't got there yet")
                                  .npc("Or the wizards there would have contacted me")
                                  .npc("I am using the RPDT to deliver it");
                                 return;
                             }
                             if (option == 2) {
                                 c.npc("The runescape parcel delivery team");
                                 return;
                             }
                             if (option == 3) {
                                 return;
                             }
                             c.npc("By all means")
                              .npc("Though I don't know where you will come out")
                              .npc("Wherever the other teleport block is I suppose")
                              .options(new Choice("Yes, that sounds good, teleport me",
                                                  "That sounds dangerous leave me here") {
                                  public void picked(int option, Conversation c) {
                                      if (option != 0) {
                                          return;
                                      }
                                      c.then(new Effect() {
                                          public void run(Conversation c) {
                                              teleportOut();
                                          }
                                      });
                                  }
                              });
                         }
                     });
                }
            })
            .start();
    }

    /** Wherever the block happens to be sitting today. */
    private void teleportOut() {
        Player p = getOwner();
        p.getActionSender().sendTeleBubble(p.getX(), p.getY(), false);
        if (has(DELIVERED)) {
            p.teleport(MANSION_X, MANSION_Y, true);
        } else {
            p.teleport(DEPOT_X, DEPOT_Y, true);
        }
    }

    private void horacio(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc)
            .npc("It's a fine day to be out in the garden isn't it?")
            .options(new Choice("So who are you", "Yes, it's very nice") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("My name is Horacio Dobson")
                     .npc("I am the gardener to Lord Handelmort")
                     .npc("All this around you is my handywork");
                    if (!questStarted()) {
                        return;
                    }
                    c.options(new Choice("So do you garden round the back too?",
                                         "Do you need any help?") {
                        public void picked(int option, Conversation c) {
                            if (option != 0) {
                                c.npc("Trying to muscle in on my job ehh?")
                                 .npc("I'm happy to do this all myself");
                                return;
                            }
                            c.npc("That I do")
                             .player("Doesn't all this security in this house")
                             .player("get in your way?")
                             .npc("Ah, I'm used to all that")
                             .npc("I have my keys, the dogs knows me")
                             .npc("And I know by heart the combination to the door lock")
                             .npc("It's rather easy, it's his middle name")
                             .player("Who's middle name?")
                             .npc("Hmm I shouldn't have said that")
                             .npc("Forget I said it");
                        }
                    });
                }
            });
        c.start();
    }
}
