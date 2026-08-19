/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.event.DuelEvent;
import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.event.WalkToMobEvent;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Inventory;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;
import org.rscdaemon.server.states.Action;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class DuelHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    private boolean busy(Player player) {
        return player.isBusy() || player.isRanging() || player.accessingBank() || player.isTrading();
    }

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        Player affectedPlayer = player.getWishToDuel();
        if (player.isDuelConfirmAccepted() && affectedPlayer != null && affectedPlayer.isDuelConfirmAccepted()) {
            return;
        }
        if (player == affectedPlayer) {
            System.out.println("Warning : " + player.getUsername() + " tried to duel himself");
            this.unsetOptions(player);
            this.unsetOptions(affectedPlayer);
            return;
        }
        if (this.busy(player) || player.getLocation().inWilderness() || player.getLocation().inBlackHole()) {
            /*
             * Same reasoning as TradeHandler's inBlackHole check: fixed
             * landing tiles mean multiple banned/visiting players could
             * stack on the same spot, and duelling (staking items) between
             * them would defeat the point of the isolation.
             */
            if (player.getLocation().inBlackHole()) {
                player.getActionSender().sendMessage("@gry@ The black hole seems to be preventing you from duelling.");
            }
            this.unsetOptions(player);
            this.unsetOptions(affectedPlayer);
            return;
        }
        if (player.getLocation().inModRoom() || player.getLocation().inEden()) {
            player.getActionSender().sendMessage("@gry@ Dueling disabled.");
            this.unsetOptions(player);
            this.unsetOptions(affectedPlayer);
            return;
        }
        switch (pID) {
            case 222: {
                affectedPlayer = world.getPlayer(p.readShort());
                if (affectedPlayer == null || !player.withinRange(affectedPlayer, 8) || player.isDueling() || player.tradeDuelThrottling()) {
                    this.unsetOptions(player);
                    return;
                }
                if (affectedPlayer.getPrivacySetting(3) && !affectedPlayer.isFriendsWith(player.getUsernameHash()) || affectedPlayer.isIgnoring(player.getUsernameHash())) {
                    player.getActionSender().sendMessage("@gry@ This player has duel requests blocked.");
                    return;
                }
                player.setWishToDuel(affectedPlayer);
                player.getActionSender().sendMessage("@gry@ " + (affectedPlayer.isDueling() ? affectedPlayer.getUsername() + " is already in a duel" : "Sending duel request"));
                affectedPlayer.getActionSender().sendMessage(player.getUsername() + " " + Formulae.getLvlDiffColour(affectedPlayer.getCombatLevel() - player.getCombatLevel()) + "(level-" + player.getCombatLevel() + ")@whi@ wishes to duel with you");
                if (player.isDueling() || affectedPlayer.getWishToDuel() == null || !affectedPlayer.getWishToDuel().equals(player) || affectedPlayer.isDueling()) break;
                player.setDueling(true);
                player.resetPath();
                player.clearDuelOptions();
                player.resetAllExceptDueling();
                affectedPlayer.setDueling(true);
                affectedPlayer.resetPath();
                affectedPlayer.clearDuelOptions();
                affectedPlayer.resetAllExceptDueling();
                player.getActionSender().sendDuelWindowOpen();
                affectedPlayer.getActionSender().sendDuelWindowOpen();
                break;
            }
            case 252: {
                affectedPlayer = player.getWishToDuel();
                if (affectedPlayer == null || this.busy(affectedPlayer) || !player.isDueling() || !affectedPlayer.isDueling()) {
                    player.setSuspiciousPlayer(true);
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                player.setDuelOfferAccepted(true);
                player.getActionSender().sendDuelAcceptUpdate();
                affectedPlayer.getActionSender().sendDuelAcceptUpdate();
                if (!affectedPlayer.isDuelOfferAccepted()) break;
                player.getActionSender().sendDuelAccept();
                affectedPlayer.getActionSender().sendDuelAccept();
                break;
            }
            case 87: {
                affectedPlayer = player.getWishToDuel();
                if (!(affectedPlayer != null && !this.busy(affectedPlayer) && player.isDueling() && affectedPlayer.isDueling() && player.isDuelOfferAccepted() && affectedPlayer.isDuelOfferAccepted())) {
                    player.setSuspiciousPlayer(true);
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                player.setDuelConfirmAccepted(true);
                if (!affectedPlayer.isDuelConfirmAccepted()) break;
                player.getActionSender().sendDuelWindowClose();
                player.getActionSender().sendMessage("Commencing Duel");
                affectedPlayer.getActionSender().sendDuelWindowClose();
                affectedPlayer.getActionSender().sendMessage("Commencing Duel");
                player.resetAllExceptDueling();
                player.setBusy(true);
                player.setStatus(Action.DUELING_PLAYER);
                affectedPlayer.resetAllExceptDueling();
                affectedPlayer.setBusy(true);
                affectedPlayer.setStatus(Action.DUELING_PLAYER);
                if (player.getDuelSetting(3)) {
                    for (InvItem item : player.getInventory().getItems()) {
                        if (!item.isWielded()) continue;
                        item.setWield(false);
                        player.updateWornItems(item.getWieldableDef().getWieldPos(), player.getPlayerAppearance().getSprite(item.getWieldableDef().getWieldPos()));
                    }
                    player.getActionSender().sendSound("click");
                    player.getActionSender().sendInventory();
                    player.getActionSender().sendEquipmentStats();
                    for (InvItem item : affectedPlayer.getInventory().getItems()) {
                        if (!item.isWielded()) continue;
                        item.setWield(false);
                        affectedPlayer.updateWornItems(item.getWieldableDef().getWieldPos(), affectedPlayer.getPlayerAppearance().getSprite(item.getWieldableDef().getWieldPos()));
                    }
                    affectedPlayer.getActionSender().sendSound("click");
                    affectedPlayer.getActionSender().sendInventory();
                    affectedPlayer.getActionSender().sendEquipmentStats();
                }
                if (player.getDuelSetting(2)) {
                    for (int x = 0; x < 14; ++x) {
                        if (player.isPrayerActivated(x)) {
                            player.removePrayerDrain(x);
                            player.setPrayer(x, false);
                        }
                        if (!affectedPlayer.isPrayerActivated(x)) continue;
                        affectedPlayer.removePrayerDrain(x);
                        affectedPlayer.setPrayer(x, false);
                    }
                    player.getActionSender().sendPrayers();
                    affectedPlayer.getActionSender().sendPrayers();
                }
                player.setFollowing(affectedPlayer);
                WalkToMobEvent walking = new WalkToMobEvent(player, affectedPlayer, 1){

                    public void arrived() {
                        world.getDelayedEventHandler().add(new SingleEvent(this.owner, 1000){

                            public void action() {
                                Player opponent;
                                Player attacker;
                                Player affectedPlayer = (Player)affectedMob;
                                this.owner.resetPath();
                                if (!this.owner.nextTo(affectedPlayer)) {
                                    DuelHandler.this.unsetOptions(this.owner);
                                    DuelHandler.this.unsetOptions(affectedPlayer);
                                    return;
                                }
                                affectedPlayer.resetPath();
                                this.owner.resetAllExceptDueling();
                                affectedPlayer.resetAllExceptDueling();
                                this.owner.setLocation(affectedPlayer.getLocation(), true);
                                for (Player p : this.owner.getViewArea().getPlayersInView()) {
                                    p.removeWatchedPlayer(this.owner);
                                }
                                this.owner.setSprite(9);
                                this.owner.setOpponent(affectedMob);
                                this.owner.setCombatTimer();
                                affectedPlayer.setSprite(8);
                                affectedPlayer.setOpponent(this.owner);
                                affectedPlayer.setCombatTimer();
                                if (this.owner.getCombatLevel() > affectedPlayer.getCombatLevel()) {
                                    attacker = affectedPlayer;
                                    opponent = this.owner;
                                } else if (affectedPlayer.getCombatLevel() > this.owner.getCombatLevel()) {
                                    attacker = this.owner;
                                    opponent = affectedPlayer;
                                } else if (DataConversions.random(0, 1) == 1) {
                                    attacker = this.owner;
                                    opponent = affectedPlayer;
                                } else {
                                    attacker = affectedPlayer;
                                    opponent = this.owner;
                                }
                                DuelEvent dueling = new DuelEvent(attacker, opponent);
                                dueling.setLastRun(0L);
                                world.getDelayedEventHandler().add(dueling);
                            }
                        });
                    }

                    public void failed() {
                        Player affectedPlayer = (Player)this.affectedMob;
                        this.owner.getActionSender().sendMessage("You were unable to reach " + affectedPlayer.getUsername() + " - the duel has been cancelled");
                        affectedPlayer.getActionSender().sendMessage(this.owner.getUsername() + " was unable to reach you - the duel has been cancelled");
                        DuelHandler.this.unsetOptions(this.owner);
                        DuelHandler.this.unsetOptions(affectedPlayer);
                        this.owner.setBusy(false);
                        affectedPlayer.setBusy(false);
                    }
                };
                walking.setLastRun(System.currentTimeMillis() + 500L);
                world.getDelayedEventHandler().add(walking);
                break;
            }
            case 35: {
                affectedPlayer = player.getWishToDuel();
                if (affectedPlayer == null || this.busy(affectedPlayer) || !player.isDueling() || !affectedPlayer.isDueling()) {
                    player.setSuspiciousPlayer(true);
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                affectedPlayer.getActionSender().sendMessage(player.getUsername() + " has declined the duel.");
                this.unsetOptions(player);
                this.unsetOptions(affectedPlayer);
                break;
            }
            case 123: {
                affectedPlayer = player.getWishToDuel();
                if (affectedPlayer == null || this.busy(affectedPlayer) || !player.isDueling() || !affectedPlayer.isDueling() || player.isDuelOfferAccepted() && affectedPlayer.isDuelOfferAccepted() || player.isDuelConfirmAccepted() || affectedPlayer.isDuelConfirmAccepted()) {
                    player.setSuspiciousPlayer(true);
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                player.setDuelOfferAccepted(false);
                player.setDuelConfirmAccepted(false);
                affectedPlayer.setDuelOfferAccepted(false);
                affectedPlayer.setDuelConfirmAccepted(false);
                player.getActionSender().sendDuelAcceptUpdate();
                affectedPlayer.getActionSender().sendDuelAcceptUpdate();
                Inventory duelOffer = new Inventory();
                player.resetDuelOffer();
                int count = p.readByte();
                for (int slot = 0; slot < count; ++slot) {
                    InvItem tItem = new InvItem(p.readShort(), p.readInt());
                    if (tItem.getAmount() < 1) {
                        player.setSuspiciousPlayer(true);
                        continue;
                    }
                    duelOffer.add(tItem);
                }
                for (InvItem item : duelOffer.getItems()) {
                    if (duelOffer.countId(item.getID()) > player.getInventory().countId(item.getID())) {
                        player.setSuspiciousPlayer(true);
                        return;
                    }
                    /* Untradeable items can't be staked either. */
                    if (Formulae.isUntradeable(item.getID())) {
                        player.getActionSender().sendMessage("@gry@ " + item.getDef().getName() + " cannot be staked.");
                        return;
                    }
                    player.addToDuelOffer(item);
                }
                player.setRequiresOfferUpdate(true);
                break;
            }
            case 225: {
                affectedPlayer = player.getWishToDuel();
                if (affectedPlayer == null || this.busy(affectedPlayer) || !player.isDueling() || !affectedPlayer.isDueling() || player.isDuelOfferAccepted() && affectedPlayer.isDuelOfferAccepted() || player.isDuelConfirmAccepted() || affectedPlayer.isDuelConfirmAccepted()) {
                    player.setSuspiciousPlayer(true);
                    this.unsetOptions(player);
                    this.unsetOptions(affectedPlayer);
                    return;
                }
                player.setDuelOfferAccepted(false);
                player.setDuelConfirmAccepted(false);
                affectedPlayer.setDuelOfferAccepted(false);
                affectedPlayer.setDuelConfirmAccepted(false);
                player.getActionSender().sendDuelAcceptUpdate();
                affectedPlayer.getActionSender().sendDuelAcceptUpdate();
                for (int i = 0; i < 4; ++i) {
                    boolean b = p.readByte() == 1;
                    player.setDuelSetting(i, b);
                    affectedPlayer.setDuelSetting(i, b);
                }
                player.getActionSender().sendDuelSettingUpdate();
                affectedPlayer.getActionSender().sendDuelSettingUpdate();
            }
        }
    }

    private void unsetOptions(Player p) {
        if (p == null) {
            return;
        }
        p.resetDueling();
    }
}

