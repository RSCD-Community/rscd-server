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
 * Doric's quest.
 *
 * Doric the dwarf smith wants 6 clay, 4 copper ore and 2 iron ore in exchange
 * for 180 coins, some mining experience, and permission to use his anvils.
 *
 * Unlike Sheep shearer there is no part-payment: Doric takes all twelve ores at
 * once or none, so this is a plain two-stage quest.
 *
 * The anvil permission is the part with a lasting effect -- his anvils turn
 * away anyone who has not done this -- so completion is what the anvil check
 * should consult, not a separate flag.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class DoricsQuest extends Quest {

    public final static int UID = Quests.DORICS_QUEST;

    private static final int STARTED = 1;
    private static final int FINISHED = 2;

    private static final int DORIC = 144;
    private static final int CLAY = 149;
    private static final int COPPER_ORE = 150;
    private static final int IRON_ORE = 151;
    private static final int COINS = 10;

    private static final int CLAY_NEEDED = 6;
    private static final int COPPER_NEEDED = 4;
    private static final int IRON_NEEDED = 2;

    private static final int REWARD_COINS = 180;
    private static final int MINING = 14; /* skill index */

    public DoricsQuest(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Doric's quest");
        setFinalStage(FINISHED);
        associateNpc(DORIC);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Doric the dwarf is happy to let you use his anvils but first he would like you to run an errand for him.");
        setStartPoint("Anvils north of Falador");
        setSpeakTo("Doric");
        setMissionLength("Short");
        rewardItem(COINS, REWARD_COINS);
        rewardExp(MINING, 175, 75);
        rewardOther("Use of Doric's anvils");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed Doric's quest");
    }

    /** Doric's anvils are his own until this is done. */
    public boolean mayUseAnvils() {
        return completed();
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger != QuestTrigger.NPC_TALK || !(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (npc.getID() != DORIC) {
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

    // ------------------------------------------------------------- before --

    private void offerQuest(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("Hello traveller, what brings you to my humble smithy?")
            .options(new Choice("I wanted to use your anvils",
                                "What do you make here?",
                                "I was just checking out the landscape",
                                "Mind your own business, shortstuff") {
                public void picked(int option, Conversation c) {
                    switch (option) {
                        case 0:
                            askForMaterials(c);
                            break;
                        case 1:
                            c.npc("I make amulets. I am the best maker of them in Runescape")
                             .player("Do you have any to sell?")
                             .npc("Not at the moment, sorry. Try again later");
                            break;
                        case 2:
                            c.npc("We have a fine town here, it suits us very well")
                             .npc("Please enjoy your travels. And do visit my friends in their mine");
                            break;
                        default:
                            c.npc("How nice to meet someone with such pleasant manners")
                             .npc("Do come again when you need to shout at someone smaller than you");
                            break;
                    }
                }
            })
            .start();
    }

    private void askForMaterials(Conversation c) {
        c.npc("My anvils get enough work with my own use")
         .npc("I make amulets, it takes a lot of work.")
         .npc("If you could get me some more materials I could let you use them")
         .options(new Choice("Yes I will get you materials",
                             "No, hitting rocks is for the boring people, sorry") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     c.npc("That is your choice, nice to meet you anyway");
                     return;
                 }
                 c.npc("Well, clay is what I use more than anything. I make casts")
                  .npc("Could you get me 6 clay, and 4 copper ore and 2 iron ore please?")
                  .npc("I could pay a little, and let you use my anvils")
                  .player("Certainly, I will get them for you. goodbye")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          setStage(STARTED);
                      }
                  });
             }
         });
    }

    // ------------------------------------------------------------- during --

    private void handIn(Npc npc) {
        Player p = getOwner();
        boolean got = p.getInventory().countId(CLAY) >= CLAY_NEEDED
                   && p.getInventory().countId(COPPER_ORE) >= COPPER_NEEDED
                   && p.getInventory().countId(IRON_ORE) >= IRON_NEEDED;

        Conversation c = new Conversation(p, npc);
        c.npc("Have you got my materials yet traveller?");
        if (!got) {
            c.player("Sorry, I don't have them all yet")
             .npc("Not to worry, stick at it")
             .npc("Remember I need 6 Clay, 4 Copper ore and 2 Iron ore");
        } else {
            c.player("I have everything you need")
             .npc("Many thanks, pass them here please")
             .then(new Effect() {
                 public void run(Conversation c) {
                     c.getPlayer().getInventory().remove(CLAY, CLAY_NEEDED);
                     c.getPlayer().getInventory().remove(COPPER_ORE, COPPER_NEEDED);
                     c.getPlayer().getInventory().remove(IRON_ORE, IRON_NEEDED);
                     c.getPlayer().getActionSender().sendInventory();
                 }
             })
             .npc("I can spare you some coins for your trouble")
             .npc("Please use my anvils any time you want")
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
            .npc("Hello traveller, how is your Metalworking coming along?")
            .player("Not too bad thanks Doric")
            .npc("Good, the love of metal is a thing close to my heart")
            .start();
    }
}
