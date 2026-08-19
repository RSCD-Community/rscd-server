/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs.extras;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.rscdaemon.server.entityhandling.EntityHandler;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class ItemWieldableDef {
    public int sprite;
    public int type;
    private int wieldPos;
    public int armourPoints;
    public int weaponAimPoints;
    public int weaponPowerPoints;
    public int magicPoints;
    public int prayerPoints;
    public int rangePoints;
    public HashMap<Integer, Integer> requiredStats;
    private boolean femaleOnly;

    public int getSprite() {
        return this.sprite;
    }

    public int getType() {
        return this.type;
    }

    public int[] getAffectedTypes() {
        int[] affectedTypes = EntityHandler.getItemAffectedTypes(this.type);
        if (affectedTypes != null) {
            return affectedTypes;
        }
        return new int[0];
    }

    public int getWieldPos() {
        return this.wieldPos;
    }

    public int getArmourPoints() {
        return this.armourPoints;
    }

    public int getWeaponAimPoints() {
        return this.weaponAimPoints;
    }

    public int getWeaponPowerPoints() {
        return this.weaponPowerPoints;
    }

    public int getMagicPoints() {
        return this.magicPoints;
    }

    public int getPrayerPoints() {
        return this.prayerPoints;
    }

    public int getRangePoints() {
        return this.rangePoints;
    }

    public Set<Map.Entry<Integer, Integer>> getStatsRequired() {
        return this.requiredStats.entrySet();
    }

    public boolean femaleOnly() {
        return this.femaleOnly;
    }
}

