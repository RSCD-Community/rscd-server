package org.rscdaemon.server.model;

import java.util.ArrayList;

import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.event.MiniEvent;

/**
 * Gnome cooking -- the seven cocktails of Blurberry's bar and the thirteen
 * dishes of Gianne's restaurant, plus the fruit-cutting and dough-moulding
 * that feed them.
 *
 * None of this existed. Every item and every npc was already in the def files
 * -- 833-888 and 896-917 are all present and correctly named -- but nothing
 * anywhere in the tree referenced a single one of them, so the whole of the
 * Grand Tree's first floor was scenery.
 *
 * HOW A RECIPE IS REPRESENTED
 *
 * Jagex gave gnome cooking no intermediate item ids. A half-built batta is
 * item 884 whether it has cheese on it or not, and a half-built cocktail is
 * item 853 whether it holds one garnish or four. So the progress cannot live
 * on the item; it lives on the player, as the ordered list of actions taken
 * so far -- see Player.getGnomeDish().
 *
 * After every action the list is matched as a prefix against all twenty
 * recipes. Exactly one match of full length finishes the dish. No match at all
 * means the player has gone off-recipe, and the two halves of the system part
 * company there:
 *
 *   - Cocktails spoil. "If ingredients are added in the wrong order, you will
 *     end up with an odd looking cocktail" -- item 867, which exists, has a
 *     drink command and a documented (-3 attack, -1 defense, -3 strength, no
 *     heal) effect. So a wrong step really does cost you the drink.
 *
 *   - Food refuses. The equivalent failure item for food, 903 "gnome batta"
 *     / "smells like pants", is recorded as unobtainable -- players never
 *     found a way to make one. So a wrong ingredient on a batta is turned
 *     away and nothing is consumed.
 *
 * ORDER WITHIN A STEP RUN
 *
 * The step lists below are transcribed from the two in-game books, which the
 * player can read, and are matched strictly in order. That is the documented
 * behaviour for cocktails. Where a book says "mix A and B", A then B is what
 * it says and what is required.
 */
public final class GnomeCooking {

    /* ------------------------------------------------------------ item ids */

    /* Vessels and bases. */
    public static final int COCKTAIL_GLASS = 833;   // empty
    public static final int GLASS_POURED = 853;     // holds the mix, no garnish yet
    public static final int GLASS_GARNISHED = 854;  // holds the mix and at least one garnish
    public static final int SHAKER = 834;
    public static final int ODD_COCKTAIL = 867;

    public static final int GIANNE_DOUGH = 881;
    public static final int BATTA_DOUGH = 880, BOWL_DOUGH = 882, CRUNCHIE_DOUGH = 883;
    public static final int BATTA = 884, BOWL = 885, CRUNCHIE = 900;
    public static final int BATTA_BURNT = 886, CRUNCHIE_BURNT = 887, BOWL_BURNT = 888;

    public static final int COCKTAIL_GUIDE = 851, COOK_BOOK = 899;

    /* Ingredients. */
    private static final int LEMON = 855, LEMON_SLICES = 856;
    private static final int ORANGE = 857, ORANGE_SLICES = 858;
    private static final int DICED_ORANGE = 859, DICED_LEMON = 860;
    private static final int PINEAPPLE_FRESH = 861, PINEAPPLE_CHUNKS = 862;
    private static final int PINEAPPLE_OLD = 748, PINEAPPLE_RING = 749;
    private static final int LIME = 863, LIME_CHUNKS = 864, LIME_SLICES = 865;
    private static final int WHISKY = 868, VODKA = 869, GIN = 870, BRANDY = 876;
    private static final int CREAM = 871, EQUA_LEAVES = 873;
    private static final int DWELLBERRIES = 765, CHOCOLATE_BAR = 337, CHOCOLATE_DUST = 772;
    private static final int MILK = 22, BUCKET = 21;
    private static final int CHEESE = 319, TOMATO = 320, ONION = 241, POTATO = 348;
    private static final int CABBAGE = 18, CABBAGE_DRAYNOR = 228;
    private static final int SWAMP_TOAD = 895, TOAD_LEGS = 896, KING_WORM = 897, GNOME_SPICE = 898;
    private static final int KNIFE = 13;

    /* Results. */
    private static final int FRUIT_BLAST = 866, DRUNK_DRAGON = 872, SGG = 874;
    private static final int CHOC_SATURDAY = 875, BLURBERRY_SPECIAL = 877;
    private static final int WIZARD_BLIZZARD = 878, PINEAPPLE_PUNCH = 879;

    private static final int CHEESE_TOM_BATTA = 901, TOAD_BATTA = 902, WORM_BATTA = 904;
    private static final int FRUIT_BATTA = 905, VEG_BATTA = 906;
    private static final int CHOC_BOMB = 907, VEGBALL = 908, WORM_HOLE = 909, TANGLED_TOADS = 910;
    private static final int CHOC_CRUNCHIES = 911, WORM_CRUNCHIES = 912;
    private static final int TOAD_CRUNCHIES = 913, SPICE_CRUNCHIES = 914;

    /* -------------------------------------------------------- action codes
     *
     * An action is either an item id (add that ingredient) or one of these
     * pseudo-ids, kept above the 1291-entry item table so the two can never
     * collide.
     */
    private static final int MOULD_BATTA = 10000;
    private static final int MOULD_BOWL = 10001;
    private static final int MOULD_CRUNCHIE = 10002;
    private static final int BAKE = 10010;
    private static final int POUR = 10020;
    private static final int HEAT = 10021;

    /* ----------------------------------------------------------- the table */

    /**
     * A recipe: the finished item, the cooking level it needs, and the ordered
     * actions that make it.
     */
    public static final class Recipe {
        public final int result;
        public final int level;
        public final boolean drink;
        public final int[] steps;

