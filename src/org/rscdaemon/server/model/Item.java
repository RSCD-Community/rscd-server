/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.ItemDef;
import org.rscdaemon.server.entityhandling.locs.ItemLoc;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;

public class Item
extends Entity {
    private static final World world = World.getWorld();
    private Player owner;
    private int amount;
    private long spawnedTime;
    private boolean removed = false;
    private ItemLoc loc = null;

    public Item(ItemLoc loc) {
        this.loc = loc;
        this.setID(loc.id);
        this.setAmount(loc.amount);
        this.spawnedTime = System.currentTimeMillis();
        this.setLocation(Point.location(loc.x, loc.y));
    }

    public Item(int id, int x, int y, int amount, Player owner) {
        this.setID(id);
        this.setAmount(amount);
        this.owner = owner;
        this.spawnedTime = System.currentTimeMillis();
        this.setLocation(Point.location(x, y));
    }

    public boolean visibleTo(Player p) {
        if (this.owner == null || p.equals(this.owner)) {
            return true;
        }
        return System.currentTimeMillis() - this.spawnedTime > 60000L;
    }

    public ItemLoc getLoc() {
        return this.loc;
    }

    public boolean isRemoved() {
        return this.removed;
    }

    public void remove() {
        if (!this.removed && this.loc != null && this.loc.getRespawnTime() > 0) {
            world.getDelayedEventHandler().add(new DelayedEvent(null, this.loc.getRespawnTime() * 1000){

                public void run() {
                    world.registerItem(new Item(Item.this.loc));
                    this.running = false;
                }
            });
        }
        this.removed = true;
    }

    public ItemDef getDef() {
        return EntityHandler.getItemDef(this.id);
    }

    public void setAmount(int amount) {
        this.amount = this.getDef().isStackable() ? amount : 1;
    }

    /*
     * GHOST GROUND ITEMS. There used to be a value-based equals() here --
     * same id, amount, spawnedTime, owner and location made two Items "equal"
     * -- and that is exactly wrong for this class. A death or an npc kill
     * constructs several Item objects in the same loop, so spawnedTime (a
     * millisecond timestamp) is frequently identical across the whole batch,
     * and every other field is often identical too: five bones stacks from one
     * death are five DISTINCT objects that used to be indistinguishable by
     * equals().
     *
     * That mattered because ActiveTile.items is a plain ArrayList, and both
     * hasItem() and the unregister path go through it using equals():
     *
     *    this.items.contains(item)   // hasItem()
     *    this.items.remove(entity)   // unregisterItem(), via World.setLocation
     *
     * List.remove(Object) removes the first element equals() to the argument,
     * not the argument itself. A stale, redundant pickup event -- the leftover
     * from an earlier spam-click that resolved to a sibling before the list had
     * shrunk -- would find tile.hasItem() true (a sibling still there, equals()
     * to the one it is holding) and call unregisterItem() again. That call's
     * list removal then struck the sibling by value, not the item it thought it
     * was removing. The sibling vanished from the world's tile list without its
     * own isRemoved flag ever being set, so revalidateWatchedItems() -- which
     * keys off that flag -- never queued a removal for it, and no client was
     * ever told it was gone. One redundant click, one permanent ghost; harmless
     * everywhere the inventory was concerned, since the click that mattered
     * still succeeded and the count came out right.
     *
     * No override needed to fix it: nothing outside this class actually wants
     * value equality (checked -- the only other equals() calls in the codebase
     * are on the unrelated InvItem class). Identity is what a ground item
     * should be tracked by, and that is Object's default.
     */

    public long getSpawnedTime() {
        return this.spawnedTime;
    }

    public int getAmount() {
        return this.amount;
    }

    public Player getOwner() {
        return this.owner;
    }

    public boolean isOn(int x, int y) {
        return x == this.getX() && y == this.getY();
    }
}

