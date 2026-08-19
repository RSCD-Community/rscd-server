package org.rscdaemon.server.entityhandling.defs.extras;

import java.util.ArrayList;

/**
 * What it takes to pick one npc's pocket, and what is in it.
 *
 * Keyed by npc id in defs/extras/NpcPickpocket.xml.gz. Twenty-nine npcs carry
 * the "pickpocket" command in NPCDef and every one of them has an entry here;
 * an npc with the command and no entry cannot be robbed, which is the same
 * answer as not having the command at all.
 *
 * On the experience field: Jagex stored experience in quarters, which is why
 * the wiki records a guard's pocket as 46.75 and not as a whole number. RSCD's
 * experience scale is Jagex's divided by four -- 83 for level two, not 332 --
 * so quarters cannot survive in it. The exact Jagex figure is kept here and
 * divided at the moment it is awarded, so the number in the file is the real
 * one and the rounding happens once, in one place, rather than being baked
 * into the data.
 */
public class NpcPickpocketDef {
    private int requiredLvl;
    private int quarterExp;
    private ArrayList<ThievingLoot> loot;

    public int getReqLevel() {
        return this.requiredLvl;
    }

    /** Jagex's own figure, in quarter-experience. */
    public int getQuarterExp() {
        return this.quarterExp;
    }

    /** The same figure on RSCD's scale, rounded half up. */
    public int getExp() {
        return (this.quarterExp + 2) / 4;
    }

    public ArrayList<ThievingLoot> getLoot() {
        return this.loot;
    }
}
