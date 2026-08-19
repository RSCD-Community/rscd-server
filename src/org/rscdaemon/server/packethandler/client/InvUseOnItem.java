/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.extras.ItemArrowHeadDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemBowStringDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemDartTipDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemGemDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemHerbDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemHerbSecond;
import org.rscdaemon.server.entityhandling.defs.extras.ItemLogCutDef;
import org.rscdaemon.server.event.MiniEvent;
import org.rscdaemon.server.model.GnomeCooking;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Poison;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class InvUseOnItem
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        InvItem item1 = player.getInventory().get(p.readShort());
        InvItem item2 = player.getInventory().get(p.readShort());
        if (item1 == null || item2 == null) {
            player.setSuspiciousPlayer(true);
            return;
        }
        // A quest that has claimed both items owns the pairing outright, and
        // everything below is skipped. Only a quest naming both ids can reach
        // here, so combining herbs or fletching arrows is untouched.
        if (player.getQuestManager().triggerItemPair(item1, item2)) {
            return;
        }
        ItemHerbSecond secondDef = null;
        secondDef = EntityHandler.getItemHerbSecond(item1.getID(), item2.getID());
        if (secondDef != null && this.doHerbSecond(player, item1, item2, secondDef)) {
            return;
        }
        secondDef = EntityHandler.getItemHerbSecond(item2.getID(), item1.getID());
        if (secondDef != null && this.doHerbSecond(player, item2, item1, secondDef)) {
            return;
        }
        if (item1.getID() == 381 && this.attachFeathers(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 381 && this.attachFeathers(player, item2, item1)) {
            return;
        }
        if (item1.getID() == Poison.WEAPON_POISON && this.doWeaponPoison(player, item1, item2)) {
            return;
        }
        if (item2.getID() == Poison.WEAPON_POISON && this.doWeaponPoison(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 167 && this.doCutGem(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 167 && this.doCutGem(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 464 && this.doHerblaw(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 464 && this.doHerblaw(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 13 && this.doLogCut(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 13 && this.doLogCut(player, item2, item1)) {
            return;
        }
        /* Gnome cooking: the knife on a fruit, an ingredient into the shaker,
           the shaker into a glass, a garnish onto a poured glass, and every
           ingredient that goes onto a dough or a baked base. All of it is one
           recipe engine, so it gets one entry point -- see GnomeCooking. It
           sits after the knife's log cutting so fletching keeps first refusal
           on a knife. */
        if (GnomeCooking.useOnItem(player, item1, item2)) {
            return;
        }
        if (item1.getID() == 676 && this.doBowString(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 676 && this.doBowString(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 637 && this.doArrowHeads(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 637 && this.doArrowHeads(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 207 && this.useWool(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 207 && this.useWool(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 39 && this.makeLeather(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 39 && this.makeLeather(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 468 && this.doGrind(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 468 && this.doGrind(player, item2, item1)) {
            return;
        }
        if (this.makeStew(player, item1, item2) || this.makeStew(player, item2, item1)) {
            return;
        }
        if ((item1.getID() == 50 || item1.getID() == 141 || item1.getID() == 342) && this.useWater(player, item1, item2)) {
            return;
        }
        if ((item2.getID() == 50 || item2.getID() == 141 || item2.getID() == 342) && this.useWater(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 621 && this.doGlassBlowing(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 621 && this.doGlassBlowing(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 614 && this.attachOrb(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 614 && this.attachOrb(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 526 && this.combineKeys(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 526 && this.combineKeys(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 250 && this.lineDish(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 250 && this.lineDish(player, item2, item1)) {
            return;
        }
        if (item1.getID() == 253 && this.fillPie(player, item1, item2)) {
            return;
        }
        if (item2.getID() == 253 && this.fillPie(player, item2, item1)) {
            return;
        }
        if (this.wrapOomlie(player, item1, item2) || this.wrapOomlie(player, item2, item1)) {
            return;
        }
        if (item1.getID() == SWAMP_TAR && this.makeSwampPaste(player, item1, item2)) {
            return;
        }
        if (item2.getID() == SWAMP_TAR && this.makeSwampPaste(player, item2, item1)) {
            return;
        }
        if (this.mixHangoverCure(player, item1, item2)
            || this.mixHangoverCure(player, item2, item1)) {
            return;
        }
        if (this.oilFishingRod(player, item1, item2)
            || this.oilFishingRod(player, item2, item1)) {
            return;
        }
        if (this.lightCandle(player, item1, item2) || this.lightCandle(player, item2, item1)) {
            return;
        }
        if (this.mixDyes(player, item1, item2) || this.mixDyes(player, item2, item1)) {
            return;
        }
        if (this.dyeCape(player, item1, item2) || this.dyeCape(player, item2, item1)) {
            return;
        }
        if (this.dyeGoblinArmour(player, item1, item2) || this.dyeGoblinArmour(player, item2, item1)) {
            return;
        }
        player.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
    }

    /* --------------------------------------------------------------- candles
     *
     * A tinderbox lights a candle. Both kinds have a separate lit id and no
     * command of their own, so this is the only way either of them is ever lit.
     *
     * The black one is Merlin's crystal's, but the lighting itself is not: it is
     * ordinary item behaviour, and putting it in the quest would mean the plain
     * candle stayed unlightable forever.
     */

    private static final int TINDERBOX = 166;
    private static final int CANDLE = 599, LIT_CANDLE = 601;
    private static final int BLACK_CANDLE = 600, LIT_BLACK_CANDLE = 602;

    private boolean lightCandle(Player player, InvItem tinderbox, InvItem candle) {
        if (tinderbox.getID() != TINDERBOX) {
            return false;
        }
        int lit;
        if (candle.getID() == CANDLE) {
            lit = LIT_CANDLE;
        } else if (candle.getID() == BLACK_CANDLE) {
            lit = LIT_BLACK_CANDLE;
        } else {
            return false;
        }
        if (player.getInventory().remove(candle) > -1) {
            player.getInventory().add(new InvItem(lit, 1));
            player.getActionSender().sendInventory();
            player.getActionSender().sendMessage("You light the candle");
        }
        return true;
    }

    /* ------------------------------------------------------------------ dyes
     *
     * Six dyes exist: red, yellow and blue are sold (Aggie in Draynor sells
     * hers; other sellers may exist elsewhere), and the other three are made
     * by mixing two of those together -- red+yellow makes orange, blue+yellow
     * makes green, red+blue makes purple. This is general-purpose crafting,
     * not tied to Aggie or to any one quest: any two dye bottles that make a
     * pair react wherever they are used together.
     *
     * Dye is applied to a cape to change its colour -- the vanilla cache
     * defines seven Cape items, one plain ("a warm black cape") and six that
     * read "a thick <colour> cape", one per dye. Goblin armour is a separate,
     * quest-specific dye target (Goblin diplomacy) and is handled on its own
     * below; it only ever accepts orange and blue, never the other four.
     */

    private static final int REDDYE = 238;
    private static final int YELLOWDYE = 239;
    private static final int BLUEDYE = 272;
    private static final int ORANGEDYE = 282;
    private static final int GREENDYE = 515;
    private static final int PURPLEDYE = 516;

    private static final int CAPE_RED = 183;
    private static final int CAPE_BLUE = 229;
    private static final int CAPE_GREEN = 511;
    private static final int CAPE_YELLOW = 512;
    private static final int CAPE_ORANGE = 513;
    private static final int CAPE_PURPLE = 514;
    /* The undyed cape everyone starts from; not itself a dye product. */
    private static final int CAPE_BLACK = 209;
    private static final int[] CAPE_IDS =
        { CAPE_RED, CAPE_BLACK, CAPE_BLUE, CAPE_GREEN, CAPE_YELLOW, CAPE_ORANGE, CAPE_PURPLE };

    private static final int GOBLIN_ARMOUR = 273;
    private static final int GOBLIN_ARMOUR_ORANGE = 274;
    private static final int GOBLIN_ARMOUR_BLUE = 275;

    /* Mixing two primary dyes into the secondary they make, or 0 if this pair
       does not react. Order does not matter -- both ids are checked either
       way -- so the caller need not try the pairing both ways round. */
    private static int mixedDye(int a, int b) {
        if (a == REDDYE && b == YELLOWDYE || a == YELLOWDYE && b == REDDYE) {
            return ORANGEDYE;
        }
        if (a == BLUEDYE && b == YELLOWDYE || a == YELLOWDYE && b == BLUEDYE) {
            return GREENDYE;
        }
        if (a == REDDYE && b == BLUEDYE || a == BLUEDYE && b == REDDYE) {
            return PURPLEDYE;
        }
        return 0;
    }

    private static String dyeColourName(int dyeId) {
        switch (dyeId) {
            case REDDYE: return "red";
            case YELLOWDYE: return "yellow";
            case BLUEDYE: return "blue";
            case ORANGEDYE: return "orange";
            case GREENDYE: return "green";
            case PURPLEDYE: return "purple";
            default: return "";
        }
    }

    private boolean mixDyes(Player player, InvItem dye, InvItem other) {
        int product = mixedDye(dye.getID(), other.getID());
        if (product == 0) {
            return false;
        }
        if (player.getInventory().remove(dye) > -1 && player.getInventory().remove(other) > -1) {
            player.getInventory().add(new InvItem(product, 1));
            player.getActionSender().sendInventory();
            player.getActionSender().sendMessage("You mix the two dyes and make " + dyeColourName(product) + " dye.");
        }
        return true;
    }

    private static boolean isCape(int id) {
        for (int cape : CAPE_IDS) {
            if (cape == id) {
                return true;
            }
        }
        return false;
    }

    private static int dyedCape(int dyeId) {
        switch (dyeId) {
            case REDDYE: return CAPE_RED;
            case BLUEDYE: return CAPE_BLUE;
            case YELLOWDYE: return CAPE_YELLOW;
            case ORANGEDYE: return CAPE_ORANGE;
            case GREENDYE: return CAPE_GREEN;
            case PURPLEDYE: return CAPE_PURPLE;
            default: return 0;
        }
    }

    private boolean dyeCape(Player player, InvItem dye, InvItem target) {
        if (!isCape(target.getID())) {
            return false;
        }
        int coloured = dyedCape(dye.getID());
        if (coloured == 0) {
            return false;
        }
        if (coloured == target.getID()) {
            player.getActionSender().sendMessage("That cape is already " + dyeColourName(dye.getID()) + ".");
            return true;
        }
        if (player.getInventory().remove(dye) > -1 && player.getInventory().remove(target) > -1) {
            player.getInventory().add(new InvItem(coloured, 1));
            player.getActionSender().sendInventory();
            player.getActionSender().sendMessage("You dye the cape " + dyeColourName(dye.getID()) + ".");
        }
        return true;
    }

    /* Goblin diplomacy's own dye target: two of the six colours turn the
       plain goblin armour into the shade one general or the other asked for.
       The other four are legitimate dyes with nothing to do here, so a miss
       is a real "nothing happens" rather than an unhandled case. */
    private boolean dyeGoblinArmour(Player player, InvItem dye, InvItem target) {
        if (target.getID() != GOBLIN_ARMOUR) {
            return false;
        }
        int dyed;
        if (dye.getID() == ORANGEDYE) {
            dyed = GOBLIN_ARMOUR_ORANGE;
        } else if (dye.getID() == BLUEDYE) {
            dyed = GOBLIN_ARMOUR_BLUE;
        } else {
            return false;
        }
        if (player.getInventory().remove(dye) > -1 && player.getInventory().remove(target) > -1) {
            player.getInventory().add(new InvItem(dyed, 1));
            player.getActionSender().sendInventory();
            player.getActionSender().sendMessage("You dye the goblin armour.");
        }
        return true;
    }

    private boolean combineKeys(Player player, InvItem firstHalf, InvItem secondHalf) {
        if (secondHalf.getID() != 527) {
            return false;
        }
        if (player.getInventory().remove(firstHalf) > -1 && player.getInventory().remove(secondHalf) > -1) {
            player.getActionSender().sendMessage("@pnk@ You combine the key halves to make a crystal key.");
            player.getInventory().add(new InvItem(525, 1));
            player.getActionSender().sendInventory();
        }
        return true;
    }

    private boolean doGlassBlowing(Player player, InvItem pipe, final InvItem glass) {
        if (glass.getID() != 623) {
            return false;
        }
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                String[] options = new String[]{"Beer Glass", "Vial", "Orb", "Cancel"};
                this.owner.setMenuHandler(new MenuHandler(options){

                    public void handleReply(int option, String reply) {
                        int exp;
                        int reqLvl;
                        InvItem result;
                        switch (option) {
                            case 0: {
                                result = new InvItem(620, 1);
                                reqLvl = 1;
                                exp = 18;
                                break;
                            }
                            case 1: {
                                result = new InvItem(465, 1);
                                reqLvl = 33;
                                exp = 35;
                                break;
                            }
                            case 2: {
                                result = new InvItem(611, 1);
                                reqLvl = 46;
                                exp = 53;
                                break;
                            }
                            default: {
                                return;
                            }
                        }
                        if (this.owner.getCurStat(12) < reqLvl) {
                            this.owner.getActionSender().sendMessage("@gry@ You need a crafting level of " + reqLvl + " to make a " + result.getDef().getName() + ".");
                            return;
                        }
                        if (this.owner.getInventory().remove(glass) > -1) {
                            this.owner.getActionSender().sendMessage("@pnk@ You make a " + result.getDef().getName());
                            this.owner.getInventory().add(result);
                            this.owner.incExp(12, exp, true);
                            this.owner.getActionSender().sendStat(12);
                            this.owner.getActionSender().sendInventory();
                        }
                    }
                });
                this.owner.getActionSender().sendMenu(options);
            }
        });
        return true;
    }

    /*
     * A charged orb on a battlestaff. The orbs come off the elemental
     * obelisks via the Charge Orb spells (see SpellHandler); this is the
     * crafting half of the elemental battlestaff chain. Levels and exp are
     * the classic values: water 54/100, earth 58/112.5, fire 62/125,
     * air 66/137.5 (halves rounded up as everywhere else in this file).
     */
    private boolean attachOrb(Player player, InvItem staff, InvItem orb) {
        int result;
        int reqLvl;
        int exp;
        switch (orb.getID()) {
            case 613: result = 616; reqLvl = 54; exp = 100; break;  // Water orb
            case 627: result = 618; reqLvl = 58; exp = 113; break;  // earth orb
            case 612: result = 615; reqLvl = 62; exp = 125; break;  // Fire orb
            case 626: result = 617; reqLvl = 66; exp = 138; break;  // air orb
            default: return false;
        }
        InvItem made = new InvItem(result, 1);
        if (player.getCurStat(12) < reqLvl) {
            player.getActionSender().sendMessage("@gry@ You need a crafting level of " + reqLvl + " to make a " + made.getDef().getName() + ".");
            return true;
        }
        if (player.getInventory().remove(staff) > -1 && player.getInventory().remove(orb) > -1) {
            player.getActionSender().sendMessage("@pnk@ You make a " + made.getDef().getName());
            player.getInventory().add(made);
            player.incExp(12, exp, true);
            player.getActionSender().sendStat(12);
            player.getActionSender().sendInventory();
        }
        return true;
    }

    /* ----------------------------------------------------------- baking
     *
     * Water on a pot of flour makes dough, and which dough is the player's
     * choice -- three different foods start the same way. The pot comes back
     * empty along with the container the water was in.
     *
     * Pies then go pastry dough -> pie dish -> pie shell -> filling -> uncooked
     * pie -> range. None of that existed here, which left the redberry pie
     * unobtainable and The knight's sword unfinishable: Thurgo will not say a
     * word to anybody who has not fed him one.
     */

    private static final int FLOUR = 136;
    private static final int POT = 135;
    private static final int BREAD_DOUGH = 137;
    private static final int PASTRY_DOUGH = 250;
    private static final int PIZZA_BASE = 321;
    private static final int PIE_DISH = 251;
    private static final int PIE_SHELL = 253;
    private static final int COOKING = 7;

    private boolean useWater(Player player, final InvItem water, final InvItem item) {
        final int jugID = Formulae.getEmptyJug(water.getID());
        if (jugID == -1) {
            return false;
        }
        switch (item.getID()) {
            case 149: {
                if (player.getInventory().remove(water) <= -1 || player.getInventory().remove(item) <= -1) break;
                /* Two messages with a beat between them, not the one invented
                   line ("You soften the clay.") this used to send. The clay is
                   already in the inventory before the second arrives. */
                player.getActionSender().sendMessage("@que@You mix the clay and water");
                player.getInventory().add(new InvItem(jugID, 1));
                player.getInventory().add(new InvItem(243, 1));
                player.getActionSender().sendInventory();
                world.getDelayedEventHandler().add(new MiniEvent(player, 1200){

                    public void action() {
                        this.owner.getActionSender().sendMessage("@que@You now have some soft workable clay");
                    }
                });
                break;
            }
            case FLOUR: {
                this.makeDough(player, water, item, jugID);
                break;
            }
            default: {
                return false;
            }
        }
        return true;
    }

    private void makeDough(Player player, final InvItem water, final InvItem flour, final int jugID) {
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                String[] options = new String[]{"Bread dough", "Pastry dough", "Pizza dough", "Cancel"};
                this.owner.setMenuHandler(new MenuHandler(options){

                    public void handleReply(int option, String reply) {
                        int reqLvl = 1;
                        InvItem result;
                        switch (option) {
                            case 0: {
                                result = new InvItem(BREAD_DOUGH, 1);
                                break;
                            }
                            case 1: {
                                result = new InvItem(PASTRY_DOUGH, 1);
                                break;
                            }
                            case 2: {
                                result = new InvItem(PIZZA_BASE, 1);
                                reqLvl = 35;
                                break;
                            }
                            default: {
                                return;
                            }
                        }
                        if (this.owner.getCurStat(COOKING) < reqLvl) {
                            this.owner.getActionSender().sendMessage("@gry@ You need a cooking level of " + reqLvl + " to make " + result.getDef().getName() + ".");
                            return;
                        }
                        if (this.owner.getInventory().remove(water) <= -1 || this.owner.getInventory().remove(flour) <= -1) {
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ You mix the flour and water to make " + result.getDef().getName() + ".");
                        this.owner.getInventory().add(new InvItem(jugID, 1));
                        this.owner.getInventory().add(new InvItem(POT, 1));
                        this.owner.getInventory().add(result);
                        this.owner.getActionSender().sendInventory();
                    }
                });
                this.owner.getActionSender().sendMenu(options);
            }
        });
    }

    /** Pastry dough into a pie dish, which gives an empty pie shell. */
    /* ---------------------------------------------------------- swamp paste
     *
     * Swamp tar out of the Lumbridge swamp, mixed with flour and then warmed
     * over a fire. Nothing in the server made it, which left Sea slug
     * unfinishable: Holgart's rowboat is full of holes and swamp paste is the
     * only thing that fills them, and no shop in the world sells any.
     *
     * The warming is the ordinary cooking path -- item 784 has an entry in
     * conf/server/defs/extras/ItemCookingDef.xml that cooks it into 785 -- so
     * only the mixing needs code. The pot the flour came in is not returned:
     * Jagex's flour is a bare item, and 135 is the empty pot you already have.
     */
    private static final int SWAMP_TAR = 783;
    private static final int UNCOOKED_PASTE = 784;

    /* ---------------------------------------------------------------- oomlie
     *
     * The Kharazi Jungle's Oomlie bird meat cannot be cooked bare: it has no
     * cooking def, so a fire or range answers "nothing interesting happens".
     * Wrapping it in a palm leaf makes the parcel (level 50 cooking, 10 xp),
     * and the parcel cooks on a fire or range through the ordinary cooking
     * table (30 xp). The parcel's table entry is fireproof -- burnt id equals
     * cooked id -- because the burnt parcel was unobtainable in RSC: level 50
     * is required to make the parcel at all, and at 50 it no longer burned.
     */

    private static final int RAW_OOMLIE = 1268;
    private static final int PALM_LEAF = 1279;
    private static final int OOMLIE_PARCEL = 1280;

    private boolean wrapOomlie(Player player, InvItem leaf, InvItem meat) {
        if (leaf.getID() != PALM_LEAF || meat.getID() != RAW_OOMLIE) {
            return false;
        }
        if (player.getCurStat(7) < 50) {
            player.getActionSender().sendMessage("@gry@ You need a cooking level of 50 to prepare the oomlie meat.");
            return true;
        }
        if (player.getInventory().remove(meat) > -1 && player.getInventory().remove(leaf) > -1) {
            player.getActionSender().sendMessage("@pnk@ You wrap the oomlie meat in the palm leaf.");
            player.getInventory().add(new InvItem(OOMLIE_PARCEL, 1));
            player.incExp(7, 10, true);
            player.getActionSender().sendStat(7);
            player.getActionSender().sendInventory();
        }
        return true;
    }

    private boolean makeSwampPaste(Player player, InvItem tar, InvItem flour) {
        if (flour.getID() != FLOUR) {
            return false;
        }
        if (player.getInventory().remove(tar) > -1 && player.getInventory().remove(flour) > -1) {
            player.getActionSender().sendMessage("@pnk@ You mix the swamp tar with the flour.");
            player.getInventory().add(new InvItem(UNCOOKED_PASTE, 1));
            player.getActionSender().sendInventory();
        }
        return true;
    }

    /* ---------------------------------------------------- oily fishing rod
     *
     * Blamish oil over an ordinary fishing rod, which is the only rod a lava
     * fishing spot will accept. The oil itself is a herblaw mixture and comes
     * out of ItemHerbSecond like every other potion; only this last step needs
     * code, because it is not a potion and not a craft.
     *
     * Ordinary world content rather than Hero's quest's, for the same reason
     * the hangover cure below is: a quest that claimed the fishing rod would
     * take fishing rods away from everything else in the game. Nothing here is
     * gated on the quest -- a player who has the oil has already had the whole
     * conversation with Gerrant that produces it.
     */
    private static final int BLAMISH_OIL = 588, FISHING_ROD = 377, OILY_ROD = 589;

    private boolean oilFishingRod(Player player, InvItem oil, InvItem rod) {
        if (oil.getID() != BLAMISH_OIL || rod.getID() != FISHING_ROD) {
            return false;
        }
        if (player.getInventory().remove(oil) > -1 && player.getInventory().remove(rod) > -1) {
            player.getActionSender().sendMessage("@pnk@ You rub the blamish oil into the fishing rod.");
            player.getInventory().add(new InvItem(OILY_ROD, 1));
            player.getActionSender().sendInventory();
        }
        return true;
    }

    /* ------------------------------------------------------- hangover cure
     *
     * Trudi the herbalist wrote it down for Bravek before she caught the
     * plague: chocolate dust into a bucket of milk, then snape grass. The
     * scruffy note Bravek hands over says the same thing in his handwriting.
     *
     * Ordinary world content rather than Plague city's, because the recipe is
     * fixed and none of the three ingredients belongs to a quest -- claiming
     * milk would take every other use of it away from everything else.
     */
    private static final int MILK = 22, CHOCOLATE_DUST = 772;
    private static final int CHOCOLATY_MILK = 770, SNAPE_GRASS = 469;
    private static final int HANGOVER_CURE = 771;

    private boolean mixHangoverCure(Player player, InvItem first, InvItem second) {
        int made;
        String what;
        if (first.getID() == MILK && second.getID() == CHOCOLATE_DUST) {
            made = CHOCOLATY_MILK;
            what = "chocolate into the milk";
        } else if (first.getID() == CHOCOLATY_MILK && second.getID() == SNAPE_GRASS) {
            made = HANGOVER_CURE;
            what = "snape grass into the chocolaty milk";
        } else {
            return false;
        }
        if (player.getInventory().remove(first) > -1 && player.getInventory().remove(second) > -1) {
            player.getActionSender().sendMessage("@pnk@ You mix the " + what + ".");
            player.getInventory().add(new InvItem(made, 1));
            player.getActionSender().sendInventory();
        }
        return true;
    }

    private boolean lineDish(Player player, InvItem dough, InvItem dish) {
        if (dish.getID() != PIE_DISH) {
            return false;
        }
        if (player.getInventory().remove(dough) > -1 && player.getInventory().remove(dish) > -1) {
            player.getActionSender().sendMessage("@pnk@ You put the pastry dough into the pie dish.");
            player.getInventory().add(new InvItem(PIE_SHELL, 1));
            player.getActionSender().sendInventory();
        }
        return true;
    }

    /**
     * A filling into a pie shell. Redberries, cooked meat and cooking apples
     * are the three Jagex shipped; anything else is not a pie.
     */
    private boolean fillPie(Player player, InvItem shell, InvItem filling) {
        int uncooked;
        String what;
        switch (filling.getID()) {
            case 236: {
                uncooked = 256;
                what = "redberries";
                break;
            }
            case 132: {
                uncooked = 255;
                what = "meat";
                break;
            }
            case 252: {
                uncooked = 254;
                what = "apple";
                break;
            }
            default: {
                return false;
            }
        }
        if (player.getInventory().remove(shell) > -1 && player.getInventory().remove(filling) > -1) {
            player.getActionSender().sendMessage("@pnk@ You fill your pie with " + what);
            player.getInventory().add(new InvItem(uncooked, 1));
            player.getActionSender().sendInventory();
        }
        return true;
    }

    private boolean doGrind(Player player, InvItem mortar, InvItem item) {
        int newID;
        switch (item.getID()) {
            case 466: {
                newID = 473;
                break;
            }
            case 467: {
                newID = 472;
                break;
            }
            /* Chocolate dust. Not a herblaw secondary like the other two -- it
               goes into a hangover cure for Bravek, and into chocolate bombs
               and chocolate saturdays at the Gnome bar. */
            case 337: {
                newID = 772;
                break;
            }
            /* Bat bones into ground bat bones. The mortar only knew the two
               herblaw secondaries and the chocolate bar, so the last ingredient
               of the Watchtower ogre potion could not be made at all. Grinding
               is a mortar recipe rather than quest machinery, so it belongs
               here and not in quests/Watchtower.java. */
            case 604: {
                newID = 1051;
                break;
            }
            /* Charcoal into ground charcoal, one of the four ingredients of the
               Digsite explosive compound. Same reasoning as the bat bones: it
               is a mortar recipe, so it lives here and not in the quest. */
            case 983: {
                newID = 1179;
                break;
            }
            default: {
                return false;
            }
        }
        if (player.getInventory().remove(item) > -1) {
            player.getActionSender().sendMessage("@pnk@ You grind up the " + item.getDef().getName());
            player.getInventory().add(new InvItem(newID, 1));
            player.getActionSender().sendInventory();
        }
        return true;
    }

    private boolean doHerbSecond(Player player, InvItem second, InvItem unfinished, ItemHerbSecond def) {
        if (unfinished.getID() != def.getUnfinishedID()) {
            return false;
        }
        if (!canDoHerblaw(player)) {
            return true;
        }
        if (player.getCurStat(15) < def.getReqLevel()) {
            player.getActionSender().sendMessage("@gry@ You need a herblaw level of " + def.getReqLevel() + " to mix those");
            return true;
        }
        if (player.getInventory().remove(second) > -1 && player.getInventory().remove(unfinished) > -1) {
            player.getActionSender().sendMessage("@pnk@ You mix the " + second.getDef().getName() + " with the " + unfinished.getDef().getName());
            player.getInventory().add(new InvItem(def.getPotionID(), 1));
            player.incExp(15, def.getExp(), true);
            player.getActionSender().sendStat(15);
            player.getActionSender().sendInventory();
        }
        return true;
    }

    private static final int BOWL_OF_WATER = 342;
    private static final int STEW_NEEDS_MEAT = 343;
    private static final int STEW_NEEDS_POTATO = 344;
    private static final int UNCOOKED_STEW = 345;
    private static final int POTATO = 348;
    private static final int COOKED_MEAT = 132;

    /**
     * Building a stew. Cooking one already worked -- Jagex's ItemCookingDef
     * has 345 at level 25 for 22.5 experience -- but there was no way to make
     * a 345 in the first place, so the whole of stew was unreachable.
     *
     * Meat and potato go in in either order, and Jagex's own item
     * descriptions are the proof: 343 reads "I need to add some meat too" and
     * 344 "I need to add some potato too". Two half-made stews for two orders.
     *
     * Raw meat is refused with a line that opens lower case. Leave it.
     */
    private boolean makeStew(Player player, InvItem first, InvItem second) {
        int a = first.getID();
        int b = second.getID();
        int result;
        String message;
        if (a == BOWL_OF_WATER && b == POTATO) {
            result = STEW_NEEDS_MEAT;
            message = "@que@You cut up the potato and put it into the bowl";
        } else if (a == BOWL_OF_WATER && b == COOKED_MEAT) {
            result = STEW_NEEDS_POTATO;
            message = "@que@You cut up the meat and put it into the bowl";
        } else if (a == STEW_NEEDS_MEAT && b == COOKED_MEAT) {
            result = UNCOOKED_STEW;
            message = "@que@You cut up the meat and put it into the stew";
        } else if (a == STEW_NEEDS_POTATO && b == POTATO) {
            result = UNCOOKED_STEW;
            message = "@que@You cut up the potato and put it into the stew";
        } else if (a == BOWL_OF_WATER && (b == 133 || b == 502 || b == 503 || b == 504)) {
            player.getActionSender().sendMessage("@que@you need to precook the meat");
            return true;
        } else {
            return false;
        }
        if (player.getInventory().remove(first) <= -1 || player.getInventory().remove(second) <= -1) {
            return true;
        }
        player.getActionSender().sendMessage(message);
        player.getInventory().add(new InvItem(result, 1));
        player.getActionSender().sendInventory();
        return true;
    }

    private static final int THREAD = 43;

    /**
     * One reel of thread is used up every fifth leather item, and the player
     * is told about it. This used to be a one-in-six coin flip with no message
     * at all, taken before the menu even opened -- so cancelling out of the
     * menu could still cost a reel.
     *
     * The count is a session flag. Losing it at logout is worth strictly less
     * than a saved column: the worst a player can do with it is get an extra
     * few items out of one reel by relogging, which is more trouble than the
     * 3 gold the reel costs.
     */
    private void useThread(Player player) {
        int used = player.getFlag("leather_thread");
        if (used > 4) {
            player.getActionSender().sendMessage("@que@You use up one of your reels of thread");
            player.getInventory().remove(THREAD, 1);
            player.getActionSender().sendInventory();
            used = 0;
        } else {
            ++used;
        }
        player.setFlag("leather_thread", used);
    }

    private boolean makeLeather(Player player, InvItem needle, final InvItem leather) {
        if (leather.getID() != 148) {
            return false;
        }
        if (player.getInventory().countId(THREAD) < 1) {
            player.getActionSender().sendMessage("@que@You need some thread to make anything out of leather");
            return true;
        }
        player.getActionSender().sendMessage("@que@What would you like to make?");
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                String[] options = new String[]{"Armour", "Gloves", "Boots", "Cancel"};
                this.owner.setMenuHandler(new MenuHandler(options){

                    public void handleReply(int option, String reply) {
                        int exp;
                        int reqLvl;
                        InvItem result;
                        /* Jagex's refusals name the item, and disagree with
                           each other about capitals -- "Leather Armour" but
                           "boots". Gloves have no requirement to refuse. Both
                           read "a crafting of level N or higher", which is not
                           a typo of ours. */
                        String refusal;
                        String made;
                        switch (option) {
                            case 0: {
                                result = new InvItem(15, 1);
                                reqLvl = 14;
                                exp = 25;
                                refusal = "@que@You need to have a crafting of level 14 or higher to make Leather Armour";
                                made = "You make some leather armour";
                                break;
                            }
                            case 1: {
                                result = new InvItem(16, 1);
                                reqLvl = 1;
                                exp = 14;
                                refusal = null;
                                made = "You make some leather gloves";
                                break;
                            }
                            case 2: {
                                result = new InvItem(17, 1);
                                reqLvl = 7;
                                exp = 17;
                                refusal = "@que@You need to have a crafting of level 7 or higher to make boots";
                                made = "You make some boots";
                                break;
                            }
                            default: {
                                return;
                            }
                        }
                        if (this.owner.getCurStat(12) < reqLvl) {
                            this.owner.getActionSender().sendMessage(refusal);
                            return;
                        }
                        if (this.owner.getInventory().remove(leather) > -1) {
                            this.owner.getActionSender().sendMessage(made);
                            this.owner.getInventory().add(result);
                            this.owner.incExp(12, exp, true);
                            this.owner.getActionSender().sendStat(12);
                            this.owner.getActionSender().sendInventory();
                            useThread(this.owner);
                        }
                    }
                });
                this.owner.getActionSender().sendMenu(options);
            }
        });
        return true;
    }

    private boolean useWool(Player player, final InvItem woolBall, final InvItem item) {
        int newID;
        switch (item.getID()) {
            case 44: {
                newID = 45;
                break;
            }
            case 1027: {
                newID = 1028;
                break;
            }
            case 296: {
                newID = 301;
                break;
            }
            case 297: {
                newID = 302;
                break;
            }
            case 298: {
                newID = 303;
                break;
            }
            case 299: {
                newID = 304;
                break;
            }
            case 300: {
                newID = 305;
                break;
            }
            case 524: {
                newID = 610;
                break;
            }
            default: {
                return false;
            }
        }
        final int newId = newID;
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                if (this.owner.getInventory().remove(woolBall) > -1 && this.owner.getInventory().remove(item) > -1) {
                    this.owner.getActionSender().sendMessage("@pnk@ You string the amulet");
                    this.owner.getInventory().add(new InvItem(newId, 1));
                    this.owner.getActionSender().sendInventory();
                }
            }
        });
        return true;
    }

    /**
     * Weapon poison, used on a weapon.
     *
     * "This potion may not be consumed directly by the player, and is instead
     * used only on other items in the inventory." Its examine text has said
     * "For use on daggers and arrows" since 2002 and it was in the definitions
     * all along; nothing in this server had ever done anything with it.
     *
     * One dose treats one dagger, one spear or one throwing knife, five arrows
     * or bolts, or six darts, and the potion is used up whichever it was --
     * "Unlike most potions, this potion only has one use", so there is no vial
     * back and no doses left to report. Fewer than a full batch is still one
     * whole potion, which is the player's problem and not something to round
     * up: five arrows is what a dose treats, not what it needs.
     */
    private boolean doWeaponPoison(Player player, final InvItem potion, final InvItem weapon) {
        int[] form = Poison.poisonedForm(weapon.getID());
        if (form == null) {
            return false;
        }
        final int poisonedId = form[0];
        int batch = form[1];
        if (weapon.getAmount() < batch) {
            batch = weapon.getAmount();
        }
        final int amount = batch;
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                if (this.owner.getInventory().remove(weapon.getID(), amount) > -1
                        && this.owner.getInventory().remove(potion.getID(), 1) > -1) {
                    this.owner.getInventory().add(new InvItem(poisonedId, amount));
                    this.owner.getActionSender().sendMessage("@pnk@ You apply the poison to the "
                            + weapon.getDef().getName());
                    this.owner.getActionSender().sendInventory();
                }
                this.owner.setBusy(false);
            }
        });
        player.setBusy(true);
        return true;
    }

    private boolean attachFeathers(Player player, final InvItem feathers, final InvItem item) {
        int exp;
        InvItem newItem;
        int amount = 10;
        if (feathers.getAmount() < amount) {
            amount = feathers.getAmount();
        }
        if (item.getAmount() < amount) {
            amount = item.getAmount();
        }
        ItemDartTipDef tipDef = null;
        if (item.getID() == 280) {
            newItem = new InvItem(637, amount);
            exp = amount;
        } else {
            tipDef = EntityHandler.getItemDartTipDef(item.getID());
            if (tipDef != null) {
                newItem = new InvItem(tipDef.getDartID(), amount);
                exp = (int)(tipDef.getExp() * (double)amount);
            } else {
                return false;
            }
        }
        final int amt = amount;
        final int xp = exp;
        final InvItem newItm = newItem;
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                if (this.owner.getInventory().remove(feathers.getID(), amt) > -1 && this.owner.getInventory().remove(item.getID(), amt) > -1) {
                    this.owner.getActionSender().sendMessage("@pnk@ You attach the feathers to the " + item.getDef().getName());
                    this.owner.getInventory().add(newItm);
                    this.owner.incExp(9, xp, true);
                    this.owner.getActionSender().sendStat(9);
                    this.owner.getActionSender().sendInventory();
                }
            }
        });
        return true;
    }

    private boolean doCutGem(Player player, InvItem chisel, final InvItem gem) {
        final ItemGemDef gemDef = EntityHandler.getItemGemDef(gem.getID());
        if (gemDef == null) {
            return false;
        }
        if (player.getCurStat(12) < gemDef.getReqLevel()) {
            player.getActionSender().sendMessage("@gry@ You need a crafting level of " + gemDef.getReqLevel() + " to cut this gem");
            return true;
        }
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                if (this.owner.getInventory().remove(gem) > -1) {
                    InvItem cutGem = new InvItem(gemDef.getGemID(), 1);
                    this.owner.getActionSender().sendMessage("@pnk@ You cut the " + cutGem.getDef().getName());
                    this.owner.getActionSender().sendSound("chisel");
                    this.owner.getInventory().add(cutGem);
                    this.owner.incExp(12, gemDef.getExp(), true);
                    this.owner.getActionSender().sendStat(12);
                    this.owner.getActionSender().sendInventory();
                }
            }
        });
        return true;
    }

    private boolean doArrowHeads(Player player, final InvItem headlessArrows, final InvItem arrowHeads) {
        final ItemArrowHeadDef headDef = EntityHandler.getItemArrowHeadDef(arrowHeads.getID());
        if (headDef == null) {
            return false;
        }
        if (player.getCurStat(9) < headDef.getReqLevel()) {
            player.getActionSender().sendMessage("@gry@ You need a fletching level of " + headDef.getReqLevel() + " to attach those.");
            return true;
        }
        int amount = 10;
        if (headlessArrows.getAmount() < amount) {
            amount = headlessArrows.getAmount();
        }
        if (arrowHeads.getAmount() < amount) {
            amount = arrowHeads.getAmount();
        }
        final int amt = amount;
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                if (this.owner.getInventory().remove(headlessArrows.getID(), amt) > -1 && this.owner.getInventory().remove(arrowHeads.getID(), amt) > -1) {
                    this.owner.getActionSender().sendMessage("@pnk@ You attach the heads to the arrows");
                    this.owner.getInventory().add(new InvItem(headDef.getArrowID(), amt));
                    this.owner.incExp(9, (int)(headDef.getExp() * (double)amt), true);
                    this.owner.getActionSender().sendStat(9);
                    this.owner.getActionSender().sendInventory();
                }
            }
        });
        return true;
    }

    private boolean doBowString(Player player, final InvItem bowString, final InvItem bow) {
        final ItemBowStringDef stringDef = EntityHandler.getItemBowStringDef(bow.getID());
        if (stringDef == null) {
            return false;
        }
        if (player.getCurStat(9) < stringDef.getReqLevel()) {
            player.getActionSender().sendMessage("@gry@ You need a fletching level of " + stringDef.getReqLevel() + " to do that.");
            return true;
        }
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                if (this.owner.getInventory().remove(bowString) > -1 && this.owner.getInventory().remove(bow) > -1) {
                    this.owner.getActionSender().sendMessage("@pnk@ You add the bow string to the bow");
                    this.owner.getInventory().add(new InvItem(stringDef.getBowID(), 1));
                    this.owner.incExp(9, stringDef.getExp(), true);
                    this.owner.getActionSender().sendStat(9);
                    this.owner.getActionSender().sendInventory();
                }
            }
        });
        return true;
    }

    private boolean doLogCut(Player player, InvItem knife, final InvItem log) {
        final ItemLogCutDef cutDef = EntityHandler.getItemLogCutDef(log.getID());
        if (cutDef == null) {
            return false;
        }
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                String[] options = new String[]{"Arrow shafts", "Shortbow", "Longbow", "Cancel"};
                this.owner.setMenuHandler(new MenuHandler(options){

                    public void handleReply(int option, String reply) {
                        int exp;
                        int reqLvl;
                        InvItem result;
                        switch (option) {
                            case 0: {
                                result = new InvItem(280, cutDef.getShaftAmount());
                                reqLvl = cutDef.getShaftLvl();
                                exp = cutDef.getShaftExp();
                                break;
                            }
                            case 1: {
                                result = new InvItem(cutDef.getShortbowID(), 1);
                                reqLvl = cutDef.getShortbowLvl();
                                exp = cutDef.getShortbowExp();
                                break;
                            }
                            case 2: {
                                result = new InvItem(cutDef.getLongbowID(), 1);
                                reqLvl = cutDef.getLongbowLvl();
                                exp = cutDef.getLongbowExp();
                                break;
                            }
                            default: {
                                return;
                            }
                        }
                        if (this.owner.getCurStat(9) < reqLvl) {
                            this.owner.getActionSender().sendMessage("@gry@ You need a fletching level of " + reqLvl + " to cut that.");
                            return;
                        }
                        if (this.owner.getInventory().remove(log) > -1) {
                            this.owner.getActionSender().sendMessage("@pnk@ You make a " + result.getDef().getName());
                            this.owner.getInventory().add(result);
                            this.owner.incExp(9, exp, true);
                            this.owner.getActionSender().sendStat(9);
                            this.owner.getActionSender().sendInventory();
                        }
                    }
                });
                this.owner.getActionSender().sendMenu(options);
            }
        });
        return true;
    }

    private boolean doHerblaw(Player player, final InvItem vial, final InvItem herb) {
        final ItemHerbDef herbDef = EntityHandler.getItemHerbDef(herb.getID());
        if (herbDef == null) {
            return false;
        }
        if (!canDoHerblaw(player)) {
            return true;
        }
        if (player.getCurStat(15) < herbDef.getReqLevel()) {
            player.getActionSender().sendMessage("@gry@ You need a herblaw level of " + herbDef.getReqLevel() + " to mix those.");
            return true;
        }
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                if (this.owner.getInventory().remove(vial) > -1 && this.owner.getInventory().remove(herb) > -1) {
                    this.owner.getActionSender().sendMessage("@pnk@ You add the " + herb.getDef().getName() + " to the water");
                    this.owner.getInventory().add(new InvItem(herbDef.getPotionId(), 1));
                    this.owner.incExp(15, herbDef.getExp(), true);
                    this.owner.getActionSender().sendStat(15);
                    this.owner.getActionSender().sendInventory();
                }
            }
        });
        return true;
    }

    /**
     * Herblaw is Druidic ritual's reward, and its only one.
     *
     * Cleaning a herb, adding a herb to water and mixing in a secondary all go
     * through here first. Jagex gated every one of them on the quest: without
     * it the druids have taught you nothing, so there is nothing to do with a
     * herb but look at it. The wording is ours -- the transcripts record what
     * the druids say, not what the game says when you try herblaw too early.
     */
    private static boolean canDoHerblaw(Player player) {
        if (player.getQuestManager().completed(Quests.DRUIDIC_RITUAL)) {
            return true;
        }
        player.getActionSender().sendMessage(
            "@gry@ You need to complete the Druidic ritual quest to use herblaw.");
        return false;
    }

}

