/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.model;

import org.rscdaemon.ls.model.Item;

public class InvItem
extends Item {
    private boolean wielded;

    public InvItem(int id, int amount, boolean wielded) {
        super(id, amount);
        this.wielded = wielded;
    }

    public boolean isWielded() {
        return this.wielded;
    }
}

