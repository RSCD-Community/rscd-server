package org.rscdaemon.server.util;

import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.extras.ItemSmeltingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ReqOreDef;
import org.rscdaemon.server.model.Player;

/**
 * Turning ore into bars, in Jagex's words.
 *
 * Two things smelt: a furnace (InvUseOnObject) and the Superheat item spell
 * (SpellHandler). They shared nothing, and between them they invented every
 * line of text a player ever saw while smelting -- "You need 2 coal to smelt a
 * iron ore.", "You need a smithing level of 30 to smelt this.", "You make a
 * steel bar." None of those are Jagex's. The real messages are per-metal and
 * differ between the two, and they were recovered from RSCSundae's
 * skill_smithing/smelting.lua and skill_magic/superheat.lua, each line cited
 * there to a named packet capture.
 *
 * Everything here is driven off ItemSmeltingDef, which already carried the
 * right levels and recipes -- only the words and the order of the checks were
 * wrong.
 */
public final class Smelting {

    public static final int COAL = 155;
    public static final int COPPER_ORE = 150;
    public static final int TIN_ORE = 202;
    public static final int IRON_ORE = 151;

    public static final int BRONZE_BAR = 169;
    public static final int IRON_BAR = 170;
    public static final int STEEL_BAR = 171;

    /**
     * The smelting def keyed under 9999 is iron ore on its own -- the recipe
     * you get when there is no coal to make steel with. It is a real entry in
     * ItemSmeltingDef.xml, not a sentinel we invented.
     */
    private static final int IRON_ALONE = 9999;

    private Smelting() {
    }

    /**
     * What is actually being made, and out of what.
     *
     * The clicked item is not always the ore that gets consumed: coal used on a
     * furnace smelts steel, which eats an iron ore the player never clicked.
     * Consuming the clicked item and then the recipe's extras -- which is what
     * both call sites used to do -- would take three coal and no iron.
     */
    public static final class Recipe {

        private final int oreId;
        private final ItemSmeltingDef def;

        private Recipe(int oreId, ItemSmeltingDef def) {
            this.oreId = oreId;
            this.def = def;
        }

        public int getOreId() {
            return this.oreId;
        }

        public ItemSmeltingDef getDef() {
            return this.def;
        }

        public int getBarId() {
            return this.def.getBarId();
        }

        public int getExp() {
            return this.def.getExp();
        }
    }

    /**
     * Works out which recipe an item is being smelted into.
     *
     * Iron ore has two: on its own it makes an iron bar, with coal it makes
     * steel. Jagex picks between them on how much coal is carried, and the
     * threshold is not the same in both places -- the furnace switches to steel
     * at a single coal (so one coal and an iron ore tells you the steel recipe
     * rather than quietly smelting iron), the spell needs the full two.
     *
     * Returns null if the item is not an ore.
     */
    public static Recipe recipeFor(Player player, int itemId, boolean furnace) {
        if (itemId == COAL || itemId == IRON_ORE) {
            int coal = player.getInventory().countId(COAL);
            boolean steel = itemId == COAL || coal >= (furnace ? 1 : 2);
            return new Recipe(IRON_ORE, EntityHandler.getItemSmeltingDef(steel ? IRON_ORE : IRON_ALONE));
        }
        ItemSmeltingDef def = EntityHandler.getItemSmeltingDef(itemId);
        return def == null ? null : new Recipe(itemId, def);
    }

    /**
     * Runs Jagex's checks in Jagex's order and tells the player about the first
     * one that fails. The order is load-bearing: smithing level comes first for
     * every metal, so a level 1 player holding no coal is told to come back at
     * 30 rather than told to go and find coal.
     */
    public static boolean canSmelt(Player player, Recipe recipe) {
        ItemSmeltingDef def = recipe.getDef();
        String metal = metal(def.getBarId());

        if (player.getCurStat(13) < def.getReqLevel()) {
            player.getActionSender().sendMessage(
                "@que@You need to be at least level-" + def.getReqLevel() + " smithing to smelt " + metal);
            /* Iron is the only metal with a second line, and it is the first
               wall most players hit: level 15 with nothing to do about it. */
            if (def.getBarId() == IRON_BAR) {
                player.getActionSender().sendMessage(
                    "@que@Practice your smithing using tin and copper to make bronze");
            }
            return false;
        }

        if (player.getInventory().countId(recipe.getOreId()) < 1) {
            player.getActionSender().sendMessage(shortOf(def, recipe.getOreId()));
            return false;
        }
        for (ReqOreDef reqOre : def.getReqOres()) {
            if (player.getInventory().countId(reqOre.getId()) < reqOre.getAmount()) {
                player.getActionSender().sendMessage(shortOf(def, reqOre.getId()));
                return false;
            }
        }
        return true;
    }

