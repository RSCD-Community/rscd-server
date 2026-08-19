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
 * Imp catcher.
 *
 * Wizard Mizgog, top floor of the Wizards' tower, lost four coloured beads to
 * the imps Grayzag summoned. Bring back all four and he hands over an amulet of
 * accuracy.
 *
 * The beads are an imp drop, so there is nothing for this quest to do between
 * being started and being finished -- no intermediate stage, and the "some but
 * not all" case is only a different line of dialogue, not a different state.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class ImpCatcher extends Quest {

    public final static int UID = Quests.IMP_CATCHER;

    private static final int STARTED = 1;
    private static final int FINISHED = 2;

    private static final int MIZGOG = 117;

    private static final int RED_BEAD = 231;
    private static final int YELLOW_BEAD = 232;
    private static final int BLACK_BEAD = 233;
    private static final int WHITE_BEAD = 234;
    private static final int[] BEADS = { RED_BEAD, YELLOW_BEAD, BLACK_BEAD, WHITE_BEAD };

    private static final int AMULET_OF_ACCURACY = 235;

    private static final int MAGIC = 6; /* skill index */

    public ImpCatcher(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Imp catcher");
        setFinalStage(FINISHED);
        associateNpc(MIZGOG);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("The Wizard Grayzag has summoned hundreds of little imps. They have stolen a lot of things belonging to the Wizard Mizgog including his magic beads.");
        setStartPoint("Wizard's tower");
        setSpeakTo("Wizard Mizgog");
        setMissionLength("Medium");
        rewardItem(AMULET_OF_ACCURACY, 1);
        rewardExp(MAGIC, 375, 100);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Imp catcher quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger != QuestTrigger.NPC_TALK || !(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (npc.getID() != MIZGOG) {
            return;
        }
        if (completed()) {
            afterwards(npc);
        } else if (questStarted()) {
            handIn(npc);
        } else {
            offerQuest(npc);
        }
    }

    /** How many of the four he asked for are in the pack. */
    private int beadsHeld() {
        int held = 0;
        for (int i = 0; i < BEADS.length; ++i) {
            if (getOwner().getInventory().countId(BEADS[i]) > 0) {
                ++held;
            }
        }
        return held;
    }

    // ------------------------------------------------------------- before --

    private void offerQuest(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello there")
            .options(new Choice("Give me a quest!",
                                "Most of your friends are pretty quiet aren't they?") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        quietFriends(c);
                        return;
                    }
                    c.npc("Give me a quest what?")
                     .options(new Choice("Give me a quest please",
                                         "Give me a quest or else",
                                         "Just stop messing around and give me a quest") {
                         public void picked(int option, Conversation c) {
                             if (option == 1) {
                                 c.npc("Or else what? You'll attack me?")
                                  .npc("Hahaha");
                                 return;
                             }
                             if (option == 2) {
                                 c.npc("Ah now you're assuming I have one to give");
                                 return;
                             }
                             c.npc("Well seeing as you asked nicely")
                              .npc("I could do with some help")
                              .npc("The wizard Grayzag next door decided he didn't like me")
                              .npc("So he cast of spell of summoning")
                              .npc("And summoned hundreds of little imps")
                              .npc("These imps stole all sorts of my things")
                              .npc("Most of these things I don't really care about")
                              .npc("They're just eggs and balls of string and things")
                              .npc("But they stole my 4 magical beads")
                              .npc("There was a red one, a yellow one, a black one and a white one")
                              .npc("These imps have now spread out all over the kingdom")
                              .npc("Could you get my beads back for me")
                              .player("I'll try")
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

    // ------------------------------------------------------------- during --

    private void handIn(Npc npc) {
        int held = beadsHeld();
        Conversation c = new Conversation(getOwner(), npc);
        c.npc("So how are you doing finding my beads?");
        if (held == 0) {
            c.player("I've not found any yet")
             .npc("Well get on with it")
             .npc("I've lost a white bead, a red bead, a black bead and a yellow bead")
             .npc("Go kill some imps");
        } else if (held < BEADS.length) {
            c.player("I have found some of your beads")
             .npc("Come back when you have them all")
             .npc("The four colours of beads I need")
             .npc("Are red,yellow,black and white")
             .npc("Go chase some imps");
        } else {
            c.player("I've got all four beads")
             .player("It was hard work I can tell you")
             .npc("Give them here and I'll sort out a reward")
             .then(new Effect() {
                 public void run(Conversation c) {
                     for (int i = 0; i < BEADS.length; ++i) {
                         c.getPlayer().getInventory().remove(BEADS[i], 1);
                     }
                     c.getPlayer().getActionSender().sendInventory();
                 }
             })
             .npc("Here's you're reward then")
             .npc("An Amulet of accuracy")
             .then(new Effect() {
                 public void run(Conversation c) {
                     setStage(getFinalStage());
                 }
             });
        }
        c.start();
    }

    // -------------------------------------------------------------- after --

    private void afterwards(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello there")
            .options(new Choice("Got any more quests?",
                                "Most of your friends are pretty quiet aren't they?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("No Everything is good with the world today");
                    } else {
                        quietFriends(c);
                    }
                }
            })
            .start();
    }

    private void quietFriends(Conversation c) {
        c.npc("Yes they've mostly got their heads in the clouds")
         .npc("Thinking about magic");
    }
}
