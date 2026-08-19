package org.rscdaemon.server.model;

/**
 * The windmills.
 *
 * There are four of them in the world and not one of them worked, which means
 * there was no way to make a pot of flour anywhere in the game -- so Cook's
 * assistant could not be finished, and neither could bread, pies or cake be
 * baked. The chain is three clicks:
 *
 *   1. use grain on the hopper       -- the mill is loaded
 *   2. operate the hopper            -- the grindstones turn
 *   3. use an empty pot on the chute -- a pot of flour
 *
 * The loaded and ground states are session flags on the player rather than on
 * the mill. A real mill is world scenery and Jagex's was too, but a shared mill
 * lets one player grind and another collect, and there is nothing to gain from
 * reproducing that. Each player mills their own grain.
 *
 * Object ids and the examine text they come with ("You put grain in here",
 * "Flour comes out here") are Jagex's. The two progress messages are ours; the
 * transcript for the hopper records that both actions say something but not what.
 */
public final class Mill {

    /** The four hoppers. Same object, four ids, one per mill. */
    private static final int[] HOPPERS = { 52, 173, 246, 343 };
    /** The chute the flour falls out of, on the floor below. One id, four mills. */
    private static final int CHUTE = 53;

    private static final int GRAIN = 29;
    private static final int POT = 135;
    private static final int POT_OF_FLOUR = 136;

    /** Grain is in the hopper, waiting to be ground. */
    private static final String LOADED = "mill.loaded";
    /** It has been ground and is waiting in the chute. */
    private static final String GROUND = "mill.ground";

    private Mill() {
    }

    public static boolean isHopper(int id) {
        for (int hopper : HOPPERS) {
            if (hopper == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * An item used on a mill. True if this was a mill interaction at all, in
     * which case the caller must not go on to its own handling.
     */
    public static boolean handle(Player player, GameObject object, InvItem item) {
        if (isHopper(object.getID())) {
            putIn(player, item);
            return true;
        }
        if (object.getID() == CHUTE) {
            collect(player, item);
            return true;
        }
        return false;
    }

    /** Something used on the hopper. Only grain interests it. */
    private static void putIn(Player player, InvItem item) {
        if (item.getID() != GRAIN) {
            player.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
            return;
        }
        if (player.getFlag(LOADED) != 0) {
            player.getActionSender().sendMessage("@gry@ There is already grain in the hopper");
            return;
        }
        if (player.getInventory().remove(item) <= -1) {
            return;
        }
        player.setFlag(LOADED, 1);
        player.getActionSender().sendInventory();
        player.getActionSender().sendMessage("@pnk@ You put the grain in the hopper");
    }

    /**
     * Operating the hopper. Called from ObjectAction, since "operate" is the
     * hopper's own command and takes no item.
     */
    public static void operate(Player player) {
        if (player.getFlag(LOADED) == 0) {
            player.getActionSender().sendMessage("@gry@ You need to put grain in the hopper first");
            return;
        }
        player.setFlag(LOADED, 0);
        player.setFlag(GROUND, 1);
        player.getActionSender().sendSound("mechanical");
        player.getActionSender().sendMessage("@pnk@ You operate the hopper. The grain is ground into flour");
        player.getActionSender().sendMessage("@gry@ The flour lands in the bin at the bottom of the chute");
    }

    /** Something used on the chute. Only an empty pot interests it. */
    private static void collect(Player player, InvItem item) {
        if (item.getID() != POT) {
            player.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
            return;
        }
        if (player.getFlag(GROUND) == 0) {
            player.getActionSender().sendMessage("@gry@ There is no flour in the bin");
            return;
        }
        if (player.getInventory().remove(item) <= -1) {
            return;
        }
        player.setFlag(GROUND, 0);
        player.getInventory().add(new InvItem(POT_OF_FLOUR, 1));
        player.getActionSender().sendInventory();
        player.getActionSender().sendMessage("@pnk@ You fill the pot with flour");
    }
}
