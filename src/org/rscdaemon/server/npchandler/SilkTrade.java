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
 * The two ends of the silk run: the silk trader on the Al Kharid market stall
 * who sells it, and the silk merchant in East Ardougne who buys it back.
 *
 * Both of these lived in {@link FlavorNpcs} with dialogue that was invented
 * whole -- not one line of either matched the recorded conversations, the Al
 * Kharid haggle was a single "can you do it any cheaper" rather than Jagex's
 * two-step refusal, and the Ardougne end had no haggle at all, just a flat
 * sixty coins. The recorded version is the more interesting half of the trade:
 * the merchant asks what you want per piece, and what you name decides whether
 * he agrees, counters, or throws you out.
 *
 * Prices, verbatim from Transcript:Silk merchant. Asking 20 is accepted flat;
 * 80 is countered at 40 and settles at 40 or 50; 120 is countered at 50 and
 * settles at 50 or 60; 200 ends the conversation with nothing. So sixty is the
 * ceiling, and only by naming 120 first and then refusing the counter -- which
 * is the whole point of the haggle and was exactly what our version threw away
 * by paying sixty to anyone who asked.
 *
 * Two things the transcripts do not record and which are therefore ours, and
 * flagged rather than buried:
 *
 *   - How much silk one agreement covers. The merchant says "per piece" and
 *     the wiki records no message for the settlement at all. Selling the whole
 *     holding at the agreed rate is what the trade is remembered for, so that
 *     is what happens here.
 *   - The stall-theft refusal ("Do you really think I'm going to buy something
 *     / That you have just stolen from me / guards guards") is left out. It
 *     needs a record of recent thefts from his own stall, which nothing in the
 *     server keeps; inventing a timer to hang it on would put a guess where a
 *     gap is honest.
 *
 * The silk trader's "buy some silk" message printing even when the player
 * cannot afford it is Jagex's own bug -- the wiki marks it {{sic}} on the
 * two coin branch and not on the three coin one, and both are reproduced as
 * recorded rather than tidied into agreement.
 */
public class SilkTrade implements NpcHandler {

    public static final int SILK_TRADER = 71;
    public static final int SILK_MERCHANT = 326;

    private static final int COINS = 10;
    private static final int SILK = 200;

    private static final int ASKING = 3, HAGGLED = 2;

    public void handleNpc(Npc npc, Player player) throws Exception {
        if (npc.getID() == SILK_MERCHANT) {
            merchant(npc, player);
            return;
        }
        trader(npc, player);
    }

    // ------------------------------------------------------------ trader --

    /**
     * Al Kharid. Three coins, two if you push, and during Family Crest he is
     * one of the people who can be asked after Avan.
     *
     * The clue is gated the same way the gem trader's is -- after Caleb has
     * handed his piece over and before Avan has been found -- because that is
     * the window in which the player has a reason to be asking. Family Crest
     * itself never associates npc 71, so the branch has to live here; an npc
     * handler beats quest dispatch, and a quest that claimed him would take
     * the stall away from everybody not on the quest.
     */
    private void trader(Npc npc, Player player) {
        QuestManager q = player.getQuestManager();
        boolean clue = q.reached(Quests.FAMILY_CREST, "got-caleb")
                && !q.reached(Quests.FAMILY_CREST, "met-avan")
                && !q.completed(Quests.FAMILY_CREST);

        String[] options = clue
            ? new String[] { "How much are they?", "No. Silk doesn't suit me",
                             "I'm in search of a man named adam fitzharmon" }
            : new String[] { "How much are they?", "No. Silk doesn't suit me" };

        new Conversation(player, npc)
            .npc("Do you want to buy any fine silks?")
            .options(new Choice(options) {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        return;
                    }
                    if (option == 2) {
                        c.npc("I haven't seen him")
                         .npc("I'm sure if he's been to Al Kharid recently")
                         .npc("Someone around here will have seen him though");
                        return;
                    }
                    askPrice(c);
                }
            })
            .start();
    }

    private void askPrice(Conversation c) {
        c.npc(ASKING + " coins")
         .options(new Choice("No. That's too much for me", "OK, that sounds good") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     buy(c, ASKING, false);
                     return;
                 }
                 c.npc("Two coins and that's as low as I'll go")
                  .npc("I'm not selling it for less")
                  .npc("You'll probably go and sell it in Varrock for a profit anyway")
                  .options(new Choice("Two coins sounds good", "No, really. I don't want it") {
                      public void picked(int option, Conversation c) {
                          if (option == 1) {
                              c.npc("Ok, but that's the best price you're going to get");
                              return;
                          }
                          buy(c, HAGGLED, true);
                      }
                  });
             }
         }.says(1, "Ok, that sounds good"));
    }

    /**
     * @param always whether the purchase message prints even when the player
     *               cannot pay. It does on the haggled branch and does not on
     *               the full price one -- Jagex's inconsistency, not ours.
     */
    private void buy(Conversation c, final int price, final boolean always) {
        if (always) {
            c.message("You buy some silk for " + price + " coins");
        }
        if (c.getPlayer().getInventory().countId(COINS) < price) {
            c.player("Oh dear. I don't have enough money");
            return;
        }
        if (!always) {
            c.message("You buy some silk for " + price + " coins");
        }
        c.take(COINS, price).give(new InvItem(SILK, 1));
    }

    // ---------------------------------------------------------- merchant --

    private void merchant(Npc npc, Player player) {
        if (player.getInventory().countId(SILK) < 1) {
            new Conversation(player, npc)
                .npc("I buy silk")
                .npc("If you get any silk to sell bring it here")
                .start();
            return;
        }
        new Conversation(player, npc)
            .player("Hello I have some fine silk from Al Kharid to sell to you")
            .npc("Ah I may be intersted in that")
            .npc("What sort of price were you looking at per piece of silk?")
            .options(new Choice("20 coins", "80 coins", "120 coins", "200 coins") {
                public void picked(int option, Conversation c) {
                    switch (option) {
                        case 0:
                            c.npc("Ok that suits me");
                            sell(c, 20);
                            return;
                        case 1:
                            counter(c, "80 coins that's a bit steep", "How about 40 coins",
                                    "Ok 40 sounds good", 40,
                                    "50 and that's my final price",
                                    "50 and that's my final price", 50, "Done");
                            return;
                        case 2:
                            counter(c, "You'll never get that much for it",
                                    "I'll be generous and give you 50 for it",
                                    "Ok I guess 50 will do", 50,
                                    "I'll give it to you for 60",
                                    "I'll give it you for 60", 60, null);
                            return;
                        default:
                            c.npc("Don't be ridiculous that is far to much")
                             .npc("You insult me with that price");
                    }
                }
            }.says(0, "20 coinsa"))
            .start();
    }

    /**
     * The two middle asking prices behave identically: he objects, names his
     * own figure, and the player either takes it or pushes ten coins higher
     * and gets it. Only the wording differs, and on the 120 branch he grumbles
     * about it in two lines where on the 80 branch he says "Done".
     *
     * The push option is the one place the menu and the line come apart: on
     * the 120 branch the menu offers "I'll give it to you for 60" and the
     * player says "I'll give it you for 60", so both wordings are passed in.
     *
     * @param accept  what he says on the push, or null for the two grumbling
     *                lines the 120 branch uses instead.
     */
    private void counter(Conversation c, String objection, String offer,
                         String takeIt, final int offered,
                         String pushIt, String pushSpoken, final int pushed,
                         final String accept) {
        c.npc(objection)
         .npc(offer)
         .options(new Choice(takeIt, pushIt, "No that is not enough") {
             public void picked(int option, Conversation c) {
                 if (option == 2) {
                     return;
                 }
                 if (option == 0) {
                     sell(c, offered);
                     return;
                 }
                 if (accept != null) {
                     c.npc(accept);
                 } else {
                     c.npc("You drive a hard bargain")
                      .npc("but I guess that will have to do");
                 }
                 sell(c, pushed);
             }
         }.says(1, pushSpoken));
    }

    /** Sells everything the player is carrying at the price just agreed. */
    private void sell(Conversation c, final int each) {
        c.then(new Effect() {
            public void run(Conversation c) {
                Player p = c.getPlayer();
                int held = p.getInventory().countId(SILK);
                if (held < 1) {
                    return;
                }
                p.getInventory().remove(SILK, held);
                p.getInventory().add(new InvItem(COINS, each * held));
                p.getActionSender().sendInventory();
            }
        });
    }
}
