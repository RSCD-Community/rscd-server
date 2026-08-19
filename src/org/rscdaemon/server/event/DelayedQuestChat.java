/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;

public abstract class DelayedQuestChat
extends DelayedEvent {
    public int curIndex;
    public String[] messages;
    public Npc npc;
    public Player owner;

    public DelayedQuestChat(Npc npc, Player owner, String[] messages) {
        super(null, 2200);
        this.owner = owner;
        this.npc = npc;
        this.messages = messages;
        this.curIndex = 0;
    }

    public void run() {
        this.owner.informOfNpcMessage(new ChatMessage(this.npc, this.messages[this.curIndex], this.owner));
        ++this.curIndex;
        if (this.curIndex == this.messages.length) {
            this.finished();
            this.stop();
            return;
        }
    }

    public abstract void finished();
}