        private Recipe(int result, int level, boolean drink, int[] steps) {
            this.result = result;
            this.level = level;
            this.drink = drink;
            this.steps = steps;
        }
    }

    private static Recipe cocktail(int result, int[] steps) {
        return new Recipe(result, 1, true, steps);
    }

    private static Recipe dish(int result, int level, int[] steps) {
        return new Recipe(result, level, false, steps);
    }

    /*
     * The cocktails, transcribed from Transcript:Gnome cocktail guide -- the
     * seven interface pages the player reads out of item 851. All are level 1
     * and give no cooking experience; the experience for these comes from
     * Blurberry paying you for an order, not from the mixing.
     */
    private static final Recipe[] COCKTAILS = new Recipe[] {

        /* "Mix the juice of one lemon, one orange and one pineapple in the
            shaker / Pour into glass and top with slices of lemon." */
        cocktail(FRUIT_BLAST, new int[] {
            LEMON, ORANGE, PINEAPPLE_FRESH, POUR, LEMON_SLICES }),

        /* "mix the juice of two pineapples with the juice of one lemon and one
            orange / pour the mix into a glass and add diced pineapple followed
            by diced lime / top drink with one slice of lime" */
        cocktail(PINEAPPLE_PUNCH, new int[] {
            PINEAPPLE_FRESH, PINEAPPLE_FRESH, LEMON, ORANGE,
            POUR, PINEAPPLE_CHUNKS, LIME_CHUNKS, LIME_SLICES }),

        /* "Mix vodka with gin and dwellberry juice / Pour the mixture into a
            glass and add a diced pineapple. Next add a generous portion of
            cream / Heat the drink briefly in a warm oven.. yum." */
        cocktail(DRUNK_DRAGON, new int[] {
            VODKA, GIN, DWELLBERRIES, POUR, PINEAPPLE_CHUNKS, CREAM, HEAT }),

        /* "Mix vodka with the juice of three limes and pour into a glass /
            sprinkle equa leaves over the top of the drink / Finally add a
            slice of lime to finish the drink" */
        cocktail(SGG, new int[] {
            VODKA, LIME, LIME, LIME, POUR, EQUA_LEAVES, LIME_SLICES }),

        /* "Mix together whiskey, milk, equa leaves / Pour mixture into a glass
            add some chocolate and briefly heat in the oven / Then add a
            generous helping of cream / Finish of the drink with sprinkled
            chocolate dust" */
        cocktail(CHOC_SATURDAY, new int[] {
            WHISKY, MILK, EQUA_LEAVES, POUR, CHOCOLATE_BAR, HEAT, CREAM, CHOCOLATE_DUST }),

        /* "Mix together vodka, gin and brandy / Add to this the juice of two
            lemons and one orange and pour into the glass / next add to the
            glass orange chunks and then lemon chunks / Finish of with one lime
            slice and then add a sprinkling of equa leaves" */
        cocktail(BLURBERRY_SPECIAL, new int[] {
            VODKA, GIN, BRANDY, LEMON, LEMON, ORANGE,
            POUR, DICED_ORANGE, DICED_LEMON, LIME_SLICES, EQUA_LEAVES }),

        /* "thoroughly mix together the juice of one pinapple, one orange, one
            lemon and one lime / Add to this two measures of vodka and one
            measure of gin / Pour the mixture into a glass, top with pineapple
            chunks and then add slices of lime" */
        cocktail(WIZARD_BLIZZARD, new int[] {
            PINEAPPLE_FRESH, ORANGE, LEMON, LIME, VODKA, VODKA, GIN,
            POUR, PINEAPPLE_CHUNKS, LIME_SLICES }),
    };

