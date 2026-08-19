/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.rscdaemon.server.entityhandling.defs.EntityDef;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class SpellDef
extends EntityDef {
    public int reqLevel;
    public int type;
    public int runeCount;
    public HashMap<Integer, Integer> requiredRunes;
    public int exp;

    public int getReqLevel() {
        return this.reqLevel;
    }

    public int getSpellType() {
        return this.type;
    }

    public int getRuneCount() {
        return this.runeCount;
    }

    public Set<Map.Entry<Integer, Integer>> getRunesRequired() {
        return this.requiredRunes.entrySet();
    }

    public int getExp() {
        return this.exp;
    }
}

