/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.event.WalkToPointEvent;
import org.rscdaemon.server.model.ActiveTile;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.states.Action;

public class PickupItem
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
        final Item item = this.getItem(id, tile, player);
        if (item == null) {
            player.setSuspiciousPlayer(true);
            player.resetPath();
            return;
        }
        player.setStatus(Action.TAKING_GITEM);
        world.getDelayedEventHandler().add(new WalkToPointEvent(player, location, 1, false){

            public void arrived() {
                if (this.owner.isBusy() || this.owner.isRanging() || !tile.hasItem(item) || !this.owner.nextTo(item) || this.owner.getStatus() != Action.TAKING_GITEM) {
                    return;
                }
                if (item.getID() == 501 && item.getX() == 333 && item.getY() == 434) {
                    this.owner.getActionSender().sendMessage("@pnk@ A force stops you from taking the holy wine.");
                    return;
                }
                this.owner.resetAll();
                InvItem invItem = new InvItem(item.getID(), item.getAmount());
                // Asked before the item moves: a quest may be holding it down.
                if (this.owner.getQuestManager().refusesPickup(invItem)) {
                    return;
                }
                if (!this.owner.getInventory().canHold(invItem)) {
                    this.owner.getActionSender().sendMessage("@gry@ You cannot pickup this item, your inventory is full!");
                    return;
                }
                world.unregisterItem(item);
                this.owner.getActionSender().sendSound("takeobject");
                this.owner.getInventory().add(invItem);
                this.owner.getActionSender().sendInventory();
                // After the item is actually in hand: a quest reacting to the
                // pickup will usually want to inspect the inventory.
                this.owner.getQuestManager().triggerEntity(QuestTrigger.ITEM_PICKUP, invItem);
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

