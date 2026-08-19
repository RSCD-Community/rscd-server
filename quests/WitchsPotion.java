import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Witch's potion.
 *
 * Hetty in Rimmington brews a potion to "bring out your darker self" and wants
 * an eye of newt, a rat's tail, an onion and some burnt meat for it. The reward
 * is magic experience.
 *
 * The quest does not end when Hetty takes the ingredients -- she tells you to
 * drink from the cauldron, and it is the drink that finishes it. That is why
 * there is a stage between handing over and completion, and why this quest
 * claims the cauldron as well as Hetty.
 *
 * Hetty stands at (317, 667) and her cauldron is object 147 at (316, 666); the
 * other cauldrons in the world are id 257 and are not hers.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class WitchsPotion extends Quest {

    public final static int UID = Quests.WITCHS_POTION;

    private static final int STARTED = 1;
    /** Ingredients handed over; Hetty is waiting for you to drink. */
    private static final int BREWED = 2;
    private static final int FINISHED = 3;

    private static final int HETTY = 148;
    private static final int CAULDRON = 147;

    private static final int EYE_OF_NEWT = 270;
    private static final int RATS_TAIL = 271;
    private static final int ONION = 241;
    private static final int BURNT_MEAT = 134;

    private static final int MAGIC = 6; /* skill index */

    public WitchsPotion(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Witch's potion");
        setFinalStage(FINISHED);
        associateNpc(HETTY);
        associateObject(CAULDRON);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Become one with your darker side. Tap into your hidden depths of magical potential by making a potion with the help of Hetty the Rimmington witch.");
        setStartPoint("Rimmington");
        setSpeakTo("Hetty");
        setMissionLength("Short");
        rewardExp(MAGIC, 225, 50);
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Witch's potion quest");
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc && trigger == QuestTrigger.NPC_TALK) {
            if (((Npc) entity).getID() == HETTY) {
                talkToHetty((Npc) entity);
            }
        } else if (entity instanceof GameObject) {
            if (((GameObject) entity).getID() == CAULDRON) {
                useCauldron();
            }
        }
    }

    // ------------------------------------------------------------- Hetty --

    private void talkToHetty(Npc npc) {
        if (completed()) {
            new Conversation(getOwner(), npc)
                .npc("Greetings Traveller")
                .npc("How's your magic coming along?")
                .player("I'm practicing and slowly getting better")
                .npc("good good")
                .start();
            return;
        }
        if (getStage() == BREWED) {
            new Conversation(getOwner(), npc)
                .npc("Greetings Traveller")
                .npc("Well are you going to drink the potion or not?")
                .start();
            return;
        }
        if (questStarted()) {
            handIn(npc);
            return;
        }
        offerQuest(npc);
    }

    private void offerQuest(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Greetings Traveller")
            .npc("What could you want with an old woman like me?")
            .options(new Choice("I am in search of a quest",
                                "Show me the mysteries of the dark arts",
                                "I've heard that you're a witch") {
                public void picked(int option, Conversation c) {
                    if (option == 2) {
                        c.npc("Yes it does seem to be getting fairly common knowledge")
                         .npc("I fear I may get a visit from the witch hunters of Falador before long");
                        return;
                    }
                    if (option == 1) {
                        // Asking outright skips her preamble and goes straight
                        // to the offer, as it did in the original.
                        acceptOffer(c);
                        return;
                    }
                    c.npc("Hmm maybe I can think of something for you")
                     .npc("Would you like to become more proficient in the dark arts?")
                     .options(new Choice("Yes help me become one with my darker side",
                                         "What you mean improve my magic?",
                                         "No, I have my principles and honour") {
                         public void picked(int option, Conversation c) {
                             if (option == 2) {
                                 c.npc("Suit yourself, but you're missing out");
                                 return;
                             }
                             if (option == 1) {
                                 c.npc("Yes improve your magic")
                                  .npc("Do you have no sense of drama?")
                                  .options(new Choice("Yes I'd like to improve my magic",
                                                      "No I'm not interested") {
                                      public void picked(int option, Conversation c) {
                                          if (option == 0) {
                                              acceptOffer(c);
                                          } else {
                                              c.npc("Many aren't to start off with")
                                               .npc("But I think you'll be drawn back to this place");
                                          }
                                      }
                                  });
                                 return;
                             }
                             acceptOffer(c);
                         }
                     });
                }
            }.says(0, "I'm in search of a quest")
             .says(2, "I've heard that you are a witch"))
            .start();
    }

    private void acceptOffer(Conversation c) {
        c.npc("Ok I'm going to make a potion to help bring out your darker self")
         .npc("So that you can perform acts of dark magic with greater ease")
         .npc("You will need certain ingredients")
         .player("What do I need")
         .npc("You need an eye of newt, a rat's tail, an onion and a piece of burnt meat")
         .then(new Effect() {
             public void run(Conversation c) {
                 setStage(STARTED);
             }
         });
    }

    private void handIn(Npc npc) {
        Player p = getOwner();
        boolean got = p.getInventory().countId(EYE_OF_NEWT) > 0
                   && p.getInventory().countId(RATS_TAIL) > 0
                   && p.getInventory().countId(ONION) > 0
                   && p.getInventory().countId(BURNT_MEAT) > 0;

        Conversation c = new Conversation(p, npc);
        c.npc("Greetings Traveller")
         .npc("So have you found the things for the potion");
        if (!got) {
            c.player("No not yet")
             .npc("Well remember you need to get")
             .npc("An eye of newt, a rat's tail,some burnt meat and an onion");
        } else {
            c.player("Yes I have everything")
             .npc("Excellent, can I have them then?")
             .then(new Effect() {
                 public void run(Conversation c) {
                     c.getPlayer().getInventory().remove(EYE_OF_NEWT, 1);
                     c.getPlayer().getInventory().remove(RATS_TAIL, 1);
                     c.getPlayer().getInventory().remove(ONION, 1);
                     c.getPlayer().getInventory().remove(BURNT_MEAT, 1);
                     c.getPlayer().getActionSender().sendInventory();
                     setStage(BREWED);
                 }
             })
             .npc("Ok drink from the cauldron");
        }
        c.start();
    }

    // ---------------------------------------------------------- cauldron --

    /**
     * The cauldron. Claiming an object takes it away from the default handler
     * entirely, so this has to answer for every player who clicks it, not only
     * the ones mid-quest.
     */
    private void useCauldron() {
        Player p = getOwner();
        if (getStage() == BREWED) {
            p.getActionSender().sendMessage("You drink from the cauldron.");
            p.getActionSender().sendMessage("You feel a little strange...");
            setStage(getFinalStage());
        } else if (completed()) {
            p.getActionSender().sendMessage("You have already drunk Hetty's potion.");
        } else {
            p.getActionSender().sendMessage("You look into the cauldron.");
            p.getActionSender().sendMessage("The liquid inside looks most unpleasant.");
        }
    }
}
