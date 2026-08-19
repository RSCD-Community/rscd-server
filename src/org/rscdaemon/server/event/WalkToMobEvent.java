/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Player;

public abstract class WalkToMobEvent
extends DelayedEvent {
    protected Mob affectedMob;
    private int radius;

    public WalkToMobEvent(Player owner, Mob affectedMob, int radius) {
        super(owner, 500);
        this.affectedMob = affectedMob;
        this.radius = radius;
        if (owner.withinRange(affectedMob, radius)) {
            this.arrived();
            this.running = false;
        }
    }

    public final void run() {
        if (this.owner.withinRange(this.affectedMob, this.radius)) {
            this.arrived();
        } else {
            if (this.owner.hasMoved()) {
                return;
            }
            this.failed();
        }
        this.running = false;
    }

    public abstract void arrived();

    public void failed() {
    }

    public Mob getAffectedMob() {
        return this.affectedMob;
    }
}

