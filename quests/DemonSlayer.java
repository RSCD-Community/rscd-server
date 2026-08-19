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
 * Demon slayer.
 *
 * Gypsy Aris reads the player's fortune and sees Delrith, the demon the hero
 * Wally sealed into the stone circle south of Varrock a hundred and fifty years
 * ago. Wally used the sword Silverlight and an incantation; both are needed
 * again. Sir Prysin has Silverlight locked in a box that takes three keys, and
 * he has lost track of all three: one is with Captain Rovin at the top of the
 * guard house, one is with Wizard Traiborn who will not open his closet without
 * twenty-five sets of bones, and one Prysin himself dropped down the kitchen
 * drain.
 *
 * Three quest points and Silverlight. No experience.
 *
 * The bone count for Traiborn is saved (a quest var slot), for the same reason
 * Pirate's treasure saves the bananas in the crate: he removes the bones from
 * the inventory as they are handed over, so losing the count to a logout or a
 * restart would silently eat every set already given.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class DemonSlayer extends Quest {

    public final static int UID = Quests.DEMON_SLAYER;

    /** The gypsy has named Delrith and taught the incantation. */
    private static final int STARTED = 1;
    /** Sir Prysin has explained where the three keys went. */
    private static final int SEEK_KEYS = 2;
    /** The box is open and Silverlight is the player's. */
    private static final int HAS_SILVERLIGHT = 3;
    private static final int FINISHED = 4;

    private static final int GYPSY = 14;
    private static final int SIR_PRYSIN = 16;
    private static final int TRAIBORN = 17;
    private static final int CAPTAIN_ROVIN = 18;
    private static final int DELRITH = 35;

    /** The drainpipe from the palace kitchen down to the sewers, at (117,461). */
    private static final int DRAIN = 77;

    private static final int COINS = 10;
    private static final int BONES = 20;
    private static final int BUCKET = 21;
    private static final int BUCKET_OF_WATER = 50;
    private static final int SILVERLIGHT = 52;
    private static final int SPINACH_ROLL = 179;

    /** Traiborn's, Rovin's, and the one down the drain. */
    private static final int KEY_TRAIBORN = 25;
    private static final int KEY_ROVIN = 26;
    private static final int KEY_DRAIN = 51;

    private static final int BONES_WANTED = 25;
    private static final int FORTUNE_PRICE = 1;

    /**
     * Where the key lands when the drain is flushed: the sewer directly below
     * the kitchen, beside the pipe the water comes out of.
     */
    private static final int SEWER_X = 116;
    private static final int SEWER_Y = 3295;

    /**
     * The four sets of words, in the order Delrith offers them. Only one is the
     * order the gypsy gave -- Carlem, Aber, Camerinthum, Purchai, Gabindo.
     */
    private static final String[] INCANTATIONS = new String[]{
        "Carlem Gabindo Purchai Zaree Camerinthum",
        "Purchai Zaree Gabindo Carlem Camerinthum",
        "Purchai Camerinthum Aber Gabindo Carlem",
        "Carlem Aber Camerinthum Purchai Gabindo"
    };
    private static final int RIGHT_WORDS = 3;

    /**
     * Bones given to Traiborn so far, and whether he has been asked about the
     * closet (his greeting changes). Both persisted: he removes bones from the
     * inventory as they are handed over, so a logout mid-errand with a
     * session-only count would eat every set already given.
     */
    private static final int VAR_BONES = 0;
    private static final int VAR_ERRAND = 1;

    private int bonesGiven() { return getVar(VAR_BONES, 0); }
    private void setBonesGiven(int n) { setVar(VAR_BONES, n); }
    private boolean bonesErrand() { return getVar(VAR_ERRAND, 0) == 1; }
    private void setBonesErrand(boolean b) { setVar(VAR_ERRAND, b ? 1 : 0); }

    /**
     * Set while the vortex is open and the menu is on screen, so a second
     * killing blow cannot stack a second prompt on top of the first.
     */
    private boolean vortexOpen = false;

    /**
     * Set by the right incantation. It is what lets the very next kill through
     * {@link #refusesKill}, and it is read again in {@link #killedDelrith} to
     * tell a banishment from an ordinary death.
     */
    private boolean banished = false;

    public DemonSlayer(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Demon slayer");
        setFinalStage(FINISHED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("A mighty demon is being summoned to destroy the city of Varrock. You find out you are the one destined to stop him.(or at least to try)");
        setStartPoint("Varrock Square");
        setSpeakTo("Gypsy");
        setMissionLength("Medium");
        require("Able to defeat an apocalyptic demon");
        require("1 coin to pay the gypsy");
        rewardOther("The sword Silverlight, kept from the quest");

        associateNpc(GYPSY);
        associateNpc(SIR_PRYSIN);
        associateNpc(TRAIBORN);
        associateNpc(CAPTAIN_ROVIN);
        associateNpc(DELRITH);
        associateObject(DRAIN);
    }

    public void completeQuest() {
        // The completion line is sent by killedDelrith() itself -- "You have
        // completed the demonslayer quest", verbatim from the transcript,
        // lowercase and all -- so there is nothing to repeat here. The green
        // quest-points line comes from Quest.setStage centrally.
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof GameObject) {
            // The drain's commands are WalkTo/Search, so Search is the second
            // action, not the first.
            if (trigger == QuestTrigger.OBJECT_ACT2
                    && ((GameObject) entity).getID() == DRAIN) {
                searchDrain();
            }
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.NPC_KILLED) {
            if (npc.getID() == DELRITH) {
                killedDelrith();
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case GYPSY:         talkToGypsy(npc);   break;
            case SIR_PRYSIN:    talkToPrysin(npc);  break;
            case TRAIBORN:      talkToTraiborn(npc); break;
            case CAPTAIN_ROVIN: talkToRovin(npc);   break;
            case DELRITH:       talkToDelrith(npc); break;
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT && entity instanceof GameObject
                && ((GameObject) entity).getID() == DRAIN) {
            flushDrain(used);
            return;
        }
        triggerEntity(trigger, entity);
    }

    private boolean has(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private boolean allThreeKeys() {
        return has(KEY_TRAIBORN) && has(KEY_ROVIN) && has(KEY_DRAIN);
    }

    // -------------------------------------------------------------- gypsy --

    private void talkToGypsy(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("Greetings young one")
             .npc("You're a hero now")
             .npc("That was a good bit of demonslaying")
             .options(new Choice("Thanks", "How do you know I killed it?") {
                 public void picked(int option, Conversation c) {
                     if (option == 1) {
                         c.npc("You forget")
                          .npc("I'm good at knowing these things");
                     }
                 }
             });
            c.start();
            return;
        }

        if (questStarted()) {
            if (has(SILVERLIGHT)) {
                c.npc("How goes the quest?")
                 .player("I have the sword, now. I just need to kill the demon I think")
                 .npc("Yep, that's right")
                 .start();
                return;
            }
            c.npc("Greetings how goes thy quest?")
             .player("I'm still working on it")
             .npc("Well if you need my advice I'm always here young one")
             .options(new Choice("What is the magical incantion?",
                                 "Where can I find Silverlight?",
                                 "Well I'd better press on with it") {
                 public void picked(int option, Conversation c) {
                     if (option == 0) {
                         incantation(c);
                     } else if (option == 1) {
                         whereIsSilverlight(c);
                     } else {
                         c.npc("See you anon");
                     }
                 }
             });
            c.start();
            return;
        }

        c.npc("Hello, young one")
         .npc("Cross my palm with silver and the future will be revealed to you")
         .options(new Choice("Ok, here you go",
                             "Who are you calling young one?!",
                             "No, I don't believe in that stuff") {
             public void picked(int option, Conversation c) {
                 if (option == 2) {
                     c.npc("Ok suit yourself");
                     return;
                 }
                 if (option == 1) {
                     c.npc("You have been on this world")
                      .npc("A relatively short time")
                      .npc("At least compared to me")
                      .npc("So do you want your fortune told or not?");
                 }
                 if (c.getPlayer().getInventory().countId(COINS) < FORTUNE_PRICE) {
                     c.player("Oh dear. I don't have any money");
                     return;
                 }
                 c.take(COINS, FORTUNE_PRICE);
                 fortune(c);
             }
         });
        c.start();
    }

    private void fortune(Conversation c) {
        c.npc("Come closer")
         .npc("And listen carefully to what the future holds for you")
         .npc("As I peer into the swirling mists of the crystal ball")
         .npc("I can see images forming")
         .npc("I can see you")
         .npc("You are holding a very impressive looking sword")
         .npc("I'm sure I recognise that sword")
         .npc("There is a big dark shadow appearing now")
         .npc("Aaargh")
         .player("Very interesting what does the Aaargh bit mean?")
         .npc("Aaargh its Delrith")
         .npc("Delrith is coming")
         .player("Who's Delrith?")
         .npc("Delrith")
         .npc("Delrtih is a powerfull demon")
         .npc("Oh I really hope he didn't see me")
         .npc("Looking at him through my crystal ball")
         .npc("He tried to destroy this city 150 years ago")
         .npc("He was stopped just in time, by the great hero Wally")
         .npc("Wally managed to trap the demon")
         .npc("In the stone circle just south of this city")
         .npc("Using his magic sword silverlight")
         .npc("Ye Gods")
         .npc("Silverlight was the sword you were holding the ball vision")
         .npc("You are the one destined to try and stop the demon this time")
         .player("How am I meant to fight a demon who can destroy cities?")
         .npc("I admit it won't be easy")
         .npc("Wally managed to arrive at the stone circle")
         .npc("Just as Delrith was summoned by a cult of chaos druids")
         .npc("By reciting the correct magical incantation")
         .npc("and thrusting Silverlight into Delright, while he was newly summoned")
         .npc("Wally was able to imprison Delrith")
         .npc("in the stone block in the centre of the circle")
         .npc("Delrith will come forth from the stone circle again")
         .npc("I would imagine an evil sorcerer is already starting on the rituals")
         .npc("To summon Delrith as we speak")
         .player("What is the magical incantion?");
        incantation(c);
        c.then(new Effect() {
            public void run(Conversation c) {
                if (!questStarted()) {
                    setStage(STARTED);
                }
            }
        })
         .options(new Choice("Where can I find Silverlight?",
                             "Ok thanks I'll do my best to stop the Demon") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     whereIsSilverlight(c);
                 } else {
                     c.npc("Good luck, may Guthix be with you");
                 }
             }
         });
    }

    private void incantation(Conversation c) {
        c.npc("Oh yes let me think a second")
         .npc("Alright I've got it now I think")
         .npc("It goes")
         .npc("Carlem")
         .npc("Aber")
         .npc("Camerinthum")
         .npc("Purchai")
         .npc("Gabindo")
         .npc("Have you got that?")
         .player("I think so, yes");
    }

    private void whereIsSilverlight(Conversation c) {
        c.npc("Silverlight has been passed down through Wally's descendents")
         .npc("I believe it is currently in the care of one of the king's knights")
         .npc("called Sir Prysin")
         .npc("He shouldn't be to hard to find the he lives in the royal palace in this city")
         .npc("Tell him Gypsy Aris sent you");
    }

    // ------------------------------------------------------------- prysin --

    private void talkToPrysin(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("Hello, I've heard you stopped that demon well done")
             .player("Yes, that's right")
             .npc("A good job well done then")
             .player("Thank you")
             .start();
            return;
        }

        if (getStage() == HAS_SILVERLIGHT) {
            c.npc("You sorted that demon yet?")
             .player("No, not yet")
             .npc("Well get on with it")
             .npc("He'll be pretty powerful when he gets to full strength")
             .start();
            return;
        }

        if (getStage() == SEEK_KEYS) {
            c.npc("So how are you doing with getting the keys?");
            if (allThreeKeys()) {
                c.player("I've got them all")
                 .npc("Excellent. Now I can give you Silverlight")
                 .take(KEY_TRAIBORN, 1)
                 .take(KEY_ROVIN, 1)
                 .take(KEY_DRAIN, 1)
                 .give(new InvItem(SILVERLIGHT, 1))
                 .message("Sir Prysin gives you Silverlight")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         setStage(HAS_SILVERLIGHT);
                     }
                 })
                 .start();
                return;
            }
            c.player("I've not found any of them yet")
             .options(new Choice("Can you remind me where all the keys were again",
                                 "I'm still looking") {
                 public void picked(int option, Conversation c) {
                     if (option == 1) {
                         c.npc("Ok, tell me when you've got them all");
                         return;
                     }
                     whereAreTheKeys(c);
                 }
             });
            c.start();
            return;
        }

        c.npc("Hello, who are you");
        if (!questStarted()) {
            c.options(new Choice("I am a mighty adventurer. Who are you?",
                                 "I'm not sure. I was hoping you could tell me") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("I am")
                         .npc("A bold and famous knight of the realm");
                    } else {
                        c.npc("Well I've never met you before");
                    }
                }
            });
            c.start();
            return;
        }

        c.player("Gypsy Aris said I should come and talk to you")
         .npc("Gypsy Aris? Is she still alive?")
         .npc("I remember her from when I was pretty young")
         .npc("Well what do you need to talk to me about?")
         .player("I need to find Silverlight")
         .npc("What do you need to find that for?")
         .player("I need it to fight Delrith")
         .npc("Delrith?")
         .npc("I thought the world was rid of him")
         .player("Well, the gypsy's crystal ball seems to think otherwise")
         .npc("Well if the ball says so, I'd better help you")
         .npc("The problem is getting silverlight")
         .player("You mean you don't have it?")
         .npc("Oh I do have it")
         .npc("But it is so powerful")
         .npc("That I have put it in a special box")
         .npc("Which needs three different keys to open it")
         .npc("That way, it won't fall into the wrong hands")
         .player("So give me the keys")
         .npc("Um")
         .npc("Well, It's not so easy");
        whereAreTheKeys(c);
        c.then(new Effect() {
            public void run(Conversation c) {
                setStage(SEEK_KEYS);
            }
        });
        c.start();
    }

    private void whereAreTheKeys(Conversation c) {
        c.npc("I kept one of the keys")
         .npc("I gave the other two")
         .npc("To other people for safe keeping")
         .npc("One I gave to Rovin")
         .npc("who is captain of the palace guard")
         .npc("I gave the other to the wizard Traiborn")
         .player("Can you give me your key?")
         .npc("Um")
         .npc("Ah")
         .npc("Well there's a problem there as well")
         .npc("I managed to drop the key in the drain")
         .npc("Just outside the palace kitchen")
         .npc("It is just inside and I can't reach it")
         .player("So what does the drain connect to?")
         .npc("It is the drain")
         .npc("For the drainpipe running from the sink in the kitchen")
         .npc("Down to the palace sewers");
    }

    // -------------------------------------------------------------- rovin --

    private void talkToRovin(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("What are you doing up here?")
         .npc("Only the palace guards are allowed up here")
         .options(new Choice("Yes I know but this important",
                             "I am one of the palace guards",
                             "What about the king?") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("No you're not. I know all the palace guards")
                      .options(new Choice("I'm a new recruit",
                                          "I've had extensive plastic surgery") {
                          public void picked(int option, Conversation c) {
                              if (option == 0) {
                                  c.npc("I interview all the new recruits")
                                   .npc("I'd know if you were one of them")
                                   .player("That blows my story out of the window then")
                                   .npc("Get out of my sight");
                              } else {
                                  c.npc("What kind of surgery is that?")
                                   .npc("Never heard of it")
                                   .npc("Besides, you look reasonably healthy")
                                   .npc("Why is this relevant anyway?")
                                   .npc("You still shouldn't be here");
                              }
                          }
                      });
                     return;
                 }
                 if (option == 2) {
                     c.player("Surely you'd let him up here?")
                      .npc("Well, yes, I suppose we'd let him up")
                      .npc("He doesn't generally want to come up here")
                      .npc("But if he did want to")
                      .npc("He could come up")
                      .npc("Anyway, you're not the king either")
                      .npc("So get out of my sight");
                     return;
                 }
                 c.npc("Ok, I'm listening")
                  .npc("Tell me what's so important");
                 if (getStage() != SEEK_KEYS) {
                     // Off quest he simply has nothing worth hearing.
                     c.options(new Choice("Erm I forgot",
                                          "The castle has just received it's ale delivery") {
                         public void picked(int option, Conversation c) {
                             if (option == 0) {
                                 c.npc("Well it can't be that important then")
                                  .player("How do you know?")
                                  .npc("Just go away");
                             } else {
                                 c.npc("Now that is important")
                                  .npc("However, I'm the wrong person to speak to about it")
                                  .npc("Go talk to the kitchen staff");
                             }
                         }
                     });
                     return;
                 }
                 c.player("There's a demon who wants to invade this city")
                  .npc("Is it a powerful demon?")
                  .player("Yes, very")
                  .npc("Well as good as the palace guards are")
                  .npc("I don't think they're up to taking on a very powerful")
                  .player("No no, it's not them who's going to fight the demon")
                  .player("It's me")
                  .npc("What all by yourself?")
                  .player("Well I am going to use the powerful sword silverlight")
                  .player("Which I believe you have one of the keys for")
                  .npc("Yes you're right")
                  .npc("Here you go")
                  // He hands out as many as are asked for; the transcript is
                  // explicit that this can be repeated.
                  .give(new InvItem(KEY_ROVIN, 1))
                  .message("Captain Rovin gives you a key");
             }
         }.says(0, "Yes I know but this is important").says(1, "I am one of the palace guard"));
        c.start();
    }

    // ----------------------------------------------------------- traiborn --

    /*
     * Wizard Traiborn, npc 17, and the reason this section was rewritten:
     * Transcript:Traiborn the wizard is filed under the wizard, not under the
     * quest, so the per-quest dump does not have him. What was here before was
     * built from the per-quest dump and had the spine of the conversation
     * right, but it had lost both digressions entirely -- the thingummywut
     * argument, the "so aren't you a wizard" exchange, the spinach roll's
     * stage directions, the ship called Silverlight, the accusation that he
     * has lost the key -- and every one of his stage-direction messages. All
     * of it is recorded and all of it is back.
     */
    private void talkToTraiborn(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("Ello young thingummywut");

        final boolean onQuest = getStage() == SEEK_KEYS && !has(KEY_TRAIBORN);

        if (onQuest && this.bonesErrand()) {
            c.npc("How are you doing finding bones?")
             .player("I have some bones");
            handOverBones(c);
            c.start();
            return;
        }

        String[] options = onQuest
            ? new String[] { "I need to get a key given to you by Sir Prysin",
                             "Whats a thingummywut?",
                             "Teach me to be a mighty and powerful wizard" }
            : new String[] { "Whats a thingummywut?",
                             "Teach me to be a mighty and powerful wizard" };
        c.options(new Choice(options) {
            public void picked(int option, Conversation c) {
                String chosen = getOptions()[option];
                if (chosen.startsWith("Whats a")) {
                    thingummywut(c);
                } else if (chosen.startsWith("Teach me")) {
                    mightyWizard(c);
                } else {
                    aboutTheKey(c);
                }
            }
        });
        c.start();
    }

    private void thingummywut(Conversation c) {
        c.npc("A thingummywut?")
         .npc("Where? , Where?")
         .npc("Those pesky thingummywuts")
         .npc("They get everywhere")
         .npc("They leave a terrible mess too")
         .options(new Choice("Err you just called me thingummywut",
                             "Tell me what they look like and I'll mash 'em") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Don't be ridiculous")
                      .npc("No-one has ever seen one")
                      .npc("They're invisible")
                      .npc("Or a myth")
                      .npc("Or a figment of my imagination")
                      .npc("Can't remember which right now");
                     return;
                 }
                 c.npc("You're a thingummywut?")
                  .npc("I've never seen one up close before")
                  .npc("They said I was mad")
                  .npc("Now you are my proof")
                  .npc("There ARE thingummywuts in this tower")
                  .npc("Now where can I find a cage big enough to keep you?")
                  .options(new Choice("Err I'd better be off really",
                                      "They're right, you are mad") {
                      public void picked(int option, Conversation c) {
                          if (option == 0) {
                              betterBeOff(c);
                              return;
                          }
                          c.npc("That's a pity")
                           .npc("I thought maybe they were winding me up");
                      }
                  });
             }
         });
    }

    private void mightyWizard(Conversation c) {
        c.npc("Wizard, Eh?")
         .npc("You don't want any truck with that sort")
         .npc("They're not to be trusted")
         .npc("That's what I've heard anyways")
         .options(new Choice("So aren't you a wizard",
                             "Oh I'd better stop talking to you then") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("How dare you?")
                      .npc("Of course I'm a wizard")
                      .npc("Now don't be so cheeky or I'll turn you into a frog");
                     return;
                 }
                 c.npc("Cheerio then")
                  .npc("Was nice chatting to you");
             }
         });
    }

    /** His way of ending a conversation, reached from three different places. */
    private void betterBeOff(Conversation c) {
        c.npc("Oh ok have a good time")
         .npc("and watch out for sheep!")
         .npc("They're more cunning than they look");
    }

    private void aboutTheKey(Conversation c) {
        c.npc("Sir Prysin? Who's that?")
         .npc("What would I want his key for?")
         .options(new Choice("He's one of the king's knights",
                             "He told me you were looking after it for him",
                             "Well, have you got any keys knocking around?") {
             public void picked(int option, Conversation c) {
                 if (option == 2) {
                     theCloset(c);
                     return;
                 }
                 if (option == 1) {
                     c.npc("That wasn't very clever of him")
                      .npc("I'd lose my head if it wasn't screwed on proper")
                      .npc("Go tell him to find someone else")
                      .npc("to look after his valuables in future")
                      .options(new Choice("Ok, I'll go and tell him that",
                                          "Well, have you got any keys knocking around?") {
                          public void picked(int option, Conversation c) {
                              if (option == 1) {
                                  theCloset(c);
                                  return;
                              }
                              c.npc("Oh that's great")
                               .npc("If it wouldn't be too much trouble");
                              offOrKeys(c);
                          }
                      });
                     return;
                 }
                 c.npc("Say, I remember a knight with a key")
                  .npc("He had nice shoes")
                  .npc("and didn't like my homemade spinach rolls")
                  .npc("Would you like a spinach roll?")
                  .options(new Choice("Yes Please", "Just tell me if you have the key") {
                      public void picked(int option, Conversation c) {
                          if (option == 1) {
                              whichKey(c);
                              return;
                          }
                          c.message("Traiborn digs around in the pockets of his robes")
                           .message("Traiborn hands you a spinach roll")
                           .give(new InvItem(SPINACH_ROLL, 1))
                           .player("Thank you very much");
                          offOrKeys(c);
                      }
                  });
             }
         });
    }

    /** The pair of answers he leaves open after most of his digressions. */
    private void offOrKeys(Conversation c) {
        c.options(new Choice("Err I'd better be off really",
                             "Well, have you got any keys knocking around?") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    betterBeOff(c);
                    return;
                }
                theCloset(c);
            }
        });
    }

    private void whichKey(Conversation c) {
        c.npc("The key?")
         .npc("The key to what?")
         .npc("There's more than one key in the world, don't you know")
         .npc("Would be a bit odd if there was only one")
         .options(new Choice("Its the key to get a sword called Silverlight",
                             "You've lost it, haven't you?") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Me? Lose things?")
                      .npc("Thats a nasty accusation")
                      .player("Well, have you got any keys knocking around?");
                     theCloset(c);
                     return;
                 }
                 c.npc("Silverlight? Never heard of that")
                  .npc("Sounds a good name for a ship")
                  .npc("Are you sure it's not the name of a ship, rather than a sword?")
                  .options(new Choice("Yeah, pretty sure",
                                      "Well, have you got any keys knocking around?") {
                      public void picked(int option, Conversation c) {
                          if (option == 1) {
                              theCloset(c);
                              return;
                          }
                          c.npc("That's a pity")
                           .npc("Waste of a name");
                          offOrKeys(c);
                      }
                  });
             }
         });
    }

    private void theCloset(Conversation c) {
        c.npc("Now you come to mention it - yes I do have a key")
         .npc("Its in my special closet of valuable stuff")
         .npc("Now how do I get into that?")
         .message("The wizard scratches his head")
         .npc("I sealed it using one of my magic rituals")
         .npc("so it would make sense that another ritual")
         .npc("Would open it again")
         .message("The wizard beams")
         .player("So do you know what ritual to use?")
         .npc("Let me think a second")
         .npc("Yes a simple drazier style ritual should suffice")
         .npc("Hmm")
         .npc("Main problem with that is I'll need " + BONES_WANTED + " sets of bones")
         .npc("Now where am I going to get hold of something like that")
         .options(new Choice("I'll get the bones for you",
                             "Hmm, thats too bad. I really need that key") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Ah well sorry I couldn't be any more help");
                     return;
                 }
                 c.npc("Ooh that would very good of you")
                  .player("Ok I'll speak to you when I've got some bones");
                 DemonSlayer.this.setBonesErrand(true);
                 handOverBones(c);
             }
         });
    }

    /**
     * Give him everything currently carried, and see whether that finishes it.
     *
     * He takes them a set at a time in the real game, and the transcript is
     * explicit that "You give Traiborn a set of bones" prints once per set;
     * that is twenty-five lines of dialogue for one errand, so they go in
     * together and the line is said once. That repetition is the only thing
     * about this scene that is not reproduced exactly.
     */
    private void handOverBones(Conversation c) {
        c.npc("Give 'em here then")
         .then(new Effect() {
             public void run(Conversation c) {
                 Player p = c.getPlayer();
                 int carried = p.getInventory().countId(BONES);
                 int wanted = BONES_WANTED - DemonSlayer.this.bonesGiven();
                 int given = carried < wanted ? carried : wanted;
                 if (given > 0) {
                     p.getInventory().remove(BONES, given);
                     p.getActionSender().sendInventory();
                     DemonSlayer.this.setBonesGiven(DemonSlayer.this.bonesGiven() + given);
                     c.message("You give Traiborn a set of bones");
                 }
                 if (DemonSlayer.this.bonesGiven() < BONES_WANTED) {
                     c.player("That's all of them")
                      .npc("I still need more")
                      .player("Ok, I'll look for some more");
                     return;
                 }
                 c.npc("Hurrah! That's all " + BONES_WANTED + " sets of bones")
                  .message("Traiborn places the bones in a circle on the floor")
                  .message("Traiborn waves his arms about")
                  .npc("Wings of dark and colour too")
                  .npc("Spreading in the morning dew")
                  .message("The wizard waves his arms some more")
                  .npc("Locked away I have a key")
                  .npc("Return it now unto me")
                  .message("Traiborn smiles")
                  .message("Traiborn hands you a key")
                  .give(new InvItem(KEY_TRAIBORN, 1))
                  .player("Thank you very much")
                  .npc("Not a problem for a friend of sir what's-his-face");
                 DemonSlayer.this.setBonesGiven(0);
                 DemonSlayer.this.setBonesErrand(false);
             }
         });
    }

    // -------------------------------------------------------------- drain --

    /**
     * The drain's own "Search" command.
     *
     * The key is visible and out of arm's reach, which is the whole puzzle: it
     * has to be washed down into the sewer rather than picked up.
     */
    private void searchDrain() {
        Player p = getOwner();
        if (getStage() != SEEK_KEYS || has(KEY_DRAIN)) {
            p.getActionSender().sendMessage("You find nothing of interest");
            return;
        }
        p.getActionSender().sendMessage("You can see a key just inside the drain");
        p.getActionSender().sendMessage("But your arm is too big to reach it");
    }

    private void flushDrain(InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != BUCKET_OF_WATER) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        p.getInventory().remove(BUCKET_OF_WATER, 1);
        p.getInventory().add(new InvItem(BUCKET, 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You pour the water down the drain");
        if (getStage() != SEEK_KEYS || has(KEY_DRAIN)) {
            p.getActionSender().sendMessage("The water washes away out of sight");
            return;
        }
        p.getActionSender().sendMessage("You hear something fall into the sewer below");
        world.registerItem(new Item(KEY_DRAIN, SEWER_X, SEWER_Y, 1, p));
    }

    // ------------------------------------------------------------ delrith --

    private void talkToDelrith(Npc npc) {
        // He has nothing to say. Every line the transcript records for him is a
        // response to being attacked, not to being spoken to -- see
        // refusesAttack below, which is where they now live.
        new Conversation(getOwner(), npc).start();
    }

    /**
     * Delrith answers an attack rather than a greeting, and what he answers
     * with depends on three things: whether the quest is over, whether the
     * player has Silverlight at all, and whether it is in their hands.
     *
     * All four branches are `Transcript:Delrith` verbatim, and the section
     * headings there are what pins each one to a condition -- "Attempting to
     * attack before having obtained a Silverlight" keys on *having* the sword,
     * not on having started the quest, which is why the first test is the
     * stage and not questStarted().
     *
     * The post-quest branch weakens him first and refuses second. That order is
     * the transcript's: "Attacking after quest while wielding Silverlight"
     * lists the weaken line above "You've already done that quest", so the
     * sword bites even on a swing that never lands.
     */
    public boolean refusesAttack(Npc npc) {
        if (npc.getID() != DELRITH) {
            return false;
        }
        Player p = getOwner();
        if (completed()) {
            org.rscdaemon.server.util.Formulae.applySilverlight(npc, p);
            p.getActionSender().sendMessage("You've already done that quest");
            return true;
        }
        if (getStage() < HAS_SILVERLIGHT) {
            new Conversation(p, npc).player("I'd rather not. He looks scary").start();
            return true;
        }
        if (!p.getInventory().wielding(SILVERLIGHT)) {
            new Conversation(p, npc).player("Maybe I'd better wield silverlight first").start();
            return true;
        }
        return false;
    }

    /**
     * The incantation, at the moment the transcript puts it: the killing blow.
     *
     * A previous revision asked for the words when the player *talked* to
     * Delrith, and said in this file's header that it had to, because "the
     * server has no trigger for the start of a fight -- only for a death, in
     * Npc.killedBy". The premise was right and the conclusion was wrong. A
     * death is exactly when the real game asks: the quest page says the player
     * "will eventually have to recite an incantation" as the fight goes on, and
     * `refusesKill` exists precisely to hold an npc at the door of death while
     * a quest decides. Chronozon is its other user.
     *
     * Getting it wrong is not a failure state. The vortex closes, Delrith is
     * still standing, and the fight carries on -- which is what the wiki means
     * by "it is possible to fight Delrith for extended periods of time".
     *
     * He sits on one hit point for as long as the menu is open, because that is
     * the reprieve `Npc.killedBy` grants when a quest refuses; the wrong words
     * put him back to full. A player who closes the menu instead of answering
     * leaves him there, and the next blow simply re-opens the vortex.
     */
    public boolean refusesKill(Npc npc) {
        if (npc.getID() != DELRITH || this.banished) {
            return false;
        }
        final Player p = getOwner();
        if (completed() || getStage() != HAS_SILVERLIGHT
                || !p.getInventory().wielding(SILVERLIGHT)) {
            // Killed by something other than the sword -- he dies, unbanished,
            // and killedDelrith says so.
            return false;
        }
        if (this.vortexOpen) {
            if (p.isBusy() || p.getMenuHandler() != null) {
                // The incantation dialogue really is still on screen.
                return true;
            }
            // The dialogue died without an answer -- something reset the
            // player's menu handler out from under it (a cast slipping in
            // during the option wait was the live case), so the Conversation
            // never ran chant() and never cleared this flag. Fall through and
            // ask again: this blow re-opens the vortex.
            this.vortexOpen = false;
        }
        this.vortexOpen = true;
        final Npc delrith = npc;
        new Conversation(p, npc)
            .message("As you strike Delrith a vortex opens up")
            .player("Now what was that incantation again")
            .options(new Choice(INCANTATIONS) {
                public void picked(int option, Conversation c) {
                    DemonSlayer.this.chant(option, delrith, c);
                }
            })
            .start();
        return true;
    }

    /**
     * Both endings. The wrong-words trio is the transcript's, in its order.
     *
     * RSCSundae opens that trio with a fourth line, "As you chant, Delrith is
     * sucked towards the vortex", which is not in the transcript. It is left
     * out rather than adopted: the transcript captures all four options and
     * shows the same three lines under each, so its absence is four witnesses
     * rather than a gap. Worth revisiting if a replay ever shows otherwise.
     */
    private void chant(int option, Npc npc, Conversation c) {
        Player p = getOwner();
        this.vortexOpen = false;
        if (option != RIGHT_WORDS) {
            npc.setHits(npc.getDef().getHits());
            for (Player viewer : npc.getViewArea().getPlayersInView()) {
                viewer.informOfModifiedHits(npc);
            }
            c.message("Suddenly the vortex closes")
             .message("And Delrith is still here")
             .message("That was the wrong incantation");
            return;
        }
        c.message("Delrith is sucked back into the dark dimension from which he came")
         .then(new Effect() {
             public void run(Conversation c) {
                 DemonSlayer.this.banish(c.getPlayer(), npc);
             }
         });
    }

    private void banish(Player p, Npc npc) {
        this.banished = true;
        // Straight back through the ordinary death path, so the drop table, the
        // NPC_KILLED trigger and the respawn all behave exactly as they would
        // for any other kill. refusesKill waves this one through on the flag.
        npc.killedBy(p, false);
    }

    private void killedDelrith() {
        Player p = getOwner();
        if (completed() || getStage() != HAS_SILVERLIGHT) {
            return;
        }
        if (!this.banished) {
            p.getActionSender().sendMessage("The demon fades away, but he is not banished");
            p.getActionSender().sendMessage("He will re-form in the stone circle before long");
            return;
        }
        this.banished = false;
        p.getActionSender().sendMessage("You have completed the demonslayer quest");
        setStage(getFinalStage());
    }
}
