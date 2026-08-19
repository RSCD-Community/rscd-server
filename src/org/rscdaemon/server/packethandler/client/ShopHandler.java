/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;

/**
 * The three shop packets: 253 closes the window, 128 buys, 255 sells.
 *
 * The anti-cheat shape is the same in both trades: the client sends the
 * price it believes it is paying, and the server recomputes
 * modifier * basePrice / 100 and silently drops the request on any
 * mismatch. That check is also why shop prices cannot be made dynamic
 * without a protocol change -- both ends derive the price independently,
 * so they can only ever agree on a formula both already know.
 *
 * Item id 10 is coins. Non-stackables trade at most five per request
 * (the original's cap), one at a time so a mid-loop full inventory or
 * emptied shop stops cleanly. A shop packet from a player with no shop
 * open cannot happen through the real client, so it flags the sender
 * as suspicious.
 */
public class ShopHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (player.isBusy()) {
            player.resetShop();
            return;
        }
        Shop shop = player.getShop();
        if (shop == null) {
            player.setSuspiciousPlayer(true);
            player.resetShop();
            return;
        }
        switch (pID) {
            case 253: {
                player.resetShop();
                break;
            }
            case 128: {
                short x = p.readShort();
                int value = p.readInt();
                int quantity = Integer.parseInt(p.readString());
                InvItem item = new InvItem(x, 1);
                if (item.getDef().isStackable()) {
                    if (value != shop.getBuyModifier() * item.getDef().getBasePrice() / 100 || shop.countId(item.getID()) < 1) {
                        return;
                    }
                    if (player.getInventory().countId(10) < value * quantity) {
                        player.getActionSender().sendMessage("@gry@ You don't have enough money to buy that!");
                        return;
                    }
                    if (30 - player.getInventory().size() + player.getInventory().getFreedSlots(new InvItem(10, value)) < player.getInventory().getRequiredSlots(item)) {
                        player.getActionSender().sendMessage("@gry@ You don't have room for that in your inventory");
                        return;
                    }
                    if (player.getInventory().remove(10, value * quantity) <= -1) break;
                    item = new InvItem(x, quantity);
                    shop.remove(item);
                    player.getInventory().add(item);
                    player.getActionSender().sendInventory();
                    shop.updatePlayers();
                    break;
                }
                if (quantity > 5) {
                    quantity = 5;
                    player.getActionSender().sendMessage("@gry@ You can only buy 5 nonstackables at a time!");
                }
                if (value != shop.getBuyModifier() * item.getDef().getBasePrice() / 100 || shop.countId(item.getID()) < 1) {
                    return;
                }
                if (player.getInventory().countId(10) < value * quantity) {
                    player.getActionSender().sendMessage("@gry@ You don't have enough money to buy that!");
                    return;
                }
                if (30 - player.getInventory().size() + player.getInventory().getFreedSlots(new InvItem(10, value)) < player.getInventory().getRequiredSlots(item) * quantity) {
                    player.getActionSender().sendMessage("@gry@ You don't have room for that in your inventory");
                    return;
                }
                while (quantity > 0) {
                    item = new InvItem(x, 1);
                    if (player.getInventory().remove(10, value) > -1) {
                        shop.remove(item);
                        player.getInventory().add(item);
                        player.getActionSender().sendInventory();
                        shop.updatePlayers();
                    }
                    --quantity;
                }
                break;
            }
            case 255: {
                short x = p.readShort();
                int value = p.readInt();
                int quantity = Integer.parseInt(p.readString());
                InvItem item = new InvItem(x, 1);
                if (item.getDef().isStackable()) {
                    if (value != shop.getSellModifier() * item.getDef().getBasePrice() / 100 || player.getInventory().countId(item.getID()) < 1) {
                        return;
                    }
                    if (!shop.shouldStock(item.getID())) {
                        return;
                    }
                    if (!shop.canHold(item)) {
                        player.getActionSender().sendMessage("@gry@ The shop is currently full!");
                        return;
                    }
                    item = new InvItem(x, quantity);
                    if (player.getInventory().remove(item) <= -1) break;
                    player.getInventory().add(new InvItem(10, value * quantity));
                    shop.add(item);
                    player.getActionSender().sendInventory();
                    shop.updatePlayers();
                    break;
                }
                if (quantity > 5) {
                    quantity = 5;
                    player.getActionSender().sendMessage("@gry@ You can only sell 5 nonstackables at a time!");
                }
                if (value != shop.getSellModifier() * item.getDef().getBasePrice() / 100 || player.getInventory().countId(item.getID()) < 1) {
                    return;
                }
                if (!shop.shouldStock(item.getID())) {
                    return;
                }
                if (!shop.canHold(item)) {
                    player.getActionSender().sendMessage("@gry@ The shop is currently full!");
                    return;
                }
                while (quantity > 0) {
                    item = new InvItem(x, 1);
                    if (player.getInventory().remove(item) > -1) {
                        player.getInventory().add(new InvItem(10, value));
                        shop.add(item);
                        player.getActionSender().sendInventory();
                        shop.updatePlayers();
                    }
                    --quantity;
                }
                break;
            }
        }
    }
}

