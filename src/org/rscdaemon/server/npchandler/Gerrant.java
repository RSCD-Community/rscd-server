package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.QuestManager;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Gerrant, who keeps the fishing shop in Port Sarim.
 *
 * He is a shopkeeper for everybody and the source of the blamish snail slime
 * for anybody on Hero's quest, and those are two different handlers -- the
 * generic ShopKeeper cannot grow a quest branch without growing one for all
 * sixty-odd shops, and a quest that claimed him would take the shop away from
 * every player not on the quest. So he lives here, the way King Arthur does,
 * and asks the quest where the player has got to rather than deciding for
 * itself. He was removed from ShopKeeper's id list when this was written.
 *
 * The shop itself is still the one in Shops.xml, found by location exactly as
 * ShopKeeper finds it, so stock, prices and restocking are untouched.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class Gerrant implements NpcHandler {

    public static final World world = World.getWorld();

    private static final int SLIME = 587, OIL = 588, OILY_ROD = 589;

    public void handleNpc(Npc npc, Player player) throws Exception {
        final Shop shop = world.getShop(npc);
        if (shop == null) {
            return;
        }
        QuestManager q = player.getQuestManager();
        boolean onQuest = q.reached(Quests.HEROS_QUEST, "started")
                && !q.completed(Quests.HEROS_QUEST);

        String[] options = shop.getOptions();
        if (onQuest) {
            String[] longer = new String[options.length + 1];
            System.arraycopy(options, 0, longer, 0, options.length);
            longer[options.length] = "I want to find out how to catch a lava eel";
            options = longer;
        }
        final int lavaEel = onQuest ? options.length - 1 : -1;
        final int browse = 0;

        new Conversation(player, npc)
            .npc(shop.getGreeting())
            .npc("We'll also buy anything you catch off you")
            .options(new Choice(options) {
                public void picked(int option, Conversation c) {
                    if (option == browse) {
                        c.then(new Effect() {
                            public void run(Conversation c) {
                                Player p = c.getPlayer();
                                c.stop();
                                p.setAccessingShop(shop);
                                p.getActionSender().showShop(shop);
                            }
                        });
                        return;
                    }
                    if (option == lavaEel) {
                        lavaEel(c);
                    }
                }
            })
            .start();
    }

    /**
     * How to catch a lava eel.
     *
     * A player who already has the slime, the oil or the finished rod is told
     * the method and nothing more; anyone else is handed a jar. Gerrant will
     * keep handing them out, which is how the real shop behaved and is the
     * whole of the herblaw trick built on it.
     */
    private void lavaEel(Conversation c) {
        c.npc("Lava eels eh?")
         .npc("That's a tricky one that is")
         .npc("I wouldn't even know where find them myself")
         .npc("Probably in some lava somewhere")
         .npc("You'll also need a lava proof fishing line")
         .npc("The method for this would be take an ordinary fishing rod")
         .npc("And cover it with fire proof blamish oil");
        if (holdsAny(c.getPlayer())) {
            return;
        }
        c.npc("Now I may have a jar of Blaimish snail slime")
         .npc("I wonder where I put it")
         .npc("Aha here it is")
         .give(new InvItem(SLIME, 1))
         .npc("You'll need to mix this with some of the Harralander herb and water");
    }

    private boolean holdsAny(Player player) {
        return player.getInventory().countId(SLIME) > 0
            || player.getInventory().countId(OIL) > 0
            || player.getInventory().countId(OILY_ROD) > 0;
    }
}
