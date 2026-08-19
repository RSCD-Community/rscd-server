package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Wyson the gardener, Falador park. The only source of woad leaves.
 *
 * He haggles: 5 or 10 coins is refused outright, 15 buys one leaf, and 20 buys
 * two -- "Here have some more you're a generous person". Woad leaves are what
 * Aggie needs for blue dye, so without him Goblin diplomacy cannot be finished.
 *
 * A handler rather than part of the quest for the same reason as Aggie: he is
 * also the man who catches you digging up his flowers during Pirate's treasure,
 * and an npc can only belong to one quest.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class Wyson implements NpcHandler {

    private static final int COINS = 10;
    private static final int WOAD_LEAF = 281;

    public void handleNpc(Npc npc, Player player) throws Exception {
        new Conversation(player, npc)
            .npc("I am the gardener round here")
            .npc("Do you have any gardening that needs doing?")
            .options(new Choice("I'm looking for woad leaves",
                                "Not right now thanks") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("Well luckily for you I may have some around here somewhere")
                     .player("Can I buy one please?")
                     .npc("How much are you willing to pay?");
                    haggle(c);
                }
            })
            .start();
    }

    private void haggle(Conversation c) {
        c.options(new Choice("How about 5 coins?",
                             "How about 10 coins?",
                             "How about 15 coins?",
                             "How about 20 coins?") {
            public void picked(int option, Conversation c) {
                if (option < 2) {
                    c.npc("No No thats far too little. Woad leaves are hard to get you know")
                     .npc("I used to have plenty but someone kept stealing them off me");
                    return;
                }
                final int price = option == 2 ? 15 : 20;
                final int leaves = option == 2 ? 1 : 2;
                c.npc(option == 2 ? "Mmmm Ok that sounds fair." : "Ok that's more than fair.")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         Player p = c.getPlayer();
                         if (p.getInventory().countId(COINS) < price) {
                             p.getActionSender().sendMessage("You don't have enough coins.");
                             c.stop();
                             return;
                         }
                         p.getInventory().remove(COINS, price);
                         p.getInventory().add(new InvItem(WOAD_LEAF, leaves));
                         p.getActionSender().sendInventory();
                     }
                 });
                if (leaves > 1) {
                    c.npc("Here have some more you're a generous person");
                }
            }
        });
    }
}
