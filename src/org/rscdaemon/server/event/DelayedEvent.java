/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import org.rscdaemon.server.DelayedEventHandler;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;

public abstract class DelayedEvent {
    public static final World world = World.getWorld();
    protected boolean running = true;
    protected int delay = 500;
    protected Player owner;
    private long lastRun = System.currentTimeMillis();
    protected final DelayedEventHandler handler = World.getWorld().getDelayedEventHandler();

    public DelayedEvent(Player owner, int delay) {
        this.owner = owner;
        this.delay = delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public void setLastRun(long time) {
        this.lastRun = time;
    }

    public final boolean shouldRun() {
        return this.running && System.currentTimeMillis() - this.lastRun >= (long)this.delay;
    }

    public int timeTillNextRun() {
        int time = (int)((long)this.delay - (System.currentTimeMillis() - this.lastRun));
        return time < 0 ? 0 : time;
    }

    public abstract void run();

    public final void updateLastRun() {
        this.lastRun = System.currentTimeMillis();
    }

    public final void stop() {
        this.running = false;
    }

    public final boolean shouldRemove() {
        return !this.running;
    }

    public boolean belongsTo(Player player) {
        return this.owner != null && this.owner.equals(player);
    }

    public boolean hasOwner() {
        return this.owner != null;
    }

    public Player getOwner() {
        return this.owner;
    }
}

