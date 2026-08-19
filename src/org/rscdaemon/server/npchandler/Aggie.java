package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.QuestManager;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Aggie the witch, Draynor village. She makes dye.
 *
 * A handler rather than a quest, deliberately. Aggie is needed by two quests --
 * Goblin diplomacy for the dyes and Prince Ali rescue for the skin paste -- and
 * a quest that associates an npc takes it away from every other quest, so
 * putting her inside either one would break the other. She lives out here and
 * asks the player's QuestManager what it needs to know instead.
 *
 * Red: 3 redberries and 5 coins. Yellow: 2 onions and 5 coins. Blue: 2 woad
 * leaves and 5 coins. There is no orange dye for sale -- red mixed into yellow
 * makes it, in InvUseOnItem.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class Aggie implements NpcHandler {

    private static final int COINS = 10;
    private static final int FEE = 5;

    private static final int REDBERRIES = 236;
    private static final int ONION = 241;
    private static final int WOAD_LEAF = 281;

    private static final int REDDYE = 238;
    private static final int YELLOWDYE = 239;
    private static final int BLUEDYE = 272;

    /* Prince Ali rescue: the skin paste. */
    private static final int BUCKET = 21;
    private static final int BUCKET_OF_WATER = 50;
    private static final int POT = 135;
    private static final int POT_OF_FLOUR = 136;
    private static final int JUG = 140;
    private static final int JUG_OF_WATER = 141;
    private static final int ASHES = 181;
    private static final int PASTE = 240;

    /** The three recipes, in the order Aggie offers them. */
    private static final int[] INGREDIENT = { REDBERRIES, ONION, WOAD_LEAF };
    private static final int[] AMOUNT = { 3, 2, 2 };
    private static final int[] PRODUCT = { REDDYE, YELLOWDYE, BLUEDYE };
    private static final String[] COLOUR = { "red", "yellow", "Blue" };

    public void handleNpc(Npc npc, Player player) throws Exception {
        // The paste line is only there while Prince Ali rescue is running; the
        // wiki records that Aggie stops offering it once the quest is over.
        final boolean paste = pasteWanted(player);
        String[] options = paste
            ? new String[]{"Can you make dyes for me please",
                           "Could you think of a way to make pink skin paste",
                           "What could you make for me",
                           "Cool, do you turn people into frogs?",
                           "You mad old witch, you can't help me"}
            : new String[]{"Can you make dyes for me please",
                           "What could you make for me",
                           "Cool, do you turn people into frogs?",
                           "You mad old witch, you can't help me"};

        new Conversation(player, npc)
            .npc("What can I help you with?")
            .options(new Choice(options) {
                public void picked(int option, Conversation c) {
                    // Fold the optional second entry away so the rest of the
                    // branch numbering does not depend on whether it is there.
                    if (paste && option == 1) {
                        skinPaste(c);
                        return;
                    }
                    int choice = paste && option > 1 ? option - 1 : option;
                    switch (choice) {
                        case 0:
                            c.npc("What sort of dye would you like? Red, yellow or Blue?");
                            offerDyes(c);
                            break;
                        case 1:
                            c.npc("I mostly just make what I find pretty")
                             .npc("I sometimes make dye for the womens clothes, brighten the place up")
                             .npc("I can make red,yellow and blue dyes would u like some");
                            offerDyes(c);
                            break;
                        case 2:
                            c.npc("Oh, not for years, but if you met a talking chicken,")
                             .npc("You have probably met the professor in the Manor north of here")
                             .npc("A few years ago it was flying fish, that machine is a menace");
                            break;
                        default:
                            c.npc("Oh, you like to call a witch names, do you?")
                             .npc("You should be careful about insulting a Witch")
                             .npc("You never know what shape you could wake up in");
                            break;
                    }
                }
            })
            .start();
    }

    /**
     * Whether to offer the skin paste: Prince Ali rescue started and not yet
     * finished. The quest's stage numbers live in the quest class, which is
     * loaded out of quests/ into the default package and cannot be imported
     * from here, so this asks the only two questions that cross that line.
     */
    private boolean pasteWanted(Player player) {
        QuestManager qm = player.getQuestManager();
        return qm.stageOf(Quests.PRINCE_ALI_RESCUE) > 0
            && !qm.completed(Quests.PRINCE_ALI_RESCUE);
    }

    // ---------------------------------------------------------- skin paste --

    /**
     * Ash, flour, water and red berries, mixed into a bottle of skin-coloured
     * paste for the prince.
     *
     * Water is taken from either container: the requirements list says a bucket
     * and the walkthrough says a jug, and both hold water, so both are accepted.
     * The empty container and the empty pot come back, as they do everywhere
     * else a recipe uses them up.
     */
    private void skinPaste(Conversation c) {
        if (!hasPasteIngredients(c.getPlayer())) {
            c.npc("Why, its one of my most popular potions")
             .npc("The women here, they like to have smooth looking skin")
             .npc("(and I must admit, some of the men buy it too)")
             .npc("I can make it for you, just get me what needed")
             .player("What do you need to make it?")
             .npc("Well deary, you need a base for the paste")
             .npc("That's a mix of ash, flour and water")
             .npc("Then you need red berries to colour it as you want")
             .npc("bring me those four items and I will make you some");
            return;
        }
        c.npc("Yes I can, you have the ingredients for it already")
         .npc("Would you like me to mix you some?")
         .options(new Choice("Yes please, mix me some skin paste",
                             "No thankyou, I don't need paste") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("Okay dearie, thats always your choice");
                     return;
                 }
                 c.npc("That should be simple, hand the things to Aggie then")
                  .npc("Tourniquet, Fenderbaum, Tottenham, MonsterMunch, MarbleArch")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          Player p = c.getPlayer();
                          if (!hasPasteIngredients(p)) {
                              // Handed something over mid-spell.
                              p.getActionSender().sendMessage("You don't have the ingredients Aggie asked for.");
                              c.stop();
                              return;
                          }
                          boolean bucket = p.getInventory().countId(BUCKET_OF_WATER) > 0;
                          p.getInventory().remove(bucket ? BUCKET_OF_WATER : JUG_OF_WATER, 1);
                          p.getInventory().remove(POT_OF_FLOUR, 1);
                          p.getInventory().remove(ASHES, 1);
                          p.getInventory().remove(REDBERRIES, 1);
                          p.getInventory().add(new InvItem(bucket ? BUCKET : JUG, 1));
                          p.getInventory().add(new InvItem(POT, 1));
                          p.getInventory().add(new InvItem(PASTE, 1));
                          p.getActionSender().sendInventory();
                      }
                  })
                  .npc("There you go dearie, your skin potion")
                  .npc("That will make you look good at the Varrock dances");
             }
         }.says(1, "No thank you, I don't need skin paste"));
    }

    private boolean hasPasteIngredients(Player p) {
        return p.getInventory().countId(ASHES) > 0
            && p.getInventory().countId(POT_OF_FLOUR) > 0
            && p.getInventory().countId(REDBERRIES) > 0
            && (p.getInventory().countId(BUCKET_OF_WATER) > 0
                || p.getInventory().countId(JUG_OF_WATER) > 0);
    }

    private void offerDyes(Conversation c) {
        c.options(new Choice("What do you need to make some red dye please",
                             "What do you need to make some yellow dye please",
                             "What do you need to make some blue dye please",
                             "No thanks, I am happy the colour I am") {
            public void picked(int option, Conversation c) {
                if (option == 3) {
                    c.npc("You are easily pleased with yourself then")
                     .npc("when you need dyes, come to me");
                    return;
                }
                if (option == 0) {
                    c.npc("3 lots of Red berries, and 5 coins, to you");
                } else if (option == 1) {
                    c.npc("Yellow is a strange colour to get, comes from onion skins")
                     .npc("I need 2 onions, and 5 coins to make yellow");
                } else {
                    c.npc("2 woad leaves, and 5 coins, to you");
                }
                confirm(c, option);
            }
        });
    }

    private void confirm(Conversation c, final int which) {
        c.options(new Choice("Okay, make me some some " + COLOUR[which] + " dye please",
                             "I don't think I have all the ingredients yet",
                             "I can do without dye at that price") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.npc("You know what you need to get now, come back when you have them")
                     .npc("goodbye for now");
                    return;
                }
                if (option == 2) {
                    c.npc("Thats your choice, but I would think you have killed for less")
                     .npc("I can see it in your eyes");
                    return;
                }
                c.then(new Effect() {
                    public void run(Conversation c) {
                        Player p = c.getPlayer();
                        if (p.getInventory().countId(INGREDIENT[which]) < AMOUNT[which]
                                || p.getInventory().countId(COINS) < FEE) {
                            p.getActionSender().sendMessage("You don't have the ingredients Aggie asked for.");
                            c.stop();
                            return;
                        }
                        p.getInventory().remove(INGREDIENT[which], AMOUNT[which]);
                        p.getInventory().remove(COINS, FEE);
                        p.getInventory().add(new InvItem(PRODUCT[which], 1));
                        p.getActionSender().sendInventory();
                        p.getActionSender().sendMessage("Aggie mixes you some dye.");
                    }
                });
            }
        });
    }
}
