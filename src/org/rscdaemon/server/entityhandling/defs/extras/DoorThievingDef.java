package org.rscdaemon.server.entityhandling.defs.extras;

/**
 * What it takes to pick one door's lock.
 *
 * Keyed by DoorDef id in defs/extras/DoorThieving.xml.gz. Doors number from
 * zero independently of scenery -- door 97 is the Paladin house and scenery 97
 * is something else entirely -- so this map is keyed by the door id and nothing
 * else may be looked up in it.
 *
 * There is no loot list because a picked lock gives nothing but the doorway.
 * The Underground Pass shortcut railings give not even experience, which is why
 * the experience field is allowed to be zero rather than assumed non-zero.
 *
 * Thirteen door ids in Classic carry a "pick lock" command; eleven have an
 * entry here. The two that do not are door 166, which is in the data files and
 * nowhere in the world, and door 168, the unicorn cage of Underground Pass,
 * which is that quest's business and has no published level. The Ancient Wooden
 * Doors of the Viyeldi caves are also picked at level 50, but they are scenery
 * rather than a door and belong to Legend's Quest.
 *
 * On the experience field, see NpcPickpocketDef: the number stored is Jagex's,
 * in quarters, and is divided when it is awarded.
 */
public class DoorThievingDef {
    private int requiredLvl;
    private int quarterExp;
    private boolean lockpick;

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

    /** Whether a lockpick is needed. True for four of the eleven. */
    public boolean needsLockpick() {
        return this.lockpick;
    }
}
