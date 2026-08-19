/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import java.util.ArrayList;
import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Inventory;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.util.Formulae;

public class TradeHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    private boolean busy(Player player) {
        return player.isBusy() || player.isRanging() || player.accessingBank() || player.isDueling();
    }

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        if (this.busy(player)) {
            Player affectedPlayer = player.getWishToTrade();
            this.unsetOptions(player);
            this.unsetOptions(affectedPlayer);
            return;
        }
        if (player.getLocation().inEden() || player.getLocation().inBlackHole()) {
            /*
             * Fixed, known coordinates inside the Black Hole mean more than
             * one banned/visiting player can land on the exact same tile at
             * different times -- trading between them would defeat the
             * whole point of keeping banned players isolated. Blocked
             * outright rather than trying to keep every landing spot
             * unique.
             */
            player.getActionSender().sendMessage(player.getLocation().inBlackHole()
                    ? "@gry@ The black hole seems to be preventing you from trading."
                    : "@gry@ Trading disabled.");
            this.unsetOptions(player);
            Player affectedPlayer = player.getWishToTrade();
            this.unsetOptions(affectedPlayer);
            return;
        }
        switch (pID) {
            case 166: {
                Player affectedPlayer = world.getPlayer(p.readShort());
                if (affectedPlayer == null || !player.withinRange(affectedPlayer, 8) || player.isTrading() || player.tradeDuelThrottling()) {
                    this.unsetOptions(player);
                    return;
                }
                if (affectedPlayer.getX() < 86 && affectedPlayer.getX() > 61 && affectedPlayer.getY() == 129) {
                    this.unsetOptions(player);
                    player.getActionSender().sendMessage("@gry@ Trading disabled.");
                    return;
                }
                if (affectedPlayer == player) {
                    player.setSuspiciousPlayer(true);
                    System.out.println("Warning: " + player.getUsername() + " tried to trade to himself.");
                    this.unsetOptions(player);
                    return;
                }
                player.setWishToTrade(affectedPlayer);
                player.getActionSender().sendMessage("@gry@ " + (affectedPlayer.isTrading() ? affectedPlayer.getUsername() + " is already in a trade" : "Sending trade request"));
                affectedPlayer.getActionSender().sendMessage("@gry@ " + player.getUsername() + " wishes to trade with you");
                if (player.isTrading() || affectedPlayer.getWishToTrade() == null || !affectedPlayer.getWishToTrade().equals(player) || affectedPlayer.isTrading()) break;
                player.setTrading(true);
                player.resetPath();
                player.resetAllExceptTrading();
                affectedPlayer.setTrading(true);
                affectedPlayer.resetPath();
                affectedPlayer.resetAllExceptTrading();
                player.getActionSender().sendTradeWindowOpen();
                affectedPlayer.getActionSender().sendTradeWindowOpen();
                break;
            }
            case 211: {
                Player affectedPlayer = player.getWishToTrade();
                if (affectedPlayer == null || this.busy(affectedPlayer) || !player.isTrading() || !affectedPlayer.isTrading()) {
                    player.setSuspiciousPlayer(true);
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                player.setTradeOfferAccepted(true);
                player.getActionSender().sendTradeAcceptUpdate();
                affectedPlayer.getActionSender().sendTradeAcceptUpdate();
                if (!affectedPlayer.isTradeOfferAccepted()) break;
                player.getActionSender().sendTradeAccept();
                affectedPlayer.getActionSender().sendTradeAccept();
                break;
            }
            case 53: {
                InvItem affectedItem;
                int theirAvailableSlots;
                Player affectedPlayer = player.getWishToTrade();
                if (!(affectedPlayer != null && !this.busy(affectedPlayer) && player.isTrading() && affectedPlayer.isTrading() && player.isTradeOfferAccepted() && affectedPlayer.isTradeOfferAccepted())) {
                    player.setSuspiciousPlayer(true);
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                player.setTradeConfirmAccepted(true);
                if (!affectedPlayer.isTradeConfirmAccepted()) break;
                ArrayList<InvItem> myOffer = player.getTradeOffer();
                ArrayList<InvItem> theirOffer = affectedPlayer.getTradeOffer();
                int myRequiredSlots = player.getInventory().getRequiredSlots(theirOffer);
                int myAvailableSlots = 30 - player.getInventory().size() + player.getInventory().getFreedSlots(myOffer);
                int theirRequiredSlots = affectedPlayer.getInventory().getRequiredSlots(myOffer);
                if (theirRequiredSlots > (theirAvailableSlots = 30 - affectedPlayer.getInventory().size() + affectedPlayer.getInventory().getFreedSlots(theirOffer))) {
                    player.getActionSender().sendMessage("@gry@ The other player does not have room to accept your items.");
                    affectedPlayer.getActionSender().sendMessage("@gry@ You do not have room in your inventory to hold those items.");
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                if (myRequiredSlots > myAvailableSlots) {
                    player.getActionSender().sendMessage("@gry@ You do not have room in your inventory to hold those items.");
                    affectedPlayer.getActionSender().sendMessage("@gry@ The other player does not have room to accept your items.");
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                for (InvItem item : myOffer) {
                    affectedItem = player.getInventory().get(item);
                    if (affectedItem == null) {
                        player.setSuspiciousPlayer(true);
                        this.unsetOptions(player);
                        this.unsetOptions(affectedPlayer);
                        return;
                    }
                    if (affectedItem.isWielded()) {
                        affectedItem.setWield(false);
                        player.updateWornItems(affectedItem.getWieldableDef().getWieldPos(), player.getPlayerAppearance().getSprite(affectedItem.getWieldableDef().getWieldPos()));
                    }
                    player.getInventory().remove(item);
                }
                for (InvItem item : theirOffer) {
                    affectedItem = affectedPlayer.getInventory().get(item);
                    if (affectedItem == null) {
                        affectedPlayer.setSuspiciousPlayer(true);
                        this.unsetOptions(player);
                        this.unsetOptions(affectedPlayer);
                        return;
                    }
                    if (affectedItem.isWielded()) {
                        affectedItem.setWield(false);
                        affectedPlayer.updateWornItems(affectedItem.getWieldableDef().getWieldPos(), affectedPlayer.getPlayerAppearance().getSprite(affectedItem.getWieldableDef().getWieldPos()));
                    }
                    affectedPlayer.getInventory().remove(item);
                }
                for (InvItem item : myOffer) {
                    affectedPlayer.getInventory().add(item);
                }
                for (InvItem item : theirOffer) {
                    player.getInventory().add(item);
                }
                player.getActionSender().sendInventory();
                player.getActionSender().sendEquipmentStats();
                player.getActionSender().sendMessage("@gry@ Trade completed.");
                affectedPlayer.getActionSender().sendInventory();
                affectedPlayer.getActionSender().sendEquipmentStats();
                affectedPlayer.getActionSender().sendMessage("@gry@ Trade completed.");
                this.unsetOptions(player);
                this.unsetOptions(affectedPlayer);
                break;
            }
            case 216: {
                Player affectedPlayer = player.getWishToTrade();
                if (affectedPlayer == null || this.busy(affectedPlayer) || !player.isTrading() || !affectedPlayer.isTrading()) {
                    player.setSuspiciousPlayer(true);
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                affectedPlayer.getActionSender().sendMessage("@gry@ " + player.getUsername() + " has declined the trade.");
                this.unsetOptions(player);
                this.unsetOptions(affectedPlayer);
                break;
            }
            case 70: {
                Player affectedPlayer = player.getWishToTrade();
                if (affectedPlayer == null || this.busy(affectedPlayer) || !player.isTrading() || !affectedPlayer.isTrading() || player.isTradeOfferAccepted() && affectedPlayer.isTradeOfferAccepted() || player.isTradeConfirmAccepted() || affectedPlayer.isTradeConfirmAccepted()) {
                    player.setSuspiciousPlayer(true);
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                player.setTradeOfferAccepted(false);
                player.setTradeConfirmAccepted(false);
                affectedPlayer.setTradeOfferAccepted(false);
                affectedPlayer.setTradeConfirmAccepted(false);
                player.getActionSender().sendTradeAcceptUpdate();
                affectedPlayer.getActionSender().sendTradeAcceptUpdate();
                Inventory tradeOffer = new Inventory();
                player.resetTradeOffer();
                int count = p.readByte();
                for (int slot = 0; slot < count; ++slot) {
                    InvItem tItem = new InvItem(p.readShort(), p.readInt());
                    if (tItem.getAmount() < 1) {
                        player.setSuspiciousPlayer(true);
                        continue;
                    }
                    tradeOffer.add(tItem);
                }
                for (InvItem item : tradeOffer.getItems()) {
                    if (tradeOffer.countId(item.getID()) > player.getInventory().countId(item.getID())) {
                        player.setSuspiciousPlayer(true);
                        this.unsetOptions(player);
                        this.unsetOptions(affectedPlayer);
                        return;
                    }
                    /* Cape of legends and anything else marked untradeable.
                       Rejecting the whole update rather than silently
                       dropping just this item, so the offer the other side
                       sees never briefly includes it. */
                    if (Formulae.isUntradeable(item.getID())) {
                        player.getActionSender().sendMessage("@gry@ " + item.getDef().getName() + " cannot be traded.");
                        this.unsetOptions(player);
                        this.unsetOptions(affectedPlayer);
                        return;
                    }
                    player.addToTradeOffer(item);
                }
                player.setRequiresOfferUpdate(true);
            }
        }
    }

    private void unsetOptions(Player p) {
        if (p == null) {
            return;
        }
        p.resetTrading();
    }
}

