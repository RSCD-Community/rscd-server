/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.ActiveTile;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.World;

public class ObjectRemover
extends DelayedEvent {
    public static final World world = World.getWorld();
    private GameObject object;

    public ObjectRemover(GameObject object, int delay) {
        super(null, delay);
        this.object = object;
    }

    public void run() {
        ActiveTile tile = world.getTile(this.object.getLocation());
        if (!tile.hasGameObject() || !tile.getGameObject().equals(this.object)) {
            this.running = false;
            return;
        }
        tile.remove(this.object);
        world.unregisterGameObject(this.object);
        this.running = false;
    }

    public boolean equals(Object o) {
        if (o instanceof ObjectRemover) {
            return ((ObjectRemover)o).getObject().equals(this.getObject());
        }
        return false;
    }

    public GameObject getObject() {
        return this.object;
    }
}

