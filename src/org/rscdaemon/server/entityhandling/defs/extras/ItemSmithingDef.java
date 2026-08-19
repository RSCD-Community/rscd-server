/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs.extras;

public class ItemSmithingDef {
    public int level;
    public int bars;
    public int itemID;
    public int amount;

    public int getRequiredLevel() {
        return this.level;
    }

    public int getRequiredBars() {
        return this.bars;
    }

    public int getItemID() {
        return this.itemID;
    }

    public int getAmount() {
        return this.amount;
    }
}

