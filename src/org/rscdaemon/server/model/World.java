/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.rscdaemon.server.ClientUpdater;
import org.rscdaemon.server.DelayedEventHandler;
import org.rscdaemon.server.Server;
import org.rscdaemon.server.entityhandling.locs.GameObjectLoc;
import org.rscdaemon.server.entityhandling.locs.NPCLoc;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.io.WorldLoader;
import org.rscdaemon.server.model.ActiveTile;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.TileValue;
import org.rscdaemon.server.npchandler.NpcHandler;
import org.rscdaemon.server.npchandler.NpcHandlerDef;
import org.rscdaemon.server.states.CombatState;
import org.rscdaemon.server.util.EntityList;
import org.rscdaemon.server.util.Logger;
import org.rscdaemon.server.util.PersistenceManager;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public final class World {
    private static World worldInstance;
    public static final int MAX_WIDTH = 944;
    public static final int MAX_HEIGHT = 3776;
    public ActiveTile[][] tiles = new ActiveTile[944][3776];
    private TileValue[][] tileType = new TileValue[944][3776];
    private EntityList<Player> players = new EntityList(2000);
    // A starting size, not a limit -- EntityList grows now, so this only
    // decides how much is allocated before the first resize. NpcLoc.xml.gz
    // holds 3578 spawns and quests add more at run time; 8000 is enough that
    // a normal world never resizes at all.
    private EntityList<Npc> npcs = new EntityList(8000);
    private ClientUpdater clientUpdater;
    private DelayedEventHandler delayedEventHandler;
    private Server server;
    private List<Shop> shops = new ArrayList<Shop>();
    private TreeMap<Integer, NpcHandler> npcHandlers = new TreeMap();

    public static synchronized World getWorld() {
        if (worldInstance == null) {
            worldInstance = new World();
            try {
                WorldLoader wl = new WorldLoader();
                wl.loadWorld(worldInstance);
                worldInstance.loadNpcHandlers();
            }
            catch (Exception e) {
                Logger.error(e);
            }
        }
        return worldInstance;
    }

    public NpcHandler getNpcHandler(int npcID) {
        return this.npcHandlers.get(npcID);
    }

    private void loadNpcHandlers() {
        NpcHandlerDef[] handlerDefs;
        for (NpcHandlerDef handlerDef : handlerDefs = (NpcHandlerDef[])PersistenceManager.load("NpcHandlers.xml")) {
            try {
                String className = handlerDef.getClassName();
                Class<?> c = Class.forName(className);
                if (c == null) continue;
                NpcHandler handler = (NpcHandler)c.newInstance();
                for (int npcID : handlerDef.getAssociatedNpcs()) {
                    this.npcHandlers.put(npcID, handler);
                }
            }
            catch (Exception e) {
                Logger.error(e);
            }
        }
    }

    public void registerShop(Shop shop) {
        shop.setEquilibrium();
        this.shops.add(shop);
    }

    public List<Shop> getShops() {
        return this.shops;
    }

    public Shop getShop(Point location) {
        for (Shop shop : this.shops) {
            if (!shop.withinShop(location)) continue;
            return shop;
        }
        return null;
    }

    /**
     * Which shop does this npc run?
     *
     * Answered from the tile the shopkeeper was <em>placed</em> on, not the one
     * they happen to be standing on.
     *
     * Every handler used to ask getShop(npc.getLocation()), and that worked
     * only for as long as no shopkeeper's roam box was bigger than their
     * shop's rectangle. RSCD had narrowed a lot of roam boxes; restoring the
     * vanilla ones (task #87) quietly took twelve shopkeepers out of service.
     * The Lumbridge general store pair roam (131,638)-(138,645) while the shop
     * is only (133,641)-(136,642), so eight tiles in sixty-four answered and
     * the other fifty-six did nothing at all -- no message, no menu. The
     * Falador shop assistant was worse: forty-two tiles out of fifteen
     * hundred.
     *
     * The spawn tile is the better question anyway. A shop is a place; the
     * shopkeeper is the person who happens to work there, and where they have
     * wandered to since the world booted says nothing about which counter is
     * theirs. It also gives a stable answer for an npc id spawned at more than
     * one shop, which the roaming tile never could.
     */
    public Shop getShop(Npc npc) {
        if (npc.getLoc() != null) {
            Shop shop = getShop(Point.location(npc.getLoc().startX(), npc.getLoc().startY()));
            if (shop != null) {
                return shop;
            }
        }
        return getShop(npc.getLocation());
    }

    /*
     * The same lookup by name. Shops were only ever found by the tile the
     * shopkeeper was standing on, which works everywhere except the second
     * floor of the Grand Tree: Heckel funch and the Blurberry barman roam
     * boxes that overlap, and the Gnome waiter's box is inside Aluft Gianne's.
     * A handler that knows which npc it is talking to can ask for its shop by
     * name instead of by tile and get the right one every time.
     */
    public Shop getShop(String name) {
        for (Shop shop : this.shops) {
            if (!shop.getName().equalsIgnoreCase(name)) continue;
            return shop;
        }
        return null;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Server getServer() {
        return this.server;
    }

    public void setClientUpdater(ClientUpdater clientUpdater) {
        this.clientUpdater = clientUpdater;
    }

    public void setDelayedEventHandler(DelayedEventHandler delayedEventHandler) {
        this.delayedEventHandler = delayedEventHandler;
    }

    public ClientUpdater getClientUpdater() {
        return this.clientUpdater;
    }

    public DelayedEventHandler getDelayedEventHandler() {
        return this.delayedEventHandler;
    }

    /*
     * getTile returns null for anything outside the four planes, so both of
     * these have to be checked. They used not to be, and a teleport to a
     * y that does not exist threw an NPE out of the game thread and stopped
     * the world for everybody -- see DelayedEventHandler.doEvents, which is
     * now the backstop, and Point.inBlackHole, which was the caller.
     *
     * Refusing quietly is right for the old point (the entity was never on a
     * tile, so there is nothing to remove) and wrong to leave silent for the
     * new one: an entity that asked to be somewhere impossible is a bug in
     * whoever asked, and it should be in the log with a name attached.
     */
    public void setLocation(Entity entity, Point oldPoint, Point newPoint) {
        ActiveTile t;
        if (oldPoint != null && (t = this.getTile(oldPoint)) != null) {
            t.remove(entity);
        }
        if (newPoint != null) {
            t = this.getTile(newPoint);
            if (t == null) {
                Logger.error("Refusing to place " + entity.getClass().getSimpleName()
                        + " outside the world at " + newPoint.getX() + "," + newPoint.getY());
                return;
            }
            t.add(entity);
        }
    }

    public boolean withinWorld(int x, int y) {
        return x >= 0 && x < 944 && y >= 0 && y < 3776;
    }

    public TileValue getTileValue(int x, int y) {
        if (!this.withinWorld(x, y)) {
            return null;
        }
        TileValue t = this.tileType[x][y];
        if (t == null) {
            this.tileType[x][y] = t = new TileValue();
        }
        return t;
    }

    public ActiveTile getTile(int x, int y) {
        if (!this.withinWorld(x, y)) {
            return null;
        }
        ActiveTile t = this.tiles[x][y];
        if (t == null) {
            this.tiles[x][y] = t = new ActiveTile(x, y);
        }
        return t;
    }

    public ActiveTile getTile(Point p) {
        return this.getTile(p.getX(), p.getY());
    }

    /**
     * Whether this tile is inside a building, which the client decides from
     * the floor you are standing on rather than from the roof above you.
     * Set in WorldLoader; see the comment there.
     */
    public boolean isIndoors(int x, int y) {
        TileValue t = this.getTileValue(x, y);
        return t != null && (t.mapValue & 0x80) != 0;
    }

    public void delayedSpawnObject(final GameObjectLoc loc, int respawnTime) {
        this.delayedEventHandler.add(new SingleEvent(null, respawnTime){

            public void action() {
                World.this.registerGameObject(new GameObject(loc));
            }
        });
    }

    public void delayedRemoveObject(final GameObject object, int delay) {
        this.delayedEventHandler.add(new SingleEvent(null, delay){

            public void action() {
                ActiveTile tile = World.this.getTile(object.getLocation());
                if (tile.hasGameObject() && tile.getGameObject().equals(object)) {
                    World.this.unregisterGameObject(object);
                }
            }
        });
    }

    public void registerPlayer(Player p) {
        p.setInitialized();
        this.players.add(p);
    }

    /**
     * True when nothing can stand on this tile: bit 0x40 is set by
     * {@link org.rscdaemon.server.io.WorldLoader#loadSection} for any ground
     * overlay whose tile definition has a non-zero objectType -- water, lava
     * and the like.
     */
    private boolean isBlockedGround(int x, int y) {
        if (!this.withinWorld(x, y)) {
            return true;
        }
        return (this.getTileValue(x, y).mapValue & 0x40) != 0;
    }

    public void registerNpc(Npc n) {
        NPCLoc npc = n.getLoc();
        if (npc.startX < npc.minX || npc.startX > npc.maxX || npc.startY < npc.minY
                || npc.startY > npc.maxY || this.isBlockedGround(npc.startX, npc.startY)) {
            /* Jagex's start tile is not a hand-placed position, it is the
               centre of the roam box -- (390..392, 3731..3733) starts at
               (391, 3732), (440..460, 792..812) starts at (450, 802). For a
               box that straddles water or lava the computed centre lands in
               it, so two of the vanilla spawns ask to stand on a tile nothing
               can stand on. The old code printed "Fucked Npc" and added the
               npc anyway, which left a hobgoblin standing in the water off
               Karamja until something pushed it out.

               The spawn table stays as Jagex wrote it; the engine picks the
               nearest tile inside the same box that an npc can actually
               occupy. The correction is written back into the NPCLoc because
               Npc.remove() respawns from that same object. */
            int[] fixed = this.nearestOpenTile(npc);
            if (fixed == null) {
                Logger.print("Npc " + npc.id + " has no open tile in its roam box ("
                        + npc.minX + "-" + npc.maxX + ", " + npc.minY + "-" + npc.maxY
                        + "); leaving it at " + npc.startX + "," + npc.startY);
            } else if (fixed[0] != npc.startX || fixed[1] != npc.startY) {
                npc.startX = fixed[0];
                npc.startY = fixed[1];
                n.setLocation(Point.location(fixed[0], fixed[1]), true);
            }
        }
        this.npcs.add(n);
    }

    /**
     * The tile inside the roam box, closest to the recorded start tile, that
     * is not blocked ground. Null when the whole box is blocked.
     */
    private int[] nearestOpenTile(NPCLoc npc) {
        int[] best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int x = npc.minX; x <= npc.maxX; ++x) {
            for (int y = npc.minY; y <= npc.maxY; ++y) {
                if (this.isBlockedGround(x, y)) continue;
                int dist = Math.max(Math.abs(x - npc.startX), Math.abs(y - npc.startY));
                if (dist >= bestDist) continue;
                bestDist = dist;
                best = new int[]{x, y};
            }
        }
        return best;
    }

    public boolean isLoggedIn(long usernameHash) {
        Player friend = this.getPlayer(usernameHash);
        if (friend != null) {
            return friend.loggedIn();
        }
        return false;
    }

    public void registerGameObject(GameObject o) {
        switch (o.getType()) {
            case 0: {
                this.registerObject(o);
                break;
            }
            case 1: {
                this.registerDoor(o);
            }
        }
    }

    public void registerObject(GameObject o) {
        int height;
        int width;
        if (o.getGameObjectDef().getType() != 1 && o.getGameObjectDef().getType() != 2) {
            return;
        }
        int dir = o.getDirection();
        if (dir == 0 || dir == 4) {
            width = o.getGameObjectDef().getWidth();
            height = o.getGameObjectDef().getHeight();
        } else {
            height = o.getGameObjectDef().getWidth();
            width = o.getGameObjectDef().getHeight();
        }
        for (int x = o.getX(); x < o.getX() + width; ++x) {
            for (int y = o.getY(); y < o.getY() + height; ++y) {
                if (o.getGameObjectDef().getType() == 1) {
                    this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue | 0x40);
                    continue;
                }
                if (dir == 0) {
                    this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue | 2);
                    this.getTileValue((int)(x - 1), (int)y).objectValue = (byte)(this.getTileValue((int)(x - 1), (int)y).objectValue | 8);
                    continue;
                }
                if (dir == 2) {
                    this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue | 4);
                    this.getTileValue((int)x, (int)(y + 1)).objectValue = (byte)(this.getTileValue((int)x, (int)(y + 1)).objectValue | 1);
                    continue;
                }
                if (dir == 4) {
                    this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue | 8);
                    this.getTileValue((int)(x + 1), (int)y).objectValue = (byte)(this.getTileValue((int)(x + 1), (int)y).objectValue | 2);
                    continue;
                }
                if (dir != 6) continue;
                this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue | 1);
                this.getTileValue((int)x, (int)(y - 1)).objectValue = (byte)(this.getTileValue((int)x, (int)(y - 1)).objectValue | 4);
            }
        }
    }

    public void registerDoor(GameObject o) {
        if (o.getDoorDef().getDoorType() != 1) {
            return;
        }
        int dir = o.getDirection();
        int x = o.getX();
        int y = o.getY();
        if (dir == 0) {
            this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue | 1);
            this.getTileValue((int)x, (int)(y - 1)).objectValue = (byte)(this.getTileValue((int)x, (int)(y - 1)).objectValue | 4);
        } else if (dir == 1) {
            this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue | 2);
            this.getTileValue((int)(x - 1), (int)y).objectValue = (byte)(this.getTileValue((int)(x - 1), (int)y).objectValue | 8);
        } else if (dir == 2) {
            this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue | 0x10);
        } else if (dir == 3) {
            this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue | 0x20);
        }
    }

    public void unregisterObject(GameObject o) {
        int height;
        int width;
        if (o.getGameObjectDef().getType() != 1 && o.getGameObjectDef().getType() != 2) {
            return;
        }
        int dir = o.getDirection();
        if (dir == 0 || dir == 4) {
            width = o.getGameObjectDef().getWidth();
            height = o.getGameObjectDef().getHeight();
        } else {
            height = o.getGameObjectDef().getWidth();
            width = o.getGameObjectDef().getHeight();
        }
        for (int x = o.getX(); x < o.getX() + width; ++x) {
            for (int y = o.getY(); y < o.getY() + height; ++y) {
                if (o.getGameObjectDef().getType() == 1) {
                    this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue & 0xFFBF);
                    continue;
                }
                if (dir == 0) {
                    this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue & 0xFFFD);
                    this.getTileValue((int)(x - 1), (int)y).objectValue = (byte)(this.getTileValue((int)(x - 1), (int)y).objectValue & 0xFFF7);
                    continue;
                }
                if (dir == 2) {
                    this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue & 0xFFFB);
                    this.getTileValue((int)x, (int)(y + 1)).objectValue = (byte)(this.getTileValue((int)x, (int)(y + 1)).objectValue & 0xFFFE);
                    continue;
                }
                if (dir == 4) {
                    this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue & 0xFFF7);
                    this.getTileValue((int)(x + 1), (int)y).objectValue = (byte)(this.getTileValue((int)(x + 1), (int)y).objectValue & 0xFFFD);
                    continue;
                }
                if (dir != 6) continue;
                this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue & 0xFFFE);
                this.getTileValue((int)x, (int)(y - 1)).objectValue = (byte)(this.getTileValue((int)x, (int)(y - 1)).objectValue & 0xFFFB);
            }
        }
    }

    public void unregisterDoor(GameObject o) {
        if (o.getDoorDef().getDoorType() != 1) {
            return;
        }
        int dir = o.getDirection();
        int x = o.getX();
        int y = o.getY();
        if (dir == 0) {
            this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue & 0xFFFE);
            this.getTileValue((int)x, (int)(y - 1)).objectValue = (byte)(this.getTileValue((int)x, (int)(y - 1)).objectValue & 0xFFFB);
        } else if (dir == 1) {
            this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue & 0xFFFD);
            this.getTileValue((int)(x - 1), (int)y).objectValue = (byte)(this.getTileValue((int)(x - 1), (int)y).objectValue & 0xFFF7);
        } else if (dir == 2) {
            this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue & 0xFFEF);
        } else if (dir == 3) {
            this.getTileValue((int)x, (int)y).objectValue = (byte)(this.getTileValue((int)x, (int)y).objectValue & 0xFFDF);
        }
    }

    /**
     * Take a door out of the world, or put it back, so that players can walk
     * through the gap. For lever puzzles and nothing else.
     *
     * An ordinary door is opened by teleporting the player past it -- see
     * openOrdinaryDoor() in WallObjectAction -- and that works because the door
     * never really moves. A lever puzzle has no such trick available: the point
     * of it is that some doors stand open for as long as the levers say so, and
     * anybody may walk through them.
     *
     * Unregistering the GameObject is not enough on its own. A door is written
     * down twice: once in GameObjectLoc, which registerDoor() turns into bits in
     * ActiveTile.objectValue, and once in Landscape.rscd, where the wall byte
     * holds the door's id plus one and WorldLoader turns it into bits in
     * ActiveTile.mapValue. PathHandler tests both, so clearing one leaves the
     * other blocking and the door looks open while refusing to be walked
     * through. This clears both, and sets both back.
     *
     * The map bits are safe to clear because a tile has one wall byte per axis:
     * bit 1 at (x,y) is this wall and no other. The pair of bits per axis is how
     * WorldLoader writes it -- the tile the wall is on, and the tile on the far
     * side of it -- so both ends have to be undone together.
     *
     * @param id  the door's DoorDef id, used to check the tile really does hold
     *            this door before removing it
     * @param dir 0 for a door lying north to south, 1 for one lying east to west
     */
    public void setDoorOpen(int id, int x, int y, int dir, boolean open) {
        if (open) {
            ActiveTile tile = this.getTile(x, y);
            GameObject door = tile == null ? null : tile.getDoor();
            if (door != null && door.getID() == id) {
                this.unregisterGameObject(door);
            }
            if (dir == 0) {
                this.getTileValue(x, y).mapValue = (byte)(this.getTileValue(x, y).mapValue & 0xFE);
                this.getTileValue(x, y - 1).mapValue = (byte)(this.getTileValue(x, y - 1).mapValue & 0xFB);
            } else {
                this.getTileValue(x, y).mapValue = (byte)(this.getTileValue(x, y).mapValue & 0xFD);
                this.getTileValue(x - 1, y).mapValue = (byte)(this.getTileValue(x - 1, y).mapValue & 0xF7);
            }
        } else {
            this.registerGameObject(new GameObject(Point.location(x, y), id, dir, 1));
            if (dir == 0) {
                this.getTileValue(x, y).mapValue = (byte)(this.getTileValue(x, y).mapValue | 1);
                this.getTileValue(x, y - 1).mapValue = (byte)(this.getTileValue(x, y - 1).mapValue | 4);
            } else {
                this.getTileValue(x, y).mapValue = (byte)(this.getTileValue(x, y).mapValue | 2);
                this.getTileValue(x - 1, y).mapValue = (byte)(this.getTileValue(x - 1, y).mapValue | 8);
            }
        }
    }

    public void registerItem(final Item i) {
        if (i.getLoc() == null) {
            this.delayedEventHandler.add(new DelayedEvent(null, 180000){

                public void run() {
                    ActiveTile tile = World.this.getTile(i.getLocation());
                    if (tile.hasItem(i)) {
                        World.this.unregisterItem(i);
                    }
                    this.running = false;
                }
            });
        }
    }

    public void unregisterPlayer(Player p) {
        p.setLoggedIn(false);
        p.resetAll();
        /* A cannon belongs to whoever set it down and nobody else can lift it,
           so one left standing after its owner has gone would stand for ever.
           It goes with them; the engineer replaces cannons lost in action. */
        Multicannon.ownerLeft(p);
        p.save();
        Mob opponent = p.getOpponent();
        if (opponent != null) {
            p.resetCombat(CombatState.ERROR);
            opponent.resetCombat(CombatState.ERROR);
        }
        this.server.getLoginConnector().getActionSender().playerLogout(p.getUsernameHash());
        this.delayedEventHandler.removePlayersEvents(p);
        this.players.remove(p);
        this.setLocation(p, p.getLocation(), null);
    }

    public void unregisterNpc(Npc n) {
        if (this.hasNpc(n)) {
            this.npcs.remove(n);
        }
        this.setLocation(n, n.getLocation(), null);
    }

    public void unregisterGameObject(GameObject o) {
        o.remove();
        this.setLocation(o, o.getLocation(), null);
        switch (o.getType()) {
            case 0: {
                this.unregisterObject(o);
                break;
            }
            case 1: {
                this.unregisterDoor(o);
            }
        }
    }

    public void unregisterItem(Item i) {
        i.remove();
        this.setLocation(i, i.getLocation(), null);
    }

    public EntityList<Player> getPlayers() {
        return this.players;
    }

    public EntityList<Npc> getNpcs() {
        return this.npcs;
    }

    public int countPlayers() {
        return this.players.size();
    }

    public int countNpcs() {
        return this.npcs.size();
    }

    public boolean hasNpc(Npc n) {
        return this.npcs.contains(n);
    }

    public boolean hasPlayer(Player p) {
        return this.players.contains(p);
    }

    public Player getPlayer(long usernameHash) {
        for (Player p : this.players) {
            if (p.getUsernameHash() != usernameHash) continue;
            return p;
        }
        return null;
    }

    public Npc getNpc(int idx) {
        return this.npcs.get(idx);
    }

    public Npc getNpc(int id, int minX, int maxX, int minY, int maxY) {
        for (Npc npc : this.npcs) {
            if (npc.getID() != id || npc.getX() < minX || npc.getX() > maxX || npc.getY() < minY || npc.getY() > maxY) continue;
            return npc;
        }
        return null;
    }

    public Player getPlayer(int idx) {
        return this.players.get(idx);
    }
}

