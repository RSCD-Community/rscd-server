/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.model.Bank;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Inventory;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;

public class BankHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (player.isBusy() || player.isRanging() || player.isTrading() || player.isDueling()) {
            player.resetBank();
            return;
        }
        if (!player.accessingBank()) {
            player.setSuspiciousPlayer(true);
            player.resetBank();
            return;
        }
        Bank bank = player.getBank();
        Inventory inventory = player.getInventory();
        switch (pID) {
            case 48: {
                player.resetBank();
                break;
            }
            case 198: {
                short itemID = p.readShort();
                int amount = p.readInt();
                if (amount < 1 || inventory.countId(itemID) < amount) {
                    player.setSuspiciousPlayer(true);
                    return;
                }
                if (EntityHandler.getItemDef(itemID).isStackable()) {
                    InvItem item = new InvItem(itemID, amount);
                    if (bank.canHold(item) && inventory.remove(item) > -1) {
                        bank.add(item);
                    } else {
                        player.getActionSender().sendMessage("@gry@ You don't have room for that in your bank");
                    }
                } else {
                    int idx;
                    InvItem item;
                    for (int i = 0; i < amount && (item = inventory.get(idx = inventory.getLastIndexById(itemID))) != null; ++i) {
                        if (!bank.canHold(item) || inventory.remove(item) <= -1) {
                            player.getActionSender().sendMessage("@gry@ You don't have room for that in your bank");
                            break;
                        }
                        bank.add(item);
                    }
                }
                int slot = bank.getFirstIndexById(itemID);
                if (slot <= -1) break;
                player.getActionSender().sendInventory();
                player.getActionSender().updateBankItem(slot, itemID, bank.countId(itemID));
                break;
            }
            case 183: {
                short itemID = p.readShort();
                int amount = p.readInt();
                if (amount < 1 || bank.countId(itemID) < amount) {
                    player.setSuspiciousPlayer(true);
                    return;
                }
                int slot = bank.getFirstIndexById(itemID);
                if (EntityHandler.getItemDef(itemID).isStackable()) {
                    InvItem item = new InvItem(itemID, amount);
                    if (inventory.canHold(item) && bank.remove(item) > -1) {
                        inventory.add(item);
                    } else {
                        player.getActionSender().sendMessage("@gry@ You don't have room for that in your inventory");
                    }
                } else {
                    for (int i = 0; i < amount && bank.getFirstIndexById(itemID) >= 0; ++i) {
                        InvItem item = new InvItem(itemID, 1);
                        if (!inventory.canHold(item) || bank.remove(item) <= -1) {
                            player.getActionSender().sendMessage("@gry@ You don't have room for that in your inventory");
                            break;
                        }
                        inventory.add(item);
                    }
                }
                if (slot <= -1) break;
                player.getActionSender().sendInventory();
                player.getActionSender().updateBankItem(slot, itemID, bank.countId(itemID));
            }
        }
    }
}

