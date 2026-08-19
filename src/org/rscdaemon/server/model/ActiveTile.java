/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.List;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class ActiveTile {
    private List<Player> players = new ArrayList<Player>();
    private List<Npc> npcs = new ArrayList<Npc>();
    private List<Item> items = new ArrayList<Item>();
    private GameObject object = null;
    private GameObject door = null;
    /* A corner tile carries two wall pieces -- one horizontal, one vertical
       -- and they are not duplicates of each other. The Legends flame wall's
       corner (450,3704) held both and the single slot silently evicted the
       first, leaving a gap in the wall that unregisterDoor had also unblocked.
       Same-direction doors on one tile are still a genuine conflict. */
    private GameObject door2 = null;
    private static World world = World.getWorld();
    private int x;
    private int y;

    public ActiveTile(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /*
     * A tile's scenery object (type 0) and its wall/door object (type 1) are
     * independent -- the client tracks them in separate arrays
     * (objectModelArray vs doorModel) and a real tile can carry both at once,
     * e.g. a door with a sign or torch scenery piece beside it. They used to
     * share this one `object` field, so placing a door onto a tile that
     * already had scenery (or vice versa) silently evicted whichever was
     * there first via unregisterGameObject -- that's how the Grand Tree's
     * plane-2 trunk (id 571) vanished: a door loaded onto the same tile after
     * it and kicked it off silently. Two type-0 (or two type-1) objects on
     * one tile is still a genuine conflict and still evicts -- that case is a
     * real duplicate, not two different kinds of scenery.
     */
    public void add(Entity entity) {
        if (entity instanceof Player) {
            this.players.add((Player)entity);
        } else if (entity instanceof Npc) {
            /* players and npcs are ArrayLists, so add() takes duplicates
               happily while remove() only ever drops the first occurrence.
               One entity listed twice on a tile therefore survives its own
               death: unregisterNpc clears one entry, the other stays, and
               getNpcsInView keeps handing a dead npc to every client that
               walks past. Nothing found in the current code doubles an entry
               -- Entity.setLocation is the only thing that moves one, and it
               removes before it adds -- but the failure is silent, permanent
               and player-visible, and the guard costs a scan of a list that
               holds one or two elements. */
            if (!this.npcs.contains(entity)) {
                this.npcs.add((Npc)entity);
            }
        } else if (entity instanceof Item) {
            this.items.add((Item)entity);
        } else if (entity instanceof GameObject) {
            GameObject go = (GameObject)entity;
            if (go.getType() == 1) {
                if (this.door != null && this.door.getDirection() == go.getDirection()) {
                    world.unregisterGameObject(this.door);
                    this.door = go;
                } else if (this.door == null) {
                    this.door = go;
                } else {
                    /* Different direction: a legitimate second wall piece,
                       not a duplicate. Only an equally-oriented third door
                       is a real conflict for this slot. */
                    if (this.door2 != null) {
                        world.unregisterGameObject(this.door2);
                    }
                    this.door2 = go;
                }
            } else {
                if (this.object != null) {
                    world.unregisterGameObject(this.object);
                }
                this.object = go;
            }
        }
    }

    public void remove(Entity entity) {
        if (entity instanceof Player) {
            this.players.remove(entity);
        } else if (entity instanceof Npc) {
            this.npcs.remove(entity);
        } else if (entity instanceof Item) {
            this.items.remove(entity);
        } else if (entity instanceof GameObject) {
            /* Only vacate a slot the entity actually occupies. Clearing
             * unconditionally let a stale unregister wipe a DIFFERENT object
             * that had since taken the slot: TouristTrap's cactus swap
             * constructed the dried cactus (whose ctor evicts the cut one and
             * takes the slot) and then unregistered the cut one -- which
             * nulled the dried cactus out of the tile, leaving an invisible
             * blocker where a model should have been. */
            if (entity == this.door) {
                this.door = null;
            } else if (entity == this.door2) {
                this.door2 = null;
            } else if (entity == this.object) {
                this.object = null;
            }
        }
    }

    public boolean hasPlayers() {
        return this.players != null && this.players.size() > 0;
    }

    public List<Player> getPlayers() {
        return this.players;
    }

    public boolean hasGameObject() {
        return this.object != null;
    }

    public GameObject getGameObject() {
        return this.object;
    }

    public boolean hasDoor() {
        return this.door != null;
    }

    public GameObject getDoor() {
        return this.door;
    }

    /** The corner tile's other wall piece, or null. Clicks and item-uses go
        through getDoor(); this exists so the view sends both to the client. */
    public GameObject getSecondDoor() {
        return this.door2;
    }

    public List<Item> getItems() {
        return this.items;
    }

    public boolean hasItem(Item item) {
        return this.items.contains(item);
    }

    public boolean hasItems() {
        return this.items != null && this.items.size() > 0;
    }

    public List<Npc> getNpcs() {
        return this.npcs;
    }

    public boolean hasNpcs() {
        return this.npcs != null && this.npcs.size() > 0;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }
}

