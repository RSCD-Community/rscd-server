/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs;

import org.rscdaemon.server.entityhandling.defs.EntityDef;

public class PrayerDef
extends EntityDef {
    public int reqLevel;
    public int drainRate;

    public int getReqLevel() {
        return this.reqLevel;
    }

    public int getDrainRate() {
        return this.drainRate;
    }
}

