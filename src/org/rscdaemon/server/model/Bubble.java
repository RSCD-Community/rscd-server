/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.model.Player;

public class Bubble {
    private Player owner;
    private int itemID;

    public Bubble(Player owner, int itemID) {
        this.owner = owner;
        this.itemID = itemID;
    }

    public Player getOwner() {
        return this.owner;
    }

    public int getID() {
        return this.itemID;
    }
}

