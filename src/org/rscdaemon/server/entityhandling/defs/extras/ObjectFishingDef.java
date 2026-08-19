/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs.extras;

import org.rscdaemon.server.entityhandling.defs.extras.ObjectFishDef;

public class ObjectFishingDef {
    public ObjectFishDef[] defs;
    public int netId;
    public int baitId;

    public int getNetId() {
        return this.netId;
    }

    public int getBaitId() {
        return this.baitId;
    }

    public int getReqLevel() {
        int requiredLevel = 99;
        for (ObjectFishDef def : this.defs) {
            if (def.getReqLevel() >= requiredLevel) continue;
            requiredLevel = def.getReqLevel();
        }
        return requiredLevel;
    }

    public ObjectFishDef[] getFishDefs() {
        return this.defs;
    }
}

