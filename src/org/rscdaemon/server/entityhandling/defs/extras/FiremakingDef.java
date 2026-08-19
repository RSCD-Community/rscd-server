/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs.extras;

public class FiremakingDef {
    public int level;
    public int exp;
    public int length;

    public int getRequiredLevel() {
        return this.level;
    }

    public int getExp() {
        return this.exp;
    }

    public int getLength() {
        return this.length * 1000;
    }
}

