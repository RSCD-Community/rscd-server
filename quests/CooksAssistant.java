import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Cook's assistant -- the first quest Jagex shipped, 4 January 2001.
 *
 * The cook in Lumbridge castle has forgotten the ingredients for the duke's
 * birthday cake and wants a bucket of milk, a pot of flour and an egg. There is
 * no combat and nothing to fail; the whole quest is the conversation, which is
 * why it is a good first one to rebuild.
 *
 * The dialogue is Jagex's, taken line for line from the recorded transcript of
 * the live game. Where the original branched on how many ingredients the player
 * was carrying, so does this.
 *
 * Reward: 1 quest point, and cooking experience of (level * 50) + 250. In the
 * original the multiplier was applied to the player's cooking level at the
 * moment of completion, so finishing it late is worth more.
 */
public class CooksAssistant extends Quest {

    public final static int UID = Quests.COOKS_ASSISTANT;

    /** Stages. -1 is "never spoken to the cook", which Quest gives us free. */
    private static final int STARTED = 1;
    private static final int FINISHED = 2;

    private static final int COOK = 7;
    private static final int MILK = 22;   /* Bucket of milk */
    /**
     * "flour -- There is flour in this pot". Not item 23, which is a separate,
     * untradeable "little heap of flour"; the cook wants the potted kind, and
     * that is the one the mill and the food shops give out.
     */
    private static final int FLOUR = 136;
    private static final int EGG = 19;

    private static final int COOKING = 7; /* skill index */

    public CooksAssistant(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Cook's assistant");
        setFinalStage(FINISHED);
        associateNpc(COOK);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("The lumbridge castle cook is in a mess. It is the duke of Lumbridge's birthday and the cook is making the cake. He needs a lot of ingredients and doesn't have much time.");
        setStartPoint("Lumbridge Castle");
        setSpeakTo("Cook");
        setMissionLength("Short");
        rewardExp(COOKING, 250, 50);
        /* RuneHQ and derived/quest_rewards.json both list this beside the
         * experience. Declared for the manual only: nothing in this server
         * gates the Lumbridge castle range on quest completion, before or
         * after this fix, so anyone could always use it. */
        rewardOther("Access to the Cook's Range in Lumbridge Castle");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Cook's assistant quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger != QuestTrigger.NPC_TALK || !(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (npc.getID() != COOK) {
            return;
        }
        if (completed()) {
            afterwards(npc);
        } else if (questStarted()) {
            duringQuest(npc);
        } else {
            offerQuest(npc);
        }
    }

    // ------------------------------------------------------------- before --

    private void offerQuest(Npc npc) {
        final Conversation c = new Conversation(getOwner(), npc);
        c.npc("What am I to do?")
         .options(new Choice("What's wrong?",
                             "You don't look very happy",
                             "Nice hat",
                             "Well you could give me all your money") {
             public void picked(int option, Conversation c) {
                 switch (option) {
                     case 0:
                         explainProblem(c);
                         break;
                     case 1:
                         c.npc("No, I'm not")
                          .options(new Choice("What's wrong?",
                                              "I'd take the rest of the day off if I were you") {
                              public void picked(int option, Conversation c) {
                                  if (option == 1) {
                                      c.npc("No, that's the worst thing I could do - I'd get in terrible trouble");
                                  }
                                  // Both answers lead back to the same place: in
                                  // the original the player could stall as long
                                  // as they liked and still end up being asked.
                                  explainProblem(c);
                              }
                          });
                         break;
                     case 2:
                         c.npc("Err thank you -it's a pretty ordinary cooks hat really");
                         break;
                     default:
                         c.npc("HaHa very funny");
                         break;
                 }
             }
         })
         .start();
    }

    /** The cook's account of his predicament, and the offer that follows it. */
    private void explainProblem(Conversation c) {
        c.npc("Ooh dear I'm in a terrible mess")
         .npc("It's the duke's birthday today")
         .npc("I'm meant to be making him a big cake for this evening")
         .npc("Unfortunately, I've forgotten to buy some of the ingredients")
         .npc("I'll never get them in time now")
         .npc("I don't suppose you could help me?")
         .options(new Choice("Yes, I'll help you",
                             "No, I don't feel like it. Maybe later") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     c.npc("OK, suit yourself");
                     return;
                 }
                 c.npc("Oh thank you, thank you")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(STARTED);
                      }
                  })
                  .npc("I need milk, eggs and flour")
                  .npc("I'd be very grateful if you can get them to me");
             }
         });
    }

    // ------------------------------------------------------------- during --

    private void duringQuest(Npc npc) {
        Player p = getOwner();
        final boolean milk = p.getInventory().countId(MILK) > 0;
        final boolean flour = p.getInventory().countId(FLOUR) > 0;
        final boolean egg = p.getInventory().countId(EGG) > 0;

        Conversation c = new Conversation(p, npc);
        c.npc("How are you getting on with finding the ingredients?");

        if (milk && flour && egg) {
            c.player("I now have everything you need for your cake")
             .player("Milk, flour, and an egg!")
             .then(new Effect() {
                 public void run(Conversation c) {
                     c.getPlayer().getInventory().remove(MILK, 1);
                     c.getPlayer().getInventory().remove(FLOUR, 1);
                     c.getPlayer().getInventory().remove(EGG, 1);
                     c.getPlayer().getActionSender().sendInventory();
                     setStage(getFinalStage());
                 }
             })
             .npc("I am saved thankyou!");
        } else if (!milk && !flour && !egg) {
            c.player("I'm afraid I don't have any yet!")
             .npc("Oh dear oh dear!")
             .npc("I need flour, eggs, and milk")
             .npc("Without them I am doomed!");
        } else {
            // The cook lists back exactly what you brought, then exactly what is
            // still missing -- so the player is never left guessing which of the
            // three they forgot.
            c.player("I have found some of the things you asked for:");
            if (milk) {
                c.player("I have some milk");
            }
            if (flour) {
                c.player("I have some flour");
            }
            if (egg) {
                c.player("I have an egg");
            }
            c.npc("Great, but can you get the other ingredients as well?")
             .npc("You still need to find");
            if (!milk) {
                c.npc("Some milk");
            }
            if (!flour) {
                c.npc("Some flour");
            }
            if (!egg) {
                c.npc("An egg");
            }
        }
        c.start();
    }

    // -------------------------------------------------------------- after --

    private void afterwards(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello friend, how is the adventuring going?")
            .options(new Choice("I am getting strong and mighty",
                                "I keep on dying",
                                "Nice hat",
                                "Can I use your range?") {
                public void picked(int option, Conversation c) {
                    switch (option) {
                        case 0:
                            c.npc("Glad to hear it");
                            break;
                        case 1:
                            c.npc("Ah well at least you keep coming back to life!");
                            break;
                        case 2:
                            c.npc("Err thank you -it's a pretty ordinary cooks hat really");
                            break;
                        default:
                            c.npc("Go ahead")
                             .npc("It's a very good range")
                             .npc("It's easier to use than most other ranges");
                            break;
                    }
                }
            })
            .start();
    }
}
