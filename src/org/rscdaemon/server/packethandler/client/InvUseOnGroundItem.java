/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.extras.FiremakingDef;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.event.ShortEvent;
import org.rscdaemon.server.event.WalkToPointEvent;
import org.rscdaemon.server.model.ActiveTile;
import org.rscdaemon.server.model.Bubble;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class InvUseOnGroundItem
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        if (player.isBusy()) {
            player.resetPath();
            return;
        }
        player.resetAll();
        Point location = Point.location(p.readShort(), p.readShort());
        short id = p.readShort();
        final ActiveTile tile = world.getTile(location);
        if (tile.hasGameObject()) {
            player.getActionSender().sendMessage("@gry@ You cannot do that here, please move to a new area.");
            return;
        }
        final Item item = this.getItem(id, tile, player);
        final InvItem myItem = player.getInventory().get(p.readShort());
        if (item == null || myItem == null) {
            player.setSuspiciousPlayer(true);
            player.resetPath();
            return;
        }
        player.setStatus(Action.USING_INVITEM_ON_GITEM);
        world.getDelayedEventHandler().add(new WalkToPointEvent(player, location, 1, false){

            public void arrived() {
                if (this.owner.isBusy() || this.owner.isRanging() || !tile.hasItem(item) || !this.owner.nextTo(item) || this.owner.getStatus() != Action.USING_INVITEM_ON_GITEM) {
                    return;
                }
                // A quest that claimed the item on the ground owns what is done
                // to it, the same contract scenery gets in ObjectAction. Before
                // this the switch below was the whole handler and it knew about
                // logs and a tinderbox, so everything else -- pouring milk over
                // a cat, for one -- answered "Nothing interesting happens".
                if (this.owner.getQuestManager().triggerGroundItem(item, myItem)) {
                    return;
                }
                switch (item.getID()) {
                    case 14: 
                    case 632: 
                    case 633: 
                    case 634: 
                    case 635: 
                    case 636: {
                        final FiremakingDef def = EntityHandler.getFiremakingDef(item.getID());
                        if (!this.itemId(new int[]{166}) || def == null) {
                            this.owner.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
                            return;
                        }
                        if (this.owner.getCurStat(11) < def.getRequiredLevel()) {
                            this.owner.getActionSender().sendMessage("@gry@ You need at least " + def.getRequiredLevel() + " firemaking to light these logs.");
                            return;
                        }
                        this.owner.setBusy(true);
                        Bubble bubble = new Bubble(this.owner, 166);
                        for (Player p : this.owner.getViewArea().getPlayersInView()) {
                            p.informOfBubble(bubble);
                        }
                        this.owner.getActionSender().sendMessage("@pnk@ You attempt to light the logs...");
                        world.getDelayedEventHandler().add(new ShortEvent(this.owner){

                            public void action() {
                                if (Formulae.lightLogs(def, this.owner.getCurStat(11))) {
                                    this.owner.getActionSender().sendMessage("@pnk@ They catch fire and start to burn.");
                                    world.unregisterItem(item);
                                    final GameObject fire = new GameObject(item.getLocation(), 97, 0, 0);
                                    world.registerGameObject(fire);
                                    world.getDelayedEventHandler().add(new DelayedEvent(null, def.getLength()){

                                        public void run() {
                                            if (tile.hasGameObject() && tile.getGameObject().equals(fire)) {
                                                world.unregisterGameObject(fire);
                                                world.registerItem(new Item(181, tile.getX(), tile.getY(), 1, null));
                                            }
                                            this.running = false;
                                        }
                                    });
                                    this.owner.incExp(11, Formulae.firemakingExp(this.owner.getMaxStat(11), def.getExp()), true);
                                    this.owner.getActionSender().sendStat(11);
                                } else {
                                    this.owner.getActionSender().sendMessage("@pnk@ You fail to light them.");
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

            private boolean itemId(int[] ids) {
                return DataConversions.inArray(ids, myItem.getID());
            }
        });
    }

    private Item getItem(int id, ActiveTile tile, Player player) {
        for (Item i : tile.getItems()) {
            if (i.getID() != id || !i.visibleTo(player)) continue;
            return i;
        }
        return null;
    }
}