    /*
     * The dishes, transcribed from Transcript:Gianne cook book -- the thirteen
     * interface pages the player reads out of item 899.
     *
     * Experience is not listed here because it is not paid on the finished
     * dish: 25 comes from moulding the gianne dough and 30 from each
     * successful bake, which is exactly how the wiki's per-food totals of 55
     * (one bake) and 85 (two bakes) are made up.
     */
    private static final Recipe[] DISHES = new Recipe[] {

        /* -- gnomebattas, level 25 -- */

        /* "Bake the gnome batta, once removed place cheese and then tomato on
            top / Place batta in oven once more until cheese has melted, remove
            and top with equaleaves." */
        dish(CHEESE_TOM_BATTA, 25, new int[] {
            MOULD_BATTA, BAKE, CHEESE, TOMATO, BAKE, EQUA_LEAVES }),

        /* "Bake the gnome batta, mix some equa leaves with your toad's legs
            and then add some gnomespice / Place the seasoned toads legs on the
            batta, add cheese and bake once more." */
        dish(TOAD_BATTA, 25, new int[] {
            MOULD_BATTA, BAKE, EQUA_LEAVES, TOAD_LEGS, GNOME_SPICE, CHEESE, BAKE }),

        /* "Bake the gnome batta, mix some gnomespice with a king worm / Place
            the seasoned worm on the batta, add cheese and bake once more /
            Remove from oven and finish with a sprinkle of equaleaves...yum." */
        dish(WORM_BATTA, 25, new int[] {
            MOULD_BATTA, BAKE, GNOME_SPICE, KING_WORM, CHEESE, BAKE, EQUA_LEAVES }),

        /* "Bake the gnome batta and remove from oven, then lay four sprigs of
            equa leaves on the batta and bake once more / Add chunks of
            pineapple, orange and lime then finish with a sprinkle of
            gnomespice." */
        dish(FRUIT_BATTA, 25, new int[] {
            MOULD_BATTA, BAKE, EQUA_LEAVES, EQUA_LEAVES, EQUA_LEAVES, EQUA_LEAVES,
            BAKE, PINEAPPLE_CHUNKS, DICED_ORANGE, LIME_CHUNKS, GNOME_SPICE }),

        /* "Bake the gnome batta then add an onion, two tomatos, one cabbage
            and some dwellberrys, next place the batta in the oven / Add some
            cheese and place in the oven once more / To finish add a sprinkle
            of equa leaves." */
        dish(VEG_BATTA, 25, new int[] {
            MOULD_BATTA, BAKE, ONION, TOMATO, TOMATO, CABBAGE, DWELLBERRIES,
            BAKE, CHEESE, BAKE, EQUA_LEAVES }),

        /* -- gnomebakes (bowls), level 30 -- */

        /* "Bake the gnomebowl / Add to the gnomebowl four bars of chocolae and
            one sprig of equaleaves / Bake the gnome bowl in an oven / Next add
            two portions of cream and finish with a sprinkle of chocolate
            dust." */
        dish(CHOC_BOMB, 30, new int[] {
            MOULD_BOWL, BAKE, CHOCOLATE_BAR, CHOCOLATE_BAR, CHOCOLATE_BAR, CHOCOLATE_BAR,
            EQUA_LEAVES, BAKE, CREAM, CREAM, CHOCOLATE_DUST }),

        /* "Bake the gnomebowl / Add two onions,two potatoes and some gnome
            spice / Bake the gnomebowl once more / To finish sprinkle with
            equaleaves" */
        dish(VEGBALL, 30, new int[] {
            MOULD_BOWL, BAKE, ONION, ONION, POTATO, POTATO, GNOME_SPICE,
            BAKE, EQUA_LEAVES }),

        /* "Bake the gnomebowl / Add six king worms, two onions and some gnome
            spice / Bake the gnomebowl once more / To finish sprinkle with
            equaleaves" */
        dish(WORM_HOLE, 30, new int[] {
            MOULD_BOWL, BAKE, KING_WORM, KING_WORM, KING_WORM, KING_WORM, KING_WORM,
            KING_WORM, ONION, ONION, GNOME_SPICE, BAKE, EQUA_LEAVES }),

        /* "Bake the gnomebowl / Add two portions of cheese, five pairs of
            toad's legs, two sprigs of equa leaves, some dwell berries and two
            sprinkle's of gnomespice / Bake the gnomebowl once more" */
        dish(TANGLED_TOADS, 30, new int[] {
            MOULD_BOWL, BAKE, CHEESE, CHEESE,
            TOAD_LEGS, TOAD_LEGS, TOAD_LEGS, TOAD_LEGS, TOAD_LEGS,
            EQUA_LEAVES, EQUA_LEAVES, DWELLBERRIES, GNOME_SPICE, GNOME_SPICE, BAKE }),

        /* -- gnomecrunchies, level 15 --
         *
         * These are the only recipes where ingredients go into the gianne
         * dough BEFORE it is shaped, which is why the mould step is not first.
         */

        /* "Mix some gnome spice and two bars of chocolate with the Gianne
            dough / Use dough to make gnomecrunchie dough / Bake in oven / Add
            of sprinkle of chocolate dust" */
        dish(CHOC_CRUNCHIES, 15, new int[] {
            GNOME_SPICE, CHOCOLATE_BAR, CHOCOLATE_BAR, MOULD_CRUNCHIE, BAKE, CHOCOLATE_DUST }),

        /* "Mix some gnome spice, two king worms and some equa leaves with the
            Gianne dough / Use dough to make gnomecrunchie dough / Bake in oven
            / Add of sprinkle of gnome spice" */
        dish(WORM_CRUNCHIES, 15, new int[] {
            GNOME_SPICE, KING_WORM, KING_WORM, EQUA_LEAVES, MOULD_CRUNCHIE, BAKE, GNOME_SPICE }),

        /* "Mix some gnome spice and two pair's of toads legs with the Gianne
            dough / Use dough to make gnomecrunchie dough / Bake in oven / Add
            of sprinkle of equa leaves" */
        dish(TOAD_CRUNCHIES, 15, new int[] {
            GNOME_SPICE, TOAD_LEGS, TOAD_LEGS, MOULD_CRUNCHIE, BAKE, EQUA_LEAVES }),

        /* "Mix three sprinkles of gnomespice and two sprigs of equa leaves
            with Gianne dough / Use dough to make gnomecrunchie dough / Bake in
            oven / Add of sprinkle of gnome spice" */
        dish(SPICE_CRUNCHIES, 15, new int[] {
            GNOME_SPICE, GNOME_SPICE, GNOME_SPICE, EQUA_LEAVES, EQUA_LEAVES,
            MOULD_CRUNCHIE, BAKE, GNOME_SPICE }),
    };

    private GnomeCooking() {
    }

    /* --------------------------------------------------------- the matcher */

