/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.defs.extras.ItemUnIdentHerbDef;
import org.rscdaemon.server.event.MiniEvent;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.model.Bubble;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.GnomeCooking;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Multicannon;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Poison;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.npchandler.ShantayPass;
import org.rscdaemon.server.npchandler.ThordurHandler;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.util.DataConversions;

public class InvActionHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    /** The Draynor Manor cabbage patch. Ordinary cabbage is 18. */
    private static final int MAGIC_CABBAGE = 228;

    /**
     * What Jagex printed when a player ate something.
     *
     * "You eat the <item name>" is the general case and stays the general
     * case, but a good deal of food said something else, and this server said
     * the general thing for all of it -- including "You eat the cookedmeat."
     * and "You eat the Half Meat Pizza.", which are the item def's internal
     * names leaking straight into the chat box.
     *
     * Two habits run through the real set. Anything eaten in two goes says
     * "half of" on BOTH bites, not just the first, and the pizzas never name
     * their topping while the pies always name their filling. And every one of
     * them is a "@que@" line without a full stop.
     */
    private static String eatMessage(int id, String name) {
        switch (id) {
            case 18: {
                return "@que@You eat the cabbage. Yuck!";
            }
            case MAGIC_CABBAGE: {
                return "@que@You eat the cabbage";
            }
            case 132: {
                return "@que@You eat the meat";
            }
            case 179: {
                return "@que@You eat the spinach roll";
            }
            case 257:
            case 263: {
                return "@que@You eat half of an apple pie";
            }
            case 258:
            case 262: {
                return "@que@You eat half of a redberry pie";
            }
            case 259:
            case 261: {
                return "@que@You eat half of a meat pie";
            }
            case 325: {
                /* The plain pizza is the odd one out: it goes in one bite. */
                return "@que@You eat the pizza";
            }
            case 326:
            case 327:
            case 328:
            case 329:
            case 750:
            case 751: {
                return "@que@You eat half of the pizza";
            }
            case 330: {
                return "@que@You eat part of the cake";
            }
            case 333: {
                return "@que@You eat some more of the cake";
            }
            case 335: {
                return "@que@You eat the slice of cake";
            }
            case 332: {
                return "@que@You eat part of the chocolate cake";
            }
            case 334: {
                return "@que@You eat some more of the chocolate cake";
            }
            case 336: {
                return "@que@You eat the chocolate slice";
            }
            case 422: {
                return "@que@You eat the pumpkin";
            }
        }
        return "@que@You eat the " + name;
    }

    /** The line a beat later, sent only when the food actually healed. */
    private static String healMessage(int id) {
        switch (id) {
            case 18: {
                /* A cabbage is not worth eating and Jagex says so twice. */
                return "@que@It heals some health anyway";
            }
            case 179: {
                return "@que@It tastes a bit weird, but fills you up";
            }
        }
        return "@que@It heals some health";
    }

    /** The Al Kharid kebab. Not the Ugthanki kebab, which is ordinary food. */
    private static final int KEBAB = 210;

    /**
     * Chocolaty milk, and what is left of it.
     *
     * Milk and chocolate dust, mixed anywhere -- no fire, no range -- for the
     * hangover cure in Plague city. Jagex gave it a drink option on 12 December
     * 2002 and it heals 4.
     *
     * The bucket comes back, because a container empties rather than vanishing
     * when what is inside it is consumed -- the same as the two drinks in this
     * switch that hand back a beer glass. No surviving page states it for this
     * item specifically; it is the game's rule everywhere else.
     */
    private static final int CHOCOLATY_MILK = 770, CHOCOLATY_MILK_HEALS = 4, BUCKET = 21;

    /*
     * The kebab is the only food in the game that rolls for what it does, and
     * this server had it down as a flat 2 hits like a bowl of stew. The wiki
     * article states the shape of it plainly: "They have a high chance to heal
     * 10% of the player's hits, some chance to heal between 10 and 20 hits and
     * on very rare occasions they may not only have good heal but also provide
     * a 1-3 boost in Attack, Strength, and Defense. On very rare occasions they
     * can damage the player as well but never kill them."
     *
     * Two of the six outcomes below are attested from replay captures -- the
     * common heal, and the 1-in-64 "good kebab". The four rare ones are
     * reconstruction: the effects follow the wiki description, and the wording
     * is borrowed from the poison chalice, whose message table IS attested and
     * which is the only other item in the game built this way. The pairing of
     * "very dodgy"/"You feel very ill" against "a bit dodgy"/"You feel a bit
     * ill" comes from that table.
     *
     * The floor on the damage branches is 1, not 0, because the wiki is
     * explicit that a kebab never kills you.
     */
    private static void kebabHeal(Player player, int constant, int percent) {
        int base = player.getMaxStat(3);
        int cur = player.getCurStat(3);
        int cap = cur > base ? cur : base;
        int healed = cur + constant + base * percent / 100;
        player.setCurStat(3, healed > cap ? cap : healed);
        player.getActionSender().sendStat(3);
    }

    private static void kebabBoost(Player player, int stat, int amount) {
        int ceiling = player.getMaxStat(stat) + amount;
        int boosted = player.getCurStat(stat) + amount;
        player.setCurStat(stat, boosted > ceiling ? ceiling : boosted);
        player.getActionSender().sendStat(stat);
    }

    private static void kebabDrain(Player player, int stat, int amount, int floor) {
        int drained = player.getCurStat(stat) - amount;
        player.setCurStat(stat, drained < floor ? floor : drained);
        player.getActionSender().sendStat(stat);
    }

    /** The second line of every kebab outcome, a beat after the first. */
    private void kebabAftertaste(Player player, final String message, final Runnable effect) {
        world.getDelayedEventHandler().add(new MiniEvent(player, 1200){

            public void action() {
                this.owner.getActionSender().sendMessage(message);
                effect.run();
                this.owner.setBusy(false);
            }
        });
    }

    private void eatKebab(final Player player, InvItem item) {
        player.setBusy(true);
        player.getActionSender().sendSound("eat");
        player.getInventory().remove(item);
        player.getActionSender().sendInventory();
        player.getActionSender().sendMessage("@que@You eat the Kebab");
        boolean hurt = player.getCurStat(3) < player.getMaxStat(3);
        if (DataConversions.random(0, 63) == 0) {
            /* The good kebab. It has nothing to say to a player who is
               already at full health. */
            if (!hurt) {
                player.setBusy(false);
                return;
            }
            player.getActionSender().sendMessage("@que@That was a good kebab");
            this.kebabAftertaste(player, "@que@You feel a lot better", new Runnable(){

                public void run() {
                    InvActionHandler.kebabHeal(player, 6, 14);
                }
            });
            return;
        }
        if (DataConversions.random(0, 7) == 0) {
            player.getActionSender().sendMessage("@que@That tasted a bit dodgy");
            this.kebabAftertaste(player, "@que@You feel a bit ill", new Runnable(){

                public void run() {
                    InvActionHandler.kebabDrain(player, 3, 3, 1);
                }
            });
            return;
        }
        if (DataConversions.random(0, 7) == 0) {
            player.getActionSender().sendMessage("@que@That tasted very dodgy");
            this.kebabAftertaste(player, "@que@You feel very ill", new Runnable(){

                public void run() {
                    InvActionHandler.kebabDrain(player, 0, 3, 0);
                    InvActionHandler.kebabDrain(player, 1, 3, 0);
                    InvActionHandler.kebabDrain(player, 2, 3, 0);
                    InvActionHandler.kebabDrain(player, 3, 4, 1);
                }
            });
            return;
        }
        if (DataConversions.random(0, 7) == 0) {
            player.getActionSender().sendMessage("@que@Wow that was an amazing kebab!!");
            this.kebabAftertaste(player, "@que@You feel really invigorated", new Runnable(){

                public void run() {
                    InvActionHandler.kebabBoost(player, 0, 3);
                    InvActionHandler.kebabBoost(player, 1, 3);
                    InvActionHandler.kebabBoost(player, 2, 3);
                    InvActionHandler.kebabHeal(player, 7, 24);
                }
            });
            return;
        }
        if (DataConversions.random(0, 7) == 0) {
            player.getActionSender().sendMessage("@que@That kebab didn't seem to do a lot");
            player.setBusy(false);
            return;
        }
        if (!hurt) {
            player.setBusy(false);
            return;
        }
        this.kebabAftertaste(player, "@que@It heals some health", new Runnable(){

            public void run() {
                InvActionHandler.kebabHeal(player, 3, 7);
            }
        });
    }

    public void handlePacket(Packet p, Connection session) throws Exception {
        block61: {
            InvItem item;
            Player player;
            block64: {
                block63: {
                    block62: {
                        block60: {
                            boolean heals;
                            player = (Player)session.getAttachment();
                            if (player.isBusy()) {
                                if (player.inCombat()) {
                                    player.getActionSender().sendMessage("@gry@ You cannot do that whilst fighting!");
                                }
                                return;
                            }
                            player.resetAll();
                            short idx = p.readShort();
                            if (idx < 0 || idx >= player.getInventory().size()) {
                                player.setSuspiciousPlayer(true);
                                return;
                            }
                            item = player.getInventory().get(idx);
                            if (item == null || item.getDef().getCommand().equals("")) {
                                player.setSuspiciousPlayer(true);
                                return;
                            }
                            // A quest that has claimed this item owns its
                            // command -- see QuestTrigger.ITEM_COMMAND. Nothing
                            // below runs for it, which is what lets a quest give
                            // "read" a meaning the hardcoded list has none for.
                            if (player.getQuestManager().triggerEntity(QuestTrigger.ITEM_COMMAND, item)) {
                                return;
                            }
                            // The cannon base is the only item in the game whose
                            // command puts something into the world; the other
                            // three parts are then used on what is standing
                            // there. Its manual is read the same way Nulodion's
                            // notes are, into the chat box.
                            if (item.getID() == Multicannon.BASE) {
                                Multicannon.setDown(player);
                                return;
                            }
                            if (item.getID() == Multicannon.MANUAL) {
                                Multicannon.read(player);
                                return;
                            }
                            if (!item.isEdible()) break block60;
                            if (item.getID() == KEBAB) {
                                this.eatKebab(player, item);
                                return;
                            }
                            player.setBusy(true);
                            player.getActionSender().sendSound("eat");
                            player.getActionSender().sendMessage(InvActionHandler.eatMessage(item.getID(), item.getDef().getName()));
                            boolean bl = heals = player.getCurStat(3) < player.getMaxStat(3);
                            if (heals) {
                                int newHp = player.getCurStat(3) + item.eatingHeals();
                                if (newHp > player.getMaxStat(3)) {
                                    newHp = player.getMaxStat(3);
                                }
                                player.setCurStat(3, newHp);
                                player.getActionSender().sendStat(3);
                            }
                            world.getDelayedEventHandler().add(new SingleEvent(player, 200){

                                public void action() {
                                    if (item.getID() == MAGIC_CABBAGE) {
                                        /* The Draynor Manor cabbage. It says
                                           so whether or not it healed, and it
                                           is the only cabbage in the game
                                           worth eating. */
                                        this.owner.getActionSender().sendMessage("@que@It seems to taste nicer than normal");
                                        if (this.owner.getCurStat(1) <= this.owner.getMaxStat(1)) {
                                            this.owner.setCurStat(1, this.owner.getCurStat(1) + 1);
                                            this.owner.getActionSender().sendStat(1);
                                        }
                                    } else if (heals) {
                                        this.owner.getActionSender().sendMessage(InvActionHandler.healMessage(item.getID()));
                                    }
                                    this.owner.getInventory().remove(item);
                                    switch (item.getID()) {
                                        case 326: {
                                            this.owner.getInventory().add(new InvItem(328));
                                            break;
                                        }
                                        case 750: {
                                            this.owner.getInventory().add(new InvItem(751));
                                            break;
                                        }
                                        case 327: {
                                            this.owner.getInventory().add(new InvItem(329));
                                            break;
                                        }
                                        case 330: {
                                            this.owner.getInventory().add(new InvItem(333));
                                            break;
                                        }
                                        case 333: {
                                            this.owner.getInventory().add(new InvItem(335));
                                            break;
                                        }
                                        case 332: {
                                            this.owner.getInventory().add(new InvItem(334));
                                            break;
                                        }
                                        case 334: {
                                            this.owner.getInventory().add(new InvItem(336));
                                            break;
                                        }
                                        case 257: {
                                            this.owner.getInventory().add(new InvItem(263));
                                            break;
                                        }
                                        case 261: {
                                            this.owner.getInventory().add(new InvItem(251));
                                            break;
                                        }
                                        case 258: {
                                            this.owner.getInventory().add(new InvItem(262));
                                            break;
                                        }
                                        case 262: {
                                            this.owner.getInventory().add(new InvItem(251));
                                            break;
                                        }
                                        case 259: {
                                            this.owner.getInventory().add(new InvItem(261));
                                            break;
                                        }
                                        case 263: {
                                            this.owner.getInventory().add(new InvItem(251));
                                        }
                                    }
                                    this.owner.getActionSender().sendInventory();
                                    this.owner.setBusy(false);
                                }
                            });
                            break block61;
                        }
                        if (!item.getDef().getCommand().equalsIgnoreCase("bury")) break block62;
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You dig a hole in the ground.");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You bury the " + item.getDef().getName() + ".");
                                this.owner.getInventory().remove(item);
                                /*
                                 * Original prayer table (Tip.It classic /
                                 * classic wiki), in quarter-units: bones
                                 * 3.75, bat bones 4.5, big bones 12.5,
                                 * dragon bones 60.
                                 */
                                switch (item.getID()) {
                                    case 20: {
                                        this.owner.incExpQuarters(5, 15, true);
                                        break;
                                    }
                                    case 604: {
                                        this.owner.incExpQuarters(5, 18, true);
                                        break;
                                    }
                                    case 413: {
                                        this.owner.incExpQuarters(5, 50, true);
                                        break;
                                    }
                                    case 814: {
                                        this.owner.incExpQuarters(5, 240, true);
                                    }
                                }
                                this.owner.getActionSender().sendStat(5);
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        break block61;
                    }
                    /*
                     * HERBLAW WAS DEAD ON ARRIVAL. This checked for the
                     * command "clean" -- but ItemDef.xml.gz, sourced from the
                     * official cache, gives every unidentified herb the
                     * command "Identify" (that's the menu text a player
                     * actually right-clicks). No item in the whole definition
                     * table has ever had "clean" as its command, so this
                     * branch could never be reached: identifying a herb fell
                     * straight through to the generic "Nothing interesting
                     * happens", regardless of herblaw level or the Druidic
                     * Ritual quest.
                     */
                    if (!item.getDef().getCommand().equalsIgnoreCase("identify")) break block63;
                    if (!canDoHerblaw(player)) {
                        return;
                    }
                    ItemUnIdentHerbDef herb = item.getUnIdentHerbDef();
                    if (herb == null) {
                        return;
                    }
                    if (player.getMaxStat(15) < herb.getLevelRequired()) {
                        player.getActionSender().sendMessage("@gry@ Your herblaw ability is not high enough to clean this herb.");
                        return;
                    }
                    player.setBusy(true);
                    world.getDelayedEventHandler().add(new MiniEvent(player){

                        public void action() {
                            ItemUnIdentHerbDef herb = item.getUnIdentHerbDef();
                            InvItem newItem = new InvItem(herb.getNewId());
                            this.owner.getInventory().remove(item);
                            this.owner.getInventory().add(newItem);
                            this.owner.getActionSender().sendMessage("@pnk@ You clean the mud off the " + newItem.getDef().getName() + ".");
                            this.owner.incExp(15, herb.getExp(), true);
                            this.owner.getActionSender().sendStat(15);
                            this.owner.getActionSender().sendInventory();
                            this.owner.setBusy(false);
                        }
                    });
                    break block61;
                }
                if (!item.getDef().getCommand().equalsIgnoreCase("drink")) break block64;
                switch (item.getID()) {
                    case 739: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ It's nice and refreshing.");
                                InvActionHandler.this.boostStat(this.owner, 0, 2, 3);
                                this.owner.getInventory().remove(item);
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 193: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel slightly dizzy.");
                                InvActionHandler.this.drainStat(this.owner, 0, 5, 1);
                                if (this.owner.getCurStat(2) <= this.owner.getMaxStat(2)) {
                                    this.owner.setCurStat(2, this.owner.getCurStat(2) + 2);
                                    this.owner.getActionSender().sendStat(2);
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 830: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ It has a strange taste.");
                                InvActionHandler.this.drainStat(this.owner, 0, 5, 0);
                                for (int stat = 1; stat < 3; ++stat) {
                                    this.owner.setCurStat(stat, this.owner.getCurStat(stat) - 4);
                                    this.owner.getActionSender().sendStat(stat);
                                }
                                if (this.owner.getCurStat(15) <= this.owner.getMaxStat(15)) {
                                    this.owner.setCurStat(15, this.owner.getCurStat(15) + 1);
                                    this.owner.getActionSender().sendStat(15);
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 268: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel very strange.");
                                InvActionHandler.this.drainStat(this.owner, 0, 5, 0);
                                for (int stat = 1; stat < 3; ++stat) {
                                    this.owner.setCurStat(stat, this.owner.getCurStat(stat) - 4);
                                    this.owner.getActionSender().sendStat(stat);
                                }
                                if (this.owner.getCurStat(6) <= this.owner.getMaxStat(6)) {
                                    this.owner.setCurStat(6, this.owner.getCurStat(6) + 2);
                                    this.owner.getActionSender().sendStat(6);
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 269: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@que@It tastes foul");
                                /* The stout has a second line, a beat behind
                                   the first, that this never sent. */
                                world.getDelayedEventHandler().add(new MiniEvent(this.owner, 600){

                                    public void action() {
                                        this.owner.getActionSender().sendMessage("@que@It tastes pretty strong too");
                                    }
                                });
                                InvActionHandler.this.drainStat(this.owner, 0, 5, 0);
                                for (int stat = 1; stat < 3; ++stat) {
                                    this.owner.setCurStat(stat, this.owner.getCurStat(stat) - 4);
                                    this.owner.getActionSender().sendStat(stat);
                                }
                                if (this.owner.getCurStat(13) <= this.owner.getMaxStat(13)) {
                                    this.owner.setCurStat(13, this.owner.getCurStat(13) + 1);
                                    this.owner.getActionSender().sendStat(13);
                                }
                                if (this.owner.getCurStat(14) <= this.owner.getMaxStat(14)) {
                                    this.owner.setCurStat(14, this.owner.getCurStat(14) + 1);
                                    this.owner.getActionSender().sendStat(14);
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 267: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel slightly reinvigorated");
                                this.owner.getActionSender().sendMessage("@pnk@ And slightly dizzy too.");
                                InvActionHandler.this.drainStat(this.owner, 0, 5, 1);
                                if (this.owner.getCurStat(2) <= this.owner.getMaxStat(2)) {
                                    this.owner.setCurStat(2, this.owner.getCurStat(2) + 2);
                                    this.owner.getActionSender().sendStat(2);
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 829: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel slightly dizzy.");
                                InvActionHandler.this.drainStat(this.owner, 0, 5, 1);
                                if (this.owner.getCurStat(2) <= this.owner.getMaxStat(2)) {
                                    this.owner.setCurStat(2, this.owner.getCurStat(2) + 2);
                                    this.owner.getActionSender().sendStat(2);
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 221: {
                        this.useNormalPotion(player, item, 2, 10, 2, 222, 3);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 222: {
                        this.useNormalPotion(player, item, 2, 10, 2, 223, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 223: {
                        this.useNormalPotion(player, item, 2, 10, 2, 224, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 224: {
                        this.useNormalPotion(player, item, 2, 10, 2, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    /* Cure poison and antidote. RSCD left a comment where
                       these should have gone -- "HANDLE WINE+ CURE POISON AND
                       ANTIDOTE AND ZAMAROCK POTIONS" -- and never wrote them,
                       so both potions were drinkable and did nothing at all.
                       Ids run 3, 2, 1 doses; the last empties into a vial like
                       every other potion here. */
                    case 566: {
                        this.useCurePoisonPotion(player, item, 567, 2, false);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 567: {
                        this.useCurePoisonPotion(player, item, 568, 1, false);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 568: {
                        this.useCurePoisonPotion(player, item, 465, 0, false);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 569: {
                        this.useCurePoisonPotion(player, item, 570, 2, true);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 570: {
                        this.useCurePoisonPotion(player, item, 571, 1, true);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 571: {
                        this.useCurePoisonPotion(player, item, 465, 0, true);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 474: {
                        this.useNormalPotion(player, item, 0, 10, 3, 475, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 475: {
                        this.useNormalPotion(player, item, 0, 10, 3, 476, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 476: {
                        this.useNormalPotion(player, item, 0, 10, 3, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 477: {
                        this.useStatRestorePotion(player, item, 478, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 478: {
                        this.useStatRestorePotion(player, item, 479, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 479: {
                        this.useStatRestorePotion(player, item, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 480: {
                        this.useNormalPotion(player, item, 1, 10, 2, 481, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 481: {
                        this.useNormalPotion(player, item, 1, 10, 2, 482, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 482: {
                        this.useNormalPotion(player, item, 1, 10, 2, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 483: {
                        this.usePrayerPotion(player, item, 484, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 484: {
                        this.usePrayerPotion(player, item, 485, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 485: {
                        this.usePrayerPotion(player, item, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 486: {
                        this.useNormalPotion(player, item, 0, 15, 5, 487, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 487: {
                        this.useNormalPotion(player, item, 0, 15, 5, 488, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 488: {
                        this.useNormalPotion(player, item, 0, 15, 5, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 489: {
                        this.useFishingPotion(player, item, 490, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 490: {
                        this.useFishingPotion(player, item, 491, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 491: {
                        this.useFishingPotion(player, item, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 492: {
                        this.useNormalPotion(player, item, 2, 15, 4, 493, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 493: {
                        this.useNormalPotion(player, item, 2, 15, 4, 494, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 494: {
                        this.useNormalPotion(player, item, 2, 15, 4, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 495: {
                        this.useNormalPotion(player, item, 1, 15, 4, 496, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 496: {
                        this.useNormalPotion(player, item, 1, 15, 4, 497, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 497: {
                        this.useNormalPotion(player, item, 1, 15, 4, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 498: {
                        this.useNormalPotion(player, item, 4, 10, 2, 499, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 499: {
                        this.useNormalPotion(player, item, 4, 10, 2, 500, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 500: {
                        this.useNormalPotion(player, item, 4, 10, 2, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    /*
                     * Potion of Zamorak -- unimplemented before this fix, despite
                     * having a full 3-dose ItemDef chain (963/964/965) like every
                     * other potion. classic.runescape.wiki's Attack boosts/drains
                     * table: +20% + 2 levels on the first two doses, +20% + 4 on
                     * the final one -- an unusual per-dose difference no other
                     * potion in this codebase has, which is presumably why it was
                     * never wired the same way as the rest by whoever built this.
                     */
                    case 963: {
                        this.useNormalPotion(player, item, 0, 20, 2, 964, 2);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 964: {
                        this.useNormalPotion(player, item, 0, 20, 2, 965, 1);
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 965: {
                        this.useNormalPotion(player, item, 0, 20, 4, 465, 0);
                        this.showBubble(player, item);
                        break block61;
                    }
                    /*
                     * Wine, half full wine jug, poison chalice, brandy, vodka,
                     * whisky, grog -- none of these had a case at all before this
                     * fix (falling to "Nothing interesting happens", not even
                     * consuming the item). Table figures for these are flat
                     * levels, not percent, so drainStat/boostStat are called with
                     * percent 0.
                     */
                    case 142: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel a little tipsy.");
                                InvActionHandler.this.drainStat(this.owner, 0, 0, 3);
                                this.owner.getInventory().remove(item);
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 246: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel a little tipsy.");
                                InvActionHandler.this.drainStat(this.owner, 0, 0, 1);
                                /*
                                 * Missed on the first pass through this drink
                                 * -- the earlier fix only had the Attack
                                 * drain. Real documented effect (last
                                 * reported status, since it's discontinued
                                 * and untested by design): heals 5 Hits on
                                 * top of the -1 Attack, same as a normal jug
                                 * of wine.
                                 */
                                if (this.owner.getCurStat(3) < this.owner.getMaxStat(3)) {
                                    this.owner.setCurStat(3, Math.min(this.owner.getMaxStat(3), this.owner.getCurStat(3) + 5));
                                    this.owner.getActionSender().sendStat(3);
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 737: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                if (DataConversions.random(0, 1) == 0) {
                                    this.owner.getActionSender().sendMessage("@pnk@ That tasted good!");
                                    InvActionHandler.this.boostStat(this.owner, 0, 4, 4);
                                } else {
                                    this.owner.getActionSender().sendMessage("@pnk@ That tasted awful!");
                                    InvActionHandler.this.drainStat(this.owner, 0, 0, 3);
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 876: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel quite drunk.");
                                InvActionHandler.this.drainStat(this.owner, 0, 0, DataConversions.random(3, 4));
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 869: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel quite drunk.");
                                InvActionHandler.this.drainStat(this.owner, 0, 0, DataConversions.random(3, 4));
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 584:
                    case 868: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel very drunk.");
                                InvActionHandler.this.drainStat(this.owner, 0, 0, 4);
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    case 598: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ You feel extremely drunk.");
                                InvActionHandler.this.drainStat(this.owner, 0, 0, 6);
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(620));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    /*
                     * The seven gnome cocktails of Blurberry's bar, and the
                     * seven "premade" ids the barman sells over the counter.
                     *
                     * The first pass at these gave the five alcoholic ones the
                     * attack drain out of the boosts table and nothing else,
                     * and handed back a beer glass. All three parts were
                     * wrong: they also heal and they also raise strength, and
                     * what a cocktail leaves behind is a cocktail glass.
                     *
                     * Alcoholic: heal 5, attack -3% and one more level,
                     * strength +6% and one more level -- five drinks, one set
                     * of figures, all five confirmed by replay on the wiki's
                     * Strength and Attack boosts/drains tables.
                     *
                     * Non-alcoholic: the fruit blast and the pineapple punch
                     * heal 8 and 9 and touch no stat at all.
                     */
                    case 866:
                    case 937: {
                        this.drinkCocktail(player, item, 8, false);
                        break block61;
                    }
                    case 879:
                    case 940: {
                        this.drinkCocktail(player, item, 9, false);
                        break block61;
                    }
                    case 877:
                    case 938: {
                        this.drinkCocktail(player, item, 5, true);
                        break block61;
                    }
                    case 875:
                    case 942: {
                        this.drinkCocktail(player, item, 5, true);
                        break block61;
                    }
                    case 872:
                    case 943: {
                        this.drinkCocktail(player, item, 5, true);
                        break block61;
                    }
                    case 874:
                    case 941: {
                        this.drinkCocktail(player, item, 5, true);
                        break block61;
                    }
                    case 878:
                    case 939: {
                        this.drinkCocktail(player, item, 5, true);
                        break block61;
                    }
                    /*
                     * The odd looking cocktail -- what you get for mixing a
                     * recipe in the wrong order. Recorded from a replay: -3
                     * attack, -1 defense, -3 strength, and it neither heals
                     * nor hurts. Flat levels, so the percentages are zero.
                     */
                    case 867: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the cocktail");
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                this.owner.getActionSender().sendMessage("@pnk@ It tastes awful..yuck");
                                InvActionHandler.this.drainStat(this.owner, 0, 0, 3);
                                InvActionHandler.this.drainStat(this.owner, 1, 0, 1);
                                InvActionHandler.this.drainStat(this.owner, 2, 0, 3);
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(GnomeCooking.COCKTAIL_GLASS));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    /*
                     * A cocktail glass part way through a recipe. Jagex left
                     * the drink command on both half-made ids, so it has to do
                     * something; it costs the mix and gives the glass back.
                     */
                    case 853:
                    case 854: {
                        GnomeCooking.drinkUnfinished(player, item);
                        break block61;
                    }
                    /*
                     * The one drink here that is food rather than a potion, so
                     * it heals off stat 3 and says so only when it actually
                     * healed -- the same rule the eating path follows.
                     */
                    case CHOCOLATY_MILK: {
                        player.setBusy(true);
                        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
                        final boolean hurt = player.getCurStat(3) < player.getMaxStat(3);
                        world.getDelayedEventHandler().add(new MiniEvent(player){

                            public void action() {
                                if (hurt) {
                                    int healed = this.owner.getCurStat(3) + CHOCOLATY_MILK_HEALS;
                                    if (healed > this.owner.getMaxStat(3)) {
                                        healed = this.owner.getMaxStat(3);
                                    }
                                    this.owner.setCurStat(3, healed);
                                    this.owner.getActionSender().sendStat(3);
                                    this.owner.getActionSender().sendMessage("@que@It heals some health");
                                }
                                this.owner.getInventory().remove(item);
                                this.owner.getInventory().add(new InvItem(BUCKET));
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        this.showBubble(player, item);
                        break block61;
                    }
                    default: {
                        player.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                        return;
                    }
                }
            }
            switch (item.getID()) {
                /*
                 * The two gnome recipe books. Both carry the command "read"
                 * and neither had a case anywhere, so buying either one got
                 * you "Nothing interesting happens" -- which is most of why
                 * gnome cooking was unplayable rather than merely unwired.
                 */
                case GnomeCooking.COCKTAIL_GUIDE: {
                    GnomeCooking.readCocktailGuide(player);
                    break;
                }
                case GnomeCooking.COOK_BOOK: {
                    GnomeCooking.readCookBook(player);
                    break;
                }
                /* The Magical Party Schedule ("read"). The page renders from a
                   fresh fetch over the LS link, not the cache -- see
                   MiscPacketBuilder.partyBookPage -- so the book is never five
                   minutes stale. The reply opens the alert window. */
                case org.rscdaemon.server.npchandler.PartyHall.MAGICAL_PARTY_SCHEDULE: {
                    world.getServer().getLoginConnector().getActionSender()
                            .partyBookPage(player);
                    break;
                }
                /* Gianne dough carries the command "mould", and shaping it is
                   where the 25 cooking experience of every gnome dish comes
                   from. */
                case GnomeCooking.GIANNE_DOUGH: {
                    GnomeCooking.mould(player, item);
                    break;
                }
                /* The two papers from the Shantay Pass, both of which carry
                   "read" and neither of which had a case: the disclaimer the
                   guard on the gate hands over, and the kebab recipe that
                   falls out of Shantay's pocket. See npchandler/ShantayPass. */
                case 1099: {
                    ShantayPass.readDisclaimer(player);
                    break;
                }
                /* The Bailing Bucket carries the command "bail with " and
                   is sold by the Fishing Trawler general store for no other
                   purpose. Nothing answered it, so the one item the shop
                   exists to sell did nothing at all. Murphy's own recorded
                   opinion is that bailing is a waste of time; it isn't,
                   quite -- it buys the crew a little water back. */
                case 1282: {
                    org.rscdaemon.server.npchandler.FishingTrawler.bail(player);
                    break;
                }
                case 1120: {
                    ShantayPass.readRecipe(player);
                    break;
                }
                /* A swamp toad's command is "remove legs". The toads west of
                   the Grand Tree are the only source of toad legs in the game
                   short of pickpocketing a gnome at 75 thieving, and three
                   recipes need them. */
                case 895: {
                    GnomeCooking.removeToadLegs(player, item);
                    break;
                }
                case 1256: {
                    player.getActionSender().sendMessage("@pnk@ You absorb the power from the obsidian...");
                    this.useDaggerCharge(player, item, 0, 15, 2, 1255, 0);
                    this.useDaggerCharge(player, item, 1, 15, 2, 1255, 0);
                    this.useDaggerCharge(player, item, 2, 15, 2, 1255, 0);
                    this.showBubble(player, item);
                    if (DataConversions.random(0, 5) == 1 && player.getInventory().remove(item) > -1) {
                        player.getInventory().add(new InvItem(1255, 1));
                        player.getActionSender().sendInventory();
                        player.getActionSender().sendMessage("@pnk@ You feel powerful as the glow disappears from your shank.");
                        break;
                    }
                    player.getActionSender().sendMessage("@pnk@ You feel powerful, yet the shank remains glowing.");
                    break;
                }
                case 597: {
                    /* No wilderness check here. Escaping the wilderness is the
                       whole point of this amulet: it teleports from as deep as
                       level 29, where every other teleport in the game stops at
                       20. The level 30 cut-off inside the menu handler is the
                       real limit, and it was already right. */
                    player.getActionSender().sendMessage("@pnk@ You rub the amulet...");
                    world.getDelayedEventHandler().add(new MiniEvent(player){

                        public void action() {
                            /* Four destinations and a way out, which is what the
                               recovered menu has. RSCD had added Mage Arena,
                               Seers and Yanille; those are not Jagex's and an
                               added teleport belongs on something of its own
                               rather than on a vanilla amulet.

                               Every one of the seven had also been overwritten
                               with teleport(555, 555) at some point, so the
                               amulet dropped you in a field outside Falador
                               whichever option you picked. The four coordinates
                               below are the originals. */
                            String[] options = new String[]{"Edgeville", "Karamja", "Draynor village", "Al Kharid", "Nowhere"};
                            this.owner.setMenuHandler(new MenuHandler(options){

                                public void handleReply(int option, String reply) {
                                    if (this.owner.isBusy() || this.owner.getInventory().get(item) == null) {
                                        return;
                                    }
                                    if (this.owner.getLocation().wildernessLevel() >= 30 || this.owner.getLocation().inModRoom() && !this.owner.isMod()) {
                                        this.owner.getActionSender().sendMessage("@gry@ Spell disabled.");
                                        return;
                                    }
                                    this.owner.getActionSender().sendSound("spellok");
                                    if (option >= 0 && option <= 3) {
                                        // Not on "Nowhere": no teleport, nothing lost.
                                        org.rscdaemon.server.util.Formulae.teleportContraband(this.owner);
                                    }
                                    switch (option) {
                                        case 0: {   // Edgeville
                                            this.owner.teleport(193, 435, true);
                                            break;
                                        }
                                        case 1: {   // Karamja, in Luthas' banana plantation
                                            this.owner.teleport(360, 696, true);
                                            break;
                                        }
                                        case 2: {   // Draynor village, on the road by the bank
                                            this.owner.teleport(214, 632, true);
                                            break;
                                        }
                                        case 3: {   // Al Kharid palace courtyard
                                            this.owner.teleport(72, 696, true);
                                            break;
                                        }
                                        default: {  // Nowhere
                                            return;
                                        }
                                    }
                                    /* RSCD had this as DataConversions.random(0, 5) == 1,
                                       a flat one-in-six roll per teleport. That is not
                                       what the amulet does: the count is four, it is
                                       exact, and it belongs to the player rather than
                                       to the amulet.

                                       Which is why it FEELS random. The counter is not
                                       reset by dipping and it is not shown anywhere, so
                                       a freshly charged amulet gives you however many
                                       teleports were left over from the last one --
                                       four if you had just run one out, one if you had
                                       not. Same item, same action, different answer. */
                                    if (this.owner.spendAmuletCharge() && this.owner.getInventory().remove(item) > -1) {
                                        /* No message. The recovered transcript has
                                           "You rub the amulet" and the menu and
                                           nothing else, and the fountain has already
                                           said "using it to much means you will need
                                           to recharge it". The amulet going quiet in
                                           your inventory is the whole notification. */
                                        this.owner.getInventory().add(new InvItem(522, 1));
                                        this.owner.getActionSender().sendInventory();
                                    }
                                }
                            });
                            this.owner.getActionSender().sendMenu(options);
                        }
                    });
                    break;
                }
                case 796: {
                    /* Mithril seeds, 40 of which fall out of Waterfall quest
                       and which the Legends' Guild shop restocks. Opening the
                       case plants a pointy tree on the tile you are standing
                       on; it cannot be chopped and it goes away after about
                       thirty seconds. The item has carried its "open" command
                       since Tutorial island in 2002 and nothing has ever
                       answered it here.

                       All three outcomes and their wording are Jagex's. */
                    if (world.isIndoors(player.getX(), player.getY())) {
                        player.getActionSender().sendMessage("@pnk@ you open the small mithril case");
                        player.getActionSender().sendMessage("@pnk@ you can't plant a tree in here");
                        return;
                    }
                    if (world.getTile(player.getX(), player.getY()).hasGameObject()) {
                        player.getActionSender().sendMessage("@pnk@ you open the small mithril case");
                        player.getActionSender().sendMessage("@pnk@ you can't plant a tree here");
                        return;
                    }
                    if (player.getInventory().remove(796, 1) < 0) {
                        return;
                    }
                    player.getActionSender().sendInventory();
                    player.getActionSender().sendMessage("@pnk@ you open the small mithril case");
                    player.getActionSender().sendMessage("@pnk@ and drop a seed by your feet");
                    player.getActionSender().sendMessage("@pnk@ a tree magically sprouts around you");
                    GameObject tree = new GameObject(Point.location(player.getX(), player.getY()), 490, 0, 0);
                    world.registerGameObject(tree);
                    world.delayedRemoveObject(tree, 32000);
                    break;
                }
                case 387: {
                    /*
                     * The Disk of Returning's real mechanic: it only works
                     * inside Thordur's Black Hole, and takes you back to him,
                     * not to Edgeville -- the generic anywhere-to-Edgeville
                     * teleport this replaced was invented, not Jagex's (there
                     * was never a "from any location" version of this in the
                     * removal history). No 7-second stand-still gate either;
                     * the real complaint about this item was people getting
                     * TRICKED into dropping it, not that it worked too fast.
                     */
                    if (!player.getLocation().inBlackHole()) {
                        player.getActionSender().sendMessage("@gry@ The disk will only work from in Thordur's black hole");
                        return;
                    }
                    player.resetPath();
                    player.teleport(ThordurHandler.THORDUR_X, ThordurHandler.THORDUR_Y, true);
                    player.getInventory().remove(item);
                    player.getActionSender().sendMessage("@pnk@ You spin the disk");
                    player.getActionSender().sendMessage("@pnk@ You find yourself back near Thordur and the ladder");
                    player.getActionSender().sendMessage("@pnk@ The disk has now gone");
                    player.getActionSender().sendInventory();
                    break;
                }
                case 958: {
                    if (player.getLocation().wildernessLevel() >= 1 || player.getLocation().inModRoom() && !player.isMod()) {
                        player.getActionSender().sendMessage("@gry@ Object disabled.");
                        return;
                    }
                    player.getActionSender().sendMessage("@pnk@ The stone starts to glow...");
                    world.getDelayedEventHandler().add(new MiniEvent(player){

                        public void action() {
                            this.owner.resetPath();
                            this.owner.teleport(569, 3331, true);
                            this.owner.getActionSender().sendMessage("@pnk@ You find yourself in an unusual area");
                        }
                    });
                    if (DataConversions.random(0, 5) != 1 || player.getInventory().remove(item) <= -1) break;
                    player.getActionSender().sendInventory();
                    player.getActionSender().sendMessage("@pnk@ The mark has exhausted its power");
                    break;
                }
                case 1263: {
                    player.resetPath();
                    player.setBusy(true);
                    player.getActionSender().sendMessage("@pnk@ You rest in the sleeping bag");
                    world.getDelayedEventHandler().add(new ShortEvent(player){

                        public void action() {
                            this.owner.setFatigue(0);
                            this.owner.getActionSender().sendFatigue();
                            this.owner.getActionSender().sendMessage("@pnk@ You wake up - feeling refreshed");
                            this.owner.setBusy(false);
                        }
                    });
                    this.showBubble(player, item);
                    break;
                }
                /*
                 * The war ship model, command "play with", was never claimed
                 * by any quest and had no case here, so it fell to the
                 * generic "Nothing interesting happens." Jagex's own line is
                 * more specific than that.
                 */
                case 920: {
                    player.getActionSender().sendMessage("@pnk@ You pretend to sail the ship across the floor. You soon become very bored, and realise you look quite silly.");
                    break;
                }
                default: {
                    player.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                    return;
                }
            }
        }
    }

    private void showBubble(Player player, InvItem item) {
        Bubble bubble = new Bubble(player, item.getID());
        for (Player p1 : player.getViewArea().getPlayersInView()) {
            p1.informOfBubble(bubble);
        }
    }

    private void useNormalPotion(Player player, final InvItem item, final int affectedStat, final int percentageIncrease, final int modifier, final int newItem, final int left) {
        player.setBusy(true);
        player.getActionSender().sendMessage("@pnk@ You drink some of your " + item.getDef().getName() + ".");
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                this.owner.getActionSender().sendMessage("@pnk@ You have " + left + " doses left.");
                int baseStat = this.owner.getCurStat(affectedStat) > this.owner.getMaxStat(affectedStat) ? this.owner.getMaxStat(affectedStat) : this.owner.getCurStat(affectedStat);
                int newStat = baseStat + DataConversions.roundUp((double)this.owner.getMaxStat(affectedStat) / 100.0 * (double)percentageIncrease) + modifier;
                if (newStat > this.owner.getCurStat(affectedStat)) {
                    this.owner.setCurStat(affectedStat, newStat);
                    this.owner.getActionSender().sendStat(affectedStat);
                }
                this.owner.getInventory().remove(item);
                this.owner.getInventory().add(new InvItem(newItem));
                this.owner.getActionSender().sendInventory();
                this.owner.setBusy(false);
            }
        });
    }

    /*
     * The percent+flat drain counterpart to useNormalPotion, which is boost-
     * only by construction (it never applies unless the result is HIGHER
     * than the current stat). Ales/wines/cocktails all drain by a documented
     * "-X% - Y levels" shape per classic.runescape.wiki's Attack boosts/
     * drains table, sourced from RSC+ replay recordings. percent and
     * flat are both taken as
     * positive magnitudes here; the drain itself is the subtraction.
     */
    private void drainStat(Player player, int affectedStat, double percent, int flat) {
        int amount = DataConversions.roundUp((double) player.getMaxStat(affectedStat) / 100.0 * percent) + flat;
        player.setCurStat(affectedStat, player.getCurStat(affectedStat) - amount);
        player.getActionSender().sendStat(affectedStat);
    }

    /*
     * Flat random boost, for the handful of drinks the table gives a plain
     * level range rather than a percent -- Cup of tea (+2-3) and one branch
     * of Poison chalice's random +4/-3. Guarded the same way the ales' own
     * side-boosts already were (only applies while not already above max),
     * so repeat drinking cannot stack indefinitely.
     */
    private void boostStat(Player player, int affectedStat, int min, int max) {
        if (player.getCurStat(affectedStat) > player.getMaxStat(affectedStat)) {
            return;
        }
        int amount = min == max ? min : DataConversions.random(min, max);
        player.setCurStat(affectedStat, player.getCurStat(affectedStat) + amount);
        player.getActionSender().sendStat(affectedStat);
    }

    /*
     * A gnome cocktail. Fourteen ids drink identically -- seven recipes, each
     * with a "premade" twin the barman sells -- so they share one method
     * rather than fourteen copies of the same anonymous event.
     *
     * The alcoholic five all carry the same figures: -3% and one more level of
     * Attack, +6% and one more level of Strength. boostStat takes flat levels
     * only, so the percent half is worked out here.
     *
     * Whichever it was, the glass it came in is left behind -- a cocktail
     * glass, item 833, which is the empty end of the same chain the player
     * pours a new drink into.
     */
    private void drinkCocktail(Player player, final InvItem item, final int heals, final boolean alcoholic) {
        player.setBusy(true);
        player.getActionSender().sendMessage("@pnk@ You drink the " + item.getDef().getName() + ".");
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                if (alcoholic) {
                    this.owner.getActionSender().sendMessage("@pnk@ You feel slightly dizzy.");
                    InvActionHandler.this.drainStat(this.owner, 0, 3, 1);
                    int strength = DataConversions.roundUp((double) this.owner.getMaxStat(2) / 100.0 * 6.0) + 1;
                    InvActionHandler.this.boostStat(this.owner, 2, strength, strength);
                }
                if (this.owner.getCurStat(3) < this.owner.getMaxStat(3)) {
                    this.owner.setCurStat(3, Math.min(this.owner.getMaxStat(3),
                            this.owner.getCurStat(3) + heals));
                    this.owner.getActionSender().sendStat(3);
                }
                this.owner.getInventory().remove(item);
                this.owner.getInventory().add(new InvItem(GnomeCooking.COCKTAIL_GLASS));
                this.owner.getActionSender().sendInventory();
                this.owner.setBusy(false);
            }
        });
        this.showBubble(player, item);
    }

    private void useDaggerCharge(Player player, InvItem item, final int affectedStat, final int percentageIncrease, final int modifier, int newItem, int left) {
        player.setBusy(true);
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                int baseStat = this.owner.getCurStat(affectedStat) > this.owner.getMaxStat(affectedStat) ? this.owner.getMaxStat(affectedStat) : this.owner.getCurStat(affectedStat);
                int newStat = baseStat + DataConversions.roundUp((double)this.owner.getMaxStat(affectedStat) / 100.0 * (double)percentageIncrease) + modifier;
                if (newStat > this.owner.getCurStat(affectedStat)) {
                    this.owner.setCurStat(affectedStat, newStat);
                    this.owner.getActionSender().sendStat(affectedStat);
                }
                this.owner.getActionSender().sendInventory();
                this.owner.setBusy(false);
            }
        });
    }

    private void usePrayerPotion(Player player, final InvItem item, final int newItem, final int left) {
        player.setBusy(true);
        player.getActionSender().sendMessage("@pnk@ You drink some of your " + item.getDef().getName() + ".");
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                this.owner.getActionSender().sendMessage("@pnk@ You have " + left + " doses left.");
                int newPrayer = this.owner.getCurStat(5) + 21;
                if (newPrayer > this.owner.getMaxStat(5)) {
                    newPrayer = this.owner.getMaxStat(5);
                }
                this.owner.setCurStat(5, newPrayer);
                this.owner.getInventory().remove(item);
                this.owner.getInventory().add(new InvItem(newItem));
                this.owner.getActionSender().sendStat(5);
                this.owner.getActionSender().sendInventory();
                this.owner.setBusy(false);
            }
        });
    }

    private void useStatRestorePotion(Player player, final InvItem item, final int newItem, final int left) {
        player.setBusy(true);
        player.getActionSender().sendMessage("@pnk@ You drink some of your " + item.getDef().getName() + ".");
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                this.owner.getActionSender().sendMessage("@pnk@ You have " + left + " doses left.");
                for (int i = 0; i < 19; ++i) {
                    if (i == 3 || i == 5) continue;
                    int max = this.owner.getMaxStat(i);
                    if (this.owner.getCurStat(i) >= max) continue;
                    this.owner.setCurStat(i, max);
                    this.owner.getActionSender().sendStat(i);
                }
                this.owner.getInventory().remove(item);
                this.owner.getInventory().add(new InvItem(newItem));
                this.owner.getActionSender().sendInventory();
                this.owner.setBusy(false);
            }
        });
    }

    /**
     * Cure poison potion and poison antidote.
     *
     * Both cure outright and then hold poison off for a while: the cure poison
     * potion "provides protection from poison for about 2:30-5:30 minutes" and
     * the antidote "for about 6-12:30 minutes". The window is rolled per drink
     * between those bounds, which is what "about" has to mean -- a fixed
     * number in that range would not be described as a range.
     *
     * Neither potion says anything when it works. Vanilla had no message for
     * being poisoned and none for being cured; the doses line is the ordinary
     * one every potion here sends.
     */
    private void useCurePoisonPotion(Player player, final InvItem item, final int newItem,
            final int left, final boolean antidote) {
        player.setBusy(true);
        player.getActionSender().sendMessage("@pnk@ You drink some of your " + item.getDef().getName() + ".");
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                this.owner.getActionSender().sendMessage("@pnk@ You have " + left + " doses left.");
                if (antidote) {
                    Poison.cure(this.owner, 360000, 750000);   /* 6:00 - 12:30 */
                } else {
                    Poison.cure(this.owner, 150000, 330000);   /* 2:30 - 5:30  */
                }
                this.owner.getInventory().remove(item);
                this.owner.getInventory().add(new InvItem(newItem));
                this.owner.getActionSender().sendInventory();
                this.owner.setBusy(false);
            }
        });
    }

    private void useFishingPotion(Player player, final InvItem item, final int newItem, final int left) {
        player.setBusy(true);
        player.getActionSender().sendMessage("@pnk@ You drink some of your " + item.getDef().getName() + ".");
        world.getDelayedEventHandler().add(new MiniEvent(player){

            public void action() {
                this.owner.getActionSender().sendMessage("@pnk@ You have " + left + " doses left.");
                this.owner.setCurStat(10, this.owner.getMaxStat(10) + 3);
                this.owner.getInventory().remove(item);
                this.owner.getInventory().add(new InvItem(newItem));
                this.owner.getActionSender().sendStat(10);
                this.owner.getActionSender().sendInventory();
                this.owner.setBusy(false);
            }
        });
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

