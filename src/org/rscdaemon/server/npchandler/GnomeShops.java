package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * The four shops on the second floor of the Grand Tree: Heckel funch and Hudo
 * glenfad's grocery stores, the Blurberry barman's cocktail bar and the Gnome
 * waiters' restaurant. None of the four npcs had anything wired to them
 * before this, and none of the four shops existed in Shops.xml -- which is
 * most of why gnome cooking could not be started, since Hudo is the only
 * source of gianne dough and gnome spice in the game.
 *
 * These do not go through ShopKeeper for two reasons. The first is that
 * ShopKeeper finds a shop by the tile the npc is standing on, and up here
 * that does not work: Heckel funch roams (680,1397)-(690,1407) and the
 * Blurberry barman roams (685,1398)-(697,1409), so whichever shop was listed
 * first would answer for both, and the Gnome waiter's box sits entirely
 * inside Aluft Gianne's. They are looked up by name instead -- see
 * World.getShop(String). The second is that ShopKeeper sends exactly one
 * greeting line and all four of these open with two or three, and the yes/no
 * options are not in the same order for the groceries as for the bar.
 *
 * Every line below is verbatim from the wiki's transcripts, misspellings and
 * missing apostrophes included ("can i get you drink?", "no thankyou",
 * "if your partial to a drink").
 */
public class GnomeShops implements NpcHandler {

    public static final World world = World.getWorld();

    public static final int HECKEL_FUNCH = 535;
    public static final int HUDO_GLENFAD = 537;
    public static final int BLURBERRY_BARMAN = 580;
    public static final int GNOME_WAITER = 581;

    public void handleNpc(Npc npc, Player player) throws Exception {
        switch (npc.getID()) {
            case HECKEL_FUNCH:
                heckel(npc, player);
                return;
            case HUDO_GLENFAD:
                hudo(npc, player);
                return;
            case BLURBERRY_BARMAN:
                barman(npc, player);
                return;
            case GNOME_WAITER:
                waiter(npc, player);
                return;
        }
    }

    /** Opens a shop by name once the dialogue has run its course. */
    private static Effect open(final String name) {
        return new Effect() {
            public void run(Conversation c) {
                Shop shop = world.getShop(name);
                if (shop == null) {
                    return;
                }
                c.getPlayer().setAccessingShop(shop);
                c.getPlayer().getActionSender().showShop(shop);
            }
        };
    }

    private void heckel(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("hello there")
            .npc("good day to you my friend ..and a beautiful one at that")
            .npc("would you like some groceries? i have all sorts")
            .npc("alcohol also, if your partial to a drink")
            .options(new Choice("no thank you", "i'll have a look") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("ahh well, all the best to you");
                        return;
                    }
                    c.npc("there's a good human");
                    c.then(open("Heckel funch's grocery store"));
                }
            })
            .start();
    }

    private void hudo(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("hello there")
            .npc("good day ..and a beautiful one at that")
            .npc("would you like some groceries? i have a large selection")
            .options(new Choice("no thankyou", "i'll have a look") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("ahh well, all the best to you");
                        return;
                    }
                    c.npc("great stuff");
                    c.then(open("Hudo glenfad's grocery store"));
                }
            })
            .start();
    }

    private void barman(Npc npc, Player player) {
        new Conversation(player, npc)
            .npc("good day to you")
            .npc("can i get you drink?")
            .options(new Choice("what do you have?", "no thanks") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("ok, take it easy");
                        return;
                    }
                    c.npc("take a look");
                    c.then(open("Blurberry's cocktail bar"));
                }
            })
            .start();
    }

    private void waiter(Npc npc, Player player) {
        new Conversation(player, npc)
            .player("hello")
            .npc("good afternoon")
            .npc("can i tempt you with our new menu?")
            .options(new Choice("i'll take a look", "not really") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("ok then, enjoy your stay");
                        return;
                    }
                    c.npc("i hope you like what you see");
                    c.then(open("Giannes tree gnome cuisine"));
                }
            })
            .start();
    }
}
