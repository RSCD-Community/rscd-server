/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.util.EntityListIterator;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class EntityList<T extends Entity>
implements Iterable<T> {
    public static final int DEFAULT_CAPACITY = 2000;

    /**
     * An entity's index is its identity on the wire -- every packet builder
     * that names one writes it as 16 bits (NpcPositionPacketBuilder:29,
     * PlayerUpdatePacketBuilder:47, and so on). So the list may grow, but
     * never past what a short can carry, or the client would be told about an
     * entity under somebody else's number.
     */
    public static final int MAX_CAPACITY = 65535;

    protected Object[] entities;
    protected Set<Integer> indicies = new HashSet<Integer>();
    protected int curIndex = 0;
    protected int capacity;

    public EntityList(int capacity) {
        this.entities = new Object[capacity];
        this.capacity = capacity;
    }

    public EntityList() {
        this(2000);
    }

    /*
     * Guarded by identity, not just index: a stale entity -- one already
     * removed once, still reachable through some in-flight event that held
     * onto the Java reference -- keeps the index it was last stamped with
     * forever. If that index has since been handed to a new entity (add()
     * reuses freed slots), an unguarded remove would null out the new
     * occupant's slot instead of its own. Observed for real: an npc killed
     * twice by two near-simultaneous hits (one landing on the fresh respawn
     * autocast retargeted onto the reused index before the client learned
     * the original had died) silently vanished from the registry while
     * still on screen -- see Npc.killedBy's own guard for the other half of
     * this fix.
     */
    public void remove(T entity) {
        int idx = ((Entity)entity).getIndex();
        if (this.entities[idx] != entity) {
            return;
        }
        this.entities[idx] = null;
        this.indicies.remove(idx);
    }

    public T remove(int index) {
        Object temp = this.entities[index];
        this.entities[index] = null;
        this.indicies.remove(index);
        return (T)((Entity)temp);
    }

    public T get(int index) {
        return (T)((Entity)this.entities[index]);
    }

    /**
     * Finds the next free slot and takes it.
     *
     * This was written as a recursive scan, which meant a full list did not
     * fail -- it recursed until the stack ran out, and took the server with
     * it. That is exactly what happened once NpcLoc.xml.gz grew past 4000
     * entries: the world would not finish loading, with a StackOverflowError
     * a thousand frames deep in here and nothing to say what had overflowed.
     *
     * The guard that replaced it dropped the entity instead, which stopped the
     * crash but traded it for silent loss -- a boot could log two hundred
     * dropped npcs and still call itself started, with which two hundred
     * depending on load order. So the list grows now. A capacity is a starting
     * size, not a limit, and the only real ceiling is the 16 bits the index
     * travels in.
     */
    public void add(T entity) {
        for (int scanned = 0; scanned < this.capacity; scanned++) {
            if (this.entities[this.curIndex] == null) {
                this.entities[this.curIndex] = entity;
                ((Entity)entity).setIndex(this.curIndex);
                this.indicies.add(this.curIndex);
                this.increaseIndex();
                return;
            }
            this.increaseIndex();
        }

        if (!this.grow()) {
            Logger.print("EntityList cannot hold more than " + MAX_CAPACITY
                    + " entries -- an index has to fit in the 16 bits the client"
                    + " reads it from. Dropped " + entity);
            return;
        }
        this.add(entity);
    }

    /**
     * Doubles the list, up to {@link #MAX_CAPACITY}. Existing entities keep
     * the index they already have -- the array is copied, not rebuilt -- so
     * nothing a client has already been told about changes number.
     *
     * Returns false when there is no room left to grow into, which is the only
     * case where an entity is still lost.
     */
    private boolean grow() {
        if (this.capacity >= MAX_CAPACITY) {
            return false;
        }
        int wanted = this.capacity * 2;
        if (wanted > MAX_CAPACITY || wanted < this.capacity) {
            wanted = MAX_CAPACITY;
        }
        Object[] bigger = new Object[wanted];
        System.arraycopy(this.entities, 0, bigger, 0, this.capacity);
        // Aim at the first of the new slots, so the scan above finds one
        // immediately instead of walking the full old list again.
        this.curIndex = this.capacity;
        this.entities = bigger;
        this.capacity = wanted;
        Logger.print("EntityList grew to " + wanted + " entries");
        return true;
    }

    @Override
    public Iterator<T> iterator() {
        return new EntityListIterator(this.entities, this.indicies, this);
    }

    public void increaseIndex() {
        ++this.curIndex;
        if (this.curIndex >= this.capacity) {
            this.curIndex = 0;
        }
    }

    public boolean contains(T entity) {
        return this.indexOf(entity) > -1;
    }

    public int indexOf(T entity) {
        for (int index : this.indicies) {
            if (!this.entities[index].equals(entity)) continue;
            return index;
        }
        return -1;
    }

    public int count() {
        return this.indicies.size();
    }

    public int size() {
        return this.indicies.size();
    }
}