    /**
     * Takes the whole recipe out of the inventory, or nothing at all.
     *
     * A furnace takes three seconds, and the ore is only checked for at the
     * start. Everything has to still be there when the bar comes out, or the
     * player banks the coal mid-smelt and gets the steel for free.
     */
    public static boolean consume(Player player, Recipe recipe) {
        if (player.getInventory().countId(recipe.getOreId()) < 1) {
            return false;
        }
        for (ReqOreDef reqOre : recipe.getDef().getReqOres()) {
            if (player.getInventory().countId(reqOre.getId()) < reqOre.getAmount()) {
                return false;
            }
        }
        player.getInventory().remove(recipe.getOreId(), 1);
        for (ReqOreDef reqOre : recipe.getDef().getReqOres()) {
            player.getInventory().remove(reqOre.getId(), reqOre.getAmount());
        }
        return true;
    }

    /** What a furnace says while it works, before the bar comes out. */
    public static String furnaceNarration(Recipe recipe) {
        ItemSmeltingDef def = recipe.getDef();
        switch (def.getBarId()) {
        case BRONZE_BAR:
            return "@que@You smelt the copper and tin together in the furnace";
        case IRON_BAR:
            return "@que@You smelt the iron in the furnace";
        case STEEL_BAR:
            /* Named for the ore, not the bar -- there is no such thing as
               steel ore to place in a furnace. */
            return "@que@You place the iron and 2 heaps of coal into the furnace";
        default:
            break;
        }
        int coal = coalNeeded(def);
        if (coal > 0) {
            return "@que@You place the " + metal(def.getBarId()) + " and " + coal
                + " heaps of coal into the furnace";
        }
        return "@que@You place a lump of " + metal(def.getBarId()) + " in the furnace";
    }

    /** The furnace hands you the bar. */
    public static String retrieved(Recipe recipe) {
        return "@que@You retrieve a bar of " + metal(recipe.getBarId());
    }

    /** Superheat has no furnace to reach into, so the bar simply appears. */
    public static String made(Recipe recipe) {
        return "@que@You make a bar of " + metal(recipe.getBarId());
    }

    /** Iron is the one ore that can come out of a furnace as nothing at all. */
    public static boolean canFailToRefine(Recipe recipe) {
        return recipe.getDef().getBarId() == IRON_BAR;
    }

    public static String impure() {
        return "@que@The ore is too impure and you fail to refine it";
    }

    private static String shortOf(ItemSmeltingDef def, int missingId) {
        if (def.getBarId() == BRONZE_BAR) {
            /* Jagex names the metal, not the ore: "some copper", not "some
               copper ore". */
            return missingId == COPPER_ORE
                ? "@que@You also need some copper to make bronze"
                : "@que@You also need some tin to make bronze";
        }
        if (def.getBarId() == STEEL_BAR) {
            /* One line whichever half is missing, hyphen and all. */
            return "@que@You need 1 iron-ore and 2 coal to make steel";
        }
        return "@que@You need " + coalNeeded(def) + " heaps of coal to smelt " + metal(def.getBarId());
    }

    private static int coalNeeded(ItemSmeltingDef def) {
        for (ReqOreDef reqOre : def.getReqOres()) {
            if (reqOre.getId() == COAL) {
                return reqOre.getAmount();
            }
        }
        return 0;
    }

    /**
     * The metal a bar is made of, the way Jagex writes it in these messages.
     * Every bar is named "<metal> bar", so the item definitions carry this
     * already -- including "Runite bar", which is the only one capitalised and
     * which these lines spell lower case.
     */
    public static String metal(int barId) {
        String name = EntityHandler.getItemDef(barId).getName().toLowerCase();
        return name.endsWith(" bar") ? name.substring(0, name.length() - 4) : name;
    }
}
