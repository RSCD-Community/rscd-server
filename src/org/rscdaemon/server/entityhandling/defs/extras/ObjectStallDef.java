package org.rscdaemon.server.entityhandling.defs.extras;

import java.util.ArrayList;

/**
 * A thing you steal from with the "steal from" command.
 *
 * Keyed by object id in defs/extras/ObjectStall.xml.gz. Nine objects in Classic
 * carry the command: the six Ardougne market stalls, the Varrock tea stall, the
 * Gu'tanoth rock cake counter, and the four Gu'tanoth ogre counters, which
 * share one id and hold nothing.
 *
 * A stall hands over exactly one entry from its table, drawn at random. Only
 * the gem stall has more than one, and only it needs the draw.
 *
 * On emptying: a robbed stall is replaced by another object for a few seconds
 * and then put back, the same mechanic a mined rock uses. The replacement is
 * "empty stall" (341) for the market and tea stalls and the empty counter
 * (1034) for the rock cake counter; both are objects with no locations of their
 * own in the vanilla landscape, which is what an object that only exists as a
 * runtime state looks like. An emptyId of -1 means the object is never replaced
 * -- the ogre counters, which can be searched forever and never yield.
 *
 * On the experience field, see NpcPickpocketDef: the number stored is Jagex's,
 * in quarters, and is divided when it is awarded.
 */
public class ObjectStallDef {
    private int requiredLvl;
    private int quarterExp;
    private int respawnTime;
    private int emptyId;
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

    /** Seconds the stall stands empty before it restocks. */
    public int getRespawnTime() {
        return this.respawnTime;
    }

    /** What stands here while it is empty, or -1 if it is never emptied. */
    public int getEmptyId() {
        return this.emptyId;
    }

    public ArrayList<ThievingLoot> getLoot() {
        return this.loot;
    }
}
