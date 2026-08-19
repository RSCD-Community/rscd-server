/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs.extras;

public class ItemCraftingDef {
    public int requiredLvl;
    public int itemID;
    public int exp;

    public int getReqLevel() {
        return this.requiredLvl;
    }

    public int getItemID() {
        return this.itemID;
    }

    public int getExp() {
        return this.exp;
    }
}

