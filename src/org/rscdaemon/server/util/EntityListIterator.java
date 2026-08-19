/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.util;

import java.util.Iterator;
import java.util.Set;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.util.EntityList;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class EntityListIterator<E extends Entity>
implements Iterator<E> {
    private Integer[] indicies;
    private Object[] entities;
    private EntityList<E> entityList;
    private int curIndex = 0;

    public EntityListIterator(Object[] entities, Set<Integer> indicies, EntityList<E> entityList) {
        this.entities = entities;
        this.indicies = indicies.toArray(new Integer[0]);
        this.entityList = entityList;
    }

    @Override
    public boolean hasNext() {
        return this.indicies.length != this.curIndex;
    }

    @Override
    public E next() {
        Object temp = this.entities[this.indicies[this.curIndex]];
        ++this.curIndex;
        return (E)((Entity)temp);
    }

    /*
     * Passing the Integer index here used to unbox and bind to
     * EntityList.remove(int) -- the unguarded overload -- rather than to the
     * identity-guarded remove(T). That is the wrong half of the pair: it nulls
     * whatever occupies the slot now, which after an index reuse is somebody
     * else, and it does it without going through World.unregisterNpc, so the
     * entity stays listed on its ActiveTile. An npc in that state is still
     * broadcast to every client in view but answers "Attacking disabled." to
     * all of them, because world.getNpc(index) is null. Nothing calls this
     * method today (both it.remove() sites in the server are on ordinary
     * collections), which is the only reason it has not fired.
     *
     * Removing by entity instead means the guard applies and a stale index
     * simply does nothing, matching EntityList.remove(T)'s own contract.
     */
    @Override
    public void remove() {
        if (this.curIndex >= 1) {
            E entity = (E)((Entity)this.entities[this.indicies[this.curIndex - 1]]);
            if (entity != null) {
                this.entityList.remove(entity);
            }
        }
    }
}

