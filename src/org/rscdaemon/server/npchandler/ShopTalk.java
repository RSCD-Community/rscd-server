package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Six shop owners whose recorded conversations do not fit through ShopKeeper.
 *
 *   Aemad (336)          East Ardougne Adventurers' Store
 *   Kortan (337)         the same shop, and word for word the same greeting
 *   Zenesha (331)        Zenesha's Plate Mail Top Shop
 *   gem merchant (330)   Gems Stall, East Ardougne market
 *   spice merchant (329) Spices Stall, the same market
 *   Chadwell (661)       West Ardougne General Store
 *
 * ShopKeeper sends exactly one greeting line, echoes whichever option was
 * picked back as the player's line, and then either opens the shop or stops.
 * Between them these six need four things it cannot do: a second greeting line
 * (Aemad, Kortan, the spice merchant), a reply on the refusal (Aemad's "Hmph",
 * Chadwell's "ok then"), a line after the acceptance and before the stock
 * appears (Zenesha's "Look at these fine samples then"), and a spoken line that
 * differs from the menu entry -- which every one of the six has, because Jagex
 * misspelled "interested" on four of them and clipped the wording on the other
 * two.
 *
 * The Shops.xml rows for these six still carry a greeting and an options list.
 * Nothing reads them any more; they are left in place because the file is
 * generated and the stock, prices and restock rates in the same rows are still
 * live. Where the two disagree the transcripts win: the adventurers' store row
 * has the generic "Can I help you at all?", Zenesha's has her two options the
 * wrong way round, and the spices row has both greeting lines run together.
 *
 * Every line below is verbatim from the wiki's transcripts. The misspellings
 * are Jagex's: "intersting", "intersted", "Lets have a look them then".
 */
public class ShopTalk implements NpcHandler {

    public static final World world = World.getWorld();

    public static final int SPICE_MERCHANT = 329;
    public static final int GEM_MERCHANT = 330;
    public static final int ZENESHA = 331;
    public static final int AEMAD = 336;
    public static final int KORTAN = 337;
    public static final int CHADWELL = 661;

    public void handleNpc(Npc npc, Player player) throws Exception {
        final Shop shop = world.getShop(npc);
        if (shop == null) {
            return;
        }
        switch (npc.getID()) {
            case AEMAD:
            case KORTAN:
                adventurers(npc, player, shop);
                return;
            case ZENESHA:
                zenesha(npc, player, shop);
                return;
            case GEM_MERCHANT:
                gems(npc, player, shop);
                return;
            case SPICE_MERCHANT:
                spices(npc, player, shop);
                return;
            case CHADWELL:
                chadwell(npc, player, shop);
                return;
        }
    }

    /** Puts the stock in front of the player once the talking is done. */
    private static Effect open(final Shop shop) {
        return new Effect() {
            public void run(Conversation c) {
                Player p = c.getPlayer();
                p.setAccessingShop(shop);
                p.getActionSender().showShop(shop);
            }
        };
    }

    /**
     * Aemad and Kortan share the adventurers' store and share every line of
     * this, down to the "Hmph" when you turn them down.
     */
    private void adventurers(Npc npc, Player player, final Shop shop) {
        new Conversation(player, npc)
            .npc("Hello you look like a bold adventurer")
            .npc("You've come to the right place for adventurer's equipment")
            .options(new Choice("Oh that sounds intersting",
                                "No I've come to the wrong place") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("Hmph");
                        return;
                    }
                    c.then(open(shop));
                }
            }.says(0, "Oh that sounds interesting"))
            .start();
    }

    private void zenesha(Npc npc, Player player, final Shop shop) {
        new Conversation(player, npc)
            .npc("hello I sell plate mail tops")
            .options(new Choice("I'm not intersted", "I may be intersted") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        return;
                    }
                    c.npc("Look at these fine samples then")
                     .then(open(shop));
                }
            }.says(0, "I'm not interested").says(1, "I may be interested"))
            .start();
    }

    private void gems(Npc npc, Player player, final Shop shop) {
        new Conversation(player, npc)
            .npc("Here, look at my lovely gems")
            .options(new Choice("Ok show them to me", "I'm not interested thankyou") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.then(open(shop));
                    }
                }
            }.says(1, "I'm not intersted thankyou"))
            .start();
    }

    private void spices(Npc npc, Player player, final Shop shop) {
        new Conversation(player, npc)
            .npc("Get your exotic spices here")
            .npc("rare very valuable spices here")
            .options(new Choice("Lets have a look them then",
                                "No thank you I'm not interested") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.then(open(shop));
                    }
                }
            }.says(0, "Lets have a look then").says(1, "No thank you"))
            .start();
    }

    /** The only one of the six the player greets first. */
    private void chadwell(Npc npc, Player player, final Shop shop) {
        new Conversation(player, npc)
            .player("hello there")
            .npc("good day, what can i get you?")
            .options(new Choice("nothing thanks, just browsing",
                                "lets see what you've got") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("ok then");
                        return;
                    }
                    c.then(open(shop));
                }
            }.says(0, "nothing thanks").says(1, "let's see what you've got then"))
            .start();
    }
}
