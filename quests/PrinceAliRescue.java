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
 * Prince Ali rescue, 28 February 2001.
 *
 * The Emir's son is being held for ransom by Lady Keli, east of Draynor. The
 * chancellor Hassan hires the player, the spymaster Osman briefs them, and his
 * daughter Leela -- watching the jail from the wheat field -- runs the escape.
 * The prince walks out dressed as Keli: a blonde wig, a pink skirt and a bottle
 * of skin paste, with Keli herself tied up and her door guard drunk.
 *
 * Three quest points and 700 coins, or 620 if the 80-coin advance was taken
 * after the key was made. No experience. In the real game it also bought free
 * passage through the Al Kharid toll gate, which this server does not charge
 * for yet -- there is no toll to be exempt from, so that part of the reward is
 * waiting on the gate rather than on the quest.
 *
 * Two npcs the quest needs are handlers rather than part of it, because each
 * belongs to a second quest as well and associating an npc takes it away from
 * every other quest entirely: Aggie makes the skin paste (and Goblin diplomacy's
 * dyes), and Ned makes the wig (and sails to Crandor in Dragon slayer). Both ask
 * this quest's stage through QuestManager.
 *
 * The 80-coin advance is optional and has to be remembered until the payout, but
 * a quest persists exactly one integer. So the two possibilities are two stages
 * rather than a stage and a flag: taking the advance moves the stage on by one
 * and every later step keeps that offset, which is why the numbering below runs
 * in pairs.
 *
 * Two pieces of state are deliberately not persisted, for the same reason
 * Pirate's treasure does not persist a half-filled crate: whether Joe is drunk
 * and whether Keli is tied. Both are things that happen in the course of one
 * visit and both wear off -- the transcripts have Keli say she "will not stay
 * tied up for long". Logging out sobers Joe up and unties Keli, and the beers
 * and the rope have to be bought again.
 *
 * Dialogue is Jagex's, from the recorded transcripts. The handful of lines with
 * no recorded original -- tying Keli up, and the cell door -- are marked where
 * they appear.
 */
public class PrinceAliRescue extends Quest {

    public final static int UID = Quests.PRINCE_ALI_RESCUE;

    /** Hassan has taken the offer of help and sent the player to Osman. */
    private static final int STARTED = 1;
    /** Osman has briefed them. Leela will talk. */
    private static final int HIRED = 2;
    /** Osman has the keyprint and the bar; the key is waiting with Leela. */
    private static final int KEY_ORDERED = 3;
    /** The same, with the 80-coin advance taken. */
    private static final int KEY_ORDERED_PAID = 4;
    /** The key has been collected from Leela. */
    private static final int HAS_KEY = 5;
    private static final int HAS_KEY_PAID = 6;
    /** The prince is out and on his way home with Leela. */
    private static final int RESCUED = 7;
    private static final int RESCUED_PAID = 8;
    private static final int FINISHED = 9;

    private static final int PRINCE_ALI = 118;
    private static final int HASSAN = 119;
    private static final int OSMAN = 120;
    private static final int JOE = 121;
    private static final int LEELA = 122;
    private static final int LADY_KELI = 123;
    private static final int JAILGUARD = 127;

    /**
     * The cell door, DoorDef 45, at (199,640) facing east-west. There is exactly
     * one of it in the world, so unlike most claimed ids this one needs no
     * coordinate guard -- but the check is here anyway, because a claim is on
     * the id and the map can gain another one.
     */
    private static final int CELL_DOOR = 45;
    private static final int CELL_DOOR_X = 199;
    private static final int CELL_DOOR_Y = 640;
    /** The walk-through doorframe every other door in the server opens into. */
    private static final int OPEN_DOOR = 11;

    private static final int COINS = 10;
    private static final int BEER = 193;
    /**
     * "skirt -- A ladies skirt", the pink one. Three items share that name and
     * that description and differ only by colour: 187 is blue, 195 is grey and
     * 194 is Keli's pink. It is sold in the Varrock clothes shop and one
     * respawns on the ground at (110,518).
     */
    private static final int PINK_SKIRT = 194;
    private static final int BALL_OF_WOOL = 207;
    private static final int YELLOWDYE = 239;
    private static final int PASTE = 240;
    /**
     * "Bronze key -- A heavy key". RSCD renamed this one to "Wave 6 Key" for a
     * minigame, the same way it renamed Pirate's treasure's chest key. Nothing
     * in the code uses the id, only the name was taken, so the quest keeps it.
     */
    private static final int BRONZE_KEY = 242;
    private static final int SOFT_CLAY = 243;
    private static final int WIG_BLONDE = 244;
    private static final int WIG_WOOL = 245;
    private static final int KEYPRINT = 247;
    private static final int BRONZE_BAR = 169;
    private static final int ROPE = 237;

    private static final int BEERS_WANTED = 3;
    private static final int ADVANCE = 80;
    private static final int REWARD = 700;

    /**
     * Joe has had his three beers. Not persisted -- see the class comment.
     * The guard is the player's problem alone: he is drunk for whoever bought
     * the round and sober for everyone else, which is the only way a shared
     * world can run a single-player rescue.
     */
    private boolean joeDrunk = false;

    /** Keli is tied up. Not persisted either, and per-player for the same reason. */
    private boolean keliTied = false;

