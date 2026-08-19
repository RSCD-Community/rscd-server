package org.rscdaemon.server.model;

import org.rscdaemon.server.quest.Quests;

/**
 * The rune essence mine -- the white island on plane 3 the teleporter
 * wizards send players to, and the only place essence comes from.
 *
 * The mine has no walking entrance on purpose: a wizard casts you in, and
 * the glowing portals cast you back out. Which wizard matters -- the mine
 * remembers who sent you and every portal returns you to that wizard's
 * side, so mining trips are routed by where you entered from. The memory
 * rides the quest-stage map under its own record id
 * ({@link Quests#ESSENCE_MINE_SENDER}), so it survives a logout: log out
 * in the mine and the way home is still the wizard who sent you. A player
 * with no recorded sender (teleported by an admin, say) is handed to
 * Aubury, whose shop is the closest thing the mine has to a front door.
 */
public class EssenceMine {

    public static final int SPIRE = 1206;
    public static final int PORTAL = 1207;

    /** Where every wizard's teleport lands: the centre of the X. */
    public static final int CENTER_X = 785;
    public static final int CENTER_Y = 3075;

    /**
     * Teleporter wizards and where their return portal drops you --
     * one tile beside the wizard, facing them.
     */
    private static final int[][] WIZARDS = {
        // npc id, return x, return y
        { 54,  102, 522  },   // Aubury, Varrock rune shop
        { 799, 220, 3515 },   // Sedridor, Wizards' Tower basement
        { 333, 546, 576  },   // Wizard Cromperty, East Ardougne
        { 800, 596, 754  },   // Distentor, Yanille wizards' guild
        { 590, 740, 3334 },   // Brimstail, the cave under the Gnome Stronghold
    };

    public static boolean isWizard(int npcId) {
        for (int[] w : WIZARDS) {
            if (w[0] == npcId) {
                return true;
            }
        }
        return false;
    }

    /** A wizard casts the player into the mine and is remembered as the way home. */
    public static void teleportIn(Player player, int wizardId) {
        player.setQuestStage(Quests.ESSENCE_MINE_SENDER, wizardId);
        player.teleport(CENTER_X, CENTER_Y, true);
    }

    /** A portal casts the player back to whoever sent them in. */
    public static void exitThroughPortal(Player player) {
        int sender = player.getQuestStage(Quests.ESSENCE_MINE_SENDER);
        int[] home = WIZARDS[0];
        for (int[] w : WIZARDS) {
            if (w[0] == sender) {
                home = w;
                break;
            }
        }
        player.teleport(home[1], home[2], true);
        player.getActionSender().sendMessage("You step through the portal");
    }
}
