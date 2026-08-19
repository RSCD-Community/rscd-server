/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs.extras;

public class ObjectMiningDef {
    public int requiredLvl;
    private int oreId;
    public int exp;
    public int respawnTime;

    public int getExp() {
        return this.exp;
    }

    public int getOreId() {
        return this.oreId;
    }

    public int getReqLevel() {
        return this.requiredLvl;
    }

    public int getRespawnTime() {
        return this.respawnTime;
    }
}

