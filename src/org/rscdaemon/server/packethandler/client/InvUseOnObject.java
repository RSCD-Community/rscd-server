/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import java.util.List;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.extras.ItemCookingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ObjectAgilityDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemCraftingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemSmeltingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemSmithingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemWieldableDef;
import org.rscdaemon.server.entityhandling.defs.extras.ReqOreDef;
import org.rscdaemon.server.entityhandling.locs.GameObjectLoc;
import org.rscdaemon.server.event.MiniEvent;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.event.WalkToObjectEvent;
import org.rscdaemon.server.model.ActiveTile;
import org.rscdaemon.server.model.Bubble;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.GnomeCooking;
import org.rscdaemon.server.model.Inventory;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Mill;
import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Multicannon;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;
import org.rscdaemon.server.util.Smelting;

public class InvUseOnObject
implements PacketHandler {
    /**
     * What the sinister chest holds: 1 torstol, 1 kwuarm, 1 avantoe, 1 irit
     * leaf, 3 ranarr weed and 2 harralander, all grimy. Nine herbs, always the
     * same nine -- the wiki lists every one at "Always".
     */
    private static final int[] SINISTER_HERBS = { 933, 441, 440, 439, 438, 438, 438, 437, 437 };

    public static final World world = World.getWorld();

    /** The tree off Grew's island before and after a rope is tied to it. */
    private static final int TREE_FOR_ROPE = 662, TREE_WITH_ROPE = 663;

    /**
     * How long a tied rope stays tied.
     *
     * Classic's rule was per-player: the rope stayed for an unlimited number of
     * swings and vanished when the player who tied it logged out. Objects here
     * are global -- every player in the region sees the same tree -- so that
     * rule cannot be expressed, and the honest approximation is a clock. Five
     * minutes is long enough for the visit the rope is tied for and short
     * enough that the next player brings their own, which is what the quest's
     * "2 ropes" item list assumes.
     */
    private static final int ROPE_LIFETIME = 300000;

    /**
     * Swap the bare tree for the roped one, and swap it back when the rope
     * rots. Nothing else in the world does this, which is why it is written out
     * rather than being a World method: there is no general "replace this
     * object with that one" in the game, only spawn and despawn.
     */
    private static void tieRope(final GameObjectLoc tree) {
        ActiveTile tile = world.getTile(tree.getX(), tree.getY());
        if (tile == null || !tile.hasGameObject() || tile.getGameObject().getID() != TREE_FOR_ROPE) {
            return;
        }
        world.unregisterGameObject(tile.getGameObject());
        final GameObject swing = new GameObject(new GameObjectLoc(TREE_WITH_ROPE,
            tree.getX(), tree.getY(), tree.getDirection(), tree.getType()));
        world.registerGameObject(swing);
        world.getDelayedEventHandler().add(new SingleEvent(null, ROPE_LIFETIME){

            public void action() {
                ActiveTile t = world.getTile(swing.getLocation());
                // Somebody may have got there first; only undo our own rope.
                if (t != null && t.hasGameObject() && t.getGameObject().equals(swing)) {
                    world.unregisterGameObject(swing);
                    world.registerGameObject(new GameObject(tree));
                }
            }
        });
    }

    /**
     * Whether this item can cut a web: the knife, or anything held in the
     * weapon hand -- Classic asked for "a knife or a sharp weapon" and let the
     * whole weapon slot answer for the second half.
     *
     * The two exceptions are recorded and are the reason this is a method
     * rather than the inline wieldPos test it replaced. The machette and the
     * scythe are weapon-slot items and they do not cut webs, which is the sort
     * of detail somebody carrying a machette through the jungle finds out the
     * hard way.
     */
    private static boolean cutsWebs(InvItem item) {
        if (item.getID() == 13) {
            return true;
        }
        if (item.getID() == 1172 || item.getID() == 1289) {
            return false;
        }
        ItemWieldableDef def = item.getWieldableDef();
        return def != null && def.getWieldPos() == 4;
    }

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        ActiveTile tile = world.getTile(p.readShort(), p.readShort());
        if (tile == null) {
            player.setSuspiciousPlayer(true);
            player.resetPath();
            return;
        }
        switch (pID) {
            case 36: {
                byte dir = p.readByte();
                InvItem item = player.getInventory().get(p.readShort());
                GameObject door = tile.getDoor();
                if (door == null || item == null) {
                    player.setSuspiciousPlayer(true);
                    return;
                }
                this.handleDoor(player, tile, door, dir, item);
                break;
            }
            case 94: {
                InvItem item = player.getInventory().get(p.readShort());
                GameObject object = tile.getGameObject();
                if (object == null || item == null) {
                    player.setSuspiciousPlayer(true);
                    return;
                }
                this.handleObject(player, tile, object, item);
            }
        }
    }

    private void handleObject(final Player player, final ActiveTile tile, GameObject object, final InvItem item) {
        player.setStatus(Action.USING_INVITEM_ON_OBJECT);
        world.getDelayedEventHandler().add(new WalkToObjectEvent(player, object, false){

            public void arrived() {
                this.owner.resetPath();
                if (this.owner.isBusy() || this.owner.isRanging() || !this.owner.getInventory().contains(item) || !this.owner.nextTo(this.object) || !tile.hasGameObject() || !tile.getGameObject().equals(this.object) || this.owner.getStatus() != Action.USING_INVITEM_ON_OBJECT) {
                    return;
                }
                this.owner.resetAll();
                // A quest that has associated this object owns it outright, the
                // same contract npc handlers get in TalkToNpcHandler and scenery
                // gets in ObjectAction.
                if (this.owner.getQuestManager().associatedWithQuest(this.object)) {
                    this.owner.getQuestManager().triggerEntity(QuestTrigger.ITEM_ON_OBJECT, this.object, item);
                    return;
                }
                /* The windmills: grain into a hopper, an empty pot under the
                   chute. Ahead of the switch because there are four hoppers with
                   four different ids and nothing else to say about them -- and
                   because until now there was no way to make a pot of flour
                   anywhere in the game, which is half of Cook's assistant. */
                if (Mill.handle(this.owner, this.object, item)) {
                    return;
                }
                /* The runecrafting altars: a talisman (or essence, carrying
                   the talisman) binds every essence held at once. */
                if (org.rscdaemon.server.model.Runecrafting.handle(this.owner, this.object, item)) {
                    return;
                }
                /* The Party Cannons: any item used on one is a load, and the
                   whole carried stack of it goes in at once. */
                if (this.object.getID() == org.rscdaemon.server.model.PartyCannon.OBJECT) {
                    org.rscdaemon.server.model.PartyCannon.load(this.owner, this.object, item);
                    return;
                }
                /* The cannon is built on whatever tile its owner chose, so
                   there is no fixed object id to put in the switch. Ask the
                   cannon itself whether this object is one of its four, and let
                   it refuse parts that do not go on next. */
                Multicannon cannon = Multicannon.at(this.object);
                if (cannon != null && cannon.fit(this.owner, item.getID())) {
                    return;
                }
                /* An agility obstacle that asks for an item answers Use with
                   that item the same as its own command -- the Underground
                   Pass swamps say "Use the rocks on the swamp" in as many
                   words, and until now only "step over" listened. The item is
                   a tie, not a cost, exactly as in ObjectAction. */
                if (this.handleAgilityItem()) {
                    return;
                }
                switch (this.object.getID()) {
                    case 383: {
                        /* The Coal Trucks: coal in at the mine, coal out west
                           of Seers' Village bank. One lump per use, capacity
                           121 as in 2002; the count is the player's own,
                           persisted under quest-stage id 3000. The withdrawal
                           half is ObjectAction.handleCoalTruck. */
                        if (item.getID() != 155) {
                            break;
                        }
                        int stored = Math.max(0, this.owner.getQuestStage(3000));
                        if (stored >= 121) {
                            this.owner.getActionSender().sendMessage("@gry@ The truck is full");
                            return;
                        }
                        if (this.owner.getInventory().remove(item) > -1) {
                            this.owner.setQuestStage(3000, stored + 1);
                            this.owner.getActionSender().sendInventory();
                            this.owner.getActionSender().sendMessage("@pnk@ You put the coal into the truck");
                        }
                        return;
                    }
                    case 282: {
                        /* The five lines are Jagex's, from the recovered
                           transcript, including "to much" -- the game really
                           did spell it that way. The last two were missing
                           entirely, which mattered: they are the only place
                           the game tells you the amulet runs out of charges
                           and that it improves your gem finds while mining. */
                        if (item.getID() == 522) {
                            this.owner.getActionSender().sendMessage("@pnk@ You dip the amulet in the fountain");
                            this.owner.setBusy(true);
                            world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                public void action() {
                                    this.owner.getActionSender().sendMessage("@pnk@ You feel more power emanating from it than before");
                                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                        public void action() {
                                            if (this.owner.getInventory().remove(item) > -1) {
                                                this.owner.getInventory().add(new InvItem(597));
                                                this.owner.getActionSender().sendInventory();
                                                this.owner.getActionSender().sendMessage("@pnk@ you can now rub this amulet to teleport");
                                                this.owner.getActionSender().sendMessage("@pnk@ Though using it to much means you will need to recharge it");
                                                this.owner.getActionSender().sendMessage("@pnk@ It now also means you can find more gems when mining");
                                            }
                                            this.owner.setBusy(false);
                                        }
                                    });
                                }
                            });
                            break;
                        }
                        if (item.getID() == 1255) {
                            this.owner.getActionSender().sendMessage("@pnk@ You dip the obsidian shank in the fountain...");
                            this.owner.setBusy(true);
                            world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                public void action() {
                                    this.owner.getActionSender().sendMessage("@pnk@ You feel more power coming from it than before.");
                                    world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                        public void action() {
                                            if (this.owner.getInventory().remove(item) > -1) {
                                                this.owner.getActionSender().sendMessage("@gry@ You can now absorb the power from it.");
                                                this.owner.getInventory().add(new InvItem(1256));
                                                this.owner.getActionSender().sendInventory();
                                            }
                                            this.owner.setBusy(false);
                                        }
                                    });
                                }
                            });
                            break;
                        }
                    }
                    case 2: 
                    case 26: 
                    case 48: 
                    case 86: 
                    case 466: 
                    case 814: 
                    case 1130: {
                        if (!(this.itemId(new int[]{21, 140, 465}) || this.itemId(Formulae.potionsUnfinished) || this.itemId(Formulae.potions1Dose) || this.itemId(Formulae.potions2Dose) || this.itemId(Formulae.potions3Dose))) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        if (this.owner.getInventory().remove(item) <= -1) break;
                        showBubble();
                        this.owner.getActionSender().sendSound("filljug");
                        switch (item.getID()) {
                            case 21: {
                                this.owner.getInventory().add(new InvItem(50));
                                break;
                            }
                            case 140: {
                                this.owner.getInventory().add(new InvItem(141));
                                break;
                            }
                            default: {
                                this.owner.getInventory().add(new InvItem(464));
                            }
                        }
                        this.owner.getActionSender().sendInventory();
                        break;
                    }
                    case 11: 
                    case 97: 
                    case 119: 
                    case 274: 
                    case 435: 
                    case 491: {
                        if (item.getID() == 622) {
                            this.owner.setBusy(true);
                            showBubble();
                            this.owner.getActionSender().sendSound("cooking");
                            this.owner.getActionSender().sendMessage("@pnk@ You put the seaweed on the " + this.object.getGameObjectDef().getName() + ".");
                            world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                public void action() {
                                    if (this.owner.getInventory().remove(item) > -1) {
                                        this.owner.getActionSender().sendMessage("@pnk@ The seaweed burns to ashes");
                                        this.owner.getInventory().add(new InvItem(624, 1));
                                        this.owner.getActionSender().sendInventory();
                                    }
                                    this.owner.setBusy(false);
                                }
                            });
                            break;
                        }
                        /* Two of the seven cocktails are warmed over a range
                           part way through -- the drunk dragon and the choc
                           saturday. A half-made cocktail is a glass, not food,
                           so it has no cooking def and has to be taken before
                           the lookup below. */
                        if (GnomeCooking.heatCocktail(this.owner, item)) {
                            break;
                        }
                        final ItemCookingDef cookingDef = item.getCookingDef();
                        if (cookingDef == null) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        if (this.owner.getCurStat(7) < cookingDef.getReqLevel()) {
                            this.owner.getActionSender().sendMessage("@gry@ You need a cooking level of " + cookingDef.getReqLevel() + " to cook this.");
                            return;
                        }
                        this.owner.setBusy(true);
                        showBubble();
                        this.owner.getActionSender().sendSound("cooking");
                        this.owner.getActionSender().sendMessage("@pnk@ You cook the " + item.getDef().getName() + " on the " + this.object.getGameObjectDef().getName() + ".");
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                InvItem cookedFood = new InvItem(cookingDef.getCookedId());
                                /* Something whose burnt form is its cooked form
                                   cannot burn. Swamp paste is warmed over a fire
                                   rather than cooked and Jagex never let it
                                   spoil, so it is entered in the cooking table
                                   with both ids the same rather than given a
                                   branch of its own here. */
                                boolean fireproof = cookingDef.getCookedId() == cookingDef.getBurnedId();
                                if (this.owner.getInventory().remove(item) > -1) {
                                /* Gauntlets of cooking, the Family crest reward
                                   the chef gives. They "reduce the chance of
                                   burning food", which players measured as
                                   worth about nine cooking levels, so nine
                                   levels is what they are worth here rather
                                   than a second formula alongside the first. */
                                int cookingLevel = this.owner.getCurStat(7)
                                    + (this.owner.getInventory().wielding(700) ? 9 : 0);
                                    if (fireproof || !Formulae.burnFood(item.getID(), cookingLevel)) {
                                        this.owner.getInventory().add(cookedFood);
                                        this.owner.getActionSender().sendMessage("@pnk@ The " + item.getDef().getName() + " is now nicely cooked.");
                                        this.owner.incExp(7, cookingDef.getExp(), true);
                                        this.owner.getActionSender().sendStat(7);
                                        /* A gnome dough or base going into the
                                           oven is a step in a recipe as well
                                           as a cook, so the recipe has to be
                                           told. Everything else ignores it. */
                                        GnomeCooking.onBaked(this.owner, item.getID(), false);
                                    } else {
                                        this.owner.getInventory().add(new InvItem(cookingDef.getBurnedId()));
                                        this.owner.getActionSender().sendMessage("@pnk@ You accidently burn the " + item.getDef().getName() + ".");
                                        GnomeCooking.onBaked(this.owner, item.getID(), true);
                                    }
                                    this.owner.getActionSender().sendInventory();
                                }
                                this.owner.setBusy(false);
                            }
                        });
                        break;
                    }
                    case 118: 
                    case 813: {
                        /* 691 is the bar smelted from the perfect gold in the
                           pillars of Zanash. It is ordinary gold in every way
                           that matters here -- same moulds, same gems, same
                           levels -- so it goes through the same menu, and only
                           the two things Avan asked for come out different. */
                        if (item.getID() == 172 || item.getID() == 691) {
                            world.getDelayedEventHandler().add(new MiniEvent(this.owner){

                                public void action() {
                                    this.owner.getActionSender().sendMessage("@gry@ What would you like to make?");
                                    String[] options = new String[]{"Ring", "Necklace", "Amulet"};
                                    this.owner.setMenuHandler(new MenuHandler(options){

                                        public void handleReply(int option, String reply) {
                                            if (this.owner.isBusy() || option < 0 || option > 2) {
                                                return;
                                            }
                                            int[] moulds = new int[]{293, 295, 294};
                                            final int[] gems = new int[]{-1, 164, 163, 162, 161, 523};
                                            String[] options = new String[]{"Gold", "Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone"};
                                            final int craftType = option;
                                            if (this.owner.getInventory().countId(moulds[craftType]) < 1) {
                                                this.owner.getActionSender().sendMessage("@gry@ You need a " + EntityHandler.getItemDef(moulds[craftType]).getName() + " to make a " + reply);
                                                return;
                                            }
                                            this.owner.getActionSender().sendMessage("@gry@ What type of " + reply + " would you like to make?");
                                            this.owner.setMenuHandler(new MenuHandler(options){

                                                public void handleReply(int option, String reply) {
                                                    if (this.owner.isBusy() || option < 0 || option > 5) {
                                                        return;
                                                    }
                                                    if (option != 0 && this.owner.getInventory().countId(gems[option]) < 1) {
                                                        this.owner.getActionSender().sendMessage("@gry@ You don't have a " + reply + ".");
                                                        return;
                                                    }
                                                    ItemCraftingDef def = EntityHandler.getCraftingDef(option * 3 + craftType);
                                                    if (def == null) {
                                                        this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                                                        return;
                                                    }
                                                    if (this.owner.getCurStat(12) < def.getReqLevel()) {
                                                        this.owner.getActionSender().sendMessage("@gry@ You need a crafting level of " + def.getReqLevel() + " to make this");
                                                        return;
                                                    }
                                                    if (this.owner.getInventory().remove(item) > -1 && (option == 0 || this.owner.getInventory().remove(gems[option], 1) > -1)) {
                                                        showBubble();
                                                        /* The ruby ring and ruby
                                                           necklace Avan wanted are
                                                           their own items, made the
                                                           ordinary way out of the
                                                           perfect gold. Option 3 is
                                                           ruby; craftType 0 and 1
                                                           are ring and necklace.
                                                           Everything else the
                                                           perfect bar makes is just
                                                           what the bar next to it
                                                           makes. */
                                                        int made = def.getItemID();
                                                        if (item.getID() == 691 && option == 3 && craftType < 2) {
                                                            made = craftType == 0 ? 692 : 693;
                                                        }
                                                        InvItem result = new InvItem(made, 1);
                                                        this.owner.getActionSender().sendMessage("@pnk@ You make a " + result.getDef().getName());
                                                        this.owner.getInventory().add(result);
                                                        this.owner.incExp(12, def.getExp(), true);
                                                        this.owner.getActionSender().sendStat(12);
                                                        this.owner.getActionSender().sendInventory();
                                                    }
                                                }
                                            });
                                            this.owner.getActionSender().sendMenu(options);
                                        }
                                    });
                                    this.owner.getActionSender().sendMenu(options);
                                }
                            });
                            break;
                        }
                        if (item.getID() == 384) {
                            world.getDelayedEventHandler().add(new MiniEvent(this.owner){

                                public void action() {
                                    this.owner.getActionSender().sendMessage("@gry@ What would you like to make?");
                                    String[] options = new String[]{"Holy Symbol of Saradomin", "UnHoly Symbol of Zamorak"};
                                    this.owner.setMenuHandler(new MenuHandler(options){

                                        public void handleReply(int option, String reply) {
                                            if (this.owner.isBusy() || option < 0 || option > 1) {
                                                return;
                                            }
                                            int[] moulds = new int[]{386, 1026};
                                            int[] results = new int[]{44, 1027};
                                            if (this.owner.getInventory().countId(moulds[option]) < 1) {
                                                this.owner.getActionSender().sendMessage("@gry@ You need a " + EntityHandler.getItemDef(moulds[option]).getName() + " to make a " + reply);
                                                return;
                                            }
                                            if (this.owner.getCurStat(12) < 16) {
                                                this.owner.getActionSender().sendMessage("@gry@ You need a crafting level of 16 to make this");
                                                return;
                                            }
                                            if (this.owner.getInventory().remove(item) > -1) {
                                                showBubble();
                                                InvItem result = new InvItem(results[option]);
                                                this.owner.getActionSender().sendMessage("@pnk@ You make a " + result.getDef().getName());
                                                this.owner.getInventory().add(result);
                                                this.owner.incExp(12, 50, true);
                                                this.owner.getActionSender().sendStat(12);
                                                this.owner.getActionSender().sendInventory();
                                            }
                                        }
                                    });
                                    this.owner.getActionSender().sendMenu(options);
                                }
                            });
                            break;
                        }
                        if (item.getID() == 625) {
                            if (player.getInventory().countId(624) < 1) {
                                this.owner.getActionSender().sendMessage("@gry@ You need some soda ash to mix the sand with.");
                                return;
                            }
                            this.owner.setBusy(true);
                            showBubble();
                            this.owner.getActionSender().sendMessage("@pnk@ You put the seaweed and the soda ash in the furnace.");
                            world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                                public void action() {
                                    if (player.getInventory().remove(624, 1) > -1 && player.getInventory().remove(item) > -1) {
                                        this.owner.getActionSender().sendMessage("@pnk@ It mixes to make some molten glass");
                                        this.owner.getInventory().add(new InvItem(623, 1));
                                        this.owner.incExp(12, 20, true);
                                        this.owner.getActionSender().sendStat(12);
                                        this.owner.getActionSender().sendInventory();
                                    }
                                    this.owner.setBusy(false);
                                }
                            });
                            break;
                        }
                        /* Cannon ammo. A steel bar is a finished bar and has no
                           smelting def, so without this it falls through to
                           "Nothing interesting happens" -- which is exactly
                           what a player holding a mould got until now.

                           The manual says it plainly: "Firstly you must heat up
                           a steel bar in a furnace / Then pour the molten steel
                           into a cannon ammo mould". One bar, one ball, 30
                           Smithing, 25 experience, and the four lines are the
                           recorded ones. It takes twice as long as an ordinary
                           smelt, which is recorded too, so the delay is a
                           SingleEvent at 3000 rather than a ShortEvent.

                           Without the mould the bar still goes in the fire and
                           comes back out as itself: "(you heat the steel bar)",
                           and the wiki is explicit that quest progress does not
                           matter to that. */
                        if (item.getID() == 171 && this.owner.getInventory().countId(Multicannon.AMMO_MOULD) < 1) {
                            this.owner.getActionSender().sendMessage("@pnk@ you heat the steel bar");
                            return;
                        }
                        if (item.getID() == 171) {
                            if (this.owner.getCurStat(13) < 30) {
                                this.owner.getActionSender().sendMessage("@gry@ You need a smithing level of 30 to make cannon balls.");
                                return;
                            }
                            this.owner.setBusy(true);
                            showBubble();
                            this.owner.getActionSender().sendMessage("@pnk@ you heat the steel bar into a liquid state");
                            world.getDelayedEventHandler().add(new SingleEvent(this.owner, 3000){

                                public void action() {
                                    if (this.owner.getInventory().remove(item) > -1) {
                                        this.owner.getActionSender().sendMessage("@pnk@ and pour it into your cannon ball mould");
                                        this.owner.getActionSender().sendMessage("@pnk@ you then leave it to cool for a short while");
                                        this.owner.getInventory().add(new InvItem(Multicannon.CANNON_BALL, 1));
                                        this.owner.getActionSender().sendMessage("@pnk@ it's very heavy");
                                        this.owner.incExp(13, 25, true);
                                        this.owner.getActionSender().sendStat(13);
                                        this.owner.getActionSender().sendInventory();
                                    }
                                    this.owner.setBusy(false);
                                }
                            });
                            break;
                        }
                        /* Coal has no smelting def of its own, but using it on a
                           furnace is how a great many players smelt steel. It
                           used to get "Nothing interesting happens." */
                        final Smelting.Recipe recipe = Smelting.recipeFor(this.owner, item.getID(), true);
                        if (recipe == null) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        if (!Smelting.canSmelt(this.owner, recipe)) {
                            return;
                        }
                        this.owner.setBusy(true);
                        showBubble();
                        this.owner.getActionSender().sendMessage(Smelting.furnaceNarration(recipe));
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                if (!Smelting.consume(this.owner, recipe)) {
                                    this.owner.setBusy(false);
                                    return;
                                }
                                if (Smelting.canFailToRefine(recipe) && DataConversions.random(0, 1) == 1) {
                                    /* The ore is gone either way. */
                                    this.owner.getActionSender().sendMessage(Smelting.impure());
                                } else {
                                    this.owner.getInventory().add(new InvItem(recipe.getBarId()));
                                    this.owner.getActionSender().sendMessage(Smelting.retrieved(recipe));
                                    /* Gauntlets of goldsmithing, the Family
                                       crest reward Avan gives: half as much
                                       smithing experience again for a gold
                                       bar, and nothing for anything else.
                                       Both golds count -- the perfect ore
                                       from the pillars of Zanash is still
                                       gold. */
                                    int smeltExp = recipe.getExp();
                                    if ((recipe.getOreId() == 152 || recipe.getOreId() == 690)
                                            && this.owner.getInventory().wielding(699)) {
                                        smeltExp = smeltExp * 3 / 2;
                                    }
                                    this.owner.incExp(13, smeltExp, true);
                                    this.owner.getActionSender().sendStat(13);
                                }
                                this.owner.getActionSender().sendInventory();
                                this.owner.setBusy(false);
                            }
                        });
                        break;
                    }
                    case 50:
                    case 177: {
                        /*
                         * Object 177 is not just an anvil that happens to be
                         * Doric's -- Jagex named it "Doric's anvil" and gave
                         * it the description "Property of Doric the dwarf".
                         * His quest's whole lasting reward is permission to
                         * use it, and nothing anywhere enforced that: the
                         * quest published mayUseAnvils() and no one ever
                         * called it, so the reward was worth nothing.
                         *
                         * Doric objects in person if he is standing there,
                         * and the player thinks better of it if he is not.
                         * Both lines are Jagex's; the transcript even has a
                         * section heading for this exact refusal.
                         */
                        if (this.object.getID() == 177 && !this.owner.getQuestManager().completed(Quests.DORICS_QUEST)) {
                            Npc doric = world.getNpc(144, this.object.getX() - 6, this.object.getX() + 6, this.object.getY() - 6, this.object.getY() + 6);
                            if (doric != null) {
                                this.owner.informOfNpcMessage(new ChatMessage(doric, "Heh who said you could use that?", this.owner));
                            } else {
                                this.owner.informOfChatMessage(new ChatMessage(this.owner, "I'd better ask Doric if I can use this first", null));
                            }
                            return;
                        }
                        /*
                         * Dragon Square Shield exists only as an item
                         * definition and a rare-drop mention (see the
                         * NPCDef.xml.gz comment on that item) -- nothing
                         * anywhere combined the halves into it. Authentically
                         * the right half is bought from the Legends' Guild for
                         * 750,000gp and the left is the actual rare drop; a
                         * monster is never supposed to hand over the finished
                         * item directly, which is exactly the bug the drop-
                         * table audit found and fixed.
                         *
                         * Checked ahead of the bar-smithing switch below
                         * because a half-shield is not a bar id, and
                         * Formulae.minSmithingLevel(1276/1277) returning -1
                         * would otherwise just print "Nothing interesting
                         * happens" -- silently unimplemented rather than
                         * broken, but still not the real recipe.
                         */
                        if (item.getID() == 1276 || item.getID() == 1277) {
                            combineDragonSquareShield();
                            return;
                        }
                        /*
                         * Legends' Quest: the golden bowl. Gold is crafted at
                         * a furnace, never smithed, so a gold bar has no row
                         * in the recipe tables below -- but with Gujuo's
                         * sketch in hand, two bars hammered out on any anvil
                         * become the bowl. The shaping can fail and ruined
                         * gold is gone, which is why the walkthroughs say to
                         * bring spare bars.
                         */
                        if ((item.getID() == 172 || item.getID() == 691)
                                && this.owner.getInventory().countId(1246) > 0) {
                            makeGoldenBowl();
                            return;
                        }
                        int minSmithingLevel = Formulae.minSmithingLevel(item.getID());
                        if (minSmithingLevel < 0) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        if (this.owner.getInventory().countId(168) < 1) {
                            this.owner.getActionSender().sendMessage("@gry@ You need a hammer to work the metal with.");
                            return;
                        }
                        if (this.owner.getCurStat(13) < minSmithingLevel) {
                            this.owner.getActionSender().sendMessage("@gry@ You need a smithing level of " + minSmithingLevel + " to use this type of bar");
                            return;
                        }
                        String[] options = new String[]{"Make Weapon", "Make Armour", "Make Missile Heads", "Cancel"};
                        this.owner.setMenuHandler(new MenuHandler(options){

                            public void handleReply(int option, String reply) {
                                if (this.owner.isBusy()) {
                                    return;
                                }
                                switch (option) {
                                    case 0: {
                                        this.owner.getActionSender().sendMessage("@gry@ Choose a type of weapon to make");
                                        String[] options = new String[]{"Dagger", "Throwing Knife", "Sword", "Axe", "Mace"};
                                        this.owner.setMenuHandler(new MenuHandler(options){

                                            public void handleReply(int option, String reply) {
                                                if (this.owner.isBusy()) {
                                                    return;
                                                }
                                                switch (option) {
                                                    case 0: {
                                                        handleSmithing(item.getID(), 0);
                                                        break;
                                                    }
                                                    case 1: {
                                                        handleSmithing(item.getID(), 1);
                                                        break;
                                                    }
                                                    case 2: {
                                                        this.owner.getActionSender().sendMessage("@gry@ What sort of sword do you want to make?");
                                                        String[] options = new String[]{"Short Sword", "Long Sword (2 bars)", "Scimitar (2 bars)", "2-handed Sword (3 bars)"};
                                                        this.owner.setMenuHandler(new MenuHandler(options){

                                                            public void handleReply(int option, String reply) {
                                                                if (this.owner.isBusy()) {
                                                                    return;
                                                                }
                                                                switch (option) {
                                                                    case 0: {
                                                                        handleSmithing(item.getID(), 2);
                                                                        break;
                                                                    }
                                                                    case 1: {
                                                                        handleSmithing(item.getID(), 3);
                                                                        break;
                                                                    }
                                                                    case 2: {
                                                                        handleSmithing(item.getID(), 4);
                                                                        break;
                                                                    }
                                                                    case 3: {
                                                                        handleSmithing(item.getID(), 5);
                                                                        break;
                                                                    }
                                                                    default: {
                                                                        return;
                                                                    }
                                                                }
                                                            }
                                                        });
                                                        this.owner.getActionSender().sendMenu(options);
                                                        break;
                                                    }
                                                    case 3: {
                                                        this.owner.getActionSender().sendMessage("@gry@ What sort of axe do you want to make?");
                                                        String[] options = new String[]{"Hatchet", "Pickaxe", "Battle Axe (3 bars)"};
                                                        this.owner.setMenuHandler(new MenuHandler(options){

                                                            public void handleReply(int option, String reply) {
                                                                if (this.owner.isBusy()) {
                                                                    return;
                                                                }
                                                                switch (option) {
                                                                    case 0: {
                                                                        handleSmithing(item.getID(), 6);
                                                                        break;
                                                                    }
                                                                    case 1: {
                                                                        handleSmithing(item.getID(), 7);
                                                                        break;
                                                                    }
                                                                    case 2: {
                                                                        handleSmithing(item.getID(), 8);
                                                                        break;
                                                                    }
                                                                    default: {
                                                                        return;
                                                                    }
                                                                }
                                                            }
                                                        });
                                                        this.owner.getActionSender().sendMenu(options);
                                                        break;
                                                    }
                                                    case 4: {
                                                        handleSmithing(item.getID(), 9);
                                                        break;
                                                    }
                                                    default: {
                                                        return;
                                                    }
                                                }
                                            }
                                        });
                                        this.owner.getActionSender().sendMenu(options);
                                        break;
                                    }
                                    case 1: {
                                        this.owner.getActionSender().sendMessage("@gry@ Choose a type of armour to make");
                                        String[] options = new String[]{"Helmet", "Shield", "Armour"};
                                        this.owner.setMenuHandler(new MenuHandler(options){

                                            public void handleReply(int option, String reply) {
                                                if (this.owner.isBusy()) {
                                                    return;
                                                }
                                                switch (option) {
                                                    case 0: {
                                                        this.owner.getActionSender().sendMessage("@gry@ What sort of helmet do you want to make?");
                                                        this.options = new String[]{"Medium Helmet", "Large Helmet (2 bars)"};
                                                        this.owner.setMenuHandler(new MenuHandler(this.options){

                                                            public void handleReply(int option, String reply) {
                                                                if (this.owner.isBusy()) {
                                                                    return;
                                                                }
                                                                switch (option) {
                                                                    case 0: {
                                                                        handleSmithing(item.getID(), 10);
                                                                        break;
                                                                    }
                                                                    case 1: {
                                                                        handleSmithing(item.getID(), 11);
                                                                        break;
                                                                    }
                                                                    default: {
                                                                        return;
                                                                    }
                                                                }
                                                            }
                                                        });
                                                        this.owner.getActionSender().sendMenu(this.options);
                                                        break;
                                                    }
                                                    case 1: {
                                                        this.owner.getActionSender().sendMessage("@gry@ What sort of shield do you want to make?");
                                                        this.options = new String[]{"Square Shield (2 bars)", "Kite Shield (3 bars)"};
                                                        this.owner.setMenuHandler(new MenuHandler(this.options){

                                                            public void handleReply(int option, String reply) {
                                                                if (this.owner.isBusy()) {
                                                                    return;
                                                                }
                                                                switch (option) {
                                                                    case 0: {
                                                                        handleSmithing(item.getID(), 12);
                                                                        break;
                                                                    }
                                                                    case 1: {
                                                                        handleSmithing(item.getID(), 13);
                                                                        break;
                                                                    }
                                                                    default: {
                                                                        return;
                                                                    }
                                                                }
                                                            }
                                                        });
                                                        this.owner.getActionSender().sendMenu(this.options);
                                                        break;
                                                    }
                                                    case 2: {
                                                        this.owner.getActionSender().sendMessage("@gry@ What sort of armour do you want to make?");
                                                        this.options = new String[]{"Chain Mail Body (3 bars)", "Plate Mail Body (5 bars)", "Plate Mail Legs (3 bars)", "Plated Skirt (3 bars)"};
                                                        this.owner.setMenuHandler(new MenuHandler(this.options){

                                                            public void handleReply(int option, String reply) {
                                                                if (this.owner.isBusy()) {
                                                                    return;
                                                                }
                                                                switch (option) {
                                                                    case 0: {
                                                                        handleSmithing(item.getID(), 14);
                                                                        break;
                                                                    }
                                                                    case 1: {
                                                                        handleSmithing(item.getID(), 15);
                                                                        break;
                                                                    }
                                                                    case 2: {
                                                                        handleSmithing(item.getID(), 16);
                                                                        break;
                                                                    }
                                                                    case 3: {
                                                                        handleSmithing(item.getID(), 17);
                                                                        break;
                                                                    }
                                                                    default: {
                                                                        return;
                                                                    }
                                                                }
                                                            }
                                                        });
                                                        this.owner.getActionSender().sendMenu(this.options);
                                                        break;
                                                    }
                                                    default: {
                                                        return;
                                                    }
                                                }
                                            }
                                        });
                                        this.owner.getActionSender().sendMenu(options);
                                        break;
                                    }
                                    case 2: {
                                        String[] options = new String[]{"Make 10 Arrow Heads", "Make 50 Arrow Heads (5 bars)", "Forge Dart Tips", "Cancel"};
                                        this.owner.setMenuHandler(new MenuHandler(options){

                                            public void handleReply(int option, String reply) {
                                                if (this.owner.isBusy()) {
                                                    return;
                                                }
                                                switch (option) {
                                                    case 0: {
                                                        handleSmithing(item.getID(), 18);
                                                        break;
                                                    }
                                                    case 1: {
                                                        handleSmithing(item.getID(), 19);
                                                        break;
                                                    }
                                                    case 2: {
                                                        handleSmithing(item.getID(), 20);
                                                        break;
                                                    }
                                                    default: {
                                                        return;
                                                    }
                                                }
                                            }
                                        });
                                        this.owner.getActionSender().sendMenu(options);
                                        break;
                                    }
                                    default: {
                                        return;
                                    }
                                }
                            }
                        });
                        this.owner.getActionSender().sendMenu(options);
                        break;
                    }
                    case 121: {
                        switch (item.getID()) {
                            case 145: {
                                this.owner.getActionSender().sendMessage("@pnk@ You spin the sheeps wool into a nice ball of wool");
                                world.getDelayedEventHandler().add(new MiniEvent(this.owner){

                                    public void action() {
                                        if (this.owner.getInventory().remove(item) > -1) {
                                            this.owner.getInventory().add(new InvItem(207, 1));
                                            this.owner.incExp(12, 3, true);
                                            this.owner.getActionSender().sendStat(12);
                                            this.owner.getActionSender().sendInventory();
                                        }
                                        this.owner.setBusy(false);
                                    }
                                });
                                break;
                            }
                            case 675: {
                                if (this.owner.getCurStat(12) < 10) {
                                    this.owner.getActionSender().sendMessage("@gry@ You need a crafting level of 10 to spin flax");
                                    return;
                                }
                                this.owner.getActionSender().sendMessage("@pnk@ You make the flax into a bow string");
                                world.getDelayedEventHandler().add(new MiniEvent(this.owner){

                                    public void action() {
                                        if (this.owner.getInventory().remove(item) > -1) {
                                            this.owner.getInventory().add(new InvItem(676, 1));
                                            this.owner.incExp(12, 15, true);
                                            this.owner.getActionSender().sendStat(12);
                                            this.owner.getActionSender().sendInventory();
                                        }
                                        this.owner.setBusy(false);
                                    }
                                });
                                break;
                            }
                            default: {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                                return;
                            }
                        }
                        this.owner.setBusy(true);
                        showBubble();
                        this.owner.getActionSender().sendSound("mechanical");
                        break;
                    }
                    /**
                     * The crystal chest, in the house north of Jatix's herblaw
                     * shop in Taverley. The crystal key opens it, the key is
                     * gone afterwards, and an uncut dragonstone comes out every
                     * single time -- one, never two. What comes out with it is
                     * one of the ten possibilities in KeyChestLoot.xml.gz.
                     *
                     * The chest is not replaced with its open form (247) while
                     * this runs. Jagex's own did, and that is the whole of the
                     * glitch that took the chest off every live world for long
                     * stretches: the script paused between opening and closing,
                     * a player who logged out in that gap left the chest open,
                     * and the open chest has no "use key on" option, so nobody
                     * could ever open it again. It is the reason uncut
                     * dragonstone ended up effectively discontinued. Leaving
                     * the chest closed for the half-second it takes costs the
                     * player nothing and cannot strand it.
                     */
                    case 248: {
                        if (item.getID() != 525) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ You use the key to unlock the chest");
                        this.owner.setBusy(true);
                        showBubble();
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                if (this.owner.getInventory().remove(item) > -1) {
                                    this.owner.getInventory().add(new InvItem(542, 1));
                                    /* Already one InvItem per bag slot, and every
                                       one of them new: see Formulae. */
                                    for (InvItem i : Formulae.getKeyChestLoot()) {
                                        this.owner.getInventory().add(i);
                                    }
                                    this.owner.getActionSender().sendInventory();
                                }
                                this.owner.setBusy(false);
                            }
                        });
                        break;
                    }
                    /**
                     * The sinister chest, Yanille agility dungeon.
                     *
                     * Salarin the Twisted drops the key, the key opens this and
                     * nothing else, and the key is gone afterwards. Nine grimy
                     * herbs come out -- one torstol, one kwuarm, one avantoe,
                     * one irit, three ranarr and two harralander -- and this is
                     * the only source of torstol in the game.
                     *
                     * The three messages are Jagex's, from the transcript, and
                     * so is their order: the gas is between unlocking and
                     * finding, because the chest poisons whoever opens it. The
                     * wiki is explicit that it starts at 6, which is the
                     * strongest poison in Classic -- stronger than any monster.
                     */
                    case 645: {
                        if (item.getID() != 932) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        /* Nine herbs in, one key out, so eight slots have to be
                           free before it is worth starting. */
                        if (Inventory.MAX_SIZE - this.owner.getInventory().size() < 8) {
                            this.owner.getActionSender().sendMessage("@gry@ You don't have room for all the herbs");
                            return;
                        }
                        this.owner.setBusy(true);
                        showBubble();
                        this.owner.getActionSender().sendMessage("@pnk@ you unlock the chest with your key");
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                if (this.owner.getInventory().remove(item) > -1) {
                                    this.owner.getActionSender().sendMessage("@pnk@ A foul gas seeps from the chest");
                                    org.rscdaemon.server.model.Poison.infect(this.owner,
                                        org.rscdaemon.server.model.Poison.SINISTER_CHEST_STRENGTH);
                                    this.owner.getActionSender().sendMessage("@pnk@ You find a lot of herbs in the chest");
                                    for (int i = 0; i < SINISTER_HERBS.length; i++) {
                                        this.owner.getInventory().add(new InvItem(SINISTER_HERBS[i], 1));
                                    }
                                    this.owner.getActionSender().sendInventory();
                                }
                                this.owner.setBusy(false);
                            }
                        });
                        /* Open while the herbs are being taken, then shut again,
                           so the next player with a key finds a chest and not an
                           empty box. Jagex never said how long; a minute is this
                           server's figure. */
                        world.registerGameObject(new GameObject(this.object.getLocation(), 644,
                            this.object.getDirection(), this.object.getType()));
                        world.delayedSpawnObject(this.object.getLoc(), 60000);
                        break;
                    }
                    case 662: {
                        /* The tree off Grew's island, west of Yanille. Its model
                           is called tree_for_rope and its examine is "It has a
                           branch ideal for tying ropes to", which is the whole
                           instruction. Tie a rope to it and it becomes 663,
                           tree_with_rope, and 663 is the Agility shortcut --
                           level 30, 12.5 experience, no failure. The tree on the
                           island, 664, is roped already and always was, so the
                           way back is free and nobody can be stranded.

                           Here rather than in Watchtower because it is not the
                           quest's: anyone with a rope and the level can tie it,
                           the quest only happens to need the crossing. Claiming
                           662 for the quest would have taken the shortcut away
                           from Agility, which is the same mistake the rock cake
                           stall was rescued from. */
                        final GameObjectLoc tree = this.object.getLoc();
                        if (item.getID() != 237) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ You tie the rope to the tree");
                        this.owner.setBusy(true);
                        showBubble();
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                if (this.owner.getInventory().remove(item) > -1) {
                                    this.owner.getActionSender().sendInventory();
                                    InvUseOnObject.tieRope(tree);
                                }
                                this.owner.setBusy(false);
                            }
                        });
                        break;
                    }
                    case 302: {
                        if (item.getID() != 21) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ You fill the bucket with sand.");
                        this.owner.setBusy(true);
                        showBubble();
                        world.getDelayedEventHandler().add(new MiniEvent(this.owner){

                            public void action() {
                                if (this.owner.getInventory().remove(item) > -1) {
                                    this.owner.getInventory().add(new InvItem(625, 1));
                                    this.owner.getActionSender().sendInventory();
                                }
                                this.owner.setBusy(false);
                            }
                        });
                        break;
                    }
                    case 179: {
                        if (item.getID() != 243) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@gry@ What would you like to make?");
                        String[] options = new String[]{"Pot", "Pie Dish", "Bowl", "Cancel"};
                        this.owner.setMenuHandler(new MenuHandler(options){

                            public void handleReply(int option, String reply) {
                                int exp;
                                int reqLvl;
                                InvItem result;
                                if (this.owner.isBusy()) {
                                    return;
                                }
                                switch (option) {
                                    case 0: {
                                        result = new InvItem(279, 1);
                                        reqLvl = 1;
                                        exp = 6;
                                        break;
                                    }
                                    case 1: {
                                        result = new InvItem(278, 1);
                                        reqLvl = 4;
                                        exp = 10;
                                        break;
                                    }
                                    case 2: {
                                        result = new InvItem(340, 1);
                                        reqLvl = 7;
                                        exp = 10;
                                        break;
                                    }
                                    default: {
                                        this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                                        return;
                                    }
                                }
                                if (this.owner.getCurStat(12) < reqLvl) {
                                    this.owner.getActionSender().sendMessage("@gry@ You need a crafting level of " + reqLvl + " to make this");
                                    return;
                                }
                                if (this.owner.getInventory().remove(item) > -1) {
                                    showBubble();
                                    this.owner.getActionSender().sendMessage("@pnk@ You make a " + result.getDef().getName());
                                    this.owner.getInventory().add(result);
                                    this.owner.incExp(12, exp, true);
                                    this.owner.getActionSender().sendStat(12);
                                    this.owner.getActionSender().sendInventory();
                                }
                            }
                        });
                        this.owner.getActionSender().sendMenu(options);
                        break;
                    }
                    case 178: {
                        int xp;
                        int reqLvl;
                        int resultID;
                        switch (item.getID()) {
                            case 279: {
                                resultID = 135;
                                reqLvl = 1;
                                xp = 7;
                                break;
                            }
                            case 278: {
                                resultID = 251;
                                reqLvl = 4;
                                xp = 15;
                                break;
                            }
                            case 340: {
                                resultID = 341;
                                reqLvl = 7;
                                xp = 15;
                                break;
                            }
                            default: {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                                return;
                            }
                        }
                        if (this.owner.getCurStat(12) < reqLvl) {
                            this.owner.getActionSender().sendMessage("@gry@ You need a crafting level of " + reqLvl + " to make this");
                            return;
                        }
                        final InvItem result = new InvItem(resultID, 1);
                        final int exp = xp;
                        final boolean fail = Formulae.crackPot(reqLvl, this.owner.getCurStat(12));
                        showBubble();
                        this.owner.getActionSender().sendMessage("@pnk@ You place the " + item.getDef().getName() + " in the oven");
                        this.owner.setBusy(true);
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                if (this.owner.getInventory().remove(item) > -1) {
                                    if (fail) {
                                        this.owner.getActionSender().sendMessage("@pnk@ The " + result.getDef().getName() + " cracks in the oven, you throw it away.");
                                    } else {
                                        this.owner.getActionSender().sendMessage("@pnk@ You take out the " + result.getDef().getName());
                                        this.owner.getInventory().add(result);
                                        this.owner.incExp(12, exp, true);
                                        this.owner.getActionSender().sendStat(12);
                                    }
                                    this.owner.getActionSender().sendInventory();
                                }
                                this.owner.setBusy(false);
                            }
                        });
                        break;
                    }
                    default: {
                        this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                        return;
                    }
                }
            }

            /*
             * classic.runescape.wiki, 2026-08-02: level 60 Smithing, a
             * hammer, both halves, any anvil, 75 xp. The right half (1276) is
             * the one bought from the Legends' Guild for 750,000gp; the left
             * (1277) is the genuine rare drop. Neither is checked against how
             * the player obtained it -- that would need tracking provenance
             * this server doesn't keep -- so this only enforces having both
             * items, which is the same thing every other smithing recipe here
             * enforces.
             */
            private void makeGoldenBowl() {
                if (this.owner.getInventory().countId(168) < 1) {
                    this.owner.getActionSender().sendMessage("@gry@ You need a hammer to work the metal with.");
                    return;
                }
                /*
                 * A perfect gold bar (691, Family Crest) is still gold, the
                 * same stance the goldsmithing gauntlets take on its ore.
                 */
                int plain = this.owner.getInventory().countId(172);
                if (plain + this.owner.getInventory().countId(691) < 2) {
                    this.owner.getActionSender().sendMessage("@gry@ You need two bars of gold to make the bowl.");
                    return;
                }
                this.owner.getActionSender().sendSound("anvil");
                for (int i = 0; i < 2; ++i) {
                    this.owner.getInventory().remove(plain > i ? 172 : 691, 1);
                }
                if (DataConversions.random(0, 4) == 0) {
                    this.owner.getActionSender().sendMessage("@pnk@ You make a mistake and beat the gold too thin.");
                    this.owner.getActionSender().sendMessage("@pnk@ The ruined bars are useless.");
                } else {
                    this.owner.getActionSender().sendMessage("@pnk@ You hammer the gold into a wide shallow bowl,");
                    this.owner.getActionSender().sendMessage("@pnk@ the same shape as the one in Gujuo's drawing.");
                    this.owner.getInventory().add(new InvItem(1188, 1));
                }
                this.owner.getActionSender().sendInventory();
            }

            private void combineDragonSquareShield() {
                if (this.owner.getInventory().countId(1276) < 1 || this.owner.getInventory().countId(1277) < 1) {
                    this.owner.getActionSender().sendMessage("@gry@ You need both halves of the shield to do this.");
                    return;
                }
                if (this.owner.getInventory().countId(168) < 1) {
                    this.owner.getActionSender().sendMessage("@gry@ You need a hammer to work the metal with.");
                    return;
                }
                if (this.owner.getCurStat(13) < 60) {
                    this.owner.getActionSender().sendMessage("@gry@ You need a smithing level of 60 to combine the halves.");
                    return;
                }
                this.owner.getInventory().remove(1276, 1);
                this.owner.getInventory().remove(1277, 1);
                this.owner.getActionSender().sendSound("anvil");
                this.owner.getActionSender().sendMessage("@pnk@ You combine the two halves of the shield.");
                this.owner.getInventory().add(new InvItem(1278, 1));
                this.owner.incExp(13, 75, true);
                this.owner.getActionSender().sendStat(13);
                this.owner.getActionSender().sendInventory();
            }

            private void handleSmithing(int barID, int toMake) {
                ItemSmithingDef def = EntityHandler.getSmithingDef(Formulae.getBarType(barID) * 21 + toMake);
                if (def == null) {
                    this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                    return;
                }
                if (this.owner.getCurStat(13) < def.getRequiredLevel()) {
                    this.owner.getActionSender().sendMessage("@gry@ You need a smithing level of " + def.getRequiredLevel() + " to make this");
                    return;
                }
                if (this.owner.getInventory().countId(barID) < def.getRequiredBars()) {
                    this.owner.getActionSender().sendMessage("@gry@ You don't have enough bars to make this.");
                    return;
                }
                this.owner.getActionSender().sendSound("anvil");
                for (int x = 0; x < def.getRequiredBars(); ++x) {
                    this.owner.getInventory().remove(new InvItem(barID, 1));
                }
                Bubble bubble = new Bubble(this.owner, item.getID());
                for (Player p : this.owner.getViewArea().getPlayersInView()) {
                    p.informOfBubble(bubble);
                }
                if (EntityHandler.getItemDef(def.getItemID()).isStackable()) {
                    this.owner.getActionSender().sendMessage("@gry@ You hammer the metal into some " + EntityHandler.getItemDef(def.getItemID()).getName());
                    this.owner.getInventory().add(new InvItem(def.getItemID(), def.getAmount()));
                } else {
                    this.owner.getActionSender().sendMessage("@pnk@ You hammer the metal into a " + EntityHandler.getItemDef(def.getItemID()).getName());
                    for (int x = 0; x < def.getAmount(); ++x) {
                        this.owner.getInventory().add(new InvItem(def.getItemID(), 1));
                    }
                }
                this.owner.incExp(13, Formulae.getSmithingExp(barID, def.getRequiredBars()), true);
                this.owner.getActionSender().sendStat(13);
                this.owner.getActionSender().sendInventory();
            }

            private boolean itemId(int[] ids) {
                return DataConversions.inArray(ids, item.getID());
            }

            private boolean handleAgilityItem() {
                final ObjectAgilityDef def = EntityHandler.getObjectAgilityDef(
                    this.object.getLocation(), this.object.getID());
                if (def == null || def.getRequiredItem() <= 0
                        || item.getID() != def.getRequiredItem()) {
                    return false;
                }
                if (this.owner.getCurStat(17) < def.getReqLevel()) {
                    this.owner.getActionSender().sendMessage(
                        "@gry@ You need an agility level of " + def.getReqLevel() + " to do that");
                    return true;
                }
                if (!def.ignoresFatigue() && this.owner.getFatigue() >= 100) {
                    this.owner.getActionSender().sendMessage(
                        "@gry@ You are too tired to do that, get some rest!");
                    return true;
                }
                int d1 = Math.abs(this.owner.getX() - def.getX1()) + Math.abs(this.owner.getY() - def.getY1());
                int d2 = Math.abs(this.owner.getX() - def.getX2()) + Math.abs(this.owner.getY() - def.getY2());
                if (def.isOneWay() && d1 > d2) {
                    this.owner.getActionSender().sendMessage("@gry@ You cannot get back this way");
                    return true;
                }
                final int destX = d1 <= d2 ? def.getX2() : def.getX1();
                final int destY = d1 <= d2 ? def.getY2() : def.getY1();
                this.owner.setBusy(true);
                this.owner.getActionSender().sendMessage("@pnk@ " + def.getMessage());
                world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                    public void action() {
                        this.owner.setBusy(false);
                        /* The swamps (the entries with a fall point) use the
                           rocks up laying the path; a rope tied for a swing
                           comes back with you. */
                        if (def.hasFallPoint()
                                && this.owner.getInventory().remove(item) > -1) {
                            this.owner.getActionSender().sendInventory();
                        }
                        this.owner.teleport(destX, destY, false);
                    }
                });
                return true;
            }

            private void showBubble() {
                Bubble bubble = new Bubble(this.owner, item.getID());
                for (Player p : this.owner.getViewArea().getPlayersInView()) {
                    p.informOfBubble(bubble);
                }
            }
        });
    }

    private void handleDoor(Player player, final ActiveTile tile, GameObject object, int dir, final InvItem item) {
        player.setStatus(Action.USING_INVITEM_ON_DOOR);
        world.getDelayedEventHandler().add(new WalkToObjectEvent(player, object, false){

            private void doDoor() {
                this.owner.getActionSender().sendSound("opendoor");
                world.registerGameObject(new GameObject(this.object.getLocation(), 11, this.object.getDirection(), this.object.getType()));
                world.delayedSpawnObject(this.object.getLoc(), 1000);
            }

            private void showBubble() {
                Bubble bubble = new Bubble(this.owner, item.getID());
                for (Player p : this.owner.getViewArea().getPlayersInView()) {
                    p.informOfBubble(bubble);
                }
            }

            public void arrived() {
                this.owner.resetPath();
                if (this.owner.isBusy() || this.owner.isRanging() || !this.owner.getInventory().contains(item) || !tile.hasDoor() || !tile.getDoor().equals(this.object) || this.owner.getStatus() != Action.USING_INVITEM_ON_DOOR) {
                    return;
                }
                this.owner.resetAll();
                // Doors are matched against the quest's door list, not its
                // object list -- see QuestTrigger.DOOR_ACT1. A quest that has
                // claimed this door owns what is used on it.
                if (this.owner.getQuestManager().triggerDoor(QuestTrigger.ITEM_ON_DOOR, this.object, item)) {
                    return;
                }
                switch (this.object.getID()) {
                    case 218: {
                        if (!InvUseOnObject.cutsWebs(item)) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        if (this.owner.getX() == 220) {
                            this.owner.getActionSender().sendMessage("@pnk@ You cut your way through the web");
                            this.owner.teleport(219, 130, false);
                            break;
                        }
                        if (this.owner.getX() != 219) break;
                        this.owner.getActionSender().sendMessage("@pnk@ You cut your way through the web");
                        this.owner.teleport(220, 130, false);
                        break;
                    }
                    case 219: {
                        if (!InvUseOnObject.cutsWebs(item)) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        if (this.owner.getX() == 236) {
                            this.owner.getActionSender().sendMessage("@pnk@ You cut your way through the web");
                            this.owner.teleport(237, 130, false);
                            break;
                        }
                        if (this.owner.getX() != 237) break;
                        this.owner.getActionSender().sendMessage("@pnk@ You cut your way through the web");
                        this.owner.teleport(236, 130, false);
                        break;
                    }
                    case 24: {
                        if (!InvUseOnObject.cutsWebs(item)) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        /* Three lines, and the punctuation is the transcript's:
                           the first trails off with two dots and the other two
                           end with none. Cutting a web costs no fatigue and can
                           be attempted at any fatigue, which is why nothing here
                           touches it. */
                        this.owner.getActionSender().sendMessage("@pnk@ You try to destroy the web..");
                        this.owner.setBusy(true);
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                if (Formulae.cutWeb()) {
                                    this.owner.getActionSender().sendMessage("@pnk@ You slice through the web");
                                    world.unregisterGameObject(object);
                                    world.delayedSpawnObject(object.getLoc(), 31000);
                                } else {
                                    this.owner.getActionSender().sendMessage("@pnk@ You fail to cut through it");
                                }
                                this.owner.setBusy(false);
                            }
                        });
                        break;
                    }
                    case 23: {
                        if (!this.itemId(new int[]{99})) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ You unlock the door and go through it");
                        this.doDoor();
                        if (this.owner.getY() <= 484) {
                            this.owner.teleport(this.owner.getX(), 485, false);
                            break;
                        }
                        this.owner.teleport(this.owner.getX(), 484, false);
                        break;
                    }
                    case 60: {
                        if (!this.itemId(new int[]{421})) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ You unlock the door and go through it");
                        this.doDoor();
                        if (this.owner.getX() > 337) break;
                        this.owner.teleport(338, this.owner.getY(), false);
                        break;
                    }
                    case 109: {
                        if (!this.itemId(new int[]{932})) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        if (this.owner.getY() >= 437) {
                            showBubble();
                            this.owner.getActionSender().sendMessage("@pnk@ You unlock the sinister door and go through it");
                            this.doDoor();
                            this.owner.teleport(219, 436, false);
                            break;
                        }
                        if (this.owner.getY() > 436) break;
                        showBubble();
                        this.owner.getActionSender().sendMessage("@pnk@ You unlock the sinister door and go through it");
                        this.doDoor();
                        this.owner.teleport(219, 437, false);
                        break;
                    }
                    case 214: {
                        if (this.owner.isTeam2()) {
                            if (!this.itemId(new int[]{382})) {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens");
                                return;
                            }
                            showBubble();
                            this.owner.getActionSender().sendMessage("@pnk@ You unlock the door");
                            GameObject door1 = new GameObject(Point.location(this.owner.getX(), this.owner.getY()), 2, 0, 1);
                            world.registerDoor(door1);
                            break;
                        }
                        if (!this.owner.isTeam1()) break;
                        if (!this.itemId(new int[]{391})) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@gry@ Wrong door! You have to go around.");
                        break;
                    }
                    case 215: {
                        if (this.owner.isTeam1()) {
                            if (!this.itemId(new int[]{391})) {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                                return;
                            }
                            showBubble();
                            this.owner.getActionSender().sendMessage("@pnk@ You unlock the door");
                            GameObject door1 = new GameObject(Point.location(this.owner.getX(), this.owner.getY()), 2, 0, 1);
                            world.registerDoor(door1);
                            break;
                        }
                        if (!this.owner.isTeam2()) break;
                        if (!this.itemId(new int[]{382})) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@gry@ Wrong door! You have to go around.");
                        break;
                    }
                    case 216: {
                        if (this.owner.isTeam2()) {
                            if (!this.itemId(new int[]{382})) {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens");
                                return;
                            }
                            showBubble();
                            this.owner.getActionSender().sendMessage("@pnk@ You unlock the door");
                            GameObject door1 = new GameObject(Point.location(234, 130), 2, 1, 1);
                            world.registerDoor(door1);
                            break;
                        }
                        if (!this.owner.isTeam1()) break;
                        if (!this.itemId(new int[]{391})) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@gry@ Wrong door!");
                        break;
                    }
                    case 217: {
                        if (this.owner.isTeam1()) {
                            if (!this.itemId(new int[]{391})) {
                                this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                                return;
                            }
                            showBubble();
                            this.owner.getActionSender().sendMessage("@pnk@ You unlock the door");
                            GameObject door1 = new GameObject(Point.location(223, 130), 2, 1, 1);
                            world.registerDoor(door1);
                            break;
                        }
                        if (!this.owner.isTeam2()) break;
                        if (!this.itemId(new int[]{382})) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        this.owner.getActionSender().sendMessage("@gry@ Wrong door!");
                        break;
                    }
                    default: {
                        this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                        return;
                    }
                }
                this.owner.getActionSender().sendInventory();
            }

            private boolean itemId(int[] ids) {
                return DataConversions.inArray(ids, item.getID());
            }
        });
    }
}

