/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.Player;

public abstract class WalkToObjectEvent
extends DelayedEvent {
    protected GameObject object;
    private boolean stop;

    public WalkToObjectEvent(Player owner, GameObject object, boolean stop) {
        super(owner, 500);
        this.object = object;
        this.stop = stop;
        if (stop && owner.atObject(object)) {
            owner.resetPath();
            this.arrived();
            this.running = false;
        }
    }

    public final void run() {
        if (this.stop && this.owner.atObject(this.object)) {
            this.owner.resetPath();
            this.arrived();
        } else {
            if (this.owner.hasMoved()) {
                return;
            }
            if (this.owner.atObject(this.object)) {
                this.arrived();
            }
        }
        this.running = false;
    }

    public abstract void arrived();
}

