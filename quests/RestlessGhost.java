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
 * The restless ghost.
 *
 * Father Aereck has a ghost in his graveyard. His friend Father Urhney, hiding
 * out in the Lumbridge swamp, hands over an amulet of ghostspeak; wearing it,
 * the ghost can explain that a warlock took his skull. The skull is in the
 * wizards' tower, and putting it back in his coffin lays him to rest.
 *
 * Three npcs and an object, and the stage is what keeps them in step -- each of
 * the three has a different line for each stage, and Aereck in particular has
 * five. It is the first quest here where the stage is doing real work rather
 * than just recording that the player turned up.
 *
 * Two of the stages look redundant and are not. HAS_SKULL exists because Aereck
 * greets you differently once you have found the skull even if you have since
 * put it down -- the wiki transcript notes it is not checked against the
 * inventory -- so it has to be remembered rather than derived.
 *
 * Positions, for anyone checking this against the world files: Aereck (npc 9) is
 * at (113, 667), the ghost (npc 15) at (103, 674), his coffin (object 40) at
 * (103, 675) right beside him, and Urhney (npc 10) at (116, 711) out in the
 * swamp. The ghost, Urhney and the skull all had no spawn at all until
 * tools/restore_spawns.py put them back.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class RestlessGhost extends Quest {

    public final static int UID = Quests.THE_RESTLESS_GHOST;

    /** Aereck has asked; Urhney has not been found. */
    private static final int STARTED = 1;
    /** Urhney has handed over the amulet. */
    private static final int HAS_AMULET = 2;
    /** The ghost has explained about the skull. */
    private static final int KNOWS_ABOUT_SKULL = 3;
    /** The skull has been picked up at least once. */
    private static final int HAS_SKULL = 4;
    private static final int FINISHED = 5;

    private static final int AERECK = 9;
    private static final int URHNEY = 10;
    private static final int GHOST = 15;
    private static final int COFFIN = 40;

    private static final int GHOSTSPEAK = 24;
    /** id 412 is a same-named, same-sprite decorative "skull" scattered
        around the world as dungeon dressing -- it is not this item. The real
        Jagex cache places the quest skull at id 27, in the tower basement. */
    private static final int SKULL = 27;

    private static final int PRAYER = 5; /* skill index */

    public RestlessGhost(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("The restless ghost");
        setFinalStage(FINISHED);
        associateNpc(AERECK);
        associateNpc(URHNEY);
        associateNpc(GHOST);
        associateObject(COFFIN);
        associateItem(SKULL);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("A ghost is haunting Lumbridge graveyard. The priest of the Lumbridge church of Saradomin wants you to find out how to get rid of it.");
        setStartPoint("Lumbridge");
        setSpeakTo("Priest");
        setMissionLength("Medium");
        /* The prayer xp is level x 62.5 + 500 -- a fractional per-level rate
           that rewardExp's integer base + perLevel form cannot represent, so
           completeQuest() keeps granting it imperatively. */
        rewardOther("Prayer experience of (level x 62.5) + 500");
    }

    public void completeQuest() {
        grantRewards();
        Player p = getOwner();
        p.getActionSender().sendMessage("Well done.You have completed the Restless ghost quest");
        // Level x 62.5 + 500. Doubled and halved rather than written as a float,
        // so an odd level rounds the same way every time instead of depending on
        // how the division is spelled.
        p.incExp(PRAYER, ((p.getMaxStat(PRAYER) * 125) / 2) + 500, false);
        p.getActionSender().sendStat(PRAYER);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT && entity instanceof GameObject) {
            if (((GameObject) entity).getID() == COFFIN) {
                useOnCoffin(used);
            }
            return;
        }
        triggerEntity(trigger, entity);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc && trigger == QuestTrigger.NPC_TALK) {
            switch (((Npc) entity).getID()) {
                case AERECK:
                    talkToAereck((Npc) entity);
                    break;
                case URHNEY:
                    talkToUrhney((Npc) entity);
                    break;
                case GHOST:
                    talkToGhost((Npc) entity);
                    break;
            }
        } else if (entity instanceof GameObject && ((GameObject) entity).getID() == COFFIN) {
            openCoffin();
        } else if (entity instanceof InvItem && trigger == QuestTrigger.ITEM_PICKUP) {
            if (((InvItem) entity).getID() == SKULL && getStage() == KNOWS_ABOUT_SKULL) {
                setStage(HAS_SKULL);
            }
        }
    }

    // ------------------------------------------------------- Father Aereck --

    private void talkToAereck(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (questStarted() && !completed()) {
            c.npc("Have you got rid of the ghost yet?");
            switch (getStage()) {
                case STARTED:
                    c.player("I can't find father Urhney at the moment")
                     .npc("Well to get to the swamp he is in")
                     .npc("you need to go round the back of the castle")
                     .npc("The swamp is on the otherside of the fence to the south")
                     .npc("You'll have to go through the wood to the west to get round the fence")
                     .npc("Then you'll have to go right into the eastern depths of the swamp");
                    break;
                case HAS_AMULET:
                    c.player("I had a talk with father Urhney")
                     .player("He has given me this funny amulet to talk to the ghost with")
                     .npc("I always wondered what that amulet was")
                     .npc("Well I hope it's useful. Tell me if you get rid of the ghost");
                    break;
                case KNOWS_ABOUT_SKULL:
                    c.player("I've found out that the ghost's corpse has lost its skull")
                     .player("If I can find the skull the ghost will go")
                     .npc("That would explain it")
                     .npc("Well I haven't seen any skulls")
                     .player("Yes I think a warlock has stolen it")
                     .npc("I hate warlocks")
                     .npc("Ah well good luck");
                    break;
                default:
                    c.player("I've finally found the ghost's skull")
                     .npc("Great. Put it in the ghost's coffin and see what happens!");
                    break;
            }
            c.start();
            return;
        }

        c.npc("Welcome to the church of holy Saradomin")
         .options(new Choice("Who's Saradomin?",
                             "Nice place you've got here",
                             "I'm looking for a quest") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     saradomin(c);
                 } else if (option == 1) {
                     c.npc("It is, isn't it?")
                      .npc("It was built 230 years ago");
                 } else if (completed()) {
                     c.npc("Sorry I only had the one quest");
                 } else {
                     c.npc("That's lucky, I need someone to do a quest for me")
                      .player("Ok I'll help")
                      .npc("Ok the problem is, there is a ghost in the church graveyard")
                      .npc("I would like you to get rid of it")
                      .npc("If you need any help")
                      .npc("My friend father Urhney is an expert on ghosts")
                      .npc("I believe he is currently living as a hermit")
                      .npc("He has a little shack somewhere in the swamps south of here")
                      .npc("I'm sure if you told him that I sent you he'd be willing to help")
                      .npc("My name is father Aereck by the way")
                      .npc("Be careful going through the swamps")
                      .npc("I have heard they can be quite dangerous")
                      .then(new Effect() {
                          public void run(Conversation c) {
                              setStage(STARTED);
                          }
                      });
                 }
             }
         })
         .start();
    }

    private void saradomin(Conversation c) {
        c.npc("Surely you have heard of the God, Saradomin?")
         .npc("He who creates the forces of goodness and purity in this world?")
         .npc("I cannot believe your ignorance!")
         .npc("This is the God with more followers than any other!")
         .npc("At least in these parts!")
         .npc("He who along with his brothers Guthix and Zamorak created this world")
         .options(new Choice("Oh that Saradomin",
                             "Oh sorry I'm not from this world") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("There is only one Saradomin");
                     return;
                 }
                 c.npc("That's strange")
                  .npc("I thought things not from this world were all slime and tenticles")
                  .options(new Choice("You don't understand. This is a computer game",
                                      "I am - do you like my disguise?") {
                      public void picked(int option, Conversation c) {
                          if (option == 0) {
                              c.npc("I beg your pardon?")
                               .player("Never mind");
                          } else {
                              c.npc("Aargh begone foul creature from another dimension")
                               .player("Ok, Ok, It was a joke");
                          }
                      }
                  });
             }
         });
    }

    // ------------------------------------------------------- Father Urhney --

    private void talkToUrhney(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Go away, I'm meditating");

        // The amulet is replaceable, and has to be: it is the only way to talk
        // to the ghost, and it can be dropped or left in the bank.
        boolean needsAmulet = getStage() >= HAS_AMULET && !completed()
                && getOwner().getInventory().countId(GHOSTSPEAK) == 0;

        if (getStage() == STARTED) {
            c.options(new Choice("Father Aereck sent me to talk to you",
                                 "Well that's friendly",
                                 "I've come to repossess your house") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("I said go away!").player("Ok, ok");
                    } else if (option == 2) {
                        repossess(c);
                    } else {
                        c.npc("I suppose I'd better talk to you then")
                         .npc("What problems has he got himself into this time?")
                         .options(new Choice("He's got a ghost haunting his graveyard",
                                             "You mean he gets himself into lots of problems?") {
                             public void picked(int option, Conversation c) {
                                 if (option == 1) {
                                     c.npc("Yeah. For example when we were trainee priests")
                                      .npc("He kept on getting stuck up bell ropes")
                                      .npc("Anyway I don't have time for chitchat")
                                      .npc("What's his problem this time?")
                                      .player("He's got a ghost haunting his graveyard");
                                 }
                                 giveAmulet(c);
                             }
                         });
                    }
                }
            });
        } else if (needsAmulet) {
            c.options(new Choice("I've lost the amulet",
                                 "Well that's friendly") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("I said go away!").player("Ok, ok");
                        return;
                    }
                    c.npc("How careless can you get")
                     .npc("Those things aren't easy to come by you know")
                     .npc("It's a good job I've got a spare")
                     .npc("Be more careful this time")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             c.getPlayer().getInventory().add(new InvItem(GHOSTSPEAK, 1));
                             c.getPlayer().getActionSender().sendInventory();
                         }
                     })
                     .player("Ok I'll try to be");
                }
            });
        } else {
            c.options(new Choice("Well that's friendly",
                                 "I've come to repossess your house") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("I said go away!").player("Ok, ok");
                    } else {
                        repossess(c);
                    }
                }
            });
        }
        c.start();
    }

    private void giveAmulet(Conversation c) {
        c.npc("Oh the silly fool")
         .npc("I leave town for just five months")
         .npc("and already he can't manage")
         .npc("Sigh")
         .npc("Well I can't go back and exorcise it")
         .npc("I vowed not to leave this place")
         .npc("Until I had done a full two years of prayer and meditation")
         .npc("Tell you what I can do though")
         .npc("Take this amulet")
         .then(new Effect() {
             public void run(Conversation c) {
                 c.getPlayer().getInventory().add(new InvItem(GHOSTSPEAK, 1));
                 c.getPlayer().getActionSender().sendInventory();
                 setStage(HAS_AMULET);
             }
         })
         .npc("It is an amulet of Ghostspeak")
         .npc("So called because when you wear it you can speak to ghosts")
         .npc("A lot of ghosts are doomed to be ghosts")
         .npc("Because they have left some task uncompleted")
         .npc("Maybe if you know what this task is")
         .npc("You can get rid of the ghost")
         .npc("I'm not making any guarantees mind you")
         .npc("But it is the best I can do right now")
         .player("Thank you, I'll give it a try");
    }

    private void repossess(Conversation c) {
        c.npc("Under what grounds?")
         .options(new Choice("Repeated failure on mortgage payments",
                             "I don't know, I just wanted this house") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("I don't have a mortgage")
                      .npc("I built this house myself")
                      .player("Sorry I must have got the wrong address")
                      .player("All the houses look the same around here");
                 } else {
                     c.npc("Oh go away and stop wasting my time");
                 }
             }
         });
    }

    // -------------------------------------------------------------- ghost --

    private void talkToGhost(Npc npc) {
        if (completed()) {
            getOwner().getActionSender().sendMessage("The ghost doesn't appear interested in talking.");
            return;
        }
        if (!getOwner().getInventory().wielding(GHOSTSPEAK)) {
            wooWoo(npc);
            return;
        }

        Conversation c = new Conversation(getOwner(), npc);
        if (getStage() == KNOWS_ABOUT_SKULL || getStage() == HAS_SKULL) {
            c.npc("How are you doing finding my skull?");
            if (getOwner().getInventory().countId(SKULL) > 0) {
                c.player("I have found it")
                 .npc("Hurrah now I can stop being a ghost")
                 .npc("You just need to put it in my coffin over there")
                 .npc("And I will be free");
            } else {
                c.player("Sorry, I can't find it at the moment")
                 .npc("Ah well keep on looking")
                 .npc("I'm pretty sure it's somewhere in the tower south west from here")
                 .npc("There's a lot of levels to the tower, though")
                 .npc("I suppose it might take a little while to find");
            }
            c.start();
            return;
        }

        c.player("Hello ghost, how are you?")
         .npc("Not very good actually")
         .player("What's the problem then?")
         .npc("Did you just understand what I said?")
         .options(new Choice("Yep, now tell me what the problem is",
                             "Wow, this amulet works",
                             "No, you sound like you're speaking non-sense to me") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("Wow this is incredible, I didn't expect any one to understand me again")
                      .player("Yes, yes I can understand you")
                      .player("But have you any idea why you are doomed to be a ghost?")
                      .npc("I'm not sure")
                      .player("I have been told a certain task may need to be completed")
                      .player("So you can rest in peace");
                     aboutTheSkull(c);
                     return;
                 }
                 if (option == 1) {
                     c.npc("Oh its your amulet that's doing it. I did wonder")
                      .npc("I don't suppose you can help me? I don't like being a ghost")
                      .options(new Choice("Yes, Ok do you know why you're a ghost?",
                                          "No, you're scary") {
                          public void picked(int option, Conversation c) {
                              if (option != 0) {
                                  return;
                              }
                              c.npc("No, I just know I can't do anything much like this")
                               .player("I've been told a certain task may need to be completed")
                               .player("So you can rest in peace");
                              aboutTheSkull(c);
                          }
                      });
                     return;
                 }
                 c.npc("Oh that's a pity. You got my hopes up there")
                  .player("Yeah, it is pity. Sorry")
                  .npc("Hang on a second. You can understand me")
                  .options(new Choice("Yep clever aren't I",
                                      "No I can't") {
                      public void picked(int option, Conversation c) {
                          if (option != 0) {
                              c.npc("I don't know, the first person I can speak to in ages is a moron");
                              return;
                          }
                          c.npc("I'm impressed")
                           .npc("You must be very powerfull")
                           .npc("I don't suppose you can stop me being a ghost?");
                          aboutTheSkull(c);
                      }
                  });
             }
         }.says(2, "No"))
         .start();
    }

    private void aboutTheSkull(Conversation c) {
        c.npc("I should think it is probably because")
         .npc("A warlock has come along and stolen my skull")
         .npc("If you look inside my coffin there")
         .npc("you'll find my corpse without a head on it")
         .player("Do you know where this warlock might be now?")
         .npc("I think it was one of the warlocks who lives in a big tower")
         .npc("In the sea southwest from here")
         .player("Ok I will try and get the skull back for you, so you can rest in peace.")
         .npc("Ooh thank you, That would be such a great relief")
         .npc("It is so dull being a ghost")
         .then(new Effect() {
             public void run(Conversation c) {
                 if (getStage() < KNOWS_ABOUT_SKULL) {
                     setStage(KNOWS_ABOUT_SKULL);
                 }
             }
         });
    }

    /** Talking to a ghost without the amulet on, which goes exactly nowhere. */
    private void wooWoo(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("Hello ghost, how are you?")
            .npc("Wooo wooo wooooo")
            .options(new Choice("Sorry I don't speak ghost",
                                "Ooh that's interesting",
                                "Any hints where I can find some treasure?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("Woo woo?")
                         .player("Nope still don't understand you")
                         .npc("Woooooooo")
                         .player("Never mind");
                        return;
                    }
                    if (option == 2) {
                        c.npc("Wooooooo woo!")
                         .player("Thank you. You've been very helpfull")
                         .npc("Wooooooo");
                        return;
                    }
                    c.npc("Woo wooo")
                     .npc("Woooooooooooooooooo")
                     .player("Did he really?")
                     .npc("Woo")
                     .player("Yeah that's what I thought")
                     .npc("Wooo woooooooooooooo")
                     .player("Goodbye. Thanks for the chat")
                     .npc("Wooo wooo");
                }
            })
            .start();
    }

    // ------------------------------------------------------------- coffin --

    private void openCoffin() {
        Player p = getOwner();
        if (getStage() >= KNOWS_ABOUT_SKULL && !completed()) {
            p.getActionSender().sendMessage("You open the coffin.");
            p.getActionSender().sendMessage("The skeleton inside it has no skull.");
        } else {
            p.getActionSender().sendMessage("You open the coffin.");
            p.getActionSender().sendMessage("There is a skeleton inside.");
        }
    }

    private void useOnCoffin(InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != SKULL) {
            p.getActionSender().sendMessage("Nothing interesting happens.");
            return;
        }
        if (completed() || getStage() < KNOWS_ABOUT_SKULL) {
            p.getActionSender().sendMessage("Nothing interesting happens.");
            return;
        }
        p.getInventory().remove(SKULL, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You put the skull in the coffin.");
        p.getActionSender().sendMessage("The ghost fades away, at rest at last.");
        setStage(getFinalStage());
    }
}
