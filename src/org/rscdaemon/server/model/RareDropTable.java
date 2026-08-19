package org.rscdaemon.server.model;

import org.rscdaemon.server.entityhandling.defs.extras.ItemDropDef;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.PersistenceManager;

/*
 * Authentic RSC monsters roll drops from two tables: their own, and (for
 * roughly a dozen mid/high-level monsters -- Ice warrior, Chaos dwarf,
 * Lesser demon, Fire giant, Giant, Hobgoblin, at minimum) a small extra
 * chance of rolling this shared table instead of anything on their own
 * list: uncut gems, half-keys, rune equipment, Dragon medium helmet, Half
 * Dragon Square Shield, ore/bar certificates, high-value runes.
 *
 * An NPCDef's own drops[] references this table with a -2 sentinel id
 * (see Npc.die()) as one weighted outcome among its normal ones, the same
 * shape as the -1 "nothing" sentinel added the same day: a per-outcome
 * marker rather than a new top-level field on NPCDef.
 *
 * Once rolled, the RDT itself always yields something -- there is no
 * further "nothing" inside it. The outer table's own weight on the -2
 * entry is already the rarity; that is where a monster's real RDT-access
 * chance (documented per-monster as roughly 1/128 to 4/128) belongs.
 */
public class RareDropTable {
    private static final World world = World.getWorld();
    private static final ItemDropDef[] entries = (ItemDropDef[]) PersistenceManager.load("defs/RareDropTable.xml.gz");

    public static void roll(int x, int y, Player owner) {
        int total = 0;
        for (ItemDropDef drop : entries) {
            total += drop.getWeight();
        }
        int hit = DataConversions.random(0, total);
        total = 0;
        for (ItemDropDef drop : entries) {
            if (hit >= total && hit < total + drop.getWeight()) {
                if (drop.getID() >= 0) {
                    world.registerItem(new Item(drop.getID(), x, y, drop.getAmount(), owner));
                }
                return;
            }
            total += drop.getWeight();
        }
    }
}
