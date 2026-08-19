/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder.loginserver;

import org.rscdaemon.server.model.Bank;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Inventory;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.PlayerAppearance;
import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.packetbuilder.LSPacketBuilder;
import org.rscdaemon.server.util.DataConversions;

public class SavePacketBuilder {
    private Player player;

    public void setPlayer(Player player) {
        this.player = player;
    }

    public LSPacket getPacket() {
        LSPacketBuilder packet = new LSPacketBuilder();
        packet.setID(20);
        packet.addLong(this.player.getUsernameHash());
        packet.addInt(this.player.getOwner());
        packet.addLong(this.player.getLastLogin() == 0L && this.player.isChangingAppearance() ? 0L : this.player.getCurrentLogin());
        packet.addLong(DataConversions.IPToLong(this.player.getCurrentIP()));
        packet.addShort(this.player.getCombatLevel());
        packet.addShort(this.player.getSkillTotal());
        packet.addShort(this.player.getX());
        packet.addShort(this.player.getY());
        packet.addShort(this.player.getFatigue());
        PlayerAppearance a = this.player.getPlayerAppearance();
        packet.addByte(a.getHairColour());
        packet.addByte(a.getTopColour());
        packet.addByte(a.getTrouserColour());
        packet.addByte(a.getSkinColour());
        packet.addByte((byte)a.getSprite(0));
        packet.addByte((byte)a.getSprite(1));
        packet.addByte((byte)(this.player.isMale() ? 1 : 0));
        packet.addLong(this.player.getSkullTime());
        packet.addByte((byte)this.player.getCombatStyle());
        for (int i = 0; i < 19; ++i) {
            packet.addLong(this.player.getExp(i));
            packet.addShort(this.player.getCurStat(i));
        }
        Inventory inv = this.player.getInventory();
        packet.addShort(inv.size());
        for (InvItem i : inv.getItems()) {
            packet.addShort(i.getID());
            packet.addInt(i.getAmount());
            packet.addByte((byte)(i.isWielded() ? 1 : 0));
        }
        Bank bnk = this.player.getBank();
        packet.addShort(bnk.size());
        for (InvItem i : bnk.getItems()) {
            packet.addShort(i.getID());
            packet.addInt(i.getAmount());
        }
        java.util.Map<Integer, Integer> quests = this.player.getQuestStages();
        packet.addShort(quests.size());
        for (java.util.Map.Entry<Integer, Integer> q : quests.entrySet()) {
            packet.addShort(q.getKey());
            packet.addInt(q.getValue());
        }
        // Dragonstone amulet teleports left. One byte, 1..4, and it goes last so
        // that this and the login packet stay mirror images of each other.
        packet.addByte((byte)this.player.getAmuletCharges());
        // Poison, so that logging out stalls it rather than curing it. Strength
        // is at most 6 and hits at most five per point of it, so a byte each.
        // The immunity a cure buys is deliberately not here -- see Player.
        packet.addByte((byte)this.player.getPoisonStrength());
        packet.addByte((byte)this.player.getPoisonHits());
        return packet.toPacket();
    }
}

