package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * The doormen of the Zanaris faerie market, npc 221.
 *
 * Four of them stand around (117,3537) and between them they guard two doors,
 * (116,3537) and (117,3539), both plain id 67. Going either way through either
 * door costs a cut diamond -- which is what makes Fairy Lunderwin's hundred
 * coins a cabbage the joke it is, since getting to her and back out again
 * costs two diamonds.
 *
 * Talking to a doorman does nothing at all, and that is not a gap: the wiki
 * says so outright ("Doormen will not respond when their talk-to option is
 * selected, instead, they must be spoken to by clicking on one of the doors
 * they guard"). So 221 is deliberately in no handler list, and the whole of
 * this runs from WallObjectAction's case 67 instead.
 *
 * The RSCD handler that used to sit on 221 was called ArenaTele and teleported
 * the player to RSCD's own arena. It went with the rest of that content in the
 * August 2026 sweep and left the doors free to walk through, which is the
 * state this restores.
 */
public class Doorman {

    public static final World world = World.getWorld();

    public static final int DOORMAN = 221;
    private static final int DIAMOND = 161;

    /** The two doors, and the box the four doormen roam inside. */
    public static final int DOOR_ID = 67;
    private static final int WEST_X = 116, WEST_Y = 3537;
    private static final int NORTH_X = 117, NORTH_Y = 3539;
    private static final int MIN_X = 108, MAX_X = 118;
    private static final int MIN_Y = 3532, MAX_Y = 3542;

    /** Is this one of the two doors the doormen are paid to watch? */
    public static boolean guarded(GameObject door) {
        if (door.getID() != DOOR_ID) {
            return false;
        }
        return (door.getX() == WEST_X && door.getY() == WEST_Y)
            || (door.getX() == NORTH_X && door.getY() == NORTH_Y);
    }

    /**
     * The toll, asked at the door.
     *
     * @param through what to run once the diamond has been handed over. The
     *                caller owns the door itself -- opening it and stepping
     *                the player through is WallObjectAction's business, and
     *                it already knows how, so this only decides whether it
     *                happens.
     */
    public static void challenge(Player player, final Runnable through) {
        Npc doorman = world.getNpc(DOORMAN, MIN_X, MAX_X, MIN_Y, MAX_Y);
        if (doorman == null) {
            /* Ours, for a world that has lost all four spawns: the door is a
               toll gate, not a wall, so with nobody to pay it opens. Leaving
               it shut would trap anyone already inside the market. */
            through.run();
            return;
        }
        new Conversation(player, doorman)
            .npc("You cannot go through this door without paying the trading tax")
            .player("What do I need to pay?")
            .npc("One diamond")
            .options(new Choice("Okay", "A diamond, are you crazy?",
                                "I haven't brought my diamonds with me") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("Nope those are the rules");
                        return;
                    }
                    if (option == 2) {
                        return;
                    }
                    pay(c, through);
                }
            }.says(1, "A diamond?", "are you crazy?"))
            .start();
    }

    /**
     * "Okay", and then either the diamond or the admission that there isn't
     * one. The player says the same line here as the third menu entry, which
     * is Jagex's own doubling up -- the third entry exists so that somebody
     * who already knows they cannot pay does not have to say "Okay" first.
     */
    private static void pay(Conversation c, final Runnable through) {
        if (c.getPlayer().getInventory().countId(DIAMOND) < 1) {
            c.player("I haven't brought my diamonds with me");
            return;
        }
        c.take(DIAMOND, 1)
         .message("You give the doorman a diamond")
         .message("You go through the door")
         .then(new Effect() {
             public void run(Conversation c) {
                 c.stop();
                 through.run();
             }
         });
    }
}
