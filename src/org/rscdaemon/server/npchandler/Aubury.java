package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.EssenceMine;
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
 * Aubury, who keeps the rune shop in south east Varrock.
 *
 * A shopkeeper for everybody, Sedridor's correspondent for anyone on Rune
 * mysteries, and a mine teleporter for anyone who has finished it -- the
 * Gerrant arrangement exactly, and for Gerrant's reason: the generic
 * ShopKeeper cannot grow a quest branch, and a quest that claimed him would
 * take the shop away from everyone else. He was removed from ShopKeeper's id
 * list when this was written. The shop itself is still the one in Shops.xml.
 *
 * Quest state is asked for by name and moved by note() -- the ids of the
 * package and notes are the one thing shared with RuneMysteries.java.
 */
public class Aubury implements NpcHandler {

    public static final World world = World.getWorld();

    private static final int PACKAGE = 1306, NOTES = 1307;

    public void handleNpc(final Npc npc, Player player) throws Exception {
        final Shop shop = world.getShop(npc);
        if (shop == null) {
            return;
        }
        QuestManager q = player.getQuestManager();
        boolean carrying = q.reached(Quests.RUNE_MYSTERIES, "package")
                && player.getInventory().countId(PACKAGE) >= 1;
        boolean initiate = q.completed(Quests.RUNE_MYSTERIES);

        String[] options = shop.getOptions();
        int extra = (carrying ? 1 : 0) + (initiate ? 1 : 0);
        if (extra > 0) {
            String[] longer = new String[options.length + extra];
            System.arraycopy(options, 0, longer, 0, options.length);
            int at = options.length;
            if (carrying) {
                longer[at++] = "I have a package for you from Sedridor";
            }
            if (initiate) {
                longer[at++] = "Can you teleport me to the rune essence mine?";
            }
            options = longer;
        }
        final int deliver = carrying ? shop.getOptions().length : -1;
        final int teleport = initiate ? shop.getOptions().length + (carrying ? 1 : 0) : -1;
        final int browse = 0;

        new Conversation(player, npc)
            .npc(shop.getGreeting())
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
                    if (option == deliver) {
                        deliver(c);
                    } else if (option == teleport) {
                        c.npc("Senventior disthine molenko!")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 c.stop();
                                 EssenceMine.teleportIn(c.getPlayer(), npc.getID());
                             }
                         });
                    }
                }
            })
            .start();
    }

    private void deliver(Conversation c) {
        c.npc("From Sedridor? Give it here then")
         .npc("Well well. So the old theories were right after all")
         .npc("I must write down what I know of the essence at once")
         .npc("Take my notes back to him, and be quick about it")
         .then(new Effect() {
             public void run(Conversation c) {
                 Player p = c.getPlayer();
                 p.getInventory().remove(PACKAGE, 1);
                 p.getInventory().add(new InvItem(NOTES, 1));
                 p.getActionSender().sendInventory();
                 p.getQuestManager().note(Quests.RUNE_MYSTERIES, "aubury-swapped");
             }
         })
         .message("Aubury takes the package and hands you his research notes");
    }
}
