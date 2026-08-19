/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs.extras;

public class ObjectWoodcuttingDef {
    public int requiredLvl;
    private int logId;
    public int exp;
    public int fell;
    public int respawnTime;

    public int getExp() {
        return this.exp;
    }

    public int getLogId() {
        return this.logId;
    }

    public int getReqLevel() {
        return this.requiredLvl;
    }

    public int getFell() {
        return this.fell;
    }

    public int getRespawnTime() {
        return this.respawnTime;
    }
}

