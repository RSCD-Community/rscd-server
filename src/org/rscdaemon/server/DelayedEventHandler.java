/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server;

import java.util.ArrayList;
import java.util.Iterator;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.util.Logger;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public final class DelayedEventHandler {
    private static World world = World.getWorld();
    private ArrayList<DelayedEvent> toAdd = new ArrayList();
    private ArrayList<DelayedEvent> events = new ArrayList();

    public DelayedEventHandler() {
        world.setDelayedEventHandler(this);
    }

    public boolean contains(DelayedEvent event) {
        return this.events.contains(event);
    }

    public ArrayList<DelayedEvent> getEvents() {
        return this.events;
    }

    /**
     * Queue an event to start on the next tick.
     *
     * Callable from any thread, and it has to be: QuestManager.triggerEntity()
     * runs each quest on a thread of its own, and a quest's whole job is to
     * schedule things. The staging list is the hand-off point to the game
     * thread, so it is the one piece that needs locking -- doEvents() drains it
     * under the same lock.
     */
    public void add(DelayedEvent event) {
        synchronized (this.toAdd) {
            /* Both lists, not just the live one: two equal events staged in
               the same tick would otherwise both go live, and resetCombat
               only ever stops the first match it finds. */
            if (!this.events.contains(event) && !this.toAdd.contains(event)) {
                this.toAdd.add(event);
            }
        }
    }

    public void remove(DelayedEvent event) {
        this.events.remove(event);
    }

    public void removePlayersEvents(Player player) {
        Iterator<DelayedEvent> iterator = this.events.iterator();
        while (iterator.hasNext()) {
            DelayedEvent event = iterator.next();
            if (!event.belongsTo(player)) continue;
            iterator.remove();
        }
    }

    public void doEvents() {
        synchronized (this.toAdd) {
            if (this.toAdd.size() > 0) {
                this.events.addAll(this.toAdd);
                this.toAdd.clear();
            }
        }
        Iterator<DelayedEvent> iterator = this.events.iterator();
        while (iterator.hasNext()) {
            DelayedEvent event = iterator.next();
            if (event.shouldRun()) {
                try {
                    event.run();
                }
                catch (Throwable t) {
                    /* One broken event must not end the world -- literally.
                       An NPE thrown out of a dialogue effect used to unwind
                       through doEvents into GameEngine.run, killing the game
                       thread and with it every player's session and every
                       unsaved minute they had. The event is dropped, the tick
                       carries on, and the stack trace is in the log where it
                       can be fixed. */
                    Logger.error("Event " + event.getClass().getName() + " threw"
                            + (event.hasOwner() ? " for " + event.getOwner().getUsername() : ""));
                    Logger.error(t);
                    event.stop();
                }
                event.updateLastRun();
            }
            if (!event.shouldRemove()) continue;
            iterator.remove();
        }
    }
}

