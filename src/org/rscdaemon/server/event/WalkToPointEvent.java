/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;

public abstract class WalkToPointEvent
extends DelayedEvent {
    protected Point location;
    private int radius;
    private boolean stop;

    public WalkToPointEvent(Player owner, Point location, int radius, boolean stop) {
        super(owner, 500);
        this.location = location;
        this.radius = radius;
        this.stop = stop;
        if (stop && owner.withinRange(location, radius)) {
            owner.resetPath();
            this.arrived();
            this.running = false;
        }
    }

    public final void run() {
        if (this.stop && this.owner.withinRange(this.location, this.radius)) {
            this.owner.resetPath();
            this.arrived();
        } else {
            if (this.owner.hasMoved()) {
                return;
            }
            if (this.owner.withinRange(this.location, this.radius)) {
                this.arrived();
            }
        }
        this.running = false;
    }

    public abstract void arrived();

    public Point getLocation() {
        return this.location;
    }
}

