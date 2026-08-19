import org.rscdaemon.server.model.Entity;
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
 * Scorpion catcher. Released 25 March 2002, written by Paul Gower.
 *
 * Thormac left the cage door open and his three lesser kharid scorpions went
 * everywhere. The Seer knows where, if asked.
 *
 *     Thormac the sorceror  npc 300, (511,1452), the top of the sorcerer's tower
 *     Seer                  npc 301, (524,463) and (524,1406)
 *     Kharid Scorpion       npc 302, (380,3353), the secret room in Taverley
 *                           npc 303, (487,543), the barbarian outpost
 *                           npc 304, (263,1408), upstairs in the monastery
 *
 *     scorpion cage         item 678 empty, 679 with one, 680 with two,
 *                           681 with all three
 *
 * The cage is four items rather than one with a counter, which is how Jagex
 * built it, so the number caught is on the item in the player's hand as well as
 * in the stage. The stage is what decides: a cage can be dropped and Thormac
 * will hand over a replacement holding whatever the player had already caught.
 *
 * Deviations:
 *
 *  - A caught scorpion stays where it is. These are NpcLoc spawns shared by
 *    everybody and unregistering one would take it away from every other player
 *    until the server restarted. The quest refuses to cage the same scorpion
 *    twice instead, which is the part that actually matters.
 *
 *  - Nothing here gates the three hiding places. The barbarian outpost wants
 *    the Alfred Grimhand bar crawl, the monastery ladder wants prayer 31, and
 *    the Taverley secret room wants a dusty key and the Seer's hint. None of
 *    those are this quest's to enforce -- the bar crawl is a mini-quest nobody
 *    has written, the ladder belongs to the monastery, and the odd looking wall
 *    is already handled in WallObjectAction.SECRET_WALLS. Talking to the Seer
 *    is still required to finish, because Jagex made it a checkpoint.
 *
 *  - The scorpions may be caught in any order. Jagex's hints come in one order
 *    and the walkthroughs disagree about whether that order was enforced; the
 *    transcripts only show the Seer counting how many are in the cage.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class ScorpionCatcher extends Quest {

    public final static int UID = Quests.SCORPION_CATCHER;

    private static final int THORMAC = 300, SEER = 301;
    private static final int[] SCORPIONS = { 302, 303, 304 };

    private static final int EMPTY_CAGE = 678;   /* 679, 680, 681 follow it */
    private static final int COINS = 10, ENCHANT_PRICE = 40000;
    private static final int[][] STAFFS = {
        { 615, 682 },  /* fire  */
        { 616, 683 },  /* water */
        { 617, 684 },  /* air   */
        { 618, 685 },  /* earth */
    };
    private static final String[] STAFF_NAMES = { "fire", "water", "air", "earth" };

    private static final int STRENGTH = 2;

    private static final int STARTED = 1;
    private static final int[] CAUGHT = { 2, 4, 8 };
    private static final int ASKED = 16;   /* the Seer has been consulted */
    private static final int DONE = 32;
    private static final int FINISHED = 63;

    public ScorpionCatcher(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Scorpion catcher");
        setFinalStage(FINISHED);
        associateNpc(THORMAC);
        associateNpc(SEER);
        associateNpc(SCORPIONS[0]);
        associateNpc(SCORPIONS[1]);
        associateNpc(SCORPIONS[2]);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Thormac has lost his rare lesser kharid scorpions after leaving their cage door open. These scorpions have hidden in areas that are rather difficult to get into. You will have to overcome various challenges (and drink a lot of beer) to get all the scorpions back If you manage to help him Thormac will improve your battle staffs.");
        setStartPoint("Sorcerer's tower");
        setSpeakTo("Thormac the sorcerer");
        setMissionLength("Long");
        /* The manual's minimum requirement. Nothing in this class enforces it:
           the prayer 31 gate is the monastery ladder's, not the quest's. */
        require("level 31 prayer");
        rewardExp(STRENGTH, 375, 125);
        rewardOther("Thormac will enchant battlestaffs for 40000 coins");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Scorpion catcher quest");
    }

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        setStage((questStarted() ? getStage() : 0) | bit);
    }

    /** How many scorpions the stage says are in the cage. */
    private int caught() {
        int n = 0;
        for (int i = 0; i < CAUGHT.length; i++) {
            if (has(CAUGHT[i])) {
                n++;
            }
        }
        return n;
    }

    /** The cage the player is carrying, or -1. */
    private int cageHeld() {
        Player p = getOwner();
        for (int i = 0; i < 4; i++) {
            if (p.getInventory().countId(EMPTY_CAGE + i) > 0) {
                return EMPTY_CAGE + i;
            }
        }
        return -1;
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        int scorpion = -1;
        for (int i = 0; i < SCORPIONS.length; i++) {
            if (SCORPIONS[i] == npc.getID()) {
                scorpion = i;
            }
        }
        if (scorpion > -1) {
            if (trigger == QuestTrigger.ITEM_ON_NPC) {
                cage(scorpion, used);
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        if (npc.getID() == THORMAC) {
            thormac(npc);
        } else {
            seer(npc);
        }
    }

    private void cage(int scorpion, InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() < EMPTY_CAGE || used.getID() > EMPTY_CAGE + 3) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!questStarted()) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (has(CAUGHT[scorpion])) {
            p.getActionSender().sendMessage("You have already caught this one");
            return;
        }
        int held = used.getID();
        p.getInventory().remove(held, 1);
        set(CAUGHT[scorpion]);
        p.getInventory().add(new InvItem(EMPTY_CAGE + caught(), 1));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("You catch the scorpion in the cage");
        int left = SCORPIONS.length - caught();
        if (left > 0) {
            p.getActionSender().sendMessage("@gre@" + left
                + (left == 1 ? " scorpion" : " scorpions") + " still to find");
        } else {
            p.getActionSender().sendMessage("@gre@That's all of them");
            p.getActionSender().sendMessage("@gre@You should take them back to Thormac");
        }
    }

    // ------------------------------------------------------------- Thormac --

    private void thormac(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .npc("Thankyou for rescuing my scorpions")
                .options(new Choice("You said you'd enchant my battlestaff for me",
                                    "That's ok") {
                    public void picked(int option, Conversation c) {
                        if (option != 0) {
                            return;
                        }
                        c.npc("Yes it'll cost you " + ENCHANT_PRICE
                                + " coins for the materials needed mind you")
                         .npc("Which sort of staff did you want enchanting?");
                        offerStaffs(c);
                    }
                })
                .start();
            return;
        }
        if (questStarted()) {
            if (caught() == SCORPIONS.length && cageHeld() == EMPTY_CAGE + 3) {
                new Conversation(p, npc)
                    .npc("How goes your quest?")
                    .player("I have retrieved all your scorpions")
                    .take(EMPTY_CAGE + 3, 1)
                    .npc("aha my little scorpions home at last")
                    .then(new Effect() {
                        public void run(Conversation c) {
                            set(DONE | ASKED);
                        }
                    })
                    .start();
                return;
            }
            if (cageHeld() < 0) {
                new Conversation(p, npc)
                    .npc("How goes your quest?")
                    .options(new Choice("I've lost my cage",
                                        "I've not caught all the scorpions yet") {
                        public void picked(int option, Conversation c) {
                            if (option != 0) {
                                c.npc("Well remember, go speak to the seers north of here if you need any help");
                                return;
                            }
                            c.npc("Ok here is another cage")
                             .give(new InvItem(EMPTY_CAGE + caught(), 1))
                             .npc("You're almost as bad at loosing things as me");
                        }
                    })
                    .start();
                return;
            }
            new Conversation(p, npc)
                .npc("How goes your quest?")
                .player("I've not caught all the scorpions yet")
                .npc("Well remember, go speak to the seers north of here if you need any help")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("Hello I am Thormac the sorceror")
            .npc("I don't suppose you could be of assistance to me?")
            .options(new Choice("What do you need assistance with?", "I'm a little busy") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("I've lost my pet scorpions")
                     .npc("They're lesser kharid scorpions, a very rare breed")
                     .npc("I left there cage door open")
                     .npc("now I don't know where they have gone")
                     .npc("There's 3 of them and they're quick little beasties")
                     .npc("They're all over runescape")
                     .options(new Choice("So how would I go about catching them then?",
                                         "What's in it for me?",
                                         "I'm not interested then") {
                         public void picked(int option, Conversation c) {
                             if (option == 2) {
                                 c.npc("Blast, I suppose I will have to have find someone else then");
                                 return;
                             }
                             if (option == 1) {
                                 c.npc("Well I suppose I can aid you with my skills as a staff sorcerer")
                                  .npc("Most the battlestaffs around here are pretty puny")
                                  .npc("I can beef them up for you a bit");
                             }
                             // He hands the cage over here, before the player
                             // has agreed to anything, and says something
                             // different to a player who is already carrying
                             // one -- both from Transcript:Thormac the
                             // sorceror. Before the sweep the cage arrived at
                             // the end and the second wording was missing.
                             if (cageHeld() >= 0) {
                                 c.npc("Well you have that scorpion cage I gave you")
                                  .npc("Which you can use to catch them in");
                             } else {
                                 c.npc("Well I have a scorpion cage here")
                                  .npc("Which you can use to catch them in")
                                  .message("Thromac gives you a cage")
                                  .give(new InvItem(EMPTY_CAGE, 1));
                             }
                             c.npc("If you go up to the village of seers to the north of here")
                              .npc("One of them will be able to tell you where the scorpions are now")
                              .options(new Choice("Ok I will do it then", "I'm not interested then") {
                                  public void picked(int option, Conversation c) {
                                      if (option != 0) {
                                          c.npc("Blast, I suppose I will have to have find someone else then");
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
                     });
                }
            })
            .start();
    }

    private void offerStaffs(Conversation c) {
        c.options(new Choice("Battlestaff of fire",
                             "battlestaff of water",
                             "battlestaff of air",
                             "battlestaff of earth",
                             "I won't bother yet actually") {
            public void picked(int option, Conversation c) {
                if (option < 0 || option >= STAFFS.length) {
                    return;
                }
                Player p = c.getPlayer();
                // Money first, then the staff: that is the order the transcript
                // records the two refusals in.
                if (p.getInventory().countId(COINS) < ENCHANT_PRICE) {
                    c.player("I'll just get the money for you");
                    return;
                }
                if (p.getInventory().countId(STAFFS[option][0]) < 1) {
                    c.player("I don't have a battlestaff of " + STAFF_NAMES[option] + " yet though");
                    return;
                }
                // "There you go, one enchanted battlestaff" was ours. The
                // transcript has no line here at all, only a message.
                c.take(COINS, ENCHANT_PRICE)
                 .take(STAFFS[option][0], 1)
                 .give(new InvItem(STAFFS[option][1], 1))
                 .message("Thormac enchants your staff");
            }
        }.says(3, "battlestaff of earth please").says(2, "battlestaff of air please").says(1, "battlestaff of water please").says(0, "battlestaff of fire please"));
    }

    // ---------------------------------------------------------------- Seer --

    private void seer(Npc npc) {
        Player p = getOwner();
        if (!questStarted() || completed()) {
            new Conversation(p, npc)
                .npc("Many greetings")
                .options(new Choice("I seek knowledge and power", "Many greetings") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("Knowledge comes from experience, power comes from battleaxes");
                        }
                    }
                })
                .start();
            return;
        }
        if (!has(ASKED)) {
            new Conversation(p, npc)
                .npc("Many greetings")
                .options(new Choice("Your friend Thormac sent me to speak to you",
                                    "I need to locate some scorpions",
                                    "I seek knowledge and power") {
                    public void picked(int option, Conversation c) {
                        if (option == 2) {
                            c.npc("Knowledge comes from experience, power comes from battleaxes");
                            return;
                        }
                        if (option == 0) {
                            c.npc("What does the old fellow want")
                             .player("He's lost his valuable lesser kharid scorpions")
                             .npc("Well you have come to the right place")
                             .npc("I am a master of animal detection");
                        } else {
                            c.npc("Well you have come to the right place")
                             .npc("I am a master of animal detection")
                             .npc("Do you need to locate any particular scorpion")
                             .npc("Scorpions are a creature somewhat in abundance")
                             .player("I'm looking for some lesser kharid scorpions")
                             .player("They belong to Thormac the sorceror");
                        }
                        firstHint(c);
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                set(ASKED);
                            }
                        });
                    }
                })
                .start();
            return;
        }
        int n = caught();
        Conversation c = new Conversation(p, npc);
        if (n == 1) {
            c.player("Hi I have retrieved the scorpion from near the spiders")
             .npc("Well I've checked my looking glass")
             .npc("There seems to be a kharid scorpion in a village full of axe wielding warriors")
             .npc("One of the warriors there, dressed mainly in black has picked it up")
             .npc("That's all I can tell you about that scorpion");
        } else if (n == 2) {
            c.npc("Many greetings")
             .player("I have retrieved a second scoprion")
             .npc("That's lucky because I've got some information on the last scorpion for you")
             .npc("It seems to be in some sort of upstairs room")
             .npc("There seems to be some sort of brown clothing lying on the floor");
        } else if (n >= 3) {
            // Transcript:Seer records no state for a full cage -- by then the
            // player's business is with Thormac, not with him. What it does
            // record is a catch-all for a player holding scorpions he has not
            // given a hint for, and that is what plays here. The two lines
            // that used to be here, "I have all three of them now" and "Then
            // Thormac will be a happy man", were ours.
            c.player("I need to locate some scorpions")
             .npc("Well you have come to the right place")
             .npc("I am a master of animal detection")
             .npc("Do you need to locate any particular scorpion")
             .npc("Scorpions are a creature somewhat in abundance")
             .player("I'm looking for some lesser kharid scorpions")
             .player("They belong to Thormac the sorceror");
            firstHint(c);
        } else {
            c.npc("Many greetings")
             .player("Where did you say that scorpion was again?");
            firstHint(c);
        }
        c.start();
    }

    private void firstHint(Conversation c) {
        c.npc("Let me look into my looking glass")
         .message("The seer produces a small mirror")
         .message("The seer gazes into the mirror")
         .message("The seer smoothes his hair with his hand")
         .npc("I can see a scorpion that you seek")
         .npc("It would appear to be near some nasty looking spiders")
         .npc("I can see two coffins there as well")
         .npc("The scorpion seems to be going through some crack in the wall")
         .npc("He's gone into some sort of secret room")
         .npc("Well see if you can find that scorpion then")
         .npc("And I'll try and get you some information on the others");
    }
}
