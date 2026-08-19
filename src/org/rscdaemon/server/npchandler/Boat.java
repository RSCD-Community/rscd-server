package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * The ferries.
 *
 * This replaces an inherited version that was not Jagex's: it greeted every
 * sailor with "G'day sailor, where would you like to go?" and offered a menu of
 * seven destinations spanning the whole map, free of charge and with no customs
 * check. Convenient, but it dissolved a piece of the world -- the whole point of
 * Pirate's treasure is that rum cannot be carried past the customs officer, and
 * a free ferry to anywhere makes half the map's geography decorative.
 *
 * Restored to what the transcripts record. A sailor runs one route and one only,
 * asks 30 gold for it, and the two customs officers search the player before
 * letting them aboard, confiscating any Karamja rum they find.
 *
 * Arhein of Catherby (280) used to be in this handler and is not a sailor at
 * all. He is a general-store merchant who ships goods up and down the coast and
 * refuses point blank to carry passengers -- that refusal is a step of Merlin's
 * crystal, which is why the player has to stow away in his hold instead. He now
 * belongs to that quest.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class Boat implements NpcHandler {

    public static final World world = World.getWorld();

    private static final int COINS = 10;
    private static final int KARAMJA_RUM = 318;

    /** Every ferry in the game charges the same. */
    private static final int FARE = 30;

    /**
     * One route per sailor: who they are, where they set you down, and what they
     * call the place. Both customs officers search first -- that is the whole
     * difference between them and a sailor.
     */
    private static class Route {
        final int npc;
        final String destination;
        final Point arrival;
        final boolean customs;

        Route(int npc, String destination, Point arrival, boolean customs) {
            this.npc = npc;
            this.destination = destination;
            this.arrival = arrival;
            this.customs = customs;
        }
    }

    private static final Route[] ROUTES = new Route[]{
        /* Port Sarim docks. Three men, one crossing. */
        new Route(166, "Karamja", Point.location(324, 713), false),   /* Captain Tobias */
        new Route(170, "Karamja", Point.location(324, 713), false),   /* Seaman Lorris */
        new Route(171, "Karamja", Point.location(324, 713), false),   /* Seaman Thresnor */
        /* Musa Point, going home. Asgarnia bans the import of spirits. */
        new Route(163, "Port Sarim", Point.location(269, 649), true), /* Customs Officer */
        /* Ardougne to Brimhaven and back. Kandarin bans them too. */
        new Route(316, "Karamja", Point.location(467, 649), false),   /* Captain Barnaby */
        new Route(317, "Ardougne", Point.location(538, 616), true)    /* Customs Official */
    };

    public void handleNpc(Npc npc, Player player) throws Exception {
        Route route = null;
        for (Route r : ROUTES) {
            if (r.npc == npc.getID()) {
                route = r;
                break;
            }
        }
        if (route == null) {
            return;
        }
        if (route.customs) {
            customs(npc, player, route);
        } else {
            sailor(npc, player, route);
        }
    }

    // ------------------------------------------------------------- sailors --

    private void sailor(Npc npc, Player player, final Route route) {
        Conversation c = new Conversation(player, npc);
        c.npc("Do you want to go on a trip to " + route.destination + "?")
         .npc("The trip will cost you " + FARE + " gold")
         .options(new Choice("Yes please", "No thankyou", "I'd rather go to Crandor Isle") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     return;
                 }
                 if (option == 2) {
                     // Crandor is Dragon slayer's business, and no sailor alive
                     // will take anyone there.
                     c.npc("No I need to stay alive")
                      .npc("I have a wife and family to support");
                     return;
                 }
                 pay(c, route);
             }
         });
        c.start();
    }

    // ------------------------------------------------------------- customs --

    private void customs(Npc npc, Player player, final Route route) {
        final String banned = route.npc == 163 ? "Asgarnia" : "Kandarin";
        Conversation c = new Conversation(player, npc);
        c.player("Can I board this ship?")
         .npc("You need to be searched before you can board")
         .options(new Choice("Search away I have nothing to hide",
                             "You're not putting your hands on my things",
                             "Why?",
                             "Does Karamja have any unusual customs then?") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("You're not getting on this ship then");
                     return;
                 }
                 if (option == 2) {
                     c.npc("Because " + banned + " has banned the import of intoxicating spirits");
                     return;
                 }
                 if (option == 3) {
                     c.npc("I'm not that sort of customs officer");
                     return;
                 }
                 search(c, route);
             }
         });
        c.start();
    }

    /**
     * The search itself.
     *
     * Rum found is rum lost -- which is exactly why Pirate's treasure has the
     * player post it home inside a crate of bananas instead of carrying it.
     */
    private void search(Conversation c, final Route route) {
        Player p = c.getPlayer();
        if (p.getInventory().countId(KARAMJA_RUM) > 0) {
            c.npc("Aha trying to smuggle rum are we?")
             .then(new Effect() {
                 public void run(Conversation c) {
                     Player p = c.getPlayer();
                     p.getInventory().remove(KARAMJA_RUM, p.getInventory().countId(KARAMJA_RUM));
                     p.getActionSender().sendInventory();
                     // "confiscates", from the transcript, not "takes" -- and
                     // the two of them are separate npcs with separate
                     // recorded wordings, Customs Officer at Port Sarim and
                     // Customs Official on Karamja, so the name comes off
                     // whichever one is actually doing the searching.
                     p.getActionSender().sendMessage("The "
                         + c.getNpc().getDef().getName().toLowerCase()
                         + " confiscates your rum");
                 }
             });
        } else {
            c.npc("Well you've got some odd stuff, but it's all legal");
        }
        c.npc("Now you need to pay a boarding charge of " + FARE + " gold")
         .options(new Choice("Ok", "Oh, I'll not bother then") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     return;
                 }
                 pay(c, route);
             }
         });
    }

    // -------------------------------------------------------------- voyage --

    private void pay(Conversation c, final Route route) {
        final Player p = c.getPlayer();
        if (p.getInventory().countId(COINS) < FARE) {
            c.player("Oh dear I don't seem to have enough money");
            return;
        }
        c.then(new Effect() {
            public void run(Conversation c) {
                Player p = c.getPlayer();
                // Checked again here: the fare is taken a line later than it was
                // tested, and a player can drop coins in between.
                if (p.getInventory().countId(COINS) < FARE) {
                    p.getActionSender().sendMessage("You don't have enough coins");
                    c.stop();
                    return;
                }
                p.getInventory().remove(COINS, FARE);
                p.getActionSender().sendInventory();
                p.getActionSender().sendMessage("You board the ship");
            }
        })
         .message("You pay " + FARE + " coins")
         .then(new Effect() {
             public void run(Conversation c) {
                 Player p = c.getPlayer();
                 p.teleport(route.arrival.getX(), route.arrival.getY(), false);
                 p.getActionSender().sendMessage("The ship arrives at " + route.destination);
             }
         });
    }
}