    /**
     * Every recipe in the given table that starts with the actions taken so
     * far.
     */
    private static ArrayList<Recipe> matching(Recipe[] table, ArrayList<Integer> so_far) {
        ArrayList<Recipe> out = new ArrayList<Recipe>();
        for (int r = 0; r < table.length; r++) {
            Recipe recipe = table[r];
            if (recipe.steps.length < so_far.size()) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < so_far.size(); i++) {
                if (recipe.steps[i] != so_far.get(i).intValue()) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(recipe);
            }
        }
        return out;
    }

    /** The one recipe the actions so far have completed, or null. */
    private static Recipe finished(ArrayList<Recipe> live, int taken) {
        for (int i = 0; i < live.size(); i++) {
            if (live.get(i).steps.length == taken) {
                return live.get(i);
            }
        }
        return null;
    }

    /**
     * The player's recipe in progress, forgetting it first if whatever it was
     * being built on has gone.
     *
     * A dish always has a physical token to go with the recorded steps -- the
     * shaker before the pour, the glass after it, the gianne dough before the
     * mould and the shaped base after it. If the token has been eaten, dropped
     * or banked then the steps mean nothing, and leaving them behind would
     * block the next dish rather than the old one.
     */
    private static ArrayList<Integer> dishOf(Player player) {
        ArrayList<Integer> dish = player.getGnomeDish();
        if (dish == null) {
            dish = new ArrayList<Integer>();
            player.setGnomeDish(dish);
            return dish;
        }
        if (dish.isEmpty()) {
            return dish;
        }
        boolean shaped = dish.contains(Integer.valueOf(POUR))
                || dish.contains(Integer.valueOf(MOULD_BATTA))
                || dish.contains(Integer.valueOf(MOULD_BOWL))
                || dish.contains(Integer.valueOf(MOULD_CRUNCHIE));
        boolean lost = shaped
                ? !holdingUnfinished(player)
                : player.getInventory().countId(GIANNE_DOUGH) < 1
                        && player.getInventory().countId(SHAKER) < 1;
        if (lost) {
            dish = new ArrayList<Integer>();
            player.setGnomeDish(dish);
        }
        return dish;
    }

    /**
     * True while the player is carrying something half-made. Gianne's rule --
     * one unfinished dish at a time -- is enforced against this.
     */
    public static boolean holdingUnfinished(Player player) {
        Inventory inv = player.getInventory();
        return inv.countId(BATTA_DOUGH) > 0 || inv.countId(BOWL_DOUGH) > 0
                || inv.countId(CRUNCHIE_DOUGH) > 0 || inv.countId(BATTA) > 0
                || inv.countId(BOWL) > 0 || inv.countId(CRUNCHIE) > 0
                || inv.countId(GLASS_POURED) > 0 || inv.countId(GLASS_GARNISHED) > 0;
    }

    /**
     * Forget any half-made dish. Called when the work item leaves the
     * inventory by any route -- eaten, burnt, spoiled or finished.
     */
    public static void clear(Player player) {
        player.setGnomeDish(null);
    }

    /* ------------------------------------------------------------ messages */

    private static void mes(Player player, String text) {
        player.getActionSender().sendMessage("@pnk@ " + text);
    }

    private static void grey(Player player, String text) {
        player.getActionSender().sendMessage("@gry@ " + text);
    }

    private static String nameOf(int id) {
        return EntityHandler.getItemDef(id).getName();
    }

    /* ------------------------------------------------------- the cocktails */

    private static boolean isCocktailIngredient(int id) {
        for (int r = 0; r < COCKTAILS.length; r++) {
            int[] steps = COCKTAILS[r].steps;
            for (int i = 0; i < steps.length; i++) {
                if (steps[i] == id) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * An ingredient going into the shaker. Everything before the pour happens
     * here, and a wrong ingredient is mixed in and ruins the drink rather than
     * being refused -- you cannot pick a lemon back out of a shaker.
     */
    public static boolean mixIntoShaker(final Player player, final InvItem ingredient) {
        if (player.getInventory().countId(SHAKER) < 1) {
            return false;
        }
        if (!isCocktailIngredient(ingredient.getID())) {
            return false;
        }
        ArrayList<Integer> dish = dishOf(player);
        /* Pouring is what empties the shaker. If the glass is already poured,
           the shaker has moved on and this is a fresh mix. */
        if (dish.contains(Integer.valueOf(POUR)) || holdingUnfinished(player)) {
            grey(player, "you need to finish, eat or drop the unfinished dish you hold");
            grey(player, "before you can make another - giannes rules");
            return true;
        }
        if (player.getInventory().remove(ingredient) < 0) {
            return true;
        }
        dish.add(Integer.valueOf(ingredient.getID()));
        if (ingredient.getID() == MILK) {
            player.getInventory().add(new InvItem(BUCKET, 1));
        }
        player.getActionSender().sendInventory();
        mes(player, "You mix the " + nameOf(ingredient.getID()) + " into the cocktail shaker");
        if (matching(COCKTAILS, dish).isEmpty()) {
            /* Not refused, and not announced either. The player finds out when
               they pour it. */
            dish.add(Integer.valueOf(-1));
        }
        return true;
    }

    /** Emptying the shaker into a glass. */
    public static boolean pourShaker(final Player player) {
        if (player.getInventory().countId(SHAKER) < 1) {
            grey(player, "You need a cocktail shaker to pour from");
            return true;
        }
        if (player.getInventory().countId(COCKTAIL_GLASS) < 1) {
            grey(player, "You need an empty cocktail glass to pour into");
            return true;
        }
        ArrayList<Integer> dish = dishOf(player);
        if (dish.isEmpty()) {
            grey(player, "There's nothing in the shaker");
            return true;
        }
        if (dish.contains(Integer.valueOf(POUR))) {
            grey(player, "you need to finish, eat or drop the unfinished dish you hold");
            grey(player, "before you can make another - giannes rules");
            return true;
        }
        if (player.getInventory().remove(COCKTAIL_GLASS, 1) < 0) {
            return true;
        }
        dish.add(Integer.valueOf(POUR));
        mes(player, "You pour the mixture into the cocktail glass");
        ArrayList<Recipe> live = matching(COCKTAILS, dish);
        if (live.isEmpty()) {
            spoil(player);
            return true;
        }
        player.getInventory().add(new InvItem(GLASS_POURED, 1));
        player.getActionSender().sendInventory();
        return true;
    }

    /** A garnish going onto a poured glass. */
    private static boolean garnishGlass(final Player player, final InvItem glass, final InvItem garnish) {
        if (!isCocktailIngredient(garnish.getID())) {
            /* Nothing in any of the seven recipes, so it is not a wrong step
               -- it is not a step at all. Refused, and nothing is taken. */
            grey(player, "That's nothing to do with any cocktail");
            return true;
        }
        ArrayList<Integer> dish = dishOf(player);
        if (!dish.contains(Integer.valueOf(POUR))) {
            /* A glass that survived a logout, or one bought from the barman.
               There is no way to know what is in it, so nothing can be added. */
            grey(player, "You don't remember what's in this glass");
            return true;
        }
        if (player.getInventory().remove(garnish) < 0) {
            return true;
        }
        dish.add(Integer.valueOf(garnish.getID()));
        player.getActionSender().sendInventory();
        mes(player, "You add the " + nameOf(garnish.getID()) + " to the cocktail");
        ArrayList<Recipe> live = matching(COCKTAILS, dish);
        if (live.isEmpty()) {
            spoil(player);
            return true;
        }
        Recipe done = finished(live, dish.size());
        if (done != null) {
            complete(player, done);
            return true;
        }
        /* The glass has a garnish in it now, and Jagex gave the half-made
           cocktail two sprites rather than one. 853 is the poured mix and 854
           is the mix once something has been added to it -- our reading, since
           both ids carry the same name and the same examine and nothing
           records which is which. */
        if (glass.getID() == GLASS_POURED) {
            player.getInventory().remove(GLASS_POURED, 1);
            player.getInventory().add(new InvItem(GLASS_GARNISHED, 1));
            player.getActionSender().sendInventory();
        }
        return true;
    }

    /** Heating a cocktail over a range -- the drunk dragon and choc saturday. */
    public static boolean heatCocktail(final Player player, final InvItem glass) {
        if (glass.getID() != GLASS_POURED && glass.getID() != GLASS_GARNISHED) {
            return false;
        }
        ArrayList<Integer> dish = dishOf(player);
        if (!dish.contains(Integer.valueOf(POUR))) {
            grey(player, "You don't remember what's in this glass");
            return true;
        }
        dish.add(Integer.valueOf(HEAT));
        mes(player, "You warm the drink over the range");
        ArrayList<Recipe> live = matching(COCKTAILS, dish);
        if (live.isEmpty()) {
            spoil(player);
            return true;
        }
        Recipe done = finished(live, dish.size());
        if (done != null) {
            complete(player, done);
        }
        return true;
    }

    private static void spoil(Player player) {
        player.getInventory().remove(GLASS_POURED, 1);
        player.getInventory().remove(GLASS_GARNISHED, 1);
        player.getInventory().add(new InvItem(ODD_COCKTAIL, 1));
        player.getActionSender().sendInventory();
        grey(player, "That doesn't look right");
        clear(player);
    }

    private static void complete(Player player, Recipe recipe) {
        player.getInventory().remove(GLASS_POURED, 1);
        player.getInventory().remove(GLASS_GARNISHED, 1);
        player.getInventory().add(new InvItem(recipe.result, 1));
        player.getActionSender().sendInventory();
        mes(player, "You make a " + nameOf(recipe.result));
        clear(player);
    }

    /* ------------------------------------------------------------ the food */

    private static boolean isDishIngredient(int id) {
        for (int r = 0; r < DISHES.length; r++) {
            int[] steps = DISHES[r].steps;
            for (int i = 0; i < steps.length; i++) {
                if (steps[i] == id) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Cabbage has two item ids and both are cabbage. */
    private static int normalise(int id) {
        if (id == CABBAGE_DRAYNOR) {
            return CABBAGE;
        }
        if (id == PINEAPPLE_OLD) {
            return PINEAPPLE_FRESH;
        }
        return id;
    }

    /**
     * An ingredient going onto a dish -- either the unshaped gianne dough (the
     * crunchie recipes) or a baked base.
     */
    private static boolean addToDish(final Player player, final InvItem base, final InvItem ingredient) {
        int id = normalise(ingredient.getID());
        if (!isDishIngredient(id)) {
            return false;
        }
        ArrayList<Integer> dish = dishOf(player);
        if (base.getID() == GIANNE_DOUGH && dish.isEmpty()
                && player.getCurStat(7) < 15) {
            grey(player, "You need a cooking level of 15 to prepare gnome food");
            return true;
        }
        if (base.getID() != GIANNE_DOUGH && dish.isEmpty()) {
            /* A baked base with no recorded recipe -- bought, dropped or
               carried across a logout. Nothing can be built on it. */
            grey(player, "You don't remember what this was going to be");
            return true;
        }
        ArrayList<Integer> attempt = new ArrayList<Integer>(dish);
        attempt.add(Integer.valueOf(id));
        ArrayList<Recipe> live = matching(DISHES, attempt);
        if (live.isEmpty()) {
            /* Food does not spoil -- see the class comment. Nothing is taken. */
            grey(player, "That's not the next thing any of aluft's recipes needs");
            return true;
        }
        if (player.getInventory().remove(ingredient) < 0) {
            return true;
        }
        dish.add(Integer.valueOf(id));
        player.getActionSender().sendInventory();
        mes(player, "You add the " + nameOf(ingredient.getID()) + " to the "
                + nameOf(base.getID()));
        Recipe done = finished(live, dish.size());
        if (done != null) {
            player.getInventory().remove(base.getID(), 1);
            player.getInventory().add(new InvItem(done.result, 1));
            player.getActionSender().sendInventory();
            mes(player, "You make a " + nameOf(done.result));
            clear(player);
        }
        return true;
    }

    /**
     * Shaping gianne dough. The "mould" command on item 881.
     */
    public static boolean mould(final Player player, final InvItem dough) {
        if (dough.getID() != GIANNE_DOUGH) {
            return false;
        }
        if (holdingUnfinished(player)) {
            grey(player, "you need to finish, eat or drop the unfinished dish you hold");
            grey(player, "before you can make another - giannes rules");
            return true;
        }
        final String[] options = new String[] {
            "gnomebatta dough", "gnomebowl dough", "gnomecrunchie dough", "nothing" };
        player.setMenuHandler(new MenuHandler(options) {

            public void handleReply(int option, String reply) {
                if (this.owner.isBusy() || option < 0 || option > 2) {
                    return;
                }
                int shaped, mark, level;
                switch (option) {
                    case 0:
                        shaped = BATTA_DOUGH;
                        mark = MOULD_BATTA;
                        level = 25;
                        break;
                    case 1:
                        shaped = BOWL_DOUGH;
                        mark = MOULD_BOWL;
                        level = 30;
                        break;
                    default:
                        shaped = CRUNCHIE_DOUGH;
                        mark = MOULD_CRUNCHIE;
                        level = 15;
                        break;
                }
                if (this.owner.getCurStat(7) < level) {
                    grey(this.owner, "You need a cooking level of " + level + " to make that");
                    return;
                }
                ArrayList<Integer> dish = dishOf(this.owner);
                ArrayList<Integer> attempt = new ArrayList<Integer>(dish);
                attempt.add(Integer.valueOf(mark));
                if (matching(DISHES, attempt).isEmpty()) {
                    grey(this.owner, "That's not the next thing any of aluft's recipes needs");
                    return;
                }
                if (this.owner.getInventory().remove(GIANNE_DOUGH, 1) < 0) {
                    return;
                }
                dish.add(Integer.valueOf(mark));
                this.owner.getInventory().add(new InvItem(shaped, 1));
                this.owner.getActionSender().sendInventory();
                mes(this.owner, "You mould the dough into a " + nameOf(shaped));
                /* 25 for the shaping and 30 for each bake, which is what makes
                   up the 55 and 85 totals the wiki records per finished dish. */
                this.owner.incExp(7, 25, true);
                this.owner.getActionSender().sendStat(7);
            }
        });
        player.getActionSender().sendMenu(options);
        return true;
    }

    /**
     * Called from the range handler after the generic cooking code has cooked
     * or burnt one of the six gnome dough/base items, to record the bake step.
     */
    public static void onBaked(Player player, int itemId, boolean burnt) {
        int mark;
        switch (itemId) {
            case BATTA_DOUGH:
            case BOWL_DOUGH:
            case CRUNCHIE_DOUGH:
            case BATTA:
            case BOWL:
            case CRUNCHIE:
                mark = BAKE;
                break;
            default:
                return;
        }
        if (burnt) {
            clear(player);
            return;
        }
        ArrayList<Integer> dish = dishOf(player);
        if (dish.isEmpty()) {
            /* Re-baking a base that is not part of anything. Jagex allowed it
               -- "you may continuously place the gnomebatta into the oven and
               it will not burn" -- and it is left allowed here. */
            return;
        }
        ArrayList<Integer> attempt = new ArrayList<Integer>(dish);
        attempt.add(Integer.valueOf(mark));
        ArrayList<Recipe> live = matching(DISHES, attempt);
        if (live.isEmpty()) {
            return;
        }
        dish.add(Integer.valueOf(mark));
        Recipe done = finished(live, dish.size());
        if (done != null) {
            int base = itemId == BATTA_DOUGH || itemId == BATTA ? BATTA
                    : itemId == BOWL_DOUGH || itemId == BOWL ? BOWL : CRUNCHIE;
            player.getInventory().remove(base, 1);
            player.getInventory().add(new InvItem(done.result, 1));
            player.getActionSender().sendInventory();
            mes(player, "You make a " + nameOf(done.result));
            clear(player);
        }
    }

    /* -------------------------------------------------------- fruit and toad */

    /**
     * A knife on a fruit. Each of the four can be sliced or diced, and the two
     * give different items -- lemon slices go into a fruit blast where diced
     * lemon goes into a blurberry special.
     */
    public static boolean cutFruit(final Player player, final InvItem fruit) {
        final int sliced, diced;
        switch (fruit.getID()) {
            case LEMON:
                sliced = LEMON_SLICES;
                diced = DICED_LEMON;
                break;
            case ORANGE:
                sliced = ORANGE_SLICES;
                diced = DICED_ORANGE;
                break;
            case LIME:
                sliced = LIME_SLICES;
                diced = LIME_CHUNKS;
                break;
            case PINEAPPLE_FRESH:
            case PINEAPPLE_OLD:
                sliced = PINEAPPLE_RING;
                diced = PINEAPPLE_CHUNKS;
                break;
            default:
                return false;
        }
        final int cutting = fruit.getID();
        final String what = nameOf(cutting);
        final String[] options = new String[] {
            "slice " + what, "dice " + what, "leave it whole" };
        player.setMenuHandler(new MenuHandler(options) {

            public void handleReply(int option, String reply) {
                if (this.owner.isBusy() || option < 0 || option > 1) {
                    return;
                }
                if (this.owner.getInventory().countId(KNIFE) < 1) {
                    return;
                }
                if (this.owner.getInventory().remove(cutting, 1) < 0) {
                    return;
                }
                /* A pineapple slices into four rings; everything else, and
                   dicing anything, gives one. */
                int made = option == 0 ? sliced : diced;
                int amount = option == 0 && made == PINEAPPLE_RING ? 4 : 1;
                this.owner.getInventory().add(new InvItem(made, amount));
                this.owner.getActionSender().sendInventory();
                mes(this.owner, "You cut the " + what + " into " + nameOf(made));
            }
        });
        player.getActionSender().sendMenu(options);
        return true;
    }

    /** The "remove legs" command on a swamp toad. */
    public static boolean removeToadLegs(final Player player, final InvItem toad) {
        if (toad.getID() != SWAMP_TOAD) {
            return false;
        }
        if (player.getInventory().remove(toad) < 0) {
            return true;
        }
        player.getInventory().add(new InvItem(TOAD_LEGS, 1));
        player.getActionSender().sendInventory();
        mes(player, "You remove the legs from the swamp toad");
        return true;
    }

    /* ---------------------------------------------------------- the entries */

    /**
     * The one hook InvUseOnItem needs. Returns true if the pairing was gnome
     * cooking and has been dealt with.
     */
    public static boolean useOnItem(Player player, InvItem a, InvItem b) {
        if (a.getID() == KNIFE && cutFruit(player, b)) {
            return true;
        }
        if (b.getID() == KNIFE && cutFruit(player, a)) {
            return true;
        }
        if (a.getID() == SHAKER && b.getID() == COCKTAIL_GLASS) {
            return pourShaker(player);
        }
        if (b.getID() == SHAKER && a.getID() == COCKTAIL_GLASS) {
            return pourShaker(player);
        }
        if (a.getID() == SHAKER && mixIntoShaker(player, b)) {
            return true;
        }
        if (b.getID() == SHAKER && mixIntoShaker(player, a)) {
            return true;
        }
        if (a.getID() == GLASS_POURED || a.getID() == GLASS_GARNISHED) {
            return garnishGlass(player, a, b);
        }
        if (b.getID() == GLASS_POURED || b.getID() == GLASS_GARNISHED) {
            return garnishGlass(player, b, a);
        }
        if (isBase(a.getID())) {
            return addToDish(player, a, b);
        }
        if (isBase(b.getID())) {
            return addToDish(player, b, a);
        }
        return false;
    }

    private static boolean isBase(int id) {
        return id == GIANNE_DOUGH || id == BATTA || id == BOWL || id == CRUNCHIE;
    }

    /**
     * Drinking a half-made cocktail. Jagex left the drink command on both
     * half-made glasses, so it has to do something; it costs you the mix and
     * gives the glass back.
     */
    public static boolean drinkUnfinished(final Player player, final InvItem glass) {
        if (glass.getID() != GLASS_POURED && glass.getID() != GLASS_GARNISHED) {
            return false;
        }
        if (player.getInventory().remove(glass) < 0) {
            return true;
        }
        player.getInventory().add(new InvItem(COCKTAIL_GLASS, 1));
        player.getActionSender().sendInventory();
        mes(player, "You drink the unfinished cocktail");
        clear(player);
        return true;
    }

    /* ------------------------------------------------------------ the books
     *
     * Both books are read straight out of the inventory and open a menu of
     * recipes; picking one prints its page. Every line below is verbatim from
     * Transcript:Gnome cocktail guide and Transcript:Gianne cook book,
     * misspellings included -- "chocolae", "pinapple", "Add of sprinkle" and
     * the rest are Jagex's.
     */

    private static final String[] COCKTAIL_PAGE_NAMES = new String[] {
        "fruit blast", "pineapple punch", "drunkdragon", "sgg",
        "choc saturday", "blurberry special", "wizard blizzard" };

    private static final String[][] COCKTAIL_PAGES = new String[][] {
        { "Fruit blast",
          "Mix the juice of one lemon, one orange and one pineapple in the",
          "shaker",
          "Pour into glass and top with slices of lemon." },
        { "Pineapple Punch",
          "mix the juice of two pineapples with the juice of one lemon and",
          "one orange",
          "pour the mix into a glass and add diced pineapple followed by",
          "diced lime",
          "top drink with one slice of lime" },
        { "Drunk Dragon",
          "Mix vodka with gin and dwellberry juice",
          "Pour the mixture into a glass and add a diced pineapple.Next add",
          "a generous portion of cream",
          "Heat the drink briefly in a warm oven.. yum." },
        { "s g g - short green guy",
          "Mix vodka with the juice of three limes and pour into a glass",
          "sprinkle equa leaves over the top of the drink",
          "Finally add a slice of lime to finish the drink" },
        { "Choc Saturday",
          "Mix together whiskey, milk, equa leaves",
          "Pour mixture into a glass add some chocolate and briefly heat in",
          "the oven",
          "Then add a generous helping of cream",
          "Finish of the drink with sprinkled chocolate dust" },
        { "Blurberry Special",
          "Mix together vodka, gin and brandy",
          "Add to this the juice of two lemons and one orange and pour into",
          "the glass",
          "next add to the glass orange chunks and then lemon chunks",
          "Finish of with one lime slice and then add a sprinkling of equa",
          "leaves" },
        { "Wizard Blizzard",
          "thoroughly mix together the juice of one pinapple, one orange,",
          "one lemon and one lime",
          "Add to this two measures of vodka and one measure of gin",
          "Pour the mixture into a glass, top with pineapple chunks and",
          "then add slices of lime" },
    };

    private static final String[] DISH_PAGE_NAMES = new String[] {
        "cheese and tomato batta", "toad batta", "worm batta", "fruit batta", "veg batta",
        "choc bomb", "veg ball", "wormhole", "tangled toads legs",
        "choc crunchies", "worm crunchies", "toad crunchies", "spice crunchies" };

    private static final String[][] DISH_PAGES = new String[][] {
        { "Cheese and tomato batta",
          "Make some gnome batta dough from the Gianne dough",
          "Bake the gnome batta, once removed place cheese and then tomato on top",
          "Place batta in oven once more until cheese has melted, remove and top with equaleaves." },
        { "Toad batta",
          "Make some gnome batta dough from the Gianne dough",
          "Bake the gnome batta, mix some equa leaves with your toad's legs and then add some gnomespice",
          "Place the seasoned toads legs on the batta, add cheese and bake once more." },
        { "Worm batta",
          "Make some gnome batta dough from the Gianne dough",
          "Bake the gnome batta, mix some gnomespice with a king worm",
          "Place the seasoned worm on the batta, add cheese and bake once more",
          "Remove from oven and finish with a sprinkle of equaleaves...yum." },
        { "Fruit batta",
          "Make some gnome batta dough from the Gianne dough",
          "Bake the gnome batta and remove from oven, then lay four sprigs of equa leaves on the batta and bake once more",
          "Add chunks of pineapple, orange and lime then finish with a sprinkle of gnomespice." },
        { "Veg Batta",
          "Make some gnome batta dough from the Gianne dough",
          "Bake the gnome batta then add an onion, two tomatos, one cabbage and some dwellberrys, next place the batta in the oven",
          "Add some cheese and place in the oven once more",
          "To finish add a sprinkle of equa leaves." },
        { "Choc bomb",
          "Make some gnomebowl dough from the Gianne dough",
          "Bake the gnomebowl",
          "Add to the gnomebowl four bars of chocolae and one sprig of equaleaves",
          "Bake the gnome bowl in an oven",
          "Next add two portions of cream and finish with a sprinkle of chocolate dust." },
        { "Vegball",
          "Make some gnomebowl dough from the Gianne dough",
          "Bake the gnomebowl",
          "Add two onions,two potatoes and some gnome spice",
          "Bake the gnomebowl once more",
          "To finish sprinkle with equaleaves" },
        { "Worm hole",
          "Make some gnomebowl dough from the Gianne dough",
          "Bake the gnomebowl",
          "Add six king worms, two onions and some gnome spice",
          "Bake the gnomebowl once more",
          "To finish sprinkle with equaleaves" },
        { "Tangled toads legs",
          "Make some gnomebowl dough from the Gianne dough",
          "Bake the gnomebowl",
          "Add two portions of cheese, five pairs of toad's legs, two sprigs of equa leaves, some dwell berries and two sprinkle's of gnomespice",
          "Bake the gnomebowl once more" },
        { "choc crunchies",
          "Mix some gnome spice and two bars of chocolate with the Gianne dough",
          "Use dough to make gnomecrunchie dough",
          "Bake in oven",
          "Add of sprinkle of chocolate dust" },
        { "worm crunchies",
          "Mix some gnome spice, two king worms and some equa leaves with the Gianne dough",
          "Use dough to make gnomecrunchie dough",
          "Bake in oven",
          "Add of sprinkle of gnome spice" },
        { "toad crunchies",
          "Mix some gnome spice and two pair's of toads legs with the Gianne dough",
          "Use dough to make gnomecrunchie dough",
          "Bake in oven",
          "Add of sprinkle of equa leaves" },
        { "spice crunchies",
          "Mix three sprinkles of gnomespice and two sprigs of equa leaves with Gianne dough",
          "Use dough to make gnomecrunchie dough",
          "Bake in oven",
          "Add of sprinkle of gnome spice" },
    };

    /** The cocktail guide -- two lines, then alcoholic / non alcoholic. */
    public static boolean readCocktailGuide(final Player player) {
        mes(player, "you open blurberry's cocktail book");
        mes(player, "inside are a list of cocktails");
        final String[] top = new String[] { "non alcoholic", "alcoholic" };
        World.getWorld().getDelayedEventHandler().add(new MiniEvent(player) {

            public void action() {
                this.owner.setMenuHandler(new MenuHandler(top) {

                    public void handleReply(int option, String reply) {
                        if (this.owner.isBusy()) {
                            return;
                        }
                        final int[] page = option == 0
                                ? new int[] { 0, 1 }
                                : new int[] { 2, 3, 4, 5, 6 };
                        String[] names = new String[page.length];
                        for (int i = 0; i < page.length; i++) {
                            names[i] = COCKTAIL_PAGE_NAMES[page[i]];
                        }
                        this.owner.setMenuHandler(new MenuHandler(names) {

                            public void handleReply(int option, String reply) {
                                if (this.owner.isBusy() || option < 0 || option >= page.length) {
                                    return;
                                }
                                show(this.owner, COCKTAIL_PAGES[page[option]]);
                            }
                        });
                        this.owner.getActionSender().sendMenu(names);
                    }
                });
                this.owner.getActionSender().sendMenu(top);
            }
        });
        return true;
    }

    /** The cook book -- gnomebattas, gnomebakes, gnomecrunchies. */
    public static boolean readCookBook(final Player player) {
        mes(player, "you open aluft's cook book");
        mes(player, "inside are various gnome dishes");
        final String[] top = new String[] { "gnomebattas", "gnomebakes", "gnomecrunchies" };
        World.getWorld().getDelayedEventHandler().add(new MiniEvent(player) {

            public void action() {
                this.owner.setMenuHandler(new MenuHandler(top) {

                    public void handleReply(int option, String reply) {
                        if (this.owner.isBusy()) {
                            return;
                        }
                        final int[] page = option == 0
                                ? new int[] { 0, 1, 2, 3, 4 }
                                : option == 1
                                        ? new int[] { 5, 6, 7, 8 }
                                        : new int[] { 9, 10, 11, 12 };
                        String[] names = new String[page.length];
                        for (int i = 0; i < page.length; i++) {
                            names[i] = DISH_PAGE_NAMES[page[i]];
                        }
                        this.owner.setMenuHandler(new MenuHandler(names) {

                            public void handleReply(int option, String reply) {
                                if (this.owner.isBusy() || option < 0 || option >= page.length) {
                                    return;
                                }
                                show(this.owner, DISH_PAGES[page[option]]);
                            }
                        });
                        this.owner.getActionSender().sendMenu(names);
                    }
                });
                this.owner.getActionSender().sendMenu(top);
            }
        });
        return true;
    }

    private static void show(Player player, String[] page) {
        for (int i = 0; i < page.length; i++) {
            player.getActionSender().sendMessage("@yel@" + page[i]);
        }
    }
}
