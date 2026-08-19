package org.rscdaemon.server.entityhandling.defs.extras;

/**
 * One thing Thieving can produce, from a pocket, a stall or a chest.
 *
 * A man carries three coins and nothing else, so his table is one of these. A
 * hero carries any of nine things, so his is nine, and one of them is drawn at
 * random when the pick succeeds -- Classic never gave out two rewards from a
 * single pickpocket, and the same is true of a stall: the gem stall has four
 * entries and hands over exactly one of them.
 *
 * A chest is the exception and hands over its whole table at once, which is why
 * the Paladin chest is written as four of these rather than one drawn from
 * four. Whether a table is drawn from or handed over whole is a property of the
 * thing being robbed, not of this class, so it lives in the def that owns the
 * list.
 *
 * The amount is a range because the gnomes of the Stronghold carry between two
 * and four hundred coins. Everything else has amountLow equal to amountHigh,
 * which is how a fixed reward is written here.
 */
public class ThievingLoot {
    private int id;
    private int amountLow;
    private int amountHigh;

    public int getID() {
        return this.id;
    }

    public int getAmountLow() {
        return this.amountLow;
    }

    public int getAmountHigh() {
        return this.amountHigh;
    }
}