    public PrinceAliRescue(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Prince Ali rescue");
        setFinalStage(FINISHED);
        associateNpc(HASSAN);
        associateNpc(OSMAN);
        associateNpc(LEELA);
        associateNpc(JOE);
        associateNpc(LADY_KELI);
        associateNpc(PRINCE_ALI);
        associateNpc(JAILGUARD);
        associateDoor(CELL_DOOR);
        // Only for dyeing the wig blonde, which is an item-on-item and so needs
        // both halves claimed. Yellow dye on anything else -- goblin armour, in
        // Goblin diplomacy -- is untouched, because the other half is not ours.
        associateItem(YELLOWDYE);
        associateItem(WIG_WOOL);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Prince Ali of Al Kharid has been kidnapped by the scheming Lady Keli. You are hired to stage a rescue mission.");
        setStartPoint("Al Kharid palace");
        setSpeakTo("Hassan");
        setMissionLength("Long");
        rewardOther("700 coins paid by Hassan, less the 80 coin advance if it was drawn");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Prince Ali rescue quest");
    }

    // ----------------------------------------------------------- dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger == QuestTrigger.NPC_TALK && entity instanceof Npc) {
            Npc npc = (Npc) entity;
            switch (npc.getID()) {
                case HASSAN:     talkToHassan(npc);   break;
                case OSMAN:      talkToOsman(npc);    break;
                case LEELA:      talkToLeela(npc);    break;
                case JOE:        talkToJoe(npc);      break;
                case LADY_KELI:  talkToKeli(npc);     break;
                case PRINCE_ALI: talkToPrince(npc);   break;
                case JAILGUARD:  talkToJailguard(npc); break;
            }
            return;
        }
        if ((trigger == QuestTrigger.DOOR_ACT1 || trigger == QuestTrigger.DOOR_ACT2)
                && entity instanceof GameObject) {
            openCellDoor((GameObject) entity, trigger);
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_NPC && entity instanceof Npc
                && ((Npc) entity).getID() == LADY_KELI) {
            tieUpKeli(used);
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_DOOR && entity instanceof GameObject) {
            unlockCellDoor((GameObject) entity, used);
            return;
        }
        if (trigger == QuestTrigger.ITEM_ON_ITEM && entity instanceof InvItem) {
            dyeWig((InvItem) entity, used);
            return;
        }
        triggerEntity(trigger, entity);
    }

    // -------------------------------------------------------- stage tests --

    /** Whether the 80-coin advance has already been drawn. */
    private boolean advancePaid() {
        int s = getStage();
        return s == KEY_ORDERED_PAID || s == HAS_KEY_PAID || s == RESCUED_PAID;
    }

    /** Osman has made the key, whether or not it has been collected yet. */
    private boolean keyOrdered() {
        int s = getStage();
        return s >= KEY_ORDERED && s <= HAS_KEY_PAID;
    }

    private boolean keyCollected() {
        int s = getStage();
        return s == HAS_KEY || s == HAS_KEY_PAID;
    }

    private boolean rescued() {
        int s = getStage();
        return s == RESCUED || s == RESCUED_PAID;
    }

    private int count(int id) {
        return getOwner().getInventory().countId(id);
    }

    /** The three pieces of Keli that the prince has to be dressed in. */
    private boolean hasDisguise() {
        return count(WIG_BLONDE) > 0 && count(PINK_SKIRT) > 0 && count(PASTE) > 0;
    }

    // ------------------------------------------------------------ hassan --

    private void talkToHassan(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("You are a friend of the town of Al Kharid")
             .npc("Please, keep in contact. Good employees are not easy to find")
             .start();
            return;
        }

        if (rescued()) {
            final int payout = advancePaid() ? REWARD - ADVANCE : REWARD;
            c.npc("You have the eternal gratitude of the Emir for rescuing his son")
             .npc("I am authorised to pay you " + REWARD + " coins");
            if (advancePaid()) {
                c.npc(ADVANCE + " was put aside for the key. that leaves " + payout);
            }
            c.then(new Effect() {
                public void run(Conversation c) {
                    Player p = c.getPlayer();
                    p.getInventory().add(new InvItem(COINS, payout));
                    p.getActionSender().sendInventory();
                    setStage(FINISHED);
                }
            });
            c.start();
            return;
        }

        if (keyOrdered() && !advancePaid()) {
            c.npc("You have proved your services useful to us")
             .npc("Here is " + ADVANCE + " coins for the work you have already done")
             .then(new Effect() {
                 public void run(Conversation c) {
                     Player p = c.getPlayer();
                     p.getInventory().add(new InvItem(COINS, ADVANCE));
                     p.getActionSender().sendInventory();
                     // Every later stage carries the offset -- see the class
                     // comment. 3 -> 4 and 5 -> 6.
                     setStage(getStage() + 1);
                 }
             });
            c.start();
            return;
        }

        if (advancePaid()) {
            c.npc("Hello again adventurer")
             .npc("You have received payment for your tasks so far")
             .npc("No more will be paid until the Prince is rescued")
             .start();
            return;
        }

        if (getStage() >= HIRED) {
            c.npc("I understand the Spymaster has hired you")
             .npc("I will pay the reward only when the Prince is rescued")
             .npc("I can pay some expenses once the spymaster approves it")
             .start();
            return;
        }

        if (getStage() == STARTED) {
            c.npc("Have you found the spymaster, Osman, Yet?")
             .npc("You cannot proceed in your task without reporting to him")
             .start();
            return;
        }

        c.npc("Greetings. I am Hassan, Chancellor to the Emir of Al Kharid")
         .options(new Choice("Can I help you? You must need some help here in the desert.",
                             "Its just too hot here. How can you stand it?",
                             "Do you mind if I just kill your warriors?") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("We manage, in our humble way. We are a wealthy town")
                      .npc("And we have water. It cures many thirsts");
                     return;
                 }
                 if (option == 2) {
                     c.npc("You are welcome. They are not expensive.")
                      .npc("We have them here to stop the elite guard being bothered")
                      .npc("They are a little harder to kill.");
                     return;
                 }
                 c.npc("I need the services of someone, yes.")
                  .npc("If you are interested, see the spymaster, Osman")
                  .npc("I manage the finances here. come to me when you need payment")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(STARTED);
                      }
                  });
             }
         });
        c.start();
    }

    // ------------------------------------------------------------- osman --

    private void talkToOsman(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed()) {
            c.npc("Well done. A great rescue")
             .npc("I will remember you if I have anything dangerous to do")
             .start();
            return;
        }

        if (rescued()) {
            c.npc("The prince is safe, and on his way home with Leela")
             .npc("You can pick up your payment from the chancellor")
             .start();
            return;
        }

        if (getStage() == STARTED) {
            c.player("The chancellor trusts me. I have come for instructions")
             .npc("Our Prince is captive by the Lady Keli")
             .npc("We just need to make the rescue")
             .npc("There are three things we need you to do")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(HIRED);
                 }
             });
            briefing(c);
            c.start();
            return;
        }

        if (getStage() < STARTED) {
            c.npc("Hello, I am Osman")
             .npc("What can I assist you with")
             .options(new Choice("I hear wild rumours about a Prince",
                                 "You don't seem very tough. Who are you?",
                                 "I am just being nosy") {
                 public void picked(int option, Conversation c) {
                     if (option == 0) {
                         c.npc("The prince is not here. He is... away")
                          .npc("If you can be trusted, speak to the chancellor, Hassan");
                     } else if (option == 1) {
                         c.npc("I am in the employ of the Emir")
                          .npc("That is all you need to know");
                     } else {
                         c.npc("That bothers me not")
                          .npc("The secrets of Al Kharid protect themselves");
                     }
                 }
             });
            c.start();
            return;
        }

        // Hired, key not yet ordered. Osman takes the print and the bar the
        // moment the player is carrying both.
        if (getStage() == HIRED && count(KEYPRINT) > 0 && count(BRONZE_BAR) > 0) {
            c.npc("Well done, we can make the key now.")
             .then(new Effect() {
                 public void run(Conversation c) {
                     Player p = c.getPlayer();
                     p.getInventory().remove(KEYPRINT, 1);
                     p.getInventory().remove(BRONZE_BAR, 1);
                     p.getActionSender().sendInventory();
                     setStage(KEY_ORDERED);
                 }
             })
             .npc("Pick the key up from Leela.")
             .npc("I will let you get " + ADVANCE + " coins from the chancellor for getting this key")
             .player("Thankyou, I will try to find the other items")
             .start();
            return;
        }

        if (getStage() == HIRED && count(KEYPRINT) > 0) {
            c.npc("Good, you have the print of the key")
             .npc("Get a bar of Bronze too, and I can get the key made")
             .player("I will get one, and come back")
             .start();
            return;
        }

        checklist(c);
        c.start();
    }

    /** The three tasks, as a menu the player can work through in any order. */
    private void briefing(Conversation c) {
        c.options(new Choice("What is the first thing I must do?",
                             "What is needed second?",
                             "And the final things you need?",
                             "Okay, I better go find some things") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:
                        c.npc("The prince is guarded by some stupid guards, and a clever woman")
                         .npc("The woman is our only way to get the prince out")
                         .npc("Only she can walk freely about the area")
                         .npc("I think you will need to tie her up")
                         .npc("one coil of rope should do for that")
                         .npc("And then disguise the prince as her to get him out without suspicion")
                         .player("How good must the disguise be?")
                         .npc("Only good enough to fool the guards at a distance")
                         .npc("Get a skirt like hers. Same colour, same style")
                         .npc("We will only have a short time")
                         .npc("A blonde wig too. That is up to you to make or find")
                         .npc("Something to colour the skin of the prince")
                         .npc("My daughter and top spy, leela, can help you there");
                        break;
                    case 1:
                        c.npc("We need the key, or a copy made")
                         .npc("If you can get some soft clay, then you can copy the key")
                         .npc("If you can convince Lady Keli to show it to you for a moment")
                         .npc("She is very boastful. It should not be too hard")
                         .npc("Bring the imprint to me, with a bar of bronze.");
                        break;
                    case 2:
                        c.npc("You will need to stop the guard at the door")
                         .npc("Find out if he has any weaknesses, and use them");
                        break;
                    default:
                        c.npc("May good luck travel with you")
                         .npc("Don't forget to find Leela. It can't be done without her help");
                        return;
                }
                briefing(c);
            }
        });
    }

    /**
     * Osman reading back what is still missing. The transcript runs the whole
     * list every time, saying either the "you need" line or the "you have got"
     * line for each item, so that is what this does.
     */
    private void checklist(Conversation c) {
        c.player("Can you tell me what I still need to get?")
         .npc("Let me check. You need:");

        if (keyCollected() || count(BRONZE_KEY) > 0) {
            c.npc("You have the key, good");
        } else if (keyOrdered()) {
            c.npc("Then collect the key from Leela");
        } else {
            c.npc("A print of the key in soft clay, and a bronze bar");
        }

        c.npc(count(WIG_BLONDE) > 0
            ? "The wig you have got, well done"
            : "You need to make a Blonde Wig somehow. Leela may help");
        c.npc(count(PINK_SKIRT) > 0
            ? "You have the skirt, good"
            : "A skirt the same as Keli's,");
        if (count(PASTE) > 0) {
            c.npc("You have the skin paint, well done")
             .npc("I thought you would struggle to make that");
        } else {
            c.npc("Something to colour the Princes skin lighter");
        }
        c.npc(count(ROPE) > 0
            ? "Yes, you have the rope."
            : "Rope to tie Keli up with");
        if (!this.joeDrunk) {
            c.npc("You still need some way to stop the guard from interfering");
        }
        c.npc("Once you have everything, Go to Leela")
         .npc("she must be ready to get the prince away");
    }

    // ------------------------------------------------------------- leela --

    private void talkToLeela(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (completed() || rescued()) {
            c.npc("Thank you, Al Kharid will forever owe you for your help")
             .npc("I think that if there is ever anything that needs to be done,")
             .npc("you will be someone they can rely on")
             .start();
            return;
        }

        if (getStage() < HIRED) {
            c.player("What are you waiting here for")
             .npc("That is no concern of yours, adventurer")
             .start();
            return;
        }

        // Handing over the key her father had cut.
        if (keyOrdered() && !keyCollected()) {
            c.npc("My father sent this key for you, be careful not to lose it")
             .give(new InvItem(BRONZE_KEY, 1))
             .message("Leela gives you a key")
             .then(new Effect() {
                 public void run(Conversation c) {
                     // 3 -> 5, 4 -> 6: the advance offset is carried through.
                     setStage(getStage() + 2);
                 }
             })
             .start();
            return;
        }

        /* Ours, not Jagex's. The cell has one key and one lock, so a player who
           loses it after collecting it would otherwise be stuck for good. Frank
           replaces the chest key in Pirate's treasure without complaint and this
           follows him. */
        if (keyCollected() && count(BRONZE_KEY) < 1) {
            c.player("I seem to have lost the key")
             .npc("Then it is as well my father made more than one")
             .give(new InvItem(BRONZE_KEY, 1))
             .message("Leela gives you a key")
             .start();
            return;
        }

        if (hasDisguise() && count(ROPE) > 0) {
            c.npc("Good, you have all the basic equipment")
             .npc("What are your plans to stop the guard interfering?")
             .options(new Choice("I hoped to get him drunk",
                                 "I haven't spoken to him yet",
                                 "I was going to attack him",
                                 "Maybe I could bribe him to leave") {
                 public void picked(int option, Conversation c) {
                     switch (option) {
                         case 0:
                             c.npc("Well, thats possible. These guards do like a drink")
                              .npc("I would think that it will take at least " + BEERS_WANTED
                                  + " beers to do it well")
                              .npc("You would probably have to do it all at the same time too")
                              .npc("The effects of the local beer wear of quickly");
                             break;
                         case 1:
                             c.npc("Well, speaking to him may find a weakness he has")
                              .npc("See if theres something that could stop him bothering us");
                             break;
                         case 2:
                             c.npc("I don't think you should")
                              .npc("If you do the rest of the gang and Keli would attack you")
                              .npc("The door guard should be removed first, to make it easy");
                             break;
                         default:
                             c.npc("You could try. I don't think the emir will pay anything towards it")
                              .npc("And we did bribe one of their guards once")
                              .npc("Keli killed him in front of the other guards, as a deterrent")
                              .npc("It would probably take a lot of gold");
                             break;
                     }
                     c.npc("Good luck with the guard. When the guard is out you can tie up Keli");
                 }
             });
            c.start();
            return;
        }

        c.player("I am here to help you free the prince")
         .npc("Your employment is known to me.")
         .npc("Now, do you know all that we need to make the break?");
        leelaAdvice(c);
        c.start();
    }

    private void leelaAdvice(Conversation c) {
        c.options(new Choice("I must make a disguise. What do you suggest?",
                             "I need to get the key made",
                             "What can i do with the guards?",
                             "I will go and get the rest of the escape equipment") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:
                        c.npc("Only the lady Keli, can wander about outside the jail")
                         .npc("The guards will shoot to kill if they see the prince out")
                         .npc("so we need a disguise well enough to fool them at a distance");
                        if (count(WIG_BLONDE) > 0) {
                            c.npc("The wig you have got, well done");
                        } else {
                            c.npc("You need a wig, maybe made from wool")
                             .npc("If you find someone who can work with wool, ask them about it")
                             .npc("Then the old witch may be able to help you dye it");
                        }
                        if (count(PINK_SKIRT) > 0) {
                            c.npc("You have got the skirt, good");
                        } else {
                            c.npc("You will need to get a pink skirt, same as Keli's");
                        }
                        if (count(PASTE) > 0) {
                            c.npc("You have the skin paint, well done")
                             .npc("I thought you would struggle to make that");
                        } else {
                            c.npc("we still need something to colour the Princes skin lighter")
                             .npc("There's an old witch close to here, she knows about many things")
                             .npc("She may know some way to make the skin lighter");
                        }
                        if (count(ROPE) > 0) {
                            c.npc("You have rope I see, tie up Keli")
                             .npc("that will be the most dangerous part of the plan");
                        } else {
                            c.npc("You will still need some rope to tie up Keli, of course")
                             .npc("I heard that there was a good ropemaker around here");
                        }
                        break;
                    case 1:
                        c.npc("Yes, that is most important")
                         .npc("There is no way you can get the real key.")
                         .npc("It is on a chain around Keli's neck. almost impossible to steal")
                         .npc("get some soft clay, and get her to show you the key somehow")
                         .npc("then take the print, with bronze, to my father");
                        break;
                    case 2:
                        c.npc("Most of the guards will be easy")
                         .npc("The disguise will get past them")
                         .npc("The only guard who will be a problem will be the one at the door")
                         .npc("He is talkative, try to find a weakness in him")
                         .npc("We can discuss this more when you have the rest of the escape kit");
                        break;
                    default:
                        c.npc("I shall await your return with everything");
                        return;
                }
                leelaAdvice(c);
            }
        });
    }

    // --------------------------------------------------------------- joe --

    private void talkToJoe(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (rescued() || completed()) {
            c.npc("Halt, who goes there? Friend or foe?")
             .player("Hi friend, I am just checking out things here")
             .npc("The Prince got away, I am in trouble")
             .npc("I better not talk to you, they are not sure I was drunk")
             .player("I won't say anything, your secret is safe with me")
             .start();
            return;
        }

        if (this.joeDrunk) {
            c.npc("Halt. Who goes there?")
             .player("Hello friend, I am just rescuing the prince, is that ok?")
             .npc("Thatsh a funny joke. you are lucky I am shober")
             .npc("Go in peace, friend")
             .start();
            return;
        }

        c.npc("Hi, I'm Joe, door guard for Lady Keli")
         .player("Hi, who are you guarding here?")
         .npc("Can't say, all very secret. you should get out of here")
         .npc("I am not supposed to talk while I guard");

        // The beer line appears once the player is hired, however many beers
        // they hold -- what Joe does with the offer depends on the count, see
        // beers(). Gating this option on holding all three (as a previous
        // revision did, with a player-choice fork inside) stranded anyone who
        // gave him one beer and dropped to two: the option vanished and he
        // could never be got drunk.
        if (getStage() >= HIRED) {
            c.options(new Choice("I have some beer here, fancy one?",
                                 "Tell me about the life of a guard",
                                 "What did you want to be when you were a boy",
                                 "I had better leave, I don't want trouble") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        beers(c);
                    } else {
                        // 1 -> life of a guard, 2 -> as a boy, 3 -> leaving.
                        joeSmallTalk(option == 3 ? LEAVING : option + 1, c);
                    }
                }
            });
            c.start();
            return;
        }

        c.options(new Choice("Chill out, I wont cause you trouble",
                             "Tell me about the life of a guard",
                             "What did you want to be when you were a boy",
                             "I had better leave, I don't want trouble") {
            public void picked(int option, Conversation c) {
                joeSmallTalk(option + 1, c);
            }
        }.says(0, "Hey, chill out, I wont cause you trouble"));
        c.start();
    }

    /** Joe's four small-talk answers, shared between both versions of his menu. */
    private static final int CHILL_OUT = 1;
    private static final int GUARD_LIFE = 2;
    private static final int AS_A_BOY = 3;
    private static final int LEAVING = 4;

    private void joeSmallTalk(int option, Conversation c) {
        switch (option) {
            case CHILL_OUT:
                c.player("I was just wondering what you do to relax")
                 .npc("You never relax with these people, but its a good career for a young man")
                 .npc("And some of the shouting I rather like")
                 .npc("RESISTANCE IS USELESS!")
                 .options(new Choice("So what do you buy with these great wages?",
                                     "Would you be interested in making a little more money?") {
                     public void picked(int option, Conversation c) {
                         if (option == 0) {
                             c.npc("Really, after working here, theres only time for a drink or three")
                              .npc("All of us guards go to the same bar, And drink ourselves stupid")
                              .npc("Its what I enjoy these days, that fade into unconsciousness")
                              .npc("I can't resist the sight of a really cold beer");
                             return;
                         }
                         c.npc("WHAT! are you trying to bribe me?")
                          .npc("I may not be a great guard, but I am loyal")
                          .npc("How DARE you try to bribe me!")
                          .player("No,no, you got the wrong idea, totally")
                          .player("I just wondered if you wanted some part-time bodyguard work")
                          .npc("Oh. sorry. no, I don't need money")
                          .npc("As long as you were not offering me a bribe");
                     }
                 });
                break;
            case GUARD_LIFE:
                c.npc("Well, the hours are good.....")
                 .npc(".... But most of those hours are a drag")
                 .npc("If only I had spent more time in Knight school when I was a young boy")
                 .npc("Maybe I wouldn't be here now, scared of Keli");
                break;
            case AS_A_BOY:
                c.npc("Well, I loved to sit by the lake, with my toes in the water")
                 .npc("And shoot the fish with my bow and arrow")
                 .player("That was a strange hobby for a little boy")
                 .npc("It kept us from goblin hunting, which was what most boys did");
                break;
            default:
                c.npc("Thanks I appreciate that")
                 .npc("Talking on duty can be punishable by having your mouth stitched up")
                 .npc("These are tough people, no mistake");
                break;
        }
    }

    /**
     * Count-driven, not a player choice -- OpenRSC's authentic flow. With no
     * beer Joe just laments; with one or two he drinks one and stays sober,
     * and the player can come back; with three he drinks one and pockets the
     * other two "for later", which is what gets him drunk. The wiki's "takes
     * 3 beers" means three in hand at once, but handing them over one at a
     * time only ever costs beers -- one wears off before the next arrives.
     */
    private void beers(Conversation c) {
        int held = count(BEER);
        c.npc("Ah, that would be lovely, just one now just to wet my throat");
        if (held == 0) {
            c.player("Of course, it must be tough being here without a drink")
             .player("Oh dear seems like I don't have any beer");
            return;
        }
        c.player("Of course, it must be tough being here without a drink")
         .message("You hand a beer to the guard, he drinks it in seconds")
         .then(new Effect() {
             public void run(Conversation c) {
                 Player p = c.getPlayer();
                 p.getInventory().remove(BEER, 1);
                 p.getActionSender().sendInventory();
             }
         })
         .npc("Thas was perfect, i cant thank you enough");
        if (held < BEERS_WANTED) {
            c.player("How are you? still ok. Not too drunk?")
             .npc("No, I don't get drunk with only one drink")
             .npc("You would need a few to do that, but thanks for the beer");
            return;
        }
        c.player("Would you care for another, my friend?")
         .npc("I better not, I don't want to be drunk on duty")
         .player("Here, just keep these for later, I hate to see a thirsty guard")
         .then(new Effect() {
             public void run(Conversation c) {
                 Player p = c.getPlayer();
                 p.getInventory().remove(BEER, BEERS_WANTED - 1);
                 p.getActionSender().sendInventory();
                 PrinceAliRescue.this.joeDrunk = true;
             }
         })
         .npc("Franksh, that wash just what I need to shtay on guard")
         .npc("No more beersh, i don't want to get drunk");
    }

    private void talkToJailguard(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("Hi, who are you guarding here?")
            .npc("Can't say, all very secret. you should get out of here")
            .npc("I am not supposed to talk while I guard")
            .options(new Choice("Chill out, I wont cause you trouble",
                                "I had better leave, I don't want trouble") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("I was just wondering what you do to relax")
                         .npc("You never relax with these people, but its a good career for a young man");
                        return;
                    }
                    c.npc("Thanks I appreciate that")
                     .npc("Talking on duty can be punishable by having your mouth stitched up")
                     .npc("These are tough people, no mistake");
                }
            }.says(0, "Hey, chill out, I wont cause you trouble"))
            .start();
    }

    // -------------------------------------------------------------- keli --

    private void talkToKeli(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (rescued() || completed()) {
            c.npc("You tricked me, and tied me up")
             .npc("You should not stay here if you want to remain alive")
             .npc("Guards! Guards! Kill this stranger")
             .start();
            return;
        }

        c.player("Are you the famous Lady Keli?")
         .player("Leader of the toughest gang of mercenary killers around?")
         .npc("I am Keli, you have heard of me then")
         // picker rather than options throughout Keli: three of these four
         // labels are not what the player then says out loud -- "Heard of you?
         // you are famous in Runescape!" is answered with "The great Lady Keli,
         // of course I have heard of you" -- so the echo cannot produce the
         // recorded lines and each branch speaks for itself.
         .picker(new Choice("Heard of you? you are famous in Runescape!",
                            "I have heard a little, but I think Katrine is tougher",
                            "I have heard rumours that you kill people",
                            "No I have never really heard of you") {
             public void picked(int option, Conversation c) {
                 switch (option) {
                     case 0:
                         keliHeard(c);
                         return;
                     case 1:
                         keliKatrine(c);
                         return;
                     case 2:
                         c.player("I have heard rumours that you kill people")
                          .npc("Theres always someone ready to spread rumours")
                          .npc("I heard a rumour the other day, that some men are wearing skirts")
                          .npc("If one of my men wore a skirt, he would wish he hadn't");
                         keliPrompt(c);
                         return;
                     default:
                         keliNeverHeard(c);
                         return;
                 }
             }
         });
        c.start();
    }

    /** "Heard of you? you are famous in Runescape!" */
    private void keliHeard(Conversation c) {
        c.player("The great Lady Keli, of course I have heard of you")
         .player("You are famous in Runescape!")
         .npc("Thats very kind of you to say. Reputation are not easily earnt")
         .npc("I have managed to succeed where many fail");
        keliPrompt(c);
    }

    /**
     * The menu Keli returns to whenever a branch has not ended the
     * conversation. Reached from three places, which is why it is its own
     * method: after she is flattered, after the rumour about skirts, and
     * from "No I have never really heard of you".
     */
    private void keliPrompt(Conversation c) {
        c.picker(new Choice("I think Katrine is still tougher",
                            "What is your latest plan then?",
                            "You must have trained a lot for this work",
                            "I should not disturb someone as tough as you") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:  keliKatrine(c);  return;
                    case 1:  keliPlan(c);     return;
                    case 2:  keliTrained(c);  return;
                    default: keliDisturb(c);  return;
                }
            }
        });
    }

    private void keliKatrine(Conversation c) {
        c.player("I think Katrine is still tougher")
         .npc("Well you can think that all you like")
         .npc("I know those blackarm cowards dare not leave the city")
         .npc("Out here, I am toughest. You can tell them that!")
         .npc("Now get out of my sight, before I call my guards");
    }

    private void keliTrained(Conversation c) {
        c.player("You must have trained a lot for this work")
         .npc("I have used a sword since I was a small girl")
         .npc("stabbed three people before I was 6 years old");
    }

    /**
     * The polite way out, offered under six different menus. One of those
     * six labels ends "..., great lady", but the line spoken is the same as
     * the other five.
     */
    private void keliDisturb(Conversation c) {
        c.player("I should not disturb someone as tough as you")
         .npc("I need to do a lot of work, goodbye")
         .npc("When you get a little tougher, maybe I will give you a job");
    }

    /** "No I have never really heard of you" -- an entire branch we lacked. */
    private void keliNeverHeard(Conversation c) {
        c.player("No I have never really heard of you")
         .npc("You must be new to this land then")
         .npc("EVERYONE knows of Lady Keli and her prowess with the sword")
         .picker(new Choice("No, still doesn't ring a bell",
                            "Yes, of course I have heard of you",
                            "You must have trained a lot for this work",
                            "I should not disturb someone as tough as you") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:  keliRingABell(c); return;
                    case 1:  keliHeard(c);     return;
                    case 2:  keliTrained(c);   return;
                    default: keliDisturb(c);   return;
                }
            }
        });
    }

    private void keliRingABell(Conversation c) {
        c.player("No, your name still doesn't ring a bell")
         .npc("Well, you know of me now")
         .npc("I will ring your bell if you do not show respect")
         .picker(new Choice("I do not show respect to killers and hoodlums",
                            "You must have trained a lot for this work",
                            "I should not disturb someone as tough as you, great lady") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    keliTrained(c);
                    return;
                }
                if (option == 2) {
                    keliDisturb(c);
                    return;
                }
                c.player("I do not show respect to killers and hoodlums")
                 .npc("You should, you really should")
                 .npc("I am wealthy enough to place a bounty on your head")
                 .npc("Or just remove your head myself")
                 .npc("Now go, I am busy, too busy to fight a would-be hoodlum");
            }
        });
    }

    private void keliPlan(Conversation c) {
        c.player("What is your latest plan then?")
         .player("Of course you need not go into specific details")
         .npc("Well, I can tell you, I have a valuable prisoner here in my cells")
         .npc("I can expect a high reward to be paid very soon for this guy")
         .npc("I can't tell you who he is, but he is a lot colder now")
         .picker(new Choice("Ah, I see. You must have been very skilful",
                            "Thats great, are you sure they will pay?",
                            "Can you be sure they will not try to get him out?",
                            "I should not disturb someone as tough as you") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:  keliSkilful(c);    return;
                    case 1:  keliPay(c);        return;
                    case 2:  keliGetHimOut(c);  return;
                    default: keliDisturb(c);    return;
                }
            }
        });
    }

    /**
     * Flattering her about the kidnap. The three-option menu underneath is
     * the plan menu minus the option just taken -- Jagex did not loop back to
     * the full list, so neither does this.
     */
    private void keliSkilful(Conversation c) {
        c.player("You must have been very skilful")
         .npc("Yes, I did most of the work, we had to grab the Pr...")
         .npc("er, we had to grab him under cover of ten of his bodyguards")
         .npc("It was a stronke of genius")
         .picker(new Choice("Are you sure they will pay?",
                            "Can you be sure they will not try to get him out?",
                            "I should not disturb someone as tough as you") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:  keliPay(c);       return;
                    case 1:  keliGetHimOut(c); return;
                    default: keliDisturb(c);   return;
                }
            }
        });
    }

    private void keliPay(Conversation c) {
        c.player("Are you sure they will pay?")
         .npc("They will pay, or we will cut his hair off and send it to them")
         .player("Don't you think that something tougher, maybe cut his finger off?")
         .npc("Thats a good idea. I could use talented people like you")
         .npc("I may call on you if I need work doing");
    }

    /** The branch the quest actually needs: it is where the key comes up. */
    private void keliGetHimOut(Conversation c) {
        c.player("Can you be sure they will not try to get him out?")
         .npc("There is no way to release him")
         .npc("The only key to the door is on a chain around my neck")
         .npc("And the locksmith who made the lock,")
         .npc("died suddenly when he had finished")
         .npc("There is not another key like this in the world")
         .picker(new Choice("Could I see the key please",
                            "That is a good way to keep secrets",
                            "I should not disturb someone as tough as you") {
            public void picked(int option, Conversation c) {
                switch (option) {
                    case 0:  keliSeeKey(c);  return;
                    case 1:  keliSecrets(c); return;
                    default: keliDisturb(c); return;
                }
            }
        });
    }

    private void keliSecrets(Conversation c) {
        c.player("That is a good way to keep secrets")
         .npc("It is the best way I know")
         .npc("Dead men tell no tales")
         .player("I am glad I know none of your secrets, Keli");
    }

    /**
     * The point of the whole conversation: getting a look at the key.
     *
     * Jagex's only condition on the imprint is soft clay in the bag. It does
     * not check the quest stage, and it does not check whether the player is
     * already carrying a keyprint, so neither does this -- the conditions this
     * server used to impose here were its own, and a player who lost a keyprint
     * had no way back.
     */
    private void keliSeeKey(Conversation c) {
        c.player("Could I see the key please, just for a moment")
         .player("It would be something I can tell my grandchildren")
         .player("When you are even more famous than you are now")
         .npc("As you put it that way, I am sure you can see it")
         .npc("You cannot steal the key, it is on an Adamantite chain")
         .npc("I cannot see the harm")
         .message("Keli shows you a small key on a stronglooking chain");
        if (count(SOFT_CLAY) < 1) {
            c.npc("There, run along now, I am very busy");
            return;
        }
        c.picker(new Choice("Could I touch the key for a moment please",
                            "I should not disturb someone as tough as you") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    keliDisturb(c);
                    return;
                }
                c.player("Could I touch the key for a moment please")
                 .npc("Only for a moment then")
                 .message("You put a piece of your soft clay in your hand")
                 .message("As you touch the key, you take an imprint of it")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         Player p = c.getPlayer();
                         if (p.getInventory().countId(SOFT_CLAY) < 1) {
                             c.stop();
                             return;
                         }
                         p.getInventory().remove(SOFT_CLAY, 1);
                         p.getInventory().add(new InvItem(KEYPRINT, 1));
                         p.getActionSender().sendInventory();
                     }
                 })
                 .player("Thankyou so much, you are too kind, o great Keli")
                 .npc("There, run along now, I am very busy");
            }
        });
    }

    /**
     * Rope on Keli. Transcript:Lady Keli carries two sections for this, one for
     * the first time and one for every time after, and they are what she says
     * now. This server had invented its own pair.
     *
     * "Again" is remembered through {@link #note}, not through the session
     * field beside it, because the tie itself wears off on logout but having
     * once done it does not.
     *
     * The three refusals below are still ours: no source records what Jagex
     * said to a player who tried this too early.
     */
    private void tieUpKeli(InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != ROPE) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (getStage() < HIRED || rescued() || completed()) {
            p.getActionSender().sendMessage("There is no reason to do that");
            return;
        }
        if (!this.joeDrunk) {
            p.getActionSender().sendMessage("The door guard is watching you too closely");
            return;
        }
        if (this.keliTied) {
            p.getActionSender().sendMessage("She is already tied up");
            return;
        }
        p.getInventory().remove(ROPE, 1);
        p.getActionSender().sendInventory();
        if (reached("keli_tied")) {
            p.getActionSender().sendMessage("You overpower Keli again, tie her up, and put her in a cupboard");
            p.getActionSender().sendMessage("You must open the door to rescue the prince before she escapes");
        } else {
            p.getActionSender().sendMessage("You overpower Keli, tie her up, and put her in a cupboard");
            note("keli_tied");
        }
        this.keliTied = true;
    }

    // --------------------------------------------------------- cell door --

    private boolean isCellDoor(GameObject door) {
        return door.getID() == CELL_DOOR
            && door.getX() == CELL_DOOR_X && door.getY() == CELL_DOOR_Y;
    }

    /**
     * Opening the door by hand. It never opens this way -- the key has to be
     * used on it, which is what the door says. Both lines are Jagex's, recorded
     * on the bronze key's page.
     */
    private void openCellDoor(GameObject door, QuestTrigger trigger) {
        Player p = getOwner();
        if (!isCellDoor(door)) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (trigger == QuestTrigger.DOOR_ACT2) {
            p.getActionSender().sendMessage("The door is shut");
            return;
        }
        // The lock is on the outside only. From inside the cell a plain Open
        // always goes through -- which is how the player leaves after handing
        // the prince the key. A previous revision said "The door is locked"
        // from both sides, so completing the rescue trapped the rescuer.
        if (p.getX() >= CELL_DOOR_X) {
            p.getActionSender().sendMessage("You go through the door");
            passCellDoor(door);
            return;
        }
        p.getActionSender().sendMessage("The door is locked");
        p.getActionSender().sendMessage("Maybe you should try using your key on it");
    }

    /** Swap in the walk-through frame and step the player across. */
    private void passCellDoor(GameObject door) {
        Player p = getOwner();
        p.getActionSender().sendSound("opendoor");
        world.registerGameObject(new GameObject(door.getLocation(), OPEN_DOOR,
            door.getDirection(), door.getType()));
        world.delayedSpawnObject(door.getLoc(), 1000);
        // The door faces east-west, so the cell is everything east of it.
        p.teleport(p.getX() < CELL_DOOR_X ? CELL_DOOR_X : CELL_DOOR_X - 1,
            CELL_DOOR_Y, false);
    }

    private void unlockCellDoor(GameObject door, InvItem used) {
        Player p = getOwner();
        if (!isCellDoor(door)) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (used == null || used.getID() != BRONZE_KEY) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!this.keliTied && !rescued() && !completed()) {
            p.getActionSender().sendMessage("You'd better get rid of Lady Keli before trying to go through here");
            return;
        }
        passCellDoor(door);
    }

    // ------------------------------------------------------------- items --

    /** Yellow dye on the wool wig. */
    private void dyeWig(InvItem first, InvItem second) {
        Player p = getOwner();
        if (second == null) {
            return;
        }
        int a = first.getID();
        int b = second.getID();
        if (!((a == YELLOWDYE && b == WIG_WOOL) || (a == WIG_WOOL && b == YELLOWDYE))) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        p.getInventory().remove(YELLOWDYE, 1);
        p.getInventory().remove(WIG_WOOL, 1);
        p.getInventory().add(new InvItem(WIG_BLONDE, 1));
        p.getActionSender().sendInventory();
        // "blond" is Jagex's spelling, recorded on the wig's page.
        p.getActionSender().sendMessage("You dye the wig blond");
    }

    // ------------------------------------------------------------ prince --

    private void talkToPrince(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);

        if (rescued() || completed()) {
            c.npc("I owe you my life for that escape")
             .npc("You cannot help me this time, they know who you are")
             .npc("Go in peace, friend of Al Kharid")
             .start();
            return;
        }

        if (getStage() < HIRED) {
            /* Not reachable in vanilla -- the cell door needs Keli's key, which
             * needs Leela -- so Jagex recorded nothing for it. Rather than
             * invent a line, this falls back on the two he really does say to
             * someone who turns up without the means to get him out. */
            c.npc("You don't seem to have all I need to escape yet")
             .npc("I dare not risk death to these people")
             .start();
            return;
        }

        c.player("Prince, I come to rescue you")
         .npc("That is very very kind of you, how do I get out?")
         .player("With a disguise, I have removed the Lady Keli")
         .player("She is tied up, but will not stay tied up for long");

        if (!hasDisguise() || count(BRONZE_KEY) < 1 || !this.keliTied) {
            c.npc("You don't seem to have all I need to escape yet")
             .npc("I dare not risk death to these people")
             .start();
            return;
        }

        c.player("Take this disguise, and this key")
         .then(new Effect() {
             public void run(Conversation c) {
                 Player p = c.getPlayer();
                 p.getInventory().remove(WIG_BLONDE, 1);
                 p.getInventory().remove(PINK_SKIRT, 1);
                 p.getInventory().remove(PASTE, 1);
                 p.getInventory().remove(BRONZE_KEY, 1);
                 p.getActionSender().sendInventory();
                 setStage(advancePaid() ? RESCUED_PAID : RESCUED);
             }
         })
         .npc("Thankyou my friend, I must leave you now")
         .npc("My father will pay you well for this")
         .player("Go to Leela, she is close to here");
        c.start();
    }
}
