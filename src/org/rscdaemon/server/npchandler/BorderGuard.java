package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * The two Border Guards on the Al Kharid toll gate.
 *
 * The gate itself, scenery 180 at (92,649), already swung open for anybody who
 * clicked it, so the toll -- the one thing the gate is for -- was not collected
 * and Prince Ali rescue's reward did not exist. Both halves are here: talking to
 * a guard and clicking the gate lead to the same conversation, which is how the
 * transcript reads it ("Can I come through this gate?" is the player's opening
 * line either way).
 *
 * Nothing is persisted. Paying opens the gate then and there and the player
 * walks through; coming back means paying again. Finishing Prince Ali rescue is
 * what makes it free, and that the quest already remembers.
 *
 * The toll is charged in both directions. The guards stand on the Lumbridge side
 * and the transcript does not distinguish, so this is the plain reading.
 *
 * Dialogue is Jagex's, from the recorded transcript.
 */
public class BorderGuard implements NpcHandler {

    private static final World world = World.getWorld();

    private static final int COINS = 10;
    private static final int TOLL = 10;

    /** The toll gate. Scenery, not a wall object -- see ObjectAction. */
    public static final int GATE = 180;
    public static final int GATE_X = 92;
    public static final int GATE_Y = 649;
    /** The gate's open frame, the way doorframe 11 is a door's. */
    private static final int GATE_OPEN = 181;

    public void handleNpc(Npc npc, Player player) throws Exception {
        askToPass(player, npc);
    }

    /**
     * Whether this player is a friend of Al Kharid and pays nothing.
     */
    public static boolean passesFree(Player player) {
        return player.getQuestManager().completed(Quests.PRINCE_ALI_RESCUE);
    }

    /** Either of the two guards, whichever is nearer the gate right now. */
    public static Npc guard() {
        Npc npc = world.getNpc(162, GATE_X - 3, GATE_X + 3, GATE_Y - 3, GATE_Y + 3);
        if (npc == null) {
            npc = world.getNpc(161, GATE_X - 3, GATE_X + 3, GATE_Y - 3, GATE_Y + 3);
        }
        return npc;
    }

    /**
     * The toll conversation. Called from the guard and from the gate.
     */
    public static void askToPass(Player player, Npc npc) {
        Conversation c = new Conversation(player, npc)
            .player("Can I come through this gate?");

        if (passesFree(player)) {
            c.npc("You may pass for free, you are a friend of Al Kharid");
            c.start();
            return;
        }

        c.npc("You must pay a toll of " + TOLL + " gold coins to pass")
         .options(new Choice("Yes ok",
                             "Who does my money go to?",
                             "No thankyou, I'll walk round") {
             public void picked(int option, Conversation c) {
                 if (option == 1) {
                     c.npc("The money goes to the city of Al Kharid");
                     return;
                 }
                 if (option == 2) {
                     c.npc("Ok suit yourself");
                     return;
                 }
                 pay(c);
             }
         });
        c.start();
    }

    /**
     * Answering "Yes ok". The purse is counted here rather than in an Effect
     * because the player has only just clicked and the transcript's answer to an
     * empty one is a line of the player's own, not a server message.
     */
    private static void pay(Conversation c) {
        if (c.getPlayer().getInventory().countId(COINS) < TOLL) {
            c.player("Oh dear I don't actually seem to have enough money");
            return;
        }
        c.then(new Effect() {
             public void run(Conversation c) {
                 Player p = c.getPlayer();
                 p.getInventory().remove(COINS, TOLL);
                 p.getActionSender().sendInventory();
             }
         })
         .npc("You may pass")
         .then(new Effect() {
             public void run(Conversation c) {
                 openGate(c.getPlayer());
             }
         });
    }

    /**
     * Swing the gate and put the player on the far side of it.
     *
     * The gate faces north/south, so it stands between x 91 and x 92; whichever
     * of those the player is on, they come out on the other.
     */
    public static void openGate(Player player) {
        GameObject gate = world.getTile(GATE_X, GATE_Y).getGameObject();
        if (gate != null && gate.getID() == GATE) {
            player.getActionSender().sendSound("opendoor");
            world.registerGameObject(new GameObject(gate.getLocation(), GATE_OPEN,
                gate.getDirection(), gate.getType()));
            world.delayedSpawnObject(gate.getLoc(), 1000);
        }
        player.teleport(player.getX() <= GATE_X - 1 ? GATE_X : GATE_X - 1, GATE_Y, false);
    }
}
