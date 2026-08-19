package org.rscdaemon.server.entityhandling.defs.extras;

import java.util.ArrayList;

/**
 * A chest Thieving can open.
 *
 * Keyed by object id in defs/extras/ObjectChest.xml.gz. Six chests in Classic
 * are opened this way. Five of them carry "Search for traps" and are trapped;
 * the Hemenster one carries "picklock" instead, is not trapped, and wants a
 * lockpick in the bag.
 *
 * A chest is not a pocket and not a stall: it hands over its entire table at
 * once. The Paladin chest gives a raw shark and adamantite ore and an uncut
 * sapphire and a thousand coins, all of them, every time. Nor is there a
 * failure roll -- the level is a gate, and above it the chest always opens.
 * That is Jagex's design and not a simplification: the recovered transcript of
 * the Nature-Rune chest runs straight through search, disable, open and reward
 * with no branch anywhere.
 *
 * On emptying: unlike a stall, a trapped chest passes through two states. The
 * moment the trap is found it becomes the searched chest (340), it becomes the
 * open chest (339) as the loot is handed over, and it goes back to searched
 * while it restocks. The Hemenster chest has its own pair, 379 and 380.
 *
 * On the experience field, see NpcPickpocketDef: the number stored is Jagex's,
 * in quarters, and is divided when it is awarded.
 */
public class ObjectChestDef {
    private int requiredLvl;
    private int quarterExp;
    private int respawnTime;
    private int searchedId;
    private int openId;
    private boolean lockpick;
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

    /** Seconds from the trap being found to the chest being ready again. */
    public int getRespawnTime() {
        return this.respawnTime;
    }

    /** What stands here while the chest is worked on and while it restocks. */
    public int getSearchedId() {
        return this.searchedId;
    }

    /** What stands here for the moment the loot is handed over. */
    public int getOpenId() {
        return this.openId;
    }

    /** Whether a lockpick is needed. True only for the Hemenster chest. */
    public boolean needsLockpick() {
        return this.lockpick;
    }

    public ArrayList<ThievingLoot> getLoot() {
        return this.loot;
    }
}
