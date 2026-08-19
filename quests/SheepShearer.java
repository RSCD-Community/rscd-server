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
 * Sheep shearer.
 *
 * Fred the farmer wants twenty balls of wool: shear his sheep, spin the wool,
 * bring it back. He pays 60 coins and a little crafting experience.
 *
 * The one subtlety is that Fred takes wool in part-payments. Handing him twelve
 * balls and walking off does not lose them -- he keeps count, and asks for the
 * rest next time. That running total is the quest stage, which is why the stages
 * here are the number of balls delivered rather than a list of named steps.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class SheepShearer extends Quest {

    public final static int UID = Quests.SHEEP_SHEARER;

    private static final int FRED = 77;
    private static final int BALL_OF_WOOL = 207;
    private static final int COINS = 10;

    private static final int NEEDED = 20;
    private static final int REWARD_COINS = 60;
    private static final int CRAFTING = 12; /* skill index */

    public SheepShearer(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Sheep shearer");
        // The final stage is the twentieth ball. Stage 0 means the quest has
        // been accepted but nothing has been handed over yet -- distinct from
        // -1, which is "never spoken to Fred".
        setFinalStage(NEEDED);
        associateNpc(FRED);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Farmer Fred's sheep are getting mighty woolly. He will pay you to shear them.");
        setStartPoint("Northwest of Lumbridge");
        setSpeakTo("Farmer Fred");
        setMissionLength("Short");
        rewardItem(COINS, REWARD_COINS);
        rewardExp(CRAFTING, 125, 25);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Sheep shearer quest");
    }

    /** Balls of wool handed over so far. */
    private int delivered() {
        return getStage() < 0 ? 0 : getStage();
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger != QuestTrigger.NPC_TALK || !(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (npc.getID() != FRED) {
            return;
        }
        if (!questStarted() || completed()) {
            greeting(npc);
        } else {
            handIn(npc);
        }
    }

    // ------------------------------------------------------------- before --

    /**
     * Fred's opening. He says the same thing before the quest and after it --
     * the transcript's "after you complete the quest" section is just an arrow
     * back up to this -- so both cases come here.
     */
    private void greeting(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("What are you doing on my land?")
            .npc("You're not the one who keeps leaving all my gates open?")
            .npc("And letting out all my sheep?")
            .options(new Choice("I'm looking for a quest",
                                "I'm looking for something to kill",
                                "I'm lost") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("What on my land?")
                         .npc("Leave my livestock alone you scoundrel");
                        return;
                    }
                    if (option == 2) {
                        c.npc("How can you be lost?")
                         .npc("Just follow the road east and south")
                         .npc("You'll end up in Lumbridge fairly quickly");
                        return;
                    }
                    if (completed()) {
                        // Nothing left to offer, but he should not repeat the
                        // pitch for a quest already done.
                        c.npc("You're after a quest, you say?")
                         .npc("You've done enough for me already");
                        return;
                    }
                    offer(c);
                }
            })
            .start();
    }

    private void offer(Conversation c) {
        c.npc("You're after a quest, you say?")
         .npc("Actually I could do with a bit of help")
         .npc("My sheep are getting mighty woolly")
         .npc("If you could shear them")
         .npc("And while your at it spin the wool for me too")
         .npc("Yes that's it. Bring me 20 balls of wool")
         .npc("And I'm sure I could sort out some sort of payment")
         .npc("Of course, there's the small matter of the thing")
         .options(new Choice("What do you mean, the thing?",
                             "That doesn't sound a very exciting quest") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.npc("I wouldn't worry about it")
                      .npc("Something ate all the previous shearers")
                      .npc("They probably got unlucky")
                      .npc("So are you going to help me?");
                 } else {
                     c.npc("Well what do you expect if you ask a farmer for a quest?")
                      .npc("Now are you going to help me or not?");
                 }
                 c.options(new Choice("Yes okay. I can do that",
                                      "Erm I'm a bit worried about this thing",
                                      "No I'll give it a miss") {
                     public void picked(int option, Conversation c) {
                         if (option == 0) {
                             c.then(new Effect() {
                                 public void run(Conversation c) {
                                     setStage(0);
                                 }
                             }).npc("Ok I'll see you when you have some wool");
                         } else if (option == 1) {
                             c.npc("I'm sure it's nothing to worry about")
                              .npc("It's possible the other shearers aren't dead at all")
                              .npc("And are just hiding in the woods or something")
                              .player("I'm not convinced");
                         }
                         // Declining says nothing further, as in the original.
                     }
                 });
             }
         });
    }

    // ------------------------------------------------------------- during --

    private void handIn(Npc npc) {
        Player p = getOwner();
        final int carrying = p.getInventory().countId(BALL_OF_WOOL);
        final int wanted = NEEDED - delivered();

        Conversation c = new Conversation(p, npc);
        c.npc("How are you doing getting those balls of wool?");

        if (carrying <= 0) {
            // Fred distinguishes "nothing at all" from "wool but not spun", so
            // check the raw wool too rather than lumping both together.
            if (p.getInventory().countId(145) > 0) {
                c.player("Well I've got some wool")
                 .player("I've not managed to make it into a ball though")
                 .npc("Well go find a spinning wheel then")
                 .npc("And get spinning");
            } else {
                c.player("I haven't got any at the moment")
                 .npc("Ah well at least you haven't been eaten");
            }
            c.start();
            return;
        }

        final int handed = carrying < wanted ? carrying : wanted;
        if (handed >= wanted) {
            c.player("Thats all of them");
        } else {
            c.player("I have some");
        }
        c.npc("Give em here then")
         .then(new Effect() {
             public void run(Conversation c) {
                 c.getPlayer().getInventory().remove(BALL_OF_WOOL, handed);
                 c.getPlayer().getActionSender().sendInventory();
             }
         });

        if (handed < wanted) {
            c.player("That's all I've got so far")
             .npc("I need more before I can pay you")
             .player("Ok I'll work on it");
        } else {
            c.npc("I guess I'd better pay you then");
        }
        // Last, not with the wool: reaching the final stage pays out, and the
        // coins should arrive after Fred has said he is paying rather than
        // before he has finished counting.
        c.then(new Effect() {
            public void run(Conversation c) {
                setStage(delivered() + handed);
            }
        });
        c.start();
    }
}
