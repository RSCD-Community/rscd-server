/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Player;

public abstract class SingleEvent
extends DelayedEvent {
    public SingleEvent(Player owner, int delay) {
        super(owner, delay);
    }

    public void run() {
        this.action();
        this.running = false;
    }

    public abstract void action();
}

