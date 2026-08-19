package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

/**
 * The Party Cannons: one per floor of the Seers' Village party hall, standing
 * in the middle of the room on the multicannon's model (object 1192).
 *
 * Anyone loads them, any time -- use an item on a cannon and your whole
 * carried stack of that item goes in. The cannon then runs the party by
 * itself: every few seconds it lobs one thing from its belly onto a random
 * open tile of its own floor, where anybody may pick it up. Non-stackables go
 * in as separate shots so a load of thirty lobsters rains thirty times;
 * a stack of coins lands as the one stack it was.
 *
 * Deliberately decoupled from the schedule (see PartySchedule): a booking
 * buys announcements and nothing else, so the cannon works exactly the same
 * at an unannounced private party.
 *
 * Untradeable items are refused -- the cannon makes anything it swallows
 * everyone's, and quest kit that cannot change hands by trade should not
 * change hands out of a cannon either.
 *
 * Loaded items live in memory only. A restart eats whatever was in the barrel,
 * the same way it eats anything mid-flight anywhere else; the trickle is
 * seconds long, so the window is tiny.
 */
public final class PartyCannon {

    public static final int OBJECT = 1192;

    /** One shot every this many milliseconds while loaded. */
    private static final int FIRE_INTERVAL = 4000;

    /** The two floors' open boxes; upstairs is the same box a plane up. */
    private static final int MIN_X = 491, MAX_X = 499;
    private static final int MIN_Y = 464, MAX_Y = 470;
    private static final int UPSTAIRS = 944;

    /** Index 0 = ground floor, 1 = upstairs. */
    private static final LinkedList<InvItem>[] loads = newLoads();

    @SuppressWarnings("unchecked")
    private static LinkedList<InvItem>[] newLoads() {
        LinkedList<InvItem>[] l = new LinkedList[2];
        l[0] = new LinkedList<InvItem>();
        l[1] = new LinkedList<InvItem>();
        return l;
    }

    /** The landable tiles per floor, computed once the world data is up. */
    private static final List<Point>[] floorTiles = newTiles();

    @SuppressWarnings("unchecked")
    private static List<Point>[] newTiles() {
        return new List[2];
    }

    private PartyCannon() {
    }

    private static int floorOf(GameObject cannon) {
        return cannon.getY() >= UPSTAIRS ? 1 : 0;
    }

    /**
     * Every open tile of a floor: inside the box, nothing standing on it, and
     * neither the landscape byte nor the object byte marking it impassable
     * (0x10/0x20/0x40 -- the every-direction bits; see PathHandler). That
     * keeps the loot off the fireplace, the barrels, the staircase and the
     * cannon's own tile, so nothing ever lands where it cannot be reached.
     */
    private static synchronized List<Point> tiles(World world, int floor) {
        if (floorTiles[floor] == null) {
            List<Point> open = new ArrayList<Point>();
            int dy = floor == 1 ? UPSTAIRS : 0;
            for (int x = MIN_X; x <= MAX_X; x++) {
                for (int y = MIN_Y + dy; y <= MAX_Y + dy; y++) {
                    TileValue t = world.getTileValue(x, y);
                    if (((t.mapValue | t.objectValue) & 0x70) != 0) {
                        continue;
                    }
                    ActiveTile at = world.getTile(x, y);
                    if (at != null && at.hasGameObject()) {
                        continue;
                    }
                    open.add(Point.location(x, y));
                }
            }
            floorTiles[floor] = open;
        }
        return floorTiles[floor];
    }

    /** Use-item-on-cannon: swallow the player's whole stack of that item. */
    public static void load(Player player, GameObject cannon, InvItem item) {
        if (Formulae.isUntradeable(item.getID())) {
            player.getActionSender().sendMessage(
                "The cannon spits it straight back out - that can't change hands");
            return;
        }
        int floor = floorOf(cannon);
        String name = item.getDef().getName().toLowerCase();
        synchronized (loads) {
            if (item.getDef().isStackable()) {
                int amount = player.getInventory().countId(item.getID());
                player.getInventory().remove(item.getID(), amount);
                loads[floor].add(new InvItem(item.getID(), amount));
                player.getActionSender().sendMessage(
                    "You load " + amount + " x " + name + " into the party cannon");
            } else {
                int count = player.getInventory().countId(item.getID());
                for (int i = 0; i < count; i++) {
                    player.getInventory().remove(item.getID(), 1);
                    loads[floor].add(new InvItem(item.getID(), 1));
                }
                player.getActionSender().sendMessage(count == 1
                    ? "You load your " + name + " into the party cannon"
                    : "You load " + count + " x " + name + " into the party cannon");
            }
        }
        player.getActionSender().sendInventory();
        player.getActionSender().sendMessage("It rumbles happily");
    }

    /** The "fire" command: the cannon runs itself, so this just reports. */
    public static void inspect(Player player, GameObject cannon) {
        int waiting;
        synchronized (loads) {
            waiting = loads[floorOf(cannon)].size();
        }
        if (waiting == 0) {
            player.getActionSender().sendMessage(
                "The party cannon is empty - use items on it to load it");
        } else {
            player.getActionSender().sendMessage("The party cannon rumbles - "
                + waiting + (waiting == 1 ? " thing" : " things")
                + " waiting to be fired");
        }
    }

    /** The trickle. Installed from Server once the world is up. */
    public static void install(final World world) {
        world.getDelayedEventHandler().add(new DelayedEvent(null, FIRE_INTERVAL) {
            public void run() {
                this.updateLastRun();
                int fired = 0;
                for (int floor = 0; floor < 2; floor++) {
                    InvItem shot;
                    synchronized (loads) {
                        shot = loads[floor].poll();
                    }
                    if (shot == null) {
                        continue;
                    }
                    List<Point> open = tiles(world, floor);
                    if (open.isEmpty()) {
                        continue;
                    }
                    Point where = open.get(DataConversions.random(0, open.size() - 1));
                    world.registerItem(new Item(shot.getID(), where.getX(),
                        where.getY(), shot.getAmount(), null));
                    fired++;
                }
                /* The Party Animals tally: shots fired while a scheduled
                   party is running count toward its host's hiscore. Fired
                   outside any window they count for nobody, and nothing is
                   sent -- the hiscore ranks scheduled parties, not private
                   cannon use. */
                if (fired > 0 && PartySchedule.partyActive()) {
                    world.getServer().getLoginConnector().getActionSender().partyShots(fired);
                }
            }
        });
    }
}
