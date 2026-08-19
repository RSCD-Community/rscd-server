/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler.client;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.PrayerDef;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.packethandler.PacketHandler;

public class PrayerHandler
implements PacketHandler {
    public static final World world = World.getWorld();

    public void handlePacket(Packet p, Connection session) throws Exception {
        Player player = (Player)session.getAttachment();
        int pID = ((RSCPacket)p).getID();
        byte prayerID = p.readByte();
        if (prayerID < 0 || prayerID >= EntityHandler.prayerCount()) {
            player.setSuspiciousPlayer(true);
            player.getActionSender().sendPrayers();
            return;
        }
        if (player.isDueling() && player.getDuelSetting(2)) {
            player.getActionSender().sendMessage("@gry@ Prayer is disabled in this duel");
            player.getActionSender().sendPrayers();
            return;
        }
        PrayerDef prayer = EntityHandler.getPrayerDef(prayerID);
        switch (pID) {
            case 56: {
                if (player.getMaxStat(5) < prayer.getReqLevel()) {
                    player.setSuspiciousPlayer(true);
                    player.getActionSender().sendMessage("@gry@ Your prayer ability is not high enough to use this prayer");
                    break;
                }
                if (player.getCurStat(5) <= 0) {
                    player.setPrayer(prayerID, false);
                    player.getActionSender().sendMessage("@gry@ You have run out of prayer points. Return to a church to recharge");
                    break;
                }
                this.activatePrayer(player, prayerID);
                break;
            }
            case 248: {
                this.deactivatePrayer(player, prayerID);
            }
        }
        player.getActionSender().sendPrayers();
    }

    /**
     * The stat-boost tiers of one skill exclude each other -- turning one on
     * turns its siblings off, exactly the switch the original if-chain
     * encoded for the melee trios, and Rapid renewal supersedes Rapid heal
     * the same way. The protect prayers and the ungrouped prayers (Rapid
     * restore, Protect items, Paralyze monster, Retribution, Smite) stack
     * freely.
     */
    private static final int[][] EXCLUSIVE = {
        { 0, 3, 9 },    // defense:  Thick skin / Rock skin / Steel skin
        { 1, 4, 10 },   // strength: Burst of strength / Superhuman / Ultimate
        { 2, 5, 11 },   // attack:   Clarity / Improved / Incredible reflexes
        { 14, 16, 18 }, // ranged:   Sharp eye / Hawk eye / Eagle eye
        { 15, 17, 19 }, // magic:    Mystic will / Mystic lore / Mystic might
        { 7, 22 },      // healing:  Rapid heal / Rapid renewal
    };

    private boolean activatePrayer(Player player, int prayerID) {
        if (!player.isPrayerActivated(prayerID)) {
            for (int[] group : EXCLUSIVE) {
                boolean mine = false;
                for (int id : group) {
                    if (id == prayerID) {
                        mine = true;
                        break;
                    }
                }
                if (!mine) {
                    continue;
                }
                for (int id : group) {
                    if (id != prayerID) {
                        this.deactivatePrayer(player, id);
                    }
                }
                break;
            }
            player.addPrayerDrain(prayerID);
            player.setPrayer(prayerID, true);
            return true;
        }
        return false;
    }

    private boolean deactivatePrayer(Player player, int prayerID) {
        if (player.isPrayerActivated(prayerID)) {
            player.removePrayerDrain(prayerID);
            player.setPrayer(prayerID, false);
            return true;
        }
        return false;
    }
}

