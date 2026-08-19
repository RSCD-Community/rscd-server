package org.rscdaemon.server.entityhandling.defs.extras;

import java.util.ArrayList;

/**
 * One possible outcome of opening a locked chest.
 *
 * The crystal chest in Taverley does not roll each item separately. It rolls
 * one of ten possibilities, and that possibility then hands over everything on
 * it -- the spinach roll comes with its two thousand coins, the raw swordfish
 * certificate with its thousand, the eleven kinds of rune all together. This
 * class is that possibility: a weight, the items it always gives, and the
 * items it picks exactly one of.
 *
 * The weight is relative, not a percentage: the picker sums the table and
 * rolls against the sum, so the numbers in the document can stay as the source
 * wrote them rather than being renormalised into something no source says.
 *
 * {@code choice} exists for one entry and is empty on the other nine. The
 * seven-hundred-and-fifty-coin possibility gives a crystal key half with it,
 * and which half is an even coin flip; without a second list that would have
 * to be two possibilities, and their weights would then be a guess rather than
 * a halving of one weight the source does give.
 *
 * ItemDropDef is reused for the entries because it is already exactly
 * {@code id} and {@code amount}. Its third field, weight, means nothing here
 * and the document does not write it -- everything in {@code items} is given,
 * and everything in {@code choice} is equally likely.
 */
public class ChestLootDef {
    private int weight;
    private ArrayList<ItemDropDef> items;
    private ArrayList<ItemDropDef> choice;

    /** Relative to the other possibilities on the same table, not out of 100. */
    public int getWeight() {
        return this.weight;
    }

    /** Everything this possibility gives. Never null once loaded. */
    public ArrayList<ItemDropDef> getItems() {
        return this.items == null ? EMPTY : this.items;
    }

    /** One of these as well, chosen evenly. Empty for most possibilities. */
    public ArrayList<ItemDropDef> getChoice() {
        return this.choice == null ? EMPTY : this.choice;
    }

    private static final ArrayList<ItemDropDef> EMPTY = new ArrayList<ItemDropDef>(0);
}
