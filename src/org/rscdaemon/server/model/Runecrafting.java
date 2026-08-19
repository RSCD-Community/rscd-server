package org.rscdaemon.server.model;

import org.rscdaemon.server.entityhandling.EntityHandler;

/**
 * Runecrafting at the altar circles.
 *
 * There are no inner altar realms here -- the circles stand in the open
 * world, guarded by whatever lives around them -- so the mysterious-ruin
 * teleport step is folded away: using the matching talisman (or essence,
 * while carrying the talisman) on an altar binds every essence in the
 * inventory at once, the way one Craft-rune click did at a real altar.
 *
 * Regular essence (1304) binds only the basic runes, Air through Body.
 * Pure essence (1305) binds anything. That split is the 2007 rule applied
 * deliberately: pure is the reason to keep mining past the basics.
 *
 * Levels, experience and the multiple-rune breakpoints are the genuine
 * 2004 tables (Blood and Soul continue the ladder -- Jagex's own numbers
 * for them belong to reworked systems on landmasses this world lacks).
 * Experience is stored in quarter-units like everything else.
 */
public class Runecrafting {

    /** altar object id, talisman item id, rune item id, level required,
     *  quarter-xp per essence, bonus-rune breakpoint (0 = always one),
     *  whether pure essence is required. */
    private static final int[][] ALTARS = {
        // altar  talis  rune  lvl  qxp  every  pure
        { 1193,   1291,  33,   1,   20,  11,    0 },   // Air
        { 1194,   1292,  35,   2,   22,  14,    0 },   // Mind
        { 1195,   1293,  32,   5,   24,  19,    0 },   // Water
        { 1196,   1294,  34,   9,   26,  26,    0 },   // Earth
        { 1197,   1295,  31,   14,  28,  35,    0 },   // Fire
        { 1198,   1296,  36,   20,  30,  46,    0 },   // Body
        { 1200,   1298,  46,   27,  32,  59,    1 },   // Cosmic
        { 1199,   1297,  41,   35,  34,  74,    1 },   // Chaos
        { 1201,   1299,  40,   44,  36,  91,    1 },   // Nature
        { 1202,   1300,  42,   54,  38,  0,     1 },   // Law
        { 1203,   1301,  38,   65,  40,  0,     1 },   // Death
        { 1204,   1302,  619,  77,  42,  0,     1 },   // Blood
        { 1205,   1303,  825,  90,  44,  0,     1 },   // Soul
    };

    public static final int SKILL = 18;
    public static final int ESSENCE = 1304;
    public static final int PURE_ESSENCE = 1305;

    /** True if this use-on-object was runecrafting business (handled here,
     *  whatever the outcome). False hands the object back to the switch. */
    public static boolean handle(Player player, GameObject object, InvItem item) {
        int[] altar = null;
        for (int[] row : ALTARS) {
            if (row[0] == object.getID()) {
                altar = row;
                break;
            }
        }
        if (altar == null) {
            return false;
        }
        int used = item.getID();
        if (used != altar[1] && used != ESSENCE && used != PURE_ESSENCE) {
            return false;
        }
        if (player.getInventory().countId(altar[1]) < 1) {
            player.getActionSender().sendMessage(
                "You need the " + EntityHandler.getItemDef(altar[1]).getName()
                + " to channel this altar's power");
            return true;
        }
        if (player.getMaxStat(SKILL) < altar[3]) {
            player.getActionSender().sendMessage(
                "You require level " + altar[3] + " runecrafting to bind "
                + EntityHandler.getItemDef(altar[2]).getName().toLowerCase() + "s");
            return true;
        }
        int pure = player.getInventory().countId(PURE_ESSENCE);
        int regular = altar[6] == 1 ? 0 : player.getInventory().countId(ESSENCE);
        int essence = pure + regular;
        if (essence == 0) {
            player.getActionSender().sendMessage(altar[6] == 1
                ? "This altar only answers to pure rune essence"
                : "You have no rune essence to bind");
            return true;
        }
        int each = altar[5] > 0 ? 1 + player.getMaxStat(SKILL) / altar[5] : 1;
        if (regular > 0) {
            player.getInventory().remove(ESSENCE, regular);
        }
        if (pure > 0) {
            player.getInventory().remove(PURE_ESSENCE, pure);
        }
        player.getInventory().add(new InvItem(altar[2], essence * each));
        player.getActionSender().sendInventory();
        player.incExpQuarters(SKILL, altar[4] * essence, true);
        player.getActionSender().sendStat(SKILL);
        player.getActionSender().sendMessage("You bind the temple's power into "
            + EntityHandler.getItemDef(altar[2]).getName().toLowerCase() + "s");
        return true;
    }
}
