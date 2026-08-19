package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Siegfried Erkle, npc 779, who runs the shop on the top floor of the Legends'
 * Guild and will not open it to anyone who has not finished Legend's quest.
 *
 * He was registered to ShopKeeper, which is generic and has no gate of any
 * kind, so the half dragon square shield and the cape of legends were on sale
 * to anybody who could climb the stairs. He needs a handler of his own for two
 * reasons beyond the gate: his greeting is two lines where a shop's greeting is
 * one, and turning him down gets an answer, which the generic menu cannot do.
 *
 * A handler rather than a quest step, and deliberately: Legend's quest cannot
 * have him. Associating an npc with a quest takes it away from every other
 * quest and from every handler, and Siegfried is not part of the quest at all
 * -- he is a shop that happens to read the quest's answer. quests/LegendsQuest
 * records this as a gap; this closes it.
 *
 * Fionella (788), on the first floor, has no such gate and stays on ShopKeeper.
 *
 * Dialogue is Jagex's, from the recorded transcript. The refusal's "rightfull"
 * is Jagex's spelling and is kept.
 */
public class SiegfriedErkle implements NpcHandler {

    public static final World world = World.getWorld();

    public void handleNpc(final Npc npc, Player player) throws Exception {
        if (!player.getQuestManager().completed(Quests.LEGENDS_QUEST)) {
            /* Four lines and then nothing: no menu, no shop. He does not tell
               you how to become a member, which is the point -- if you have to
               ask, the answer is not for you. */
            new Conversation(player, npc)
                .npc("I'm sorry but the services of this shop are only for")
                .npc("the pleasure of those who are rightfull members of the")
                .npc("Legends Guild. I would get into serious trouble if I sold")
                .npc("a non-member an item from this store.")
                .start();
            return;
        }
        Conversation c = new Conversation(player, npc)
            .npc("Hello there and welcome to the shop of useful items.")
            .npc("Can I help you at all?");
        c.options(new Choice("Yes please, what are you selling?", "No thanks") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    c.npc("Take a look");
                    openShop(c, npc);
                    return;
                }
                c.npc("Ok, well, if you change your mind, do pop back.");
            }
        });
        c.start();
    }

    /**
     * Hand the player over to the shop screen, which is the tail of
     * ShopKeeper.handleNpc. The conversation has to be closed first: it holds
     * the player busy and the npc blocked for as long as it runs, and the shop
     * screen is not a conversation step.
     *
     * The stock, the prices and the restock rate are in Shops.xml against this
     * corner of the guild's top floor, the same as every other shop. Only the
     * door to it is here. The greeting and the two options in that entry are
     * corrected to the transcript but are not read for this npc -- the lines
     * above are what he says, because there are more of them than a shop entry
     * can hold.
     */
    private void openShop(Conversation c, final Npc npc) {
        c.then(new Effect() {
            public void run(Conversation c) {
                Player p = c.getPlayer();
                c.stop();
                Shop shop = world.getShop(npc);
                if (shop == null) {
                    return;
                }
                p.setAccessingShop(shop);
                p.getActionSender().showShop(shop);
            }
        });
    }
}
