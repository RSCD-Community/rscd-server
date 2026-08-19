/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs.extras;

import org.rscdaemon.server.entityhandling.defs.extras.ReqOreDef;

public class ItemSmeltingDef {
    public int exp;
    public int barId;
    public int requiredLvl;
    public ReqOreDef[] reqOres;

    public int getExp() {
        return this.exp;
    }

    public int getBarId() {
        return this.barId;
    }

    public int getReqLevel() {
        return this.requiredLvl;
    }

    public ReqOreDef[] getReqOres() {
        return this.reqOres;
    }
}

